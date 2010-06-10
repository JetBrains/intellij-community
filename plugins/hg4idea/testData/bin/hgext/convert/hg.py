# hg.py - hg backend for convert extension
#
#  Copyright 2005-2009 Matt Mackall <mpm@selenic.com> and others
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


import os, time, cStringIO
from mercurial.i18n import _
from mercurial.node import bin, hex, nullid
from mercurial import hg, util, context, error

from common import NoRepo, commit, converter_source, converter_sink

class mercurial_sink(converter_sink):
    def __init__(self, ui, path):
        converter_sink.__init__(self, ui, path)
        self.branchnames = ui.configbool('convert', 'hg.usebranchnames', True)
        self.clonebranches = ui.configbool('convert', 'hg.clonebranches', False)
        self.tagsbranch = ui.config('convert', 'hg.tagsbranch', 'default')
        self.lastbranch = None
        if os.path.isdir(path) and len(os.listdir(path)) > 0:
            try:
                self.repo = hg.repository(self.ui, path)
                if not self.repo.local():
                    raise NoRepo(_('%s is not a local Mercurial repository')
                                 % path)
            except error.RepoError, err:
                ui.traceback()
                raise NoRepo(err.args[0])
        else:
            try:
                ui.status(_('initializing destination %s repository\n') % path)
                self.repo = hg.repository(self.ui, path, create=True)
                if not self.repo.local():
                    raise NoRepo(_('%s is not a local Mercurial repository')
                                 % path)
                self.created.append(path)
            except error.RepoError:
                ui.traceback()
                raise NoRepo(_("could not create hg repository %s as sink")
                             % path)
        self.lock = None
        self.wlock = None
        self.filemapmode = False

    def before(self):
        self.ui.debug('run hg sink pre-conversion action\n')
        self.wlock = self.repo.wlock()
        self.lock = self.repo.lock()

    def after(self):
        self.ui.debug('run hg sink post-conversion action\n')
        if self.lock:
            self.lock.release()
        if self.wlock:
            self.wlock.release()

    def revmapfile(self):
        return os.path.join(self.path, ".hg", "shamap")

    def authorfile(self):
        return os.path.join(self.path, ".hg", "authormap")

    def getheads(self):
        h = self.repo.changelog.heads()
        return [hex(x) for x in h]

    def setbranch(self, branch, pbranches):
        if not self.clonebranches:
            return

        setbranch = (branch != self.lastbranch)
        self.lastbranch = branch
        if not branch:
            branch = 'default'
        pbranches = [(b[0], b[1] and b[1] or 'default') for b in pbranches]
        pbranch = pbranches and pbranches[0][1] or 'default'

        branchpath = os.path.join(self.path, branch)
        if setbranch:
            self.after()
            try:
                self.repo = hg.repository(self.ui, branchpath)
            except:
                self.repo = hg.repository(self.ui, branchpath, create=True)
            self.before()

        # pbranches may bring revisions from other branches (merge parents)
        # Make sure we have them, or pull them.
        missings = {}
        for b in pbranches:
            try:
                self.repo.lookup(b[0])
            except:
                missings.setdefault(b[1], []).append(b[0])

        if missings:
            self.after()
            for pbranch, heads in missings.iteritems():
                pbranchpath = os.path.join(self.path, pbranch)
                prepo = hg.repository(self.ui, pbranchpath)
                self.ui.note(_('pulling from %s into %s\n') % (pbranch, branch))
                self.repo.pull(prepo, [prepo.lookup(h) for h in heads])
            self.before()

    def _rewritetags(self, source, revmap, data):
        fp = cStringIO.StringIO()
        for line in data.splitlines():
            s = line.split(' ', 1)
            if len(s) != 2:
                continue
            revid = revmap.get(source.lookuprev(s[0]))
            if not revid:
                continue
            fp.write('%s %s\n' % (revid, s[1]))
        return fp.getvalue()

    def putcommit(self, files, copies, parents, commit, source, revmap):

        files = dict(files)
        def getfilectx(repo, memctx, f):
            v = files[f]
            data = source.getfile(f, v)
            e = source.getmode(f, v)
            if f == '.hgtags':
                data = self._rewritetags(source, revmap, data)
            return context.memfilectx(f, data, 'l' in e, 'x' in e, copies.get(f))

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
            parents.append(nullid)
        if len(parents) < 2:
            parents.append(nullid)
        p2 = parents.pop(0)

        text = commit.desc
        extra = commit.extra.copy()
        if self.branchnames and commit.branch:
            extra['branch'] = commit.branch
        if commit.rev:
            extra['convert_revision'] = commit.rev

        while parents:
            p1 = p2
            p2 = parents.pop(0)
            ctx = context.memctx(self.repo, (p1, p2), text, files.keys(),
                                 getfilectx, commit.author, commit.date, extra)
            self.repo.commitctx(ctx)
            text = "(octopus merge fixup)\n"
            p2 = hex(self.repo.changelog.tip())

        if self.filemapmode and nparents == 1:
            man = self.repo.manifest
            mnode = self.repo.changelog.read(bin(p2))[0]
            if not man.cmp(m1node, man.revision(mnode)):
                self.ui.status(_("filtering out empty revision\n"))
                self.repo.rollback()
                return parent
        return p2

    def puttags(self, tags):
        try:
            parentctx = self.repo[self.tagsbranch]
            tagparent = parentctx.node()
        except error.RepoError:
            parentctx = None
            tagparent = nullid

        try:
            oldlines = sorted(parentctx['.hgtags'].data().splitlines(True))
        except:
            oldlines = []

        newlines = sorted([("%s %s\n" % (tags[tag], tag)) for tag in tags])
        if newlines == oldlines:
            return None, None
        data = "".join(newlines)
        def getfilectx(repo, memctx, f):
            return context.memfilectx(f, data, False, False, None)

        self.ui.status(_("updating tags\n"))
        date = "%s 0" % int(time.mktime(time.gmtime()))
        extra = {'branch': self.tagsbranch}
        ctx = context.memctx(self.repo, (tagparent, None), "update tags",
                             [".hgtags"], getfilectx, "convert-repo", date,
                             extra)
        self.repo.commitctx(ctx)
        return hex(self.repo.changelog.tip()), hex(tagparent)

    def setfilemapmode(self, active):
        self.filemapmode = active

