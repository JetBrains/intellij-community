# httppeer.py - HTTP repository proxy classes for mercurial
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import errno
import io
import os
import socket
import struct

from concurrent import futures
from .i18n import _
from . import (
    bundle2,
    error,
    httpconnection,
    pycompat,
    statichttprepo,
    url as urlmod,
    util,
    wireprotov1peer,
)
from .utils import urlutil

httplib = util.httplib
urlerr = util.urlerr
urlreq = util.urlreq


def encodevalueinheaders(value, header, limit):
    """Encode a string value into multiple HTTP headers.

    ``value`` will be encoded into 1 or more HTTP headers with the names
    ``header-<N>`` where ``<N>`` is an integer starting at 1. Each header
    name + value will be at most ``limit`` bytes long.

    Returns an iterable of 2-tuples consisting of header names and
    values as native strings.
    """
    # HTTP Headers are ASCII. Python 3 requires them to be unicodes,
    # not bytes. This function always takes bytes in as arguments.
    fmt = pycompat.strurl(header) + r'-%s'
    # Note: it is *NOT* a bug that the last bit here is a bytestring
    # and not a unicode: we're just getting the encoded length anyway,
    # and using an r-string to make it portable between Python 2 and 3
    # doesn't work because then the \r is a literal backslash-r
    # instead of a carriage return.
    valuelen = limit - len(fmt % '000') - len(b': \r\n')
    result = []

    n = 0
    for i in range(0, len(value), valuelen):
        n += 1
        result.append((fmt % str(n), pycompat.strurl(value[i : i + valuelen])))

    return result


class _multifile:
    def __init__(self, *fileobjs):
        for f in fileobjs:
            if not hasattr(f, 'length'):
                raise ValueError(
                    b'_multifile only supports file objects that '
                    b'have a length but this one does not:',
                    type(f),
                    f,
                )
        self._fileobjs = fileobjs
        self._index = 0

    @property
    def length(self):
        return sum(f.length for f in self._fileobjs)

    def read(self, amt=None):
        if amt <= 0:
            return b''.join(f.read() for f in self._fileobjs)
        parts = []
        while amt and self._index < len(self._fileobjs):
            parts.append(self._fileobjs[self._index].read(amt))
            got = len(parts[-1])
            if got < amt:
                self._index += 1
            amt -= got
        return b''.join(parts)

    def seek(self, offset, whence=os.SEEK_SET):
        if whence != os.SEEK_SET:
            raise NotImplementedError(
                b'_multifile does not support anything other'
                b' than os.SEEK_SET for whence on seek()'
            )
        if offset != 0:
            raise NotImplementedError(
                b'_multifile only supports seeking to start, but that '
                b'could be fixed if you need it'
            )
        for f in self._fileobjs:
            f.seek(0)
        self._index = 0


