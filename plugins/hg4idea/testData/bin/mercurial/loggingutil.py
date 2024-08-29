# loggingutil.py - utility for logging events
#
# Copyright 2010 Nicolas Dumazet
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import errno

from . import (
    encoding,
)

from .utils import (
    dateutil,
    procutil,
    stringutil,
)


def openlogfile(ui, vfs, name, maxfiles=0, maxsize=0):
    """Open log file in append mode, with optional rotation

    If maxsize > 0, the log file will be rotated up to maxfiles.
    """

    def rotate(oldpath, newpath):
        try:
            vfs.unlink(newpath)
        except OSError as err:
            if err.errno != errno.ENOENT:
                ui.debug(
                    b"warning: cannot remove '%s': %s\n"
                    % (newpath, encoding.strtolocal(err.strerror))
                )
        try:
            if newpath:
                vfs.rename(oldpath, newpath)
        except OSError as err:
            if err.errno != errno.ENOENT:
                ui.debug(
                    b"warning: cannot rename '%s' to '%s': %s\n"
                    % (newpath, oldpath, encoding.strtolocal(err.strerror))
                )

    if maxsize > 0:
        try:
            st = vfs.stat(name)
        except OSError:
            pass
        else:
            if st.st_size >= maxsize:
                path = vfs.join(name)
                for i in range(maxfiles - 1, 1, -1):
                    rotate(
                        oldpath=b'%s.%d' % (path, i - 1),
                        newpath=b'%s.%d' % (path, i),
                    )
                rotate(oldpath=path, newpath=maxfiles > 0 and path + b'.1')
    return vfs(name, b'a', makeparentdirs=False)


def _formatlogline(msg):
    date = dateutil.datestr(format=b'%Y/%m/%d %H:%M:%S')
    pid = procutil.getpid()
    return b'%s (%d)> %s' % (date, pid, msg)


def _matchevent(event, tracked):
    return b'*' in tracked or event in tracked


class filelogger:
    """Basic logger backed by physical file with optional rotation"""

    def __init__(self, vfs, name, tracked, maxfiles=0, maxsize=0):
        self._vfs = vfs
        self._name = name
        self._trackedevents = set(tracked)
        self._maxfiles = maxfiles
        self._maxsize = maxsize

    def tracked(self, event):
        return _matchevent(event, self._trackedevents)

    def log(self, ui, event, msg, opts):
        line = _formatlogline(msg)
        try:
            with openlogfile(
                ui,
                self._vfs,
                self._name,
                maxfiles=self._maxfiles,
                maxsize=self._maxsize,
            ) as fp:
                fp.write(line)
        except IOError as err:
            ui.debug(
                b'cannot write to %s: %s\n'
                % (self._name, stringutil.forcebytestr(err))
            )


class fileobjectlogger:
    """Basic logger backed by file-like object"""

    def __init__(self, fp, tracked):
        self._fp = fp
        self._trackedevents = set(tracked)

    def tracked(self, event):
        return _matchevent(event, self._trackedevents)

    def log(self, ui, event, msg, opts):
        line = _formatlogline(msg)
        try:
            self._fp.write(line)
            self._fp.flush()
        except IOError as err:
            ui.debug(
                b'cannot write to %s: %s\n'
                % (
                    stringutil.forcebytestr(self._fp.name),
                    stringutil.forcebytestr(err),
                )
            )


class proxylogger:
    """Forward log events to another logger to be set later"""

    def __init__(self):
        self.logger = None

    def tracked(self, event):
        return self.logger is not None and self.logger.tracked(event)

    def log(self, ui, event, msg, opts):
        assert self.logger is not None
        self.logger.log(ui, event, msg, opts)
