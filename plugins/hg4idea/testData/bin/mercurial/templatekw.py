# templatekw.py - common changeset template keywords
#
# Copyright 2005-2009 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from .i18n import _
from .node import (
    hex,
    wdirrev,
)

from . import (
    diffutil,
    encoding,
    error,
    hbisect,
    i18n,
    obsutil,
    patch,
    pycompat,
    registrar,
    scmutil,
    templateutil,
    util,
)
from .utils import (
    stringutil,
    urlutil,
)

_hybrid = templateutil.hybrid
hybriddict = templateutil.hybriddict
hybridlist = templateutil.hybridlist
compatdict = templateutil.compatdict
compatlist = templateutil.compatlist
_showcompatlist = templateutil._showcompatlist


def getlatesttags(context, mapping, pattern=None):
    '''return date, distance and name for the latest tag of rev'''
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    cache = context.resource(mapping, b'cache')

    cachename = b'latesttags'
    if pattern is not None:
        cachename += b'-' + pattern
        match = stringutil.stringmatcher(pattern)[2]
    else:
        match = util.always

    if cachename not in cache:
        # Cache mapping from rev to a tuple with tag date, tag
        # distance and tag name
        cache[cachename] = {-1: (0, 0, [b'null'])}
    latesttags = cache[cachename]

    rev = ctx.rev()
    todo = [rev]
    while todo:
        rev = todo.pop()
        if rev in latesttags:
            continue
        ctx = repo[rev]
        tags = [
            t
            for t in ctx.tags()
            if (repo.tagtype(t) and repo.tagtype(t) != b'local' and match(t))
        ]
        if tags:
            latesttags[rev] = ctx.date()[0], 0, [t for t in sorted(tags)]
            continue
        try:
            ptags = [latesttags[p.rev()] for p in ctx.parents()]
            if len(ptags) > 1:
                if ptags[0][2] == ptags[1][2]:
                    # The tuples are laid out so the right one can be found by
                    # comparison in this case.
                    pdate, pdist, ptag = max(ptags)
                else:

                    def key(x):
                        tag = x[2][0]
                        if ctx.rev() is None:
                            # only() doesn't support wdir
                            prevs = [c.rev() for c in ctx.parents()]
                            changes = repo.revs(b'only(%ld, %s)', prevs, tag)
                            changessincetag = len(changes) + 1
                        else:
                            changes = repo.revs(b'only(%d, %s)', ctx.rev(), tag)
                            changessincetag = len(changes)
                        # Smallest number of changes since tag wins. Date is
                        # used as tiebreaker.
                        return [-changessincetag, x[0]]

                    pdate, pdist, ptag = max(ptags, key=key)
            else:
                pdate, pdist, ptag = ptags[0]
        except KeyError:
            # Cache miss - recurse
            todo.append(rev)
            todo.extend(p.rev() for p in ctx.parents())
            continue
        latesttags[rev] = pdate, pdist + 1, ptag
    return latesttags[rev]


def getlogcolumns():
    """Return a dict of log column labels"""
    _ = pycompat.identity  # temporarily disable gettext
    # i18n: column positioning for "hg log"
    columns = _(
        b'bookmark:    %s\n'
        b'branch:      %s\n'
        b'changeset:   %s\n'
        b'copies:      %s\n'
        b'date:        %s\n'
        b'extra:       %s=%s\n'
        b'files+:      %s\n'
        b'files-:      %s\n'
        b'files:       %s\n'
        b'instability: %s\n'
        b'manifest:    %s\n'
        b'obsolete:    %s\n'
        b'parent:      %s\n'
        b'phase:       %s\n'
        b'summary:     %s\n'
        b'tag:         %s\n'
        b'user:        %s\n'
    )
    return dict(
        zip(
            [s.split(b':', 1)[0] for s in columns.splitlines()],
            i18n._(columns).splitlines(True),
        )
    )


# basic internal templates
_changeidtmpl = b'{rev}:{node|formatnode}'

# default templates internally used for rendering of lists
defaulttempl = {
    b'parent': _changeidtmpl + b' ',
    b'manifest': _changeidtmpl,
    b'file_copy': b'{name} ({source})',
    b'envvar': b'{key}={value}',
    b'extra': b'{key}={value|stringescape}',
}
# filecopy is preserved for compatibility reasons
defaulttempl[b'filecopy'] = defaulttempl[b'file_copy']

