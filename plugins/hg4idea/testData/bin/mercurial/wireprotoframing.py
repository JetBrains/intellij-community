# wireprotoframing.py - unified framing protocol for wire protocol
#
# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# This file contains functionality to support the unified frame-based wire
# protocol. For details about the protocol, see
# `hg help internals.wireprotocol`.

from __future__ import absolute_import

import collections
import struct

from .i18n import _
from .pycompat import getattr
from .thirdparty import attr
from . import (
    encoding,
    error,
    pycompat,
    util,
    wireprototypes,
)
from .utils import (
    cborutil,
    stringutil,
)

FRAME_HEADER_SIZE = 8
DEFAULT_MAX_FRAME_SIZE = 32768

STREAM_FLAG_BEGIN_STREAM = 0x01
STREAM_FLAG_END_STREAM = 0x02
STREAM_FLAG_ENCODING_APPLIED = 0x04

STREAM_FLAGS = {
    b'stream-begin': STREAM_FLAG_BEGIN_STREAM,
    b'stream-end': STREAM_FLAG_END_STREAM,
    b'encoded': STREAM_FLAG_ENCODING_APPLIED,
}

FRAME_TYPE_COMMAND_REQUEST = 0x01
FRAME_TYPE_COMMAND_DATA = 0x02
FRAME_TYPE_COMMAND_RESPONSE = 0x03
FRAME_TYPE_ERROR_RESPONSE = 0x05
FRAME_TYPE_TEXT_OUTPUT = 0x06
FRAME_TYPE_PROGRESS = 0x07
FRAME_TYPE_SENDER_PROTOCOL_SETTINGS = 0x08
FRAME_TYPE_STREAM_SETTINGS = 0x09

FRAME_TYPES = {
    b'command-request': FRAME_TYPE_COMMAND_REQUEST,
    b'command-data': FRAME_TYPE_COMMAND_DATA,
    b'command-response': FRAME_TYPE_COMMAND_RESPONSE,
    b'error-response': FRAME_TYPE_ERROR_RESPONSE,
    b'text-output': FRAME_TYPE_TEXT_OUTPUT,
    b'progress': FRAME_TYPE_PROGRESS,
    b'sender-protocol-settings': FRAME_TYPE_SENDER_PROTOCOL_SETTINGS,
    b'stream-settings': FRAME_TYPE_STREAM_SETTINGS,
}

FLAG_COMMAND_REQUEST_NEW = 0x01
FLAG_COMMAND_REQUEST_CONTINUATION = 0x02
FLAG_COMMAND_REQUEST_MORE_FRAMES = 0x04
FLAG_COMMAND_REQUEST_EXPECT_DATA = 0x08

FLAGS_COMMAND_REQUEST = {
    b'new': FLAG_COMMAND_REQUEST_NEW,
    b'continuation': FLAG_COMMAND_REQUEST_CONTINUATION,
    b'more': FLAG_COMMAND_REQUEST_MORE_FRAMES,
    b'have-data': FLAG_COMMAND_REQUEST_EXPECT_DATA,
}

FLAG_COMMAND_DATA_CONTINUATION = 0x01
FLAG_COMMAND_DATA_EOS = 0x02

FLAGS_COMMAND_DATA = {
    b'continuation': FLAG_COMMAND_DATA_CONTINUATION,
    b'eos': FLAG_COMMAND_DATA_EOS,
}

FLAG_COMMAND_RESPONSE_CONTINUATION = 0x01
FLAG_COMMAND_RESPONSE_EOS = 0x02

FLAGS_COMMAND_RESPONSE = {
    b'continuation': FLAG_COMMAND_RESPONSE_CONTINUATION,
    b'eos': FLAG_COMMAND_RESPONSE_EOS,
}

FLAG_SENDER_PROTOCOL_SETTINGS_CONTINUATION = 0x01
FLAG_SENDER_PROTOCOL_SETTINGS_EOS = 0x02

FLAGS_SENDER_PROTOCOL_SETTINGS = {
    b'continuation': FLAG_SENDER_PROTOCOL_SETTINGS_CONTINUATION,
    b'eos': FLAG_SENDER_PROTOCOL_SETTINGS_EOS,
}

FLAG_STREAM_ENCODING_SETTINGS_CONTINUATION = 0x01
FLAG_STREAM_ENCODING_SETTINGS_EOS = 0x02

FLAGS_STREAM_ENCODING_SETTINGS = {
    b'continuation': FLAG_STREAM_ENCODING_SETTINGS_CONTINUATION,
    b'eos': FLAG_STREAM_ENCODING_SETTINGS_EOS,
}

# Maps frame types to their available flags.
FRAME_TYPE_FLAGS = {
    FRAME_TYPE_COMMAND_REQUEST: FLAGS_COMMAND_REQUEST,
    FRAME_TYPE_COMMAND_DATA: FLAGS_COMMAND_DATA,
    FRAME_TYPE_COMMAND_RESPONSE: FLAGS_COMMAND_RESPONSE,
    FRAME_TYPE_ERROR_RESPONSE: {},
    FRAME_TYPE_TEXT_OUTPUT: {},
    FRAME_TYPE_PROGRESS: {},
    FRAME_TYPE_SENDER_PROTOCOL_SETTINGS: FLAGS_SENDER_PROTOCOL_SETTINGS,
    FRAME_TYPE_STREAM_SETTINGS: FLAGS_STREAM_ENCODING_SETTINGS,
}

ARGUMENT_RECORD_HEADER = struct.Struct('<HH')


def humanflags(mapping, value):
    """Convert a numeric flags value to a human value, using a mapping table."""
    namemap = {v: k for k, v in pycompat.iteritems(mapping)}
    flags = []
    val = 1
    while value >= val:
        if value & val:
            flags.append(namemap.get(val, b'<unknown 0x%02x>' % val))
        val <<= 1

    return b'|'.join(flags)


@attr.s(slots=True)
class frameheader(object):
    """Represents the data in a frame header."""

    length = attr.ib()
    requestid = attr.ib()
    streamid = attr.ib()
    streamflags = attr.ib()
    typeid = attr.ib()
    flags = attr.ib()


@attr.s(slots=True, repr=False)
class frame(object):
    """Represents a parsed frame."""

    requestid = attr.ib()
    streamid = attr.ib()
    streamflags = attr.ib()
    typeid = attr.ib()
    flags = attr.ib()
    payload = attr.ib()

    @encoding.strmethod
    def __repr__(self):
        typename = b'<unknown 0x%02x>' % self.typeid
        for name, value in pycompat.iteritems(FRAME_TYPES):
            if value == self.typeid:
                typename = name
                break

        return (
            b'frame(size=%d; request=%d; stream=%d; streamflags=%s; '
            b'type=%s; flags=%s)'
            % (
                len(self.payload),
                self.requestid,
                self.streamid,
                humanflags(STREAM_FLAGS, self.streamflags),
                typename,
                humanflags(FRAME_TYPE_FLAGS.get(self.typeid, {}), self.flags),
            )
        )


