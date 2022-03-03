# revsetlang.py - parser, tokenizer and utility for revision set language
#
# Copyright 2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import string

from .i18n import _
from .pycompat import getattr
from .node import hex
from . import (
    error,
    parser,
    pycompat,
    smartset,
    util,
)
from .utils import stringutil

elements = {
    # token-type: binding-strength, primary, prefix, infix, suffix
    b"(": (21, None, (b"group", 1, b")"), (b"func", 1, b")"), None),
    b"[": (21, None, None, (b"subscript", 1, b"]"), None),
    b"#": (21, None, None, (b"relation", 21), None),
    b"##": (20, None, None, (b"_concat", 20), None),
    b"~": (18, None, None, (b"ancestor", 18), None),
    b"^": (18, None, None, (b"parent", 18), b"parentpost"),
    b"-": (5, None, (b"negate", 19), (b"minus", 5), None),
    b"::": (
        17,
        b"dagrangeall",
        (b"dagrangepre", 17),
        (b"dagrange", 17),
        b"dagrangepost",
    ),
    b"..": (
        17,
        b"dagrangeall",
        (b"dagrangepre", 17),
        (b"dagrange", 17),
        b"dagrangepost",
    ),
    b":": (15, b"rangeall", (b"rangepre", 15), (b"range", 15), b"rangepost"),
    b"not": (10, None, (b"not", 10), None, None),
    b"!": (10, None, (b"not", 10), None, None),
    b"and": (5, None, None, (b"and", 5), None),
    b"&": (5, None, None, (b"and", 5), None),
    b"%": (5, None, None, (b"only", 5), b"onlypost"),
    b"or": (4, None, None, (b"or", 4), None),
    b"|": (4, None, None, (b"or", 4), None),
    b"+": (4, None, None, (b"or", 4), None),
    b"=": (3, None, None, (b"keyvalue", 3), None),
    b",": (2, None, None, (b"list", 2), None),
    b")": (0, None, None, None, None),
    b"]": (0, None, None, None, None),
    b"symbol": (0, b"symbol", None, None, None),
    b"string": (0, b"string", None, None, None),
    b"end": (0, None, None, None, None),
}

keywords = {b'and', b'or', b'not'}

symbols = {}

_quoteletters = {b'"', b"'"}
_simpleopletters = set(pycompat.iterbytestr(b"()[]#:=,-|&+!~^%"))

# default set of valid characters for the initial letter of symbols
_syminitletters = set(
    pycompat.iterbytestr(
        pycompat.sysbytes(string.ascii_letters)
        + pycompat.sysbytes(string.digits)
        + b'._@'
    )
) | set(map(pycompat.bytechr, pycompat.xrange(128, 256)))

# default set of valid characters for non-initial letters of symbols
_symletters = _syminitletters | set(pycompat.iterbytestr(b'-/'))


