# templatefilters.py - common template expansion filters
#
# Copyright 2005-2008 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import os
import re
import time

from .i18n import _
from .node import hex
from . import (
    encoding,
    error,
    pycompat,
    registrar,
    smartset,
    templateutil,
    url,
    util,
)
from .utils import (
    cborutil,
    dateutil,
    stringutil,
)

urlerr = util.urlerr
urlreq = util.urlreq

# filters are callables like:
#   fn(obj)
# with:
#   obj - object to be filtered (text, date, list and so on)
filters = {}

templatefilter = registrar.templatefilter(filters)


@templatefilter(b'addbreaks', intype=bytes)
def addbreaks(text):
    """Any text. Add an XHTML "<br />" tag before the end of
    every line except the last.
    """
    return text.replace(b'\n', b'<br/>\n')


agescales = [
    (b"year", 3600 * 24 * 365, b'Y'),
    (b"month", 3600 * 24 * 30, b'M'),
    (b"week", 3600 * 24 * 7, b'W'),
    (b"day", 3600 * 24, b'd'),
    (b"hour", 3600, b'h'),
    (b"minute", 60, b'm'),
    (b"second", 1, b's'),
]


@templatefilter(b'age', intype=templateutil.date)
def age(date, abbrev=False):
    """Date. Returns a human-readable date/time difference between the
    given date/time and the current date/time.
    """

    def plural(t, c):
        if c == 1:
            return t
        return t + b"s"

    def fmt(t, c, a):
        if abbrev:
            return b"%d%s" % (c, a)
        return b"%d %s" % (c, plural(t, c))

    now = time.time()
    then = date[0]
    future = False
    if then > now:
        future = True
        delta = max(1, int(then - now))
        if delta > agescales[0][1] * 30:
            return b'in the distant future'
    else:
        delta = max(1, int(now - then))
        if delta > agescales[0][1] * 2:
            return dateutil.shortdate(date)

    for t, s, a in agescales:
        n = delta // s
        if n >= 2 or s == 1:
            if future:
                return b'%s from now' % fmt(t, n, a)
            return b'%s ago' % fmt(t, n, a)


@templatefilter(b'basename', intype=bytes)
def basename(path):
    """Any text. Treats the text as a path, and returns the last
    component of the path after splitting by the path separator.
    For example, "foo/bar/baz" becomes "baz" and "foo/bar//" becomes "".
    """
    return os.path.basename(path)


def _tocborencodable(obj):
    if isinstance(obj, smartset.abstractsmartset):
        return list(obj)
    return obj


@templatefilter(b'cbor')
def cbor(obj):
    """Any object. Serializes the object to CBOR bytes."""
    # cborutil is stricter about type than json() filter
    obj = pycompat.rapply(_tocborencodable, obj)
    return b''.join(cborutil.streamencode(obj))


@templatefilter(b'commondir')
def commondir(filelist):
    """List of text. Treats each list item as file name with /
    as path separator and returns the longest common directory
    prefix shared by all list items.
    Returns the empty string if no common prefix exists.

    The list items are not normalized, i.e. "foo/../bar" is handled as
    file "bar" in the directory "foo/..". Leading slashes are ignored.

    For example, ["foo/bar/baz", "foo/baz/bar"] becomes "foo" and
    ["foo/bar", "baz"] becomes "".
    """

    def common(a, b):
        if len(a) > len(b):
            a = b[: len(a)]
        elif len(b) > len(a):
            b = b[: len(a)]
        if a == b:
            return a
        for i in pycompat.xrange(len(a)):
            if a[i] != b[i]:
                return a[:i]
        return a

    try:
        if not filelist:
            return b""
        dirlist = [f.lstrip(b'/').split(b'/')[:-1] for f in filelist]
        if len(dirlist) == 1:
            return b'/'.join(dirlist[0])
        a = min(dirlist)
        b = max(dirlist)
        # The common prefix of a and b is shared with all
        # elements of the list since Python sorts lexicographical
        # and [1, x] after [1].
        return b'/'.join(common(a, b))
    except TypeError:
        raise error.ParseError(_(b'argument is not a list of text'))


