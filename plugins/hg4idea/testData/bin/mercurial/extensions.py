# extensions.py - extension handling for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import imp, os
import util, cmdutil, help, error
from i18n import _, gettext

_extensions = {}
_order = []

def extensions():
    for name in _order:
        module = _extensions[name]
        if module:
            yield name, module

def find(name):
    '''return module with given extension name'''
    try:
        return _extensions[name]
    except KeyError:
        for k, v in _extensions.iteritems():
            if k.endswith('.' + name) or k.endswith('/' + name):
                return v
        raise KeyError(name)

def loadpath(path, module_name):
    module_name = module_name.replace('.', '_')
    path = util.expandpath(path)
    if os.path.isdir(path):
        # module/__init__.py style
        d, f = os.path.split(path.rstrip('/'))
        fd, fpath, desc = imp.find_module(f, [d])
        return imp.load_module(module_name, fd, fpath, desc)
    else:
        return imp.load_source(module_name, path)

def load(ui, name, path):
    # unused ui argument kept for backwards compatibility
    if name.startswith('hgext.') or name.startswith('hgext/'):
        shortname = name[6:]
    else:
        shortname = name
    if shortname in _extensions:
        return
    _extensions[shortname] = None
    if path:
        # the module will be loaded in sys.modules
        # choose an unique name so that it doesn't
        # conflicts with other modules
        mod = loadpath(path, 'hgext.%s' % name)
    else:
        def importh(name):
            mod = __import__(name)
            components = name.split('.')
            for comp in components[1:]:
                mod = getattr(mod, comp)
            return mod
        try:
            mod = importh("hgext.%s" % name)
        except ImportError:
            mod = importh(name)
    _extensions[shortname] = mod
    _order.append(shortname)

def loadall(ui):
    result = ui.configitems("extensions")
    newindex = len(_order)
    for (name, path) in result:
        if path:
            if path[0] == '!':
                continue
        try:
            load(ui, name, path)
        except KeyboardInterrupt:
            raise
        except Exception, inst:
            if path:
                ui.warn(_("*** failed to import extension %s from %s: %s\n")
                        % (name, path, inst))
            else:
                ui.warn(_("*** failed to import extension %s: %s\n")
                        % (name, inst))
            if ui.traceback():
                return 1

    for name in _order[newindex:]:
        uisetup = getattr(_extensions[name], 'uisetup', None)
        if uisetup:
            uisetup(ui)

    for name in _order[newindex:]:
        extsetup = getattr(_extensions[name], 'extsetup', None)
        if extsetup:
            try:
                extsetup(ui)
            except TypeError:
                if extsetup.func_code.co_argcount != 0:
                    raise
                extsetup() # old extsetup with no ui argument

def wrapcommand(table, command, wrapper):
    aliases, entry = cmdutil.findcmd(command, table)
    for alias, e in table.iteritems():
        if e is entry:
            key = alias
            break

    origfn = entry[0]
    def wrap(*args, **kwargs):
        return util.checksignature(wrapper)(
            util.checksignature(origfn), *args, **kwargs)

    wrap.__doc__ = getattr(origfn, '__doc__')
    wrap.__module__ = getattr(origfn, '__module__')

    newentry = list(entry)
    newentry[0] = wrap
    table[key] = tuple(newentry)
    return entry

def wrapfunction(container, funcname, wrapper):
    def wrap(*args, **kwargs):
        return wrapper(origfn, *args, **kwargs)

    origfn = getattr(container, funcname)
    setattr(container, funcname, wrap)
    return origfn

def _disabledpaths(strip_init=False):
    '''find paths of disabled extensions. returns a dict of {name: path}
    removes /__init__.py from packages if strip_init is True'''
    import hgext
    extpath = os.path.dirname(os.path.abspath(hgext.__file__))
    try: # might not be a filesystem path
        files = os.listdir(extpath)
    except OSError:
        return {}

    exts = {}
    for e in files:
        if e.endswith('.py'):
            name = e.rsplit('.', 1)[0]
            path = os.path.join(extpath, e)
        else:
            name = e
            path = os.path.join(extpath, e, '__init__.py')
            if not os.path.exists(path):
                continue
            if strip_init:
                path = os.path.dirname(path)
        if name in exts or name in _order or name == '__init__':
            continue
        exts[name] = path
    return exts

def _disabledhelp(path):
    '''retrieve help synopsis of a disabled extension (without importing)'''
    try:
        file = open(path)
    except IOError:
        return
    else:
        doc = help.moduledoc(file)
        file.close()

    if doc: # extracting localized synopsis
        return gettext(doc).splitlines()[0]
    else:
        return _('(no help text available)')

def disabled():
    '''find disabled extensions from hgext
    returns a dict of {name: desc}, and the max name length'''

    paths = _disabledpaths()
    if not paths:
        return None, 0

    exts = {}
    maxlength = 0
    for name, path in paths.iteritems():
        doc = _disabledhelp(path)
        if not doc:
            continue

        exts[name] = doc
        if len(name) > maxlength:
            maxlength = len(name)

    return exts, maxlength

def disabledext(name):
    '''find a specific disabled extension from hgext. returns desc'''
    paths = _disabledpaths()
    if name in paths:
        return _disabledhelp(paths[name])

def disabledcmd(cmd, strict=False):
    '''import disabled extensions until cmd is found.
    returns (cmdname, extname, doc)'''

    paths = _disabledpaths(strip_init=True)
    if not paths:
        raise error.UnknownCommand(cmd)

    def findcmd(cmd, name, path):
        try:
            mod = loadpath(path, 'hgext.%s' % name)
        except Exception:
            return
        try:
            aliases, entry = cmdutil.findcmd(cmd,
                getattr(mod, 'cmdtable', {}), strict)
        except (error.AmbiguousCommand, error.UnknownCommand):
            return
        for c in aliases:
            if c.startswith(cmd):
                cmd = c
                break
        else:
            cmd = aliases[0]
        return (cmd, name, mod)

    # first, search for an extension with the same name as the command
    path = paths.pop(cmd, None)
    if path:
        ext = findcmd(cmd, cmd, path)
        if ext:
            return ext

    # otherwise, interrogate each extension until there's a match
    for name, path in paths.iteritems():
        ext = findcmd(cmd, name, path)
        if ext:
            return ext

    raise error.UnknownCommand(cmd)

def enabled():
    '''return a dict of {name: desc} of extensions, and the max name length'''
    exts = {}
    maxlength = 0
    for ename, ext in extensions():
        doc = (gettext(ext.__doc__) or _('(no help text available)'))
        ename = ename.split('.')[-1]
        maxlength = max(len(ename), maxlength)
        exts[ename] = doc.splitlines()[0].strip()

    return exts, maxlength
