# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import contextlib
import struct
import threading

from .i18n import _
from . import (
    encoding,
    error,
    pycompat,
    util,
    wireprototypes,
    wireprotov1server,
)
from .interfaces import util as interfaceutil
from .utils import (
    compression,
    stringutil,
)

stringio = util.stringio

urlerr = util.urlerr
urlreq = util.urlreq

HTTP_OK = 200

HGTYPE = b'application/mercurial-0.1'
HGTYPE2 = b'application/mercurial-0.2'
HGERRTYPE = b'application/hg-error'

SSHV1 = wireprototypes.SSHV1


def decodevaluefromheaders(req, headerprefix):
    """Decode a long value from multiple HTTP request headers.

    Returns the value as a bytes, not a str.
    """
    chunks = []
    i = 1
    while True:
        v = req.headers.get(b'%s-%d' % (headerprefix, i))
        if v is None:
            break
        chunks.append(pycompat.bytesurl(v))
        i += 1

    return b''.join(chunks)


@interfaceutil.implementer(wireprototypes.baseprotocolhandler)
class httpv1protocolhandler:
    def __init__(self, req, ui, checkperm):
        self._req = req
        self._ui = ui
        self._checkperm = checkperm
        self._protocaps = None

    @property
    def name(self):
        return b'http-v1'

    def getargs(self, args):
        knownargs = self._args()
        data = {}
        keys = args.split()
        for k in keys:
            if k == b'*':
                star = {}
                for key in knownargs.keys():
                    if key != b'cmd' and key not in keys:
                        star[key] = knownargs[key][0]
                data[b'*'] = star
            else:
                data[k] = knownargs[k][0]
        return [data[k] for k in keys]

    def _args(self):
        args = self._req.qsparams.asdictoflists()
        postlen = int(self._req.headers.get(b'X-HgArgs-Post', 0))
        if postlen:
            args.update(
                urlreq.parseqs(
                    self._req.bodyfh.read(postlen), keep_blank_values=True
                )
            )
            return args

        argvalue = decodevaluefromheaders(self._req, b'X-HgArg')
        args.update(urlreq.parseqs(argvalue, keep_blank_values=True))
        return args

    def getprotocaps(self):
        if self._protocaps is None:
            value = decodevaluefromheaders(self._req, b'X-HgProto')
            self._protocaps = set(value.split(b' '))
        return self._protocaps

    def getpayload(self):
        # Existing clients *always* send Content-Length.
        length = int(self._req.headers[b'Content-Length'])

        # If httppostargs is used, we need to read Content-Length
        # minus the amount that was consumed by args.
        length -= int(self._req.headers.get(b'X-HgArgs-Post', 0))
        return util.filechunkiter(self._req.bodyfh, limit=length)

    @contextlib.contextmanager
    def mayberedirectstdio(self):
        oldout = self._ui.fout
        olderr = self._ui.ferr

        out = util.stringio()

        try:
            self._ui.fout = out
            self._ui.ferr = out
            yield out
        finally:
            self._ui.fout = oldout
            self._ui.ferr = olderr

    def client(self):
        return b'remote:%s:%s:%s' % (
            self._req.urlscheme,
            urlreq.quote(self._req.remotehost or b''),
            urlreq.quote(self._req.remoteuser or b''),
        )

    def addcapabilities(self, repo, caps):
        caps.append(b'batch')

        caps.append(
            b'httpheader=%d' % repo.ui.configint(b'server', b'maxhttpheaderlen')
        )
        if repo.ui.configbool(b'experimental', b'httppostargs'):
            caps.append(b'httppostargs')

        # FUTURE advertise 0.2rx once support is implemented
        # FUTURE advertise minrx and mintx after consulting config option
        caps.append(b'httpmediatype=0.1rx,0.1tx,0.2tx')

        compengines = wireprototypes.supportedcompengines(
            repo.ui, compression.SERVERROLE
        )
        if compengines:
            comptypes = b','.join(
                urlreq.quote(e.wireprotosupport().name) for e in compengines
            )
            caps.append(b'compression=%s' % comptypes)

        return caps

    def checkperm(self, perm):
        return self._checkperm(perm)


