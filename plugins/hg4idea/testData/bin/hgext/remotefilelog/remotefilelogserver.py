# remotefilelogserver.py - server logic for a remotefilelog server
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from __future__ import absolute_import

import errno
import os
import stat
import time
import zlib

from mercurial.i18n import _
from mercurial.node import bin, hex
from mercurial.pycompat import open
from mercurial import (
    changegroup,
    changelog,
    context,
    error,
    extensions,
    match,
    pycompat,
    scmutil,
    store,
    streamclone,
    util,
    wireprotoserver,
    wireprototypes,
    wireprotov1server,
)
from . import (
    constants,
    shallowutil,
)

_sshv1server = wireprotoserver.sshv1protocolhandler


def setupserver(ui, repo):
    """Sets up a normal Mercurial repo so it can serve files to shallow repos."""
    onetimesetup(ui)

    # don't send files to shallow clients during pulls
    def generatefiles(
        orig, self, changedfiles, linknodes, commonrevs, source, *args, **kwargs
    ):
        caps = self._bundlecaps or []
        if constants.BUNDLE2_CAPABLITY in caps:
            # only send files that don't match the specified patterns
            includepattern = None
            excludepattern = None
            for cap in self._bundlecaps or []:
                if cap.startswith(b"includepattern="):
                    includepattern = cap[len(b"includepattern=") :].split(b'\0')
                elif cap.startswith(b"excludepattern="):
                    excludepattern = cap[len(b"excludepattern=") :].split(b'\0')

            m = match.always()
            if includepattern or excludepattern:
                m = match.match(
                    repo.root, b'', None, includepattern, excludepattern
                )

            changedfiles = list([f for f in changedfiles if not m(f)])
        return orig(
            self, changedfiles, linknodes, commonrevs, source, *args, **kwargs
        )

    extensions.wrapfunction(
        changegroup.cgpacker, b'generatefiles', generatefiles
    )


onetime = False


