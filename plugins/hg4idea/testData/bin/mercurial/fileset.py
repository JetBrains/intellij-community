# fileset.py - file set queries for mercurial
#
# Copyright 2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno
import re

from .i18n import _
from .pycompat import getattr
from . import (
    error,
    filesetlang,
    match as matchmod,
    mergestate as mergestatemod,
    pycompat,
    registrar,
    scmutil,
    util,
)
from .utils import stringutil

# common weight constants
_WEIGHT_CHECK_FILENAME = filesetlang.WEIGHT_CHECK_FILENAME
_WEIGHT_READ_CONTENTS = filesetlang.WEIGHT_READ_CONTENTS
_WEIGHT_STATUS = filesetlang.WEIGHT_STATUS
_WEIGHT_STATUS_THOROUGH = filesetlang.WEIGHT_STATUS_THOROUGH

# helpers for processing parsed tree
getsymbol = filesetlang.getsymbol
getstring = filesetlang.getstring
_getkindpat = filesetlang.getkindpat
getpattern = filesetlang.getpattern
getargs = filesetlang.getargs


def getmatch(mctx, x):
    if not x:
        raise error.ParseError(_(b"missing argument"))
    return methods[x[0]](mctx, *x[1:])


def getmatchwithstatus(mctx, x, hint):
    keys = set(getstring(hint, b'status hint must be a string').split())
    return getmatch(mctx.withstatus(keys), x)


def stringmatch(mctx, x):
    return mctx.matcher([x])


def kindpatmatch(mctx, x, y):
    return stringmatch(
        mctx,
        _getkindpat(
            x, y, matchmod.allpatternkinds, _(b"pattern must be a string")
        ),
    )


def patternsmatch(mctx, *xs):
    allkinds = matchmod.allpatternkinds
    patterns = [
        getpattern(x, allkinds, _(b"pattern must be a string")) for x in xs
    ]
    return mctx.matcher(patterns)


def andmatch(mctx, x, y):
    xm = getmatch(mctx, x)
    ym = getmatch(mctx.narrowed(xm), y)
    return matchmod.intersectmatchers(xm, ym)


def ormatch(mctx, *xs):
    ms = [getmatch(mctx, x) for x in xs]
    return matchmod.unionmatcher(ms)


def notmatch(mctx, x):
    m = getmatch(mctx, x)
    return mctx.predicate(lambda f: not m(f), predrepr=(b'<not %r>', m))


def minusmatch(mctx, x, y):
    xm = getmatch(mctx, x)
    ym = getmatch(mctx.narrowed(xm), y)
    return matchmod.differencematcher(xm, ym)


def listmatch(mctx, *xs):
    raise error.ParseError(
        _(b"can't use a list in this context"),
        hint=_(b'see \'hg help "filesets.x or y"\''),
    )


def func(mctx, a, b):
    funcname = getsymbol(a)
    if funcname in symbols:
        return symbols[funcname](mctx, b)

    keep = lambda fn: getattr(fn, '__doc__', None) is not None

    syms = [s for (s, fn) in symbols.items() if keep(fn)]
    raise error.UnknownIdentifier(funcname, syms)


# symbols are callable like:
#  fun(mctx, x)
# with:
#  mctx - current matchctx instance
#  x - argument in tree form
symbols = filesetlang.symbols

predicate = registrar.filesetpredicate(symbols)


@predicate(b'modified()', callstatus=True, weight=_WEIGHT_STATUS)
def modified(mctx, x):
    """File that is modified according to :hg:`status`."""
    # i18n: "modified" is a keyword
    getargs(x, 0, 0, _(b"modified takes no arguments"))
    s = set(mctx.status().modified)
    return mctx.predicate(s.__contains__, predrepr=b'modified')


@predicate(b'added()', callstatus=True, weight=_WEIGHT_STATUS)
def added(mctx, x):
    """File that is added according to :hg:`status`."""
    # i18n: "added" is a keyword
    getargs(x, 0, 0, _(b"added takes no arguments"))
    s = set(mctx.status().added)
    return mctx.predicate(s.__contains__, predrepr=b'added')


