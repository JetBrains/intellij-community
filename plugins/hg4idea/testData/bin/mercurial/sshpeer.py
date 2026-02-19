# sshpeer.py - ssh repository proxy class for mercurial
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import re
import uuid

from .i18n import _
from . import (
    error,
    pycompat,
    util,
    wireprototypes,
    wireprotov1peer,
    wireprotov1server,
)
from .utils import (
    procutil,
    stringutil,
    urlutil,
)


def _serverquote(s):
    """quote a string for the remote shell ... which we assume is sh"""
    if not s:
        return s
    if re.match(b'[a-zA-Z0-9@%_+=:,./-]*$', s):
        return s
    return b"'%s'" % s.replace(b"'", b"'\\''")


def _forwardoutput(ui, pipe, warn=False):
    """display all data currently available on pipe as remote output.

    This is non blocking."""
    if pipe and not pipe.closed:
        s = procutil.readpipe(pipe)
        if s:
            display = ui.warn if warn else ui.status
            for l in s.splitlines():
                display(_(b"remote: "), l, b'\n')


class doublepipe:
    """Operate a side-channel pipe in addition of a main one

    The side-channel pipe contains server output to be forwarded to the user
    input. The double pipe will behave as the "main" pipe, but will ensure the
    content of the "side" pipe is properly processed while we wait for blocking
    call on the "main" pipe.

    If large amounts of data are read from "main", the forward will cease after
    the first bytes start to appear. This simplifies the implementation
    without affecting actual output of sshpeer too much as we rarely issue
    large read for data not yet emitted by the server.

    The main pipe is expected to be a 'bufferedinputpipe' from the util module
    that handle all the os specific bits. This class lives in this module
    because it focus on behavior specific to the ssh protocol."""

    def __init__(self, ui, main, side):
        self._ui = ui
        self._main = main
        self._side = side

    def _wait(self):
        """wait until some data are available on main or side

        return a pair of boolean (ismainready, issideready)

        (This will only wait for data if the setup is supported by `util.poll`)
        """
        if (
            isinstance(self._main, util.bufferedinputpipe)
            and self._main.hasbuffer
        ):
            # Main has data. Assume side is worth poking at.
            return True, True

        fds = [self._main.fileno(), self._side.fileno()]
        try:
            act = util.poll(fds)
        except NotImplementedError:
            # non supported yet case, assume all have data.
            act = fds
        return (self._main.fileno() in act, self._side.fileno() in act)

    def write(self, data):
        return self._call(b'write', data)

    def read(self, size):
        r = self._call(b'read', size)
        if size != 0 and not r:
            # We've observed a condition that indicates the
            # stdout closed unexpectedly. Check stderr one
            # more time and snag anything that's there before
            # letting anyone know the main part of the pipe
            # closed prematurely.
            _forwardoutput(self._ui, self._side)
        return r

    def unbufferedread(self, size):
        r = self._call(b'unbufferedread', size)
        if size != 0 and not r:
            # We've observed a condition that indicates the
            # stdout closed unexpectedly. Check stderr one
            # more time and snag anything that's there before
            # letting anyone know the main part of the pipe
            # closed prematurely.
            _forwardoutput(self._ui, self._side)
        return r

    def readline(self):
        return self._call(b'readline')

    def _call(self, methname, data=None):
        """call <methname> on "main", forward output of "side" while blocking"""
        # data can be '' or 0
        if (data is not None and not data) or self._main.closed:
            _forwardoutput(self._ui, self._side)
            return b''
        while True:
            mainready, sideready = self._wait()
            if sideready:
                _forwardoutput(self._ui, self._side)
            if mainready:
                meth = getattr(self._main, pycompat.sysstr(methname))
                if data is None:
                    return meth()
                else:
                    return meth(data)

    def close(self):
        return self._main.close()

    @property
    def closed(self):
        return self._main.closed

    def flush(self):
        return self._main.flush()


