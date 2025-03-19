# fancyopts.py - better command line parsing
#
#  Copyright 2005-2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import abc
import functools

from .i18n import _
from . import (
    error,
    pycompat,
)

# Set of flags to not apply boolean negation logic on
nevernegate = {
    # avoid --no-noninteractive
    b'noninteractive',
    # These two flags are special because they cause hg to do one
    # thing and then exit, and so aren't suitable for use in things
    # like aliases anyway.
    b'help',
    b'version',
}


def _earlyoptarg(arg, shortlist, namelist):
    """Check if the given arg is a valid unabbreviated option

    Returns (flag_str, has_embedded_value?, embedded_value, takes_value?)

    >>> def opt(arg):
    ...     return _earlyoptarg(arg, b'R:q', [b'cwd=', b'debugger'])

    long form:

    >>> opt(b'--cwd')
    ('--cwd', False, '', True)
    >>> opt(b'--cwd=')
    ('--cwd', True, '', True)
    >>> opt(b'--cwd=foo')
    ('--cwd', True, 'foo', True)
    >>> opt(b'--debugger')
    ('--debugger', False, '', False)
    >>> opt(b'--debugger=')  # invalid but parsable
    ('--debugger', True, '', False)

    short form:

    >>> opt(b'-R')
    ('-R', False, '', True)
    >>> opt(b'-Rfoo')
    ('-R', True, 'foo', True)
    >>> opt(b'-q')
    ('-q', False, '', False)
    >>> opt(b'-qfoo')  # invalid but parsable
    ('-q', True, 'foo', False)

    unknown or invalid:

    >>> opt(b'--unknown')
    ('', False, '', False)
    >>> opt(b'-u')
    ('', False, '', False)
    >>> opt(b'-ufoo')
    ('', False, '', False)
    >>> opt(b'--')
    ('', False, '', False)
    >>> opt(b'-')
    ('', False, '', False)
    >>> opt(b'-:')
    ('', False, '', False)
    >>> opt(b'-:foo')
    ('', False, '', False)
    """
    if arg.startswith(b'--'):
        flag, eq, val = arg.partition(b'=')
        if flag[2:] in namelist:
            return flag, bool(eq), val, False
        if flag[2:] + b'=' in namelist:
            return flag, bool(eq), val, True
    elif arg.startswith(b'-') and arg != b'-' and not arg.startswith(b'-:'):
        flag, val = arg[:2], arg[2:]
        i = shortlist.find(flag[1:])
        if i >= 0:
            return flag, bool(val), val, shortlist.startswith(b':', i + 1)
    return b'', False, b'', False


def earlygetopt(args, shortlist, namelist, gnu=False, keepsep=False):
    """Parse options like getopt, but ignores unknown options and abbreviated
    forms

    If gnu=False, this stops processing options as soon as a non/unknown-option
    argument is encountered. Otherwise, option and non-option arguments may be
    intermixed, and unknown-option arguments are taken as non-option.

    If keepsep=True, '--' won't be removed from the list of arguments left.
    This is useful for stripping early options from a full command arguments.

    >>> def get(args, gnu=False, keepsep=False):
    ...     return earlygetopt(args, b'R:q', [b'cwd=', b'debugger'],
    ...                        gnu=gnu, keepsep=keepsep)

    default parsing rules for early options:

    >>> get([b'x', b'--cwd', b'foo', b'-Rbar', b'-q', b'y'], gnu=True)
    ([('--cwd', 'foo'), ('-R', 'bar'), ('-q', '')], ['x', 'y'])
    >>> get([b'x', b'--cwd=foo', b'y', b'-R', b'bar', b'--debugger'], gnu=True)
    ([('--cwd', 'foo'), ('-R', 'bar'), ('--debugger', '')], ['x', 'y'])
    >>> get([b'--unknown', b'--cwd=foo', b'--', '--debugger'], gnu=True)
    ([('--cwd', 'foo')], ['--unknown', '--debugger'])

    restricted parsing rules (early options must come first):

    >>> get([b'--cwd', b'foo', b'-Rbar', b'x', b'-q', b'y'], gnu=False)
    ([('--cwd', 'foo'), ('-R', 'bar')], ['x', '-q', 'y'])
    >>> get([b'--cwd=foo', b'x', b'y', b'-R', b'bar', b'--debugger'], gnu=False)
    ([('--cwd', 'foo')], ['x', 'y', '-R', 'bar', '--debugger'])
    >>> get([b'--unknown', b'--cwd=foo', b'--', '--debugger'], gnu=False)
    ([], ['--unknown', '--cwd=foo', '--', '--debugger'])

    stripping early options (without loosing '--'):

    >>> get([b'x', b'-Rbar', b'--', '--debugger'], gnu=True, keepsep=True)[1]
    ['x', '--', '--debugger']

    last argument:

    >>> get([b'--cwd'])
    ([], ['--cwd'])
    >>> get([b'--cwd=foo'])
    ([('--cwd', 'foo')], [])
    >>> get([b'-R'])
    ([], ['-R'])
    >>> get([b'-Rbar'])
    ([('-R', 'bar')], [])
    >>> get([b'-q'])
    ([('-q', '')], [])
    >>> get([b'-q', b'--'])
    ([('-q', '')], [])

    '--' may be a value:

    >>> get([b'-R', b'--', b'x'])
    ([('-R', '--')], ['x'])
    >>> get([b'--cwd', b'--', b'x'])
    ([('--cwd', '--')], ['x'])

    value passed to bool options:

    >>> get([b'--debugger=foo', b'x'])
    ([], ['--debugger=foo', 'x'])
    >>> get([b'-qfoo', b'x'])
    ([], ['-qfoo', 'x'])

    short option isn't separated with '=':

    >>> get([b'-R=bar'])
    ([('-R', '=bar')], [])

    ':' may be in shortlist, but shouldn't be taken as an option letter:

    >>> get([b'-:', b'y'])
    ([], ['-:', 'y'])

    '-' is a valid non-option argument:

    >>> get([b'-', b'y'])
    ([], ['-', 'y'])
    """
    parsedopts = []
    parsedargs = []
    pos = 0
    while pos < len(args):
        arg = args[pos]
        if arg == b'--':
            pos += not keepsep
            break
        flag, hasval, val, takeval = _earlyoptarg(arg, shortlist, namelist)
        if not hasval and takeval and pos + 1 >= len(args):
            # missing last argument
            break
        if not flag or hasval and not takeval:
            # non-option argument or -b/--bool=INVALID_VALUE
            if gnu:
                parsedargs.append(arg)
                pos += 1
            else:
                break
        elif hasval == takeval:
            # -b/--bool or -s/--str=VALUE
            parsedopts.append((flag, val))
            pos += 1
        else:
            # -s/--str VALUE
            parsedopts.append((flag, args[pos + 1]))
            pos += 2

    parsedargs.extend(args[pos:])
    return parsedopts, parsedargs


