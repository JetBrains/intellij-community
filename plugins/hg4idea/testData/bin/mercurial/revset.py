# revset.py - revision set queries for mercurial
#
# Copyright 2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import binascii
import functools
import random
import re

from .i18n import _
from .node import (
    bin,
    nullrev,
    wdirrev,
)
from . import (
    dagop,
    destutil,
    diffutil,
    encoding,
    error,
    grep as grepmod,
    hbisect,
    match as matchmod,
    obsolete as obsmod,
    obsutil,
    pathutil,
    phases,
    pycompat,
    registrar,
    repoview,
    revsetlang,
    scmutil,
    smartset,
    stack as stackmod,
    util,
)
from .utils import (
    dateutil,
    stringutil,
    urlutil,
)

# helpers for processing parsed tree
getsymbol = revsetlang.getsymbol
getstring = revsetlang.getstring
getinteger = revsetlang.getinteger
getboolean = revsetlang.getboolean
getlist = revsetlang.getlist
getintrange = revsetlang.getintrange
getargs = revsetlang.getargs
getargsdict = revsetlang.getargsdict

baseset = smartset.baseset
generatorset = smartset.generatorset
spanset = smartset.spanset
fullreposet = smartset.fullreposet

# revisions not included in all(), but populated if specified
_virtualrevs = (nullrev, wdirrev)

# Constants for ordering requirement, used in getset():
#
# If 'define', any nested functions and operations MAY change the ordering of
# the entries in the set (but if changes the ordering, it MUST ALWAYS change
# it). If 'follow', any nested functions and operations MUST take the ordering
# specified by the first operand to the '&' operator.
#
# For instance,
#
#   X & (Y | Z)
#   ^   ^^^^^^^
#   |   follow
#   define
#
# will be evaluated as 'or(y(x()), z(x()))', where 'x()' can change the order
# of the entries in the set, but 'y()', 'z()' and 'or()' shouldn't.
#
# 'any' means the order doesn't matter. For instance,
#
#   (X & !Y) | ancestors(Z)
#         ^              ^
#         any            any
#
# For 'X & !Y', 'X' decides the order and 'Y' is subtracted from 'X', so the
# order of 'Y' does not matter. For 'ancestors(Z)', Z's order does not matter
# since 'ancestors' does not care about the order of its argument.
#
# Currently, most revsets do not care about the order, so 'define' is
# equivalent to 'follow' for them, and the resulting order is based on the
# 'subset' parameter passed down to them:
#
#   m = revset.match(...)
#   m(repo, subset, order=defineorder)
#           ^^^^^^
#      For most revsets, 'define' means using the order this subset provides
#
# There are a few revsets that always redefine the order if 'define' is
# specified: 'sort(X)', 'reverse(X)', 'x:y'.
anyorder = b'any'  # don't care the order, could be even random-shuffled
defineorder = b'define'  # ALWAYS redefine, or ALWAYS follow the current order
followorder = b'follow'  # MUST follow the current order

# helpers


def getset(repo, subset, x, order=defineorder):
    if not x:
        raise error.ParseError(_(b"missing argument"))
    return methods[x[0]](repo, subset, *x[1:], order=order)


def _getrevsource(repo, r):
    extra = repo[r].extra()
    for label in (b'source', b'transplant_source', b'rebase_source'):
        if label in extra:
            try:
                return repo[extra[label]].rev()
            except error.RepoLookupError:
                pass
    return None


def _sortedb(xs):
    return sorted(pycompat.rapply(pycompat.maybebytestr, xs))


# operator methods


def stringset(repo, subset, x, order):
    if not x:
        raise error.ParseError(_(b"empty string is not a valid revision"))
    x = scmutil.intrev(scmutil.revsymbol(repo, x))
    if x in subset or x in _virtualrevs and isinstance(subset, fullreposet):
        return baseset([x])
    return baseset()


def rawsmartset(repo, subset, x, order):
    """argument is already a smartset, use that directly"""
    if order == followorder:
        return subset & x
    else:
        return x & subset


def raw_node_set(repo, subset, x, order):
    """argument is a list of nodeid, resolve and use them"""
    nodes = _ordered_node_set(repo, x)
    if order == followorder:
        return subset & nodes
    else:
        return nodes & subset


def _ordered_node_set(repo, nodes):
    if not nodes:
        return baseset()
    to_rev = repo.changelog.index.rev
    return baseset([to_rev(r) for r in nodes])


def rangeset(repo, subset, x, y, order):
    m = getset(repo, fullreposet(repo), x)
    n = getset(repo, fullreposet(repo), y)

    if not m or not n:
        return baseset()
    return _makerangeset(repo, subset, m.first(), n.last(), order)


def rangeall(repo, subset, x, order):
    assert x is None
    return _makerangeset(repo, subset, 0, repo.changelog.tiprev(), order)


def rangepre(repo, subset, y, order):
    # ':y' can't be rewritten to '0:y' since '0' may be hidden
    n = getset(repo, fullreposet(repo), y)
    if not n:
        return baseset()
    return _makerangeset(repo, subset, 0, n.last(), order)


def rangepost(repo, subset, x, order):
    m = getset(repo, fullreposet(repo), x)
    if not m:
        return baseset()
    return _makerangeset(
        repo, subset, m.first(), repo.changelog.tiprev(), order
    )


def _makerangeset(repo, subset, m, n, order):
    if m == n:
        r = baseset([m])
    elif n == wdirrev:
        r = spanset(repo, m, len(repo)) + baseset([n])
    elif m == wdirrev:
        r = baseset([m]) + spanset(repo, repo.changelog.tiprev(), n - 1)
    elif m < n:
        r = spanset(repo, m, n + 1)
    else:
        r = spanset(repo, m, n - 1)

    if order == defineorder:
        return r & subset
    else:
        # carrying the sorting over when possible would be more efficient
        return subset & r


def dagrange(repo, subset, x, y, order):
    r = fullreposet(repo)
    xs = dagop.reachableroots(
        repo, getset(repo, r, x), getset(repo, r, y), includepath=True
    )
    return subset & xs


def andset(repo, subset, x, y, order):
    if order == anyorder:
        yorder = anyorder
    else:
        yorder = followorder
    return getset(repo, getset(repo, subset, x, order), y, yorder)


def andsmallyset(repo, subset, x, y, order):
    # 'andsmally(x, y)' is equivalent to 'and(x, y)', but faster when y is small
    if order == anyorder:
        yorder = anyorder
    else:
        yorder = followorder
    return getset(repo, getset(repo, subset, y, yorder), x, order)


def differenceset(repo, subset, x, y, order):
    return getset(repo, subset, x, order) - getset(repo, subset, y, anyorder)


def _orsetlist(repo, subset, xs, order):
    assert xs
    if len(xs) == 1:
        return getset(repo, subset, xs[0], order)
    p = len(xs) // 2
    a = _orsetlist(repo, subset, xs[:p], order)
    b = _orsetlist(repo, subset, xs[p:], order)
    return a + b


def orset(repo, subset, x, order):
    xs = getlist(x)
    if not xs:
        return baseset()
    if order == followorder:
        # slow path to take the subset order
        return subset & _orsetlist(repo, fullreposet(repo), xs, anyorder)
    else:
        return _orsetlist(repo, subset, xs, order)


def notset(repo, subset, x, order):
    return subset - getset(repo, subset, x, anyorder)


def relationset(repo, subset, x, y, order):
    # this is pretty basic implementation of 'x#y' operator, still
    # experimental so undocumented. see the wiki for further ideas.
    # https://www.mercurial-scm.org/wiki/RevsetOperatorPlan
    rel = getsymbol(y)
    if rel in relations:
        return relations[rel](repo, subset, x, rel, order)

    relnames = [r for r in relations.keys() if len(r) > 1]
    raise error.UnknownIdentifier(rel, relnames)


def _splitrange(a, b):
    """Split range with bounds a and b into two ranges at 0 and return two
    tuples of numbers for use as startdepth and stopdepth arguments of
    revancestors and revdescendants.

    >>> _splitrange(-10, -5)     # [-10:-5]
    ((5, 11), (None, None))
    >>> _splitrange(5, 10)       # [5:10]
    ((None, None), (5, 11))
    >>> _splitrange(-10, 10)     # [-10:10]
    ((0, 11), (0, 11))
    >>> _splitrange(-10, 0)      # [-10:0]
    ((0, 11), (None, None))
    >>> _splitrange(0, 10)       # [0:10]
    ((None, None), (0, 11))
    >>> _splitrange(0, 0)        # [0:0]
    ((0, 1), (None, None))
    >>> _splitrange(1, -1)       # [1:-1]
    ((None, None), (None, None))
    """
    ancdepths = (None, None)
    descdepths = (None, None)
    if a == b == 0:
        ancdepths = (0, 1)
    if a < 0:
        ancdepths = (-min(b, 0), -a + 1)
    if b > 0:
        descdepths = (max(a, 0), b + 1)
    return ancdepths, descdepths


def generationsrel(repo, subset, x, rel, order):
    z = (b'rangeall', None)
    return generationssubrel(repo, subset, x, rel, z, order)


def generationssubrel(repo, subset, x, rel, z, order):
    # TODO: rewrite tests, and drop startdepth argument from ancestors() and
    # descendants() predicates
    a, b = getintrange(
        z,
        _(b'relation subscript must be an integer or a range'),
        _(b'relation subscript bounds must be integers'),
        deffirst=-(dagop.maxlogdepth - 1),
        deflast=+(dagop.maxlogdepth - 1),
    )
    (ancstart, ancstop), (descstart, descstop) = _splitrange(a, b)

    if ancstart is None and descstart is None:
        return baseset()

    revs = getset(repo, fullreposet(repo), x)
    if not revs:
        return baseset()

    if ancstart is not None and descstart is not None:
        s = dagop.revancestors(repo, revs, False, ancstart, ancstop)
        s += dagop.revdescendants(repo, revs, False, descstart, descstop)
    elif ancstart is not None:
        s = dagop.revancestors(repo, revs, False, ancstart, ancstop)
    elif descstart is not None:
        s = dagop.revdescendants(repo, revs, False, descstart, descstop)

    return subset & s


def relsubscriptset(repo, subset, x, y, z, order):
    # this is pretty basic implementation of 'x#y[z]' operator, still
    # experimental so undocumented. see the wiki for further ideas.
    # https://www.mercurial-scm.org/wiki/RevsetOperatorPlan
    rel = getsymbol(y)
    if rel in subscriptrelations:
        return subscriptrelations[rel](repo, subset, x, rel, z, order)

    relnames = [r for r in subscriptrelations.keys() if len(r) > 1]
    raise error.UnknownIdentifier(rel, relnames)


def subscriptset(repo, subset, x, y, order):
    raise error.ParseError(_(b"can't use a subscript in this context"))


def listset(repo, subset, *xs, **opts):
    raise error.ParseError(
        _(b"can't use a list in this context"),
        hint=_(b'see \'hg help "revsets.x or y"\''),
    )


