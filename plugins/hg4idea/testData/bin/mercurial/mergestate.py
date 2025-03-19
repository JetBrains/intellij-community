import collections
import shutil
import struct
import weakref

from .i18n import _
from .node import (
    bin,
    hex,
    nullrev,
)
from . import (
    error,
    filemerge,
    util,
)
from .utils import hashutil

_pack = struct.pack
_unpack = struct.unpack


def _droponode(data):
    # used for compatibility for v1
    bits = data.split(b'\0')
    bits = bits[:-2] + bits[-1:]
    return b'\0'.join(bits)


def _filectxorabsent(hexnode, ctx, f):
    if hexnode == ctx.repo().nodeconstants.nullhex:
        return filemerge.absentfilectx(ctx, f)
    else:
        return ctx[f]


# Merge state record types. See ``mergestate`` docs for more.

####
# merge records which records metadata about a current merge
# exists only once in a mergestate
#####
RECORD_LOCAL = b'L'
RECORD_OTHER = b'O'
# record merge labels
RECORD_LABELS = b'l'

#####
# record extra information about files, with one entry containing info about one
# file. Hence, multiple of them can exists
#####
RECORD_FILE_VALUES = b'f'

#####
# merge records which represents state of individual merges of files/folders
# These are top level records for each entry containing merge related info.
# Each record of these has info about one file. Hence multiple of them can
# exists
#####
RECORD_MERGED = b'F'
RECORD_CHANGEDELETE_CONFLICT = b'C'
# the path was dir on one side of merge and file on another
RECORD_PATH_CONFLICT = b'P'

#####
# possible state which a merge entry can have. These are stored inside top-level
# merge records mentioned just above.
#####
MERGE_RECORD_UNRESOLVED = b'u'
MERGE_RECORD_RESOLVED = b'r'
MERGE_RECORD_UNRESOLVED_PATH = b'pu'
MERGE_RECORD_RESOLVED_PATH = b'pr'
# represents that the file was automatically merged in favor
# of other version. This info is used on commit.
# This is now deprecated and commit related information is now
# stored in RECORD_FILE_VALUES
MERGE_RECORD_MERGED_OTHER = b'o'

#####
# top level record which stores other unknown records. Multiple of these can
# exists
#####
RECORD_OVERRIDE = b't'

#####
# legacy records which are no longer used but kept to prevent breaking BC
#####
# This record was release in 5.4 and usage was removed in 5.5
LEGACY_RECORD_RESOLVED_OTHER = b'R'
# This record was release in 3.7 and usage was removed in 5.6
LEGACY_RECORD_DRIVER_RESOLVED = b'd'
# This record was release in 3.7 and usage was removed in 5.6
LEGACY_MERGE_DRIVER_STATE = b'm'
# This record was release in 3.7 and usage was removed in 5.6
LEGACY_MERGE_DRIVER_MERGE = b'D'

CHANGE_ADDED = b'added'
CHANGE_REMOVED = b'removed'
CHANGE_MODIFIED = b'modified'


class MergeAction:
    """represent an "action" merge need to take for a given file

    Attributes:

    _short: internal representation used to identify each action

    no_op:  True if the action does affect the file content or tracking status

    narrow_safe:
        True if the action can be safely used for a file outside of the narrow
        set

    changes:
        The types of changes that this actions involves. This is a work in
        progress and not all actions have one yet. In addition, some requires
        user changes and cannot be fully decided. The value currently available
        are:

        - ADDED: the files is new in both parents
        - REMOVED: the files existed in one parent and is getting removed
        - MODIFIED: the files existed in at least one parent and is getting changed
    """

    ALL_ACTIONS = weakref.WeakSet()
    NO_OP_ACTIONS = weakref.WeakSet()

    def __init__(self, short, no_op=False, narrow_safe=False, changes=None):
        self._short = short
        self.ALL_ACTIONS.add(self)
        self.no_op = no_op
        if self.no_op:
            self.NO_OP_ACTIONS.add(self)
        self.narrow_safe = narrow_safe
        self.changes = changes

    def __hash__(self):
        return hash(self._short)

    def __repr__(self):
        return 'MergeAction<%s>' % self._short.decode('ascii')

    def __bytes__(self):
        return self._short

    def __eq__(self, other):
        if other is None:
            return False
        assert isinstance(other, MergeAction)
        return self._short == other._short

    def __lt__(self, other):
        return self._short < other._short


