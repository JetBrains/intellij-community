# revset.py - revision set queries for mercurial
#
# Copyright 2010 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import re
import parser, util, error, discovery, hbisect, phases
import node
import match as matchmod
from i18n import _
import encoding
import obsolete as obsmod
import repoview

def _revancestors(repo, revs, followfirst):
    """Like revlog.ancestors(), but supports followfirst."""
    cut = followfirst and 1 or None
    cl = repo.changelog
    visit = util.deque(revs)
    seen = set([node.nullrev])
    while visit:
        for parent in cl.parentrevs(visit.popleft())[:cut]:
            if parent not in seen:
                visit.append(parent)
                seen.add(parent)
                yield parent

def _revdescendants(repo, revs, followfirst):
    """Like revlog.descendants() but supports followfirst."""
    cut = followfirst and 1 or None
    cl = repo.changelog
    first = min(revs)
    nullrev = node.nullrev
    if first == nullrev:
        # Are there nodes with a null first parent and a non-null
        # second one? Maybe. Do we care? Probably not.
        for i in cl:
            yield i
        return

    seen = set(revs)
    for i in cl.revs(first + 1):
        for x in cl.parentrevs(i)[:cut]:
            if x != nullrev and x in seen:
                seen.add(i)
                yield i
                break

def _revsbetween(repo, roots, heads):
    """Return all paths between roots and heads, inclusive of both endpoint
    sets."""
    if not roots:
        return []
    parentrevs = repo.changelog.parentrevs
    visit = heads[:]
    reachable = set()
    seen = {}
    minroot = min(roots)
    roots = set(roots)
    # open-code the post-order traversal due to the tiny size of
    # sys.getrecursionlimit()
    while visit:
        rev = visit.pop()
        if rev in roots:
            reachable.add(rev)
        parents = parentrevs(rev)
        seen[rev] = parents
        for parent in parents:
            if parent >= minroot and parent not in seen:
                visit.append(parent)
    if not reachable:
        return []
    for rev in sorted(seen):
        for parent in seen[rev]:
            if parent in reachable:
                reachable.add(rev)
    return sorted(reachable)

elements = {
    "(": (20, ("group", 1, ")"), ("func", 1, ")")),
    "~": (18, None, ("ancestor", 18)),
    "^": (18, None, ("parent", 18), ("parentpost", 18)),
    "-": (5, ("negate", 19), ("minus", 5)),
    "::": (17, ("dagrangepre", 17), ("dagrange", 17),
           ("dagrangepost", 17)),
    "..": (17, ("dagrangepre", 17), ("dagrange", 17),
           ("dagrangepost", 17)),
    ":": (15, ("rangepre", 15), ("range", 15), ("rangepost", 15)),
    "not": (10, ("not", 10)),
    "!": (10, ("not", 10)),
    "and": (5, None, ("and", 5)),
    "&": (5, None, ("and", 5)),
    "or": (4, None, ("or", 4)),
    "|": (4, None, ("or", 4)),
    "+": (4, None, ("or", 4)),
    ",": (2, None, ("list", 2)),
    ")": (0, None, None),
    "symbol": (0, ("symbol",), None),
    "string": (0, ("string",), None),
    "end": (0, None, None),
}

keywords = set(['and', 'or', 'not'])

def tokenize(program):
    '''
    Parse a revset statement into a stream of tokens

    Check that @ is a valid unquoted token character (issue3686):
    >>> list(tokenize("@::"))
    [('symbol', '@', 0), ('::', None, 1), ('end', None, 3)]

    '''

    pos, l = 0, len(program)
    while pos < l:
        c = program[pos]
        if c.isspace(): # skip inter-token whitespace
            pass
        elif c == ':' and program[pos:pos + 2] == '::': # look ahead carefully
            yield ('::', None, pos)
            pos += 1 # skip ahead
        elif c == '.' and program[pos:pos + 2] == '..': # look ahead carefully
            yield ('..', None, pos)
            pos += 1 # skip ahead
        elif c in "():,-|&+!~^": # handle simple operators
            yield (c, None, pos)
        elif (c in '"\'' or c == 'r' and
              program[pos:pos + 2] in ("r'", 'r"')): # handle quoted strings
            if c == 'r':
                pos += 1
                c = program[pos]
                decode = lambda x: x
            else:
                decode = lambda x: x.decode('string-escape')
            pos += 1
            s = pos
            while pos < l: # find closing quote
                d = program[pos]
                if d == '\\': # skip over escaped characters
                    pos += 2
                    continue
                if d == c:
                    yield ('string', decode(program[s:pos]), s)
                    break
                pos += 1
            else:
                raise error.ParseError(_("unterminated string"), s)
        # gather up a symbol/keyword
        elif c.isalnum() or c in '._@' or ord(c) > 127:
            s = pos
            pos += 1
            while pos < l: # find end of symbol
                d = program[pos]
                if not (d.isalnum() or d in "._/@" or ord(d) > 127):
                    break
                if d == '.' and program[pos - 1] == '.': # special case for ..
                    pos -= 1
                    break
                pos += 1
            sym = program[s:pos]
            if sym in keywords: # operator keywords
                yield (sym, None, s)
            else:
                yield ('symbol', sym, s)
            pos -= 1
        else:
            raise error.ParseError(_("syntax error"), pos)
        pos += 1
    yield ('end', None, pos)

# helpers

def getstring(x, err):
    if x and (x[0] == 'string' or x[0] == 'symbol'):
        return x[1]
    raise error.ParseError(err)

def getlist(x):
    if not x:
        return []
    if x[0] == 'list':
        return getlist(x[1]) + [x[2]]
    return [x]

def getargs(x, min, max, err):
    l = getlist(x)
    if len(l) < min or (max >= 0 and len(l) > max):
        raise error.ParseError(err)
    return l

def getset(repo, subset, x):
    if not x:
        raise error.ParseError(_("missing argument"))
    return methods[x[0]](repo, subset, *x[1:])

def _getrevsource(repo, r):
    extra = repo[r].extra()
    for label in ('source', 'transplant_source', 'rebase_source'):
        if label in extra:
            try:
                return repo[extra[label]].rev()
            except error.RepoLookupError:
                pass
    return None

# operator methods

def stringset(repo, subset, x):
    x = repo[x].rev()
    if x == -1 and len(subset) == len(repo):
        return [-1]
    if len(subset) == len(repo) or x in subset:
        return [x]
    return []

def symbolset(repo, subset, x):
    if x in symbols:
        raise error.ParseError(_("can't use %s here") % x)
    return stringset(repo, subset, x)

def rangeset(repo, subset, x, y):
    cl = repo.changelog
    m = getset(repo, cl, x)
    n = getset(repo, cl, y)

    if not m or not n:
        return []
    m, n = m[0], n[-1]

    if m < n:
        r = range(m, n + 1)
    else:
        r = range(m, n - 1, -1)
    s = set(subset)
    return [x for x in r if x in s]

def dagrange(repo, subset, x, y):
    r = list(repo)
    xs = _revsbetween(repo, getset(repo, r, x), getset(repo, r, y))
    s = set(subset)
    return [r for r in xs if r in s]

def andset(repo, subset, x, y):
    return getset(repo, getset(repo, subset, x), y)

