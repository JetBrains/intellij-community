# hg.py - repository classes for mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno
import os
import shutil
import stat

from .i18n import _
from .node import (
    hex,
    sha1nodeconstants,
    short,
)
from .pycompat import getattr

from . import (
    bookmarks,
    bundlerepo,
    cmdutil,
    destutil,
    discovery,
    error,
    exchange,
    extensions,
    graphmod,
    httppeer,
    localrepo,
    lock,
    logcmdutil,
    logexchange,
    merge as mergemod,
    mergestate as mergestatemod,
    narrowspec,
    phases,
    requirements,
    scmutil,
    sshpeer,
    statichttprepo,
    ui as uimod,
    unionrepo,
    url,
    util,
    verify as verifymod,
    vfs as vfsmod,
)
from .interfaces import repository as repositorymod
from .utils import (
    hashutil,
    stringutil,
    urlutil,
)


release = lock.release

# shared features
sharedbookmarks = b'bookmarks'


def _local(path):
    path = util.expandpath(urlutil.urllocalpath(path))

    try:
        # we use os.stat() directly here instead of os.path.isfile()
        # because the latter started returning `False` on invalid path
        # exceptions starting in 3.8 and we care about handling
        # invalid paths specially here.
        st = os.stat(path)
        isfile = stat.S_ISREG(st.st_mode)
    # Python 2 raises TypeError, Python 3 ValueError.
    except (TypeError, ValueError) as e:
        raise error.Abort(
            _(b'invalid path %s: %s') % (path, stringutil.forcebytestr(e))
        )
    except OSError:
        isfile = False

    return isfile and bundlerepo or localrepo


def addbranchrevs(lrepo, other, branches, revs):
    peer = other.peer()  # a courtesy to callers using a localrepo for other
    hashbranch, branches = branches
    if not hashbranch and not branches:
        x = revs or None
        if revs:
            y = revs[0]
        else:
            y = None
        return x, y
    if revs:
        revs = list(revs)
    else:
        revs = []

    if not peer.capable(b'branchmap'):
        if branches:
            raise error.Abort(_(b"remote branch lookup not supported"))
        revs.append(hashbranch)
        return revs, revs[0]

    with peer.commandexecutor() as e:
        branchmap = e.callcommand(b'branchmap', {}).result()

    def primary(branch):
        if branch == b'.':
            if not lrepo:
                raise error.Abort(_(b"dirstate branch not accessible"))
            branch = lrepo.dirstate.branch()
        if branch in branchmap:
            revs.extend(hex(r) for r in reversed(branchmap[branch]))
            return True
        else:
            return False

    for branch in branches:
        if not primary(branch):
            raise error.RepoLookupError(_(b"unknown branch '%s'") % branch)
    if hashbranch:
        if not primary(hashbranch):
            revs.append(hashbranch)
    return revs, revs[0]


def parseurl(path, branches=None):
    '''parse url#branch, returning (url, (branch, branches))'''
    msg = b'parseurl(...) moved to mercurial.utils.urlutil'
    util.nouideprecwarn(msg, b'6.0', stacklevel=2)
    return urlutil.parseurl(path, branches=branches)


schemes = {
    b'bundle': bundlerepo,
    b'union': unionrepo,
    b'file': _local,
    b'http': httppeer,
    b'https': httppeer,
    b'ssh': sshpeer,
    b'static-http': statichttprepo,
}


def _peerlookup(path):
    u = urlutil.url(path)
    scheme = u.scheme or b'file'
    thing = schemes.get(scheme) or schemes[b'file']
    try:
        return thing(path)
    except TypeError:
        # we can't test callable(thing) because 'thing' can be an unloaded
        # module that implements __call__
        if not util.safehasattr(thing, b'instance'):
            raise
        return thing


def islocal(repo):
    '''return true if repo (or path pointing to repo) is local'''
    if isinstance(repo, bytes):
        try:
            return _peerlookup(repo).islocal(repo)
        except AttributeError:
            return False
    return repo.local()


def openpath(ui, path, sendaccept=True):
    '''open path with open if local, url.open if remote'''
    pathurl = urlutil.url(path, parsequery=False, parsefragment=False)
    if pathurl.islocal():
        return util.posixfile(pathurl.localpath(), b'rb')
    else:
        return url.open(ui, path, sendaccept=sendaccept)


# a list of (ui, repo) functions called for wire peer initialization
wirepeersetupfuncs = []


def _peerorrepo(
    ui, path, create=False, presetupfuncs=None, intents=None, createopts=None
):
    """return a repository object for the specified path"""
    obj = _peerlookup(path).instance(
        ui, path, create, intents=intents, createopts=createopts
    )
    ui = getattr(obj, "ui", ui)
    for f in presetupfuncs or []:
        f(ui, obj)
    ui.log(b'extension', b'- executing reposetup hooks\n')
    with util.timedcm('all reposetup') as allreposetupstats:
        for name, module in extensions.extensions(ui):
            ui.log(b'extension', b'  - running reposetup for %s\n', name)
            hook = getattr(module, 'reposetup', None)
            if hook:
                with util.timedcm('reposetup %r', name) as stats:
                    hook(ui, obj)
                ui.log(
                    b'extension', b'  > reposetup for %s took %s\n', name, stats
                )
    ui.log(b'extension', b'> all reposetup took %s\n', allreposetupstats)
    if not obj.local():
        for f in wirepeersetupfuncs:
            f(ui, obj)
    return obj


def repository(
    ui,
    path=b'',
    create=False,
    presetupfuncs=None,
    intents=None,
    createopts=None,
):
    """return a repository object for the specified path"""
    peer = _peerorrepo(
        ui,
        path,
        create,
        presetupfuncs=presetupfuncs,
        intents=intents,
        createopts=createopts,
    )
    repo = peer.local()
    if not repo:
        raise error.Abort(
            _(b"repository '%s' is not local") % (path or peer.url())
        )
    return repo.filtered(b'visible')


