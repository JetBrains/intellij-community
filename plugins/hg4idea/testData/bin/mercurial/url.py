# url.py - HTTP handling for mercurial
#
# Copyright 2005, 2006, 2007, 2008 Matt Mackall <mpm@selenic.com>
# Copyright 2006, 2007 Alexis S. L. Carvalho <alexis@cecm.usp.br>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import urllib, urllib2, urlparse, httplib, os, re, socket, cStringIO
from i18n import _
import keepalive, util

def _urlunparse(scheme, netloc, path, params, query, fragment, url):
    '''Handle cases where urlunparse(urlparse(x://)) doesn't preserve the "//"'''
    result = urlparse.urlunparse((scheme, netloc, path, params, query, fragment))
    if (scheme and
        result.startswith(scheme + ':') and
        not result.startswith(scheme + '://') and
        url.startswith(scheme + '://')
       ):
        result = scheme + '://' + result[len(scheme + ':'):]
    return result

def hidepassword(url):
    '''hide user credential in a url string'''
    scheme, netloc, path, params, query, fragment = urlparse.urlparse(url)
    netloc = re.sub('([^:]*):([^@]*)@(.*)', r'\1:***@\3', netloc)
    return _urlunparse(scheme, netloc, path, params, query, fragment, url)

def removeauth(url):
    '''remove all authentication information from a url string'''
    scheme, netloc, path, params, query, fragment = urlparse.urlparse(url)
    netloc = netloc[netloc.find('@')+1:]
    return _urlunparse(scheme, netloc, path, params, query, fragment, url)

def netlocsplit(netloc):
    '''split [user[:passwd]@]host[:port] into 4-tuple.'''

    a = netloc.find('@')
    if a == -1:
        user, passwd = None, None
    else:
        userpass, netloc = netloc[:a], netloc[a + 1:]
        c = userpass.find(':')
        if c == -1:
            user, passwd = urllib.unquote(userpass), None
        else:
            user = urllib.unquote(userpass[:c])
            passwd = urllib.unquote(userpass[c + 1:])
    c = netloc.find(':')
    if c == -1:
        host, port = netloc, None
    else:
        host, port = netloc[:c], netloc[c + 1:]
    return host, port, user, passwd

def netlocunsplit(host, port, user=None, passwd=None):
    '''turn host, port, user, passwd into [user[:passwd]@]host[:port].'''
    if port:
        hostport = host + ':' + port
    else:
        hostport = host
    if user:
        quote = lambda s: urllib.quote(s, safe='')
        if passwd:
            userpass = quote(user) + ':' + quote(passwd)
        else:
            userpass = quote(user)
        return userpass + '@' + hostport
    return hostport

_safe = ('abcdefghijklmnopqrstuvwxyz'
         'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
         '0123456789' '_.-/')
_safeset = None
_hex = None
def quotepath(path):
    '''quote the path part of a URL

    This is similar to urllib.quote, but it also tries to avoid
    quoting things twice (inspired by wget):

    >>> quotepath('abc def')
    'abc%20def'
    >>> quotepath('abc%20def')
    'abc%20def'
    >>> quotepath('abc%20 def')
    'abc%20%20def'
    >>> quotepath('abc def%20')
    'abc%20def%20'
    >>> quotepath('abc def%2')
    'abc%20def%252'
    >>> quotepath('abc def%')
    'abc%20def%25'
    '''
    global _safeset, _hex
    if _safeset is None:
        _safeset = set(_safe)
        _hex = set('abcdefABCDEF0123456789')
    l = list(path)
    for i in xrange(len(l)):
        c = l[i]
        if (c == '%' and i + 2 < len(l) and
            l[i + 1] in _hex and l[i + 2] in _hex):
            pass
        elif c not in _safeset:
            l[i] = '%%%02X' % ord(c)
    return ''.join(l)

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

        if not user:
            auth = self.readauthtoken(authuri)
            if auth:
                user, passwd = auth.get('username'), auth.get('password')
        if not user or not passwd:
            if not self.ui.interactive():
                raise util.Abort(_('http authorization required'))

            self.ui.write(_("http authorization required\n"))
            self.ui.status(_("realm: %s\n") % realm)
            if user:
                self.ui.status(_("user: %s\n") % user)
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

    def readauthtoken(self, uri):
        # Read configuration
        config = dict()
        for key, val in self.ui.configitems('auth'):
            if '.' not in key:
                self.ui.warn(_("ignoring invalid [auth] key '%s'\n") % key)
                continue
            group, setting = key.split('.', 1)
            gdict = config.setdefault(group, dict())
            if setting in ('cert', 'key'):
                val = util.expandpath(val)
            gdict[setting] = val

        # Find the best match
        scheme, hostpath = uri.split('://', 1)
        bestlen = 0
        bestauth = None
        for auth in config.itervalues():
            prefix = auth.get('prefix')
            if not prefix:
                continue
            p = prefix.split('://', 1)
            if len(p) > 1:
                schemes, prefix = [p[0]], p[1]
            else:
                schemes = (auth.get('schemes') or 'https').split()
            if (prefix == '*' or hostpath.startswith(prefix)) and \
                len(prefix) > bestlen and scheme in schemes:
                bestlen = len(prefix)
                bestauth = auth
        return bestauth

