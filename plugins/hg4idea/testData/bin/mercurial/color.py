# utility for color output for Mercurial commands
#
# Copyright (C) 2007 Kevin Christen <kevin.christen@gmail.com> and other
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import re

from .i18n import _
from .pycompat import getattr

from . import (
    encoding,
    pycompat,
)

from .utils import stringutil

try:
    import curses

    # Mapping from effect name to terminfo attribute name (or raw code) or
    # color number.  This will also force-load the curses module.
    _baseterminfoparams = {
        b'none': (True, b'sgr0', b''),
        b'standout': (True, b'smso', b''),
        b'underline': (True, b'smul', b''),
        b'reverse': (True, b'rev', b''),
        b'inverse': (True, b'rev', b''),
        b'blink': (True, b'blink', b''),
        b'dim': (True, b'dim', b''),
        b'bold': (True, b'bold', b''),
        b'invisible': (True, b'invis', b''),
        b'italic': (True, b'sitm', b''),
        b'black': (False, curses.COLOR_BLACK, b''),
        b'red': (False, curses.COLOR_RED, b''),
        b'green': (False, curses.COLOR_GREEN, b''),
        b'yellow': (False, curses.COLOR_YELLOW, b''),
        b'blue': (False, curses.COLOR_BLUE, b''),
        b'magenta': (False, curses.COLOR_MAGENTA, b''),
        b'cyan': (False, curses.COLOR_CYAN, b''),
        b'white': (False, curses.COLOR_WHITE, b''),
    }
except (ImportError, AttributeError):
    curses = None
    _baseterminfoparams = {}

# start and stop parameters for effects
_effects = {
    b'none': 0,
    b'black': 30,
    b'red': 31,
    b'green': 32,
    b'yellow': 33,
    b'blue': 34,
    b'magenta': 35,
    b'cyan': 36,
    b'white': 37,
    b'bold': 1,
    b'italic': 3,
    b'underline': 4,
    b'inverse': 7,
    b'dim': 2,
    b'black_background': 40,
    b'red_background': 41,
    b'green_background': 42,
    b'yellow_background': 43,
    b'blue_background': 44,
    b'purple_background': 45,
    b'cyan_background': 46,
    b'white_background': 47,
}

_defaultstyles = {
    b'grep.match': b'red bold',
    b'grep.linenumber': b'green',
    b'grep.rev': b'blue',
    b'grep.sep': b'cyan',
    b'grep.filename': b'magenta',
    b'grep.user': b'magenta',
    b'grep.date': b'magenta',
    b'grep.inserted': b'green bold',
    b'grep.deleted': b'red bold',
    b'bookmarks.active': b'green',
    b'branches.active': b'none',
    b'branches.closed': b'black bold',
    b'branches.current': b'green',
    b'branches.inactive': b'none',
    b'diff.changed': b'white',
    b'diff.deleted': b'red',
    b'diff.deleted.changed': b'red bold underline',
    b'diff.deleted.unchanged': b'red',
    b'diff.diffline': b'bold',
    b'diff.extended': b'cyan bold',
    b'diff.file_a': b'red bold',
    b'diff.file_b': b'green bold',
    b'diff.hunk': b'magenta',
    b'diff.inserted': b'green',
    b'diff.inserted.changed': b'green bold underline',
    b'diff.inserted.unchanged': b'green',
    b'diff.tab': b'',
    b'diff.trailingwhitespace': b'bold red_background',
    b'changeset.public': b'',
    b'changeset.draft': b'',
    b'changeset.secret': b'',
    b'diffstat.deleted': b'red',
    b'diffstat.inserted': b'green',
    b'formatvariant.name.mismatchconfig': b'red',
    b'formatvariant.name.mismatchdefault': b'yellow',
    b'formatvariant.name.uptodate': b'green',
    b'formatvariant.repo.mismatchconfig': b'red',
    b'formatvariant.repo.mismatchdefault': b'yellow',
    b'formatvariant.repo.uptodate': b'green',
    b'formatvariant.config.special': b'yellow',
    b'formatvariant.config.default': b'green',
    b'formatvariant.default': b'',
    b'histedit.remaining': b'red bold',
    b'ui.addremove.added': b'green',
    b'ui.addremove.removed': b'red',
    b'ui.error': b'red',
    b'ui.prompt': b'yellow',
    b'log.changeset': b'yellow',
    b'patchbomb.finalsummary': b'',
    b'patchbomb.from': b'magenta',
    b'patchbomb.to': b'cyan',
    b'patchbomb.subject': b'green',
    b'patchbomb.diffstats': b'',
    b'rebase.rebased': b'blue',
    b'rebase.remaining': b'red bold',
    b'resolve.resolved': b'green bold',
    b'resolve.unresolved': b'red bold',
    b'shelve.age': b'cyan',
    b'shelve.newest': b'green bold',
    b'shelve.name': b'blue bold',
    b'status.added': b'green bold',
    b'status.clean': b'none',
    b'status.copied': b'none',
    b'status.deleted': b'cyan bold underline',
    b'status.ignored': b'black bold',
    b'status.modified': b'blue bold',
    b'status.removed': b'red bold',
    b'status.unknown': b'magenta bold underline',
    b'tags.normal': b'green',
    b'tags.local': b'black bold',
    b'upgrade-repo.requirement.preserved': b'cyan',
    b'upgrade-repo.requirement.added': b'green',
    b'upgrade-repo.requirement.removed': b'red',
}


