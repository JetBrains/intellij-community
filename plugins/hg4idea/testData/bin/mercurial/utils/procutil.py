# procutil.py - utility for managing processes and executable environment
#
#  Copyright 2005 K. Thananchayan <thananck@yahoo.com>
#  Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#  Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import contextlib
import errno
import io
import os
import signal
import subprocess
import sys
import threading
import time

from ..i18n import _
from ..pycompat import (
    getattr,
    open,
)

from .. import (
    encoding,
    error,
    policy,
    pycompat,
)

# Import like this to keep import-checker happy
from ..utils import resourceutil

osutil = policy.importmod('osutil')

if pycompat.iswindows:
    from .. import windows as platform
else:
    from .. import posix as platform


def isatty(fp):
    try:
        return fp.isatty()
    except AttributeError:
        return False


class BadFile(io.RawIOBase):
    """Dummy file object to simulate closed stdio behavior"""

    def readinto(self, b):
        raise IOError(errno.EBADF, 'Bad file descriptor')

    def write(self, b):
        raise IOError(errno.EBADF, 'Bad file descriptor')


class LineBufferedWrapper(object):
    def __init__(self, orig):
        self.orig = orig

    def __getattr__(self, attr):
        return getattr(self.orig, attr)

    def write(self, s):
        orig = self.orig
        res = orig.write(s)
        if s.endswith(b'\n'):
            orig.flush()
        return res


io.BufferedIOBase.register(LineBufferedWrapper)


def make_line_buffered(stream):
    if pycompat.ispy3 and not isinstance(stream, io.BufferedIOBase):
        # On Python 3, buffered streams can be expected to subclass
        # BufferedIOBase. This is definitively the case for the streams
        # initialized by the interpreter. For unbuffered streams, we don't need
        # to emulate line buffering.
        return stream
    if isinstance(stream, LineBufferedWrapper):
        return stream
    return LineBufferedWrapper(stream)


def unwrap_line_buffered(stream):
    if isinstance(stream, LineBufferedWrapper):
        assert not isinstance(stream.orig, LineBufferedWrapper)
        return stream.orig
    return stream


class WriteAllWrapper(object):
    def __init__(self, orig):
        self.orig = orig

    def __getattr__(self, attr):
        return getattr(self.orig, attr)

    def write(self, s):
        write1 = self.orig.write
        m = memoryview(s)
        total_to_write = len(s)
        total_written = 0
        while total_written < total_to_write:
            total_written += write1(m[total_written:])
        return total_written


io.IOBase.register(WriteAllWrapper)


def _make_write_all(stream):
    assert pycompat.ispy3
    if isinstance(stream, WriteAllWrapper):
        return stream
    if isinstance(stream, io.BufferedIOBase):
        # The io.BufferedIOBase.write() contract guarantees that all data is
        # written.
        return stream
    # In general, the write() method of streams is free to write only part of
    # the data.
    return WriteAllWrapper(stream)


if pycompat.ispy3:
    # Python 3 implements its own I/O streams. Unlike stdio of C library,
    # sys.stdin/stdout/stderr may be None if underlying fd is closed.

    # TODO: .buffer might not exist if std streams were replaced; we'll need
    # a silly wrapper to make a bytes stream backed by a unicode one.

    if sys.stdin is None:
        stdin = BadFile()
    else:
        stdin = sys.stdin.buffer
    if sys.stdout is None:
        stdout = BadFile()
    else:
        stdout = _make_write_all(sys.stdout.buffer)
    if sys.stderr is None:
        stderr = BadFile()
    else:
        stderr = _make_write_all(sys.stderr.buffer)

    if pycompat.iswindows:
        # Work around Windows bugs.
        stdout = platform.winstdout(stdout)  # pytype: disable=module-attr
        stderr = platform.winstdout(stderr)  # pytype: disable=module-attr
    if isatty(stdout):
        # The standard library doesn't offer line-buffered binary streams.
        stdout = make_line_buffered(stdout)
