# hgweb/hgweb_mod.py - Web interface for a repository.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import contextlib
import os

from .common import (
    ErrorResponse,
    HTTP_BAD_REQUEST,
    cspvalues,
    permhooks,
    statusmessage,
)

from .. import (
    encoding,
    error,
    extensions,
    formatter,
    hg,
    hook,
    profiling,
    pycompat,
    registrar,
    repoview,
    templatefilters,
    templater,
    templateutil,
    ui as uimod,
    wireprotoserver,
)

from . import (
    common,
    request as requestmod,
    webcommands,
    webutil,
    wsgicgi,
)


def getstyle(req, configfn, templatepath):
    styles = (
        req.qsparams.get(b'style', None),
        configfn(b'web', b'style'),
        b'paper',
    )
    return styles, _stylemap(styles, templatepath)


def _stylemap(styles, path=None):
    """Return path to mapfile for a given style.

    Searches mapfile in the following locations:
    1. templatepath/style/map
    2. templatepath/map-style
    3. templatepath/map
    """

    for style in styles:
        # only plain name is allowed to honor template paths
        if (
            not style
            or style in (pycompat.oscurdir, pycompat.ospardir)
            or pycompat.ossep in style
            or pycompat.osaltsep
            and pycompat.osaltsep in style
        ):
            continue
        locations = (os.path.join(style, b'map'), b'map-' + style, b'map')

        for location in locations:
            mapfile, fp = templater.try_open_template(location, path)
            if mapfile:
                return style, mapfile, fp

    raise RuntimeError(b"No hgweb templates found in %r" % path)


def makebreadcrumb(url, prefix=b''):
    """Return a 'URL breadcrumb' list

    A 'URL breadcrumb' is a list of URL-name pairs,
    corresponding to each of the path items on a URL.
    This can be used to create path navigation entries.
    """
    if url.endswith(b'/'):
        url = url[:-1]
    if prefix:
        url = b'/' + prefix + url
    relpath = url
    if relpath.startswith(b'/'):
        relpath = relpath[1:]

    breadcrumb = []
    urlel = url
    pathitems = [b''] + relpath.split(b'/')
    for pathel in reversed(pathitems):
        if not pathel or not urlel:
            break
        breadcrumb.append({b'url': urlel, b'name': pathel})
        urlel = os.path.dirname(urlel)
    return templateutil.mappinglist(reversed(breadcrumb))


