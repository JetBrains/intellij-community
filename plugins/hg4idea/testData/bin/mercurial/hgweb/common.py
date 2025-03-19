# hgweb/common.py - Utility functions needed by hgweb_mod and hgwebdir_mod
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import base64
import errno
import mimetypes
import os
import stat

from ..i18n import _
from ..pycompat import (
    open,
)
from .. import (
    encoding,
    pycompat,
    scmutil,
    templater,
    util,
)

httpserver = util.httpserver

HTTP_OK = 200
HTTP_CREATED = 201
HTTP_NOT_MODIFIED = 304
HTTP_BAD_REQUEST = 400
HTTP_UNAUTHORIZED = 401
HTTP_FORBIDDEN = 403
HTTP_NOT_FOUND = 404
HTTP_METHOD_NOT_ALLOWED = 405
HTTP_NOT_ACCEPTABLE = 406
HTTP_UNSUPPORTED_MEDIA_TYPE = 415
HTTP_SERVER_ERROR = 500

ismember = scmutil.ismember


def hashiddenaccess(repo, req):
    if bool(req.qsparams.get(b'access-hidden')):
        # Disable this by default for now. Main risk is to get critical
        # information exposed through this. This is expecially risky if
        # someone decided to make a changeset secret for good reason, but
        # its predecessors are still draft.
        #
        # The feature is currently experimental, so we can still decide to
        # change the default.
        ui = repo.ui
        allow = ui.configlist(b'experimental', b'server.allow-hidden-access')
        user = req.remoteuser
        if allow and ismember(ui, user, allow):
            return True
        else:
            msg = (
                _(
                    b'ignoring request to access hidden changeset by '
                    b'unauthorized user: %r\n'
                )
                % user
            )
            ui.warn(msg)
    return False


def checkauthz(hgweb, req, op):
    """Check permission for operation based on request data (including
    authentication info). Return if op allowed, else raise an ErrorResponse
    exception."""

    user = req.remoteuser

    deny_read = hgweb.configlist(b'web', b'deny_read')
    if deny_read and (not user or ismember(hgweb.repo.ui, user, deny_read)):
        raise ErrorResponse(HTTP_UNAUTHORIZED, b'read not authorized')

    allow_read = hgweb.configlist(b'web', b'allow_read')
    if allow_read and (not ismember(hgweb.repo.ui, user, allow_read)):
        raise ErrorResponse(HTTP_UNAUTHORIZED, b'read not authorized')

    if op == b'pull' and not hgweb.allowpull:
        raise ErrorResponse(HTTP_UNAUTHORIZED, b'pull not authorized')
    elif op == b'pull' or op is None:  # op is None for interface requests
        return

    # Allow LFS uploading via PUT requests
    if op == b'upload':
        if req.method != b'PUT':
            msg = b'upload requires PUT request'
            raise ErrorResponse(HTTP_METHOD_NOT_ALLOWED, msg)
    # enforce that you can only push using POST requests
    elif req.method != b'POST':
        msg = b'push requires POST request'
        raise ErrorResponse(HTTP_METHOD_NOT_ALLOWED, msg)

    # require ssl by default for pushing, auth info cannot be sniffed
    # and replayed
    if hgweb.configbool(b'web', b'push_ssl') and req.urlscheme != b'https':
        raise ErrorResponse(HTTP_FORBIDDEN, b'ssl required')

    deny = hgweb.configlist(b'web', b'deny_push')
    if deny and (not user or ismember(hgweb.repo.ui, user, deny)):
        raise ErrorResponse(HTTP_UNAUTHORIZED, b'push not authorized')

    allow = hgweb.configlist(b'web', b'allow-push')
    if not (allow and ismember(hgweb.repo.ui, user, allow)):
        raise ErrorResponse(HTTP_UNAUTHORIZED, b'push not authorized')


# Hooks for hgweb permission checks; extensions can add hooks here.
# Each hook is invoked like this: hook(hgweb, request, operation),
# where operation is either read, pull, push or upload. Hooks should either
# raise an ErrorResponse exception, or just return.
#
# It is possible to do both authentication and authorization through
# this.
permhooks = [checkauthz]


class ErrorResponse(Exception):
    def __init__(self, code, message=None, headers=None):
        if message is None:
            message = _statusmessage(code)
        Exception.__init__(self, pycompat.sysstr(message))
        self.code = code
        if headers is None:
            headers = []
        self.headers = headers
        self.message = message


