# url.py - HTTP handling for mercurial
#
# Copyright 2005, 2006, 2007, 2008 Olivia Mackall <olivia@selenic.com>
# Copyright 2006, 2007 Alexis S. L. Carvalho <alexis@cecm.usp.br>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import base64
import socket
import sys

from .i18n import _
from .pycompat import getattr
from . import (
    encoding,
    error,
    httpconnection as httpconnectionmod,
    keepalive,
    pycompat,
    sslutil,
    urllibcompat,
    util,
)
from .utils import (
    stringutil,
    urlutil,
)

httplib = util.httplib
stringio = util.stringio
urlerr = util.urlerr
urlreq = util.urlreq


def escape(s, quote=None):
    """Replace special characters "&", "<" and ">" to HTML-safe sequences.
    If the optional flag quote is true, the quotation mark character (")
    is also translated.

    This is the same as cgi.escape in Python, but always operates on
    bytes, whereas cgi.escape in Python 3 only works on unicodes.
    """
    s = s.replace(b"&", b"&amp;")
    s = s.replace(b"<", b"&lt;")
    s = s.replace(b">", b"&gt;")
    if quote:
        s = s.replace(b'"', b"&quot;")
    return s


class passwordmgr(object):
    def __init__(self, ui, passwddb):
        self.ui = ui
        self.passwddb = passwddb

    def add_password(self, realm, uri, user, passwd):
        return self.passwddb.add_password(realm, uri, user, passwd)

    def find_user_password(self, realm, authuri):
        assert isinstance(realm, (type(None), str))
        assert isinstance(authuri, str)
        authinfo = self.passwddb.find_user_password(realm, authuri)
        user, passwd = authinfo
        user, passwd = pycompat.bytesurl(user), pycompat.bytesurl(passwd)
        if user and passwd:
            self._writedebug(user, passwd)
            return (pycompat.strurl(user), pycompat.strurl(passwd))

        if not user or not passwd:
            res = httpconnectionmod.readauthforuri(self.ui, authuri, user)
            if res:
                group, auth = res
                user, passwd = auth.get(b'username'), auth.get(b'password')
                self.ui.debug(b"using auth.%s.* for authentication\n" % group)
        if not user or not passwd:
            u = urlutil.url(pycompat.bytesurl(authuri))
            u.query = None
            if not self.ui.interactive():
                raise error.Abort(
                    _(b'http authorization required for %s')
                    % urlutil.hidepassword(bytes(u))
                )

            self.ui.write(
                _(b"http authorization required for %s\n")
                % urlutil.hidepassword(bytes(u))
            )
            self.ui.write(_(b"realm: %s\n") % pycompat.bytesurl(realm))
            if user:
                self.ui.write(_(b"user: %s\n") % user)
            else:
                user = self.ui.prompt(_(b"user:"), default=None)

            if not passwd:
                passwd = self.ui.getpass()

        # As of Python 3.8, the default implementation of
        # AbstractBasicAuthHandler.retry_http_basic_auth() assumes the user
        # is set if pw is not None. This means (None, str) is not a valid
        # return type of find_user_password().
        if user is None:
            return None, None

        self.passwddb.add_password(realm, authuri, user, passwd)
        self._writedebug(user, passwd)
        return (pycompat.strurl(user), pycompat.strurl(passwd))

    def _writedebug(self, user, passwd):
        msg = _(b'http auth: user %s, password %s\n')
        self.ui.debug(msg % (user, passwd and b'*' * len(passwd) or b'not set'))

    def find_stored_password(self, authuri):
        return self.passwddb.find_user_password(None, authuri)


class proxyhandler(urlreq.proxyhandler):
    def __init__(self, ui):
        proxyurl = ui.config(b"http_proxy", b"host") or encoding.environ.get(
            b'http_proxy'
        )
        # XXX proxyauthinfo = None

        if proxyurl:
            # proxy can be proper url or host[:port]
            if not (
                proxyurl.startswith(b'http:') or proxyurl.startswith(b'https:')
            ):
                proxyurl = b'http://' + proxyurl + b'/'
            proxy = urlutil.url(proxyurl)
            if not proxy.user:
                proxy.user = ui.config(b"http_proxy", b"user")
                proxy.passwd = ui.config(b"http_proxy", b"passwd")

            # see if we should use a proxy for this url
            no_list = [b"localhost", b"127.0.0.1"]
            no_list.extend(
                [p.lower() for p in ui.configlist(b"http_proxy", b"no")]
            )
            no_list.extend(
                [
                    p.strip().lower()
                    for p in encoding.environ.get(b"no_proxy", b'').split(b',')
                    if p.strip()
                ]
            )
            # "http_proxy.always" config is for running tests on localhost
            if ui.configbool(b"http_proxy", b"always"):
                self.no_list = []
            else:
                self.no_list = no_list

            # Keys and values need to be str because the standard library
            # expects them to be.
            proxyurl = str(proxy)
            proxies = {'http': proxyurl, 'https': proxyurl}
            ui.debug(
                b'proxying through %s\n' % urlutil.hidepassword(bytes(proxy))
            )
        else:
            proxies = {}

        urlreq.proxyhandler.__init__(self, proxies)
        self.ui = ui

    def proxy_open(self, req, proxy, type_):
        host = pycompat.bytesurl(urllibcompat.gethost(req)).split(b':')[0]
        for e in self.no_list:
            if host == e:
                return None
            if e.startswith(b'*.') and host.endswith(e[2:]):
                return None
            if e.startswith(b'.') and host.endswith(e[1:]):
                return None

        return urlreq.proxyhandler.proxy_open(self, req, proxy, type_)


