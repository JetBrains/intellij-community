# dirstate.py - working directory tracking for mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import collections
import contextlib
import os
import stat
import uuid

from .i18n import _

from hgdemandimport import tracing

from . import (
    dirstatemap,
    encoding,
    error,
    match as matchmod,
    node,
    pathutil,
    policy,
    pycompat,
    scmutil,
    txnutil,
    util,
)

from .dirstateutils import (
    timestamp,
)

from .interfaces import (
    dirstate as intdirstate,
    util as interfaceutil,
)

parsers = policy.importmod('parsers')
rustmod = policy.importrust('dirstate')

HAS_FAST_DIRSTATE_V2 = rustmod is not None

propertycache = util.propertycache
filecache = scmutil.filecache
_rangemask = dirstatemap.rangemask

DirstateItem = dirstatemap.DirstateItem


class repocache(filecache):
    """filecache for files in .hg/"""

    def join(self, obj, fname):
        return obj._opener.join(fname)


class rootcache(filecache):
    """filecache for files in the repository root"""

    def join(self, obj, fname):
        return obj._join(fname)


def check_invalidated(func):
    """check that the func is called with a non-invalidated dirstate

    The dirstate is in an "invalidated state" after an error occured during its
    modification and remains so until we exited the top level scope that framed
    such change.
    """

    def wrap(self, *args, **kwargs):
        if self._invalidated_context:
            msg = 'calling `%s` after the dirstate was invalidated'
            msg %= func.__name__
            raise error.ProgrammingError(msg)
        return func(self, *args, **kwargs)

    return wrap


def requires_changing_parents(func):
    def wrap(self, *args, **kwargs):
        if not self.is_changing_parents:
            msg = 'calling `%s` outside of a changing_parents context'
            msg %= func.__name__
            raise error.ProgrammingError(msg)
        return func(self, *args, **kwargs)

    return check_invalidated(wrap)


def requires_changing_files(func):
    def wrap(self, *args, **kwargs):
        if not self.is_changing_files:
            msg = 'calling `%s` outside of a `changing_files`'
            msg %= func.__name__
            raise error.ProgrammingError(msg)
        return func(self, *args, **kwargs)

    return check_invalidated(wrap)


def requires_changing_any(func):
    def wrap(self, *args, **kwargs):
        if not self.is_changing_any:
            msg = 'calling `%s` outside of a changing context'
            msg %= func.__name__
            raise error.ProgrammingError(msg)
        return func(self, *args, **kwargs)

    return check_invalidated(wrap)


def requires_changing_files_or_status(func):
    def wrap(self, *args, **kwargs):
        if not (self.is_changing_files or self._running_status > 0):
            msg = (
                'calling `%s` outside of a changing_files '
                'or running_status context'
            )
            msg %= func.__name__
            raise error.ProgrammingError(msg)
        return func(self, *args, **kwargs)

    return check_invalidated(wrap)


CHANGE_TYPE_PARENTS = "parents"
CHANGE_TYPE_FILES = "files"


