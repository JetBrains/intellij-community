# Copyright 2016-present Facebook. All Rights Reserved.
#
# context: context needed to annotate a file
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import collections
import contextlib
import os

from mercurial.i18n import _
from mercurial.pycompat import (
    getattr,
    open,
    setattr,
)
from mercurial.node import (
    bin,
    hex,
    short,
)
from mercurial import (
    error,
    linelog as linelogmod,
    lock as lockmod,
    mdiff,
    pycompat,
    scmutil,
    util,
)
from mercurial.utils import (
    hashutil,
    stringutil,
)

from . import (
    error as faerror,
    revmap as revmapmod,
)

# given path, get filelog, cached
@util.lrucachefunc
def _getflog(repo, path):
    return repo.file(path)


# extracted from mercurial.context.basefilectx.annotate
def _parents(f, follow=True):
    # Cut _descendantrev here to mitigate the penalty of lazy linkrev
    # adjustment. Otherwise, p._adjustlinkrev() would walk changelog
    # from the topmost introrev (= srcrev) down to p.linkrev() if it
    # isn't an ancestor of the srcrev.
    f._changeid
    pl = f.parents()

    # Don't return renamed parents if we aren't following.
    if not follow:
        pl = [p for p in pl if p.path() == f.path()]

    # renamed filectx won't have a filelog yet, so set it
    # from the cache to save time
    for p in pl:
        if not '_filelog' in p.__dict__:
            p._filelog = _getflog(f._repo, p.path())

    return pl


# extracted from mercurial.context.basefilectx.annotate. slightly modified
# so it takes a fctx instead of a pair of text and fctx.
def _decorate(fctx):
    text = fctx.data()
    linecount = text.count(b'\n')
    if text and not text.endswith(b'\n'):
        linecount += 1
    return ([(fctx, i) for i in pycompat.xrange(linecount)], text)


# extracted from mercurial.context.basefilectx.annotate. slightly modified
# so it takes an extra "blocks" parameter calculated elsewhere, instead of
# calculating diff here.
def _pair(parent, child, blocks):
    for (a1, a2, b1, b2), t in blocks:
        # Changed blocks ('!') or blocks made only of blank lines ('~')
        # belong to the child.
        if t == b'=':
            child[0][b1:b2] = parent[0][a1:a2]
    return child


# like scmutil.revsingle, but with lru cache, so their states (like manifests)
# could be reused
_revsingle = util.lrucachefunc(scmutil.revsingle)


def resolvefctx(repo, rev, path, resolverev=False, adjustctx=None):
    """(repo, str, str) -> fctx

    get the filectx object from repo, rev, path, in an efficient way.

    if resolverev is True, "rev" is a revision specified by the revset
    language, otherwise "rev" is a nodeid, or a revision number that can
    be consumed by repo.__getitem__.

    if adjustctx is not None, the returned fctx will point to a changeset
    that introduces the change (last modified the file). if adjustctx
    is 'linkrev', trust the linkrev and do not adjust it. this is noticeably
    faster for big repos but is incorrect for some cases.
    """
    if resolverev and not isinstance(rev, int) and rev is not None:
        ctx = _revsingle(repo, rev)
    else:
        ctx = repo[rev]

    # If we don't need to adjust the linkrev, create the filectx using the
    # changectx instead of using ctx[path]. This means it already has the
    # changectx information, so blame -u will be able to look directly at the
    # commitctx object instead of having to resolve it by going through the
    # manifest. In a lazy-manifest world this can prevent us from downloading a
    # lot of data.
    if adjustctx is None:
        # ctx.rev() is None means it's the working copy, which is a special
        # case.
        if ctx.rev() is None:
            fctx = ctx[path]
        else:
            fctx = repo.filectx(path, changeid=ctx.rev())
    else:
        fctx = ctx[path]
        if adjustctx == b'linkrev':
            introrev = fctx.linkrev()
        else:
            introrev = fctx.introrev()
        if introrev != ctx.rev():
            fctx._changeid = introrev
            fctx._changectx = repo[introrev]
    return fctx


