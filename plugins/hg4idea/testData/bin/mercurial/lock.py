# lock.py - simple advisory locking scheme for mercurial
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import contextlib
import errno
import os
import signal
import socket
import time
import typing
import warnings

from .i18n import _

from . import (
    encoding,
    error,
    pycompat,
    util,
)

from .utils import procutil


def _getlockprefix():
    """Return a string which is used to differentiate pid namespaces

    It's useful to detect "dead" processes and remove stale locks with
    confidence. Typically it's just hostname. On modern linux, we include an
    extra Linux-specific pid namespace identifier.
    """
    result = encoding.strtolocal(socket.gethostname())
    if pycompat.sysplatform.startswith(b'linux'):
        try:
            result += b'/%x' % os.stat(b'/proc/self/ns/pid').st_ino
        except (FileNotFoundError, PermissionError, NotADirectoryError):
            pass
    return result


@contextlib.contextmanager
def _delayedinterrupt():
    """Block signal interrupt while doing something critical

    This makes sure that the code block wrapped by this context manager won't
    be interrupted.

    For Windows developers: It appears not possible to guard time.sleep()
    from CTRL_C_EVENT, so please don't use time.sleep() to test if this is
    working.
    """
    assertedsigs = []
    blocked = False
    orighandlers = {}

    def raiseinterrupt(num):
        if num == getattr(signal, 'SIGINT', None) or num == getattr(
            signal, 'CTRL_C_EVENT', None
        ):
            raise KeyboardInterrupt
        else:
            raise error.SignalInterrupt

    def catchterm(num, frame):
        if blocked:
            assertedsigs.append(num)
        else:
            raiseinterrupt(num)

    try:
        # save handlers first so they can be restored even if a setup is
        # interrupted between signal.signal() and orighandlers[] =.
        for name in [
            'CTRL_C_EVENT',
            'SIGINT',
            'SIGBREAK',
            'SIGHUP',
            'SIGTERM',
        ]:
            num = getattr(signal, name, None)
            if num and num not in orighandlers:
                orighandlers[num] = signal.getsignal(num)
        try:
            for num in orighandlers:
                signal.signal(num, catchterm)
        except ValueError:
            pass  # in a thread? no luck

        blocked = True
        yield
    finally:
        # no simple way to reliably restore all signal handlers because
        # any loops, recursive function calls, except blocks, etc. can be
        # interrupted. so instead, make catchterm() raise interrupt.
        blocked = False
        try:
            for num, handler in orighandlers.items():
                signal.signal(num, handler)
        except ValueError:
            pass  # in a thread?

    # re-raise interrupt exception if any, which may be shadowed by a new
    # interrupt occurred while re-raising the first one
    if assertedsigs:
        raiseinterrupt(assertedsigs[0])


def trylock(ui, vfs, lockname, timeout, warntimeout, *args, **kwargs):
    """return an acquired lock or raise an a LockHeld exception

    This function is responsible to issue warnings and or debug messages about
    the held lock while trying to acquires it."""
    devel_wait_file = kwargs.pop("devel_wait_sync_file", None)

    def printwarning(printer, locker):
        """issue the usual "waiting on lock" message through any channel"""
        # show more details for new-style locks
        if b':' in locker:
            host, pid = locker.split(b":", 1)
            msg = _(
                b"waiting for lock on %s held by process %r on host %r\n"
            ) % (
                pycompat.bytestr(l.desc),
                pycompat.bytestr(pid),
                pycompat.bytestr(host),
            )
        else:
            msg = _(b"waiting for lock on %s held by %r\n") % (
                l.desc,
                pycompat.bytestr(locker),
            )
        printer(msg)

    l = lock(vfs, lockname, 0, *args, dolock=False, **kwargs)

    debugidx = 0 if (warntimeout and timeout) else -1
    warningidx = 0
    if not timeout:
        warningidx = -1
    elif warntimeout:
        warningidx = warntimeout

    delay = 0
    while True:
        try:
            l._trylock()
            break
        except error.LockHeld as inst:
            if devel_wait_file is not None:
                # create the file to signal we are waiting
                with open(devel_wait_file, 'w'):
                    pass

            if delay == debugidx:
                printwarning(ui.debug, inst.locker)
            if delay == warningidx:
                printwarning(ui.warn, inst.locker)
            if timeout <= delay:
                assert isinstance(inst.filename, bytes)
                raise error.LockHeld(
                    errno.ETIMEDOUT,
                    typing.cast(bytes, inst.filename),
                    l.desc,
                    inst.locker,
                )
            time.sleep(1)
            delay += 1

    l.delay = delay
    if l.delay:
        if 0 <= warningidx <= l.delay:
            ui.warn(_(b"got lock after %d seconds\n") % l.delay)
        else:
            ui.debug(b"got lock after %d seconds\n" % l.delay)
    if l.acquirefn:
        l.acquirefn()
    return l


