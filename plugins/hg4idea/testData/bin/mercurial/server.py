# server.py - utility and factory of server
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import os

from .i18n import _
from .pycompat import open

from . import (
    chgserver,
    cmdutil,
    commandserver,
    error,
    hgweb,
    pycompat,
    util,
)

from .utils import (
    procutil,
    urlutil,
)


def runservice(
    opts,
    parentfn=None,
    initfn=None,
    runfn=None,
    logfile=None,
    runargs=None,
    appendpid=False,
):
    '''Run a command as a service.'''

    postexecargs = {}

    if opts[b'daemon_postexec']:
        for inst in opts[b'daemon_postexec']:
            if inst.startswith(b'unlink:'):
                postexecargs[b'unlink'] = inst[7:]
            elif inst.startswith(b'chdir:'):
                postexecargs[b'chdir'] = inst[6:]
            elif inst != b'none':
                raise error.Abort(
                    _(b'invalid value for --daemon-postexec: %s') % inst
                )

    # When daemonized on Windows, redirect stdout/stderr to the lockfile (which
    # gets cleaned up after the child is up and running), so that the parent can
    # read and print the error if this child dies early.  See 594dd384803c.  On
    # other platforms, the child can write to the parent's stdio directly, until
    # it is redirected prior to runfn().
    if pycompat.iswindows and opts[b'daemon_postexec']:
        if b'unlink' in postexecargs and os.path.exists(
            postexecargs[b'unlink']
        ):
            procutil.stdout.flush()
            procutil.stderr.flush()

            fd = os.open(
                postexecargs[b'unlink'], os.O_WRONLY | os.O_APPEND | os.O_BINARY
            )
            try:
                os.dup2(fd, procutil.stdout.fileno())
                os.dup2(fd, procutil.stderr.fileno())
            finally:
                os.close(fd)

    def writepid(pid):
        if opts[b'pid_file']:
            if appendpid:
                mode = b'ab'
            else:
                mode = b'wb'
            fp = open(opts[b'pid_file'], mode)
            fp.write(b'%d\n' % pid)
            fp.close()

    if opts[b'daemon'] and not opts[b'daemon_postexec']:
        # Signal child process startup with file removal
        lockfd, lockpath = pycompat.mkstemp(prefix=b'hg-service-')
        os.close(lockfd)
        try:
            if not runargs:
                runargs = procutil.hgcmd() + pycompat.sysargv[1:]
            runargs.append(b'--daemon-postexec=unlink:%s' % lockpath)
            # Don't pass --cwd to the child process, because we've already
            # changed directory.
            for i in range(1, len(runargs)):
                if runargs[i].startswith(b'--cwd='):
                    del runargs[i]
                    break
                elif runargs[i].startswith(b'--cwd'):
                    del runargs[i : i + 2]
                    break

            def condfn():
                return not os.path.exists(lockpath)

            pid = procutil.rundetached(runargs, condfn)
            if pid < 0:
                # If the daemonized process managed to write out an error msg,
                # report it.
                if pycompat.iswindows and os.path.exists(lockpath):
                    with open(lockpath, b'rb') as log:
                        for line in log:
                            procutil.stderr.write(line)
                raise error.Abort(_(b'child process failed to start'))
            writepid(pid)
        finally:
            util.tryunlink(lockpath)
        if parentfn:
            return parentfn(pid)
        else:
            return

    if initfn:
        initfn()

    if not opts[b'daemon']:
        writepid(procutil.getpid())

    if opts[b'daemon_postexec']:
        try:
            os.setsid()
        except AttributeError:
            pass

        if b'chdir' in postexecargs:
            os.chdir(postexecargs[b'chdir'])
        procutil.hidewindow()
        procutil.stdout.flush()
        procutil.stderr.flush()

        nullfd = os.open(os.devnull, os.O_RDWR)
        logfilefd = nullfd
        if logfile:
            logfilefd = os.open(
                logfile, os.O_RDWR | os.O_CREAT | os.O_APPEND, 0o666
            )
        os.dup2(nullfd, procutil.stdin.fileno())
        os.dup2(logfilefd, procutil.stdout.fileno())
        os.dup2(logfilefd, procutil.stderr.fileno())
        stdio = (
            procutil.stdin.fileno(),
            procutil.stdout.fileno(),
            procutil.stderr.fileno(),
        )
        if nullfd not in stdio:
            os.close(nullfd)
        if logfile and logfilefd not in stdio:
            os.close(logfilefd)

        # Only unlink after redirecting stdout/stderr, so Windows doesn't
        # complain about a sharing violation.
        if b'unlink' in postexecargs:
            os.unlink(postexecargs[b'unlink'])

    if runfn:
        return runfn()


_cmdservicemap = {
    b'chgunix': chgserver.chgunixservice,
    b'pipe': commandserver.pipeservice,
    b'unix': commandserver.unixforkingservice,
}


def _createcmdservice(ui, repo, opts):
    mode = opts[b'cmdserver']
    try:
        servicefn = _cmdservicemap[mode]
    except KeyError:
        raise error.Abort(_(b'unknown mode %s') % mode)
    commandserver.setuplogging(ui, repo)
    return servicefn(ui, repo, opts)


def _createhgwebservice(ui, repo, opts):
    # this way we can check if something was given in the command-line
    if opts.get(b'port'):
        opts[b'port'] = urlutil.getport(opts.get(b'port'))

    alluis = {ui}
    if repo:
        baseui = repo.baseui
        alluis.update([repo.baseui, repo.ui])
    else:
        baseui = ui
    webconf = opts.get(b'web_conf') or opts.get(b'webdir_conf')
    if webconf:
        if opts.get(b'subrepos'):
            raise error.Abort(_(b'--web-conf cannot be used with --subrepos'))

        # load server settings (e.g. web.port) to "copied" ui, which allows
        # hgwebdir to reload webconf cleanly
        servui = ui.copy()
        servui.readconfig(webconf, sections=[b'web'])
        alluis.add(servui)
    elif opts.get(b'subrepos'):
        servui = ui

        # If repo is None, hgweb.createapp() already raises a proper abort
        # message as long as webconf is None.
        if repo:
            webconf = dict()
            cmdutil.addwebdirpath(repo, b"", webconf)
    else:
        servui = ui

    optlist = (
        b"name templates style address port prefix ipv6"
        b" accesslog errorlog certificate encoding"
    )
    for o in optlist.split():
        val = opts.get(o, b'')
        if val in (None, b''):  # should check against default options instead
            continue
        for u in alluis:
            u.setconfig(b"web", o, val, b'serve')

    app = hgweb.createapp(baseui, repo, webconf)
    return hgweb.httpservice(servui, app, opts)


def createservice(ui, repo, opts):
    if opts[b"cmdserver"]:
        return _createcmdservice(ui, repo, opts)
    else:
        return _createhgwebservice(ui, repo, opts)