def makev1commandrequest(
    ui,
    requestbuilder,
    caps,
    capablefn,
    repobaseurl,
    cmd,
    args,
    remotehidden=False,
):
    """Make an HTTP request to run a command for a version 1 client.

    ``caps`` is a set of known server capabilities. The value may be
    None if capabilities are not yet known.

    ``capablefn`` is a function to evaluate a capability.

    ``cmd``, ``args``, and ``data`` define the command, its arguments, and
    raw data to pass to it.
    """
    if cmd == b'pushkey':
        args[b'data'] = b''
    data = args.pop(b'data', None)
    headers = args.pop(b'headers', {})

    ui.debug(b"sending %s command\n" % cmd)
    q = [(b'cmd', cmd)]
    if remotehidden:
        q.append(('access-hidden', '1'))
    headersize = 0
    # Important: don't use self.capable() here or else you end up
    # with infinite recursion when trying to look up capabilities
    # for the first time.
    postargsok = caps is not None and b'httppostargs' in caps

    # Send arguments via POST.
    if postargsok and args:
        strargs = urlreq.urlencode(sorted(args.items()))
        if not data:
            data = strargs
        else:
            if isinstance(data, bytes):
                i = io.BytesIO(data)
                i.length = len(data)
                data = i
            argsio = io.BytesIO(strargs)
            argsio.length = len(strargs)
            data = _multifile(argsio, data)
        headers['X-HgArgs-Post'] = len(strargs)
    elif args:
        # Calling self.capable() can infinite loop if we are calling
        # "capabilities". But that command should never accept wire
        # protocol arguments. So this should never happen.
        assert cmd != b'capabilities'
        httpheader = capablefn(b'httpheader')
        if httpheader:
            headersize = int(httpheader.split(b',', 1)[0])

        # Send arguments via HTTP headers.
        if headersize > 0:
            # The headers can typically carry more data than the URL.
            encoded_args = urlreq.urlencode(sorted(args.items()))
            for header, value in encodevalueinheaders(
                encoded_args, b'X-HgArg', headersize
            ):
                headers[header] = value
        # Send arguments via query string (Mercurial <1.9).
        else:
            q += sorted(args.items())

    qs = b'?%s' % urlreq.urlencode(q)
    cu = b"%s%s" % (repobaseurl, qs)
    size = 0
    if hasattr(data, 'length'):
        size = data.length
    elif data is not None:
        size = len(data)
    if data is not None and 'Content-Type' not in headers:
        headers['Content-Type'] = 'application/mercurial-0.1'

    # Tell the server we accept application/mercurial-0.2 and multiple
    # compression formats if the server is capable of emitting those
    # payloads.
    # Note: Keep this set empty by default, as client advertisement of
    # protocol parameters should only occur after the handshake.
    protoparams = set()

    mediatypes = set()
    if caps is not None:
        mt = capablefn(b'httpmediatype')
        if mt:
            protoparams.add(b'0.1')
            mediatypes = set(mt.split(b','))

        protoparams.add(b'partial-pull')

    if b'0.2tx' in mediatypes:
        protoparams.add(b'0.2')

    if b'0.2tx' in mediatypes and capablefn(b'compression'):
        # We /could/ compare supported compression formats and prune
        # non-mutually supported or error if nothing is mutually supported.
        # For now, send the full list to the server and have it error.
        comps = [
            e.wireprotosupport().name
            for e in util.compengines.supportedwireengines(util.CLIENTROLE)
        ]
        protoparams.add(b'comp=%s' % b','.join(comps))

    if protoparams:
        protoheaders = encodevalueinheaders(
            b' '.join(sorted(protoparams)), b'X-HgProto', headersize or 1024
        )
        for header, value in protoheaders:
            headers[header] = value

    varyheaders = []
    for header in headers:
        if header.lower().startswith('x-hg'):
            varyheaders.append(header)

    if varyheaders:
        headers['Vary'] = ','.join(sorted(varyheaders))

    req = requestbuilder(pycompat.strurl(cu), data, headers)

    if data is not None:
        ui.debug(b"sending %d bytes\n" % size)
        req.add_unredirected_header('Content-Length', '%d' % size)

    return req, cu, qs


