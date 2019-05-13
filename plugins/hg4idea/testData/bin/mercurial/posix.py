# posix.py - Posix utility function implementations for Mercurial
#
#  Copyright 2005-2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import encoding
import os, sys, errno, stat, getpass, pwd, grp, socket, tempfile, unicodedata

posixfile = open
normpath = os.path.normpath
samestat = os.path.samestat
oslink = os.link
unlink = os.unlink
rename = os.rename
expandglobs = False

umask = os.umask(0)
os.umask(umask)

def split(p):
    '''Same as posixpath.split, but faster

    >>> import posixpath
    >>> for f in ['/absolute/path/to/file',
    ...           'relative/path/to/file',
    ...           'file_alone',
    ...           'path/to/directory/',
    ...           '/multiple/path//separators',
    ...           '/file_at_root',
    ...           '///multiple_leading_separators_at_root',
    ...           '']:
    ...     assert split(f) == posixpath.split(f), f
    '''
    ht = p.rsplit('/', 1)
    if len(ht) == 1:
        return '', p
    nh = ht[0].rstrip('/')
    if nh:
        return nh, ht[1]
    return ht[0] + '/', ht[1]

def openhardlinks():
    '''return true if it is safe to hold open file handles to hardlinks'''
    return True

def nlinks(name):
    '''return number of hardlinks for the given file'''
    return os.lstat(name).st_nlink

def parsepatchoutput(output_line):
    """parses the output produced by patch and returns the filename"""
    pf = output_line[14:]
    if os.sys.platform == 'OpenVMS':
        if pf[0] == '`':
            pf = pf[1:-1] # Remove the quotes
    else:
        if pf.startswith("'") and pf.endswith("'") and " " in pf:
            pf = pf[1:-1] # Remove the quotes
    return pf

def sshargs(sshcmd, host, user, port):
    '''Build argument list for ssh'''
    args = user and ("%s@%s" % (user, host)) or host
    return port and ("%s -p %s" % (args, port)) or args

def isexec(f):
    """check whether a file is executable"""
    return (os.lstat(f).st_mode & 0100 != 0)

def setflags(f, l, x):
    s = os.lstat(f).st_mode
    if l:
        if not stat.S_ISLNK(s):
            # switch file to link
            fp = open(f)
            data = fp.read()
            fp.close()
            os.unlink(f)
            try:
                os.symlink(data, f)
            except OSError:
                # failed to make a link, rewrite file
                fp = open(f, "w")
                fp.write(data)
                fp.close()
        # no chmod needed at this point
        return
    if stat.S_ISLNK(s):
        # switch link to file
        data = os.readlink(f)
        os.unlink(f)
        fp = open(f, "w")
        fp.write(data)
        fp.close()
        s = 0666 & ~umask # avoid restatting for chmod

    sx = s & 0100
    if x and not sx:
        # Turn on +x for every +r bit when making a file executable
        # and obey umask.
        os.chmod(f, s | (s & 0444) >> 2 & ~umask)
    elif not x and sx:
        # Turn off all +x bits
        os.chmod(f, s & 0666)

def copymode(src, dst, mode=None):
    '''Copy the file mode from the file at path src to dst.
    If src doesn't exist, we're using mode instead. If mode is None, we're
    using umask.'''
    try:
        st_mode = os.lstat(src).st_mode & 0777
    except OSError, inst:
        if inst.errno != errno.ENOENT:
            raise
        st_mode = mode
        if st_mode is None:
            st_mode = ~umask
        st_mode &= 0666
    os.chmod(dst, st_mode)

def checkexec(path):
    """
    Check whether the given path is on a filesystem with UNIX-like exec flags

    Requires a directory (like /foo/.hg)
    """

    # VFAT on some Linux versions can flip mode but it doesn't persist
    # a FS remount. Frequently we can detect it if files are created
    # with exec bit on.

    try:
        EXECFLAGS = stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH
        fh, fn = tempfile.mkstemp(dir=path, prefix='hg-checkexec-')
        try:
            os.close(fh)
            m = os.stat(fn).st_mode & 0777
            new_file_has_exec = m & EXECFLAGS
            os.chmod(fn, m ^ EXECFLAGS)
            exec_flags_cannot_flip = ((os.stat(fn).st_mode & 0777) == m)
        finally:
            os.unlink(fn)
    except (IOError, OSError):
        # we don't care, the user probably won't be able to commit anyway
        return False
    return not (new_file_has_exec or exec_flags_cannot_flip)

