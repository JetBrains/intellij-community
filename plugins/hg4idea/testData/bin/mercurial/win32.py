# win32.py - utility functions that use win32 API
#
# Copyright 2005-2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import ctypes
import ctypes.wintypes as wintypes
import errno
import msvcrt
import os
import random
import subprocess

from typing import (
    List,
    NoReturn,
    Optional,
    Tuple,
)

from . import (
    encoding,
    pycompat,
)

# pytype: disable=module-attr
_kernel32 = ctypes.windll.kernel32
_advapi32 = ctypes.windll.advapi32
_user32 = ctypes.windll.user32
_crypt32 = ctypes.windll.crypt32
# pytype: enable=module-attr

_BOOL = ctypes.c_long
_WORD = ctypes.c_ushort
_DWORD = ctypes.c_ulong
_UINT = ctypes.c_uint
_LONG = ctypes.c_long
_LPCSTR = _LPSTR = ctypes.c_char_p
_HANDLE = ctypes.c_void_p
_HWND = _HANDLE
_PCCERT_CONTEXT = ctypes.c_void_p
_MAX_PATH = wintypes.MAX_PATH

_INVALID_HANDLE_VALUE = _HANDLE(-1).value

# GetLastError
_ERROR_SUCCESS = 0
_ERROR_NO_MORE_FILES = 18
_ERROR_INVALID_PARAMETER = 87
_ERROR_BROKEN_PIPE = 109
_ERROR_INSUFFICIENT_BUFFER = 122
_ERROR_NO_DATA = 232

# WPARAM is defined as UINT_PTR (unsigned type)
# LPARAM is defined as LONG_PTR (signed type)
if ctypes.sizeof(ctypes.c_long) == ctypes.sizeof(ctypes.c_void_p):
    _WPARAM = ctypes.c_ulong
    _LPARAM = ctypes.c_long
elif ctypes.sizeof(ctypes.c_longlong) == ctypes.sizeof(ctypes.c_void_p):
    _WPARAM = ctypes.c_ulonglong
    _LPARAM = ctypes.c_longlong


class _FILETIME(ctypes.Structure):
    _fields_ = [('dwLowDateTime', _DWORD), ('dwHighDateTime', _DWORD)]


class _BY_HANDLE_FILE_INFORMATION(ctypes.Structure):
    _fields_ = [
        ('dwFileAttributes', _DWORD),
        ('ftCreationTime', _FILETIME),
        ('ftLastAccessTime', _FILETIME),
        ('ftLastWriteTime', _FILETIME),
        ('dwVolumeSerialNumber', _DWORD),
        ('nFileSizeHigh', _DWORD),
        ('nFileSizeLow', _DWORD),
        ('nNumberOfLinks', _DWORD),
        ('nFileIndexHigh', _DWORD),
        ('nFileIndexLow', _DWORD),
    ]


# CreateFile
_FILE_SHARE_READ = 0x00000001
_FILE_SHARE_WRITE = 0x00000002
_FILE_SHARE_DELETE = 0x00000004

_OPEN_EXISTING = 3

_FILE_FLAG_BACKUP_SEMANTICS = 0x02000000

# SetFileAttributes
_FILE_ATTRIBUTE_NORMAL = 0x80
_FILE_ATTRIBUTE_NOT_CONTENT_INDEXED = 0x2000

# Process Security and Access Rights
_PROCESS_QUERY_INFORMATION = 0x0400

# GetExitCodeProcess
_STILL_ACTIVE = 259


class _STARTUPINFO(ctypes.Structure):
    _fields_ = [
        ('cb', _DWORD),
        ('lpReserved', _LPSTR),
        ('lpDesktop', _LPSTR),
        ('lpTitle', _LPSTR),
        ('dwX', _DWORD),
        ('dwY', _DWORD),
        ('dwXSize', _DWORD),
        ('dwYSize', _DWORD),
        ('dwXCountChars', _DWORD),
        ('dwYCountChars', _DWORD),
        ('dwFillAttribute', _DWORD),
        ('dwFlags', _DWORD),
        ('wShowWindow', _WORD),
        ('cbReserved2', _WORD),
        ('lpReserved2', ctypes.c_char_p),
        ('hStdInput', _HANDLE),
        ('hStdOutput', _HANDLE),
        ('hStdError', _HANDLE),
    ]