def orset(repo, subset, x, y):
    xl = getset(repo, subset, x)
    s = set(xl)
    yl = getset(repo, [r for r in subset if r not in s], y)
    return xl + yl

def notset(repo, subset, x):
    s = set(getset(repo, subset, x))
    return [r for r in subset if r not in s]

def listset(repo, subset, a, b):
    raise error.ParseError(_("can't use a list in this context"))

def func(repo, subset, a, b):
    if a[0] == 'symbol' and a[1] in symbols:
        return symbols[a[1]](repo, subset, b)
    raise error.ParseError(_("not a function: %s") % a[1])

# functions

def adds(repo, subset, x):
    """``adds(pattern)``
    Changesets that add a file matching pattern.
    """
    # i18n: "adds" is a keyword
    pat = getstring(x, _("adds requires a pattern"))
    return checkstatus(repo, subset, pat, 1)

def ancestor(repo, subset, x):
    """``ancestor(*changeset)``
    Greatest common ancestor of the changesets.

    Accepts 0 or more changesets.
    Will return empty list when passed no args.
    Greatest common ancestor of a single changeset is that changeset.
    """
    # i18n: "ancestor" is a keyword
    l = getlist(x)
    rl = list(repo)
    anc = None

    # (getset(repo, rl, i) for i in l) generates a list of lists
    rev = repo.changelog.rev
    ancestor = repo.changelog.ancestor
    node = repo.changelog.node
    for revs in (getset(repo, rl, i) for i in l):
        for r in revs:
            if anc is None:
                anc = r
            else:
                anc = rev(ancestor(node(anc), node(r)))

    if anc is not None and anc in subset:
        return [anc]
    return []

def _ancestors(repo, subset, x, followfirst=False):
    args = getset(repo, list(repo), x)
    if not args:
        return []
    s = set(_revancestors(repo, args, followfirst)) | set(args)
    return [r for r in subset if r in s]

def ancestors(repo, subset, x):
    """``ancestors(set)``
    Changesets that are ancestors of a changeset in set.
    """
    return _ancestors(repo, subset, x)

def _firstancestors(repo, subset, x):
    # ``_firstancestors(set)``
    # Like ``ancestors(set)`` but follows only the first parents.
    return _ancestors(repo, subset, x, followfirst=True)

def ancestorspec(repo, subset, x, n):
    """``set~n``
    Changesets that are the Nth ancestor (first parents only) of a changeset
    in set.
    """
    try:
        n = int(n[1])
    except (TypeError, ValueError):
        raise error.ParseError(_("~ expects a number"))
    ps = set()
    cl = repo.changelog
    for r in getset(repo, cl, x):
        for i in range(n):
            r = cl.parentrevs(r)[0]
        ps.add(r)
    return [r for r in subset if r in ps]

def author(repo, subset, x):
    """``author(string)``
    Alias for ``user(string)``.
    """
    # i18n: "author" is a keyword
    n = encoding.lower(getstring(x, _("author requires a string")))
    kind, pattern, matcher = _substringmatcher(n)
    return [r for r in subset if matcher(encoding.lower(repo[r].user()))]

def bisect(repo, subset, x):
    """``bisect(string)``
    Changesets marked in the specified bisect status:

    - ``good``, ``bad``, ``skip``: csets explicitly marked as good/bad/skip
    - ``goods``, ``bads``      : csets topologically good/bad
    - ``range``              : csets taking part in the bisection
    - ``pruned``             : csets that are goods, bads or skipped
    - ``untested``           : csets whose fate is yet unknown
    - ``ignored``            : csets ignored due to DAG topology
    - ``current``            : the cset currently being bisected
    """
    # i18n: "bisect" is a keyword
    status = getstring(x, _("bisect requires a string")).lower()
    state = set(hbisect.get(repo, status))
    return [r for r in subset if r in state]

# Backward-compatibility
# - no help entry so that we do not advertise it any more
def bisected(repo, subset, x):
    return bisect(repo, subset, x)

def bookmark(repo, subset, x):
    """``bookmark([name])``
    The named bookmark or all bookmarks.

    If `name` starts with `re:`, the remainder of the name is treated as
    a regular expression. To match a bookmark that actually starts with `re:`,
    use the prefix `literal:`.
    """
    # i18n: "bookmark" is a keyword
    args = getargs(x, 0, 1, _('bookmark takes one or no arguments'))
    if args:
        bm = getstring(args[0],
                       # i18n: "bookmark" is a keyword
                       _('the argument to bookmark must be a string'))
        kind, pattern, matcher = _stringmatcher(bm)
        if kind == 'literal':
            bmrev = repo._bookmarks.get(bm, None)
            if not bmrev:
                raise util.Abort(_("bookmark '%s' does not exist") % bm)
            bmrev = repo[bmrev].rev()
            return [r for r in subset if r == bmrev]
        else:
            matchrevs = set()
            for name, bmrev in repo._bookmarks.iteritems():
                if matcher(name):
                    matchrevs.add(bmrev)
            if not matchrevs:
                raise util.Abort(_("no bookmarks exist that match '%s'")
                                 % pattern)
            bmrevs = set()
            for bmrev in matchrevs:
                bmrevs.add(repo[bmrev].rev())
            return [r for r in subset if r in bmrevs]

    bms = set([repo[r].rev()
               for r in repo._bookmarks.values()])
    return [r for r in subset if r in bms]

def branch(repo, subset, x):
    """``branch(string or set)``
    All changesets belonging to the given branch or the branches of the given
    changesets.

    If `string` starts with `re:`, the remainder of the name is treated as
    a regular expression. To match a branch that actually starts with `re:`,
    use the prefix `literal:`.
    """
    try:
        b = getstring(x, '')
    except error.ParseError:
        # not a string, but another revspec, e.g. tip()
        pass
    else:
        kind, pattern, matcher = _stringmatcher(b)
        if kind == 'literal':
            # note: falls through to the revspec case if no branch with
            # this name exists
            if pattern in repo.branchmap():
                return [r for r in subset if matcher(repo[r].branch())]
        else:
            return [r for r in subset if matcher(repo[r].branch())]

    s = getset(repo, list(repo), x)
    b = set()
    for r in s:
        b.add(repo[r].branch())
    s = set(s)
    return [r for r in subset if r in s or repo[r].branch() in b]

def bumped(repo, subset, x):
    """``bumped()``
    Mutable changesets marked as successors of public changesets.

    Only non-public and non-obsolete changesets can be `bumped`.
    """
    # i18n: "bumped" is a keyword
    getargs(x, 0, 0, _("bumped takes no arguments"))
    bumped = obsmod.getrevs(repo, 'bumped')
    return [r for r in subset if r in bumped]

def bundle(repo, subset, x):
    """``bundle()``
    Changesets in the bundle.

    Bundle must be specified by the -R option."""

    try:
        bundlerevs = repo.changelog.bundlerevs
    except AttributeError:
        raise util.Abort(_("no bundle provided - specify with -R"))
    return [r for r in subset if r in bundlerevs]

