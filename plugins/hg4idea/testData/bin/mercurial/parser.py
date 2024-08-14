# parser.py - simple top-down operator precedence parser for mercurial
#
# Copyright 2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# see http://effbot.org/zone/simple-top-down-parsing.htm and
# http://eli.thegreenplace.net/2010/01/02/top-down-operator-precedence-parsing/
# for background

# takes a tokenizer and elements
# tokenizer is an iterator that returns (type, value, pos) tuples
# elements is a mapping of types to binding strength, primary, prefix, infix
# and suffix actions
# an action is a tree node name, a tree label, and an optional match
# __call__(program) parses program into a labeled tree


from .i18n import _
from . import (
    error,
    util,
)
from .utils import stringutil


class parser:
    def __init__(self, elements, methods=None):
        self._elements = elements
        self._methods = methods
        self.current = None

    def _advance(self):
        """advance the tokenizer"""
        t = self.current
        self.current = next(self._iter, None)
        return t

    def _hasnewterm(self):
        """True if next token may start new term"""
        return any(self._elements[self.current[0]][1:3])

    def _match(self, m):
        """make sure the tokenizer matches an end condition"""
        if self.current[0] != m:
            raise error.ParseError(
                _(b"unexpected token: %s") % self.current[0], self.current[2]
            )
        self._advance()

    def _parseoperand(self, bind, m=None):
        """gather right-hand-side operand until an end condition or binding
        met"""
        if m and self.current[0] == m:
            expr = None
        else:
            expr = self._parse(bind)
        if m:
            self._match(m)
        return expr

    def _parse(self, bind=0):
        token, value, pos = self._advance()
        # handle prefix rules on current token, take as primary if unambiguous
        primary, prefix = self._elements[token][1:3]
        if primary and not (prefix and self._hasnewterm()):
            expr = (primary, value)
        elif prefix:
            expr = (prefix[0], self._parseoperand(*prefix[1:]))
        else:
            raise error.ParseError(_(b"not a prefix: %s") % token, pos)
        # gather tokens until we meet a lower binding strength
        while bind < self._elements[self.current[0]][0]:
            token, value, pos = self._advance()
            # handle infix rules, take as suffix if unambiguous
            infix, suffix = self._elements[token][3:]
            if suffix and not (infix and self._hasnewterm()):
                expr = (suffix, expr)
            elif infix:
                expr = (infix[0], expr, self._parseoperand(*infix[1:]))
            else:
                raise error.ParseError(_(b"not an infix: %s") % token, pos)
        return expr

    def parse(self, tokeniter):
        """generate a parse tree from tokens"""
        self._iter = tokeniter
        self._advance()
        res = self._parse()
        token, value, pos = self.current
        return res, pos

    def eval(self, tree):
        """recursively evaluate a parse tree using node methods"""
        if not isinstance(tree, tuple):
            return tree
        return self._methods[tree[0]](*[self.eval(t) for t in tree[1:]])

    def __call__(self, tokeniter):
        """parse tokens into a parse tree and evaluate if methods given"""
        t = self.parse(tokeniter)
        if self._methods:
            return self.eval(t)
        return t


def splitargspec(spec):
    """Parse spec of function arguments into (poskeys, varkey, keys, optkey)

    >>> splitargspec(b'')
    ([], None, [], None)
    >>> splitargspec(b'foo bar')
    ([], None, ['foo', 'bar'], None)
    >>> splitargspec(b'foo *bar baz **qux')
    (['foo'], 'bar', ['baz'], 'qux')
    >>> splitargspec(b'*foo')
    ([], 'foo', [], None)
    >>> splitargspec(b'**foo')
    ([], None, [], 'foo')
    """
    optkey = None
    pre, sep, post = spec.partition(b'**')
    if sep:
        posts = post.split()
        if not posts:
            raise error.ProgrammingError(b'no **optkey name provided')
        if len(posts) > 1:
            raise error.ProgrammingError(b'excessive **optkey names provided')
        optkey = posts[0]

    pre, sep, post = pre.partition(b'*')
    pres = pre.split()
    posts = post.split()
    if sep:
        if not posts:
            raise error.ProgrammingError(b'no *varkey name provided')
        return pres, posts[0], posts[1:], optkey
    return [], None, pres, optkey


