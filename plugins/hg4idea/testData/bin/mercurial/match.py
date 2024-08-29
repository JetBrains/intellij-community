# match.py - filename matching
#
#  Copyright 2008, 2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import bisect
import copy
import itertools
import os
import re

from .i18n import _
from .pycompat import open
from . import (
    encoding,
    error,
    pathutil,
    policy,
    pycompat,
    util,
)
from .utils import stringutil

rustmod = policy.importrust('dirstate')

allpatternkinds = (
    b're',
    b'glob',
    b'path',
    b'filepath',
    b'relglob',
    b'relpath',
    b'relre',
    b'rootglob',
    b'listfile',
    b'listfile0',
    b'set',
    b'include',
    b'subinclude',
    b'rootfilesin',
)
cwdrelativepatternkinds = (b'relpath', b'glob')

propertycache = util.propertycache


def _rematcher(regex):
    """compile the regexp with the best available regexp engine and return a
    matcher function"""
    m = util.re.compile(regex)
    try:
        # slightly faster, provided by facebook's re2 bindings
        return m.test_match
    except AttributeError:
        return m.match


def _expandsets(cwd, kindpats, ctx=None, listsubrepos=False, badfn=None):
    '''Returns the kindpats list with the 'set' patterns expanded to matchers'''
    matchers = []
    other = []

    for kind, pat, source in kindpats:
        if kind == b'set':
            if ctx is None:
                raise error.ProgrammingError(
                    b"fileset expression with no context"
                )
            matchers.append(ctx.matchfileset(cwd, pat, badfn=badfn))

            if listsubrepos:
                for subpath in ctx.substate:
                    sm = ctx.sub(subpath).matchfileset(cwd, pat, badfn=badfn)
                    pm = prefixdirmatcher(subpath, sm, badfn=badfn)
                    matchers.append(pm)

            continue
        other.append((kind, pat, source))
    return matchers, other


def _expandsubinclude(kindpats, root):
    """Returns the list of subinclude matcher args and the kindpats without the
    subincludes in it."""
    relmatchers = []
    other = []

    for kind, pat, source in kindpats:
        if kind == b'subinclude':
            sourceroot = pathutil.dirname(util.normpath(source))
            pat = util.pconvert(pat)
            path = pathutil.join(sourceroot, pat)

            newroot = pathutil.dirname(path)
            matcherargs = (newroot, b'', [], [b'include:%s' % path])

            prefix = pathutil.canonpath(root, root, newroot)
            if prefix:
                prefix += b'/'
            relmatchers.append((prefix, matcherargs))
        else:
            other.append((kind, pat, source))

    return relmatchers, other


def _kindpatsalwaysmatch(kindpats):
    """Checks whether the kindspats match everything, as e.g.
    'relpath:.' does.
    """
    for kind, pat, source in kindpats:
        if pat != b'' or kind not in [b'relpath', b'glob']:
            return False
    return True


def _buildkindpatsmatcher(
    matchercls,
    root,
    cwd,
    kindpats,
    ctx=None,
    listsubrepos=False,
    badfn=None,
):
    matchers = []
    fms, kindpats = _expandsets(
        cwd,
        kindpats,
        ctx=ctx,
        listsubrepos=listsubrepos,
        badfn=badfn,
    )
    if kindpats:
        m = matchercls(root, kindpats, badfn=badfn)
        matchers.append(m)
    if fms:
        matchers.extend(fms)
    if not matchers:
        return nevermatcher(badfn=badfn)
    if len(matchers) == 1:
        return matchers[0]
    return unionmatcher(matchers)


def match(
    root,
    cwd,
    patterns=None,
    include=None,
    exclude=None,
    default=b'glob',
    auditor=None,
    ctx=None,
    listsubrepos=False,
    warn=None,
    badfn=None,
    icasefs=False,
):
    r"""build an object to match a set of file patterns

    arguments:
    root - the canonical root of the tree you're matching against
    cwd - the current working directory, if relevant
    patterns - patterns to find
    include - patterns to include (unless they are excluded)
    exclude - patterns to exclude (even if they are included)
    default - if a pattern in patterns has no explicit type, assume this one
    auditor - optional path auditor
    ctx - optional changecontext
    listsubrepos - if True, recurse into subrepositories
    warn - optional function used for printing warnings
    badfn - optional bad() callback for this matcher instead of the default
    icasefs - make a matcher for wdir on case insensitive filesystems, which
        normalizes the given patterns to the case in the filesystem

    a pattern is one of:
    'glob:<glob>' - a glob relative to cwd
    're:<regexp>' - a regular expression
    'path:<path>' - a path relative to repository root, which is matched
                    recursively
    'filepath:<path>' - an exact path to a single file, relative to the
                        repository root
    'rootfilesin:<path>' - a path relative to repository root, which is
                    matched non-recursively (will not match subdirectories)
    'relglob:<glob>' - an unrooted glob (*.c matches C files in all dirs)
    'relpath:<path>' - a path relative to cwd
    'relre:<regexp>' - a regexp that needn't match the start of a name
    'set:<fileset>' - a fileset expression
    'include:<path>' - a file of patterns to read and include
    'subinclude:<path>' - a file of patterns to match against files under
                          the same directory
    '<something>' - a pattern of the specified default type

    >>> def _match(root, *args, **kwargs):
    ...     return match(util.localpath(root), *args, **kwargs)

    Usually a patternmatcher is returned:
    >>> _match(b'/foo', b'.', [br're:.*\.c$', b'path:foo/a', b'*.py'])
    <patternmatcher patterns='[^/]*\\.py$|foo/a(?:/|$)|.*\\.c$'>

    Combining 'patterns' with 'include' (resp. 'exclude') gives an
    intersectionmatcher (resp. a differencematcher):
    >>> type(_match(b'/foo', b'.', [br're:.*\.c$'], include=[b'path:lib']))
    <class 'mercurial.match.intersectionmatcher'>
    >>> type(_match(b'/foo', b'.', [br're:.*\.c$'], exclude=[b'path:build']))
    <class 'mercurial.match.differencematcher'>

    Notice that, if 'patterns' is empty, an alwaysmatcher is returned:
    >>> _match(b'/foo', b'.', [])
    <alwaysmatcher>

    The 'default' argument determines which kind of pattern is assumed if a
    pattern has no prefix:
    >>> _match(b'/foo', b'.', [br'.*\.c$'], default=b're')
    <patternmatcher patterns='.*\\.c$'>
    >>> _match(b'/foo', b'.', [b'main.py'], default=b'relpath')
    <patternmatcher patterns='main\\.py(?:/|$)'>
    >>> _match(b'/foo', b'.', [b'main.py'], default=b're')
    <patternmatcher patterns='main.py'>

    The primary use of matchers is to check whether a value (usually a file
    name) matches againset one of the patterns given at initialization. There
    are two ways of doing this check.

    >>> m = _match(b'/foo', b'', [br're:.*\.c$', b'relpath:a'])

    1. Calling the matcher with a file name returns True if any pattern
    matches that file name:
    >>> m(b'a')
    True
    >>> m(b'main.c')
    True
    >>> m(b'test.py')
    False

    2. Using the exact() method only returns True if the file name matches one
    of the exact patterns (i.e. not re: or glob: patterns):
    >>> m.exact(b'a')
    True
    >>> m.exact(b'main.c')
    False
    """
    assert os.path.isabs(root)
    cwd = os.path.join(root, util.localpath(cwd))
    normalize = _donormalize
    if icasefs:
        dirstate = ctx.repo().dirstate
        dsnormalize = dirstate.normalize

        def normalize(patterns, default, root, cwd, auditor, warn):
            kp = _donormalize(patterns, default, root, cwd, auditor, warn)
            kindpats = []
            for kind, pats, source in kp:
                if kind not in (b're', b'relre'):  # regex can't be normalized
                    p = pats
                    pats = dsnormalize(pats)

                    # Preserve the original to handle a case only rename.
                    if p != pats and p in dirstate:
                        kindpats.append((kind, p, source))

                kindpats.append((kind, pats, source))
            return kindpats

    if patterns:
        kindpats = normalize(patterns, default, root, cwd, auditor, warn)
        if _kindpatsalwaysmatch(kindpats):
            m = alwaysmatcher(badfn)
        else:
            m = _buildkindpatsmatcher(
                patternmatcher,
                root,
                cwd,
                kindpats,
                ctx=ctx,
                listsubrepos=listsubrepos,
                badfn=badfn,
            )
    else:
        # It's a little strange that no patterns means to match everything.
        # Consider changing this to match nothing (probably using nevermatcher).
        m = alwaysmatcher(badfn)

    if include:
        kindpats = normalize(include, b'glob', root, cwd, auditor, warn)
        im = _buildkindpatsmatcher(
            includematcher,
            root,
            cwd,
            kindpats,
            ctx=ctx,
            listsubrepos=listsubrepos,
            badfn=None,
        )
        m = intersectmatchers(m, im)
    if exclude:
        kindpats = normalize(exclude, b'glob', root, cwd, auditor, warn)
        em = _buildkindpatsmatcher(
            includematcher,
            root,
            cwd,
            kindpats,
            ctx=ctx,
            listsubrepos=listsubrepos,
            badfn=None,
        )
        m = differencematcher(m, em)
    return m