def tokenize(program, lookup=None, syminitletters=None, symletters=None):
    """
    Parse a revset statement into a stream of tokens

    ``syminitletters`` is the set of valid characters for the initial
    letter of symbols.

    By default, character ``c`` is recognized as valid for initial
    letter of symbols, if ``c.isalnum() or c in '._@' or ord(c) > 127``.

    ``symletters`` is the set of valid characters for non-initial
    letters of symbols.

    By default, character ``c`` is recognized as valid for non-initial
    letters of symbols, if ``c.isalnum() or c in '-._/@' or ord(c) > 127``.

    Check that @ is a valid unquoted token character (issue3686):
    >>> list(tokenize(b"@::"))
    [('symbol', '@', 0), ('::', None, 1), ('end', None, 3)]

    """
    if not isinstance(program, bytes):
        raise error.ProgrammingError(
            b'revset statement must be bytes, got %r' % program
        )
    program = pycompat.bytestr(program)
    if syminitletters is None:
        syminitletters = _syminitletters
    if symletters is None:
        symletters = _symletters

    if program and lookup:
        # attempt to parse old-style ranges first to deal with
        # things like old-tag which contain query metacharacters
        parts = program.split(b':', 1)
        if all(lookup(sym) for sym in parts if sym):
            if parts[0]:
                yield (b'symbol', parts[0], 0)
            if len(parts) > 1:
                s = len(parts[0])
                yield (b':', None, s)
                if parts[1]:
                    yield (b'symbol', parts[1], s + 1)
            yield (b'end', None, len(program))
            return

    pos, l = 0, len(program)
    while pos < l:
        c = program[pos]
        if c.isspace():  # skip inter-token whitespace
            pass
        elif (
            c == b':' and program[pos : pos + 2] == b'::'
        ):  # look ahead carefully
            yield (b'::', None, pos)
            pos += 1  # skip ahead
        elif (
            c == b'.' and program[pos : pos + 2] == b'..'
        ):  # look ahead carefully
            yield (b'..', None, pos)
            pos += 1  # skip ahead
        elif (
            c == b'#' and program[pos : pos + 2] == b'##'
        ):  # look ahead carefully
            yield (b'##', None, pos)
            pos += 1  # skip ahead
        elif c in _simpleopletters:  # handle simple operators
            yield (c, None, pos)
        elif (
            c in _quoteletters
            or c == b'r'
            and program[pos : pos + 2] in (b"r'", b'r"')
        ):  # handle quoted strings
            if c == b'r':
                pos += 1
                c = program[pos]
                decode = lambda x: x
            else:
                decode = parser.unescapestr
            pos += 1
            s = pos
            while pos < l:  # find closing quote
                d = program[pos]
                if d == b'\\':  # skip over escaped characters
                    pos += 2
                    continue
                if d == c:
                    yield (b'string', decode(program[s:pos]), s)
                    break
                pos += 1
            else:
                raise error.ParseError(_(b"unterminated string"), s)
        # gather up a symbol/keyword
        elif c in syminitletters:
            s = pos
            pos += 1
            while pos < l:  # find end of symbol
                d = program[pos]
                if d not in symletters:
                    break
                if (
                    d == b'.' and program[pos - 1] == b'.'
                ):  # special case for ..
                    pos -= 1
                    break
                pos += 1
            sym = program[s:pos]
            if sym in keywords:  # operator keywords
                yield (sym, None, s)
            elif b'-' in sym:
                # some jerk gave us foo-bar-baz, try to check if it's a symbol
                if lookup and lookup(sym):
                    # looks like a real symbol
                    yield (b'symbol', sym, s)
                else:
                    # looks like an expression
                    parts = sym.split(b'-')
                    for p in parts[:-1]:
                        if p:  # possible consecutive -
                            yield (b'symbol', p, s)
                        s += len(p)
                        yield (b'-', None, s)
                        s += 1
                    if parts[-1]:  # possible trailing -
                        yield (b'symbol', parts[-1], s)
            else:
                yield (b'symbol', sym, s)
            pos -= 1
        else:
            raise error.ParseError(
                _(b"syntax error in revset '%s'") % program, pos
            )
        pos += 1
    yield (b'end', None, pos)


# helpers

_notset = object()


def getsymbol(x):
    if x and x[0] == b'symbol':
        return x[1]
    raise error.ParseError(_(b'not a symbol'))


def getstring(x, err):
    if x and (x[0] == b'string' or x[0] == b'symbol'):
        return x[1]
    raise error.ParseError(err)


def getinteger(x, err, default=_notset):
    if not x and default is not _notset:
        return default
    try:
        return int(getstring(x, err))
    except ValueError:
        raise error.ParseError(err)


def getboolean(x, err):
    value = stringutil.parsebool(getsymbol(x))
    if value is not None:
        return value
    raise error.ParseError(err)


def getlist(x):
    if not x:
        return []
    if x[0] == b'list':
        return list(x[1:])
    return [x]


def getrange(x, err):
    if not x:
        raise error.ParseError(err)
    op = x[0]
    if op == b'range':
        return x[1], x[2]
    elif op == b'rangepre':
        return None, x[1]
    elif op == b'rangepost':
        return x[1], None
    elif op == b'rangeall':
        return None, None
    raise error.ParseError(err)


def getintrange(x, err1, err2, deffirst=_notset, deflast=_notset):
    """Get [first, last] integer range (both inclusive) from a parsed tree

    If any of the sides omitted, and if no default provided, ParseError will
    be raised.
    """
    if x and (x[0] == b'string' or x[0] == b'symbol'):
        n = getinteger(x, err1)
        return n, n
    a, b = getrange(x, err1)
    return getinteger(a, err2, deffirst), getinteger(b, err2, deflast)