def buildargsdict(trees, funcname, argspec, keyvaluenode, keynode):
    """Build dict from list containing positional and keyword arguments

    Arguments are specified by a tuple of ``(poskeys, varkey, keys, optkey)``
    where

    - ``poskeys``: list of names of positional arguments
    - ``varkey``: optional argument name that takes up remainder
    - ``keys``: list of names that can be either positional or keyword arguments
    - ``optkey``: optional argument name that takes up excess keyword arguments

    If ``varkey`` specified, all ``keys`` must be given as keyword arguments.

    Invalid keywords, too few positional arguments, or too many positional
    arguments are rejected, but missing keyword arguments are just omitted.
    """
    poskeys, varkey, keys, optkey = argspec
    kwstart = next(
        (i for i, x in enumerate(trees) if x and x[0] == keyvaluenode),
        len(trees),
    )
    if kwstart < len(poskeys):
        raise error.ParseError(
            _(b"%(func)s takes at least %(nargs)d positional arguments")
            % {b'func': funcname, b'nargs': len(poskeys)}
        )
    if not varkey and kwstart > len(poskeys) + len(keys):
        raise error.ParseError(
            _(b"%(func)s takes at most %(nargs)d positional arguments")
            % {b'func': funcname, b'nargs': len(poskeys) + len(keys)}
        )
    args = util.sortdict()
    # consume positional arguments
    for k, x in zip(poskeys, trees[:kwstart]):
        args[k] = x
    if varkey:
        args[varkey] = trees[len(args) : kwstart]
    else:
        for k, x in zip(keys, trees[len(args) : kwstart]):
            args[k] = x
    # remainder should be keyword arguments
    if optkey:
        args[optkey] = util.sortdict()
    for x in trees[kwstart:]:
        if not x or x[0] != keyvaluenode or x[1][0] != keynode:
            raise error.ParseError(
                _(b"%(func)s got an invalid argument") % {b'func': funcname}
            )
        k = x[1][1]
        if k in keys:
            d = args
        elif not optkey:
            raise error.ParseError(
                _(b"%(func)s got an unexpected keyword argument '%(key)s'")
                % {b'func': funcname, b'key': k}
            )
        else:
            d = args[optkey]
        if k in d:
            raise error.ParseError(
                _(
                    b"%(func)s got multiple values for keyword "
                    b"argument '%(key)s'"
                )
                % {b'func': funcname, b'key': k}
            )
        d[k] = x[2]
    return args


def unescapestr(s):
    try:
        return stringutil.unescapestr(s)
    except ValueError as e:
        # mangle Python's exception into our format
        # TODO: remove this suppression.  For some reason, pytype 2021.09.09
        #   thinks .lower() is being called on Union[ValueError, bytes].
        # pytype: disable=attribute-error
        raise error.ParseError(stringutil.forcebytestr(e).lower())
        # pytype: enable=attribute-error


def _prettyformat(tree, leafnodes, level, lines):
    if not isinstance(tree, tuple):
        lines.append((level, stringutil.pprint(tree)))
    elif tree[0] in leafnodes:
        rs = map(stringutil.pprint, tree[1:])
        lines.append((level, b'(%s %s)' % (tree[0], b' '.join(rs))))
    else:
        lines.append((level, b'(%s' % tree[0]))
        for s in tree[1:]:
            _prettyformat(s, leafnodes, level + 1, lines)
        lines[-1:] = [(lines[-1][0], lines[-1][1] + b')')]


def prettyformat(tree, leafnodes):
    lines = []
    _prettyformat(tree, leafnodes, 0, lines)
    output = b'\n'.join((b'  ' * l + s) for l, s in lines)
    return output


