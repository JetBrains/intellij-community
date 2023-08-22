# cborutil.py - CBOR extensions
#
# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import struct
import sys

from .. import pycompat

# Very short very of RFC 7049...
#
# Each item begins with a byte. The 3 high bits of that byte denote the
# "major type." The lower 5 bits denote the "subtype." Each major type
# has its own encoding mechanism.
#
# Most types have lengths. However, bytestring, string, array, and map
# can be indefinite length. These are denotes by a subtype with value 31.
# Sub-components of those types then come afterwards and are terminated
# by a "break" byte.

MAJOR_TYPE_UINT = 0
MAJOR_TYPE_NEGINT = 1
MAJOR_TYPE_BYTESTRING = 2
MAJOR_TYPE_STRING = 3
MAJOR_TYPE_ARRAY = 4
MAJOR_TYPE_MAP = 5
MAJOR_TYPE_SEMANTIC = 6
MAJOR_TYPE_SPECIAL = 7

SUBTYPE_MASK = 0b00011111

SUBTYPE_FALSE = 20
SUBTYPE_TRUE = 21
SUBTYPE_NULL = 22
SUBTYPE_HALF_FLOAT = 25
SUBTYPE_SINGLE_FLOAT = 26
SUBTYPE_DOUBLE_FLOAT = 27
SUBTYPE_INDEFINITE = 31

SEMANTIC_TAG_FINITE_SET = 258

# Indefinite types begin with their major type ORd with information value 31.
BEGIN_INDEFINITE_BYTESTRING = struct.pack(
    '>B', MAJOR_TYPE_BYTESTRING << 5 | SUBTYPE_INDEFINITE
)
BEGIN_INDEFINITE_ARRAY = struct.pack(
    '>B', MAJOR_TYPE_ARRAY << 5 | SUBTYPE_INDEFINITE
)
BEGIN_INDEFINITE_MAP = struct.pack(
    '>B', MAJOR_TYPE_MAP << 5 | SUBTYPE_INDEFINITE
)

ENCODED_LENGTH_1 = struct.Struct('>B')
ENCODED_LENGTH_2 = struct.Struct('>BB')
ENCODED_LENGTH_3 = struct.Struct('>BH')
ENCODED_LENGTH_4 = struct.Struct('>BL')
ENCODED_LENGTH_5 = struct.Struct('>BQ')

# The break ends an indefinite length item.
BREAK = b'\xff'
BREAK_INT = 255


def encodelength(majortype, length):
    """Obtain a value encoding the major type and its length."""
    if length < 24:
        return ENCODED_LENGTH_1.pack(majortype << 5 | length)
    elif length < 256:
        return ENCODED_LENGTH_2.pack(majortype << 5 | 24, length)
    elif length < 65536:
        return ENCODED_LENGTH_3.pack(majortype << 5 | 25, length)
    elif length < 4294967296:
        return ENCODED_LENGTH_4.pack(majortype << 5 | 26, length)
    else:
        return ENCODED_LENGTH_5.pack(majortype << 5 | 27, length)


def streamencodebytestring(v):
    yield encodelength(MAJOR_TYPE_BYTESTRING, len(v))
    yield v


def streamencodebytestringfromiter(it):
    """Convert an iterator of chunks to an indefinite bytestring.

    Given an input that is iterable and each element in the iterator is
    representable as bytes, emit an indefinite length bytestring.
    """
    yield BEGIN_INDEFINITE_BYTESTRING

    for chunk in it:
        yield encodelength(MAJOR_TYPE_BYTESTRING, len(chunk))
        yield chunk

    yield BREAK


def streamencodeindefinitebytestring(source, chunksize=65536):
    """Given a large source buffer, emit as an indefinite length bytestring.

    This is a generator of chunks constituting the encoded CBOR data.
    """
    yield BEGIN_INDEFINITE_BYTESTRING

    i = 0
    l = len(source)

    while True:
        chunk = source[i : i + chunksize]
        i += len(chunk)

        yield encodelength(MAJOR_TYPE_BYTESTRING, len(chunk))
        yield chunk

        if i >= l:
            break

    yield BREAK


