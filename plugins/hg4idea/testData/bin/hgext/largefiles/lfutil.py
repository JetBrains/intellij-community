# Copyright 2009-2010 Gregory P. Ward
# Copyright 2009-2010 Intelerad Medical Systems Incorporated
# Copyright 2010-2011 Fog Creek Software
# Copyright 2010-2011 Unity Technologies
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''largefiles utility code: must not import other modules in this package.'''

import os
import platform
import shutil
import stat

from mercurial import dirstate, httpconnection, match as match_, util, scmutil
from mercurial.i18n import _

shortname = '.hglf'
shortnameslash = shortname + '/'
longname = 'largefiles'


# -- Private worker functions ------------------------------------------

def getminsize(ui, assumelfiles, opt, default=10):
    lfsize = opt
    if not lfsize and assumelfiles:
        lfsize = ui.config(longname, 'minsize', default=default)
    if lfsize:
        try:
            lfsize = float(lfsize)
        except ValueError:
            raise util.Abort(_('largefiles: size must be number (not %s)\n')
                             % lfsize)
    if lfsize is None:
        raise util.Abort(_('minimum size for largefiles must be specified'))
    return lfsize

def link(src, dest):
    util.makedirs(os.path.dirname(dest))
    try:
        util.oslink(src, dest)
    except OSError:
        # if hardlinks fail, fallback on atomic copy
        dst = util.atomictempfile(dest)
        for chunk in util.filechunkiter(open(src, 'rb')):
            dst.write(chunk)
        dst.close()
        os.chmod(dest, os.stat(src).st_mode)

def usercachepath(ui, hash):
    path = ui.configpath(longname, 'usercache', None)
    if path:
        path = os.path.join(path, hash)
    else:
        if os.name == 'nt':
            appdata = os.getenv('LOCALAPPDATA', os.getenv('APPDATA'))
            if appdata:
                path = os.path.join(appdata, longname, hash)
        elif platform.system() == 'Darwin':
            home = os.getenv('HOME')
            if home:
                path = os.path.join(home, 'Library', 'Caches',
                                    longname, hash)
        elif os.name == 'posix':
            path = os.getenv('XDG_CACHE_HOME')
            if path:
                path = os.path.join(path, longname, hash)
            else:
                home = os.getenv('HOME')
                if home:
                    path = os.path.join(home, '.cache', longname, hash)
        else:
            raise util.Abort(_('unknown operating system: %s\n') % os.name)
    return path

def inusercache(ui, hash):
    path = usercachepath(ui, hash)
    return path and os.path.exists(path)

def findfile(repo, hash):
    if instore(repo, hash):
        repo.ui.note(_('found %s in store\n') % hash)
        return storepath(repo, hash)
    elif inusercache(repo.ui, hash):
        repo.ui.note(_('found %s in system cache\n') % hash)
        path = storepath(repo, hash)
        link(usercachepath(repo.ui, hash), path)
        return path
    return None

class largefilesdirstate(dirstate.dirstate):
    def __getitem__(self, key):
        return super(largefilesdirstate, self).__getitem__(unixpath(key))
    def normal(self, f):
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
    def _ignore(self):
        return False

def openlfdirstate(ui, repo, create=True):
    '''
    Return a dirstate object that tracks largefiles: i.e. its root is
    the repo root, but it is saved in .hg/largefiles/dirstate.
    '''
    lfstoredir = repo.join(longname)
    opener = scmutil.opener(lfstoredir)
    lfdirstate = largefilesdirstate(opener, ui, repo.root,
                                     repo.dirstate._validate)

    # If the largefiles dirstate does not exist, populate and create
    # it. This ensures that we create it on the first meaningful
    # largefiles operation in a new clone.
    if create and not os.path.exists(os.path.join(lfstoredir, 'dirstate')):
        util.makedirs(lfstoredir)
        matcher = getstandinmatcher(repo)
        for standin in repo.dirstate.walk(matcher, [], False, False):
            lfile = splitstandin(standin)
            lfdirstate.normallookup(lfile)
    return lfdirstate

def lfdirstatestatus(lfdirstate, repo, rev):
    match = match_.always(repo.root, repo.getcwd())
    s = lfdirstate.status(match, [], False, False, False)
    unsure, modified, added, removed, missing, unknown, ignored, clean = s
    for lfile in unsure:
        try:
            fctx = repo[rev][standin(lfile)]
        except LookupError:
            fctx = None
        if not fctx or fctx.data().strip() != hashfile(repo.wjoin(lfile)):
            modified.append(lfile)
        else:
            clean.append(lfile)
            lfdirstate.normal(lfile)
    return (modified, added, removed, missing, unknown, ignored, clean)

def listlfiles(repo, rev=None, matcher=None):
    '''return a list of largefiles in the working copy or the
    specified changeset'''

    if matcher is None:
        matcher = getstandinmatcher(repo)

    # ignore unknown files in working directory
    return [splitstandin(f)
            for f in repo[rev].walk(matcher)
            if rev is not None or repo.dirstate[f] != '?']

def instore(repo, hash):
    return os.path.exists(storepath(repo, hash))

def storepath(repo, hash):
    return repo.join(os.path.join(longname, hash))

def copyfromcache(repo, hash, filename):
    '''Copy the specified largefile from the repo or system cache to
    filename in the repository. Return true on success or false if the
    file was not found in either cache (which should not happened:
    this is meant to be called only after ensuring that the needed
    largefile exists in the cache).'''
    path = findfile(repo, hash)
    if path is None:
        return False
    util.makedirs(os.path.dirname(repo.wjoin(filename)))
    # The write may fail before the file is fully written, but we
    # don't use atomic writes in the working copy.
    shutil.copy(path, repo.wjoin(filename))
    return True

def copytostore(repo, rev, file, uploaded=False):
    hash = readstandin(repo, file, rev)
    if instore(repo, hash):
        return
    copytostoreabsolute(repo, repo.wjoin(file), hash)

def copyalltostore(repo, node):
    '''Copy all largefiles in a given revision to the store'''

    ctx = repo[node]
    for filename in ctx.files():
        if isstandin(filename) and filename in ctx.manifest():
            realfile = splitstandin(filename)
            copytostore(repo, ctx.node(), realfile)


def copytostoreabsolute(repo, file, hash):
    if inusercache(repo.ui, hash):
        link(usercachepath(repo.ui, hash), storepath(repo, hash))
    elif not getattr(repo, "_isconverting", False):
        util.makedirs(os.path.dirname(storepath(repo, hash)))
        dst = util.atomictempfile(storepath(repo, hash),
                                  createmode=repo.store.createmode)
        for chunk in util.filechunkiter(open(file, 'rb')):
            dst.write(chunk)
        dst.close()
        linktousercache(repo, hash)

def linktousercache(repo, hash):
    path = usercachepath(repo.ui, hash)
    if path:
        link(storepath(repo, hash), path)

def getstandinmatcher(repo, pats=[], opts={}):
    '''Return a match object that applies pats to the standin directory'''
    standindir = repo.wjoin(shortname)
    if pats:
        pats = [os.path.join(standindir, pat) for pat in pats]
    else:
        # no patterns: relative to repo root
        pats = [standindir]
    # no warnings about missing files or directories
    match = scmutil.match(repo[None], pats, opts)
    match.bad = lambda f, msg: None
    return match

def composestandinmatcher(repo, rmatcher):
    '''Return a matcher that accepts standins corresponding to the
    files accepted by rmatcher. Pass the list of files in the matcher
    as the paths specified by the user.'''
    smatcher = getstandinmatcher(repo, rmatcher.files())
    isstandin = smatcher.matchfn
    def composedmatchfn(f):
        return isstandin(f) and rmatcher.matchfn(splitstandin(f))
    smatcher.matchfn = composedmatchfn

    return smatcher

def standin(filename):
    '''Return the repo-relative path to the standin for the specified big
    file.'''
    # Notes:
    # 1) Some callers want an absolute path, but for instance addlargefiles
    #    needs it repo-relative so it can be passed to repo[None].add().  So
    #    leave it up to the caller to use repo.wjoin() to get an absolute path.
    # 2) Join with '/' because that's what dirstate always uses, even on
    #    Windows. Change existing separator to '/' first in case we are
    #    passed filenames from an external source (like the command line).
    return shortnameslash + util.pconvert(filename)

def isstandin(filename):
    '''Return true if filename is a big file standin. filename must be
    in Mercurial's internal form (slash-separated).'''
    return filename.startswith(shortnameslash)

