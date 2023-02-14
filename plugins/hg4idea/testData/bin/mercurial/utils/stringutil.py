# stringutil.py - utility for generic string formatting, parsing, etc.
#
#  Copyright 2005 K. Thananchayan <thananck@yahoo.com>
#  Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#  Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import ast
import codecs
import re as remod
import textwrap
import types

from ..i18n import _
from ..thirdparty import attr

from .. import (
    encoding,
    error,
    pycompat,
)

# regex special chars pulled from https://bugs.python.org/issue29995
# which was part of Python 3.7.
_respecial = pycompat.bytestr(b'()[]{}?*+-|^$\\.&~# \t\n\r\v\f')
_regexescapemap = {ord(i): (b'\\' + i).decode('latin1') for i in _respecial}
regexbytesescapemap = {i: (b'\\' + i) for i in _respecial}


def reescape(pat):
    """Drop-in replacement for re.escape."""
    # NOTE: it is intentional that this works on unicodes and not
    # bytes, as it's only possible to do the escaping with
    # unicode.translate, not bytes.translate. Sigh.
    wantuni = True
    if isinstance(pat, bytes):
        wantuni = False
        pat = pat.decode('latin1')
    pat = pat.translate(_regexescapemap)
    if wantuni:
        return pat
    return pat.encode('latin1')


def pprint(o, bprefix=False, indent=0, level=0):
    """Pretty print an object."""
    return b''.join(pprintgen(o, bprefix=bprefix, indent=indent, level=level))


def pprintgen(o, bprefix=False, indent=0, level=0):
    """Pretty print an object to a generator of atoms.

    ``bprefix`` is a flag influencing whether bytestrings are preferred with
    a ``b''`` prefix.

    ``indent`` controls whether collections and nested data structures
    span multiple lines via the indentation amount in spaces. By default,
    no newlines are emitted.

    ``level`` specifies the initial indent level. Used if ``indent > 0``.
    """

    if isinstance(o, bytes):
        if bprefix:
            yield b"b'%s'" % escapestr(o)
        else:
            yield b"'%s'" % escapestr(o)
    elif isinstance(o, bytearray):
        # codecs.escape_encode() can't handle bytearray, so escapestr fails
        # without coercion.
        yield b"bytearray['%s']" % escapestr(bytes(o))
    elif isinstance(o, list):
        if not o:
            yield b'[]'
            return

        yield b'['

        if indent:
            level += 1
            yield b'\n'
            yield b' ' * (level * indent)

        for i, a in enumerate(o):
            for chunk in pprintgen(
                a, bprefix=bprefix, indent=indent, level=level
            ):
                yield chunk

            if i + 1 < len(o):
                if indent:
                    yield b',\n'
                    yield b' ' * (level * indent)
                else:
                    yield b', '

        if indent:
            level -= 1
            yield b'\n'
            yield b' ' * (level * indent)

        yield b']'
    elif isinstance(o, dict):
        if not o:
            yield b'{}'
            return

        yield b'{'

        if indent:
            level += 1
            yield b'\n'
            yield b' ' * (level * indent)

        for i, (k, v) in enumerate(sorted(o.items())):
            for chunk in pprintgen(
                k, bprefix=bprefix, indent=indent, level=level
            ):
                yield chunk

            yield b': '

            for chunk in pprintgen(
                v, bprefix=bprefix, indent=indent, level=level
            ):
                yield chunk

            if i + 1 < len(o):
                if indent:
                    yield b',\n'
                    yield b' ' * (level * indent)
                else:
                    yield b', '

        if indent:
            level -= 1
            yield b'\n'
            yield b' ' * (level * indent)

        yield b'}'
    elif isinstance(o, set):
        if not o:
            yield b'set([])'
            return

        yield b'set(['

        if indent:
            level += 1
            yield b'\n'
            yield b' ' * (level * indent)

        for i, k in enumerate(sorted(o)):
            for chunk in pprintgen(
                k, bprefix=bprefix, indent=indent, level=level
            ):
                yield chunk

            if i + 1 < len(o):
                if indent:
                    yield b',\n'
                    yield b' ' * (level * indent)
                else:
                    yield b', '

        if indent:
            level -= 1
            yield b'\n'
            yield b' ' * (level * indent)

        yield b'])'
    elif isinstance(o, tuple):
        if not o:
            yield b'()'
            return

        yield b'('

        if indent:
            level += 1
            yield b'\n'
            yield b' ' * (level * indent)

        for i, a in enumerate(o):
            for chunk in pprintgen(
                a, bprefix=bprefix, indent=indent, level=level
            ):
                yield chunk

            if i + 1 < len(o):
                if indent:
                    yield b',\n'
                    yield b' ' * (level * indent)
                else:
                    yield b', '

        if indent:
            level -= 1
            yield b'\n'
            yield b' ' * (level * indent)

        yield b')'
    elif isinstance(o, types.GeneratorType):
        # Special case of empty generator.
        try:
            nextitem = next(o)
        except StopIteration:
            yield b'gen[]'
            return

        yield b'gen['

        if indent:
            level += 1
            yield b'\n'
            yield b' ' * (level * indent)

        last = False

        while not last:
            current = nextitem

            try:
                nextitem = next(o)
            except StopIteration:
                last = True

            for chunk in pprintgen(
                current, bprefix=bprefix, indent=indent, level=level
            ):
                yield chunk

            if not last:
                if indent:
                    yield b',\n'
                    yield b' ' * (level * indent)
                else:
                    yield b', '

        if indent:
            level -= 1
            yield b'\n'
            yield b' ' * (level * indent)

        yield b']'
    else:
        yield pycompat.byterepr(o)