def checkstatus(repo, subset, pat, field):
    m = None
    s = []
    hasset = matchmod.patkind(pat) == 'set'
    fname = None
    for r in subset:
        c = repo[r]
        if not m or hasset:
            m = matchmod.match(repo.root, repo.getcwd(), [pat], ctx=c)
            if not m.anypats() and len(m.files()) == 1:
                fname = m.files()[0]
        if fname is not None:
            if fname not in c.files():
                continue
        else:
            for f in c.files():
                if m(f):
                    break
            else:
                continue
        files = repo.status(c.p1().node(), c.node())[field]
        if fname is not None:
            if fname in files:
                s.append(r)
        else:
            for f in files:
                if m(f):
                    s.append(r)
                    break
    return s

def _children(repo, narrow, parentset):
    cs = set()
    if not parentset:
        return cs
    pr = repo.changelog.parentrevs
    minrev = min(parentset)
    for r in narrow:
        if r <= minrev:
            continue
        for p in pr(r):
            if p in parentset:
                cs.add(r)
    return cs

def children(repo, subset, x):
    """``children(set)``
    Child changesets of changesets in set.
    """
    s = set(getset(repo, list(repo), x))
    cs = _children(repo, subset, s)
    return [r for r in subset if r in cs]

def closed(repo, subset, x):
    """``closed()``
    Changeset is closed.
    """
    # i18n: "closed" is a keyword
    getargs(x, 0, 0, _("closed takes no arguments"))
    return [r for r in subset if repo[r].closesbranch()]

def contains(repo, subset, x):
    """``contains(pattern)``
    Revision contains a file matching pattern. See :hg:`help patterns`
    for information about file patterns.
    """
    # i18n: "contains" is a keyword
    pat = getstring(x, _("contains requires a pattern"))
    m = None
    s = []
    if not matchmod.patkind(pat):
        for r in subset:
            if pat in repo[r]:
                s.append(r)
    else:
        for r in subset:
            c = repo[r]
            if not m or matchmod.patkind(pat) == 'set':
                m = matchmod.match(repo.root, repo.getcwd(), [pat], ctx=c)
            for f in c.manifest():
                if m(f):
                    s.append(r)
                    break
    return s

def converted(repo, subset, x):
    """``converted([id])``
    Changesets converted from the given identifier in the old repository if
    present, or all converted changesets if no identifier is specified.
    """

    # There is exactly no chance of resolving the revision, so do a simple
    # string compare and hope for the best

    rev = None
    # i18n: "converted" is a keyword
    l = getargs(x, 0, 1, _('converted takes one or no arguments'))
    if l:
        # i18n: "converted" is a keyword
        rev = getstring(l[0], _('converted requires a revision'))

    def _matchvalue(r):
        source = repo[r].extra().get('convert_revision', None)
        return source is not None and (rev is None or source.startswith(rev))

    return [r for r in subset if _matchvalue(r)]

def date(repo, subset, x):
    """``date(interval)``
    Changesets within the interval, see :hg:`help dates`.
    """
    # i18n: "date" is a keyword
    ds = getstring(x, _("date requires a string"))
    dm = util.matchdate(ds)
    return [r for r in subset if dm(repo[r].date()[0])]

def desc(repo, subset, x):
    """``desc(string)``
    Search commit message for string. The match is case-insensitive.
    """
    # i18n: "desc" is a keyword
    ds = encoding.lower(getstring(x, _("desc requires a string")))
    l = []
    for r in subset:
        c = repo[r]
        if ds in encoding.lower(c.description()):
            l.append(r)
    return l

def _descendants(repo, subset, x, followfirst=False):
    args = getset(repo, list(repo), x)
    if not args:
        return []
    s = set(_revdescendants(repo, args, followfirst)) | set(args)
    return [r for r in subset if r in s]

def descendants(repo, subset, x):
    """``descendants(set)``
    Changesets which are descendants of changesets in set.
    """
    return _descendants(repo, subset, x)

def _firstdescendants(repo, subset, x):
    # ``_firstdescendants(set)``
    # Like ``descendants(set)`` but follows only the first parents.
    return _descendants(repo, subset, x, followfirst=True)

def destination(repo, subset, x):
    """``destination([set])``
    Changesets that were created by a graft, transplant or rebase operation,
    with the given revisions specified as the source.  Omitting the optional set
    is the same as passing all().
    """
    if x is not None:
        args = set(getset(repo, list(repo), x))
    else:
        args = set(getall(repo, list(repo), x))

    dests = set()

    # subset contains all of the possible destinations that can be returned, so
    # iterate over them and see if their source(s) were provided in the args.
    # Even if the immediate src of r is not in the args, src's source (or
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
            if src in args or src in dests:
                dests.update(lineage)
                break

            r = src
            src = _getrevsource(repo, r)

    return [r for r in subset if r in dests]

def divergent(repo, subset, x):
    """``divergent()``
    Final successors of changesets with an alternative set of final successors.
    """
    # i18n: "divergent" is a keyword
    getargs(x, 0, 0, _("divergent takes no arguments"))
    divergent = obsmod.getrevs(repo, 'divergent')
    return [r for r in subset if r in divergent]

def draft(repo, subset, x):
    """``draft()``
    Changeset in draft phase."""
    # i18n: "draft" is a keyword
    getargs(x, 0, 0, _("draft takes no arguments"))
    pc = repo._phasecache
    return [r for r in subset if pc.phase(repo, r) == phases.draft]

def extinct(repo, subset, x):
    """``extinct()``
    Obsolete changesets with obsolete descendants only.
    """
    # i18n: "extinct" is a keyword
    getargs(x, 0, 0, _("extinct takes no arguments"))
    extincts = obsmod.getrevs(repo, 'extinct')
    return [r for r in subset if r in extincts]

def extra(repo, subset, x):
    """``extra(label, [value])``
    Changesets with the given label in the extra metadata, with the given
    optional value.

    If `value` starts with `re:`, the remainder of the value is treated as
    a regular expression. To match a value that actually starts with `re:`,
    use the prefix `literal:`.
    """

    # i18n: "extra" is a keyword
    l = getargs(x, 1, 2, _('extra takes at least 1 and at most 2 arguments'))
    # i18n: "extra" is a keyword
    label = getstring(l[0], _('first argument to extra must be a string'))
    value = None

    if len(l) > 1:
        # i18n: "extra" is a keyword
        value = getstring(l[1], _('second argument to extra must be a string'))
        kind, value, matcher = _stringmatcher(value)

    def _matchvalue(r):
        extra = repo[r].extra()
        return label in extra and (value is None or matcher(extra[label]))

    return [r for r in subset if _matchvalue(r)]

def filelog(repo, subset, x):
    """``filelog(pattern)``
    Changesets connected to the specified filelog.

    For performance reasons, ``filelog()`` does not show every changeset
    that affects the requested file(s). See :hg:`help log` for details. For
    a slower, more accurate result, use ``file()``.
    """

    # i18n: "filelog" is a keyword
    pat = getstring(x, _("filelog requires a pattern"))
    m = matchmod.match(repo.root, repo.getcwd(), [pat], default='relpath',
                       ctx=repo[None])
    s = set()

    if not matchmod.patkind(pat):
        for f in m.files():
            fl = repo.file(f)
            for fr in fl:
                s.add(fl.linkrev(fr))
    else:
        for f in repo[None]:
            if m(f):
                fl = repo.file(f)
                for fr in fl:
                    s.add(fl.linkrev(fr))

    return [r for r in subset if r in s]