def keyvaluepair(repo, subset, k, v, order):
    raise error.ParseError(_(b"can't use a key-value pair in this context"))


def func(repo, subset, a, b, order):
    f = getsymbol(a)
    if f in symbols:
        func = symbols[f]
        if getattr(func, '_takeorder', False):
            return func(repo, subset, b, order)
        return func(repo, subset, b)

    keep = lambda fn: getattr(fn, '__doc__', None) is not None

    syms = [s for (s, fn) in symbols.items() if keep(fn)]
    raise error.UnknownIdentifier(f, syms)


# functions

# symbols are callables like:
#   fn(repo, subset, x)
# with:
#   repo - current repository instance
#   subset - of revisions to be examined
#   x - argument in tree form
symbols = revsetlang.symbols

# symbols which can't be used for a DoS attack for any given input
# (e.g. those which accept regexes as plain strings shouldn't be included)
# functions that just return a lot of changesets (like all) don't count here
safesymbols = set()

predicate = registrar.revsetpredicate()


@predicate(b'_destupdate')
def _destupdate(repo, subset, x):
    # experimental revset for update destination
    args = getargsdict(x, b'limit', b'clean')
    return subset & baseset(
        [destutil.destupdate(repo, **pycompat.strkwargs(args))[0]]
    )


@predicate(b'_destmerge')
def _destmerge(repo, subset, x):
    # experimental revset for merge destination
    sourceset = None
    if x is not None:
        sourceset = getset(repo, fullreposet(repo), x)
    return subset & baseset([destutil.destmerge(repo, sourceset=sourceset)])


@predicate(b'adds(pattern)', safe=True, weight=30)
def adds(repo, subset, x):
    """Changesets that add a file matching pattern.

    The pattern without explicit kind like ``glob:`` is expected to be
    relative to the current directory and match against a file or a
    directory.
    """
    # i18n: "adds" is a keyword
    pat = getstring(x, _(b"adds requires a pattern"))
    return checkstatus(repo, subset, pat, 'added')


@predicate(b'ancestor(*changeset)', safe=True, weight=0.5)
def ancestor(repo, subset, x):
    """A greatest common ancestor of the changesets.

    Accepts 0 or more changesets.
    Will return empty list when passed no args.
    Greatest common ancestor of a single changeset is that changeset.
    """
    reviter = iter(orset(repo, fullreposet(repo), x, order=anyorder))
    try:
        anc = repo[next(reviter)]
    except StopIteration:
        return baseset()
    for r in reviter:
        anc = anc.ancestor(repo[r])

    r = scmutil.intrev(anc)
    if r in subset:
        return baseset([r])
    return baseset()


def _ancestors(
    repo, subset, x, followfirst=False, startdepth=None, stopdepth=None
):
    heads = getset(repo, fullreposet(repo), x)
    if not heads:
        return baseset()
    s = dagop.revancestors(repo, heads, followfirst, startdepth, stopdepth)
    return subset & s


@predicate(b'ancestors(set[, depth])', safe=True)
def ancestors(repo, subset, x):
    """Changesets that are ancestors of changesets in set, including the
    given changesets themselves.

    If depth is specified, the result only includes changesets up to
    the specified generation.
    """
    # startdepth is for internal use only until we can decide the UI
    args = getargsdict(x, b'ancestors', b'set depth startdepth')
    if b'set' not in args:
        # i18n: "ancestors" is a keyword
        raise error.ParseError(_(b'ancestors takes at least 1 argument'))
    startdepth = stopdepth = None
    if b'startdepth' in args:
        n = getinteger(
            args[b'startdepth'], b"ancestors expects an integer startdepth"
        )
        if n < 0:
            raise error.ParseError(b"negative startdepth")
        startdepth = n
    if b'depth' in args:
        # i18n: "ancestors" is a keyword
        n = getinteger(args[b'depth'], _(b"ancestors expects an integer depth"))
        if n < 0:
            raise error.ParseError(_(b"negative depth"))
        stopdepth = n + 1
    return _ancestors(
        repo, subset, args[b'set'], startdepth=startdepth, stopdepth=stopdepth
    )


@predicate(b'_firstancestors', safe=True)
def _firstancestors(repo, subset, x):
    # ``_firstancestors(set)``
    # Like ``ancestors(set)`` but follows only the first parents.
    return _ancestors(repo, subset, x, followfirst=True)


def _childrenspec(repo, subset, x, n, order):
    """Changesets that are the Nth child of a changeset
    in set.
    """
    cs = set()
    for r in getset(repo, fullreposet(repo), x):
        for i in range(n):
            c = repo[r].children()
            if len(c) == 0:
                break
            if len(c) > 1:
                raise error.RepoLookupError(
                    _(b"revision in set has more than one child")
                )
            r = c[0].rev()
        else:
            cs.add(r)
    return subset & cs


def ancestorspec(repo, subset, x, n, order):
    """``set~n``
    Changesets that are the Nth ancestor (first parents only) of a changeset
    in set.
    """
    n = getinteger(n, _(b"~ expects a number"))
    if n < 0:
        # children lookup
        return _childrenspec(repo, subset, x, -n, order)
    ps = set()
    cl = repo.changelog
    for r in getset(repo, fullreposet(repo), x):
        for i in range(n):
            try:
                r = cl.parentrevs(r)[0]
            except error.WdirUnsupported:
                r = repo[r].p1().rev()
        ps.add(r)
    return subset & ps


@predicate(b'author(string)', safe=True, weight=10)
def author(repo, subset, x):
    """Alias for ``user(string)``."""
    # i18n: "author" is a keyword
    n = getstring(x, _(b"author requires a string"))
    kind, pattern, matcher = _substringmatcher(n, casesensitive=False)
    return subset.filter(
        lambda x: matcher(repo[x].user()), condrepr=(b'<user %r>', n)
    )


@predicate(b'bisect(string)', safe=True)
def bisect(repo, subset, x):
    """Changesets marked in the specified bisect status:

    - ``good``, ``bad``, ``skip``: csets explicitly marked as good/bad/skip
    - ``goods``, ``bads``      : csets topologically good/bad
    - ``range``              : csets taking part in the bisection
    - ``pruned``             : csets that are goods, bads or skipped
    - ``untested``           : csets whose fate is yet unknown
    - ``ignored``            : csets ignored due to DAG topology
    - ``current``            : the cset currently being bisected
    """
    # i18n: "bisect" is a keyword
    status = getstring(x, _(b"bisect requires a string")).lower()
    state = set(hbisect.get(repo, status))
    return subset & state


# Backward-compatibility
# - no help entry so that we do not advertise it any more
@predicate(b'bisected', safe=True)
def bisected(repo, subset, x):
    return bisect(repo, subset, x)


@predicate(b'bookmark([name])', safe=True)
def bookmark(repo, subset, x):
    """The named bookmark or all bookmarks.

    Pattern matching is supported for `name`. See :hg:`help revisions.patterns`.
    """
    # i18n: "bookmark" is a keyword
    args = getargs(x, 0, 1, _(b'bookmark takes one or no arguments'))
    if args:
        bm = getstring(
            args[0],
            # i18n: "bookmark" is a keyword
            _(b'the argument to bookmark must be a string'),
        )
        kind, pattern, matcher = stringutil.stringmatcher(bm)
        bms = set()
        if kind == b'literal':
            if bm == pattern:
                pattern = repo._bookmarks.expandname(pattern)
            bmrev = repo._bookmarks.get(pattern, None)
            if not bmrev:
                raise error.RepoLookupError(
                    _(b"bookmark '%s' does not exist") % pattern
                )
            bms.add(repo[bmrev].rev())
        else:
            matchrevs = set()
            for name, bmrev in repo._bookmarks.items():
                if matcher(name):
                    matchrevs.add(bmrev)
            for bmrev in matchrevs:
                bms.add(repo[bmrev].rev())
    else:
        bms = {repo[r].rev() for r in repo._bookmarks.values()}
    bms -= {nullrev}
    return subset & bms


@predicate(b'branch(string or set)', safe=True, weight=10)
def branch(repo, subset, x):
    """
    All changesets belonging to the given branch or the branches of the given
    changesets.

    Pattern matching is supported for `string`. See
    :hg:`help revisions.patterns`.
    """
    getbi = repo.revbranchcache().branchinfo

    def getbranch(r):
        try:
            return getbi(r)[0]
        except error.WdirUnsupported:
            return repo[r].branch()

    try:
        b = getstring(x, b'')
    except error.ParseError:
        # not a string, but another revspec, e.g. tip()
        pass
    else:
        kind, pattern, matcher = stringutil.stringmatcher(b)
        if kind == b'literal':
            # note: falls through to the revspec case if no branch with
            # this name exists and pattern kind is not specified explicitly
            if repo.branchmap().hasbranch(pattern):
                return subset.filter(
                    lambda r: matcher(getbranch(r)),
                    condrepr=(b'<branch %r>', b),
                )
            if b.startswith(b'literal:'):
                raise error.RepoLookupError(
                    _(b"branch '%s' does not exist") % pattern
                )
        else:
            return subset.filter(
                lambda r: matcher(getbranch(r)), condrepr=(b'<branch %r>', b)
            )

    s = getset(repo, fullreposet(repo), x)
    b = set()
    for r in s:
        b.add(getbranch(r))
    c = s.__contains__
    return subset.filter(
        lambda r: c(r) or getbranch(r) in b,
        condrepr=lambda: b'<branch %r>' % _sortedb(b),
    )


@predicate(b'phasedivergent()', safe=True)
def phasedivergent(repo, subset, x):
    """Mutable changesets marked as successors of public changesets.

    Only non-public and non-obsolete changesets can be `phasedivergent`.
    (EXPERIMENTAL)
    """
    # i18n: "phasedivergent" is a keyword
    getargs(x, 0, 0, _(b"phasedivergent takes no arguments"))
    phasedivergent = obsmod.getrevs(repo, b'phasedivergent')
    return subset & phasedivergent


@predicate(b'bundle()', safe=True)
def bundle(repo, subset, x):
    """Changesets in the bundle.

    Bundle must be specified by the -R option."""

    try:
        bundlerevs = repo.changelog.bundlerevs
    except AttributeError:
        raise error.Abort(_(b"no bundle provided - specify with -R"))
    return subset & bundlerevs


def checkstatus(repo, subset, pat, field):
    """Helper for status-related revsets (adds, removes, modifies).
    The field parameter says which kind is desired.
    """
    hasset = matchmod.patkind(pat) == b'set'

    mcache = [None]

    def matches(x):
        c = repo[x]
        if not mcache[0] or hasset:
            mcache[0] = matchmod.match(repo.root, repo.getcwd(), [pat], ctx=c)
        m = mcache[0]
        fname = None

        assert m is not None  # help pytype
        if not m.anypats() and len(m.files()) == 1:
            fname = m.files()[0]
        if fname is not None:
            if fname not in c.files():
                return False
        else:
            if not any(m(f) for f in c.files()):
                return False
        files = getattr(repo.status(c.p1().node(), c.node()), field)
        if fname is not None:
            if fname in files:
                return True
        else:
            if any(m(f) for f in files):
                return True

    return subset.filter(
        matches, condrepr=(b'<status.%s %r>', pycompat.sysbytes(field), pat)
    )