class requestcontext:
    """Holds state/context for an individual request.

    Servers can be multi-threaded. Holding state on the WSGI application
    is prone to race conditions. Instances of this class exist to hold
    mutable and race-free state for requests.
    """

    def __init__(self, app, repo, req, res):
        self.repo = repo
        self.reponame = app.reponame
        self.req = req
        self.res = res

        # Only works if the filter actually support being upgraded to show
        # visible changesets
        current_filter = repo.filtername
        if (
            common.hashiddenaccess(repo, req)
            and current_filter is not None
            and current_filter + b'.hidden' in repoview.filtertable
        ):
            self.repo = self.repo.filtered(repo.filtername + b'.hidden')

        self.maxchanges = self.configint(b'web', b'maxchanges')
        self.stripecount = self.configint(b'web', b'stripes')
        self.maxshortchanges = self.configint(b'web', b'maxshortchanges')
        self.maxfiles = self.configint(b'web', b'maxfiles')
        self.allowpull = self.configbool(b'web', b'allow-pull')

        # we use untrusted=False to prevent a repo owner from using
        # web.templates in .hg/hgrc to get access to any file readable
        # by the user running the CGI script
        self.templatepath = self.config(b'web', b'templates', untrusted=False)

        # This object is more expensive to build than simple config values.
        # It is shared across requests. The app will replace the object
        # if it is updated. Since this is a reference and nothing should
        # modify the underlying object, it should be constant for the lifetime
        # of the request.
        self.websubtable = app.websubtable

        self.csp, self.nonce = cspvalues(self.repo.ui)

    # Trust the settings from the .hg/hgrc files by default.
    def config(self, *args, **kwargs):
        kwargs.setdefault('untrusted', True)
        return self.repo.ui.config(*args, **kwargs)

    def configbool(self, *args, **kwargs):
        kwargs.setdefault('untrusted', True)
        return self.repo.ui.configbool(*args, **kwargs)

    def configint(self, *args, **kwargs):
        kwargs.setdefault('untrusted', True)
        return self.repo.ui.configint(*args, **kwargs)

    def configlist(self, *args, **kwargs):
        kwargs.setdefault('untrusted', True)
        return self.repo.ui.configlist(*args, **kwargs)

    def archivelist(self, nodeid):
        return webutil.archivelist(self.repo.ui, nodeid)

    def templater(self, req):
        # determine scheme, port and server name
        # this is needed to create absolute urls
        logourl = self.config(b'web', b'logourl')
        logoimg = self.config(b'web', b'logoimg')
        staticurl = (
            self.config(b'web', b'staticurl')
            or req.apppath.rstrip(b'/') + b'/static/'
        )
        if not staticurl.endswith(b'/'):
            staticurl += b'/'

        # figure out which style to use

        vars = {}
        styles, (style, mapfile, fp) = getstyle(
            req, self.config, self.templatepath
        )
        if style == styles[0]:
            vars[b'style'] = style

        sessionvars = webutil.sessionvars(vars, b'?')

        if not self.reponame:
            self.reponame = (
                self.config(b'web', b'name', b'')
                or req.reponame
                or req.apppath
                or self.repo.root
            )

        filters = {}
        templatefilter = registrar.templatefilter(filters)

        @templatefilter(b'websub', intype=bytes)
        def websubfilter(text):
            return templatefilters.websub(text, self.websubtable)

        # create the templater
        # TODO: export all keywords: defaults = templatekw.keywords.copy()
        defaults = {
            b'url': req.apppath + b'/',
            b'logourl': logourl,
            b'logoimg': logoimg,
            b'staticurl': staticurl,
            b'urlbase': req.advertisedbaseurl,
            b'repo': self.reponame,
            b'encoding': encoding.encoding,
            b'sessionvars': sessionvars,
            b'pathdef': makebreadcrumb(req.apppath),
            b'style': style,
            b'nonce': self.nonce,
        }
        templatekeyword = registrar.templatekeyword(defaults)

        @templatekeyword(b'motd', requires=())
        def motd(context, mapping):
            yield self.config(b'web', b'motd')

        tres = formatter.templateresources(self.repo.ui, self.repo)
        return templater.templater.frommapfile(
            mapfile, fp=fp, filters=filters, defaults=defaults, resources=tres
        )

    def sendtemplate(self, name, **kwargs):
        """Helper function to send a response generated from a template."""
        if self.req.method != b'HEAD':
            kwargs = pycompat.byteskwargs(kwargs)
            self.res.setbodygen(self.tmpl.generate(name, kwargs))
        return self.res.sendresponse()