ACTION_FORGET = MergeAction(b'f', narrow_safe=True, changes=CHANGE_REMOVED)
ACTION_REMOVE = MergeAction(b'r', narrow_safe=True, changes=CHANGE_REMOVED)
ACTION_ADD = MergeAction(b'a', narrow_safe=True, changes=CHANGE_ADDED)
ACTION_GET = MergeAction(b'g', narrow_safe=True, changes=CHANGE_MODIFIED)
ACTION_PATH_CONFLICT = MergeAction(b'p')
ACTION_PATH_CONFLICT_RESOLVE = MergeAction(b'pr')
ACTION_ADD_MODIFIED = MergeAction(
    b'am', narrow_safe=True, changes=CHANGE_ADDED
)  # not 100% about the changes value here
ACTION_CREATED = MergeAction(b'c', narrow_safe=True, changes=CHANGE_ADDED)
ACTION_DELETED_CHANGED = MergeAction(b'dc')
ACTION_CHANGED_DELETED = MergeAction(b'cd')
ACTION_MERGE = MergeAction(b'm')
ACTION_LOCAL_DIR_RENAME_GET = MergeAction(b'dg')
ACTION_DIR_RENAME_MOVE_LOCAL = MergeAction(b'dm')
ACTION_KEEP = MergeAction(b'k', no_op=True)
# the file was absent on local side before merge and we should
# keep it absent (absent means file not present, it can be a result
# of file deletion, rename etc.)
ACTION_KEEP_ABSENT = MergeAction(b'ka', no_op=True)
# the file is absent on the ancestor and remote side of the merge
# hence this file is new and we should keep it
ACTION_KEEP_NEW = MergeAction(b'kn', no_op=True)
ACTION_EXEC = MergeAction(b'e', narrow_safe=True, changes=CHANGE_MODIFIED)
ACTION_CREATED_MERGE = MergeAction(
    b'cm', narrow_safe=True, changes=CHANGE_ADDED
)


# Used by concert to detect situation it does not like, not sure what the exact
# criteria is
CONVERT_MERGE_ACTIONS = (
    ACTION_MERGE,
    ACTION_DIR_RENAME_MOVE_LOCAL,
    ACTION_CHANGED_DELETED,
    ACTION_DELETED_CHANGED,
)