def _children(repo, subset, parentset):
    if not parentset:
        return baseset()
    cs = set()
    pr = repo.changelog.parentrevs
    minrev = parentset.min()
    for r in subset:
        if r <= minrev:
            continue
        p1, p2 = pr(r)
        if p1 in parentset:
            cs.add(r)
        if p2 != nullrev and p2 in parentset:
            cs.add(r)
    return baseset(cs)


@predicate(b'children(set)', safe=True)
def children(repo, subset, x):
    """Child changesets of changesets in set."""
    s = getset(repo, fullreposet(repo), x)
    cs = _children(repo, subset, s)
    return subset & cs


@predicate(b'closed()', safe=True, weight=10)
def closed(repo, subset, x):
    """Changeset is closed."""
    # i18n: "closed" is a keyword
    getargs(x, 0, 0, _(b"closed takes no arguments"))
    return subset.filter(
        lambda r: repo[r].closesbranch(), condrepr=b'<branch closed>'
    )


# for internal use
@predicate(b'_commonancestorheads(set)', safe=True)
def _commonancestorheads(repo, subset, x):
    # This is an internal method is for quickly calculating "heads(::x and
    # ::y)"

    # These greatest common ancestors are the same ones that the consensus bid
    # merge will find.
    startrevs = getset(repo, fullreposet(repo), x, order=anyorder)

    ancs = repo.changelog._commonancestorsheads(*list(startrevs))
    return subset & baseset(ancs)


@predicate(b'commonancestors(set)', safe=True)
def commonancestors(repo, subset, x):
    """Changesets that are ancestors of every changeset in set."""
    startrevs = getset(repo, fullreposet(repo), x, order=anyorder)
    if not startrevs:
        return baseset()
    for r in startrevs:
        subset &= dagop.revancestors(repo, baseset([r]))
    return subset


@predicate(b'conflictlocal()', safe=True)
def conflictlocal(repo, subset, x):
    """The local side of the merge, if currently in an unresolved merge.

    "merge" here includes merge conflicts from e.g. 'hg rebase' or 'hg graft'.
    """
    getargs(x, 0, 0, _(b"conflictlocal takes no arguments"))
    from . import mergestate as mergestatemod

    mergestate = mergestatemod.mergestate.read(repo)
    if mergestate.active() and repo.changelog.hasnode(mergestate.local):
        return subset & {repo.changelog.rev(mergestate.local)}

    return baseset()


@predicate(b'conflictother()', safe=True)
def conflictother(repo, subset, x):
    """The other side of the merge, if currently in an unresolved merge.

    "merge" here includes merge conflicts from e.g. 'hg rebase' or 'hg graft'.
    """
    getargs(x, 0, 0, _(b"conflictother takes no arguments"))
    from . import mergestate as mergestatemod

    mergestate = mergestatemod.mergestate.read(repo)
    if mergestate.active() and repo.changelog.hasnode(mergestate.other):
        return subset & {repo.changelog.rev(mergestate.other)}

    return baseset()


@predicate(b'contains(pattern)', weight=100)
def contains(repo, subset, x):
    """The revision's manifest contains a file matching pattern (but might not
    modify it). See :hg:`help patterns` for information about file patterns.

    The pattern without explicit kind like ``glob:`` is expected to be
    relative to the current directory and match against a file exactly
    for efficiency.
    """
    # i18n: "contains" is a keyword
    pat = getstring(x, _(b"contains requires a pattern"))

    def matches(x):
        if not matchmod.patkind(pat):
            pats = pathutil.canonpath(repo.root, repo.getcwd(), pat)
            if pats in repo[x]:
                return True
        else:
            c = repo[x]
            m = matchmod.match(repo.root, repo.getcwd(), [pat], ctx=c)
            for f in c.manifest():
                if m(f):
                    return True
        return False

    return subset.filter(matches, condrepr=(b'<contains %r>', pat))


@predicate(b'converted([id])', safe=True)
def converted(repo, subset, x):
    """Changesets converted from the given identifier in the old repository if
    present, or all converted changesets if no identifier is specified.
    """

    # There is exactly no chance of resolving the revision, so do a simple
    # string compare and hope for the best

    rev = None
    # i18n: "converted" is a keyword
    l = getargs(x, 0, 1, _(b'converted takes one or no arguments'))
    if l:
        # i18n: "converted" is a keyword
        rev = getstring(l[0], _(b'converted requires a revision'))

    def _matchvalue(r):
        source = repo[r].extra().get(b'convert_revision', None)
        return source is not None and (rev is None or source.startswith(rev))

    return subset.filter(
        lambda r: _matchvalue(r), condrepr=(b'<converted %r>', rev)
    )


@predicate(b'date(interval)', safe=True, weight=10)
def date(repo, subset, x):
    """Changesets within the interval, see :hg:`help dates`."""
    # i18n: "date" is a keyword
    ds = getstring(x, _(b"date requires a string"))
    dm = dateutil.matchdate(ds)
    return subset.filter(
        lambda x: dm(repo[x].date()[0]), condrepr=(b'<date %r>', ds)
    )


@predicate(b'desc(string)', safe=True, weight=10)
def desc(repo, subset, x):
    """Search commit message for string. The match is case-insensitive.

    Pattern matching is supported for `string`. See
    :hg:`help revisions.patterns`.
    """
    # i18n: "desc" is a keyword
    ds = getstring(x, _(b"desc requires a string"))

    kind, pattern, matcher = _substringmatcher(ds, casesensitive=False)

    return subset.filter(
        lambda r: matcher(repo[r].description()), condrepr=(b'<desc %r>', ds)
    )


def _descendants(
    repo, subset, x, followfirst=False, startdepth=None, stopdepth=None
):
    roots = getset(repo, fullreposet(repo), x)
    if not roots:
        return baseset()
    s = dagop.revdescendants(repo, roots, followfirst, startdepth, stopdepth)
    return subset & s


@predicate(b'descendants(set[, depth])', safe=True)
def descendants(repo, subset, x):
    """Changesets which are descendants of changesets in set, including the
    given changesets themselves.

    If depth is specified, the result only includes changesets up to
    the specified generation.
    """
    # startdepth is for internal use only until we can decide the UI
    args = getargsdict(x, b'descendants', b'set depth startdepth')
    if b'set' not in args:
        # i18n: "descendants" is a keyword
        raise error.ParseError(_(b'descendants takes at least 1 argument'))
    startdepth = stopdepth = None
    if b'startdepth' in args:
        n = getinteger(
            args[b'startdepth'], b"descendants expects an integer startdepth"
        )
        if n < 0:
            raise error.ParseError(b"negative startdepth")
        startdepth = n
    if b'depth' in args:
        # i18n: "descendants" is a keyword
        n = getinteger(
            args[b'depth'], _(b"descendants expects an integer depth")
        )
        if n < 0:
            raise error.ParseError(_(b"negative depth"))
        stopdepth = n + 1
    return _descendants(
        repo, subset, args[b'set'], startdepth=startdepth, stopdepth=stopdepth
    )


@predicate(b'_firstdescendants', safe=True)
def _firstdescendants(repo, subset, x):
    # ``_firstdescendants(set)``
    # Like ``descendants(set)`` but follows only the first parents.
    return _descendants(repo, subset, x, followfirst=True)


@predicate(b'destination([set])', safe=True, weight=10)
def destination(repo, subset, x):
    """Changesets that were created by a graft, transplant or rebase operation,
    with the given revisions specified as the source.  Omitting the optional set
    is the same as passing all().
    """
    if x is not None:
        sources = getset(repo, fullreposet(repo), x)
    else:
        sources = fullreposet(repo)

    dests = set()

    # subset contains all of the possible destinations that can be returned, so
    # iterate over them and see if their source(s) were provided in the arg set.
    # Even if the immediate src of r is not in the arg set, src's source (or
    # further back) may be.  Scanning back further than the immediate src allows
    # transitive transplants and rebases to yield the same results as transitive
    # grafts.
    for r in subset:
        src = _getrevsource(repo, r)
        lineage = None

        while src is not None:
            if lineage is None:
                lineage = list()

            lineage.append(r)

            # The visited lineage is a match if the current source is in the arg
            # set.  Since every candidate dest is visited by way of iterating
            # subset, any dests further back in the lineage will be tested by a
            # different iteration over subset.  Likewise, if the src was already
            # selected, the current lineage can be selected without going back
            # further.
            if src in sources or src in dests:
                dests.update(lineage)
                break

            r = src
            src = _getrevsource(repo, r)

    return subset.filter(
        dests.__contains__,
        condrepr=lambda: b'<destination %r>' % _sortedb(dests),
    )


@predicate(b'diffcontains(pattern)', weight=110)
def diffcontains(repo, subset, x):
    """Search revision differences for when the pattern was added or removed.

    The pattern may be a substring literal or a regular expression. See
    :hg:`help revisions.patterns`.
    """
    args = getargsdict(x, b'diffcontains', b'pattern')
    if b'pattern' not in args:
        # i18n: "diffcontains" is a keyword
        raise error.ParseError(_(b'diffcontains takes at least 1 argument'))

    pattern = getstring(
        args[b'pattern'], _(b'diffcontains requires a string pattern')
    )
    regexp = stringutil.substringregexp(pattern, re.M)

    # TODO: add support for file pattern and --follow. For example,
    # diffcontains(pattern[, set]) where set may be file(pattern) or
    # follow(pattern), and we'll eventually add a support for narrowing
    # files by revset?
    fmatch = matchmod.always()

    def makefilematcher(ctx):
        return fmatch

    # TODO: search in a windowed way
    searcher = grepmod.grepsearcher(repo.ui, repo, regexp, diff=True)

    def testdiff(rev):
        # consume the generator to discard revfiles/matches cache
        found = False
        for fn, ctx, pstates, states in searcher.searchfiles(
            baseset([rev]), makefilematcher
        ):
            if next(grepmod.difflinestates(pstates, states), None):
                found = True
        return found

    return subset.filter(testdiff, condrepr=(b'<diffcontains %r>', pattern))


@predicate(b'contentdivergent()', safe=True)
def contentdivergent(repo, subset, x):
    """
    Final successors of changesets with an alternative set of final
    successors. (EXPERIMENTAL)
    """
    # i18n: "contentdivergent" is a keyword
    getargs(x, 0, 0, _(b"contentdivergent takes no arguments"))
    contentdivergent = obsmod.getrevs(repo, b'contentdivergent')
    return subset & contentdivergent


