# posix.py - Posix utility function implementations for Mercurial
#
#  Copyright 2005-2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import errno
import fcntl
import getpass
import grp
import os
import pwd
import re
import select
import stat
import sys
import tempfile
import typing
import unicodedata

from typing import (
    Any,
    AnyStr,
    Iterable,
    Iterator,
    List,
    Match,
    NoReturn,
    Optional,
    Sequence,
    Tuple,
    Union,
)

from .i18n import _
from .pycompat import (
    open,
)
from . import (
    encoding,
    error,
    policy,
    pycompat,
)

osutil = policy.importmod('osutil')

normpath = os.path.normpath
samestat = os.path.samestat
abspath = os.path.abspath  # re-exports

try:
    oslink = os.link
except AttributeError:
    # Some platforms build Python without os.link on systems that are
    # vaguely unix-like but don't have hardlink support. For those
    # poor souls, just say we tried and that it failed so we fall back
    # to copies.
    def oslink(src: bytes, dst: bytes) -> NoReturn:
        raise OSError(
            errno.EINVAL, b'hardlinks not supported: %s to %s' % (src, dst)
        )


readlink = os.readlink
unlink = os.unlink
rename = os.rename
removedirs = os.removedirs

if typing.TYPE_CHECKING:

    def normpath(path: bytes) -> bytes:
        raise NotImplementedError

    def abspath(path: AnyStr) -> AnyStr:
        raise NotImplementedError

    def oslink(src: bytes, dst: bytes) -> None:
        raise NotImplementedError

    def readlink(path: bytes) -> bytes:
        raise NotImplementedError

    def unlink(path: bytes) -> None:
        raise NotImplementedError

    def rename(src: bytes, dst: bytes) -> None:
        raise NotImplementedError

    def removedirs(name: bytes) -> None:
        raise NotImplementedError


expandglobs: bool = False

umask: int = os.umask(0)
os.umask(umask)

posixfile = open


def split(p: bytes) -> Tuple[bytes, bytes]:
    """Same as posixpath.split, but faster

    >>> import posixpath
    >>> for f in [b'/absolute/path/to/file',
    ...           b'relative/path/to/file',
    ...           b'file_alone',
    ...           b'path/to/directory/',
    ...           b'/multiple/path//separators',
    ...           b'/file_at_root',
    ...           b'///multiple_leading_separators_at_root',
    ...           b'']:
    ...     assert split(f) == posixpath.split(f), f
    """
    ht = p.rsplit(b'/', 1)
    if len(ht) == 1:
        return b'', p
    nh = ht[0].rstrip(b'/')
    if nh:
        return nh, ht[1]
    return ht[0] + b'/', ht[1]


def openhardlinks() -> bool:
    '''return true if it is safe to hold open file handles to hardlinks'''
    return True


def nlinks(name: bytes) -> int:
    '''return number of hardlinks for the given file'''
    return os.lstat(name).st_nlink


def parsepatchoutput(output_line: bytes) -> bytes:
    """parses the output produced by patch and returns the filename"""
    pf = output_line[14:]
    if pycompat.sysplatform == b'OpenVMS':
        if pf[0] == b'`':
            pf = pf[1:-1]  # Remove the quotes
    else:
        if pf.startswith(b"'") and pf.endswith(b"'") and b" " in pf:
            pf = pf[1:-1]  # Remove the quotes
    return pf


def sshargs(
    sshcmd: bytes, host: bytes, user: Optional[bytes], port: Optional[bytes]
) -> bytes:
    '''Build argument list for ssh'''
    args = user and (b"%s@%s" % (user, host)) or host
    if b'-' in args[:1]:
        raise error.Abort(
            _(b'illegal ssh hostname or username starting with -: %s') % args
        )
    args = shellquote(args)
    if port:
        args = b'-p %s %s' % (shellquote(port), args)
    return args


