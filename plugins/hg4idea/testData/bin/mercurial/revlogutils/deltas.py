# revlogdeltas.py - Logic around delta computation for revlog
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
# Copyright 2018 Octobus <contact@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""Helper class to compute deltas stored inside revlogs"""


import abc
import collections
import struct

# import stuff from node for others to import from revlog
from ..node import nullrev
from ..i18n import _

from .constants import (
    COMP_MODE_DEFAULT,
    COMP_MODE_INLINE,
    COMP_MODE_PLAIN,
    DELTA_BASE_REUSE_FORCE,
    DELTA_BASE_REUSE_NO,
    KIND_CHANGELOG,
    KIND_FILELOG,
    KIND_MANIFESTLOG,
    REVIDX_ISCENSORED,
    REVIDX_RAWTEXT_CHANGING_FLAGS,
)

from ..thirdparty import attr

from .. import (
    error,
    mdiff,
    util,
)

from . import flagutil

# maximum <delta-chain-data>/<revision-text-length> ratio
LIMIT_DELTA2TEXT = 2


class _testrevlog:
    """minimalist fake revlog to use in doctests"""

    def __init__(self, data, density=0.5, mingap=0, snapshot=()):
        """data is an list of revision payload boundaries"""
        from .. import revlog

        self._data = data
        self.data_config = revlog.DataConfig()
        self.data_config.sr_density_threshold = density
        self.data_config.sr_min_gap_size = mingap
        self.delta_config = revlog.DeltaConfig()
        self.feature_config = revlog.FeatureConfig()
        self._snapshot = set(snapshot)
        self.index = None

    def start(self, rev):
        if rev == nullrev:
            return 0
        if rev == 0:
            return 0
        return self._data[rev - 1]

    def end(self, rev):
        if rev == nullrev:
            return 0
        return self._data[rev]

    def length(self, rev):
        return self.end(rev) - self.start(rev)

    def __len__(self):
        return len(self._data)

    def issnapshot(self, rev):
        if rev == nullrev:
            return True
        return rev in self._snapshot


def slicechunk(revlog, revs, targetsize=None):
    """slice revs to reduce the amount of unrelated data to be read from disk.

    ``revs`` is sliced into groups that should be read in one time.
    Assume that revs are sorted.

    The initial chunk is sliced until the overall density (payload/chunks-span
    ratio) is above `revlog.data_config.sr_density_threshold`. No gap smaller
    than `revlog.data_config.sr_min_gap_size` is skipped.

    If `targetsize` is set, no chunk larger than `targetsize` will be yield.
    For consistency with other slicing choice, this limit won't go lower than
    `revlog.data_config.sr_min_gap_size`.

    If individual revisions chunk are larger than this limit, they will still
    be raised individually.

    >>> data = [
    ...  5,  #00 (5)
    ...  10, #01 (5)
    ...  12, #02 (2)
    ...  12, #03 (empty)
    ...  27, #04 (15)
    ...  31, #05 (4)
    ...  31, #06 (empty)
    ...  42, #07 (11)
    ...  47, #08 (5)
    ...  47, #09 (empty)
    ...  48, #10 (1)
    ...  51, #11 (3)
    ...  74, #12 (23)
    ...  85, #13 (11)
    ...  86, #14 (1)
    ...  91, #15 (5)
    ... ]
    >>> revlog = _testrevlog(data, snapshot=range(16))

    >>> list(slicechunk(revlog, list(range(16))))
    [[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]]
    >>> list(slicechunk(revlog, [0, 15]))
    [[0], [15]]
    >>> list(slicechunk(revlog, [0, 11, 15]))
    [[0], [11], [15]]
    >>> list(slicechunk(revlog, [0, 11, 13, 15]))
    [[0], [11, 13, 15]]
    >>> list(slicechunk(revlog, [1, 2, 3, 5, 8, 10, 11, 14]))
    [[1, 2], [5, 8, 10, 11], [14]]

    Slicing with a maximum chunk size
    >>> list(slicechunk(revlog, [0, 11, 13, 15], targetsize=15))
    [[0], [11], [13], [15]]
    >>> list(slicechunk(revlog, [0, 11, 13, 15], targetsize=20))
    [[0], [11], [13, 15]]

    Slicing involving nullrev
    >>> list(slicechunk(revlog, [-1, 0, 11, 13, 15], targetsize=20))
    [[-1, 0], [11], [13, 15]]
    >>> list(slicechunk(revlog, [-1, 13, 15], targetsize=5))
    [[-1], [13], [15]]
    """
    if targetsize is not None:
        targetsize = max(targetsize, revlog.data_config.sr_min_gap_size)
    # targetsize should not be specified when evaluating delta candidates:
    # * targetsize is used to ensure we stay within specification when reading,
    densityslicing = getattr(revlog.index, 'slicechunktodensity', None)
    if densityslicing is None:
        densityslicing = lambda x, y, z: _slicechunktodensity(revlog, x, y, z)
    for chunk in densityslicing(
        revs,
        revlog.data_config.sr_density_threshold,
        revlog.data_config.sr_min_gap_size,
    ):
        for subchunk in _slicechunktosize(revlog, chunk, targetsize):
            yield subchunk