def streamencodeint(v):
    if v >= 18446744073709551616 or v < -18446744073709551616:
        raise ValueError(b'big integers not supported')

    if v >= 0:
        yield encodelength(MAJOR_TYPE_UINT, v)
    else:
        yield encodelength(MAJOR_TYPE_NEGINT, abs(v) - 1)


def streamencodearray(l):
    """Encode a known size iterable to an array."""

    yield encodelength(MAJOR_TYPE_ARRAY, len(l))

    for i in l:
        for chunk in streamencode(i):
            yield chunk


def streamencodearrayfromiter(it):
    """Encode an iterator of items to an indefinite length array."""

    yield BEGIN_INDEFINITE_ARRAY

    for i in it:
        for chunk in streamencode(i):
            yield chunk

    yield BREAK


def _mixedtypesortkey(v):
    return type(v).__name__, v


def streamencodeset(s):
    # https://www.iana.org/assignments/cbor-tags/cbor-tags.xhtml defines
    # semantic tag 258 for finite sets.
    yield encodelength(MAJOR_TYPE_SEMANTIC, SEMANTIC_TAG_FINITE_SET)

    for chunk in streamencodearray(sorted(s, key=_mixedtypesortkey)):
        yield chunk


def streamencodemap(d):
    """Encode dictionary to a generator.

    Does not supporting indefinite length dictionaries.
    """
    yield encodelength(MAJOR_TYPE_MAP, len(d))

    for key, value in sorted(
        pycompat.iteritems(d), key=lambda x: _mixedtypesortkey(x[0])
    ):
        for chunk in streamencode(key):
            yield chunk
        for chunk in streamencode(value):
            yield chunk


def streamencodemapfromiter(it):
    """Given an iterable of (key, value), encode to an indefinite length map."""
    yield BEGIN_INDEFINITE_MAP

    for key, value in it:
        for chunk in streamencode(key):
            yield chunk
        for chunk in streamencode(value):
            yield chunk

    yield BREAK


def streamencodebool(b):
    # major type 7, simple value 20 and 21.
    yield b'\xf5' if b else b'\xf4'


def streamencodenone(v):
    # major type 7, simple value 22.
    yield b'\xf6'


STREAM_ENCODERS = {
    bytes: streamencodebytestring,
    int: streamencodeint,
    pycompat.long: streamencodeint,
    list: streamencodearray,
    tuple: streamencodearray,
    dict: streamencodemap,
    set: streamencodeset,
    bool: streamencodebool,
    type(None): streamencodenone,
}


def streamencode(v):
    """Encode a value in a streaming manner.

    Given an input object, encode it to CBOR recursively.

    Returns a generator of CBOR encoded bytes. There is no guarantee
    that each emitted chunk fully decodes to a value or sub-value.

    Encoding is deterministic - unordered collections are sorted.
    """
    fn = STREAM_ENCODERS.get(v.__class__)

    if not fn:
        # handle subtypes such as encoding.localstr and util.sortdict
        for ty in STREAM_ENCODERS:
            if not isinstance(v, ty):
                continue
            fn = STREAM_ENCODERS[ty]
            break

    if not fn:
        raise ValueError(b'do not know how to encode %s' % type(v))

    return fn(v)


class CBORDecodeError(Exception):
    """Represents an error decoding CBOR."""


if sys.version_info.major >= 3:

    def _elementtointeger(b, i):
        return b[i]


else:

    def _elementtointeger(b, i):
        return ord(b[i])


STRUCT_BIG_UBYTE = struct.Struct('>B')
STRUCT_BIG_USHORT = struct.Struct(b'>H')
STRUCT_BIG_ULONG = struct.Struct(b'>L')
STRUCT_BIG_ULONGLONG = struct.Struct(b'>Q')

SPECIAL_NONE = 0
SPECIAL_START_INDEFINITE_BYTESTRING = 1
SPECIAL_START_ARRAY = 2
SPECIAL_START_MAP = 3
SPECIAL_START_SET = 4
SPECIAL_INDEFINITE_BREAK = 5


