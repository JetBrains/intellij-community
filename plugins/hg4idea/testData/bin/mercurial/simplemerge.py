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


from .i18n import _
from . import (
    error,
    mdiff,
)
from .utils import stringutil


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
    for ia, ib in zip(range(astart, aend), range(bstart, bend)):
        if a[ia] != b[ib]:
            return False
    else:
        return True


class Merge3Text:
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

        'conflict', (base_lines, a_lines, b_lines)
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
                    (
                        self.base[t[1] : t[2]],
                        self.a[t[3] : t[4]],
                        self.b[t[5] : t[6]],
                    ),
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


def _verifytext(input):
    """verifies that text is non-binary (unless opts[text] is passed,
    then we just warn)"""
    if stringutil.binary(input.text()):
        msg = _(b"%s looks like a binary file.") % input.fctx.path()
        raise error.Abort(msg)


def _format_labels(*inputs):
    pad = max(len(input.label) if input.label else 0 for input in inputs)
    labels = []
    for input in inputs:
        if input.label:
            if input.label_detail:
                label = (
                    (input.label + b':').ljust(pad + 1)
                    + b' '
                    + input.label_detail
                )
            else:
                label = input.label
            # 8 for the prefix of conflict marker lines (e.g. '<<<<<<< ')
            labels.append(stringutil.ellipsis(label, 80 - 8))
        else:
            labels.append(None)
    return labels


def _detect_newline(m3):
    if len(m3.a) > 0:
        if m3.a[0].endswith(b'\r\n'):
            return b'\r\n'
        elif m3.a[0].endswith(b'\r'):
            return b'\r'
    return b'\n'


def _minimize(a_lines, b_lines):
    """Trim conflict regions of lines where A and B sides match.

    Lines where both A and B have made the same changes at the beginning
    or the end of each merge region are eliminated from the conflict
    region and are instead considered the same.
    """
    alen = len(a_lines)
    blen = len(b_lines)

    # find matches at the front
    ii = 0
    while ii < alen and ii < blen and a_lines[ii] == b_lines[ii]:
        ii += 1
    startmatches = ii

    # find matches at the end
    ii = 0
    while ii < alen and ii < blen and a_lines[-ii - 1] == b_lines[-ii - 1]:
        ii += 1
    endmatches = ii

    lines_before = a_lines[:startmatches]
    new_a_lines = a_lines[startmatches : alen - endmatches]
    new_b_lines = b_lines[startmatches : blen - endmatches]
    lines_after = a_lines[alen - endmatches :]
    return lines_before, new_a_lines, new_b_lines, lines_after


def render_minimized(
    m3,
    name_a=None,
    name_b=None,
    start_marker=b'<<<<<<<',
    mid_marker=b'=======',
    end_marker=b'>>>>>>>',
):
    """Return merge in cvs-like form."""
    newline = _detect_newline(m3)
    conflicts = False
    if name_a:
        start_marker = start_marker + b' ' + name_a
    if name_b:
        end_marker = end_marker + b' ' + name_b
    merge_groups = m3.merge_groups()
    lines = []
    for what, group_lines in merge_groups:
        if what == b'conflict':
            conflicts = True
            base_lines, a_lines, b_lines = group_lines
            minimized = _minimize(a_lines, b_lines)
            lines_before, a_lines, b_lines, lines_after = minimized
            lines.extend(lines_before)
            lines.append(start_marker + newline)
            lines.extend(a_lines)
            lines.append(mid_marker + newline)
            lines.extend(b_lines)
            lines.append(end_marker + newline)
            lines.extend(lines_after)
        else:
            lines.extend(group_lines)
    return lines, conflicts


def render_merge3(m3, name_a, name_b, name_base):
    """Render conflicts as 3-way conflict markers."""
    newline = _detect_newline(m3)
    conflicts = False
    lines = []
    for what, group_lines in m3.merge_groups():
        if what == b'conflict':
            base_lines, a_lines, b_lines = group_lines
            conflicts = True
            lines.append(b'<<<<<<< ' + name_a + newline)
            lines.extend(a_lines)
            lines.append(b'||||||| ' + name_base + newline)
            lines.extend(base_lines)
            lines.append(b'=======' + newline)
            lines.extend(b_lines)
            lines.append(b'>>>>>>> ' + name_b + newline)
        else:
            lines.extend(group_lines)
    return lines, conflicts


def render_mergediff(m3, name_a, name_b, name_base):
    """Render conflicts as conflict markers with one snapshot and one diff."""
    newline = _detect_newline(m3)
    lines = []
    conflicts = False
    for what, group_lines in m3.merge_groups():
        if what == b'conflict':
            base_lines, a_lines, b_lines = group_lines
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

            lines.append(b"<<<<<<<" + newline)
            if matching_lines(a_blocks) < matching_lines(b_blocks):
                lines.append(b"======= " + name_a + newline)
                lines.extend(a_lines)
                lines.append(b"------- " + name_base + newline)
                lines.append(b"+++++++ " + name_b + newline)
                lines.extend(diff_lines(b_blocks, base_lines, b_lines))
            else:
                lines.append(b"------- " + name_base + newline)
                lines.append(b"+++++++ " + name_a + newline)
                lines.extend(diff_lines(a_blocks, base_lines, a_lines))
                lines.append(b"======= " + name_b + newline)
                lines.extend(b_lines)
            lines.append(b">>>>>>>" + newline)
            conflicts = True
        else:
            lines.extend(group_lines)
    return lines, conflicts


def _resolve(m3, sides):
    lines = []
    for what, group_lines in m3.merge_groups():
        if what == b'conflict':
            for side in sides:
                lines.extend(group_lines[side])
        else:
            lines.extend(group_lines)
    return lines


class MergeInput:
    def __init__(self, fctx, label=None, label_detail=None):
        self.fctx = fctx
        self.label = label
        # If the "detail" part is set, then that is rendered after the label and
        # separated by a ':'. The label is padded to make the ':' aligned among
        # all merge inputs.
        self.label_detail = label_detail
        self._text = None

    def text(self):
        if self._text is None:
            # Merges were always run in the working copy before, which means
            # they used decoded data, if the user defined any repository
            # filters.
            #
            # Maintain that behavior today for BC, though perhaps in the future
            # it'd be worth considering whether merging encoded data (what the
            # repository usually sees) might be more useful.
            self._text = self.fctx.decodeddata()
        return self._text

    def set_text(self, text):
        self._text = text


def simplemerge(
    local,
    base,
    other,
    mode=b'merge',
    allow_binary=False,
):
    """Performs the simplemerge algorithm.

    The merged result is written into `localctx`.
    """

    if not allow_binary:
        _verifytext(local)
        _verifytext(base)
        _verifytext(other)

    m3 = Merge3Text(base.text(), local.text(), other.text())
    conflicts = False
    if mode == b'union':
        lines = _resolve(m3, (1, 2))
    elif mode == b'union-other-first':
        lines = _resolve(m3, (2, 1))
    elif mode == b'local':
        lines = _resolve(m3, (1,))
    elif mode == b'other':
        lines = _resolve(m3, (2,))
    else:
        if mode == b'mergediff':
            labels = _format_labels(local, other, base)
            lines, conflicts = render_mergediff(m3, *labels)
        elif mode == b'merge3':
            labels = _format_labels(local, other, base)
            lines, conflicts = render_merge3(m3, *labels)
        else:
            labels = _format_labels(local, other)
            lines, conflicts = render_minimized(m3, *labels)

    mergedtext = b''.join(lines)
    return mergedtext, conflicts