def prettyrepr(o):
    """Pretty print a representation of a possibly-nested object"""
    lines = []
    rs = pycompat.byterepr(o)
    p0 = p1 = 0
    while p0 < len(rs):
        # '... field=<type ... field=<type ...'
        #      ~~~~~~~~~~~~~~~~
        #      p0    p1        q0    q1
        q0 = -1
        q1 = rs.find(b'<', p1 + 1)
        if q1 < 0:
            q1 = len(rs)
        elif q1 > p1 + 1 and rs.startswith(b'=', q1 - 1):
            # backtrack for ' field=<'
            q0 = rs.rfind(b' ', p1 + 1, q1 - 1)
        if q0 < 0:
            q0 = q1
        else:
            q0 += 1  # skip ' '
        l = rs.count(b'<', 0, p0) - rs.count(b'>', 0, p0)
        assert l >= 0
        lines.append((l, rs[p0:q0].rstrip()))
        p0, p1 = q0, q1
    return b'\n'.join(b'  ' * l + s for l, s in lines)


def buildrepr(r):
    """Format an optional printable representation from unexpanded bits

    ========  =================================
    type(r)   example
    ========  =================================
    tuple     ('<not %r>', other)
    bytes     '<branch closed>'
    callable  lambda: '<branch %r>' % sorted(b)
    object    other
    ========  =================================
    """
    if r is None:
        return b''
    elif isinstance(r, tuple):
        return r[0] % pycompat.rapply(pycompat.maybebytestr, r[1:])
    elif isinstance(r, bytes):
        return r
    elif callable(r):
        return r()
    else:
        return pprint(r)


def binary(s):
    """return true if a string is binary data"""
    return bool(s and b'\0' in s)


def _splitpattern(pattern):
    if pattern.startswith(b're:'):
        return b're', pattern[3:]
    elif pattern.startswith(b'literal:'):
        return b'literal', pattern[8:]
    return b'literal', pattern