def getargs(x, min, max, err):
    l = getlist(x)
    if len(l) < min or (max >= 0 and len(l) > max):
        raise error.ParseError(err)
    return l


def getargsdict(x, funcname, keys):
    return parser.buildargsdict(
        getlist(x),
        funcname,
        parser.splitargspec(keys),
        keyvaluenode=b'keyvalue',
        keynode=b'symbol',
    )


# cache of {spec: raw parsed tree} built internally
_treecache = {}


def _cachedtree(spec):
    # thread safe because parse() is reentrant and dict.__setitem__() is atomic
    tree = _treecache.get(spec)
    if tree is None:
        _treecache[spec] = tree = parse(spec)
    return tree


def _build(tmplspec, *repls):
    """Create raw parsed tree from a template revset statement

    >>> _build(b'f(_) and _', (b'string', b'1'), (b'symbol', b'2'))
    ('and', ('func', ('symbol', 'f'), ('string', '1')), ('symbol', '2'))
    """
    template = _cachedtree(tmplspec)
    return parser.buildtree(template, (b'symbol', b'_'), *repls)


def _match(patspec, tree):
    """Test if a tree matches the given pattern statement; return the matches

    >>> _match(b'f(_)', parse(b'f()'))
    >>> _match(b'f(_)', parse(b'f(1)'))
    [('func', ('symbol', 'f'), ('symbol', '1')), ('symbol', '1')]
    >>> _match(b'f(_)', parse(b'f(1, 2)'))
    """
    pattern = _cachedtree(patspec)
    return parser.matchtree(
        pattern, tree, (b'symbol', b'_'), {b'keyvalue', b'list'}
    )


def _matchonly(revs, bases):
    return _match(b'ancestors(_) and not ancestors(_)', (b'and', revs, bases))


def _fixops(x):
    """Rewrite raw parsed tree to resolve ambiguous syntax which cannot be
    handled well by our simple top-down parser"""
    if not isinstance(x, tuple):
        return x

    op = x[0]
    if op == b'parent':
        # x^:y means (x^) : y, not x ^ (:y)
        # x^:  means (x^) :,   not x ^ (:)
        post = (b'parentpost', x[1])
        if x[2][0] == b'dagrangepre':
            return _fixops((b'dagrange', post, x[2][1]))
        elif x[2][0] == b'dagrangeall':
            return _fixops((b'dagrangepost', post))
        elif x[2][0] == b'rangepre':
            return _fixops((b'range', post, x[2][1]))
        elif x[2][0] == b'rangeall':
            return _fixops((b'rangepost', post))
    elif op == b'or':
        # make number of arguments deterministic:
        # x + y + z -> (or x y z) -> (or (list x y z))
        return (op, _fixops((b'list',) + x[1:]))
    elif op == b'subscript' and x[1][0] == b'relation':
        # x#y[z] ternary
        return _fixops((b'relsubscript', x[1][1], x[1][2], x[2]))

    return (op,) + tuple(_fixops(y) for y in x[1:])


def _analyze(x):
    if x is None:
        return x

    op = x[0]
    if op == b'minus':
        return _analyze(_build(b'_ and not _', *x[1:]))
    elif op == b'only':
        return _analyze(_build(b'only(_, _)', *x[1:]))
    elif op == b'onlypost':
        return _analyze(_build(b'only(_)', x[1]))
    elif op == b'dagrangeall':
        raise error.ParseError(_(b"can't use '::' in this context"))
    elif op == b'dagrangepre':
        return _analyze(_build(b'ancestors(_)', x[1]))
    elif op == b'dagrangepost':
        return _analyze(_build(b'descendants(_)', x[1]))
    elif op == b'negate':
        s = getstring(x[1], _(b"can't negate that"))
        return _analyze((b'string', b'-' + s))
    elif op in (b'string', b'symbol', b'smartset'):
        return x
    elif op == b'rangeall':
        return (op, None)
    elif op in {b'or', b'not', b'rangepre', b'rangepost', b'parentpost'}:
        return (op, _analyze(x[1]))
    elif op == b'group':
        return _analyze(x[1])
    elif op in {
        b'and',
        b'dagrange',
        b'range',
        b'parent',
        b'ancestor',
        b'relation',
        b'subscript',
    }:
        ta = _analyze(x[1])
        tb = _analyze(x[2])
        return (op, ta, tb)
    elif op == b'relsubscript':
        ta = _analyze(x[1])
        tb = _analyze(x[2])
        tc = _analyze(x[3])
        return (op, ta, tb, tc)
    elif op == b'list':
        return (op,) + tuple(_analyze(y) for y in x[1:])
    elif op == b'keyvalue':
        return (op, x[1], _analyze(x[2]))
    elif op == b'func':
        return (op, x[1], _analyze(x[2]))
    raise ValueError(b'invalid operator %r' % op)


