# highlight - syntax highlighting in hgweb, based on Pygments
#
#  Copyright 2008, 2009 Patrick Mezard <pmezard@gmail.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
#
# The original module was split in an interface and an implementation
# file to defer pygments loading and speedup extension setup.

"""syntax highlighting for hgweb (requires Pygments)

It depends on the Pygments syntax highlighting library:
http://pygments.org/

There is a single configuration option::

  [web]
  pygments_style = <style>

The default is 'colorful'.
"""

import highlight
from mercurial.hgweb import webcommands, webutil, common
from mercurial import extensions, encoding

def filerevision_highlight(orig, web, tmpl, fctx):
    mt = ''.join(tmpl('mimetype', encoding=encoding.encoding))
    # only pygmentize for mimetype containing 'html' so we both match
    # 'text/html' and possibly 'application/xhtml+xml' in the future
    # so that we don't have to touch the extension when the mimetype
    # for a template changes; also hgweb optimizes the case that a
    # raw file is sent using rawfile() and doesn't call us, so we
    # can't clash with the file's content-type here in case we
    # pygmentize a html file
    if 'html' in mt:
        style = web.config('web', 'pygments_style', 'colorful')
        highlight.pygmentize('fileline', fctx, style, tmpl)
    return orig(web, tmpl, fctx)

def annotate_highlight(orig, web, req, tmpl):
    mt = ''.join(tmpl('mimetype', encoding=encoding.encoding))
    if 'html' in mt:
        fctx = webutil.filectx(web.repo, req)
        style = web.config('web', 'pygments_style', 'colorful')
        highlight.pygmentize('annotateline', fctx, style, tmpl)
    return orig(web, req, tmpl)

def generate_css(web, req, tmpl):
    pg_style = web.config('web', 'pygments_style', 'colorful')
    fmter = highlight.HtmlFormatter(style = pg_style)
    req.respond(common.HTTP_OK, 'text/css')
    return ['/* pygments_style = %s */\n\n' % pg_style, fmter.get_style_defs('')]

def extsetup():
    # monkeypatch in the new version
    extensions.wrapfunction(webcommands, '_filerevision', filerevision_highlight)
    extensions.wrapfunction(webcommands, 'annotate', annotate_highlight)
    webcommands.highlightcss = generate_css
    webcommands.__all__.append('highlightcss')
