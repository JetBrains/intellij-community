# hg.py - hg backend for convert extension
#
#  Copyright 2005-2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# Notes for hg->hg conversion:
#
# * Old versions of Mercurial didn't trim the whitespace from the ends
#   of commit messages, but new versions do.  Changesets created by
#   those older versions, then converted, may thus have different
#   hashes for changesets that are otherwise identical.
#
# * Using "--config convert.hg.saverev=true" will make the source
#   identifier to be stored in the converted revision. This will cause
#   the converted revision to have a different identity than the
#   source.
from __future__ import absolute_import

import os
import re
import time

from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial.node import (
    bin,
    hex,
    sha1nodeconstants,
)
from mercurial import (
    bookmarks,
    context,
    error,
    exchange,
    hg,
    lock as lockmod,
    merge as mergemod,
    phases,
    pycompat,
    scmutil,
    util,
)
from mercurial.utils import dateutil

stringio = util.stringio

from . import common

mapfile = common.mapfile
NoRepo = common.NoRepo

sha1re = re.compile(br'\b[0-9a-f]{12,40}\b')


class mercurial_sink(common.converter_sink):
    def __init__(self, ui, repotype, path):
        common.converter_sink.__init__(self, ui, repotype, path)
        self.branchnames = ui.configbool(b'convert', b'hg.usebranchnames')
        self.clonebranches = ui.configbool(b'convert', b'hg.clonebranches')
        self.tagsbranch = ui.config(b'convert', b'hg.tagsbranch')
        self.lastbranch = None
        if os.path.isdir(path) and len(os.listdir(path)) > 0:
            try:
                self.repo = hg.repository(self.ui, path)
                if not self.repo.local():
                    raise NoRepo(
                        _(b'%s is not a local Mercurial repository') % path
                    )
            except error.RepoError as err:
                ui.traceback()
                raise NoRepo(err.args[0])
        else:
            try:
                ui.status(_(b'initializing destination %s repository\n') % path)
                self.repo = hg.repository(self.ui, path, create=True)
                if not self.repo.local():
                    raise NoRepo(
                        _(b'%s is not a local Mercurial repository') % path
                    )
                self.created.append(path)
            except error.RepoError:
                ui.traceback()
                raise NoRepo(
                    _(b"could not create hg repository %s as sink") % path
                )
        self.lock = None
        self.wlock = None
        self.filemapmode = False
        self.subrevmaps = {}

    def before(self):
        self.ui.debug(b'run hg sink pre-conversion action\n')
        self.wlock = self.repo.wlock()
        self.lock = self.repo.lock()

    def after(self):
        self.ui.debug(b'run hg sink post-conversion action\n')
        if self.lock:
            self.lock.release()
        if self.wlock:
            self.wlock.release()

    def revmapfile(self):
        return self.repo.vfs.join(b"shamap")

    def authorfile(self):
        return self.repo.vfs.join(b"authormap")

    def setbranch(self, branch, pbranches):
        if not self.clonebranches:
            return

        setbranch = branch != self.lastbranch
        self.lastbranch = branch
        if not branch:
            branch = b'default'
        pbranches = [(b[0], b[1] and b[1] or b'default') for b in pbranches]

        branchpath = os.path.join(self.path, branch)
        if setbranch:
            self.after()
            try:
                self.repo = hg.repository(self.ui, branchpath)
            except Exception:
                self.repo = hg.repository(self.ui, branchpath, create=True)
            self.before()

        # pbranches may bring revisions from other branches (merge parents)
        # Make sure we have them, or pull them.
        missings = {}
        for b in pbranches:
            try:
                self.repo.lookup(b[0])
            except Exception:
                missings.setdefault(b[1], []).append(b[0])

        if missings:
            self.after()
            for pbranch, heads in sorted(pycompat.iteritems(missings)):
                pbranchpath = os.path.join(self.path, pbranch)
                prepo = hg.peer(self.ui, {}, pbranchpath)
                self.ui.note(
                    _(b'pulling from %s into %s\n') % (pbranch, branch)
                )
                exchange.pull(
                    self.repo, prepo, [prepo.lookup(h) for h in heads]
                )
            self.before()

    def _rewritetags(self, source, revmap, data):
        fp = stringio()
        for line in data.splitlines():
            s = line.split(b' ', 1)
            if len(s) != 2:
                self.ui.warn(_(b'invalid tag entry: "%s"\n') % line)
                fp.write(b'%s\n' % line)  # Bogus, but keep for hash stability
                continue
            revid = revmap.get(source.lookuprev(s[0]))
            if not revid:
                if s[0] == sha1nodeconstants.nullhex:
                    revid = s[0]
                else:
                    # missing, but keep for hash stability
                    self.ui.warn(_(b'missing tag entry: "%s"\n') % line)
                    fp.write(b'%s\n' % line)
                    continue
            fp.write(b'%s %s\n' % (revid, s[1]))
        return fp.getvalue()

    def _rewritesubstate(self, source, data):
        fp = stringio()
        for line in data.splitlines():
            s = line.split(b' ', 1)
            if len(s) != 2:
                continue

            revid = s[0]
            subpath = s[1]
            if revid != sha1nodeconstants.nullhex:
                revmap = self.subrevmaps.get(subpath)
                if revmap is None:
                    revmap = mapfile(
                        self.ui, self.repo.wjoin(subpath, b'.hg/shamap')
                    )
                    self.subrevmaps[subpath] = revmap

                    # It is reasonable that one or more of the subrepos don't
                    # need to be converted, in which case they can be cloned
                    # into place instead of converted.  Therefore, only warn
                    # once.
                    msg = _(b'no ".hgsubstate" updates will be made for "%s"\n')
                    if len(revmap) == 0:
                        sub = self.repo.wvfs.reljoin(subpath, b'.hg')

                        if self.repo.wvfs.exists(sub):
                            self.ui.warn(msg % subpath)

                newid = revmap.get(revid)
                if not newid:
                    if len(revmap) > 0:
                        self.ui.warn(
                            _(b"%s is missing from %s/.hg/shamap\n")
                            % (revid, subpath)
                        )
                else:
                    revid = newid

            fp.write(b'%s %s\n' % (revid, subpath))

        return fp.getvalue()

    def _calculatemergedfiles(self, source, p1ctx, p2ctx):
        """Calculates the files from p2 that we need to pull in when merging p1
        and p2, given that the merge is coming from the given source.

        This prevents us from losing files that only exist in the target p2 and
        that don't come from the source repo (like if you're merging multiple
        repositories together).
        """
        anc = [p1ctx.ancestor(p2ctx)]
        # Calculate what files are coming from p2
        # TODO: mresult.commitinfo might be able to get that info
        mresult = mergemod.calculateupdates(
            self.repo,
            p1ctx,
            p2ctx,
            anc,
            branchmerge=True,
            force=True,
            acceptremote=False,
            followcopies=False,
        )

        for file, (action, info, msg) in mresult.filemap():
            if source.targetfilebelongstosource(file):
                # If the file belongs to the source repo, ignore the p2
                # since it will be covered by the existing fileset.
                continue

            # If the file requires actual merging, abort. We don't have enough
            # context to resolve merges correctly.
            if action in [b'm', b'dm', b'cd', b'dc']:
                raise error.Abort(
                    _(
                        b"unable to convert merge commit "
                        b"since target parents do not merge cleanly (file "
                        b"%s, parents %s and %s)"
                    )
                    % (file, p1ctx, p2ctx)
                )
            elif action == b'k':
                # 'keep' means nothing changed from p1
                continue
            else:
                # Any other change means we want to take the p2 version
                yield file

    def putcommit(
        self, files, copies, parents, commit, source, revmap, full, cleanp2
    ):
        files = dict(files)

        def getfilectx(repo, memctx, f):
            if p2ctx and f in p2files and f not in copies:
                self.ui.debug(b'reusing %s from p2\n' % f)
                try:
                    return p2ctx[f]
                except error.ManifestLookupError:
                    # If the file doesn't exist in p2, then we're syncing a
                    # delete, so just return None.
                    return None
            try:
                v = files[f]
            except KeyError:
                return None
            data, mode = source.getfile(f, v)
            if data is None:
                return None
            if f == b'.hgtags':
                data = self._rewritetags(source, revmap, data)
            if f == b'.hgsubstate':
                data = self._rewritesubstate(source, data)
            return context.memfilectx(
                self.repo,
                memctx,
                f,
                data,
                b'l' in mode,
                b'x' in mode,
                copies.get(f),
            )

        pl = []
        for p in parents:
            if p not in pl:
                pl.append(p)
        parents = pl
        nparents = len(parents)
        if self.filemapmode and nparents == 1:
            m1node = self.repo.changelog.read(bin(parents[0]))[0]
            parent = parents[0]

        if len(parents) < 2:
            parents.append(self.repo.nullid)
        if len(parents) < 2:
            parents.append(self.repo.nullid)
        p2 = parents.pop(0)

        text = commit.desc

        sha1s = re.findall(sha1re, text)
        for sha1 in sha1s:
            oldrev = source.lookuprev(sha1)
            newrev = revmap.get(oldrev)
            if newrev is not None:
                text = text.replace(sha1, newrev[: len(sha1)])

        extra = commit.extra.copy()

        sourcename = self.repo.ui.config(b'convert', b'hg.sourcename')
        if sourcename:
            extra[b'convert_source'] = sourcename

        for label in (
            b'source',
            b'transplant_source',
            b'rebase_source',
            b'intermediate-source',
        ):
            node = extra.get(label)

            if node is None:
                continue

            # Only transplant stores its reference in binary
            if label == b'transplant_source':
                node = hex(node)

            newrev = revmap.get(node)
            if newrev is not None:
                if label == b'transplant_source':
                    newrev = bin(newrev)

                extra[label] = newrev

        if self.branchnames and commit.branch:
            extra[b'branch'] = commit.branch
        if commit.rev and commit.saverev:
            extra[b'convert_revision'] = commit.rev

        while parents:
            p1 = p2
            p2 = parents.pop(0)
            p1ctx = self.repo[p1]
            p2ctx = None
            if p2 != self.repo.nullid:
                p2ctx = self.repo[p2]
            fileset = set(files)
            if full:
                fileset.update(self.repo[p1])
                fileset.update(self.repo[p2])

            if p2ctx:
                p2files = set(cleanp2)
                for file in self._calculatemergedfiles(source, p1ctx, p2ctx):
                    p2files.add(file)
                    fileset.add(file)

            ctx = context.memctx(
                self.repo,
                (p1, p2),
                text,
                fileset,
                getfilectx,
                commit.author,
                commit.date,
                extra,
            )

            # We won't know if the conversion changes the node until after the
            # commit, so copy the source's phase for now.
            self.repo.ui.setconfig(
                b'phases',
                b'new-commit',
                phases.phasenames[commit.phase],
                b'convert',
            )

            with self.repo.transaction(b"convert") as tr:
                if self.repo.ui.config(b'convert', b'hg.preserve-hash'):
                    origctx = commit.ctx
                else:
                    origctx = None
                node = hex(self.repo.commitctx(ctx, origctx=origctx))

                # If the node value has changed, but the phase is lower than
                # draft, set it back to draft since it hasn't been exposed
                # anywhere.
                if commit.rev != node:
                    ctx = self.repo[node]
                    if ctx.phase() < phases.draft:
                        phases.registernew(
                            self.repo, tr, phases.draft, [ctx.rev()]
                        )

            text = b"(octopus merge fixup)\n"
            p2 = node

        if self.filemapmode and nparents == 1:
            man = self.repo.manifestlog.getstorage(b'')
            mnode = self.repo.changelog.read(bin(p2))[0]
            closed = b'close' in commit.extra
            if not closed and not man.cmp(m1node, man.revision(mnode)):
                self.ui.status(_(b"filtering out empty revision\n"))
                self.repo.rollback(force=True)
                return parent
        return p2

    def puttags(self, tags):
        tagparent = self.repo.branchtip(self.tagsbranch, ignoremissing=True)
        tagparent = tagparent or self.repo.nullid

        oldlines = set()
        for branch, heads in pycompat.iteritems(self.repo.branchmap()):
            for h in heads:
                if b'.hgtags' in self.repo[h]:
                    oldlines.update(
                        set(self.repo[h][b'.hgtags'].data().splitlines(True))
                    )
        oldlines = sorted(list(oldlines))

        newlines = sorted([(b"%s %s\n" % (tags[tag], tag)) for tag in tags])
        if newlines == oldlines:
            return None, None

        # if the old and new tags match, then there is nothing to update
        oldtags = set()
        newtags = set()
        for line in oldlines:
            s = line.strip().split(b' ', 1)
            if len(s) != 2:
                continue
            oldtags.add(s[1])
        for line in newlines:
            s = line.strip().split(b' ', 1)
            if len(s) != 2:
                continue
            if s[1] not in oldtags:
                newtags.add(s[1].strip())

        if not newtags:
            return None, None

        data = b"".join(newlines)

        def getfilectx(repo, memctx, f):
            return context.memfilectx(repo, memctx, f, data, False, False, None)

        self.ui.status(_(b"updating tags\n"))
        date = b"%d 0" % int(time.mktime(time.gmtime()))
        extra = {b'branch': self.tagsbranch}
        ctx = context.memctx(
            self.repo,
            (tagparent, None),
            b"update tags",
            [b".hgtags"],
            getfilectx,
            b"convert-repo",
            date,
            extra,
        )
        node = self.repo.commitctx(ctx)
        return hex(node), hex(tagparent)

    def setfilemapmode(self, active):
        self.filemapmode = active

    def putbookmarks(self, updatedbookmark):
        if not len(updatedbookmark):
            return
        wlock = lock = tr = None
        try:
            wlock = self.repo.wlock()
            lock = self.repo.lock()
            tr = self.repo.transaction(b'bookmark')
            self.ui.status(_(b"updating bookmarks\n"))
            destmarks = self.repo._bookmarks
            changes = [
                (bookmark, bin(updatedbookmark[bookmark]))
                for bookmark in updatedbookmark
            ]
            destmarks.applychanges(self.repo, tr, changes)
            tr.close()
        finally:
            lockmod.release(lock, wlock, tr)

    def hascommitfrommap(self, rev):
        # the exact semantics of clonebranches is unclear so we can't say no
        return rev in self.repo or self.clonebranches

    def hascommitforsplicemap(self, rev):
        if rev not in self.repo and self.clonebranches:
            raise error.Abort(
                _(
                    b'revision %s not found in destination '
                    b'repository (lookups with clonebranches=true '
                    b'are not implemented)'
                )
                % rev
            )
        return rev in self.repo