def first(repo, subset, x):
    """``first(set, [n])``
    An alias for limit().
    """
    return limit(repo, subset, x)

def _follow(repo, subset, x, name, followfirst=False):
    l = getargs(x, 0, 1, _("%s takes no arguments or a filename") % name)
    c = repo['.']
    if l:
        x = getstring(l[0], _("%s expected a filename") % name)
        if x in c:
            cx = c[x]
            s = set(ctx.rev() for ctx in cx.ancestors(followfirst=followfirst))
            # include the revision responsible for the most recent version
            s.add(cx.linkrev())
        else:
            return []
    else:
        s = set(_revancestors(repo, [c.rev()], followfirst)) | set([c.rev()])

    return [r for r in subset if r in s]

def follow(repo, subset, x):
    """``follow([file])``
    An alias for ``::.`` (ancestors of the working copy's first parent).
    If a filename is specified, the history of the given file is followed,
    including copies.
    """
    return _follow(repo, subset, x, 'follow')

def _followfirst(repo, subset, x):
    # ``followfirst([file])``
    # Like ``follow([file])`` but follows only the first parent of
    # every revision or file revision.
    return _follow(repo, subset, x, '_followfirst', followfirst=True)

def getall(repo, subset, x):
    """``all()``
    All changesets, the same as ``0:tip``.
    """
    # i18n: "all" is a keyword
    getargs(x, 0, 0, _("all takes no arguments"))
    return subset

def grep(repo, subset, x):
    """``grep(regex)``
    Like ``keyword(string)`` but accepts a regex. Use ``grep(r'...')``
    to ensure special escape characters are handled correctly. Unlike
    ``keyword(string)``, the match is case-sensitive.
    """
    try:
        # i18n: "grep" is a keyword
        gr = re.compile(getstring(x, _("grep requires a string")))
    except re.error, e:
        raise error.ParseError(_('invalid match pattern: %s') % e)
    l = []
    for r in subset:
        c = repo[r]
        for e in c.files() + [c.user(), c.description()]:
            if gr.search(e):
                l.append(r)
                break
    return l

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

    # i18n: "_matchfiles" is a keyword
    l = getargs(x, 1, -1, _("_matchfiles requires at least one argument"))
    pats, inc, exc = [], [], []
    hasset = False
    rev, default = None, None
    for arg in l:
        # i18n: "_matchfiles" is a keyword
        s = getstring(arg, _("_matchfiles requires string arguments"))
        prefix, value = s[:2], s[2:]
        if prefix == 'p:':
            pats.append(value)
        elif prefix == 'i:':
            inc.append(value)
        elif prefix == 'x:':
            exc.append(value)
        elif prefix == 'r:':
            if rev is not None:
                # i18n: "_matchfiles" is a keyword
                raise error.ParseError(_('_matchfiles expected at most one '
                                         'revision'))
            rev = value
        elif prefix == 'd:':
            if default is not None:
                # i18n: "_matchfiles" is a keyword
                raise error.ParseError(_('_matchfiles expected at most one '
                                         'default mode'))
            default = value
        else:
            # i18n: "_matchfiles" is a keyword
            raise error.ParseError(_('invalid _matchfiles prefix: %s') % prefix)
        if not hasset and matchmod.patkind(value) == 'set':
            hasset = True
    if not default:
        default = 'glob'
    m = None
    s = []
    for r in subset:
        c = repo[r]
        if not m or (hasset and rev is None):
            ctx = c
            if rev is not None:
                ctx = repo[rev or None]
            m = matchmod.match(repo.root, repo.getcwd(), pats, include=inc,
                               exclude=exc, ctx=ctx, default=default)
        for f in c.files():
            if m(f):
                s.append(r)
                break
    return s

def hasfile(repo, subset, x):
    """``file(pattern)``
    Changesets affecting files matched by pattern.

    For a faster but less accurate result, consider using ``filelog()``
    instead.
    """
    # i18n: "file" is a keyword
    pat = getstring(x, _("file requires a pattern"))
    return _matchfiles(repo, subset, ('string', 'p:' + pat))

def head(repo, subset, x):
    """``head()``
    Changeset is a named branch head.
    """
    # i18n: "head" is a keyword
    getargs(x, 0, 0, _("head takes no arguments"))
    hs = set()
    for b, ls in repo.branchmap().iteritems():
        hs.update(repo[h].rev() for h in ls)
    return [r for r in subset if r in hs]

def heads(repo, subset, x):
    """``heads(set)``
    Members of set with no children in set.
    """
    s = getset(repo, subset, x)
    ps = set(parents(repo, subset, x))
    return [r for r in s if r not in ps]

def hidden(repo, subset, x):
    """``hidden()``
    Hidden changesets.
    """
    # i18n: "hidden" is a keyword
    getargs(x, 0, 0, _("hidden takes no arguments"))
    hiddenrevs = repoview.filterrevs(repo, 'visible')
    return [r for r in subset if r in hiddenrevs]

def keyword(repo, subset, x):
    """``keyword(string)``
    Search commit message, user name, and names of changed files for
    string. The match is case-insensitive.
    """
    # i18n: "keyword" is a keyword
    kw = encoding.lower(getstring(x, _("keyword requires a string")))
    l = []
    for r in subset:
        c = repo[r]
        t = " ".join(c.files() + [c.user(), c.description()])
        if kw in encoding.lower(t):
            l.append(r)
    return l

def limit(repo, subset, x):
    """``limit(set, [n])``
    First n members of set, defaulting to 1.
    """
    # i18n: "limit" is a keyword
    l = getargs(x, 1, 2, _("limit requires one or two arguments"))
    try:
        lim = 1
        if len(l) == 2:
            # i18n: "limit" is a keyword
            lim = int(getstring(l[1], _("limit requires a number")))
    except (TypeError, ValueError):
        # i18n: "limit" is a keyword
        raise error.ParseError(_("limit expects a number"))
    ss = set(subset)
    os = getset(repo, list(repo), l[0])[:lim]
    return [r for r in os if r in ss]

def last(repo, subset, x):
    """``last(set, [n])``
    Last n members of set, defaulting to 1.
    """
    # i18n: "last" is a keyword
    l = getargs(x, 1, 2, _("last requires one or two arguments"))
    try:
        lim = 1
        if len(l) == 2:
            # i18n: "last" is a keyword
            lim = int(getstring(l[1], _("last requires a number")))
    except (TypeError, ValueError):
        # i18n: "last" is a keyword
        raise error.ParseError(_("last expects a number"))
    ss = set(subset)
    os = getset(repo, list(repo), l[0])[-lim:]
    return [r for r in os if r in ss]

def maxrev(repo, subset, x):
    """``max(set)``
    Changeset with highest revision number in set.
    """
    os = getset(repo, list(repo), x)
    if os:
        m = max(os)
        if m in subset:
            return [m]
    return []

def merge(repo, subset, x):
    """``merge()``
    Changeset is a merge changeset.
    """
    # i18n: "merge" is a keyword
    getargs(x, 0, 0, _("merge takes no arguments"))
    cl = repo.changelog
    return [r for r in subset if cl.parentrevs(r)[1] != -1]