def loadcolortable(ui, extname, colortable):
    _defaultstyles.update(colortable)


def _terminfosetup(ui, mode, formatted):
    '''Initialize terminfo data and the terminal if we're in terminfo mode.'''

    # If we failed to load curses, we go ahead and return.
    if curses is None:
        return
    # Otherwise, see what the config file says.
    if mode not in (b'auto', b'terminfo'):
        return
    ui._terminfoparams.update(_baseterminfoparams)

    for key, val in ui.configitems(b'color'):
        if key.startswith(b'color.'):
            newval = (False, int(val), b'')
            ui._terminfoparams[key[6:]] = newval
        elif key.startswith(b'terminfo.'):
            newval = (True, b'', val.replace(b'\\E', b'\x1b'))
            ui._terminfoparams[key[9:]] = newval
    try:
        curses.setupterm()
    except curses.error:
        ui._terminfoparams.clear()
        return

    for key, (b, e, c) in ui._terminfoparams.copy().items():
        if not b:
            continue
        if not c and not curses.tigetstr(pycompat.sysstr(e)):
            # Most terminals don't support dim, invis, etc, so don't be
            # noisy and use ui.debug().
            ui.debug(b"no terminfo entry for %s\n" % e)
            del ui._terminfoparams[key]
    if not curses.tigetstr('setaf') or not curses.tigetstr('setab'):
        # Only warn about missing terminfo entries if we explicitly asked for
        # terminfo mode and we're in a formatted terminal.
        if mode == b"terminfo" and formatted:
            ui.warn(
                _(
                    b"no terminfo entry for setab/setaf: reverting to "
                    b"ECMA-48 color\n"
                )
            )
        ui._terminfoparams.clear()


def setup(ui):
    """configure color on a ui

    That function both set the colormode for the ui object and read
    the configuration looking for custom colors and effect definitions."""
    mode = _modesetup(ui)
    ui._colormode = mode
    if mode and mode != b'debug':
        configstyles(ui)