def sendrequest(ui, opener, req):
    """Send a prepared HTTP request.

    Returns the response object.
    """
    dbg = ui.debug
    if ui.debugflag and ui.configbool(b'devel', b'debug.peer-request'):
        line = b'devel-peer-request: %s\n'
        dbg(
            line
            % b'%s %s'
            % (
                pycompat.bytesurl(req.get_method()),
                pycompat.bytesurl(req.get_full_url()),
            )
        )
        hgargssize = None

        for header, value in sorted(req.header_items()):
            header = pycompat.bytesurl(header)
            value = pycompat.bytesurl(value)
            if header.startswith(b'X-hgarg-'):
                if hgargssize is None:
                    hgargssize = 0
                hgargssize += len(value)
            else:
                dbg(line % b'  %s %s' % (header, value))

        if hgargssize is not None:
            dbg(
                line
                % b'  %d bytes of commands arguments in headers'
                % hgargssize
            )
        data = req.data
        if data is not None:
            length = getattr(data, 'length', None)
            if length is None:
                length = len(data)
            dbg(line % b'  %d bytes of data' % length)

        start = util.timer()

    res = None
    try:
        res = opener.open(req)
    except urlerr.httperror as inst:
        if inst.code == 401:
            raise error.Abort(_(b'authorization failed'))
        raise
    except httplib.HTTPException as inst:
        ui.debug(
            b'http error requesting %s\n'
            % urlutil.hidepassword(req.get_full_url())
        )
        ui.traceback()
        raise IOError(None, inst)
    finally:
        if ui.debugflag and ui.configbool(b'devel', b'debug.peer-request'):
            code = res.code if res else -1
            dbg(
                line
                % b'  finished in %.4f seconds (%d)'
                % (util.timer() - start, code)
            )

    # Insert error handlers for common I/O failures.
    urlmod.wrapresponse(res)

    return res


class RedirectedRepoError(error.RepoError):
    def __init__(self, msg, respurl):
        super(RedirectedRepoError, self).__init__(msg)
        self.respurl = respurl


def parsev1commandresponse(ui, baseurl, requrl, qs, resp, compressible):
    # record the url we got redirected to
    redirected = False
    respurl = pycompat.bytesurl(resp.geturl())
    if respurl.endswith(qs):
        respurl = respurl[: -len(qs)]
        qsdropped = False
    else:
        qsdropped = True

    if baseurl.rstrip(b'/') != respurl.rstrip(b'/'):
        redirected = True
        if not ui.quiet:
            ui.warn(_(b'real URL is %s\n') % respurl)

    try:
        proto = pycompat.bytesurl(resp.getheader('content-type', ''))
    except AttributeError:
        proto = pycompat.bytesurl(resp.headers.get('content-type', ''))

    safeurl = urlutil.hidepassword(baseurl)
    if proto.startswith(b'application/hg-error'):
        raise error.OutOfBandError(resp.read())

    # Pre 1.0 versions of Mercurial used text/plain and
    # application/hg-changegroup. We don't support such old servers.
    if not proto.startswith(b'application/mercurial-'):
        ui.debug(b"requested URL: '%s'\n" % urlutil.hidepassword(requrl))
        msg = _(
            b"'%s' does not appear to be an hg repository:\n"
            b"---%%<--- (%s)\n%s\n---%%<---\n"
        ) % (safeurl, proto or b'no content-type', resp.read(1024))

        # Some servers may strip the query string from the redirect. We
        # raise a special error type so callers can react to this specially.
        if redirected and qsdropped:
            raise RedirectedRepoError(msg, respurl)
        else:
            raise error.RepoError(msg)

    try:
        subtype = proto.split(b'-', 1)[1]

        version_info = tuple([int(n) for n in subtype.split(b'.')])
    except ValueError:
        raise error.RepoError(
            _(b"'%s' sent a broken Content-Type header (%s)") % (safeurl, proto)
        )

    # TODO consider switching to a decompression reader that uses
    # generators.
    if version_info == (0, 1):
        if compressible:
            resp = util.compengines[b'zlib'].decompressorreader(resp)

    elif version_info == (0, 2):
        # application/mercurial-0.2 always identifies the compression
        # engine in the payload header.
        elen = struct.unpack(b'B', util.readexactly(resp, 1))[0]
        ename = util.readexactly(resp, elen)
        engine = util.compengines.forwiretype(ename)

        resp = engine.decompressorreader(resp)
    else:
        raise error.RepoError(
            _(b"'%s' uses newer protocol %s") % (safeurl, subtype)
        )

    return respurl, proto, resp