@predicate(b'expectsize(set[, size])', safe=True, takeorder=True)
def expectsize(repo, subset, x, order):
    """Return the given revset if size matches the revset size.
    Abort if the revset doesn't expect given size.
    size can either be an integer range or an integer.

    For example, ``expectsize(0:1, 3:5)`` will abort as revset size is 2 and
    2 is not between 3 and 5 inclusive."""

    args = getargsdict(x, b'expectsize', b'set size')
    minsize = 0
    maxsize = len(repo) + 1
    err = b''
    if b'size' not in args or b'set' not in args:
        raise error.ParseError(_(b'invalid set of arguments'))
    minsize, maxsize = getintrange(
        args[b'size'],
        _(b'expectsize requires a size range or a positive integer'),
        _(b'size range bounds must be integers'),
        minsize,
        maxsize,
    )
    if minsize < 0 or maxsize < 0:
        raise error.ParseError(_(b'negative size'))
    rev = getset(repo, fullreposet(repo), args[b'set'], order=order)
    if minsize != maxsize and (len(rev) < minsize or len(rev) > maxsize):
        err = _(b'revset size mismatch. expected between %d and %d, got %d') % (
            minsize,
            maxsize,
            len(rev),
        )
    elif minsize == maxsize and len(rev) != minsize:
        err = _(b'revset size mismatch. expected %d, got %d') % (
            minsize,
            len(rev),
        )
    if err:
        raise error.RepoLookupError(err)
    if order == followorder:
        return subset & rev
    else:
        return rev & subset


@predicate(b'extdata(source)', safe=False, weight=100)
def extdata(repo, subset, x):
    """Changesets in the specified extdata source. (EXPERIMENTAL)"""
    # i18n: "extdata" is a keyword
    args = getargsdict(x, b'extdata', b'source')
    source = getstring(
        args.get(b'source'),
        # i18n: "extdata" is a keyword
        _(b'extdata takes at least 1 string argument'),
    )
    data = scmutil.extdatasource(repo, source)
    return subset & baseset(data)


@predicate(b'extinct()', safe=True)
def extinct(repo, subset, x):
    """Obsolete changesets with obsolete descendants only. (EXPERIMENTAL)"""
    # i18n: "extinct" is a keyword
    getargs(x, 0, 0, _(b"extinct takes no arguments"))
    extincts = obsmod.getrevs(repo, b'extinct')
    return subset & extincts


@predicate(b'extra(label, [value])', safe=True)
def extra(repo, subset, x):
    """Changesets with the given label in the extra metadata, with the given
    optional value.

    Pattern matching is supported for `value`. See
    :hg:`help revisions.patterns`.
    """
    args = getargsdict(x, b'extra', b'label value')
    if b'label' not in args:
        # i18n: "extra" is a keyword
        raise error.ParseError(_(b'extra takes at least 1 argument'))
    # i18n: "extra" is a keyword
    label = getstring(
        args[b'label'], _(b'first argument to extra must be a string')
    )
    value = None

    if b'value' in args:
        # i18n: "extra" is a keyword
        value = getstring(
            args[b'value'], _(b'second argument to extra must be a string')
        )
        kind, value, matcher = stringutil.stringmatcher(value)

    def _matchvalue(r):
        extra = repo[r].extra()
        return label in extra and (value is None or matcher(extra[label]))

    return subset.filter(
        lambda r: _matchvalue(r), condrepr=(b'<extra[%r] %r>', label, value)
    )


@predicate(b'filelog(pattern)', safe=True)
def filelog(repo, subset, x):
    """Changesets connected to the specified filelog.

    For performance reasons, visits only revisions mentioned in the file-level
    filelog, rather than filtering through all changesets (much faster, but
    doesn't include deletes or duplicate changes). For a slower, more accurate
    result, use ``file()``.

    The pattern without explicit kind like ``glob:`` is expected to be
    relative to the current directory and match against a file exactly
    for efficiency.
    """

    # i18n: "filelog" is a keyword
    pat = getstring(x, _(b"filelog requires a pattern"))
    s = set()
    cl = repo.changelog

    if not matchmod.patkind(pat):
        f = pathutil.canonpath(repo.root, repo.getcwd(), pat)
        files = [f]
    else:
        m = matchmod.match(repo.root, repo.getcwd(), [pat], ctx=repo[None])
        files = (f for f in repo[None] if m(f))

    for f in files:
        fl = repo.file(f)
        known = {}
        scanpos = 0
        for fr in list(fl):
            fn = fl.node(fr)
            if fn in known:
                s.add(known[fn])
                continue

            lr = fl.linkrev(fr)
            if lr in cl:
                s.add(lr)
            elif scanpos is not None:
                # lowest matching changeset is filtered, scan further
                # ahead in changelog
                start = max(lr, scanpos) + 1
                scanpos = None
                for r in cl.revs(start):
                    # minimize parsing of non-matching entries
                    if f in cl.revision(r) and f in cl.readfiles(r):
                        try:
                            # try to use manifest delta fastpath
                            n = repo[r].filenode(f)
                            if n not in known:
                                if n == fn:
                                    s.add(r)
                                    scanpos = r
                                    break
                                else:
                                    known[n] = r
                        except error.ManifestLookupError:
                            # deletion in changelog
                            continue

    return subset & s


@predicate(b'first(set, [n])', safe=True, takeorder=True, weight=0)
def first(repo, subset, x, order):
    """An alias for limit()."""
    return limit(repo, subset, x, order)


def _follow(repo, subset, x, name, followfirst=False):
    args = getargsdict(x, name, b'file startrev')
    revs = None
    if b'startrev' in args:
        revs = getset(repo, fullreposet(repo), args[b'startrev'])
    if b'file' in args:
        x = getstring(args[b'file'], _(b"%s expected a pattern") % name)
        if revs is None:
            revs = [None]
        fctxs = []
        for r in revs:
            ctx = mctx = repo[r]
            if r is None:
                ctx = repo[b'.']
            m = matchmod.match(
                repo.root, repo.getcwd(), [x], ctx=mctx, default=b'path'
            )
            fctxs.extend(ctx[f].introfilectx() for f in ctx.manifest().walk(m))
        s = dagop.filerevancestors(fctxs, followfirst)
    else:
        if revs is None:
            revs = baseset([repo[b'.'].rev()])
        s = dagop.revancestors(repo, revs, followfirst)

    return subset & s


@predicate(b'follow([file[, startrev]])', safe=True)
def follow(repo, subset, x):
    """
    An alias for ``::.`` (ancestors of the working directory's first parent).
    If file pattern is specified, the histories of files matching given
    pattern in the revision given by startrev are followed, including copies.
    """
    return _follow(repo, subset, x, b'follow')


@predicate(b'_followfirst', safe=True)
def _followfirst(repo, subset, x):
    # ``followfirst([file[, startrev]])``
    # Like ``follow([file[, startrev]])`` but follows only the first parent
    # of every revisions or files revisions.
    return _follow(repo, subset, x, b'_followfirst', followfirst=True)


@predicate(
    b'followlines(file, fromline:toline[, startrev=., descend=False])',
    safe=True,
)
def followlines(repo, subset, x):
    """Changesets modifying `file` in line range ('fromline', 'toline').

    Line range corresponds to 'file' content at 'startrev' and should hence be
    consistent with file size. If startrev is not specified, working directory's
    parent is used.

    By default, ancestors of 'startrev' are returned. If 'descend' is True,
    descendants of 'startrev' are returned though renames are (currently) not
    followed in this direction.
    """
    args = getargsdict(x, b'followlines', b'file *lines startrev descend')
    if len(args[b'lines']) != 1:
        raise error.ParseError(_(b"followlines requires a line range"))

    rev = b'.'
    if b'startrev' in args:
        revs = getset(repo, fullreposet(repo), args[b'startrev'])
        if len(revs) != 1:
            raise error.ParseError(
                # i18n: "followlines" is a keyword
                _(b"followlines expects exactly one revision")
            )
        rev = revs.last()

    pat = getstring(args[b'file'], _(b"followlines requires a pattern"))
    # i18n: "followlines" is a keyword
    msg = _(b"followlines expects exactly one file")
    fname = scmutil.parsefollowlinespattern(repo, rev, pat, msg)
    fromline, toline = util.processlinerange(
        *getintrange(
            args[b'lines'][0],
            # i18n: "followlines" is a keyword
            _(b"followlines expects a line number or a range"),
            _(b"line range bounds must be integers"),
        )
    )

    fctx = repo[rev].filectx(fname)
    descend = False
    if b'descend' in args:
        descend = getboolean(
            args[b'descend'],
            # i18n: "descend" is a keyword
            _(b"descend argument must be a boolean"),
        )
    if descend:
        rs = generatorset(
            (
                c.rev()
                for c, _linerange in dagop.blockdescendants(
                    fctx, fromline, toline
                )
            ),
            iterasc=True,
        )
    else:
        rs = generatorset(
            (
                c.rev()
                for c, _linerange in dagop.blockancestors(
                    fctx, fromline, toline
                )
            ),
            iterasc=False,
        )
    return subset & rs


@predicate(b'nodefromfile(path)')
def nodefromfile(repo, subset, x):
    """Read a list of nodes from the file at `path`.

    This applies `id(LINE)` to each line of the file.

    This is useful when the amount of nodes you need to specify gets too large
    for the command line.
    """
    path = getstring(x, _(b"nodefromfile require a file path"))
    listed_rev = set()
    try:
        with pycompat.open(path, 'rb') as f:
            for line in f:
                n = line.strip()
                rn = _node(repo, n)
                if rn is not None:
                    listed_rev.add(rn)
    except IOError as exc:
        m = _(b'cannot open nodes file "%s": %s')
        m %= (path, encoding.strtolocal(exc.strerror))
        raise error.Abort(m)
    return subset & baseset(listed_rev)


@predicate(b'all()', safe=True)
def getall(repo, subset, x):
    """All changesets, the same as ``0:tip``."""
    # i18n: "all" is a keyword
    getargs(x, 0, 0, _(b"all takes no arguments"))
    return subset & spanset(repo)  # drop "null" if any


@predicate(b'grep(regex)', weight=10)
def grep(repo, subset, x):
    """Like ``keyword(string)`` but accepts a regex. Use ``grep(r'...')``
    to ensure special escape characters are handled correctly. Unlike
    ``keyword(string)``, the match is case-sensitive.
    """
    try:
        # i18n: "grep" is a keyword
        gr = re.compile(getstring(x, _(b"grep requires a string")))
    except re.error as e:
        raise error.ParseError(
            _(b'invalid match pattern: %s') % stringutil.forcebytestr(e)
        )

    def matches(x):
        c = repo[x]
        for e in c.files() + [c.user(), c.description()]:
            if gr.search(e):
                return True
        return False

    return subset.filter(matches, condrepr=(b'<grep %r>', gr.pattern))