def decodeitem(b, offset=0):
    """Decode a new CBOR value from a buffer at offset.

    This function attempts to decode up to one complete CBOR value
    from ``b`` starting at offset ``offset``.

    The beginning of a collection (such as an array, map, set, or
    indefinite length bytestring) counts as a single value. For these
    special cases, a state flag will indicate that a special value was seen.

    When called, the function either returns a decoded value or gives
    a hint as to how many more bytes are needed to do so. By calling
    the function repeatedly given a stream of bytes, the caller can
    build up the original values.

    Returns a tuple with the following elements:

    * Bool indicating whether a complete value was decoded.
    * A decoded value if first value is True otherwise None
    * Integer number of bytes. If positive, the number of bytes
      read. If negative, the number of bytes we need to read to
      decode this value or the next chunk in this value.
    * One of the ``SPECIAL_*`` constants indicating special treatment
      for this value. ``SPECIAL_NONE`` means this is a fully decoded
      simple value (such as an integer or bool).
    """

    initial = _elementtointeger(b, offset)
    offset += 1

    majortype = initial >> 5
    subtype = initial & SUBTYPE_MASK

    if majortype == MAJOR_TYPE_UINT:
        complete, value, readcount = decodeuint(subtype, b, offset)

        if complete:
            return True, value, readcount + 1, SPECIAL_NONE
        else:
            return False, None, readcount, SPECIAL_NONE

    elif majortype == MAJOR_TYPE_NEGINT:
        # Negative integers are the same as UINT except inverted minus 1.
        complete, value, readcount = decodeuint(subtype, b, offset)

        if complete:
            return True, -value - 1, readcount + 1, SPECIAL_NONE
        else:
            return False, None, readcount, SPECIAL_NONE

    elif majortype == MAJOR_TYPE_BYTESTRING:
        # Beginning of bytestrings are treated as uints in order to
        # decode their length, which may be indefinite.
        complete, size, readcount = decodeuint(
            subtype, b, offset, allowindefinite=True
        )

        # We don't know the size of the bytestring. It must be a definitive
        # length since the indefinite subtype would be encoded in the initial
        # byte.
        if not complete:
            return False, None, readcount, SPECIAL_NONE

        # We know the length of the bytestring.
        if size is not None:
            # And the data is available in the buffer.
            if offset + readcount + size <= len(b):
                value = b[offset + readcount : offset + readcount + size]
                return True, value, readcount + size + 1, SPECIAL_NONE

            # And we need more data in order to return the bytestring.
            else:
                wanted = len(b) - offset - readcount - size
                return False, None, wanted, SPECIAL_NONE

        # It is an indefinite length bytestring.
        else:
            return True, None, 1, SPECIAL_START_INDEFINITE_BYTESTRING

    elif majortype == MAJOR_TYPE_STRING:
        raise CBORDecodeError(b'string major type not supported')

    elif majortype == MAJOR_TYPE_ARRAY:
        # Beginning of arrays are treated as uints in order to decode their
        # length. We don't allow indefinite length arrays.
        complete, size, readcount = decodeuint(subtype, b, offset)

        if complete:
            return True, size, readcount + 1, SPECIAL_START_ARRAY
        else:
            return False, None, readcount, SPECIAL_NONE

    elif majortype == MAJOR_TYPE_MAP:
        # Beginning of maps are treated as uints in order to decode their
        # number of elements. We don't allow indefinite length arrays.
        complete, size, readcount = decodeuint(subtype, b, offset)

        if complete:
            return True, size, readcount + 1, SPECIAL_START_MAP
        else:
            return False, None, readcount, SPECIAL_NONE

    elif majortype == MAJOR_TYPE_SEMANTIC:
        # Semantic tag value is read the same as a uint.
        complete, tagvalue, readcount = decodeuint(subtype, b, offset)

        if not complete:
            return False, None, readcount, SPECIAL_NONE

        # This behavior here is a little wonky. The main type being "decorated"
        # by this semantic tag follows. A more robust parser would probably emit
        # a special flag indicating this as a semantic tag and let the caller
        # deal with the types that follow. But since we don't support many
        # semantic tags, it is easier to deal with the special cases here and
        # hide complexity from the caller. If we add support for more semantic
        # tags, we should probably move semantic tag handling into the caller.
        if tagvalue == SEMANTIC_TAG_FINITE_SET:
            if offset + readcount >= len(b):
                return False, None, -1, SPECIAL_NONE

            complete, size, readcount2, special = decodeitem(
                b, offset + readcount
            )

            if not complete:
                return False, None, readcount2, SPECIAL_NONE

            if special != SPECIAL_START_ARRAY:
                raise CBORDecodeError(
                    b'expected array after finite set semantic tag'
                )

            return True, size, readcount + readcount2 + 1, SPECIAL_START_SET

        else:
            raise CBORDecodeError(b'semantic tag %d not allowed' % tagvalue)

    elif majortype == MAJOR_TYPE_SPECIAL:
        # Only specific values for the information field are allowed.
        if subtype == SUBTYPE_FALSE:
            return True, False, 1, SPECIAL_NONE
        elif subtype == SUBTYPE_TRUE:
            return True, True, 1, SPECIAL_NONE
        elif subtype == SUBTYPE_NULL:
            return True, None, 1, SPECIAL_NONE
        elif subtype == SUBTYPE_INDEFINITE:
            return True, None, 1, SPECIAL_INDEFINITE_BREAK
        # If value is 24, subtype is in next byte.
        else:
            raise CBORDecodeError(b'special type %d not allowed' % subtype)
    else:
        assert False


