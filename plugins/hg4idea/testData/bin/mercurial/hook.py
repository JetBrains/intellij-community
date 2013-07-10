# hook.py - hook support for mercurial
#
# Copyright 2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import os, sys, time, types
import extensions, util, demandimport

def _pythonhook(ui, repo, name, hname, funcname, args, throw):
    '''call python hook. hook is callable object, looked up as
    name in python module. if callable returns "true", hook
    fails, else passes. if hook raises exception, treated as
    hook failure. exception propagates if throw is "true".

    reason for "true" meaning "hook failed" is so that
    unmodified commands (e.g. mercurial.commands.update) can
    be run as hooks without wrappers to convert return values.'''

    ui.note(_("calling hook %s: %s\n") % (hname, funcname))
    starttime = time.time()

    obj = funcname
    if not util.safehasattr(obj, '__call__'):
        d = funcname.rfind('.')
        if d == -1:
            raise util.Abort(_('%s hook is invalid ("%s" not in '
                               'a module)') % (hname, funcname))
        modname = funcname[:d]
        oldpaths = sys.path
        if util.mainfrozen():
            # binary installs require sys.path manipulation
            modpath, modfile = os.path.split(modname)
            if modpath and modfile:
                sys.path = sys.path[:] + [modpath]
                modname = modfile
        try:
            demandimport.disable()
            obj = __import__(modname)
            demandimport.enable()
        except ImportError:
            e1 = sys.exc_type, sys.exc_value, sys.exc_traceback
            try:
                # extensions are loaded with hgext_ prefix
                obj = __import__("hgext_%s" % modname)
                demandimport.enable()
            except ImportError:
                demandimport.enable()
                e2 = sys.exc_type, sys.exc_value, sys.exc_traceback
                if ui.tracebackflag:
                    ui.warn(_('exception from first failed import attempt:\n'))
                ui.traceback(e1)
                if ui.tracebackflag:
                    ui.warn(_('exception from second failed import attempt:\n'))
                ui.traceback(e2)
                raise util.Abort(_('%s hook is invalid '
                                   '(import of "%s" failed)') %
                                 (hname, modname))
        sys.path = oldpaths
        try:
            for p in funcname.split('.')[1:]:
                obj = getattr(obj, p)
        except AttributeError:
            raise util.Abort(_('%s hook is invalid '
                               '("%s" is not defined)') %
                             (hname, funcname))
        if not util.safehasattr(obj, '__call__'):
            raise util.Abort(_('%s hook is invalid '
                               '("%s" is not callable)') %
                             (hname, funcname))
    try:
        try:
            # redirect IO descriptors to the ui descriptors so hooks
            # that write directly to these don't mess up the command
            # protocol when running through the command server
            old = sys.stdout, sys.stderr, sys.stdin
            sys.stdout, sys.stderr, sys.stdin = ui.fout, ui.ferr, ui.fin

            r = obj(ui=ui, repo=repo, hooktype=name, **args)
        except KeyboardInterrupt:
            raise
        except Exception, exc:
            if isinstance(exc, util.Abort):
                ui.warn(_('error: %s hook failed: %s\n') %
                             (hname, exc.args[0]))
            else:
                ui.warn(_('error: %s hook raised an exception: '
                               '%s\n') % (hname, exc))
            if throw:
                raise
            ui.traceback()
            return True
    finally:
        sys.stdout, sys.stderr, sys.stdin = old
        duration = time.time() - starttime
        readablefunc = funcname
        if isinstance(funcname, types.FunctionType):
            readablefunc = funcname.__module__ + "." + funcname.__name__
        ui.log('pythonhook', 'pythonhook-%s: %s finished in %0.2f seconds\n',
               name, readablefunc, duration)
    if r:
        if throw:
            raise util.Abort(_('%s hook failed') % hname)
        ui.warn(_('warning: %s hook failed\n') % hname)
    return r

def _exthook(ui, repo, name, cmd, args, throw):
    ui.note(_("running hook %s: %s\n") % (name, cmd))

    starttime = time.time()
    env = {}
    for k, v in args.iteritems():
        if util.safehasattr(v, '__call__'):
            v = v()
        if isinstance(v, dict):
            # make the dictionary element order stable across Python
            # implementations
            v = ('{' +
                 ', '.join('%r: %r' % i for i in sorted(v.iteritems())) +
                 '}')
        env['HG_' + k.upper()] = v

    if repo:
        cwd = repo.root
    else:
        cwd = os.getcwd()
    if 'HG_URL' in env and env['HG_URL'].startswith('remote:http'):
        r = util.system(cmd, environ=env, cwd=cwd, out=ui)
    else:
        r = util.system(cmd, environ=env, cwd=cwd, out=ui.fout)

    duration = time.time() - starttime
    ui.log('exthook', 'exthook-%s: %s finished in %0.2f seconds\n',
           name, cmd, duration)
    if r:
        desc, r = util.explainexit(r)
        if throw:
            raise util.Abort(_('%s hook %s') % (name, desc))
        ui.warn(_('warning: %s hook %s\n') % (name, desc))
    return r

def _allhooks(ui):
    hooks = []
    for name, cmd in ui.configitems('hooks'):
        if not name.startswith('priority'):
            priority = ui.configint('hooks', 'priority.%s' % name, 0)
            hooks.append((-priority, len(hooks), name, cmd))
    return [(k, v) for p, o, k, v in sorted(hooks)]

_redirect = False
def redirect(state):
    global _redirect
    _redirect = state

def hook(ui, repo, name, throw=False, **args):
    if not ui.callhooks:
        return False

    r = False
    oldstdout = -1

    try:
        for hname, cmd in _allhooks(ui):
            if hname.split('.')[0] != name or not cmd:
                continue

            if oldstdout == -1 and _redirect:
                try:
                    stdoutno = sys.__stdout__.fileno()
                    stderrno = sys.__stderr__.fileno()
                    # temporarily redirect stdout to stderr, if possible
                    if stdoutno >= 0 and stderrno >= 0:
                        sys.__stdout__.flush()
                        oldstdout = os.dup(stdoutno)
                        os.dup2(stderrno, stdoutno)
                except (OSError, AttributeError):
                    # files seem to be bogus, give up on redirecting (WSGI, etc)
                    pass

            if util.safehasattr(cmd, '__call__'):
                r = _pythonhook(ui, repo, name, hname, cmd, args, throw) or r
            elif cmd.startswith('python:'):
                if cmd.count(':') >= 2:
                    path, cmd = cmd[7:].rsplit(':', 1)
                    path = util.expandpath(path)
                    if repo:
                        path = os.path.join(repo.root, path)
                    try:
                        mod = extensions.loadpath(path, 'hghook.%s' % hname)
                    except Exception:
                        ui.write(_("loading %s hook failed:\n") % hname)
                        raise
                    hookfn = getattr(mod, cmd)
                else:
                    hookfn = cmd[7:].strip()
                r = _pythonhook(ui, repo, name, hname, hookfn, args, throw) or r
            else:
                r = _exthook(ui, repo, hname, cmd, args, throw) or r
    finally:
        if _redirect and oldstdout >= 0:
            os.dup2(oldstdout, stdoutno)
            os.close(oldstdout)

    return r
