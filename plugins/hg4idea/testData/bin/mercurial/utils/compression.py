# compression.py - Mercurial utility functions for compression
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from __future__ import absolute_import, print_function

import bz2
import collections
import zlib

from ..pycompat import getattr
from .. import (
    error,
    i18n,
    pycompat,
)
from . import stringutil

safehasattr = pycompat.safehasattr


_ = i18n._

# compression code

SERVERROLE = b'server'
CLIENTROLE = b'client'

compewireprotosupport = collections.namedtuple(
    'compenginewireprotosupport',
    ('name', 'serverpriority', 'clientpriority'),
)


class propertycache(object):
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


class compressormanager(object):
    """Holds registrations of various compression engines.

    This class essentially abstracts the differences between compression
    engines to allow new compression formats to be added easily, possibly from
    extensions.

    Compressors are registered against the global instance by calling its
    ``register()`` method.
    """

    def __init__(self):
        self._engines = {}
        # Bundle spec human name to engine name.
        self._bundlenames = {}
        # Internal bundle identifier to engine name.
        self._bundletypes = {}
        # Revlog header to engine name.
        self._revlogheaders = {}
        # Wire proto identifier to engine name.
        self._wiretypes = {}

    def __getitem__(self, key):
        return self._engines[key]

    def __contains__(self, key):
        return key in self._engines

    def __iter__(self):
        return iter(self._engines.keys())

    def register(self, engine):
        """Register a compression engine with the manager.

        The argument must be a ``compressionengine`` instance.
        """
        if not isinstance(engine, compressionengine):
            raise ValueError(_(b'argument must be a compressionengine'))

        name = engine.name()

        if name in self._engines:
            raise error.Abort(
                _(b'compression engine %s already registered') % name
            )

        bundleinfo = engine.bundletype()
        if bundleinfo:
            bundlename, bundletype = bundleinfo

            if bundlename in self._bundlenames:
                raise error.Abort(
                    _(b'bundle name %s already registered') % bundlename
                )
            if bundletype in self._bundletypes:
                raise error.Abort(
                    _(b'bundle type %s already registered by %s')
                    % (bundletype, self._bundletypes[bundletype])
                )

            # No external facing name declared.
            if bundlename:
                self._bundlenames[bundlename] = name

            self._bundletypes[bundletype] = name

        wiresupport = engine.wireprotosupport()
        if wiresupport:
            wiretype = wiresupport.name
            if wiretype in self._wiretypes:
                raise error.Abort(
                    _(
                        b'wire protocol compression %s already '
                        b'registered by %s'
                    )
                    % (wiretype, self._wiretypes[wiretype])
                )

            self._wiretypes[wiretype] = name

        revlogheader = engine.revlogheader()
        if revlogheader and revlogheader in self._revlogheaders:
            raise error.Abort(
                _(b'revlog header %s already registered by %s')
                % (revlogheader, self._revlogheaders[revlogheader])
            )

        if revlogheader:
            self._revlogheaders[revlogheader] = name

        self._engines[name] = engine

    @property
    def supportedbundlenames(self):
        return set(self._bundlenames.keys())

    @property
    def supportedbundletypes(self):
        return set(self._bundletypes.keys())

    def forbundlename(self, bundlename):
        """Obtain a compression engine registered to a bundle name.

        Will raise KeyError if the bundle type isn't registered.

        Will abort if the engine is known but not available.
        """
        engine = self._engines[self._bundlenames[bundlename]]
        if not engine.available():
            raise error.Abort(
                _(b'compression engine %s could not be loaded') % engine.name()
            )
        return engine

    def forbundletype(self, bundletype):
        """Obtain a compression engine registered to a bundle type.

        Will raise KeyError if the bundle type isn't registered.

        Will abort if the engine is known but not available.
        """
        engine = self._engines[self._bundletypes[bundletype]]
        if not engine.available():
            raise error.Abort(
                _(b'compression engine %s could not be loaded') % engine.name()
            )
        return engine

    def supportedwireengines(self, role, onlyavailable=True):
        """Obtain compression engines that support the wire protocol.

        Returns a list of engines in prioritized order, most desired first.

        If ``onlyavailable`` is set, filter out engines that can't be
        loaded.
        """
        assert role in (SERVERROLE, CLIENTROLE)

        attr = b'serverpriority' if role == SERVERROLE else b'clientpriority'

        engines = [self._engines[e] for e in self._wiretypes.values()]
        if onlyavailable:
            engines = [e for e in engines if e.available()]

        def getkey(e):
            # Sort first by priority, highest first. In case of tie, sort
            # alphabetically. This is arbitrary, but ensures output is
            # stable.
            w = e.wireprotosupport()
            return -1 * getattr(w, attr), w.name

        return list(sorted(engines, key=getkey))

    def forwiretype(self, wiretype):
        engine = self._engines[self._wiretypes[wiretype]]
        if not engine.available():
            raise error.Abort(
                _(b'compression engine %s could not be loaded') % engine.name()
            )
        return engine

    def forrevlogheader(self, header):
        """Obtain a compression engine registered to a revlog header.

        Will raise KeyError if the revlog header value isn't registered.
        """
        return self._engines[self._revlogheaders[header]]


