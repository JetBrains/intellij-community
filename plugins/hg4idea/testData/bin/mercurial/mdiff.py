# mdiff.py - diff and patch routines for mercurial
#
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import bdiff, mpatch, util
import re, struct, base85, zlib

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
        text = bdiff.fixws(text, 1)
    elif opts.ignorewsamount:
        text = bdiff.fixws(text, 0)
    if blank and opts.ignoreblanklines:
        text = re.sub('\n+', '\n', text).strip('\n')
    return text

def splitblock(base1, lines1, base2, lines2, opts):
    # The input lines matches except for interwoven blank lines. We
    # transform it into a sequence of matching blocks and blank blocks.
    lines1 = [(wsclean(opts, l) and 1 or 0) for l in lines1]
    lines2 = [(wsclean(opts, l) and 1 or 0) for l in lines2]
    s1, e1 = 0, len(lines1)
    s2, e2 = 0, len(lines2)
    while s1 < e1 or s2 < e2:
        i1, i2, btype = s1, s2, '='
        if (i1 >= e1 or lines1[i1] == 0
            or i2 >= e2 or lines2[i2] == 0):
            # Consume the block of blank lines
            btype = '~'
            while i1 < e1 and lines1[i1] == 0:
                i1 += 1
            while i2 < e2 and lines2[i2] == 0:
                i2 += 1
        else:
            # Consume the matching lines
            while i1 < e1 and lines1[i1] == 1 and lines2[i2] == 1:
                i1 += 1
                i2 += 1
        yield [base1 + s1, base1 + i1, base2 + s2, base2 + i2], btype
        s1 = i1
        s2 = i2

def allblocks(text1, text2, opts=None, lines1=None, lines2=None, refine=False):
    """Return (block, type) tuples, where block is an mdiff.blocks
    line entry. type is '=' for blocks matching exactly one another
    (bdiff blocks), '!' for non-matching blocks and '~' for blocks
    matching only after having filtered blank lines. If refine is True,
    then '~' blocks are refined and are only made of blank lines.
    line1 and line2 are text1 and text2 split with splitnewlines() if
    they are already available.
    """
    if opts is None:
        opts = defaultopts
    if opts.ignorews or opts.ignorewsamount:
        text1 = wsclean(opts, text1, False)
        text2 = wsclean(opts, text2, False)
    diff = bdiff.blocks(text1, text2)
    for i, s1 in enumerate(diff):
        # The first match is special.
        # we've either found a match starting at line 0 or a match later
        # in the file.  If it starts later, old and new below will both be
        # empty and we'll continue to the next match.
        if i > 0:
            s = diff[i - 1]
        else:
            s = [0, 0, 0, 0]
        s = [s[1], s1[0], s[3], s1[2]]

        # bdiff sometimes gives huge matches past eof, this check eats them,
        # and deals with the special first match case described above
        if s[0] != s[1] or s[2] != s[3]:
            type = '!'
            if opts.ignoreblanklines:
                if lines1 is None:
                    lines1 = splitnewlines(text1)
                if lines2 is None:
                    lines2 = splitnewlines(text2)
                old = wsclean(opts, "".join(lines1[s[0]:s[1]]))
                new = wsclean(opts, "".join(lines2[s[2]:s[3]]))
                if old == new:
                    type = '~'
            yield s, type
        yield s1, '='

def unidiff(a, ad, b, bd, fn1, fn2, opts=defaultopts):
    def datetag(date, fn=None):
        if not opts.git and not opts.nodates:
            return '\t%s\n' % date
        if fn and ' ' in fn:
            return '\t\n'
        return '\n'

    if not a and not b:
        return ""
    epoch = util.datestr((0, 0))

    fn1 = util.pconvert(fn1)
    fn2 = util.pconvert(fn2)

    if not opts.text and (util.binary(a) or util.binary(b)):
        if a and b and len(a) == len(b) and a == b:
            return ""
        l = ['Binary file %s has changed\n' % fn1]
    elif not a:
        b = splitnewlines(b)
        if a is None:
            l1 = '--- /dev/null%s' % datetag(epoch)
        else:
            l1 = "--- %s%s" % ("a/" + fn1, datetag(ad, fn1))
        l2 = "+++ %s%s" % ("b/" + fn2, datetag(bd, fn2))
        l3 = "@@ -0,0 +1,%d @@\n" % len(b)
        l = [l1, l2, l3] + ["+" + e for e in b]
    elif not b:
        a = splitnewlines(a)
        l1 = "--- %s%s" % ("a/" + fn1, datetag(ad, fn1))
        if b is None:
            l2 = '+++ /dev/null%s' % datetag(epoch)
        else:
            l2 = "+++ %s%s" % ("b/" + fn2, datetag(bd, fn2))
        l3 = "@@ -1,%d +0,0 @@\n" % len(a)
        l = [l1, l2, l3] + ["-" + e for e in a]
    else:
        al = splitnewlines(a)
        bl = splitnewlines(b)
        l = list(_unidiff(a, b, al, bl, opts=opts))
        if not l:
            return ""

        l.insert(0, "--- a/%s%s" % (fn1, datetag(ad, fn1)))
        l.insert(1, "+++ b/%s%s" % (fn2, datetag(bd, fn2)))

    for ln in xrange(len(l)):
        if l[ln][-1] != '\n':
            l[ln] += "\n\ No newline at end of file\n"

    return "".join(l)