def stringmatcher(pattern, casesensitive=True):
    """
    accepts a string, possibly starting with 're:' or 'literal:' prefix.
    returns the matcher name, pattern, and matcher function.
    missing or unknown prefixes are treated as literal matches.

    helper for tests:
    >>> def test(pattern, *tests):
    ...     kind, pattern, matcher = stringmatcher(pattern)
    ...     return (kind, pattern, [bool(matcher(t)) for t in tests])
    >>> def itest(pattern, *tests):
    ...     kind, pattern, matcher = stringmatcher(pattern, casesensitive=False)
    ...     return (kind, pattern, [bool(matcher(t)) for t in tests])

    exact matching (no prefix):
    >>> test(b'abcdefg', b'abc', b'def', b'abcdefg')
    ('literal', 'abcdefg', [False, False, True])

    regex matching ('re:' prefix)
    >>> test(b're:a.+b', b'nomatch', b'fooadef', b'fooadefbar')
    ('re', 'a.+b', [False, False, True])

    force exact matches ('literal:' prefix)
    >>> test(b'literal:re:foobar', b'foobar', b're:foobar')
    ('literal', 're:foobar', [False, True])

    unknown prefixes are ignored and treated as literals
    >>> test(b'foo:bar', b'foo', b'bar', b'foo:bar')
    ('literal', 'foo:bar', [False, False, True])

    case insensitive regex matches
    >>> itest(b're:A.+b', b'nomatch', b'fooadef', b'fooadefBar')
    ('re', 'A.+b', [False, False, True])

    case insensitive literal matches
    >>> itest(b'ABCDEFG', b'abc', b'def', b'abcdefg')
    ('literal', 'ABCDEFG', [False, False, True])
    """
    kind, pattern = _splitpattern(pattern)
    if kind == b're':
        try:
            flags = 0
            if not casesensitive:
                flags = remod.I
            regex = remod.compile(pattern, flags)
        except remod.error as e:
            raise error.ParseError(
                _(b'invalid regular expression: %s') % forcebytestr(e)
            )
        return kind, pattern, regex.search
    elif kind == b'literal':
        if casesensitive:
            match = pattern.__eq__
        else:
            ipat = encoding.lower(pattern)
            match = lambda s: ipat == encoding.lower(s)
        return kind, pattern, match

    raise error.ProgrammingError(b'unhandled pattern kind: %s' % kind)


def substringregexp(pattern, flags=0):
    """Build a regexp object from a string pattern possibly starting with
    're:' or 'literal:' prefix.

    helper for tests:
    >>> def test(pattern, *tests):
    ...     regexp = substringregexp(pattern)
    ...     return [bool(regexp.search(t)) for t in tests]
    >>> def itest(pattern, *tests):
    ...     regexp = substringregexp(pattern, remod.I)
    ...     return [bool(regexp.search(t)) for t in tests]

    substring matching (no prefix):
    >>> test(b'bcde', b'abc', b'def', b'abcdefg')
    [False, False, True]

    substring pattern should be escaped:
    >>> substringregexp(b'.bc').pattern
    '\\\\.bc'
    >>> test(b'.bc', b'abc', b'def', b'abcdefg')
    [False, False, False]

    regex matching ('re:' prefix)
    >>> test(b're:a.+b', b'nomatch', b'fooadef', b'fooadefbar')
    [False, False, True]

    force substring matches ('literal:' prefix)
    >>> test(b'literal:re:foobar', b'foobar', b're:foobar')
    [False, True]

    case insensitive literal matches
    >>> itest(b'BCDE', b'abc', b'def', b'abcdefg')
    [False, False, True]

    case insensitive regex matches
    >>> itest(b're:A.+b', b'nomatch', b'fooadef', b'fooadefBar')
    [False, False, True]
    """
    kind, pattern = _splitpattern(pattern)
    if kind == b're':
        try:
            return remod.compile(pattern, flags)
        except remod.error as e:
            raise error.ParseError(
                _(b'invalid regular expression: %s') % forcebytestr(e)
            )
    elif kind == b'literal':
        return remod.compile(remod.escape(pattern), flags)

    raise error.ProgrammingError(b'unhandled pattern kind: %s' % kind)