class _PROCESS_INFORMATION(ctypes.Structure):
    _fields_ = [
        ('hProcess', _HANDLE),
        ('hThread', _HANDLE),
        ('dwProcessId', _DWORD),
        ('dwThreadId', _DWORD),
    ]


_CREATE_NO_WINDOW = 0x08000000
_SW_HIDE = 0


class _COORD(ctypes.Structure):
    _fields_ = [('X', ctypes.c_short), ('Y', ctypes.c_short)]


class _SMALL_RECT(ctypes.Structure):
    _fields_ = [
        ('Left', ctypes.c_short),
        ('Top', ctypes.c_short),
        ('Right', ctypes.c_short),
        ('Bottom', ctypes.c_short),
    ]


class _CONSOLE_SCREEN_BUFFER_INFO(ctypes.Structure):
    _fields_ = [
        ('dwSize', _COORD),
        ('dwCursorPosition', _COORD),
        ('wAttributes', _WORD),
        ('srWindow', _SMALL_RECT),
        ('dwMaximumWindowSize', _COORD),
    ]


_STD_OUTPUT_HANDLE = _DWORD(-11).value
_STD_ERROR_HANDLE = _DWORD(-12).value

# CERT_TRUST_STATUS dwErrorStatus
CERT_TRUST_IS_PARTIAL_CHAIN = 0x10000

# CertCreateCertificateContext encodings
X509_ASN_ENCODING = 0x00000001
PKCS_7_ASN_ENCODING = 0x00010000

# These structs are only complete enough to achieve what we need.
class CERT_CHAIN_CONTEXT(ctypes.Structure):
    _fields_ = (
        ("cbSize", _DWORD),
        # CERT_TRUST_STATUS struct
        ("dwErrorStatus", _DWORD),
        ("dwInfoStatus", _DWORD),
        ("cChain", _DWORD),
        ("rgpChain", ctypes.c_void_p),
        ("cLowerQualityChainContext", _DWORD),
        ("rgpLowerQualityChainContext", ctypes.c_void_p),
        ("fHasRevocationFreshnessTime", _BOOL),
        ("dwRevocationFreshnessTime", _DWORD),
    )


class CERT_USAGE_MATCH(ctypes.Structure):
    _fields_ = (
        ("dwType", _DWORD),
        # CERT_ENHKEY_USAGE struct
        ("cUsageIdentifier", _DWORD),
        ("rgpszUsageIdentifier", ctypes.c_void_p),  # LPSTR *
    )


class CERT_CHAIN_PARA(ctypes.Structure):
    _fields_ = (
        ("cbSize", _DWORD),
        ("RequestedUsage", CERT_USAGE_MATCH),
        ("RequestedIssuancePolicy", CERT_USAGE_MATCH),
        ("dwUrlRetrievalTimeout", _DWORD),
        ("fCheckRevocationFreshnessTime", _BOOL),
        ("dwRevocationFreshnessTime", _DWORD),
        ("pftCacheResync", ctypes.c_void_p),  # LPFILETIME
        ("pStrongSignPara", ctypes.c_void_p),  # PCCERT_STRONG_SIGN_PARA
        ("dwStrongSignFlags", _DWORD),
    )


# types of parameters of C functions used (required by pypy)

_crypt32.CertCreateCertificateContext.argtypes = [
    _DWORD,  # cert encoding
    ctypes.c_char_p,  # cert
    _DWORD,
]  # cert size
_crypt32.CertCreateCertificateContext.restype = _PCCERT_CONTEXT

