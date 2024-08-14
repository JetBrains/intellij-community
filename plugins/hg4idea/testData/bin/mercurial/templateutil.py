# templateutil.py - utility for template evaluation
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import abc
import types

from .i18n import _
from . import (
    error,
    pycompat,
    smartset,
    util,
)
from .utils import (
    dateutil,
    stringutil,
)


class ResourceUnavailable(error.Abort):
    pass


class TemplateNotFound(error.Abort):
    pass


class wrapped:  # pytype: disable=ignored-metaclass
    """Object requiring extra conversion prior to displaying or processing
    as value

    Use unwrapvalue() or unwrapastype() to obtain the inner object.
    """

    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def contains(self, context, mapping, item):
        """Test if the specified item is in self

        The item argument may be a wrapped object.
        """

    @abc.abstractmethod
    def getmember(self, context, mapping, key):
        """Return a member item for the specified key

        The key argument may be a wrapped object.
        A returned object may be either a wrapped object or a pure value
        depending on the self type.
        """

    @abc.abstractmethod
    def getmin(self, context, mapping):
        """Return the smallest item, which may be either a wrapped or a pure
        value depending on the self type"""

    @abc.abstractmethod
    def getmax(self, context, mapping):
        """Return the largest item, which may be either a wrapped or a pure
        value depending on the self type"""

    @abc.abstractmethod
    def filter(self, context, mapping, select):
        """Return new container of the same type which includes only the
        selected elements

        select() takes each item as a wrapped object and returns True/False.
        """

    @abc.abstractmethod
    def itermaps(self, context):
        """Yield each template mapping"""

    @abc.abstractmethod
    def join(self, context, mapping, sep):
        """Join items with the separator; Returns a bytes or (possibly nested)
        generator of bytes

        A pre-configured template may be rendered per item if this container
        holds unprintable items.
        """

    @abc.abstractmethod
    def show(self, context, mapping):
        """Return a bytes or (possibly nested) generator of bytes representing
        the underlying object

        A pre-configured template may be rendered if the underlying object is
        not printable.
        """

    @abc.abstractmethod
    def tobool(self, context, mapping):
        """Return a boolean representation of the inner value"""

    @abc.abstractmethod
    def tovalue(self, context, mapping):
        """Move the inner value object out or create a value representation

        A returned value must be serializable by templaterfilters.json().
        """


class mappable:  # pytype: disable=ignored-metaclass
    """Object which can be converted to a single template mapping"""

    __metaclass__ = abc.ABCMeta

    def itermaps(self, context):
        yield self.tomap(context)

    @abc.abstractmethod
    def tomap(self, context):
        """Create a single template mapping representing this"""


class wrappedbytes(wrapped):
    """Wrapper for byte string"""

    def __init__(self, value):
        self._value = value

    def contains(self, context, mapping, item):
        item = stringify(context, mapping, item)
        return item in self._value

    def getmember(self, context, mapping, key):
        raise error.ParseError(
            _(b'%r is not a dictionary') % pycompat.bytestr(self._value)
        )

    def getmin(self, context, mapping):
        return self._getby(context, mapping, min)

    def getmax(self, context, mapping):
        return self._getby(context, mapping, max)

    def _getby(self, context, mapping, func):
        if not self._value:
            raise error.ParseError(_(b'empty string'))
        return func(pycompat.iterbytestr(self._value))

    def filter(self, context, mapping, select):
        raise error.ParseError(
            _(b'%r is not filterable') % pycompat.bytestr(self._value)
        )

    def itermaps(self, context):
        raise error.ParseError(
            _(b'%r is not iterable of mappings') % pycompat.bytestr(self._value)
        )

    def join(self, context, mapping, sep):
        return joinitems(pycompat.iterbytestr(self._value), sep)

    def show(self, context, mapping):
        return self._value

    def tobool(self, context, mapping):
        return bool(self._value)

    def tovalue(self, context, mapping):
        return self._value


