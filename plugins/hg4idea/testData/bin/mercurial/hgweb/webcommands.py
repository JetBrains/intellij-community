#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os, mimetypes, re, cgi, copy
import webutil
from mercurial import error, archival, templater, templatefilters
from mercurial.node import short, hex
from mercurial.util import binary
from common import paritygen, staticfile, get_contact, ErrorResponse
from common import HTTP_OK, HTTP_FORBIDDEN, HTTP_NOT_FOUND
from mercurial import graphmod

# __all__ is populated with the allowed commands. Be sure to add to it if
# you're adding a new command, or the new command won't work.

__all__ = [
   'log', 'rawfile', 'file', 'changelog', 'shortlog', 'changeset', 'rev',
   'manifest', 'tags', 'branches', 'summary', 'filediff', 'diff', 'annotate',
   'filelog', 'archive', 'static', 'graph',
]

def log(web, req, tmpl):
    if 'file' in req.form and req.form['file'][0]:
        return filelog(web, req, tmpl)
    else:
        return changelog(web, req, tmpl)

def rawfile(web, req, tmpl):
    path = webutil.cleanpath(web.repo, req.form.get('file', [''])[0])
    if not path:
        content = manifest(web, req, tmpl)
        req.respond(HTTP_OK, web.ctype)
        return content

    try:
        fctx = webutil.filectx(web.repo, req)
    except error.LookupError, inst:
        try:
            content = manifest(web, req, tmpl)
            req.respond(HTTP_OK, web.ctype)
            return content
        except ErrorResponse:
            raise inst

    path = fctx.path()
    text = fctx.data()
    mt = mimetypes.guess_type(path)[0]
    if mt is None:
        mt = binary(text) and 'application/octet-stream' or 'text/plain'

    req.respond(HTTP_OK, mt, path, len(text))
    return [text]

def _filerevision(web, tmpl, fctx):
    f = fctx.path()
    text = fctx.data()
    parity = paritygen(web.stripecount)

    if binary(text):
        mt = mimetypes.guess_type(f)[0] or 'application/octet-stream'
        text = '(binary:%s)' % mt

    def lines():
        for lineno, t in enumerate(text.splitlines(True)):
            yield {"line": t,
                   "lineid": "l%d" % (lineno + 1),
                   "linenumber": "% 6d" % (lineno + 1),
                   "parity": parity.next()}

    return tmpl("filerevision",
                file=f,
                path=webutil.up(f),
                text=lines(),
                rev=fctx.rev(),
                node=hex(fctx.node()),
                author=fctx.user(),
                date=fctx.date(),
                desc=fctx.description(),
                branch=webutil.nodebranchnodefault(fctx),
                parent=webutil.parents(fctx),
                child=webutil.children(fctx),
                rename=webutil.renamelink(fctx),
                permissions=fctx.manifest().flags(f))

def file(web, req, tmpl):
    path = webutil.cleanpath(web.repo, req.form.get('file', [''])[0])
    if not path:
        return manifest(web, req, tmpl)
    try:
        return _filerevision(web, tmpl, webutil.filectx(web.repo, req))
    except error.LookupError, inst:
        try:
            return manifest(web, req, tmpl)
        except ErrorResponse:
            raise inst