class _mergestate_base:
    """track 3-way merge state of individual files

    The merge state is stored on disk when needed. Two files are used: one with
    an old format (version 1), and one with a new format (version 2). Version 2
    stores a superset of the data in version 1, including new kinds of records
    in the future. For more about the new format, see the documentation for
    `_readrecordsv2`.

    Each record can contain arbitrary content, and has an associated type. This
    `type` should be a letter. If `type` is uppercase, the record is mandatory:
    versions of Mercurial that don't support it should abort. If `type` is
    lowercase, the record can be safely ignored.

    Currently known records:

    L: the node of the "local" part of the merge (hexified version)
    O: the node of the "other" part of the merge (hexified version)
    F: a file to be merged entry
    C: a change/delete or delete/change conflict
    P: a path conflict (file vs directory)
    f: a (filename, dictionary) tuple of optional values for a given file
    l: the labels for the parts of the merge.

    Merge record states (stored in self._state, indexed by filename):
    u: unresolved conflict
    r: resolved conflict
    pu: unresolved path conflict (file conflicts with directory)
    pr: resolved path conflict
    o: file was merged in favor of other parent of merge (DEPRECATED)

    The resolve command transitions between 'u' and 'r' for conflicts and
    'pu' and 'pr' for path conflicts.
    """

    def __init__(self, repo):
        """Initialize the merge state.

        Do not use this directly! Instead call read() or clean()."""
        self._repo = repo
        self._state = {}
        self._stateextras = collections.defaultdict(dict)
        self._local = None
        self._other = None
        self._labels = None
        # contains a mapping of form:
        # {filename : (merge_return_value, action_to_be_performed}
        # these are results of re-running merge process
        # this dict is used to perform actions on dirstate caused by re-running
        # the merge
        self._results = {}
        self._dirty = False

    def reset(self):
        pass

    def start(self, node, other, labels=None):
        self._local = node
        self._other = other
        self._labels = labels

    @util.propertycache
    def local(self):
        if self._local is None:
            msg = b"local accessed but self._local isn't set"
            raise error.ProgrammingError(msg)
        return self._local

    @util.propertycache
    def localctx(self):
        return self._repo[self.local]

    @util.propertycache
    def other(self):
        if self._other is None:
            msg = b"other accessed but self._other isn't set"
            raise error.ProgrammingError(msg)
        return self._other

    @util.propertycache
    def otherctx(self):
        return self._repo[self.other]

    def active(self):
        """Whether mergestate is active.

        Returns True if there appears to be mergestate. This is a rough proxy
        for "is a merge in progress."
        """
        return bool(self._local) or bool(self._state)

    def commit(self):
        """Write current state on disk (if necessary)"""

    @staticmethod
    def getlocalkey(path):
        """hash the path of a local file context for storage in the .hg/merge
        directory."""

        return hex(hashutil.sha1(path).digest())

    def _make_backup(self, fctx, localkey):
        raise NotImplementedError()

    def _restore_backup(self, fctx, localkey, flags):
        raise NotImplementedError()

    def add(self, fcl, fco, fca, fd):
        """add a new (potentially?) conflicting file the merge state
        fcl: file context for local,
        fco: file context for remote,
        fca: file context for ancestors,
        fd:  file path of the resulting merge.

        note: also write the local version to the `.hg/merge` directory.
        """
        if fcl.isabsent():
            localkey = self._repo.nodeconstants.nullhex
        else:
            localkey = mergestate.getlocalkey(fcl.path())
            self._make_backup(fcl, localkey)
        self._state[fd] = [
            MERGE_RECORD_UNRESOLVED,
            localkey,
            fcl.path(),
            fca.path(),
            hex(fca.filenode()),
            fco.path(),
            hex(fco.filenode()),
            fcl.flags(),
        ]
        self._stateextras[fd][b'ancestorlinknode'] = hex(fca.node())
        self._dirty = True

    def addpathconflict(self, path, frename, forigin):
        """add a new conflicting path to the merge state
        path:    the path that conflicts
        frename: the filename the conflicting file was renamed to
        forigin: origin of the file ('l' or 'r' for local/remote)
        """
        self._state[path] = [MERGE_RECORD_UNRESOLVED_PATH, frename, forigin]
        self._dirty = True

    def addcommitinfo(self, path, data):
        """stores information which is required at commit
        into _stateextras"""
        self._stateextras[path].update(data)
        self._dirty = True

    def __contains__(self, dfile):
        return dfile in self._state

    def __getitem__(self, dfile):
        return self._state[dfile][0]

    def __iter__(self):
        return iter(sorted(self._state))

    def files(self):
        return self._state.keys()

    def mark(self, dfile, state):
        self._state[dfile][0] = state
        self._dirty = True

    def unresolved(self):
        """Obtain the paths of unresolved files."""

        for f, entry in self._state.items():
            if entry[0] in (
                MERGE_RECORD_UNRESOLVED,
                MERGE_RECORD_UNRESOLVED_PATH,
            ):
                yield f

    def allextras(self):
        """return all extras information stored with the mergestate"""
        return self._stateextras

    def extras(self, filename):
        """return extras stored with the mergestate for the given filename"""
        return self._stateextras[filename]

    def resolve(self, dfile, wctx):
        """run merge process for dfile

        Returns the exit code of the merge."""
        if self[dfile] in (
            MERGE_RECORD_RESOLVED,
            LEGACY_RECORD_DRIVER_RESOLVED,
        ):
            return 0
        stateentry = self._state[dfile]
        state, localkey, lfile, afile, anode, ofile, onode, flags = stateentry
        octx = self._repo[self._other]
        extras = self.extras(dfile)
        anccommitnode = extras.get(b'ancestorlinknode')
        if anccommitnode:
            actx = self._repo[anccommitnode]
        else:
            actx = None
        fcd = _filectxorabsent(localkey, wctx, dfile)
        fco = _filectxorabsent(onode, octx, ofile)
        # TODO: move this to filectxorabsent
        fca = self._repo.filectx(afile, fileid=anode, changectx=actx)
        # "premerge" x flags
        flo = fco.flags()
        fla = fca.flags()
        if b'x' in flags + flo + fla and b'l' not in flags + flo + fla:
            if fca.rev() == nullrev and flags != flo:
                self._repo.ui.warn(
                    _(
                        b'warning: cannot merge flags for %s '
                        b'without common ancestor - keeping local flags\n'
                    )
                    % afile
                )
            elif flags == fla:
                flags = flo
        # restore local
        if localkey != self._repo.nodeconstants.nullhex:
            self._restore_backup(wctx[dfile], localkey, flags)
        else:
            wctx[dfile].remove(ignoremissing=True)

        if not fco.cmp(fcd):  # files identical?
            # If return value of merge is None, then there are no real conflict
            del self._state[dfile]
            self._results[dfile] = None, None
            self._dirty = True
            return None

        merge_ret, deleted = filemerge.filemerge(
            self._repo,
            wctx,
            self._local,
            lfile,
            fcd,
            fco,
            fca,
            labels=self._labels,
        )

        if not merge_ret:
            self.mark(dfile, MERGE_RECORD_RESOLVED)

        action = None
        if deleted:
            if fcd.isabsent():
                # dc: local picked. Need to drop if present, which may
                # happen on re-resolves.
                action = ACTION_FORGET
            else:
                # cd: remote picked (or otherwise deleted)
                action = ACTION_REMOVE
        else:
            if fcd.isabsent():  # dc: remote picked
                action = ACTION_GET
            elif fco.isabsent():  # cd: local picked
                if dfile in self.localctx:
                    action = ACTION_ADD_MODIFIED
                else:
                    action = ACTION_ADD
            # else: regular merges (no action necessary)
        self._results[dfile] = merge_ret, action

        return merge_ret

    def counts(self):
        """return counts for updated, merged and removed files in this
        session"""
        updated, merged, removed = 0, 0, 0
        for r, action in self._results.values():
            if r is None:
                updated += 1
            elif r == 0:
                if action == ACTION_REMOVE:
                    removed += 1
                else:
                    merged += 1
        return updated, merged, removed

    def unresolvedcount(self):
        """get unresolved count for this merge (persistent)"""
        return len(list(self.unresolved()))

    def actions(self):
        """return lists of actions to perform on the dirstate"""
        actions = {
            ACTION_REMOVE: [],
            ACTION_FORGET: [],
            ACTION_ADD: [],
            ACTION_ADD_MODIFIED: [],
            ACTION_GET: [],
        }
        for f, (r, action) in self._results.items():
            if action is not None:
                actions[action].append((f, None, b"merge result"))
        return actions


