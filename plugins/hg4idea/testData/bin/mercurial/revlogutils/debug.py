# revlogutils/debug.py - utility used for revlog debuging
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
# Copyright 2022 Octobus <contact@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import collections
import string

from .. import (
    mdiff,
    node as nodemod,
    revlogutils,
)

from . import (
    constants,
    deltas as deltautil,
)

INDEX_ENTRY_DEBUG_COLUMN = []

NODE_SIZE = object()


class _column_base:
    """constains the definition of a revlog column

    name:         the column header,
    value_func:   the function called to get a value,
    size:         the width of the column,
    verbose_only: only include the column in verbose mode.
    """

    def __init__(self, name, value_func, size=None, verbose=False):
        self.name = name
        self.value_func = value_func
        if size is not NODE_SIZE:
            if size is None:
                size = 8  # arbitrary default
            size = max(len(name), size)
        self._size = size
        self.verbose_only = verbose

    def get_size(self, node_size):
        if self._size is NODE_SIZE:
            return node_size
        else:
            return self._size


def debug_column(name, size=None, verbose=False):
    """decorated function is registered as a column

    name: the name of the column,
    size: the expected size of the column.
    """

    def register(func):
        entry = _column_base(
            name=name,
            value_func=func,
            size=size,
            verbose=verbose,
        )
        INDEX_ENTRY_DEBUG_COLUMN.append(entry)
        return entry

    return register


@debug_column(b"rev", size=6)
def _rev(index, rev, entry, hexfn):
    return b"%d" % rev


@debug_column(b"rank", size=6, verbose=True)
def rank(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_RANK]


@debug_column(b"linkrev", size=6)
def _linkrev(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_LINK_REV]


@debug_column(b"nodeid", size=NODE_SIZE)
def _nodeid(index, rev, entry, hexfn):
    return hexfn(entry[constants.ENTRY_NODE_ID])


@debug_column(b"p1-rev", size=6, verbose=True)
def _p1_rev(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_PARENT_1]


@debug_column(b"p1-nodeid", size=NODE_SIZE)
def _p1_node(index, rev, entry, hexfn):
    parent = entry[constants.ENTRY_PARENT_1]
    p_entry = index[parent]
    return hexfn(p_entry[constants.ENTRY_NODE_ID])


@debug_column(b"p2-rev", size=6, verbose=True)
def _p2_rev(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_PARENT_2]


@debug_column(b"p2-nodeid", size=NODE_SIZE)
def _p2_node(index, rev, entry, hexfn):
    parent = entry[constants.ENTRY_PARENT_2]
    p_entry = index[parent]
    return hexfn(p_entry[constants.ENTRY_NODE_ID])


@debug_column(b"full-size", size=20, verbose=True)
def full_size(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_DATA_UNCOMPRESSED_LENGTH]


@debug_column(b"delta-base", size=6, verbose=True)
def delta_base(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_DELTA_BASE]


@debug_column(b"flags", size=2, verbose=True)
def flags(index, rev, entry, hexfn):
    field = entry[constants.ENTRY_DATA_OFFSET]
    field &= 0xFFFF
    return b"%d" % field


@debug_column(b"comp-mode", size=4, verbose=True)
def compression_mode(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_DATA_COMPRESSION_MODE]


@debug_column(b"data-offset", size=20, verbose=True)
def data_offset(index, rev, entry, hexfn):
    field = entry[constants.ENTRY_DATA_OFFSET]
    field >>= 16
    return b"%d" % field


@debug_column(b"chunk-size", size=10, verbose=True)
def data_chunk_size(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_DATA_COMPRESSED_LENGTH]


@debug_column(b"sd-comp-mode", size=7, verbose=True)
def sidedata_compression_mode(index, rev, entry, hexfn):
    compression = entry[constants.ENTRY_SIDEDATA_COMPRESSION_MODE]
    if compression == constants.COMP_MODE_PLAIN:
        return b"plain"
    elif compression == constants.COMP_MODE_DEFAULT:
        return b"default"
    elif compression == constants.COMP_MODE_INLINE:
        return b"inline"
    else:
        return b"%d" % compression


@debug_column(b"sidedata-offset", size=20, verbose=True)
def sidedata_offset(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_SIDEDATA_OFFSET]