def _search(web, req, tmpl):

    query = req.form['rev'][0]
    revcount = web.maxchanges
    if 'revcount' in req.form:
        revcount = int(req.form.get('revcount', [revcount])[0])
        tmpl.defaults['sessionvars']['revcount'] = revcount

    lessvars = copy.copy(tmpl.defaults['sessionvars'])
    lessvars['revcount'] = revcount / 2
    lessvars['rev'] = query
    morevars = copy.copy(tmpl.defaults['sessionvars'])
    morevars['revcount'] = revcount * 2
    morevars['rev'] = query

    def changelist(**map):
        cl = web.repo.changelog
        count = 0
        qw = query.lower().split()

        def revgen():
            for i in xrange(len(cl) - 1, 0, -100):
                l = []
                for j in xrange(max(0, i - 100), i + 1):
                    ctx = web.repo[j]
                    l.append(ctx)
                l.reverse()
                for e in l:
                    yield e

        for ctx in revgen():
            miss = 0
            for q in qw:
                if not (q in ctx.user().lower() or
                        q in ctx.description().lower() or
                        q in " ".join(ctx.files()).lower()):
                    miss = 1
                    break
            if miss:
                continue

            count += 1
            n = ctx.node()
            showtags = webutil.showtag(web.repo, tmpl, 'changelogtag', n)
            files = webutil.listfilediffs(tmpl, ctx.files(), n, web.maxfiles)

            yield tmpl('searchentry',
                       parity=parity.next(),
                       author=ctx.user(),
                       parent=webutil.parents(ctx),
                       child=webutil.children(ctx),
                       changelogtag=showtags,
                       desc=ctx.description(),
                       date=ctx.date(),
                       files=files,
                       rev=ctx.rev(),
                       node=hex(n),
                       tags=webutil.nodetagsdict(web.repo, n),
                       inbranch=webutil.nodeinbranch(web.repo, ctx),
                       branches=webutil.nodebranchdict(web.repo, ctx))

            if count >= revcount:
                break

    cl = web.repo.changelog
    parity = paritygen(web.stripecount)

    return tmpl('search', query=query, node=hex(cl.tip()),
                entries=changelist, archives=web.archivelist("tip"),
                morevars=morevars, lessvars=lessvars)

def changelog(web, req, tmpl, shortlog=False):

    if 'node' in req.form:
        ctx = webutil.changectx(web.repo, req)
    else:
        if 'rev' in req.form:
            hi = req.form['rev'][0]
        else:
            hi = len(web.repo) - 1
        try:
            ctx = web.repo[hi]
        except error.RepoError:
            return _search(web, req, tmpl) # XXX redirect to 404 page?

    def changelist(limit=0, **map):
        l = [] # build a list in forward order for efficiency
        for i in xrange(start, end):
            ctx = web.repo[i]
            n = ctx.node()
            showtags = webutil.showtag(web.repo, tmpl, 'changelogtag', n)
            files = webutil.listfilediffs(tmpl, ctx.files(), n, web.maxfiles)

            l.insert(0, {"parity": parity.next(),
                         "author": ctx.user(),
                         "parent": webutil.parents(ctx, i - 1),
                         "child": webutil.children(ctx, i + 1),
                         "changelogtag": showtags,
                         "desc": ctx.description(),
                         "date": ctx.date(),
                         "files": files,
                         "rev": i,
                         "node": hex(n),
                         "tags": webutil.nodetagsdict(web.repo, n),
                         "inbranch": webutil.nodeinbranch(web.repo, ctx),
                         "branches": webutil.nodebranchdict(web.repo, ctx)
                        })

        if limit > 0:
            l = l[:limit]

        for e in l:
            yield e

    revcount = shortlog and web.maxshortchanges or web.maxchanges
    if 'revcount' in req.form:
        revcount = int(req.form.get('revcount', [revcount])[0])
        tmpl.defaults['sessionvars']['revcount'] = revcount

    lessvars = copy.copy(tmpl.defaults['sessionvars'])
    lessvars['revcount'] = revcount / 2
    morevars = copy.copy(tmpl.defaults['sessionvars'])
    morevars['revcount'] = revcount * 2

    cl = web.repo.changelog
    count = len(cl)
    pos = ctx.rev()
    start = max(0, pos - revcount + 1)
    end = min(count, start + revcount)
    pos = end - 1
    parity = paritygen(web.stripecount, offset=start - end)

    changenav = webutil.revnavgen(pos, revcount, count, web.repo.changectx)

    return tmpl(shortlog and 'shortlog' or 'changelog', changenav=changenav,
                node=hex(ctx.node()), rev=pos, changesets=count,
                entries=lambda **x: changelist(limit=0,**x),
                latestentry=lambda **x: changelist(limit=1,**x),
                archives=web.archivelist("tip"), revcount=revcount,
                morevars=morevars, lessvars=lessvars)

def shortlog(web, req, tmpl):
    return changelog(web, req, tmpl, shortlog = True)

