# Copyright (C) 2004, 2005 Canonical Ltd
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, see <http://www.gnu.org/licenses/>.

# mbp: "you know that thing where cvs gives you conflict markers?"
# s: "i hate that."

from __future__ import absolute_import

from .i18n import _
from .node import nullrev
from . import (
    error,
    mdiff,
    pycompat,
    util,
)
from .utils import stringutil


class CantReprocessAndShowBase(Exception):
    pass


def intersect(ra, rb):
    """Given two ranges return the range where they intersect or None.

    >>> intersect((0, 10), (0, 6))
    (0, 6)
    >>> intersect((0, 10), (5, 15))
    (5, 10)
    >>> intersect((0, 10), (10, 15))
    >>> intersect((0, 9), (10, 15))
    >>> intersect((0, 9), (7, 15))
    (7, 9)
    """
    assert ra[0] <= ra[1]
    assert rb[0] <= rb[1]

    sa = max(ra[0], rb[0])
    sb = min(ra[1], rb[1])
    if sa < sb:
        return sa, sb
    else:
        return None


def compare_range(a, astart, aend, b, bstart, bend):
    """Compare a[astart:aend] == b[bstart:bend], without slicing."""
    if (aend - astart) != (bend - bstart):
        return False
    for ia, ib in zip(
        pycompat.xrange(astart, aend), pycompat.xrange(bstart, bend)
    ):
        if a[ia] != b[ib]:
            return False
    else:
        return True


