# hgweb/webutil.py - utility library for the web interface.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import copy
import difflib
import os
import re

from ..i18n import _
from ..node import hex, short
from ..pycompat import setattr

from .common import (
    ErrorResponse,
    HTTP_BAD_REQUEST,
    HTTP_NOT_FOUND,
    paritygen,
)

from .. import (
    context,
    diffutil,
    error,
    match,
    mdiff,
    obsutil,
    patch,
    pathutil,
    pycompat,
    scmutil,
    templatefilters,
    templatekw,
    templateutil,
    ui as uimod,
    util,
)

from ..utils import stringutil

archivespecs = util.sortdict(
    (
        (b'zip', (b'application/zip', b'zip', b'.zip', None)),
        (b'gz', (b'application/x-gzip', b'tgz', b'.tar.gz', None)),
        (b'bz2', (b'application/x-bzip2', b'tbz2', b'.tar.bz2', None)),
    )
)


def archivelist(ui, nodeid, url=None):
    allowed = ui.configlist(b'web', b'allow-archive', untrusted=True)
    archives = []

    for typ, spec in pycompat.iteritems(archivespecs):
        if typ in allowed or ui.configbool(
            b'web', b'allow' + typ, untrusted=True
        ):
            archives.append(
                {
                    b'type': typ,
                    b'extension': spec[2],
                    b'node': nodeid,
                    b'url': url,
                }
            )

    return templateutil.mappinglist(archives)


def up(p):
    if p[0:1] != b"/":
        p = b"/" + p
    if p[-1:] == b"/":
        p = p[:-1]
    up = os.path.dirname(p)
    if up == b"/":
        return b"/"
    return up + b"/"


def _navseq(step, firststep=None):
    if firststep:
        yield firststep
        if firststep >= 20 and firststep <= 40:
            firststep = 50
            yield firststep
        assert step > 0
        assert firststep > 0
        while step <= firststep:
            step *= 10
    while True:
        yield 1 * step
        yield 3 * step
        step *= 10


class revnav(object):
    def __init__(self, repo):
        """Navigation generation object

        :repo: repo object we generate nav for
        """
        # used for hex generation
        self._revlog = repo.changelog

    def __nonzero__(self):
        """return True if any revision to navigate over"""
        return self._first() is not None

    __bool__ = __nonzero__

    def _first(self):
        """return the minimum non-filtered changeset or None"""
        try:
            return next(iter(self._revlog))
        except StopIteration:
            return None

    def hex(self, rev):
        return hex(self._revlog.node(rev))

    def gen(self, pos, pagelen, limit):
        """computes label and revision id for navigation link

        :pos: is the revision relative to which we generate navigation.
        :pagelen: the size of each navigation page
        :limit: how far shall we link

        The return is:
            - a single element mappinglist
            - containing a dictionary with a `before` and `after` key
            - values are dictionaries with `label` and `node` keys
        """
        if not self:
            # empty repo
            return templateutil.mappinglist(
                [
                    {
                        b'before': templateutil.mappinglist([]),
                        b'after': templateutil.mappinglist([]),
                    },
                ]
            )

        targets = []
        for f in _navseq(1, pagelen):
            if f > limit:
                break
            targets.append(pos + f)
            targets.append(pos - f)
        targets.sort()

        first = self._first()
        navbefore = [{b'label': b'(%i)' % first, b'node': self.hex(first)}]
        navafter = []
        for rev in targets:
            if rev not in self._revlog:
                continue
            if pos < rev < limit:
                navafter.append(
                    {b'label': b'+%d' % abs(rev - pos), b'node': self.hex(rev)}
                )
            if 0 < rev < pos:
                navbefore.append(
                    {b'label': b'-%d' % abs(rev - pos), b'node': self.hex(rev)}
                )

        navafter.append({b'label': b'tip', b'node': b'tip'})

        # TODO: maybe this can be a scalar object supporting tomap()
        return templateutil.mappinglist(
            [
                {
                    b'before': templateutil.mappinglist(navbefore),
                    b'after': templateutil.mappinglist(navafter),
                },
            ]
        )


class filerevnav(revnav):
    def __init__(self, repo, path):
        """Navigation generation object

        :repo: repo object we generate nav for
        :path: path of the file we generate nav for
        """
        # used for iteration
        self._changelog = repo.unfiltered().changelog
        # used for hex generation
        self._revlog = repo.file(path)

    def hex(self, rev):
        return hex(self._changelog.node(self._revlog.linkrev(rev)))