def onetimesetup(ui):
    """Configures the wireprotocol for both clients and servers."""
    global onetime
    if onetime:
        return
    onetime = True

    # support file content requests
    wireprotov1server.wireprotocommand(
        b'x_rfl_getflogheads', b'path', permission=b'pull'
    )(getflogheads)
    wireprotov1server.wireprotocommand(
        b'x_rfl_getfiles', b'', permission=b'pull'
    )(getfiles)
    wireprotov1server.wireprotocommand(
        b'x_rfl_getfile', b'file node', permission=b'pull'
    )(getfile)

    class streamstate(object):
        match = None
        shallowremote = False
        noflatmf = False

    state = streamstate()

    def stream_out_shallow(repo, proto, other):
        includepattern = None
        excludepattern = None
        raw = other.get(b'includepattern')
        if raw:
            includepattern = raw.split(b'\0')
        raw = other.get(b'excludepattern')
        if raw:
            excludepattern = raw.split(b'\0')

        oldshallow = state.shallowremote
        oldmatch = state.match
        oldnoflatmf = state.noflatmf
        try:
            state.shallowremote = True
            state.match = match.always()
            state.noflatmf = other.get(b'noflatmanifest') == b'True'
            if includepattern or excludepattern:
                state.match = match.match(
                    repo.root, b'', None, includepattern, excludepattern
                )
            streamres = wireprotov1server.stream(repo, proto)

            # Force the first value to execute, so the file list is computed
            # within the try/finally scope
            first = next(streamres.gen)
            second = next(streamres.gen)

            def gen():
                yield first
                yield second
                for value in streamres.gen:
                    yield value

            return wireprototypes.streamres(gen())
        finally:
            state.shallowremote = oldshallow
            state.match = oldmatch
            state.noflatmf = oldnoflatmf

    wireprotov1server.commands[b'stream_out_shallow'] = (
        stream_out_shallow,
        b'*',
    )

    # don't clone filelogs to shallow clients
    def _walkstreamfiles(orig, repo, matcher=None):
        if state.shallowremote:
            # if we are shallow ourselves, stream our local commits
            if shallowutil.isenabled(repo):
                striplen = len(repo.store.path) + 1
                readdir = repo.store.rawvfs.readdir
                visit = [os.path.join(repo.store.path, b'data')]
                while visit:
                    p = visit.pop()
                    for f, kind, st in readdir(p, stat=True):
                        fp = p + b'/' + f
                        if kind == stat.S_IFREG:
                            if not fp.endswith(b'.i') and not fp.endswith(
                                b'.d'
                            ):
                                n = util.pconvert(fp[striplen:])
                                d = store.decodedir(n)
                                t = store.FILETYPE_OTHER
                                yield (t, d, n, st.st_size)
                        if kind == stat.S_IFDIR:
                            visit.append(fp)

            if scmutil.istreemanifest(repo):
                for (t, u, e, s) in repo.store.datafiles():
                    if u.startswith(b'meta/') and (
                        u.endswith(b'.i') or u.endswith(b'.d')
                    ):
                        yield (t, u, e, s)

            # Return .d and .i files that do not match the shallow pattern
            match = state.match
            if match and not match.always():
                for (t, u, e, s) in repo.store.datafiles():
                    f = u[5:-2]  # trim data/...  and .i/.d
                    if not state.match(f):
                        yield (t, u, e, s)

            for x in repo.store.topfiles():
                if state.noflatmf and x[1][:11] == b'00manifest.':
                    continue
                yield x

        elif shallowutil.isenabled(repo):
            # don't allow cloning from a shallow repo to a full repo
            # since it would require fetching every version of every
            # file in order to create the revlogs.
            raise error.Abort(
                _(b"Cannot clone from a shallow repo to a full repo.")
            )
        else:
            for x in orig(repo, matcher):
                yield x

    extensions.wrapfunction(streamclone, b'_walkstreamfiles', _walkstreamfiles)

    # expose remotefilelog capabilities
    def _capabilities(orig, repo, proto):
        caps = orig(repo, proto)
        if shallowutil.isenabled(repo) or ui.configbool(
            b'remotefilelog', b'server'
        ):
            if isinstance(proto, _sshv1server):
                # legacy getfiles method which only works over ssh
                caps.append(constants.NETWORK_CAP_LEGACY_SSH_GETFILES)
            caps.append(b'x_rfl_getflogheads')
            caps.append(b'x_rfl_getfile')
        return caps

    extensions.wrapfunction(wireprotov1server, b'_capabilities', _capabilities)

    def _adjustlinkrev(orig, self, *args, **kwargs):
        # When generating file blobs, taking the real path is too slow on large
        # repos, so force it to just return the linkrev directly.
        repo = self._repo
        if util.safehasattr(repo, b'forcelinkrev') and repo.forcelinkrev:
            return self._filelog.linkrev(self._filelog.rev(self._filenode))
        return orig(self, *args, **kwargs)

    extensions.wrapfunction(
        context.basefilectx, b'_adjustlinkrev', _adjustlinkrev
    )

    def _iscmd(orig, cmd):
        if cmd == b'x_rfl_getfiles':
            return False
        return orig(cmd)

    extensions.wrapfunction(wireprotoserver, b'iscmd', _iscmd)


def _loadfileblob(repo, cachepath, path, node):
    filecachepath = os.path.join(cachepath, path, hex(node))
    if not os.path.exists(filecachepath) or os.path.getsize(filecachepath) == 0:
        filectx = repo.filectx(path, fileid=node)
        if filectx.node() == repo.nullid:
            repo.changelog = changelog.changelog(repo.svfs)
            filectx = repo.filectx(path, fileid=node)

        text = createfileblob(filectx)
        # TODO configurable compression engines
        text = zlib.compress(text)

        # everything should be user & group read/writable
        oldumask = os.umask(0o002)
        try:
            dirname = os.path.dirname(filecachepath)
            if not os.path.exists(dirname):
                try:
                    os.makedirs(dirname)
                except OSError as ex:
                    if ex.errno != errno.EEXIST:
                        raise

            f = None
            try:
                f = util.atomictempfile(filecachepath, b"wb")
                f.write(text)
            except (IOError, OSError):
                # Don't abort if the user only has permission to read,
                # and not write.
                pass
            finally:
                if f:
                    f.close()
        finally:
            os.umask(oldumask)
    else:
        with open(filecachepath, b"rb") as f:
            text = f.read()
    return text


def getflogheads(repo, proto, path):
    """A server api for requesting a filelog's heads"""
    flog = repo.file(path)
    heads = flog.heads()
    return b'\n'.join((hex(head) for head in heads if head != repo.nullid))