class mergestate(_mergestate_base):

    statepathv1 = b'merge/state'
    statepathv2 = b'merge/state2'

    @staticmethod
    def clean(repo):
        """Initialize a brand new merge state, removing any existing state on
        disk."""
        ms = mergestate(repo)
        ms.reset()
        return ms

    @staticmethod
    def read(repo):
        """Initialize the merge state, reading it from disk."""
        ms = mergestate(repo)
        ms._read()
        return ms

    def _read(self):
        """Analyse each record content to restore a serialized state from disk

        This function process "record" entry produced by the de-serialization
        of on disk file.
        """
        unsupported = set()
        records = self._readrecords()
        for rtype, record in records:
            if rtype == RECORD_LOCAL:
                self._local = bin(record)
            elif rtype == RECORD_OTHER:
                self._other = bin(record)
            elif rtype == LEGACY_MERGE_DRIVER_STATE:
                pass
            elif rtype in (
                RECORD_MERGED,
                RECORD_CHANGEDELETE_CONFLICT,
                RECORD_PATH_CONFLICT,
                LEGACY_MERGE_DRIVER_MERGE,
                LEGACY_RECORD_RESOLVED_OTHER,
            ):
                bits = record.split(b'\0')
                # merge entry type MERGE_RECORD_MERGED_OTHER is deprecated
                # and we now store related information in _stateextras, so
                # lets write to _stateextras directly
                if bits[1] == MERGE_RECORD_MERGED_OTHER:
                    self._stateextras[bits[0]][b'filenode-source'] = b'other'
                else:
                    self._state[bits[0]] = bits[1:]
            elif rtype == RECORD_FILE_VALUES:
                filename, rawextras = record.split(b'\0', 1)
                extraparts = rawextras.split(b'\0')
                extras = {}
                i = 0
                while i < len(extraparts):
                    extras[extraparts[i]] = extraparts[i + 1]
                    i += 2

                self._stateextras[filename] = extras
            elif rtype == RECORD_LABELS:
                labels = record.split(b'\0', 2)
                self._labels = [l for l in labels if len(l) > 0]
            elif not rtype.islower():
                unsupported.add(rtype)

        if unsupported:
            raise error.UnsupportedMergeRecords(unsupported)

    def _readrecords(self):
        """Read merge state from disk and return a list of record (TYPE, data)

        We read data from both v1 and v2 files and decide which one to use.

        V1 has been used by version prior to 2.9.1 and contains less data than
        v2. We read both versions and check if no data in v2 contradicts
        v1. If there is not contradiction we can safely assume that both v1
        and v2 were written at the same time and use the extract data in v2. If
        there is contradiction we ignore v2 content as we assume an old version
        of Mercurial has overwritten the mergestate file and left an old v2
        file around.

        returns list of record [(TYPE, data), ...]"""
        v1records = self._readrecordsv1()
        v2records = self._readrecordsv2()
        if self._v1v2match(v1records, v2records):
            return v2records
        else:
            # v1 file is newer than v2 file, use it
            # we have to infer the "other" changeset of the merge
            # we cannot do better than that with v1 of the format
            mctx = self._repo[None].parents()[-1]
            v1records.append((RECORD_OTHER, mctx.hex()))
            # add place holder "other" file node information
            # nobody is using it yet so we do no need to fetch the data
            # if mctx was wrong `mctx[bits[-2]]` may fails.
            for idx, r in enumerate(v1records):
                if r[0] == RECORD_MERGED:
                    bits = r[1].split(b'\0')
                    bits.insert(-2, b'')
                    v1records[idx] = (r[0], b'\0'.join(bits))
            return v1records

    def _v1v2match(self, v1records, v2records):
        oldv2 = set()  # old format version of v2 record
        for rec in v2records:
            if rec[0] == RECORD_LOCAL:
                oldv2.add(rec)
            elif rec[0] == RECORD_MERGED:
                # drop the onode data (not contained in v1)
                oldv2.add((RECORD_MERGED, _droponode(rec[1])))
        for rec in v1records:
            if rec not in oldv2:
                return False
        else:
            return True

    def _readrecordsv1(self):
        """read on disk merge state for version 1 file

        returns list of record [(TYPE, data), ...]

        Note: the "F" data from this file are one entry short
              (no "other file node" entry)
        """
        records = []
        try:
            f = self._repo.vfs(self.statepathv1)
            for i, l in enumerate(f):
                if i == 0:
                    records.append((RECORD_LOCAL, l[:-1]))
                else:
                    records.append((RECORD_MERGED, l[:-1]))
            f.close()
        except FileNotFoundError:
            pass
        return records

    def _readrecordsv2(self):
        """read on disk merge state for version 2 file

        This format is a list of arbitrary records of the form:

          [type][length][content]

        `type` is a single character, `length` is a 4 byte integer, and
        `content` is an arbitrary byte sequence of length `length`.

        Mercurial versions prior to 3.7 have a bug where if there are
        unsupported mandatory merge records, attempting to clear out the merge
        state with hg update --clean or similar aborts. The 't' record type
        works around that by writing out what those versions treat as an
        advisory record, but later versions interpret as special: the first
        character is the 'real' record type and everything onwards is the data.

        Returns list of records [(TYPE, data), ...]."""
        records = []
        try:
            f = self._repo.vfs(self.statepathv2)
            data = f.read()
            off = 0
            end = len(data)
            while off < end:
                rtype = data[off : off + 1]
                off += 1
                length = _unpack(b'>I', data[off : (off + 4)])[0]
                off += 4
                record = data[off : (off + length)]
                off += length
                if rtype == RECORD_OVERRIDE:
                    rtype, record = record[0:1], record[1:]
                records.append((rtype, record))
            f.close()
        except FileNotFoundError:
            pass
        return records

    def commit(self):
        if self._dirty:
            records = self._makerecords()
            self._writerecords(records)
            self._dirty = False

    def _makerecords(self):
        records = []
        records.append((RECORD_LOCAL, hex(self._local)))
        records.append((RECORD_OTHER, hex(self._other)))
        # Write out state items. In all cases, the value of the state map entry
        # is written as the contents of the record. The record type depends on
        # the type of state that is stored, and capital-letter records are used
        # to prevent older versions of Mercurial that do not support the feature
        # from loading them.
        for filename, v in self._state.items():
            if v[0] in (
                MERGE_RECORD_UNRESOLVED_PATH,
                MERGE_RECORD_RESOLVED_PATH,
            ):
                # Path conflicts. These are stored in 'P' records.  The current
                # resolution state ('pu' or 'pr') is stored within the record.
                records.append(
                    (RECORD_PATH_CONFLICT, b'\0'.join([filename] + v))
                )
            elif (
                v[1] == self._repo.nodeconstants.nullhex
                or v[6] == self._repo.nodeconstants.nullhex
            ):
                # Change/Delete or Delete/Change conflicts. These are stored in
                # 'C' records. v[1] is the local file, and is nullhex when the
                # file is deleted locally ('dc'). v[6] is the remote file, and
                # is nullhex when the file is deleted remotely ('cd').
                records.append(
                    (RECORD_CHANGEDELETE_CONFLICT, b'\0'.join([filename] + v))
                )
            else:
                # Normal files.  These are stored in 'F' records.
                records.append((RECORD_MERGED, b'\0'.join([filename] + v)))
        for filename, extras in sorted(self._stateextras.items()):
            rawextras = b'\0'.join(
                b'%s\0%s' % (k, v) for k, v in extras.items()
            )
            records.append(
                (RECORD_FILE_VALUES, b'%s\0%s' % (filename, rawextras))
            )
        if self._labels is not None:
            labels = b'\0'.join(self._labels)
            records.append((RECORD_LABELS, labels))
        return records

    def _writerecords(self, records):
        """Write current state on disk (both v1 and v2)"""
        self._writerecordsv1(records)
        self._writerecordsv2(records)

    def _writerecordsv1(self, records):
        """Write current state on disk in a version 1 file"""
        f = self._repo.vfs(self.statepathv1, b'wb')
        irecords = iter(records)
        lrecords = next(irecords)
        assert lrecords[0] == RECORD_LOCAL
        f.write(hex(self._local) + b'\n')
        for rtype, data in irecords:
            if rtype == RECORD_MERGED:
                f.write(b'%s\n' % _droponode(data))
        f.close()

    def _writerecordsv2(self, records):
        """Write current state on disk in a version 2 file

        See the docstring for _readrecordsv2 for why we use 't'."""
        # these are the records that all version 2 clients can read
        allowlist = (RECORD_LOCAL, RECORD_OTHER, RECORD_MERGED)
        f = self._repo.vfs(self.statepathv2, b'wb')
        for key, data in records:
            assert len(key) == 1
            if key not in allowlist:
                key, data = RECORD_OVERRIDE, b'%s%s' % (key, data)
            format = b'>sI%is' % len(data)
            f.write(_pack(format, key, len(data), data))
        f.close()

    def _make_backup(self, fctx, localkey):
        self._repo.vfs.write(b'merge/' + localkey, fctx.data())

    def _restore_backup(self, fctx, localkey, flags):
        with self._repo.vfs(b'merge/' + localkey) as f:
            fctx.write(f.read(), flags)

    def reset(self):
        shutil.rmtree(self._repo.vfs.join(b'merge'), True)