# TODO: maybe this can be a wrapper class for changectx/filectx list, which
# yields {'ctx': ctx}
def _ctxsgen(context, ctxs):
    for s in ctxs:
        d = {
            b'node': s.hex(),
            b'rev': s.rev(),
            b'user': s.user(),
            b'date': s.date(),
            b'description': s.description(),
            b'branch': s.branch(),
        }
        if util.safehasattr(s, b'path'):
            d[b'file'] = s.path()
        yield d


def _siblings(siblings=None, hiderev=None):
    if siblings is None:
        siblings = []
    siblings = [s for s in siblings if s.node() != s.repo().nullid]
    if len(siblings) == 1 and siblings[0].rev() == hiderev:
        siblings = []
    return templateutil.mappinggenerator(_ctxsgen, args=(siblings,))


def difffeatureopts(req, ui, section):
    diffopts = diffutil.difffeatureopts(
        ui, untrusted=True, section=section, whitespace=True
    )

    for k in (
        b'ignorews',
        b'ignorewsamount',
        b'ignorewseol',
        b'ignoreblanklines',
    ):
        v = req.qsparams.get(k)
        if v is not None:
            v = stringutil.parsebool(v)
            setattr(diffopts, k, v if v is not None else True)

    return diffopts


def annotate(req, fctx, ui):
    diffopts = difffeatureopts(req, ui, b'annotate')
    return fctx.annotate(follow=True, diffopts=diffopts)


def parents(ctx, hide=None):
    if isinstance(ctx, context.basefilectx):
        introrev = ctx.introrev()
        if ctx.changectx().rev() != introrev:
            return _siblings([ctx.repo()[introrev]], hide)
    return _siblings(ctx.parents(), hide)


def children(ctx, hide=None):
    return _siblings(ctx.children(), hide)


def renamelink(fctx):
    r = fctx.renamed()
    if r:
        return templateutil.mappinglist([{b'file': r[0], b'node': hex(r[1])}])
    return templateutil.mappinglist([])


def nodetagsdict(repo, node):
    return templateutil.hybridlist(repo.nodetags(node), name=b'name')


def nodebookmarksdict(repo, node):
    return templateutil.hybridlist(repo.nodebookmarks(node), name=b'name')


def nodebranchdict(repo, ctx):
    branches = []
    branch = ctx.branch()
    # If this is an empty repo, ctx.node() == nullid,
    # ctx.branch() == 'default'.
    try:
        branchnode = repo.branchtip(branch)
    except error.RepoLookupError:
        branchnode = None
    if branchnode == ctx.node():
        branches.append(branch)
    return templateutil.hybridlist(branches, name=b'name')


def nodeinbranch(repo, ctx):
    branches = []
    branch = ctx.branch()
    try:
        branchnode = repo.branchtip(branch)
    except error.RepoLookupError:
        branchnode = None
    if branch != b'default' and branchnode != ctx.node():
        branches.append(branch)
    return templateutil.hybridlist(branches, name=b'name')


def nodebranchnodefault(ctx):
    branches = []
    branch = ctx.branch()
    if branch != b'default':
        branches.append(branch)
    return templateutil.hybridlist(branches, name=b'name')


def _nodenamesgen(context, f, node, name):
    for t in f(node):
        yield {name: t}


def showtag(repo, t1, node=None):
    if node is None:
        node = repo.nullid
    args = (repo.nodetags, node, b'tag')
    return templateutil.mappinggenerator(_nodenamesgen, args=args, name=t1)


def showbookmark(repo, t1, node=None):
    if node is None:
        node = repo.nullid
    args = (repo.nodebookmarks, node, b'bookmark')
    return templateutil.mappinggenerator(_nodenamesgen, args=args, name=t1)


def branchentries(repo, stripecount, limit=0):
    tips = []
    heads = repo.heads()
    parity = paritygen(stripecount)
    sortkey = lambda item: (not item[1], item[0].rev())

    def entries(context):
        count = 0
        if not tips:
            for tag, hs, tip, closed in repo.branchmap().iterbranches():
                tips.append((repo[tip], closed))
        for ctx, closed in sorted(tips, key=sortkey, reverse=True):
            if limit > 0 and count >= limit:
                return
            count += 1
            if closed:
                status = b'closed'
            elif ctx.node() not in heads:
                status = b'inactive'
            else:
                status = b'open'
            yield {
                b'parity': next(parity),
                b'branch': ctx.branch(),
                b'status': status,
                b'node': ctx.hex(),
                b'date': ctx.date(),
            }

    return templateutil.mappinggenerator(entries)


