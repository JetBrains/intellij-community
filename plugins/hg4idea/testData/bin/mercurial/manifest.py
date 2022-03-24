# manifest.py - manifest revision class for mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import heapq
import itertools
import struct
import weakref

from .i18n import _
from .node import (
    bin,
    hex,
    nullrev,
)
from .pycompat import getattr
from . import (
    encoding,
    error,
    match as matchmod,
    mdiff,
    pathutil,
    policy,
    pycompat,
    revlog,
    util,
)
from .interfaces import (
    repository,
    util as interfaceutil,
)
from .revlogutils import (
    constants as revlog_constants,
)

parsers = policy.importmod('parsers')
propertycache = util.propertycache

# Allow tests to more easily test the alternate path in manifestdict.fastdelta()
FASTDELTA_TEXTDIFF_THRESHOLD = 1000


def _parse(nodelen, data):
    # This method does a little bit of excessive-looking
    # precondition checking. This is so that the behavior of this
    # class exactly matches its C counterpart to try and help
    # prevent surprise breakage for anyone that develops against
    # the pure version.
    if data and data[-1:] != b'\n':
        raise ValueError(b'Manifest did not end in a newline.')
    prev = None
    for l in data.splitlines():
        if prev is not None and prev > l:
            raise ValueError(b'Manifest lines not in sorted order.')
        prev = l
        f, n = l.split(b'\0')
        nl = len(n)
        flags = n[-1:]
        if flags in _manifestflags:
            n = n[:-1]
            nl -= 1
        else:
            flags = b''
        if nl != 2 * nodelen:
            raise ValueError(b'Invalid manifest line')

        yield f, bin(n), flags


def _text(it):
    files = []
    lines = []
    for f, n, fl in it:
        files.append(f)
        # if this is changed to support newlines in filenames,
        # be sure to check the templates/ dir again (especially *-raw.tmpl)
        lines.append(b"%s\0%s%s\n" % (f, hex(n), fl))

    _checkforbidden(files)
    return b''.join(lines)


class lazymanifestiter(object):
    def __init__(self, lm):
        self.pos = 0
        self.lm = lm

    def __iter__(self):
        return self

    def next(self):
        try:
            data, pos = self.lm._get(self.pos)
        except IndexError:
            raise StopIteration
        if pos == -1:
            self.pos += 1
            return data[0]
        self.pos += 1
        zeropos = data.find(b'\x00', pos)
        return data[pos:zeropos]

    __next__ = next


class lazymanifestiterentries(object):
    def __init__(self, lm):
        self.lm = lm
        self.pos = 0

    def __iter__(self):
        return self

    def next(self):
        try:
            data, pos = self.lm._get(self.pos)
        except IndexError:
            raise StopIteration
        if pos == -1:
            self.pos += 1
            return data
        zeropos = data.find(b'\x00', pos)
        nlpos = data.find(b'\n', pos)
        if zeropos == -1 or nlpos == -1 or nlpos < zeropos:
            raise error.StorageError(b'Invalid manifest line')
        flags = data[nlpos - 1 : nlpos]
        if flags in _manifestflags:
            hlen = nlpos - zeropos - 2
        else:
            hlen = nlpos - zeropos - 1
            flags = b''
        if hlen != 2 * self.lm._nodelen:
            raise error.StorageError(b'Invalid manifest line')
        hashval = unhexlify(
            data, self.lm.extrainfo[self.pos], zeropos + 1, hlen
        )
        self.pos += 1
        return (data[pos:zeropos], hashval, flags)

    __next__ = next


def unhexlify(data, extra, pos, length):
    s = bin(data[pos : pos + length])
    if extra:
        s += chr(extra & 0xFF)
    return s


def _cmp(a, b):
    return (a > b) - (a < b)


_manifestflags = {b'', b'l', b't', b'x'}


