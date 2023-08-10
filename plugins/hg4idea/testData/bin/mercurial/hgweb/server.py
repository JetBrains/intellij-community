# hgweb/server.py - The standalone hg web server.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno
import importlib
import os
import socket
import sys
import traceback
import wsgiref.validate

from ..i18n import _
from ..pycompat import (
    getattr,
    open,
)

from .. import (
    encoding,
    error,
    pycompat,
    util,
)
from ..utils import (
    urlutil,
)

httpservermod = util.httpserver
socketserver = util.socketserver
urlerr = util.urlerr
urlreq = util.urlreq

from . import common


def _splitURI(uri):
    """Return path and query that has been split from uri

    Just like CGI environment, the path is unquoted, the query is
    not.
    """
    if '?' in uri:
        path, query = uri.split('?', 1)
    else:
        path, query = uri, r''
    return urlreq.unquote(path), query


class _error_logger(object):
    def __init__(self, handler):
        self.handler = handler

    def flush(self):
        pass

    def write(self, str):
        self.writelines(str.split(b'\n'))

    def writelines(self, seq):
        for msg in seq:
            self.handler.log_error("HG error:  %s", encoding.strfromlocal(msg))


class _httprequesthandler(httpservermod.basehttprequesthandler):

    url_scheme = b'http'

    @staticmethod
    def preparehttpserver(httpserver, ui):
        """Prepare .socket of new HTTPServer instance"""

    def __init__(self, *args, **kargs):
        self.protocol_version = r'HTTP/1.1'
        httpservermod.basehttprequesthandler.__init__(self, *args, **kargs)

    def _log_any(self, fp, format, *args):
        fp.write(
            pycompat.sysbytes(
                r"%s - - [%s] %s"
                % (
                    self.client_address[0],
                    self.log_date_time_string(),
                    format % args,
                )
            )
            + b'\n'
        )
        fp.flush()

    def log_error(self, format, *args):
        self._log_any(self.server.errorlog, format, *args)

    def log_message(self, format, *args):
        self._log_any(self.server.accesslog, format, *args)

    def log_request(self, code='-', size='-'):
        xheaders = []
        if util.safehasattr(self, b'headers'):
            xheaders = [
                h for h in self.headers.items() if h[0].startswith('x-')
            ]
        self.log_message(
            '"%s" %s %s%s',
            self.requestline,
            str(code),
            str(size),
            ''.join([' %s:%s' % h for h in sorted(xheaders)]),
        )

    def do_write(self):
        try:
            self.do_hgweb()
        except socket.error as inst:
            if inst.errno != errno.EPIPE:
                raise

    def do_POST(self):
        try:
            self.do_write()
        except Exception as e:
            # I/O below could raise another exception. So log the original
            # exception first to ensure it is recorded.
            if not (
                isinstance(e, (OSError, socket.error))
                and e.errno == errno.ECONNRESET
            ):
                tb = "".join(traceback.format_exception(*sys.exc_info()))
                # We need a native-string newline to poke in the log
                # message, because we won't get a newline when using an
                # r-string. This is the easy way out.
                newline = chr(10)
                self.log_error(
                    r"Exception happened during processing "
                    "request '%s':%s%s",
                    self.path,
                    newline,
                    tb,
                )

            self._start_response("500 Internal Server Error", [])
            self._write(b"Internal Server Error")
            self._done()

    def do_PUT(self):
        self.do_POST()

    def do_GET(self):
        self.do_POST()

    def do_hgweb(self):
        self.sent_headers = False
        path, query = _splitURI(self.path)

        # Ensure the slicing of path below is valid
        if path != self.server.prefix and not path.startswith(
            self.server.prefix + b'/'
        ):
            self._start_response(pycompat.strurl(common.statusmessage(404)), [])
            if self.command == 'POST':
                # Paranoia: tell the client we're going to close the
                # socket so they don't try and reuse a socket that
                # might have a POST body waiting to confuse us. We do
                # this by directly munging self.saved_headers because
                # self._start_response ignores Connection headers.
                self.saved_headers = [('Connection', 'Close')]
            self._write(b"Not Found")
            self._done()
            return

        env = {}
        env['GATEWAY_INTERFACE'] = 'CGI/1.1'
        env['REQUEST_METHOD'] = self.command
        env['SERVER_NAME'] = self.server.server_name
        env['SERVER_PORT'] = str(self.server.server_port)
        env['REQUEST_URI'] = self.path
        env['SCRIPT_NAME'] = pycompat.sysstr(self.server.prefix)
        env['PATH_INFO'] = pycompat.sysstr(path[len(self.server.prefix) :])
        env['REMOTE_HOST'] = self.client_address[0]
        env['REMOTE_ADDR'] = self.client_address[0]
        env['QUERY_STRING'] = query or ''

        if pycompat.ispy3:
            if self.headers.get_content_type() is None:
                env['CONTENT_TYPE'] = self.headers.get_default_type()
            else:
                env['CONTENT_TYPE'] = self.headers.get_content_type()
            length = self.headers.get('content-length')
        else:
            if self.headers.typeheader is None:
                env['CONTENT_TYPE'] = self.headers.type
            else:
                env['CONTENT_TYPE'] = self.headers.typeheader
            length = self.headers.getheader('content-length')
        if length:
            env['CONTENT_LENGTH'] = length
        for header in [
            h
            for h in self.headers.keys()
            if h.lower() not in ('content-type', 'content-length')
        ]:
            hkey = 'HTTP_' + header.replace('-', '_').upper()
            hval = self.headers.get(header)
            hval = hval.replace('\n', '').strip()
            if hval:
                env[hkey] = hval
        env['SERVER_PROTOCOL'] = self.request_version
        env['wsgi.version'] = (1, 0)
        env['wsgi.url_scheme'] = pycompat.sysstr(self.url_scheme)
        if env.get('HTTP_EXPECT', b'').lower() == b'100-continue':
            self.rfile = common.continuereader(self.rfile, self.wfile.write)

        env['wsgi.input'] = self.rfile
        env['wsgi.errors'] = _error_logger(self)
        env['wsgi.multithread'] = isinstance(
            self.server, socketserver.ThreadingMixIn
        )
        if util.safehasattr(socketserver, b'ForkingMixIn'):
            env['wsgi.multiprocess'] = isinstance(
                self.server, socketserver.ForkingMixIn
            )
        else:
            env['wsgi.multiprocess'] = False

        env['wsgi.run_once'] = 0

        wsgiref.validate.check_environ(env)

        self.saved_status = None
        self.saved_headers = []
        self.length = None
        self._chunked = None
        for chunk in self.server.application(env, self._start_response):
            self._write(chunk)
        if not self.sent_headers:
            self.send_headers()
        self._done()

    def send_headers(self):
        if not self.saved_status:
            raise AssertionError(
                b"Sending headers before start_response() called"
            )
        saved_status = self.saved_status.split(None, 1)
        saved_status[0] = int(saved_status[0])
        self.send_response(*saved_status)
        self.length = None
        self._chunked = False
        for h in self.saved_headers:
            self.send_header(*h)
            if h[0].lower() == 'content-length':
                self.length = int(h[1])
        if self.length is None and saved_status[0] != common.HTTP_NOT_MODIFIED:
            self._chunked = (
                not self.close_connection and self.request_version == 'HTTP/1.1'
            )
            if self._chunked:
                self.send_header('Transfer-Encoding', 'chunked')
            else:
                self.send_header('Connection', 'close')
        self.end_headers()
        self.sent_headers = True

    def _start_response(self, http_status, headers, exc_info=None):
        assert isinstance(http_status, str)
        code, msg = http_status.split(None, 1)
        code = int(code)
        self.saved_status = http_status
        bad_headers = ('connection', 'transfer-encoding')
        self.saved_headers = [
            h for h in headers if h[0].lower() not in bad_headers
        ]
        return self._write

    def _write(self, data):
        if not self.saved_status:
            raise AssertionError(b"data written before start_response() called")
        elif not self.sent_headers:
            self.send_headers()
        if self.length is not None:
            if len(data) > self.length:
                raise AssertionError(
                    b"Content-length header sent, but more "
                    b"bytes than specified are being written."
                )
            self.length = self.length - len(data)
        elif self._chunked and data:
            data = b'%x\r\n%s\r\n' % (len(data), data)
        self.wfile.write(data)
        self.wfile.flush()

    def _done(self):
        if self._chunked:
            self.wfile.write(b'0\r\n\r\n')
            self.wfile.flush()

    def version_string(self):
        if self.server.serverheader:
            return encoding.strfromlocal(self.server.serverheader)
        return httpservermod.basehttprequesthandler.version_string(self)


