import unittest

# picked from test-parse-index2, copied rather than imported
# so that it stays stable even if test-parse-index2 changes or disappears.
data_non_inlined = (
    b'\x00\x00\x00\x01\x00\x00\x00\x00\x00\x01D\x19'
    b'\x00\x07e\x12\x00\x00\x00\x00\x00\x00\x00\x00\xff\xff\xff\xff'
    b'\xff\xff\xff\xff\xd1\xf4\xbb\xb0\xbe\xfc\x13\xbd\x8c\xd3\x9d'
    b'\x0f\xcd\xd9;\x8c\x07\x8cJ/\x00\x00\x00\x00\x00\x00\x00\x00\x00'
    b'\x00\x00\x00\x00\x00\x00\x01D\x19\x00\x00\x00\x00\x00\xdf\x00'
    b'\x00\x01q\x00\x00\x00\x01\x00\x00\x00\x01\x00\x00\x00\x00\xff'
    b'\xff\xff\xff\xc1\x12\xb9\x04\x96\xa4Z1t\x91\xdfsJ\x90\xf0\x9bh'
    b'\x07l&\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00'
    b'\x00\x01D\xf8\x00\x00\x00\x00\x01\x1b\x00\x00\x01\xb8\x00\x00'
    b'\x00\x01\x00\x00\x00\x02\x00\x00\x00\x01\xff\xff\xff\xff\x02\n'
    b'\x0e\xc6&\xa1\x92\xae6\x0b\x02i\xfe-\xe5\xbao\x05\xd1\xe7\x00'
    b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x01F'
    b'\x13\x00\x00\x00\x00\x01\xec\x00\x00\x03\x06\x00\x00\x00\x01'
    b'\x00\x00\x00\x03\x00\x00\x00\x02\xff\xff\xff\xff\x12\xcb\xeby1'
    b'\xb6\r\x98B\xcb\x07\xbd`\x8f\x92\xd9\xc4\x84\xbdK\x00\x00\x00'
    b'\x00\x00\x00\x00\x00\x00\x00\x00\x00'
)

from ..revlogutils.constants import REVLOGV1


try:
    from ..cext import parsers as cparsers  # pytype: disable=import-error
except ImportError:
    cparsers = None

try:
    from ..rustext.revlog import (  # pytype: disable=import-error
        Index as RustIndex,
    )
except ImportError:
    RustIndex = None


@unittest.skipIf(
    cparsers is None,
    'The C version of the "parsers" module is not available. It is needed for this test.',
)
class RevlogBasedTestBase(unittest.TestCase):
    def parseindex(self, data=None):
        if data is None:
            data = data_non_inlined
        return cparsers.parse_index2(data, False)[0]


@unittest.skipIf(
    RustIndex is None,
    'The Rust index is not available. It is needed for this test.',
)
class RustRevlogBasedTestBase(unittest.TestCase):
    def parserustindex(self, data=None):
        if data is None:
            data = data_non_inlined
        # not inheriting RevlogBasedTestCase to avoid having a
        # `parseindex` method that would be shadowed by future subclasses
        # this duplication will soon be removed
        return RustIndex(data, REVLOGV1)
