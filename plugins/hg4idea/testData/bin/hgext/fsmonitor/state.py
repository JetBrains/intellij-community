# state.py - fsmonitor persistent state
#
# Copyright 2013-2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import errno
import os
import socket
import struct

from mercurial.i18n import _
from mercurial import (
    encoding,
    pathutil,
    util,
)

_version = 4
_versionformat = b">I"


class state:
    def __init__(self, repo):
        self._vfs = repo.vfs
        self._ui = repo.ui
        self._rootdir = pathutil.normasprefix(repo.root)
        self._lastclock = None
        self._identity = util.filestat(None)

        self.mode = self._ui.config(b'fsmonitor', b'mode')
        self.walk_on_invalidate = self._ui.configbool(
            b'fsmonitor', b'walk_on_invalidate'
        )
        self.timeout = float(self._ui.config(b'fsmonitor', b'timeout'))

    def get(self):
        try:
            file = self._vfs(b'fsmonitor.state', b'rb')
        except IOError as inst:
            self._identity = util.filestat(None)
            if inst.errno != errno.ENOENT:
                raise
            return None, None, None

        self._identity = util.filestat.fromfp(file)

        versionbytes = file.read(4)
        if len(versionbytes) < 4:
            self._ui.log(
                b'fsmonitor',
                b'fsmonitor: state file only has %d bytes, '
                b'nuking state\n' % len(versionbytes),
            )
            self.invalidate()
            return None, None, None
        try:
            diskversion = struct.unpack(_versionformat, versionbytes)[0]
            if diskversion != _version:
                # different version, nuke state and start over
                self._ui.log(
                    b'fsmonitor',
                    b'fsmonitor: version switch from %d to '
                    b'%d, nuking state\n' % (diskversion, _version),
                )
                self.invalidate()
                return None, None, None

            state = file.read().split(b'\0')
            # state = hostname\0clock\0ignorehash\0 + list of files, each
            # followed by a \0
            if len(state) < 3:
                self._ui.log(
                    b'fsmonitor',
                    b'fsmonitor: state file truncated (expected '
                    b'3 chunks, found %d), nuking state\n',
                    len(state),
                )
                self.invalidate()
                return None, None, None
            diskhostname = state[0]
            hostname = encoding.strtolocal(socket.gethostname())
            if diskhostname != hostname:
                # file got moved to a different host
                self._ui.log(
                    b'fsmonitor',
                    b'fsmonitor: stored hostname "%s" '
                    b'different from current "%s", nuking state\n'
                    % (diskhostname, hostname),
                )
                self.invalidate()
                return None, None, None

            clock = state[1]
            ignorehash = state[2]
            # discard the value after the last \0
            notefiles = state[3:-1]

        finally:
            file.close()

        return clock, ignorehash, notefiles

    def set(self, clock, ignorehash, notefiles):
        if clock is None:
            self.invalidate()
            return

        # Read the identity from the file on disk rather than from the open file
        # pointer below, because the latter is actually a brand new file.
        identity = util.filestat.frompath(self._vfs.join(b'fsmonitor.state'))
        if identity != self._identity:
            self._ui.debug(
                b'skip updating fsmonitor.state: identity mismatch\n'
            )
            return

        try:
            file = self._vfs(
                b'fsmonitor.state', b'wb', atomictemp=True, checkambig=True
            )
        except (IOError, OSError):
            self._ui.warn(_(b"warning: unable to write out fsmonitor state\n"))
            return

        with file:
            file.write(struct.pack(_versionformat, _version))
            file.write(encoding.strtolocal(socket.gethostname()) + b'\0')
            file.write(clock + b'\0')
            file.write(ignorehash + b'\0')
            if notefiles:
                file.write(b'\0'.join(notefiles))
                file.write(b'\0')

    def invalidate(self):
        try:
            os.unlink(os.path.join(self._rootdir, b'.hg', b'fsmonitor.state'))
        except FileNotFoundError:
            pass
        self._identity = util.filestat(None)

    def setlastclock(self, clock):
        self._lastclock = clock

    def getlastclock(self):
        return self._lastclock
