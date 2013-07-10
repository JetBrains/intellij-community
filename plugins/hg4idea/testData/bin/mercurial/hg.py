# hg.py - repository classes for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
from lock import release
from node import hex, nullid
import localrepo, bundlerepo, unionrepo, httppeer, sshpeer, statichttprepo
import bookmarks, lock, util, extensions, error, node, scmutil, phases, url
import cmdutil, discovery
import merge as mergemod
import verify as verifymod
import errno, os, shutil

def _local(path):
    path = util.expandpath(util.urllocalpath(path))
    return (os.path.isfile(path) and bundlerepo or localrepo)

def addbranchrevs(lrepo, other, branches, revs):
    peer = other.peer() # a courtesy to callers using a localrepo for other
    hashbranch, branches = branches
    if not hashbranch and not branches:
        return revs or None, revs and revs[0] or None
    revs = revs and list(revs) or []
    if not peer.capable('branchmap'):
        if branches:
            raise util.Abort(_("remote branch lookup not supported"))
        revs.append(hashbranch)
        return revs, revs[0]
    branchmap = peer.branchmap()

    def primary(branch):
        if branch == '.':
            if not lrepo:
                raise util.Abort(_("dirstate branch not accessible"))
            branch = lrepo.dirstate.branch()
        if branch in branchmap:
            revs.extend(node.hex(r) for r in reversed(branchmap[branch]))
            return True
        else:
            return False

    for branch in branches:
        if not primary(branch):
            raise error.RepoLookupError(_("unknown branch '%s'") % branch)
    if hashbranch:
        if not primary(hashbranch):
            revs.append(hashbranch)
    return revs, revs[0]

def parseurl(path, branches=None):
    '''parse url#branch, returning (url, (branch, branches))'''

    u = util.url(path)
    branch = None
    if u.fragment:
        branch = u.fragment
        u.fragment = None
    return str(u), (branch, branches or [])

schemes = {
    'bundle': bundlerepo,
    'union': unionrepo,
    'file': _local,
    'http': httppeer,
    'https': httppeer,
    'ssh': sshpeer,
    'static-http': statichttprepo,
}

def _peerlookup(path):
    u = util.url(path)
    scheme = u.scheme or 'file'
    thing = schemes.get(scheme) or schemes['file']
    try:
        return thing(path)
    except TypeError:
        return thing

def islocal(repo):
    '''return true if repo or path is local'''
    if isinstance(repo, str):
        try:
            return _peerlookup(repo).islocal(repo)
        except AttributeError:
            return False
    return repo.local()

def openpath(ui, path):
    '''open path with open if local, url.open if remote'''
    if islocal(path):
        return util.posixfile(util.urllocalpath(path), 'rb')
    else:
        return url.open(ui, path)

def _peerorrepo(ui, path, create=False):
    """return a repository object for the specified path"""
    obj = _peerlookup(path).instance(ui, path, create)
    ui = getattr(obj, "ui", ui)
    for name, module in extensions.extensions():
        hook = getattr(module, 'reposetup', None)
        if hook:
            hook(ui, obj)
    return obj

def repository(ui, path='', create=False):
    """return a repository object for the specified path"""
    peer = _peerorrepo(ui, path, create)
    repo = peer.local()
    if not repo:
        raise util.Abort(_("repository '%s' is not local") %
                         (path or peer.url()))
    return repo.filtered('visible')

def peer(uiorrepo, opts, path, create=False):
    '''return a repository peer for the specified path'''
    rui = remoteui(uiorrepo, opts)
    return _peerorrepo(rui, path, create).peer()

def defaultdest(source):
    '''return default destination of clone if none is given'''
    return os.path.basename(os.path.normpath(util.url(source).path or ''))

def share(ui, source, dest=None, update=True):
    '''create a shared repository'''

    if not islocal(source):
        raise util.Abort(_('can only share local repositories'))

    if not dest:
        dest = defaultdest(source)
    else:
        dest = ui.expandpath(dest)

    if isinstance(source, str):
        origsource = ui.expandpath(source)
        source, branches = parseurl(origsource)
        srcrepo = repository(ui, source)
        rev, checkout = addbranchrevs(srcrepo, srcrepo, branches, None)
    else:
        srcrepo = source.local()
        origsource = source = srcrepo.url()
        checkout = None

    sharedpath = srcrepo.sharedpath # if our source is already sharing

    root = os.path.realpath(dest)
    roothg = os.path.join(root, '.hg')

    if os.path.exists(roothg):
        raise util.Abort(_('destination already exists'))

    if not os.path.isdir(root):
        os.mkdir(root)
    util.makedir(roothg, notindexed=True)

    requirements = ''
    try:
        requirements = srcrepo.opener.read('requires')
    except IOError, inst:
        if inst.errno != errno.ENOENT:
            raise

    requirements += 'shared\n'
    util.writefile(os.path.join(roothg, 'requires'), requirements)
    util.writefile(os.path.join(roothg, 'sharedpath'), sharedpath)

    r = repository(ui, root)

    default = srcrepo.ui.config('paths', 'default')
    if default:
        fp = r.opener("hgrc", "w", text=True)
        fp.write("[paths]\n")
        fp.write("default = %s\n" % default)
        fp.close()

    if update:
        r.ui.status(_("updating working directory\n"))
        if update is not True:
            checkout = update
        for test in (checkout, 'default', 'tip'):
            if test is None:
                continue
            try:
                uprev = r.lookup(test)
                break
            except error.RepoLookupError:
                continue
        _update(r, uprev)

