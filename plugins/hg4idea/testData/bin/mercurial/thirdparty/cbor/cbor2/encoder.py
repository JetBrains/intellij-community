import re
import struct
from collections import OrderedDict, defaultdict
from contextlib import contextmanager
from functools import wraps
from datetime import datetime, date, time
from io import BytesIO

from .compat import (
    iteritems, timezone, long, unicode, as_unicode, bytes_from_list, pack_float16, unpack_float16)
from .types import CBORTag, undefined, CBORSimpleValue


class CBOREncodeError(Exception):
    """Raised when an error occurs while serializing an object into a CBOR datastream."""


def shareable_encoder(func):
    """
    Wrap the given encoder function to gracefully handle cyclic data structures.

    If value sharing is enabled, this marks the given value shared in the datastream on the
    first call. If the value has already been passed to this method, a reference marker is
    instead written to the data stream and the wrapped function is not called.

    If value sharing is disabled, only infinite recursion protection is done.

    """
    @wraps(func)
    def wrapper(encoder, value, *args, **kwargs):
        value_id = id(value)
        container, container_index = encoder._shared_containers.get(value_id, (None, None))
        if encoder.value_sharing:
            if container is value:
                # Generate a reference to the previous index instead of encoding this again
                encoder.write(encode_length(0xd8, 0x1d))
                encode_int(encoder, container_index)
            else:
                # Mark the container as shareable
                encoder._shared_containers[value_id] = (value, len(encoder._shared_containers))
                encoder.write(encode_length(0xd8, 0x1c))
                func(encoder, value, *args, **kwargs)
        else:
            if container is value:
                raise CBOREncodeError('cyclic data structure detected but value sharing is '
                                      'disabled')
            else:
                encoder._shared_containers[value_id] = (value, None)
                func(encoder, value, *args, **kwargs)
                del encoder._shared_containers[value_id]

    return wrapper


def encode_length(major_tag, length):
    if length < 24:
        return struct.pack('>B', major_tag | length)
    elif length < 256:
        return struct.pack('>BB', major_tag | 24, length)
    elif length < 65536:
        return struct.pack('>BH', major_tag | 25, length)
    elif length < 4294967296:
        return struct.pack('>BL', major_tag | 26, length)
    else:
        return struct.pack('>BQ', major_tag | 27, length)


def encode_int(encoder, value):
    # Big integers (2 ** 64 and over)
    if value >= 18446744073709551616 or value < -18446744073709551616:
        if value >= 0:
            major_type = 0x02
        else:
            major_type = 0x03
            value = -value - 1

        values = []
        while value > 0:
            value, remainder = divmod(value, 256)
            values.insert(0, remainder)

        payload = bytes_from_list(values)
        encode_semantic(encoder, CBORTag(major_type, payload))
    elif value >= 0:
        encoder.write(encode_length(0, value))
    else:
        encoder.write(encode_length(0x20, abs(value) - 1))


def encode_bytestring(encoder, value):
    encoder.write(encode_length(0x40, len(value)) + value)


def encode_bytearray(encoder, value):
    encode_bytestring(encoder, bytes(value))


def encode_string(encoder, value):
    encoded = value.encode('utf-8')
    encoder.write(encode_length(0x60, len(encoded)) + encoded)


@shareable_encoder
def encode_array(encoder, value):
    encoder.write(encode_length(0x80, len(value)))
    for item in value:
        encoder.encode(item)


@shareable_encoder
def encode_map(encoder, value):
    encoder.write(encode_length(0xa0, len(value)))
    for key, val in iteritems(value):
        encoder.encode(key)
        encoder.encode(val)


def encode_sortable_key(encoder, value):
    """Takes a key and calculates the length of its optimal byte representation"""
    encoded = encoder.encode_to_bytes(value)
    return len(encoded), encoded


@shareable_encoder
def encode_canonical_map(encoder, value):
    """Reorder keys according to Canonical CBOR specification"""
    keyed_keys = ((encode_sortable_key(encoder, key), key) for key in value.keys())
    encoder.write(encode_length(0xa0, len(value)))
    for sortkey, realkey in sorted(keyed_keys):
        encoder.write(sortkey[1])
        encoder.encode(value[realkey])


def encode_semantic(encoder, value):
    encoder.write(encode_length(0xc0, value.tag))
    encoder.encode(value.value)


#
# Semantic decoders (major tag 6)
#

