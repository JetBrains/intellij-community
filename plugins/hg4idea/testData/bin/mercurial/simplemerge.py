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

from i18n import _
import scmutil, util, mdiff
import sys, os

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
    """Compare a[astart:aend] == b[bstart:bend], without slicing.
    """
    if (aend - astart) != (bend - bstart):
        return False
    for ia, ib in zip(xrange(astart, aend), xrange(bstart, bend)):
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

    def merge_lines(self,
                    name_a=None,
                    name_b=None,
                    name_base=None,
                    start_marker='<<<<<<<',
                    mid_marker='=======',
                    end_marker='>>>>>>>',
                    base_marker=None,
                    reprocess=False):
        """Return merge in cvs-like form.
        """
        self.conflicts = False
        newline = '\n'
        if len(self.a) > 0:
            if self.a[0].endswith('\r\n'):
                newline = '\r\n'
            elif self.a[0].endswith('\r'):
                newline = '\r'
        if base_marker and reprocess:
            raise CantReprocessAndShowBase
        if name_a:
            start_marker = start_marker + ' ' + name_a
        if name_b:
            end_marker = end_marker + ' ' + name_b
        if name_base and base_marker:
            base_marker = base_marker + ' ' + name_base
        merge_regions = self.merge_regions()
        if reprocess is True:
            merge_regions = self.reprocess_merge_regions(merge_regions)
        for t in merge_regions:
            what = t[0]
            if what == 'unchanged':
                for i in range(t[1], t[2]):
                    yield self.base[i]
            elif what == 'a' or what == 'same':
                for i in range(t[1], t[2]):
                    yield self.a[i]
            elif what == 'b':
                for i in range(t[1], t[2]):
                    yield self.b[i]
            elif what == 'conflict':
                self.conflicts = True
                yield start_marker + newline
                for i in range(t[3], t[4]):
                    yield self.a[i]
                if base_marker is not None:
                    yield base_marker + newline
                    for i in range(t[1], t[2]):
                        yield self.base[i]
                yield mid_marker + newline
                for i in range(t[5], t[6]):
                    yield self.b[i]
                yield end_marker + newline
            else:
                raise ValueError(what)

    def merge_annotated(self):
        """Return merge with conflicts, showing origin of lines.

        Most useful for debugging merge.
        """
        for t in self.merge_regions():
            what = t[0]
            if what == 'unchanged':
                for i in range(t[1], t[2]):
                    yield 'u | ' + self.base[i]
            elif what == 'a' or what == 'same':
                for i in range(t[1], t[2]):
                    yield what[0] + ' | ' + self.a[i]
            elif what == 'b':
                for i in range(t[1], t[2]):
                    yield 'b | ' + self.b[i]
            elif what == 'conflict':
                yield '<<<<\n'
                for i in range(t[3], t[4]):
                    yield 'A | ' + self.a[i]
                yield '----\n'
                for i in range(t[5], t[6]):
                    yield 'B | ' + self.b[i]
                yield '>>>>\n'
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
            if what == 'unchanged':
                yield what, self.base[t[1]:t[2]]
            elif what == 'a' or what == 'same':
                yield what, self.a[t[1]:t[2]]
            elif what == 'b':
                yield what, self.b[t[1]:t[2]]
            elif what == 'conflict':
                yield (what,
                       self.base[t[1]:t[2]],
                       self.a[t[3]:t[4]],
                       self.b[t[5]:t[6]])
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
            #print 'match base [%d:%d]' % (zmatch, zend)

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

            #print 'unmatched a=%d, b=%d' % (len_a, len_b)

            if len_a or len_b:
                # try to avoid actually slicing the lists
                equal_a = compare_range(self.a, ia, amatch,
                                        self.base, iz, zmatch)
                equal_b = compare_range(self.b, ib, bmatch,
                                        self.base, iz, zmatch)
                same = compare_range(self.a, ia, amatch,
                                     self.b, ib, bmatch)

                if same:
                    yield 'same', ia, amatch
                elif equal_a and not equal_b:
                    yield 'b', ib, bmatch
                elif equal_b and not equal_a:
                    yield 'a', ia, amatch
                elif not equal_a and not equal_b:
                    yield 'conflict', iz, zmatch, ia, amatch, ib, bmatch
                else:
                    raise AssertionError("can't handle a=b=base but unmatched")

                ia = amatch
                ib = bmatch
            iz = zmatch

            # if the same part of the base was deleted on both sides
            # that's OK, we can just skip it.


            if matchlen > 0:
                assert ia == amatch
                assert ib == bmatch
                assert iz == zmatch

                yield 'unchanged', zmatch, zend
                iz = zend
                ia = aend
                ib = bend

    def reprocess_merge_regions(self, merge_regions):
        """Where there are conflict regions, remove the agreed lines.

        Lines where both A and B have made the same changes are
        eliminated.
        """
        for region in merge_regions:
            if region[0] != "conflict":
                yield region
                continue
            type, iz, zmatch, ia, amatch, ib, bmatch = region
            a_region = self.a[ia:amatch]
            b_region = self.b[ib:bmatch]
            matches = mdiff.get_matching_blocks(''.join(a_region),
                                                ''.join(b_region))
            next_a = ia
            next_b = ib
            for region_ia, region_ib, region_len in matches[:-1]:
                region_ia += ia
                region_ib += ib
                reg = self.mismatch_region(next_a, region_ia, next_b,
                                           region_ib)
                if reg is not None:
                    yield reg
                yield 'same', region_ia, region_len + region_ia
                next_a = region_ia + region_len
                next_b = region_ib + region_len
            reg = self.mismatch_region(next_a, amatch, next_b, bmatch)
            if reg is not None:
                yield reg

    def mismatch_region(next_a, region_ia,  next_b, region_ib):
        if next_a < region_ia or next_b < region_ib:
            return 'conflict', None, None, next_a, region_ia, next_b, region_ib
    mismatch_region = staticmethod(mismatch_region)

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

                assert self.base[intbase:intend] == self.a[asub:aend], \
                       (self.base[intbase:intend], self.a[asub:aend])

                assert self.base[intbase:intend] == self.b[bsub:bend]

                sl.append((intbase, intend,
                           asub, aend,
                           bsub, bend))

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

    def find_unconflicted(self):
        """Return a list of ranges in base that are not conflicted."""
        am = mdiff.get_matching_blocks(self.basetext, self.atext)
        bm = mdiff.get_matching_blocks(self.basetext, self.btext)

        unc = []

        while am and bm:
            # there is an unconflicted block at i; how long does it
            # extend?  until whichever one ends earlier.
            a1 = am[0][0]
            a2 = a1 + am[0][2]
            b1 = bm[0][0]
            b2 = b1 + bm[0][2]
            i = intersect((a1, a2), (b1, b2))
            if i:
                unc.append(i)

            if a2 < b2:
                del am[0]
            else:
                del bm[0]

        return unc