def isexec(f: bytes) -> bool:
    """check whether a file is executable"""
    return os.lstat(f).st_mode & 0o100 != 0


def setflags(f: bytes, l: bool, x: bool) -> None:
    st = os.lstat(f)
    s = st.st_mode
    if l:
        if not stat.S_ISLNK(s):
            # switch file to link
            with open(f, b'rb') as fp:
                data = fp.read()
            unlink(f)
            try:
                os.symlink(data, f)
            except OSError:
                # failed to make a link, rewrite file
                with open(f, b"wb") as fp:
                    fp.write(data)

        # no chmod needed at this point
        return
    if stat.S_ISLNK(s):
        # switch link to file
        data = os.readlink(f)
        unlink(f)
        with open(f, b"wb") as fp:
            fp.write(data)
        s = 0o666 & ~umask  # avoid restatting for chmod

    sx = s & 0o100
    if st.st_nlink > 1 and bool(x) != bool(sx):
        # the file is a hardlink, break it
        with open(f, b"rb") as fp:
            data = fp.read()
        unlink(f)
        with open(f, b"wb") as fp:
            fp.write(data)

    if x and not sx:
        # Turn on +x for every +r bit when making a file executable
        # and obey umask.
        os.chmod(f, s | (s & 0o444) >> 2 & ~umask)
    elif not x and sx:
        # Turn off all +x bits
        os.chmod(f, s & 0o666)


def copymode(
    src: bytes,
    dst: bytes,
    mode: Optional[bytes] = None,
    enforcewritable: bool = False,
) -> None:
    """Copy the file mode from the file at path src to dst.
    If src doesn't exist, we're using mode instead. If mode is None, we're
    using umask."""
    try:
        st_mode = os.lstat(src).st_mode & 0o777
    except FileNotFoundError:
        st_mode = mode
        if st_mode is None:
            st_mode = ~umask
        st_mode &= 0o666

    new_mode = st_mode

    if enforcewritable:
        new_mode |= stat.S_IWUSR

    os.chmod(dst, new_mode)


def checkexec(path: bytes) -> bool:
    """
    Check whether the given path is on a filesystem with UNIX-like exec flags

    Requires a directory (like /foo/.hg)
    """

    # VFAT on some Linux versions can flip mode but it doesn't persist
    # a FS remount. Frequently we can detect it if files are created
    # with exec bit on.

    try:
        EXECFLAGS = stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH
        basedir = os.path.join(path, b'.hg')
        cachedir = os.path.join(basedir, b'wcache')
        storedir = os.path.join(basedir, b'store')
        if not os.path.exists(cachedir):
            try:
                # we want to create the 'cache' directory, not the '.hg' one.
                # Automatically creating '.hg' directory could silently spawn
                # invalid Mercurial repositories. That seems like a bad idea.
                os.mkdir(cachedir)
                if os.path.exists(storedir):
                    copymode(storedir, cachedir)
                else:
                    copymode(basedir, cachedir)
            except (IOError, OSError):
                # we other fallback logic triggers
                pass
        if os.path.isdir(cachedir):
            checkisexec = os.path.join(cachedir, b'checkisexec')
            checknoexec = os.path.join(cachedir, b'checknoexec')

            try:
                m = os.stat(checkisexec).st_mode
            except FileNotFoundError:
                # checkisexec does not exist - fall through ...
                pass
            else:
                # checkisexec exists, check if it actually is exec
                if m & EXECFLAGS != 0:
                    # ensure checknoexec exists, check it isn't exec
                    try:
                        m = os.stat(checknoexec).st_mode
                    except FileNotFoundError:
                        open(checknoexec, b'w').close()  # might fail
                        m = os.stat(checknoexec).st_mode
                    if m & EXECFLAGS == 0:
                        # check-exec is exec and check-no-exec is not exec
                        return True
                    # checknoexec exists but is exec - delete it
                    unlink(checknoexec)
                # checkisexec exists but is not exec - delete it
                unlink(checkisexec)

            # check using one file, leave it as checkisexec
            checkdir = cachedir
        else:
            # check directly in path and don't leave checkisexec behind
            checkdir = path
            checkisexec = None
        fh, fn = pycompat.mkstemp(dir=checkdir, prefix=b'hg-checkexec-')
        try:
            os.close(fh)
            m = os.stat(fn).st_mode
            if m & EXECFLAGS == 0:
                os.chmod(fn, m & 0o777 | EXECFLAGS)
                if os.stat(fn).st_mode & EXECFLAGS != 0:
                    if checkisexec is not None:
                        os.rename(fn, checkisexec)
                        fn = None
                    return True
        finally:
            if fn is not None:
                unlink(fn)
    except (IOError, OSError):
        # we don't care, the user probably won't be able to commit anyway
        return False


