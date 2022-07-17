import re
import struct
from datetime import datetime, timedelta
from io import BytesIO

from .compat import timezone, xrange, byte_as_integer, unpack_float16
from .types import CBORTag, undefined, break_marker, CBORSimpleValue

timestamp_re = re.compile(r'^(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d):(\d\d)'
                          r'(?:\.(\d+))?(?:Z|([+-]\d\d):(\d\d))$')


class CBORDecodeError(Exception):
    """Raised when an error occurs deserializing a CBOR datastream."""


def decode_uint(decoder, subtype, shareable_index=None, allow_indefinite=False):
    # Major tag 0
    if subtype < 24:
        return subtype
    elif subtype == 24:
        return struct.unpack('>B', decoder.read(1))[0]
    elif subtype == 25:
        return struct.unpack('>H', decoder.read(2))[0]
    elif subtype == 26:
        return struct.unpack('>L', decoder.read(4))[0]
    elif subtype == 27:
        return struct.unpack('>Q', decoder.read(8))[0]
    elif subtype == 31 and allow_indefinite:
        return None
    else:
        raise CBORDecodeError('unknown unsigned integer subtype 0x%x' % subtype)


def decode_negint(decoder, subtype, shareable_index=None):
    # Major tag 1
    uint = decode_uint(decoder, subtype)
    return -uint - 1


def decode_bytestring(decoder, subtype, shareable_index=None):
    # Major tag 2
    length = decode_uint(decoder, subtype, allow_indefinite=True)
    if length is None:
        # Indefinite length
        buf = bytearray()
        while True:
            initial_byte = byte_as_integer(decoder.read(1))
            if initial_byte == 255:
                return buf
            else:
                length = decode_uint(decoder, initial_byte & 31)
                value = decoder.read(length)
                buf.extend(value)
    else:
        return decoder.read(length)


def decode_string(decoder, subtype, shareable_index=None):
    # Major tag 3
    return decode_bytestring(decoder, subtype).decode('utf-8')


def decode_array(decoder, subtype, shareable_index=None):
    # Major tag 4
    items = []
    decoder.set_shareable(shareable_index, items)
    length = decode_uint(decoder, subtype, allow_indefinite=True)
    if length is None:
        # Indefinite length
        while True:
            value = decoder.decode()
            if value is break_marker:
                break
            else:
                items.append(value)
    else:
        for _ in xrange(length):
            item = decoder.decode()
            items.append(item)

    return items


def decode_map(decoder, subtype, shareable_index=None):
    # Major tag 5
    dictionary = {}
    decoder.set_shareable(shareable_index, dictionary)
    length = decode_uint(decoder, subtype, allow_indefinite=True)
    if length is None:
        # Indefinite length
        while True:
            key = decoder.decode()
            if key is break_marker:
                break
            else:
                value = decoder.decode()
                dictionary[key] = value
    else:
        for _ in xrange(length):
            key = decoder.decode()
            value = decoder.decode()
            dictionary[key] = value

    if decoder.object_hook:
        return decoder.object_hook(decoder, dictionary)
    else:
        return dictionary


def decode_semantic(decoder, subtype, shareable_index=None):
    # Major tag 6
    tagnum = decode_uint(decoder, subtype)

    # Special handling for the "shareable" tag
    if tagnum == 28:
        shareable_index = decoder._allocate_shareable()
        return decoder.decode(shareable_index)

    value = decoder.decode()
    semantic_decoder = semantic_decoders.get(tagnum)
    if semantic_decoder:
        return semantic_decoder(decoder, value, shareable_index)

    tag = CBORTag(tagnum, value)
    if decoder.tag_hook:
        return decoder.tag_hook(decoder, tag, shareable_index)
    else:
        return tag


def decode_special(decoder, subtype, shareable_index=None):
    # Simple value
    if subtype < 20:
        return CBORSimpleValue(subtype)

    # Major tag 7
    return special_decoders[subtype](decoder)


#
# Semantic decoders (major tag 6)
#

def decode_datetime_string(decoder, value, shareable_index=None):
    # Semantic tag 0
    match = timestamp_re.match(value)
    if match:
        year, month, day, hour, minute, second, micro, offset_h, offset_m = match.groups()
        if offset_h:
            tz = timezone(timedelta(hours=int(offset_h), minutes=int(offset_m)))
        else:
            tz = timezone.utc

        return datetime(int(year), int(month), int(day), int(hour), int(minute), int(second),
                        int(micro or 0), tz)
    else:
        raise CBORDecodeError('invalid datetime string: {}'.format(value))


def decode_epoch_datetime(decoder, value, shareable_index=None):
    # Semantic tag 1
    return datetime.fromtimestamp(value, timezone.utc)


def decode_positive_bignum(decoder, value, shareable_index=None):
    # Semantic tag 2
    from binascii import hexlify
    return int(hexlify(value), 16)


def decode_negative_bignum(decoder, value, shareable_index=None):
    # Semantic tag 3
    return -decode_positive_bignum(decoder, value) - 1


def decode_fraction(decoder, value, shareable_index=None):
    # Semantic tag 4
    from decimal import Decimal
    exp = Decimal(value[0])
    mantissa = Decimal(value[1])
    return mantissa * (10 ** exp)


def decode_bigfloat(decoder, value, shareable_index=None):
    # Semantic tag 5
    from decimal import Decimal
    exp = Decimal(value[0])
    mantissa = Decimal(value[1])
    return mantissa * (2 ** exp)