def changeset(web, req, tmpl):
    ctx = webutil.changectx(web.repo, req)
    showtags = webutil.showtag(web.repo, tmpl, 'changesettag', ctx.node())
    showbranch = webutil.nodebranchnodefault(ctx)

    files = []
    parity = paritygen(web.stripecount)
    for f in ctx.files():
        template = f in ctx and 'filenodelink' or 'filenolink'
        files.append(tmpl(template,
                          node=ctx.hex(), file=f,
                          parity=parity.next()))

    parity = paritygen(web.stripecount)
    style = web.config('web', 'style', 'paper')
    if 'style' in req.form:
        style = req.form['style'][0]

    diffs = webutil.diffs(web.repo, tmpl, ctx, None, parity, style)
    return tmpl('changeset',
                diff=diffs,
                rev=ctx.rev(),
                node=ctx.hex(),
                parent=webutil.parents(ctx),
                child=webutil.children(ctx),
                changesettag=showtags,
                changesetbranch=showbranch,
                author=ctx.user(),
                desc=ctx.description(),
                date=ctx.date(),
                files=files,
                archives=web.archivelist(ctx.hex()),
                tags=webutil.nodetagsdict(web.repo, ctx.node()),
                branch=webutil.nodebranchnodefault(ctx),
                inbranch=webutil.nodeinbranch(web.repo, ctx),
                branches=webutil.nodebranchdict(web.repo, ctx))

rev = changeset

def manifest(web, req, tmpl):
    ctx = webutil.changectx(web.repo, req)
    path = webutil.cleanpath(web.repo, req.form.get('file', [''])[0])
    mf = ctx.manifest()
    node = ctx.node()

    files = {}
    dirs = {}
    parity = paritygen(web.stripecount)

    if path and path[-1] != "/":
        path += "/"
    l = len(path)
    abspath = "/" + path

    for f, n in mf.iteritems():
        if f[:l] != path:
            continue
        remain = f[l:]
        elements = remain.split('/')
        if len(elements) == 1:
            files[remain] = f
        else:
            h = dirs # need to retain ref to dirs (root)
            for elem in elements[0:-1]:
                if elem not in h:
                    h[elem] = {}
                h = h[elem]
                if len(h) > 1:
                    break
            h[None] = None # denotes files present

    if mf and not files and not dirs:
        raise ErrorResponse(HTTP_NOT_FOUND, 'path not found: ' + path)

    def filelist(**map):
        for f in sorted(files):
            full = files[f]

            fctx = ctx.filectx(full)
            yield {"file": full,
                   "parity": parity.next(),
                   "basename": f,
                   "date": fctx.date(),
                   "size": fctx.size(),
                   "permissions": mf.flags(full)}

    def dirlist(**map):
        for d in sorted(dirs):

            emptydirs = []
            h = dirs[d]
            while isinstance(h, dict) and len(h) == 1:
                k, v = h.items()[0]
                if v:
                    emptydirs.append(k)
                h = v

            path = "%s%s" % (abspath, d)
            yield {"parity": parity.next(),
                   "path": path,
                   "emptydirs": "/".join(emptydirs),
                   "basename": d}

    return tmpl("manifest",
                rev=ctx.rev(),
                node=hex(node),
                path=abspath,
                up=webutil.up(abspath),
                upparity=parity.next(),
                fentries=filelist,
                dentries=dirlist,
                archives=web.archivelist(hex(node)),
                tags=webutil.nodetagsdict(web.repo, node),
                inbranch=webutil.nodeinbranch(web.repo, ctx),
                branches=webutil.nodebranchdict(web.repo, ctx))

def tags(web, req, tmpl):
    i = web.repo.tagslist()
    i.reverse()
    parity = paritygen(web.stripecount)

    def entries(notip=False, limit=0, **map):
        count = 0
        for k, n in i:
            if notip and k == "tip":
                continue
            if limit > 0 and count >= limit:
                continue
            count = count + 1
            yield {"parity": parity.next(),
                   "tag": k,
                   "date": web.repo[n].date(),
                   "node": hex(n)}

    return tmpl("tags",
                node=hex(web.repo.changelog.tip()),
                entries=lambda **x: entries(False, 0, **x),
                entriesnotip=lambda **x: entries(True, 0, **x),
                latestentry=lambda **x: entries(True, 1, **x))

