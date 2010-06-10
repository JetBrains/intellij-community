# util.py - Mercurial utility functions and platform specfic implementations
#
#  Copyright 2005 K. Thananchayan <thananck@yahoo.com>
#  Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#  Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Mercurial utility functions and platform specfic implementations.

This contains helper routines that are independent of the SCM core and
hide platform-specific details from the core.
"""

from i18n import _
import error, osutil, encoding
import cStringIO, errno, re, shutil, sys, tempfile, traceback
import os, stat, time, calendar, textwrap, signal
import imp

# Python compatibility

def sha1(s):
    return _fastsha1(s)

def _fastsha1(s):
    # This function will import sha1 from hashlib or sha (whichever is
    # available) and overwrite itself with it on the first call.
    # Subsequent calls will go directly to the imported function.
    try:
        from hashlib import sha1 as _sha1
    except ImportError:
        from sha import sha as _sha1
    global _fastsha1, sha1
    _fastsha1 = sha1 = _sha1
    return _sha1(s)

import subprocess
closefds = os.name == 'posix'

def popen2(cmd, env=None, newlines=False):
    # Setting bufsize to -1 lets the system decide the buffer size.
    # The default for bufsize is 0, meaning unbuffered. This leads to
    # poor performance on Mac OS X: http://bugs.python.org/issue4194
    p = subprocess.Popen(cmd, shell=True, bufsize=-1,
                         close_fds=closefds,
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         universal_newlines=newlines,
                         env=env)
    return p.stdin, p.stdout

def popen3(cmd, env=None, newlines=False):
    p = subprocess.Popen(cmd, shell=True, bufsize=-1,
                         close_fds=closefds,
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE,
                         universal_newlines=newlines,
                         env=env)
    return p.stdin, p.stdout, p.stderr

def version():
    """Return version information if available."""
    try:
        import __version__
        return __version__.version
    except ImportError:
        return 'unknown'

# used by parsedate
defaultdateformats = (
    '%Y-%m-%d %H:%M:%S',
    '%Y-%m-%d %I:%M:%S%p',
    '%Y-%m-%d %H:%M',
    '%Y-%m-%d %I:%M%p',
    '%Y-%m-%d',
    '%m-%d',
    '%m/%d',
    '%m/%d/%y',
    '%m/%d/%Y',
    '%a %b %d %H:%M:%S %Y',
    '%a %b %d %I:%M:%S%p %Y',
    '%a, %d %b %Y %H:%M:%S',        #  GNU coreutils "/bin/date --rfc-2822"
    '%b %d %H:%M:%S %Y',
    '%b %d %I:%M:%S%p %Y',
    '%b %d %H:%M:%S',
    '%b %d %I:%M:%S%p',
    '%b %d %H:%M',
    '%b %d %I:%M%p',
    '%b %d %Y',
    '%b %d',
    '%H:%M:%S',
    '%I:%M:%S%p',
    '%H:%M',
    '%I:%M%p',
)

extendeddateformats = defaultdateformats + (
    "%Y",
    "%Y-%m",
    "%b",
    "%b %Y",
    )

def cachefunc(func):
    '''cache the result of function calls'''
    # XXX doesn't handle keywords args
    cache = {}
    if func.func_code.co_argcount == 1:
        # we gain a small amount of time because
        # we don't need to pack/unpack the list
        def f(arg):
            if arg not in cache:
                cache[arg] = func(arg)
            return cache[arg]
    else:
        def f(*args):
            if args not in cache:
                cache[args] = func(*args)
            return cache[args]

    return f

def lrucachefunc(func):
    '''cache most recent results of function calls'''
    cache = {}
    order = []
    if func.func_code.co_argcount == 1:
        def f(arg):
            if arg not in cache:
                if len(cache) > 20:
                    del cache[order.pop(0)]
                cache[arg] = func(arg)
            else:
                order.remove(arg)
            order.append(arg)
            return cache[arg]
    else:
        def f(*args):
            if args not in cache:
                if len(cache) > 20:
                    del cache[order.pop(0)]
                cache[args] = func(*args)
            else:
                order.remove(args)
            order.append(args)
            return cache[args]

    return f

class propertycache(object):
    def __init__(self, func):
        self.func = func
        self.name = func.__name__
    def __get__(self, obj, type=None):
        result = self.func(obj)
        setattr(obj, self.name, result)
        return result

def pipefilter(s, cmd):
    '''filter string S through command CMD, returning its output'''
    p = subprocess.Popen(cmd, shell=True, close_fds=closefds,
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    pout, perr = p.communicate(s)
    return pout

def tempfilter(s, cmd):
    '''filter string S through a pair of temporary files with CMD.
    CMD is used as a template to create the real command to be run,
    with the strings INFILE and OUTFILE replaced by the real names of
    the temporary files generated.'''
    inname, outname = None, None
    try:
        infd, inname = tempfile.mkstemp(prefix='hg-filter-in-')
        fp = os.fdopen(infd, 'wb')
        fp.write(s)
        fp.close()
        outfd, outname = tempfile.mkstemp(prefix='hg-filter-out-')
        os.close(outfd)
        cmd = cmd.replace('INFILE', inname)
        cmd = cmd.replace('OUTFILE', outname)
        code = os.system(cmd)
        if sys.platform == 'OpenVMS' and code & 1:
            code = 0
        if code:
            raise Abort(_("command '%s' failed: %s") %
                        (cmd, explain_exit(code)))
        return open(outname, 'rb').read()
    finally:
        try:
            if inname:
                os.unlink(inname)
        except:
            pass
        try:
            if outname:
                os.unlink(outname)
        except:
            pass

filtertable = {
    'tempfile:': tempfilter,
    'pipe:': pipefilter,
    }

def filter(s, cmd):
    "filter a string through a command that transforms its input to its output"
    for name, fn in filtertable.iteritems():
        if cmd.startswith(name):
            return fn(s, cmd[len(name):].lstrip())
    return pipefilter(s, cmd)

def binary(s):
    """return true if a string is binary data"""
    return bool(s and '\0' in s)

def increasingchunks(source, min=1024, max=65536):
    '''return no less than min bytes per chunk while data remains,
    doubling min after each chunk until it reaches max'''
    def log2(x):
        if not x:
            return 0
        i = 0
        while x:
            x >>= 1
            i += 1
        return i - 1

    buf = []
    blen = 0
    for chunk in source:
        buf.append(chunk)
        blen += len(chunk)
        if blen >= min:
            if min < max:
                min = min << 1
                nmin = 1 << log2(blen)
                if nmin > min:
                    min = nmin
                if min > max:
                    min = max
            yield ''.join(buf)
            blen = 0
            buf = []
    if buf:
        yield ''.join(buf)

Abort = error.Abort

def always(fn):
    return True

def never(fn):
    return False

def pathto(root, n1, n2):
    '''return the relative path from one place to another.
    root should use os.sep to separate directories
    n1 should use os.sep to separate directories
    n2 should use "/" to separate directories
    returns an os.sep-separated path.

    If n1 is a relative path, it's assumed it's
    relative to root.
    n2 should always be relative to root.
    '''
    if not n1:
        return localpath(n2)
    if os.path.isabs(n1):
        if os.path.splitdrive(root)[0] != os.path.splitdrive(n1)[0]:
            return os.path.join(root, localpath(n2))
        n2 = '/'.join((pconvert(root), n2))
    a, b = splitpath(n1), n2.split('/')
    a.reverse()
    b.reverse()
    while a and b and a[-1] == b[-1]:
        a.pop()
        b.pop()
    b.reverse()
    return os.sep.join((['..'] * len(a)) + b) or '.'

def canonpath(root, cwd, myname):
    """return the canonical path of myname, given cwd and root"""
    if endswithsep(root):
        rootsep = root
    else:
        rootsep = root + os.sep
    name = myname
    if not os.path.isabs(name):
        name = os.path.join(root, cwd, name)
    name = os.path.normpath(name)
    audit_path = path_auditor(root)
    if name != rootsep and name.startswith(rootsep):
        name = name[len(rootsep):]
        audit_path(name)
        return pconvert(name)
    elif name == root:
        return ''
    else:
        # Determine whether `name' is in the hierarchy at or beneath `root',
        # by iterating name=dirname(name) until that causes no change (can't
        # check name == '/', because that doesn't work on windows).  For each
        # `name', compare dev/inode numbers.  If they match, the list `rel'
        # holds the reversed list of components making up the relative file
        # name we want.
        root_st = os.stat(root)
        rel = []
        while True:
            try:
                name_st = os.stat(name)
            except OSError:
                break
            if samestat(name_st, root_st):
                if not rel:
                    # name was actually the same as root (maybe a symlink)
                    return ''
                rel.reverse()
                name = os.path.join(*rel)
                audit_path(name)
                return pconvert(name)
            dirname, basename = os.path.split(name)
            rel.append(basename)
            if dirname == name:
                break
            name = dirname

        raise Abort('%s not under root' % myname)

_hgexecutable = None

def main_is_frozen():
    """return True if we are a frozen executable.

    The code supports py2exe (most common, Windows only) and tools/freeze
    (portable, not much used).
    """
    return (hasattr(sys, "frozen") or # new py2exe
            hasattr(sys, "importers") or # old py2exe
            imp.is_frozen("__main__")) # tools/freeze

def hgexecutable():
    """return location of the 'hg' executable.

    Defaults to $HG or 'hg' in the search path.
    """
    if _hgexecutable is None:
        hg = os.environ.get('HG')
        if hg:
            set_hgexecutable(hg)
        elif main_is_frozen():
            set_hgexecutable(sys.executable)
        else:
            exe = find_exe('hg') or os.path.basename(sys.argv[0])
            set_hgexecutable(exe)
    return _hgexecutable

def set_hgexecutable(path):
    """set location of the 'hg' executable"""
    global _hgexecutable
    _hgexecutable = path

def system(cmd, environ={}, cwd=None, onerr=None, errprefix=None):
    '''enhanced shell command execution.
    run with environment maybe modified, maybe in different dir.

    if command fails and onerr is None, return status.  if ui object,
    print error message and return status, else raise onerr object as
    exception.'''
    def py2shell(val):
        'convert python object into string that is useful to shell'
        if val is None or val is False:
            return '0'
        if val is True:
            return '1'
        return str(val)
    origcmd = cmd
    if os.name == 'nt':
        cmd = '"%s"' % cmd
    env = dict(os.environ)
    env.update((k, py2shell(v)) for k, v in environ.iteritems())
    env['HG'] = hgexecutable()
    rc = subprocess.call(cmd, shell=True, close_fds=closefds,
                         env=env, cwd=cwd)
    if sys.platform == 'OpenVMS' and rc & 1:
        rc = 0
    if rc and onerr:
        errmsg = '%s %s' % (os.path.basename(origcmd.split(None, 1)[0]),
                            explain_exit(rc)[0])
        if errprefix:
            errmsg = '%s: %s' % (errprefix, errmsg)
        try:
            onerr.warn(errmsg + '\n')
        except AttributeError:
            raise onerr(errmsg)
    return rc

def checksignature(func):
    '''wrap a function with code to check for calling errors'''
    def check(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except TypeError:
            if len(traceback.extract_tb(sys.exc_info()[2])) == 1:
                raise error.SignatureError
            raise

    return check

# os.path.lexists is not available on python2.3
def lexists(filename):
    "test whether a file with this name exists. does not follow symlinks"
    try:
        os.lstat(filename)
    except:
        return False
    return True

def unlink(f):
    """unlink and remove the directory if it is empty"""
    os.unlink(f)
    # try removing directories that might now be empty
    try:
        os.removedirs(os.path.dirname(f))
    except OSError:
        pass

def copyfile(src, dest):
    "copy a file, preserving mode and atime/mtime"
    if os.path.islink(src):
        try:
            os.unlink(dest)
        except:
            pass
        os.symlink(os.readlink(src), dest)
    else:
        try:
            shutil.copyfile(src, dest)
            shutil.copystat(src, dest)
        except shutil.Error, inst:
            raise Abort(str(inst))

def copyfiles(src, dst, hardlink=None):
    """Copy a directory tree using hardlinks if possible"""

    if hardlink is None:
        hardlink = (os.stat(src).st_dev ==
                    os.stat(os.path.dirname(dst)).st_dev)

    if os.path.isdir(src):
        os.mkdir(dst)
        for name, kind in osutil.listdir(src):
            srcname = os.path.join(src, name)
            dstname = os.path.join(dst, name)
            copyfiles(srcname, dstname, hardlink)
    else:
        if hardlink:
            try:
                os_link(src, dst)
            except (IOError, OSError):
                hardlink = False
                shutil.copy(src, dst)
        else:
            shutil.copy(src, dst)

class path_auditor(object):
    '''ensure that a filesystem path contains no banned components.
    the following properties of a path are checked:

    - under top-level .hg
    - starts at the root of a windows drive
    - contains ".."
    - traverses a symlink (e.g. a/symlink_here/b)
    - inside a nested repository'''

    def __init__(self, root):
        self.audited = set()
        self.auditeddir = set()
        self.root = root

    def __call__(self, path):
        if path in self.audited:
            return
        normpath = os.path.normcase(path)
        parts = splitpath(normpath)
        if (os.path.splitdrive(path)[0]
            or parts[0].lower() in ('.hg', '.hg.', '')
            or os.pardir in parts):
            raise Abort(_("path contains illegal component: %s") % path)
        if '.hg' in path.lower():
            lparts = [p.lower() for p in parts]
            for p in '.hg', '.hg.':
                if p in lparts[1:]:
                    pos = lparts.index(p)
                    base = os.path.join(*parts[:pos])
                    raise Abort(_('path %r is inside repo %r') % (path, base))
        def check(prefix):
            curpath = os.path.join(self.root, prefix)
            try:
                st = os.lstat(curpath)
            except OSError, err:
                # EINVAL can be raised as invalid path syntax under win32.
                # They must be ignored for patterns can be checked too.
                if err.errno not in (errno.ENOENT, errno.ENOTDIR, errno.EINVAL):
                    raise
            else:
                if stat.S_ISLNK(st.st_mode):
                    raise Abort(_('path %r traverses symbolic link %r') %
                                (path, prefix))
                elif (stat.S_ISDIR(st.st_mode) and
                      os.path.isdir(os.path.join(curpath, '.hg'))):
                    raise Abort(_('path %r is inside repo %r') %
                                (path, prefix))
        parts.pop()
        prefixes = []
        while parts:
            prefix = os.sep.join(parts)
            if prefix in self.auditeddir:
                break
            check(prefix)
            prefixes.append(prefix)
            parts.pop()

        self.audited.add(path)
        # only add prefixes to the cache after checking everything: we don't
        # want to add "foo/bar/baz" before checking if there's a "foo/.hg"
        self.auditeddir.update(prefixes)

def nlinks(pathname):
    """Return number of hardlinks for the given file."""
    return os.lstat(pathname).st_nlink

if hasattr(os, 'link'):
    os_link = os.link
else:
    def os_link(src, dst):
        raise OSError(0, _("Hardlinks not supported"))

def lookup_reg(key, name=None, scope=None):
    return None

def hidewindow():
    """Hide current shell window.

    Used to hide the window opened when starting asynchronous
    child process under Windows, unneeded on other systems.
    """
    pass

if os.name == 'nt':
    from windows import *
else:
    from posix import *

def makelock(info, pathname):
    try:
        return os.symlink(info, pathname)
    except OSError, why:
        if why.errno == errno.EEXIST:
            raise
    except AttributeError: # no symlink in os
        pass

    ld = os.open(pathname, os.O_CREAT | os.O_WRONLY | os.O_EXCL)
    os.write(ld, info)
    os.close(ld)

def readlock(pathname):
    try:
        return os.readlink(pathname)
    except OSError, why:
        if why.errno not in (errno.EINVAL, errno.ENOSYS):
            raise
    except AttributeError: # no symlink in os
        pass
    return posixfile(pathname).read()

def fstat(fp):
    '''stat file object that may not have fileno method.'''
    try:
        return os.fstat(fp.fileno())
    except AttributeError:
        return os.stat(fp.name)

# File system features

def checkcase(path):
    """
    Check whether the given path is on a case-sensitive filesystem

    Requires a path (like /foo/.hg) ending with a foldable final
    directory component.
    """
    s1 = os.stat(path)
    d, b = os.path.split(path)
    p2 = os.path.join(d, b.upper())
    if path == p2:
        p2 = os.path.join(d, b.lower())
    try:
        s2 = os.stat(p2)
        if s2 == s1:
            return False
        return True
    except:
        return True

_fspathcache = {}
def fspath(name, root):
    '''Get name in the case stored in the filesystem

    The name is either relative to root, or it is an absolute path starting
    with root. Note that this function is unnecessary, and should not be
    called, for case-sensitive filesystems (simply because it's expensive).
    '''
    # If name is absolute, make it relative
    if name.lower().startswith(root.lower()):
        l = len(root)
        if name[l] == os.sep or name[l] == os.altsep:
            l = l + 1
        name = name[l:]

    if not os.path.exists(os.path.join(root, name)):
        return None

    seps = os.sep
    if os.altsep:
        seps = seps + os.altsep
    # Protect backslashes. This gets silly very quickly.
    seps.replace('\\','\\\\')
    pattern = re.compile(r'([^%s]+)|([%s]+)' % (seps, seps))
    dir = os.path.normcase(os.path.normpath(root))
    result = []
    for part, sep in pattern.findall(name):
        if sep:
            result.append(sep)
            continue

        if dir not in _fspathcache:
            _fspathcache[dir] = os.listdir(dir)
        contents = _fspathcache[dir]

        lpart = part.lower()
        lenp = len(part)
        for n in contents:
            if lenp == len(n) and n.lower() == lpart:
                result.append(n)
                break
        else:
            # Cannot happen, as the file exists!
            result.append(part)
        dir = os.path.join(dir, lpart)

    return ''.join(result)

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

def needbinarypatch():
    """return True if patches should be applied in binary mode by default."""
    return os.name == 'nt'

def endswithsep(path):
    '''Check path ends with os.sep or os.altsep.'''
    return path.endswith(os.sep) or os.altsep and path.endswith(os.altsep)

def splitpath(path):
    '''Split path by os.sep.
    Note that this function does not use os.altsep because this is
    an alternative of simple "xxx.split(os.sep)".
    It is recommended to use os.path.normpath() before using this
    function if need.'''
    return path.split(os.sep)

def gui():
    '''Are we running in a GUI?'''
    return os.name == "nt" or os.name == "mac" or os.environ.get("DISPLAY")

def mktempcopy(name, emptyok=False, createmode=None):
    """Create a temporary file with the same contents from name

    The permission bits are copied from the original file.

    If the temporary file is going to be truncated immediately, you
    can use emptyok=True as an optimization.

    Returns the name of the temporary file.
    """
    d, fn = os.path.split(name)
    fd, temp = tempfile.mkstemp(prefix='.%s-' % fn, dir=d)
    os.close(fd)
    # Temporary files are created with mode 0600, which is usually not
    # what we want.  If the original file already exists, just copy
    # its mode.  Otherwise, manually obey umask.
    try:
        st_mode = os.lstat(name).st_mode & 0777
    except OSError, inst:
        if inst.errno != errno.ENOENT:
            raise
        st_mode = createmode
        if st_mode is None:
            st_mode = ~umask
        st_mode &= 0666
    os.chmod(temp, st_mode)
    if emptyok:
        return temp
    try:
        try:
            ifp = posixfile(name, "rb")
        except IOError, inst:
            if inst.errno == errno.ENOENT:
                return temp
            if not getattr(inst, 'filename', None):
                inst.filename = name
            raise
        ofp = posixfile(temp, "wb")
        for chunk in filechunkiter(ifp):
            ofp.write(chunk)
        ifp.close()
        ofp.close()
    except:
        try: os.unlink(temp)
        except: pass
        raise
    return temp

class atomictempfile(object):
    """file-like object that atomically updates a file

    All writes will be redirected to a temporary copy of the original
    file.  When rename is called, the copy is renamed to the original
    name, making the changes visible.
    """
    def __init__(self, name, mode, createmode):
        self.__name = name
        self._fp = None
        self.temp = mktempcopy(name, emptyok=('w' in mode),
                               createmode=createmode)
        self._fp = posixfile(self.temp, mode)

    def __getattr__(self, name):
        return getattr(self._fp, name)

    def rename(self):
        if not self._fp.closed:
            self._fp.close()
            rename(self.temp, localpath(self.__name))

    def __del__(self):
        if not self._fp:
            return
        if not self._fp.closed:
            try:
                os.unlink(self.temp)
            except: pass
            self._fp.close()

def makedirs(name, mode=None):
    """recursive directory creation with parent mode inheritance"""
    try:
        os.mkdir(name)
        if mode is not None:
            os.chmod(name, mode)
        return
    except OSError, err:
        if err.errno == errno.EEXIST:
            return
        if err.errno != errno.ENOENT:
            raise
    parent = os.path.abspath(os.path.dirname(name))
    makedirs(parent, mode)
    makedirs(name, mode)

class opener(object):
    """Open files relative to a base directory

    This class is used to hide the details of COW semantics and
    remote file access from higher level code.
    """
    def __init__(self, base, audit=True):
        self.base = base
        if audit:
            self.audit_path = path_auditor(base)
        else:
            self.audit_path = always
        self.createmode = None

    @propertycache
    def _can_symlink(self):
        return checklink(self.base)

    def _fixfilemode(self, name):
        if self.createmode is None:
            return
        os.chmod(name, self.createmode & 0666)

    def __call__(self, path, mode="r", text=False, atomictemp=False):
        self.audit_path(path)
        f = os.path.join(self.base, path)

        if not text and "b" not in mode:
            mode += "b" # for that other OS

        nlink = -1
        if mode not in ("r", "rb"):
            try:
                nlink = nlinks(f)
            except OSError:
                nlink = 0
                d = os.path.dirname(f)
                if not os.path.isdir(d):
                    makedirs(d, self.createmode)
            if atomictemp:
                return atomictempfile(f, mode, self.createmode)
            if nlink > 1:
                rename(mktempcopy(f), f)
        fp = posixfile(f, mode)
        if nlink == 0:
            self._fixfilemode(f)
        return fp

    def symlink(self, src, dst):
        self.audit_path(dst)
        linkname = os.path.join(self.base, dst)
        try:
            os.unlink(linkname)
        except OSError:
            pass

        dirname = os.path.dirname(linkname)
        if not os.path.exists(dirname):
            makedirs(dirname, self.createmode)

        if self._can_symlink:
            try:
                os.symlink(src, linkname)
            except OSError, err:
                raise OSError(err.errno, _('could not symlink to %r: %s') %
                              (src, err.strerror), linkname)
        else:
            f = self(dst, "w")
            f.write(src)
            f.close()
            self._fixfilemode(dst)

class chunkbuffer(object):
    """Allow arbitrary sized chunks of data to be efficiently read from an
    iterator over chunks of arbitrary size."""

    def __init__(self, in_iter):
        """in_iter is the iterator that's iterating over the input chunks.
        targetsize is how big a buffer to try to maintain."""
        self.iter = iter(in_iter)
        self.buf = ''
        self.targetsize = 2**16

    def read(self, l):
        """Read L bytes of data from the iterator of chunks of data.
        Returns less than L bytes if the iterator runs dry."""
        if l > len(self.buf) and self.iter:
            # Clamp to a multiple of self.targetsize
            targetsize = max(l, self.targetsize)
            collector = cStringIO.StringIO()
            collector.write(self.buf)
            collected = len(self.buf)
            for chunk in self.iter:
                collector.write(chunk)
                collected += len(chunk)
                if collected >= targetsize:
                    break
            if collected < targetsize:
                self.iter = False
            self.buf = collector.getvalue()
        if len(self.buf) == l:
            s, self.buf = str(self.buf), ''
        else:
            s, self.buf = self.buf[:l], buffer(self.buf, l)
        return s

def filechunkiter(f, size=65536, limit=None):
    """Create a generator that produces the data in the file size
    (default 65536) bytes at a time, up to optional limit (default is
    to read all data).  Chunks may be less than size bytes if the
    chunk is the last chunk in the file, or the file is a socket or
    some other type of file that sometimes reads less data than is
    requested."""
    assert size >= 0
    assert limit is None or limit >= 0
    while True:
        if limit is None:
            nbytes = size
        else:
            nbytes = min(limit, size)
        s = nbytes and f.read(nbytes)
        if not s:
            break
        if limit:
            limit -= len(s)
        yield s

def makedate():
    lt = time.localtime()
    if lt[8] == 1 and time.daylight:
        tz = time.altzone
    else:
        tz = time.timezone
    return time.mktime(lt), tz

def datestr(date=None, format='%a %b %d %H:%M:%S %Y %1%2'):
    """represent a (unixtime, offset) tuple as a localized time.
    unixtime is seconds since the epoch, and offset is the time zone's
    number of seconds away from UTC. if timezone is false, do not
    append time zone to string."""
    t, tz = date or makedate()
    if "%1" in format or "%2" in format:
        sign = (tz > 0) and "-" or "+"
        minutes = abs(tz) // 60
        format = format.replace("%1", "%c%02d" % (sign, minutes // 60))
        format = format.replace("%2", "%02d" % (minutes % 60))
    s = time.strftime(format, time.gmtime(float(t) - tz))
    return s

def shortdate(date=None):
    """turn (timestamp, tzoff) tuple into iso 8631 date."""
    return datestr(date, format='%Y-%m-%d')

def strdate(string, format, defaults=[]):
    """parse a localized time string and return a (unixtime, offset) tuple.
    if the string cannot be parsed, ValueError is raised."""
    def timezone(string):
        tz = string.split()[-1]
        if tz[0] in "+-" and len(tz) == 5 and tz[1:].isdigit():
            sign = (tz[0] == "+") and 1 or -1
            hours = int(tz[1:3])
            minutes = int(tz[3:5])
            return -sign * (hours * 60 + minutes) * 60
        if tz == "GMT" or tz == "UTC":
            return 0
        return None

    # NOTE: unixtime = localunixtime + offset
    offset, date = timezone(string), string
    if offset != None:
        date = " ".join(string.split()[:-1])

    # add missing elements from defaults
    for part in defaults:
        found = [True for p in part if ("%"+p) in format]
        if not found:
            date += "@" + defaults[part]
            format += "@%" + part[0]

    timetuple = time.strptime(date, format)
    localunixtime = int(calendar.timegm(timetuple))
    if offset is None:
        # local timezone
        unixtime = int(time.mktime(timetuple))
        offset = unixtime - localunixtime
    else:
        unixtime = localunixtime + offset
    return unixtime, offset

def parsedate(date, formats=None, defaults=None):
    """parse a localized date/time string and return a (unixtime, offset) tuple.

    The date may be a "unixtime offset" string or in one of the specified
    formats. If the date already is a (unixtime, offset) tuple, it is returned.
    """
    if not date:
        return 0, 0
    if isinstance(date, tuple) and len(date) == 2:
        return date
    if not formats:
        formats = defaultdateformats
    date = date.strip()
    try:
        when, offset = map(int, date.split(' '))
    except ValueError:
        # fill out defaults
        if not defaults:
            defaults = {}
        now = makedate()
        for part in "d mb yY HI M S".split():
            if part not in defaults:
                if part[0] in "HMS":
                    defaults[part] = "00"
                else:
                    defaults[part] = datestr(now, "%" + part[0])

        for format in formats:
            try:
                when, offset = strdate(date, format, defaults)
            except (ValueError, OverflowError):
                pass
            else:
                break
        else:
            raise Abort(_('invalid date: %r ') % date)
    # validate explicit (probably user-specified) date and
    # time zone offset. values must fit in signed 32 bits for
    # current 32-bit linux runtimes. timezones go from UTC-12
    # to UTC+14
    if abs(when) > 0x7fffffff:
        raise Abort(_('date exceeds 32 bits: %d') % when)
    if offset < -50400 or offset > 43200:
        raise Abort(_('impossible time zone offset: %d') % offset)
    return when, offset

def matchdate(date):
    """Return a function that matches a given date match specifier

    Formats include:

    '{date}' match a given date to the accuracy provided

    '<{date}' on or before a given date

    '>{date}' on or after a given date

    """

    def lower(date):
        d = dict(mb="1", d="1")
        return parsedate(date, extendeddateformats, d)[0]

    def upper(date):
        d = dict(mb="12", HI="23", M="59", S="59")
        for days in "31 30 29".split():
            try:
                d["d"] = days
                return parsedate(date, extendeddateformats, d)[0]
            except:
                pass
        d["d"] = "28"
        return parsedate(date, extendeddateformats, d)[0]

    date = date.strip()
    if date[0] == "<":
        when = upper(date[1:])
        return lambda x: x <= when
    elif date[0] == ">":
        when = lower(date[1:])
        return lambda x: x >= when
    elif date[0] == "-":
        try:
            days = int(date[1:])
        except ValueError:
            raise Abort(_("invalid day spec: %s") % date[1:])
        when = makedate()[0] - days * 3600 * 24
        return lambda x: x >= when
    elif " to " in date:
        a, b = date.split(" to ")
        start, stop = lower(a), upper(b)
        return lambda x: x >= start and x <= stop
    else:
        start, stop = lower(date), upper(date)
        return lambda x: x >= start and x <= stop

def shortuser(user):
    """Return a short representation of a user name or email address."""
    f = user.find('@')
    if f >= 0:
        user = user[:f]
    f = user.find('<')
    if f >= 0:
        user = user[f + 1:]
    f = user.find(' ')
    if f >= 0:
        user = user[:f]
    f = user.find('.')
    if f >= 0:
        user = user[:f]
    return user

def email(author):
    '''get email of author.'''
    r = author.find('>')
    if r == -1:
        r = None
    return author[author.find('<') + 1:r]

def ellipsis(text, maxlength=400):
    """Trim string to at most maxlength (default: 400) characters."""
    if len(text) <= maxlength:
        return text
    else:
        return "%s..." % (text[:maxlength - 3])

def walkrepos(path, followsym=False, seen_dirs=None, recurse=False):
    '''yield every hg repository under path, recursively.'''
    def errhandler(err):
        if err.filename == path:
            raise err
    if followsym and hasattr(os.path, 'samestat'):
        def _add_dir_if_not_there(dirlst, dirname):
            match = False
            samestat = os.path.samestat
            dirstat = os.stat(dirname)
            for lstdirstat in dirlst:
                if samestat(dirstat, lstdirstat):
                    match = True
                    break
            if not match:
                dirlst.append(dirstat)
            return not match
    else:
        followsym = False

    if (seen_dirs is None) and followsym:
        seen_dirs = []
        _add_dir_if_not_there(seen_dirs, path)
    for root, dirs, files in os.walk(path, topdown=True, onerror=errhandler):
        dirs.sort()
        if '.hg' in dirs:
            yield root # found a repository
            qroot = os.path.join(root, '.hg', 'patches')
            if os.path.isdir(os.path.join(qroot, '.hg')):
                yield qroot # we have a patch queue repo here
            if recurse:
                # avoid recursing inside the .hg directory
                dirs.remove('.hg')
            else:
                dirs[:] = [] # don't descend further
        elif followsym:
            newdirs = []
            for d in dirs:
                fname = os.path.join(root, d)
                if _add_dir_if_not_there(seen_dirs, fname):
                    if os.path.islink(fname):
                        for hgname in walkrepos(fname, True, seen_dirs):
                            yield hgname
                    else:
                        newdirs.append(d)
            dirs[:] = newdirs

_rcpath = None

def os_rcpath():
    '''return default os-specific hgrc search path'''
    path = system_rcpath()
    path.extend(user_rcpath())
    path = [os.path.normpath(f) for f in path]
    return path

def rcpath():
    '''return hgrc search path. if env var HGRCPATH is set, use it.
    for each item in path, if directory, use files ending in .rc,
    else use item.
    make HGRCPATH empty to only look in .hg/hgrc of current repo.
    if no HGRCPATH, use default os-specific path.'''
    global _rcpath
    if _rcpath is None:
        if 'HGRCPATH' in os.environ:
            _rcpath = []
            for p in os.environ['HGRCPATH'].split(os.pathsep):
                if not p:
                    continue
                p = expandpath(p)
                if os.path.isdir(p):
                    for f, kind in osutil.listdir(p):
                        if f.endswith('.rc'):
                            _rcpath.append(os.path.join(p, f))
                else:
                    _rcpath.append(p)
        else:
            _rcpath = os_rcpath()
    return _rcpath

def bytecount(nbytes):
    '''return byte count formatted as readable string, with units'''

    units = (
        (100, 1 << 30, _('%.0f GB')),
        (10, 1 << 30, _('%.1f GB')),
        (1, 1 << 30, _('%.2f GB')),
        (100, 1 << 20, _('%.0f MB')),
        (10, 1 << 20, _('%.1f MB')),
        (1, 1 << 20, _('%.2f MB')),
        (100, 1 << 10, _('%.0f KB')),
        (10, 1 << 10, _('%.1f KB')),
        (1, 1 << 10, _('%.2f KB')),
        (1, 1, _('%.0f bytes')),
        )

    for multiplier, divisor, format in units:
        if nbytes >= divisor * multiplier:
            return format % (nbytes / float(divisor))
    return units[-1][2] % nbytes

def drop_scheme(scheme, path):
    sc = scheme + ':'
    if path.startswith(sc):
        path = path[len(sc):]
        if path.startswith('//'):
            if scheme == 'file':
                i = path.find('/', 2)
                if i == -1:
                    return ''
                # On Windows, absolute paths are rooted at the current drive
                # root. On POSIX they are rooted at the file system root.
                if os.name == 'nt':
                    droot = os.path.splitdrive(os.getcwd())[0] + '/'
                    path = os.path.join(droot, path[i + 1:])
                else:
                    path = path[i:]
            else:
                path = path[2:]
    return path

def uirepr(s):
    # Avoid double backslash in Windows path repr()
    return repr(s).replace('\\\\', '\\')

def wrap(line, hangindent, width=None):
    if width is None:
        width = termwidth() - 2
    if width <= hangindent:
        # adjust for weird terminal size
        width = max(78, hangindent + 1)
    padding = '\n' + ' ' * hangindent
    # To avoid corrupting multi-byte characters in line, we must wrap
    # a Unicode string instead of a bytestring.
    try:
        u = line.decode(encoding.encoding)
        w = padding.join(textwrap.wrap(u, width=width - hangindent))
        return w.encode(encoding.encoding)
    except UnicodeDecodeError:
        return padding.join(textwrap.wrap(line, width=width - hangindent))

def iterlines(iterator):
    for chunk in iterator:
        for line in chunk.splitlines():
            yield line

def expandpath(path):
    return os.path.expanduser(os.path.expandvars(path))

def hgcmd():
    """Return the command used to execute current hg

    This is different from hgexecutable() because on Windows we want
    to avoid things opening new shell windows like batch files, so we
    get either the python call or current executable.
    """
    if main_is_frozen():
        return [sys.executable]
    return gethgcmd()

def rundetached(args, condfn):
    """Execute the argument list in a detached process.

    condfn is a callable which is called repeatedly and should return
    True once the child process is known to have started successfully.
    At this point, the child process PID is returned. If the child
    process fails to start or finishes before condfn() evaluates to
    True, return -1.
    """
    # Windows case is easier because the child process is either
    # successfully starting and validating the condition or exiting
    # on failure. We just poll on its PID. On Unix, if the child
    # process fails to start, it will be left in a zombie state until
    # the parent wait on it, which we cannot do since we expect a long
    # running process on success. Instead we listen for SIGCHLD telling
    # us our child process terminated.
    terminated = set()
    def handler(signum, frame):
        terminated.add(os.wait())
    prevhandler = None
    if hasattr(signal, 'SIGCHLD'):
        prevhandler = signal.signal(signal.SIGCHLD, handler)
    try:
        pid = spawndetached(args)
        while not condfn():
            if ((pid in terminated or not testpid(pid))
                and not condfn()):
                return -1
            time.sleep(0.1)
        return pid
    finally:
        if prevhandler is not None:
            signal.signal(signal.SIGCHLD, prevhandler)

try:
    any, all = any, all
except NameError:
    def any(iterable):
        for i in iterable:
            if i:
                return True
        return False

    def all(iterable):
        for i in iterable:
            if not i:
                return False
        return True

def termwidth():
    if 'COLUMNS' in os.environ:
        try:
            return int(os.environ['COLUMNS'])
        except ValueError:
            pass
    return termwidth_()
