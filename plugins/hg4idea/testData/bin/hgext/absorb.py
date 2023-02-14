# absorb.py
#
# Copyright 2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""apply working directory changes to changesets (EXPERIMENTAL)

The absorb extension provides a command to use annotate information to
amend modified chunks into the corresponding non-public changesets.

::

    [absorb]
    # only check 50 recent non-public changesets at most
    max-stack-size = 50
    # whether to add noise to new commits to avoid obsolescence cycle
    add-noise = 1
    # make `amend --correlated` a shortcut to the main command
    amend-flag = correlated

    [color]
    absorb.description = yellow
    absorb.node = blue bold
    absorb.path = bold
"""

# TODO:
#  * Rename config items to [commands] namespace
#  * Converge getdraftstack() with other code in core
#  * move many attributes on fixupstate to be private

from __future__ import absolute_import

import collections

from mercurial.i18n import _
from mercurial.node import (
    hex,
    short,
)
from mercurial import (
    cmdutil,
    commands,
    context,
    crecord,
    error,
    linelog,
    mdiff,
    obsolete,
    patch,
    phases,
    pycompat,
    registrar,
    rewriteutil,
    scmutil,
    util,
)
from mercurial.utils import stringutil

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

cmdtable = {}
command = registrar.command(cmdtable)

configtable = {}
configitem = registrar.configitem(configtable)

configitem(b'absorb', b'add-noise', default=True)
configitem(b'absorb', b'amend-flag', default=None)
configitem(b'absorb', b'max-stack-size', default=50)

colortable = {
    b'absorb.description': b'yellow',
    b'absorb.node': b'blue bold',
    b'absorb.path': b'bold',
}

defaultdict = collections.defaultdict


class nullui(object):
    """blank ui object doing nothing"""

    debugflag = False
    verbose = False
    quiet = True

    def __getitem__(name):
        def nullfunc(*args, **kwds):
            return

        return nullfunc


class emptyfilecontext(object):
    """minimal filecontext representing an empty file"""

    def __init__(self, repo):
        self._repo = repo

    def data(self):
        return b''

    def node(self):
        return self._repo.nullid


def uniq(lst):
    """list -> list. remove duplicated items without changing the order"""
    seen = set()
    result = []
    for x in lst:
        if x not in seen:
            seen.add(x)
            result.append(x)
    return result


def getdraftstack(headctx, limit=None):
    """(ctx, int?) -> [ctx]. get a linear stack of non-public changesets.

    changesets are sorted in topo order, oldest first.
    return at most limit items, if limit is a positive number.

    merges are considered as non-draft as well. i.e. every commit
    returned has and only has 1 parent.
    """
    ctx = headctx
    result = []
    while ctx.phase() != phases.public:
        if limit and len(result) >= limit:
            break
        parents = ctx.parents()
        if len(parents) != 1:
            break
        result.append(ctx)
        ctx = parents[0]
    result.reverse()
    return result


def getfilestack(stack, path, seenfctxs=None):
    """([ctx], str, set) -> [fctx], {ctx: fctx}

    stack is a list of contexts, from old to new. usually they are what
    "getdraftstack" returns.

    follows renames, but not copies.

    seenfctxs is a set of filecontexts that will be considered "immutable".
    they are usually what this function returned in earlier calls, useful
    to avoid issues that a file was "moved" to multiple places and was then
    modified differently, like: "a" was copied to "b", "a" was also copied to
    "c" and then "a" was deleted, then both "b" and "c" were "moved" from "a"
    and we enforce only one of them to be able to affect "a"'s content.

    return an empty list and an empty dict, if the specified path does not
    exist in stack[-1] (the top of the stack).

    otherwise, return a list of de-duplicated filecontexts, and the map to
    convert ctx in the stack to fctx, for possible mutable fctxs. the first item
    of the list would be outside the stack and should be considered immutable.
    the remaining items are within the stack.

    for example, given the following changelog and corresponding filelog
    revisions:

      changelog: 3----4----5----6----7
      filelog:   x    0----1----1----2 (x: no such file yet)

    - if stack = [5, 6, 7], returns ([0, 1, 2], {5: 1, 6: 1, 7: 2})
    - if stack = [3, 4, 5], returns ([e, 0, 1], {4: 0, 5: 1}), where "e" is a
      dummy empty filecontext.
    - if stack = [2], returns ([], {})
    - if stack = [7], returns ([1, 2], {7: 2})
    - if stack = [6, 7], returns ([1, 2], {6: 1, 7: 2}), although {6: 1} can be
      removed, since 1 is immutable.
    """
    if seenfctxs is None:
        seenfctxs = set()
    assert stack

    if path not in stack[-1]:
        return [], {}

    fctxs = []
    fctxmap = {}

    pctx = stack[0].p1()  # the public (immutable) ctx we stop at
    for ctx in reversed(stack):
        if path not in ctx:  # the file is added in the next commit
            pctx = ctx
            break
        fctx = ctx[path]
        fctxs.append(fctx)
        if fctx in seenfctxs:  # treat fctx as the immutable one
            pctx = None  # do not add another immutable fctx
            break
        fctxmap[ctx] = fctx  # only for mutable fctxs
        copy = fctx.copysource()
        if copy:
            path = copy  # follow rename
            if path in ctx:  # but do not follow copy
                pctx = ctx.p1()
                break

    if pctx is not None:  # need an extra immutable fctx
        if path in pctx:
            fctxs.append(pctx[path])
        else:
            fctxs.append(emptyfilecontext(pctx.repo()))

    fctxs.reverse()
    # note: we rely on a property of hg: filerev is not reused for linear
    # history. i.e. it's impossible to have:
    #   changelog:  4----5----6 (linear, no merges)
    #   filelog:    1----2----1
    #                         ^ reuse filerev (impossible)
    # because parents are part of the hash. if that's not true, we need to
    # remove uniq and find a different way to identify fctxs.
    return uniq(fctxs), fctxmap


class overlaystore(patch.filestore):
    """read-only, hybrid store based on a dict and ctx.
    memworkingcopy: {path: content}, overrides file contents.
    """

    def __init__(self, basectx, memworkingcopy):
        self.basectx = basectx
        self.memworkingcopy = memworkingcopy

    def getfile(self, path):
        """comply with mercurial.patch.filestore.getfile"""
        if path not in self.basectx:
            return None, None, None
        fctx = self.basectx[path]
        if path in self.memworkingcopy:
            content = self.memworkingcopy[path]
        else:
            content = fctx.data()
        mode = (fctx.islink(), fctx.isexec())
        copy = fctx.copysource()
        return content, mode, copy


def overlaycontext(memworkingcopy, ctx, parents=None, extra=None, desc=None):
    """({path: content}, ctx, (p1node, p2node)?, {}?) -> memctx
    memworkingcopy overrides file contents.
    """
    # parents must contain 2 items: (node1, node2)
    if parents is None:
        parents = ctx.repo().changelog.parents(ctx.node())
    if extra is None:
        extra = ctx.extra()
    if desc is None:
        desc = ctx.description()
    date = ctx.date()
    user = ctx.user()
    files = set(ctx.files()).union(memworkingcopy)
    store = overlaystore(ctx, memworkingcopy)
    return context.memctx(
        repo=ctx.repo(),
        parents=parents,
        text=desc,
        files=files,
        filectxfn=store,
        user=user,
        date=date,
        branch=None,
        extra=extra,
    )


class filefixupstate(object):
    """state needed to apply fixups to a single file

    internally, it keeps file contents of several revisions and a linelog.

    the linelog uses odd revision numbers for original contents (fctxs passed
    to __init__), and even revision numbers for fixups, like:

        linelog rev 1: self.fctxs[0] (from an immutable "public" changeset)
        linelog rev 2: fixups made to self.fctxs[0]
        linelog rev 3: self.fctxs[1] (a child of fctxs[0])
        linelog rev 4: fixups made to self.fctxs[1]
        ...

    a typical use is like:

        1. call diffwith, to calculate self.fixups
        2. (optionally), present self.fixups to the user, or change it
        3. call apply, to apply changes
        4. read results from "finalcontents", or call getfinalcontent
    """

    def __init__(self, fctxs, path, ui=None, opts=None):
        """([fctx], ui or None) -> None

        fctxs should be linear, and sorted by topo order - oldest first.
        fctxs[0] will be considered as "immutable" and will not be changed.
        """
        self.fctxs = fctxs
        self.path = path
        self.ui = ui or nullui()
        self.opts = opts or {}

        # following fields are built from fctxs. they exist for perf reason
        self.contents = [f.data() for f in fctxs]
        self.contentlines = pycompat.maplist(mdiff.splitnewlines, self.contents)
        self.linelog = self._buildlinelog()
        if self.ui.debugflag:
            assert self._checkoutlinelog() == self.contents

        # following fields will be filled later
        self.chunkstats = [0, 0]  # [adopted, total : int]
        self.targetlines = []  # [str]
        self.fixups = []  # [(linelog rev, a1, a2, b1, b2)]
        self.finalcontents = []  # [str]
        self.ctxaffected = set()

    def diffwith(self, targetfctx, fm=None):
        """calculate fixups needed by examining the differences between
        self.fctxs[-1] and targetfctx, chunk by chunk.

        targetfctx is the target state we move towards. we may or may not be
        able to get there because not all modified chunks can be amended into
        a non-public fctx unambiguously.

        call this only once, before apply().

        update self.fixups, self.chunkstats, and self.targetlines.
        """
        a = self.contents[-1]
        alines = self.contentlines[-1]
        b = targetfctx.data()
        blines = mdiff.splitnewlines(b)
        self.targetlines = blines

        self.linelog.annotate(self.linelog.maxrev)
        annotated = self.linelog.annotateresult  # [(linelog rev, linenum)]
        assert len(annotated) == len(alines)
        # add a dummy end line to make insertion at the end easier
        if annotated:
            dummyendline = (annotated[-1][0], annotated[-1][1] + 1)
            annotated.append(dummyendline)

        # analyse diff blocks
        for chunk in self._alldiffchunks(a, b, alines, blines):
            newfixups = self._analysediffchunk(chunk, annotated)
            self.chunkstats[0] += bool(newfixups)  # 1 or 0
            self.chunkstats[1] += 1
            self.fixups += newfixups
            if fm is not None:
                self._showchanges(fm, alines, blines, chunk, newfixups)

    def apply(self):
        """apply self.fixups. update self.linelog, self.finalcontents.

        call this only once, before getfinalcontent(), after diffwith().
        """
        # the following is unnecessary, as it's done by "diffwith":
        #   self.linelog.annotate(self.linelog.maxrev)
        for rev, a1, a2, b1, b2 in reversed(self.fixups):
            blines = self.targetlines[b1:b2]
            if self.ui.debugflag:
                idx = (max(rev - 1, 0)) // 2
                self.ui.write(
                    _(b'%s: chunk %d:%d -> %d lines\n')
                    % (short(self.fctxs[idx].node()), a1, a2, len(blines))
                )
            self.linelog.replacelines(rev, a1, a2, b1, b2)
        if self.opts.get(b'edit_lines', False):
            self.finalcontents = self._checkoutlinelogwithedits()
        else:
            self.finalcontents = self._checkoutlinelog()

    def getfinalcontent(self, fctx):
        """(fctx) -> str. get modified file content for a given filecontext"""
        idx = self.fctxs.index(fctx)
        return self.finalcontents[idx]

    def _analysediffchunk(self, chunk, annotated):
        """analyse a different chunk and return new fixups found

        return [] if no lines from the chunk can be safely applied.

        the chunk (or lines) cannot be safely applied, if, for example:
          - the modified (deleted) lines belong to a public changeset
            (self.fctxs[0])
          - the chunk is a pure insertion and the adjacent lines (at most 2
            lines) belong to different non-public changesets, or do not belong
            to any non-public changesets.
          - the chunk is modifying lines from different changesets.
            in this case, if the number of lines deleted equals to the number
            of lines added, assume it's a simple 1:1 map (could be wrong).
            otherwise, give up.
          - the chunk is modifying lines from a single non-public changeset,
            but other revisions touch the area as well. i.e. the lines are
            not continuous as seen from the linelog.
        """
        a1, a2, b1, b2 = chunk
        # find involved indexes from annotate result
        involved = annotated[a1:a2]
        if not involved and annotated:  # a1 == a2 and a is not empty
            # pure insertion, check nearby lines. ignore lines belong
            # to the public (first) changeset (i.e. annotated[i][0] == 1)
            nearbylinenums = {a2, max(0, a1 - 1)}
            involved = [
                annotated[i] for i in nearbylinenums if annotated[i][0] != 1
            ]
        involvedrevs = list({r for r, l in involved})
        newfixups = []
        if len(involvedrevs) == 1 and self._iscontinuous(a1, a2 - 1, True):
            # chunk belongs to a single revision
            rev = involvedrevs[0]
            if rev > 1:
                fixuprev = rev + 1
                newfixups.append((fixuprev, a1, a2, b1, b2))
        elif a2 - a1 == b2 - b1 or b1 == b2:
            # 1:1 line mapping, or chunk was deleted
            for i in pycompat.xrange(a1, a2):
                rev, linenum = annotated[i]
                if rev > 1:
                    if b1 == b2:  # deletion, simply remove that single line
                        nb1 = nb2 = 0
                    else:  # 1:1 line mapping, change the corresponding rev
                        nb1 = b1 + i - a1
                        nb2 = nb1 + 1
                    fixuprev = rev + 1
                    newfixups.append((fixuprev, i, i + 1, nb1, nb2))
        return self._optimizefixups(newfixups)

    @staticmethod
    def _alldiffchunks(a, b, alines, blines):
        """like mdiff.allblocks, but only care about differences"""
        blocks = mdiff.allblocks(a, b, lines1=alines, lines2=blines)
        for chunk, btype in blocks:
            if btype != b'!':
                continue
            yield chunk

    def _buildlinelog(self):
        """calculate the initial linelog based on self.content{,line}s.
        this is similar to running a partial "annotate".
        """
        llog = linelog.linelog()
        a, alines = b'', []
        for i in pycompat.xrange(len(self.contents)):
            b, blines = self.contents[i], self.contentlines[i]
            llrev = i * 2 + 1
            chunks = self._alldiffchunks(a, b, alines, blines)
            for a1, a2, b1, b2 in reversed(list(chunks)):
                llog.replacelines(llrev, a1, a2, b1, b2)
            a, alines = b, blines
        return llog

    def _checkoutlinelog(self):
        """() -> [str]. check out file contents from linelog"""
        contents = []
        for i in pycompat.xrange(len(self.contents)):
            rev = (i + 1) * 2
            self.linelog.annotate(rev)
            content = b''.join(map(self._getline, self.linelog.annotateresult))
            contents.append(content)
        return contents

    def _checkoutlinelogwithedits(self):
        """() -> [str]. prompt all lines for edit"""
        alllines = self.linelog.getalllines()
        # header
        editortext = (
            _(
                b'HG: editing %s\nHG: "y" means the line to the right '
                b'exists in the changeset to the top\nHG:\n'
            )
            % self.fctxs[-1].path()
        )
        # [(idx, fctx)]. hide the dummy emptyfilecontext
        visiblefctxs = [
            (i, f)
            for i, f in enumerate(self.fctxs)
            if not isinstance(f, emptyfilecontext)
        ]
        for i, (j, f) in enumerate(visiblefctxs):
            editortext += _(b'HG: %s/%s %s %s\n') % (
                b'|' * i,
                b'-' * (len(visiblefctxs) - i + 1),
                short(f.node()),
                f.description().split(b'\n', 1)[0],
            )
        editortext += _(b'HG: %s\n') % (b'|' * len(visiblefctxs))
        # figure out the lifetime of a line, this is relatively inefficient,
        # but probably fine
        lineset = defaultdict(lambda: set())  # {(llrev, linenum): {llrev}}
        for i, f in visiblefctxs:
            self.linelog.annotate((i + 1) * 2)
            for l in self.linelog.annotateresult:
                lineset[l].add(i)
        # append lines
        for l in alllines:
            editortext += b'    %s : %s' % (
                b''.join(
                    [
                        (b'y' if i in lineset[l] else b' ')
                        for i, _f in visiblefctxs
                    ]
                ),
                self._getline(l),
            )
        # run editor
        editedtext = self.ui.edit(editortext, b'', action=b'absorb')
        if not editedtext:
            raise error.InputError(_(b'empty editor text'))
        # parse edited result
        contents = [b''] * len(self.fctxs)
        leftpadpos = 4
        colonpos = leftpadpos + len(visiblefctxs) + 1
        for l in mdiff.splitnewlines(editedtext):
            if l.startswith(b'HG:'):
                continue
            if l[colonpos - 1 : colonpos + 2] != b' : ':
                raise error.InputError(_(b'malformed line: %s') % l)
            linecontent = l[colonpos + 2 :]
            for i, ch in enumerate(
                pycompat.bytestr(l[leftpadpos : colonpos - 1])
            ):
                if ch == b'y':
                    contents[visiblefctxs[i][0]] += linecontent
        # chunkstats is hard to calculate if anything changes, therefore
        # set them to just a simple value (1, 1).
        if editedtext != editortext:
            self.chunkstats = [1, 1]
        return contents

    def _getline(self, lineinfo):
        """((rev, linenum)) -> str. convert rev+line number to line content"""
        rev, linenum = lineinfo
        if rev & 1:  # odd: original line taken from fctxs
            return self.contentlines[rev // 2][linenum]
        else:  # even: fixup line from targetfctx
            return self.targetlines[linenum]

    def _iscontinuous(self, a1, a2, closedinterval=False):
        """(a1, a2 : int) -> bool

        check if these lines are continuous. i.e. no other insertions or
        deletions (from other revisions) among these lines.

        closedinterval decides whether a2 should be included or not. i.e. is
        it [a1, a2), or [a1, a2] ?
        """
        if a1 >= a2:
            return True
        llog = self.linelog
        offset1 = llog.getoffset(a1)
        offset2 = llog.getoffset(a2) + int(closedinterval)
        linesinbetween = llog.getalllines(offset1, offset2)
        return len(linesinbetween) == a2 - a1 + int(closedinterval)

    def _optimizefixups(self, fixups):
        """[(rev, a1, a2, b1, b2)] -> [(rev, a1, a2, b1, b2)].
        merge adjacent fixups to make them less fragmented.
        """
        result = []
        pcurrentchunk = [[-1, -1, -1, -1, -1]]

        def pushchunk():
            if pcurrentchunk[0][0] != -1:
                result.append(tuple(pcurrentchunk[0]))

        for i, chunk in enumerate(fixups):
            rev, a1, a2, b1, b2 = chunk
            lastrev = pcurrentchunk[0][0]
            lasta2 = pcurrentchunk[0][2]
            lastb2 = pcurrentchunk[0][4]
            if (
                a1 == lasta2
                and b1 == lastb2
                and rev == lastrev
                and self._iscontinuous(max(a1 - 1, 0), a1)
            ):
                # merge into currentchunk
                pcurrentchunk[0][2] = a2
                pcurrentchunk[0][4] = b2
            else:
                pushchunk()
                pcurrentchunk[0] = list(chunk)
        pushchunk()
        return result

    def _showchanges(self, fm, alines, blines, chunk, fixups):
        def trim(line):
            if line.endswith(b'\n'):
                line = line[:-1]
            return line

        # this is not optimized for perf but _showchanges only gets executed
        # with an extra command-line flag.
        a1, a2, b1, b2 = chunk
        aidxs, bidxs = [0] * (a2 - a1), [0] * (b2 - b1)
        for idx, fa1, fa2, fb1, fb2 in fixups:
            for i in pycompat.xrange(fa1, fa2):
                aidxs[i - a1] = (max(idx, 1) - 1) // 2
            for i in pycompat.xrange(fb1, fb2):
                bidxs[i - b1] = (max(idx, 1) - 1) // 2

        fm.startitem()
        fm.write(
            b'hunk',
            b'        %s\n',
            b'@@ -%d,%d +%d,%d @@' % (a1, a2 - a1, b1, b2 - b1),
            label=b'diff.hunk',
        )
        fm.data(path=self.path, linetype=b'hunk')

        def writeline(idx, diffchar, line, linetype, linelabel):
            fm.startitem()
            node = b''
            if idx:
                ctx = self.fctxs[idx]
                fm.context(fctx=ctx)
                node = ctx.hex()
                self.ctxaffected.add(ctx.changectx())
            fm.write(b'node', b'%-7.7s ', node, label=b'absorb.node')
            fm.write(
                b'diffchar ' + linetype,
                b'%s%s\n',
                diffchar,
                line,
                label=linelabel,
            )
            fm.data(path=self.path, linetype=linetype)

        for i in pycompat.xrange(a1, a2):
            writeline(
                aidxs[i - a1],
                b'-',
                trim(alines[i]),
                b'deleted',
                b'diff.deleted',
            )
        for i in pycompat.xrange(b1, b2):
            writeline(
                bidxs[i - b1],
                b'+',
                trim(blines[i]),
                b'inserted',
                b'diff.inserted',
            )


class fixupstate(object):
    """state needed to run absorb

    internally, it keeps paths and filefixupstates.

    a typical use is like filefixupstates:

        1. call diffwith, to calculate fixups
        2. (optionally), present fixups to the user, or edit fixups
        3. call apply, to apply changes to memory
        4. call commit, to commit changes to hg database
    """

    def __init__(self, stack, ui=None, opts=None):
        """([ctx], ui or None) -> None

        stack: should be linear, and sorted by topo order - oldest first.
        all commits in stack are considered mutable.
        """
        assert stack
        self.ui = ui or nullui()
        self.opts = opts or {}
        self.stack = stack
        self.repo = stack[-1].repo().unfiltered()

        # following fields will be filled later
        self.paths = []  # [str]
        self.status = None  # ctx.status output
        self.fctxmap = {}  # {path: {ctx: fctx}}
        self.fixupmap = {}  # {path: filefixupstate}
        self.replacemap = {}  # {oldnode: newnode or None}
        self.finalnode = None  # head after all fixups
        self.ctxaffected = set()  # ctx that will be absorbed into

    def diffwith(self, targetctx, match=None, fm=None):
        """diff and prepare fixups. update self.fixupmap, self.paths"""
        # only care about modified files
        self.status = self.stack[-1].status(targetctx, match)
        self.paths = []
        # but if --edit-lines is used, the user may want to edit files
        # even if they are not modified
        editopt = self.opts.get(b'edit_lines')
        if not self.status.modified and editopt and match:
            interestingpaths = match.files()
        else:
            interestingpaths = self.status.modified
        # prepare the filefixupstate
        seenfctxs = set()
        # sorting is necessary to eliminate ambiguity for the "double move"
        # case: "hg cp A B; hg cp A C; hg rm A", then only "B" can affect "A".
        for path in sorted(interestingpaths):
            self.ui.debug(b'calculating fixups for %s\n' % path)
            targetfctx = targetctx[path]
            fctxs, ctx2fctx = getfilestack(self.stack, path, seenfctxs)
            # ignore symbolic links or binary, or unchanged files
            if any(
                f.islink() or stringutil.binary(f.data())
                for f in [targetfctx] + fctxs
                if not isinstance(f, emptyfilecontext)
            ):
                continue
            if targetfctx.data() == fctxs[-1].data() and not editopt:
                continue
            seenfctxs.update(fctxs[1:])
            self.fctxmap[path] = ctx2fctx
            fstate = filefixupstate(fctxs, path, ui=self.ui, opts=self.opts)
            if fm is not None:
                fm.startitem()
                fm.plain(b'showing changes for ')
                fm.write(b'path', b'%s\n', path, label=b'absorb.path')
                fm.data(linetype=b'path')
            fstate.diffwith(targetfctx, fm)
            self.fixupmap[path] = fstate
            self.paths.append(path)
            self.ctxaffected.update(fstate.ctxaffected)

    def apply(self):
        """apply fixups to individual filefixupstates"""
        for path, state in pycompat.iteritems(self.fixupmap):
            if self.ui.debugflag:
                self.ui.write(_(b'applying fixups to %s\n') % path)
            state.apply()

    @property
    def chunkstats(self):
        """-> {path: chunkstats}. collect chunkstats from filefixupstates"""
        return {
            path: state.chunkstats
            for path, state in pycompat.iteritems(self.fixupmap)
        }

    def commit(self):
        """commit changes. update self.finalnode, self.replacemap"""
        with self.repo.transaction(b'absorb') as tr:
            self._commitstack()
            self._movebookmarks(tr)
            if self.repo[b'.'].node() in self.replacemap:
                self._moveworkingdirectoryparent()
            self._cleanupoldcommits()
        return self.finalnode

    def printchunkstats(self):
        """print things like '1 of 2 chunk(s) applied'"""
        ui = self.ui
        chunkstats = self.chunkstats
        if ui.verbose:
            # chunkstats for each file
            for path, stat in pycompat.iteritems(chunkstats):
                if stat[0]:
                    ui.write(
                        _(b'%s: %d of %d chunk(s) applied\n')
                        % (path, stat[0], stat[1])
                    )
        elif not ui.quiet:
            # a summary for all files
            stats = chunkstats.values()
            applied, total = (sum(s[i] for s in stats) for i in (0, 1))
            ui.write(_(b'%d of %d chunk(s) applied\n') % (applied, total))

    def _commitstack(self):
        """make new commits. update self.finalnode, self.replacemap.
        it is splitted from "commit" to avoid too much indentation.
        """
        # last node (20-char) committed by us
        lastcommitted = None
        # p1 which overrides the parent of the next commit, "None" means use
        # the original parent unchanged
        nextp1 = None
        for ctx in self.stack:
            memworkingcopy = self._getnewfilecontents(ctx)
            if not memworkingcopy and not lastcommitted:
                # nothing changed, nothing commited
                nextp1 = ctx
                continue
            willbecomenoop = ctx.files() and self._willbecomenoop(
                memworkingcopy, ctx, nextp1
            )
            if self.skip_empty_successor and willbecomenoop:
                # changeset is no longer necessary
                self.replacemap[ctx.node()] = None
                msg = _(b'became empty and was dropped')
            else:
                # changeset needs re-commit
                nodestr = self._commitsingle(memworkingcopy, ctx, p1=nextp1)
                lastcommitted = self.repo[nodestr]
                nextp1 = lastcommitted
                self.replacemap[ctx.node()] = lastcommitted.node()
                if memworkingcopy:
                    if willbecomenoop:
                        msg = _(b'%d file(s) changed, became empty as %s')
                    else:
                        msg = _(b'%d file(s) changed, became %s')
                    msg = msg % (
                        len(memworkingcopy),
                        self._ctx2str(lastcommitted),
                    )
                else:
                    msg = _(b'became %s') % self._ctx2str(lastcommitted)
            if self.ui.verbose and msg:
                self.ui.write(_(b'%s: %s\n') % (self._ctx2str(ctx), msg))
        self.finalnode = lastcommitted and lastcommitted.node()

    def _ctx2str(self, ctx):
        if self.ui.debugflag:
            return b'%d:%s' % (ctx.rev(), ctx.hex())
        else:
            return b'%d:%s' % (ctx.rev(), short(ctx.node()))

    def _getnewfilecontents(self, ctx):
        """(ctx) -> {path: str}

        fetch file contents from filefixupstates.
        return the working copy overrides - files different from ctx.
        """
        result = {}
        for path in self.paths:
            ctx2fctx = self.fctxmap[path]  # {ctx: fctx}
            if ctx not in ctx2fctx:
                continue
            fctx = ctx2fctx[ctx]
            content = fctx.data()
            newcontent = self.fixupmap[path].getfinalcontent(fctx)
            if content != newcontent:
                result[fctx.path()] = newcontent
        return result

    def _movebookmarks(self, tr):
        repo = self.repo
        needupdate = [
            (name, self.replacemap[hsh])
            for name, hsh in pycompat.iteritems(repo._bookmarks)
            if hsh in self.replacemap
        ]
        changes = []
        for name, hsh in needupdate:
            if hsh:
                changes.append((name, hsh))
                if self.ui.verbose:
                    self.ui.write(
                        _(b'moving bookmark %s to %s\n') % (name, hex(hsh))
                    )
            else:
                changes.append((name, None))
                if self.ui.verbose:
                    self.ui.write(_(b'deleting bookmark %s\n') % name)
        repo._bookmarks.applychanges(repo, tr, changes)

    def _moveworkingdirectoryparent(self):
        if not self.finalnode:
            # Find the latest not-{obsoleted,stripped} parent.
            revs = self.repo.revs(b'max(::. - %ln)', self.replacemap.keys())
            ctx = self.repo[revs.first()]
            self.finalnode = ctx.node()
        else:
            ctx = self.repo[self.finalnode]

        dirstate = self.repo.dirstate
        # dirstate.rebuild invalidates fsmonitorstate, causing "hg status" to
        # be slow. in absorb's case, no need to invalidate fsmonitorstate.
        noop = lambda: 0
        restore = noop
        if util.safehasattr(dirstate, '_fsmonitorstate'):
            bak = dirstate._fsmonitorstate.invalidate

            def restore():
                dirstate._fsmonitorstate.invalidate = bak

            dirstate._fsmonitorstate.invalidate = noop
        try:
            with dirstate.parentchange():
                dirstate.rebuild(ctx.node(), ctx.manifest(), self.paths)
        finally:
            restore()

    @staticmethod
    def _willbecomenoop(memworkingcopy, ctx, pctx=None):
        """({path: content}, ctx, ctx) -> bool. test if a commit will be noop

        if it will become an empty commit (does not change anything, after the
        memworkingcopy overrides), return True. otherwise return False.
        """
        if not pctx:
            parents = ctx.parents()
            if len(parents) != 1:
                return False
            pctx = parents[0]
        if ctx.branch() != pctx.branch():
            return False
        if ctx.extra().get(b'close'):
            return False
        # ctx changes more files (not a subset of memworkingcopy)
        if not set(ctx.files()).issubset(set(memworkingcopy)):
            return False
        for path, content in pycompat.iteritems(memworkingcopy):
            if path not in pctx or path not in ctx:
                return False
            fctx = ctx[path]
            pfctx = pctx[path]
            if pfctx.flags() != fctx.flags():
                return False
            if pfctx.data() != content:
                return False
        return True

    def _commitsingle(self, memworkingcopy, ctx, p1=None):
        """(ctx, {path: content}, node) -> node. make a single commit

        the commit is a clone from ctx, with a (optionally) different p1, and
        different file contents replaced by memworkingcopy.
        """
        parents = p1 and (p1, self.repo.nullid)
        extra = ctx.extra()
        if self._useobsolete and self.ui.configbool(b'absorb', b'add-noise'):
            extra[b'absorb_source'] = ctx.hex()

        desc = rewriteutil.update_hash_refs(
            ctx.repo(),
            ctx.description(),
            {
                oldnode: [newnode]
                for oldnode, newnode in self.replacemap.items()
            },
        )
        mctx = overlaycontext(
            memworkingcopy, ctx, parents, extra=extra, desc=desc
        )
        return mctx.commit()

    @util.propertycache
    def _useobsolete(self):
        """() -> bool"""
        return obsolete.isenabled(self.repo, obsolete.createmarkersopt)

    def _cleanupoldcommits(self):
        replacements = {
            k: ([v] if v is not None else [])
            for k, v in pycompat.iteritems(self.replacemap)
        }
        if replacements:
            scmutil.cleanupnodes(
                self.repo, replacements, operation=b'absorb', fixphase=True
            )

    @util.propertycache
    def skip_empty_successor(self):
        return rewriteutil.skip_empty_successor(self.ui, b'absorb')


def _parsechunk(hunk):
    """(crecord.uihunk or patch.recordhunk) -> (path, (a1, a2, [bline]))"""
    if type(hunk) not in (crecord.uihunk, patch.recordhunk):
        return None, None
    path = hunk.header.filename()
    a1 = hunk.fromline + len(hunk.before) - 1
    # remove before and after context
    hunk.before = hunk.after = []
    buf = util.stringio()
    hunk.write(buf)
    patchlines = mdiff.splitnewlines(buf.getvalue())
    # hunk.prettystr() will update hunk.removed
    a2 = a1 + hunk.removed
    blines = [l[1:] for l in patchlines[1:] if not l.startswith(b'-')]
    return path, (a1, a2, blines)


def overlaydiffcontext(ctx, chunks):
    """(ctx, [crecord.uihunk]) -> memctx

    return a memctx with some [1] patches (chunks) applied to ctx.
    [1]: modifications are handled. renames, mode changes, etc. are ignored.
    """
    # sadly the applying-patch logic is hardly reusable, and messy:
    # 1. the core logic "_applydiff" is too heavy - it writes .rej files, it
    #    needs a file stream of a patch and will re-parse it, while we have
    #    structured hunk objects at hand.
    # 2. a lot of different implementations about "chunk" (patch.hunk,
    #    patch.recordhunk, crecord.uihunk)
    # as we only care about applying changes to modified files, no mode
    # change, no binary diff, and no renames, it's probably okay to
    # re-invent the logic using much simpler code here.
    memworkingcopy = {}  # {path: content}
    patchmap = defaultdict(lambda: [])  # {path: [(a1, a2, [bline])]}
    for path, info in map(_parsechunk, chunks):
        if not path or not info:
            continue
        patchmap[path].append(info)
    for path, patches in pycompat.iteritems(patchmap):
        if path not in ctx or not patches:
            continue
        patches.sort(reverse=True)
        lines = mdiff.splitnewlines(ctx[path].data())
        for a1, a2, blines in patches:
            lines[a1:a2] = blines
        memworkingcopy[path] = b''.join(lines)
    return overlaycontext(memworkingcopy, ctx)


def absorb(ui, repo, stack=None, targetctx=None, pats=None, opts=None):
    """pick fixup chunks from targetctx, apply them to stack.

    if targetctx is None, the working copy context will be used.
    if stack is None, the current draft stack will be used.
    return fixupstate.
    """
    if stack is None:
        limit = ui.configint(b'absorb', b'max-stack-size')
        headctx = repo[b'.']
        if len(headctx.parents()) > 1:
            raise error.InputError(_(b'cannot absorb into a merge'))
        stack = getdraftstack(headctx, limit)
        if limit and len(stack) >= limit:
            ui.warn(
                _(
                    b'absorb: only the recent %d changesets will '
                    b'be analysed\n'
                )
                % limit
            )
    if not stack:
        raise error.InputError(_(b'no mutable changeset to change'))
    if targetctx is None:  # default to working copy
        targetctx = repo[None]
    if pats is None:
        pats = ()
    if opts is None:
        opts = {}
    state = fixupstate(stack, ui=ui, opts=opts)
    matcher = scmutil.match(targetctx, pats, opts)
    if opts.get(b'interactive'):
        diff = patch.diff(repo, stack[-1].node(), targetctx.node(), matcher)
        origchunks = patch.parsepatch(diff)
        chunks = cmdutil.recordfilter(ui, origchunks, matcher)[0]
        targetctx = overlaydiffcontext(stack[-1], chunks)
    fm = None
    if opts.get(b'print_changes') or not opts.get(b'apply_changes'):
        fm = ui.formatter(b'absorb', opts)
    state.diffwith(targetctx, matcher, fm)
    if fm is not None:
        fm.startitem()
        fm.write(
            b"count", b"\n%d changesets affected\n", len(state.ctxaffected)
        )
        fm.data(linetype=b'summary')
        for ctx in reversed(stack):
            if ctx not in state.ctxaffected:
                continue
            fm.startitem()
            fm.context(ctx=ctx)
            fm.data(linetype=b'changeset')
            fm.write(b'node', b'%-7.7s ', ctx.hex(), label=b'absorb.node')
            descfirstline = ctx.description().splitlines()[0]
            fm.write(
                b'descfirstline',
                b'%s\n',
                descfirstline,
                label=b'absorb.description',
            )
        fm.end()
    if not opts.get(b'dry_run'):
        if (
            not opts.get(b'apply_changes')
            and state.ctxaffected
            and ui.promptchoice(
                b"apply changes (y/N)? $$ &Yes $$ &No", default=1
            )
        ):
            raise error.CanceledError(_(b'absorb cancelled\n'))

        state.apply()
        if state.commit():
            state.printchunkstats()
        elif not ui.quiet:
            ui.write(_(b'nothing applied\n'))
    return state


@command(
    b'absorb',
    [
        (
            b'a',
            b'apply-changes',
            None,
            _(b'apply changes without prompting for confirmation'),
        ),
        (
            b'p',
            b'print-changes',
            None,
            _(b'always print which changesets are modified by which changes'),
        ),
        (
            b'i',
            b'interactive',
            None,
            _(b'interactively select which chunks to apply'),
        ),
        (
            b'e',
            b'edit-lines',
            None,
            _(
                b'edit what lines belong to which changesets before commit '
                b'(EXPERIMENTAL)'
            ),
        ),
    ]
    + commands.dryrunopts
    + commands.templateopts
    + commands.walkopts,
    _(b'hg absorb [OPTION] [FILE]...'),
    helpcategory=command.CATEGORY_COMMITTING,
    helpbasic=True,
)
def absorbcmd(ui, repo, *pats, **opts):
    """incorporate corrections into the stack of draft changesets

    absorb analyzes each change in your working directory and attempts to
    amend the changed lines into the changesets in your stack that first
    introduced those lines.

    If absorb cannot find an unambiguous changeset to amend for a change,
    that change will be left in the working directory, untouched. They can be
    observed by :hg:`status` or :hg:`diff` afterwards. In other words,
    absorb does not write to the working directory.

    Changesets outside the revset `::. and not public() and not merge()` will
    not be changed.

    Changesets that become empty after applying the changes will be deleted.

    By default, absorb will show what it plans to do and prompt for
    confirmation.  If you are confident that the changes will be absorbed
    to the correct place, run :hg:`absorb -a` to apply the changes
    immediately.

    Returns 0 on success, 1 if all chunks were ignored and nothing amended.
    """
    opts = pycompat.byteskwargs(opts)

    with repo.wlock(), repo.lock():
        if not opts[b'dry_run']:
            cmdutil.checkunfinished(repo)

        state = absorb(ui, repo, pats=pats, opts=opts)
        if sum(s[0] for s in state.chunkstats.values()) == 0:
            return 1