def _gen_sendfile(orgsend):
    def _sendfile(self, data):
        # send a file
        if isinstance(data, httpconnectionmod.httpsendfile):
            # if auth required, some data sent twice, so rewind here
            data.seek(0)
            for chunk in util.filechunkiter(data):
                orgsend(self, chunk)
        else:
            orgsend(self, data)

    return _sendfile


has_https = util.safehasattr(urlreq, b'httpshandler')


class httpconnection(keepalive.HTTPConnection):
    # must be able to send big bundle as stream.
    send = _gen_sendfile(keepalive.HTTPConnection.send)

    def getresponse(self):
        proxyres = getattr(self, 'proxyres', None)
        if proxyres:
            if proxyres.will_close:
                self.close()
            self.proxyres = None
            return proxyres
        return keepalive.HTTPConnection.getresponse(self)


# Large parts of this function have their origin from before Python 2.6
# and could potentially be removed.
def _generic_start_transaction(handler, h, req):
    tunnel_host = req._tunnel_host
    if tunnel_host:
        if tunnel_host[:7] not in ['http://', 'https:/']:
            tunnel_host = 'https://' + tunnel_host
        new_tunnel = True
    else:
        tunnel_host = urllibcompat.getselector(req)
        new_tunnel = False

    if new_tunnel or tunnel_host == urllibcompat.getfullurl(req):  # has proxy
        u = urlutil.url(pycompat.bytesurl(tunnel_host))
        if new_tunnel or u.scheme == b'https':  # only use CONNECT for HTTPS
            h.realhostport = b':'.join([u.host, (u.port or b'443')])
            h.headers = req.headers.copy()
            h.headers.update(handler.parent.addheaders)
            return

    h.realhostport = None
    h.headers = None


def _generic_proxytunnel(self):
    proxyheaders = {
        pycompat.bytestr(x): pycompat.bytestr(self.headers[x])
        for x in self.headers
        if x.lower().startswith('proxy-')
    }
    self.send(b'CONNECT %s HTTP/1.0\r\n' % self.realhostport)
    for header in pycompat.iteritems(proxyheaders):
        self.send(b'%s: %s\r\n' % header)
    self.send(b'\r\n')

    # majority of the following code is duplicated from
    # httplib.HTTPConnection as there are no adequate places to
    # override functions to provide the needed functionality
    # strict was removed in Python 3.4.
    kwargs = {}
    if not pycompat.ispy3:
        kwargs[b'strict'] = self.strict

    res = self.response_class(self.sock, method=self._method, **kwargs)

    while True:
        version, status, reason = res._read_status()
        if status != httplib.CONTINUE:
            break
        # skip lines that are all whitespace
        list(iter(lambda: res.fp.readline().strip(), b''))
    res.status = status
    res.reason = reason.strip()

    if res.status == 200:
        # skip lines until we find a blank line
        list(iter(res.fp.readline, b'\r\n'))
        return True

    if version == b'HTTP/1.0':
        res.version = 10
    elif version.startswith(b'HTTP/1.'):
        res.version = 11
    elif version == b'HTTP/0.9':
        res.version = 9
    else:
        raise httplib.UnknownProtocol(version)

    if res.version == 9:
        res.length = None
        res.chunked = 0
        res.will_close = 1
        res.msg = httplib.HTTPMessage(stringio())
        return False

    res.msg = httplib.HTTPMessage(res.fp)
    res.msg.fp = None

    # are we using the chunked-style of transfer encoding?
    trenc = res.msg.getheader(b'transfer-encoding')
    if trenc and trenc.lower() == b"chunked":
        res.chunked = 1
        res.chunk_left = None
    else:
        res.chunked = 0

    # will the connection close at the end of the response?
    res.will_close = res._check_close()

    # do we have a Content-Length?
    # NOTE: RFC 2616, section 4.4, #3 says we ignore this if
    # transfer-encoding is "chunked"
    length = res.msg.getheader(b'content-length')
    if length and not res.chunked:
        try:
            res.length = int(length)
        except ValueError:
            res.length = None
        else:
            if res.length < 0:  # ignore nonsensical negative lengths
                res.length = None
    else:
        res.length = None

    # does the body have a fixed length? (of zero)
    if (
        status == httplib.NO_CONTENT
        or status == httplib.NOT_MODIFIED
        or 100 <= status < 200
        or res._method == b'HEAD'  # 1xx codes
    ):
        res.length = 0

    # if the connection remains open, and we aren't using chunked, and
    # a content-length was not provided, then assume that the connection
    # WILL close.
    if not res.will_close and not res.chunked and res.length is None:
        res.will_close = 1

    self.proxyres = res

    return False