class Merge3Text(object):
    """3-way merge of texts.

    Given strings BASE, OTHER, THIS, tries to produce a combined text
    incorporating the changes from both BASE->OTHER and BASE->THIS."""

    def __init__(self, basetext, atext, btext, base=None, a=None, b=None):
        self.basetext = basetext
        self.atext = atext
        self.btext = btext
        if base is None:
            base = mdiff.splitnewlines(basetext)
        if a is None:
            a = mdiff.splitnewlines(atext)
        if b is None:
            b = mdiff.splitnewlines(btext)
        self.base = base
        self.a = a
        self.b = b

    def merge_lines(
        self,
        name_a=None,
        name_b=None,
        name_base=None,
        start_marker=b'<<<<<<<',
        mid_marker=b'=======',
        end_marker=b'>>>>>>>',
        base_marker=None,
        localorother=None,
        minimize=False,
    ):
        """Return merge in cvs-like form."""
        self.conflicts = False
        newline = b'\n'
        if len(self.a) > 0:
            if self.a[0].endswith(b'\r\n'):
                newline = b'\r\n'
            elif self.a[0].endswith(b'\r'):
                newline = b'\r'
        if name_a and start_marker:
            start_marker = start_marker + b' ' + name_a
        if name_b and end_marker:
            end_marker = end_marker + b' ' + name_b
        if name_base and base_marker:
            base_marker = base_marker + b' ' + name_base
        merge_regions = self.merge_regions()
        if minimize:
            merge_regions = self.minimize(merge_regions)
        for t in merge_regions:
            what = t[0]
            if what == b'unchanged':
                for i in range(t[1], t[2]):
                    yield self.base[i]
            elif what == b'a' or what == b'same':
                for i in range(t[1], t[2]):
                    yield self.a[i]
            elif what == b'b':
                for i in range(t[1], t[2]):
                    yield self.b[i]
            elif what == b'conflict':
                if localorother == b'local':
                    for i in range(t[3], t[4]):
                        yield self.a[i]
                elif localorother == b'other':
                    for i in range(t[5], t[6]):
                        yield self.b[i]
                else:
                    self.conflicts = True
                    if start_marker is not None:
                        yield start_marker + newline
                    for i in range(t[3], t[4]):
                        yield self.a[i]
                    if base_marker is not None:
                        yield base_marker + newline
                        for i in range(t[1], t[2]):
                            yield self.base[i]
                    if mid_marker is not None:
                        yield mid_marker + newline
                    for i in range(t[5], t[6]):
                        yield self.b[i]
                    if end_marker is not None:
                        yield end_marker + newline
            else:
                raise ValueError(what)

    def merge_groups(self):
        """Yield sequence of line groups.  Each one is a tuple:

        'unchanged', lines
             Lines unchanged from base

        'a', lines
             Lines taken from a

        'same', lines
             Lines taken from a (and equal to b)

        'b', lines
             Lines taken from b

        'conflict', base_lines, a_lines, b_lines
             Lines from base were changed to either a or b and conflict.
        """
        for t in self.merge_regions():
            what = t[0]
            if what == b'unchanged':
                yield what, self.base[t[1] : t[2]]
            elif what == b'a' or what == b'same':
                yield what, self.a[t[1] : t[2]]
            elif what == b'b':
                yield what, self.b[t[1] : t[2]]
            elif what == b'conflict':
                yield (
                    what,
                    self.base[t[1] : t[2]],
                    self.a[t[3] : t[4]],
                    self.b[t[5] : t[6]],
                )
            else:
                raise ValueError(what)

    def merge_regions(self):
        """Return sequences of matching and conflicting regions.

        This returns tuples, where the first value says what kind we
        have:

        'unchanged', start, end
             Take a region of base[start:end]

        'same', astart, aend
             b and a are different from base but give the same result

        'a', start, end
             Non-clashing insertion from a[start:end]

        'conflict', zstart, zend, astart, aend, bstart, bend
            Conflict between a and b, with z as common ancestor

        Method is as follows:

        The two sequences align only on regions which match the base
        and both descendants.  These are found by doing a two-way diff
        of each one against the base, and then finding the
        intersections between those regions.  These "sync regions"
        are by definition unchanged in both and easily dealt with.

        The regions in between can be in any of three cases:
        conflicted, or changed on only one side.
        """

        # section a[0:ia] has been disposed of, etc
        iz = ia = ib = 0

        for region in self.find_sync_regions():
            zmatch, zend, amatch, aend, bmatch, bend = region
            # print 'match base [%d:%d]' % (zmatch, zend)

            matchlen = zend - zmatch
            assert matchlen >= 0
            assert matchlen == (aend - amatch)
            assert matchlen == (bend - bmatch)

            len_a = amatch - ia
            len_b = bmatch - ib
            len_base = zmatch - iz
            assert len_a >= 0
            assert len_b >= 0
            assert len_base >= 0

            # print 'unmatched a=%d, b=%d' % (len_a, len_b)

            if len_a or len_b:
                # try to avoid actually slicing the lists
                equal_a = compare_range(
                    self.a, ia, amatch, self.base, iz, zmatch
                )
                equal_b = compare_range(
                    self.b, ib, bmatch, self.base, iz, zmatch
                )
                same = compare_range(self.a, ia, amatch, self.b, ib, bmatch)

                if same:
                    yield b'same', ia, amatch
                elif equal_a and not equal_b:
                    yield b'b', ib, bmatch
                elif equal_b and not equal_a:
                    yield b'a', ia, amatch
                elif not equal_a and not equal_b:
                    yield b'conflict', iz, zmatch, ia, amatch, ib, bmatch
                else:
                    raise AssertionError(b"can't handle a=b=base but unmatched")

                ia = amatch
                ib = bmatch
            iz = zmatch

            # if the same part of the base was deleted on both sides
            # that's OK, we can just skip it.

            if matchlen > 0:
                assert ia == amatch
                assert ib == bmatch
                assert iz == zmatch

                yield b'unchanged', zmatch, zend
                iz = zend
                ia = aend
                ib = bend

    def minimize(self, merge_regions):
        """Trim conflict regions of lines where A and B sides match.

        Lines where both A and B have made the same changes at the beginning
        or the end of each merge region are eliminated from the conflict
        region and are instead considered the same.
        """
        for region in merge_regions:
            if region[0] != b"conflict":
                yield region
                continue
            # pytype thinks this tuple contains only 3 things, but
            # that's clearly not true because this code successfully
            # executes. It might be wise to rework merge_regions to be
            # some kind of attrs type.
            (
                issue,
                z1,
                z2,
                a1,
                a2,
                b1,
                b2,
            ) = region  # pytype: disable=bad-unpacking
            alen = a2 - a1
            blen = b2 - b1

            # find matches at the front
            ii = 0
            while (
                ii < alen and ii < blen and self.a[a1 + ii] == self.b[b1 + ii]
            ):
                ii += 1
            startmatches = ii

            # find matches at the end
            ii = 0
            while (
                ii < alen
                and ii < blen
                and self.a[a2 - ii - 1] == self.b[b2 - ii - 1]
            ):
                ii += 1
            endmatches = ii

            if startmatches > 0:
                yield b'same', a1, a1 + startmatches

            yield (
                b'conflict',
                z1,
                z2,
                a1 + startmatches,
                a2 - endmatches,
                b1 + startmatches,
                b2 - endmatches,
            )

            if endmatches > 0:
                yield b'same', a2 - endmatches, a2

    def find_sync_regions(self):
        """Return a list of sync regions, where both descendants match the base.

        Generates a list of (base1, base2, a1, a2, b1, b2).  There is
        always a zero-length sync region at the end of all the files.
        """

        ia = ib = 0
        amatches = mdiff.get_matching_blocks(self.basetext, self.atext)
        bmatches = mdiff.get_matching_blocks(self.basetext, self.btext)
        len_a = len(amatches)
        len_b = len(bmatches)

        sl = []

        while ia < len_a and ib < len_b:
            abase, amatch, alen = amatches[ia]
            bbase, bmatch, blen = bmatches[ib]

            # there is an unconflicted block at i; how long does it
            # extend?  until whichever one ends earlier.
            i = intersect((abase, abase + alen), (bbase, bbase + blen))
            if i:
                intbase = i[0]
                intend = i[1]
                intlen = intend - intbase

                # found a match of base[i[0], i[1]]; this may be less than
                # the region that matches in either one
                assert intlen <= alen
                assert intlen <= blen
                assert abase <= intbase
                assert bbase <= intbase

                asub = amatch + (intbase - abase)
                bsub = bmatch + (intbase - bbase)
                aend = asub + intlen
                bend = bsub + intlen

                assert self.base[intbase:intend] == self.a[asub:aend], (
                    self.base[intbase:intend],
                    self.a[asub:aend],
                )

                assert self.base[intbase:intend] == self.b[bsub:bend]

                sl.append((intbase, intend, asub, aend, bsub, bend))

            # advance whichever one ends first in the base text
            if (abase + alen) < (bbase + blen):
                ia += 1
            else:
                ib += 1

        intbase = len(self.base)
        abase = len(self.a)
        bbase = len(self.b)
        sl.append((intbase, intbase, abase, abase, bbase, bbase))

        return sl