def shortuser(user):
    """Return a short representation of a user name or email address."""
    f = user.find(b'@')
    if f >= 0:
        user = user[:f]
    f = user.find(b'<')
    if f >= 0:
        user = user[f + 1 :]
    f = user.find(b' ')
    if f >= 0:
        user = user[:f]
    f = user.find(b'.')
    if f >= 0:
        user = user[:f]
    return user


def emailuser(user):
    """Return the user portion of an email address."""
    f = user.find(b'@')
    if f >= 0:
        user = user[:f]
    f = user.find(b'<')
    if f >= 0:
        user = user[f + 1 :]
    return user


def email(author):
    '''get email of author.'''
    r = author.find(b'>')
    if r == -1:
        r = None
    return author[author.find(b'<') + 1 : r]


def person(author):
    """Returns the name before an email address,
    interpreting it as per RFC 5322

    >>> person(b'foo@bar')
    'foo'
    >>> person(b'Foo Bar <foo@bar>')
    'Foo Bar'
    >>> person(b'"Foo Bar" <foo@bar>')
    'Foo Bar'
    >>> person(b'"Foo \"buz\" Bar" <foo@bar>')
    'Foo "buz" Bar'
    >>> # The following are invalid, but do exist in real-life
    ...
    >>> person(b'Foo "buz" Bar <foo@bar>')
    'Foo "buz" Bar'
    >>> person(b'"Foo Bar <foo@bar>')
    'Foo Bar'
    """
    if b'@' not in author:
        return author
    f = author.find(b'<')
    if f != -1:
        return author[:f].strip(b' "').replace(b'\\"', b'"')
    f = author.find(b'@')
    return author[:f].replace(b'.', b' ')


@attr.s(hash=True)
class mailmapping(object):
    """Represents a username/email key or value in
    a mailmap file"""

    email = attr.ib()
    name = attr.ib(default=None)


def _ismailmaplineinvalid(names, emails):
    """Returns True if the parsed names and emails
    in a mailmap entry are invalid.

    >>> # No names or emails fails
    >>> names, emails = [], []
    >>> _ismailmaplineinvalid(names, emails)
    True
    >>> # Only one email fails
    >>> emails = [b'email@email.com']
    >>> _ismailmaplineinvalid(names, emails)
    True
    >>> # One email and one name passes
    >>> names = [b'Test Name']
    >>> _ismailmaplineinvalid(names, emails)
    False
    >>> # No names but two emails passes
    >>> names = []
    >>> emails = [b'proper@email.com', b'commit@email.com']
    >>> _ismailmaplineinvalid(names, emails)
    False
    """
    return not emails or not names and len(emails) < 2


def parsemailmap(mailmapcontent):
    """Parses data in the .mailmap format

    >>> mmdata = b"\\n".join([
    ... b'# Comment',
    ... b'Name <commit1@email.xx>',
    ... b'<name@email.xx> <commit2@email.xx>',
    ... b'Name <proper@email.xx> <commit3@email.xx>',
    ... b'Name <proper@email.xx> Commit <commit4@email.xx>',
    ... ])
    >>> mm = parsemailmap(mmdata)
    >>> for key in sorted(mm.keys()):
    ...     print(key)
    mailmapping(email='commit1@email.xx', name=None)
    mailmapping(email='commit2@email.xx', name=None)
    mailmapping(email='commit3@email.xx', name=None)
    mailmapping(email='commit4@email.xx', name='Commit')
    >>> for val in sorted(mm.values()):
    ...     print(val)
    mailmapping(email='commit1@email.xx', name='Name')
    mailmapping(email='name@email.xx', name=None)
    mailmapping(email='proper@email.xx', name='Name')
    mailmapping(email='proper@email.xx', name='Name')
    """
    mailmap = {}

    if mailmapcontent is None:
        return mailmap

    for line in mailmapcontent.splitlines():

        # Don't bother checking the line if it is a comment or
        # is an improperly formed author field
        if line.lstrip().startswith(b'#'):
            continue

        # names, emails hold the parsed emails and names for each line
        # name_builder holds the words in a persons name
        names, emails = [], []
        namebuilder = []

        for element in line.split():
            if element.startswith(b'#'):
                # If we reach a comment in the mailmap file, move on
                break

            elif element.startswith(b'<') and element.endswith(b'>'):
                # We have found an email.
                # Parse it, and finalize any names from earlier
                emails.append(element[1:-1])  # Slice off the "<>"

                if namebuilder:
                    names.append(b' '.join(namebuilder))
                    namebuilder = []

                # Break if we have found a second email, any other
                # data does not fit the spec for .mailmap
                if len(emails) > 1:
                    break

            else:
                # We have found another word in the committers name
                namebuilder.append(element)

        # Check to see if we have parsed the line into a valid form
        # We require at least one email, and either at least one
        # name or a second email
        if _ismailmaplineinvalid(names, emails):
            continue

        mailmapkey = mailmapping(
            email=emails[-1],
            name=names[-1] if len(names) == 2 else None,
        )

        mailmap[mailmapkey] = mailmapping(
            email=emails[0],
            name=names[0] if names else None,
        )

    return mailmap


