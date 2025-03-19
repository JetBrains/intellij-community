#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import copy
import mimetypes
import os
import re

from ..i18n import _
from ..node import hex, short

from .common import (
    ErrorResponse,
    HTTP_FORBIDDEN,
    HTTP_NOT_FOUND,
    get_contact,
    paritygen,
    staticfile,
)

from .. import (
    archival,
    dagop,
    encoding,
    error,
    graphmod,
    pycompat,
    revset,
    revsetlang,
    scmutil,
    smartset,
    templateutil,
)

from ..utils import stringutil

from . import webutil

__all__ = []
commands = {}


class webcommand:
    """Decorator used to register a web command handler.

    The decorator takes as its positional arguments the name/path the
    command should be accessible under.

    When called, functions receive as arguments a ``requestcontext``,
    ``wsgirequest``, and a templater instance for generatoring output.
    The functions should populate the ``rctx.res`` object with details
    about the HTTP response.

    The function returns a generator to be consumed by the WSGI application.
    For most commands, this should be the result from
    ``web.res.sendresponse()``. Many commands will call ``web.sendtemplate()``
    to render a template.

    Usage:

    @webcommand('mycommand')
    def mycommand(web):
        pass
    """

    def __init__(self, name):
        self.name = name

    def __call__(self, func):
        __all__.append(self.name)
        commands[self.name] = func
        return func


@webcommand(b'log')
def log(web):
    """
    /log[/{revision}[/{path}]]
    --------------------------

    Show repository or file history.

    For URLs of the form ``/log/{revision}``, a list of changesets starting at
    the specified changeset identifier is shown. If ``{revision}`` is not
    defined, the default is ``tip``. This form is equivalent to the
    ``changelog`` handler.

    For URLs of the form ``/log/{revision}/{file}``, the history for a specific
    file will be shown. This form is equivalent to the ``filelog`` handler.
    """

    if web.req.qsparams.get(b'file'):
        return filelog(web)
    else:
        return changelog(web)


@webcommand(b'rawfile')
def rawfile(web):
    guessmime = web.configbool(b'web', b'guessmime')

    path = webutil.cleanpath(web.repo, web.req.qsparams.get(b'file', b''))
    if not path:
        return manifest(web)

    try:
        fctx = webutil.filectx(web.repo, web.req)
    except error.LookupError as inst:
        try:
            return manifest(web)
        except ErrorResponse:
            raise inst

    path = fctx.path()
    text = fctx.data()
    mt = b'application/binary'
    if guessmime:
        mt = mimetypes.guess_type(pycompat.fsdecode(path))[0]
        if mt is None:
            if stringutil.binary(text):
                mt = b'application/binary'
            else:
                mt = b'text/plain'
        else:
            mt = pycompat.sysbytes(mt)

    if mt.startswith(b'text/'):
        mt += b'; charset="%s"' % encoding.encoding

    web.res.headers[b'Content-Type'] = mt
    filename = (
        path.rpartition(b'/')[-1].replace(b'\\', b'\\\\').replace(b'"', b'\\"')
    )
    web.res.headers[b'Content-Disposition'] = (
        b'inline; filename="%s"' % filename
    )
    web.res.setbodybytes(text)
    return web.res.sendresponse()


def _filerevision(web, fctx):
    f = fctx.path()
    text = fctx.data()
    parity = paritygen(web.stripecount)
    ishead = fctx.filenode() in fctx.filelog().heads()

    if stringutil.binary(text):
        mt = pycompat.sysbytes(
            mimetypes.guess_type(pycompat.fsdecode(f))[0]
            or r'application/octet-stream'
        )
        text = b'(binary:%s)' % mt

    def lines(context):
        for lineno, t in enumerate(text.splitlines(True)):
            yield {
                b"line": t,
                b"lineid": b"l%d" % (lineno + 1),
                b"linenumber": b"% 6d" % (lineno + 1),
                b"parity": next(parity),
            }

    return web.sendtemplate(
        b'filerevision',
        file=f,
        path=webutil.up(f),
        text=templateutil.mappinggenerator(lines),
        symrev=webutil.symrevorshortnode(web.req, fctx),
        rename=webutil.renamelink(fctx),
        permissions=fctx.manifest().flags(f),
        ishead=int(ishead),
        **pycompat.strkwargs(webutil.commonentry(web.repo, fctx))
    )


@webcommand(b'file')
def file(web):
    """
    /file/{revision}[/{path}]
    -------------------------

    Show information about a directory or file in the repository.

    Info about the ``path`` given as a URL parameter will be rendered.

    If ``path`` is a directory, information about the entries in that
    directory will be rendered. This form is equivalent to the ``manifest``
    handler.

    If ``path`` is a file, information about that file will be shown via
    the ``filerevision`` template.

    If ``path`` is not defined, information about the root directory will
    be rendered.
    """
    if web.req.qsparams.get(b'style') == b'raw':
        return rawfile(web)

    path = webutil.cleanpath(web.repo, web.req.qsparams.get(b'file', b''))
    if not path:
        return manifest(web)
    try:
        return _filerevision(web, webutil.filectx(web.repo, web.req))
    except error.LookupError as inst:
        try:
            return manifest(web)
        except ErrorResponse:
            raise inst


