# upgrade.py - functions for in place upgrade of Mercurial repository
#
# Copyright (c) 2016-present, Gregory Szorc
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import stat

from ..i18n import _
from .. import (
    error,
    metadata,
    pycompat,
    requirements,
    scmutil,
    store,
    util,
    vfs as vfsmod,
)
from ..revlogutils import (
    constants as revlogconst,
    flagutil,
    nodemap,
    sidedata as sidedatamod,
)
from . import actions as upgrade_actions


def get_sidedata_helpers(srcrepo, dstrepo):
    use_w = srcrepo.ui.configbool(b'experimental', b'worker.repository-upgrade')
    sequential = pycompat.iswindows or not use_w
    if not sequential:
        srcrepo.register_sidedata_computer(
            revlogconst.KIND_CHANGELOG,
            sidedatamod.SD_FILES,
            (sidedatamod.SD_FILES,),
            metadata._get_worker_sidedata_adder(srcrepo, dstrepo),
            flagutil.REVIDX_HASCOPIESINFO,
            replace=True,
        )
    return sidedatamod.get_sidedata_helpers(srcrepo, dstrepo._wanted_sidedata)


def _copyrevlog(tr, destrepo, oldrl, entry):
    """copy all relevant files for `oldrl` into `destrepo` store

    Files are copied "as is" without any transformation. The copy is performed
    without extra checks. Callers are responsible for making sure the copied
    content is compatible with format of the destination repository.
    """
    oldrl = getattr(oldrl, '_revlog', oldrl)
    newrl = entry.get_revlog_instance(destrepo)
    newrl = getattr(newrl, '_revlog', newrl)

    oldvfs = oldrl.opener
    newvfs = newrl.opener
    oldindex = oldvfs.join(oldrl._indexfile)
    newindex = newvfs.join(newrl._indexfile)
    olddata = oldvfs.join(oldrl._datafile)
    newdata = newvfs.join(newrl._datafile)

    with newvfs(newrl._indexfile, b'w'):
        pass  # create all the directories

    util.copyfile(oldindex, newindex)
    copydata = oldrl.opener.exists(oldrl._datafile)
    if copydata:
        util.copyfile(olddata, newdata)

    if entry.is_filelog:
        unencodedname = entry.main_file_path()
        destrepo.svfs.fncache.add(unencodedname)
        if copydata:
            destrepo.svfs.fncache.add(unencodedname[:-2] + b'.d')


UPGRADE_CHANGELOG = b"changelog"
UPGRADE_MANIFEST = b"manifest"
UPGRADE_FILELOGS = b"all-filelogs"

UPGRADE_ALL_REVLOGS = frozenset(
    [UPGRADE_CHANGELOG, UPGRADE_MANIFEST, UPGRADE_FILELOGS]
)


def matchrevlog(revlogfilter, entry):
    """check if a revlog is selected for cloning.

    In other words, are there any updates which need to be done on revlog
    or it can be blindly copied.

    The store entry is checked against the passed filter"""
    if entry.is_changelog:
        return UPGRADE_CHANGELOG in revlogfilter
    elif entry.is_manifestlog:
        return UPGRADE_MANIFEST in revlogfilter
    assert entry.is_filelog
    return UPGRADE_FILELOGS in revlogfilter


def _perform_clone(
    ui,
    dstrepo,
    tr,
    old_revlog,
    entry,
    upgrade_op,
    sidedata_helpers,
    oncopiedrevision,
):
    """returns the new revlog object created"""
    newrl = None
    revlog_path = entry.main_file_path()
    if matchrevlog(upgrade_op.revlogs_to_process, entry):
        ui.note(
            _(b'cloning %d revisions from %s\n')
            % (len(old_revlog), revlog_path)
        )
        newrl = entry.get_revlog_instance(dstrepo)
        old_revlog.clone(
            tr,
            newrl,
            addrevisioncb=oncopiedrevision,
            deltareuse=upgrade_op.delta_reuse_mode,
            forcedeltabothparents=upgrade_op.force_re_delta_both_parents,
            sidedata_helpers=sidedata_helpers,
        )
    else:
        msg = _(b'blindly copying %s containing %i revisions\n')
        ui.note(msg % (revlog_path, len(old_revlog)))
        _copyrevlog(tr, dstrepo, old_revlog, entry)

        newrl = entry.get_revlog_instance(dstrepo)
    return newrl