def _cleanuppipes(ui, pipei, pipeo, pipee, warn):
    """Clean up pipes used by an SSH connection."""
    didsomething = False
    if pipeo and not pipeo.closed:
        didsomething = True
        pipeo.close()
    if pipei and not pipei.closed:
        didsomething = True
        pipei.close()

    if pipee and not pipee.closed:
        didsomething = True
        # Try to read from the err descriptor until EOF.
        try:
            for l in pipee:
                ui.status(_(b'remote: '), l)
        except (IOError, ValueError):
            pass

        pipee.close()

    if didsomething and warn is not None:
        # Encourage explicit close of sshpeers. Closing via __del__ is
        # not very predictable when exceptions are thrown, which has led
        # to deadlocks due to a peer get gc'ed in a fork
        # We add our own stack trace, because the stacktrace when called
        # from __del__ is useless.
        ui.develwarn(b'missing close on SSH connection created at:\n%s' % warn)


def _makeconnection(
    ui, sshcmd, args, remotecmd, path, sshenv=None, remotehidden=False
):
    """Create an SSH connection to a server.

    Returns a tuple of (process, stdin, stdout, stderr) for the
    spawned process.
    """
    cmd = b'%s %s %s' % (
        sshcmd,
        args,
        procutil.shellquote(
            b'%s -R %s serve --stdio%s'
            % (
                _serverquote(remotecmd),
                _serverquote(path),
                b' --hidden' if remotehidden else b'',
            )
        ),
    )

    ui.debug(b'running %s\n' % cmd)

    # no buffer allow the use of 'select'
    # feel free to remove buffering and select usage when we ultimately
    # move to threading.
    stdin, stdout, stderr, proc = procutil.popen4(cmd, bufsize=0, env=sshenv)

    return proc, stdin, stdout, stderr


def _clientcapabilities():
    """Return list of capabilities of this client.

    Returns a list of capabilities that are supported by this client.
    """
    protoparams = {b'partial-pull'}
    comps = [
        e.wireprotosupport().name
        for e in util.compengines.supportedwireengines(util.CLIENTROLE)
    ]
    protoparams.add(b'comp=%s' % b','.join(comps))
    return protoparams


