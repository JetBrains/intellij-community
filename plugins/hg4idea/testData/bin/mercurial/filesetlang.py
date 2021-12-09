# filesetlang.py - parser, tokenizer and utility for file set language
#
# Copyright 2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from .i18n import _
from .pycompat import getattr
from . import (
    error,
    parser,
    pycompat,
)

# common weight constants for static optimization
# (see registrar.filesetpredicate for details)
WEIGHT_CHECK_FILENAME = 0.5
WEIGHT_READ_CONTENTS = 30
WEIGHT_STATUS = 10
WEIGHT_STATUS_THOROUGH = 50

elements = {
    # token-type: binding-strength, primary, prefix, infix, suffix
    b"(": (20, None, (b"group", 1, b")"), (b"func", 1, b")"), None),
    b":": (15, None, None, (b"kindpat", 15), None),
    b"-": (5, None, (b"negate", 19), (b"minus", 5), None),
    b"not": (10, None, (b"not", 10), None, None),
    b"!": (10, None, (b"not", 10), None, None),
    b"and": (5, None, None, (b"and", 5), None),
    b"&": (5, None, None, (b"and", 5), None),
    b"or": (4, None, None, (b"or", 4), None),
    b"|": (4, None, None, (b"or", 4), None),
    b"+": (4, None, None, (b"or", 4), None),
    b",": (2, None, None, (b"list", 2), None),
    b")": (0, None, None, None, None),
    b"symbol": (0, b"symbol", None, None, None),
    b"string": (0, b"string", None, None, None),
    b"end": (0, None, None, None, None),
}

keywords = {b'and', b'or', b'not'}

symbols = {}

globchars = b".*{}[]?/\\_"