class mercurial_source(common.converter_source):
    def __init__(self, ui, repotype, path, revs=None):
        common.converter_source.__init__(self, ui, repotype, path, revs)
        self.ignoreerrors = ui.configbool(b'convert', b'hg.ignoreerrors')
        self.ignored = set()
        self.saverev = ui.configbool(b'convert', b'hg.saverev')
        try:
            self.repo = hg.repository(self.ui, path)
            # try to provoke an exception if this isn't really a hg
            # repo, but some other bogus compatible-looking url
            if not self.repo.local():
                raise error.RepoError
        except error.RepoError:
            ui.traceback()
            raise NoRepo(_(b"%s is not a local Mercurial repository") % path)
        self.lastrev = None
        self.lastctx = None
        self._changescache = None, None
        self.convertfp = None
        # Restrict converted revisions to startrev descendants
        startnode = ui.config(b'convert', b'hg.startrev')
        hgrevs = ui.config(b'convert', b'hg.revs')
        if hgrevs is None:
            if startnode is not None:
                try:
                    startnode = self.repo.lookup(startnode)
                except error.RepoError:
                    raise error.Abort(
                        _(b'%s is not a valid start revision') % startnode
                    )
                startrev = self.repo.changelog.rev(startnode)
                children = {startnode: 1}
                for r in self.repo.changelog.descendants([startrev]):
                    children[self.repo.changelog.node(r)] = 1
                self.keep = children.__contains__
            else:
                self.keep = util.always
            if revs:
                self._heads = [self.repo.lookup(r) for r in revs]
            else:
                self._heads = self.repo.heads()
        else:
            if revs or startnode is not None:
                raise error.Abort(
                    _(
                        b'hg.revs cannot be combined with '
                        b'hg.startrev or --rev'
                    )
                )
            nodes = set()
            parents = set()
            for r in scmutil.revrange(self.repo, [hgrevs]):
                ctx = self.repo[r]
                nodes.add(ctx.node())
                parents.update(p.node() for p in ctx.parents())
            self.keep = nodes.__contains__
            self._heads = nodes - parents

    def _changectx(self, rev):
        if self.lastrev != rev:
            self.lastctx = self.repo[rev]
            self.lastrev = rev
        return self.lastctx

    def _parents(self, ctx):
        return [p for p in ctx.parents() if p and self.keep(p.node())]

    def getheads(self):
        return [hex(h) for h in self._heads if self.keep(h)]

    def getfile(self, name, rev):
        try:
            fctx = self._changectx(rev)[name]
            return fctx.data(), fctx.flags()
        except error.LookupError:
            return None, None

    def _changedfiles(self, ctx1, ctx2):
        ma, r = [], []
        maappend = ma.append
        rappend = r.append
        d = ctx1.manifest().diff(ctx2.manifest())
        for f, ((node1, flag1), (node2, flag2)) in pycompat.iteritems(d):
            if node2 is None:
                rappend(f)
            else:
                maappend(f)
        return ma, r

    def getchanges(self, rev, full):
        ctx = self._changectx(rev)
        parents = self._parents(ctx)
        if full or not parents:
            files = copyfiles = ctx.manifest()
        if parents:
            if self._changescache[0] == rev:
                ma, r = self._changescache[1]
            else:
                ma, r = self._changedfiles(parents[0], ctx)
            if not full:
                files = ma + r
            copyfiles = ma
        # _getcopies() is also run for roots and before filtering so missing
        # revlogs are detected early
        copies = self._getcopies(ctx, parents, copyfiles)
        cleanp2 = set()
        if len(parents) == 2:
            d = parents[1].manifest().diff(ctx.manifest(), clean=True)
            for f, value in pycompat.iteritems(d):
                if value is None:
                    cleanp2.add(f)
        changes = [(f, rev) for f in files if f not in self.ignored]
        changes.sort()
        return changes, copies, cleanp2

    def _getcopies(self, ctx, parents, files):
        copies = {}
        for name in files:
            if name in self.ignored:
                continue
            try:
                copysource = ctx.filectx(name).copysource()
                if copysource in self.ignored:
                    continue
                # Ignore copy sources not in parent revisions
                if not any(copysource in p for p in parents):
                    continue
                copies[name] = copysource
            except TypeError:
                pass
            except error.LookupError as e:
                if not self.ignoreerrors:
                    raise
                self.ignored.add(name)
                self.ui.warn(_(b'ignoring: %s\n') % e)
        return copies

    def getcommit(self, rev):
        ctx = self._changectx(rev)
        _parents = self._parents(ctx)
        parents = [p.hex() for p in _parents]
        optparents = [p.hex() for p in ctx.parents() if p and p not in _parents]
        crev = rev

        return common.commit(
            author=ctx.user(),
            date=dateutil.datestr(ctx.date(), b'%Y-%m-%d %H:%M:%S %1%2'),
            desc=ctx.description(),
            rev=crev,
            parents=parents,
            optparents=optparents,
            branch=ctx.branch(),
            extra=ctx.extra(),
            sortkey=ctx.rev(),
            saverev=self.saverev,
            phase=ctx.phase(),
            ctx=ctx,
        )

    def numcommits(self):
        return len(self.repo)

    def gettags(self):
        # This will get written to .hgtags, filter non global tags out.
        tags = [
            t
            for t in self.repo.tagslist()
            if self.repo.tagtype(t[0]) == b'global'
        ]
        return {name: hex(node) for name, node in tags if self.keep(node)}

    def getchangedfiles(self, rev, i):
        ctx = self._changectx(rev)
        parents = self._parents(ctx)
        if not parents and i is None:
            i = 0
            ma, r = ctx.manifest().keys(), []
        else:
            i = i or 0
            ma, r = self._changedfiles(parents[i], ctx)
        ma, r = [[f for f in l if f not in self.ignored] for l in (ma, r)]

        if i == 0:
            self._changescache = (rev, (ma, r))

        return ma + r

    def converted(self, rev, destrev):
        if self.convertfp is None:
            self.convertfp = open(self.repo.vfs.join(b'shamap'), b'ab')
        self.convertfp.write(util.tonativeeol(b'%s %s\n' % (destrev, rev)))
        self.convertfp.flush()

    def before(self):
        self.ui.debug(b'run hg source pre-conversion action\n')

    def after(self):
        self.ui.debug(b'run hg source post-conversion action\n')

    def hasnativeorder(self):
        return True

    def hasnativeclose(self):
        return True

    def lookuprev(self, rev):
        try:
            return hex(self.repo.lookup(rev))
        except (error.RepoError, error.LookupError):
            return None

    def getbookmarks(self):
        return bookmarks.listbookmarks(self.repo)

    def checkrevformat(self, revstr, mapname=b'splicemap'):
        """Mercurial, revision string is a 40 byte hex"""
        self.checkhexformat(revstr, mapname)