def _verifytext(text, path, ui, opts):
    """verifies that text is non-binary (unless opts[text] is passed,
    then we just warn)"""
    if stringutil.binary(text):
        msg = _(b"%s looks like a binary file.") % path
        if not opts.get('quiet'):
            ui.warn(_(b'warning: %s\n') % msg)
        if not opts.get('text'):
            raise error.Abort(msg)
    return text


def _picklabels(defaults, overrides):
    if len(overrides) > 3:
        raise error.Abort(_(b"can only specify three labels."))
    result = defaults[:]
    for i, override in enumerate(overrides):
        result[i] = override
    return result


def is_not_null(ctx):
    if not util.safehasattr(ctx, "node"):
        return False
    return ctx.rev() != nullrev


def _mergediff(m3, name_a, name_b, name_base):
    lines = []
    conflicts = False
    for group in m3.merge_groups():
        if group[0] == b'conflict':
            base_lines, a_lines, b_lines = group[1:]
            base_text = b''.join(base_lines)
            b_blocks = list(
                mdiff.allblocks(
                    base_text,
                    b''.join(b_lines),
                    lines1=base_lines,
                    lines2=b_lines,
                )
            )
            a_blocks = list(
                mdiff.allblocks(
                    base_text,
                    b''.join(a_lines),
                    lines1=base_lines,
                    lines2=b_lines,
                )
            )

            def matching_lines(blocks):
                return sum(
                    block[1] - block[0]
                    for block, kind in blocks
                    if kind == b'='
                )

            def diff_lines(blocks, lines1, lines2):
                for block, kind in blocks:
                    if kind == b'=':
                        for line in lines1[block[0] : block[1]]:
                            yield b' ' + line
                    else:
                        for line in lines1[block[0] : block[1]]:
                            yield b'-' + line
                        for line in lines2[block[2] : block[3]]:
                            yield b'+' + line

            lines.append(b"<<<<<<<\n")
            if matching_lines(a_blocks) < matching_lines(b_blocks):
                lines.append(b"======= %s\n" % name_a)
                lines.extend(a_lines)
                lines.append(b"------- %s\n" % name_base)
                lines.append(b"+++++++ %s\n" % name_b)
                lines.extend(diff_lines(b_blocks, base_lines, b_lines))
            else:
                lines.append(b"------- %s\n" % name_base)
                lines.append(b"+++++++ %s\n" % name_a)
                lines.extend(diff_lines(a_blocks, base_lines, a_lines))
                lines.append(b"======= %s\n" % name_b)
                lines.extend(b_lines)
            lines.append(b">>>>>>>\n")
            conflicts = True
        else:
            lines.extend(group[1])
    return lines, conflicts