def makeframe(requestid, streamid, streamflags, typeid, flags, payload):
    """Assemble a frame into a byte array."""
    # TODO assert size of payload.
    frame = bytearray(FRAME_HEADER_SIZE + len(payload))

    # 24 bits length
    # 16 bits request id
    # 8 bits stream id
    # 8 bits stream flags
    # 4 bits type
    # 4 bits flags

    l = struct.pack('<I', len(payload))
    frame[0:3] = l[0:3]
    struct.pack_into('<HBB', frame, 3, requestid, streamid, streamflags)
    frame[7] = (typeid << 4) | flags
    frame[8:] = payload

    return frame


def makeframefromhumanstring(s):
    """Create a frame from a human readable string

    Strings have the form:

        <request-id> <stream-id> <stream-flags> <type> <flags> <payload>

    This can be used by user-facing applications and tests for creating
    frames easily without having to type out a bunch of constants.

    Request ID and stream IDs are integers.

    Stream flags, frame type, and flags can be specified by integer or
    named constant.

    Flags can be delimited by `|` to bitwise OR them together.

    If the payload begins with ``cbor:``, the following string will be
    evaluated as Python literal and the resulting object will be fed into
    a CBOR encoder. Otherwise, the payload is interpreted as a Python
    byte string literal.
    """
    fields = s.split(b' ', 5)
    requestid, streamid, streamflags, frametype, frameflags, payload = fields

    requestid = int(requestid)
    streamid = int(streamid)

    finalstreamflags = 0
    for flag in streamflags.split(b'|'):
        if flag in STREAM_FLAGS:
            finalstreamflags |= STREAM_FLAGS[flag]
        else:
            finalstreamflags |= int(flag)

    if frametype in FRAME_TYPES:
        frametype = FRAME_TYPES[frametype]
    else:
        frametype = int(frametype)

    finalflags = 0
    validflags = FRAME_TYPE_FLAGS[frametype]
    for flag in frameflags.split(b'|'):
        if flag in validflags:
            finalflags |= validflags[flag]
        else:
            finalflags |= int(flag)

    if payload.startswith(b'cbor:'):
        payload = b''.join(
            cborutil.streamencode(stringutil.evalpythonliteral(payload[5:]))
        )

    else:
        payload = stringutil.unescapestr(payload)

    return makeframe(
        requestid=requestid,
        streamid=streamid,
        streamflags=finalstreamflags,
        typeid=frametype,
        flags=finalflags,
        payload=payload,
    )


def parseheader(data):
    """Parse a unified framing protocol frame header from a buffer.

    The header is expected to be in the buffer at offset 0 and the
    buffer is expected to be large enough to hold a full header.
    """
    # 24 bits payload length (little endian)
    # 16 bits request ID
    # 8 bits stream ID
    # 8 bits stream flags
    # 4 bits frame type
    # 4 bits frame flags
    # ... payload
    framelength = data[0] + 256 * data[1] + 16384 * data[2]
    requestid, streamid, streamflags = struct.unpack_from('<HBB', data, 3)
    typeflags = data[7]

    frametype = (typeflags & 0xF0) >> 4
    frameflags = typeflags & 0x0F

    return frameheader(
        framelength, requestid, streamid, streamflags, frametype, frameflags
    )


def readframe(fh):
    """Read a unified framing protocol frame from a file object.

    Returns a 3-tuple of (type, flags, payload) for the decoded frame or
    None if no frame is available. May raise if a malformed frame is
    seen.
    """
    header = bytearray(FRAME_HEADER_SIZE)

    readcount = fh.readinto(header)

    if readcount == 0:
        return None

    if readcount != FRAME_HEADER_SIZE:
        raise error.Abort(
            _(b'received incomplete frame: got %d bytes: %s')
            % (readcount, header)
        )

    h = parseheader(header)

    payload = fh.read(h.length)
    if len(payload) != h.length:
        raise error.Abort(
            _(b'frame length error: expected %d; got %d')
            % (h.length, len(payload))
        )

    return frame(
        h.requestid, h.streamid, h.streamflags, h.typeid, h.flags, payload
    )


def createcommandframes(
    stream,
    requestid,
    cmd,
    args,
    datafh=None,
    maxframesize=DEFAULT_MAX_FRAME_SIZE,
    redirect=None,
):
    """Create frames necessary to transmit a request to run a command.

    This is a generator of bytearrays. Each item represents a frame
    ready to be sent over the wire to a peer.
    """
    data = {b'name': cmd}
    if args:
        data[b'args'] = args

    if redirect:
        data[b'redirect'] = redirect

    data = b''.join(cborutil.streamencode(data))

    offset = 0

    while True:
        flags = 0

        # Must set new or continuation flag.
        if not offset:
            flags |= FLAG_COMMAND_REQUEST_NEW
        else:
            flags |= FLAG_COMMAND_REQUEST_CONTINUATION

        # Data frames is set on all frames.
        if datafh:
            flags |= FLAG_COMMAND_REQUEST_EXPECT_DATA

        payload = data[offset : offset + maxframesize]
        offset += len(payload)

        if len(payload) == maxframesize and offset < len(data):
            flags |= FLAG_COMMAND_REQUEST_MORE_FRAMES

        yield stream.makeframe(
            requestid=requestid,
            typeid=FRAME_TYPE_COMMAND_REQUEST,
            flags=flags,
            payload=payload,
        )

        if not (flags & FLAG_COMMAND_REQUEST_MORE_FRAMES):
            break

    if datafh:
        while True:
            data = datafh.read(DEFAULT_MAX_FRAME_SIZE)

            done = False
            if len(data) == DEFAULT_MAX_FRAME_SIZE:
                flags = FLAG_COMMAND_DATA_CONTINUATION
            else:
                flags = FLAG_COMMAND_DATA_EOS
                assert datafh.read(1) == b''
                done = True

            yield stream.makeframe(
                requestid=requestid,
                typeid=FRAME_TYPE_COMMAND_DATA,
                flags=flags,
                payload=data,
            )

            if done:
                break


def createcommandresponseokframe(stream, requestid):
    overall = b''.join(cborutil.streamencode({b'status': b'ok'}))

    if stream.streamsettingssent:
        overall = stream.encode(overall)
        encoded = True

        if not overall:
            return None
    else:
        encoded = False

    return stream.makeframe(
        requestid=requestid,
        typeid=FRAME_TYPE_COMMAND_RESPONSE,
        flags=FLAG_COMMAND_RESPONSE_CONTINUATION,
        payload=overall,
        encoded=encoded,
    )


def createcommandresponseeosframes(
    stream, requestid, maxframesize=DEFAULT_MAX_FRAME_SIZE
):
    """Create an empty payload frame representing command end-of-stream."""
    payload = stream.flush()

    offset = 0
    while True:
        chunk = payload[offset : offset + maxframesize]
        offset += len(chunk)

        done = offset == len(payload)

        if done:
            flags = FLAG_COMMAND_RESPONSE_EOS
        else:
            flags = FLAG_COMMAND_RESPONSE_CONTINUATION

        yield stream.makeframe(
            requestid=requestid,
            typeid=FRAME_TYPE_COMMAND_RESPONSE,
            flags=flags,
            payload=chunk,
            encoded=payload != b'',
        )

        if done:
            break


