# demandimport.py - global demand-loading of modules for Mercurial
#
# Copyright 2006, 2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''
demandimport - automatic demandloading of modules

To enable this module, do:

  import demandimport; demandimport.enable()

Imports of the following forms will be demand-loaded:

  import a, b.c
  import a.b as c
  from a import b,c # a will be loaded immediately

These imports will not be delayed:

  from a import *
  b = __import__(a)
'''

from __future__ import absolute_import

import __builtin__ as builtins
import contextlib
import sys

from . import tracing

contextmanager = contextlib.contextmanager

_origimport = __import__

nothing = object()


def _hgextimport(importfunc, name, globals, *args, **kwargs):
    try:
        return importfunc(name, globals, *args, **kwargs)
    except ImportError:
        if not globals:
            raise
        # extensions are loaded with "hgext_" prefix
        hgextname = 'hgext_%s' % name
        nameroot = hgextname.split('.', 1)[0]
        contextroot = globals.get('__name__', '').split('.', 1)[0]
        if nameroot != contextroot:
            raise
        # retry to import with "hgext_" prefix
        return importfunc(hgextname, globals, *args, **kwargs)


class _demandmod(object):
    """module demand-loader and proxy

    Specify 1 as 'level' argument at construction, to import module
    relatively.
    """

    def __init__(self, name, globals, locals, level):
        if '.' in name:
            head, rest = name.split('.', 1)
            after = [rest]
        else:
            head = name
            after = []
        object.__setattr__(
            self, "_data", (head, globals, locals, after, level, set())
        )
        object.__setattr__(self, "_module", None)

    def _extend(self, name):
        """add to the list of submodules to load"""
        self._data[3].append(name)

    def _addref(self, name):
        """Record that the named module ``name`` imports this module.

        References to this proxy class having the name of this module will be
        replaced at module load time. We assume the symbol inside the importing
        module is identical to the "head" name of this module. We don't
        actually know if "as X" syntax is being used to change the symbol name
        because this information isn't exposed to __import__.
        """
        self._data[5].add(name)

    def _load(self):
        if not self._module:
            with tracing.log('demandimport %s', self._data[0]):
                head, globals, locals, after, level, modrefs = self._data
                mod = _hgextimport(
                    _origimport, head, globals, locals, None, level
                )
                if mod is self:
                    # In this case, _hgextimport() above should imply
                    # _demandimport(). Otherwise, _hgextimport() never
                    # returns _demandmod. This isn't intentional behavior,
                    # in fact. (see also issue5304 for detail)
                    #
                    # If self._module is already bound at this point, self
                    # should be already _load()-ed while _hgextimport().
                    # Otherwise, there is no way to import actual module
                    # as expected, because (re-)invoking _hgextimport()
                    # should cause same result.
                    # This is reason why _load() returns without any more
                    # setup but assumes self to be already bound.
                    mod = self._module
                    assert mod and mod is not self, "%s, %s" % (self, mod)
                    return

                # load submodules
                def subload(mod, p):
                    h, t = p, None
                    if '.' in p:
                        h, t = p.split('.', 1)
                    if getattr(mod, h, nothing) is nothing:
                        setattr(
                            mod,
                            h,
                            _demandmod(p, mod.__dict__, mod.__dict__, level=1),
                        )
                    elif t:
                        subload(getattr(mod, h), t)

                for x in after:
                    subload(mod, x)

                # Replace references to this proxy instance with the
                # actual module.
                if locals:
                    if locals.get(head) is self:
                        locals[head] = mod
                    elif locals.get(head + 'mod') is self:
                        locals[head + 'mod'] = mod

                for modname in modrefs:
                    modref = sys.modules.get(modname, None)
                    if modref and getattr(modref, head, None) is self:
                        setattr(modref, head, mod)

                object.__setattr__(self, "_module", mod)

    def __repr__(self):
        if self._module:
            return "<proxied module '%s'>" % self._data[0]
        return "<unloaded module '%s'>" % self._data[0]

    def __call__(self, *args, **kwargs):
        raise TypeError("%s object is not callable" % repr(self))

    def __getattr__(self, attr):
        self._load()
        return getattr(self._module, attr)

    def __setattr__(self, attr, val):
        self._load()
        setattr(self._module, attr, val)

    @property
    def __dict__(self):
        self._load()
        return self._module.__dict__

    @property
    def __doc__(self):
        self._load()
        return self._module.__doc__


_pypy = '__pypy__' in sys.builtin_module_names


def _demandimport(name, globals=None, locals=None, fromlist=None, level=-1):
    if locals is None or name in ignores or fromlist == ('*',):
        # these cases we can't really delay
        return _hgextimport(_origimport, name, globals, locals, fromlist, level)
    elif not fromlist:
        # import a [as b]
        if '.' in name:  # a.b
            base, rest = name.split('.', 1)
            # email.__init__ loading email.mime
            if globals and globals.get('__name__', None) == base:
                return _origimport(name, globals, locals, fromlist, level)
            # if a is already demand-loaded, add b to its submodule list
            if base in locals:
                if isinstance(locals[base], _demandmod):
                    locals[base]._extend(rest)
                return locals[base]
        return _demandmod(name, globals, locals, level)
    else:
        # There is a fromlist.
        # from a import b,c,d
        # from . import b,c,d
        # from .a import b,c,d

        # level == -1: relative and absolute attempted (Python 2 only).
        # level >= 0: absolute only (Python 2 w/ absolute_import and Python 3).
        # The modern Mercurial convention is to use absolute_import everywhere,
        # so modern Mercurial code will have level >= 0.

        # The name of the module the import statement is located in.
        globalname = globals.get('__name__')

        def processfromitem(mod, attr):
            """Process an imported symbol in the import statement.

            If the symbol doesn't exist in the parent module, and if the
            parent module is a package, it must be a module. We set missing
            modules up as _demandmod instances.
            """
            symbol = getattr(mod, attr, nothing)
            nonpkg = getattr(mod, '__path__', nothing) is nothing
            if symbol is nothing:
                if nonpkg:
                    # do not try relative import, which would raise ValueError,
                    # and leave unknown attribute as the default __import__()
                    # would do. the missing attribute will be detected later
                    # while processing the import statement.
                    return
                mn = '%s.%s' % (mod.__name__, attr)
                if mn in ignores:
                    importfunc = _origimport
                else:
                    importfunc = _demandmod
                symbol = importfunc(attr, mod.__dict__, locals, level=1)
                setattr(mod, attr, symbol)

            # Record the importing module references this symbol so we can
            # replace the symbol with the actual module instance at load
            # time.
            if globalname and isinstance(symbol, _demandmod):
                symbol._addref(globalname)

        def chainmodules(rootmod, modname):
            # recurse down the module chain, and return the leaf module
            mod = rootmod
            for comp in modname.split('.')[1:]:
                obj = getattr(mod, comp, nothing)
                if obj is nothing:
                    obj = _demandmod(comp, mod.__dict__, mod.__dict__, level=1)
                    setattr(mod, comp, obj)
                elif mod.__name__ + '.' + comp in sys.modules:
                    # prefer loaded module over attribute (issue5617)
                    obj = sys.modules[mod.__name__ + '.' + comp]
                mod = obj
            return mod

        if level >= 0:
            if name:
                # "from a import b" or "from .a import b" style
                rootmod = _hgextimport(
                    _origimport, name, globals, locals, level=level
                )
                mod = chainmodules(rootmod, name)
            elif _pypy:
                # PyPy's __import__ throws an exception if invoked
                # with an empty name and no fromlist.  Recreate the
                # desired behaviour by hand.
                mn = globalname
                mod = sys.modules[mn]
                if getattr(mod, '__path__', nothing) is nothing:
                    mn = mn.rsplit('.', 1)[0]
                    mod = sys.modules[mn]
                if level > 1:
                    mn = mn.rsplit('.', level - 1)[0]
                    mod = sys.modules[mn]
            else:
                mod = _hgextimport(
                    _origimport, name, globals, locals, level=level
                )

            for x in fromlist:
                processfromitem(mod, x)

            return mod

        # But, we still need to support lazy loading of standard library and 3rd
        # party modules. So handle level == -1.
        mod = _hgextimport(_origimport, name, globals, locals)
        mod = chainmodules(mod, name)

        for x in fromlist:
            processfromitem(mod, x)

        return mod


ignores = set()


def init(ignoreset):
    global ignores
    ignores = ignoreset


def isenabled():
    return builtins.__import__ == _demandimport


def enable():
    """enable global demand-loading of modules"""
    builtins.__import__ = _demandimport


def disable():
    """disable global demand-loading of modules"""
    builtins.__import__ = _origimport


@contextmanager
def deactivated():
    """context manager for disabling demandimport in 'with' blocks"""
    demandenabled = isenabled()
    if demandenabled:
        disable()

    try:
        yield
    finally:
        if demandenabled:
            enable()