def _performhandshake(ui, stdin, stdout, stderr):
    def badresponse():
        # Flush any output on stderr. In general, the stderr contains errors
        # from the remote (ssh errors, some hg errors), and status indications
        # (like "adding changes"), with no current way to tell them apart.
        # Here we failed so early that it's almost certainly only errors, so
        # use warn=True so -q doesn't hide them.
        _forwardoutput(ui, stderr, warn=True)

        msg = _(b'no suitable response from remote hg')
        hint = ui.config(b'ui', b'ssherrorhint')
        raise error.RepoError(msg, hint=hint)

    # The handshake consists of sending wire protocol commands in reverse
    # order of protocol implementation and then sniffing for a response
    # to one of them.
    #
    # Those commands (from oldest to newest) are:
    #
    # ``between``
    #   Asks for the set of revisions between a pair of revisions. Command
    #   present in all Mercurial server implementations.
    #
    # ``hello``
    #   Instructs the server to advertise its capabilities. Introduced in
    #   Mercurial 0.9.1.
    #
    # ``upgrade``
    #   Requests upgrade from default transport protocol version 1 to
    #   a newer version. Introduced in Mercurial 4.6 as an experimental
    #   feature.
    #
    # The ``between`` command is issued with a request for the null
    # range. If the remote is a Mercurial server, this request will
    # generate a specific response: ``1\n\n``. This represents the
    # wire protocol encoded value for ``\n``. We look for ``1\n\n``
    # in the output stream and know this is the response to ``between``
    # and we're at the end of our handshake reply.
    #
    # The response to the ``hello`` command will be a line with the
    # length of the value returned by that command followed by that
    # value. If the server doesn't support ``hello`` (which should be
    # rare), that line will be ``0\n``. Otherwise, the value will contain
    # RFC 822 like lines. Of these, the ``capabilities:`` line contains
    # the capabilities of the server.
    #
    # The ``upgrade`` command isn't really a command in the traditional
    # sense of version 1 of the transport because it isn't using the
    # proper mechanism for formatting insteads: instead, it just encodes
    # arguments on the line, delimited by spaces.
    #
    # The ``upgrade`` line looks like ``upgrade <token> <capabilities>``.
    # If the server doesn't support protocol upgrades, it will reply to
    # this line with ``0\n``. Otherwise, it emits an
    # ``upgraded <token> <protocol>`` line to both stdout and stderr.
    # Content immediately following this line describes additional
    # protocol and server state.
    #
    # In addition to the responses to our command requests, the server
    # may emit "banner" output on stdout. SSH servers are allowed to
    # print messages to stdout on login. Issuing commands on connection
    # allows us to flush this banner output from the server by scanning
    # for output to our well-known ``between`` command. Of course, if
    # the banner contains ``1\n\n``, this will throw off our detection.

    requestlog = ui.configbool(b'devel', b'debug.peer-request')

    # Generate a random token to help identify responses to version 2
    # upgrade request.
    token = pycompat.sysbytes(str(uuid.uuid4()))

    try:
        pairsarg = b'%s-%s' % (b'0' * 40, b'0' * 40)
        handshake = [
            b'hello\n',
            b'between\n',
            b'pairs %d\n' % len(pairsarg),
            pairsarg,
        ]

        if requestlog:
            ui.debug(b'devel-peer-request: hello+between\n')
            ui.debug(b'devel-peer-request:   pairs: %d bytes\n' % len(pairsarg))
        ui.debug(b'sending hello command\n')
        ui.debug(b'sending between command\n')

        stdin.write(b''.join(handshake))
        stdin.flush()
    except IOError:
        badresponse()

    # Assume version 1 of wire protocol by default.
    protoname = wireprototypes.SSHV1
    reupgraded = re.compile(b'^upgraded %s (.*)$' % stringutil.reescape(token))

    lines = [b'', b'dummy']
    max_noise = 500
    while lines[-1] and max_noise:
        try:
            l = stdout.readline()
            _forwardoutput(ui, stderr, warn=True)

            # Look for reply to protocol upgrade request. It has a token
            # in it, so there should be no false positives.
            m = reupgraded.match(l)
            if m:
                protoname = m.group(1)
                ui.debug(b'protocol upgraded to %s\n' % protoname)
                # If an upgrade was handled, the ``hello`` and ``between``
                # requests are ignored. The next output belongs to the
                # protocol, so stop scanning lines.
                break

            # Otherwise it could be a banner, ``0\n`` response if server
            # doesn't support upgrade.

            if lines[-1] == b'1\n' and l == b'\n':
                break
            if l:
                ui.debug(b'remote: ', l)
            lines.append(l)
            max_noise -= 1
        except IOError:
            badresponse()
    else:
        badresponse()

    caps = set()

    # For version 1, we should see a ``capabilities`` line in response to the
    # ``hello`` command.
    if protoname == wireprototypes.SSHV1:
        for l in reversed(lines):
            # Look for response to ``hello`` command. Scan from the back so
            # we don't misinterpret banner output as the command reply.
            if l.startswith(b'capabilities:'):
                caps.update(l[:-1].split(b':')[1].split())
                break

    # Error if we couldn't find capabilities, this means:
    #
    # 1. Remote isn't a Mercurial server
    # 2. Remote is a <0.9.1 Mercurial server
    # 3. Remote is a future Mercurial server that dropped ``hello``
    #    and other attempted handshake mechanisms.
    if not caps:
        badresponse()

    # Flush any output on stderr before proceeding.
    _forwardoutput(ui, stderr, warn=True)

    return protoname, caps