def exact(files, badfn=None):
    return exactmatcher(files, badfn=badfn)


def always(badfn=None):
    return alwaysmatcher(badfn)


def never(badfn=None):
    return nevermatcher(badfn)


def badmatch(match, badfn):
    """Make a copy of the given matcher, replacing its bad method with the given
    one.
    """
    m = copy.copy(match)
    m.bad = badfn
    return m


def _donormalize(patterns, default, root, cwd, auditor=None, warn=None):
    """Convert 'kind:pat' from the patterns list to tuples with kind and
    normalized and rooted patterns and with listfiles expanded."""
    kindpats = []
    kinds_to_normalize = (
        b'relglob',
        b'path',
        b'filepath',
        b'rootfilesin',
        b'rootglob',
    )

    for kind, pat in [_patsplit(p, default) for p in patterns]:
        if kind in cwdrelativepatternkinds:
            pat = pathutil.canonpath(root, cwd, pat, auditor=auditor)
        elif kind in kinds_to_normalize:
            pat = util.normpath(pat)
        elif kind in (b'listfile', b'listfile0'):
            try:
                files = util.readfile(pat)
                if kind == b'listfile0':
                    files = files.split(b'\0')
                else:
                    files = files.splitlines()
                files = [f for f in files if f]
            except EnvironmentError:
                raise error.Abort(_(b"unable to read file list (%s)") % pat)
            for k, p, source in _donormalize(
                files, default, root, cwd, auditor, warn
            ):
                kindpats.append((k, p, pat))
            continue
        elif kind == b'include':
            try:
                fullpath = os.path.join(root, util.localpath(pat))
                includepats = readpatternfile(fullpath, warn)
                for k, p, source in _donormalize(
                    includepats, default, root, cwd, auditor, warn
                ):
                    kindpats.append((k, p, source or pat))
            except error.Abort as inst:
                raise error.Abort(
                    b'%s: %s'
                    % (
                        pat,
                        inst.message,
                    )
                )
            except IOError as inst:
                if warn:
                    warn(
                        _(b"skipping unreadable pattern file '%s': %s\n")
                        % (pat, stringutil.forcebytestr(inst.strerror))
                    )
            continue
        # else: re or relre - which cannot be normalized
        kindpats.append((kind, pat, b''))
    return kindpats


