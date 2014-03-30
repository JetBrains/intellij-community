# hgweb/common.py - Utility functions needed by hgweb_mod and hgwebdir_mod
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import errno, mimetypes, os

HTTP_OK = 200
HTTP_NOT_MODIFIED = 304
HTTP_BAD_REQUEST = 400
HTTP_UNAUTHORIZED = 401
HTTP_FORBIDDEN = 403
HTTP_NOT_FOUND = 404
HTTP_METHOD_NOT_ALLOWED = 405
HTTP_SERVER_ERROR = 500


def ismember(ui, username, userlist):
    """Check if username is a member of userlist.

    If userlist has a single '*' member, all users are considered members.
    Can be overriden by extensions to provide more complex authorization
    schemes.
    """
    return userlist == ['*'] or username in userlist

def checkauthz(hgweb, req, op):
    '''Check permission for operation based on request data (including
    authentication info). Return if op allowed, else raise an ErrorResponse
    exception.'''

    user = req.env.get('REMOTE_USER')

    deny_read = hgweb.configlist('web', 'deny_read')
    if deny_read and (not user or ismember(hgweb.repo.ui, user, deny_read)):
        raise ErrorResponse(HTTP_UNAUTHORIZED, 'read not authorized')

    allow_read = hgweb.configlist('web', 'allow_read')
    if allow_read and (not ismember(hgweb.repo.ui, user, allow_read)):
        raise ErrorResponse(HTTP_UNAUTHORIZED, 'read not authorized')

    if op == 'pull' and not hgweb.allowpull:
        raise ErrorResponse(HTTP_UNAUTHORIZED, 'pull not authorized')
    elif op == 'pull' or op is None: # op is None for interface requests
        return

    # enforce that you can only push using POST requests
    if req.env['REQUEST_METHOD'] != 'POST':
        msg = 'push requires POST request'
        raise ErrorResponse(HTTP_METHOD_NOT_ALLOWED, msg)

    # require ssl by default for pushing, auth info cannot be sniffed
    # and replayed
    scheme = req.env.get('wsgi.url_scheme')
    if hgweb.configbool('web', 'push_ssl', True) and scheme != 'https':
        raise ErrorResponse(HTTP_FORBIDDEN, 'ssl required')

    deny = hgweb.configlist('web', 'deny_push')
    if deny and (not user or ismember(hgweb.repo.ui, user, deny)):
        raise ErrorResponse(HTTP_UNAUTHORIZED, 'push not authorized')

    allow = hgweb.configlist('web', 'allow_push')
    if not (allow and ismember(hgweb.repo.ui, user, allow)):
        raise ErrorResponse(HTTP_UNAUTHORIZED, 'push not authorized')

# Hooks for hgweb permission checks; extensions can add hooks here.
# Each hook is invoked like this: hook(hgweb, request, operation),
# where operation is either read, pull or push. Hooks should either
# raise an ErrorResponse exception, or just return.
#
# It is possible to do both authentication and authorization through
# this.
permhooks = [checkauthz]


class ErrorResponse(Exception):
    def __init__(self, code, message=None, headers=[]):
        if message is None:
            message = _statusmessage(code)
        Exception.__init__(self)
        self.code = code
        self.message = message
        self.headers = headers
    def __str__(self):
        return self.message

class continuereader(object):
    def __init__(self, f, write):
        self.f = f
        self._write = write
        self.continued = False

    def read(self, amt=-1):
        if not self.continued:
            self.continued = True
            self._write('HTTP/1.1 100 Continue\r\n\r\n')
        return self.f.read(amt)

    def __getattr__(self, attr):
        if attr in ('close', 'readline', 'readlines', '__iter__'):
            return getattr(self.f, attr)
        raise AttributeError

def _statusmessage(code):
    from BaseHTTPServer import BaseHTTPRequestHandler
    responses = BaseHTTPRequestHandler.responses
    return responses.get(code, ('Error', 'Unknown error'))[0]

def statusmessage(code, message=None):
    return '%d %s' % (code, message or _statusmessage(code))

def get_stat(spath):
    """stat changelog if it exists, spath otherwise"""
    cl_path = os.path.join(spath, "00changelog.i")
    if os.path.exists(cl_path):
        return os.stat(cl_path)
    else:
        return os.stat(spath)

def get_mtime(spath):
    return get_stat(spath).st_mtime

def staticfile(directory, fname, req):
    """return a file inside directory with guessed Content-Type header

    fname always uses '/' as directory separator and isn't allowed to
    contain unusual path components.
    Content-Type is guessed using the mimetypes module.
    Return an empty string if fname is illegal or file not found.

    """
    parts = fname.split('/')
    for part in parts:
        if (part in ('', os.curdir, os.pardir) or
            os.sep in part or os.altsep is not None and os.altsep in part):
            return
    fpath = os.path.join(*parts)
    if isinstance(directory, str):
        directory = [directory]
    for d in directory:
        path = os.path.join(d, fpath)
        if os.path.exists(path):
            break
    try:
        os.stat(path)
        ct = mimetypes.guess_type(path)[0] or "text/plain"
        fp = open(path, 'rb')
        data = fp.read()
        fp.close()
        req.respond(HTTP_OK, ct, body=data)
    except TypeError:
        raise ErrorResponse(HTTP_SERVER_ERROR, 'illegal filename')
    except OSError, err:
        if err.errno == errno.ENOENT:
            raise ErrorResponse(HTTP_NOT_FOUND)
        else:
            raise ErrorResponse(HTTP_SERVER_ERROR, err.strerror)

def paritygen(stripecount, offset=0):
    """count parity of horizontal stripes for easier reading"""
    if stripecount and offset:
        # account for offset, e.g. due to building the list in reverse
        count = (stripecount + offset) % stripecount
        parity = (stripecount + offset) / stripecount & 1
    else:
        count = 0
        parity = 0
    while True:
        yield parity
        count += 1
        if stripecount and count >= stripecount:
            parity = 1 - parity
            count = 0

def get_contact(config):
    """Return repo contact information or empty string.

    web.contact is the primary source, but if that is not set, try
    ui.username or $EMAIL as a fallback to display something useful.
    """
    return (config("web", "contact") or
            config("ui", "username") or
            os.environ.get("EMAIL") or "")

def caching(web, req):
    tag = str(web.mtime)
    if req.env.get('HTTP_IF_NONE_MATCH') == tag:
        raise ErrorResponse(HTTP_NOT_MODIFIED)
    req.headers.append(('ETag', tag))
