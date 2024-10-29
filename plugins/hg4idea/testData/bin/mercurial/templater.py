# templater.py - template expansion for output
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Slightly complicated template engine for commands and hgweb

This module provides low-level interface to the template engine. See the
formatter and cmdutil modules if you are looking for high-level functions
such as ``cmdutil.rendertemplate(ctx, tmpl)``.

Internal Data Types
-------------------

Template keywords and functions take a dictionary of current symbols and
resources (a "mapping") and return result. Inputs and outputs must be one
of the following data types:

bytes
    a byte string, which is generally a human-readable text in local encoding.

generator
    a lazily-evaluated byte string, which is a possibly nested generator of
    values of any printable types, and  will be folded by ``stringify()``
    or ``flatten()``.

None
    sometimes represents an empty value, which can be stringified to ''.

True, False, int, float
    can be stringified as such.

wrappedbytes, wrappedvalue
    a wrapper for the above printable types.

date
    represents a (unixtime, offset) tuple.

hybrid
    represents a list/dict of printable values, which can also be converted
    to mappings by % operator.

hybriditem
    represents a scalar printable value, also supports % operator.

revslist
    represents a list of revision numbers.

mappinggenerator, mappinglist
    represents mappings (i.e. a list of dicts), which may have default
    output format.

mappingdict
    represents a single mapping (i.e. a dict), which may have default output
    format.

mappingnone
    represents None of Optional[mappable], which will be mapped to an empty
    string by % operation.

mappedgenerator
    a lazily-evaluated list of byte strings, which is e.g. a result of %
    operation.
