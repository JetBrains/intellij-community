# demandimport.py - global demand-loading of modules for Mercurial
#
# Copyright 2006, 2007 Matt Mackall <mpm@selenic.com>
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

import __builtin__
_origimport = __import__

nothing = object()

try:
    _origimport(__builtin__.__name__, {}, {}, None, -1)
except TypeError: # no level argument
    def _import(name, globals, locals, fromlist, level):
        "call _origimport with no level argument"
        return _origimport(name, globals, locals, fromlist)
else:
    _import = _origimport

class _demandmod(object):
    """module demand-loader and proxy"""
    def __init__(self, name, globals, locals):
        if '.' in name:
            head, rest = name.split('.', 1)
            after = [rest]
        else:
            head = name
            after = []
        object.__setattr__(self, "_data", (head, globals, locals, after))
        object.__setattr__(self, "_module", None)
    def _extend(self, name):
        """add to the list of submodules to load"""
        self._data[3].append(name)
    def _load(self):
        if not self._module:
            head, globals, locals, after = self._data
            mod = _origimport(head, globals, locals)
            # load submodules
            def subload(mod, p):
                h, t = p, None
                if '.' in p:
                    h, t = p.split('.', 1)
                if getattr(mod, h, nothing) is nothing:
                    setattr(mod, h, _demandmod(p, mod.__dict__, mod.__dict__))
                elif t:
                    subload(getattr(mod, h), t)

            for x in after:
                subload(mod, x)

            # are we in the locals dictionary still?
            if locals and locals.get(head) == self:
                locals[head] = mod
            object.__setattr__(self, "_module", mod)

    def __repr__(self):
        if self._module:
            return "<proxied module '%s'>" % self._data[0]
        return "<unloaded module '%s'>" % self._data[0]
    def __call__(self, *args, **kwargs):
        raise TypeError("%s object is not callable" % repr(self))
    def __getattribute__(self, attr):
        if attr in ('_data', '_extend', '_load', '_module'):
            return object.__getattribute__(self, attr)
        self._load()
        return getattr(self._module, attr)
    def __setattr__(self, attr, val):
        self._load()
        setattr(self._module, attr, val)

def _demandimport(name, globals=None, locals=None, fromlist=None, level=-1):
    if not locals or name in ignore or fromlist == ('*',):
        # these cases we can't really delay
        return _import(name, globals, locals, fromlist, level)
    elif not fromlist:
        # import a [as b]
        if '.' in name: # a.b
            base, rest = name.split('.', 1)
            # email.__init__ loading email.mime
            if globals and globals.get('__name__', None) == base:
                return _import(name, globals, locals, fromlist, level)
            # if a is already demand-loaded, add b to its submodule list
            if base in locals:
                if isinstance(locals[base], _demandmod):
                    locals[base]._extend(rest)
                return locals[base]
        return _demandmod(name, globals, locals)
    else:
        if level != -1:
            # from . import b,c,d or from .a import b,c,d
            return _origimport(name, globals, locals, fromlist, level)
        # from a import b,c,d
        mod = _origimport(name, globals, locals)
        # recurse down the module chain
        for comp in name.split('.')[1:]:
            if getattr(mod, comp, nothing) is nothing:
                setattr(mod, comp, _demandmod(comp, mod.__dict__, mod.__dict__))
            mod = getattr(mod, comp)
        for x in fromlist:
            # set requested submodules for demand load
            if getattr(mod, x, nothing) is nothing:
                setattr(mod, x, _demandmod(x, mod.__dict__, locals))
        return mod

ignore = [
    '_hashlib',
    '_xmlplus',
    'fcntl',
    'win32com.gen_py',
    '_winreg', # 2.7 mimetypes needs immediate ImportError
    'pythoncom',
    # imported by tarfile, not available under Windows
    'pwd',
    'grp',
    # imported by profile, itself imported by hotshot.stats,
    # not available under Windows
    'resource',
    # this trips up many extension authors
    'gtk',
    # setuptools' pkg_resources.py expects "from __main__ import x" to
    # raise ImportError if x not defined
    '__main__',
    '_ssl', # conditional imports in the stdlib, issue1964
    'rfc822',
    'mimetools',
    ]

def enable():
    "enable global demand-loading of modules"
    __builtin__.__import__ = _demandimport

def disable():
    "disable global demand-loading of modules"
    __builtin__.__import__ = _origimport
