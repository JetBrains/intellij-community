# templatefuncs.py - common template functions
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import binascii
import re

from .i18n import _
from .node import bin
from . import (
    color,
    dagop,
    diffutil,
    encoding,
    error,
    minirst,
    obsutil,
    pycompat,
    registrar,
    revset as revsetmod,
    revsetlang,
    scmutil,
    templatefilters,
    templatekw,
    templateutil,
    util,
)
from .utils import (
    dateutil,
    stringutil,
)

evalrawexp = templateutil.evalrawexp
evalwrapped = templateutil.evalwrapped
evalfuncarg = templateutil.evalfuncarg
evalboolean = templateutil.evalboolean
evaldate = templateutil.evaldate
evalinteger = templateutil.evalinteger
evalstring = templateutil.evalstring
evalstringliteral = templateutil.evalstringliteral

# dict of template built-in functions
funcs = {}
templatefunc = registrar.templatefunc(funcs)


@templatefunc(b'date(date[, fmt])')
def date(context, mapping, args):
    """Format a date. The format string uses the Python strftime format.
    The default is a Unix date format, including the timezone:
    "Mon Sep 04 15:13:13 2006 0700"."""
    if not (1 <= len(args) <= 2):
        # i18n: "date" is a keyword
        raise error.ParseError(_(b"date expects one or two arguments"))

    date = evaldate(
        context,
        mapping,
        args[0],
        # i18n: "date" is a keyword
        _(b"date expects a date information"),
    )
    fmt = None
    if len(args) == 2:
        fmt = evalstring(context, mapping, args[1])
    if fmt is None:
        return dateutil.datestr(date)
    else:
        return dateutil.datestr(date, fmt)


@templatefunc(b'dict([[key=]value...])', argspec=b'*args **kwargs')
def dict_(context, mapping, args):
    """Construct a dict from key-value pairs. A key may be omitted if
    a value expression can provide an unambiguous name."""
    data = util.sortdict()

    for v in args[b'args']:
        k = templateutil.findsymbolicname(v)
        if not k:
            raise error.ParseError(_(b'dict key cannot be inferred'))
        if k in data or k in args[b'kwargs']:
            raise error.ParseError(_(b"duplicated dict key '%s' inferred") % k)
        data[k] = evalfuncarg(context, mapping, v)

    data.update(
        (k, evalfuncarg(context, mapping, v))
        for k, v in args[b'kwargs'].items()
    )
    return templateutil.hybriddict(data)


@templatefunc(
    b'diff([includepattern [, excludepattern]])', requires={b'ctx', b'ui'}
)
def diff(context, mapping, args):
    """Show a diff, optionally
    specifying files to include or exclude."""
    if len(args) > 2:
        # i18n: "diff" is a keyword
        raise error.ParseError(_(b"diff expects zero, one, or two arguments"))

    def getpatterns(i):
        if i < len(args):
            s = evalstring(context, mapping, args[i]).strip()
            if s:
                return [s]
        return []

    ctx = context.resource(mapping, b'ctx')
    ui = context.resource(mapping, b'ui')
    diffopts = diffutil.diffallopts(ui)
    chunks = ctx.diff(
        match=ctx.match([], getpatterns(0), getpatterns(1)), opts=diffopts
    )

    return b''.join(chunks)


@templatefunc(
    b'extdata(source)', argspec=b'source', requires={b'ctx', b'cache'}
)
def extdata(context, mapping, args):
    """Show a text read from the specified extdata source. (EXPERIMENTAL)"""
    if b'source' not in args:
        # i18n: "extdata" is a keyword
        raise error.ParseError(_(b'extdata expects one argument'))

    source = evalstring(context, mapping, args[b'source'])
    if not source:
        sym = templateutil.findsymbolicname(args[b'source'])
        if sym:
            raise error.ParseError(
                _(b'empty data source specified'),
                hint=_(b"did you mean extdata('%s')?") % sym,
            )
        else:
            raise error.ParseError(_(b'empty data source specified'))
    cache = context.resource(mapping, b'cache').setdefault(b'extdata', {})
    ctx = context.resource(mapping, b'ctx')
    if source in cache:
        data = cache[source]
    else:
        data = cache[source] = scmutil.extdatasource(ctx.repo(), source)
    return data.get(ctx.rev(), b'')


