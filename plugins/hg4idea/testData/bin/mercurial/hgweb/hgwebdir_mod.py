# hgweb/hgwebdir_mod.py - Web interface for a directory of repositories.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os, re, time
from mercurial.i18n import _
from mercurial import ui, hg, scmutil, util, templater
from mercurial import error, encoding
from common import ErrorResponse, get_mtime, staticfile, paritygen, ismember, \
                   get_contact, HTTP_OK, HTTP_NOT_FOUND, HTTP_SERVER_ERROR
from hgweb_mod import hgweb, makebreadcrumb
from request import wsgirequest
import webutil

def cleannames(items):
    return [(util.pconvert(name).strip('/'), path) for name, path in items]

def findrepos(paths):
    repos = []
    for prefix, root in cleannames(paths):
        roothead, roottail = os.path.split(root)
        # "foo = /bar/*" or "foo = /bar/**" lets every repo /bar/N in or below
        # /bar/ be served as as foo/N .
        # '*' will not search inside dirs with .hg (except .hg/patches),
        # '**' will search inside dirs with .hg (and thus also find subrepos).
        try:
            recurse = {'*': False, '**': True}[roottail]
        except KeyError:
            repos.append((prefix, root))
            continue
        roothead = os.path.normpath(os.path.abspath(roothead))
        paths = scmutil.walkrepos(roothead, followsym=True, recurse=recurse)
        repos.extend(urlrepos(prefix, roothead, paths))
    return repos

def urlrepos(prefix, roothead, paths):
    """yield url paths and filesystem paths from a list of repo paths

    >>> conv = lambda seq: [(v, util.pconvert(p)) for v,p in seq]
    >>> conv(urlrepos('hg', '/opt', ['/opt/r', '/opt/r/r', '/opt']))
    [('hg/r', '/opt/r'), ('hg/r/r', '/opt/r/r'), ('hg', '/opt')]
    >>> conv(urlrepos('', '/opt', ['/opt/r', '/opt/r/r', '/opt']))
    [('r', '/opt/r'), ('r/r', '/opt/r/r'), ('', '/opt')]
    """
    for path in paths:
        path = os.path.normpath(path)
        yield (prefix + '/' +
               util.pconvert(path[len(roothead):]).lstrip('/')).strip('/'), path