def decodeuint(subtype, b, offset=0, allowindefinite=False):
    """Decode an unsigned integer.

    ``subtype`` is the lower 5 bits from the initial byte CBOR item
    "header." ``b`` is a buffer containing bytes. ``offset`` points to
    the index of the first byte after the byte that ``subtype`` was
    derived from.

    ``allowindefinite`` allows the special indefinite length value
    indicator.

    Returns a 3-tuple of (successful, value, count).

    The first element is a bool indicating if decoding completed. The 2nd
    is the decoded integer value or None if not fully decoded or the subtype
    is 31 and ``allowindefinite`` is True. The 3rd value is the count of bytes.
    If positive, it is the number of additional bytes decoded. If negative,
    it is the number of additional bytes needed to decode this value.
    """

    # Small values are inline.
    if subtype < 24:
        return True, subtype, 0
    # Indefinite length specifier.
    elif subtype == 31:
        if allowindefinite:
            return True, None, 0
        else:
            raise CBORDecodeError(b'indefinite length uint not allowed here')
    elif subtype >= 28:
        raise CBORDecodeError(
            b'unsupported subtype on integer type: %d' % subtype
        )

    if subtype == 24:
        s = STRUCT_BIG_UBYTE
    elif subtype == 25:
        s = STRUCT_BIG_USHORT
    elif subtype == 26:
        s = STRUCT_BIG_ULONG
    elif subtype == 27:
        s = STRUCT_BIG_ULONGLONG
    else:
        raise CBORDecodeError(b'bounds condition checking violation')

    if len(b) - offset >= s.size:
        return True, s.unpack_from(b, offset)[0], s.size
    else:
        return False, None, len(b) - offset - s.size


class bytestringchunk(bytes):
    """Represents a chunk/segment in an indefinite length bytestring.

    This behaves like a ``bytes`` but in addition has the ``isfirst``
    and ``islast`` attributes indicating whether this chunk is the first
    or last in an indefinite length bytestring.
    """

    def __new__(cls, v, first=False, last=False):
        self = bytes.__new__(cls, v)
        self.isfirst = first
        self.islast = last

        return self


