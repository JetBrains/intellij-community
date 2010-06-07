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


By default, win32mbcs uses encoding.encoding decided by Mercurial.
You can specify the encoding by config option::

 [win32mbcs]
 encoding = sjis

It is useful for the users who want to commit with UTF-8 log message.
'''

import os, sys
from mercurial.i18n import _
from mercurial import util, encoding

_encoding = None                                # see reposetup()

def decode(arg):
    if isinstance(arg, str):
        uarg = arg.decode(_encoding)
        if arg == uarg.encode(_encoding):
            return uarg
        raise UnicodeError("Not local encoding")
    elif isinstance(arg, tuple):
        return tuple(map(decode, arg))
    elif isinstance(arg, list):
        return map(decode, arg)
    elif isinstance(arg, dict):
        for k, v in arg.items():
            arg[k] = decode(v)
    return arg

def encode(arg):
    if isinstance(arg, unicode):
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
    if us and us[-1] not in ':/\\':
        s += os.sep
    return s

def wrapper(func, args, kwds):
    # check argument is unicode, then call original
    for arg in args:
        if isinstance(arg, unicode):
            return func(*args, **kwds)

    try:
        # convert arguments to unicode, call func, then convert back
        return encode(func(*decode(args), **decode(kwds)))
    except UnicodeError:
        raise util.Abort(_("[win32mbcs] filename conversion failed with"
                         " %s encoding\n") % (_encoding))

def wrapperforlistdir(func, args, kwds):
    # Ensure 'path' argument ends with os.sep to avoids
    # misinterpreting last 0x5c of MBCS 2nd byte as path separator.
    if args:
        args = list(args)
        args[0] = appendsep(args[0])
    if 'path' in kwds:
        kwds['path'] = appendsep(kwds['path'])
    return func(*args, **kwds)

def wrapname(name, wrapper):
    module, name = name.rsplit('.', 1)
    module = sys.modules[module]
    func = getattr(module, name)
    def f(*args, **kwds):
        return wrapper(func, args, kwds)
    try:
        f.__name__ = func.__name__                # fail with python23
    except Exception:
        pass
    setattr(module, name, f)

# List of functions to be wrapped.
# NOTE: os.path.dirname() and os.path.basename() are safe because
#       they use result of os.path.split()
funcs = '''os.path.join os.path.split os.path.splitext
 os.path.splitunc os.path.normpath os.path.normcase os.makedirs
 mercurial.util.endswithsep mercurial.util.splitpath mercurial.util.checkcase
 mercurial.util.fspath mercurial.util.pconvert mercurial.util.normpath'''

# codec and alias names of sjis and big5 to be faked.
problematic_encodings = '''big5 big5-tw csbig5 big5hkscs big5-hkscs
 hkscs cp932 932 ms932 mskanji ms-kanji shift_jis csshiftjis shiftjis
 sjis s_jis shift_jis_2004 shiftjis2004 sjis_2004 sjis2004
 shift_jisx0213 shiftjisx0213 sjisx0213 s_jisx0213 950 cp950 ms950 '''

def reposetup(ui, repo):
    # TODO: decide use of config section for this extension
    if not os.path.supports_unicode_filenames:
        ui.warn(_("[win32mbcs] cannot activate on this platform.\n"))
        return
    # determine encoding for filename
    global _encoding
    _encoding = ui.config('win32mbcs', 'encoding', encoding.encoding)
    # fake is only for relevant environment.
    if _encoding.lower() in problematic_encodings.split():
        for f in funcs.split():
            wrapname(f, wrapper)
        wrapname("mercurial.osutil.listdir", wrapperforlistdir)
        ui.debug("[win32mbcs] activated with encoding: %s\n"
                 % _encoding)