compengines = compressormanager()


class compressionengine(object):
    """Base class for compression engines.

    Compression engines must implement the interface defined by this class.
    """

    def name(self):
        """Returns the name of the compression engine.

        This is the key the engine is registered under.

        This method must be implemented.
        """
        raise NotImplementedError()

    def available(self):
        """Whether the compression engine is available.

        The intent of this method is to allow optional compression engines
        that may not be available in all installations (such as engines relying
        on C extensions that may not be present).
        """
        return True

    def bundletype(self):
        """Describes bundle identifiers for this engine.

        If this compression engine isn't supported for bundles, returns None.

        If this engine can be used for bundles, returns a 2-tuple of strings of
        the user-facing "bundle spec" compression name and an internal
        identifier used to denote the compression format within bundles. To
        exclude the name from external usage, set the first element to ``None``.

        If bundle compression is supported, the class must also implement
        ``compressstream`` and `decompressorreader``.

        The docstring of this method is used in the help system to tell users
        about this engine.
        """
        return None

    def wireprotosupport(self):
        """Declare support for this compression format on the wire protocol.

        If this compression engine isn't supported for compressing wire
        protocol payloads, returns None.

        Otherwise, returns ``compenginewireprotosupport`` with the following
        fields:

        * String format identifier
        * Integer priority for the server
        * Integer priority for the client

        The integer priorities are used to order the advertisement of format
        support by server and client. The highest integer is advertised
        first. Integers with non-positive values aren't advertised.

        The priority values are somewhat arbitrary and only used for default
        ordering. The relative order can be changed via config options.

        If wire protocol compression is supported, the class must also implement
        ``compressstream`` and ``decompressorreader``.
        """
        return None

    def revlogheader(self):
        """Header added to revlog chunks that identifies this engine.

        If this engine can be used to compress revlogs, this method should
        return the bytes used to identify chunks compressed with this engine.
        Else, the method should return ``None`` to indicate it does not
        participate in revlog compression.
        """
        return None

    def compressstream(self, it, opts=None):
        """Compress an iterator of chunks.

        The method receives an iterator (ideally a generator) of chunks of
        bytes to be compressed. It returns an iterator (ideally a generator)
        of bytes of chunks representing the compressed output.

        Optionally accepts an argument defining how to perform compression.
        Each engine treats this argument differently.
        """
        raise NotImplementedError()

    def decompressorreader(self, fh):
        """Perform decompression on a file object.

        Argument is an object with a ``read(size)`` method that returns
        compressed data. Return value is an object with a ``read(size)`` that
        returns uncompressed data.
        """
        raise NotImplementedError()

    def revlogcompressor(self, opts=None):
        """Obtain an object that can be used to compress revlog entries.

        The object has a ``compress(data)`` method that compresses binary
        data. This method returns compressed binary data or ``None`` if
        the data could not be compressed (too small, not compressible, etc).
        The returned data should have a header uniquely identifying this
        compression format so decompression can be routed to this engine.
        This header should be identified by the ``revlogheader()`` return
        value.

        The object has a ``decompress(data)`` method that decompresses
        data. The method will only be called if ``data`` begins with
        ``revlogheader()``. The method should return the raw, uncompressed
        data or raise a ``StorageError``.

        The object is reusable but is not thread safe.
        """
        raise NotImplementedError()


