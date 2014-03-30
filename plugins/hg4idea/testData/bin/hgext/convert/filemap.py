# Copyright 2007 Bryan O'Sullivan <bos@serpentine.com>
# Copyright 2007 Alexis S. L. Carvalho <alexis@cecm.usp.br>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import posixpath
import shlex
from mercurial.i18n import _
from mercurial import util
from common import SKIPREV, converter_source

def rpairs(name):
    e = len(name)
    while e != -1:
        yield name[:e], name[e + 1:]
        e = name.rfind('/', 0, e)
    yield '.', name

def normalize(path):
    ''' We use posixpath.normpath to support cross-platform path format.
    However, it doesn't handle None input. So we wrap it up. '''
    if path is None:
        return None
    return posixpath.normpath(path)

class filemapper(object):
    '''Map and filter filenames when importing.
    A name can be mapped to itself, a new name, or None (omit from new
    repository).'''

    def __init__(self, ui, path=None):
        self.ui = ui
        self.include = {}
        self.exclude = {}
        self.rename = {}
        if path:
            if self.parse(path):
                raise util.Abort(_('errors in filemap'))

    def parse(self, path):
        errs = 0
        def check(name, mapping, listname):
            if not name:
                self.ui.warn(_('%s:%d: path to %s is missing\n') %
                             (lex.infile, lex.lineno, listname))
                return 1
            if name in mapping:
                self.ui.warn(_('%s:%d: %r already in %s list\n') %
                             (lex.infile, lex.lineno, name, listname))
                return 1
            if (name.startswith('/') or
                name.endswith('/') or
                '//' in name):
                self.ui.warn(_('%s:%d: superfluous / in %s %r\n') %
                             (lex.infile, lex.lineno, listname, name))
                return 1
            return 0
        lex = shlex.shlex(open(path), path, True)
        lex.wordchars += '!@#$%^&*()-=+[]{}|;:,./<>?'
        cmd = lex.get_token()
        while cmd:
            if cmd == 'include':
                name = normalize(lex.get_token())
                errs += check(name, self.exclude, 'exclude')
                self.include[name] = name
            elif cmd == 'exclude':
                name = normalize(lex.get_token())
                errs += check(name, self.include, 'include')
                errs += check(name, self.rename, 'rename')
                self.exclude[name] = name
            elif cmd == 'rename':
                src = normalize(lex.get_token())
                dest = normalize(lex.get_token())
                errs += check(src, self.exclude, 'exclude')
                self.rename[src] = dest
            elif cmd == 'source':
                errs += self.parse(normalize(lex.get_token()))
            else:
                self.ui.warn(_('%s:%d: unknown directive %r\n') %
                             (lex.infile, lex.lineno, cmd))
                errs += 1
            cmd = lex.get_token()
        return errs

    def lookup(self, name, mapping):
        name = normalize(name)
        for pre, suf in rpairs(name):
            try:
                return mapping[pre], pre, suf
            except KeyError:
                pass
        return '', name, ''

    def __call__(self, name):
        if self.include:
            inc = self.lookup(name, self.include)[0]
        else:
            inc = name
        if self.exclude:
            exc = self.lookup(name, self.exclude)[0]
        else:
            exc = ''
        if (not self.include and exc) or (len(inc) <= len(exc)):
            return None
        newpre, pre, suf = self.lookup(name, self.rename)
        if newpre:
            if newpre == '.':
                return suf
            if suf:
                if newpre.endswith('/'):
                    return newpre + suf
                return newpre + '/' + suf
            return newpre
        return name

    def active(self):
        return bool(self.include or self.exclude or self.rename)

# This class does two additional things compared to a regular source:
#
# - Filter and rename files.  This is mostly wrapped by the filemapper
#   class above. We hide the original filename in the revision that is
#   returned by getchanges to be able to find things later in getfile.
#
# - Return only revisions that matter for the files we're interested in.
#   This involves rewriting the parents of the original revision to
#   create a graph that is restricted to those revisions.
#
#   This set of revisions includes not only revisions that directly
#   touch files we're interested in, but also merges that merge two
#   or more interesting revisions.