def peer(uiorrepo, opts, path, create=False, intents=None, createopts=None):
    '''return a repository peer for the specified path'''
    rui = remoteui(uiorrepo, opts)
    return _peerorrepo(
        rui, path, create, intents=intents, createopts=createopts
    ).peer()


def defaultdest(source):
    """return default destination of clone if none is given

    >>> defaultdest(b'foo')
    'foo'
    >>> defaultdest(b'/foo/bar')
    'bar'
    >>> defaultdest(b'/')
    ''
    >>> defaultdest(b'')
    ''
    >>> defaultdest(b'http://example.org/')
    ''
    >>> defaultdest(b'http://example.org/foo/')
    'foo'
    """
    path = urlutil.url(source).path
    if not path:
        return b''
    return os.path.basename(os.path.normpath(path))


def sharedreposource(repo):
    """Returns repository object for source repository of a shared repo.

    If repo is not a shared repository, returns None.
    """
    if repo.sharedpath == repo.path:
        return None

    if util.safehasattr(repo, b'srcrepo') and repo.srcrepo:
        return repo.srcrepo

    # the sharedpath always ends in the .hg; we want the path to the repo
    source = repo.vfs.split(repo.sharedpath)[0]
    srcurl, branches = urlutil.parseurl(source)
    srcrepo = repository(repo.ui, srcurl)
    repo.srcrepo = srcrepo
    return srcrepo


def share(
    ui,
    source,
    dest=None,
    update=True,
    bookmarks=True,
    defaultpath=None,
    relative=False,
):
    '''create a shared repository'''

    if not islocal(source):
        raise error.Abort(_(b'can only share local repositories'))

    if not dest:
        dest = defaultdest(source)
    else:
        dest = urlutil.get_clone_path(ui, dest)[1]

    if isinstance(source, bytes):
        origsource, source, branches = urlutil.get_clone_path(ui, source)
        srcrepo = repository(ui, source)
        rev, checkout = addbranchrevs(srcrepo, srcrepo, branches, None)
    else:
        srcrepo = source.local()
        checkout = None

    shareditems = set()
    if bookmarks:
        shareditems.add(sharedbookmarks)

    r = repository(
        ui,
        dest,
        create=True,
        createopts={
            b'sharedrepo': srcrepo,
            b'sharedrelative': relative,
            b'shareditems': shareditems,
        },
    )

    postshare(srcrepo, r, defaultpath=defaultpath)
    r = repository(ui, dest)
    _postshareupdate(r, update, checkout=checkout)
    return r


def _prependsourcehgrc(repo):
    """copies the source repo config and prepend it in current repo .hg/hgrc
    on unshare. This is only done if the share was perfomed using share safe
    method where we share config of source in shares"""
    srcvfs = vfsmod.vfs(repo.sharedpath)
    dstvfs = vfsmod.vfs(repo.path)

    if not srcvfs.exists(b'hgrc'):
        return

    currentconfig = b''
    if dstvfs.exists(b'hgrc'):
        currentconfig = dstvfs.read(b'hgrc')

    with dstvfs(b'hgrc', b'wb') as fp:
        sourceconfig = srcvfs.read(b'hgrc')
        fp.write(b"# Config copied from shared source\n")
        fp.write(sourceconfig)
        fp.write(b'\n')
        fp.write(currentconfig)


def unshare(ui, repo):
    """convert a shared repository to a normal one

    Copy the store data to the repo and remove the sharedpath data.

    Returns a new repository object representing the unshared repository.

    The passed repository object is not usable after this function is
    called.
    """

    with repo.lock():
        # we use locks here because if we race with commit, we
        # can end up with extra data in the cloned revlogs that's
        # not pointed to by changesets, thus causing verify to
        # fail
        destlock = copystore(ui, repo, repo.path)
        with destlock or util.nullcontextmanager():
            if requirements.SHARESAFE_REQUIREMENT in repo.requirements:
                # we were sharing .hg/hgrc of the share source with the current
                # repo. We need to copy that while unsharing otherwise it can
                # disable hooks and other checks
                _prependsourcehgrc(repo)

            sharefile = repo.vfs.join(b'sharedpath')
            util.rename(sharefile, sharefile + b'.old')

            repo.requirements.discard(requirements.SHARED_REQUIREMENT)
            repo.requirements.discard(requirements.RELATIVE_SHARED_REQUIREMENT)
            scmutil.writereporequirements(repo)

    # Removing share changes some fundamental properties of the repo instance.
    # So we instantiate a new repo object and operate on it rather than
    # try to keep the existing repo usable.
    newrepo = repository(repo.baseui, repo.root, create=False)

    # TODO: figure out how to access subrepos that exist, but were previously
    #       removed from .hgsub
    c = newrepo[b'.']
    subs = c.substate
    for s in sorted(subs):
        c.sub(s).unshare()

    localrepo.poisonrepository(repo)

    return newrepo


def postshare(sourcerepo, destrepo, defaultpath=None):
    """Called after a new shared repo is created.

    The new repo only has a requirements file and pointer to the source.
    This function configures additional shared data.

    Extensions can wrap this function and write additional entries to
    destrepo/.hg/shared to indicate additional pieces of data to be shared.
    """
    default = defaultpath or sourcerepo.ui.config(b'paths', b'default')
    if default:
        template = b'[paths]\ndefault = %s\n'
        destrepo.vfs.write(b'hgrc', util.tonativeeol(template % default))
    if requirements.NARROW_REQUIREMENT in sourcerepo.requirements:
        with destrepo.wlock():
            narrowspec.copytoworkingcopy(destrepo)