class proxyhandler(urllib2.ProxyHandler):
    def __init__(self, ui):
        proxyurl = ui.config("http_proxy", "host") or os.getenv('http_proxy')
        # XXX proxyauthinfo = None

        if proxyurl:
            # proxy can be proper url or host[:port]
            if not (proxyurl.startswith('http:') or
                    proxyurl.startswith('https:')):
                proxyurl = 'http://' + proxyurl + '/'
            snpqf = urlparse.urlsplit(proxyurl)
            proxyscheme, proxynetloc, proxypath, proxyquery, proxyfrag = snpqf
            hpup = netlocsplit(proxynetloc)

            proxyhost, proxyport, proxyuser, proxypasswd = hpup
            if not proxyuser:
                proxyuser = ui.config("http_proxy", "user")
                proxypasswd = ui.config("http_proxy", "passwd")

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

            proxyurl = urlparse.urlunsplit((
                proxyscheme, netlocunsplit(proxyhost, proxyport,
                                                proxyuser, proxypasswd or ''),
                proxypath, proxyquery, proxyfrag))
            proxies = {'http': proxyurl, 'https': proxyurl}
            ui.debug('proxying through http://%s:%s\n' %
                      (proxyhost, proxyport))
        else:
            proxies = {}

        # urllib2 takes proxy values from the environment and those
        # will take precedence if found, so drop them
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

class httpsendfile(file):
    def __len__(self):
        return os.fstat(self.fileno()).st_size

def _gen_sendfile(connection):
    def _sendfile(self, data):
        # send a file
        if isinstance(data, httpsendfile):
            # if auth required, some data sent twice, so rewind here
            data.seek(0)
            for chunk in util.filechunkiter(data):
                connection.send(self, chunk)
        else:
            connection.send(self, data)
    return _sendfile

has_https = hasattr(urllib2, 'HTTPSHandler')
if has_https:
    try:
        # avoid using deprecated/broken FakeSocket in python 2.6
        import ssl
        _ssl_wrap_socket = ssl.wrap_socket
        CERT_REQUIRED = ssl.CERT_REQUIRED
    except ImportError:
        CERT_REQUIRED = 2

        def _ssl_wrap_socket(sock, key_file, cert_file,
                             cert_reqs=CERT_REQUIRED, ca_certs=None):
            if ca_certs:
                raise util.Abort(_(
                    'certificate checking requires Python 2.6'))

            ssl = socket.ssl(sock, key_file, cert_file)
            return httplib.FakeSocket(sock, ssl)

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

            raise socket.error, msg

class httpconnection(keepalive.HTTPConnection):
    # must be able to send big bundle as stream.
    send = _gen_sendfile(keepalive.HTTPConnection)

    def connect(self):
        if has_https and self.realhostport: # use CONNECT proxy
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.connect((self.host, self.port))
            if _generic_proxytunnel(self):
                # we do not support client x509 certificates
                self.sock = _ssl_wrap_socket(self.sock, None, None)
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
    if hasattr(req, '_tunnel_host') and req._tunnel_host:
        tunnel_host = req._tunnel_host
        if tunnel_host[:7] not in ['http://', 'https:/']:
            tunnel_host = 'https://' + tunnel_host
        new_tunnel = True
    else:
        tunnel_host = req.get_selector()
        new_tunnel = False

    if new_tunnel or tunnel_host == req.get_full_url(): # has proxy
        urlparts = urlparse.urlparse(tunnel_host)
        if new_tunnel or urlparts[0] == 'https': # only use CONNECT for HTTPS
            realhostport = urlparts[1]
            if realhostport[-1] == ']' or ':' not in realhostport:
                realhostport += ':443'

            h.realhostport = realhostport
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
    # NOTE: RFC 2616, S4.4, #3 says we ignore this if tr_enc is "chunked"
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

    def __del__(self):
        self.close_all()

