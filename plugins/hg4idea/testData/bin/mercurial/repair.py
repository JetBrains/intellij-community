# repair.py - functions for repository repair for mercurial
#
# Copyright 2005, 2006 Chris Mason <mason@suse.com>
# Copyright 2007 Olivia Mackall
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno

from .i18n import _
from .node import (
    hex,
    short,
)
from . import (
    bundle2,
    changegroup,
    discovery,
    error,
    exchange,
    obsolete,
    obsutil,
    pathutil,
    phases,
    pycompat,
    requirements,
    scmutil,
    util,
)
from .utils import (
    hashutil,
    stringutil,
    urlutil,
)


def backupbundle(
    repo, bases, heads, node, suffix, compress=True, obsolescence=True
):
    """create a bundle with the specified revisions as a backup"""

    backupdir = b"strip-backup"
    vfs = repo.vfs
    if not vfs.isdir(backupdir):
        vfs.mkdir(backupdir)

    # Include a hash of all the nodes in the filename for uniqueness
    allcommits = repo.set(b'%ln::%ln', bases, heads)
    allhashes = sorted(c.hex() for c in allcommits)
    totalhash = hashutil.sha1(b''.join(allhashes)).digest()
    name = b"%s/%s-%s-%s.hg" % (
        backupdir,
        short(node),
        hex(totalhash[:4]),
        suffix,
    )

    cgversion = changegroup.localversion(repo)
    comp = None
    if cgversion != b'01':
        bundletype = b"HG20"
        if compress:
            comp = b'BZ'
    elif compress:
        bundletype = b"HG10BZ"
    else:
        bundletype = b"HG10UN"

    outgoing = discovery.outgoing(repo, missingroots=bases, ancestorsof=heads)
    contentopts = {
        b'cg.version': cgversion,
        b'obsolescence': obsolescence,
        b'phases': True,
    }
    return bundle2.writenewbundle(
        repo.ui,
        repo,
        b'strip',
        name,
        bundletype,
        outgoing,
        contentopts,
        vfs,
        compression=comp,
    )


def _collectfiles(repo, striprev):
    """find out the filelogs affected by the strip"""
    files = set()

    for x in pycompat.xrange(striprev, len(repo)):
        files.update(repo[x].files())

    return sorted(files)


def _collectrevlog(revlog, striprev):
    _, brokenset = revlog.getstrippoint(striprev)
    return [revlog.linkrev(r) for r in brokenset]


def _collectbrokencsets(repo, files, striprev):
    """return the changesets which will be broken by the truncation"""
    s = set()

    for revlog in manifestrevlogs(repo):
        s.update(_collectrevlog(revlog, striprev))
    for fname in files:
        s.update(_collectrevlog(repo.file(fname), striprev))

    return s


