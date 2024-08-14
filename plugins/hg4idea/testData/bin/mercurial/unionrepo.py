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

import contextlib


from .i18n import _

from . import (
    changelog,
    cmdutil,
    encoding,
    error,
    filelog,
    localrepo,
    manifest,
    mdiff,
    pathutil,
    revlog,
    util,
    vfs as vfsmod,
)

from .revlogutils import (
    constants as revlog_constants,
)


class unionrevlog(revlog.revlog):
    def __init__(self, opener, radix, revlog2, linkmapper):
        # How it works:
        # To retrieve a revision, we just need to know the node id so we can
        # look it up in revlog2.
        #
        # To differentiate a rev in the second revlog from a rev in the revlog,
        # we check revision against repotiprev.
        opener = vfsmod.readonlyvfs(opener)
        target = getattr(revlog2, 'target', None)
        if target is None:
            # a revlog wrapper, eg: the manifestlog that is not an actual revlog
            target = revlog2._revlog.target
        revlog.revlog.__init__(self, opener, target=target, radix=radix)
        self.revlog2 = revlog2

        n = len(self)
        self.repotiprev = n - 1
        self.bundlerevs = set()  # used by 'bundle()' revset expression
        for rev2 in self.revlog2:
            rev = self.revlog2.index[rev2]
            # rev numbers - in revlog2, very different from self.rev
            (
                _start,
                _csize,
                rsize,
                base,
                linkrev,
                p1rev,
                p2rev,
                node,
                _sdo,
                _sds,
                _dcm,
                _sdcm,
                rank,
            ) = rev
            flags = _start & 0xFFFF

            if linkmapper is None:  # link is to same revlog
                assert linkrev == rev2  # we never link back
                link = n
            else:  # rev must be mapped from repo2 cl to unified cl by linkmapper
                link = linkmapper(linkrev)

            if linkmapper is not None:  # link is to same revlog
                base = linkmapper(base)

            this_rev = self.index.get_rev(node)
            if this_rev is not None:
                # this happens for the common revlog revisions
                self.bundlerevs.add(this_rev)
                continue

            p1node = self.revlog2.node(p1rev)
            p2node = self.revlog2.node(p2rev)

            # TODO: it's probably wrong to set compressed length to -1, but
            # I have no idea if csize is valid in the base revlog context.
            e = (
                flags,
                -1,
                rsize,
                base,
                link,
                self.rev(p1node),
                self.rev(p2node),
                node,
                0,  # sidedata offset
                0,  # sidedata size
                revlog_constants.COMP_MODE_INLINE,
                revlog_constants.COMP_MODE_INLINE,
                rank,
            )
            self.index.append(e)
            self.bundlerevs.add(n)
            n += 1

    @contextlib.contextmanager
    def reading(self):
        if 0 <= len(self.bundlerevs) < len(self.index):
            read_1 = super().reading
        else:
            read_1 = util.nullcontextmanager
        if 0 < len(self.bundlerevs):
            read_2 = self.revlog2.reading
        else:
            read_2 = util.nullcontextmanager
        with read_1(), read_2():
            yield

    def _chunk(self, rev):
        if rev <= self.repotiprev:
            return revlog.revlog._chunk(self, rev)
        return self.revlog2._chunk(self.node(rev))

    def revdiff(self, rev1, rev2):
        """return or calculate a delta between two revisions"""
        if rev1 > self.repotiprev and rev2 > self.repotiprev:
            return self.revlog2.revdiff(
                self.revlog2.rev(self.node(rev1)),
                self.revlog2.rev(self.node(rev2)),
            )
        elif rev1 <= self.repotiprev and rev2 <= self.repotiprev:
            return super(unionrevlog, self).revdiff(rev1, rev2)

        return mdiff.textdiff(self.rawdata(rev1), self.rawdata(rev2))

    def _revisiondata(self, nodeorrev, raw=False):
        if isinstance(nodeorrev, int):
            rev = nodeorrev
            node = self.node(rev)
        else:
            node = nodeorrev
            rev = self.rev(node)

        if rev > self.repotiprev:
            # work around manifestrevlog NOT being a revlog
            revlog2 = getattr(self.revlog2, '_revlog', self.revlog2)
            func = revlog2._revisiondata
        else:
            func = super(unionrevlog, self)._revisiondata
        return func(node, raw=raw)

    def addrevision(
        self,
        text,
        transaction,
        link,
        p1,
        p2,
        cachedelta=None,
        node=None,
        flags=revlog.REVIDX_DEFAULT_FLAGS,
        deltacomputer=None,
        sidedata=None,
    ):
        raise NotImplementedError

    def addgroup(
        self,
        deltas,
        linkmapper,
        transaction,
        alwayscache=False,
        addrevisioncb=None,
        duplicaterevisioncb=None,
        debug_info=None,
        delta_base_reuse_policy=None,
    ):
        raise NotImplementedError

    def strip(self, minlink, transaction):
        raise NotImplementedError

    def checksize(self):
        raise NotImplementedError