def encode_datetime(encoder, value):
    # Semantic tag 0
    if not value.tzinfo:
        if encoder.timezone:
            value = value.replace(tzinfo=encoder.timezone)
        else:
            raise CBOREncodeError(
                'naive datetime encountered and no default timezone has been set')

    if encoder.datetime_as_timestamp:
        from calendar import timegm
        timestamp = timegm(value.utctimetuple()) + value.microsecond // 1000000
        encode_semantic(encoder, CBORTag(1, timestamp))
    else:
        datestring = as_unicode(value.isoformat().replace('+00:00', 'Z'))
        encode_semantic(encoder, CBORTag(0, datestring))


def encode_date(encoder, value):
    value = datetime.combine(value, time()).replace(tzinfo=timezone.utc)
    encode_datetime(encoder, value)


def encode_decimal(encoder, value):
    # Semantic tag 4
    if value.is_nan():
        encoder.write(b'\xf9\x7e\x00')
    elif value.is_infinite():
        encoder.write(b'\xf9\x7c\x00' if value > 0 else b'\xf9\xfc\x00')
    else:
        dt = value.as_tuple()
        mantissa = sum(d * 10 ** i for i, d in enumerate(reversed(dt.digits)))
        with encoder.disable_value_sharing():
            encode_semantic(encoder, CBORTag(4, [dt.exponent, mantissa]))


def encode_rational(encoder, value):
    # Semantic tag 30
    with encoder.disable_value_sharing():
        encode_semantic(encoder, CBORTag(30, [value.numerator, value.denominator]))


def encode_regexp(encoder, value):
    # Semantic tag 35
    encode_semantic(encoder, CBORTag(35, as_unicode(value.pattern)))


def encode_mime(encoder, value):
    # Semantic tag 36
    encode_semantic(encoder, CBORTag(36, as_unicode(value.as_string())))


def encode_uuid(encoder, value):
    # Semantic tag 37
    encode_semantic(encoder, CBORTag(37, value.bytes))


def encode_set(encoder, value):
    # Semantic tag 258
    encode_semantic(encoder, CBORTag(258, tuple(value)))


def encode_canonical_set(encoder, value):
    # Semantic tag 258
    values = sorted([(encode_sortable_key(encoder, key), key) for key in value])
    encode_semantic(encoder, CBORTag(258, [key[1] for key in values]))


#
# Special encoders (major tag 7)
#

def encode_simple_value(encoder, value):
    if value.value < 20:
        encoder.write(struct.pack('>B', 0xe0 | value.value))
    else:
        encoder.write(struct.pack('>BB', 0xf8, value.value))


def encode_float(encoder, value):
    # Handle special values efficiently
    import math
    if math.isnan(value):
        encoder.write(b'\xf9\x7e\x00')
    elif math.isinf(value):
        encoder.write(b'\xf9\x7c\x00' if value > 0 else b'\xf9\xfc\x00')
    else:
        encoder.write(struct.pack('>Bd', 0xfb, value))


def encode_minimal_float(encoder, value):
    # Handle special values efficiently
    import math
    if math.isnan(value):
        encoder.write(b'\xf9\x7e\x00')
    elif math.isinf(value):
        encoder.write(b'\xf9\x7c\x00' if value > 0 else b'\xf9\xfc\x00')
    else:
        encoded = struct.pack('>Bf', 0xfa, value)
        if struct.unpack('>Bf', encoded)[1] != value:
            encoded = struct.pack('>Bd', 0xfb, value)
            encoder.write(encoded)
        else:
            f16 = pack_float16(value)
            if f16 and unpack_float16(f16[1:]) == value:
                encoder.write(f16)
            else:
                encoder.write(encoded)


def encode_boolean(encoder, value):
    encoder.write(b'\xf5' if value else b'\xf4')


def encode_none(encoder, value):
    encoder.write(b'\xf6')


def encode_undefined(encoder, value):
    encoder.write(b'\xf7')


default_encoders = OrderedDict([
    (bytes, encode_bytestring),
    (bytearray, encode_bytearray),
    (unicode, encode_string),
    (int, encode_int),
    (long, encode_int),
    (float, encode_float),
    (('decimal', 'Decimal'), encode_decimal),
    (bool, encode_boolean),
    (type(None), encode_none),
    (tuple, encode_array),
    (list, encode_array),
    (dict, encode_map),
    (defaultdict, encode_map),
    (OrderedDict, encode_map),
    (type(undefined), encode_undefined),
    (datetime, encode_datetime),
    (date, encode_date),
    (type(re.compile('')), encode_regexp),
    (('fractions', 'Fraction'), encode_rational),
    (('email.message', 'Message'), encode_mime),
    (('uuid', 'UUID'), encode_uuid),
    (CBORSimpleValue, encode_simple_value),
    (CBORTag, encode_semantic),
    (set, encode_set),
    (frozenset, encode_set)
])

