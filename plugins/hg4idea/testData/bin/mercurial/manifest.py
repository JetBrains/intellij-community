# manifest.py - manifest revision class for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import mdiff, parsers, error, revlog, util, dicthelpers
import array, struct

class manifestdict(dict):
    def __init__(self, mapping=None, flags=None):
        if mapping is None:
            mapping = {}
        if flags is None:
            flags = {}
        dict.__init__(self, mapping)
        self._flags = flags
    def flags(self, f):
        return self._flags.get(f, "")
    def withflags(self):
        return set(self._flags.keys())
    def set(self, f, flags):
        self._flags[f] = flags
    def copy(self):
        return manifestdict(self, dict.copy(self._flags))
    def flagsdiff(self, d2):
        return dicthelpers.diff(self._flags, d2._flags, "")

class manifest(revlog.revlog):
    def __init__(self, opener):
        # we expect to deal with not more than three revs at a time in merge
        self._mancache = util.lrucachedict(3)
        revlog.revlog.__init__(self, opener, "00manifest.i")

    def parse(self, lines):
        mfdict = manifestdict()
        parsers.parse_manifest(mfdict, mfdict._flags, lines)
        return mfdict

    def readdelta(self, node):
        r = self.rev(node)
        return self.parse(mdiff.patchtext(self.revdiff(self.deltaparent(r), r)))

    def readfast(self, node):
        '''use the faster of readdelta or read'''
        r = self.rev(node)
        deltaparent = self.deltaparent(r)
        if deltaparent != revlog.nullrev and deltaparent in self.parentrevs(r):
            return self.readdelta(node)
        return self.read(node)

    def read(self, node):
        if node == revlog.nullid:
            return manifestdict() # don't upset local cache
        if node in self._mancache:
            return self._mancache[node][0]
        text = self.revision(node)
        arraytext = array.array('c', text)
        mapping = self.parse(text)
        self._mancache[node] = (mapping, arraytext)
        return mapping

    def _search(self, m, s, lo=0, hi=None):
        '''return a tuple (start, end) that says where to find s within m.

        If the string is found m[start:end] are the line containing
        that string.  If start == end the string was not found and
        they indicate the proper sorted insertion point.

        m should be a buffer or a string
        s is a string'''
        def advance(i, c):
            while i < lenm and m[i] != c:
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
            while start > 0 and m[start - 1] != '\n':
                start -= 1
            end = advance(start, '\0')
            if m[start:end] < s:
                # we know that after the null there are 40 bytes of sha1
                # this translates to the bisect lo = mid + 1
                lo = advance(end + 40, '\n') + 1
            else:
                # this translates to the bisect hi = mid
                hi = start
        end = advance(lo, '\0')
        found = m[lo:end]
        if s == found:
            # we know that after the null there are 40 bytes of sha1
            end = advance(end + 40, '\n')
            return (lo, end + 1)
        else:
            return (lo, lo)

    def find(self, node, f):
        '''look up entry for a single file efficiently.
        return (node, flags) pair if found, (None, None) if not.'''
        if node in self._mancache:
            mapping = self._mancache[node][0]
            return mapping.get(f), mapping.flags(f)
        text = self.revision(node)
        start, end = self._search(text, f)
        if start == end:
            return None, None
        l = text[start:end]
        f, n = l.split('\0')
        return revlog.bin(n[:40]), n[40:-1]

    def add(self, map, transaction, link, p1=None, p2=None,
            changed=None):
        # apply the changes collected during the bisect loop to our addlist
        # return a delta suitable for addrevision
        def addlistdelta(addlist, x):
            # for large addlist arrays, building a new array is cheaper
            # than repeatedly modifying the existing one
            currentposition = 0
            newaddlist = array.array('c')

            for start, end, content in x:
                newaddlist += addlist[currentposition:start]
                if content:
                    newaddlist += array.array('c', content)

                currentposition = end

            newaddlist += addlist[currentposition:]

            deltatext = "".join(struct.pack(">lll", start, end, len(content))
                           + content for start, end, content in x)
            return deltatext, newaddlist

        def checkforbidden(l):
            for f in l:
                if '\n' in f or '\r' in f:
                    raise error.RevlogError(
                        _("'\\n' and '\\r' disallowed in filenames: %r") % f)

        # if we're using the cache, make sure it is valid and
        # parented by the same node we're diffing against
        if not (changed and p1 and (p1 in self._mancache)):
            files = sorted(map)
            checkforbidden(files)

            # if this is changed to support newlines in filenames,
            # be sure to check the templates/ dir again (especially *-raw.tmpl)
            hex, flags = revlog.hex, map.flags
            text = ''.join("%s\0%s%s\n" % (f, hex(map[f]), flags(f))
                           for f in files)
            arraytext = array.array('c', text)
            cachedelta = None
        else:
            added, removed = changed
            addlist = self._mancache[p1][1]

            checkforbidden(added)
            # combine the changed lists into one list for sorting
            work = [(x, False) for x in added]
            work.extend((x, True) for x in removed)
            # this could use heapq.merge() (from Python 2.6+) or equivalent
            # since the lists are already sorted
            work.sort()

            delta = []
            dstart = None
            dend = None
            dline = [""]
            start = 0
            # zero copy representation of addlist as a buffer
            addbuf = util.buffer(addlist)

            # start with a readonly loop that finds the offset of
            # each line and creates the deltas
            for f, todelete in work:
                # bs will either be the index of the item or the insert point
                start, end = self._search(addbuf, f, start)
                if not todelete:
                    l = "%s\0%s%s\n" % (f, revlog.hex(map[f]), map.flags(f))
                else:
                    if start == end:
                        # item we want to delete was not found, error out
                        raise AssertionError(
                                _("failed to remove %s from manifest") % f)
                    l = ""
                if dstart is not None and dstart <= start and dend >= start:
                    if dend < end:
                        dend = end
                    if l:
                        dline.append(l)
                else:
                    if dstart is not None:
                        delta.append([dstart, dend, "".join(dline)])
                    dstart = start
                    dend = end
                    dline = [l]

            if dstart is not None:
                delta.append([dstart, dend, "".join(dline)])
            # apply the delta to the addlist, and get a delta for addrevision
            deltatext, addlist = addlistdelta(addlist, delta)
            cachedelta = (self.rev(p1), deltatext)
            arraytext = addlist
            text = util.buffer(arraytext)

        n = self.addrevision(text, transaction, link, p1, p2, cachedelta)
        self._mancache[n] = (map, arraytext)

        return n
