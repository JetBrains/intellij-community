# Copyright 2016-present Facebook. All Rights Reserved.
#
# format: defines the format used to output annotate result
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from __future__ import absolute_import

from mercurial.node import (
    hex,
    short,
)
from mercurial import (
    encoding,
    pycompat,
    templatefilters,
    util,
)
from mercurial.utils import dateutil

# imitating mercurial.commands.annotate, not using the vanilla formatter since
# the data structures are a bit different, and we have some fast paths.
class defaultformatter(object):
    """the default formatter that does leftpad and support some common flags"""

    def __init__(self, ui, repo, opts):
        self.ui = ui
        self.opts = opts

        if ui.quiet:
            datefunc = dateutil.shortdate
        else:
            datefunc = dateutil.datestr
        datefunc = util.cachefunc(datefunc)
        getctx = util.cachefunc(lambda x: repo[x[0]])
        hexfunc = self._hexfunc

        # special handling working copy "changeset" and "rev" functions
        if self.opts.get(b'rev') == b'wdir()':
            orig = hexfunc
            hexfunc = lambda x: None if x is None else orig(x)
            wnode = hexfunc(repo[b'.'].node()) + b'+'
            wrev = b'%d' % repo[b'.'].rev()
            wrevpad = b''
            if not opts.get(b'changeset'):  # only show + if changeset is hidden
                wrev += b'+'
                wrevpad = b' '
            revenc = lambda x: wrev if x is None else (b'%d' % x) + wrevpad

            def csetenc(x):
                if x is None:
                    return wnode
                return pycompat.bytestr(x) + b' '

        else:
            revenc = csetenc = pycompat.bytestr

        # opt name, separator, raw value (for json/plain), encoder (for plain)
        opmap = [
            (b'user', b' ', lambda x: getctx(x).user(), ui.shortuser),
            (b'number', b' ', lambda x: getctx(x).rev(), revenc),
            (b'changeset', b' ', lambda x: hexfunc(x[0]), csetenc),
            (b'date', b' ', lambda x: getctx(x).date(), datefunc),
            (b'file', b' ', lambda x: x[2], pycompat.bytestr),
            (b'line_number', b':', lambda x: x[1] + 1, pycompat.bytestr),
        ]
        fieldnamemap = {b'number': b'rev', b'changeset': b'node'}
        funcmap = [
            (get, sep, fieldnamemap.get(op, op), enc)
            for op, sep, get, enc in opmap
            if opts.get(op)
        ]
        # no separator for first column
        funcmap[0] = list(funcmap[0])
        funcmap[0][1] = b''
        self.funcmap = funcmap

    def write(self, annotatedresult, lines=None, existinglines=None):
        """(annotateresult, [str], set([rev, linenum])) -> None. write output.
        annotateresult can be [(node, linenum, path)], or [(node, linenum)]
        """
        pieces = []  # [[str]]
        maxwidths = []  # [int]

        # calculate padding
        for f, sep, name, enc in self.funcmap:
            l = [enc(f(x)) for x in annotatedresult]
            pieces.append(l)
            if name in [b'node', b'date']:  # node and date has fixed size
                l = l[:1]
            widths = pycompat.maplist(encoding.colwidth, set(l))
            maxwidth = max(widths) if widths else 0
            maxwidths.append(maxwidth)

        # buffered output
        result = b''
        for i in pycompat.xrange(len(annotatedresult)):
            for j, p in enumerate(pieces):
                sep = self.funcmap[j][1]
                padding = b' ' * (maxwidths[j] - len(p[i]))
                result += sep + padding + p[i]
            if lines:
                if existinglines is None:
                    result += b': ' + lines[i]
                else:  # extra formatting showing whether a line exists
                    key = (annotatedresult[i][0], annotatedresult[i][1])
                    if key in existinglines:
                        result += b':  ' + lines[i]
                    else:
                        result += b': ' + self.ui.label(
                            b'-' + lines[i], b'diff.deleted'
                        )

            if result[-1:] != b'\n':
                result += b'\n'

        self.ui.write(result)

    @util.propertycache
    def _hexfunc(self):
        if self.ui.debugflag or self.opts.get(b'long_hash'):
            return hex
        else:
            return short

    def end(self):
        pass


class jsonformatter(defaultformatter):
    def __init__(self, ui, repo, opts):
        super(jsonformatter, self).__init__(ui, repo, opts)
        self.ui.write(b'[')
        self.needcomma = False

    def write(self, annotatedresult, lines=None, existinglines=None):
        if annotatedresult:
            self._writecomma()

        pieces = [
            (name, pycompat.maplist(f, annotatedresult))
            for f, sep, name, enc in self.funcmap
        ]
        if lines is not None:
            pieces.append((b'line', lines))
        pieces.sort()

        seps = [b','] * len(pieces[:-1]) + [b'']

        result = b''
        lasti = len(annotatedresult) - 1
        for i in pycompat.xrange(len(annotatedresult)):
            result += b'\n {\n'
            for j, p in enumerate(pieces):
                k, vs = p
                result += b'  "%s": %s%s\n' % (
                    k,
                    templatefilters.json(vs[i], paranoid=False),
                    seps[j],
                )
            result += b' }%s' % (b'' if i == lasti else b',')
        if lasti >= 0:
            self.needcomma = True

        self.ui.write(result)

    def _writecomma(self):
        if self.needcomma:
            self.ui.write(b',')
            self.needcomma = False

    @util.propertycache
    def _hexfunc(self):
        return hex

    def end(self):
        self.ui.write(b'\n]\n')
