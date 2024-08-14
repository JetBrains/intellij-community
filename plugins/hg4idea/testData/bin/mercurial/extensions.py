# extensions.py - extension handling for mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import ast
import collections
import functools
import importlib
import inspect
import os
import sys

from .i18n import (
    _,
    gettext,
)
from .pycompat import (
    open,
)

from . import (
    cmdutil,
    configitems,
    error,
    pycompat,
    util,
)

from .utils import stringutil

_extensions = {}
_disabledextensions = {}
_aftercallbacks = {}
_order = []
_builtin = {
    b'hbisect',
    b'bookmarks',
    b'color',
    b'parentrevspec',
    b'progress',
    b'interhg',
    b'inotify',
    b'hgcia',
    b'shelve',
}


def extensions(ui=None):
    if ui:

        def enabled(name):
            for format in [b'%s', b'hgext.%s']:
                conf = ui.config(b'extensions', format % name)
                if conf is not None and not conf.startswith(b'!'):
                    return True

    else:
        enabled = lambda name: True
    for name in _order:
        module = _extensions[name]
        if module and enabled(name):
            yield name, module


def find(name):
    '''return module with given extension name'''
    mod = None
    try:
        mod = _extensions[name]
    except KeyError:
        for k, v in _extensions.items():
            if k.endswith(b'.' + name) or k.endswith(b'/' + name):
                mod = v
                break
    if not mod:
        raise KeyError(name)
    return mod


def loadpath(path, module_name):
    module_name = module_name.replace('.', '_')
    path = util.normpath(util.expandpath(path))
    path = pycompat.fsdecode(path)
    if os.path.isdir(path):
        # module/__init__.py style
        init_py_path = os.path.join(path, '__init__.py')
        if not os.path.exists(init_py_path):
            raise ImportError("No module named '%s'" % os.path.basename(path))
        path = init_py_path

    loader = importlib.machinery.SourceFileLoader(module_name, path)
    spec = importlib.util.spec_from_file_location(module_name, loader=loader)
    assert spec is not None  # help Pytype
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def _importh(name):
    """import and return the <name> module"""
    mod = __import__(name)
    components = name.split('.')
    for comp in components[1:]:
        mod = getattr(mod, comp)
    return mod


def _importext(name, path=None, reportfunc=None):
    name = pycompat.fsdecode(name)
    if path:
        # the module will be loaded in sys.modules
        # choose an unique name so that it doesn't
        # conflicts with other modules
        mod = loadpath(path, 'hgext.%s' % name)
    else:
        try:
            mod = _importh("hgext.%s" % name)
        except ImportError as err:
            if reportfunc:
                reportfunc(err, "hgext.%s" % name, "hgext3rd.%s" % name)
            try:
                mod = _importh("hgext3rd.%s" % name)
            except ImportError as err:
                if reportfunc:
                    reportfunc(err, "hgext3rd.%s" % name, name)
                mod = _importh(name)
    return mod


def _reportimporterror(ui, err, failed, next):
    # note: this ui.log happens before --debug is processed,
    #       Use --config ui.debug=1 to see them.
    ui.log(
        b'extension',
        b'    - could not import %s (%s): trying %s\n',
        stringutil.forcebytestr(failed),
        stringutil.forcebytestr(err),
        stringutil.forcebytestr(next),
    )
    if ui.debugflag and ui.configbool(b'devel', b'debug.extensions'):
        ui.traceback()


def _rejectunicode(name, xs):
    if isinstance(xs, (list, set, tuple)):
        for x in xs:
            _rejectunicode(name, x)
    elif isinstance(xs, dict):
        for k, v in xs.items():
            _rejectunicode(name, k)
            k = pycompat.sysstr(k)
            _rejectunicode('%s.%s' % (name, k), v)
    elif isinstance(xs, str):
        raise error.ProgrammingError(
            b"unicode %r found in %s" % (xs, stringutil.forcebytestr(name)),
            hint=b"use b'' to make it byte string",
        )


# attributes set by registrar.command
_cmdfuncattrs = ('norepo', 'optionalrepo', 'inferrepo')


