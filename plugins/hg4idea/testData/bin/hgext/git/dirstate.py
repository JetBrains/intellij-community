from __future__ import absolute_import

import contextlib
import errno
import os

from mercurial.node import sha1nodeconstants
from mercurial import (
    error,
    extensions,
    match as matchmod,
    pycompat,
    scmutil,
    util,
)
from mercurial.interfaces import (
    dirstate as intdirstate,
    util as interfaceutil,
)

from . import gitutil

pygit2 = gitutil.get_pygit2()


def readpatternfile(orig, filepath, warn, sourceinfo=False):
    if not (b'info/exclude' in filepath or filepath.endswith(b'.gitignore')):
        return orig(filepath, warn, sourceinfo=False)
    result = []
    warnings = []
    with open(filepath, b'rb') as fp:
        for l in fp:
            l = l.strip()
            if not l or l.startswith(b'#'):
                continue
            if l.startswith(b'!'):
                warnings.append(b'unsupported ignore pattern %s' % l)
                continue
            if l.startswith(b'/'):
                result.append(b'rootglob:' + l[1:])
            else:
                result.append(b'relglob:' + l)
    return result, warnings


extensions.wrapfunction(matchmod, b'readpatternfile', readpatternfile)


_STATUS_MAP = {}
if pygit2:
    _STATUS_MAP = {
        pygit2.GIT_STATUS_CONFLICTED: b'm',
        pygit2.GIT_STATUS_CURRENT: b'n',
        pygit2.GIT_STATUS_IGNORED: b'?',
        pygit2.GIT_STATUS_INDEX_DELETED: b'r',
        pygit2.GIT_STATUS_INDEX_MODIFIED: b'n',
        pygit2.GIT_STATUS_INDEX_NEW: b'a',
        pygit2.GIT_STATUS_INDEX_RENAMED: b'a',
        pygit2.GIT_STATUS_INDEX_TYPECHANGE: b'n',
        pygit2.GIT_STATUS_WT_DELETED: b'r',
        pygit2.GIT_STATUS_WT_MODIFIED: b'n',
        pygit2.GIT_STATUS_WT_NEW: b'?',
        pygit2.GIT_STATUS_WT_RENAMED: b'a',
        pygit2.GIT_STATUS_WT_TYPECHANGE: b'n',
        pygit2.GIT_STATUS_WT_UNREADABLE: b'?',
        pygit2.GIT_STATUS_INDEX_MODIFIED | pygit2.GIT_STATUS_WT_MODIFIED: b'm',
    }