def _modesetup(ui):
    if ui.plain(b'color'):
        return None
    config = ui.config(b'ui', b'color')
    if config == b'debug':
        return b'debug'

    auto = config == b'auto'
    always = False
    if not auto and stringutil.parsebool(config):
        # We want the config to behave like a boolean, "on" is actually auto,
        # but "always" value is treated as a special case to reduce confusion.
        if (
            ui.configsource(b'ui', b'color') == b'--color'
            or config == b'always'
        ):
            always = True
        else:
            auto = True

    if not always and not auto:
        return None

    formatted = always or (
        encoding.environ.get(b'TERM') != b'dumb' and ui.formatted()
    )

    mode = ui.config(b'color', b'mode')

    # If pager is active, color.pagermode overrides color.mode.
    if getattr(ui, 'pageractive', False):
        mode = ui.config(b'color', b'pagermode', mode)

    realmode = mode
    if pycompat.iswindows:
        from . import win32

        term = encoding.environ.get(b'TERM')
        # TERM won't be defined in a vanilla cmd.exe environment.

        # UNIX-like environments on Windows such as Cygwin and MSYS will
        # set TERM. They appear to make a best effort attempt at setting it
        # to something appropriate. However, not all environments with TERM
        # defined support ANSI.
        ansienviron = term and b'xterm' in term

        if mode == b'auto':
            # Since "ansi" could result in terminal gibberish, we error on the
            # side of selecting "win32". However, if w32effects is not defined,
            # we almost certainly don't support "win32", so don't even try.
            # w32effects is not populated when stdout is redirected, so checking
            # it first avoids win32 calls in a state known to error out.
            if ansienviron or not w32effects or win32.enablevtmode():
                realmode = b'ansi'
            else:
                realmode = b'win32'
        # An empty w32effects is a clue that stdout is redirected, and thus
        # cannot enable VT mode.
        elif mode == b'ansi' and w32effects and not ansienviron:
            win32.enablevtmode()
    elif mode == b'auto':
        realmode = b'ansi'

    def modewarn():
        # only warn if color.mode was explicitly set and we're in
        # a formatted terminal
        if mode == realmode and formatted:
            ui.warn(_(b'warning: failed to set color mode to %s\n') % mode)

    if realmode == b'win32':
        ui._terminfoparams.clear()
        if not w32effects:
            modewarn()
            return None
    elif realmode == b'ansi':
        ui._terminfoparams.clear()
    elif realmode == b'terminfo':
        _terminfosetup(ui, mode, formatted)
        if not ui._terminfoparams:
            ## FIXME Shouldn't we return None in this case too?
            modewarn()
            realmode = b'ansi'
    else:
        return None

    if always or (auto and formatted):
        return realmode
    return None


def configstyles(ui):
    ui._styles.update(_defaultstyles)
    for status, cfgeffects in ui.configitems(b'color'):
        if b'.' not in status or status.startswith((b'color.', b'terminfo.')):
            continue
        cfgeffects = ui.configlist(b'color', status)
        if cfgeffects:
            good = []
            for e in cfgeffects:
                if valideffect(ui, e):
                    good.append(e)
                else:
                    ui.warn(
                        _(
                            b"ignoring unknown color/effect %s "
                            b"(configured in color.%s)\n"
                        )
                        % (stringutil.pprint(e), status)
                    )
            ui._styles[status] = b' '.join(good)


def _activeeffects(ui):
    '''Return the effects map for the color mode set on the ui.'''
    if ui._colormode == b'win32':
        return w32effects
    elif ui._colormode is not None:
        return _effects
    return {}


def valideffect(ui, effect):
    """Determine if the effect is valid or not."""
    return (not ui._terminfoparams and effect in _activeeffects(ui)) or (
        effect in ui._terminfoparams or effect[:-11] in ui._terminfoparams
    )


def _effect_str(ui, effect):
    '''Helper function for render_effects().'''

    bg = False
    if effect.endswith(b'_background'):
        bg = True
        effect = effect[:-11]
    try:
        attr, val, termcode = ui._terminfoparams[effect]
    except KeyError:
        return b''
    if attr:
        if termcode:
            return termcode
        else:
            return curses.tigetstr(pycompat.sysstr(val))
    elif bg:
        return curses.tparm(curses.tigetstr('setab'), val)
    else:
        return curses.tparm(curses.tigetstr('setaf'), val)