@predicate(b'removed()', callstatus=True, weight=_WEIGHT_STATUS)
def removed(mctx, x):
    """File that is removed according to :hg:`status`."""
    # i18n: "removed" is a keyword
    getargs(x, 0, 0, _(b"removed takes no arguments"))
    s = set(mctx.status().removed)
    return mctx.predicate(s.__contains__, predrepr=b'removed')


@predicate(b'deleted()', callstatus=True, weight=_WEIGHT_STATUS)
def deleted(mctx, x):
    """Alias for ``missing()``."""
    # i18n: "deleted" is a keyword
    getargs(x, 0, 0, _(b"deleted takes no arguments"))
    s = set(mctx.status().deleted)
    return mctx.predicate(s.__contains__, predrepr=b'deleted')


@predicate(b'missing()', callstatus=True, weight=_WEIGHT_STATUS)
def missing(mctx, x):
    """File that is missing according to :hg:`status`."""
    # i18n: "missing" is a keyword
    getargs(x, 0, 0, _(b"missing takes no arguments"))
    s = set(mctx.status().deleted)
    return mctx.predicate(s.__contains__, predrepr=b'deleted')


@predicate(b'unknown()', callstatus=True, weight=_WEIGHT_STATUS_THOROUGH)
def unknown(mctx, x):
    """File that is unknown according to :hg:`status`."""
    # i18n: "unknown" is a keyword
    getargs(x, 0, 0, _(b"unknown takes no arguments"))
    s = set(mctx.status().unknown)
    return mctx.predicate(s.__contains__, predrepr=b'unknown')


@predicate(b'ignored()', callstatus=True, weight=_WEIGHT_STATUS_THOROUGH)
def ignored(mctx, x):
    """File that is ignored according to :hg:`status`."""
    # i18n: "ignored" is a keyword
    getargs(x, 0, 0, _(b"ignored takes no arguments"))
    s = set(mctx.status().ignored)
    return mctx.predicate(s.__contains__, predrepr=b'ignored')


@predicate(b'clean()', callstatus=True, weight=_WEIGHT_STATUS)
def clean(mctx, x):
    """File that is clean according to :hg:`status`."""
    # i18n: "clean" is a keyword
    getargs(x, 0, 0, _(b"clean takes no arguments"))
    s = set(mctx.status().clean)
    return mctx.predicate(s.__contains__, predrepr=b'clean')


@predicate(b'tracked()')
def tracked(mctx, x):
    """File that is under Mercurial control."""
    # i18n: "tracked" is a keyword
    getargs(x, 0, 0, _(b"tracked takes no arguments"))
    return mctx.predicate(mctx.ctx.__contains__, predrepr=b'tracked')


@predicate(b'binary()', weight=_WEIGHT_READ_CONTENTS)
def binary(mctx, x):
    """File that appears to be binary (contains NUL bytes)."""
    # i18n: "binary" is a keyword
    getargs(x, 0, 0, _(b"binary takes no arguments"))
    return mctx.fpredicate(
        lambda fctx: fctx.isbinary(), predrepr=b'binary', cache=True
    )


@predicate(b'exec()')
def exec_(mctx, x):
    """File that is marked as executable."""
    # i18n: "exec" is a keyword
    getargs(x, 0, 0, _(b"exec takes no arguments"))
    ctx = mctx.ctx
    return mctx.predicate(lambda f: ctx.flags(f) == b'x', predrepr=b'exec')


@predicate(b'symlink()')
def symlink(mctx, x):
    """File that is marked as a symlink."""
    # i18n: "symlink" is a keyword
    getargs(x, 0, 0, _(b"symlink takes no arguments"))
    ctx = mctx.ctx
    return mctx.predicate(lambda f: ctx.flags(f) == b'l', predrepr=b'symlink')


@predicate(b'resolved()', weight=_WEIGHT_STATUS)
def resolved(mctx, x):
    """File that is marked resolved according to :hg:`resolve -l`."""
    # i18n: "resolved" is a keyword
    getargs(x, 0, 0, _(b"resolved takes no arguments"))
    if mctx.ctx.rev() is not None:
        return mctx.never()
    ms = mergestatemod.mergestate.read(mctx.ctx.repo())
    return mctx.predicate(
        lambda f: f in ms and ms[f] == b'r', predrepr=b'resolved'
    )