# like mercurial.store.encodedir, but use linelog suffixes: .m, .l, .lock
def encodedir(path):
    return (
        path.replace(b'.hg/', b'.hg.hg/')
        .replace(b'.l/', b'.l.hg/')
        .replace(b'.m/', b'.m.hg/')
        .replace(b'.lock/', b'.lock.hg/')
    )


def hashdiffopts(diffopts):
    diffoptstr = stringutil.pprint(
        sorted((k, getattr(diffopts, k)) for k in mdiff.diffopts.defaults)
    )
    return hex(hashutil.sha1(diffoptstr).digest())[:6]


_defaultdiffopthash = hashdiffopts(mdiff.defaultopts)


class annotateopts(object):
    """like mercurial.mdiff.diffopts, but is for annotate

    followrename: follow renames, like "hg annotate -f"
    followmerge: follow p2 of a merge changeset, otherwise p2 is ignored
    """

    defaults = {
        b'diffopts': None,
        b'followrename': True,
        b'followmerge': True,
    }

    def __init__(self, **opts):
        opts = pycompat.byteskwargs(opts)
        for k, v in pycompat.iteritems(self.defaults):
            setattr(self, k, opts.get(k, v))

    @util.propertycache
    def shortstr(self):
        """represent opts in a short string, suitable for a directory name"""
        result = b''
        if not self.followrename:
            result += b'r0'
        if not self.followmerge:
            result += b'm0'
        if self.diffopts is not None:
            assert isinstance(self.diffopts, mdiff.diffopts)
            diffopthash = hashdiffopts(self.diffopts)
            if diffopthash != _defaultdiffopthash:
                result += b'i' + diffopthash
        return result or b'default'


defaultopts = annotateopts()