def _clonerevlogs(
    ui,
    srcrepo,
    dstrepo,
    tr,
    upgrade_op,
):
    """Copy revlogs between 2 repos."""
    revcount = 0
    srcsize = 0
    srcrawsize = 0
    dstsize = 0
    fcount = 0
    frevcount = 0
    fsrcsize = 0
    frawsize = 0
    fdstsize = 0
    mcount = 0
    mrevcount = 0
    msrcsize = 0
    mrawsize = 0
    mdstsize = 0
    crevcount = 0
    csrcsize = 0
    crawsize = 0
    cdstsize = 0

    alldatafiles = list(srcrepo.store.walk())
    # mapping of data files which needs to be cloned
    # key is unencoded filename
    # value is revlog_object_from_srcrepo
    manifests = {}
    changelogs = {}
    filelogs = {}

    # Perform a pass to collect metadata. This validates we can open all
    # source files and allows a unified progress bar to be displayed.
    for entry in alldatafiles:
        if not entry.is_revlog:
            continue

        rl = entry.get_revlog_instance(srcrepo)

        info = rl.storageinfo(
            exclusivefiles=True,
            revisionscount=True,
            trackedsize=True,
            storedsize=True,
        )

        revcount += info[b'revisionscount'] or 0
        datasize = info[b'storedsize'] or 0
        rawsize = info[b'trackedsize'] or 0

        srcsize += datasize
        srcrawsize += rawsize

        # This is for the separate progress bars.
        if entry.is_changelog:
            changelogs[entry.target_id] = entry
            crevcount += len(rl)
            csrcsize += datasize
            crawsize += rawsize
        elif entry.is_manifestlog:
            manifests[entry.target_id] = entry
            mcount += 1
            mrevcount += len(rl)
            msrcsize += datasize
            mrawsize += rawsize
        elif entry.is_filelog:
            filelogs[entry.target_id] = entry
            fcount += 1
            frevcount += len(rl)
            fsrcsize += datasize
            frawsize += rawsize
        else:
            error.ProgrammingError(b'unknown revlog type')

    if not revcount:
        return

    ui.status(
        _(
            b'migrating %d total revisions (%d in filelogs, %d in manifests, '
            b'%d in changelog)\n'
        )
        % (revcount, frevcount, mrevcount, crevcount)
    )
    ui.status(
        _(b'migrating %s in store; %s tracked data\n')
        % ((util.bytecount(srcsize), util.bytecount(srcrawsize)))
    )

    # Used to keep track of progress.
    progress = None

    def oncopiedrevision(rl, rev, node):
        progress.increment()

    sidedata_helpers = get_sidedata_helpers(srcrepo, dstrepo)

    # Migrating filelogs
    ui.status(
        _(
            b'migrating %d filelogs containing %d revisions '
            b'(%s in store; %s tracked data)\n'
        )
        % (
            fcount,
            frevcount,
            util.bytecount(fsrcsize),
            util.bytecount(frawsize),
        )
    )
    progress = srcrepo.ui.makeprogress(_(b'file revisions'), total=frevcount)
    for target_id, entry in sorted(filelogs.items()):
        oldrl = entry.get_revlog_instance(srcrepo)

        newrl = _perform_clone(
            ui,
            dstrepo,
            tr,
            oldrl,
            entry,
            upgrade_op,
            sidedata_helpers,
            oncopiedrevision,
        )
        info = newrl.storageinfo(storedsize=True)
        fdstsize += info[b'storedsize'] or 0
    ui.status(
        _(
            b'finished migrating %d filelog revisions across %d '
            b'filelogs; change in size: %s\n'
        )
        % (frevcount, fcount, util.bytecount(fdstsize - fsrcsize))
    )

    # Migrating manifests
    ui.status(
        _(
            b'migrating %d manifests containing %d revisions '
            b'(%s in store; %s tracked data)\n'
        )
        % (
            mcount,
            mrevcount,
            util.bytecount(msrcsize),
            util.bytecount(mrawsize),
        )
    )
    if progress:
        progress.complete()
    progress = srcrepo.ui.makeprogress(
        _(b'manifest revisions'), total=mrevcount
    )
    for target_id, entry in sorted(manifests.items()):
        oldrl = entry.get_revlog_instance(srcrepo)
        newrl = _perform_clone(
            ui,
            dstrepo,
            tr,
            oldrl,
            entry,
            upgrade_op,
            sidedata_helpers,
            oncopiedrevision,
        )
        info = newrl.storageinfo(storedsize=True)
        mdstsize += info[b'storedsize'] or 0
    ui.status(
        _(
            b'finished migrating %d manifest revisions across %d '
            b'manifests; change in size: %s\n'
        )
        % (mrevcount, mcount, util.bytecount(mdstsize - msrcsize))
    )

    # Migrating changelog
    ui.status(
        _(
            b'migrating changelog containing %d revisions '
            b'(%s in store; %s tracked data)\n'
        )
        % (
            crevcount,
            util.bytecount(csrcsize),
            util.bytecount(crawsize),
        )
    )
    if progress:
        progress.complete()
    progress = srcrepo.ui.makeprogress(
        _(b'changelog revisions'), total=crevcount
    )
    for target_id, entry in sorted(changelogs.items()):
        oldrl = entry.get_revlog_instance(srcrepo)
        newrl = _perform_clone(
            ui,
            dstrepo,
            tr,
            oldrl,
            entry,
            upgrade_op,
            sidedata_helpers,
            oncopiedrevision,
        )
        info = newrl.storageinfo(storedsize=True)
        cdstsize += info[b'storedsize'] or 0
    progress.complete()
    ui.status(
        _(
            b'finished migrating %d changelog revisions; change in size: '
            b'%s\n'
        )
        % (crevcount, util.bytecount(cdstsize - csrcsize))
    )

    dstsize = fdstsize + mdstsize + cdstsize
    ui.status(
        _(
            b'finished migrating %d total revisions; total change in store '
            b'size: %s\n'
        )
        % (revcount, util.bytecount(dstsize - srcsize))
    )


