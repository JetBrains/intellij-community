# win32mbcs.py -- MBCS filename support for Mercurial
#
# Copyright (c) 2008 Shun-ichi Goto <shunichi.goto@gmail.com>
#
# Version: 0.3
# Author:  Shun-ichi Goto <shunichi.goto@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
#

'''allow the use of MBCS paths with problematic encodings

Some MBCS encodings are not good for some path operations (i.e.
splitting path, case conversion, etc.) with its encoded bytes. We call
such a encoding (i.e. shift_jis and big5) as "problematic encoding".
This extension can be used to fix the issue with those encodings by
wrapping some functions to convert to Unicode string before path
operation.

This extension is useful for:

- Japanese Windows users using shift_jis encoding.
- Chinese Windows users using big5 encoding.
- All users who use a repository with one of problematic encodings on
  case-insensitive file system.

This extension is not needed for:

- Any user who use only ASCII chars in path.
- Any user who do not use any of problematic encodings.

Note that there are some limitations on using this extension:

- You should use single encoding in one repository.
- If the repository path ends with 0x5c, .hg/hgrc cannot be read.
- win32mbcs is not compatible with fixutf8 extension.

By default, win32mbcs uses encoding.encoding decided by Mercurial.
You can specify the encoding by config option::

 [win32mbcs]
 encoding = sjis

It is useful for the users who want to commit with UTF-8 log message.
'''
from __future__ import absolute_import

import os
import sys

from mercurial.i18n import _
from mercurial.pycompat import getattr, setattr
from mercurial import (
    encoding,
    error,
    pycompat,
    registrar,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

configtable = {}
configitem = registrar.configitem(configtable)

# Encoding.encoding may be updated by --encoding option.
# Use a lambda do delay the resolution.
configitem(
    b'win32mbcs',
    b'encoding',
    default=lambda: encoding.encoding,
)

_encoding = None  # see extsetup


def decode(arg):
    if isinstance(arg, bytes):
        uarg = arg.decode(_encoding)
        if arg == uarg.encode(_encoding):
            return uarg
        raise UnicodeError(b"Not local encoding")
    elif isinstance(arg, tuple):
        return tuple(map(decode, arg))
    elif isinstance(arg, list):
        return map(decode, arg)
    elif isinstance(arg, dict):
        for k, v in arg.items():
            arg[k] = decode(v)
    return arg


def encode(arg):
    if isinstance(arg, pycompat.unicode):
        return arg.encode(_encoding)
    elif isinstance(arg, tuple):
        return tuple(map(encode, arg))
    elif isinstance(arg, list):
        return map(encode, arg)
    elif isinstance(arg, dict):
        for k, v in arg.items():
            arg[k] = encode(v)
    return arg


def appendsep(s):
    # ensure the path ends with os.sep, appending it if necessary.
    try:
        us = decode(s)
    except UnicodeError:
        us = s
    if us and us[-1] not in b':/\\':
        s += pycompat.ossep
    return s


def basewrapper(func, argtype, enc, dec, args, kwds):
    # check check already converted, then call original
    for arg in args:
        if isinstance(arg, argtype):
            return func(*args, **kwds)

    try:
        # convert string arguments, call func, then convert back the
        # return value.
        return enc(func(*dec(args), **dec(kwds)))
    except UnicodeError:
        raise error.Abort(
            _(b"[win32mbcs] filename conversion failed with %s encoding\n")
            % _encoding
        )


def wrapper(func, args, kwds):
    return basewrapper(func, pycompat.unicode, encode, decode, args, kwds)


def reversewrapper(func, args, kwds):
    return basewrapper(func, str, decode, encode, args, kwds)


def wrapperforlistdir(func, args, kwds):
    # Ensure 'path' argument ends with os.sep to avoids
    # misinterpreting last 0x5c of MBCS 2nd byte as path separator.
    if args:
        args = list(args)
        args[0] = appendsep(args[0])
    if b'path' in kwds:
        kwds[b'path'] = appendsep(kwds[b'path'])
    return func(*args, **kwds)


def wrapname(name, wrapper):
    module, name = name.rsplit(b'.', 1)
    module = sys.modules[module]
    func = getattr(module, name)

    def f(*args, **kwds):
        return wrapper(func, args, kwds)

    f.__name__ = func.__name__
    setattr(module, name, f)


# List of functions to be wrapped.
# NOTE: os.path.dirname() and os.path.basename() are safe because
#       they use result of os.path.split()
funcs = b'''os.path.join os.path.split os.path.splitext
 os.path.normpath os.makedirs mercurial.util.endswithsep
 mercurial.util.splitpath mercurial.util.fscasesensitive
 mercurial.util.fspath mercurial.util.pconvert mercurial.util.normpath
 mercurial.util.checkwinfilename mercurial.util.checkosfilename
 mercurial.util.split'''

# These functions are required to be called with local encoded string
# because they expects argument is local encoded string and cause
# problem with unicode string.
rfuncs = b'''mercurial.encoding.upper mercurial.encoding.lower
 mercurial.util._filenamebytestr'''

# List of Windows specific functions to be wrapped.
winfuncs = b'''os.path.splitunc'''

# codec and alias names of sjis and big5 to be faked.
problematic_encodings = b'''big5 big5-tw csbig5 big5hkscs big5-hkscs
 hkscs cp932 932 ms932 mskanji ms-kanji shift_jis csshiftjis shiftjis
 sjis s_jis shift_jis_2004 shiftjis2004 sjis_2004 sjis2004
 shift_jisx0213 shiftjisx0213 sjisx0213 s_jisx0213 950 cp950 ms950 '''


def extsetup(ui):
    # TODO: decide use of config section for this extension
    if (not os.path.supports_unicode_filenames) and (
        pycompat.sysplatform != b'cygwin'
    ):
        ui.warn(_(b"[win32mbcs] cannot activate on this platform.\n"))
        return
    # determine encoding for filename
    global _encoding
    _encoding = ui.config(b'win32mbcs', b'encoding')
    # fake is only for relevant environment.
    if _encoding.lower() in problematic_encodings.split():
        for f in funcs.split():
            wrapname(f, wrapper)
        if pycompat.iswindows:
            for f in winfuncs.split():
                wrapname(f, wrapper)
        wrapname(b"mercurial.util.listdir", wrapperforlistdir)
        wrapname(b"mercurial.windows.listdir", wrapperforlistdir)
        # wrap functions to be called with local byte string arguments
        for f in rfuncs.split():
            wrapname(f, reversewrapper)
        # Check sys.args manually instead of using ui.debug() because
        # command line options is not yet applied when
        # extensions.loadall() is called.
        if b'--debug' in sys.argv:
            ui.writenoi18n(
                b"[win32mbcs] activated with encoding: %s\n" % _encoding
            )