# keywords are callables (see registrar.templatekeyword for details)
keywords = {}
templatekeyword = registrar.templatekeyword(keywords)


@templatekeyword(b'author', requires={b'ctx'})
def showauthor(context, mapping):
    """Alias for ``{user}``"""
    return showuser(context, mapping)


@templatekeyword(b'bisect', requires={b'repo', b'ctx'})
def showbisect(context, mapping):
    """String. The changeset bisection status."""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    return hbisect.label(repo, ctx.node())


@templatekeyword(b'branch', requires={b'ctx'})
def showbranch(context, mapping):
    """String. The name of the branch on which the changeset was
    committed.
    """
    ctx = context.resource(mapping, b'ctx')
    return ctx.branch()


@templatekeyword(b'branches', requires={b'ctx'})
def showbranches(context, mapping):
    """List of strings. The name of the branch on which the
    changeset was committed. Will be empty if the branch name was
    default. (DEPRECATED)
    """
    ctx = context.resource(mapping, b'ctx')
    branch = ctx.branch()
    if branch != b'default':
        return compatlist(
            context, mapping, b'branch', [branch], plural=b'branches'
        )
    return compatlist(context, mapping, b'branch', [], plural=b'branches')


@templatekeyword(b'bookmarks', requires={b'repo', b'ctx'})
def showbookmarks(context, mapping):
    """List of strings. Any bookmarks associated with the
    changeset. Also sets 'active', the name of the active bookmark.
    """
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    bookmarks = ctx.bookmarks()
    active = repo._activebookmark
    makemap = lambda v: {b'bookmark': v, b'active': active, b'current': active}
    f = _showcompatlist(context, mapping, b'bookmark', bookmarks)
    return _hybrid(f, bookmarks, makemap, pycompat.identity)


@templatekeyword(b'children', requires={b'ctx'})
def showchildren(context, mapping):
    """List of strings. The children of the changeset."""
    ctx = context.resource(mapping, b'ctx')
    childrevs = [b'%d:%s' % (cctx.rev(), cctx) for cctx in ctx.children()]
    return compatlist(
        context, mapping, b'children', childrevs, element=b'child'
    )


# Deprecated, but kept alive for help generation a purpose.
@templatekeyword(b'currentbookmark', requires={b'repo', b'ctx'})
def showcurrentbookmark(context, mapping):
    """String. The active bookmark, if it is associated with the changeset.
    (DEPRECATED)"""
    return showactivebookmark(context, mapping)


@templatekeyword(b'activebookmark', requires={b'repo', b'ctx'})
def showactivebookmark(context, mapping):
    """String. The active bookmark, if it is associated with the changeset."""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    active = repo._activebookmark
    if active and active in ctx.bookmarks():
        return active
    return b''


@templatekeyword(b'date', requires={b'ctx'})
def showdate(context, mapping):
    """Date information. The date when the changeset was committed."""
    ctx = context.resource(mapping, b'ctx')
    # the default string format is '<float(unixtime)><tzoffset>' because
    # python-hglib splits date at decimal separator.
    return templateutil.date(ctx.date(), showfmt=b'%d.0%d')


@templatekeyword(b'desc', requires={b'ctx'})
def showdescription(context, mapping):
    """String. The text of the changeset description."""
    ctx = context.resource(mapping, b'ctx')
    s = ctx.description()
    if isinstance(s, encoding.localstr):
        # try hard to preserve utf-8 bytes
        return encoding.tolocal(encoding.fromlocal(s).strip())
    elif isinstance(s, encoding.safelocalstr):
        return encoding.safelocalstr(s.strip())
    else:
        return s.strip()


@templatekeyword(b'diffstat', requires={b'ui', b'ctx'})
def showdiffstat(context, mapping):
    """String. Statistics of changes with the following format:
    "modified files: +added/-removed lines"
    """
    ui = context.resource(mapping, b'ui')
    ctx = context.resource(mapping, b'ctx')
    diffopts = diffutil.diffallopts(ui, {b'noprefix': False})
    diff = ctx.diff(diffutil.diff_parent(ctx), opts=diffopts)
    stats = patch.diffstatdata(util.iterlines(diff))
    maxname, maxtotal, adds, removes, binary = patch.diffstatsum(stats)
    return b'%d: +%d/-%d' % (len(stats), adds, removes)


