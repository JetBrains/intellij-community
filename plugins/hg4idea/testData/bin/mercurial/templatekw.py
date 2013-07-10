# templatekw.py - common changeset template keywords
#
# Copyright 2005-2009 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import hex
import patch, util, error
import hbisect

# This helper class allows us to handle both:
#  "{files}" (legacy command-line-specific list hack) and
#  "{files % '{file}\n'}" (hgweb-style with inlining and function support)

class _hybrid(object):
    def __init__(self, gen, values, joinfmt=None):
        self.gen = gen
        self.values = values
        if joinfmt:
            self.joinfmt = joinfmt
        else:
            self.joinfmt = lambda x: x.values()[0]
    def __iter__(self):
        return self.gen
    def __call__(self):
        for x in self.values:
            yield x

def showlist(name, values, plural=None, element=None, **args):
    if not element:
        element = name
    f = _showlist(name, values, plural, **args)
    return _hybrid(f, [{element: x} for x in values])

def _showlist(name, values, plural=None, **args):
    '''expand set of values.
    name is name of key in template map.
    values is list of strings or dicts.
    plural is plural of name, if not simply name + 's'.

    expansion works like this, given name 'foo'.

    if values is empty, expand 'no_foos'.

    if 'foo' not in template map, return values as a string,
    joined by space.

    expand 'start_foos'.

    for each value, expand 'foo'. if 'last_foo' in template
    map, expand it instead of 'foo' for last key.

    expand 'end_foos'.
    '''
    templ = args['templ']
    if plural:
        names = plural
    else: names = name + 's'
    if not values:
        noname = 'no_' + names
        if noname in templ:
            yield templ(noname, **args)
        return
    if name not in templ:
        if isinstance(values[0], str):
            yield ' '.join(values)
        else:
            for v in values:
                yield dict(v, **args)
        return
    startname = 'start_' + names
    if startname in templ:
        yield templ(startname, **args)
    vargs = args.copy()
    def one(v, tag=name):
        try:
            vargs.update(v)
        except (AttributeError, ValueError):
            try:
                for a, b in v:
                    vargs[a] = b
            except ValueError:
                vargs[name] = v
        return templ(tag, **vargs)
    lastname = 'last_' + name
    if lastname in templ:
        last = values.pop()
    else:
        last = None
    for v in values:
        yield one(v)
    if last is not None:
        yield one(last, tag=lastname)
    endname = 'end_' + names
    if endname in templ:
        yield templ(endname, **args)

def getfiles(repo, ctx, revcache):
    if 'files' not in revcache:
        revcache['files'] = repo.status(ctx.p1().node(), ctx.node())[:3]
    return revcache['files']

def getlatesttags(repo, ctx, cache):
    '''return date, distance and name for the latest tag of rev'''

    if 'latesttags' not in cache:
        # Cache mapping from rev to a tuple with tag date, tag
        # distance and tag name
        cache['latesttags'] = {-1: (0, 0, 'null')}
    latesttags = cache['latesttags']

    rev = ctx.rev()
    todo = [rev]
    while todo:
        rev = todo.pop()
        if rev in latesttags:
            continue
        ctx = repo[rev]
        tags = [t for t in ctx.tags() if repo.tagtype(t) == 'global']
        if tags:
            latesttags[rev] = ctx.date()[0], 0, ':'.join(sorted(tags))
            continue
        try:
            # The tuples are laid out so the right one can be found by
            # comparison.
            pdate, pdist, ptag = max(
                latesttags[p.rev()] for p in ctx.parents())
        except KeyError:
            # Cache miss - recurse
            todo.append(rev)
            todo.extend(p.rev() for p in ctx.parents())
            continue
        latesttags[rev] = pdate, pdist + 1, ptag
    return latesttags[rev]

def getrenamedfn(repo, endrev=None):
    rcache = {}
    if endrev is None:
        endrev = len(repo)

    def getrenamed(fn, rev):
        '''looks up all renames for a file (up to endrev) the first
        time the file is given. It indexes on the changerev and only
        parses the manifest if linkrev != changerev.
        Returns rename info for fn at changerev rev.'''
        if fn not in rcache:
            rcache[fn] = {}
            fl = repo.file(fn)
            for i in fl:
                lr = fl.linkrev(i)
                renamed = fl.renamed(fl.node(i))
                rcache[fn][lr] = renamed
                if lr >= endrev:
                    break
        if rev in rcache[fn]:
            return rcache[fn][rev]

        # If linkrev != rev (i.e. rev not found in rcache) fallback to
        # filectx logic.
        try:
            return repo[rev][fn].renamed()
        except error.LookupError:
            return None

    return getrenamed