def _postshareupdate(repo, update, checkout=None):
    """Maybe perform a working directory update after a shared repo is created.

    ``update`` can be a boolean or a revision to update to.
    """
    if not update:
        return

    repo.ui.status(_(b"updating working directory\n"))
    if update is not True:
        checkout = update
    for test in (checkout, b'default', b'tip'):
        if test is None:
            continue
        try:
            uprev = repo.lookup(test)
            break
        except error.RepoLookupError:
            continue
    _update(repo, uprev)


def copystore(ui, srcrepo, destpath):
    """copy files from store of srcrepo in destpath

    returns destlock
    """
    destlock = None
    try:
        hardlink = None
        topic = _(b'linking') if hardlink else _(b'copying')
        with ui.makeprogress(topic, unit=_(b'files')) as progress:
            num = 0
            srcpublishing = srcrepo.publishing()
            srcvfs = vfsmod.vfs(srcrepo.sharedpath)
            dstvfs = vfsmod.vfs(destpath)
            for f in srcrepo.store.copylist():
                if srcpublishing and f.endswith(b'phaseroots'):
                    continue
                dstbase = os.path.dirname(f)
                if dstbase and not dstvfs.exists(dstbase):
                    dstvfs.mkdir(dstbase)
                if srcvfs.exists(f):
                    if f.endswith(b'data'):
                        # 'dstbase' may be empty (e.g. revlog format 0)
                        lockfile = os.path.join(dstbase, b"lock")
                        # lock to avoid premature writing to the target
                        destlock = lock.lock(dstvfs, lockfile)
                    hardlink, n = util.copyfiles(
                        srcvfs.join(f), dstvfs.join(f), hardlink, progress
                    )
                    num += n
            if hardlink:
                ui.debug(b"linked %d files\n" % num)
            else:
                ui.debug(b"copied %d files\n" % num)
        return destlock
    except:  # re-raises
        release(destlock)
        raise


def clonewithshare(
    ui,
    peeropts,
    sharepath,
    source,
    srcpeer,
    dest,
    pull=False,
    rev=None,
    update=True,
    stream=False,
):
    """Perform a clone using a shared repo.

    The store for the repository will be located at <sharepath>/.hg. The
    specified revisions will be cloned or pulled from "source". A shared repo
    will be created at "dest" and a working copy will be created if "update" is
    True.
    """
    revs = None
    if rev:
        if not srcpeer.capable(b'lookup'):
            raise error.Abort(
                _(
                    b"src repository does not support "
                    b"revision lookup and so doesn't "
                    b"support clone by revision"
                )
            )

        # TODO this is batchable.
        remoterevs = []
        for r in rev:
            with srcpeer.commandexecutor() as e:
                remoterevs.append(
                    e.callcommand(
                        b'lookup',
                        {
                            b'key': r,
                        },
                    ).result()
                )
        revs = remoterevs

    # Obtain a lock before checking for or cloning the pooled repo otherwise
    # 2 clients may race creating or populating it.
    pooldir = os.path.dirname(sharepath)
    # lock class requires the directory to exist.
    try:
        util.makedir(pooldir, False)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise

    poolvfs = vfsmod.vfs(pooldir)
    basename = os.path.basename(sharepath)

    with lock.lock(poolvfs, b'%s.lock' % basename):
        if os.path.exists(sharepath):
            ui.status(
                _(b'(sharing from existing pooled repository %s)\n') % basename
            )
        else:
            ui.status(
                _(b'(sharing from new pooled repository %s)\n') % basename
            )
            # Always use pull mode because hardlinks in share mode don't work
            # well. Never update because working copies aren't necessary in
            # share mode.
            clone(
                ui,
                peeropts,
                source,
                dest=sharepath,
                pull=True,
                revs=rev,
                update=False,
                stream=stream,
            )

    # Resolve the value to put in [paths] section for the source.
    if islocal(source):
        defaultpath = util.abspath(urlutil.urllocalpath(source))
    else:
        defaultpath = source

    sharerepo = repository(ui, path=sharepath)
    destrepo = share(
        ui,
        sharerepo,
        dest=dest,
        update=False,
        bookmarks=False,
        defaultpath=defaultpath,
    )

    # We need to perform a pull against the dest repo to fetch bookmarks
    # and other non-store data that isn't shared by default. In the case of
    # non-existing shared repo, this means we pull from the remote twice. This
    # is a bit weird. But at the time it was implemented, there wasn't an easy
    # way to pull just non-changegroup data.
    exchange.pull(destrepo, srcpeer, heads=revs)

    _postshareupdate(destrepo, update)

    return srcpeer, peer(ui, peeropts, dest)


# Recomputing caches is often slow on big repos, so copy them.
def _copycache(srcrepo, dstcachedir, fname):
    """copy a cache from srcrepo to destcachedir (if it exists)"""
    srcfname = srcrepo.cachevfs.join(fname)
    dstfname = os.path.join(dstcachedir, fname)
    if os.path.exists(srcfname):
        if not os.path.exists(dstcachedir):
            os.mkdir(dstcachedir)
        util.copyfile(srcfname, dstfname)


