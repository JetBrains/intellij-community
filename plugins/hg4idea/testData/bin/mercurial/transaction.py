# transaction.py - simple journaling scheme for mercurial
#
# This transaction scheme is intended to gracefully handle program
# errors and interruptions. More serious failures like system crashes
# can be recovered with an fsck-like tool. As the whole repository is
# effectively log-structured, this should amount to simply truncating
# anything that isn't referenced in the changelog.
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import errno
import os

from .i18n import _
from . import (
    encoding,
    error,
    pycompat,
    util,
)
from .utils import stringutil

version = 2

GEN_GROUP_ALL = b'all'
GEN_GROUP_PRE_FINALIZE = b'prefinalize'
GEN_GROUP_POST_FINALIZE = b'postfinalize'


def active(func):
    def _active(self, *args, **kwds):
        if self._count == 0:
            raise error.ProgrammingError(
                b'cannot use transaction when it is already committed/aborted'
            )
        return func(self, *args, **kwds)

    return _active


UNDO_BACKUP = b'%s.backupfiles'

UNDO_FILES_MAY_NEED_CLEANUP = [
    # legacy entries that might exists on disk from previous version:
    (b'store', b'%s.narrowspec'),
    (b'plain', b'%s.narrowspec.dirstate'),
    (b'plain', b'%s.branch'),
    (b'plain', b'%s.bookmarks'),
    (b'store', b'%s.phaseroots'),
    (b'plain', b'%s.dirstate'),
    # files actually in uses today:
    (b'plain', b'%s.desc'),
    # Always delete undo last to make sure we detect that a clean up is needed if
    # the process is interrupted.
    (b'store', b'%s'),
]


def has_abandoned_transaction(repo):
    """Return True if the repo has an abandoned transaction"""
    return os.path.exists(repo.sjoin(b"journal"))


def cleanup_undo_files(report, vfsmap, undo_prefix=b'undo'):
    """remove "undo" files used by the rollback logic

    This is useful to prevent rollback running in situation were it does not
    make sense. For example after a strip.
    """
    backup_listing = UNDO_BACKUP % undo_prefix

    backup_entries = []
    undo_files = []
    svfs = vfsmap[b'store']
    try:
        with svfs(backup_listing) as f:
            backup_entries = read_backup_files(report, f)
    except OSError as e:
        if e.errno != errno.ENOENT:
            msg = _(b'could not read %s: %s\n')
            msg %= (svfs.join(backup_listing), stringutil.forcebytestr(e))
            report(msg)

    for location, f, backup_path, c in backup_entries:
        if location in vfsmap and backup_path:
            undo_files.append((vfsmap[location], backup_path))

    undo_files.append((svfs, backup_listing))
    for location, undo_path in UNDO_FILES_MAY_NEED_CLEANUP:
        undo_files.append((vfsmap[location], undo_path % undo_prefix))
    for undovfs, undofile in undo_files:
        try:
            undovfs.unlink(undofile)
        except OSError as e:
            if e.errno != errno.ENOENT:
                msg = _(b'error removing %s: %s\n')
                msg %= (undovfs.join(undofile), stringutil.forcebytestr(e))
                report(msg)


