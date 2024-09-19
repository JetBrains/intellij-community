# httprepo.py - HTTP repository proxy classes for mercurial
#
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import bin, hex, nullid
from i18n import _
import repo, changegroup, statichttprepo, error, url, util
import os, urllib, urllib2, urlparse, zlib, httplib
import errno, socket
import encoding

def zgenerator(f):
    zd = zlib.decompressobj()
    try:
        for chunk in util.filechunkiter(f):
            yield zd.decompress(chunk)
    except httplib.HTTPException:
        raise IOError(None, _('connection ended unexpectedly'))
    yield zd.flush()

class httprepository(repo.repository):
    def __init__(self, ui, path):
        self.path = path
        self.caps = None
        self.handler = None
        scheme, netloc, urlpath, query, frag = urlparse.urlsplit(path)
        if query or frag:
            raise util.Abort(_('unsupported URL component: "%s"') %
                             (query or frag))

        # urllib cannot handle URLs with embedded user or passwd
        self._url, authinfo = url.getauthinfo(path)

        self.ui = ui
        self.ui.debug('using %s\n' % self._url)

        self.urlopener = url.opener(ui, authinfo)

    def __del__(self):
        for h in self.urlopener.handlers:
            h.close()
            if hasattr(h, "close_all"):
                h.close_all()

    def url(self):
        return self.path

    # look up capabilities only when needed

    def get_caps(self):
        if self.caps is None:
            try:
                self.caps = set(self.do_read('capabilities').split())
            except error.RepoError:
                self.caps = set()
            self.ui.debug('capabilities: %s\n' %
                          (' '.join(self.caps or ['none'])))
        return self.caps

    capabilities = property(get_caps)

    def lock(self):
        raise util.Abort(_('operation not supported over http'))

    def do_cmd(self, cmd, **args):
        data = args.pop('data', None)
        headers = args.pop('headers', {})
        self.ui.debug("sending %s command\n" % cmd)
        q = {"cmd": cmd}
        q.update(args)
        qs = '?%s' % urllib.urlencode(q)
        cu = "%s%s" % (self._url, qs)
        req = urllib2.Request(cu, data, headers)
        if data is not None:
            # len(data) is broken if data doesn't fit into Py_ssize_t
            # add the header ourself to avoid OverflowError
            size = data.__len__()
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
            self.ui.status(_('real URL is %s\n') % resp_url)
        self._url = resp_url
        try:
            proto = resp.getheader('content-type')
        except AttributeError:
            proto = resp.headers['content-type']

        safeurl = url.hidepassword(self._url)
        # accept old "text/plain" and "application/hg-changegroup" for now
        if not (proto.startswith('application/mercurial-') or
                proto.startswith('text/plain') or
                proto.startswith('application/hg-changegroup')):
            self.ui.debug("requested URL: '%s'\n" % url.hidepassword(cu))
            raise error.RepoError(
                _("'%s' does not appear to be an hg repository:\n"
                  "---%%<--- (%s)\n%s\n---%%<---\n")
                % (safeurl, proto, resp.read()))

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

    def do_read(self, cmd, **args):
        fp = self.do_cmd(cmd, **args)
        try:
            return fp.read()
        finally:
            # if using keepalive, allow connection to be reused
            fp.close()

    def lookup(self, key):
        self.requirecap('lookup', _('look up remote revision'))
        d = self.do_cmd("lookup", key = key).read()
        success, data = d[:-1].split(' ', 1)
        if int(success):
            return bin(data)
        raise error.RepoError(data)

    def heads(self):
        d = self.do_read("heads")
        try:
            return map(bin, d[:-1].split(" "))
        except:
            raise error.ResponseError(_("unexpected response:"), d)

    def branchmap(self):
        d = self.do_read("branchmap")
        try:
            branchmap = {}
            for branchpart in d.splitlines():
                branchheads = branchpart.split(' ')
                branchname = urllib.unquote(branchheads[0])
                # Earlier servers (1.3.x) send branch names in (their) local
                # charset. The best we can do is assume it's identical to our
                # own local charset, in case it's not utf-8.
                try:
                    branchname.decode('utf-8')
                except UnicodeDecodeError:
                    branchname = encoding.fromlocal(branchname)
                branchheads = [bin(x) for x in branchheads[1:]]
                branchmap[branchname] = branchheads
            return branchmap
        except:
            raise error.ResponseError(_("unexpected response:"), d)

    def branches(self, nodes):
        n = " ".join(map(hex, nodes))
        d = self.do_read("branches", nodes=n)
        try:
            br = [tuple(map(bin, b.split(" "))) for b in d.splitlines()]
            return br
        except:
            raise error.ResponseError(_("unexpected response:"), d)

    def between(self, pairs):
        batch = 8 # avoid giant requests
        r = []
        for i in xrange(0, len(pairs), batch):
            n = " ".join(["-".join(map(hex, p)) for p in pairs[i:i + batch]])
            d = self.do_read("between", pairs=n)
            try:
                r += [l and map(bin, l.split(" ")) or []
                      for l in d.splitlines()]
            except:
                raise error.ResponseError(_("unexpected response:"), d)
        return r

    def changegroup(self, nodes, kind):
        n = " ".join(map(hex, nodes))
        f = self.do_cmd("changegroup", roots=n)
        return util.chunkbuffer(zgenerator(f))

    def changegroupsubset(self, bases, heads, source):
        self.requirecap('changegroupsubset', _('look up remote changes'))
        baselst = " ".join([hex(n) for n in bases])
        headlst = " ".join([hex(n) for n in heads])
        f = self.do_cmd("changegroupsubset", bases=baselst, heads=headlst)
        return util.chunkbuffer(zgenerator(f))

    def unbundle(self, cg, heads, source):
        # have to stream bundle to a temp file because we do not have
        # http 1.1 chunked transfer.

        type = ""
        types = self.capable('unbundle')
        # servers older than d1b16a746db6 will send 'unbundle' as a
        # boolean capability
        try:
            types = types.split(',')
        except AttributeError:
            types = [""]
        if types:
            for x in types:
                if x in changegroup.bundletypes:
                    type = x
                    break

        tempname = changegroup.writebundle(cg, None, type)
        fp = url.httpsendfile(tempname, "rb")
        try:
            try:
                resp = self.do_read(
                     'unbundle', data=fp,
                     headers={'Content-Type': 'application/mercurial-0.1'},
                     heads=' '.join(map(hex, heads)))
                resp_code, output = resp.split('\n', 1)
                try:
                    ret = int(resp_code)
                except ValueError, err:
                    raise error.ResponseError(
                            _('push failed (unexpected response):'), resp)
                for l in output.splitlines(True):
                    self.ui.status(_('remote: '), l)
                return ret
            except socket.error, err:
                if err[0] in (errno.ECONNRESET, errno.EPIPE):
                    raise util.Abort(_('push failed: %s') % err[1])
                raise util.Abort(err[1])
        finally:
            fp.close()
            os.unlink(tempname)

    def stream_out(self):
        return self.do_cmd('stream_out')

class httpsrepository(httprepository):
    def __init__(self, ui, path):
        if not url.has_https:
            raise util.Abort(_('Python support for SSL and HTTPS '
                               'is not installed'))
        httprepository.__init__(self, ui, path)

def instance(ui, path, create):
    if create:
        raise util.Abort(_('cannot create new http repository'))
    try:
        if path.startswith('https:'):
            inst = httpsrepository(ui, path)
        else:
            inst = httprepository(ui, path)
        inst.between([(nullid, nullid)])
        return inst
    except error.RepoError:
        ui.note('(falling back to static-http)\n')
        return statichttprepo.instance(ui, "static-" + path, create)