class httphandler(keepalive.HTTPHandler):
    def http_open(self, req):
        return self.do_open(httpconnection, req)

    def _start_transaction(self, h, req):
        _generic_start_transaction(self, h, req)
        return keepalive.HTTPHandler._start_transaction(self, h, req)


class logginghttpconnection(keepalive.HTTPConnection):
    def __init__(self, createconn, *args, **kwargs):
        keepalive.HTTPConnection.__init__(self, *args, **kwargs)
        self._create_connection = createconn

    if sys.version_info < (2, 7, 7):
        # copied from 2.7.14, since old implementations directly call
        # socket.create_connection()
        def connect(self):
            self.sock = self._create_connection(
                (self.host, self.port), self.timeout, self.source_address
            )
            if self._tunnel_host:
                self._tunnel()


class logginghttphandler(httphandler):
    """HTTP handler that logs socket I/O."""

    def __init__(self, logfh, name, observeropts, timeout=None):
        super(logginghttphandler, self).__init__(timeout=timeout)

        self._logfh = logfh
        self._logname = name
        self._observeropts = observeropts

    # do_open() calls the passed class to instantiate an HTTPConnection. We
    # pass in a callable method that creates a custom HTTPConnection instance
    # whose callback to create the socket knows how to proxy the socket.
    def http_open(self, req):
        return self.do_open(self._makeconnection, req)

    def _makeconnection(self, *args, **kwargs):
        def createconnection(*args, **kwargs):
            sock = socket.create_connection(*args, **kwargs)
            return util.makeloggingsocket(
                self._logfh, sock, self._logname, **self._observeropts
            )

        return logginghttpconnection(createconnection, *args, **kwargs)


if has_https:

    class httpsconnection(keepalive.HTTPConnection):
        response_class = keepalive.HTTPResponse
        default_port = httplib.HTTPS_PORT
        # must be able to send big bundle as stream.
        send = _gen_sendfile(keepalive.safesend)
        getresponse = keepalive.wrapgetresponse(httplib.HTTPConnection)

        def __init__(
            self,
            host,
            port=None,
            key_file=None,
            cert_file=None,
            *args,
            **kwargs
        ):
            keepalive.HTTPConnection.__init__(self, host, port, *args, **kwargs)
            self.key_file = key_file
            self.cert_file = cert_file

        def connect(self):
            self.sock = socket.create_connection((self.host, self.port))

            host = self.host
            if self.realhostport:  # use CONNECT proxy
                _generic_proxytunnel(self)
                host = self.realhostport.rsplit(b':', 1)[0]
            self.sock = sslutil.wrapsocket(
                self.sock,
                self.key_file,
                self.cert_file,
                ui=self.ui,
                serverhostname=host,
            )
            sslutil.validatesocket(self.sock)

    class httpshandler(keepalive.KeepAliveHandler, urlreq.httpshandler):
        def __init__(self, ui, timeout=None):
            keepalive.KeepAliveHandler.__init__(self, timeout=timeout)
            urlreq.httpshandler.__init__(self)
            self.ui = ui
            self.pwmgr = passwordmgr(self.ui, self.ui.httppasswordmgrdb)

        def _start_transaction(self, h, req):
            _generic_start_transaction(self, h, req)
            return keepalive.KeepAliveHandler._start_transaction(self, h, req)

        def https_open(self, req):
            # urllibcompat.getfullurl() does not contain credentials
            # and we may need them to match the certificates.
            url = urllibcompat.getfullurl(req)
            user, password = self.pwmgr.find_stored_password(url)
            res = httpconnectionmod.readauthforuri(self.ui, url, user)
            if res:
                group, auth = res
                self.auth = auth
                self.ui.debug(b"using auth.%s.* for authentication\n" % group)
            else:
                self.auth = None
            return self.do_open(self._makeconnection, req)

        def _makeconnection(self, host, port=None, *args, **kwargs):
            keyfile = None
            certfile = None

            if len(args) >= 1:  # key_file
                keyfile = args[0]
            if len(args) >= 2:  # cert_file
                certfile = args[1]
            args = args[2:]

            # if the user has specified different key/cert files in
            # hgrc, we prefer these
            if self.auth and b'key' in self.auth and b'cert' in self.auth:
                keyfile = self.auth[b'key']
                certfile = self.auth[b'cert']

            conn = httpsconnection(
                host, port, keyfile, certfile, *args, **kwargs
            )
            conn.ui = self.ui
            return conn