@debug_column(b"sd-chunk-size", size=10, verbose=True)
def sidedata_chunk_size(index, rev, entry, hexfn):
    return b"%d" % entry[constants.ENTRY_SIDEDATA_COMPRESSED_LENGTH]


def debug_index(
    ui,
    repo,
    formatter,
    revlog,
    full_node,
):
    """display index data for a revlog"""
    if full_node:
        hexfn = nodemod.hex
    else:
        hexfn = nodemod.short

    idlen = 12
    for i in revlog:
        idlen = len(hexfn(revlog.node(i)))
        break

    fm = formatter

    header_pieces = []
    for column in INDEX_ENTRY_DEBUG_COLUMN:
        if column.verbose_only and not ui.verbose:
            continue
        size = column.get_size(idlen)
        name = column.name
        header_pieces.append(name.rjust(size))

    fm.plain(b' '.join(header_pieces) + b'\n')

    index = revlog.index

    for rev in revlog:
        fm.startitem()
        entry = index[rev]
        first = True
        for column in INDEX_ENTRY_DEBUG_COLUMN:
            if column.verbose_only and not ui.verbose:
                continue
            if not first:
                fm.plain(b' ')
            first = False

            size = column.get_size(idlen)
            value = column.value_func(index, rev, entry, hexfn)
            display = b"%%%ds" % size
            fm.write(column.name, display, value)
        fm.plain(b'\n')

    fm.end()


def dump(ui, revlog):
    """perform the work for `hg debugrevlog --dump"""
    # XXX seems redundant with debug index ?
    r = revlog
    numrevs = len(r)
    ui.write(
        (
            b"# rev p1rev p2rev start   end deltastart base   p1   p2"
            b" rawsize totalsize compression heads chainlen\n"
        )
    )
    ts = 0
    heads = set()

    for rev in range(numrevs):
        dbase = r.deltaparent(rev)
        if dbase == -1:
            dbase = rev
        cbase = r.chainbase(rev)
        clen = r.chainlen(rev)
        p1, p2 = r.parentrevs(rev)
        rs = r.rawsize(rev)
        ts = ts + rs
        heads -= set(r.parentrevs(rev))
        heads.add(rev)
        try:
            compression = ts / r.end(rev)
        except ZeroDivisionError:
            compression = 0
        ui.write(
            b"%5d %5d %5d %5d %5d %10d %4d %4d %4d %7d %9d "
            b"%11d %5d %8d\n"
            % (
                rev,
                p1,
                p2,
                r.start(rev),
                r.end(rev),
                r.start(dbase),
                r.start(cbase),
                r.start(p1),
                r.start(p2),
                rs,
                ts,
                compression,
                len(heads),
                clen,
            )
        )