def _validatecmdtable(ui, cmdtable):
    """Check if extension commands have required attributes"""
    for c, e in cmdtable.items():
        f = e[0]
        missing = [a for a in _cmdfuncattrs if not hasattr(f, a)]
        if not missing:
            continue
        msg = b'missing attributes: %s'
        msg %= b', '.join([stringutil.forcebytestr(m) for m in missing])
        hint = b"use @command decorator to register '%s'" % c
        raise error.ProgrammingError(msg, hint=hint)


def _validatetables(ui, mod):
    """Sanity check for loadable tables provided by extension module"""
    for t in ['cmdtable', 'colortable', 'configtable']:
        _rejectunicode(t, getattr(mod, t, {}))
    for t in [
        'filesetpredicate',
        'internalmerge',
        'revsetpredicate',
        'templatefilter',
        'templatefunc',
        'templatekeyword',
    ]:
        o = getattr(mod, t, None)
        if o:
            _rejectunicode(t, o._table)
    _validatecmdtable(ui, getattr(mod, 'cmdtable', {}))


def load(ui, name, path, loadingtime=None):
    if name.startswith(b'hgext.') or name.startswith(b'hgext/'):
        shortname = name[6:]
    else:
        shortname = name
    if shortname in _builtin:
        return None
    if shortname in _extensions:
        return _extensions[shortname]
    ui.log(b'extension', b'  - loading extension: %s\n', shortname)
    _extensions[shortname] = None
    with util.timedcm('load extension %s', shortname) as stats:
        mod = _importext(name, path, bind(_reportimporterror, ui))
    ui.log(b'extension', b'  > %s extension loaded in %s\n', shortname, stats)
    if loadingtime is not None:
        loadingtime[shortname] += stats.elapsed

    # Before we do anything with the extension, check against minimum stated
    # compatibility. This gives extension authors a mechanism to have their
    # extensions short circuit when loaded with a known incompatible version
    # of Mercurial.
    minver = getattr(mod, 'minimumhgversion', None)
    if minver:
        curver = util.versiontuple(n=2)
        extmin = util.versiontuple(stringutil.forcebytestr(minver), 2)

        if None in extmin:
            extmin = (extmin[0] or 0, extmin[1] or 0)

        if None in curver or extmin > curver:
            msg = _(
                b'(third party extension %s requires version %s or newer '
                b'of Mercurial (current: %s); disabling)\n'
            )
            ui.warn(msg % (shortname, minver, util.version()))
            return
    ui.log(b'extension', b'    - validating extension tables: %s\n', shortname)
    _validatetables(ui, mod)

    _extensions[shortname] = mod
    _order.append(shortname)
    ui.log(
        b'extension', b'    - invoking registered callbacks: %s\n', shortname
    )
    with util.timedcm('callbacks extension %s', shortname) as stats:
        for fn in _aftercallbacks.get(shortname, []):
            fn(loaded=True)
    ui.log(b'extension', b'    > callbacks completed in %s\n', stats)
    return mod


def _runuisetup(name, ui):
    uisetup = getattr(_extensions[name], 'uisetup', None)
    if uisetup:
        try:
            uisetup(ui)
        except Exception as inst:
            ui.traceback(force=True)
            msg = stringutil.forcebytestr(inst)
            ui.warn(_(b"*** failed to set up extension %s: %s\n") % (name, msg))
            return False
    return True


def _runextsetup(name, ui):
    extsetup = getattr(_extensions[name], 'extsetup', None)
    if extsetup:
        try:
            extsetup(ui)
        except Exception as inst:
            ui.traceback(force=True)
            msg = stringutil.forcebytestr(inst)
            ui.warn(_(b"*** failed to set up extension %s: %s\n") % (name, msg))
            return False
    return True