class wrappedvalue(wrapped):
    """Generic wrapper for pure non-list/dict/bytes value"""

    def __init__(self, value):
        self._value = value

    def contains(self, context, mapping, item):
        raise error.ParseError(_(b"%r is not iterable") % self._value)

    def getmember(self, context, mapping, key):
        raise error.ParseError(_(b'%r is not a dictionary') % self._value)

    def getmin(self, context, mapping):
        raise error.ParseError(_(b"%r is not iterable") % self._value)

    def getmax(self, context, mapping):
        raise error.ParseError(_(b"%r is not iterable") % self._value)

    def filter(self, context, mapping, select):
        raise error.ParseError(_(b"%r is not iterable") % self._value)

    def itermaps(self, context):
        raise error.ParseError(
            _(b'%r is not iterable of mappings') % self._value
        )

    def join(self, context, mapping, sep):
        raise error.ParseError(_(b'%r is not iterable') % self._value)

    def show(self, context, mapping):
        if self._value is None:
            return b''
        return pycompat.bytestr(self._value)

    def tobool(self, context, mapping):
        if self._value is None:
            return False
        if isinstance(self._value, bool):
            return self._value
        # otherwise evaluate as string, which means 0 is True
        return bool(pycompat.bytestr(self._value))

    def tovalue(self, context, mapping):
        return self._value


class date(mappable, wrapped):
    """Wrapper for date tuple"""

    def __init__(self, value, showfmt=b'%d %d'):
        # value may be (float, int), but public interface shouldn't support
        # floating-point timestamp
        self._unixtime, self._tzoffset = map(int, value)
        self._showfmt = showfmt

    def contains(self, context, mapping, item):
        raise error.ParseError(_(b'date is not iterable'))

    def getmember(self, context, mapping, key):
        raise error.ParseError(_(b'date is not a dictionary'))

    def getmin(self, context, mapping):
        raise error.ParseError(_(b'date is not iterable'))

    def getmax(self, context, mapping):
        raise error.ParseError(_(b'date is not iterable'))

    def filter(self, context, mapping, select):
        raise error.ParseError(_(b'date is not iterable'))

    def join(self, context, mapping, sep):
        raise error.ParseError(_(b"date is not iterable"))

    def show(self, context, mapping):
        return self._showfmt % (self._unixtime, self._tzoffset)

    def tomap(self, context):
        return {b'unixtime': self._unixtime, b'tzoffset': self._tzoffset}

    def tobool(self, context, mapping):
        return True

    def tovalue(self, context, mapping):
        return (self._unixtime, self._tzoffset)


class hybrid(wrapped):
    """Wrapper for list or dict to support legacy template

    This class allows us to handle both:
    - "{files}" (legacy command-line-specific list hack) and
    - "{files % '{file}\n'}" (hgweb-style with inlining and function support)
    and to access raw values:
    - "{ifcontains(file, files, ...)}", "{ifcontains(key, extras, ...)}"
    - "{get(extras, key)}"
    - "{files|json}"
    """

    def __init__(self, gen, values, makemap, joinfmt, keytype=None):
        self._gen = gen  # generator or function returning generator
        self._values = values
        self._makemap = makemap
        self._joinfmt = joinfmt
        self._keytype = keytype  # hint for 'x in y' where type(x) is unresolved

    def contains(self, context, mapping, item):
        item = unwrapastype(context, mapping, item, self._keytype)
        return item in self._values

    def getmember(self, context, mapping, key):
        # TODO: maybe split hybrid list/dict types?
        if not hasattr(self._values, 'get'):
            raise error.ParseError(_(b'not a dictionary'))
        key = unwrapastype(context, mapping, key, self._keytype)
        return self._wrapvalue(key, self._values.get(key))

    def getmin(self, context, mapping):
        return self._getby(context, mapping, min)

    def getmax(self, context, mapping):
        return self._getby(context, mapping, max)

    def _getby(self, context, mapping, func):
        if not self._values:
            raise error.ParseError(_(b'empty sequence'))
        val = func(self._values)
        return self._wrapvalue(val, val)

    def _wrapvalue(self, key, val):
        if val is None:
            return
        if hasattr(val, '_makemap'):
            # a nested hybrid list/dict, which has its own way of map operation
            return val
        return hybriditem(None, key, val, self._makemap)

    def filter(self, context, mapping, select):
        if hasattr(self._values, 'get'):
            values = {
                k: v
                for k, v in self._values.items()
                if select(self._wrapvalue(k, v))
            }
        else:
            values = [v for v in self._values if select(self._wrapvalue(v, v))]
        return hybrid(None, values, self._makemap, self._joinfmt, self._keytype)

    def itermaps(self, context):
        makemap = self._makemap
        for x in self._values:
            yield makemap(x)

    def join(self, context, mapping, sep):
        # TODO: switch gen to (context, mapping) API?
        return joinitems((self._joinfmt(x) for x in self._values), sep)

    def show(self, context, mapping):
        # TODO: switch gen to (context, mapping) API?
        gen = self._gen
        if gen is None:
            return self.join(context, mapping, b' ')
        if callable(gen):
            return gen()
        return gen

    def tobool(self, context, mapping):
        return bool(self._values)

    def tovalue(self, context, mapping):
        # TODO: make it non-recursive for trivial lists/dicts
        xs = self._values
        if hasattr(xs, 'get'):
            return {k: unwrapvalue(context, mapping, v) for k, v in xs.items()}
        return [unwrapvalue(context, mapping, x) for x in xs]


