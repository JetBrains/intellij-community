# highlight.py - highlight extension implementation file
#
#  Copyright 2007-2009 Adam Hupp <adam@hupp.org> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
#
# The original module was split in an interface and an implementation
# file to defer pygments loading and speedup extension setup.

from mercurial import demandimport
demandimport.ignore.extend(['pkgutil', 'pkg_resources', '__main__'])
from mercurial import util, encoding

from pygments import highlight
from pygments.util import ClassNotFound
from pygments.lexers import guess_lexer, guess_lexer_for_filename, TextLexer
from pygments.formatters import HtmlFormatter

SYNTAX_CSS = ('\n<link rel="stylesheet" href="{url}highlightcss" '
              'type="text/css" />')

def pygmentize(field, fctx, style, tmpl):

    # append a <link ...> to the syntax highlighting css
    old_header = tmpl.load('header')
    if SYNTAX_CSS not in old_header:
        new_header =  old_header + SYNTAX_CSS
        tmpl.cache['header'] = new_header

    text = fctx.data()
    if util.binary(text):
        return

    # Pygments is best used with Unicode strings:
    # <http://pygments.org/docs/unicode/>
    text = text.decode(encoding.encoding, 'replace')

    # To get multi-line strings right, we can't format line-by-line
    try:
        lexer = guess_lexer_for_filename(fctx.path(), text[:1024],
                                         stripnl=False)
    except (ClassNotFound, ValueError):
        try:
            lexer = guess_lexer(text[:1024], stripnl=False)
        except (ClassNotFound, ValueError):
            lexer = TextLexer(stripnl=False)

    formatter = HtmlFormatter(style=style)

    colorized = highlight(text, lexer, formatter)
    # strip wrapping div
    colorized = colorized[:colorized.find('\n</pre>')]
    colorized = colorized[colorized.find('<pre>') + 5:]
    coloriter = (s.encode(encoding.encoding, 'replace')
                 for s in colorized.splitlines())

    tmpl.filters['colorize'] = lambda x: coloriter.next()

    oldl = tmpl.cache[field]
    newl = oldl.replace('line|escape', 'line|colorize')
    tmpl.cache[field] = newl
