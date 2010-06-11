# mdiff.py - diff and patch routines for mercurial
#
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import bdiff, mpatch, util
import re, struct

def splitnewlines(text):
    '''like str.splitlines, but only split on newlines.'''
    lines = [l + '\n' for l in text.split('\n')]
    if lines:
        if lines[-1] == '\n':
            lines.pop()
        else:
            lines[-1] = lines[-1][:-1]
    return lines

class diffopts(object):
    '''context is the number of context lines
    text treats all files as text
    showfunc enables diff -p output
    git enables the git extended patch format
    nodates removes dates from diff headers
    ignorews ignores all whitespace changes in the diff
    ignorewsamount ignores changes in the amount of whitespace
    ignoreblanklines ignores changes whose lines are all blank
    upgrade generates git diffs to avoid data loss
    '''

    defaults = {
        'context': 3,
        'text': False,
        'showfunc': False,
        'git': False,
        'nodates': False,
        'ignorews': False,
        'ignorewsamount': False,
        'ignoreblanklines': False,
        'upgrade': False,
        }

    __slots__ = defaults.keys()

    def __init__(self, **opts):
        for k in self.__slots__:
            v = opts.get(k)
            if v is None:
                v = self.defaults[k]
            setattr(self, k, v)

        try:
            self.context = int(self.context)
        except ValueError:
            raise util.Abort(_('diff context lines count must be '
                               'an integer, not %r') % self.context)

    def copy(self, **kwargs):
        opts = dict((k, getattr(self, k)) for k in self.defaults)
        opts.update(kwargs)
        return diffopts(**opts)

defaultopts = diffopts()

def wsclean(opts, text, blank=True):
    if opts.ignorews:
        text = re.sub('[ \t]+', '', text)
    elif opts.ignorewsamount:
        text = re.sub('[ \t]+', ' ', text)
        text = re.sub('[ \t]+\n', '\n', text)
    if blank and opts.ignoreblanklines:
        text = re.sub('\n+', '', text)
    return text

def diffline(revs, a, b, opts):
    parts = ['diff']
    if opts.git:
        parts.append('--git')
    if revs and not opts.git:
        parts.append(' '.join(["-r %s" % rev for rev in revs]))
    if opts.git:
        parts.append('a/%s' % a)
        parts.append('b/%s' % b)
    else:
        parts.append(a)
    return ' '.join(parts) + '\n'

def unidiff(a, ad, b, bd, fn1, fn2, r=None, opts=defaultopts):
    def datetag(date, addtab=True):
        if not opts.git and not opts.nodates:
            return '\t%s\n' % date
        if addtab and ' ' in fn1:
            return '\t\n'
        return '\n'

    if not a and not b:
        return ""
    epoch = util.datestr((0, 0))

    if not opts.text and (util.binary(a) or util.binary(b)):
        if a and b and len(a) == len(b) and a == b:
            return ""
        l = ['Binary file %s has changed\n' % fn1]
    elif not a:
        b = splitnewlines(b)
        if a is None:
            l1 = '--- /dev/null%s' % datetag(epoch, False)
        else:
            l1 = "--- %s%s" % ("a/" + fn1, datetag(ad))
        l2 = "+++ %s%s" % ("b/" + fn2, datetag(bd))
        l3 = "@@ -0,0 +1,%d @@\n" % len(b)
        l = [l1, l2, l3] + ["+" + e for e in b]
    elif not b:
        a = splitnewlines(a)
        l1 = "--- %s%s" % ("a/" + fn1, datetag(ad))
        if b is None:
            l2 = '+++ /dev/null%s' % datetag(epoch, False)
        else:
            l2 = "+++ %s%s" % ("b/" + fn2, datetag(bd))
        l3 = "@@ -1,%d +0,0 @@\n" % len(a)
        l = [l1, l2, l3] + ["-" + e for e in a]
    else:
        al = splitnewlines(a)
        bl = splitnewlines(b)
        l = list(bunidiff(a, b, al, bl, "a/" + fn1, "b/" + fn2, opts=opts))
        if not l:
            return ""
        # difflib uses a space, rather than a tab
        l[0] = "%s%s" % (l[0][:-2], datetag(ad))
        l[1] = "%s%s" % (l[1][:-2], datetag(bd))

    for ln in xrange(len(l)):
        if l[ln][-1] != '\n':
            l[ln] += "\n\ No newline at end of file\n"

    if r:
        l.insert(0, diffline(r, fn1, fn2, opts))

    return "".join(l)