def showauthor(repo, ctx, templ, **args):
    """:author: String. The unmodified author of the changeset."""
    return ctx.user()

def showbisect(repo, ctx, templ, **args):
    """:bisect: String. The changeset bisection status."""
    return hbisect.label(repo, ctx.node())

def showbranch(**args):
    """:branch: String. The name of the branch on which the changeset was
    committed.
    """
    return args['ctx'].branch()

def showbranches(**args):
    """:branches: List of strings. The name of the branch on which the
    changeset was committed. Will be empty if the branch name was
    default.
    """
    branch = args['ctx'].branch()
    if branch != 'default':
        return showlist('branch', [branch], plural='branches', **args)

def showbookmarks(**args):
    """:bookmarks: List of strings. Any bookmarks associated with the
    changeset.
    """
    bookmarks = args['ctx'].bookmarks()
    return showlist('bookmark', bookmarks, **args)

def showchildren(**args):
    """:children: List of strings. The children of the changeset."""
    ctx = args['ctx']
    childrevs = ['%d:%s' % (cctx, cctx) for cctx in ctx.children()]
    return showlist('children', childrevs, element='child', **args)

def showdate(repo, ctx, templ, **args):
    """:date: Date information. The date when the changeset was committed."""
    return ctx.date()

def showdescription(repo, ctx, templ, **args):
    """:desc: String. The text of the changeset description."""
    return ctx.description().strip()

def showdiffstat(repo, ctx, templ, **args):
    """:diffstat: String. Statistics of changes with the following format:
    "modified files: +added/-removed lines"
    """
    stats = patch.diffstatdata(util.iterlines(ctx.diff()))
    maxname, maxtotal, adds, removes, binary = patch.diffstatsum(stats)
    return '%s: +%s/-%s' % (len(stats), adds, removes)

def showextras(**args):
    templ = args['templ']
    for key, value in sorted(args['ctx'].extra().items()):
        args = args.copy()
        args.update(dict(key=key, value=value))
        yield templ('extra', **args)

def showfileadds(**args):
    """:file_adds: List of strings. Files added by this changeset."""
    repo, ctx, revcache = args['repo'], args['ctx'], args['revcache']
    return showlist('file_add', getfiles(repo, ctx, revcache)[1],
                    element='file', **args)

def showfilecopies(**args):
    """:file_copies: List of strings. Files copied in this changeset with
    their sources.
    """
    cache, ctx = args['cache'], args['ctx']
    copies = args['revcache'].get('copies')
    if copies is None:
        if 'getrenamed' not in cache:
            cache['getrenamed'] = getrenamedfn(args['repo'])
        copies = []
        getrenamed = cache['getrenamed']
        for fn in ctx.files():
            rename = getrenamed(fn, ctx.rev())
            if rename:
                copies.append((fn, rename[0]))

    c = [{'name': x[0], 'source': x[1]} for x in copies]
    f = _showlist('file_copy', c, plural='file_copies', **args)
    return _hybrid(f, c, lambda x: '%s (%s)' % (x['name'], x['source']))

# showfilecopiesswitch() displays file copies only if copy records are
# provided before calling the templater, usually with a --copies
# command line switch.
def showfilecopiesswitch(**args):
    """:file_copies_switch: List of strings. Like "file_copies" but displayed
    only if the --copied switch is set.
    """
    copies = args['revcache'].get('copies') or []
    c = [{'name': x[0], 'source': x[1]} for x in copies]
    f = _showlist('file_copy', c, plural='file_copies', **args)
    return _hybrid(f, c, lambda x: '%s (%s)' % (x['name'], x['source']))

def showfiledels(**args):
    """:file_dels: List of strings. Files removed by this changeset."""
    repo, ctx, revcache = args['repo'], args['ctx'], args['revcache']
    return showlist('file_del', getfiles(repo, ctx, revcache)[2],
                    element='file', **args)

