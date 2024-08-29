# pycompat.py - portability shim for python 3
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Mercurial portability shim for python 3.

This contains aliases to hide python version-specific details from the core.
"""


import builtins
import codecs
import concurrent.futures as futures
import getopt
import http.client as httplib
import http.cookiejar as cookielib
import inspect
import io
import json
import os
import queue
import shlex
import socketserver
import struct
import sys
import tempfile
import xmlrpc.client as xmlrpclib

from typing import (
    Any,
    AnyStr,
    BinaryIO,
    Callable,
    Dict,
    Iterable,
    Iterator,
    List,
    Mapping,
    NoReturn,
    Optional,
    Sequence,
    Tuple,
    Type,
    TypeVar,
    cast,
    overload,
)

ispy3 = sys.version_info[0] >= 3
ispypy = '__pypy__' in sys.builtin_module_names
TYPE_CHECKING = False

if not globals():  # hide this from non-pytype users
    import typing

    TYPE_CHECKING = typing.TYPE_CHECKING

_GetOptResult = Tuple[List[Tuple[bytes, bytes]], List[bytes]]
_T0 = TypeVar('_T0')
_T1 = TypeVar('_T1')
_S = TypeVar('_S')
_Tbytestr = TypeVar('_Tbytestr', bound='bytestr')


def future_set_exception_info(f, exc_info):
    f.set_exception(exc_info[0])


FileNotFoundError = builtins.FileNotFoundError


def identity(a: _T0) -> _T0:
    return a


def _rapply(f, xs):
    if xs is None:
        # assume None means non-value of optional data
        return xs
    if isinstance(xs, (list, set, tuple)):
        return type(xs)(_rapply(f, x) for x in xs)
    if isinstance(xs, dict):
        return type(xs)((_rapply(f, k), _rapply(f, v)) for k, v in xs.items())
    return f(xs)


def rapply(f, xs):
    """Apply function recursively to every item preserving the data structure

    >>> def f(x):
    ...     return 'f(%s)' % x
    >>> rapply(f, None) is None
    True
    >>> rapply(f, 'a')
    'f(a)'
    >>> rapply(f, {'a'}) == {'f(a)'}
    True
    >>> rapply(f, ['a', 'b', None, {'c': 'd'}, []])
    ['f(a)', 'f(b)', None, {'f(c)': 'f(d)'}, []]

    >>> xs = [object()]
    >>> rapply(identity, xs) is xs
    True
    """
    if f is identity:
        # fast path mainly for py2
        return xs
    return _rapply(f, xs)


if os.name == r'nt':
    # MBCS (or ANSI) filesystem encoding must be used as before.
    # Otherwise non-ASCII filenames in existing repositories would be
    # corrupted.
    # This must be set once prior to any fsencode/fsdecode calls.
    sys._enablelegacywindowsfsencoding()  # pytype: disable=module-attr

fsencode = os.fsencode
fsdecode = os.fsdecode
oscurdir: bytes = os.curdir.encode('ascii')
oslinesep: bytes = os.linesep.encode('ascii')
osname: bytes = os.name.encode('ascii')
ospathsep: bytes = os.pathsep.encode('ascii')
ospardir: bytes = os.pardir.encode('ascii')
ossep: bytes = os.sep.encode('ascii')
osaltsep: Optional[bytes] = os.altsep.encode('ascii') if os.altsep else None
osdevnull: bytes = os.devnull.encode('ascii')

sysplatform: bytes = sys.platform.encode('ascii')
sysexecutable: bytes = os.fsencode(sys.executable) if sys.executable else b''


if TYPE_CHECKING:

    @overload
    def maplist(f: Callable[[_T0], _S], arg: Iterable[_T0]) -> List[_S]:
        ...

    @overload
    def maplist(
        f: Callable[[_T0, _T1], _S], arg1: Iterable[_T0], arg2: Iterable[_T1]
    ) -> List[_S]:
        ...


def maplist(f, *args):
    return list(map(f, *args))


def rangelist(*args) -> List[int]:
    return list(range(*args))


def ziplist(*args):
    return list(zip(*args))


rawinput = input
getargspec = inspect.getfullargspec

long = int

if builtins.getattr(sys, 'argv', None) is not None:
    # On POSIX, the char** argv array is converted to Python str using
    # Py_DecodeLocale(). The inverse of this is Py_EncodeLocale(), which
    # isn't directly callable from Python code. In practice, os.fsencode()
    # can be used instead (this is recommended by Python's documentation
    # for sys.argv).
    #
    # On Windows, the wchar_t **argv is passed into the interpreter as-is.
    # Like POSIX, we need to emulate what Py_EncodeLocale() would do. But
    # there's an additional wrinkle. What we really want to access is the
    # ANSI codepage representation of the arguments, as this is what
    # `int main()` would receive if Python 3 didn't define `int wmain()`
    # (this is how Python 2 worked). To get that, we encode with the mbcs
    # encoding, which will pass CP_ACP to the underlying Windows API to
    # produce bytes.
    sysargv: List[bytes] = []
    if os.name == r'nt':
        sysargv = [a.encode("mbcs", "ignore") for a in sys.argv]
    else:
        sysargv = [fsencode(a) for a in sys.argv]

bytechr = struct.Struct('>B').pack
byterepr = b'%r'.__mod__


class bytestr(bytes):
    """A bytes which mostly acts as a Python 2 str

    >>> bytestr(), bytestr(bytearray(b'foo')), bytestr(u'ascii'), bytestr(1)
    ('', 'foo', 'ascii', '1')
    >>> s = bytestr(b'foo')
    >>> assert s is bytestr(s)

    __bytes__() should be called if provided:

    >>> class bytesable:
    ...     def __bytes__(self):
    ...         return b'bytes'
    >>> bytestr(bytesable())
    'bytes'

    ...unless the argument is the bytes *type* itself: it gets a
    __bytes__() method in Python 3.11, which cannot be used as in an instance
    of bytes:

    >>> bytestr(bytes)
    "<class 'bytes'>"

    There's no implicit conversion from non-ascii str as its encoding is
    unknown:

    >>> bytestr(chr(0x80)) # doctest: +ELLIPSIS
    Traceback (most recent call last):
      ...
    UnicodeEncodeError: ...

    Comparison between bytestr and bytes should work:

    >>> assert bytestr(b'foo') == b'foo'
    >>> assert b'foo' == bytestr(b'foo')
    >>> assert b'f' in bytestr(b'foo')
    >>> assert bytestr(b'f') in b'foo'

    Sliced elements should be bytes, not integer:

    >>> s[1], s[:2]
    (b'o', b'fo')
    >>> list(s), list(reversed(s))
    ([b'f', b'o', b'o'], [b'o', b'o', b'f'])

    As bytestr type isn't propagated across operations, you need to cast
    bytes to bytestr explicitly:

    >>> s = bytestr(b'foo').upper()
    >>> t = bytestr(s)
    >>> s[0], t[0]
    (70, b'F')

    Be careful to not pass a bytestr object to a function which expects
    bytearray-like behavior.

    >>> t = bytes(t)  # cast to bytes
    >>> assert type(t) is bytes
    """

    # Trick pytype into not demanding Iterable[int] be passed to __new__(),
    # since the appropriate bytes format is done internally.
    #
    # https://github.com/google/pytype/issues/500
    if TYPE_CHECKING:

        def __init__(self, s: object = b'') -> None:
            pass

    def __new__(cls: Type[_Tbytestr], s: object = b'') -> _Tbytestr:
        if isinstance(s, bytestr):
            return s
        if not isinstance(s, (bytes, bytearray)) and (
            isinstance(s, type)
            or not builtins.hasattr(s, u'__bytes__')  # hasattr-py3-only
        ):
            s = str(s).encode('ascii')
        return bytes.__new__(cls, s)

    # The base class uses `int` return in py3, but the point of this class is to
    # behave like py2.
    def __getitem__(self, key) -> bytes:  # pytype: disable=signature-mismatch
        s = bytes.__getitem__(self, key)
        if not isinstance(s, bytes):
            s = bytechr(s)
        return s

    # The base class expects `Iterator[int]` return in py3, but the point of
    # this class is to behave like py2.
    def __iter__(self) -> Iterator[bytes]:  # pytype: disable=signature-mismatch
        return iterbytestr(bytes.__iter__(self))

    def __repr__(self) -> str:
        return bytes.__repr__(self)[1:]  # drop b''


def iterbytestr(s: Iterable[int]) -> Iterator[bytes]:
    """Iterate bytes as if it were a str object of Python 2"""
    return map(bytechr, s)


if TYPE_CHECKING:

    @overload
    def maybebytestr(s: bytes) -> bytestr:
        ...

    @overload
    def maybebytestr(s: _T0) -> _T0:
        ...


def maybebytestr(s):
    """Promote bytes to bytestr"""
    if isinstance(s, bytes):
        return bytestr(s)
    return s


def sysbytes(s: AnyStr) -> bytes:
    """Convert an internal str (e.g. keyword, __doc__) back to bytes

    This never raises UnicodeEncodeError, but only ASCII characters
    can be round-trip by sysstr(sysbytes(s)).
    """
    if isinstance(s, bytes):
        return s
    return s.encode('utf-8')


def sysstr(s: AnyStr) -> str:
    """Return a keyword str to be passed to Python functions such as
    getattr() and str.encode()

    This never raises UnicodeDecodeError. Non-ascii characters are
    considered invalid and mapped to arbitrary but unique code points
    such that 'sysstr(a) != sysstr(b)' for all 'a != b'.
    """
    if isinstance(s, builtins.str):
        return s
    return s.decode('latin-1')


def strurl(url: AnyStr) -> str:
    """Converts a bytes url back to str"""
    if isinstance(url, bytes):
        return url.decode('ascii')
    return url


def bytesurl(url: AnyStr) -> bytes:
    """Converts a str url to bytes by encoding in ascii"""
    if isinstance(url, str):
        return url.encode('ascii')
    return url


def raisewithtb(exc: BaseException, tb) -> NoReturn:
    """Raise exception with the given traceback"""
    raise exc.with_traceback(tb)


def getdoc(obj: object) -> Optional[bytes]:
    """Get docstring as bytes; may be None so gettext() won't confuse it
    with _('')"""
    doc = builtins.getattr(obj, '__doc__', None)
    if doc is None:
        return doc
    return sysbytes(doc)


# these wrappers are automagically imported by hgloader
delattr = builtins.delattr
getattr = builtins.getattr
hasattr = builtins.hasattr
setattr = builtins.setattr
xrange = builtins.range
unicode = str


def open(
    name,
    mode: AnyStr = b'r',
    buffering: int = -1,
    encoding: Optional[str] = None,
) -> Any:
    # TODO: assert binary mode, and cast result to BinaryIO?
    return builtins.open(name, sysstr(mode), buffering, encoding)


safehasattr = builtins.hasattr


def _getoptbwrapper(
    orig, args: Sequence[bytes], shortlist: bytes, namelist: Sequence[bytes]
) -> _GetOptResult:
    """
    Takes bytes arguments, converts them to unicode, pass them to
    getopt.getopt(), convert the returned values back to bytes and then
    return them for Python 3 compatibility as getopt.getopt() don't accepts
    bytes on Python 3.
    """
    args = [a.decode('latin-1') for a in args]
    shortlist = shortlist.decode('latin-1')
    namelist = [a.decode('latin-1') for a in namelist]
    opts, args = orig(args, shortlist, namelist)
    opts = [(a[0].encode('latin-1'), a[1].encode('latin-1')) for a in opts]
    args = [a.encode('latin-1') for a in args]
    return opts, args


def strkwargs(dic: Mapping[bytes, _T0]) -> Dict[str, _T0]:
    """
    Converts the keys of a python dictonary to str i.e. unicodes so that
    they can be passed as keyword arguments as dictionaries with bytes keys
    can't be passed as keyword arguments to functions on Python 3.
    """
    dic = {k.decode('latin-1'): v for k, v in dic.items()}
    return dic


def byteskwargs(dic: Mapping[str, _T0]) -> Dict[bytes, _T0]:
    """
    Converts keys of python dictionaries to bytes as they were converted to
    str to pass that dictonary as a keyword argument on Python 3.
    """
    dic = {k.encode('latin-1'): v for k, v in dic.items()}
    return dic


# TODO: handle shlex.shlex().
def shlexsplit(
    s: bytes, comments: bool = False, posix: bool = True
) -> List[bytes]:
    """
    Takes bytes argument, convert it to str i.e. unicodes, pass that into
    shlex.split(), convert the returned value to bytes and return that for
    Python 3 compatibility as shelx.split() don't accept bytes on Python 3.
    """
    ret = shlex.split(s.decode('latin-1'), comments, posix)
    return [a.encode('latin-1') for a in ret]


iteritems = lambda x: x.items()
itervalues = lambda x: x.values()

json_loads = json.loads

isjython: bool = sysplatform.startswith(b'java')

isdarwin: bool = sysplatform.startswith(b'darwin')
islinux: bool = sysplatform.startswith(b'linux')
isposix: bool = osname == b'posix'
iswindows: bool = osname == b'nt'


def getoptb(
    args: Sequence[bytes], shortlist: bytes, namelist: Sequence[bytes]
) -> _GetOptResult:
    return _getoptbwrapper(getopt.getopt, args, shortlist, namelist)


def gnugetoptb(
    args: Sequence[bytes], shortlist: bytes, namelist: Sequence[bytes]
) -> _GetOptResult:
    return _getoptbwrapper(getopt.gnu_getopt, args, shortlist, namelist)


def mkdtemp(
    suffix: bytes = b'', prefix: bytes = b'tmp', dir: Optional[bytes] = None
) -> bytes:
    return tempfile.mkdtemp(suffix, prefix, dir)


# text=True is not supported; use util.from/tonativeeol() instead
def mkstemp(
    suffix: bytes = b'', prefix: bytes = b'tmp', dir: Optional[bytes] = None
) -> Tuple[int, bytes]:
    return tempfile.mkstemp(suffix, prefix, dir)


# TemporaryFile does not support an "encoding=" argument on python2.
# This wrapper file are always open in byte mode.
def unnamedtempfile(mode: Optional[bytes] = None, *args, **kwargs) -> BinaryIO:
    if mode is None:
        mode = 'w+b'
    else:
        mode = sysstr(mode)
    assert 'b' in mode
    return cast(BinaryIO, tempfile.TemporaryFile(mode, *args, **kwargs))


# NamedTemporaryFile does not support an "encoding=" argument on python2.
# This wrapper file are always open in byte mode.
def namedtempfile(
    mode: bytes = b'w+b',
    bufsize: int = -1,
    suffix: bytes = b'',
    prefix: bytes = b'tmp',
    dir: Optional[bytes] = None,
    delete: bool = True,
):
    mode = sysstr(mode)
    assert 'b' in mode
    return tempfile.NamedTemporaryFile(
        mode, bufsize, suffix=suffix, prefix=prefix, dir=dir, delete=delete
    )
