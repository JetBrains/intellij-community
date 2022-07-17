# osutil.py - pure Python version of osutil.c
#
#  Copyright 2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import, division

import ctypes
import ctypes.util
import os
import socket
import stat as statmod

from ..pycompat import getattr
from .. import (
    encoding,
    pycompat,
)


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
    """listdir(path, stat=False) -> list_of_tuples

    Return a sorted list containing information about the entries
    in the directory.

    If stat is True, each element is a 3-tuple:

      (name, type, stat object)

    Otherwise, each element is a 2-tuple:

      (name, type)
    """
    result = []
    prefix = path
    if not prefix.endswith(pycompat.ossep):
        prefix += pycompat.ossep
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


if not pycompat.iswindows:
    posixfile = open

    _SCM_RIGHTS = 0x01
    _socklen_t = ctypes.c_uint

    if pycompat.sysplatform.startswith(b'linux'):
        # socket.h says "the type should be socklen_t but the definition of
        # the kernel is incompatible with this."
        _cmsg_len_t = ctypes.c_size_t
        _msg_controllen_t = ctypes.c_size_t
        _msg_iovlen_t = ctypes.c_size_t
    else:
        _cmsg_len_t = _socklen_t
        _msg_controllen_t = _socklen_t
        _msg_iovlen_t = ctypes.c_int

    class _iovec(ctypes.Structure):
        _fields_ = [
            (u'iov_base', ctypes.c_void_p),
            (u'iov_len', ctypes.c_size_t),
        ]

    class _msghdr(ctypes.Structure):
        _fields_ = [
            (u'msg_name', ctypes.c_void_p),
            (u'msg_namelen', _socklen_t),
            (u'msg_iov', ctypes.POINTER(_iovec)),
            (u'msg_iovlen', _msg_iovlen_t),
            (u'msg_control', ctypes.c_void_p),
            (u'msg_controllen', _msg_controllen_t),
            (u'msg_flags', ctypes.c_int),
        ]

    class _cmsghdr(ctypes.Structure):
        _fields_ = [
            (u'cmsg_len', _cmsg_len_t),
            (u'cmsg_level', ctypes.c_int),
            (u'cmsg_type', ctypes.c_int),
            (u'cmsg_data', ctypes.c_ubyte * 0),
        ]

    _libc = ctypes.CDLL(ctypes.util.find_library(u'c'), use_errno=True)
    _recvmsg = getattr(_libc, 'recvmsg', None)
    if _recvmsg:
        _recvmsg.restype = getattr(ctypes, 'c_ssize_t', ctypes.c_long)
        _recvmsg.argtypes = (
            ctypes.c_int,
            ctypes.POINTER(_msghdr),
            ctypes.c_int,
        )
    else:
        # recvmsg isn't always provided by libc; such systems are unsupported
        def _recvmsg(sockfd, msg, flags):
            raise NotImplementedError(b'unsupported platform')

    def _CMSG_FIRSTHDR(msgh):
        if msgh.msg_controllen < ctypes.sizeof(_cmsghdr):
            return
        cmsgptr = ctypes.cast(msgh.msg_control, ctypes.POINTER(_cmsghdr))
        return cmsgptr.contents

    # The pure version is less portable than the native version because the
    # handling of socket ancillary data heavily depends on C preprocessor.
    # Also, some length fields are wrongly typed in Linux kernel.
    def recvfds(sockfd):
        """receive list of file descriptors via socket"""
        dummy = (ctypes.c_ubyte * 1)()
        iov = _iovec(ctypes.cast(dummy, ctypes.c_void_p), ctypes.sizeof(dummy))
        cbuf = ctypes.create_string_buffer(256)
        msgh = _msghdr(
            None,
            0,
            ctypes.pointer(iov),
            1,
            ctypes.cast(cbuf, ctypes.c_void_p),
            ctypes.sizeof(cbuf),
            0,
        )
        r = _recvmsg(sockfd, ctypes.byref(msgh), 0)
        if r < 0:
            e = ctypes.get_errno()
            raise OSError(e, os.strerror(e))
        # assumes that the first cmsg has fds because it isn't easy to write
        # portable CMSG_NXTHDR() with ctypes.
        cmsg = _CMSG_FIRSTHDR(msgh)
        if not cmsg:
            return []
        if (
            cmsg.cmsg_level != socket.SOL_SOCKET
            or cmsg.cmsg_type != _SCM_RIGHTS
        ):
            return []
        rfds = ctypes.cast(cmsg.cmsg_data, ctypes.POINTER(ctypes.c_int))
        rfdscount = (
            cmsg.cmsg_len - _cmsghdr.cmsg_data.offset
        ) // ctypes.sizeof(ctypes.c_int)
        return [rfds[i] for i in pycompat.xrange(rfdscount)]