class _annotatecontext(object):
    """do not use this class directly as it does not use lock to protect
    writes. use "with annotatecontext(...)" instead.
    """

    def __init__(self, repo, path, linelogpath, revmappath, opts):
        self.repo = repo
        self.ui = repo.ui
        self.path = path
        self.opts = opts
        self.linelogpath = linelogpath
        self.revmappath = revmappath
        self._linelog = None
        self._revmap = None
        self._node2path = {}  # {str: str}

    @property
    def linelog(self):
        if self._linelog is None:
            if os.path.exists(self.linelogpath):
                with open(self.linelogpath, b'rb') as f:
                    try:
                        self._linelog = linelogmod.linelog.fromdata(f.read())
                    except linelogmod.LineLogError:
                        self._linelog = linelogmod.linelog()
            else:
                self._linelog = linelogmod.linelog()
        return self._linelog

    @property
    def revmap(self):
        if self._revmap is None:
            self._revmap = revmapmod.revmap(self.revmappath)
        return self._revmap

    def close(self):
        if self._revmap is not None:
            self._revmap.flush()
            self._revmap = None
        if self._linelog is not None:
            with open(self.linelogpath, b'wb') as f:
                f.write(self._linelog.encode())
            self._linelog = None

    __del__ = close

    def rebuild(self):
        """delete linelog and revmap, useful for rebuilding"""
        self.close()
        self._node2path.clear()
        _unlinkpaths([self.revmappath, self.linelogpath])

    @property
    def lastnode(self):
        """return last node in revmap, or None if revmap is empty"""
        if self._revmap is None:
            # fast path, read revmap without loading its full content
            return revmapmod.getlastnode(self.revmappath)
        else:
            return self._revmap.rev2hsh(self._revmap.maxrev)

    def isuptodate(self, master, strict=True):
        """return True if the revmap / linelog is up-to-date, or the file
        does not exist in the master revision. False otherwise.

        it tries to be fast and could return false negatives, because of the
        use of linkrev instead of introrev.

        useful for both server and client to decide whether to update
        fastannotate cache or not.

        if strict is True, even if fctx exists in the revmap, but is not the
        last node, isuptodate will return False. it's good for performance - no
        expensive check was done.

        if strict is False, if fctx exists in the revmap, this function may
        return True. this is useful for the client to skip downloading the
        cache if the client's master is behind the server's.
        """
        lastnode = self.lastnode
        try:
            f = self._resolvefctx(master, resolverev=True)
            # choose linkrev instead of introrev as the check is meant to be
            # *fast*.
            linknode = self.repo.changelog.node(f.linkrev())
            if not strict and lastnode and linknode != lastnode:
                # check if f.node() is in the revmap. note: this loads the
                # revmap and can be slow.
                return self.revmap.hsh2rev(linknode) is not None
            # avoid resolving old manifest, or slow adjustlinkrev to be fast,
            # false negatives are acceptable in this case.
            return linknode == lastnode
        except LookupError:
            # master does not have the file, or the revmap is ahead
            return True

    def annotate(self, rev, master=None, showpath=False, showlines=False):
        """incrementally update the cache so it includes revisions in the main
        branch till 'master'. and run annotate on 'rev', which may or may not be
        included in the main branch.

        if master is None, do not update linelog.

        the first value returned is the annotate result, it is [(node, linenum)]
        by default. [(node, linenum, path)] if showpath is True.

        if showlines is True, a second value will be returned, it is a list of
        corresponding line contents.
        """

        # the fast path test requires commit hash, convert rev number to hash,
        # so it may hit the fast path. note: in the "fctx" mode, the "annotate"
        # command could give us a revision number even if the user passes a
        # commit hash.
        if isinstance(rev, int):
            rev = hex(self.repo.changelog.node(rev))

        # fast path: if rev is in the main branch already
        directly, revfctx = self.canannotatedirectly(rev)
        if directly:
            if self.ui.debugflag:
                self.ui.debug(
                    b'fastannotate: %s: using fast path '
                    b'(resolved fctx: %s)\n'
                    % (
                        self.path,
                        stringutil.pprint(util.safehasattr(revfctx, b'node')),
                    )
                )
            return self.annotatedirectly(revfctx, showpath, showlines)

        # resolve master
        masterfctx = None
        if master:
            try:
                masterfctx = self._resolvefctx(
                    master, resolverev=True, adjustctx=True
                )
            except LookupError:  # master does not have the file
                pass
            else:
                if masterfctx in self.revmap:  # no need to update linelog
                    masterfctx = None

        #                  ... - @ <- rev (can be an arbitrary changeset,
        #                 /                not necessarily a descendant
        #      master -> o                 of master)
        #                |
        #     a merge -> o         'o': new changesets in the main branch
        #                |\        '#': revisions in the main branch that
        #                o *            exist in linelog / revmap
        #                | .       '*': changesets in side branches, or
        # last master -> # .            descendants of master
        #                | .
        #                # *       joint: '#', and is a parent of a '*'
        #                |/
        #     a joint -> # ^^^^ --- side branches
        #                |
        #                ^ --- main branch (in linelog)

        # these DFSes are similar to the traditional annotate algorithm.
        # we cannot really reuse the code for perf reason.

        # 1st DFS calculates merges, joint points, and needed.
        # "needed" is a simple reference counting dict to free items in
        # "hist", reducing its memory usage otherwise could be huge.
        initvisit = [revfctx]
        if masterfctx:
            if masterfctx.rev() is None:
                raise error.Abort(
                    _(b'cannot update linelog to wdir()'),
                    hint=_(b'set fastannotate.mainbranch'),
                )
            initvisit.append(masterfctx)
        visit = initvisit[:]
        pcache = {}
        needed = {revfctx: 1}
        hist = {}  # {fctx: ([(llrev or fctx, linenum)], text)}
        while visit:
            f = visit.pop()
            if f in pcache or f in hist:
                continue
            if f in self.revmap:  # in the old main branch, it's a joint
                llrev = self.revmap.hsh2rev(f.node())
                self.linelog.annotate(llrev)
                result = self.linelog.annotateresult
                hist[f] = (result, f.data())
                continue
            pl = self._parentfunc(f)
            pcache[f] = pl
            for p in pl:
                needed[p] = needed.get(p, 0) + 1
                if p not in pcache:
                    visit.append(p)

        # 2nd (simple) DFS calculates new changesets in the main branch
        # ('o' nodes in # the above graph), so we know when to update linelog.
        newmainbranch = set()
        f = masterfctx
        while f and f not in self.revmap:
            newmainbranch.add(f)
            pl = pcache[f]
            if pl:
                f = pl[0]
            else:
                f = None
                break

        # f, if present, is the position where the last build stopped at, and
        # should be the "master" last time. check to see if we can continue
        # building the linelog incrementally. (we cannot if diverged)
        if masterfctx is not None:
            self._checklastmasterhead(f)

        if self.ui.debugflag:
            if newmainbranch:
                self.ui.debug(
                    b'fastannotate: %s: %d new changesets in the main'
                    b' branch\n' % (self.path, len(newmainbranch))
                )
            elif not hist:  # no joints, no updates
                self.ui.debug(
                    b'fastannotate: %s: linelog cannot help in '
                    b'annotating this revision\n' % self.path
                )

        # prepare annotateresult so we can update linelog incrementally
        self.linelog.annotate(self.linelog.maxrev)

        # 3rd DFS does the actual annotate
        visit = initvisit[:]
        progress = self.ui.makeprogress(
            b'building cache', total=len(newmainbranch)
        )
        while visit:
            f = visit[-1]
            if f in hist:
                visit.pop()
                continue

            ready = True
            pl = pcache[f]
            for p in pl:
                if p not in hist:
                    ready = False
                    visit.append(p)
            if not ready:
                continue

            visit.pop()
            blocks = None  # mdiff blocks, used for appending linelog
            ismainbranch = f in newmainbranch
            # curr is the same as the traditional annotate algorithm,
            # if we only care about linear history (do not follow merge),
            # then curr is not actually used.
            assert f not in hist
            curr = _decorate(f)
            for i, p in enumerate(pl):
                bs = list(self._diffblocks(hist[p][1], curr[1]))
                if i == 0 and ismainbranch:
                    blocks = bs
                curr = _pair(hist[p], curr, bs)
                if needed[p] == 1:
                    del hist[p]
                    del needed[p]
                else:
                    needed[p] -= 1

            hist[f] = curr
            del pcache[f]

            if ismainbranch:  # need to write to linelog
                progress.increment()
                bannotated = None
                if len(pl) == 2 and self.opts.followmerge:  # merge
                    bannotated = curr[0]
                if blocks is None:  # no parents, add an empty one
                    blocks = list(self._diffblocks(b'', curr[1]))
                self._appendrev(f, blocks, bannotated)
            elif showpath:  # not append linelog, but we need to record path
                self._node2path[f.node()] = f.path()

        progress.complete()

        result = [
            ((self.revmap.rev2hsh(fr) if isinstance(fr, int) else fr.node()), l)
            for fr, l in hist[revfctx][0]
        ]  # [(node, linenumber)]
        return self._refineannotateresult(result, revfctx, showpath, showlines)

    def canannotatedirectly(self, rev):
        """(str) -> bool, fctx or node.
        return (True, f) if we can annotate without updating the linelog, pass
        f to annotatedirectly.
        return (False, f) if we need extra calculation. f is the fctx resolved
        from rev.
        """
        result = True
        f = None
        if not isinstance(rev, int) and rev is not None:
            hsh = {20: bytes, 40: bin}.get(len(rev), lambda x: None)(rev)
            if hsh is not None and (hsh, self.path) in self.revmap:
                f = hsh
        if f is None:
            adjustctx = b'linkrev' if self._perfhack else True
            f = self._resolvefctx(rev, adjustctx=adjustctx, resolverev=True)
            result = f in self.revmap
            if not result and self._perfhack:
                # redo the resolution without perfhack - as we are going to
                # do write operations, we need a correct fctx.
                f = self._resolvefctx(rev, adjustctx=True, resolverev=True)
        return result, f

    def annotatealllines(self, rev, showpath=False, showlines=False):
        """(rev : str) -> [(node : str, linenum : int, path : str)]

        the result has the same format with annotate, but include all (including
        deleted) lines up to rev. call this after calling annotate(rev, ...) for
        better performance and accuracy.
        """
        revfctx = self._resolvefctx(rev, resolverev=True, adjustctx=True)

        # find a chain from rev to anything in the mainbranch
        if revfctx not in self.revmap:
            chain = [revfctx]
            a = b''
            while True:
                f = chain[-1]
                pl = self._parentfunc(f)
                if not pl:
                    break
                if pl[0] in self.revmap:
                    a = pl[0].data()
                    break
                chain.append(pl[0])

            # both self.linelog and self.revmap is backed by filesystem. now
            # we want to modify them but do not want to write changes back to
            # files. so we create in-memory objects and copy them. it's like
            # a "fork".
            linelog = linelogmod.linelog()
            linelog.copyfrom(self.linelog)
            linelog.annotate(linelog.maxrev)
            revmap = revmapmod.revmap()
            revmap.copyfrom(self.revmap)

            for f in reversed(chain):
                b = f.data()
                blocks = list(self._diffblocks(a, b))
                self._doappendrev(linelog, revmap, f, blocks)
                a = b
        else:
            # fastpath: use existing linelog, revmap as we don't write to them
            linelog = self.linelog
            revmap = self.revmap

        lines = linelog.getalllines()
        hsh = revfctx.node()
        llrev = revmap.hsh2rev(hsh)
        result = [(revmap.rev2hsh(r), l) for r, l in lines if r <= llrev]
        # cannot use _refineannotateresult since we need custom logic for
        # resolving line contents
        if showpath:
            result = self._addpathtoresult(result, revmap)
        if showlines:
            linecontents = self._resolvelines(result, revmap, linelog)
            result = (result, linecontents)
        return result

    def _resolvelines(self, annotateresult, revmap, linelog):
        """(annotateresult) -> [line]. designed for annotatealllines.
        this is probably the most inefficient code in the whole fastannotate
        directory. but we have made a decision that the linelog does not
        store line contents. so getting them requires random accesses to
        the revlog data, since they can be many, it can be very slow.
        """
        # [llrev]
        revs = [revmap.hsh2rev(l[0]) for l in annotateresult]
        result = [None] * len(annotateresult)
        # {(rev, linenum): [lineindex]}
        key2idxs = collections.defaultdict(list)
        for i in pycompat.xrange(len(result)):
            key2idxs[(revs[i], annotateresult[i][1])].append(i)
        while key2idxs:
            # find an unresolved line and its linelog rev to annotate
            hsh = None
            try:
                for (rev, _linenum), idxs in pycompat.iteritems(key2idxs):
                    if revmap.rev2flag(rev) & revmapmod.sidebranchflag:
                        continue
                    hsh = annotateresult[idxs[0]][0]
                    break
            except StopIteration:  # no more unresolved lines
                return result
            if hsh is None:
                # the remaining key2idxs are not in main branch, resolving them
                # using the hard way...
                revlines = {}
                for (rev, linenum), idxs in pycompat.iteritems(key2idxs):
                    if rev not in revlines:
                        hsh = annotateresult[idxs[0]][0]
                        if self.ui.debugflag:
                            self.ui.debug(
                                b'fastannotate: reading %s line #%d '
                                b'to resolve lines %r\n'
                                % (short(hsh), linenum, idxs)
                            )
                        fctx = self._resolvefctx(hsh, revmap.rev2path(rev))
                        lines = mdiff.splitnewlines(fctx.data())
                        revlines[rev] = lines
                    for idx in idxs:
                        result[idx] = revlines[rev][linenum]
                assert all(x is not None for x in result)
                return result

            # run the annotate and the lines should match to the file content
            self.ui.debug(
                b'fastannotate: annotate %s to resolve lines\n' % short(hsh)
            )
            linelog.annotate(rev)
            fctx = self._resolvefctx(hsh, revmap.rev2path(rev))
            annotated = linelog.annotateresult
            lines = mdiff.splitnewlines(fctx.data())
            if len(lines) != len(annotated):
                raise faerror.CorruptedFileError(b'unexpected annotated lines')
            # resolve lines from the annotate result
            for i, line in enumerate(lines):
                k = annotated[i]
                if k in key2idxs:
                    for idx in key2idxs[k]:
                        result[idx] = line
                    del key2idxs[k]
        return result

    def annotatedirectly(self, f, showpath, showlines):
        """like annotate, but when we know that f is in linelog.
        f can be either a 20-char str (node) or a fctx. this is for perf - in
        the best case, the user provides a node and we don't need to read the
        filelog or construct any filecontext.
        """
        if isinstance(f, bytes):
            hsh = f
        else:
            hsh = f.node()
        llrev = self.revmap.hsh2rev(hsh)
        if not llrev:
            raise faerror.CorruptedFileError(b'%s is not in revmap' % hex(hsh))
        if (self.revmap.rev2flag(llrev) & revmapmod.sidebranchflag) != 0:
            raise faerror.CorruptedFileError(
                b'%s is not in revmap mainbranch' % hex(hsh)
            )
        self.linelog.annotate(llrev)
        result = [
            (self.revmap.rev2hsh(r), l) for r, l in self.linelog.annotateresult
        ]
        return self._refineannotateresult(result, f, showpath, showlines)

    def _refineannotateresult(self, result, f, showpath, showlines):
        """add the missing path or line contents, they can be expensive.
        f could be either node or fctx.
        """
        if showpath:
            result = self._addpathtoresult(result)
        if showlines:
            if isinstance(f, bytes):  # f: node or fctx
                llrev = self.revmap.hsh2rev(f)
                fctx = self._resolvefctx(f, self.revmap.rev2path(llrev))
            else:
                fctx = f
            lines = mdiff.splitnewlines(fctx.data())
            if len(lines) != len(result):  # linelog is probably corrupted
                raise faerror.CorruptedFileError()
            result = (result, lines)
        return result

    def _appendrev(self, fctx, blocks, bannotated=None):
        self._doappendrev(self.linelog, self.revmap, fctx, blocks, bannotated)

    def _diffblocks(self, a, b):
        return mdiff.allblocks(a, b, self.opts.diffopts)

    @staticmethod
    def _doappendrev(linelog, revmap, fctx, blocks, bannotated=None):
        """append a revision to linelog and revmap"""

        def getllrev(f):
            """(fctx) -> int"""
            # f should not be a linelog revision
            if isinstance(f, int):
                raise error.ProgrammingError(b'f should not be an int')
            # f is a fctx, allocate linelog rev on demand
            hsh = f.node()
            rev = revmap.hsh2rev(hsh)
            if rev is None:
                rev = revmap.append(hsh, sidebranch=True, path=f.path())
            return rev

        # append sidebranch revisions to revmap
        siderevs = []
        siderevmap = {}  # node: int
        if bannotated is not None:
            for (a1, a2, b1, b2), op in blocks:
                if op != b'=':
                    # f could be either linelong rev, or fctx.
                    siderevs += [
                        f
                        for f, l in bannotated[b1:b2]
                        if not isinstance(f, int)
                    ]
        siderevs = set(siderevs)
        if fctx in siderevs:  # mainnode must be appended seperately
            siderevs.remove(fctx)
        for f in siderevs:
            siderevmap[f] = getllrev(f)

        # the changeset in the main branch, could be a merge
        llrev = revmap.append(fctx.node(), path=fctx.path())
        siderevmap[fctx] = llrev

        for (a1, a2, b1, b2), op in reversed(blocks):
            if op == b'=':
                continue
            if bannotated is None:
                linelog.replacelines(llrev, a1, a2, b1, b2)
            else:
                blines = [
                    ((r if isinstance(r, int) else siderevmap[r]), l)
                    for r, l in bannotated[b1:b2]
                ]
                linelog.replacelines_vec(llrev, a1, a2, blines)

    def _addpathtoresult(self, annotateresult, revmap=None):
        """(revmap, [(node, linenum)]) -> [(node, linenum, path)]"""
        if revmap is None:
            revmap = self.revmap

        def _getpath(nodeid):
            path = self._node2path.get(nodeid)
            if path is None:
                path = revmap.rev2path(revmap.hsh2rev(nodeid))
                self._node2path[nodeid] = path
            return path

        return [(n, l, _getpath(n)) for n, l in annotateresult]

    def _checklastmasterhead(self, fctx):
        """check if fctx is the master's head last time, raise if not"""
        if fctx is None:
            llrev = 0
        else:
            llrev = self.revmap.hsh2rev(fctx.node())
            if not llrev:
                raise faerror.CannotReuseError()
        if self.linelog.maxrev != llrev:
            raise faerror.CannotReuseError()

    @util.propertycache
    def _parentfunc(self):
        """-> (fctx) -> [fctx]"""
        followrename = self.opts.followrename
        followmerge = self.opts.followmerge

        def parents(f):
            pl = _parents(f, follow=followrename)
            if not followmerge:
                pl = pl[:1]
            return pl

        return parents

    @util.propertycache
    def _perfhack(self):
        return self.ui.configbool(b'fastannotate', b'perfhack')

    def _resolvefctx(self, rev, path=None, **kwds):
        return resolvefctx(self.repo, rev, (path or self.path), **kwds)


