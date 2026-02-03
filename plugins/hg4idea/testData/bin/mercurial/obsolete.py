# obsolete.py - obsolete markers handling
#
# Copyright 2012 Pierre-Yves David <pierre-yves.david@ens-lyon.org>
#                Logilab SA        <contact@logilab.fr>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Obsolete marker handling

An obsolete marker maps an old changeset to a list of new
changesets. If the list of new changesets is empty, the old changeset
is said to be "killed". Otherwise, the old changeset is being
"replaced" by the new changesets.

Obsolete markers can be used to record and distribute changeset graph
transformations performed by history rewrite operations, and help
building new tools to reconcile conflicting rewrite actions. To
facilitate conflict resolution, markers include various annotations
besides old and news changeset identifiers, such as creation date or
author name.

The old obsoleted changeset is called a "predecessor" and possible
replacements are called "successors". Markers that used changeset X as
a predecessor are called "successor markers of X" because they hold
information about the successors of X. Markers that use changeset Y as
a successors are call "predecessor markers of Y" because they hold
information about the predecessors of Y.

Examples:

- When changeset A is replaced by changeset A', one marker is stored:

    (A, (A',))

- When changesets A and B are folded into a new changeset C, two markers are
  stored:

    (A, (C,)) and (B, (C,))

- When changeset A is simply "pruned" from the graph, a marker is created:

    (A, ())

- When changeset A is split into B and C, a single marker is used:

    (A, (B, C))

  We use a single marker to distinguish the "split" case from the "divergence"
  case. If two independent operations rewrite the same changeset A in to A' and
  A'', we have an error case: divergent rewriting. We can detect it because
  two markers will be created independently:

  (A, (B,)) and (A, (C,))

Format
------

Markers are stored in an append-only file stored in
'.hg/store/obsstore'.

The file starts with a version header:

- 1 unsigned byte: version number, starting at zero.

The header is followed by the markers. Marker format depend of the version. See
comment associated with each format for details.

"""

import binascii
import struct
import weakref

from .i18n import _
from .node import (
    bin,
    hex,
)
from . import (
    encoding,
    error,
    obsutil,
    phases,
    policy,
    pycompat,
    util,
)
from .utils import (
    dateutil,
    hashutil,
)

parsers = policy.importmod('parsers')

_pack = struct.pack
_unpack = struct.unpack
_calcsize = struct.calcsize
propertycache = util.propertycache

# Options for obsolescence
createmarkersopt = b'createmarkers'
allowunstableopt = b'allowunstable'
allowdivergenceopt = b'allowdivergence'
exchangeopt = b'exchange'


def _getoptionvalue(repo, option):
    """Returns True if the given repository has the given obsolete option
    enabled.
    """
    configkey = b'evolution.%s' % option
    newconfig = repo.ui.configbool(b'experimental', configkey)

    # Return the value only if defined
    if newconfig is not None:
        return newconfig

    # Fallback on generic option
    try:
        return repo.ui.configbool(b'experimental', b'evolution')
    except (error.ConfigError, AttributeError):
        # Fallback on old-fashion config
        # inconsistent config: experimental.evolution
        result = set(repo.ui.configlist(b'experimental', b'evolution'))

        if b'all' in result:
            return True

        # Temporary hack for next check
        newconfig = repo.ui.config(b'experimental', b'evolution.createmarkers')
        if newconfig:
            result.add(b'createmarkers')

        return option in result


def getoptions(repo):
    """Returns dicts showing state of obsolescence features."""

    createmarkersvalue = _getoptionvalue(repo, createmarkersopt)
    if createmarkersvalue:
        unstablevalue = _getoptionvalue(repo, allowunstableopt)
        divergencevalue = _getoptionvalue(repo, allowdivergenceopt)
        exchangevalue = _getoptionvalue(repo, exchangeopt)
    else:
        # if we cannot create obsolescence markers, we shouldn't exchange them
        # or perform operations that lead to instability or divergence
        unstablevalue = False
        divergencevalue = False
        exchangevalue = False

    return {
        createmarkersopt: createmarkersvalue,
        allowunstableopt: unstablevalue,
        allowdivergenceopt: divergencevalue,
        exchangeopt: exchangevalue,
    }


def isenabled(repo, option):
    """Returns True if the given repository has the given obsolete option
    enabled.
    """
    return getoptions(repo)[option]


# Creating aliases for marker flags because evolve extension looks for
# bumpedfix in obsolete.py
bumpedfix = obsutil.bumpedfix
usingsha256 = obsutil.usingsha256

## Parsing and writing of version "0"
#
# The header is followed by the markers. Each marker is made of:
#
# - 1 uint8 : number of new changesets "N", can be zero.
#
# - 1 uint32: metadata size "M" in bytes.
#
# - 1 byte: a bit field. It is reserved for flags used in common
#   obsolete marker operations, to avoid repeated decoding of metadata
#   entries.
#
# - 20 bytes: obsoleted changeset identifier.
#
# - N*20 bytes: new changesets identifiers.
#
# - M bytes: metadata as a sequence of nul-terminated strings. Each
#   string contains a key and a value, separated by a colon ':', without
#   additional encoding. Keys cannot contain '\0' or ':' and values
#   cannot contain '\0'.
_fm0version = 0
_fm0fixed = b'>BIB20s'
_fm0node = b'20s'
_fm0fsize = _calcsize(_fm0fixed)
_fm0fnodesize = _calcsize(_fm0node)


def _fm0readmarkers(data, off, stop):
    # Loop on markers
    while off < stop:
        # read fixed part
        cur = data[off : off + _fm0fsize]
        off += _fm0fsize
        numsuc, mdsize, flags, pre = _unpack(_fm0fixed, cur)
        # read replacement
        sucs = ()
        if numsuc:
            s = _fm0fnodesize * numsuc
            cur = data[off : off + s]
            sucs = _unpack(_fm0node * numsuc, cur)
            off += s
        # read metadata
        # (metadata will be decoded on demand)
        metadata = data[off : off + mdsize]
        if len(metadata) != mdsize:
            raise error.Abort(
                _(
                    b'parsing obsolete marker: metadata is too '
                    b'short, %d bytes expected, got %d'
                )
                % (mdsize, len(metadata))
            )
        off += mdsize
        metadata = _fm0decodemeta(metadata)
        try:
            when, offset = metadata.pop(b'date', b'0 0').split(b' ')
            date = float(when), int(offset)
        except ValueError:
            date = (0.0, 0)
        parents = None
        if b'p2' in metadata:
            parents = (metadata.pop(b'p1', None), metadata.pop(b'p2', None))
        elif b'p1' in metadata:
            parents = (metadata.pop(b'p1', None),)
        elif b'p0' in metadata:
            parents = ()
        if parents is not None:
            try:
                parents = tuple(bin(p) for p in parents)
                # if parent content is not a nodeid, drop the data
                for p in parents:
                    if len(p) != 20:
                        parents = None
                        break
            except binascii.Error:
                # if content cannot be translated to nodeid drop the data.
                parents = None

        metadata = tuple(sorted(metadata.items()))

        yield (pre, sucs, flags, metadata, date, parents)


def _fm0encodeonemarker(marker):
    pre, sucs, flags, metadata, date, parents = marker
    if flags & usingsha256:
        raise error.Abort(_(b'cannot handle sha256 with old obsstore format'))
    metadata = dict(metadata)
    time, tz = date
    metadata[b'date'] = b'%r %i' % (time, tz)
    if parents is not None:
        if not parents:
            # mark that we explicitly recorded no parents
            metadata[b'p0'] = b''
        for i, p in enumerate(parents, 1):
            metadata[b'p%i' % i] = hex(p)
    metadata = _fm0encodemeta(metadata)
    numsuc = len(sucs)
    format = _fm0fixed + (_fm0node * numsuc)
    data = [numsuc, len(metadata), flags, pre]
    data.extend(sucs)
    return _pack(format, *data) + metadata


def _fm0encodemeta(meta):
    """Return encoded metadata string to string mapping.

    Assume no ':' in key and no '\0' in both key and value."""
    for key, value in meta.items():
        if b':' in key or b'\0' in key:
            raise ValueError(b"':' and '\0' are forbidden in metadata key'")
        if b'\0' in value:
            raise ValueError(b"':' is forbidden in metadata value'")
    return b'\0'.join([b'%s:%s' % (k, meta[k]) for k in sorted(meta)])


def _fm0decodemeta(data):
    """Return string to string dictionary from encoded version."""
    d = {}
    for l in data.split(b'\0'):
        if l:
            key, value = l.split(b':', 1)
            d[key] = value
    return d


## Parsing and writing of version "1"
#
# The header is followed by the markers. Each marker is made of:
#
# - uint32: total size of the marker (including this field)
#
# - float64: date in seconds since epoch
#
# - int16: timezone offset in minutes
#
# - uint16: a bit field. It is reserved for flags used in common
#   obsolete marker operations, to avoid repeated decoding of metadata
#   entries.
#
# - uint8: number of successors "N", can be zero.
#
# - uint8: number of parents "P", can be zero.
#
#     0: parents data stored but no parent,
#     1: one parent stored,
#     2: two parents stored,
#     3: no parent data stored
#
# - uint8: number of metadata entries M
#
# - 20 or 32 bytes: predecessor changeset identifier.
#
# - N*(20 or 32) bytes: successors changesets identifiers.
#
# - P*(20 or 32) bytes: parents of the predecessors changesets.
#
# - M*(uint8, uint8): size of all metadata entries (key and value)
#
# - remaining bytes: the metadata, each (key, value) pair after the other.
_fm1version = 1
_fm1fixed = b'>IdhHBBB'
_fm1nodesha1 = b'20s'
_fm1nodesha256 = b'32s'
_fm1nodesha1size = _calcsize(_fm1nodesha1)
_fm1nodesha256size = _calcsize(_fm1nodesha256)
_fm1fsize = _calcsize(_fm1fixed)
_fm1parentnone = 3
_fm1metapair = b'BB'
_fm1metapairsize = _calcsize(_fm1metapair)


def _fm1purereadmarkers(data, off, stop):
    # make some global constants local for performance
    noneflag = _fm1parentnone
    sha2flag = usingsha256
    sha1size = _fm1nodesha1size
    sha2size = _fm1nodesha256size
    sha1fmt = _fm1nodesha1
    sha2fmt = _fm1nodesha256
    metasize = _fm1metapairsize
    metafmt = _fm1metapair
    fsize = _fm1fsize
    unpack = _unpack

    # Loop on markers
    ufixed = struct.Struct(_fm1fixed).unpack

    while off < stop:
        # read fixed part
        o1 = off + fsize
        t, secs, tz, flags, numsuc, numpar, nummeta = ufixed(data[off:o1])

        if flags & sha2flag:
            nodefmt = sha2fmt
            nodesize = sha2size
        else:
            nodefmt = sha1fmt
            nodesize = sha1size

        (prec,) = unpack(nodefmt, data[o1 : o1 + nodesize])
        o1 += nodesize

        # read 0 or more successors
        if numsuc == 1:
            o2 = o1 + nodesize
            sucs = (data[o1:o2],)
        else:
            o2 = o1 + nodesize * numsuc
            sucs = unpack(nodefmt * numsuc, data[o1:o2])

        # read parents
        if numpar == noneflag:
            o3 = o2
            parents = None
        elif numpar == 1:
            o3 = o2 + nodesize
            parents = (data[o2:o3],)
        else:
            o3 = o2 + nodesize * numpar
            parents = unpack(nodefmt * numpar, data[o2:o3])

        # read metadata
        off = o3 + metasize * nummeta
        metapairsize = unpack(b'>' + (metafmt * nummeta), data[o3:off])
        metadata = []
        for idx in range(0, len(metapairsize), 2):
            o1 = off + metapairsize[idx]
            o2 = o1 + metapairsize[idx + 1]
            metadata.append((data[off:o1], data[o1:o2]))
            off = o2

        yield (prec, sucs, flags, tuple(metadata), (secs, tz * 60), parents)


def _fm1encodeonemarker(marker):
    pre, sucs, flags, metadata, date, parents = marker
    # determine node size
    _fm1node = _fm1nodesha1
    if flags & usingsha256:
        _fm1node = _fm1nodesha256
    numsuc = len(sucs)
    numextranodes = 1 + numsuc
    if parents is None:
        numpar = _fm1parentnone
    else:
        numpar = len(parents)
        numextranodes += numpar
    formatnodes = _fm1node * numextranodes
    formatmeta = _fm1metapair * len(metadata)
    format = _fm1fixed + formatnodes + formatmeta
    # tz is stored in minutes so we divide by 60
    tz = date[1] // 60
    data = [None, date[0], tz, flags, numsuc, numpar, len(metadata), pre]
    data.extend(sucs)
    if parents is not None:
        data.extend(parents)
    totalsize = _calcsize(format)
    for key, value in metadata:
        lk = len(key)
        lv = len(value)
        if lk > 255:
            msg = (
                b'obsstore metadata key cannot be longer than 255 bytes'
                b' (key "%s" is %u bytes)'
            ) % (key, lk)
            raise error.ProgrammingError(msg)
        if lv > 255:
            msg = (
                b'obsstore metadata value cannot be longer than 255 bytes'
                b' (value "%s" for key "%s" is %u bytes)'
            ) % (value, key, lv)
            raise error.ProgrammingError(msg)
        data.append(lk)
        data.append(lv)
        totalsize += lk + lv
    data[0] = totalsize
    data = [_pack(format, *data)]
    for key, value in metadata:
        data.append(key)
        data.append(value)
    return b''.join(data)


def _fm1readmarkers(data, off, stop):
    native = getattr(parsers, 'fm1readmarkers', None)
    if not native:
        return _fm1purereadmarkers(data, off, stop)
    return native(data, off, stop)


# mapping to read/write various marker formats
# <version> -> (decoder, encoder)
formats = {
    _fm0version: (_fm0readmarkers, _fm0encodeonemarker),
    _fm1version: (_fm1readmarkers, _fm1encodeonemarker),
}


def _readmarkerversion(data):
    return _unpack(b'>B', data[0:1])[0]


@util.nogc
def _readmarkers(data, off=None, stop=None):
    """Read and enumerate markers from raw data"""
    diskversion = _readmarkerversion(data)
    if not off:
        off = 1  # skip 1 byte version number
    if stop is None:
        stop = len(data)
    if diskversion not in formats:
        msg = _(b'parsing obsolete marker: unknown version %r') % diskversion
        raise error.UnknownVersion(msg, version=diskversion)
    return diskversion, formats[diskversion][0](data, off, stop)


def encodeheader(version=_fm0version):
    return _pack(b'>B', version)


def encodemarkers(markers, addheader=False, version=_fm0version):
    # Kept separate from flushmarkers(), it will be reused for
    # markers exchange.
    encodeone = formats[version][1]
    if addheader:
        yield encodeheader(version)
    for marker in markers:
        yield encodeone(marker)


@util.nogc
def _addsuccessors(successors, markers):
    for mark in markers:
        successors.setdefault(mark[0], set()).add(mark)


@util.nogc
def _addpredecessors(predecessors, markers):
    for mark in markers:
        for suc in mark[1]:
            predecessors.setdefault(suc, set()).add(mark)


@util.nogc
def _addchildren(children, markers):
    for mark in markers:
        parents = mark[5]
        if parents is not None:
            for p in parents:
                children.setdefault(p, set()).add(mark)


def _checkinvalidmarkers(repo, markers):
    """search for marker with invalid data and raise error if needed

    Exist as a separated function to allow the evolve extension for a more
    subtle handling.
    """
    for mark in markers:
        if repo.nullid in mark[1]:
            raise error.Abort(
                _(
                    b'bad obsolescence marker detected: '
                    b'invalid successors nullid'
                )
            )


class obsstore:
    """Store obsolete markers

    Markers can be accessed with two mappings:
    - predecessors[x] -> set(markers on predecessors edges of x)
    - successors[x] -> set(markers on successors edges of x)
    - children[x]   -> set(markers on predecessors edges of children(x)
    """

    fields = (b'prec', b'succs', b'flag', b'meta', b'date', b'parents')
    # prec:    nodeid, predecessors changesets
    # succs:   tuple of nodeid, successor changesets (0-N length)
    # flag:    integer, flag field carrying modifier for the markers (see doc)
    # meta:    binary blob in UTF-8, encoded metadata dictionary
    # date:    (float, int) tuple, date of marker creation
    # parents: (tuple of nodeid) or None, parents of predecessors
    #          None is used when no data has been recorded

    def __init__(self, repo, svfs, defaultformat=_fm1version, readonly=False):
        # caches for various obsolescence related cache
        self.caches = {}
        self.svfs = svfs
        self._repo = weakref.ref(repo)
        self._defaultformat = defaultformat
        self._readonly = readonly

    @property
    def repo(self):
        r = self._repo()
        if r is None:
            msg = "using the obsstore of a deallocated repo"
            raise error.ProgrammingError(msg)
        return r

    def __iter__(self):
        return iter(self._all)

    def __len__(self):
        return len(self._all)

    def __nonzero__(self):
        from . import statichttprepo

        if isinstance(self.repo, statichttprepo.statichttprepository):
            # If repo is accessed via static HTTP, then we can't use os.stat()
            # to just peek at the file size.
            return len(self._data) > 1
        if not self._cached('_all'):
            try:
                return self.svfs.stat(b'obsstore').st_size > 1
            except FileNotFoundError:
                # just build an empty _all list if no obsstore exists, which
                # avoids further stat() syscalls
                pass
        return bool(self._all)

    __bool__ = __nonzero__

    @property
    def readonly(self):
        """True if marker creation is disabled

        Remove me in the future when obsolete marker is always on."""
        return self._readonly

    def create(
        self,
        transaction,
        prec,
        succs=(),
        flag=0,
        parents=None,
        date=None,
        metadata=None,
        ui=None,
    ):
        """obsolete: add a new obsolete marker

        * ensuring it is hashable
        * check mandatory metadata
        * encode metadata

        If you are a human writing code creating marker you want to use the
        `createmarkers` function in this module instead.

        return True if a new marker have been added, False if the markers
        already existed (no op).
        """
        flag = int(flag)
        if metadata is None:
            metadata = {}
        if date is None:
            if b'date' in metadata:
                # as a courtesy for out-of-tree extensions
                date = dateutil.parsedate(metadata.pop(b'date'))
            elif ui is not None:
                date = ui.configdate(b'devel', b'default-date')
                if date is None:
                    date = dateutil.makedate()
            else:
                date = dateutil.makedate()
        if flag & usingsha256:
            if len(prec) != 32:
                raise ValueError(prec)
            for succ in succs:
                if len(succ) != 32:
                    raise ValueError(succ)
        else:
            if len(prec) != 20:
                raise ValueError(prec)
            for succ in succs:
                if len(succ) != 20:
                    raise ValueError(succ)
        if prec in succs:
            raise ValueError('in-marker cycle with %s' % prec.hex())

        metadata = tuple(sorted(metadata.items()))
        for k, v in metadata:
            try:
                # might be better to reject non-ASCII keys
                k.decode('utf-8')
                v.decode('utf-8')
            except UnicodeDecodeError:
                raise error.ProgrammingError(
                    b'obsstore metadata must be valid UTF-8 sequence '
                    b'(key = %r, value = %r)'
                    % (pycompat.bytestr(k), pycompat.bytestr(v))
                )

        marker = (bytes(prec), tuple(succs), flag, metadata, date, parents)
        return bool(self.add(transaction, [marker]))

    def add(self, transaction, markers):
        """Add new markers to the store

        Take care of filtering duplicate.
        Return the number of new marker."""
        if self._readonly:
            raise error.Abort(
                _(b'creating obsolete markers is not enabled on this repo')
            )
        known = set()
        getsuccessors = self.successors.get
        new = []
        for m in markers:
            if m not in getsuccessors(m[0], ()) and m not in known:
                known.add(m)
                new.append(m)
        if new:
            f = self.svfs(b'obsstore', b'ab')
            try:
                offset = f.tell()
                transaction.add(b'obsstore', offset)
                # offset == 0: new file - add the version header
                data = b''.join(encodemarkers(new, offset == 0, self._version))
                f.write(data)
            finally:
                # XXX: f.close() == filecache invalidation == obsstore rebuilt.
                # call 'filecacheentry.refresh()'  here
                f.close()
            addedmarkers = transaction.changes.get(b'obsmarkers')
            if addedmarkers is not None:
                addedmarkers.update(new)
            self._addmarkers(new, data)
            # new marker *may* have changed several set. invalidate the cache.
            self.caches.clear()
        # records the number of new markers for the transaction hooks
        previous = int(transaction.hookargs.get(b'new_obsmarkers', b'0'))
        transaction.hookargs[b'new_obsmarkers'] = b'%d' % (previous + len(new))
        return len(new)

    def mergemarkers(self, transaction, data):
        """merge a binary stream of markers inside the obsstore

        Returns the number of new markers added."""
        version, markers = _readmarkers(data)
        return self.add(transaction, markers)

    @propertycache
    def _data(self):
        return self.svfs.tryread(b'obsstore')

    @propertycache
    def _version(self):
        if len(self._data) >= 1:
            return _readmarkerversion(self._data)
        else:
            return self._defaultformat

    @propertycache
    def _all(self):
        data = self._data
        if not data:
            return []
        self._version, markers = _readmarkers(data)
        markers = list(markers)
        _checkinvalidmarkers(self.repo, markers)
        return markers

    @propertycache
    def successors(self):
        successors = {}
        _addsuccessors(successors, self._all)
        return successors

    @propertycache
    def predecessors(self):
        predecessors = {}
        _addpredecessors(predecessors, self._all)
        return predecessors

    @propertycache
    def children(self):
        children = {}
        _addchildren(children, self._all)
        return children

    def _cached(self, attr):
        return attr in self.__dict__

    def _addmarkers(self, markers, rawdata):
        markers = list(markers)  # to allow repeated iteration
        self._data = self._data + rawdata
        self._all.extend(markers)
        if self._cached('successors'):
            _addsuccessors(self.successors, markers)
        if self._cached('predecessors'):
            _addpredecessors(self.predecessors, markers)
        if self._cached('children'):
            _addchildren(self.children, markers)
        _checkinvalidmarkers(self.repo, markers)

    def relevantmarkers(self, nodes):
        """return a set of all obsolescence markers relevant to a set of nodes.

        "relevant" to a set of nodes mean:

        - marker that use this changeset as successor
        - prune marker of direct children on this changeset
        - recursive application of the two rules on predecessors of these
          markers

        It is a set so you cannot rely on order."""

        pendingnodes = set(nodes)
        seenmarkers = set()
        seennodes = set(pendingnodes)
        precursorsmarkers = self.predecessors
        succsmarkers = self.successors
        children = self.children
        while pendingnodes:
            direct = set()
            for current in pendingnodes:
                direct.update(precursorsmarkers.get(current, ()))
                pruned = [m for m in children.get(current, ()) if not m[1]]
                direct.update(pruned)
                pruned = [m for m in succsmarkers.get(current, ()) if not m[1]]
                direct.update(pruned)
            direct -= seenmarkers
            pendingnodes = {m[0] for m in direct}
            seenmarkers |= direct
            pendingnodes -= seennodes
            seennodes |= pendingnodes
        return seenmarkers


def makestore(ui, repo):
    """Create an obsstore instance from a repo."""
    # read default format for new obsstore.
    # developer config: format.obsstore-version
    defaultformat = ui.configint(b'format', b'obsstore-version')
    # rely on obsstore class default when possible.
    kwargs = {}
    if defaultformat is not None:
        kwargs['defaultformat'] = defaultformat
    readonly = not isenabled(repo, createmarkersopt)
    store = obsstore(repo, repo.svfs, readonly=readonly, **kwargs)
    if store and readonly:
        ui.warn(
            _(b'"obsolete" feature not enabled but %i markers found!\n')
            % len(list(store))
        )
    return store


def commonversion(versions):
    """Return the newest version listed in both versions and our local formats.

    Returns None if no common version exists.
    """
    versions.sort(reverse=True)
    # search for highest version known on both side
    for v in versions:
        if v in formats:
            return v
    return None


# arbitrary picked to fit into 8K limit from HTTP server
# you have to take in account:
# - the version header
# - the base85 encoding
_maxpayload = 5300


def _pushkeyescape(markers):
    """encode markers into a dict suitable for pushkey exchange

    - binary data is base85 encoded
    - split in chunks smaller than 5300 bytes"""
    keys = {}
    parts = []
    currentlen = _maxpayload * 2  # ensure we create a new part
    for marker in markers:
        nextdata = _fm0encodeonemarker(marker)
        if len(nextdata) + currentlen > _maxpayload:
            currentpart = []
            currentlen = 0
            parts.append(currentpart)
        currentpart.append(nextdata)
        currentlen += len(nextdata)
    for idx, part in enumerate(reversed(parts)):
        data = b''.join([_pack(b'>B', _fm0version)] + part)
        keys[b'dump%i' % idx] = util.b85encode(data)
    return keys


def listmarkers(repo):
    """List markers over pushkey"""
    if not repo.obsstore:
        return {}
    return _pushkeyescape(sorted(repo.obsstore))


def pushmarker(repo, key, old, new):
    """Push markers over pushkey"""
    if not key.startswith(b'dump'):
        repo.ui.warn(_(b'unknown key: %r') % key)
        return False
    if old:
        repo.ui.warn(_(b'unexpected old value for %r') % key)
        return False
    data = util.b85decode(new)
    with repo.lock(), repo.transaction(b'pushkey: obsolete markers') as tr:
        repo.obsstore.mergemarkers(tr, data)
        repo.invalidatevolatilesets()
        return True


# mapping of 'set-name' -> <function to compute this set>
cachefuncs = {}


def cachefor(name):
    """Decorator to register a function as computing the cache for a set"""

    def decorator(func):
        if name in cachefuncs:
            msg = b"duplicated registration for volatileset '%s' (existing: %r)"
            raise error.ProgrammingError(msg % (name, cachefuncs[name]))
        cachefuncs[name] = func
        return func

    return decorator


def getrevs(repo, name):
    """Return the set of revision that belong to the <name> set

    Such access may compute the set and cache it for future use"""
    repo = repo.unfiltered()
    with util.timedcm('getrevs %s', name):
        if not repo.obsstore:
            return frozenset()
        if name not in repo.obsstore.caches:
            repo.obsstore.caches[name] = cachefuncs[name](repo)
        return repo.obsstore.caches[name]


# To be simple we need to invalidate obsolescence cache when:
#
# - new changeset is added:
# - public phase is changed
# - obsolescence marker are added
# - strip is used a repo
def clearobscaches(repo):
    """Remove all obsolescence related cache from a repo

    This remove all cache in obsstore is the obsstore already exist on the
    repo.

    (We could be smarter here given the exact event that trigger the cache
    clearing)"""
    # only clear cache is there is obsstore data in this repo
    if b'obsstore' in repo._filecache:
        repo.obsstore.caches.clear()


def _mutablerevs(repo):
    """the set of mutable revision in the repository"""
    return repo._phasecache.getrevset(repo, phases.relevant_mutable_phases)


@cachefor(b'obsolete')
def _computeobsoleteset(repo):
    """the set of obsolete revisions"""
    getnode = repo.changelog.node
    notpublic = _mutablerevs(repo)
    isobs = repo.obsstore.successors.__contains__
    return frozenset(r for r in notpublic if isobs(getnode(r)))


@cachefor(b'orphan')
def _computeorphanset(repo):
    """the set of non obsolete revisions with obsolete parents"""
    pfunc = repo.changelog.parentrevs
    mutable = _mutablerevs(repo)
    obsolete = getrevs(repo, b'obsolete')
    others = mutable - obsolete
    unstable = set()
    for r in sorted(others):
        # A rev is unstable if one of its parent is obsolete or unstable
        # this works since we traverse following growing rev order
        for p in pfunc(r):
            if p in obsolete or p in unstable:
                unstable.add(r)
                break
    return frozenset(unstable)


@cachefor(b'suspended')
def _computesuspendedset(repo):
    """the set of obsolete parents with non obsolete descendants"""
    suspended = repo.changelog.ancestors(getrevs(repo, b'orphan'))
    return frozenset(r for r in getrevs(repo, b'obsolete') if r in suspended)


@cachefor(b'extinct')
def _computeextinctset(repo):
    """the set of obsolete parents without non obsolete descendants"""
    return getrevs(repo, b'obsolete') - getrevs(repo, b'suspended')


@cachefor(b'phasedivergent')
def _computephasedivergentset(repo):
    """the set of revs trying to obsolete public revisions"""
    bumped = set()
    # util function (avoid attribute lookup in the loop)
    phase = repo._phasecache.phase  # would be faster to grab the full list
    public = phases.public
    cl = repo.changelog
    torev = cl.index.get_rev
    tonode = cl.node
    obsstore = repo.obsstore
    candidates = sorted(_mutablerevs(repo) - getrevs(repo, b"obsolete"))
    for rev in candidates:
        # We only evaluate mutable, non-obsolete revision
        node = tonode(rev)
        # (future) A cache of predecessors may worth if split is very common
        for pnode in obsutil.allpredecessors(
            obsstore, [node], ignoreflags=bumpedfix
        ):
            prev = torev(pnode)  # unfiltered! but so is phasecache
            if (prev is not None) and (phase(repo, prev) <= public):
                # we have a public predecessor
                bumped.add(rev)
                break  # Next draft!
    return frozenset(bumped)


@cachefor(b'contentdivergent')
def _computecontentdivergentset(repo):
    """the set of rev that compete to be the final successors of some revision."""
    divergent = set()
    obsstore = repo.obsstore
    newermap = {}
    tonode = repo.changelog.node
    candidates = sorted(_mutablerevs(repo) - getrevs(repo, b"obsolete"))
    for rev in candidates:
        node = tonode(rev)
        mark = obsstore.predecessors.get(node, ())
        toprocess = set(mark)
        seen = set()
        while toprocess:
            prec = toprocess.pop()[0]
            if prec in seen:
                continue  # emergency cycle hanging prevention
            seen.add(prec)
            if prec not in newermap:
                obsutil.successorssets(repo, prec, cache=newermap)
            newer = [n for n in newermap[prec] if n]
            if len(newer) > 1:
                divergent.add(rev)
                break
            toprocess.update(obsstore.predecessors.get(prec, ()))
    return frozenset(divergent)


def makefoldid(relation, user):

    folddigest = hashutil.sha1(user)
    for p in relation[0] + relation[1]:
        folddigest.update(b'%d' % p.rev())
        folddigest.update(p.node())
    # Since fold only has to compete against fold for the same successors, it
    # seems fine to use a small ID. Smaller ID save space.
    return hex(folddigest.digest())[:8]


def createmarkers(
    repo, relations, flag=0, date=None, metadata=None, operation=None
):
    """Add obsolete markers between changesets in a repo

    <relations> must be an iterable of ((<old>,...), (<new>, ...)[,{metadata}])
    tuple. `old` and `news` are changectx. metadata is an optional dictionary
    containing metadata for this marker only. It is merged with the global
    metadata specified through the `metadata` argument of this function.
    Any string values in metadata must be UTF-8 bytes.

    Trying to obsolete a public changeset will raise an exception.

    Current user and date are used except if specified otherwise in the
    metadata attribute.

    This function operates within a transaction of its own, but does
    not take any lock on the repo.
    """
    # prepare metadata
    if metadata is None:
        metadata = {}
    if b'user' not in metadata:
        luser = (
            repo.ui.config(b'devel', b'user.obsmarker') or repo.ui.username()
        )
        metadata[b'user'] = encoding.fromlocal(luser)

    # Operation metadata handling
    useoperation = repo.ui.configbool(
        b'experimental', b'evolution.track-operation'
    )
    if useoperation and operation:
        metadata[b'operation'] = operation

    # Effect flag metadata handling
    saveeffectflag = repo.ui.configbool(
        b'experimental', b'evolution.effect-flags'
    )

    with repo.transaction(b'add-obsolescence-marker') as tr:
        markerargs = []
        for rel in relations:
            predecessors = rel[0]
            if not isinstance(predecessors, tuple):
                # preserve compat with old API until all caller are migrated
                predecessors = (predecessors,)
            if len(predecessors) > 1 and len(rel[1]) != 1:
                msg = b'Fold markers can only have 1 successors, not %d'
                raise error.ProgrammingError(msg % len(rel[1]))
            foldid = None
            foldsize = len(predecessors)
            if 1 < foldsize:
                foldid = makefoldid(rel, metadata[b'user'])
            for foldidx, prec in enumerate(predecessors, 1):
                sucs = rel[1]
                localmetadata = metadata.copy()
                if len(rel) > 2:
                    localmetadata.update(rel[2])
                if foldid is not None:
                    localmetadata[b'fold-id'] = foldid
                    localmetadata[b'fold-idx'] = b'%d' % foldidx
                    localmetadata[b'fold-size'] = b'%d' % foldsize

                if not prec.mutable():
                    raise error.Abort(
                        _(b"cannot obsolete public changeset: %s") % prec,
                        hint=b"see 'hg help phases' for details",
                    )
                nprec = prec.node()
                nsucs = tuple(s.node() for s in sucs)
                npare = None
                if not nsucs:
                    npare = tuple(p.node() for p in prec.parents())
                if nprec in nsucs:
                    raise error.Abort(
                        _(b"changeset %s cannot obsolete itself") % prec
                    )

                # Effect flag can be different by relation
                if saveeffectflag:
                    # The effect flag is saved in a versioned field name for
                    # future evolution
                    effectflag = obsutil.geteffectflag(prec, sucs)
                    localmetadata[obsutil.EFFECTFLAGFIELD] = b"%d" % effectflag

                # Creating the marker causes the hidden cache to become
                # invalid, which causes recomputation when we ask for
                # prec.parents() above.  Resulting in n^2 behavior.  So let's
                # prepare all of the args first, then create the markers.
                markerargs.append((nprec, nsucs, npare, localmetadata))

        for args in markerargs:
            nprec, nsucs, npare, localmetadata = args
            repo.obsstore.create(
                tr,
                nprec,
                nsucs,
                flag,
                parents=npare,
                date=date,
                metadata=localmetadata,
                ui=repo.ui,
            )
            repo.filteredrevcache.clear()
