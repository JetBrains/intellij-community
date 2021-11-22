from __future__ import absolute_import

import contextlib
import errno
import os
import posixpath
import stat

from .i18n import _
from . import (
    encoding,
    error,
    policy,
    pycompat,
    util,
)

if pycompat.TYPE_CHECKING:
    from typing import (
        Any,
        Callable,
        Iterator,
        Optional,
    )


rustdirs = policy.importrust('dirstate', 'Dirs')
parsers = policy.importmod('parsers')


def _lowerclean(s):
    # type: (bytes) -> bytes
    return encoding.hfsignoreclean(s.lower())


class pathauditor(object):
    """ensure that a filesystem path contains no banned components.
    the following properties of a path are checked:

    - ends with a directory separator
    - under top-level .hg
    - starts at the root of a windows drive
    - contains ".."

    More check are also done about the file system states:
    - traverses a symlink (e.g. a/symlink_here/b)
    - inside a nested repository (a callback can be used to approve
      some nested repositories, e.g., subrepositories)

    The file system checks are only done when 'realfs' is set to True (the
    default). They should be disable then we are auditing path for operation on
    stored history.

    If 'cached' is set to True, audited paths and sub-directories are cached.
    Be careful to not keep the cache of unmanaged directories for long because
    audited paths may be replaced with symlinks.
    """

    def __init__(self, root, callback=None, realfs=True, cached=False):
        self.audited = set()
        self.auditeddir = set()
        self.root = root
        self._realfs = realfs
        self._cached = cached
        self.callback = callback
        if os.path.lexists(root) and not util.fscasesensitive(root):
            self.normcase = util.normcase
        else:
            self.normcase = lambda x: x

    def __call__(self, path, mode=None):
        # type: (bytes, Optional[Any]) -> None
        """Check the relative path.
        path may contain a pattern (e.g. foodir/**.txt)"""

        path = util.localpath(path)
        normpath = self.normcase(path)
        if normpath in self.audited:
            return
        # AIX ignores "/" at end of path, others raise EISDIR.
        if util.endswithsep(path):
            raise error.Abort(_(b"path ends in directory separator: %s") % path)
        parts = util.splitpath(path)
        if (
            os.path.splitdrive(path)[0]
            or _lowerclean(parts[0]) in (b'.hg', b'.hg.', b'')
            or pycompat.ospardir in parts
        ):
            raise error.Abort(_(b"path contains illegal component: %s") % path)
        # Windows shortname aliases
        for p in parts:
            if b"~" in p:
                first, last = p.split(b"~", 1)
                if last.isdigit() and first.upper() in [b"HG", b"HG8B6C"]:
                    raise error.Abort(
                        _(b"path contains illegal component: %s") % path
                    )
        if b'.hg' in _lowerclean(path):
            lparts = [_lowerclean(p) for p in parts]
            for p in b'.hg', b'.hg.':
                if p in lparts[1:]:
                    pos = lparts.index(p)
                    base = os.path.join(*parts[:pos])
                    raise error.Abort(
                        _(b"path '%s' is inside nested repo %r")
                        % (path, pycompat.bytestr(base))
                    )

        normparts = util.splitpath(normpath)
        assert len(parts) == len(normparts)

        parts.pop()
        normparts.pop()
        # It's important that we check the path parts starting from the root.
        # We don't want to add "foo/bar/baz" to auditeddir before checking if
        # there's a "foo/.hg" directory. This also means we won't accidentally
        # traverse a symlink into some other filesystem (which is potentially
        # expensive to access).
        for i in range(len(parts)):
            prefix = pycompat.ossep.join(parts[: i + 1])
            normprefix = pycompat.ossep.join(normparts[: i + 1])
            if normprefix in self.auditeddir:
                continue
            if self._realfs:
                self._checkfs(prefix, path)
            if self._cached:
                self.auditeddir.add(normprefix)

        if self._cached:
            self.audited.add(normpath)

    def _checkfs(self, prefix, path):
        # type: (bytes, bytes) -> None
        """raise exception if a file system backed check fails"""
        curpath = os.path.join(self.root, prefix)
        try:
            st = os.lstat(curpath)
        except OSError as err:
            # EINVAL can be raised as invalid path syntax under win32.
            # They must be ignored for patterns can be checked too.
            if err.errno not in (errno.ENOENT, errno.ENOTDIR, errno.EINVAL):
                raise
        else:
            if stat.S_ISLNK(st.st_mode):
                msg = _(b'path %r traverses symbolic link %r') % (
                    pycompat.bytestr(path),
                    pycompat.bytestr(prefix),
                )
                raise error.Abort(msg)
            elif stat.S_ISDIR(st.st_mode) and os.path.isdir(
                os.path.join(curpath, b'.hg')
            ):
                if not self.callback or not self.callback(curpath):
                    msg = _(b"path '%s' is inside nested repo %r")
                    raise error.Abort(msg % (path, pycompat.bytestr(prefix)))

    def check(self, path):
        # type: (bytes) -> bool
        try:
            self(path)
            return True
        except (OSError, error.Abort):
            return False

    @contextlib.contextmanager
    def cached(self):
        if self._cached:
            yield
        else:
            try:
                self._cached = True
                yield
            finally:
                self.audited.clear()
                self.auditeddir.clear()
                self._cached = False