_crypt32.CertGetCertificateChain.argtypes = [
    ctypes.c_void_p,  # HCERTCHAINENGINE
    _PCCERT_CONTEXT,
    ctypes.c_void_p,  # LPFILETIME
    ctypes.c_void_p,  # HCERTSTORE
    ctypes.c_void_p,  # PCERT_CHAIN_PARA
    _DWORD,
    ctypes.c_void_p,  # LPVOID
    ctypes.c_void_p,  # PCCERT_CHAIN_CONTEXT *
]
_crypt32.CertGetCertificateChain.restype = _BOOL

_crypt32.CertFreeCertificateContext.argtypes = [_PCCERT_CONTEXT]
_crypt32.CertFreeCertificateContext.restype = _BOOL

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

_kernel32.GetFileInformationByHandle.argtypes = [_HANDLE, ctypes.c_void_p]
_kernel32.GetFileInformationByHandle.restype = _BOOL

_kernel32.CloseHandle.argtypes = [_HANDLE]
_kernel32.CloseHandle.restype = _BOOL

try:
    _kernel32.CreateHardLinkA.argtypes = [_LPCSTR, _LPCSTR, ctypes.c_void_p]
    _kernel32.CreateHardLinkA.restype = _BOOL
except AttributeError:
    pass

_kernel32.SetFileAttributesA.argtypes = [_LPCSTR, _DWORD]
_kernel32.SetFileAttributesA.restype = _BOOL

_DRIVE_UNKNOWN = 0
_DRIVE_NO_ROOT_DIR = 1
_DRIVE_REMOVABLE = 2
_DRIVE_FIXED = 3
_DRIVE_REMOTE = 4
_DRIVE_CDROM = 5
_DRIVE_RAMDISK = 6

_kernel32.GetDriveTypeA.argtypes = [_LPCSTR]
_kernel32.GetDriveTypeA.restype = _UINT

_kernel32.GetVolumeInformationA.argtypes = [
    _LPCSTR,
    ctypes.c_void_p,
    _DWORD,
    ctypes.c_void_p,
    ctypes.c_void_p,
    ctypes.c_void_p,
    ctypes.c_void_p,
    _DWORD,
]
_kernel32.GetVolumeInformationA.restype = _BOOL

_kernel32.GetVolumePathNameA.argtypes = [_LPCSTR, ctypes.c_void_p, _DWORD]
_kernel32.GetVolumePathNameA.restype = _BOOL

_kernel32.OpenProcess.argtypes = [_DWORD, _BOOL, _DWORD]
_kernel32.OpenProcess.restype = _HANDLE

_kernel32.GetExitCodeProcess.argtypes = [_HANDLE, ctypes.c_void_p]
_kernel32.GetExitCodeProcess.restype = _BOOL

_kernel32.GetLastError.argtypes = []
_kernel32.GetLastError.restype = _DWORD

_kernel32.GetModuleFileNameA.argtypes = [_HANDLE, ctypes.c_void_p, _DWORD]
_kernel32.GetModuleFileNameA.restype = _DWORD

_kernel32.CreateProcessA.argtypes = [
    _LPCSTR,
    _LPCSTR,
    ctypes.c_void_p,
    ctypes.c_void_p,
    _BOOL,
    _DWORD,
    ctypes.c_void_p,
    _LPCSTR,
    ctypes.c_void_p,
    ctypes.c_void_p,
]
_kernel32.CreateProcessA.restype = _BOOL

_kernel32.ExitProcess.argtypes = [_UINT]
_kernel32.ExitProcess.restype = None

_kernel32.GetCurrentProcessId.argtypes = []
_kernel32.GetCurrentProcessId.restype = _DWORD

# pytype: disable=module-attr
_SIGNAL_HANDLER = ctypes.WINFUNCTYPE(_BOOL, _DWORD)
# pytype: enable=module-attr
_kernel32.SetConsoleCtrlHandler.argtypes = [_SIGNAL_HANDLER, _BOOL]
_kernel32.SetConsoleCtrlHandler.restype = _BOOL