class httppeer(wireprotov1peer.wirepeer):
    def __init__(
        self, ui, path, url, opener, requestbuilder, caps, remotehidden=False
    ):
        super().__init__(ui, path=path, remotehidden=remotehidden)
        self._url = url
        self._caps = caps
        self.limitedarguments = caps is not None and b'httppostargs' not in caps
        self._urlopener = opener
        self._requestbuilder = requestbuilder
        self._remotehidden = remotehidden

    def __del__(self):
        for h in self._urlopener.handlers:
            h.close()
            getattr(h, "close_all", lambda: None)()

    # Begin of ipeerconnection interface.

    def url(self):
        return self.path.loc

    def local(self):
        return None

    def canpush(self):
        return True

    def close(self):
        try:
            reqs, sent, recv = (
                self._urlopener.requestscount,
                self._urlopener.sentbytescount,
                self._urlopener.receivedbytescount,
            )
        except AttributeError:
            return
        self.ui.note(
            _(
                b'(sent %d HTTP requests and %d bytes; '
                b'received %d bytes in responses)\n'
            )
            % (reqs, sent, recv)
        )

    # End of ipeerconnection interface.

    # Begin of ipeercommands interface.

    def capabilities(self):
        return self._caps

    def _finish_inline_clone_bundle(self, stream):
        # HTTP streams must hit the end to process the last empty
        # chunk of Chunked-Encoding so the connection can be reused.
        chunk = stream.read(1)
        if chunk:
            self._abort(error.ResponseError(_(b"unexpected response:"), chunk))

    # End of ipeercommands interface.

    def _callstream(self, cmd, _compressible=False, **args):
        args = pycompat.byteskwargs(args)

        req, cu, qs = makev1commandrequest(
            self.ui,
            self._requestbuilder,
            self._caps,
            self.capable,
            self._url,
            cmd,
            args,
            self._remotehidden,
        )

        resp = sendrequest(self.ui, self._urlopener, req)

        self._url, ct, resp = parsev1commandresponse(
            self.ui, self._url, cu, qs, resp, _compressible
        )

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

        types = self.capable(b'unbundle')
        try:
            types = types.split(b',')
        except AttributeError:
            # servers older than d1b16a746db6 will send 'unbundle' as a
            # boolean capability. They only support headerless/uncompressed
            # bundles.
            types = [b""]
        for x in types:
            if x in bundle2.bundletypes:
                type = x
                break

        tempname = bundle2.writebundle(self.ui, cg, None, type)
        fp = httpconnection.httpsendfile(self.ui, tempname, b"rb")
        headers = {'Content-Type': 'application/mercurial-0.1'}

        try:
            r = self._call(cmd, data=fp, headers=headers, **args)
            vals = r.split(b'\n', 1)
            if len(vals) < 2:
                raise error.ResponseError(_(b"unexpected response:"), r)
            return vals
        except urlerr.httperror:
            # Catch and re-raise these so we don't try and treat them
            # like generic socket errors. They lack any values in
            # .args on Python 3 which breaks our socket.error block.
            raise
        except socket.error as err:
            if err.args[0] in (errno.ECONNRESET, errno.EPIPE):
                raise error.Abort(_(b'push failed: %s') % err.args[1])
            raise error.Abort(err.args[1])
        finally:
            fp.close()
            os.unlink(tempname)

    def _calltwowaystream(self, cmd, fp, **args):
        filename = None
        try:
            # dump bundle to disk
            fd, filename = pycompat.mkstemp(prefix=b"hg-bundle-", suffix=b".hg")
            with os.fdopen(fd, "wb") as fh:
                d = fp.read(4096)
                while d:
                    fh.write(d)
                    d = fp.read(4096)
            # start http push
            with httpconnection.httpsendfile(self.ui, filename, b"rb") as fp_:
                headers = {'Content-Type': 'application/mercurial-0.1'}
                return self._callstream(cmd, data=fp_, headers=headers, **args)
        finally:
            if filename is not None:
                os.unlink(filename)

    def _callcompressable(self, cmd, **args):
        return self._callstream(cmd, _compressible=True, **args)

    def _abort(self, exception):
        raise exception