@predicate(b'unresolved()', weight=_WEIGHT_STATUS)
def unresolved(mctx, x):
    """File that is marked unresolved according to :hg:`resolve -l`."""
    # i18n: "unresolved" is a keyword
    getargs(x, 0, 0, _(b"unresolved takes no arguments"))
    if mctx.ctx.rev() is not None:
        return mctx.never()
    ms = mergestatemod.mergestate.read(mctx.ctx.repo())
    return mctx.predicate(
        lambda f: f in ms and ms[f] == b'u', predrepr=b'unresolved'
    )


@predicate(b'hgignore()', weight=_WEIGHT_STATUS)
def hgignore(mctx, x):
    """File that matches the active .hgignore pattern."""
    # i18n: "hgignore" is a keyword
    getargs(x, 0, 0, _(b"hgignore takes no arguments"))
    return mctx.ctx.repo().dirstate._ignore


@predicate(b'portable()', weight=_WEIGHT_CHECK_FILENAME)
def portable(mctx, x):
    """File that has a portable name. (This doesn't include filenames with case
    collisions.)
    """
    # i18n: "portable" is a keyword
    getargs(x, 0, 0, _(b"portable takes no arguments"))
    return mctx.predicate(
        lambda f: util.checkwinfilename(f) is None, predrepr=b'portable'
    )


@predicate(b'grep(regex)', weight=_WEIGHT_READ_CONTENTS)
def grep(mctx, x):
    """File contains the given regular expression."""
    try:
        # i18n: "grep" is a keyword
        r = re.compile(getstring(x, _(b"grep requires a pattern")))
    except re.error as e:
        raise error.ParseError(
            _(b'invalid match pattern: %s') % stringutil.forcebytestr(e)
        )
    return mctx.fpredicate(
        lambda fctx: r.search(fctx.data()),
        predrepr=(b'grep(%r)', r.pattern),
        cache=True,
    )


def _sizetomax(s):
    try:
        s = s.strip().lower()
        for k, v in util._sizeunits:
            if s.endswith(k):
                # max(4k) = 5k - 1, max(4.5k) = 4.6k - 1
                n = s[: -len(k)]
                inc = 1.0
                if b"." in n:
                    inc /= 10 ** len(n.split(b".")[1])
                return int((float(n) + inc) * v) - 1
        # no extension, this is a precise value
        return int(s)
    except ValueError:
        raise error.ParseError(_(b"couldn't parse size: %s") % s)


def sizematcher(expr):
    """Return a function(size) -> bool from the ``size()`` expression"""
    expr = expr.strip()
    if b'-' in expr:  # do we have a range?
        a, b = expr.split(b'-', 1)
        a = util.sizetoint(a)
        b = util.sizetoint(b)
        return lambda x: x >= a and x <= b
    elif expr.startswith(b"<="):
        a = util.sizetoint(expr[2:])
        return lambda x: x <= a
    elif expr.startswith(b"<"):
        a = util.sizetoint(expr[1:])
        return lambda x: x < a
    elif expr.startswith(b">="):
        a = util.sizetoint(expr[2:])
        return lambda x: x >= a
    elif expr.startswith(b">"):
        a = util.sizetoint(expr[1:])
        return lambda x: x > a
    else:
        a = util.sizetoint(expr)
        b = _sizetomax(expr)
        return lambda x: x >= a and x <= b


@predicate(b'size(expression)', weight=_WEIGHT_STATUS)
def size(mctx, x):
    """File size matches the given expression. Examples:

    - size('1k') - files from 1024 to 2047 bytes
    - size('< 20k') - files less than 20480 bytes
    - size('>= .5MB') - files at least 524288 bytes
    - size('4k - 1MB') - files from 4096 bytes to 1048576 bytes
    """
    # i18n: "size" is a keyword
    expr = getstring(x, _(b"size requires an expression"))
    m = sizematcher(expr)
    return mctx.fpredicate(
        lambda fctx: m(fctx.size()), predrepr=(b'size(%r)', expr), cache=True
    )