# somewhat self contained replacement for difflib.unified_diff
# t1 and t2 are the text to be diffed
# l1 and l2 are the text broken up into lines
# header1 and header2 are the filenames for the diff output
def bunidiff(t1, t2, l1, l2, header1, header2, opts=defaultopts):
    def contextend(l, len):
        ret = l + opts.context
        if ret > len:
            ret = len
        return ret

    def contextstart(l):
        ret = l - opts.context
        if ret < 0:
            return 0
        return ret

    def yieldhunk(hunk, header):
        if header:
            for x in header:
                yield x
        (astart, a2, bstart, b2, delta) = hunk
        aend = contextend(a2, len(l1))
        alen = aend - astart
        blen = b2 - bstart + aend - a2

        func = ""
        if opts.showfunc:
            # walk backwards from the start of the context
            # to find a line starting with an alphanumeric char.
            for x in xrange(astart - 1, -1, -1):
                t = l1[x].rstrip()
                if funcre.match(t):
                    func = ' ' + t[:40]
                    break

        yield "@@ -%d,%d +%d,%d @@%s\n" % (astart + 1, alen,
                                           bstart + 1, blen, func)
        for x in delta:
            yield x
        for x in xrange(a2, aend):
            yield ' ' + l1[x]

    header = ["--- %s\t\n" % header1, "+++ %s\t\n" % header2]

    if opts.showfunc:
        funcre = re.compile('\w')

    # bdiff.blocks gives us the matching sequences in the files.  The loop
    # below finds the spaces between those matching sequences and translates
    # them into diff output.
    #
    if opts.ignorews or opts.ignorewsamount:
        t1 = wsclean(opts, t1, False)
        t2 = wsclean(opts, t2, False)

    diff = bdiff.blocks(t1, t2)
    hunk = None
    for i, s1 in enumerate(diff):
        # The first match is special.
        # we've either found a match starting at line 0 or a match later
        # in the file.  If it starts later, old and new below will both be
        # empty and we'll continue to the next match.
        if i > 0:
            s = diff[i - 1]
        else:
            s = [0, 0, 0, 0]
        delta = []
        a1 = s[1]
        a2 = s1[0]
        b1 = s[3]
        b2 = s1[2]

        old = l1[a1:a2]
        new = l2[b1:b2]

        # bdiff sometimes gives huge matches past eof, this check eats them,
        # and deals with the special first match case described above
        if not old and not new:
            continue

        if opts.ignoreblanklines:
            if wsclean(opts, "".join(old)) == wsclean(opts, "".join(new)):
                continue

        astart = contextstart(a1)
        bstart = contextstart(b1)
        prev = None
        if hunk:
            # join with the previous hunk if it falls inside the context
            if astart < hunk[1] + opts.context + 1:
                prev = hunk
                astart = hunk[1]
                bstart = hunk[3]
            else:
                for x in yieldhunk(hunk, header):
                    yield x
                # we only want to yield the header if the files differ, and
                # we only want to yield it once.
                header = None
        if prev:
            # we've joined the previous hunk, record the new ending points.
            hunk[1] = a2
            hunk[3] = b2
            delta = hunk[4]
        else:
            # create a new hunk
            hunk = [astart, a2, bstart, b2, delta]

        delta[len(delta):] = [' ' + x for x in l1[astart:a1]]
        delta[len(delta):] = ['-' + x for x in old]
        delta[len(delta):] = ['+' + x for x in new]

    if hunk:
        for x in yieldhunk(hunk, header):
            yield x

def patchtext(bin):
    pos = 0
    t = []
    while pos < len(bin):
        p1, p2, l = struct.unpack(">lll", bin[pos:pos + 12])
        pos += 12
        t.append(bin[pos:pos + l])
        pos += l
    return "".join(t)

def patch(a, bin):
    return mpatch.patches(a, [bin])

# similar to difflib.SequenceMatcher.get_matching_blocks
def get_matching_blocks(a, b):
    return [(d[0], d[2], d[1] - d[0]) for d in bdiff.blocks(a, b)]

def trivialdiffheader(length):
    return struct.pack(">lll", 0, 0, length)

patches = mpatch.patches
patchedsize = mpatch.patchedsize
textdiff = bdiff.bdiff