def checklink(path):
    """check whether the given path is on a symlink-capable filesystem"""
    # mktemp is not racy because symlink creation will fail if the
    # file already exists
    name = tempfile.mktemp(dir=path, prefix='hg-checklink-')
    try:
        os.symlink(".", name)
        os.unlink(name)
        return True
    except (OSError, AttributeError):
        return False

def checkosfilename(path):
    '''Check that the base-relative path is a valid filename on this platform.
    Returns None if the path is ok, or a UI string describing the problem.'''
    pass # on posix platforms, every path is ok

def setbinary(fd):
    pass

def pconvert(path):
    return path

def localpath(path):
    return path

def samefile(fpath1, fpath2):
    """Returns whether path1 and path2 refer to the same file. This is only
    guaranteed to work for files, not directories."""
    return os.path.samefile(fpath1, fpath2)

def samedevice(fpath1, fpath2):
    """Returns whether fpath1 and fpath2 are on the same device. This is only
    guaranteed to work for files, not directories."""
    st1 = os.lstat(fpath1)
    st2 = os.lstat(fpath2)
    return st1.st_dev == st2.st_dev

# os.path.normcase is a no-op, which doesn't help us on non-native filesystems
def normcase(path):
    return path.lower()

if sys.platform == 'darwin':
    import fcntl # only needed on darwin, missing on jython

    def normcase(path):
        '''
        Normalize a filename for OS X-compatible comparison:
        - escape-encode invalid characters
        - decompose to NFD
        - lowercase

        >>> normcase('UPPER')
        'upper'
        >>> normcase('Caf\xc3\xa9')
        'cafe\\xcc\\x81'
        >>> normcase('\xc3\x89')
        'e\\xcc\\x81'
        >>> normcase('\xb8\xca\xc3\xca\xbe\xc8.JPG') # issue3918
        '%b8%ca%c3\\xca\\xbe%c8.jpg'
        '''

        try:
            path.decode('ascii') # throw exception for non-ASCII character
            return path.lower()
        except UnicodeDecodeError:
            pass
        try:
            u = path.decode('utf-8')
        except UnicodeDecodeError:
            # OS X percent-encodes any bytes that aren't valid utf-8
            s = ''
            g = ''
            l = 0
            for c in path:
                o = ord(c)
                if l and o < 128 or o >= 192:
                    # we want a continuation byte, but didn't get one
                    s += ''.join(["%%%02X" % ord(x) for x in g])
                    g = ''
                    l = 0
                if l == 0 and o < 128:
                    # ascii
                    s += c
                elif l == 0 and 194 <= o < 245:
                    # valid leading bytes
                    if o < 224:
                        l = 1
                    elif o < 240:
                        l = 2
                    else:
                        l = 3
                    g = c
                elif l > 0 and 128 <= o < 192:
                    # valid continuations
                    g += c
                    l -= 1
                    if not l:
                        s += g
                        g = ''
                else:
                    # invalid
                    s += "%%%02X" % o

            # any remaining partial characters
            s += ''.join(["%%%02X" % ord(x) for x in g])
            u = s.decode('utf-8')

        # Decompose then lowercase (HFS+ technote specifies lower)
        return unicodedata.normalize('NFD', u).lower().encode('utf-8')

    def realpath(path):
        '''
        Returns the true, canonical file system path equivalent to the given
        path.

        Equivalent means, in this case, resulting in the same, unique
        file system link to the path. Every file system entry, whether a file,
        directory, hard link or symbolic link or special, will have a single
        path preferred by the system, but may allow multiple, differing path
        lookups to point to it.

        Most regular UNIX file systems only allow a file system entry to be
        looked up by its distinct path. Obviously, this does not apply to case
        insensitive file systems, whether case preserving or not. The most
        complex issue to deal with is file systems transparently reencoding the
        path, such as the non-standard Unicode normalisation required for HFS+
        and HFSX.
        '''
        # Constants copied from /usr/include/sys/fcntl.h
        F_GETPATH = 50
        O_SYMLINK = 0x200000

        try:
            fd = os.open(path, O_SYMLINK)
        except OSError, err:
            if err.errno == errno.ENOENT:
                return path
            raise

        try:
            return fcntl.fcntl(fd, F_GETPATH, '\0' * 1024).rstrip('\0')
        finally:
            os.close(fd)