class basematcher:
    def __init__(self, badfn=None):
        self._was_tampered_with = False
        if badfn is not None:
            self.bad = badfn

    def was_tampered_with_nonrec(self):
        # [_was_tampered_with] is used to track if when extensions changed the matcher
        # behavior (crazy stuff!), so we disable the rust fast path.
        return self._was_tampered_with

    def was_tampered_with(self):
        return self.was_tampered_with_nonrec()

    def __call__(self, fn):
        return self.matchfn(fn)

    # Callbacks related to how the matcher is used by dirstate.walk.
    # Subscribers to these events must monkeypatch the matcher object.
    def bad(self, f, msg):
        """Callback from dirstate.walk for each explicit file that can't be
        found/accessed, with an error message."""

    # If an traversedir is set, it will be called when a directory discovered
    # by recursive traversal is visited.
    traversedir = None

    @propertycache
    def _files(self):
        return []

    def files(self):
        """Explicitly listed files or patterns or roots:
        if no patterns or .always(): empty list,
        if exact: list exact files,
        if not .anypats(): list all files and dirs,
        else: optimal roots"""
        return self._files

    @propertycache
    def _fileset(self):
        return set(self._files)

    def exact(self, f):
        '''Returns True if f is in .files().'''
        return f in self._fileset

    def matchfn(self, f):
        return False

    def visitdir(self, dir):
        """Decides whether a directory should be visited based on whether it
        has potential matches in it or one of its subdirectories. This is
        based on the match's primary, included, and excluded patterns.

        Returns the string 'all' if the given directory and all subdirectories
        should be visited. Otherwise returns True or False indicating whether
        the given directory should be visited.
        """
        return True

    def visitchildrenset(self, dir):
        """Decides whether a directory should be visited based on whether it
        has potential matches in it or one of its subdirectories, and
        potentially lists which subdirectories of that directory should be
        visited. This is based on the match's primary, included, and excluded
        patterns.

        This function is very similar to 'visitdir', and the following mapping
        can be applied:

             visitdir | visitchildrenlist
            ----------+-------------------
             False    | set()
             'all'    | 'all'
             True     | 'this' OR non-empty set of subdirs -or files- to visit

        Example:
          Assume matchers ['path:foo/bar', 'rootfilesin:qux'], we would return
          the following values (assuming the implementation of visitchildrenset
          is capable of recognizing this; some implementations are not).

          '' -> {'foo', 'qux'}
          'baz' -> set()
          'foo' -> {'bar'}
          # Ideally this would be 'all', but since the prefix nature of matchers
          # is applied to the entire matcher, we have to downgrade this to
          # 'this' due to the non-prefix 'rootfilesin'-kind matcher being mixed
          # in.
          'foo/bar' -> 'this'
          'qux' -> 'this'

        Important:
          Most matchers do not know if they're representing files or
          directories. They see ['path:dir/f'] and don't know whether 'f' is a
          file or a directory, so visitchildrenset('dir') for most matchers will
          return {'f'}, but if the matcher knows it's a file (like exactmatcher
          does), it may return 'this'. Do not rely on the return being a set
          indicating that there are no files in this dir to investigate (or
          equivalently that if there are files to investigate in 'dir' that it
          will always return 'this').
        """
        return b'this'

    def always(self):
        """Matcher will match everything and .files() will be empty --
        optimization might be possible."""
        return False

    def isexact(self):
        """Matcher will match exactly the list of files in .files() --
        optimization might be possible."""
        return False

    def prefix(self):
        """Matcher will match the paths in .files() recursively --
        optimization might be possible."""
        return False

    def anypats(self):
        """None of .always(), .isexact(), and .prefix() is true --
        optimizations will be difficult."""
        return not self.always() and not self.isexact() and not self.prefix()


class alwaysmatcher(basematcher):
    '''Matches everything.'''

    def __init__(self, badfn=None):
        super(alwaysmatcher, self).__init__(badfn)

    def always(self):
        return True

    def matchfn(self, f):
        return True

    def visitdir(self, dir):
        return b'all'

    def visitchildrenset(self, dir):
        return b'all'

    def __repr__(self):
        return r'<alwaysmatcher>'


class nevermatcher(basematcher):
    '''Matches nothing.'''

    def __init__(self, badfn=None):
        super(nevermatcher, self).__init__(badfn)

    # It's a little weird to say that the nevermatcher is an exact matcher
    # or a prefix matcher, but it seems to make sense to let callers take
    # fast paths based on either. There will be no exact matches, nor any
    # prefixes (files() returns []), so fast paths iterating over them should
    # be efficient (and correct).
    def isexact(self):
        return True

    def prefix(self):
        return True

    def visitdir(self, dir):
        return False

    def visitchildrenset(self, dir):
        return set()

    def __repr__(self):
        return r'<nevermatcher>'


class predicatematcher(basematcher):
    """A matcher adapter for a simple boolean function"""

    def __init__(self, predfn, predrepr=None, badfn=None):
        super(predicatematcher, self).__init__(badfn)
        self.matchfn = predfn
        self._predrepr = predrepr

    @encoding.strmethod
    def __repr__(self):
        s = stringutil.buildrepr(self._predrepr) or pycompat.byterepr(
            self.matchfn
        )
        return b'<predicatenmatcher pred=%s>' % s


def path_or_parents_in_set(path, prefix_set):
    """Returns True if `path` (or any parent of `path`) is in `prefix_set`."""
    l = len(prefix_set)
    if l == 0:
        return False
    if path in prefix_set:
        return True
    # If there's more than 5 paths in prefix_set, it's *probably* quicker to
    # "walk up" the directory hierarchy instead, with the assumption that most
    # directory hierarchies are relatively shallow and hash lookup is cheap.
    if l > 5:
        return any(
            parentdir in prefix_set for parentdir in pathutil.finddirs(path)
        )

    # FIXME: Ideally we'd never get to this point if this is the case - we'd
    # recognize ourselves as an 'always' matcher and skip this.
    if b'' in prefix_set:
        return True

    sl = ord(b'/')

    # We already checked that path isn't in prefix_set exactly, so
    # `path[len(pf)] should never raise IndexError.
    return any(path.startswith(pf) and path[len(pf)] == sl for pf in prefix_set)


class patternmatcher(basematcher):
    r"""Matches a set of (kind, pat, source) against a 'root' directory.

    >>> kindpats = [
    ...     (b're', br'.*\.c$', b''),
    ...     (b'path', b'foo/a', b''),
    ...     (b'relpath', b'b', b''),
    ...     (b'glob', b'*.h', b''),
    ... ]
    >>> m = patternmatcher(b'foo', kindpats)
    >>> m(b'main.c')  # matches re:.*\.c$
    True
    >>> m(b'b.txt')
    False
    >>> m(b'foo/a')  # matches path:foo/a
    True
    >>> m(b'a')  # does not match path:b, since 'root' is 'foo'
    False
    >>> m(b'b')  # matches relpath:b, since 'root' is 'foo'
    True
    >>> m(b'lib.h')  # matches glob:*.h
    True

    >>> m.files()
    [b'', b'foo/a', b'', b'b']
    >>> m.exact(b'foo/a')
    True
    >>> m.exact(b'b')
    True
    >>> m.exact(b'lib.h')  # exact matches are for (rel)path kinds
    False
    """

    def __init__(self, root, kindpats, badfn=None):
        super(patternmatcher, self).__init__(badfn)
        kindpats.sort()

        if rustmod is not None:
            # We need to pass the patterns to Rust because they can contain
            # patterns from the user interface
            self._kindpats = kindpats

        roots, dirs, parents = _rootsdirsandparents(kindpats)
        self._files = _explicitfiles(kindpats)
        self._dirs_explicit = set(dirs)
        self._dirs = parents
        self._prefix = _prefix(kindpats)
        self._pats, self._matchfn = _buildmatch(kindpats, b'$', root)

    def matchfn(self, fn):
        if fn in self._fileset:
            return True
        return self._matchfn(fn)

    def visitdir(self, dir):
        if self._prefix and dir in self._fileset:
            return b'all'
        return (
            dir in self._dirs
            or path_or_parents_in_set(dir, self._fileset)
            or path_or_parents_in_set(dir, self._dirs_explicit)
        )

    def visitchildrenset(self, dir):
        ret = self.visitdir(dir)
        if ret is True:
            return b'this'
        elif not ret:
            return set()
        assert ret == b'all'
        return b'all'

    def prefix(self):
        return self._prefix

    @encoding.strmethod
    def __repr__(self):
        return b'<patternmatcher patterns=%r>' % pycompat.bytestr(self._pats)


