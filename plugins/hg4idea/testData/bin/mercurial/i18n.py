# i18n.py - internationalization support for mercurial
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import gettext as gettextmod
import locale
import os
import sys

from typing import (
    List,
)

from .utils import resourceutil
from . import (
    encoding,
    pycompat,
)

# modelled after templater.templatepath:
if getattr(sys, 'frozen', None) is not None:
    module = pycompat.sysexecutable
else:
    module = pycompat.fsencode(__file__)

_languages = None
if (
    pycompat.iswindows
    and b'LANGUAGE' not in encoding.environ
    and b'LC_ALL' not in encoding.environ
    and b'LC_MESSAGES' not in encoding.environ
    and b'LANG' not in encoding.environ
):
    # Try to detect UI language by "User Interface Language Management" API
    # if no locale variables are set. Note that locale.getdefaultlocale()
    # uses GetLocaleInfo(), which may be different from UI language.
    # (See http://msdn.microsoft.com/en-us/library/dd374098(v=VS.85).aspx )
    try:
        import ctypes

        # pytype: disable=module-attr
        langid = ctypes.windll.kernel32.GetUserDefaultUILanguage()
        # pytype: enable=module-attr

        _languages = [locale.windows_locale[langid]]
    except (ImportError, AttributeError, KeyError):
        # ctypes not found or unknown langid
        pass


datapath = pycompat.fsdecode(resourceutil.datapath)
localedir = os.path.join(datapath, 'locale')
t = gettextmod.translation('hg', localedir, _languages, fallback=True)
try:
    _ugettext = t.ugettext  # pytype: disable=attribute-error
except AttributeError:
    _ugettext = t.gettext


_msgcache = {}  # encoding: {message: translation}


def gettext(message: bytes) -> bytes:
    """Translate message.

    The message is looked up in the catalog to get a Unicode string,
    which is encoded in the local encoding before being returned.

    Important: message is restricted to characters in the encoding
    given by sys.getdefaultencoding() which is most likely 'ascii'.
    """
    # If message is None, t.ugettext will return u'None' as the
    # translation whereas our callers expect us to return None.
    if message is None or not _ugettext:
        return message

    cache = _msgcache.setdefault(encoding.encoding, {})
    if message not in cache:
        if type(message) is str:
            # goofy unicode docstrings in test
            paragraphs: List[str] = message.split(u'\n\n')
        else:
            # should be ascii, but we have unicode docstrings in test, which
            # are converted to utf-8 bytes on Python 3.
            paragraphs = [p.decode("utf-8") for p in message.split(b'\n\n')]
        # Be careful not to translate the empty string -- it holds the
        # meta data of the .po file.
        u = u'\n\n'.join([p and _ugettext(p) or u'' for p in paragraphs])
        try:
            # encoding.tolocal cannot be used since it will first try to
            # decode the Unicode string. Calling u.decode(enc) really
            # means u.encode(sys.getdefaultencoding()).decode(enc). Since
            # the Python encoding defaults to 'ascii', this fails if the
            # translated string use non-ASCII characters.
            encodingstr = pycompat.sysstr(encoding.encoding)
            cache[message] = u.encode(encodingstr, "replace")
        except LookupError:
            # An unknown encoding results in a LookupError.
            cache[message] = message
    return cache[message]


def _plain():
    if (
        b'HGPLAIN' not in encoding.environ
        and b'HGPLAINEXCEPT' not in encoding.environ
    ):
        return False
    exceptions = encoding.environ.get(b'HGPLAINEXCEPT', b'').strip().split(b',')
    return b'i18n' not in exceptions


if _plain():

    def _(message: bytes) -> bytes:
        return message


else:
    _ = gettext
