# commandserver.py - communicate with Mercurial's API over a pipe
#
#  Copyright Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import gc
import os
import random
import selectors
import signal
import socket
import struct
import traceback

from .i18n import _
from . import (
    encoding,
    error,
    loggingutil,
    pycompat,
    repocache,
    util,
    vfs as vfsmod,
)
from .utils import (
    cborutil,
    procutil,
)


class channeledoutput:
    """
    Write data to out in the following format:

    data length (unsigned int),
    data
    """

    def __init__(self, out, channel):
        self.out = out
        self.channel = channel

    @property
    def name(self):
        return b'<%c-channel>' % self.channel

    def write(self, data):
        if not data:
            return
        # single write() to guarantee the same atomicity as the underlying file
        self.out.write(struct.pack(b'>cI', self.channel, len(data)) + data)
        self.out.flush()

    def __getattr__(self, attr):
        if attr in ('isatty', 'fileno', 'tell', 'seek'):
            raise AttributeError(attr)
        return getattr(self.out, attr)


class channeledmessage:
    """
    Write encoded message and metadata to out in the following format:

    data length (unsigned int),
    encoded message and metadata, as a flat key-value dict.

    Each message should have 'type' attribute. Messages of unknown type
    should be ignored.
    """

    # teach ui that write() can take **opts
    structured = True

    def __init__(self, out, channel, encodename, encodefn):
        self._cout = channeledoutput(out, channel)
        self.encoding = encodename
        self._encodefn = encodefn

    def write(self, data, **opts):
        opts = pycompat.byteskwargs(opts)
        if data is not None:
            opts[b'data'] = data
        self._cout.write(self._encodefn(opts))

    def __getattr__(self, attr):
        return getattr(self._cout, attr)


class channeledinput:
    """
    Read data from in_.

    Requests for input are written to out in the following format:
    channel identifier - 'I' for plain input, 'L' line based (1 byte)
    how many bytes to send at most (unsigned int),

    The client replies with:
    data length (unsigned int), 0 meaning EOF
    data
    """

    maxchunksize = 4 * 1024

    def __init__(self, in_, out, channel):
        self.in_ = in_
        self.out = out
        self.channel = channel

    @property
    def name(self):
        return b'<%c-channel>' % self.channel

    def read(self, size=-1):
        if size < 0:
            # if we need to consume all the clients input, ask for 4k chunks
            # so the pipe doesn't fill up risking a deadlock
            size = self.maxchunksize
            s = self._read(size, self.channel)
            buf = s
            while s:
                s = self._read(size, self.channel)
                buf += s

            return buf
        else:
            return self._read(size, self.channel)

    def _read(self, size, channel):
        if not size:
            return b''
        assert size > 0

        # tell the client we need at most size bytes
        self.out.write(struct.pack(b'>cI', channel, size))
        self.out.flush()

        length = self.in_.read(4)
        length = struct.unpack(b'>I', length)[0]
        if not length:
            return b''
        else:
            return self.in_.read(length)

    def readline(self, size=-1):
        if size < 0:
            size = self.maxchunksize
            s = self._read(size, b'L')
            buf = s
            # keep asking for more until there's either no more or
            # we got a full line
            while s and not s.endswith(b'\n'):
                s = self._read(size, b'L')
                buf += s

            return buf
        else:
            return self._read(size, b'L')

    def __iter__(self):
        return self

    def next(self):
        l = self.readline()
        if not l:
            raise StopIteration
        return l

    __next__ = next

    def __getattr__(self, attr):
        if attr in ('isatty', 'fileno', 'tell', 'seek'):
            raise AttributeError(attr)
        return getattr(self.in_, attr)


_messageencoders = {
    b'cbor': lambda v: b''.join(cborutil.streamencode(v)),
}


def _selectmessageencoder(ui):
    encnames = ui.configlist(b'cmdserver', b'message-encodings')
    for n in encnames:
        f = _messageencoders.get(n)
        if f:
            return n, f
    raise error.Abort(
        b'no supported message encodings: %s' % b' '.join(encnames)
    )


