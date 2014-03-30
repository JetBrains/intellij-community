# color.py color output for the status and qseries commands
#
# Copyright (C) 2007 Kevin Christen <kevin.christen@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''colorize output from some commands

This extension modifies the status and resolve commands to add color
to their output to reflect file status, the qseries command to add
color to reflect patch status (applied, unapplied, missing), and to
diff-related commands to highlight additions, removals, diff headers,
and trailing whitespace.

Other effects in addition to color, like bold and underlined text, are
also available. By default, the terminfo database is used to find the
terminal codes used to change color and effect.  If terminfo is not
available, then effects are rendered with the ECMA-48 SGR control
function (aka ANSI escape codes).

Default effects may be overridden from your configuration file::

  [color]
  status.modified = blue bold underline red_background
  status.added = green bold
  status.removed = red bold blue_background
  status.deleted = cyan bold underline
  status.unknown = magenta bold underline
  status.ignored = black bold

  # 'none' turns off all effects
  status.clean = none
  status.copied = none

  qseries.applied = blue bold underline
  qseries.unapplied = black bold
  qseries.missing = red bold

  diff.diffline = bold
  diff.extended = cyan bold
  diff.file_a = red bold
  diff.file_b = green bold
  diff.hunk = magenta
  diff.deleted = red
  diff.inserted = green
  diff.changed = white
  diff.trailingwhitespace = bold red_background

  resolve.unresolved = red bold
  resolve.resolved = green bold

  bookmarks.current = green

  branches.active = none
  branches.closed = black bold
  branches.current = green
  branches.inactive = none

  tags.normal = green
  tags.local = black bold

The available effects in terminfo mode are 'blink', 'bold', 'dim',
'inverse', 'invisible', 'italic', 'standout', and 'underline'; in
ECMA-48 mode, the options are 'bold', 'inverse', 'italic', and
'underline'.  How each is rendered depends on the terminal emulator.
Some may not be available for a given terminal type, and will be
silently ignored.

Note that on some systems, terminfo mode may cause problems when using
color with the pager extension and less -R. less with the -R option
will only display ECMA-48 color codes, and terminfo mode may sometimes
emit codes that less doesn't understand. You can work around this by
either using ansi mode (or auto mode), or by using less -r (which will
pass through all terminal control codes, not just color control
codes).

Because there are only eight standard colors, this module allows you
to define color names for other color slots which might be available
for your terminal type, assuming terminfo mode.  For instance::

  color.brightblue = 12
  color.pink = 207
  color.orange = 202

to set 'brightblue' to color slot 12 (useful for 16 color terminals
that have brighter colors defined in the upper eight) and, 'pink' and
'orange' to colors in 256-color xterm's default color cube.  These
defined colors may then be used as any of the pre-defined eight,
including appending '_background' to set the background to that color.

By default, the color extension will use ANSI mode (or win32 mode on
Windows) if it detects a terminal. To override auto mode (to enable
terminfo mode, for example), set the following configuration option::

  [color]
  mode = terminfo

Any value other than 'ansi', 'win32', 'terminfo', or 'auto' will
disable color.
'''

import os

from mercurial import commands, dispatch, extensions, ui as uimod, util
from mercurial import templater, error
from mercurial.i18n import _

testedwith = 'internal'

# start and stop parameters for effects
_effects = {'none': 0, 'black': 30, 'red': 31, 'green': 32, 'yellow': 33,
            'blue': 34, 'magenta': 35, 'cyan': 36, 'white': 37, 'bold': 1,
            'italic': 3, 'underline': 4, 'inverse': 7,
            'black_background': 40, 'red_background': 41,
            'green_background': 42, 'yellow_background': 43,
            'blue_background': 44, 'purple_background': 45,
            'cyan_background': 46, 'white_background': 47}

def _terminfosetup(ui, mode):
    '''Initialize terminfo data and the terminal if we're in terminfo mode.'''

    global _terminfo_params
    # If we failed to load curses, we go ahead and return.
    if not _terminfo_params:
        return
    # Otherwise, see what the config file says.
    if mode not in ('auto', 'terminfo'):
        return

    _terminfo_params.update((key[6:], (False, int(val)))
        for key, val in ui.configitems('color')
        if key.startswith('color.'))

    try:
        curses.setupterm()
    except curses.error, e:
        _terminfo_params = {}
        return

    for key, (b, e) in _terminfo_params.items():
        if not b:
            continue
        if not curses.tigetstr(e):
            # Most terminals don't support dim, invis, etc, so don't be
            # noisy and use ui.debug().
            ui.debug("no terminfo entry for %s\n" % e)
            del _terminfo_params[key]
    if not curses.tigetstr('setaf') or not curses.tigetstr('setab'):
        # Only warn about missing terminfo entries if we explicitly asked for
        # terminfo mode.
        if mode == "terminfo":
            ui.warn(_("no terminfo entry for setab/setaf: reverting to "
              "ECMA-48 color\n"))
        _terminfo_params = {}

