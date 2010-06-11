# windows.py - Windows utility function implementations for Mercurial
#
#  Copyright 2005-2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import osutil, error
import errno, msvcrt, os, re, sys, random, subprocess

nulldev = 'NUL:'
umask = 002

# wrap osutil.posixfile to provide friendlier exceptions
def posixfile(name, mode='r', buffering=-1):
    try:
        return osutil.posixfile(name, mode, buffering)
    except WindowsError, err:
        raise IOError(err.errno, '%s: %s' % (name, err.strerror))
posixfile.__doc__ = osutil.posixfile.__doc__

class winstdout(object):
    '''stdout on windows misbehaves if sent through a pipe'''

    def __init__(self, fp):
        self.fp = fp

    def __getattr__(self, key):
        return getattr(self.fp, key)

    def close(self):
        try:
            self.fp.close()
        except: pass

    def write(self, s):
        try:
            # This is workaround for "Not enough space" error on
            # writing large size of data to console.
            limit = 16000
            l = len(s)
            start = 0
            self.softspace = 0
            while start < l:
                end = start + limit
                self.fp.write(s[start:end])
                start = end
        except IOError, inst:
            if inst.errno != 0:
                raise
            self.close()
            raise IOError(errno.EPIPE, 'Broken pipe')

    def flush(self):
        try:
            return self.fp.flush()
        except IOError, inst:
            if inst.errno != errno.EINVAL:
                raise
            self.close()
            raise IOError(errno.EPIPE, 'Broken pipe')

sys.stdout = winstdout(sys.stdout)

def _is_win_9x():
    '''return true if run on windows 95, 98 or me.'''
    try:
        return sys.getwindowsversion()[3] == 1
    except AttributeError:
        return 'command' in os.environ.get('comspec', '')

def openhardlinks():
    return not _is_win_9x() and "win32api" in globals()

def system_rcpath():
    try:
        return system_rcpath_win32()
    except:
        return [r'c:\mercurial\mercurial.ini']

def user_rcpath():
    '''return os-specific hgrc search path to the user dir'''
    try:
        path = user_rcpath_win32()
    except:
        home = os.path.expanduser('~')
        path = [os.path.join(home, 'mercurial.ini'),
                os.path.join(home, '.hgrc')]
    userprofile = os.environ.get('USERPROFILE')
    if userprofile:
        path.append(os.path.join(userprofile, 'mercurial.ini'))
        path.append(os.path.join(userprofile, '.hgrc'))
    return path

def parse_patch_output(output_line):
    """parses the output produced by patch and returns the filename"""
    pf = output_line[14:]
    if pf[0] == '`':
        pf = pf[1:-1] # Remove the quotes
    return pf

def sshargs(sshcmd, host, user, port):
    '''Build argument list for ssh or Plink'''
    pflag = 'plink' in sshcmd.lower() and '-P' or '-p'
    args = user and ("%s@%s" % (user, host)) or host
    return port and ("%s %s %s" % (args, pflag, port)) or args

def testpid(pid):
    '''return False if pid dead, True if running or not known'''
    return True

def set_flags(f, l, x):
    pass

def set_binary(fd):
    # When run without console, pipes may expose invalid
    # fileno(), usually set to -1.
    if hasattr(fd, 'fileno') and fd.fileno() >= 0:
        msvcrt.setmode(fd.fileno(), os.O_BINARY)

def pconvert(path):
    return '/'.join(path.split(os.sep))

def localpath(path):
    return path.replace('/', '\\')

def normpath(path):
    return pconvert(os.path.normpath(path))

def realpath(path):
    '''
    Returns the true, canonical file system path equivalent to the given
    path.
    '''
    # TODO: There may be a more clever way to do this that also handles other,
    # less common file systems.
    return os.path.normpath(os.path.normcase(os.path.realpath(path)))

def samestat(s1, s2):
    return False

# A sequence of backslashes is special iff it precedes a double quote:
# - if there's an even number of backslashes, the double quote is not
#   quoted (i.e. it ends the quoted region)
# - if there's an odd number of backslashes, the double quote is quoted
# - in both cases, every pair of backslashes is unquoted into a single
#   backslash
# (See http://msdn2.microsoft.com/en-us/library/a1y7w461.aspx )
# So, to quote a string, we must surround it in double quotes, double
# the number of backslashes that preceed double quotes and add another
# backslash before every double quote (being careful with the double
# quote we've appended to the end)
_quotere = None
def shellquote(s):
    global _quotere
    if _quotere is None:
        _quotere = re.compile(r'(\\*)("|\\$)')
    return '"%s"' % _quotere.sub(r'\1\1\\\2', s)

def quotecommand(cmd):
    """Build a command string suitable for os.popen* calls."""
    # The extra quotes are needed because popen* runs the command
    # through the current COMSPEC. cmd.exe suppress enclosing quotes.
    return '"' + cmd + '"'

def popen(command, mode='r'):
    # Work around "popen spawned process may not write to stdout
    # under windows"
    # http://bugs.python.org/issue1366
    command += " 2> %s" % nulldev
    return os.popen(quotecommand(command), mode)

def explain_exit(code):
    return _("exited with status %d") % code, code

# if you change this stub into a real check, please try to implement the
# username and groupname functions above, too.
def isowner(st):
    return True