# This is basically a reimplementation of pathutil.dirs that stores the
# children instead of just a count of them, plus a small optional optimization
# to avoid some directories we don't need.
class _dirchildren:
    def __init__(self, paths, onlyinclude=None):
        self._dirs = {}
        self._onlyinclude = onlyinclude or []
        addpath = self.addpath
        for f in paths:
            addpath(f)

    def addpath(self, path):
        if path == b'':
            return
        dirs = self._dirs
        findsplitdirs = _dirchildren._findsplitdirs
        for d, b in findsplitdirs(path):
            if d not in self._onlyinclude:
                continue
            dirs.setdefault(d, set()).add(b)

    @staticmethod
    def _findsplitdirs(path):
        # yields (dirname, basename) tuples, walking back to the root.  This is
        # very similar to pathutil.finddirs, except:
        #  - produces a (dirname, basename) tuple, not just 'dirname'
        # Unlike manifest._splittopdir, this does not suffix `dirname` with a
        # slash.
        oldpos = len(path)
        pos = path.rfind(b'/')
        while pos != -1:
            yield path[:pos], path[pos + 1 : oldpos]
            oldpos = pos
            pos = path.rfind(b'/', 0, pos)
        yield b'', path[:oldpos]

    def get(self, path):
        return self._dirs.get(path, set())


class includematcher(basematcher):
    def __init__(self, root, kindpats, badfn=None):
        super(includematcher, self).__init__(badfn)
        if rustmod is not None:
            # We need to pass the patterns to Rust because they can contain
            # patterns from the user interface
            self._kindpats = kindpats
        self._pats, self.matchfn = _buildmatch(kindpats, b'(?:/|$)', root)
        self._prefix = _prefix(kindpats)
        roots, dirs, parents = _rootsdirsandparents(kindpats)
        # roots are directories which are recursively included.
        self._roots = set(roots)
        # dirs are directories which are non-recursively included.
        self._dirs = set(dirs)
        # parents are directories which are non-recursively included because
        # they are needed to get to items in _dirs or _roots.
        self._parents = parents

    def visitdir(self, dir):
        if self._prefix and dir in self._roots:
            return b'all'
        return (
            dir in self._dirs
            or dir in self._parents
            or path_or_parents_in_set(dir, self._roots)
        )

    @propertycache
    def _allparentschildren(self):
        # It may seem odd that we add dirs, roots, and parents, and then
        # restrict to only parents. This is to catch the case of:
        #   dirs = ['foo/bar']
        #   parents = ['foo']
        # if we asked for the children of 'foo', but had only added
        # self._parents, we wouldn't be able to respond ['bar'].
        return _dirchildren(
            itertools.chain(self._dirs, self._roots, self._parents),
            onlyinclude=self._parents,
        )

    def visitchildrenset(self, dir):
        if self._prefix and dir in self._roots:
            return b'all'
        # Note: this does *not* include the 'dir in self._parents' case from
        # visitdir, that's handled below.
        if (
            b'' in self._roots
            or dir in self._dirs
            or path_or_parents_in_set(dir, self._roots)
        ):
            return b'this'

        if dir in self._parents:
            return self._allparentschildren.get(dir) or set()
        return set()

    @encoding.strmethod
    def __repr__(self):
        return b'<includematcher includes=%r>' % pycompat.bytestr(self._pats)


class exactmatcher(basematcher):
    r"""Matches the input files exactly. They are interpreted as paths, not
    patterns (so no kind-prefixes).

    >>> m = exactmatcher([b'a.txt', br're:.*\.c$'])
    >>> m(b'a.txt')
    True
    >>> m(b'b.txt')
    False

    Input files that would be matched are exactly those returned by .files()
    >>> m.files()
    ['a.txt', 're:.*\\.c$']

    So pattern 're:.*\.c$' is not considered as a regex, but as a file name
    >>> m(b'main.c')
    False
    >>> m(br're:.*\.c$')
    True
    """

    def __init__(self, files, badfn=None):
        super(exactmatcher, self).__init__(badfn)

        if isinstance(files, list):
            self._files = files
        else:
            self._files = list(files)

    matchfn = basematcher.exact

    @propertycache
    def _dirs(self):
        return set(pathutil.dirs(self._fileset))

    def visitdir(self, dir):
        return dir in self._dirs

    @propertycache
    def _visitchildrenset_candidates(self):
        """A memoized set of candidates for visitchildrenset."""
        return self._fileset | self._dirs - {b''}

    @propertycache
    def _sorted_visitchildrenset_candidates(self):
        """A memoized sorted list of candidates for visitchildrenset."""
        return sorted(self._visitchildrenset_candidates)

    def visitchildrenset(self, dir):
        if not self._fileset or dir not in self._dirs:
            return set()

        if dir == b'':
            candidates = self._visitchildrenset_candidates
        else:
            candidates = self._sorted_visitchildrenset_candidates
            d = dir + b'/'
            # Use bisect to find the first element potentially starting with d
            # (i.e. >= d). This should always find at least one element (we'll
            # assert later if this is not the case).
            first = bisect.bisect_left(candidates, d)
            # We need a representation of the first element that is > d that
            # does not start with d, so since we added a `/` on the end of dir,
            # we'll add whatever comes after slash (we could probably assume
            # that `0` is after `/`, but let's not) to the end of dir instead.
            dnext = dir + encoding.strtolocal(chr(ord(b'/') + 1))
            # Use bisect to find the first element >= d_next
            last = bisect.bisect_left(candidates, dnext, lo=first)
            dlen = len(d)
            candidates = {c[dlen:] for c in candidates[first:last]}
        # self._dirs includes all of the directories, recursively, so if
        # we're attempting to match foo/bar/baz.txt, it'll have '', 'foo',
        # 'foo/bar' in it. Thus we can safely ignore a candidate that has a
        # '/' in it, indicating a it's for a subdir-of-a-subdir; the
        # immediate subdir will be in there without a slash.
        ret = {c for c in candidates if b'/' not in c}
        # We really do not expect ret to be empty, since that would imply that
        # there's something in _dirs that didn't have a file in _fileset.
        assert ret
        return ret

    def isexact(self):
        return True

    @encoding.strmethod
    def __repr__(self):
        return b'<exactmatcher files=%r>' % self._files