else:
    # Python 2 uses the I/O streams provided by the C library.
    stdin = sys.stdin
    stdout = sys.stdout
    stderr = sys.stderr
    if pycompat.iswindows:
        # Work around Windows bugs.
        stdout = platform.winstdout(stdout)  # pytype: disable=module-attr
        stderr = platform.winstdout(stderr)  # pytype: disable=module-attr
    if isatty(stdout):
        if pycompat.iswindows:
            # The Windows C runtime library doesn't support line buffering.
            stdout = make_line_buffered(stdout)
        else:
            # glibc determines buffering on first write to stdout - if we
            # replace a TTY destined stdout with a pipe destined stdout (e.g.
            # pager), we want line buffering.
            stdout = os.fdopen(stdout.fileno(), 'wb', 1)


findexe = platform.findexe
_gethgcmd = platform.gethgcmd
getuser = platform.getuser
getpid = os.getpid
hidewindow = platform.hidewindow
readpipe = platform.readpipe
setbinary = platform.setbinary
setsignalhandler = platform.setsignalhandler
shellquote = platform.shellquote
shellsplit = platform.shellsplit
spawndetached = platform.spawndetached
sshargs = platform.sshargs
testpid = platform.testpid

try:
    setprocname = osutil.setprocname
except AttributeError:
    pass
try:
    unblocksignal = osutil.unblocksignal
except AttributeError:
    pass

closefds = pycompat.isposix


def explainexit(code):
    """return a message describing a subprocess status
    (codes from kill are negative - not os.system/wait encoding)"""
    if code >= 0:
        return _(b"exited with status %d") % code
    return _(b"killed by signal %d") % -code


class _pfile(object):
    """File-like wrapper for a stream opened by subprocess.Popen()"""

    def __init__(self, proc, fp):
        self._proc = proc
        self._fp = fp

    def close(self):
        # unlike os.popen(), this returns an integer in subprocess coding
        self._fp.close()
        return self._proc.wait()

    def __iter__(self):
        return iter(self._fp)

    def __getattr__(self, attr):
        return getattr(self._fp, attr)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, exc_tb):
        self.close()


def popen(cmd, mode=b'rb', bufsize=-1):
    if mode == b'rb':
        return _popenreader(cmd, bufsize)
    elif mode == b'wb':
        return _popenwriter(cmd, bufsize)
    raise error.ProgrammingError(b'unsupported mode: %r' % mode)


def _popenreader(cmd, bufsize):
    p = subprocess.Popen(
        tonativestr(cmd),
        shell=True,
        bufsize=bufsize,
        close_fds=closefds,
        stdout=subprocess.PIPE,
    )
    return _pfile(p, p.stdout)


def _popenwriter(cmd, bufsize):
    p = subprocess.Popen(
        tonativestr(cmd),
        shell=True,
        bufsize=bufsize,
        close_fds=closefds,
        stdin=subprocess.PIPE,
    )
    return _pfile(p, p.stdin)


def popen2(cmd, env=None):
    # Setting bufsize to -1 lets the system decide the buffer size.
    # The default for bufsize is 0, meaning unbuffered. This leads to
    # poor performance on Mac OS X: http://bugs.python.org/issue4194
    p = subprocess.Popen(
        tonativestr(cmd),
        shell=True,
        bufsize=-1,
        close_fds=closefds,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        env=tonativeenv(env),
    )
    return p.stdin, p.stdout


def popen3(cmd, env=None):
    stdin, stdout, stderr, p = popen4(cmd, env)
    return stdin, stdout, stderr


def popen4(cmd, env=None, bufsize=-1):
    p = subprocess.Popen(
        tonativestr(cmd),
        shell=True,
        bufsize=bufsize,
        close_fds=closefds,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=tonativeenv(env),
    )
    return p.stdin, p.stdout, p.stderr, p


def pipefilter(s, cmd):
    '''filter string S through command CMD, returning its output'''
    p = subprocess.Popen(
        tonativestr(cmd),
        shell=True,
        close_fds=closefds,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
    )
    pout, perr = p.communicate(s)
    return pout


