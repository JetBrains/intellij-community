import array
import errno
import fcntl
import os
import sys
import typing

from typing import (
    List,
    Tuple,
)

from . import (
    encoding,
    pycompat,
    util,
)

if typing.TYPE_CHECKING:
    from . import ui as uimod

# BSD 'more' escapes ANSI color sequences by default. This can be disabled by
# $MORE variable, but there's no compatible option with Linux 'more'. Given
# OS X is widely used and most modern Unix systems would have 'less', setting
# 'less' as the default seems reasonable.
fallbackpager = b'less'


def _rcfiles(path: bytes) -> List[bytes]:
    rcs = [os.path.join(path, b'hgrc')]
    rcdir = os.path.join(path, b'hgrc.d')
    try:
        rcs.extend(
            [
                os.path.join(rcdir, f)
                for f, kind in sorted(util.listdir(rcdir))
                if f.endswith(b".rc")
            ]
        )
    except OSError:
        pass
    return rcs


def systemrcpath() -> List[bytes]:
    path = []
    if pycompat.sysplatform == b'plan9':
        root = b'lib/mercurial'
    else:
        root = b'etc/mercurial'
    # old mod_python does not set sys.argv
    if len(getattr(sys, 'argv', [])) > 0:
        p = os.path.dirname(os.path.dirname(pycompat.sysargv[0]))
        if p != b'/':
            path.extend(_rcfiles(os.path.join(p, root)))
    path.extend(_rcfiles(b'/' + root))
    return path


def userrcpath() -> List[bytes]:
    if pycompat.sysplatform == b'plan9':
        return [encoding.environ[b'home'] + b'/lib/hgrc']
    else:
        confighome = encoding.environ.get(b'XDG_CONFIG_HOME')
        if confighome is None or not os.path.isabs(confighome):
            confighome = os.path.expanduser(b'~/.config')

        return [
            os.path.expanduser(b'~/.hgrc'),
            os.path.join(confighome, b'hg', b'hgrc'),
        ]


def termsize(ui: "uimod.ui") -> Tuple[int, int]:
    try:
        import termios

        TIOCGWINSZ = termios.TIOCGWINSZ  # unavailable on IRIX (issue3449)
    except (AttributeError, ImportError):
        return 80, 24

    for dev in (ui.ferr, ui.fout, ui.fin):
        try:
            try:
                fd = dev.fileno()
            except AttributeError:
                continue
            if not os.isatty(fd):
                continue
            arri = fcntl.ioctl(fd, TIOCGWINSZ, b'\0' * 8)
            height, width = array.array('h', arri)[:2]
            if width > 0 and height > 0:
                return width, height
        except ValueError:
            pass
        except IOError as e:
            if e.errno == errno.EINVAL:
                pass
            else:
                raise
    return 80, 24