class queuedcommandfuture(futures.Future):
    """Wraps result() on command futures to trigger submission on call."""

    def result(self, timeout=None):
        if self.done():
            return futures.Future.result(self, timeout)

        self._peerexecutor.sendcommands()

        # sendcommands() will restore the original __class__ and self.result
        # will resolve to Future.result.
        return self.result(timeout)


def performhandshake(ui, url, opener, requestbuilder):
    # The handshake is a request to the capabilities command.

    caps = None

    def capable(x):
        raise error.ProgrammingError(b'should not be called')

    args = {}

    req, requrl, qs = makev1commandrequest(
        ui, requestbuilder, caps, capable, url, b'capabilities', args
    )
    resp = sendrequest(ui, opener, req)

    # The server may redirect us to the repo root, stripping the
    # ?cmd=capabilities query string from the URL. The server would likely
    # return HTML in this case and ``parsev1commandresponse()`` would raise.
    # We catch this special case and re-issue the capabilities request against
    # the new URL.
    #
    # We should ideally not do this, as a redirect that drops the query
    # string from the URL is arguably a server bug. (Garbage in, garbage out).
    # However,  Mercurial clients for several years appeared to handle this
    # issue without behavior degradation. And according to issue 5860, it may
    # be a longstanding bug in some server implementations. So we allow a
    # redirect that drops the query string to "just work."
    try:
        respurl, ct, resp = parsev1commandresponse(
            ui, url, requrl, qs, resp, compressible=False
        )
    except RedirectedRepoError as e:
        req, requrl, qs = makev1commandrequest(
            ui, requestbuilder, caps, capable, e.respurl, b'capabilities', args
        )
        resp = sendrequest(ui, opener, req)
        respurl, ct, resp = parsev1commandresponse(
            ui, url, requrl, qs, resp, compressible=False
        )

    try:
        rawdata = resp.read()
    finally:
        resp.close()

    if not ct.startswith(b'application/mercurial-'):
        raise error.ProgrammingError(b'unexpected content-type: %s' % ct)

    info = {b'v1capabilities': set(rawdata.split())}

    return respurl, info


def _make_peer(
    ui, path, opener=None, requestbuilder=urlreq.request, remotehidden=False
):
    """Construct an appropriate HTTP peer instance.

    ``opener`` is an ``url.opener`` that should be used to establish
    connections, perform HTTP requests.

    ``requestbuilder`` is the type used for constructing HTTP requests.
    It exists as an argument so extensions can override the default.
    """
    if path.url.query or path.url.fragment:
        msg = _(b'unsupported URL component: "%s"')
        msg %= path.url.query or path.url.fragment
        raise error.Abort(msg)

    # urllib cannot handle URLs with embedded user or passwd.
    url, authinfo = path.url.authinfo()
    ui.debug(b'using %s\n' % url)

    opener = opener or urlmod.opener(ui, authinfo)

    respurl, info = performhandshake(ui, url, opener, requestbuilder)

    return httppeer(
        ui,
        path,
        respurl,
        opener,
        requestbuilder,
        info[b'v1capabilities'],
        remotehidden=remotehidden,
    )


def make_peer(
    ui, path, create, intents=None, createopts=None, remotehidden=False
):
    if create:
        raise error.Abort(_(b'cannot create new http repository'))
    try:
        if path.url.scheme == b'https' and not urlmod.has_https:
            raise error.Abort(
                _(b'Python support for SSL and HTTPS is not installed')
            )

        inst = _make_peer(ui, path, remotehidden=remotehidden)

        return inst
    except error.RepoError as httpexception:
        try:
            path = path.copy(new_raw_location=b"static-" + path.rawloc)
            r = statichttprepo.make_peer(ui, path, create)
            ui.note(_(b'(falling back to static-http)\n'))
            return r
        except error.RepoError:
            raise httpexception  # use the original http RepoError instead