class hybriditem(mappable, wrapped):
    """Wrapper for non-list/dict object to support map operation

    This class allows us to handle both:
    - "{manifest}"
    - "{manifest % '{rev}:{node}'}"
    - "{manifest.rev}"
    """

    def __init__(self, gen, key, value, makemap):
        self._gen = gen  # generator or function returning generator
        self._key = key
        self._value = value  # may be generator of strings
        self._makemap = makemap

    def tomap(self, context):
        return self._makemap(self._key)

    def contains(self, context, mapping, item):
        w = makewrapped(context, mapping, self._value)
        return w.contains(context, mapping, item)

    def getmember(self, context, mapping, key):
        w = makewrapped(context, mapping, self._value)
        return w.getmember(context, mapping, key)

    def getmin(self, context, mapping):
        w = makewrapped(context, mapping, self._value)
        return w.getmin(context, mapping)

    def getmax(self, context, mapping):
        w = makewrapped(context, mapping, self._value)
        return w.getmax(context, mapping)

    def filter(self, context, mapping, select):
        w = makewrapped(context, mapping, self._value)
        return w.filter(context, mapping, select)

    def join(self, context, mapping, sep):
        w = makewrapped(context, mapping, self._value)
        return w.join(context, mapping, sep)

    def show(self, context, mapping):
        # TODO: switch gen to (context, mapping) API?
        gen = self._gen
        if gen is None:
            return pycompat.bytestr(self._value)
        if callable(gen):
            return gen()
        return gen

    def tobool(self, context, mapping):
        w = makewrapped(context, mapping, self._value)
        return w.tobool(context, mapping)

    def tovalue(self, context, mapping):
        return _unthunk(context, mapping, self._value)


class revslist(wrapped):
    """Wrapper for a smartset (a list/set of revision numbers)

    If name specified, the revs will be rendered with the old-style list
    template of the given name by default.

    The cachekey provides a hint to cache further computation on this
    smartset. If the underlying smartset is dynamically created, the cachekey
    should be None.
    """

    def __init__(self, repo, revs, name=None, cachekey=None):
        assert isinstance(revs, smartset.abstractsmartset)
        self._repo = repo
        self._revs = revs
        self._name = name
        self.cachekey = cachekey

    def contains(self, context, mapping, item):
        rev = unwrapinteger(context, mapping, item)
        return rev in self._revs

    def getmember(self, context, mapping, key):
        raise error.ParseError(_(b'not a dictionary'))

    def getmin(self, context, mapping):
        makehybriditem = self._makehybriditemfunc()
        return makehybriditem(self._revs.min())

    def getmax(self, context, mapping):
        makehybriditem = self._makehybriditemfunc()
        return makehybriditem(self._revs.max())

    def filter(self, context, mapping, select):
        makehybriditem = self._makehybriditemfunc()
        frevs = self._revs.filter(lambda r: select(makehybriditem(r)))
        # once filtered, no need to support old-style list template
        return revslist(self._repo, frevs, name=None)

    def itermaps(self, context):
        makemap = self._makemapfunc()
        for r in self._revs:
            yield makemap(r)

    def _makehybriditemfunc(self):
        makemap = self._makemapfunc()
        return lambda r: hybriditem(None, r, r, makemap)

    def _makemapfunc(self):
        repo = self._repo
        name = self._name
        if name:
            return lambda r: {name: r, b'ctx': repo[r]}
        else:
            return lambda r: {b'ctx': repo[r]}

    def join(self, context, mapping, sep):
        return joinitems(self._revs, sep)

    def show(self, context, mapping):
        if self._name:
            srevs = [b'%d' % r for r in self._revs]
            return _showcompatlist(context, mapping, self._name, srevs)
        else:
            return self.join(context, mapping, b' ')

    def tobool(self, context, mapping):
        return bool(self._revs)

    def tovalue(self, context, mapping):
        return self._revs