def splitstandin(filename):
    # Split on / because that's what dirstate always uses, even on Windows.
    # Change local separator to / first just in case we are passed filenames
    # from an external source (like the command line).
    bits = util.pconvert(filename).split('/', 1)
    if len(bits) == 2 and bits[0] == shortname:
        return bits[1]
    else:
        return None

def updatestandin(repo, standin):
    file = repo.wjoin(splitstandin(standin))
    if os.path.exists(file):
        hash = hashfile(file)
        executable = getexecutable(file)
        writestandin(repo, standin, hash, executable)

def readstandin(repo, filename, node=None):
    '''read hex hash from standin for filename at given node, or working
    directory if no node is given'''
    return repo[node][standin(filename)].data().strip()

def writestandin(repo, standin, hash, executable):
    '''write hash to <repo.root>/<standin>'''
    repo.wwrite(standin, hash + '\n', executable and 'x' or '')

def copyandhash(instream, outfile):
    '''Read bytes from instream (iterable) and write them to outfile,
    computing the SHA-1 hash of the data along the way. Return the hash.'''
    hasher = util.sha1('')
    for data in instream:
        hasher.update(data)
        outfile.write(data)
    return hasher.hexdigest()

def hashrepofile(repo, file):
    return hashfile(repo.wjoin(file))