class server:
    """
    Listens for commands on fin, runs them and writes the output on a channel
    based stream to fout.
    """

    def __init__(self, ui, repo, fin, fout, prereposetups=None):
        self.cwd = encoding.getcwd()

        if repo:
            # the ui here is really the repo ui so take its baseui so we don't
            # end up with its local configuration
            self.ui = repo.baseui
            self.repo = repo
            self.repoui = repo.ui
        else:
            self.ui = ui
            self.repo = self.repoui = None
        self._prereposetups = prereposetups

        self.cdebug = channeledoutput(fout, b'd')
        self.cerr = channeledoutput(fout, b'e')
        self.cout = channeledoutput(fout, b'o')
        self.cin = channeledinput(fin, fout, b'I')
        self.cresult = channeledoutput(fout, b'r')

        if self.ui.config(b'cmdserver', b'log') == b'-':
            # switch log stream of server's ui to the 'd' (debug) channel
            # (don't touch repo.ui as its lifetime is longer than the server)
            self.ui = self.ui.copy()
            setuplogging(self.ui, repo=None, fp=self.cdebug)

        self.cmsg = None
        if ui.config(b'ui', b'message-output') == b'channel':
            encname, encfn = _selectmessageencoder(ui)
            self.cmsg = channeledmessage(fout, b'm', encname, encfn)

        self.client = fin

        # If shutdown-on-interrupt is off, the default SIGINT handler is
        # removed so that client-server communication wouldn't be interrupted.
        # For example, 'runcommand' handler will issue three short read()s.
        # If one of the first two read()s were interrupted, the communication
        # channel would be left at dirty state and the subsequent request
        # wouldn't be parsed. So catching KeyboardInterrupt isn't enough.
        self._shutdown_on_interrupt = ui.configbool(
            b'cmdserver', b'shutdown-on-interrupt'
        )
        self._old_inthandler = None
        if not self._shutdown_on_interrupt:
            self._old_inthandler = signal.signal(signal.SIGINT, signal.SIG_IGN)

    def cleanup(self):
        """release and restore resources taken during server session"""
        if not self._shutdown_on_interrupt:
            signal.signal(signal.SIGINT, self._old_inthandler)

    def _read(self, size):
        if not size:
            return b''

        data = self.client.read(size)

        # is the other end closed?
        if not data:
            raise EOFError

        return data

    def _readstr(self):
        """read a string from the channel

        format:
        data length (uint32), data
        """
        length = struct.unpack(b'>I', self._read(4))[0]
        if not length:
            return b''
        return self._read(length)

    def _readlist(self):
        """read a list of NULL separated strings from the channel"""
        s = self._readstr()
        if s:
            return s.split(b'\0')
        else:
            return []

    def _dispatchcommand(self, req):
        from . import dispatch  # avoid cycle

        if self._shutdown_on_interrupt:
            # no need to restore SIGINT handler as it is unmodified.
            return dispatch.dispatch(req)

        try:
            signal.signal(signal.SIGINT, self._old_inthandler)
            return dispatch.dispatch(req)
        except error.SignalInterrupt:
            # propagate SIGBREAK, SIGHUP, or SIGTERM.
            raise
        except KeyboardInterrupt:
            # SIGINT may be received out of the try-except block of dispatch(),
            # so catch it as last ditch. Another KeyboardInterrupt may be
            # raised while handling exceptions here, but there's no way to
            # avoid that except for doing everything in C.
            pass
        finally:
            signal.signal(signal.SIGINT, signal.SIG_IGN)
        # On KeyboardInterrupt, print error message and exit *after* SIGINT
        # handler removed.
        req.ui.error(_(b'interrupted!\n'))
        return -1

    def runcommand(self):
        """reads a list of \0 terminated arguments, executes
        and writes the return code to the result channel"""
        from . import dispatch  # avoid cycle

        args = self._readlist()

        # copy the uis so changes (e.g. --config or --verbose) don't
        # persist between requests
        copiedui = self.ui.copy()
        uis = [copiedui]
        if self.repo:
            self.repo.baseui = copiedui
            # clone ui without using ui.copy because this is protected
            repoui = self.repoui.__class__(self.repoui)
            repoui.copy = copiedui.copy  # redo copy protection
            uis.append(repoui)
            self.repo.ui = self.repo.dirstate._ui = repoui
            self.repo.invalidateall()

        for ui in uis:
            ui.resetstate()
            # any kind of interaction must use server channels, but chg may
            # replace channels by fully functional tty files. so nontty is
            # enforced only if cin is a channel.
            if not hasattr(self.cin, 'fileno'):
                ui.setconfig(b'ui', b'nontty', b'true', b'commandserver')

        req = dispatch.request(
            args[:],
            copiedui,
            self.repo,
            self.cin,
            self.cout,
            self.cerr,
            self.cmsg,
            prereposetups=self._prereposetups,
        )

        try:
            ret = self._dispatchcommand(req) & 255
            # If shutdown-on-interrupt is off, it's important to write the
            # result code *after* SIGINT handler removed. If the result code
            # were lost, the client wouldn't be able to continue processing.
            self.cresult.write(struct.pack(b'>i', int(ret)))
        finally:
            # restore old cwd
            if b'--cwd' in args:
                os.chdir(self.cwd)

    def getencoding(self):
        """writes the current encoding to the result channel"""
        self.cresult.write(encoding.encoding)

    def serveone(self):
        cmd = self.client.readline()[:-1]
        if cmd:
            handler = self.capabilities.get(cmd)
            if handler:
                handler(self)
            else:
                # clients are expected to check what commands are supported by
                # looking at the servers capabilities
                raise error.Abort(_(b'unknown command %s') % cmd)

        return cmd != b''

    capabilities = {b'runcommand': runcommand, b'getencoding': getencoding}

    def serve(self):
        hellomsg = b'capabilities: ' + b' '.join(sorted(self.capabilities))
        hellomsg += b'\n'
        hellomsg += b'encoding: ' + encoding.encoding
        hellomsg += b'\n'
        if self.cmsg:
            hellomsg += b'message-encoding: %s\n' % self.cmsg.encoding
        hellomsg += b'pid: %d' % procutil.getpid()
        if hasattr(os, 'getpgid'):
            hellomsg += b'\n'
            hellomsg += b'pgid: %d' % os.getpgid(0)

        # write the hello msg in -one- chunk
        self.cout.write(hellomsg)

        try:
            while self.serveone():
                pass
        except EOFError:
            # we'll get here if the client disconnected while we were reading
            # its request
            return 1

        return 0


