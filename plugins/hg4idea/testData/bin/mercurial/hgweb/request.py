# hgweb/request.py - An http request from either CGI or the standalone server.
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

# import wsgiref.validate

from ..thirdparty import attr
from .. import (
    encoding,
    error,
    pycompat,
    util,
)
from ..utils import (
    urlutil,
)


class multidict(object):
    """A dict like object that can store multiple values for a key.

    Used to store parsed request parameters.

    This is inspired by WebOb's class of the same name.
    """

    def __init__(self):
        self._items = {}

    def __getitem__(self, key):
        """Returns the last set value for a key."""
        return self._items[key][-1]

    def __setitem__(self, key, value):
        """Replace a values for a key with a new value."""
        self._items[key] = [value]

    def __delitem__(self, key):
        """Delete all values for a key."""
        del self._items[key]

    def __contains__(self, key):
        return key in self._items

    def __len__(self):
        return len(self._items)

    def get(self, key, default=None):
        try:
            return self.__getitem__(key)
        except KeyError:
            return default

    def add(self, key, value):
        """Add a new value for a key. Does not replace existing values."""
        self._items.setdefault(key, []).append(value)

    def getall(self, key):
        """Obtains all values for a key."""
        return self._items.get(key, [])

    def getone(self, key):
        """Obtain a single value for a key.

        Raises KeyError if key not defined or it has multiple values set.
        """
        vals = self._items[key]

        if len(vals) > 1:
            raise KeyError(b'multiple values for %r' % key)

        return vals[0]

    def asdictoflists(self):
        return {k: list(v) for k, v in pycompat.iteritems(self._items)}


@attr.s(frozen=True)
class parsedrequest(object):
    """Represents a parsed WSGI request.

    Contains both parsed parameters as well as a handle on the input stream.
    """

    # Request method.
    method = attr.ib()
    # Full URL for this request.
    url = attr.ib()
    # URL without any path components. Just <proto>://<host><port>.
    baseurl = attr.ib()
    # Advertised URL. Like ``url`` and ``baseurl`` but uses SERVER_NAME instead
    # of HTTP: Host header for hostname. This is likely what clients used.
    advertisedurl = attr.ib()
    advertisedbaseurl = attr.ib()
    # URL scheme (part before ``://``). e.g. ``http`` or ``https``.
    urlscheme = attr.ib()
    # Value of REMOTE_USER, if set, or None.
    remoteuser = attr.ib()
    # Value of REMOTE_HOST, if set, or None.
    remotehost = attr.ib()
    # Relative WSGI application path. If defined, will begin with a
    # ``/``.
    apppath = attr.ib()
    # List of path parts to be used for dispatch.
    dispatchparts = attr.ib()
    # URL path component (no query string) used for dispatch. Can be
    # ``None`` to signal no path component given to the request, an
    # empty string to signal a request to the application's root URL,
    # or a string not beginning with ``/`` containing the requested
    # path under the application.
    dispatchpath = attr.ib()
    # The name of the repository being accessed.
    reponame = attr.ib()
    # Raw query string (part after "?" in URL).
    querystring = attr.ib()
    # multidict of query string parameters.
    qsparams = attr.ib()
    # wsgiref.headers.Headers instance. Operates like a dict with case
    # insensitive keys.
    headers = attr.ib()
    # Request body input stream.
    bodyfh = attr.ib()
    # WSGI environment dict, unmodified.
    rawenv = attr.ib()