class _mappingsequence(wrapped):
    """Wrapper for sequence of template mappings

    This represents an inner template structure (i.e. a list of dicts),
    which can also be rendered by the specified named/literal template.

    Template mappings may be nested.
    """

    def __init__(self, name=None, tmpl=None, sep=b''):
        if name is not None and tmpl is not None:
            raise error.ProgrammingError(
                b'name and tmpl are mutually exclusive'
            )
        self._name = name
        self._tmpl = tmpl
        self._defaultsep = sep

    def contains(self, context, mapping, item):
        raise error.ParseError(_(b'not comparable'))

    def getmember(self, context, mapping, key):
        raise error.ParseError(_(b'not a dictionary'))

    def getmin(self, context, mapping):
        raise error.ParseError(_(b'not comparable'))

    def getmax(self, context, mapping):
        raise error.ParseError(_(b'not comparable'))

    def filter(self, context, mapping, select):
        # implement if necessary; we'll need a wrapped type for a mapping dict
        raise error.ParseError(_(b'not filterable without template'))

    def join(self, context, mapping, sep):
        mapsiter = _iteroverlaymaps(context, mapping, self.itermaps(context))
        if self._name:
            itemiter = (context.process(self._name, m) for m in mapsiter)
        elif self._tmpl:
            itemiter = (context.expand(self._tmpl, m) for m in mapsiter)
        else:
            raise error.ParseError(_(b'not displayable without template'))
        return joinitems(itemiter, sep)

    def show(self, context, mapping):
        return self.join(context, mapping, self._defaultsep)

    def tovalue(self, context, mapping):
        knownres = context.knownresourcekeys()
        items = []
        for nm in self.itermaps(context):
            # drop internal resources (recursively) which shouldn't be displayed
            lm = context.overlaymap(mapping, nm)
            items.append(
                {
                    k: unwrapvalue(context, lm, v)
                    for k, v in nm.items()
                    if k not in knownres
                }
            )
        return items


class mappinggenerator(_mappingsequence):
    """Wrapper for generator of template mappings

    The function ``make(context, *args)`` should return a generator of
    mapping dicts.
    """

    def __init__(self, make, args=(), name=None, tmpl=None, sep=b''):
        super(mappinggenerator, self).__init__(name, tmpl, sep)
        self._make = make
        self._args = args

    def itermaps(self, context):
        return self._make(context, *self._args)

    def tobool(self, context, mapping):
        return _nonempty(self.itermaps(context))


class mappinglist(_mappingsequence):
    """Wrapper for list of template mappings"""

    def __init__(self, mappings, name=None, tmpl=None, sep=b''):
        super(mappinglist, self).__init__(name, tmpl, sep)
        self._mappings = mappings

    def itermaps(self, context):
        return iter(self._mappings)

    def tobool(self, context, mapping):
        return bool(self._mappings)


