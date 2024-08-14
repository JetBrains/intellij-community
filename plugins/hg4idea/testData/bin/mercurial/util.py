# util.py - Mercurial utility functions and platform specific implementations
#
#  Copyright 2005 K. Thananchayan <thananck@yahoo.com>
#  Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#  Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Mercurial utility functions and platform specific implementations.

This contains helper routines that are independent of the SCM core and
hide platform-specific details from the core.
"""


import abc
import collections
import contextlib
import errno
import gc
import hashlib
import io
import itertools
import locale
import mmap
import os
import pickle  # provides util.pickle symbol
import re as remod
import shutil
import stat
import sys
import time
import traceback
import warnings

from typing import (
    Any,
    Iterable,
    Iterator,
    List,
    Optional,
    Tuple,
)

from .node import hex
from .thirdparty import attr
from .pycompat import (
    open,
)
from hgdemandimport import tracing
from . import (
    encoding,
    error,
    i18n,
    policy,
    pycompat,
    urllibcompat,
)
from .utils import (
    compression,
    hashutil,
    procutil,
    stringutil,
)

# keeps pyflakes happy
assert [
    Iterable,
    Iterator,
    List,
    Optional,
    Tuple,
]


base85 = policy.importmod('base85')
osutil = policy.importmod('osutil')

b85decode = base85.b85decode
b85encode = base85.b85encode

cookielib = pycompat.cookielib
httplib = pycompat.httplib
safehasattr = pycompat.safehasattr
socketserver = pycompat.socketserver
bytesio = io.BytesIO
# TODO deprecate stringio name, as it is a lie on Python 3.
stringio = bytesio
xmlrpclib = pycompat.xmlrpclib

httpserver = urllibcompat.httpserver
urlerr = urllibcompat.urlerr
urlreq = urllibcompat.urlreq

# workaround for win32mbcs
_filenamebytestr = pycompat.bytestr

if pycompat.iswindows:
    from . import windows as platform
else:
    from . import posix as platform

_ = i18n._

abspath = platform.abspath
bindunixsocket = platform.bindunixsocket
cachestat = platform.cachestat
checkexec = platform.checkexec
checklink = platform.checklink
copymode = platform.copymode
expandglobs = platform.expandglobs
getfsmountpoint = platform.getfsmountpoint
getfstype = platform.getfstype
get_password = platform.get_password
groupmembers = platform.groupmembers
groupname = platform.groupname
isexec = platform.isexec
isowner = platform.isowner
listdir = osutil.listdir
localpath = platform.localpath
lookupreg = platform.lookupreg
makedir = platform.makedir
nlinks = platform.nlinks
normpath = platform.normpath
normcase = platform.normcase
normcasespec = platform.normcasespec
normcasefallback = platform.normcasefallback
openhardlinks = platform.openhardlinks
oslink = platform.oslink
parsepatchoutput = platform.parsepatchoutput
pconvert = platform.pconvert
poll = platform.poll
posixfile = platform.posixfile
readlink = platform.readlink
rename = platform.rename
removedirs = platform.removedirs
samedevice = platform.samedevice
samefile = platform.samefile
samestat = platform.samestat
setflags = platform.setflags
split = platform.split
statfiles = getattr(osutil, 'statfiles', platform.statfiles)
statisexec = platform.statisexec
statislink = platform.statislink
umask = platform.umask
unlink = platform.unlink
username = platform.username


def setumask(val: int) -> None:
    '''updates the umask. used by chg server'''
    if pycompat.iswindows:
        return
    os.umask(val)
    global umask
    platform.umask = umask = val & 0o777


# small compat layer
compengines = compression.compengines
SERVERROLE = compression.SERVERROLE
CLIENTROLE = compression.CLIENTROLE

# Python compatibility

_notset = object()


def bitsfrom(container):
    bits = 0
    for bit in container:
        bits |= bit
    return bits


# python 2.6 still have deprecation warning enabled by default. We do not want
# to display anything to standard user so detect if we are running test and
# only use python deprecation warning in this case.
_dowarn = bool(encoding.environ.get(b'HGEMITWARNINGS'))
if _dowarn:
    # explicitly unfilter our warning for python 2.7
    #
    # The option of setting PYTHONWARNINGS in the test runner was investigated.
    # However, module name set through PYTHONWARNINGS was exactly matched, so
    # we cannot set 'mercurial' and have it match eg: 'mercurial.scmutil'. This
    # makes the whole PYTHONWARNINGS thing useless for our usecase.
    warnings.filterwarnings('default', '', DeprecationWarning, 'mercurial')
    warnings.filterwarnings('default', '', DeprecationWarning, 'hgext')
    warnings.filterwarnings('default', '', DeprecationWarning, 'hgext3rd')
if _dowarn:
    # silence warning emitted by passing user string to re.sub()
    warnings.filterwarnings(
        'ignore', 'bad escape', DeprecationWarning, 'mercurial'
    )
    warnings.filterwarnings(
        'ignore', 'invalid escape sequence', DeprecationWarning, 'mercurial'
    )
    # TODO: reinvent imp.is_frozen()
    warnings.filterwarnings(
        'ignore',
        'the imp module is deprecated',
        DeprecationWarning,
        'mercurial',
    )


def nouideprecwarn(msg, version, stacklevel=1):
    """Issue an python native deprecation warning

    This is a noop outside of tests, use 'ui.deprecwarn' when possible.
    """
    if _dowarn:
        msg += (
            b"\n(compatibility will be dropped after Mercurial-%s,"
            b" update your code.)"
        ) % version
        warnings.warn(pycompat.sysstr(msg), DeprecationWarning, stacklevel + 1)
        # on python 3 with chg, we will need to explicitly flush the output
        sys.stderr.flush()


DIGESTS = {
    b'md5': hashlib.md5,
    b'sha1': hashutil.sha1,
    b'sha512': hashlib.sha512,
}
# List of digest types from strongest to weakest
DIGESTS_BY_STRENGTH = [b'sha512', b'sha1', b'md5']

for k in DIGESTS_BY_STRENGTH:
    assert k in DIGESTS


class digester:
    """helper to compute digests.

    This helper can be used to compute one or more digests given their name.

    >>> d = digester([b'md5', b'sha1'])
    >>> d.update(b'foo')
    >>> [k for k in sorted(d)]
    ['md5', 'sha1']
    >>> d[b'md5']
    'acbd18db4cc2f85cedef654fccc4a4d8'
    >>> d[b'sha1']
    '0beec7b5ea3f0fdbc95d0dd47f3c5bc275da8a33'
    >>> digester.preferred([b'md5', b'sha1'])
    'sha1'
    """

    def __init__(self, digests, s=b''):
        self._hashes = {}
        for k in digests:
            if k not in DIGESTS:
                raise error.Abort(_(b'unknown digest type: %s') % k)
            self._hashes[k] = DIGESTS[k]()
        if s:
            self.update(s)

    def update(self, data):
        for h in self._hashes.values():
            h.update(data)

    def __getitem__(self, key):
        if key not in DIGESTS:
            raise error.Abort(_(b'unknown digest type: %s') % k)
        return hex(self._hashes[key].digest())

    def __iter__(self):
        return iter(self._hashes)

    @staticmethod
    def preferred(supported):
        """returns the strongest digest type in both supported and DIGESTS."""

        for k in DIGESTS_BY_STRENGTH:
            if k in supported:
                return k
        return None


class digestchecker:
    """file handle wrapper that additionally checks content against a given
    size and digests.

        d = digestchecker(fh, size, {'md5': '...'})

    When multiple digests are given, all of them are validated.
    """

    def __init__(self, fh, size, digests):
        self._fh = fh
        self._size = size
        self._got = 0
        self._digests = dict(digests)
        self._digester = digester(self._digests.keys())

    def read(self, length=-1):
        content = self._fh.read(length)
        self._digester.update(content)
        self._got += len(content)
        return content

    def validate(self):
        if self._size != self._got:
            raise error.Abort(
                _(b'size mismatch: expected %d, got %d')
                % (self._size, self._got)
            )
        for k, v in self._digests.items():
            if v != self._digester[k]:
                # i18n: first parameter is a digest name
                raise error.Abort(
                    _(b'%s mismatch: expected %s, got %s')
                    % (k, v, self._digester[k])
                )


try:
    buffer = buffer  # pytype: disable=name-error
except NameError:

    def buffer(sliceable, offset=0, length=None):
        if length is not None:
            return memoryview(sliceable)[offset : offset + length]
        return memoryview(sliceable)[offset:]


_chunksize = 4096


class bufferedinputpipe:
    """a manually buffered input pipe

    Python will not let us use buffered IO and lazy reading with 'polling' at
    the same time. We cannot probe the buffer state and select will not detect
    that data are ready to read if they are already buffered.

    This class let us work around that by implementing its own buffering
    (allowing efficient readline) while offering a way to know if the buffer is
    empty from the output (allowing collaboration of the buffer with polling).

    This class lives in the 'util' module because it makes use of the 'os'
    module from the python stdlib.
    """

    def __new__(cls, fh):
        # If we receive a fileobjectproxy, we need to use a variation of this
        # class that notifies observers about activity.
        if isinstance(fh, fileobjectproxy):
            cls = observedbufferedinputpipe

        return super(bufferedinputpipe, cls).__new__(cls)

    def __init__(self, input):
        self._input = input
        self._buffer = []
        self._eof = False
        self._lenbuf = 0

    @property
    def hasbuffer(self):
        """True is any data is currently buffered

        This will be used externally a pre-step for polling IO. If there is
        already data then no polling should be set in place."""
        return bool(self._buffer)

    @property
    def closed(self):
        return self._input.closed

    def fileno(self):
        return self._input.fileno()

    def close(self):
        return self._input.close()

    def read(self, size):
        while (not self._eof) and (self._lenbuf < size):
            self._fillbuffer()
        return self._frombuffer(size)

    def unbufferedread(self, size):
        if not self._eof and self._lenbuf == 0:
            self._fillbuffer(max(size, _chunksize))
        return self._frombuffer(min(self._lenbuf, size))

    def readline(self, *args, **kwargs):
        if len(self._buffer) > 1:
            # this should not happen because both read and readline end with a
            # _frombuffer call that collapse it.
            self._buffer = [b''.join(self._buffer)]
            self._lenbuf = len(self._buffer[0])
        lfi = -1
        if self._buffer:
            lfi = self._buffer[-1].find(b'\n')
        while (not self._eof) and lfi < 0:
            self._fillbuffer()
            if self._buffer:
                lfi = self._buffer[-1].find(b'\n')
        size = lfi + 1
        if lfi < 0:  # end of file
            size = self._lenbuf
        elif len(self._buffer) > 1:
            # we need to take previous chunks into account
            size += self._lenbuf - len(self._buffer[-1])
        return self._frombuffer(size)

    def _frombuffer(self, size):
        """return at most 'size' data from the buffer

        The data are removed from the buffer."""
        if size == 0 or not self._buffer:
            return b''
        buf = self._buffer[0]
        if len(self._buffer) > 1:
            buf = b''.join(self._buffer)

        data = buf[:size]
        buf = buf[len(data) :]
        if buf:
            self._buffer = [buf]
            self._lenbuf = len(buf)
        else:
            self._buffer = []
            self._lenbuf = 0
        return data

    def _fillbuffer(self, size=_chunksize):
        """read data to the buffer"""
        data = os.read(self._input.fileno(), size)
        if not data:
            self._eof = True
        else:
            self._lenbuf += len(data)
            self._buffer.append(data)

        return data


def mmapread(fp, size=None):
    """Read a file content using mmap

    The responsability of checking the file system is mmap safe is the
    responsability of the caller.

    In some case, a normal string might be returned.
    """
    if size == 0:
        # size of 0 to mmap.mmap() means "all data"
        # rather than "zero bytes", so special case that.
        return b''
    elif size is None:
        size = 0
    fd = getattr(fp, 'fileno', lambda: fp)()
    try:
        return mmap.mmap(fd, size, access=mmap.ACCESS_READ)
    except ValueError:
        # Empty files cannot be mmapped, but mmapread should still work.  Check
        # if the file is empty, and if so, return an empty buffer.
        if os.fstat(fd).st_size == 0:
            return b''
        raise


class fileobjectproxy:
    """A proxy around file objects that tells a watcher when events occur.

    This type is intended to only be used for testing purposes. Think hard
    before using it in important code.
    """

    __slots__ = (
        '_orig',
        '_observer',
    )

    def __init__(self, fh, observer):
        object.__setattr__(self, '_orig', fh)
        object.__setattr__(self, '_observer', observer)

    def __getattribute__(self, name):
        ours = {
            '_observer',
            # IOBase
            'close',
            # closed if a property
            'fileno',
            'flush',
            'isatty',
            'readable',
            'readline',
            'readlines',
            'seek',
            'seekable',
            'tell',
            'truncate',
            'writable',
            'writelines',
            # RawIOBase
            'read',
            'readall',
            'readinto',
            'write',
            # BufferedIOBase
            # raw is a property
            'detach',
            # read defined above
            'read1',
            # readinto defined above
            # write defined above
        }

        # We only observe some methods.
        if name in ours:
            return object.__getattribute__(self, name)

        return getattr(object.__getattribute__(self, '_orig'), name)

    def __nonzero__(self):
        return bool(object.__getattribute__(self, '_orig'))

    __bool__ = __nonzero__

    def __delattr__(self, name):
        return delattr(object.__getattribute__(self, '_orig'), name)

    def __setattr__(self, name, value):
        return setattr(object.__getattribute__(self, '_orig'), name, value)

    def __iter__(self):
        return object.__getattribute__(self, '_orig').__iter__()

    def _observedcall(self, name, *args, **kwargs):
        # Call the original object.
        orig = object.__getattribute__(self, '_orig')
        res = getattr(orig, name)(*args, **kwargs)

        # Call a method on the observer of the same name with arguments
        # so it can react, log, etc.
        observer = object.__getattribute__(self, '_observer')
        fn = getattr(observer, name, None)
        if fn:
            fn(res, *args, **kwargs)

        return res

    def close(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'close', *args, **kwargs
        )

    def fileno(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'fileno', *args, **kwargs
        )

    def flush(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'flush', *args, **kwargs
        )

    def isatty(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'isatty', *args, **kwargs
        )

    def readable(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'readable', *args, **kwargs
        )

    def readline(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'readline', *args, **kwargs
        )

    def readlines(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'readlines', *args, **kwargs
        )

    def seek(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'seek', *args, **kwargs
        )

    def seekable(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'seekable', *args, **kwargs
        )

    def tell(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'tell', *args, **kwargs
        )

    def truncate(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'truncate', *args, **kwargs
        )

    def writable(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'writable', *args, **kwargs
        )

    def writelines(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'writelines', *args, **kwargs
        )

    def read(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'read', *args, **kwargs
        )

    def readall(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'readall', *args, **kwargs
        )

    def readinto(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'readinto', *args, **kwargs
        )

    def write(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'write', *args, **kwargs
        )

    def detach(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'detach', *args, **kwargs
        )

    def read1(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'read1', *args, **kwargs
        )


class observedbufferedinputpipe(bufferedinputpipe):
    """A variation of bufferedinputpipe that is aware of fileobjectproxy.

    ``bufferedinputpipe`` makes low-level calls to ``os.read()`` that
    bypass ``fileobjectproxy``. Because of this, we need to make
    ``bufferedinputpipe`` aware of these operations.

    This variation of ``bufferedinputpipe`` can notify observers about
    ``os.read()`` events. It also re-publishes other events, such as
    ``read()`` and ``readline()``.
    """

    def _fillbuffer(self, size=_chunksize):
        res = super(observedbufferedinputpipe, self)._fillbuffer(size=size)

        fn = getattr(self._input._observer, 'osread', None)
        if fn:
            fn(res, size)

        return res

    # We use different observer methods because the operation isn't
    # performed on the actual file object but on us.
    def read(self, size):
        res = super(observedbufferedinputpipe, self).read(size)

        fn = getattr(self._input._observer, 'bufferedread', None)
        if fn:
            fn(res, size)

        return res

    def readline(self, *args, **kwargs):
        res = super(observedbufferedinputpipe, self).readline(*args, **kwargs)

        fn = getattr(self._input._observer, 'bufferedreadline', None)
        if fn:
            fn(res)

        return res


PROXIED_SOCKET_METHODS = {
    'makefile',
    'recv',
    'recvfrom',
    'recvfrom_into',
    'recv_into',
    'send',
    'sendall',
    'sendto',
    'setblocking',
    'settimeout',
    'gettimeout',
    'setsockopt',
}


class socketproxy:
    """A proxy around a socket that tells a watcher when events occur.

    This is like ``fileobjectproxy`` except for sockets.

    This type is intended to only be used for testing purposes. Think hard
    before using it in important code.
    """

    __slots__ = (
        '_orig',
        '_observer',
    )

    def __init__(self, sock, observer):
        object.__setattr__(self, '_orig', sock)
        object.__setattr__(self, '_observer', observer)

    def __getattribute__(self, name):
        if name in PROXIED_SOCKET_METHODS:
            return object.__getattribute__(self, name)

        return getattr(object.__getattribute__(self, '_orig'), name)

    def __delattr__(self, name):
        return delattr(object.__getattribute__(self, '_orig'), name)

    def __setattr__(self, name, value):
        return setattr(object.__getattribute__(self, '_orig'), name, value)

    def __nonzero__(self):
        return bool(object.__getattribute__(self, '_orig'))

    __bool__ = __nonzero__

    def _observedcall(self, name, *args, **kwargs):
        # Call the original object.
        orig = object.__getattribute__(self, '_orig')
        res = getattr(orig, name)(*args, **kwargs)

        # Call a method on the observer of the same name with arguments
        # so it can react, log, etc.
        observer = object.__getattribute__(self, '_observer')
        fn = getattr(observer, name, None)
        if fn:
            fn(res, *args, **kwargs)

        return res

    def makefile(self, *args, **kwargs):
        res = object.__getattribute__(self, '_observedcall')(
            'makefile', *args, **kwargs
        )

        # The file object may be used for I/O. So we turn it into a
        # proxy using our observer.
        observer = object.__getattribute__(self, '_observer')
        return makeloggingfileobject(
            observer.fh,
            res,
            observer.name,
            reads=observer.reads,
            writes=observer.writes,
            logdata=observer.logdata,
            logdataapis=observer.logdataapis,
        )

    def recv(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'recv', *args, **kwargs
        )

    def recvfrom(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'recvfrom', *args, **kwargs
        )

    def recvfrom_into(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'recvfrom_into', *args, **kwargs
        )

    def recv_into(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'recv_info', *args, **kwargs
        )

    def send(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'send', *args, **kwargs
        )

    def sendall(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'sendall', *args, **kwargs
        )

    def sendto(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'sendto', *args, **kwargs
        )

    def setblocking(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'setblocking', *args, **kwargs
        )

    def settimeout(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'settimeout', *args, **kwargs
        )

    def gettimeout(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'gettimeout', *args, **kwargs
        )

    def setsockopt(self, *args, **kwargs):
        return object.__getattribute__(self, '_observedcall')(
            'setsockopt', *args, **kwargs
        )


class baseproxyobserver:
    def __init__(self, fh, name, logdata, logdataapis):
        self.fh = fh
        self.name = name
        self.logdata = logdata
        self.logdataapis = logdataapis

    def _writedata(self, data):
        if not self.logdata:
            if self.logdataapis:
                self.fh.write(b'\n')
                self.fh.flush()
            return

        # Simple case writes all data on a single line.
        if b'\n' not in data:
            if self.logdataapis:
                self.fh.write(b': %s\n' % stringutil.escapestr(data))
            else:
                self.fh.write(
                    b'%s>     %s\n' % (self.name, stringutil.escapestr(data))
                )
            self.fh.flush()
            return

        # Data with newlines is written to multiple lines.
        if self.logdataapis:
            self.fh.write(b':\n')

        lines = data.splitlines(True)
        for line in lines:
            self.fh.write(
                b'%s>     %s\n' % (self.name, stringutil.escapestr(line))
            )
        self.fh.flush()


class fileobjectobserver(baseproxyobserver):
    """Logs file object activity."""

    def __init__(
        self, fh, name, reads=True, writes=True, logdata=False, logdataapis=True
    ):
        super(fileobjectobserver, self).__init__(fh, name, logdata, logdataapis)
        self.reads = reads
        self.writes = writes

    def read(self, res, size=-1):
        if not self.reads:
            return
        # Python 3 can return None from reads at EOF instead of empty strings.
        if res is None:
            res = b''

        if size == -1 and res == b'':
            # Suppress pointless read(-1) calls that return
            # nothing. These happen _a lot_ on Python 3, and there
            # doesn't seem to be a better workaround to have matching
            # Python 2 and 3 behavior. :(
            return

        if self.logdataapis:
            self.fh.write(b'%s> read(%d) -> %d' % (self.name, size, len(res)))

        self._writedata(res)

    def readline(self, res, limit=-1):
        if not self.reads:
            return

        if self.logdataapis:
            self.fh.write(b'%s> readline() -> %d' % (self.name, len(res)))

        self._writedata(res)

    def readinto(self, res, dest):
        if not self.reads:
            return

        if self.logdataapis:
            self.fh.write(
                b'%s> readinto(%d) -> %r' % (self.name, len(dest), res)
            )

        data = dest[0:res] if res is not None else b''

        # _writedata() uses "in" operator and is confused by memoryview because
        # characters are ints on Python 3.
        if isinstance(data, memoryview):
            data = data.tobytes()

        self._writedata(data)

    def write(self, res, data):
        if not self.writes:
            return

        # Python 2 returns None from some write() calls. Python 3 (reasonably)
        # returns the integer bytes written.
        if res is None and data:
            res = len(data)

        if self.logdataapis:
            self.fh.write(b'%s> write(%d) -> %r' % (self.name, len(data), res))

        self._writedata(data)

    def flush(self, res):
        if not self.writes:
            return

        self.fh.write(b'%s> flush() -> %r\n' % (self.name, res))

    # For observedbufferedinputpipe.
    def bufferedread(self, res, size):
        if not self.reads:
            return

        if self.logdataapis:
            self.fh.write(
                b'%s> bufferedread(%d) -> %d' % (self.name, size, len(res))
            )

        self._writedata(res)

    def bufferedreadline(self, res):
        if not self.reads:
            return

        if self.logdataapis:
            self.fh.write(
                b'%s> bufferedreadline() -> %d' % (self.name, len(res))
            )

        self._writedata(res)


def makeloggingfileobject(
    logh, fh, name, reads=True, writes=True, logdata=False, logdataapis=True
):
    """Turn a file object into a logging file object."""

    observer = fileobjectobserver(
        logh,
        name,
        reads=reads,
        writes=writes,
        logdata=logdata,
        logdataapis=logdataapis,
    )
    return fileobjectproxy(fh, observer)


class socketobserver(baseproxyobserver):
    """Logs socket activity."""

    def __init__(
        self,
        fh,
        name,
        reads=True,
        writes=True,
        states=True,
        logdata=False,
        logdataapis=True,
    ):
        super(socketobserver, self).__init__(fh, name, logdata, logdataapis)
        self.reads = reads
        self.writes = writes
        self.states = states

    def makefile(self, res, mode=None, bufsize=None):
        if not self.states:
            return

        self.fh.write(b'%s> makefile(%r, %r)\n' % (self.name, mode, bufsize))

    def recv(self, res, size, flags=0):
        if not self.reads:
            return

        if self.logdataapis:
            self.fh.write(
                b'%s> recv(%d, %d) -> %d' % (self.name, size, flags, len(res))
            )
        self._writedata(res)

    def recvfrom(self, res, size, flags=0):
        if not self.reads:
            return

        if self.logdataapis:
            self.fh.write(
                b'%s> recvfrom(%d, %d) -> %d'
                % (self.name, size, flags, len(res[0]))
            )

        self._writedata(res[0])

    def recvfrom_into(self, res, buf, size, flags=0):
        if not self.reads:
            return

        if self.logdataapis:
            self.fh.write(
                b'%s> recvfrom_into(%d, %d) -> %d'
                % (self.name, size, flags, res[0])
            )

        self._writedata(buf[0 : res[0]])

    def recv_into(self, res, buf, size=0, flags=0):
        if not self.reads:
            return

        if self.logdataapis:
            self.fh.write(
                b'%s> recv_into(%d, %d) -> %d' % (self.name, size, flags, res)
            )

        self._writedata(buf[0:res])

    def send(self, res, data, flags=0):
        if not self.writes:
            return

        self.fh.write(
            b'%s> send(%d, %d) -> %d' % (self.name, len(data), flags, len(res))
        )
        self._writedata(data)

    def sendall(self, res, data, flags=0):
        if not self.writes:
            return

        if self.logdataapis:
            # Returns None on success. So don't bother reporting return value.
            self.fh.write(
                b'%s> sendall(%d, %d)' % (self.name, len(data), flags)
            )

        self._writedata(data)

    def sendto(self, res, data, flagsoraddress, address=None):
        if not self.writes:
            return

        if address:
            flags = flagsoraddress
        else:
            flags = 0

        if self.logdataapis:
            self.fh.write(
                b'%s> sendto(%d, %d, %r) -> %d'
                % (self.name, len(data), flags, address, res)
            )

        self._writedata(data)

    def setblocking(self, res, flag):
        if not self.states:
            return

        self.fh.write(b'%s> setblocking(%r)\n' % (self.name, flag))

    def settimeout(self, res, value):
        if not self.states:
            return

        self.fh.write(b'%s> settimeout(%r)\n' % (self.name, value))

    def gettimeout(self, res):
        if not self.states:
            return

        self.fh.write(b'%s> gettimeout() -> %f\n' % (self.name, res))

    def setsockopt(self, res, level, optname, value):
        if not self.states:
            return

        self.fh.write(
            b'%s> setsockopt(%r, %r, %r) -> %r\n'
            % (self.name, level, optname, value, res)
        )


def makeloggingsocket(
    logh,
    fh,
    name,
    reads=True,
    writes=True,
    states=True,
    logdata=False,
    logdataapis=True,
):
    """Turn a socket into a logging socket."""

    observer = socketobserver(
        logh,
        name,
        reads=reads,
        writes=writes,
        states=states,
        logdata=logdata,
        logdataapis=logdataapis,
    )
    return socketproxy(fh, observer)


def version():
    """Return version information if available."""
    try:
        from . import __version__

        return __version__.version
    except ImportError:
        return b'unknown'


def versiontuple(v=None, n=4):
    """Parses a Mercurial version string into an N-tuple.

    The version string to be parsed is specified with the ``v`` argument.
    If it isn't defined, the current Mercurial version string will be parsed.

    ``n`` can be 2, 3, or 4. Here is how some version strings map to
    returned values:

    >>> v = b'3.6.1+190-df9b73d2d444'
    >>> versiontuple(v, 2)
    (3, 6)
    >>> versiontuple(v, 3)
    (3, 6, 1)
    >>> versiontuple(v, 4)
    (3, 6, 1, '190-df9b73d2d444')

    >>> versiontuple(b'3.6.1+190-df9b73d2d444+20151118')
    (3, 6, 1, '190-df9b73d2d444+20151118')

    >>> v = b'3.6'
    >>> versiontuple(v, 2)
    (3, 6)
    >>> versiontuple(v, 3)
    (3, 6, None)
    >>> versiontuple(v, 4)
    (3, 6, None, None)

    >>> v = b'3.9-rc'
    >>> versiontuple(v, 2)
    (3, 9)
    >>> versiontuple(v, 3)
    (3, 9, None)
    >>> versiontuple(v, 4)
    (3, 9, None, 'rc')

    >>> v = b'3.9-rc+2-02a8fea4289b'
    >>> versiontuple(v, 2)
    (3, 9)
    >>> versiontuple(v, 3)
    (3, 9, None)
    >>> versiontuple(v, 4)
    (3, 9, None, 'rc+2-02a8fea4289b')

    >>> versiontuple(b'4.6rc0')
    (4, 6, None, 'rc0')
    >>> versiontuple(b'4.6rc0+12-425d55e54f98')
    (4, 6, None, 'rc0+12-425d55e54f98')
    >>> versiontuple(b'.1.2.3')
    (None, None, None, '.1.2.3')
    >>> versiontuple(b'12.34..5')
    (12, 34, None, '..5')
    >>> versiontuple(b'1.2.3.4.5.6')
    (1, 2, 3, '.4.5.6')
    """
    if not v:
        v = version()
    m = remod.match(br'(\d+(?:\.\d+){,2})[+-]?(.*)', v)
    if not m:
        vparts, extra = b'', v
    elif m.group(2):
        vparts, extra = m.groups()
    else:
        vparts, extra = m.group(1), None

    assert vparts is not None  # help pytype

    vints = []
    for i in vparts.split(b'.'):
        try:
            vints.append(int(i))
        except ValueError:
            break
    # (3, 6) -> (3, 6, None)
    while len(vints) < 3:
        vints.append(None)

    if n == 2:
        return (vints[0], vints[1])
    if n == 3:
        return (vints[0], vints[1], vints[2])
    if n == 4:
        return (vints[0], vints[1], vints[2], extra)

    raise error.ProgrammingError(b"invalid version part request: %d" % n)


def cachefunc(func):
    '''cache the result of function calls'''
    # XXX doesn't handle keywords args
    if func.__code__.co_argcount == 0:
        listcache = []

        def f():
            if len(listcache) == 0:
                listcache.append(func())
            return listcache[0]

        return f
    cache = {}
    if func.__code__.co_argcount == 1:
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


class cow:
    """helper class to make copy-on-write easier

    Call preparewrite before doing any writes.
    """

    def preparewrite(self):
        """call this before writes, return self or a copied new object"""
        if getattr(self, '_copied', 0):
            self._copied -= 1
            # Function cow.__init__ expects 1 arg(s), got 2 [wrong-arg-count]
            return self.__class__(self)  # pytype: disable=wrong-arg-count
        return self

    def copy(self):
        """always do a cheap copy"""
        self._copied = getattr(self, '_copied', 0) + 1
        return self


class sortdict(collections.OrderedDict):
    """a simple sorted dictionary

    >>> d1 = sortdict([(b'a', 0), (b'b', 1)])
    >>> d2 = d1.copy()
    >>> list(d2.items())
    [('a', 0), ('b', 1)]
    >>> d2.update([(b'a', 2)])
    >>> list(d2.keys()) # should still be in last-set order
    ['b', 'a']
    >>> d1.insert(1, b'a.5', 0.5)
    >>> list(d1.items())
    [('a', 0), ('a.5', 0.5), ('b', 1)]
    """

    def __setitem__(self, key, value):
        if key in self:
            del self[key]
        super(sortdict, self).__setitem__(key, value)

    if pycompat.ispypy:
        # __setitem__() isn't called as of PyPy 5.8.0
        def update(self, src, **f):
            if isinstance(src, dict):
                src = src.items()
            for k, v in src:
                self[k] = v
            for k in f:
                self[k] = f[k]

    def insert(self, position, key, value):
        for (i, (k, v)) in enumerate(list(self.items())):
            if i == position:
                self[key] = value
            if i >= position:
                del self[k]
                self[k] = v


class cowdict(cow, dict):
    """copy-on-write dict

    Be sure to call d = d.preparewrite() before writing to d.

    >>> a = cowdict()
    >>> a is a.preparewrite()
    True
    >>> b = a.copy()
    >>> b is a
    True
    >>> c = b.copy()
    >>> c is a
    True
    >>> a = a.preparewrite()
    >>> b is a
    False
    >>> a is a.preparewrite()
    True
    >>> c = c.preparewrite()
    >>> b is c
    False
    >>> b is b.preparewrite()
    True
    """


class cowsortdict(cow, sortdict):
    """copy-on-write sortdict

    Be sure to call d = d.preparewrite() before writing to d.
    """


class transactional:  # pytype: disable=ignored-metaclass
    """Base class for making a transactional type into a context manager."""

    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def close(self):
        """Successfully closes the transaction."""

    @abc.abstractmethod
    def release(self):
        """Marks the end of the transaction.

        If the transaction has not been closed, it will be aborted.
        """

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        try:
            if exc_type is None:
                self.close()
        finally:
            self.release()


@contextlib.contextmanager
def acceptintervention(tr=None):
    """A context manager that closes the transaction on InterventionRequired

    If no transaction was provided, this simply runs the body and returns
    """
    if not tr:
        yield
        return
    try:
        yield
        tr.close()
    except error.InterventionRequired:
        tr.close()
        raise
    finally:
        tr.release()


@contextlib.contextmanager
def nullcontextmanager(enter_result=None):
    yield enter_result


class _lrucachenode:
    """A node in a doubly linked list.

    Holds a reference to nodes on either side as well as a key-value
    pair for the dictionary entry.
    """

    __slots__ = ('next', 'prev', 'key', 'value', 'cost')

    def __init__(self):
        self.next = self
        self.prev = self

        self.key = _notset
        self.value = None
        self.cost = 0

    def markempty(self):
        """Mark the node as emptied."""
        self.key = _notset
        self.value = None
        self.cost = 0


class lrucachedict:
    """Dict that caches most recent accesses and sets.

    The dict consists of an actual backing dict - indexed by original
    key - and a doubly linked circular list defining the order of entries in
    the cache.

    The head node is the newest entry in the cache. If the cache is full,
    we recycle head.prev and make it the new head. Cache accesses result in
    the node being moved to before the existing head and being marked as the
    new head node.

    Items in the cache can be inserted with an optional "cost" value. This is
    simply an integer that is specified by the caller. The cache can be queried
    for the total cost of all items presently in the cache.

    The cache can also define a maximum cost. If a cache insertion would
    cause the total cost of the cache to go beyond the maximum cost limit,
    nodes will be evicted to make room for the new code. This can be used
    to e.g. set a max memory limit and associate an estimated bytes size
    cost to each item in the cache. By default, no maximum cost is enforced.
    """

    def __init__(self, max, maxcost=0):
        self._cache = {}

        self._head = _lrucachenode()
        self._size = 1
        self.capacity = max
        self.totalcost = 0
        self.maxcost = maxcost

    def __len__(self):
        return len(self._cache)

    def __contains__(self, k):
        return k in self._cache

    def __iter__(self):
        # We don't have to iterate in cache order, but why not.
        n = self._head
        for i in range(len(self._cache)):
            yield n.key
            n = n.next

    def __getitem__(self, k):
        node = self._cache[k]
        self._movetohead(node)
        return node.value

    def insert(self, k, v, cost=0):
        """Insert a new item in the cache with optional cost value."""
        node = self._cache.get(k)
        # Replace existing value and mark as newest.
        if node is not None:
            self.totalcost -= node.cost
            node.value = v
            node.cost = cost
            self.totalcost += cost
            self._movetohead(node)

            if self.maxcost:
                self._enforcecostlimit()

            return

        if self._size < self.capacity:
            node = self._addcapacity()
        else:
            # Grab the last/oldest item.
            node = self._head.prev

        # At capacity. Kill the old entry.
        if node.key is not _notset:
            self.totalcost -= node.cost
            del self._cache[node.key]

        node.key = k
        node.value = v
        node.cost = cost
        self.totalcost += cost
        self._cache[k] = node
        # And mark it as newest entry. No need to adjust order since it
        # is already self._head.prev.
        self._head = node

        if self.maxcost:
            self._enforcecostlimit()

    def __setitem__(self, k, v):
        self.insert(k, v)

    def __delitem__(self, k):
        self.pop(k)

    def pop(self, k, default=_notset):
        try:
            node = self._cache.pop(k)
        except KeyError:
            if default is _notset:
                raise
            return default

        value = node.value
        self.totalcost -= node.cost
        node.markempty()

        # Temporarily mark as newest item before re-adjusting head to make
        # this node the oldest item.
        self._movetohead(node)
        self._head = node.next

        return value

    # Additional dict methods.

    def get(self, k, default=None):
        try:
            return self.__getitem__(k)
        except KeyError:
            return default

    def peek(self, k, default=_notset):
        """Get the specified item without moving it to the head

        Unlike get(), this doesn't mutate the internal state. But be aware
        that it doesn't mean peek() is thread safe.
        """
        try:
            node = self._cache[k]
            return node.value
        except KeyError:
            if default is _notset:
                raise
            return default

    def clear(self):
        n = self._head
        while n.key is not _notset:
            self.totalcost -= n.cost
            n.markempty()
            n = n.next

        self._cache.clear()

    def copy(self, capacity=None, maxcost=0):
        """Create a new cache as a copy of the current one.

        By default, the new cache has the same capacity as the existing one.
        But, the cache capacity can be changed as part of performing the
        copy.

        Items in the copy have an insertion/access order matching this
        instance.
        """

        capacity = capacity or self.capacity
        maxcost = maxcost or self.maxcost
        result = lrucachedict(capacity, maxcost=maxcost)

        # We copy entries by iterating in oldest-to-newest order so the copy
        # has the correct ordering.

        # Find the first non-empty entry.
        n = self._head.prev
        while n.key is _notset and n is not self._head:
            n = n.prev

        # We could potentially skip the first N items when decreasing capacity.
        # But let's keep it simple unless it is a performance problem.
        for i in range(len(self._cache)):
            result.insert(n.key, n.value, cost=n.cost)
            n = n.prev

        return result

    def popoldest(self):
        """Remove the oldest item from the cache.

        Returns the (key, value) describing the removed cache entry.
        """
        if not self._cache:
            return

        # Walk the linked list backwards starting at tail node until we hit
        # a non-empty node.
        n = self._head.prev

        while n.key is _notset:
            n = n.prev

        key, value = n.key, n.value

        # And remove it from the cache and mark it as empty.
        del self._cache[n.key]
        self.totalcost -= n.cost
        n.markempty()

        return key, value

    def _movetohead(self, node: _lrucachenode):
        """Mark a node as the newest, making it the new head.

        When a node is accessed, it becomes the freshest entry in the LRU
        list, which is denoted by self._head.

        Visually, let's make ``N`` the new head node (* denotes head):

            previous/oldest <-> head <-> next/next newest

            ----<->--- A* ---<->-----
            |                       |
            E <-> D <-> N <-> C <-> B

        To:

            ----<->--- N* ---<->-----
            |                       |
            E <-> D <-> C <-> B <-> A

        This requires the following moves:

           C.next = D  (node.prev.next = node.next)
           D.prev = C  (node.next.prev = node.prev)
           E.next = N  (head.prev.next = node)
           N.prev = E  (node.prev = head.prev)
           N.next = A  (node.next = head)
           A.prev = N  (head.prev = node)
        """
        head = self._head
        # C.next = D
        node.prev.next = node.next
        # D.prev = C
        node.next.prev = node.prev
        # N.prev = E
        node.prev = head.prev
        # N.next = A
        # It is tempting to do just "head" here, however if node is
        # adjacent to head, this will do bad things.
        node.next = head.prev.next
        # E.next = N
        node.next.prev = node
        # A.prev = N
        node.prev.next = node

        self._head = node

    def _addcapacity(self) -> _lrucachenode:
        """Add a node to the circular linked list.

        The new node is inserted before the head node.
        """
        head = self._head
        node = _lrucachenode()
        head.prev.next = node
        node.prev = head.prev
        node.next = head
        head.prev = node
        self._size += 1
        return node

    def _enforcecostlimit(self):
        # This should run after an insertion. It should only be called if total
        # cost limits are being enforced.
        # The most recently inserted node is never evicted.
        if len(self) <= 1 or self.totalcost <= self.maxcost:
            return

        # This is logically equivalent to calling popoldest() until we
        # free up enough cost. We don't do that since popoldest() needs
        # to walk the linked list and doing this in a loop would be
        # quadratic. So we find the first non-empty node and then
        # walk nodes until we free up enough capacity.
        #
        # If we only removed the minimum number of nodes to free enough
        # cost at insert time, chances are high that the next insert would
        # also require pruning. This would effectively constitute quadratic
        # behavior for insert-heavy workloads. To mitigate this, we set a
        # target cost that is a percentage of the max cost. This will tend
        # to free more nodes when the high water mark is reached, which
        # lowers the chances of needing to prune on the subsequent insert.
        targetcost = int(self.maxcost * 0.75)

        n = self._head.prev
        while n.key is _notset:
            n = n.prev

        while len(self) > 1 and self.totalcost > targetcost:
            del self._cache[n.key]
            self.totalcost -= n.cost
            n.markempty()
            n = n.prev


def lrucachefunc(func):
    '''cache most recent results of function calls'''
    cache = {}
    order = collections.deque()
    if func.__code__.co_argcount == 1:

        def f(arg):
            if arg not in cache:
                if len(cache) > 20:
                    del cache[order.popleft()]
                cache[arg] = func(arg)
            else:
                order.remove(arg)
            order.append(arg)
            return cache[arg]

    else:

        def f(*args):
            if args not in cache:
                if len(cache) > 20:
                    del cache[order.popleft()]
                cache[args] = func(*args)
            else:
                order.remove(args)
            order.append(args)
            return cache[args]

    return f


class propertycache:
    def __init__(self, func):
        self.func = func
        self.name = func.__name__

    def __get__(self, obj, type=None):
        result = self.func(obj)
        self.cachevalue(obj, result)
        return result

    def cachevalue(self, obj, value):
        # __dict__ assignment required to bypass __setattr__ (eg: repoview)
        obj.__dict__[self.name] = value


def clearcachedproperty(obj, prop):
    '''clear a cached property value, if one has been set'''
    prop = pycompat.sysstr(prop)
    if prop in obj.__dict__:
        del obj.__dict__[prop]


def increasingchunks(source, min=1024, max=65536):
    """return no less than min bytes per chunk while data remains,
    doubling min after each chunk until it reaches max"""

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
            yield b''.join(buf)
            blen = 0
            buf = []
    if buf:
        yield b''.join(buf)


def always(fn):
    return True


def never(fn):
    return False


def nogc(func=None) -> Any:
    """disable garbage collector

    Python's garbage collector triggers a GC each time a certain number of
    container objects (the number being defined by gc.get_threshold()) are
    allocated even when marked not to be tracked by the collector. Tracking has
    no effect on when GCs are triggered, only on what objects the GC looks
    into. As a workaround, disable GC while building complex (huge)
    containers.

    This garbage collector issue have been fixed in 2.7. But it still affect
    CPython's performance.
    """
    if func is None:
        return _nogc_context()
    else:
        return _nogc_decorator(func)


@contextlib.contextmanager
def _nogc_context():
    gcenabled = gc.isenabled()
    gc.disable()
    try:
        yield
    finally:
        if gcenabled:
            gc.enable()


def _nogc_decorator(func):
    def wrapper(*args, **kwargs):
        with _nogc_context():
            return func(*args, **kwargs)

    return wrapper


if pycompat.ispypy:
    # PyPy runs slower with gc disabled
    nogc = lambda x: x


def pathto(root: bytes, n1: bytes, n2: bytes) -> bytes:
    """return the relative path from one place to another.
    root should use os.sep to separate directories
    n1 should use os.sep to separate directories
    n2 should use "/" to separate directories
    returns an os.sep-separated path.

    If n1 is a relative path, it's assumed it's
    relative to root.
    n2 should always be relative to root.
    """
    if not n1:
        return localpath(n2)
    if os.path.isabs(n1):
        if os.path.splitdrive(root)[0] != os.path.splitdrive(n1)[0]:
            return os.path.join(root, localpath(n2))
        n2 = b'/'.join((pconvert(root), n2))
    a, b = splitpath(n1), n2.split(b'/')
    a.reverse()
    b.reverse()
    while a and b and a[-1] == b[-1]:
        a.pop()
        b.pop()
    b.reverse()
    return pycompat.ossep.join(([b'..'] * len(a)) + b) or b'.'


def checksignature(func, depth=1):
    '''wrap a function with code to check for calling errors'''

    def check(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except TypeError:
            if len(traceback.extract_tb(sys.exc_info()[2])) == depth:
                raise error.SignatureError
            raise

    return check


# a whilelist of known filesystems where hardlink works reliably
_hardlinkfswhitelist = {
    b'apfs',
    b'btrfs',
    b'ext2',
    b'ext3',
    b'ext4',
    b'hfs',
    b'jfs',
    b'NTFS',
    b'reiserfs',
    b'tmpfs',
    b'ufs',
    b'xfs',
    b'zfs',
}


def copyfile(
    src,
    dest,
    hardlink=False,
    copystat=False,
    checkambig=False,
    nb_bytes=None,
    no_hardlink_cb=None,
    check_fs_hardlink=True,
):
    """copy a file, preserving mode and optionally other stat info like
    atime/mtime

    checkambig argument is used with filestat, and is useful only if
    destination file is guarded by any lock (e.g. repo.lock or
    repo.wlock).

    copystat and checkambig should be exclusive.

    nb_bytes: if set only copy the first `nb_bytes` of the source file.
    """
    assert not (copystat and checkambig)
    oldstat = None
    if os.path.lexists(dest):
        if checkambig:
            oldstat = checkambig and filestat.frompath(dest)
        unlink(dest)
    if hardlink and check_fs_hardlink:
        # Hardlinks are problematic on CIFS (issue4546), do not allow hardlinks
        # unless we are confident that dest is on a whitelisted filesystem.
        try:
            fstype = getfstype(os.path.dirname(dest))
        except OSError:
            fstype = None
        if fstype not in _hardlinkfswhitelist:
            if no_hardlink_cb is not None:
                no_hardlink_cb()
            hardlink = False
    if hardlink:
        try:
            oslink(src, dest)
            if nb_bytes is not None:
                m = "the `nb_bytes` argument is incompatible with `hardlink`"
                raise error.ProgrammingError(m)
            return
        except (IOError, OSError) as exc:
            if exc.errno != errno.EEXIST and no_hardlink_cb is not None:
                no_hardlink_cb()
            # fall back to normal copy
    if os.path.islink(src):
        os.symlink(os.readlink(src), dest)
        # copytime is ignored for symlinks, but in general copytime isn't needed
        # for them anyway
        if nb_bytes is not None:
            m = "cannot use `nb_bytes` on a symlink"
            raise error.ProgrammingError(m)
    else:
        try:
            shutil.copyfile(src, dest)
            if copystat:
                # copystat also copies mode
                shutil.copystat(src, dest)
            else:
                shutil.copymode(src, dest)
                if oldstat and oldstat.stat:
                    newstat = filestat.frompath(dest)
                    if newstat.isambig(oldstat):
                        # stat of copied file is ambiguous to original one
                        advanced = (
                            oldstat.stat[stat.ST_MTIME] + 1
                        ) & 0x7FFFFFFF
                        os.utime(dest, (advanced, advanced))
            # We could do something smarter using `copy_file_range` call or similar
            if nb_bytes is not None:
                with open(dest, mode='r+') as f:
                    f.truncate(nb_bytes)
        except shutil.Error as inst:
            raise error.Abort(stringutil.forcebytestr(inst))


def copyfiles(src, dst, hardlink=None, progress=None):
    """Copy a directory tree using hardlinks if possible."""
    num = 0

    def settopic():
        if progress:
            progress.topic = _(b'linking') if hardlink else _(b'copying')

    if os.path.isdir(src):
        if hardlink is None:
            hardlink = (
                os.stat(src).st_dev == os.stat(os.path.dirname(dst)).st_dev
            )
        settopic()
        os.mkdir(dst)
        for name, kind in listdir(src):
            srcname = os.path.join(src, name)
            dstname = os.path.join(dst, name)
            hardlink, n = copyfiles(srcname, dstname, hardlink, progress)
            num += n
    else:
        if hardlink is None:
            hardlink = (
                os.stat(os.path.dirname(src)).st_dev
                == os.stat(os.path.dirname(dst)).st_dev
            )
        settopic()

        if hardlink:
            try:
                oslink(src, dst)
            except (IOError, OSError) as exc:
                if exc.errno != errno.EEXIST:
                    hardlink = False
                # XXX maybe try to relink if the file exist ?
                shutil.copy(src, dst)
        else:
            shutil.copy(src, dst)
        num += 1
        if progress:
            progress.increment()

    return hardlink, num


_winreservednames = {
    b'con',
    b'prn',
    b'aux',
    b'nul',
    b'com1',
    b'com2',
    b'com3',
    b'com4',
    b'com5',
    b'com6',
    b'com7',
    b'com8',
    b'com9',
    b'lpt1',
    b'lpt2',
    b'lpt3',
    b'lpt4',
    b'lpt5',
    b'lpt6',
    b'lpt7',
    b'lpt8',
    b'lpt9',
}
_winreservedchars = b':*?"<>|'


def checkwinfilename(path: bytes) -> Optional[bytes]:
    r"""Check that the base-relative path is a valid filename on Windows.
    Returns None if the path is ok, or a UI string describing the problem.

    >>> checkwinfilename(b"just/a/normal/path")
    >>> checkwinfilename(b"foo/bar/con.xml")
    "filename contains 'con', which is reserved on Windows"
    >>> checkwinfilename(b"foo/con.xml/bar")
    "filename contains 'con', which is reserved on Windows"
    >>> checkwinfilename(b"foo/bar/xml.con")
    >>> checkwinfilename(b"foo/bar/AUX/bla.txt")
    "filename contains 'AUX', which is reserved on Windows"
    >>> checkwinfilename(b"foo/bar/bla:.txt")
    "filename contains ':', which is reserved on Windows"
    >>> checkwinfilename(b"foo/bar/b\07la.txt")
    "filename contains '\\x07', which is invalid on Windows"
    >>> checkwinfilename(b"foo/bar/bla ")
    "filename ends with ' ', which is not allowed on Windows"
    >>> checkwinfilename(b"../bar")
    >>> checkwinfilename(b"foo\\")
    "filename ends with '\\', which is invalid on Windows"
    >>> checkwinfilename(b"foo\\/bar")
    "directory name ends with '\\', which is invalid on Windows"
    """
    if path.endswith(b'\\'):
        return _(b"filename ends with '\\', which is invalid on Windows")
    if b'\\/' in path:
        return _(b"directory name ends with '\\', which is invalid on Windows")
    for n in path.replace(b'\\', b'/').split(b'/'):
        if not n:
            continue
        for c in _filenamebytestr(n):
            if c in _winreservedchars:
                return (
                    _(
                        b"filename contains '%s', which is reserved "
                        b"on Windows"
                    )
                    % c
                )
            if ord(c) <= 31:
                return _(
                    b"filename contains '%s', which is invalid on Windows"
                ) % stringutil.escapestr(c)
        base = n.split(b'.')[0]
        if base and base.lower() in _winreservednames:
            return (
                _(b"filename contains '%s', which is reserved on Windows")
                % base
            )
        t = n[-1:]
        if t in b'. ' and n not in b'..':
            return (
                _(
                    b"filename ends with '%s', which is not allowed "
                    b"on Windows"
                )
                % t
            )


timer = getattr(time, "perf_counter", None)

if pycompat.iswindows:
    checkosfilename = checkwinfilename
    if not timer:
        timer = time.clock  # pytype: disable=module-attr
else:
    # mercurial.windows doesn't have platform.checkosfilename
    checkosfilename = platform.checkosfilename  # pytype: disable=module-attr
    if not timer:
        timer = time.time


def makelock(info, pathname):
    """Create a lock file atomically if possible

    This may leave a stale lock file if symlink isn't supported and signal
    interrupt is enabled.
    """
    try:
        return os.symlink(info, pathname)
    except OSError as why:
        if why.errno == errno.EEXIST:
            raise
    except AttributeError:  # no symlink in os
        pass

    flags = os.O_CREAT | os.O_WRONLY | os.O_EXCL | getattr(os, 'O_BINARY', 0)
    ld = os.open(pathname, flags)
    os.write(ld, info)
    os.close(ld)


def readlock(pathname: bytes) -> bytes:
    try:
        return readlink(pathname)
    except OSError as why:
        if why.errno not in (errno.EINVAL, errno.ENOSYS):
            raise
    except AttributeError:  # no symlink in os
        pass
    with posixfile(pathname, b'rb') as fp:
        return fp.read()


def fstat(fp):
    '''stat file object that may not have fileno method.'''
    try:
        return os.fstat(fp.fileno())
    except AttributeError:
        return os.stat(fp.name)


# File system features


def fscasesensitive(path: bytes) -> bool:
    """
    Return true if the given path is on a case-sensitive filesystem

    Requires a path (like /foo/.hg) ending with a foldable final
    directory component.
    """
    s1 = os.lstat(path)
    d, b = os.path.split(path)
    b2 = b.upper()
    if b == b2:
        b2 = b.lower()
        if b == b2:
            return True  # no evidence against case sensitivity
    p2 = os.path.join(d, b2)
    try:
        s2 = os.lstat(p2)
        if s2 == s1:
            return False
        return True
    except OSError:
        return True


_re2_input = lambda x: x
# google-re2 will need to be tell to not output error on its own
_re2_options = None
try:
    import re2  # pytype: disable=import-error

    _re2 = None
except ImportError:
    _re2 = False


def has_re2():
    """return True is re2 is available, False otherwise"""
    if _re2 is None:
        _re._checkre2()
    return _re2


class _re:
    @staticmethod
    def _checkre2():
        global _re2
        global _re2_input
        global _re2_options
        if _re2 is not None:
            # we already have the answer
            return

        check_pattern = br'\[([^\[]+)\]'
        check_input = b'[ui]'
        try:
            # check if match works, see issue3964
            _re2 = bool(re2.match(check_pattern, check_input))
        except ImportError:
            _re2 = False
        except TypeError:
            # the `pyre-2` project provides a re2 module that accept bytes
            # the `fb-re2` project provides a re2 module that acccept sysstr
            check_pattern = pycompat.sysstr(check_pattern)
            check_input = pycompat.sysstr(check_input)
            _re2 = bool(re2.match(check_pattern, check_input))
            _re2_input = pycompat.sysstr
        try:
            quiet = re2.Options()
            quiet.log_errors = False
            _re2_options = quiet
        except AttributeError:
            pass

    def compile(self, pat, flags=0):
        """Compile a regular expression, using re2 if possible

        For best performance, use only re2-compatible regexp features. The
        only flags from the re module that are re2-compatible are
        IGNORECASE and MULTILINE."""
        if _re2 is None:
            self._checkre2()
        if _re2 and (flags & ~(remod.IGNORECASE | remod.MULTILINE)) == 0:
            if flags & remod.IGNORECASE:
                pat = b'(?i)' + pat
            if flags & remod.MULTILINE:
                pat = b'(?m)' + pat
            try:
                input_regex = _re2_input(pat)
                if _re2_options is not None:
                    compiled = re2.compile(input_regex, options=_re2_options)
                else:
                    compiled = re2.compile(input_regex)
                return compiled
            except re2.error:
                pass
        return remod.compile(pat, flags)

    @propertycache
    def escape(self):
        """Return the version of escape corresponding to self.compile.

        This is imperfect because whether re2 or re is used for a particular
        function depends on the flags, etc, but it's the best we can do.
        """
        global _re2
        if _re2 is None:
            self._checkre2()
        if _re2:
            return re2.escape
        else:
            return remod.escape


re = _re()

_fspathcache = {}


def fspath(name: bytes, root: bytes) -> bytes:
    """Get name in the case stored in the filesystem

    The name should be relative to root, and be normcase-ed for efficiency.

    Note that this function is unnecessary, and should not be
    called, for case-sensitive filesystems (simply because it's expensive).

    The root should be normcase-ed, too.
    """

    def _makefspathcacheentry(dir):
        return {normcase(n): n for n in os.listdir(dir)}

    seps = pycompat.ossep
    if pycompat.osaltsep:
        seps = seps + pycompat.osaltsep
    # Protect backslashes. This gets silly very quickly.
    seps.replace(b'\\', b'\\\\')
    pattern = remod.compile(br'([^%s]+)|([%s]+)' % (seps, seps))
    dir = os.path.normpath(root)
    result = []
    for part, sep in pattern.findall(name):
        if sep:
            result.append(sep)
            continue

        if dir not in _fspathcache:
            _fspathcache[dir] = _makefspathcacheentry(dir)
        contents = _fspathcache[dir]

        found = contents.get(part)
        if not found:
            # retry "once per directory" per "dirstate.walk" which
            # may take place for each patches of "hg qpush", for example
            _fspathcache[dir] = contents = _makefspathcacheentry(dir)
            found = contents.get(part)

        result.append(found or part)
        dir = os.path.join(dir, part)

    return b''.join(result)


def checknlink(testfile: bytes) -> bool:
    '''check whether hardlink count reporting works properly'''

    # testfile may be open, so we need a separate file for checking to
    # work around issue2543 (or testfile may get lost on Samba shares)
    f1, f2, fp = None, None, None
    try:
        fd, f1 = pycompat.mkstemp(
            prefix=b'.%s-' % os.path.basename(testfile),
            suffix=b'1~',
            dir=os.path.dirname(testfile),
        )
        os.close(fd)
        f2 = b'%s2~' % f1[:-2]

        oslink(f1, f2)
        # nlinks() may behave differently for files on Windows shares if
        # the file is open.
        fp = posixfile(f2)
        return nlinks(f2) > 1
    except OSError:
        return False
    finally:
        if fp is not None:
            fp.close()
        for f in (f1, f2):
            try:
                if f is not None:
                    os.unlink(f)
            except OSError:
                pass


def endswithsep(path: bytes) -> bool:
    '''Check path ends with os.sep or os.altsep.'''
    return bool(  # help pytype
        path.endswith(pycompat.ossep)
        or pycompat.osaltsep
        and path.endswith(pycompat.osaltsep)
    )


def splitpath(path: bytes) -> List[bytes]:
    """Split path by os.sep.
    Note that this function does not use os.altsep because this is
    an alternative of simple "xxx.split(os.sep)".
    It is recommended to use os.path.normpath() before using this
    function if need."""
    return path.split(pycompat.ossep)


def mktempcopy(name, emptyok=False, createmode=None, enforcewritable=False):
    """Create a temporary file with the same contents from name

    The permission bits are copied from the original file.

    If the temporary file is going to be truncated immediately, you
    can use emptyok=True as an optimization.

    Returns the name of the temporary file.
    """
    d, fn = os.path.split(name)
    fd, temp = pycompat.mkstemp(prefix=b'.%s-' % fn, suffix=b'~', dir=d)
    os.close(fd)
    # Temporary files are created with mode 0600, which is usually not
    # what we want.  If the original file already exists, just copy
    # its mode.  Otherwise, manually obey umask.
    copymode(name, temp, createmode, enforcewritable)

    if emptyok:
        return temp
    try:
        try:
            ifp = posixfile(name, b"rb")
        except IOError as inst:
            if inst.errno == errno.ENOENT:
                return temp
            if not getattr(inst, 'filename', None):
                inst.filename = name
            raise
        ofp = posixfile(temp, b"wb")
        for chunk in filechunkiter(ifp):
            ofp.write(chunk)
        ifp.close()
        ofp.close()
    except:  # re-raises
        try:
            os.unlink(temp)
        except OSError:
            pass
        raise
    return temp


class filestat:
    """help to exactly detect change of a file

    'stat' attribute is result of 'os.stat()' if specified 'path'
    exists. Otherwise, it is None. This can avoid preparative
    'exists()' examination on client side of this class.
    """

    def __init__(self, stat):
        self.stat = stat

    @classmethod
    def frompath(cls, path):
        try:
            stat = os.stat(path)
        except FileNotFoundError:
            stat = None
        return cls(stat)

    @classmethod
    def fromfp(cls, fp):
        stat = os.fstat(fp.fileno())
        return cls(stat)

    __hash__ = object.__hash__

    def __eq__(self, old):
        try:
            # if ambiguity between stat of new and old file is
            # avoided, comparison of size, ctime and mtime is enough
            # to exactly detect change of a file regardless of platform
            return (
                self.stat.st_size == old.stat.st_size
                and self.stat[stat.ST_CTIME] == old.stat[stat.ST_CTIME]
                and self.stat[stat.ST_MTIME] == old.stat[stat.ST_MTIME]
            )
        except AttributeError:
            pass
        try:
            return self.stat is None and old.stat is None
        except AttributeError:
            return False

    def isambig(self, old):
        """Examine whether new (= self) stat is ambiguous against old one

        "S[N]" below means stat of a file at N-th change:

        - S[n-1].ctime  < S[n].ctime: can detect change of a file
        - S[n-1].ctime == S[n].ctime
          - S[n-1].ctime  < S[n].mtime: means natural advancing (*1)
          - S[n-1].ctime == S[n].mtime: is ambiguous (*2)
          - S[n-1].ctime  > S[n].mtime: never occurs naturally (don't care)
        - S[n-1].ctime  > S[n].ctime: never occurs naturally (don't care)

        Case (*2) above means that a file was changed twice or more at
        same time in sec (= S[n-1].ctime), and comparison of timestamp
        is ambiguous.

        Base idea to avoid such ambiguity is "advance mtime 1 sec, if
        timestamp is ambiguous".

        But advancing mtime only in case (*2) doesn't work as
        expected, because naturally advanced S[n].mtime in case (*1)
        might be equal to manually advanced S[n-1 or earlier].mtime.

        Therefore, all "S[n-1].ctime == S[n].ctime" cases should be
        treated as ambiguous regardless of mtime, to avoid overlooking
        by confliction between such mtime.

        Advancing mtime "if isambig(oldstat)" ensures "S[n-1].mtime !=
        S[n].mtime", even if size of a file isn't changed.
        """
        try:
            return self.stat[stat.ST_CTIME] == old.stat[stat.ST_CTIME]
        except AttributeError:
            return False

    def avoidambig(self, path, old):
        """Change file stat of specified path to avoid ambiguity

        'old' should be previous filestat of 'path'.

        This skips avoiding ambiguity, if a process doesn't have
        appropriate privileges for 'path'. This returns False in this
        case.

        Otherwise, this returns True, as "ambiguity is avoided".
        """
        advanced = (old.stat[stat.ST_MTIME] + 1) & 0x7FFFFFFF
        try:
            os.utime(path, (advanced, advanced))
        except PermissionError:
            # utime() on the file created by another user causes EPERM,
            # if a process doesn't have appropriate privileges
            return False
        return True

    def __ne__(self, other):
        return not self == other


class atomictempfile:
    """writable file object that atomically updates a file

    All writes will go to a temporary copy of the original file. Call
    close() when you are done writing, and atomictempfile will rename
    the temporary copy to the original name, making the changes
    visible. If the object is destroyed without being closed, all your
    writes are discarded.

    checkambig argument of constructor is used with filestat, and is
    useful only if target file is guarded by any lock (e.g. repo.lock
    or repo.wlock).
    """

    def __init__(self, name, mode=b'w+b', createmode=None, checkambig=False):
        self.__name = name  # permanent name
        self._tempname = mktempcopy(
            name,
            emptyok=(b'w' in mode),
            createmode=createmode,
            enforcewritable=(b'w' in mode),
        )

        self._fp = posixfile(self._tempname, mode)
        self._checkambig = checkambig

        # delegated methods
        self.read = self._fp.read
        self.write = self._fp.write
        self.writelines = self._fp.writelines
        self.seek = self._fp.seek
        self.tell = self._fp.tell
        self.fileno = self._fp.fileno

    def close(self):
        if not self._fp.closed:
            self._fp.close()
            filename = localpath(self.__name)
            oldstat = self._checkambig and filestat.frompath(filename)
            if oldstat and oldstat.stat:
                rename(self._tempname, filename)
                newstat = filestat.frompath(filename)
                if newstat.isambig(oldstat):
                    # stat of changed file is ambiguous to original one
                    advanced = (oldstat.stat[stat.ST_MTIME] + 1) & 0x7FFFFFFF
                    os.utime(filename, (advanced, advanced))
            else:
                rename(self._tempname, filename)

    def discard(self):
        if not self._fp.closed:
            try:
                os.unlink(self._tempname)
            except OSError:
                pass
            self._fp.close()

    def __del__(self):
        if hasattr(self, '_fp'):  # constructor actually did something
            self.discard()

    def __enter__(self):
        return self

    def __exit__(self, exctype, excvalue, traceback):
        if exctype is not None:
            self.discard()
        else:
            self.close()


def tryrmdir(f):
    try:
        removedirs(f)
    except OSError as e:
        if e.errno != errno.ENOENT and e.errno != errno.ENOTEMPTY:
            raise


def unlinkpath(
    f: bytes, ignoremissing: bool = False, rmdir: bool = True
) -> None:
    """unlink and remove the directory if it is empty"""
    if ignoremissing:
        tryunlink(f)
    else:
        unlink(f)
    if rmdir:
        # try removing directories that might now be empty
        try:
            removedirs(os.path.dirname(f))
        except OSError:
            pass


def tryunlink(f: bytes) -> bool:
    """Attempt to remove a file, ignoring FileNotFoundError.

    Returns False in case the file did not exit, True otherwise
    """
    try:
        unlink(f)
        return True
    except FileNotFoundError:
        return False


def makedirs(
    name: bytes, mode: Optional[int] = None, notindexed: bool = False
) -> None:
    """recursive directory creation with parent mode inheritance

    Newly created directories are marked as "not to be indexed by
    the content indexing service", if ``notindexed`` is specified
    for "write" mode access.
    """
    try:
        makedir(name, notindexed)
    except OSError as err:
        if err.errno == errno.EEXIST:
            return
        if err.errno != errno.ENOENT or not name:
            raise
        parent = os.path.dirname(abspath(name))
        if parent == name:
            raise
        makedirs(parent, mode, notindexed)
        try:
            makedir(name, notindexed)
        except OSError as err:
            # Catch EEXIST to handle races
            if err.errno == errno.EEXIST:
                return
            raise
    if mode is not None:
        os.chmod(name, mode)


def readfile(path: bytes) -> bytes:
    with open(path, b'rb') as fp:
        return fp.read()


def writefile(path: bytes, text: bytes) -> None:
    with open(path, b'wb') as fp:
        fp.write(text)


def appendfile(path: bytes, text: bytes) -> None:
    with open(path, b'ab') as fp:
        fp.write(text)


class chunkbuffer:
    """Allow arbitrary sized chunks of data to be efficiently read from an
    iterator over chunks of arbitrary size."""

    def __init__(self, in_iter):
        """in_iter is the iterator that's iterating over the input chunks."""

        def splitbig(chunks):
            for chunk in chunks:
                if len(chunk) > 2 ** 20:
                    pos = 0
                    while pos < len(chunk):
                        end = pos + 2 ** 18
                        yield chunk[pos:end]
                        pos = end
                else:
                    yield chunk

        self.iter = splitbig(in_iter)
        self._queue = collections.deque()
        self._chunkoffset = 0

    def read(self, l=None):
        """Read L bytes of data from the iterator of chunks of data.
        Returns less than L bytes if the iterator runs dry.

        If size parameter is omitted, read everything"""
        if l is None:
            return b''.join(self.iter)

        left = l
        buf = []
        queue = self._queue
        while left > 0:
            # refill the queue
            if not queue:
                target = 2 ** 18
                for chunk in self.iter:
                    queue.append(chunk)
                    target -= len(chunk)
                    if target <= 0:
                        break
                if not queue:
                    break

            # The easy way to do this would be to queue.popleft(), modify the
            # chunk (if necessary), then queue.appendleft(). However, for cases
            # where we read partial chunk content, this incurs 2 dequeue
            # mutations and creates a new str for the remaining chunk in the
            # queue. Our code below avoids this overhead.

            chunk = queue[0]
            chunkl = len(chunk)
            offset = self._chunkoffset

            # Use full chunk.
            if offset == 0 and left >= chunkl:
                left -= chunkl
                queue.popleft()
                buf.append(chunk)
                # self._chunkoffset remains at 0.
                continue

            chunkremaining = chunkl - offset

            # Use all of unconsumed part of chunk.
            if left >= chunkremaining:
                left -= chunkremaining
                queue.popleft()
                # offset == 0 is enabled by block above, so this won't merely
                # copy via ``chunk[0:]``.
                buf.append(chunk[offset:])
                self._chunkoffset = 0

            # Partial chunk needed.
            else:
                buf.append(chunk[offset : offset + left])
                self._chunkoffset += left
                left -= chunkremaining

        return b''.join(buf)


def filechunkiter(f, size=131072, limit=None):
    """Create a generator that produces the data in the file size
    (default 131072) bytes at a time, up to optional limit (default is
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


class cappedreader:
    """A file object proxy that allows reading up to N bytes.

    Given a source file object, instances of this type allow reading up to
    N bytes from that source file object. Attempts to read past the allowed
    limit are treated as EOF.

    It is assumed that I/O is not performed on the original file object
    in addition to I/O that is performed by this instance. If there is,
    state tracking will get out of sync and unexpected results will ensue.
    """

    def __init__(self, fh, limit):
        """Allow reading up to <limit> bytes from <fh>."""
        self._fh = fh
        self._left = limit

    def read(self, n=-1):
        if not self._left:
            return b''

        if n < 0:
            n = self._left

        data = self._fh.read(min(n, self._left))
        self._left -= len(data)
        assert self._left >= 0

        return data

    def readinto(self, b):
        res = self.read(len(b))
        if res is None:
            return None

        b[0 : len(res)] = res
        return len(res)


def unitcountfn(*unittable):
    '''return a function that renders a readable count of some quantity'''

    def go(count):
        for multiplier, divisor, format in unittable:
            if abs(count) >= divisor * multiplier:
                return format % (count / float(divisor))
        return unittable[-1][2] % count

    return go


def processlinerange(fromline: int, toline: int) -> Tuple[int, int]:
    """Check that linerange <fromline>:<toline> makes sense and return a
    0-based range.

    >>> processlinerange(10, 20)
    (9, 20)
    >>> processlinerange(2, 1)
    Traceback (most recent call last):
        ...
    ParseError: line range must be positive
    >>> processlinerange(0, 5)
    Traceback (most recent call last):
        ...
    ParseError: fromline must be strictly positive
    """
    if toline - fromline < 0:
        raise error.ParseError(_(b"line range must be positive"))
    if fromline < 1:
        raise error.ParseError(_(b"fromline must be strictly positive"))
    return fromline - 1, toline


bytecount = unitcountfn(
    (100, 1 << 30, _(b'%.0f GB')),
    (10, 1 << 30, _(b'%.1f GB')),
    (1, 1 << 30, _(b'%.2f GB')),
    (100, 1 << 20, _(b'%.0f MB')),
    (10, 1 << 20, _(b'%.1f MB')),
    (1, 1 << 20, _(b'%.2f MB')),
    (100, 1 << 10, _(b'%.0f KB')),
    (10, 1 << 10, _(b'%.1f KB')),
    (1, 1 << 10, _(b'%.2f KB')),
    (1, 1, _(b'%.0f bytes')),
)


class transformingwriter:
    """Writable file wrapper to transform data by function"""

    def __init__(self, fp, encode):
        self._fp = fp
        self._encode = encode

    def close(self):
        self._fp.close()

    def flush(self):
        self._fp.flush()

    def write(self, data):
        return self._fp.write(self._encode(data))


# Matches a single EOL which can either be a CRLF where repeated CR
# are removed or a LF. We do not care about old Macintosh files, so a
# stray CR is an error.
_eolre = remod.compile(br'\r*\n')


def tolf(s: bytes) -> bytes:
    return _eolre.sub(b'\n', s)


def tocrlf(s: bytes) -> bytes:
    return _eolre.sub(b'\r\n', s)


def _crlfwriter(fp):
    return transformingwriter(fp, tocrlf)


if pycompat.oslinesep == b'\r\n':
    tonativeeol = tocrlf
    fromnativeeol = tolf
    nativeeolwriter = _crlfwriter
else:
    tonativeeol = pycompat.identity
    fromnativeeol = pycompat.identity
    nativeeolwriter = pycompat.identity


# TODO delete since workaround variant for Python 2 no longer needed.
def iterfile(fp):
    return fp


def iterlines(iterator: Iterable[bytes]) -> Iterator[bytes]:
    for chunk in iterator:
        for line in chunk.splitlines():
            yield line


def expandpath(path: bytes) -> bytes:
    return os.path.expanduser(os.path.expandvars(path))


def interpolate(prefix, mapping, s, fn=None, escape_prefix=False):
    """Return the result of interpolating items in the mapping into string s.

    prefix is a single character string, or a two character string with
    a backslash as the first character if the prefix needs to be escaped in
    a regular expression.

    fn is an optional function that will be applied to the replacement text
    just before replacement.

    escape_prefix is an optional flag that allows using doubled prefix for
    its escaping.
    """
    fn = fn or (lambda s: s)
    patterns = b'|'.join(mapping.keys())
    if escape_prefix:
        patterns += b'|' + prefix
        if len(prefix) > 1:
            prefix_char = prefix[1:]
        else:
            prefix_char = prefix
        mapping[prefix_char] = prefix_char
    r = remod.compile(br'%s(%s)' % (prefix, patterns))
    return r.sub(lambda x: fn(mapping[x.group()[1:]]), s)


timecount = unitcountfn(
    (1, 1e3, _(b'%.0f s')),
    (100, 1, _(b'%.1f s')),
    (10, 1, _(b'%.2f s')),
    (1, 1, _(b'%.3f s')),
    (100, 0.001, _(b'%.1f ms')),
    (10, 0.001, _(b'%.2f ms')),
    (1, 0.001, _(b'%.3f ms')),
    (100, 0.000001, _(b'%.1f us')),
    (10, 0.000001, _(b'%.2f us')),
    (1, 0.000001, _(b'%.3f us')),
    (100, 0.000000001, _(b'%.1f ns')),
    (10, 0.000000001, _(b'%.2f ns')),
    (1, 0.000000001, _(b'%.3f ns')),
)


@attr.s
class timedcmstats:
    """Stats information produced by the timedcm context manager on entering."""

    # the starting value of the timer as a float (meaning and resulution is
    # platform dependent, see util.timer)
    start = attr.ib(default=attr.Factory(lambda: timer()))
    # the number of seconds as a floating point value; starts at 0, updated when
    # the context is exited.
    elapsed = attr.ib(default=0)
    # the number of nested timedcm context managers.
    level = attr.ib(default=1)

    def __bytes__(self):
        return timecount(self.elapsed) if self.elapsed else b'<unknown>'

    __str__ = encoding.strmethod(__bytes__)


@contextlib.contextmanager
def timedcm(whencefmt, *whenceargs):
    """A context manager that produces timing information for a given context.

    On entering a timedcmstats instance is produced.

    This context manager is reentrant.

    """
    # track nested context managers
    timedcm._nested += 1
    timing_stats = timedcmstats(level=timedcm._nested)
    try:
        with tracing.log(whencefmt, *whenceargs):
            yield timing_stats
    finally:
        timing_stats.elapsed = timer() - timing_stats.start
        timedcm._nested -= 1


timedcm._nested = 0


def timed(func):
    """Report the execution time of a function call to stderr.

    During development, use as a decorator when you need to measure
    the cost of a function, e.g. as follows:

    @util.timed
    def foo(a, b, c):
        pass
    """

    def wrapper(*args, **kwargs):
        with timedcm(pycompat.bytestr(func.__name__)) as time_stats:
            result = func(*args, **kwargs)
        stderr = procutil.stderr
        stderr.write(
            b'%s%s: %s\n'
            % (
                b' ' * time_stats.level * 2,
                pycompat.bytestr(func.__name__),
                time_stats,
            )
        )
        return result

    return wrapper


_sizeunits = (
    (b'm', 2 ** 20),
    (b'k', 2 ** 10),
    (b'g', 2 ** 30),
    (b'kb', 2 ** 10),
    (b'mb', 2 ** 20),
    (b'gb', 2 ** 30),
    (b'b', 1),
)


def sizetoint(s: bytes) -> int:
    """Convert a space specifier to a byte count.

    >>> sizetoint(b'30')
    30
    >>> sizetoint(b'2.2kb')
    2252
    >>> sizetoint(b'6M')
    6291456
    """
    t = s.strip().lower()
    try:
        for k, u in _sizeunits:
            if t.endswith(k):
                return int(float(t[: -len(k)]) * u)
        return int(t)
    except ValueError:
        raise error.ParseError(_(b"couldn't parse size: %s") % s)


class hooks:
    """A collection of hook functions that can be used to extend a
    function's behavior. Hooks are called in lexicographic order,
    based on the names of their sources."""

    def __init__(self):
        self._hooks = []

    def add(self, source, hook):
        self._hooks.append((source, hook))

    def __call__(self, *args):
        self._hooks.sort(key=lambda x: x[0])
        results = []
        for source, hook in self._hooks:
            results.append(hook(*args))
        return results


def getstackframes(skip=0, line=b' %-*s in %s\n', fileline=b'%s:%d', depth=0):
    """Yields lines for a nicely formatted stacktrace.
    Skips the 'skip' last entries, then return the last 'depth' entries.
    Each file+linenumber is formatted according to fileline.
    Each line is formatted according to line.
    If line is None, it yields:
      length of longest filepath+line number,
      filepath+linenumber,
      function

    Not be used in production code but very convenient while developing.
    """
    entries = [
        (fileline % (pycompat.sysbytes(fn), ln), pycompat.sysbytes(func))
        for fn, ln, func, _text in traceback.extract_stack()[: -skip - 1]
    ][-depth:]
    if entries:
        fnmax = max(len(entry[0]) for entry in entries)
        for fnln, func in entries:
            if line is None:
                yield (fnmax, fnln, func)
            else:
                yield line % (fnmax, fnln, func)


def debugstacktrace(
    msg=b'stacktrace',
    skip=0,
    f=procutil.stderr,
    otherf=procutil.stdout,
    depth=0,
    prefix=b'',
):
    """Writes a message to f (stderr) with a nicely formatted stacktrace.
    Skips the 'skip' entries closest to the call, then show 'depth' entries.
    By default it will flush stdout first.
    It can be used everywhere and intentionally does not require an ui object.
    Not be used in production code but very convenient while developing.
    """
    if otherf:
        otherf.flush()
    f.write(b'%s%s at:\n' % (prefix, msg.rstrip()))
    for line in getstackframes(skip + 1, depth=depth):
        f.write(prefix + line)
    f.flush()


# convenient shortcut
dst = debugstacktrace


def safename(f, tag, ctx, others=None):
    """
    Generate a name that it is safe to rename f to in the given context.

    f:      filename to rename
    tag:    a string tag that will be included in the new name
    ctx:    a context, in which the new name must not exist
    others: a set of other filenames that the new name must not be in

    Returns a file name of the form oldname~tag[~number] which does not exist
    in the provided context and is not in the set of other names.
    """
    if others is None:
        others = set()

    fn = b'%s~%s' % (f, tag)
    if fn not in ctx and fn not in others:
        return fn
    for n in itertools.count(1):
        fn = b'%s~%s~%s' % (f, tag, n)
        if fn not in ctx and fn not in others:
            return fn


def readexactly(stream, n):
    '''read n bytes from stream.read and abort if less was available'''
    s = stream.read(n)
    if len(s) < n:
        raise error.Abort(
            _(b"stream ended unexpectedly (got %d bytes, expected %d)")
            % (len(s), n)
        )
    return s


def uvarintencode(value):
    """Encode an unsigned integer value to a varint.

    A varint is a variable length integer of 1 or more bytes. Each byte
    except the last has the most significant bit set. The lower 7 bits of
    each byte store the 2's complement representation, least significant group
    first.

    >>> uvarintencode(0)
    '\\x00'
    >>> uvarintencode(1)
    '\\x01'
    >>> uvarintencode(127)
    '\\x7f'
    >>> uvarintencode(1337)
    '\\xb9\\n'
    >>> uvarintencode(65536)
    '\\x80\\x80\\x04'
    >>> uvarintencode(-1)
    Traceback (most recent call last):
        ...
    ProgrammingError: negative value for uvarint: -1
    """
    if value < 0:
        raise error.ProgrammingError(b'negative value for uvarint: %d' % value)
    bits = value & 0x7F
    value >>= 7
    bytes = []
    while value:
        bytes.append(pycompat.bytechr(0x80 | bits))
        bits = value & 0x7F
        value >>= 7
    bytes.append(pycompat.bytechr(bits))

    return b''.join(bytes)


def uvarintdecodestream(fh):
    """Decode an unsigned variable length integer from a stream.

    The passed argument is anything that has a ``.read(N)`` method.

    >>> from io import BytesIO
    >>> uvarintdecodestream(BytesIO(b'\\x00'))
    0
    >>> uvarintdecodestream(BytesIO(b'\\x01'))
    1
    >>> uvarintdecodestream(BytesIO(b'\\x7f'))
    127
    >>> uvarintdecodestream(BytesIO(b'\\xb9\\n'))
    1337
    >>> uvarintdecodestream(BytesIO(b'\\x80\\x80\\x04'))
    65536
    >>> uvarintdecodestream(BytesIO(b'\\x80'))
    Traceback (most recent call last):
        ...
    Abort: stream ended unexpectedly (got 0 bytes, expected 1)
    """
    result = 0
    shift = 0
    while True:
        byte = ord(readexactly(fh, 1))
        result |= (byte & 0x7F) << shift
        if not (byte & 0x80):
            return result
        shift += 7


# Passing the '' locale means that the locale should be set according to the
# user settings (environment variables).
# Python sometimes avoids setting the global locale settings. When interfacing
# with C code (e.g. the curses module or the Subversion bindings), the global
# locale settings must be initialized correctly. Python 2 does not initialize
# the global locale settings on interpreter startup. Python 3 sometimes
# initializes LC_CTYPE, but not consistently at least on Windows. Therefore we
# explicitly initialize it to get consistent behavior if it's not already
# initialized. Since CPython commit 177d921c8c03d30daa32994362023f777624b10d,
# LC_CTYPE is always initialized. If we require Python 3.8+, we should re-check
# if we can remove this code.
@contextlib.contextmanager
def with_lc_ctype():
    oldloc = locale.setlocale(locale.LC_CTYPE, None)
    if oldloc == 'C':
        try:
            try:
                locale.setlocale(locale.LC_CTYPE, '')
            except locale.Error:
                # The likely case is that the locale from the environment
                # variables is unknown.
                pass
            yield
        finally:
            locale.setlocale(locale.LC_CTYPE, oldloc)
    else:
        yield


def _estimatememory() -> Optional[int]:
    """Provide an estimate for the available system memory in Bytes.

    If no estimate can be provided on the platform, returns None.
    """
    if pycompat.sysplatform.startswith(b'win'):
        # On Windows, use the GlobalMemoryStatusEx kernel function directly.
        from ctypes import c_long as DWORD, c_ulonglong as DWORDLONG
        from ctypes.wintypes import (  # pytype: disable=import-error
            Structure,
            byref,
            sizeof,
            windll,
        )

        class MEMORYSTATUSEX(Structure):
            _fields_ = [
                ('dwLength', DWORD),
                ('dwMemoryLoad', DWORD),
                ('ullTotalPhys', DWORDLONG),
                ('ullAvailPhys', DWORDLONG),
                ('ullTotalPageFile', DWORDLONG),
                ('ullAvailPageFile', DWORDLONG),
                ('ullTotalVirtual', DWORDLONG),
                ('ullAvailVirtual', DWORDLONG),
                ('ullExtendedVirtual', DWORDLONG),
            ]

        x = MEMORYSTATUSEX()
        x.dwLength = sizeof(x)
        windll.kernel32.GlobalMemoryStatusEx(byref(x))
        return x.ullAvailPhys

    # On newer Unix-like systems and Mac OSX, the sysconf interface
    # can be used. _SC_PAGE_SIZE is part of POSIX; _SC_PHYS_PAGES
    # seems to be implemented on most systems.
    try:
        pagesize = os.sysconf(os.sysconf_names['SC_PAGE_SIZE'])
        pages = os.sysconf(os.sysconf_names['SC_PHYS_PAGES'])
        return pagesize * pages
    except OSError:  # sysconf can fail
        pass
    except KeyError:  # unknown parameter
        pass