def analyze(x):
    """Transform raw parsed tree to evaluatable tree which can be fed to
    optimize() or getset()

    All pseudo operations should be mapped to real operations or functions
    defined in methods or symbols table respectively.
    """
    return _analyze(x)


def _optimize(x):
    if x is None:
        return 0, x

    op = x[0]
    if op in (b'string', b'symbol', b'smartset'):
        return 0.5, x  # single revisions are small
    elif op == b'and':
        wa, ta = _optimize(x[1])
        wb, tb = _optimize(x[2])
        w = min(wa, wb)

        # (draft/secret/_notpublic() & ::x) have a fast path
        m = _match(b'_() & ancestors(_)', (b'and', ta, tb))
        if m and getsymbol(m[1]) in {b'draft', b'secret', b'_notpublic'}:
            return w, _build(b'_phaseandancestors(_, _)', m[1], m[2])

        # (::x and not ::y)/(not ::y and ::x) have a fast path
        m = _matchonly(ta, tb) or _matchonly(tb, ta)
        if m:
            return w, _build(b'only(_, _)', *m[1:])

        m = _match(b'not _', tb)
        if m:
            return wa, (b'difference', ta, m[1])
        if wa > wb:
            op = b'andsmally'
        return w, (op, ta, tb)
    elif op == b'or':
        # fast path for machine-generated expression, that is likely to have
        # lots of trivial revisions: 'a + b + c()' to '_list(a b) + c()'
        ws, ts, ss = [], [], []

        def flushss():
            if not ss:
                return
            if len(ss) == 1:
                w, t = ss[0]
            else:
                s = b'\0'.join(t[1] for w, t in ss)
                y = _build(b'_list(_)', (b'string', s))
                w, t = _optimize(y)
            ws.append(w)
            ts.append(t)
            del ss[:]

        for y in getlist(x[1]):
            w, t = _optimize(y)
            if t is not None and (t[0] == b'string' or t[0] == b'symbol'):
                ss.append((w, t))
                continue
            flushss()
            ws.append(w)
            ts.append(t)
        flushss()
        if len(ts) == 1:
            return ws[0], ts[0]  # 'or' operation is fully optimized out
        return max(ws), (op, (b'list',) + tuple(ts))
    elif op == b'not':
        # Optimize not public() to _notpublic() because we have a fast version
        if _match(b'public()', x[1]):
            o = _optimize(_build(b'_notpublic()'))
            return o[0], o[1]
        else:
            o = _optimize(x[1])
            return o[0], (op, o[1])
    elif op == b'rangeall':
        return 1, x
    elif op in (b'rangepre', b'rangepost', b'parentpost'):
        o = _optimize(x[1])
        return o[0], (op, o[1])
    elif op in (b'dagrange', b'range'):
        wa, ta = _optimize(x[1])
        wb, tb = _optimize(x[2])
        return wa + wb, (op, ta, tb)
    elif op in (b'parent', b'ancestor', b'relation', b'subscript'):
        w, t = _optimize(x[1])
        return w, (op, t, x[2])
    elif op == b'relsubscript':
        w, t = _optimize(x[1])
        return w, (op, t, x[2], x[3])
    elif op == b'list':
        ws, ts = zip(*(_optimize(y) for y in x[1:]))
        return sum(ws), (op,) + ts
    elif op == b'keyvalue':
        w, t = _optimize(x[2])
        return w, (op, x[1], t)
    elif op == b'func':
        f = getsymbol(x[1])
        wa, ta = _optimize(x[2])
        w = getattr(symbols.get(f), '_weight', 1)
        m = _match(b'commonancestors(_)', ta)

        # Optimize heads(commonancestors(_)) because we have a fast version
        if f == b'heads' and m:
            return w + wa, _build(b'_commonancestorheads(_)', m[1])

        return w + wa, (op, x[1], ta)
    raise ValueError(b'invalid operator %r' % op)