class _CompressedStreamReader(object):
    def __init__(self, fh):
        if safehasattr(fh, 'unbufferedread'):
            self._reader = fh.unbufferedread
        else:
            self._reader = fh.read
        self._pending = []
        self._pos = 0
        self._eof = False

    def _decompress(self, chunk):
        raise NotImplementedError()

    def read(self, l):
        buf = []
        while True:
            while self._pending:
                if len(self._pending[0]) > l + self._pos:
                    newbuf = self._pending[0]
                    buf.append(newbuf[self._pos : self._pos + l])
                    self._pos += l
                    return b''.join(buf)

                newbuf = self._pending.pop(0)
                if self._pos:
                    buf.append(newbuf[self._pos :])
                    l -= len(newbuf) - self._pos
                else:
                    buf.append(newbuf)
                    l -= len(newbuf)
                self._pos = 0

            if self._eof:
                return b''.join(buf)
            chunk = self._reader(65536)
            self._decompress(chunk)
            if not chunk and not self._pending and not self._eof:
                # No progress and no new data, bail out
                return b''.join(buf)


class _GzipCompressedStreamReader(_CompressedStreamReader):
    def __init__(self, fh):
        super(_GzipCompressedStreamReader, self).__init__(fh)
        self._decompobj = zlib.decompressobj()

    def _decompress(self, chunk):
        newbuf = self._decompobj.decompress(chunk)
        if newbuf:
            self._pending.append(newbuf)
        d = self._decompobj.copy()
        try:
            d.decompress(b'x')
            d.flush()
            if d.unused_data == b'x':
                self._eof = True
        except zlib.error:
            pass


class _BZ2CompressedStreamReader(_CompressedStreamReader):
    def __init__(self, fh):
        super(_BZ2CompressedStreamReader, self).__init__(fh)
        self._decompobj = bz2.BZ2Decompressor()

    def _decompress(self, chunk):
        newbuf = self._decompobj.decompress(chunk)
        if newbuf:
            self._pending.append(newbuf)
        try:
            while True:
                newbuf = self._decompobj.decompress(b'')
                if newbuf:
                    self._pending.append(newbuf)
                else:
                    break
        except EOFError:
            self._eof = True


class _TruncatedBZ2CompressedStreamReader(_BZ2CompressedStreamReader):
    def __init__(self, fh):
        super(_TruncatedBZ2CompressedStreamReader, self).__init__(fh)
        newbuf = self._decompobj.decompress(b'BZ')
        if newbuf:
            self._pending.append(newbuf)


class _ZstdCompressedStreamReader(_CompressedStreamReader):
    def __init__(self, fh, zstd):
        super(_ZstdCompressedStreamReader, self).__init__(fh)
        self._zstd = zstd
        self._decompobj = zstd.ZstdDecompressor().decompressobj()

    def _decompress(self, chunk):
        newbuf = self._decompobj.decompress(chunk)
        if newbuf:
            self._pending.append(newbuf)
        try:
            while True:
                newbuf = self._decompobj.decompress(b'')
                if newbuf:
                    self._pending.append(newbuf)
                else:
                    break
        except self._zstd.ZstdError:
            self._eof = True


