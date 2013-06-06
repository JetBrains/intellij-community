# url.py - HTTP handling for mercurial
#
# Copyright 2005, 2006, 2007, 2008 Matt Mackall <mpm@selenic.com>
# Copyright 2006, 2007 Alexis S. L. Carvalho <alexis@cecm.usp.br>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import urllib, urllib2, httplib, os, socket, cStringIO
from i18n import _
import keepalive, util, sslutil
import httpconnection as httpconnectionmod

class passwordmgr(urllib2.HTTPPasswordMgrWithDefaultRealm):
    def __init__(self, ui):
        urllib2.HTTPPasswordMgrWithDefaultRealm.__init__(self)
        self.ui = ui

    def find_user_password(self, realm, authuri):
        authinfo = urllib2.HTTPPasswordMgrWithDefaultRealm.find_user_password(
            self, realm, authuri)
        user, passwd = authinfo
        if user and passwd:
            self._writedebug(user, passwd)
            return (user, passwd)

        if not user or not passwd:
            res = httpconnectionmod.readauthforuri(self.ui, authuri, user)
            if res:
                group, auth = res
                user, passwd = auth.get('username'), auth.get('password')
                self.ui.debug("using auth.%s.* for authentication\n" % group)
        if not user or not passwd:
            if not self.ui.interactive():
                raise util.Abort(_('http authorization required'))

            self.ui.write(_("http authorization required\n"))
            self.ui.write(_("realm: %s\n") % realm)
            if user:
                self.ui.write(_("user: %s\n") % user)
            else:
                user = self.ui.prompt(_("user:"), default=None)

            if not passwd:
                passwd = self.ui.getpass()

        self.add_password(realm, authuri, user, passwd)
        self._writedebug(user, passwd)
        return (user, passwd)

    def _writedebug(self, user, passwd):
        msg = _('http auth: user %s, password %s\n')
        self.ui.debug(msg % (user, passwd and '*' * len(passwd) or 'not set'))

    def find_stored_password(self, authuri):
        return urllib2.HTTPPasswordMgrWithDefaultRealm.find_user_password(
            self, None, authuri)

class proxyhandler(urllib2.ProxyHandler):
    def __init__(self, ui):
        proxyurl = ui.config("http_proxy", "host") or os.getenv('http_proxy')
        # XXX proxyauthinfo = None

        if proxyurl:
            # proxy can be proper url or host[:port]
            if not (proxyurl.startswith('http:') or
                    proxyurl.startswith('https:')):
                proxyurl = 'http://' + proxyurl + '/'
            proxy = util.url(proxyurl)
            if not proxy.user:
                proxy.user = ui.config("http_proxy", "user")
                proxy.passwd = ui.config("http_proxy", "passwd")

            # see if we should use a proxy for this url
            no_list = ["localhost", "127.0.0.1"]
            no_list.extend([p.lower() for
                            p in ui.configlist("http_proxy", "no")])
            no_list.extend([p.strip().lower() for
                            p in os.getenv("no_proxy", '').split(',')
                            if p.strip()])
            # "http_proxy.always" config is for running tests on localhost
            if ui.configbool("http_proxy", "always"):
                self.no_list = []
            else:
                self.no_list = no_list

            proxyurl = str(proxy)
            proxies = {'http': proxyurl, 'https': proxyurl}
            ui.debug('proxying through http://%s:%s\n' %
                      (proxy.host, proxy.port))
        else:
            proxies = {}

        # urllib2 takes proxy values from the environment and those
        # will take precedence if found. So, if there's a config entry
        # defining a proxy, drop the environment ones
        if ui.config("http_proxy", "host"):
            for env in ["HTTP_PROXY", "http_proxy", "no_proxy"]:
                try:
                    if env in os.environ:
                        del os.environ[env]
                except OSError:
                    pass

        urllib2.ProxyHandler.__init__(self, proxies)
        self.ui = ui

    def proxy_open(self, req, proxy, type_):
        host = req.get_host().split(':')[0]
        if host in self.no_list:
            return None

        # work around a bug in Python < 2.4.2
        # (it leaves a "\n" at the end of Proxy-authorization headers)
        baseclass = req.__class__
        class _request(baseclass):
            def add_header(self, key, val):
                if key.lower() == 'proxy-authorization':
                    val = val.strip()
                return baseclass.add_header(self, key, val)
        req.__class__ = _request

        return urllib2.ProxyHandler.proxy_open(self, req, proxy, type_)

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