def _playback(
    journal,
    report,
    opener,
    vfsmap,
    entries,
    backupentries,
    unlink=True,
    checkambigfiles=None,
):
    """rollback a transaction :
    - truncate files that have been appended to
    - restore file backups
    - delete temporary files
    """
    backupfiles = []

    def restore_one_backup(vfs, f, b, checkambig):
        filepath = vfs.join(f)
        backuppath = vfs.join(b)
        try:
            util.copyfile(backuppath, filepath, checkambig=checkambig)
            backupfiles.append((vfs, b))
        except IOError as exc:
            e_msg = stringutil.forcebytestr(exc)
            report(_(b"failed to recover %s (%s)\n") % (f, e_msg))
            raise

    # gather all backup files that impact the store
    # (we need this to detect files that are both backed up and truncated)
    store_backup = {}
    for entry in backupentries:
        location, file_path, backup_path, cache = entry
        vfs = vfsmap[location]
        is_store = vfs.join(b'') == opener.join(b'')
        if is_store and file_path and backup_path:
            store_backup[file_path] = entry
    copy_done = set()

    # truncate all file `f` to offset `o`
    for f, o in sorted(dict(entries).items()):
        # if we have a backup for `f`, we should restore it first and truncate
        # the restored file
        bck_entry = store_backup.get(f)
        if bck_entry is not None:
            location, file_path, backup_path, cache = bck_entry
            checkambig = False
            if checkambigfiles:
                checkambig = (file_path, location) in checkambigfiles
            restore_one_backup(opener, file_path, backup_path, checkambig)
            copy_done.add(bck_entry)
        # truncate the file to its pre-transaction size
        if o or not unlink:
            checkambig = checkambigfiles and (f, b'') in checkambigfiles
            try:
                fp = opener(f, b'a', checkambig=checkambig)
                if fp.tell() < o:
                    raise error.Abort(
                        _(
                            b"attempted to truncate %s to %d bytes, but it was "
                            b"already %d bytes\n"
                        )
                        % (f, o, fp.tell())
                    )
                fp.truncate(o)
                fp.close()
            except IOError:
                report(_(b"failed to truncate %s\n") % f)
                raise
        else:
            # delete empty file
            try:
                opener.unlink(f)
            except FileNotFoundError:
                pass
    # restore backed up files and clean up temporary files
    for entry in backupentries:
        if entry in copy_done:
            continue
        l, f, b, c = entry
        if l not in vfsmap and c:
            report(b"couldn't handle %s: unknown cache location %s\n" % (b, l))
        vfs = vfsmap[l]
        try:
            checkambig = checkambigfiles and (f, l) in checkambigfiles
            if f and b:
                restore_one_backup(vfs, f, b, checkambig)
            else:
                target = f or b
                try:
                    vfs.unlink(target)
                except FileNotFoundError:
                    # This is fine because
                    #
                    # either we are trying to delete the main file, and it is
                    # already deleted.
                    #
                    # or we are trying to delete a temporary file and it is
                    # already deleted.
                    #
                    # in both case, our target result (delete the file) is
                    # already achieved.
                    pass
        except (IOError, OSError, error.Abort):
            if not c:
                raise

    # cleanup transaction state file and the backups file
    backuppath = b"%s.backupfiles" % journal
    if opener.exists(backuppath):
        opener.unlink(backuppath)
    opener.unlink(journal)
    try:
        for vfs, f in backupfiles:
            if vfs.exists(f):
                vfs.unlink(f)
    except (IOError, OSError, error.Abort):
        # only pure backup file remains, it is sage to ignore any error
        pass