def debug_revlog(ui, revlog):
    """code for `hg debugrevlog`"""
    r = revlog
    format = r._format_version
    v = r._format_flags
    flags = []
    gdelta = False
    if v & constants.FLAG_INLINE_DATA:
        flags.append(b'inline')
    if v & constants.FLAG_GENERALDELTA:
        gdelta = True
        flags.append(b'generaldelta')
    if not flags:
        flags = [b'(none)']

    ### the total size of stored content if incompressed.
    full_text_total_size = 0
    ### tracks merge vs single parent
    nummerges = 0

    ### tracks ways the "delta" are build
    # nodelta
    numempty = 0
    numemptytext = 0
    numemptydelta = 0
    # full file content
    numfull = 0
    # intermediate snapshot against a prior snapshot
    numsemi = 0
    # snapshot count per depth
    numsnapdepth = collections.defaultdict(lambda: 0)
    # number of snapshots with a non-ancestor delta
    numsnapdepth_nad = collections.defaultdict(lambda: 0)
    # delta against previous revision
    numprev = 0
    # delta against prev, where prev is a non-ancestor
    numprev_nad = 0
    # delta against first or second parent (not prev)
    nump1 = 0
    nump2 = 0
    # delta against neither prev nor parents
    numother = 0
    # delta against other that is a non-ancestor
    numother_nad = 0
    # delta against prev that are also first or second parent
    # (details of `numprev`)
    nump1prev = 0
    nump2prev = 0

    # data about delta chain of each revs
    chainlengths = []
    chainbases = []
    chainspans = []

    # data about each revision
    datasize = [None, 0, 0]
    fullsize = [None, 0, 0]
    semisize = [None, 0, 0]
    # snapshot count per depth
    snapsizedepth = collections.defaultdict(lambda: [None, 0, 0])
    deltasize = [None, 0, 0]
    chunktypecounts = {}
    chunktypesizes = {}

    def addsize(size, l):
        if l[0] is None or size < l[0]:
            l[0] = size
        if size > l[1]:
            l[1] = size
        l[2] += size

    with r.reading():
        numrevs = len(r)
        for rev in range(numrevs):
            p1, p2 = r.parentrevs(rev)
            delta = r.deltaparent(rev)
            if format > 0:
                s = r.rawsize(rev)
                full_text_total_size += s
                addsize(s, datasize)
            if p2 != nodemod.nullrev:
                nummerges += 1
            size = r.length(rev)
            if delta == nodemod.nullrev:
                chainlengths.append(0)
                chainbases.append(r.start(rev))
                chainspans.append(size)
                if size == 0:
                    numempty += 1
                    numemptytext += 1
                else:
                    numfull += 1
                    numsnapdepth[0] += 1
                    addsize(size, fullsize)
                    addsize(size, snapsizedepth[0])
            else:
                nad = (
                    delta != p1
                    and delta != p2
                    and not r.isancestorrev(delta, rev)
                )
                chainlengths.append(chainlengths[delta] + 1)
                baseaddr = chainbases[delta]
                revaddr = r.start(rev)
                chainbases.append(baseaddr)
                chainspans.append((revaddr - baseaddr) + size)
                if size == 0:
                    numempty += 1
                    numemptydelta += 1
                elif r.issnapshot(rev):
                    addsize(size, semisize)
                    numsemi += 1
                    depth = r.snapshotdepth(rev)
                    numsnapdepth[depth] += 1
                    if nad:
                        numsnapdepth_nad[depth] += 1
                    addsize(size, snapsizedepth[depth])
                else:
                    addsize(size, deltasize)
                    if delta == rev - 1:
                        numprev += 1
                        if delta == p1:
                            nump1prev += 1
                        elif delta == p2:
                            nump2prev += 1
                        elif nad:
                            numprev_nad += 1
                    elif delta == p1:
                        nump1 += 1
                    elif delta == p2:
                        nump2 += 1
                    elif delta != nodemod.nullrev:
                        numother += 1
                        numother_nad += 1

            # Obtain data on the raw chunks in the revlog.
            if hasattr(r, '_inner'):
                segment = r._inner.get_segment_for_revs(rev, rev)[1]
            else:
                segment = r._revlog._getsegmentforrevs(rev, rev)[1]
            if segment:
                chunktype = bytes(segment[0:1])
            else:
                chunktype = b'empty'

            if chunktype not in chunktypecounts:
                chunktypecounts[chunktype] = 0
                chunktypesizes[chunktype] = 0

            chunktypecounts[chunktype] += 1
            chunktypesizes[chunktype] += size

    # Adjust size min value for empty cases
    for size in (datasize, fullsize, semisize, deltasize):
        if size[0] is None:
            size[0] = 0

    numdeltas = numrevs - numfull - numempty - numsemi
    numoprev = numprev - nump1prev - nump2prev - numprev_nad
    num_other_ancestors = numother - numother_nad
    totalrawsize = datasize[2]
    datasize[2] /= numrevs
    fulltotal = fullsize[2]
    if numfull == 0:
        fullsize[2] = 0
    else:
        fullsize[2] /= numfull
    semitotal = semisize[2]
    snaptotal = {}
    if numsemi > 0:
        semisize[2] /= numsemi
    for depth in snapsizedepth:
        snaptotal[depth] = snapsizedepth[depth][2]
        snapsizedepth[depth][2] /= numsnapdepth[depth]

    deltatotal = deltasize[2]
    if numdeltas > 0:
        deltasize[2] /= numdeltas
    totalsize = fulltotal + semitotal + deltatotal
    avgchainlen = sum(chainlengths) / numrevs
    maxchainlen = max(chainlengths)
    maxchainspan = max(chainspans)
    compratio = 1
    if totalsize:
        compratio = totalrawsize / totalsize

    basedfmtstr = b'%%%dd\n'
    basepcfmtstr = b'%%%dd %s(%%5.2f%%%%)\n'

    def dfmtstr(max):
        return basedfmtstr % len(str(max))

    def pcfmtstr(max, padding=0):
        return basepcfmtstr % (len(str(max)), b' ' * padding)

    def pcfmt(value, total):
        if total:
            return (value, 100 * float(value) / total)
        else:
            return value, 100.0

    ui.writenoi18n(b'format : %d\n' % format)
    ui.writenoi18n(b'flags  : %s\n' % b', '.join(flags))

    ui.write(b'\n')
    fmt = pcfmtstr(totalsize)
    fmt2 = dfmtstr(totalsize)
    ui.writenoi18n(b'revisions     : ' + fmt2 % numrevs)
    ui.writenoi18n(b'    merges    : ' + fmt % pcfmt(nummerges, numrevs))
    ui.writenoi18n(
        b'    normal    : ' + fmt % pcfmt(numrevs - nummerges, numrevs)
    )
    ui.writenoi18n(b'revisions     : ' + fmt2 % numrevs)
    ui.writenoi18n(b'    empty     : ' + fmt % pcfmt(numempty, numrevs))
    ui.writenoi18n(
        b'                   text  : '
        + fmt % pcfmt(numemptytext, numemptytext + numemptydelta)
    )
    ui.writenoi18n(
        b'                   delta : '
        + fmt % pcfmt(numemptydelta, numemptytext + numemptydelta)
    )
    ui.writenoi18n(
        b'    snapshot  : ' + fmt % pcfmt(numfull + numsemi, numrevs)
    )
    for depth in sorted(numsnapdepth):
        base = b'      lvl-%-3d :       ' % depth
        count = fmt % pcfmt(numsnapdepth[depth], numrevs)
        pieces = [base, count]
        if numsnapdepth_nad[depth]:
            pieces[-1] = count = count[:-1]  # drop the final '\n'
            more = b'  non-ancestor-bases: '
            anc_count = fmt
            anc_count %= pcfmt(numsnapdepth_nad[depth], numsnapdepth[depth])
            pieces.append(more)
            pieces.append(anc_count)
        ui.write(b''.join(pieces))
    ui.writenoi18n(b'    deltas    : ' + fmt % pcfmt(numdeltas, numrevs))
    ui.writenoi18n(b'revision size : ' + fmt2 % totalsize)
    ui.writenoi18n(
        b'    snapshot  : ' + fmt % pcfmt(fulltotal + semitotal, totalsize)
    )
    for depth in sorted(numsnapdepth):
        ui.write(
            (b'      lvl-%-3d :       ' % depth)
            + fmt % pcfmt(snaptotal[depth], totalsize)
        )
    ui.writenoi18n(b'    deltas    : ' + fmt % pcfmt(deltatotal, totalsize))

    letters = string.ascii_letters.encode('ascii')

    def fmtchunktype(chunktype):
        if chunktype == b'empty':
            return b'    %s     : ' % chunktype
        elif chunktype in letters:
            return b'    0x%s (%s)  : ' % (nodemod.hex(chunktype), chunktype)
        else:
            return b'    0x%s      : ' % nodemod.hex(chunktype)

    ui.write(b'\n')
    ui.writenoi18n(b'chunks        : ' + fmt2 % numrevs)
    for chunktype in sorted(chunktypecounts):
        ui.write(fmtchunktype(chunktype))
        ui.write(fmt % pcfmt(chunktypecounts[chunktype], numrevs))
    ui.writenoi18n(b'chunks size   : ' + fmt2 % totalsize)
    for chunktype in sorted(chunktypecounts):
        ui.write(fmtchunktype(chunktype))
        ui.write(fmt % pcfmt(chunktypesizes[chunktype], totalsize))

    ui.write(b'\n')
    b_total = b"%d" % full_text_total_size
    p_total = []
    while len(b_total) > 3:
        p_total.append(b_total[-3:])
        b_total = b_total[:-3]
    p_total.append(b_total)
    p_total.reverse()
    b_total = b' '.join(p_total)

    ui.write(b'\n')
    ui.writenoi18n(b'total-stored-content: %s bytes\n' % b_total)
    ui.write(b'\n')
    fmt = dfmtstr(max(avgchainlen, maxchainlen, maxchainspan, compratio))
    ui.writenoi18n(b'avg chain length  : ' + fmt % avgchainlen)
    ui.writenoi18n(b'max chain length  : ' + fmt % maxchainlen)
    ui.writenoi18n(b'max chain reach   : ' + fmt % maxchainspan)
    ui.writenoi18n(b'compression ratio : ' + fmt % compratio)

    if format > 0:
        ui.write(b'\n')
        ui.writenoi18n(
            b'uncompressed data size (min/max/avg) : %d / %d / %d\n'
            % tuple(datasize)
        )
    ui.writenoi18n(
        b'full revision size (min/max/avg)     : %d / %d / %d\n'
        % tuple(fullsize)
    )
    ui.writenoi18n(
        b'inter-snapshot size (min/max/avg)    : %d / %d / %d\n'
        % tuple(semisize)
    )
    for depth in sorted(snapsizedepth):
        if depth == 0:
            continue
        ui.writenoi18n(
            b'    level-%-3d (min/max/avg)          : %d / %d / %d\n'
            % ((depth,) + tuple(snapsizedepth[depth]))
        )
    ui.writenoi18n(
        b'delta size (min/max/avg)             : %d / %d / %d\n'
        % tuple(deltasize)
    )

    if numdeltas > 0:
        ui.write(b'\n')
        fmt = pcfmtstr(numdeltas)
        fmt2 = pcfmtstr(numdeltas, 4)
        ui.writenoi18n(
            b'deltas against prev  : ' + fmt % pcfmt(numprev, numdeltas)
        )
        if numprev > 0:
            ui.writenoi18n(
                b'    where prev = p1  : ' + fmt2 % pcfmt(nump1prev, numprev)
            )
            ui.writenoi18n(
                b'    where prev = p2  : ' + fmt2 % pcfmt(nump2prev, numprev)
            )
            ui.writenoi18n(
                b'    other-ancestor   : ' + fmt2 % pcfmt(numoprev, numprev)
            )
            ui.writenoi18n(
                b'    unrelated        : ' + fmt2 % pcfmt(numoprev, numprev)
            )
        if gdelta:
            ui.writenoi18n(
                b'deltas against p1    : ' + fmt % pcfmt(nump1, numdeltas)
            )
            ui.writenoi18n(
                b'deltas against p2    : ' + fmt % pcfmt(nump2, numdeltas)
            )
            ui.writenoi18n(
                b'deltas against ancs  : '
                + fmt % pcfmt(num_other_ancestors, numdeltas)
            )
            ui.writenoi18n(
                b'deltas against other : '
                + fmt % pcfmt(numother_nad, numdeltas)
            )