def branches(web, req, tmpl):
    b = web.repo.branchtags()
    tips = (web.repo[n] for t, n in web.repo.branchtags().iteritems())
    heads = web.repo.heads()
    parity = paritygen(web.stripecount)
    sortkey = lambda ctx: ('close' not in ctx.extra(), ctx.rev())

    def entries(limit, **map):
        count = 0
        for ctx in sorted(tips, key=sortkey, reverse=True):
            if limit > 0 and count >= limit:
                return
            count += 1
            if ctx.node() not in heads:
                status = 'inactive'
            elif not web.repo.branchheads(ctx.branch()):
                status = 'closed'
            else:
                status = 'open'
            yield {'parity': parity.next(),
                   'branch': ctx.branch(),
                   'status': status,
                   'node': ctx.hex(),
                   'date': ctx.date()}

    return tmpl('branches', node=hex(web.repo.changelog.tip()),
                entries=lambda **x: entries(0, **x),
                latestentry=lambda **x: entries(1, **x))

def summary(web, req, tmpl):
    i = web.repo.tagslist()
    i.reverse()

    def tagentries(**map):
        parity = paritygen(web.stripecount)
        count = 0
        for k, n in i:
            if k == "tip": # skip tip
                continue

            count += 1
            if count > 10: # limit to 10 tags
                break

            yield tmpl("tagentry",
                       parity=parity.next(),
                       tag=k,
                       node=hex(n),
                       date=web.repo[n].date())

    def branches(**map):
        parity = paritygen(web.stripecount)

        b = web.repo.branchtags()
        l = [(-web.repo.changelog.rev(n), n, t) for t, n in b.iteritems()]
        for r, n, t in sorted(l):
            yield {'parity': parity.next(),
                   'branch': t,
                   'node': hex(n),
                   'date': web.repo[n].date()}

    def changelist(**map):
        parity = paritygen(web.stripecount, offset=start - end)
        l = [] # build a list in forward order for efficiency
        for i in xrange(start, end):
            ctx = web.repo[i]
            n = ctx.node()
            hn = hex(n)

            l.insert(0, tmpl(
               'shortlogentry',
                parity=parity.next(),
                author=ctx.user(),
                desc=ctx.description(),
                date=ctx.date(),
                rev=i,
                node=hn,
                tags=webutil.nodetagsdict(web.repo, n),
                inbranch=webutil.nodeinbranch(web.repo, ctx),
                branches=webutil.nodebranchdict(web.repo, ctx)))

        yield l

    cl = web.repo.changelog
    count = len(cl)
    start = max(0, count - web.maxchanges)
    end = min(count, start + web.maxchanges)

    return tmpl("summary",
                desc=web.config("web", "description", "unknown"),
                owner=get_contact(web.config) or "unknown",
                lastchange=cl.read(cl.tip())[2],
                tags=tagentries,
                branches=branches,
                shortlog=changelist,
                node=hex(cl.tip()),
                archives=web.archivelist("tip"))

def filediff(web, req, tmpl):
    fctx, ctx = None, None
    try:
        fctx = webutil.filectx(web.repo, req)
    except LookupError:
        ctx = webutil.changectx(web.repo, req)
        path = webutil.cleanpath(web.repo, req.form['file'][0])
        if path not in ctx.files():
            raise

    if fctx is not None:
        n = fctx.node()
        path = fctx.path()
    else:
        n = ctx.node()
        # path already defined in except clause

    parity = paritygen(web.stripecount)
    style = web.config('web', 'style', 'paper')
    if 'style' in req.form:
        style = req.form['style'][0]

    diffs = webutil.diffs(web.repo, tmpl, fctx or ctx, [path], parity, style)
    rename = fctx and webutil.renamelink(fctx) or []
    ctx = fctx and fctx or ctx
    return tmpl("filediff",
                file=path,
                node=hex(n),
                rev=ctx.rev(),
                date=ctx.date(),
                desc=ctx.description(),
                author=ctx.user(),
                rename=rename,
                branch=webutil.nodebranchnodefault(ctx),
                parent=webutil.parents(ctx),
                child=webutil.children(ctx),
                diff=diffs)