def _slicechunktosize(revlog, revs, targetsize=None):
    """slice revs to match the target size

    This is intended to be used on chunk that density slicing selected by that
    are still too large compared to the read garantee of revlog. This might
    happens when "minimal gap size" interrupted the slicing or when chain are
    built in a way that create large blocks next to each other.

    >>> data = [
    ...  3,  #0 (3)
    ...  5,  #1 (2)
    ...  6,  #2 (1)
    ...  8,  #3 (2)
    ...  8,  #4 (empty)
    ...  11, #5 (3)
    ...  12, #6 (1)
    ...  13, #7 (1)
    ...  14, #8 (1)
    ... ]

    == All snapshots cases ==
    >>> revlog = _testrevlog(data, snapshot=range(9))

    Cases where chunk is already small enough
    >>> list(_slicechunktosize(revlog, [0], 3))
    [[0]]
    >>> list(_slicechunktosize(revlog, [6, 7], 3))
    [[6, 7]]
    >>> list(_slicechunktosize(revlog, [0], None))
    [[0]]
    >>> list(_slicechunktosize(revlog, [6, 7], None))
    [[6, 7]]

    cases where we need actual slicing
    >>> list(_slicechunktosize(revlog, [0, 1], 3))
    [[0], [1]]
    >>> list(_slicechunktosize(revlog, [1, 3], 3))
    [[1], [3]]
    >>> list(_slicechunktosize(revlog, [1, 2, 3], 3))
    [[1, 2], [3]]
    >>> list(_slicechunktosize(revlog, [3, 5], 3))
    [[3], [5]]
    >>> list(_slicechunktosize(revlog, [3, 4, 5], 3))
    [[3], [5]]
    >>> list(_slicechunktosize(revlog, [5, 6, 7, 8], 3))
    [[5], [6, 7, 8]]
    >>> list(_slicechunktosize(revlog, [0, 1, 2, 3, 4, 5, 6, 7, 8], 3))
    [[0], [1, 2], [3], [5], [6, 7, 8]]

    Case with too large individual chunk (must return valid chunk)
    >>> list(_slicechunktosize(revlog, [0, 1], 2))
    [[0], [1]]
    >>> list(_slicechunktosize(revlog, [1, 3], 1))
    [[1], [3]]
    >>> list(_slicechunktosize(revlog, [3, 4, 5], 2))
    [[3], [5]]

    == No Snapshot cases ==
    >>> revlog = _testrevlog(data)

    Cases where chunk is already small enough
    >>> list(_slicechunktosize(revlog, [0], 3))
    [[0]]
    >>> list(_slicechunktosize(revlog, [6, 7], 3))
    [[6, 7]]
    >>> list(_slicechunktosize(revlog, [0], None))
    [[0]]
    >>> list(_slicechunktosize(revlog, [6, 7], None))
    [[6, 7]]

    cases where we need actual slicing
    >>> list(_slicechunktosize(revlog, [0, 1], 3))
    [[0], [1]]
    >>> list(_slicechunktosize(revlog, [1, 3], 3))
    [[1], [3]]
    >>> list(_slicechunktosize(revlog, [1, 2, 3], 3))
    [[1], [2, 3]]
    >>> list(_slicechunktosize(revlog, [3, 5], 3))
    [[3], [5]]
    >>> list(_slicechunktosize(revlog, [3, 4, 5], 3))
    [[3], [4, 5]]
    >>> list(_slicechunktosize(revlog, [5, 6, 7, 8], 3))
    [[5], [6, 7, 8]]
    >>> list(_slicechunktosize(revlog, [0, 1, 2, 3, 4, 5, 6, 7, 8], 3))
    [[0], [1, 2], [3], [5], [6, 7, 8]]

    Case with too large individual chunk (must return valid chunk)
    >>> list(_slicechunktosize(revlog, [0, 1], 2))
    [[0], [1]]
    >>> list(_slicechunktosize(revlog, [1, 3], 1))
    [[1], [3]]
    >>> list(_slicechunktosize(revlog, [3, 4, 5], 2))
    [[3], [5]]

    == mixed case ==
    >>> revlog = _testrevlog(data, snapshot=[0, 1, 2])
    >>> list(_slicechunktosize(revlog, list(range(9)), 5))
    [[0, 1], [2], [3, 4, 5], [6, 7, 8]]
    """
    assert targetsize is None or 0 <= targetsize
    startdata = revlog.start(revs[0])
    enddata = revlog.end(revs[-1])
    fullspan = enddata - startdata
    if targetsize is None or fullspan <= targetsize:
        yield revs
        return

    startrevidx = 0
    endrevidx = 1
    iterrevs = enumerate(revs)
    next(iterrevs)  # skip first rev.
    # first step: get snapshots out of the way
    for idx, r in iterrevs:
        span = revlog.end(r) - startdata
        snapshot = revlog.issnapshot(r)
        if span <= targetsize and snapshot:
            endrevidx = idx + 1
        else:
            chunk = _trimchunk(revlog, revs, startrevidx, endrevidx)
            if chunk:
                yield chunk
            startrevidx = idx
            startdata = revlog.start(r)
            endrevidx = idx + 1
        if not snapshot:
            break

    # for the others, we use binary slicing to quickly converge toward valid
    # chunks (otherwise, we might end up looking for start/end of many
    # revisions). This logic is not looking for the perfect slicing point, it
    # focuses on quickly converging toward valid chunks.
    nbitem = len(revs)
    while (enddata - startdata) > targetsize:
        endrevidx = nbitem
        if nbitem - startrevidx <= 1:
            break  # protect against individual chunk larger than limit
        localenddata = revlog.end(revs[endrevidx - 1])
        span = localenddata - startdata
        while span > targetsize:
            if endrevidx - startrevidx <= 1:
                break  # protect against individual chunk larger than limit
            endrevidx -= (endrevidx - startrevidx) // 2
            localenddata = revlog.end(revs[endrevidx - 1])
            span = localenddata - startdata
        chunk = _trimchunk(revlog, revs, startrevidx, endrevidx)
        if chunk:
            yield chunk
        startrevidx = endrevidx
        startdata = revlog.start(revs[startrevidx])

    chunk = _trimchunk(revlog, revs, startrevidx)
    if chunk:
        yield chunk


def _slicechunktodensity(revlog, revs, targetdensity=0.5, mingapsize=0):
    """slice revs to reduce the amount of unrelated data to be read from disk.

    ``revs`` is sliced into groups that should be read in one time.
    Assume that revs are sorted.

    The initial chunk is sliced until the overall density (payload/chunks-span
    ratio) is above `targetdensity`. No gap smaller than `mingapsize` is
    skipped.

    >>> revlog = _testrevlog([
    ...  5,  #00 (5)
    ...  10, #01 (5)
    ...  12, #02 (2)
    ...  12, #03 (empty)
    ...  27, #04 (15)
    ...  31, #05 (4)
    ...  31, #06 (empty)
    ...  42, #07 (11)
    ...  47, #08 (5)
    ...  47, #09 (empty)
    ...  48, #10 (1)
    ...  51, #11 (3)
    ...  74, #12 (23)
    ...  85, #13 (11)
    ...  86, #14 (1)
    ...  91, #15 (5)
    ... ])

    >>> list(_slicechunktodensity(revlog, list(range(16))))
    [[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]]
    >>> list(_slicechunktodensity(revlog, [0, 15]))
    [[0], [15]]
    >>> list(_slicechunktodensity(revlog, [0, 11, 15]))
    [[0], [11], [15]]
    >>> list(_slicechunktodensity(revlog, [0, 11, 13, 15]))
    [[0], [11, 13, 15]]
    >>> list(_slicechunktodensity(revlog, [1, 2, 3, 5, 8, 10, 11, 14]))
    [[1, 2], [5, 8, 10, 11], [14]]
    >>> list(_slicechunktodensity(revlog, [1, 2, 3, 5, 8, 10, 11, 14],
    ...                           mingapsize=20))
    [[1, 2, 3, 5, 8, 10, 11], [14]]
    >>> list(_slicechunktodensity(revlog, [1, 2, 3, 5, 8, 10, 11, 14],
    ...                           targetdensity=0.95))
    [[1, 2], [5], [8, 10, 11], [14]]
    >>> list(_slicechunktodensity(revlog, [1, 2, 3, 5, 8, 10, 11, 14],
    ...                           targetdensity=0.95, mingapsize=12))
    [[1, 2], [5, 8, 10, 11], [14]]
    """
    start = revlog.start
    length = revlog.length

    if len(revs) <= 1:
        yield revs
        return

    deltachainspan = segmentspan(revlog, revs)

    if deltachainspan < mingapsize:
        yield revs
        return

    readdata = deltachainspan
    chainpayload = sum(length(r) for r in revs)

    if deltachainspan:
        density = chainpayload / float(deltachainspan)
    else:
        density = 1.0

    if density >= targetdensity:
        yield revs
        return

    # Store the gaps in a heap to have them sorted by decreasing size
    gaps = []
    prevend = None
    for i, rev in enumerate(revs):
        revstart = start(rev)
        revlen = length(rev)

        # Skip empty revisions to form larger holes
        if revlen == 0:
            continue

        if prevend is not None:
            gapsize = revstart - prevend
            # only consider holes that are large enough
            if gapsize > mingapsize:
                gaps.append((gapsize, i))

        prevend = revstart + revlen
    # sort the gaps to pop them from largest to small
    gaps.sort()

    # Collect the indices of the largest holes until the density is acceptable
    selected = []
    while gaps and density < targetdensity:
        gapsize, gapidx = gaps.pop()

        selected.append(gapidx)

        # the gap sizes are stored as negatives to be sorted decreasingly
        # by the heap
        readdata -= gapsize
        if readdata > 0:
            density = chainpayload / float(readdata)
        else:
            density = 1.0
    selected.sort()

    # Cut the revs at collected indices
    previdx = 0
    for idx in selected:

        chunk = _trimchunk(revlog, revs, previdx, idx)
        if chunk:
            yield chunk

        previdx = idx

    chunk = _trimchunk(revlog, revs, previdx)
    if chunk:
        yield chunk