@templatefunc(b'files(pattern)', requires={b'ctx'})
def files(context, mapping, args):
    """All files of the current changeset matching the pattern. See
    :hg:`help patterns`."""
    if not len(args) == 1:
        # i18n: "files" is a keyword
        raise error.ParseError(_(b"files expects one argument"))

    raw = evalstring(context, mapping, args[0])
    ctx = context.resource(mapping, b'ctx')
    m = ctx.match([raw])
    files = list(ctx.matches(m))
    return templateutil.compatfileslist(context, mapping, b"file", files)


@templatefunc(b'fill(text[, width[, initialident[, hangindent]]])')
def fill(context, mapping, args):
    """Fill many
    paragraphs with optional indentation. See the "fill" filter."""
    if not (1 <= len(args) <= 4):
        # i18n: "fill" is a keyword
        raise error.ParseError(_(b"fill expects one to four arguments"))

    text = evalstring(context, mapping, args[0])
    width = 76
    initindent = b''
    hangindent = b''
    if 2 <= len(args) <= 4:
        width = evalinteger(
            context,
            mapping,
            args[1],
            # i18n: "fill" is a keyword
            _(b"fill expects an integer width"),
        )
        try:
            initindent = evalstring(context, mapping, args[2])
            hangindent = evalstring(context, mapping, args[3])
        except IndexError:
            pass

    return templatefilters.fill(text, width, initindent, hangindent)


@templatefunc(b'filter(iterable[, expr])')
def filter_(context, mapping, args):
    """Remove empty elements from a list or a dict. If expr specified, it's
    applied to each element to test emptiness."""
    if not (1 <= len(args) <= 2):
        # i18n: "filter" is a keyword
        raise error.ParseError(_(b"filter expects one or two arguments"))
    iterable = evalwrapped(context, mapping, args[0])
    if len(args) == 1:

        def select(w):
            return w.tobool(context, mapping)

    else:

        def select(w):
            if not isinstance(w, templateutil.mappable):
                raise error.ParseError(_(b"not filterable by expression"))
            lm = context.overlaymap(mapping, w.tomap(context))
            return evalboolean(context, lm, args[1])

    return iterable.filter(context, mapping, select)


@templatefunc(b'formatnode(node)', requires={b'ui'})
def formatnode(context, mapping, args):
    """Obtain the preferred form of a changeset hash. (DEPRECATED)"""
    if len(args) != 1:
        # i18n: "formatnode" is a keyword
        raise error.ParseError(_(b"formatnode expects one argument"))

    ui = context.resource(mapping, b'ui')
    node = evalstring(context, mapping, args[0])
    if ui.debugflag:
        return node
    return templatefilters.short(node)


@templatefunc(b'mailmap(author)', requires={b'repo', b'cache'})
def mailmap(context, mapping, args):
    """Return the author, updated according to the value
    set in the .mailmap file"""
    if len(args) != 1:
        raise error.ParseError(_(b"mailmap expects one argument"))

    author = evalstring(context, mapping, args[0])

    cache = context.resource(mapping, b'cache')
    repo = context.resource(mapping, b'repo')

    if b'mailmap' not in cache:
        data = repo.wvfs.tryread(b'.mailmap')
        cache[b'mailmap'] = stringutil.parsemailmap(data)

    return stringutil.mapname(cache[b'mailmap'], author)


@templatefunc(
    b'pad(text, width[, fillchar=\' \'[, left=False[, truncate=False]]])',
    argspec=b'text width fillchar left truncate',
)
def pad(context, mapping, args):
    """Pad text with a
    fill character."""
    if b'text' not in args or b'width' not in args:
        # i18n: "pad" is a keyword
        raise error.ParseError(_(b"pad() expects two to four arguments"))

    width = evalinteger(
        context,
        mapping,
        args[b'width'],
        # i18n: "pad" is a keyword
        _(b"pad() expects an integer width"),
    )

    text = evalstring(context, mapping, args[b'text'])

    truncate = False
    left = False
    fillchar = b' '
    if b'fillchar' in args:
        fillchar = evalstring(context, mapping, args[b'fillchar'])
        if len(color.stripeffects(fillchar)) != 1:
            # i18n: "pad" is a keyword
            raise error.ParseError(_(b"pad() expects a single fill character"))
    if b'left' in args:
        left = evalboolean(context, mapping, args[b'left'])
    if b'truncate' in args:
        truncate = evalboolean(context, mapping, args[b'truncate'])

    fillwidth = width - encoding.colwidth(color.stripeffects(text))
    if fillwidth < 0 and truncate:
        return encoding.trim(color.stripeffects(text), width, leftside=left)
    if fillwidth <= 0:
        return text
    if left:
        return fillchar * fillwidth + text
    else:
        return text + fillchar * fillwidth