@interfaceutil.implementer(intdirstate.idirstate)
class gitdirstate(object):
    def __init__(self, ui, root, gitrepo):
        self._ui = ui
        self._root = os.path.dirname(root)
        self.git = gitrepo
        self._plchangecallbacks = {}
        # TODO: context.poststatusfixup is bad and uses this attribute
        self._dirty = False

    def p1(self):
        try:
            return self.git.head.peel().id.raw
        except pygit2.GitError:
            # Typically happens when peeling HEAD fails, as in an
            # empty repository.
            return sha1nodeconstants.nullid

    def p2(self):
        # TODO: MERGE_HEAD? something like that, right?
        return sha1nodeconstants.nullid

    def setparents(self, p1, p2=None):
        if p2 is None:
            p2 = sha1nodeconstants.nullid
        assert p2 == sha1nodeconstants.nullid, b'TODO merging support'
        self.git.head.set_target(gitutil.togitnode(p1))

    @util.propertycache
    def identity(self):
        return util.filestat.frompath(
            os.path.join(self._root, b'.git', b'index')
        )

    def branch(self):
        return b'default'

    def parents(self):
        # TODO how on earth do we find p2 if a merge is in flight?
        return self.p1(), sha1nodeconstants.nullid

    def __iter__(self):
        return (pycompat.fsencode(f.path) for f in self.git.index)

    def items(self):
        for ie in self.git.index:
            yield ie.path, None  # value should be a DirstateItem

    # py2,3 compat forward
    iteritems = items

    def __getitem__(self, filename):
        try:
            gs = self.git.status_file(filename)
        except KeyError:
            return b'?'
        return _STATUS_MAP[gs]

    def __contains__(self, filename):
        try:
            gs = self.git.status_file(filename)
            return _STATUS_MAP[gs] != b'?'
        except KeyError:
            return False

    def status(self, match, subrepos, ignored, clean, unknown):
        listclean = clean
        # TODO handling of clean files - can we get that from git.status()?
        modified, added, removed, deleted, unknown, ignored, clean = (
            [],
            [],
            [],
            [],
            [],
            [],
            [],
        )
        gstatus = self.git.status()
        for path, status in gstatus.items():
            path = pycompat.fsencode(path)
            if not match(path):
                continue
            if status == pygit2.GIT_STATUS_IGNORED:
                if path.endswith(b'/'):
                    continue
                ignored.append(path)
            elif status in (
                pygit2.GIT_STATUS_WT_MODIFIED,
                pygit2.GIT_STATUS_INDEX_MODIFIED,
                pygit2.GIT_STATUS_WT_MODIFIED
                | pygit2.GIT_STATUS_INDEX_MODIFIED,
            ):
                modified.append(path)
            elif status == pygit2.GIT_STATUS_INDEX_NEW:
                added.append(path)
            elif status == pygit2.GIT_STATUS_WT_NEW:
                unknown.append(path)
            elif status == pygit2.GIT_STATUS_WT_DELETED:
                deleted.append(path)
            elif status == pygit2.GIT_STATUS_INDEX_DELETED:
                removed.append(path)
            else:
                raise error.Abort(
                    b'unhandled case: status for %r is %r' % (path, status)
                )

        if listclean:
            observed = set(
                modified + added + removed + deleted + unknown + ignored
            )
            index = self.git.index
            index.read()
            for entry in index:
                path = pycompat.fsencode(entry.path)
                if not match(path):
                    continue
                if path in observed:
                    continue  # already in some other set
                if path[-1] == b'/':
                    continue  # directory
                clean.append(path)

        # TODO are we really always sure of status here?
        return (
            False,
            scmutil.status(
                modified, added, removed, deleted, unknown, ignored, clean
            ),
        )

    def flagfunc(self, buildfallback):
        # TODO we can do better
        return buildfallback()

    def getcwd(self):
        # TODO is this a good way to do this?
        return os.path.dirname(
            os.path.dirname(pycompat.fsencode(self.git.path))
        )

    def normalize(self, path):
        normed = util.normcase(path)
        assert normed == path, b"TODO handling of case folding: %s != %s" % (
            normed,
            path,
        )
        return path

    @property
    def _checklink(self):
        return util.checklink(os.path.dirname(pycompat.fsencode(self.git.path)))

    def copies(self):
        # TODO support copies?
        return {}

    # # TODO what the heck is this
    _filecache = set()

    def pendingparentchange(self):
        # TODO: we need to implement the context manager bits and
        # correctly stage/revert index edits.
        return False

    def write(self, tr):
        # TODO: call parent change callbacks

        if tr:

            def writeinner(category):
                self.git.index.write()

            tr.addpending(b'gitdirstate', writeinner)
        else:
            self.git.index.write()

    def pathto(self, f, cwd=None):
        if cwd is None:
            cwd = self.getcwd()
        # TODO core dirstate does something about slashes here
        assert isinstance(f, bytes)
        r = util.pathto(self._root, cwd, f)
        return r

    def matches(self, match):
        for x in self.git.index:
            p = pycompat.fsencode(x.path)
            if match(p):
                yield p

    def set_clean(self, f, parentfiledata=None):
        """Mark a file normal and clean."""
        # TODO: for now we just let libgit2 re-stat the file. We can
        # clearly do better.

    def set_possibly_dirty(self, f):
        """Mark a file normal, but possibly dirty."""
        # TODO: for now we just let libgit2 re-stat the file. We can
        # clearly do better.

    def walk(self, match, subrepos, unknown, ignored, full=True):
        # TODO: we need to use .status() and not iterate the index,
        # because the index doesn't force a re-walk and so `hg add` of
        # a new file without an intervening call to status will
        # silently do nothing.
        r = {}
        cwd = self.getcwd()
        for path, status in self.git.status().items():
            if path.startswith('.hg/'):
                continue
            path = pycompat.fsencode(path)
            if not match(path):
                continue
            # TODO construct the stat info from the status object?
            try:
                s = os.stat(os.path.join(cwd, path))
            except OSError as e:
                if e.errno != errno.ENOENT:
                    raise
                continue
            r[path] = s
        return r

    def savebackup(self, tr, backupname):
        # TODO: figure out a strategy for saving index backups.
        pass

    def restorebackup(self, tr, backupname):
        # TODO: figure out a strategy for saving index backups.
        pass

    def set_tracked(self, f):
        uf = pycompat.fsdecode(f)
        if uf in self.git.index:
            return False
        index = self.git.index
        index.read()
        index.add(uf)
        index.write()
        return True

    def add(self, f):
        index = self.git.index
        index.read()
        index.add(pycompat.fsdecode(f))
        index.write()

    def drop(self, f):
        index = self.git.index
        index.read()
        fs = pycompat.fsdecode(f)
        if fs in index:
            index.remove(fs)
            index.write()

    def set_untracked(self, f):
        index = self.git.index
        index.read()
        fs = pycompat.fsdecode(f)
        if fs in index:
            index.remove(fs)
            index.write()
            return True
        return False

    def remove(self, f):
        index = self.git.index
        index.read()
        index.remove(pycompat.fsdecode(f))
        index.write()

    def copied(self, path):
        # TODO: track copies?
        return None

    def prefetch_parents(self):
        # TODO
        pass

    def update_file(self, *args, **kwargs):
        # TODO
        pass

    @contextlib.contextmanager
    def parentchange(self):
        # TODO: track this maybe?
        yield

    def addparentchangecallback(self, category, callback):
        # TODO: should this be added to the dirstate interface?
        self._plchangecallbacks[category] = callback

    def clearbackup(self, tr, backupname):
        # TODO
        pass

    def setbranch(self, branch):
        raise error.Abort(
            b'git repos do not support branches. try using bookmarks'
        )