def createalternatelocationresponseframe(stream, requestid, location):
    data = {
        b'status': b'redirect',
        b'location': {
            b'url': location.url,
            b'mediatype': location.mediatype,
        },
    }

    for a in (
        'size',
        'fullhashes',
        'fullhashseed',
        'serverdercerts',
        'servercadercerts',
    ):
        value = getattr(location, a)
        if value is not None:
            data[b'location'][pycompat.bytestr(a)] = value

    payload = b''.join(cborutil.streamencode(data))

    if stream.streamsettingssent:
        payload = stream.encode(payload)
        encoded = True
    else:
        encoded = False

    return stream.makeframe(
        requestid=requestid,
        typeid=FRAME_TYPE_COMMAND_RESPONSE,
        flags=FLAG_COMMAND_RESPONSE_CONTINUATION,
        payload=payload,
        encoded=encoded,
    )


def createcommanderrorresponse(stream, requestid, message, args=None):
    # TODO should this be using a list of {'msg': ..., 'args': {}} so atom
    # formatting works consistently?
    m = {
        b'status': b'error',
        b'error': {
            b'message': message,
        },
    }

    if args:
        m[b'error'][b'args'] = args

    overall = b''.join(cborutil.streamencode(m))

    yield stream.makeframe(
        requestid=requestid,
        typeid=FRAME_TYPE_COMMAND_RESPONSE,
        flags=FLAG_COMMAND_RESPONSE_EOS,
        payload=overall,
    )


def createerrorframe(stream, requestid, msg, errtype):
    # TODO properly handle frame size limits.
    assert len(msg) <= DEFAULT_MAX_FRAME_SIZE

    payload = b''.join(
        cborutil.streamencode(
            {
                b'type': errtype,
                b'message': [{b'msg': msg}],
            }
        )
    )

    yield stream.makeframe(
        requestid=requestid,
        typeid=FRAME_TYPE_ERROR_RESPONSE,
        flags=0,
        payload=payload,
    )


def createtextoutputframe(
    stream, requestid, atoms, maxframesize=DEFAULT_MAX_FRAME_SIZE
):
    """Create a text output frame to render text to people.

    ``atoms`` is a 3-tuple of (formatting string, args, labels).

    The formatting string contains ``%s`` tokens to be replaced by the
    corresponding indexed entry in ``args``. ``labels`` is an iterable of
    formatters to be applied at rendering time. In terms of the ``ui``
    class, each atom corresponds to a ``ui.write()``.
    """
    atomdicts = []

    for (formatting, args, labels) in atoms:
        # TODO look for localstr, other types here?

        if not isinstance(formatting, bytes):
            raise ValueError(b'must use bytes formatting strings')
        for arg in args:
            if not isinstance(arg, bytes):
                raise ValueError(b'must use bytes for arguments')
        for label in labels:
            if not isinstance(label, bytes):
                raise ValueError(b'must use bytes for labels')

        # Formatting string must be ASCII.
        formatting = formatting.decode('ascii', 'replace').encode('ascii')

        # Arguments must be UTF-8.
        args = [a.decode('utf-8', 'replace').encode('utf-8') for a in args]

        # Labels must be ASCII.
        labels = [l.decode('ascii', 'strict').encode('ascii') for l in labels]

        atom = {b'msg': formatting}
        if args:
            atom[b'args'] = args
        if labels:
            atom[b'labels'] = labels

        atomdicts.append(atom)

    payload = b''.join(cborutil.streamencode(atomdicts))

    if len(payload) > maxframesize:
        raise ValueError(b'cannot encode data in a single frame')

    yield stream.makeframe(
        requestid=requestid,
        typeid=FRAME_TYPE_TEXT_OUTPUT,
        flags=0,
        payload=payload,
    )


class bufferingcommandresponseemitter(object):
    """Helper object to emit command response frames intelligently.

    Raw command response data is likely emitted in chunks much smaller
    than what can fit in a single frame. This class exists to buffer
    chunks until enough data is available to fit in a single frame.

    TODO we'll need something like this when compression is supported.
    So it might make sense to implement this functionality at the stream
    level.
    """

    def __init__(self, stream, requestid, maxframesize=DEFAULT_MAX_FRAME_SIZE):
        self._stream = stream
        self._requestid = requestid
        self._maxsize = maxframesize
        self._chunks = []
        self._chunkssize = 0

    def send(self, data):
        """Send new data for emission.

        Is a generator of new frames that were derived from the new input.

        If the special input ``None`` is received, flushes all buffered
        data to frames.
        """

        if data is None:
            for frame in self._flush():
                yield frame
            return

        data = self._stream.encode(data)

        # There is a ton of potential to do more complicated things here.
        # Our immediate goal is to coalesce small chunks into big frames,
        # not achieve the fewest number of frames possible. So we go with
        # a simple implementation:
        #
        # * If a chunk is too large for a frame, we flush and emit frames
        #   for the new chunk.
        # * If a chunk can be buffered without total buffered size limits
        #   being exceeded, we do that.
        # * If a chunk causes us to go over our buffering limit, we flush
        #   and then buffer the new chunk.

        if not data:
            return

        if len(data) > self._maxsize:
            for frame in self._flush():
                yield frame

            # Now emit frames for the big chunk.
            offset = 0
            while True:
                chunk = data[offset : offset + self._maxsize]
                offset += len(chunk)

                yield self._stream.makeframe(
                    self._requestid,
                    typeid=FRAME_TYPE_COMMAND_RESPONSE,
                    flags=FLAG_COMMAND_RESPONSE_CONTINUATION,
                    payload=chunk,
                    encoded=True,
                )

                if offset == len(data):
                    return

        # If we don't have enough to constitute a full frame, buffer and
        # return.
        if len(data) + self._chunkssize < self._maxsize:
            self._chunks.append(data)
            self._chunkssize += len(data)
            return

        # Else flush what we have and buffer the new chunk. We could do
        # something more intelligent here, like break the chunk. Let's
        # keep things simple for now.
        for frame in self._flush():
            yield frame

        self._chunks.append(data)
        self._chunkssize = len(data)

    def _flush(self):
        payload = b''.join(self._chunks)
        assert len(payload) <= self._maxsize

        self._chunks[:] = []
        self._chunkssize = 0

        if not payload:
            return

        yield self._stream.makeframe(
            self._requestid,
            typeid=FRAME_TYPE_COMMAND_RESPONSE,
            flags=FLAG_COMMAND_RESPONSE_CONTINUATION,
            payload=payload,
            encoded=True,
        )


# TODO consider defining encoders/decoders using the util.compressionengine
# mechanism.


class identityencoder(object):
    """Encoder for the "identity" stream encoding profile."""

    def __init__(self, ui):
        pass

    def encode(self, data):
        return data

    def flush(self):
        return b''

    def finish(self):
        return b''


class identitydecoder(object):
    """Decoder for the "identity" stream encoding profile."""

    def __init__(self, ui, extraobjs):
        if extraobjs:
            raise error.Abort(
                _(b'identity decoder received unexpected additional values')
            )

    def decode(self, data):
        return data


class zlibencoder(object):
    def __init__(self, ui):
        import zlib

        self._zlib = zlib
        self._compressor = zlib.compressobj()

    def encode(self, data):
        return self._compressor.compress(data)

    def flush(self):
        # Z_SYNC_FLUSH doesn't reset compression context, which is
        # what we want.
        return self._compressor.flush(self._zlib.Z_SYNC_FLUSH)

    def finish(self):
        res = self._compressor.flush(self._zlib.Z_FINISH)
        self._compressor = None
        return res


