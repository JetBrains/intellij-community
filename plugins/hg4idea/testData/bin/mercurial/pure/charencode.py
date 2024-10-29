# charencode.py - miscellaneous character encoding
#
#  Copyright 2005-2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import array

from .. import pycompat


def isasciistr(s):
    try:
        s.decode('ascii')
        return True
    except UnicodeDecodeError:
        return False


def asciilower(s):
    """convert a string to lowercase if ASCII

    Raises UnicodeDecodeError if non-ASCII characters are found."""
    s.decode('ascii')
    return s.lower()


def asciiupper(s):
    """convert a string to uppercase if ASCII

    Raises UnicodeDecodeError if non-ASCII characters are found."""
    s.decode('ascii')
    return s.upper()


_jsonmap = []
_jsonmap.extend(b"\\u%04x" % x for x in range(32))
_jsonmap.extend(pycompat.bytechr(x) for x in range(32, 127))
_jsonmap.append(b'\\u007f')
_jsonmap[0x09] = b'\\t'
_jsonmap[0x0A] = b'\\n'
_jsonmap[0x22] = b'\\"'
_jsonmap[0x5C] = b'\\\\'
_jsonmap[0x08] = b'\\b'
_jsonmap[0x0C] = b'\\f'
_jsonmap[0x0D] = b'\\r'
_paranoidjsonmap = _jsonmap[:]
_paranoidjsonmap[0x3C] = b'\\u003c'  # '<' (e.g. escape "</script>")
_paranoidjsonmap[0x3E] = b'\\u003e'  # '>'
_jsonmap.extend(pycompat.bytechr(x) for x in range(128, 256))


def jsonescapeu8fast(u8chars, paranoid):
    """Convert a UTF-8 byte string to JSON-escaped form (fast path)

    Raises ValueError if non-ASCII characters have to be escaped.
    """
    if paranoid:
        jm = _paranoidjsonmap
    else:
        jm = _jsonmap
    try:
        return b''.join(jm[x] for x in bytearray(u8chars))
    except IndexError:
        raise ValueError


_utf8strict = r'surrogatepass'


def jsonescapeu8fallback(u8chars, paranoid):
    """Convert a UTF-8 byte string to JSON-escaped form (slow path)

    Escapes all non-ASCII characters no matter if paranoid is False.
    """
    if paranoid:
        jm = _paranoidjsonmap
    else:
        jm = _jsonmap
    # non-BMP char is represented as UTF-16 surrogate pair
    u16b = u8chars.decode('utf-8', _utf8strict).encode('utf-16', _utf8strict)
    u16codes = array.array('H', u16b)
    u16codes.pop(0)  # drop BOM
    return b''.join(jm[x] if x < 128 else b'\\u%04x' % x for x in u16codes)