def optimize(tree):
    """Optimize evaluatable tree

    All pseudo operations should be transformed beforehand.
    """
    _weight, newtree = _optimize(tree)
    return newtree


# the set of valid characters for the initial letter of symbols in
# alias declarations and definitions
_aliassyminitletters = _syminitletters | {b'$'}


def _parsewith(spec, lookup=None, syminitletters=None):
    """Generate a parse tree of given spec with given tokenizing options

    >>> _parsewith(b'foo($1)', syminitletters=_aliassyminitletters)
    ('func', ('symbol', 'foo'), ('symbol', '$1'))
    >>> from . import error
    >>> from . import pycompat
    >>> try:
    ...   _parsewith(b'$1')
    ... except error.ParseError as e:
    ...   pycompat.sysstr(e.message)
    ...   e.location
    "syntax error in revset '$1'"
    0
    >>> try:
    ...   _parsewith(b'foo bar')
    ... except error.ParseError as e:
    ...   pycompat.sysstr(e.message)
    ...   e.location
    'invalid token'
    4
    """
    if lookup and spec.startswith(b'revset(') and spec.endswith(b')'):
        lookup = None
    p = parser.parser(elements)
    tree, pos = p.parse(
        tokenize(spec, lookup=lookup, syminitletters=syminitletters)
    )
    if pos != len(spec):
        raise error.ParseError(_(b'invalid token'), pos)
    return _fixops(parser.simplifyinfixops(tree, (b'list', b'or')))


class _aliasrules(parser.basealiasrules):
    """Parsing and expansion rule set of revset aliases"""

    _section = _(b'revset alias')

    @staticmethod
    def _parse(spec):
        """Parse alias declaration/definition ``spec``

        This allows symbol names to use also ``$`` as an initial letter
        (for backward compatibility), and callers of this function should
        examine whether ``$`` is used also for unexpected symbols or not.
        """
        return _parsewith(spec, syminitletters=_aliassyminitletters)

    @staticmethod
    def _trygetfunc(tree):
        if tree[0] == b'func' and tree[1][0] == b'symbol':
            return tree[1][1], getlist(tree[2])


def expandaliases(tree, aliases, warn=None):
    """Expand aliases in a tree, aliases is a list of (name, value) tuples"""
    aliases = _aliasrules.buildmap(aliases)
    tree = _aliasrules.expand(aliases, tree)
    # warn about problematic (but not referred) aliases
    if warn is not None:
        for name, alias in sorted(pycompat.iteritems(aliases)):
            if alias.error and not alias.warned:
                warn(_(b'warning: %s\n') % (alias.error))
                alias.warned = True
    return tree


def foldconcat(tree):
    """Fold elements to be concatenated by `##`"""
    if not isinstance(tree, tuple) or tree[0] in (
        b'string',
        b'symbol',
        b'smartset',
    ):
        return tree
    if tree[0] == b'_concat':
        pending = [tree]
        l = []
        while pending:
            e = pending.pop()
            if e[0] == b'_concat':
                pending.extend(reversed(e[1:]))
            elif e[0] in (b'string', b'symbol'):
                l.append(e[1])
            else:
                msg = _(b"\"##\" can't concatenate \"%s\" element") % (e[0])
                raise error.ParseError(msg)
        return (b'string', b''.join(l))
    else:
        return tuple(foldconcat(t) for t in tree)


def parse(spec, lookup=None):
    try:
        return _parsewith(spec, lookup=lookup)
    except error.ParseError as inst:
        if inst.location is not None:
            loc = inst.location
            # Remove newlines -- spaces are equivalent whitespace.
            spec = spec.replace(b'\n', b' ')
            # We want the caret to point to the place in the template that
            # failed to parse, but in a hint we get a open paren at the
            # start. Therefore, we print "loc + 1" spaces (instead of "loc")
            # to line up the caret with the location of the error.
            inst.hint = spec + b'\n' + b' ' * (loc + 1) + b'^ ' + _(b'here')
        raise