@templatefunc(b'indent(text, indentchars[, firstline])')
def indent(context, mapping, args):
    """Indents all non-empty lines
    with the characters given in the indentchars string. An optional
    third parameter will override the indent for the first line only
    if present."""
    if not (2 <= len(args) <= 3):
        # i18n: "indent" is a keyword
        raise error.ParseError(_(b"indent() expects two or three arguments"))

    text = evalstring(context, mapping, args[0])
    indent = evalstring(context, mapping, args[1])

    firstline = indent
    if len(args) == 3:
        firstline = evalstring(context, mapping, args[2])

    return templatefilters.indent(text, indent, firstline=firstline)


@templatefunc(b'get(dict, key)')
def get(context, mapping, args):
    """Get an attribute/key from an object. Some keywords
    are complex types. This function allows you to obtain the value of an
    attribute on these types."""
    if len(args) != 2:
        # i18n: "get" is a keyword
        raise error.ParseError(_(b"get() expects two arguments"))

    dictarg = evalwrapped(context, mapping, args[0])
    key = evalrawexp(context, mapping, args[1])
    try:
        return dictarg.getmember(context, mapping, key)
    except error.ParseError as err:
        # i18n: "get" is a keyword
        hint = _(b"get() expects a dict as first argument")
        raise error.ParseError(bytes(err), hint=hint)


@templatefunc(b'config(section, name[, default])', requires={b'ui'})
def config(context, mapping, args):
    """Returns the requested hgrc config option as a string."""
    fn = context.resource(mapping, b'ui').config
    return _config(context, mapping, args, fn, evalstring)


@templatefunc(b'configbool(section, name[, default])', requires={b'ui'})
def configbool(context, mapping, args):
    """Returns the requested hgrc config option as a boolean."""
    fn = context.resource(mapping, b'ui').configbool
    return _config(context, mapping, args, fn, evalboolean)


@templatefunc(b'configint(section, name[, default])', requires={b'ui'})
def configint(context, mapping, args):
    """Returns the requested hgrc config option as an integer."""
    fn = context.resource(mapping, b'ui').configint
    return _config(context, mapping, args, fn, evalinteger)


def _config(context, mapping, args, configfn, defaultfn):
    if not (2 <= len(args) <= 3):
        raise error.ParseError(_(b"config expects two or three arguments"))

    # The config option can come from any section, though we specifically
    # reserve the [templateconfig] section for dynamically defining options
    # for this function without also requiring an extension.
    section = evalstringliteral(context, mapping, args[0])
    name = evalstringliteral(context, mapping, args[1])
    if len(args) == 3:
        default = defaultfn(context, mapping, args[2])
        return configfn(section, name, default)
    else:
        return configfn(section, name)


@templatefunc(b'if(expr, then[, else])')
def if_(context, mapping, args):
    """Conditionally execute based on the result of
    an expression."""
    if not (2 <= len(args) <= 3):
        # i18n: "if" is a keyword
        raise error.ParseError(_(b"if expects two or three arguments"))

    test = evalboolean(context, mapping, args[0])
    if test:
        return evalrawexp(context, mapping, args[1])
    elif len(args) == 3:
        return evalrawexp(context, mapping, args[2])


@templatefunc(b'ifcontains(needle, haystack, then[, else])')
def ifcontains(context, mapping, args):
    """Conditionally execute based
    on whether the item "needle" is in "haystack"."""
    if not (3 <= len(args) <= 4):
        # i18n: "ifcontains" is a keyword
        raise error.ParseError(_(b"ifcontains expects three or four arguments"))

    haystack = evalwrapped(context, mapping, args[1])
    try:
        needle = evalrawexp(context, mapping, args[0])
        found = haystack.contains(context, mapping, needle)
    except error.ParseError:
        found = False

    if found:
        return evalrawexp(context, mapping, args[2])
    elif len(args) == 4:
        return evalrawexp(context, mapping, args[3])