def decode_sharedref(decoder, value, shareable_index=None):
    # Semantic tag 29
    try:
        shared = decoder._shareables[value]
    except IndexError:
        raise CBORDecodeError('shared reference %d not found' % value)

    if shared is None:
        raise CBORDecodeError('shared value %d has not been initialized' % value)
    else:
        return shared


def decode_rational(decoder, value, shareable_index=None):
    # Semantic tag 30
    from fractions import Fraction
    return Fraction(*value)


def decode_regexp(decoder, value, shareable_index=None):
    # Semantic tag 35
    return re.compile(value)


def decode_mime(decoder, value, shareable_index=None):
    # Semantic tag 36
    from email.parser import Parser
    return Parser().parsestr(value)


def decode_uuid(decoder, value, shareable_index=None):
    # Semantic tag 37
    from uuid import UUID
    return UUID(bytes=value)


def decode_set(decoder, value, shareable_index=None):
    # Semantic tag 258
    return set(value)


#
# Special decoders (major tag 7)
#

def decode_simple_value(decoder, shareable_index=None):
    return CBORSimpleValue(struct.unpack('>B', decoder.read(1))[0])


def decode_float16(decoder, shareable_index=None):
    payload = decoder.read(2)
    return unpack_float16(payload)


def decode_float32(decoder, shareable_index=None):
    return struct.unpack('>f', decoder.read(4))[0]


def decode_float64(decoder, shareable_index=None):
    return struct.unpack('>d', decoder.read(8))[0]


major_decoders = {
    0: decode_uint,
    1: decode_negint,
    2: decode_bytestring,
    3: decode_string,
    4: decode_array,
    5: decode_map,
    6: decode_semantic,
    7: decode_special
}

special_decoders = {
    20: lambda self: False,
    21: lambda self: True,
    22: lambda self: None,
    23: lambda self: undefined,
    24: decode_simple_value,
    25: decode_float16,
    26: decode_float32,
    27: decode_float64,
    31: lambda self: break_marker
}

semantic_decoders = {
    0: decode_datetime_string,
    1: decode_epoch_datetime,
    2: decode_positive_bignum,
    3: decode_negative_bignum,
    4: decode_fraction,
    5: decode_bigfloat,
    29: decode_sharedref,
    30: decode_rational,
    35: decode_regexp,
    36: decode_mime,
    37: decode_uuid,
    258: decode_set
}


class CBORDecoder(object):
    """
    Deserializes a CBOR encoded byte stream.

    :param tag_hook: Callable that takes 3 arguments: the decoder instance, the
        :class:`~cbor2.types.CBORTag` and the shareable index for the resulting object, if any.
        This callback is called for any tags for which there is no built-in decoder.
        The return value is substituted for the CBORTag object in the deserialized output.
    :param object_hook: Callable that takes 2 arguments: the decoder instance and the dictionary.
        This callback is called for each deserialized :class:`dict` object.
        The return value is substituted for the dict in the deserialized output.
    """

    __slots__ = ('fp', 'tag_hook', 'object_hook', '_shareables')

    def __init__(self, fp, tag_hook=None, object_hook=None):
        self.fp = fp
        self.tag_hook = tag_hook
        self.object_hook = object_hook
        self._shareables = []

    def _allocate_shareable(self):
        self._shareables.append(None)
        return len(self._shareables) - 1

    def set_shareable(self, index, value):
        """
        Set the shareable value for the last encountered shared value marker, if any.

        If the given index is ``None``, nothing is done.

        :param index: the value of the ``shared_index`` argument to the decoder
        :param value: the shared value

        """
        if index is not None:
            self._shareables[index] = value

    def read(self, amount):
        """
        Read bytes from the data stream.

        :param int amount: the number of bytes to read

        """
        data = self.fp.read(amount)
        if len(data) < amount:
            raise CBORDecodeError('premature end of stream (expected to read {} bytes, got {} '
                                  'instead)'.format(amount, len(data)))

        return data

    def decode(self, shareable_index=None):
        """
        Decode the next value from the stream.

        :raises CBORDecodeError: if there is any problem decoding the stream

        """
        try:
            initial_byte = byte_as_integer(self.fp.read(1))
            major_type = initial_byte >> 5
            subtype = initial_byte & 31
        except Exception as e:
            raise CBORDecodeError('error reading major type at index {}: {}'
                                  .format(self.fp.tell(), e))

        decoder = major_decoders[major_type]
        try:
            return decoder(self, subtype, shareable_index)
        except CBORDecodeError:
            raise
        except Exception as e:
            raise CBORDecodeError('error decoding value at index {}: {}'.format(self.fp.tell(), e))

    def decode_from_bytes(self, buf):
        """
        Wrap the given bytestring as a file and call :meth:`decode` with it as the argument.

        This method was intended to be used from the ``tag_hook`` hook when an object needs to be
        decoded separately from the rest but while still taking advantage of the shared value
        registry.

        """
        old_fp = self.fp
        self.fp = BytesIO(buf)
        retval = self.decode()
        self.fp = old_fp
        return retval


def loads(payload, **kwargs):
    """
    Deserialize an object from a bytestring.

    :param bytes payload: the bytestring to serialize
    :param kwargs: keyword arguments passed to :class:`~.CBORDecoder`
    :return: the deserialized object

    """
    fp = BytesIO(payload)
    return CBORDecoder(fp, **kwargs).decode()


def load(fp, **kwargs):
    """
    Deserialize an object from an open file.

    :param fp: the input file (any file-like object)
    :param kwargs: keyword arguments passed to :class:`~.CBORDecoder`
    :return: the deserialized object

    """
    return CBORDecoder(fp, **kwargs).decode()