class _lazymanifest(object):
    """A pure python manifest backed by a byte string.  It is supplimented with
    internal lists as it is modified, until it is compacted back to a pure byte
    string.

    ``data`` is the initial manifest data.

    ``positions`` is a list of offsets, one per manifest entry.  Positive
    values are offsets into ``data``, negative values are offsets into the
    ``extradata`` list.  When an entry is removed, its entry is dropped from
    ``positions``.  The values are encoded such that when walking the list and
    indexing into ``data`` or ``extradata`` as appropriate, the entries are
    sorted by filename.

    ``extradata`` is a list of (key, hash, flags) for entries that were added or
    modified since the manifest was created or compacted.
    """

    def __init__(
        self,
        nodelen,
        data,
        positions=None,
        extrainfo=None,
        extradata=None,
        hasremovals=False,
    ):
        self._nodelen = nodelen
        if positions is None:
            self.positions = self.findlines(data)
            self.extrainfo = [0] * len(self.positions)
            self.data = data
            self.extradata = []
            self.hasremovals = False
        else:
            self.positions = positions[:]
            self.extrainfo = extrainfo[:]
            self.extradata = extradata[:]
            self.data = data
            self.hasremovals = hasremovals

    def findlines(self, data):
        if not data:
            return []
        pos = data.find(b"\n")
        if pos == -1 or data[-1:] != b'\n':
            raise ValueError(b"Manifest did not end in a newline.")
        positions = [0]
        prev = data[: data.find(b'\x00')]
        while pos < len(data) - 1 and pos != -1:
            positions.append(pos + 1)
            nexts = data[pos + 1 : data.find(b'\x00', pos + 1)]
            if nexts < prev:
                raise ValueError(b"Manifest lines not in sorted order.")
            prev = nexts
            pos = data.find(b"\n", pos + 1)
        return positions

    def _get(self, index):
        # get the position encoded in pos:
        #   positive number is an index in 'data'
        #   negative number is in extrapieces
        pos = self.positions[index]
        if pos >= 0:
            return self.data, pos
        return self.extradata[-pos - 1], -1

    def _getkey(self, pos):
        if pos >= 0:
            return self.data[pos : self.data.find(b'\x00', pos + 1)]
        return self.extradata[-pos - 1][0]

    def bsearch(self, key):
        first = 0
        last = len(self.positions) - 1

        while first <= last:
            midpoint = (first + last) // 2
            nextpos = self.positions[midpoint]
            candidate = self._getkey(nextpos)
            r = _cmp(key, candidate)
            if r == 0:
                return midpoint
            else:
                if r < 0:
                    last = midpoint - 1
                else:
                    first = midpoint + 1
        return -1

    def bsearch2(self, key):
        # same as the above, but will always return the position
        # done for performance reasons
        first = 0
        last = len(self.positions) - 1

        while first <= last:
            midpoint = (first + last) // 2
            nextpos = self.positions[midpoint]
            candidate = self._getkey(nextpos)
            r = _cmp(key, candidate)
            if r == 0:
                return (midpoint, True)
            else:
                if r < 0:
                    last = midpoint - 1
                else:
                    first = midpoint + 1
        return (first, False)

    def __contains__(self, key):
        return self.bsearch(key) != -1

    def __getitem__(self, key):
        if not isinstance(key, bytes):
            raise TypeError(b"getitem: manifest keys must be a bytes.")
        needle = self.bsearch(key)
        if needle == -1:
            raise KeyError
        data, pos = self._get(needle)
        if pos == -1:
            return (data[1], data[2])
        zeropos = data.find(b'\x00', pos)
        nlpos = data.find(b'\n', zeropos)
        assert 0 <= needle <= len(self.positions)
        assert len(self.extrainfo) == len(self.positions)
        if zeropos == -1 or nlpos == -1 or nlpos < zeropos:
            raise error.StorageError(b'Invalid manifest line')
        hlen = nlpos - zeropos - 1
        flags = data[nlpos - 1 : nlpos]
        if flags in _manifestflags:
            hlen -= 1
        else:
            flags = b''
        if hlen != 2 * self._nodelen:
            raise error.StorageError(b'Invalid manifest line')
        hashval = unhexlify(data, self.extrainfo[needle], zeropos + 1, hlen)
        return (hashval, flags)

    def __delitem__(self, key):
        needle, found = self.bsearch2(key)
        if not found:
            raise KeyError
        cur = self.positions[needle]
        self.positions = self.positions[:needle] + self.positions[needle + 1 :]
        self.extrainfo = self.extrainfo[:needle] + self.extrainfo[needle + 1 :]
        if cur >= 0:
            # This does NOT unsort the list as far as the search functions are
            # concerned, as they only examine lines mapped by self.positions.
            self.data = self.data[:cur] + b'\x00' + self.data[cur + 1 :]
            self.hasremovals = True

    def __setitem__(self, key, value):
        if not isinstance(key, bytes):
            raise TypeError(b"setitem: manifest keys must be a byte string.")
        if not isinstance(value, tuple) or len(value) != 2:
            raise TypeError(
                b"Manifest values must be a tuple of (node, flags)."
            )
        hashval = value[0]
        if not isinstance(hashval, bytes) or len(hashval) not in (20, 32):
            raise TypeError(b"node must be a 20-byte or 32-byte byte string")
        flags = value[1]
        if not isinstance(flags, bytes) or len(flags) > 1:
            raise TypeError(b"flags must a 0 or 1 byte string, got %r", flags)
        needle, found = self.bsearch2(key)
        if found:
            # put the item
            pos = self.positions[needle]
            if pos < 0:
                self.extradata[-pos - 1] = (key, hashval, value[1])
            else:
                # just don't bother
                self.extradata.append((key, hashval, value[1]))
                self.positions[needle] = -len(self.extradata)
        else:
            # not found, put it in with extra positions
            self.extradata.append((key, hashval, value[1]))
            self.positions = (
                self.positions[:needle]
                + [-len(self.extradata)]
                + self.positions[needle:]
            )
            self.extrainfo = (
                self.extrainfo[:needle] + [0] + self.extrainfo[needle:]
            )

    def copy(self):
        # XXX call _compact like in C?
        return _lazymanifest(
            self._nodelen,
            self.data,
            self.positions,
            self.extrainfo,
            self.extradata,
            self.hasremovals,
        )

    def _compact(self):
        # hopefully not called TOO often
        if len(self.extradata) == 0 and not self.hasremovals:
            return
        l = []
        i = 0
        offset = 0
        self.extrainfo = [0] * len(self.positions)
        while i < len(self.positions):
            if self.positions[i] >= 0:
                cur = self.positions[i]
                last_cut = cur

                # Collect all contiguous entries in the buffer at the current
                # offset, breaking out only for added/modified items held in
                # extradata, or a deleted line prior to the next position.
                while True:
                    self.positions[i] = offset
                    i += 1
                    if i == len(self.positions) or self.positions[i] < 0:
                        break

                    # A removed file has no positions[] entry, but does have an
                    # overwritten first byte.  Break out and find the end of the
                    # current good entry/entries if there is a removed file
                    # before the next position.
                    if (
                        self.hasremovals
                        and self.data.find(b'\n\x00', cur, self.positions[i])
                        != -1
                    ):
                        break

                    offset += self.positions[i] - cur
                    cur = self.positions[i]
                end_cut = self.data.find(b'\n', cur)
                if end_cut != -1:
                    end_cut += 1
                offset += end_cut - cur
                l.append(self.data[last_cut:end_cut])
            else:
                while i < len(self.positions) and self.positions[i] < 0:
                    cur = self.positions[i]
                    t = self.extradata[-cur - 1]
                    l.append(self._pack(t))
                    self.positions[i] = offset
                    # Hashes are either 20 bytes (old sha1s) or 32
                    # bytes (new non-sha1).
                    hlen = 20
                    if len(t[1]) > 25:
                        hlen = 32
                    if len(t[1]) > hlen:
                        self.extrainfo[i] = ord(t[1][hlen + 1])
                    offset += len(l[-1])
                    i += 1
        self.data = b''.join(l)
        self.hasremovals = False
        self.extradata = []

    def _pack(self, d):
        n = d[1]
        assert len(n) in (20, 32)
        return d[0] + b'\x00' + hex(n) + d[2] + b'\n'

    def text(self):
        self._compact()
        return self.data

    def diff(self, m2, clean=False):
        '''Finds changes between the current manifest and m2.'''
        # XXX think whether efficiency matters here
        diff = {}

        for fn, e1, flags in self.iterentries():
            if fn not in m2:
                diff[fn] = (e1, flags), (None, b'')
            else:
                e2 = m2[fn]
                if (e1, flags) != e2:
                    diff[fn] = (e1, flags), e2
                elif clean:
                    diff[fn] = None

        for fn, e2, flags in m2.iterentries():
            if fn not in self:
                diff[fn] = (None, b''), (e2, flags)

        return diff

    def iterentries(self):
        return lazymanifestiterentries(self)

    def iterkeys(self):
        return lazymanifestiter(self)

    def __iter__(self):
        return lazymanifestiter(self)

    def __len__(self):
        return len(self.positions)

    def filtercopy(self, filterfn):
        # XXX should be optimized
        c = _lazymanifest(self._nodelen, b'')
        for f, n, fl in self.iterentries():
            if filterfn(f):
                c[f] = n, fl
        return c


try:
    _lazymanifest = parsers.lazymanifest
except AttributeError:
    pass


@interfaceutil.implementer(repository.imanifestdict)
class manifestdict(object):
    def __init__(self, nodelen, data=b''):
        self._nodelen = nodelen
        self._lm = _lazymanifest(nodelen, data)

    def __getitem__(self, key):
        return self._lm[key][0]

    def find(self, key):
        return self._lm[key]

    def __len__(self):
        return len(self._lm)

    def __nonzero__(self):
        # nonzero is covered by the __len__ function, but implementing it here
        # makes it easier for extensions to override.
        return len(self._lm) != 0

    __bool__ = __nonzero__

    def __setitem__(self, key, node):
        self._lm[key] = node, self.flags(key)

    def __contains__(self, key):
        if key is None:
            return False
        return key in self._lm

    def __delitem__(self, key):
        del self._lm[key]

    def __iter__(self):
        return self._lm.__iter__()

    def iterkeys(self):
        return self._lm.iterkeys()

    def keys(self):
        return list(self.iterkeys())

    def filesnotin(self, m2, match=None):
        '''Set of files in this manifest that are not in the other'''
        if match is not None:
            match = matchmod.badmatch(match, lambda path, msg: None)
            sm2 = set(m2.walk(match))
            return {f for f in self.walk(match) if f not in sm2}
        return {f for f in self if f not in m2}

    @propertycache
    def _dirs(self):
        return pathutil.dirs(self)

    def dirs(self):
        return self._dirs

    def hasdir(self, dir):
        return dir in self._dirs

    def _filesfastpath(self, match):
        """Checks whether we can correctly and quickly iterate over matcher
        files instead of over manifest files."""
        files = match.files()
        return len(files) < 100 and (
            match.isexact()
            or (match.prefix() and all(fn in self for fn in files))
        )

    def walk(self, match):
        """Generates matching file names.

        Equivalent to manifest.matches(match).iterkeys(), but without creating
        an entirely new manifest.

        It also reports nonexistent files by marking them bad with match.bad().
        """
        if match.always():
            for f in iter(self):
                yield f
            return

        fset = set(match.files())

        # avoid the entire walk if we're only looking for specific files
        if self._filesfastpath(match):
            for fn in sorted(fset):
                if fn in self:
                    yield fn
            return

        for fn in self:
            if fn in fset:
                # specified pattern is the exact name
                fset.remove(fn)
            if match(fn):
                yield fn

        # for dirstate.walk, files=[''] means "walk the whole tree".
        # follow that here, too
        fset.discard(b'')

        for fn in sorted(fset):
            if not self.hasdir(fn):
                match.bad(fn, None)

    def _matches(self, match):
        '''generate a new manifest filtered by the match argument'''
        if match.always():
            return self.copy()

        if self._filesfastpath(match):
            m = manifestdict(self._nodelen)
            lm = self._lm
            for fn in match.files():
                if fn in lm:
                    m._lm[fn] = lm[fn]
            return m

        m = manifestdict(self._nodelen)
        m._lm = self._lm.filtercopy(match)
        return m

    def diff(self, m2, match=None, clean=False):
        """Finds changes between the current manifest and m2.

        Args:
          m2: the manifest to which this manifest should be compared.
          clean: if true, include files unchanged between these manifests
                 with a None value in the returned dictionary.

        The result is returned as a dict with filename as key and
        values of the form ((n1,fl1),(n2,fl2)), where n1/n2 is the
        nodeid in the current/other manifest and fl1/fl2 is the flag
        in the current/other manifest. Where the file does not exist,
        the nodeid will be None and the flags will be the empty
        string.
        """
        if match:
            m1 = self._matches(match)
            m2 = m2._matches(match)
            return m1.diff(m2, clean=clean)
        return self._lm.diff(m2._lm, clean)

    def setflag(self, key, flag):
        if flag not in _manifestflags:
            raise TypeError(b"Invalid manifest flag set.")
        self._lm[key] = self[key], flag

    def get(self, key, default=None):
        try:
            return self._lm[key][0]
        except KeyError:
            return default

    def flags(self, key):
        try:
            return self._lm[key][1]
        except KeyError:
            return b''

    def copy(self):
        c = manifestdict(self._nodelen)
        c._lm = self._lm.copy()
        return c

    def items(self):
        return (x[:2] for x in self._lm.iterentries())

    def iteritems(self):
        return (x[:2] for x in self._lm.iterentries())

    def iterentries(self):
        return self._lm.iterentries()

    def text(self):
        # most likely uses native version
        return self._lm.text()

    def fastdelta(self, base, changes):
        """Given a base manifest text as a bytearray and a list of changes
        relative to that text, compute a delta that can be used by revlog.
        """
        delta = []
        dstart = None
        dend = None
        dline = [b""]
        start = 0
        # zero copy representation of base as a buffer
        addbuf = util.buffer(base)

        changes = list(changes)
        if len(changes) < FASTDELTA_TEXTDIFF_THRESHOLD:
            # start with a readonly loop that finds the offset of
            # each line and creates the deltas
            for f, todelete in changes:
                # bs will either be the index of the item or the insert point
                start, end = _msearch(addbuf, f, start)
                if not todelete:
                    h, fl = self._lm[f]
                    l = b"%s\0%s%s\n" % (f, hex(h), fl)
                else:
                    if start == end:
                        # item we want to delete was not found, error out
                        raise AssertionError(
                            _(b"failed to remove %s from manifest") % f
                        )
                    l = b""
                if dstart is not None and dstart <= start and dend >= start:
                    if dend < end:
                        dend = end
                    if l:
                        dline.append(l)
                else:
                    if dstart is not None:
                        delta.append([dstart, dend, b"".join(dline)])
                    dstart = start
                    dend = end
                    dline = [l]

            if dstart is not None:
                delta.append([dstart, dend, b"".join(dline)])
            # apply the delta to the base, and get a delta for addrevision
            deltatext, arraytext = _addlistdelta(base, delta)
        else:
            # For large changes, it's much cheaper to just build the text and
            # diff it.
            arraytext = bytearray(self.text())
            deltatext = mdiff.textdiff(
                util.buffer(base), util.buffer(arraytext)
            )

        return arraytext, deltatext