@templatekeyword(b'envvars', requires={b'ui'})
def showenvvars(context, mapping):
    """A dictionary of environment variables. (EXPERIMENTAL)"""
    ui = context.resource(mapping, b'ui')
    env = ui.exportableenviron()
    env = util.sortdict((k, env[k]) for k in sorted(env))
    return compatdict(context, mapping, b'envvar', env, plural=b'envvars')


@templatekeyword(b'extras', requires={b'ctx'})
def showextras(context, mapping):
    """List of dicts with key, value entries of the 'extras'
    field of this changeset."""
    ctx = context.resource(mapping, b'ctx')
    extras = ctx.extra()
    extras = util.sortdict((k, extras[k]) for k in sorted(extras))
    makemap = lambda k: {b'key': k, b'value': extras[k]}
    c = [makemap(k) for k in extras]
    f = _showcompatlist(context, mapping, b'extra', c, plural=b'extras')
    return _hybrid(
        f,
        extras,
        makemap,
        lambda k: b'%s=%s' % (k, stringutil.escapestr(extras[k])),
    )


@templatekeyword(b'_fast_rank', requires={b'ctx'})
def fast_rank(context, mapping):
    """the rank of a changeset if cached

    The rank of a revision is the size of the sub-graph it defines as a head.
    Equivalently, the rank of a revision `r` is the size of the set
    `ancestors(r)`, `r` included.
    """
    ctx = context.resource(mapping, b'ctx')
    rank = ctx.fast_rank()
    if rank is None:
        return None
    return b"%d" % rank


def _getfilestatus(context, mapping, listall=False):
    ctx = context.resource(mapping, b'ctx')
    revcache = context.resource(mapping, b'revcache')
    if b'filestatus' not in revcache or revcache[b'filestatusall'] < listall:
        stat = ctx.p1().status(
            ctx, listignored=listall, listclean=listall, listunknown=listall
        )
        revcache[b'filestatus'] = stat
        revcache[b'filestatusall'] = listall
    return revcache[b'filestatus']


def _getfilestatusmap(context, mapping, listall=False):
    revcache = context.resource(mapping, b'revcache')
    if b'filestatusmap' not in revcache or revcache[b'filestatusall'] < listall:
        stat = _getfilestatus(context, mapping, listall=listall)
        revcache[b'filestatusmap'] = statmap = {}
        for char, files in zip(pycompat.iterbytestr(b'MAR!?IC'), stat):
            statmap.update((f, char) for f in files)
    return revcache[b'filestatusmap']  # {path: statchar}


@templatekeyword(
    b'file_copies', requires={b'repo', b'ctx', b'cache', b'revcache'}
)
def showfilecopies(context, mapping):
    """List of strings. Files copied in this changeset with
    their sources.
    """
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    cache = context.resource(mapping, b'cache')
    copies = context.resource(mapping, b'revcache').get(b'copies')
    if copies is None:
        if b'getcopies' not in cache:
            cache[b'getcopies'] = scmutil.getcopiesfn(repo)
        getcopies = cache[b'getcopies']
        copies = getcopies(ctx)
    return templateutil.compatfilecopiesdict(
        context, mapping, b'file_copy', copies
    )


# showfilecopiesswitch() displays file copies only if copy records are
# provided before calling the templater, usually with a --copies
# command line switch.
@templatekeyword(b'file_copies_switch', requires={b'revcache'})
def showfilecopiesswitch(context, mapping):
    """List of strings. Like "file_copies" but displayed
    only if the --copied switch is set.
    """
    copies = context.resource(mapping, b'revcache').get(b'copies') or []
    return templateutil.compatfilecopiesdict(
        context, mapping, b'file_copy', copies
    )


@templatekeyword(b'file_adds', requires={b'ctx', b'revcache'})
def showfileadds(context, mapping):
    """List of strings. Files added by this changeset."""
    ctx = context.resource(mapping, b'ctx')
    return templateutil.compatfileslist(
        context, mapping, b'file_add', ctx.filesadded()
    )


@templatekeyword(b'file_dels', requires={b'ctx', b'revcache'})
def showfiledels(context, mapping):
    """List of strings. Files removed by this changeset."""
    ctx = context.resource(mapping, b'ctx')
    return templateutil.compatfileslist(
        context, mapping, b'file_del', ctx.filesremoved()
    )


