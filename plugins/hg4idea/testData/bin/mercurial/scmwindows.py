from __future__ import absolute_import

import os

from . import (
    encoding,
    pycompat,
    util,
    win32,
)

try:
    import _winreg as winreg  # pytype: disable=import-error

    winreg.CloseKey
except ImportError:
    # py2 only
    import winreg  # pytype: disable=import-error

# MS-DOS 'more' is the only pager available by default on Windows.
fallbackpager = b'more'


def systemrcpath():
    '''return default os-specific hgrc search path'''
    rcpath = []
    filename = win32.executablepath()
    # Use mercurial.ini found in directory with hg.exe
    progrc = os.path.join(os.path.dirname(filename), b'mercurial.ini')
    rcpath.append(progrc)

    def _processdir(progrcd):
        if os.path.isdir(progrcd):
            for f, kind in sorted(util.listdir(progrcd)):
                if f.endswith(b'.rc'):
                    rcpath.append(os.path.join(progrcd, f))

    # Use hgrc.d found in directory with hg.exe
    _processdir(os.path.join(os.path.dirname(filename), b'hgrc.d'))

    # treat a PROGRAMDATA directory as equivalent to /etc/mercurial
    programdata = encoding.environ.get(b'PROGRAMDATA')
    if programdata:
        programdata = os.path.join(programdata, b'Mercurial')
        _processdir(os.path.join(programdata, b'hgrc.d'))

        ini = os.path.join(programdata, b'mercurial.ini')
        if os.path.isfile(ini):
            rcpath.append(ini)

        ini = os.path.join(programdata, b'hgrc')
        if os.path.isfile(ini):
            rcpath.append(ini)

    # next look for a system rcpath in the registry
    value = util.lookupreg(
        b'SOFTWARE\\Mercurial', None, winreg.HKEY_LOCAL_MACHINE
    )
    if value and isinstance(value, bytes):
        value = util.localpath(value)
        for p in value.split(pycompat.ospathsep):
            if p.lower().endswith(b'mercurial.ini'):
                rcpath.append(p)
            else:
                _processdir(p)
    return rcpath


def userrcpath():
    '''return os-specific hgrc search path to the user dir'''
    home = _legacy_expanduser(b'~')
    path = [os.path.join(home, b'mercurial.ini'), os.path.join(home, b'.hgrc')]
    userprofile = encoding.environ.get(b'USERPROFILE')
    if userprofile and userprofile != home:
        path.append(os.path.join(userprofile, b'mercurial.ini'))
        path.append(os.path.join(userprofile, b'.hgrc'))
    return path


def _legacy_expanduser(path):
    """Expand ~ and ~user constructs in the pre 3.8 style"""

    # Python 3.8+ changed the expansion of '~' from HOME to USERPROFILE.  See
    # https://bugs.python.org/issue36264.  It also seems to capitalize the drive
    # letter, as though it was processed through os.path.realpath().
    if not path.startswith(b'~'):
        return path

    i, n = 1, len(path)
    while i < n and path[i] not in b'\\/':
        i += 1

    if b'HOME' in encoding.environ:
        userhome = encoding.environ[b'HOME']
    elif b'USERPROFILE' in encoding.environ:
        userhome = encoding.environ[b'USERPROFILE']
    elif b'HOMEPATH' not in encoding.environ:
        return path
    else:
        try:
            drive = encoding.environ[b'HOMEDRIVE']
        except KeyError:
            drive = b''
        userhome = os.path.join(drive, encoding.environ[b'HOMEPATH'])

    if i != 1:  # ~user
        userhome = os.path.join(os.path.dirname(userhome), path[1:i])

    return userhome + path[i:]


def termsize(ui):
    return win32.termsize()