def simplifyinfixops(tree, targetnodes):
    """Flatten chained infix operations to reduce usage of Python stack

    >>> from . import pycompat
    >>> def f(tree):
    ...     s = prettyformat(simplifyinfixops(tree, (b'or',)), (b'symbol',))
    ...     print(pycompat.sysstr(s))
    >>> f((b'or',
    ...     (b'or',
    ...       (b'symbol', b'1'),
    ...       (b'symbol', b'2')),
    ...     (b'symbol', b'3')))
    (or
      (symbol '1')
      (symbol '2')
      (symbol '3'))
    >>> f((b'func',
    ...     (b'symbol', b'p1'),
    ...     (b'or',
    ...       (b'or',
    ...         (b'func',
    ...           (b'symbol', b'sort'),
    ...           (b'list',
    ...             (b'or',
    ...               (b'or',
    ...                 (b'symbol', b'1'),
    ...                 (b'symbol', b'2')),
    ...               (b'symbol', b'3')),
    ...             (b'negate',
    ...               (b'symbol', b'rev')))),
    ...         (b'and',
    ...           (b'symbol', b'4'),
    ...           (b'group',
    ...             (b'or',
    ...               (b'or',
    ...                 (b'symbol', b'5'),
    ...                 (b'symbol', b'6')),
    ...               (b'symbol', b'7'))))),
    ...       (b'symbol', b'8'))))
    (func
      (symbol 'p1')
      (or
        (func
          (symbol 'sort')
          (list
            (or
              (symbol '1')
              (symbol '2')
              (symbol '3'))
            (negate
              (symbol 'rev'))))
        (and
          (symbol '4')
          (group
            (or
              (symbol '5')
              (symbol '6')
              (symbol '7'))))
        (symbol '8')))
    """
    if not isinstance(tree, tuple):
        return tree
    op = tree[0]
    if op not in targetnodes:
        return (op,) + tuple(simplifyinfixops(x, targetnodes) for x in tree[1:])

    # walk down left nodes taking each right node. no recursion to left nodes
    # because infix operators are left-associative, i.e. left tree is deep.
    # e.g. '1 + 2 + 3' -> (+ (+ 1 2) 3) -> (+ 1 2 3)
    simplified = []
    x = tree
    while x[0] == op:
        l, r = x[1:]
        simplified.append(simplifyinfixops(r, targetnodes))
        x = l
    simplified.append(simplifyinfixops(x, targetnodes))
    simplified.append(op)
    return tuple(reversed(simplified))


def _buildtree(template, placeholder, replstack):
    if template == placeholder:
        return replstack.pop()
    if not isinstance(template, tuple):
        return template
    return tuple(_buildtree(x, placeholder, replstack) for x in template)


def buildtree(template, placeholder, *repls):
    """Create new tree by substituting placeholders by replacements

    >>> _ = (b'symbol', b'_')
    >>> def f(template, *repls):
    ...     return buildtree(template, _, *repls)
    >>> f((b'func', (b'symbol', b'only'), (b'list', _, _)),
    ...   ('symbol', '1'), ('symbol', '2'))
    ('func', ('symbol', 'only'), ('list', ('symbol', '1'), ('symbol', '2')))
    >>> f((b'and', _, (b'not', _)), (b'symbol', b'1'), (b'symbol', b'2'))
    ('and', ('symbol', '1'), ('not', ('symbol', '2')))
    """
    if not isinstance(placeholder, tuple):
        raise error.ProgrammingError(b'placeholder must be a node tuple')
    replstack = list(reversed(repls))
    r = _buildtree(template, placeholder, replstack)
    if replstack:
        raise error.ProgrammingError(b'too many replacements')
    return r


def _matchtree(pattern, tree, placeholder, incompletenodes, matches):
    if pattern == tree:
        return True
    if not isinstance(pattern, tuple) or not isinstance(tree, tuple):
        return False
    if pattern == placeholder and tree[0] not in incompletenodes:
        matches.append(tree)
        return True
    if len(pattern) != len(tree):
        return False
    return all(
        _matchtree(p, x, placeholder, incompletenodes, matches)
        for p, x in zip(pattern, tree)
    )


def matchtree(pattern, tree, placeholder=None, incompletenodes=()):
    """If a tree matches the pattern, return a list of the tree and nodes
    matched with the placeholder; Otherwise None

    >>> def f(pattern, tree):
    ...     m = matchtree(pattern, tree, _, {b'keyvalue', b'list'})
    ...     if m:
    ...         return m[1:]

    >>> _ = (b'symbol', b'_')
    >>> f((b'func', (b'symbol', b'ancestors'), _),
    ...   (b'func', (b'symbol', b'ancestors'), (b'symbol', b'1')))
    [('symbol', '1')]
    >>> f((b'func', (b'symbol', b'ancestors'), _),
    ...   (b'func', (b'symbol', b'ancestors'), None))
    >>> f((b'range', (b'dagrange', _, _), _),
    ...   (b'range',
    ...     (b'dagrange', (b'symbol', b'1'), (b'symbol', b'2')),
    ...     (b'symbol', b'3')))
    [('symbol', '1'), ('symbol', '2'), ('symbol', '3')]

    The placeholder does not match the specified incomplete nodes because
    an incomplete node (e.g. argument list) cannot construct an expression.

    >>> f((b'func', (b'symbol', b'ancestors'), _),
    ...   (b'func', (b'symbol', b'ancestors'),
    ...     (b'list', (b'symbol', b'1'), (b'symbol', b'2'))))

    The placeholder may be omitted, but which shouldn't match a None node.

    >>> _ = None
    >>> f((b'func', (b'symbol', b'ancestors'), None),
    ...   (b'func', (b'symbol', b'ancestors'), (b'symbol', b'0')))
    """
    if placeholder is not None and not isinstance(placeholder, tuple):
        raise error.ProgrammingError(b'placeholder must be a node tuple')
    matches = [tree]
    if _matchtree(pattern, tree, placeholder, incompletenodes, matches):
        return matches


