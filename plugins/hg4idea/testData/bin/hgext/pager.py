# pager.py - display output using a pager
#
# Copyright 2008 David Soria Parra <dsp@php.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
#
# To load the extension, add it to your .hgrc file:
#
#   [extension]
#   pager =
#
# Run "hg help pager" to get info on configuration.

'''browse command output with an external pager

To set the pager that should be used, set the application variable::

  [pager]
  pager = LESS='FSRX' less

If no pager is set, the pager extensions uses the environment variable
$PAGER. If neither pager.pager, nor $PAGER is set, no pager is used.

If you notice "BROKEN PIPE" error messages, you can disable them by
setting::

  [pager]
  quiet = True

You can disable the pager for certain commands by adding them to the
pager.ignore list::

  [pager]
  ignore = version, help, update

You can also enable the pager only for certain commands using
pager.attend. Below is the default list of commands to be paged::

  [pager]
  attend = annotate, cat, diff, export, glog, log, qdiff

Setting pager.attend to an empty value will cause all commands to be
paged.

If pager.attend is present, pager.ignore will be ignored.

To ignore global commands like "hg version" or "hg help", you have to
specify them in the global .hgrc
'''

import sys, os, signal
from mercurial import dispatch, util, extensions

def uisetup(ui):
    def pagecmd(orig, ui, options, cmd, cmdfunc):
        p = ui.config("pager", "pager", os.environ.get("PAGER"))
        if p and sys.stdout.isatty() and '--debugger' not in sys.argv:
            attend = ui.configlist('pager', 'attend', attended)
            if (cmd in attend or
                (cmd not in ui.configlist('pager', 'ignore') and not attend)):
                ui.setconfig('ui', 'interactive', False)
                sys.stderr = sys.stdout = util.popen(p, "wb")
                if ui.configbool('pager', 'quiet'):
                    signal.signal(signal.SIGPIPE, signal.SIG_DFL)
        return orig(ui, options, cmd, cmdfunc)

    extensions.wrapfunction(dispatch, '_runcommand', pagecmd)

attended = ['annotate', 'cat', 'diff', 'export', 'glog', 'log', 'qdiff']