class hgweb:
    """HTTP server for individual repositories.

    Instances of this class serve HTTP responses for a particular
    repository.

    Instances are typically used as WSGI applications.

    Some servers are multi-threaded. On these servers, there may
    be multiple active threads inside __call__.
    """

    def __init__(self, repo, name=None, baseui=None):
        if isinstance(repo, bytes):
            if baseui:
                u = baseui.copy()
            else:
                u = uimod.ui.load()
                extensions.loadall(u)
                extensions.populateui(u)
            r = hg.repository(u, repo)
        else:
            # we trust caller to give us a private copy
            r = repo

        r.ui.setconfig(b'ui', b'report_untrusted', b'off', b'hgweb')
        r.baseui.setconfig(b'ui', b'report_untrusted', b'off', b'hgweb')
        r.ui.setconfig(b'ui', b'nontty', b'true', b'hgweb')
        r.baseui.setconfig(b'ui', b'nontty', b'true', b'hgweb')
        # resolve file patterns relative to repo root
        r.ui.setconfig(b'ui', b'forcecwd', r.root, b'hgweb')
        r.baseui.setconfig(b'ui', b'forcecwd', r.root, b'hgweb')
        # it's unlikely that we can replace signal handlers in WSGI server,
        # and mod_wsgi issues a big warning. a plain hgweb process (with no
        # threading) could replace signal handlers, but we don't bother
        # conditionally enabling it.
        r.ui.setconfig(b'ui', b'signal-safe-lock', b'false', b'hgweb')
        r.baseui.setconfig(b'ui', b'signal-safe-lock', b'false', b'hgweb')
        # displaying bundling progress bar while serving feel wrong and may
        # break some wsgi implementation.
        r.ui.setconfig(b'progress', b'disable', b'true', b'hgweb')
        r.baseui.setconfig(b'progress', b'disable', b'true', b'hgweb')
        self._repos = [hg.cachedlocalrepo(self._webifyrepo(r))]
        self._lastrepo = self._repos[0]
        hook.redirect(True)
        self.reponame = name

    def _webifyrepo(self, repo):
        repo = getwebview(repo)
        self.websubtable = webutil.getwebsubs(repo)
        return repo

    @contextlib.contextmanager
    def _obtainrepo(self):
        """Obtain a repo unique to the caller.

        Internally we maintain a stack of cachedlocalrepo instances
        to be handed out. If one is available, we pop it and return it,
        ensuring it is up to date in the process. If one is not available,
        we clone the most recently used repo instance and return it.

        It is currently possible for the stack to grow without bounds
        if the server allows infinite threads. However, servers should
        have a thread limit, thus establishing our limit.
        """
        if self._repos:
            cached = self._repos.pop()
            r, created = cached.fetch()
        else:
            cached = self._lastrepo.copy()
            r, created = cached.fetch()
        if created:
            r = self._webifyrepo(r)

        self._lastrepo = cached
        self.mtime = cached.mtime
        try:
            yield r
        finally:
            self._repos.append(cached)

    def run(self):
        """Start a server from CGI environment.

        Modern servers should be using WSGI and should avoid this
        method, if possible.
        """
        if not encoding.environ.get(b'GATEWAY_INTERFACE', b'').startswith(
            b"CGI/1."
        ):
            raise RuntimeError(
                b"This function is only intended to be "
                b"called while running as a CGI script."
            )
        wsgicgi.launch(self)

    def __call__(self, env, respond):
        """Run the WSGI application.

        This may be called by multiple threads.
        """
        req = requestmod.parserequestfromenv(env)
        res = requestmod.wsgiresponse(req, respond)

        return self.run_wsgi(req, res)

    def run_wsgi(self, req, res):
        """Internal method to run the WSGI application.

        This is typically only called by Mercurial. External consumers
        should be using instances of this class as the WSGI application.
        """
        with self._obtainrepo() as repo:
            profile = repo.ui.configbool(b'profiling', b'enabled')
            with profiling.profile(repo.ui, enabled=profile):
                for r in self._runwsgi(req, res, repo):
                    yield r

    def _runwsgi(self, req, res, repo):
        rctx = requestcontext(self, repo, req, res)

        # This state is global across all threads.
        encoding.encoding = rctx.config(b'web', b'encoding')
        rctx.repo.ui.environ = req.rawenv

        if rctx.csp:
            # hgwebdir may have added CSP header. Since we generate our own,
            # replace it.
            res.headers[b'Content-Security-Policy'] = rctx.csp

        handled = wireprotoserver.handlewsgirequest(
            rctx, req, res, self.check_perm
        )
        if handled:
            return res.sendresponse()

        # Old implementations of hgweb supported dispatching the request via
        # the initial query string parameter instead of using PATH_INFO.
        # If PATH_INFO is present (signaled by ``req.dispatchpath`` having
        # a value), we use it. Otherwise fall back to the query string.
        if req.dispatchpath is not None:
            query = req.dispatchpath
        else:
            query = req.querystring.partition(b'&')[0].partition(b';')[0]

        # translate user-visible url structure to internal structure

        args = query.split(b'/', 2)
        if b'cmd' not in req.qsparams and args and args[0]:
            cmd = args.pop(0)
            style = cmd.rfind(b'-')
            if style != -1:
                req.qsparams[b'style'] = cmd[:style]
                cmd = cmd[style + 1 :]

            # avoid accepting e.g. style parameter as command
            if hasattr(webcommands, pycompat.sysstr(cmd)):
                req.qsparams[b'cmd'] = cmd

            if cmd == b'static':
                req.qsparams[b'file'] = b'/'.join(args)
            else:
                if args and args[0]:
                    node = args.pop(0).replace(b'%2F', b'/')
                    req.qsparams[b'node'] = node
                if args:
                    if b'file' in req.qsparams:
                        del req.qsparams[b'file']
                    for a in args:
                        req.qsparams.add(b'file', a)

            ua = req.headers.get(b'User-Agent', b'')
            if cmd == b'rev' and b'mercurial' in ua:
                req.qsparams[b'style'] = b'raw'

            if cmd == b'archive':
                fn = req.qsparams[b'node']
                for type_, spec in webutil.archivespecs.items():
                    ext = spec[2]
                    if fn.endswith(ext):
                        req.qsparams[b'node'] = fn[: -len(ext)]
                        req.qsparams[b'type'] = type_
        else:
            cmd = req.qsparams.get(b'cmd', b'')

        # process the web interface request

        try:
            rctx.tmpl = rctx.templater(req)
            ctype = rctx.tmpl.render(
                b'mimetype', {b'encoding': encoding.encoding}
            )

            # check read permissions non-static content
            if cmd != b'static':
                self.check_perm(rctx, req, None)

            if cmd == b'':
                req.qsparams[b'cmd'] = rctx.tmpl.render(b'default', {})
                cmd = req.qsparams[b'cmd']

            # Don't enable caching if using a CSP nonce because then it wouldn't
            # be a nonce.
            if rctx.configbool(b'web', b'cache') and not rctx.nonce:
                tag = b'W/"%d"' % self.mtime
                if req.headers.get(b'If-None-Match') == tag:
                    res.status = b'304 Not Modified'
                    # Content-Type may be defined globally. It isn't valid on a
                    # 304, so discard it.
                    try:
                        del res.headers[b'Content-Type']
                    except KeyError:
                        pass
                    # Response body not allowed on 304.
                    res.setbodybytes(b'')
                    return res.sendresponse()

                res.headers[b'ETag'] = tag

            if cmd not in webcommands.__all__:
                msg = b'no such method: %s' % cmd
                raise ErrorResponse(HTTP_BAD_REQUEST, msg)
            else:
                # Set some globals appropriate for web handlers. Commands can
                # override easily enough.
                res.status = b'200 Script output follows'
                res.headers[b'Content-Type'] = ctype
                return getattr(webcommands, pycompat.sysstr(cmd))(rctx)

        except (error.LookupError, error.RepoLookupError) as err:
            msg = pycompat.bytestr(err)
            if hasattr(err, 'name') and not isinstance(
                err, error.ManifestLookupError
            ):
                msg = b'revision not found: %s' % err.name

            res.status = b'404 Not Found'
            res.headers[b'Content-Type'] = ctype
            return rctx.sendtemplate(b'error', error=msg)
        except (error.RepoError, error.StorageError) as e:
            res.status = b'500 Internal Server Error'
            res.headers[b'Content-Type'] = ctype
            return rctx.sendtemplate(b'error', error=pycompat.bytestr(e))
        except error.Abort as e:
            res.status = b'403 Forbidden'
            res.headers[b'Content-Type'] = ctype
            return rctx.sendtemplate(b'error', error=e.message)
        except ErrorResponse as e:
            for k, v in e.headers:
                res.headers[k] = v
            res.status = statusmessage(e.code, pycompat.bytestr(e))
            res.headers[b'Content-Type'] = ctype
            return rctx.sendtemplate(b'error', error=pycompat.bytestr(e))

    def check_perm(self, rctx, req, op):
        for permhook in permhooks:
            permhook(rctx, req, op)


def getwebview(repo):
    """The 'web.view' config controls changeset filter to hgweb. Possible
    values are ``served``, ``visible`` and ``all``. Default is ``served``.
    The ``served`` filter only shows changesets that can be pulled from the
    hgweb instance.  The``visible`` filter includes secret changesets but
    still excludes "hidden" one.

    See the repoview module for details.

    The option has been around undocumented since Mercurial 2.5, but no
    user ever asked about it. So we better keep it undocumented for now."""
    # experimental config: web.view
    viewconfig = repo.ui.config(b'web', b'view', untrusted=True)
    if viewconfig == b'all':
        return repo.unfiltered()
    elif viewconfig in repoview.filtertable:
        return repo.filtered(viewconfig)
    else:
        return repo.filtered(b'served')