def _msearch(m, s, lo=0, hi=None):
    """return a tuple (start, end) that says where to find s within m.

    If the string is found m[start:end] are the line containing
    that string.  If start == end the string was not found and
    they indicate the proper sorted insertion point.

    m should be a buffer, a memoryview or a byte string.
    s is a byte string"""

    def advance(i, c):
        while i < lenm and m[i : i + 1] != c:
            i += 1
        return i

    if not s:
        return (lo, lo)
    lenm = len(m)
    if not hi:
        hi = lenm
    while lo < hi:
        mid = (lo + hi) // 2
        start = mid
        while start > 0 and m[start - 1 : start] != b'\n':
            start -= 1
        end = advance(start, b'\0')
        if bytes(m[start:end]) < s:
            # we know that after the null there are 40 bytes of sha1
            # this translates to the bisect lo = mid + 1
            lo = advance(end + 40, b'\n') + 1
        else:
            # this translates to the bisect hi = mid
            hi = start
    end = advance(lo, b'\0')
    found = m[lo:end]
    if s == found:
        # we know that after the null there are 40 bytes of sha1
        end = advance(end + 40, b'\n')
        return (lo, end + 1)
    else:
        return (lo, lo)


def _checkforbidden(l):
    """Check filenames for illegal characters."""
    for f in l:
        if b'\n' in f or b'\r' in f:
            raise error.StorageError(
                _(b"'\\n' and '\\r' disallowed in filenames: %r")
                % pycompat.bytestr(f)
            )


# apply the changes collected during the bisect loop to our addlist
# return a delta suitable for addrevision
def _addlistdelta(addlist, x):
    # for large addlist arrays, building a new array is cheaper
    # than repeatedly modifying the existing one
    currentposition = 0
    newaddlist = bytearray()

    for start, end, content in x:
        newaddlist += addlist[currentposition:start]
        if content:
            newaddlist += bytearray(content)

        currentposition = end

    newaddlist += addlist[currentposition:]

    deltatext = b"".join(
        struct.pack(b">lll", start, end, len(content)) + content
        for start, end, content in x
    )
    return deltatext, newaddlist


def _splittopdir(f):
    if b'/' in f:
        dir, subpath = f.split(b'/', 1)
        return dir + b'/', subpath
    else:
        return b'', f


_noop = lambda s: None


