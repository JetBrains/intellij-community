# blackbox.py - log repository events to a file for post-mortem debugging
#
# Copyright 2010 Nicolas Dumazet
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""log repository events to a blackbox for debugging

Logs event information to .hg/blackbox.log to help debug and diagnose problems.
The events that get logged can be configured via the blackbox.track config key.
Examples::

  [blackbox]
  track = *

  [blackbox]
  track = command, commandfinish, commandexception, exthook, pythonhook

  [blackbox]
  track = incoming

  [blackbox]
  # limit the size of a log file
  maxsize = 1.5 MB
  # rotate up to N log files when the current one gets too big
  maxfiles = 3

"""

from mercurial import util, cmdutil
from mercurial.i18n import _
import errno, os, re

cmdtable = {}
command = cmdutil.command(cmdtable)
testedwith = 'internal'
lastblackbox = None

def wrapui(ui):
    class blackboxui(ui.__class__):
        @util.propertycache
        def track(self):
            return self.configlist('blackbox', 'track', ['*'])

        def _openlogfile(self):
            def rotate(oldpath, newpath):
                try:
                    os.unlink(newpath)
                except OSError, err:
                    if err.errno != errno.ENOENT:
                        self.debug("warning: cannot remove '%s': %s\n" %
                                   (newpath, err.strerror))
                try:
                    if newpath:
                        os.rename(oldpath, newpath)
                except OSError, err:
                    if err.errno != errno.ENOENT:
                        self.debug("warning: cannot rename '%s' to '%s': %s\n" %
                                   (newpath, oldpath, err.strerror))

            fp = self._bbopener('blackbox.log', 'a')
            maxsize = self.configbytes('blackbox', 'maxsize', 1048576)
            if maxsize > 0:
                st = os.fstat(fp.fileno())
                if st.st_size >= maxsize:
                    path = fp.name
                    fp.close()
                    maxfiles = self.configint('blackbox', 'maxfiles', 7)
                    for i in xrange(maxfiles - 1, 1, -1):
                        rotate(oldpath='%s.%d' % (path, i - 1),
                               newpath='%s.%d' % (path, i))
                    rotate(oldpath=path,
                           newpath=maxfiles > 0 and path + '.1')
                    fp = self._bbopener('blackbox.log', 'a')
            return fp

        def log(self, event, *msg, **opts):
            global lastblackbox
            super(blackboxui, self).log(event, *msg, **opts)

            if not '*' in self.track and not event in self.track:
                return

            if util.safehasattr(self, '_blackbox'):
                blackbox = self._blackbox
            elif util.safehasattr(self, '_bbopener'):
                try:
                    self._blackbox = self._openlogfile()
                except (IOError, OSError), err:
                    self.debug('warning: cannot write to blackbox.log: %s\n' %
                               err.strerror)
                    del self._bbopener
                    self._blackbox = None
                blackbox = self._blackbox
            else:
                # certain ui instances exist outside the context of
                # a repo, so just default to the last blackbox that
                # was seen.
                blackbox = lastblackbox

            if blackbox:
                date = util.datestr(None, '%Y/%m/%d %H:%M:%S')
                user = util.getuser()
                formattedmsg = msg[0] % msg[1:]
                try:
                    blackbox.write('%s %s> %s' % (date, user, formattedmsg))
                except IOError, err:
                    self.debug('warning: cannot write to blackbox.log: %s\n' %
                               err.strerror)
                lastblackbox = blackbox

        def setrepo(self, repo):
            self._bbopener = repo.opener

    ui.__class__ = blackboxui

def uisetup(ui):
    wrapui(ui)

def reposetup(ui, repo):
    # During 'hg pull' a httppeer repo is created to represent the remote repo.
    # It doesn't have a .hg directory to put a blackbox in, so we don't do
    # the blackbox setup for it.
    if not repo.local():
        return

    if util.safehasattr(ui, 'setrepo'):
        ui.setrepo(repo)

@command('^blackbox',
    [('l', 'limit', 10, _('the number of events to show')),
    ],
    _('hg blackbox [OPTION]...'))
def blackbox(ui, repo, *revs, **opts):
    '''view the recent repository events
    '''

    if not os.path.exists(repo.join('blackbox.log')):
        return

    limit = opts.get('limit')
    blackbox = repo.opener('blackbox.log', 'r')
    lines = blackbox.read().split('\n')

    count = 0
    output = []
    for line in reversed(lines):
        if count >= limit:
            break

        # count the commands by matching lines like: 2013/01/23 19:13:36 root>
        if re.match('^\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2} .*> .*', line):
            count += 1
        output.append(line)

    ui.status('\n'.join(reversed(output)))