_kernel32.SetConsoleMode.argtypes = [_HANDLE, _DWORD]
_kernel32.SetConsoleMode.restype = _BOOL

_kernel32.GetConsoleMode.argtypes = [_HANDLE, ctypes.c_void_p]
_kernel32.GetConsoleMode.restype = _BOOL

_kernel32.GetStdHandle.argtypes = [_DWORD]
_kernel32.GetStdHandle.restype = _HANDLE

_kernel32.GetConsoleScreenBufferInfo.argtypes = [_HANDLE, ctypes.c_void_p]
_kernel32.GetConsoleScreenBufferInfo.restype = _BOOL

_advapi32.GetUserNameA.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
_advapi32.GetUserNameA.restype = _BOOL

_user32.GetWindowThreadProcessId.argtypes = [_HANDLE, ctypes.c_void_p]
_user32.GetWindowThreadProcessId.restype = _DWORD

_user32.ShowWindow.argtypes = [_HANDLE, ctypes.c_int]
_user32.ShowWindow.restype = _BOOL

# pytype: disable=module-attr
_WNDENUMPROC = ctypes.WINFUNCTYPE(_BOOL, _HWND, _LPARAM)
# pytype: enable=module-attr
_user32.EnumWindows.argtypes = [_WNDENUMPROC, _LPARAM]
_user32.EnumWindows.restype = _BOOL

_kernel32.PeekNamedPipe.argtypes = [
    _HANDLE,
    ctypes.c_void_p,
    _DWORD,
    ctypes.c_void_p,
    ctypes.c_void_p,
    ctypes.c_void_p,
]
_kernel32.PeekNamedPipe.restype = _BOOL


def _raiseoserror(name: bytes) -> NoReturn:
    # Force the code to a signed int to avoid an 'int too large' error.
    # See https://bugs.python.org/issue28474
    code = _kernel32.GetLastError()
    if code > 0x7FFFFFFF:
        code -= 2 ** 32
    err = ctypes.WinError(code=code)  # pytype: disable=module-attr
    raise OSError(
        err.errno, '%s: %s' % (encoding.strfromlocal(name), err.strerror)
    )


def _getfileinfo(name: bytes) -> _BY_HANDLE_FILE_INFORMATION:
    fh = _kernel32.CreateFileA(
        name,
        0,
        _FILE_SHARE_READ | _FILE_SHARE_WRITE | _FILE_SHARE_DELETE,
        None,
        _OPEN_EXISTING,
        _FILE_FLAG_BACKUP_SEMANTICS,
        None,
    )
    if fh == _INVALID_HANDLE_VALUE:
        _raiseoserror(name)
    try:
        fi = _BY_HANDLE_FILE_INFORMATION()
        if not _kernel32.GetFileInformationByHandle(fh, ctypes.byref(fi)):
            _raiseoserror(name)
        return fi
    finally:
        _kernel32.CloseHandle(fh)


def checkcertificatechain(cert: bytes, build: bool = True) -> bool:
    """Tests the given certificate to see if there is a complete chain to a
    trusted root certificate.  As a side effect, missing certificates are
    downloaded and installed unless ``build=False``.  True is returned if a
    chain to a trusted root exists (even if built on the fly), otherwise
    False.  NB: A chain to a trusted root does NOT imply that the certificate
    is valid.
    """

    chainctxptr = ctypes.POINTER(CERT_CHAIN_CONTEXT)

    pchainctx = chainctxptr()
    chainpara = CERT_CHAIN_PARA(
        cbSize=ctypes.sizeof(CERT_CHAIN_PARA), RequestedUsage=CERT_USAGE_MATCH()
    )

    certctx = _crypt32.CertCreateCertificateContext(
        X509_ASN_ENCODING, cert, len(cert)
    )
    if certctx is None:
        _raiseoserror(b'CertCreateCertificateContext')

    flags = 0

    if not build:
        flags |= 0x100  # CERT_CHAIN_DISABLE_AUTH_ROOT_AUTO_UPDATE

    try:
        # Building the certificate chain will update root certs as necessary.
        if not _crypt32.CertGetCertificateChain(
            None,  # hChainEngine
            certctx,  # pCertContext
            None,  # pTime
            None,  # hAdditionalStore
            ctypes.byref(chainpara),
            flags,
            None,  # pvReserved
            ctypes.byref(pchainctx),
        ):
            _raiseoserror(b'CertGetCertificateChain')

        chainctx = pchainctx.contents

        return chainctx.dwErrorStatus & CERT_TRUST_IS_PARTIAL_CHAIN == 0
    finally:
        if pchainctx:
            _crypt32.CertFreeCertificateChain(pchainctx)
        _crypt32.CertFreeCertificateContext(certctx)