def _mergeeffects(text, start, stop):
    """Insert start sequence at every occurrence of stop sequence

    >>> s = _mergeeffects(b'cyan', b'[C]', b'|')
    >>> s = _mergeeffects(s + b'yellow', b'[Y]', b'|')
    >>> s = _mergeeffects(b'ma' + s + b'genta', b'[M]', b'|')
    >>> s = _mergeeffects(b'red' + s, b'[R]', b'|')
    >>> s
    '[R]red[M]ma[Y][C]cyan|[R][M][Y]yellow|[R][M]genta|'
    """
    parts = []
    for t in text.split(stop):
        if not t:
            continue
        parts.extend([start, t, stop])
    return b''.join(parts)


def _render_effects(ui, text, effects):
    """Wrap text in commands to turn on each effect."""
    if not text:
        return text
    if ui._terminfoparams:
        start = b''.join(
            _effect_str(ui, effect) for effect in [b'none'] + effects.split()
        )
        stop = _effect_str(ui, b'none')
    else:
        activeeffects = _activeeffects(ui)
        start = [
            pycompat.bytestr(activeeffects[e])
            for e in [b'none'] + effects.split()
        ]
        start = b'\033[' + b';'.join(start) + b'm'
        stop = b'\033[' + pycompat.bytestr(activeeffects[b'none']) + b'm'
    return _mergeeffects(text, start, stop)


_ansieffectre = re.compile(br'\x1b\[[0-9;]*m')


def stripeffects(text):
    """Strip ANSI control codes which could be inserted by colorlabel()"""
    return _ansieffectre.sub(b'', text)


def colorlabel(ui, msg, label):
    """add color control code according to the mode"""
    if ui._colormode == b'debug':
        if label and msg:
            if msg.endswith(b'\n'):
                msg = b"[%s|%s]\n" % (label, msg[:-1])
            else:
                msg = b"[%s|%s]" % (label, msg)
    elif ui._colormode is not None:
        effects = []
        for l in label.split():
            s = ui._styles.get(l, b'')
            if s:
                effects.append(s)
            elif valideffect(ui, l):
                effects.append(l)
        effects = b' '.join(effects)
        if effects:
            msg = b'\n'.join(
                [
                    _render_effects(ui, line, effects)
                    for line in msg.split(b'\n')
                ]
            )
    return msg


