# logexchange.py
#
# Copyright 2017 Augie Fackler <raf@durin42.com>
# Copyright 2017 Sean Farley <sean@farley.io>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from .node import hex

from . import (
    pycompat,
    util,
    vfs as vfsmod,
)
from .utils import (
    urlutil,
)

# directory name in .hg/ in which remotenames files will be present
remotenamedir = b'logexchange'


def readremotenamefile(repo, filename):
    """
    reads a file from .hg/logexchange/ directory and yields it's content
    filename: the file to be read
    yield a tuple (node, remotepath, name)
    """

    vfs = vfsmod.vfs(repo.vfs.join(remotenamedir))
    if not vfs.exists(filename):
        return
    f = vfs(filename)
    lineno = 0
    for line in f:
        line = line.strip()
        if not line:
            continue
        # contains the version number
        if lineno == 0:
            lineno += 1
        try:
            node, remote, rname = line.split(b'\0')
            yield node, remote, rname
        except ValueError:
            pass

    f.close()


def readremotenames(repo):
    """
    read the details about the remotenames stored in .hg/logexchange/ and
    yields a tuple (node, remotepath, name). It does not yields information
    about whether an entry yielded is branch or bookmark. To get that
    information, call the respective functions.
    """

    for bmentry in readremotenamefile(repo, b'bookmarks'):
        yield bmentry
    for branchentry in readremotenamefile(repo, b'branches'):
        yield branchentry


def writeremotenamefile(repo, remotepath, names, nametype):
    vfs = vfsmod.vfs(repo.vfs.join(remotenamedir))
    f = vfs(nametype, b'w', atomictemp=True)
    # write the storage version info on top of file
    # version '0' represents the very initial version of the storage format
    f.write(b'0\n\n')

    olddata = set(readremotenamefile(repo, nametype))
    # re-save the data from a different remote than this one.
    for node, oldpath, rname in sorted(olddata):
        if oldpath != remotepath:
            f.write(b'%s\0%s\0%s\n' % (node, oldpath, rname))

    for name, node in sorted(pycompat.iteritems(names)):
        if nametype == b"branches":
            for n in node:
                f.write(b'%s\0%s\0%s\n' % (n, remotepath, name))
        elif nametype == b"bookmarks":
            if node:
                f.write(b'%s\0%s\0%s\n' % (node, remotepath, name))

    f.close()


def saveremotenames(repo, remotepath, branches=None, bookmarks=None):
    """
    save remotenames i.e. remotebookmarks and remotebranches in their
    respective files under ".hg/logexchange/" directory.
    """
    wlock = repo.wlock()
    try:
        if bookmarks:
            writeremotenamefile(repo, remotepath, bookmarks, b'bookmarks')
        if branches:
            writeremotenamefile(repo, remotepath, branches, b'branches')
    finally:
        wlock.release()


def activepath(repo, remote):
    """returns remote path"""
    # is the remote a local peer
    local = remote.local()

    # determine the remote path from the repo, if possible; else just
    # use the string given to us
    rpath = remote
    if local:
        rpath = util.pconvert(remote._repo.root)
    elif not isinstance(remote, bytes):
        rpath = remote._url

    # represent the remotepath with user defined path name if exists
    for path, url in repo.ui.configitems(b'paths'):
        # remove auth info from user defined url
        noauthurl = urlutil.removeauth(url)

        # Standardize on unix style paths, otherwise some {remotenames} end up
        # being an absolute path on Windows.
        url = util.pconvert(bytes(url))
        noauthurl = util.pconvert(noauthurl)
        if url == rpath or noauthurl == rpath:
            rpath = path
            break

    return rpath


def pullremotenames(localrepo, remoterepo):
    """
    pulls bookmarks and branches information of the remote repo during a
    pull or clone operation.
    localrepo is our local repository
    remoterepo is the peer instance
    """
    remotepath = activepath(localrepo, remoterepo)

    with remoterepo.commandexecutor() as e:
        bookmarks = e.callcommand(
            b'listkeys',
            {
                b'namespace': b'bookmarks',
            },
        ).result()

    # on a push, we don't want to keep obsolete heads since
    # they won't show up as heads on the next pull, so we
    # remove them here otherwise we would require the user
    # to issue a pull to refresh the storage
    bmap = {}
    repo = localrepo.unfiltered()

    with remoterepo.commandexecutor() as e:
        branchmap = e.callcommand(b'branchmap', {}).result()

    for branch, nodes in pycompat.iteritems(branchmap):
        bmap[branch] = []
        for node in nodes:
            if node in repo and not repo[node].obsolete():
                bmap[branch].append(hex(node))

    saveremotenames(localrepo, remotepath, bmap, bookmarks)
