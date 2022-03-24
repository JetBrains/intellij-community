# shallowrepo.py - shallow repository that uses remote filelogs
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from __future__ import absolute_import

import os

from mercurial.i18n import _
from mercurial.node import hex, nullrev
from mercurial import (
    encoding,
    error,
    localrepo,
    match,
    pycompat,
    scmutil,
    sparse,
    util,
)
from mercurial.utils import procutil
from . import (
    connectionpool,
    constants,
    contentstore,
    datapack,
    fileserverclient,
    historypack,
    metadatastore,
    remotefilectx,
    remotefilelog,
    shallowutil,
)

# These make*stores functions are global so that other extensions can replace
# them.
def makelocalstores(repo):
    """In-repo stores, like .hg/store/data; can not be discarded."""
    localpath = os.path.join(repo.svfs.vfs.base, b'data')
    if not os.path.exists(localpath):
        os.makedirs(localpath)

    # Instantiate local data stores
    localcontent = contentstore.remotefilelogcontentstore(
        repo, localpath, repo.name, shared=False
    )
    localmetadata = metadatastore.remotefilelogmetadatastore(
        repo, localpath, repo.name, shared=False
    )
    return localcontent, localmetadata


def makecachestores(repo):
    """Typically machine-wide, cache of remote data; can be discarded."""
    # Instantiate shared cache stores
    cachepath = shallowutil.getcachepath(repo.ui)
    cachecontent = contentstore.remotefilelogcontentstore(
        repo, cachepath, repo.name, shared=True
    )
    cachemetadata = metadatastore.remotefilelogmetadatastore(
        repo, cachepath, repo.name, shared=True
    )

    repo.sharedstore = cachecontent
    repo.shareddatastores.append(cachecontent)
    repo.sharedhistorystores.append(cachemetadata)

    return cachecontent, cachemetadata


def makeremotestores(repo, cachecontent, cachemetadata):
    """These stores fetch data from a remote server."""
    # Instantiate remote stores
    repo.fileservice = fileserverclient.fileserverclient(repo)
    remotecontent = contentstore.remotecontentstore(
        repo.ui, repo.fileservice, cachecontent
    )
    remotemetadata = metadatastore.remotemetadatastore(
        repo.ui, repo.fileservice, cachemetadata
    )
    return remotecontent, remotemetadata


def makepackstores(repo):
    """Packs are more efficient (to read from) cache stores."""
    # Instantiate pack stores
    packpath = shallowutil.getcachepackpath(repo, constants.FILEPACK_CATEGORY)
    packcontentstore = datapack.datapackstore(repo.ui, packpath)
    packmetadatastore = historypack.historypackstore(repo.ui, packpath)

    repo.shareddatastores.append(packcontentstore)
    repo.sharedhistorystores.append(packmetadatastore)
    shallowutil.reportpackmetrics(
        repo.ui, b'filestore', packcontentstore, packmetadatastore
    )
    return packcontentstore, packmetadatastore


def makeunionstores(repo):
    """Union stores iterate the other stores and return the first result."""
    repo.shareddatastores = []
    repo.sharedhistorystores = []

    packcontentstore, packmetadatastore = makepackstores(repo)
    cachecontent, cachemetadata = makecachestores(repo)
    localcontent, localmetadata = makelocalstores(repo)
    remotecontent, remotemetadata = makeremotestores(
        repo, cachecontent, cachemetadata
    )

    # Instantiate union stores
    repo.contentstore = contentstore.unioncontentstore(
        packcontentstore,
        cachecontent,
        localcontent,
        remotecontent,
        writestore=localcontent,
    )
    repo.metadatastore = metadatastore.unionmetadatastore(
        packmetadatastore,
        cachemetadata,
        localmetadata,
        remotemetadata,
        writestore=localmetadata,
    )

    fileservicedatawrite = cachecontent
    fileservicehistorywrite = cachemetadata
    repo.fileservice.setstore(
        repo.contentstore,
        repo.metadatastore,
        fileservicedatawrite,
        fileservicehistorywrite,
    )
    shallowutil.reportpackmetrics(
        repo.ui, b'filestore', packcontentstore, packmetadatastore
    )