def debug_delta_find(ui, revlog, rev, base_rev=nodemod.nullrev):
    """display the search process for a delta"""
    deltacomputer = deltautil.deltacomputer(
        revlog,
        write_debug=ui.write,
        debug_search=not ui.quiet,
    )

    node = revlog.node(rev)
    p1r, p2r = revlog.parentrevs(rev)
    p1 = revlog.node(p1r)
    p2 = revlog.node(p2r)
    full_text = revlog.revision(rev)
    btext = [full_text]
    textlen = len(btext[0])
    cachedelta = None
    flags = revlog.flags(rev)

    if base_rev != nodemod.nullrev:
        base_text = revlog.revision(base_rev)
        delta = mdiff.textdiff(base_text, full_text)

        cachedelta = (base_rev, delta, constants.DELTA_BASE_REUSE_TRY)
        btext = [None]

    revinfo = revlogutils.revisioninfo(
        node,
        p1,
        p2,
        btext,
        textlen,
        cachedelta,
        flags,
    )

    fh = revlog._datafp()
    deltacomputer.finddeltainfo(revinfo, fh, target_rev=rev)


def debug_revlog_stats(
    repo, fm, changelog: bool, manifest: bool, filelogs: bool
):
    """Format revlog statistics for debugging purposes

    fm: the output formatter.
    """
    fm.plain(b'rev-count   data-size inl type      target \n')

    revlog_entries = [e for e in repo.store.walk() if e.is_revlog]
    revlog_entries.sort(key=lambda e: (e.revlog_type, e.target_id))

    for entry in revlog_entries:
        if not changelog and entry.is_changelog:
            continue
        elif not manifest and entry.is_manifestlog:
            continue
        elif not filelogs and entry.is_filelog:
            continue
        rlog = entry.get_revlog_instance(repo).get_revlog()
        fm.startitem()
        nb_rev = len(rlog)
        inline = rlog._inline
        data_size = rlog._get_data_offset(nb_rev - 1)

        target = rlog.target
        revlog_type = b'unknown'
        revlog_target = b''
        if target[0] == constants.KIND_CHANGELOG:
            revlog_type = b'changelog'
        elif target[0] == constants.KIND_MANIFESTLOG:
            revlog_type = b'manifest'
            revlog_target = target[1]
        elif target[0] == constants.KIND_FILELOG:
            revlog_type = b'file'
            revlog_target = target[1]

        fm.write(b'revlog.rev-count', b'%9d', nb_rev)
        fm.write(b'revlog.data-size', b'%12d', data_size)

        fm.write(b'revlog.inline', b' %-3s', b'yes' if inline else b'no')
        fm.write(b'revlog.type', b' %-9s', revlog_type)
        fm.write(b'revlog.target', b' %s', revlog_target)

        fm.plain(b'\n')