@templatefilter(b'count')
def count(i):
    """List or text. Returns the length as an integer."""
    try:
        return len(i)
    except TypeError:
        raise error.ParseError(_(b'not countable'))


@templatefilter(b'dirname', intype=bytes)
def dirname(path):
    """Any text. Treats the text as a path, and strips the last
    component of the path after splitting by the path separator.
    """
    return os.path.dirname(path)


@templatefilter(b'domain', intype=bytes)
def domain(author):
    """Any text. Finds the first string that looks like an email
    address, and extracts just the domain component. Example: ``User
    <user@example.com>`` becomes ``example.com``.
    """
    f = author.find(b'@')
    if f == -1:
        return b''
    author = author[f + 1 :]
    f = author.find(b'>')
    if f >= 0:
        author = author[:f]
    return author


@templatefilter(b'email', intype=bytes)
def email(text):
    """Any text. Extracts the first string that looks like an email
    address. Example: ``User <user@example.com>`` becomes
    ``user@example.com``.
    """
    return stringutil.email(text)


@templatefilter(b'escape', intype=bytes)
def escape(text):
    """Any text. Replaces the special XML/XHTML characters "&", "<"
    and ">" with XML entities, and filters out NUL characters.
    """
    return url.escape(text.replace(b'\0', b''), True)


para_re = None
space_re = None


def fill(text, width, initindent=b'', hangindent=b''):
    '''fill many paragraphs with optional indentation.'''
    global para_re, space_re
    if para_re is None:
        para_re = re.compile(b'(\n\n|\n\\s*[-*]\\s*)', re.M)
        space_re = re.compile(br'  +')

    def findparas():
        start = 0
        while True:
            m = para_re.search(text, start)
            if not m:
                uctext = encoding.unifromlocal(text[start:])
                w = len(uctext)
                while w > 0 and uctext[w - 1].isspace():
                    w -= 1
                yield (
                    encoding.unitolocal(uctext[:w]),
                    encoding.unitolocal(uctext[w:]),
                )
                break
            yield text[start : m.start(0)], m.group(1)
            start = m.end(1)

    return b"".join(
        [
            stringutil.wrap(
                space_re.sub(b' ', stringutil.wrap(para, width)),
                width,
                initindent,
                hangindent,
            )
            + rest
            for para, rest in findparas()
        ]
    )


@templatefilter(b'fill68', intype=bytes)
def fill68(text):
    """Any text. Wraps the text to fit in 68 columns."""
    return fill(text, 68)


@templatefilter(b'fill76', intype=bytes)
def fill76(text):
    """Any text. Wraps the text to fit in 76 columns."""
    return fill(text, 76)


@templatefilter(b'firstline', intype=bytes)
def firstline(text):
    """Any text. Returns the first line of text."""
    try:
        return text.splitlines(True)[0].rstrip(b'\r\n')
    except IndexError:
        return b''


@templatefilter(b'hex', intype=bytes)
def hexfilter(text):
    """Any text. Convert a binary Mercurial node identifier into
    its long hexadecimal representation.
    """
    return hex(text)


@templatefilter(b'hgdate', intype=templateutil.date)
def hgdate(text):
    """Date. Returns the date as a pair of numbers: "1157407993
    25200" (Unix timestamp, timezone offset).
    """
    return b"%d %d" % text


@templatefilter(b'isodate', intype=templateutil.date)
def isodate(text):
    """Date. Returns the date in ISO 8601 format: "2009-08-18 13:00
    +0200".
    """
    return dateutil.datestr(text, b'%Y-%m-%d %H:%M %1%2')