class differencematcher(basematcher):
    """Composes two matchers by matching if the first matches and the second
    does not.

    The second matcher's non-matching-attributes (bad, traversedir) are ignored.
    """

    def __init__(self, m1, m2):
        super(differencematcher, self).__init__()
        self._m1 = m1
        self._m2 = m2
        self.bad = m1.bad
        self.traversedir = m1.traversedir

    def was_tampered_with(self):
        return (
            self.was_tampered_with_nonrec()
            or self._m1.was_tampered_with()
            or self._m2.was_tampered_with()
        )

    def matchfn(self, f):
        return self._m1(f) and not self._m2(f)

    @propertycache
    def _files(self):
        if self.isexact():
            return [f for f in self._m1.files() if self(f)]
        # If m1 is not an exact matcher, we can't easily figure out the set of
        # files, because its files() are not always files. For example, if
        # m1 is "path:dir" and m2 is "rootfileins:.", we don't
        # want to remove "dir" from the set even though it would match m2,
        # because the "dir" in m1 may not be a file.
        return self._m1.files()

    def visitdir(self, dir):
        if self._m2.visitdir(dir) == b'all':
            return False
        elif not self._m2.visitdir(dir):
            # m2 does not match dir, we can return 'all' here if possible
            return self._m1.visitdir(dir)
        return bool(self._m1.visitdir(dir))

    def visitchildrenset(self, dir):
        m2_set = self._m2.visitchildrenset(dir)
        if m2_set == b'all':
            return set()
        m1_set = self._m1.visitchildrenset(dir)
        # Possible values for m1: 'all', 'this', set(...), set()
        # Possible values for m2:        'this', set(...), set()
        # If m2 has nothing under here that we care about, return m1, even if
        # it's 'all'. This is a change in behavior from visitdir, which would
        # return True, not 'all', for some reason.
        if not m2_set:
            return m1_set
        if m1_set in [b'all', b'this']:
            # Never return 'all' here if m2_set is any kind of non-empty (either
            # 'this' or set(foo)), since m2 might return set() for a
            # subdirectory.
            return b'this'
        # Possible values for m1:         set(...), set()
        # Possible values for m2: 'this', set(...)
        # We ignore m2's set results. They're possibly incorrect:
        #  m1 = path:dir/subdir, m2=rootfilesin:dir, visitchildrenset(''):
        #    m1 returns {'dir'}, m2 returns {'dir'}, if we subtracted we'd
        #    return set(), which is *not* correct, we still need to visit 'dir'!
        return m1_set

    def isexact(self):
        return self._m1.isexact()

    @encoding.strmethod
    def __repr__(self):
        return b'<differencematcher m1=%r, m2=%r>' % (self._m1, self._m2)


def intersectmatchers(m1, m2):
    """Composes two matchers by matching if both of them match.

    The second matcher's non-matching-attributes (bad, traversedir) are ignored.
    """
    if m1 is None or m2 is None:
        return m1 or m2
    if m1.always():
        m = copy.copy(m2)
        # TODO: Consider encapsulating these things in a class so there's only
        # one thing to copy from m1.
        m.bad = m1.bad
        m.traversedir = m1.traversedir
        return m
    if m2.always():
        m = copy.copy(m1)
        return m
    return intersectionmatcher(m1, m2)


class intersectionmatcher(basematcher):
    def __init__(self, m1, m2):
        super(intersectionmatcher, self).__init__()
        self._m1 = m1
        self._m2 = m2
        self.bad = m1.bad
        self.traversedir = m1.traversedir

    def was_tampered_with(self):
        return (
            self.was_tampered_with_nonrec()
            or self._m1.was_tampered_with()
            or self._m2.was_tampered_with()
        )

    @propertycache
    def _files(self):
        if self.isexact():
            m1, m2 = self._m1, self._m2
            if not m1.isexact():
                m1, m2 = m2, m1
            return [f for f in m1.files() if m2(f)]
        # It neither m1 nor m2 is an exact matcher, we can't easily intersect
        # the set of files, because their files() are not always files. For
        # example, if intersecting a matcher "-I glob:foo.txt" with matcher of
        # "path:dir2", we don't want to remove "dir2" from the set.
        return self._m1.files() + self._m2.files()

    def matchfn(self, f):
        return self._m1(f) and self._m2(f)

    def visitdir(self, dir):
        visit1 = self._m1.visitdir(dir)
        if visit1 == b'all':
            return self._m2.visitdir(dir)
        # bool() because visit1=True + visit2='all' should not be 'all'
        return bool(visit1 and self._m2.visitdir(dir))

    def visitchildrenset(self, dir):
        m1_set = self._m1.visitchildrenset(dir)
        if not m1_set:
            return set()
        m2_set = self._m2.visitchildrenset(dir)
        if not m2_set:
            return set()

        if m1_set == b'all':
            return m2_set
        elif m2_set == b'all':
            return m1_set

        if m1_set == b'this' or m2_set == b'this':
            return b'this'

        assert isinstance(m1_set, set) and isinstance(m2_set, set)
        return m1_set.intersection(m2_set)

    def always(self):
        return self._m1.always() and self._m2.always()

    def isexact(self):
        return self._m1.isexact() or self._m2.isexact()

    @encoding.strmethod
    def __repr__(self):
        return b'<intersectionmatcher m1=%r, m2=%r>' % (self._m1, self._m2)