else:
    import msvcrt

    _kernel32 = ctypes.windll.kernel32  # pytype: disable=module-attr

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

    _kernel32.CreateFileA.argtypes = [
        _LPCSTR,
        _DWORD,
        _DWORD,
        ctypes.c_void_p,
        _DWORD,
        _DWORD,
        _HANDLE,
    ]
    _kernel32.CreateFileA.restype = _HANDLE

    def _raiseioerror(name):
        err = ctypes.WinError()  # pytype: disable=module-attr
        raise IOError(
            err.errno, '%s: %s' % (encoding.strfromlocal(name), err.strerror)
        )

    class posixfile(object):
        """a file object aiming for POSIX-like semantics

        CPython's open() returns a file that was opened *without* setting the
        _FILE_SHARE_DELETE flag, which causes rename and unlink to abort.
        This even happens if any hardlinked copy of the file is in open state.
        We set _FILE_SHARE_DELETE here, so files opened with posixfile can be
        renamed and deleted while they are held open.
        Note that if a file opened with posixfile is unlinked, the file
        remains but cannot be opened again or be recreated under the same name,
        until all reading processes have closed the file."""

        def __init__(self, name, mode=b'r', bufsize=-1):
            if b'b' in mode:
                flags = _O_BINARY
            else:
                flags = _O_TEXT

            m0 = mode[0:1]
            if m0 == b'r' and b'+' not in mode:
                flags |= _O_RDONLY
                access = _GENERIC_READ
            else:
                # work around http://support.microsoft.com/kb/899149 and
                # set _O_RDWR for 'w' and 'a', even if mode has no '+'
                flags |= _O_RDWR
                access = _GENERIC_READ | _GENERIC_WRITE

            if m0 == b'r':
                creation = _OPEN_EXISTING
            elif m0 == b'w':
                creation = _CREATE_ALWAYS
            elif m0 == b'a':
                creation = _OPEN_ALWAYS
                flags |= _O_APPEND
            else:
                raise ValueError("invalid mode: %s" % pycompat.sysstr(mode))

            fh = _kernel32.CreateFileA(
                name,
                access,
                _FILE_SHARE_READ | _FILE_SHARE_WRITE | _FILE_SHARE_DELETE,
                None,
                creation,
                _FILE_ATTRIBUTE_NORMAL,
                None,
            )
            if fh == _INVALID_HANDLE_VALUE:
                _raiseioerror(name)

            fd = msvcrt.open_osfhandle(fh, flags)  # pytype: disable=module-attr
            if fd == -1:
                _kernel32.CloseHandle(fh)
                _raiseioerror(name)

            f = os.fdopen(fd, pycompat.sysstr(mode), bufsize)
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
            """mimics the read-only attributes of Python file objects
            by raising 'TypeError: readonly attribute' if someone tries:
              f = posixfile('foo.txt')
              f.name = 'bla'
            """
            return self._file.__setattr__(name, value)

        def __enter__(self):
            self._file.__enter__()
            return self

        def __exit__(self, exc_type, exc_value, exc_tb):
            return self._file.__exit__(exc_type, exc_value, exc_tb)