def cleanpath(repo, path):
    path = path.lstrip(b'/')
    auditor = pathutil.pathauditor(repo.root, realfs=False)
    return pathutil.canonpath(repo.root, b'', path, auditor=auditor)


def changectx(repo, req):
    changeid = b"tip"
    if b'node' in req.qsparams:
        changeid = req.qsparams[b'node']
        ipos = changeid.find(b':')
        if ipos != -1:
            changeid = changeid[(ipos + 1) :]

    return scmutil.revsymbol(repo, changeid)


def basechangectx(repo, req):
    if b'node' in req.qsparams:
        changeid = req.qsparams[b'node']
        ipos = changeid.find(b':')
        if ipos != -1:
            changeid = changeid[:ipos]
            return scmutil.revsymbol(repo, changeid)

    return None


def filectx(repo, req):
    if b'file' not in req.qsparams:
        raise ErrorResponse(HTTP_NOT_FOUND, b'file not given')
    path = cleanpath(repo, req.qsparams[b'file'])
    if b'node' in req.qsparams:
        changeid = req.qsparams[b'node']
    elif b'filenode' in req.qsparams:
        changeid = req.qsparams[b'filenode']
    else:
        raise ErrorResponse(HTTP_NOT_FOUND, b'node or filenode not given')
    try:
        fctx = scmutil.revsymbol(repo, changeid)[path]
    except error.RepoError:
        fctx = repo.filectx(path, fileid=changeid)

    return fctx


def linerange(req):
    linerange = req.qsparams.getall(b'linerange')
    if not linerange:
        return None
    if len(linerange) > 1:
        raise ErrorResponse(HTTP_BAD_REQUEST, b'redundant linerange parameter')
    try:
        fromline, toline = map(int, linerange[0].split(b':', 1))
    except ValueError:
        raise ErrorResponse(HTTP_BAD_REQUEST, b'invalid linerange parameter')
    try:
        return util.processlinerange(fromline, toline)
    except error.ParseError as exc:
        raise ErrorResponse(HTTP_BAD_REQUEST, pycompat.bytestr(exc))


def formatlinerange(fromline, toline):
    return b'%d:%d' % (fromline + 1, toline)


def _succsandmarkersgen(context, mapping):
    repo = context.resource(mapping, b'repo')
    itemmappings = templatekw.showsuccsandmarkers(context, mapping)
    for item in itemmappings.tovalue(context, mapping):
        item[b'successors'] = _siblings(
            repo[successor] for successor in item[b'successors']
        )
        yield item


def succsandmarkers(context, mapping):
    return templateutil.mappinggenerator(_succsandmarkersgen, args=(mapping,))


# teach templater succsandmarkers is switched to (context, mapping) API
succsandmarkers._requires = {b'repo', b'ctx'}


def _whyunstablegen(context, mapping):
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')

    entries = obsutil.whyunstable(repo, ctx)
    for entry in entries:
        if entry.get(b'divergentnodes'):
            entry[b'divergentnodes'] = _siblings(entry[b'divergentnodes'])
        yield entry


def whyunstable(context, mapping):
    return templateutil.mappinggenerator(_whyunstablegen, args=(mapping,))


whyunstable._requires = {b'repo', b'ctx'}


def commonentry(repo, ctx):
    node = scmutil.binnode(ctx)
    return {
        # TODO: perhaps ctx.changectx() should be assigned if ctx is a
        # filectx, but I'm not pretty sure if that would always work because
        # fctx.parents() != fctx.changectx.parents() for example.
        b'ctx': ctx,
        b'rev': ctx.rev(),
        b'node': hex(node),
        b'author': ctx.user(),
        b'desc': ctx.description(),
        b'date': ctx.date(),
        b'extra': ctx.extra(),
        b'phase': ctx.phasestr(),
        b'obsolete': ctx.obsolete(),
        b'succsandmarkers': succsandmarkers,
        b'instabilities': templateutil.hybridlist(
            ctx.instabilities(), name=b'instability'
        ),
        b'whyunstable': whyunstable,
        b'branch': nodebranchnodefault(ctx),
        b'inbranch': nodeinbranch(repo, ctx),
        b'branches': nodebranchdict(repo, ctx),
        b'tags': nodetagsdict(repo, node),
        b'bookmarks': nodebookmarksdict(repo, node),
        b'parent': lambda context, mapping: parents(ctx),
        b'child': lambda context, mapping: children(ctx),
    }