def tokenize(program):
    pos, l = 0, len(program)
    program = pycompat.bytestr(program)
    while pos < l:
        c = program[pos]
        if c.isspace():  # skip inter-token whitespace
            pass
        elif c in b"(),-:|&+!":  # handle simple operators
            yield (c, None, pos)
        elif (
            c in b'"\''
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
        elif c.isalnum() or c in globchars or ord(c) > 127:
            # gather up a symbol/keyword
            s = pos
            pos += 1
            while pos < l:  # find end of symbol
                d = program[pos]
                if not (d.isalnum() or d in globchars or ord(d) > 127):
                    break
                pos += 1
            sym = program[s:pos]
            if sym in keywords:  # operator keywords
                yield (sym, None, s)
            else:
                yield (b'symbol', sym, s)
            pos -= 1
        else:
            raise error.ParseError(_(b"syntax error"), pos)
        pos += 1
    yield (b'end', None, pos)


def parse(expr):
    p = parser.parser(elements)
    tree, pos = p.parse(tokenize(expr))
    if pos != len(expr):
        raise error.ParseError(_(b"invalid token"), pos)
    return parser.simplifyinfixops(tree, {b'list', b'or'})


def getsymbol(x):
    if x and x[0] == b'symbol':
        return x[1]
    raise error.ParseError(_(b'not a symbol'))


def getstring(x, err):
    if x and (x[0] == b'string' or x[0] == b'symbol'):
        return x[1]
    raise error.ParseError(err)


def getkindpat(x, y, allkinds, err):
    kind = getsymbol(x)
    pat = getstring(y, err)
    if kind not in allkinds:
        raise error.ParseError(_(b"invalid pattern kind: %s") % kind)
    return b'%s:%s' % (kind, pat)


def getpattern(x, allkinds, err):
    if x and x[0] == b'kindpat':
        return getkindpat(x[1], x[2], allkinds, err)
    return getstring(x, err)


def getlist(x):
    if not x:
        return []
    if x[0] == b'list':
        return list(x[1:])
    return [x]


def getargs(x, min, max, err):
    l = getlist(x)
    if len(l) < min or len(l) > max:
        raise error.ParseError(err)
    return l


def _analyze(x):
    if x is None:
        return x

    op = x[0]
    if op in {b'string', b'symbol'}:
        return x
    if op == b'kindpat':
        getsymbol(x[1])  # kind must be a symbol
        t = _analyze(x[2])
        return (op, x[1], t)
    if op == b'group':
        return _analyze(x[1])
    if op == b'negate':
        raise error.ParseError(_(b"can't use negate operator in this context"))
    if op == b'not':
        t = _analyze(x[1])
        return (op, t)
    if op == b'and':
        ta = _analyze(x[1])
        tb = _analyze(x[2])
        return (op, ta, tb)
    if op == b'minus':
        return _analyze((b'and', x[1], (b'not', x[2])))
    if op in {b'list', b'or'}:
        ts = tuple(_analyze(y) for y in x[1:])
        return (op,) + ts
    if op == b'func':
        getsymbol(x[1])  # function name must be a symbol
        ta = _analyze(x[2])
        return (op, x[1], ta)
    raise error.ProgrammingError(b'invalid operator %r' % op)


def _insertstatushints(x):
    """Insert hint nodes where status should be calculated (first path)

    This works in bottom-up way, summing up status names and inserting hint
    nodes at 'and' and 'or' as needed. Thus redundant hint nodes may be left.

    Returns (status-names, new-tree) at the given subtree, where status-names
    is a sum of status names referenced in the given subtree.
    """
    if x is None:
        return (), x

    op = x[0]
    if op in {b'string', b'symbol', b'kindpat'}:
        return (), x
    if op == b'not':
        h, t = _insertstatushints(x[1])
        return h, (op, t)
    if op == b'and':
        ha, ta = _insertstatushints(x[1])
        hb, tb = _insertstatushints(x[2])
        hr = ha + hb
        if ha and hb:
            return hr, (b'withstatus', (op, ta, tb), (b'string', b' '.join(hr)))
        return hr, (op, ta, tb)
    if op == b'or':
        hs, ts = zip(*(_insertstatushints(y) for y in x[1:]))
        hr = sum(hs, ())
        if sum(bool(h) for h in hs) > 1:
            return hr, (b'withstatus', (op,) + ts, (b'string', b' '.join(hr)))
        return hr, (op,) + ts
    if op == b'list':
        hs, ts = zip(*(_insertstatushints(y) for y in x[1:]))
        return sum(hs, ()), (op,) + ts
    if op == b'func':
        f = getsymbol(x[1])
        # don't propagate 'ha' crossing a function boundary
        ha, ta = _insertstatushints(x[2])
        if getattr(symbols.get(f), '_callstatus', False):
            return (f,), (b'withstatus', (op, x[1], ta), (b'string', f))
        return (), (op, x[1], ta)
    raise error.ProgrammingError(b'invalid operator %r' % op)


def _mergestatushints(x, instatus):
    """Remove redundant status hint nodes (second path)

    This is the top-down path to eliminate inner hint nodes.
    """
    if x is None:
        return x

    op = x[0]
    if op == b'withstatus':
        if instatus:
            # drop redundant hint node
            return _mergestatushints(x[1], instatus)
        t = _mergestatushints(x[1], instatus=True)
        return (op, t, x[2])
    if op in {b'string', b'symbol', b'kindpat'}:
        return x
    if op == b'not':
        t = _mergestatushints(x[1], instatus)
        return (op, t)
    if op == b'and':
        ta = _mergestatushints(x[1], instatus)
        tb = _mergestatushints(x[2], instatus)
        return (op, ta, tb)
    if op in {b'list', b'or'}:
        ts = tuple(_mergestatushints(y, instatus) for y in x[1:])
        return (op,) + ts
    if op == b'func':
        # don't propagate 'instatus' crossing a function boundary
        ta = _mergestatushints(x[2], instatus=False)
        return (op, x[1], ta)
    raise error.ProgrammingError(b'invalid operator %r' % op)


def analyze(x):
    """Transform raw parsed tree to evaluatable tree which can be fed to
    optimize() or getmatch()

    All pseudo operations should be mapped to real operations or functions
    defined in methods or symbols table respectively.
    """
    t = _analyze(x)
    _h, t = _insertstatushints(t)
    return _mergestatushints(t, instatus=False)


def _optimizeandops(op, ta, tb):
    if tb is not None and tb[0] == b'not':
        return (b'minus', ta, tb[1])
    return (op, ta, tb)


def _optimizeunion(xs):
    # collect string patterns so they can be compiled into a single regexp
    ws, ts, ss = [], [], []
    for x in xs:
        w, t = _optimize(x)
        if t is not None and t[0] in {b'string', b'symbol', b'kindpat'}:
            ss.append(t)
            continue
        ws.append(w)
        ts.append(t)
    if ss:
        ws.append(WEIGHT_CHECK_FILENAME)
        ts.append((b'patterns',) + tuple(ss))
    return ws, ts


def _optimize(x):
    if x is None:
        return 0, x

    op = x[0]
    if op == b'withstatus':
        w, t = _optimize(x[1])
        return w, (op, t, x[2])
    if op in {b'string', b'symbol'}:
        return WEIGHT_CHECK_FILENAME, x
    if op == b'kindpat':
        w, t = _optimize(x[2])
        return w, (op, x[1], t)
    if op == b'not':
        w, t = _optimize(x[1])
        return w, (op, t)
    if op == b'and':
        wa, ta = _optimize(x[1])
        wb, tb = _optimize(x[2])
        if wa <= wb:
            return wa, _optimizeandops(op, ta, tb)
        else:
            return wb, _optimizeandops(op, tb, ta)
    if op == b'or':
        ws, ts = _optimizeunion(x[1:])
        if len(ts) == 1:
            return ws[0], ts[0]  # 'or' operation is fully optimized out
        ts = tuple(
            it[1] for it in sorted(enumerate(ts), key=lambda it: ws[it[0]])
        )
        return max(ws), (op,) + ts
    if op == b'list':
        ws, ts = zip(*(_optimize(y) for y in x[1:]))
        return sum(ws), (op,) + ts
    if op == b'func':
        f = getsymbol(x[1])
        w = getattr(symbols.get(f), '_weight', 1)
        wa, ta = _optimize(x[2])
        return w + wa, (op, x[1], ta)
    raise error.ProgrammingError(b'invalid operator %r' % op)


def optimize(x):
    """Reorder/rewrite evaluatable tree for optimization

    All pseudo operations should be transformed beforehand.
    """
    _w, t = _optimize(x)
    return t


def prettyformat(tree):
    return parser.prettyformat(tree, (b'string', b'symbol'))