@interfaceutil.implementer(repository.imanifestdict)
class treemanifest(object):
    def __init__(self, nodeconstants, dir=b'', text=b''):
        self._dir = dir
        self.nodeconstants = nodeconstants
        self._node = self.nodeconstants.nullid
        self._nodelen = self.nodeconstants.nodelen
        self._loadfunc = _noop
        self._copyfunc = _noop
        self._dirty = False
        self._dirs = {}
        self._lazydirs = {}
        # Using _lazymanifest here is a little slower than plain old dicts
        self._files = {}
        self._flags = {}
        if text:

            def readsubtree(subdir, subm):
                raise AssertionError(
                    b'treemanifest constructor only accepts flat manifests'
                )

            self.parse(text, readsubtree)
            self._dirty = True  # Mark flat manifest dirty after parsing

    def _subpath(self, path):
        return self._dir + path

    def _loadalllazy(self):
        selfdirs = self._dirs
        subpath = self._subpath
        for d, (node, readsubtree, docopy) in pycompat.iteritems(
            self._lazydirs
        ):
            if docopy:
                selfdirs[d] = readsubtree(subpath(d), node).copy()
            else:
                selfdirs[d] = readsubtree(subpath(d), node)
        self._lazydirs = {}

    def _loadlazy(self, d):
        v = self._lazydirs.get(d)
        if v:
            node, readsubtree, docopy = v
            if docopy:
                self._dirs[d] = readsubtree(self._subpath(d), node).copy()
            else:
                self._dirs[d] = readsubtree(self._subpath(d), node)
            del self._lazydirs[d]

    def _loadchildrensetlazy(self, visit):
        if not visit:
            return None
        if visit == b'all' or visit == b'this':
            self._loadalllazy()
            return None

        loadlazy = self._loadlazy
        for k in visit:
            loadlazy(k + b'/')
        return visit

    def _loaddifflazy(self, t1, t2):
        """load items in t1 and t2 if they're needed for diffing.

        The criteria currently is:
        - if it's not present in _lazydirs in either t1 or t2, load it in the
          other (it may already be loaded or it may not exist, doesn't matter)
        - if it's present in _lazydirs in both, compare the nodeid; if it
          differs, load it in both
        """
        toloadlazy = []
        for d, v1 in pycompat.iteritems(t1._lazydirs):
            v2 = t2._lazydirs.get(d)
            if not v2 or v2[0] != v1[0]:
                toloadlazy.append(d)
        for d, v1 in pycompat.iteritems(t2._lazydirs):
            if d not in t1._lazydirs:
                toloadlazy.append(d)

        for d in toloadlazy:
            t1._loadlazy(d)
            t2._loadlazy(d)

    def __len__(self):
        self._load()
        size = len(self._files)
        self._loadalllazy()
        for m in self._dirs.values():
            size += m.__len__()
        return size

    def __nonzero__(self):
        # Faster than "__len() != 0" since it avoids loading sub-manifests
        return not self._isempty()

    __bool__ = __nonzero__

    def _isempty(self):
        self._load()  # for consistency; already loaded by all callers
        # See if we can skip loading everything.
        if self._files or (
            self._dirs and any(not m._isempty() for m in self._dirs.values())
        ):
            return False
        self._loadalllazy()
        return not self._dirs or all(m._isempty() for m in self._dirs.values())

    @encoding.strmethod
    def __repr__(self):
        return (
            b'<treemanifest dir=%s, node=%s, loaded=%r, dirty=%r at 0x%x>'
            % (
                self._dir,
                hex(self._node),
                bool(self._loadfunc is _noop),
                self._dirty,
                id(self),
            )
        )

    def dir(self):
        """The directory that this tree manifest represents, including a
        trailing '/'. Empty string for the repo root directory."""
        return self._dir

    def node(self):
        """This node of this instance. nullid for unsaved instances. Should
        be updated when the instance is read or written from a revlog.
        """
        assert not self._dirty
        return self._node

    def setnode(self, node):
        self._node = node
        self._dirty = False

    def iterentries(self):
        self._load()
        self._loadalllazy()
        for p, n in sorted(
            itertools.chain(self._dirs.items(), self._files.items())
        ):
            if p in self._files:
                yield self._subpath(p), n, self._flags.get(p, b'')
            else:
                for x in n.iterentries():
                    yield x

    def items(self):
        self._load()
        self._loadalllazy()
        for p, n in sorted(
            itertools.chain(self._dirs.items(), self._files.items())
        ):
            if p in self._files:
                yield self._subpath(p), n
            else:
                for f, sn in pycompat.iteritems(n):
                    yield f, sn

    iteritems = items

    def iterkeys(self):
        self._load()
        self._loadalllazy()
        for p in sorted(itertools.chain(self._dirs, self._files)):
            if p in self._files:
                yield self._subpath(p)
            else:
                for f in self._dirs[p]:
                    yield f

    def keys(self):
        return list(self.iterkeys())

    def __iter__(self):
        return self.iterkeys()

    def __contains__(self, f):
        if f is None:
            return False
        self._load()
        dir, subpath = _splittopdir(f)
        if dir:
            self._loadlazy(dir)

            if dir not in self._dirs:
                return False

            return self._dirs[dir].__contains__(subpath)
        else:
            return f in self._files

    def get(self, f, default=None):
        self._load()
        dir, subpath = _splittopdir(f)
        if dir:
            self._loadlazy(dir)

            if dir not in self._dirs:
                return default
            return self._dirs[dir].get(subpath, default)
        else:
            return self._files.get(f, default)

    def __getitem__(self, f):
        self._load()
        dir, subpath = _splittopdir(f)
        if dir:
            self._loadlazy(dir)

            return self._dirs[dir].__getitem__(subpath)
        else:
            return self._files[f]

    def flags(self, f):
        self._load()
        dir, subpath = _splittopdir(f)
        if dir:
            self._loadlazy(dir)

            if dir not in self._dirs:
                return b''
            return self._dirs[dir].flags(subpath)
        else:
            if f in self._lazydirs or f in self._dirs:
                return b''
            return self._flags.get(f, b'')

    def find(self, f):
        self._load()
        dir, subpath = _splittopdir(f)
        if dir:
            self._loadlazy(dir)

            return self._dirs[dir].find(subpath)
        else:
            return self._files[f], self._flags.get(f, b'')

    def __delitem__(self, f):
        self._load()
        dir, subpath = _splittopdir(f)
        if dir:
            self._loadlazy(dir)

            self._dirs[dir].__delitem__(subpath)
            # If the directory is now empty, remove it
            if self._dirs[dir]._isempty():
                del self._dirs[dir]
        else:
            del self._files[f]
            if f in self._flags:
                del self._flags[f]
        self._dirty = True

    def __setitem__(self, f, n):
        assert n is not None
        self._load()
        dir, subpath = _splittopdir(f)
        if dir:
            self._loadlazy(dir)
            if dir not in self._dirs:
                self._dirs[dir] = treemanifest(
                    self.nodeconstants, self._subpath(dir)
                )
            self._dirs[dir].__setitem__(subpath, n)
        else:
            # manifest nodes are either 20 bytes or 32 bytes,
            # depending on the hash in use. Assert this as historically
            # sometimes extra bytes were added.
            assert len(n) in (20, 32)
            self._files[f] = n
        self._dirty = True

    def _load(self):
        if self._loadfunc is not _noop:
            lf, self._loadfunc = self._loadfunc, _noop
            lf(self)
        elif self._copyfunc is not _noop:
            cf, self._copyfunc = self._copyfunc, _noop
            cf(self)

    def setflag(self, f, flags):
        """Set the flags (symlink, executable) for path f."""
        if flags not in _manifestflags:
            raise TypeError(b"Invalid manifest flag set.")
        self._load()
        dir, subpath = _splittopdir(f)
        if dir:
            self._loadlazy(dir)
            if dir not in self._dirs:
                self._dirs[dir] = treemanifest(
                    self.nodeconstants, self._subpath(dir)
                )
            self._dirs[dir].setflag(subpath, flags)
        else:
            self._flags[f] = flags
        self._dirty = True

    def copy(self):
        copy = treemanifest(self.nodeconstants, self._dir)
        copy._node = self._node
        copy._dirty = self._dirty
        if self._copyfunc is _noop:

            def _copyfunc(s):
                self._load()
                s._lazydirs = {
                    d: (n, r, True)
                    for d, (n, r, c) in pycompat.iteritems(self._lazydirs)
                }
                sdirs = s._dirs
                for d, v in pycompat.iteritems(self._dirs):
                    sdirs[d] = v.copy()
                s._files = dict.copy(self._files)
                s._flags = dict.copy(self._flags)

            if self._loadfunc is _noop:
                _copyfunc(copy)
            else:
                copy._copyfunc = _copyfunc
        else:
            copy._copyfunc = self._copyfunc
        return copy

    def filesnotin(self, m2, match=None):
        '''Set of files in this manifest that are not in the other'''
        if match and not match.always():
            m1 = self._matches(match)
            m2 = m2._matches(match)
            return m1.filesnotin(m2)

        files = set()

        def _filesnotin(t1, t2):
            if t1._node == t2._node and not t1._dirty and not t2._dirty:
                return
            t1._load()
            t2._load()
            self._loaddifflazy(t1, t2)
            for d, m1 in pycompat.iteritems(t1._dirs):
                if d in t2._dirs:
                    m2 = t2._dirs[d]
                    _filesnotin(m1, m2)
                else:
                    files.update(m1.iterkeys())

            for fn in t1._files:
                if fn not in t2._files:
                    files.add(t1._subpath(fn))

        _filesnotin(self, m2)
        return files

    @propertycache
    def _alldirs(self):
        return pathutil.dirs(self)

    def dirs(self):
        return self._alldirs

    def hasdir(self, dir):
        self._load()
        topdir, subdir = _splittopdir(dir)
        if topdir:
            self._loadlazy(topdir)
            if topdir in self._dirs:
                return self._dirs[topdir].hasdir(subdir)
            return False
        dirslash = dir + b'/'
        return dirslash in self._dirs or dirslash in self._lazydirs

    def walk(self, match):
        """Generates matching file names.

        It also reports nonexistent files by marking them bad with match.bad().
        """
        if match.always():
            for f in iter(self):
                yield f
            return

        fset = set(match.files())

        for fn in self._walk(match):
            if fn in fset:
                # specified pattern is the exact name
                fset.remove(fn)
            yield fn

        # for dirstate.walk, files=[''] means "walk the whole tree".
        # follow that here, too
        fset.discard(b'')

        for fn in sorted(fset):
            if not self.hasdir(fn):
                match.bad(fn, None)

    def _walk(self, match):
        '''Recursively generates matching file names for walk().'''
        visit = match.visitchildrenset(self._dir[:-1])
        if not visit:
            return

        # yield this dir's files and walk its submanifests
        self._load()
        visit = self._loadchildrensetlazy(visit)
        for p in sorted(list(self._dirs) + list(self._files)):
            if p in self._files:
                fullp = self._subpath(p)
                if match(fullp):
                    yield fullp
            else:
                if not visit or p[:-1] in visit:
                    for f in self._dirs[p]._walk(match):
                        yield f

    def _matches(self, match):
        """recursively generate a new manifest filtered by the match argument."""
        if match.always():
            return self.copy()
        return self._matches_inner(match)

    def _matches_inner(self, match):
        if match.always():
            return self.copy()

        visit = match.visitchildrenset(self._dir[:-1])
        if visit == b'all':
            return self.copy()
        ret = treemanifest(self.nodeconstants, self._dir)
        if not visit:
            return ret

        self._load()
        for fn in self._files:
            # While visitchildrenset *usually* lists only subdirs, this is
            # actually up to the matcher and may have some files in the set().
            # If visit == 'this', we should obviously look at the files in this
            # directory; if visit is a set, and fn is in it, we should inspect
            # fn (but no need to inspect things not in the set).
            if visit != b'this' and fn not in visit:
                continue
            fullp = self._subpath(fn)
            # visitchildrenset isn't perfect, we still need to call the regular
            # matcher code to further filter results.
            if not match(fullp):
                continue
            ret._files[fn] = self._files[fn]
            if fn in self._flags:
                ret._flags[fn] = self._flags[fn]

        visit = self._loadchildrensetlazy(visit)
        for dir, subm in pycompat.iteritems(self._dirs):
            if visit and dir[:-1] not in visit:
                continue
            m = subm._matches_inner(match)
            if not m._isempty():
                ret._dirs[dir] = m

        if not ret._isempty():
            ret._dirty = True
        return ret

    def fastdelta(self, base, changes):
        raise FastdeltaUnavailable()

    def diff(self, m2, match=None, clean=False):
        """Finds changes between the current manifest and m2.

        Args:
          m2: the manifest to which this manifest should be compared.
          clean: if true, include files unchanged between these manifests
                 with a None value in the returned dictionary.

        The result is returned as a dict with filename as key and
        values of the form ((n1,fl1),(n2,fl2)), where n1/n2 is the
        nodeid in the current/other manifest and fl1/fl2 is the flag
        in the current/other manifest. Where the file does not exist,
        the nodeid will be None and the flags will be the empty
        string.
        """
        if match and not match.always():
            m1 = self._matches(match)
            m2 = m2._matches(match)
            return m1.diff(m2, clean=clean)
        result = {}
        emptytree = treemanifest(self.nodeconstants)

        def _iterativediff(t1, t2, stack):
            """compares two tree manifests and append new tree-manifests which
            needs to be compared to stack"""
            if t1._node == t2._node and not t1._dirty and not t2._dirty:
                return
            t1._load()
            t2._load()
            self._loaddifflazy(t1, t2)

            for d, m1 in pycompat.iteritems(t1._dirs):
                m2 = t2._dirs.get(d, emptytree)
                stack.append((m1, m2))

            for d, m2 in pycompat.iteritems(t2._dirs):
                if d not in t1._dirs:
                    stack.append((emptytree, m2))

            for fn, n1 in pycompat.iteritems(t1._files):
                fl1 = t1._flags.get(fn, b'')
                n2 = t2._files.get(fn, None)
                fl2 = t2._flags.get(fn, b'')
                if n1 != n2 or fl1 != fl2:
                    result[t1._subpath(fn)] = ((n1, fl1), (n2, fl2))
                elif clean:
                    result[t1._subpath(fn)] = None

            for fn, n2 in pycompat.iteritems(t2._files):
                if fn not in t1._files:
                    fl2 = t2._flags.get(fn, b'')
                    result[t2._subpath(fn)] = ((None, b''), (n2, fl2))

        stackls = []
        _iterativediff(self, m2, stackls)
        while stackls:
            t1, t2 = stackls.pop()
            # stackls is populated in the function call
            _iterativediff(t1, t2, stackls)
        return result

    def unmodifiedsince(self, m2):
        return not self._dirty and not m2._dirty and self._node == m2._node

    def parse(self, text, readsubtree):
        selflazy = self._lazydirs
        for f, n, fl in _parse(self._nodelen, text):
            if fl == b't':
                f = f + b'/'
                # False below means "doesn't need to be copied" and can use the
                # cached value from readsubtree directly.
                selflazy[f] = (n, readsubtree, False)
            elif b'/' in f:
                # This is a flat manifest, so use __setitem__ and setflag rather
                # than assigning directly to _files and _flags, so we can
                # assign a path in a subdirectory, and to mark dirty (compared
                # to nullid).
                self[f] = n
                if fl:
                    self.setflag(f, fl)
            else:
                # Assigning to _files and _flags avoids marking as dirty,
                # and should be a little faster.
                self._files[f] = n
                if fl:
                    self._flags[f] = fl

    def text(self):
        """Get the full data of this manifest as a bytestring."""
        self._load()
        return _text(self.iterentries())

    def dirtext(self):
        """Get the full data of this directory as a bytestring. Make sure that
        any submanifests have been written first, so their nodeids are correct.
        """
        self._load()
        flags = self.flags
        lazydirs = [
            (d[:-1], v[0], b't') for d, v in pycompat.iteritems(self._lazydirs)
        ]
        dirs = [(d[:-1], self._dirs[d]._node, b't') for d in self._dirs]
        files = [(f, self._files[f], flags(f)) for f in self._files]
        return _text(sorted(dirs + files + lazydirs))

    def read(self, gettext, readsubtree):
        def _load_for_read(s):
            s.parse(gettext(), readsubtree)
            s._dirty = False

        self._loadfunc = _load_for_read

    def writesubtrees(self, m1, m2, writesubtree, match):
        self._load()  # for consistency; should never have any effect here
        m1._load()
        m2._load()
        emptytree = treemanifest(self.nodeconstants)

        def getnode(m, d):
            ld = m._lazydirs.get(d)
            if ld:
                return ld[0]
            return m._dirs.get(d, emptytree)._node

        # let's skip investigating things that `match` says we do not need.
        visit = match.visitchildrenset(self._dir[:-1])
        visit = self._loadchildrensetlazy(visit)
        if visit == b'this' or visit == b'all':
            visit = None
        for d, subm in pycompat.iteritems(self._dirs):
            if visit and d[:-1] not in visit:
                continue
            subp1 = getnode(m1, d)
            subp2 = getnode(m2, d)
            if subp1 == self.nodeconstants.nullid:
                subp1, subp2 = subp2, subp1
            writesubtree(subm, subp1, subp2, match)

    def walksubtrees(self, matcher=None):
        """Returns an iterator of the subtrees of this manifest, including this
        manifest itself.

        If `matcher` is provided, it only returns subtrees that match.
        """
        if matcher and not matcher.visitdir(self._dir[:-1]):
            return
        if not matcher or matcher(self._dir[:-1]):
            yield self

        self._load()
        # OPT: use visitchildrenset to avoid loading everything.
        self._loadalllazy()
        for d, subm in pycompat.iteritems(self._dirs):
            for subtree in subm.walksubtrees(matcher=matcher):
                yield subtree