def changelistentry(web, ctx):
    """Obtain a dictionary to be used for entries in a changelist.

    This function is called when producing items for the "entries" list passed
    to the "shortlog" and "changelog" templates.
    """
    repo = web.repo
    rev = ctx.rev()
    n = scmutil.binnode(ctx)
    showtags = showtag(repo, b'changelogtag', n)
    files = listfilediffs(ctx.files(), n, web.maxfiles)

    entry = commonentry(repo, ctx)
    entry.update(
        {
            b'allparents': lambda context, mapping: parents(ctx),
            b'parent': lambda context, mapping: parents(ctx, rev - 1),
            b'child': lambda context, mapping: children(ctx, rev + 1),
            b'changelogtag': showtags,
            b'files': files,
        }
    )
    return entry


def changelistentries(web, revs, maxcount, parityfn):
    """Emit up to N records for an iterable of revisions."""
    repo = web.repo

    count = 0
    for rev in revs:
        if count >= maxcount:
            break

        count += 1

        entry = changelistentry(web, repo[rev])
        entry[b'parity'] = next(parityfn)

        yield entry


def symrevorshortnode(req, ctx):
    if b'node' in req.qsparams:
        return templatefilters.revescape(req.qsparams[b'node'])
    else:
        return short(scmutil.binnode(ctx))


def _listfilesgen(context, ctx, stripecount):
    parity = paritygen(stripecount)
    filesadded = ctx.filesadded()
    for blockno, f in enumerate(ctx.files()):
        if f not in ctx:
            status = b'removed'
        elif f in filesadded:
            status = b'added'
        else:
            status = b'modified'
        template = b'filenolink' if status == b'removed' else b'filenodelink'
        yield context.process(
            template,
            {
                b'node': ctx.hex(),
                b'file': f,
                b'blockno': blockno + 1,
                b'parity': next(parity),
                b'status': status,
            },
        )


def changesetentry(web, ctx):
    '''Obtain a dictionary to be used to render the "changeset" template.'''

    showtags = showtag(web.repo, b'changesettag', scmutil.binnode(ctx))
    showbookmarks = showbookmark(
        web.repo, b'changesetbookmark', scmutil.binnode(ctx)
    )
    showbranch = nodebranchnodefault(ctx)

    basectx = basechangectx(web.repo, web.req)
    if basectx is None:
        basectx = ctx.p1()

    style = web.config(b'web', b'style')
    if b'style' in web.req.qsparams:
        style = web.req.qsparams[b'style']

    diff = diffs(web, ctx, basectx, None, style)

    parity = paritygen(web.stripecount)
    diffstatsgen = diffstatgen(web.repo.ui, ctx, basectx)
    diffstats = diffstat(ctx, diffstatsgen, parity)

    return dict(
        diff=diff,
        symrev=symrevorshortnode(web.req, ctx),
        basenode=basectx.hex(),
        changesettag=showtags,
        changesetbookmark=showbookmarks,
        changesetbranch=showbranch,
        files=templateutil.mappedgenerator(
            _listfilesgen, args=(ctx, web.stripecount)
        ),
        diffsummary=lambda context, mapping: diffsummary(diffstatsgen),
        diffstat=diffstats,
        archives=web.archivelist(ctx.hex()),
        **pycompat.strkwargs(commonentry(web.repo, ctx))
    )


def _listfilediffsgen(context, files, node, max):
    for f in files[:max]:
        yield context.process(b'filedifflink', {b'node': hex(node), b'file': f})
    if len(files) > max:
        yield context.process(b'fileellipses', {})


def listfilediffs(files, node, max):
    return templateutil.mappedgenerator(
        _listfilediffsgen, args=(files, node, max)
    )


def _prettyprintdifflines(context, lines, blockno, lineidprefix):
    for lineno, l in enumerate(lines, 1):
        difflineno = b"%d.%d" % (blockno, lineno)
        if l.startswith(b'+'):
            ltype = b"difflineplus"
        elif l.startswith(b'-'):
            ltype = b"difflineminus"
        elif l.startswith(b'@'):
            ltype = b"difflineat"
        else:
            ltype = b"diffline"
        yield context.process(
            ltype,
            {
                b'line': l,
                b'lineno': lineno,
                b'lineid': lineidprefix + b"l%s" % difflineno,
                b'linenumber': b"% 8s" % difflineno,
            },
        )