def parseerrordetail(inst):
    """Compose error message from specified ParseError object"""
    if inst.location is not None:
        return _(b'at %d: %s') % (inst.location, inst.message)
    else:
        return inst.message


class alias:
    """Parsed result of alias"""

    def __init__(self, name, args, err, replacement):
        self.name = name
        self.args = args
        self.error = err
        self.replacement = replacement
        # whether own `error` information is already shown or not.
        # this avoids showing same warning multiple times at each
        # `expandaliases`.
        self.warned = False


class basealiasrules:
    """Parsing and expansion rule set of aliases

    This is a helper for fileset/revset/template aliases. A concrete rule set
    should be made by sub-classing this and implementing class/static methods.

    It supports alias expansion of symbol and function-call styles::

        # decl = defn
        h = heads(default)
        b($1) = ancestors($1) - ancestors(default)
    """

    # typically a config section, which will be included in error messages
    _section = None
    # tag of symbol node
    _symbolnode = b'symbol'

    def __new__(cls):
        raise TypeError(b"'%s' is not instantiatable" % cls.__name__)

    @staticmethod
    def _parse(spec):
        """Parse an alias name, arguments and definition"""
        raise NotImplementedError

    @staticmethod
    def _trygetfunc(tree):
        """Return (name, args) if tree is a function; otherwise None"""
        raise NotImplementedError

    @classmethod
    def _builddecl(cls, decl):
        """Parse an alias declaration into ``(name, args, errorstr)``

        This function analyzes the parsed tree. The parsing rule is provided
        by ``_parse()``.

        - ``name``: of declared alias (may be ``decl`` itself at error)
        - ``args``: list of argument names (or None for symbol declaration)
        - ``errorstr``: detail about detected error (or None)

        >>> sym = lambda x: (b'symbol', x)
        >>> symlist = lambda *xs: (b'list',) + tuple(sym(x) for x in xs)
        >>> func = lambda n, a: (b'func', sym(n), a)
        >>> parsemap = {
        ...     b'foo': sym(b'foo'),
        ...     b'$foo': sym(b'$foo'),
        ...     b'foo::bar': (b'dagrange', sym(b'foo'), sym(b'bar')),
        ...     b'foo()': func(b'foo', None),
        ...     b'$foo()': func(b'$foo', None),
        ...     b'foo($1, $2)': func(b'foo', symlist(b'$1', b'$2')),
        ...     b'foo(bar_bar, baz.baz)':
        ...         func(b'foo', symlist(b'bar_bar', b'baz.baz')),
        ...     b'foo(bar($1, $2))':
        ...         func(b'foo', func(b'bar', symlist(b'$1', b'$2'))),
        ...     b'foo($1, $2, nested($1, $2))':
        ...         func(b'foo', (symlist(b'$1', b'$2') +
        ...                      (func(b'nested', symlist(b'$1', b'$2')),))),
        ...     b'foo("bar")': func(b'foo', (b'string', b'bar')),
        ...     b'foo($1, $2': error.ParseError(b'unexpected token: end', 10),
        ...     b'foo("bar': error.ParseError(b'unterminated string', 5),
        ...     b'foo($1, $2, $1)': func(b'foo', symlist(b'$1', b'$2', b'$1')),
        ... }
        >>> def parse(expr):
        ...     x = parsemap[expr]
        ...     if isinstance(x, Exception):
        ...         raise x
        ...     return x
        >>> def trygetfunc(tree):
        ...     if not tree or tree[0] != b'func' or tree[1][0] != b'symbol':
        ...         return None
        ...     if not tree[2]:
        ...         return tree[1][1], []
        ...     if tree[2][0] == b'list':
        ...         return tree[1][1], list(tree[2][1:])
        ...     return tree[1][1], [tree[2]]
        >>> class aliasrules(basealiasrules):
        ...     _parse = staticmethod(parse)
        ...     _trygetfunc = staticmethod(trygetfunc)
        >>> builddecl = aliasrules._builddecl
        >>> builddecl(b'foo')
        ('foo', None, None)
        >>> builddecl(b'$foo')
        ('$foo', None, "invalid symbol '$foo'")
        >>> builddecl(b'foo::bar')
        ('foo::bar', None, 'invalid format')
        >>> builddecl(b'foo()')
        ('foo', [], None)
        >>> builddecl(b'$foo()')
        ('$foo()', None, "invalid function '$foo'")
        >>> builddecl(b'foo($1, $2)')
        ('foo', ['$1', '$2'], None)
        >>> builddecl(b'foo(bar_bar, baz.baz)')
        ('foo', ['bar_bar', 'baz.baz'], None)
        >>> builddecl(b'foo($1, $2, nested($1, $2))')
        ('foo($1, $2, nested($1, $2))', None, 'invalid argument list')
        >>> builddecl(b'foo(bar($1, $2))')
        ('foo(bar($1, $2))', None, 'invalid argument list')
        >>> builddecl(b'foo("bar")')
        ('foo("bar")', None, 'invalid argument list')
        >>> builddecl(b'foo($1, $2')
        ('foo($1, $2', None, 'at 10: unexpected token: end')
        >>> builddecl(b'foo("bar')
        ('foo("bar', None, 'at 5: unterminated string')
        >>> builddecl(b'foo($1, $2, $1)')
        ('foo', None, 'argument names collide with each other')
        """
        try:
            tree = cls._parse(decl)
        except error.ParseError as inst:
            return (decl, None, parseerrordetail(inst))

        if tree[0] == cls._symbolnode:
            # "name = ...." style
            name = tree[1]
            if name.startswith(b'$'):
                return (decl, None, _(b"invalid symbol '%s'") % name)
            return (name, None, None)

        func = cls._trygetfunc(tree)
        if func:
            # "name(arg, ....) = ...." style
            name, args = func
            if name.startswith(b'$'):
                return (decl, None, _(b"invalid function '%s'") % name)
            if any(t[0] != cls._symbolnode for t in args):
                return (decl, None, _(b"invalid argument list"))
            if len(args) != len(set(args)):
                return (
                    name,
                    None,
                    _(b"argument names collide with each other"),
                )
            return (name, [t[1] for t in args], None)

        return (decl, None, _(b"invalid format"))

    @classmethod
    def _relabelargs(cls, tree, args):
        """Mark alias arguments as ``_aliasarg``"""
        if not isinstance(tree, tuple):
            return tree
        op = tree[0]
        if op != cls._symbolnode:
            return (op,) + tuple(cls._relabelargs(x, args) for x in tree[1:])

        assert len(tree) == 2
        sym = tree[1]
        if sym in args:
            op = b'_aliasarg'
        elif sym.startswith(b'$'):
            raise error.ParseError(_(b"invalid symbol '%s'") % sym)
        return (op, sym)

    @classmethod
    def _builddefn(cls, defn, args):
        """Parse an alias definition into a tree and marks substitutions

        This function marks alias argument references as ``_aliasarg``. The
        parsing rule is provided by ``_parse()``.

        ``args`` is a list of alias argument names, or None if the alias
        is declared as a symbol.

        >>> from . import pycompat
        >>> parsemap = {
        ...     b'$1 or foo': (b'or', (b'symbol', b'$1'), (b'symbol', b'foo')),
        ...     b'$1 or $bar':
        ...         (b'or', (b'symbol', b'$1'), (b'symbol', b'$bar')),
        ...     b'$10 or baz':
        ...         (b'or', (b'symbol', b'$10'), (b'symbol', b'baz')),
        ...     b'"$1" or "foo"':
        ...         (b'or', (b'string', b'$1'), (b'string', b'foo')),
        ... }
        >>> class aliasrules(basealiasrules):
        ...     _parse = staticmethod(parsemap.__getitem__)
        ...     _trygetfunc = staticmethod(lambda x: None)
        >>> builddefn = aliasrules._builddefn
        >>> def pprint(tree):
        ...     s = prettyformat(tree, (b'_aliasarg', b'string', b'symbol'))
        ...     print(pycompat.sysstr(s))
        >>> args = [b'$1', b'$2', b'foo']
        >>> pprint(builddefn(b'$1 or foo', args))
        (or
          (_aliasarg '$1')
          (_aliasarg 'foo'))
        >>> try:
        ...     builddefn(b'$1 or $bar', args)
        ... except error.ParseError as inst:
        ...     print(pycompat.sysstr(parseerrordetail(inst)))
        invalid symbol '$bar'
        >>> args = [b'$1', b'$10', b'foo']
        >>> pprint(builddefn(b'$10 or baz', args))
        (or
          (_aliasarg '$10')
          (symbol 'baz'))
        >>> pprint(builddefn(b'"$1" or "foo"', args))
        (or
          (string '$1')
          (string 'foo'))
        """
        tree = cls._parse(defn)
        if args:
            args = set(args)
        else:
            args = set()
        return cls._relabelargs(tree, args)

    @classmethod
    def build(cls, decl, defn):
        """Parse an alias declaration and definition into an alias object"""
        repl = efmt = None
        name, args, err = cls._builddecl(decl)
        if err:
            efmt = _(b'bad declaration of %(section)s "%(name)s": %(error)s')
        else:
            try:
                repl = cls._builddefn(defn, args)
            except error.ParseError as inst:
                err = parseerrordetail(inst)
                efmt = _(b'bad definition of %(section)s "%(name)s": %(error)s')
        if err:
            err = efmt % {
                b'section': cls._section,
                b'name': name,
                b'error': err,
            }
        return alias(name, args, err, repl)

    @classmethod
    def buildmap(cls, items):
        """Parse a list of alias (name, replacement) pairs into a dict of
        alias objects"""
        aliases = {}
        for decl, defn in items:
            a = cls.build(decl, defn)
            aliases[a.name] = a
        return aliases

    @classmethod
    def _getalias(cls, aliases, tree):
        """If tree looks like an unexpanded alias, return (alias, pattern-args)
        pair. Return None otherwise.
        """
        if not isinstance(tree, tuple):
            return None
        if tree[0] == cls._symbolnode:
            name = tree[1]
            a = aliases.get(name)
            if a and a.args is None:
                return a, None
        func = cls._trygetfunc(tree)
        if func:
            name, args = func
            a = aliases.get(name)
            if a and a.args is not None:
                return a, args
        return None

    @classmethod
    def _expandargs(cls, tree, args):
        """Replace _aliasarg instances with the substitution value of the
        same name in args, recursively.
        """
        if not isinstance(tree, tuple):
            return tree
        if tree[0] == b'_aliasarg':
            sym = tree[1]
            return args[sym]
        return tuple(cls._expandargs(t, args) for t in tree)

    @classmethod
    def _expand(cls, aliases, tree, expanding, cache):
        if not isinstance(tree, tuple):
            return tree
        r = cls._getalias(aliases, tree)
        if r is None:
            return tuple(
                cls._expand(aliases, t, expanding, cache) for t in tree
            )
        a, l = r
        if a.error:
            raise error.Abort(a.error)
        if a in expanding:
            raise error.ParseError(
                _(b'infinite expansion of %(section)s "%(name)s" detected')
                % {b'section': cls._section, b'name': a.name}
            )
        # get cacheable replacement tree by expanding aliases recursively
        expanding.append(a)
        if a.name not in cache:
            cache[a.name] = cls._expand(
                aliases, a.replacement, expanding, cache
            )
        result = cache[a.name]
        expanding.pop()
        if a.args is None:
            return result
        # substitute function arguments in replacement tree
        if len(l) != len(a.args):
            raise error.ParseError(
                _(b'invalid number of arguments: %d') % len(l)
            )
        l = [cls._expand(aliases, t, [], cache) for t in l]
        return cls._expandargs(result, dict(zip(a.args, l)))

    @classmethod
    def expand(cls, aliases, tree):
        """Expand aliases in tree, recursively.

        'aliases' is a dictionary mapping user defined aliases to alias objects.
        """
        return cls._expand(aliases, tree, [], {})