def mapname(mailmap, author):
    """Returns the author field according to the mailmap cache, or
    the original author field.

    >>> mmdata = b"\\n".join([
    ...     b'# Comment',
    ...     b'Name <commit1@email.xx>',
    ...     b'<name@email.xx> <commit2@email.xx>',
    ...     b'Name <proper@email.xx> <commit3@email.xx>',
    ...     b'Name <proper@email.xx> Commit <commit4@email.xx>',
    ... ])
    >>> m = parsemailmap(mmdata)
    >>> mapname(m, b'Commit <commit1@email.xx>')
    'Name <commit1@email.xx>'
    >>> mapname(m, b'Name <commit2@email.xx>')
    'Name <name@email.xx>'
    >>> mapname(m, b'Commit <commit3@email.xx>')
    'Name <proper@email.xx>'
    >>> mapname(m, b'Commit <commit4@email.xx>')
    'Name <proper@email.xx>'
    >>> mapname(m, b'Unknown Name <unknown@email.com>')
    'Unknown Name <unknown@email.com>'
    """
    # If the author field coming in isn't in the correct format,
    # or the mailmap is empty just return the original author field
    if not isauthorwellformed(author) or not mailmap:
        return author

    # Turn the user name into a mailmapping
    commit = mailmapping(name=person(author), email=email(author))

    try:
        # Try and use both the commit email and name as the key
        proper = mailmap[commit]

    except KeyError:
        # If the lookup fails, use just the email as the key instead
        # We call this commit2 as not to erase original commit fields
        commit2 = mailmapping(email=commit.email)
        proper = mailmap.get(commit2, mailmapping(None, None))

    # Return the author field with proper values filled in
    return b'%s <%s>' % (
        proper.name if proper.name else commit.name,
        proper.email if proper.email else commit.email,
    )


_correctauthorformat = remod.compile(br'^[^<]+\s<[^<>]+@[^<>]+>$')


def isauthorwellformed(author):
    """Return True if the author field is well formed
    (ie "Contributor Name <contrib@email.dom>")

    >>> isauthorwellformed(b'Good Author <good@author.com>')
    True
    >>> isauthorwellformed(b'Author <good@author.com>')
    True
    >>> isauthorwellformed(b'Bad Author')
    False
    >>> isauthorwellformed(b'Bad Author <author@author.com')
    False
    >>> isauthorwellformed(b'Bad Author author@author.com')
    False
    >>> isauthorwellformed(b'<author@author.com>')
    False
    >>> isauthorwellformed(b'Bad Author <author>')
    False
    """
    return _correctauthorformat.match(author) is not None


def ellipsis(text, maxlength=400):
    """Trim string to at most maxlength (default: 400) columns in display."""
    return encoding.trim(text, maxlength, ellipsis=b'...')


def escapestr(s):
    if isinstance(s, memoryview):
        s = bytes(s)
    # call underlying function of s.encode('string_escape') directly for
    # Python 3 compatibility
    return codecs.escape_encode(s)[0]