def _search(web):
    MODE_REVISION = b'rev'
    MODE_KEYWORD = b'keyword'
    MODE_REVSET = b'revset'

    def revsearch(ctx):
        yield ctx

    def keywordsearch(query):
        lower = encoding.lower
        qw = lower(query).split()

        def revgen():
            cl = web.repo.changelog
            for i in range(len(web.repo) - 1, 0, -100):
                l = []
                for j in cl.revs(max(0, i - 99), i):
                    ctx = web.repo[j]
                    l.append(ctx)
                l.reverse()
                for e in l:
                    yield e

        for ctx in revgen():
            miss = 0
            for q in qw:
                if not (
                    q in lower(ctx.user())
                    or q in lower(ctx.description())
                    or q in lower(b" ".join(ctx.files()))
                ):
                    miss = 1
                    break
            if miss:
                continue

            yield ctx

    def revsetsearch(revs):
        for r in revs:
            yield web.repo[r]

    searchfuncs = {
        MODE_REVISION: (revsearch, b'exact revision search'),
        MODE_KEYWORD: (keywordsearch, b'literal keyword search'),
        MODE_REVSET: (revsetsearch, b'revset expression search'),
    }

    def getsearchmode(query):
        try:
            ctx = scmutil.revsymbol(web.repo, query)
        except (error.RepoError, error.LookupError):
            # query is not an exact revision pointer, need to
            # decide if it's a revset expression or keywords
            pass
        else:
            return MODE_REVISION, ctx

        revdef = b'reverse(%s)' % query
        try:
            tree = revsetlang.parse(revdef)
        except error.ParseError:
            # can't parse to a revset tree
            return MODE_KEYWORD, query

        if revsetlang.depth(tree) <= 2:
            # no revset syntax used
            return MODE_KEYWORD, query

        if any(
            (token, (value or b'')[:3]) == (b'string', b're:')
            for token, value, pos in revsetlang.tokenize(revdef)
        ):
            return MODE_KEYWORD, query

        funcsused = revsetlang.funcsused(tree)
        if not funcsused.issubset(revset.safesymbols):
            return MODE_KEYWORD, query

        try:
            mfunc = revset.match(
                web.repo.ui, revdef, lookup=revset.lookupfn(web.repo)
            )
            revs = mfunc(web.repo)
            return MODE_REVSET, revs
            # ParseError: wrongly placed tokens, wrongs arguments, etc
            # RepoLookupError: no such revision, e.g. in 'revision:'
            # Abort: bookmark/tag not exists
            # LookupError: ambiguous identifier, e.g. in '(bc)' on a large repo
        except (
            error.ParseError,
            error.RepoLookupError,
            error.Abort,
            LookupError,
        ):
            return MODE_KEYWORD, query

    def changelist(context):
        count = 0

        for ctx in searchfunc[0](funcarg):
            count += 1
            n = scmutil.binnode(ctx)
            showtags = webutil.showtag(web.repo, b'changelogtag', n)
            files = webutil.listfilediffs(ctx.files(), n, web.maxfiles)

            lm = webutil.commonentry(web.repo, ctx)
            lm.update(
                {
                    b'parity': next(parity),
                    b'changelogtag': showtags,
                    b'files': files,
                }
            )
            yield lm

            if count >= revcount:
                break

    query = web.req.qsparams[b'rev']
    revcount = web.maxchanges
    if b'revcount' in web.req.qsparams:
        try:
            revcount = int(web.req.qsparams.get(b'revcount', revcount))
            revcount = max(revcount, 1)
            web.tmpl.defaults[b'sessionvars'][b'revcount'] = revcount
        except ValueError:
            pass

    lessvars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    lessvars[b'revcount'] = max(revcount // 2, 1)
    lessvars[b'rev'] = query
    morevars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    morevars[b'revcount'] = revcount * 2
    morevars[b'rev'] = query

    mode, funcarg = getsearchmode(query)

    if b'forcekw' in web.req.qsparams:
        showforcekw = b''
        showunforcekw = searchfuncs[mode][1]
        mode = MODE_KEYWORD
        funcarg = query
    else:
        if mode != MODE_KEYWORD:
            showforcekw = searchfuncs[MODE_KEYWORD][1]
        else:
            showforcekw = b''
        showunforcekw = b''

    searchfunc = searchfuncs[mode]

    tip = web.repo[b'tip']
    parity = paritygen(web.stripecount)

    return web.sendtemplate(
        b'search',
        query=query,
        node=tip.hex(),
        symrev=b'tip',
        entries=templateutil.mappinggenerator(changelist, name=b'searchentry'),
        archives=web.archivelist(b'tip'),
        morevars=morevars,
        lessvars=lessvars,
        modedesc=searchfunc[1],
        showforcekw=showforcekw,
        showunforcekw=showunforcekw,
    )


@webcommand(b'changelog')
def changelog(web, shortlog=False):
    """
    /changelog[/{revision}]
    -----------------------

    Show information about multiple changesets.

    If the optional ``revision`` URL argument is absent, information about
    all changesets starting at ``tip`` will be rendered. If the ``revision``
    argument is present, changesets will be shown starting from the specified
    revision.

    If ``revision`` is absent, the ``rev`` query string argument may be
    defined. This will perform a search for changesets.

    The argument for ``rev`` can be a single revision, a revision set,
    or a literal keyword to search for in changeset data (equivalent to
    :hg:`log -k`).

    The ``revcount`` query string argument defines the maximum numbers of
    changesets to render.

    For non-searches, the ``changelog`` template will be rendered.
    """

    query = b''
    if b'node' in web.req.qsparams:
        ctx = webutil.changectx(web.repo, web.req)
        symrev = webutil.symrevorshortnode(web.req, ctx)
    elif b'rev' in web.req.qsparams:
        return _search(web)
    else:
        ctx = web.repo[b'tip']
        symrev = b'tip'

    def changelist(maxcount):
        revs = []
        if pos != -1:
            revs = web.repo.changelog.revs(pos, 0)

        for entry in webutil.changelistentries(web, revs, maxcount, parity):
            yield entry

    if shortlog:
        revcount = web.maxshortchanges
    else:
        revcount = web.maxchanges

    if b'revcount' in web.req.qsparams:
        try:
            revcount = int(web.req.qsparams.get(b'revcount', revcount))
            revcount = max(revcount, 1)
            web.tmpl.defaults[b'sessionvars'][b'revcount'] = revcount
        except ValueError:
            pass

    lessvars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    lessvars[b'revcount'] = max(revcount // 2, 1)
    morevars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    morevars[b'revcount'] = revcount * 2

    count = len(web.repo)
    pos = ctx.rev()
    parity = paritygen(web.stripecount)

    changenav = webutil.revnav(web.repo).gen(pos, revcount, count)

    entries = list(changelist(revcount + 1))
    latestentry = entries[:1]
    if len(entries) > revcount:
        nextentry = entries[-1:]
        entries = entries[:-1]
    else:
        nextentry = []

    return web.sendtemplate(
        b'shortlog' if shortlog else b'changelog',
        changenav=changenav,
        node=ctx.hex(),
        rev=pos,
        symrev=symrev,
        changesets=count,
        entries=templateutil.mappinglist(entries),
        latestentry=templateutil.mappinglist(latestentry),
        nextentry=templateutil.mappinglist(nextentry),
        archives=web.archivelist(b'tip'),
        revcount=revcount,
        morevars=morevars,
        lessvars=lessvars,
        query=query,
    )


@webcommand(b'shortlog')
def shortlog(web):
    """
    /shortlog
    ---------

    Show basic information about a set of changesets.

    This accepts the same parameters as the ``changelog`` handler. The only
    difference is the ``shortlog`` template will be rendered instead of the
    ``changelog`` template.
    """
    return changelog(web, shortlog=True)


@webcommand(b'changeset')
def changeset(web):
    """
    /changeset[/{revision}]
    -----------------------

    Show information about a single changeset.

    A URL path argument is the changeset identifier to show. See ``hg help
    revisions`` for possible values. If not defined, the ``tip`` changeset
    will be shown.

    The ``changeset`` template is rendered. Contents of the ``changesettag``,
    ``changesetbookmark``, ``filenodelink``, ``filenolink``, and the many
    templates related to diffs may all be used to produce the output.
    """
    ctx = webutil.changectx(web.repo, web.req)

    return web.sendtemplate(b'changeset', **webutil.changesetentry(web, ctx))


rev = webcommand(b'rev')(changeset)


def decodepath(path: bytes) -> bytes:
    """Hook for mapping a path in the repository to a path in the
    working copy.

    Extensions (e.g., largefiles) can override this to remap files in
    the virtual file system presented by the manifest command below."""
    return path


@webcommand(b'manifest')
def manifest(web):
    """
    /manifest[/{revision}[/{path}]]
    -------------------------------

    Show information about a directory.

    If the URL path arguments are omitted, information about the root
    directory for the ``tip`` changeset will be shown.

    Because this handler can only show information for directories, it
    is recommended to use the ``file`` handler instead, as it can handle both
    directories and files.

    The ``manifest`` template will be rendered for this handler.
    """
    if b'node' in web.req.qsparams:
        ctx = webutil.changectx(web.repo, web.req)
        symrev = webutil.symrevorshortnode(web.req, ctx)
    else:
        ctx = web.repo[b'tip']
        symrev = b'tip'
    path = webutil.cleanpath(web.repo, web.req.qsparams.get(b'file', b''))
    mf = ctx.manifest()
    node = scmutil.binnode(ctx)

    files = {}
    dirs = {}
    parity = paritygen(web.stripecount)

    if path and path[-1:] != b"/":
        path += b"/"
    l = len(path)
    abspath = b"/" + path

    for full, n in mf.items():
        # the virtual path (working copy path) used for the full
        # (repository) path
        f = decodepath(full)

        if f[:l] != path:
            continue
        remain = f[l:]
        elements = remain.split(b'/')
        if len(elements) == 1:
            files[remain] = full
        else:
            h = dirs  # need to retain ref to dirs (root)
            for elem in elements[0:-1]:
                if elem not in h:
                    h[elem] = {}
                h = h[elem]
                if len(h) > 1:
                    break
            h[None] = None  # denotes files present

    if mf and not files and not dirs:
        raise ErrorResponse(HTTP_NOT_FOUND, b'path not found: ' + path)

    def filelist(context):
        for f in sorted(files):
            full = files[f]

            fctx = ctx.filectx(full)
            yield {
                b"file": full,
                b"parity": next(parity),
                b"basename": f,
                b"date": fctx.date(),
                b"size": fctx.size(),
                b"permissions": mf.flags(full),
            }

    def dirlist(context):
        for d in sorted(dirs):

            emptydirs = []
            h = dirs[d]
            while isinstance(h, dict) and len(h) == 1:
                k, v = next(iter(h.items()))
                if v:
                    emptydirs.append(k)
                h = v

            path = b"%s%s" % (abspath, d)
            yield {
                b"parity": next(parity),
                b"path": path,
                # pytype: disable=wrong-arg-types
                b"emptydirs": b"/".join(emptydirs),
                # pytype: enable=wrong-arg-types
                b"basename": d,
            }

    return web.sendtemplate(
        b'manifest',
        symrev=symrev,
        path=abspath,
        up=webutil.up(abspath),
        upparity=next(parity),
        fentries=templateutil.mappinggenerator(filelist),
        dentries=templateutil.mappinggenerator(dirlist),
        archives=web.archivelist(hex(node)),
        **pycompat.strkwargs(webutil.commonentry(web.repo, ctx))
    )


@webcommand(b'tags')
def tags(web):
    """
    /tags
    -----

    Show information about tags.

    No arguments are accepted.

    The ``tags`` template is rendered.
    """
    i = list(reversed(web.repo.tagslist()))
    parity = paritygen(web.stripecount)

    def entries(context, notip, latestonly):
        t = i
        if notip:
            t = [(k, n) for k, n in i if k != b"tip"]
        if latestonly:
            t = t[:1]
        for k, n in t:
            yield {
                b"parity": next(parity),
                b"tag": k,
                b"date": web.repo[n].date(),
                b"node": hex(n),
            }

    return web.sendtemplate(
        b'tags',
        node=hex(web.repo.changelog.tip()),
        entries=templateutil.mappinggenerator(entries, args=(False, False)),
        entriesnotip=templateutil.mappinggenerator(entries, args=(True, False)),
        latestentry=templateutil.mappinggenerator(entries, args=(True, True)),
    )


@webcommand(b'bookmarks')
def bookmarks(web):
    """
    /bookmarks
    ----------

    Show information about bookmarks.

    No arguments are accepted.

    The ``bookmarks`` template is rendered.
    """
    i = [b for b in web.repo._bookmarks.items() if b[1] in web.repo]
    sortkey = lambda b: (web.repo[b[1]].rev(), b[0])
    i = sorted(i, key=sortkey, reverse=True)
    parity = paritygen(web.stripecount)

    def entries(context, latestonly):
        t = i
        if latestonly:
            t = i[:1]
        for k, n in t:
            yield {
                b"parity": next(parity),
                b"bookmark": k,
                b"date": web.repo[n].date(),
                b"node": hex(n),
            }

    if i:
        latestrev = i[0][1]
    else:
        latestrev = -1
    lastdate = web.repo[latestrev].date()

    return web.sendtemplate(
        b'bookmarks',
        node=hex(web.repo.changelog.tip()),
        lastchange=templateutil.mappinglist([{b'date': lastdate}]),
        entries=templateutil.mappinggenerator(entries, args=(False,)),
        latestentry=templateutil.mappinggenerator(entries, args=(True,)),
    )


@webcommand(b'branches')
def branches(web):
    """
    /branches
    ---------

    Show information about branches.

    All known branches are contained in the output, even closed branches.

    No arguments are accepted.

    The ``branches`` template is rendered.
    """
    entries = webutil.branchentries(web.repo, web.stripecount)
    latestentry = webutil.branchentries(web.repo, web.stripecount, 1)

    return web.sendtemplate(
        b'branches',
        node=hex(web.repo.changelog.tip()),
        entries=entries,
        latestentry=latestentry,
    )


@webcommand(b'summary')
def summary(web):
    """
    /summary
    --------

    Show a summary of repository state.

    Information about the latest changesets, bookmarks, tags, and branches
    is captured by this handler.

    The ``summary`` template is rendered.
    """
    i = reversed(web.repo.tagslist())

    def tagentries(context):
        parity = paritygen(web.stripecount)
        count = 0
        for k, n in i:
            if k == b"tip":  # skip tip
                continue

            count += 1
            if count > 10:  # limit to 10 tags
                break

            yield {
                b'parity': next(parity),
                b'tag': k,
                b'node': hex(n),
                b'date': web.repo[n].date(),
            }

    def bookmarks(context):
        parity = paritygen(web.stripecount)
        marks = [b for b in web.repo._bookmarks.items() if b[1] in web.repo]
        sortkey = lambda b: (web.repo[b[1]].rev(), b[0])
        marks = sorted(marks, key=sortkey, reverse=True)
        for k, n in marks[:10]:  # limit to 10 bookmarks
            yield {
                b'parity': next(parity),
                b'bookmark': k,
                b'date': web.repo[n].date(),
                b'node': hex(n),
            }

    def changelist(context):
        parity = paritygen(web.stripecount, offset=start - end)
        l = []  # build a list in forward order for efficiency
        revs = []
        if start < end:
            revs = web.repo.changelog.revs(start, end - 1)
        for i in revs:
            ctx = web.repo[i]
            lm = webutil.commonentry(web.repo, ctx)
            lm[b'parity'] = next(parity)
            l.append(lm)

        for entry in reversed(l):
            yield entry

    tip = web.repo[b'tip']
    count = len(web.repo)
    start = max(0, count - web.maxchanges)
    end = min(count, start + web.maxchanges)

    desc = web.config(b"web", b"description")
    if not desc:
        desc = b'unknown'
    labels = web.configlist(b'web', b'labels')

    return web.sendtemplate(
        b'summary',
        desc=desc,
        owner=get_contact(web.config) or b'unknown',
        lastchange=tip.date(),
        tags=templateutil.mappinggenerator(tagentries, name=b'tagentry'),
        bookmarks=templateutil.mappinggenerator(bookmarks),
        branches=webutil.branchentries(web.repo, web.stripecount, 10),
        shortlog=templateutil.mappinggenerator(
            changelist, name=b'shortlogentry'
        ),
        node=tip.hex(),
        symrev=b'tip',
        archives=web.archivelist(b'tip'),
        labels=templateutil.hybridlist(labels, name=b'label'),
    )


@webcommand(b'filediff')
def filediff(web):
    """
    /diff/{revision}/{path}
    -----------------------

    Show how a file changed in a particular commit.

    The ``filediff`` template is rendered.

    This handler is registered under both the ``/diff`` and ``/filediff``
    paths. ``/diff`` is used in modern code.
    """
    fctx, ctx = None, None
    try:
        fctx = webutil.filectx(web.repo, web.req)
    except LookupError:
        ctx = webutil.changectx(web.repo, web.req)
        path = webutil.cleanpath(web.repo, web.req.qsparams[b'file'])
        if path not in ctx.files():
            raise

    if fctx is not None:
        path = fctx.path()
        ctx = fctx.changectx()
    basectx = ctx.p1()

    style = web.config(b'web', b'style')
    if b'style' in web.req.qsparams:
        style = web.req.qsparams[b'style']

    diffs = webutil.diffs(web, ctx, basectx, [path], style)
    if fctx is not None:
        rename = webutil.renamelink(fctx)
        ctx = fctx
    else:
        rename = templateutil.mappinglist([])
        ctx = ctx

    return web.sendtemplate(
        b'filediff',
        file=path,
        symrev=webutil.symrevorshortnode(web.req, ctx),
        rename=rename,
        diff=diffs,
        **pycompat.strkwargs(webutil.commonentry(web.repo, ctx))
    )


diff = webcommand(b'diff')(filediff)


@webcommand(b'comparison')
def comparison(web):
    """
    /comparison/{revision}/{path}
    -----------------------------

    Show a comparison between the old and new versions of a file from changes
    made on a particular revision.

    This is similar to the ``diff`` handler. However, this form features
    a split or side-by-side diff rather than a unified diff.

    The ``context`` query string argument can be used to control the lines of
    context in the diff.

    The ``filecomparison`` template is rendered.
    """
    ctx = webutil.changectx(web.repo, web.req)
    if b'file' not in web.req.qsparams:
        raise ErrorResponse(HTTP_NOT_FOUND, b'file not given')
    path = webutil.cleanpath(web.repo, web.req.qsparams[b'file'])

    parsecontext = lambda v: v == b'full' and -1 or int(v)
    if b'context' in web.req.qsparams:
        context = parsecontext(web.req.qsparams[b'context'])
    else:
        context = parsecontext(web.config(b'web', b'comparisoncontext'))

    def filelines(f):
        if f.isbinary():
            mt = pycompat.sysbytes(
                mimetypes.guess_type(pycompat.fsdecode(f.path()))[0]
                or r'application/octet-stream'
            )
            return [_(b'(binary file %s, hash: %s)') % (mt, hex(f.filenode()))]
        return f.data().splitlines()

    fctx = None
    parent = ctx.p1()
    leftrev = parent.rev()
    leftnode = parent.node()
    rightrev = ctx.rev()
    rightnode = scmutil.binnode(ctx)
    if path in ctx:
        fctx = ctx[path]
        rightlines = filelines(fctx)
        if path not in parent:
            leftlines = ()
        else:
            pfctx = parent[path]
            leftlines = filelines(pfctx)
    else:
        rightlines = ()
        pfctx = ctx.p1()[path]
        leftlines = filelines(pfctx)

    comparison = webutil.compare(context, leftlines, rightlines)
    if fctx is not None:
        rename = webutil.renamelink(fctx)
        ctx = fctx
    else:
        rename = templateutil.mappinglist([])
        ctx = ctx

    return web.sendtemplate(
        b'filecomparison',
        file=path,
        symrev=webutil.symrevorshortnode(web.req, ctx),
        rename=rename,
        leftrev=leftrev,
        leftnode=hex(leftnode),
        rightrev=rightrev,
        rightnode=hex(rightnode),
        comparison=comparison,
        **pycompat.strkwargs(webutil.commonentry(web.repo, ctx))
    )


@webcommand(b'annotate')
def annotate(web):
    """
    /annotate/{revision}/{path}
    ---------------------------

    Show changeset information for each line in a file.

    The ``ignorews``, ``ignorewsamount``, ``ignorewseol``, and
    ``ignoreblanklines`` query string arguments have the same meaning as
    their ``[annotate]`` config equivalents. It uses the hgrc boolean
    parsing logic to interpret the value. e.g. ``0`` and ``false`` are
    false and ``1`` and ``true`` are true. If not defined, the server
    default settings are used.

    The ``fileannotate`` template is rendered.
    """
    fctx = webutil.filectx(web.repo, web.req)
    f = fctx.path()
    parity = paritygen(web.stripecount)
    ishead = fctx.filenode() in fctx.filelog().heads()

    # parents() is called once per line and several lines likely belong to
    # same revision. So it is worth caching.
    # TODO there are still redundant operations within basefilectx.parents()
    # and from the fctx.annotate() call itself that could be cached.
    parentscache = {}

    def parents(context, f):
        rev = f.rev()
        if rev not in parentscache:
            parentscache[rev] = []
            for p in f.parents():
                entry = {
                    b'node': p.hex(),
                    b'rev': p.rev(),
                }
                parentscache[rev].append(entry)

        for p in parentscache[rev]:
            yield p

    def annotate(context):
        if fctx.isbinary():
            mt = pycompat.sysbytes(
                mimetypes.guess_type(pycompat.fsdecode(fctx.path()))[0]
                or r'application/octet-stream'
            )
            lines = [
                dagop.annotateline(
                    fctx=fctx.filectx(fctx.filerev()),
                    lineno=1,
                    text=b'(binary:%s)' % mt,
                )
            ]
        else:
            lines = webutil.annotate(web.req, fctx, web.repo.ui)

        previousrev = None
        blockparitygen = paritygen(1)
        for lineno, aline in enumerate(lines):
            f = aline.fctx
            rev = f.rev()
            if rev != previousrev:
                blockhead = True
                blockparity = next(blockparitygen)
            else:
                blockhead = None
            previousrev = rev
            yield {
                b"parity": next(parity),
                b"node": f.hex(),
                b"rev": rev,
                b"author": f.user(),
                b"parents": templateutil.mappinggenerator(parents, args=(f,)),
                b"desc": f.description(),
                b"extra": f.extra(),
                b"file": f.path(),
                b"blockhead": blockhead,
                b"blockparity": blockparity,
                b"targetline": aline.lineno,
                b"line": aline.text,
                b"lineno": lineno + 1,
                b"lineid": b"l%d" % (lineno + 1),
                b"linenumber": b"% 6d" % (lineno + 1),
                b"revdate": f.date(),
            }

    diffopts = webutil.difffeatureopts(web.req, web.repo.ui, b'annotate')
    diffopts = {
        k: getattr(diffopts, pycompat.sysstr(k)) for k in diffopts.defaults
    }

    return web.sendtemplate(
        b'fileannotate',
        file=f,
        annotate=templateutil.mappinggenerator(annotate),
        path=webutil.up(f),
        symrev=webutil.symrevorshortnode(web.req, fctx),
        rename=webutil.renamelink(fctx),
        permissions=fctx.manifest().flags(f),
        ishead=int(ishead),
        diffopts=templateutil.hybriddict(diffopts),
        **pycompat.strkwargs(webutil.commonentry(web.repo, fctx))
    )


@webcommand(b'filelog')
def filelog(web):
    """
    /filelog/{revision}/{path}
    --------------------------

    Show information about the history of a file in the repository.

    The ``revcount`` query string argument can be defined to control the
    maximum number of entries to show.

    The ``filelog`` template will be rendered.
    """

    try:
        fctx = webutil.filectx(web.repo, web.req)
        f = fctx.path()
        fl = fctx.filelog()
    except error.LookupError:
        f = webutil.cleanpath(web.repo, web.req.qsparams[b'file'])
        fl = web.repo.file(f)
        numrevs = len(fl)
        if not numrevs:  # file doesn't exist at all
            raise
        rev = webutil.changectx(web.repo, web.req).rev()
        first = fl.linkrev(0)
        if rev < first:  # current rev is from before file existed
            raise
        frev = numrevs - 1
        while fl.linkrev(frev) > rev:
            frev -= 1
        fctx = web.repo.filectx(f, fl.linkrev(frev))

    revcount = web.maxshortchanges
    if b'revcount' in web.req.qsparams:
        try:
            revcount = int(web.req.qsparams.get(b'revcount', revcount))
            revcount = max(revcount, 1)
            web.tmpl.defaults[b'sessionvars'][b'revcount'] = revcount
        except ValueError:
            pass

    lrange = webutil.linerange(web.req)

    lessvars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    lessvars[b'revcount'] = max(revcount // 2, 1)
    morevars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    morevars[b'revcount'] = revcount * 2

    patch = b'patch' in web.req.qsparams
    if patch:
        lessvars[b'patch'] = morevars[b'patch'] = web.req.qsparams[b'patch']
    descend = b'descend' in web.req.qsparams
    if descend:
        lessvars[b'descend'] = morevars[b'descend'] = web.req.qsparams[
            b'descend'
        ]

    count = fctx.filerev() + 1
    start = max(0, count - revcount)  # first rev on this page
    end = min(count, start + revcount)  # last rev on this page
    parity = paritygen(web.stripecount, offset=start - end)

    repo = web.repo
    filelog = fctx.filelog()
    revs = [
        filerev
        for filerev in filelog.revs(start, end - 1)
        if filelog.linkrev(filerev) in repo
    ]
    entries = []

    diffstyle = web.config(b'web', b'style')
    if b'style' in web.req.qsparams:
        diffstyle = web.req.qsparams[b'style']

    def diff(fctx, linerange=None):
        ctx = fctx.changectx()
        basectx = ctx.p1()
        path = fctx.path()
        return webutil.diffs(
            web,
            ctx,
            basectx,
            [path],
            diffstyle,
            linerange=linerange,
            lineidprefix=b'%s-' % ctx.hex()[:12],
        )

    linerange = None
    if lrange is not None:
        assert lrange is not None  # help pytype (!?)
        linerange = webutil.formatlinerange(*lrange)
        # deactivate numeric nav links when linerange is specified as this
        # would required a dedicated "revnav" class
        nav = templateutil.mappinglist([])
        if descend:
            it = dagop.blockdescendants(fctx, *lrange)
        else:
            it = dagop.blockancestors(fctx, *lrange)
        for i, (c, lr) in enumerate(it, 1):
            diffs = None
            if patch:
                diffs = diff(c, linerange=lr)
            # follow renames accross filtered (not in range) revisions
            path = c.path()
            lm = webutil.commonentry(repo, c)
            lm.update(
                {
                    b'parity': next(parity),
                    b'filerev': c.rev(),
                    b'file': path,
                    b'diff': diffs,
                    b'linerange': webutil.formatlinerange(*lr),
                    b'rename': templateutil.mappinglist([]),
                }
            )
            entries.append(lm)
            if i == revcount:
                break
        lessvars[b'linerange'] = webutil.formatlinerange(*lrange)
        morevars[b'linerange'] = lessvars[b'linerange']
    else:
        for i in revs:
            iterfctx = fctx.filectx(i)
            diffs = None
            if patch:
                diffs = diff(iterfctx)
            lm = webutil.commonentry(repo, iterfctx)
            lm.update(
                {
                    b'parity': next(parity),
                    b'filerev': i,
                    b'file': f,
                    b'diff': diffs,
                    b'rename': webutil.renamelink(iterfctx),
                }
            )
            entries.append(lm)
        entries.reverse()
        revnav = webutil.filerevnav(web.repo, fctx.path())
        nav = revnav.gen(end - 1, revcount, count)

    latestentry = entries[:1]

    return web.sendtemplate(
        b'filelog',
        file=f,
        nav=nav,
        symrev=webutil.symrevorshortnode(web.req, fctx),
        entries=templateutil.mappinglist(entries),
        descend=descend,
        patch=patch,
        latestentry=templateutil.mappinglist(latestentry),
        linerange=linerange,
        revcount=revcount,
        morevars=morevars,
        lessvars=lessvars,
        **pycompat.strkwargs(webutil.commonentry(web.repo, fctx))
    )


@webcommand(b'archive')
def archive(web):
    """
    /archive/{revision}.{format}[/{path}]
    -------------------------------------

    Obtain an archive of repository content.

    The content and type of the archive is defined by a URL path parameter.
    ``format`` is the file extension of the archive type to be generated. e.g.
    ``zip`` or ``tar.bz2``. Not all archive types may be allowed by your
    server configuration.

    The optional ``path`` URL parameter controls content to include in the
    archive. If omitted, every file in the specified revision is present in the
    archive. If included, only the specified file or contents of the specified
    directory will be included in the archive.

    No template is used for this handler. Raw, binary content is generated.
    """

    type_ = web.req.qsparams.get(b'type')
    allowed = web.configlist(b"web", b"allow-archive")
    key = web.req.qsparams[b'node']

    if type_ not in webutil.archivespecs:
        msg = b'Unsupported archive type: %s' % stringutil.pprint(type_)
        raise ErrorResponse(HTTP_NOT_FOUND, msg)

    if not ((type_ in allowed or web.configbool(b"web", b"allow" + type_))):
        msg = b'Archive type not allowed: %s' % type_
        raise ErrorResponse(HTTP_FORBIDDEN, msg)

    reponame = re.sub(br"\W+", b"-", os.path.basename(web.reponame))
    cnode = web.repo.lookup(key)
    arch_version = key
    if cnode == key or key == b'tip':
        arch_version = short(cnode)
    name = b"%s-%s" % (reponame, arch_version)

    ctx = webutil.changectx(web.repo, web.req)
    match = scmutil.match(ctx, [])
    file = web.req.qsparams.get(b'file')
    if file:
        pats = [b'path:' + file]
        match = scmutil.match(ctx, pats, default=b'path')
        if pats:
            files = [f for f in ctx.manifest().keys() if match(f)]
            if not files:
                raise ErrorResponse(
                    HTTP_NOT_FOUND, b'file(s) not found: %s' % file
                )

    mimetype, artype, extension, encoding = webutil.archivespecs[type_]

    web.res.headers[b'Content-Type'] = mimetype
    web.res.headers[b'Content-Disposition'] = b'attachment; filename=%s%s' % (
        name,
        extension,
    )

    if encoding:
        web.res.headers[b'Content-Encoding'] = encoding

    web.res.setbodywillwrite()
    if list(web.res.sendresponse()):
        raise error.ProgrammingError(
            b'sendresponse() should not emit data if writing later'
        )

    if web.req.method == b'HEAD':
        return []

    bodyfh = web.res.getbodyfile()

    archival.archive(
        web.repo,
        bodyfh,
        cnode,
        artype,
        prefix=name,
        match=match,
        subrepos=web.configbool(b"web", b"archivesubrepos"),
    )

    return []


@webcommand(b'static')
def static(web):
    fname = web.req.qsparams[b'file']
    # a repo owner may set web.static in .hg/hgrc to get any file
    # readable by the user running the CGI script
    static = web.config(b"web", b"static", untrusted=False)
    staticfile(web.templatepath, static, fname, web.res)
    return web.res.sendresponse()


@webcommand(b'graph')
def graph(web):
    """
    /graph[/{revision}]
    -------------------

    Show information about the graphical topology of the repository.

    Information rendered by this handler can be used to create visual
    representations of repository topology.

    The ``revision`` URL parameter controls the starting changeset. If it's
    absent, the default is ``tip``.

    The ``revcount`` query string argument can define the number of changesets
    to show information for.

    The ``graphtop`` query string argument can specify the starting changeset
    for producing ``jsdata`` variable that is used for rendering graph in
    JavaScript. By default it has the same value as ``revision``.

    This handler will render the ``graph`` template.
    """

    if b'node' in web.req.qsparams:
        ctx = webutil.changectx(web.repo, web.req)
        symrev = webutil.symrevorshortnode(web.req, ctx)
    else:
        ctx = web.repo[b'tip']
        symrev = b'tip'
    rev = ctx.rev()

    bg_height = 39
    revcount = web.maxshortchanges
    if b'revcount' in web.req.qsparams:
        try:
            revcount = int(web.req.qsparams.get(b'revcount', revcount))
            revcount = max(revcount, 1)
            web.tmpl.defaults[b'sessionvars'][b'revcount'] = revcount
        except ValueError:
            pass

    lessvars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    lessvars[b'revcount'] = max(revcount // 2, 1)
    morevars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    morevars[b'revcount'] = revcount * 2

    graphtop = web.req.qsparams.get(b'graphtop', ctx.hex())
    graphvars = copy.copy(web.tmpl.defaults[b'sessionvars'])
    graphvars[b'graphtop'] = graphtop

    count = len(web.repo)
    pos = rev

    uprev = min(max(0, count - 1), rev + revcount)
    downrev = max(0, rev - revcount)
    changenav = webutil.revnav(web.repo).gen(pos, revcount, count)

    tree = []
    nextentry = []
    lastrev = 0
    if pos != -1:
        allrevs = web.repo.changelog.revs(pos, 0)
        revs = []
        for i in allrevs:
            revs.append(i)
            if len(revs) >= revcount + 1:
                break

        if len(revs) > revcount:
            nextentry = [webutil.commonentry(web.repo, web.repo[revs[-1]])]
            revs = revs[:-1]

        lastrev = revs[-1]

        # We have to feed a baseset to dagwalker as it is expecting smartset
        # object. This does not have a big impact on hgweb performance itself
        # since hgweb graphing code is not itself lazy yet.
        dag = graphmod.dagwalker(web.repo, smartset.baseset(revs))
        # As we said one line above... not lazy.
        tree = list(
            item
            for item in graphmod.colored(dag, web.repo)
            if item[1] == graphmod.CHANGESET
        )

    def fulltree():
        pos = web.repo[graphtop].rev()
        tree = []
        if pos != -1:
            revs = web.repo.changelog.revs(pos, lastrev)
            dag = graphmod.dagwalker(web.repo, smartset.baseset(revs))
            tree = list(
                item
                for item in graphmod.colored(dag, web.repo)
                if item[1] == graphmod.CHANGESET
            )
        return tree

    def jsdata(context):
        for (id, type, ctx, vtx, edges) in fulltree():
            yield {
                b'node': pycompat.bytestr(ctx),
                b'graphnode': webutil.getgraphnode(web.repo, ctx),
                b'vertex': vtx,
                b'edges': edges,
            }

    def nodes(context):
        parity = paritygen(web.stripecount)
        for row, (id, type, ctx, vtx, edges) in enumerate(tree):
            entry = webutil.commonentry(web.repo, ctx)
            edgedata = [
                {
                    b'col': edge[0],
                    b'nextcol': edge[1],
                    b'color': (edge[2] - 1) % 6 + 1,
                    b'width': edge[3],
                    b'bcolor': edge[4],
                }
                for edge in edges
            ]

            entry.update(
                {
                    b'col': vtx[0],
                    b'color': (vtx[1] - 1) % 6 + 1,
                    b'parity': next(parity),
                    b'edges': templateutil.mappinglist(edgedata),
                    b'row': row,
                    b'nextrow': row + 1,
                }
            )

            yield entry

    rows = len(tree)

    return web.sendtemplate(
        b'graph',
        rev=rev,
        symrev=symrev,
        revcount=revcount,
        uprev=uprev,
        lessvars=lessvars,
        morevars=morevars,
        downrev=downrev,
        graphvars=graphvars,
        rows=rows,
        bg_height=bg_height,
        changesets=count,
        nextentry=templateutil.mappinglist(nextentry),
        jsdata=templateutil.mappinggenerator(jsdata),
        nodes=templateutil.mappinggenerator(nodes),
        node=ctx.hex(),
        archives=web.archivelist(b'tip'),
        changenav=changenav,
    )


def _getdoc(e):
    doc = e[0].__doc__
    if doc:
        doc = _(doc).partition(b'\n')[0]
    else:
        doc = _(b'(no help text available)')
    return doc


@webcommand(b'help')
def help(web):
    """
    /help[/{topic}]
    ---------------

    Render help documentation.

    This web command is roughly equivalent to :hg:`help`. If a ``topic``
    is defined, that help topic will be rendered. If not, an index of
    available help topics will be rendered.

    The ``help`` template will be rendered when requesting help for a topic.
    ``helptopics`` will be rendered for the index of help topics.
    """
    from .. import commands, help as helpmod  # avoid cycle

    topicname = web.req.qsparams.get(b'node')
    if not topicname:

        def topics(context):
            for h in helpmod.helptable:
                entries, summary, _doc = h[0:3]
                yield {b'topic': entries[0], b'summary': summary}

        early, other = [], []
        primary = lambda s: s.partition(b'|')[0]
        for c, e in commands.table.items():
            doc = _getdoc(e)
            if b'DEPRECATED' in doc or c.startswith(b'debug'):
                continue
            cmd = primary(c)
            if getattr(e[0], 'helpbasic', False):
                early.append((cmd, doc))
            else:
                other.append((cmd, doc))

        early.sort()
        other.sort()

        def earlycommands(context):
            for c, doc in early:
                yield {b'topic': c, b'summary': doc}

        def othercommands(context):
            for c, doc in other:
                yield {b'topic': c, b'summary': doc}

        return web.sendtemplate(
            b'helptopics',
            topics=templateutil.mappinggenerator(topics),
            earlycommands=templateutil.mappinggenerator(earlycommands),
            othercommands=templateutil.mappinggenerator(othercommands),
            title=b'Index',
        )

    # Render an index of sub-topics.
    if topicname in helpmod.subtopics:
        topics = []
        for entries, summary, _doc in helpmod.subtopics[topicname]:
            topics.append(
                {
                    b'topic': b'%s.%s' % (topicname, entries[0]),
                    b'basename': entries[0],
                    b'summary': summary,
                }
            )

        return web.sendtemplate(
            b'helptopics',
            topics=templateutil.mappinglist(topics),
            title=topicname,
            subindex=True,
        )

    u = webutil.wsgiui.load()
    u.verbose = True

    # Render a page from a sub-topic.
    if b'.' in topicname:
        # TODO implement support for rendering sections, like
        # `hg help` works.
        topic, subtopic = topicname.split(b'.', 1)
        if topic not in helpmod.subtopics:
            raise ErrorResponse(HTTP_NOT_FOUND)
    else:
        topic = topicname
        subtopic = None

    try:
        doc = helpmod.help_(u, commands, topic, subtopic=subtopic)
    except error.Abort:
        raise ErrorResponse(HTTP_NOT_FOUND)

    return web.sendtemplate(b'help', topic=topicname, doc=doc)


# tell hggettext to extract docstrings from these functions:
i18nfunctions = commands.values()
