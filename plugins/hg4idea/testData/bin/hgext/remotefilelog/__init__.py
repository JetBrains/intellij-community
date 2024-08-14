# __init__.py - remotefilelog extension
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""remotefilelog causes Mercurial to lazilly fetch file contents (EXPERIMENTAL)

This extension is HIGHLY EXPERIMENTAL. There are NO BACKWARDS COMPATIBILITY
GUARANTEES. This means that repositories created with this extension may
only be usable with the exact version of this extension/Mercurial that was
used. The extension attempts to enforce this in order to prevent repository
corruption.

remotefilelog works by fetching file contents lazily and storing them
in a cache on the client rather than in revlogs. This allows enormous
histories to be transferred only partially, making them easier to
operate on.

Configs:

    ``packs.maxchainlen`` specifies the maximum delta chain length in pack files

    ``packs.maxpacksize`` specifies the maximum pack file size

    ``packs.maxpackfilecount`` specifies the maximum number of packs in the
      shared cache (trees only for now)

    ``remotefilelog.backgroundprefetch`` runs prefetch in background when True

    ``remotefilelog.bgprefetchrevs`` specifies revisions to fetch on commit and
      update, and on other commands that use them. Different from pullprefetch.

    ``remotefilelog.gcrepack`` does garbage collection during repack when True

    ``remotefilelog.nodettl`` specifies maximum TTL of a node in seconds before
      it is garbage collected

    ``remotefilelog.repackonhggc`` runs repack on hg gc when True

    ``remotefilelog.prefetchdays`` specifies the maximum age of a commit in
      days after which it is no longer prefetched.

    ``remotefilelog.prefetchdelay`` specifies delay between background
      prefetches in seconds after operations that change the working copy parent

    ``remotefilelog.data.gencountlimit`` constraints the minimum number of data
      pack files required to be considered part of a generation. In particular,
      minimum number of packs files > gencountlimit.

    ``remotefilelog.data.generations`` list for specifying the lower bound of
      each generation of the data pack files. For example, list ['100MB','1MB']
      or ['1MB', '100MB'] will lead to three generations: [0, 1MB), [
      1MB, 100MB) and [100MB, infinity).

    ``remotefilelog.data.maxrepackpacks`` the maximum number of pack files to
      include in an incremental data repack.

    ``remotefilelog.data.repackmaxpacksize`` the maximum size of a pack file for
      it to be considered for an incremental data repack.

    ``remotefilelog.data.repacksizelimit`` the maximum total size of pack files
      to include in an incremental data repack.

    ``remotefilelog.history.gencountlimit`` constraints the minimum number of
      history pack files required to be considered part of a generation. In
      particular, minimum number of packs files > gencountlimit.

    ``remotefilelog.history.generations`` list for specifying the lower bound of
      each generation of the history pack files. For example, list [
      '100MB', '1MB'] or ['1MB', '100MB'] will lead to three generations: [
      0, 1MB), [1MB, 100MB) and [100MB, infinity).

    ``remotefilelog.history.maxrepackpacks`` the maximum number of pack files to
      include in an incremental history repack.

    ``remotefilelog.history.repackmaxpacksize`` the maximum size of a pack file
      for it to be considered for an incremental history repack.

    ``remotefilelog.history.repacksizelimit`` the maximum total size of pack
      files to include in an incremental history repack.

    ``remotefilelog.backgroundrepack`` automatically consolidate packs in the
      background

    ``remotefilelog.cachepath`` path to cache

    ``remotefilelog.cachegroup`` if set, make cache directory sgid to this
      group

    ``remotefilelog.cacheprocess`` binary to invoke for fetching file data

    ``remotefilelog.debug`` turn on remotefilelog-specific debug output

    ``remotefilelog.excludepattern`` pattern of files to exclude from pulls

    ``remotefilelog.includepattern`` pattern of files to include in pulls

    ``remotefilelog.fetchwarning``: message to print when too many
      single-file fetches occur

    ``remotefilelog.getfilesstep`` number of files to request in a single RPC

    ``remotefilelog.getfilestype`` if set to 'threaded' use threads to fetch
      files, otherwise use optimistic fetching

    ``remotefilelog.pullprefetch`` revset for selecting files that should be
      eagerly downloaded rather than lazily

    ``remotefilelog.reponame`` name of the repo. If set, used to partition
      data from other repos in a shared store.

    ``remotefilelog.server`` if true, enable server-side functionality

    ``remotefilelog.servercachepath`` path for caching blobs on the server

    ``remotefilelog.serverexpiration`` number of days to keep cached server
      blobs

    ``remotefilelog.validatecache`` if set, check cache entries for corruption
      before returning blobs

    ``remotefilelog.validatecachelog`` if set, check cache entries for
      corruption before returning metadata