def canonpath(root, cwd, myname, auditor=None):
    # type: (bytes, bytes, bytes, Optional[pathauditor]) -> bytes
    """return the canonical path of myname, given cwd and root

    >>> def check(root, cwd, myname):
    ...     a = pathauditor(root, realfs=False)
    ...     try:
    ...         return canonpath(root, cwd, myname, a)
    ...     except error.Abort:
    ...         return 'aborted'
    >>> def unixonly(root, cwd, myname, expected='aborted'):
    ...     if pycompat.iswindows:
    ...         return expected
    ...     return check(root, cwd, myname)
    >>> def winonly(root, cwd, myname, expected='aborted'):
    ...     if not pycompat.iswindows:
    ...         return expected
    ...     return check(root, cwd, myname)
    >>> winonly(b'd:\\\\repo', b'c:\\\\dir', b'filename')
    'aborted'
    >>> winonly(b'c:\\\\repo', b'c:\\\\dir', b'filename')
    'aborted'
    >>> winonly(b'c:\\\\repo', b'c:\\\\', b'filename')
    'aborted'
    >>> winonly(b'c:\\\\repo', b'c:\\\\', b'repo\\\\filename',
    ...         b'filename')
    'filename'
    >>> winonly(b'c:\\\\repo', b'c:\\\\repo', b'filename', b'filename')
    'filename'
    >>> winonly(b'c:\\\\repo', b'c:\\\\repo\\\\subdir', b'filename',
    ...         b'subdir/filename')
    'subdir/filename'
    >>> unixonly(b'/repo', b'/dir', b'filename')
    'aborted'
    >>> unixonly(b'/repo', b'/', b'filename')
    'aborted'
    >>> unixonly(b'/repo', b'/', b'repo/filename', b'filename')
    'filename'
    >>> unixonly(b'/repo', b'/repo', b'filename', b'filename')
    'filename'
    >>> unixonly(b'/repo', b'/repo/subdir', b'filename', b'subdir/filename')
    'subdir/filename'
    """
    if util.endswithsep(root):
        rootsep = root
    else:
        rootsep = root + pycompat.ossep
    name = myname
    if not os.path.isabs(name):
        name = os.path.join(root, cwd, name)
    name = os.path.normpath(name)
    if auditor is None:
        auditor = pathauditor(root)
    if name != rootsep and name.startswith(rootsep):
        name = name[len(rootsep) :]
        auditor(name)
        return util.pconvert(name)
    elif name == root:
        return b''
    else:
        # Determine whether `name' is in the hierarchy at or beneath `root',
        # by iterating name=dirname(name) until that causes no change (can't
        # check name == '/', because that doesn't work on windows). The list
        # `rel' holds the reversed list of components making up the relative
        # file name we want.
        rel = []
        while True:
            try:
                s = util.samefile(name, root)
            except OSError:
                s = False
            if s:
                if not rel:
                    # name was actually the same as root (maybe a symlink)
                    return b''
                rel.reverse()
                name = os.path.join(*rel)
                auditor(name)
                return util.pconvert(name)
            dirname, basename = util.split(name)
            rel.append(basename)
            if dirname == name:
                break
            name = dirname

        # A common mistake is to use -R, but specify a file relative to the repo
        # instead of cwd.  Detect that case, and provide a hint to the user.
        hint = None
        try:
            if cwd != root:
                canonpath(root, root, myname, auditor)
                relpath = util.pathto(root, cwd, b'')
                if relpath.endswith(pycompat.ossep):
                    relpath = relpath[:-1]
                hint = _(b"consider using '--cwd %s'") % relpath
        except error.Abort:
            pass

        raise error.Abort(
            _(b"%s not under root '%s'") % (myname, root), hint=hint
        )