def find_exe(command):
    '''Find executable for command searching like cmd.exe does.
    If command is a basename then PATH is searched for command.
    PATH isn't searched if command is an absolute or relative path.
    An extension from PATHEXT is found and added if not present.
    If command isn't found None is returned.'''
    pathext = os.environ.get('PATHEXT', '.COM;.EXE;.BAT;.CMD')
    pathexts = [ext for ext in pathext.lower().split(os.pathsep)]
    if os.path.splitext(command)[1].lower() in pathexts:
        pathexts = ['']

    def findexisting(pathcommand):
        'Will append extension (if needed) and return existing file'
        for ext in pathexts:
            executable = pathcommand + ext
            if os.path.exists(executable):
                return executable
        return None

    if os.sep in command:
        return findexisting(command)

    for path in os.environ.get('PATH', '').split(os.pathsep):
        executable = findexisting(os.path.join(path, command))
        if executable is not None:
            return executable
    return findexisting(os.path.expanduser(os.path.expandvars(command)))

def set_signal_handler():
    try:
        set_signal_handler_win32()
    except NameError:
        pass

def statfiles(files):
    '''Stat each file in files and yield stat or None if file does not exist.
    Cluster and cache stat per directory to minimize number of OS stat calls.'''
    ncase = os.path.normcase
    dircache = {} # dirname -> filename -> status | None if file does not exist
    for nf in files:
        nf  = ncase(nf)
        dir, base = os.path.split(nf)
        if not dir:
            dir = '.'
        cache = dircache.get(dir, None)
        if cache is None:
            try:
                dmap = dict([(ncase(n), s)
                    for n, k, s in osutil.listdir(dir, True)])
            except OSError, err:
                # handle directory not found in Python version prior to 2.5
                # Python <= 2.4 returns native Windows code 3 in errno
                # Python >= 2.5 returns ENOENT and adds winerror field
                # EINVAL is raised if dir is not a directory.
                if err.errno not in (3, errno.ENOENT, errno.EINVAL,
                                     errno.ENOTDIR):
                    raise
                dmap = {}
            cache = dircache.setdefault(dir, dmap)
        yield cache.get(base, None)

def getuser():
    '''return name of current user'''
    raise error.Abort(_('user name not available - set USERNAME '
                       'environment variable'))

def username(uid=None):
    """Return the name of the user with the given uid.

    If uid is None, return the name of the current user."""
    return None

def groupname(gid=None):
    """Return the name of the group with the given gid.

    If gid is None, return the name of the current group."""
    return None

def _removedirs(name):
    """special version of os.removedirs that does not remove symlinked
    directories or junction points if they actually contain files"""
    if osutil.listdir(name):
        return
    os.rmdir(name)
    head, tail = os.path.split(name)
    if not tail:
        head, tail = os.path.split(head)
    while head and tail:
        try:
            if osutil.listdir(head):
                return
            os.rmdir(head)
        except:
            break
        head, tail = os.path.split(head)

def unlink(f):
    """unlink and remove the directory if it is empty"""
    os.unlink(f)
    # try removing directories that might now be empty
    try:
        _removedirs(os.path.dirname(f))
    except OSError:
        pass

def rename(src, dst):
    '''atomically rename file src to dst, replacing dst if it exists'''
    try:
        os.rename(src, dst)
    except OSError, err: # FIXME: check err (EEXIST ?)

        # On windows, rename to existing file is not allowed, so we
        # must delete destination first. But if a file is open, unlink
        # schedules it for delete but does not delete it. Rename
        # happens immediately even for open files, so we rename
        # destination to a temporary name, then delete that. Then
        # rename is safe to do.
        # The temporary name is chosen at random to avoid the situation
        # where a file is left lying around from a previous aborted run.
        # The usual race condition this introduces can't be avoided as
        # we need the name to rename into, and not the file itself. Due
        # to the nature of the operation however, any races will at worst
        # lead to the rename failing and the current operation aborting.

        def tempname(prefix):
            for tries in xrange(10):
                temp = '%s-%08x' % (prefix, random.randint(0, 0xffffffff))
                if not os.path.exists(temp):
                    return temp
            raise IOError, (errno.EEXIST, "No usable temporary filename found")

        temp = tempname(dst)
        os.rename(dst, temp)
        try:
            os.unlink(temp)
        except:
            # Some rude AV-scanners on Windows may cause the unlink to
            # fail. Not aborting here just leaks the temp file, whereas
            # aborting at this point may leave serious inconsistencies.
            # Ideally, we would notify the user here.
            pass
        os.rename(src, dst)

def spawndetached(args):
    # No standard library function really spawns a fully detached
    # process under win32 because they allocate pipes or other objects
    # to handle standard streams communications. Passing these objects
    # to the child process requires handle inheritance to be enabled
    # which makes really detached processes impossible.
    class STARTUPINFO:
        dwFlags = subprocess.STARTF_USESHOWWINDOW
        hStdInput = None
        hStdOutput = None
        hStdError = None
        wShowWindow = subprocess.SW_HIDE

    args = subprocess.list2cmdline(args)
    # Not running the command in shell mode makes python26 hang when
    # writing to hgweb output socket.
    comspec = os.environ.get("COMSPEC", "cmd.exe")
    args = comspec + " /c " + args
    hp, ht, pid, tid = subprocess.CreateProcess(
        None, args,
        # no special security
        None, None,
        # Do not inherit handles
        0,
        # DETACHED_PROCESS
        0x00000008,
        os.environ,
        os.getcwd(),
        STARTUPINFO())
    return pid

def gethgcmd():
    return [sys.executable] + sys.argv[:1]

def termwidth_():
    # cmd.exe does not handle CR like a unix console, the CR is
    # counted in the line length. On 80 columns consoles, if 80
    # characters are written, the following CR won't apply on the
    # current line but on the new one. Keep room for it.
    return 79

try:
    # override functions with win32 versions if possible
    from win32 import *
except ImportError:
    pass

expandglobs = True