def checklink(path: bytes) -> bool:
    """check whether the given path is on a symlink-capable filesystem"""
    # mktemp is not racy because symlink creation will fail if the
    # file already exists
    while True:
        cachedir = os.path.join(path, b'.hg', b'wcache')
        checklink = os.path.join(cachedir, b'checklink')
        # try fast path, read only
        if os.path.islink(checklink):
            return True
        if os.path.isdir(cachedir):
            checkdir = cachedir
        else:
            checkdir = path
            cachedir = None
        name = tempfile.mktemp(
            dir=pycompat.fsdecode(checkdir), prefix=r'checklink-'
        )
        name = pycompat.fsencode(name)
        try:
            fd = None
            if cachedir is None:
                fd = pycompat.namedtempfile(
                    dir=checkdir, prefix=b'hg-checklink-'
                )
                target = os.path.basename(fd.name)
            else:
                # create a fixed file to link to; doesn't matter if it
                # already exists.
                target = b'checklink-target'
                try:
                    fullpath = os.path.join(cachedir, target)
                    open(fullpath, b'w').close()
                except PermissionError:
                    # If we can't write to cachedir, just pretend
                    # that the fs is readonly and by association
                    # that the fs won't support symlinks. This
                    # seems like the least dangerous way to avoid
                    # data loss.
                    return False
            try:
                os.symlink(target, name)
                if cachedir is None:
                    unlink(name)
                else:
                    try:
                        os.rename(name, checklink)
                    except OSError:
                        unlink(name)
                return True
            except FileExistsError:
                # link creation might race, try again
                continue
            finally:
                if fd is not None:
                    fd.close()
        except AttributeError:
            return False
        except OSError as inst:
            # sshfs might report failure while successfully creating the link
            if inst.errno == errno.EIO and os.path.exists(name):
                unlink(name)
            return False


def checkosfilename(path: bytes) -> Optional[bytes]:
    """Check that the base-relative path is a valid filename on this platform.
    Returns None if the path is ok, or a UI string describing the problem."""
    return None  # on posix platforms, every path is ok


def getfsmountpoint(dirpath: bytes) -> Optional[bytes]:
    """Get the filesystem mount point from a directory (best-effort)

    Returns None if we are unsure. Raises OSError on ENOENT, EPERM, etc.
    """
    return getattr(osutil, 'getfsmountpoint', lambda x: None)(dirpath)


def getfstype(dirpath: bytes) -> Optional[bytes]:
    """Get the filesystem type name from a directory (best-effort)

    Returns None if we are unsure. Raises OSError on ENOENT, EPERM, etc.
    """
    return getattr(osutil, 'getfstype', lambda x: None)(dirpath)


def get_password() -> bytes:
    return encoding.strtolocal(getpass.getpass(''))


def setbinary(fd) -> None:
    pass


def pconvert(path: bytes) -> bytes:
    return path


def localpath(path: bytes) -> bytes:
    return path


def samefile(fpath1: bytes, fpath2: bytes) -> bool:
    """Returns whether path1 and path2 refer to the same file. This is only
    guaranteed to work for files, not directories."""
    return os.path.samefile(fpath1, fpath2)