def _modesetup(ui, opts):
    global _terminfo_params

    coloropt = opts['color']
    auto = coloropt == 'auto'
    always = not auto and util.parsebool(coloropt)
    if not always and not auto:
        return None

    formatted = always or (os.environ.get('TERM') != 'dumb' and ui.formatted())

    mode = ui.config('color', 'mode', 'auto')
    realmode = mode
    if mode == 'auto':
        if os.name == 'nt' and 'TERM' not in os.environ:
            # looks line a cmd.exe console, use win32 API or nothing
            realmode = 'win32'
        else:
            realmode = 'ansi'

    if realmode == 'win32':
        _terminfo_params = {}
        if not w32effects:
            if mode == 'win32':
                # only warn if color.mode is explicitly set to win32
                ui.warn(_('warning: failed to set color mode to %s\n') % mode)
            return None
        _effects.update(w32effects)
    elif realmode == 'ansi':
        _terminfo_params = {}
    elif realmode == 'terminfo':
        _terminfosetup(ui, mode)
        if not _terminfo_params:
            if mode == 'terminfo':
                ## FIXME Shouldn't we return None in this case too?
                # only warn if color.mode is explicitly set to win32
                ui.warn(_('warning: failed to set color mode to %s\n') % mode)
            realmode = 'ansi'
    else:
        return None

    if always or (auto and formatted):
        return realmode
    return None

try:
    import curses
    # Mapping from effect name to terminfo attribute name or color number.
    # This will also force-load the curses module.
    _terminfo_params = {'none': (True, 'sgr0'),
                        'standout': (True, 'smso'),
                        'underline': (True, 'smul'),
                        'reverse': (True, 'rev'),
                        'inverse': (True, 'rev'),
                        'blink': (True, 'blink'),
                        'dim': (True, 'dim'),
                        'bold': (True, 'bold'),
                        'invisible': (True, 'invis'),
                        'italic': (True, 'sitm'),
                        'black': (False, curses.COLOR_BLACK),
                        'red': (False, curses.COLOR_RED),
                        'green': (False, curses.COLOR_GREEN),
                        'yellow': (False, curses.COLOR_YELLOW),
                        'blue': (False, curses.COLOR_BLUE),
                        'magenta': (False, curses.COLOR_MAGENTA),
                        'cyan': (False, curses.COLOR_CYAN),
                        'white': (False, curses.COLOR_WHITE)}
except ImportError:
    _terminfo_params = False

_styles = {'grep.match': 'red bold',
           'grep.linenumber': 'green',
           'grep.rev': 'green',
           'grep.change': 'green',
           'grep.sep': 'cyan',
           'grep.filename': 'magenta',
           'grep.user': 'magenta',
           'grep.date': 'magenta',
           'bookmarks.current': 'green',
           'branches.active': 'none',
           'branches.closed': 'black bold',
           'branches.current': 'green',
           'branches.inactive': 'none',
           'diff.changed': 'white',
           'diff.deleted': 'red',
           'diff.diffline': 'bold',
           'diff.extended': 'cyan bold',
           'diff.file_a': 'red bold',
           'diff.file_b': 'green bold',
           'diff.hunk': 'magenta',
           'diff.inserted': 'green',
           'diff.trailingwhitespace': 'bold red_background',
           'diffstat.deleted': 'red',
           'diffstat.inserted': 'green',
           'ui.prompt': 'yellow',
           'log.changeset': 'yellow',
           'resolve.resolved': 'green bold',
           'resolve.unresolved': 'red bold',
           'status.added': 'green bold',
           'status.clean': 'none',
           'status.copied': 'none',
           'status.deleted': 'cyan bold underline',
           'status.ignored': 'black bold',
           'status.modified': 'blue bold',
           'status.removed': 'red bold',
           'status.unknown': 'magenta bold underline',
           'tags.normal': 'green',
           'tags.local': 'black bold'}


def _effect_str(effect):
    '''Helper function for render_effects().'''

    bg = False
    if effect.endswith('_background'):
        bg = True
        effect = effect[:-11]
    attr, val = _terminfo_params[effect]
    if attr:
        return curses.tigetstr(val)
    elif bg:
        return curses.tparm(curses.tigetstr('setab'), val)
    else:
        return curses.tparm(curses.tigetstr('setaf'), val)

