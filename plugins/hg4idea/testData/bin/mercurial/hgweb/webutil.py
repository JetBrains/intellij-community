# hgweb/webutil.py - utility library for the web interface.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os, copy
from mercurial import match, patch, scmutil, error, ui, util
from mercurial.i18n import _
from mercurial.node import hex, nullid
from common import ErrorResponse
from common import HTTP_NOT_FOUND
import difflib

def up(p):
    if p[0] != "/":
        p = "/" + p
    if p[-1] == "/":
        p = p[:-1]
    up = os.path.dirname(p)
    if up == "/":
        return "/"
    return up + "/"

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

    def _first(self):
        """return the minimum non-filtered changeset or None"""
        try:
            return iter(self._revlog).next()
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
            - a single element tuple
            - containing a dictionary with a `before` and `after` key
            - values are generator functions taking arbitrary number of kwargs
            - yield items are dictionaries with `label` and `node` keys
        """
        if not self:
            # empty repo
            return ({'before': (), 'after': ()},)

        targets = []
        for f in _navseq(1, pagelen):
            if f > limit:
                break
            targets.append(pos + f)
            targets.append(pos - f)
        targets.sort()

        first = self._first()
        navbefore = [("(%i)" % first, self.hex(first))]
        navafter = []
        for rev in targets:
            if rev not in self._revlog:
                continue
            if pos < rev < limit:
                navafter.append(("+%d" % abs(rev - pos), self.hex(rev)))
            if 0 < rev < pos:
                navbefore.append(("-%d" % abs(rev - pos), self.hex(rev)))


        navafter.append(("tip", "tip"))

        data = lambda i: {"label": i[0], "node": i[1]}
        return ({'before': lambda **map: (data(i) for i in navbefore),
                 'after':  lambda **map: (data(i) for i in navafter)},)

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


def _siblings(siblings=[], hiderev=None):
    siblings = [s for s in siblings if s.node() != nullid]
    if len(siblings) == 1 and siblings[0].rev() == hiderev:
        return
    for s in siblings:
        d = {'node': s.hex(), 'rev': s.rev()}
        d['user'] = s.user()
        d['date'] = s.date()
        d['description'] = s.description()
        d['branch'] = s.branch()
        if util.safehasattr(s, 'path'):
            d['file'] = s.path()
        yield d

def parents(ctx, hide=None):
    return _siblings(ctx.parents(), hide)

def children(ctx, hide=None):
    return _siblings(ctx.children(), hide)

def renamelink(fctx):
    r = fctx.renamed()
    if r:
        return [dict(file=r[0], node=hex(r[1]))]
    return []

def nodetagsdict(repo, node):
    return [{"name": i} for i in repo.nodetags(node)]

def nodebookmarksdict(repo, node):
    return [{"name": i} for i in repo.nodebookmarks(node)]

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
        branches.append({"name": branch})
    return branches

def nodeinbranch(repo, ctx):
    branches = []
    branch = ctx.branch()
    try:
        branchnode = repo.branchtip(branch)
    except error.RepoLookupError:
        branchnode = None
    if branch != 'default' and branchnode != ctx.node():
        branches.append({"name": branch})
    return branches

def nodebranchnodefault(ctx):
    branches = []
    branch = ctx.branch()
    if branch != 'default':
        branches.append({"name": branch})
    return branches

def showtag(repo, tmpl, t1, node=nullid, **args):
    for t in repo.nodetags(node):
        yield tmpl(t1, tag=t, **args)

def showbookmark(repo, tmpl, t1, node=nullid, **args):
    for t in repo.nodebookmarks(node):
        yield tmpl(t1, bookmark=t, **args)

def cleanpath(repo, path):
    path = path.lstrip('/')
    return scmutil.canonpath(repo.root, '', path)

def changeidctx (repo, changeid):
    try:
        ctx = repo[changeid]
    except error.RepoError:
        man = repo.manifest
        ctx = repo[man.linkrev(man.rev(man.lookup(changeid)))]

    return ctx

def changectx (repo, req):
    changeid = "tip"
    if 'node' in req.form:
        changeid = req.form['node'][0]
        ipos=changeid.find(':')
        if ipos != -1:
            changeid = changeid[(ipos + 1):]
    elif 'manifest' in req.form:
        changeid = req.form['manifest'][0]

    return changeidctx(repo, changeid)

def basechangectx(repo, req):
    if 'node' in req.form:
        changeid = req.form['node'][0]
        ipos=changeid.find(':')
        if ipos != -1:
            changeid = changeid[:ipos]
            return changeidctx(repo, changeid)

    return None

def filectx(repo, req):
    if 'file' not in req.form:
        raise ErrorResponse(HTTP_NOT_FOUND, 'file not given')
    path = cleanpath(repo, req.form['file'][0])
    if 'node' in req.form:
        changeid = req.form['node'][0]
    elif 'filenode' in req.form:
        changeid = req.form['filenode'][0]
    else:
        raise ErrorResponse(HTTP_NOT_FOUND, 'node or filenode not given')
    try:
        fctx = repo[changeid][path]
    except error.RepoError:
        fctx = repo.filectx(path, fileid=changeid)

    return fctx

def listfilediffs(tmpl, files, node, max):
    for f in files[:max]:
        yield tmpl('filedifflink', node=hex(node), file=f)
    if len(files) > max:
        yield tmpl('fileellipses')

def diffs(repo, tmpl, ctx, basectx, files, parity, style):

    def countgen():
        start = 1
        while True:
            yield start
            start += 1

    blockcount = countgen()
    def prettyprintlines(diff, blockno):
        for lineno, l in enumerate(diff.splitlines(True)):
            lineno = "%d.%d" % (blockno, lineno + 1)
            if l.startswith('+'):
                ltype = "difflineplus"
            elif l.startswith('-'):
                ltype = "difflineminus"
            elif l.startswith('@'):
                ltype = "difflineat"
            else:
                ltype = "diffline"
            yield tmpl(ltype,
                       line=l,
                       lineid="l%s" % lineno,
                       linenumber="% 8s" % lineno)

    if files:
        m = match.exact(repo.root, repo.getcwd(), files)
    else:
        m = match.always(repo.root, repo.getcwd())

    diffopts = patch.diffopts(repo.ui, untrusted=True)
    if basectx is None:
        parents = ctx.parents()
        node1 = parents and parents[0].node() or nullid
    else:
        node1 = basectx.node()
    node2 = ctx.node()

    block = []
    for chunk in patch.diff(repo, node1, node2, m, opts=diffopts):
        if chunk.startswith('diff') and block:
            blockno = blockcount.next()
            yield tmpl('diffblock', parity=parity.next(), blockno=blockno,
                       lines=prettyprintlines(''.join(block), blockno))
            block = []
        if chunk.startswith('diff') and style != 'raw':
            chunk = ''.join(chunk.splitlines(True)[1:])
        block.append(chunk)
    blockno = blockcount.next()
    yield tmpl('diffblock', parity=parity.next(), blockno=blockno,
               lines=prettyprintlines(''.join(block), blockno))

def compare(tmpl, context, leftlines, rightlines):
    '''Generator function that provides side-by-side comparison data.'''

    def compline(type, leftlineno, leftline, rightlineno, rightline):
        lineid = leftlineno and ("l%s" % leftlineno) or ''
        lineid += rightlineno and ("r%s" % rightlineno) or ''
        return tmpl('comparisonline',
                    type=type,
                    lineid=lineid,
                    leftlinenumber="% 6s" % (leftlineno or ''),
                    leftline=leftline or '',
                    rightlinenumber="% 6s" % (rightlineno or ''),
                    rightline=rightline or '')

    def getblock(opcodes):
        for type, llo, lhi, rlo, rhi in opcodes:
            len1 = lhi - llo
            len2 = rhi - rlo
            count = min(len1, len2)
            for i in xrange(count):
                yield compline(type=type,
                               leftlineno=llo + i + 1,
                               leftline=leftlines[llo + i],
                               rightlineno=rlo + i + 1,
                               rightline=rightlines[rlo + i])
            if len1 > len2:
                for i in xrange(llo + count, lhi):
                    yield compline(type=type,
                                   leftlineno=i + 1,
                                   leftline=leftlines[i],
                                   rightlineno=None,
                                   rightline=None)
            elif len2 > len1:
                for i in xrange(rlo + count, rhi):
                    yield compline(type=type,
                                   leftlineno=None,
                                   leftline=None,
                                   rightlineno=i + 1,
                                   rightline=rightlines[i])

    s = difflib.SequenceMatcher(None, leftlines, rightlines)
    if context < 0:
        yield tmpl('comparisonblock', lines=getblock(s.get_opcodes()))
    else:
        for oc in s.get_grouped_opcodes(n=context):
            yield tmpl('comparisonblock', lines=getblock(oc))

def diffstatgen(ctx, basectx):
    '''Generator function that provides the diffstat data.'''

    stats = patch.diffstatdata(util.iterlines(ctx.diff(basectx)))
    maxname, maxtotal, addtotal, removetotal, binary = patch.diffstatsum(stats)
    while True:
        yield stats, maxname, maxtotal, addtotal, removetotal, binary

def diffsummary(statgen):
    '''Return a short summary of the diff.'''

    stats, maxname, maxtotal, addtotal, removetotal, binary = statgen.next()
    return _(' %d files changed, %d insertions(+), %d deletions(-)\n') % (
             len(stats), addtotal, removetotal)

def diffstat(tmpl, ctx, statgen, parity):
    '''Return a diffstat template for each file in the diff.'''

    stats, maxname, maxtotal, addtotal, removetotal, binary = statgen.next()
    files = ctx.files()

    def pct(i):
        if maxtotal == 0:
            return 0
        return (float(i) / maxtotal) * 100

    fileno = 0
    for filename, adds, removes, isbinary in stats:
        template = filename in files and 'diffstatlink' or 'diffstatnolink'
        total = adds + removes
        fileno += 1
        yield tmpl(template, node=ctx.hex(), file=filename, fileno=fileno,
                   total=total, addpct=pct(adds), removepct=pct(removes),
                   parity=parity.next())

class sessionvars(object):
    def __init__(self, vars, start='?'):
        self.start = start
        self.vars = vars
    def __getitem__(self, key):
        return self.vars[key]
    def __setitem__(self, key, value):
        self.vars[key] = value
    def __copy__(self):
        return sessionvars(copy.copy(self.vars), self.start)
    def __iter__(self):
        separator = self.start
        for key, value in sorted(self.vars.iteritems()):
            yield {'name': key, 'value': str(value), 'separator': separator}
            separator = '&'

class wsgiui(ui.ui):
    # default termwidth breaks under mod_wsgi
    def termwidth(self):
        return 80
