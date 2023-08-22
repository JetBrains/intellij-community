# Copyright 2016-present Facebook. All Rights Reserved.
#
# revmap: trivial hg hash - linelog rev bidirectional map
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import bisect
import io
import os
import struct

from mercurial.node import hex
from mercurial.pycompat import open
from mercurial import (
    error as hgerror,
    pycompat,
)
from . import error

# the revmap file format is straightforward:
#
#    8 bytes: header
#    1 byte : flag for linelog revision 1
#    ? bytes: (optional) '\0'-terminated path string
#             only exists if (flag & renameflag) != 0
#   20 bytes: hg hash for linelog revision 1
#    1 byte : flag for linelog revision 2
#    ? bytes: (optional) '\0'-terminated path string
#   20 bytes: hg hash for linelog revision 2
#   ....
#
# the implementation is kinda stupid: __init__ loads the whole revmap.
# no laziness. benchmark shows loading 10000 revisions is about 0.015
# seconds, which looks enough for our use-case. if this implementation
# becomes a bottleneck, we can change it to lazily read the file
# from the end.

# whether the changeset is in the side branch. i.e. not in the linear main
# branch but only got referenced by lines in merge changesets.
sidebranchflag = 1

# whether the changeset changes the file path (ie. is a rename)
renameflag = 2

# len(mercurial.node.nullid)
_hshlen = 20


