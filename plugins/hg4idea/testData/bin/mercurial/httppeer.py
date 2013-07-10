# httppeer.py - HTTP repository proxy classes for mercurial
#
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import nullid
from i18n import _
import changegroup, statichttprepo, error, httpconnection, url, util, wireproto
import os, urllib, urllib2, zlib, httplib
import errno, socket

def zgenerator(f):
    zd = zlib.decompressobj()
    try:
        for chunk in util.filechunkiter(f):
            while chunk:
                yield zd.decompress(chunk, 2**18)
                chunk = zd.unconsumed_tail
    except httplib.HTTPException:
        raise IOError(None, _('connection ended unexpectedly'))
    yield zd.flush()

class httppeer(wireproto.wirepeer):
    def __init__(self, ui, path):
        self.path = path
        self.caps = None
        self.handler = None
        self.urlopener = None
        u = util.url(path)
        if u.query or u.fragment:
            raise util.Abort(_('unsupported URL component: "%s"') %
                             (u.query or u.fragment))

        # urllib cannot handle URLs with embedded user or passwd
        self._url, authinfo = u.authinfo()

        self.ui = ui
        self.ui.debug('using %s\n' % self._url)

        self.urlopener = url.opener(ui, authinfo)

    def __del__(self):
        if self.urlopener:
            for h in self.urlopener.handlers:
                h.close()
                getattr(h, "close_all", lambda : None)()

    def url(self):
        return self.path

    # look up capabilities only when needed

    def _fetchcaps(self):
        self.caps = set(self._call('capabilities').split())

    def _capabilities(self):
        if self.caps is None:
            try:
                self._fetchcaps()
            except error.RepoError:
                self.caps = set()
            self.ui.debug('capabilities: %s\n' %
                          (' '.join(self.caps or ['none'])))
        return self.caps

    def lock(self):
        raise util.Abort(_('operation not supported over http'))

    def _callstream(self, cmd, **args):
        if cmd == 'pushkey':
            args['data'] = ''
        data = args.pop('data', None)
        size = 0
        if util.safehasattr(data, 'length'):
            size = data.length
        elif data is not None:
            size = len(data)
        headers = args.pop('headers', {})
        if data is not None and 'Content-Type' not in headers:
            headers['Content-Type'] = 'application/mercurial-0.1'


        if size and self.ui.configbool('ui', 'usehttp2', False):
            headers['Expect'] = '100-Continue'
            headers['X-HgHttp2'] = '1'

        self.ui.debug("sending %s command\n" % cmd)
        q = [('cmd', cmd)]
        headersize = 0
        if len(args) > 0:
            httpheader = self.capable('httpheader')
            if httpheader:
                headersize = int(httpheader.split(',')[0])
        if headersize > 0:
            # The headers can typically carry more data than the URL.
            encargs = urllib.urlencode(sorted(args.items()))
            headerfmt = 'X-HgArg-%s'
            contentlen = headersize - len(headerfmt % '000' + ': \r\n')
            headernum = 0
            for i in xrange(0, len(encargs), contentlen):
                headernum += 1
                header = headerfmt % str(headernum)
                headers[header] = encargs[i:i + contentlen]
            varyheaders = [headerfmt % str(h) for h in range(1, headernum + 1)]
            headers['Vary'] = ','.join(varyheaders)
        else:
            q += sorted(args.items())
        qs = '?%s' % urllib.urlencode(q)
        cu = "%s%s" % (self._url, qs)
        req = urllib2.Request(cu, data, headers)
        if data is not None:
            self.ui.debug("sending %s bytes\n" % size)
            req.add_unredirected_header('Content-Length', '%d' % size)
        try:
            resp = self.urlopener.open(req)
        except urllib2.HTTPError, inst:
            if inst.code == 401:
                raise util.Abort(_('authorization failed'))
            raise
        except httplib.HTTPException, inst:
            self.ui.debug('http error while sending %s command\n' % cmd)
            self.ui.traceback()
            raise IOError(None, inst)
        except IndexError:
            # this only happens with Python 2.3, later versions raise URLError
            raise util.Abort(_('http error, possibly caused by proxy setting'))
        # record the url we got redirected to
        resp_url = resp.geturl()
        if resp_url.endswith(qs):
            resp_url = resp_url[:-len(qs)]
        if self._url.rstrip('/') != resp_url.rstrip('/'):
            if not self.ui.quiet:
                self.ui.warn(_('real URL is %s\n') % resp_url)
        self._url = resp_url
        try:
            proto = resp.getheader('content-type')
        except AttributeError:
            proto = resp.headers.get('content-type', '')

        safeurl = util.hidepassword(self._url)
        if proto.startswith('application/hg-error'):
            raise error.OutOfBandError(resp.read())
        # accept old "text/plain" and "application/hg-changegroup" for now
        if not (proto.startswith('application/mercurial-') or
                (proto.startswith('text/plain')
                 and not resp.headers.get('content-length')) or
                proto.startswith('application/hg-changegroup')):
            self.ui.debug("requested URL: '%s'\n" % util.hidepassword(cu))
            raise error.RepoError(
                _("'%s' does not appear to be an hg repository:\n"
                  "---%%<--- (%s)\n%s\n---%%<---\n")
                % (safeurl, proto or 'no content-type', resp.read(1024)))

        if proto.startswith('application/mercurial-'):
            try:
                version = proto.split('-', 1)[1]
                version_info = tuple([int(n) for n in version.split('.')])
            except ValueError:
                raise error.RepoError(_("'%s' sent a broken Content-Type "
                                        "header (%s)") % (safeurl, proto))
            if version_info > (0, 1):
                raise error.RepoError(_("'%s' uses newer protocol %s") %
                                      (safeurl, version))

        return resp

    def _call(self, cmd, **args):
        fp = self._callstream(cmd, **args)
        try:
            return fp.read()
        finally:
            # if using keepalive, allow connection to be reused
            fp.close()

    def _callpush(self, cmd, cg, **args):
        # have to stream bundle to a temp file because we do not have
        # http 1.1 chunked transfer.

        types = self.capable('unbundle')
        try:
            types = types.split(',')
        except AttributeError:
            # servers older than d1b16a746db6 will send 'unbundle' as a
            # boolean capability. They only support headerless/uncompressed
            # bundles.
            types = [""]
        for x in types:
            if x in changegroup.bundletypes:
                type = x
                break

        tempname = changegroup.writebundle(cg, None, type)
        fp = httpconnection.httpsendfile(self.ui, tempname, "rb")
        headers = {'Content-Type': 'application/mercurial-0.1'}

        try:
            try:
                r = self._call(cmd, data=fp, headers=headers, **args)
                vals = r.split('\n', 1)
                if len(vals) < 2:
                    raise error.ResponseError(_("unexpected response:"), r)
                return vals
            except socket.error, err:
                if err.args[0] in (errno.ECONNRESET, errno.EPIPE):
                    raise util.Abort(_('push failed: %s') % err.args[1])
                raise util.Abort(err.args[1])
        finally:
            fp.close()
            os.unlink(tempname)

    def _abort(self, exception):
        raise exception

    def _decompress(self, stream):
        return util.chunkbuffer(zgenerator(stream))

class httpspeer(httppeer):
    def __init__(self, ui, path):
        if not url.has_https:
            raise util.Abort(_('Python support for SSL and HTTPS '
                               'is not installed'))
        httppeer.__init__(self, ui, path)

def instance(ui, path, create):
    if create:
        raise util.Abort(_('cannot create new http repository'))
    try:
        if path.startswith('https:'):
            inst = httpspeer(ui, path)
        else:
            inst = httppeer(ui, path)
        try:
            # Try to do useful work when checking compatibility.
            # Usually saves a roundtrip since we want the caps anyway.
            inst._fetchcaps()
        except error.RepoError:
            # No luck, try older compatibility check.
            inst.between([(nullid, nullid)])
        return inst
    except error.RepoError, httpexception:
        try:
            r = statichttprepo.instance(ui, "static-" + path, create)
            ui.note('(falling back to static-http)\n')
            return r
        except error.RepoError:
            raise httpexception # use the original http RepoError instead