@predicate(b'_matchfiles', safe=True)
def _matchfiles(repo, subset, x):
    # _matchfiles takes a revset list of prefixed arguments:
    #
    #   [p:foo, i:bar, x:baz]
    #
    # builds a match object from them and filters subset. Allowed
    # prefixes are 'p:' for regular patterns, 'i:' for include
    # patterns and 'x:' for exclude patterns. Use 'r:' prefix to pass
    # a revision identifier, or the empty string to reference the
    # working directory, from which the match object is
    # initialized. Use 'd:' to set the default matching mode, default
    # to 'glob'. At most one 'r:' and 'd:' argument can be passed.

    l = getargs(x, 1, -1, b"_matchfiles requires at least one argument")
    pats, inc, exc = [], [], []
    rev, default = None, None
    for arg in l:
        s = getstring(arg, b"_matchfiles requires string arguments")
        prefix, value = s[:2], s[2:]
        if prefix == b'p:':
            pats.append(value)
        elif prefix == b'i:':
            inc.append(value)
        elif prefix == b'x:':
            exc.append(value)
        elif prefix == b'r:':
            if rev is not None:
                raise error.ParseError(
                    b'_matchfiles expected at most one revision'
                )
            if value == b'':  # empty means working directory
                rev = wdirrev
            else:
                rev = value
        elif prefix == b'd:':
            if default is not None:
                raise error.ParseError(
                    b'_matchfiles expected at most one default mode'
                )
            default = value
        else:
            raise error.ParseError(b'invalid _matchfiles prefix: %s' % prefix)
    if not default:
        default = b'glob'
    hasset = any(matchmod.patkind(p) == b'set' for p in pats + inc + exc)

    mcache = [None]

    # This directly read the changelog data as creating changectx for all
    # revisions is quite expensive.
    getfiles = repo.changelog.readfiles

    def matches(x):
        if x == wdirrev:
            files = repo[x].files()
        else:
            files = getfiles(x)

        if not mcache[0] or (hasset and rev is None):
            r = x if rev is None else rev
            mcache[0] = matchmod.match(
                repo.root,
                repo.getcwd(),
                pats,
                include=inc,
                exclude=exc,
                ctx=repo[r],
                default=default,
            )
        m = mcache[0]

        for f in files:
            if m(f):
                return True
        return False

    return subset.filter(
        matches,
        condrepr=(
            b'<matchfiles patterns=%r, include=%r '
            b'exclude=%r, default=%r, rev=%r>',
            pats,
            inc,
            exc,
            default,
            rev,
        ),
    )


@predicate(b'file(pattern)', safe=True, weight=10)
def hasfile(repo, subset, x):
    """Changesets affecting files matched by pattern.

    For a faster but less accurate result, consider using ``filelog()``
    instead.

    This predicate uses ``glob:`` as the default kind of pattern.
    """
    # i18n: "file" is a keyword
    pat = getstring(x, _(b"file requires a pattern"))
    return _matchfiles(repo, subset, (b'string', b'p:' + pat))


@predicate(b'head()', safe=True)
def head(repo, subset, x):
    """Changeset is a named branch head."""
    # i18n: "head" is a keyword
    getargs(x, 0, 0, _(b"head takes no arguments"))
    hs = set()
    cl = repo.changelog
    for ls in repo.branchmap().iterheads():
        hs.update(cl.rev(h) for h in ls)
    return subset & baseset(hs)


@predicate(b'heads(set)', safe=True, takeorder=True)
def heads(repo, subset, x, order):
    """Members of set with no children in set."""
    # argument set should never define order
    if order == defineorder:
        order = followorder
    inputset = getset(repo, fullreposet(repo), x, order=order)
    wdirparents = None
    if wdirrev in inputset:
        # a bit slower, but not common so good enough for now
        wdirparents = [p.rev() for p in repo[None].parents()]
        inputset = set(inputset)
        inputset.discard(wdirrev)
    heads = repo.changelog.headrevs(inputset)
    if wdirparents is not None:
        heads.difference_update(wdirparents)
        heads.add(wdirrev)
    heads = baseset(heads)
    return subset & heads


@predicate(b'hidden()', safe=True)
def hidden(repo, subset, x):
    """Hidden changesets."""
    # i18n: "hidden" is a keyword
    getargs(x, 0, 0, _(b"hidden takes no arguments"))
    hiddenrevs = repoview.filterrevs(repo, b'visible')
    return subset & hiddenrevs


@predicate(b'keyword(string)', safe=True, weight=10)
def keyword(repo, subset, x):
    """Search commit message, user name, and names of changed files for
    string. The match is case-insensitive.

    For a regular expression or case sensitive search of these fields, use
    ``grep(regex)``.
    """
    # i18n: "keyword" is a keyword
    kw = encoding.lower(getstring(x, _(b"keyword requires a string")))

    def matches(r):
        c = repo[r]
        return any(
            kw in encoding.lower(t)
            for t in c.files() + [c.user(), c.description()]
        )

    return subset.filter(matches, condrepr=(b'<keyword %r>', kw))


@predicate(b'limit(set[, n[, offset]])', safe=True, takeorder=True, weight=0)
def limit(repo, subset, x, order):
    """First n members of set, defaulting to 1, starting from offset."""
    args = getargsdict(x, b'limit', b'set n offset')
    if b'set' not in args:
        # i18n: "limit" is a keyword
        raise error.ParseError(_(b"limit requires one to three arguments"))
    # i18n: "limit" is a keyword
    lim = getinteger(args.get(b'n'), _(b"limit expects a number"), default=1)
    if lim < 0:
        raise error.ParseError(_(b"negative number to select"))
    # i18n: "limit" is a keyword
    ofs = getinteger(
        args.get(b'offset'), _(b"limit expects a number"), default=0
    )
    if ofs < 0:
        raise error.ParseError(_(b"negative offset"))
    os = getset(repo, fullreposet(repo), args[b'set'])
    ls = os.slice(ofs, ofs + lim)
    if order == followorder and lim > 1:
        return subset & ls
    return ls & subset


@predicate(b'last(set, [n])', safe=True, takeorder=True)
def last(repo, subset, x, order):
    """Last n members of set, defaulting to 1."""
    # i18n: "last" is a keyword
    l = getargs(x, 1, 2, _(b"last requires one or two arguments"))
    lim = 1
    if len(l) == 2:
        # i18n: "last" is a keyword
        lim = getinteger(l[1], _(b"last expects a number"))
    if lim < 0:
        raise error.ParseError(_(b"negative number to select"))
    os = getset(repo, fullreposet(repo), l[0])
    os.reverse()
    ls = os.slice(0, lim)
    if order == followorder and lim > 1:
        return subset & ls
    ls.reverse()
    return ls & subset


@predicate(b'max(set)', safe=True)
def maxrev(repo, subset, x):
    """Changeset with highest revision number in set."""
    os = getset(repo, fullreposet(repo), x)
    try:
        m = os.max()
        if m in subset:
            return baseset([m], datarepr=(b'<max %r, %r>', subset, os))
    except ValueError:
        # os.max() throws a ValueError when the collection is empty.
        # Same as python's max().
        pass
    return baseset(datarepr=(b'<max %r, %r>', subset, os))


@predicate(b'merge()', safe=True)
def merge(repo, subset, x):
    """Changeset is a merge changeset."""
    # i18n: "merge" is a keyword
    getargs(x, 0, 0, _(b"merge takes no arguments"))
    cl = repo.changelog

    def ismerge(r):
        try:
            return cl.parentrevs(r)[1] != nullrev
        except error.WdirUnsupported:
            return bool(repo[r].p2())

    return subset.filter(ismerge, condrepr=b'<merge>')


@predicate(b'branchpoint()', safe=True)
def branchpoint(repo, subset, x):
    """Changesets with more than one child."""
    # i18n: "branchpoint" is a keyword
    getargs(x, 0, 0, _(b"branchpoint takes no arguments"))
    cl = repo.changelog
    if not subset:
        return baseset()
    # XXX this should be 'parentset.min()' assuming 'parentset' is a smartset
    # (and if it is not, it should.)
    baserev = min(subset)
    parentscount = [0] * (len(repo) - baserev)
    for r in cl.revs(start=baserev + 1):
        for p in cl.parentrevs(r):
            if p >= baserev:
                parentscount[p - baserev] += 1
    return subset.filter(
        lambda r: parentscount[r - baserev] > 1, condrepr=b'<branchpoint>'
    )


@predicate(b'min(set)', safe=True)
def minrev(repo, subset, x):
    """Changeset with lowest revision number in set."""
    os = getset(repo, fullreposet(repo), x)
    try:
        m = os.min()
        if m in subset:
            return baseset([m], datarepr=(b'<min %r, %r>', subset, os))
    except ValueError:
        # os.min() throws a ValueError when the collection is empty.
        # Same as python's min().
        pass
    return baseset(datarepr=(b'<min %r, %r>', subset, os))


@predicate(b'modifies(pattern)', safe=True, weight=30)
def modifies(repo, subset, x):
    """Changesets modifying files matched by pattern.

    The pattern without explicit kind like ``glob:`` is expected to be
    relative to the current directory and match against a file or a
    directory.
    """
    # i18n: "modifies" is a keyword
    pat = getstring(x, _(b"modifies requires a pattern"))
    return checkstatus(repo, subset, pat, 'modified')


@predicate(b'named(namespace)')
def named(repo, subset, x):
    """The changesets in a given namespace.

    Pattern matching is supported for `namespace`. See
    :hg:`help revisions.patterns`.
    """
    # i18n: "named" is a keyword
    args = getargs(x, 1, 1, _(b'named requires a namespace argument'))

    ns = getstring(
        args[0],
        # i18n: "named" is a keyword
        _(b'the argument to named must be a string'),
    )
    kind, pattern, matcher = stringutil.stringmatcher(ns)
    namespaces = set()
    if kind == b'literal':
        if pattern not in repo.names:
            raise error.RepoLookupError(
                _(b"namespace '%s' does not exist") % ns
            )
        namespaces.add(repo.names[pattern])
    else:
        for name, ns in repo.names.items():
            if matcher(name):
                namespaces.add(ns)

    names = set()
    for ns in namespaces:
        for name in ns.listnames(repo):
            if name not in ns.deprecated:
                names.update(repo[n].rev() for n in ns.nodes(repo, name))

    names -= {nullrev}
    return subset & names


def _node(repo, n):
    """process a node input"""
    rn = None
    if len(n) == 2 * repo.nodeconstants.nodelen:
        try:
            rn = repo.changelog.rev(bin(n))
        except error.WdirUnsupported:
            rn = wdirrev
        except (binascii.Error, LookupError):
            rn = None
    else:
        try:
            pm = scmutil.resolvehexnodeidprefix(repo, n)
            if pm is not None:
                rn = repo.changelog.rev(pm)
        except LookupError:
            pass
        except error.WdirUnsupported:
            rn = wdirrev
    return rn


@predicate(b'id(string)', safe=True)
def node_(repo, subset, x):
    """Revision non-ambiguously specified by the given hex string prefix."""
    # i18n: "id" is a keyword
    l = getargs(x, 1, 1, _(b"id requires one argument"))
    # i18n: "id" is a keyword
    n = getstring(l[0], _(b"id requires a string"))
    rn = _node(repo, n)

    if rn is None:
        return baseset()
    result = baseset([rn])
    return result & subset


@predicate(b'none()', safe=True)
def none(repo, subset, x):
    """No changesets."""
    # i18n: "none" is a keyword
    getargs(x, 0, 0, _(b"none takes no arguments"))
    return baseset()