@interfaceutil.implementer(intdirstate.idirstate)
class dirstate:

    # used by largefile to avoid overwritting transaction callback
    _tr_key_suffix = b''

    def __init__(
        self,
        opener,
        ui,
        root,
        validate,
        sparsematchfn,
        nodeconstants,
        use_dirstate_v2,
        use_tracked_hint=False,
    ):
        """Create a new dirstate object.

        opener is an open()-like callable that can be used to open the
        dirstate file; root is the root of the directory tracked by
        the dirstate.
        """
        self._use_dirstate_v2 = use_dirstate_v2
        self._use_tracked_hint = use_tracked_hint
        self._nodeconstants = nodeconstants
        self._opener = opener
        self._validate = validate
        self._root = root
        # Either build a sparse-matcher or None if sparse is disabled
        self._sparsematchfn = sparsematchfn
        # ntpath.join(root, '') of Python 2.7.9 does not add sep if root is
        # UNC path pointing to root share (issue4557)
        self._rootdir = pathutil.normasprefix(root)
        # True is any internal state may be different
        self._dirty = False
        # True if the set of tracked file may be different
        self._dirty_tracked_set = False
        self._ui = ui
        self._filecache = {}
        # nesting level of `changing_parents` context
        self._changing_level = 0
        # the change currently underway
        self._change_type = None
        # number of open _running_status context
        self._running_status = 0
        # True if the current dirstate changing operations have been
        # invalidated (used to make sure all nested contexts have been exited)
        self._invalidated_context = False
        self._attached_to_a_transaction = False
        self._filename = b'dirstate'
        self._filename_th = b'dirstate-tracked-hint'
        self._pendingfilename = b'%s.pending' % self._filename
        self._plchangecallbacks = {}
        self._origpl = None
        self._mapcls = dirstatemap.dirstatemap
        # Access and cache cwd early, so we don't access it for the first time
        # after a working-copy update caused it to not exist (accessing it then
        # raises an exception).
        self._cwd

    def refresh(self):
        # XXX if this happens, you likely did not enter the `changing_xxx`
        # using `repo.dirstate`, so a later `repo.dirstate` accesss might call
        # `refresh`.
        if self.is_changing_any:
            msg = "refreshing the dirstate in the middle of a change"
            raise error.ProgrammingError(msg)
        if '_branch' in vars(self):
            del self._branch
        if '_map' in vars(self) and self._map.may_need_refresh():
            self.invalidate()

    def prefetch_parents(self):
        """make sure the parents are loaded

        Used to avoid a race condition.
        """
        self._pl

    @contextlib.contextmanager
    @check_invalidated
    def running_status(self, repo):
        """Wrap a status operation

        This context is not mutally exclusive with the `changing_*` context. It
        also do not warrant for the `wlock` to be taken.

        If the wlock is taken, this context will behave in a simple way, and
        ensure the data are scheduled for write when leaving the top level
        context.

        If the lock is not taken, it will only warrant that the data are either
        committed (written) and rolled back (invalidated) when exiting the top
        level context. The write/invalidate action must be performed by the
        wrapped code.


        The expected  logic is:

        A: read the dirstate
        B: run status
           This might make the dirstate dirty by updating cache,
           especially in Rust.
        C: do more "post status fixup if relevant
        D: try to take the w-lock (this will invalidate the changes if they were raced)
        E0: if dirstate changed on disk → discard change (done by dirstate internal)
        E1: elif lock was acquired → write the changes
        E2: else → discard the changes
        """
        has_lock = repo.currentwlock() is not None
        is_changing = self.is_changing_any
        tr = repo.currenttransaction()
        has_tr = tr is not None
        nested = bool(self._running_status)

        first_and_alone = not (is_changing or has_tr or nested)

        # enforce no change happened outside of a proper context.
        if first_and_alone and self._dirty:
            has_tr = repo.currenttransaction() is not None
            if not has_tr and self._changing_level == 0 and self._dirty:
                msg = "entering a status context, but dirstate is already dirty"
                raise error.ProgrammingError(msg)

        should_write = has_lock and not (nested or is_changing)

        self._running_status += 1
        try:
            yield
        except Exception:
            self.invalidate()
            raise
        finally:
            self._running_status -= 1
            if self._invalidated_context:
                should_write = False
                self.invalidate()

        if should_write:
            assert repo.currenttransaction() is tr
            self.write(tr)
        elif not has_lock:
            if self._dirty:
                msg = b'dirstate dirty while exiting an isolated status context'
                repo.ui.develwarn(msg)
                self.invalidate()

    @contextlib.contextmanager
    @check_invalidated
    def _changing(self, repo, change_type):
        if repo.currentwlock() is None:
            msg = b"trying to change the dirstate without holding the wlock"
            raise error.ProgrammingError(msg)

        has_tr = repo.currenttransaction() is not None
        if not has_tr and self._changing_level == 0 and self._dirty:
            msg = b"entering a changing context, but dirstate is already dirty"
            repo.ui.develwarn(msg)

        assert self._changing_level >= 0
        # different type of change are mutually exclusive
        if self._change_type is None:
            assert self._changing_level == 0
            self._change_type = change_type
        elif self._change_type != change_type:
            msg = (
                'trying to open "%s" dirstate-changing context while a "%s" is'
                ' already open'
            )
            msg %= (change_type, self._change_type)
            raise error.ProgrammingError(msg)
        should_write = False
        self._changing_level += 1
        try:
            yield
        except:  # re-raises
            self.invalidate()  # this will set `_invalidated_context`
            raise
        finally:
            assert self._changing_level > 0
            self._changing_level -= 1
            # If the dirstate is being invalidated, call invalidate again.
            # This will throw away anything added by a upper context and
            # reset the `_invalidated_context` flag when relevant
            if self._changing_level <= 0:
                self._change_type = None
                assert self._changing_level == 0
            if self._invalidated_context:
                # make sure we invalidate anything an upper context might
                # have changed.
                self.invalidate()
            else:
                should_write = self._changing_level <= 0
        tr = repo.currenttransaction()
        if has_tr != (tr is not None):
            if has_tr:
                m = "transaction vanished while changing dirstate"
            else:
                m = "transaction appeared while changing dirstate"
            raise error.ProgrammingError(m)
        if should_write:
            self.write(tr)

    @contextlib.contextmanager
    def changing_parents(self, repo):
        """Wrap a dirstate change related to a change of working copy parents

        This context scopes a series of dirstate modifications that match an
        update of the working copy parents (typically `hg update`, `hg merge`
        etc).

        The dirstate's methods that perform this kind of modifications require
        this context to be present before being called.
        Such methods are decorated with `@requires_changing_parents`.

        The new dirstate contents will be written to disk when the top-most
        `changing_parents` context exits successfully. If an exception is
        raised during a `changing_parents` context of any level, all changes
        are invalidated. If this context is open within an open transaction,
        the dirstate writing is delayed until that transaction is successfully
        committed (and the dirstate is invalidated on transaction abort).

        The `changing_parents` operation is mutually exclusive with the
        `changing_files` one.
        """
        with self._changing(repo, CHANGE_TYPE_PARENTS) as c:
            yield c

    @contextlib.contextmanager
    def changing_files(self, repo):
        """Wrap a dirstate change related to the set of tracked files

        This context scopes a series of dirstate modifications that change the
        set of tracked files. (typically `hg add`, `hg remove` etc) or some
        dirstate stored information (like `hg rename --after`) but preserve
        the working copy parents.

        The dirstate's methods that perform this kind of modifications require
        this context to be present before being called.
        Such methods are decorated with `@requires_changing_files`.

        The new dirstate contents will be written to disk when the top-most
        `changing_files` context exits successfully. If an exception is raised
        during a `changing_files` context of any level, all changes are
        invalidated.  If this context is open within an open transaction, the
        dirstate writing is delayed until that transaction is successfully
        committed (and the dirstate is invalidated on transaction abort).

        The `changing_files` operation is mutually exclusive with the
        `changing_parents` one.
        """
        with self._changing(repo, CHANGE_TYPE_FILES) as c:
            yield c

    # here to help migration to the new code
    def parentchange(self):
        msg = (
            "Mercurial 6.4 and later requires call to "
            "`dirstate.changing_parents(repo)`"
        )
        raise error.ProgrammingError(msg)

    @property
    def is_changing_any(self):
        """Returns true if the dirstate is in the middle of a set of changes.

        This returns True for any kind of change.
        """
        return self._changing_level > 0

    @property
    def is_changing_parents(self):
        """Returns true if the dirstate is in the middle of a set of changes
        that modify the dirstate parent.
        """
        if self._changing_level <= 0:
            return False
        return self._change_type == CHANGE_TYPE_PARENTS

    @property
    def is_changing_files(self):
        """Returns true if the dirstate is in the middle of a set of changes
        that modify the files tracked or their sources.
        """
        if self._changing_level <= 0:
            return False
        return self._change_type == CHANGE_TYPE_FILES

    @propertycache
    def _map(self):
        """Return the dirstate contents (see documentation for dirstatemap)."""
        return self._mapcls(
            self._ui,
            self._opener,
            self._root,
            self._nodeconstants,
            self._use_dirstate_v2,
        )

    @property
    def _sparsematcher(self):
        """The matcher for the sparse checkout.

        The working directory may not include every file from a manifest. The
        matcher obtained by this property will match a path if it is to be
        included in the working directory.

        When sparse if disabled, return None.
        """
        if self._sparsematchfn is None:
            return None
        # TODO there is potential to cache this property. For now, the matcher
        # is resolved on every access. (But the called function does use a
        # cache to keep the lookup fast.)
        return self._sparsematchfn()

    @repocache(b'branch')
    def _branch(self):
        f = None
        data = b''
        try:
            f, mode = txnutil.trypending(self._root, self._opener, b'branch')
            data = f.read().strip()
        except FileNotFoundError:
            pass
        finally:
            if f is not None:
                f.close()
        if not data:
            return b"default"
        return data

    @property
    def _pl(self):
        return self._map.parents()

    def hasdir(self, d):
        return self._map.hastrackeddir(d)

    @rootcache(b'.hgignore')
    def _ignore(self):
        files = self._ignorefiles()
        if not files:
            return matchmod.never()

        pats = [b'include:%s' % f for f in files]
        return matchmod.match(self._root, b'', [], pats, warn=self._ui.warn)

    @propertycache
    def _slash(self):
        return self._ui.configbool(b'ui', b'slash') and pycompat.ossep != b'/'

    @propertycache
    def _checklink(self):
        return util.checklink(self._root)

    @propertycache
    def _checkexec(self):
        return bool(util.checkexec(self._root))

    @propertycache
    def _checkcase(self):
        return not util.fscasesensitive(self._join(b'.hg'))

    def _join(self, f):
        # much faster than os.path.join()
        # it's safe because f is always a relative path
        return self._rootdir + f

    def flagfunc(self, buildfallback):
        """build a callable that returns flags associated with a filename

        The information is extracted from three possible layers:
        1. the file system if it supports the information
        2. the "fallback" information stored in the dirstate if any
        3. a more expensive mechanism inferring the flags from the parents.
        """

        # small hack to cache the result of buildfallback()
        fallback_func = []

        def get_flags(x):
            entry = None
            fallback_value = None
            try:
                st = os.lstat(self._join(x))
            except OSError:
                return b''

            if self._checklink:
                if util.statislink(st):
                    return b'l'
            else:
                entry = self.get_entry(x)
                if entry.has_fallback_symlink:
                    if entry.fallback_symlink:
                        return b'l'
                else:
                    if not fallback_func:
                        fallback_func.append(buildfallback())
                    fallback_value = fallback_func[0](x)
                    if b'l' in fallback_value:
                        return b'l'

            if self._checkexec:
                if util.statisexec(st):
                    return b'x'
            else:
                if entry is None:
                    entry = self.get_entry(x)
                if entry.has_fallback_exec:
                    if entry.fallback_exec:
                        return b'x'
                else:
                    if fallback_value is None:
                        if not fallback_func:
                            fallback_func.append(buildfallback())
                        fallback_value = fallback_func[0](x)
                    if b'x' in fallback_value:
                        return b'x'
            return b''

        return get_flags

    @propertycache
    def _cwd(self):
        # internal config: ui.forcecwd
        forcecwd = self._ui.config(b'ui', b'forcecwd')
        if forcecwd:
            return forcecwd
        return encoding.getcwd()

    def getcwd(self):
        """Return the path from which a canonical path is calculated.

        This path should be used to resolve file patterns or to convert
        canonical paths back to file paths for display. It shouldn't be
        used to get real file paths. Use vfs functions instead.
        """
        cwd = self._cwd
        if cwd == self._root:
            return b''
        # self._root ends with a path separator if self._root is '/' or 'C:\'
        rootsep = self._root
        if not util.endswithsep(rootsep):
            rootsep += pycompat.ossep
        if cwd.startswith(rootsep):
            return cwd[len(rootsep) :]
        else:
            # we're outside the repo. return an absolute path.
            return cwd

    def pathto(self, f, cwd=None):
        if cwd is None:
            cwd = self.getcwd()
        path = util.pathto(self._root, cwd, f)
        if self._slash:
            return util.pconvert(path)
        return path

    def get_entry(self, path):
        """return a DirstateItem for the associated path"""
        entry = self._map.get(path)
        if entry is None:
            return DirstateItem()
        return entry

    def __contains__(self, key):
        return key in self._map

    def __iter__(self):
        return iter(sorted(self._map))

    def items(self):
        return self._map.items()

    iteritems = items

    def parents(self):
        return [self._validate(p) for p in self._pl]

    def p1(self):
        return self._validate(self._pl[0])

    def p2(self):
        return self._validate(self._pl[1])

    @property
    def in_merge(self):
        """True if a merge is in progress"""
        return self._pl[1] != self._nodeconstants.nullid

    def branch(self):
        return encoding.tolocal(self._branch)

    @requires_changing_parents
    def setparents(self, p1, p2=None):
        """Set dirstate parents to p1 and p2.

        When moving from two parents to one, "merged" entries a
        adjusted to normal and previous copy records discarded and
        returned by the call.

        See localrepo.setparents()
        """
        if p2 is None:
            p2 = self._nodeconstants.nullid
        if self._changing_level == 0:
            raise ValueError(
                b"cannot set dirstate parent outside of "
                b"dirstate.changing_parents context manager"
            )

        self._dirty = True
        oldp2 = self._pl[1]
        if self._origpl is None:
            self._origpl = self._pl
        nullid = self._nodeconstants.nullid
        # True if we need to fold p2 related state back to a linear case
        fold_p2 = oldp2 != nullid and p2 == nullid
        return self._map.setparents(p1, p2, fold_p2=fold_p2)

    def setbranch(self, branch, transaction):
        self.__class__._branch.set(self, encoding.fromlocal(branch))
        if transaction is not None:
            self._setup_tr_abort(transaction)
            transaction.addfilegenerator(
                b'dirstate-3-branch%s' % self._tr_key_suffix,
                (b'branch',),
                self._write_branch,
                location=b'plain',
                post_finalize=True,
            )
            return

        vfs = self._opener
        with vfs(b'branch', b'w', atomictemp=True, checkambig=True) as f:
            self._write_branch(f)
            # make sure filecache has the correct stat info for _branch after
            # replacing the underlying file
            #
            # XXX do we actually need this,
            # refreshing the attribute is quite cheap
            ce = self._filecache[b'_branch']
            if ce:
                ce.refresh()

    def _write_branch(self, file_obj):
        file_obj.write(self._branch + b'\n')

    def invalidate(self):
        """Causes the next access to reread the dirstate.

        This is different from localrepo.invalidatedirstate() because it always
        rereads the dirstate. Use localrepo.invalidatedirstate() if you want to
        check whether the dirstate has changed before rereading it."""

        for a in ("_map", "_branch", "_ignore"):
            if a in self.__dict__:
                delattr(self, a)
        self._dirty = False
        self._dirty_tracked_set = False
        self._invalidated_context = bool(
            self._changing_level > 0
            or self._attached_to_a_transaction
            or self._running_status
        )
        self._origpl = None

    @requires_changing_any
    def copy(self, source, dest):
        """Mark dest as a copy of source. Unmark dest if source is None."""
        if source == dest:
            return
        self._dirty = True
        if source is not None:
            self._check_sparse(source)
            self._map.copymap[dest] = source
        else:
            self._map.copymap.pop(dest, None)

    def copied(self, file):
        return self._map.copymap.get(file, None)

    def copies(self):
        return self._map.copymap

    @requires_changing_files
    def set_tracked(self, filename, reset_copy=False):
        """a "public" method for generic code to mark a file as tracked

        This function is to be called outside of "update/merge" case. For
        example by a command like `hg add X`.

        if reset_copy is set, any existing copy information will be dropped.

        return True the file was previously untracked, False otherwise.
        """
        self._dirty = True
        entry = self._map.get(filename)
        if entry is None or not entry.tracked:
            self._check_new_tracked_filename(filename)
        pre_tracked = self._map.set_tracked(filename)
        if reset_copy:
            self._map.copymap.pop(filename, None)
        if pre_tracked:
            self._dirty_tracked_set = True
        return pre_tracked

    @requires_changing_files
    def set_untracked(self, filename):
        """a "public" method for generic code to mark a file as untracked

        This function is to be called outside of "update/merge" case. For
        example by a command like `hg remove X`.

        return True the file was previously tracked, False otherwise.
        """
        ret = self._map.set_untracked(filename)
        if ret:
            self._dirty = True
            self._dirty_tracked_set = True
        return ret

    @requires_changing_files_or_status
    def set_clean(self, filename, parentfiledata):
        """record that the current state of the file on disk is known to be clean"""
        self._dirty = True
        if not self._map[filename].tracked:
            self._check_new_tracked_filename(filename)
        (mode, size, mtime) = parentfiledata
        self._map.set_clean(filename, mode, size, mtime)

    @requires_changing_files_or_status
    def set_possibly_dirty(self, filename):
        """record that the current state of the file on disk is unknown"""
        self._dirty = True
        self._map.set_possibly_dirty(filename)

    @requires_changing_parents
    def update_file_p1(
        self,
        filename,
        p1_tracked,
    ):
        """Set a file as tracked in the parent (or not)

        This is to be called when adjust the dirstate to a new parent after an history
        rewriting operation.

        It should not be called during a merge (p2 != nullid) and only within
        a `with dirstate.changing_parents(repo):` context.
        """
        if self.in_merge:
            msg = b'update_file_reference should not be called when merging'
            raise error.ProgrammingError(msg)
        entry = self._map.get(filename)
        if entry is None:
            wc_tracked = False
        else:
            wc_tracked = entry.tracked
        if not (p1_tracked or wc_tracked):
            # the file is no longer relevant to anyone
            if self._map.get(filename) is not None:
                self._map.reset_state(filename)
                self._dirty = True
        elif (not p1_tracked) and wc_tracked:
            if entry is not None and entry.added:
                return  # avoid dropping copy information (maybe?)

        self._map.reset_state(
            filename,
            wc_tracked,
            p1_tracked,
            # the underlying reference might have changed, we will have to
            # check it.
            has_meaningful_mtime=False,
        )

    @requires_changing_parents
    def update_file(
        self,
        filename,
        wc_tracked,
        p1_tracked,
        p2_info=False,
        possibly_dirty=False,
        parentfiledata=None,
    ):
        """update the information about a file in the dirstate

        This is to be called when the direstates parent changes to keep track
        of what is the file situation in regards to the working copy and its parent.

        This function must be called within a `dirstate.changing_parents` context.

        note: the API is at an early stage and we might need to adjust it
        depending of what information ends up being relevant and useful to
        other processing.
        """
        self._update_file(
            filename=filename,
            wc_tracked=wc_tracked,
            p1_tracked=p1_tracked,
            p2_info=p2_info,
            possibly_dirty=possibly_dirty,
            parentfiledata=parentfiledata,
        )

    def hacky_extension_update_file(self, *args, **kwargs):
        """NEVER USE THIS, YOU DO NOT NEED IT

        This function is a variant of "update_file" to be called by a small set
        of extensions, it also adjust the internal state of file, but can be
        called outside an `changing_parents` context.

        A very small number of extension meddle with the working copy content
        in a way that requires to adjust the dirstate accordingly. At the time
        this command is written they are :
        - keyword,
        - largefile,
        PLEASE DO NOT GROW THIS LIST ANY FURTHER.

        This function could probably be replaced by more semantic one (like
        "adjust expected size" or "always revalidate file content", etc)
        however at the time where this is writen, this is too much of a detour
        to be considered.
        """
        if not (self._changing_level > 0 or self._running_status > 0):
            msg = "requires a changes context"
            raise error.ProgrammingError(msg)
        self._update_file(
            *args,
            **kwargs,
        )

    def _update_file(
        self,
        filename,
        wc_tracked,
        p1_tracked,
        p2_info=False,
        possibly_dirty=False,
        parentfiledata=None,
    ):

        # note: I do not think we need to double check name clash here since we
        # are in a update/merge case that should already have taken care of
        # this. The test agrees

        self._dirty = True
        old_entry = self._map.get(filename)
        if old_entry is None:
            prev_tracked = False
        else:
            prev_tracked = old_entry.tracked
        if prev_tracked != wc_tracked:
            self._dirty_tracked_set = True

        self._map.reset_state(
            filename,
            wc_tracked,
            p1_tracked,
            p2_info=p2_info,
            has_meaningful_mtime=not possibly_dirty,
            parentfiledata=parentfiledata,
        )

    def _check_new_tracked_filename(self, filename):
        scmutil.checkfilename(filename)
        if self._map.hastrackeddir(filename):
            msg = _(b'directory %r already in dirstate')
            msg %= pycompat.bytestr(filename)
            raise error.Abort(msg)
        # shadows
        for d in pathutil.finddirs(filename):
            if self._map.hastrackeddir(d):
                break
            entry = self._map.get(d)
            if entry is not None and not entry.removed:
                msg = _(b'file %r in dirstate clashes with %r')
                msg %= (pycompat.bytestr(d), pycompat.bytestr(filename))
                raise error.Abort(msg)
        self._check_sparse(filename)

    def _check_sparse(self, filename):
        """Check that a filename is inside the sparse profile"""
        sparsematch = self._sparsematcher
        if sparsematch is not None and not sparsematch.always():
            if not sparsematch(filename):
                msg = _(b"cannot add '%s' - it is outside the sparse checkout")
                hint = _(
                    b'include file with `hg debugsparse --include <pattern>` or use '
                    b'`hg add -s <file>` to include file directory while adding'
                )
                raise error.Abort(msg % filename, hint=hint)

    def _discoverpath(self, path, normed, ignoremissing, exists, storemap):
        if exists is None:
            exists = os.path.lexists(os.path.join(self._root, path))
        if not exists:
            # Maybe a path component exists
            if not ignoremissing and b'/' in path:
                d, f = path.rsplit(b'/', 1)
                d = self._normalize(d, False, ignoremissing, None)
                folded = d + b"/" + f
            else:
                # No path components, preserve original case
                folded = path
        else:
            # recursively normalize leading directory components
            # against dirstate
            if b'/' in normed:
                d, f = normed.rsplit(b'/', 1)
                d = self._normalize(d, False, ignoremissing, True)
                r = self._root + b"/" + d
                folded = d + b"/" + util.fspath(f, r)
            else:
                folded = util.fspath(normed, self._root)
            storemap[normed] = folded

        return folded

    def _normalizefile(self, path, isknown, ignoremissing=False, exists=None):
        normed = util.normcase(path)
        folded = self._map.filefoldmap.get(normed, None)
        if folded is None:
            if isknown:
                folded = path
            else:
                folded = self._discoverpath(
                    path, normed, ignoremissing, exists, self._map.filefoldmap
                )
        return folded

    def _normalize(self, path, isknown, ignoremissing=False, exists=None):
        normed = util.normcase(path)
        folded = self._map.filefoldmap.get(normed, None)
        if folded is None:
            folded = self._map.dirfoldmap.get(normed, None)
        if folded is None:
            if isknown:
                folded = path
            else:
                # store discovered result in dirfoldmap so that future
                # normalizefile calls don't start matching directories
                folded = self._discoverpath(
                    path, normed, ignoremissing, exists, self._map.dirfoldmap
                )
        return folded

    def normalize(self, path, isknown=False, ignoremissing=False):
        """
        normalize the case of a pathname when on a casefolding filesystem

        isknown specifies whether the filename came from walking the
        disk, to avoid extra filesystem access.

        If ignoremissing is True, missing path are returned
        unchanged. Otherwise, we try harder to normalize possibly
        existing path components.

        The normalized case is determined based on the following precedence:

        - version of name already stored in the dirstate
        - version of name stored on disk
        - version provided via command arguments
        """

        if self._checkcase:
            return self._normalize(path, isknown, ignoremissing)
        return path

    # XXX this method is barely used, as a result:
    # - its semantic is unclear
    # - do we really needs it ?
    @requires_changing_parents
    def clear(self):
        self._map.clear()
        self._dirty = True

    @requires_changing_parents
    def rebuild(self, parent, allfiles, changedfiles=None):
        matcher = self._sparsematcher
        if matcher is not None and not matcher.always():
            # should not add non-matching files
            allfiles = [f for f in allfiles if matcher(f)]
            if changedfiles:
                changedfiles = [f for f in changedfiles if matcher(f)]

            if changedfiles is not None:
                # these files will be deleted from the dirstate when they are
                # not found to be in allfiles
                dirstatefilestoremove = {f for f in self if not matcher(f)}
                changedfiles = dirstatefilestoremove.union(changedfiles)

        if changedfiles is None:
            # Rebuild entire dirstate
            to_lookup = allfiles
            to_drop = []
            self.clear()
        elif len(changedfiles) < 10:
            # Avoid turning allfiles into a set, which can be expensive if it's
            # large.
            to_lookup = []
            to_drop = []
            for f in changedfiles:
                if f in allfiles:
                    to_lookup.append(f)
                else:
                    to_drop.append(f)
        else:
            changedfilesset = set(changedfiles)
            to_lookup = changedfilesset & set(allfiles)
            to_drop = changedfilesset - to_lookup

        if self._origpl is None:
            self._origpl = self._pl
        self._map.setparents(parent, self._nodeconstants.nullid)

        for f in to_lookup:
            if self.in_merge:
                self.set_tracked(f)
            else:
                self._map.reset_state(
                    f,
                    wc_tracked=True,
                    p1_tracked=True,
                )
        for f in to_drop:
            self._map.reset_state(f)

        self._dirty = True

    def _setup_tr_abort(self, tr):
        """make sure we invalidate the current change on abort"""
        if tr is None:
            return

        def on_abort(tr):
            self._attached_to_a_transaction = False
            self.invalidate()

        tr.addabort(
            b'dirstate-invalidate%s' % self._tr_key_suffix,
            on_abort,
        )

    def write(self, tr):
        if not self._dirty:
            return
        # make sure we don't request a write of invalidated content
        # XXX move before the dirty check once `unlock` stop calling `write`
        assert not self._invalidated_context

        write_key = self._use_tracked_hint and self._dirty_tracked_set
        if tr:

            self._setup_tr_abort(tr)
            self._attached_to_a_transaction = True

            def on_success(f):
                self._attached_to_a_transaction = False
                self._writedirstate(tr, f),

            # delay writing in-memory changes out
            tr.addfilegenerator(
                b'dirstate-1-main%s' % self._tr_key_suffix,
                (self._filename,),
                on_success,
                location=b'plain',
                post_finalize=True,
            )
            if write_key:
                tr.addfilegenerator(
                    b'dirstate-2-key-post%s' % self._tr_key_suffix,
                    (self._filename_th,),
                    lambda f: self._write_tracked_hint(tr, f),
                    location=b'plain',
                    post_finalize=True,
                )
            return

        file = lambda f: self._opener(f, b"w", atomictemp=True, checkambig=True)
        with file(self._filename) as f:
            self._writedirstate(tr, f)
        if write_key:
            # we update the key-file after writing to make sure reader have a
            # key that match the newly written content
            with file(self._filename_th) as f:
                self._write_tracked_hint(tr, f)

    def delete_tracked_hint(self):
        """remove the tracked_hint file

        To be used by format downgrades operation"""
        self._opener.unlink(self._filename_th)
        self._use_tracked_hint = False

    def addparentchangecallback(self, category, callback):
        """add a callback to be called when the wd parents are changed

        Callback will be called with the following arguments:
            dirstate, (oldp1, oldp2), (newp1, newp2)

        Category is a unique identifier to allow overwriting an old callback
        with a newer callback.
        """
        self._plchangecallbacks[category] = callback

    def _writedirstate(self, tr, st):
        # make sure we don't write invalidated content
        assert not self._invalidated_context
        # notify callbacks about parents change
        if self._origpl is not None and self._origpl != self._pl:
            for c, callback in sorted(self._plchangecallbacks.items()):
                callback(self, self._origpl, self._pl)
            self._origpl = None
        self._map.write(tr, st)
        self._dirty = False
        self._dirty_tracked_set = False

    def _write_tracked_hint(self, tr, f):
        key = node.hex(uuid.uuid4().bytes)
        f.write(b"1\n%s\n" % key)  # 1 is the format version

    def _dirignore(self, f):
        if self._ignore(f):
            return True
        for p in pathutil.finddirs(f):
            if self._ignore(p):
                return True
        return False

    def _ignorefiles(self):
        files = []
        if os.path.exists(self._join(b'.hgignore')):
            files.append(self._join(b'.hgignore'))
        for name, path in self._ui.configitems(b"ui"):
            if name == b'ignore' or name.startswith(b'ignore.'):
                # we need to use os.path.join here rather than self._join
                # because path is arbitrary and user-specified
                files.append(os.path.join(self._rootdir, util.expandpath(path)))
        return files

    def _ignorefileandline(self, f):
        files = collections.deque(self._ignorefiles())
        visited = set()
        while files:
            i = files.popleft()
            patterns = matchmod.readpatternfile(
                i, self._ui.warn, sourceinfo=True
            )
            for pattern, lineno, line in patterns:
                kind, p = matchmod._patsplit(pattern, b'glob')
                if kind == b"subinclude":
                    if p not in visited:
                        files.append(p)
                    continue
                m = matchmod.match(
                    self._root, b'', [], [pattern], warn=self._ui.warn
                )
                if m(f):
                    return (i, lineno, line)
            visited.add(i)
        return (None, -1, b"")

    def _walkexplicit(self, match, subrepos):
        """Get stat data about the files explicitly specified by match.

        Return a triple (results, dirsfound, dirsnotfound).
        - results is a mapping from filename to stat result. It also contains
          listings mapping subrepos and .hg to None.
        - dirsfound is a list of files found to be directories.
        - dirsnotfound is a list of files that the dirstate thinks are
          directories and that were not found."""

        def badtype(mode):
            kind = _(b'unknown')
            if stat.S_ISCHR(mode):
                kind = _(b'character device')
            elif stat.S_ISBLK(mode):
                kind = _(b'block device')
            elif stat.S_ISFIFO(mode):
                kind = _(b'fifo')
            elif stat.S_ISSOCK(mode):
                kind = _(b'socket')
            elif stat.S_ISDIR(mode):
                kind = _(b'directory')
            return _(b'unsupported file type (type is %s)') % kind

        badfn = match.bad
        dmap = self._map
        lstat = os.lstat
        getkind = stat.S_IFMT
        dirkind = stat.S_IFDIR
        regkind = stat.S_IFREG
        lnkkind = stat.S_IFLNK
        join = self._join
        dirsfound = []
        foundadd = dirsfound.append
        dirsnotfound = []
        notfoundadd = dirsnotfound.append

        if not match.isexact() and self._checkcase:
            normalize = self._normalize
        else:
            normalize = None

        files = sorted(match.files())
        subrepos.sort()
        i, j = 0, 0
        while i < len(files) and j < len(subrepos):
            subpath = subrepos[j] + b"/"
            if files[i] < subpath:
                i += 1
                continue
            while i < len(files) and files[i].startswith(subpath):
                del files[i]
            j += 1

        if not files or b'' in files:
            files = [b'']
            # constructing the foldmap is expensive, so don't do it for the
            # common case where files is ['']
            normalize = None
        results = dict.fromkeys(subrepos)
        results[b'.hg'] = None

        for ff in files:
            if normalize:
                nf = normalize(ff, False, True)
            else:
                nf = ff
            if nf in results:
                continue

            try:
                st = lstat(join(nf))
                kind = getkind(st.st_mode)
                if kind == dirkind:
                    if nf in dmap:
                        # file replaced by dir on disk but still in dirstate
                        results[nf] = None
                    foundadd((nf, ff))
                elif kind == regkind or kind == lnkkind:
                    results[nf] = st
                else:
                    badfn(ff, badtype(kind))
                    if nf in dmap:
                        results[nf] = None
            except (OSError) as inst:
                # nf not found on disk - it is dirstate only
                if nf in dmap:  # does it exactly match a missing file?
                    results[nf] = None
                else:  # does it match a missing directory?
                    if self._map.hasdir(nf):
                        notfoundadd(nf)
                    else:
                        badfn(ff, encoding.strtolocal(inst.strerror))

        # match.files() may contain explicitly-specified paths that shouldn't
        # be taken; drop them from the list of files found. dirsfound/notfound
        # aren't filtered here because they will be tested later.
        if match.anypats():
            for f in list(results):
                if f == b'.hg' or f in subrepos:
                    # keep sentinel to disable further out-of-repo walks
                    continue
                if not match(f):
                    del results[f]

        # Case insensitive filesystems cannot rely on lstat() failing to detect
        # a case-only rename.  Prune the stat object for any file that does not
        # match the case in the filesystem, if there are multiple files that
        # normalize to the same path.
        if match.isexact() and self._checkcase:
            normed = {}

            for f, st in results.items():
                if st is None:
                    continue

                nc = util.normcase(f)
                paths = normed.get(nc)

                if paths is None:
                    paths = set()
                    normed[nc] = paths

                paths.add(f)

            for norm, paths in normed.items():
                if len(paths) > 1:
                    for path in paths:
                        folded = self._discoverpath(
                            path, norm, True, None, self._map.dirfoldmap
                        )
                        if path != folded:
                            results[path] = None

        return results, dirsfound, dirsnotfound

    def walk(self, match, subrepos, unknown, ignored, full=True):
        """
        Walk recursively through the directory tree, finding all files
        matched by match.

        If full is False, maybe skip some known-clean files.

        Return a dict mapping filename to stat-like object (either
        mercurial.osutil.stat instance or return value of os.stat()).

        """
        # full is a flag that extensions that hook into walk can use -- this
        # implementation doesn't use it at all. This satisfies the contract
        # because we only guarantee a "maybe".

        if ignored:
            ignore = util.never
            dirignore = util.never
        elif unknown:
            ignore = self._ignore
            dirignore = self._dirignore
        else:
            # if not unknown and not ignored, drop dir recursion and step 2
            ignore = util.always
            dirignore = util.always

        if self._sparsematchfn is not None:
            em = matchmod.exact(match.files())
            sm = matchmod.unionmatcher([self._sparsematcher, em])
            match = matchmod.intersectmatchers(match, sm)

        matchfn = match.matchfn
        matchalways = match.always()
        matchtdir = match.traversedir
        dmap = self._map
        listdir = util.listdir
        lstat = os.lstat
        dirkind = stat.S_IFDIR
        regkind = stat.S_IFREG
        lnkkind = stat.S_IFLNK
        join = self._join

        exact = skipstep3 = False
        if match.isexact():  # match.exact
            exact = True
            dirignore = util.always  # skip step 2
        elif match.prefix():  # match.match, no patterns
            skipstep3 = True

        if not exact and self._checkcase:
            normalize = self._normalize
            normalizefile = self._normalizefile
            skipstep3 = False
        else:
            normalize = self._normalize
            normalizefile = None

        # step 1: find all explicit files
        results, work, dirsnotfound = self._walkexplicit(match, subrepos)
        if matchtdir:
            for d in work:
                matchtdir(d[0])
            for d in dirsnotfound:
                matchtdir(d)

        skipstep3 = skipstep3 and not (work or dirsnotfound)
        work = [d for d in work if not dirignore(d[0])]

        # step 2: visit subdirectories
        def traverse(work, alreadynormed):
            wadd = work.append
            while work:
                tracing.counter('dirstate.walk work', len(work))
                nd = work.pop()
                visitentries = match.visitchildrenset(nd)
                if not visitentries:
                    continue
                if visitentries == b'this' or visitentries == b'all':
                    visitentries = None
                skip = None
                if nd != b'':
                    skip = b'.hg'
                try:
                    with tracing.log('dirstate.walk.traverse listdir %s', nd):
                        entries = listdir(join(nd), stat=True, skip=skip)
                except (PermissionError, FileNotFoundError) as inst:
                    match.bad(
                        self.pathto(nd), encoding.strtolocal(inst.strerror)
                    )
                    continue
                for f, kind, st in entries:
                    # Some matchers may return files in the visitentries set,
                    # instead of 'this', if the matcher explicitly mentions them
                    # and is not an exactmatcher. This is acceptable; we do not
                    # make any hard assumptions about file-or-directory below
                    # based on the presence of `f` in visitentries. If
                    # visitchildrenset returned a set, we can always skip the
                    # entries *not* in the set it provided regardless of whether
                    # they're actually a file or a directory.
                    if visitentries and f not in visitentries:
                        continue
                    if normalizefile:
                        # even though f might be a directory, we're only
                        # interested in comparing it to files currently in the
                        # dmap -- therefore normalizefile is enough
                        nf = normalizefile(
                            nd and (nd + b"/" + f) or f, True, True
                        )
                    else:
                        nf = nd and (nd + b"/" + f) or f
                    if nf not in results:
                        if kind == dirkind:
                            if not ignore(nf):
                                if matchtdir:
                                    matchtdir(nf)
                                wadd(nf)
                            if nf in dmap and (matchalways or matchfn(nf)):
                                results[nf] = None
                        elif kind == regkind or kind == lnkkind:
                            if nf in dmap:
                                if matchalways or matchfn(nf):
                                    results[nf] = st
                            elif (matchalways or matchfn(nf)) and not ignore(
                                nf
                            ):
                                # unknown file -- normalize if necessary
                                if not alreadynormed:
                                    nf = normalize(nf, False, True)
                                results[nf] = st
                        elif nf in dmap and (matchalways or matchfn(nf)):
                            results[nf] = None

        for nd, d in work:
            # alreadynormed means that processwork doesn't have to do any
            # expensive directory normalization
            alreadynormed = not normalize or nd == d
            traverse([d], alreadynormed)

        for s in subrepos:
            del results[s]
        del results[b'.hg']

        # step 3: visit remaining files from dmap
        if not skipstep3 and not exact:
            # If a dmap file is not in results yet, it was either
            # a) not matching matchfn b) ignored, c) missing, or d) under a
            # symlink directory.
            if not results and matchalways:
                visit = [f for f in dmap]
            else:
                visit = [f for f in dmap if f not in results and matchfn(f)]
            visit.sort()

            if unknown:
                # unknown == True means we walked all dirs under the roots
                # that wasn't ignored, and everything that matched was stat'ed
                # and is already in results.
                # The rest must thus be ignored or under a symlink.
                audit_path = pathutil.pathauditor(self._root, cached=True)

                for nf in iter(visit):
                    # If a stat for the same file was already added with a
                    # different case, don't add one for this, since that would
                    # make it appear as if the file exists under both names
                    # on disk.
                    if (
                        normalizefile
                        and normalizefile(nf, True, True) in results
                    ):
                        results[nf] = None
                    # Report ignored items in the dmap as long as they are not
                    # under a symlink directory.
                    elif audit_path.check(nf):
                        try:
                            results[nf] = lstat(join(nf))
                            # file was just ignored, no links, and exists
                        except OSError:
                            # file doesn't exist
                            results[nf] = None
                    else:
                        # It's either missing or under a symlink directory
                        # which we in this case report as missing
                        results[nf] = None
            else:
                # We may not have walked the full directory tree above,
                # so stat and check everything we missed.
                iv = iter(visit)
                for st in util.statfiles([join(i) for i in visit]):
                    results[next(iv)] = st
        return results

    def _rust_status(self, matcher, list_clean, list_ignored, list_unknown):
        if self._sparsematchfn is not None:
            em = matchmod.exact(matcher.files())
            sm = matchmod.unionmatcher([self._sparsematcher, em])
            matcher = matchmod.intersectmatchers(matcher, sm)
        # Force Rayon (Rust parallelism library) to respect the number of
        # workers. This is a temporary workaround until Rust code knows
        # how to read the config file.
        numcpus = self._ui.configint(b"worker", b"numcpus")
        if numcpus is not None:
            encoding.environ.setdefault(b'RAYON_NUM_THREADS', b'%d' % numcpus)

        workers_enabled = self._ui.configbool(b"worker", b"enabled", True)
        if not workers_enabled:
            encoding.environ[b"RAYON_NUM_THREADS"] = b"1"

        (
            lookup,
            modified,
            added,
            removed,
            deleted,
            clean,
            ignored,
            unknown,
            warnings,
            bad,
            traversed,
            dirty,
        ) = rustmod.status(
            self._map._map,
            matcher,
            self._rootdir,
            self._ignorefiles(),
            self._checkexec,
            bool(list_clean),
            bool(list_ignored),
            bool(list_unknown),
            bool(matcher.traversedir),
        )

        self._dirty |= dirty

        if matcher.traversedir:
            for dir in traversed:
                matcher.traversedir(dir)

        if self._ui.warn:
            for item in warnings:
                if isinstance(item, tuple):
                    file_path, syntax = item
                    msg = _(b"%s: ignoring invalid syntax '%s'\n") % (
                        file_path,
                        syntax,
                    )
                    self._ui.warn(msg)
                else:
                    msg = _(b"skipping unreadable pattern file '%s': %s\n")
                    self._ui.warn(
                        msg
                        % (
                            pathutil.canonpath(
                                self._rootdir, self._rootdir, item
                            ),
                            b"No such file or directory",
                        )
                    )

        for fn, message in sorted(bad):
            matcher.bad(fn, encoding.strtolocal(message))

        status = scmutil.status(
            modified=modified,
            added=added,
            removed=removed,
            deleted=deleted,
            unknown=unknown,
            ignored=ignored,
            clean=clean,
        )
        return (lookup, status)

    def status(self, match, subrepos, ignored, clean, unknown):
        """Determine the status of the working copy relative to the
        dirstate and return a pair of (unsure, status), where status is of type
        scmutil.status and:

          unsure:
            files that might have been modified since the dirstate was
            written, but need to be read to be sure (size is the same
            but mtime differs)
          status.modified:
            files that have definitely been modified since the dirstate
            was written (different size or mode)
          status.clean:
            files that have definitely not been modified since the
            dirstate was written
        """
        if not self._running_status:
            msg = "Calling `status` outside a `running_status` context"
            raise error.ProgrammingError(msg)
        listignored, listclean, listunknown = ignored, clean, unknown
        lookup, modified, added, unknown, ignored = [], [], [], [], []
        removed, deleted, clean = [], [], []

        dmap = self._map
        dmap.preload()

        use_rust = True

        if rustmod is None:
            use_rust = False
        elif self._checkcase:
            # Case-insensitive filesystems are not handled yet
            use_rust = False
        elif subrepos:
            use_rust = False

        # Get the time from the filesystem so we can disambiguate files that
        # appear modified in the present or future.
        try:
            mtime_boundary = timestamp.get_fs_now(self._opener)
        except OSError:
            # In largefiles or readonly context
            mtime_boundary = None

        if use_rust:
            try:
                res = self._rust_status(
                    match, listclean, listignored, listunknown
                )
                return res + (mtime_boundary,)
            except rustmod.FallbackError:
                pass

        def noop(f):
            pass

        dcontains = dmap.__contains__
        dget = dmap.__getitem__
        ladd = lookup.append  # aka "unsure"
        madd = modified.append
        aadd = added.append
        uadd = unknown.append if listunknown else noop
        iadd = ignored.append if listignored else noop
        radd = removed.append
        dadd = deleted.append
        cadd = clean.append if listclean else noop
        mexact = match.exact
        dirignore = self._dirignore
        checkexec = self._checkexec
        checklink = self._checklink
        copymap = self._map.copymap

        # We need to do full walks when either
        # - we're listing all clean files, or
        # - match.traversedir does something, because match.traversedir should
        #   be called for every dir in the working dir
        full = listclean or match.traversedir is not None
        for fn, st in self.walk(
            match, subrepos, listunknown, listignored, full=full
        ).items():
            if not dcontains(fn):
                if (listignored or mexact(fn)) and dirignore(fn):
                    if listignored:
                        iadd(fn)
                else:
                    uadd(fn)
                continue

            t = dget(fn)
            mode = t.mode
            size = t.size

            if not st and t.tracked:
                dadd(fn)
            elif t.p2_info:
                madd(fn)
            elif t.added:
                aadd(fn)
            elif t.removed:
                radd(fn)
            elif t.tracked:
                if not checklink and t.has_fallback_symlink:
                    # If the file system does not support symlink, the mode
                    # might not be correctly stored in the dirstate, so do not
                    # trust it.
                    ladd(fn)
                elif not checkexec and t.has_fallback_exec:
                    # If the file system does not support exec bits, the mode
                    # might not be correctly stored in the dirstate, so do not
                    # trust it.
                    ladd(fn)
                elif (
                    size >= 0
                    and (
                        (size != st.st_size and size != st.st_size & _rangemask)
                        or ((mode ^ st.st_mode) & 0o100 and checkexec)
                    )
                    or fn in copymap
                ):
                    if stat.S_ISLNK(st.st_mode) and size != st.st_size:
                        # issue6456: Size returned may be longer due to
                        # encryption on EXT-4 fscrypt, undecided.
                        ladd(fn)
                    else:
                        madd(fn)
                elif not t.mtime_likely_equal_to(timestamp.mtime_of(st)):
                    # There might be a change in the future if for example the
                    # internal clock is off, but this is a case where the issues
                    # the user would face would be a lot worse and there is
                    # nothing we can really do.
                    ladd(fn)
                elif listclean:
                    cadd(fn)
        status = scmutil.status(
            modified, added, removed, deleted, unknown, ignored, clean
        )
        return (lookup, status, mtime_boundary)

    def matches(self, match):
        """
        return files in the dirstate (in whatever state) filtered by match
        """
        dmap = self._map
        if rustmod is not None:
            dmap = self._map._map

        if match.always():
            return dmap.keys()
        files = match.files()
        if match.isexact():
            # fast path -- filter the other way around, since typically files is
            # much smaller than dmap
            return [f for f in files if f in dmap]
        if match.prefix() and all(fn in dmap for fn in files):
            # fast path -- all the values are known to be files, so just return
            # that
            return list(files)
        return [f for f in dmap if match(f)]

    def all_file_names(self):
        """list all filename currently used by this dirstate

        This is only used to do `hg rollback` related backup in the transaction
        """
        files = [b'branch']
        if self._opener.exists(self._filename):
            files.append(self._filename)
            if self._use_dirstate_v2:
                files.append(self._map.docket.data_filename())
        return tuple(files)

    def verify(self, m1, m2, p1, narrow_matcher=None):
        """
        check the dirstate contents against the parent manifest and yield errors
        """
        missing_from_p1 = _(
            b"%s marked as tracked in p1 (%s) but not in manifest1\n"
        )
        unexpected_in_p1 = _(b"%s marked as added, but also in manifest1\n")
        missing_from_ps = _(
            b"%s marked as modified, but not in either manifest\n"
        )
        missing_from_ds = _(
            b"%s in manifest1, but not marked as tracked in p1 (%s)\n"
        )
        for f, entry in self.items():
            if entry.p1_tracked:
                if entry.modified and f not in m1 and f not in m2:
                    yield missing_from_ps % f
                elif f not in m1:
                    yield missing_from_p1 % (f, node.short(p1))
            if entry.added and f in m1:
                yield unexpected_in_p1 % f
        for f in m1:
            if narrow_matcher is not None and not narrow_matcher(f):
                continue
            entry = self.get_entry(f)
            if not entry.p1_tracked:
                yield missing_from_ds % (f, node.short(p1))