class mappingdict(mappable, _mappingsequence):
    """Wrapper for a single template mapping

    This isn't a sequence in a way that the underlying dict won't be iterated
    as a dict, but shares most of the _mappingsequence functions.
    """

    def __init__(self, mapping, name=None, tmpl=None):
        super(mappingdict, self).__init__(name, tmpl)
        self._mapping = mapping

    def tomap(self, context):
        return self._mapping

    def tobool(self, context, mapping):
        # no idea when a template mapping should be considered an empty, but
        # a mapping dict should have at least one item in practice, so always
        # mark this as non-empty.
        return True

    def tovalue(self, context, mapping):
        return super(mappingdict, self).tovalue(context, mapping)[0]


class mappingnone(wrappedvalue):
    """Wrapper for None, but supports map operation

    This represents None of Optional[mappable]. It's similar to
    mapplinglist([]), but the underlying value is not [], but None.
    """

    def __init__(self):
        super(mappingnone, self).__init__(None)

    def itermaps(self, context):
        return iter([])


class mappedgenerator(wrapped):
    """Wrapper for generator of strings which acts as a list

    The function ``make(context, *args)`` should return a generator of
    byte strings, or a generator of (possibly nested) generators of byte
    strings (i.e. a generator for a list of byte strings.)
    """

    def __init__(self, make, args=()):
        self._make = make
        self._args = args

    def contains(self, context, mapping, item):
        item = stringify(context, mapping, item)
        return item in self.tovalue(context, mapping)

    def _gen(self, context):
        return self._make(context, *self._args)

    def getmember(self, context, mapping, key):
        raise error.ParseError(_(b'not a dictionary'))

    def getmin(self, context, mapping):
        return self._getby(context, mapping, min)

    def getmax(self, context, mapping):
        return self._getby(context, mapping, max)

    def _getby(self, context, mapping, func):
        xs = self.tovalue(context, mapping)
        if not xs:
            raise error.ParseError(_(b'empty sequence'))
        return func(xs)

    @staticmethod
    def _filteredgen(context, mapping, make, args, select):
        for x in make(context, *args):
            s = stringify(context, mapping, x)
            if select(wrappedbytes(s)):
                yield s

    def filter(self, context, mapping, select):
        args = (mapping, self._make, self._args, select)
        return mappedgenerator(self._filteredgen, args)

    def itermaps(self, context):
        raise error.ParseError(_(b'list of strings is not mappable'))

    def join(self, context, mapping, sep):
        return joinitems(self._gen(context), sep)

    def show(self, context, mapping):
        return self.join(context, mapping, b'')

    def tobool(self, context, mapping):
        return _nonempty(self._gen(context))

    def tovalue(self, context, mapping):
        return [stringify(context, mapping, x) for x in self._gen(context)]


def hybriddict(data, key=b'key', value=b'value', fmt=None, gen=None):
    """Wrap data to support both dict-like and string-like operations"""
    prefmt = pycompat.identity
    if fmt is None:
        fmt = b'%s=%s'
        prefmt = pycompat.bytestr
    return hybrid(
        gen,
        data,
        lambda k: {key: k, value: data[k]},
        lambda k: fmt % (prefmt(k), prefmt(data[k])),
    )


def hybridlist(data, name, fmt=None, gen=None):
    """Wrap data to support both list-like and string-like operations"""
    prefmt = pycompat.identity
    if fmt is None:
        fmt = b'%s'
        prefmt = pycompat.bytestr
    return hybrid(gen, data, lambda x: {name: x}, lambda x: fmt % prefmt(x))


def compatdict(
    context,
    mapping,
    name,
    data,
    key=b'key',
    value=b'value',
    fmt=None,
    plural=None,
    separator=b' ',
):
    """Wrap data like hybriddict(), but also supports old-style list template

    This exists for backward compatibility with the old-style template. Use
    hybriddict() for new template keywords.
    """
    c = [{key: k, value: v} for k, v in data.items()]
    f = _showcompatlist(context, mapping, name, c, plural, separator)
    return hybriddict(data, key=key, value=value, fmt=fmt, gen=f)


def compatlist(
    context,
    mapping,
    name,
    data,
    element=None,
    fmt=None,
    plural=None,
    separator=b' ',
):
    """Wrap data like hybridlist(), but also supports old-style list template

    This exists for backward compatibility with the old-style template. Use
    hybridlist() for new template keywords.
    """
    f = _showcompatlist(context, mapping, name, data, plural, separator)
    return hybridlist(data, name=element or name, fmt=fmt, gen=f)