def loadall(ui, whitelist=None):
    loadingtime = collections.defaultdict(int)
    result = ui.configitems(b"extensions")
    if whitelist is not None:
        result = [(k, v) for (k, v) in result if k in whitelist]
    result = [(k, v) for (k, v) in result if b':' not in k]
    newindex = len(_order)
    ui.log(
        b'extension',
        b'loading %sextensions\n',
        b'additional ' if newindex else b'',
    )
    ui.log(b'extension', b'- processing %d entries\n', len(result))
    with util.timedcm('load all extensions') as stats:
        default_sub_options = ui.configsuboptions(b"extensions", b"*")[1]

        for (name, path) in result:
            if path:
                if path[0:1] == b'!':
                    if name not in _disabledextensions:
                        ui.log(
                            b'extension',
                            b'  - skipping disabled extension: %s\n',
                            name,
                        )
                    _disabledextensions[name] = path[1:]
                    continue
            try:
                load(ui, name, path, loadingtime)
            except Exception as inst:
                msg = stringutil.forcebytestr(inst)
                if path:
                    error_msg = _(
                        b'failed to import extension "%s" from %s: %s'
                    )
                    error_msg %= (name, path, msg)
                else:
                    error_msg = _(b'failed to import extension "%s": %s')
                    error_msg %= (name, msg)

                options = default_sub_options.copy()
                ext_options = ui.configsuboptions(b"extensions", name)[1]
                options.update(ext_options)
                if stringutil.parsebool(options.get(b"required", b'no')):
                    hint = None
                    if isinstance(inst, error.Hint) and inst.hint:
                        hint = inst.hint
                    if hint is None:
                        hint = _(
                            b"loading of this extension was required, "
                            b"see `hg help config.extensions` for details"
                        )
                    raise error.Abort(error_msg, hint=hint)
                else:
                    ui.warn((b"*** %s\n") % error_msg)
                    if isinstance(inst, error.Hint) and inst.hint:
                        ui.warn(_(b"*** (%s)\n") % inst.hint)
                    ui.traceback()

    ui.log(
        b'extension',
        b'> loaded %d extensions, total time %s\n',
        len(_order) - newindex,
        stats,
    )
    # list of (objname, loadermod, loadername) tuple:
    # - objname is the name of an object in extension module,
    #   from which extra information is loaded
    # - loadermod is the module where loader is placed
    # - loadername is the name of the function,
    #   which takes (ui, extensionname, extraobj) arguments
    #
    # This one is for the list of item that must be run before running any setup
    earlyextraloaders = [
        ('configtable', configitems, 'loadconfigtable'),
    ]

    ui.log(b'extension', b'- loading configtable attributes\n')
    _loadextra(ui, newindex, earlyextraloaders)

    broken = set()
    ui.log(b'extension', b'- executing uisetup hooks\n')
    with util.timedcm('all uisetup') as alluisetupstats:
        for name in _order[newindex:]:
            ui.log(b'extension', b'  - running uisetup for %s\n', name)
            with util.timedcm('uisetup %s', name) as stats:
                if not _runuisetup(name, ui):
                    ui.log(
                        b'extension',
                        b'    - the %s extension uisetup failed\n',
                        name,
                    )
                    broken.add(name)
            ui.log(b'extension', b'  > uisetup for %s took %s\n', name, stats)
            loadingtime[name] += stats.elapsed
    ui.log(b'extension', b'> all uisetup took %s\n', alluisetupstats)

    ui.log(b'extension', b'- executing extsetup hooks\n')
    with util.timedcm('all extsetup') as allextetupstats:
        for name in _order[newindex:]:
            if name in broken:
                continue
            ui.log(b'extension', b'  - running extsetup for %s\n', name)
            with util.timedcm('extsetup %s', name) as stats:
                if not _runextsetup(name, ui):
                    ui.log(
                        b'extension',
                        b'    - the %s extension extsetup failed\n',
                        name,
                    )
                    broken.add(name)
            ui.log(b'extension', b'  > extsetup for %s took %s\n', name, stats)
            loadingtime[name] += stats.elapsed
    ui.log(b'extension', b'> all extsetup took %s\n', allextetupstats)

    for name in broken:
        ui.log(b'extension', b'    - disabling broken %s extension\n', name)
        _extensions[name] = None

    # Call aftercallbacks that were never met.
    ui.log(b'extension', b'- executing remaining aftercallbacks\n')
    with util.timedcm('aftercallbacks') as stats:
        for shortname in _aftercallbacks:
            if shortname in _extensions:
                continue

            for fn in _aftercallbacks[shortname]:
                ui.log(
                    b'extension',
                    b'  - extension %s not loaded, notify callbacks\n',
                    shortname,
                )
                fn(loaded=False)
    ui.log(b'extension', b'> remaining aftercallbacks completed in %s\n', stats)

    # loadall() is called multiple times and lingering _aftercallbacks
    # entries could result in double execution. See issue4646.
    _aftercallbacks.clear()

    # delay importing avoids cyclic dependency (especially commands)
    from . import (
        color,
        commands,
        filemerge,
        fileset,
        revset,
        templatefilters,
        templatefuncs,
        templatekw,
    )

    # list of (objname, loadermod, loadername) tuple:
    # - objname is the name of an object in extension module,
    #   from which extra information is loaded
    # - loadermod is the module where loader is placed
    # - loadername is the name of the function,
    #   which takes (ui, extensionname, extraobj) arguments
    ui.log(b'extension', b'- loading extension registration objects\n')
    extraloaders = [
        ('cmdtable', commands, 'loadcmdtable'),
        ('colortable', color, 'loadcolortable'),
        ('filesetpredicate', fileset, 'loadpredicate'),
        ('internalmerge', filemerge, 'loadinternalmerge'),
        ('revsetpredicate', revset, 'loadpredicate'),
        ('templatefilter', templatefilters, 'loadfilter'),
        ('templatefunc', templatefuncs, 'loadfunction'),
        ('templatekeyword', templatekw, 'loadkeyword'),
    ]
    with util.timedcm('load registration objects') as stats:
        _loadextra(ui, newindex, extraloaders)
    ui.log(
        b'extension',
        b'> extension registration object loading took %s\n',
        stats,
    )

    # Report per extension loading time (except reposetup)
    for name in sorted(loadingtime):
        ui.log(
            b'extension',
            b'> extension %s take a total of %s to load\n',
            name,
            util.timecount(loadingtime[name]),
        )

    ui.log(b'extension', b'extension loading complete\n')