@templatekeyword(b'file_mods', requires={b'ctx', b'revcache'})
def showfilemods(context, mapping):
    """List of strings. Files modified by this changeset."""
    ctx = context.resource(mapping, b'ctx')
    return templateutil.compatfileslist(
        context, mapping, b'file_mod', ctx.filesmodified()
    )


@templatekeyword(b'files', requires={b'ctx'})
def showfiles(context, mapping):
    """List of strings. All files modified, added, or removed by this
    changeset.
    """
    ctx = context.resource(mapping, b'ctx')
    return templateutil.compatfileslist(context, mapping, b'file', ctx.files())


@templatekeyword(b'graphnode', requires={b'repo', b'ctx', b'cache'})
def showgraphnode(context, mapping):
    """String. The character representing the changeset node in an ASCII
    revision graph."""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    cache = context.resource(mapping, b'cache')
    return getgraphnode(repo, ctx, cache)


def getgraphnode(repo, ctx, cache):
    return getgraphnodecurrent(repo, ctx, cache) or getgraphnodesymbol(ctx)


def getgraphnodecurrent(repo, ctx, cache):
    wpnodes = repo.dirstate.parents()
    if wpnodes[1] == repo.nullid:
        wpnodes = wpnodes[:1]
    if ctx.node() in wpnodes:
        return b'@'
    else:
        merge_nodes = cache.get(b'merge_nodes')
        if merge_nodes is None:
            from . import mergestate as mergestatemod

            mergestate = mergestatemod.mergestate.read(repo)
            if mergestate.unresolvedcount():
                merge_nodes = (mergestate.local, mergestate.other)
            else:
                merge_nodes = ()
            cache[b'merge_nodes'] = merge_nodes

        if ctx.node() in merge_nodes:
            return b'%'
        return b''


def getgraphnodesymbol(ctx):
    if ctx.obsolete():
        return b'x'
    elif ctx.isunstable():
        return b'*'
    elif ctx.closesbranch():
        return b'_'
    else:
        return b'o'


@templatekeyword(b'graphwidth', requires=())
def showgraphwidth(context, mapping):
    """Integer. The width of the graph drawn by 'log --graph' or zero."""
    # just hosts documentation; should be overridden by template mapping
    return 0


@templatekeyword(b'index', requires=())
def showindex(context, mapping):
    """Integer. The current iteration of the loop. (0 indexed)"""
    # just hosts documentation; should be overridden by template mapping
    raise error.Abort(_(b"can't use index in this context"))


@templatekeyword(b'latesttag', requires={b'repo', b'ctx', b'cache'})
def showlatesttag(context, mapping):
    """List of strings. The global tags on the most recent globally
    tagged ancestor of this changeset.  If no such tags exist, the list
    consists of the single string "null".
    """
    return showlatesttags(context, mapping, None)


def showlatesttags(context, mapping, pattern):
    """helper method for the latesttag keyword and function"""
    latesttags = getlatesttags(context, mapping, pattern)

    # latesttag[0] is an implementation detail for sorting csets on different
    # branches in a stable manner- it is the date the tagged cset was created,
    # not the date the tag was created.  Therefore it isn't made visible here.
    makemap = lambda v: {
        b'changes': _showchangessincetag,
        b'distance': latesttags[1],
        b'latesttag': v,  # BC with {latesttag % '{latesttag}'}
        b'tag': v,
    }

    tags = latesttags[2]
    f = _showcompatlist(context, mapping, b'latesttag', tags, separator=b':')
    return _hybrid(f, tags, makemap, pycompat.identity)


@templatekeyword(b'latesttagdistance', requires={b'repo', b'ctx', b'cache'})
def showlatesttagdistance(context, mapping):
    """Integer. Longest path to the latest tag."""
    return getlatesttags(context, mapping)[1]


@templatekeyword(b'changessincelatesttag', requires={b'repo', b'ctx', b'cache'})
def showchangessincelatesttag(context, mapping):
    """Integer. All ancestors not in the latest tag."""
    tag = getlatesttags(context, mapping)[2][0]
    mapping = context.overlaymap(mapping, {b'tag': tag})
    return _showchangessincetag(context, mapping)