class _httprequesthandlerssl(_httprequesthandler):
    """HTTPS handler based on Python's ssl module"""

    url_scheme = b'https'

    @staticmethod
    def preparehttpserver(httpserver, ui):
        try:
            from .. import sslutil

            sslutil.wrapserversocket
        except ImportError:
            raise error.Abort(_(b"SSL support is unavailable"))

        certfile = ui.config(b'web', b'certificate')

        # These config options are currently only meant for testing. Use
        # at your own risk.
        cafile = ui.config(b'devel', b'servercafile')
        reqcert = ui.configbool(b'devel', b'serverrequirecert')

        httpserver.socket = sslutil.wrapserversocket(
            httpserver.socket,
            ui,
            certfile=certfile,
            cafile=cafile,
            requireclientcert=reqcert,
        )

    def setup(self):
        self.connection = self.request
        self.rfile = self.request.makefile("rb", self.rbufsize)
        self.wfile = self.request.makefile("wb", self.wbufsize)


try:
    import threading

    threading.active_count()  # silence pyflakes and bypass demandimport
    _mixin = socketserver.ThreadingMixIn
except ImportError:
    if util.safehasattr(os, b"fork"):
        _mixin = socketserver.ForkingMixIn
    else:

        class _mixin(object):
            pass


def openlog(opt, default):
    if opt and opt != b'-':
        return open(opt, b'ab')
    return default


