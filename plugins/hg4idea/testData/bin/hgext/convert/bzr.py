# bzr.py - bzr support for the convert extension
#
#  Copyright 2008, 2009 Marek Kubica <marek@xivilization.net> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# This module is for handling 'bzr', that was formerly known as Bazaar-NG;
# it cannot access 'bar' repositories, but they were never used very much

import os
from mercurial import demandimport
# these do not work with demandimport, blacklist
demandimport.ignore.extend([
        'bzrlib.transactions',
        'bzrlib.urlutils',
        'ElementPath',
    ])

from mercurial.i18n import _
from mercurial import util
from common import NoRepo, commit, converter_source

try:
    # bazaar imports
    from bzrlib import bzrdir, revision, errors
    from bzrlib.revisionspec import RevisionSpec
except ImportError:
    pass

supportedkinds = ('file', 'symlink')

class bzr_source(converter_source):
    """Reads Bazaar repositories by using the Bazaar Python libraries"""

    def __init__(self, ui, path, rev=None):
        super(bzr_source, self).__init__(ui, path, rev=rev)

        if not os.path.exists(os.path.join(path, '.bzr')):
            raise NoRepo(_('%s does not look like a Bazaar repository')
                         % path)

        try:
            # access bzrlib stuff
            bzrdir
        except NameError:
            raise NoRepo(_('Bazaar modules could not be loaded'))

        path = os.path.abspath(path)
        self._checkrepotype(path)
        try:
            self.sourcerepo = bzrdir.BzrDir.open(path).open_repository()
        except errors.NoRepositoryPresent:
            raise NoRepo(_('%s does not look like a Bazaar repository')
                         % path)
        self._parentids = {}

    def _checkrepotype(self, path):
        # Lightweight checkouts detection is informational but probably
        # fragile at API level. It should not terminate the conversion.
        try:
            from bzrlib import bzrdir
            dir = bzrdir.BzrDir.open_containing(path)[0]
            try:
                tree = dir.open_workingtree(recommend_upgrade=False)
                branch = tree.branch
            except (errors.NoWorkingTree, errors.NotLocalUrl):
                tree = None
                branch = dir.open_branch()
            if (tree is not None and tree.bzrdir.root_transport.base !=
                branch.bzrdir.root_transport.base):
                self.ui.warn(_('warning: lightweight checkouts may cause '
                               'conversion failures, try with a regular '
                               'branch instead.\n'))
        except Exception:
            self.ui.note(_('bzr source type could not be determined\n'))

    def before(self):
        """Before the conversion begins, acquire a read lock
        for all the operations that might need it. Fortunately
        read locks don't block other reads or writes to the
        repository, so this shouldn't have any impact on the usage of
        the source repository.

        The alternative would be locking on every operation that
        needs locks (there are currently two: getting the file and
        getting the parent map) and releasing immediately after,
        but this approach can take even 40% longer."""
        self.sourcerepo.lock_read()

    def after(self):
        self.sourcerepo.unlock()

    def _bzrbranches(self):
        return self.sourcerepo.find_branches(using=True)

    def getheads(self):
        if not self.rev:
            # Set using=True to avoid nested repositories (see issue3254)
            heads = sorted([b.last_revision() for b in self._bzrbranches()])
        else:
            revid = None
            for branch in self._bzrbranches():
                try:
                    r = RevisionSpec.from_string(self.rev)
                    info = r.in_history(branch)
                except errors.BzrError:
                    pass
                revid = info.rev_id
            if revid is None:
                raise util.Abort(_('%s is not a valid revision') % self.rev)
            heads = [revid]
        # Empty repositories return 'null:', which cannot be retrieved
        heads = [h for h in heads if h != 'null:']
        return heads

    def getfile(self, name, rev):
        revtree = self.sourcerepo.revision_tree(rev)
        fileid = revtree.path2id(name.decode(self.encoding or 'utf-8'))
        kind = None
        if fileid is not None:
            kind = revtree.kind(fileid)
        if kind not in supportedkinds:
            # the file is not available anymore - was deleted
            raise IOError(_('%s is not available in %s anymore') %
                    (name, rev))
        mode = self._modecache[(name, rev)]
        if kind == 'symlink':
            target = revtree.get_symlink_target(fileid)
            if target is None:
                raise util.Abort(_('%s.%s symlink has no target')
                                 % (name, rev))
            return target, mode
        else:
            sio = revtree.get_file(fileid)
            return sio.read(), mode

    def getchanges(self, version):
        # set up caches: modecache and revtree
        self._modecache = {}
        self._revtree = self.sourcerepo.revision_tree(version)
        # get the parentids from the cache
        parentids = self._parentids.pop(version)
        # only diff against first parent id
        prevtree = self.sourcerepo.revision_tree(parentids[0])
        return self._gettreechanges(self._revtree, prevtree)

    def getcommit(self, version):
        rev = self.sourcerepo.get_revision(version)
        # populate parent id cache
        if not rev.parent_ids:
            parents = []
            self._parentids[version] = (revision.NULL_REVISION,)
        else:
            parents = self._filterghosts(rev.parent_ids)
            self._parentids[version] = parents

        branch = self.recode(rev.properties.get('branch-nick', u'default'))
        if branch == 'trunk':
            branch = 'default'
        return commit(parents=parents,
                date='%d %d' % (rev.timestamp, -rev.timezone),
                author=self.recode(rev.committer),
                desc=self.recode(rev.message),
                branch=branch,
                rev=version)

    def gettags(self):
        bytetags = {}
        for branch in self._bzrbranches():
            if not branch.supports_tags():
                return {}
            tagdict = branch.tags.get_tag_dict()
            for name, rev in tagdict.iteritems():
                bytetags[self.recode(name)] = rev
        return bytetags

    def getchangedfiles(self, rev, i):
        self._modecache = {}
        curtree = self.sourcerepo.revision_tree(rev)
        if i is not None:
            parentid = self._parentids[rev][i]
        else:
            # no parent id, get the empty revision
            parentid = revision.NULL_REVISION

        prevtree = self.sourcerepo.revision_tree(parentid)
        changes = [e[0] for e in self._gettreechanges(curtree, prevtree)[0]]
        return changes

    def _gettreechanges(self, current, origin):
        revid = current._revision_id
        changes = []
        renames = {}
        seen = set()
        # Process the entries by reverse lexicographic name order to
        # handle nested renames correctly, most specific first.
        curchanges = sorted(current.iter_changes(origin),
                            key=lambda c: c[1][0] or c[1][1],
                            reverse=True)
        for (fileid, paths, changed_content, versioned, parent, name,
            kind, executable) in curchanges:

            if paths[0] == u'' or paths[1] == u'':
                # ignore changes to tree root
                continue

            # bazaar tracks directories, mercurial does not, so
            # we have to rename the directory contents
            if kind[1] == 'directory':
                if kind[0] not in (None, 'directory'):
                    # Replacing 'something' with a directory, record it
                    # so it can be removed.
                    changes.append((self.recode(paths[0]), revid))

                if kind[0] == 'directory' and None not in paths:
                    renaming = paths[0] != paths[1]
                    # neither an add nor an delete - a move
                    # rename all directory contents manually
                    subdir = origin.inventory.path2id(paths[0])
                    # get all child-entries of the directory
                    for name, entry in origin.inventory.iter_entries(subdir):
                        # hg does not track directory renames
                        if entry.kind == 'directory':
                            continue
                        frompath = self.recode(paths[0] + '/' + name)
                        if frompath in seen:
                            # Already handled by a more specific change entry
                            # This is important when you have:
                            # a => b
                            # a/c => a/c
                            # Here a/c must not be renamed into b/c
                            continue
                        seen.add(frompath)
                        if not renaming:
                            continue
                        topath = self.recode(paths[1] + '/' + name)
                        # register the files as changed
                        changes.append((frompath, revid))
                        changes.append((topath, revid))
                        # add to mode cache
                        mode = ((entry.executable and 'x')
                                or (entry.kind == 'symlink' and 's')
                                or '')
                        self._modecache[(topath, revid)] = mode
                        # register the change as move
                        renames[topath] = frompath

                # no further changes, go to the next change
                continue

            # we got unicode paths, need to convert them
            path, topath = paths
            if path is not None:
                path = self.recode(path)
            if topath is not None:
                topath = self.recode(topath)
            seen.add(path or topath)

            if topath is None:
                # file deleted
                changes.append((path, revid))
                continue

            # renamed
            if path and path != topath:
                renames[topath] = path
                changes.append((path, revid))

            # populate the mode cache
            kind, executable = [e[1] for e in (kind, executable)]
            mode = ((executable and 'x') or (kind == 'symlink' and 'l')
                    or '')
            self._modecache[(topath, revid)] = mode
            changes.append((topath, revid))

        return changes, renames

    def _filterghosts(self, ids):
        """Filters out ghost revisions which hg does not support, see
        <http://bazaar-vcs.org/GhostRevision>
        """
        parentmap = self.sourcerepo.get_parent_map(ids)
        parents = tuple([parent for parent in ids if parent in parentmap])
        return parents