class zlibdecoder(object):
    def __init__(self, ui, extraobjs):
        import zlib

        if extraobjs:
            raise error.Abort(
                _(b'zlib decoder received unexpected additional values')
            )

        self._decompressor = zlib.decompressobj()

    def decode(self, data):
        # Python 2's zlib module doesn't use the buffer protocol and can't
        # handle all bytes-like types.
        if not pycompat.ispy3 and isinstance(data, bytearray):
            data = bytes(data)

        return self._decompressor.decompress(data)


class zstdbaseencoder(object):
    def __init__(self, level):
        from . import zstd

        self._zstd = zstd
        cctx = zstd.ZstdCompressor(level=level)
        self._compressor = cctx.compressobj()

    def encode(self, data):
        return self._compressor.compress(data)

    def flush(self):
        # COMPRESSOBJ_FLUSH_BLOCK flushes all data previously fed into the
        # compressor and allows a decompressor to access all encoded data
        # up to this point.
        return self._compressor.flush(self._zstd.COMPRESSOBJ_FLUSH_BLOCK)

    def finish(self):
        res = self._compressor.flush(self._zstd.COMPRESSOBJ_FLUSH_FINISH)
        self._compressor = None
        return res


class zstd8mbencoder(zstdbaseencoder):
    def __init__(self, ui):
        super(zstd8mbencoder, self).__init__(3)


class zstdbasedecoder(object):
    def __init__(self, maxwindowsize):
        from . import zstd

        dctx = zstd.ZstdDecompressor(max_window_size=maxwindowsize)
        self._decompressor = dctx.decompressobj()

    def decode(self, data):
        return self._decompressor.decompress(data)


class zstd8mbdecoder(zstdbasedecoder):
    def __init__(self, ui, extraobjs):
        if extraobjs:
            raise error.Abort(
                _(b'zstd8mb decoder received unexpected additional values')
            )

        super(zstd8mbdecoder, self).__init__(maxwindowsize=8 * 1048576)


# We lazily populate this to avoid excessive module imports when importing
# this module.
STREAM_ENCODERS = {}
STREAM_ENCODERS_ORDER = []


def populatestreamencoders():
    if STREAM_ENCODERS:
        return

    try:
        from . import zstd

        zstd.__version__
    except ImportError:
        zstd = None

    # zstandard is fastest and is preferred.
    if zstd:
        STREAM_ENCODERS[b'zstd-8mb'] = (zstd8mbencoder, zstd8mbdecoder)
        STREAM_ENCODERS_ORDER.append(b'zstd-8mb')

    STREAM_ENCODERS[b'zlib'] = (zlibencoder, zlibdecoder)
    STREAM_ENCODERS_ORDER.append(b'zlib')

    STREAM_ENCODERS[b'identity'] = (identityencoder, identitydecoder)
    STREAM_ENCODERS_ORDER.append(b'identity')


class stream(object):
    """Represents a logical unidirectional series of frames."""

    def __init__(self, streamid, active=False):
        self.streamid = streamid
        self._active = active

    def makeframe(self, requestid, typeid, flags, payload):
        """Create a frame to be sent out over this stream.

        Only returns the frame instance. Does not actually send it.
        """
        streamflags = 0
        if not self._active:
            streamflags |= STREAM_FLAG_BEGIN_STREAM
            self._active = True

        return makeframe(
            requestid, self.streamid, streamflags, typeid, flags, payload
        )


class inputstream(stream):
    """Represents a stream used for receiving data."""

    def __init__(self, streamid, active=False):
        super(inputstream, self).__init__(streamid, active=active)
        self._decoder = None

    def setdecoder(self, ui, name, extraobjs):
        """Set the decoder for this stream.

        Receives the stream profile name and any additional CBOR objects
        decoded from the stream encoding settings frame payloads.
        """
        if name not in STREAM_ENCODERS:
            raise error.Abort(_(b'unknown stream decoder: %s') % name)

        self._decoder = STREAM_ENCODERS[name][1](ui, extraobjs)

    def decode(self, data):
        # Default is identity decoder. We don't bother instantiating one
        # because it is trivial.
        if not self._decoder:
            return data

        return self._decoder.decode(data)

    def flush(self):
        if not self._decoder:
            return b''

        return self._decoder.flush()


class outputstream(stream):
    """Represents a stream used for sending data."""

    def __init__(self, streamid, active=False):
        super(outputstream, self).__init__(streamid, active=active)
        self.streamsettingssent = False
        self._encoder = None
        self._encodername = None

    def setencoder(self, ui, name):
        """Set the encoder for this stream.

        Receives the stream profile name.
        """
        if name not in STREAM_ENCODERS:
            raise error.Abort(_(b'unknown stream encoder: %s') % name)

        self._encoder = STREAM_ENCODERS[name][0](ui)
        self._encodername = name

    def encode(self, data):
        if not self._encoder:
            return data

        return self._encoder.encode(data)

    def flush(self):
        if not self._encoder:
            return b''

        return self._encoder.flush()

    def finish(self):
        if not self._encoder:
            return b''

        self._encoder.finish()

    def makeframe(self, requestid, typeid, flags, payload, encoded=False):
        """Create a frame to be sent out over this stream.

        Only returns the frame instance. Does not actually send it.
        """
        streamflags = 0
        if not self._active:
            streamflags |= STREAM_FLAG_BEGIN_STREAM
            self._active = True

        if encoded:
            if not self.streamsettingssent:
                raise error.ProgrammingError(
                    b'attempting to send encoded frame without sending stream '
                    b'settings'
                )

            streamflags |= STREAM_FLAG_ENCODING_APPLIED

        if (
            typeid == FRAME_TYPE_STREAM_SETTINGS
            and flags & FLAG_STREAM_ENCODING_SETTINGS_EOS
        ):
            self.streamsettingssent = True

        return makeframe(
            requestid, self.streamid, streamflags, typeid, flags, payload
        )

    def makestreamsettingsframe(self, requestid):
        """Create a stream settings frame for this stream.

        Returns frame data or None if no stream settings frame is needed or has
        already been sent.
        """
        if not self._encoder or self.streamsettingssent:
            return None

        payload = b''.join(cborutil.streamencode(self._encodername))
        return self.makeframe(
            requestid,
            FRAME_TYPE_STREAM_SETTINGS,
            FLAG_STREAM_ENCODING_SETTINGS_EOS,
            payload,
        )


def ensureserverstream(stream):
    if stream.streamid % 2:
        raise error.ProgrammingError(
            b'server should only write to even '
            b'numbered streams; %d is not even' % stream.streamid
        )


DEFAULT_PROTOCOL_SETTINGS = {
    b'contentencodings': [b'identity'],
}