def clone(
    ui,
    peeropts,
    source,
    dest=None,
    pull=False,
    revs=None,
    update=True,
    stream=False,
    branch=None,
    shareopts=None,
    storeincludepats=None,
    storeexcludepats=None,
    depth=None,
):
    """Make a copy of an existing repository.

    Create a copy of an existing repository in a new directory.  The
    source and destination are URLs, as passed to the repository
    function.  Returns a pair of repository peers, the source and
    newly created destination.

    The location of the source is added to the new repository's
    .hg/hgrc file, as the default to be used for future pulls and
    pushes.

    If an exception is raised, the partly cloned/updated destination
    repository will be deleted.

    Arguments:

    source: repository object or URL

    dest: URL of destination repository to create (defaults to base
    name of source repository)

    pull: always pull from source repository, even in local case or if the
    server prefers streaming

    stream: stream raw data uncompressed from repository (fast over
    LAN, slow over WAN)

    revs: revision to clone up to (implies pull=True)

    update: update working directory after clone completes, if
    destination is local repository (True means update to default rev,
    anything else is treated as a revision)

    branch: branches to clone

    shareopts: dict of options to control auto sharing behavior. The "pool" key
    activates auto sharing mode and defines the directory for stores. The
    "mode" key determines how to construct the directory name of the shared
    repository. "identity" means the name is derived from the node of the first
    changeset in the repository. "remote" means the name is derived from the
    remote's path/URL. Defaults to "identity."

    storeincludepats and storeexcludepats: sets of file patterns to include and
    exclude in the repository copy, respectively. If not defined, all files
    will be included (a "full" clone). Otherwise a "narrow" clone containing
    only the requested files will be performed. If ``storeincludepats`` is not
    defined but ``storeexcludepats`` is, ``storeincludepats`` is assumed to be
    ``path:.``. If both are empty sets, no files will be cloned.
    """

    if isinstance(source, bytes):
        src = urlutil.get_clone_path(ui, source, branch)
        origsource, source, branches = src
        srcpeer = peer(ui, peeropts, source)
    else:
        srcpeer = source.peer()  # in case we were called with a localrepo
        branches = (None, branch or [])
        origsource = source = srcpeer.url()
    srclock = destlock = cleandir = None
    destpeer = None
    try:
        revs, checkout = addbranchrevs(srcpeer, srcpeer, branches, revs)

        if dest is None:
            dest = defaultdest(source)
            if dest:
                ui.status(_(b"destination directory: %s\n") % dest)
        else:
            dest = urlutil.get_clone_path(ui, dest)[0]

        dest = urlutil.urllocalpath(dest)
        source = urlutil.urllocalpath(source)

        if not dest:
            raise error.InputError(_(b"empty destination path is not valid"))

        destvfs = vfsmod.vfs(dest, expandpath=True)
        if destvfs.lexists():
            if not destvfs.isdir():
                raise error.InputError(
                    _(b"destination '%s' already exists") % dest
                )
            elif destvfs.listdir():
                raise error.InputError(
                    _(b"destination '%s' is not empty") % dest
                )

        createopts = {}
        narrow = False

        if storeincludepats is not None:
            narrowspec.validatepatterns(storeincludepats)
            narrow = True

        if storeexcludepats is not None:
            narrowspec.validatepatterns(storeexcludepats)
            narrow = True

        if narrow:
            # Include everything by default if only exclusion patterns defined.
            if storeexcludepats and not storeincludepats:
                storeincludepats = {b'path:.'}

            createopts[b'narrowfiles'] = True

        if depth:
            createopts[b'shallowfilestore'] = True

        if srcpeer.capable(b'lfs-serve'):
            # Repository creation honors the config if it disabled the extension, so
            # we can't just announce that lfs will be enabled.  This check avoids
            # saying that lfs will be enabled, and then saying it's an unknown
            # feature.  The lfs creation option is set in either case so that a
            # requirement is added.  If the extension is explicitly disabled but the
            # requirement is set, the clone aborts early, before transferring any
            # data.
            createopts[b'lfs'] = True

            if extensions.disabled_help(b'lfs'):
                ui.status(
                    _(
                        b'(remote is using large file support (lfs), but it is '
                        b'explicitly disabled in the local configuration)\n'
                    )
                )
            else:
                ui.status(
                    _(
                        b'(remote is using large file support (lfs); lfs will '
                        b'be enabled for this repository)\n'
                    )
                )

        shareopts = shareopts or {}
        sharepool = shareopts.get(b'pool')
        sharenamemode = shareopts.get(b'mode')
        if sharepool and islocal(dest):
            sharepath = None
            if sharenamemode == b'identity':
                # Resolve the name from the initial changeset in the remote
                # repository. This returns nullid when the remote is empty. It
                # raises RepoLookupError if revision 0 is filtered or otherwise
                # not available. If we fail to resolve, sharing is not enabled.
                try:
                    with srcpeer.commandexecutor() as e:
                        rootnode = e.callcommand(
                            b'lookup',
                            {
                                b'key': b'0',
                            },
                        ).result()

                    if rootnode != sha1nodeconstants.nullid:
                        sharepath = os.path.join(sharepool, hex(rootnode))
                    else:
                        ui.status(
                            _(
                                b'(not using pooled storage: '
                                b'remote appears to be empty)\n'
                            )
                        )
                except error.RepoLookupError:
                    ui.status(
                        _(
                            b'(not using pooled storage: '
                            b'unable to resolve identity of remote)\n'
                        )
                    )
            elif sharenamemode == b'remote':
                sharepath = os.path.join(
                    sharepool, hex(hashutil.sha1(source).digest())
                )
            else:
                raise error.Abort(
                    _(b'unknown share naming mode: %s') % sharenamemode
                )

            # TODO this is a somewhat arbitrary restriction.
            if narrow:
                ui.status(
                    _(b'(pooled storage not supported for narrow clones)\n')
                )
                sharepath = None

            if sharepath:
                return clonewithshare(
                    ui,
                    peeropts,
                    sharepath,
                    source,
                    srcpeer,
                    dest,
                    pull=pull,
                    rev=revs,
                    update=update,
                    stream=stream,
                )

        srcrepo = srcpeer.local()

        abspath = origsource
        if islocal(origsource):
            abspath = util.abspath(urlutil.urllocalpath(origsource))

        if islocal(dest):
            if os.path.exists(dest):
                # only clean up directories we create ourselves
                hgdir = os.path.realpath(os.path.join(dest, b".hg"))
                cleandir = hgdir
            else:
                cleandir = dest

        copy = False
        if (
            srcrepo
            and srcrepo.cancopy()
            and islocal(dest)
            and not phases.hassecret(srcrepo)
        ):
            copy = not pull and not revs

        # TODO this is a somewhat arbitrary restriction.
        if narrow:
            copy = False

        if copy:
            try:
                # we use a lock here because if we race with commit, we
                # can end up with extra data in the cloned revlogs that's
                # not pointed to by changesets, thus causing verify to
                # fail
                srclock = srcrepo.lock(wait=False)
            except error.LockError:
                copy = False

        if copy:
            srcrepo.hook(b'preoutgoing', throw=True, source=b'clone')

            destrootpath = urlutil.urllocalpath(dest)
            dest_reqs = localrepo.clone_requirements(ui, createopts, srcrepo)
            localrepo.createrepository(
                ui,
                destrootpath,
                requirements=dest_reqs,
            )
            destrepo = localrepo.makelocalrepository(ui, destrootpath)
            destlock = destrepo.lock()
            from . import streamclone  # avoid cycle

            streamclone.local_copy(srcrepo, destrepo)

            # we need to re-init the repo after manually copying the data
            # into it
            destpeer = peer(srcrepo, peeropts, dest)
            srcrepo.hook(
                b'outgoing', source=b'clone', node=srcrepo.nodeconstants.nullhex
            )
        else:
            try:
                # only pass ui when no srcrepo
                destpeer = peer(
                    srcrepo or ui,
                    peeropts,
                    dest,
                    create=True,
                    createopts=createopts,
                )
            except OSError as inst:
                if inst.errno == errno.EEXIST:
                    cleandir = None
                    raise error.Abort(
                        _(b"destination '%s' already exists") % dest
                    )
                raise

            if revs:
                if not srcpeer.capable(b'lookup'):
                    raise error.Abort(
                        _(
                            b"src repository does not support "
                            b"revision lookup and so doesn't "
                            b"support clone by revision"
                        )
                    )

                # TODO this is batchable.
                remoterevs = []
                for rev in revs:
                    with srcpeer.commandexecutor() as e:
                        remoterevs.append(
                            e.callcommand(
                                b'lookup',
                                {
                                    b'key': rev,
                                },
                            ).result()
                        )
                revs = remoterevs

                checkout = revs[0]
            else:
                revs = None
            local = destpeer.local()
            if local:
                if narrow:
                    with local.wlock(), local.lock():
                        local.setnarrowpats(storeincludepats, storeexcludepats)
                        narrowspec.copytoworkingcopy(local)

                u = urlutil.url(abspath)
                defaulturl = bytes(u)
                local.ui.setconfig(b'paths', b'default', defaulturl, b'clone')
                if not stream:
                    if pull:
                        stream = False
                    else:
                        stream = None
                # internal config: ui.quietbookmarkmove
                overrides = {(b'ui', b'quietbookmarkmove'): True}
                with local.ui.configoverride(overrides, b'clone'):
                    exchange.pull(
                        local,
                        srcpeer,
                        revs,
                        streamclonerequested=stream,
                        includepats=storeincludepats,
                        excludepats=storeexcludepats,
                        depth=depth,
                    )
            elif srcrepo:
                # TODO lift restriction once exchange.push() accepts narrow
                # push.
                if narrow:
                    raise error.Abort(
                        _(
                            b'narrow clone not available for '
                            b'remote destinations'
                        )
                    )

                exchange.push(
                    srcrepo,
                    destpeer,
                    revs=revs,
                    bookmarks=srcrepo._bookmarks.keys(),
                )
            else:
                raise error.Abort(
                    _(b"clone from remote to remote not supported")
                )

        cleandir = None

        destrepo = destpeer.local()
        if destrepo:
            template = uimod.samplehgrcs[b'cloned']
            u = urlutil.url(abspath)
            u.passwd = None
            defaulturl = bytes(u)
            destrepo.vfs.write(b'hgrc', util.tonativeeol(template % defaulturl))
            destrepo.ui.setconfig(b'paths', b'default', defaulturl, b'clone')

            if ui.configbool(b'experimental', b'remotenames'):
                logexchange.pullremotenames(destrepo, srcpeer)

            if update:
                if update is not True:
                    with srcpeer.commandexecutor() as e:
                        checkout = e.callcommand(
                            b'lookup',
                            {
                                b'key': update,
                            },
                        ).result()

                uprev = None
                status = None
                if checkout is not None:
                    # Some extensions (at least hg-git and hg-subversion) have
                    # a peer.lookup() implementation that returns a name instead
                    # of a nodeid. We work around it here until we've figured
                    # out a better solution.
                    if len(checkout) == 20 and checkout in destrepo:
                        uprev = checkout
                    elif scmutil.isrevsymbol(destrepo, checkout):
                        uprev = scmutil.revsymbol(destrepo, checkout).node()
                    else:
                        if update is not True:
                            try:
                                uprev = destrepo.lookup(update)
                            except error.RepoLookupError:
                                pass
                if uprev is None:
                    try:
                        if destrepo._activebookmark:
                            uprev = destrepo.lookup(destrepo._activebookmark)
                            update = destrepo._activebookmark
                        else:
                            uprev = destrepo._bookmarks[b'@']
                            update = b'@'
                        bn = destrepo[uprev].branch()
                        if bn == b'default':
                            status = _(b"updating to bookmark %s\n" % update)
                        else:
                            status = (
                                _(b"updating to bookmark %s on branch %s\n")
                            ) % (update, bn)
                    except KeyError:
                        try:
                            uprev = destrepo.branchtip(b'default')
                        except error.RepoLookupError:
                            uprev = destrepo.lookup(b'tip')
                if not status:
                    bn = destrepo[uprev].branch()
                    status = _(b"updating to branch %s\n") % bn
                destrepo.ui.status(status)
                _update(destrepo, uprev)
                if update in destrepo._bookmarks:
                    bookmarks.activate(destrepo, update)
            if destlock is not None:
                release(destlock)
            # here is a tiny windows were someone could end up writing the
            # repository before the cache are sure to be warm. This is "fine"
            # as the only "bad" outcome would be some slowness. That potential
            # slowness already affect reader.
            with destrepo.lock():
                destrepo.updatecaches(caches=repositorymod.CACHES_POST_CLONE)
    finally:
        release(srclock, destlock)
        if cleandir is not None:
            shutil.rmtree(cleandir, True)
        if srcpeer is not None:
            srcpeer.close()
        if destpeer and destpeer.local() is None:
            destpeer.close()
    return srcpeer, destpeer


