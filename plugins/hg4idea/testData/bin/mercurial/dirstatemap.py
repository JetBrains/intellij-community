# dirstatemap.py
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno

from .i18n import _

from . import (
    error,
    pathutil,
    policy,
    pycompat,
    txnutil,
    util,
)

from .dirstateutils import (
    docket as docketmod,
)

parsers = policy.importmod('parsers')
rustmod = policy.importrust('dirstate')

propertycache = util.propertycache

DirstateItem = parsers.DirstateItem


# a special value used internally for `size` if the file come from the other parent
FROM_P2 = -2

# a special value used internally for `size` if the file is modified/merged/added
NONNORMAL = -1

# a special value used internally for `time` if the time is ambigeous
AMBIGUOUS_TIME = -1

rangemask = 0x7FFFFFFF


class dirstatemap(object):
    """Map encapsulating the dirstate's contents.

    The dirstate contains the following state:

    - `identity` is the identity of the dirstate file, which can be used to
      detect when changes have occurred to the dirstate file.

    - `parents` is a pair containing the parents of the working copy. The
      parents are updated by calling `setparents`.

    - the state map maps filenames to tuples of (state, mode, size, mtime),
      where state is a single character representing 'normal', 'added',
      'removed', or 'merged'. It is read by treating the dirstate as a
      dict.  File state is updated by calling the `addfile`, `removefile` and
      `dropfile` methods.

    - `copymap` maps destination filenames to their source filename.

    The dirstate also provides the following views onto the state:

    - `nonnormalset` is a set of the filenames that have state other
      than 'normal', or are normal but have an mtime of -1 ('normallookup').

    - `otherparentset` is a set of the filenames that are marked as coming
      from the second parent when the dirstate is currently being merged.

    - `filefoldmap` is a dict mapping normalized filenames to the denormalized
      form that they appear as in the dirstate.

    - `dirfoldmap` is a dict mapping normalized directory names to the
      denormalized form that they appear as in the dirstate.
    """

    def __init__(self, ui, opener, root, nodeconstants, use_dirstate_v2):
        self._ui = ui
        self._opener = opener
        self._root = root
        self._filename = b'dirstate'
        self._nodelen = 20
        self._nodeconstants = nodeconstants
        assert (
            not use_dirstate_v2
        ), "should have detected unsupported requirement"

        self._parents = None
        self._dirtyparents = False

        # for consistent view between _pl() and _read() invocations
        self._pendingmode = None

    @propertycache
    def _map(self):
        self._map = {}
        self.read()
        return self._map

    @propertycache
    def copymap(self):
        self.copymap = {}
        self._map
        return self.copymap

    def clear(self):
        self._map.clear()
        self.copymap.clear()
        self.setparents(self._nodeconstants.nullid, self._nodeconstants.nullid)
        util.clearcachedproperty(self, b"_dirs")
        util.clearcachedproperty(self, b"_alldirs")
        util.clearcachedproperty(self, b"filefoldmap")
        util.clearcachedproperty(self, b"dirfoldmap")
        util.clearcachedproperty(self, b"nonnormalset")
        util.clearcachedproperty(self, b"otherparentset")

    def items(self):
        return pycompat.iteritems(self._map)

    # forward for python2,3 compat
    iteritems = items

    debug_iter = items

    def __len__(self):
        return len(self._map)

    def __iter__(self):
        return iter(self._map)

    def get(self, key, default=None):
        return self._map.get(key, default)

    def __contains__(self, key):
        return key in self._map

    def __getitem__(self, key):
        return self._map[key]

    def keys(self):
        return self._map.keys()

    def preload(self):
        """Loads the underlying data, if it's not already loaded"""
        self._map

    def _dirs_incr(self, filename, old_entry=None):
        """incremente the dirstate counter if applicable"""
        if (
            old_entry is None or old_entry.removed
        ) and "_dirs" in self.__dict__:
            self._dirs.addpath(filename)
        if old_entry is None and "_alldirs" in self.__dict__:
            self._alldirs.addpath(filename)

    def _dirs_decr(self, filename, old_entry=None, remove_variant=False):
        """decremente the dirstate counter if applicable"""
        if old_entry is not None:
            if "_dirs" in self.__dict__ and not old_entry.removed:
                self._dirs.delpath(filename)
            if "_alldirs" in self.__dict__ and not remove_variant:
                self._alldirs.delpath(filename)
        elif remove_variant and "_alldirs" in self.__dict__:
            self._alldirs.addpath(filename)
        if "filefoldmap" in self.__dict__:
            normed = util.normcase(filename)
            self.filefoldmap.pop(normed, None)

    def set_possibly_dirty(self, filename):
        """record that the current state of the file on disk is unknown"""
        self[filename].set_possibly_dirty()

    def addfile(
        self,
        f,
        mode=0,
        size=None,
        mtime=None,
        added=False,
        merged=False,
        from_p2=False,
        possibly_dirty=False,
    ):
        """Add a tracked file to the dirstate."""
        if added:
            assert not merged
            assert not possibly_dirty
            assert not from_p2
            state = b'a'
            size = NONNORMAL
            mtime = AMBIGUOUS_TIME
        elif merged:
            assert not possibly_dirty
            assert not from_p2
            state = b'm'
            size = FROM_P2
            mtime = AMBIGUOUS_TIME
        elif from_p2:
            assert not possibly_dirty
            state = b'n'
            size = FROM_P2
            mtime = AMBIGUOUS_TIME
        elif possibly_dirty:
            state = b'n'
            size = NONNORMAL
            mtime = AMBIGUOUS_TIME
        else:
            assert size != FROM_P2
            assert size != NONNORMAL
            assert size is not None
            assert mtime is not None

            state = b'n'
            size = size & rangemask
            mtime = mtime & rangemask
        assert state is not None
        assert size is not None
        assert mtime is not None
        old_entry = self.get(f)
        self._dirs_incr(f, old_entry)
        e = self._map[f] = DirstateItem(state, mode, size, mtime)
        if e.dm_nonnormal:
            self.nonnormalset.add(f)
        if e.dm_otherparent:
            self.otherparentset.add(f)

    def reset_state(
        self,
        filename,
        wc_tracked,
        p1_tracked,
        p2_tracked=False,
        merged=False,
        clean_p1=False,
        clean_p2=False,
        possibly_dirty=False,
        parentfiledata=None,
    ):
        """Set a entry to a given state, diregarding all previous state

        This is to be used by the part of the dirstate API dedicated to
        adjusting the dirstate after a update/merge.

        note: calling this might result to no entry existing at all if the
        dirstate map does not see any point at having one for this file
        anymore.
        """
        if merged and (clean_p1 or clean_p2):
            msg = b'`merged` argument incompatible with `clean_p1`/`clean_p2`'
            raise error.ProgrammingError(msg)
        # copy information are now outdated
        # (maybe new information should be in directly passed to this function)
        self.copymap.pop(filename, None)

        if not (p1_tracked or p2_tracked or wc_tracked):
            self.dropfile(filename)
        elif merged:
            # XXX might be merged and removed ?
            entry = self.get(filename)
            if entry is not None and entry.tracked:
                # XXX mostly replicate dirstate.other parent.  We should get
                # the higher layer to pass us more reliable data where `merged`
                # actually mean merged. Dropping the else clause will show
                # failure in `test-graft.t`
                self.addfile(filename, merged=True)
            else:
                self.addfile(filename, from_p2=True)
        elif not (p1_tracked or p2_tracked) and wc_tracked:
            self.addfile(filename, added=True, possibly_dirty=possibly_dirty)
        elif (p1_tracked or p2_tracked) and not wc_tracked:
            # XXX might be merged and removed ?
            old_entry = self._map.get(filename)
            self._dirs_decr(filename, old_entry=old_entry, remove_variant=True)
            self._map[filename] = DirstateItem(b'r', 0, 0, 0)
            self.nonnormalset.add(filename)
        elif clean_p2 and wc_tracked:
            if p1_tracked or self.get(filename) is not None:
                # XXX the `self.get` call is catching some case in
                # `test-merge-remove.t` where the file is tracked in p1, the
                # p1_tracked argument is False.
                #
                # In addition, this seems to be a case where the file is marked
                # as merged without actually being the result of a merge
                # action. So thing are not ideal here.
                self.addfile(filename, merged=True)
            else:
                self.addfile(filename, from_p2=True)
        elif not p1_tracked and p2_tracked and wc_tracked:
            self.addfile(filename, from_p2=True, possibly_dirty=possibly_dirty)
        elif possibly_dirty:
            self.addfile(filename, possibly_dirty=possibly_dirty)
        elif wc_tracked:
            # this is a "normal" file
            if parentfiledata is None:
                msg = b'failed to pass parentfiledata for a normal file: %s'
                msg %= filename
                raise error.ProgrammingError(msg)
            mode, size, mtime = parentfiledata
            self.addfile(filename, mode=mode, size=size, mtime=mtime)
            self.nonnormalset.discard(filename)
        else:
            assert False, 'unreachable'

    def removefile(self, f, in_merge=False):
        """
        Mark a file as removed in the dirstate.

        The `size` parameter is used to store sentinel values that indicate
        the file's previous state.  In the future, we should refactor this
        to be more explicit about what that state is.
        """
        entry = self.get(f)
        size = 0
        if in_merge:
            # XXX we should not be able to have 'm' state and 'FROM_P2' if not
            # during a merge. So I (marmoute) am not sure we need the
            # conditionnal at all. Adding double checking this with assert
            # would be nice.
            if entry is not None:
                # backup the previous state
                if entry.merged:  # merge
                    size = NONNORMAL
                elif entry.from_p2:
                    size = FROM_P2
                    self.otherparentset.add(f)
        if entry is not None and not (entry.merged or entry.from_p2):
            self.copymap.pop(f, None)
        self._dirs_decr(f, old_entry=entry, remove_variant=True)
        self._map[f] = DirstateItem(b'r', 0, size, 0)
        self.nonnormalset.add(f)

    def dropfile(self, f):
        """
        Remove a file from the dirstate.  Returns True if the file was
        previously recorded.
        """
        old_entry = self._map.pop(f, None)
        self._dirs_decr(f, old_entry=old_entry)
        self.nonnormalset.discard(f)
        return old_entry is not None

    def clearambiguoustimes(self, files, now):
        for f in files:
            e = self.get(f)
            if e is not None and e.need_delay(now):
                e.set_possibly_dirty()
                self.nonnormalset.add(f)

    def nonnormalentries(self):
        '''Compute the nonnormal dirstate entries from the dmap'''
        try:
            return parsers.nonnormalotherparententries(self._map)
        except AttributeError:
            nonnorm = set()
            otherparent = set()
            for fname, e in pycompat.iteritems(self._map):
                if e.dm_nonnormal:
                    nonnorm.add(fname)
                if e.from_p2:
                    otherparent.add(fname)
            return nonnorm, otherparent

    @propertycache
    def filefoldmap(self):
        """Returns a dictionary mapping normalized case paths to their
        non-normalized versions.
        """
        try:
            makefilefoldmap = parsers.make_file_foldmap
        except AttributeError:
            pass
        else:
            return makefilefoldmap(
                self._map, util.normcasespec, util.normcasefallback
            )

        f = {}
        normcase = util.normcase
        for name, s in pycompat.iteritems(self._map):
            if not s.removed:
                f[normcase(name)] = name
        f[b'.'] = b'.'  # prevents useless util.fspath() invocation
        return f

    def hastrackeddir(self, d):
        """
        Returns True if the dirstate contains a tracked (not removed) file
        in this directory.
        """
        return d in self._dirs

    def hasdir(self, d):
        """
        Returns True if the dirstate contains a file (tracked or removed)
        in this directory.
        """
        return d in self._alldirs

    @propertycache
    def _dirs(self):
        return pathutil.dirs(self._map, b'r')

    @propertycache
    def _alldirs(self):
        return pathutil.dirs(self._map)

    def _opendirstatefile(self):
        fp, mode = txnutil.trypending(self._root, self._opener, self._filename)
        if self._pendingmode is not None and self._pendingmode != mode:
            fp.close()
            raise error.Abort(
                _(b'working directory state may be changed parallelly')
            )
        self._pendingmode = mode
        return fp

    def parents(self):
        if not self._parents:
            try:
                fp = self._opendirstatefile()
                st = fp.read(2 * self._nodelen)
                fp.close()
            except IOError as err:
                if err.errno != errno.ENOENT:
                    raise
                # File doesn't exist, so the current state is empty
                st = b''

            l = len(st)
            if l == self._nodelen * 2:
                self._parents = (
                    st[: self._nodelen],
                    st[self._nodelen : 2 * self._nodelen],
                )
            elif l == 0:
                self._parents = (
                    self._nodeconstants.nullid,
                    self._nodeconstants.nullid,
                )
            else:
                raise error.Abort(
                    _(b'working directory state appears damaged!')
                )

        return self._parents

    def setparents(self, p1, p2):
        self._parents = (p1, p2)
        self._dirtyparents = True

    def read(self):
        # ignore HG_PENDING because identity is used only for writing
        self.identity = util.filestat.frompath(
            self._opener.join(self._filename)
        )

        try:
            fp = self._opendirstatefile()
            try:
                st = fp.read()
            finally:
                fp.close()
        except IOError as err:
            if err.errno != errno.ENOENT:
                raise
            return
        if not st:
            return

        if util.safehasattr(parsers, b'dict_new_presized'):
            # Make an estimate of the number of files in the dirstate based on
            # its size. This trades wasting some memory for avoiding costly
            # resizes. Each entry have a prefix of 17 bytes followed by one or
            # two path names. Studies on various large-scale real-world repositories
            # found 54 bytes a reasonable upper limit for the average path names.
            # Copy entries are ignored for the sake of this estimate.
            self._map = parsers.dict_new_presized(len(st) // 71)

        # Python's garbage collector triggers a GC each time a certain number
        # of container objects (the number being defined by
        # gc.get_threshold()) are allocated. parse_dirstate creates a tuple
        # for each file in the dirstate. The C version then immediately marks
        # them as not to be tracked by the collector. However, this has no
        # effect on when GCs are triggered, only on what objects the GC looks
        # into. This means that O(number of files) GCs are unavoidable.
        # Depending on when in the process's lifetime the dirstate is parsed,
        # this can get very expensive. As a workaround, disable GC while
        # parsing the dirstate.
        #
        # (we cannot decorate the function directly since it is in a C module)
        parse_dirstate = util.nogc(parsers.parse_dirstate)
        p = parse_dirstate(self._map, self.copymap, st)
        if not self._dirtyparents:
            self.setparents(*p)

        # Avoid excess attribute lookups by fast pathing certain checks
        self.__contains__ = self._map.__contains__
        self.__getitem__ = self._map.__getitem__
        self.get = self._map.get

    def write(self, _tr, st, now):
        st.write(
            parsers.pack_dirstate(self._map, self.copymap, self.parents(), now)
        )
        st.close()
        self._dirtyparents = False
        self.nonnormalset, self.otherparentset = self.nonnormalentries()

    @propertycache
    def nonnormalset(self):
        nonnorm, otherparents = self.nonnormalentries()
        self.otherparentset = otherparents
        return nonnorm

    @propertycache
    def otherparentset(self):
        nonnorm, otherparents = self.nonnormalentries()
        self.nonnormalset = nonnorm
        return otherparents

    def non_normal_or_other_parent_paths(self):
        return self.nonnormalset.union(self.otherparentset)

    @propertycache
    def identity(self):
        self._map
        return self.identity

    @propertycache
    def dirfoldmap(self):
        f = {}
        normcase = util.normcase
        for name in self._dirs:
            f[normcase(name)] = name
        return f


if rustmod is not None:

    class dirstatemap(object):
        def __init__(self, ui, opener, root, nodeconstants, use_dirstate_v2):
            self._use_dirstate_v2 = use_dirstate_v2
            self._nodeconstants = nodeconstants
            self._ui = ui
            self._opener = opener
            self._root = root
            self._filename = b'dirstate'
            self._nodelen = 20  # Also update Rust code when changing this!
            self._parents = None
            self._dirtyparents = False
            self._docket = None

            # for consistent view between _pl() and _read() invocations
            self._pendingmode = None

            self._use_dirstate_tree = self._ui.configbool(
                b"experimental",
                b"dirstate-tree.in-memory",
                False,
            )

        def addfile(
            self,
            f,
            mode=0,
            size=None,
            mtime=None,
            added=False,
            merged=False,
            from_p2=False,
            possibly_dirty=False,
        ):
            return self._rustmap.addfile(
                f,
                mode,
                size,
                mtime,
                added,
                merged,
                from_p2,
                possibly_dirty,
            )

        def reset_state(
            self,
            filename,
            wc_tracked,
            p1_tracked,
            p2_tracked=False,
            merged=False,
            clean_p1=False,
            clean_p2=False,
            possibly_dirty=False,
            parentfiledata=None,
        ):
            """Set a entry to a given state, disregarding all previous state

            This is to be used by the part of the dirstate API dedicated to
            adjusting the dirstate after a update/merge.

            note: calling this might result to no entry existing at all if the
            dirstate map does not see any point at having one for this file
            anymore.
            """
            if merged and (clean_p1 or clean_p2):
                msg = (
                    b'`merged` argument incompatible with `clean_p1`/`clean_p2`'
                )
                raise error.ProgrammingError(msg)
            # copy information are now outdated
            # (maybe new information should be in directly passed to this function)
            self.copymap.pop(filename, None)

            if not (p1_tracked or p2_tracked or wc_tracked):
                self.dropfile(filename)
            elif merged:
                # XXX might be merged and removed ?
                entry = self.get(filename)
                if entry is not None and entry.tracked:
                    # XXX mostly replicate dirstate.other parent.  We should get
                    # the higher layer to pass us more reliable data where `merged`
                    # actually mean merged. Dropping the else clause will show
                    # failure in `test-graft.t`
                    self.addfile(filename, merged=True)
                else:
                    self.addfile(filename, from_p2=True)
            elif not (p1_tracked or p2_tracked) and wc_tracked:
                self.addfile(
                    filename, added=True, possibly_dirty=possibly_dirty
                )
            elif (p1_tracked or p2_tracked) and not wc_tracked:
                # XXX might be merged and removed ?
                self[filename] = DirstateItem(b'r', 0, 0, 0)
                self.nonnormalset.add(filename)
            elif clean_p2 and wc_tracked:
                if p1_tracked or self.get(filename) is not None:
                    # XXX the `self.get` call is catching some case in
                    # `test-merge-remove.t` where the file is tracked in p1, the
                    # p1_tracked argument is False.
                    #
                    # In addition, this seems to be a case where the file is marked
                    # as merged without actually being the result of a merge
                    # action. So thing are not ideal here.
                    self.addfile(filename, merged=True)
                else:
                    self.addfile(filename, from_p2=True)
            elif not p1_tracked and p2_tracked and wc_tracked:
                self.addfile(
                    filename, from_p2=True, possibly_dirty=possibly_dirty
                )
            elif possibly_dirty:
                self.addfile(filename, possibly_dirty=possibly_dirty)
            elif wc_tracked:
                # this is a "normal" file
                if parentfiledata is None:
                    msg = b'failed to pass parentfiledata for a normal file: %s'
                    msg %= filename
                    raise error.ProgrammingError(msg)
                mode, size, mtime = parentfiledata
                self.addfile(filename, mode=mode, size=size, mtime=mtime)
                self.nonnormalset.discard(filename)
            else:
                assert False, 'unreachable'

        def removefile(self, *args, **kwargs):
            return self._rustmap.removefile(*args, **kwargs)

        def dropfile(self, *args, **kwargs):
            return self._rustmap.dropfile(*args, **kwargs)

        def clearambiguoustimes(self, *args, **kwargs):
            return self._rustmap.clearambiguoustimes(*args, **kwargs)

        def nonnormalentries(self):
            return self._rustmap.nonnormalentries()

        def get(self, *args, **kwargs):
            return self._rustmap.get(*args, **kwargs)

        @property
        def copymap(self):
            return self._rustmap.copymap()

        def directories(self):
            return self._rustmap.directories()

        def debug_iter(self):
            return self._rustmap.debug_iter()

        def preload(self):
            self._rustmap

        def clear(self):
            self._rustmap.clear()
            self.setparents(
                self._nodeconstants.nullid, self._nodeconstants.nullid
            )
            util.clearcachedproperty(self, b"_dirs")
            util.clearcachedproperty(self, b"_alldirs")
            util.clearcachedproperty(self, b"dirfoldmap")

        def items(self):
            return self._rustmap.items()

        def keys(self):
            return iter(self._rustmap)

        def __contains__(self, key):
            return key in self._rustmap

        def __getitem__(self, item):
            return self._rustmap[item]

        def __len__(self):
            return len(self._rustmap)

        def __iter__(self):
            return iter(self._rustmap)

        # forward for python2,3 compat
        iteritems = items

        def _opendirstatefile(self):
            fp, mode = txnutil.trypending(
                self._root, self._opener, self._filename
            )
            if self._pendingmode is not None and self._pendingmode != mode:
                fp.close()
                raise error.Abort(
                    _(b'working directory state may be changed parallelly')
                )
            self._pendingmode = mode
            return fp

        def _readdirstatefile(self, size=-1):
            try:
                with self._opendirstatefile() as fp:
                    return fp.read(size)
            except IOError as err:
                if err.errno != errno.ENOENT:
                    raise
                # File doesn't exist, so the current state is empty
                return b''

        def setparents(self, p1, p2):
            self._parents = (p1, p2)
            self._dirtyparents = True

        def parents(self):
            if not self._parents:
                if self._use_dirstate_v2:
                    self._parents = self.docket.parents
                else:
                    read_len = self._nodelen * 2
                    st = self._readdirstatefile(read_len)
                    l = len(st)
                    if l == read_len:
                        self._parents = (
                            st[: self._nodelen],
                            st[self._nodelen : 2 * self._nodelen],
                        )
                    elif l == 0:
                        self._parents = (
                            self._nodeconstants.nullid,
                            self._nodeconstants.nullid,
                        )
                    else:
                        raise error.Abort(
                            _(b'working directory state appears damaged!')
                        )

            return self._parents

        @property
        def docket(self):
            if not self._docket:
                if not self._use_dirstate_v2:
                    raise error.ProgrammingError(
                        b'dirstate only has a docket in v2 format'
                    )
                self._docket = docketmod.DirstateDocket.parse(
                    self._readdirstatefile(), self._nodeconstants
                )
            return self._docket

        @propertycache
        def _rustmap(self):
            """
            Fills the Dirstatemap when called.
            """
            # ignore HG_PENDING because identity is used only for writing
            self.identity = util.filestat.frompath(
                self._opener.join(self._filename)
            )

            if self._use_dirstate_v2:
                if self.docket.uuid:
                    # TODO: use mmap when possible
                    data = self._opener.read(self.docket.data_filename())
                else:
                    data = b''
                self._rustmap = rustmod.DirstateMap.new_v2(
                    data, self.docket.data_size, self.docket.tree_metadata
                )
                parents = self.docket.parents
            else:
                self._rustmap, parents = rustmod.DirstateMap.new_v1(
                    self._use_dirstate_tree, self._readdirstatefile()
                )

            if parents and not self._dirtyparents:
                self.setparents(*parents)

            self.__contains__ = self._rustmap.__contains__
            self.__getitem__ = self._rustmap.__getitem__
            self.get = self._rustmap.get
            return self._rustmap

        def write(self, tr, st, now):
            if not self._use_dirstate_v2:
                p1, p2 = self.parents()
                packed = self._rustmap.write_v1(p1, p2, now)
                st.write(packed)
                st.close()
                self._dirtyparents = False
                return

            # We can only append to an existing data file if there is one
            can_append = self.docket.uuid is not None
            packed, meta, append = self._rustmap.write_v2(now, can_append)
            if append:
                docket = self.docket
                data_filename = docket.data_filename()
                if tr:
                    tr.add(data_filename, docket.data_size)
                with self._opener(data_filename, b'r+b') as fp:
                    fp.seek(docket.data_size)
                    assert fp.tell() == docket.data_size
                    written = fp.write(packed)
                    if written is not None:  # py2 may return None
                        assert written == len(packed), (written, len(packed))
                docket.data_size += len(packed)
                docket.parents = self.parents()
                docket.tree_metadata = meta
                st.write(docket.serialize())
                st.close()
            else:
                old_docket = self.docket
                new_docket = docketmod.DirstateDocket.with_new_uuid(
                    self.parents(), len(packed), meta
                )
                data_filename = new_docket.data_filename()
                if tr:
                    tr.add(data_filename, 0)
                self._opener.write(data_filename, packed)
                # Write the new docket after the new data file has been
                # written. Because `st` was opened with `atomictemp=True`,
                # the actual `.hg/dirstate` file is only affected on close.
                st.write(new_docket.serialize())
                st.close()
                # Remove the old data file after the new docket pointing to
                # the new data file was written.
                if old_docket.uuid:
                    data_filename = old_docket.data_filename()
                    unlink = lambda _tr=None: self._opener.unlink(data_filename)
                    if tr:
                        category = b"dirstate-v2-clean-" + old_docket.uuid
                        tr.addpostclose(category, unlink)
                    else:
                        unlink()
                self._docket = new_docket
            # Reload from the newly-written file
            util.clearcachedproperty(self, b"_rustmap")
            self._dirtyparents = False

        @propertycache
        def filefoldmap(self):
            """Returns a dictionary mapping normalized case paths to their
            non-normalized versions.
            """
            return self._rustmap.filefoldmapasdict()

        def hastrackeddir(self, d):
            return self._rustmap.hastrackeddir(d)

        def hasdir(self, d):
            return self._rustmap.hasdir(d)

        @propertycache
        def identity(self):
            self._rustmap
            return self.identity

        @property
        def nonnormalset(self):
            nonnorm = self._rustmap.non_normal_entries()
            return nonnorm

        @propertycache
        def otherparentset(self):
            otherparents = self._rustmap.other_parent_entries()
            return otherparents

        def non_normal_or_other_parent_paths(self):
            return self._rustmap.non_normal_or_other_parent_paths()

        @propertycache
        def dirfoldmap(self):
            f = {}
            normcase = util.normcase
            for name in self._rustmap.tracked_dirs():
                f[normcase(name)] = name
            return f

        def set_possibly_dirty(self, filename):
            """record that the current state of the file on disk is unknown"""
            entry = self[filename]
            entry.set_possibly_dirty()
            self._rustmap.set_v1(filename, entry)

        def __setitem__(self, key, value):
            assert isinstance(value, DirstateItem)
            self._rustmap.set_v1(key, value)