def compatfilecopiesdict(context, mapping, name, copies):
    """Wrap list of (dest, source) file names to support old-style list
    template and field names

    This exists for backward compatibility. Use hybriddict for new template
    keywords.
    """
    # no need to provide {path} to old-style list template
    c = [{b'name': k, b'source': v} for k, v in copies]
    f = _showcompatlist(context, mapping, name, c, plural=b'file_copies')
    copies = util.sortdict(copies)
    return hybrid(
        f,
        copies,
        lambda k: {b'name': k, b'path': k, b'source': copies[k]},
        lambda k: b'%s (%s)' % (k, copies[k]),
    )


def compatfileslist(context, mapping, name, files):
    """Wrap list of file names to support old-style list template and field
    names

    This exists for backward compatibility. Use hybridlist for new template
    keywords.
    """
    f = _showcompatlist(context, mapping, name, files)
    return hybrid(
        f, files, lambda x: {b'file': x, b'path': x}, pycompat.identity
    )


def _showcompatlist(
    context, mapping, name, values, plural=None, separator=b' '
):
    """Return a generator that renders old-style list template

    name is name of key in template map.
    values is list of strings or dicts.
    plural is plural of name, if not simply name + 's'.
    separator is used to join values as a string

    expansion works like this, given name 'foo'.

    if values is empty, expand 'no_foos'.

    if 'foo' not in template map, return values as a string,
    joined by 'separator'.

    expand 'start_foos'.

    for each value, expand 'foo'. if 'last_foo' in template
    map, expand it instead of 'foo' for last key.

    expand 'end_foos'.
    """
    if not plural:
        plural = name + b's'
    if not values:
        noname = b'no_' + plural
        if context.preload(noname):
            yield context.process(noname, mapping)
        return
    if not context.preload(name):
        if isinstance(values[0], bytes):
            yield separator.join(values)
        else:
            for v in values:
                r = dict(v)
                r.update(mapping)
                yield r
        return
    startname = b'start_' + plural
    if context.preload(startname):
        yield context.process(startname, mapping)

    def one(v, tag=name):
        vmapping = {}
        try:
            vmapping.update(v)
        # Python 2 raises ValueError if the type of v is wrong. Python
        # 3 raises TypeError.
        except (AttributeError, TypeError, ValueError):
            try:
                # Python 2 raises ValueError trying to destructure an e.g.
                # bytes. Python 3 raises TypeError.
                for a, b in v:
                    vmapping[a] = b
            except (TypeError, ValueError):
                vmapping[name] = v
        vmapping = context.overlaymap(mapping, vmapping)
        return context.process(tag, vmapping)

    lastname = b'last_' + name
    if context.preload(lastname):
        last = values.pop()
    else:
        last = None
    for v in values:
        yield one(v)
    if last is not None:
        yield one(last, tag=lastname)
    endname = b'end_' + plural
    if context.preload(endname):
        yield context.process(endname, mapping)


def flatten(context, mapping, thing):
    """Yield a single stream from a possibly nested set of iterators"""
    if isinstance(thing, wrapped):
        thing = thing.show(context, mapping)
    if isinstance(thing, bytes):
        yield thing
    elif isinstance(thing, str):
        # We can only hit this on Python 3, and it's here to guard
        # against infinite recursion.
        raise error.ProgrammingError(
            b'Mercurial IO including templates is done'
            b' with bytes, not strings, got %r' % thing
        )
    elif thing is None:
        pass
    elif not hasattr(thing, '__iter__'):
        yield pycompat.bytestr(thing)
    else:
        for i in thing:
            if isinstance(i, wrapped):
                i = i.show(context, mapping)
            if isinstance(i, bytes):
                yield i
            elif i is None:
                pass
            elif not hasattr(i, '__iter__'):
                yield pycompat.bytestr(i)
            else:
                for j in flatten(context, mapping, i):
                    yield j