class mercurial_source(converter_source):
    def __init__(self, ui, path, rev=None):
        converter_source.__init__(self, ui, path, rev)
        self.ignoreerrors = ui.configbool('convert', 'hg.ignoreerrors', False)
        self.ignored = set()
        self.saverev = ui.configbool('convert', 'hg.saverev', False)
        try:
            self.repo = hg.repository(self.ui, path)
            # try to provoke an exception if this isn't really a hg
            # repo, but some other bogus compatible-looking url
            if not self.repo.local():
                raise error.RepoError()
        except error.RepoError:
            ui.traceback()
            raise NoRepo(_("%s is not a local Mercurial repository") % path)
        self.lastrev = None
        self.lastctx = None
        self._changescache = None
        self.convertfp = None
        # Restrict converted revisions to startrev descendants
        startnode = ui.config('convert', 'hg.startrev')
        if startnode is not None:
            try:
                startnode = self.repo.lookup(startnode)
            except error.RepoError:
                raise util.Abort(_('%s is not a valid start revision')
                                 % startnode)
            startrev = self.repo.changelog.rev(startnode)
            children = {startnode: 1}
            for rev in self.repo.changelog.descendants(startrev):
                children[self.repo.changelog.node(rev)] = 1
            self.keep = children.__contains__
        else:
            self.keep = util.always

    def changectx(self, rev):
        if self.lastrev != rev:
            self.lastctx = self.repo[rev]
            self.lastrev = rev
        return self.lastctx

    def parents(self, ctx):
        return [p for p in ctx.parents() if p and self.keep(p.node())]

    def getheads(self):
        if self.rev:
            heads = [self.repo[self.rev].node()]
        else:
            heads = self.repo.heads()
        return [hex(h) for h in heads if self.keep(h)]

    def getfile(self, name, rev):
        try:
            return self.changectx(rev)[name].data()
        except error.LookupError, err:
            raise IOError(err)

    def getmode(self, name, rev):
        return self.changectx(rev).manifest().flags(name)

    def getchanges(self, rev):
        ctx = self.changectx(rev)
        parents = self.parents(ctx)
        if not parents:
            files = sorted(ctx.manifest())
            if self.ignoreerrors:
                # calling getcopies() is a simple way to detect missing
                # revlogs and populate self.ignored
                self.getcopies(ctx, parents, files)
            return [(f, rev) for f in files if f not in self.ignored], {}
        if self._changescache and self._changescache[0] == rev:
            m, a, r = self._changescache[1]
        else:
            m, a, r = self.repo.status(parents[0].node(), ctx.node())[:3]
        # getcopies() detects missing revlogs early, run it before
        # filtering the changes.
        copies = self.getcopies(ctx, parents, m + a)
        changes = [(name, rev) for name in m + a + r
                   if name not in self.ignored]
        return sorted(changes), copies

    def getcopies(self, ctx, parents, files):
        copies = {}
        for name in files:
            if name in self.ignored:
                continue
            try:
                copysource, copynode = ctx.filectx(name).renamed()
                if copysource in self.ignored or not self.keep(copynode):
                    continue
                # Ignore copy sources not in parent revisions
                found = False
                for p in parents:
                    if copysource in p:
                        found = True
                        break
                if not found:
                    continue
                copies[name] = copysource
            except TypeError:
                pass
            except error.LookupError, e:
                if not self.ignoreerrors:
                    raise
                self.ignored.add(name)
                self.ui.warn(_('ignoring: %s\n') % e)
        return copies

    def getcommit(self, rev):
        ctx = self.changectx(rev)
        parents = [p.hex() for p in self.parents(ctx)]
        if self.saverev:
            crev = rev
        else:
            crev = None
        return commit(author=ctx.user(), date=util.datestr(ctx.date()),
                      desc=ctx.description(), rev=crev, parents=parents,
                      branch=ctx.branch(), extra=ctx.extra(),
                      sortkey=ctx.rev())

    def gettags(self):
        tags = [t for t in self.repo.tagslist() if t[0] != 'tip']
        return dict([(name, hex(node)) for name, node in tags
                     if self.keep(node)])

    def getchangedfiles(self, rev, i):
        ctx = self.changectx(rev)
        parents = self.parents(ctx)
        if not parents and i is None:
            i = 0
            changes = [], ctx.manifest().keys(), []
        else:
            i = i or 0
            changes = self.repo.status(parents[i].node(), ctx.node())[:3]
        changes = [[f for f in l if f not in self.ignored] for l in changes]

        if i == 0:
            self._changescache = (rev, changes)

        return changes[0] + changes[1] + changes[2]

    def converted(self, rev, destrev):
        if self.convertfp is None:
            self.convertfp = open(os.path.join(self.path, '.hg', 'shamap'),
                                  'a')
        self.convertfp.write('%s %s\n' % (destrev, rev))
        self.convertfp.flush()

    def before(self):
        self.ui.debug('run hg source pre-conversion action\n')

    def after(self):
        self.ui.debug('run hg source post-conversion action\n')

    def hasnativeorder(self):
        return True

    def lookuprev(self, rev):
        try:
            return hex(self.repo.lookup(rev))
        except error.RepoError:
            return None