@predicate(b'obsolete()', safe=True)
def obsolete(repo, subset, x):
    """Mutable changeset with a newer version. (EXPERIMENTAL)"""
    # i18n: "obsolete" is a keyword
    getargs(x, 0, 0, _(b"obsolete takes no arguments"))
    obsoletes = obsmod.getrevs(repo, b'obsolete')
    return subset & obsoletes


@predicate(b'only(set, [set])', safe=True)
def only(repo, subset, x):
    """Changesets that are ancestors of the first set that are not ancestors
    of any other head in the repo. If a second set is specified, the result
    is ancestors of the first set that are not ancestors of the second set
    (i.e. ::<set1> - ::<set2>).
    """
    cl = repo.changelog
    # i18n: "only" is a keyword
    args = getargs(x, 1, 2, _(b'only takes one or two arguments'))
    include = getset(repo, fullreposet(repo), args[0])
    if len(args) == 1:
        if not include:
            return baseset()

        descendants = set(dagop.revdescendants(repo, include, False))
        exclude = [
            rev
            for rev in cl.headrevs()
            if not rev in descendants and not rev in include
        ]
    else:
        exclude = getset(repo, fullreposet(repo), args[1])

    results = set(cl.findmissingrevs(common=exclude, heads=include))
    # XXX we should turn this into a baseset instead of a set, smartset may do
    # some optimizations from the fact this is a baseset.
    return subset & results


@predicate(b'origin([set])', safe=True)
def origin(repo, subset, x):
    """
    Changesets that were specified as a source for the grafts, transplants or
    rebases that created the given revisions.  Omitting the optional set is the
    same as passing all().  If a changeset created by these operations is itself
    specified as a source for one of these operations, only the source changeset
    for the first operation is selected.
    """
    if x is not None:
        dests = getset(repo, fullreposet(repo), x)
    else:
        dests = fullreposet(repo)

    def _firstsrc(rev):
        src = _getrevsource(repo, rev)
        if src is None:
            return None

        while True:
            prev = _getrevsource(repo, src)

            if prev is None:
                return src
            src = prev

    o = {_firstsrc(r) for r in dests}
    o -= {None}
    # XXX we should turn this into a baseset instead of a set, smartset may do
    # some optimizations from the fact this is a baseset.
    return subset & o


@predicate(b'outgoing([path])', safe=False, weight=10)
def outgoing(repo, subset, x):
    """Changesets not found in the specified destination repository, or the
    default push location.

    If the location resolve to multiple repositories, the union of all
    outgoing changeset will be used.
    """
    # Avoid cycles.
    from . import (
        discovery,
        hg,
    )

    # i18n: "outgoing" is a keyword
    l = getargs(x, 0, 1, _(b"outgoing takes one or no arguments"))
    # i18n: "outgoing" is a keyword
    dest = (
        l and getstring(l[0], _(b"outgoing requires a repository path")) or b''
    )
    if dest:
        dests = [dest]
    else:
        dests = []
    missing = set()
    for path in urlutil.get_push_paths(repo, repo.ui, dests):
        branches = path.branch, []

        revs, checkout = hg.addbranchrevs(repo, repo, branches, [])
        if revs:
            revs = [repo.lookup(rev) for rev in revs]
        other = hg.peer(repo, {}, path)
        try:
            with repo.ui.silent():
                outgoing = discovery.findcommonoutgoing(
                    repo, other, onlyheads=revs
                )
        finally:
            other.close()
        missing.update(outgoing.missing)
    cl = repo.changelog
    o = {cl.rev(r) for r in missing}
    return subset & o


@predicate(b'p1([set])', safe=True)
def p1(repo, subset, x):
    """First parent of changesets in set, or the working directory."""
    if x is None:
        p = repo[x].p1().rev()
        if p >= 0:
            return subset & baseset([p])
        return baseset()

    ps = set()
    cl = repo.changelog
    for r in getset(repo, fullreposet(repo), x):
        try:
            ps.add(cl.parentrevs(r)[0])
        except error.WdirUnsupported:
            ps.add(repo[r].p1().rev())
    ps -= {nullrev}
    # XXX we should turn this into a baseset instead of a set, smartset may do
    # some optimizations from the fact this is a baseset.
    return subset & ps


@predicate(b'p2([set])', safe=True)
def p2(repo, subset, x):
    """Second parent of changesets in set, or the working directory."""
    if x is None:
        ps = repo[x].parents()
        try:
            p = ps[1].rev()
            if p >= 0:
                return subset & baseset([p])
            return baseset()
        except IndexError:
            return baseset()

    ps = set()
    cl = repo.changelog
    for r in getset(repo, fullreposet(repo), x):
        try:
            ps.add(cl.parentrevs(r)[1])
        except error.WdirUnsupported:
            parents = repo[r].parents()
            if len(parents) == 2:
                ps.add(parents[1])
    ps -= {nullrev}
    # XXX we should turn this into a baseset instead of a set, smartset may do
    # some optimizations from the fact this is a baseset.
    return subset & ps


def parentpost(repo, subset, x, order):
    return p1(repo, subset, x)


@predicate(b'parents([set])', safe=True)
def parents(repo, subset, x):
    """
    The set of all parents for all changesets in set, or the working directory.
    """
    if x is None:
        ps = {p.rev() for p in repo[x].parents()}
    else:
        ps = set()
        cl = repo.changelog
        up = ps.update
        parentrevs = cl.parentrevs
        for r in getset(repo, fullreposet(repo), x):
            try:
                up(parentrevs(r))
            except error.WdirUnsupported:
                up(p.rev() for p in repo[r].parents())
    ps -= {nullrev}
    return subset & ps


def _phase(repo, subset, *targets):
    """helper to select all rev in <targets> phases"""
    return repo._phasecache.getrevset(repo, targets, subset)


@predicate(b'_internal()', safe=True)
def _internal(repo, subset, x):
    getargs(x, 0, 0, _(b"_internal takes no arguments"))
    return _phase(repo, subset, *phases.all_internal_phases)


@predicate(b'_phase(idx)', safe=True)
def phase(repo, subset, x):
    l = getargs(x, 1, 1, b"_phase requires one argument")
    target = getinteger(l[0], b"_phase expects a number")
    return _phase(repo, subset, target)


@predicate(b'draft()', safe=True)
def draft(repo, subset, x):
    """Changeset in draft phase."""
    # i18n: "draft" is a keyword
    getargs(x, 0, 0, _(b"draft takes no arguments"))
    target = phases.draft
    return _phase(repo, subset, target)


@predicate(b'secret()', safe=True)
def secret(repo, subset, x):
    """Changeset in secret phase."""
    # i18n: "secret" is a keyword
    getargs(x, 0, 0, _(b"secret takes no arguments"))
    target = phases.secret
    return _phase(repo, subset, target)


@predicate(b'stack([revs])', safe=True)
def stack(repo, subset, x):
    """Experimental revset for the stack of changesets or working directory
    parent. (EXPERIMENTAL)
    """
    if x is None:
        stacks = stackmod.getstack(repo)
    else:
        stacks = smartset.baseset([])
        for revision in getset(repo, fullreposet(repo), x):
            currentstack = stackmod.getstack(repo, revision)
            stacks = stacks + currentstack

    return subset & stacks


def parentspec(repo, subset, x, n, order):
    """``set^0``
    The set.
    ``set^1`` (or ``set^``), ``set^2``
    First or second parent, respectively, of all changesets in set.
    """
    try:
        n = int(n[1])
        if n not in (0, 1, 2):
            raise ValueError
    except (TypeError, ValueError):
        raise error.ParseError(_(b"^ expects a number 0, 1, or 2"))
    ps = set()
    cl = repo.changelog
    for r in getset(repo, fullreposet(repo), x):
        if n == 0:
            ps.add(r)
        elif n == 1:
            try:
                ps.add(cl.parentrevs(r)[0])
            except error.WdirUnsupported:
                ps.add(repo[r].p1().rev())
        else:
            try:
                parents = cl.parentrevs(r)
                if parents[1] != nullrev:
                    ps.add(parents[1])
            except error.WdirUnsupported:
                parents = repo[r].parents()
                if len(parents) == 2:
                    ps.add(parents[1].rev())
    return subset & ps


@predicate(b'present(set)', safe=True, takeorder=True)
def present(repo, subset, x, order):
    """An empty set, if any revision in set isn't found; otherwise,
    all revisions in set.

    If any of specified revisions is not present in the local repository,
    the query is normally aborted. But this predicate allows the query
    to continue even in such cases.
    """
    try:
        return getset(repo, subset, x, order)
    except error.RepoLookupError:
        return baseset()


# for internal use
@predicate(b'_notpublic', safe=True)
def _notpublic(repo, subset, x):
    getargs(x, 0, 0, b"_notpublic takes no arguments")
    return _phase(repo, subset, *phases.not_public_phases)


# for internal use
@predicate(b'_phaseandancestors(phasename, set)', safe=True)
def _phaseandancestors(repo, subset, x):
    # equivalent to (phasename() & ancestors(set)) but more efficient
    # phasename could be one of 'draft', 'secret', or '_notpublic'
    args = getargs(x, 2, 2, b"_phaseandancestors requires two arguments")
    phasename = getsymbol(args[0])
    s = getset(repo, fullreposet(repo), args[1])

    draft = phases.draft
    secret = phases.secret
    phasenamemap = {
        b'_notpublic': draft,
        b'draft': draft,  # follow secret's ancestors
        b'secret': secret,
    }
    if phasename not in phasenamemap:
        raise error.ParseError(b'%r is not a valid phasename' % phasename)

    minimalphase = phasenamemap[phasename]
    getphase = repo._phasecache.phase

    def cutfunc(rev):
        return getphase(repo, rev) < minimalphase

    revs = dagop.revancestors(repo, s, cutfunc=cutfunc)

    if phasename == b'draft':  # need to remove secret changesets
        revs = revs.filter(lambda r: getphase(repo, r) == draft)
    return subset & revs


@predicate(b'public()', safe=True)
def public(repo, subset, x):
    """Changeset in public phase."""
    # i18n: "public" is a keyword
    getargs(x, 0, 0, _(b"public takes no arguments"))
    return _phase(repo, subset, phases.public)


@predicate(b'remote([id [,path]])', safe=False)
def remote(repo, subset, x):
    """Local revision that corresponds to the given identifier in a
    remote repository, if present. Here, the '.' identifier is a
    synonym for the current local branch.
    """

    from . import hg  # avoid start-up nasties

    # i18n: "remote" is a keyword
    l = getargs(x, 0, 2, _(b"remote takes zero, one, or two arguments"))

    q = b'.'
    if len(l) > 0:
        # i18n: "remote" is a keyword
        q = getstring(l[0], _(b"remote requires a string id"))
    if q == b'.':
        q = repo[b'.'].branch()

    dest = b''
    if len(l) > 1:
        # i18n: "remote" is a keyword
        dest = getstring(l[1], _(b"remote requires a repository path"))
    if not dest:
        dest = b'default'
    path = urlutil.get_unique_pull_path_obj(b'remote', repo.ui, dest)

    other = hg.peer(repo, {}, path)
    n = other.lookup(q)
    if n in repo:
        r = repo[n].rev()
        if r in subset:
            return baseset([r])
    return baseset()