class _zlibengine(compressionengine):
    def name(self):
        return b'zlib'

    def bundletype(self):
        """zlib compression using the DEFLATE algorithm.

        All Mercurial clients should support this format. The compression
        algorithm strikes a reasonable balance between compression ratio
        and size.
        """
        return b'gzip', b'GZ'

    def wireprotosupport(self):
        return compewireprotosupport(b'zlib', 20, 20)

    def revlogheader(self):
        return b'x'

    def compressstream(self, it, opts=None):
        opts = opts or {}

        z = zlib.compressobj(opts.get(b'level', -1))
        for chunk in it:
            data = z.compress(chunk)
            # Not all calls to compress emit data. It is cheaper to inspect
            # here than to feed empty chunks through generator.
            if data:
                yield data

        yield z.flush()

    def decompressorreader(self, fh):
        return _GzipCompressedStreamReader(fh)

    class zlibrevlogcompressor(object):
        def __init__(self, level=None):
            self._level = level

        def compress(self, data):
            insize = len(data)
            # Caller handles empty input case.
            assert insize > 0

            if insize < 44:
                return None

            elif insize <= 1000000:
                if self._level is None:
                    compressed = zlib.compress(data)
                else:
                    compressed = zlib.compress(data, self._level)
                if len(compressed) < insize:
                    return compressed
                return None

            # zlib makes an internal copy of the input buffer, doubling
            # memory usage for large inputs. So do streaming compression
            # on large inputs.
            else:
                if self._level is None:
                    z = zlib.compressobj()
                else:
                    z = zlib.compressobj(level=self._level)
                parts = []
                pos = 0
                while pos < insize:
                    pos2 = pos + 2 ** 20
                    parts.append(z.compress(data[pos:pos2]))
                    pos = pos2
                parts.append(z.flush())

                if sum(map(len, parts)) < insize:
                    return b''.join(parts)
                return None

        def decompress(self, data):
            try:
                return zlib.decompress(data)
            except zlib.error as e:
                raise error.StorageError(
                    _(b'revlog decompress error: %s')
                    % stringutil.forcebytestr(e)
                )

    def revlogcompressor(self, opts=None):
        level = None
        if opts is not None:
            level = opts.get(b'zlib.level')
        return self.zlibrevlogcompressor(level)


compengines.register(_zlibengine())


class _bz2engine(compressionengine):
    def name(self):
        return b'bz2'

    def bundletype(self):
        """An algorithm that produces smaller bundles than ``gzip``.

        All Mercurial clients should support this format.

        This engine will likely produce smaller bundles than ``gzip`` but
        will be significantly slower, both during compression and
        decompression.

        If available, the ``zstd`` engine can yield similar or better
        compression at much higher speeds.
        """
        return b'bzip2', b'BZ'

    # We declare a protocol name but don't advertise by default because
    # it is slow.
    def wireprotosupport(self):
        return compewireprotosupport(b'bzip2', 0, 0)

    def compressstream(self, it, opts=None):
        opts = opts or {}
        z = bz2.BZ2Compressor(opts.get(b'level', 9))
        for chunk in it:
            data = z.compress(chunk)
            if data:
                yield data

        yield z.flush()

    def decompressorreader(self, fh):
        return _BZ2CompressedStreamReader(fh)


compengines.register(_bz2engine())


class _truncatedbz2engine(compressionengine):
    def name(self):
        return b'bz2truncated'

    def bundletype(self):
        return None, b'_truncatedBZ'

    # We don't implement compressstream because it is hackily handled elsewhere.

    def decompressorreader(self, fh):
        return _TruncatedBZ2CompressedStreamReader(fh)


compengines.register(_truncatedbz2engine())


class _noopengine(compressionengine):
    def name(self):
        return b'none'

    def bundletype(self):
        """No compression is performed.

        Use this compression engine to explicitly disable compression.
        """
        return b'none', b'UN'

    # Clients always support uncompressed payloads. Servers don't because
    # unless you are on a fast network, uncompressed payloads can easily
    # saturate your network pipe.
    def wireprotosupport(self):
        return compewireprotosupport(b'none', 0, 10)

    # revlog special cases the uncompressed case, but implementing
    # revlogheader allows forcing uncompressed storage.
    def revlogheader(self):
        return b'\0'

    def compressstream(self, it, opts=None):
        return it

    def decompressorreader(self, fh):
        return fh

    class nooprevlogcompressor(object):
        def compress(self, data):
            return None

    def revlogcompressor(self, opts=None):
        return self.nooprevlogcompressor()


compengines.register(_noopengine())