def _loadextra(ui, newindex, extraloaders):
    for name in _order[newindex:]:
        module = _extensions[name]
        if not module:
            continue  # loading this module failed

        for objname, loadermod, loadername in extraloaders:
            extraobj = getattr(module, objname, None)
            if extraobj is not None:
                getattr(loadermod, loadername)(ui, name, extraobj)


def afterloaded(extension, callback):
    """Run the specified function after a named extension is loaded.

    If the named extension is already loaded, the callback will be called
    immediately.

    If the named extension never loads, the callback will be called after
    all extensions have been loaded.

    The callback receives the named argument ``loaded``, which is a boolean
    indicating whether the dependent extension actually loaded.
    """

    if extension in _extensions:
        # Report loaded as False if the extension is disabled
        loaded = _extensions[extension] is not None
        callback(loaded=loaded)
    else:
        _aftercallbacks.setdefault(extension, []).append(callback)


def populateui(ui):
    """Run extension hooks on the given ui to populate additional members,
    extend the class dynamically, etc.

    This will be called after the configuration is loaded, and/or extensions
    are loaded. In general, it's once per ui instance, but in command-server
    and hgweb, this may be called more than once with the same ui.
    """
    for name, mod in extensions(ui):
        hook = getattr(mod, 'uipopulate', None)
        if not hook:
            continue
        try:
            hook(ui)
        except Exception as inst:
            ui.traceback(force=True)
            ui.warn(
                _(b'*** failed to populate ui by extension %s: %s\n')
                % (name, stringutil.forcebytestr(inst))
            )


def bind(func, *args):
    """Partial function application

    Returns a new function that is the partial application of args and kwargs
    to func.  For example,

        f(1, 2, bar=3) === bind(f, 1)(2, bar=3)"""
    assert callable(func)

    def closure(*a, **kw):
        return func(*(args + a), **kw)

    return closure


