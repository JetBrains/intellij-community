# match.py - filename matching
#
#  Copyright 2008, 2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import re
import scmutil, util, fileset
from i18n import _

def _rematcher(pat):
    m = util.compilere(pat)
    try:
        # slightly faster, provided by facebook's re2 bindings
        return m.test_match
    except AttributeError:
        return m.match

def _expandsets(pats, ctx):
    '''convert set: patterns into a list of files in the given context'''
    fset = set()
    other = []

    for kind, expr in pats:
        if kind == 'set':
            if not ctx:
                raise util.Abort("fileset expression with no context")
            s = fileset.getfileset(ctx, expr)
            fset.update(s)
            continue
        other.append((kind, expr))
    return fset, other

class match(object):
    def __init__(self, root, cwd, patterns, include=[], exclude=[],
                 default='glob', exact=False, auditor=None, ctx=None):
        """build an object to match a set of file patterns

        arguments:
        root - the canonical root of the tree you're matching against
        cwd - the current working directory, if relevant
        patterns - patterns to find
        include - patterns to include
        exclude - patterns to exclude
        default - if a pattern in names has no explicit type, assume this one
        exact - patterns are actually literals

        a pattern is one of:
        'glob:<glob>' - a glob relative to cwd
        're:<regexp>' - a regular expression
        'path:<path>' - a path relative to repository root
        'relglob:<glob>' - an unrooted glob (*.c matches C files in all dirs)
        'relpath:<path>' - a path relative to cwd
        'relre:<regexp>' - a regexp that needn't match the start of a name
        'set:<fileset>' - a fileset expression
        '<something>' - a pattern of the specified default type
        """

        self._root = root
        self._cwd = cwd
        self._files = []
        self._anypats = bool(include or exclude)
        self._ctx = ctx
        self._always = False

        if include:
            pats = _normalize(include, 'glob', root, cwd, auditor)
            self.includepat, im = _buildmatch(ctx, pats, '(?:/|$)')
        if exclude:
            pats = _normalize(exclude, 'glob', root, cwd, auditor)
            self.excludepat, em = _buildmatch(ctx, pats, '(?:/|$)')
        if exact:
            if isinstance(patterns, list):
                self._files = patterns
            else:
                self._files = list(patterns)
            pm = self.exact
        elif patterns:
            pats = _normalize(patterns, default, root, cwd, auditor)
            self._files = _roots(pats)
            self._anypats = self._anypats or _anypats(pats)
            self.patternspat, pm = _buildmatch(ctx, pats, '$')

        if patterns or exact:
            if include:
                if exclude:
                    m = lambda f: im(f) and not em(f) and pm(f)
                else:
                    m = lambda f: im(f) and pm(f)
            else:
                if exclude:
                    m = lambda f: not em(f) and pm(f)
                else:
                    m = pm
        else:
            if include:
                if exclude:
                    m = lambda f: im(f) and not em(f)
                else:
                    m = im
            else:
                if exclude:
                    m = lambda f: not em(f)
                else:
                    m = lambda f: True
                    self._always = True

        self.matchfn = m
        self._fmap = set(self._files)

    def __call__(self, fn):
        return self.matchfn(fn)
    def __iter__(self):
        for f in self._files:
            yield f
    def bad(self, f, msg):
        '''callback for each explicit file that can't be
        found/accessed, with an error message
        '''
        pass
    def dir(self, f):
        pass
    def missing(self, f):
        pass
    def exact(self, f):
        return f in self._fmap
    def rel(self, f):
        return util.pathto(self._root, self._cwd, f)
    def files(self):
        return self._files
    def anypats(self):
        return self._anypats
    def always(self):
        return self._always

class exact(match):
    def __init__(self, root, cwd, files):
        match.__init__(self, root, cwd, files, exact = True)

class always(match):
    def __init__(self, root, cwd):
        match.__init__(self, root, cwd, [])
        self._always = True

class narrowmatcher(match):
    """Adapt a matcher to work on a subdirectory only.

    The paths are remapped to remove/insert the path as needed:

    >>> m1 = match('root', '', ['a.txt', 'sub/b.txt'])
    >>> m2 = narrowmatcher('sub', m1)
    >>> bool(m2('a.txt'))
    False
    >>> bool(m2('b.txt'))
    True
    >>> bool(m2.matchfn('a.txt'))
    False
    >>> bool(m2.matchfn('b.txt'))
    True
    >>> m2.files()
    ['b.txt']
    >>> m2.exact('b.txt')
    True
    >>> m2.rel('b.txt')
    'b.txt'
    >>> def bad(f, msg):
    ...     print "%s: %s" % (f, msg)
    >>> m1.bad = bad
    >>> m2.bad('x.txt', 'No such file')
    sub/x.txt: No such file
    """

    def __init__(self, path, matcher):
        self._root = matcher._root
        self._cwd = matcher._cwd
        self._path = path
        self._matcher = matcher
        self._always = matcher._always

        self._files = [f[len(path) + 1:] for f in matcher._files
                       if f.startswith(path + "/")]
        self._anypats = matcher._anypats
        self.matchfn = lambda fn: matcher.matchfn(self._path + "/" + fn)
        self._fmap = set(self._files)

    def bad(self, f, msg):
        self._matcher.bad(self._path + "/" + f, msg)

