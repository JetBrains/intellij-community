# minifileset.py - a simple language to select files
#
# Copyright 2017 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from .i18n import _
from . import (
    error,
    fileset,
    filesetlang,
    pycompat,
)


def _sizep(x):
    # i18n: "size" is a keyword
    expr = filesetlang.getstring(x, _(b"size requires an expression"))
    return fileset.sizematcher(expr)


def _compile(tree):
    if not tree:
        raise error.ParseError(_(b"missing argument"))
    op = tree[0]
    if op == b'withstatus':
        return _compile(tree[1])
    elif op in {b'symbol', b'string', b'kindpat'}:
        name = filesetlang.getpattern(
            tree, {b'path'}, _(b'invalid file pattern')
        )
        if name.startswith(b'**'):  # file extension test, ex. "**.tar.gz"
            ext = name[2:]
            for c in pycompat.bytestr(ext):
                if c in b'*{}[]?/\\':
                    raise error.ParseError(_(b'reserved character: %s') % c)
            return lambda n, s: n.endswith(ext)
        elif name.startswith(b'path:'):  # directory or full path test
            p = name[5:]  # prefix
            pl = len(p)
            f = lambda n, s: n.startswith(p) and (
                len(n) == pl or n[pl : pl + 1] == b'/'
            )
            return f
        raise error.ParseError(
            _(b"unsupported file pattern: %s") % name,
            hint=_(b'paths must be prefixed with "path:"'),
        )
    elif op in {b'or', b'patterns'}:
        funcs = [_compile(x) for x in tree[1:]]
        return lambda n, s: any(f(n, s) for f in funcs)
    elif op == b'and':
        func1 = _compile(tree[1])
        func2 = _compile(tree[2])
        return lambda n, s: func1(n, s) and func2(n, s)
    elif op == b'not':
        return lambda n, s: not _compile(tree[1])(n, s)
    elif op == b'func':
        symbols = {
            b'all': lambda n, s: True,
            b'none': lambda n, s: False,
            b'size': lambda n, s: _sizep(tree[2])(s),
        }

        name = filesetlang.getsymbol(tree[1])
        if name in symbols:
            return symbols[name]

        raise error.UnknownIdentifier(name, symbols.keys())
    elif op == b'minus':  # equivalent to 'x and not y'
        func1 = _compile(tree[1])
        func2 = _compile(tree[2])
        return lambda n, s: func1(n, s) and not func2(n, s)
    elif op == b'list':
        raise error.ParseError(
            _(b"can't use a list in this context"),
            hint=_(b'see \'hg help "filesets.x or y"\''),
        )
    raise error.ProgrammingError(b'illegal tree: %r' % (tree,))


def compile(text):
    """generate a function (path, size) -> bool from filter specification.

    "text" could contain the operators defined by the fileset language for
    common logic operations, and parenthesis for grouping.  The supported path
    tests are '**.extname' for file extension test, and '"path:dir/subdir"'
    for prefix test.  The ``size()`` predicate is borrowed from filesets to test
    file size.  The predicates ``all()`` and ``none()`` are also supported.

    '(**.php & size(">10MB")) | **.zip | (path:bin & !path:bin/README)' for
    example, will catch all php files whose size is greater than 10 MB, all
    files whose name ends with ".zip", and all files under "bin" in the repo
    root except for "bin/README".
    """
    tree = filesetlang.parse(text)
    tree = filesetlang.analyze(tree)
    tree = filesetlang.optimize(tree)
    return _compile(tree)