def tempfilter(s, cmd):
    """filter string S through a pair of temporary files with CMD.
    CMD is used as a template to create the real command to be run,
    with the strings INFILE and OUTFILE replaced by the real names of
    the temporary files generated."""
    inname, outname = None, None
    try:
        infd, inname = pycompat.mkstemp(prefix=b'hg-filter-in-')
        fp = os.fdopen(infd, 'wb')
        fp.write(s)
        fp.close()
        outfd, outname = pycompat.mkstemp(prefix=b'hg-filter-out-')
        os.close(outfd)
        cmd = cmd.replace(b'INFILE', inname)
        cmd = cmd.replace(b'OUTFILE', outname)
        code = system(cmd)
        if pycompat.sysplatform == b'OpenVMS' and code & 1:
            code = 0
        if code:
            raise error.Abort(
                _(b"command '%s' failed: %s") % (cmd, explainexit(code))
            )
        with open(outname, b'rb') as fp:
            return fp.read()
    finally:
        try:
            if inname:
                os.unlink(inname)
        except OSError:
            pass
        try:
            if outname:
                os.unlink(outname)
        except OSError:
            pass


_filtertable = {
    b'tempfile:': tempfilter,
    b'pipe:': pipefilter,
}


def filter(s, cmd):
    """filter a string through a command that transforms its input to its
    output"""
    for name, fn in pycompat.iteritems(_filtertable):
        if cmd.startswith(name):
            return fn(s, cmd[len(name) :].lstrip())
    return pipefilter(s, cmd)


_hgexecutable = None


def hgexecutable():
    """return location of the 'hg' executable.

    Defaults to $HG or 'hg' in the search path.
    """
    if _hgexecutable is None:
        hg = encoding.environ.get(b'HG')
        mainmod = sys.modules['__main__']
        if hg:
            _sethgexecutable(hg)
        elif resourceutil.mainfrozen():
            if getattr(sys, 'frozen', None) == 'macosx_app':
                # Env variable set by py2app
                _sethgexecutable(encoding.environ[b'EXECUTABLEPATH'])
            else:
                _sethgexecutable(pycompat.sysexecutable)
        elif (
            not pycompat.iswindows
            and os.path.basename(getattr(mainmod, '__file__', '')) == 'hg'
        ):
            _sethgexecutable(pycompat.fsencode(mainmod.__file__))
        else:
            _sethgexecutable(
                findexe(b'hg') or os.path.basename(pycompat.sysargv[0])
            )
    return _hgexecutable


def _sethgexecutable(path):
    """set location of the 'hg' executable"""
    global _hgexecutable
    _hgexecutable = path


def _testfileno(f, stdf):
    fileno = getattr(f, 'fileno', None)
    try:
        return fileno and fileno() == stdf.fileno()
    except io.UnsupportedOperation:
        return False  # fileno() raised UnsupportedOperation


def isstdin(f):
    return _testfileno(f, sys.__stdin__)


def isstdout(f):
    return _testfileno(f, sys.__stdout__)


def protectstdio(uin, uout):
    """Duplicate streams and redirect original if (uin, uout) are stdio

    If uin is stdin, it's redirected to /dev/null. If uout is stdout, it's
    redirected to stderr so the output is still readable.

    Returns (fin, fout) which point to the original (uin, uout) fds, but
    may be copy of (uin, uout). The returned streams can be considered
    "owned" in that print(), exec(), etc. never reach to them.
    """
    uout.flush()
    fin, fout = uin, uout
    if _testfileno(uin, stdin):
        newfd = os.dup(uin.fileno())
        nullfd = os.open(os.devnull, os.O_RDONLY)
        os.dup2(nullfd, uin.fileno())
        os.close(nullfd)
        fin = os.fdopen(newfd, 'rb')
    if _testfileno(uout, stdout):
        newfd = os.dup(uout.fileno())
        os.dup2(stderr.fileno(), uout.fileno())
        fout = os.fdopen(newfd, 'wb')
    return fin, fout


def restorestdio(uin, uout, fin, fout):
    """Restore (uin, uout) streams from possibly duplicated (fin, fout)"""
    uout.flush()
    for f, uif in [(fin, uin), (fout, uout)]:
        if f is not uif:
            os.dup2(f.fileno(), uif.fileno())
            f.close()