has_https = util.safehasattr(urllib2, 'HTTPSHandler')
if has_https:
    try:
        _create_connection = socket.create_connection
    except AttributeError:
        _GLOBAL_DEFAULT_TIMEOUT = object()

        def _create_connection(address, timeout=_GLOBAL_DEFAULT_TIMEOUT,
                               source_address=None):
            # lifted from Python 2.6

            msg = "getaddrinfo returns an empty list"
            host, port = address
            for res in socket.getaddrinfo(host, port, 0, socket.SOCK_STREAM):
                af, socktype, proto, canonname, sa = res
                sock = None
                try:
                    sock = socket.socket(af, socktype, proto)
                    if timeout is not _GLOBAL_DEFAULT_TIMEOUT:
                        sock.settimeout(timeout)
                    if source_address:
                        sock.bind(source_address)
                    sock.connect(sa)
                    return sock

                except socket.error, msg:
                    if sock is not None:
                        sock.close()

            raise socket.error(msg)

class httpconnection(keepalive.HTTPConnection):
    # must be able to send big bundle as stream.
    send = _gen_sendfile(keepalive.HTTPConnection.send)

    def connect(self):
        if has_https and self.realhostport: # use CONNECT proxy
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.connect((self.host, self.port))
            if _generic_proxytunnel(self):
                # we do not support client X.509 certificates
                self.sock = sslutil.ssl_wrap_socket(self.sock, None, None)
        else:
            keepalive.HTTPConnection.connect(self)

    def getresponse(self):
        proxyres = getattr(self, 'proxyres', None)
        if proxyres:
            if proxyres.will_close:
                self.close()
            self.proxyres = None
            return proxyres
        return keepalive.HTTPConnection.getresponse(self)

# general transaction handler to support different ways to handle
# HTTPS proxying before and after Python 2.6.3.
def _generic_start_transaction(handler, h, req):
    tunnel_host = getattr(req, '_tunnel_host', None)
    if tunnel_host:
        if tunnel_host[:7] not in ['http://', 'https:/']:
            tunnel_host = 'https://' + tunnel_host
        new_tunnel = True
    else:
        tunnel_host = req.get_selector()
        new_tunnel = False

    if new_tunnel or tunnel_host == req.get_full_url(): # has proxy
        u = util.url(tunnel_host)
        if new_tunnel or u.scheme == 'https': # only use CONNECT for HTTPS
            h.realhostport = ':'.join([u.host, (u.port or '443')])
            h.headers = req.headers.copy()
            h.headers.update(handler.parent.addheaders)
            return

    h.realhostport = None
    h.headers = None

def _generic_proxytunnel(self):
    proxyheaders = dict(
            [(x, self.headers[x]) for x in self.headers
             if x.lower().startswith('proxy-')])
    self._set_hostport(self.host, self.port)
    self.send('CONNECT %s HTTP/1.0\r\n' % self.realhostport)
    for header in proxyheaders.iteritems():
        self.send('%s: %s\r\n' % header)
    self.send('\r\n')

    # majority of the following code is duplicated from
    # httplib.HTTPConnection as there are no adequate places to
    # override functions to provide the needed functionality
    res = self.response_class(self.sock,
                              strict=self.strict,
                              method=self._method)

    while True:
        version, status, reason = res._read_status()
        if status != httplib.CONTINUE:
            break
        while True:
            skip = res.fp.readline().strip()
            if not skip:
                break
    res.status = status
    res.reason = reason.strip()

    if res.status == 200:
        while True:
            line = res.fp.readline()
            if line == '\r\n':
                break
        return True

    if version == 'HTTP/1.0':
        res.version = 10
    elif version.startswith('HTTP/1.'):
        res.version = 11
    elif version == 'HTTP/0.9':
        res.version = 9
    else:
        raise httplib.UnknownProtocol(version)

    if res.version == 9:
        res.length = None
        res.chunked = 0
        res.will_close = 1
        res.msg = httplib.HTTPMessage(cStringIO.StringIO())
        return False

    res.msg = httplib.HTTPMessage(res.fp)
    res.msg.fp = None

    # are we using the chunked-style of transfer encoding?
    trenc = res.msg.getheader('transfer-encoding')
    if trenc and trenc.lower() == "chunked":
        res.chunked = 1
        res.chunk_left = None
    else:
        res.chunked = 0

    # will the connection close at the end of the response?
    res.will_close = res._check_close()

    # do we have a Content-Length?
    # NOTE: RFC 2616, section 4.4, #3 says we ignore this if
    # transfer-encoding is "chunked"
    length = res.msg.getheader('content-length')
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
    if (status == httplib.NO_CONTENT or status == httplib.NOT_MODIFIED or
        100 <= status < 200 or # 1xx codes
        res._method == 'HEAD'):
        res.length = 0

    # if the connection remains open, and we aren't using chunked, and
    # a content-length was not provided, then assume that the connection
    # WILL close.
    if (not res.will_close and
       not res.chunked and
       res.length is None):
        res.will_close = 1

    self.proxyres = res

    return False

class httphandler(keepalive.HTTPHandler):
    def http_open(self, req):
        return self.do_open(httpconnection, req)

    def _start_transaction(self, h, req):
        _generic_start_transaction(self, h, req)
        return keepalive.HTTPHandler._start_transaction(self, h, req)