def _showchangessincetag(context, mapping):
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    offset = 0
    revs = [ctx.rev()]
    tag = context.symbol(mapping, b'tag')

    # The only() revset doesn't currently support wdir()
    if ctx.rev() is None:
        offset = 1
        revs = [p.rev() for p in ctx.parents()]

    return len(repo.revs(b'only(%ld, %s)', revs, tag)) + offset


# teach templater latesttags.changes is switched to (context, mapping) API
_showchangessincetag._requires = {b'repo', b'ctx'}


@templatekeyword(b'manifest', requires={b'repo', b'ctx'})
def showmanifest(context, mapping):
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    mnode = ctx.manifestnode()
    if mnode is None:
        mnode = repo.nodeconstants.wdirid
        mrev = wdirrev
        mhex = repo.nodeconstants.wdirhex
    else:
        mrev = repo.manifestlog.rev(mnode)
        mhex = hex(mnode)
    mapping = context.overlaymap(mapping, {b'rev': mrev, b'node': mhex})
    f = context.process(b'manifest', mapping)
    return templateutil.hybriditem(
        f, None, f, lambda x: {b'rev': mrev, b'node': mhex}
    )


@templatekeyword(b'obsfate', requires={b'ui', b'repo', b'ctx'})
def showobsfate(context, mapping):
    # this function returns a list containing pre-formatted obsfate strings.
    #
    # This function will be replaced by templates fragments when we will have
    # the verbosity templatekw available.
    succsandmarkers = showsuccsandmarkers(context, mapping)

    ui = context.resource(mapping, b'ui')
    repo = context.resource(mapping, b'repo')
    values = []

    for x in succsandmarkers.tovalue(context, mapping):
        v = obsutil.obsfateprinter(
            ui, repo, x[b'successors'], x[b'markers'], scmutil.formatchangeid
        )
        values.append(v)

    return compatlist(context, mapping, b"fate", values)


def shownames(context, mapping, namespace):
    """helper method to generate a template keyword for a namespace"""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    ns = repo.names.get(namespace)
    if ns is None:
        # namespaces.addnamespace() registers new template keyword, but
        # the registered namespace might not exist in the current repo.
        return
    names = ns.names(repo, ctx.node())
    return compatlist(
        context, mapping, ns.templatename, names, plural=namespace
    )


@templatekeyword(b'namespaces', requires={b'repo', b'ctx'})
def shownamespaces(context, mapping):
    """Dict of lists. Names attached to this changeset per
    namespace."""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')

    namespaces = util.sortdict()

    def makensmapfn(ns):
        # 'name' for iterating over namespaces, templatename for local reference
        return lambda v: {b'name': v, ns.templatename: v}

    for k, ns in repo.names.items():
        names = ns.names(repo, ctx.node())
        f = _showcompatlist(context, mapping, b'name', names)
        namespaces[k] = _hybrid(f, names, makensmapfn(ns), pycompat.identity)

    f = _showcompatlist(context, mapping, b'namespace', list(namespaces))

    def makemap(ns):
        return {
            b'namespace': ns,
            b'names': namespaces[ns],
            b'builtin': repo.names[ns].builtin,
            b'colorname': repo.names[ns].colorname,
        }

    return _hybrid(f, namespaces, makemap, pycompat.identity)


@templatekeyword(b'negrev', requires={b'repo', b'ctx'})
def shownegrev(context, mapping):
    """Integer. The repository-local changeset negative revision number,
    which counts in the opposite direction."""
    ctx = context.resource(mapping, b'ctx')
    rev = ctx.rev()
    if rev is None or rev < 0:  # wdir() or nullrev?
        return None
    repo = context.resource(mapping, b'repo')
    return rev - len(repo)


@templatekeyword(b'node', requires={b'ctx'})
def shownode(context, mapping):
    """String. The changeset identification hash, as a 40 hexadecimal
    digit string.
    """
    ctx = context.resource(mapping, b'ctx')
    return ctx.hex()


@templatekeyword(b'obsolete', requires={b'ctx'})
def showobsolete(context, mapping):
    """String. Whether the changeset is obsolete. (EXPERIMENTAL)"""
    ctx = context.resource(mapping, b'ctx')
    if ctx.obsolete():
        return b'obsolete'
    return b''