def shellenviron(environ=None):
    """return environ with optional override, useful for shelling out"""

    def py2shell(val):
        """convert python object into string that is useful to shell"""
        if val is None or val is False:
            return b'0'
        if val is True:
            return b'1'
        return pycompat.bytestr(val)

    env = dict(encoding.environ)
    if environ:
        env.update((k, py2shell(v)) for k, v in pycompat.iteritems(environ))
    env[b'HG'] = hgexecutable()
    return env


if pycompat.iswindows:

    def shelltonative(cmd, env):
        return platform.shelltocmdexe(  # pytype: disable=module-attr
            cmd, shellenviron(env)
        )

    tonativestr = encoding.strfromlocal
else:

    def shelltonative(cmd, env):
        return cmd

    tonativestr = pycompat.identity


def tonativeenv(env):
    """convert the environment from bytes to strings suitable for Popen(), etc."""
    return pycompat.rapply(tonativestr, env)


def system(cmd, environ=None, cwd=None, out=None):
    """enhanced shell command execution.
    run with environment maybe modified, maybe in different dir.

    if out is specified, it is assumed to be a file-like object that has a
    write() method. stdout and stderr will be redirected to out."""
    try:
        stdout.flush()
    except Exception:
        pass
    env = shellenviron(environ)
    if out is None or isstdout(out):
        rc = subprocess.call(
            tonativestr(cmd),
            shell=True,
            close_fds=closefds,
            env=tonativeenv(env),
            cwd=pycompat.rapply(tonativestr, cwd),
        )
    else:
        proc = subprocess.Popen(
            tonativestr(cmd),
            shell=True,
            close_fds=closefds,
            env=tonativeenv(env),
            cwd=pycompat.rapply(tonativestr, cwd),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        for line in iter(proc.stdout.readline, b''):
            out.write(line)
        proc.wait()
        rc = proc.returncode
    if pycompat.sysplatform == b'OpenVMS' and rc & 1:
        rc = 0
    return rc


_is_gui = None


def _gui():
    '''Are we running in a GUI?'''
    if pycompat.isdarwin:
        if b'SSH_CONNECTION' in encoding.environ:
            # handle SSH access to a box where the user is logged in
            return False
        elif getattr(osutil, 'isgui', None):
            # check if a CoreGraphics session is available
            return osutil.isgui()
        else:
            # pure build; use a safe default
            return True
    else:
        return (
            pycompat.iswindows
            or encoding.environ.get(b"DISPLAY")
            or encoding.environ.get(b"WAYLAND_DISPLAY")
        )


def gui():
    global _is_gui
    if _is_gui is None:
        _is_gui = _gui()
    return _is_gui


def hgcmd():
    """Return the command used to execute current hg

    This is different from hgexecutable() because on Windows we want
    to avoid things opening new shell windows like batch files, so we
    get either the python call or current executable.
    """
    if resourceutil.mainfrozen():
        if getattr(sys, 'frozen', None) == 'macosx_app':
            # Env variable set by py2app
            return [encoding.environ[b'EXECUTABLEPATH']]
        else:
            return [pycompat.sysexecutable]
    return _gethgcmd()


def rundetached(args, condfn):
    """Execute the argument list in a detached process.

    condfn is a callable which is called repeatedly and should return
    True once the child process is known to have started successfully.
    At this point, the child process PID is returned. If the child
    process fails to start or finishes before condfn() evaluates to
    True, return -1.
    """
    # Windows case is easier because the child process is either
    # successfully starting and validating the condition or exiting
    # on failure. We just poll on its PID. On Unix, if the child
    # process fails to start, it will be left in a zombie state until
    # the parent wait on it, which we cannot do since we expect a long
    # running process on success. Instead we listen for SIGCHLD telling
    # us our child process terminated.
    terminated = set()

    def handler(signum, frame):
        terminated.add(os.wait())

    prevhandler = None
    SIGCHLD = getattr(signal, 'SIGCHLD', None)
    if SIGCHLD is not None:
        prevhandler = signal.signal(SIGCHLD, handler)
    try:
        pid = spawndetached(args)
        while not condfn():
            if (pid in terminated or not testpid(pid)) and not condfn():
                return -1
            time.sleep(0.1)
        return pid
    finally:
        if prevhandler is not None:
            signal.signal(signal.SIGCHLD, prevhandler)


@contextlib.contextmanager
def uninterruptible(warn):
    """Inhibit SIGINT handling on a region of code.

    Note that if this is called in a non-main thread, it turns into a no-op.

    Args:
      warn: A callable which takes no arguments, and returns True if the
            previous signal handling should be restored.
    """

    oldsiginthandler = [signal.getsignal(signal.SIGINT)]
    shouldbail = []

    def disabledsiginthandler(*args):
        if warn():
            signal.signal(signal.SIGINT, oldsiginthandler[0])
            del oldsiginthandler[0]
        shouldbail.append(True)

    try:
        try:
            signal.signal(signal.SIGINT, disabledsiginthandler)
        except ValueError:
            # wrong thread, oh well, we tried
            del oldsiginthandler[0]
        yield
    finally:
        if oldsiginthandler:
            signal.signal(signal.SIGINT, oldsiginthandler[0])
        if shouldbail:
            raise KeyboardInterrupt


if pycompat.iswindows:
    # no fork on Windows, but we can create a detached process
    # https://msdn.microsoft.com/en-us/library/windows/desktop/ms684863.aspx
    # No stdlib constant exists for this value
    DETACHED_PROCESS = 0x00000008
    # Following creation flags might create a console GUI window.
    # Using subprocess.CREATE_NEW_CONSOLE might helps.
    # See https://phab.mercurial-scm.org/D1701 for discussion
    _creationflags = (
        DETACHED_PROCESS
        | subprocess.CREATE_NEW_PROCESS_GROUP  # pytype: disable=module-attr
    )

    def runbgcommand(
        script,
        env,
        shell=False,
        stdout=None,
        stderr=None,
        ensurestart=True,
        record_wait=None,
        stdin_bytes=None,
    ):
        '''Spawn a command without waiting for it to finish.'''
        # we can't use close_fds *and* redirect stdin. I'm not sure that we
        # need to because the detached process has no console connection.

        try:
            stdin = None
            if stdin_bytes is not None:
                stdin = pycompat.unnamedtempfile()
                stdin.write(stdin_bytes)
                stdin.flush()
                stdin.seek(0)

            p = subprocess.Popen(
                pycompat.rapply(tonativestr, script),
                shell=shell,
                env=tonativeenv(env),
                close_fds=True,
                creationflags=_creationflags,
                stdin=stdin,
                stdout=stdout,
                stderr=stderr,
            )
            if record_wait is not None:
                record_wait(p.wait)
        finally:
            if stdin is not None:
                stdin.close()


else:

    def runbgcommandpy3(
        cmd,
        env,
        shell=False,
        stdout=None,
        stderr=None,
        ensurestart=True,
        record_wait=None,
        stdin_bytes=None,
    ):
        """Spawn a command without waiting for it to finish.


        When `record_wait` is not None, the spawned process will not be fully
        detached and the `record_wait` argument will be called with a the
        `Subprocess.wait` function for the spawned process.  This is mostly
        useful for developers that need to make sure the spawned process
        finished before a certain point. (eg: writing test)"""
        if pycompat.isdarwin:
            # avoid crash in CoreFoundation in case another thread
            # calls gui() while we're calling fork().
            gui()

        if shell:
            script = cmd
        else:
            if isinstance(cmd, bytes):
                cmd = [cmd]
            script = b' '.join(shellquote(x) for x in cmd)
        if record_wait is None:
            # double-fork to completely detach from the parent process
            script = b'( %s ) &' % script
            start_new_session = True
        else:
            start_new_session = False
            ensurestart = True

        try:
            if stdin_bytes is None:
                stdin = subprocess.DEVNULL
            else:
                stdin = pycompat.unnamedtempfile()
                stdin.write(stdin_bytes)
                stdin.flush()
                stdin.seek(0)
            if stdout is None:
                stdout = subprocess.DEVNULL
            if stderr is None:
                stderr = subprocess.DEVNULL

            p = subprocess.Popen(
                script,
                shell=True,
                env=env,
                close_fds=True,
                stdin=stdin,
                stdout=stdout,
                stderr=stderr,
                start_new_session=start_new_session,
            )
        except Exception:
            if record_wait is not None:
                record_wait(255)
            raise
        finally:
            if stdin_bytes is not None:
                stdin.close()
        if not ensurestart:
            # Even though we're not waiting on the child process,
            # we still must call waitpid() on it at some point so
            # it's not a zombie/defunct. This is especially relevant for
            # chg since the parent process won't die anytime soon.
            # We use a thread to make the overhead tiny.
            t = threading.Thread(target=lambda: p.wait)
            t.daemon = True
            t.start()
        else:
            returncode = p.wait
            if record_wait is not None:
                record_wait(returncode)

    def runbgcommandpy2(
        cmd,
        env,
        shell=False,
        stdout=None,
        stderr=None,
        ensurestart=True,
        record_wait=None,
        stdin_bytes=None,
    ):
        """Spawn a command without waiting for it to finish.


        When `record_wait` is not None, the spawned process will not be fully
        detached and the `record_wait` argument will be called with a the
        `Subprocess.wait` function for the spawned process.  This is mostly
        useful for developers that need to make sure the spawned process
        finished before a certain point. (eg: writing test)"""
        if pycompat.isdarwin:
            # avoid crash in CoreFoundation in case another thread
            # calls gui() while we're calling fork().
            gui()

        # double-fork to completely detach from the parent process
        # based on http://code.activestate.com/recipes/278731
        if record_wait is None:
            pid = os.fork()
            if pid:
                if not ensurestart:
                    # Even though we're not waiting on the child process,
                    # we still must call waitpid() on it at some point so
                    # it's not a zombie/defunct. This is especially relevant for
                    # chg since the parent process won't die anytime soon.
                    # We use a thread to make the overhead tiny.
                    def _do_wait():
                        os.waitpid(pid, 0)

                    t = threading.Thread(target=_do_wait)
                    t.daemon = True
                    t.start()
                    return
                # Parent process
                (_pid, status) = os.waitpid(pid, 0)
                if os.WIFEXITED(status):
                    returncode = os.WEXITSTATUS(status)
                else:
                    returncode = -(os.WTERMSIG(status))
                if returncode != 0:
                    # The child process's return code is 0 on success, an errno
                    # value on failure, or 255 if we don't have a valid errno
                    # value.
                    #
                    # (It would be slightly nicer to return the full exception info
                    # over a pipe as the subprocess module does.  For now it
                    # doesn't seem worth adding that complexity here, though.)
                    if returncode == 255:
                        returncode = errno.EINVAL
                    raise OSError(
                        returncode,
                        b'error running %r: %s'
                        % (cmd, os.strerror(returncode)),
                    )
                return

        returncode = 255
        try:
            if record_wait is None:
                # Start a new session
                os.setsid()
            # connect stdin to devnull to make sure the subprocess can't
            # muck up that stream for mercurial.
            if stdin_bytes is None:
                stdin = open(os.devnull, b'r')
            else:
                stdin = pycompat.unnamedtempfile()
                stdin.write(stdin_bytes)
                stdin.flush()
                stdin.seek(0)

            if stdout is None:
                stdout = open(os.devnull, b'w')
            if stderr is None:
                stderr = open(os.devnull, b'w')

            p = subprocess.Popen(
                cmd,
                shell=shell,
                env=env,
                close_fds=True,
                stdin=stdin,
                stdout=stdout,
                stderr=stderr,
            )
            if record_wait is not None:
                record_wait(p.wait)
            returncode = 0
        except EnvironmentError as ex:
            returncode = ex.errno & 0xFF
            if returncode == 0:
                # This shouldn't happen, but just in case make sure the
                # return code is never 0 here.
                returncode = 255
        except Exception:
            returncode = 255
        finally:
            # mission accomplished, this child needs to exit and not
            # continue the hg process here.
            stdin.close()
            if record_wait is None:
                os._exit(returncode)

    if pycompat.ispy3:
        # This branch is more robust, because it avoids running python
        # code (hence gc finalizers, like sshpeer.__del__, which
        # blocks).  But we can't easily do the equivalent in py2,
        # because of the lack of start_new_session=True flag. Given
        # that the py2 branch should die soon, the short-lived
        # duplication seems acceptable.
        runbgcommand = runbgcommandpy3
    else:
        runbgcommand = runbgcommandpy2