class _zstdengine(compressionengine):
    def name(self):
        return b'zstd'

    @propertycache
    def _module(self):
        # Not all installs have the zstd module available. So defer importing
        # until first access.
        try:
            from .. import zstd  # pytype: disable=import-error

            # Force delayed import.
            zstd.__version__
            return zstd
        except ImportError:
            return None

    def available(self):
        return bool(self._module)

    def bundletype(self):
        """A modern compression algorithm that is fast and highly flexible.

        Only supported by Mercurial 4.1 and newer clients.

        With the default settings, zstd compression is both faster and yields
        better compression than ``gzip``. It also frequently yields better
        compression than ``bzip2`` while operating at much higher speeds.

        If this engine is available and backwards compatibility is not a
        concern, it is likely the best available engine.
        """
        return b'zstd', b'ZS'

    def wireprotosupport(self):
        return compewireprotosupport(b'zstd', 50, 50)

    def revlogheader(self):
        return b'\x28'

    def compressstream(self, it, opts=None):
        opts = opts or {}
        # zstd level 3 is almost always significantly faster than zlib
        # while providing no worse compression. It strikes a good balance
        # between speed and compression.
        level = opts.get(b'level', 3)
        # default to single-threaded compression
        threads = opts.get(b'threads', 0)

        zstd = self._module
        z = zstd.ZstdCompressor(level=level, threads=threads).compressobj()
        for chunk in it:
            data = z.compress(chunk)
            if data:
                yield data

        yield z.flush()

    def decompressorreader(self, fh):
        return _ZstdCompressedStreamReader(fh, self._module)

    class zstdrevlogcompressor(object):
        def __init__(self, zstd, level=3):
            # TODO consider omitting frame magic to save 4 bytes.
            # This writes content sizes into the frame header. That is
            # extra storage. But it allows a correct size memory allocation
            # to hold the result.
            self._cctx = zstd.ZstdCompressor(level=level)
            self._dctx = zstd.ZstdDecompressor()
            self._compinsize = zstd.COMPRESSION_RECOMMENDED_INPUT_SIZE
            self._decompinsize = zstd.DECOMPRESSION_RECOMMENDED_INPUT_SIZE

        def compress(self, data):
            insize = len(data)
            # Caller handles empty input case.
            assert insize > 0

            if insize < 50:
                return None

            elif insize <= 1000000:
                compressed = self._cctx.compress(data)
                if len(compressed) < insize:
                    return compressed
                return None
            else:
                z = self._cctx.compressobj()
                chunks = []
                pos = 0
                while pos < insize:
                    pos2 = pos + self._compinsize
                    chunk = z.compress(data[pos:pos2])
                    if chunk:
                        chunks.append(chunk)
                    pos = pos2
                chunks.append(z.flush())

                if sum(map(len, chunks)) < insize:
                    return b''.join(chunks)
                return None

        def decompress(self, data):
            insize = len(data)

            try:
                # This was measured to be faster than other streaming
                # decompressors.
                dobj = self._dctx.decompressobj()
                chunks = []
                pos = 0
                while pos < insize:
                    pos2 = pos + self._decompinsize
                    chunk = dobj.decompress(data[pos:pos2])
                    if chunk:
                        chunks.append(chunk)
                    pos = pos2
                # Frame should be exhausted, so no finish() API.

                return b''.join(chunks)
            except Exception as e:
                raise error.StorageError(
                    _(b'revlog decompress error: %s')
                    % stringutil.forcebytestr(e)
                )

    def revlogcompressor(self, opts=None):
        opts = opts or {}
        level = opts.get(b'zstd.level')
        if level is None:
            level = opts.get(b'level')
        if level is None:
            level = 3
        return self.zstdrevlogcompressor(self._module, level=level)


compengines.register(_zstdengine())


def bundlecompressiontopics():
    """Obtains a list of available bundle compressions for use in help."""
    # help.makeitemsdocs() expects a dict of names to items with a .__doc__.
    items = {}

    # We need to format the docstring. So use a dummy object/type to hold it
    # rather than mutating the original.
    class docobject(object):
        pass

    for name in compengines:
        engine = compengines[name]

        if not engine.available():
            continue

        bt = engine.bundletype()
        if not bt or not bt[0]:
            continue

        doc = b'``%s``\n    %s' % (bt[0], pycompat.getdoc(engine.bundletype))

        value = docobject()
        value.__doc__ = pycompat.sysstr(doc)
        value._origdoc = engine.bundletype.__doc__
        value._origfunc = engine.bundletype

        items[bt[0]] = value

    return items


i18nfunctions = bundlecompressiontopics().values()
