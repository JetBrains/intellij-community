# urllibcompat.py - adapters to ease using urllib2 on Py2 and urllib on Py3
#
# Copyright 2017 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import http.server
import urllib.error
import urllib.parse
import urllib.request
import urllib.response

from . import pycompat

_sysstr = pycompat.sysstr


class _pycompatstub:
    def __init__(self):
        self._aliases = {}

    def _registeraliases(self, origin, items):
        """Add items that will be populated at the first access"""
        items = map(_sysstr, items)
        self._aliases.update(
            (item.replace('_', '').lower(), (origin, item)) for item in items
        )

    def _registeralias(self, origin, attr, name):
        """Alias ``origin``.``attr`` as ``name``"""
        self._aliases[_sysstr(name)] = (origin, _sysstr(attr))

    def __getattr__(self, name):
        try:
            origin, item = self._aliases[name]
        except KeyError:
            raise AttributeError(name)
        self.__dict__[name] = obj = getattr(origin, item)
        return obj


httpserver = _pycompatstub()
urlreq = _pycompatstub()
urlerr = _pycompatstub()

urlreq._registeraliases(
    urllib.parse,
    (
        b"splitattr",
        b"splitpasswd",
        b"splitport",
        b"splituser",
        b"urlparse",
        b"urlunparse",
    ),
)
urlreq._registeralias(urllib.parse, b"parse_qs", b"parseqs")
urlreq._registeralias(urllib.parse, b"parse_qsl", b"parseqsl")
urlreq._registeralias(urllib.parse, b"unquote_to_bytes", b"unquote")

urlreq._registeraliases(
    urllib.request,
    (
        b"AbstractHTTPHandler",
        b"BaseHandler",
        b"build_opener",
        b"FileHandler",
        b"FTPHandler",
        b"ftpwrapper",
        b"HTTPHandler",
        b"HTTPSHandler",
        b"install_opener",
        b"pathname2url",
        b"HTTPBasicAuthHandler",
        b"HTTPDigestAuthHandler",
        b"HTTPPasswordMgrWithDefaultRealm",
        b"ProxyHandler",
        b"Request",
        b"url2pathname",
        b"urlopen",
    ),
)


urlreq._registeraliases(
    urllib.response,
    (
        b"addclosehook",
        b"addinfourl",
    ),
)

urlerr._registeraliases(
    urllib.error,
    (
        b"HTTPError",
        b"URLError",
    ),
)

httpserver._registeraliases(
    http.server,
    (
        b"HTTPServer",
        b"BaseHTTPRequestHandler",
        b"SimpleHTTPRequestHandler",
        b"CGIHTTPRequestHandler",
    ),
)

# urllib.parse.quote() accepts both str and bytes, decodes bytes
# (if necessary), and returns str. This is wonky. We provide a custom
# implementation that only accepts bytes and emits bytes.
def quote(s, safe='/'):
    # bytestr has an __iter__ that emits characters. quote_from_bytes()
    # does an iteration and expects ints. We coerce to bytes to appease it.
    if isinstance(s, pycompat.bytestr):
        s = bytes(s)
    s = urllib.parse.quote_from_bytes(s, safe=safe)
    return s.encode('ascii', 'strict')


# urllib.parse.urlencode() returns str. We use this function to make
# sure we return bytes.
def urlencode(query, doseq=False):
    s = urllib.parse.urlencode(query, doseq=doseq)
    return s.encode('ascii')


urlreq.quote = quote
urlreq.urlencode = urlencode


def getfullurl(req):
    return req.full_url


def gethost(req):
    return req.host


def getselector(req):
    return req.selector


def getdata(req):
    return req.data


def hasdata(req):
    return req.data is not None