def normasprefix(path):
    # type: (bytes) -> bytes
    """normalize the specified path as path prefix

    Returned value can be used safely for "p.startswith(prefix)",
    "p[len(prefix):]", and so on.

    For efficiency, this expects "path" argument to be already
    normalized by "os.path.normpath", "os.path.realpath", and so on.

    See also issue3033 for detail about need of this function.

    >>> normasprefix(b'/foo/bar').replace(pycompat.ossep, b'/')
    '/foo/bar/'
    >>> normasprefix(b'/').replace(pycompat.ossep, b'/')
    '/'
    """
    d, p = os.path.splitdrive(path)
    if len(p) != len(pycompat.ossep):
        return path + pycompat.ossep
    else:
        return path


def finddirs(path):
    # type: (bytes) -> Iterator[bytes]
    pos = path.rfind(b'/')
    while pos != -1:
        yield path[:pos]
        pos = path.rfind(b'/', 0, pos)
    yield b''


class dirs(object):
    '''a multiset of directory names from a set of file paths'''

    def __init__(self, map, skip=None):
        """
        a dict map indicates a dirstate while a list indicates a manifest
        """
        self._dirs = {}
        addpath = self.addpath
        if isinstance(map, dict) and skip is not None:
            for f, s in pycompat.iteritems(map):
                if s.state != skip:
                    addpath(f)
        elif skip is not None:
            raise error.ProgrammingError(
                b"skip character is only supported with a dict source"
            )
        else:
            for f in map:
                addpath(f)

    def addpath(self, path):
        # type: (bytes) -> None
        dirs = self._dirs
        for base in finddirs(path):
            if base.endswith(b'/'):
                raise ValueError(
                    "found invalid consecutive slashes in path: %r" % base
                )
            if base in dirs:
                dirs[base] += 1
                return
            dirs[base] = 1

    def delpath(self, path):
        # type: (bytes) -> None
        dirs = self._dirs
        for base in finddirs(path):
            if dirs[base] > 1:
                dirs[base] -= 1
                return
            del dirs[base]

    def __iter__(self):
        return iter(self._dirs)

    def __contains__(self, d):
        # type: (bytes) -> bool
        return d in self._dirs


if util.safehasattr(parsers, 'dirs'):
    dirs = parsers.dirs

if rustdirs is not None:
    dirs = rustdirs


# forward two methods from posixpath that do what we need, but we'd
# rather not let our internals know that we're thinking in posix terms
# - instead we'll let them be oblivious.
join = posixpath.join
dirname = posixpath.dirname  # type: Callable[[bytes], bytes]
