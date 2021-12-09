# -*- coding: UTF-8 -*-
# beautifygraph.py - improve graph output by using Unicode characters
#
# Copyright 2018 John Stiles <johnstiles@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''beautify log -G output by using Unicode characters (EXPERIMENTAL)

   A terminal with UTF-8 support and monospace narrow text are required.
'''

from __future__ import absolute_import

from mercurial.i18n import _
from mercurial import (
    encoding,
    extensions,
    graphmod,
    pycompat,
    templatekw,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


def prettyedge(before, edge, after):
    if edge == b'~':
        return b'\xE2\x95\xA7'  # U+2567 ╧
    if edge == b'/':
        return b'\xE2\x95\xB1'  # U+2571 ╱
    if edge == b'-':
        return b'\xE2\x94\x80'  # U+2500 ─
    if edge == b'|':
        return b'\xE2\x94\x82'  # U+2502 │
    if edge == b':':
        return b'\xE2\x94\x86'  # U+2506 ┆
    if edge == b'\\':
        return b'\xE2\x95\xB2'  # U+2572 ╲
    if edge == b'+':
        if before == b' ' and not after == b' ':
            return b'\xE2\x94\x9C'  # U+251C ├
        if after == b' ' and not before == b' ':
            return b'\xE2\x94\xA4'  # U+2524 ┤
        return b'\xE2\x94\xBC'  # U+253C ┼
    return edge


def convertedges(line):
    line = b' %s ' % line
    pretty = []
    for idx in pycompat.xrange(len(line) - 2):
        pretty.append(
            prettyedge(
                line[idx : idx + 1],
                line[idx + 1 : idx + 2],
                line[idx + 2 : idx + 3],
            )
        )
    return b''.join(pretty)


def getprettygraphnode(orig, *args, **kwargs):
    node = orig(*args, **kwargs)
    if node == b'o':
        return b'\xE2\x97\x8B'  # U+25CB ○
    if node == b'@':
        return b'\xE2\x97\x89'  # U+25C9 ◉
    if node == b'%':
        return b'\xE2\x97\x8D'  # U+25CE ◎
    if node == b'*':
        return b'\xE2\x88\x97'  # U+2217 ∗
    if node == b'x':
        return b'\xE2\x97\x8C'  # U+25CC ◌
    if node == b'_':
        return b'\xE2\x95\xA4'  # U+2564 ╤
    return node


def outputprettygraph(orig, ui, graph, *args, **kwargs):
    (edges, text) = zip(*graph)
    graph = zip([convertedges(e) for e in edges], text)
    return orig(ui, graph, *args, **kwargs)


def extsetup(ui):
    if ui.plain(b'graph'):
        return

    if encoding.encoding != b'UTF-8':
        ui.warn(_(b'beautifygraph: unsupported encoding, UTF-8 required\n'))
        return

    if 'A' in encoding._wide:
        ui.warn(
            _(
                b'beautifygraph: unsupported terminal settings, '
                b'monospace narrow text required\n'
            )
        )
        return

    extensions.wrapfunction(graphmod, b'outputgraph', outputprettygraph)
    extensions.wrapfunction(templatekw, b'getgraphnode', getprettygraphnode)