# This method exists mostly so that extensions like remotefilelog can
# disable a kludgey legacy method only over http. As of early 2018,
# there are no other known users, so with any luck we can discard this
# hook if remotefilelog becomes a first-party extension.
def iscmd(cmd):
    return cmd in wireprotov1server.commands


def handlewsgirequest(rctx, req, res, checkperm):
    """Possibly process a wire protocol request.

    If the current request is a wire protocol request, the request is
    processed by this function.

    ``req`` is a ``parsedrequest`` instance.
    ``res`` is a ``wsgiresponse`` instance.

    Returns a bool indicating if the request was serviced. If set, the caller
    should stop processing the request, as a response has already been issued.
    """
    # Avoid cycle involving hg module.
    from .hgweb import common as hgwebcommon

    repo = rctx.repo

    # HTTP version 1 wire protocol requests are denoted by a "cmd" query
    # string parameter. If it isn't present, this isn't a wire protocol
    # request.
    if b'cmd' not in req.qsparams:
        return False

    cmd = req.qsparams[b'cmd']

    # The "cmd" request parameter is used by both the wire protocol and hgweb.
    # While not all wire protocol commands are available for all transports,
    # if we see a "cmd" value that resembles a known wire protocol command, we
    # route it to a protocol handler. This is better than routing possible
    # wire protocol requests to hgweb because it prevents hgweb from using
    # known wire protocol commands and it is less confusing for machine
    # clients.
    if not iscmd(cmd):
        return False

    # The "cmd" query string argument is only valid on the root path of the
    # repo. e.g. ``/?cmd=foo``, ``/repo?cmd=foo``. URL paths within the repo
    # like ``/blah?cmd=foo`` are not allowed. So don't recognize the request
    # in this case. We send an HTTP 404 for backwards compatibility reasons.
    if req.dispatchpath:
        res.status = hgwebcommon.statusmessage(404)
        res.headers[b'Content-Type'] = HGTYPE
        # TODO This is not a good response to issue for this request. This
        # is mostly for BC for now.
        res.setbodybytes(b'0\n%s\n' % b'Not Found')
        return True

    proto = httpv1protocolhandler(
        req, repo.ui, lambda perm: checkperm(rctx, req, perm)
    )

    # The permissions checker should be the only thing that can raise an
    # ErrorResponse. It is kind of a layer violation to catch an hgweb
    # exception here. So consider refactoring into a exception type that
    # is associated with the wire protocol.
    try:
        _callhttp(repo, req, res, proto, cmd)
    except hgwebcommon.ErrorResponse as e:
        for k, v in e.headers:
            res.headers[k] = v
        res.status = hgwebcommon.statusmessage(
            e.code, stringutil.forcebytestr(e)
        )
        # TODO This response body assumes the failed command was
        # "unbundle." That assumption is not always valid.
        res.setbodybytes(b'0\n%s\n' % stringutil.forcebytestr(e))

    return True


def _httpresponsetype(ui, proto, prefer_uncompressed):
    """Determine the appropriate response type and compression settings.

    Returns a tuple of (mediatype, compengine, engineopts).
    """
    # Determine the response media type and compression engine based
    # on the request parameters.

    if b'0.2' in proto.getprotocaps():
        # All clients are expected to support uncompressed data.
        if prefer_uncompressed:
            return HGTYPE2, compression._noopengine(), {}

        # Now find an agreed upon compression format.
        compformats = wireprotov1server.clientcompressionsupport(proto)
        for engine in wireprototypes.supportedcompengines(
            ui, compression.SERVERROLE
        ):
            if engine.wireprotosupport().name in compformats:
                opts = {}
                level = ui.configint(b'server', b'%slevel' % engine.name())
                if level is not None:
                    opts[b'level'] = level

                return HGTYPE2, engine, opts

        # No mutually supported compression format. Fall back to the
        # legacy protocol.

    # Don't allow untrusted settings because disabling compression or
    # setting a very high compression level could lead to flooding
    # the server's network or CPU.
    opts = {b'level': ui.configint(b'server', b'zliblevel')}
    return HGTYPE, util.compengines[b'zlib'], opts


