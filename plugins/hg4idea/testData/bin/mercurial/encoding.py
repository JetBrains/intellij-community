# encoding.py - character transcoding support for Mercurial
#
#  Copyright 2005-2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import error
import sys, unicodedata, locale, os

_encodingfixup = {'646': 'ascii', 'ANSI_X3.4-1968': 'ascii'}

try:
    encoding = os.environ.get("HGENCODING")
    if sys.platform == 'darwin' and not encoding:
        # On darwin, getpreferredencoding ignores the locale environment and
        # always returns mac-roman. We override this if the environment is
        # not C (has been customized by the user).
        lc = locale.setlocale(locale.LC_CTYPE, '')
        if lc == 'UTF-8':
            locale.setlocale(locale.LC_CTYPE, 'en_US.UTF-8')
        encoding = locale.getlocale()[1]
    if not encoding:
        encoding = locale.getpreferredencoding() or 'ascii'
        encoding = _encodingfixup.get(encoding, encoding)
except locale.Error:
    encoding = 'ascii'
encodingmode = os.environ.get("HGENCODINGMODE", "strict")
fallbackencoding = 'ISO-8859-1'

def tolocal(s):
    """
    Convert a string from internal UTF-8 to local encoding

    All internal strings should be UTF-8 but some repos before the
    implementation of locale support may contain latin1 or possibly
    other character sets. We attempt to decode everything strictly
    using UTF-8, then Latin-1, and failing that, we use UTF-8 and
    replace unknown characters.
    """
    for e in ('UTF-8', fallbackencoding):
        try:
            u = s.decode(e) # attempt strict decoding
            return u.encode(encoding, "replace")
        except LookupError, k:
            raise error.Abort("%s, please check your locale settings" % k)
        except UnicodeDecodeError:
            pass
    u = s.decode("utf-8", "replace") # last ditch
    return u.encode(encoding, "replace")

def fromlocal(s):
    """
    Convert a string from the local character encoding to UTF-8

    We attempt to decode strings using the encoding mode set by
    HGENCODINGMODE, which defaults to 'strict'. In this mode, unknown
    characters will cause an error message. Other modes include
    'replace', which replaces unknown characters with a special
    Unicode character, and 'ignore', which drops the character.
    """
    try:
        return s.decode(encoding, encodingmode).encode("utf-8")
    except UnicodeDecodeError, inst:
        sub = s[max(0, inst.start - 10):inst.start + 10]
        raise error.Abort("decoding near '%s': %s!" % (sub, inst))
    except LookupError, k:
        raise error.Abort("%s, please check your locale settings" % k)

def colwidth(s):
    "Find the column width of a UTF-8 string for display"
    d = s.decode(encoding, 'replace')
    if hasattr(unicodedata, 'east_asian_width'):
        w = unicodedata.east_asian_width
        return sum([w(c) in 'WF' and 2 or 1 for c in d])
    return len(d)