def _trimchunk(revlog, revs, startidx, endidx=None):
    """returns revs[startidx:endidx] without empty trailing revs

    Doctest Setup
    >>> revlog = _testrevlog([
    ...  5,  #0
    ...  10, #1
    ...  12, #2
    ...  12, #3 (empty)
    ...  17, #4
    ...  21, #5
    ...  21, #6 (empty)
    ... ])

    Contiguous cases:
    >>> _trimchunk(revlog, [0, 1, 2, 3, 4, 5, 6], 0)
    [0, 1, 2, 3, 4, 5]
    >>> _trimchunk(revlog, [0, 1, 2, 3, 4, 5, 6], 0, 5)
    [0, 1, 2, 3, 4]
    >>> _trimchunk(revlog, [0, 1, 2, 3, 4, 5, 6], 0, 4)
    [0, 1, 2]
    >>> _trimchunk(revlog, [0, 1, 2, 3, 4, 5, 6], 2, 4)
    [2]
    >>> _trimchunk(revlog, [0, 1, 2, 3, 4, 5, 6], 3)
    [3, 4, 5]
    >>> _trimchunk(revlog, [0, 1, 2, 3, 4, 5, 6], 3, 5)
    [3, 4]

    Discontiguous cases:
    >>> _trimchunk(revlog, [1, 3, 5, 6], 0)
    [1, 3, 5]
    >>> _trimchunk(revlog, [1, 3, 5, 6], 0, 2)
    [1]
    >>> _trimchunk(revlog, [1, 3, 5, 6], 1, 3)
    [3, 5]
    >>> _trimchunk(revlog, [1, 3, 5, 6], 1)
    [3, 5]
    """
    length = revlog.length

    if endidx is None:
        endidx = len(revs)

    # If we have a non-emtpy delta candidate, there are nothing to trim
    if revs[endidx - 1] < len(revlog):
        # Trim empty revs at the end, except the very first revision of a chain
        while (
            endidx > 1 and endidx > startidx and length(revs[endidx - 1]) == 0
        ):
            endidx -= 1

    return revs[startidx:endidx]


def segmentspan(revlog, revs):
    """Get the byte span of a segment of revisions

    revs is a sorted array of revision numbers

    >>> revlog = _testrevlog([
    ...  5,  #0
    ...  10, #1
    ...  12, #2
    ...  12, #3 (empty)
    ...  17, #4
    ... ])

    >>> segmentspan(revlog, [0, 1, 2, 3, 4])
    17
    >>> segmentspan(revlog, [0, 4])
    17
    >>> segmentspan(revlog, [3, 4])
    5
    >>> segmentspan(revlog, [1, 2, 3,])
    7
    >>> segmentspan(revlog, [1, 3])
    7
    """
    if not revs:
        return 0
    end = revlog.end(revs[-1])
    return end - revlog.start(revs[0])


def _textfromdelta(revlog, baserev, delta, p1, p2, flags, expectednode):
    """build full text from a (base, delta) pair and other metadata"""
    # special case deltas which replace entire base; no need to decode
    # base revision. this neatly avoids censored bases, which throw when
    # they're decoded.
    hlen = struct.calcsize(b">lll")
    if delta[:hlen] == mdiff.replacediffheader(
        revlog.rawsize(baserev), len(delta) - hlen
    ):
        fulltext = delta[hlen:]
    else:
        # deltabase is rawtext before changed by flag processors, which is
        # equivalent to non-raw text
        basetext = revlog.revision(baserev)
        fulltext = mdiff.patch(basetext, delta)

    try:
        validatehash = flagutil.processflagsraw(revlog, fulltext, flags)
        if validatehash:
            revlog.checkhash(fulltext, expectednode, p1=p1, p2=p2)
        if flags & REVIDX_ISCENSORED:
            raise error.StorageError(
                _(b'node %s is not censored') % expectednode
            )
    except error.CensoredNodeError:
        # must pass the censored index flag to add censored revisions
        if not flags & REVIDX_ISCENSORED:
            raise
    return fulltext


@attr.s(slots=True, frozen=True)
class _deltainfo:
    distance = attr.ib()
    deltalen = attr.ib()
    data = attr.ib()
    base = attr.ib()
    chainbase = attr.ib()
    chainlen = attr.ib()
    compresseddeltalen = attr.ib()
    snapshotdepth = attr.ib()


def drop_u_compression(delta):
    """turn into a "u" (no-compression) into no-compression without header

    This is useful for revlog format that has better compression method.
    """
    assert delta.data[0] == b'u', delta.data[0]
    return _deltainfo(
        delta.distance,
        delta.deltalen - 1,
        (b'', delta.data[1]),
        delta.base,
        delta.chainbase,
        delta.chainlen,
        delta.compresseddeltalen,
        delta.snapshotdepth,
    )


# If a revision's full text is that much bigger than a base candidate full
# text's, it is very unlikely that it will produce a valid delta. We no longer
# consider these candidates.
LIMIT_BASE2TEXT = 500

### stage of the search, used for debug and to select and to adjust some logic.
# initial stage, next step is unknown
_STAGE_UNSPECIFIED = "unspecified"
# trying the cached delta
_STAGE_CACHED = "cached"
# trying delta based on parents
_STAGE_PARENTS = "parents"
# trying to build a valid snapshot of any level
_STAGE_SNAPSHOT = "snapshot"
# trying to build a delta based of the previous revision
_STAGE_PREV = "prev"
# trying to build a full snapshot
_STAGE_FULL = "full"


class _BaseDeltaSearch(abc.ABC):
    """perform the search of a good delta for a single revlog revision

    note: some of the deltacomputer.finddeltainfo logic should probably move
    here.
    """

    def __init__(
        self,
        revlog,
        revinfo,
        p1,
        p2,
        cachedelta,
        excluded_bases=None,
        target_rev=None,
        snapshot_cache=None,
    ):
        # the DELTA_BASE_REUSE_FORCE case should have been taken care of sooner
        # so we should never end up asking such question. Adding the assert as
        # a safe-guard to detect anything that would be fishy in this regard.
        assert (
            cachedelta is None
            or cachedelta[2] != DELTA_BASE_REUSE_FORCE
            or not revlog.delta_config.general_delta
        )
        self.revlog = revlog
        self.revinfo = revinfo
        self.textlen = revinfo.textlen
        self.p1 = p1
        self.p2 = p2
        self.cachedelta = cachedelta
        self.excluded_bases = excluded_bases
        if target_rev is None:
            self.target_rev = len(self.revlog)
        self.target_rev = target_rev
        if snapshot_cache is None:
            # map: base-rev: [snapshot-revs]
            snapshot_cache = SnapshotCache()
        self.snapshot_cache = snapshot_cache

        self.tested = {nullrev}

        self.current_stage = _STAGE_UNSPECIFIED
        self.current_group = None
        self._init_group()

    def is_good_delta_info(self, deltainfo):
        """Returns True if the given delta is good.

        Good means that it is within the disk span, disk size, and chain length
        bounds that we know to be performant.
        """
        if not self._is_good_delta_info_universal(deltainfo):
            return False
        if not self._is_good_delta_info_chain_quality(deltainfo):
            return False
        return True

    def _is_good_delta_info_universal(self, deltainfo):
        """Returns True if the given delta is good.

        This performs generic checks needed by all format variants.

        This is used by is_good_delta_info.
        """

        if deltainfo is None:
            return False

        # the DELTA_BASE_REUSE_FORCE case should have been taken care of sooner
        # so we should never end up asking such question. Adding the assert as
        # a safe-guard to detect anything that would be fishy in this regard.
        assert (
            self.revinfo.cachedelta is None
            or self.revinfo.cachedelta[2] != DELTA_BASE_REUSE_FORCE
            or not self.revlog.delta_config.general_delta
        )

        # Bad delta from new delta size:
        #
        #   If the delta size is larger than the target text, storing the delta
        #   will be inefficient.
        if self.revinfo.textlen < deltainfo.deltalen:
            return False

        return True

    def _is_good_delta_info_chain_quality(self, deltainfo):
        """Returns True if the chain associated with the delta is good.

        This performs checks for format that use delta chains.

        This is used by is_good_delta_info.
        """
        # - 'deltainfo.distance' is the distance from the base revision --
        #   bounding it limits the amount of I/O we need to do.

        defaultmax = self.revinfo.textlen * 4
        maxdist = self.revlog.delta_config.max_deltachain_span
        if not maxdist:
            maxdist = deltainfo.distance  # ensure the conditional pass
        maxdist = max(maxdist, defaultmax)

        # Bad delta from read span:
        #
        #   If the span of data read is larger than the maximum allowed.
        #
        #   In the sparse-revlog case, we rely on the associated "sparse
        #   reading" to avoid issue related to the span of data. In theory, it
        #   would be possible to build pathological revlog where delta pattern
        #   would lead to too many reads. However, they do not happen in
        #   practice at all. So we skip the span check entirely.
        if (
            not self.revlog.delta_config.sparse_revlog
            and maxdist < deltainfo.distance
        ):
            return False

        # Bad delta from cumulated payload size:
        #
        # - 'deltainfo.compresseddeltalen' is the sum of the total size of
        #   deltas we need to apply -- bounding it limits the amount of CPU
        #   we consume.
        max_chain_data = self.revinfo.textlen * LIMIT_DELTA2TEXT
        #   If the sum of delta get larger than K * target text length.
        if max_chain_data < deltainfo.compresseddeltalen:
            return False

        # Bad delta from chain length:
        #
        #   If the number of delta in the chain gets too high.
        if (
            self.revlog.delta_config.max_chain_len
            and self.revlog.delta_config.max_chain_len < deltainfo.chainlen
        ):
            return False
        return True

    @property
    def done(self):
        """True when all possible candidate have been tested"""
        return self.current_group is None

    @abc.abstractmethod
    def next_group(self, good_delta=None):
        """move to the next group to test

        The group of revision to test will be available in
        `self.current_group`.  If the previous group had any good delta, the
        best one can be passed as the `good_delta` parameter to help selecting
        the next group.

        If not revision remains to be, `self.done` will be True and
        `self.current_group` will be None.
        """
        pass

    @abc.abstractmethod
    def _init_group(self):
        pass