class revmap(object):
    """trivial hg bin hash - linelog rev bidirectional map

    also stores a flag (uint8) for each revision, and track renames.
    """

    HEADER = b'REVMAP1\0'

    def __init__(self, path=None):
        """create or load the revmap, optionally associate to a file

        if path is None, the revmap is entirely in-memory. the caller is
        responsible for locking. concurrent writes to a same file is unsafe.
        the caller needs to make sure one file is associated to at most one
        revmap object at a time."""
        self.path = path
        self._rev2hsh = [None]
        self._rev2flag = [None]
        self._hsh2rev = {}
        # since rename does not happen frequently, do not store path for every
        # revision. self._renamerevs can be used for bisecting.
        self._renamerevs = [0]
        self._renamepaths = [b'']
        self._lastmaxrev = -1
        if path:
            if os.path.exists(path):
                self._load()
            else:
                # write the header so "append" can do incremental updates
                self.flush()

    def copyfrom(self, rhs):
        """copy the map data from another revmap. do not affect self.path"""
        self._rev2hsh = rhs._rev2hsh[:]
        self._rev2flag = rhs._rev2flag[:]
        self._hsh2rev = rhs._hsh2rev.copy()
        self._renamerevs = rhs._renamerevs[:]
        self._renamepaths = rhs._renamepaths[:]
        self._lastmaxrev = -1

    @property
    def maxrev(self):
        """return max linelog revision number"""
        return len(self._rev2hsh) - 1

    def append(self, hsh, sidebranch=False, path=None, flush=False):
        """add a binary hg hash and return the mapped linelog revision.
        if flush is True, incrementally update the file.
        """
        if hsh in self._hsh2rev:
            raise error.CorruptedFileError(
                b'%r is in revmap already' % hex(hsh)
            )
        if len(hsh) != _hshlen:
            raise hgerror.ProgrammingError(
                b'hsh must be %d-char long' % _hshlen
            )
        idx = len(self._rev2hsh)
        flag = 0
        if sidebranch:
            flag |= sidebranchflag
        if path is not None and path != self._renamepaths[-1]:
            flag |= renameflag
            self._renamerevs.append(idx)
            self._renamepaths.append(path)
        self._rev2hsh.append(hsh)
        self._rev2flag.append(flag)
        self._hsh2rev[hsh] = idx
        if flush:
            self.flush()
        return idx

    def rev2hsh(self, rev):
        """convert linelog revision to hg hash. return None if not found."""
        if rev > self.maxrev or rev < 0:
            return None
        return self._rev2hsh[rev]

    def rev2flag(self, rev):
        """get the flag (uint8) for a given linelog revision.
        return None if revision does not exist.
        """
        if rev > self.maxrev or rev < 0:
            return None
        return self._rev2flag[rev]

    def rev2path(self, rev):
        """get the path for a given linelog revision.
        return None if revision does not exist.
        """
        if rev > self.maxrev or rev < 0:
            return None
        idx = bisect.bisect_right(self._renamerevs, rev) - 1
        return self._renamepaths[idx]

    def hsh2rev(self, hsh):
        """convert hg hash to linelog revision. return None if not found."""
        return self._hsh2rev.get(hsh)

    def clear(self, flush=False):
        """make the map empty. if flush is True, write to disk"""
        # rev 0 is reserved, real rev starts from 1
        self._rev2hsh = [None]
        self._rev2flag = [None]
        self._hsh2rev = {}
        self._rev2path = [b'']
        self._lastmaxrev = -1
        if flush:
            self.flush()

    def flush(self):
        """write the state down to the file"""
        if not self.path:
            return
        if self._lastmaxrev == -1:  # write the entire file
            with open(self.path, b'wb') as f:
                f.write(self.HEADER)
                for i in pycompat.xrange(1, len(self._rev2hsh)):
                    self._writerev(i, f)
        else:  # append incrementally
            with open(self.path, b'ab') as f:
                for i in pycompat.xrange(
                    self._lastmaxrev + 1, len(self._rev2hsh)
                ):
                    self._writerev(i, f)
        self._lastmaxrev = self.maxrev

    def _load(self):
        """load state from file"""
        if not self.path:
            return
        # use local variables in a loop. CPython uses LOAD_FAST for them,
        # which is faster than both LOAD_CONST and LOAD_GLOBAL.
        flaglen = 1
        hshlen = _hshlen
        with open(self.path, b'rb') as f:
            if f.read(len(self.HEADER)) != self.HEADER:
                raise error.CorruptedFileError()
            self.clear(flush=False)
            while True:
                buf = f.read(flaglen)
                if not buf:
                    break
                flag = ord(buf)
                rev = len(self._rev2hsh)
                if flag & renameflag:
                    path = self._readcstr(f)
                    self._renamerevs.append(rev)
                    self._renamepaths.append(path)
                hsh = f.read(hshlen)
                if len(hsh) != hshlen:
                    raise error.CorruptedFileError()
                self._hsh2rev[hsh] = rev
                self._rev2flag.append(flag)
                self._rev2hsh.append(hsh)
        self._lastmaxrev = self.maxrev

    def _writerev(self, rev, f):
        """append a revision data to file"""
        flag = self._rev2flag[rev]
        hsh = self._rev2hsh[rev]
        f.write(struct.pack(b'B', flag))
        if flag & renameflag:
            path = self.rev2path(rev)
            if path is None:
                raise error.CorruptedFileError(b'cannot find path for %s' % rev)
            f.write(path + b'\0')
        f.write(hsh)

    @staticmethod
    def _readcstr(f):
        """read a C-language-like '\0'-terminated string"""
        buf = b''
        while True:
            ch = f.read(1)
            if not ch:  # unexpected eof
                raise error.CorruptedFileError()
            if ch == b'\0':
                break
            buf += ch
        return buf

    def __contains__(self, f):
        """(fctx or (node, path)) -> bool.
        test if (node, path) is in the map, and is not in a side branch.
        f can be either a tuple of (node, path), or a fctx.
        """
        if isinstance(f, tuple):  # f: (node, path)
            hsh, path = f
        else:  # f: fctx
            hsh, path = f.node(), f.path()
        rev = self.hsh2rev(hsh)
        if rev is None:
            return False
        if path is not None and path != self.rev2path(rev):
            return False
        return (self.rev2flag(rev) & sidebranchflag) == 0


def getlastnode(path):
    """return the last hash in a revmap, without loading its full content.
    this is equivalent to `m = revmap(path); m.rev2hsh(m.maxrev)`, but faster.
    """
    hsh = None
    try:
        with open(path, b'rb') as f:
            f.seek(-_hshlen, io.SEEK_END)
            if f.tell() > len(revmap.HEADER):
                hsh = f.read(_hshlen)
    except IOError:
        pass
    return hsh
