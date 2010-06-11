# templater.py - template expansion for output
#
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import re, sys, os
import util, config, templatefilters

path = ['templates', '../templates']
stringify = templatefilters.stringify

def parsestring(s, quoted=True):
    '''parse a string using simple c-like syntax.
    string must be in quotes if quoted is True.'''
    if quoted:
        if len(s) < 2 or s[0] != s[-1]:
            raise SyntaxError(_('unmatched quotes'))
        return s[1:-1].decode('string_escape')

    return s.decode('string_escape')

class engine(object):
    '''template expansion engine.

    template expansion works like this. a map file contains key=value
    pairs. if value is quoted, it is treated as string. otherwise, it
    is treated as name of template file.

    templater is asked to expand a key in map. it looks up key, and
    looks for strings like this: {foo}. it expands {foo} by looking up
    foo in map, and substituting it. expansion is recursive: it stops
    when there is no more {foo} to replace.

    expansion also allows formatting and filtering.

    format uses key to expand each item in list. syntax is
    {key%format}.

    filter uses function to transform value. syntax is
    {key|filter1|filter2|...}.'''

    template_re = re.compile(r'{([\w\|%]+)}')

    def __init__(self, loader, filters={}, defaults={}):
        self.loader = loader
        self.filters = filters
        self.defaults = defaults
        self.cache = {}

    def process(self, t, map):
        '''Perform expansion. t is name of map element to expand. map contains
        added elements for use during expansion. Is a generator.'''
        tmpl = self.loader(t)
        iters = [self._process(tmpl, map)]
        while iters:
            try:
                item = iters[0].next()
            except StopIteration:
                iters.pop(0)
                continue
            if isinstance(item, str):
                yield item
            elif item is None:
                yield ''
            elif hasattr(item, '__iter__'):
                iters.insert(0, iter(item))
            else:
                yield str(item)

    def _format(self, expr, get, map):
        key, format = expr.split('%')
        v = get(key)
        if not hasattr(v, '__iter__'):
            raise SyntaxError(_("error expanding '%s%%%s'") % (key, format))
        lm = map.copy()
        for i in v:
            if isinstance(i, dict):
                lm.update(i)
                yield self.process(format, lm)
            else:
                # v is not an iterable of dicts, this happen when 'key'
                # has been fully expanded already and format is useless.
                # If so, return the expanded value.
                yield i

    def _filter(self, expr, get, map):
        if expr not in self.cache:
            parts = expr.split('|')
            val = parts[0]
            try:
                filters = [self.filters[f] for f in parts[1:]]
            except KeyError, i:
                raise SyntaxError(_("unknown filter '%s'") % i[0])
            def apply(get):
                x = get(val)
                for f in filters:
                    x = f(x)
                return x
            self.cache[expr] = apply
        return self.cache[expr](get)

    def _process(self, tmpl, map):
        '''Render a template. Returns a generator.'''

        def get(key):
            v = map.get(key)
            if v is None:
                v = self.defaults.get(key, '')
            if hasattr(v, '__call__'):
                v = v(**map)
            return v

        while tmpl:
            m = self.template_re.search(tmpl)
            if not m:
                yield tmpl
                break

            start, end = m.span(0)
            variants = m.groups()
            expr = variants[0] or variants[1]

            if start:
                yield tmpl[:start]
            tmpl = tmpl[end:]

            if '%' in expr:
                yield self._format(expr, get, map)
            elif '|' in expr:
                yield self._filter(expr, get, map)
            else:
                yield get(expr)

engines = {'default': engine}

class templater(object):

    def __init__(self, mapfile, filters={}, defaults={}, cache={},
                 minchunk=1024, maxchunk=65536):
        '''set up template engine.
        mapfile is name of file to read map definitions from.
        filters is dict of functions. each transforms a value into another.
        defaults is dict of default map definitions.'''
        self.mapfile = mapfile or 'template'
        self.cache = cache.copy()
        self.map = {}
        self.base = (mapfile and os.path.dirname(mapfile)) or ''
        self.filters = templatefilters.filters.copy()
        self.filters.update(filters)
        self.defaults = defaults
        self.minchunk, self.maxchunk = minchunk, maxchunk
        self.engines = {}

        if not mapfile:
            return
        if not os.path.exists(mapfile):
            raise util.Abort(_('style not found: %s') % mapfile)

        conf = config.config()
        conf.read(mapfile)

        for key, val in conf[''].items():
            if val[0] in "'\"":
                try:
                    self.cache[key] = parsestring(val)
                except SyntaxError, inst:
                    raise SyntaxError('%s: %s' %
                                      (conf.source('', key), inst.args[0]))
            else:
                val = 'default', val
                if ':' in val[1]:
                    val = val[1].split(':', 1)
                self.map[key] = val[0], os.path.join(self.base, val[1])

    def __contains__(self, key):
        return key in self.cache or key in self.map

    def load(self, t):
        '''Get the template for the given template name. Use a local cache.'''
        if not t in self.cache:
            try:
                self.cache[t] = open(self.map[t][1]).read()
            except IOError, inst:
                raise IOError(inst.args[0], _('template file %s: %s') %
                              (self.map[t][1], inst.args[1]))
        return self.cache[t]

    def __call__(self, t, **map):
        ttype = t in self.map and self.map[t][0] or 'default'
        proc = self.engines.get(ttype)
        if proc is None:
            proc = engines[ttype](self.load, self.filters, self.defaults)
            self.engines[ttype] = proc

        stream = proc.process(t, map)
        if self.minchunk:
            stream = util.increasingchunks(stream, min=self.minchunk,
                                           max=self.maxchunk)
        return stream

def templatepath(name=None):
    '''return location of template file or directory (if no name).
    returns None if not found.'''
    normpaths = []

    # executable version (py2exe) doesn't support __file__
    if hasattr(sys, 'frozen'):
        module = sys.executable
    else:
        module = __file__
    for f in path:
        if f.startswith('/'):
            p = f
        else:
            fl = f.split('/')
            p = os.path.join(os.path.dirname(module), *fl)
        if name:
            p = os.path.join(p, name)
        if name and os.path.exists(p):
            return os.path.normpath(p)
        elif os.path.isdir(p):
            normpaths.append(os.path.normpath(p))

    return normpaths

def stylemap(styles, paths=None):
    """Return path to mapfile for a given style.

    Searches mapfile in the following locations:
    1. templatepath/style/map
    2. templatepath/map-style
    3. templatepath/map
    """

    if paths is None:
        paths = templatepath()
    elif isinstance(paths, str):
        paths = [paths]

    if isinstance(styles, str):
        styles = [styles]

    for style in styles:
        if not style:
            continue
        locations = [os.path.join(style, 'map'), 'map-' + style]
        locations.append('map')

        for path in paths:
            for location in locations:
                mapfile = os.path.join(path, location)
                if os.path.isfile(mapfile):
                    return style, mapfile

    raise RuntimeError("No hgweb templates found in %r" % paths)