def samedevice(fpath1: bytes, fpath2: bytes) -> bool:
    """Returns whether fpath1 and fpath2 are on the same device. This is only
    guaranteed to work for files, not directories."""
    st1 = os.lstat(fpath1)
    st2 = os.lstat(fpath2)
    return st1.st_dev == st2.st_dev


# os.path.normcase is a no-op, which doesn't help us on non-native filesystems
def normcase(path: bytes) -> bytes:
    return path.lower()


# what normcase does to ASCII strings
normcasespec: int = encoding.normcasespecs.lower
# fallback normcase function for non-ASCII strings
normcasefallback = normcase

if pycompat.isdarwin:

    def normcase(path: bytes) -> bytes:
        """
        Normalize a filename for OS X-compatible comparison:
        - escape-encode invalid characters
        - decompose to NFD
        - lowercase
        - omit ignored characters [200c-200f, 202a-202e, 206a-206f,feff]

        >>> normcase(b'UPPER')
        'upper'
        >>> normcase(b'Caf\\xc3\\xa9')
        'cafe\\xcc\\x81'
        >>> normcase(b'\\xc3\\x89')
        'e\\xcc\\x81'
        >>> normcase(b'\\xb8\\xca\\xc3\\xca\\xbe\\xc8.JPG') # issue3918
        '%b8%ca%c3\\xca\\xbe%c8.jpg'
        """

        try:
            return encoding.asciilower(path)  # exception for non-ASCII
        except UnicodeDecodeError:
            return normcasefallback(path)

    normcasespec = encoding.normcasespecs.lower

    def normcasefallback(path: bytes) -> bytes:
        try:
            u = path.decode('utf-8')
        except UnicodeDecodeError:
            # OS X percent-encodes any bytes that aren't valid utf-8
            s = b''
            pos = 0
            l = len(path)
            while pos < l:
                try:
                    c = encoding.getutf8char(path, pos)
                    pos += len(c)
                except ValueError:
                    c = b'%%%02X' % ord(path[pos : pos + 1])
                    pos += 1
                s += c

            u = s.decode('utf-8')

        # Decompose then lowercase (HFS+ technote specifies lower)
        enc = unicodedata.normalize('NFD', u).lower().encode('utf-8')
        # drop HFS+ ignored characters
        return encoding.hfsignoreclean(enc)


if pycompat.sysplatform == b'cygwin':
    # workaround for cygwin, in which mount point part of path is
    # treated as case sensitive, even though underlying NTFS is case
    # insensitive.

    # default mount points
    cygwinmountpoints = sorted(
        [
            b"/usr/bin",
            b"/usr/lib",
            b"/cygdrive",
        ],
        reverse=True,
    )

    # use upper-ing as normcase as same as NTFS workaround
    def normcase(path: bytes) -> bytes:
        pathlen = len(path)
        if (pathlen == 0) or (path[0] != pycompat.ossep):
            # treat as relative
            return encoding.upper(path)

        # to preserve case of mountpoint part
        for mp in cygwinmountpoints:
            if not path.startswith(mp):
                continue

            mplen = len(mp)
            if mplen == pathlen:  # mount point itself
                return mp
            if path[mplen] == pycompat.ossep:
                return mp + encoding.upper(path[mplen:])

        return encoding.upper(path)

    normcasespec = encoding.normcasespecs.other
    normcasefallback = normcase

    # Cygwin translates native ACLs to POSIX permissions,
    # but these translations are not supported by native
    # tools, so the exec bit tends to be set erroneously.
    # Therefore, disable executable bit access on Cygwin.
    def checkexec(path: bytes) -> bool:
        return False

    # Similarly, Cygwin's symlink emulation is likely to create
    # problems when Mercurial is used from both Cygwin and native
    # Windows, with other native tools, or on shared volumes
    def checklink(path: bytes) -> bool:
        return False