def render_effects(text, effects):
    'Wrap text in commands to turn on each effect.'
    if not text:
        return text
    if not _terminfo_params:
        start = [str(_effects[e]) for e in ['none'] + effects.split()]
        start = '\033[' + ';'.join(start) + 'm'
        stop = '\033[' + str(_effects['none']) + 'm'
    else:
        start = ''.join(_effect_str(effect)
                        for effect in ['none'] + effects.split())
        stop = _effect_str('none')
    return ''.join([start, text, stop])

def extstyles():
    for name, ext in extensions.extensions():
        _styles.update(getattr(ext, 'colortable', {}))

def configstyles(ui):
    for status, cfgeffects in ui.configitems('color'):
        if '.' not in status or status.startswith('color.'):
            continue
        cfgeffects = ui.configlist('color', status)
        if cfgeffects:
            good = []
            for e in cfgeffects:
                if not _terminfo_params and e in _effects:
                    good.append(e)
                elif e in _terminfo_params or e[:-11] in _terminfo_params:
                    good.append(e)
                else:
                    ui.warn(_("ignoring unknown color/effect %r "
                              "(configured in color.%s)\n")
                            % (e, status))
            _styles[status] = ' '.join(good)

class colorui(uimod.ui):
    def popbuffer(self, labeled=False):
        if self._colormode is None:
            return super(colorui, self).popbuffer(labeled)

        if labeled:
            return ''.join(self.label(a, label) for a, label
                           in self._buffers.pop())
        return ''.join(a for a, label in self._buffers.pop())

    _colormode = 'ansi'
    def write(self, *args, **opts):
        if self._colormode is None:
            return super(colorui, self).write(*args, **opts)

        label = opts.get('label', '')
        if self._buffers:
            self._buffers[-1].extend([(str(a), label) for a in args])
        elif self._colormode == 'win32':
            for a in args:
                win32print(a, super(colorui, self).write, **opts)
        else:
            return super(colorui, self).write(
                *[self.label(str(a), label) for a in args], **opts)

    def write_err(self, *args, **opts):
        if self._colormode is None:
            return super(colorui, self).write_err(*args, **opts)

        label = opts.get('label', '')
        if self._colormode == 'win32':
            for a in args:
                win32print(a, super(colorui, self).write_err, **opts)
        else:
            return super(colorui, self).write_err(
                *[self.label(str(a), label) for a in args], **opts)

    def label(self, msg, label):
        if self._colormode is None:
            return super(colorui, self).label(msg, label)

        effects = []
        for l in label.split():
            s = _styles.get(l, '')
            if s:
                effects.append(s)
        effects = ' '.join(effects)
        if effects:
            return '\n'.join([render_effects(s, effects)
                              for s in msg.split('\n')])
        return msg

def templatelabel(context, mapping, args):
    if len(args) != 2:
        # i18n: "label" is a keyword
        raise error.ParseError(_("label expects two arguments"))

    thing = templater.stringify(args[1][0](context, mapping, args[1][1]))
    thing = templater.runtemplate(context, mapping,
                                  templater.compiletemplate(thing, context))

    # apparently, repo could be a string that is the favicon?
    repo = mapping.get('repo', '')
    if isinstance(repo, str):
        return thing

    label = templater.stringify(args[0][0](context, mapping, args[0][1]))
    label = templater.runtemplate(context, mapping,
                                  templater.compiletemplate(label, context))

    thing = templater.stringify(thing)
    label = templater.stringify(label)

    return repo.ui.label(thing, label)

def uisetup(ui):
    if ui.plain():
        return
    if not issubclass(ui.__class__, colorui):
        colorui.__bases__ = (ui.__class__,)
        ui.__class__ = colorui
    def colorcmd(orig, ui_, opts, cmd, cmdfunc):
        mode = _modesetup(ui_, opts)
        colorui._colormode = mode
        if mode:
            extstyles()
            configstyles(ui_)
        return orig(ui_, opts, cmd, cmdfunc)
    extensions.wrapfunction(dispatch, '_runcommand', colorcmd)
    templater.funcs['label'] = templatelabel

def extsetup(ui):
    commands.globalopts.append(
        ('', 'color', 'auto',
         # i18n: 'always', 'auto', and 'never' are keywords and should
         # not be translated
         _("when to colorize (boolean, always, auto, or never)"),
         _('TYPE')))

if os.name != 'nt':
    w32effects = None
