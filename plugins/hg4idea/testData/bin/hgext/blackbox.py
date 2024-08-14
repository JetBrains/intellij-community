# blackbox.py - log repository events to a file for post-mortem debugging
#
# Copyright 2010 Nicolas Dumazet
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""log repository events to a blackbox for debugging

Logs event information to .hg/blackbox.log to help debug and diagnose problems.
The events that get logged can be configured via the blackbox.track and
blackbox.ignore config keys.

Examples::

  [blackbox]
  track = *
  ignore = pythonhook
  # dirty is *EXPENSIVE* (slow);
  # each log entry indicates `+` if the repository is dirty, like :hg:`id`.
  dirty = True
  # record the source of log messages
  logsource = True

  [blackbox]
  track = command, commandfinish, commandexception, exthook, pythonhook

  [blackbox]
  track = incoming

  [blackbox]
  # limit the size of a log file
  maxsize = 1.5 MB
  # rotate up to N log files when the current one gets too big
  maxfiles = 3

  [blackbox]
  # Include microseconds in log entries with %f (see Python function
  # datetime.datetime.strftime)
  date-format = %Y-%m-%d @ %H:%M:%S.%f

"""


import re

from mercurial.i18n import _
from mercurial.node import hex

from mercurial import (
    encoding,
    loggingutil,
    registrar,
)
from mercurial.utils import (
    dateutil,
    procutil,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

cmdtable = {}
command = registrar.command(cmdtable)

_lastlogger = loggingutil.proxylogger()


class blackboxlogger:
    def __init__(self, ui, repo):
        self._repo = repo
        self._trackedevents = set(ui.configlist(b'blackbox', b'track'))
        self._ignoredevents = set(ui.configlist(b'blackbox', b'ignore'))
        self._maxfiles = ui.configint(b'blackbox', b'maxfiles')
        self._maxsize = ui.configbytes(b'blackbox', b'maxsize')
        self._inlog = False

    def tracked(self, event):
        return (
            b'*' in self._trackedevents and event not in self._ignoredevents
        ) or event in self._trackedevents

    def log(self, ui, event, msg, opts):
        # self._log() -> ctx.dirty() may create new subrepo instance, which
        # ui is derived from baseui. So the recursion guard in ui.log()
        # doesn't work as it's local to the ui instance.
        if self._inlog:
            return
        self._inlog = True
        try:
            self._log(ui, event, msg, opts)
        finally:
            self._inlog = False

    def _log(self, ui, event, msg, opts):
        default = ui.configdate(b'devel', b'default-date')
        dateformat = ui.config(b'blackbox', b'date-format')
        debug_to_stderr = ui.configbool(b'blackbox', b'debug.to-stderr')
        if dateformat:
            date = dateutil.datestr(default, dateformat)
        else:
            # We want to display milliseconds (more precision seems
            # unnecessary).  Since %.3f is not supported, use %f and truncate
            # microseconds.
            date = dateutil.datestr(default, b'%Y-%m-%d %H:%M:%S.%f')[:-3]
        user = procutil.getuser()
        pid = b'%d' % procutil.getpid()
        changed = b''
        ctx = self._repo[None]
        parents = ctx.parents()
        rev = b'+'.join([hex(p.node()) for p in parents])
        if ui.configbool(b'blackbox', b'dirty') and ctx.dirty(
            missing=True, merge=False, branch=False
        ):
            changed = b'+'
        if ui.configbool(b'blackbox', b'logsource'):
            src = b' [%s]' % event
        else:
            src = b''
        try:
            fmt = b'%s %s @%s%s (%s)%s> %s'
            args = (date, user, rev, changed, pid, src, msg)
            with loggingutil.openlogfile(
                ui,
                self._repo.vfs,
                name=b'blackbox.log',
                maxfiles=self._maxfiles,
                maxsize=self._maxsize,
            ) as fp:
                msg = fmt % args
                fp.write(msg)
                if debug_to_stderr:
                    ui.write_err(msg)
        except (IOError, OSError) as err:
            # deactivate this to avoid failed logging again
            self._trackedevents.clear()
            ui.debug(
                b'warning: cannot write to blackbox.log: %s\n'
                % encoding.strtolocal(err.strerror)
            )
            return
        _lastlogger.logger = self


def uipopulate(ui):
    ui.setlogger(b'blackbox', _lastlogger)


def reposetup(ui, repo):
    # During 'hg pull' a httppeer repo is created to represent the remote repo.
    # It doesn't have a .hg directory to put a blackbox in, so we don't do
    # the blackbox setup for it.
    if not repo.local():
        return

    # Since blackbox.log is stored in the repo directory, the logger should be
    # instantiated per repository.
    logger = blackboxlogger(ui, repo)
    ui.setlogger(b'blackbox', logger)

    # Set _lastlogger even if ui.log is not called. This gives blackbox a
    # fallback place to log
    if _lastlogger.logger is None:
        _lastlogger.logger = logger

    repo._wlockfreeprefix.add(b'blackbox.log')


@command(
    b'blackbox',
    [
        (b'l', b'limit', 10, _(b'the number of events to show')),
    ],
    _(b'hg blackbox [OPTION]...'),
    helpcategory=command.CATEGORY_MAINTENANCE,
    helpbasic=True,
)
def blackbox(ui, repo, *revs, **opts):
    """view the recent repository events"""

    if not repo.vfs.exists(b'blackbox.log'):
        return

    limit = opts.get('limit')
    assert limit is not None  # help pytype

    fp = repo.vfs(b'blackbox.log', b'r')
    lines = fp.read().split(b'\n')

    count = 0
    output = []
    for line in reversed(lines):
        if count >= limit:
            break

        # count the commands by matching lines like:
        # 2013/01/23 19:13:36 root>
        # 2013/01/23 19:13:36 root (1234)>
        # 2013/01/23 19:13:36 root @0000000000000000000000000000000000000000 (1234)>
        # 2013-01-23 19:13:36.000 root @0000000000000000000000000000000000000000 (1234)>
        if re.match(
            br'^\d{4}[-/]\d{2}[-/]\d{2} \d{2}:\d{2}:\d{2}(.\d*)? .*> .*', line
        ):
            count += 1
        output.append(line)

    ui.status(b'\n'.join(reversed(output)))