@templatefilter(b'isodatesec', intype=templateutil.date)
def isodatesec(text):
    """Date. Returns the date in ISO 8601 format, including
    seconds: "2009-08-18 13:00:13 +0200". See also the rfc3339date
    filter.
    """
    return dateutil.datestr(text, b'%Y-%m-%d %H:%M:%S %1%2')


def indent(text, prefix, firstline=b''):
    '''indent each non-empty line of text after first with prefix.'''
    lines = text.splitlines()
    num_lines = len(lines)
    endswithnewline = text[-1:] == b'\n'

    def indenter():
        for i in pycompat.xrange(num_lines):
            l = lines[i]
            if l.strip():
                yield prefix if i else firstline
            yield l
            if i < num_lines - 1 or endswithnewline:
                yield b'\n'

    return b"".join(indenter())


@templatefilter(b'json')
def json(obj, paranoid=True):
    """Any object. Serializes the object to a JSON formatted text."""
    if obj is None:
        return b'null'
    elif obj is False:
        return b'false'
    elif obj is True:
        return b'true'
    elif isinstance(obj, (int, pycompat.long, float)):
        return pycompat.bytestr(obj)
    elif isinstance(obj, bytes):
        return b'"%s"' % encoding.jsonescape(obj, paranoid=paranoid)
    elif isinstance(obj, type(u'')):
        raise error.ProgrammingError(
            b'Mercurial only does output with bytes: %r' % obj
        )
    elif util.safehasattr(obj, b'keys'):
        out = [
            b'"%s": %s'
            % (encoding.jsonescape(k, paranoid=paranoid), json(v, paranoid))
            for k, v in sorted(pycompat.iteritems(obj))
        ]
        return b'{' + b', '.join(out) + b'}'
    elif util.safehasattr(obj, b'__iter__'):
        out = [json(i, paranoid) for i in obj]
        return b'[' + b', '.join(out) + b']'
    raise error.ProgrammingError(b'cannot encode %r' % obj)


@templatefilter(b'lower', intype=bytes)
def lower(text):
    """Any text. Converts the text to lowercase."""
    return encoding.lower(text)


@templatefilter(b'nonempty', intype=bytes)
def nonempty(text):
    """Any text. Returns '(none)' if the string is empty."""
    return text or b"(none)"


@templatefilter(b'obfuscate', intype=bytes)
def obfuscate(text):
    """Any text. Returns the input text rendered as a sequence of
    XML entities.
    """
    text = pycompat.unicode(
        text, pycompat.sysstr(encoding.encoding), r'replace'
    )
    return b''.join([b'&#%d;' % ord(c) for c in text])


@templatefilter(b'permissions', intype=bytes)
def permissions(flags):
    if b"l" in flags:
        return b"lrwxrwxrwx"
    if b"x" in flags:
        return b"-rwxr-xr-x"
    return b"-rw-r--r--"


@templatefilter(b'person', intype=bytes)
def person(author):
    """Any text. Returns the name before an email address,
    interpreting it as per RFC 5322.
    """
    return stringutil.person(author)


@templatefilter(b'revescape', intype=bytes)
def revescape(text):
    """Any text. Escapes all "special" characters, except @.
    Forward slashes are escaped twice to prevent web servers from prematurely
    unescaping them. For example, "@foo bar/baz" becomes "@foo%20bar%252Fbaz".
    """
    return urlreq.quote(text, safe=b'/@').replace(b'/', b'%252F')


@templatefilter(b'rfc3339date', intype=templateutil.date)
def rfc3339date(text):
    """Date. Returns a date using the Internet date format
    specified in RFC 3339: "2009-08-18T13:00:13+02:00".
    """
    return dateutil.datestr(text, b"%Y-%m-%dT%H:%M:%S%1:%2")


@templatefilter(b'rfc822date', intype=templateutil.date)
def rfc822date(text):
    """Date. Returns a date using the same format used in email
    headers: "Tue, 18 Aug 2009 13:00:13 +0200".
    """
    return dateutil.datestr(text, b"%a, %d %b %Y %H:%M:%S %1%2")