class sansiodecoder(object):
    """A CBOR decoder that doesn't perform its own I/O.

    To use, construct an instance and feed it segments containing
    CBOR-encoded bytes via ``decode()``. The return value from ``decode()``
    indicates whether a fully-decoded value is available, how many bytes
    were consumed, and offers a hint as to how many bytes should be fed
    in next time to decode the next value.

    The decoder assumes it will decode N discrete CBOR values, not just
    a single value. i.e. if the bytestream contains uints packed one after
    the other, the decoder will decode them all, rather than just the initial
    one.

    When ``decode()`` indicates a value is available, call ``getavailable()``
    to return all fully decoded values.

    ``decode()`` can partially decode input. It is up to the caller to keep
    track of what data was consumed and to pass unconsumed data in on the
    next invocation.

    The decoder decodes atomically at the *item* level. See ``decodeitem()``.
    If an *item* cannot be fully decoded, the decoder won't record it as
    partially consumed. Instead, the caller will be instructed to pass in
    the initial bytes of this item on the next invocation. This does result
    in some redundant parsing. But the overhead should be minimal.

    This decoder only supports a subset of CBOR as required by Mercurial.
    It lacks support for:

    * Indefinite length arrays
    * Indefinite length maps
    * Use of indefinite length bytestrings as keys or values within
      arrays, maps, or sets.
    * Nested arrays, maps, or sets within sets
    * Any semantic tag that isn't a mathematical finite set
    * Floating point numbers
    * Undefined special value

    CBOR types are decoded to Python types as follows:

    uint -> int
    negint -> int
    bytestring -> bytes
    map -> dict
    array -> list
    True -> bool
    False -> bool
    null -> None
    indefinite length bytestring chunk -> [bytestringchunk]

    The only non-obvious mapping here is an indefinite length bytestring
    to the ``bytestringchunk`` type. This is to facilitate streaming
    indefinite length bytestrings out of the decoder and to differentiate
    a regular bytestring from an indefinite length bytestring.
    """

    _STATE_NONE = 0
    _STATE_WANT_MAP_KEY = 1
    _STATE_WANT_MAP_VALUE = 2
    _STATE_WANT_ARRAY_VALUE = 3
    _STATE_WANT_SET_VALUE = 4
    _STATE_WANT_BYTESTRING_CHUNK_FIRST = 5
    _STATE_WANT_BYTESTRING_CHUNK_SUBSEQUENT = 6

    def __init__(self):
        # TODO add support for limiting size of bytestrings
        # TODO add support for limiting number of keys / values in collections
        # TODO add support for limiting size of buffered partial values

        self.decodedbytecount = 0

        self._state = self._STATE_NONE

        # Stack of active nested collections. Each entry is a dict describing
        # the collection.
        self._collectionstack = []

        # Fully decoded key to use for the current map.
        self._currentmapkey = None

        # Fully decoded values available for retrieval.
        self._decodedvalues = []

    @property
    def inprogress(self):
        """Whether the decoder has partially decoded a value."""
        return self._state != self._STATE_NONE

    def decode(self, b, offset=0):
        """Attempt to decode bytes from an input buffer.

        ``b`` is a collection of bytes and ``offset`` is the byte
        offset within that buffer from which to begin reading data.

        ``b`` must support ``len()`` and accessing bytes slices via
        ``__slice__``. Typically ``bytes`` instances are used.

        Returns a tuple with the following fields:

        * Bool indicating whether values are available for retrieval.
        * Integer indicating the number of bytes that were fully consumed,
          starting from ``offset``.
        * Integer indicating the number of bytes that are desired for the
          next call in order to decode an item.
        """
        if not b:
            return bool(self._decodedvalues), 0, 0

        initialoffset = offset

        # We could easily split the body of this loop into a function. But
        # Python performance is sensitive to function calls and collections
        # are composed of many items. So leaving as a while loop could help
        # with performance. One thing that may not help is the use of
        # if..elif versus a lookup/dispatch table. There may be value
        # in switching that.
        while offset < len(b):
            # Attempt to decode an item. This could be a whole value or a
            # special value indicating an event, such as start or end of a
            # collection or indefinite length type.
            complete, value, readcount, special = decodeitem(b, offset)

            if readcount > 0:
                self.decodedbytecount += readcount

            if not complete:
                assert readcount < 0
                return (
                    bool(self._decodedvalues),
                    offset - initialoffset,
                    -readcount,
                )

            offset += readcount

            # No nested state. We either have a full value or beginning of a
            # complex value to deal with.
            if self._state == self._STATE_NONE:
                # A normal value.
                if special == SPECIAL_NONE:
                    self._decodedvalues.append(value)

                elif special == SPECIAL_START_ARRAY:
                    self._collectionstack.append(
                        {
                            b'remaining': value,
                            b'v': [],
                        }
                    )
                    self._state = self._STATE_WANT_ARRAY_VALUE

                elif special == SPECIAL_START_MAP:
                    self._collectionstack.append(
                        {
                            b'remaining': value,
                            b'v': {},
                        }
                    )
                    self._state = self._STATE_WANT_MAP_KEY

                elif special == SPECIAL_START_SET:
                    self._collectionstack.append(
                        {
                            b'remaining': value,
                            b'v': set(),
                        }
                    )
                    self._state = self._STATE_WANT_SET_VALUE

                elif special == SPECIAL_START_INDEFINITE_BYTESTRING:
                    self._state = self._STATE_WANT_BYTESTRING_CHUNK_FIRST

                else:
                    raise CBORDecodeError(
                        b'unhandled special state: %d' % special
                    )

            # This value becomes an element of the current array.
            elif self._state == self._STATE_WANT_ARRAY_VALUE:
                # Simple values get appended.
                if special == SPECIAL_NONE:
                    c = self._collectionstack[-1]
                    c[b'v'].append(value)
                    c[b'remaining'] -= 1

                    # self._state doesn't need changed.

                # An array nested within an array.
                elif special == SPECIAL_START_ARRAY:
                    lastc = self._collectionstack[-1]
                    newvalue = []

                    lastc[b'v'].append(newvalue)
                    lastc[b'remaining'] -= 1

                    self._collectionstack.append(
                        {
                            b'remaining': value,
                            b'v': newvalue,
                        }
                    )

                    # self._state doesn't need changed.

                # A map nested within an array.
                elif special == SPECIAL_START_MAP:
                    lastc = self._collectionstack[-1]
                    newvalue = {}

                    lastc[b'v'].append(newvalue)
                    lastc[b'remaining'] -= 1

                    self._collectionstack.append(
                        {b'remaining': value, b'v': newvalue}
                    )

                    self._state = self._STATE_WANT_MAP_KEY

                elif special == SPECIAL_START_SET:
                    lastc = self._collectionstack[-1]
                    newvalue = set()

                    lastc[b'v'].append(newvalue)
                    lastc[b'remaining'] -= 1

                    self._collectionstack.append(
                        {
                            b'remaining': value,
                            b'v': newvalue,
                        }
                    )

                    self._state = self._STATE_WANT_SET_VALUE

                elif special == SPECIAL_START_INDEFINITE_BYTESTRING:
                    raise CBORDecodeError(
                        b'indefinite length bytestrings '
                        b'not allowed as array values'
                    )

                else:
                    raise CBORDecodeError(
                        b'unhandled special item when '
                        b'expecting array value: %d' % special
                    )

            # This value becomes the key of the current map instance.
            elif self._state == self._STATE_WANT_MAP_KEY:
                if special == SPECIAL_NONE:
                    self._currentmapkey = value
                    self._state = self._STATE_WANT_MAP_VALUE

                elif special == SPECIAL_START_INDEFINITE_BYTESTRING:
                    raise CBORDecodeError(
                        b'indefinite length bytestrings '
                        b'not allowed as map keys'
                    )

                elif special in (
                    SPECIAL_START_ARRAY,
                    SPECIAL_START_MAP,
                    SPECIAL_START_SET,
                ):
                    raise CBORDecodeError(
                        b'collections not supported as map keys'
                    )

                # We do not allow special values to be used as map keys.
                else:
                    raise CBORDecodeError(
                        b'unhandled special item when '
                        b'expecting map key: %d' % special
                    )

            # This value becomes the value of the current map key.
            elif self._state == self._STATE_WANT_MAP_VALUE:
                # Simple values simply get inserted into the map.
                if special == SPECIAL_NONE:
                    lastc = self._collectionstack[-1]
                    lastc[b'v'][self._currentmapkey] = value
                    lastc[b'remaining'] -= 1

                    self._state = self._STATE_WANT_MAP_KEY

                # A new array is used as the map value.
                elif special == SPECIAL_START_ARRAY:
                    lastc = self._collectionstack[-1]
                    newvalue = []

                    lastc[b'v'][self._currentmapkey] = newvalue
                    lastc[b'remaining'] -= 1

                    self._collectionstack.append(
                        {
                            b'remaining': value,
                            b'v': newvalue,
                        }
                    )

                    self._state = self._STATE_WANT_ARRAY_VALUE

                # A new map is used as the map value.
                elif special == SPECIAL_START_MAP:
                    lastc = self._collectionstack[-1]
                    newvalue = {}

                    lastc[b'v'][self._currentmapkey] = newvalue
                    lastc[b'remaining'] -= 1

                    self._collectionstack.append(
                        {
                            b'remaining': value,
                            b'v': newvalue,
                        }
                    )

                    self._state = self._STATE_WANT_MAP_KEY

                # A new set is used as the map value.
                elif special == SPECIAL_START_SET:
                    lastc = self._collectionstack[-1]
                    newvalue = set()

                    lastc[b'v'][self._currentmapkey] = newvalue
                    lastc[b'remaining'] -= 1

                    self._collectionstack.append(
                        {
                            b'remaining': value,
                            b'v': newvalue,
                        }
                    )

                    self._state = self._STATE_WANT_SET_VALUE

                elif special == SPECIAL_START_INDEFINITE_BYTESTRING:
                    raise CBORDecodeError(
                        b'indefinite length bytestrings not '
                        b'allowed as map values'
                    )

                else:
                    raise CBORDecodeError(
                        b'unhandled special item when '
                        b'expecting map value: %d' % special
                    )

                self._currentmapkey = None

            # This value is added to the current set.
            elif self._state == self._STATE_WANT_SET_VALUE:
                if special == SPECIAL_NONE:
                    lastc = self._collectionstack[-1]
                    lastc[b'v'].add(value)
                    lastc[b'remaining'] -= 1

                elif special == SPECIAL_START_INDEFINITE_BYTESTRING:
                    raise CBORDecodeError(
                        b'indefinite length bytestrings not '
                        b'allowed as set values'
                    )

                elif special in (
                    SPECIAL_START_ARRAY,
                    SPECIAL_START_MAP,
                    SPECIAL_START_SET,
                ):
                    raise CBORDecodeError(
                        b'collections not allowed as set values'
                    )

                # We don't allow non-trivial types to exist as set values.
                else:
                    raise CBORDecodeError(
                        b'unhandled special item when '
                        b'expecting set value: %d' % special
                    )

            # This value represents the first chunk in an indefinite length
            # bytestring.
            elif self._state == self._STATE_WANT_BYTESTRING_CHUNK_FIRST:
                # We received a full chunk.
                if special == SPECIAL_NONE:
                    self._decodedvalues.append(
                        bytestringchunk(value, first=True)
                    )

                    self._state = self._STATE_WANT_BYTESTRING_CHUNK_SUBSEQUENT

                # The end of stream marker. This means it is an empty
                # indefinite length bytestring.
                elif special == SPECIAL_INDEFINITE_BREAK:
                    # We /could/ convert this to a b''. But we want to preserve
                    # the nature of the underlying data so consumers expecting
                    # an indefinite length bytestring get one.
                    self._decodedvalues.append(
                        bytestringchunk(b'', first=True, last=True)
                    )

                    # Since indefinite length bytestrings can't be used in
                    # collections, we must be at the root level.
                    assert not self._collectionstack
                    self._state = self._STATE_NONE

                else:
                    raise CBORDecodeError(
                        b'unexpected special value when '
                        b'expecting bytestring chunk: %d' % special
                    )

            # This value represents the non-initial chunk in an indefinite
            # length bytestring.
            elif self._state == self._STATE_WANT_BYTESTRING_CHUNK_SUBSEQUENT:
                # We received a full chunk.
                if special == SPECIAL_NONE:
                    self._decodedvalues.append(bytestringchunk(value))

                # The end of stream marker.
                elif special == SPECIAL_INDEFINITE_BREAK:
                    self._decodedvalues.append(bytestringchunk(b'', last=True))

                    # Since indefinite length bytestrings can't be used in
                    # collections, we must be at the root level.
                    assert not self._collectionstack
                    self._state = self._STATE_NONE

                else:
                    raise CBORDecodeError(
                        b'unexpected special value when '
                        b'expecting bytestring chunk: %d' % special
                    )

            else:
                raise CBORDecodeError(
                    b'unhandled decoder state: %d' % self._state
                )

            # We could have just added the final value in a collection. End
            # all complete collections at the top of the stack.
            while True:
                # Bail if we're not waiting on a new collection item.
                if self._state not in (
                    self._STATE_WANT_ARRAY_VALUE,
                    self._STATE_WANT_MAP_KEY,
                    self._STATE_WANT_SET_VALUE,
                ):
                    break

                # Or we are expecting more items for this collection.
                lastc = self._collectionstack[-1]

                if lastc[b'remaining']:
                    break

                # The collection at the top of the stack is complete.

                # Discard it, as it isn't needed for future items.
                self._collectionstack.pop()

                # If this is a nested collection, we don't emit it, since it
                # will be emitted by its parent collection. But we do need to
                # update state to reflect what the new top-most collection
                # on the stack is.
                if self._collectionstack:
                    self._state = {
                        list: self._STATE_WANT_ARRAY_VALUE,
                        dict: self._STATE_WANT_MAP_KEY,
                        set: self._STATE_WANT_SET_VALUE,
                    }[type(self._collectionstack[-1][b'v'])]

                # If this is the root collection, emit it.
                else:
                    self._decodedvalues.append(lastc[b'v'])
                    self._state = self._STATE_NONE

        return (
            bool(self._decodedvalues),
            offset - initialoffset,
            0,
        )

    def getavailable(self):
        """Returns an iterator over fully decoded values.

        Once values are retrieved, they won't be available on the next call.
        """

        l = list(self._decodedvalues)
        self._decodedvalues = []
        return l