class DeltaChainAuditor:
    def __init__(self, revlog):
        self._revlog = revlog
        self._index = self._revlog.index
        self._generaldelta = revlog.delta_config.general_delta
        self._chain_size_cache = {}
        # security to avoid crash on corrupted revlogs
        self._total_revs = len(self._index)

    def revinfo(self, rev, size_info=True, dist_info=True, sparse_info=True):
        e = self._index[rev]
        compsize = e[constants.ENTRY_DATA_COMPRESSED_LENGTH]
        uncompsize = e[constants.ENTRY_DATA_UNCOMPRESSED_LENGTH]

        base = e[constants.ENTRY_DELTA_BASE]
        p1 = e[constants.ENTRY_PARENT_1]
        p2 = e[constants.ENTRY_PARENT_2]

        # If the parents of a revision has an empty delta, we never try to
        # delta against that parent, but directly against the delta base of
        # that parent (recursively). It avoids adding a useless entry in the
        # chain.
        #
        # However we need to detect that as a special case for delta-type, that
        # is not simply "other".
        p1_base = p1
        if p1 != nodemod.nullrev and p1 < self._total_revs:
            e1 = self._index[p1]
            while e1[constants.ENTRY_DATA_COMPRESSED_LENGTH] == 0:
                new_base = e1[constants.ENTRY_DELTA_BASE]
                if (
                    new_base == p1_base
                    or new_base == nodemod.nullrev
                    or new_base >= self._total_revs
                ):
                    break
                p1_base = new_base
                e1 = self._index[p1_base]
        p2_base = p2
        if p2 != nodemod.nullrev and p2 < self._total_revs:
            e2 = self._index[p2]
            while e2[constants.ENTRY_DATA_COMPRESSED_LENGTH] == 0:
                new_base = e2[constants.ENTRY_DELTA_BASE]
                if (
                    new_base == p2_base
                    or new_base == nodemod.nullrev
                    or new_base >= self._total_revs
                ):
                    break
                p2_base = new_base
                e2 = self._index[p2_base]

        if self._generaldelta:
            if base == p1:
                deltatype = b'p1'
            elif base == p2:
                deltatype = b'p2'
            elif base == rev:
                deltatype = b'base'
            elif base == p1_base:
                deltatype = b'skip1'
            elif base == p2_base:
                deltatype = b'skip2'
            elif self._revlog.issnapshot(rev):
                deltatype = b'snap'
            elif base == rev - 1:
                deltatype = b'prev'
            else:
                deltatype = b'other'
        else:
            if base == rev:
                deltatype = b'base'
            else:
                deltatype = b'prev'

        chain = self._revlog._deltachain(rev)[0]

        data = {
            'p1': p1,
            'p2': p2,
            'compressed_size': compsize,
            'uncompressed_size': uncompsize,
            'deltatype': deltatype,
            'chain': chain,
        }

        if size_info or dist_info or sparse_info:
            chain_size = 0
            for iter_rev in reversed(chain):
                cached = self._chain_size_cache.get(iter_rev)
                if cached is not None:
                    chain_size += cached
                    break
                e = self._index[iter_rev]
                chain_size += e[constants.ENTRY_DATA_COMPRESSED_LENGTH]
            self._chain_size_cache[rev] = chain_size
            data['chain_size'] = chain_size

        return data