w32effects = None
if pycompat.iswindows:
    import ctypes

    _kernel32 = ctypes.windll.kernel32  # pytype: disable=module-attr

    _WORD = ctypes.c_ushort

    _INVALID_HANDLE_VALUE = -1

    class _COORD(ctypes.Structure):
        _fields_ = [('X', ctypes.c_short), ('Y', ctypes.c_short)]

    class _SMALL_RECT(ctypes.Structure):
        _fields_ = [
            ('Left', ctypes.c_short),
            ('Top', ctypes.c_short),
            ('Right', ctypes.c_short),
            ('Bottom', ctypes.c_short),
        ]

    class _CONSOLE_SCREEN_BUFFER_INFO(ctypes.Structure):
        _fields_ = [
            ('dwSize', _COORD),
            ('dwCursorPosition', _COORD),
            ('wAttributes', _WORD),
            ('srWindow', _SMALL_RECT),
            ('dwMaximumWindowSize', _COORD),
        ]

    _STD_OUTPUT_HANDLE = 0xFFFFFFF5  # (DWORD)-11
    _STD_ERROR_HANDLE = 0xFFFFFFF4  # (DWORD)-12

    _FOREGROUND_BLUE = 0x0001
    _FOREGROUND_GREEN = 0x0002
    _FOREGROUND_RED = 0x0004
    _FOREGROUND_INTENSITY = 0x0008

    _BACKGROUND_BLUE = 0x0010
    _BACKGROUND_GREEN = 0x0020
    _BACKGROUND_RED = 0x0040
    _BACKGROUND_INTENSITY = 0x0080

    _COMMON_LVB_REVERSE_VIDEO = 0x4000
    _COMMON_LVB_UNDERSCORE = 0x8000

    # http://msdn.microsoft.com/en-us/library/ms682088%28VS.85%29.aspx
    w32effects = {
        b'none': -1,
        b'black': 0,
        b'red': _FOREGROUND_RED,
        b'green': _FOREGROUND_GREEN,
        b'yellow': _FOREGROUND_RED | _FOREGROUND_GREEN,
        b'blue': _FOREGROUND_BLUE,
        b'magenta': _FOREGROUND_BLUE | _FOREGROUND_RED,
        b'cyan': _FOREGROUND_BLUE | _FOREGROUND_GREEN,
        b'white': _FOREGROUND_RED | _FOREGROUND_GREEN | _FOREGROUND_BLUE,
        b'bold': _FOREGROUND_INTENSITY,
        b'black_background': 0x100,  # unused value > 0x0f
        b'red_background': _BACKGROUND_RED,
        b'green_background': _BACKGROUND_GREEN,
        b'yellow_background': _BACKGROUND_RED | _BACKGROUND_GREEN,
        b'blue_background': _BACKGROUND_BLUE,
        b'purple_background': _BACKGROUND_BLUE | _BACKGROUND_RED,
        b'cyan_background': _BACKGROUND_BLUE | _BACKGROUND_GREEN,
        b'white_background': (
            _BACKGROUND_RED | _BACKGROUND_GREEN | _BACKGROUND_BLUE
        ),
        b'bold_background': _BACKGROUND_INTENSITY,
        b'underline': _COMMON_LVB_UNDERSCORE,  # double-byte charsets only
        b'inverse': _COMMON_LVB_REVERSE_VIDEO,  # double-byte charsets only
    }

    passthrough = {
        _FOREGROUND_INTENSITY,
        _BACKGROUND_INTENSITY,
        _COMMON_LVB_UNDERSCORE,
        _COMMON_LVB_REVERSE_VIDEO,
    }

    stdout = _kernel32.GetStdHandle(
        _STD_OUTPUT_HANDLE
    )  # don't close the handle returned
    if stdout is None or stdout == _INVALID_HANDLE_VALUE:
        w32effects = None
    else:
        csbi = _CONSOLE_SCREEN_BUFFER_INFO()
        if not _kernel32.GetConsoleScreenBufferInfo(stdout, ctypes.byref(csbi)):
            # stdout may not support GetConsoleScreenBufferInfo()
            # when called from subprocess or redirected
            w32effects = None
        else:
            origattr = csbi.wAttributes
            ansire = re.compile(
                br'\033\[([^m]*)m([^\033]*)(.*)', re.MULTILINE | re.DOTALL
            )

    def win32print(ui, writefunc, text, **opts):
        label = opts.get('label', b'')
        attr = origattr

        def mapcolor(val, attr):
            if val == -1:
                return origattr
            elif val in passthrough:
                return attr | val
            elif val > 0x0F:
                return (val & 0x70) | (attr & 0x8F)
            else:
                return (val & 0x07) | (attr & 0xF8)

        # determine console attributes based on labels
        for l in label.split():
            style = ui._styles.get(l, b'')
            for effect in style.split():
                try:
                    attr = mapcolor(w32effects[effect], attr)
                except KeyError:
                    # w32effects could not have certain attributes so we skip
                    # them if not found
                    pass
        # hack to ensure regexp finds data
        if not text.startswith(b'\033['):
            text = b'\033[m' + text

        # Look for ANSI-like codes embedded in text
        m = re.match(ansire, text)

        try:
            while m:
                for sattr in m.group(1).split(b';'):
                    if sattr:
                        attr = mapcolor(int(sattr), attr)
                ui.flush()
                _kernel32.SetConsoleTextAttribute(stdout, attr)
                writefunc(m.group(2))
                m = re.match(ansire, m.group(3))
        finally:
            # Explicitly reset original attributes
            ui.flush()
            _kernel32.SetConsoleTextAttribute(stdout, origattr)