class sshv1peer(wireprotov1peer.wirepeer):
    def __init__(
        self,
        ui,
        path,
        proc,
        stdin,
        stdout,
        stderr,
        caps,
        autoreadstderr=True,
        remotehidden=False,
    ):
        """Create a peer from an existing SSH connection.

        ``proc`` is a handle on the underlying SSH process.
        ``stdin``, ``stdout``, and ``stderr`` are handles on the stdio
        pipes for that process.
        ``caps`` is a set of capabilities supported by the remote.
        ``autoreadstderr`` denotes whether to automatically read from
        stderr and to forward its output.
        """
        super().__init__(ui, path=path, remotehidden=remotehidden)
        # self._subprocess is unused. Keeping a handle on the process
        # holds a reference and prevents it from being garbage collected.
        self._subprocess = proc

        # And we hook up our "doublepipe" wrapper to allow querying
        # stderr any time we perform I/O.
        if autoreadstderr:
            stdout = doublepipe(ui, util.bufferedinputpipe(stdout), stderr)
            stdin = doublepipe(ui, stdin, stderr)

        self._pipeo = stdin
        self._pipei = stdout
        self._pipee = stderr
        self._caps = caps
        self._autoreadstderr = autoreadstderr
        self._initstack = b''.join(util.getstackframes(1))
        self._remotehidden = remotehidden

    # Commands that have a "framed" response where the first line of the
    # response contains the length of that response.
    _FRAMED_COMMANDS = {
        b'batch',
    }

    # Begin of ipeerconnection interface.

    def url(self):
        return self.path.loc

    def local(self):
        return None

    def canpush(self):
        return True

    def close(self):
        self._cleanup()

    # End of ipeerconnection interface.

    # Begin of ipeercommands interface.

    def capabilities(self):
        return self._caps

    # End of ipeercommands interface.

    def _readerr(self):
        _forwardoutput(self.ui, self._pipee)

    def _abort(self, exception):
        self._cleanup()
        raise exception

    def _cleanup(self, warn=None):
        _cleanuppipes(self.ui, self._pipei, self._pipeo, self._pipee, warn=warn)

    def __del__(self):
        self._cleanup(warn=self._initstack)

    def _sendrequest(self, cmd, args, framed=False):
        if self.ui.debugflag and self.ui.configbool(
            b'devel', b'debug.peer-request'
        ):
            dbg = self.ui.debug
            line = b'devel-peer-request: %s\n'
            dbg(line % cmd)
            for key, value in sorted(args.items()):
                if not isinstance(value, dict):
                    dbg(line % b'  %s: %d bytes' % (key, len(value)))
                else:
                    for dk, dv in sorted(value.items()):
                        dbg(line % b'  %s-%s: %d' % (key, dk, len(dv)))
        self.ui.debug(b"sending %s command\n" % cmd)
        self._pipeo.write(b"%s\n" % cmd)
        _func, names = wireprotov1server.commands[cmd]
        keys = names.split()
        wireargs = {}
        for k in keys:
            if k == b'*':
                wireargs[b'*'] = args
                break
            else:
                wireargs[k] = args[k]
                del args[k]
        for k, v in sorted(wireargs.items()):
            self._pipeo.write(b"%s %d\n" % (k, len(v)))
            if isinstance(v, dict):
                for dk, dv in v.items():
                    self._pipeo.write(b"%s %d\n" % (dk, len(dv)))
                    self._pipeo.write(dv)
            else:
                self._pipeo.write(v)
        self._pipeo.flush()

        # We know exactly how many bytes are in the response. So return a proxy
        # around the raw output stream that allows reading exactly this many
        # bytes. Callers then can read() without fear of overrunning the
        # response.
        if framed:
            amount = self._getamount()
            return util.cappedreader(self._pipei, amount)

        return self._pipei

    def _callstream(self, cmd, **args):
        args = pycompat.byteskwargs(args)
        return self._sendrequest(cmd, args, framed=cmd in self._FRAMED_COMMANDS)

    def _callcompressable(self, cmd, **args):
        args = pycompat.byteskwargs(args)
        return self._sendrequest(cmd, args, framed=cmd in self._FRAMED_COMMANDS)

    def _call(self, cmd, **args):
        args = pycompat.byteskwargs(args)
        return self._sendrequest(cmd, args, framed=True).read()

    def _callpush(self, cmd, fp, **args):
        # The server responds with an empty frame if the client should
        # continue submitting the payload.
        r = self._call(cmd, **args)
        if r:
            return b'', r

        # The payload consists of frames with content followed by an empty
        # frame.
        for d in iter(lambda: fp.read(4096), b''):
            self._writeframed(d)
        self._writeframed(b"", flush=True)

        # In case of success, there is an empty frame and a frame containing
        # the integer result (as a string).
        # In case of error, there is a non-empty frame containing the error.
        r = self._readframed()
        if r:
            return b'', r
        return self._readframed(), b''

    def _calltwowaystream(self, cmd, fp, **args):
        # The server responds with an empty frame if the client should
        # continue submitting the payload.
        r = self._call(cmd, **args)
        if r:
            # XXX needs to be made better
            raise error.Abort(_(b'unexpected remote reply: %s') % r)

        # The payload consists of frames with content followed by an empty
        # frame.
        for d in iter(lambda: fp.read(4096), b''):
            self._writeframed(d)
        self._writeframed(b"", flush=True)

        return self._pipei

    def _getamount(self):
        l = self._pipei.readline()
        if l == b'\n':
            if self._autoreadstderr:
                self._readerr()
            msg = _(b'check previous remote output')
            self._abort(error.OutOfBandError(hint=msg))
        if self._autoreadstderr:
            self._readerr()
        try:
            return int(l)
        except ValueError:
            self._abort(error.ResponseError(_(b"unexpected response:"), l))

    def _readframed(self):
        size = self._getamount()
        if not size:
            return b''

        return self._pipei.read(size)

    def _writeframed(self, data, flush=False):
        self._pipeo.write(b"%d\n" % len(data))
        if data:
            self._pipeo.write(data)
        if flush:
            self._pipeo.flush()
        if self._autoreadstderr:
            self._readerr()