canonical_encoders = OrderedDict([
    (float, encode_minimal_float),
    (dict, encode_canonical_map),
    (defaultdict, encode_canonical_map),
    (OrderedDict, encode_canonical_map),
    (set, encode_canonical_set),
    (frozenset, encode_canonical_set)
])


class CBOREncoder(object):
    """
    Serializes objects to a byte stream using Concise Binary Object Representation.

    :param datetime_as_timestamp: set to ``True`` to serialize datetimes as UNIX timestamps
        (this makes datetimes more concise on the wire but loses the time zone information)
    :param datetime.tzinfo timezone: the default timezone to use for serializing naive datetimes
    :param value_sharing: if ``True``, allows more efficient serializing of repeated values and,
        more importantly, cyclic data structures, at the cost of extra line overhead
    :param default: a callable that is called by the encoder with three arguments
        (encoder, value, file object) when no suitable encoder has been found, and should use the
        methods on the encoder to encode any objects it wants to add to the data stream
    :param canonical: Forces mapping types to be output in a stable order to guarantee that the
        output will always produce the same hash given the same input.
    """

    __slots__ = ('fp', 'datetime_as_timestamp', 'timezone', 'default', 'value_sharing',
                 'json_compatible', '_shared_containers', '_encoders')

    def __init__(self, fp, datetime_as_timestamp=False, timezone=None, value_sharing=False,
                 default=None, canonical=False):
        self.fp = fp
        self.datetime_as_timestamp = datetime_as_timestamp
        self.timezone = timezone
        self.value_sharing = value_sharing
        self.default = default
        self._shared_containers = {}  # indexes used for value sharing
        self._encoders = default_encoders.copy()
        if canonical:
            self._encoders.update(canonical_encoders)

    def _find_encoder(self, obj_type):
        from sys import modules

        for type_, enc in list(iteritems(self._encoders)):
            if type(type_) is tuple:
                modname, typename = type_
                imported_type = getattr(modules.get(modname), typename, None)
                if imported_type is not None:
                    del self._encoders[type_]
                    self._encoders[imported_type] = enc
                    type_ = imported_type
                else:  # pragma: nocover
                    continue

            if issubclass(obj_type, type_):
                self._encoders[obj_type] = enc
                return enc

        return None

    @contextmanager
    def disable_value_sharing(self):
        """Disable value sharing in the encoder for the duration of the context block."""
        old_value_sharing = self.value_sharing
        self.value_sharing = False
        yield
        self.value_sharing = old_value_sharing

    def write(self, data):
        """
        Write bytes to the data stream.

        :param data: the bytes to write

        """
        self.fp.write(data)

    def encode(self, obj):
        """
        Encode the given object using CBOR.

        :param obj: the object to encode

        """
        obj_type = obj.__class__
        encoder = self._encoders.get(obj_type) or self._find_encoder(obj_type) or self.default
        if not encoder:
            raise CBOREncodeError('cannot serialize type %s' % obj_type.__name__)

        encoder(self, obj)

    def encode_to_bytes(self, obj):
        """
        Encode the given object to a byte buffer and return its value as bytes.

        This method was intended to be used from the ``default`` hook when an object needs to be
        encoded separately from the rest but while still taking advantage of the shared value
        registry.

        """
        old_fp = self.fp
        self.fp = fp = BytesIO()
        self.encode(obj)
        self.fp = old_fp
        return fp.getvalue()


def dumps(obj, **kwargs):
    """
    Serialize an object to a bytestring.

    :param obj: the object to serialize
    :param kwargs: keyword arguments passed to :class:`~.CBOREncoder`
    :return: the serialized output
    :rtype: bytes

    """
    fp = BytesIO()
    dump(obj, fp, **kwargs)
    return fp.getvalue()


def dump(obj, fp, **kwargs):
    """
    Serialize an object to a file.

    :param obj: the object to serialize
    :param fp: a file-like object
    :param kwargs: keyword arguments passed to :class:`~.CBOREncoder`

    """
    CBOREncoder(fp, **kwargs).encode(obj)