def oslink(src: bytes, dst: bytes) -> None:
    try:
        if not _kernel32.CreateHardLinkA(dst, src, None):
            _raiseoserror(src)
    except AttributeError:  # Wine doesn't support this function
        _raiseoserror(src)


def nlinks(name: bytes) -> int:
    '''return number of hardlinks for the given file'''
    return _getfileinfo(name).nNumberOfLinks


def samefile(path1: bytes, path2: bytes) -> bool:
    '''Returns whether path1 and path2 refer to the same file or directory.'''
    res1 = _getfileinfo(path1)
    res2 = _getfileinfo(path2)
    return (
        res1.dwVolumeSerialNumber == res2.dwVolumeSerialNumber
        and res1.nFileIndexHigh == res2.nFileIndexHigh
        and res1.nFileIndexLow == res2.nFileIndexLow
    )


def samedevice(path1: bytes, path2: bytes) -> bool:
    '''Returns whether path1 and path2 are on the same device.'''
    res1 = _getfileinfo(path1)
    res2 = _getfileinfo(path2)
    return res1.dwVolumeSerialNumber == res2.dwVolumeSerialNumber


def peekpipe(pipe) -> int:
    handle = msvcrt.get_osfhandle(pipe.fileno())  # pytype: disable=module-attr
    avail = _DWORD()

    if not _kernel32.PeekNamedPipe(
        handle, None, 0, None, ctypes.byref(avail), None
    ):
        err = _kernel32.GetLastError()
        if err == _ERROR_BROKEN_PIPE:
            return 0
        raise ctypes.WinError(err)  # pytype: disable=module-attr

    return avail.value


def lasterrorwaspipeerror(err) -> bool:
    if err.errno != errno.EINVAL:
        return False
    err = _kernel32.GetLastError()
    return err == _ERROR_BROKEN_PIPE or err == _ERROR_NO_DATA


def testpid(pid: int) -> bool:
    """return True if pid is still running or unable to
    determine, False otherwise"""
    h = _kernel32.OpenProcess(_PROCESS_QUERY_INFORMATION, False, pid)
    if h:
        try:
            status = _DWORD()
            if _kernel32.GetExitCodeProcess(h, ctypes.byref(status)):
                return status.value == _STILL_ACTIVE
        finally:
            _kernel32.CloseHandle(h)
    return _kernel32.GetLastError() != _ERROR_INVALID_PARAMETER


def executablepath() -> bytes:
    '''return full path of hg.exe'''
    size = 600
    buf = ctypes.create_string_buffer(size + 1)
    len = _kernel32.GetModuleFileNameA(None, ctypes.byref(buf), size)
    # pytype: disable=module-attr
    if len == 0:
        raise ctypes.WinError()  # Note: WinError is a function
    elif len == size:
        raise ctypes.WinError(_ERROR_INSUFFICIENT_BUFFER)
    # pytype: enable=module-attr
    return buf.value


