# Copyright 2009-2010 Gregory P. Ward
# Copyright 2009-2010 Intelerad Medical Systems Incorporated
# Copyright 2010-2011 Fog Creek Software
# Copyright 2010-2011 Unity Technologies
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''largefiles utility code: must not import other modules in this package.'''

import contextlib
import copy
import os
import stat

from mercurial.i18n import _
from mercurial.node import hex
from mercurial.pycompat import open

from mercurial import (
    dirstate,
    encoding,
    error,
    httpconnection,
    match as matchmod,
    pycompat,
    requirements,
    scmutil,
    sparse,
    util,
    vfs as vfsmod,
)
from mercurial.utils import hashutil
from mercurial.dirstateutils import timestamp

shortname = b'.hglf'
shortnameslash = shortname + b'/'
longname = b'largefiles'

# -- Private worker functions ------------------------------------------


@contextlib.contextmanager
def lfstatus(repo, value=True):
    oldvalue = getattr(repo, 'lfstatus', False)
    repo.lfstatus = value
    try:
        yield
    finally:
        repo.lfstatus = oldvalue


def getminsize(ui, assumelfiles, opt, default=10):
    lfsize = opt
    if not lfsize and assumelfiles:
        lfsize = ui.config(longname, b'minsize', default=default)
    if lfsize:
        try:
            lfsize = float(lfsize)
        except ValueError:
            raise error.Abort(
                _(b'largefiles: size must be number (not %s)\n') % lfsize
            )
    if lfsize is None:
        raise error.Abort(_(b'minimum size for largefiles must be specified'))
    return lfsize


def link(src, dest):
    """Try to create hardlink - if that fails, efficiently make a copy."""
    util.makedirs(os.path.dirname(dest))
    try:
        util.oslink(src, dest)
    except OSError:
        # if hardlinks fail, fallback on atomic copy
        with open(src, b'rb') as srcf, util.atomictempfile(dest) as dstf:
            for chunk in util.filechunkiter(srcf):
                dstf.write(chunk)
        os.chmod(dest, os.stat(src).st_mode)


def usercachepath(ui, hash):
    """Return the correct location in the "global" largefiles cache for a file
    with the given hash.
    This cache is used for sharing of largefiles across repositories - both
    to preserve download bandwidth and storage space."""
    return os.path.join(_usercachedir(ui), hash)


def _usercachedir(ui, name=longname):
    '''Return the location of the "global" largefiles cache.'''
    path = ui.configpath(name, b'usercache')
    if path:
        return path

    hint = None

    if pycompat.iswindows:
        appdata = encoding.environ.get(
            b'LOCALAPPDATA', encoding.environ.get(b'APPDATA')
        )
        if appdata:
            return os.path.join(appdata, name)

        hint = _(b"define %s or %s in the environment, or set %s.usercache") % (
            b"LOCALAPPDATA",
            b"APPDATA",
            name,
        )
    elif pycompat.isdarwin:
        home = encoding.environ.get(b'HOME')
        if home:
            return os.path.join(home, b'Library', b'Caches', name)

        hint = _(b"define %s in the environment, or set %s.usercache") % (
            b"HOME",
            name,
        )
    elif pycompat.isposix:
        path = encoding.environ.get(b'XDG_CACHE_HOME')
        if path:
            return os.path.join(path, name)
        home = encoding.environ.get(b'HOME')
        if home:
            return os.path.join(home, b'.cache', name)

        hint = _(b"define %s or %s in the environment, or set %s.usercache") % (
            b"XDG_CACHE_HOME",
            b"HOME",
            name,
        )
    else:
        raise error.Abort(
            _(b'unknown operating system: %s\n') % pycompat.osname
        )

    raise error.Abort(_(b'unknown %s usercache location') % name, hint=hint)


def inusercache(ui, hash):
    path = usercachepath(ui, hash)
    return os.path.exists(path)


def findfile(repo, hash):
    """Return store path of the largefile with the specified hash.
    As a side effect, the file might be linked from user cache.
    Return None if the file can't be found locally."""
    path, exists = findstorepath(repo, hash)
    if exists:
        repo.ui.note(_(b'found %s in store\n') % hash)
        return path
    elif inusercache(repo.ui, hash):
        repo.ui.note(_(b'found %s in system cache\n') % hash)
        path = storepath(repo, hash)
        link(usercachepath(repo.ui, hash), path)
        return path
    return None


class largefilesdirstate(dirstate.dirstate):
    _large_file_dirstate = True
    _tr_key_suffix = b'-large-files'

    def __getitem__(self, key):
        return super(largefilesdirstate, self).__getitem__(unixpath(key))

    def set_tracked(self, f):
        return super(largefilesdirstate, self).set_tracked(unixpath(f))

    def set_untracked(self, f):
        return super(largefilesdirstate, self).set_untracked(unixpath(f))

    def normal(self, f, parentfiledata=None):
        # not sure if we should pass the `parentfiledata` down or throw it
        # away. So throwing it away to stay on the safe side.
        return super(largefilesdirstate, self).normal(unixpath(f))

    def remove(self, f):
        return super(largefilesdirstate, self).remove(unixpath(f))

    def add(self, f):
        return super(largefilesdirstate, self).add(unixpath(f))

    def drop(self, f):
        return super(largefilesdirstate, self).drop(unixpath(f))

    def forget(self, f):
        return super(largefilesdirstate, self).forget(unixpath(f))

    def normallookup(self, f):
        return super(largefilesdirstate, self).normallookup(unixpath(f))

    def _ignore(self, f):
        return False

    def write(self, tr):
        # (1) disable PENDING mode always
        #     (lfdirstate isn't yet managed as a part of the transaction)
        # (2) avoid develwarn 'use dirstate.write with ....'
        if tr:
            tr.addbackup(b'largefiles/dirstate', location=b'plain')
        super(largefilesdirstate, self).write(None)


def openlfdirstate(ui, repo, create=True):
    """
    Return a dirstate object that tracks largefiles: i.e. its root is
    the repo root, but it is saved in .hg/largefiles/dirstate.

    If a dirstate object already exists and is being used for a 'changing_*'
    context, it will be returned.
    """
    sub_dirstate = getattr(repo.dirstate, '_sub_dirstate', None)
    if sub_dirstate is not None:
        return sub_dirstate
    vfs = repo.vfs
    lfstoredir = longname
    opener = vfsmod.vfs(vfs.join(lfstoredir))
    use_dirstate_v2 = requirements.DIRSTATE_V2_REQUIREMENT in repo.requirements
    lfdirstate = largefilesdirstate(
        opener,
        ui,
        repo.root,
        repo.dirstate._validate,
        lambda: sparse.matcher(repo),
        repo.nodeconstants,
        use_dirstate_v2,
    )

    # If the largefiles dirstate does not exist, populate and create
    # it. This ensures that we create it on the first meaningful
    # largefiles operation in a new clone.
    if create and not vfs.exists(vfs.join(lfstoredir, b'dirstate')):
        try:
            with repo.wlock(wait=False), lfdirstate.changing_files(repo):
                matcher = getstandinmatcher(repo)
                standins = repo.dirstate.walk(
                    matcher, subrepos=[], unknown=False, ignored=False
                )

                if len(standins) > 0:
                    vfs.makedirs(lfstoredir)

                for standin in standins:
                    lfile = splitstandin(standin)
                    lfdirstate.hacky_extension_update_file(
                        lfile,
                        p1_tracked=True,
                        wc_tracked=True,
                        possibly_dirty=True,
                    )
        except error.LockError:
            # Assume that whatever was holding the lock was important.
            # If we were doing something important, we would already have
            # either the lock or a largefile dirstate.
            pass
    return lfdirstate


def lfdirstatestatus(lfdirstate, repo):
    pctx = repo[b'.']
    match = matchmod.always()
    unsure, s, mtime_boundary = lfdirstate.status(
        match, subrepos=[], ignored=False, clean=False, unknown=False
    )
    modified, clean = s.modified, s.clean
    wctx = repo[None]
    for lfile in unsure:
        try:
            fctx = pctx[standin(lfile)]
        except LookupError:
            fctx = None
        if not fctx or readasstandin(fctx) != hashfile(repo.wjoin(lfile)):
            modified.append(lfile)
        else:
            clean.append(lfile)
            st = wctx[lfile].lstat()
            mode = st.st_mode
            size = st.st_size
            mtime = timestamp.reliable_mtime_of(st, mtime_boundary)
            if mtime is not None:
                cache_data = (mode, size, mtime)
                lfdirstate.set_clean(lfile, cache_data)
    return s


def listlfiles(repo, rev=None, matcher=None):
    """return a list of largefiles in the working copy or the
    specified changeset"""

    if matcher is None:
        matcher = getstandinmatcher(repo)

    # ignore unknown files in working directory
    return [
        splitstandin(f)
        for f in repo[rev].walk(matcher)
        if rev is not None or repo.dirstate.get_entry(f).any_tracked
    ]


def instore(repo, hash, forcelocal=False):
    '''Return true if a largefile with the given hash exists in the store'''
    return os.path.exists(storepath(repo, hash, forcelocal))


def storepath(repo, hash, forcelocal=False):
    """Return the correct location in the repository largefiles store for a
    file with the given hash."""
    if not forcelocal and repo.shared():
        return repo.vfs.reljoin(repo.sharedpath, longname, hash)
    return repo.vfs.join(longname, hash)


def findstorepath(repo, hash):
    """Search through the local store path(s) to find the file for the given
    hash.  If the file is not found, its path in the primary store is returned.
    The return value is a tuple of (path, exists(path)).
    """
    # For shared repos, the primary store is in the share source.  But for
    # backward compatibility, force a lookup in the local store if it wasn't
    # found in the share source.
    path = storepath(repo, hash, False)

    if instore(repo, hash):
        return (path, True)
    elif repo.shared() and instore(repo, hash, True):
        return storepath(repo, hash, True), True

    return (path, False)


def copyfromcache(repo, hash, filename):
    """Copy the specified largefile from the repo or system cache to
    filename in the repository. Return true on success or false if the
    file was not found in either cache (which should not happened:
    this is meant to be called only after ensuring that the needed
    largefile exists in the cache)."""
    wvfs = repo.wvfs
    path = findfile(repo, hash)
    if path is None:
        return False
    wvfs.makedirs(wvfs.dirname(wvfs.join(filename)))
    # The write may fail before the file is fully written, but we
    # don't use atomic writes in the working copy.
    with open(path, b'rb') as srcfd, wvfs(filename, b'wb') as destfd:
        gothash = copyandhash(util.filechunkiter(srcfd), destfd)
    if gothash != hash:
        repo.ui.warn(
            _(b'%s: data corruption in %s with hash %s\n')
            % (filename, path, gothash)
        )
        wvfs.unlink(filename)
        return False
    return True


def copytostore(repo, ctx, file, fstandin):
    wvfs = repo.wvfs
    hash = readasstandin(ctx[fstandin])
    if instore(repo, hash):
        return
    if wvfs.exists(file):
        copytostoreabsolute(repo, wvfs.join(file), hash)
    else:
        repo.ui.warn(
            _(b"%s: largefile %s not available from local store\n")
            % (file, hash)
        )


def copyalltostore(repo, node):
    '''Copy all largefiles in a given revision to the store'''

    ctx = repo[node]
    for filename in ctx.files():
        realfile = splitstandin(filename)
        if realfile is not None and filename in ctx.manifest():
            copytostore(repo, ctx, realfile, filename)


def copytostoreabsolute(repo, file, hash):
    if inusercache(repo.ui, hash):
        link(usercachepath(repo.ui, hash), storepath(repo, hash))
    else:
        util.makedirs(os.path.dirname(storepath(repo, hash)))
        with open(file, b'rb') as srcf:
            with util.atomictempfile(
                storepath(repo, hash), createmode=repo.store.createmode
            ) as dstf:
                for chunk in util.filechunkiter(srcf):
                    dstf.write(chunk)
        linktousercache(repo, hash)


def linktousercache(repo, hash):
    """Link / copy the largefile with the specified hash from the store
    to the cache."""
    path = usercachepath(repo.ui, hash)
    link(storepath(repo, hash), path)


def getstandinmatcher(repo, rmatcher=None):
    '''Return a match object that applies rmatcher to the standin directory'''
    wvfs = repo.wvfs
    standindir = shortname

    # no warnings about missing files or directories
    badfn = lambda f, msg: None

    if rmatcher and not rmatcher.always():
        pats = [wvfs.join(standindir, pat) for pat in rmatcher.files()]
        if not pats:
            pats = [wvfs.join(standindir)]
        match = scmutil.match(repo[None], pats, badfn=badfn)
    else:
        # no patterns: relative to repo root
        match = scmutil.match(repo[None], [wvfs.join(standindir)], badfn=badfn)
    return match


def composestandinmatcher(repo, rmatcher):
    """Return a matcher that accepts standins corresponding to the
    files accepted by rmatcher. Pass the list of files in the matcher
    as the paths specified by the user."""
    smatcher = getstandinmatcher(repo, rmatcher)
    isstandin = smatcher.matchfn

    def composedmatchfn(f):
        return isstandin(f) and rmatcher.matchfn(splitstandin(f))

    smatcher._was_tampered_with = True
    smatcher.matchfn = composedmatchfn

    return smatcher


def standin(filename):
    """Return the repo-relative path to the standin for the specified big
    file."""
    # Notes:
    # 1) Some callers want an absolute path, but for instance addlargefiles
    #    needs it repo-relative so it can be passed to repo[None].add().  So
    #    leave it up to the caller to use repo.wjoin() to get an absolute path.
    # 2) Join with '/' because that's what dirstate always uses, even on
    #    Windows. Change existing separator to '/' first in case we are
    #    passed filenames from an external source (like the command line).
    return shortnameslash + util.pconvert(filename)


def isstandin(filename):
    """Return true if filename is a big file standin. filename must be
    in Mercurial's internal form (slash-separated)."""
    return filename.startswith(shortnameslash)


def splitstandin(filename):
    # Split on / because that's what dirstate always uses, even on Windows.
    # Change local separator to / first just in case we are passed filenames
    # from an external source (like the command line).
    bits = util.pconvert(filename).split(b'/', 1)
    if len(bits) == 2 and bits[0] == shortname:
        return bits[1]
    else:
        return None


def updatestandin(repo, lfile, standin):
    """Re-calculate hash value of lfile and write it into standin

    This assumes that "lfutil.standin(lfile) == standin", for efficiency.
    """
    file = repo.wjoin(lfile)
    if repo.wvfs.exists(lfile):
        hash = hashfile(file)
        executable = getexecutable(file)
        writestandin(repo, standin, hash, executable)
    else:
        raise error.Abort(_(b'%s: file not found!') % lfile)


def readasstandin(fctx):
    """read hex hash from given filectx of standin file

    This encapsulates how "standin" data is stored into storage layer."""
    return fctx.data().strip()


def writestandin(repo, standin, hash, executable):
    '''write hash to <repo.root>/<standin>'''
    repo.wwrite(standin, hash + b'\n', executable and b'x' or b'')


def copyandhash(instream, outfile):
    """Read bytes from instream (iterable) and write them to outfile,
    computing the SHA-1 hash of the data along the way. Return the hash."""
    hasher = hashutil.sha1(b'')
    for data in instream:
        hasher.update(data)
        outfile.write(data)
    return hex(hasher.digest())


def hashfile(file):
    if not os.path.exists(file):
        return b''
    with open(file, b'rb') as fd:
        return hexsha1(fd)


def getexecutable(filename):
    mode = os.stat(filename).st_mode
    return (
        (mode & stat.S_IXUSR)
        and (mode & stat.S_IXGRP)
        and (mode & stat.S_IXOTH)
    )


def urljoin(first, second, *arg):
    def join(left, right):
        if not left.endswith(b'/'):
            left += b'/'
        if right.startswith(b'/'):
            right = right[1:]
        return left + right

    url = join(first, second)
    for a in arg:
        url = join(url, a)
    return url


def hexsha1(fileobj):
    """hexsha1 returns the hex-encoded sha1 sum of the data in the file-like
    object data"""
    h = hashutil.sha1()
    for chunk in util.filechunkiter(fileobj):
        h.update(chunk)
    return hex(h.digest())


def httpsendfile(ui, filename):
    return httpconnection.httpsendfile(ui, filename, b'rb')


def unixpath(path):
    '''Return a version of path normalized for use with the lfdirstate.'''
    return util.pconvert(os.path.normpath(path))


def islfilesrepo(repo):
    '''Return true if the repo is a largefile repo.'''
    if b'largefiles' in repo.requirements:
        for entry in repo.store.data_entries():
            if entry.is_revlog and shortnameslash in entry.target_id:
                return True

    return any(openlfdirstate(repo.ui, repo, False))


class storeprotonotcapable(Exception):
    def __init__(self, storetypes):
        self.storetypes = storetypes


def getstandinsstate(repo):
    standins = []
    matcher = getstandinmatcher(repo)
    wctx = repo[None]
    for standin in repo.dirstate.walk(
        matcher, subrepos=[], unknown=False, ignored=False
    ):
        lfile = splitstandin(standin)
        try:
            hash = readasstandin(wctx[standin])
        except IOError:
            hash = None
        standins.append((lfile, hash))
    return standins


def synclfdirstate(repo, lfdirstate, lfile, normallookup):
    lfstandin = standin(lfile)
    if lfstandin not in repo.dirstate:
        lfdirstate.hacky_extension_update_file(
            lfile,
            p1_tracked=False,
            wc_tracked=False,
        )
    else:
        entry = repo.dirstate.get_entry(lfstandin)
        lfdirstate.hacky_extension_update_file(
            lfile,
            wc_tracked=entry.tracked,
            p1_tracked=entry.p1_tracked,
            p2_info=entry.p2_info,
            possibly_dirty=True,
        )


def markcommitted(orig, ctx, node):
    repo = ctx.repo()

    with repo.dirstate.changing_parents(repo):
        orig(node)

        # ATTENTION: "ctx.files()" may differ from "repo[node].files()"
        # because files coming from the 2nd parent are omitted in the latter.
        #
        # The former should be used to get targets of "synclfdirstate",
        # because such files:
        # - are marked as "a" by "patch.patch()" (e.g. via transplant), and
        # - have to be marked as "n" after commit, but
        # - aren't listed in "repo[node].files()"

        lfdirstate = openlfdirstate(repo.ui, repo)
        for f in ctx.files():
            lfile = splitstandin(f)
            if lfile is not None:
                synclfdirstate(repo, lfdirstate, lfile, False)

    # As part of committing, copy all of the largefiles into the cache.
    #
    # Using "node" instead of "ctx" implies additional "repo[node]"
    # lookup while copyalltostore(), but can omit redundant check for
    # files comming from the 2nd parent, which should exist in store
    # at merging.
    copyalltostore(repo, node)


def getlfilestoupdate(oldstandins, newstandins):
    changedstandins = set(oldstandins).symmetric_difference(set(newstandins))
    filelist = []
    for f in changedstandins:
        if f[0] not in filelist:
            filelist.append(f[0])
    return filelist


def getlfilestoupload(repo, missing, addfunc):
    makeprogress = repo.ui.makeprogress
    with makeprogress(
        _(b'finding outgoing largefiles'),
        unit=_(b'revisions'),
        total=len(missing),
    ) as progress:
        for i, n in enumerate(missing):
            progress.update(i)
            parents = [p for p in repo[n].parents() if p != repo.nullid]

            with lfstatus(repo, value=False):
                ctx = repo[n]

            files = set(ctx.files())
            if len(parents) == 2:
                mc = ctx.manifest()
                mp1 = ctx.p1().manifest()
                mp2 = ctx.p2().manifest()
                for f in mp1:
                    if f not in mc:
                        files.add(f)
                for f in mp2:
                    if f not in mc:
                        files.add(f)
                for f in mc:
                    if mc[f] != mp1.get(f, None) or mc[f] != mp2.get(f, None):
                        files.add(f)
            for fn in files:
                if isstandin(fn) and fn in ctx:
                    addfunc(fn, readasstandin(ctx[fn]))


def updatestandinsbymatch(repo, match):
    """Update standins in the working directory according to specified match

    This returns (possibly modified) ``match`` object to be used for
    subsequent commit process.
    """

    ui = repo.ui

    # Case 1: user calls commit with no specific files or
    # include/exclude patterns: refresh and commit all files that
    # are "dirty".
    if match is None or match.always():
        # Spend a bit of time here to get a list of files we know
        # are modified so we can compare only against those.
        # It can cost a lot of time (several seconds)
        # otherwise to update all standins if the largefiles are
        # large.
        dirtymatch = matchmod.always()
        with repo.dirstate.running_status(repo):
            lfdirstate = openlfdirstate(ui, repo)
            unsure, s, mtime_boundary = lfdirstate.status(
                dirtymatch,
                subrepos=[],
                ignored=False,
                clean=False,
                unknown=False,
            )
        modifiedfiles = unsure + s.modified + s.added + s.removed
        lfiles = listlfiles(repo)
        # this only loops through largefiles that exist (not
        # removed/renamed)
        for lfile in lfiles:
            if lfile in modifiedfiles:
                fstandin = standin(lfile)
                if repo.wvfs.exists(fstandin):
                    # this handles the case where a rebase is being
                    # performed and the working copy is not updated
                    # yet.
                    if repo.wvfs.exists(lfile):
                        updatestandin(repo, lfile, fstandin)

        return match

    lfiles = listlfiles(repo)
    match._was_tampered_with = True
    match._files = repo._subdirlfs(match.files(), lfiles)

    # Case 2: user calls commit with specified patterns: refresh
    # any matching big files.
    smatcher = composestandinmatcher(repo, match)
    standins = repo.dirstate.walk(
        smatcher, subrepos=[], unknown=False, ignored=False
    )

    # No matching big files: get out of the way and pass control to
    # the usual commit() method.
    if not standins:
        return match

    # Refresh all matching big files.  It's possible that the
    # commit will end up failing, in which case the big files will
    # stay refreshed.  No harm done: the user modified them and
    # asked to commit them, so sooner or later we're going to
    # refresh the standins.  Might as well leave them refreshed.
    lfdirstate = openlfdirstate(ui, repo)
    for fstandin in standins:
        lfile = splitstandin(fstandin)
        if lfdirstate.get_entry(lfile).tracked:
            updatestandin(repo, lfile, fstandin)

    # Cook up a new matcher that only matches regular files or
    # standins corresponding to the big files requested by the
    # user.  Have to modify _files to prevent commit() from
    # complaining "not tracked" for big files.
    match = copy.copy(match)
    match._was_tampered_with = True
    origmatchfn = match.matchfn

    # Check both the list of largefiles and the list of
    # standins because if a largefile was removed, it
    # won't be in the list of largefiles at this point
    match._files += sorted(standins)

    actualfiles = []
    for f in match._files:
        fstandin = standin(f)

        # For largefiles, only one of the normal and standin should be
        # committed (except if one of them is a remove).  In the case of a
        # standin removal, drop the normal file if it is unknown to dirstate.
        # Thus, skip plain largefile names but keep the standin.
        if f in lfiles or fstandin in standins:
            if not repo.dirstate.get_entry(fstandin).removed:
                if not repo.dirstate.get_entry(f).removed:
                    continue
            elif not repo.dirstate.get_entry(f).any_tracked:
                continue

        actualfiles.append(f)
    match._files = actualfiles

    def matchfn(f):
        if origmatchfn(f):
            return f not in lfiles
        else:
            return f in standins

    match.matchfn = matchfn

    return match


class automatedcommithook:
    """Stateful hook to update standins at the 1st commit of resuming

    For efficiency, updating standins in the working directory should
    be avoided while automated committing (like rebase, transplant and
    so on), because they should be updated before committing.

    But the 1st commit of resuming automated committing (e.g. ``rebase
    --continue``) should update them, because largefiles may be
    modified manually.
    """

    def __init__(self, resuming):
        self.resuming = resuming

    def __call__(self, repo, match):
        if self.resuming:
            self.resuming = False  # avoids updating at subsequent commits
            return updatestandinsbymatch(repo, match)
        else:
            return match


def getstatuswriter(ui, repo, forcibly=None):
    """Return the function to write largefiles specific status out

    If ``forcibly`` is ``None``, this returns the last element of
    ``repo._lfstatuswriters`` as "default" writer function.

    Otherwise, this returns the function to always write out (or
    ignore if ``not forcibly``) status.
    """
    if forcibly is None and hasattr(repo, '_largefilesenabled'):
        return repo._lfstatuswriters[-1]
    else:
        if forcibly:
            return ui.status  # forcibly WRITE OUT
        else:
            return lambda *msg, **opts: None  # forcibly IGNORE
