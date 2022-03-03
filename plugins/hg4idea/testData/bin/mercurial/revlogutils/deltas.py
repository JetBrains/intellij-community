# revlogdeltas.py - Logic around delta computation for revlog
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
# Copyright 2018 Octobus <contact@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""Helper class to compute deltas stored inside revlogs"""

from __future__ import absolute_import

import collections
import struct

# import stuff from node for others to import from revlog
from ..node import nullrev
from ..i18n import _
from ..pycompat import getattr

from .constants import (
    COMP_MODE_DEFAULT,
    COMP_MODE_INLINE,
    COMP_MODE_PLAIN,
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


class _testrevlog(object):
    """minimalist fake revlog to use in doctests"""

    def __init__(self, data, density=0.5, mingap=0, snapshot=()):
        """data is an list of revision payload boundaries"""
        self._data = data
        self._srdensitythreshold = density
        self._srmingapsize = mingap
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
    ratio) is above `revlog._srdensitythreshold`. No gap smaller than
    `revlog._srmingapsize` is skipped.

    If `targetsize` is set, no chunk larger than `targetsize` will be yield.
    For consistency with other slicing choice, this limit won't go lower than
    `revlog._srmingapsize`.

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
        targetsize = max(targetsize, revlog._srmingapsize)
    # targetsize should not be specified when evaluating delta candidates:
    # * targetsize is used to ensure we stay within specification when reading,
    densityslicing = getattr(revlog.index, 'slicechunktodensity', None)
    if densityslicing is None:
        densityslicing = lambda x, y, z: _slicechunktodensity(revlog, x, y, z)
    for chunk in densityslicing(
        revs, revlog._srdensitythreshold, revlog._srmingapsize
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


def _textfromdelta(fh, revlog, baserev, delta, p1, p2, flags, expectednode):
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
        basetext = revlog.revision(baserev, _df=fh, raw=False)
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
class _deltainfo(object):
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


def isgooddeltainfo(revlog, deltainfo, revinfo):
    """Returns True if the given delta is good. Good means that it is within
    the disk span, disk size, and chain length bounds that we know to be
    performant."""
    if deltainfo is None:
        return False

    # - 'deltainfo.distance' is the distance from the base revision --
    #   bounding it limits the amount of I/O we need to do.
    # - 'deltainfo.compresseddeltalen' is the sum of the total size of
    #   deltas we need to apply -- bounding it limits the amount of CPU
    #   we consume.

    textlen = revinfo.textlen
    defaultmax = textlen * 4
    maxdist = revlog._maxdeltachainspan
    if not maxdist:
        maxdist = deltainfo.distance  # ensure the conditional pass
    maxdist = max(maxdist, defaultmax)

    # Bad delta from read span:
    #
    #   If the span of data read is larger than the maximum allowed.
    #
    #   In the sparse-revlog case, we rely on the associated "sparse reading"
    #   to avoid issue related to the span of data. In theory, it would be
    #   possible to build pathological revlog where delta pattern would lead
    #   to too many reads. However, they do not happen in practice at all. So
    #   we skip the span check entirely.
    if not revlog._sparserevlog and maxdist < deltainfo.distance:
        return False

    # Bad delta from new delta size:
    #
    #   If the delta size is larger than the target text, storing the
    #   delta will be inefficient.
    if textlen < deltainfo.deltalen:
        return False

    # Bad delta from cumulated payload size:
    #
    #   If the sum of delta get larger than K * target text length.
    if textlen * LIMIT_DELTA2TEXT < deltainfo.compresseddeltalen:
        return False

    # Bad delta from chain length:
    #
    #   If the number of delta in the chain gets too high.
    if revlog._maxchainlen and revlog._maxchainlen < deltainfo.chainlen:
        return False

    # bad delta from intermediate snapshot size limit
    #
    #   If an intermediate snapshot size is higher than the limit.  The
    #   limit exist to prevent endless chain of intermediate delta to be
    #   created.
    if (
        deltainfo.snapshotdepth is not None
        and (textlen >> deltainfo.snapshotdepth) < deltainfo.deltalen
    ):
        return False

    # bad delta if new intermediate snapshot is larger than the previous
    # snapshot
    if (
        deltainfo.snapshotdepth
        and revlog.length(deltainfo.base) < deltainfo.deltalen
    ):
        return False

    return True


# If a revision's full text is that much bigger than a base candidate full
# text's, it is very unlikely that it will produce a valid delta. We no longer
# consider these candidates.
LIMIT_BASE2TEXT = 500


def _candidategroups(revlog, textlen, p1, p2, cachedelta):
    """Provides group of revision to be tested as delta base

    This top level function focus on emitting groups with unique and worthwhile
    content. See _raw_candidate_groups for details about the group order.
    """
    # should we try to build a delta?
    if not (len(revlog) and revlog._storedeltachains):
        yield None
        return

    deltalength = revlog.length
    deltaparent = revlog.deltaparent
    sparse = revlog._sparserevlog
    good = None

    deltas_limit = textlen * LIMIT_DELTA2TEXT

    tested = {nullrev}
    candidates = _refinedgroups(revlog, p1, p2, cachedelta)
    while True:
        temptative = candidates.send(good)
        if temptative is None:
            break
        group = []
        for rev in temptative:
            # skip over empty delta (no need to include them in a chain)
            while revlog._generaldelta and not (
                rev == nullrev or rev in tested or deltalength(rev)
            ):
                tested.add(rev)
                rev = deltaparent(rev)
            # no need to try a delta against nullrev, this will be done as a
            # last resort.
            if rev == nullrev:
                continue
            # filter out revision we tested already
            if rev in tested:
                continue
            tested.add(rev)
            # filter out delta base that will never produce good delta
            if deltas_limit < revlog.length(rev):
                continue
            if sparse and revlog.rawsize(rev) < (textlen // LIMIT_BASE2TEXT):
                continue
            # no delta for rawtext-changing revs (see "candelta" for why)
            if revlog.flags(rev) & REVIDX_RAWTEXT_CHANGING_FLAGS:
                continue
            # If we reach here, we are about to build and test a delta.
            # The delta building process will compute the chaininfo in all
            # case, since that computation is cached, it is fine to access it
            # here too.
            chainlen, chainsize = revlog._chaininfo(rev)
            # if chain will be too long, skip base
            if revlog._maxchainlen and chainlen >= revlog._maxchainlen:
                continue
            # if chain already have too much data, skip base
            if deltas_limit < chainsize:
                continue
            if sparse and revlog.upperboundcomp is not None:
                maxcomp = revlog.upperboundcomp
                basenotsnap = (p1, p2, nullrev)
                if rev not in basenotsnap and revlog.issnapshot(rev):
                    snapshotdepth = revlog.snapshotdepth(rev)
                    # If text is significantly larger than the base, we can
                    # expect the resulting delta to be proportional to the size
                    # difference
                    revsize = revlog.rawsize(rev)
                    rawsizedistance = max(textlen - revsize, 0)
                    # use an estimate of the compression upper bound.
                    lowestrealisticdeltalen = rawsizedistance // maxcomp

                    # check the absolute constraint on the delta size
                    snapshotlimit = textlen >> snapshotdepth
                    if snapshotlimit < lowestrealisticdeltalen:
                        # delta lower bound is larger than accepted upper bound
                        continue

                    # check the relative constraint on the delta size
                    revlength = revlog.length(rev)
                    if revlength < lowestrealisticdeltalen:
                        # delta probable lower bound is larger than target base
                        continue

            group.append(rev)
        if group:
            # XXX: in the sparse revlog case, group can become large,
            #      impacting performances. Some bounding or slicing mecanism
            #      would help to reduce this impact.
            good = yield tuple(group)
    yield None


def _findsnapshots(revlog, cache, start_rev):
    """find snapshot from start_rev to tip"""
    if util.safehasattr(revlog.index, b'findsnapshots'):
        revlog.index.findsnapshots(cache, start_rev)
    else:
        deltaparent = revlog.deltaparent
        issnapshot = revlog.issnapshot
        for rev in revlog.revs(start_rev):
            if issnapshot(rev):
                cache[deltaparent(rev)].append(rev)


def _refinedgroups(revlog, p1, p2, cachedelta):
    good = None
    # First we try to reuse a the delta contained in the bundle.
    # (or from the source revlog)
    #
    # This logic only applies to general delta repositories and can be disabled
    # through configuration. Disabling reuse source delta is useful when
    # we want to make sure we recomputed "optimal" deltas.
    if cachedelta and revlog._generaldelta and revlog._lazydeltabase:
        # Assume what we received from the server is a good choice
        # build delta will reuse the cache
        good = yield (cachedelta[0],)
        if good is not None:
            yield None
            return
    snapshots = collections.defaultdict(list)
    for candidates in _rawgroups(revlog, p1, p2, cachedelta, snapshots):
        good = yield candidates
        if good is not None:
            break

    # If sparse revlog is enabled, we can try to refine the available deltas
    if not revlog._sparserevlog:
        yield None
        return

    # if we have a refinable value, try to refine it
    if good is not None and good not in (p1, p2) and revlog.issnapshot(good):
        # refine snapshot down
        previous = None
        while previous != good:
            previous = good
            base = revlog.deltaparent(good)
            if base == nullrev:
                break
            good = yield (base,)
        # refine snapshot up
        if not snapshots:
            _findsnapshots(revlog, snapshots, good + 1)
        previous = None
        while good != previous:
            previous = good
            children = tuple(sorted(c for c in snapshots[good]))
            good = yield children

    # we have found nothing
    yield None


def _rawgroups(revlog, p1, p2, cachedelta, snapshots=None):
    """Provides group of revision to be tested as delta base

    This lower level function focus on emitting delta theorically interresting
    without looking it any practical details.

    The group order aims at providing fast or small candidates first.
    """
    gdelta = revlog._generaldelta
    # gate sparse behind general-delta because of issue6056
    sparse = gdelta and revlog._sparserevlog
    curr = len(revlog)
    prev = curr - 1
    deltachain = lambda rev: revlog._deltachain(rev)[0]

    if gdelta:
        # exclude already lazy tested base if any
        parents = [p for p in (p1, p2) if p != nullrev]

        if not revlog._deltabothparents and len(parents) == 2:
            parents.sort()
            # To minimize the chance of having to build a fulltext,
            # pick first whichever parent is closest to us (max rev)
            yield (parents[1],)
            # then the other one (min rev) if the first did not fit
            yield (parents[0],)
        elif len(parents) > 0:
            # Test all parents (1 or 2), and keep the best candidate
            yield parents

    if sparse and parents:
        if snapshots is None:
            # map: base-rev: snapshot-rev
            snapshots = collections.defaultdict(list)
        # See if we can use an existing snapshot in the parent chains to use as
        # a base for a new intermediate-snapshot
        #
        # search for snapshot in parents delta chain
        # map: snapshot-level: snapshot-rev
        parents_snaps = collections.defaultdict(set)
        candidate_chains = [deltachain(p) for p in parents]
        for chain in candidate_chains:
            for idx, s in enumerate(chain):
                if not revlog.issnapshot(s):
                    break
                parents_snaps[idx].add(s)
        snapfloor = min(parents_snaps[0]) + 1
        _findsnapshots(revlog, snapshots, snapfloor)
        # search for the highest "unrelated" revision
        #
        # Adding snapshots used by "unrelated" revision increase the odd we
        # reuse an independant, yet better snapshot chain.
        #
        # XXX instead of building a set of revisions, we could lazily enumerate
        # over the chains. That would be more efficient, however we stick to
        # simple code for now.
        all_revs = set()
        for chain in candidate_chains:
            all_revs.update(chain)
        other = None
        for r in revlog.revs(prev, snapfloor):
            if r not in all_revs:
                other = r
                break
        if other is not None:
            # To avoid unfair competition, we won't use unrelated intermediate
            # snapshot that are deeper than the ones from the parent delta
            # chain.
            max_depth = max(parents_snaps.keys())
            chain = deltachain(other)
            for idx, s in enumerate(chain):
                if s < snapfloor:
                    continue
                if max_depth < idx:
                    break
                if not revlog.issnapshot(s):
                    break
                parents_snaps[idx].add(s)
        # Test them as possible intermediate snapshot base
        # We test them from highest to lowest level. High level one are more
        # likely to result in small delta
        floor = None
        for idx, snaps in sorted(parents_snaps.items(), reverse=True):
            siblings = set()
            for s in snaps:
                siblings.update(snapshots[s])
            # Before considering making a new intermediate snapshot, we check
            # if an existing snapshot, children of base we consider, would be
            # suitable.
            #
            # It give a change to reuse a delta chain "unrelated" to the
            # current revision instead of starting our own. Without such
            # re-use, topological branches would keep reopening new chains.
            # Creating more and more snapshot as the repository grow.

            if floor is not None:
                # We only do this for siblings created after the one in our
                # parent's delta chain. Those created before has less chances
                # to be valid base since our ancestors had to create a new
                # snapshot.
                siblings = [r for r in siblings if floor < r]
            yield tuple(sorted(siblings))
            # then test the base from our parent's delta chain.
            yield tuple(sorted(snaps))
            floor = min(snaps)
        # No suitable base found in the parent chain, search if any full
        # snapshots emitted since parent's base would be a suitable base for an
        # intermediate snapshot.
        #
        # It give a chance to reuse a delta chain unrelated to the current
        # revisions instead of starting our own. Without such re-use,
        # topological branches would keep reopening new full chains. Creating
        # more and more snapshot as the repository grow.
        yield tuple(snapshots[nullrev])

    if not sparse:
        # other approach failed try against prev to hopefully save us a
        # fulltext.
        yield (prev,)


class deltacomputer(object):
    def __init__(self, revlog):
        self.revlog = revlog

    def buildtext(self, revinfo, fh):
        """Builds a fulltext version of a revision

        revinfo: revisioninfo instance that contains all needed info
        fh:      file handle to either the .i or the .d revlog file,
                 depending on whether it is inlined or not
        """
        btext = revinfo.btext
        if btext[0] is not None:
            return btext[0]

        revlog = self.revlog
        cachedelta = revinfo.cachedelta
        baserev = cachedelta[0]
        delta = cachedelta[1]

        fulltext = btext[0] = _textfromdelta(
            fh,
            revlog,
            baserev,
            delta,
            revinfo.p1,
            revinfo.p2,
            revinfo.flags,
            revinfo.node,
        )
        return fulltext

    def _builddeltadiff(self, base, revinfo, fh):
        revlog = self.revlog
        t = self.buildtext(revinfo, fh)
        if revlog.iscensored(base):
            # deltas based on a censored revision must replace the
            # full content in one patch, so delta works everywhere
            header = mdiff.replacediffheader(revlog.rawsize(base), len(t))
            delta = header + t
        else:
            ptext = revlog.rawdata(base, _df=fh)
            delta = mdiff.textdiff(ptext, t)

        return delta

    def _builddeltainfo(self, revinfo, base, fh):
        # can we use the cached delta?
        revlog = self.revlog
        chainbase = revlog.chainbase(base)
        if revlog._generaldelta:
            deltabase = base
        else:
            deltabase = chainbase
        snapshotdepth = None
        if revlog._sparserevlog and deltabase == nullrev:
            snapshotdepth = 0
        elif revlog._sparserevlog and revlog.issnapshot(deltabase):
            # A delta chain should always be one full snapshot,
            # zero or more semi-snapshots, and zero or more deltas
            p1, p2 = revlog.rev(revinfo.p1), revlog.rev(revinfo.p2)
            if deltabase not in (p1, p2) and revlog.issnapshot(deltabase):
                snapshotdepth = len(revlog._deltachain(deltabase)[0])
        delta = None
        if revinfo.cachedelta:
            cachebase, cachediff = revinfo.cachedelta
            # check if the diff still apply
            currentbase = cachebase
            while (
                currentbase != nullrev
                and currentbase != base
                and self.revlog.length(currentbase) == 0
            ):
                currentbase = self.revlog.deltaparent(currentbase)
            if self.revlog._lazydelta and currentbase == base:
                delta = revinfo.cachedelta[1]
        if delta is None:
            delta = self._builddeltadiff(base, revinfo, fh)
        # snapshotdept need to be neither None nor 0 level snapshot
        if revlog.upperboundcomp is not None and snapshotdepth:
            lowestrealisticdeltalen = len(delta) // revlog.upperboundcomp
            snapshotlimit = revinfo.textlen >> snapshotdepth
            if snapshotlimit < lowestrealisticdeltalen:
                return None
            if revlog.length(base) < lowestrealisticdeltalen:
                return None
        header, data = revlog.compress(delta)
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

    def _fullsnapshotinfo(self, fh, revinfo, curr):
        rawtext = self.buildtext(revinfo, fh)
        data = self.revlog.compress(rawtext)
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

    def finddeltainfo(self, revinfo, fh, excluded_bases=None, target_rev=None):
        """Find an acceptable delta against a candidate revision

        revinfo: information about the revision (instance of _revisioninfo)
        fh:      file handle to either the .i or the .d revlog file,
                 depending on whether it is inlined or not

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

        if not revinfo.textlen:
            return self._fullsnapshotinfo(fh, revinfo, target_rev)

        if excluded_bases is None:
            excluded_bases = set()

        # no delta for flag processor revision (see "candelta" for why)
        # not calling candelta since only one revision needs test, also to
        # avoid overhead fetching flags again.
        if revinfo.flags & REVIDX_RAWTEXT_CHANGING_FLAGS:
            return self._fullsnapshotinfo(fh, revinfo, target_rev)

        cachedelta = revinfo.cachedelta
        p1 = revinfo.p1
        p2 = revinfo.p2
        revlog = self.revlog

        deltainfo = None
        p1r, p2r = revlog.rev(p1), revlog.rev(p2)
        groups = _candidategroups(
            self.revlog, revinfo.textlen, p1r, p2r, cachedelta
        )
        candidaterevs = next(groups)
        while candidaterevs is not None:
            nominateddeltas = []
            if deltainfo is not None:
                # if we already found a good delta,
                # challenge it against refined candidates
                nominateddeltas.append(deltainfo)
            for candidaterev in candidaterevs:
                if candidaterev in excluded_bases:
                    continue
                if candidaterev >= target_rev:
                    continue
                candidatedelta = self._builddeltainfo(revinfo, candidaterev, fh)
                if candidatedelta is not None:
                    if isgooddeltainfo(self.revlog, candidatedelta, revinfo):
                        nominateddeltas.append(candidatedelta)
            if nominateddeltas:
                deltainfo = min(nominateddeltas, key=lambda x: x.deltalen)
            if deltainfo is not None:
                candidaterevs = groups.send(deltainfo.base)
            else:
                candidaterevs = next(groups)

        if deltainfo is None:
            deltainfo = self._fullsnapshotinfo(fh, revinfo, target_rev)
        return deltainfo


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
