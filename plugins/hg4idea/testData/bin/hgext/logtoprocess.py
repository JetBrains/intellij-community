# logtoprocess.py - send ui.log() data to a subprocess
#
# Copyright 2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""send ui.log() data to a subprocess (EXPERIMENTAL)

This extension lets you specify a shell command per ui.log() event,
sending all remaining arguments to as environment variables to that command.

Positional arguments construct a log message, which is passed in the `MSG1`
environment variables. Each keyword argument is set as a `OPT_UPPERCASE_KEY`
variable (so the key is uppercased, and prefixed with `OPT_`). The original
event name is passed in the `EVENT` environment variable, and the process ID
of mercurial is given in `HGPID`.

So given a call `ui.log('foo', 'bar %s\n', 'baz', spam='eggs'), a script
configured for the `foo` event can expect an environment with `MSG1=bar baz`,
and `OPT_SPAM=eggs`.

Scripts are configured in the `[logtoprocess]` section, each key an event name.
For example::

  [logtoprocess]
  commandexception = echo "$MSG1" > /var/log/mercurial_exceptions.log

would log the warning message and traceback of any failed command dispatch.

Scripts are run asynchronously as detached daemon processes; mercurial will
not ensure that they exit cleanly.

"""


import os

from mercurial.utils import procutil

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


class processlogger:
    """Map log events to external commands

    Arguments are passed on as environment variables.
    """

    def __init__(self, ui):
        self._scripts = dict(ui.configitems(b'logtoprocess'))

    def tracked(self, event):
        return bool(self._scripts.get(event))

    def log(self, ui, event, msg, opts):
        script = self._scripts[event]
        maxmsg = 100000
        if len(msg) > maxmsg:
            # Each env var has a 128KiB limit on linux. msg can be long, in
            # particular for command event, where it's the full command line.
            # Prefer truncating the message than raising "Argument list too
            # long" error.
            msg = msg[:maxmsg] + b' (truncated)'
        env = {
            b'EVENT': event,
            b'HGPID': os.getpid(),
            b'MSG1': msg,
        }
        # keyword arguments get prefixed with OPT_ and uppercased
        env.update(
            (b'OPT_%s' % key.upper(), value) for key, value in opts.items()
        )
        fullenv = procutil.shellenviron(env)
        procutil.runbgcommand(script, fullenv, shell=True)


def uipopulate(ui):
    ui.setlogger(b'logtoprocess', processlogger(ui))