class httpdigestauthhandler(urlreq.httpdigestauthhandler):
    def __init__(self, *args, **kwargs):
        urlreq.httpdigestauthhandler.__init__(self, *args, **kwargs)
        self.retried_req = None

    def reset_retry_count(self):
        # Python 2.6.5 will call this on 401 or 407 errors and thus loop
        # forever. We disable reset_retry_count completely and reset in
        # http_error_auth_reqed instead.
        pass

    def http_error_auth_reqed(self, auth_header, host, req, headers):
        # Reset the retry counter once for each request.
        if req is not self.retried_req:
            self.retried_req = req
            self.retried = 0
        return urlreq.httpdigestauthhandler.http_error_auth_reqed(
            self, auth_header, host, req, headers
        )


class httpbasicauthhandler(urlreq.httpbasicauthhandler):
    def __init__(self, *args, **kwargs):
        self.auth = None
        urlreq.httpbasicauthhandler.__init__(self, *args, **kwargs)
        self.retried_req = None

    def http_request(self, request):
        if self.auth:
            request.add_unredirected_header(self.auth_header, self.auth)

        return request

    def https_request(self, request):
        if self.auth:
            request.add_unredirected_header(self.auth_header, self.auth)

        return request

    def reset_retry_count(self):
        # Python 2.6.5 will call this on 401 or 407 errors and thus loop
        # forever. We disable reset_retry_count completely and reset in
        # http_error_auth_reqed instead.
        pass

    def http_error_auth_reqed(self, auth_header, host, req, headers):
        # Reset the retry counter once for each request.
        if req is not self.retried_req:
            self.retried_req = req
            self.retried = 0
        return urlreq.httpbasicauthhandler.http_error_auth_reqed(
            self, auth_header, host, req, headers
        )

    def retry_http_basic_auth(self, host, req, realm):
        user, pw = self.passwd.find_user_password(
            realm, urllibcompat.getfullurl(req)
        )
        if pw is not None:
            raw = b"%s:%s" % (pycompat.bytesurl(user), pycompat.bytesurl(pw))
            auth = 'Basic %s' % pycompat.strurl(base64.b64encode(raw).strip())
            if req.get_header(self.auth_header, None) == auth:
                return None
            self.auth = auth
            req.add_unredirected_header(self.auth_header, auth)
            return self.parent.open(req)
        else:
            return None


class cookiehandler(urlreq.basehandler):
    def __init__(self, ui):
        self.cookiejar = None

        cookiefile = ui.config(b'auth', b'cookiefile')
        if not cookiefile:
            return

        cookiefile = util.expandpath(cookiefile)
        try:
            cookiejar = util.cookielib.MozillaCookieJar(
                pycompat.fsdecode(cookiefile)
            )
            cookiejar.load()
            self.cookiejar = cookiejar
        except util.cookielib.LoadError as e:
            ui.warn(
                _(
                    b'(error loading cookie file %s: %s; continuing without '
                    b'cookies)\n'
                )
                % (cookiefile, stringutil.forcebytestr(e))
            )

    def http_request(self, request):
        if self.cookiejar:
            self.cookiejar.add_cookie_header(request)

        return request

    def https_request(self, request):
        if self.cookiejar:
            self.cookiejar.add_cookie_header(request)

        return request


handlerfuncs = []