diff = filediff

def annotate(web, req, tmpl):
    fctx = webutil.filectx(web.repo, req)
    f = fctx.path()
    parity = paritygen(web.stripecount)

    def annotate(**map):
        last = None
        if binary(fctx.data()):
            mt = (mimetypes.guess_type(fctx.path())[0]
                  or 'application/octet-stream')
            lines = enumerate([((fctx.filectx(fctx.filerev()), 1),
                                '(binary:%s)' % mt)])
        else:
            lines = enumerate(fctx.annotate(follow=True, linenumber=True))
        for lineno, ((f, targetline), l) in lines:
            fnode = f.filenode()

            if last != fnode:
                last = fnode

            yield {"parity": parity.next(),
                   "node": hex(f.node()),
                   "rev": f.rev(),
                   "author": f.user(),
                   "desc": f.description(),
                   "file": f.path(),
                   "targetline": targetline,
                   "line": l,
                   "lineid": "l%d" % (lineno + 1),
                   "linenumber": "% 6d" % (lineno + 1)}

    return tmpl("fileannotate",
                file=f,
                annotate=annotate,
                path=webutil.up(f),
                rev=fctx.rev(),
                node=hex(fctx.node()),
                author=fctx.user(),
                date=fctx.date(),
                desc=fctx.description(),
                rename=webutil.renamelink(fctx),
                branch=webutil.nodebranchnodefault(fctx),
                parent=webutil.parents(fctx),
                child=webutil.children(fctx),
                permissions=fctx.manifest().flags(f))

def filelog(web, req, tmpl):

    try:
        fctx = webutil.filectx(web.repo, req)
        f = fctx.path()
        fl = fctx.filelog()
    except error.LookupError:
        f = webutil.cleanpath(web.repo, req.form['file'][0])
        fl = web.repo.file(f)
        numrevs = len(fl)
        if not numrevs: # file doesn't exist at all
            raise
        rev = webutil.changectx(web.repo, req).rev()
        first = fl.linkrev(0)
        if rev < first: # current rev is from before file existed
            raise
        frev = numrevs - 1
        while fl.linkrev(frev) > rev:
            frev -= 1
        fctx = web.repo.filectx(f, fl.linkrev(frev))

    revcount = web.maxshortchanges
    if 'revcount' in req.form:
        revcount = int(req.form.get('revcount', [revcount])[0])
        tmpl.defaults['sessionvars']['revcount'] = revcount

    lessvars = copy.copy(tmpl.defaults['sessionvars'])
    lessvars['revcount'] = revcount / 2
    morevars = copy.copy(tmpl.defaults['sessionvars'])
    morevars['revcount'] = revcount * 2

    count = fctx.filerev() + 1
    start = max(0, fctx.filerev() - revcount + 1) # first rev on this page
    end = min(count, start + revcount) # last rev on this page
    parity = paritygen(web.stripecount, offset=start - end)

    def entries(limit=0, **map):
        l = []

        repo = web.repo
        for i in xrange(start, end):
            iterfctx = fctx.filectx(i)

            l.insert(0, {"parity": parity.next(),
                         "filerev": i,
                         "file": f,
                         "node": hex(iterfctx.node()),
                         "author": iterfctx.user(),
                         "date": iterfctx.date(),
                         "rename": webutil.renamelink(iterfctx),
                         "parent": webutil.parents(iterfctx),
                         "child": webutil.children(iterfctx),
                         "desc": iterfctx.description(),
                         "tags": webutil.nodetagsdict(repo, iterfctx.node()),
                         "branch": webutil.nodebranchnodefault(iterfctx),
                         "inbranch": webutil.nodeinbranch(repo, iterfctx),
                         "branches": webutil.nodebranchdict(repo, iterfctx)})

        if limit > 0:
            l = l[:limit]

        for e in l:
            yield e

    nodefunc = lambda x: fctx.filectx(fileid=x)
    nav = webutil.revnavgen(end - 1, revcount, count, nodefunc)
    return tmpl("filelog", file=f, node=hex(fctx.node()), nav=nav,
                entries=lambda **x: entries(limit=0, **x),
                latestentry=lambda **x: entries(limit=1, **x),
                revcount=revcount, morevars=morevars, lessvars=lessvars)