def wraprepo(repo):
    class shallowrepository(repo.__class__):
        @util.propertycache
        def name(self):
            return self.ui.config(b'remotefilelog', b'reponame')

        @util.propertycache
        def fallbackpath(self):
            path = repo.ui.config(
                b"remotefilelog",
                b"fallbackpath",
                repo.ui.config(b'paths', b'default'),
            )
            if not path:
                raise error.Abort(
                    b"no remotefilelog server "
                    b"configured - is your .hg/hgrc trusted?"
                )

            return path

        def maybesparsematch(self, *revs, **kwargs):
            """
            A wrapper that allows the remotefilelog to invoke sparsematch() if
            this is a sparse repository, or returns None if this is not a
            sparse repository.
            """
            if revs:
                ret = sparse.matcher(repo, revs=revs)
            else:
                ret = sparse.matcher(repo)

            if ret.always():
                return None
            return ret

        def file(self, f):
            if f[0] == b'/':
                f = f[1:]

            if self.shallowmatch(f):
                return remotefilelog.remotefilelog(self.svfs, f, self)
            else:
                return super(shallowrepository, self).file(f)

        def filectx(self, path, *args, **kwargs):
            if self.shallowmatch(path):
                return remotefilectx.remotefilectx(self, path, *args, **kwargs)
            else:
                return super(shallowrepository, self).filectx(
                    path, *args, **kwargs
                )

        @localrepo.unfilteredmethod
        def commitctx(self, ctx, error=False, origctx=None):
            """Add a new revision to current repository.
            Revision information is passed via the context argument.
            """

            # some contexts already have manifest nodes, they don't need any
            # prefetching (for example if we're just editing a commit message
            # we can reuse manifest
            if not ctx.manifestnode():
                # prefetch files that will likely be compared
                m1 = ctx.p1().manifest()
                files = []
                for f in ctx.modified() + ctx.added():
                    fparent1 = m1.get(f, self.nullid)
                    if fparent1 != self.nullid:
                        files.append((f, hex(fparent1)))
                self.fileservice.prefetch(files)
            return super(shallowrepository, self).commitctx(
                ctx, error=error, origctx=origctx
            )

        def backgroundprefetch(
            self, revs, base=None, repack=False, pats=None, opts=None
        ):
            """Runs prefetch in background with optional repack"""
            cmd = [procutil.hgexecutable(), b'-R', repo.origroot, b'prefetch']
            if repack:
                cmd.append(b'--repack')
            if revs:
                cmd += [b'-r', revs]
            # We know this command will find a binary, so don't block
            # on it starting.
            kwargs = {}
            if repo.ui.configbool(b'devel', b'remotefilelog.bg-wait'):
                kwargs['record_wait'] = repo.ui.atexit

            procutil.runbgcommand(
                cmd, encoding.environ, ensurestart=False, **kwargs
            )

        def prefetch(self, revs, base=None, pats=None, opts=None):
            """Prefetches all the necessary file revisions for the given revs
            Optionally runs repack in background
            """
            with repo._lock(
                repo.svfs,
                b'prefetchlock',
                True,
                None,
                None,
                _(b'prefetching in %s') % repo.origroot,
            ):
                self._prefetch(revs, base, pats, opts)

        def _prefetch(self, revs, base=None, pats=None, opts=None):
            fallbackpath = self.fallbackpath
            if fallbackpath:
                # If we know a rev is on the server, we should fetch the server
                # version of those files, since our local file versions might
                # become obsolete if the local commits are stripped.
                localrevs = repo.revs(b'outgoing(%s)', fallbackpath)
                if base is not None and base != nullrev:
                    serverbase = list(
                        repo.revs(
                            b'first(reverse(::%s) - %ld)', base, localrevs
                        )
                    )
                    if serverbase:
                        base = serverbase[0]
            else:
                localrevs = repo

            mfl = repo.manifestlog
            mfrevlog = mfl.getstorage(b'')
            if base is not None:
                mfdict = mfl[repo[base].manifestnode()].read()
                skip = set(pycompat.iteritems(mfdict))
            else:
                skip = set()

            # Copy the skip set to start large and avoid constant resizing,
            # and since it's likely to be very similar to the prefetch set.
            files = skip.copy()
            serverfiles = skip.copy()
            visited = set()
            visited.add(nullrev)
            revcount = len(revs)
            progress = self.ui.makeprogress(_(b'prefetching'), total=revcount)
            progress.update(0)
            for rev in sorted(revs):
                ctx = repo[rev]
                if pats:
                    m = scmutil.match(ctx, pats, opts)
                sparsematch = repo.maybesparsematch(rev)

                mfnode = ctx.manifestnode()
                mfrev = mfrevlog.rev(mfnode)

                # Decompressing manifests is expensive.
                # When possible, only read the deltas.
                p1, p2 = mfrevlog.parentrevs(mfrev)
                if p1 in visited and p2 in visited:
                    mfdict = mfl[mfnode].readfast()
                else:
                    mfdict = mfl[mfnode].read()

                diff = pycompat.iteritems(mfdict)
                if pats:
                    diff = (pf for pf in diff if m(pf[0]))
                if sparsematch:
                    diff = (pf for pf in diff if sparsematch(pf[0]))
                if rev not in localrevs:
                    serverfiles.update(diff)
                else:
                    files.update(diff)

                visited.add(mfrev)
                progress.increment()

            files.difference_update(skip)
            serverfiles.difference_update(skip)
            progress.complete()

            # Fetch files known to be on the server
            if serverfiles:
                results = [(path, hex(fnode)) for (path, fnode) in serverfiles]
                repo.fileservice.prefetch(results, force=True)

            # Fetch files that may or may not be on the server
            if files:
                results = [(path, hex(fnode)) for (path, fnode) in files]
                repo.fileservice.prefetch(results)

        def close(self):
            super(shallowrepository, self).close()
            self.connectionpool.close()

    repo.__class__ = shallowrepository

    repo.shallowmatch = match.always()

    makeunionstores(repo)

    repo.includepattern = repo.ui.configlist(
        b"remotefilelog", b"includepattern", None
    )
    repo.excludepattern = repo.ui.configlist(
        b"remotefilelog", b"excludepattern", None
    )
    if not util.safehasattr(repo, 'connectionpool'):
        repo.connectionpool = connectionpool.connectionpool(repo)

    if repo.includepattern or repo.excludepattern:
        repo.shallowmatch = match.match(
            repo.root, b'', None, repo.includepattern, repo.excludepattern
        )