class customopt:  # pytype: disable=ignored-metaclass
    """Manage defaults and mutations for any type of opt."""

    __metaclass__ = abc.ABCMeta

    def __init__(self, defaultvalue):
        self._defaultvalue = defaultvalue

    def _isboolopt(self):
        return False

    def getdefaultvalue(self):
        """Returns the default value for this opt.

        Subclasses should override this to return a new value if the value type
        is mutable."""
        return self._defaultvalue

    @abc.abstractmethod
    def newstate(self, oldstate, newparam, abort):
        """Adds newparam to oldstate and returns the new state.

        On failure, abort can be called with a string error message."""


class _simpleopt(customopt):
    def _isboolopt(self):
        return isinstance(self._defaultvalue, (bool, type(None)))

    def newstate(self, oldstate, newparam, abort):
        return newparam


class _callableopt(customopt):
    def __init__(self, callablefn):
        self.callablefn = callablefn
        super(_callableopt, self).__init__(None)

    def newstate(self, oldstate, newparam, abort):
        return self.callablefn(newparam)


class _listopt(customopt):
    def getdefaultvalue(self):
        return self._defaultvalue[:]

    def newstate(self, oldstate, newparam, abort):
        oldstate.append(newparam)
        return oldstate


class _intopt(customopt):
    def newstate(self, oldstate, newparam, abort):
        try:
            return int(newparam)
        except ValueError:
            abort(_(b'expected int'))


def _defaultopt(default):
    """Returns a default opt implementation, given a default value."""

    if isinstance(default, customopt):
        return default
    elif callable(default):
        return _callableopt(default)
    elif isinstance(default, list):
        return _listopt(default[:])
    elif type(default) is type(1):
        return _intopt(default)
    else:
        return _simpleopt(default)


def fancyopts(args, options, state, gnu=False, early=False, optaliases=None):
    """
    read args, parse options, and store options in state

    each option is a tuple of:

      short option or ''
      long option
      default value
      description
      option value label(optional)

    option types include:

      boolean or none - option sets variable in state to true
      string - parameter string is stored in state
      list - parameter string is added to a list
      integer - parameter strings is stored as int
      function - call function with parameter
      customopt - subclass of 'customopt'

    optaliases is a mapping from a canonical option name to a list of
    additional long options. This exists for preserving backward compatibility
    of early options. If we want to use it extensively, please consider moving
    the functionality to the options table (e.g separate long options by '|'.)

    non-option args are returned
    """
    if optaliases is None:
        optaliases = {}
    namelist = []
    shortlist = b''
    argmap = {}
    defmap = {}
    negations = {}
    alllong = {o[1] for o in options}

    for option in options:
        if len(option) == 5:
            short, name, default, comment, dummy = option
        else:
            short, name, default, comment = option
        # convert opts to getopt format
        onames = [name]
        onames.extend(optaliases.get(name, []))
        name = name.replace(b'-', b'_')

        argmap[b'-' + short] = name
        for n in onames:
            argmap[b'--' + n] = name
        defmap[name] = _defaultopt(default)

        # copy defaults to state
        state[name] = defmap[name].getdefaultvalue()

        # does it take a parameter?
        if not defmap[name]._isboolopt():
            if short:
                short += b':'
            onames = [n + b'=' for n in onames]
        elif name not in nevernegate:
            for n in onames:
                if n.startswith(b'no-'):
                    insert = n[3:]
                else:
                    insert = b'no-' + n
                # backout (as a practical example) has both --commit and
                # --no-commit options, so we don't want to allow the
                # negations of those flags.
                if insert not in alllong:
                    assert (b'--' + n) not in negations
                    negations[b'--' + insert] = b'--' + n
                    namelist.append(insert)
        if short:
            shortlist += short
        if name:
            namelist.extend(onames)

    # parse arguments
    if early:
        parse = functools.partial(earlygetopt, gnu=gnu)
    elif gnu:
        parse = pycompat.gnugetoptb
    else:
        parse = pycompat.getoptb
    opts, args = parse(args, shortlist, namelist)

    # transfer result to state
    for opt, val in opts:
        boolval = True
        negation = negations.get(opt, False)
        if negation:
            opt = negation
            boolval = False
        name = argmap[opt]
        obj = defmap[name]
        if obj._isboolopt():
            state[name] = boolval
        else:

            def abort(s):
                raise error.InputError(
                    _(b'invalid value %r for option %s, %s')
                    % (pycompat.maybebytestr(val), opt, s)
                )

            state[name] = defmap[name].newstate(state[name], val, abort)

    # return unparsed args
    return args
