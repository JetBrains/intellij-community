# Copyright 2009-2010 Gregory P. Ward
# Copyright 2009-2010 Intelerad Medical Systems Incorporated
# Copyright 2010-2011 Fog Creek Software
# Copyright 2010-2011 Unity Technologies
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''High-level command function for lfconvert, plus the cmdtable.'''

import binascii
import os
import shutil

from mercurial.i18n import _
from mercurial.node import (
    bin,
    hex,
)

from mercurial import (
    cmdutil,
    context,
    error,
    exthelper,
    hg,
    lock,
    logcmdutil,
    match as matchmod,
    scmutil,
    util,
)
from mercurial.utils import hashutil

from ..convert import (
    convcmd,
    filemap,
)

from . import lfutil, storefactory

release = lock.release

# -- Commands ----------------------------------------------------------

eh = exthelper.exthelper()


@eh.command(
    b'lfconvert',
    [
        (
            b's',
            b'size',
            b'',
            _(b'minimum size (MB) for files to be converted as largefiles'),
            b'SIZE',
        ),
        (
            b'',
            b'to-normal',
            False,
            _(b'convert from a largefiles repo to a normal repo'),
        ),
    ],
    _(b'hg lfconvert SOURCE DEST [FILE ...]'),
    norepo=True,
    inferrepo=True,
)
def lfconvert(ui, src, dest, *pats, **opts):
    """convert a normal repository to a largefiles repository

    Convert repository SOURCE to a new repository DEST, identical to
    SOURCE except that certain files will be converted as largefiles:
    specifically, any file that matches any PATTERN *or* whose size is
    above the minimum size threshold is converted as a largefile. The
    size used to determine whether or not to track a file as a
    largefile is the size of the first version of the file. The
    minimum size can be specified either with --size or in
    configuration as ``largefiles.size``.

    After running this command you will need to make sure that
    largefiles is enabled anywhere you intend to push the new
    repository.

    Use --to-normal to convert largefiles back to normal files; after
    this, the DEST repository can be used without largefiles at all."""

    if opts['to_normal']:
        tolfile = False
    else:
        tolfile = True
        size = lfutil.getminsize(ui, True, opts.get('size'), default=None)

    if not hg.islocal(src):
        raise error.Abort(_(b'%s is not a local Mercurial repo') % src)
    if not hg.islocal(dest):
        raise error.Abort(_(b'%s is not a local Mercurial repo') % dest)

    rsrc = hg.repository(ui, src)
    ui.status(_(b'initializing destination %s\n') % dest)
    rdst = hg.repository(ui, dest, create=True)

    success = False
    dstwlock = dstlock = None
    try:
        # Get a list of all changesets in the source.  The easy way to do this
        # is to simply walk the changelog, using changelog.nodesbetween().
        # Take a look at mercurial/revlog.py:639 for more details.
        # Use a generator instead of a list to decrease memory usage
        ctxs = (
            rsrc[ctx]
            for ctx in rsrc.changelog.nodesbetween(None, rsrc.heads())[0]
        )
        revmap = {rsrc.nullid: rdst.nullid}
        if tolfile:
            # Lock destination to prevent modification while it is converted to.
            # Don't need to lock src because we are just reading from its
            # history which can't change.
            dstwlock = rdst.wlock()
            dstlock = rdst.lock()

            lfiles = set()
            normalfiles = set()
            if not pats:
                pats = ui.configlist(lfutil.longname, b'patterns')
            if pats:
                matcher = matchmod.match(rsrc.root, b'', list(pats))
            else:
                matcher = None

            lfiletohash = {}
            with ui.makeprogress(
                _(b'converting revisions'),
                unit=_(b'revisions'),
                total=rsrc[b'tip'].rev(),
            ) as progress:
                for ctx in ctxs:
                    progress.update(ctx.rev())
                    _lfconvert_addchangeset(
                        rsrc,
                        rdst,
                        ctx,
                        revmap,
                        lfiles,
                        normalfiles,
                        matcher,
                        size,
                        lfiletohash,
                    )

            if rdst.wvfs.exists(lfutil.shortname):
                rdst.wvfs.rmtree(lfutil.shortname)

            for f in lfiletohash.keys():
                if rdst.wvfs.isfile(f):
                    rdst.wvfs.unlink(f)
                try:
                    rdst.wvfs.removedirs(rdst.wvfs.dirname(f))
                except OSError:
                    pass

            # If there were any files converted to largefiles, add largefiles
            # to the destination repository's requirements.
            if lfiles:
                rdst.requirements.add(b'largefiles')
                scmutil.writereporequirements(rdst)
        else:

            class lfsource(filemap.filemap_source):
                def __init__(self, ui, source):
                    super(lfsource, self).__init__(ui, source, None)
                    self.filemapper.rename[lfutil.shortname] = b'.'

                def getfile(self, name, rev):
                    realname, realrev = rev
                    f = super(lfsource, self).getfile(name, rev)

                    if (
                        not realname.startswith(lfutil.shortnameslash)
                        or f[0] is None
                    ):
                        return f

                    # Substitute in the largefile data for the hash
                    hash = f[0].strip()
                    path = lfutil.findfile(rsrc, hash)

                    if path is None:
                        raise error.Abort(
                            _(b"missing largefile for '%s' in %s")
                            % (realname, realrev)
                        )
                    return util.readfile(path), f[1]

            class converter(convcmd.converter):
                def __init__(self, ui, source, dest, revmapfile, opts):
                    src = lfsource(ui, source)

                    super(converter, self).__init__(
                        ui, src, dest, revmapfile, opts
                    )

            found, missing = downloadlfiles(ui, rsrc)
            if missing != 0:
                raise error.Abort(_(b"all largefiles must be present locally"))

            orig = convcmd.converter
            convcmd.converter = converter

            try:
                convcmd.convert(
                    ui, src, dest, source_type=b'hg', dest_type=b'hg'
                )
            finally:
                convcmd.converter = orig
        success = True
    finally:
        if tolfile:
            # XXX is this the right context semantically ?
            with rdst.dirstate.changing_parents(rdst):
                rdst.dirstate.clear()
            release(dstlock, dstwlock)
        if not success:
            # we failed, remove the new directory
            shutil.rmtree(rdst.root)


def _lfconvert_addchangeset(
    rsrc, rdst, ctx, revmap, lfiles, normalfiles, matcher, size, lfiletohash
):
    # Convert src parents to dst parents
    parents = _convertparents(ctx, revmap)

    # Generate list of changed files
    files = _getchangedfiles(ctx, parents)

    dstfiles = []
    for f in files:
        if f not in lfiles and f not in normalfiles:
            islfile = _islfile(f, ctx, matcher, size)
            # If this file was renamed or copied then copy
            # the largefile-ness of its predecessor
            if f in ctx.manifest():
                fctx = ctx.filectx(f)
                renamed = fctx.copysource()
                if renamed is None:
                    # the code below assumes renamed to be a boolean or a list
                    # and won't quite work with the value None
                    renamed = False
                renamedlfile = renamed and renamed in lfiles
                islfile |= renamedlfile
                if b'l' in fctx.flags():
                    if renamedlfile:
                        raise error.Abort(
                            _(b'renamed/copied largefile %s becomes symlink')
                            % f
                        )
                    islfile = False
            if islfile:
                lfiles.add(f)
            else:
                normalfiles.add(f)

        if f in lfiles:
            fstandin = lfutil.standin(f)
            dstfiles.append(fstandin)
            # largefile in manifest if it has not been removed/renamed
            if f in ctx.manifest():
                fctx = ctx.filectx(f)
                if b'l' in fctx.flags():
                    renamed = fctx.copysource()
                    if renamed and renamed in lfiles:
                        raise error.Abort(
                            _(b'largefile %s becomes symlink') % f
                        )

                # largefile was modified, update standins
                m = hashutil.sha1(b'')
                m.update(ctx[f].data())
                hash = hex(m.digest())
                if f not in lfiletohash or lfiletohash[f] != hash:
                    rdst.wwrite(f, ctx[f].data(), ctx[f].flags())
                    executable = b'x' in ctx[f].flags()
                    lfutil.writestandin(rdst, fstandin, hash, executable)
                    lfiletohash[f] = hash
        else:
            # normal file
            dstfiles.append(f)

    def getfilectx(repo, memctx, f):
        srcfname = lfutil.splitstandin(f)
        if srcfname is not None:
            # if the file isn't in the manifest then it was removed
            # or renamed, return None to indicate this
            try:
                fctx = ctx.filectx(srcfname)
            except error.LookupError:
                return None
            renamed = fctx.copysource()
            if renamed:
                # standin is always a largefile because largefile-ness
                # doesn't change after rename or copy
                renamed = lfutil.standin(renamed)

            return context.memfilectx(
                repo,
                memctx,
                f,
                lfiletohash[srcfname] + b'\n',
                b'l' in fctx.flags(),
                b'x' in fctx.flags(),
                renamed,
            )
        else:
            return _getnormalcontext(repo, ctx, f, revmap)

    # Commit
    _commitcontext(rdst, parents, ctx, dstfiles, getfilectx, revmap)


def _commitcontext(rdst, parents, ctx, dstfiles, getfilectx, revmap):
    mctx = context.memctx(
        rdst,
        parents,
        ctx.description(),
        dstfiles,
        getfilectx,
        ctx.user(),
        ctx.date(),
        ctx.extra(),
    )
    ret = rdst.commitctx(mctx)
    lfutil.copyalltostore(rdst, ret)
    rdst.setparents(ret)
    revmap[ctx.node()] = rdst.changelog.tip()


# Generate list of changed files
def _getchangedfiles(ctx, parents):
    files = set(ctx.files())
    if ctx.repo().nullid not in parents:
        mc = ctx.manifest()
        for pctx in ctx.parents():
            for fn in pctx.manifest().diff(mc):
                files.add(fn)
    return files


# Convert src parents to dst parents
def _convertparents(ctx, revmap):
    parents = []
    for p in ctx.parents():
        parents.append(revmap[p.node()])
    while len(parents) < 2:
        parents.append(ctx.repo().nullid)
    return parents


# Get memfilectx for a normal file
def _getnormalcontext(repo, ctx, f, revmap):
    try:
        fctx = ctx.filectx(f)
    except error.LookupError:
        return None
    renamed = fctx.copysource()

    data = fctx.data()
    if f == b'.hgtags':
        data = _converttags(repo.ui, revmap, data)
    return context.memfilectx(
        repo, ctx, f, data, b'l' in fctx.flags(), b'x' in fctx.flags(), renamed
    )


# Remap tag data using a revision map
def _converttags(ui, revmap, data):
    newdata = []
    for line in data.splitlines():
        try:
            id, name = line.split(b' ', 1)
        except ValueError:
            ui.warn(_(b'skipping incorrectly formatted tag %s\n') % line)
            continue
        try:
            newid = bin(id)
        except binascii.Error:
            ui.warn(_(b'skipping incorrectly formatted id %s\n') % id)
            continue
        try:
            newdata.append(b'%s %s\n' % (hex(revmap[newid]), name))
        except KeyError:
            ui.warn(_(b'no mapping for id %s\n') % id)
            continue
    return b''.join(newdata)


def _islfile(file, ctx, matcher, size):
    """Return true if file should be considered a largefile, i.e.
    matcher matches it or it is larger than size."""
    # never store special .hg* files as largefiles
    if file == b'.hgtags' or file == b'.hgignore' or file == b'.hgsigs':
        return False
    if matcher and matcher(file):
        return True
    try:
        return ctx.filectx(file).size() >= size * 1024 * 1024
    except error.LookupError:
        return False


def uploadlfiles(ui, rsrc, rdst, files):
    '''upload largefiles to the central store'''

    if not files:
        return

    store = storefactory.openstore(rsrc, rdst, put=True)

    at = 0
    ui.debug(b"sending statlfile command for %d largefiles\n" % len(files))
    retval = store.exists(files)
    files = [h for h in files if not retval[h]]
    ui.debug(b"%d largefiles need to be uploaded\n" % len(files))

    with ui.makeprogress(
        _(b'uploading largefiles'), unit=_(b'files'), total=len(files)
    ) as progress:
        for hash in files:
            progress.update(at)
            source = lfutil.findfile(rsrc, hash)
            if not source:
                raise error.Abort(
                    _(
                        b'largefile %s missing from store'
                        b' (needs to be uploaded)'
                    )
                    % hash
                )
            # XXX check for errors here
            store.put(source, hash)
            at += 1


def verifylfiles(ui, repo, all=False, contents=False):
    """Verify that every largefile revision in the current changeset
    exists in the central store.  With --contents, also verify that
    the contents of each local largefile file revision are correct (SHA-1 hash
    matches the revision ID).  With --all, check every changeset in
    this repository."""
    if all:
        revs = repo.revs(b'all()')
    else:
        revs = [b'.']

    store = storefactory.openstore(repo)
    return store.verify(revs, contents=contents)


def cachelfiles(ui, repo, node, filelist=None):
    """cachelfiles ensures that all largefiles needed by the specified revision
    are present in the repository's largefile cache.

    returns a tuple (cached, missing).  cached is the list of files downloaded
    by this operation; missing is the list of files that were needed but could
    not be found."""
    lfiles = lfutil.listlfiles(repo, node)
    if filelist:
        lfiles = set(lfiles) & set(filelist)
    toget = []

    ctx = repo[node]
    for lfile in lfiles:
        try:
            expectedhash = lfutil.readasstandin(ctx[lfutil.standin(lfile)])
        except FileNotFoundError:
            continue  # node must be None and standin wasn't found in wctx
        if not lfutil.findfile(repo, expectedhash):
            toget.append((lfile, expectedhash))

    if toget:
        store = storefactory.openstore(repo)
        ret = store.get(toget)
        return ret

    return ([], [])


def downloadlfiles(ui, repo):
    tonode = repo.changelog.node
    totalsuccess = 0
    totalmissing = 0
    for rev in repo.revs(b'file(%s)', b'path:' + lfutil.shortname):
        success, missing = cachelfiles(ui, repo, tonode(rev))
        totalsuccess += len(success)
        totalmissing += len(missing)
    ui.status(_(b"%d additional largefiles cached\n") % totalsuccess)
    if totalmissing > 0:
        ui.status(_(b"%d largefiles failed to download\n") % totalmissing)
    return totalsuccess, totalmissing


def updatelfiles(
    ui, repo, filelist=None, printmessage=None, normallookup=False
):
    """Update largefiles according to standins in the working directory

    If ``printmessage`` is other than ``None``, it means "print (or
    ignore, for false) message forcibly".
    """
    statuswriter = lfutil.getstatuswriter(ui, repo, printmessage)
    with repo.wlock():
        lfdirstate = lfutil.openlfdirstate(ui, repo)
        lfiles = set(lfutil.listlfiles(repo)) | set(lfdirstate)

        if filelist is not None:
            filelist = set(filelist)
            lfiles = [f for f in lfiles if f in filelist]

        update = {}
        dropped = set()
        updated, removed = 0, 0
        wvfs = repo.wvfs
        wctx = repo[None]
        for lfile in lfiles:
            lfileorig = os.path.relpath(
                scmutil.backuppath(ui, repo, lfile), start=repo.root
            )
            standin = lfutil.standin(lfile)
            standinorig = os.path.relpath(
                scmutil.backuppath(ui, repo, standin), start=repo.root
            )
            if wvfs.exists(standin):
                if wvfs.exists(standinorig) and wvfs.exists(lfile):
                    shutil.copyfile(wvfs.join(lfile), wvfs.join(lfileorig))
                    wvfs.unlinkpath(standinorig)
                expecthash = lfutil.readasstandin(wctx[standin])
                if expecthash != b'':
                    if lfile not in wctx:  # not switched to normal file
                        if repo.dirstate.get_entry(standin).any_tracked:
                            wvfs.unlinkpath(lfile, ignoremissing=True)
                        else:
                            dropped.add(lfile)

                    # allocate an entry in largefiles dirstate to prevent
                    # lfilesrepo.status() from reporting missing files as
                    # removed.
                    lfdirstate.hacky_extension_update_file(
                        lfile,
                        p1_tracked=True,
                        wc_tracked=True,
                        possibly_dirty=True,
                    )
                    update[lfile] = expecthash
            else:
                # Remove lfiles for which the standin is deleted, unless the
                # lfile is added to the repository again. This happens when a
                # largefile is converted back to a normal file: the standin
                # disappears, but a new (normal) file appears as the lfile.
                if (
                    wvfs.exists(lfile)
                    and repo.dirstate.normalize(lfile) not in wctx
                ):
                    wvfs.unlinkpath(lfile)
                    removed += 1

        # largefile processing might be slow and be interrupted - be prepared
        lfdirstate.write(repo.currenttransaction())

        if lfiles:
            lfiles = [f for f in lfiles if f not in dropped]

            for f in dropped:
                repo.wvfs.unlinkpath(lfutil.standin(f))
                # This needs to happen for dropped files, otherwise they stay in
                # the M state.
                lfdirstate._map.reset_state(f)

            statuswriter(_(b'getting changed largefiles\n'))
            cachelfiles(ui, repo, None, lfiles)

        for lfile in lfiles:
            update1 = 0

            expecthash = update.get(lfile)
            if expecthash:
                if not lfutil.copyfromcache(repo, expecthash, lfile):
                    # failed ... but already removed and set to normallookup
                    continue
                # Synchronize largefile dirstate to the last modified
                # time of the file
                lfdirstate.hacky_extension_update_file(
                    lfile,
                    p1_tracked=True,
                    wc_tracked=True,
                )
                update1 = 1

            # copy the exec mode of largefile standin from the repository's
            # dirstate to its state in the lfdirstate.
            standin = lfutil.standin(lfile)
            if wvfs.exists(standin):
                # exec is decided by the users permissions using mask 0o100
                standinexec = wvfs.stat(standin).st_mode & 0o100
                st = wvfs.stat(lfile)
                mode = st.st_mode
                if standinexec != mode & 0o100:
                    # first remove all X bits, then shift all R bits to X
                    mode &= ~0o111
                    if standinexec:
                        mode |= (mode >> 2) & 0o111 & ~util.umask
                    wvfs.chmod(lfile, mode)
                    update1 = 1

            updated += update1

            lfutil.synclfdirstate(repo, lfdirstate, lfile, normallookup)

        lfdirstate.write(repo.currenttransaction())
        if lfiles:
            statuswriter(
                _(b'%d largefiles updated, %d removed\n') % (updated, removed)
            )


@eh.command(
    b'lfpull',
    [(b'r', b'rev', [], _(b'pull largefiles for these revisions'))]
    + cmdutil.remoteopts,
    _(b'-r REV... [-e CMD] [--remotecmd CMD] [SOURCE]'),
)
def lfpull(ui, repo, source=b"default", **opts):
    """pull largefiles for the specified revisions from the specified source

    Pull largefiles that are referenced from local changesets but missing
    locally, pulling from a remote repository to the local cache.

    If SOURCE is omitted, the 'default' path will be used.
    See :hg:`help urls` for more information.

    .. container:: verbose

      Some examples:

      - pull largefiles for all branch heads::

          hg lfpull -r "head() and not closed()"

      - pull largefiles on the default branch::

          hg lfpull -r "branch(default)"
    """
    repo.lfpullsource = source

    revs = opts.get('rev', [])
    if not revs:
        raise error.Abort(_(b'no revisions specified'))
    revs = logcmdutil.revrange(repo, revs)

    numcached = 0
    for rev in revs:
        ui.note(_(b'pulling largefiles for revision %d\n') % rev)
        (cached, missing) = cachelfiles(ui, repo, rev)
        numcached += len(cached)
    ui.status(_(b"%d largefiles cached\n") % numcached)


@eh.command(b'debuglfput', [] + cmdutil.remoteopts, _(b'FILE'))
def debuglfput(ui, repo, filepath, **kwargs):
    hash = lfutil.hashfile(filepath)
    storefactory.openstore(repo).put(filepath, hash)
    ui.write(b'%s\n' % hash)
    return 0