@templatefunc(b'ifeq(expr1, expr2, then[, else])')
def ifeq(context, mapping, args):
    """Conditionally execute based on
    whether 2 items are equivalent."""
    if not (3 <= len(args) <= 4):
        # i18n: "ifeq" is a keyword
        raise error.ParseError(_(b"ifeq expects three or four arguments"))

    test = evalstring(context, mapping, args[0])
    match = evalstring(context, mapping, args[1])
    if test == match:
        return evalrawexp(context, mapping, args[2])
    elif len(args) == 4:
        return evalrawexp(context, mapping, args[3])


@templatefunc(b'join(list, sep)')
def join(context, mapping, args):
    """Join items in a list with a delimiter."""
    if not (1 <= len(args) <= 2):
        # i18n: "join" is a keyword
        raise error.ParseError(_(b"join expects one or two arguments"))

    joinset = evalwrapped(context, mapping, args[0])
    joiner = b" "
    if len(args) > 1:
        joiner = evalstring(context, mapping, args[1])
    return joinset.join(context, mapping, joiner)


@templatefunc(b'label(label, expr)', requires={b'ui'})
def label(context, mapping, args):
    """Apply a label to generated content. Content with a label
    applied can result in additional post-processing, such as
    automatic colorization. In order to receive effects, labels must
    have a dot, such as `log.secret` or `branch.active`."""
    if len(args) != 2:
        # i18n: "label" is a keyword
        raise error.ParseError(_(b"label expects two arguments"))

    ui = context.resource(mapping, b'ui')
    thing = evalstring(context, mapping, args[1])
    # preserve unknown symbol as literal so effects like 'red', 'bold',
    # etc. don't need to be quoted
    label = evalstringliteral(context, mapping, args[0])

    return ui.label(thing, label)


@templatefunc(b'latesttag([pattern])')
def latesttag(context, mapping, args):
    """The global tags matching the given pattern on the
    most recent globally tagged ancestor of this changeset.
    If no such tags exist, the "{tag}" template resolves to
    the string "null". See :hg:`help revisions.patterns` for the pattern
    syntax.
    """
    if len(args) > 1:
        # i18n: "latesttag" is a keyword
        raise error.ParseError(_(b"latesttag expects at most one argument"))

    pattern = None
    if len(args) == 1:
        pattern = evalstring(context, mapping, args[0])
    return templatekw.showlatesttags(context, mapping, pattern)


@templatefunc(b'localdate(date[, tz])')
def localdate(context, mapping, args):
    """Converts a date to the specified timezone.
    The default is local date."""
    if not (1 <= len(args) <= 2):
        # i18n: "localdate" is a keyword
        raise error.ParseError(_(b"localdate expects one or two arguments"))

    date = evaldate(
        context,
        mapping,
        args[0],
        # i18n: "localdate" is a keyword
        _(b"localdate expects a date information"),
    )
    if len(args) >= 2:
        tzoffset = None
        tz = evalfuncarg(context, mapping, args[1])
        if isinstance(tz, bytes):
            tzoffset, remainder = dateutil.parsetimezone(tz)
            if remainder:
                tzoffset = None
        if tzoffset is None:
            try:
                tzoffset = int(tz)
            except (TypeError, ValueError):
                # i18n: "localdate" is a keyword
                raise error.ParseError(_(b"localdate expects a timezone"))
    else:
        tzoffset = dateutil.makedate()[1]
    return templateutil.date((date[0], tzoffset))


@templatefunc(b'max(iterable)')
def max_(context, mapping, args, **kwargs):
    """Return the max of an iterable"""
    if len(args) != 1:
        # i18n: "max" is a keyword
        raise error.ParseError(_(b"max expects one argument"))

    iterable = evalwrapped(context, mapping, args[0])
    try:
        return iterable.getmax(context, mapping)
    except error.ParseError as err:
        # i18n: "max" is a keyword
        hint = _(b"max first argument should be an iterable")
        raise error.ParseError(bytes(err), hint=hint)


@templatefunc(b'min(iterable)')
def min_(context, mapping, args, **kwargs):
    """Return the min of an iterable"""
    if len(args) != 1:
        # i18n: "min" is a keyword
        raise error.ParseError(_(b"min expects one argument"))

    iterable = evalwrapped(context, mapping, args[0])
    try:
        return iterable.getmin(context, mapping)
    except error.ParseError as err:
        # i18n: "min" is a keyword
        hint = _(b"min first argument should be an iterable")
        raise error.ParseError(bytes(err), hint=hint)