def parserequestfromenv(env, reponame=None, altbaseurl=None, bodyfh=None):
    """Parse URL components from environment variables.

    WSGI defines request attributes via environment variables. This function
    parses the environment variables into a data structure.

    If ``reponame`` is defined, the leading path components matching that
    string are effectively shifted from ``PATH_INFO`` to ``SCRIPT_NAME``.
    This simulates the world view of a WSGI application that processes
    requests from the base URL of a repo.

    If ``altbaseurl`` (typically comes from ``web.baseurl`` config option)
    is defined, it is used - instead of the WSGI environment variables - for
    constructing URL components up to and including the WSGI application path.
    For example, if the current WSGI application is at ``/repo`` and a request
    is made to ``/rev/@`` with this argument set to
    ``http://myserver:9000/prefix``, the URL and path components will resolve as
    if the request were to ``http://myserver:9000/prefix/rev/@``. In other
    words, ``wsgi.url_scheme``, ``SERVER_NAME``, ``SERVER_PORT``, and
    ``SCRIPT_NAME`` are all effectively replaced by components from this URL.

    ``bodyfh`` can be used to specify a file object to read the request body
    from. If not defined, ``wsgi.input`` from the environment dict is used.
    """
    # PEP 3333 defines the WSGI spec and is a useful reference for this code.

    # We first validate that the incoming object conforms with the WSGI spec.
    # We only want to be dealing with spec-conforming WSGI implementations.
    # TODO enable this once we fix internal violations.
    # wsgiref.validate.check_environ(env)

    # PEP-0333 states that environment keys and values are native strings
    # (bytes on Python 2 and str on Python 3). The code points for the Unicode
    # strings on Python 3 must be between \00000-\000FF. We deal with bytes
    # in Mercurial, so mass convert string keys and values to bytes.
    if pycompat.ispy3:

        def tobytes(s):
            if not isinstance(s, str):
                return s
            if pycompat.iswindows:
                # This is what mercurial.encoding does for os.environ on
                # Windows.
                return encoding.strtolocal(s)
            else:
                # This is what is documented to be used for os.environ on Unix.
                return pycompat.fsencode(s)

        env = {tobytes(k): tobytes(v) for k, v in pycompat.iteritems(env)}

    # Some hosting solutions are emulating hgwebdir, and dispatching directly
    # to an hgweb instance using this environment variable.  This was always
    # checked prior to d7fd203e36cc; keep doing so to avoid breaking them.
    if not reponame:
        reponame = env.get(b'REPO_NAME')

    if altbaseurl:
        altbaseurl = urlutil.url(altbaseurl)

    # https://www.python.org/dev/peps/pep-0333/#environ-variables defines
    # the environment variables.
    # https://www.python.org/dev/peps/pep-0333/#url-reconstruction defines
    # how URLs are reconstructed.
    fullurl = env[b'wsgi.url_scheme'] + b'://'

    if altbaseurl and altbaseurl.scheme:
        advertisedfullurl = altbaseurl.scheme + b'://'
    else:
        advertisedfullurl = fullurl

    def addport(s, port):
        if s.startswith(b'https://'):
            if port != b'443':
                s += b':' + port
        else:
            if port != b'80':
                s += b':' + port

        return s

    if env.get(b'HTTP_HOST'):
        fullurl += env[b'HTTP_HOST']
    else:
        fullurl += env[b'SERVER_NAME']
        fullurl = addport(fullurl, env[b'SERVER_PORT'])

    if altbaseurl and altbaseurl.host:
        advertisedfullurl += altbaseurl.host

        if altbaseurl.port:
            port = altbaseurl.port
        elif altbaseurl.scheme == b'http' and not altbaseurl.port:
            port = b'80'
        elif altbaseurl.scheme == b'https' and not altbaseurl.port:
            port = b'443'
        else:
            port = env[b'SERVER_PORT']

        advertisedfullurl = addport(advertisedfullurl, port)
    else:
        advertisedfullurl += env[b'SERVER_NAME']
        advertisedfullurl = addport(advertisedfullurl, env[b'SERVER_PORT'])

    baseurl = fullurl
    advertisedbaseurl = advertisedfullurl

    fullurl += util.urlreq.quote(env.get(b'SCRIPT_NAME', b''))
    fullurl += util.urlreq.quote(env.get(b'PATH_INFO', b''))

    if altbaseurl:
        path = altbaseurl.path or b''
        if path and not path.startswith(b'/'):
            path = b'/' + path
        advertisedfullurl += util.urlreq.quote(path)
    else:
        advertisedfullurl += util.urlreq.quote(env.get(b'SCRIPT_NAME', b''))

    advertisedfullurl += util.urlreq.quote(env.get(b'PATH_INFO', b''))

    if env.get(b'QUERY_STRING'):
        fullurl += b'?' + env[b'QUERY_STRING']
        advertisedfullurl += b'?' + env[b'QUERY_STRING']

    # If ``reponame`` is defined, that must be a prefix on PATH_INFO
    # that represents the repository being dispatched to. When computing
    # the dispatch info, we ignore these leading path components.

    if altbaseurl:
        apppath = altbaseurl.path or b''
        if apppath and not apppath.startswith(b'/'):
            apppath = b'/' + apppath
    else:
        apppath = env.get(b'SCRIPT_NAME', b'')

    if reponame:
        repoprefix = b'/' + reponame.strip(b'/')

        if not env.get(b'PATH_INFO'):
            raise error.ProgrammingError(b'reponame requires PATH_INFO')

        if not env[b'PATH_INFO'].startswith(repoprefix):
            raise error.ProgrammingError(
                b'PATH_INFO does not begin with repo '
                b'name: %s (%s)' % (env[b'PATH_INFO'], reponame)
            )

        dispatchpath = env[b'PATH_INFO'][len(repoprefix) :]

        if dispatchpath and not dispatchpath.startswith(b'/'):
            raise error.ProgrammingError(
                b'reponame prefix of PATH_INFO does '
                b'not end at path delimiter: %s (%s)'
                % (env[b'PATH_INFO'], reponame)
            )

        apppath = apppath.rstrip(b'/') + repoprefix
        dispatchparts = dispatchpath.strip(b'/').split(b'/')
        dispatchpath = b'/'.join(dispatchparts)

    elif b'PATH_INFO' in env:
        if env[b'PATH_INFO'].strip(b'/'):
            dispatchparts = env[b'PATH_INFO'].strip(b'/').split(b'/')
            dispatchpath = b'/'.join(dispatchparts)
        else:
            dispatchparts = []
            dispatchpath = b''
    else:
        dispatchparts = []
        dispatchpath = None

    querystring = env.get(b'QUERY_STRING', b'')

    # We store as a list so we have ordering information. We also store as
    # a dict to facilitate fast lookup.
    qsparams = multidict()
    for k, v in util.urlreq.parseqsl(querystring, keep_blank_values=True):
        qsparams.add(k, v)

    # HTTP_* keys contain HTTP request headers. The Headers structure should
    # perform case normalization for us. We just rewrite underscore to dash
    # so keys match what likely went over the wire.
    headers = []
    for k, v in pycompat.iteritems(env):
        if k.startswith(b'HTTP_'):
            headers.append((k[len(b'HTTP_') :].replace(b'_', b'-'), v))

    from . import wsgiheaders  # avoid cycle

    headers = wsgiheaders.Headers(headers)

    # This is kind of a lie because the HTTP header wasn't explicitly
    # sent. But for all intents and purposes it should be OK to lie about
    # this, since a consumer will either either value to determine how many
    # bytes are available to read.
    if b'CONTENT_LENGTH' in env and b'HTTP_CONTENT_LENGTH' not in env:
        headers[b'Content-Length'] = env[b'CONTENT_LENGTH']

    if b'CONTENT_TYPE' in env and b'HTTP_CONTENT_TYPE' not in env:
        headers[b'Content-Type'] = env[b'CONTENT_TYPE']

    if bodyfh is None:
        bodyfh = env[b'wsgi.input']
        if b'Content-Length' in headers:
            bodyfh = util.cappedreader(
                bodyfh, int(headers[b'Content-Length'] or b'0')
            )

    return parsedrequest(
        method=env[b'REQUEST_METHOD'],
        url=fullurl,
        baseurl=baseurl,
        advertisedurl=advertisedfullurl,
        advertisedbaseurl=advertisedbaseurl,
        urlscheme=env[b'wsgi.url_scheme'],
        remoteuser=env.get(b'REMOTE_USER'),
        remotehost=env.get(b'REMOTE_HOST'),
        apppath=apppath,
        dispatchparts=dispatchparts,
        dispatchpath=dispatchpath,
        reponame=reponame,
        querystring=querystring,
        qsparams=qsparams,
        headers=headers,
        bodyfh=bodyfh,
        rawenv=env,
    )