def _quote(s):
    r"""Quote a value in order to make it safe for the revset engine.

    >>> _quote(b'asdf')
    "'asdf'"
    >>> _quote(b"asdf'\"")
    '\'asdf\\\'"\''
    >>> _quote(b'asdf\'')
    "'asdf\\''"
    >>> _quote(1)
    "'1'"
    """
    return b"'%s'" % stringutil.escapestr(pycompat.bytestr(s))


def _formatargtype(c, arg):
    if c == b'd':
        return b'_rev(%d)' % int(arg)
    elif c == b's':
        return _quote(arg)
    elif c == b'r':
        if not isinstance(arg, bytes):
            raise TypeError
        parse(arg)  # make sure syntax errors are confined
        return b'(%s)' % arg
    elif c == b'n':
        return _quote(hex(arg))
    elif c == b'b':
        try:
            return _quote(arg.branch())
        except AttributeError:
            raise TypeError
    raise error.ParseError(_(b'unexpected revspec format character %s') % c)


def _formatlistexp(s, t):
    l = len(s)
    if l == 0:
        return b"_list('')"
    elif l == 1:
        return _formatargtype(t, s[0])
    elif t == b'd':
        return _formatintlist(s)
    elif t == b's':
        return b"_list(%s)" % _quote(b"\0".join(s))
    elif t == b'n':
        return b"_hexlist('%s')" % b"\0".join(hex(a) for a in s)
    elif t == b'b':
        try:
            return b"_list('%s')" % b"\0".join(a.branch() for a in s)
        except AttributeError:
            raise TypeError

    m = l // 2
    return b'(%s or %s)' % (_formatlistexp(s[:m], t), _formatlistexp(s[m:], t))


def _formatintlist(data):
    try:
        l = len(data)
        if l == 0:
            return b"_list('')"
        elif l == 1:
            return _formatargtype(b'd', data[0])
        return b"_intlist('%s')" % b"\0".join(b'%d' % int(a) for a in data)
    except (TypeError, ValueError):
        raise error.ParseError(_(b'invalid argument for revspec'))


def _formatparamexp(args, t):
    return b', '.join(_formatargtype(t, a) for a in args)


_formatlistfuncs = {
    b'l': _formatlistexp,
    b'p': _formatparamexp,
}


def formatspec(expr, *args):
    """
    This is a convenience function for using revsets internally, and
    escapes arguments appropriately. Aliases are intentionally ignored
    so that intended expression behavior isn't accidentally subverted.

    Supported arguments:

    %r = revset expression, parenthesized
    %d = rev(int(arg)), no quoting
    %s = string(arg), escaped and single-quoted
    %b = arg.branch(), escaped and single-quoted
    %n = hex(arg), single-quoted
    %% = a literal '%'

    Prefixing the type with 'l' specifies a parenthesized list of that type,
    and 'p' specifies a list of function parameters of that type.

    >>> formatspec(b'%r:: and %lr', b'10 or 11', (b"this()", b"that()"))
    '(10 or 11):: and ((this()) or (that()))'
    >>> formatspec(b'%d:: and not %d::', 10, 20)
    '_rev(10):: and not _rev(20)::'
    >>> formatspec(b'%ld or %ld', [], [1])
    "_list('') or _rev(1)"
    >>> formatspec(b'keyword(%s)', b'foo\\xe9')
    "keyword('foo\\\\xe9')"
    >>> b = lambda: b'default'
    >>> b.branch = b
    >>> formatspec(b'branch(%b)', b)
    "branch('default')"
    >>> formatspec(b'root(%ls)', [b'a', b'b', b'c', b'd'])
    "root(_list('a\\\\x00b\\\\x00c\\\\x00d'))"
    >>> formatspec(b'sort(%r, %ps)', b':', [b'desc', b'user'])
    "sort((:), 'desc', 'user')"
    >>> formatspec(b'%ls', [b'a', b"'"])
    "_list('a\\\\x00\\\\'')"
    """
    parsed = _parseargs(expr, args)
    ret = []
    for t, arg in parsed:
        if t is None:
            ret.append(arg)
        elif t == b'baseset':
            if isinstance(arg, set):
                arg = sorted(arg)
            ret.append(_formatintlist(list(arg)))
        else:
            raise error.ProgrammingError(b"unknown revspec item type: %r" % t)
    return b''.join(ret)