class manifestfulltextcache(util.lrucachedict):
    """File-backed LRU cache for the manifest cache

    File consists of entries, up to EOF:

    - 20 bytes node, 4 bytes length, <length> manifest data

    These are written in reverse cache order (oldest to newest).

    """

    _file = b'manifestfulltextcache'

    def __init__(self, max):
        super(manifestfulltextcache, self).__init__(max)
        self._dirty = False
        self._read = False
        self._opener = None

    def read(self):
        if self._read or self._opener is None:
            return

        try:
            with self._opener(self._file) as fp:
                set = super(manifestfulltextcache, self).__setitem__
                # ignore trailing data, this is a cache, corruption is skipped
                while True:
                    # TODO do we need to do work here for sha1 portability?
                    node = fp.read(20)
                    if len(node) < 20:
                        break
                    try:
                        size = struct.unpack(b'>L', fp.read(4))[0]
                    except struct.error:
                        break
                    value = bytearray(fp.read(size))
                    if len(value) != size:
                        break
                    set(node, value)
        except IOError:
            # the file is allowed to be missing
            pass

        self._read = True
        self._dirty = False

    def write(self):
        if not self._dirty or self._opener is None:
            return
        # rotate backwards to the first used node
        try:
            with self._opener(
                self._file, b'w', atomictemp=True, checkambig=True
            ) as fp:
                node = self._head.prev
                while True:
                    if node.key in self._cache:
                        fp.write(node.key)
                        fp.write(struct.pack(b'>L', len(node.value)))
                        fp.write(node.value)
                    if node is self._head:
                        break
                    node = node.prev
        except IOError:
            # We could not write the cache (eg: permission error)
            # the content can be missing.
            #
            # We could try harder and see if we could recreate a wcache
            # directory were we coudl write too.
            #
            # XXX the error pass silently, having some way to issue an error
            # log `ui.log` would be nice.
            pass

    def __len__(self):
        if not self._read:
            self.read()
        return super(manifestfulltextcache, self).__len__()

    def __contains__(self, k):
        if not self._read:
            self.read()
        return super(manifestfulltextcache, self).__contains__(k)

    def __iter__(self):
        if not self._read:
            self.read()
        return super(manifestfulltextcache, self).__iter__()

    def __getitem__(self, k):
        if not self._read:
            self.read()
        # the cache lru order can change on read
        setdirty = self._cache.get(k) is not self._head
        value = super(manifestfulltextcache, self).__getitem__(k)
        if setdirty:
            self._dirty = True
        return value

    def __setitem__(self, k, v):
        if not self._read:
            self.read()
        super(manifestfulltextcache, self).__setitem__(k, v)
        self._dirty = True

    def __delitem__(self, k):
        if not self._read:
            self.read()
        super(manifestfulltextcache, self).__delitem__(k)
        self._dirty = True

    def get(self, k, default=None):
        if not self._read:
            self.read()
        return super(manifestfulltextcache, self).get(k, default=default)

    def clear(self, clear_persisted_data=False):
        super(manifestfulltextcache, self).clear()
        if clear_persisted_data:
            self._dirty = True
            self.write()
        self._read = False


