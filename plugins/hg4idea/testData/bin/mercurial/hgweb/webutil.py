# hgweb/webutil.py - utility library for the web interface.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os, copy
from mercurial import match, patch, util, error
from mercurial.node import hex, nullid

def up(p):
    if p[0] != "/":
        p = "/" + p
    if p[-1] == "/":
        p = p[:-1]
    up = os.path.dirname(p)
    if up == "/":
        return "/"
    return up + "/"

def revnavgen(pos, pagelen, limit, nodefunc):
    def seq(factor, limit=None):
        if limit:
            yield limit
            if limit >= 20 and limit <= 40:
                yield 50
        else:
            yield 1 * factor
            yield 3 * factor
        for f in seq(factor * 10):
            yield f

    navbefore = []
    navafter = []

    last = 0
    for f in seq(1, pagelen):
        if f < pagelen or f <= last:
            continue
        if f > limit:
            break
        last = f
        if pos + f < limit:
            navafter.append(("+%d" % f, hex(nodefunc(pos + f).node())))
        if pos - f >= 0:
            navbefore.insert(0, ("-%d" % f, hex(nodefunc(pos - f).node())))

    navafter.append(("tip", "tip"))
    try:
        navbefore.insert(0, ("(0)", hex(nodefunc('0').node())))
    except error.RepoError:
        pass

    def gen(l):
        def f(**map):
            for label, node in l:
                yield {"label": label, "node": node}
        return f

    return (dict(before=gen(navbefore), after=gen(navafter)),)

def _siblings(siblings=[], hiderev=None):
    siblings = [s for s in siblings if s.node() != nullid]
    if len(siblings) == 1 and siblings[0].rev() == hiderev:
        return
    for s in siblings:
        d = {'node': hex(s.node()), 'rev': s.rev()}
        d['user'] = s.user()
        d['date'] = s.date()
        d['description'] = s.description()
        d['branch'] = s.branch()
        if hasattr(s, 'path'):
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

def nodebranchdict(repo, ctx):
    branches = []
    branch = ctx.branch()
    # If this is an empty repo, ctx.node() == nullid,
    # ctx.branch() == 'default', but branchtags() is
    # an empty dict. Using dict.get avoids a traceback.
    if repo.branchtags().get(branch) == ctx.node():
        branches.append({"name": branch})
    return branches

def nodeinbranch(repo, ctx):
    branches = []
    branch = ctx.branch()
    if branch != 'default' and repo.branchtags().get(branch) != ctx.node():
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

def cleanpath(repo, path):
    path = path.lstrip('/')
    return util.canonpath(repo.root, '', path)

def changectx(repo, req):
    changeid = "tip"
    if 'node' in req.form:
        changeid = req.form['node'][0]
    elif 'manifest' in req.form:
        changeid = req.form['manifest'][0]

    try:
        ctx = repo[changeid]
    except error.RepoError:
        man = repo.manifest
        ctx = repo[man.linkrev(man.rev(man.lookup(changeid)))]

    return ctx

def filectx(repo, req):
    path = cleanpath(repo, req.form['file'][0])
    if 'node' in req.form:
        changeid = req.form['node'][0]
    else:
        changeid = req.form['filenode'][0]
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

def diffs(repo, tmpl, ctx, files, parity, style):

    def countgen():
        start = 1
        while True:
            yield start
            start += 1

    blockcount = countgen()
    def prettyprintlines(diff):
        blockno = blockcount.next()
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
    parents = ctx.parents()
    node1 = parents and parents[0].node() or nullid
    node2 = ctx.node()

    block = []
    for chunk in patch.diff(repo, node1, node2, m, opts=diffopts):
        if chunk.startswith('diff') and block:
            yield tmpl('diffblock', parity=parity.next(),
                       lines=prettyprintlines(''.join(block)))
            block = []
        if chunk.startswith('diff') and style != 'raw':
            chunk = ''.join(chunk.splitlines(True)[1:])
        block.append(chunk)
    yield tmpl('diffblock', parity=parity.next(),
               lines=prettyprintlines(''.join(block)))

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
        for key, value in self.vars.iteritems():
            yield {'name': key, 'value': str(value), 'separator': separator}
            separator = '&'