@templatekeyword(b'onelinesummary', requires={b'ui', b'ctx'})
def showonelinesummary(context, mapping):
    """String. A one-line summary for the ctx (not including trailing newline).
    The default template be overridden in command-templates.oneline-summary."""
    # Avoid cycle:
    # mercurial.cmdutil -> mercurial.templatekw -> mercurial.cmdutil
    from . import cmdutil

    ui = context.resource(mapping, b'ui')
    ctx = context.resource(mapping, b'ctx')
    return cmdutil.format_changeset_summary(ui, ctx)


@templatekeyword(b'path', requires={b'fctx'})
def showpath(context, mapping):
    """String. Repository-absolute path of the current file. (EXPERIMENTAL)"""
    fctx = context.resource(mapping, b'fctx')
    return fctx.path()


@templatekeyword(b'peerurls', requires={b'repo'})
def showpeerurls(context, mapping):
    """A dictionary of repository locations defined in the [paths] section
    of your configuration file."""
    repo = context.resource(mapping, b'repo')
    # see commands.paths() for naming of dictionary keys
    paths = repo.ui.paths
    all_paths = urlutil.list_paths(repo.ui)
    urls = util.sortdict((k, p.rawloc) for k, p in all_paths)

    def makemap(k):
        ps = paths[k]
        d = {b'name': k}
        if len(ps) == 1:
            d[b'url'] = ps[0].rawloc
            sub_opts = ps[0].suboptions.items()
            sub_opts = util.sortdict(sorted(sub_opts))
            d.update(sub_opts)
        path_dict = util.sortdict()
        for p in ps:
            sub_opts = util.sortdict(sorted(p.suboptions.items()))
            path_dict[b'url'] = p.rawloc
            path_dict.update(sub_opts)
            d[b'urls'] = [path_dict]
        return d

    def format_one(k):
        return b'%s=%s' % (k, urls[k])

    return _hybrid(None, urls, makemap, format_one)


@templatekeyword(b"predecessors", requires={b'repo', b'ctx'})
def showpredecessors(context, mapping):
    """Returns the list of the closest visible predecessors. (EXPERIMENTAL)"""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    predecessors = sorted(obsutil.closestpredecessors(repo, ctx.node()))
    predecessors = pycompat.maplist(hex, predecessors)

    return _hybrid(
        None,
        predecessors,
        lambda x: {b'ctx': repo[x]},
        lambda x: scmutil.formatchangeid(repo[x]),
    )


@templatekeyword(b'reporoot', requires={b'repo'})
def showreporoot(context, mapping):
    """String. The root directory of the current repository."""
    repo = context.resource(mapping, b'repo')
    return repo.root


@templatekeyword(b'size', requires={b'fctx'})
def showsize(context, mapping):
    """Integer. Size of the current file in bytes. (EXPERIMENTAL)"""
    fctx = context.resource(mapping, b'fctx')
    return fctx.size()


# requires 'fctx' to denote {status} depends on (ctx, path) pair
@templatekeyword(b'status', requires={b'ctx', b'fctx', b'revcache'})
def showstatus(context, mapping):
    """String. Status code of the current file. (EXPERIMENTAL)"""
    path = templateutil.runsymbol(context, mapping, b'path')
    path = templateutil.stringify(context, mapping, path)
    if not path:
        return
    statmap = _getfilestatusmap(context, mapping)
    if path not in statmap:
        statmap = _getfilestatusmap(context, mapping, listall=True)
    return statmap.get(path)


@templatekeyword(b"successorssets", requires={b'repo', b'ctx'})
def showsuccessorssets(context, mapping):
    """Returns a string of sets of successors for a changectx. Format used
    is: [ctx1, ctx2], [ctx3] if ctx has been split into ctx1 and ctx2
    while also diverged into ctx3. (EXPERIMENTAL)"""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    data = []

    if ctx.obsolete():
        ssets = obsutil.successorssets(repo, ctx.node(), closest=True)
        ssets = [[hex(n) for n in ss] for ss in ssets]

        for ss in ssets:
            h = _hybrid(
                None,
                ss,
                lambda x: {b'ctx': repo[x]},
                lambda x: scmutil.formatchangeid(repo[x]),
            )
            data.append(h)

    # Format the successorssets
    def render(d):
        return templateutil.stringify(context, mapping, d)

    def gen(data):
        yield b"; ".join(render(d) for d in data)

    return _hybrid(
        gen(data), data, lambda x: {b'successorset': x}, pycompat.identity
    )