# and upper bound of what we expect from compression
# (real live value seems to be "3")
MAXCOMPRESSION = 3


class FastdeltaUnavailable(Exception):
    """Exception raised when fastdelta isn't usable on a manifest."""


@interfaceutil.implementer(repository.imanifeststorage)
class manifestrevlog(object):
    """A revlog that stores manifest texts. This is responsible for caching the
    full-text manifest contents.
    """

    def __init__(
        self,
        nodeconstants,
        opener,
        tree=b'',
        dirlogcache=None,
        treemanifest=False,
    ):
        """Constructs a new manifest revlog

        `indexfile` - used by extensions to have two manifests at once, like
        when transitioning between flatmanifeset and treemanifests.

        `treemanifest` - used to indicate this is a tree manifest revlog. Opener
        options can also be used to make this a tree manifest revlog. The opener
        option takes precedence, so if it is set to True, we ignore whatever
        value is passed in to the constructor.
        """
        self.nodeconstants = nodeconstants
        # During normal operations, we expect to deal with not more than four
        # revs at a time (such as during commit --amend). When rebasing large
        # stacks of commits, the number can go up, hence the config knob below.
        cachesize = 4
        optiontreemanifest = False
        opts = getattr(opener, 'options', None)
        if opts is not None:
            cachesize = opts.get(b'manifestcachesize', cachesize)
            optiontreemanifest = opts.get(b'treemanifest', False)

        self._treeondisk = optiontreemanifest or treemanifest

        self._fulltextcache = manifestfulltextcache(cachesize)

        if tree:
            assert self._treeondisk, b'opts is %r' % opts

        radix = b'00manifest'
        if tree:
            radix = b"meta/" + tree + radix

        self.tree = tree

        # The dirlogcache is kept on the root manifest log
        if tree:
            self._dirlogcache = dirlogcache
        else:
            self._dirlogcache = {b'': self}

        self._revlog = revlog.revlog(
            opener,
            target=(revlog_constants.KIND_MANIFESTLOG, self.tree),
            radix=radix,
            # only root indexfile is cached
            checkambig=not bool(tree),
            mmaplargeindex=True,
            upperboundcomp=MAXCOMPRESSION,
            persistentnodemap=opener.options.get(b'persistent-nodemap', False),
        )

        self.index = self._revlog.index
        self._generaldelta = self._revlog._generaldelta

    def _setupmanifestcachehooks(self, repo):
        """Persist the manifestfulltextcache on lock release"""
        if not util.safehasattr(repo, b'_wlockref'):
            return

        self._fulltextcache._opener = repo.wcachevfs
        if repo._currentlock(repo._wlockref) is None:
            return

        reporef = weakref.ref(repo)
        manifestrevlogref = weakref.ref(self)

        def persistmanifestcache(success):
            # Repo is in an unknown state, do not persist.
            if not success:
                return

            repo = reporef()
            self = manifestrevlogref()
            if repo is None or self is None:
                return
            if repo.manifestlog.getstorage(b'') is not self:
                # there's a different manifest in play now, abort
                return
            self._fulltextcache.write()

        repo._afterlock(persistmanifestcache)

    @property
    def fulltextcache(self):
        return self._fulltextcache

    def clearcaches(self, clear_persisted_data=False):
        self._revlog.clearcaches()
        self._fulltextcache.clear(clear_persisted_data=clear_persisted_data)
        self._dirlogcache = {self.tree: self}

    def dirlog(self, d):
        if d:
            assert self._treeondisk
        if d not in self._dirlogcache:
            mfrevlog = manifestrevlog(
                self.nodeconstants,
                self.opener,
                d,
                self._dirlogcache,
                treemanifest=self._treeondisk,
            )
            self._dirlogcache[d] = mfrevlog
        return self._dirlogcache[d]

    def add(
        self,
        m,
        transaction,
        link,
        p1,
        p2,
        added,
        removed,
        readtree=None,
        match=None,
    ):
        """add some manifest entry in to the manifest log

        input:

          m:           the manifest dict we want to store
          transaction: the open transaction
          p1:          manifest-node of p1
          p2:          manifest-node of p2
          added:       file added/changed compared to parent
          removed:     file removed compared to parent

        tree manifest input:

          readtree:    a function to read a subtree
          match:       a filematcher for the subpart of the tree manifest
        """
        try:
            if p1 not in self.fulltextcache:
                raise FastdeltaUnavailable()
            # If our first parent is in the manifest cache, we can
            # compute a delta here using properties we know about the
            # manifest up-front, which may save time later for the
            # revlog layer.

            _checkforbidden(added)
            # combine the changed lists into one sorted iterator
            work = heapq.merge(
                [(x, False) for x in sorted(added)],
                [(x, True) for x in sorted(removed)],
            )

            arraytext, deltatext = m.fastdelta(self.fulltextcache[p1], work)
            cachedelta = self._revlog.rev(p1), deltatext
            text = util.buffer(arraytext)
            rev = self._revlog.addrevision(
                text, transaction, link, p1, p2, cachedelta
            )
            n = self._revlog.node(rev)
        except FastdeltaUnavailable:
            # The first parent manifest isn't already loaded or the
            # manifest implementation doesn't support fastdelta, so
            # we'll just encode a fulltext of the manifest and pass
            # that through to the revlog layer, and let it handle the
            # delta process.
            if self._treeondisk:
                assert readtree, b"readtree must be set for treemanifest writes"
                assert match, b"match must be specified for treemanifest writes"
                m1 = readtree(self.tree, p1)
                m2 = readtree(self.tree, p2)
                n = self._addtree(
                    m, transaction, link, m1, m2, readtree, match=match
                )
                arraytext = None
            else:
                text = m.text()
                rev = self._revlog.addrevision(text, transaction, link, p1, p2)
                n = self._revlog.node(rev)
                arraytext = bytearray(text)

        if arraytext is not None:
            self.fulltextcache[n] = arraytext

        return n

    def _addtree(self, m, transaction, link, m1, m2, readtree, match):
        # If the manifest is unchanged compared to one parent,
        # don't write a new revision
        if self.tree != b'' and (
            m.unmodifiedsince(m1) or m.unmodifiedsince(m2)
        ):
            return m.node()

        def writesubtree(subm, subp1, subp2, match):
            sublog = self.dirlog(subm.dir())
            sublog.add(
                subm,
                transaction,
                link,
                subp1,
                subp2,
                None,
                None,
                readtree=readtree,
                match=match,
            )

        m.writesubtrees(m1, m2, writesubtree, match)
        text = m.dirtext()
        n = None
        if self.tree != b'':
            # Double-check whether contents are unchanged to one parent
            if text == m1.dirtext():
                n = m1.node()
            elif text == m2.dirtext():
                n = m2.node()

        if not n:
            rev = self._revlog.addrevision(
                text, transaction, link, m1.node(), m2.node()
            )
            n = self._revlog.node(rev)

        # Save nodeid so parent manifest can calculate its nodeid
        m.setnode(n)
        return n

    def __len__(self):
        return len(self._revlog)

    def __iter__(self):
        return self._revlog.__iter__()

    def rev(self, node):
        return self._revlog.rev(node)

    def node(self, rev):
        return self._revlog.node(rev)

    def lookup(self, value):
        return self._revlog.lookup(value)

    def parentrevs(self, rev):
        return self._revlog.parentrevs(rev)

    def parents(self, node):
        return self._revlog.parents(node)

    def linkrev(self, rev):
        return self._revlog.linkrev(rev)

    def checksize(self):
        return self._revlog.checksize()

    def revision(self, node, _df=None, raw=False):
        return self._revlog.revision(node, _df=_df, raw=raw)

    def rawdata(self, node, _df=None):
        return self._revlog.rawdata(node, _df=_df)

    def revdiff(self, rev1, rev2):
        return self._revlog.revdiff(rev1, rev2)

    def cmp(self, node, text):
        return self._revlog.cmp(node, text)

    def deltaparent(self, rev):
        return self._revlog.deltaparent(rev)

    def emitrevisions(
        self,
        nodes,
        nodesorder=None,
        revisiondata=False,
        assumehaveparentrevisions=False,
        deltamode=repository.CG_DELTAMODE_STD,
        sidedata_helpers=None,
    ):
        return self._revlog.emitrevisions(
            nodes,
            nodesorder=nodesorder,
            revisiondata=revisiondata,
            assumehaveparentrevisions=assumehaveparentrevisions,
            deltamode=deltamode,
            sidedata_helpers=sidedata_helpers,
        )

    def addgroup(
        self,
        deltas,
        linkmapper,
        transaction,
        alwayscache=False,
        addrevisioncb=None,
        duplicaterevisioncb=None,
    ):
        return self._revlog.addgroup(
            deltas,
            linkmapper,
            transaction,
            alwayscache=alwayscache,
            addrevisioncb=addrevisioncb,
            duplicaterevisioncb=duplicaterevisioncb,
        )

    def rawsize(self, rev):
        return self._revlog.rawsize(rev)

    def getstrippoint(self, minlink):
        return self._revlog.getstrippoint(minlink)

    def strip(self, minlink, transaction):
        return self._revlog.strip(minlink, transaction)

    def files(self):
        return self._revlog.files()

    def clone(self, tr, destrevlog, **kwargs):
        if not isinstance(destrevlog, manifestrevlog):
            raise error.ProgrammingError(b'expected manifestrevlog to clone()')

        return self._revlog.clone(tr, destrevlog._revlog, **kwargs)

    def storageinfo(
        self,
        exclusivefiles=False,
        sharedfiles=False,
        revisionscount=False,
        trackedsize=False,
        storedsize=False,
    ):
        return self._revlog.storageinfo(
            exclusivefiles=exclusivefiles,
            sharedfiles=sharedfiles,
            revisionscount=revisionscount,
            trackedsize=trackedsize,
            storedsize=storedsize,
        )

    @property
    def opener(self):
        return self._revlog.opener

    @opener.setter
    def opener(self, value):
        self._revlog.opener = value