elif sys.version_info < (2, 4, 2, 'final'):
    # Workaround for http://bugs.python.org/issue1213894 (os.path.realpath
    # didn't resolve symlinks that were the first component of the path.)
    def realpath(path):
        if os.path.isabs(path):
            return os.path.realpath(path)
        else:
            return os.path.realpath('./' + path)
else:
    # Fallback to the likely inadequate Python builtin function.
    realpath = os.path.realpath

if sys.platform == 'cygwin':
    # workaround for cygwin, in which mount point part of path is
    # treated as case sensitive, even though underlying NTFS is case
    # insensitive.

    # default mount points
    cygwinmountpoints = sorted([
            "/usr/bin",
            "/usr/lib",
            "/cygdrive",
            ], reverse=True)

    # use upper-ing as normcase as same as NTFS workaround
    def normcase(path):
        pathlen = len(path)
        if (pathlen == 0) or (path[0] != os.sep):
            # treat as relative
            return encoding.upper(path)

        # to preserve case of mountpoint part
        for mp in cygwinmountpoints:
            if not path.startswith(mp):
                continue

            mplen = len(mp)
            if mplen == pathlen: # mount point itself
                return mp
            if path[mplen] == os.sep:
                return mp + encoding.upper(path[mplen:])

        return encoding.upper(path)

    # Cygwin translates native ACLs to POSIX permissions,
    # but these translations are not supported by native
    # tools, so the exec bit tends to be set erroneously.
    # Therefore, disable executable bit access on Cygwin.
    def checkexec(path):
        return False

    # Similarly, Cygwin's symlink emulation is likely to create
    # problems when Mercurial is used from both Cygwin and native
    # Windows, with other native tools, or on shared volumes
    def checklink(path):
        return False

def shellquote(s):
    if os.sys.platform == 'OpenVMS':
        return '"%s"' % s
    else:
        return "'%s'" % s.replace("'", "'\\''")

def quotecommand(cmd):
    return cmd

def popen(command, mode='r'):
    return os.popen(command, mode)

def testpid(pid):
    '''return False if pid dead, True if running or not sure'''
    if os.sys.platform == 'OpenVMS':
        return True
    try:
        os.kill(pid, 0)
        return True
    except OSError, inst:
        return inst.errno != errno.ESRCH

def explainexit(code):
    """return a 2-tuple (desc, code) describing a subprocess status
    (codes from kill are negative - not os.system/wait encoding)"""
    if code >= 0:
        return _("exited with status %d") % code, code
    return _("killed by signal %d") % -code, -code

def isowner(st):
    """Return True if the stat object st is from the current user."""
    return st.st_uid == os.getuid()

def findexe(command):
    '''Find executable for command searching like which does.
    If command is a basename then PATH is searched for command.
    PATH isn't searched if command is an absolute or relative path.
    If command isn't found None is returned.'''
    if sys.platform == 'OpenVMS':
        return command

    def findexisting(executable):
        'Will return executable if existing file'
        if os.path.isfile(executable) and os.access(executable, os.X_OK):
            return executable
        return None

    if os.sep in command:
        return findexisting(command)

    if sys.platform == 'plan9':
        return findexisting(os.path.join('/bin', command))

    for path in os.environ.get('PATH', '').split(os.pathsep):
        executable = findexisting(os.path.join(path, command))
        if executable is not None:
            return executable
    return None

def setsignalhandler():
    pass

_wantedkinds = set([stat.S_IFREG, stat.S_IFLNK])

def statfiles(files):
    '''Stat each file in files. Yield each stat, or None if a file does not
    exist or has a type we don't care about.'''
    lstat = os.lstat
    getkind = stat.S_IFMT
    for nf in files:
        try:
            st = lstat(nf)
            if getkind(st.st_mode) not in _wantedkinds:
                st = None
        except OSError, err:
            if err.errno not in (errno.ENOENT, errno.ENOTDIR):
                raise
            st = None
        yield st

def getuser():
    '''return name of current user'''
    return getpass.getuser()

def username(uid=None):
    """Return the name of the user with the given uid.

    If uid is None, return the name of the current user."""

    if uid is None:
        uid = os.getuid()
    try:
        return pwd.getpwuid(uid)[0]
    except KeyError:
        return str(uid)

def groupname(gid=None):
    """Return the name of the group with the given gid.

    If gid is None, return the name of the current group."""

    if gid is None:
        gid = os.getgid()
    try:
        return grp.getgrgid(gid)[0]
    except KeyError:
        return str(gid)

