# pycompat.py - portability shim for python 3
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Mercurial portability shim for python 3.

This contains aliases to hide python version-specific details from the core.
"""

from __future__ import absolute_import

import getopt
import inspect
import json
import os
import shlex
import sys
import tempfile

ispy3 = sys.version_info[0] >= 3
ispypy = '__pypy__' in sys.builtin_module_names
TYPE_CHECKING = False

if not globals():  # hide this from non-pytype users
    import typing

    TYPE_CHECKING = typing.TYPE_CHECKING

if not ispy3:
    import cookielib
    import cPickle as pickle
    import httplib
    import Queue as queue
    import SocketServer as socketserver
    import xmlrpclib

    from .thirdparty.concurrent import futures

    def future_set_exception_info(f, exc_info):
        f.set_exception_info(*exc_info)


else:
    import concurrent.futures as futures
    import http.cookiejar as cookielib
    import http.client as httplib
    import pickle
    import queue as queue
    import socketserver
    import xmlrpc.client as xmlrpclib

    def future_set_exception_info(f, exc_info):
        f.set_exception(exc_info[0])


def identity(a):
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


if ispy3:
    import builtins
    import codecs
    import functools
    import io
    import struct

    if os.name == r'nt' and sys.version_info >= (3, 6):
        # MBCS (or ANSI) filesystem encoding must be used as before.
        # Otherwise non-ASCII filenames in existing repositories would be
        # corrupted.
        # This must be set once prior to any fsencode/fsdecode calls.
        sys._enablelegacywindowsfsencoding()  # pytype: disable=module-attr

    fsencode = os.fsencode
    fsdecode = os.fsdecode
    oscurdir = os.curdir.encode('ascii')
    oslinesep = os.linesep.encode('ascii')
    osname = os.name.encode('ascii')
    ospathsep = os.pathsep.encode('ascii')
    ospardir = os.pardir.encode('ascii')
    ossep = os.sep.encode('ascii')
    osaltsep = os.altsep
    if osaltsep:
        osaltsep = osaltsep.encode('ascii')
    osdevnull = os.devnull.encode('ascii')

    sysplatform = sys.platform.encode('ascii')
    sysexecutable = sys.executable
    if sysexecutable:
        sysexecutable = os.fsencode(sysexecutable)
    bytesio = io.BytesIO
    # TODO deprecate stringio name, as it is a lie on Python 3.
    stringio = bytesio

    def maplist(*args):
        return list(map(*args))

    def rangelist(*args):
        return list(range(*args))

    def ziplist(*args):
        return list(zip(*args))

    rawinput = input
    getargspec = inspect.getfullargspec

    long = int

    if getattr(sys, 'argv', None) is not None:
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

        >>> class bytesable(object):
        ...     def __bytes__(self):
        ...         return b'bytes'
        >>> bytestr(bytesable())
        'bytes'

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

        def __new__(cls, s=b''):
            if isinstance(s, bytestr):
                return s
            if not isinstance(
                s, (bytes, bytearray)
            ) and not hasattr(  # hasattr-py3-only
                s, u'__bytes__'
            ):
                s = str(s).encode('ascii')
            return bytes.__new__(cls, s)

        def __getitem__(self, key):
            s = bytes.__getitem__(self, key)
            if not isinstance(s, bytes):
                s = bytechr(s)
            return s

        def __iter__(self):
            return iterbytestr(bytes.__iter__(self))

        def __repr__(self):
            return bytes.__repr__(self)[1:]  # drop b''

    def iterbytestr(s):
        """Iterate bytes as if it were a str object of Python 2"""
        return map(bytechr, s)

    def maybebytestr(s):
        """Promote bytes to bytestr"""
        if isinstance(s, bytes):
            return bytestr(s)
        return s

    def sysbytes(s):
        """Convert an internal str (e.g. keyword, __doc__) back to bytes

        This never raises UnicodeEncodeError, but only ASCII characters
        can be round-trip by sysstr(sysbytes(s)).
        """
        if isinstance(s, bytes):
            return s
        return s.encode('utf-8')

    def sysstr(s):
        """Return a keyword str to be passed to Python functions such as
        getattr() and str.encode()

        This never raises UnicodeDecodeError. Non-ascii characters are
        considered invalid and mapped to arbitrary but unique code points
        such that 'sysstr(a) != sysstr(b)' for all 'a != b'.
        """
        if isinstance(s, builtins.str):
            return s
        return s.decode('latin-1')

    def strurl(url):
        """Converts a bytes url back to str"""
        if isinstance(url, bytes):
            return url.decode('ascii')
        return url

    def bytesurl(url):
        """Converts a str url to bytes by encoding in ascii"""
        if isinstance(url, str):
            return url.encode('ascii')
        return url

    def raisewithtb(exc, tb):
        """Raise exception with the given traceback"""
        raise exc.with_traceback(tb)

    def getdoc(obj):
        """Get docstring as bytes; may be None so gettext() won't confuse it
        with _('')"""
        doc = getattr(obj, '__doc__', None)
        if doc is None:
            return doc
        return sysbytes(doc)

    def _wrapattrfunc(f):
        @functools.wraps(f)
        def w(object, name, *args):
            return f(object, sysstr(name), *args)

        return w

    # these wrappers are automagically imported by hgloader
    delattr = _wrapattrfunc(builtins.delattr)
    getattr = _wrapattrfunc(builtins.getattr)
    hasattr = _wrapattrfunc(builtins.hasattr)
    setattr = _wrapattrfunc(builtins.setattr)
    xrange = builtins.range
    unicode = str

    def open(name, mode=b'r', buffering=-1, encoding=None):
        return builtins.open(name, sysstr(mode), buffering, encoding)

    safehasattr = _wrapattrfunc(builtins.hasattr)

    def _getoptbwrapper(orig, args, shortlist, namelist):
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

    def strkwargs(dic):
        """
        Converts the keys of a python dictonary to str i.e. unicodes so that
        they can be passed as keyword arguments as dictionaries with bytes keys
        can't be passed as keyword arguments to functions on Python 3.
        """
        dic = {k.decode('latin-1'): v for k, v in dic.items()}
        return dic

    def byteskwargs(dic):
        """
        Converts keys of python dictionaries to bytes as they were converted to
        str to pass that dictonary as a keyword argument on Python 3.
        """
        dic = {k.encode('latin-1'): v for k, v in dic.items()}
        return dic

    # TODO: handle shlex.shlex().
    def shlexsplit(s, comments=False, posix=True):
        """
        Takes bytes argument, convert it to str i.e. unicodes, pass that into
        shlex.split(), convert the returned value to bytes and return that for
        Python 3 compatibility as shelx.split() don't accept bytes on Python 3.
        """
        ret = shlex.split(s.decode('latin-1'), comments, posix)
        return [a.encode('latin-1') for a in ret]

    iteritems = lambda x: x.items()
    itervalues = lambda x: x.values()

    # Python 3.5's json.load and json.loads require str. We polyfill its
    # code for detecting encoding from bytes.
    if sys.version_info[0:2] < (3, 6):

        def _detect_encoding(b):
            bstartswith = b.startswith
            if bstartswith((codecs.BOM_UTF32_BE, codecs.BOM_UTF32_LE)):
                return 'utf-32'
            if bstartswith((codecs.BOM_UTF16_BE, codecs.BOM_UTF16_LE)):
                return 'utf-16'
            if bstartswith(codecs.BOM_UTF8):
                return 'utf-8-sig'

            if len(b) >= 4:
                if not b[0]:
                    # 00 00 -- -- - utf-32-be
                    # 00 XX -- -- - utf-16-be
                    return 'utf-16-be' if b[1] else 'utf-32-be'
                if not b[1]:
                    # XX 00 00 00 - utf-32-le
                    # XX 00 00 XX - utf-16-le
                    # XX 00 XX -- - utf-16-le
                    return 'utf-16-le' if b[2] or b[3] else 'utf-32-le'
            elif len(b) == 2:
                if not b[0]:
                    # 00 XX - utf-16-be
                    return 'utf-16-be'
                if not b[1]:
                    # XX 00 - utf-16-le
                    return 'utf-16-le'
            # default
            return 'utf-8'

        def json_loads(s, *args, **kwargs):
            if isinstance(s, (bytes, bytearray)):
                s = s.decode(_detect_encoding(s), 'surrogatepass')

            return json.loads(s, *args, **kwargs)

    else:
        json_loads = json.loads

else:
    import cStringIO

    xrange = xrange
    unicode = unicode
    bytechr = chr
    byterepr = repr
    bytestr = str
    iterbytestr = iter
    maybebytestr = identity
    sysbytes = identity
    sysstr = identity
    strurl = identity
    bytesurl = identity
    open = open
    delattr = delattr
    getattr = getattr
    hasattr = hasattr
    setattr = setattr

    # this can't be parsed on Python 3
    exec(b'def raisewithtb(exc, tb):\n    raise exc, None, tb\n')

    def fsencode(filename):
        """
        Partial backport from os.py in Python 3, which only accepts bytes.
        In Python 2, our paths should only ever be bytes, a unicode path
        indicates a bug.
        """
        if isinstance(filename, str):
            return filename
        else:
            raise TypeError("expect str, not %s" % type(filename).__name__)

    # In Python 2, fsdecode() has a very chance to receive bytes. So it's
    # better not to touch Python 2 part as it's already working fine.
    fsdecode = identity

    def getdoc(obj):
        return getattr(obj, '__doc__', None)

    _notset = object()

    def safehasattr(thing, attr):
        return getattr(thing, attr, _notset) is not _notset

    def _getoptbwrapper(orig, args, shortlist, namelist):
        return orig(args, shortlist, namelist)

    strkwargs = identity
    byteskwargs = identity

    oscurdir = os.curdir
    oslinesep = os.linesep
    osname = os.name
    ospathsep = os.pathsep
    ospardir = os.pardir
    ossep = os.sep
    osaltsep = os.altsep
    osdevnull = os.devnull
    long = long
    if getattr(sys, 'argv', None) is not None:
        sysargv = sys.argv
    sysplatform = sys.platform
    sysexecutable = sys.executable
    shlexsplit = shlex.split
    bytesio = cStringIO.StringIO
    stringio = bytesio
    maplist = map
    rangelist = range
    ziplist = zip
    rawinput = raw_input
    getargspec = inspect.getargspec
    iteritems = lambda x: x.iteritems()
    itervalues = lambda x: x.itervalues()
    json_loads = json.loads

isjython = sysplatform.startswith(b'java')

isdarwin = sysplatform.startswith(b'darwin')
islinux = sysplatform.startswith(b'linux')
isposix = osname == b'posix'
iswindows = osname == b'nt'


def getoptb(args, shortlist, namelist):
    return _getoptbwrapper(getopt.getopt, args, shortlist, namelist)


def gnugetoptb(args, shortlist, namelist):
    return _getoptbwrapper(getopt.gnu_getopt, args, shortlist, namelist)


def mkdtemp(suffix=b'', prefix=b'tmp', dir=None):
    return tempfile.mkdtemp(suffix, prefix, dir)


# text=True is not supported; use util.from/tonativeeol() instead
def mkstemp(suffix=b'', prefix=b'tmp', dir=None):
    return tempfile.mkstemp(suffix, prefix, dir)


# TemporaryFile does not support an "encoding=" argument on python2.
# This wrapper file are always open in byte mode.
def unnamedtempfile(mode=None, *args, **kwargs):
    if mode is None:
        mode = 'w+b'
    else:
        mode = sysstr(mode)
    assert 'b' in mode
    return tempfile.TemporaryFile(mode, *args, **kwargs)


# NamedTemporaryFile does not support an "encoding=" argument on python2.
# This wrapper file are always open in byte mode.
def namedtempfile(
    mode=b'w+b', bufsize=-1, suffix=b'', prefix=b'tmp', dir=None, delete=True
):
    mode = sysstr(mode)
    assert 'b' in mode
    return tempfile.NamedTemporaryFile(
        mode, bufsize, suffix=suffix, prefix=prefix, dir=dir, delete=delete
    )
