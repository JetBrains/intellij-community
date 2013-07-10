# template-filters.py - common template expansion filters
#
# Copyright 2005-2008 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import cgi, re, os, time, urllib
import encoding, node, util, error
import hbisect

def addbreaks(text):
    """:addbreaks: Any text. Add an XHTML "<br />" tag before the end of
    every line except the last.
    """
    return text.replace('\n', '<br/>\n')

agescales = [("year", 3600 * 24 * 365),
             ("month", 3600 * 24 * 30),
             ("week", 3600 * 24 * 7),
             ("day", 3600 * 24),
             ("hour", 3600),
             ("minute", 60),
             ("second", 1)]

def age(date):
    """:age: Date. Returns a human-readable date/time difference between the
    given date/time and the current date/time.
    """

    def plural(t, c):
        if c == 1:
            return t
        return t + "s"
    def fmt(t, c):
        return "%d %s" % (c, plural(t, c))

    now = time.time()
    then = date[0]
    future = False
    if then > now:
        future = True
        delta = max(1, int(then - now))
        if delta > agescales[0][1] * 30:
            return 'in the distant future'
    else:
        delta = max(1, int(now - then))
        if delta > agescales[0][1] * 2:
            return util.shortdate(date)

    for t, s in agescales:
        n = delta // s
        if n >= 2 or s == 1:
            if future:
                return '%s from now' % fmt(t, n)
            return '%s ago' % fmt(t, n)

def basename(path):
    """:basename: Any text. Treats the text as a path, and returns the last
    component of the path after splitting by the path separator
    (ignoring trailing separators). For example, "foo/bar/baz" becomes
    "baz" and "foo/bar//" becomes "bar".
    """
    return os.path.basename(path)

def datefilter(text):
    """:date: Date. Returns a date in a Unix date format, including the
    timezone: "Mon Sep 04 15:13:13 2006 0700".
    """
    return util.datestr(text)

def domain(author):
    """:domain: Any text. Finds the first string that looks like an email
    address, and extracts just the domain component. Example: ``User
    <user@example.com>`` becomes ``example.com``.
    """
    f = author.find('@')
    if f == -1:
        return ''
    author = author[f + 1:]
    f = author.find('>')
    if f >= 0:
        author = author[:f]
    return author

def email(text):
    """:email: Any text. Extracts the first string that looks like an email
    address. Example: ``User <user@example.com>`` becomes
    ``user@example.com``.
    """
    return util.email(text)

def escape(text):
    """:escape: Any text. Replaces the special XML/XHTML characters "&", "<"
    and ">" with XML entities, and filters out NUL characters.
    """
    return cgi.escape(text.replace('\0', ''), True)

para_re = None
space_re = None

def fill(text, width):
    '''fill many paragraphs.'''
    global para_re, space_re
    if para_re is None:
        para_re = re.compile('(\n\n|\n\\s*[-*]\\s*)', re.M)
        space_re = re.compile(r'  +')

    def findparas():
        start = 0
        while True:
            m = para_re.search(text, start)
            if not m:
                uctext = unicode(text[start:], encoding.encoding)
                w = len(uctext)
                while 0 < w and uctext[w - 1].isspace():
                    w -= 1
                yield (uctext[:w].encode(encoding.encoding),
                       uctext[w:].encode(encoding.encoding))
                break
            yield text[start:m.start(0)], m.group(1)
            start = m.end(1)

    return "".join([space_re.sub(' ', util.wrap(para, width=width)) + rest
                    for para, rest in findparas()])

def fill68(text):
    """:fill68: Any text. Wraps the text to fit in 68 columns."""
    return fill(text, 68)

def fill76(text):
    """:fill76: Any text. Wraps the text to fit in 76 columns."""
    return fill(text, 76)

def firstline(text):
    """:firstline: Any text. Returns the first line of text."""
    try:
        return text.splitlines(True)[0].rstrip('\r\n')
    except IndexError:
        return ''

def hexfilter(text):
    """:hex: Any text. Convert a binary Mercurial node identifier into
    its long hexadecimal representation.
    """
    return node.hex(text)

def hgdate(text):
    """:hgdate: Date. Returns the date as a pair of numbers: "1157407993
    25200" (Unix timestamp, timezone offset).
    """
    return "%d %d" % text

def isodate(text):
    """:isodate: Date. Returns the date in ISO 8601 format: "2009-08-18 13:00
    +0200".
    """
    return util.datestr(text, '%Y-%m-%d %H:%M %1%2')