def _showstats(repo, stats, quietempty=False):
    if quietempty and stats.isempty():
        return
    repo.ui.status(
        _(
            b"%d files updated, %d files merged, "
            b"%d files removed, %d files unresolved\n"
        )
        % (
            stats.updatedcount,
            stats.mergedcount,
            stats.removedcount,
            stats.unresolvedcount,
        )
    )


def updaterepo(repo, node, overwrite, updatecheck=None):
    """Update the working directory to node.

    When overwrite is set, changes are clobbered, merged else

    returns stats (see pydoc mercurial.merge.applyupdates)"""
    repo.ui.deprecwarn(
        b'prefer merge.update() or merge.clean_update() over hg.updaterepo()',
        b'5.7',
    )
    return mergemod._update(
        repo,
        node,
        branchmerge=False,
        force=overwrite,
        labels=[b'working copy', b'destination'],
        updatecheck=updatecheck,
    )


def update(repo, node, quietempty=False, updatecheck=None):
    """update the working directory to node"""
    stats = mergemod.update(repo[node], updatecheck=updatecheck)
    _showstats(repo, stats, quietempty)
    if stats.unresolvedcount:
        repo.ui.status(_(b"use 'hg resolve' to retry unresolved file merges\n"))
    return stats.unresolvedcount > 0