@templatefilter(b'short', intype=bytes)
def short(text):
    """Changeset hash. Returns the short form of a changeset hash,
    i.e. a 12 hexadecimal digit string.
    """
    return text[:12]


@templatefilter(b'shortbisect', intype=bytes)
def shortbisect(label):
    """Any text. Treats `label` as a bisection status, and
    returns a single-character representing the status (G: good, B: bad,
    S: skipped, U: untested, I: ignored). Returns single space if `text`
    is not a valid bisection status.
    """
    if label:
        return label[0:1].upper()
    return b' '


@templatefilter(b'shortdate', intype=templateutil.date)
def shortdate(text):
    """Date. Returns a date like "2006-09-18"."""
    return dateutil.shortdate(text)


@templatefilter(b'slashpath', intype=bytes)
def slashpath(path):
    """Any text. Replaces the native path separator with slash."""
    return util.pconvert(path)


@templatefilter(b'splitlines', intype=bytes)
def splitlines(text):
    """Any text. Split text into a list of lines."""
    return templateutil.hybridlist(text.splitlines(), name=b'line')


@templatefilter(b'stringescape', intype=bytes)
def stringescape(text):
    return stringutil.escapestr(text)


@templatefilter(b'stringify', intype=bytes)
def stringify(thing):
    """Any type. Turns the value into text by converting values into
    text and concatenating them.
    """
    return thing  # coerced by the intype


@templatefilter(b'stripdir', intype=bytes)
def stripdir(text):
    """Treat the text as path and strip a directory level, if
    possible. For example, "foo" and "foo/bar" becomes "foo".
    """
    dir = os.path.dirname(text)
    if dir == b"":
        return os.path.basename(text)
    else:
        return dir


@templatefilter(b'tabindent', intype=bytes)
def tabindent(text):
    """Any text. Returns the text, with every non-empty line
    except the first starting with a tab character.
    """
    return indent(text, b'\t')


@templatefilter(b'upper', intype=bytes)
def upper(text):
    """Any text. Converts the text to uppercase."""
    return encoding.upper(text)


@templatefilter(b'urlescape', intype=bytes)
def urlescape(text):
    """Any text. Escapes all "special" characters. For example,
    "foo bar" becomes "foo%20bar".
    """
    return urlreq.quote(text)


@templatefilter(b'user', intype=bytes)
def userfilter(text):
    """Any text. Returns a short representation of a user name or email
    address."""
    return stringutil.shortuser(text)


@templatefilter(b'emailuser', intype=bytes)
def emailuser(text):
    """Any text. Returns the user portion of an email address."""
    return stringutil.emailuser(text)


@templatefilter(b'utf8', intype=bytes)
def utf8(text):
    """Any text. Converts from the local character encoding to UTF-8."""
    return encoding.fromlocal(text)


@templatefilter(b'xmlescape', intype=bytes)
def xmlescape(text):
    text = (
        text.replace(b'&', b'&amp;')
        .replace(b'<', b'&lt;')
        .replace(b'>', b'&gt;')
        .replace(b'"', b'&quot;')
        .replace(b"'", b'&#39;')
    )  # &apos; invalid in HTML
    return re.sub(b'[\x00-\x08\x0B\x0C\x0E-\x1F]', b' ', text)


def websub(text, websubtable):
    """:websub: Any text. Only applies to hgweb. Applies the regular
    expression replacements defined in the websub section.
    """
    if websubtable:
        for regexp, format in websubtable:
            text = regexp.sub(format, text)
    return text


def loadfilter(ui, extname, registrarobj):
    """Load template filter from specified registrarobj"""
    for name, func in pycompat.iteritems(registrarobj._table):
        filters[name] = func


# tell hggettext to extract docstrings from these functions:
i18nfunctions = filters.values()