def geturlcgivars(baseurl, port):
    """
    Extract CGI variables from baseurl

    >>> geturlcgivars("http://host.org/base", "80")
    ('host.org', '80', '/base')
    >>> geturlcgivars("http://host.org:8000/base", "80")
    ('host.org', '8000', '/base')
    >>> geturlcgivars('/base', 8000)
    ('', '8000', '/base')
    >>> geturlcgivars("base", '8000')
    ('', '8000', '/base')
    >>> geturlcgivars("http://host", '8000')
    ('host', '8000', '/')
    >>> geturlcgivars("http://host/", '8000')
    ('host', '8000', '/')
    """
    u = util.url(baseurl)
    name = u.host or ''
    if u.port:
        port = u.port
    path = u.path or ""
    if not path.startswith('/'):
        path = '/' + path

    return name, str(port), path

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
            u = self.baseui.copy()
        else:
            u = ui.ui()
            u.setconfig('ui', 'report_untrusted', 'off')
            u.setconfig('ui', 'nontty', 'true')

        if not isinstance(self.conf, (dict, list, tuple)):
            map = {'paths': 'hgweb-paths'}
            if not os.path.exists(self.conf):
                raise util.Abort(_('config file %s not found!') % self.conf)
            u.readconfig(self.conf, remap=map, trust=True)
            paths = []
            for name, ignored in u.configitems('hgweb-paths'):
                for path in u.configlist('hgweb-paths', name):
                    paths.append((name, path))
        elif isinstance(self.conf, (list, tuple)):
            paths = self.conf
        elif isinstance(self.conf, dict):
            paths = self.conf.items()

        repos = findrepos(paths)
        for prefix, root in u.configitems('collections'):
            prefix = util.pconvert(prefix)
            for path in scmutil.walkrepos(root, followsym=True):
                repo = os.path.normpath(path)
                name = util.pconvert(repo)
                if name.startswith(prefix):
                    name = name[len(prefix):]
                repos.append((name.lstrip('/'), repo))

        self.repos = repos
        self.ui = u
        encoding.encoding = self.ui.config('web', 'encoding',
                                           encoding.encoding)
        self.style = self.ui.config('web', 'style', 'paper')
        self.templatepath = self.ui.config('web', 'templates', None)
        self.stripecount = self.ui.config('web', 'stripes', 1)
        if self.stripecount:
            self.stripecount = int(self.stripecount)
        self._baseurl = self.ui.config('web', 'baseurl')
        prefix = self.ui.config('web', 'prefix', '')
        if prefix.startswith('/'):
            prefix = prefix[1:]
        if prefix.endswith('/'):
            prefix = prefix[:-1]
        self.prefix = prefix
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
        if deny_read and (not user or ismember(ui, user, deny_read)):
            return False

        allow_read = ui.configlist('web', 'allow_read', untrusted=True)
        # by default, allow reading if no allow_read option has been set
        if (not allow_read) or ismember(ui, user, allow_read):
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
                    static = self.ui.config("web", "static", None,
                                            untrusted=False)
                    if not static:
                        tp = self.templatepath or templater.templatepath()
                        if isinstance(tp, str):
                            tp = [tp]
                        static = [os.path.join(p, 'static') for p in tp]
                    staticfile(static, fname, req)
                    return []

                # top-level index
                elif not virtual:
                    req.respond(HTTP_OK, ctype)
                    return self.makeindex(req, tmpl)

                # nested indexes and hgwebs

                repos = dict(self.repos)
                virtualrepo = virtual
                while virtualrepo:
                    real = repos.get(virtualrepo)
                    if real:
                        req.env['REPO_NAME'] = virtualrepo
                        try:
                            repo = hg.repository(self.ui, real)
                            return hgweb(repo).run_wsgi(req)
                        except IOError, inst:
                            msg = inst.strerror
                            raise ErrorResponse(HTTP_SERVER_ERROR, msg)
                        except error.RepoError, inst:
                            raise ErrorResponse(HTTP_SERVER_ERROR, str(inst))

                    up = virtualrepo.rfind('/')
                    if up < 0:
                        break
                    virtualrepo = virtualrepo[:up]

                # browse subdirectories
                subdir = virtual + '/'
                if [r for r in repos if r.startswith(subdir)]:
                    req.respond(HTTP_OK, ctype)
                    return self.makeindex(req, tmpl, subdir)

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
            archives = []
            for i in [('zip', '.zip'), ('gz', '.tar.gz'), ('bz2', '.tar.bz2')]:
                if i[0] in allowed or ui.configbool("web", "allow" + i[0],
                                                    untrusted=True):
                    archives.append({"type" : i[0], "extension": i[1],
                                     "node": nodeid, "url": url})
            return archives

        def rawentries(subdir="", **map):

            descend = self.ui.configbool('web', 'descend', True)
            collapse = self.ui.configbool('web', 'collapse', False)
            seenrepos = set()
            seendirs = set()
            for name, path in self.repos:

                if not name.startswith(subdir):
                    continue
                name = name[len(subdir):]
                directory = False

                if '/' in name:
                    if not descend:
                        continue

                    nameparts = name.split('/')
                    rootname = nameparts[0]

                    if not collapse:
                        pass
                    elif rootname in seendirs:
                        continue
                    elif rootname in seenrepos:
                        pass
                    else:
                        directory = True
                        name = rootname

                        # redefine the path to refer to the directory
                        discarded = '/'.join(nameparts[1:])

                        # remove name parts plus accompanying slash
                        path = path[:-len(discarded) - 1]

                parts = [name]
                if 'PATH_INFO' in req.env:
                    parts.insert(0, req.env['PATH_INFO'].rstrip('/'))
                if req.env['SCRIPT_NAME']:
                    parts.insert(0, req.env['SCRIPT_NAME'])
                url = re.sub(r'/+', '/', '/'.join(parts) + '/')

                # show either a directory entry or a repository
                if directory:
                    # get the directory's time information
                    try:
                        d = (get_mtime(path), util.makedate()[1])
                    except OSError:
                        continue

                    # add '/' to the name to make it obvious that
                    # the entry is a directory, not a regular repository
                    row = dict(contact="",
                               contact_sort="",
                               name=name + '/',
                               name_sort=name,
                               url=url,
                               description="",
                               description_sort="",
                               lastchange=d,
                               lastchange_sort=d[1]-d[0],
                               archives=[],
                               isdirectory=True)

                    seendirs.add(name)
                    yield row
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

                # update time with local timezone
                try:
                    r = hg.repository(self.ui, path)
                except IOError:
                    u.warn(_('error accessing repository at %s\n') % path)
                    continue
                except error.RepoError:
                    u.warn(_('error accessing repository at %s\n') % path)
                    continue
                try:
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

                seenrepos.add(name)
                yield row

        sortdefault = None, False
        def entries(sortcolumn="", descending=False, subdir="", **map):
            rows = rawentries(subdir=subdir, **map)

            if sortcolumn and sortdefault != (sortcolumn, descending):
                sortkey = '%s_sort' % sortcolumn
                rows = sorted(rows, key=lambda x: x[sortkey],
                              reverse=descending)
            for row, parity in zip(rows, paritygen(self.stripecount)):
                row['parity'] = parity
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
                    pathdef=makebreadcrumb('/' + subdir, self.prefix),
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
        style, mapfile = templater.stylemap(styles, self.templatepath)
        if style == styles[0]:
            vars['style'] = style

        start = url[-1] == '?' and '&' or '?'
        sessionvars = webutil.sessionvars(vars, start)
        logourl = config('web', 'logourl', 'http://mercurial.selenic.com/')
        logoimg = config('web', 'logoimg', 'hglogo.png')
        staticurl = config('web', 'staticurl') or url + 'static/'
        if not staticurl.endswith('/'):
            staticurl += '/'

        tmpl = templater.templater(mapfile,
                                   defaults={"header": header,
                                             "footer": footer,
                                             "motd": motd,
                                             "url": url,
                                             "logourl": logourl,
                                             "logoimg": logoimg,
                                             "staticurl": staticurl,
                                             "sessionvars": sessionvars})
        return tmpl

    def updatereqenv(self, env):
        if self._baseurl is not None:
            name, port, path = geturlcgivars(self._baseurl, env['SERVER_PORT'])
            env['SERVER_NAME'] = name
            env['SERVER_PORT'] = port
            env['SCRIPT_NAME'] = path
