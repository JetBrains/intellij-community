# posix.py - Posix utility function implementations for Mercurial
#
#  Copyright 2005-2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import osutil
import os, sys, errno, stat, getpass, pwd, grp, fcntl

posixfile = open
nulldev = '/dev/null'
normpath = os.path.normpath
samestat = os.path.samestat
rename = os.rename
expandglobs = False

umask = os.umask(0)
os.umask(umask)

def openhardlinks():
    '''return true if it is safe to hold open file handles to hardlinks'''
    return True

def rcfiles(path):
    rcs = [os.path.join(path, 'hgrc')]
    rcdir = os.path.join(path, 'hgrc.d')
    try:
        rcs.extend([os.path.join(rcdir, f)
                    for f, kind in osutil.listdir(rcdir)
                    if f.endswith(".rc")])
    except OSError:
        pass
    return rcs

def system_rcpath():
    path = []
    # old mod_python does not set sys.argv
    if len(getattr(sys, 'argv', [])) > 0:
        path.extend(rcfiles(os.path.dirname(sys.argv[0]) +
                              '/../etc/mercurial'))
    path.extend(rcfiles('/etc/mercurial'))
    return path

def user_rcpath():
    return [os.path.expanduser('~/.hgrc')]

def parse_patch_output(output_line):
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

def is_exec(f):
    """check whether a file is executable"""
    return (os.lstat(f).st_mode & 0100 != 0)

def set_flags(f, l, x):
    s = os.lstat(f).st_mode
    if l:
        if not stat.S_ISLNK(s):
            # switch file to link
            data = open(f).read()
            os.unlink(f)
            try:
                os.symlink(data, f)
            except:
                # failed to make a link, rewrite file
                open(f, "w").write(data)
        # no chmod needed at this point
        return
    if stat.S_ISLNK(s):
        # switch link to file
        data = os.readlink(f)
        os.unlink(f)
        open(f, "w").write(data)
        s = 0666 & ~umask # avoid restatting for chmod

    sx = s & 0100
    if x and not sx:
        # Turn on +x for every +r bit when making a file executable
        # and obey umask.
        os.chmod(f, s | (s & 0444) >> 2 & ~umask)
    elif not x and sx:
        # Turn off all +x bits
        os.chmod(f, s & 0666)

def set_binary(fd):
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

if sys.platform == 'darwin':
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
            if err.errno is errno.ENOENT:
                return path
            raise

        try:
            return fcntl.fcntl(fd, F_GETPATH, '\0' * 1024).rstrip('\0')
        finally:
            os.close(fd)
else:
    # Fallback to the likely inadequate Python builtin function.
    realpath = os.path.realpath

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

def explain_exit(code):
    """return a 2-tuple (desc, code) describing a subprocess status
    (codes from kill are negative - not os.system/wait encoding)"""
    if code >= 0:
        return _("exited with status %d") % code, code
    return _("killed by signal %d") % -code, -code

def isowner(st):
    """Return True if the stat object st is from the current user."""
    return st.st_uid == os.getuid()

def find_exe(command):
    '''Find executable for command searching like which does.
    If command is a basename then PATH is searched for command.
    PATH isn't searched if command is an absolute or relative path.
    If command isn't found None is returned.'''
    if sys.platform == 'OpenVMS':
        return command

    def findexisting(executable):
        'Will return executable if existing file'
        if os.path.exists(executable):
            return executable
        return None

    if os.sep in command:
        return findexisting(command)

    for path in os.environ.get('PATH', '').split(os.pathsep):
        executable = findexisting(os.path.join(path, command))
        if executable is not None:
            return executable
    return None

def set_signal_handler():
    pass

def statfiles(files):
    'Stat each file in files and yield stat or None if file does not exist.'
    lstat = os.lstat
    for nf in files:
        try:
            st = lstat(nf)
        except OSError, err:
            if err.errno not in (errno.ENOENT, errno.ENOTDIR):
                raise
            st = None
        yield st

def getuser():
    '''return name of current user'''
    return getpass.getuser()

def expand_glob(pats):
    '''On Windows, expand the implicit globs in a list of patterns'''
    return list(pats)

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

def spawndetached(args):
    return os.spawnvp(os.P_NOWAIT | getattr(os, 'P_DETACH', 0),
                      args[0], args)

def gethgcmd():
    return sys.argv[:1]

def termwidth_():
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
                arri = fcntl.ioctl(fd, termios.TIOCGWINSZ, '\0' * 8)
                return array.array('h', arri)[1]
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