def unescapestr(s):
    return codecs.escape_decode(s)[0]


def forcebytestr(obj):
    """Portably format an arbitrary object (e.g. exception) into a byte
    string."""
    try:
        return pycompat.bytestr(obj)
    except UnicodeEncodeError:
        # non-ascii string, may be lossy
        return pycompat.bytestr(encoding.strtolocal(str(obj)))


def uirepr(s):
    # Avoid double backslash in Windows path repr()
    return pycompat.byterepr(pycompat.bytestr(s)).replace(b'\\\\', b'\\')


# delay import of textwrap
def _MBTextWrapper(**kwargs):
    class tw(textwrap.TextWrapper):
        """
        Extend TextWrapper for width-awareness.

        Neither number of 'bytes' in any encoding nor 'characters' is
        appropriate to calculate terminal columns for specified string.

        Original TextWrapper implementation uses built-in 'len()' directly,
        so overriding is needed to use width information of each characters.

        In addition, characters classified into 'ambiguous' width are
        treated as wide in East Asian area, but as narrow in other.

        This requires use decision to determine width of such characters.
        """

        def _cutdown(self, ucstr, space_left):
            l = 0
            colwidth = encoding.ucolwidth
            for i in pycompat.xrange(len(ucstr)):
                l += colwidth(ucstr[i])
                if space_left < l:
                    return (ucstr[:i], ucstr[i:])
            return ucstr, b''

        # overriding of base class
        def _handle_long_word(self, reversed_chunks, cur_line, cur_len, width):
            space_left = max(width - cur_len, 1)

            if self.break_long_words:
                cut, res = self._cutdown(reversed_chunks[-1], space_left)
                cur_line.append(cut)
                reversed_chunks[-1] = res
            elif not cur_line:
                cur_line.append(reversed_chunks.pop())

        # this overriding code is imported from TextWrapper of Python 2.6
        # to calculate columns of string by 'encoding.ucolwidth()'
        def _wrap_chunks(self, chunks):
            colwidth = encoding.ucolwidth

            lines = []
            if self.width <= 0:
                raise ValueError(b"invalid width %r (must be > 0)" % self.width)

            # Arrange in reverse order so items can be efficiently popped
            # from a stack of chucks.
            chunks.reverse()

            while chunks:

                # Start the list of chunks that will make up the current line.
                # cur_len is just the length of all the chunks in cur_line.
                cur_line = []
                cur_len = 0

                # Figure out which static string will prefix this line.
                if lines:
                    indent = self.subsequent_indent
                else:
                    indent = self.initial_indent

                # Maximum width for this line.
                width = self.width - len(indent)

                # First chunk on line is whitespace -- drop it, unless this
                # is the very beginning of the text (i.e. no lines started yet).
                if self.drop_whitespace and chunks[-1].strip() == '' and lines:
                    del chunks[-1]

                while chunks:
                    l = colwidth(chunks[-1])

                    # Can at least squeeze this chunk onto the current line.
                    if cur_len + l <= width:
                        cur_line.append(chunks.pop())
                        cur_len += l

                    # Nope, this line is full.
                    else:
                        break

                # The current line is full, and the next chunk is too big to
                # fit on *any* line (not just this one).
                if chunks and colwidth(chunks[-1]) > width:
                    self._handle_long_word(chunks, cur_line, cur_len, width)

                # If the last chunk on this line is all whitespace, drop it.
                if (
                    self.drop_whitespace
                    and cur_line
                    and cur_line[-1].strip() == r''
                ):
                    del cur_line[-1]

                # Convert current line back to a string and store it in list
                # of all lines (return value).
                if cur_line:
                    lines.append(indent + ''.join(cur_line))

            return lines

    global _MBTextWrapper
    _MBTextWrapper = tw
    return tw(**kwargs)


