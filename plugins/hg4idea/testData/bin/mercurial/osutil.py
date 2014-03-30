# osutil.py - pure Python version of osutil.c
#
#  Copyright 2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os
import stat as statmod

def _mode_to_kind(mode):
    if statmod.S_ISREG(mode):
        return statmod.S_IFREG
    if statmod.S_ISDIR(mode):
        return statmod.S_IFDIR
    if statmod.S_ISLNK(mode):
        return statmod.S_IFLNK
    if statmod.S_ISBLK(mode):
        return statmod.S_IFBLK
    if statmod.S_ISCHR(mode):
        return statmod.S_IFCHR
    if statmod.S_ISFIFO(mode):
        return statmod.S_IFIFO
    if statmod.S_ISSOCK(mode):
        return statmod.S_IFSOCK
    return mode

def listdir(path, stat=False, skip=None):
    '''listdir(path, stat=False) -> list_of_tuples

    Return a sorted list containing information about the entries
    in the directory.

    If stat is True, each element is a 3-tuple:

      (name, type, stat object)

    Otherwise, each element is a 2-tuple:

      (name, type)
    '''
    result = []
    prefix = path
    if not prefix.endswith(os.sep):
        prefix += os.sep
    names = os.listdir(path)
    names.sort()
    for fn in names:
        st = os.lstat(prefix + fn)
        if fn == skip and statmod.S_ISDIR(st.st_mode):
            return []
        if stat:
            result.append((fn, _mode_to_kind(st.st_mode), st))
        else:
            result.append((fn, _mode_to_kind(st.st_mode)))
    return result

if os.name != 'nt':
    posixfile = open
else:
    import ctypes, msvcrt

    _kernel32 = ctypes.windll.kernel32

    _DWORD = ctypes.c_ulong
    _LPCSTR = _LPSTR = ctypes.c_char_p
    _HANDLE = ctypes.c_void_p

    _INVALID_HANDLE_VALUE = _HANDLE(-1).value

    # CreateFile
    _FILE_SHARE_READ = 0x00000001
    _FILE_SHARE_WRITE = 0x00000002
    _FILE_SHARE_DELETE = 0x00000004

    _CREATE_ALWAYS = 2
    _OPEN_EXISTING = 3
    _OPEN_ALWAYS = 4

    _GENERIC_READ = 0x80000000
    _GENERIC_WRITE = 0x40000000

    _FILE_ATTRIBUTE_NORMAL = 0x80

    # open_osfhandle flags
    _O_RDONLY = 0x0000
    _O_RDWR = 0x0002
    _O_APPEND = 0x0008

    _O_TEXT = 0x4000
    _O_BINARY = 0x8000

    # types of parameters of C functions used (required by pypy)

    _kernel32.CreateFileA.argtypes = [_LPCSTR, _DWORD, _DWORD, ctypes.c_void_p,
        _DWORD, _DWORD, _HANDLE]
    _kernel32.CreateFileA.restype = _HANDLE

    def _raiseioerror(name):
        err = ctypes.WinError()
        raise IOError(err.errno, '%s: %s' % (name, err.strerror))

    class posixfile(object):
        '''a file object aiming for POSIX-like semantics

        CPython's open() returns a file that was opened *without* setting the
        _FILE_SHARE_DELETE flag, which causes rename and unlink to abort.
        This even happens if any hardlinked copy of the file is in open state.
        We set _FILE_SHARE_DELETE here, so files opened with posixfile can be
        renamed and deleted while they are held open.
        Note that if a file opened with posixfile is unlinked, the file
        remains but cannot be opened again or be recreated under the same name,
        until all reading processes have closed the file.'''

        def __init__(self, name, mode='r', bufsize=-1):
            if 'b' in mode:
                flags = _O_BINARY
            else:
                flags = _O_TEXT

            m0 = mode[0]
            if m0 == 'r' and '+' not in mode:
                flags |= _O_RDONLY
                access = _GENERIC_READ
            else:
                # work around http://support.microsoft.com/kb/899149 and
                # set _O_RDWR for 'w' and 'a', even if mode has no '+'
                flags |= _O_RDWR
                access = _GENERIC_READ | _GENERIC_WRITE

            if m0 == 'r':
                creation = _OPEN_EXISTING
            elif m0 == 'w':
                creation = _CREATE_ALWAYS
            elif m0 == 'a':
                creation = _OPEN_ALWAYS
                flags |= _O_APPEND
            else:
                raise ValueError("invalid mode: %s" % mode)

            fh = _kernel32.CreateFileA(name, access,
                    _FILE_SHARE_READ | _FILE_SHARE_WRITE | _FILE_SHARE_DELETE,
                    None, creation, _FILE_ATTRIBUTE_NORMAL, None)
            if fh == _INVALID_HANDLE_VALUE:
                _raiseioerror(name)

            fd = msvcrt.open_osfhandle(fh, flags)
            if fd == -1:
                _kernel32.CloseHandle(fh)
                _raiseioerror(name)

            f = os.fdopen(fd, mode, bufsize)
            # unfortunately, f.name is '<fdopen>' at this point -- so we store
            # the name on this wrapper. We cannot just assign to f.name,
            # because that attribute is read-only.
            object.__setattr__(self, 'name', name)
            object.__setattr__(self, '_file', f)

        def __iter__(self):
            return self._file

        def __getattr__(self, name):
            return getattr(self._file, name)

        def __setattr__(self, name, value):
            '''mimics the read-only attributes of Python file objects
            by raising 'TypeError: readonly attribute' if someone tries:
              f = posixfile('foo.txt')
              f.name = 'bla'  '''
            return self._file.__setattr__(name, value)