def strip(ui, repo, nodelist, backup=True, topic=b'backup'):
    # This function requires the caller to lock the repo, but it operates
    # within a transaction of its own, and thus requires there to be no current
    # transaction when it is called.
    if repo.currenttransaction() is not None:
        raise error.ProgrammingError(b'cannot strip from inside a transaction')

    # Simple way to maintain backwards compatibility for this
    # argument.
    if backup in [b'none', b'strip']:
        backup = False

    repo = repo.unfiltered()
    repo.destroying()
    vfs = repo.vfs
    # load bookmark before changelog to avoid side effect from outdated
    # changelog (see repo._refreshchangelog)
    repo._bookmarks
    cl = repo.changelog

    # TODO handle undo of merge sets
    if isinstance(nodelist, bytes):
        nodelist = [nodelist]
    striplist = [cl.rev(node) for node in nodelist]
    striprev = min(striplist)

    files = _collectfiles(repo, striprev)
    saverevs = _collectbrokencsets(repo, files, striprev)

    # Some revisions with rev > striprev may not be descendants of striprev.
    # We have to find these revisions and put them in a bundle, so that
    # we can restore them after the truncations.
    # To create the bundle we use repo.changegroupsubset which requires
    # the list of heads and bases of the set of interesting revisions.
    # (head = revision in the set that has no descendant in the set;
    #  base = revision in the set that has no ancestor in the set)
    tostrip = set(striplist)
    saveheads = set(saverevs)
    for r in cl.revs(start=striprev + 1):
        if any(p in tostrip for p in cl.parentrevs(r)):
            tostrip.add(r)

        if r not in tostrip:
            saverevs.add(r)
            saveheads.difference_update(cl.parentrevs(r))
            saveheads.add(r)
    saveheads = [cl.node(r) for r in saveheads]

    # compute base nodes
    if saverevs:
        descendants = set(cl.descendants(saverevs))
        saverevs.difference_update(descendants)
    savebases = [cl.node(r) for r in saverevs]
    stripbases = [cl.node(r) for r in tostrip]

    stripobsidx = obsmarkers = ()
    if repo.ui.configbool(b'devel', b'strip-obsmarkers'):
        obsmarkers = obsutil.exclusivemarkers(repo, stripbases)
    if obsmarkers:
        stripobsidx = [
            i for i, m in enumerate(repo.obsstore) if m in obsmarkers
        ]

    newbmtarget, updatebm = _bookmarkmovements(repo, tostrip)

    backupfile = None
    node = nodelist[-1]
    if backup:
        backupfile = _createstripbackup(repo, stripbases, node, topic)
    # create a changegroup for all the branches we need to keep
    tmpbundlefile = None
    if saveheads:
        # do not compress temporary bundle if we remove it from disk later
        #
        # We do not include obsolescence, it might re-introduce prune markers
        # we are trying to strip.  This is harmless since the stripped markers
        # are already backed up and we did not touched the markers for the
        # saved changesets.
        tmpbundlefile = backupbundle(
            repo,
            savebases,
            saveheads,
            node,
            b'temp',
            compress=False,
            obsolescence=False,
        )

    with ui.uninterruptible():
        try:
            with repo.transaction(b"strip") as tr:
                # TODO this code violates the interface abstraction of the
                # transaction and makes assumptions that file storage is
                # using append-only files. We'll need some kind of storage
                # API to handle stripping for us.
                oldfiles = set(tr._offsetmap.keys())
                oldfiles.update(tr._newfiles)

                tr.startgroup()
                cl.strip(striprev, tr)
                stripmanifest(repo, striprev, tr, files)

                for fn in files:
                    repo.file(fn).strip(striprev, tr)
                tr.endgroup()

                entries = tr.readjournal()

                for file, troffset in entries:
                    if file in oldfiles:
                        continue
                    with repo.svfs(file, b'a', checkambig=True) as fp:
                        fp.truncate(troffset)
                    if troffset == 0:
                        repo.store.markremoved(file)

                deleteobsmarkers(repo.obsstore, stripobsidx)
                del repo.obsstore
                repo.invalidatevolatilesets()
                repo._phasecache.filterunknown(repo)

            if tmpbundlefile:
                ui.note(_(b"adding branch\n"))
                f = vfs.open(tmpbundlefile, b"rb")
                gen = exchange.readbundle(ui, f, tmpbundlefile, vfs)
                # silence internal shuffling chatter
                maybe_silent = (
                    repo.ui.silent()
                    if not repo.ui.verbose
                    else util.nullcontextmanager()
                )
                with maybe_silent:
                    tmpbundleurl = b'bundle:' + vfs.join(tmpbundlefile)
                    txnname = b'strip'
                    if not isinstance(gen, bundle2.unbundle20):
                        txnname = b"strip\n%s" % urlutil.hidepassword(
                            tmpbundleurl
                        )
                    with repo.transaction(txnname) as tr:
                        bundle2.applybundle(
                            repo, gen, tr, source=b'strip', url=tmpbundleurl
                        )
                f.close()

            with repo.transaction(b'repair') as tr:
                bmchanges = [(m, repo[newbmtarget].node()) for m in updatebm]
                repo._bookmarks.applychanges(repo, tr, bmchanges)

            # remove undo files
            for undovfs, undofile in repo.undofiles():
                try:
                    undovfs.unlink(undofile)
                except OSError as e:
                    if e.errno != errno.ENOENT:
                        ui.warn(
                            _(b'error removing %s: %s\n')
                            % (
                                undovfs.join(undofile),
                                stringutil.forcebytestr(e),
                            )
                        )

        except:  # re-raises
            if backupfile:
                ui.warn(
                    _(b"strip failed, backup bundle stored in '%s'\n")
                    % vfs.join(backupfile)
                )
            if tmpbundlefile:
                ui.warn(
                    _(b"strip failed, unrecovered changes stored in '%s'\n")
                    % vfs.join(tmpbundlefile)
                )
                ui.warn(
                    _(
                        b"(fix the problem, then recover the changesets with "
                        b"\"hg unbundle '%s'\")\n"
                    )
                    % vfs.join(tmpbundlefile)
                )
            raise
        else:
            if tmpbundlefile:
                # Remove temporary bundle only if there were no exceptions
                vfs.unlink(tmpbundlefile)

    repo.destroyed()
    # return the backup file path (or None if 'backup' was False) so
    # extensions can use it
    return backupfile