def simplemerge(ui, local, base, other, **opts):
    def readfile(filename):
        f = open(filename, "rb")
        text = f.read()
        f.close()
        if util.binary(text):
            msg = _("%s looks like a binary file.") % filename
            if not opts.get('quiet'):
                ui.warn(_('warning: %s\n') % msg)
            if not opts.get('text'):
                raise util.Abort(msg)
        return text

    name_a = local
    name_b = other
    labels = opts.get('label', [])
    if labels:
        name_a = labels.pop(0)
    if labels:
        name_b = labels.pop(0)
    if labels:
        raise util.Abort(_("can only specify two labels."))

    try:
        localtext = readfile(local)
        basetext = readfile(base)
        othertext = readfile(other)
    except util.Abort:
        return 1

    local = os.path.realpath(local)
    if not opts.get('print'):
        opener = scmutil.opener(os.path.dirname(local))
        out = opener(os.path.basename(local), "w", atomictemp=True)
    else:
        out = sys.stdout

    reprocess = not opts.get('no_minimal')

    m3 = Merge3Text(basetext, localtext, othertext)
    for line in m3.merge_lines(name_a=name_a, name_b=name_b,
                               reprocess=reprocess):
        out.write(line)

    if not opts.get('print'):
        out.close()

    if m3.conflicts:
        if not opts.get('quiet'):
            ui.warn(_("warning: conflicts during merge.\n"))
        return 1