class MercurialHTTPServer(_mixin, httpservermod.httpserver, object):

    # SO_REUSEADDR has broken semantics on windows
    if pycompat.iswindows:
        allow_reuse_address = 0

    def __init__(self, ui, app, addr, handler, **kwargs):
        httpservermod.httpserver.__init__(self, addr, handler, **kwargs)
        self.daemon_threads = True
        self.application = app

        handler.preparehttpserver(self, ui)

        prefix = ui.config(b'web', b'prefix')
        if prefix:
            prefix = b'/' + prefix.strip(b'/')
        self.prefix = prefix

        alog = openlog(ui.config(b'web', b'accesslog'), ui.fout)
        elog = openlog(ui.config(b'web', b'errorlog'), ui.ferr)
        self.accesslog = alog
        self.errorlog = elog

        self.addr, self.port = self.socket.getsockname()[0:2]
        self.fqaddr = self.server_name

        self.serverheader = ui.config(b'web', b'server-header')


class IPv6HTTPServer(MercurialHTTPServer):
    address_family = getattr(socket, 'AF_INET6', None)

    def __init__(self, *args, **kwargs):
        if self.address_family is None:
            raise error.RepoError(_(b'IPv6 is not available on this system'))
        super(IPv6HTTPServer, self).__init__(*args, **kwargs)


def create_server(ui, app):

    if ui.config(b'web', b'certificate'):
        handler = _httprequesthandlerssl
    else:
        handler = _httprequesthandler

    if ui.configbool(b'web', b'ipv6'):
        cls = IPv6HTTPServer
    else:
        cls = MercurialHTTPServer

    # ugly hack due to python issue5853 (for threaded use)
    try:
        import mimetypes

        mimetypes.init()
    except UnicodeDecodeError:
        # Python 2.x's mimetypes module attempts to decode strings
        # from Windows' ANSI APIs as ascii (fail), then re-encode them
        # as ascii (clown fail), because the default Python Unicode
        # codec is hardcoded as ascii.

        sys.argv  # unwrap demand-loader so that reload() works
        # resurrect sys.setdefaultencoding()
        try:
            importlib.reload(sys)
        except AttributeError:
            reload(sys)
        oldenc = sys.getdefaultencoding()
        sys.setdefaultencoding(b"latin1")  # or any full 8-bit encoding
        mimetypes.init()
        sys.setdefaultencoding(oldenc)

    address = ui.config(b'web', b'address')
    port = urlutil.getport(ui.config(b'web', b'port'))
    try:
        return cls(ui, app, (address, port), handler)
    except socket.error as inst:
        raise error.Abort(
            _(b"cannot start server at '%s:%d': %s")
            % (address, port, encoding.strtolocal(inst.args[1]))
        )