@interfaceutil.implementer(repository.imanifestlog)
class manifestlog(object):
    """A collection class representing the collection of manifest snapshots
    referenced by commits in the repository.

    In this situation, 'manifest' refers to the abstract concept of a snapshot
    of the list of files in the given commit. Consumers of the output of this
    class do not care about the implementation details of the actual manifests
    they receive (i.e. tree or flat or lazily loaded, etc)."""

    def __init__(self, opener, repo, rootstore, narrowmatch):
        self.nodeconstants = repo.nodeconstants
        usetreemanifest = False
        cachesize = 4

        opts = getattr(opener, 'options', None)
        if opts is not None:
            usetreemanifest = opts.get(b'treemanifest', usetreemanifest)
            cachesize = opts.get(b'manifestcachesize', cachesize)

        self._treemanifests = usetreemanifest

        self._rootstore = rootstore
        self._rootstore._setupmanifestcachehooks(repo)
        self._narrowmatch = narrowmatch

        # A cache of the manifestctx or treemanifestctx for each directory
        self._dirmancache = {}
        self._dirmancache[b''] = util.lrucachedict(cachesize)

        self._cachesize = cachesize

    def __getitem__(self, node):
        """Retrieves the manifest instance for the given node. Throws a
        LookupError if not found.
        """
        return self.get(b'', node)

    def get(self, tree, node, verify=True):
        """Retrieves the manifest instance for the given node. Throws a
        LookupError if not found.

        `verify` - if True an exception will be thrown if the node is not in
                   the revlog
        """
        if node in self._dirmancache.get(tree, ()):
            return self._dirmancache[tree][node]

        if not self._narrowmatch.always():
            if not self._narrowmatch.visitdir(tree[:-1]):
                return excludeddirmanifestctx(self.nodeconstants, tree, node)
        if tree:
            if self._rootstore._treeondisk:
                if verify:
                    # Side-effect is LookupError is raised if node doesn't
                    # exist.
                    self.getstorage(tree).rev(node)

                m = treemanifestctx(self, tree, node)
            else:
                raise error.Abort(
                    _(
                        b"cannot ask for manifest directory '%s' in a flat "
                        b"manifest"
                    )
                    % tree
                )
        else:
            if verify:
                # Side-effect is LookupError is raised if node doesn't exist.
                self._rootstore.rev(node)

            if self._treemanifests:
                m = treemanifestctx(self, b'', node)
            else:
                m = manifestctx(self, node)

        if node != self.nodeconstants.nullid:
            mancache = self._dirmancache.get(tree)
            if not mancache:
                mancache = util.lrucachedict(self._cachesize)
                self._dirmancache[tree] = mancache
            mancache[node] = m
        return m

    def getstorage(self, tree):
        return self._rootstore.dirlog(tree)

    def clearcaches(self, clear_persisted_data=False):
        self._dirmancache.clear()
        self._rootstore.clearcaches(clear_persisted_data=clear_persisted_data)

    def rev(self, node):
        return self._rootstore.rev(node)

    def update_caches(self, transaction):
        return self._rootstore._revlog.update_caches(transaction=transaction)


@interfaceutil.implementer(repository.imanifestrevisionwritable)
class memmanifestctx(object):
    def __init__(self, manifestlog):
        self._manifestlog = manifestlog
        self._manifestdict = manifestdict(manifestlog.nodeconstants.nodelen)

    def _storage(self):
        return self._manifestlog.getstorage(b'')

    def copy(self):
        memmf = memmanifestctx(self._manifestlog)
        memmf._manifestdict = self.read().copy()
        return memmf

    def read(self):
        return self._manifestdict

    def write(self, transaction, link, p1, p2, added, removed, match=None):
        return self._storage().add(
            self._manifestdict,
            transaction,
            link,
            p1,
            p2,
            added,
            removed,
            match=match,
        )


@interfaceutil.implementer(repository.imanifestrevisionstored)
class manifestctx(object):
    """A class representing a single revision of a manifest, including its
    contents, its parent revs, and its linkrev.
    """

    def __init__(self, manifestlog, node):
        self._manifestlog = manifestlog
        self._data = None

        self._node = node

        # TODO: We eventually want p1, p2, and linkrev exposed on this class,
        # but let's add it later when something needs it and we can load it
        # lazily.
        # self.p1, self.p2 = store.parents(node)
        # rev = store.rev(node)
        # self.linkrev = store.linkrev(rev)

    def _storage(self):
        return self._manifestlog.getstorage(b'')

    def node(self):
        return self._node

    def copy(self):
        memmf = memmanifestctx(self._manifestlog)
        memmf._manifestdict = self.read().copy()
        return memmf

    @propertycache
    def parents(self):
        return self._storage().parents(self._node)

    def read(self):
        if self._data is None:
            nc = self._manifestlog.nodeconstants
            if self._node == nc.nullid:
                self._data = manifestdict(nc.nodelen)
            else:
                store = self._storage()
                if self._node in store.fulltextcache:
                    text = pycompat.bytestr(store.fulltextcache[self._node])
                else:
                    text = store.revision(self._node)
                    arraytext = bytearray(text)
                    store.fulltextcache[self._node] = arraytext
                self._data = manifestdict(nc.nodelen, text)
        return self._data

    def readfast(self, shallow=False):
        """Calls either readdelta or read, based on which would be less work.
        readdelta is called if the delta is against the p1, and therefore can be
        read quickly.

        If `shallow` is True, nothing changes since this is a flat manifest.
        """
        store = self._storage()
        r = store.rev(self._node)
        deltaparent = store.deltaparent(r)
        if deltaparent != nullrev and deltaparent in store.parentrevs(r):
            return self.readdelta()
        return self.read()

    def readdelta(self, shallow=False):
        """Returns a manifest containing just the entries that are present
        in this manifest, but not in its p1 manifest. This is efficient to read
        if the revlog delta is already p1.

        Changing the value of `shallow` has no effect on flat manifests.
        """
        store = self._storage()
        r = store.rev(self._node)
        d = mdiff.patchtext(store.revdiff(store.deltaparent(r), r))
        return manifestdict(store.nodeconstants.nodelen, d)

    def find(self, key):
        return self.read().find(key)