if pycompat.sysplatform == b'OpenVMS':
    # OpenVMS's symlink emulation is broken on some OpenVMS versions.
    def checklink(path):
        return False


_needsshellquote: Optional[Match[bytes]] = None


def shellquote(s: bytes) -> bytes:
    if pycompat.sysplatform == b'OpenVMS':
        return b'"%s"' % s
    global _needsshellquote
    if _needsshellquote is None:
        _needsshellquote = re.compile(br'[^a-zA-Z0-9._/+-]').search
    if s and not _needsshellquote(s):
        # "s" shouldn't have to be quoted
        return s
    else:
        return b"'%s'" % s.replace(b"'", b"'\\''")


def shellsplit(s: bytes) -> List[bytes]:
    """Parse a command string in POSIX shell way (best-effort)"""
    return pycompat.shlexsplit(s, posix=True)


def testpid(pid: int) -> bool:
    '''return False if pid dead, True if running or not sure'''
    if pycompat.sysplatform == b'OpenVMS':
        return True
    try:
        os.kill(pid, 0)
        return True
    except OSError as inst:
        return inst.errno != errno.ESRCH


def isowner(st: os.stat_result) -> bool:
    """Return True if the stat object st is from the current user."""
    return st.st_uid == os.getuid()


def findexe(command: bytes) -> Optional[bytes]:
    """Find executable for command searching like which does.
    If command is a basename then PATH is searched for command.
    PATH isn't searched if command is an absolute or relative path.
    If command isn't found None is returned."""
    if pycompat.sysplatform == b'OpenVMS':
        return command

    def findexisting(executable: bytes) -> Optional[bytes]:
        b'Will return executable if existing file'
        if os.path.isfile(executable) and os.access(executable, os.X_OK):
            return executable
        return None

    if pycompat.ossep in command:
        return findexisting(command)

    if pycompat.sysplatform == b'plan9':
        return findexisting(os.path.join(b'/bin', command))

    for path in encoding.environ.get(b'PATH', b'').split(pycompat.ospathsep):
        executable = findexisting(os.path.join(path, command))
        if executable is not None:
            return executable
    return None


def setsignalhandler() -> None:
    pass


_wantedkinds = {stat.S_IFREG, stat.S_IFLNK}


def statfiles(files: Sequence[bytes]) -> Iterator[Optional[os.stat_result]]:
    """Stat each file in files. Yield each stat, or None if a file does not
    exist or has a type we don't care about."""
    lstat = os.lstat
    getkind = stat.S_IFMT
    for nf in files:
        try:
            st = lstat(nf)
            if getkind(st.st_mode) not in _wantedkinds:
                st = None
        except (FileNotFoundError, NotADirectoryError):
            st = None
        yield st


def getuser() -> bytes:
    '''return name of current user'''
    return pycompat.fsencode(getpass.getuser())


def username(uid: Optional[int] = None) -> Optional[bytes]:
    """Return the name of the user with the given uid.

    If uid is None, return the name of the current user."""

    if uid is None:
        uid = os.getuid()
    try:
        return pycompat.fsencode(pwd.getpwuid(uid)[0])
    except KeyError:
        return b'%d' % uid


def groupname(gid: Optional[int] = None) -> Optional[bytes]:
    """Return the name of the group with the given gid.

    If gid is None, return the name of the current group."""

    if gid is None:
        gid = os.getgid()
    try:
        return pycompat.fsencode(grp.getgrgid(gid)[0])
    except KeyError:
        return pycompat.bytestr(gid)


def groupmembers(name: bytes) -> List[bytes]:
    """Return the list of members of the group with the given
    name, KeyError if the group does not exist.
    """
    name = pycompat.fsdecode(name)
    return pycompat.rapply(pycompat.fsencode, list(grp.getgrnam(name).gr_mem))