@predicate(b'removes(pattern)', safe=True, weight=30)
def removes(repo, subset, x):
    """Changesets which remove files matching pattern.

    The pattern without explicit kind like ``glob:`` is expected to be
    relative to the current directory and match against a file or a
    directory.
    """
    # i18n: "removes" is a keyword
    pat = getstring(x, _(b"removes requires a pattern"))
    return checkstatus(repo, subset, pat, 'removed')


@predicate(b'rev(number)', safe=True)
def rev(repo, subset, x):
    """Revision with the given numeric identifier."""
    try:
        return _rev(repo, subset, x)
    except error.RepoLookupError:
        return baseset()


@predicate(b'_rev(number)', safe=True)
def _rev(repo, subset, x):
    # internal version of "rev(x)" that raise error if "x" is invalid
    # i18n: "rev" is a keyword
    l = getargs(x, 1, 1, _(b"rev requires one argument"))
    try:
        # i18n: "rev" is a keyword
        l = int(getstring(l[0], _(b"rev requires a number")))
    except (TypeError, ValueError):
        # i18n: "rev" is a keyword
        raise error.ParseError(_(b"rev expects a number"))
    if l not in _virtualrevs:
        try:
            repo.changelog.node(l)  # check that the rev exists
        except IndexError:
            raise error.RepoLookupError(_(b"unknown revision '%d'") % l)
    return subset & baseset([l])


@predicate(b'revset(set)', safe=True, takeorder=True)
def revsetpredicate(repo, subset, x, order):
    """Strictly interpret the content as a revset.

    The content of this special predicate will be strictly interpreted as a
    revset. For example, ``revset(id(0))`` will be interpreted as "id(0)"
    without possible ambiguity with a "id(0)" bookmark or tag.
    """
    return getset(repo, subset, x, order)


@predicate(b'matching(revision [, field])', safe=True)
def matching(repo, subset, x):
    """Changesets in which a given set of fields match the set of fields in the
    selected revision or set.

    To match more than one field pass the list of fields to match separated
    by spaces (e.g. ``author description``).

    Valid fields are most regular revision fields and some special fields.

    Regular revision fields are ``description``, ``author``, ``branch``,
    ``date``, ``files``, ``phase``, ``parents``, ``substate``, ``user``
    and ``diff``.
    Note that ``author`` and ``user`` are synonyms. ``diff`` refers to the
    contents of the revision. Two revisions matching their ``diff`` will
    also match their ``files``.

    Special fields are ``summary`` and ``metadata``:
    ``summary`` matches the first line of the description.
    ``metadata`` is equivalent to matching ``description user date``
    (i.e. it matches the main metadata fields).

    ``metadata`` is the default field which is used when no fields are
    specified. You can match more than one field at a time.
    """
    # i18n: "matching" is a keyword
    l = getargs(x, 1, 2, _(b"matching takes 1 or 2 arguments"))

    revs = getset(repo, fullreposet(repo), l[0])

    fieldlist = [b'metadata']
    if len(l) > 1:
        fieldlist = getstring(
            l[1],
            # i18n: "matching" is a keyword
            _(b"matching requires a string as its second argument"),
        ).split()

    # Make sure that there are no repeated fields,
    # expand the 'special' 'metadata' field type
    # and check the 'files' whenever we check the 'diff'
    fields = []
    for field in fieldlist:
        if field == b'metadata':
            fields += [b'user', b'description', b'date']
        elif field == b'diff':
            # a revision matching the diff must also match the files
            # since matching the diff is very costly, make sure to
            # also match the files first
            fields += [b'files', b'diff']
        else:
            if field == b'author':
                field = b'user'
            fields.append(field)
    fields = set(fields)
    if b'summary' in fields and b'description' in fields:
        # If a revision matches its description it also matches its summary
        fields.discard(b'summary')

    # We may want to match more than one field
    # Not all fields take the same amount of time to be matched
    # Sort the selected fields in order of increasing matching cost
    fieldorder = [
        b'phase',
        b'parents',
        b'user',
        b'date',
        b'branch',
        b'summary',
        b'files',
        b'description',
        b'substate',
        b'diff',
    ]

    def fieldkeyfunc(f):
        try:
            return fieldorder.index(f)
        except ValueError:
            # assume an unknown field is very costly
            return len(fieldorder)

    fields = list(fields)
    fields.sort(key=fieldkeyfunc)

    # Each field will be matched with its own "getfield" function
    # which will be added to the getfieldfuncs array of functions
    getfieldfuncs = []
    _funcs = {
        b'user': lambda r: repo[r].user(),
        b'branch': lambda r: repo[r].branch(),
        b'date': lambda r: repo[r].date(),
        b'description': lambda r: repo[r].description(),
        b'files': lambda r: repo[r].files(),
        b'parents': lambda r: repo[r].parents(),
        b'phase': lambda r: repo[r].phase(),
        b'substate': lambda r: repo[r].substate,
        b'summary': lambda r: repo[r].description().splitlines()[0],
        b'diff': lambda r: list(
            repo[r].diff(opts=diffutil.diffallopts(repo.ui, {b'git': True}))
        ),
    }
    for info in fields:
        getfield = _funcs.get(info, None)
        if getfield is None:
            raise error.ParseError(
                # i18n: "matching" is a keyword
                _(b"unexpected field name passed to matching: %s")
                % info
            )
        getfieldfuncs.append(getfield)
    # convert the getfield array of functions into a "getinfo" function
    # which returns an array of field values (or a single value if there
    # is only one field to match)
    getinfo = lambda r: [f(r) for f in getfieldfuncs]

    def matches(x):
        for rev in revs:
            target = getinfo(rev)
            match = True
            for n, f in enumerate(getfieldfuncs):
                if target[n] != f(x):
                    match = False
            if match:
                return True
        return False

    return subset.filter(matches, condrepr=(b'<matching%r %r>', fields, revs))


@predicate(b'reverse(set)', safe=True, takeorder=True, weight=0)
def reverse(repo, subset, x, order):
    """Reverse order of set."""
    l = getset(repo, subset, x, order)
    if order == defineorder:
        l.reverse()
    return l


@predicate(b'roots(set)', safe=True)
def roots(repo, subset, x):
    """Changesets in set with no parent changeset in set."""
    s = getset(repo, fullreposet(repo), x)
    parents = repo.changelog.parentrevs

    def filter(r):
        try:
            for p in parents(r):
                if 0 <= p and p in s:
                    return False
        except error.WdirUnsupported:
            for p in repo[None].parents():
                if p.rev() in s:
                    return False
        return True

    return subset & s.filter(filter, condrepr=b'<roots>')


MAXINT = (1 << 31) - 1
MININT = -MAXINT - 1


def pick_random(c, gen=random):
    # exists as its own function to make it possible to overwrite the seed
    return gen.randint(MININT, MAXINT)


_sortkeyfuncs = {
    b'rev': scmutil.intrev,
    b'branch': lambda c: c.branch(),
    b'desc': lambda c: c.description(),
    b'user': lambda c: c.user(),
    b'author': lambda c: c.user(),
    b'date': lambda c: c.date()[0],
    b'node': scmutil.binnode,
    b'random': pick_random,
}


def _getsortargs(x):
    """Parse sort options into (set, [(key, reverse)], opts)"""
    args = getargsdict(
        x,
        b'sort',
        b'set keys topo.firstbranch random.seed',
    )
    if b'set' not in args:
        # i18n: "sort" is a keyword
        raise error.ParseError(_(b'sort requires one or two arguments'))
    keys = b"rev"
    if b'keys' in args:
        # i18n: "sort" is a keyword
        keys = getstring(args[b'keys'], _(b"sort spec must be a string"))

    keyflags = []
    for k in keys.split():
        fk = k
        reverse = k.startswith(b'-')
        if reverse:
            k = k[1:]
        if k not in _sortkeyfuncs and k != b'topo':
            raise error.ParseError(
                _(b"unknown sort key %r") % pycompat.bytestr(fk)
            )
        keyflags.append((k, reverse))

    if len(keyflags) > 1 and any(k == b'topo' for k, reverse in keyflags):
        # i18n: "topo" is a keyword
        raise error.ParseError(
            _(b'topo sort order cannot be combined with other sort keys')
        )

    opts = {}
    if b'topo.firstbranch' in args:
        if any(k == b'topo' for k, reverse in keyflags):
            opts[b'topo.firstbranch'] = args[b'topo.firstbranch']
        else:
            # i18n: "topo" and "topo.firstbranch" are keywords
            raise error.ParseError(
                _(
                    b'topo.firstbranch can only be used '
                    b'when using the topo sort key'
                )
            )

    if b'random.seed' in args:
        if any(k == b'random' for k, reverse in keyflags):
            s = args[b'random.seed']
            seed = getstring(s, _(b"random.seed must be a string"))
            opts[b'random.seed'] = seed
        else:
            # i18n: "random" and "random.seed" are keywords
            raise error.ParseError(
                _(
                    b'random.seed can only be used '
                    b'when using the random sort key'
                )
            )

    return args[b'set'], keyflags, opts


@predicate(
    b'sort(set[, [-]key... [, ...]])', safe=True, takeorder=True, weight=10
)
def sort(repo, subset, x, order):
    """Sort set by keys. The default sort order is ascending, specify a key
    as ``-key`` to sort in descending order.

    The keys can be:

    - ``rev`` for the revision number,
    - ``branch`` for the branch name,
    - ``desc`` for the commit message (description),
    - ``user`` for user name (``author`` can be used as an alias),
    - ``date`` for the commit date
    - ``topo`` for a reverse topographical sort
    - ``node`` the nodeid of the revision
    - ``random`` randomly shuffle revisions

    The ``topo`` sort order cannot be combined with other sort keys. This sort
    takes one optional argument, ``topo.firstbranch``, which takes a revset that
    specifies what topographical branches to prioritize in the sort.

    The ``random`` sort takes one optional ``random.seed`` argument to control
    the pseudo-randomness of the result.
    """
    s, keyflags, opts = _getsortargs(x)
    revs = getset(repo, subset, s, order)

    if not keyflags or order != defineorder:
        return revs
    if len(keyflags) == 1 and keyflags[0][0] == b"rev":
        revs.sort(reverse=keyflags[0][1])
        return revs
    elif keyflags[0][0] == b"topo":
        firstbranch = ()
        parentrevs = repo.changelog.parentrevs
        parentsfunc = parentrevs
        if wdirrev in revs:

            def parentsfunc(r):
                try:
                    return parentrevs(r)
                except error.WdirUnsupported:
                    return [p.rev() for p in repo[None].parents()]

        if b'topo.firstbranch' in opts:
            firstbranch = getset(repo, subset, opts[b'topo.firstbranch'])
        revs = baseset(
            dagop.toposort(revs, parentsfunc, firstbranch),
            istopo=True,
        )
        if keyflags[0][1]:
            revs.reverse()
        return revs

    # sort() is guaranteed to be stable
    ctxs = [repo[r] for r in revs]
    for k, reverse in reversed(keyflags):
        func = _sortkeyfuncs[k]
        if k == b'random' and b'random.seed' in opts:
            seed = opts[b'random.seed']
            r = random.Random(seed)
            func = functools.partial(func, gen=r)
        ctxs.sort(key=func, reverse=reverse)
    return baseset([c.rev() for c in ctxs])