def _updatewrapper(wrap, origfn, unboundwrapper):
    '''Copy and add some useful attributes to wrapper'''
    try:
        wrap.__name__ = origfn.__name__
    except AttributeError:
        pass
    wrap.__module__ = getattr(origfn, '__module__')
    wrap.__doc__ = getattr(origfn, '__doc__')
    wrap.__dict__.update(getattr(origfn, '__dict__', {}))
    wrap._origfunc = origfn
    wrap._unboundwrapper = unboundwrapper


def wrapcommand(table, command, wrapper, synopsis=None, docstring=None):
    '''Wrap the command named `command' in table

    Replace command in the command table with wrapper. The wrapped command will
    be inserted into the command table specified by the table argument.

    The wrapper will be called like

      wrapper(orig, *args, **kwargs)

    where orig is the original (wrapped) function, and *args, **kwargs
    are the arguments passed to it.

    Optionally append to the command synopsis and docstring, used for help.
    For example, if your extension wraps the ``bookmarks`` command to add the
    flags ``--remote`` and ``--all`` you might call this function like so:

      synopsis = ' [-a] [--remote]'
      docstring = """

      The ``remotenames`` extension adds the ``--remote`` and ``--all`` (``-a``)
      flags to the bookmarks command. Either flag will show the remote bookmarks
      known to the repository; ``--remote`` will also suppress the output of the
      local bookmarks.
      """

      extensions.wrapcommand(commands.table, 'bookmarks', exbookmarks,
                             synopsis, docstring)
    '''
    assert callable(wrapper)
    aliases, entry = cmdutil.findcmd(command, table)
    for alias, e in table.items():
        if e is entry:
            key = alias
            break

    origfn = entry[0]
    wrap = functools.partial(
        util.checksignature(wrapper), util.checksignature(origfn)
    )
    _updatewrapper(wrap, origfn, wrapper)
    if docstring is not None:
        wrap.__doc__ += docstring

    newentry = list(entry)
    newentry[0] = wrap
    if synopsis is not None:
        newentry[2] += synopsis
    table[key] = tuple(newentry)
    return entry


def wrapfilecache(cls, propname, wrapper):
    """Wraps a filecache property.

    These can't be wrapped using the normal wrapfunction.
    """
    propname = pycompat.sysstr(propname)
    assert callable(wrapper)
    for currcls in cls.__mro__:
        if propname in currcls.__dict__:
            origfn = currcls.__dict__[propname].func
            assert callable(origfn)

            def wrap(*args, **kwargs):
                return wrapper(origfn, *args, **kwargs)

            currcls.__dict__[propname].func = wrap
            break

    if currcls is object:
        raise AttributeError("type '%s' has no property '%s'" % (cls, propname))


class wrappedfunction:
    '''context manager for temporarily wrapping a function'''

    def __init__(self, container, funcname, wrapper):
        assert callable(wrapper)
        if not isinstance(funcname, str):
            msg = b"wrappedfunction target name should be `str`, not `bytes`"
            raise TypeError(msg)
        self._container = container
        self._funcname = funcname
        self._wrapper = wrapper

    def __enter__(self):
        wrapfunction(self._container, self._funcname, self._wrapper)

    def __exit__(self, exctype, excvalue, traceback):
        unwrapfunction(self._container, self._funcname, self._wrapper)


def wrapfunction(container, funcname, wrapper):
    """Wrap the function named funcname in container

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
    """
    assert callable(wrapper)

    if not isinstance(funcname, str):
        msg = b"wrapfunction target name should be `str`, not `bytes`"
        raise TypeError(msg)

    origfn = getattr(container, funcname)
    assert callable(origfn)
    if inspect.ismodule(container):
        # origfn is not an instance or class method. "partial" can be used.
        # "partial" won't insert a frame in traceback.
        wrap = functools.partial(wrapper, origfn)
    else:
        # "partial" cannot be safely used. Emulate its effect by using "bind".
        # The downside is one more frame in traceback.
        wrap = bind(wrapper, origfn)
    _updatewrapper(wrap, origfn, wrapper)
    setattr(container, funcname, wrap)
    return origfn