class transaction(util.transactional):
    def __init__(
        self,
        report,
        opener,
        vfsmap,
        journalname,
        undoname=None,
        after=None,
        createmode=None,
        validator=None,
        releasefn=None,
        checkambigfiles=None,
        name=b'<unnamed>',
    ):
        """Begin a new transaction

        Begins a new transaction that allows rolling back writes in the event of
        an exception.

        * `after`: called after the transaction has been committed
        * `createmode`: the mode of the journal file that will be created
        * `releasefn`: called after releasing (with transaction and result)

        `checkambigfiles` is a set of (path, vfs-location) tuples,
        which determine whether file stat ambiguity should be avoided
        for corresponded files.
        """
        self._count = 1
        self._usages = 1
        self._report = report
        # a vfs to the store content
        self._opener = opener
        # a map to access file in various {location -> vfs}
        vfsmap = vfsmap.copy()
        vfsmap[b''] = opener  # set default value
        self._vfsmap = vfsmap
        self._after = after
        self._offsetmap = {}
        self._newfiles = set()
        self._journal = journalname
        self._journal_files = []
        self._undoname = undoname
        self._queue = []
        # A callback to do something just after releasing transaction.
        if releasefn is None:
            releasefn = lambda tr, success: None
        self._releasefn = releasefn

        self._checkambigfiles = set()
        if checkambigfiles:
            self._checkambigfiles.update(checkambigfiles)

        self._names = [name]

        # A dict dedicated to precisely tracking the changes introduced in the
        # transaction.
        self.changes = {}

        # a dict of arguments to be passed to hooks
        self.hookargs = {}
        self._file = opener.open(self._journal, b"w+")

        # a list of ('location', 'path', 'backuppath', cache) entries.
        # - if 'backuppath' is empty, no file existed at backup time
        # - if 'path' is empty, this is a temporary transaction file
        # - if 'location' is not empty, the path is outside main opener reach.
        #   use 'location' value as a key in a vfsmap to find the right 'vfs'
        # (cache is currently unused)
        self._backupentries = []
        self._backupmap = {}
        self._backupjournal = b"%s.backupfiles" % self._journal
        self._backupsfile = opener.open(self._backupjournal, b'w')
        self._backupsfile.write(b'%d\n' % version)
        # the set of temporary files
        self._tmp_files = set()

        if createmode is not None:
            opener.chmod(self._journal, createmode & 0o666)
            opener.chmod(self._backupjournal, createmode & 0o666)

        # hold file generations to be performed on commit
        self._filegenerators = {}
        # hold callback to write pending data for hooks
        self._pendingcallback = {}
        # True is any pending data have been written ever
        self._anypending = False
        # holds callback to call when writing the transaction
        self._finalizecallback = {}
        # holds callback to call when validating the transaction
        # should raise exception if anything is wrong
        self._validatecallback = {}
        if validator is not None:
            self._validatecallback[b'001-userhooks'] = validator
        # hold callback for post transaction close
        self._postclosecallback = {}
        # holds callbacks to call during abort
        self._abortcallback = {}

    def __repr__(self):
        name = b'/'.join(self._names)
        return '<transaction name=%s, count=%d, usages=%d>' % (
            encoding.strfromlocal(name),
            self._count,
            self._usages,
        )

    def __del__(self):
        if self._journal:
            self._abort()

    @property
    def finalized(self):
        return self._finalizecallback is None

    @active
    def startgroup(self):
        """delay registration of file entry

        This is used by strip to delay vision of strip offset. The transaction
        sees either none or all of the strip actions to be done."""
        self._queue.append([])

    @active
    def endgroup(self):
        """apply delayed registration of file entry.

        This is used by strip to delay vision of strip offset. The transaction
        sees either none or all of the strip actions to be done."""
        q = self._queue.pop()
        for f, o in q:
            self._addentry(f, o)

    @active
    def add(self, file, offset):
        """record the state of an append-only file before update"""
        if (
            file in self._newfiles
            or file in self._offsetmap
            or file in self._backupmap
            or file in self._tmp_files
        ):
            return
        if self._queue:
            self._queue[-1].append((file, offset))
            return

        self._addentry(file, offset)

    def _addentry(self, file, offset):
        """add a append-only entry to memory and on-disk state"""
        if (
            file in self._newfiles
            or file in self._offsetmap
            or file in self._backupmap
            or file in self._tmp_files
        ):
            return
        if offset:
            self._offsetmap[file] = offset
        else:
            self._newfiles.add(file)
        # add enough data to the journal to do the truncate
        self._file.write(b"%s\0%d\n" % (file, offset))
        self._file.flush()

    @active
    def addbackup(self, file, hardlink=True, location=b'', for_offset=False):
        """Adds a backup of the file to the transaction

        Calling addbackup() creates a hardlink backup of the specified file
        that is used to recover the file in the event of the transaction
        aborting.

        * `file`: the file path, relative to .hg/store
        * `hardlink`: use a hardlink to quickly create the backup

        If `for_offset` is set, we expect a offset for this file to have been previously recorded
        """
        if self._queue:
            msg = b'cannot use transaction.addbackup inside "group"'
            raise error.ProgrammingError(msg)

        if file in self._newfiles or file in self._backupmap:
            return
        elif file in self._offsetmap and not for_offset:
            return
        elif for_offset and file not in self._offsetmap:
            msg = (
                'calling `addbackup` with `for_offmap=True`, '
                'but no offset recorded: [%r] %r'
            )
            msg %= (location, file)
            raise error.ProgrammingError(msg)

        vfs = self._vfsmap[location]
        dirname, filename = vfs.split(file)
        backupfilename = b"%s.backup.%s.bck" % (self._journal, filename)
        backupfile = vfs.reljoin(dirname, backupfilename)
        if vfs.exists(file):
            filepath = vfs.join(file)
            backuppath = vfs.join(backupfile)
            # store encoding may result in different directory here.
            # so we have to ensure the destination directory exist
            final_dir_name = os.path.dirname(backuppath)
            util.makedirs(final_dir_name, mode=vfs.createmode, notindexed=True)
            # then we can copy the backup
            util.copyfile(filepath, backuppath, hardlink=hardlink)
        else:
            backupfile = b''

        self._addbackupentry((location, file, backupfile, False))

    def _addbackupentry(self, entry):
        """register a new backup entry and write it to disk"""
        self._backupentries.append(entry)
        self._backupmap[entry[1]] = len(self._backupentries) - 1
        self._backupsfile.write(b"%s\0%s\0%s\0%d\n" % entry)
        self._backupsfile.flush()

    @active
    def registertmp(self, tmpfile, location=b''):
        """register a temporary transaction file

        Such files will be deleted when the transaction exits (on both
        failure and success).
        """
        self._tmp_files.add(tmpfile)
        self._addbackupentry((location, b'', tmpfile, False))

    @active
    def addfilegenerator(
        self,
        genid,
        filenames,
        genfunc,
        order=0,
        location=b'',
        post_finalize=False,
    ):
        """add a function to generates some files at transaction commit

        The `genfunc` argument is a function capable of generating proper
        content of each entry in the `filename` tuple.

        At transaction close time, `genfunc` will be called with one file
        object argument per entries in `filenames`.

        The transaction itself is responsible for the backup, creation and
        final write of such file.

        The `genid` argument is used to ensure the same set of file is only
        generated once. Call to `addfilegenerator` for a `genid` already
        present will overwrite the old entry.

        The `order` argument may be used to control the order in which multiple
        generator will be executed.

        The `location` arguments may be used to indicate the files are located
        outside of the the standard directory for transaction. It should match
        one of the key of the `transaction.vfsmap` dictionary.

        The `post_finalize` argument can be set to `True` for file generation
        that must be run after the transaction has been finalized.
        """
        # For now, we are unable to do proper backup and restore of custom vfs
        # but for bookmarks that are handled outside this mechanism.
        entry = (order, filenames, genfunc, location, post_finalize)
        self._filegenerators[genid] = entry

    @active
    def removefilegenerator(self, genid):
        """reverse of addfilegenerator, remove a file generator function"""
        if genid in self._filegenerators:
            del self._filegenerators[genid]

    def _generatefiles(self, suffix=b'', group=GEN_GROUP_ALL):
        # write files registered for generation
        any = False

        if group == GEN_GROUP_ALL:
            skip_post = skip_pre = False
        else:
            skip_pre = group == GEN_GROUP_POST_FINALIZE
            skip_post = group == GEN_GROUP_PRE_FINALIZE

        for id, entry in sorted(self._filegenerators.items()):
            any = True
            order, filenames, genfunc, location, post_finalize = entry

            # for generation at closing, check if it's before or after finalize
            if skip_post and post_finalize:
                continue
            elif skip_pre and not post_finalize:
                continue

            vfs = self._vfsmap[location]
            files = []
            try:
                for name in filenames:
                    name += suffix
                    if suffix:
                        self.registertmp(name, location=location)
                        checkambig = False
                    else:
                        self.addbackup(name, location=location)
                        checkambig = (name, location) in self._checkambigfiles
                    files.append(
                        vfs(name, b'w', atomictemp=True, checkambig=checkambig)
                    )
                genfunc(*files)
                for f in files:
                    f.close()
                # skip discard() loop since we're sure no open file remains
                del files[:]
            finally:
                for f in files:
                    f.discard()
        return any

    @active
    def findoffset(self, file):
        if file in self._newfiles:
            return 0
        return self._offsetmap.get(file)

    @active
    def readjournal(self):
        self._file.seek(0)
        entries = []
        for l in self._file.readlines():
            file, troffset = l.split(b'\0')
            entries.append((file, int(troffset)))
        return entries

    @active
    def replace(self, file, offset):
        """
        replace can only replace already committed entries
        that are not pending in the queue
        """
        if file in self._newfiles:
            if not offset:
                return
            self._newfiles.remove(file)
            self._offsetmap[file] = offset
        elif file in self._offsetmap:
            if not offset:
                del self._offsetmap[file]
                self._newfiles.add(file)
            else:
                self._offsetmap[file] = offset
        else:
            raise KeyError(file)
        self._file.write(b"%s\0%d\n" % (file, offset))
        self._file.flush()

    @active
    def nest(self, name=b'<unnamed>'):
        self._count += 1
        self._usages += 1
        self._names.append(name)
        return self

    def release(self):
        if self._count > 0:
            self._usages -= 1
        if self._names:
            self._names.pop()
        # if the transaction scopes are left without being closed, fail
        if self._count > 0 and self._usages == 0:
            self._abort()

    def running(self):
        return self._count > 0

    def addpending(self, category, callback):
        """add a callback to be called when the transaction is pending

        The transaction will be given as callback's first argument.

        Category is a unique identifier to allow overwriting an old callback
        with a newer callback.
        """
        self._pendingcallback[category] = callback

    @active
    def writepending(self):
        """write pending file to temporary version

        This is used to allow hooks to view a transaction before commit"""
        categories = sorted(self._pendingcallback)
        for cat in categories:
            # remove callback since the data will have been flushed
            any = self._pendingcallback.pop(cat)(self)
            self._anypending = self._anypending or any
        self._anypending |= self._generatefiles(suffix=b'.pending')
        return self._anypending

    @active
    def hasfinalize(self, category):
        """check is a callback already exist for a category"""
        return category in self._finalizecallback

    @active
    def addfinalize(self, category, callback):
        """add a callback to be called when the transaction is closed

        The transaction will be given as callback's first argument.

        Category is a unique identifier to allow overwriting old callbacks with
        newer callbacks.
        """
        self._finalizecallback[category] = callback

    @active
    def addpostclose(self, category, callback):
        """add or replace a callback to be called after the transaction closed

        The transaction will be given as callback's first argument.

        Category is a unique identifier to allow overwriting an old callback
        with a newer callback.
        """
        self._postclosecallback[category] = callback

    @active
    def getpostclose(self, category):
        """return a postclose callback added before, or None"""
        return self._postclosecallback.get(category, None)

    @active
    def addabort(self, category, callback):
        """add a callback to be called when the transaction is aborted.

        The transaction will be given as the first argument to the callback.

        Category is a unique identifier to allow overwriting an old callback
        with a newer callback.
        """
        self._abortcallback[category] = callback

    @active
    def addvalidator(self, category, callback):
        """adds a callback to be called when validating the transaction.

        The transaction will be given as the first argument to the callback.

        callback should raise exception if to abort transaction"""
        self._validatecallback[category] = callback

    @active
    def close(self):
        '''commit the transaction'''
        if self._count == 1:
            for category in sorted(self._validatecallback):
                self._validatecallback[category](self)
            self._validatecallback = None  # Help prevent cycles.
            self._generatefiles(group=GEN_GROUP_PRE_FINALIZE)
            while self._finalizecallback:
                callbacks = self._finalizecallback
                self._finalizecallback = {}
                categories = sorted(callbacks)
                for cat in categories:
                    callbacks[cat](self)
            # Prevent double usage and help clear cycles.
            self._finalizecallback = None
            self._generatefiles(group=GEN_GROUP_POST_FINALIZE)

        self._count -= 1
        if self._count != 0:
            return
        self._file.close()
        self._backupsfile.close()
        # cleanup temporary files
        for l, f, b, c in self._backupentries:
            if l not in self._vfsmap and c:
                self._report(
                    b"couldn't remove %s: unknown cache location %s\n" % (b, l)
                )
                continue
            vfs = self._vfsmap[l]
            if not f and b and vfs.exists(b):
                try:
                    vfs.unlink(b)
                except (IOError, OSError, error.Abort) as inst:
                    if not c:
                        raise
                    # Abort may be raise by read only opener
                    self._report(
                        b"couldn't remove %s: %s\n" % (vfs.join(b), inst)
                    )
        self._offsetmap = {}
        self._newfiles = set()
        self._writeundo()
        if self._after:
            self._after()
            self._after = None  # Help prevent cycles.
        if self._opener.isfile(self._backupjournal):
            self._opener.unlink(self._backupjournal)
        if self._opener.isfile(self._journal):
            self._opener.unlink(self._journal)
        for l, _f, b, c in self._backupentries:
            if l not in self._vfsmap and c:
                self._report(
                    b"couldn't remove %s: unknown cache location"
                    b"%s\n" % (b, l)
                )
                continue
            vfs = self._vfsmap[l]
            if b and vfs.exists(b):
                try:
                    vfs.unlink(b)
                except (IOError, OSError, error.Abort) as inst:
                    if not c:
                        raise
                    # Abort may be raise by read only opener
                    self._report(
                        b"couldn't remove %s: %s\n" % (vfs.join(b), inst)
                    )
        self._backupentries = []
        self._journal = None

        self._releasefn(self, True)  # notify success of closing transaction
        self._releasefn = None  # Help prevent cycles.

        # run post close action
        categories = sorted(self._postclosecallback)
        for cat in categories:
            self._postclosecallback[cat](self)
        # Prevent double usage and help clear cycles.
        self._postclosecallback = None

    @active
    def abort(self):
        """abort the transaction (generally called on error, or when the
        transaction is not explicitly committed before going out of
        scope)"""
        self._abort()

    @active
    def add_journal(self, vfs_id, path):
        self._journal_files.append((vfs_id, path))

    def _writeundo(self):
        """write transaction data for possible future undo call"""
        if self._undoname is None:
            return
        cleanup_undo_files(
            self._report,
            self._vfsmap,
            undo_prefix=self._undoname,
        )

        def undoname(fn: bytes) -> bytes:
            base, name = os.path.split(fn)
            assert name.startswith(self._journal)
            new_name = name.replace(self._journal, self._undoname, 1)
            return os.path.join(base, new_name)

        undo_backup_path = b"%s.backupfiles" % self._undoname
        undobackupfile = self._opener.open(undo_backup_path, b'w')
        undobackupfile.write(b'%d\n' % version)
        for l, f, b, c in self._backupentries:
            if not f:  # temporary file
                continue
            if not b:
                u = b''
            else:
                if l not in self._vfsmap and c:
                    self._report(
                        b"couldn't remove %s: unknown cache location"
                        b"%s\n" % (b, l)
                    )
                    continue
                vfs = self._vfsmap[l]
                u = undoname(b)
                util.copyfile(vfs.join(b), vfs.join(u), hardlink=True)
            undobackupfile.write(b"%s\0%s\0%s\0%d\n" % (l, f, u, c))
        undobackupfile.close()
        for vfs, src in self._journal_files:
            dest = undoname(src)
            # if src and dest refer to a same file, vfs.rename is a no-op,
            # leaving both src and dest on disk. delete dest to make sure
            # the rename couldn't be such a no-op.
            vfs.tryunlink(dest)
            try:
                vfs.rename(src, dest)
            except FileNotFoundError:  # journal file does not yet exist
                pass

    def _abort(self):
        entries = self.readjournal()
        self._count = 0
        self._usages = 0
        self._file.close()
        self._backupsfile.close()

        quick = self._can_quick_abort(entries)
        try:
            if not quick:
                self._report(_(b"transaction abort!\n"))
            for cat in sorted(self._abortcallback):
                self._abortcallback[cat](self)
            # Prevent double usage and help clear cycles.
            self._abortcallback = None
            if quick:
                self._do_quick_abort(entries)
            else:
                self._do_full_abort(entries)
        finally:
            self._journal = None
            self._releasefn(self, False)  # notify failure of transaction
            self._releasefn = None  # Help prevent cycles.

    def _can_quick_abort(self, entries):
        """False if any semantic content have been written on disk

        True if nothing, except temporary files has been writen on disk."""
        if entries:
            return False
        for e in self._backupentries:
            if e[1]:
                return False
        return True

    def _do_quick_abort(self, entries):
        """(Silently) do a quick cleanup (see _can_quick_abort)"""
        assert self._can_quick_abort(entries)
        tmp_files = [e for e in self._backupentries if not e[1]]
        for vfs_id, old_path, tmp_path, xxx in tmp_files:
            vfs = self._vfsmap[vfs_id]
            try:
                vfs.unlink(tmp_path)
            except FileNotFoundError:
                pass
        if self._backupjournal:
            self._opener.unlink(self._backupjournal)
        if self._journal:
            self._opener.unlink(self._journal)

    def _do_full_abort(self, entries):
        """(Noisily) rollback all the change introduced by the transaction"""
        try:
            _playback(
                self._journal,
                self._report,
                self._opener,
                self._vfsmap,
                entries,
                self._backupentries,
                unlink=True,
                checkambigfiles=self._checkambigfiles,
            )
            self._report(_(b"rollback completed\n"))
        except BaseException as exc:
            self._report(_(b"rollback failed - please run hg recover\n"))
            self._report(
                _(b"(failure reason: %s)\n") % stringutil.forcebytestr(exc)
            )