@templatekeyword(b"succsandmarkers", requires={b'repo', b'ctx'})
def showsuccsandmarkers(context, mapping):
    """Returns a list of dict for each final successor of ctx. The dict
    contains successors node id in "successors" keys and the list of
    obs-markers from ctx to the set of successors in "markers".
    (EXPERIMENTAL)
    """
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')

    values = obsutil.successorsandmarkers(repo, ctx)

    if values is None:
        values = []

    # Format successors and markers to avoid exposing binary to templates
    data = []
    for i in values:
        # Format successors
        successors = i[b'successors']

        successors = [hex(n) for n in successors]
        successors = _hybrid(
            None,
            successors,
            lambda x: {b'ctx': repo[x]},
            lambda x: scmutil.formatchangeid(repo[x]),
        )

        # Format markers
        finalmarkers = []
        for m in i[b'markers']:
            hexprec = hex(m[0])
            hexsucs = tuple(hex(n) for n in m[1])
            hexparents = None
            if m[5] is not None:
                hexparents = tuple(hex(n) for n in m[5])
            newmarker = (hexprec, hexsucs) + m[2:5] + (hexparents,) + m[6:]
            finalmarkers.append(newmarker)

        data.append({b'successors': successors, b'markers': finalmarkers})

    return templateutil.mappinglist(data)


@templatekeyword(b'p1', requires={b'ctx'})
def showp1(context, mapping):
    """Changeset. The changeset's first parent. ``{p1.rev}`` for the revision
    number, and ``{p1.node}`` for the identification hash."""
    ctx = context.resource(mapping, b'ctx')
    return templateutil.mappingdict({b'ctx': ctx.p1()}, tmpl=_changeidtmpl)


@templatekeyword(b'p2', requires={b'ctx'})
def showp2(context, mapping):
    """Changeset. The changeset's second parent. ``{p2.rev}`` for the revision
    number, and ``{p2.node}`` for the identification hash."""
    ctx = context.resource(mapping, b'ctx')
    return templateutil.mappingdict({b'ctx': ctx.p2()}, tmpl=_changeidtmpl)


@templatekeyword(b'p1rev', requires={b'ctx'})
def showp1rev(context, mapping):
    """Integer. The repository-local revision number of the changeset's
    first parent, or -1 if the changeset has no parents. (DEPRECATED)"""
    ctx = context.resource(mapping, b'ctx')
    return ctx.p1().rev()


@templatekeyword(b'p2rev', requires={b'ctx'})
def showp2rev(context, mapping):
    """Integer. The repository-local revision number of the changeset's
    second parent, or -1 if the changeset has no second parent. (DEPRECATED)"""
    ctx = context.resource(mapping, b'ctx')
    return ctx.p2().rev()


@templatekeyword(b'p1node', requires={b'ctx'})
def showp1node(context, mapping):
    """String. The identification hash of the changeset's first parent,
    as a 40 digit hexadecimal string. If the changeset has no parents, all
    digits are 0. (DEPRECATED)"""
    ctx = context.resource(mapping, b'ctx')
    return ctx.p1().hex()


@templatekeyword(b'p2node', requires={b'ctx'})
def showp2node(context, mapping):
    """String. The identification hash of the changeset's second
    parent, as a 40 digit hexadecimal string. If the changeset has no second
    parent, all digits are 0. (DEPRECATED)"""
    ctx = context.resource(mapping, b'ctx')
    return ctx.p2().hex()


@templatekeyword(b'parents', requires={b'repo', b'ctx'})
def showparents(context, mapping):
    """List of strings. The parents of the changeset in "rev:node"
    format. If the changeset has only one "natural" parent (the predecessor
    revision) nothing is shown."""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    pctxs = scmutil.meaningfulparents(repo, ctx)
    prevs = [p.rev() for p in pctxs]
    parents = [
        [(b'rev', p.rev()), (b'node', p.hex()), (b'phase', p.phasestr())]
        for p in pctxs
    ]
    f = _showcompatlist(context, mapping, b'parent', parents)
    return _hybrid(
        f,
        prevs,
        lambda x: {b'ctx': repo[x]},
        lambda x: scmutil.formatchangeid(repo[x]),
        keytype=int,
    )