def stringify(context, mapping, thing):
    """Turn values into bytes by converting into text and concatenating them"""
    if isinstance(thing, bytes):
        return thing  # retain localstr to be round-tripped
    return b''.join(flatten(context, mapping, thing))


def findsymbolicname(arg):
    """Find symbolic name for the given compiled expression; returns None
    if nothing found reliably"""
    while True:
        func, data = arg
        if func is runsymbol:
            return data
        elif func is runfilter:
            arg = data[0]
        else:
            return None


def _nonempty(xiter):
    try:
        next(xiter)
        return True
    except StopIteration:
        return False


def _unthunk(context, mapping, thing):
    """Evaluate a lazy byte string into value"""
    if not isinstance(thing, types.GeneratorType):
        return thing
    return stringify(context, mapping, thing)


def evalrawexp(context, mapping, arg):
    """Evaluate given argument as a bare template object which may require
    further processing (such as folding generator of strings)"""
    func, data = arg
    return func(context, mapping, data)


def evalwrapped(context, mapping, arg):
    """Evaluate given argument to wrapped object"""
    thing = evalrawexp(context, mapping, arg)
    return makewrapped(context, mapping, thing)


def makewrapped(context, mapping, thing):
    """Lift object to a wrapped type"""
    if isinstance(thing, wrapped):
        return thing
    thing = _unthunk(context, mapping, thing)
    if isinstance(thing, bytes):
        return wrappedbytes(thing)
    return wrappedvalue(thing)


def evalfuncarg(context, mapping, arg):
    """Evaluate given argument as value type"""
    return unwrapvalue(context, mapping, evalrawexp(context, mapping, arg))


def unwrapvalue(context, mapping, thing):
    """Move the inner value object out of the wrapper"""
    if isinstance(thing, wrapped):
        return thing.tovalue(context, mapping)
    # evalrawexp() may return string, generator of strings or arbitrary object
    # such as date tuple, but filter does not want generator.
    return _unthunk(context, mapping, thing)


def evalboolean(context, mapping, arg):
    """Evaluate given argument as boolean, but also takes boolean literals"""
    func, data = arg
    if func is runsymbol:
        thing = func(context, mapping, data, default=None)
        if thing is None:
            # not a template keyword, takes as a boolean literal
            thing = stringutil.parsebool(data)
    else:
        thing = func(context, mapping, data)
    return makewrapped(context, mapping, thing).tobool(context, mapping)


def evaldate(context, mapping, arg, err=None):
    """Evaluate given argument as a date tuple or a date string; returns
    a (unixtime, offset) tuple"""
    thing = evalrawexp(context, mapping, arg)
    return unwrapdate(context, mapping, thing, err)


def unwrapdate(context, mapping, thing, err=None):
    if isinstance(thing, date):
        return thing.tovalue(context, mapping)
    # TODO: update hgweb to not return bare tuple; then just stringify 'thing'
    thing = unwrapvalue(context, mapping, thing)
    try:
        return dateutil.parsedate(thing)
    except AttributeError:
        raise error.ParseError(err or _(b'not a date tuple nor a string'))
    except error.ParseError:
        if not err:
            raise
        raise error.ParseError(err)


def evalinteger(context, mapping, arg, err=None):
    thing = evalrawexp(context, mapping, arg)
    return unwrapinteger(context, mapping, thing, err)


def unwrapinteger(context, mapping, thing, err=None):
    thing = unwrapvalue(context, mapping, thing)
    try:
        return int(thing)
    except (TypeError, ValueError):
        raise error.ParseError(err or _(b'not an integer'))


def evalstring(context, mapping, arg):
    return stringify(context, mapping, evalrawexp(context, mapping, arg))


def evalstringliteral(context, mapping, arg):
    """Evaluate given argument as string template, but returns symbol name
    if it is unknown"""
    func, data = arg
    if func is runsymbol:
        thing = func(context, mapping, data, default=data)
    else:
        thing = func(context, mapping, data)
    return stringify(context, mapping, thing)


_unwrapfuncbytype = {
    None: unwrapvalue,
    bytes: stringify,
    date: unwrapdate,
    int: unwrapinteger,
}