def groupmembers(name):
    """Return the list of members of the group with the given
    name, KeyError if the group does not exist.
    """
    return list(grp.getgrnam(name).gr_mem)

def spawndetached(args):
    return os.spawnvp(os.P_NOWAIT | getattr(os, 'P_DETACH', 0),
                      args[0], args)

def gethgcmd():
    return sys.argv[:1]

def termwidth():
    try:
        import termios, array, fcntl
        for dev in (sys.stderr, sys.stdout, sys.stdin):
            try:
                try:
                    fd = dev.fileno()
                except AttributeError:
                    continue
                if not os.isatty(fd):
                    continue
                try:
                    arri = fcntl.ioctl(fd, termios.TIOCGWINSZ, '\0' * 8)
                    width = array.array('h', arri)[1]
                    if width > 0:
                        return width
                except AttributeError:
                    pass
            except ValueError:
                pass
            except IOError, e:
                if e[0] == errno.EINVAL:
                    pass
                else:
                    raise
    except ImportError:
        pass
    return 80

def makedir(path, notindexed):
    os.mkdir(path)

def unlinkpath(f, ignoremissing=False):
    """unlink and remove the directory if it is empty"""
    try:
        os.unlink(f)
    except OSError, e:
        if not (ignoremissing and e.errno == errno.ENOENT):
            raise
    # try removing directories that might now be empty
    try:
        os.removedirs(os.path.dirname(f))
    except OSError:
        pass

def lookupreg(key, name=None, scope=None):
    return None

def hidewindow():
    """Hide current shell window.

    Used to hide the window opened when starting asynchronous
    child process under Windows, unneeded on other systems.
    """
    pass

class cachestat(object):
    def __init__(self, path):
        self.stat = os.stat(path)

    def cacheable(self):
        return bool(self.stat.st_ino)

    __hash__ = object.__hash__

    def __eq__(self, other):
        try:
            # Only dev, ino, size, mtime and atime are likely to change. Out
            # of these, we shouldn't compare atime but should compare the
            # rest. However, one of the other fields changing indicates
            # something fishy going on, so return False if anything but atime
            # changes.
            return (self.stat.st_mode == other.stat.st_mode and
                    self.stat.st_ino == other.stat.st_ino and
                    self.stat.st_dev == other.stat.st_dev and
                    self.stat.st_nlink == other.stat.st_nlink and
                    self.stat.st_uid == other.stat.st_uid and
                    self.stat.st_gid == other.stat.st_gid and
                    self.stat.st_size == other.stat.st_size and
                    self.stat.st_mtime == other.stat.st_mtime and
                    self.stat.st_ctime == other.stat.st_ctime)
        except AttributeError:
            return False

    def __ne__(self, other):
        return not self == other

def executablepath():
    return None # available on Windows only

class unixdomainserver(socket.socket):
    def __init__(self, join, subsystem):
        '''Create a unix domain socket with the given prefix.'''
        super(unixdomainserver, self).__init__(socket.AF_UNIX)
        sockname = subsystem + '.sock'
        self.realpath = self.path = join(sockname)
        if os.path.islink(self.path):
            if os.path.exists(self.path):
                self.realpath = os.readlink(self.path)
            else:
                os.unlink(self.path)
        try:
            self.bind(self.realpath)
        except socket.error, err:
            if err.args[0] == 'AF_UNIX path too long':
                tmpdir = tempfile.mkdtemp(prefix='hg-%s-' % subsystem)
                self.realpath = os.path.join(tmpdir, sockname)
                try:
                    self.bind(self.realpath)
                    os.symlink(self.realpath, self.path)
                except (OSError, socket.error):
                    self.cleanup()
                    raise
            else:
                raise
        self.listen(5)

    def cleanup(self):
        def okayifmissing(f, path):
            try:
                f(path)
            except OSError, err:
                if err.errno != errno.ENOENT:
                    raise

        okayifmissing(os.unlink, self.path)
        if self.realpath != self.path:
            okayifmissing(os.unlink, self.realpath)
            okayifmissing(os.rmdir, os.path.dirname(self.realpath))

def statislink(st):
    '''check whether a stat result is a symlink'''
    return st and stat.S_ISLNK(st.st_mode)

def statisexec(st):
    '''check whether a stat result is an executable file'''
    return st and (st.st_mode & 0100 != 0)