# naming conflict in clone()
_update = update


def clean(repo, node, show_stats=True, quietempty=False):
    """forcibly switch the working directory to node, clobbering changes"""
    stats = mergemod.clean_update(repo[node])
    assert stats.unresolvedcount == 0
    if show_stats:
        _showstats(repo, stats, quietempty)
    return False


# naming conflict in updatetotally()
_clean = clean

_VALID_UPDATECHECKS = {
    mergemod.UPDATECHECK_ABORT,
    mergemod.UPDATECHECK_NONE,
    mergemod.UPDATECHECK_LINEAR,
    mergemod.UPDATECHECK_NO_CONFLICT,
}


def updatetotally(ui, repo, checkout, brev, clean=False, updatecheck=None):
    """Update the working directory with extra care for non-file components

    This takes care of non-file components below:

    :bookmark: might be advanced or (in)activated

    This takes arguments below:

    :checkout: to which revision the working directory is updated
    :brev: a name, which might be a bookmark to be activated after updating
    :clean: whether changes in the working directory can be discarded
    :updatecheck: how to deal with a dirty working directory

    Valid values for updatecheck are the UPDATECHECK_* constants
    defined in the merge module. Passing `None` will result in using the
    configured default.

     * ABORT: abort if the working directory is dirty
     * NONE: don't check (merge working directory changes into destination)
     * LINEAR: check that update is linear before merging working directory
               changes into destination
     * NO_CONFLICT: check that the update does not result in file merges

    This returns whether conflict is detected at updating or not.
    """
    if updatecheck is None:
        updatecheck = ui.config(b'commands', b'update.check')
        if updatecheck not in _VALID_UPDATECHECKS:
            # If not configured, or invalid value configured
            updatecheck = mergemod.UPDATECHECK_LINEAR
    if updatecheck not in _VALID_UPDATECHECKS:
        raise ValueError(
            r'Invalid updatecheck value %r (can accept %r)'
            % (updatecheck, _VALID_UPDATECHECKS)
        )
    with repo.wlock():
        movemarkfrom = None
        warndest = False
        if checkout is None:
            updata = destutil.destupdate(repo, clean=clean)
            checkout, movemarkfrom, brev = updata
            warndest = True

        if clean:
            ret = _clean(repo, checkout)
        else:
            if updatecheck == mergemod.UPDATECHECK_ABORT:
                cmdutil.bailifchanged(repo, merge=False)
                updatecheck = mergemod.UPDATECHECK_NONE
            ret = _update(repo, checkout, updatecheck=updatecheck)

        if not ret and movemarkfrom:
            if movemarkfrom == repo[b'.'].node():
                pass  # no-op update
            elif bookmarks.update(repo, [movemarkfrom], repo[b'.'].node()):
                b = ui.label(repo._activebookmark, b'bookmarks.active')
                ui.status(_(b"updating bookmark %s\n") % b)
            else:
                # this can happen with a non-linear update
                b = ui.label(repo._activebookmark, b'bookmarks')
                ui.status(_(b"(leaving bookmark %s)\n") % b)
                bookmarks.deactivate(repo)
        elif brev in repo._bookmarks:
            if brev != repo._activebookmark:
                b = ui.label(brev, b'bookmarks.active')
                ui.status(_(b"(activating bookmark %s)\n") % b)
            bookmarks.activate(repo, brev)
        elif brev:
            if repo._activebookmark:
                b = ui.label(repo._activebookmark, b'bookmarks')
                ui.status(_(b"(leaving bookmark %s)\n") % b)
            bookmarks.deactivate(repo)

        if warndest:
            destutil.statusotherdests(ui, repo)

    return ret