@templatefunc(b'mod(a, b)')
def mod(context, mapping, args):
    """Calculate a mod b such that a / b + a mod b == a"""
    if not len(args) == 2:
        # i18n: "mod" is a keyword
        raise error.ParseError(_(b"mod expects two arguments"))

    func = lambda a, b: a % b
    return templateutil.runarithmetic(
        context, mapping, (func, args[0], args[1])
    )


@templatefunc(b'obsfateoperations(markers)')
def obsfateoperations(context, mapping, args):
    """Compute obsfate related information based on markers (EXPERIMENTAL)"""
    if len(args) != 1:
        # i18n: "obsfateoperations" is a keyword
        raise error.ParseError(_(b"obsfateoperations expects one argument"))

    markers = evalfuncarg(context, mapping, args[0])

    try:
        data = obsutil.markersoperations(markers)
        return templateutil.hybridlist(data, name=b'operation')
    except (TypeError, KeyError):
        # i18n: "obsfateoperations" is a keyword
        errmsg = _(b"obsfateoperations first argument should be an iterable")
        raise error.ParseError(errmsg)


@templatefunc(b'obsfatedate(markers)')
def obsfatedate(context, mapping, args):
    """Compute obsfate related information based on markers (EXPERIMENTAL)"""
    if len(args) != 1:
        # i18n: "obsfatedate" is a keyword
        raise error.ParseError(_(b"obsfatedate expects one argument"))

    markers = evalfuncarg(context, mapping, args[0])

    try:
        # TODO: maybe this has to be a wrapped list of date wrappers?
        data = obsutil.markersdates(markers)
        return templateutil.hybridlist(data, name=b'date', fmt=b'%d %d')
    except (TypeError, KeyError):
        # i18n: "obsfatedate" is a keyword
        errmsg = _(b"obsfatedate first argument should be an iterable")
        raise error.ParseError(errmsg)


@templatefunc(b'obsfateusers(markers)')
def obsfateusers(context, mapping, args):
    """Compute obsfate related information based on markers (EXPERIMENTAL)"""
    if len(args) != 1:
        # i18n: "obsfateusers" is a keyword
        raise error.ParseError(_(b"obsfateusers expects one argument"))

    markers = evalfuncarg(context, mapping, args[0])

    try:
        data = obsutil.markersusers(markers)
        return templateutil.hybridlist(data, name=b'user')
    except (TypeError, KeyError, ValueError):
        # i18n: "obsfateusers" is a keyword
        msg = _(
            b"obsfateusers first argument should be an iterable of "
            b"obsmakers"
        )
        raise error.ParseError(msg)


@templatefunc(b'obsfateverb(successors, markers)')
def obsfateverb(context, mapping, args):
    """Compute obsfate related information based on successors (EXPERIMENTAL)"""
    if len(args) != 2:
        # i18n: "obsfateverb" is a keyword
        raise error.ParseError(_(b"obsfateverb expects two arguments"))

    successors = evalfuncarg(context, mapping, args[0])
    markers = evalfuncarg(context, mapping, args[1])

    try:
        return obsutil.obsfateverb(successors, markers)
    except TypeError:
        # i18n: "obsfateverb" is a keyword
        errmsg = _(b"obsfateverb first argument should be countable")
        raise error.ParseError(errmsg)


@templatefunc(b'relpath(path)', requires={b'repo'})
def relpath(context, mapping, args):
    """Convert a repository-absolute path into a filesystem path relative to
    the current working directory."""
    if len(args) != 1:
        # i18n: "relpath" is a keyword
        raise error.ParseError(_(b"relpath expects one argument"))

    repo = context.resource(mapping, b'repo')
    path = evalstring(context, mapping, args[0])
    return repo.pathto(path)