def patkind(pat):
    return _patsplit(pat, None)[0]

def _patsplit(pat, default):
    """Split a string into an optional pattern kind prefix and the
    actual pattern."""
    if ':' in pat:
        kind, val = pat.split(':', 1)
        if kind in ('re', 'glob', 'path', 'relglob', 'relpath', 'relre',
                    'listfile', 'listfile0', 'set'):
            return kind, val
    return default, pat

def _globre(pat):
    "convert a glob pattern into a regexp"
    i, n = 0, len(pat)
    res = ''
    group = 0
    escape = re.escape
    def peek():
        return i < n and pat[i]
    while i < n:
        c = pat[i]
        i += 1
        if c not in '*?[{},\\':
            res += escape(c)
        elif c == '*':
            if peek() == '*':
                i += 1
                res += '.*'
            else:
                res += '[^/]*'
        elif c == '?':
            res += '.'
        elif c == '[':
            j = i
            if j < n and pat[j] in '!]':
                j += 1
            while j < n and pat[j] != ']':
                j += 1
            if j >= n:
                res += '\\['
            else:
                stuff = pat[i:j].replace('\\','\\\\')
                i = j + 1
                if stuff[0] == '!':
                    stuff = '^' + stuff[1:]
                elif stuff[0] == '^':
                    stuff = '\\' + stuff
                res = '%s[%s]' % (res, stuff)
        elif c == '{':
            group += 1
            res += '(?:'
        elif c == '}' and group:
            res += ')'
            group -= 1
        elif c == ',' and group:
            res += '|'
        elif c == '\\':
            p = peek()
            if p:
                i += 1
                res += escape(p)
            else:
                res += escape(c)
        else:
            res += escape(c)
    return res

def _regex(kind, name, tail):
    '''convert a pattern into a regular expression'''
    if not name:
        return ''
    if kind == 're':
        return name
    elif kind == 'path':
        return '^' + re.escape(name) + '(?:/|$)'
    elif kind == 'relglob':
        return '(?:|.*/)' + _globre(name) + tail
    elif kind == 'relpath':
        return re.escape(name) + '(?:/|$)'
    elif kind == 'relre':
        if name.startswith('^'):
            return name
        return '.*' + name
    return _globre(name) + tail

def _buildmatch(ctx, pats, tail):
    fset, pats = _expandsets(pats, ctx)
    if not pats:
        return "", fset.__contains__

    pat, mf = _buildregexmatch(pats, tail)
    if fset:
        return pat, lambda f: f in fset or mf(f)
    return pat, mf

def _buildregexmatch(pats, tail):
    """build a matching function from a set of patterns"""
    try:
        pat = '(?:%s)' % '|'.join([_regex(k, p, tail) for (k, p) in pats])
        if len(pat) > 20000:
            raise OverflowError
        return pat, _rematcher(pat)
    except OverflowError:
        # We're using a Python with a tiny regex engine and we
        # made it explode, so we'll divide the pattern list in two
        # until it works
        l = len(pats)
        if l < 2:
            raise
        pata, a = _buildregexmatch(pats[:l//2], tail)
        patb, b = _buildregexmatch(pats[l//2:], tail)
        return pat, lambda s: a(s) or b(s)
    except re.error:
        for k, p in pats:
            try:
                _rematcher('(?:%s)' % _regex(k, p, tail))
            except re.error:
                raise util.Abort(_("invalid pattern (%s): %s") % (k, p))
        raise util.Abort(_("invalid pattern"))

def _normalize(names, default, root, cwd, auditor):
    pats = []
    for kind, name in [_patsplit(p, default) for p in names]:
        if kind in ('glob', 'relpath'):
            name = scmutil.canonpath(root, cwd, name, auditor)
        elif kind in ('relglob', 'path'):
            name = util.normpath(name)
        elif kind in ('listfile', 'listfile0'):
            try:
                files = util.readfile(name)
                if kind == 'listfile0':
                    files = files.split('\0')
                else:
                    files = files.splitlines()
                files = [f for f in files if f]
            except EnvironmentError:
                raise util.Abort(_("unable to read file list (%s)") % name)
            pats += _normalize(files, default, root, cwd, auditor)
            continue

        pats.append((kind, name))
    return pats

def _roots(patterns):
    r = []
    for kind, name in patterns:
        if kind == 'glob': # find the non-glob prefix
            root = []
            for p in name.split('/'):
                if '[' in p or '{' in p or '*' in p or '?' in p:
                    break
                root.append(p)
            r.append('/'.join(root) or '.')
        elif kind in ('relpath', 'path'):
            r.append(name or '.')
        else: # relglob, re, relre
            r.append('.')
    return r

def _anypats(patterns):
    for kind, name in patterns:
        if kind in ('glob', 're', 'relglob', 'relre', 'set'):
            return True
