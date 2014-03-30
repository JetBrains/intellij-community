# unionrepo.py - repository class for viewing union of repository changesets
#
# Derived from bundlerepo.py
# Copyright 2006, 2007 Benoit Boissinot <bboissin@gmail.com>
# Copyright 2013 Unity Technologies, Mads Kiilerich <madski@unity3d.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Repository class for "in-memory pull" of one local repository to another,
allowing operations like diff and log with revsets.
"""

from node import nullid
from i18n import _
import os
import util, mdiff, cmdutil, scmutil
import localrepo, changelog, manifest, filelog, revlog

class unionrevlog(revlog.revlog):
    def __init__(self, opener, indexfile, revlog2, linkmapper):
        # How it works:
        # To retrieve a revision, we just need to know the node id so we can
        # look it up in revlog2.
        #
        # To differentiate a rev in the second revlog from a rev in the revlog,
        # we check revision against repotiprev.
        opener = scmutil.readonlyvfs(opener)
        revlog.revlog.__init__(self, opener, indexfile)
        self.revlog2 = revlog2

        n = len(self)
        self.repotiprev = n - 1
        self.bundlerevs = set() # used by 'bundle()' revset expression
        for rev2 in self.revlog2:
            rev = self.revlog2.index[rev2]
            # rev numbers - in revlog2, very different from self.rev
            _start, _csize, _rsize, _base, linkrev, p1rev, p2rev, node = rev

            if linkmapper is None: # link is to same revlog
                assert linkrev == rev2 # we never link back
                link = n
            else: # rev must be mapped from repo2 cl to unified cl by linkmapper
                link = linkmapper(linkrev)

            if node in self.nodemap:
                # this happens for the common revlog revisions
                self.bundlerevs.add(self.nodemap[node])
                continue

            p1node = self.revlog2.node(p1rev)
            p2node = self.revlog2.node(p2rev)

            e = (None, None, None, None,
                 link, self.rev(p1node), self.rev(p2node), node)
            self.index.insert(-1, e)
            self.nodemap[node] = n
            self.bundlerevs.add(n)
            n += 1

    def _chunk(self, rev):
        if rev <= self.repotiprev:
            return revlog.revlog._chunk(self, rev)
        return self.revlog2._chunk(self.node(rev))

    def revdiff(self, rev1, rev2):
        """return or calculate a delta between two revisions"""
        if rev1 > self.repotiprev and rev2 > self.repotiprev:
            return self.revlog2.revdiff(
                self.revlog2.rev(self.node(rev1)),
                self.revlog2.rev(self.node(rev2)))
        elif rev1 <= self.repotiprev and rev2 <= self.repotiprev:
            return revlog.revlog.revdiff(self, rev1, rev2)

        return mdiff.textdiff(self.revision(self.node(rev1)),
                              self.revision(self.node(rev2)))

    def revision(self, nodeorrev):
        """return an uncompressed revision of a given node or revision
        number.
        """
        if isinstance(nodeorrev, int):
            rev = nodeorrev
            node = self.node(rev)
        else:
            node = nodeorrev
            rev = self.rev(node)

        if node == nullid:
            return ""

        if rev > self.repotiprev:
            text = self.revlog2.revision(node)
            self._cache = (node, rev, text)
        else:
            text = revlog.revlog.revision(self, rev)
            # already cached
        return text

    def addrevision(self, text, transaction, link, p1=None, p2=None, d=None):
        raise NotImplementedError
    def addgroup(self, revs, linkmapper, transaction):
        raise NotImplementedError
    def strip(self, rev, minlink):
        raise NotImplementedError
    def checksize(self):
        raise NotImplementedError

class unionchangelog(unionrevlog, changelog.changelog):
    def __init__(self, opener, opener2):
        changelog.changelog.__init__(self, opener)
        linkmapper = None
        changelog2 = changelog.changelog(opener2)
        unionrevlog.__init__(self, opener, self.indexfile, changelog2,
                             linkmapper)

class unionmanifest(unionrevlog, manifest.manifest):
    def __init__(self, opener, opener2, linkmapper):
        manifest.manifest.__init__(self, opener)
        manifest2 = manifest.manifest(opener2)
        unionrevlog.__init__(self, opener, self.indexfile, manifest2,
                             linkmapper)

class unionfilelog(unionrevlog, filelog.filelog):
    def __init__(self, opener, path, opener2, linkmapper, repo):
        filelog.filelog.__init__(self, opener, path)
        filelog2 = filelog.filelog(opener2, path)
        unionrevlog.__init__(self, opener, self.indexfile, filelog2,
                             linkmapper)
        self._repo = repo

    def _file(self, f):
        self._repo.file(f)

class unionpeer(localrepo.localpeer):
    def canpush(self):
        return False

class unionrepository(localrepo.localrepository):
    def __init__(self, ui, path, path2):
        localrepo.localrepository.__init__(self, ui, path)
        self.ui.setconfig('phases', 'publish', False)

        self._url = 'union:%s+%s' % (util.expandpath(path),
                                     util.expandpath(path2))
        self.repo2 = localrepo.localrepository(ui, path2)

    @localrepo.unfilteredpropertycache
    def changelog(self):
        return unionchangelog(self.sopener, self.repo2.sopener)

    def _clrev(self, rev2):
        """map from repo2 changelog rev to temporary rev in self.changelog"""
        node = self.repo2.changelog.node(rev2)
        return self.changelog.rev(node)

    @localrepo.unfilteredpropertycache
    def manifest(self):
        return unionmanifest(self.sopener, self.repo2.sopener,
                             self._clrev)

    def url(self):
        return self._url

    def file(self, f):
        return unionfilelog(self.sopener, f, self.repo2.sopener,
                            self._clrev, self)

    def close(self):
        self.repo2.close()

    def cancopy(self):
        return False

    def peer(self):
        return unionpeer(self)

    def getcwd(self):
        return os.getcwd() # always outside the repo

def instance(ui, path, create):
    if create:
        raise util.Abort(_('cannot create new union repository'))
    parentpath = ui.config("bundle", "mainreporoot", "")
    if not parentpath:
        # try to find the correct path to the working directory repo
        parentpath = cmdutil.findrepo(os.getcwd())
        if parentpath is None:
            parentpath = ''
    if parentpath:
        # Try to make the full path relative so we get a nice, short URL.
        # In particular, we don't want temp dir names in test outputs.
        cwd = os.getcwd()
        if parentpath == cwd:
            parentpath = ''
        else:
            cwd = os.path.join(cwd,'')
            if parentpath.startswith(cwd):
                parentpath = parentpath[len(cwd):]
    if path.startswith('union:'):
        s = path.split(":", 1)[1].split("+", 1)
        if len(s) == 1:
            repopath, repopath2 = parentpath, s[0]
        else:
            repopath, repopath2 = s
    else:
        repopath, repopath2 = parentpath, path
    return unionrepository(ui, repopath, repopath2)