@templatekeyword(b'phase', requires={b'ctx'})
def showphase(context, mapping):
    """String. The changeset phase name."""
    ctx = context.resource(mapping, b'ctx')
    return ctx.phasestr()


@templatekeyword(b'phaseidx', requires={b'ctx'})
def showphaseidx(context, mapping):
    """Integer. The changeset phase index. (ADVANCED)"""
    ctx = context.resource(mapping, b'ctx')
    return ctx.phase()


@templatekeyword(b'rev', requires={b'ctx'})
def showrev(context, mapping):
    """Integer. The repository-local changeset revision number."""
    ctx = context.resource(mapping, b'ctx')
    return scmutil.intrev(ctx)


@templatekeyword(b'subrepos', requires={b'ctx'})
def showsubrepos(context, mapping):
    """List of strings. Updated subrepositories in the changeset."""
    ctx = context.resource(mapping, b'ctx')
    substate = ctx.substate
    if not substate:
        return compatlist(context, mapping, b'subrepo', [])
    psubstate = ctx.p1().substate or {}
    subrepos = []
    for sub in substate:
        if sub not in psubstate or substate[sub] != psubstate[sub]:
            subrepos.append(sub)  # modified or newly added in ctx
    for sub in psubstate:
        if sub not in substate:
            subrepos.append(sub)  # removed in ctx
    return compatlist(context, mapping, b'subrepo', sorted(subrepos))


# don't remove "showtags" definition, even though namespaces will put
# a helper function for "tags" keyword into "keywords" map automatically,
# because online help text is built without namespaces initialization
@templatekeyword(b'tags', requires={b'repo', b'ctx'})
def showtags(context, mapping):
    """List of strings. Any tags associated with the changeset."""
    return shownames(context, mapping, b'tags')


@templatekeyword(b'termwidth', requires={b'ui'})
def showtermwidth(context, mapping):
    """Integer. The width of the current terminal."""
    ui = context.resource(mapping, b'ui')
    return ui.termwidth()


@templatekeyword(b'user', requires={b'ctx'})
def showuser(context, mapping):
    """String. The unmodified author of the changeset."""
    ctx = context.resource(mapping, b'ctx')
    return ctx.user()


@templatekeyword(b'instabilities', requires={b'ctx'})
def showinstabilities(context, mapping):
    """List of strings. Evolution instabilities affecting the changeset.
    (EXPERIMENTAL)
    """
    ctx = context.resource(mapping, b'ctx')
    return compatlist(
        context,
        mapping,
        b'instability',
        ctx.instabilities(),
        plural=b'instabilities',
    )


@templatekeyword(b'verbosity', requires={b'ui'})
def showverbosity(context, mapping):
    """String. The current output verbosity in 'debug', 'quiet', 'verbose',
    or ''."""
    ui = context.resource(mapping, b'ui')
    # see logcmdutil.changesettemplater for priority of these flags
    if ui.debugflag:
        return b'debug'
    elif ui.quiet:
        return b'quiet'
    elif ui.verbose:
        return b'verbose'
    return b''


@templatekeyword(b'whyunstable', requires={b'repo', b'ctx'})
def showwhyunstable(context, mapping):
    """List of dicts explaining all instabilities of a changeset.
    (EXPERIMENTAL)
    """
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')

    def formatnode(ctx):
        return b'%s (%s)' % (scmutil.formatchangeid(ctx), ctx.phasestr())

    entries = obsutil.whyunstable(repo, ctx)

    for entry in entries:
        if entry.get(b'divergentnodes'):
            dnodes = entry[b'divergentnodes']
            dnhybrid = _hybrid(
                None,
                [dnode.hex() for dnode in dnodes],
                lambda x: {b'ctx': repo[x]},
                lambda x: formatnode(repo[x]),
            )
            entry[b'divergentnodes'] = dnhybrid

    tmpl = (
        b'{instability}:{if(divergentnodes, " ")}{divergentnodes} '
        b'{reason} {node|short}'
    )
    return templateutil.mappinglist(entries, tmpl=tmpl, sep=b'\n')


def loadkeyword(ui, extname, registrarobj):
    """Load template keyword from specified registrarobj"""
    for name, func in registrarobj._table.items():
        keywords[name] = func


# tell hggettext to extract docstrings from these functions:
i18nfunctions = keywords.values()