if has_https:
    class httpsconnection(httplib.HTTPSConnection):
        response_class = keepalive.HTTPResponse
        # must be able to send big bundle as stream.
        send = _gen_sendfile(keepalive.safesend)
        getresponse = keepalive.wrapgetresponse(httplib.HTTPSConnection)

        def connect(self):
            self.sock = _create_connection((self.host, self.port))

            host = self.host
            if self.realhostport: # use CONNECT proxy
                _generic_proxytunnel(self)
                host = self.realhostport.rsplit(':', 1)[0]
            self.sock = sslutil.ssl_wrap_socket(
                self.sock, self.key_file, self.cert_file,
                **sslutil.sslkwargs(self.ui, host))
            sslutil.validator(self.ui, host)(self.sock)

    class httpshandler(keepalive.KeepAliveHandler, urllib2.HTTPSHandler):
        def __init__(self, ui):
            keepalive.KeepAliveHandler.__init__(self)
            urllib2.HTTPSHandler.__init__(self)
            self.ui = ui
            self.pwmgr = passwordmgr(self.ui)

        def _start_transaction(self, h, req):
            _generic_start_transaction(self, h, req)
            return keepalive.KeepAliveHandler._start_transaction(self, h, req)

        def https_open(self, req):
            # req.get_full_url() does not contain credentials and we may
            # need them to match the certificates.
            url = req.get_full_url()
            user, password = self.pwmgr.find_stored_password(url)
            res = httpconnectionmod.readauthforuri(self.ui, url, user)
            if res:
                group, auth = res
                self.auth = auth
                self.ui.debug("using auth.%s.* for authentication\n" % group)
            else:
                self.auth = None
            return self.do_open(self._makeconnection, req)

        def _makeconnection(self, host, port=None, *args, **kwargs):
            keyfile = None
            certfile = None

            if len(args) >= 1: # key_file
                keyfile = args[0]
            if len(args) >= 2: # cert_file
                certfile = args[1]
            args = args[2:]

            # if the user has specified different key/cert files in
            # hgrc, we prefer these
            if self.auth and 'key' in self.auth and 'cert' in self.auth:
                keyfile = self.auth['key']
                certfile = self.auth['cert']

            conn = httpsconnection(host, port, keyfile, certfile, *args,
                                   **kwargs)
            conn.ui = self.ui
            return conn

class httpdigestauthhandler(urllib2.HTTPDigestAuthHandler):
    def __init__(self, *args, **kwargs):
        urllib2.HTTPDigestAuthHandler.__init__(self, *args, **kwargs)
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
        # In python < 2.5 AbstractDigestAuthHandler raises a ValueError if
        # it doesn't know about the auth type requested. This can happen if
        # somebody is using BasicAuth and types a bad password.
        try:
            return urllib2.HTTPDigestAuthHandler.http_error_auth_reqed(
                        self, auth_header, host, req, headers)
        except ValueError, inst:
            arg = inst.args[0]
            if arg.startswith("AbstractDigestAuthHandler doesn't know "):
                return
            raise

class httpbasicauthhandler(urllib2.HTTPBasicAuthHandler):
    def __init__(self, *args, **kwargs):
        urllib2.HTTPBasicAuthHandler.__init__(self, *args, **kwargs)
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
        return urllib2.HTTPBasicAuthHandler.http_error_auth_reqed(
                        self, auth_header, host, req, headers)

handlerfuncs = []

def opener(ui, authinfo=None):
    '''
    construct an opener suitable for urllib2
    authinfo will be added to the password manager
    '''
    if ui.configbool('ui', 'usehttp2', False):
        handlers = [httpconnectionmod.http2handler(ui, passwordmgr(ui))]
    else:
        handlers = [httphandler()]
        if has_https:
            handlers.append(httpshandler(ui))

    handlers.append(proxyhandler(ui))

    passmgr = passwordmgr(ui)
    if authinfo is not None:
        passmgr.add_password(*authinfo)
        user, passwd = authinfo[2:4]
        ui.debug('http auth: user %s, password %s\n' %
                 (user, passwd and '*' * len(passwd) or 'not set'))

    handlers.extend((httpbasicauthhandler(passmgr),
                     httpdigestauthhandler(passmgr)))
    handlers.extend([h(ui, passmgr) for h in handlerfuncs])
    opener = urllib2.build_opener(*handlers)

    # 1.0 here is the _protocol_ version
    opener.addheaders = [('User-agent', 'mercurial/proto-1.0')]
    opener.addheaders.append(('Accept', 'application/mercurial-0.1'))
    return opener

def open(ui, url_, data=None):
    u = util.url(url_)
    if u.scheme:
        u.scheme = u.scheme.lower()
        url_, authinfo = u.authinfo()
    else:
        path = util.normpath(os.path.abspath(url_))
        url_ = 'file://' + urllib.pathname2url(path)
        authinfo = None
    return opener(ui, authinfo).open(url_, data)