if has_https:
    class BetterHTTPS(httplib.HTTPSConnection):
        send = keepalive.safesend

        def connect(self):
            if hasattr(self, 'ui'):
                cacerts = self.ui.config('web', 'cacerts')
            else:
                cacerts = None

            if cacerts:
                sock = _create_connection((self.host, self.port))
                self.sock = _ssl_wrap_socket(sock, self.key_file,
                        self.cert_file, cert_reqs=CERT_REQUIRED,
                        ca_certs=cacerts)
                self.ui.debug(_('server identity verification succeeded\n'))
            else:
                httplib.HTTPSConnection.connect(self)

    class httpsconnection(BetterHTTPS):
        response_class = keepalive.HTTPResponse
        # must be able to send big bundle as stream.
        send = _gen_sendfile(BetterHTTPS)
        getresponse = keepalive.wrapgetresponse(httplib.HTTPSConnection)

        def connect(self):
            if self.realhostport: # use CONNECT proxy
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.connect((self.host, self.port))
                if _generic_proxytunnel(self):
                    self.sock = _ssl_wrap_socket(self.sock, self.cert_file,
                                                 self.key_file)
            else:
                BetterHTTPS.connect(self)

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
            self.auth = self.pwmgr.readauthtoken(req.get_full_url())
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

            conn = httpsconnection(host, port, keyfile, certfile, *args, **kwargs)
            conn.ui = self.ui
            return conn

# In python < 2.5 AbstractDigestAuthHandler raises a ValueError if
# it doesn't know about the auth type requested.  This can happen if
# somebody is using BasicAuth and types a bad password.
class httpdigestauthhandler(urllib2.HTTPDigestAuthHandler):
    def http_error_auth_reqed(self, auth_header, host, req, headers):
        try:
            return urllib2.HTTPDigestAuthHandler.http_error_auth_reqed(
                        self, auth_header, host, req, headers)
        except ValueError, inst:
            arg = inst.args[0]
            if arg.startswith("AbstractDigestAuthHandler doesn't know "):
                return
            raise

def getauthinfo(path):
    scheme, netloc, urlpath, query, frag = urlparse.urlsplit(path)
    if not urlpath:
        urlpath = '/'
    if scheme != 'file':
        # XXX: why are we quoting the path again with some smart
        # heuristic here? Anyway, it cannot be done with file://
        # urls since path encoding is os/fs dependent (see
        # urllib.pathname2url() for details).
        urlpath = quotepath(urlpath)
    host, port, user, passwd = netlocsplit(netloc)

    # urllib cannot handle URLs with embedded user or passwd
    url = urlparse.urlunsplit((scheme, netlocunsplit(host, port),
                              urlpath, query, frag))
    if user:
        netloc = host
        if port:
            netloc += ':' + port
        # Python < 2.4.3 uses only the netloc to search for a password
        authinfo = (None, (url, netloc), user, passwd or '')
    else:
        authinfo = None
    return url, authinfo

handlerfuncs = []

def opener(ui, authinfo=None):
    '''
    construct an opener suitable for urllib2
    authinfo will be added to the password manager
    '''
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

    handlers.extend((urllib2.HTTPBasicAuthHandler(passmgr),
                     httpdigestauthhandler(passmgr)))
    handlers.extend([h(ui, passmgr) for h in handlerfuncs])
    opener = urllib2.build_opener(*handlers)

    # 1.0 here is the _protocol_ version
    opener.addheaders = [('User-agent', 'mercurial/proto-1.0')]
    opener.addheaders.append(('Accept', 'application/mercurial-0.1'))
    return opener

scheme_re = re.compile(r'^([a-zA-Z0-9+-.]+)://')

def open(ui, url, data=None):
    scheme = None
    m = scheme_re.search(url)
    if m:
        scheme = m.group(1).lower()
    if not scheme:
        path = util.normpath(os.path.abspath(url))
        url = 'file://' + urllib.pathname2url(path)
        authinfo = None
    else:
        url, authinfo = getauthinfo(url)
    return opener(ui, authinfo).open(url, data)