def _callhttp(repo, req, res, proto, cmd):
    # Avoid cycle involving hg module.
    from .hgweb import common as hgwebcommon

    def genversion2(gen, engine, engineopts):
        # application/mercurial-0.2 always sends a payload header
        # identifying the compression engine.
        name = engine.wireprotosupport().name
        assert 0 < len(name) < 256
        yield struct.pack(b'B', len(name))
        yield name

        for chunk in gen:
            yield chunk

    def setresponse(code, contenttype, bodybytes=None, bodygen=None):
        if code == HTTP_OK:
            res.status = b'200 Script output follows'
        else:
            res.status = hgwebcommon.statusmessage(code)

        res.headers[b'Content-Type'] = contenttype

        if bodybytes is not None:
            res.setbodybytes(bodybytes)
        if bodygen is not None:
            res.setbodygen(bodygen)

    if not wireprotov1server.commands.commandavailable(cmd, proto):
        setresponse(
            HTTP_OK,
            HGERRTYPE,
            _(
                b'requested wire protocol command is not available over '
                b'HTTP'
            ),
        )
        return

    proto.checkperm(wireprotov1server.commands[cmd].permission)

    accesshidden = hgwebcommon.hashiddenaccess(repo, req)
    rsp = wireprotov1server.dispatch(repo, proto, cmd, accesshidden)

    if isinstance(rsp, bytes):
        setresponse(HTTP_OK, HGTYPE, bodybytes=rsp)
    elif isinstance(rsp, wireprototypes.bytesresponse):
        setresponse(HTTP_OK, HGTYPE, bodybytes=rsp.data)
    elif isinstance(rsp, wireprototypes.streamreslegacy):
        setresponse(HTTP_OK, HGTYPE, bodygen=rsp.gen)
    elif isinstance(rsp, wireprototypes.streamres):
        gen = rsp.gen

        # This code for compression should not be streamres specific. It
        # is here because we only compress streamres at the moment.
        mediatype, engine, engineopts = _httpresponsetype(
            repo.ui, proto, rsp.prefer_uncompressed
        )
        gen = engine.compressstream(gen, engineopts)

        if mediatype == HGTYPE2:
            gen = genversion2(gen, engine, engineopts)

        setresponse(HTTP_OK, mediatype, bodygen=gen)
    elif isinstance(rsp, wireprototypes.pushres):
        rsp = b'%d\n%s' % (rsp.res, rsp.output)
        setresponse(HTTP_OK, HGTYPE, bodybytes=rsp)
    elif isinstance(rsp, wireprototypes.pusherr):
        rsp = b'0\n%s\n' % rsp.res
        res.drain = True
        setresponse(HTTP_OK, HGTYPE, bodybytes=rsp)
    elif isinstance(rsp, wireprototypes.ooberror):
        setresponse(HTTP_OK, HGERRTYPE, bodybytes=rsp.message)
    else:
        raise error.ProgrammingError(b'hgweb.protocol internal failure', rsp)


def _sshv1respondbytes(fout, value):
    """Send a bytes response for protocol version 1."""
    fout.write(b'%d\n' % len(value))
    fout.write(value)
    fout.flush()


def _sshv1respondstream(fout, source):
    write = fout.write
    for chunk in source.gen:
        write(chunk)
    fout.flush()


def _sshv1respondooberror(fout, ferr, rsp):
    ferr.write(b'%s\n-\n' % rsp)
    ferr.flush()
    fout.write(b'\n')
    fout.flush()


@interfaceutil.implementer(wireprototypes.baseprotocolhandler)
class sshv1protocolhandler:
    """Handler for requests services via version 1 of SSH protocol."""

    def __init__(self, ui, fin, fout):
        self._ui = ui
        self._fin = fin
        self._fout = fout
        self._protocaps = set()

    @property
    def name(self):
        return wireprototypes.SSHV1

    def getargs(self, args):
        data = {}
        keys = args.split()
        for n in range(len(keys)):
            argline = self._fin.readline()[:-1]
            arg, l = argline.split()
            if arg not in keys:
                raise error.Abort(_(b"unexpected parameter %r") % arg)
            if arg == b'*':
                star = {}
                for k in range(int(l)):
                    argline = self._fin.readline()[:-1]
                    arg, l = argline.split()
                    val = self._fin.read(int(l))
                    star[arg] = val
                data[b'*'] = star
            else:
                val = self._fin.read(int(l))
                data[arg] = val
        return [data[k] for k in keys]

    def getprotocaps(self):
        return self._protocaps

    def getpayload(self):
        # We initially send an empty response. This tells the client it is
        # OK to start sending data. If a client sees any other response, it
        # interprets it as an error.
        _sshv1respondbytes(self._fout, b'')

        # The file is in the form:
        #
        # <chunk size>\n<chunk>
        # ...
        # 0\n
        count = int(self._fin.readline())
        while count:
            yield self._fin.read(count)
            count = int(self._fin.readline())

    @contextlib.contextmanager
    def mayberedirectstdio(self):
        yield None

    def client(self):
        client = encoding.environ.get(b'SSH_CLIENT', b'').split(b' ', 1)[0]
        return b'remote:ssh:' + client

    def addcapabilities(self, repo, caps):
        if self.name == wireprototypes.SSHV1:
            caps.append(b'protocaps')
        caps.append(b'batch')
        return caps

    def checkperm(self, perm):
        pass