def branchpoint(repo, subset, x):
    """``branchpoint()``
    Changesets with more than one child.
    """
    # i18n: "branchpoint" is a keyword
    getargs(x, 0, 0, _("branchpoint takes no arguments"))
    cl = repo.changelog
    if not subset:
        return []
    baserev = min(subset)
    parentscount = [0]*(len(repo) - baserev)
    for r in cl.revs(start=baserev + 1):
        for p in cl.parentrevs(r):
            if p >= baserev:
                parentscount[p - baserev] += 1
    return [r for r in subset if (parentscount[r - baserev] > 1)]

def minrev(repo, subset, x):
    """``min(set)``
    Changeset with lowest revision number in set.
    """
    os = getset(repo, list(repo), x)
    if os:
        m = min(os)
        if m in subset:
            return [m]
    return []

def modifies(repo, subset, x):
    """``modifies(pattern)``
    Changesets modifying files matched by pattern.
    """
    # i18n: "modifies" is a keyword
    pat = getstring(x, _("modifies requires a pattern"))
    return checkstatus(repo, subset, pat, 0)

def node_(repo, subset, x):
    """``id(string)``
    Revision non-ambiguously specified by the given hex string prefix.
    """
    # i18n: "id" is a keyword
    l = getargs(x, 1, 1, _("id requires one argument"))
    # i18n: "id" is a keyword
    n = getstring(l[0], _("id requires a string"))
    if len(n) == 40:
        rn = repo[n].rev()
    else:
        rn = None
        pm = repo.changelog._partialmatch(n)
        if pm is not None:
            rn = repo.changelog.rev(pm)

    return [r for r in subset if r == rn]

def obsolete(repo, subset, x):
    """``obsolete()``
    Mutable changeset with a newer version."""
    # i18n: "obsolete" is a keyword
    getargs(x, 0, 0, _("obsolete takes no arguments"))
    obsoletes = obsmod.getrevs(repo, 'obsolete')
    return [r for r in subset if r in obsoletes]

def origin(repo, subset, x):
    """``origin([set])``
    Changesets that were specified as a source for the grafts, transplants or
    rebases that created the given revisions.  Omitting the optional set is the
    same as passing all().  If a changeset created by these operations is itself
    specified as a source for one of these operations, only the source changeset
    for the first operation is selected.
    """
    if x is not None:
        args = set(getset(repo, list(repo), x))
    else:
        args = set(getall(repo, list(repo), x))

    def _firstsrc(rev):
        src = _getrevsource(repo, rev)
        if src is None:
            return None

        while True:
            prev = _getrevsource(repo, src)

            if prev is None:
                return src
            src = prev

    o = set([_firstsrc(r) for r in args])
    return [r for r in subset if r in o]

def outgoing(repo, subset, x):
    """``outgoing([path])``
    Changesets not found in the specified destination repository, or the
    default push location.
    """
    import hg # avoid start-up nasties
    # i18n: "outgoing" is a keyword
    l = getargs(x, 0, 1, _("outgoing takes one or no arguments"))
    # i18n: "outgoing" is a keyword
    dest = l and getstring(l[0], _("outgoing requires a repository path")) or ''
    dest = repo.ui.expandpath(dest or 'default-push', dest or 'default')
    dest, branches = hg.parseurl(dest)
    revs, checkout = hg.addbranchrevs(repo, repo, branches, [])
    if revs:
        revs = [repo.lookup(rev) for rev in revs]
    other = hg.peer(repo, {}, dest)
    repo.ui.pushbuffer()
    outgoing = discovery.findcommonoutgoing(repo, other, onlyheads=revs)
    repo.ui.popbuffer()
    cl = repo.changelog
    o = set([cl.rev(r) for r in outgoing.missing])
    return [r for r in subset if r in o]

def p1(repo, subset, x):
    """``p1([set])``
    First parent of changesets in set, or the working directory.
    """
    if x is None:
        p = repo[x].p1().rev()
        return [r for r in subset if r == p]

    ps = set()
    cl = repo.changelog
    for r in getset(repo, list(repo), x):
        ps.add(cl.parentrevs(r)[0])
    return [r for r in subset if r in ps]

def p2(repo, subset, x):
    """``p2([set])``
    Second parent of changesets in set, or the working directory.
    """
    if x is None:
        ps = repo[x].parents()
        try:
            p = ps[1].rev()
            return [r for r in subset if r == p]
        except IndexError:
            return []

    ps = set()
    cl = repo.changelog
    for r in getset(repo, list(repo), x):
        ps.add(cl.parentrevs(r)[1])
    return [r for r in subset if r in ps]

def parents(repo, subset, x):
    """``parents([set])``
    The set of all parents for all changesets in set, or the working directory.
    """
    if x is None:
        ps = tuple(p.rev() for p in repo[x].parents())
        return [r for r in subset if r in ps]

    ps = set()
    cl = repo.changelog
    for r in getset(repo, list(repo), x):
        ps.update(cl.parentrevs(r))
    return [r for r in subset if r in ps]

def parentspec(repo, subset, x, n):
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
        raise error.ParseError(_("^ expects a number 0, 1, or 2"))
    ps = set()
    cl = repo.changelog
    for r in getset(repo, cl, x):
        if n == 0:
            ps.add(r)
        elif n == 1:
            ps.add(cl.parentrevs(r)[0])
        elif n == 2:
            parents = cl.parentrevs(r)
            if len(parents) > 1:
                ps.add(parents[1])
    return [r for r in subset if r in ps]

def present(repo, subset, x):
    """``present(set)``
    An empty set, if any revision in set isn't found; otherwise,
    all revisions in set.

    If any of specified revisions is not present in the local repository,
    the query is normally aborted. But this predicate allows the query
    to continue even in such cases.
    """
    try:
        return getset(repo, subset, x)
    except error.RepoLookupError:
        return []

def public(repo, subset, x):
    """``public()``
    Changeset in public phase."""
    # i18n: "public" is a keyword
    getargs(x, 0, 0, _("public takes no arguments"))
    pc = repo._phasecache
    return [r for r in subset if pc.phase(repo, r) == phases.public]

def remote(repo, subset, x):
    """``remote([id [,path]])``
    Local revision that corresponds to the given identifier in a
    remote repository, if present. Here, the '.' identifier is a
    synonym for the current local branch.
    """

    import hg # avoid start-up nasties
    # i18n: "remote" is a keyword
    l = getargs(x, 0, 2, _("remote takes one, two or no arguments"))

    q = '.'
    if len(l) > 0:
    # i18n: "remote" is a keyword
        q = getstring(l[0], _("remote requires a string id"))
    if q == '.':
        q = repo['.'].branch()

    dest = ''
    if len(l) > 1:
        # i18n: "remote" is a keyword
        dest = getstring(l[1], _("remote requires a repository path"))
    dest = repo.ui.expandpath(dest or 'default')
    dest, branches = hg.parseurl(dest)
    revs, checkout = hg.addbranchrevs(repo, repo, branches, [])
    if revs:
        revs = [repo.lookup(rev) for rev in revs]
    other = hg.peer(repo, {}, dest)
    n = other.lookup(q)
    if n in repo:
        r = repo[n].rev()
        if r in subset:
            return [r]
    return []

def removes(repo, subset, x):
    """``removes(pattern)``
    Changesets which remove files matching pattern.
    """
    # i18n: "removes" is a keyword
    pat = getstring(x, _("removes requires a pattern"))
    return checkstatus(repo, subset, pat, 2)