"""

import os
import time
import traceback

from mercurial.node import (
    hex,
    wdirrev,
)
from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial import (
    changegroup,
    changelog,
    commands,
    configitems,
    context,
    copies,
    debugcommands as hgdebugcommands,
    dispatch,
    error,
    exchange,
    extensions,
    hg,
    localrepo,
    match as matchmod,
    merge,
    mergestate as mergestatemod,
    patch,
    pycompat,
    registrar,
    repair,
    repoview,
    revset,
    scmutil,
    smartset,
    streamclone,
    util,
)
from . import (
    constants,
    debugcommands,
    fileserverclient,
    remotefilectx,
    remotefilelog,
    remotefilelogserver,
    repack as repackmod,
    shallowbundle,
    shallowrepo,
    shallowstore,
    shallowutil,
    shallowverifier,
)

# ensures debug commands are registered
hgdebugcommands.command

cmdtable = {}
command = registrar.command(cmdtable)

configtable = {}
configitem = registrar.configitem(configtable)

configitem(b'remotefilelog', b'debug', default=False)

configitem(b'remotefilelog', b'reponame', default=b'')
configitem(b'remotefilelog', b'cachepath', default=None)
configitem(b'remotefilelog', b'cachegroup', default=None)
configitem(b'remotefilelog', b'cacheprocess', default=None)
configitem(b'remotefilelog', b'cacheprocess.includepath', default=None)
configitem(b"remotefilelog", b"cachelimit", default=b"1000 GB")

configitem(
    b'remotefilelog',
    b'fallbackpath',
    default=configitems.dynamicdefault,
    alias=[(b'remotefilelog', b'fallbackrepo')],
)

configitem(b'remotefilelog', b'validatecachelog', default=None)
configitem(b'remotefilelog', b'validatecache', default=b'on')
configitem(b'remotefilelog', b'server', default=None)
configitem(b'remotefilelog', b'servercachepath', default=None)
configitem(b"remotefilelog", b"serverexpiration", default=30)
configitem(b'remotefilelog', b'backgroundrepack', default=False)
configitem(b'remotefilelog', b'bgprefetchrevs', default=None)
configitem(b'remotefilelog', b'pullprefetch', default=None)
configitem(b'remotefilelog', b'backgroundprefetch', default=False)
configitem(b'remotefilelog', b'prefetchdelay', default=120)
configitem(b'remotefilelog', b'prefetchdays', default=14)
# Other values include 'local' or 'none'. Any unrecognized value is 'all'.
configitem(b'remotefilelog', b'strip.includefiles', default='all')

configitem(b'remotefilelog', b'getfilesstep', default=10000)
configitem(b'remotefilelog', b'getfilestype', default=b'optimistic')
configitem(b'remotefilelog', b'batchsize', configitems.dynamicdefault)
configitem(b'remotefilelog', b'fetchwarning', default=b'')

configitem(b'remotefilelog', b'includepattern', default=None)
configitem(b'remotefilelog', b'excludepattern', default=None)

configitem(b'remotefilelog', b'gcrepack', default=False)
configitem(b'remotefilelog', b'repackonhggc', default=False)
configitem(b'repack', b'chainorphansbysize', default=True, experimental=True)

configitem(b'packs', b'maxpacksize', default=0)
configitem(b'packs', b'maxchainlen', default=1000)

configitem(b'devel', b'remotefilelog.bg-wait', default=False)

#  default TTL limit is 30 days
_defaultlimit = 60 * 60 * 24 * 30
configitem(b'remotefilelog', b'nodettl', default=_defaultlimit)

configitem(b'remotefilelog', b'data.gencountlimit', default=2),
configitem(
    b'remotefilelog', b'data.generations', default=[b'1GB', b'100MB', b'1MB']
)
configitem(b'remotefilelog', b'data.maxrepackpacks', default=50)
configitem(b'remotefilelog', b'data.repackmaxpacksize', default=b'4GB')
configitem(b'remotefilelog', b'data.repacksizelimit', default=b'100MB')

configitem(b'remotefilelog', b'history.gencountlimit', default=2),
configitem(b'remotefilelog', b'history.generations', default=[b'100MB'])
configitem(b'remotefilelog', b'history.maxrepackpacks', default=50)
configitem(b'remotefilelog', b'history.repackmaxpacksize', default=b'400MB')
configitem(b'remotefilelog', b'history.repacksizelimit', default=b'100MB')

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

repoclass = localrepo.localrepository
repoclass._basesupported.add(constants.SHALLOWREPO_REQUIREMENT)

isenabled = shallowutil.isenabled


def uisetup(ui):
    """Wraps user facing Mercurial commands to swap them out with shallow
    versions.
    """
    hg.wirepeersetupfuncs.append(fileserverclient.peersetup)

    entry = extensions.wrapcommand(commands.table, b'clone', cloneshallow)
    entry[1].append(
        (
            b'',
            b'shallow',
            None,
            _(b"create a shallow clone which uses remote file history"),
        )
    )

    extensions.wrapcommand(
        commands.table, b'debugindex', debugcommands.debugindex
    )
    extensions.wrapcommand(
        commands.table, b'debugindexdot', debugcommands.debugindexdot
    )
    extensions.wrapcommand(commands.table, b'log', log)
    extensions.wrapcommand(commands.table, b'pull', pull)

    # Prevent 'hg manifest --all'
    def _manifest(orig, ui, repo, *args, **opts):
        if isenabled(repo) and opts.get('all'):
            raise error.Abort(_(b"--all is not supported in a shallow repo"))

        return orig(ui, repo, *args, **opts)

    extensions.wrapcommand(commands.table, b"manifest", _manifest)

    # Wrap remotefilelog with lfs code
    def _lfsloaded(loaded=False):
        lfsmod = None
        try:
            lfsmod = extensions.find(b'lfs')
        except KeyError:
            pass
        if lfsmod:
            lfsmod.wrapfilelog(remotefilelog.remotefilelog)
            fileserverclient._lfsmod = lfsmod

    extensions.afterloaded(b'lfs', _lfsloaded)

    # debugdata needs remotefilelog.len to work
    extensions.wrapcommand(commands.table, b'debugdata', debugdatashallow)

    changegroup.cgpacker = shallowbundle.shallowcg1packer

    extensions.wrapfunction(
        changegroup, '_addchangegroupfiles', shallowbundle.addchangegroupfiles
    )
    extensions.wrapfunction(
        changegroup, 'makechangegroup', shallowbundle.makechangegroup
    )
    extensions.wrapfunction(localrepo, 'makestore', storewrapper)
    extensions.wrapfunction(exchange, 'pull', exchangepull)
    extensions.wrapfunction(merge, 'applyupdates', applyupdates)
    extensions.wrapfunction(merge, '_checkunknownfiles', checkunknownfiles)
    extensions.wrapfunction(context.workingctx, '_checklookup', checklookup)
    extensions.wrapfunction(scmutil, '_findrenames', findrenames)
    extensions.wrapfunction(
        copies, '_computeforwardmissing', computeforwardmissing
    )
    extensions.wrapfunction(dispatch, 'runcommand', runcommand)
    extensions.wrapfunction(repair, '_collectbrokencsets', _collectbrokencsets)
    extensions.wrapfunction(context.changectx, 'filectx', filectx)
    extensions.wrapfunction(context.workingctx, 'filectx', workingfilectx)
    extensions.wrapfunction(patch, 'trydiff', trydiff)
    extensions.wrapfunction(hg, 'verify', _verify)
    scmutil.fileprefetchhooks.add(b'remotefilelog', _fileprefetchhook)

    # disappointing hacks below
    extensions.wrapfunction(scmutil, 'getrenamedfn', getrenamedfn)
    extensions.wrapfunction(revset, 'filelog', filelogrevset)
    revset.symbols[b'filelog'] = revset.filelog


def cloneshallow(orig, ui, repo, *args, **opts):
    if opts.get('shallow'):
        repos = []

        def pull_shallow(orig, self, *args, **kwargs):
            if not isenabled(self):
                repos.append(self.unfiltered())
                # set up the client hooks so the post-clone update works
                setupclient(self.ui, self.unfiltered())

                # setupclient fixed the class on the repo itself
                # but we also need to fix it on the repoview
                if isinstance(self, repoview.repoview):
                    self.__class__.__bases__ = (
                        self.__class__.__bases__[0],
                        self.unfiltered().__class__,
                    )
                self.requirements.add(constants.SHALLOWREPO_REQUIREMENT)
                with self.lock():
                    # acquire store lock before writing requirements as some
                    # requirements might be written to .hg/store/requires
                    scmutil.writereporequirements(self)

                # Since setupclient hadn't been called, exchange.pull was not
                # wrapped. So we need to manually invoke our version of it.
                return exchangepull(orig, self, *args, **kwargs)
            else:
                return orig(self, *args, **kwargs)

        extensions.wrapfunction(exchange, 'pull', pull_shallow)

        # Wrap the stream logic to add requirements and to pass include/exclude
        # patterns around.
        def setup_streamout(repo, remote):
            # Replace remote.stream_out with a version that sends file
            # patterns.
            def stream_out_shallow(orig):
                caps = remote.capabilities()
                if constants.NETWORK_CAP_LEGACY_SSH_GETFILES in caps:
                    opts = {}
                    if repo.includepattern:
                        opts['includepattern'] = b'\0'.join(repo.includepattern)
                    if repo.excludepattern:
                        opts['excludepattern'] = b'\0'.join(repo.excludepattern)
                    return remote._callstream(b'stream_out_shallow', **opts)
                else:
                    return orig()

            extensions.wrapfunction(remote, 'stream_out', stream_out_shallow)

        def stream_wrap(orig, op):
            setup_streamout(op.repo, op.remote)
            return orig(op)

        extensions.wrapfunction(
            streamclone, 'maybeperformlegacystreamclone', stream_wrap
        )

        def canperformstreamclone(orig, pullop, bundle2=False):
            # remotefilelog is currently incompatible with the
            # bundle2 flavor of streamclones, so force us to use
            # v1 instead.
            if b'v2' in pullop.remotebundle2caps.get(b'stream', []):
                pullop.remotebundle2caps[b'stream'] = []
            if bundle2:
                return False, None
            supported, requirements = orig(pullop, bundle2=bundle2)
            if requirements is not None:
                requirements.add(constants.SHALLOWREPO_REQUIREMENT)
            return supported, requirements

        extensions.wrapfunction(
            streamclone, 'canperformstreamclone', canperformstreamclone
        )

    try:
        orig(ui, repo, *args, **opts)
    finally:
        if opts.get('shallow'):
            for r in repos:
                if hasattr(r, 'fileservice'):
                    r.fileservice.close()


def debugdatashallow(orig, *args, **kwds):
    oldlen = remotefilelog.remotefilelog.__len__
    try:
        remotefilelog.remotefilelog.__len__ = lambda x: 1
        return orig(*args, **kwds)
    finally:
        remotefilelog.remotefilelog.__len__ = oldlen


def reposetup(ui, repo):
    if not repo.local():
        return

    # put here intentionally bc doesnt work in uisetup
    ui.setconfig(b'hooks', b'update.prefetch', wcpprefetch)
    ui.setconfig(b'hooks', b'commit.prefetch', wcpprefetch)

    isserverenabled = ui.configbool(b'remotefilelog', b'server')
    isshallowclient = isenabled(repo)

    if isserverenabled and isshallowclient:
        raise RuntimeError(b"Cannot be both a server and shallow client.")

    if isshallowclient:
        setupclient(ui, repo)

    if isserverenabled:
        remotefilelogserver.setupserver(ui, repo)


def setupclient(ui, repo):
    if not isinstance(repo, localrepo.localrepository):
        return

    # Even clients get the server setup since they need to have the
    # wireprotocol endpoints registered.
    remotefilelogserver.onetimesetup(ui)
    onetimeclientsetup(ui)

    shallowrepo.wraprepo(repo)
    repo.store = shallowstore.wrapstore(repo.store)


def storewrapper(orig, requirements, path, vfstype):
    s = orig(requirements, path, vfstype)
    if constants.SHALLOWREPO_REQUIREMENT in requirements:
        s = shallowstore.wrapstore(s)

    return s


# prefetch files before update
def applyupdates(
    orig, repo, mresult, wctx, mctx, overwrite, wantfiledata, **opts
):
    if isenabled(repo):
        manifest = mctx.manifest()
        files = []
        for f, args, msg in mresult.getactions([mergestatemod.ACTION_GET]):
            files.append((f, hex(manifest[f])))
        # batch fetch the needed files from the server
        repo.fileservice.prefetch(files)
    return orig(repo, mresult, wctx, mctx, overwrite, wantfiledata, **opts)


# Prefetch merge checkunknownfiles
def checkunknownfiles(orig, repo, wctx, mctx, force, mresult, *args, **kwargs):
    if isenabled(repo):
        files = []
        sparsematch = repo.maybesparsematch(mctx.rev())
        for f, (m, actionargs, msg) in mresult.filemap():
            if sparsematch and not sparsematch(f):
                continue
            if m in (
                mergestatemod.ACTION_CREATED,
                mergestatemod.ACTION_DELETED_CHANGED,
                mergestatemod.ACTION_CREATED_MERGE,
            ):
                files.append((f, hex(mctx.filenode(f))))
            elif m == mergestatemod.ACTION_LOCAL_DIR_RENAME_GET:
                f2 = actionargs[0]
                files.append((f2, hex(mctx.filenode(f2))))
        # batch fetch the needed files from the server
        repo.fileservice.prefetch(files)
    return orig(repo, wctx, mctx, force, mresult, *args, **kwargs)


# Prefetch files before status attempts to look at their size and contents
def checklookup(orig, self, files, mtime_boundary):
    repo = self._repo
    if isenabled(repo):
        prefetchfiles = []
        for parent in self._parents:
            for f in files:
                if f in parent:
                    prefetchfiles.append((f, hex(parent.filenode(f))))
        # batch fetch the needed files from the server
        repo.fileservice.prefetch(prefetchfiles)
    return orig(self, files, mtime_boundary)


# Prefetch the logic that compares added and removed files for renames
def findrenames(orig, repo, matcher, added, removed, *args, **kwargs):
    if isenabled(repo):
        files = []
        pmf = repo[b'.'].manifest()
        for f in removed:
            if f in pmf:
                files.append((f, hex(pmf[f])))
        # batch fetch the needed files from the server
        repo.fileservice.prefetch(files)
    return orig(repo, matcher, added, removed, *args, **kwargs)


# prefetch files before pathcopies check
def computeforwardmissing(orig, a, b, match=None):
    missing = orig(a, b, match=match)
    repo = a._repo
    if isenabled(repo):
        mb = b.manifest()

        files = []
        sparsematch = repo.maybesparsematch(b.rev())
        if sparsematch:
            sparsemissing = set()
            for f in missing:
                if sparsematch(f):
                    files.append((f, hex(mb[f])))
                    sparsemissing.add(f)
            missing = sparsemissing

        # batch fetch the needed files from the server
        repo.fileservice.prefetch(files)
    return missing


# close cache miss server connection after the command has finished
def runcommand(orig, lui, repo, *args, **kwargs):
    fileservice = None
    # repo can be None when running in chg:
    # - at startup, reposetup was called because serve is not norepo
    # - a norepo command like "help" is called
    if repo and isenabled(repo):
        fileservice = repo.fileservice
    try:
        return orig(lui, repo, *args, **kwargs)
    finally:
        if fileservice:
            fileservice.close()


# prevent strip from stripping remotefilelogs
def _collectbrokencsets(orig, repo, files, striprev):
    if isenabled(repo):
        files = [f for f in files if not repo.shallowmatch(f)]
    return orig(repo, files, striprev)


# changectx wrappers
def filectx(orig, self, path, fileid=None, filelog=None):
    if fileid is None:
        fileid = self.filenode(path)
    if isenabled(self._repo) and self._repo.shallowmatch(path):
        return remotefilectx.remotefilectx(
            self._repo, path, fileid=fileid, changectx=self, filelog=filelog
        )
    return orig(self, path, fileid=fileid, filelog=filelog)


def workingfilectx(orig, self, path, filelog=None):
    if isenabled(self._repo) and self._repo.shallowmatch(path):
        return remotefilectx.remoteworkingfilectx(
            self._repo, path, workingctx=self, filelog=filelog
        )
    return orig(self, path, filelog=filelog)


# prefetch required revisions before a diff
def trydiff(
    orig,
    repo,
    revs,
    ctx1,
    ctx2,
    modified,
    added,
    removed,
    copy,
    getfilectx,
    *args,
    **kwargs
):
    if isenabled(repo):
        prefetch = []
        mf1 = ctx1.manifest()
        for fname in modified + added + removed:
            if fname in mf1:
                fnode = getfilectx(fname, ctx1).filenode()
                # fnode can be None if it's a edited working ctx file
                if fnode:
                    prefetch.append((fname, hex(fnode)))
            if fname not in removed:
                fnode = getfilectx(fname, ctx2).filenode()
                if fnode:
                    prefetch.append((fname, hex(fnode)))

        repo.fileservice.prefetch(prefetch)

    return orig(
        repo,
        revs,
        ctx1,
        ctx2,
        modified,
        added,
        removed,
        copy,
        getfilectx,
        *args,
        **kwargs
    )


# Prevent verify from processing files
# a stub for mercurial.hg.verify()
def _verify(orig, repo, level=None):
    lock = repo.lock()
    try:
        return shallowverifier.shallowverifier(repo).verify()
    finally:
        lock.release()


clientonetime = False


def onetimeclientsetup(ui):
    global clientonetime
    if clientonetime:
        return
    clientonetime = True

    # Don't commit filelogs until we know the commit hash, since the hash
    # is present in the filelog blob.
    # This violates Mercurial's filelog->manifest->changelog write order,
    # but is generally fine for client repos.
    pendingfilecommits = []

    def addrawrevision(
        orig,
        self,
        rawtext,
        transaction,
        link,
        p1,
        p2,
        node,
        flags,
        cachedelta=None,
        _metatuple=None,
    ):
        if isinstance(link, int):
            pendingfilecommits.append(
                (
                    self,
                    rawtext,
                    transaction,
                    link,
                    p1,
                    p2,
                    node,
                    flags,
                    cachedelta,
                    _metatuple,
                )
            )
            return node
        else:
            return orig(
                self,
                rawtext,
                transaction,
                link,
                p1,
                p2,
                node,
                flags,
                cachedelta,
                _metatuple=_metatuple,
            )

    extensions.wrapfunction(
        remotefilelog.remotefilelog, 'addrawrevision', addrawrevision
    )

    def changelogadd(orig, self, *args, **kwargs):
        oldlen = len(self)
        node = orig(self, *args, **kwargs)
        newlen = len(self)
        if oldlen != newlen:
            for oldargs in pendingfilecommits:
                log, rt, tr, link, p1, p2, n, fl, c, m = oldargs
                linknode = self.node(link)
                if linknode == node:
                    log.addrawrevision(rt, tr, linknode, p1, p2, n, fl, c, m)
                else:
                    raise error.ProgrammingError(
                        b'pending multiple integer revisions are not supported'
                    )
        else:
            # "link" is actually wrong here (it is set to len(changelog))
            # if changelog remains unchanged, skip writing file revisions
            # but still do a sanity check about pending multiple revisions
            if len({x[3] for x in pendingfilecommits}) > 1:
                raise error.ProgrammingError(
                    b'pending multiple integer revisions are not supported'
                )
        del pendingfilecommits[:]
        return node

    extensions.wrapfunction(changelog.changelog, 'add', changelogadd)


def getrenamedfn(orig, repo, endrev=None):
    if not isenabled(repo) or copies.usechangesetcentricalgo(repo):
        return orig(repo, endrev)

    rcache = {}

    def getrenamed(fn, rev):
        """looks up all renames for a file (up to endrev) the first
        time the file is given. It indexes on the changerev and only
        parses the manifest if linkrev != changerev.
        Returns rename info for fn at changerev rev."""
        if rev in rcache.setdefault(fn, {}):
            return rcache[fn][rev]

        try:
            fctx = repo[rev].filectx(fn)
            for ancestor in fctx.ancestors():
                if ancestor.path() == fn:
                    renamed = ancestor.renamed()
                    rcache[fn][ancestor.rev()] = renamed and renamed[0]

            renamed = fctx.renamed()
            return renamed and renamed[0]
        except error.LookupError:
            return None

    return getrenamed


def filelogrevset(orig, repo, subset, x):
    """``filelog(pattern)``
    Changesets connected to the specified filelog.

    For performance reasons, ``filelog()`` does not show every changeset
    that affects the requested file(s). See :hg:`help log` for details. For
    a slower, more accurate result, use ``file()``.
    """

    if not isenabled(repo):
        return orig(repo, subset, x)

    # i18n: "filelog" is a keyword
    pat = revset.getstring(x, _(b"filelog requires a pattern"))
    m = matchmod.match(
        repo.root, repo.getcwd(), [pat], default=b'relpath', ctx=repo[None]
    )
    s = set()

    if not matchmod.patkind(pat):
        # slow
        for r in subset:
            ctx = repo[r]
            cfiles = ctx.files()
            for f in m.files():
                if f in cfiles:
                    s.add(ctx.rev())
                    break
    else:
        # partial
        files = (f for f in repo[None] if m(f))
        for f in files:
            fctx = repo[None].filectx(f)
            s.add(fctx.linkrev())
            for actx in fctx.ancestors():
                s.add(actx.linkrev())

    return smartset.baseset([r for r in subset if r in s])


@command(b'gc', [], _(b'hg gc [REPO...]'), norepo=True)
def gc(ui, *args, **opts):
    """garbage collect the client and server filelog caches"""
    cachepaths = set()

    # get the system client cache
    systemcache = shallowutil.getcachepath(ui, allowempty=True)
    if systemcache:
        cachepaths.add(systemcache)

    # get repo client and server cache
    repopaths = []
    pwd = ui.environ.get(b'PWD')
    if pwd:
        repopaths.append(pwd)

    repopaths.extend(args)
    repos = []
    for repopath in repopaths:
        try:
            repo = hg.peer(ui, {}, repopath)
            repos.append(repo)

            repocache = shallowutil.getcachepath(repo.ui, allowempty=True)
            if repocache:
                cachepaths.add(repocache)
        except error.RepoError:
            pass

    # gc client cache
    for cachepath in cachepaths:
        gcclient(ui, cachepath)

    # gc server cache
    for repo in repos:
        remotefilelogserver.gcserver(ui, repo._repo)


def gcclient(ui, cachepath):
    # get list of repos that use this cache
    repospath = os.path.join(cachepath, b'repos')
    if not os.path.exists(repospath):
        ui.warn(_(b"no known cache at %s\n") % cachepath)
        return

    reposfile = open(repospath, b'rb')
    repos = {r[:-1] for r in reposfile.readlines()}
    reposfile.close()

    # build list of useful files
    validrepos = []
    keepkeys = set()

    sharedcache = None
    filesrepacked = False

    count = 0
    progress = ui.makeprogress(
        _(b"analyzing repositories"), unit=b"repos", total=len(repos)
    )
    for path in repos:
        progress.update(count)
        count += 1
        try:
            path = util.expandpath(os.path.normpath(path))
        except TypeError as e:
            ui.warn(_(b"warning: malformed path: %r:%s\n") % (path, e))
            traceback.print_exc()
            continue
        try:
            peer = hg.peer(ui, {}, path)
            repo = peer._repo
        except error.RepoError:
            continue

        validrepos.append(path)

        # Protect against any repo or config changes that have happened since
        # this repo was added to the repos file. We'd rather this loop succeed
        # and too much be deleted, than the loop fail and nothing gets deleted.
        if not isenabled(repo):
            continue

        if not hasattr(repo, 'name'):
            ui.warn(
                _(b"repo %s is a misconfigured remotefilelog repo\n") % path
            )
            continue

        # If garbage collection on repack and repack on hg gc are enabled
        # then loose files are repacked and garbage collected.
        # Otherwise regular garbage collection is performed.
        repackonhggc = repo.ui.configbool(b'remotefilelog', b'repackonhggc')
        gcrepack = repo.ui.configbool(b'remotefilelog', b'gcrepack')
        if repackonhggc and gcrepack:
            try:
                repackmod.incrementalrepack(repo)
                filesrepacked = True
                continue
            except (IOError, repackmod.RepackAlreadyRunning):
                # If repack cannot be performed due to not enough disk space
                # continue doing garbage collection of loose files w/o repack
                pass

        reponame = repo.name
        if not sharedcache:
            sharedcache = repo.sharedstore

        # Compute a keepset which is not garbage collected
        def keyfn(fname, fnode):
            return fileserverclient.getcachekey(reponame, fname, hex(fnode))

        keepkeys = repackmod.keepset(repo, keyfn=keyfn, lastkeepkeys=keepkeys)

    progress.complete()

    # write list of valid repos back
    oldumask = os.umask(0o002)
    try:
        reposfile = open(repospath, b'wb')
        reposfile.writelines([(b"%s\n" % r) for r in validrepos])
        reposfile.close()
    finally:
        os.umask(oldumask)

    # prune cache
    if sharedcache is not None:
        sharedcache.gc(keepkeys)
    elif not filesrepacked:
        ui.warn(_(b"warning: no valid repos in repofile\n"))


def log(orig, ui, repo, *pats, **opts):
    if not isenabled(repo):
        return orig(ui, repo, *pats, **opts)

    follow = opts.get('follow')
    revs = opts.get('rev')
    if pats:
        # Force slowpath for non-follow patterns and follows that start from
        # non-working-copy-parent revs.
        if not follow or revs:
            # This forces the slowpath
            opts['removed'] = True

        # If this is a non-follow log without any revs specified, recommend that
        # the user add -f to speed it up.
        if not follow and not revs:
            match = scmutil.match(repo[b'.'], pats, pycompat.byteskwargs(opts))
            isfile = not match.anypats()
            if isfile:
                for file in match.files():
                    if not os.path.isfile(repo.wjoin(file)):
                        isfile = False
                        break

            if isfile:
                ui.warn(
                    _(
                        b"warning: file log can be slow on large repos - "
                        + b"use -f to speed it up\n"
                    )
                )

    return orig(ui, repo, *pats, **opts)


def revdatelimit(ui, revset):
    """Update revset so that only changesets no older than 'prefetchdays' days
    are included. The default value is set to 14 days. If 'prefetchdays' is set
    to zero or negative value then date restriction is not applied.
    """
    days = ui.configint(b'remotefilelog', b'prefetchdays')
    if days > 0:
        revset = b'(%s) & date(-%s)' % (revset, days)
    return revset


def readytofetch(repo):
    """Check that enough time has passed since the last background prefetch.
    This only relates to prefetches after operations that change the working
    copy parent. Default delay between background prefetches is 2 minutes.
    """
    timeout = repo.ui.configint(b'remotefilelog', b'prefetchdelay')
    fname = repo.vfs.join(b'lastprefetch')

    ready = False
    with open(fname, b'a'):
        # the with construct above is used to avoid race conditions
        modtime = os.path.getmtime(fname)
        if (time.time() - modtime) > timeout:
            os.utime(fname, None)
            ready = True

    return ready


def wcpprefetch(ui, repo, **kwargs):
    """Prefetches in background revisions specified by bgprefetchrevs revset.
    Does background repack if backgroundrepack flag is set in config.
    """
    shallow = isenabled(repo)
    bgprefetchrevs = ui.config(b'remotefilelog', b'bgprefetchrevs')
    isready = readytofetch(repo)

    if not (shallow and bgprefetchrevs and isready):
        return

    bgrepack = repo.ui.configbool(b'remotefilelog', b'backgroundrepack')
    # update a revset with a date limit
    bgprefetchrevs = revdatelimit(ui, bgprefetchrevs)

    def anon(unused_success):
        if hasattr(repo, 'ranprefetch') and repo.ranprefetch:
            return
        repo.ranprefetch = True
        repo.backgroundprefetch(bgprefetchrevs, repack=bgrepack)

    repo._afterlock(anon)


def pull(orig, ui, repo, *pats, **opts):
    result = orig(ui, repo, *pats, **opts)

    if isenabled(repo):
        # prefetch if it's configured
        prefetchrevset = ui.config(b'remotefilelog', b'pullprefetch')
        bgrepack = repo.ui.configbool(b'remotefilelog', b'backgroundrepack')
        bgprefetch = repo.ui.configbool(b'remotefilelog', b'backgroundprefetch')

        if prefetchrevset:
            ui.status(_(b"prefetching file contents\n"))
            revs = scmutil.revrange(repo, [prefetchrevset])
            base = repo[b'.'].rev()
            if bgprefetch:
                repo.backgroundprefetch(prefetchrevset, repack=bgrepack)
            else:
                repo.prefetch(revs, base=base)
                if bgrepack:
                    repackmod.backgroundrepack(repo, incremental=True)
        elif bgrepack:
            repackmod.backgroundrepack(repo, incremental=True)

    return result


def exchangepull(orig, repo, remote, *args, **kwargs):
    # Hook into the callstream/getbundle to insert bundle capabilities
    # during a pull.
    def localgetbundle(
        orig, source, heads=None, common=None, bundlecaps=None, **kwargs
    ):
        if not bundlecaps:
            bundlecaps = set()
        bundlecaps.add(constants.BUNDLE2_CAPABLITY)
        return orig(
            source, heads=heads, common=common, bundlecaps=bundlecaps, **kwargs
        )

    if hasattr(remote, '_callstream'):
        remote._localrepo = repo
    elif hasattr(remote, 'getbundle'):
        extensions.wrapfunction(remote, 'getbundle', localgetbundle)

    return orig(repo, remote, *args, **kwargs)


def _fileprefetchhook(repo, revmatches):
    if isenabled(repo):
        allfiles = []
        for rev, match in revmatches:
            if rev == wdirrev or rev is None:
                continue
            ctx = repo[rev]
            mf = ctx.manifest()
            sparsematch = repo.maybesparsematch(ctx.rev())
            for path in ctx.walk(match):
                if (not sparsematch or sparsematch(path)) and path in mf:
                    allfiles.append((path, hex(mf[path])))
        repo.fileservice.prefetch(allfiles)


@command(
    b'debugremotefilelog',
    [
        (b'd', b'decompress', None, _(b'decompress the filelog first')),
    ],
    _(b'hg debugremotefilelog <path>'),
    norepo=True,
)
def debugremotefilelog(ui, path, **opts):
    return debugcommands.debugremotefilelog(ui, path, **opts)


@command(
    b'verifyremotefilelog',
    [
        (b'd', b'decompress', None, _(b'decompress the filelogs first')),
    ],
    _(b'hg verifyremotefilelogs <directory>'),
    norepo=True,
)
def verifyremotefilelog(ui, path, **opts):
    return debugcommands.verifyremotefilelog(ui, path, **opts)


@command(
    b'debugdatapack',
    [
        (b'', b'long', None, _(b'print the long hashes')),
        (b'', b'node', b'', _(b'dump the contents of node'), b'NODE'),
    ],
    _(b'hg debugdatapack <paths>'),
    norepo=True,
)
def debugdatapack(ui, *paths, **opts):
    return debugcommands.debugdatapack(ui, *paths, **opts)


@command(b'debughistorypack', [], _(b'hg debughistorypack <path>'), norepo=True)
def debughistorypack(ui, path, **opts):
    return debugcommands.debughistorypack(ui, path)


@command(b'debugkeepset', [], _(b'hg debugkeepset'))
def debugkeepset(ui, repo, **opts):
    # The command is used to measure keepset computation time
    def keyfn(fname, fnode):
        return fileserverclient.getcachekey(repo.name, fname, hex(fnode))

    repackmod.keepset(repo, keyfn)
    return


@command(b'debugwaitonrepack', [], _(b'hg debugwaitonrepack'))
def debugwaitonrepack(ui, repo, **opts):
    return debugcommands.debugwaitonrepack(repo)


@command(b'debugwaitonprefetch', [], _(b'hg debugwaitonprefetch'))
def debugwaitonprefetch(ui, repo, **opts):
    return debugcommands.debugwaitonprefetch(repo)


def resolveprefetchopts(ui, opts):
    if not opts.get(b'rev'):
        revset = [b'.', b'draft()']

        prefetchrevset = ui.config(b'remotefilelog', b'pullprefetch', None)
        if prefetchrevset:
            revset.append(b'(%s)' % prefetchrevset)
        bgprefetchrevs = ui.config(b'remotefilelog', b'bgprefetchrevs', None)
        if bgprefetchrevs:
            revset.append(b'(%s)' % bgprefetchrevs)
        revset = b'+'.join(revset)

        # update a revset with a date limit
        revset = revdatelimit(ui, revset)

        opts[b'rev'] = [revset]

    if not opts.get(b'base'):
        opts[b'base'] = None

    return opts


@command(
    b'prefetch',
    [
        (b'r', b'rev', [], _(b'prefetch the specified revisions'), _(b'REV')),
        (b'', b'repack', False, _(b'run repack after prefetch')),
        (b'b', b'base', b'', _(b"rev that is assumed to already be local")),
    ]
    + commands.walkopts,
    _(b'hg prefetch [OPTIONS] [FILE...]'),
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def prefetch(ui, repo, *pats, **opts):
    """prefetch file revisions from the server

    Prefetchs file revisions for the specified revs and stores them in the
    local remotefilelog cache.  If no rev is specified, the default rev is
    used which is the union of dot, draft, pullprefetch and bgprefetchrev.
    File names or patterns can be used to limit which files are downloaded.

    Return 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    if not isenabled(repo):
        raise error.Abort(_(b"repo is not shallow"))

    opts = resolveprefetchopts(ui, opts)
    revs = scmutil.revrange(repo, opts.get(b'rev'))
    repo.prefetch(revs, opts.get(b'base'), pats, opts)

    # Run repack in background
    if opts.get(b'repack'):
        repackmod.backgroundrepack(repo, incremental=True)


@command(
    b'repack',
    [
        (b'', b'background', None, _(b'run in a background process'), None),
        (b'', b'incremental', None, _(b'do an incremental repack'), None),
        (
            b'',
            b'packsonly',
            None,
            _(b'only repack packs (skip loose objects)'),
            None,
        ),
    ],
    _(b'hg repack [OPTIONS]'),
)
def repack_(ui, repo, *pats, **opts):
    if opts.get('background'):
        repackmod.backgroundrepack(
            repo,
            incremental=opts.get('incremental'),
            packsonly=opts.get('packsonly', False),
        )
        return

    options = {b'packsonly': opts.get('packsonly')}

    try:
        if opts.get('incremental'):
            repackmod.incrementalrepack(repo, options=options)
        else:
            repackmod.fullrepack(repo, options=options)
    except repackmod.RepackAlreadyRunning as ex:
        # Don't propogate the exception if the repack is already in
        # progress, since we want the command to exit 0.
        repo.ui.warn(b'%s\n' % ex)