def getvolumename(path: bytes) -> Optional[bytes]:
    """Get the mount point of the filesystem from a directory or file
    (best-effort)

    Returns None if we are unsure. Raises OSError on ENOENT, EPERM, etc.
    """
    # realpath() calls GetFullPathName()
    realpath = os.path.realpath(path)

    # allocate at least MAX_PATH long since GetVolumePathName('c:\\', buf, 4)
    # somehow fails on Windows XP
    size = max(len(realpath), _MAX_PATH) + 1
    buf = ctypes.create_string_buffer(size)

    if not _kernel32.GetVolumePathNameA(realpath, ctypes.byref(buf), size):
        # Note: WinError is a function
        raise ctypes.WinError()  # pytype: disable=module-attr

    return buf.value


def getfstype(path: bytes) -> Optional[bytes]:
    """Get the filesystem type name from a directory or file (best-effort)

    Returns None if we are unsure. Raises OSError on ENOENT, EPERM, etc.
    """
    volume = getvolumename(path)

    t = _kernel32.GetDriveTypeA(volume)

    if t == _DRIVE_REMOTE:
        return b'cifs'
    elif t not in (
        _DRIVE_REMOVABLE,
        _DRIVE_FIXED,
        _DRIVE_CDROM,
        _DRIVE_RAMDISK,
    ):
        return None

    size = _MAX_PATH + 1
    name = ctypes.create_string_buffer(size)

    if not _kernel32.GetVolumeInformationA(
        volume, None, 0, None, None, None, ctypes.byref(name), size
    ):
        # Note: WinError is a function
        raise ctypes.WinError()  # pytype: disable=module-attr

    return name.value


def getuser() -> bytes:
    '''return name of current user'''
    size = _DWORD(300)
    buf = ctypes.create_string_buffer(size.value + 1)
    if not _advapi32.GetUserNameA(ctypes.byref(buf), ctypes.byref(size)):
        raise ctypes.WinError()  # pytype: disable=module-attr
    return buf.value


_signalhandler: List[_SIGNAL_HANDLER] = []


def setsignalhandler() -> None:
    """Register a termination handler for console events including
    CTRL+C. python signal handlers do not work well with socket
    operations.
    """

    def handler(event):
        _kernel32.ExitProcess(1)

    if _signalhandler:
        return  # already registered
    h = _SIGNAL_HANDLER(handler)
    _signalhandler.append(h)  # needed to prevent garbage collection
    if not _kernel32.SetConsoleCtrlHandler(h, True):
        raise ctypes.WinError()  # pytype: disable=module-attr


def hidewindow() -> None:
    def callback(hwnd, pid):
        wpid = _DWORD()
        _user32.GetWindowThreadProcessId(hwnd, ctypes.byref(wpid))
        if pid == wpid.value:
            _user32.ShowWindow(hwnd, _SW_HIDE)
            return False  # stop enumerating windows
        return True

    pid = _kernel32.GetCurrentProcessId()
    _user32.EnumWindows(_WNDENUMPROC(callback), pid)


def termsize() -> Tuple[int, int]:
    # cmd.exe does not handle CR like a unix console, the CR is
    # counted in the line length. On 80 columns consoles, if 80
    # characters are written, the following CR won't apply on the
    # current line but on the new one. Keep room for it.
    width = 80 - 1
    height = 25
    # Query stderr to avoid problems with redirections
    screenbuf = _kernel32.GetStdHandle(
        _STD_ERROR_HANDLE
    )  # don't close the handle returned
    if screenbuf is None or screenbuf == _INVALID_HANDLE_VALUE:
        return width, height
    csbi = _CONSOLE_SCREEN_BUFFER_INFO()
    if not _kernel32.GetConsoleScreenBufferInfo(screenbuf, ctypes.byref(csbi)):
        return width, height
    width = csbi.srWindow.Right - csbi.srWindow.Left  # don't '+ 1'
    height = csbi.srWindow.Bottom - csbi.srWindow.Top + 1
    return width, height


def enablevtmode() -> bool:
    """Enable virtual terminal mode for the associated console.  Return True if
    enabled, else False."""

    ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x4

    handle = _kernel32.GetStdHandle(
        _STD_OUTPUT_HANDLE
    )  # don't close the handle
    if handle == _INVALID_HANDLE_VALUE:
        return False

    mode = _DWORD(0)

    if not _kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
        return False

    if (mode.value & ENABLE_VIRTUAL_TERMINAL_PROCESSING) == 0:
        mode.value |= ENABLE_VIRTUAL_TERMINAL_PROCESSING

        if not _kernel32.SetConsoleMode(handle, mode):
            return False

    return True