def rev(repo, subset, x):
    """``rev(number)``
    Revision with the given numeric identifier.
    """
    # i18n: "rev" is a keyword
    l = getargs(x, 1, 1, _("rev requires one argument"))
    try:
        # i18n: "rev" is a keyword
        l = int(getstring(l[0], _("rev requires a number")))
    except (TypeError, ValueError):
        # i18n: "rev" is a keyword
        raise error.ParseError(_("rev expects a number"))
    return [r for r in subset if r == l]

def matching(repo, subset, x):
    """``matching(revision [, field])``
    Changesets in which a given set of fields match the set of fields in the
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
    l = getargs(x, 1, 2, _("matching takes 1 or 2 arguments"))

    revs = getset(repo, repo.changelog, l[0])

    fieldlist = ['metadata']
    if len(l) > 1:
            fieldlist = getstring(l[1],
                # i18n: "matching" is a keyword
                _("matching requires a string "
                "as its second argument")).split()

    # Make sure that there are no repeated fields,
    # expand the 'special' 'metadata' field type
    # and check the 'files' whenever we check the 'diff'
    fields = []
    for field in fieldlist:
        if field == 'metadata':
            fields += ['user', 'description', 'date']
        elif field == 'diff':
            # a revision matching the diff must also match the files
            # since matching the diff is very costly, make sure to
            # also match the files first
            fields += ['files', 'diff']
        else:
            if field == 'author':
                field = 'user'
            fields.append(field)
    fields = set(fields)
    if 'summary' in fields and 'description' in fields:
        # If a revision matches its description it also matches its summary
        fields.discard('summary')

    # We may want to match more than one field
    # Not all fields take the same amount of time to be matched
    # Sort the selected fields in order of increasing matching cost
    fieldorder = ['phase', 'parents', 'user', 'date', 'branch', 'summary',
        'files', 'description', 'substate', 'diff']
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
        'user': lambda r: repo[r].user(),
        'branch': lambda r: repo[r].branch(),
        'date': lambda r: repo[r].date(),
        'description': lambda r: repo[r].description(),
        'files': lambda r: repo[r].files(),
        'parents': lambda r: repo[r].parents(),
        'phase': lambda r: repo[r].phase(),
        'substate': lambda r: repo[r].substate,
        'summary': lambda r: repo[r].description().splitlines()[0],
        'diff': lambda r: list(repo[r].diff(git=True),)
    }
    for info in fields:
        getfield = _funcs.get(info, None)
        if getfield is None:
            raise error.ParseError(
                # i18n: "matching" is a keyword
                _("unexpected field name passed to matching: %s") % info)
        getfieldfuncs.append(getfield)
    # convert the getfield array of functions into a "getinfo" function
    # which returns an array of field values (or a single value if there
    # is only one field to match)
    getinfo = lambda r: [f(r) for f in getfieldfuncs]

    matches = set()
    for rev in revs:
        target = getinfo(rev)
        for r in subset:
            match = True
            for n, f in enumerate(getfieldfuncs):
                if target[n] != f(r):
                    match = False
                    break
            if match:
                matches.add(r)
    return [r for r in subset if r in matches]

def reverse(repo, subset, x):
    """``reverse(set)``
    Reverse order of set.
    """
    l = getset(repo, subset, x)
    if not isinstance(l, list):
        l = list(l)
    l.reverse()
    return l

def roots(repo, subset, x):
    """``roots(set)``
    Changesets in set with no parent changeset in set.
    """
    s = set(getset(repo, repo.changelog, x))
    subset = [r for r in subset if r in s]
    cs = _children(repo, subset, s)
    return [r for r in subset if r not in cs]

def secret(repo, subset, x):
    """``secret()``
    Changeset in secret phase."""
    # i18n: "secret" is a keyword
    getargs(x, 0, 0, _("secret takes no arguments"))
    pc = repo._phasecache
    return [r for r in subset if pc.phase(repo, r) == phases.secret]

def sort(repo, subset, x):
    """``sort(set[, [-]key...])``
    Sort set by keys. The default sort order is ascending, specify a key
    as ``-key`` to sort in descending order.

    The keys can be:

    - ``rev`` for the revision number,
    - ``branch`` for the branch name,
    - ``desc`` for the commit message (description),
    - ``user`` for user name (``author`` can be used as an alias),
    - ``date`` for the commit date
    """
    # i18n: "sort" is a keyword
    l = getargs(x, 1, 2, _("sort requires one or two arguments"))
    keys = "rev"
    if len(l) == 2:
        # i18n: "sort" is a keyword
        keys = getstring(l[1], _("sort spec must be a string"))

    s = l[0]
    keys = keys.split()
    l = []
    def invert(s):
        return "".join(chr(255 - ord(c)) for c in s)
    for r in getset(repo, subset, s):
        c = repo[r]
        e = []
        for k in keys:
            if k == 'rev':
                e.append(r)
            elif k == '-rev':
                e.append(-r)
            elif k == 'branch':
                e.append(c.branch())
            elif k == '-branch':
                e.append(invert(c.branch()))
            elif k == 'desc':
                e.append(c.description())
            elif k == '-desc':
                e.append(invert(c.description()))
            elif k in 'user author':
                e.append(c.user())
            elif k in '-user -author':
                e.append(invert(c.user()))
            elif k == 'date':
                e.append(c.date()[0])
            elif k == '-date':
                e.append(-c.date()[0])
            else:
                raise error.ParseError(_("unknown sort key %r") % k)
        e.append(r)
        l.append(e)
    l.sort()
    return [e[-1] for e in l]

def _stringmatcher(pattern):
    """
    accepts a string, possibly starting with 're:' or 'literal:' prefix.
    returns the matcher name, pattern, and matcher function.
    missing or unknown prefixes are treated as literal matches.

    helper for tests:
    >>> def test(pattern, *tests):
    ...     kind, pattern, matcher = _stringmatcher(pattern)
    ...     return (kind, pattern, [bool(matcher(t)) for t in tests])

    exact matching (no prefix):
    >>> test('abcdefg', 'abc', 'def', 'abcdefg')
    ('literal', 'abcdefg', [False, False, True])

    regex matching ('re:' prefix)
    >>> test('re:a.+b', 'nomatch', 'fooadef', 'fooadefbar')
    ('re', 'a.+b', [False, False, True])

    force exact matches ('literal:' prefix)
    >>> test('literal:re:foobar', 'foobar', 're:foobar')
    ('literal', 're:foobar', [False, True])

    unknown prefixes are ignored and treated as literals
    >>> test('foo:bar', 'foo', 'bar', 'foo:bar')
    ('literal', 'foo:bar', [False, False, True])
    """
    if pattern.startswith('re:'):
        pattern = pattern[3:]
        try:
            regex = re.compile(pattern)
        except re.error, e:
            raise error.ParseError(_('invalid regular expression: %s')
                                   % e)
        return 're', pattern, regex.search
    elif pattern.startswith('literal:'):
        pattern = pattern[8:]
    return 'literal', pattern, pattern.__eq__

def _substringmatcher(pattern):
    kind, pattern, matcher = _stringmatcher(pattern)
    if kind == 'literal':
        matcher = lambda s: pattern in s
    return kind, pattern, matcher

def tag(repo, subset, x):
    """``tag([name])``
    The specified tag by name, or all tagged revisions if no name is given.
    """
    # i18n: "tag" is a keyword
    args = getargs(x, 0, 1, _("tag takes one or no arguments"))
    cl = repo.changelog
    if args:
        pattern = getstring(args[0],
                            # i18n: "tag" is a keyword
                            _('the argument to tag must be a string'))
        kind, pattern, matcher = _stringmatcher(pattern)
        if kind == 'literal':
            # avoid resolving all tags
            tn = repo._tagscache.tags.get(pattern, None)
            if tn is None:
                raise util.Abort(_("tag '%s' does not exist") % pattern)
            s = set([repo[tn].rev()])
        else:
            s = set([cl.rev(n) for t, n in repo.tagslist() if matcher(t)])
    else:
        s = set([cl.rev(n) for t, n in repo.tagslist() if t != 'tip'])
    return [r for r in subset if r in s]

def tagged(repo, subset, x):
    return tag(repo, subset, x)

def unstable(repo, subset, x):
    """``unstable()``
    Non-obsolete changesets with obsolete ancestors.
    """
    # i18n: "unstable" is a keyword
    getargs(x, 0, 0, _("unstable takes no arguments"))
    unstables = obsmod.getrevs(repo, 'unstable')
    return [r for r in subset if r in unstables]


def user(repo, subset, x):
    """``user(string)``
    User name contains string. The match is case-insensitive.

    If `string` starts with `re:`, the remainder of the string is treated as
    a regular expression. To match a user that actually contains `re:`, use
    the prefix `literal:`.
    """
    return author(repo, subset, x)

# for internal use
def _list(repo, subset, x):
    s = getstring(x, "internal error")
    if not s:
        return []
    if not isinstance(subset, set):
        subset = set(subset)
    ls = [repo[r].rev() for r in s.split('\0')]
    return [r for r in ls if r in subset]

symbols = {
    "adds": adds,
    "all": getall,
    "ancestor": ancestor,
    "ancestors": ancestors,
    "_firstancestors": _firstancestors,
    "author": author,
    "bisect": bisect,
    "bisected": bisected,
    "bookmark": bookmark,
    "branch": branch,
    "branchpoint": branchpoint,
    "bumped": bumped,
    "bundle": bundle,
    "children": children,
    "closed": closed,
    "contains": contains,
    "converted": converted,
    "date": date,
    "desc": desc,
    "descendants": descendants,
    "_firstdescendants": _firstdescendants,
    "destination": destination,
    "divergent": divergent,
    "draft": draft,
    "extinct": extinct,
    "extra": extra,
    "file": hasfile,
    "filelog": filelog,
    "first": first,
    "follow": follow,
    "_followfirst": _followfirst,
    "grep": grep,
    "head": head,
    "heads": heads,
    "hidden": hidden,
    "id": node_,
    "keyword": keyword,
    "last": last,
    "limit": limit,
    "_matchfiles": _matchfiles,
    "max": maxrev,
    "merge": merge,
    "min": minrev,
    "modifies": modifies,
    "obsolete": obsolete,
    "origin": origin,
    "outgoing": outgoing,
    "p1": p1,
    "p2": p2,
    "parents": parents,
    "present": present,
    "public": public,
    "remote": remote,
    "removes": removes,
    "rev": rev,
    "reverse": reverse,
    "roots": roots,
    "sort": sort,
    "secret": secret,
    "matching": matching,
    "tag": tag,
    "tagged": tagged,
    "user": user,
    "unstable": unstable,
    "_list": _list,
}

methods = {
    "range": rangeset,
    "dagrange": dagrange,
    "string": stringset,
    "symbol": symbolset,
    "and": andset,
    "or": orset,
    "not": notset,
    "list": listset,
    "func": func,
    "ancestor": ancestorspec,
    "parent": parentspec,
    "parentpost": p1,
}

def optimize(x, small):
    if x is None:
        return 0, x

    smallbonus = 1
    if small:
        smallbonus = .5

    op = x[0]
    if op == 'minus':
        return optimize(('and', x[1], ('not', x[2])), small)
    elif op == 'dagrangepre':
        return optimize(('func', ('symbol', 'ancestors'), x[1]), small)
    elif op == 'dagrangepost':
        return optimize(('func', ('symbol', 'descendants'), x[1]), small)
    elif op == 'rangepre':
        return optimize(('range', ('string', '0'), x[1]), small)
    elif op == 'rangepost':
        return optimize(('range', x[1], ('string', 'tip')), small)
    elif op == 'negate':
        return optimize(('string',
                         '-' + getstring(x[1], _("can't negate that"))), small)
    elif op in 'string symbol negate':
        return smallbonus, x # single revisions are small
    elif op == 'and':
        wa, ta = optimize(x[1], True)
        wb, tb = optimize(x[2], True)
        w = min(wa, wb)
        if wa > wb:
            return w, (op, tb, ta)
        return w, (op, ta, tb)
    elif op == 'or':
        wa, ta = optimize(x[1], False)
        wb, tb = optimize(x[2], False)
        if wb < wa:
            wb, wa = wa, wb
        return max(wa, wb), (op, ta, tb)
    elif op == 'not':
        o = optimize(x[1], not small)
        return o[0], (op, o[1])
    elif op == 'parentpost':
        o = optimize(x[1], small)
        return o[0], (op, o[1])
    elif op == 'group':
        return optimize(x[1], small)
    elif op in 'dagrange range list parent ancestorspec':
        if op == 'parent':
            # x^:y means (x^) : y, not x ^ (:y)
            post = ('parentpost', x[1])
            if x[2][0] == 'dagrangepre':
                return optimize(('dagrange', post, x[2][1]), small)
            elif x[2][0] == 'rangepre':
                return optimize(('range', post, x[2][1]), small)

        wa, ta = optimize(x[1], small)
        wb, tb = optimize(x[2], small)
        return wa + wb, (op, ta, tb)
    elif op == 'func':
        f = getstring(x[1], _("not a symbol"))
        wa, ta = optimize(x[2], small)
        if f in ("author branch closed date desc file grep keyword "
                 "outgoing user"):
            w = 10 # slow
        elif f in "modifies adds removes":
            w = 30 # slower
        elif f == "contains":
            w = 100 # very slow
        elif f == "ancestor":
            w = 1 * smallbonus
        elif f in "reverse limit first":
            w = 0
        elif f in "sort":
            w = 10 # assume most sorts look at changelog
        else:
            w = 1
        return w + wa, (op, x[1], ta)
    return 1, x

_aliasarg = ('func', ('symbol', '_aliasarg'))
def _getaliasarg(tree):
    """If tree matches ('func', ('symbol', '_aliasarg'), ('string', X))
    return X, None otherwise.
    """
    if (len(tree) == 3 and tree[:2] == _aliasarg
        and tree[2][0] == 'string'):
        return tree[2][1]
    return None

def _checkaliasarg(tree, known=None):
    """Check tree contains no _aliasarg construct or only ones which
    value is in known. Used to avoid alias placeholders injection.
    """
    if isinstance(tree, tuple):
        arg = _getaliasarg(tree)
        if arg is not None and (not known or arg not in known):
            raise error.ParseError(_("not a function: %s") % '_aliasarg')
        for t in tree:
            _checkaliasarg(t, known)

class revsetalias(object):
    funcre = re.compile('^([^(]+)\(([^)]+)\)$')
    args = None

    def __init__(self, name, value):
        '''Aliases like:

        h = heads(default)
        b($1) = ancestors($1) - ancestors(default)
        '''
        m = self.funcre.search(name)
        if m:
            self.name = m.group(1)
            self.tree = ('func', ('symbol', m.group(1)))
            self.args = [x.strip() for x in m.group(2).split(',')]
            for arg in self.args:
                # _aliasarg() is an unknown symbol only used separate
                # alias argument placeholders from regular strings.
                value = value.replace(arg, '_aliasarg(%r)' % (arg,))
        else:
            self.name = name
            self.tree = ('symbol', name)

        self.replacement, pos = parse(value)
        if pos != len(value):
            raise error.ParseError(_('invalid token'), pos)
        # Check for placeholder injection
        _checkaliasarg(self.replacement, self.args)

def _getalias(aliases, tree):
    """If tree looks like an unexpanded alias, return it. Return None
    otherwise.
    """
    if isinstance(tree, tuple) and tree:
        if tree[0] == 'symbol' and len(tree) == 2:
            name = tree[1]
            alias = aliases.get(name)
            if alias and alias.args is None and alias.tree == tree:
                return alias
        if tree[0] == 'func' and len(tree) > 1:
            if tree[1][0] == 'symbol' and len(tree[1]) == 2:
                name = tree[1][1]
                alias = aliases.get(name)
                if alias and alias.args is not None and alias.tree == tree[:2]:
                    return alias
    return None

def _expandargs(tree, args):
    """Replace _aliasarg instances with the substitution value of the
    same name in args, recursively.
    """
    if not tree or not isinstance(tree, tuple):
        return tree
    arg = _getaliasarg(tree)
    if arg is not None:
        return args[arg]
    return tuple(_expandargs(t, args) for t in tree)

def _expandaliases(aliases, tree, expanding, cache):
    """Expand aliases in tree, recursively.

    'aliases' is a dictionary mapping user defined aliases to
    revsetalias objects.
    """
    if not isinstance(tree, tuple):
        # Do not expand raw strings
        return tree
    alias = _getalias(aliases, tree)
    if alias is not None:
        if alias in expanding:
            raise error.ParseError(_('infinite expansion of revset alias "%s" '
                                     'detected') % alias.name)
        expanding.append(alias)
        if alias.name not in cache:
            cache[alias.name] = _expandaliases(aliases, alias.replacement,
                                               expanding, cache)
        result = cache[alias.name]
        expanding.pop()
        if alias.args is not None:
            l = getlist(tree[2])
            if len(l) != len(alias.args):
                raise error.ParseError(
                    _('invalid number of arguments: %s') % len(l))
            l = [_expandaliases(aliases, a, [], cache) for a in l]
            result = _expandargs(result, dict(zip(alias.args, l)))
    else:
        result = tuple(_expandaliases(aliases, t, expanding, cache)
                       for t in tree)
    return result

def findaliases(ui, tree):
    _checkaliasarg(tree)
    aliases = {}
    for k, v in ui.configitems('revsetalias'):
        alias = revsetalias(k, v)
        aliases[alias.name] = alias
    return _expandaliases(aliases, tree, [], {})

parse = parser.parser(tokenize, elements).parse

def match(ui, spec):
    if not spec:
        raise error.ParseError(_("empty query"))
    tree, pos = parse(spec)
    if (pos != len(spec)):
        raise error.ParseError(_("invalid token"), pos)
    if ui:
        tree = findaliases(ui, tree)
    weight, tree = optimize(tree, True)
    def mfunc(repo, subset):
        return getset(repo, subset, tree)
    return mfunc

def formatspec(expr, *args):
    '''
    This is a convenience function for using revsets internally, and
    escapes arguments appropriately. Aliases are intentionally ignored
    so that intended expression behavior isn't accidentally subverted.

    Supported arguments:

    %r = revset expression, parenthesized
    %d = int(arg), no quoting
    %s = string(arg), escaped and single-quoted
    %b = arg.branch(), escaped and single-quoted
    %n = hex(arg), single-quoted
    %% = a literal '%'

    Prefixing the type with 'l' specifies a parenthesized list of that type.

    >>> formatspec('%r:: and %lr', '10 or 11', ("this()", "that()"))
    '(10 or 11):: and ((this()) or (that()))'
    >>> formatspec('%d:: and not %d::', 10, 20)
    '10:: and not 20::'
    >>> formatspec('%ld or %ld', [], [1])
    "_list('') or 1"
    >>> formatspec('keyword(%s)', 'foo\\xe9')
    "keyword('foo\\\\xe9')"
    >>> b = lambda: 'default'
    >>> b.branch = b
    >>> formatspec('branch(%b)', b)
    "branch('default')"
    >>> formatspec('root(%ls)', ['a', 'b', 'c', 'd'])
    "root(_list('a\\x00b\\x00c\\x00d'))"
    '''

    def quote(s):
        return repr(str(s))

    def argtype(c, arg):
        if c == 'd':
            return str(int(arg))
        elif c == 's':
            return quote(arg)
        elif c == 'r':
            parse(arg) # make sure syntax errors are confined
            return '(%s)' % arg
        elif c == 'n':
            return quote(node.hex(arg))
        elif c == 'b':
            return quote(arg.branch())

    def listexp(s, t):
        l = len(s)
        if l == 0:
            return "_list('')"
        elif l == 1:
            return argtype(t, s[0])
        elif t == 'd':
            return "_list('%s')" % "\0".join(str(int(a)) for a in s)
        elif t == 's':
            return "_list('%s')" % "\0".join(s)
        elif t == 'n':
            return "_list('%s')" % "\0".join(node.hex(a) for a in s)
        elif t == 'b':
            return "_list('%s')" % "\0".join(a.branch() for a in s)

        m = l // 2
        return '(%s or %s)' % (listexp(s[:m], t), listexp(s[m:], t))

    ret = ''
    pos = 0
    arg = 0
    while pos < len(expr):
        c = expr[pos]
        if c == '%':
            pos += 1
            d = expr[pos]
            if d == '%':
                ret += d
            elif d in 'dsnbr':
                ret += argtype(d, args[arg])
                arg += 1
            elif d == 'l':
                # a list of some type
                pos += 1
                d = expr[pos]
                ret += listexp(list(args[arg]), d)
                arg += 1
            else:
                raise util.Abort('unexpected revspec format character %s' % d)
        else:
            ret += c
        pos += 1

    return ret

def prettyformat(tree):
    def _prettyformat(tree, level, lines):
        if not isinstance(tree, tuple) or tree[0] in ('string', 'symbol'):
            lines.append((level, str(tree)))
        else:
            lines.append((level, '(%s' % tree[0]))
            for s in tree[1:]:
                _prettyformat(s, level + 1, lines)
            lines[-1:] = [(lines[-1][0], lines[-1][1] + ')')]

    lines = []
    _prettyformat(tree, 0, lines)
    output = '\n'.join(('  '*l + s) for l, s in lines)
    return output

# tell hggettext to extract docstrings from these functions:
i18nfunctions = symbols.values()