def unwrapfunction(container, funcname, wrapper=None):
    """undo wrapfunction

    If wrappers is None, undo the last wrap. Otherwise removes the wrapper
    from the chain of wrappers.

    Return the removed wrapper.
    Raise IndexError if wrapper is None and nothing to unwrap; ValueError if
    wrapper is not None but is not found in the wrapper chain.
    """
    chain = getwrapperchain(container, funcname)
    origfn = chain.pop()
    if wrapper is None:
        wrapper = chain[0]
    chain.remove(wrapper)
    setattr(container, funcname, origfn)
    for w in reversed(chain):
        wrapfunction(container, funcname, w)
    return wrapper


def getwrapperchain(container, funcname):
    """get a chain of wrappers of a function

    Return a list of functions: [newest wrapper, ..., oldest wrapper, origfunc]

    The wrapper functions are the ones passed to wrapfunction, whose first
    argument is origfunc.
    """
    result = []
    fn = getattr(container, funcname)
    while fn:
        assert callable(fn)
        result.append(getattr(fn, '_unboundwrapper', fn))
        fn = getattr(fn, '_origfunc', None)
    return result


def _disabledpaths():
    '''find paths of disabled extensions. returns a dict of {name: path}'''
    import hgext

    exts = {}

    # The hgext might not have a __file__ attribute (e.g. in PyOxidizer) and
    # it might not be on a filesystem even if it does.
    if hasattr(hgext, '__file__'):
        extpath = os.path.dirname(
            util.abspath(pycompat.fsencode(hgext.__file__))
        )
        try:
            files = os.listdir(extpath)
        except OSError:
            pass
        else:
            for e in files:
                if e.endswith(b'.py'):
                    name = e.rsplit(b'.', 1)[0]
                    path = os.path.join(extpath, e)
                else:
                    name = e
                    path = os.path.join(extpath, e, b'__init__.py')
                    if not os.path.exists(path):
                        continue
                if name in exts or name in _order or name == b'__init__':
                    continue
                exts[name] = path

    for name, path in _disabledextensions.items():
        # If no path was provided for a disabled extension (e.g. "color=!"),
        # don't replace the path we already found by the scan above.
        if path:
            exts[name] = path
    return exts


def _moduledoc(file):
    """return the top-level python documentation for the given file

    Loosely inspired by pydoc.source_synopsis(), but rewritten to
    handle triple quotes and to return the whole text instead of just
    the synopsis"""
    result = []

    line = file.readline()
    while line[:1] == b'#' or not line.strip():
        line = file.readline()
        if not line:
            break

    start = line[:3]
    if start == b'"""' or start == b"'''":
        line = line[3:]
        while line:
            if line.rstrip().endswith(start):
                line = line.split(start)[0]
                if line:
                    result.append(line)
                break
            elif not line:
                return None  # unmatched delimiter
            result.append(line)
            line = file.readline()
    else:
        return None

    return b''.join(result)


def _disabledhelp(path):
    '''retrieve help synopsis of a disabled extension (without importing)'''
    try:
        with open(path, b'rb') as src:
            doc = _moduledoc(src)
    except IOError:
        return

    if doc:  # extracting localized synopsis
        return gettext(doc)
    else:
        return _(b'(no help text available)')


def disabled():
    '''find disabled extensions from hgext. returns a dict of {name: desc}'''
    try:
        from hgext import __index__  # pytype: disable=import-error

        return {
            name: gettext(desc)
            for name, desc in __index__.docs.items()
            if name not in _order
        }
    except (ImportError, AttributeError):
        pass

    paths = _disabledpaths()
    if not paths:
        return {}

    exts = {}
    for name, path in paths.items():
        doc = _disabledhelp(path)
        if doc and name != b'__index__':
            exts[name] = stringutil.firstline(doc)

    return exts


def disabled_help(name):
    """Obtain the full help text for a disabled extension, or None."""
    paths = _disabledpaths()
    if name in paths:
        return _disabledhelp(paths[name])
    else:
        try:
            import hgext
            from hgext import __index__  # pytype: disable=import-error

            # The extensions are filesystem based, so either an error occurred
            # or all are enabled.
            if hasattr(hgext, '__file__'):
                return

            if name in _order:  # enabled
                return
            else:
                return gettext(__index__.docs.get(name))
        except (ImportError, AttributeError):
            pass