class serverreactor(object):
    """Holds state of a server handling frame-based protocol requests.

    This class is the "brain" of the unified frame-based protocol server
    component. While the protocol is stateless from the perspective of
    requests/commands, something needs to track which frames have been
    received, what frames to expect, etc. This class is that thing.

    Instances are modeled as a state machine of sorts. Instances are also
    reactionary to external events. The point of this class is to encapsulate
    the state of the connection and the exchange of frames, not to perform
    work. Instead, callers tell this class when something occurs, like a
    frame arriving. If that activity is worthy of a follow-up action (say
    *run a command*), the return value of that handler will say so.

    I/O and CPU intensive operations are purposefully delegated outside of
    this class.

    Consumers are expected to tell instances when events occur. They do so by
    calling the various ``on*`` methods. These methods return a 2-tuple
    describing any follow-up action(s) to take. The first element is the
    name of an action to perform. The second is a data structure (usually
    a dict) specific to that action that contains more information. e.g.
    if the server wants to send frames back to the client, the data structure
    will contain a reference to those frames.

    Valid actions that consumers can be instructed to take are:

    sendframes
       Indicates that frames should be sent to the client. The ``framegen``
       key contains a generator of frames that should be sent. The server
       assumes that all frames are sent to the client.

    error
       Indicates that an error occurred. Consumer should probably abort.

    runcommand
       Indicates that the consumer should run a wire protocol command. Details
       of the command to run are given in the data structure.

    wantframe
       Indicates that nothing of interest happened and the server is waiting on
       more frames from the client before anything interesting can be done.

    noop
       Indicates no additional action is required.

    Known Issues
    ------------

    There are no limits to the number of partially received commands or their
    size. A malicious client could stream command request data and exhaust the
    server's memory.

    Partially received commands are not acted upon when end of input is
    reached. Should the server error if it receives a partial request?
    Should the client send a message to abort a partially transmitted request
    to facilitate graceful shutdown?

    Active requests that haven't been responded to aren't tracked. This means
    that if we receive a command and instruct its dispatch, another command
    with its request ID can come in over the wire and there will be a race
    between who responds to what.
    """

    def __init__(self, ui, deferoutput=False):
        """Construct a new server reactor.

        ``deferoutput`` can be used to indicate that no output frames should be
        instructed to be sent until input has been exhausted. In this mode,
        events that would normally generate output frames (such as a command
        response being ready) will instead defer instructing the consumer to
        send those frames. This is useful for half-duplex transports where the
        sender cannot receive until all data has been transmitted.
        """
        self._ui = ui
        self._deferoutput = deferoutput
        self._state = b'initial'
        self._nextoutgoingstreamid = 2
        self._bufferedframegens = []
        # stream id -> stream instance for all active streams from the client.
        self._incomingstreams = {}
        self._outgoingstreams = {}
        # request id -> dict of commands that are actively being received.
        self._receivingcommands = {}
        # Request IDs that have been received and are actively being processed.
        # Once all output for a request has been sent, it is removed from this
        # set.
        self._activecommands = set()

        self._protocolsettingsdecoder = None

        # Sender protocol settings are optional. Set implied default values.
        self._sendersettings = dict(DEFAULT_PROTOCOL_SETTINGS)

        populatestreamencoders()

    def onframerecv(self, frame):
        """Process a frame that has been received off the wire.

        Returns a dict with an ``action`` key that details what action,
        if any, the consumer should take next.
        """
        if not frame.streamid % 2:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'received frame with even numbered stream ID: %d')
                % frame.streamid
            )

        if frame.streamid not in self._incomingstreams:
            if not frame.streamflags & STREAM_FLAG_BEGIN_STREAM:
                self._state = b'errored'
                return self._makeerrorresult(
                    _(
                        b'received frame on unknown inactive stream without '
                        b'beginning of stream flag set'
                    )
                )

            self._incomingstreams[frame.streamid] = inputstream(frame.streamid)

        if frame.streamflags & STREAM_FLAG_ENCODING_APPLIED:
            # TODO handle decoding frames
            self._state = b'errored'
            raise error.ProgrammingError(
                b'support for decoding stream payloads not yet implemented'
            )

        if frame.streamflags & STREAM_FLAG_END_STREAM:
            del self._incomingstreams[frame.streamid]

        handlers = {
            b'initial': self._onframeinitial,
            b'protocol-settings-receiving': self._onframeprotocolsettings,
            b'idle': self._onframeidle,
            b'command-receiving': self._onframecommandreceiving,
            b'errored': self._onframeerrored,
        }

        meth = handlers.get(self._state)
        if not meth:
            raise error.ProgrammingError(b'unhandled state: %s' % self._state)

        return meth(frame)

    def oncommandresponsereadyobjects(self, stream, requestid, objs):
        """Signal that objects are ready to be sent to the client.

        ``objs`` is an iterable of objects (typically a generator) that will
        be encoded via CBOR and added to frames, which will be sent to the
        client.
        """
        ensureserverstream(stream)

        # A more robust solution would be to check for objs.{next,__next__}.
        if isinstance(objs, list):
            objs = iter(objs)

        # We need to take care over exception handling. Uncaught exceptions
        # when generating frames could lead to premature end of the frame
        # stream and the possibility of the server or client process getting
        # in a bad state.
        #
        # Keep in mind that if ``objs`` is a generator, advancing it could
        # raise exceptions that originated in e.g. wire protocol command
        # functions. That is why we differentiate between exceptions raised
        # when iterating versus other exceptions that occur.
        #
        # In all cases, when the function finishes, the request is fully
        # handled and no new frames for it should be seen.

        def sendframes():
            emitted = False
            alternatelocationsent = False
            emitter = bufferingcommandresponseemitter(stream, requestid)
            while True:
                try:
                    o = next(objs)
                except StopIteration:
                    for frame in emitter.send(None):
                        yield frame

                    if emitted:
                        for frame in createcommandresponseeosframes(
                            stream, requestid
                        ):
                            yield frame
                    break

                except error.WireprotoCommandError as e:
                    for frame in createcommanderrorresponse(
                        stream, requestid, e.message, e.messageargs
                    ):
                        yield frame
                    break

                except Exception as e:
                    for frame in createerrorframe(
                        stream,
                        requestid,
                        b'%s' % stringutil.forcebytestr(e),
                        errtype=b'server',
                    ):

                        yield frame

                    break

                try:
                    # Alternate location responses can only be the first and
                    # only object in the output stream.
                    if isinstance(o, wireprototypes.alternatelocationresponse):
                        if emitted:
                            raise error.ProgrammingError(
                                b'alternatelocationresponse seen after initial '
                                b'output object'
                            )

                        frame = stream.makestreamsettingsframe(requestid)
                        if frame:
                            yield frame

                        yield createalternatelocationresponseframe(
                            stream, requestid, o
                        )

                        alternatelocationsent = True
                        emitted = True
                        continue

                    if alternatelocationsent:
                        raise error.ProgrammingError(
                            b'object follows alternatelocationresponse'
                        )

                    if not emitted:
                        # Frame is optional.
                        frame = stream.makestreamsettingsframe(requestid)
                        if frame:
                            yield frame

                        # May be None if empty frame (due to encoding).
                        frame = createcommandresponseokframe(stream, requestid)
                        if frame:
                            yield frame

                        emitted = True

                    # Objects emitted by command functions can be serializable
                    # data structures or special types.
                    # TODO consider extracting the content normalization to a
                    # standalone function, as it may be useful for e.g. cachers.

                    # A pre-encoded object is sent directly to the emitter.
                    if isinstance(o, wireprototypes.encodedresponse):
                        for frame in emitter.send(o.data):
                            yield frame

                    elif isinstance(
                        o, wireprototypes.indefinitebytestringresponse
                    ):
                        for chunk in cborutil.streamencodebytestringfromiter(
                            o.chunks
                        ):

                            for frame in emitter.send(chunk):
                                yield frame

                    # A regular object is CBOR encoded.
                    else:
                        for chunk in cborutil.streamencode(o):
                            for frame in emitter.send(chunk):
                                yield frame

                except Exception as e:
                    for frame in createerrorframe(
                        stream, requestid, b'%s' % e, errtype=b'server'
                    ):
                        yield frame

                    break

            self._activecommands.remove(requestid)

        return self._handlesendframes(sendframes())

    def oninputeof(self):
        """Signals that end of input has been received.

        No more frames will be received. All pending activity should be
        completed.
        """
        # TODO should we do anything about in-flight commands?

        if not self._deferoutput or not self._bufferedframegens:
            return b'noop', {}

        # If we buffered all our responses, emit those.
        def makegen():
            for gen in self._bufferedframegens:
                for frame in gen:
                    yield frame

        return b'sendframes', {
            b'framegen': makegen(),
        }

    def _handlesendframes(self, framegen):
        if self._deferoutput:
            self._bufferedframegens.append(framegen)
            return b'noop', {}
        else:
            return b'sendframes', {
                b'framegen': framegen,
            }

    def onservererror(self, stream, requestid, msg):
        ensureserverstream(stream)

        def sendframes():
            for frame in createerrorframe(
                stream, requestid, msg, errtype=b'server'
            ):
                yield frame

            self._activecommands.remove(requestid)

        return self._handlesendframes(sendframes())

    def oncommanderror(self, stream, requestid, message, args=None):
        """Called when a command encountered an error before sending output."""
        ensureserverstream(stream)

        def sendframes():
            for frame in createcommanderrorresponse(
                stream, requestid, message, args
            ):
                yield frame

            self._activecommands.remove(requestid)

        return self._handlesendframes(sendframes())

    def makeoutputstream(self):
        """Create a stream to be used for sending data to the client.

        If this is called before protocol settings frames are received, we
        don't know what stream encodings are supported by the client and
        we will default to identity.
        """
        streamid = self._nextoutgoingstreamid
        self._nextoutgoingstreamid += 2

        s = outputstream(streamid)
        self._outgoingstreams[streamid] = s

        # Always use the *server's* preferred encoder over the client's,
        # as servers have more to lose from sub-optimal encoders being used.
        for name in STREAM_ENCODERS_ORDER:
            if name in self._sendersettings[b'contentencodings']:
                s.setencoder(self._ui, name)
                break

        return s

    def _makeerrorresult(self, msg):
        return b'error', {
            b'message': msg,
        }

    def _makeruncommandresult(self, requestid):
        entry = self._receivingcommands[requestid]

        if not entry[b'requestdone']:
            self._state = b'errored'
            raise error.ProgrammingError(
                b'should not be called without requestdone set'
            )

        del self._receivingcommands[requestid]

        if self._receivingcommands:
            self._state = b'command-receiving'
        else:
            self._state = b'idle'

        # Decode the payloads as CBOR.
        entry[b'payload'].seek(0)
        request = cborutil.decodeall(entry[b'payload'].getvalue())[0]

        if b'name' not in request:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'command request missing "name" field')
            )

        if b'args' not in request:
            request[b'args'] = {}

        assert requestid not in self._activecommands
        self._activecommands.add(requestid)

        return (
            b'runcommand',
            {
                b'requestid': requestid,
                b'command': request[b'name'],
                b'args': request[b'args'],
                b'redirect': request.get(b'redirect'),
                b'data': entry[b'data'].getvalue() if entry[b'data'] else None,
            },
        )

    def _makewantframeresult(self):
        return b'wantframe', {
            b'state': self._state,
        }

    def _validatecommandrequestframe(self, frame):
        new = frame.flags & FLAG_COMMAND_REQUEST_NEW
        continuation = frame.flags & FLAG_COMMAND_REQUEST_CONTINUATION

        if new and continuation:
            self._state = b'errored'
            return self._makeerrorresult(
                _(
                    b'received command request frame with both new and '
                    b'continuation flags set'
                )
            )

        if not new and not continuation:
            self._state = b'errored'
            return self._makeerrorresult(
                _(
                    b'received command request frame with neither new nor '
                    b'continuation flags set'
                )
            )

    def _onframeinitial(self, frame):
        # Called when we receive a frame when in the "initial" state.
        if frame.typeid == FRAME_TYPE_SENDER_PROTOCOL_SETTINGS:
            self._state = b'protocol-settings-receiving'
            self._protocolsettingsdecoder = cborutil.bufferingdecoder()
            return self._onframeprotocolsettings(frame)

        elif frame.typeid == FRAME_TYPE_COMMAND_REQUEST:
            self._state = b'idle'
            return self._onframeidle(frame)

        else:
            self._state = b'errored'
            return self._makeerrorresult(
                _(
                    b'expected sender protocol settings or command request '
                    b'frame; got %d'
                )
                % frame.typeid
            )

    def _onframeprotocolsettings(self, frame):
        assert self._state == b'protocol-settings-receiving'
        assert self._protocolsettingsdecoder is not None

        if frame.typeid != FRAME_TYPE_SENDER_PROTOCOL_SETTINGS:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'expected sender protocol settings frame; got %d')
                % frame.typeid
            )

        more = frame.flags & FLAG_SENDER_PROTOCOL_SETTINGS_CONTINUATION
        eos = frame.flags & FLAG_SENDER_PROTOCOL_SETTINGS_EOS

        if more and eos:
            self._state = b'errored'
            return self._makeerrorresult(
                _(
                    b'sender protocol settings frame cannot have both '
                    b'continuation and end of stream flags set'
                )
            )

        if not more and not eos:
            self._state = b'errored'
            return self._makeerrorresult(
                _(
                    b'sender protocol settings frame must have continuation or '
                    b'end of stream flag set'
                )
            )

        # TODO establish limits for maximum amount of data that can be
        # buffered.
        try:
            self._protocolsettingsdecoder.decode(frame.payload)
        except Exception as e:
            self._state = b'errored'
            return self._makeerrorresult(
                _(
                    b'error decoding CBOR from sender protocol settings frame: %s'
                )
                % stringutil.forcebytestr(e)
            )

        if more:
            return self._makewantframeresult()

        assert eos

        decoded = self._protocolsettingsdecoder.getavailable()
        self._protocolsettingsdecoder = None

        if not decoded:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'sender protocol settings frame did not contain CBOR data')
            )
        elif len(decoded) > 1:
            self._state = b'errored'
            return self._makeerrorresult(
                _(
                    b'sender protocol settings frame contained multiple CBOR '
                    b'values'
                )
            )

        d = decoded[0]

        if b'contentencodings' in d:
            self._sendersettings[b'contentencodings'] = d[b'contentencodings']

        self._state = b'idle'

        return self._makewantframeresult()

    def _onframeidle(self, frame):
        # The only frame type that should be received in this state is a
        # command request.
        if frame.typeid != FRAME_TYPE_COMMAND_REQUEST:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'expected command request frame; got %d') % frame.typeid
            )

        res = self._validatecommandrequestframe(frame)
        if res:
            return res

        if frame.requestid in self._receivingcommands:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'request with ID %d already received') % frame.requestid
            )

        if frame.requestid in self._activecommands:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'request with ID %d is already active') % frame.requestid
            )

        new = frame.flags & FLAG_COMMAND_REQUEST_NEW
        moreframes = frame.flags & FLAG_COMMAND_REQUEST_MORE_FRAMES
        expectingdata = frame.flags & FLAG_COMMAND_REQUEST_EXPECT_DATA

        if not new:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'received command request frame without new flag set')
            )

        payload = util.bytesio()
        payload.write(frame.payload)

        self._receivingcommands[frame.requestid] = {
            b'payload': payload,
            b'data': None,
            b'requestdone': not moreframes,
            b'expectingdata': bool(expectingdata),
        }

        # This is the final frame for this request. Dispatch it.
        if not moreframes and not expectingdata:
            return self._makeruncommandresult(frame.requestid)

        assert moreframes or expectingdata
        self._state = b'command-receiving'
        return self._makewantframeresult()

    def _onframecommandreceiving(self, frame):
        if frame.typeid == FRAME_TYPE_COMMAND_REQUEST:
            # Process new command requests as such.
            if frame.flags & FLAG_COMMAND_REQUEST_NEW:
                return self._onframeidle(frame)

            res = self._validatecommandrequestframe(frame)
            if res:
                return res

        # All other frames should be related to a command that is currently
        # receiving but is not active.
        if frame.requestid in self._activecommands:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'received frame for request that is still active: %d')
                % frame.requestid
            )

        if frame.requestid not in self._receivingcommands:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'received frame for request that is not receiving: %d')
                % frame.requestid
            )

        entry = self._receivingcommands[frame.requestid]

        if frame.typeid == FRAME_TYPE_COMMAND_REQUEST:
            moreframes = frame.flags & FLAG_COMMAND_REQUEST_MORE_FRAMES
            expectingdata = bool(frame.flags & FLAG_COMMAND_REQUEST_EXPECT_DATA)

            if entry[b'requestdone']:
                self._state = b'errored'
                return self._makeerrorresult(
                    _(
                        b'received command request frame when request frames '
                        b'were supposedly done'
                    )
                )

            if expectingdata != entry[b'expectingdata']:
                self._state = b'errored'
                return self._makeerrorresult(
                    _(b'mismatch between expect data flag and previous frame')
                )

            entry[b'payload'].write(frame.payload)

            if not moreframes:
                entry[b'requestdone'] = True

            if not moreframes and not expectingdata:
                return self._makeruncommandresult(frame.requestid)

            return self._makewantframeresult()

        elif frame.typeid == FRAME_TYPE_COMMAND_DATA:
            if not entry[b'expectingdata']:
                self._state = b'errored'
                return self._makeerrorresult(
                    _(
                        b'received command data frame for request that is not '
                        b'expecting data: %d'
                    )
                    % frame.requestid
                )

            if entry[b'data'] is None:
                entry[b'data'] = util.bytesio()

            return self._handlecommanddataframe(frame, entry)
        else:
            self._state = b'errored'
            return self._makeerrorresult(
                _(b'received unexpected frame type: %d') % frame.typeid
            )

    def _handlecommanddataframe(self, frame, entry):
        assert frame.typeid == FRAME_TYPE_COMMAND_DATA

        # TODO support streaming data instead of buffering it.
        entry[b'data'].write(frame.payload)

        if frame.flags & FLAG_COMMAND_DATA_CONTINUATION:
            return self._makewantframeresult()
        elif frame.flags & FLAG_COMMAND_DATA_EOS:
            entry[b'data'].seek(0)
            return self._makeruncommandresult(frame.requestid)
        else:
            self._state = b'errored'
            return self._makeerrorresult(_(b'command data frame without flags'))

    def _onframeerrored(self, frame):
        return self._makeerrorresult(_(b'server already errored'))