else:
    import re, ctypes

    _kernel32 = ctypes.windll.kernel32

    _WORD = ctypes.c_ushort

    _INVALID_HANDLE_VALUE = -1

    class _COORD(ctypes.Structure):
        _fields_ = [('X', ctypes.c_short),
                    ('Y', ctypes.c_short)]

    class _SMALL_RECT(ctypes.Structure):
        _fields_ = [('Left', ctypes.c_short),
                    ('Top', ctypes.c_short),
                    ('Right', ctypes.c_short),
                    ('Bottom', ctypes.c_short)]

    class _CONSOLE_SCREEN_BUFFER_INFO(ctypes.Structure):
        _fields_ = [('dwSize', _COORD),
                    ('dwCursorPosition', _COORD),
                    ('wAttributes', _WORD),
                    ('srWindow', _SMALL_RECT),
                    ('dwMaximumWindowSize', _COORD)]

    _STD_OUTPUT_HANDLE = 0xfffffff5L # (DWORD)-11
    _STD_ERROR_HANDLE = 0xfffffff4L  # (DWORD)-12

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
        'none': -1,
        'black': 0,
        'red': _FOREGROUND_RED,
        'green': _FOREGROUND_GREEN,
        'yellow': _FOREGROUND_RED | _FOREGROUND_GREEN,
        'blue': _FOREGROUND_BLUE,
        'magenta': _FOREGROUND_BLUE | _FOREGROUND_RED,
        'cyan': _FOREGROUND_BLUE | _FOREGROUND_GREEN,
        'white': _FOREGROUND_RED | _FOREGROUND_GREEN | _FOREGROUND_BLUE,
        'bold': _FOREGROUND_INTENSITY,
        'black_background': 0x100,                  # unused value > 0x0f
        'red_background': _BACKGROUND_RED,
        'green_background': _BACKGROUND_GREEN,
        'yellow_background': _BACKGROUND_RED | _BACKGROUND_GREEN,
        'blue_background': _BACKGROUND_BLUE,
        'purple_background': _BACKGROUND_BLUE | _BACKGROUND_RED,
        'cyan_background': _BACKGROUND_BLUE | _BACKGROUND_GREEN,
        'white_background': (_BACKGROUND_RED | _BACKGROUND_GREEN |
                             _BACKGROUND_BLUE),
        'bold_background': _BACKGROUND_INTENSITY,
        'underline': _COMMON_LVB_UNDERSCORE,  # double-byte charsets only
        'inverse': _COMMON_LVB_REVERSE_VIDEO, # double-byte charsets only
    }

    passthrough = set([_FOREGROUND_INTENSITY,
                       _BACKGROUND_INTENSITY,
                       _COMMON_LVB_UNDERSCORE,
                       _COMMON_LVB_REVERSE_VIDEO])

    stdout = _kernel32.GetStdHandle(
                  _STD_OUTPUT_HANDLE)  # don't close the handle returned
    if stdout is None or stdout == _INVALID_HANDLE_VALUE:
        w32effects = None
    else:
        csbi = _CONSOLE_SCREEN_BUFFER_INFO()
        if not _kernel32.GetConsoleScreenBufferInfo(
                    stdout, ctypes.byref(csbi)):
            # stdout may not support GetConsoleScreenBufferInfo()
            # when called from subprocess or redirected
            w32effects = None
        else:
            origattr = csbi.wAttributes
            ansire = re.compile('\033\[([^m]*)m([^\033]*)(.*)',
                                re.MULTILINE | re.DOTALL)

    def win32print(text, orig, **opts):
        label = opts.get('label', '')
        attr = origattr

        def mapcolor(val, attr):
            if val == -1:
                return origattr
            elif val in passthrough:
                return attr | val
            elif val > 0x0f:
                return (val & 0x70) | (attr & 0x8f)
            else:
                return (val & 0x07) | (attr & 0xf8)

        # determine console attributes based on labels
        for l in label.split():
            style = _styles.get(l, '')
            for effect in style.split():
                attr = mapcolor(w32effects[effect], attr)

        # hack to ensure regexp finds data
        if not text.startswith('\033['):
            text = '\033[m' + text

        # Look for ANSI-like codes embedded in text
        m = re.match(ansire, text)

        try:
            while m:
                for sattr in m.group(1).split(';'):
                    if sattr:
                        attr = mapcolor(int(sattr), attr)
                _kernel32.SetConsoleTextAttribute(stdout, attr)
                orig(m.group(2), **opts)
                m = re.match(ansire, m.group(3))
        finally:
            # Explicitly reset original attributes
            _kernel32.SetConsoleTextAttribute(stdout, origattr)