def setuplogging(ui, repo=None, fp=None):
    """Set up server logging facility

    If cmdserver.log is '-', log messages will be sent to the given fp.
    It should be the 'd' channel while a client is connected, and otherwise
    is the stderr of the server process.
    """
    # developer config: cmdserver.log
    logpath = ui.config(b'cmdserver', b'log')
    if not logpath:
        return
    # developer config: cmdserver.track-log
    tracked = set(ui.configlist(b'cmdserver', b'track-log'))

    if logpath == b'-' and fp:
        logger = loggingutil.fileobjectlogger(fp, tracked)
    elif logpath == b'-':
        logger = loggingutil.fileobjectlogger(ui.ferr, tracked)
    else:
        logpath = util.abspath(util.expandpath(logpath))
        # developer config: cmdserver.max-log-files
        maxfiles = ui.configint(b'cmdserver', b'max-log-files')
        # developer config: cmdserver.max-log-size
        maxsize = ui.configbytes(b'cmdserver', b'max-log-size')
        vfs = vfsmod.vfs(os.path.dirname(logpath))
        logger = loggingutil.filelogger(
            vfs,
            os.path.basename(logpath),
            tracked,
            maxfiles=maxfiles,
            maxsize=maxsize,
        )

    targetuis = {ui}
    if repo:
        targetuis.add(repo.baseui)
        targetuis.add(repo.ui)
    for u in targetuis:
        u.setlogger(b'cmdserver', logger)


class pipeservice:
    def __init__(self, ui, repo, opts):
        self.ui = ui
        self.repo = repo

    def init(self):
        pass

    def run(self):
        ui = self.ui
        # redirect stdio to null device so that broken extensions or in-process
        # hooks will never cause corruption of channel protocol.
        with ui.protectedfinout() as (fin, fout):
            sv = server(ui, self.repo, fin, fout)
            try:
                return sv.serve()
            finally:
                sv.cleanup()