def debug_delta_chain(
    revlog,
    revs=None,
    size_info=True,
    dist_info=True,
    sparse_info=True,
):
    auditor = DeltaChainAuditor(revlog)
    r = revlog
    start = r.start
    length = r.length
    withsparseread = revlog.data_config.with_sparse_read

    header = (
        b'    rev'
        b'      p1'
        b'      p2'
        b'  chain#'
        b' chainlen'
        b'     prev'
        b'   delta'
    )
    if size_info:
        header += b'       size' b'    rawsize' b'  chainsize' b'     ratio'
    if dist_info:
        header += b'   lindist' b' extradist' b' extraratio'
    if withsparseread and sparse_info:
        header += b'   readsize' b' largestblk' b' rddensity' b' srchunks'
    header += b'\n'
    yield header

    if revs is None:
        all_revs = iter(r)
    else:
        revlog_size = len(r)
        all_revs = sorted(rev for rev in revs if rev < revlog_size)

    chainbases = {}
    for rev in all_revs:
        info = auditor.revinfo(
            rev,
            size_info=size_info,
            dist_info=dist_info,
            sparse_info=sparse_info,
        )
        comp = info['compressed_size']
        uncomp = info['uncompressed_size']
        chain = info['chain']
        chainbase = chain[0]
        chainid = chainbases.setdefault(chainbase, len(chainbases) + 1)
        if dist_info:
            basestart = start(chainbase)
            revstart = start(rev)
            lineardist = revstart + comp - basestart
            extradist = lineardist - info['chain_size']
        try:
            prevrev = chain[-2]
        except IndexError:
            prevrev = -1

        if size_info:
            chainsize = info['chain_size']
            if uncomp != 0:
                chainratio = float(chainsize) / float(uncomp)
            else:
                chainratio = chainsize

        if dist_info:
            if chainsize != 0:
                extraratio = float(extradist) / float(chainsize)
            else:
                extraratio = extradist

        # label, display-format, data-key, value
        entry = [
            (b'rev', b'%7d', 'rev', rev),
            (b'p1', b'%7d', 'p1', info['p1']),
            (b'p2', b'%7d', 'p2', info['p2']),
            (b'chainid', b'%7d', 'chainid', chainid),
            (b'chainlen', b'%8d', 'chainlen', len(chain)),
            (b'prevrev', b'%8d', 'prevrev', prevrev),
            (b'deltatype', b'%7s', 'deltatype', info['deltatype']),
        ]
        if size_info:
            entry.extend(
                [
                    (b'compsize', b'%10d', 'compsize', comp),
                    (b'uncompsize', b'%10d', 'uncompsize', uncomp),
                    (b'chainsize', b'%10d', 'chainsize', chainsize),
                    (b'chainratio', b'%9.5f', 'chainratio', chainratio),
                ]
            )
        if dist_info:
            entry.extend(
                [
                    (b'lindist', b'%9d', 'lindist', lineardist),
                    (b'extradist', b'%9d', 'extradist', extradist),
                    (b'extraratio', b'%10.5f', 'extraratio', extraratio),
                ]
            )
        if withsparseread and sparse_info:
            chainsize = info['chain_size']
            readsize = 0
            largestblock = 0
            srchunks = 0

            for revschunk in deltautil.slicechunk(r, chain):
                srchunks += 1
                blkend = start(revschunk[-1]) + length(revschunk[-1])
                blksize = blkend - start(revschunk[0])

                readsize += blksize
                if largestblock < blksize:
                    largestblock = blksize

            if readsize:
                readdensity = float(chainsize) / float(readsize)
            else:
                readdensity = 1
            entry.extend(
                [
                    (b'readsize', b'%10d', 'readsize', readsize),
                    (b'largestblock', b'%10d', 'largestblock', largestblock),
                    (b'readdensity', b'%9.5f', 'readdensity', readdensity),
                    (b'srchunks', b'%8d', 'srchunks', srchunks),
                ]
            )
        yield entry
