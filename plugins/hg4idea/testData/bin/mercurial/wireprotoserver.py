# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

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
    wireprotov2server,
)
from .interfaces import util as interfaceutil
from .utils import (
    cborutil,
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
SSHV2 = wireprototypes.SSHV2


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
class httpv1protocolhandler(object):
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


def _availableapis(repo):
    apis = set()

    # Registered APIs are made available via config options of the name of
    # the protocol.
    for k, v in API_HANDLERS.items():
        section, option = v[b'config']
        if repo.ui.configbool(section, option):
            apis.add(k)

    return apis


def handlewsgiapirequest(rctx, req, res, checkperm):
    """Handle requests to /api/*."""
    assert req.dispatchparts[0] == b'api'

    repo = rctx.repo

    # This whole URL space is experimental for now. But we want to
    # reserve the URL space. So, 404 all URLs if the feature isn't enabled.
    if not repo.ui.configbool(b'experimental', b'web.apiserver'):
        res.status = b'404 Not Found'
        res.headers[b'Content-Type'] = b'text/plain'
        res.setbodybytes(_(b'Experimental API server endpoint not enabled'))
        return

    # The URL space is /api/<protocol>/*. The structure of URLs under varies
    # by <protocol>.

    availableapis = _availableapis(repo)

    # Requests to /api/ list available APIs.
    if req.dispatchparts == [b'api']:
        res.status = b'200 OK'
        res.headers[b'Content-Type'] = b'text/plain'
        lines = [
            _(
                b'APIs can be accessed at /api/<name>, where <name> can be '
                b'one of the following:\n'
            )
        ]
        if availableapis:
            lines.extend(sorted(availableapis))
        else:
            lines.append(_(b'(no available APIs)\n'))
        res.setbodybytes(b'\n'.join(lines))
        return

    proto = req.dispatchparts[1]

    if proto not in API_HANDLERS:
        res.status = b'404 Not Found'
        res.headers[b'Content-Type'] = b'text/plain'
        res.setbodybytes(
            _(b'Unknown API: %s\nKnown APIs: %s')
            % (proto, b', '.join(sorted(availableapis)))
        )
        return

    if proto not in availableapis:
        res.status = b'404 Not Found'
        res.headers[b'Content-Type'] = b'text/plain'
        res.setbodybytes(_(b'API %s not enabled\n') % proto)
        return

    API_HANDLERS[proto][b'handler'](
        rctx, req, res, checkperm, req.dispatchparts[2:]
    )


# Maps API name to metadata so custom API can be registered.
# Keys are:
#
# config
#    Config option that controls whether service is enabled.
# handler
#    Callable receiving (rctx, req, res, checkperm, urlparts) that is called
#    when a request to this API is received.
# apidescriptor
#    Callable receiving (req, repo) that is called to obtain an API
#    descriptor for this service. The response must be serializable to CBOR.
API_HANDLERS = {
    wireprotov2server.HTTP_WIREPROTO_V2: {
        b'config': (b'experimental', b'web.api.http-v2'),
        b'handler': wireprotov2server.handlehttpv2request,
        b'apidescriptor': wireprotov2server.httpv2apidescriptor,
    },
}


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


def processcapabilitieshandshake(repo, req, res, proto):
    """Called during a ?cmd=capabilities request.

    If the client is advertising support for a newer protocol, we send
    a CBOR response with information about available services. If no
    advertised services are available, we don't handle the request.
    """
    # Fall back to old behavior unless the API server is enabled.
    if not repo.ui.configbool(b'experimental', b'web.apiserver'):
        return False

    clientapis = decodevaluefromheaders(req, b'X-HgUpgrade')
    protocaps = decodevaluefromheaders(req, b'X-HgProto')
    if not clientapis or not protocaps:
        return False

    # We currently only support CBOR responses.
    protocaps = set(protocaps.split(b' '))
    if b'cbor' not in protocaps:
        return False

    descriptors = {}

    for api in sorted(set(clientapis.split()) & _availableapis(repo)):
        handler = API_HANDLERS[api]

        descriptorfn = handler.get(b'apidescriptor')
        if not descriptorfn:
            continue

        descriptors[api] = descriptorfn(req, repo)

    v1caps = wireprotov1server.dispatch(repo, proto, b'capabilities')
    assert isinstance(v1caps, wireprototypes.bytesresponse)

    m = {
        # TODO allow this to be configurable.
        b'apibase': b'api/',
        b'apis': descriptors,
        b'v1capabilities': v1caps.data,
    }

    res.status = b'200 OK'
    res.headers[b'Content-Type'] = b'application/mercurial-cbor'
    res.setbodybytes(b''.join(cborutil.streamencode(m)))

    return True


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

    # Possibly handle a modern client wanting to switch protocols.
    if cmd == b'capabilities' and processcapabilitieshandshake(
        repo, req, res, proto
    ):

        return

    rsp = wireprotov1server.dispatch(repo, proto, cmd)

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
class sshv1protocolhandler(object):
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
        for n in pycompat.xrange(len(keys)):
            argline = self._fin.readline()[:-1]
            arg, l = argline.split()
            if arg not in keys:
                raise error.Abort(_(b"unexpected parameter %r") % arg)
            if arg == b'*':
                star = {}
                for k in pycompat.xrange(int(l)):
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


class sshv2protocolhandler(sshv1protocolhandler):
    """Protocol handler for version 2 of the SSH protocol."""

    @property
    def name(self):
        return wireprototypes.SSHV2

    def addcapabilities(self, repo, caps):
        return caps


def _runsshserver(ui, repo, fin, fout, ev):
    # This function operates like a state machine of sorts. The following
    # states are defined:
    #
    # protov1-serving
    #    Server is in protocol version 1 serving mode. Commands arrive on
    #    new lines. These commands are processed in this state, one command
    #    after the other.
    #
    # protov2-serving
    #    Server is in protocol version 2 serving mode.
    #
    # upgrade-initial
    #    The server is going to process an upgrade request.
    #
    # upgrade-v2-filter-legacy-handshake
    #    The protocol is being upgraded to version 2. The server is expecting
    #    the legacy handshake from version 1.
    #
    # upgrade-v2-finish
    #    The upgrade to version 2 of the protocol is imminent.
    #
    # shutdown
    #    The server is shutting down, possibly in reaction to a client event.
    #
    # And here are their transitions:
    #
    # protov1-serving -> shutdown
    #    When server receives an empty request or encounters another
    #    error.
    #
    # protov1-serving -> upgrade-initial
    #    An upgrade request line was seen.
    #
    # upgrade-initial -> upgrade-v2-filter-legacy-handshake
    #    Upgrade to version 2 in progress. Server is expecting to
    #    process a legacy handshake.
    #
    # upgrade-v2-filter-legacy-handshake -> shutdown
    #    Client did not fulfill upgrade handshake requirements.
    #
    # upgrade-v2-filter-legacy-handshake -> upgrade-v2-finish
    #    Client fulfilled version 2 upgrade requirements. Finishing that
    #    upgrade.
    #
    # upgrade-v2-finish -> protov2-serving
    #    Protocol upgrade to version 2 complete. Server can now speak protocol
    #    version 2.
    #
    # protov2-serving -> protov1-serving
    #    Ths happens by default since protocol version 2 is the same as
    #    version 1 except for the handshake.

    state = b'protov1-serving'
    proto = sshv1protocolhandler(ui, fin, fout)
    protoswitched = False

    while not ev.is_set():
        if state == b'protov1-serving':
            # Commands are issued on new lines.
            request = fin.readline()[:-1]

            # Empty lines signal to terminate the connection.
            if not request:
                state = b'shutdown'
                continue

            # It looks like a protocol upgrade request. Transition state to
            # handle it.
            if request.startswith(b'upgrade '):
                if protoswitched:
                    _sshv1respondooberror(
                        fout,
                        ui.ferr,
                        b'cannot upgrade protocols multiple times',
                    )
                    state = b'shutdown'
                    continue

                state = b'upgrade-initial'
                continue

            available = wireprotov1server.commands.commandavailable(
                request, proto
            )

            # This command isn't available. Send an empty response and go
            # back to waiting for a new command.
            if not available:
                _sshv1respondbytes(fout, b'')
                continue

            rsp = wireprotov1server.dispatch(repo, proto, request)
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

        # For now, protocol version 2 serving just goes back to version 1.
        elif state == b'protov2-serving':
            state = b'protov1-serving'
            continue

        elif state == b'upgrade-initial':
            # We should never transition into this state if we've switched
            # protocols.
            assert not protoswitched
            assert proto.name == wireprototypes.SSHV1

            # Expected: upgrade <token> <capabilities>
            # If we get something else, the request is malformed. It could be
            # from a future client that has altered the upgrade line content.
            # We treat this as an unknown command.
            try:
                token, caps = request.split(b' ')[1:]
            except ValueError:
                _sshv1respondbytes(fout, b'')
                state = b'protov1-serving'
                continue

            # Send empty response if we don't support upgrading protocols.
            if not ui.configbool(b'experimental', b'sshserver.support-v2'):
                _sshv1respondbytes(fout, b'')
                state = b'protov1-serving'
                continue

            try:
                caps = urlreq.parseqs(caps)
            except ValueError:
                _sshv1respondbytes(fout, b'')
                state = b'protov1-serving'
                continue

            # We don't see an upgrade request to protocol version 2. Ignore
            # the upgrade request.
            wantedprotos = caps.get(b'proto', [b''])[0]
            if SSHV2 not in wantedprotos:
                _sshv1respondbytes(fout, b'')
                state = b'protov1-serving'
                continue

            # It looks like we can honor this upgrade request to protocol 2.
            # Filter the rest of the handshake protocol request lines.
            state = b'upgrade-v2-filter-legacy-handshake'
            continue

        elif state == b'upgrade-v2-filter-legacy-handshake':
            # Client should have sent legacy handshake after an ``upgrade``
            # request. Expected lines:
            #
            #    hello
            #    between
            #    pairs 81
            #    0000...-0000...

            ok = True
            for line in (b'hello', b'between', b'pairs 81'):
                request = fin.readline()[:-1]

                if request != line:
                    _sshv1respondooberror(
                        fout,
                        ui.ferr,
                        b'malformed handshake protocol: missing %s' % line,
                    )
                    ok = False
                    state = b'shutdown'
                    break

            if not ok:
                continue

            request = fin.read(81)
            if request != b'%s-%s' % (b'0' * 40, b'0' * 40):
                _sshv1respondooberror(
                    fout,
                    ui.ferr,
                    b'malformed handshake protocol: '
                    b'missing between argument value',
                )
                state = b'shutdown'
                continue

            state = b'upgrade-v2-finish'
            continue

        elif state == b'upgrade-v2-finish':
            # Send the upgrade response.
            fout.write(b'upgraded %s %s\n' % (token, SSHV2))
            servercaps = wireprotov1server.capabilities(repo, proto)
            rsp = b'capabilities: %s' % servercaps.data
            fout.write(b'%d\n%s\n' % (len(rsp), rsp))
            fout.flush()

            proto = sshv2protocolhandler(ui, fin, fout)
            protoswitched = True

            state = b'protov2-serving'
            continue

        elif state == b'shutdown':
            break

        else:
            raise error.ProgrammingError(
                b'unhandled ssh server state: %s' % state
            )


class sshserver(object):
    def __init__(self, ui, repo, logfh=None):
        self._ui = ui
        self._repo = repo
        self._fin, self._fout = ui.protectfinout()

        # Log write I/O to stdout and stderr if configured.
        if logfh:
            self._fout = util.makeloggingfileobject(
                logfh, self._fout, b'o', logdata=True
            )
            ui.ferr = util.makeloggingfileobject(
                logfh, ui.ferr, b'e', logdata=True
            )

    def serve_forever(self):
        self.serveuntil(threading.Event())
        self._ui.restorefinout(self._fin, self._fout)

    def serveuntil(self, ev):
        """Serve until a threading.Event is set."""
        _runsshserver(self._ui, self._repo, self._fin, self._fout, ev)