class subdirmatcher(basematcher):
    """Adapt a matcher to work on a subdirectory only.

    The paths are remapped to remove/insert the path as needed:

    >>> from . import pycompat
    >>> m1 = match(util.localpath(b'/root'), b'', [b'a.txt', b'sub/b.txt'], auditor=lambda name: None)
    >>> m2 = subdirmatcher(b'sub', m1)
    >>> m2(b'a.txt')
    False
    >>> m2(b'b.txt')
    True
    >>> m2.matchfn(b'a.txt')
    False
    >>> m2.matchfn(b'b.txt')
    True
    >>> m2.files()
    ['b.txt']
    >>> m2.exact(b'b.txt')
    True
    >>> def bad(f, msg):
    ...     print(pycompat.sysstr(b"%s: %s" % (f, msg)))
    >>> m1.bad = bad
    >>> m2.bad(b'x.txt', b'No such file')
    sub/x.txt: No such file
    """

    def __init__(self, path, matcher):
        super(subdirmatcher, self).__init__()
        self._path = path
        self._matcher = matcher
        self._always = matcher.always()

        self._files = [
            f[len(path) + 1 :]
            for f in matcher._files
            if f.startswith(path + b"/")
        ]

        # If the parent repo had a path to this subrepo and the matcher is
        # a prefix matcher, this submatcher always matches.
        if matcher.prefix():
            self._always = any(f == path for f in matcher._files)

    def was_tampered_with(self):
        return (
            self.was_tampered_with_nonrec() or self._matcher.was_tampered_with()
        )

    def bad(self, f, msg):
        self._matcher.bad(self._path + b"/" + f, msg)

    def matchfn(self, f):
        # Some information is lost in the superclass's constructor, so we
        # can not accurately create the matching function for the subdirectory
        # from the inputs. Instead, we override matchfn() and visitdir() to
        # call the original matcher with the subdirectory path prepended.
        return self._matcher.matchfn(self._path + b"/" + f)

    def visitdir(self, dir):
        if dir == b'':
            dir = self._path
        else:
            dir = self._path + b"/" + dir
        return self._matcher.visitdir(dir)

    def visitchildrenset(self, dir):
        if dir == b'':
            dir = self._path
        else:
            dir = self._path + b"/" + dir
        return self._matcher.visitchildrenset(dir)

    def always(self):
        return self._always

    def prefix(self):
        return self._matcher.prefix() and not self._always

    @encoding.strmethod
    def __repr__(self):
        return b'<subdirmatcher path=%r, matcher=%r>' % (
            self._path,
            self._matcher,
        )


class prefixdirmatcher(basematcher):
    """Adapt a matcher to work on a parent directory.

    The matcher's non-matching-attributes (bad, traversedir) are ignored.

    The prefix path should usually be the relative path from the root of
    this matcher to the root of the wrapped matcher.

    >>> m1 = match(util.localpath(b'/root/d/e'), b'f', [b'../a.txt', b'b.txt'], auditor=lambda name: None)
    >>> m2 = prefixdirmatcher(b'd/e', m1)
    >>> m2(b'a.txt')
    False
    >>> m2(b'd/e/a.txt')
    True
    >>> m2(b'd/e/b.txt')
    False
    >>> m2.files()
    ['d/e/a.txt', 'd/e/f/b.txt']
    >>> m2.exact(b'd/e/a.txt')
    True
    >>> m2.visitdir(b'd')
    True
    >>> m2.visitdir(b'd/e')
    True
    >>> m2.visitdir(b'd/e/f')
    True
    >>> m2.visitdir(b'd/e/g')
    False
    >>> m2.visitdir(b'd/ef')
    False
    """

    def __init__(self, path, matcher, badfn=None):
        super(prefixdirmatcher, self).__init__(badfn)
        if not path:
            raise error.ProgrammingError(b'prefix path must not be empty')
        self._path = path
        self._pathprefix = path + b'/'
        self._matcher = matcher

    @propertycache
    def _files(self):
        return [self._pathprefix + f for f in self._matcher._files]

    def matchfn(self, f):
        if not f.startswith(self._pathprefix):
            return False
        return self._matcher.matchfn(f[len(self._pathprefix) :])

    @propertycache
    def _pathdirs(self):
        return set(pathutil.finddirs(self._path))

    def visitdir(self, dir):
        if dir == self._path:
            return self._matcher.visitdir(b'')
        if dir.startswith(self._pathprefix):
            return self._matcher.visitdir(dir[len(self._pathprefix) :])
        return dir in self._pathdirs

    def visitchildrenset(self, dir):
        if dir == self._path:
            return self._matcher.visitchildrenset(b'')
        if dir.startswith(self._pathprefix):
            return self._matcher.visitchildrenset(dir[len(self._pathprefix) :])
        if dir in self._pathdirs:
            return b'this'
        return set()

    def isexact(self):
        return self._matcher.isexact()

    def prefix(self):
        return self._matcher.prefix()

    @encoding.strmethod
    def __repr__(self):
        return b'<prefixdirmatcher path=%r, matcher=%r>' % (
            pycompat.bytestr(self._path),
            self._matcher,
        )


class unionmatcher(basematcher):
    """A matcher that is the union of several matchers.

    The non-matching-attributes (bad, traversedir) are taken from the first
    matcher.
    """

    def __init__(self, matchers):
        m1 = matchers[0]
        super(unionmatcher, self).__init__()
        self.traversedir = m1.traversedir
        self._matchers = matchers

    def was_tampered_with(self):
        return self.was_tampered_with_nonrec() or any(
            map(lambda m: m.was_tampered_with(), self._matchers)
        )

    def matchfn(self, f):
        for match in self._matchers:
            if match(f):
                return True
        return False

    def visitdir(self, dir):
        r = False
        for m in self._matchers:
            v = m.visitdir(dir)
            if v == b'all':
                return v
            r |= v
        return r

    def visitchildrenset(self, dir):
        r = set()
        this = False
        for m in self._matchers:
            v = m.visitchildrenset(dir)
            if not v:
                continue
            if v == b'all':
                return v
            if this or v == b'this':
                this = True
                # don't break, we might have an 'all' in here.
                continue
            assert isinstance(v, set)
            r = r.union(v)
        if this:
            return b'this'
        return r

    @encoding.strmethod
    def __repr__(self):
        return b'<unionmatcher matchers=%r>' % self._matchers


def patkind(pattern, default=None):
    r"""If pattern is 'kind:pat' with a known kind, return kind.

    >>> patkind(br're:.*\.c$')
    're'
    >>> patkind(b'glob:*.c')
    'glob'
    >>> patkind(b'relpath:test.py')
    'relpath'
    >>> patkind(b'main.py')
    >>> patkind(b'main.py', default=b're')
    're'
    """
    return _patsplit(pattern, default)[0]


def _patsplit(pattern, default):
    """Split a string into the optional pattern kind prefix and the actual
    pattern."""
    if b':' in pattern:
        kind, pat = pattern.split(b':', 1)
        if kind in allpatternkinds:
            return kind, pat
    return default, pattern