def spawndetached(args: List[bytes]) -> int:
    # No standard library function really spawns a fully detached
    # process under win32 because they allocate pipes or other objects
    # to handle standard streams communications. Passing these objects
    # to the child process requires handle inheritance to be enabled
    # which makes really detached processes impossible.
    si = _STARTUPINFO()
    si.cb = ctypes.sizeof(_STARTUPINFO)

    pi = _PROCESS_INFORMATION()

    env = b''
    for k in encoding.environ:
        env += b"%s=%s\0" % (k, encoding.environ[k])
    if not env:
        env = b'\0'
    env += b'\0'

    args = subprocess.list2cmdline(pycompat.rapply(encoding.strfromlocal, args))

    # TODO: CreateProcessW on py3?
    res = _kernel32.CreateProcessA(
        None,
        encoding.strtolocal(args),
        None,
        None,
        False,
        _CREATE_NO_WINDOW,
        env,
        encoding.getcwd(),
        ctypes.byref(si),
        ctypes.byref(pi),
    )
    if not res:
        raise ctypes.WinError()  # pytype: disable=module-attr

    _kernel32.CloseHandle(pi.hProcess)
    _kernel32.CloseHandle(pi.hThread)

    return pi.dwProcessId


def unlink(f: bytes) -> None:
    '''try to implement POSIX' unlink semantics on Windows'''

    if os.path.isdir(f):
        # use EPERM because it is POSIX prescribed value, even though
        # unlink(2) on directories returns EISDIR on Linux
        raise IOError(
            errno.EPERM,
            r"Unlinking directory not permitted: '%s'"
            % encoding.strfromlocal(f),
        )

    # POSIX allows to unlink and rename open files. Windows has serious
    # problems with doing that:
    # - Calling os.unlink (or os.rename) on a file f fails if f or any
    #   hardlinked copy of f has been opened with Python's open(). There is no
    #   way such a file can be deleted or renamed on Windows (other than
    #   scheduling the delete or rename for the next reboot).
    # - Calling os.unlink on a file that has been opened with Mercurial's
    #   posixfile (or comparable methods) will delay the actual deletion of
    #   the file for as long as the file is held open. The filename is blocked
    #   during that time and cannot be used for recreating a new file under
    #   that same name ("zombie file"). Directories containing such zombie files
    #   cannot be removed or moved.
    # A file that has been opened with posixfile can be renamed, so we rename
    # f to a random temporary name before calling os.unlink on it. This allows
    # callers to recreate f immediately while having other readers do their
    # implicit zombie filename blocking on a temporary name.

    for tries in range(10):
        temp = b'%s-%08x' % (f, random.randint(0, 0xFFFFFFFF))
        try:
            os.rename(f, temp)
            break
        except FileExistsError:
            pass
    else:
        raise IOError(errno.EEXIST, "No usable temporary filename found")

    try:
        os.unlink(temp)
    except OSError:
        # The unlink might have failed because the READONLY attribute may heave
        # been set on the original file. Rename works fine with READONLY set,
        # but not os.unlink. Reset all attributes and try again.
        _kernel32.SetFileAttributesA(temp, _FILE_ATTRIBUTE_NORMAL)
        try:
            os.unlink(temp)
        except OSError:
            # The unlink might have failed due to some very rude AV-Scanners.
            # Leaking a tempfile is the lesser evil than aborting here and
            # leaving some potentially serious inconsistencies.
            pass


def makedir(path: bytes, notindexed: bool) -> None:
    os.mkdir(path)
    if notindexed:
        _kernel32.SetFileAttributesA(path, _FILE_ATTRIBUTE_NOT_CONTENT_INDEXED)