def getfile(repo, proto, file, node):
    """A server api for requesting a particular version of a file. Can be used
    in batches to request many files at once. The return protocol is:
    <errorcode>\0<data/errormsg> where <errorcode> is 0 for success or
    non-zero for an error.

    data is a compressed blob with revlog flag and ancestors information. See
    createfileblob for its content.
    """
    if shallowutil.isenabled(repo):
        return b'1\0' + _(b'cannot fetch remote files from shallow repo')
    cachepath = repo.ui.config(b"remotefilelog", b"servercachepath")
    if not cachepath:
        cachepath = os.path.join(repo.path, b"remotefilelogcache")
    node = bin(node.strip())
    if node == repo.nullid:
        return b'0\0'
    return b'0\0' + _loadfileblob(repo, cachepath, file, node)


def getfiles(repo, proto):
    """A server api for requesting particular versions of particular files."""
    if shallowutil.isenabled(repo):
        raise error.Abort(_(b'cannot fetch remote files from shallow repo'))
    if not isinstance(proto, _sshv1server):
        raise error.Abort(_(b'cannot fetch remote files over non-ssh protocol'))

    def streamer():
        fin = proto._fin

        cachepath = repo.ui.config(b"remotefilelog", b"servercachepath")
        if not cachepath:
            cachepath = os.path.join(repo.path, b"remotefilelogcache")

        while True:
            request = fin.readline()[:-1]
            if not request:
                break

            node = bin(request[:40])
            if node == repo.nullid:
                yield b'0\n'
                continue

            path = request[40:]

            text = _loadfileblob(repo, cachepath, path, node)

            yield b'%d\n%s' % (len(text), text)

            # it would be better to only flush after processing a whole batch
            # but currently we don't know if there are more requests coming
            proto._fout.flush()

    return wireprototypes.streamres(streamer())


def createfileblob(filectx):
    """
    format:
        v0:
            str(len(rawtext)) + '\0' + rawtext + ancestortext
        v1:
            'v1' + '\n' + metalist + '\0' + rawtext + ancestortext
            metalist := metalist + '\n' + meta | meta
            meta := sizemeta | flagmeta
            sizemeta := METAKEYSIZE + str(len(rawtext))
            flagmeta := METAKEYFLAG + str(flag)

            note: sizemeta must exist. METAKEYFLAG and METAKEYSIZE must have a
            length of 1.
    """
    flog = filectx.filelog()
    frev = filectx.filerev()
    revlogflags = flog._revlog.flags(frev)
    if revlogflags == 0:
        # normal files
        text = filectx.data()
    else:
        # lfs, read raw revision data
        text = flog.rawdata(frev)

    repo = filectx._repo

    ancestors = [filectx]

    try:
        repo.forcelinkrev = True
        ancestors.extend([f for f in filectx.ancestors()])

        ancestortext = b""
        for ancestorctx in ancestors:
            parents = ancestorctx.parents()
            p1 = repo.nullid
            p2 = repo.nullid
            if len(parents) > 0:
                p1 = parents[0].filenode()
            if len(parents) > 1:
                p2 = parents[1].filenode()

            copyname = b""
            rename = ancestorctx.renamed()
            if rename:
                copyname = rename[0]
            linknode = ancestorctx.node()
            ancestortext += b"%s%s%s%s%s\0" % (
                ancestorctx.filenode(),
                p1,
                p2,
                linknode,
                copyname,
            )
    finally:
        repo.forcelinkrev = False

    header = shallowutil.buildfileblobheader(len(text), revlogflags)

    return b"%s\0%s%s" % (header, text, ancestortext)


def gcserver(ui, repo):
    if not repo.ui.configbool(b"remotefilelog", b"server"):
        return

    neededfiles = set()
    heads = repo.revs(b"heads(tip~25000:) - null")

    cachepath = repo.vfs.join(b"remotefilelogcache")
    for head in heads:
        mf = repo[head].manifest()
        for filename, filenode in pycompat.iteritems(mf):
            filecachepath = os.path.join(cachepath, filename, hex(filenode))
            neededfiles.add(filecachepath)

    # delete unneeded older files
    days = repo.ui.configint(b"remotefilelog", b"serverexpiration")
    expiration = time.time() - (days * 24 * 60 * 60)

    progress = ui.makeprogress(_(b"removing old server cache"), unit=b"files")
    progress.update(0)
    for root, dirs, files in os.walk(cachepath):
        for file in files:
            filepath = os.path.join(root, file)
            progress.increment()
            if filepath in neededfiles:
                continue

            stat = os.stat(filepath)
            if stat.st_mtime < expiration:
                os.remove(filepath)

    progress.complete()