def spawndetached(args: List[bytes]) -> int:
    return os.spawnvp(os.P_NOWAIT | getattr(os, 'P_DETACH', 0), args[0], args)


def gethgcmd():  # TODO: convert to bytes, like on Windows?
    return sys.argv[:1]


def makedir(path: bytes, notindexed: bool) -> None:
    os.mkdir(path)


def lookupreg(
    key: bytes,
    name: Optional[bytes] = None,
    scope: Optional[Union[int, Iterable[int]]] = None,
) -> Optional[bytes]:
    return None


def hidewindow() -> None:
    """Hide current shell window.

    Used to hide the window opened when starting asynchronous
    child process under Windows, unneeded on other systems.
    """
    pass


class cachestat:
    def __init__(self, path: bytes) -> None:
        self.stat = os.stat(path)

    def cacheable(self) -> bool:
        return bool(self.stat.st_ino)

    __hash__ = object.__hash__

    def __eq__(self, other: Any) -> bool:
        try:
            # Only dev, ino, size, mtime and atime are likely to change. Out
            # of these, we shouldn't compare atime but should compare the
            # rest. However, one of the other fields changing indicates
            # something fishy going on, so return False if anything but atime
            # changes.
            return (
                self.stat.st_mode == other.stat.st_mode
                and self.stat.st_ino == other.stat.st_ino
                and self.stat.st_dev == other.stat.st_dev
                and self.stat.st_nlink == other.stat.st_nlink
                and self.stat.st_uid == other.stat.st_uid
                and self.stat.st_gid == other.stat.st_gid
                and self.stat.st_size == other.stat.st_size
                and self.stat[stat.ST_MTIME] == other.stat[stat.ST_MTIME]
                and self.stat[stat.ST_CTIME] == other.stat[stat.ST_CTIME]
            )
        except AttributeError:
            return False

    def __ne__(self, other: Any) -> bool:
        return not self == other


def statislink(st: Optional[os.stat_result]) -> bool:
    '''check whether a stat result is a symlink'''
    return stat.S_ISLNK(st.st_mode) if st else False


def statisexec(st: Optional[os.stat_result]) -> bool:
    '''check whether a stat result is an executable file'''
    return (st.st_mode & 0o100 != 0) if st else False


def poll(fds):
    """block until something happens on any file descriptor

    This is a generic helper that will check for any activity
    (read, write.  exception) and return the list of touched files.

    In unsupported cases, it will raise a NotImplementedError"""
    try:
        res = select.select(fds, fds, fds)
    except ValueError:  # out of range file descriptor
        raise NotImplementedError()
    return sorted(list(set(sum(res, []))))


def readpipe(pipe) -> bytes:
    """Read all available data from a pipe."""
    # We can't fstat() a pipe because Linux will always report 0.
    # So, we set the pipe to non-blocking mode and read everything
    # that's available.
    flags = fcntl.fcntl(pipe, fcntl.F_GETFL)
    flags |= os.O_NONBLOCK
    oldflags = fcntl.fcntl(pipe, fcntl.F_SETFL, flags)

    try:
        chunks = []
        while True:
            try:
                s = pipe.read()
                if not s:
                    break
                chunks.append(s)
            except IOError:
                break

        return b''.join(chunks)
    finally:
        fcntl.fcntl(pipe, fcntl.F_SETFL, oldflags)


def bindunixsocket(sock, path: bytes) -> None:
    """Bind the UNIX domain socket to the specified path"""
    # use relative path instead of full path at bind() if possible, since
    # AF_UNIX path has very small length limit (107 chars) on common
    # platforms (see sys/un.h)
    dirname, basename = os.path.split(path)
    bakwdfd = None

    try:
        if dirname:
            bakwdfd = os.open(b'.', os.O_DIRECTORY)
            os.chdir(dirname)
        sock.bind(basename)
        if bakwdfd:
            os.fchdir(bakwdfd)
    finally:
        if bakwdfd:
            os.close(bakwdfd)