BAD_VERSION_MSG = _(
    b"journal was created by a different version of Mercurial\n"
)


def read_backup_files(report, fp):
    """parse an (already open) backup file an return contained backup entries

    entries are in the form: (location, file, backupfile, xxx)

    :location:   the vfs identifier (vfsmap's key)
    :file:       original file path (in the vfs)
    :backupfile: path of the backup (in the vfs)
    :cache:      a boolean currently always set to False
    """
    lines = fp.readlines()
    backupentries = []
    if lines:
        ver = lines[0][:-1]
        if ver != (b'%d' % version):
            report(BAD_VERSION_MSG)
        else:
            for line in lines[1:]:
                if line:
                    # Shave off the trailing newline
                    line = line[:-1]
                    l, f, b, c = line.split(b'\0')
                    backupentries.append((l, f, b, bool(c)))
    return backupentries


def rollback(
    opener,
    vfsmap,
    file,
    report,
    checkambigfiles=None,
    skip_journal_pattern=None,
):
    """Rolls back the transaction contained in the given file

    Reads the entries in the specified file, and the corresponding
    '*.backupfiles' file, to recover from an incomplete transaction.

    * `file`: a file containing a list of entries, specifying where
    to truncate each file.  The file should contain a list of
    file\0offset pairs, delimited by newlines. The corresponding
    '*.backupfiles' file should contain a list of file\0backupfile
    pairs, delimited by \0.

    `checkambigfiles` is a set of (path, vfs-location) tuples,
    which determine whether file stat ambiguity should be avoided at
    restoring corresponded files.
    """
    entries = []
    backupentries = []

    with opener.open(file) as fp:
        lines = fp.readlines()
    for l in lines:
        try:
            f, o = l.split(b'\0')
            entries.append((f, int(o)))
        except ValueError:
            report(
                _(b"couldn't read journal entry %r!\n") % pycompat.bytestr(l)
            )

    backupjournal = b"%s.backupfiles" % file
    if opener.exists(backupjournal):
        with opener.open(backupjournal) as fp:
            backupentries = read_backup_files(report, fp)
    if skip_journal_pattern is not None:
        keep = lambda x: not skip_journal_pattern.match(x[1])
        backupentries = [x for x in backupentries if keep(x)]

    _playback(
        file,
        report,
        opener,
        vfsmap,
        entries,
        backupentries,
        checkambigfiles=checkambigfiles,
    )
