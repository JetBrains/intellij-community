# hgweb/hgwebdir_mod.py - Web interface for a directory of repositories.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os, re, time, urlparse
from mercurial.i18n import _
from mercurial import ui, hg, util, templater
from mercurial import error, encoding
from common import ErrorResponse, get_mtime, staticfile, paritygen, \
                   get_contact, HTTP_OK, HTTP_NOT_FOUND, HTTP_SERVER_ERROR
from hgweb_mod import hgweb
from request import wsgirequest
import webutil

def cleannames(items):
    return [(util.pconvert(name).strip('/'), path) for name, path in items]

def findrepos(paths):
    repos = []
    for prefix, root in cleannames(paths):
        roothead, roottail = os.path.split(root)
        # "foo = /bar/*" makes every subrepo of /bar/ to be
        # mounted as foo/subrepo
        # and "foo = /bar/**" also recurses into the subdirectories,
        # remember to use it without working dir.
        try:
            recurse = {'*': False, '**': True}[roottail]
        except KeyError:
            repos.append((prefix, root))
            continue
        roothead = os.path.normpath(roothead)
        for path in util.walkrepos(roothead, followsym=True, recurse=recurse):
            path = os.path.normpath(path)
            name = util.pconvert(path[len(roothead):]).strip('/')
            if prefix:
                name = prefix + '/' + name
            repos.append((name, path))
    return repos