def softstrip(ui, repo, nodelist, backup=True, topic=b'backup'):
    """perform a "soft" strip using the archived phase"""
    tostrip = [c.node() for c in repo.set(b'sort(%ln::)', nodelist)]
    if not tostrip:
        return None

    backupfile = None
    if backup:
        node = tostrip[0]
        backupfile = _createstripbackup(repo, tostrip, node, topic)

    newbmtarget, updatebm = _bookmarkmovements(repo, tostrip)
    with repo.transaction(b'strip') as tr:
        phases.retractboundary(repo, tr, phases.archived, tostrip)
        bmchanges = [(m, repo[newbmtarget].node()) for m in updatebm]
        repo._bookmarks.applychanges(repo, tr, bmchanges)
    return backupfile


def _bookmarkmovements(repo, tostrip):
    # compute necessary bookmark movement
    bm = repo._bookmarks
    updatebm = []
    for m in bm:
        rev = repo[bm[m]].rev()
        if rev in tostrip:
            updatebm.append(m)
    newbmtarget = None
    # If we need to move bookmarks, compute bookmark
    # targets. Otherwise we can skip doing this logic.
    if updatebm:
        # For a set s, max(parents(s) - s) is the same as max(heads(::s - s)),
        # but is much faster
        newbmtarget = repo.revs(b'max(parents(%ld) - (%ld))', tostrip, tostrip)
        if newbmtarget:
            newbmtarget = repo[newbmtarget.first()].node()
        else:
            newbmtarget = b'.'
    return newbmtarget, updatebm


def _createstripbackup(repo, stripbases, node, topic):
    # backup the changeset we are about to strip
    vfs = repo.vfs
    cl = repo.changelog
    backupfile = backupbundle(repo, stripbases, cl.heads(), node, topic)
    repo.ui.status(_(b"saved backup bundle to %s\n") % vfs.join(backupfile))
    repo.ui.log(
        b"backupbundle", b"saved backup bundle to %s\n", vfs.join(backupfile)
    )
    return backupfile


def safestriproots(ui, repo, nodes):
    """return list of roots of nodes where descendants are covered by nodes"""
    torev = repo.unfiltered().changelog.rev
    revs = {torev(n) for n in nodes}
    # tostrip = wanted - unsafe = wanted - ancestors(orphaned)
    # orphaned = affected - wanted
    # affected = descendants(roots(wanted))
    # wanted = revs
    revset = b'%ld - ( ::( (roots(%ld):: and not _phase(%s)) -%ld) )'
    tostrip = set(repo.revs(revset, revs, revs, phases.internal, revs))
    notstrip = revs - tostrip
    if notstrip:
        nodestr = b', '.join(sorted(short(repo[n].node()) for n in notstrip))
        ui.warn(
            _(b'warning: orphaned descendants detected, not stripping %s\n')
            % nodestr
        )
    return [c.node() for c in repo.set(b'roots(%ld)', tostrip)]


class stripcallback(object):
    """used as a transaction postclose callback"""

    def __init__(self, ui, repo, backup, topic):
        self.ui = ui
        self.repo = repo
        self.backup = backup
        self.topic = topic or b'backup'
        self.nodelist = []

    def addnodes(self, nodes):
        self.nodelist.extend(nodes)

    def __call__(self, tr):
        roots = safestriproots(self.ui, self.repo, self.nodelist)
        if roots:
            strip(self.ui, self.repo, roots, self.backup, self.topic)