def spectree(expr, *args):
    """similar to formatspec but return a parsed and optimized tree"""
    parsed = _parseargs(expr, args)
    ret = []
    inputs = []
    for t, arg in parsed:
        if t is None:
            ret.append(arg)
        elif t == b'baseset':
            newtree = (b'smartset', smartset.baseset(arg))
            inputs.append(newtree)
            ret.append(b"$")
        else:
            raise error.ProgrammingError(b"unknown revspec item type: %r" % t)
    expr = b''.join(ret)
    tree = _parsewith(expr, syminitletters=_aliassyminitletters)
    tree = parser.buildtree(tree, (b'symbol', b'$'), *inputs)
    tree = foldconcat(tree)
    tree = analyze(tree)
    tree = optimize(tree)
    return tree


def _parseargs(expr, args):
    """parse the expression and replace all inexpensive args

    return a list of tuple [(arg-type, arg-value)]

    Arg-type can be:
    * None:      a string ready to be concatenated into a final spec
    * 'baseset': an iterable of revisions
    """
    expr = pycompat.bytestr(expr)
    argiter = iter(args)
    ret = []
    pos = 0
    while pos < len(expr):
        q = expr.find(b'%', pos)
        if q < 0:
            ret.append((None, expr[pos:]))
            break
        ret.append((None, expr[pos:q]))
        pos = q + 1
        try:
            d = expr[pos]
        except IndexError:
            raise error.ParseError(_(b'incomplete revspec format character'))
        if d == b'%':
            ret.append((None, d))
            pos += 1
            continue

        try:
            arg = next(argiter)
        except StopIteration:
            raise error.ParseError(_(b'missing argument for revspec'))
        f = _formatlistfuncs.get(d)
        if f:
            # a list of some type, might be expensive, do not replace
            pos += 1
            islist = d == b'l'
            try:
                d = expr[pos]
            except IndexError:
                raise error.ParseError(
                    _(b'incomplete revspec format character')
                )
            if islist and d == b'd' and arg:
                # we don't create a baseset yet, because it come with an
                # extra cost. If we are going to serialize it we better
                # skip it.
                ret.append((b'baseset', arg))
                pos += 1
                continue
            try:
                ret.append((None, f(list(arg), d)))
            except (TypeError, ValueError):
                raise error.ParseError(_(b'invalid argument for revspec'))
        else:
            # a single entry, not expensive, replace
            try:
                ret.append((None, _formatargtype(d, arg)))
            except (TypeError, ValueError):
                raise error.ParseError(_(b'invalid argument for revspec'))
        pos += 1

    try:
        next(argiter)
        raise error.ParseError(_(b'too many revspec arguments specified'))
    except StopIteration:
        pass
    return ret


def prettyformat(tree):
    return parser.prettyformat(tree, (b'string', b'symbol'))


def depth(tree):
    if isinstance(tree, tuple):
        return max(map(depth, tree)) + 1
    else:
        return 0


def funcsused(tree):
    if not isinstance(tree, tuple) or tree[0] in (b'string', b'symbol'):
        return set()
    else:
        funcs = set()
        for s in tree[1:]:
            funcs |= funcsused(s)
        if tree[0] == b'func':
            funcs.add(tree[1][1])
        return funcs


_hashre = util.re.compile(b'[0-9a-fA-F]{1,40}$')


def _ishashlikesymbol(symbol):
    """returns true if the symbol looks like a hash"""
    return _hashre.match(symbol)


def gethashlikesymbols(tree):
    """returns the list of symbols of the tree that look like hashes

    >>> gethashlikesymbols(parse(b'3::abe3ff'))
    ['3', 'abe3ff']
    >>> gethashlikesymbols(parse(b'precursors(.)'))
    []
    >>> gethashlikesymbols(parse(b'precursors(34)'))
    ['34']
    >>> gethashlikesymbols(parse(b'abe3ffZ'))
    []
    """
    if not tree:
        return []

    if tree[0] == b"symbol":
        if _ishashlikesymbol(tree[1]):
            return [tree[1]]
    elif len(tree) >= 3:
        results = []
        for subtree in tree[1:]:
            results += gethashlikesymbols(subtree)
        return results
    return []