class _NoDeltaSearch(_BaseDeltaSearch):
    """Search for no delta.

    This search variant is to be used in case where we should not store delta.
    """

    def _init_group(self):
        self.current_stage = _STAGE_FULL

    def next_group(self, good_delta=None):
        pass


class _PrevDeltaSearch(_BaseDeltaSearch):
    """Search for delta against the previous revision only

    This search variant is to be used when the format does not allow for delta
    against arbitrary bases.
    """

    def _init_group(self):
        self.current_stage = _STAGE_PREV
        self.current_group = [self.target_rev - 1]
        self.tested.update(self.current_group)

    def next_group(self, good_delta=None):
        self.current_stage = _STAGE_FULL
        self.current_group = None


class _GeneralDeltaSearch(_BaseDeltaSearch):
    """Delta search variant for general-delta repository"""

    def _init_group(self):
        # Why search for delta base if we cannot use a delta base ?
        # also see issue6056
        assert self.revlog.delta_config.general_delta
        self._candidates_iterator = self._iter_groups()
        self._last_good = None
        if (
            self.cachedelta is not None
            and self.cachedelta[2] > DELTA_BASE_REUSE_NO
            and self._pre_filter_rev(self.cachedelta[0])
        ):
            # First we try to reuse a the delta contained in the bundle.  (or from
            # the source revlog)
            #
            # This logic only applies to general delta repositories and can be
            # disabled through configuration. Disabling reuse source delta is
            # useful when we want to make sure we recomputed "optimal" deltas.
            self.current_stage = _STAGE_CACHED
            self._internal_group = (self.cachedelta[0],)
            self._internal_idx = 0
            self.current_group = self._internal_group
            self.tested.update(self.current_group)
        else:
            self._next_internal_group()

    def _next_internal_group(self):
        # self._internal_group can be larger than self.current_group
        self._internal_idx = 0
        group = self._candidates_iterator.send(self._last_good)
        if group is not None:
            group = self._pre_filter_candidate_revs(group)
        self._internal_group = group
        if self._internal_group is None:
            self.current_group = None
        elif len(self._internal_group) == 0:
            self.next_group()
        else:
            chunk_size = self.revlog.delta_config.candidate_group_chunk_size
            if chunk_size > 0:
                self.current_group = self._internal_group[:chunk_size]
                self._internal_idx += chunk_size
            else:
                self.current_group = self._internal_group
                self._internal_idx += len(self.current_group)

            self.tested.update(self.current_group)

    def next_group(self, good_delta=None):
        old_good = self._last_good
        if good_delta is not None:
            self._last_good = good_delta
        if self.current_stage == _STAGE_CACHED and good_delta is not None:
            # the cache is good, let us use the cache as requested
            self._candidates_iterator = None
            self._internal_group = None
            self._internal_idx = None
            self.current_group = None
            return

        if (self._internal_idx < len(self._internal_group)) and (
            old_good != good_delta
        ):
            # When the size of the candidate group is big, it can result in
            # a quite significant performance impact. To reduce this, we
            # can send them in smaller batches until the new batch does not
            # provide any improvements.
            #
            # This might reduce the overall efficiency of the compression
            # in some corner cases, but that should also prevent very
            # pathological cases from being an issue. (eg. 20 000
            # candidates).
            #
            # XXX note that the ordering of the group becomes important as
            # it now impacts the final result. The current order is
            # unprocessed and can be improved.
            chunk_size = self.revlog.delta_config.candidate_group_chunk_size
            next_idx = self._internal_idx + chunk_size
            self.current_group = self._internal_group[
                self._internal_idx : next_idx
            ]
            self.tested.update(self.current_group)
            self._internal_idx = next_idx
        else:
            self._next_internal_group()

    def _pre_filter_candidate_revs(self, temptative):
        """filter possible candidate before computing a delta

        This function use various criteria to pre-filter candidate delta base
        before we compute a delta and evaluate its quality.

        Such pre-filter limit the number of computed delta, an expensive operation.

        return the updated list of revision to test
        """
        deltalength = self.revlog.length
        deltaparent = self.revlog.deltaparent

        tested = self.tested
        group = []
        for rev in temptative:
            # skip over empty delta (no need to include them in a chain)
            while not (rev == nullrev or rev in tested or deltalength(rev)):
                tested.add(rev)
                rev = deltaparent(rev)
            if self._pre_filter_rev(rev):
                group.append(rev)
            else:
                self.tested.add(rev)
        return group

    def _pre_filter_rev_universal(self, rev):
        """pre filtering that is need in all cases.

        return True if it seems okay to test a rev, False otherwise.

        used by _pre_filter_rev.
        """
        # no need to try a delta against nullrev, this will be done as
        # a last resort.
        if rev == nullrev:
            return False
        # filter out revision we tested already
        if rev in self.tested:
            return False

        # an higher authority deamed the base unworthy (e.g. censored)
        if self.excluded_bases is not None and rev in self.excluded_bases:
            return False
        # We are in some recomputation cases and that rev is too high
        # in the revlog
        if self.target_rev is not None and rev >= self.target_rev:
            return False
        # no delta for rawtext-changing revs (see "candelta" for why)
        if self.revlog.flags(rev) & REVIDX_RAWTEXT_CHANGING_FLAGS:
            return False
        return True

    def _pre_filter_rev_delta_chain(self, rev):
        """pre filtering that is needed in sparse revlog cases

        return True if it seems okay to test a rev, False otherwise.

        used by _pre_filter_rev.
        """
        deltas_limit = self.revinfo.textlen * LIMIT_DELTA2TEXT
        # filter out delta base that will never produce good delta
        #
        # if the delta of that base is already bigger than the limit
        # for the delta chain size, doing a delta is hopeless.
        if deltas_limit < self.revlog.length(rev):
            return False

        # If we reach here, we are about to build and test a delta.
        # The delta building process will compute the chaininfo in all
        # case, since that computation is cached, it is fine to access
        # it here too.
        chainlen, chainsize = self.revlog._chaininfo(rev)
        # if chain will be too long, skip base
        if (
            self.revlog.delta_config.max_chain_len
            and chainlen >= self.revlog.delta_config.max_chain_len
        ):
            return False
        # if chain already have too much data, skip base
        if deltas_limit < chainsize:
            return False
        return True

    def _pre_filter_rev(self, rev):
        """return True if it seems okay to test a rev, False otherwise"""
        if not self._pre_filter_rev_universal(rev):
            return False
        if not self._pre_filter_rev_delta_chain(rev):
            return False
        return True

    def _iter_parents(self):
        # exclude already lazy tested base if any
        parents = [p for p in (self.p1, self.p2) if p != nullrev]

        self.current_stage = _STAGE_PARENTS
        if (
            not self.revlog.delta_config.delta_both_parents
            and len(parents) == 2
        ):
            parents.sort()
            # To minimize the chance of having to build a fulltext,
            # pick first whichever parent is closest to us (max rev)
            yield (parents[1],)
            # then the other one (min rev) if the first did not fit
            yield (parents[0],)
        elif len(parents) > 0:
            # Test all parents (1 or 2), and keep the best candidate
            yield parents

    def _iter_prev(self):
        # other approach failed try against prev to hopefully save us a
        # fulltext.
        self.current_stage = _STAGE_PREV
        yield (self.target_rev - 1,)

    def _iter_groups(self):
        good = None
        for group in self._iter_parents():
            good = yield group
            if good is not None:
                break
        else:
            assert good is None
            yield from self._iter_prev()
        yield None