def _globre(pat):
    r"""Convert an extended glob string to a regexp string.

    >>> from . import pycompat
    >>> def bprint(s):
    ...     print(pycompat.sysstr(s))
    >>> bprint(_globre(br'?'))
    .
    >>> bprint(_globre(br'*'))
    [^/]*
    >>> bprint(_globre(br'**'))
    .*
    >>> bprint(_globre(br'**/a'))
    (?:.*/)?a
    >>> bprint(_globre(br'a/**/b'))
    a/(?:.*/)?b
    >>> bprint(_globre(br'[a*?!^][^b][!c]'))
    [a*?!^][\^b][^c]
    >>> bprint(_globre(br'{a,b}'))
    (?:a|b)
    >>> bprint(_globre(br'.\*\?'))
    \.\*\?
    """
    i, n = 0, len(pat)
    res = b''
    group = 0
    escape = util.stringutil.regexbytesescapemap.get

    def peek():
        return i < n and pat[i : i + 1]

    while i < n:
        c = pat[i : i + 1]
        i += 1
        if c not in b'*?[{},\\':
            res += escape(c, c)
        elif c == b'*':
            if peek() == b'*':
                i += 1
                if peek() == b'/':
                    i += 1
                    res += b'(?:.*/)?'
                else:
                    res += b'.*'
            else:
                res += b'[^/]*'
        elif c == b'?':
            res += b'.'
        elif c == b'[':
            j = i
            if j < n and pat[j : j + 1] in b'!]':
                j += 1
            while j < n and pat[j : j + 1] != b']':
                j += 1
            if j >= n:
                res += b'\\['
            else:
                stuff = pat[i:j].replace(b'\\', b'\\\\')
                i = j + 1
                if stuff[0:1] == b'!':
                    stuff = b'^' + stuff[1:]
                elif stuff[0:1] == b'^':
                    stuff = b'\\' + stuff
                res = b'%s[%s]' % (res, stuff)
        elif c == b'{':
            group += 1
            res += b'(?:'
        elif c == b'}' and group:
            res += b')'
            group -= 1
        elif c == b',' and group:
            res += b'|'
        elif c == b'\\':
            p = peek()
            if p:
                i += 1
                res += escape(p, p)
            else:
                res += escape(c, c)
        else:
            res += escape(c, c)
    return res


FLAG_RE = util.re.compile(br'^\(\?([aiLmsux]+)\)(.*)')


def _regex(kind, pat, globsuffix):
    """Convert a (normalized) pattern of any kind into a
    regular expression.
    globsuffix is appended to the regexp of globs."""
    if not pat and kind in (b'glob', b'relpath'):
        return b''
    if kind == b're':
        return pat
    if kind == b'filepath':
        raise error.ProgrammingError(
            "'filepath:' patterns should not be converted to a regex"
        )
    if kind in (b'path', b'relpath'):
        if pat == b'.':
            return b''
        return util.stringutil.reescape(pat) + b'(?:/|$)'
    if kind == b'rootfilesin':
        if pat == b'.':
            escaped = b''
        else:
            # Pattern is a directory name.
            escaped = util.stringutil.reescape(pat) + b'/'
        # Anything after the pattern must be a non-directory.
        return escaped + b'[^/]+$'
    if kind == b'relglob':
        globre = _globre(pat)
        if globre.startswith(b'[^/]*'):
            # When pat has the form *XYZ (common), make the returned regex more
            # legible by returning the regex for **XYZ instead of **/*XYZ.
            return b'.*' + globre[len(b'[^/]*') :] + globsuffix
        return b'(?:|.*/)' + globre + globsuffix
    if kind == b'relre':
        flag = None
        m = FLAG_RE.match(pat)
        if m:
            flag, pat = m.groups()
        if not pat.startswith(b'^'):
            pat = b'.*' + pat
        if flag is not None:
            pat = br'(?%s:%s)' % (flag, pat)
        return pat
    if kind in (b'glob', b'rootglob'):
        return _globre(pat) + globsuffix
    raise error.ProgrammingError(b'not a regex pattern: %s:%s' % (kind, pat))


def _buildmatch(kindpats, globsuffix, root):
    """Return regexp string and a matcher function for kindpats.
    globsuffix is appended to the regexp of globs."""
    matchfuncs = []

    subincludes, kindpats = _expandsubinclude(kindpats, root)
    if subincludes:
        submatchers = {}

        def matchsubinclude(f):
            for prefix, matcherargs in subincludes:
                if f.startswith(prefix):
                    mf = submatchers.get(prefix)
                    if mf is None:
                        mf = match(*matcherargs)
                        submatchers[prefix] = mf

                    if mf(f[len(prefix) :]):
                        return True
            return False

        matchfuncs.append(matchsubinclude)

    regex = b''
    if kindpats:
        if all(k == b'rootfilesin' for k, p, s in kindpats):
            dirs = {p for k, p, s in kindpats}

            def mf(f):
                i = f.rfind(b'/')
                if i >= 0:
                    dir = f[:i]
                else:
                    dir = b'.'
                return dir in dirs

            regex = b'rootfilesin: %s' % stringutil.pprint(list(sorted(dirs)))
            matchfuncs.append(mf)
        else:
            regex, mf = _buildregexmatch(kindpats, globsuffix)
            matchfuncs.append(mf)

    if len(matchfuncs) == 1:
        return regex, matchfuncs[0]
    else:
        return regex, lambda f: any(mf(f) for mf in matchfuncs)


MAX_RE_SIZE = 20000


def _joinregexes(regexps):
    """gather multiple regular expressions into a single one"""
    return b'|'.join(regexps)


def _buildregexmatch(kindpats, globsuffix):
    """Build a match function from a list of kinds and kindpats,
    return regexp string and a matcher function.

    Test too large input
    >>> _buildregexmatch([
    ...     (b'relglob', b'?' * MAX_RE_SIZE, b'')
    ... ], b'$')
    Traceback (most recent call last):
    ...
    Abort: matcher pattern is too long (20009 bytes)
    """
    try:
        allgroups = []
        regexps = []
        exact = set()
        for kind, pattern, _source in kindpats:
            if kind == b'filepath':
                exact.add(pattern)
                continue
            regexps.append(_regex(kind, pattern, globsuffix))

        fullregexp = _joinregexes(regexps)

        startidx = 0
        groupsize = 0
        for idx, r in enumerate(regexps):
            piecesize = len(r)
            if piecesize > MAX_RE_SIZE:
                msg = _(b"matcher pattern is too long (%d bytes)") % piecesize
                raise error.Abort(msg)
            elif (groupsize + piecesize) > MAX_RE_SIZE:
                group = regexps[startidx:idx]
                allgroups.append(_joinregexes(group))
                startidx = idx
                groupsize = 0
            groupsize += piecesize + 1

        if startidx == 0:
            matcher = _rematcher(fullregexp)
            func = lambda s: bool(matcher(s))
        else:
            group = regexps[startidx:]
            allgroups.append(_joinregexes(group))
            allmatchers = [_rematcher(g) for g in allgroups]
            func = lambda s: any(m(s) for m in allmatchers)

        actualfunc = func
        if exact:
            # An empty regex will always match, so only call the regex if
            # there were any actual patterns to match.
            if not regexps:
                actualfunc = lambda s: s in exact
            else:
                actualfunc = lambda s: s in exact or func(s)
        return fullregexp, actualfunc
    except re.error:
        for k, p, s in kindpats:
            if k == b'filepath':
                continue
            try:
                _rematcher(_regex(k, p, globsuffix))
            except re.error:
                if s:
                    raise error.Abort(
                        _(b"%s: invalid pattern (%s): %s") % (s, k, p)
                    )
                else:
                    raise error.Abort(_(b"invalid pattern (%s): %s") % (k, p))
        raise error.Abort(_(b"invalid pattern"))


