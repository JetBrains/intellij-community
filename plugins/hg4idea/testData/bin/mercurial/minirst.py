# minirst.py - minimal reStructuredText parser
#
# Copyright 2009, 2010 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""simplified reStructuredText parser.

This parser knows just enough about reStructuredText to parse the
Mercurial docstrings.

It cheats in a major way: nested blocks are not really nested. They
are just indented blocks that look like they are nested. This relies
on the user to keep the right indentation for the blocks.

Remember to update https://mercurial-scm.org/wiki/HelpStyleGuide
when adding support for new constructs.
"""


import re

from .i18n import _
from . import (
    encoding,
    pycompat,
    url,
)
from .utils import stringutil


def section(s):
    return b"%s\n%s\n\n" % (s, b"\"" * encoding.colwidth(s))


def subsection(s):
    return b"%s\n%s\n\n" % (s, b'=' * encoding.colwidth(s))


def subsubsection(s):
    return b"%s\n%s\n\n" % (s, b"-" * encoding.colwidth(s))


def subsubsubsection(s):
    return b"%s\n%s\n\n" % (s, b"." * encoding.colwidth(s))


def subsubsubsubsection(s):
    return b"%s\n%s\n\n" % (s, b"'" * encoding.colwidth(s))


def replace(text, substs):
    """
    Apply a list of (find, replace) pairs to a text.

    >>> replace(b"foo bar", [(b'f', b'F'), (b'b', b'B')])
    'Foo Bar'
    >>> encoding.encoding = b'latin1'
    >>> replace(b'\\x81\\\\', [(b'\\\\', b'/')])
    '\\x81/'
    >>> encoding.encoding = b'shiftjis'
    >>> replace(b'\\x81\\\\', [(b'\\\\', b'/')])
    '\\x81\\\\'
    """

    # some character encodings (cp932 for Japanese, at least) use
    # ASCII characters other than control/alphabet/digit as a part of
    # multi-bytes characters, so direct replacing with such characters
    # on strings in local encoding causes invalid byte sequences.
    utext = text.decode(pycompat.sysstr(encoding.encoding))
    for f, t in substs:
        utext = utext.replace(f.decode("ascii"), t.decode("ascii"))
    return utext.encode(pycompat.sysstr(encoding.encoding))


_blockre = re.compile(br"\n(?:\s*\n)+")


def findblocks(text):
    """Find continuous blocks of lines in text.

    Returns a list of dictionaries representing the blocks. Each block
    has an 'indent' field and a 'lines' field.
    """
    blocks = []
    for b in _blockre.split(text.lstrip(b'\n').rstrip()):
        lines = b.splitlines()
        if lines:
            indent = min((len(l) - len(l.lstrip())) for l in lines)
            lines = [l[indent:] for l in lines]
            blocks.append({b'indent': indent, b'lines': lines})
    return blocks


def findliteralblocks(blocks):
    """Finds literal blocks and adds a 'type' field to the blocks.

    Literal blocks are given the type 'literal', all other blocks are
    given type the 'paragraph'.
    """
    i = 0
    while i < len(blocks):
        # Searching for a block that looks like this:
        #
        # +------------------------------+
        # | paragraph                    |
        # | (ends with "::")             |
        # +------------------------------+
        #    +---------------------------+
        #    | indented literal block    |
        #    +---------------------------+
        blocks[i][b'type'] = b'paragraph'
        if blocks[i][b'lines'][-1].endswith(b'::') and i + 1 < len(blocks):
            indent = blocks[i][b'indent']
            adjustment = blocks[i + 1][b'indent'] - indent

            if blocks[i][b'lines'] == [b'::']:
                # Expanded form: remove block
                del blocks[i]
                i -= 1
            elif blocks[i][b'lines'][-1].endswith(b' ::'):
                # Partially minimized form: remove space and both
                # colons.
                blocks[i][b'lines'][-1] = blocks[i][b'lines'][-1][:-3]
            elif (
                len(blocks[i][b'lines']) == 1
                and blocks[i][b'lines'][0].lstrip(b' ').startswith(b'.. ')
                and blocks[i][b'lines'][0].find(b' ', 3) == -1
            ):
                # directive on its own line, not a literal block
                i += 1
                continue
            else:
                # Fully minimized form: remove just one colon.
                blocks[i][b'lines'][-1] = blocks[i][b'lines'][-1][:-1]

            # List items are formatted with a hanging indent. We must
            # correct for this here while we still have the original
            # information on the indentation of the subsequent literal
            # blocks available.
            m = _bulletre.match(blocks[i][b'lines'][0])
            if m:
                indent += m.end()
                adjustment -= m.end()

            # Mark the following indented blocks.
            while i + 1 < len(blocks) and blocks[i + 1][b'indent'] > indent:
                blocks[i + 1][b'type'] = b'literal'
                blocks[i + 1][b'indent'] -= adjustment
                i += 1
        i += 1
    return blocks


_bulletre = re.compile(br'(\*|-|[0-9A-Za-z]+\.|\(?[0-9A-Za-z]+\)|\|) ')
_optionre = re.compile(
    br'^(-([a-zA-Z0-9]), )?(--[a-z0-9-]+)' br'((.*)  +)(.*)$'
)
_fieldre = re.compile(br':(?![: ])((?:\:|[^:])*)(?<! ):[ ]+(.*)')
_definitionre = re.compile(br'[^ ]')
_tablere = re.compile(br'(=+\s+)*=+')


def splitparagraphs(blocks):
    """Split paragraphs into lists."""
    # Tuples with (list type, item regexp, single line items?). Order
    # matters: definition lists has the least specific regexp and must
    # come last.
    listtypes = [
        (b'bullet', _bulletre, True),
        (b'option', _optionre, True),
        (b'field', _fieldre, True),
        (b'definition', _definitionre, False),
    ]

    def match(lines, i, itemre, singleline):
        """Does itemre match an item at line i?

        A list item can be followed by an indented line or another list
        item (but only if singleline is True).
        """
        line1 = lines[i]
        line2 = i + 1 < len(lines) and lines[i + 1] or b''
        if not itemre.match(line1):
            return False
        if singleline:
            return line2 == b'' or line2[0:1] == b' ' or itemre.match(line2)
        else:
            return line2.startswith(b' ')

    i = 0
    while i < len(blocks):
        if blocks[i][b'type'] == b'paragraph':
            lines = blocks[i][b'lines']
            for type, itemre, singleline in listtypes:
                if match(lines, 0, itemre, singleline):
                    items = []
                    for j, line in enumerate(lines):
                        if match(lines, j, itemre, singleline):
                            items.append(
                                {
                                    b'type': type,
                                    b'lines': [],
                                    b'indent': blocks[i][b'indent'],
                                }
                            )
                        items[-1][b'lines'].append(line)
                    blocks[i : i + 1] = items
                    break
        i += 1
    return blocks


_fieldwidth = 14


def updatefieldlists(blocks):
    """Find key for field lists."""
    i = 0
    while i < len(blocks):
        if blocks[i][b'type'] != b'field':
            i += 1
            continue

        j = i
        while j < len(blocks) and blocks[j][b'type'] == b'field':
            m = _fieldre.match(blocks[j][b'lines'][0])
            key, rest = m.groups()
            blocks[j][b'lines'][0] = rest
            blocks[j][b'key'] = key.replace(br'\:', b':')
            j += 1

        i = j + 1

    return blocks


def updateoptionlists(blocks):
    i = 0
    while i < len(blocks):
        if blocks[i][b'type'] != b'option':
            i += 1
            continue

        optstrwidth = 0
        j = i
        while j < len(blocks) and blocks[j][b'type'] == b'option':
            m = _optionre.match(blocks[j][b'lines'][0])

            shortoption = m.group(2)
            group3 = m.group(3)
            longoption = group3[2:].strip()
            desc = m.group(6).strip()
            longoptionarg = m.group(5).strip()
            blocks[j][b'lines'][0] = desc

            noshortop = b''
            if not shortoption:
                noshortop = b'   '

            opt = b"%s%s" % (
                shortoption and b"-%s " % shortoption or b'',
                b"%s--%s %s" % (noshortop, longoption, longoptionarg),
            )
            opt = opt.rstrip()
            blocks[j][b'optstr'] = opt
            optstrwidth = max(optstrwidth, encoding.colwidth(opt))
            j += 1

        for block in blocks[i:j]:
            block[b'optstrwidth'] = optstrwidth
        i = j + 1
    return blocks


def prunecontainers(blocks, keep):
    """Prune unwanted containers.

    The blocks must have a 'type' field, i.e., they should have been
    run through findliteralblocks first.
    """
    pruned = []
    i = 0
    while i + 1 < len(blocks):
        # Searching for a block that looks like this:
        #
        # +-------+---------------------------+
        # | ".. container ::" type            |
        # +---+                               |
        #     | blocks                        |
        #     +-------------------------------+
        if blocks[i][b'type'] == b'paragraph' and blocks[i][b'lines'][
            0
        ].startswith(b'.. container::'):
            indent = blocks[i][b'indent']
            adjustment = blocks[i + 1][b'indent'] - indent
            containertype = blocks[i][b'lines'][0][15:]
            prune = True
            for c in keep:
                if c in containertype.split(b'.'):
                    prune = False
            if prune:
                pruned.append(containertype)

            # Always delete "..container:: type" block
            del blocks[i]
            j = i
            i -= 1
            while j < len(blocks) and blocks[j][b'indent'] > indent:
                if prune:
                    del blocks[j]
                else:
                    blocks[j][b'indent'] -= adjustment
                    j += 1
        i += 1
    return blocks, pruned


_sectionre = re.compile(br"""^([-=`:.'"~^_*+#])\1+$""")


def findtables(blocks):
    """Find simple tables

    Only simple one-line table elements are supported
    """

    for block in blocks:
        # Searching for a block that looks like this:
        #
        # === ==== ===
        #  A    B   C
        # === ==== ===  <- optional
        #  1    2   3
        #  x    y   z
        # === ==== ===
        if (
            block[b'type'] == b'paragraph'
            and len(block[b'lines']) > 2
            and _tablere.match(block[b'lines'][0])
            and block[b'lines'][0] == block[b'lines'][-1]
        ):
            block[b'type'] = b'table'
            block[b'header'] = False
            div = block[b'lines'][0]

            # column markers are ASCII so we can calculate column
            # position in bytes
            columns = [
                x
                for x in range(len(div))
                if div[x : x + 1] == b'=' and (x == 0 or div[x - 1 : x] == b' ')
            ]
            rows = []
            for l in block[b'lines'][1:-1]:
                if l == div:
                    block[b'header'] = True
                    continue
                row = []
                # we measure columns not in bytes or characters but in
                # colwidth which makes things tricky
                pos = columns[0]  # leading whitespace is bytes
                for n, start in enumerate(columns):
                    if n + 1 < len(columns):
                        width = columns[n + 1] - start
                        v = encoding.getcols(l, pos, width)  # gather columns
                        pos += len(v)  # calculate byte position of end
                        row.append(v.strip())
                    else:
                        row.append(l[pos:].strip())
                rows.append(row)

            block[b'table'] = rows

    return blocks


def findsections(blocks):
    """Finds sections.

    The blocks must have a 'type' field, i.e., they should have been
    run through findliteralblocks first.
    """
    for block in blocks:
        # Searching for a block that looks like this:
        #
        # +------------------------------+
        # | Section title                |
        # | -------------                |
        # +------------------------------+
        if (
            block[b'type'] == b'paragraph'
            and len(block[b'lines']) == 2
            and encoding.colwidth(block[b'lines'][0]) == len(block[b'lines'][1])
            and _sectionre.match(block[b'lines'][1])
        ):
            block[b'underline'] = block[b'lines'][1][0:1]
            block[b'type'] = b'section'
            del block[b'lines'][1]
    return blocks


def inlineliterals(blocks):
    substs = [(b'``', b'"')]
    for b in blocks:
        if b[b'type'] in (b'paragraph', b'section'):
            b[b'lines'] = [replace(l, substs) for l in b[b'lines']]
    return blocks


def hgrole(blocks):
    substs = [(b':hg:`', b"'hg "), (b'`', b"'")]
    for b in blocks:
        if b[b'type'] in (b'paragraph', b'section'):
            # Turn :hg:`command` into "hg command". This also works
            # when there is a line break in the command and relies on
            # the fact that we have no stray back-quotes in the input
            # (run the blocks through inlineliterals first).
            b[b'lines'] = [replace(l, substs) for l in b[b'lines']]
    return blocks


def addmargins(blocks):
    """Adds empty blocks for vertical spacing.

    This groups bullets, options, and definitions together with no vertical
    space between them, and adds an empty block between all other blocks.
    """
    i = 1
    while i < len(blocks):
        if blocks[i][b'type'] == blocks[i - 1][b'type'] and blocks[i][
            b'type'
        ] in (
            b'bullet',
            b'option',
            b'field',
        ):
            i += 1
        elif not blocks[i - 1][b'lines']:
            # no lines in previous block, do not separate
            i += 1
        else:
            blocks.insert(
                i, {b'lines': [b''], b'indent': 0, b'type': b'margin'}
            )
            i += 2
    return blocks


def prunecomments(blocks):
    """Remove comments."""
    i = 0
    while i < len(blocks):
        b = blocks[i]
        if b[b'type'] == b'paragraph' and (
            b[b'lines'][0].startswith(b'.. ') or b[b'lines'] == [b'..']
        ):
            del blocks[i]
            if i < len(blocks) and blocks[i][b'type'] == b'margin':
                del blocks[i]
        else:
            i += 1
    return blocks


def findadmonitions(blocks, admonitions=None):
    """
    Makes the type of the block an admonition block if
    the first line is an admonition directive
    """
    admonitions = admonitions or _admonitiontitles.keys()

    admonitionre = re.compile(
        br'\.\. (%s)::' % b'|'.join(sorted(admonitions)), flags=re.IGNORECASE
    )

    i = 0
    while i < len(blocks):
        m = admonitionre.match(blocks[i][b'lines'][0])
        if m:
            blocks[i][b'type'] = b'admonition'
            admonitiontitle = blocks[i][b'lines'][0][3 : m.end() - 2].lower()

            firstline = blocks[i][b'lines'][0][m.end() + 1 :]
            if firstline:
                blocks[i][b'lines'].insert(1, b'   ' + firstline)

            blocks[i][b'admonitiontitle'] = admonitiontitle
            del blocks[i][b'lines'][0]
        i = i + 1
    return blocks


_admonitiontitles = {
    b'attention': _(b'Attention:'),
    b'caution': _(b'Caution:'),
    b'danger': _(b'!Danger!'),
    b'error': _(b'Error:'),
    b'hint': _(b'Hint:'),
    b'important': _(b'Important:'),
    b'note': _(b'Note:'),
    b'tip': _(b'Tip:'),
    b'warning': _(b'Warning!'),
}


def formatoption(block, width):
    desc = b' '.join(map(bytes.strip, block[b'lines']))
    colwidth = encoding.colwidth(block[b'optstr'])
    usablewidth = width - 1
    hanging = block[b'optstrwidth']
    initindent = b'%s%s  ' % (block[b'optstr'], b' ' * ((hanging - colwidth)))
    hangindent = b' ' * (encoding.colwidth(initindent) + 1)
    return b' %s\n' % (
        stringutil.wrap(
            desc, usablewidth, initindent=initindent, hangindent=hangindent
        )
    )


def formatblock(block, width):
    """Format a block according to width."""
    if width <= 0:
        width = 78
    indent = b' ' * block[b'indent']
    if block[b'type'] == b'admonition':
        admonition = _admonitiontitles[block[b'admonitiontitle']]
        if not block[b'lines']:
            return indent + admonition + b'\n'
        hang = len(block[b'lines'][-1]) - len(block[b'lines'][-1].lstrip())

        defindent = indent + hang * b' '
        text = b' '.join(map(bytes.strip, block[b'lines']))
        return b'%s\n%s\n' % (
            indent + admonition,
            stringutil.wrap(
                text, width=width, initindent=defindent, hangindent=defindent
            ),
        )
    if block[b'type'] == b'margin':
        return b'\n'
    if block[b'type'] == b'literal':
        indent += b'  '
        return indent + (b'\n' + indent).join(block[b'lines']) + b'\n'
    if block[b'type'] == b'section':
        underline = encoding.colwidth(block[b'lines'][0]) * block[b'underline']
        return b"%s%s\n%s%s\n" % (indent, block[b'lines'][0], indent, underline)
    if block[b'type'] == b'table':
        table = block[b'table']
        # compute column widths
        widths = [max([encoding.colwidth(e) for e in c]) for c in zip(*table)]
        text = b''
        span = sum(widths) + len(widths) - 1
        indent = b' ' * block[b'indent']
        hang = b' ' * (len(indent) + span - widths[-1])

        for row in table:
            l = []
            for w, v in zip(widths, row):
                pad = b' ' * (w - encoding.colwidth(v))
                l.append(v + pad)
            l = b' '.join(l)
            l = stringutil.wrap(
                l, width=width, initindent=indent, hangindent=hang
            )
            if not text and block[b'header']:
                text = l + b'\n' + indent + b'-' * (min(width, span)) + b'\n'
            else:
                text += l + b"\n"
        return text
    if block[b'type'] == b'definition':
        term = indent + block[b'lines'][0]
        hang = len(block[b'lines'][-1]) - len(block[b'lines'][-1].lstrip())
        defindent = indent + hang * b' '
        text = b' '.join(map(bytes.strip, block[b'lines'][1:]))
        return b'%s\n%s\n' % (
            term,
            stringutil.wrap(
                text, width=width, initindent=defindent, hangindent=defindent
            ),
        )
    subindent = indent
    if block[b'type'] == b'bullet':
        if block[b'lines'][0].startswith(b'| '):
            # Remove bullet for line blocks and add no extra
            # indentation.
            block[b'lines'][0] = block[b'lines'][0][2:]
        else:
            m = _bulletre.match(block[b'lines'][0])
            subindent = indent + m.end() * b' '
    elif block[b'type'] == b'field':
        key = block[b'key']
        subindent = indent + _fieldwidth * b' '
        if len(key) + 2 > _fieldwidth:
            # key too large, use full line width
            key = key.ljust(width)
        else:
            # key fits within field width
            key = key.ljust(_fieldwidth)
        block[b'lines'][0] = key + block[b'lines'][0]
    elif block[b'type'] == b'option':
        return formatoption(block, width)

    text = b' '.join(map(bytes.strip, block[b'lines']))
    return (
        stringutil.wrap(
            text, width=width, initindent=indent, hangindent=subindent
        )
        + b'\n'
    )


def formathtml(blocks):
    """Format RST blocks as HTML"""

    out = []
    headernest = b''
    listnest = []

    def escape(s):
        return url.escape(s, True)

    def openlist(start, level):
        if not listnest or listnest[-1][0] != start:
            listnest.append((start, level))
            out.append(b'<%s>\n' % start)

    blocks = [b for b in blocks if b[b'type'] != b'margin']

    for pos, b in enumerate(blocks):
        btype = b[b'type']
        level = b[b'indent']
        lines = b[b'lines']

        if btype == b'admonition':
            admonition = escape(_admonitiontitles[b[b'admonitiontitle']])
            text = escape(b' '.join(map(bytes.strip, lines)))
            out.append(b'<p>\n<b>%s</b> %s\n</p>\n' % (admonition, text))
        elif btype == b'paragraph':
            out.append(b'<p>\n%s\n</p>\n' % escape(b'\n'.join(lines)))
        elif btype == b'margin':
            pass
        elif btype == b'literal':
            out.append(b'<pre>\n%s\n</pre>\n' % escape(b'\n'.join(lines)))
        elif btype == b'section':
            i = b[b'underline']
            if i not in headernest:
                headernest += i
            level = headernest.index(i) + 1
            out.append(b'<h%d>%s</h%d>\n' % (level, escape(lines[0]), level))
        elif btype == b'table':
            table = b[b'table']
            out.append(b'<table>\n')
            for row in table:
                out.append(b'<tr>')
                for v in row:
                    out.append(b'<td>')
                    out.append(escape(v))
                    out.append(b'</td>')
                    out.append(b'\n')
                out.pop()
                out.append(b'</tr>\n')
            out.append(b'</table>\n')
        elif btype == b'definition':
            openlist(b'dl', level)
            term = escape(lines[0])
            text = escape(b' '.join(map(bytes.strip, lines[1:])))
            out.append(b' <dt>%s\n <dd>%s\n' % (term, text))
        elif btype == b'bullet':
            bullet, head = lines[0].split(b' ', 1)
            if bullet in (b'*', b'-'):
                openlist(b'ul', level)
            else:
                openlist(b'ol', level)
            out.append(b' <li> %s\n' % escape(b' '.join([head] + lines[1:])))
        elif btype == b'field':
            openlist(b'dl', level)
            key = escape(b[b'key'])
            text = escape(b' '.join(map(bytes.strip, lines)))
            out.append(b' <dt>%s\n <dd>%s\n' % (key, text))
        elif btype == b'option':
            openlist(b'dl', level)
            opt = escape(b[b'optstr'])
            desc = escape(b' '.join(map(bytes.strip, lines)))
            out.append(b' <dt>%s\n <dd>%s\n' % (opt, desc))

        # close lists if indent level of next block is lower
        if listnest:
            start, level = listnest[-1]
            if pos == len(blocks) - 1:
                out.append(b'</%s>\n' % start)
                listnest.pop()
            else:
                nb = blocks[pos + 1]
                ni = nb[b'indent']
                if ni < level or (
                    ni == level
                    and nb[b'type'] not in b'definition bullet field option'
                ):
                    out.append(b'</%s>\n' % start)
                    listnest.pop()

    return b''.join(out)


def parse(text, indent=0, keep=None, admonitions=None):
    """Parse text into a list of blocks"""
    blocks = findblocks(text)
    for b in blocks:
        b[b'indent'] += indent
    blocks = findliteralblocks(blocks)
    blocks = findtables(blocks)
    blocks, pruned = prunecontainers(blocks, keep or [])
    blocks = findsections(blocks)
    blocks = inlineliterals(blocks)
    blocks = hgrole(blocks)
    blocks = splitparagraphs(blocks)
    blocks = updatefieldlists(blocks)
    blocks = updateoptionlists(blocks)
    blocks = findadmonitions(blocks, admonitions=admonitions)
    blocks = addmargins(blocks)
    blocks = prunecomments(blocks)
    return blocks, pruned


def formatblocks(blocks, width):
    text = b''.join(formatblock(b, width) for b in blocks)
    return text


def formatplain(blocks, width):
    """Format parsed blocks as plain text"""
    return b''.join(formatblock(b, width) for b in blocks)


def format(text, width=80, indent=0, keep=None, style=b'plain', section=None):
    """Parse and format the text according to width."""
    blocks, pruned = parse(text, indent, keep or [])
    if section:
        blocks = filtersections(blocks, section)
    if style == b'html':
        return formathtml(blocks)
    else:
        return formatplain(blocks, width=width)


def filtersections(blocks, section):
    """Select parsed blocks under the specified section

    The section name is separated by a dot, and matches the suffix of the
    full section path.
    """
    parents = []
    sections = _getsections(blocks)
    blocks = []
    i = 0
    lastparents = []
    synthetic = []
    collapse = True
    while i < len(sections):
        path, nest, b = sections[i]
        del parents[nest:]
        parents.append(i)
        if path == section or path.endswith(b'.' + section):
            if lastparents != parents:
                llen = len(lastparents)
                plen = len(parents)
                if llen and llen != plen:
                    collapse = False
                s = []
                for j in range(3, plen - 1):
                    parent = parents[j]
                    if j >= llen or lastparents[j] != parent:
                        s.append(len(blocks))
                        sec = sections[parent][2]
                        blocks.append(sec[0])
                        blocks.append(sec[-1])
                if s:
                    synthetic.append(s)

            lastparents = parents[:]
            blocks.extend(b)

            ## Also show all subnested sections
            while i + 1 < len(sections) and sections[i + 1][1] > nest:
                i += 1
                blocks.extend(sections[i][2])
        i += 1
    if collapse:
        synthetic.reverse()
        for s in synthetic:
            path = [blocks[syn][b'lines'][0] for syn in s]
            real = s[-1] + 2
            realline = blocks[real][b'lines']
            realline[0] = b'"%s"' % b'.'.join(path + [realline[0]]).replace(
                b'"', b''
            )
            del blocks[s[0] : real]

    return blocks


def _getsections(blocks):
    '''return a list of (section path, nesting level, blocks) tuples'''
    nest = b""
    names = ()
    secs = []

    def getname(b):
        if b[b'type'] == b'field':
            x = b[b'key']
        else:
            x = b[b'lines'][0]
        x = encoding.lower(x).strip(b'"')
        if b'(' in x:
            x = x.split(b'(')[0]
        return x

    for b in blocks:
        if b[b'type'] == b'section':
            i = b[b'underline']
            if i not in nest:
                nest += i
            level = nest.index(i) + 1
            nest = nest[:level]
            names = names[:level] + (getname(b),)
            secs.append((b'.'.join(names), level, [b]))
        elif b[b'type'] in (b'definition', b'field'):
            i = b' '
            if i not in nest:
                nest += i
            level = nest.index(i) + 1
            nest = nest[:level]
            for i in range(1, len(secs) + 1):
                sec = secs[-i]
                if sec[1] < level:
                    break
                siblings = [a for a in sec[2] if a[b'type'] == b'definition']
                if siblings:
                    siblingindent = siblings[-1][b'indent']
                    indent = b[b'indent']
                    if siblingindent < indent:
                        level += 1
                        break
                    elif siblingindent == indent:
                        level = sec[1]
                        break
            names = names[:level] + (getname(b),)
            secs.append((b'.'.join(names), level, [b]))
        else:
            if not secs:
                # add an initial empty section
                secs = [(b'', 0, [])]
            if b[b'type'] != b'margin':
                pointer = 1
                bindent = b[b'indent']
                while pointer < len(secs):
                    section = secs[-pointer][2][0]
                    if section[b'type'] != b'margin':
                        sindent = section[b'indent']
                        if len(section[b'lines']) > 1:
                            sindent += len(section[b'lines'][1]) - len(
                                section[b'lines'][1].lstrip(b' ')
                            )
                        if bindent >= sindent:
                            break
                    pointer += 1
                if pointer > 1:
                    blevel = secs[-pointer][1]
                    if section[b'type'] != b[b'type']:
                        blevel += 1
                    secs.append((b'', blevel, []))
            secs[-1][2].append(b)
    return secs


def maketable(data, indent=0, header=False):
    '''Generate an RST table for the given table data as a list of lines'''

    widths = [max(encoding.colwidth(e) for e in c) for c in zip(*data)]
    indent = b' ' * indent
    div = indent + b' '.join(b'=' * w for w in widths) + b'\n'

    out = [div]
    for row in data:
        l = []
        for w, v in zip(widths, row):
            if b'\n' in v:
                # only remove line breaks and indentation, long lines are
                # handled by the next tool
                v = b' '.join(e.lstrip() for e in v.split(b'\n'))
            pad = b' ' * (w - encoding.colwidth(v))
            l.append(v + pad)
        out.append(indent + b' '.join(l) + b"\n")
    if header and len(data) > 1:
        out.insert(2, div)
    out.append(div)
    return out