def archive(web, req, tmpl):
    type_ = req.form.get('type', [None])[0]
    allowed = web.configlist("web", "allow_archive")
    key = req.form['node'][0]

    if type_ not in web.archives:
        msg = 'Unsupported archive type: %s' % type_
        raise ErrorResponse(HTTP_NOT_FOUND, msg)

    if not ((type_ in allowed or
        web.configbool("web", "allow" + type_, False))):
        msg = 'Archive type not allowed: %s' % type_
        raise ErrorResponse(HTTP_FORBIDDEN, msg)

    reponame = re.sub(r"\W+", "-", os.path.basename(web.reponame))
    cnode = web.repo.lookup(key)
    arch_version = key
    if cnode == key or key == 'tip':
        arch_version = short(cnode)
    name = "%s-%s" % (reponame, arch_version)
    mimetype, artype, extension, encoding = web.archive_specs[type_]
    headers = [
        ('Content-Type', mimetype),
        ('Content-Disposition', 'attachment; filename=%s%s' % (name, extension))
    ]
    if encoding:
        headers.append(('Content-Encoding', encoding))
    req.header(headers)
    req.respond(HTTP_OK)
    archival.archive(web.repo, req, cnode, artype, prefix=name)
    return []


def static(web, req, tmpl):
    fname = req.form['file'][0]
    # a repo owner may set web.static in .hg/hgrc to get any file
    # readable by the user running the CGI script
    static = web.config("web", "static", None, untrusted=False)
    if not static:
        tp = web.templatepath or templater.templatepath()
        if isinstance(tp, str):
            tp = [tp]
        static = [os.path.join(p, 'static') for p in tp]
    return [staticfile(static, fname, req)]

def graph(web, req, tmpl):

    rev = webutil.changectx(web.repo, req).rev()
    bg_height = 39
    revcount = web.maxshortchanges
    if 'revcount' in req.form:
        revcount = int(req.form.get('revcount', [revcount])[0])
        tmpl.defaults['sessionvars']['revcount'] = revcount

    lessvars = copy.copy(tmpl.defaults['sessionvars'])
    lessvars['revcount'] = revcount / 2
    morevars = copy.copy(tmpl.defaults['sessionvars'])
    morevars['revcount'] = revcount * 2

    max_rev = len(web.repo) - 1
    revcount = min(max_rev, revcount)
    revnode = web.repo.changelog.node(rev)
    revnode_hex = hex(revnode)
    uprev = min(max_rev, rev + revcount)
    downrev = max(0, rev - revcount)
    count = len(web.repo)
    changenav = webutil.revnavgen(rev, revcount, count, web.repo.changectx)

    dag = graphmod.revisions(web.repo, rev, downrev)
    tree = list(graphmod.colored(dag))
    canvasheight = (len(tree) + 1) * bg_height - 27
    data = []
    for (id, type, ctx, vtx, edges) in tree:
        if type != graphmod.CHANGESET:
            continue
        node = short(ctx.node())
        age = templatefilters.age(ctx.date())
        desc = templatefilters.firstline(ctx.description())
        desc = cgi.escape(templatefilters.nonempty(desc))
        user = cgi.escape(templatefilters.person(ctx.user()))
        branch = ctx.branch()
        branch = branch, web.repo.branchtags().get(branch) == ctx.node()
        data.append((node, vtx, edges, desc, user, age, branch, ctx.tags()))

    return tmpl('graph', rev=rev, revcount=revcount, uprev=uprev,
                lessvars=lessvars, morevars=morevars, downrev=downrev,
                canvasheight=canvasheight, jsdata=data, bg_height=bg_height,
                node=revnode_hex, changenav=changenav)