class filemap_source(converter_source):
    def __init__(self, ui, baseconverter, filemap):
        super(filemap_source, self).__init__(ui)
        self.base = baseconverter
        self.filemapper = filemapper(ui, filemap)
        self.commits = {}
        # if a revision rev has parent p in the original revision graph, then
        # rev will have parent self.parentmap[p] in the restricted graph.
        self.parentmap = {}
        # self.wantedancestors[rev] is the set of all ancestors of rev that
        # are in the restricted graph.
        self.wantedancestors = {}
        self.convertedorder = None
        self._rebuilt = False
        self.origparents = {}
        self.children = {}
        self.seenchildren = {}

    def before(self):
        self.base.before()

    def after(self):
        self.base.after()

    def setrevmap(self, revmap):
        # rebuild our state to make things restartable
        #
        # To avoid calling getcommit for every revision that has already
        # been converted, we rebuild only the parentmap, delaying the
        # rebuild of wantedancestors until we need it (i.e. until a
        # merge).
        #
        # We assume the order argument lists the revisions in
        # topological order, so that we can infer which revisions were
        # wanted by previous runs.
        self._rebuilt = not revmap
        seen = {SKIPREV: SKIPREV}
        dummyset = set()
        converted = []
        for rev in revmap.order:
            mapped = revmap[rev]
            wanted = mapped not in seen
            if wanted:
                seen[mapped] = rev
                self.parentmap[rev] = rev
            else:
                self.parentmap[rev] = seen[mapped]
            self.wantedancestors[rev] = dummyset
            arg = seen[mapped]
            if arg == SKIPREV:
                arg = None
            converted.append((rev, wanted, arg))
        self.convertedorder = converted
        return self.base.setrevmap(revmap)

    def rebuild(self):
        if self._rebuilt:
            return True
        self._rebuilt = True
        self.parentmap.clear()
        self.wantedancestors.clear()
        self.seenchildren.clear()
        for rev, wanted, arg in self.convertedorder:
            if rev not in self.origparents:
                self.origparents[rev] = self.getcommit(rev).parents
            if arg is not None:
                self.children[arg] = self.children.get(arg, 0) + 1

        for rev, wanted, arg in self.convertedorder:
            parents = self.origparents[rev]
            if wanted:
                self.mark_wanted(rev, parents)
            else:
                self.mark_not_wanted(rev, arg)
            self._discard(arg, *parents)

        return True

    def getheads(self):
        return self.base.getheads()

    def getcommit(self, rev):
        # We want to save a reference to the commit objects to be able
        # to rewrite their parents later on.
        c = self.commits[rev] = self.base.getcommit(rev)
        for p in c.parents:
            self.children[p] = self.children.get(p, 0) + 1
        return c

    def _cachedcommit(self, rev):
        if rev in self.commits:
            return self.commits[rev]
        return self.base.getcommit(rev)

    def _discard(self, *revs):
        for r in revs:
            if r is None:
                continue
            self.seenchildren[r] = self.seenchildren.get(r, 0) + 1
            if self.seenchildren[r] == self.children[r]:
                del self.wantedancestors[r]
                del self.parentmap[r]
                del self.seenchildren[r]
                if self._rebuilt:
                    del self.children[r]

    def wanted(self, rev, i):
        # Return True if we're directly interested in rev.
        #
        # i is an index selecting one of the parents of rev (if rev
        # has no parents, i is None).  getchangedfiles will give us
        # the list of files that are different in rev and in the parent
        # indicated by i.  If we're interested in any of these files,
        # we're interested in rev.
        try:
            files = self.base.getchangedfiles(rev, i)
        except NotImplementedError:
            raise util.Abort(_("source repository doesn't support --filemap"))
        for f in files:
            if self.filemapper(f):
                return True
        return False

    def mark_not_wanted(self, rev, p):
        # Mark rev as not interesting and update data structures.

        if p is None:
            # A root revision. Use SKIPREV to indicate that it doesn't
            # map to any revision in the restricted graph.  Put SKIPREV
            # in the set of wanted ancestors to simplify code elsewhere
            self.parentmap[rev] = SKIPREV
            self.wantedancestors[rev] = set((SKIPREV,))
            return

        # Reuse the data from our parent.
        self.parentmap[rev] = self.parentmap[p]
        self.wantedancestors[rev] = self.wantedancestors[p]

    def mark_wanted(self, rev, parents):
        # Mark rev ss wanted and update data structures.

        # rev will be in the restricted graph, so children of rev in
        # the original graph should still have rev as a parent in the
        # restricted graph.
        self.parentmap[rev] = rev

        # The set of wanted ancestors of rev is the union of the sets
        # of wanted ancestors of its parents. Plus rev itself.
        wrev = set()
        for p in parents:
            wrev.update(self.wantedancestors[p])
        wrev.add(rev)
        self.wantedancestors[rev] = wrev

    def getchanges(self, rev):
        parents = self.commits[rev].parents
        if len(parents) > 1:
            self.rebuild()

        # To decide whether we're interested in rev we:
        #
        # - calculate what parents rev will have if it turns out we're
        #   interested in it.  If it's going to have more than 1 parent,
        #   we're interested in it.
        #
        # - otherwise, we'll compare it with the single parent we found.
        #   If any of the files we're interested in is different in the
        #   the two revisions, we're interested in rev.

        # A parent p is interesting if its mapped version (self.parentmap[p]):
        # - is not SKIPREV
        # - is still not in the list of parents (we don't want duplicates)
        # - is not an ancestor of the mapped versions of the other parents or
        #   there is no parent in the same branch than the current revision.
        mparents = []
        knownparents = set()
        branch = self.commits[rev].branch
        hasbranchparent = False
        for i, p1 in enumerate(parents):
            mp1 = self.parentmap[p1]
            if mp1 == SKIPREV or mp1 in knownparents:
                continue
            isancestor = util.any(p2 for p2 in parents
                                  if p1 != p2 and mp1 != self.parentmap[p2]
                                  and mp1 in self.wantedancestors[p2])
            if not isancestor and not hasbranchparent and len(parents) > 1:
                # This could be expensive, avoid unnecessary calls.
                if self._cachedcommit(p1).branch == branch:
                    hasbranchparent = True
            mparents.append((p1, mp1, i, isancestor))
            knownparents.add(mp1)
        # Discard parents ancestors of other parents if there is a
        # non-ancestor one on the same branch than current revision.
        if hasbranchparent:
            mparents = [p for p in mparents if not p[3]]
        wp = None
        if mparents:
            wp = max(p[2] for p in mparents)
            mparents = [p[1] for p in mparents]
        elif parents:
            wp = 0

        self.origparents[rev] = parents

        closed = False
        if 'close' in self.commits[rev].extra:
            # A branch closing revision is only useful if one of its
            # parents belong to the branch being closed
            pbranches = [self._cachedcommit(p).branch for p in mparents]
            if branch in pbranches:
                closed = True

        if len(mparents) < 2 and not closed and not self.wanted(rev, wp):
            # We don't want this revision.
            # Update our state and tell the convert process to map this
            # revision to the same revision its parent as mapped to.
            p = None
            if parents:
                p = parents[wp]
            self.mark_not_wanted(rev, p)
            self.convertedorder.append((rev, False, p))
            self._discard(*parents)
            return self.parentmap[rev]

        # We want this revision.
        # Rewrite the parents of the commit object
        self.commits[rev].parents = mparents
        self.mark_wanted(rev, parents)
        self.convertedorder.append((rev, True, None))
        self._discard(*parents)

        # Get the real changes and do the filtering/mapping. To be
        # able to get the files later on in getfile, we hide the
        # original filename in the rev part of the return value.
        changes, copies = self.base.getchanges(rev)
        files = {}
        for f, r in changes:
            newf = self.filemapper(f)
            if newf and (newf != f or newf not in files):
                files[newf] = (f, r)
        files = sorted(files.items())

        ncopies = {}
        for c in copies:
            newc = self.filemapper(c)
            if newc:
                newsource = self.filemapper(copies[c])
                if newsource:
                    ncopies[newc] = newsource

        return files, ncopies

    def getfile(self, name, rev):
        realname, realrev = rev
        return self.base.getfile(realname, realrev)

    def gettags(self):
        return self.base.gettags()

    def hasnativeorder(self):
        return self.base.hasnativeorder()

    def lookuprev(self, rev):
        return self.base.lookuprev(rev)

    def getbookmarks(self):
        return self.base.getbookmarks()
