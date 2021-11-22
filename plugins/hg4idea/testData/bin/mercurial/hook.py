# hook.py - hook support for mercurial
#
# Copyright 2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import contextlib
import errno
import os
import sys

from .i18n import _
from .pycompat import getattr
from . import (
    demandimport,
    encoding,
    error,
    extensions,
    pycompat,
    util,
)
from .utils import (
    procutil,
    resourceutil,
    stringutil,
)


def pythonhook(ui, repo, htype, hname, funcname, args, throw):
    """call python hook. hook is callable object, looked up as
    name in python module. if callable returns "true", hook
    fails, else passes. if hook raises exception, treated as
    hook failure. exception propagates if throw is "true".

    reason for "true" meaning "hook failed" is so that
    unmodified commands (e.g. mercurial.commands.update) can
    be run as hooks without wrappers to convert return values."""

    if callable(funcname):
        obj = funcname
        funcname = pycompat.sysbytes(obj.__module__ + "." + obj.__name__)
    else:
        d = funcname.rfind(b'.')
        if d == -1:
            raise error.HookLoadError(
                _(b'%s hook is invalid: "%s" not in a module')
                % (hname, funcname)
            )
        modname = funcname[:d]
        oldpaths = sys.path
        if resourceutil.mainfrozen():
            # binary installs require sys.path manipulation
            modpath, modfile = os.path.split(modname)
            if modpath and modfile:
                sys.path = sys.path[:] + [modpath]
                modname = modfile
        with demandimport.deactivated():
            try:
                obj = __import__(pycompat.sysstr(modname))
            except (ImportError, SyntaxError):
                e1 = sys.exc_info()
                try:
                    # extensions are loaded with hgext_ prefix
                    obj = __import__("hgext_%s" % pycompat.sysstr(modname))
                except (ImportError, SyntaxError):
                    e2 = sys.exc_info()
                    if ui.tracebackflag:
                        ui.warn(
                            _(
                                b'exception from first failed import '
                                b'attempt:\n'
                            )
                        )
                    ui.traceback(e1)
                    if ui.tracebackflag:
                        ui.warn(
                            _(
                                b'exception from second failed import '
                                b'attempt:\n'
                            )
                        )
                    ui.traceback(e2)

                    if not ui.tracebackflag:
                        tracebackhint = _(
                            b'run with --traceback for stack trace'
                        )
                    else:
                        tracebackhint = None
                    raise error.HookLoadError(
                        _(b'%s hook is invalid: import of "%s" failed')
                        % (hname, modname),
                        hint=tracebackhint,
                    )
        sys.path = oldpaths
        try:
            for p in funcname.split(b'.')[1:]:
                obj = getattr(obj, p)
        except AttributeError:
            raise error.HookLoadError(
                _(b'%s hook is invalid: "%s" is not defined')
                % (hname, funcname)
            )
        if not callable(obj):
            raise error.HookLoadError(
                _(b'%s hook is invalid: "%s" is not callable')
                % (hname, funcname)
            )

    ui.note(_(b"calling hook %s: %s\n") % (hname, funcname))
    starttime = util.timer()

    try:
        r = obj(ui=ui, repo=repo, hooktype=htype, **pycompat.strkwargs(args))
    except Exception as exc:
        if isinstance(exc, error.Abort):
            ui.warn(_(b'error: %s hook failed: %s\n') % (hname, exc.args[0]))
        else:
            ui.warn(
                _(b'error: %s hook raised an exception: %s\n')
                % (hname, stringutil.forcebytestr(exc))
            )
        if throw:
            raise
        if not ui.tracebackflag:
            ui.warn(_(b'(run with --traceback for stack trace)\n'))
        ui.traceback()
        return True, True
    finally:
        duration = util.timer() - starttime
        ui.log(
            b'pythonhook',
            b'pythonhook-%s: %s finished in %0.2f seconds\n',
            htype,
            funcname,
            duration,
        )
    if r:
        if throw:
            raise error.HookAbort(_(b'%s hook failed') % hname)
        ui.warn(_(b'warning: %s hook failed\n') % hname)
    return r, False


def _exthook(ui, repo, htype, name, cmd, args, throw):
    starttime = util.timer()
    env = {}

    # make in-memory changes visible to external process
    if repo is not None:
        tr = repo.currenttransaction()
        repo.dirstate.write(tr)
        if tr and tr.writepending():
            env[b'HG_PENDING'] = repo.root
    env[b'HG_HOOKTYPE'] = htype
    env[b'HG_HOOKNAME'] = name

    if ui.config(b'hooks', b'%s:run-with-plain' % name) == b'auto':
        plain = ui.plain()
    else:
        plain = ui.configbool(b'hooks', b'%s:run-with-plain' % name)
    if plain:
        env[b'HGPLAIN'] = b'1'
    else:
        env[b'HGPLAIN'] = b''

    for k, v in pycompat.iteritems(args):
        # transaction changes can accumulate MBs of data, so skip it
        # for external hooks
        if k == b'changes':
            continue
        if callable(v):
            v = v()
        if isinstance(v, (dict, list)):
            v = stringutil.pprint(v)
        env[b'HG_' + k.upper()] = v

    if ui.configbool(b'hooks', b'tonative.%s' % name, False):
        oldcmd = cmd
        cmd = procutil.shelltonative(cmd, env)
        if cmd != oldcmd:
            ui.note(_(b'converting hook "%s" to native\n') % name)

    ui.note(_(b"running hook %s: %s\n") % (name, cmd))

    if repo:
        cwd = repo.root
    else:
        cwd = encoding.getcwd()
    r = ui.system(cmd, environ=env, cwd=cwd, blockedtag=b'exthook-%s' % (name,))

    duration = util.timer() - starttime
    ui.log(
        b'exthook',
        b'exthook-%s: %s finished in %0.2f seconds\n',
        name,
        cmd,
        duration,
    )
    if r:
        desc = procutil.explainexit(r)
        if throw:
            raise error.HookAbort(_(b'%s hook %s') % (name, desc))
        ui.warn(_(b'warning: %s hook %s\n') % (name, desc))
    return r