class offsettrackingwriter(object):
    """A file object like object that is append only and tracks write count.

    Instances are bound to a callable. This callable is called with data
    whenever a ``write()`` is attempted.

    Instances track the amount of written data so they can answer ``tell()``
    requests.

    The intent of this class is to wrap the ``write()`` function returned by
    a WSGI ``start_response()`` function. Since ``write()`` is a callable and
    not a file object, it doesn't implement other file object methods.
    """

    def __init__(self, writefn):
        self._write = writefn
        self._offset = 0

    def write(self, s):
        res = self._write(s)
        # Some Python objects don't report the number of bytes written.
        if res is None:
            self._offset += len(s)
        else:
            self._offset += res

    def flush(self):
        pass

    def tell(self):
        return self._offset


class wsgiresponse(object):
    """Represents a response to a WSGI request.

    A response consists of a status line, headers, and a body.

    Consumers must populate the ``status`` and ``headers`` fields and
    make a call to a ``setbody*()`` method before the response can be
    issued.

    When it is time to start sending the response over the wire,
    ``sendresponse()`` is called. It handles emitting the header portion
    of the response message. It then yields chunks of body data to be
    written to the peer. Typically, the WSGI application itself calls
    and returns the value from ``sendresponse()``.
    """

    def __init__(self, req, startresponse):
        """Create an empty response tied to a specific request.

        ``req`` is a ``parsedrequest``. ``startresponse`` is the
        ``start_response`` function passed to the WSGI application.
        """
        self._req = req
        self._startresponse = startresponse

        self.status = None
        from . import wsgiheaders  # avoid cycle

        self.headers = wsgiheaders.Headers([])

        self._bodybytes = None
        self._bodygen = None
        self._bodywillwrite = False
        self._started = False
        self._bodywritefn = None

    def _verifybody(self):
        if (
            self._bodybytes is not None
            or self._bodygen is not None
            or self._bodywillwrite
        ):
            raise error.ProgrammingError(b'cannot define body multiple times')

    def setbodybytes(self, b):
        """Define the response body as static bytes.

        The empty string signals that there is no response body.
        """
        self._verifybody()
        self._bodybytes = b
        self.headers[b'Content-Length'] = b'%d' % len(b)

    def setbodygen(self, gen):
        """Define the response body as a generator of bytes."""
        self._verifybody()
        self._bodygen = gen

    def setbodywillwrite(self):
        """Signal an intent to use write() to emit the response body.

        **This is the least preferred way to send a body.**

        It is preferred for WSGI applications to emit a generator of chunks
        constituting the response body. However, some consumers can't emit
        data this way. So, WSGI provides a way to obtain a ``write(data)``
        function that can be used to synchronously perform an unbuffered
        write.

        Calling this function signals an intent to produce the body in this
        manner.
        """
        self._verifybody()
        self._bodywillwrite = True

    def sendresponse(self):
        """Send the generated response to the client.

        Before this is called, ``status`` must be set and one of
        ``setbodybytes()`` or ``setbodygen()`` must be called.

        Calling this method multiple times is not allowed.
        """
        if self._started:
            raise error.ProgrammingError(
                b'sendresponse() called multiple times'
            )

        self._started = True

        if not self.status:
            raise error.ProgrammingError(b'status line not defined')

        if (
            self._bodybytes is None
            and self._bodygen is None
            and not self._bodywillwrite
        ):
            raise error.ProgrammingError(b'response body not defined')

        # RFC 7232 Section 4.1 states that a 304 MUST generate one of
        # {Cache-Control, Content-Location, Date, ETag, Expires, Vary}
        # and SHOULD NOT generate other headers unless they could be used
        # to guide cache updates. Furthermore, RFC 7230 Section 3.3.2
        # states that no response body can be issued. Content-Length can
        # be sent. But if it is present, it should be the size of the response
        # that wasn't transferred.
        if self.status.startswith(b'304 '):
            # setbodybytes('') will set C-L to 0. This doesn't conform with the
            # spec. So remove it.
            if self.headers.get(b'Content-Length') == b'0':
                del self.headers[b'Content-Length']

            # Strictly speaking, this is too strict. But until it causes
            # problems, let's be strict.
            badheaders = {
                k
                for k in self.headers.keys()
                if k.lower()
                not in (
                    b'date',
                    b'etag',
                    b'expires',
                    b'cache-control',
                    b'content-location',
                    b'content-security-policy',
                    b'vary',
                )
            }
            if badheaders:
                raise error.ProgrammingError(
                    b'illegal header on 304 response: %s'
                    % b', '.join(sorted(badheaders))
                )

            if self._bodygen is not None or self._bodywillwrite:
                raise error.ProgrammingError(
                    b"must use setbodybytes('') with 304 responses"
                )

        # Various HTTP clients (notably httplib) won't read the HTTP response
        # until the HTTP request has been sent in full. If servers (us) send a
        # response before the HTTP request has been fully sent, the connection
        # may deadlock because neither end is reading.
        #
        # We work around this by "draining" the request data before
        # sending any response in some conditions.
        drain = False
        close = False

        # If the client sent Expect: 100-continue, we assume it is smart enough
        # to deal with the server sending a response before reading the request.
        # (httplib doesn't do this.)
        if self._req.headers.get(b'Expect', b'').lower() == b'100-continue':
            pass
        # Only tend to request methods that have bodies. Strictly speaking,
        # we should sniff for a body. But this is fine for our existing
        # WSGI applications.
        elif self._req.method not in (b'POST', b'PUT'):
            pass
        else:
            # If we don't know how much data to read, there's no guarantee
            # that we can drain the request responsibly. The WSGI
            # specification only says that servers *should* ensure the
            # input stream doesn't overrun the actual request. So there's
            # no guarantee that reading until EOF won't corrupt the stream
            # state.
            if not isinstance(self._req.bodyfh, util.cappedreader):
                close = True
            else:
                # We /could/ only drain certain HTTP response codes. But 200 and
                # non-200 wire protocol responses both require draining. Since
                # we have a capped reader in place for all situations where we
                # drain, it is safe to read from that stream. We'll either do
                # a drain or no-op if we're already at EOF.
                drain = True

        if close:
            self.headers[b'Connection'] = b'Close'

        if drain:
            assert isinstance(self._req.bodyfh, util.cappedreader)
            while True:
                chunk = self._req.bodyfh.read(32768)
                if not chunk:
                    break

        strheaders = [
            (pycompat.strurl(k), pycompat.strurl(v))
            for k, v in self.headers.items()
        ]
        write = self._startresponse(pycompat.sysstr(self.status), strheaders)

        if self._bodybytes:
            yield self._bodybytes
        elif self._bodygen:
            for chunk in self._bodygen:
                # PEP-3333 says that output must be bytes. And some WSGI
                # implementations enforce this. We cast bytes-like types here
                # for convenience.
                if isinstance(chunk, bytearray):
                    chunk = bytes(chunk)

                yield chunk
        elif self._bodywillwrite:
            self._bodywritefn = write
        else:
            error.ProgrammingError(b'do not know how to send body')

    def getbodyfile(self):
        """Obtain a file object like object representing the response body.

        For this to work, you must call ``setbodywillwrite()`` and then
        ``sendresponse()`` first. ``sendresponse()`` is a generator and the
        function won't run to completion unless the generator is advanced. The
        generator yields not items. The easiest way to consume it is with
        ``list(res.sendresponse())``, which should resolve to an empty list -
        ``[]``.
        """
        if not self._bodywillwrite:
            raise error.ProgrammingError(b'must call setbodywillwrite() first')

        if not self._started:
            raise error.ProgrammingError(
                b'must call sendresponse() first; did '
                b'you remember to consume it since it '
                b'is a generator?'
            )

        assert self._bodywritefn
        return offsettrackingwriter(self._bodywritefn)


def wsgiapplication(app_maker):
    """For compatibility with old CGI scripts. A plain hgweb() or hgwebdir()
    can and should now be used as a WSGI application."""
    application = app_maker()

    def run_wsgi(env, respond):
        return application(env, respond)

    return run_wsgi