def _files_to_copy_post_revlog_clone(srcrepo):
    """yields files which should be copied to destination after revlogs
    are cloned"""
    for path, kind, st in sorted(srcrepo.store.vfs.readdir(b'', stat=True)):
        # don't copy revlogs as they are already cloned
        if store.is_revlog_file(path):
            continue
        # Skip transaction related files.
        if path.startswith(b'undo'):
            continue
        # Only copy regular files.
        if kind != stat.S_IFREG:
            continue
        # Skip other skipped files.
        if path in (b'lock', b'fncache'):
            continue
        # TODO: should we skip cache too?

        yield path


def _replacestores(currentrepo, upgradedrepo, backupvfs, upgrade_op):
    """Replace the stores after current repository is upgraded

    Creates a backup of current repository store at backup path
    Replaces upgraded store files in current repo from upgraded one

    Arguments:
      currentrepo: repo object of current repository
      upgradedrepo: repo object of the upgraded data
      backupvfs: vfs object for the backup path
      upgrade_op: upgrade operation object
                  to be used to decide what all is upgraded
    """
    # TODO: don't blindly rename everything in store
    # There can be upgrades where store is not touched at all
    if upgrade_op.backup_store:
        util.rename(currentrepo.spath, backupvfs.join(b'store'))
    else:
        currentrepo.vfs.rmtree(b'store', forcibly=True)
    util.rename(upgradedrepo.spath, currentrepo.spath)


def finishdatamigration(ui, srcrepo, dstrepo, requirements):
    """Hook point for extensions to perform additional actions during upgrade.

    This function is called after revlogs and store files have been copied but
    before the new store is swapped into the original location.
    """