@templatefunc(b'revset(query[, formatargs...])', requires={b'repo', b'cache'})
def revset(context, mapping, args):
    """Execute a revision set query. See
    :hg:`help revset`."""
    if not len(args) > 0:
        # i18n: "revset" is a keyword
        raise error.ParseError(_(b"revset expects one or more arguments"))

    raw = evalstring(context, mapping, args[0])
    repo = context.resource(mapping, b'repo')

    def query(expr):
        m = revsetmod.match(repo.ui, expr, lookup=revsetmod.lookupfn(repo))
        return m(repo)

    if len(args) > 1:
        key = None  # dynamically-created revs shouldn't be cached
        formatargs = [evalfuncarg(context, mapping, a) for a in args[1:]]
        revs = query(revsetlang.formatspec(raw, *formatargs))
    else:
        cache = context.resource(mapping, b'cache')
        revsetcache = cache.setdefault(b"revsetcache", {})
        key = raw
        if key in revsetcache:
            revs = revsetcache[key]
        else:
            revs = query(raw)
            revsetcache[key] = revs
    return templateutil.revslist(repo, revs, name=b'revision', cachekey=key)


@templatefunc(b'rstdoc(text, style)')
def rstdoc(context, mapping, args):
    """Format reStructuredText."""
    if len(args) != 2:
        # i18n: "rstdoc" is a keyword
        raise error.ParseError(_(b"rstdoc expects two arguments"))

    text = evalstring(context, mapping, args[0])
    style = evalstring(context, mapping, args[1])

    return minirst.format(text, style=style, keep=[b'verbose'])


@templatefunc(b'search(pattern, text)')
def search(context, mapping, args):
    """Look for the first text matching the regular expression pattern.
    Groups are accessible as ``{1}``, ``{2}``, ... in %-mapped template."""
    if len(args) != 2:
        # i18n: "search" is a keyword
        raise error.ParseError(_(b'search expects two arguments'))

    pat = evalstring(context, mapping, args[0])
    src = evalstring(context, mapping, args[1])
    try:
        patre = re.compile(pat)
    except re.error:
        # i18n: "search" is a keyword
        raise error.ParseError(_(b'search got an invalid pattern: %s') % pat)
    # named groups shouldn't shadow *reserved* resource keywords
    badgroups = context.knownresourcekeys() & set(
        pycompat.byteskwargs(patre.groupindex)
    )
    if badgroups:
        raise error.ParseError(
            # i18n: "search" is a keyword
            _(b'invalid group %(group)s in search pattern: %(pat)s')
            % {
                b'group': b', '.join(b"'%s'" % g for g in sorted(badgroups)),
                b'pat': pat,
            }
        )

    match = patre.search(src)
    if not match:
        return templateutil.mappingnone()

    lm = {b'0': match.group(0)}
    lm.update((b'%d' % i, v) for i, v in enumerate(match.groups(), 1))
    lm.update(pycompat.byteskwargs(match.groupdict()))
    return templateutil.mappingdict(lm, tmpl=b'{0}')


@templatefunc(b'separate(sep, args...)', argspec=b'sep *args')
def separate(context, mapping, args):
    """Add a separator between non-empty arguments."""
    if b'sep' not in args:
        # i18n: "separate" is a keyword
        raise error.ParseError(_(b"separate expects at least one argument"))

    sep = evalstring(context, mapping, args[b'sep'])
    first = True
    for arg in args[b'args']:
        argstr = evalstring(context, mapping, arg)
        if not argstr:
            continue
        if first:
            first = False
        else:
            yield sep
        yield argstr


@templatefunc(b'shortest(node, minlength=4)', requires={b'repo', b'cache'})
def shortest(context, mapping, args):
    """Obtain the shortest representation of
    a node."""
    if not (1 <= len(args) <= 2):
        # i18n: "shortest" is a keyword
        raise error.ParseError(_(b"shortest() expects one or two arguments"))

    hexnode = evalstring(context, mapping, args[0])

    minlength = 4
    if len(args) > 1:
        minlength = evalinteger(
            context,
            mapping,
            args[1],
            # i18n: "shortest" is a keyword
            _(b"shortest() expects an integer minlength"),
        )

    repo = context.resource(mapping, b'repo')
    hexnodelen = 2 * repo.nodeconstants.nodelen
    if len(hexnode) > hexnodelen:
        return hexnode
    elif len(hexnode) == hexnodelen:
        try:
            node = bin(hexnode)
        except binascii.Error:
            return hexnode
    else:
        try:
            node = scmutil.resolvehexnodeidprefix(repo, hexnode)
        except error.WdirUnsupported:
            node = repo.nodeconstants.wdirid
        except error.LookupError:
            return hexnode
        if not node:
            return hexnode
    cache = context.resource(mapping, b'cache')
    try:
        return scmutil.shortesthexnodeidprefix(repo, node, minlength, cache)
    except error.RepoLookupError:
        return hexnode