def copystore(ui, srcrepo, destpath):
    '''copy files from store of srcrepo in destpath

    returns destlock
    '''
    destlock = None
    try:
        hardlink = None
        num = 0
        srcpublishing = srcrepo.ui.configbool('phases', 'publish', True)
        for f in srcrepo.store.copylist():
            if srcpublishing and f.endswith('phaseroots'):
                continue
            src = os.path.join(srcrepo.sharedpath, f)
            dst = os.path.join(destpath, f)
            dstbase = os.path.dirname(dst)
            if dstbase and not os.path.exists(dstbase):
                os.mkdir(dstbase)
            if os.path.exists(src):
                if dst.endswith('data'):
                    # lock to avoid premature writing to the target
                    destlock = lock.lock(os.path.join(dstbase, "lock"))
                hardlink, n = util.copyfiles(src, dst, hardlink)
                num += n
        if hardlink:
            ui.debug("linked %d files\n" % num)
        else:
            ui.debug("copied %d files\n" % num)
        return destlock
    except: # re-raises
        release(destlock)
        raise

def clone(ui, peeropts, source, dest=None, pull=False, rev=None,
          update=True, stream=False, branch=None):
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

    pull: always pull from source repository, even in local case

    stream: stream raw data uncompressed from repository (fast over
    LAN, slow over WAN)

    rev: revision to clone up to (implies pull=True)

    update: update working directory after clone completes, if
    destination is local repository (True means update to default rev,
    anything else is treated as a revision)

    branch: branches to clone
    """

    if isinstance(source, str):
        origsource = ui.expandpath(source)
        source, branch = parseurl(origsource, branch)
        srcpeer = peer(ui, peeropts, source)
    else:
        srcpeer = source.peer() # in case we were called with a localrepo
        branch = (None, branch or [])
        origsource = source = srcpeer.url()
    rev, checkout = addbranchrevs(srcpeer, srcpeer, branch, rev)

    if dest is None:
        dest = defaultdest(source)
        ui.status(_("destination directory: %s\n") % dest)
    else:
        dest = ui.expandpath(dest)

    dest = util.urllocalpath(dest)
    source = util.urllocalpath(source)

    if not dest:
        raise util.Abort(_("empty destination path is not valid"))
    if os.path.exists(dest):
        if not os.path.isdir(dest):
            raise util.Abort(_("destination '%s' already exists") % dest)
        elif os.listdir(dest):
            raise util.Abort(_("destination '%s' is not empty") % dest)

    srclock = destlock = cleandir = None
    srcrepo = srcpeer.local()
    try:
        abspath = origsource
        if islocal(origsource):
            abspath = os.path.abspath(util.urllocalpath(origsource))

        if islocal(dest):
            cleandir = dest

        copy = False
        if (srcrepo and srcrepo.cancopy() and islocal(dest)
            and not phases.hassecret(srcrepo)):
            copy = not pull and not rev

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
            srcrepo.hook('preoutgoing', throw=True, source='clone')
            hgdir = os.path.realpath(os.path.join(dest, ".hg"))
            if not os.path.exists(dest):
                os.mkdir(dest)
            else:
                # only clean up directories we create ourselves
                cleandir = hgdir
            try:
                destpath = hgdir
                util.makedir(destpath, notindexed=True)
            except OSError, inst:
                if inst.errno == errno.EEXIST:
                    cleandir = None
                    raise util.Abort(_("destination '%s' already exists")
                                     % dest)
                raise

            destlock = copystore(ui, srcrepo, destpath)

            # Recomputing branch cache might be slow on big repos,
            # so just copy it
            dstcachedir = os.path.join(destpath, 'cache')
            srcbranchcache = srcrepo.sjoin('cache/branchheads')
            dstbranchcache = os.path.join(dstcachedir, 'branchheads')
            if os.path.exists(srcbranchcache):
                if not os.path.exists(dstcachedir):
                    os.mkdir(dstcachedir)
                util.copyfile(srcbranchcache, dstbranchcache)

            # we need to re-init the repo after manually copying the data
            # into it
            destpeer = peer(srcrepo, peeropts, dest)
            srcrepo.hook('outgoing', source='clone',
                          node=node.hex(node.nullid))
        else:
            try:
                destpeer = peer(srcrepo or ui, peeropts, dest, create=True)
                                # only pass ui when no srcrepo
            except OSError, inst:
                if inst.errno == errno.EEXIST:
                    cleandir = None
                    raise util.Abort(_("destination '%s' already exists")
                                     % dest)
                raise

            revs = None
            if rev:
                if not srcpeer.capable('lookup'):
                    raise util.Abort(_("src repository does not support "
                                       "revision lookup and so doesn't "
                                       "support clone by revision"))
                revs = [srcpeer.lookup(r) for r in rev]
                checkout = revs[0]
            if destpeer.local():
                destpeer.local().clone(srcpeer, heads=revs, stream=stream)
            elif srcrepo:
                srcrepo.push(destpeer, revs=revs)
            else:
                raise util.Abort(_("clone from remote to remote not supported"))

        cleandir = None

        # clone all bookmarks except divergent ones
        destrepo = destpeer.local()
        if destrepo and srcpeer.capable("pushkey"):
            rb = srcpeer.listkeys('bookmarks')
            marks = destrepo._bookmarks
            for k, n in rb.iteritems():
                try:
                    m = destrepo.lookup(n)
                    marks[k] = m
                except error.RepoLookupError:
                    pass
            if rb:
                marks.write()
        elif srcrepo and destpeer.capable("pushkey"):
            for k, n in srcrepo._bookmarks.iteritems():
                destpeer.pushkey('bookmarks', k, '', hex(n))

        if destrepo:
            fp = destrepo.opener("hgrc", "w", text=True)
            fp.write("[paths]\n")
            u = util.url(abspath)
            u.passwd = None
            defaulturl = str(u)
            fp.write("default = %s\n" % defaulturl)
            fp.close()

            destrepo.ui.setconfig('paths', 'default', defaulturl)

            if update:
                if update is not True:
                    checkout = srcpeer.lookup(update)
                uprev = None
                status = None
                if checkout is not None:
                    try:
                        uprev = destrepo.lookup(checkout)
                    except error.RepoLookupError:
                        pass
                if uprev is None:
                    try:
                        uprev = destrepo._bookmarks['@']
                        update = '@'
                        bn = destrepo[uprev].branch()
                        if bn == 'default':
                            status = _("updating to bookmark @\n")
                        else:
                            status = _("updating to bookmark @ on branch %s\n"
                                       % bn)
                    except KeyError:
                        try:
                            uprev = destrepo.branchtip('default')
                        except error.RepoLookupError:
                            uprev = destrepo.lookup('tip')
                if not status:
                    bn = destrepo[uprev].branch()
                    status = _("updating to branch %s\n") % bn
                destrepo.ui.status(status)
                _update(destrepo, uprev)
                if update in destrepo._bookmarks:
                    bookmarks.setcurrent(destrepo, update)

        return srcpeer, destpeer
    finally:
        release(srclock, destlock)
        if cleandir is not None:
            shutil.rmtree(cleandir, True)
        if srcpeer is not None:
            srcpeer.close()

def _showstats(repo, stats):
    repo.ui.status(_("%d files updated, %d files merged, "
                     "%d files removed, %d files unresolved\n") % stats)

def updaterepo(repo, node, overwrite):
    """Update the working directory to node.

    When overwrite is set, changes are clobbered, merged else

    returns stats (see pydoc mercurial.merge.applyupdates)"""
    return mergemod.update(repo, node, False, overwrite, None)

def update(repo, node):
    """update the working directory to node, merging linear changes"""
    stats = updaterepo(repo, node, False)
    _showstats(repo, stats)
    if stats[3]:
        repo.ui.status(_("use 'hg resolve' to retry unresolved file merges\n"))
    return stats[3] > 0

# naming conflict in clone()
_update = update

def clean(repo, node, show_stats=True):
    """forcibly switch the working directory to node, clobbering changes"""
    stats = updaterepo(repo, node, True)
    if show_stats:
        _showstats(repo, stats)
    return stats[3] > 0

def merge(repo, node, force=None, remind=True):
    """Branch merge with node, resolving changes. Return true if any
    unresolved conflicts."""
    stats = mergemod.update(repo, node, True, force, False)
    _showstats(repo, stats)
    if stats[3]:
        repo.ui.status(_("use 'hg resolve' to retry unresolved file merges "
                         "or 'hg update -C .' to abandon\n"))
    elif remind:
        repo.ui.status(_("(branch merge, don't forget to commit)\n"))
    return stats[3] > 0

def _incoming(displaychlist, subreporecurse, ui, repo, source,
        opts, buffered=False):
    """
    Helper for incoming / gincoming.
    displaychlist gets called with
        (remoterepo, incomingchangesetlist, displayer) parameters,
    and is supposed to contain only code that can't be unified.
    """
    source, branches = parseurl(ui.expandpath(source), opts.get('branch'))
    other = peer(repo, opts, source)
    ui.status(_('comparing with %s\n') % util.hidepassword(source))
    revs, checkout = addbranchrevs(repo, other, branches, opts.get('rev'))

    if revs:
        revs = [other.lookup(rev) for rev in revs]
    other, chlist, cleanupfn = bundlerepo.getremotechanges(ui, repo, other,
                                revs, opts["bundle"], opts["force"])
    try:
        if not chlist:
            ui.status(_("no changes found\n"))
            return subreporecurse()

        displayer = cmdutil.show_changeset(ui, other, opts, buffered)

        # XXX once graphlog extension makes it into core,
        # should be replaced by a if graph/else
        displaychlist(other, chlist, displayer)

        displayer.close()
    finally:
        cleanupfn()
    subreporecurse()
    return 0 # exit code is zero since we found incoming changes

def incoming(ui, repo, source, opts):
    def subreporecurse():
        ret = 1
        if opts.get('subrepos'):
            ctx = repo[None]
            for subpath in sorted(ctx.substate):
                sub = ctx.sub(subpath)
                ret = min(ret, sub.incoming(ui, source, opts))
        return ret

    def display(other, chlist, displayer):
        limit = cmdutil.loglimit(opts)
        if opts.get('newest_first'):
            chlist.reverse()
        count = 0
        for n in chlist:
            if limit is not None and count >= limit:
                break
            parents = [p for p in other.changelog.parents(n) if p != nullid]
            if opts.get('no_merges') and len(parents) == 2:
                continue
            count += 1
            displayer.show(other[n])
    return _incoming(display, subreporecurse, ui, repo, source, opts)

def _outgoing(ui, repo, dest, opts):
    dest = ui.expandpath(dest or 'default-push', dest or 'default')
    dest, branches = parseurl(dest, opts.get('branch'))
    ui.status(_('comparing with %s\n') % util.hidepassword(dest))
    revs, checkout = addbranchrevs(repo, repo, branches, opts.get('rev'))
    if revs:
        revs = [repo.lookup(rev) for rev in scmutil.revrange(repo, revs)]

    other = peer(repo, opts, dest)
    outgoing = discovery.findcommonoutgoing(repo.unfiltered(), other, revs,
                                            force=opts.get('force'))
    o = outgoing.missing
    if not o:
        scmutil.nochangesfound(repo.ui, repo, outgoing.excluded)
        return None
    return o

def outgoing(ui, repo, dest, opts):
    def recurse():
        ret = 1
        if opts.get('subrepos'):
            ctx = repo[None]
            for subpath in sorted(ctx.substate):
                sub = ctx.sub(subpath)
                ret = min(ret, sub.outgoing(ui, dest, opts))
        return ret

    limit = cmdutil.loglimit(opts)
    o = _outgoing(ui, repo, dest, opts)
    if o is None:
        return recurse()

    if opts.get('newest_first'):
        o.reverse()
    displayer = cmdutil.show_changeset(ui, repo, opts)
    count = 0
    for n in o:
        if limit is not None and count >= limit:
            break
        parents = [p for p in repo.changelog.parents(n) if p != nullid]
        if opts.get('no_merges') and len(parents) == 2:
            continue
        count += 1
        displayer.show(repo[n])
    displayer.close()
    recurse()
    return 0 # exit code is zero since we found outgoing changes

def revert(repo, node, choose):
    """revert changes to revision in node without updating dirstate"""
    return mergemod.update(repo, node, False, True, choose)[3] > 0

def verify(repo):
    """verify the consistency of a repository"""
    return verifymod.verify(repo)

def remoteui(src, opts):
    'build a remote ui from ui or repo and opts'
    if util.safehasattr(src, 'baseui'): # looks like a repository
        dst = src.baseui.copy() # drop repo-specific config
        src = src.ui # copy target options from repo
    else: # assume it's a global ui object
        dst = src.copy() # keep all global options

    # copy ssh-specific options
    for o in 'ssh', 'remotecmd':
        v = opts.get(o) or src.config('ui', o)
        if v:
            dst.setconfig("ui", o, v)

    # copy bundle-specific options
    r = src.config('bundle', 'mainreporoot')
    if r:
        dst.setconfig('bundle', 'mainreporoot', r)

    # copy selected local settings to the remote ui
    for sect in ('auth', 'hostfingerprints', 'http_proxy'):
        for key, val in src.configitems(sect):
            dst.setconfig(sect, key, val)
    v = src.config('web', 'cacerts')
    if v:
        dst.setconfig('web', 'cacerts', util.expandpath(v))

    return dst