def delayedstrip(ui, repo, nodelist, topic=None, backup=True):
    """like strip, but works inside transaction and won't strip irreverent revs

    nodelist must explicitly contain all descendants. Otherwise a warning will
    be printed that some nodes are not stripped.

    Will do a backup if `backup` is True. The last non-None "topic" will be
    used as the backup topic name. The default backup topic name is "backup".
    """
    tr = repo.currenttransaction()
    if not tr:
        nodes = safestriproots(ui, repo, nodelist)
        return strip(ui, repo, nodes, backup=backup, topic=topic)
    # transaction postclose callbacks are called in alphabet order.
    # use '\xff' as prefix so we are likely to be called last.
    callback = tr.getpostclose(b'\xffstrip')
    if callback is None:
        callback = stripcallback(ui, repo, backup=backup, topic=topic)
        tr.addpostclose(b'\xffstrip', callback)
    if topic:
        callback.topic = topic
    callback.addnodes(nodelist)


def stripmanifest(repo, striprev, tr, files):
    for revlog in manifestrevlogs(repo):
        revlog.strip(striprev, tr)


def manifestrevlogs(repo):
    yield repo.manifestlog.getstorage(b'')
    if scmutil.istreemanifest(repo):
        # This logic is safe if treemanifest isn't enabled, but also
        # pointless, so we skip it if treemanifest isn't enabled.
        for t, unencoded, encoded, size in repo.store.datafiles():
            if unencoded.startswith(b'meta/') and unencoded.endswith(
                b'00manifest.i'
            ):
                dir = unencoded[5:-12]
                yield repo.manifestlog.getstorage(dir)


def rebuildfncache(ui, repo):
    """Rebuilds the fncache file from repo history.

    Missing entries will be added. Extra entries will be removed.
    """
    repo = repo.unfiltered()

    if requirements.FNCACHE_REQUIREMENT not in repo.requirements:
        ui.warn(
            _(
                b'(not rebuilding fncache because repository does not '
                b'support fncache)\n'
            )
        )
        return

    with repo.lock():
        fnc = repo.store.fncache
        fnc.ensureloaded(warn=ui.warn)

        oldentries = set(fnc.entries)
        newentries = set()
        seenfiles = set()

        progress = ui.makeprogress(
            _(b'rebuilding'), unit=_(b'changesets'), total=len(repo)
        )
        for rev in repo:
            progress.update(rev)

            ctx = repo[rev]
            for f in ctx.files():
                # This is to minimize I/O.
                if f in seenfiles:
                    continue
                seenfiles.add(f)

                i = b'data/%s.i' % f
                d = b'data/%s.d' % f

                if repo.store._exists(i):
                    newentries.add(i)
                if repo.store._exists(d):
                    newentries.add(d)

        progress.complete()

        if requirements.TREEMANIFEST_REQUIREMENT in repo.requirements:
            # This logic is safe if treemanifest isn't enabled, but also
            # pointless, so we skip it if treemanifest isn't enabled.
            for dir in pathutil.dirs(seenfiles):
                i = b'meta/%s/00manifest.i' % dir
                d = b'meta/%s/00manifest.d' % dir

                if repo.store._exists(i):
                    newentries.add(i)
                if repo.store._exists(d):
                    newentries.add(d)

        addcount = len(newentries - oldentries)
        removecount = len(oldentries - newentries)
        for p in sorted(oldentries - newentries):
            ui.write(_(b'removing %s\n') % p)
        for p in sorted(newentries - oldentries):
            ui.write(_(b'adding %s\n') % p)

        if addcount or removecount:
            ui.write(
                _(b'%d items added, %d removed from fncache\n')
                % (addcount, removecount)
            )
            fnc.entries = newentries
            fnc._dirty = True

            with repo.transaction(b'fncache') as tr:
                fnc.write(tr)
        else:
            ui.write(_(b'fncache already up to date\n'))


def deleteobsmarkers(obsstore, indices):
    """Delete some obsmarkers from obsstore and return how many were deleted

    'indices' is a list of ints which are the indices
    of the markers to be deleted.

    Every invocation of this function completely rewrites the obsstore file,
    skipping the markers we want to be removed. The new temporary file is
    created, remaining markers are written there and on .close() this file
    gets atomically renamed to obsstore, thus guaranteeing consistency."""
    if not indices:
        # we don't want to rewrite the obsstore with the same content
        return

    left = []
    current = obsstore._all
    n = 0
    for i, m in enumerate(current):
        if i in indices:
            n += 1
            continue
        left.append(m)

    newobsstorefile = obsstore.svfs(b'obsstore', b'w', atomictemp=True)
    for bytes in obsolete.encodemarkers(left, True, obsstore._version):
        newobsstorefile.write(bytes)
    newobsstorefile.close()
    return n