@templatefunc(b'strip(text[, chars])')
def strip(context, mapping, args):
    """Strip characters from a string. By default,
    strips all leading and trailing whitespace."""
    if not (1 <= len(args) <= 2):
        # i18n: "strip" is a keyword
        raise error.ParseError(_(b"strip expects one or two arguments"))

    text = evalstring(context, mapping, args[0])
    if len(args) == 2:
        chars = evalstring(context, mapping, args[1])
        return text.strip(chars)
    return text.strip()


@templatefunc(b'sub(pattern, replacement, expression)')
def sub(context, mapping, args):
    """Perform text substitution
    using regular expressions."""
    if len(args) != 3:
        # i18n: "sub" is a keyword
        raise error.ParseError(_(b"sub expects three arguments"))

    pat = evalstring(context, mapping, args[0])
    rpl = evalstring(context, mapping, args[1])
    src = evalstring(context, mapping, args[2])
    try:
        patre = re.compile(pat)
    except re.error:
        # i18n: "sub" is a keyword
        raise error.ParseError(_(b"sub got an invalid pattern: %s") % pat)
    try:
        yield patre.sub(rpl, src)
    except re.error:
        # i18n: "sub" is a keyword
        raise error.ParseError(_(b"sub got an invalid replacement: %s") % rpl)


@templatefunc(b'startswith(pattern, text)')
def startswith(context, mapping, args):
    """Returns the value from the "text" argument
    if it begins with the content from the "pattern" argument."""
    if len(args) != 2:
        # i18n: "startswith" is a keyword
        raise error.ParseError(_(b"startswith expects two arguments"))

    patn = evalstring(context, mapping, args[0])
    text = evalstring(context, mapping, args[1])
    if text.startswith(patn):
        return text
    return b''


@templatefunc(
    b'subsetparents(rev, revset)',
    argspec=b'rev revset',
    requires={b'repo', b'cache'},
)
def subsetparents(context, mapping, args):
    """Look up parents of the rev in the sub graph given by the revset."""
    if b'rev' not in args or b'revset' not in args:
        # i18n: "subsetparents" is a keyword
        raise error.ParseError(_(b"subsetparents expects two arguments"))

    repo = context.resource(mapping, b'repo')

    rev = templateutil.evalinteger(context, mapping, args[b'rev'])

    # TODO: maybe subsetparents(rev) should be allowed. the default revset
    # will be the revisions specified by -rREV argument.
    q = templateutil.evalwrapped(context, mapping, args[b'revset'])
    if not isinstance(q, templateutil.revslist):
        # i18n: "subsetparents" is a keyword
        raise error.ParseError(_(b"subsetparents expects a queried revset"))
    subset = q.tovalue(context, mapping)
    key = q.cachekey

    if key:
        # cache only if revset query isn't dynamic
        cache = context.resource(mapping, b'cache')
        walkercache = cache.setdefault(b'subsetparentswalker', {})
        if key in walkercache:
            walker = walkercache[key]
        else:
            walker = dagop.subsetparentswalker(repo, subset)
            walkercache[key] = walker
    else:
        # for one-shot use, specify startrev to limit the search space
        walker = dagop.subsetparentswalker(repo, subset, startrev=rev)
    return templateutil.revslist(repo, walker.parentsset(rev))


@templatefunc(b'word(number, text[, separator])')
def word(context, mapping, args):
    """Return the nth word from a string."""
    if not (2 <= len(args) <= 3):
        # i18n: "word" is a keyword
        raise error.ParseError(
            _(b"word expects two or three arguments, got %d") % len(args)
        )

    num = evalinteger(
        context,
        mapping,
        args[0],
        # i18n: "word" is a keyword
        _(b"word expects an integer index"),
    )
    text = evalstring(context, mapping, args[1])
    if len(args) == 3:
        splitter = evalstring(context, mapping, args[2])
    else:
        splitter = None

    tokens = text.split(splitter)
    if num >= len(tokens) or num < -len(tokens):
        return b''
    else:
        return tokens[num]


def loadfunction(ui, extname, registrarobj):
    """Load template function from specified registrarobj"""
    for name, func in registrarobj._table.items():
        funcs[name] = func


# tell hggettext to extract docstrings from these functions:
i18nfunctions = funcs.values()