class lock:
    """An advisory lock held by one process to control access to a set
    of files.  Non-cooperating processes or incorrectly written scripts
    can ignore Mercurial's locking scheme and stomp all over the
    repository, so don't do that.

    Typically used via localrepository.lock() to lock the repository
    store (.hg/store/) or localrepository.wlock() to lock everything
    else under .hg/."""

    # lock is symlink on platforms that support it, file on others.

    # symlink is used because create of directory entry and contents
    # are atomic even over nfs.

    # old-style lock: symlink to pid
    # new-style lock: symlink to hostname:pid

    _host = None

    def __init__(
        self,
        vfs,
        fname,
        timeout=-1,
        releasefn=None,
        acquirefn=None,
        desc=None,
        signalsafe=True,
        dolock=True,
    ):
        self.vfs = vfs
        self.f = fname
        self.held = 0
        self.timeout = timeout
        self.releasefn = releasefn
        self.acquirefn = acquirefn
        self.desc = desc
        if signalsafe:
            self._maybedelayedinterrupt = _delayedinterrupt
        else:
            self._maybedelayedinterrupt = util.nullcontextmanager
        self.postrelease = []
        self.pid = self._getpid()
        if dolock:
            self.delay = self.lock()
            if self.acquirefn:
                self.acquirefn()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, exc_tb):
        success = all(a is None for a in (exc_type, exc_value, exc_tb))
        self.release(success=success)

    def __del__(self):
        if self.held:
            warnings.warn(
                "use lock.release instead of del lock",
                category=DeprecationWarning,
                stacklevel=2,
            )

            # ensure the lock will be removed
            # even if recursive locking did occur
            self.held = 1

        self.release()

    def _getpid(self):
        # wrapper around procutil.getpid() to make testing easier
        return procutil.getpid()

    def lock(self):
        timeout = self.timeout
        while True:
            try:
                self._trylock()
                return self.timeout - timeout
            except error.LockHeld as inst:
                if timeout != 0:
                    time.sleep(1)
                    if timeout > 0:
                        timeout -= 1
                    continue
                raise error.LockHeld(
                    errno.ETIMEDOUT, inst.filename, self.desc, inst.locker
                )

    def _trylock(self):
        if self.held:
            self.held += 1
            return
        if lock._host is None:
            lock._host = _getlockprefix()
        lockname = b'%s:%d' % (lock._host, self.pid)
        retry = 5
        while not self.held and retry:
            retry -= 1
            try:
                with self._maybedelayedinterrupt():
                    self.vfs.makelock(lockname, self.f)
                    self.held = 1
            except (OSError, IOError) as why:
                if why.errno == errno.EEXIST:
                    locker = self._readlock()
                    if locker is None:
                        continue

                    locker = self._testlock(locker)
                    if locker is not None:
                        raise error.LockHeld(
                            errno.EAGAIN,
                            self.vfs.join(self.f),
                            self.desc,
                            locker,
                        )
                else:
                    assert isinstance(why.filename, bytes)
                    assert isinstance(why.strerror, str)
                    raise error.LockUnavailable(
                        why.errno,
                        why.strerror,
                        typing.cast(bytes, why.filename),
                        self.desc,
                    )

        if not self.held:
            # use empty locker to mean "busy for frequent lock/unlock
            # by many processes"
            raise error.LockHeld(
                errno.EAGAIN, self.vfs.join(self.f), self.desc, b""
            )

    def _readlock(self):
        """read lock and return its value

        Returns None if no lock exists, pid for old-style locks, and host:pid
        for new-style locks.
        """
        try:
            return self.vfs.readlock(self.f)
        except FileNotFoundError:
            return None

    def _lockshouldbebroken(self, locker):
        if locker is None:
            return False
        try:
            host, pid = locker.split(b":", 1)
        except ValueError:
            return False
        if host != lock._host:
            return False
        try:
            pid = int(pid)
        except ValueError:
            return False
        if procutil.testpid(pid):
            return False
        return True

    def _testlock(self, locker):
        if not self._lockshouldbebroken(locker):
            return locker

        # if locker dead, break lock.  must do this with another lock
        # held, or can race and break valid lock.
        try:
            with lock(self.vfs, self.f + b'.break', timeout=0):
                locker = self._readlock()
                if not self._lockshouldbebroken(locker):
                    return locker
                self.vfs.unlink(self.f)
        except error.LockError:
            return locker

    def testlock(self):
        """return id of locker if lock is valid, else None.

        If old-style lock, we cannot tell what machine locker is on.
        with new-style lock, if locker is on this machine, we can
        see if locker is alive.  If locker is on this machine but
        not alive, we can safely break lock.

        The lock file is only deleted when None is returned.

        """
        locker = self._readlock()
        return self._testlock(locker)

    def release(self, success=True):
        """release the lock and execute callback function if any

        If the lock has been acquired multiple times, the actual release is
        delayed to the last release call."""
        if self.held > 1:
            self.held -= 1
        elif self.held == 1:
            self.held = 0
            if self._getpid() != self.pid:
                # we forked, and are not the parent
                return
            try:
                if self.releasefn:
                    self.releasefn()
            finally:
                try:
                    self.vfs.unlink(self.f)
                except OSError:
                    pass
            # The postrelease functions typically assume the lock is not held
            # at all.
            for callback in self.postrelease:
                callback(success)
            # Prevent double usage and help clear cycles.
            self.postrelease = None


def release(*locks):
    for lock in locks:
        if lock is not None:
            lock.release()
