# extensions.py - extension handling for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import imp, os
import util, cmdutil, error
from i18n import _, gettext

_extensions = {}
_order = []
_ignore = ['hbisect', 'bookmarks', 'parentrevspec', 'interhg']

def extensions():
    for name in _order:
        module = _extensions[name]
        if module:
            yield name, module

def find(name):
    '''return module with given extension name'''
    mod = None
    try:
        mod =  _extensions[name]
    except KeyError:
        for k, v in _extensions.iteritems():
            if k.endswith('.' + name) or k.endswith('/' + name):
                mod = v
                break
    if not mod:
        raise KeyError(name)
    return mod

def loadpath(path, module_name):
    module_name = module_name.replace('.', '_')
    path = util.expandpath(path)
    if os.path.isdir(path):
        # module/__init__.py style
        d, f = os.path.split(path.rstrip('/'))
        fd, fpath, desc = imp.find_module(f, [d])
        return imp.load_module(module_name, fd, fpath, desc)
    else:
        try:
            return imp.load_source(module_name, path)
        except IOError, exc:
            if not exc.filename:
                exc.filename = path # python does not fill this
            raise

def load(ui, name, path):
    if name.startswith('hgext.') or name.startswith('hgext/'):
        shortname = name[6:]
    else:
        shortname = name
    if shortname in _ignore:
        return None
    if shortname in _extensions:
        return _extensions[shortname]
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
        except ImportError, err:
            ui.debug('could not import hgext.%s (%s): trying %s\n'
                     % (name, err, name))
            mod = importh(name)
    _extensions[shortname] = mod
    _order.append(shortname)
    return mod

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
    '''Wrap the command named `command' in table

    Replace command in the command table with wrapper. The wrapped command will
    be inserted into the command table specified by the table argument.

    The wrapper will be called like

      wrapper(orig, *args, **kwargs)

    where orig is the original (wrapped) function, and *args, **kwargs
    are the arguments passed to it.
    '''
    assert util.safehasattr(wrapper, '__call__')
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
    '''Wrap the function named funcname in container

    Replace the funcname member in the given container with the specified
    wrapper. The container is typically a module, class, or instance.

    The wrapper will be called like

      wrapper(orig, *args, **kwargs)

    where orig is the original (wrapped) function, and *args, **kwargs
    are the arguments passed to it.

    Wrapping methods of the repository object is not recommended since
    it conflicts with extensions that extend the repository by
    subclassing. All extensions that need to extend methods of
    localrepository should use this subclassing trick: namely,
    reposetup() should look like

      def reposetup(ui, repo):
          class myrepo(repo.__class__):
              def whatever(self, *args, **kwargs):
                  [...extension stuff...]
                  super(myrepo, self).whatever(*args, **kwargs)
                  [...extension stuff...]

          repo.__class__ = myrepo

    In general, combining wrapfunction() with subclassing does not
    work. Since you cannot control what other extensions are loaded by
    your end users, you should play nicely with others by using the
    subclass trick.
    '''
    assert util.safehasattr(wrapper, '__call__')
    def wrap(*args, **kwargs):
        return wrapper(origfn, *args, **kwargs)

    origfn = getattr(container, funcname)
    assert util.safehasattr(origfn, '__call__')
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

def _moduledoc(file):
    '''return the top-level python documentation for the given file

    Loosely inspired by pydoc.source_synopsis(), but rewritten to
    handle triple quotes and to return the whole text instead of just
    the synopsis'''
    result = []

    line = file.readline()
    while line[:1] == '#' or not line.strip():
        line = file.readline()
        if not line:
            break

    start = line[:3]
    if start == '"""' or start == "'''":
        line = line[3:]
        while line:
            if line.rstrip().endswith(start):
                line = line.split(start)[0]
                if line:
                    result.append(line)
                break
            elif not line:
                return None # unmatched delimiter
            result.append(line)
            line = file.readline()
    else:
        return None

    return ''.join(result)

def _disabledhelp(path):
    '''retrieve help synopsis of a disabled extension (without importing)'''
    try:
        file = open(path)
    except IOError:
        return
    else:
        doc = _moduledoc(file)
        file.close()

    if doc: # extracting localized synopsis
        return gettext(doc).splitlines()[0]
    else:
        return _('(no help text available)')

def disabled():
    '''find disabled extensions from hgext. returns a dict of {name: desc}'''
    try:
        from hgext import __index__
        return dict((name, gettext(desc))
                    for name, desc in __index__.docs.iteritems()
                    if name not in _order)
    except ImportError:
        pass

    paths = _disabledpaths()
    if not paths:
        return {}

    exts = {}
    for name, path in paths.iteritems():
        doc = _disabledhelp(path)
        if doc:
            exts[name] = doc

    return exts

def disabledext(name):
    '''find a specific disabled extension from hgext. returns desc'''
    try:
        from hgext import __index__
        if name in _order:  # enabled
            return
        else:
            return gettext(__index__.docs.get(name))
    except ImportError:
        pass

    paths = _disabledpaths()
    if name in paths:
        return _disabledhelp(paths[name])

def disabledcmd(ui, cmd, strict=False):
    '''import disabled extensions until cmd is found.
    returns (cmdname, extname, module)'''

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
        except Exception:
            ui.warn(_('warning: error finding commands in %s\n') % path)
            ui.traceback()
            return
        for c in aliases:
            if c.startswith(cmd):
                cmd = c
                break
        else:
            cmd = aliases[0]
        return (cmd, name, mod)

    ext = None
    # first, search for an extension with the same name as the command
    path = paths.pop(cmd, None)
    if path:
        ext = findcmd(cmd, cmd, path)
    if not ext:
        # otherwise, interrogate each extension until there's a match
        for name, path in paths.iteritems():
            ext = findcmd(cmd, name, path)
            if ext:
                break
    if ext and 'DEPRECATED' not in ext.__doc__:
        return ext

    raise error.UnknownCommand(cmd)

def enabled():
    '''return a dict of {name: desc} of extensions'''
    exts = {}
    for ename, ext in extensions():
        doc = (gettext(ext.__doc__) or _('(no help text available)'))
        ename = ename.split('.')[-1]
        exts[ename] = doc.splitlines()[0].strip()

    return exts