def isodatesec(text):
    """:isodatesec: Date. Returns the date in ISO 8601 format, including
    seconds: "2009-08-18 13:00:13 +0200". See also the rfc3339date
    filter.
    """
    return util.datestr(text, '%Y-%m-%d %H:%M:%S %1%2')

def indent(text, prefix):
    '''indent each non-empty line of text after first with prefix.'''
    lines = text.splitlines()
    num_lines = len(lines)
    endswithnewline = text[-1:] == '\n'
    def indenter():
        for i in xrange(num_lines):
            l = lines[i]
            if i and l.strip():
                yield prefix
            yield l
            if i < num_lines - 1 or endswithnewline:
                yield '\n'
    return "".join(indenter())

def json(obj):
    if obj is None or obj is False or obj is True:
        return {None: 'null', False: 'false', True: 'true'}[obj]
    elif isinstance(obj, int) or isinstance(obj, float):
        return str(obj)
    elif isinstance(obj, str):
        u = unicode(obj, encoding.encoding, 'replace')
        return '"%s"' % jsonescape(u)
    elif isinstance(obj, unicode):
        return '"%s"' % jsonescape(obj)
    elif util.safehasattr(obj, 'keys'):
        out = []
        for k, v in obj.iteritems():
            s = '%s: %s' % (json(k), json(v))
            out.append(s)
        return '{' + ', '.join(out) + '}'
    elif util.safehasattr(obj, '__iter__'):
        out = []
        for i in obj:
            out.append(json(i))
        return '[' + ', '.join(out) + ']'
    else:
        raise TypeError('cannot encode type %s' % obj.__class__.__name__)

def _uescape(c):
    if ord(c) < 0x80:
        return c
    else:
        return '\\u%04x' % ord(c)

_escapes = [
    ('\\', '\\\\'), ('"', '\\"'), ('\t', '\\t'), ('\n', '\\n'),
    ('\r', '\\r'), ('\f', '\\f'), ('\b', '\\b'),
]

def jsonescape(s):
    for k, v in _escapes:
        s = s.replace(k, v)
    return ''.join(_uescape(c) for c in s)

def localdate(text):
    """:localdate: Date. Converts a date to local date."""
    return (util.parsedate(text)[0], util.makedate()[1])

def nonempty(str):
    """:nonempty: Any text. Returns '(none)' if the string is empty."""
    return str or "(none)"

def obfuscate(text):
    """:obfuscate: Any text. Returns the input text rendered as a sequence of
    XML entities.
    """
    text = unicode(text, encoding.encoding, 'replace')
    return ''.join(['&#%d;' % ord(c) for c in text])

def permissions(flags):
    if "l" in flags:
        return "lrwxrwxrwx"
    if "x" in flags:
        return "-rwxr-xr-x"
    return "-rw-r--r--"

def person(author):
    """:person: Any text. Returns the name before an email address,
    interpreting it as per RFC 5322.

    >>> person('foo@bar')
    'foo'
    >>> person('Foo Bar <foo@bar>')
    'Foo Bar'
    >>> person('"Foo Bar" <foo@bar>')
    'Foo Bar'
    >>> person('"Foo \"buz\" Bar" <foo@bar>')
    'Foo "buz" Bar'
    >>> # The following are invalid, but do exist in real-life
    ...
    >>> person('Foo "buz" Bar <foo@bar>')
    'Foo "buz" Bar'
    >>> person('"Foo Bar <foo@bar>')
    'Foo Bar'
    """
    if '@' not in author:
        return author
    f = author.find('<')
    if f != -1:
        return author[:f].strip(' "').replace('\\"', '"')
    f = author.find('@')
    return author[:f].replace('.', ' ')

def rfc3339date(text):
    """:rfc3339date: Date. Returns a date using the Internet date format
    specified in RFC 3339: "2009-08-18T13:00:13+02:00".
    """
    return util.datestr(text, "%Y-%m-%dT%H:%M:%S%1:%2")

def rfc822date(text):
    """:rfc822date: Date. Returns a date using the same format used in email
    headers: "Tue, 18 Aug 2009 13:00:13 +0200".
    """
    return util.datestr(text, "%a, %d %b %Y %H:%M:%S %1%2")

def short(text):
    """:short: Changeset hash. Returns the short form of a changeset hash,
    i.e. a 12 hexadecimal digit string.
    """
    return text[:12]