def _diffsgen(
    context,
    repo,
    ctx,
    basectx,
    files,
    style,
    stripecount,
    linerange,
    lineidprefix,
):
    if files:
        m = match.exact(files)
    else:
        m = match.always()

    diffopts = patch.diffopts(repo.ui, untrusted=True)
    parity = paritygen(stripecount)

    diffhunks = patch.diffhunks(repo, basectx, ctx, m, opts=diffopts)
    for blockno, (fctx1, fctx2, header, hunks) in enumerate(diffhunks, 1):
        if style != b'raw':
            header = header[1:]
        lines = [h + b'\n' for h in header]
        for hunkrange, hunklines in hunks:
            if linerange is not None and hunkrange is not None:
                s1, l1, s2, l2 = hunkrange
                if not mdiff.hunkinrange((s2, l2), linerange):
                    continue
            lines.extend(hunklines)
        if lines:
            l = templateutil.mappedgenerator(
                _prettyprintdifflines, args=(lines, blockno, lineidprefix)
            )
            yield {
                b'parity': next(parity),
                b'blockno': blockno,
                b'lines': l,
            }


def diffs(web, ctx, basectx, files, style, linerange=None, lineidprefix=b''):
    args = (
        web.repo,
        ctx,
        basectx,
        files,
        style,
        web.stripecount,
        linerange,
        lineidprefix,
    )
    return templateutil.mappinggenerator(
        _diffsgen, args=args, name=b'diffblock'
    )


def _compline(type, leftlineno, leftline, rightlineno, rightline):
    lineid = leftlineno and (b"l%d" % leftlineno) or b''
    lineid += rightlineno and (b"r%d" % rightlineno) or b''
    llno = b'%d' % leftlineno if leftlineno else b''
    rlno = b'%d' % rightlineno if rightlineno else b''
    return {
        b'type': type,
        b'lineid': lineid,
        b'leftlineno': leftlineno,
        b'leftlinenumber': b"% 6s" % llno,
        b'leftline': leftline or b'',
        b'rightlineno': rightlineno,
        b'rightlinenumber': b"% 6s" % rlno,
        b'rightline': rightline or b'',
    }


def _getcompblockgen(context, leftlines, rightlines, opcodes):
    for type, llo, lhi, rlo, rhi in opcodes:
        type = pycompat.sysbytes(type)
        len1 = lhi - llo
        len2 = rhi - rlo
        count = min(len1, len2)
        for i in pycompat.xrange(count):
            yield _compline(
                type=type,
                leftlineno=llo + i + 1,
                leftline=leftlines[llo + i],
                rightlineno=rlo + i + 1,
                rightline=rightlines[rlo + i],
            )
        if len1 > len2:
            for i in pycompat.xrange(llo + count, lhi):
                yield _compline(
                    type=type,
                    leftlineno=i + 1,
                    leftline=leftlines[i],
                    rightlineno=None,
                    rightline=None,
                )
        elif len2 > len1:
            for i in pycompat.xrange(rlo + count, rhi):
                yield _compline(
                    type=type,
                    leftlineno=None,
                    leftline=None,
                    rightlineno=i + 1,
                    rightline=rightlines[i],
                )


def _getcompblock(leftlines, rightlines, opcodes):
    args = (leftlines, rightlines, opcodes)
    return templateutil.mappinggenerator(
        _getcompblockgen, args=args, name=b'comparisonline'
    )


def _comparegen(context, contextnum, leftlines, rightlines):
    '''Generator function that provides side-by-side comparison data.'''
    s = difflib.SequenceMatcher(None, leftlines, rightlines)
    if contextnum < 0:
        l = _getcompblock(leftlines, rightlines, s.get_opcodes())
        yield {b'lines': l}
    else:
        for oc in s.get_grouped_opcodes(n=contextnum):
            l = _getcompblock(leftlines, rightlines, oc)
            yield {b'lines': l}


def compare(contextnum, leftlines, rightlines):
    args = (contextnum, leftlines, rightlines)
    return templateutil.mappinggenerator(
        _comparegen, args=args, name=b'comparisonblock'
    )


def diffstatgen(ui, ctx, basectx):
    '''Generator function that provides the diffstat data.'''

    diffopts = patch.diffopts(ui, {b'noprefix': False})
    stats = patch.diffstatdata(util.iterlines(ctx.diff(basectx, opts=diffopts)))
    maxname, maxtotal, addtotal, removetotal, binary = patch.diffstatsum(stats)
    while True:
        yield stats, maxname, maxtotal, addtotal, removetotal, binary


