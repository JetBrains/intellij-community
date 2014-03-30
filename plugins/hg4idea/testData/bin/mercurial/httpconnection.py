# httpconnection.py - urllib2 handler for new http support
#
# Copyright 2005, 2006, 2007, 2008 Matt Mackall <mpm@selenic.com>
# Copyright 2006, 2007 Alexis S. L. Carvalho <alexis@cecm.usp.br>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
# Copyright 2011 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
import logging
import socket
import urllib
import urllib2
import os

from mercurial import httpclient
from mercurial import sslutil
from mercurial import util
from mercurial.i18n import _

# moved here from url.py to avoid a cycle
class httpsendfile(object):
    """This is a wrapper around the objects returned by python's "open".

    Its purpose is to send file-like objects via HTTP.
    It do however not define a __len__ attribute because the length
    might be more than Py_ssize_t can handle.
    """

    def __init__(self, ui, *args, **kwargs):
        # We can't just "self._data = open(*args, **kwargs)" here because there
        # is an "open" function defined in this module that shadows the global
        # one
        self.ui = ui
        self._data = open(*args, **kwargs)
        self.seek = self._data.seek
        self.close = self._data.close
        self.write = self._data.write
        self.length = os.fstat(self._data.fileno()).st_size
        self._pos = 0
        self._total = self.length // 1024 * 2

    def read(self, *args, **kwargs):
        try:
            ret = self._data.read(*args, **kwargs)
        except EOFError:
            self.ui.progress(_('sending'), None)
        self._pos += len(ret)
        # We pass double the max for total because we currently have
        # to send the bundle twice in the case of a server that
        # requires authentication. Since we can't know until we try
        # once whether authentication will be required, just lie to
        # the user and maybe the push succeeds suddenly at 50%.
        self.ui.progress(_('sending'), self._pos // 1024,
                         unit=_('kb'), total=self._total)
        return ret

# moved here from url.py to avoid a cycle
def readauthforuri(ui, uri, user):
    # Read configuration
    config = dict()
    for key, val in ui.configitems('auth'):
        if '.' not in key:
            ui.warn(_("ignoring invalid [auth] key '%s'\n") % key)
            continue
        group, setting = key.rsplit('.', 1)
        gdict = config.setdefault(group, dict())
        if setting in ('username', 'cert', 'key'):
            val = util.expandpath(val)
        gdict[setting] = val

    # Find the best match
    if '://' in uri:
        scheme, hostpath = uri.split('://', 1)
    else:
        # Python 2.4.1 doesn't provide the full URI
        scheme, hostpath = 'http', uri
    bestuser = None
    bestlen = 0
    bestauth = None
    for group, auth in config.iteritems():
        if user and user != auth.get('username', user):
            # If a username was set in the URI, the entry username
            # must either match it or be unset
            continue
        prefix = auth.get('prefix')
        if not prefix:
            continue
        p = prefix.split('://', 1)
        if len(p) > 1:
            schemes, prefix = [p[0]], p[1]
        else:
            schemes = (auth.get('schemes') or 'https').split()
        if (prefix == '*' or hostpath.startswith(prefix)) and \
            (len(prefix) > bestlen or (len(prefix) == bestlen and \
                not bestuser and 'username' in auth)) \
             and scheme in schemes:
            bestlen = len(prefix)
            bestauth = group, auth
            bestuser = auth.get('username')
            if user and not bestuser:
                auth['username'] = user
    return bestauth

# Mercurial (at least until we can remove the old codepath) requires
# that the http response object be sufficiently file-like, so we
# provide a close() method here.
class HTTPResponse(httpclient.HTTPResponse):
    def close(self):
        pass

class HTTPConnection(httpclient.HTTPConnection):
    response_class = HTTPResponse
    def request(self, method, uri, body=None, headers={}):
        if isinstance(body, httpsendfile):
            body.seek(0)
        httpclient.HTTPConnection.request(self, method, uri, body=body,
                                          headers=headers)