def _initworkerprocess():
    # use a different process group from the master process, in order to:
    # 1. make the current process group no longer "orphaned" (because the
    #    parent of this process is in a different process group while
    #    remains in a same session)
    #    according to POSIX 2.2.2.52, orphaned process group will ignore
    #    terminal-generated stop signals like SIGTSTP (Ctrl+Z), which will
    #    cause trouble for things like ncurses.
    # 2. the client can use kill(-pgid, sig) to simulate terminal-generated
    #    SIGINT (Ctrl+C) and process-exit-generated SIGHUP. our child
    #    processes like ssh will be killed properly, without affecting
    #    unrelated processes.
    os.setpgid(0, 0)
    # change random state otherwise forked request handlers would have a
    # same state inherited from parent.
    random.seed()


def _serverequest(ui, repo, conn, createcmdserver, prereposetups):
    fin = conn.makefile('rb')
    fout = conn.makefile('wb')
    sv = None
    try:
        sv = createcmdserver(repo, conn, fin, fout, prereposetups)
        try:
            sv.serve()
        # handle exceptions that may be raised by command server. most of
        # known exceptions are caught by dispatch.
        except error.Abort as inst:
            ui.error(_(b'abort: %s\n') % inst.message)
        except BrokenPipeError:
            pass
        except KeyboardInterrupt:
            pass
        finally:
            sv.cleanup()
    except:  # re-raises
        # also write traceback to error channel. otherwise client cannot
        # see it because it is written to server's stderr by default.
        if sv:
            cerr = sv.cerr
        else:
            cerr = channeledoutput(fout, b'e')
        cerr.write(encoding.strtolocal(traceback.format_exc()))
        raise
    finally:
        fin.close()
        try:
            fout.close()  # implicit flush() may cause another EPIPE
        except BrokenPipeError:
            pass


class unixservicehandler:
    """Set of pluggable operations for unix-mode services

    Almost all methods except for createcmdserver() are called in the main
    process. You can't pass mutable resource back from createcmdserver().
    """

    pollinterval = None

    def __init__(self, ui):
        self.ui = ui

    def bindsocket(self, sock, address):
        util.bindunixsocket(sock, address)
        sock.listen(socket.SOMAXCONN)
        self.ui.status(_(b'listening at %s\n') % address)
        self.ui.flush()  # avoid buffering of status message

    def unlinksocket(self, address):
        os.unlink(address)

    def shouldexit(self):
        """True if server should shut down; checked per pollinterval"""
        return False

    def newconnection(self):
        """Called when main process notices new connection"""

    def createcmdserver(self, repo, conn, fin, fout, prereposetups):
        """Create new command server instance; called in the process that
        serves for the current connection"""
        return server(self.ui, repo, fin, fout, prereposetups)