def shortbisect(text):
    """:shortbisect: Any text. Treats `text` as a bisection status, and
    returns a single-character representing the status (G: good, B: bad,
    S: skipped, U: untested, I: ignored). Returns single space if `text`
    is not a valid bisection status.
    """
    return hbisect.shortlabel(text) or ' '

def shortdate(text):
    """:shortdate: Date. Returns a date like "2006-09-18"."""
    return util.shortdate(text)

def stringescape(text):
    return text.encode('string_escape')

def stringify(thing):
    """:stringify: Any type. Turns the value into text by converting values into
    text and concatenating them.
    """
    if util.safehasattr(thing, '__iter__') and not isinstance(thing, str):
        return "".join([stringify(t) for t in thing if t is not None])
    return str(thing)

def strip(text):
    """:strip: Any text. Strips all leading and trailing whitespace."""
    return text.strip()

def stripdir(text):
    """:stripdir: Treat the text as path and strip a directory level, if
    possible. For example, "foo" and "foo/bar" becomes "foo".
    """
    dir = os.path.dirname(text)
    if dir == "":
        return os.path.basename(text)
    else:
        return dir

def tabindent(text):
    """:tabindent: Any text. Returns the text, with every line except the
    first starting with a tab character.
    """
    return indent(text, '\t')

def urlescape(text):
    """:urlescape: Any text. Escapes all "special" characters. For example,
    "foo bar" becomes "foo%20bar".
    """
    return urllib.quote(text)

def userfilter(text):
    """:user: Any text. Returns a short representation of a user name or email
    address."""
    return util.shortuser(text)

def emailuser(text):
    """:emailuser: Any text. Returns the user portion of an email address."""
    return util.emailuser(text)

def xmlescape(text):
    text = (text
            .replace('&', '&amp;')
            .replace('<', '&lt;')
            .replace('>', '&gt;')
            .replace('"', '&quot;')
            .replace("'", '&#39;')) # &apos; invalid in HTML
    return re.sub('[\x00-\x08\x0B\x0C\x0E-\x1F]', ' ', text)

filters = {
    "addbreaks": addbreaks,
    "age": age,
    "basename": basename,
    "date": datefilter,
    "domain": domain,
    "email": email,
    "escape": escape,
    "fill68": fill68,
    "fill76": fill76,
    "firstline": firstline,
    "hex": hexfilter,
    "hgdate": hgdate,
    "isodate": isodate,
    "isodatesec": isodatesec,
    "json": json,
    "jsonescape": jsonescape,
    "localdate": localdate,
    "nonempty": nonempty,
    "obfuscate": obfuscate,
    "permissions": permissions,
    "person": person,
    "rfc3339date": rfc3339date,
    "rfc822date": rfc822date,
    "short": short,
    "shortbisect": shortbisect,
    "shortdate": shortdate,
    "stringescape": stringescape,
    "stringify": stringify,
    "strip": strip,
    "stripdir": stripdir,
    "tabindent": tabindent,
    "urlescape": urlescape,
    "user": userfilter,
    "emailuser": emailuser,
    "xmlescape": xmlescape,
}

def websub(text, websubtable):
    """:websub: Any text. Only applies to hgweb. Applies the regular
    expression replacements defined in the websub section.
    """
    if websubtable:
        for regexp, format in websubtable:
            text = regexp.sub(format, text)
    return text

def fillfunc(context, mapping, args):
    if not (1 <= len(args) <= 2):
        raise error.ParseError(_("fill expects one or two arguments"))

    text = stringify(args[0][0](context, mapping, args[0][1]))
    width = 76
    if len(args) == 2:
        try:
            width = int(stringify(args[1][0](context, mapping, args[1][1])))
        except ValueError:
            raise error.ParseError(_("fill expects an integer width"))

    return fill(text, width)

def datefunc(context, mapping, args):
    if not (1 <= len(args) <= 2):
        raise error.ParseError(_("date expects one or two arguments"))

    date = args[0][0](context, mapping, args[0][1])
    if len(args) == 2:
        fmt = stringify(args[1][0](context, mapping, args[1][1]))
        return util.datestr(date, fmt)
    return util.datestr(date)

funcs = {
    "fill": fillfunc,
    "date": datefunc,
}

# tell hggettext to extract docstrings from these functions:
i18nfunctions = filters.values()