@predicate(b'encoding(name)', weight=_WEIGHT_READ_CONTENTS)
def encoding(mctx, x):
    """File can be successfully decoded with the given character
    encoding. May not be useful for encodings other than ASCII and
    UTF-8.
    """

    # i18n: "encoding" is a keyword
    enc = getstring(x, _(b"encoding requires an encoding name"))

    def encp(fctx):
        d = fctx.data()
        try:
            d.decode(pycompat.sysstr(enc))
            return True
        except LookupError:
            raise error.Abort(_(b"unknown encoding '%s'") % enc)
        except UnicodeDecodeError:
            return False

    return mctx.fpredicate(encp, predrepr=(b'encoding(%r)', enc), cache=True)


@predicate(b'eol(style)', weight=_WEIGHT_READ_CONTENTS)
def eol(mctx, x):
    """File contains newlines of the given style (dos, unix, mac). Binary
    files are excluded, files with mixed line endings match multiple
    styles.
    """

    # i18n: "eol" is a keyword
    enc = getstring(x, _(b"eol requires a style name"))

    def eolp(fctx):
        if fctx.isbinary():
            return False
        d = fctx.data()
        if (enc == b'dos' or enc == b'win') and b'\r\n' in d:
            return True
        elif enc == b'unix' and re.search(b'(?<!\r)\n', d):
            return True
        elif enc == b'mac' and re.search(b'\r(?!\n)', d):
            return True
        return False

    return mctx.fpredicate(eolp, predrepr=(b'eol(%r)', enc), cache=True)


@predicate(b'copied()')
def copied(mctx, x):
    """File that is recorded as being copied."""
    # i18n: "copied" is a keyword
    getargs(x, 0, 0, _(b"copied takes no arguments"))

    def copiedp(fctx):
        p = fctx.parents()
        return p and p[0].path() != fctx.path()

    return mctx.fpredicate(copiedp, predrepr=b'copied', cache=True)


@predicate(b'revs(revs, pattern)', weight=_WEIGHT_STATUS)
def revs(mctx, x):
    """Evaluate set in the specified revisions. If the revset match multiple
    revs, this will return file matching pattern in any of the revision.
    """
    # i18n: "revs" is a keyword
    r, x = getargs(x, 2, 2, _(b"revs takes two arguments"))
    # i18n: "revs" is a keyword
    revspec = getstring(r, _(b"first argument to revs must be a revision"))
    repo = mctx.ctx.repo()
    revs = scmutil.revrange(repo, [revspec])

    matchers = []
    for r in revs:
        ctx = repo[r]
        mc = mctx.switch(ctx.p1(), ctx)
        matchers.append(getmatch(mc, x))
    if not matchers:
        return mctx.never()
    if len(matchers) == 1:
        return matchers[0]
    return matchmod.unionmatcher(matchers)


@predicate(b'status(base, rev, pattern)', weight=_WEIGHT_STATUS)
def status(mctx, x):
    """Evaluate predicate using status change between ``base`` and
    ``rev``. Examples:

    - ``status(3, 7, added())`` - matches files added from "3" to "7"
    """
    repo = mctx.ctx.repo()
    # i18n: "status" is a keyword
    b, r, x = getargs(x, 3, 3, _(b"status takes three arguments"))
    # i18n: "status" is a keyword
    baseerr = _(b"first argument to status must be a revision")
    baserevspec = getstring(b, baseerr)
    if not baserevspec:
        raise error.ParseError(baseerr)
    reverr = _(b"second argument to status must be a revision")
    revspec = getstring(r, reverr)
    if not revspec:
        raise error.ParseError(reverr)
    basectx, ctx = scmutil.revpair(repo, [baserevspec, revspec])
    mc = mctx.switch(basectx, ctx)
    return getmatch(mc, x)


@predicate(b'subrepo([pattern])')
def subrepo(mctx, x):
    """Subrepositories whose paths match the given pattern."""
    # i18n: "subrepo" is a keyword
    getargs(x, 0, 1, _(b"subrepo takes at most one argument"))
    ctx = mctx.ctx
    sstate = ctx.substate
    if x:
        pat = getpattern(
            x,
            matchmod.allpatternkinds,
            # i18n: "subrepo" is a keyword
            _(b"subrepo requires a pattern or no arguments"),
        )
        fast = not matchmod.patkind(pat)
        if fast:

            def m(s):
                return s == pat

        else:
            m = matchmod.match(ctx.repo().root, b'', [pat], ctx=ctx)
        return mctx.predicate(
            lambda f: f in sstate and m(f), predrepr=(b'subrepo(%r)', pat)
        )
    else:
        return mctx.predicate(sstate.__contains__, predrepr=b'subrepo')