class unionchangelog(unionrevlog, changelog.changelog):
    def __init__(self, opener, opener2):
        changelog.changelog.__init__(self, opener)
        linkmapper = None
        changelog2 = changelog.changelog(opener2)
        unionrevlog.__init__(self, opener, self.radix, changelog2, linkmapper)


class unionmanifest(unionrevlog, manifest.manifestrevlog):
    def __init__(self, nodeconstants, opener, opener2, linkmapper):
        # XXX manifestrevlog is not actually a revlog , so mixing it with
        # bundlerevlog is not a good idea.
        manifest.manifestrevlog.__init__(self, nodeconstants, opener)
        manifest2 = manifest.manifestrevlog(nodeconstants, opener2)
        unionrevlog.__init__(
            self, opener, self._revlog.radix, manifest2, linkmapper
        )


class unionfilelog(filelog.filelog):
    def __init__(self, opener, path, opener2, linkmapper, repo):
        filelog.filelog.__init__(self, opener, path)
        filelog2 = filelog.filelog(opener2, path)
        self._revlog = unionrevlog(
            opener, self._revlog.radix, filelog2._revlog, linkmapper
        )
        self._repo = repo
        self.repotiprev = self._revlog.repotiprev
        self.revlog2 = self._revlog.revlog2

    def iscensored(self, rev):
        """Check if a revision is censored."""
        if rev <= self.repotiprev:
            return filelog.filelog.iscensored(self, rev)
        node = self.node(rev)
        return self.revlog2.iscensored(self.revlog2.rev(node))


class unionpeer(localrepo.localpeer):
    def canpush(self):
        return False


class unionrepository:
    """Represents the union of data in 2 repositories.

    Instances are not usable if constructed directly. Use ``instance()``
    or ``makeunionrepository()`` to create a usable instance.
    """

    def __init__(self, repo2, url):
        self.repo2 = repo2
        self._url = url

        self.ui.setconfig(b'phases', b'publish', False, b'unionrepo')

    @localrepo.unfilteredpropertycache
    def changelog(self):
        return unionchangelog(self.svfs, self.repo2.svfs)

    @localrepo.unfilteredpropertycache
    def manifestlog(self):
        rootstore = unionmanifest(
            self.nodeconstants,
            self.svfs,
            self.repo2.svfs,
            self.unfiltered()._clrev,
        )
        return manifest.manifestlog(
            self.svfs, self, rootstore, self.narrowmatch()
        )

    def _clrev(self, rev2):
        """map from repo2 changelog rev to temporary rev in self.changelog"""
        node = self.repo2.changelog.node(rev2)
        return self.changelog.rev(node)

    def url(self):
        return self._url

    def file(self, f):
        return unionfilelog(
            self.svfs, f, self.repo2.svfs, self.unfiltered()._clrev, self
        )

    def close(self):
        self.repo2.close()

    def cancopy(self):
        return False

    def peer(self, path=None, remotehidden=False):
        return unionpeer(self, path=None, remotehidden=remotehidden)

    def getcwd(self):
        return encoding.getcwd()  # always outside the repo


def instance(ui, path, create, intents=None, createopts=None):
    if create:
        raise error.Abort(_(b'cannot create new union repository'))
    parentpath = ui.config(b"bundle", b"mainreporoot")
    if not parentpath:
        # try to find the correct path to the working directory repo
        parentpath = cmdutil.findrepo(encoding.getcwd())
        if parentpath is None:
            parentpath = b''
    if parentpath:
        # Try to make the full path relative so we get a nice, short URL.
        # In particular, we don't want temp dir names in test outputs.
        cwd = encoding.getcwd()
        if parentpath == cwd:
            parentpath = b''
        else:
            cwd = pathutil.normasprefix(cwd)
            if parentpath.startswith(cwd):
                parentpath = parentpath[len(cwd) :]
    if path.startswith(b'union:'):
        s = path.split(b":", 1)[1].split(b"+", 1)
        if len(s) == 1:
            repopath, repopath2 = parentpath, s[0]
        else:
            repopath, repopath2 = s
    else:
        repopath, repopath2 = parentpath, path

    return makeunionrepository(ui, repopath, repopath2)


def makeunionrepository(ui, repopath1, repopath2):
    """Make a union repository object from 2 local repo paths."""
    repo1 = localrepo.instance(ui, repopath1, create=False)
    repo2 = localrepo.instance(ui, repopath2, create=False)

    url = b'union:%s+%s' % (
        util.expandpath(repopath1),
        util.expandpath(repopath2),
    )

    class derivedunionrepository(unionrepository, repo1.__class__):
        pass

    repo = repo1
    repo.__class__ = derivedunionrepository
    unionrepository.__init__(repo1, repo2, url)

    return repo
