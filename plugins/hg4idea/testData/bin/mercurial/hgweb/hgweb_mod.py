# hgweb/hgweb_mod.py - Web interface for a repository.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os
from mercurial import ui, hg, hook, error, encoding, templater, util, repoview
from mercurial.templatefilters import websub
from mercurial.i18n import _
from common import get_stat, ErrorResponse, permhooks, caching
from common import HTTP_OK, HTTP_NOT_MODIFIED, HTTP_BAD_REQUEST
from common import HTTP_NOT_FOUND, HTTP_SERVER_ERROR
from request import wsgirequest
import webcommands, protocol, webutil, re

perms = {
    'changegroup': 'pull',
    'changegroupsubset': 'pull',
    'getbundle': 'pull',
    'stream_out': 'pull',
    'listkeys': 'pull',
    'unbundle': 'push',
    'pushkey': 'push',
}

def makebreadcrumb(url, prefix=''):
    '''Return a 'URL breadcrumb' list

    A 'URL breadcrumb' is a list of URL-name pairs,
    corresponding to each of the path items on a URL.
    This can be used to create path navigation entries.
    '''
    if url.endswith('/'):
        url = url[:-1]
    if prefix:
        url = '/' + prefix + url
    relpath = url
    if relpath.startswith('/'):
        relpath = relpath[1:]

    breadcrumb = []
    urlel = url
    pathitems = [''] + relpath.split('/')
    for pathel in reversed(pathitems):
        if not pathel or not urlel:
            break
        breadcrumb.append({'url': urlel, 'name': pathel})
        urlel = os.path.dirname(urlel)
    return reversed(breadcrumb)