def opener(
    ui,
    authinfo=None,
    useragent=None,
    loggingfh=None,
    loggingname=b's',
    loggingopts=None,
    sendaccept=True,
):
    """
    construct an opener suitable for urllib2
    authinfo will be added to the password manager

    The opener can be configured to log socket events if the various
    ``logging*`` arguments are specified.

    ``loggingfh`` denotes a file object to log events to.
    ``loggingname`` denotes the name of the to print when logging.
    ``loggingopts`` is a dict of keyword arguments to pass to the constructed
    ``util.socketobserver`` instance.

    ``sendaccept`` allows controlling whether the ``Accept`` request header
    is sent. The header is sent by default.
    """
    timeout = ui.configwith(float, b'http', b'timeout')
    handlers = []

    if loggingfh:
        handlers.append(
            logginghttphandler(
                loggingfh, loggingname, loggingopts or {}, timeout=timeout
            )
        )
        # We don't yet support HTTPS when logging I/O. If we attempt to open
        # an HTTPS URL, we'll likely fail due to unknown protocol.

    else:
        handlers.append(httphandler(timeout=timeout))
        if has_https:
            handlers.append(httpshandler(ui, timeout=timeout))

    handlers.append(proxyhandler(ui))

    passmgr = passwordmgr(ui, ui.httppasswordmgrdb)
    if authinfo is not None:
        realm, uris, user, passwd = authinfo
        saveduser, savedpass = passmgr.find_stored_password(uris[0])
        if user != saveduser or passwd:
            passmgr.add_password(realm, uris, user, passwd)
        ui.debug(
            b'http auth: user %s, password %s\n'
            % (user, passwd and b'*' * len(passwd) or b'not set')
        )

    handlers.extend(
        (httpbasicauthhandler(passmgr), httpdigestauthhandler(passmgr))
    )
    handlers.extend([h(ui, passmgr) for h in handlerfuncs])
    handlers.append(cookiehandler(ui))
    opener = urlreq.buildopener(*handlers)

    # keepalive.py's handlers will populate these attributes if they exist.
    opener.requestscount = 0
    opener.sentbytescount = 0
    opener.receivedbytescount = 0

    # The user agent should should *NOT* be used by servers for e.g.
    # protocol detection or feature negotiation: there are other
    # facilities for that.
    #
    # "mercurial/proto-1.0" was the original user agent string and
    # exists for backwards compatibility reasons.
    #
    # The "(Mercurial %s)" string contains the distribution
    # name and version. Other client implementations should choose their
    # own distribution name. Since servers should not be using the user
    # agent string for anything, clients should be able to define whatever
    # user agent they deem appropriate.
    #
    # The custom user agent is for lfs, because unfortunately some servers
    # do look at this value.
    if not useragent:
        agent = b'mercurial/proto-1.0 (Mercurial %s)' % util.version()
        opener.addheaders = [('User-agent', pycompat.sysstr(agent))]
    else:
        opener.addheaders = [('User-agent', pycompat.sysstr(useragent))]

    # This header should only be needed by wire protocol requests. But it has
    # been sent on all requests since forever. We keep sending it for backwards
    # compatibility reasons. Modern versions of the wire protocol use
    # X-HgProto-<N> for advertising client support.
    if sendaccept:
        opener.addheaders.append(('Accept', 'application/mercurial-0.1'))

    return opener


def open(ui, url_, data=None, sendaccept=True):
    u = urlutil.url(url_)
    if u.scheme:
        u.scheme = u.scheme.lower()
        url_, authinfo = u.authinfo()
    else:
        path = util.normpath(util.abspath(url_))
        url_ = b'file://' + pycompat.bytesurl(
            urlreq.pathname2url(pycompat.fsdecode(path))
        )
        authinfo = None
    return opener(ui, authinfo, sendaccept=sendaccept).open(
        pycompat.strurl(url_), data
    )


def wrapresponse(resp):
    """Wrap a response object with common error handlers.

    This ensures that any I/O from any consumer raises the appropriate
    error and messaging.
    """
    origread = resp.read

    class readerproxy(resp.__class__):
        def read(self, size=None):
            try:
                return origread(size)
            except httplib.IncompleteRead as e:
                # e.expected is an integer if length known or None otherwise.
                if e.expected:
                    got = len(e.partial)
                    total = e.expected + got
                    msg = _(
                        b'HTTP request error (incomplete response; '
                        b'expected %d bytes got %d)'
                    ) % (total, got)
                else:
                    msg = _(b'HTTP request error (incomplete response)')

                raise error.PeerTransportError(
                    msg,
                    hint=_(
                        b'this may be an intermittent network failure; '
                        b'if the error persists, consider contacting the '
                        b'network or server operator'
                    ),
                )
            except httplib.HTTPException as e:
                raise error.PeerTransportError(
                    _(b'HTTP request error (%s)') % e,
                    hint=_(
                        b'this may be an intermittent network failure; '
                        b'if the error persists, consider contacting the '
                        b'network or server operator'
                    ),
                )

    resp.__class__ = readerproxy