"""


import abc
import os

from .i18n import _
from .pycompat import (
    FileNotFoundError,
)
from . import (
    config,
    encoding,
    error,
    parser,
    pycompat,
    templatefilters,
    templatefuncs,
    templateutil,
    util,
)
from .utils import (
    resourceutil,
    stringutil,
)

# template parsing

elements = {
    # token-type: binding-strength, primary, prefix, infix, suffix
    b"(": (20, None, (b"group", 1, b")"), (b"func", 1, b")"), None),
    b".": (18, None, None, (b".", 18), None),
    b"%": (15, None, None, (b"%", 15), None),
    b"|": (15, None, None, (b"|", 15), None),
    b"*": (5, None, None, (b"*", 5), None),
    b"/": (5, None, None, (b"/", 5), None),
    b"+": (4, None, None, (b"+", 4), None),
    b"-": (4, None, (b"negate", 19), (b"-", 4), None),
    b"=": (3, None, None, (b"keyvalue", 3), None),
    b",": (2, None, None, (b"list", 2), None),
    b")": (0, None, None, None, None),
    b"integer": (0, b"integer", None, None, None),
    b"symbol": (0, b"symbol", None, None, None),
    b"string": (0, b"string", None, None, None),
    b"template": (0, b"template", None, None, None),
    b"end": (0, None, None, None, None),
}


def tokenize(program, start, end, term=None):
    """Parse a template expression into a stream of tokens, which must end
    with term if specified"""
    pos = start
    program = pycompat.bytestr(program)
    while pos < end:
        c = program[pos]
        if c.isspace():  # skip inter-token whitespace
            pass
        elif c in b"(=,).%|+-*/":  # handle simple operators
            yield (c, None, pos)
        elif c in b'"\'':  # handle quoted templates
            s = pos + 1
            data, pos = _parsetemplate(program, s, end, c)
            yield (b'template', data, s)
            pos -= 1
        elif c == b'r' and program[pos : pos + 2] in (b"r'", b'r"'):
            # handle quoted strings
            c = program[pos + 1]
            s = pos = pos + 2
            while pos < end:  # find closing quote
                d = program[pos]
                if d == b'\\':  # skip over escaped characters
                    pos += 2
                    continue
                if d == c:
                    yield (b'string', program[s:pos], s)
                    break
                pos += 1
            else:
                raise error.ParseError(_(b"unterminated string"), s)
        elif c.isdigit():
            s = pos
            while pos < end:
                d = program[pos]
                if not d.isdigit():
                    break
                pos += 1
            yield (b'integer', program[s:pos], s)
            pos -= 1
        elif (
            c == b'\\'
            and program[pos : pos + 2] in (br"\'", br'\"')
            or c == b'r'
            and program[pos : pos + 3] in (br"r\'", br'r\"')
        ):
            # handle escaped quoted strings for compatibility with 2.9.2-3.4,
            # where some of nested templates were preprocessed as strings and
            # then compiled. therefore, \"...\" was allowed. (issue4733)
            #
            # processing flow of _evalifliteral() at 5ab28a2e9962:
            # outer template string    -> stringify()  -> compiletemplate()
            # ------------------------    ------------    ------------------
            # {f("\\\\ {g(\"\\\"\")}"}    \\ {g("\"")}    [r'\\', {g("\"")}]
            #             ~~~~~~~~
            #             escaped quoted string
            if c == b'r':
                pos += 1
                token = b'string'
            else:
                token = b'template'
            quote = program[pos : pos + 2]
            s = pos = pos + 2
            while pos < end:  # find closing escaped quote
                # pycompat.bytestr (and bytes) both have .startswith() that
                # takes an optional start and an optional end, but pytype thinks
                # it only takes 2 args.

                # pytype: disable=wrong-arg-count
                if program.startswith(b'\\\\\\', pos, end):
                    pos += 4  # skip over double escaped characters
                    continue
                if program.startswith(quote, pos, end):
                    # pytype: enable=wrong-arg-count

                    # interpret as if it were a part of an outer string
                    data = parser.unescapestr(program[s:pos])
                    if token == b'template':
                        data = _parsetemplate(data, 0, len(data))[0]
                    yield (token, data, s)
                    pos += 1
                    break
                pos += 1
            else:
                raise error.ParseError(_(b"unterminated string"), s)
        elif c.isalnum() or c in b'_':
            s = pos
            pos += 1
            while pos < end:  # find end of symbol
                d = program[pos]
                if not (d.isalnum() or d == b"_"):
                    break
                pos += 1
            sym = program[s:pos]
            yield (b'symbol', sym, s)
            pos -= 1
        elif c == term:
            yield (b'end', None, pos)
            return
        else:
            raise error.ParseError(_(b"syntax error"), pos)
        pos += 1
    if term:
        raise error.ParseError(_(b"unterminated template expansion"), start)
    yield (b'end', None, pos)


def _parsetemplate(tmpl, start, stop, quote=b''):
    r"""
    >>> _parsetemplate(b'foo{bar}"baz', 0, 12)
    ([('string', 'foo'), ('symbol', 'bar'), ('string', '"baz')], 12)
    >>> _parsetemplate(b'foo{bar}"baz', 0, 12, quote=b'"')
    ([('string', 'foo'), ('symbol', 'bar')], 9)
    >>> _parsetemplate(b'foo"{bar}', 0, 9, quote=b'"')
    ([('string', 'foo')], 4)
    >>> _parsetemplate(br'foo\"bar"baz', 0, 12, quote=b'"')
    ([('string', 'foo"'), ('string', 'bar')], 9)
    >>> _parsetemplate(br'foo\\"bar', 0, 10, quote=b'"')
    ([('string', 'foo\\')], 6)
    """
    parsed = []
    for typ, val, pos in _scantemplate(tmpl, start, stop, quote):
        if typ == b'string':
            parsed.append((typ, val))
        elif typ == b'template':
            parsed.append(val)
        elif typ == b'end':
            return parsed, pos
        else:
            raise error.ProgrammingError(b'unexpected type: %s' % typ)
    raise error.ProgrammingError(b'unterminated scanning of template')


def scantemplate(tmpl, raw=False):
    r"""Scan (type, start, end) positions of outermost elements in template

    If raw=True, a backslash is not taken as an escape character just like
    r'' string in Python. Note that this is different from r'' literal in
    template in that no template fragment can appear in r'', e.g. r'{foo}'
    is a literal '{foo}', but ('{foo}', raw=True) is a template expression
    'foo'.

    >>> list(scantemplate(b'foo{bar}"baz'))
    [('string', 0, 3), ('template', 3, 8), ('string', 8, 12)]
    >>> list(scantemplate(b'outer{"inner"}outer'))
    [('string', 0, 5), ('template', 5, 14), ('string', 14, 19)]
    >>> list(scantemplate(b'foo\\{escaped}'))
    [('string', 0, 5), ('string', 5, 13)]
    >>> list(scantemplate(b'foo\\{escaped}', raw=True))
    [('string', 0, 4), ('template', 4, 13)]
    """
    last = None
    for typ, val, pos in _scantemplate(tmpl, 0, len(tmpl), raw=raw):
        if last:
            yield last + (pos,)
        if typ == b'end':
            return
        else:
            last = (typ, pos)
    raise error.ProgrammingError(b'unterminated scanning of template')


def _scantemplate(tmpl, start, stop, quote=b'', raw=False):
    """Parse template string into chunks of strings and template expressions"""
    sepchars = b'{' + quote
    unescape = [parser.unescapestr, pycompat.identity][raw]
    pos = start
    p = parser.parser(elements)
    try:
        while pos < stop:
            n = min(
                (tmpl.find(c, pos, stop) for c in pycompat.bytestr(sepchars)),
                key=lambda n: (n < 0, n),
            )
            if n < 0:
                yield (b'string', unescape(tmpl[pos:stop]), pos)
                pos = stop
                break
            c = tmpl[n : n + 1]
            bs = 0  # count leading backslashes
            if not raw:
                bs = (n - pos) - len(tmpl[pos:n].rstrip(b'\\'))
            if bs % 2 == 1:
                # escaped (e.g. '\{', '\\\{', but not '\\{')
                yield (b'string', unescape(tmpl[pos : n - 1]) + c, pos)
                pos = n + 1
                continue
            if n > pos:
                yield (b'string', unescape(tmpl[pos:n]), pos)
            if c == quote:
                yield (b'end', None, n + 1)
                return

            parseres, pos = p.parse(tokenize(tmpl, n + 1, stop, b'}'))

            # pycompat.bytestr (and bytes) both have .startswith() that
            # takes an optional start and an optional end, but pytype thinks
            # it only takes 2 args.

            # pytype: disable=wrong-arg-count
            if not tmpl.startswith(b'}', pos):
                # pytype: enable=wrong-arg-count
                raise error.ParseError(_(b"invalid token"), pos)
            yield (b'template', parseres, n)
            pos += 1

        if quote:
            raise error.ParseError(_(b"unterminated string"), start)
    except error.ParseError as inst:
        _addparseerrorhint(inst, tmpl)
        raise
    yield (b'end', None, pos)


def _addparseerrorhint(inst, tmpl):
    if inst.location is None:
        return
    loc = inst.location
    # Offset the caret location by the number of newlines before the
    # location of the error, since we will replace one-char newlines
    # with the two-char literal r'\n'.
    offset = tmpl[:loc].count(b'\n')
    tmpl = tmpl.replace(b'\n', br'\n')
    # We want the caret to point to the place in the template that
    # failed to parse, but in a hint we get a open paren at the
    # start. Therefore, we print "loc + 1" spaces (instead of "loc")
    # to line up the caret with the location of the error.
    inst.hint = tmpl + b'\n' + b' ' * (loc + 1 + offset) + b'^ ' + _(b'here')


def _unnesttemplatelist(tree):
    """Expand list of templates to node tuple

    >>> def f(tree):
    ...     print(pycompat.sysstr(prettyformat(_unnesttemplatelist(tree))))
    >>> f((b'template', []))
    (string '')
    >>> f((b'template', [(b'string', b'foo')]))
    (string 'foo')
    >>> f((b'template', [(b'string', b'foo'), (b'symbol', b'rev')]))
    (template
      (string 'foo')
      (symbol 'rev'))
    >>> f((b'template', [(b'symbol', b'rev')]))  # template(rev) -> str
    (template
      (symbol 'rev'))
    >>> f((b'template', [(b'template', [(b'string', b'foo')])]))
    (string 'foo')
    """
    if not isinstance(tree, tuple):
        return tree
    op = tree[0]
    if op != b'template':
        return (op,) + tuple(_unnesttemplatelist(x) for x in tree[1:])

    assert len(tree) == 2
    xs = tuple(_unnesttemplatelist(x) for x in tree[1])
    if not xs:
        return (b'string', b'')  # empty template ""
    elif len(xs) == 1 and xs[0][0] == b'string':
        return xs[0]  # fast path for string with no template fragment "x"
    else:
        return (op,) + xs


def parse(tmpl):
    """Parse template string into tree"""
    parsed, pos = _parsetemplate(tmpl, 0, len(tmpl))
    assert pos == len(tmpl), b'unquoted template should be consumed'
    return _unnesttemplatelist((b'template', parsed))


def parseexpr(expr):
    """Parse a template expression into tree

    >>> parseexpr(b'"foo"')
    ('string', 'foo')
    >>> parseexpr(b'foo(bar)')
    ('func', ('symbol', 'foo'), ('symbol', 'bar'))
    >>> from . import error
    >>> from . import pycompat
    >>> try:
    ...   parseexpr(b'foo(')
    ... except error.ParseError as e:
    ...   pycompat.sysstr(e.message)
    ...   e.location
    'not a prefix: end'
    4
    >>> try:
    ...   parseexpr(b'"foo" "bar"')
    ... except error.ParseError as e:
    ...   pycompat.sysstr(e.message)
    ...   e.location
    'invalid token'
    7
    """
    try:
        return _parseexpr(expr)
    except error.ParseError as inst:
        _addparseerrorhint(inst, expr)
        raise


def _parseexpr(expr):
    p = parser.parser(elements)
    tree, pos = p.parse(tokenize(expr, 0, len(expr)))
    if pos != len(expr):
        raise error.ParseError(_(b'invalid token'), pos)
    return _unnesttemplatelist(tree)


def prettyformat(tree):
    return parser.prettyformat(tree, (b'integer', b'string', b'symbol'))


def compileexp(exp, context, curmethods):
    """Compile parsed template tree to (func, data) pair"""
    if not exp:
        raise error.ParseError(_(b"missing argument"))
    t = exp[0]
    return curmethods[t](exp, context)


# template evaluation


def getsymbol(exp):
    if exp[0] == b'symbol':
        return exp[1]
    raise error.ParseError(_(b"expected a symbol, got '%s'") % exp[0])


def getlist(x):
    if not x:
        return []
    if x[0] == b'list':
        return getlist(x[1]) + [x[2]]
    return [x]


def gettemplate(exp, context):
    """Compile given template tree or load named template from map file;
    returns (func, data) pair"""
    if exp[0] in (b'template', b'string'):
        return compileexp(exp, context, methods)
    if exp[0] == b'symbol':
        # unlike runsymbol(), here 'symbol' is always taken as template name
        # even if it exists in mapping. this allows us to override mapping
        # by web templates, e.g. 'changelogtag' is redefined in map file.
        return context._load(exp[1])
    raise error.ParseError(_(b"expected template specifier"))


def _runrecursivesymbol(context, mapping, key):
    raise error.InputError(_(b"recursive reference '%s' in template") % key)


def buildtemplate(exp, context):
    ctmpl = [compileexp(e, context, methods) for e in exp[1:]]
    return (templateutil.runtemplate, ctmpl)


def buildfilter(exp, context):
    n = getsymbol(exp[2])
    if n in context._filters:
        filt = context._filters[n]
        arg = compileexp(exp[1], context, methods)
        return (templateutil.runfilter, (arg, filt))
    if n in context._funcs:
        f = context._funcs[n]
        args = _buildfuncargs(exp[1], context, methods, n, f._argspec)
        return (f, args)
    raise error.ParseError(_(b"unknown function '%s'") % n)


def buildmap(exp, context):
    darg = compileexp(exp[1], context, methods)
    targ = gettemplate(exp[2], context)
    return (templateutil.runmap, (darg, targ))


def buildmember(exp, context):
    darg = compileexp(exp[1], context, methods)
    memb = getsymbol(exp[2])
    return (templateutil.runmember, (darg, memb))


def buildnegate(exp, context):
    arg = compileexp(exp[1], context, exprmethods)
    return (templateutil.runnegate, arg)


def buildarithmetic(exp, context, func):
    left = compileexp(exp[1], context, exprmethods)
    right = compileexp(exp[2], context, exprmethods)
    return (templateutil.runarithmetic, (func, left, right))


def buildfunc(exp, context):
    n = getsymbol(exp[1])
    if n in context._funcs:
        f = context._funcs[n]
        args = _buildfuncargs(exp[2], context, exprmethods, n, f._argspec)
        return (f, args)
    if n in context._filters:
        args = _buildfuncargs(exp[2], context, exprmethods, n, argspec=None)
        if len(args) != 1:
            raise error.ParseError(_(b"filter %s expects one argument") % n)
        f = context._filters[n]
        return (templateutil.runfilter, (args[0], f))
    raise error.ParseError(_(b"unknown function '%s'") % n)


def _buildfuncargs(exp, context, curmethods, funcname, argspec):
    """Compile parsed tree of function arguments into list or dict of
    (func, data) pairs

    >>> context = engine(lambda t: (templateutil.runsymbol, t))
    >>> def fargs(expr, argspec):
    ...     x = _parseexpr(expr)
    ...     n = getsymbol(x[1])
    ...     return _buildfuncargs(x[2], context, exprmethods, n, argspec)
    >>> list(fargs(b'a(l=1, k=2)', b'k l m').keys())
    ['l', 'k']
    >>> args = fargs(b'a(opts=1, k=2)', b'**opts')
    >>> list(args.keys()), list(args[b'opts'].keys())
    (['opts'], ['opts', 'k'])
    """

    def compiledict(xs):
        return util.sortdict(
            (k, compileexp(x, context, curmethods)) for k, x in xs.items()
        )

    def compilelist(xs):
        return [compileexp(x, context, curmethods) for x in xs]

    if not argspec:
        # filter or function with no argspec: return list of positional args
        return compilelist(getlist(exp))

    # function with argspec: return dict of named args
    _poskeys, varkey, _keys, optkey = argspec = parser.splitargspec(argspec)
    treeargs = parser.buildargsdict(
        getlist(exp),
        funcname,
        argspec,
        keyvaluenode=b'keyvalue',
        keynode=b'symbol',
    )
    compargs = util.sortdict()
    if varkey:
        compargs[varkey] = compilelist(treeargs.pop(varkey))
    if optkey:
        compargs[optkey] = compiledict(treeargs.pop(optkey))
    compargs.update(compiledict(treeargs))
    return compargs


def buildkeyvaluepair(exp, content):
    raise error.ParseError(_(b"can't use a key-value pair in this context"))


def buildlist(exp, context):
    raise error.ParseError(
        _(b"can't use a list in this context"),
        hint=_(b'check place of comma and parens'),
    )


# methods to interpret function arguments or inner expressions (e.g. {_(x)})
exprmethods = {
    b"integer": lambda e, c: (templateutil.runinteger, e[1]),
    b"string": lambda e, c: (templateutil.runstring, e[1]),
    b"symbol": lambda e, c: (templateutil.runsymbol, e[1]),
    b"template": buildtemplate,
    b"group": lambda e, c: compileexp(e[1], c, exprmethods),
    b".": buildmember,
    b"|": buildfilter,
    b"%": buildmap,
    b"func": buildfunc,
    b"keyvalue": buildkeyvaluepair,
    b"list": buildlist,
    b"+": lambda e, c: buildarithmetic(e, c, lambda a, b: a + b),
    b"-": lambda e, c: buildarithmetic(e, c, lambda a, b: a - b),
    b"negate": buildnegate,
    b"*": lambda e, c: buildarithmetic(e, c, lambda a, b: a * b),
    b"/": lambda e, c: buildarithmetic(e, c, lambda a, b: a // b),
}

# methods to interpret top-level template (e.g. {x}, {x|_}, {x % "y"})
methods = exprmethods.copy()
methods[b"integer"] = exprmethods[b"symbol"]  # '{1}' as variable


class _aliasrules(parser.basealiasrules):
    """Parsing and expansion rule set of template aliases"""

    _section = _(b'template alias')
    _parse = staticmethod(_parseexpr)

    @staticmethod
    def _trygetfunc(tree):
        """Return (name, args) if tree is func(...) or ...|filter; otherwise
        None"""
        if tree[0] == b'func' and tree[1][0] == b'symbol':
            return tree[1][1], getlist(tree[2])
        if tree[0] == b'|' and tree[2][0] == b'symbol':
            return tree[2][1], [tree[1]]


def expandaliases(tree, aliases):
    """Return new tree of aliases are expanded"""
    aliasmap = _aliasrules.buildmap(aliases)
    return _aliasrules.expand(aliasmap, tree)


# template engine


def unquotestring(s):
    '''unwrap quotes if any; otherwise returns unmodified string'''
    if len(s) < 2 or s[0] not in b"'\"" or s[0] != s[-1]:
        return s
    return s[1:-1]


class resourcemapper:  # pytype: disable=ignored-metaclass
    """Mapper of internal template resources"""

    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def availablekeys(self, mapping):
        """Return a set of available resource keys based on the given mapping"""

    @abc.abstractmethod
    def knownkeys(self):
        """Return a set of supported resource keys"""

    @abc.abstractmethod
    def lookup(self, mapping, key):
        """Return a resource for the key if available; otherwise None"""

    @abc.abstractmethod
    def populatemap(self, context, origmapping, newmapping):
        """Return a dict of additional mapping items which should be paired
        with the given new mapping"""


class nullresourcemapper(resourcemapper):
    def availablekeys(self, mapping):
        return set()

    def knownkeys(self):
        return set()

    def lookup(self, mapping, key):
        return None

    def populatemap(self, context, origmapping, newmapping):
        return {}


class engine:
    """template expansion engine.

    template expansion works like this. a map file contains key=value
    pairs. if value is quoted, it is treated as string. otherwise, it
    is treated as name of template file.

    templater is asked to expand a key in map. it looks up key, and
    looks for strings like this: {foo}. it expands {foo} by looking up
    foo in map, and substituting it. expansion is recursive: it stops
    when there is no more {foo} to replace.

    expansion also allows formatting and filtering.

    format uses key to expand each item in list. syntax is
    {key%format}.

    filter uses function to transform value. syntax is
    {key|filter1|filter2|...}."""

    def __init__(self, loader, filters=None, defaults=None, resources=None):
        self._loader = loader
        if filters is None:
            filters = {}
        self._filters = filters
        self._funcs = templatefuncs.funcs  # make this a parameter if needed
        if defaults is None:
            defaults = {}
        if resources is None:
            resources = nullresourcemapper()
        self._defaults = defaults
        self._resources = resources
        self._cache = {}  # key: (func, data)
        self._tmplcache = {}  # literal template: (func, data)

    def overlaymap(self, origmapping, newmapping):
        """Create combined mapping from the original mapping and partial
        mapping to override the original"""
        # do not copy symbols which overrides the defaults depending on
        # new resources, so the defaults will be re-evaluated (issue5612)
        knownres = self._resources.knownkeys()
        newres = self._resources.availablekeys(newmapping)
        mapping = {
            k: v
            for k, v in origmapping.items()
            if (
                k in knownres  # not a symbol per self.symbol()
                or newres.isdisjoint(self._defaultrequires(k))
            )
        }
        mapping.update(newmapping)
        mapping.update(
            self._resources.populatemap(self, origmapping, newmapping)
        )
        return mapping

    def _defaultrequires(self, key):
        """Resource keys required by the specified default symbol function"""
        v = self._defaults.get(key)
        if v is None or not callable(v):
            return ()
        return getattr(v, '_requires', ())

    def symbol(self, mapping, key):
        """Resolve symbol to value or function; None if nothing found"""
        v = None
        if key not in self._resources.knownkeys():
            v = mapping.get(key)
        if v is None:
            v = self._defaults.get(key)
        return v

    def availableresourcekeys(self, mapping):
        """Return a set of available resource keys based on the given mapping"""
        return self._resources.availablekeys(mapping)

    def knownresourcekeys(self):
        """Return a set of supported resource keys"""
        return self._resources.knownkeys()

    def resource(self, mapping, key):
        """Return internal data (e.g. cache) used for keyword/function
        evaluation"""
        v = self._resources.lookup(mapping, key)
        if v is None:
            raise templateutil.ResourceUnavailable(
                _(b'template resource not available: %s') % key
            )
        return v

    def _load(self, t):
        '''load, parse, and cache a template'''
        if t not in self._cache:
            x = self._loader(t)
            # put poison to cut recursion while compiling 't'
            self._cache[t] = (_runrecursivesymbol, t)
            try:
                self._cache[t] = compileexp(x, self, methods)
            except:  # re-raises
                del self._cache[t]
                raise
        return self._cache[t]

    def _parse(self, tmpl):
        """Parse and cache a literal template"""
        if tmpl not in self._tmplcache:
            x = parse(tmpl)
            self._tmplcache[tmpl] = compileexp(x, self, methods)
        return self._tmplcache[tmpl]

    def preload(self, t):
        """Load, parse, and cache the specified template if available"""
        try:
            self._load(t)
            return True
        except templateutil.TemplateNotFound:
            return False

    def process(self, t, mapping):
        """Perform expansion. t is name of map element to expand.
        mapping contains added elements for use during expansion. Is a
        generator."""
        func, data = self._load(t)
        return self._expand(func, data, mapping)

    def expand(self, tmpl, mapping):
        """Perform expansion over a literal template

        No user aliases will be expanded since this is supposed to be called
        with an internal template string.
        """
        func, data = self._parse(tmpl)
        return self._expand(func, data, mapping)

    def _expand(self, func, data, mapping):
        # populate additional items only if they don't exist in the given
        # mapping. this is slightly different from overlaymap() because the
        # initial 'revcache' may contain pre-computed items.
        extramapping = self._resources.populatemap(self, {}, mapping)
        if extramapping:
            extramapping.update(mapping)
            mapping = extramapping
        return templateutil.flatten(self, mapping, func(self, mapping, data))


def stylelist():
    path = templatedir()
    if not path:
        return _(b'no templates found, try `hg debuginstall` for more info')
    dirlist = os.listdir(path)
    stylelist = []
    for file in dirlist:
        split = file.split(b".")
        if split[-1] in (b'orig', b'rej'):
            continue
        if split[0] == b"map-cmdline":
            stylelist.append(split[1])
    return b", ".join(sorted(stylelist))


def _open_mapfile(mapfile):
    if os.path.exists(mapfile):
        return util.posixfile(mapfile, b'rb')
    raise error.Abort(
        _(b"style '%s' not found") % mapfile,
        hint=_(b"available styles: %s") % stylelist(),
    )


def _readmapfile(fp, mapfile):
    """Load template elements from the given map file"""
    if pycompat.iswindows:
        # quick hack to make sure we can process '/' in the code dealing with
        # ressource. Ideally we would make sure we use `/` instead of `ossep`
        # in the templater code, but that seems a bigger and less certain
        # change that we better left for the default branch.
        name_paths = mapfile.split(pycompat.ossep)
        mapfile = b'/'.join(name_paths)
    base = os.path.dirname(mapfile)
    conf = config.config()

    def include(rel, remap, sections):
        subresource = None
        if base:
            abs = os.path.normpath(os.path.join(base, rel))
            if os.path.isfile(abs):
                subresource = util.posixfile(abs, b'rb')
        if not subresource:
            if pycompat.ossep not in rel:
                abs = rel
                try:
                    subresource = resourceutil.open_resource(
                        b'mercurial.templates', rel
                    )
                except FileNotFoundError:
                    subresource = None
            else:
                dir = templatedir()
                if dir:
                    abs = os.path.normpath(os.path.join(dir, rel))
                    if os.path.isfile(abs):
                        subresource = util.posixfile(abs, b'rb')
        if subresource:
            data = subresource.read()
            conf.parse(
                abs,
                data,
                sections=sections,
                remap=remap,
                include=include,
            )

    data = fp.read()
    conf.parse(mapfile, data, remap={b'': b'templates'}, include=include)

    cache = {}
    tmap = {}
    aliases = []

    val = conf.get(b'templates', b'__base__')
    if val and val[0] not in b"'\"":
        # treat as a pointer to a base class for this style
        path = os.path.normpath(os.path.join(base, val))

        # fallback check in template paths
        if not os.path.exists(path):
            dir = templatedir()
            if dir is not None:
                p2 = os.path.normpath(os.path.join(dir, val))
                if os.path.isfile(p2):
                    path = p2
                else:
                    p3 = os.path.normpath(os.path.join(p2, b"map"))
                    if os.path.isfile(p3):
                        path = p3

        fp = _open_mapfile(path)
        cache, tmap, aliases = _readmapfile(fp, path)

    for key, val in conf.items(b'templates'):
        if not val:
            raise error.ParseError(
                _(b'missing value'), conf.source(b'templates', key)
            )
        if val[0] in b"'\"":
            if val[0] != val[-1]:
                raise error.ParseError(
                    _(b'unmatched quotes'), conf.source(b'templates', key)
                )
            cache[key] = unquotestring(val)
        elif key != b'__base__':
            tmap[key] = os.path.join(base, val)
    aliases.extend(conf.items(b'templatealias'))
    return cache, tmap, aliases


class loader:
    """Load template fragments optionally from a map file"""

    def __init__(self, cache, aliases):
        if cache is None:
            cache = {}
        self.cache = cache.copy()
        self._map = {}
        self._aliasmap = _aliasrules.buildmap(aliases)

    def __contains__(self, key):
        return key in self.cache or key in self._map

    def load(self, t):
        """Get parsed tree for the given template name. Use a local cache."""
        if t not in self.cache:
            try:
                mapfile, fp = open_template(self._map[t])
                self.cache[t] = fp.read()
            except KeyError as inst:
                raise templateutil.TemplateNotFound(
                    _(b'"%s" not in template map') % inst.args[0]
                )
            except IOError as inst:
                reason = _(b'template file %s: %s') % (
                    self._map[t],
                    stringutil.forcebytestr(inst.args[1]),
                )
                raise IOError(inst.args[0], encoding.strfromlocal(reason))
        return self._parse(self.cache[t])

    def _parse(self, tmpl):
        x = parse(tmpl)
        if self._aliasmap:
            x = _aliasrules.expand(self._aliasmap, x)
        return x

    def _findsymbolsused(self, tree, syms):
        if not tree:
            return
        op = tree[0]
        if op == b'symbol':
            s = tree[1]
            if s in syms[0]:
                return  # avoid recursion: s -> cache[s] -> s
            syms[0].add(s)
            if s in self.cache or s in self._map:
                # s may be a reference for named template
                self._findsymbolsused(self.load(s), syms)
            return
        if op in {b'integer', b'string'}:
            return
        # '{arg|func}' == '{func(arg)}'
        if op == b'|':
            syms[1].add(getsymbol(tree[2]))
            self._findsymbolsused(tree[1], syms)
            return
        if op == b'func':
            syms[1].add(getsymbol(tree[1]))
            self._findsymbolsused(tree[2], syms)
            return
        for x in tree[1:]:
            self._findsymbolsused(x, syms)

    def symbolsused(self, t):
        """Look up (keywords, filters/functions) referenced from the name
        template 't'

        This may load additional templates from the map file.
        """
        syms = (set(), set())
        self._findsymbolsused(self.load(t), syms)
        return syms


class templater:
    def __init__(
        self,
        filters=None,
        defaults=None,
        resources=None,
        cache=None,
        aliases=(),
        minchunk=1024,
        maxchunk=65536,
    ):
        """Create template engine optionally with preloaded template fragments

        - ``filters``: a dict of functions to transform a value into another.
        - ``defaults``: a dict of symbol values/functions; may be overridden
          by a ``mapping`` dict.
        - ``resources``: a resourcemapper object to look up internal data
          (e.g. cache), inaccessible from user template.
        - ``cache``: a dict of preloaded template fragments.
        - ``aliases``: a list of alias (name, replacement) pairs.

        self.cache may be updated later to register additional template
        fragments.
        """
        allfilters = templatefilters.filters.copy()
        if filters:
            allfilters.update(filters)
        self._loader = loader(cache, aliases)
        self._proc = engine(self._loader.load, allfilters, defaults, resources)
        self._minchunk, self._maxchunk = minchunk, maxchunk

    @classmethod
    def frommapfile(
        cls,
        mapfile,
        fp=None,
        filters=None,
        defaults=None,
        resources=None,
        cache=None,
        minchunk=1024,
        maxchunk=65536,
    ):
        """Create templater from the specified map file"""
        t = cls(filters, defaults, resources, cache, [], minchunk, maxchunk)
        if not fp:
            fp = _open_mapfile(mapfile)
        cache, tmap, aliases = _readmapfile(fp, mapfile)
        t._loader.cache.update(cache)
        t._loader._map = tmap
        t._loader._aliasmap = _aliasrules.buildmap(aliases)
        return t

    def __contains__(self, key):
        return key in self._loader

    @property
    def cache(self):
        return self._loader.cache

    # for highlight extension to insert one-time 'colorize' filter
    @property
    def _filters(self):
        return self._proc._filters

    @property
    def defaults(self):
        return self._proc._defaults

    def load(self, t):
        """Get parsed tree for the given template name. Use a local cache."""
        return self._loader.load(t)

    def symbolsuseddefault(self):
        """Look up (keywords, filters/functions) referenced from the default
        unnamed template

        This may load additional templates from the map file.
        """
        return self.symbolsused(b'')

    def symbolsused(self, t):
        """Look up (keywords, filters/functions) referenced from the name
        template 't'

        This may load additional templates from the map file.
        """
        return self._loader.symbolsused(t)

    def renderdefault(self, mapping):
        """Render the default unnamed template and return result as string"""
        return self.render(b'', mapping)

    def render(self, t, mapping):
        """Render the specified named template and return result as string"""
        return b''.join(self.generate(t, mapping))

    def generate(self, t, mapping):
        """Return a generator that renders the specified named template and
        yields chunks"""
        stream = self._proc.process(t, mapping)
        if self._minchunk:
            stream = util.increasingchunks(
                stream, min=self._minchunk, max=self._maxchunk
            )
        return stream


def templatedir():
    '''return the directory used for template files, or None.'''
    path = os.path.normpath(os.path.join(resourceutil.datapath, b'templates'))
    return path if os.path.isdir(path) else None


def open_template(name, templatepath=None):
    """returns a file-like object for the given template, and its full path

    If the name is a relative path and we're in a frozen binary, the template
    will be read from the mercurial.templates package instead. The returned path
    will then be the relative path.
    """
    # Does the name point directly to a map file?
    if os.path.isfile(name) or os.path.isabs(name):
        return name, open(name, mode='rb')

    # Does the name point to a template in the provided templatepath, or
    # in mercurial/templates/ if no path was provided?
    if templatepath is None:
        templatepath = templatedir()
    if templatepath is not None:
        f = os.path.join(templatepath, name)
        return f, open(f, mode='rb')

    # Otherwise try to read it using the resources API
    if pycompat.iswindows:
        # quick hack to make sure we can process '/' in the code dealing with
        # ressource. Ideally we would make sure we use `/` instead of `ossep`
        # in the templater code, but that seems a bigger and less certain
        # change that we better left for the default branch.
        name_paths = name.split(pycompat.ossep)
        name = b'/'.join(name_paths)
    name_parts = name.split(b'/')
    package_name = b'.'.join([b'mercurial', b'templates'] + name_parts[:-1])
    return (
        name,
        resourceutil.open_resource(package_name, name_parts[-1]),
    )


def try_open_template(name, templatepath=None):
    try:
        return open_template(name, templatepath)
    except (EnvironmentError, ImportError):
        return None, None