def wrap(line, width, initindent=b'', hangindent=b''):
    maxindent = max(len(hangindent), len(initindent))
    if width <= maxindent:
        # adjust for weird terminal size
        width = max(78, maxindent + 1)
    line = line.decode(
        pycompat.sysstr(encoding.encoding),
        pycompat.sysstr(encoding.encodingmode),
    )
    initindent = initindent.decode(
        pycompat.sysstr(encoding.encoding),
        pycompat.sysstr(encoding.encodingmode),
    )
    hangindent = hangindent.decode(
        pycompat.sysstr(encoding.encoding),
        pycompat.sysstr(encoding.encodingmode),
    )
    wrapper = _MBTextWrapper(
        width=width, initial_indent=initindent, subsequent_indent=hangindent
    )
    return wrapper.fill(line).encode(pycompat.sysstr(encoding.encoding))


_booleans = {
    b'1': True,
    b'yes': True,
    b'true': True,
    b'on': True,
    b'always': True,
    b'0': False,
    b'no': False,
    b'false': False,
    b'off': False,
    b'never': False,
}


def parsebool(s):
    """Parse s into a boolean.

    If s is not a valid boolean, returns None.
    """
    return _booleans.get(s.lower(), None)


def parselist(value):
    """parse a configuration value as a list of comma/space separated strings

    >>> parselist(b'this,is "a small" ,test')
    ['this', 'is', 'a small', 'test']
    """

    def _parse_plain(parts, s, offset):
        whitespace = False
        while offset < len(s) and (
            s[offset : offset + 1].isspace() or s[offset : offset + 1] == b','
        ):
            whitespace = True
            offset += 1
        if offset >= len(s):
            return None, parts, offset
        if whitespace:
            parts.append(b'')
        if s[offset : offset + 1] == b'"' and not parts[-1]:
            return _parse_quote, parts, offset + 1
        elif s[offset : offset + 1] == b'"' and parts[-1][-1:] == b'\\':
            parts[-1] = parts[-1][:-1] + s[offset : offset + 1]
            return _parse_plain, parts, offset + 1
        parts[-1] += s[offset : offset + 1]
        return _parse_plain, parts, offset + 1

    def _parse_quote(parts, s, offset):
        if offset < len(s) and s[offset : offset + 1] == b'"':  # ""
            parts.append(b'')
            offset += 1
            while offset < len(s) and (
                s[offset : offset + 1].isspace()
                or s[offset : offset + 1] == b','
            ):
                offset += 1
            return _parse_plain, parts, offset

        while offset < len(s) and s[offset : offset + 1] != b'"':
            if (
                s[offset : offset + 1] == b'\\'
                and offset + 1 < len(s)
                and s[offset + 1 : offset + 2] == b'"'
            ):
                offset += 1
                parts[-1] += b'"'
            else:
                parts[-1] += s[offset : offset + 1]
            offset += 1

        if offset >= len(s):
            real_parts = _configlist(parts[-1])
            if not real_parts:
                parts[-1] = b'"'
            else:
                real_parts[0] = b'"' + real_parts[0]
                parts = parts[:-1]
                parts.extend(real_parts)
            return None, parts, offset

        offset += 1
        while offset < len(s) and s[offset : offset + 1] in [b' ', b',']:
            offset += 1

        if offset < len(s):
            if offset + 1 == len(s) and s[offset : offset + 1] == b'"':
                parts[-1] += b'"'
                offset += 1
            else:
                parts.append(b'')
        else:
            return None, parts, offset

        return _parse_plain, parts, offset

    def _configlist(s):
        s = s.rstrip(b' ,')
        if not s:
            return []
        parser, parts, offset = _parse_plain, [b''], 0
        while parser:
            parser, parts, offset = parser(parts, s, offset)
        return parts

    if value is not None and isinstance(value, bytes):
        result = _configlist(value.lstrip(b' ,\n'))
    else:
        result = value
    return result or []


def evalpythonliteral(s):
    """Evaluate a string containing a Python literal expression"""
    # We could backport our tokenizer hack to rewrite '' to u'' if we want
    if pycompat.ispy3:
        return ast.literal_eval(s.decode('latin1'))
    return ast.literal_eval(s)