def diffsummary(statgen):
    '''Return a short summary of the diff.'''

    stats, maxname, maxtotal, addtotal, removetotal, binary = next(statgen)
    return _(b' %d files changed, %d insertions(+), %d deletions(-)\n') % (
        len(stats),
        addtotal,
        removetotal,
    )


def _diffstattmplgen(context, ctx, statgen, parity):
    stats, maxname, maxtotal, addtotal, removetotal, binary = next(statgen)
    files = ctx.files()

    def pct(i):
        if maxtotal == 0:
            return 0
        return (float(i) / maxtotal) * 100

    fileno = 0
    for filename, adds, removes, isbinary in stats:
        template = b'diffstatlink' if filename in files else b'diffstatnolink'
        total = adds + removes
        fileno += 1
        yield context.process(
            template,
            {
                b'node': ctx.hex(),
                b'file': filename,
                b'fileno': fileno,
                b'total': total,
                b'addpct': pct(adds),
                b'removepct': pct(removes),
                b'parity': next(parity),
            },
        )


def diffstat(ctx, statgen, parity):
    '''Return a diffstat template for each file in the diff.'''
    args = (ctx, statgen, parity)
    return templateutil.mappedgenerator(_diffstattmplgen, args=args)


class sessionvars(templateutil.wrapped):
    def __init__(self, vars, start=b'?'):
        self._start = start
        self._vars = vars

    def __getitem__(self, key):
        return self._vars[key]

    def __setitem__(self, key, value):
        self._vars[key] = value

    def __copy__(self):
        return sessionvars(copy.copy(self._vars), self._start)

    def contains(self, context, mapping, item):
        item = templateutil.unwrapvalue(context, mapping, item)
        return item in self._vars

    def getmember(self, context, mapping, key):
        key = templateutil.unwrapvalue(context, mapping, key)
        return self._vars.get(key)

    def getmin(self, context, mapping):
        raise error.ParseError(_(b'not comparable'))

    def getmax(self, context, mapping):
        raise error.ParseError(_(b'not comparable'))

    def filter(self, context, mapping, select):
        # implement if necessary
        raise error.ParseError(_(b'not filterable'))

    def itermaps(self, context):
        separator = self._start
        for key, value in sorted(pycompat.iteritems(self._vars)):
            yield {
                b'name': key,
                b'value': pycompat.bytestr(value),
                b'separator': separator,
            }
            separator = b'&'

    def join(self, context, mapping, sep):
        # could be '{separator}{name}={value|urlescape}'
        raise error.ParseError(_(b'not displayable without template'))

    def show(self, context, mapping):
        return self.join(context, mapping, b'')

    def tobool(self, context, mapping):
        return bool(self._vars)

    def tovalue(self, context, mapping):
        return self._vars


class wsgiui(uimod.ui):
    # default termwidth breaks under mod_wsgi
    def termwidth(self):
        return 80


def getwebsubs(repo):
    websubtable = []
    websubdefs = repo.ui.configitems(b'websub')
    # we must maintain interhg backwards compatibility
    websubdefs += repo.ui.configitems(b'interhg')
    for key, pattern in websubdefs:
        # grab the delimiter from the character after the "s"
        unesc = pattern[1:2]
        delim = stringutil.reescape(unesc)

        # identify portions of the pattern, taking care to avoid escaped
        # delimiters. the replace format and flags are optional, but
        # delimiters are required.
        match = re.match(
            br'^s%s(.+)(?:(?<=\\\\)|(?<!\\))%s(.*)%s([ilmsux])*$'
            % (delim, delim, delim),
            pattern,
        )
        if not match:
            repo.ui.warn(
                _(b"websub: invalid pattern for %s: %s\n") % (key, pattern)
            )
            continue

        # we need to unescape the delimiter for regexp and format
        delim_re = re.compile(br'(?<!\\)\\%s' % delim)
        regexp = delim_re.sub(unesc, match.group(1))
        format = delim_re.sub(unesc, match.group(2))

        # the pattern allows for 6 regexp flags, so set them if necessary
        flagin = match.group(3)
        flags = 0
        if flagin:
            for flag in pycompat.sysstr(flagin.upper()):
                flags |= re.__dict__[flag]

        try:
            regexp = re.compile(regexp, flags)
            websubtable.append((regexp, format))
        except re.error:
            repo.ui.warn(
                _(b"websub: invalid regexp for %s: %s\n") % (key, regexp)
            )
    return websubtable


def getgraphnode(repo, ctx):
    return templatekw.getgraphnodecurrent(
        repo, ctx, {}
    ) + templatekw.getgraphnodesymbol(ctx)