def _walkcommand(node):
    """Scan @command() decorators in the tree starting at node"""
    todo = collections.deque([node])
    while todo:
        node = todo.popleft()
        if not isinstance(node, ast.FunctionDef):
            todo.extend(ast.iter_child_nodes(node))
            continue
        for d in node.decorator_list:
            if not isinstance(d, ast.Call):
                continue
            if not isinstance(d.func, ast.Name):
                continue
            if d.func.id != 'command':
                continue
            yield d


def _disabledcmdtable(path):
    """Construct a dummy command table without loading the extension module

    This may raise IOError or SyntaxError.
    """
    with open(path, b'rb') as src:
        root = ast.parse(src.read(), path)
    cmdtable = {}

    # Python 3.12 started removing Bytes and Str and deprecate harder
    use_constant = 'Bytes' not in vars(ast)

    for node in _walkcommand(root):
        if not node.args:
            continue
        a = node.args[0]
        if use_constant:  # Valid since Python 3.8
            if isinstance(a, ast.Constant):
                if isinstance(a.value, str):
                    name = pycompat.sysbytes(a.value)
                elif isinstance(a.value, bytes):
                    name = a.value
                else:
                    continue
            else:
                continue
        else:  # Valid until 3.11
            if isinstance(a, ast.Str):
                name = pycompat.sysbytes(a.s)
            elif isinstance(a, ast.Bytes):
                name = a.s
            else:
                continue
        cmdtable[name] = (None, [], b'')
    return cmdtable


def _finddisabledcmd(ui, cmd, name, path, strict):
    try:
        cmdtable = _disabledcmdtable(path)
    except (IOError, SyntaxError):
        return
    try:
        aliases, entry = cmdutil.findcmd(cmd, cmdtable, strict)
    except (error.AmbiguousCommand, error.UnknownCommand):
        return
    for c in aliases:
        if c.startswith(cmd):
            cmd = c
            break
    else:
        cmd = aliases[0]
    doc = _disabledhelp(path)
    return (cmd, name, doc)


def disabledcmd(ui, cmd, strict=False):
    """find cmd from disabled extensions without importing.
    returns (cmdname, extname, doc)"""

    paths = _disabledpaths()
    if not paths:
        raise error.UnknownCommand(cmd)

    ext = None
    # first, search for an extension with the same name as the command
    path = paths.pop(cmd, None)
    if path:
        ext = _finddisabledcmd(ui, cmd, cmd, path, strict=strict)
    if not ext:
        # otherwise, interrogate each extension until there's a match
        for name, path in paths.items():
            ext = _finddisabledcmd(ui, cmd, name, path, strict=strict)
            if ext:
                break
    if ext:
        return ext

    raise error.UnknownCommand(cmd)


def enabled(shortname=True):
    '''return a dict of {name: desc} of extensions'''
    exts = {}
    for ename, ext in extensions():
        doc = gettext(ext.__doc__) or _(b'(no help text available)')
        assert doc is not None  # help pytype
        if shortname:
            ename = ename.split(b'.')[-1]
        exts[ename] = stringutil.firstline(doc).strip()

    return exts


def notloaded():
    '''return short names of extensions that failed to load'''
    return [name for name, mod in _extensions.items() if mod is None]


def moduleversion(module):
    '''return version information from given module as a string'''
    if hasattr(module, 'getversion') and callable(module.getversion):
        try:
            version = module.getversion()
        except Exception:
            version = b'unknown'

    elif hasattr(module, '__version__'):
        version = module.__version__
    else:
        version = b''
    if isinstance(version, (list, tuple)):
        version = b'.'.join(pycompat.bytestr(o) for o in version)
    else:
        # version data should be bytes, but not all extensions are ported
        # to py3.
        version = stringutil.forcebytestr(version)
    return version


def ismoduleinternal(module):
    exttestedwith = getattr(module, 'testedwith', None)
    return exttestedwith == b"ships-with-hg-core"