def hashfile(file):
    if not os.path.exists(file):
        return ''
    hasher = util.sha1('')
    fd = open(file, 'rb')
    for data in util.filechunkiter(fd, 128 * 1024):
        hasher.update(data)
    fd.close()
    return hasher.hexdigest()

def getexecutable(filename):
    mode = os.stat(filename).st_mode
    return ((mode & stat.S_IXUSR) and
            (mode & stat.S_IXGRP) and
            (mode & stat.S_IXOTH))

def urljoin(first, second, *arg):
    def join(left, right):
        if not left.endswith('/'):
            left += '/'
        if right.startswith('/'):
            right = right[1:]
        return left + right

    url = join(first, second)
    for a in arg:
        url = join(url, a)
    return url

def hexsha1(data):
    """hexsha1 returns the hex-encoded sha1 sum of the data in the file-like
    object data"""
    h = util.sha1()
    for chunk in util.filechunkiter(data):
        h.update(chunk)
    return h.hexdigest()

def httpsendfile(ui, filename):
    return httpconnection.httpsendfile(ui, filename, 'rb')

def unixpath(path):
    '''Return a version of path normalized for use with the lfdirstate.'''
    return util.pconvert(os.path.normpath(path))

def islfilesrepo(repo):
    if ('largefiles' in repo.requirements and
            util.any(shortnameslash in f[0] for f in repo.store.datafiles())):
        return True

    return util.any(openlfdirstate(repo.ui, repo, False))

class storeprotonotcapable(Exception):
    def __init__(self, storetypes):
        self.storetypes = storetypes

def getstandinsstate(repo):
    standins = []
    matcher = getstandinmatcher(repo)
    for standin in repo.dirstate.walk(matcher, [], False, False):
        lfile = splitstandin(standin)
        try:
            hash = readstandin(repo, lfile)
        except IOError:
            hash = None
        standins.append((lfile, hash))
    return standins

def getlfilestoupdate(oldstandins, newstandins):
    changedstandins = set(oldstandins).symmetric_difference(set(newstandins))
    filelist = []
    for f in changedstandins:
        if f[0] not in filelist:
            filelist.append(f[0])
    return filelist