# represent an untrusted hook command
_fromuntrusted = object()


def _allhooks(ui):
    """return a list of (hook-id, cmd) pairs sorted by priority"""
    hooks = _hookitems(ui)
    # Be careful in this section, propagating the real commands from untrusted
    # sources would create a security vulnerability, make sure anything altered
    # in that section uses "_fromuntrusted" as its command.
    untrustedhooks = _hookitems(ui, _untrusted=True)
    for name, value in untrustedhooks.items():
        trustedvalue = hooks.get(name, ((), (), name, _fromuntrusted))
        if value != trustedvalue:
            (lp, lo, lk, lv) = trustedvalue
            hooks[name] = (lp, lo, lk, _fromuntrusted)
    # (end of the security sensitive section)
    return [(k, v) for p, o, k, v in sorted(hooks.values())]


def _hookitems(ui, _untrusted=False):
    """return all hooks items ready to be sorted"""
    hooks = {}
    for name, cmd in ui.configitems(b'hooks', untrusted=_untrusted):
        if (
            name.startswith(b'priority.')
            or name.startswith(b'tonative.')
            or b':' in name
        ):
            continue

        priority = ui.configint(b'hooks', b'priority.%s' % name, 0)
        hooks[name] = ((-priority,), (len(hooks),), name, cmd)
    return hooks


_redirect = False


def redirect(state):
    global _redirect
    _redirect = state


def hashook(ui, htype):
    """return True if a hook is configured for 'htype'"""
    if not ui.callhooks:
        return False
    for hname, cmd in _allhooks(ui):
        if hname.split(b'.')[0] == htype and cmd:
            return True
    return False


def hook(ui, repo, htype, throw=False, **args):
    if not ui.callhooks:
        return False

    hooks = []
    for hname, cmd in _allhooks(ui):
        if hname.split(b'.')[0] == htype and cmd:
            hooks.append((hname, cmd))

    res = runhooks(ui, repo, htype, hooks, throw=throw, **args)
    r = False
    for hname, cmd in hooks:
        r = res[hname][0] or r
    return r


@contextlib.contextmanager
def redirect_stdio():
    """Redirects stdout to stderr, if possible."""

    oldstdout = -1
    try:
        if _redirect:
            try:
                stdoutno = procutil.stdout.fileno()
                stderrno = procutil.stderr.fileno()
                # temporarily redirect stdout to stderr, if possible
                if stdoutno >= 0 and stderrno >= 0:
                    procutil.stdout.flush()
                    oldstdout = os.dup(stdoutno)
                    os.dup2(stderrno, stdoutno)
            except (OSError, AttributeError):
                # files seem to be bogus, give up on redirecting (WSGI, etc)
                pass

        yield

    finally:
        # The stderr is fully buffered on Windows when connected to a pipe.
        # A forcible flush is required to make small stderr data in the
        # remote side available to the client immediately.
        try:
            procutil.stderr.flush()
        except IOError as err:
            if err.errno not in (errno.EPIPE, errno.EIO, errno.EBADF):
                raise error.StdioError(err)

        if _redirect and oldstdout >= 0:
            try:
                procutil.stdout.flush()  # write hook output to stderr fd
            except IOError as err:
                if err.errno not in (errno.EPIPE, errno.EIO, errno.EBADF):
                    raise error.StdioError(err)
            os.dup2(oldstdout, stdoutno)
            os.close(oldstdout)


def runhooks(ui, repo, htype, hooks, throw=False, **args):
    args = pycompat.byteskwargs(args)
    res = {}

    with redirect_stdio():
        for hname, cmd in hooks:
            if cmd is _fromuntrusted:
                if throw:
                    raise error.HookAbort(
                        _(b'untrusted hook %s not executed') % hname,
                        hint=_(b"see 'hg help config.trusted'"),
                    )
                ui.warn(_(b'warning: untrusted hook %s not executed\n') % hname)
                r = 1
                raised = False
            elif callable(cmd):
                r, raised = pythonhook(ui, repo, htype, hname, cmd, args, throw)
            elif cmd.startswith(b'python:'):
                if cmd.count(b':') >= 2:
                    path, cmd = cmd[7:].rsplit(b':', 1)
                    path = util.expandpath(path)
                    if repo:
                        path = os.path.join(repo.root, path)
                    try:
                        mod = extensions.loadpath(path, b'hghook.%s' % hname)
                    except Exception:
                        ui.write(_(b"loading %s hook failed:\n") % hname)
                        raise
                    hookfn = getattr(mod, cmd)
                else:
                    hookfn = cmd[7:].strip()
                r, raised = pythonhook(
                    ui, repo, htype, hname, hookfn, args, throw
                )
            else:
                r = _exthook(ui, repo, htype, hname, cmd, args, throw)
                raised = False

            res[hname] = r, raised

    return res
