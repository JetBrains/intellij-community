# revlog.py - storage back-end for mercurial
# coding: utf8
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Storage back-end for Mercurial.

This provides efficient delta storage with O(1) retrieve and append
and O(changes) merge between branches.
"""

from __future__ import absolute_import

import binascii
import collections
import contextlib
import errno
import io
import os
import struct
import zlib

# import stuff from node for others to import from revlog
from .node import (
    bin,
    hex,
    nullrev,
    sha1nodeconstants,
    short,
    wdirrev,
)
from .i18n import _
from .pycompat import getattr
from .revlogutils.constants import (
    ALL_KINDS,
    CHANGELOGV2,
    COMP_MODE_DEFAULT,
    COMP_MODE_INLINE,
    COMP_MODE_PLAIN,
    FEATURES_BY_VERSION,
    FLAG_GENERALDELTA,
    FLAG_INLINE_DATA,
    INDEX_HEADER,
    KIND_CHANGELOG,
    REVLOGV0,
    REVLOGV1,
    REVLOGV1_FLAGS,
    REVLOGV2,
    REVLOGV2_FLAGS,
    REVLOG_DEFAULT_FLAGS,
    REVLOG_DEFAULT_FORMAT,
    REVLOG_DEFAULT_VERSION,
    SUPPORTED_FLAGS,
)
from .revlogutils.flagutil import (
    REVIDX_DEFAULT_FLAGS,
    REVIDX_ELLIPSIS,
    REVIDX_EXTSTORED,
    REVIDX_FLAGS_ORDER,
    REVIDX_HASCOPIESINFO,
    REVIDX_ISCENSORED,
    REVIDX_RAWTEXT_CHANGING_FLAGS,
)
from .thirdparty import attr
from . import (
    ancestor,
    dagop,
    error,
    mdiff,
    policy,
    pycompat,
    revlogutils,
    templatefilters,
    util,
)
from .interfaces import (
    repository,
    util as interfaceutil,
)
from .revlogutils import (
    deltas as deltautil,
    docket as docketutil,
    flagutil,
    nodemap as nodemaputil,
    randomaccessfile,
    revlogv0,
    rewrite,
    sidedata as sidedatautil,
)
from .utils import (
    storageutil,
    stringutil,
)

# blanked usage of all the name to prevent pyflakes constraints
# We need these name available in the module for extensions.

REVLOGV0
REVLOGV1
REVLOGV2
FLAG_INLINE_DATA
FLAG_GENERALDELTA
REVLOG_DEFAULT_FLAGS
REVLOG_DEFAULT_FORMAT
REVLOG_DEFAULT_VERSION
REVLOGV1_FLAGS
REVLOGV2_FLAGS
REVIDX_ISCENSORED
REVIDX_ELLIPSIS
REVIDX_HASCOPIESINFO
REVIDX_EXTSTORED
REVIDX_DEFAULT_FLAGS
REVIDX_FLAGS_ORDER
REVIDX_RAWTEXT_CHANGING_FLAGS

parsers = policy.importmod('parsers')
rustancestor = policy.importrust('ancestor')
rustdagop = policy.importrust('dagop')
rustrevlog = policy.importrust('revlog')

# Aliased for performance.
_zlibdecompress = zlib.decompress

# max size of revlog with inline data
_maxinline = 131072

# Flag processors for REVIDX_ELLIPSIS.
def ellipsisreadprocessor(rl, text):
    return text, False


def ellipsiswriteprocessor(rl, text):
    return text, False


def ellipsisrawprocessor(rl, text):
    return False


ellipsisprocessor = (
    ellipsisreadprocessor,
    ellipsiswriteprocessor,
    ellipsisrawprocessor,
)


def _verify_revision(rl, skipflags, state, node):
    """Verify the integrity of the given revlog ``node`` while providing a hook
    point for extensions to influence the operation."""
    if skipflags:
        state[b'skipread'].add(node)
    else:
        # Side-effect: read content and verify hash.
        rl.revision(node)


# True if a fast implementation for persistent-nodemap is available
#
# We also consider we have a "fast" implementation in "pure" python because
# people using pure don't really have performance consideration (and a
# wheelbarrow of other slowness source)
HAS_FAST_PERSISTENT_NODEMAP = rustrevlog is not None or util.safehasattr(
    parsers, 'BaseIndexObject'
)


@interfaceutil.implementer(repository.irevisiondelta)
@attr.s(slots=True)
class revlogrevisiondelta(object):
    node = attr.ib()
    p1node = attr.ib()
    p2node = attr.ib()
    basenode = attr.ib()
    flags = attr.ib()
    baserevisionsize = attr.ib()
    revision = attr.ib()
    delta = attr.ib()
    sidedata = attr.ib()
    protocol_flags = attr.ib()
    linknode = attr.ib(default=None)


@interfaceutil.implementer(repository.iverifyproblem)
@attr.s(frozen=True)
class revlogproblem(object):
    warning = attr.ib(default=None)
    error = attr.ib(default=None)
    node = attr.ib(default=None)


def parse_index_v1(data, inline):
    # call the C implementation to parse the index data
    index, cache = parsers.parse_index2(data, inline)
    return index, cache


def parse_index_v2(data, inline):
    # call the C implementation to parse the index data
    index, cache = parsers.parse_index2(data, inline, revlogv2=True)
    return index, cache


def parse_index_cl_v2(data, inline):
    # call the C implementation to parse the index data
    assert not inline
    from .pure.parsers import parse_index_cl_v2

    index, cache = parse_index_cl_v2(data)
    return index, cache


if util.safehasattr(parsers, 'parse_index_devel_nodemap'):

    def parse_index_v1_nodemap(data, inline):
        index, cache = parsers.parse_index_devel_nodemap(data, inline)
        return index, cache


else:
    parse_index_v1_nodemap = None


def parse_index_v1_mixed(data, inline):
    index, cache = parse_index_v1(data, inline)
    return rustrevlog.MixedIndex(index), cache


# corresponds to uncompressed length of indexformatng (2 gigs, 4-byte
# signed integer)
_maxentrysize = 0x7FFFFFFF

FILE_TOO_SHORT_MSG = _(
    b'cannot read from revlog %s;'
    b'  expected %d bytes from offset %d, data size is %d'
)


class revlog(object):
    """
    the underlying revision storage object

    A revlog consists of two parts, an index and the revision data.

    The index is a file with a fixed record size containing
    information on each revision, including its nodeid (hash), the
    nodeids of its parents, the position and offset of its data within
    the data file, and the revision it's based on. Finally, each entry
    contains a linkrev entry that can serve as a pointer to external
    data.

    The revision data itself is a linear collection of data chunks.
    Each chunk represents a revision and is usually represented as a
    delta against the previous chunk. To bound lookup time, runs of
    deltas are limited to about 2 times the length of the original
    version data. This makes retrieval of a version proportional to
    its size, or O(1) relative to the number of revisions.

    Both pieces of the revlog are written to in an append-only
    fashion, which means we never need to rewrite a file to insert or
    remove data, and can use some simple techniques to avoid the need
    for locking while reading.

    If checkambig, indexfile is opened with checkambig=True at
    writing, to avoid file stat ambiguity.

    If mmaplargeindex is True, and an mmapindexthreshold is set, the
    index will be mmapped rather than read if it is larger than the
    configured threshold.

    If censorable is True, the revlog can have censored revisions.

    If `upperboundcomp` is not None, this is the expected maximal gain from
    compression for the data content.

    `concurrencychecker` is an optional function that receives 3 arguments: a
    file handle, a filename, and an expected position. It should check whether
    the current position in the file handle is valid, and log/warn/fail (by
    raising).

    See mercurial/revlogutils/contants.py for details about the content of an
    index entry.
    """

    _flagserrorclass = error.RevlogError

    def __init__(
        self,
        opener,
        target,
        radix,
        postfix=None,  # only exist for `tmpcensored` now
        checkambig=False,
        mmaplargeindex=False,
        censorable=False,
        upperboundcomp=None,
        persistentnodemap=False,
        concurrencychecker=None,
        trypending=False,
    ):
        """
        create a revlog object

        opener is a function that abstracts the file opening operation
        and can be used to implement COW semantics or the like.

        `target`: a (KIND, ID) tuple that identify the content stored in
        this revlog. It help the rest of the code to understand what the revlog
        is about without having to resort to heuristic and index filename
        analysis. Note: that this must be reliably be set by normal code, but
        that test, debug, or performance measurement code might not set this to
        accurate value.
        """
        self.upperboundcomp = upperboundcomp

        self.radix = radix

        self._docket_file = None
        self._indexfile = None
        self._datafile = None
        self._sidedatafile = None
        self._nodemap_file = None
        self.postfix = postfix
        self._trypending = trypending
        self.opener = opener
        if persistentnodemap:
            self._nodemap_file = nodemaputil.get_nodemap_file(self)

        assert target[0] in ALL_KINDS
        assert len(target) == 2
        self.target = target
        #  When True, indexfile is opened with checkambig=True at writing, to
        #  avoid file stat ambiguity.
        self._checkambig = checkambig
        self._mmaplargeindex = mmaplargeindex
        self._censorable = censorable
        # 3-tuple of (node, rev, text) for a raw revision.
        self._revisioncache = None
        # Maps rev to chain base rev.
        self._chainbasecache = util.lrucachedict(100)
        # 2-tuple of (offset, data) of raw data from the revlog at an offset.
        self._chunkcache = (0, b'')
        # How much data to read and cache into the raw revlog data cache.
        self._chunkcachesize = 65536
        self._maxchainlen = None
        self._deltabothparents = True
        self.index = None
        self._docket = None
        self._nodemap_docket = None
        # Mapping of partial identifiers to full nodes.
        self._pcache = {}
        # Mapping of revision integer to full node.
        self._compengine = b'zlib'
        self._compengineopts = {}
        self._maxdeltachainspan = -1
        self._withsparseread = False
        self._sparserevlog = False
        self.hassidedata = False
        self._srdensitythreshold = 0.50
        self._srmingapsize = 262144

        # Make copy of flag processors so each revlog instance can support
        # custom flags.
        self._flagprocessors = dict(flagutil.flagprocessors)

        # 3-tuple of file handles being used for active writing.
        self._writinghandles = None
        # prevent nesting of addgroup
        self._adding_group = None

        self._loadindex()

        self._concurrencychecker = concurrencychecker

    def _init_opts(self):
        """process options (from above/config) to setup associated default revlog mode

        These values might be affected when actually reading on disk information.

        The relevant values are returned for use in _loadindex().

        * newversionflags:
            version header to use if we need to create a new revlog

        * mmapindexthreshold:
            minimal index size for start to use mmap

        * force_nodemap:
            force the usage of a "development" version of the nodemap code
        """
        mmapindexthreshold = None
        opts = self.opener.options

        if b'changelogv2' in opts and self.revlog_kind == KIND_CHANGELOG:
            new_header = CHANGELOGV2
        elif b'revlogv2' in opts:
            new_header = REVLOGV2
        elif b'revlogv1' in opts:
            new_header = REVLOGV1 | FLAG_INLINE_DATA
            if b'generaldelta' in opts:
                new_header |= FLAG_GENERALDELTA
        elif b'revlogv0' in self.opener.options:
            new_header = REVLOGV0
        else:
            new_header = REVLOG_DEFAULT_VERSION

        if b'chunkcachesize' in opts:
            self._chunkcachesize = opts[b'chunkcachesize']
        if b'maxchainlen' in opts:
            self._maxchainlen = opts[b'maxchainlen']
        if b'deltabothparents' in opts:
            self._deltabothparents = opts[b'deltabothparents']
        self._lazydelta = bool(opts.get(b'lazydelta', True))
        self._lazydeltabase = False
        if self._lazydelta:
            self._lazydeltabase = bool(opts.get(b'lazydeltabase', False))
        if b'compengine' in opts:
            self._compengine = opts[b'compengine']
        if b'zlib.level' in opts:
            self._compengineopts[b'zlib.level'] = opts[b'zlib.level']
        if b'zstd.level' in opts:
            self._compengineopts[b'zstd.level'] = opts[b'zstd.level']
        if b'maxdeltachainspan' in opts:
            self._maxdeltachainspan = opts[b'maxdeltachainspan']
        if self._mmaplargeindex and b'mmapindexthreshold' in opts:
            mmapindexthreshold = opts[b'mmapindexthreshold']
        self._sparserevlog = bool(opts.get(b'sparse-revlog', False))
        withsparseread = bool(opts.get(b'with-sparse-read', False))
        # sparse-revlog forces sparse-read
        self._withsparseread = self._sparserevlog or withsparseread
        if b'sparse-read-density-threshold' in opts:
            self._srdensitythreshold = opts[b'sparse-read-density-threshold']
        if b'sparse-read-min-gap-size' in opts:
            self._srmingapsize = opts[b'sparse-read-min-gap-size']
        if opts.get(b'enableellipsis'):
            self._flagprocessors[REVIDX_ELLIPSIS] = ellipsisprocessor

        # revlog v0 doesn't have flag processors
        for flag, processor in pycompat.iteritems(
            opts.get(b'flagprocessors', {})
        ):
            flagutil.insertflagprocessor(flag, processor, self._flagprocessors)

        if self._chunkcachesize <= 0:
            raise error.RevlogError(
                _(b'revlog chunk cache size %r is not greater than 0')
                % self._chunkcachesize
            )
        elif self._chunkcachesize & (self._chunkcachesize - 1):
            raise error.RevlogError(
                _(b'revlog chunk cache size %r is not a power of 2')
                % self._chunkcachesize
            )
        force_nodemap = opts.get(b'devel-force-nodemap', False)
        return new_header, mmapindexthreshold, force_nodemap

    def _get_data(self, filepath, mmap_threshold, size=None):
        """return a file content with or without mmap

        If the file is missing return the empty string"""
        try:
            with self.opener(filepath) as fp:
                if mmap_threshold is not None:
                    file_size = self.opener.fstat(fp).st_size
                    if file_size >= mmap_threshold:
                        if size is not None:
                            # avoid potentiel mmap crash
                            size = min(file_size, size)
                        # TODO: should .close() to release resources without
                        # relying on Python GC
                        if size is None:
                            return util.buffer(util.mmapread(fp))
                        else:
                            return util.buffer(util.mmapread(fp, size))
                if size is None:
                    return fp.read()
                else:
                    return fp.read(size)
        except IOError as inst:
            if inst.errno != errno.ENOENT:
                raise
            return b''

    def _loadindex(self, docket=None):

        new_header, mmapindexthreshold, force_nodemap = self._init_opts()

        if self.postfix is not None:
            entry_point = b'%s.i.%s' % (self.radix, self.postfix)
        elif self._trypending and self.opener.exists(b'%s.i.a' % self.radix):
            entry_point = b'%s.i.a' % self.radix
        else:
            entry_point = b'%s.i' % self.radix

        if docket is not None:
            self._docket = docket
            self._docket_file = entry_point
        else:
            entry_data = b''
            self._initempty = True
            entry_data = self._get_data(entry_point, mmapindexthreshold)
            if len(entry_data) > 0:
                header = INDEX_HEADER.unpack(entry_data[:4])[0]
                self._initempty = False
            else:
                header = new_header

            self._format_flags = header & ~0xFFFF
            self._format_version = header & 0xFFFF

            supported_flags = SUPPORTED_FLAGS.get(self._format_version)
            if supported_flags is None:
                msg = _(b'unknown version (%d) in revlog %s')
                msg %= (self._format_version, self.display_id)
                raise error.RevlogError(msg)
            elif self._format_flags & ~supported_flags:
                msg = _(b'unknown flags (%#04x) in version %d revlog %s')
                display_flag = self._format_flags >> 16
                msg %= (display_flag, self._format_version, self.display_id)
                raise error.RevlogError(msg)

            features = FEATURES_BY_VERSION[self._format_version]
            self._inline = features[b'inline'](self._format_flags)
            self._generaldelta = features[b'generaldelta'](self._format_flags)
            self.hassidedata = features[b'sidedata']

            if not features[b'docket']:
                self._indexfile = entry_point
                index_data = entry_data
            else:
                self._docket_file = entry_point
                if self._initempty:
                    self._docket = docketutil.default_docket(self, header)
                else:
                    self._docket = docketutil.parse_docket(
                        self, entry_data, use_pending=self._trypending
                    )

        if self._docket is not None:
            self._indexfile = self._docket.index_filepath()
            index_data = b''
            index_size = self._docket.index_end
            if index_size > 0:
                index_data = self._get_data(
                    self._indexfile, mmapindexthreshold, size=index_size
                )
                if len(index_data) < index_size:
                    msg = _(b'too few index data for %s: got %d, expected %d')
                    msg %= (self.display_id, len(index_data), index_size)
                    raise error.RevlogError(msg)

            self._inline = False
            # generaldelta implied by version 2 revlogs.
            self._generaldelta = True
            # the logic for persistent nodemap will be dealt with within the
            # main docket, so disable it for now.
            self._nodemap_file = None

        if self._docket is not None:
            self._datafile = self._docket.data_filepath()
            self._sidedatafile = self._docket.sidedata_filepath()
        elif self.postfix is None:
            self._datafile = b'%s.d' % self.radix
        else:
            self._datafile = b'%s.d.%s' % (self.radix, self.postfix)

        self.nodeconstants = sha1nodeconstants
        self.nullid = self.nodeconstants.nullid

        # sparse-revlog can't be on without general-delta (issue6056)
        if not self._generaldelta:
            self._sparserevlog = False

        self._storedeltachains = True

        devel_nodemap = (
            self._nodemap_file
            and force_nodemap
            and parse_index_v1_nodemap is not None
        )

        use_rust_index = False
        if rustrevlog is not None:
            if self._nodemap_file is not None:
                use_rust_index = True
            else:
                use_rust_index = self.opener.options.get(b'rust.index')

        self._parse_index = parse_index_v1
        if self._format_version == REVLOGV0:
            self._parse_index = revlogv0.parse_index_v0
        elif self._format_version == REVLOGV2:
            self._parse_index = parse_index_v2
        elif self._format_version == CHANGELOGV2:
            self._parse_index = parse_index_cl_v2
        elif devel_nodemap:
            self._parse_index = parse_index_v1_nodemap
        elif use_rust_index:
            self._parse_index = parse_index_v1_mixed
        try:
            d = self._parse_index(index_data, self._inline)
            index, chunkcache = d
            use_nodemap = (
                not self._inline
                and self._nodemap_file is not None
                and util.safehasattr(index, 'update_nodemap_data')
            )
            if use_nodemap:
                nodemap_data = nodemaputil.persisted_data(self)
                if nodemap_data is not None:
                    docket = nodemap_data[0]
                    if (
                        len(d[0]) > docket.tip_rev
                        and d[0][docket.tip_rev][7] == docket.tip_node
                    ):
                        # no changelog tampering
                        self._nodemap_docket = docket
                        index.update_nodemap_data(*nodemap_data)
        except (ValueError, IndexError):
            raise error.RevlogError(
                _(b"index %s is corrupted") % self.display_id
            )
        self.index = index
        self._segmentfile = randomaccessfile.randomaccessfile(
            self.opener,
            (self._indexfile if self._inline else self._datafile),
            self._chunkcachesize,
            chunkcache,
        )
        self._segmentfile_sidedata = randomaccessfile.randomaccessfile(
            self.opener,
            self._sidedatafile,
            self._chunkcachesize,
        )
        # revnum -> (chain-length, sum-delta-length)
        self._chaininfocache = util.lrucachedict(500)
        # revlog header -> revlog compressor
        self._decompressors = {}

    @util.propertycache
    def revlog_kind(self):
        return self.target[0]

    @util.propertycache
    def display_id(self):
        """The public facing "ID" of the revlog that we use in message"""
        # Maybe we should build a user facing representation of
        # revlog.target instead of using `self.radix`
        return self.radix

    def _get_decompressor(self, t):
        try:
            compressor = self._decompressors[t]
        except KeyError:
            try:
                engine = util.compengines.forrevlogheader(t)
                compressor = engine.revlogcompressor(self._compengineopts)
                self._decompressors[t] = compressor
            except KeyError:
                raise error.RevlogError(
                    _(b'unknown compression type %s') % binascii.hexlify(t)
                )
        return compressor

    @util.propertycache
    def _compressor(self):
        engine = util.compengines[self._compengine]
        return engine.revlogcompressor(self._compengineopts)

    @util.propertycache
    def _decompressor(self):
        """the default decompressor"""
        if self._docket is None:
            return None
        t = self._docket.default_compression_header
        c = self._get_decompressor(t)
        return c.decompress

    def _indexfp(self):
        """file object for the revlog's index file"""
        return self.opener(self._indexfile, mode=b"r")

    def __index_write_fp(self):
        # You should not use this directly and use `_writing` instead
        try:
            f = self.opener(
                self._indexfile, mode=b"r+", checkambig=self._checkambig
            )
            if self._docket is None:
                f.seek(0, os.SEEK_END)
            else:
                f.seek(self._docket.index_end, os.SEEK_SET)
            return f
        except IOError as inst:
            if inst.errno != errno.ENOENT:
                raise
            return self.opener(
                self._indexfile, mode=b"w+", checkambig=self._checkambig
            )

    def __index_new_fp(self):
        # You should not use this unless you are upgrading from inline revlog
        return self.opener(
            self._indexfile,
            mode=b"w",
            checkambig=self._checkambig,
            atomictemp=True,
        )

    def _datafp(self, mode=b'r'):
        """file object for the revlog's data file"""
        return self.opener(self._datafile, mode=mode)

    @contextlib.contextmanager
    def _sidedatareadfp(self):
        """file object suitable to read sidedata"""
        if self._writinghandles:
            yield self._writinghandles[2]
        else:
            with self.opener(self._sidedatafile) as fp:
                yield fp

    def tiprev(self):
        return len(self.index) - 1

    def tip(self):
        return self.node(self.tiprev())

    def __contains__(self, rev):
        return 0 <= rev < len(self)

    def __len__(self):
        return len(self.index)

    def __iter__(self):
        return iter(pycompat.xrange(len(self)))

    def revs(self, start=0, stop=None):
        """iterate over all rev in this revlog (from start to stop)"""
        return storageutil.iterrevs(len(self), start=start, stop=stop)

    @property
    def nodemap(self):
        msg = (
            b"revlog.nodemap is deprecated, "
            b"use revlog.index.[has_node|rev|get_rev]"
        )
        util.nouideprecwarn(msg, b'5.3', stacklevel=2)
        return self.index.nodemap

    @property
    def _nodecache(self):
        msg = b"revlog._nodecache is deprecated, use revlog.index.nodemap"
        util.nouideprecwarn(msg, b'5.3', stacklevel=2)
        return self.index.nodemap

    def hasnode(self, node):
        try:
            self.rev(node)
            return True
        except KeyError:
            return False

    def candelta(self, baserev, rev):
        """whether two revisions (baserev, rev) can be delta-ed or not"""
        # Disable delta if either rev requires a content-changing flag
        # processor (ex. LFS). This is because such flag processor can alter
        # the rawtext content that the delta will be based on, and two clients
        # could have a same revlog node with different flags (i.e. different
        # rawtext contents) and the delta could be incompatible.
        if (self.flags(baserev) & REVIDX_RAWTEXT_CHANGING_FLAGS) or (
            self.flags(rev) & REVIDX_RAWTEXT_CHANGING_FLAGS
        ):
            return False
        return True

    def update_caches(self, transaction):
        if self._nodemap_file is not None:
            if transaction is None:
                nodemaputil.update_persistent_nodemap(self)
            else:
                nodemaputil.setup_persistent_nodemap(transaction, self)

    def clearcaches(self):
        self._revisioncache = None
        self._chainbasecache.clear()
        self._segmentfile.clear_cache()
        self._segmentfile_sidedata.clear_cache()
        self._pcache = {}
        self._nodemap_docket = None
        self.index.clearcaches()
        # The python code is the one responsible for validating the docket, we
        # end up having to refresh it here.
        use_nodemap = (
            not self._inline
            and self._nodemap_file is not None
            and util.safehasattr(self.index, 'update_nodemap_data')
        )
        if use_nodemap:
            nodemap_data = nodemaputil.persisted_data(self)
            if nodemap_data is not None:
                self._nodemap_docket = nodemap_data[0]
                self.index.update_nodemap_data(*nodemap_data)

    def rev(self, node):
        try:
            return self.index.rev(node)
        except TypeError:
            raise
        except error.RevlogError:
            # parsers.c radix tree lookup failed
            if (
                node == self.nodeconstants.wdirid
                or node in self.nodeconstants.wdirfilenodeids
            ):
                raise error.WdirUnsupported
            raise error.LookupError(node, self.display_id, _(b'no node'))

    # Accessors for index entries.

    # First tuple entry is 8 bytes. First 6 bytes are offset. Last 2 bytes
    # are flags.
    def start(self, rev):
        return int(self.index[rev][0] >> 16)

    def sidedata_cut_off(self, rev):
        sd_cut_off = self.index[rev][8]
        if sd_cut_off != 0:
            return sd_cut_off
        # This is some annoying dance, because entries without sidedata
        # currently use 0 as their ofsset. (instead of previous-offset +
        # previous-size)
        #
        # We should reconsider this sidedata → 0 sidata_offset policy.
        # In the meantime, we need this.
        while 0 <= rev:
            e = self.index[rev]
            if e[9] != 0:
                return e[8] + e[9]
            rev -= 1
        return 0

    def flags(self, rev):
        return self.index[rev][0] & 0xFFFF

    def length(self, rev):
        return self.index[rev][1]

    def sidedata_length(self, rev):
        if not self.hassidedata:
            return 0
        return self.index[rev][9]

    def rawsize(self, rev):
        """return the length of the uncompressed text for a given revision"""
        l = self.index[rev][2]
        if l >= 0:
            return l

        t = self.rawdata(rev)
        return len(t)

    def size(self, rev):
        """length of non-raw text (processed by a "read" flag processor)"""
        # fast path: if no "read" flag processor could change the content,
        # size is rawsize. note: ELLIPSIS is known to not change the content.
        flags = self.flags(rev)
        if flags & (flagutil.REVIDX_KNOWN_FLAGS ^ REVIDX_ELLIPSIS) == 0:
            return self.rawsize(rev)

        return len(self.revision(rev, raw=False))

    def chainbase(self, rev):
        base = self._chainbasecache.get(rev)
        if base is not None:
            return base

        index = self.index
        iterrev = rev
        base = index[iterrev][3]
        while base != iterrev:
            iterrev = base
            base = index[iterrev][3]

        self._chainbasecache[rev] = base
        return base

    def linkrev(self, rev):
        return self.index[rev][4]

    def parentrevs(self, rev):
        try:
            entry = self.index[rev]
        except IndexError:
            if rev == wdirrev:
                raise error.WdirUnsupported
            raise

        return entry[5], entry[6]

    # fast parentrevs(rev) where rev isn't filtered
    _uncheckedparentrevs = parentrevs

    def node(self, rev):
        try:
            return self.index[rev][7]
        except IndexError:
            if rev == wdirrev:
                raise error.WdirUnsupported
            raise

    # Derived from index values.

    def end(self, rev):
        return self.start(rev) + self.length(rev)

    def parents(self, node):
        i = self.index
        d = i[self.rev(node)]
        return i[d[5]][7], i[d[6]][7]  # map revisions to nodes inline

    def chainlen(self, rev):
        return self._chaininfo(rev)[0]

    def _chaininfo(self, rev):
        chaininfocache = self._chaininfocache
        if rev in chaininfocache:
            return chaininfocache[rev]
        index = self.index
        generaldelta = self._generaldelta
        iterrev = rev
        e = index[iterrev]
        clen = 0
        compresseddeltalen = 0
        while iterrev != e[3]:
            clen += 1
            compresseddeltalen += e[1]
            if generaldelta:
                iterrev = e[3]
            else:
                iterrev -= 1
            if iterrev in chaininfocache:
                t = chaininfocache[iterrev]
                clen += t[0]
                compresseddeltalen += t[1]
                break
            e = index[iterrev]
        else:
            # Add text length of base since decompressing that also takes
            # work. For cache hits the length is already included.
            compresseddeltalen += e[1]
        r = (clen, compresseddeltalen)
        chaininfocache[rev] = r
        return r

    def _deltachain(self, rev, stoprev=None):
        """Obtain the delta chain for a revision.

        ``stoprev`` specifies a revision to stop at. If not specified, we
        stop at the base of the chain.

        Returns a 2-tuple of (chain, stopped) where ``chain`` is a list of
        revs in ascending order and ``stopped`` is a bool indicating whether
        ``stoprev`` was hit.
        """
        # Try C implementation.
        try:
            return self.index.deltachain(rev, stoprev, self._generaldelta)
        except AttributeError:
            pass

        chain = []

        # Alias to prevent attribute lookup in tight loop.
        index = self.index
        generaldelta = self._generaldelta

        iterrev = rev
        e = index[iterrev]
        while iterrev != e[3] and iterrev != stoprev:
            chain.append(iterrev)
            if generaldelta:
                iterrev = e[3]
            else:
                iterrev -= 1
            e = index[iterrev]

        if iterrev == stoprev:
            stopped = True
        else:
            chain.append(iterrev)
            stopped = False

        chain.reverse()
        return chain, stopped

    def ancestors(self, revs, stoprev=0, inclusive=False):
        """Generate the ancestors of 'revs' in reverse revision order.
        Does not generate revs lower than stoprev.

        See the documentation for ancestor.lazyancestors for more details."""

        # first, make sure start revisions aren't filtered
        revs = list(revs)
        checkrev = self.node
        for r in revs:
            checkrev(r)
        # and we're sure ancestors aren't filtered as well

        if rustancestor is not None and self.index.rust_ext_compat:
            lazyancestors = rustancestor.LazyAncestors
            arg = self.index
        else:
            lazyancestors = ancestor.lazyancestors
            arg = self._uncheckedparentrevs
        return lazyancestors(arg, revs, stoprev=stoprev, inclusive=inclusive)

    def descendants(self, revs):
        return dagop.descendantrevs(revs, self.revs, self.parentrevs)

    def findcommonmissing(self, common=None, heads=None):
        """Return a tuple of the ancestors of common and the ancestors of heads
        that are not ancestors of common. In revset terminology, we return the
        tuple:

          ::common, (::heads) - (::common)

        The list is sorted by revision number, meaning it is
        topologically sorted.

        'heads' and 'common' are both lists of node IDs.  If heads is
        not supplied, uses all of the revlog's heads.  If common is not
        supplied, uses nullid."""
        if common is None:
            common = [self.nullid]
        if heads is None:
            heads = self.heads()

        common = [self.rev(n) for n in common]
        heads = [self.rev(n) for n in heads]

        # we want the ancestors, but inclusive
        class lazyset(object):
            def __init__(self, lazyvalues):
                self.addedvalues = set()
                self.lazyvalues = lazyvalues

            def __contains__(self, value):
                return value in self.addedvalues or value in self.lazyvalues

            def __iter__(self):
                added = self.addedvalues
                for r in added:
                    yield r
                for r in self.lazyvalues:
                    if not r in added:
                        yield r

            def add(self, value):
                self.addedvalues.add(value)

            def update(self, values):
                self.addedvalues.update(values)

        has = lazyset(self.ancestors(common))
        has.add(nullrev)
        has.update(common)

        # take all ancestors from heads that aren't in has
        missing = set()
        visit = collections.deque(r for r in heads if r not in has)
        while visit:
            r = visit.popleft()
            if r in missing:
                continue
            else:
                missing.add(r)
                for p in self.parentrevs(r):
                    if p not in has:
                        visit.append(p)
        missing = list(missing)
        missing.sort()
        return has, [self.node(miss) for miss in missing]

    def incrementalmissingrevs(self, common=None):
        """Return an object that can be used to incrementally compute the
        revision numbers of the ancestors of arbitrary sets that are not
        ancestors of common. This is an ancestor.incrementalmissingancestors
        object.

        'common' is a list of revision numbers. If common is not supplied, uses
        nullrev.
        """
        if common is None:
            common = [nullrev]

        if rustancestor is not None and self.index.rust_ext_compat:
            return rustancestor.MissingAncestors(self.index, common)
        return ancestor.incrementalmissingancestors(self.parentrevs, common)

    def findmissingrevs(self, common=None, heads=None):
        """Return the revision numbers of the ancestors of heads that
        are not ancestors of common.

        More specifically, return a list of revision numbers corresponding to
        nodes N such that every N satisfies the following constraints:

          1. N is an ancestor of some node in 'heads'
          2. N is not an ancestor of any node in 'common'

        The list is sorted by revision number, meaning it is
        topologically sorted.

        'heads' and 'common' are both lists of revision numbers.  If heads is
        not supplied, uses all of the revlog's heads.  If common is not
        supplied, uses nullid."""
        if common is None:
            common = [nullrev]
        if heads is None:
            heads = self.headrevs()

        inc = self.incrementalmissingrevs(common=common)
        return inc.missingancestors(heads)

    def findmissing(self, common=None, heads=None):
        """Return the ancestors of heads that are not ancestors of common.

        More specifically, return a list of nodes N such that every N
        satisfies the following constraints:

          1. N is an ancestor of some node in 'heads'
          2. N is not an ancestor of any node in 'common'

        The list is sorted by revision number, meaning it is
        topologically sorted.

        'heads' and 'common' are both lists of node IDs.  If heads is
        not supplied, uses all of the revlog's heads.  If common is not
        supplied, uses nullid."""
        if common is None:
            common = [self.nullid]
        if heads is None:
            heads = self.heads()

        common = [self.rev(n) for n in common]
        heads = [self.rev(n) for n in heads]

        inc = self.incrementalmissingrevs(common=common)
        return [self.node(r) for r in inc.missingancestors(heads)]

    def nodesbetween(self, roots=None, heads=None):
        """Return a topological path from 'roots' to 'heads'.

        Return a tuple (nodes, outroots, outheads) where 'nodes' is a
        topologically sorted list of all nodes N that satisfy both of
        these constraints:

          1. N is a descendant of some node in 'roots'
          2. N is an ancestor of some node in 'heads'

        Every node is considered to be both a descendant and an ancestor
        of itself, so every reachable node in 'roots' and 'heads' will be
        included in 'nodes'.

        'outroots' is the list of reachable nodes in 'roots', i.e., the
        subset of 'roots' that is returned in 'nodes'.  Likewise,
        'outheads' is the subset of 'heads' that is also in 'nodes'.

        'roots' and 'heads' are both lists of node IDs.  If 'roots' is
        unspecified, uses nullid as the only root.  If 'heads' is
        unspecified, uses list of all of the revlog's heads."""
        nonodes = ([], [], [])
        if roots is not None:
            roots = list(roots)
            if not roots:
                return nonodes
            lowestrev = min([self.rev(n) for n in roots])
        else:
            roots = [self.nullid]  # Everybody's a descendant of nullid
            lowestrev = nullrev
        if (lowestrev == nullrev) and (heads is None):
            # We want _all_ the nodes!
            return (
                [self.node(r) for r in self],
                [self.nullid],
                list(self.heads()),
            )
        if heads is None:
            # All nodes are ancestors, so the latest ancestor is the last
            # node.
            highestrev = len(self) - 1
            # Set ancestors to None to signal that every node is an ancestor.
            ancestors = None
            # Set heads to an empty dictionary for later discovery of heads
            heads = {}
        else:
            heads = list(heads)
            if not heads:
                return nonodes
            ancestors = set()
            # Turn heads into a dictionary so we can remove 'fake' heads.
            # Also, later we will be using it to filter out the heads we can't
            # find from roots.
            heads = dict.fromkeys(heads, False)
            # Start at the top and keep marking parents until we're done.
            nodestotag = set(heads)
            # Remember where the top was so we can use it as a limit later.
            highestrev = max([self.rev(n) for n in nodestotag])
            while nodestotag:
                # grab a node to tag
                n = nodestotag.pop()
                # Never tag nullid
                if n == self.nullid:
                    continue
                # A node's revision number represents its place in a
                # topologically sorted list of nodes.
                r = self.rev(n)
                if r >= lowestrev:
                    if n not in ancestors:
                        # If we are possibly a descendant of one of the roots
                        # and we haven't already been marked as an ancestor
                        ancestors.add(n)  # Mark as ancestor
                        # Add non-nullid parents to list of nodes to tag.
                        nodestotag.update(
                            [p for p in self.parents(n) if p != self.nullid]
                        )
                    elif n in heads:  # We've seen it before, is it a fake head?
                        # So it is, real heads should not be the ancestors of
                        # any other heads.
                        heads.pop(n)
            if not ancestors:
                return nonodes
            # Now that we have our set of ancestors, we want to remove any
            # roots that are not ancestors.

            # If one of the roots was nullid, everything is included anyway.
            if lowestrev > nullrev:
                # But, since we weren't, let's recompute the lowest rev to not
                # include roots that aren't ancestors.

                # Filter out roots that aren't ancestors of heads
                roots = [root for root in roots if root in ancestors]
                # Recompute the lowest revision
                if roots:
                    lowestrev = min([self.rev(root) for root in roots])
                else:
                    # No more roots?  Return empty list
                    return nonodes
            else:
                # We are descending from nullid, and don't need to care about
                # any other roots.
                lowestrev = nullrev
                roots = [self.nullid]
        # Transform our roots list into a set.
        descendants = set(roots)
        # Also, keep the original roots so we can filter out roots that aren't
        # 'real' roots (i.e. are descended from other roots).
        roots = descendants.copy()
        # Our topologically sorted list of output nodes.
        orderedout = []
        # Don't start at nullid since we don't want nullid in our output list,
        # and if nullid shows up in descendants, empty parents will look like
        # they're descendants.
        for r in self.revs(start=max(lowestrev, 0), stop=highestrev + 1):
            n = self.node(r)
            isdescendant = False
            if lowestrev == nullrev:  # Everybody is a descendant of nullid
                isdescendant = True
            elif n in descendants:
                # n is already a descendant
                isdescendant = True
                # This check only needs to be done here because all the roots
                # will start being marked is descendants before the loop.
                if n in roots:
                    # If n was a root, check if it's a 'real' root.
                    p = tuple(self.parents(n))
                    # If any of its parents are descendants, it's not a root.
                    if (p[0] in descendants) or (p[1] in descendants):
                        roots.remove(n)
            else:
                p = tuple(self.parents(n))
                # A node is a descendant if either of its parents are
                # descendants.  (We seeded the dependents list with the roots
                # up there, remember?)
                if (p[0] in descendants) or (p[1] in descendants):
                    descendants.add(n)
                    isdescendant = True
            if isdescendant and ((ancestors is None) or (n in ancestors)):
                # Only include nodes that are both descendants and ancestors.
                orderedout.append(n)
                if (ancestors is not None) and (n in heads):
                    # We're trying to figure out which heads are reachable
                    # from roots.
                    # Mark this head as having been reached
                    heads[n] = True
                elif ancestors is None:
                    # Otherwise, we're trying to discover the heads.
                    # Assume this is a head because if it isn't, the next step
                    # will eventually remove it.
                    heads[n] = True
                    # But, obviously its parents aren't.
                    for p in self.parents(n):
                        heads.pop(p, None)
        heads = [head for head, flag in pycompat.iteritems(heads) if flag]
        roots = list(roots)
        assert orderedout
        assert roots
        assert heads
        return (orderedout, roots, heads)

    def headrevs(self, revs=None):
        if revs is None:
            try:
                return self.index.headrevs()
            except AttributeError:
                return self._headrevs()
        if rustdagop is not None and self.index.rust_ext_compat:
            return rustdagop.headrevs(self.index, revs)
        return dagop.headrevs(revs, self._uncheckedparentrevs)

    def computephases(self, roots):
        return self.index.computephasesmapsets(roots)

    def _headrevs(self):
        count = len(self)
        if not count:
            return [nullrev]
        # we won't iter over filtered rev so nobody is a head at start
        ishead = [0] * (count + 1)
        index = self.index
        for r in self:
            ishead[r] = 1  # I may be an head
            e = index[r]
            ishead[e[5]] = ishead[e[6]] = 0  # my parent are not
        return [r for r, val in enumerate(ishead) if val]

    def heads(self, start=None, stop=None):
        """return the list of all nodes that have no children

        if start is specified, only heads that are descendants of
        start will be returned
        if stop is specified, it will consider all the revs from stop
        as if they had no children
        """
        if start is None and stop is None:
            if not len(self):
                return [self.nullid]
            return [self.node(r) for r in self.headrevs()]

        if start is None:
            start = nullrev
        else:
            start = self.rev(start)

        stoprevs = {self.rev(n) for n in stop or []}

        revs = dagop.headrevssubset(
            self.revs, self.parentrevs, startrev=start, stoprevs=stoprevs
        )

        return [self.node(rev) for rev in revs]

    def children(self, node):
        """find the children of a given node"""
        c = []
        p = self.rev(node)
        for r in self.revs(start=p + 1):
            prevs = [pr for pr in self.parentrevs(r) if pr != nullrev]
            if prevs:
                for pr in prevs:
                    if pr == p:
                        c.append(self.node(r))
            elif p == nullrev:
                c.append(self.node(r))
        return c

    def commonancestorsheads(self, a, b):
        """calculate all the heads of the common ancestors of nodes a and b"""
        a, b = self.rev(a), self.rev(b)
        ancs = self._commonancestorsheads(a, b)
        return pycompat.maplist(self.node, ancs)

    def _commonancestorsheads(self, *revs):
        """calculate all the heads of the common ancestors of revs"""
        try:
            ancs = self.index.commonancestorsheads(*revs)
        except (AttributeError, OverflowError):  # C implementation failed
            ancs = ancestor.commonancestorsheads(self.parentrevs, *revs)
        return ancs

    def isancestor(self, a, b):
        """return True if node a is an ancestor of node b

        A revision is considered an ancestor of itself."""
        a, b = self.rev(a), self.rev(b)
        return self.isancestorrev(a, b)

    def isancestorrev(self, a, b):
        """return True if revision a is an ancestor of revision b

        A revision is considered an ancestor of itself.

        The implementation of this is trivial but the use of
        reachableroots is not."""
        if a == nullrev:
            return True
        elif a == b:
            return True
        elif a > b:
            return False
        return bool(self.reachableroots(a, [b], [a], includepath=False))

    def reachableroots(self, minroot, heads, roots, includepath=False):
        """return (heads(::(<roots> and <roots>::<heads>)))

        If includepath is True, return (<roots>::<heads>)."""
        try:
            return self.index.reachableroots2(
                minroot, heads, roots, includepath
            )
        except AttributeError:
            return dagop._reachablerootspure(
                self.parentrevs, minroot, roots, heads, includepath
            )

    def ancestor(self, a, b):
        """calculate the "best" common ancestor of nodes a and b"""

        a, b = self.rev(a), self.rev(b)
        try:
            ancs = self.index.ancestors(a, b)
        except (AttributeError, OverflowError):
            ancs = ancestor.ancestors(self.parentrevs, a, b)
        if ancs:
            # choose a consistent winner when there's a tie
            return min(map(self.node, ancs))
        return self.nullid

    def _match(self, id):
        if isinstance(id, int):
            # rev
            return self.node(id)
        if len(id) == self.nodeconstants.nodelen:
            # possibly a binary node
            # odds of a binary node being all hex in ASCII are 1 in 10**25
            try:
                node = id
                self.rev(node)  # quick search the index
                return node
            except error.LookupError:
                pass  # may be partial hex id
        try:
            # str(rev)
            rev = int(id)
            if b"%d" % rev != id:
                raise ValueError
            if rev < 0:
                rev = len(self) + rev
            if rev < 0 or rev >= len(self):
                raise ValueError
            return self.node(rev)
        except (ValueError, OverflowError):
            pass
        if len(id) == 2 * self.nodeconstants.nodelen:
            try:
                # a full hex nodeid?
                node = bin(id)
                self.rev(node)
                return node
            except (TypeError, error.LookupError):
                pass

    def _partialmatch(self, id):
        # we don't care wdirfilenodeids as they should be always full hash
        maybewdir = self.nodeconstants.wdirhex.startswith(id)
        ambiguous = False
        try:
            partial = self.index.partialmatch(id)
            if partial and self.hasnode(partial):
                if maybewdir:
                    # single 'ff...' match in radix tree, ambiguous with wdir
                    ambiguous = True
                else:
                    return partial
            elif maybewdir:
                # no 'ff...' match in radix tree, wdir identified
                raise error.WdirUnsupported
            else:
                return None
        except error.RevlogError:
            # parsers.c radix tree lookup gave multiple matches
            # fast path: for unfiltered changelog, radix tree is accurate
            if not getattr(self, 'filteredrevs', None):
                ambiguous = True
            # fall through to slow path that filters hidden revisions
        except (AttributeError, ValueError):
            # we are pure python, or key was too short to search radix tree
            pass
        if ambiguous:
            raise error.AmbiguousPrefixLookupError(
                id, self.display_id, _(b'ambiguous identifier')
            )

        if id in self._pcache:
            return self._pcache[id]

        if len(id) <= 40:
            try:
                # hex(node)[:...]
                l = len(id) // 2  # grab an even number of digits
                prefix = bin(id[: l * 2])
                nl = [e[7] for e in self.index if e[7].startswith(prefix)]
                nl = [
                    n for n in nl if hex(n).startswith(id) and self.hasnode(n)
                ]
                if self.nodeconstants.nullhex.startswith(id):
                    nl.append(self.nullid)
                if len(nl) > 0:
                    if len(nl) == 1 and not maybewdir:
                        self._pcache[id] = nl[0]
                        return nl[0]
                    raise error.AmbiguousPrefixLookupError(
                        id, self.display_id, _(b'ambiguous identifier')
                    )
                if maybewdir:
                    raise error.WdirUnsupported
                return None
            except TypeError:
                pass

    def lookup(self, id):
        """locate a node based on:
        - revision number or str(revision number)
        - nodeid or subset of hex nodeid
        """
        n = self._match(id)
        if n is not None:
            return n
        n = self._partialmatch(id)
        if n:
            return n

        raise error.LookupError(id, self.display_id, _(b'no match found'))

    def shortest(self, node, minlength=1):
        """Find the shortest unambiguous prefix that matches node."""

        def isvalid(prefix):
            try:
                matchednode = self._partialmatch(prefix)
            except error.AmbiguousPrefixLookupError:
                return False
            except error.WdirUnsupported:
                # single 'ff...' match
                return True
            if matchednode is None:
                raise error.LookupError(node, self.display_id, _(b'no node'))
            return True

        def maybewdir(prefix):
            return all(c == b'f' for c in pycompat.iterbytestr(prefix))

        hexnode = hex(node)

        def disambiguate(hexnode, minlength):
            """Disambiguate against wdirid."""
            for length in range(minlength, len(hexnode) + 1):
                prefix = hexnode[:length]
                if not maybewdir(prefix):
                    return prefix

        if not getattr(self, 'filteredrevs', None):
            try:
                length = max(self.index.shortest(node), minlength)
                return disambiguate(hexnode, length)
            except error.RevlogError:
                if node != self.nodeconstants.wdirid:
                    raise error.LookupError(
                        node, self.display_id, _(b'no node')
                    )
            except AttributeError:
                # Fall through to pure code
                pass

        if node == self.nodeconstants.wdirid:
            for length in range(minlength, len(hexnode) + 1):
                prefix = hexnode[:length]
                if isvalid(prefix):
                    return prefix

        for length in range(minlength, len(hexnode) + 1):
            prefix = hexnode[:length]
            if isvalid(prefix):
                return disambiguate(hexnode, length)

    def cmp(self, node, text):
        """compare text with a given file revision

        returns True if text is different than what is stored.
        """
        p1, p2 = self.parents(node)
        return storageutil.hashrevisionsha1(text, p1, p2) != node

    def _getsegmentforrevs(self, startrev, endrev, df=None):
        """Obtain a segment of raw data corresponding to a range of revisions.

        Accepts the start and end revisions and an optional already-open
        file handle to be used for reading. If the file handle is read, its
        seek position will not be preserved.

        Requests for data may be satisfied by a cache.

        Returns a 2-tuple of (offset, data) for the requested range of
        revisions. Offset is the integer offset from the beginning of the
        revlog and data is a str or buffer of the raw byte data.

        Callers will need to call ``self.start(rev)`` and ``self.length(rev)``
        to determine where each revision's data begins and ends.
        """
        # Inlined self.start(startrev) & self.end(endrev) for perf reasons
        # (functions are expensive).
        index = self.index
        istart = index[startrev]
        start = int(istart[0] >> 16)
        if startrev == endrev:
            end = start + istart[1]
        else:
            iend = index[endrev]
            end = int(iend[0] >> 16) + iend[1]

        if self._inline:
            start += (startrev + 1) * self.index.entry_size
            end += (endrev + 1) * self.index.entry_size
        length = end - start

        return start, self._segmentfile.read_chunk(start, length, df)

    def _chunk(self, rev, df=None):
        """Obtain a single decompressed chunk for a revision.

        Accepts an integer revision and an optional already-open file handle
        to be used for reading. If used, the seek position of the file will not
        be preserved.

        Returns a str holding uncompressed data for the requested revision.
        """
        compression_mode = self.index[rev][10]
        data = self._getsegmentforrevs(rev, rev, df=df)[1]
        if compression_mode == COMP_MODE_PLAIN:
            return data
        elif compression_mode == COMP_MODE_DEFAULT:
            return self._decompressor(data)
        elif compression_mode == COMP_MODE_INLINE:
            return self.decompress(data)
        else:
            msg = b'unknown compression mode %d'
            msg %= compression_mode
            raise error.RevlogError(msg)

    def _chunks(self, revs, df=None, targetsize=None):
        """Obtain decompressed chunks for the specified revisions.

        Accepts an iterable of numeric revisions that are assumed to be in
        ascending order. Also accepts an optional already-open file handle
        to be used for reading. If used, the seek position of the file will
        not be preserved.

        This function is similar to calling ``self._chunk()`` multiple times,
        but is faster.

        Returns a list with decompressed data for each requested revision.
        """
        if not revs:
            return []
        start = self.start
        length = self.length
        inline = self._inline
        iosize = self.index.entry_size
        buffer = util.buffer

        l = []
        ladd = l.append

        if not self._withsparseread:
            slicedchunks = (revs,)
        else:
            slicedchunks = deltautil.slicechunk(
                self, revs, targetsize=targetsize
            )

        for revschunk in slicedchunks:
            firstrev = revschunk[0]
            # Skip trailing revisions with empty diff
            for lastrev in revschunk[::-1]:
                if length(lastrev) != 0:
                    break

            try:
                offset, data = self._getsegmentforrevs(firstrev, lastrev, df=df)
            except OverflowError:
                # issue4215 - we can't cache a run of chunks greater than
                # 2G on Windows
                return [self._chunk(rev, df=df) for rev in revschunk]

            decomp = self.decompress
            # self._decompressor might be None, but will not be used in that case
            def_decomp = self._decompressor
            for rev in revschunk:
                chunkstart = start(rev)
                if inline:
                    chunkstart += (rev + 1) * iosize
                chunklength = length(rev)
                comp_mode = self.index[rev][10]
                c = buffer(data, chunkstart - offset, chunklength)
                if comp_mode == COMP_MODE_PLAIN:
                    ladd(c)
                elif comp_mode == COMP_MODE_INLINE:
                    ladd(decomp(c))
                elif comp_mode == COMP_MODE_DEFAULT:
                    ladd(def_decomp(c))
                else:
                    msg = b'unknown compression mode %d'
                    msg %= comp_mode
                    raise error.RevlogError(msg)

        return l

    def deltaparent(self, rev):
        """return deltaparent of the given revision"""
        base = self.index[rev][3]
        if base == rev:
            return nullrev
        elif self._generaldelta:
            return base
        else:
            return rev - 1

    def issnapshot(self, rev):
        """tells whether rev is a snapshot"""
        if not self._sparserevlog:
            return self.deltaparent(rev) == nullrev
        elif util.safehasattr(self.index, b'issnapshot'):
            # directly assign the method to cache the testing and access
            self.issnapshot = self.index.issnapshot
            return self.issnapshot(rev)
        if rev == nullrev:
            return True
        entry = self.index[rev]
        base = entry[3]
        if base == rev:
            return True
        if base == nullrev:
            return True
        p1 = entry[5]
        p2 = entry[6]
        if base == p1 or base == p2:
            return False
        return self.issnapshot(base)

    def snapshotdepth(self, rev):
        """number of snapshot in the chain before this one"""
        if not self.issnapshot(rev):
            raise error.ProgrammingError(b'revision %d not a snapshot')
        return len(self._deltachain(rev)[0]) - 1

    def revdiff(self, rev1, rev2):
        """return or calculate a delta between two revisions

        The delta calculated is in binary form and is intended to be written to
        revlog data directly. So this function needs raw revision data.
        """
        if rev1 != nullrev and self.deltaparent(rev2) == rev1:
            return bytes(self._chunk(rev2))

        return mdiff.textdiff(self.rawdata(rev1), self.rawdata(rev2))

    def _processflags(self, text, flags, operation, raw=False):
        """deprecated entry point to access flag processors"""
        msg = b'_processflag(...) use the specialized variant'
        util.nouideprecwarn(msg, b'5.2', stacklevel=2)
        if raw:
            return text, flagutil.processflagsraw(self, text, flags)
        elif operation == b'read':
            return flagutil.processflagsread(self, text, flags)
        else:  # write operation
            return flagutil.processflagswrite(self, text, flags)

    def revision(self, nodeorrev, _df=None, raw=False):
        """return an uncompressed revision of a given node or revision
        number.

        _df - an existing file handle to read from. (internal-only)
        raw - an optional argument specifying if the revision data is to be
        treated as raw data when applying flag transforms. 'raw' should be set
        to True when generating changegroups or in debug commands.
        """
        if raw:
            msg = (
                b'revlog.revision(..., raw=True) is deprecated, '
                b'use revlog.rawdata(...)'
            )
            util.nouideprecwarn(msg, b'5.2', stacklevel=2)
        return self._revisiondata(nodeorrev, _df, raw=raw)

    def sidedata(self, nodeorrev, _df=None):
        """a map of extra data related to the changeset but not part of the hash

        This function currently return a dictionary. However, more advanced
        mapping object will likely be used in the future for a more
        efficient/lazy code.
        """
        # deal with <nodeorrev> argument type
        if isinstance(nodeorrev, int):
            rev = nodeorrev
        else:
            rev = self.rev(nodeorrev)
        return self._sidedata(rev)

    def _revisiondata(self, nodeorrev, _df=None, raw=False):
        # deal with <nodeorrev> argument type
        if isinstance(nodeorrev, int):
            rev = nodeorrev
            node = self.node(rev)
        else:
            node = nodeorrev
            rev = None

        # fast path the special `nullid` rev
        if node == self.nullid:
            return b""

        # ``rawtext`` is the text as stored inside the revlog. Might be the
        # revision or might need to be processed to retrieve the revision.
        rev, rawtext, validated = self._rawtext(node, rev, _df=_df)

        if raw and validated:
            # if we don't want to process the raw text and that raw
            # text is cached, we can exit early.
            return rawtext
        if rev is None:
            rev = self.rev(node)
        # the revlog's flag for this revision
        # (usually alter its state or content)
        flags = self.flags(rev)

        if validated and flags == REVIDX_DEFAULT_FLAGS:
            # no extra flags set, no flag processor runs, text = rawtext
            return rawtext

        if raw:
            validatehash = flagutil.processflagsraw(self, rawtext, flags)
            text = rawtext
        else:
            r = flagutil.processflagsread(self, rawtext, flags)
            text, validatehash = r
        if validatehash:
            self.checkhash(text, node, rev=rev)
        if not validated:
            self._revisioncache = (node, rev, rawtext)

        return text

    def _rawtext(self, node, rev, _df=None):
        """return the possibly unvalidated rawtext for a revision

        returns (rev, rawtext, validated)
        """

        # revision in the cache (could be useful to apply delta)
        cachedrev = None
        # An intermediate text to apply deltas to
        basetext = None

        # Check if we have the entry in cache
        # The cache entry looks like (node, rev, rawtext)
        if self._revisioncache:
            if self._revisioncache[0] == node:
                return (rev, self._revisioncache[2], True)
            cachedrev = self._revisioncache[1]

        if rev is None:
            rev = self.rev(node)

        chain, stopped = self._deltachain(rev, stoprev=cachedrev)
        if stopped:
            basetext = self._revisioncache[2]

        # drop cache to save memory, the caller is expected to
        # update self._revisioncache after validating the text
        self._revisioncache = None

        targetsize = None
        rawsize = self.index[rev][2]
        if 0 <= rawsize:
            targetsize = 4 * rawsize

        bins = self._chunks(chain, df=_df, targetsize=targetsize)
        if basetext is None:
            basetext = bytes(bins[0])
            bins = bins[1:]

        rawtext = mdiff.patches(basetext, bins)
        del basetext  # let us have a chance to free memory early
        return (rev, rawtext, False)

    def _sidedata(self, rev):
        """Return the sidedata for a given revision number."""
        index_entry = self.index[rev]
        sidedata_offset = index_entry[8]
        sidedata_size = index_entry[9]

        if self._inline:
            sidedata_offset += self.index.entry_size * (1 + rev)
        if sidedata_size == 0:
            return {}

        if self._docket.sidedata_end < sidedata_offset + sidedata_size:
            filename = self._sidedatafile
            end = self._docket.sidedata_end
            offset = sidedata_offset
            length = sidedata_size
            m = FILE_TOO_SHORT_MSG % (filename, length, offset, end)
            raise error.RevlogError(m)

        comp_segment = self._segmentfile_sidedata.read_chunk(
            sidedata_offset, sidedata_size
        )

        comp = self.index[rev][11]
        if comp == COMP_MODE_PLAIN:
            segment = comp_segment
        elif comp == COMP_MODE_DEFAULT:
            segment = self._decompressor(comp_segment)
        elif comp == COMP_MODE_INLINE:
            segment = self.decompress(comp_segment)
        else:
            msg = b'unknown compression mode %d'
            msg %= comp
            raise error.RevlogError(msg)

        sidedata = sidedatautil.deserialize_sidedata(segment)
        return sidedata

    def rawdata(self, nodeorrev, _df=None):
        """return an uncompressed raw data of a given node or revision number.

        _df - an existing file handle to read from. (internal-only)
        """
        return self._revisiondata(nodeorrev, _df, raw=True)

    def hash(self, text, p1, p2):
        """Compute a node hash.

        Available as a function so that subclasses can replace the hash
        as needed.
        """
        return storageutil.hashrevisionsha1(text, p1, p2)

    def checkhash(self, text, node, p1=None, p2=None, rev=None):
        """Check node hash integrity.

        Available as a function so that subclasses can extend hash mismatch
        behaviors as needed.
        """
        try:
            if p1 is None and p2 is None:
                p1, p2 = self.parents(node)
            if node != self.hash(text, p1, p2):
                # Clear the revision cache on hash failure. The revision cache
                # only stores the raw revision and clearing the cache does have
                # the side-effect that we won't have a cache hit when the raw
                # revision data is accessed. But this case should be rare and
                # it is extra work to teach the cache about the hash
                # verification state.
                if self._revisioncache and self._revisioncache[0] == node:
                    self._revisioncache = None

                revornode = rev
                if revornode is None:
                    revornode = templatefilters.short(hex(node))
                raise error.RevlogError(
                    _(b"integrity check failed on %s:%s")
                    % (self.display_id, pycompat.bytestr(revornode))
                )
        except error.RevlogError:
            if self._censorable and storageutil.iscensoredtext(text):
                raise error.CensoredNodeError(self.display_id, node, text)
            raise

    def _enforceinlinesize(self, tr):
        """Check if the revlog is too big for inline and convert if so.

        This should be called after revisions are added to the revlog. If the
        revlog has grown too large to be an inline revlog, it will convert it
        to use multiple index and data files.
        """
        tiprev = len(self) - 1
        total_size = self.start(tiprev) + self.length(tiprev)
        if not self._inline or total_size < _maxinline:
            return

        troffset = tr.findoffset(self._indexfile)
        if troffset is None:
            raise error.RevlogError(
                _(b"%s not found in the transaction") % self._indexfile
            )
        trindex = 0
        tr.add(self._datafile, 0)

        existing_handles = False
        if self._writinghandles is not None:
            existing_handles = True
            fp = self._writinghandles[0]
            fp.flush()
            fp.close()
            # We can't use the cached file handle after close(). So prevent
            # its usage.
            self._writinghandles = None
            self._segmentfile.writing_handle = None
            # No need to deal with sidedata writing handle as it is only
            # relevant with revlog-v2 which is never inline, not reaching
            # this code

        new_dfh = self._datafp(b'w+')
        new_dfh.truncate(0)  # drop any potentially existing data
        try:
            with self._indexfp() as read_ifh:
                for r in self:
                    new_dfh.write(self._getsegmentforrevs(r, r, df=read_ifh)[1])
                    if troffset <= self.start(r) + r * self.index.entry_size:
                        trindex = r
                new_dfh.flush()

            with self.__index_new_fp() as fp:
                self._format_flags &= ~FLAG_INLINE_DATA
                self._inline = False
                for i in self:
                    e = self.index.entry_binary(i)
                    if i == 0 and self._docket is None:
                        header = self._format_flags | self._format_version
                        header = self.index.pack_header(header)
                        e = header + e
                    fp.write(e)
                if self._docket is not None:
                    self._docket.index_end = fp.tell()

                # There is a small transactional race here. If the rename of
                # the index fails, we should remove the datafile. It is more
                # important to ensure that the data file is not truncated
                # when the index is replaced as otherwise data is lost.
                tr.replace(self._datafile, self.start(trindex))

                # the temp file replace the real index when we exit the context
                # manager

            tr.replace(self._indexfile, trindex * self.index.entry_size)
            nodemaputil.setup_persistent_nodemap(tr, self)
            self._segmentfile = randomaccessfile.randomaccessfile(
                self.opener,
                self._datafile,
                self._chunkcachesize,
            )

            if existing_handles:
                # switched from inline to conventional reopen the index
                ifh = self.__index_write_fp()
                self._writinghandles = (ifh, new_dfh, None)
                self._segmentfile.writing_handle = new_dfh
                new_dfh = None
                # No need to deal with sidedata writing handle as it is only
                # relevant with revlog-v2 which is never inline, not reaching
                # this code
        finally:
            if new_dfh is not None:
                new_dfh.close()

    def _nodeduplicatecallback(self, transaction, node):
        """called when trying to add a node already stored."""

    @contextlib.contextmanager
    def reading(self):
        """Context manager that keeps data and sidedata files open for reading"""
        with self._segmentfile.reading():
            with self._segmentfile_sidedata.reading():
                yield

    @contextlib.contextmanager
    def _writing(self, transaction):
        if self._trypending:
            msg = b'try to write in a `trypending` revlog: %s'
            msg %= self.display_id
            raise error.ProgrammingError(msg)
        if self._writinghandles is not None:
            yield
        else:
            ifh = dfh = sdfh = None
            try:
                r = len(self)
                # opening the data file.
                dsize = 0
                if r:
                    dsize = self.end(r - 1)
                dfh = None
                if not self._inline:
                    try:
                        dfh = self._datafp(b"r+")
                        if self._docket is None:
                            dfh.seek(0, os.SEEK_END)
                        else:
                            dfh.seek(self._docket.data_end, os.SEEK_SET)
                    except IOError as inst:
                        if inst.errno != errno.ENOENT:
                            raise
                        dfh = self._datafp(b"w+")
                    transaction.add(self._datafile, dsize)
                if self._sidedatafile is not None:
                    # revlog-v2 does not inline, help Pytype
                    assert dfh is not None
                    try:
                        sdfh = self.opener(self._sidedatafile, mode=b"r+")
                        dfh.seek(self._docket.sidedata_end, os.SEEK_SET)
                    except IOError as inst:
                        if inst.errno != errno.ENOENT:
                            raise
                        sdfh = self.opener(self._sidedatafile, mode=b"w+")
                    transaction.add(
                        self._sidedatafile, self._docket.sidedata_end
                    )

                # opening the index file.
                isize = r * self.index.entry_size
                ifh = self.__index_write_fp()
                if self._inline:
                    transaction.add(self._indexfile, dsize + isize)
                else:
                    transaction.add(self._indexfile, isize)
                # exposing all file handle for writing.
                self._writinghandles = (ifh, dfh, sdfh)
                self._segmentfile.writing_handle = ifh if self._inline else dfh
                self._segmentfile_sidedata.writing_handle = sdfh
                yield
                if self._docket is not None:
                    self._write_docket(transaction)
            finally:
                self._writinghandles = None
                self._segmentfile.writing_handle = None
                self._segmentfile_sidedata.writing_handle = None
                if dfh is not None:
                    dfh.close()
                if sdfh is not None:
                    sdfh.close()
                # closing the index file last to avoid exposing referent to
                # potential unflushed data content.
                if ifh is not None:
                    ifh.close()

    def _write_docket(self, transaction):
        """write the current docket on disk

        Exist as a method to help changelog to implement transaction logic

        We could also imagine using the same transaction logic for all revlog
        since docket are cheap."""
        self._docket.write(transaction)

    def addrevision(
        self,
        text,
        transaction,
        link,
        p1,
        p2,
        cachedelta=None,
        node=None,
        flags=REVIDX_DEFAULT_FLAGS,
        deltacomputer=None,
        sidedata=None,
    ):
        """add a revision to the log

        text - the revision data to add
        transaction - the transaction object used for rollback
        link - the linkrev data to add
        p1, p2 - the parent nodeids of the revision
        cachedelta - an optional precomputed delta
        node - nodeid of revision; typically node is not specified, and it is
            computed by default as hash(text, p1, p2), however subclasses might
            use different hashing method (and override checkhash() in such case)
        flags - the known flags to set on the revision
        deltacomputer - an optional deltacomputer instance shared between
            multiple calls
        """
        if link == nullrev:
            raise error.RevlogError(
                _(b"attempted to add linkrev -1 to %s") % self.display_id
            )

        if sidedata is None:
            sidedata = {}
        elif sidedata and not self.hassidedata:
            raise error.ProgrammingError(
                _(b"trying to add sidedata to a revlog who don't support them")
            )

        if flags:
            node = node or self.hash(text, p1, p2)

        rawtext, validatehash = flagutil.processflagswrite(self, text, flags)

        # If the flag processor modifies the revision data, ignore any provided
        # cachedelta.
        if rawtext != text:
            cachedelta = None

        if len(rawtext) > _maxentrysize:
            raise error.RevlogError(
                _(
                    b"%s: size of %d bytes exceeds maximum revlog storage of 2GiB"
                )
                % (self.display_id, len(rawtext))
            )

        node = node or self.hash(rawtext, p1, p2)
        rev = self.index.get_rev(node)
        if rev is not None:
            return rev

        if validatehash:
            self.checkhash(rawtext, node, p1=p1, p2=p2)

        return self.addrawrevision(
            rawtext,
            transaction,
            link,
            p1,
            p2,
            node,
            flags,
            cachedelta=cachedelta,
            deltacomputer=deltacomputer,
            sidedata=sidedata,
        )

    def addrawrevision(
        self,
        rawtext,
        transaction,
        link,
        p1,
        p2,
        node,
        flags,
        cachedelta=None,
        deltacomputer=None,
        sidedata=None,
    ):
        """add a raw revision with known flags, node and parents
        useful when reusing a revision not stored in this revlog (ex: received
        over wire, or read from an external bundle).
        """
        with self._writing(transaction):
            return self._addrevision(
                node,
                rawtext,
                transaction,
                link,
                p1,
                p2,
                flags,
                cachedelta,
                deltacomputer=deltacomputer,
                sidedata=sidedata,
            )

    def compress(self, data):
        """Generate a possibly-compressed representation of data."""
        if not data:
            return b'', data

        compressed = self._compressor.compress(data)

        if compressed:
            # The revlog compressor added the header in the returned data.
            return b'', compressed

        if data[0:1] == b'\0':
            return b'', data
        return b'u', data

    def decompress(self, data):
        """Decompress a revlog chunk.

        The chunk is expected to begin with a header identifying the
        format type so it can be routed to an appropriate decompressor.
        """
        if not data:
            return data

        # Revlogs are read much more frequently than they are written and many
        # chunks only take microseconds to decompress, so performance is
        # important here.
        #
        # We can make a few assumptions about revlogs:
        #
        # 1) the majority of chunks will be compressed (as opposed to inline
        #    raw data).
        # 2) decompressing *any* data will likely by at least 10x slower than
        #    returning raw inline data.
        # 3) we want to prioritize common and officially supported compression
        #    engines
        #
        # It follows that we want to optimize for "decompress compressed data
        # when encoded with common and officially supported compression engines"
        # case over "raw data" and "data encoded by less common or non-official
        # compression engines." That is why we have the inline lookup first
        # followed by the compengines lookup.
        #
        # According to `hg perfrevlogchunks`, this is ~0.5% faster for zlib
        # compressed chunks. And this matters for changelog and manifest reads.
        t = data[0:1]

        if t == b'x':
            try:
                return _zlibdecompress(data)
            except zlib.error as e:
                raise error.RevlogError(
                    _(b'revlog decompress error: %s')
                    % stringutil.forcebytestr(e)
                )
        # '\0' is more common than 'u' so it goes first.
        elif t == b'\0':
            return data
        elif t == b'u':
            return util.buffer(data, 1)

        compressor = self._get_decompressor(t)

        return compressor.decompress(data)

    def _addrevision(
        self,
        node,
        rawtext,
        transaction,
        link,
        p1,
        p2,
        flags,
        cachedelta,
        alwayscache=False,
        deltacomputer=None,
        sidedata=None,
    ):
        """internal function to add revisions to the log

        see addrevision for argument descriptions.

        note: "addrevision" takes non-raw text, "_addrevision" takes raw text.

        if "deltacomputer" is not provided or None, a defaultdeltacomputer will
        be used.

        invariants:
        - rawtext is optional (can be None); if not set, cachedelta must be set.
          if both are set, they must correspond to each other.
        """
        if node == self.nullid:
            raise error.RevlogError(
                _(b"%s: attempt to add null revision") % self.display_id
            )
        if (
            node == self.nodeconstants.wdirid
            or node in self.nodeconstants.wdirfilenodeids
        ):
            raise error.RevlogError(
                _(b"%s: attempt to add wdir revision") % self.display_id
            )
        if self._writinghandles is None:
            msg = b'adding revision outside `revlog._writing` context'
            raise error.ProgrammingError(msg)

        if self._inline:
            fh = self._writinghandles[0]
        else:
            fh = self._writinghandles[1]

        btext = [rawtext]

        curr = len(self)
        prev = curr - 1

        offset = self._get_data_offset(prev)

        if self._concurrencychecker:
            ifh, dfh, sdfh = self._writinghandles
            # XXX no checking for the sidedata file
            if self._inline:
                # offset is "as if" it were in the .d file, so we need to add on
                # the size of the entry metadata.
                self._concurrencychecker(
                    ifh, self._indexfile, offset + curr * self.index.entry_size
                )
            else:
                # Entries in the .i are a consistent size.
                self._concurrencychecker(
                    ifh, self._indexfile, curr * self.index.entry_size
                )
                self._concurrencychecker(dfh, self._datafile, offset)

        p1r, p2r = self.rev(p1), self.rev(p2)

        # full versions are inserted when the needed deltas
        # become comparable to the uncompressed text
        if rawtext is None:
            # need rawtext size, before changed by flag processors, which is
            # the non-raw size. use revlog explicitly to avoid filelog's extra
            # logic that might remove metadata size.
            textlen = mdiff.patchedsize(
                revlog.size(self, cachedelta[0]), cachedelta[1]
            )
        else:
            textlen = len(rawtext)

        if deltacomputer is None:
            deltacomputer = deltautil.deltacomputer(self)

        revinfo = revlogutils.revisioninfo(
            node,
            p1,
            p2,
            btext,
            textlen,
            cachedelta,
            flags,
        )

        deltainfo = deltacomputer.finddeltainfo(revinfo, fh)

        compression_mode = COMP_MODE_INLINE
        if self._docket is not None:
            default_comp = self._docket.default_compression_header
            r = deltautil.delta_compression(default_comp, deltainfo)
            compression_mode, deltainfo = r

        sidedata_compression_mode = COMP_MODE_INLINE
        if sidedata and self.hassidedata:
            sidedata_compression_mode = COMP_MODE_PLAIN
            serialized_sidedata = sidedatautil.serialize_sidedata(sidedata)
            sidedata_offset = self._docket.sidedata_end
            h, comp_sidedata = self.compress(serialized_sidedata)
            if (
                h != b'u'
                and comp_sidedata[0:1] != b'\0'
                and len(comp_sidedata) < len(serialized_sidedata)
            ):
                assert not h
                if (
                    comp_sidedata[0:1]
                    == self._docket.default_compression_header
                ):
                    sidedata_compression_mode = COMP_MODE_DEFAULT
                    serialized_sidedata = comp_sidedata
                else:
                    sidedata_compression_mode = COMP_MODE_INLINE
                    serialized_sidedata = comp_sidedata
        else:
            serialized_sidedata = b""
            # Don't store the offset if the sidedata is empty, that way
            # we can easily detect empty sidedata and they will be no different
            # than ones we manually add.
            sidedata_offset = 0

        e = revlogutils.entry(
            flags=flags,
            data_offset=offset,
            data_compressed_length=deltainfo.deltalen,
            data_uncompressed_length=textlen,
            data_compression_mode=compression_mode,
            data_delta_base=deltainfo.base,
            link_rev=link,
            parent_rev_1=p1r,
            parent_rev_2=p2r,
            node_id=node,
            sidedata_offset=sidedata_offset,
            sidedata_compressed_length=len(serialized_sidedata),
            sidedata_compression_mode=sidedata_compression_mode,
        )

        self.index.append(e)
        entry = self.index.entry_binary(curr)
        if curr == 0 and self._docket is None:
            header = self._format_flags | self._format_version
            header = self.index.pack_header(header)
            entry = header + entry
        self._writeentry(
            transaction,
            entry,
            deltainfo.data,
            link,
            offset,
            serialized_sidedata,
            sidedata_offset,
        )

        rawtext = btext[0]

        if alwayscache and rawtext is None:
            rawtext = deltacomputer.buildtext(revinfo, fh)

        if type(rawtext) == bytes:  # only accept immutable objects
            self._revisioncache = (node, curr, rawtext)
        self._chainbasecache[curr] = deltainfo.chainbase
        return curr

    def _get_data_offset(self, prev):
        """Returns the current offset in the (in-transaction) data file.
        Versions < 2 of the revlog can get this 0(1), revlog v2 needs a docket
        file to store that information: since sidedata can be rewritten to the
        end of the data file within a transaction, you can have cases where, for
        example, rev `n` does not have sidedata while rev `n - 1` does, leading
        to `n - 1`'s sidedata being written after `n`'s data.

        TODO cache this in a docket file before getting out of experimental."""
        if self._docket is None:
            return self.end(prev)
        else:
            return self._docket.data_end

    def _writeentry(
        self, transaction, entry, data, link, offset, sidedata, sidedata_offset
    ):
        # Files opened in a+ mode have inconsistent behavior on various
        # platforms. Windows requires that a file positioning call be made
        # when the file handle transitions between reads and writes. See
        # 3686fa2b8eee and the mixedfilemodewrapper in windows.py. On other
        # platforms, Python or the platform itself can be buggy. Some versions
        # of Solaris have been observed to not append at the end of the file
        # if the file was seeked to before the end. See issue4943 for more.
        #
        # We work around this issue by inserting a seek() before writing.
        # Note: This is likely not necessary on Python 3. However, because
        # the file handle is reused for reads and may be seeked there, we need
        # to be careful before changing this.
        if self._writinghandles is None:
            msg = b'adding revision outside `revlog._writing` context'
            raise error.ProgrammingError(msg)
        ifh, dfh, sdfh = self._writinghandles
        if self._docket is None:
            ifh.seek(0, os.SEEK_END)
        else:
            ifh.seek(self._docket.index_end, os.SEEK_SET)
        if dfh:
            if self._docket is None:
                dfh.seek(0, os.SEEK_END)
            else:
                dfh.seek(self._docket.data_end, os.SEEK_SET)
        if sdfh:
            sdfh.seek(self._docket.sidedata_end, os.SEEK_SET)

        curr = len(self) - 1
        if not self._inline:
            transaction.add(self._datafile, offset)
            if self._sidedatafile:
                transaction.add(self._sidedatafile, sidedata_offset)
            transaction.add(self._indexfile, curr * len(entry))
            if data[0]:
                dfh.write(data[0])
            dfh.write(data[1])
            if sidedata:
                sdfh.write(sidedata)
            ifh.write(entry)
        else:
            offset += curr * self.index.entry_size
            transaction.add(self._indexfile, offset)
            ifh.write(entry)
            ifh.write(data[0])
            ifh.write(data[1])
            assert not sidedata
            self._enforceinlinesize(transaction)
        if self._docket is not None:
            # revlog-v2 always has 3 writing handles, help Pytype
            assert self._writinghandles[2] is not None
            self._docket.index_end = self._writinghandles[0].tell()
            self._docket.data_end = self._writinghandles[1].tell()
            self._docket.sidedata_end = self._writinghandles[2].tell()

        nodemaputil.setup_persistent_nodemap(transaction, self)

    def addgroup(
        self,
        deltas,
        linkmapper,
        transaction,
        alwayscache=False,
        addrevisioncb=None,
        duplicaterevisioncb=None,
    ):
        """
        add a delta group

        given a set of deltas, add them to the revision log. the
        first delta is against its parent, which should be in our
        log, the rest are against the previous delta.

        If ``addrevisioncb`` is defined, it will be called with arguments of
        this revlog and the node that was added.
        """

        if self._adding_group:
            raise error.ProgrammingError(b'cannot nest addgroup() calls')

        self._adding_group = True
        empty = True
        try:
            with self._writing(transaction):
                deltacomputer = deltautil.deltacomputer(self)
                # loop through our set of deltas
                for data in deltas:
                    (
                        node,
                        p1,
                        p2,
                        linknode,
                        deltabase,
                        delta,
                        flags,
                        sidedata,
                    ) = data
                    link = linkmapper(linknode)
                    flags = flags or REVIDX_DEFAULT_FLAGS

                    rev = self.index.get_rev(node)
                    if rev is not None:
                        # this can happen if two branches make the same change
                        self._nodeduplicatecallback(transaction, rev)
                        if duplicaterevisioncb:
                            duplicaterevisioncb(self, rev)
                        empty = False
                        continue

                    for p in (p1, p2):
                        if not self.index.has_node(p):
                            raise error.LookupError(
                                p, self.radix, _(b'unknown parent')
                            )

                    if not self.index.has_node(deltabase):
                        raise error.LookupError(
                            deltabase, self.display_id, _(b'unknown delta base')
                        )

                    baserev = self.rev(deltabase)

                    if baserev != nullrev and self.iscensored(baserev):
                        # if base is censored, delta must be full replacement in a
                        # single patch operation
                        hlen = struct.calcsize(b">lll")
                        oldlen = self.rawsize(baserev)
                        newlen = len(delta) - hlen
                        if delta[:hlen] != mdiff.replacediffheader(
                            oldlen, newlen
                        ):
                            raise error.CensoredBaseError(
                                self.display_id, self.node(baserev)
                            )

                    if not flags and self._peek_iscensored(baserev, delta):
                        flags |= REVIDX_ISCENSORED

                    # We assume consumers of addrevisioncb will want to retrieve
                    # the added revision, which will require a call to
                    # revision(). revision() will fast path if there is a cache
                    # hit. So, we tell _addrevision() to always cache in this case.
                    # We're only using addgroup() in the context of changegroup
                    # generation so the revision data can always be handled as raw
                    # by the flagprocessor.
                    rev = self._addrevision(
                        node,
                        None,
                        transaction,
                        link,
                        p1,
                        p2,
                        flags,
                        (baserev, delta),
                        alwayscache=alwayscache,
                        deltacomputer=deltacomputer,
                        sidedata=sidedata,
                    )

                    if addrevisioncb:
                        addrevisioncb(self, rev)
                    empty = False
        finally:
            self._adding_group = False
        return not empty

    def iscensored(self, rev):
        """Check if a file revision is censored."""
        if not self._censorable:
            return False

        return self.flags(rev) & REVIDX_ISCENSORED

    def _peek_iscensored(self, baserev, delta):
        """Quickly check if a delta produces a censored revision."""
        if not self._censorable:
            return False

        return storageutil.deltaiscensored(delta, baserev, self.rawsize)

    def getstrippoint(self, minlink):
        """find the minimum rev that must be stripped to strip the linkrev

        Returns a tuple containing the minimum rev and a set of all revs that
        have linkrevs that will be broken by this strip.
        """
        return storageutil.resolvestripinfo(
            minlink,
            len(self) - 1,
            self.headrevs(),
            self.linkrev,
            self.parentrevs,
        )

    def strip(self, minlink, transaction):
        """truncate the revlog on the first revision with a linkrev >= minlink

        This function is called when we're stripping revision minlink and
        its descendants from the repository.

        We have to remove all revisions with linkrev >= minlink, because
        the equivalent changelog revisions will be renumbered after the
        strip.

        So we truncate the revlog on the first of these revisions, and
        trust that the caller has saved the revisions that shouldn't be
        removed and that it'll re-add them after this truncation.
        """
        if len(self) == 0:
            return

        rev, _ = self.getstrippoint(minlink)
        if rev == len(self):
            return

        # first truncate the files on disk
        data_end = self.start(rev)
        if not self._inline:
            transaction.add(self._datafile, data_end)
            end = rev * self.index.entry_size
        else:
            end = data_end + (rev * self.index.entry_size)

        if self._sidedatafile:
            sidedata_end = self.sidedata_cut_off(rev)
            transaction.add(self._sidedatafile, sidedata_end)

        transaction.add(self._indexfile, end)
        if self._docket is not None:
            # XXX we could, leverage the docket while stripping. However it is
            # not powerfull enough at the time of this comment
            self._docket.index_end = end
            self._docket.data_end = data_end
            self._docket.sidedata_end = sidedata_end
            self._docket.write(transaction, stripping=True)

        # then reset internal state in memory to forget those revisions
        self._revisioncache = None
        self._chaininfocache = util.lrucachedict(500)
        self._segmentfile.clear_cache()
        self._segmentfile_sidedata.clear_cache()

        del self.index[rev:-1]

    def checksize(self):
        """Check size of index and data files

        return a (dd, di) tuple.
        - dd: extra bytes for the "data" file
        - di: extra bytes for the "index" file

        A healthy revlog will return (0, 0).
        """
        expected = 0
        if len(self):
            expected = max(0, self.end(len(self) - 1))

        try:
            with self._datafp() as f:
                f.seek(0, io.SEEK_END)
                actual = f.tell()
            dd = actual - expected
        except IOError as inst:
            if inst.errno != errno.ENOENT:
                raise
            dd = 0

        try:
            f = self.opener(self._indexfile)
            f.seek(0, io.SEEK_END)
            actual = f.tell()
            f.close()
            s = self.index.entry_size
            i = max(0, actual // s)
            di = actual - (i * s)
            if self._inline:
                databytes = 0
                for r in self:
                    databytes += max(0, self.length(r))
                dd = 0
                di = actual - len(self) * s - databytes
        except IOError as inst:
            if inst.errno != errno.ENOENT:
                raise
            di = 0

        return (dd, di)

    def files(self):
        res = [self._indexfile]
        if self._docket_file is None:
            if not self._inline:
                res.append(self._datafile)
        else:
            res.append(self._docket_file)
            res.extend(self._docket.old_index_filepaths(include_empty=False))
            if self._docket.data_end:
                res.append(self._datafile)
            res.extend(self._docket.old_data_filepaths(include_empty=False))
            if self._docket.sidedata_end:
                res.append(self._sidedatafile)
            res.extend(self._docket.old_sidedata_filepaths(include_empty=False))
        return res

    def emitrevisions(
        self,
        nodes,
        nodesorder=None,
        revisiondata=False,
        assumehaveparentrevisions=False,
        deltamode=repository.CG_DELTAMODE_STD,
        sidedata_helpers=None,
    ):
        if nodesorder not in (b'nodes', b'storage', b'linear', None):
            raise error.ProgrammingError(
                b'unhandled value for nodesorder: %s' % nodesorder
            )

        if nodesorder is None and not self._generaldelta:
            nodesorder = b'storage'

        if (
            not self._storedeltachains
            and deltamode != repository.CG_DELTAMODE_PREV
        ):
            deltamode = repository.CG_DELTAMODE_FULL

        return storageutil.emitrevisions(
            self,
            nodes,
            nodesorder,
            revlogrevisiondelta,
            deltaparentfn=self.deltaparent,
            candeltafn=self.candelta,
            rawsizefn=self.rawsize,
            revdifffn=self.revdiff,
            flagsfn=self.flags,
            deltamode=deltamode,
            revisiondata=revisiondata,
            assumehaveparentrevisions=assumehaveparentrevisions,
            sidedata_helpers=sidedata_helpers,
        )

    DELTAREUSEALWAYS = b'always'
    DELTAREUSESAMEREVS = b'samerevs'
    DELTAREUSENEVER = b'never'

    DELTAREUSEFULLADD = b'fulladd'

    DELTAREUSEALL = {b'always', b'samerevs', b'never', b'fulladd'}

    def clone(
        self,
        tr,
        destrevlog,
        addrevisioncb=None,
        deltareuse=DELTAREUSESAMEREVS,
        forcedeltabothparents=None,
        sidedata_helpers=None,
    ):
        """Copy this revlog to another, possibly with format changes.

        The destination revlog will contain the same revisions and nodes.
        However, it may not be bit-for-bit identical due to e.g. delta encoding
        differences.

        The ``deltareuse`` argument control how deltas from the existing revlog
        are preserved in the destination revlog. The argument can have the
        following values:

        DELTAREUSEALWAYS
           Deltas will always be reused (if possible), even if the destination
           revlog would not select the same revisions for the delta. This is the
           fastest mode of operation.
        DELTAREUSESAMEREVS
           Deltas will be reused if the destination revlog would pick the same
           revisions for the delta. This mode strikes a balance between speed
           and optimization.
        DELTAREUSENEVER
           Deltas will never be reused. This is the slowest mode of execution.
           This mode can be used to recompute deltas (e.g. if the diff/delta
           algorithm changes).
        DELTAREUSEFULLADD
           Revision will be re-added as if their were new content. This is
           slower than DELTAREUSEALWAYS but allow more mechanism to kicks in.
           eg: large file detection and handling.

        Delta computation can be slow, so the choice of delta reuse policy can
        significantly affect run time.

        The default policy (``DELTAREUSESAMEREVS``) strikes a balance between
        two extremes. Deltas will be reused if they are appropriate. But if the
        delta could choose a better revision, it will do so. This means if you
        are converting a non-generaldelta revlog to a generaldelta revlog,
        deltas will be recomputed if the delta's parent isn't a parent of the
        revision.

        In addition to the delta policy, the ``forcedeltabothparents``
        argument controls whether to force compute deltas against both parents
        for merges. By default, the current default is used.

        See `revlogutil.sidedata.get_sidedata_helpers` for the doc on
        `sidedata_helpers`.
        """
        if deltareuse not in self.DELTAREUSEALL:
            raise ValueError(
                _(b'value for deltareuse invalid: %s') % deltareuse
            )

        if len(destrevlog):
            raise ValueError(_(b'destination revlog is not empty'))

        if getattr(self, 'filteredrevs', None):
            raise ValueError(_(b'source revlog has filtered revisions'))
        if getattr(destrevlog, 'filteredrevs', None):
            raise ValueError(_(b'destination revlog has filtered revisions'))

        # lazydelta and lazydeltabase controls whether to reuse a cached delta,
        # if possible.
        oldlazydelta = destrevlog._lazydelta
        oldlazydeltabase = destrevlog._lazydeltabase
        oldamd = destrevlog._deltabothparents

        try:
            if deltareuse == self.DELTAREUSEALWAYS:
                destrevlog._lazydeltabase = True
                destrevlog._lazydelta = True
            elif deltareuse == self.DELTAREUSESAMEREVS:
                destrevlog._lazydeltabase = False
                destrevlog._lazydelta = True
            elif deltareuse == self.DELTAREUSENEVER:
                destrevlog._lazydeltabase = False
                destrevlog._lazydelta = False

            destrevlog._deltabothparents = forcedeltabothparents or oldamd

            self._clone(
                tr,
                destrevlog,
                addrevisioncb,
                deltareuse,
                forcedeltabothparents,
                sidedata_helpers,
            )

        finally:
            destrevlog._lazydelta = oldlazydelta
            destrevlog._lazydeltabase = oldlazydeltabase
            destrevlog._deltabothparents = oldamd

    def _clone(
        self,
        tr,
        destrevlog,
        addrevisioncb,
        deltareuse,
        forcedeltabothparents,
        sidedata_helpers,
    ):
        """perform the core duty of `revlog.clone` after parameter processing"""
        deltacomputer = deltautil.deltacomputer(destrevlog)
        index = self.index
        for rev in self:
            entry = index[rev]

            # Some classes override linkrev to take filtered revs into
            # account. Use raw entry from index.
            flags = entry[0] & 0xFFFF
            linkrev = entry[4]
            p1 = index[entry[5]][7]
            p2 = index[entry[6]][7]
            node = entry[7]

            # (Possibly) reuse the delta from the revlog if allowed and
            # the revlog chunk is a delta.
            cachedelta = None
            rawtext = None
            if deltareuse == self.DELTAREUSEFULLADD:
                text = self._revisiondata(rev)
                sidedata = self.sidedata(rev)

                if sidedata_helpers is not None:
                    (sidedata, new_flags) = sidedatautil.run_sidedata_helpers(
                        self, sidedata_helpers, sidedata, rev
                    )
                    flags = flags | new_flags[0] & ~new_flags[1]

                destrevlog.addrevision(
                    text,
                    tr,
                    linkrev,
                    p1,
                    p2,
                    cachedelta=cachedelta,
                    node=node,
                    flags=flags,
                    deltacomputer=deltacomputer,
                    sidedata=sidedata,
                )
            else:
                if destrevlog._lazydelta:
                    dp = self.deltaparent(rev)
                    if dp != nullrev:
                        cachedelta = (dp, bytes(self._chunk(rev)))

                sidedata = None
                if not cachedelta:
                    rawtext = self._revisiondata(rev)
                    sidedata = self.sidedata(rev)
                if sidedata is None:
                    sidedata = self.sidedata(rev)

                if sidedata_helpers is not None:
                    (sidedata, new_flags) = sidedatautil.run_sidedata_helpers(
                        self, sidedata_helpers, sidedata, rev
                    )
                    flags = flags | new_flags[0] & ~new_flags[1]

                with destrevlog._writing(tr):
                    destrevlog._addrevision(
                        node,
                        rawtext,
                        tr,
                        linkrev,
                        p1,
                        p2,
                        flags,
                        cachedelta,
                        deltacomputer=deltacomputer,
                        sidedata=sidedata,
                    )

            if addrevisioncb:
                addrevisioncb(self, rev, node)

    def censorrevision(self, tr, censornode, tombstone=b''):
        if self._format_version == REVLOGV0:
            raise error.RevlogError(
                _(b'cannot censor with version %d revlogs')
                % self._format_version
            )
        elif self._format_version == REVLOGV1:
            rewrite.v1_censor(self, tr, censornode, tombstone)
        else:
            rewrite.v2_censor(self, tr, censornode, tombstone)

    def verifyintegrity(self, state):
        """Verifies the integrity of the revlog.

        Yields ``revlogproblem`` instances describing problems that are
        found.
        """
        dd, di = self.checksize()
        if dd:
            yield revlogproblem(error=_(b'data length off by %d bytes') % dd)
        if di:
            yield revlogproblem(error=_(b'index contains %d extra bytes') % di)

        version = self._format_version

        # The verifier tells us what version revlog we should be.
        if version != state[b'expectedversion']:
            yield revlogproblem(
                warning=_(b"warning: '%s' uses revlog format %d; expected %d")
                % (self.display_id, version, state[b'expectedversion'])
            )

        state[b'skipread'] = set()
        state[b'safe_renamed'] = set()

        for rev in self:
            node = self.node(rev)

            # Verify contents. 4 cases to care about:
            #
            #   common: the most common case
            #   rename: with a rename
            #   meta: file content starts with b'\1\n', the metadata
            #         header defined in filelog.py, but without a rename
            #   ext: content stored externally
            #
            # More formally, their differences are shown below:
            #
            #                       | common | rename | meta  | ext
            #  -------------------------------------------------------
            #   flags()             | 0      | 0      | 0     | not 0
            #   renamed()           | False  | True   | False | ?
            #   rawtext[0:2]=='\1\n'| False  | True   | True  | ?
            #
            # "rawtext" means the raw text stored in revlog data, which
            # could be retrieved by "rawdata(rev)". "text"
            # mentioned below is "revision(rev)".
            #
            # There are 3 different lengths stored physically:
            #  1. L1: rawsize, stored in revlog index
            #  2. L2: len(rawtext), stored in revlog data
            #  3. L3: len(text), stored in revlog data if flags==0, or
            #     possibly somewhere else if flags!=0
            #
            # L1 should be equal to L2. L3 could be different from them.
            # "text" may or may not affect commit hash depending on flag
            # processors (see flagutil.addflagprocessor).
            #
            #              | common  | rename | meta  | ext
            # -------------------------------------------------
            #    rawsize() | L1      | L1     | L1    | L1
            #       size() | L1      | L2-LM  | L1(*) | L1 (?)
            # len(rawtext) | L2      | L2     | L2    | L2
            #    len(text) | L2      | L2     | L2    | L3
            #  len(read()) | L2      | L2-LM  | L2-LM | L3 (?)
            #
            # LM:  length of metadata, depending on rawtext
            # (*): not ideal, see comment in filelog.size
            # (?): could be "- len(meta)" if the resolved content has
            #      rename metadata
            #
            # Checks needed to be done:
            #  1. length check: L1 == L2, in all cases.
            #  2. hash check: depending on flag processor, we may need to
            #     use either "text" (external), or "rawtext" (in revlog).

            try:
                skipflags = state.get(b'skipflags', 0)
                if skipflags:
                    skipflags &= self.flags(rev)

                _verify_revision(self, skipflags, state, node)

                l1 = self.rawsize(rev)
                l2 = len(self.rawdata(node))

                if l1 != l2:
                    yield revlogproblem(
                        error=_(b'unpacked size is %d, %d expected') % (l2, l1),
                        node=node,
                    )

            except error.CensoredNodeError:
                if state[b'erroroncensored']:
                    yield revlogproblem(
                        error=_(b'censored file data'), node=node
                    )
                    state[b'skipread'].add(node)
            except Exception as e:
                yield revlogproblem(
                    error=_(b'unpacking %s: %s')
                    % (short(node), stringutil.forcebytestr(e)),
                    node=node,
                )
                state[b'skipread'].add(node)

    def storageinfo(
        self,
        exclusivefiles=False,
        sharedfiles=False,
        revisionscount=False,
        trackedsize=False,
        storedsize=False,
    ):
        d = {}

        if exclusivefiles:
            d[b'exclusivefiles'] = [(self.opener, self._indexfile)]
            if not self._inline:
                d[b'exclusivefiles'].append((self.opener, self._datafile))

        if sharedfiles:
            d[b'sharedfiles'] = []

        if revisionscount:
            d[b'revisionscount'] = len(self)

        if trackedsize:
            d[b'trackedsize'] = sum(map(self.rawsize, iter(self)))

        if storedsize:
            d[b'storedsize'] = sum(
                self.opener.stat(path).st_size for path in self.files()
            )

        return d

    def rewrite_sidedata(self, transaction, helpers, startrev, endrev):
        if not self.hassidedata:
            return
        # revlog formats with sidedata support does not support inline
        assert not self._inline
        if not helpers[1] and not helpers[2]:
            # Nothing to generate or remove
            return

        new_entries = []
        # append the new sidedata
        with self._writing(transaction):
            ifh, dfh, sdfh = self._writinghandles
            dfh.seek(self._docket.sidedata_end, os.SEEK_SET)

            current_offset = sdfh.tell()
            for rev in range(startrev, endrev + 1):
                entry = self.index[rev]
                new_sidedata, flags = sidedatautil.run_sidedata_helpers(
                    store=self,
                    sidedata_helpers=helpers,
                    sidedata={},
                    rev=rev,
                )

                serialized_sidedata = sidedatautil.serialize_sidedata(
                    new_sidedata
                )

                sidedata_compression_mode = COMP_MODE_INLINE
                if serialized_sidedata and self.hassidedata:
                    sidedata_compression_mode = COMP_MODE_PLAIN
                    h, comp_sidedata = self.compress(serialized_sidedata)
                    if (
                        h != b'u'
                        and comp_sidedata[0] != b'\0'
                        and len(comp_sidedata) < len(serialized_sidedata)
                    ):
                        assert not h
                        if (
                            comp_sidedata[0]
                            == self._docket.default_compression_header
                        ):
                            sidedata_compression_mode = COMP_MODE_DEFAULT
                            serialized_sidedata = comp_sidedata
                        else:
                            sidedata_compression_mode = COMP_MODE_INLINE
                            serialized_sidedata = comp_sidedata
                if entry[8] != 0 or entry[9] != 0:
                    # rewriting entries that already have sidedata is not
                    # supported yet, because it introduces garbage data in the
                    # revlog.
                    msg = b"rewriting existing sidedata is not supported yet"
                    raise error.Abort(msg)

                # Apply (potential) flags to add and to remove after running
                # the sidedata helpers
                new_offset_flags = entry[0] | flags[0] & ~flags[1]
                entry_update = (
                    current_offset,
                    len(serialized_sidedata),
                    new_offset_flags,
                    sidedata_compression_mode,
                )

                # the sidedata computation might have move the file cursors around
                sdfh.seek(current_offset, os.SEEK_SET)
                sdfh.write(serialized_sidedata)
                new_entries.append(entry_update)
                current_offset += len(serialized_sidedata)
                self._docket.sidedata_end = sdfh.tell()

            # rewrite the new index entries
            ifh.seek(startrev * self.index.entry_size)
            for i, e in enumerate(new_entries):
                rev = startrev + i
                self.index.replace_sidedata_info(rev, *e)
                packed = self.index.entry_binary(rev)
                if rev == 0 and self._docket is None:
                    header = self._format_flags | self._format_version
                    header = self.index.pack_header(header)
                    packed = header + packed
                ifh.write(packed)