class hgweb(object):
    def __init__(self, repo, name=None, baseui=None):
        if isinstance(repo, str):
            if baseui:
                u = baseui.copy()
            else:
                u = ui.ui()
            self.repo = hg.repository(u, repo)
        else:
            self.repo = repo

        self.repo = self._getview(self.repo)
        self.repo.ui.setconfig('ui', 'report_untrusted', 'off')
        self.repo.baseui.setconfig('ui', 'report_untrusted', 'off')
        self.repo.ui.setconfig('ui', 'nontty', 'true')
        self.repo.baseui.setconfig('ui', 'nontty', 'true')
        hook.redirect(True)
        self.mtime = -1
        self.size = -1
        self.reponame = name
        self.archives = 'zip', 'gz', 'bz2'
        self.stripecount = 1
        # a repo owner may set web.templates in .hg/hgrc to get any file
        # readable by the user running the CGI script
        self.templatepath = self.config('web', 'templates')
        self.websubtable = self.loadwebsub()

    # The CGI scripts are often run by a user different from the repo owner.
    # Trust the settings from the .hg/hgrc files by default.
    def config(self, section, name, default=None, untrusted=True):
        return self.repo.ui.config(section, name, default,
                                   untrusted=untrusted)

    def configbool(self, section, name, default=False, untrusted=True):
        return self.repo.ui.configbool(section, name, default,
                                       untrusted=untrusted)

    def configlist(self, section, name, default=None, untrusted=True):
        return self.repo.ui.configlist(section, name, default,
                                       untrusted=untrusted)

    def _getview(self, repo):
        viewconfig = self.config('web', 'view', 'served')
        if viewconfig == 'all':
            return repo.unfiltered()
        elif viewconfig in repoview.filtertable:
            return repo.filtered(viewconfig)
        else:
            return repo.filtered('served')

    def refresh(self, request=None):
        st = get_stat(self.repo.spath)
        # compare changelog size in addition to mtime to catch
        # rollbacks made less than a second ago
        if st.st_mtime != self.mtime or st.st_size != self.size:
            self.mtime = st.st_mtime
            self.size = st.st_size
            r = hg.repository(self.repo.baseui, self.repo.root)
            self.repo = self._getview(r)
            self.maxchanges = int(self.config("web", "maxchanges", 10))
            self.stripecount = int(self.config("web", "stripes", 1))
            self.maxshortchanges = int(self.config("web", "maxshortchanges",
                                                   60))
            self.maxfiles = int(self.config("web", "maxfiles", 10))
            self.allowpull = self.configbool("web", "allowpull", True)
            encoding.encoding = self.config("web", "encoding",
                                            encoding.encoding)
        if request:
            self.repo.ui.environ = request.env

    def run(self):
        if not os.environ.get('GATEWAY_INTERFACE', '').startswith("CGI/1."):
            raise RuntimeError("This function is only intended to be "
                               "called while running as a CGI script.")
        import mercurial.hgweb.wsgicgi as wsgicgi
        wsgicgi.launch(self)

    def __call__(self, env, respond):
        req = wsgirequest(env, respond)
        return self.run_wsgi(req)

    def run_wsgi(self, req):

        self.refresh(req)

        # work with CGI variables to create coherent structure
        # use SCRIPT_NAME, PATH_INFO and QUERY_STRING as well as our REPO_NAME

        req.url = req.env['SCRIPT_NAME']
        if not req.url.endswith('/'):
            req.url += '/'
        if 'REPO_NAME' in req.env:
            req.url += req.env['REPO_NAME'] + '/'

        if 'PATH_INFO' in req.env:
            parts = req.env['PATH_INFO'].strip('/').split('/')
            repo_parts = req.env.get('REPO_NAME', '').split('/')
            if parts[:len(repo_parts)] == repo_parts:
                parts = parts[len(repo_parts):]
            query = '/'.join(parts)
        else:
            query = req.env['QUERY_STRING'].split('&', 1)[0]
            query = query.split(';', 1)[0]

        # process this if it's a protocol request
        # protocol bits don't need to create any URLs
        # and the clients always use the old URL structure

        cmd = req.form.get('cmd', [''])[0]
        if protocol.iscmd(cmd):
            try:
                if query:
                    raise ErrorResponse(HTTP_NOT_FOUND)
                if cmd in perms:
                    self.check_perm(req, perms[cmd])
                return protocol.call(self.repo, req, cmd)
            except ErrorResponse, inst:
                # A client that sends unbundle without 100-continue will
                # break if we respond early.
                if (cmd == 'unbundle' and
                    (req.env.get('HTTP_EXPECT',
                                 '').lower() != '100-continue') or
                    req.env.get('X-HgHttp2', '')):
                    req.drain()
                req.respond(inst, protocol.HGTYPE,
                            body='0\n%s\n' % inst.message)
                return ''

        # translate user-visible url structure to internal structure

        args = query.split('/', 2)
        if 'cmd' not in req.form and args and args[0]:

            cmd = args.pop(0)
            style = cmd.rfind('-')
            if style != -1:
                req.form['style'] = [cmd[:style]]
                cmd = cmd[style + 1:]

            # avoid accepting e.g. style parameter as command
            if util.safehasattr(webcommands, cmd):
                req.form['cmd'] = [cmd]
            else:
                cmd = ''

            if cmd == 'static':
                req.form['file'] = ['/'.join(args)]
            else:
                if args and args[0]:
                    node = args.pop(0)
                    req.form['node'] = [node]
                if args:
                    req.form['file'] = args

            ua = req.env.get('HTTP_USER_AGENT', '')
            if cmd == 'rev' and 'mercurial' in ua:
                req.form['style'] = ['raw']

            if cmd == 'archive':
                fn = req.form['node'][0]
                for type_, spec in self.archive_specs.iteritems():
                    ext = spec[2]
                    if fn.endswith(ext):
                        req.form['node'] = [fn[:-len(ext)]]
                        req.form['type'] = [type_]

        # process the web interface request

        try:
            tmpl = self.templater(req)
            ctype = tmpl('mimetype', encoding=encoding.encoding)
            ctype = templater.stringify(ctype)

            # check read permissions non-static content
            if cmd != 'static':
                self.check_perm(req, None)

            if cmd == '':
                req.form['cmd'] = [tmpl.cache['default']]
                cmd = req.form['cmd'][0]

            if self.configbool('web', 'cache', True):
                caching(self, req) # sets ETag header or raises NOT_MODIFIED
            if cmd not in webcommands.__all__:
                msg = 'no such method: %s' % cmd
                raise ErrorResponse(HTTP_BAD_REQUEST, msg)
            elif cmd == 'file' and 'raw' in req.form.get('style', []):
                self.ctype = ctype
                content = webcommands.rawfile(self, req, tmpl)
            else:
                content = getattr(webcommands, cmd)(self, req, tmpl)
                req.respond(HTTP_OK, ctype)

            return content

        except (error.LookupError, error.RepoLookupError), err:
            req.respond(HTTP_NOT_FOUND, ctype)
            msg = str(err)
            if (util.safehasattr(err, 'name') and
                not isinstance(err,  error.ManifestLookupError)):
                msg = 'revision not found: %s' % err.name
            return tmpl('error', error=msg)
        except (error.RepoError, error.RevlogError), inst:
            req.respond(HTTP_SERVER_ERROR, ctype)
            return tmpl('error', error=str(inst))
        except ErrorResponse, inst:
            req.respond(inst, ctype)
            if inst.code == HTTP_NOT_MODIFIED:
                # Not allowed to return a body on a 304
                return ['']
            return tmpl('error', error=inst.message)

    def loadwebsub(self):
        websubtable = []
        websubdefs = self.repo.ui.configitems('websub')
        # we must maintain interhg backwards compatibility
        websubdefs += self.repo.ui.configitems('interhg')
        for key, pattern in websubdefs:
            # grab the delimiter from the character after the "s"
            unesc = pattern[1]
            delim = re.escape(unesc)

            # identify portions of the pattern, taking care to avoid escaped
            # delimiters. the replace format and flags are optional, but
            # delimiters are required.
            match = re.match(
                r'^s%s(.+)(?:(?<=\\\\)|(?<!\\))%s(.*)%s([ilmsux])*$'
                % (delim, delim, delim), pattern)
            if not match:
                self.repo.ui.warn(_("websub: invalid pattern for %s: %s\n")
                                  % (key, pattern))
                continue

            # we need to unescape the delimiter for regexp and format
            delim_re = re.compile(r'(?<!\\)\\%s' % delim)
            regexp = delim_re.sub(unesc, match.group(1))
            format = delim_re.sub(unesc, match.group(2))

            # the pattern allows for 6 regexp flags, so set them if necessary
            flagin = match.group(3)
            flags = 0
            if flagin:
                for flag in flagin.upper():
                    flags |= re.__dict__[flag]

            try:
                regexp = re.compile(regexp, flags)
                websubtable.append((regexp, format))
            except re.error:
                self.repo.ui.warn(_("websub: invalid regexp for %s: %s\n")
                                  % (key, regexp))
        return websubtable

    def templater(self, req):

        # determine scheme, port and server name
        # this is needed to create absolute urls

        proto = req.env.get('wsgi.url_scheme')
        if proto == 'https':
            proto = 'https'
            default_port = "443"
        else:
            proto = 'http'
            default_port = "80"

        port = req.env["SERVER_PORT"]
        port = port != default_port and (":" + port) or ""
        urlbase = '%s://%s%s' % (proto, req.env['SERVER_NAME'], port)
        logourl = self.config("web", "logourl", "http://mercurial.selenic.com/")
        logoimg = self.config("web", "logoimg", "hglogo.png")
        staticurl = self.config("web", "staticurl") or req.url + 'static/'
        if not staticurl.endswith('/'):
            staticurl += '/'

        # some functions for the templater

        def header(**map):
            yield tmpl('header', encoding=encoding.encoding, **map)

        def footer(**map):
            yield tmpl("footer", **map)

        def motd(**map):
            yield self.config("web", "motd", "")

        # figure out which style to use

        vars = {}
        styles = (
            req.form.get('style', [None])[0],
            self.config('web', 'style'),
            'paper',
        )
        style, mapfile = templater.stylemap(styles, self.templatepath)
        if style == styles[0]:
            vars['style'] = style

        start = req.url[-1] == '?' and '&' or '?'
        sessionvars = webutil.sessionvars(vars, start)

        if not self.reponame:
            self.reponame = (self.config("web", "name")
                             or req.env.get('REPO_NAME')
                             or req.url.strip('/') or self.repo.root)

        def websubfilter(text):
            return websub(text, self.websubtable)

        # create the templater

        tmpl = templater.templater(mapfile,
                                   filters={"websub": websubfilter},
                                   defaults={"url": req.url,
                                             "logourl": logourl,
                                             "logoimg": logoimg,
                                             "staticurl": staticurl,
                                             "urlbase": urlbase,
                                             "repo": self.reponame,
                                             "header": header,
                                             "footer": footer,
                                             "motd": motd,
                                             "sessionvars": sessionvars,
                                             "pathdef": makebreadcrumb(req.url),
                                            })
        return tmpl

    def archivelist(self, nodeid):
        allowed = self.configlist("web", "allow_archive")
        for i, spec in self.archive_specs.iteritems():
            if i in allowed or self.configbool("web", "allow" + i):
                yield {"type" : i, "extension" : spec[2], "node" : nodeid}

    archive_specs = {
        'bz2': ('application/x-bzip2', 'tbz2', '.tar.bz2', None),
        'gz': ('application/x-gzip', 'tgz', '.tar.gz', None),
        'zip': ('application/zip', 'zip', '.zip', None),
        }

    def check_perm(self, req, op):
        for hook in permhooks:
            hook(self, req, op)