def _make_peer(
    ui,
    path,
    proc,
    stdin,
    stdout,
    stderr,
    autoreadstderr=True,
    remotehidden=False,
):
    """Make a peer instance from existing pipes.

    ``path`` and ``proc`` are stored on the eventual peer instance and may
    not be used for anything meaningful.

    ``stdin``, ``stdout``, and ``stderr`` are the pipes connected to the
    SSH server's stdio handles.

    This function is factored out to allow creating peers that don't
    actually spawn a new process. It is useful for starting SSH protocol
    servers and clients via non-standard means, which can be useful for
    testing.
    """
    try:
        protoname, caps = _performhandshake(ui, stdin, stdout, stderr)
    except Exception:
        _cleanuppipes(ui, stdout, stdin, stderr, warn=None)
        raise

    if protoname == wireprototypes.SSHV1:
        return sshv1peer(
            ui,
            path,
            proc,
            stdin,
            stdout,
            stderr,
            caps,
            autoreadstderr=autoreadstderr,
            remotehidden=remotehidden,
        )
    else:
        _cleanuppipes(ui, stdout, stdin, stderr, warn=None)
        raise error.RepoError(
            _(b'unknown version of SSH protocol: %s') % protoname
        )


def make_peer(
    ui, path, create, intents=None, createopts=None, remotehidden=False
):
    """Create an SSH peer.

    The returned object conforms to the ``wireprotov1peer.wirepeer`` interface.
    """
    u = urlutil.url(path.loc, parsequery=False, parsefragment=False)
    if u.scheme != b'ssh' or not u.host or u.path is None:
        raise error.RepoError(_(b"couldn't parse location %s") % path.loc)

    urlutil.checksafessh(path.loc)

    if u.passwd is not None:
        raise error.RepoError(_(b'password in URL not supported'))

    sshcmd = ui.config(b'ui', b'ssh')
    remotecmd = ui.config(b'ui', b'remotecmd')
    sshaddenv = dict(ui.configitems(b'sshenv'))
    sshenv = procutil.shellenviron(sshaddenv)
    remotepath = u.path or b'.'

    args = procutil.sshargs(sshcmd, u.host, u.user, u.port)

    if create:
        # We /could/ do this, but only if the remote init command knows how to
        # handle them. We don't yet make any assumptions about that. And without
        # querying the remote, there's no way of knowing if the remote even
        # supports said requested feature.
        if createopts:
            raise error.RepoError(
                _(
                    b'cannot create remote SSH repositories '
                    b'with extra options'
                )
            )

        cmd = b'%s %s %s' % (
            sshcmd,
            args,
            procutil.shellquote(
                b'%s init %s'
                % (_serverquote(remotecmd), _serverquote(remotepath))
            ),
        )
        ui.debug(b'running %s\n' % cmd)
        res = ui.system(cmd, blockedtag=b'sshpeer', environ=sshenv)
        if res != 0:
            raise error.RepoError(_(b'could not create remote repo'))

    proc, stdin, stdout, stderr = _makeconnection(
        ui,
        sshcmd,
        args,
        remotecmd,
        remotepath,
        sshenv,
        remotehidden=remotehidden,
    )

    peer = _make_peer(
        ui, path, proc, stdin, stdout, stderr, remotehidden=remotehidden
    )

    # Finally, if supported by the server, notify it about our own
    # capabilities.
    if b'protocaps' in peer.capabilities():
        try:
            peer._call(
                b"protocaps", caps=b' '.join(sorted(_clientcapabilities()))
            )
        except IOError:
            peer._cleanup()
            raise error.RepoError(_(b'capability exchange failed'))

    return peer
