# formatter.py - generic output formatting for mercurial
#
# Copyright 2012 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Generic output formatting for Mercurial

The formatter provides API to show data in various ways. The following
functions should be used in place of ui.write():

- fm.write() for unconditional output
- fm.condwrite() to show some extra data conditionally in plain output
- fm.context() to provide changectx to template output
- fm.data() to provide extra data to JSON or template output
- fm.plain() to show raw text that isn't provided to JSON or template output

To show structured data (e.g. date tuples, dicts, lists), apply fm.format*()
beforehand so the data is converted to the appropriate data type. Use
fm.isplain() if you need to convert or format data conditionally which isn't
supported by the formatter API.

To build nested structure (i.e. a list of dicts), use fm.nested().

See also https://www.mercurial-scm.org/wiki/GenericTemplatingPlan

fm.condwrite() vs 'if cond:':

In most cases, use fm.condwrite() so users can selectively show the data
in template output. If it's costly to build data, use plain 'if cond:' with
fm.write().

fm.nested() vs fm.formatdict() (or fm.formatlist()):

fm.nested() should be used to form a tree structure (a list of dicts of
lists of dicts...) which can be accessed through template keywords, e.g.
"{foo % "{bar % {...}} {baz % {...}}"}". On the other hand, fm.formatdict()
exports a dict-type object to template, which can be accessed by e.g.
"{get(foo, key)}" function.

Doctest helper:

>>> def show(fn, verbose=False, **opts):
...     import sys
...     from . import ui as uimod
...     ui = uimod.ui()
...     ui.verbose = verbose
...     ui.pushbuffer()
...     try:
...         return fn(ui, ui.formatter(pycompat.sysbytes(fn.__name__),
...                   pycompat.byteskwargs(opts)))
...     finally:
...         print(pycompat.sysstr(ui.popbuffer()), end='')

Basic example:

>>> def files(ui, fm):
...     files = [(b'foo', 123, (0, 0)), (b'bar', 456, (1, 0))]
...     for f in files:
...         fm.startitem()
...         fm.write(b'path', b'%s', f[0])
...         fm.condwrite(ui.verbose, b'date', b'  %s',
...                      fm.formatdate(f[2], b'%Y-%m-%d %H:%M:%S'))
...         fm.data(size=f[1])
...         fm.plain(b'\\n')
...     fm.end()
>>> show(files)
foo
bar
>>> show(files, verbose=True)
foo  1970-01-01 00:00:00
bar  1970-01-01 00:00:01
>>> show(files, template=b'json')
[
 {
  "date": [0, 0],
  "path": "foo",
  "size": 123
 },
 {
  "date": [1, 0],
  "path": "bar",
  "size": 456
 }
]
>>> show(files, template=b'path: {path}\\ndate: {date|rfc3339date}\\n')
path: foo
date: 1970-01-01T00:00:00+00:00
path: bar
date: 1970-01-01T00:00:01+00:00

Nested example:

>>> def subrepos(ui, fm):
...     fm.startitem()
...     fm.write(b'reponame', b'[%s]\\n', b'baz')
...     files(ui, fm.nested(b'files', tmpl=b'{reponame}'))
...     fm.end()
>>> show(subrepos)
[baz]
foo
bar
>>> show(subrepos, template=b'{reponame}: {join(files % "{path}", ", ")}\\n')
baz: foo, bar
"""


import contextlib
import itertools
import os
import pickle

from .i18n import _
from .node import (
    hex,
    short,
)
from .thirdparty import attr

from . import (
    error,
    pycompat,
    templatefilters,
    templatekw,
    templater,
    templateutil,
    util,
)
from .utils import (
    cborutil,
    dateutil,
    stringutil,
)


def isprintable(obj):
    """Check if the given object can be directly passed in to formatter's
    write() and data() functions

    Returns False if the object is unsupported or must be pre-processed by
    formatdate(), formatdict(), or formatlist().
    """
    return isinstance(obj, (type(None), bool, int, int, float, bytes))


class _nullconverter:
    '''convert non-primitive data types to be processed by formatter'''

    # set to True if context object should be stored as item
    storecontext = False

    @staticmethod
    def wrapnested(data, tmpl, sep):
        '''wrap nested data by appropriate type'''
        return data

    @staticmethod
    def formatdate(date, fmt):
        '''convert date tuple to appropriate format'''
        # timestamp can be float, but the canonical form should be int
        ts, tz = date
        return (int(ts), tz)

    @staticmethod
    def formatdict(data, key, value, fmt, sep):
        '''convert dict or key-value pairs to appropriate dict format'''
        # use plain dict instead of util.sortdict so that data can be
        # serialized as a builtin dict in pickle output
        return dict(data)

    @staticmethod
    def formatlist(data, name, fmt, sep):
        '''convert iterable to appropriate list format'''
        return list(data)


class baseformatter:

    # set to True if the formater output a strict format that does not support
    # arbitrary output in the stream.
    strict_format = False

    def __init__(self, ui, topic, opts, converter):
        self._ui = ui
        self._topic = topic
        self._opts = opts
        self._converter = converter
        self._item = None
        # function to convert node to string suitable for this output
        self.hexfunc = hex

    def __enter__(self):
        return self

    def __exit__(self, exctype, excvalue, traceback):
        if exctype is None:
            self.end()

    def _showitem(self):
        '''show a formatted item once all data is collected'''

    def startitem(self):
        '''begin an item in the format list'''
        if self._item is not None:
            self._showitem()
        self._item = {}

    def formatdate(self, date, fmt=b'%a %b %d %H:%M:%S %Y %1%2'):
        '''convert date tuple to appropriate format'''
        return self._converter.formatdate(date, fmt)

    def formatdict(self, data, key=b'key', value=b'value', fmt=None, sep=b' '):
        '''convert dict or key-value pairs to appropriate dict format'''
        return self._converter.formatdict(data, key, value, fmt, sep)

    def formatlist(self, data, name, fmt=None, sep=b' '):
        '''convert iterable to appropriate list format'''
        # name is mandatory argument for now, but it could be optional if
        # we have default template keyword, e.g. {item}
        return self._converter.formatlist(data, name, fmt, sep)

    def context(self, **ctxs):
        '''insert context objects to be used to render template keywords'''
        ctxs = pycompat.byteskwargs(ctxs)
        assert all(k in {b'repo', b'ctx', b'fctx'} for k in ctxs)
        if self._converter.storecontext:
            # populate missing resources in fctx -> ctx -> repo order
            if b'fctx' in ctxs and b'ctx' not in ctxs:
                ctxs[b'ctx'] = ctxs[b'fctx'].changectx()
            if b'ctx' in ctxs and b'repo' not in ctxs:
                ctxs[b'repo'] = ctxs[b'ctx'].repo()
            self._item.update(ctxs)

    def datahint(self):
        '''set of field names to be referenced'''
        return set()

    def data(self, **data):
        '''insert data into item that's not shown in default output'''
        data = pycompat.byteskwargs(data)
        self._item.update(data)

    def write(self, fields, deftext, *fielddata, **opts):
        '''do default text output while assigning data to item'''
        fieldkeys = fields.split()
        assert len(fieldkeys) == len(fielddata), (fieldkeys, fielddata)
        self._item.update(zip(fieldkeys, fielddata))

    def condwrite(self, cond, fields, deftext, *fielddata, **opts):
        '''do conditional write (primarily for plain formatter)'''
        fieldkeys = fields.split()
        assert len(fieldkeys) == len(fielddata)
        self._item.update(zip(fieldkeys, fielddata))

    def plain(self, text, **opts):
        '''show raw text for non-templated mode'''

    def isplain(self):
        '''check for plain formatter usage'''
        return False

    def nested(self, field, tmpl=None, sep=b''):
        '''sub formatter to store nested data in the specified field'''
        data = []
        self._item[field] = self._converter.wrapnested(data, tmpl, sep)
        return _nestedformatter(self._ui, self._converter, data)

    def end(self):
        '''end output for the formatter'''
        if self._item is not None:
            self._showitem()


def nullformatter(ui, topic, opts):
    '''formatter that prints nothing'''
    return baseformatter(ui, topic, opts, converter=_nullconverter)


class _nestedformatter(baseformatter):
    '''build sub items and store them in the parent formatter'''

    def __init__(self, ui, converter, data):
        baseformatter.__init__(
            self, ui, topic=b'', opts={}, converter=converter
        )
        self._data = data

    def _showitem(self):
        self._data.append(self._item)


def _iteritems(data):
    '''iterate key-value pairs in stable order'''
    if isinstance(data, dict):
        return sorted(data.items())
    return data


class _plainconverter:
    '''convert non-primitive data types to text'''

    storecontext = False

    @staticmethod
    def wrapnested(data, tmpl, sep):
        raise error.ProgrammingError(b'plainformatter should never be nested')

    @staticmethod
    def formatdate(date, fmt):
        '''stringify date tuple in the given format'''
        return dateutil.datestr(date, fmt)

    @staticmethod
    def formatdict(data, key, value, fmt, sep):
        '''stringify key-value pairs separated by sep'''
        prefmt = pycompat.identity
        if fmt is None:
            fmt = b'%s=%s'
            prefmt = pycompat.bytestr
        return sep.join(
            fmt % (prefmt(k), prefmt(v)) for k, v in _iteritems(data)
        )

    @staticmethod
    def formatlist(data, name, fmt, sep):
        '''stringify iterable separated by sep'''
        prefmt = pycompat.identity
        if fmt is None:
            fmt = b'%s'
            prefmt = pycompat.bytestr
        return sep.join(fmt % prefmt(e) for e in data)


class plainformatter(baseformatter):
    '''the default text output scheme'''

    def __init__(self, ui, out, topic, opts):
        baseformatter.__init__(self, ui, topic, opts, _plainconverter)
        if ui.debugflag:
            self.hexfunc = hex
        else:
            self.hexfunc = short
        if ui is out:
            self._write = ui.write
        else:
            self._write = lambda s, **opts: out.write(s)

    def startitem(self):
        pass

    def data(self, **data):
        pass

    def write(self, fields, deftext, *fielddata, **opts):
        self._write(deftext % fielddata, **opts)

    def condwrite(self, cond, fields, deftext, *fielddata, **opts):
        '''do conditional write'''
        if cond:
            self._write(deftext % fielddata, **opts)

    def plain(self, text, **opts):
        self._write(text, **opts)

    def isplain(self):
        return True

    def nested(self, field, tmpl=None, sep=b''):
        # nested data will be directly written to ui
        return self

    def end(self):
        pass


class debugformatter(baseformatter):
    def __init__(self, ui, out, topic, opts):
        baseformatter.__init__(self, ui, topic, opts, _nullconverter)
        self._out = out
        self._out.write(b"%s = [\n" % self._topic)

    def _showitem(self):
        self._out.write(
            b'    %s,\n' % stringutil.pprint(self._item, indent=4, level=1)
        )

    def end(self):
        baseformatter.end(self)
        self._out.write(b"]\n")


class pickleformatter(baseformatter):
    def __init__(self, ui, out, topic, opts):
        baseformatter.__init__(self, ui, topic, opts, _nullconverter)
        self._out = out
        self._data = []

    def _showitem(self):
        self._data.append(self._item)

    def end(self):
        baseformatter.end(self)
        self._out.write(pickle.dumps(self._data))


class cborformatter(baseformatter):
    '''serialize items as an indefinite-length CBOR array'''

    def __init__(self, ui, out, topic, opts):
        baseformatter.__init__(self, ui, topic, opts, _nullconverter)
        self._out = out
        self._out.write(cborutil.BEGIN_INDEFINITE_ARRAY)

    def _showitem(self):
        self._out.write(b''.join(cborutil.streamencode(self._item)))

    def end(self):
        baseformatter.end(self)
        self._out.write(cborutil.BREAK)


class jsonformatter(baseformatter):

    strict_format = True

    def __init__(self, ui, out, topic, opts):
        baseformatter.__init__(self, ui, topic, opts, _nullconverter)
        self._out = out
        self._out.write(b"[")
        self._first = True

    def _showitem(self):
        if self._first:
            self._first = False
        else:
            self._out.write(b",")

        self._out.write(b"\n {\n")
        first = True
        for k, v in sorted(self._item.items()):
            if first:
                first = False
            else:
                self._out.write(b",\n")
            u = templatefilters.json(v, paranoid=False)
            self._out.write(b'  "%s": %s' % (k, u))
        self._out.write(b"\n }")

    def end(self):
        baseformatter.end(self)
        self._out.write(b"\n]\n")


class _templateconverter:
    '''convert non-primitive data types to be processed by templater'''

    storecontext = True

    @staticmethod
    def wrapnested(data, tmpl, sep):
        '''wrap nested data by templatable type'''
        return templateutil.mappinglist(data, tmpl=tmpl, sep=sep)

    @staticmethod
    def formatdate(date, fmt):
        '''return date tuple'''
        return templateutil.date(date)

    @staticmethod
    def formatdict(data, key, value, fmt, sep):
        '''build object that can be evaluated as either plain string or dict'''
        data = util.sortdict(_iteritems(data))

        def f():
            yield _plainconverter.formatdict(data, key, value, fmt, sep)

        return templateutil.hybriddict(
            data, key=key, value=value, fmt=fmt, gen=f
        )

    @staticmethod
    def formatlist(data, name, fmt, sep):
        '''build object that can be evaluated as either plain string or list'''
        data = list(data)

        def f():
            yield _plainconverter.formatlist(data, name, fmt, sep)

        return templateutil.hybridlist(data, name=name, fmt=fmt, gen=f)


class templateformatter(baseformatter):
    def __init__(self, ui, out, topic, opts, spec, overridetemplates=None):
        baseformatter.__init__(self, ui, topic, opts, _templateconverter)
        self._out = out
        self._tref = spec.ref
        self._t = loadtemplater(
            ui,
            spec,
            defaults=templatekw.keywords,
            resources=templateresources(ui),
            cache=templatekw.defaulttempl,
        )
        if overridetemplates:
            self._t.cache.update(overridetemplates)
        self._parts = templatepartsmap(
            spec, self._t, [b'docheader', b'docfooter', b'separator']
        )
        self._counter = itertools.count()
        self._renderitem(b'docheader', {})

    def _showitem(self):
        item = self._item.copy()
        item[b'index'] = index = next(self._counter)
        if index > 0:
            self._renderitem(b'separator', {})
        self._renderitem(self._tref, item)

    def _renderitem(self, part, item):
        if part not in self._parts:
            return
        ref = self._parts[part]
        # None can't be put in the mapping dict since it means <unset>
        for k, v in item.items():
            if v is None:
                item[k] = templateutil.wrappedvalue(v)
        self._out.write(self._t.render(ref, item))

    @util.propertycache
    def _symbolsused(self):
        return self._t.symbolsused(self._tref)

    def datahint(self):
        '''set of field names to be referenced from the template'''
        return self._symbolsused[0]

    def end(self):
        baseformatter.end(self)
        self._renderitem(b'docfooter', {})


@attr.s(frozen=True)
class templatespec:
    ref = attr.ib()
    tmpl = attr.ib()
    mapfile = attr.ib()
    refargs = attr.ib(default=None)
    fp = attr.ib(default=None)


def empty_templatespec():
    return templatespec(None, None, None)


def reference_templatespec(ref, refargs=None):
    return templatespec(ref, None, None, refargs)


def literal_templatespec(tmpl):
    assert not isinstance(tmpl, str), b'tmpl must not be a str'
    return templatespec(b'', tmpl, None)


def mapfile_templatespec(topic, mapfile, fp=None):
    return templatespec(topic, None, mapfile, fp=fp)


def lookuptemplate(ui, topic, tmpl):
    """Find the template matching the given -T/--template spec 'tmpl'

    'tmpl' can be any of the following:

     - a literal template (e.g. '{rev}')
     - a reference to built-in template (i.e. formatter)
     - a map-file name or path (e.g. 'changelog')
     - a reference to [templates] in config file
     - a path to raw template file

    A map file defines a stand-alone template environment. If a map file
    selected, all templates defined in the file will be loaded, and the
    template matching the given topic will be rendered. Aliases won't be
    loaded from user config, but from the map file.

    If no map file selected, all templates in [templates] section will be
    available as well as aliases in [templatealias].
    """

    if not tmpl:
        return empty_templatespec()

    # looks like a literal template?
    if b'{' in tmpl:
        return literal_templatespec(tmpl)

    # a reference to built-in (formatter) template
    if tmpl in {b'cbor', b'json', b'pickle', b'debug'}:
        return reference_templatespec(tmpl)

    # a function-style reference to built-in template
    func, fsep, ftail = tmpl.partition(b'(')
    if func in {b'cbor', b'json'} and fsep and ftail.endswith(b')'):
        templater.parseexpr(tmpl)  # make sure syntax errors are confined
        return reference_templatespec(func, refargs=ftail[:-1])

    # perhaps a stock style?
    if not os.path.split(tmpl)[0]:
        (mapname, fp) = templater.try_open_template(
            b'map-cmdline.' + tmpl
        ) or templater.try_open_template(tmpl)
        if mapname:
            return mapfile_templatespec(topic, mapname, fp)

    # perhaps it's a reference to [templates]
    if ui.config(b'templates', tmpl):
        return reference_templatespec(tmpl)

    if tmpl == b'list':
        ui.write(_(b"available styles: %s\n") % templater.stylelist())
        raise error.Abort(_(b"specify a template"))

    # perhaps it's a path to a map or a template
    if (b'/' in tmpl or b'\\' in tmpl) and os.path.isfile(tmpl):
        # is it a mapfile for a style?
        if os.path.basename(tmpl).startswith(b"map-"):
            return mapfile_templatespec(topic, os.path.realpath(tmpl))
        with util.posixfile(tmpl, b'rb') as f:
            tmpl = f.read()
        return literal_templatespec(tmpl)

    # constant string?
    return literal_templatespec(tmpl)


def templatepartsmap(spec, t, partnames):
    """Create a mapping of {part: ref}"""
    partsmap = {spec.ref: spec.ref}  # initial ref must exist in t
    if spec.mapfile:
        partsmap.update((p, p) for p in partnames if p in t)
    elif spec.ref:
        for part in partnames:
            ref = b'%s:%s' % (spec.ref, part)  # select config sub-section
            if ref in t:
                partsmap[part] = ref
    return partsmap


def loadtemplater(ui, spec, defaults=None, resources=None, cache=None):
    """Create a templater from either a literal template or loading from
    a map file"""
    assert not (spec.tmpl and spec.mapfile)
    if spec.mapfile:
        return templater.templater.frommapfile(
            spec.mapfile,
            spec.fp,
            defaults=defaults,
            resources=resources,
            cache=cache,
        )
    return maketemplater(
        ui, spec.tmpl, defaults=defaults, resources=resources, cache=cache
    )


def maketemplater(ui, tmpl, defaults=None, resources=None, cache=None):
    """Create a templater from a string template 'tmpl'"""
    aliases = ui.configitems(b'templatealias')
    t = templater.templater(
        defaults=defaults, resources=resources, cache=cache, aliases=aliases
    )
    t.cache.update(
        (k, templater.unquotestring(v)) for k, v in ui.configitems(b'templates')
    )
    if tmpl:
        t.cache[b''] = tmpl
    return t


# marker to denote a resource to be loaded on demand based on mapping values
# (e.g. (ctx, path) -> fctx)
_placeholder = object()


class templateresources(templater.resourcemapper):
    """Resource mapper designed for the default templatekw and function"""

    def __init__(self, ui, repo=None):
        self._resmap = {
            b'cache': {},  # for templatekw/funcs to store reusable data
            b'repo': repo,
            b'ui': ui,
        }

    def availablekeys(self, mapping):
        return {
            k for k in self.knownkeys() if self._getsome(mapping, k) is not None
        }

    def knownkeys(self):
        return {b'cache', b'ctx', b'fctx', b'repo', b'revcache', b'ui'}

    def lookup(self, mapping, key):
        if key not in self.knownkeys():
            return None
        v = self._getsome(mapping, key)
        if v is _placeholder:
            v = mapping[key] = self._loadermap[key](self, mapping)
        return v

    def populatemap(self, context, origmapping, newmapping):
        mapping = {}
        if self._hasnodespec(newmapping):
            mapping[b'revcache'] = {}  # per-ctx cache
        if self._hasnodespec(origmapping) and self._hasnodespec(newmapping):
            orignode = templateutil.runsymbol(context, origmapping, b'node')
            mapping[b'originalnode'] = orignode
        # put marker to override 'ctx'/'fctx' in mapping if any, and flag
        # its existence to be reported by availablekeys()
        if b'ctx' not in newmapping and self._hasliteral(newmapping, b'node'):
            mapping[b'ctx'] = _placeholder
        if b'fctx' not in newmapping and self._hasliteral(newmapping, b'path'):
            mapping[b'fctx'] = _placeholder
        return mapping

    def _getsome(self, mapping, key):
        v = mapping.get(key)
        if v is not None:
            return v
        return self._resmap.get(key)

    def _hasliteral(self, mapping, key):
        """Test if a literal value is set or unset in the given mapping"""
        return key in mapping and not callable(mapping[key])

    def _getliteral(self, mapping, key):
        """Return value of the given name if it is a literal"""
        v = mapping.get(key)
        if callable(v):
            return None
        return v

    def _hasnodespec(self, mapping):
        """Test if context revision is set or unset in the given mapping"""
        return b'node' in mapping or b'ctx' in mapping

    def _loadctx(self, mapping):
        repo = self._getsome(mapping, b'repo')
        node = self._getliteral(mapping, b'node')
        if repo is None or node is None:
            return
        try:
            return repo[node]
        except error.RepoLookupError:
            return None  # maybe hidden/non-existent node

    def _loadfctx(self, mapping):
        ctx = self._getsome(mapping, b'ctx')
        path = self._getliteral(mapping, b'path')
        if ctx is None or path is None:
            return None
        try:
            return ctx[path]
        except error.LookupError:
            return None  # maybe removed file?

    _loadermap = {
        b'ctx': _loadctx,
        b'fctx': _loadfctx,
    }


def _internaltemplateformatter(
    ui,
    out,
    topic,
    opts,
    spec,
    tmpl,
    docheader=b'',
    docfooter=b'',
    separator=b'',
):
    """Build template formatter that handles customizable built-in templates
    such as -Tjson(...)"""
    templates = {spec.ref: tmpl}
    if docheader:
        templates[b'%s:docheader' % spec.ref] = docheader
    if docfooter:
        templates[b'%s:docfooter' % spec.ref] = docfooter
    if separator:
        templates[b'%s:separator' % spec.ref] = separator
    return templateformatter(
        ui, out, topic, opts, spec, overridetemplates=templates
    )


def formatter(ui, out, topic, opts):
    spec = lookuptemplate(ui, topic, opts.get(b'template', b''))
    if spec.ref == b"cbor" and spec.refargs is not None:
        return _internaltemplateformatter(
            ui,
            out,
            topic,
            opts,
            spec,
            tmpl=b'{dict(%s)|cbor}' % spec.refargs,
            docheader=cborutil.BEGIN_INDEFINITE_ARRAY,
            docfooter=cborutil.BREAK,
        )
    elif spec.ref == b"cbor":
        return cborformatter(ui, out, topic, opts)
    elif spec.ref == b"json" and spec.refargs is not None:
        return _internaltemplateformatter(
            ui,
            out,
            topic,
            opts,
            spec,
            tmpl=b'{dict(%s)|json}' % spec.refargs,
            docheader=b'[\n ',
            docfooter=b'\n]\n',
            separator=b',\n ',
        )
    elif spec.ref == b"json":
        return jsonformatter(ui, out, topic, opts)
    elif spec.ref == b"pickle":
        assert spec.refargs is None, r'function-style not supported'
        return pickleformatter(ui, out, topic, opts)
    elif spec.ref == b"debug":
        assert spec.refargs is None, r'function-style not supported'
        return debugformatter(ui, out, topic, opts)
    elif spec.ref or spec.tmpl or spec.mapfile:
        assert spec.refargs is None, r'function-style not supported'
        return templateformatter(ui, out, topic, opts, spec)
    # developer config: ui.formatdebug
    elif ui.configbool(b'ui', b'formatdebug'):
        return debugformatter(ui, out, topic, opts)
    # deprecated config: ui.formatjson
    elif ui.configbool(b'ui', b'formatjson'):
        return jsonformatter(ui, out, topic, opts)
    return plainformatter(ui, out, topic, opts)


@contextlib.contextmanager
def openformatter(ui, filename, topic, opts):
    """Create a formatter that writes outputs to the specified file

    Must be invoked using the 'with' statement.
    """
    with util.posixfile(filename, b'wb') as out:
        with formatter(ui, out, topic, opts) as fm:
            yield fm


@contextlib.contextmanager
def _neverending(fm):
    yield fm


def maybereopen(fm, filename):
    """Create a formatter backed by file if filename specified, else return
    the given formatter

    Must be invoked using the 'with' statement. This will never call fm.end()
    of the given formatter.
    """
    if filename:
        return openformatter(fm._ui, filename, fm._topic, fm._opts)
    else:
        return _neverending(fm)