# creates a headerless unified diff
# t1 and t2 are the text to be diffed
# l1 and l2 are the text broken up into lines
def _unidiff(t1, t2, l1, l2, opts=defaultopts):
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

    lastfunc = [0, '']
    def yieldhunk(hunk):
        (astart, a2, bstart, b2, delta) = hunk
        aend = contextend(a2, len(l1))
        alen = aend - astart
        blen = b2 - bstart + aend - a2

        func = ""
        if opts.showfunc:
            lastpos, func = lastfunc
            # walk backwards from the start of the context up to the start of
            # the previous hunk context until we find a line starting with an
            # alphanumeric char.
            for i in xrange(astart - 1, lastpos - 1, -1):
                if l1[i][0].isalnum():
                    func = ' ' + l1[i].rstrip()[:40]
                    lastfunc[1] = func
                    break
            # by recording this hunk's starting point as the next place to
            # start looking for function lines, we avoid reading any line in
            # the file more than once.
            lastfunc[0] = astart

        # zero-length hunk ranges report their start line as one less
        if alen:
            astart += 1
        if blen:
            bstart += 1

        yield "@@ -%d,%d +%d,%d @@%s\n" % (astart, alen,
                                           bstart, blen, func)
        for x in delta:
            yield x
        for x in xrange(a2, aend):
            yield ' ' + l1[x]

    # bdiff.blocks gives us the matching sequences in the files.  The loop
    # below finds the spaces between those matching sequences and translates
    # them into diff output.
    #
    hunk = None
    ignoredlines = 0
    for s, stype in allblocks(t1, t2, opts, l1, l2):
        a1, a2, b1, b2 = s
        if stype != '!':
            if stype == '~':
                # The diff context lines are based on t1 content. When
                # blank lines are ignored, the new lines offsets must
                # be adjusted as if equivalent blocks ('~') had the
                # same sizes on both sides.
                ignoredlines += (b2 - b1) - (a2 - a1)
            continue
        delta = []
        old = l1[a1:a2]
        new = l2[b1:b2]

        b1 -= ignoredlines
        b2 -= ignoredlines
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
                for x in yieldhunk(hunk):
                    yield x
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
        for x in yieldhunk(hunk):
            yield x

def b85diff(to, tn):
    '''print base85-encoded binary diff'''
    def fmtline(line):
        l = len(line)
        if l <= 26:
            l = chr(ord('A') + l - 1)
        else:
            l = chr(l - 26 + ord('a') - 1)
        return '%c%s\n' % (l, base85.b85encode(line, True))

    def chunk(text, csize=52):
        l = len(text)
        i = 0
        while i < l:
            yield text[i:i + csize]
            i += csize

    if to is None:
        to = ''
    if tn is None:
        tn = ''

    if to == tn:
        return ''

    # TODO: deltas
    ret = []
    ret.append('GIT binary patch\n')
    ret.append('literal %s\n' % len(tn))
    for l in chunk(zlib.compress(tn)):
        ret.append(fmtline(l))
    ret.append('\n')

    return ''.join(ret)

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
    if len(a) == 0:
        # skip over trivial delta header
        return util.buffer(bin, 12)
    return mpatch.patches(a, [bin])

# similar to difflib.SequenceMatcher.get_matching_blocks
def get_matching_blocks(a, b):
    return [(d[0], d[2], d[1] - d[0]) for d in bdiff.blocks(a, b)]

def trivialdiffheader(length):
    return struct.pack(">lll", 0, 0, length)

patches = mpatch.patches
patchedsize = mpatch.patchedsize
textdiff = bdiff.bdiff