methods = {
    b'withstatus': getmatchwithstatus,
    b'string': stringmatch,
    b'symbol': stringmatch,
    b'kindpat': kindpatmatch,
    b'patterns': patternsmatch,
    b'and': andmatch,
    b'or': ormatch,
    b'minus': minusmatch,
    b'list': listmatch,
    b'not': notmatch,
    b'func': func,
}


class matchctx(object):
    def __init__(self, basectx, ctx, cwd, badfn=None):
        self._basectx = basectx
        self.ctx = ctx
        self._badfn = badfn
        self._match = None
        self._status = None
        self.cwd = cwd

    def narrowed(self, match):
        """Create matchctx for a sub-tree narrowed by the given matcher"""
        mctx = matchctx(self._basectx, self.ctx, self.cwd, self._badfn)
        mctx._match = match
        # leave wider status which we don't have to care
        mctx._status = self._status
        return mctx

    def switch(self, basectx, ctx):
        mctx = matchctx(basectx, ctx, self.cwd, self._badfn)
        mctx._match = self._match
        return mctx

    def withstatus(self, keys):
        """Create matchctx which has precomputed status specified by the keys"""
        mctx = matchctx(self._basectx, self.ctx, self.cwd, self._badfn)
        mctx._match = self._match
        mctx._buildstatus(keys)
        return mctx

    def _buildstatus(self, keys):
        self._status = self._basectx.status(
            self.ctx,
            self._match,
            listignored=b'ignored' in keys,
            listclean=b'clean' in keys,
            listunknown=b'unknown' in keys,
        )

    def status(self):
        return self._status

    def matcher(self, patterns):
        return self.ctx.match(patterns, badfn=self._badfn, cwd=self.cwd)

    def predicate(self, predfn, predrepr=None, cache=False):
        """Create a matcher to select files by predfn(filename)"""
        if cache:
            predfn = util.cachefunc(predfn)
        return matchmod.predicatematcher(
            predfn, predrepr=predrepr, badfn=self._badfn
        )

    def fpredicate(self, predfn, predrepr=None, cache=False):
        """Create a matcher to select files by predfn(fctx) at the current
        revision

        Missing files are ignored.
        """
        ctx = self.ctx
        if ctx.rev() is None:

            def fctxpredfn(f):
                try:
                    fctx = ctx[f]
                except error.LookupError:
                    return False
                try:
                    fctx.audit()
                except error.Abort:
                    return False
                try:
                    return predfn(fctx)
                except (IOError, OSError) as e:
                    # open()-ing a directory fails with EACCES on Windows
                    if e.errno in (
                        errno.ENOENT,
                        errno.EACCES,
                        errno.ENOTDIR,
                        errno.EISDIR,
                    ):
                        return False
                    raise

        else:

            def fctxpredfn(f):
                try:
                    fctx = ctx[f]
                except error.LookupError:
                    return False
                return predfn(fctx)

        return self.predicate(fctxpredfn, predrepr=predrepr, cache=cache)

    def never(self):
        """Create a matcher to select nothing"""
        return matchmod.never(badfn=self._badfn)


def match(ctx, cwd, expr, badfn=None):
    """Create a matcher for a single fileset expression"""
    tree = filesetlang.parse(expr)
    tree = filesetlang.analyze(tree)
    tree = filesetlang.optimize(tree)
    mctx = matchctx(ctx.p1(), ctx, cwd, badfn=badfn)
    return getmatch(mctx, tree)


def loadpredicate(ui, extname, registrarobj):
    """Load fileset predicates from specified registrarobj"""
    for name, func in pycompat.iteritems(registrarobj._table):
        symbols[name] = func


# tell hggettext to extract docstrings from these functions:
i18nfunctions = symbols.values()