class commandrequest(object):
    """Represents a request to run a command."""

    def __init__(self, requestid, name, args, datafh=None, redirect=None):
        self.requestid = requestid
        self.name = name
        self.args = args
        self.datafh = datafh
        self.redirect = redirect
        self.state = b'pending'


class clientreactor(object):
    """Holds state of a client issuing frame-based protocol requests.

    This is like ``serverreactor`` but for client-side state.

    Each instance is bound to the lifetime of a connection. For persistent
    connection transports using e.g. TCP sockets and speaking the raw
    framing protocol, there will be a single instance for the lifetime of
    the TCP socket. For transports where there are multiple discrete
    interactions (say tunneled within in HTTP request), there will be a
    separate instance for each distinct interaction.

    Consumers are expected to tell instances when events occur by calling
    various methods. These methods return a 2-tuple describing any follow-up
    action(s) to take. The first element is the name of an action to
    perform. The second is a data structure (usually a dict) specific to
    that action that contains more information. e.g. if the reactor wants
    to send frames to the server, the data structure will contain a reference
    to those frames.

    Valid actions that consumers can be instructed to take are:

    noop
       Indicates no additional action is required.

    sendframes
       Indicates that frames should be sent to the server. The ``framegen``
       key contains a generator of frames that should be sent. The reactor
       assumes that all frames in this generator are sent to the server.

    error
       Indicates that an error occurred. The ``message`` key contains an
       error message describing the failure.

    responsedata
       Indicates a response to a previously-issued command was received.

       The ``request`` key contains the ``commandrequest`` instance that
       represents the request this data is for.

       The ``data`` key contains the decoded data from the server.

       ``expectmore`` and ``eos`` evaluate to True when more response data
       is expected to follow or we're at the end of the response stream,
       respectively.
    """

    def __init__(
        self,
        ui,
        hasmultiplesend=False,
        buffersends=True,
        clientcontentencoders=None,
    ):
        """Create a new instance.

        ``hasmultiplesend`` indicates whether multiple sends are supported
        by the transport. When True, it is possible to send commands immediately
        instead of buffering until the caller signals an intent to finish a
        send operation.

        ``buffercommands`` indicates whether sends should be buffered until the
        last request has been issued.

        ``clientcontentencoders`` is an iterable of content encoders the client
        will advertise to the server and that the server can use for encoding
        data. If not defined, the client will not advertise content encoders
        to the server.
        """
        self._ui = ui
        self._hasmultiplesend = hasmultiplesend
        self._buffersends = buffersends
        self._clientcontentencoders = clientcontentencoders

        self._canissuecommands = True
        self._cansend = True
        self._protocolsettingssent = False

        self._nextrequestid = 1
        # We only support a single outgoing stream for now.
        self._outgoingstream = outputstream(1)
        self._pendingrequests = collections.deque()
        self._activerequests = {}
        self._incomingstreams = {}
        self._streamsettingsdecoders = {}

        populatestreamencoders()

    def callcommand(self, name, args, datafh=None, redirect=None):
        """Request that a command be executed.

        Receives the command name, a dict of arguments to pass to the command,
        and an optional file object containing the raw data for the command.

        Returns a 3-tuple of (request, action, action data).
        """
        if not self._canissuecommands:
            raise error.ProgrammingError(b'cannot issue new commands')

        requestid = self._nextrequestid
        self._nextrequestid += 2

        request = commandrequest(
            requestid, name, args, datafh=datafh, redirect=redirect
        )

        if self._buffersends:
            self._pendingrequests.append(request)
            return request, b'noop', {}
        else:
            if not self._cansend:
                raise error.ProgrammingError(
                    b'sends cannot be performed on this instance'
                )

            if not self._hasmultiplesend:
                self._cansend = False
                self._canissuecommands = False

            return (
                request,
                b'sendframes',
                {
                    b'framegen': self._makecommandframes(request),
                },
            )

    def flushcommands(self):
        """Request that all queued commands be sent.

        If any commands are buffered, this will instruct the caller to send
        them over the wire. If no commands are buffered it instructs the client
        to no-op.

        If instances aren't configured for multiple sends, no new command
        requests are allowed after this is called.
        """
        if not self._pendingrequests:
            return b'noop', {}

        if not self._cansend:
            raise error.ProgrammingError(
                b'sends cannot be performed on this instance'
            )

        # If the instance only allows sending once, mark that we have fired
        # our one shot.
        if not self._hasmultiplesend:
            self._canissuecommands = False
            self._cansend = False

        def makeframes():
            while self._pendingrequests:
                request = self._pendingrequests.popleft()
                for frame in self._makecommandframes(request):
                    yield frame

        return b'sendframes', {
            b'framegen': makeframes(),
        }

    def _makecommandframes(self, request):
        """Emit frames to issue a command request.

        As a side-effect, update request accounting to reflect its changed
        state.
        """
        self._activerequests[request.requestid] = request
        request.state = b'sending'

        if not self._protocolsettingssent and self._clientcontentencoders:
            self._protocolsettingssent = True

            payload = b''.join(
                cborutil.streamencode(
                    {
                        b'contentencodings': self._clientcontentencoders,
                    }
                )
            )

            yield self._outgoingstream.makeframe(
                requestid=request.requestid,
                typeid=FRAME_TYPE_SENDER_PROTOCOL_SETTINGS,
                flags=FLAG_SENDER_PROTOCOL_SETTINGS_EOS,
                payload=payload,
            )

        res = createcommandframes(
            self._outgoingstream,
            request.requestid,
            request.name,
            request.args,
            datafh=request.datafh,
            redirect=request.redirect,
        )

        for frame in res:
            yield frame

        request.state = b'sent'

    def onframerecv(self, frame):
        """Process a frame that has been received off the wire.

        Returns a 2-tuple of (action, meta) describing further action the
        caller needs to take as a result of receiving this frame.
        """
        if frame.streamid % 2:
            return (
                b'error',
                {
                    b'message': (
                        _(b'received frame with odd numbered stream ID: %d')
                        % frame.streamid
                    ),
                },
            )

        if frame.streamid not in self._incomingstreams:
            if not frame.streamflags & STREAM_FLAG_BEGIN_STREAM:
                return (
                    b'error',
                    {
                        b'message': _(
                            b'received frame on unknown stream '
                            b'without beginning of stream flag set'
                        ),
                    },
                )

            self._incomingstreams[frame.streamid] = inputstream(frame.streamid)

        stream = self._incomingstreams[frame.streamid]

        # If the payload is encoded, ask the stream to decode it. We
        # merely substitute the decoded result into the frame payload as
        # if it had been transferred all along.
        if frame.streamflags & STREAM_FLAG_ENCODING_APPLIED:
            frame.payload = stream.decode(frame.payload)

        if frame.streamflags & STREAM_FLAG_END_STREAM:
            del self._incomingstreams[frame.streamid]

        if frame.typeid == FRAME_TYPE_STREAM_SETTINGS:
            return self._onstreamsettingsframe(frame)

        if frame.requestid not in self._activerequests:
            return (
                b'error',
                {
                    b'message': (
                        _(b'received frame for inactive request ID: %d')
                        % frame.requestid
                    ),
                },
            )

        request = self._activerequests[frame.requestid]
        request.state = b'receiving'

        handlers = {
            FRAME_TYPE_COMMAND_RESPONSE: self._oncommandresponseframe,
            FRAME_TYPE_ERROR_RESPONSE: self._onerrorresponseframe,
        }

        meth = handlers.get(frame.typeid)
        if not meth:
            raise error.ProgrammingError(
                b'unhandled frame type: %d' % frame.typeid
            )

        return meth(request, frame)

    def _onstreamsettingsframe(self, frame):
        assert frame.typeid == FRAME_TYPE_STREAM_SETTINGS

        more = frame.flags & FLAG_STREAM_ENCODING_SETTINGS_CONTINUATION
        eos = frame.flags & FLAG_STREAM_ENCODING_SETTINGS_EOS

        if more and eos:
            return (
                b'error',
                {
                    b'message': (
                        _(
                            b'stream encoding settings frame cannot have both '
                            b'continuation and end of stream flags set'
                        )
                    ),
                },
            )

        if not more and not eos:
            return (
                b'error',
                {
                    b'message': _(
                        b'stream encoding settings frame must have '
                        b'continuation or end of stream flag set'
                    ),
                },
            )

        if frame.streamid not in self._streamsettingsdecoders:
            decoder = cborutil.bufferingdecoder()
            self._streamsettingsdecoders[frame.streamid] = decoder

        decoder = self._streamsettingsdecoders[frame.streamid]

        try:
            decoder.decode(frame.payload)
        except Exception as e:
            return (
                b'error',
                {
                    b'message': (
                        _(
                            b'error decoding CBOR from stream encoding '
                            b'settings frame: %s'
                        )
                        % stringutil.forcebytestr(e)
                    ),
                },
            )

        if more:
            return b'noop', {}

        assert eos

        decoded = decoder.getavailable()
        del self._streamsettingsdecoders[frame.streamid]

        if not decoded:
            return (
                b'error',
                {
                    b'message': _(
                        b'stream encoding settings frame did not contain '
                        b'CBOR data'
                    ),
                },
            )

        try:
            self._incomingstreams[frame.streamid].setdecoder(
                self._ui, decoded[0], decoded[1:]
            )
        except Exception as e:
            return (
                b'error',
                {
                    b'message': (
                        _(b'error setting stream decoder: %s')
                        % stringutil.forcebytestr(e)
                    ),
                },
            )

        return b'noop', {}

    def _oncommandresponseframe(self, request, frame):
        if frame.flags & FLAG_COMMAND_RESPONSE_EOS:
            request.state = b'received'
            del self._activerequests[request.requestid]

        return (
            b'responsedata',
            {
                b'request': request,
                b'expectmore': frame.flags & FLAG_COMMAND_RESPONSE_CONTINUATION,
                b'eos': frame.flags & FLAG_COMMAND_RESPONSE_EOS,
                b'data': frame.payload,
            },
        )

    def _onerrorresponseframe(self, request, frame):
        request.state = b'errored'
        del self._activerequests[request.requestid]

        # The payload should be a CBOR map.
        m = cborutil.decodeall(frame.payload)[0]

        return (
            b'error',
            {
                b'request': request,
                b'type': m[b'type'],
                b'message': m[b'message'],
            },
        )