class _SparseDeltaSearch(_GeneralDeltaSearch):
    """Delta search variants for sparse-revlog"""

    def is_good_delta_info(self, deltainfo):
        """Returns True if the given delta is good.

        Good means that it is within the disk span, disk size, and chain length
        bounds that we know to be performant.
        """
        if not self._is_good_delta_info_universal(deltainfo):
            return False
        if not self._is_good_delta_info_chain_quality(deltainfo):
            return False
        if not self._is_good_delta_info_snapshot_constraints(deltainfo):
            return False
        return True

    def _is_good_delta_info_snapshot_constraints(self, deltainfo):
        """Returns True if the chain associated with snapshots

        This performs checks for format that use sparse-revlog and intermediate
        snapshots.

        This is used by is_good_delta_info.
        """
        # if not a snapshot, this method has no filtering to do
        if deltainfo.snapshotdepth is None:
            return True
        # bad delta from intermediate snapshot size limit
        #
        #   If an intermediate snapshot size is higher than the limit.  The
        #   limit exist to prevent endless chain of intermediate delta to be
        #   created.
        if (
            self.revinfo.textlen >> deltainfo.snapshotdepth
        ) < deltainfo.deltalen:
            return False

        # bad delta if new intermediate snapshot is larger than the previous
        # snapshot
        if self.revlog.length(deltainfo.base) < deltainfo.deltalen:
            return False

        return True

    def _pre_filter_rev(self, rev):
        """return True if it seems okay to test a rev, False otherwise"""
        if not self._pre_filter_rev_universal(rev):
            return False
        if not self._pre_filter_rev_delta_chain(rev):
            return False
        if not self._pre_filter_rev_sparse(rev):
            return False
        return True

    def _pre_filter_rev_sparse(self, rev):
        """pre filtering that is needed in sparse revlog cases

        return True if it seems okay to test a rev, False otherwise.

        used by _pre_filter_rev.
        """
        assert self.revlog.delta_config.sparse_revlog
        # if the revision we test again is too small, the resulting delta
        # will be large anyway as that amount of data to be added is big
        if self.revlog.rawsize(rev) < (self.textlen // LIMIT_BASE2TEXT):
            return False

        if self.revlog.delta_config.upper_bound_comp is not None:
            maxcomp = self.revlog.delta_config.upper_bound_comp
            basenotsnap = (self.p1, self.p2, nullrev)
            if rev not in basenotsnap and self.revlog.issnapshot(rev):
                snapshotdepth = self.revlog.snapshotdepth(rev)
                # If text is significantly larger than the base, we can
                # expect the resulting delta to be proportional to the size
                # difference
                revsize = self.revlog.rawsize(rev)
                rawsizedistance = max(self.textlen - revsize, 0)
                # use an estimate of the compression upper bound.
                lowestrealisticdeltalen = rawsizedistance // maxcomp

                # check the absolute constraint on the delta size
                snapshotlimit = self.textlen >> snapshotdepth
                if snapshotlimit < lowestrealisticdeltalen:
                    # delta lower bound is larger than accepted upper
                    # bound
                    return False

                # check the relative constraint on the delta size
                revlength = self.revlog.length(rev)
                if revlength < lowestrealisticdeltalen:
                    # delta probable lower bound is larger than target
                    # base
                    return False
        return True

    def _iter_snapshots_base(self):
        assert self.revlog.delta_config.sparse_revlog
        assert self.current_stage == _STAGE_SNAPSHOT
        prev = self.target_rev - 1
        deltachain = lambda rev: self.revlog._deltachain(rev)[0]

        parents = [p for p in (self.p1, self.p2) if p != nullrev]
        if not parents:
            return
        # See if we can use an existing snapshot in the parent chains to
        # use as a base for a new intermediate-snapshot
        #
        # search for snapshot in parents delta chain map: snapshot-level:
        # snapshot-rev
        parents_snaps = collections.defaultdict(set)
        candidate_chains = [deltachain(p) for p in parents]
        for chain in candidate_chains:
            for idx, s in enumerate(chain):
                if not self.revlog.issnapshot(s):
                    break
                parents_snaps[idx].add(s)
        snapfloor = min(parents_snaps[0]) + 1
        self.snapshot_cache.update(self.revlog, snapfloor)
        # search for the highest "unrelated" revision
        #
        # Adding snapshots used by "unrelated" revision increase the odd we
        # reuse an independant, yet better snapshot chain.
        #
        # XXX instead of building a set of revisions, we could lazily
        # enumerate over the chains. That would be more efficient, however
        # we stick to simple code for now.
        all_revs = set()
        for chain in candidate_chains:
            all_revs.update(chain)
        other = None
        for r in self.revlog.revs(prev, snapfloor):
            if r not in all_revs:
                other = r
                break
        if other is not None:
            # To avoid unfair competition, we won't use unrelated
            # intermediate snapshot that are deeper than the ones from the
            # parent delta chain.
            max_depth = max(parents_snaps.keys())
            chain = deltachain(other)
            for depth, s in enumerate(chain):
                if s < snapfloor:
                    continue
                if max_depth < depth:
                    break
                if not self.revlog.issnapshot(s):
                    break
                parents_snaps[depth].add(s)
        # Test them as possible intermediate snapshot base We test them
        # from highest to lowest level. High level one are more likely to
        # result in small delta
        floor = None
        for idx, snaps in sorted(parents_snaps.items(), reverse=True):
            siblings = set()
            for s in snaps:
                siblings.update(self.snapshot_cache.snapshots[s])
            # Before considering making a new intermediate snapshot, we
            # check if an existing snapshot, children of base we consider,
            # would be suitable.
            #
            # It give a change to reuse a delta chain "unrelated" to the
            # current revision instead of starting our own. Without such
            # re-use, topological branches would keep reopening new chains.
            # Creating more and more snapshot as the repository grow.

            if floor is not None:
                # We only do this for siblings created after the one in our
                # parent's delta chain. Those created before has less
                # chances to be valid base since our ancestors had to
                # create a new snapshot.
                siblings = [r for r in siblings if floor < r]
            yield tuple(sorted(siblings))
            # then test the base from our parent's delta chain.
            yield tuple(sorted(snaps))
            floor = min(snaps)
        # No suitable base found in the parent chain, search if any full
        # snapshots emitted since parent's base would be a suitable base
        # for an intermediate snapshot.
        #
        # It give a chance to reuse a delta chain unrelated to the current
        # revisions instead of starting our own. Without such re-use,
        # topological branches would keep reopening new full chains.
        # Creating more and more snapshot as the repository grow.
        full = [
            r for r in self.snapshot_cache.snapshots[nullrev] if snapfloor <= r
        ]
        yield tuple(sorted(full))

    def _iter_snapshots(self):
        assert self.revlog.delta_config.sparse_revlog
        self.current_stage = _STAGE_SNAPSHOT
        good = None
        groups = self._iter_snapshots_base()
        for candidates in groups:
            good = yield candidates
            if good is not None:
                break
        # if we have a refinable value, try to refine it
        if good is not None and good.snapshotdepth is not None:
            assert self.current_stage == _STAGE_SNAPSHOT
            # refine snapshot down
            previous = None
            while previous != good:
                previous = good
                base = self.revlog.deltaparent(good.base)
                if base == nullrev:
                    break
                good = yield (base,)
            # refine snapshot up
            if not self.snapshot_cache.snapshots:
                self.snapshot_cache.update(self.revlog, good.base + 1)
            previous = None
            while good != previous:
                previous = good
                children = tuple(
                    sorted(c for c in self.snapshot_cache.snapshots[good.base])
                )
                good = yield children
        yield None

    def _iter_groups(self):
        good = None
        for group in self._iter_parents():
            good = yield group
            if good is not None:
                break
        else:
            assert good is None
            assert self.revlog.delta_config.sparse_revlog
            # If sparse revlog is enabled, we can try to refine the
            # available deltas
            iter_snap = self._iter_snapshots()
            group = iter_snap.send(None)
            while group is not None:
                good = yield group
                group = iter_snap.send(good)
        yield None


class SnapshotCache:
    __slots__ = ('snapshots', '_start_rev', '_end_rev')

    def __init__(self):
        self.snapshots = collections.defaultdict(set)
        self._start_rev = None
        self._end_rev = None

    def update(self, revlog, start_rev=0):
        """find snapshots from start_rev to tip"""
        nb_revs = len(revlog)
        end_rev = nb_revs - 1
        if start_rev > end_rev:
            return  # range is empty

        if self._start_rev is None:
            assert self._end_rev is None
            self._update(revlog, start_rev, end_rev)
        elif not (self._start_rev <= start_rev and end_rev <= self._end_rev):
            if start_rev < self._start_rev:
                self._update(revlog, start_rev, self._start_rev - 1)
            if self._end_rev < end_rev:
                self._update(revlog, self._end_rev + 1, end_rev)

        if self._start_rev is None:
            assert self._end_rev is None
            self._end_rev = end_rev
            self._start_rev = start_rev
        else:
            self._start_rev = min(self._start_rev, start_rev)
            self._end_rev = max(self._end_rev, end_rev)
        assert self._start_rev <= self._end_rev, (
            self._start_rev,
            self._end_rev,
        )

    def _update(self, revlog, start_rev, end_rev):
        """internal method that actually do update content"""
        assert self._start_rev is None or (
            start_rev < self._start_rev or start_rev > self._end_rev
        ), (self._start_rev, self._end_rev, start_rev, end_rev)
        assert self._start_rev is None or (
            end_rev < self._start_rev or end_rev > self._end_rev
        ), (self._start_rev, self._end_rev, start_rev, end_rev)
        cache = self.snapshots
        if hasattr(revlog.index, 'findsnapshots'):
            revlog.index.findsnapshots(cache, start_rev, end_rev)
        else:
            deltaparent = revlog.deltaparent
            issnapshot = revlog.issnapshot
            for rev in revlog.revs(start_rev, end_rev):
                if issnapshot(rev):
                    cache[deltaparent(rev)].add(rev)


class deltacomputer:
    """object capable of computing delta and finding delta for multiple revision

    This object is meant to compute and find multiple delta applied to the same
    revlog.
    """

    def __init__(
        self,
        revlog,
        write_debug=None,
        debug_search=False,
        debug_info=None,
    ):
        self.revlog = revlog
        self._write_debug = write_debug
        if write_debug is None:
            self._debug_search = False
        else:
            self._debug_search = debug_search
        self._debug_info = debug_info
        self._snapshot_cache = SnapshotCache()

    @property
    def _gather_debug(self):
        return self._write_debug is not None or self._debug_info is not None

    def buildtext(self, revinfo):
        """Builds a fulltext version of a revision

        revinfo: revisioninfo instance that contains all needed info
        """
        btext = revinfo.btext
        if btext[0] is not None:
            return btext[0]

        revlog = self.revlog
        cachedelta = revinfo.cachedelta
        baserev = cachedelta[0]
        delta = cachedelta[1]

        fulltext = btext[0] = _textfromdelta(
            revlog,
            baserev,
            delta,
            revinfo.p1,
            revinfo.p2,
            revinfo.flags,
            revinfo.node,
        )
        return fulltext

    def _builddeltadiff(self, base, revinfo):
        revlog = self.revlog
        t = self.buildtext(revinfo)
        if revlog.iscensored(base):
            # deltas based on a censored revision must replace the
            # full content in one patch, so delta works everywhere
            header = mdiff.replacediffheader(revlog.rawsize(base), len(t))
            delta = header + t
        else:
            ptext = revlog.rawdata(base)
            delta = mdiff.textdiff(ptext, t)

        return delta

    def _builddeltainfo(
        self, revinfo, base, target_rev=None, as_snapshot=False
    ):
        # can we use the cached delta?
        revlog = self.revlog
        chainbase = revlog.chainbase(base)
        if revlog.delta_config.general_delta:
            deltabase = base
        else:
            if target_rev is not None and base != target_rev - 1:
                msg = (
                    b'general delta cannot use delta for something else '
                    b'than `prev`: %d<-%d'
                )
                msg %= (base, target_rev)
                raise error.ProgrammingError(msg)
            deltabase = chainbase
        snapshotdepth = None
        if revlog.delta_config.sparse_revlog and deltabase == nullrev:
            snapshotdepth = 0
        elif revlog.delta_config.sparse_revlog and as_snapshot:
            assert revlog.issnapshot(deltabase)
            # A delta chain should always be one full snapshot,
            # zero or more semi-snapshots, and zero or more deltas
            p1, p2 = revlog.rev(revinfo.p1), revlog.rev(revinfo.p2)
            if deltabase not in (p1, p2) and revlog.issnapshot(deltabase):
                snapshotdepth = len(revlog._deltachain(deltabase)[0])
        delta = None
        if revinfo.cachedelta:
            cachebase = revinfo.cachedelta[0]
            # check if the diff still apply
            currentbase = cachebase
            while (
                currentbase != nullrev
                and currentbase != base
                and self.revlog.length(currentbase) == 0
            ):
                currentbase = self.revlog.deltaparent(currentbase)
            if self.revlog.delta_config.lazy_delta and currentbase == base:
                delta = revinfo.cachedelta[1]
        if delta is None:
            delta = self._builddeltadiff(base, revinfo)
        if self._debug_search:
            msg = b"DBG-DELTAS-SEARCH:     uncompressed-delta-size=%d\n"
            msg %= len(delta)
            self._write_debug(msg)
        # snapshotdept need to be neither None nor 0 level snapshot
        if revlog.delta_config.upper_bound_comp is not None and snapshotdepth:
            lowestrealisticdeltalen = (
                len(delta) // revlog.delta_config.upper_bound_comp
            )
            snapshotlimit = revinfo.textlen >> snapshotdepth
            if self._debug_search:
                msg = b"DBG-DELTAS-SEARCH:     projected-lower-size=%d\n"
                msg %= lowestrealisticdeltalen
                self._write_debug(msg)
            if snapshotlimit < lowestrealisticdeltalen:
                if self._debug_search:
                    msg = b"DBG-DELTAS-SEARCH:     DISCARDED (snapshot limit)\n"
                    self._write_debug(msg)
                return None
            if revlog.length(base) < lowestrealisticdeltalen:
                if self._debug_search:
                    msg = b"DBG-DELTAS-SEARCH:     DISCARDED (prev size)\n"
                    self._write_debug(msg)
                return None
        header, data = revlog._inner.compress(delta)
        deltalen = len(header) + len(data)
        offset = revlog.end(len(revlog) - 1)
        dist = deltalen + offset - revlog.start(chainbase)
        chainlen, compresseddeltalen = revlog._chaininfo(base)
        chainlen += 1
        compresseddeltalen += deltalen

        return _deltainfo(
            dist,
            deltalen,
            (header, data),
            deltabase,
            chainbase,
            chainlen,
            compresseddeltalen,
            snapshotdepth,
        )

    def _fullsnapshotinfo(self, revinfo, curr):
        rawtext = self.buildtext(revinfo)
        data = self.revlog._inner.compress(rawtext)
        compresseddeltalen = deltalen = dist = len(data[1]) + len(data[0])
        deltabase = chainbase = curr
        snapshotdepth = 0
        chainlen = 1

        return _deltainfo(
            dist,
            deltalen,
            data,
            deltabase,
            chainbase,
            chainlen,
            compresseddeltalen,
            snapshotdepth,
        )

    def finddeltainfo(self, revinfo, excluded_bases=None, target_rev=None):
        """Find an acceptable delta against a candidate revision

        revinfo: information about the revision (instance of _revisioninfo)

        Returns the first acceptable candidate revision, as ordered by
        _candidategroups

        If no suitable deltabase is found, we return delta info for a full
        snapshot.

        `excluded_bases` is an optional set of revision that cannot be used as
        a delta base. Use this to recompute delta suitable in censor or strip
        context.
        """
        if target_rev is None:
            target_rev = len(self.revlog)

        gather_debug = self._gather_debug
        cachedelta = revinfo.cachedelta
        revlog = self.revlog
        p1r = p2r = None

        if excluded_bases is None:
            excluded_bases = set()

        if gather_debug:
            start = util.timer()
            dbg = self._one_dbg_data()
            dbg['revision'] = target_rev
            p1r = revlog.rev(revinfo.p1)
            p2r = revlog.rev(revinfo.p2)
            if p1r != nullrev:
                p1_chain_len = revlog._chaininfo(p1r)[0]
            else:
                p1_chain_len = -1
            if p2r != nullrev:
                p2_chain_len = revlog._chaininfo(p2r)[0]
            else:
                p2_chain_len = -1
            dbg['p1-chain-len'] = p1_chain_len
            dbg['p2-chain-len'] = p2_chain_len

        # 1) if the revision is empty, no amount of delta can beat it
        #
        # 2) no delta for flag processor revision (see "candelta" for why)
        # not calling candelta since only one revision needs test, also to
        # avoid overhead fetching flags again.
        if not revinfo.textlen or revinfo.flags & REVIDX_RAWTEXT_CHANGING_FLAGS:
            deltainfo = self._fullsnapshotinfo(revinfo, target_rev)
            if gather_debug:
                end = util.timer()
                dbg['duration'] = end - start
                dbg[
                    'delta-base'
                ] = deltainfo.base  # pytype: disable=attribute-error
                dbg['search_round_count'] = 0
                dbg['using-cached-base'] = False
                dbg['delta_try_count'] = 0
                dbg['type'] = b"full"
                dbg['snapshot-depth'] = 0
                self._dbg_process_data(dbg)
            return deltainfo

        deltainfo = None

        # If this source delta are to be forcibly reuse, let us comply early.
        if (
            revlog.delta_config.general_delta
            and revinfo.cachedelta is not None
            and revinfo.cachedelta[2] == DELTA_BASE_REUSE_FORCE
        ):
            base = revinfo.cachedelta[0]
            if base == nullrev:
                dbg_type = b"full"
                deltainfo = self._fullsnapshotinfo(revinfo, target_rev)
                if gather_debug:
                    snapshotdepth = 0
            elif base not in excluded_bases:
                delta = revinfo.cachedelta[1]
                header, data = revlog.compress(delta)
                deltalen = len(header) + len(data)
                if gather_debug:
                    offset = revlog.end(len(revlog) - 1)
                    chainbase = revlog.chainbase(base)
                    distance = deltalen + offset - revlog.start(chainbase)
                    chainlen, compresseddeltalen = revlog._chaininfo(base)
                    chainlen += 1
                    compresseddeltalen += deltalen
                    if base == p1r or base == p2r:
                        dbg_type = b"delta"
                        snapshotdepth = None
                    elif not revlog.issnapshot(base):
                        snapshotdepth = None
                    else:
                        dbg_type = b"snapshot"
                        snapshotdepth = revlog.snapshotdepth(base) + 1
                else:
                    distance = None
                    chainbase = None
                    chainlen = None
                    compresseddeltalen = None
                    snapshotdepth = None
                deltainfo = _deltainfo(
                    distance=distance,
                    deltalen=deltalen,
                    data=(header, data),
                    base=base,
                    chainbase=chainbase,
                    chainlen=chainlen,
                    compresseddeltalen=compresseddeltalen,
                    snapshotdepth=snapshotdepth,
                )

            if deltainfo is not None:
                if gather_debug:
                    end = util.timer()
                    dbg['duration'] = end - start
                    dbg[
                        'delta-base'
                    ] = deltainfo.base  # pytype: disable=attribute-error
                    dbg['search_round_count'] = 0
                    dbg['using-cached-base'] = True
                    dbg['delta_try_count'] = 0
                    dbg['type'] = b"full"
                    if snapshotdepth is None:
                        dbg['snapshot-depth'] = -1
                    else:
                        dbg['snapshot-depth'] = snapshotdepth
                    self._dbg_process_data(dbg)
                return deltainfo

        # count the number of different delta we tried (for debug purpose)
        dbg_try_count = 0
        # count the number of "search round" we did. (for debug purpose)
        dbg_try_rounds = 0
        dbg_type = b'unknown'

        if p1r is None:
            p1r = revlog.rev(revinfo.p1)
            p2r = revlog.rev(revinfo.p2)

        if self._debug_search:
            msg = b"DBG-DELTAS-SEARCH: SEARCH rev=%d\n"
            msg %= target_rev
            self._write_debug(msg)

        # should we try to build a delta?
        if not (len(self.revlog) and self.revlog._storedeltachains):
            search_cls = _NoDeltaSearch
        elif self.revlog.delta_config.sparse_revlog:
            search_cls = _SparseDeltaSearch
        elif self.revlog.delta_config.general_delta:
            search_cls = _GeneralDeltaSearch
        else:
            # before general delta, there is only one possible delta base
            search_cls = _PrevDeltaSearch

        search = search_cls(
            self.revlog,
            revinfo,
            p1r,
            p2r,
            cachedelta,
            excluded_bases,
            target_rev,
            snapshot_cache=self._snapshot_cache,
        )

        while not search.done:
            current_group = search.current_group
            # current_group can be `None`, but not is search.done is False
            # We add this assert to help pytype
            assert current_group is not None
            candidaterevs = current_group
            dbg_try_rounds += 1
            if self._debug_search:
                prev = None
                if deltainfo is not None:
                    prev = deltainfo.base

                if (
                    cachedelta is not None
                    and len(candidaterevs) == 1
                    and cachedelta[0] in candidaterevs
                ):
                    round_type = b"cached-delta"
                elif p1r in candidaterevs or p2r in candidaterevs:
                    round_type = b"parents"
                elif prev is not None and all(c < prev for c in candidaterevs):
                    round_type = b"refine-down"
                elif prev is not None and all(c > prev for c in candidaterevs):
                    round_type = b"refine-up"
                else:
                    round_type = b"search-down"
                msg = b"DBG-DELTAS-SEARCH: ROUND #%d - %d candidates - %s\n"
                msg %= (dbg_try_rounds, len(candidaterevs), round_type)
                self._write_debug(msg)
            nominateddeltas = []
            if deltainfo is not None:
                if self._debug_search:
                    msg = (
                        b"DBG-DELTAS-SEARCH:   CONTENDER: rev=%d - length=%d\n"
                    )
                    msg %= (deltainfo.base, deltainfo.deltalen)
                    self._write_debug(msg)
                # if we already found a good delta,
                # challenge it against refined candidates
                nominateddeltas.append(deltainfo)
            for candidaterev in candidaterevs:
                if self._debug_search:
                    msg = b"DBG-DELTAS-SEARCH:   CANDIDATE: rev=%d\n"
                    msg %= candidaterev
                    self._write_debug(msg)
                    candidate_type = None
                    if candidaterev == p1r:
                        candidate_type = b"p1"
                    elif candidaterev == p2r:
                        candidate_type = b"p2"
                    elif self.revlog.issnapshot(candidaterev):
                        candidate_type = b"snapshot-%d"
                        candidate_type %= self.revlog.snapshotdepth(
                            candidaterev
                        )

                    if candidate_type is not None:
                        msg = b"DBG-DELTAS-SEARCH:     type=%s\n"
                        msg %= candidate_type
                        self._write_debug(msg)
                    msg = b"DBG-DELTAS-SEARCH:     size=%d\n"
                    msg %= self.revlog.length(candidaterev)
                    self._write_debug(msg)
                    msg = b"DBG-DELTAS-SEARCH:     base=%d\n"
                    msg %= self.revlog.deltaparent(candidaterev)
                    self._write_debug(msg)

                dbg_try_count += 1

                if self._debug_search:
                    delta_start = util.timer()
                candidatedelta = self._builddeltainfo(
                    revinfo,
                    candidaterev,
                    target_rev=target_rev,
                    as_snapshot=search.current_stage == _STAGE_SNAPSHOT,
                )
                if self._debug_search:
                    delta_end = util.timer()
                    msg = b"DBG-DELTAS-SEARCH:     delta-search-time=%f\n"
                    msg %= delta_end - delta_start
                    self._write_debug(msg)
                if candidatedelta is not None:
                    if search.is_good_delta_info(candidatedelta):
                        if self._debug_search:
                            msg = b"DBG-DELTAS-SEARCH:     DELTA: length=%d (GOOD)\n"
                            msg %= candidatedelta.deltalen
                            self._write_debug(msg)
                        nominateddeltas.append(candidatedelta)
                    elif self._debug_search:
                        msg = b"DBG-DELTAS-SEARCH:     DELTA: length=%d (BAD)\n"
                        msg %= candidatedelta.deltalen
                        self._write_debug(msg)
                elif self._debug_search:
                    msg = b"DBG-DELTAS-SEARCH:     NO-DELTA\n"
                    self._write_debug(msg)
            if nominateddeltas:
                deltainfo = min(nominateddeltas, key=lambda x: x.deltalen)
            search.next_group(deltainfo)

        if deltainfo is None:
            dbg_type = b"full"
            deltainfo = self._fullsnapshotinfo(revinfo, target_rev)
        elif deltainfo.snapshotdepth:  # pytype: disable=attribute-error
            dbg_type = b"snapshot"
        else:
            dbg_type = b"delta"

        if gather_debug:
            end = util.timer()
            if dbg_type == b'full':
                used_cached = (
                    cachedelta is not None
                    and dbg_try_rounds == 0
                    and dbg_try_count == 0
                    and cachedelta[0] == nullrev
                )
            else:
                used_cached = (
                    cachedelta is not None
                    and dbg_try_rounds == 1
                    and dbg_try_count == 1
                    and deltainfo.base == cachedelta[0]
                )
            dbg['duration'] = end - start
            dbg[
                'delta-base'
            ] = deltainfo.base  # pytype: disable=attribute-error
            dbg['search_round_count'] = dbg_try_rounds
            dbg['using-cached-base'] = used_cached
            dbg['delta_try_count'] = dbg_try_count
            dbg['type'] = dbg_type
            if (
                deltainfo.snapshotdepth  # pytype: disable=attribute-error
                is not None
            ):
                dbg[
                    'snapshot-depth'
                ] = deltainfo.snapshotdepth  # pytype: disable=attribute-error
            else:
                dbg['snapshot-depth'] = -1
            self._dbg_process_data(dbg)
        return deltainfo

    def _one_dbg_data(self):
        dbg = {
            'duration': None,
            'revision': None,
            'delta-base': None,
            'search_round_count': None,
            'using-cached-base': None,
            'delta_try_count': None,
            'type': None,
            'p1-chain-len': None,
            'p2-chain-len': None,
            'snapshot-depth': None,
            'target-revlog': None,
        }
        target_revlog = b"UNKNOWN"
        target_type = self.revlog.target[0]
        target_key = self.revlog.target[1]
        if target_type == KIND_CHANGELOG:
            target_revlog = b'CHANGELOG:'
        elif target_type == KIND_MANIFESTLOG:
            target_revlog = b'MANIFESTLOG:'
            if target_key:
                target_revlog += b'%s:' % target_key
        elif target_type == KIND_FILELOG:
            target_revlog = b'FILELOG:'
            if target_key:
                target_revlog += b'%s:' % target_key
        dbg['target-revlog'] = target_revlog
        return dbg

    def _dbg_process_data(self, dbg):
        if self._debug_info is not None:
            self._debug_info.append(dbg)

        if self._write_debug is not None:
            msg = (
                b"DBG-DELTAS:"
                b" %-12s"
                b" rev=%d:"
                b" delta-base=%d"
                b" is-cached=%d"
                b" - search-rounds=%d"
                b" try-count=%d"
                b" - delta-type=%-6s"
                b" snap-depth=%d"
                b" - p1-chain-length=%d"
                b" p2-chain-length=%d"
                b" - duration=%f"
                b"\n"
            )
            msg %= (
                dbg["target-revlog"],
                dbg["revision"],
                dbg["delta-base"],
                dbg["using-cached-base"],
                dbg["search_round_count"],
                dbg["delta_try_count"],
                dbg["type"],
                dbg["snapshot-depth"],
                dbg["p1-chain-len"],
                dbg["p2-chain-len"],
                dbg["duration"],
            )
            self._write_debug(msg)


def delta_compression(default_compression_header, deltainfo):
    """return (COMPRESSION_MODE, deltainfo)

    used by revlog v2+ format to dispatch between PLAIN and DEFAULT
    compression.
    """
    h, d = deltainfo.data
    compression_mode = COMP_MODE_INLINE
    if not h and not d:
        # not data to store at all... declare them uncompressed
        compression_mode = COMP_MODE_PLAIN
    elif not h:
        t = d[0:1]
        if t == b'\0':
            compression_mode = COMP_MODE_PLAIN
        elif t == default_compression_header:
            compression_mode = COMP_MODE_DEFAULT
    elif h == b'u':
        # we have a more efficient way to declare uncompressed
        h = b''
        compression_mode = COMP_MODE_PLAIN
        deltainfo = drop_u_compression(deltainfo)
    return compression_mode, deltainfo