@predicate(b'subrepo([pattern])')
def subrepo(repo, subset, x):
    """Changesets that add, modify or remove the given subrepo.  If no subrepo
    pattern is named, any subrepo changes are returned.
    """
    # i18n: "subrepo" is a keyword
    args = getargs(x, 0, 1, _(b'subrepo takes at most one argument'))
    pat = None
    if len(args) != 0:
        pat = getstring(args[0], _(b"subrepo requires a pattern"))

    m = matchmod.exact([b'.hgsubstate'])

    def submatches(names):
        k, p, m = stringutil.stringmatcher(pat)
        for name in names:
            if m(name):
                yield name

    def matches(x):
        c = repo[x]
        s = repo.status(c.p1().node(), c.node(), match=m)

        if pat is None:
            return s.added or s.modified or s.removed

        if s.added:
            return any(submatches(c.substate.keys()))

        if s.modified:
            subs = set(c.p1().substate.keys())
            subs.update(c.substate.keys())

            for path in submatches(subs):
                if c.p1().substate.get(path) != c.substate.get(path):
                    return True

        if s.removed:
            return any(submatches(c.p1().substate.keys()))

        return False

    return subset.filter(matches, condrepr=(b'<subrepo %r>', pat))


def _mapbynodefunc(repo, s, f):
    """(repo, smartset, [node] -> [node]) -> smartset

    Helper method to map a smartset to another smartset given a function only
    talking about nodes. Handles converting between rev numbers and nodes, and
    filtering.
    """
    cl = repo.unfiltered().changelog
    torev = cl.index.get_rev
    tonode = cl.node
    result = {torev(n) for n in f(tonode(r) for r in s)}
    result.discard(None)
    return smartset.baseset(result - repo.changelog.filteredrevs)


@predicate(b'successors(set)', safe=True)
def successors(repo, subset, x):
    """All successors for set, including the given set themselves.
    (EXPERIMENTAL)"""
    s = getset(repo, fullreposet(repo), x)
    f = lambda nodes: obsutil.allsuccessors(repo.obsstore, nodes)
    d = _mapbynodefunc(repo, s, f)
    return subset & d


def _substringmatcher(pattern, casesensitive=True):
    kind, pattern, matcher = stringutil.stringmatcher(
        pattern, casesensitive=casesensitive
    )
    if kind == b'literal':
        if not casesensitive:
            pattern = encoding.lower(pattern)
            matcher = lambda s: pattern in encoding.lower(s)
        else:
            matcher = lambda s: pattern in s
    return kind, pattern, matcher


@predicate(b'tag([name])', safe=True)
def tag(repo, subset, x):
    """The specified tag by name, or all tagged revisions if no name is given.

    Pattern matching is supported for `name`. See
    :hg:`help revisions.patterns`.
    """
    # i18n: "tag" is a keyword
    args = getargs(x, 0, 1, _(b"tag takes one or no arguments"))
    cl = repo.changelog
    if args:
        pattern = getstring(
            args[0],
            # i18n: "tag" is a keyword
            _(b'the argument to tag must be a string'),
        )
        kind, pattern, matcher = stringutil.stringmatcher(pattern)
        if kind == b'literal':
            # avoid resolving all tags
            tn = repo._tagscache.tags.get(pattern, None)
            if tn is None:
                raise error.RepoLookupError(
                    _(b"tag '%s' does not exist") % pattern
                )
            s = {repo[tn].rev()}
        else:
            s = {cl.rev(n) for t, n in repo.tagslist() if matcher(t)}
    else:
        s = {cl.rev(n) for t, n in repo.tagslist() if t != b'tip'}
    return subset & s


@predicate(b'tagged', safe=True)
def tagged(repo, subset, x):
    return tag(repo, subset, x)


@predicate(b'orphan()', safe=True)
def orphan(repo, subset, x):
    """Non-obsolete changesets with obsolete ancestors. (EXPERIMENTAL)"""
    # i18n: "orphan" is a keyword
    getargs(x, 0, 0, _(b"orphan takes no arguments"))
    orphan = obsmod.getrevs(repo, b'orphan')
    return subset & orphan


@predicate(b'unstable()', safe=True)
def unstable(repo, subset, x):
    """Changesets with instabilities. (EXPERIMENTAL)"""
    # i18n: "unstable" is a keyword
    getargs(x, 0, 0, b'unstable takes no arguments')
    _unstable = set()
    _unstable.update(obsmod.getrevs(repo, b'orphan'))
    _unstable.update(obsmod.getrevs(repo, b'phasedivergent'))
    _unstable.update(obsmod.getrevs(repo, b'contentdivergent'))
    return subset & baseset(_unstable)


@predicate(b'user(string)', safe=True, weight=10)
def user(repo, subset, x):
    """User name contains string. The match is case-insensitive.

    Pattern matching is supported for `string`. See
    :hg:`help revisions.patterns`.
    """
    return author(repo, subset, x)


@predicate(b'wdir()', safe=True, weight=0)
def wdir(repo, subset, x):
    """Working directory. (EXPERIMENTAL)"""
    # i18n: "wdir" is a keyword
    getargs(x, 0, 0, _(b"wdir takes no arguments"))
    if wdirrev in subset or isinstance(subset, fullreposet):
        return baseset([wdirrev])
    return baseset()


def _orderedlist(repo, subset, x):
    s = getstring(x, b"internal error")
    if not s:
        return baseset()
    # remove duplicates here. it's difficult for caller to deduplicate sets
    # because different symbols can point to the same rev.
    cl = repo.changelog
    ls = []
    seen = set()
    for t in s.split(b'\0'):
        try:
            # fast path for integer revision
            r = int(t)
            if (b'%d' % r) != t or r not in cl:
                raise ValueError
            revs = [r]
        except ValueError:
            revs = stringset(repo, subset, t, defineorder)

        for r in revs:
            if r in seen:
                continue
            if (
                r in subset
                or r in _virtualrevs
                and isinstance(subset, fullreposet)
            ):
                ls.append(r)
            seen.add(r)
    return baseset(ls)


# for internal use
@predicate(b'_list', safe=True, takeorder=True)
def _list(repo, subset, x, order):
    if order == followorder:
        # slow path to take the subset order
        return subset & _orderedlist(repo, fullreposet(repo), x)
    else:
        return _orderedlist(repo, subset, x)


def _orderedintlist(repo, subset, x):
    s = getstring(x, b"internal error")
    if not s:
        return baseset()
    ls = [int(r) for r in s.split(b'\0')]
    s = subset
    return baseset([r for r in ls if r in s])


# for internal use
@predicate(b'_intlist', safe=True, takeorder=True, weight=0)
def _intlist(repo, subset, x, order):
    if order == followorder:
        # slow path to take the subset order
        return subset & _orderedintlist(repo, fullreposet(repo), x)
    else:
        return _orderedintlist(repo, subset, x)


def _orderedhexlist(repo, subset, x):
    s = getstring(x, b"internal error")
    if not s:
        return baseset()
    cl = repo.changelog
    ls = [cl.rev(bin(r)) for r in s.split(b'\0')]
    s = subset
    return baseset([r for r in ls if r in s])


# for internal use
@predicate(b'_hexlist', safe=True, takeorder=True)
def _hexlist(repo, subset, x, order):
    if order == followorder:
        # slow path to take the subset order
        return subset & _orderedhexlist(repo, fullreposet(repo), x)
    else:
        return _orderedhexlist(repo, subset, x)


methods = {
    b"range": rangeset,
    b"rangeall": rangeall,
    b"rangepre": rangepre,
    b"rangepost": rangepost,
    b"dagrange": dagrange,
    b"string": stringset,
    b"symbol": stringset,
    b"and": andset,
    b"andsmally": andsmallyset,
    b"or": orset,
    b"not": notset,
    b"difference": differenceset,
    b"relation": relationset,
    b"relsubscript": relsubscriptset,
    b"subscript": subscriptset,
    b"list": listset,
    b"keyvalue": keyvaluepair,
    b"func": func,
    b"ancestor": ancestorspec,
    b"parent": parentspec,
    b"parentpost": parentpost,
    b"smartset": rawsmartset,
    b"nodeset": raw_node_set,
}

relations = {
    b"g": generationsrel,
    b"generations": generationsrel,
}

subscriptrelations = {
    b"g": generationssubrel,
    b"generations": generationssubrel,
}


def lookupfn(repo):
    def fn(symbol):
        try:
            return scmutil.isrevsymbol(repo, symbol)
        except error.AmbiguousPrefixLookupError:
            raise error.InputError(
                b'ambiguous revision identifier: %s' % symbol
            )

    return fn


def match(ui, spec, lookup=None):
    """Create a matcher for a single revision spec"""
    return matchany(ui, [spec], lookup=lookup)


def matchany(ui, specs, lookup=None, localalias=None):
    """Create a matcher that will include any revisions matching one of the
    given specs

    If lookup function is not None, the parser will first attempt to handle
    old-style ranges, which may contain operator characters.

    If localalias is not None, it is a dict {name: definitionstring}. It takes
    precedence over [revsetalias] config section.
    """
    if not specs:

        def mfunc(repo, subset=None):
            return baseset()

        return mfunc
    if not all(specs):
        raise error.ParseError(_(b"empty query"))
    if len(specs) == 1:
        tree = revsetlang.parse(specs[0], lookup)
    else:
        tree = (
            b'or',
            (b'list',) + tuple(revsetlang.parse(s, lookup) for s in specs),
        )

    aliases = []
    warn = None
    if ui:
        aliases.extend(ui.configitems(b'revsetalias'))
        warn = ui.warn
    if localalias:
        aliases.extend(localalias.items())
    if aliases:
        tree = revsetlang.expandaliases(tree, aliases, warn=warn)
    tree = revsetlang.foldconcat(tree)
    tree = revsetlang.analyze(tree)
    tree = revsetlang.optimize(tree)
    return makematcher(tree)


def makematcher(tree):
    """Create a matcher from an evaluatable tree"""

    def mfunc(repo, subset=None, order=None):
        if order is None:
            if subset is None:
                order = defineorder  # 'x'
            else:
                order = followorder  # 'subset & x'
        if subset is None:
            subset = fullreposet(repo)
        return getset(repo, subset, tree, order)

    return mfunc


def loadpredicate(ui, extname, registrarobj):
    """Load revset predicates from specified registrarobj"""
    for name, func in registrarobj._table.items():
        symbols[name] = func
        if func._safe:
            safesymbols.add(name)


# load built-in predicates explicitly to setup safesymbols
loadpredicate(None, None, predicate)

# tell hggettext to extract docstrings from these functions:
i18nfunctions = symbols.values()
