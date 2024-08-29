# grep.py - logic for history walk and grep
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import difflib

from .i18n import _

from . import (
    error,
    match as matchmod,
    pycompat,
    scmutil,
    util,
)


def matchlines(body, regexp):
    begin = 0
    linenum = 0
    while begin < len(body):
        match = regexp.search(body, begin)
        if not match:
            break
        mstart, mend = match.span()
        linenum += body.count(b'\n', begin, mstart) + 1
        lstart = body.rfind(b'\n', begin, mstart) + 1 or begin
        begin = body.find(b'\n', mend) + 1 or len(body) + 1
        lend = begin - 1
        yield linenum, mstart - lstart, mend - lstart, body[lstart:lend]


class linestate:
    def __init__(self, line, linenum, colstart, colend):
        self.line = line
        self.linenum = linenum
        self.colstart = colstart
        self.colend = colend

    def __hash__(self):
        return hash(self.line)

    def __eq__(self, other):
        return self.line == other.line

    def findpos(self, regexp):
        """Iterate all (start, end) indices of matches"""
        yield self.colstart, self.colend
        p = self.colend
        while p < len(self.line):
            m = regexp.search(self.line, p)
            if not m:
                break
            if m.end() == p:
                p += 1
            else:
                yield m.span()
                p = m.end()


def difflinestates(a, b):
    sm = difflib.SequenceMatcher(None, a, b)
    for tag, alo, ahi, blo, bhi in sm.get_opcodes():
        if tag == 'insert':
            for i in range(blo, bhi):
                yield (b'+', b[i])
        elif tag == 'delete':
            for i in range(alo, ahi):
                yield (b'-', a[i])
        elif tag == 'replace':
            for i in range(alo, ahi):
                yield (b'-', a[i])
            for i in range(blo, bhi):
                yield (b'+', b[i])


class grepsearcher:
    """Search files and revisions for lines matching the given pattern

    Options:
    - all_files to search unchanged files at that revision.
    - diff to search files in the parent revision so diffs can be generated.
    - follow to skip files across copies and renames.
    """

    def __init__(
        self, ui, repo, regexp, all_files=False, diff=False, follow=False
    ):
        self._ui = ui
        self._repo = repo
        self._regexp = regexp
        self._all_files = all_files
        self._diff = diff
        self._follow = follow

        self._getfile = util.lrucachefunc(repo.file)
        self._getrenamed = scmutil.getrenamedfn(repo)

        self._matches = {}
        self._copies = {}
        self._skip = set()
        self._revfiles = {}

    def skipfile(self, fn, rev):
        """Exclude the given file (and the copy at the specified revision)
        from future search"""
        copy = self._copies.get(rev, {}).get(fn)
        self._skip.add(fn)
        if copy:
            self._skip.add(copy)

    def searchfiles(self, revs, makefilematcher):
        """Walk files and revisions to yield (fn, ctx, pstates, states)
        matches

        states is a list of linestate objects. pstates may be empty unless
        diff is True.
        """
        for ctx in scmutil.walkchangerevs(
            self._repo, revs, makefilematcher, self._prep
        ):
            rev = ctx.rev()
            parent = ctx.p1().rev()
            for fn in sorted(self._revfiles.get(rev, [])):
                states = self._matches[rev][fn]
                copy = self._copies.get(rev, {}).get(fn)
                if fn in self._skip:
                    if copy:
                        self._skip.add(copy)
                    continue
                pstates = self._matches.get(parent, {}).get(copy or fn, [])
                if pstates or states:
                    yield fn, ctx, pstates, states
            del self._revfiles[rev]
            # We will keep the matches dict for the duration of the window
            # clear the matches dict once the window is over
            if not self._revfiles:
                self._matches.clear()

    def _grepbody(self, fn, rev, body):
        self._matches[rev].setdefault(fn, [])
        m = self._matches[rev][fn]
        if body is None:
            return

        for lnum, cstart, cend, line in matchlines(body, self._regexp):
            s = linestate(line, lnum, cstart, cend)
            m.append(s)

    def _readfile(self, ctx, fn):
        rev = ctx.rev()
        if rev is None:
            fctx = ctx[fn]
            try:
                return fctx.data()
            except FileNotFoundError:
                pass
        else:
            flog = self._getfile(fn)
            fnode = ctx.filenode(fn)
            try:
                return flog.read(fnode)
            except error.CensoredNodeError:
                self._ui.warn(
                    _(
                        b'cannot search in censored file: '
                        b'%(filename)s:%(revnum)s\n'
                    )
                    % {b'filename': fn, b'revnum': pycompat.bytestr(rev)}
                )

    def _prep(self, ctx, fmatch):
        rev = ctx.rev()
        pctx = ctx.p1()
        self._matches.setdefault(rev, {})
        if self._diff:
            parent = pctx.rev()
            self._matches.setdefault(parent, {})
        files = self._revfiles.setdefault(rev, [])
        if rev is None:
            # in `hg grep pattern`, 2/3 of the time is spent is spent in
            # pathauditor checks without this in mozilla-central
            contextmanager = self._repo.wvfs.audit.cached
        else:
            contextmanager = util.nullcontextmanager
        with contextmanager():
            # TODO: maybe better to warn missing files?
            if self._all_files:
                fmatch = matchmod.badmatch(fmatch, lambda f, msg: None)
                filenames = ctx.matches(fmatch)
            else:
                filenames = (f for f in ctx.files() if fmatch(f))
            for fn in filenames:
                # fn might not exist in the revision (could be a file removed by
                # the revision). We could check `fn not in ctx` even when rev is
                # None, but it's less racy to protect againt that in readfile.
                if rev is not None and fn not in ctx:
                    continue

                copy = None
                if self._follow:
                    copy = self._getrenamed(fn, rev)
                    if copy:
                        self._copies.setdefault(rev, {})[fn] = copy
                        if fn in self._skip:
                            self._skip.add(copy)
                if fn in self._skip:
                    continue
                files.append(fn)

                if fn not in self._matches[rev]:
                    self._grepbody(fn, rev, self._readfile(ctx, fn))

                if self._diff:
                    pfn = copy or fn
                    if pfn not in self._matches[parent] and pfn in pctx:
                        self._grepbody(pfn, parent, self._readfile(pctx, pfn))