@interfaceutil.implementer(repository.imanifestrevisionwritable)
class memtreemanifestctx(object):
    def __init__(self, manifestlog, dir=b''):
        self._manifestlog = manifestlog
        self._dir = dir
        self._treemanifest = treemanifest(manifestlog.nodeconstants)

    def _storage(self):
        return self._manifestlog.getstorage(b'')

    def copy(self):
        memmf = memtreemanifestctx(self._manifestlog, dir=self._dir)
        memmf._treemanifest = self._treemanifest.copy()
        return memmf

    def read(self):
        return self._treemanifest

    def write(self, transaction, link, p1, p2, added, removed, match=None):
        def readtree(dir, node):
            return self._manifestlog.get(dir, node).read()

        return self._storage().add(
            self._treemanifest,
            transaction,
            link,
            p1,
            p2,
            added,
            removed,
            readtree=readtree,
            match=match,
        )


@interfaceutil.implementer(repository.imanifestrevisionstored)
class treemanifestctx(object):
    def __init__(self, manifestlog, dir, node):
        self._manifestlog = manifestlog
        self._dir = dir
        self._data = None

        self._node = node

        # TODO: Load p1/p2/linkrev lazily. They need to be lazily loaded so that
        # we can instantiate treemanifestctx objects for directories we don't
        # have on disk.
        # self.p1, self.p2 = store.parents(node)
        # rev = store.rev(node)
        # self.linkrev = store.linkrev(rev)

    def _storage(self):
        narrowmatch = self._manifestlog._narrowmatch
        if not narrowmatch.always():
            if not narrowmatch.visitdir(self._dir[:-1]):
                return excludedmanifestrevlog(
                    self._manifestlog.nodeconstants, self._dir
                )
        return self._manifestlog.getstorage(self._dir)

    def read(self):
        if self._data is None:
            store = self._storage()
            if self._node == self._manifestlog.nodeconstants.nullid:
                self._data = treemanifest(self._manifestlog.nodeconstants)
            # TODO accessing non-public API
            elif store._treeondisk:
                m = treemanifest(self._manifestlog.nodeconstants, dir=self._dir)

                def gettext():
                    return store.revision(self._node)

                def readsubtree(dir, subm):
                    # Set verify to False since we need to be able to create
                    # subtrees for trees that don't exist on disk.
                    return self._manifestlog.get(dir, subm, verify=False).read()

                m.read(gettext, readsubtree)
                m.setnode(self._node)
                self._data = m
            else:
                if self._node in store.fulltextcache:
                    text = pycompat.bytestr(store.fulltextcache[self._node])
                else:
                    text = store.revision(self._node)
                    arraytext = bytearray(text)
                    store.fulltextcache[self._node] = arraytext
                self._data = treemanifest(
                    self._manifestlog.nodeconstants, dir=self._dir, text=text
                )

        return self._data

    def node(self):
        return self._node

    def copy(self):
        memmf = memtreemanifestctx(self._manifestlog, dir=self._dir)
        memmf._treemanifest = self.read().copy()
        return memmf

    @propertycache
    def parents(self):
        return self._storage().parents(self._node)

    def readdelta(self, shallow=False):
        """Returns a manifest containing just the entries that are present
        in this manifest, but not in its p1 manifest. This is efficient to read
        if the revlog delta is already p1.

        If `shallow` is True, this will read the delta for this directory,
        without recursively reading subdirectory manifests. Instead, any
        subdirectory entry will be reported as it appears in the manifest, i.e.
        the subdirectory will be reported among files and distinguished only by
        its 't' flag.
        """
        store = self._storage()
        if shallow:
            r = store.rev(self._node)
            d = mdiff.patchtext(store.revdiff(store.deltaparent(r), r))
            return manifestdict(store.nodeconstants.nodelen, d)
        else:
            # Need to perform a slow delta
            r0 = store.deltaparent(store.rev(self._node))
            m0 = self._manifestlog.get(self._dir, store.node(r0)).read()
            m1 = self.read()
            md = treemanifest(self._manifestlog.nodeconstants, dir=self._dir)
            for f, ((n0, fl0), (n1, fl1)) in pycompat.iteritems(m0.diff(m1)):
                if n1:
                    md[f] = n1
                    if fl1:
                        md.setflag(f, fl1)
            return md

    def readfast(self, shallow=False):
        """Calls either readdelta or read, based on which would be less work.
        readdelta is called if the delta is against the p1, and therefore can be
        read quickly.

        If `shallow` is True, it only returns the entries from this manifest,
        and not any submanifests.
        """
        store = self._storage()
        r = store.rev(self._node)
        deltaparent = store.deltaparent(r)
        if deltaparent != nullrev and deltaparent in store.parentrevs(r):
            return self.readdelta(shallow=shallow)

        if shallow:
            return manifestdict(
                store.nodeconstants.nodelen, store.revision(self._node)
            )
        else:
            return self.read()

    def find(self, key):
        return self.read().find(key)


class excludeddir(treemanifest):
    """Stand-in for a directory that is excluded from the repository.

    With narrowing active on a repository that uses treemanifests,
    some of the directory revlogs will be excluded from the resulting
    clone. This is a huge storage win for clients, but means we need
    some sort of pseudo-manifest to surface to internals so we can
    detect a merge conflict outside the narrowspec. That's what this
    class is: it stands in for a directory whose node is known, but
    whose contents are unknown.
    """

    def __init__(self, nodeconstants, dir, node):
        super(excludeddir, self).__init__(nodeconstants, dir)
        self._node = node
        # Add an empty file, which will be included by iterators and such,
        # appearing as the directory itself (i.e. something like "dir/")
        self._files[b''] = node
        self._flags[b''] = b't'

    # Manifests outside the narrowspec should never be modified, so avoid
    # copying. This makes a noticeable difference when there are very many
    # directories outside the narrowspec. Also, it makes sense for the copy to
    # be of the same type as the original, which would not happen with the
    # super type's copy().
    def copy(self):
        return self


class excludeddirmanifestctx(treemanifestctx):
    """context wrapper for excludeddir - see that docstring for rationale"""

    def __init__(self, nodeconstants, dir, node):
        self.nodeconstants = nodeconstants
        self._dir = dir
        self._node = node

    def read(self):
        return excludeddir(self.nodeconstants, self._dir, self._node)

    def readfast(self, shallow=False):
        # special version of readfast since we don't have underlying storage
        return self.read()

    def write(self, *args):
        raise error.ProgrammingError(
            b'attempt to write manifest from excluded dir %s' % self._dir
        )


class excludedmanifestrevlog(manifestrevlog):
    """Stand-in for excluded treemanifest revlogs.

    When narrowing is active on a treemanifest repository, we'll have
    references to directories we can't see due to the revlog being
    skipped. This class exists to conform to the manifestrevlog
    interface for those directories and proactively prevent writes to
    outside the narrowspec.
    """

    def __init__(self, nodeconstants, dir):
        self.nodeconstants = nodeconstants
        self._dir = dir

    def __len__(self):
        raise error.ProgrammingError(
            b'attempt to get length of excluded dir %s' % self._dir
        )

    def rev(self, node):
        raise error.ProgrammingError(
            b'attempt to get rev from excluded dir %s' % self._dir
        )

    def linkrev(self, node):
        raise error.ProgrammingError(
            b'attempt to get linkrev from excluded dir %s' % self._dir
        )

    def node(self, rev):
        raise error.ProgrammingError(
            b'attempt to get node from excluded dir %s' % self._dir
        )

    def add(self, *args, **kwargs):
        # We should never write entries in dirlogs outside the narrow clone.
        # However, the method still gets called from writesubtree() in
        # _addtree(), so we need to handle it. We should possibly make that
        # avoid calling add() with a clean manifest (_dirty is always False
        # in excludeddir instances).
        pass