def merge(
    ctx,
    force=False,
    remind=True,
    labels=None,
):
    """Branch merge with node, resolving changes. Return true if any
    unresolved conflicts."""
    repo = ctx.repo()
    stats = mergemod.merge(ctx, force=force, labels=labels)
    _showstats(repo, stats)
    if stats.unresolvedcount:
        repo.ui.status(
            _(
                b"use 'hg resolve' to retry unresolved file merges "
                b"or 'hg merge --abort' to abandon\n"
            )
        )
    elif remind:
        repo.ui.status(_(b"(branch merge, don't forget to commit)\n"))
    return stats.unresolvedcount > 0


def abortmerge(ui, repo):
    ms = mergestatemod.mergestate.read(repo)
    if ms.active():
        # there were conflicts
        node = ms.localctx.hex()
    else:
        # there were no conficts, mergestate was not stored
        node = repo[b'.'].hex()

    repo.ui.status(_(b"aborting the merge, updating back to %s\n") % node[:12])
    stats = mergemod.clean_update(repo[node])
    assert stats.unresolvedcount == 0
    _showstats(repo, stats)


def _incoming(
    displaychlist,
    subreporecurse,
    ui,
    repo,
    source,
    opts,
    buffered=False,
    subpath=None,
):
    """
    Helper for incoming / gincoming.
    displaychlist gets called with
        (remoterepo, incomingchangesetlist, displayer) parameters,
    and is supposed to contain only code that can't be unified.
    """
    srcs = urlutil.get_pull_paths(repo, ui, [source], opts.get(b'branch'))
    srcs = list(srcs)
    if len(srcs) != 1:
        msg = _(b'for now, incoming supports only a single source, %d provided')
        msg %= len(srcs)
        raise error.Abort(msg)
    source, branches = srcs[0]
    if subpath is not None:
        subpath = urlutil.url(subpath)
        if subpath.isabs():
            source = bytes(subpath)
        else:
            p = urlutil.url(source)
            p.path = os.path.normpath(b'%s/%s' % (p.path, subpath))
            source = bytes(p)
    other = peer(repo, opts, source)
    cleanupfn = other.close
    try:
        ui.status(_(b'comparing with %s\n') % urlutil.hidepassword(source))
        revs, checkout = addbranchrevs(repo, other, branches, opts.get(b'rev'))

        if revs:
            revs = [other.lookup(rev) for rev in revs]
        other, chlist, cleanupfn = bundlerepo.getremotechanges(
            ui, repo, other, revs, opts[b"bundle"], opts[b"force"]
        )

        if not chlist:
            ui.status(_(b"no changes found\n"))
            return subreporecurse()
        ui.pager(b'incoming')
        displayer = logcmdutil.changesetdisplayer(
            ui, other, opts, buffered=buffered
        )
        displaychlist(other, chlist, displayer)
        displayer.close()
    finally:
        cleanupfn()
    subreporecurse()
    return 0  # exit code is zero since we found incoming changes


def incoming(ui, repo, source, opts, subpath=None):
    def subreporecurse():
        ret = 1
        if opts.get(b'subrepos'):
            ctx = repo[None]
            for subpath in sorted(ctx.substate):
                sub = ctx.sub(subpath)
                ret = min(ret, sub.incoming(ui, source, opts))
        return ret

    def display(other, chlist, displayer):
        limit = logcmdutil.getlimit(opts)
        if opts.get(b'newest_first'):
            chlist.reverse()
        count = 0
        for n in chlist:
            if limit is not None and count >= limit:
                break
            parents = [
                p for p in other.changelog.parents(n) if p != repo.nullid
            ]
            if opts.get(b'no_merges') and len(parents) == 2:
                continue
            count += 1
            displayer.show(other[n])

    return _incoming(
        display, subreporecurse, ui, repo, source, opts, subpath=subpath
    )


def _outgoing(ui, repo, dests, opts, subpath=None):
    out = set()
    others = []
    for path in urlutil.get_push_paths(repo, ui, dests):
        dest = path.pushloc or path.loc
        if subpath is not None:
            subpath = urlutil.url(subpath)
            if subpath.isabs():
                dest = bytes(subpath)
            else:
                p = urlutil.url(dest)
                p.path = os.path.normpath(b'%s/%s' % (p.path, subpath))
                dest = bytes(p)
        branches = path.branch, opts.get(b'branch') or []

        ui.status(_(b'comparing with %s\n') % urlutil.hidepassword(dest))
        revs, checkout = addbranchrevs(repo, repo, branches, opts.get(b'rev'))
        if revs:
            revs = [repo[rev].node() for rev in scmutil.revrange(repo, revs)]

        other = peer(repo, opts, dest)
        try:
            outgoing = discovery.findcommonoutgoing(
                repo, other, revs, force=opts.get(b'force')
            )
            o = outgoing.missing
            out.update(o)
            if not o:
                scmutil.nochangesfound(repo.ui, repo, outgoing.excluded)
            others.append(other)
        except:  # re-raises
            other.close()
            raise
    # make sure this is ordered by revision number
    outgoing_revs = list(out)
    cl = repo.changelog
    outgoing_revs.sort(key=cl.rev)
    return outgoing_revs, others


def _outgoing_recurse(ui, repo, dests, opts):
    ret = 1
    if opts.get(b'subrepos'):
        ctx = repo[None]
        for subpath in sorted(ctx.substate):
            sub = ctx.sub(subpath)
            ret = min(ret, sub.outgoing(ui, dests, opts))
    return ret


def _outgoing_filter(repo, revs, opts):
    """apply revision filtering/ordering option for outgoing"""
    limit = logcmdutil.getlimit(opts)
    no_merges = opts.get(b'no_merges')
    if opts.get(b'newest_first'):
        revs.reverse()
    if limit is None and not no_merges:
        for r in revs:
            yield r
        return

    count = 0
    cl = repo.changelog
    for n in revs:
        if limit is not None and count >= limit:
            break
        parents = [p for p in cl.parents(n) if p != repo.nullid]
        if no_merges and len(parents) == 2:
            continue
        count += 1
        yield n