class continuereader:
    """File object wrapper to handle HTTP 100-continue.

    This is used by servers so they automatically handle Expect: 100-continue
    request headers. On first read of the request body, the 100 Continue
    response is sent. This should trigger the client into actually sending
    the request body.
    """

    def __init__(self, f, write):
        self.f = f
        self._write = write
        self.continued = False

    def read(self, amt=-1):
        if not self.continued:
            self.continued = True
            self._write(b'HTTP/1.1 100 Continue\r\n\r\n')
        return self.f.read(amt)

    def __getattr__(self, attr):
        if attr in (b'close', b'readline', b'readlines', b'__iter__'):
            return getattr(self.f, attr)
        raise AttributeError


def _statusmessage(code):
    responses = httpserver.basehttprequesthandler.responses
    return pycompat.bytesurl(responses.get(code, ('Error', 'Unknown error'))[0])


def statusmessage(code, message=None):
    return b'%d %s' % (code, message or _statusmessage(code))


def get_stat(spath, fn):
    """stat fn if it exists, spath otherwise"""
    cl_path = os.path.join(spath, fn)
    if os.path.exists(cl_path):
        return os.stat(cl_path)
    else:
        return os.stat(spath)


def get_mtime(spath):
    return get_stat(spath, b"00changelog.i")[stat.ST_MTIME]


def ispathsafe(path):
    """Determine if a path is safe to use for filesystem access."""
    parts = path.split(b'/')
    for part in parts:
        if (
            part in (b'', pycompat.oscurdir, pycompat.ospardir)
            or pycompat.ossep in part
            or pycompat.osaltsep is not None
            and pycompat.osaltsep in part
        ):
            return False

    return True


def staticfile(templatepath, directory, fname, res):
    """return a file inside directory with guessed Content-Type header

    fname always uses '/' as directory separator and isn't allowed to
    contain unusual path components.
    Content-Type is guessed using the mimetypes module.
    Return an empty string if fname is illegal or file not found.

    """
    if not ispathsafe(fname):
        return

    if not directory:
        tp = templatepath or templater.templatedir()
        if tp is not None:
            directory = os.path.join(tp, b'static')

    fpath = os.path.join(*fname.split(b'/'))
    ct = pycompat.sysbytes(
        mimetypes.guess_type(pycompat.fsdecode(fpath))[0] or r"text/plain"
    )
    path = os.path.join(directory, fpath)
    try:
        os.stat(path)
        with open(path, b'rb') as fh:
            data = fh.read()
    except TypeError:
        raise ErrorResponse(HTTP_SERVER_ERROR, b'illegal filename')
    except OSError as err:
        if err.errno == errno.ENOENT:
            raise ErrorResponse(HTTP_NOT_FOUND)
        else:
            raise ErrorResponse(
                HTTP_SERVER_ERROR, encoding.strtolocal(err.strerror)
            )

    res.headers[b'Content-Type'] = ct
    res.setbodybytes(data)
    return res


def paritygen(stripecount, offset=0):
    """count parity of horizontal stripes for easier reading"""
    if stripecount and offset:
        # account for offset, e.g. due to building the list in reverse
        count = (stripecount + offset) % stripecount
        parity = (stripecount + offset) // stripecount & 1
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
    return (
        config(b"web", b"contact")
        or config(b"ui", b"username")
        or encoding.environ.get(b"EMAIL")
        or b""
    )


def cspvalues(ui):
    """Obtain the Content-Security-Policy header and nonce value.

    Returns a 2-tuple of the CSP header value and the nonce value.

    First value is ``None`` if CSP isn't enabled. Second value is ``None``
    if CSP isn't enabled or if the CSP header doesn't need a nonce.
    """
    # Without demandimport, "import uuid" could have an immediate side-effect
    # running "ldconfig" on Linux trying to find libuuid.
    # With Python <= 2.7.12, that "ldconfig" is run via a shell and the shell
    # may pollute the terminal with:
    #
    #   shell-init: error retrieving current directory: getcwd: cannot access
    #   parent directories: No such file or directory
    #
    # Python >= 2.7.13 has fixed it by running "ldconfig" directly without a
    # shell (hg changeset a09ae70f3489).
    #
    # Moved "import uuid" from here so it's executed after we know we have
    # a sane cwd (i.e. after dispatch.py cwd check).
    #
    # We can move it back once we no longer need Python <= 2.7.12 support.
    import uuid

    # Don't allow untrusted CSP setting since it be disable protections
    # from a trusted/global source.
    csp = ui.config(b'web', b'csp', untrusted=False)
    nonce = None

    if csp and b'%nonce%' in csp:
        nonce = base64.urlsafe_b64encode(uuid.uuid4().bytes).rstrip(b'=')
        csp = csp.replace(b'%nonce%', nonce)

    return csp, nonce
