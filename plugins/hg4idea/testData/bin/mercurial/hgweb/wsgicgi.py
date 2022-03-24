# hgweb/wsgicgi.py - CGI->WSGI translator
#
# Copyright 2006 Eric Hopper <hopper@omnifarious.org>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
#
# This was originally copied from the public domain code at
# http://www.python.org/dev/peps/pep-0333/#the-server-gateway-side

from __future__ import absolute_import

import os

from ..pycompat import getattr
from .. import pycompat

from ..utils import procutil

from . import common


def launch(application):
    procutil.setbinary(procutil.stdin)
    procutil.setbinary(procutil.stdout)

    environ = dict(pycompat.iteritems(os.environ))  # re-exports
    environ.setdefault('PATH_INFO', '')
    if environ.get('SERVER_SOFTWARE', '').startswith('Microsoft-IIS'):
        # IIS includes script_name in PATH_INFO
        scriptname = environ['SCRIPT_NAME']
        if environ['PATH_INFO'].startswith(scriptname):
            environ['PATH_INFO'] = environ['PATH_INFO'][len(scriptname) :]

    stdin = procutil.stdin
    if environ.get('HTTP_EXPECT', '').lower() == '100-continue':
        stdin = common.continuereader(stdin, procutil.stdout.write)

    environ['wsgi.input'] = stdin
    environ['wsgi.errors'] = procutil.stderr
    environ['wsgi.version'] = (1, 0)
    environ['wsgi.multithread'] = False
    environ['wsgi.multiprocess'] = True
    environ['wsgi.run_once'] = True

    if environ.get('HTTPS', 'off').lower() in ('on', '1', 'yes'):
        environ['wsgi.url_scheme'] = 'https'
    else:
        environ['wsgi.url_scheme'] = 'http'

    headers_set = []
    headers_sent = []
    out = procutil.stdout

    def write(data):
        if not headers_set:
            raise AssertionError(b"write() before start_response()")

        elif not headers_sent:
            # Before the first output, send the stored headers
            status, response_headers = headers_sent[:] = headers_set
            out.write(b'Status: %s\r\n' % pycompat.bytesurl(status))
            for hk, hv in response_headers:
                out.write(
                    b'%s: %s\r\n'
                    % (pycompat.bytesurl(hk), pycompat.bytesurl(hv))
                )
            out.write(b'\r\n')

        out.write(data)
        out.flush()

    def start_response(status, response_headers, exc_info=None):
        if exc_info:
            try:
                if headers_sent:
                    # Re-raise original exception if headers sent
                    raise exc_info[0](exc_info[1], exc_info[2])
            finally:
                del exc_info  # avoid dangling circular ref
        elif headers_set:
            raise AssertionError(b"Headers already set!")

        headers_set[:] = [status, response_headers]
        return write

    content = application(environ, start_response)
    try:
        for chunk in content:
            write(chunk)
        if not headers_sent:
            write(b'')  # send headers now if body was empty
    finally:
        getattr(content, 'close', lambda: None)()