def upgrade(ui, srcrepo, dstrepo, upgrade_op):
    """Do the low-level work of upgrading a repository.

    The upgrade is effectively performed as a copy between a source
    repository and a temporary destination repository.

    The source repository is unmodified for as long as possible so the
    upgrade can abort at any time without causing loss of service for
    readers and without corrupting the source repository.
    """
    assert srcrepo.currentwlock()
    assert dstrepo.currentwlock()
    backuppath = None
    backupvfs = None

    ui.status(
        _(
            b'(it is safe to interrupt this process any time before '
            b'data migration completes)\n'
        )
    )

    if upgrade_actions.dirstatev2 in upgrade_op.upgrade_actions:
        ui.status(_(b'upgrading to dirstate-v2 from v1\n'))
        upgrade_dirstate(ui, srcrepo, upgrade_op, b'v1', b'v2')
        upgrade_op.upgrade_actions.remove(upgrade_actions.dirstatev2)

    if upgrade_actions.dirstatev2 in upgrade_op.removed_actions:
        ui.status(_(b'downgrading from dirstate-v2 to v1\n'))
        upgrade_dirstate(ui, srcrepo, upgrade_op, b'v2', b'v1')
        upgrade_op.removed_actions.remove(upgrade_actions.dirstatev2)

    if upgrade_actions.dirstatetrackedkey in upgrade_op.upgrade_actions:
        ui.status(_(b'create dirstate-tracked-hint file\n'))
        upgrade_tracked_hint(ui, srcrepo, upgrade_op, add=True)
        upgrade_op.upgrade_actions.remove(upgrade_actions.dirstatetrackedkey)
    elif upgrade_actions.dirstatetrackedkey in upgrade_op.removed_actions:
        ui.status(_(b'remove dirstate-tracked-hint file\n'))
        upgrade_tracked_hint(ui, srcrepo, upgrade_op, add=False)
        upgrade_op.removed_actions.remove(upgrade_actions.dirstatetrackedkey)

    if not (upgrade_op.upgrade_actions or upgrade_op.removed_actions):
        return

    if upgrade_op.requirements_only:
        ui.status(_(b'upgrading repository requirements\n'))
        scmutil.writereporequirements(srcrepo, upgrade_op.new_requirements)
    # if there is only one action and that is persistent nodemap upgrade
    # directly write the nodemap file and update requirements instead of going
    # through the whole cloning process
    elif (
        len(upgrade_op.upgrade_actions) == 1
        and b'persistent-nodemap' in upgrade_op.upgrade_actions_names
        and not upgrade_op.removed_actions
    ):
        ui.status(
            _(b'upgrading repository to use persistent nodemap feature\n')
        )
        with srcrepo.transaction(b'upgrade') as tr:
            unfi = srcrepo.unfiltered()
            cl = unfi.changelog
            nodemap.persist_nodemap(tr, cl, force=True)
            # we want to directly operate on the underlying revlog to force
            # create a nodemap file. This is fine since this is upgrade code
            # and it heavily relies on repository being revlog based
            # hence accessing private attributes can be justified
            nodemap.persist_nodemap(
                tr, unfi.manifestlog._rootstore._revlog, force=True
            )
        scmutil.writereporequirements(srcrepo, upgrade_op.new_requirements)
    elif (
        len(upgrade_op.removed_actions) == 1
        and [
            x
            for x in upgrade_op.removed_actions
            if x.name == b'persistent-nodemap'
        ]
        and not upgrade_op.upgrade_actions
    ):
        ui.status(
            _(b'downgrading repository to not use persistent nodemap feature\n')
        )
        with srcrepo.transaction(b'upgrade') as tr:
            unfi = srcrepo.unfiltered()
            cl = unfi.changelog
            nodemap.delete_nodemap(tr, srcrepo, cl)
            # check comment 20 lines above for accessing private attributes
            nodemap.delete_nodemap(
                tr, srcrepo, unfi.manifestlog._rootstore._revlog
            )
        scmutil.writereporequirements(srcrepo, upgrade_op.new_requirements)
    else:
        with dstrepo.transaction(b'upgrade') as tr:
            _clonerevlogs(
                ui,
                srcrepo,
                dstrepo,
                tr,
                upgrade_op,
            )

        # Now copy other files in the store directory.
        for p in _files_to_copy_post_revlog_clone(srcrepo):
            srcrepo.ui.status(_(b'copying %s\n') % p)
            src = srcrepo.store.rawvfs.join(p)
            dst = dstrepo.store.rawvfs.join(p)
            util.copyfile(src, dst, copystat=True)

        finishdatamigration(ui, srcrepo, dstrepo, requirements)

        ui.status(_(b'data fully upgraded in a temporary repository\n'))

        if upgrade_op.backup_store:
            backuppath = pycompat.mkdtemp(
                prefix=b'upgradebackup.', dir=srcrepo.path
            )
            backupvfs = vfsmod.vfs(backuppath)

            # Make a backup of requires file first, as it is the first to be modified.
            util.copyfile(
                srcrepo.vfs.join(b'requires'), backupvfs.join(b'requires')
            )

        # We install an arbitrary requirement that clients must not support
        # as a mechanism to lock out new clients during the data swap. This is
        # better than allowing a client to continue while the repository is in
        # an inconsistent state.
        ui.status(
            _(
                b'marking source repository as being upgraded; clients will be '
                b'unable to read from repository\n'
            )
        )
        scmutil.writereporequirements(
            srcrepo, srcrepo.requirements | {b'upgradeinprogress'}
        )

        ui.status(_(b'starting in-place swap of repository data\n'))
        if upgrade_op.backup_store:
            ui.status(
                _(b'replaced files will be backed up at %s\n') % backuppath
            )

        # Now swap in the new store directory. Doing it as a rename should make
        # the operation nearly instantaneous and atomic (at least in well-behaved
        # environments).
        ui.status(_(b'replacing store...\n'))
        tstart = util.timer()
        _replacestores(srcrepo, dstrepo, backupvfs, upgrade_op)
        elapsed = util.timer() - tstart
        ui.status(
            _(
                b'store replacement complete; repository was inconsistent for '
                b'%0.1fs\n'
            )
            % elapsed
        )

        # We first write the requirements file. Any new requirements will lock
        # out legacy clients.
        ui.status(
            _(
                b'finalizing requirements file and making repository readable '
                b'again\n'
            )
        )
        scmutil.writereporequirements(srcrepo, upgrade_op.new_requirements)

        if upgrade_op.backup_store:
            # The lock file from the old store won't be removed because nothing has a
            # reference to its new location. So clean it up manually. Alternatively, we
            # could update srcrepo.svfs and other variables to point to the new
            # location. This is simpler.
            assert backupvfs is not None  # help pytype
            backupvfs.unlink(b'store/lock')

    return backuppath