def unwrapastype(context, mapping, thing, typ):
    """Move the inner value object out of the wrapper and coerce its type"""
    try:
        f = _unwrapfuncbytype[typ]
    except KeyError:
        raise error.ProgrammingError(b'invalid type specified: %r' % typ)
    return f(context, mapping, thing)


def runinteger(context, mapping, data):
    return int(data)


def runstring(context, mapping, data):
    return data


def _recursivesymbolblocker(key):
    def showrecursion(context, mapping):
        raise error.Abort(_(b"recursive reference '%s' in template") % key)

    return showrecursion


def runsymbol(context, mapping, key, default=b''):
    v = context.symbol(mapping, key)
    if v is None:
        # put poison to cut recursion. we can't move this to parsing phase
        # because "x = {x}" is allowed if "x" is a keyword. (issue4758)
        safemapping = mapping.copy()
        safemapping[key] = _recursivesymbolblocker(key)
        try:
            v = context.process(key, safemapping)
        except TemplateNotFound:
            v = default
    if callable(v):
        # new templatekw
        try:
            return v(context, mapping)
        except ResourceUnavailable:
            # unsupported keyword is mapped to empty just like unknown keyword
            return None
    return v


def runtemplate(context, mapping, template):
    for arg in template:
        yield evalrawexp(context, mapping, arg)


def runfilter(context, mapping, data):
    arg, filt = data
    thing = evalrawexp(context, mapping, arg)
    intype = getattr(filt, '_intype', None)
    try:
        thing = unwrapastype(context, mapping, thing, intype)
        return filt(thing)
    except error.ParseError as e:
        raise error.ParseError(bytes(e), hint=_formatfiltererror(arg, filt))


def _formatfiltererror(arg, filt):
    fn = pycompat.sysbytes(filt.__name__)
    sym = findsymbolicname(arg)
    if not sym:
        return _(b"incompatible use of template filter '%s'") % fn
    return _(b"template filter '%s' is not compatible with keyword '%s'") % (
        fn,
        sym,
    )


def _iteroverlaymaps(context, origmapping, newmappings):
    """Generate combined mappings from the original mapping and an iterable
    of partial mappings to override the original"""
    for i, nm in enumerate(newmappings):
        lm = context.overlaymap(origmapping, nm)
        lm[b'index'] = i
        yield lm


def _applymap(context, mapping, d, darg, targ):
    try:
        diter = d.itermaps(context)
    except error.ParseError as err:
        sym = findsymbolicname(darg)
        if not sym:
            raise
        hint = _(b"keyword '%s' does not support map operation") % sym
        raise error.ParseError(bytes(err), hint=hint)
    for lm in _iteroverlaymaps(context, mapping, diter):
        yield evalrawexp(context, lm, targ)


def runmap(context, mapping, data):
    darg, targ = data
    d = evalwrapped(context, mapping, darg)
    return mappedgenerator(_applymap, args=(mapping, d, darg, targ))


def runmember(context, mapping, data):
    darg, memb = data
    d = evalwrapped(context, mapping, darg)
    if isinstance(d, mappable):
        lm = context.overlaymap(mapping, d.tomap(context))
        return runsymbol(context, lm, memb)
    try:
        return d.getmember(context, mapping, memb)
    except error.ParseError as err:
        sym = findsymbolicname(darg)
        if not sym:
            raise
        hint = _(b"keyword '%s' does not support member operation") % sym
        raise error.ParseError(bytes(err), hint=hint)


def runnegate(context, mapping, data):
    data = evalinteger(
        context, mapping, data, _(b'negation needs an integer argument')
    )
    return -data


def runarithmetic(context, mapping, data):
    func, left, right = data
    left = evalinteger(
        context, mapping, left, _(b'arithmetic only defined on integers')
    )
    right = evalinteger(
        context, mapping, right, _(b'arithmetic only defined on integers')
    )
    try:
        return func(left, right)
    except ZeroDivisionError:
        raise error.Abort(_(b'division by zero is not defined'))


def joinitems(itemiter, sep):
    """Join items with the separator; Returns generator of bytes"""
    first = True
    for x in itemiter:
        if first:
            first = False
        elif sep:
            yield sep
        yield x