def outgoing(ui, repo, dests, opts, subpath=None):
    if opts.get(b'graph'):
        logcmdutil.checkunsupportedgraphflags([], opts)
    o, others = _outgoing(ui, repo, dests, opts, subpath=subpath)
    ret = 1
    try:
        if o:
            ret = 0

            if opts.get(b'graph'):
                revdag = logcmdutil.graphrevs(repo, o, opts)
                ui.pager(b'outgoing')
                displayer = logcmdutil.changesetdisplayer(
                    ui, repo, opts, buffered=True
                )
                logcmdutil.displaygraph(
                    ui, repo, revdag, displayer, graphmod.asciiedges
                )
            else:
                ui.pager(b'outgoing')
                displayer = logcmdutil.changesetdisplayer(ui, repo, opts)
                for n in _outgoing_filter(repo, o, opts):
                    displayer.show(repo[n])
                displayer.close()
        for oth in others:
            cmdutil.outgoinghooks(ui, repo, oth, opts, o)
            ret = min(ret, _outgoing_recurse(ui, repo, dests, opts))
        return ret  # exit code is zero since we found outgoing changes
    finally:
        for oth in others:
            oth.close()


def verify(repo, level=None):
    """verify the consistency of a repository"""
    ret = verifymod.verify(repo, level=level)

    # Broken subrepo references in hidden csets don't seem worth worrying about,
    # since they can't be pushed/pulled, and --hidden can be used if they are a
    # concern.

    # pathto() is needed for -R case
    revs = repo.revs(
        b"filelog(%s)", util.pathto(repo.root, repo.getcwd(), b'.hgsubstate')
    )

    if revs:
        repo.ui.status(_(b'checking subrepo links\n'))
        for rev in revs:
            ctx = repo[rev]
            try:
                for subpath in ctx.substate:
                    try:
                        ret = (
                            ctx.sub(subpath, allowcreate=False).verify() or ret
                        )
                    except error.RepoError as e:
                        repo.ui.warn(b'%d: %s\n' % (rev, e))
            except Exception:
                repo.ui.warn(
                    _(b'.hgsubstate is corrupt in revision %s\n')
                    % short(ctx.node())
                )

    return ret


def remoteui(src, opts):
    """build a remote ui from ui or repo and opts"""
    if util.safehasattr(src, b'baseui'):  # looks like a repository
        dst = src.baseui.copy()  # drop repo-specific config
        src = src.ui  # copy target options from repo
    else:  # assume it's a global ui object
        dst = src.copy()  # keep all global options

    # copy ssh-specific options
    for o in b'ssh', b'remotecmd':
        v = opts.get(o) or src.config(b'ui', o)
        if v:
            dst.setconfig(b"ui", o, v, b'copied')

    # copy bundle-specific options
    r = src.config(b'bundle', b'mainreporoot')
    if r:
        dst.setconfig(b'bundle', b'mainreporoot', r, b'copied')

    # copy selected local settings to the remote ui
    for sect in (b'auth', b'hostfingerprints', b'hostsecurity', b'http_proxy'):
        for key, val in src.configitems(sect):
            dst.setconfig(sect, key, val, b'copied')
    v = src.config(b'web', b'cacerts')
    if v:
        dst.setconfig(b'web', b'cacerts', util.expandpath(v), b'copied')

    return dst


# Files of interest
# Used to check if the repository has changed looking at mtime and size of
# these files.
foi = [
    (b'spath', b'00changelog.i'),
    (b'spath', b'phaseroots'),  # ! phase can change content at the same size
    (b'spath', b'obsstore'),
    (b'path', b'bookmarks'),  # ! bookmark can change content at the same size
]


class cachedlocalrepo(object):
    """Holds a localrepository that can be cached and reused."""

    def __init__(self, repo):
        """Create a new cached repo from an existing repo.

        We assume the passed in repo was recently created. If the
        repo has changed between when it was created and when it was
        turned into a cache, it may not refresh properly.
        """
        assert isinstance(repo, localrepo.localrepository)
        self._repo = repo
        self._state, self.mtime = self._repostate()
        self._filtername = repo.filtername

    def fetch(self):
        """Refresh (if necessary) and return a repository.

        If the cached instance is out of date, it will be recreated
        automatically and returned.

        Returns a tuple of the repo and a boolean indicating whether a new
        repo instance was created.
        """
        # We compare the mtimes and sizes of some well-known files to
        # determine if the repo changed. This is not precise, as mtimes
        # are susceptible to clock skew and imprecise filesystems and
        # file content can change while maintaining the same size.

        state, mtime = self._repostate()
        if state == self._state:
            return self._repo, False

        repo = repository(self._repo.baseui, self._repo.url())
        if self._filtername:
            self._repo = repo.filtered(self._filtername)
        else:
            self._repo = repo.unfiltered()
        self._state = state
        self.mtime = mtime

        return self._repo, True

    def _repostate(self):
        state = []
        maxmtime = -1
        for attr, fname in foi:
            prefix = getattr(self._repo, attr)
            p = os.path.join(prefix, fname)
            try:
                st = os.stat(p)
            except OSError:
                st = os.stat(prefix)
            state.append((st[stat.ST_MTIME], st.st_size))
            maxmtime = max(maxmtime, st[stat.ST_MTIME])

        return tuple(state), maxmtime

    def copy(self):
        """Obtain a copy of this class instance.

        A new localrepository instance is obtained. The new instance should be
        completely independent of the original.
        """
        repo = repository(self._repo.baseui, self._repo.origroot)
        if self._filtername:
            repo = repo.filtered(self._filtername)
        else:
            repo = repo.unfiltered()
        c = cachedlocalrepo(repo)
        c._state = self._state
        c.mtime = self.mtime
        return c