def showfilemods(**args):
    """:file_mods: List of strings. Files modified by this changeset."""
    repo, ctx, revcache = args['repo'], args['ctx'], args['revcache']
    return showlist('file_mod', getfiles(repo, ctx, revcache)[0],
                    element='file', **args)

def showfiles(**args):
    """:files: List of strings. All files modified, added, or removed by this
    changeset.
    """
    return showlist('file', args['ctx'].files(), **args)

def showlatesttag(repo, ctx, templ, cache, **args):
    """:latesttag: String. Most recent global tag in the ancestors of this
    changeset.
    """
    return getlatesttags(repo, ctx, cache)[2]

def showlatesttagdistance(repo, ctx, templ, cache, **args):
    """:latesttagdistance: Integer. Longest path to the latest tag."""
    return getlatesttags(repo, ctx, cache)[1]

def showmanifest(**args):
    repo, ctx, templ = args['repo'], args['ctx'], args['templ']
    args = args.copy()
    args.update(dict(rev=repo.manifest.rev(ctx.changeset()[0]),
                     node=hex(ctx.changeset()[0])))
    return templ('manifest', **args)

def shownode(repo, ctx, templ, **args):
    """:node: String. The changeset identification hash, as a 40 hexadecimal
    digit string.
    """
    return ctx.hex()

def showp1rev(repo, ctx, templ, **args):
    """:p1rev: Integer. The repository-local revision number of the changeset's
    first parent, or -1 if the changeset has no parents."""
    return ctx.p1().rev()

def showp2rev(repo, ctx, templ, **args):
    """:p2rev: Integer. The repository-local revision number of the changeset's
    second parent, or -1 if the changeset has no second parent."""
    return ctx.p2().rev()

def showp1node(repo, ctx, templ, **args):
    """:p1node: String. The identification hash of the changeset's first parent,
    as a 40 digit hexadecimal string. If the changeset has no parents, all
    digits are 0."""
    return ctx.p1().hex()

def showp2node(repo, ctx, templ, **args):
    """:p2node: String. The identification hash of the changeset's second
    parent, as a 40 digit hexadecimal string. If the changeset has no second
    parent, all digits are 0."""
    return ctx.p2().hex()

def showphase(repo, ctx, templ, **args):
    """:phase: String. The changeset phase name."""
    return ctx.phasestr()

def showphaseidx(repo, ctx, templ, **args):
    """:phaseidx: Integer. The changeset phase index."""
    return ctx.phase()

def showrev(repo, ctx, templ, **args):
    """:rev: Integer. The repository-local changeset revision number."""
    return ctx.rev()

def showtags(**args):
    """:tags: List of strings. Any tags associated with the changeset."""
    return showlist('tag', args['ctx'].tags(), **args)

# keywords are callables like:
# fn(repo, ctx, templ, cache, revcache, **args)
# with:
# repo - current repository instance
# ctx - the changectx being displayed
# templ - the templater instance
# cache - a cache dictionary for the whole templater run
# revcache - a cache dictionary for the current revision
keywords = {
    'author': showauthor,
    'bisect': showbisect,
    'branch': showbranch,
    'branches': showbranches,
    'bookmarks': showbookmarks,
    'children': showchildren,
    'date': showdate,
    'desc': showdescription,
    'diffstat': showdiffstat,
    'extras': showextras,
    'file_adds': showfileadds,
    'file_copies': showfilecopies,
    'file_copies_switch': showfilecopiesswitch,
    'file_dels': showfiledels,
    'file_mods': showfilemods,
    'files': showfiles,
    'latesttag': showlatesttag,
    'latesttagdistance': showlatesttagdistance,
    'manifest': showmanifest,
    'node': shownode,
    'p1rev': showp1rev,
    'p1node': showp1node,
    'p2rev': showp2rev,
    'p2node': showp2node,
    'phase': showphase,
    'phaseidx': showphaseidx,
    'rev': showrev,
    'tags': showtags,
}

def _showparents(**args):
    """:parents: List of strings. The parents of the changeset in "rev:node"
    format. If the changeset has only one "natural" parent (the predecessor
    revision) nothing is shown."""
    pass

dockeywords = {
    'parents': _showparents,
}
dockeywords.update(keywords)

# tell hggettext to extract docstrings from these functions:
i18nfunctions = dockeywords.values()