def _runsshserver(ui, repo, fin, fout, ev, accesshidden=False):
    # This function operates like a state machine of sorts. The following
    # states are defined:
    #
    # protov1-serving
    #    Server is in protocol version 1 serving mode. Commands arrive on
    #    new lines. These commands are processed in this state, one command
    #    after the other.
    #
    # shutdown
    #    The server is shutting down, possibly in reaction to a client event.
    #
    # And here are their transitions:
    #
    # protov1-serving -> shutdown
    #    When server receives an empty request or encounters another
    #    error.

    state = b'protov1-serving'
    proto = sshv1protocolhandler(ui, fin, fout)

    while not ev.is_set():
        if state == b'protov1-serving':
            # Commands are issued on new lines.
            request = fin.readline()[:-1]

            # Empty lines signal to terminate the connection.
            if not request:
                state = b'shutdown'
                continue

            available = wireprotov1server.commands.commandavailable(
                request, proto
            )

            # This command isn't available. Send an empty response and go
            # back to waiting for a new command.
            if not available:
                _sshv1respondbytes(fout, b'')
                continue

            rsp = wireprotov1server.dispatch(
                repo, proto, request, accesshidden=accesshidden
            )
            repo.ui.fout.flush()
            repo.ui.ferr.flush()

            if isinstance(rsp, bytes):
                _sshv1respondbytes(fout, rsp)
            elif isinstance(rsp, wireprototypes.bytesresponse):
                _sshv1respondbytes(fout, rsp.data)
            elif isinstance(rsp, wireprototypes.streamres):
                _sshv1respondstream(fout, rsp)
            elif isinstance(rsp, wireprototypes.streamreslegacy):
                _sshv1respondstream(fout, rsp)
            elif isinstance(rsp, wireprototypes.pushres):
                _sshv1respondbytes(fout, b'')
                _sshv1respondbytes(fout, b'%d' % rsp.res)
            elif isinstance(rsp, wireprototypes.pusherr):
                _sshv1respondbytes(fout, rsp.res)
            elif isinstance(rsp, wireprototypes.ooberror):
                _sshv1respondooberror(fout, ui.ferr, rsp.message)
            else:
                raise error.ProgrammingError(
                    b'unhandled response type from '
                    b'wire protocol command: %s' % rsp
                )

        elif state == b'shutdown':
            break

        else:
            raise error.ProgrammingError(
                b'unhandled ssh server state: %s' % state
            )


class sshserver:
    def __init__(self, ui, repo, logfh=None, accesshidden=False):
        self._ui = ui
        self._repo = repo
        self._accesshidden = accesshidden
        self._logfh = logfh

    def serve_forever(self):
        self.serveuntil(threading.Event())

    def serveuntil(self, ev):
        """Serve until a threading.Event is set."""
        with self._ui.protectedfinout() as (fin, fout):
            if self._logfh:
                # Log write I/O to stdout and stderr if configured.
                fout = util.makeloggingfileobject(
                    self._logfh,
                    fout,
                    b'o',
                    logdata=True,
                )
                self._ui.ferr = util.makeloggingfileobject(
                    self._logfh,
                    self._ui.ferr,
                    b'e',
                    logdata=True,
                )
            _runsshserver(
                self._ui,
                self._repo,
                fin,
                fout,
                ev,
                self._accesshidden,
            )