class hgwebdir(object):
    refreshinterval = 20

    def __init__(self, conf, baseui=None):
        self.conf = conf
        self.baseui = baseui
        self.lastrefresh = 0
        self.motd = None
        self.refresh()

    def refresh(self):
        if self.lastrefresh + self.refreshinterval > time.time():
            return

        if self.baseui:
            self.ui = self.baseui.copy()
        else:
            self.ui = ui.ui()
            self.ui.setconfig('ui', 'report_untrusted', 'off')
            self.ui.setconfig('ui', 'interactive', 'off')

        if not isinstance(self.conf, (dict, list, tuple)):
            map = {'paths': 'hgweb-paths'}
            self.ui.readconfig(self.conf, remap=map, trust=True)
            paths = self.ui.configitems('hgweb-paths')
        elif isinstance(self.conf, (list, tuple)):
            paths = self.conf
        elif isinstance(self.conf, dict):
            paths = self.conf.items()

        encoding.encoding = self.ui.config('web', 'encoding',
                                           encoding.encoding)
        self.style = self.ui.config('web', 'style', 'paper')
        self.stripecount = self.ui.config('web', 'stripes', 1)
        if self.stripecount:
            self.stripecount = int(self.stripecount)
        self._baseurl = self.ui.config('web', 'baseurl')

        self.repos = findrepos(paths)
        for prefix, root in self.ui.configitems('collections'):
            prefix = util.pconvert(prefix)
            for path in util.walkrepos(root, followsym=True):
                repo = os.path.normpath(path)
                name = util.pconvert(repo)
                if name.startswith(prefix):
                    name = name[len(prefix):]
                self.repos.append((name.lstrip('/'), repo))

        self.lastrefresh = time.time()

    def run(self):
        if not os.environ.get('GATEWAY_INTERFACE', '').startswith("CGI/1."):
            raise RuntimeError("This function is only intended to be "
                               "called while running as a CGI script.")
        import mercurial.hgweb.wsgicgi as wsgicgi
        wsgicgi.launch(self)

    def __call__(self, env, respond):
        req = wsgirequest(env, respond)
        return self.run_wsgi(req)

    def read_allowed(self, ui, req):
        """Check allow_read and deny_read config options of a repo's ui object
        to determine user permissions.  By default, with neither option set (or
        both empty), allow all users to read the repo.  There are two ways a
        user can be denied read access:  (1) deny_read is not empty, and the
        user is unauthenticated or deny_read contains user (or *), and (2)
        allow_read is not empty and the user is not in allow_read.  Return True
        if user is allowed to read the repo, else return False."""

        user = req.env.get('REMOTE_USER')

        deny_read = ui.configlist('web', 'deny_read', untrusted=True)
        if deny_read and (not user or deny_read == ['*'] or user in deny_read):
            return False

        allow_read = ui.configlist('web', 'allow_read', untrusted=True)
        # by default, allow reading if no allow_read option has been set
        if (not allow_read) or (allow_read == ['*']) or (user in allow_read):
            return True

        return False

    def run_wsgi(self, req):
        try:
            try:
                self.refresh()

                virtual = req.env.get("PATH_INFO", "").strip('/')
                tmpl = self.templater(req)
                ctype = tmpl('mimetype', encoding=encoding.encoding)
                ctype = templater.stringify(ctype)

                # a static file
                if virtual.startswith('static/') or 'static' in req.form:
                    if virtual.startswith('static/'):
                        fname = virtual[7:]
                    else:
                        fname = req.form['static'][0]
                    static = templater.templatepath('static')
                    return (staticfile(static, fname, req),)

                # top-level index
                elif not virtual:
                    req.respond(HTTP_OK, ctype)
                    return self.makeindex(req, tmpl)

                # nested indexes and hgwebs

                repos = dict(self.repos)
                while virtual:
                    real = repos.get(virtual)
                    if real:
                        req.env['REPO_NAME'] = virtual
                        try:
                            repo = hg.repository(self.ui, real)
                            return hgweb(repo).run_wsgi(req)
                        except IOError, inst:
                            msg = inst.strerror
                            raise ErrorResponse(HTTP_SERVER_ERROR, msg)
                        except error.RepoError, inst:
                            raise ErrorResponse(HTTP_SERVER_ERROR, str(inst))

                    # browse subdirectories
                    subdir = virtual + '/'
                    if [r for r in repos if r.startswith(subdir)]:
                        req.respond(HTTP_OK, ctype)
                        return self.makeindex(req, tmpl, subdir)

                    up = virtual.rfind('/')
                    if up < 0:
                        break
                    virtual = virtual[:up]

                # prefixes not found
                req.respond(HTTP_NOT_FOUND, ctype)
                return tmpl("notfound", repo=virtual)

            except ErrorResponse, err:
                req.respond(err, ctype)
                return tmpl('error', error=err.message or '')
        finally:
            tmpl = None

    def makeindex(self, req, tmpl, subdir=""):

        def archivelist(ui, nodeid, url):
            allowed = ui.configlist("web", "allow_archive", untrusted=True)
            for i in [('zip', '.zip'), ('gz', '.tar.gz'), ('bz2', '.tar.bz2')]:
                if i[0] in allowed or ui.configbool("web", "allow" + i[0],
                                                    untrusted=True):
                    yield {"type" : i[0], "extension": i[1],
                           "node": nodeid, "url": url}

        sortdefault = None, False
        def entries(sortcolumn="", descending=False, subdir="", **map):

            rows = []
            parity = paritygen(self.stripecount)
            descend = self.ui.configbool('web', 'descend', True)
            for name, path in self.repos:

                if not name.startswith(subdir):
                    continue
                name = name[len(subdir):]
                if not descend and '/' in name:
                    continue

                u = self.ui.copy()
                try:
                    u.readconfig(os.path.join(path, '.hg', 'hgrc'))
                except Exception, e:
                    u.warn(_('error reading %s/.hg/hgrc: %s\n') % (path, e))
                    continue
                def get(section, name, default=None):
                    return u.config(section, name, default, untrusted=True)

                if u.configbool("web", "hidden", untrusted=True):
                    continue

                if not self.read_allowed(u, req):
                    continue

                parts = [name]
                if 'PATH_INFO' in req.env:
                    parts.insert(0, req.env['PATH_INFO'].rstrip('/'))
                if req.env['SCRIPT_NAME']:
                    parts.insert(0, req.env['SCRIPT_NAME'])
                url = re.sub(r'/+', '/', '/'.join(parts) + '/')

                # update time with local timezone
                try:
                    r = hg.repository(self.ui, path)
                    d = (get_mtime(r.spath), util.makedate()[1])
                except OSError:
                    continue

                contact = get_contact(get)
                description = get("web", "description", "")
                name = get("web", "name", name)
                row = dict(contact=contact or "unknown",
                           contact_sort=contact.upper() or "unknown",
                           name=name,
                           name_sort=name,
                           url=url,
                           description=description or "unknown",
                           description_sort=description.upper() or "unknown",
                           lastchange=d,
                           lastchange_sort=d[1]-d[0],
                           archives=archivelist(u, "tip", url))
                if (not sortcolumn or (sortcolumn, descending) == sortdefault):
                    # fast path for unsorted output
                    row['parity'] = parity.next()
                    yield row
                else:
                    rows.append((row["%s_sort" % sortcolumn], row))
            if rows:
                rows.sort()
                if descending:
                    rows.reverse()
                for key, row in rows:
                    row['parity'] = parity.next()
                    yield row

        self.refresh()
        sortable = ["name", "description", "contact", "lastchange"]
        sortcolumn, descending = sortdefault
        if 'sort' in req.form:
            sortcolumn = req.form['sort'][0]
            descending = sortcolumn.startswith('-')
            if descending:
                sortcolumn = sortcolumn[1:]
            if sortcolumn not in sortable:
                sortcolumn = ""

        sort = [("sort_%s" % column,
                 "%s%s" % ((not descending and column == sortcolumn)
                            and "-" or "", column))
                for column in sortable]

        self.refresh()
        self.updatereqenv(req.env)

        return tmpl("index", entries=entries, subdir=subdir,
                    sortcolumn=sortcolumn, descending=descending,
                    **dict(sort))

    def templater(self, req):

        def header(**map):
            yield tmpl('header', encoding=encoding.encoding, **map)

        def footer(**map):
            yield tmpl("footer", **map)

        def motd(**map):
            if self.motd is not None:
                yield self.motd
            else:
                yield config('web', 'motd', '')

        def config(section, name, default=None, untrusted=True):
            return self.ui.config(section, name, default, untrusted)

        self.updatereqenv(req.env)

        url = req.env.get('SCRIPT_NAME', '')
        if not url.endswith('/'):
            url += '/'

        vars = {}
        styles = (
            req.form.get('style', [None])[0],
            config('web', 'style'),
            'paper'
        )
        style, mapfile = templater.stylemap(styles)
        if style == styles[0]:
            vars['style'] = style

        start = url[-1] == '?' and '&' or '?'
        sessionvars = webutil.sessionvars(vars, start)
        staticurl = config('web', 'staticurl') or url + 'static/'
        if not staticurl.endswith('/'):
            staticurl += '/'

        tmpl = templater.templater(mapfile,
                                   defaults={"header": header,
                                             "footer": footer,
                                             "motd": motd,
                                             "url": url,
                                             "staticurl": staticurl,
                                             "sessionvars": sessionvars})
        return tmpl

    def updatereqenv(self, env):
        def splitnetloc(netloc):
            if ':' in netloc:
                return netloc.split(':', 1)
            else:
                return (netloc, None)

        if self._baseurl is not None:
            urlcomp = urlparse.urlparse(self._baseurl)
            host, port = splitnetloc(urlcomp[1])
            path = urlcomp[2]
            env['SERVER_NAME'] = host
            if port:
                env['SERVER_PORT'] = port
            env['SCRIPT_NAME'] = path