class memmergestate(_mergestate_base):
    def __init__(self, repo):
        super(memmergestate, self).__init__(repo)
        self._backups = {}

    def _make_backup(self, fctx, localkey):
        self._backups[localkey] = fctx.data()

    def _restore_backup(self, fctx, localkey, flags):
        fctx.write(self._backups[localkey], flags)


def recordupdates(repo, actions, branchmerge, getfiledata):
    """record merge actions to the dirstate"""
    # remove (must come first)
    for f, args, msg in actions.get(ACTION_REMOVE, []):
        if branchmerge:
            repo.dirstate.update_file(f, p1_tracked=True, wc_tracked=False)
        else:
            repo.dirstate.update_file(f, p1_tracked=False, wc_tracked=False)

    # forget (must come first)
    for f, args, msg in actions.get(ACTION_FORGET, []):
        repo.dirstate.update_file(f, p1_tracked=False, wc_tracked=False)

    # resolve path conflicts
    for f, args, msg in actions.get(ACTION_PATH_CONFLICT_RESOLVE, []):
        (f0, origf0) = args
        repo.dirstate.update_file(f, p1_tracked=False, wc_tracked=True)
        repo.dirstate.copy(origf0, f)
        if f0 == origf0:
            repo.dirstate.update_file(f0, p1_tracked=True, wc_tracked=False)
        else:
            repo.dirstate.update_file(f0, p1_tracked=False, wc_tracked=False)

    # re-add
    for f, args, msg in actions.get(ACTION_ADD, []):
        repo.dirstate.update_file(f, p1_tracked=False, wc_tracked=True)

    # re-add/mark as modified
    for f, args, msg in actions.get(ACTION_ADD_MODIFIED, []):
        if branchmerge:
            repo.dirstate.update_file(
                f, p1_tracked=True, wc_tracked=True, possibly_dirty=True
            )
        else:
            repo.dirstate.update_file(f, p1_tracked=False, wc_tracked=True)

    # exec change
    for f, args, msg in actions.get(ACTION_EXEC, []):
        repo.dirstate.update_file(
            f, p1_tracked=True, wc_tracked=True, possibly_dirty=True
        )

    # keep
    for f, args, msg in actions.get(ACTION_KEEP, []):
        pass

    # keep deleted
    for f, args, msg in actions.get(ACTION_KEEP_ABSENT, []):
        pass

    # keep new
    for f, args, msg in actions.get(ACTION_KEEP_NEW, []):
        pass

    # get
    for f, args, msg in actions.get(ACTION_GET, []):
        if branchmerge:
            # tracked in p1 can be True also but update_file should not care
            old_entry = repo.dirstate.get_entry(f)
            p1_tracked = old_entry.any_tracked and not old_entry.added
            repo.dirstate.update_file(
                f,
                p1_tracked=p1_tracked,
                wc_tracked=True,
                p2_info=True,
            )
        else:
            parentfiledata = getfiledata[f] if getfiledata else None
            repo.dirstate.update_file(
                f,
                p1_tracked=True,
                wc_tracked=True,
                parentfiledata=parentfiledata,
            )

    # merge
    for f, args, msg in actions.get(ACTION_MERGE, []):
        f1, f2, fa, move, anc = args
        if branchmerge:
            # We've done a branch merge, mark this file as merged
            # so that we properly record the merger later
            p1_tracked = f1 == f
            repo.dirstate.update_file(
                f,
                p1_tracked=p1_tracked,
                wc_tracked=True,
                p2_info=True,
            )
            if f1 != f2:  # copy/rename
                if move:
                    repo.dirstate.update_file(
                        f1, p1_tracked=True, wc_tracked=False
                    )
                if f1 != f:
                    repo.dirstate.copy(f1, f)
                else:
                    repo.dirstate.copy(f2, f)
        else:
            # We've update-merged a locally modified file, so
            # we set the dirstate to emulate a normal checkout
            # of that file some time in the past. Thus our
            # merge will appear as a normal local file
            # modification.
            if f2 == f:  # file not locally copied/moved
                repo.dirstate.update_file(
                    f, p1_tracked=True, wc_tracked=True, possibly_dirty=True
                )
            if move:
                repo.dirstate.update_file(
                    f1, p1_tracked=False, wc_tracked=False
                )

    # directory rename, move local
    for f, args, msg in actions.get(ACTION_DIR_RENAME_MOVE_LOCAL, []):
        f0, flag = args
        if branchmerge:
            repo.dirstate.update_file(f, p1_tracked=False, wc_tracked=True)
            repo.dirstate.update_file(f0, p1_tracked=True, wc_tracked=False)
            repo.dirstate.copy(f0, f)
        else:
            repo.dirstate.update_file(f, p1_tracked=True, wc_tracked=True)
            repo.dirstate.update_file(f0, p1_tracked=False, wc_tracked=False)

    # directory rename, get
    for f, args, msg in actions.get(ACTION_LOCAL_DIR_RENAME_GET, []):
        f0, flag = args
        if branchmerge:
            repo.dirstate.update_file(f, p1_tracked=False, wc_tracked=True)
            repo.dirstate.copy(f0, f)
        else:
            repo.dirstate.update_file(f, p1_tracked=True, wc_tracked=True)