def simplemerge(ui, localctx, basectx, otherctx, **opts):
    """Performs the simplemerge algorithm.

    The merged result is written into `localctx`.
    """

    def readctx(ctx):
        # Merges were always run in the working copy before, which means
        # they used decoded data, if the user defined any repository
        # filters.
        #
        # Maintain that behavior today for BC, though perhaps in the future
        # it'd be worth considering whether merging encoded data (what the
        # repository usually sees) might be more useful.
        return _verifytext(ctx.decodeddata(), ctx.path(), ui, opts)

    mode = opts.get('mode', b'merge')
    name_a, name_b, name_base = None, None, None
    if mode != b'union':
        name_a, name_b, name_base = _picklabels(
            [localctx.path(), otherctx.path(), None], opts.get('label', [])
        )

    try:
        localtext = readctx(localctx)
        basetext = readctx(basectx)
        othertext = readctx(otherctx)
    except error.Abort:
        return 1

    m3 = Merge3Text(basetext, localtext, othertext)
    extrakwargs = {
        b"localorother": opts.get("localorother", None),
        b'minimize': True,
    }
    if mode == b'union':
        extrakwargs[b'start_marker'] = None
        extrakwargs[b'mid_marker'] = None
        extrakwargs[b'end_marker'] = None
    elif name_base is not None:
        extrakwargs[b'base_marker'] = b'|||||||'
        extrakwargs[b'name_base'] = name_base
        extrakwargs[b'minimize'] = False

    if mode == b'mergediff':
        lines, conflicts = _mergediff(m3, name_a, name_b, name_base)
    else:
        lines = list(
            m3.merge_lines(
                name_a=name_a, name_b=name_b, **pycompat.strkwargs(extrakwargs)
            )
        )
        conflicts = m3.conflicts

    # merge flags if necessary
    flags = localctx.flags()
    localflags = set(pycompat.iterbytestr(flags))
    otherflags = set(pycompat.iterbytestr(otherctx.flags()))
    if is_not_null(basectx) and localflags != otherflags:
        baseflags = set(pycompat.iterbytestr(basectx.flags()))
        commonflags = localflags & otherflags
        addedflags = (localflags ^ otherflags) - baseflags
        flags = b''.join(sorted(commonflags | addedflags))

    mergedtext = b''.join(lines)
    if opts.get('print'):
        ui.fout.write(mergedtext)
    else:
        localctx.write(mergedtext, flags)

    if conflicts and not mode == b'union':
        return 1