def _unlinkpaths(paths):
    """silent, best-effort unlink"""
    for path in paths:
        try:
            util.unlink(path)
        except OSError:
            pass


class pathhelper(object):
    """helper for getting paths for lockfile, linelog and revmap"""

    def __init__(self, repo, path, opts=defaultopts):
        # different options use different directories
        self._vfspath = os.path.join(
            b'fastannotate', opts.shortstr, encodedir(path)
        )
        self._repo = repo

    @property
    def dirname(self):
        return os.path.dirname(self._repo.vfs.join(self._vfspath))

    @property
    def linelogpath(self):
        return self._repo.vfs.join(self._vfspath + b'.l')

    def lock(self):
        return lockmod.lock(self._repo.vfs, self._vfspath + b'.lock')

    @property
    def revmappath(self):
        return self._repo.vfs.join(self._vfspath + b'.m')


@contextlib.contextmanager
def annotatecontext(repo, path, opts=defaultopts, rebuild=False):
    """context needed to perform (fast) annotate on a file

    an annotatecontext of a single file consists of two structures: the
    linelog and the revmap. this function takes care of locking. only 1
    process is allowed to write that file's linelog and revmap at a time.

    when something goes wrong, this function will assume the linelog and the
    revmap are in a bad state, and remove them from disk.

    use this function in the following way:

        with annotatecontext(...) as actx:
            actx. ....
    """
    helper = pathhelper(repo, path, opts)
    util.makedirs(helper.dirname)
    revmappath = helper.revmappath
    linelogpath = helper.linelogpath
    actx = None
    try:
        with helper.lock():
            actx = _annotatecontext(repo, path, linelogpath, revmappath, opts)
            if rebuild:
                actx.rebuild()
            yield actx
    except Exception:
        if actx is not None:
            actx.rebuild()
        repo.ui.debug(b'fastannotate: %s: cache broken and deleted\n' % path)
        raise
    finally:
        if actx is not None:
            actx.close()


def fctxannotatecontext(fctx, follow=True, diffopts=None, rebuild=False):
    """like annotatecontext but get the context from a fctx. convenient when
    used in fctx.annotate
    """
    repo = fctx._repo
    path = fctx._path
    if repo.ui.configbool(b'fastannotate', b'forcefollow', True):
        follow = True
    aopts = annotateopts(diffopts=diffopts, followrename=follow)
    return annotatecontext(repo, path, aopts, rebuild)
