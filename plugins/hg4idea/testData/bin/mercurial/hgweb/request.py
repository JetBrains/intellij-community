# hgweb/request.py - An http request from either CGI or the standalone server.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import socket, cgi, errno
from mercurial import util
from common import ErrorResponse, statusmessage, HTTP_NOT_MODIFIED

shortcuts = {
    'cl': [('cmd', ['changelog']), ('rev', None)],
    'sl': [('cmd', ['shortlog']), ('rev', None)],
    'cs': [('cmd', ['changeset']), ('node', None)],
    'f': [('cmd', ['file']), ('filenode', None)],
    'fl': [('cmd', ['filelog']), ('filenode', None)],
    'fd': [('cmd', ['filediff']), ('node', None)],
    'fa': [('cmd', ['annotate']), ('filenode', None)],
    'mf': [('cmd', ['manifest']), ('manifest', None)],
    'ca': [('cmd', ['archive']), ('node', None)],
    'tags': [('cmd', ['tags'])],
    'tip': [('cmd', ['changeset']), ('node', ['tip'])],
    'static': [('cmd', ['static']), ('file', None)]
}

def normalize(form):
    # first expand the shortcuts
    for k in shortcuts.iterkeys():
        if k in form:
            for name, value in shortcuts[k]:
                if value is None:
                    value = form[k]
                form[name] = value
            del form[k]
    # And strip the values
    for k, v in form.iteritems():
        form[k] = [i.strip() for i in v]
    return form

class wsgirequest(object):
    def __init__(self, wsgienv, start_response):
        version = wsgienv['wsgi.version']
        if (version < (1, 0)) or (version >= (2, 0)):
            raise RuntimeError("Unknown and unsupported WSGI version %d.%d"
                               % version)
        self.inp = wsgienv['wsgi.input']
        self.err = wsgienv['wsgi.errors']
        self.threaded = wsgienv['wsgi.multithread']
        self.multiprocess = wsgienv['wsgi.multiprocess']
        self.run_once = wsgienv['wsgi.run_once']
        self.env = wsgienv
        self.form = normalize(cgi.parse(self.inp,
                                        self.env,
                                        keep_blank_values=1))
        self._start_response = start_response
        self.server_write = None
        self.headers = []

    def __iter__(self):
        return iter([])

    def read(self, count=-1):
        return self.inp.read(count)

    def drain(self):
        '''need to read all data from request, httplib is half-duplex'''
        length = int(self.env.get('CONTENT_LENGTH') or 0)
        for s in util.filechunkiter(self.inp, limit=length):
            pass

    def respond(self, status, type, filename=None, body=None):
        if self._start_response is not None:
            self.headers.append(('Content-Type', type))
            if filename:
                filename = (filename.split('/')[-1]
                            .replace('\\', '\\\\').replace('"', '\\"'))
                self.headers.append(('Content-Disposition',
                                     'inline; filename="%s"' % filename))
            if body is not None:
                self.headers.append(('Content-Length', str(len(body))))

            for k, v in self.headers:
                if not isinstance(v, str):
                    raise TypeError('header value must be string: %r' % (v,))

            if isinstance(status, ErrorResponse):
                self.headers.extend(status.headers)
                if status.code == HTTP_NOT_MODIFIED:
                    # RFC 2616 Section 10.3.5: 304 Not Modified has cases where
                    # it MUST NOT include any headers other than these and no
                    # body
                    self.headers = [(k, v) for (k, v) in self.headers if
                                    k in ('Date', 'ETag', 'Expires',
                                          'Cache-Control', 'Vary')]
                status = statusmessage(status.code, status.message)
            elif status == 200:
                status = '200 Script output follows'
            elif isinstance(status, int):
                status = statusmessage(status)

            self.server_write = self._start_response(status, self.headers)
            self._start_response = None
            self.headers = []
        if body is not None:
            self.write(body)
            self.server_write = None

    def write(self, thing):
        if thing:
            try:
                self.server_write(thing)
            except socket.error, inst:
                if inst[0] != errno.ECONNRESET:
                    raise

    def writelines(self, lines):
        for line in lines:
            self.write(line)

    def flush(self):
        return None

    def close(self):
        return None

def wsgiapplication(app_maker):
    '''For compatibility with old CGI scripts. A plain hgweb() or hgwebdir()
    can and should now be used as a WSGI application.'''
    application = app_maker()
    def run_wsgi(env, respond):
        return application(env, respond)
    return run_wsgi