class unixforkingservice:
    """
    Listens on unix domain socket and forks server per connection
    """

    def __init__(self, ui, repo, opts, handler=None):
        self.ui = ui
        self.repo = repo
        self.address = opts[b'address']
        if not hasattr(socket, 'AF_UNIX'):
            raise error.Abort(_(b'unsupported platform'))
        if not self.address:
            raise error.Abort(_(b'no socket path specified with --address'))
        self._servicehandler = handler or unixservicehandler(ui)
        self._sock = None
        self._mainipc = None
        self._workeripc = None
        self._oldsigchldhandler = None
        self._workerpids = set()  # updated by signal handler; do not iterate
        self._socketunlinked = None
        # experimental config: cmdserver.max-repo-cache
        maxlen = ui.configint(b'cmdserver', b'max-repo-cache')
        if maxlen < 0:
            raise error.Abort(_(b'negative max-repo-cache size not allowed'))
        self._repoloader = repocache.repoloader(ui, maxlen)
        # attempt to avoid crash in CoreFoundation when using chg after fix in
        # a89381e04c58
        if pycompat.isdarwin:
            procutil.gui()

    def init(self):
        self._sock = socket.socket(socket.AF_UNIX)
        # IPC channel from many workers to one main process; this is actually
        # a uni-directional pipe, but is backed by a DGRAM socket so each
        # message can be easily separated.
        o = socket.socketpair(socket.AF_UNIX, socket.SOCK_DGRAM)
        self._mainipc, self._workeripc = o
        self._servicehandler.bindsocket(self._sock, self.address)
        if hasattr(procutil, 'unblocksignal'):
            procutil.unblocksignal(signal.SIGCHLD)
        o = signal.signal(signal.SIGCHLD, self._sigchldhandler)
        self._oldsigchldhandler = o
        self._socketunlinked = False
        self._repoloader.start()

    def _unlinksocket(self):
        if not self._socketunlinked:
            self._servicehandler.unlinksocket(self.address)
            self._socketunlinked = True

    def _cleanup(self):
        signal.signal(signal.SIGCHLD, self._oldsigchldhandler)
        self._sock.close()
        self._mainipc.close()
        self._workeripc.close()
        self._unlinksocket()
        self._repoloader.stop()
        # don't kill child processes as they have active clients, just wait
        self._reapworkers(0)

    def run(self):
        try:
            self._mainloop()
        finally:
            self._cleanup()

    def _mainloop(self):
        exiting = False
        h = self._servicehandler
        selector = selectors.DefaultSelector()
        selector.register(
            self._sock, selectors.EVENT_READ, self._acceptnewconnection
        )
        selector.register(
            self._mainipc, selectors.EVENT_READ, self._handlemainipc
        )
        while True:
            if not exiting and h.shouldexit():
                # clients can no longer connect() to the domain socket, so
                # we stop queuing new requests.
                # for requests that are queued (connect()-ed, but haven't been
                # accept()-ed), handle them before exit. otherwise, clients
                # waiting for recv() will receive ECONNRESET.
                self._unlinksocket()
                exiting = True
            events = selector.select(timeout=h.pollinterval)
            if not events:
                # only exit if we completed all queued requests
                if exiting:
                    break
                continue
            for key, _mask in events:
                key.data(key.fileobj, selector)
        selector.close()

    def _acceptnewconnection(self, sock, selector):
        h = self._servicehandler
        conn, _addr = sock.accept()

        # Future improvement: On Python 3.7, maybe gc.freeze() can be used
        # to prevent COW memory from being touched by GC.
        # https://instagram-engineering.com/
        #   copy-on-write-friendly-python-garbage-collection-ad6ed5233ddf
        pid = os.fork()
        if pid:
            try:
                self.ui.log(
                    b'cmdserver', b'forked worker process (pid=%d)\n', pid
                )
                self._workerpids.add(pid)
                h.newconnection()
            finally:
                conn.close()  # release handle in parent process
        else:
            try:
                selector.close()
                sock.close()
                self._mainipc.close()
                self._runworker(conn)
                conn.close()
                self._workeripc.close()
                os._exit(0)
            except:  # never return, hence no re-raises
                try:
                    self.ui.traceback(force=True)
                finally:
                    os._exit(255)

    def _handlemainipc(self, sock, selector):
        """Process messages sent from a worker"""
        path = sock.recv(32768)  # large enough to receive path
        self._repoloader.load(path)

    def _sigchldhandler(self, signal, frame):
        self._reapworkers(os.WNOHANG)

    def _reapworkers(self, options):
        while self._workerpids:
            try:
                pid, _status = os.waitpid(-1, options)
            except ChildProcessError:
                # no child processes at all (reaped by other waitpid()?)
                self._workerpids.clear()
                return
            if pid == 0:
                # no waitable child processes
                return
            self.ui.log(b'cmdserver', b'worker process exited (pid=%d)\n', pid)
            self._workerpids.discard(pid)

    def _runworker(self, conn):
        signal.signal(signal.SIGCHLD, self._oldsigchldhandler)
        _initworkerprocess()
        h = self._servicehandler
        try:
            _serverequest(
                self.ui,
                self.repo,
                conn,
                h.createcmdserver,
                prereposetups=[self._reposetup],
            )
        finally:
            gc.collect()  # trigger __del__ since worker process uses os._exit

    def _reposetup(self, ui, repo):
        if not repo.local():
            return

        class unixcmdserverrepo(repo.__class__):
            def close(self):
                super(unixcmdserverrepo, self).close()
                try:
                    self._cmdserveripc.send(self.root)
                except socket.error:
                    self.ui.log(
                        b'cmdserver', b'failed to send repo root to master\n'
                    )

        repo.__class__ = unixcmdserverrepo
        repo._cmdserveripc = self._workeripc

        cachedrepo = self._repoloader.get(repo.root)
        if cachedrepo is None:
            return
        repo.ui.log(b'repocache', b'repo from cache: %s\n', repo.root)
        repocache.copycache(cachedrepo, repo)