def upgrade_dirstate(ui, srcrepo, upgrade_op, old, new):
    if upgrade_op.backup_store:
        backuppath = pycompat.mkdtemp(
            prefix=b'upgradebackup.', dir=srcrepo.path
        )
        ui.status(_(b'replaced files will be backed up at %s\n') % backuppath)
        backupvfs = vfsmod.vfs(backuppath)
        util.copyfile(
            srcrepo.vfs.join(b'requires'), backupvfs.join(b'requires')
        )
        try:
            util.copyfile(
                srcrepo.vfs.join(b'dirstate'), backupvfs.join(b'dirstate')
            )
        except FileNotFoundError:
            # The dirstate does not exist on an empty repo or a repo with no
            # revision checked out
            pass

    assert srcrepo.dirstate._use_dirstate_v2 == (old == b'v2')
    use_v2 = new == b'v2'
    if use_v2:
        # Write the requirements *before* upgrading
        scmutil.writereporequirements(srcrepo, upgrade_op.new_requirements)

    srcrepo.dirstate._map.preload()
    srcrepo.dirstate._use_dirstate_v2 = use_v2
    srcrepo.dirstate._map._use_dirstate_v2 = use_v2
    srcrepo.dirstate._dirty = True
    try:
        srcrepo.vfs.unlink(b'dirstate')
    except FileNotFoundError:
        # The dirstate does not exist on an empty repo or a repo with no
        # revision checked out
        pass

    srcrepo.dirstate.write(None)
    if not use_v2:
        # Remove the v2 requirement *after* downgrading
        scmutil.writereporequirements(srcrepo, upgrade_op.new_requirements)


def upgrade_tracked_hint(ui, srcrepo, upgrade_op, add):
    if add:
        srcrepo.dirstate._use_tracked_hint = True
        srcrepo.dirstate._dirty = True
        srcrepo.dirstate._dirty_tracked_set = True
        srcrepo.dirstate.write(None)
    if not add:
        srcrepo.dirstate.delete_tracked_hint()

    scmutil.writereporequirements(srcrepo, upgrade_op.new_requirements)