_configuredlogging = False
LOGFMT = '%(levelname)s:%(name)s:%(lineno)d:%(message)s'
# Subclass BOTH of these because otherwise urllib2 "helpfully"
# reinserts them since it notices we don't include any subclasses of
# them.
class http2handler(urllib2.HTTPHandler, urllib2.HTTPSHandler):
    def __init__(self, ui, pwmgr):
        global _configuredlogging
        urllib2.AbstractHTTPHandler.__init__(self)
        self.ui = ui
        self.pwmgr = pwmgr
        self._connections = {}
        loglevel = ui.config('ui', 'http2debuglevel', default=None)
        if loglevel and not _configuredlogging:
            _configuredlogging = True
            logger = logging.getLogger('mercurial.httpclient')
            logger.setLevel(getattr(logging, loglevel.upper()))
            handler = logging.StreamHandler()
            handler.setFormatter(logging.Formatter(LOGFMT))
            logger.addHandler(handler)

    def close_all(self):
        """Close and remove all connection objects being kept for reuse."""
        for openconns in self._connections.values():
            for conn in openconns:
                conn.close()
        self._connections = {}

    # shamelessly borrowed from urllib2.AbstractHTTPHandler
    def do_open(self, http_class, req, use_ssl):
        """Return an addinfourl object for the request, using http_class.

        http_class must implement the HTTPConnection API from httplib.
        The addinfourl return value is a file-like object.  It also
        has methods and attributes including:
            - info(): return a mimetools.Message object for the headers
            - geturl(): return the original request URL
            - code: HTTP status code
        """
        # If using a proxy, the host returned by get_host() is
        # actually the proxy. On Python 2.6.1, the real destination
        # hostname is encoded in the URI in the urllib2 request
        # object. On Python 2.6.5, it's stored in the _tunnel_host
        # attribute which has no accessor.
        tunhost = getattr(req, '_tunnel_host', None)
        host = req.get_host()
        if tunhost:
            proxyhost = host
            host = tunhost
        elif req.has_proxy():
            proxyhost = req.get_host()
            host = req.get_selector().split('://', 1)[1].split('/', 1)[0]
        else:
            proxyhost = None

        if proxyhost:
            if ':' in proxyhost:
                # Note: this means we'll explode if we try and use an
                # IPv6 http proxy. This isn't a regression, so we
                # won't worry about it for now.
                proxyhost, proxyport = proxyhost.rsplit(':', 1)
            else:
                proxyport = 3128 # squid default
            proxy = (proxyhost, proxyport)
        else:
            proxy = None

        if not host:
            raise urllib2.URLError('no host given')

        connkey = use_ssl, host, proxy
        allconns = self._connections.get(connkey, [])
        conns = [c for c in allconns if not c.busy()]
        if conns:
            h = conns[0]
        else:
            if allconns:
                self.ui.debug('all connections for %s busy, making a new '
                              'one\n' % host)
            timeout = None
            if req.timeout is not socket._GLOBAL_DEFAULT_TIMEOUT:
                timeout = req.timeout
            h = http_class(host, timeout=timeout, proxy_hostport=proxy)
            self._connections.setdefault(connkey, []).append(h)

        headers = dict(req.headers)
        headers.update(req.unredirected_hdrs)
        headers = dict(
            (name.title(), val) for name, val in headers.items())
        try:
            path = req.get_selector()
            if '://' in path:
                path = path.split('://', 1)[1].split('/', 1)[1]
            if path[0] != '/':
                path = '/' + path
            h.request(req.get_method(), path, req.data, headers)
            r = h.getresponse()
        except socket.error, err: # XXX what error?
            raise urllib2.URLError(err)

        # Pick apart the HTTPResponse object to get the addinfourl
        # object initialized properly.
        r.recv = r.read

        resp = urllib.addinfourl(r, r.headers, req.get_full_url())
        resp.code = r.status
        resp.msg = r.reason
        return resp

    # httplib always uses the given host/port as the socket connect
    # target, and then allows full URIs in the request path, which it
    # then observes and treats as a signal to do proxying instead.
    def http_open(self, req):
        if req.get_full_url().startswith('https'):
            return self.https_open(req)
        def makehttpcon(*args, **kwargs):
            k2 = dict(kwargs)
            k2['use_ssl'] = False
            return HTTPConnection(*args, **k2)
        return self.do_open(makehttpcon, req, False)

    def https_open(self, req):
        # req.get_full_url() does not contain credentials and we may
        # need them to match the certificates.
        url = req.get_full_url()
        user, password = self.pwmgr.find_stored_password(url)
        res = readauthforuri(self.ui, url, user)
        if res:
            group, auth = res
            self.auth = auth
            self.ui.debug("using auth.%s.* for authentication\n" % group)
        else:
            self.auth = None
        return self.do_open(self._makesslconnection, req, True)

    def _makesslconnection(self, host, port=443, *args, **kwargs):
        keyfile = None
        certfile = None

        if args: # key_file
            keyfile = args.pop(0)
        if args: # cert_file
            certfile = args.pop(0)

        # if the user has specified different key/cert files in
        # hgrc, we prefer these
        if self.auth and 'key' in self.auth and 'cert' in self.auth:
            keyfile = self.auth['key']
            certfile = self.auth['cert']

        # let host port take precedence
        if ':' in host and '[' not in host or ']:' in host:
            host, port = host.rsplit(':', 1)
            port = int(port)
            if '[' in host:
                host = host[1:-1]

        if keyfile:
            kwargs['keyfile'] = keyfile
        if certfile:
            kwargs['certfile'] = certfile

        kwargs.update(sslutil.sslkwargs(self.ui, host))

        con = HTTPConnection(host, port, use_ssl=True,
                             ssl_validator=sslutil.validator(self.ui, host),
                             **kwargs)
        return con