def _patternrootsanddirs(kindpats):
    """Returns roots and directories corresponding to each pattern.

    This calculates the roots and directories exactly matching the patterns and
    returns a tuple of (roots, dirs) for each. It does not return other
    directories which may also need to be considered, like the parent
    directories.
    """
    r = []
    d = []
    for kind, pat, source in kindpats:
        if kind in (b'glob', b'rootglob'):  # find the non-glob prefix
            root = []
            for p in pat.split(b'/'):
                if b'[' in p or b'{' in p or b'*' in p or b'?' in p:
                    break
                root.append(p)
            r.append(b'/'.join(root))
        elif kind in (b'relpath', b'path', b'filepath'):
            if pat == b'.':
                pat = b''
            r.append(pat)
        elif kind in (b'rootfilesin',):
            if pat == b'.':
                pat = b''
            d.append(pat)
        else:  # relglob, re, relre
            r.append(b'')
    return r, d


def _roots(kindpats):
    '''Returns root directories to match recursively from the given patterns.'''
    roots, dirs = _patternrootsanddirs(kindpats)
    return roots


def _rootsdirsandparents(kindpats):
    """Returns roots and exact directories from patterns.

    `roots` are directories to match recursively, `dirs` should
    be matched non-recursively, and `parents` are the implicitly required
    directories to walk to items in either roots or dirs.

    Returns a tuple of (roots, dirs, parents).

    >>> r = _rootsdirsandparents(
    ...     [(b'glob', b'g/h/*', b''), (b'glob', b'g/h', b''),
    ...      (b'glob', b'g*', b'')])
    >>> print(r[0:2], sorted(r[2])) # the set has an unstable output
    (['g/h', 'g/h', ''], []) ['', 'g']
    >>> r = _rootsdirsandparents(
    ...     [(b'rootfilesin', b'g/h', b''), (b'rootfilesin', b'', b'')])
    >>> print(r[0:2], sorted(r[2])) # the set has an unstable output
    ([], ['g/h', '']) ['', 'g']
    >>> r = _rootsdirsandparents(
    ...     [(b'relpath', b'r', b''), (b'path', b'p/p', b''),
    ...      (b'path', b'', b'')])
    >>> print(r[0:2], sorted(r[2])) # the set has an unstable output
    (['r', 'p/p', ''], []) ['', 'p']
    >>> r = _rootsdirsandparents(
    ...     [(b'relglob', b'rg*', b''), (b're', b're/', b''),
    ...      (b'relre', b'rr', b'')])
    >>> print(r[0:2], sorted(r[2])) # the set has an unstable output
    (['', '', ''], []) ['']
    """
    r, d = _patternrootsanddirs(kindpats)

    p = set()
    # Add the parents as non-recursive/exact directories, since they must be
    # scanned to get to either the roots or the other exact directories.
    p.update(pathutil.dirs(d))
    p.update(pathutil.dirs(r))

    # FIXME: all uses of this function convert these to sets, do so before
    # returning.
    # FIXME: all uses of this function do not need anything in 'roots' and
    # 'dirs' to also be in 'parents', consider removing them before returning.
    return r, d, p


def _explicitfiles(kindpats):
    """Returns the potential explicit filenames from the patterns.

    >>> _explicitfiles([(b'path', b'foo/bar', b'')])
    ['foo/bar']
    >>> _explicitfiles([(b'rootfilesin', b'foo/bar', b'')])
    []
    """
    # Keep only the pattern kinds where one can specify filenames (vs only
    # directory names).
    filable = [kp for kp in kindpats if kp[0] not in (b'rootfilesin',)]
    return _roots(filable)


def _prefix(kindpats):
    '''Whether all the patterns match a prefix (i.e. recursively)'''
    for kind, pat, source in kindpats:
        if kind not in (b'path', b'relpath'):
            return False
    return True


_commentre = None


def readpatternfile(filepath, warn, sourceinfo=False):
    """parse a pattern file, returning a list of
    patterns. These patterns should be given to compile()
    to be validated and converted into a match function.

    trailing white space is dropped.
    the escape character is backslash.
    comments start with #.
    empty lines are skipped.

    lines can be of the following formats:

    syntax: regexp # defaults following lines to non-rooted regexps
    syntax: glob   # defaults following lines to non-rooted globs
    re:pattern     # non-rooted regular expression
    glob:pattern   # non-rooted glob
    rootglob:pat   # rooted glob (same root as ^ in regexps)
    pattern        # pattern of the current default type

    if sourceinfo is set, returns a list of tuples:
    (pattern, lineno, originalline).
    This is useful to debug ignore patterns.
    """

    syntaxes = {
        b're': b'relre:',
        b'regexp': b'relre:',
        b'glob': b'relglob:',
        b'rootglob': b'rootglob:',
        b'include': b'include',
        b'subinclude': b'subinclude',
    }
    syntax = b'relre:'
    patterns = []

    fp = open(filepath, b'rb')
    for lineno, line in enumerate(fp, start=1):
        if b"#" in line:
            global _commentre
            if not _commentre:
                _commentre = util.re.compile(br'((?:^|[^\\])(?:\\\\)*)#.*')
            # remove comments prefixed by an even number of escapes
            m = _commentre.search(line)
            if m:
                line = line[: m.end(1)]
            # fixup properly escaped comments that survived the above
            line = line.replace(b"\\#", b"#")
        line = line.rstrip()
        if not line:
            continue

        if line.startswith(b'syntax:'):
            s = line[7:].strip()
            try:
                syntax = syntaxes[s]
            except KeyError:
                if warn:
                    warn(
                        _(b"%s: ignoring invalid syntax '%s'\n") % (filepath, s)
                    )
            continue

        linesyntax = syntax
        for s, rels in syntaxes.items():
            if line.startswith(rels):
                linesyntax = rels
                line = line[len(rels) :]
                break
            elif line.startswith(s + b':'):
                linesyntax = rels
                line = line[len(s) + 1 :]
                break
        if sourceinfo:
            patterns.append((linesyntax + line, lineno, line))
        else:
            patterns.append(linesyntax + line)
    fp.close()
    return patterns