class bufferingdecoder(object):
    """A CBOR decoder that buffers undecoded input.

    This is a glorified wrapper around ``sansiodecoder`` that adds a buffering
    layer. All input that isn't consumed by ``sansiodecoder`` will be buffered
    and concatenated with any new input that arrives later.

    TODO consider adding limits as to the maximum amount of data that can
    be buffered.
    """

    def __init__(self):
        self._decoder = sansiodecoder()
        self._chunks = []
        self._wanted = 0

    def decode(self, b):
        """Attempt to decode bytes to CBOR values.

        Returns a tuple with the following fields:

        * Bool indicating whether new values are available for retrieval.
        * Integer number of bytes decoded from the new input.
        * Integer number of bytes wanted to decode the next value.
        """
        # We /might/ be able to support passing a bytearray all the
        # way through. For now, let's cheat.
        if isinstance(b, bytearray):
            b = bytes(b)

        # Our strategy for buffering is to aggregate the incoming chunks in a
        # list until we've received enough data to decode the next item.
        # This is slightly more complicated than using an ``io.BytesIO``
        # or continuously concatenating incoming data. However, because it
        # isn't constantly reallocating backing memory for a growing buffer,
        # it prevents excessive memory thrashing and is significantly faster,
        # especially in cases where the percentage of input chunks that don't
        # decode into a full item is high.

        if self._chunks:
            # A previous call said we needed N bytes to decode the next item.
            # But this call doesn't provide enough data. We buffer the incoming
            # chunk without attempting to decode.
            if len(b) < self._wanted:
                self._chunks.append(b)
                self._wanted -= len(b)
                return False, 0, self._wanted

            # Else we may have enough data to decode the next item. Aggregate
            # old data with new and reset the buffer.
            newlen = len(b)
            self._chunks.append(b)
            b = b''.join(self._chunks)
            self._chunks = []
            oldlen = len(b) - newlen

        else:
            oldlen = 0

        available, readcount, wanted = self._decoder.decode(b)
        self._wanted = wanted

        if readcount < len(b):
            self._chunks.append(b[readcount:])

        return available, readcount - oldlen, wanted

    def getavailable(self):
        return self._decoder.getavailable()


def decodeall(b):
    """Decode all CBOR items present in an iterable of bytes.

    In addition to regular decode errors, raises CBORDecodeError if the
    entirety of the passed buffer does not fully decode to complete CBOR
    values. This includes failure to decode any value, incomplete collection
    types, incomplete indefinite length items, and extra data at the end of
    the buffer.
    """
    if not b:
        return []

    decoder = sansiodecoder()

    havevalues, readcount, wantbytes = decoder.decode(b)

    if readcount != len(b):
        raise CBORDecodeError(b'input data not fully consumed')

    if decoder.inprogress:
        raise CBORDecodeError(b'input data not complete')

    return decoder.getavailable()
