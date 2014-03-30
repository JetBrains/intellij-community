# __init__.py - inotify-based status acceleration for Linux
#
# Copyright 2006, 2007, 2008 Bryan O'Sullivan <bos@serpentine.com>
# Copyright 2007, 2008 Brendan Cully <brendan@kublai.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''accelerate status report using Linux's inotify service'''

# todo: socket permissions

from mercurial.i18n import _
from mercurial import util
import server
from client import client, QueryFailed

testedwith = 'internal'

def serve(ui, repo, **opts):
    '''start an inotify server for this repository'''
    server.start(ui, repo.dirstate, repo.root, opts)

def debuginotify(ui, repo, **opts):
    '''debugging information for inotify extension

    Prints the list of directories being watched by the inotify server.
    '''
    cli = client(ui, repo)
    response = cli.debugquery()

    ui.write(_('directories being watched:\n'))
    for path in response:
        ui.write(('  %s/\n') % path)

def reposetup(ui, repo):
    if not util.safehasattr(repo, 'dirstate'):
        return

    class inotifydirstate(repo.dirstate.__class__):

        # We'll set this to false after an unsuccessful attempt so that
        # next calls of status() within the same instance don't try again
        # to start an inotify server if it won't start.
        _inotifyon = True

        def status(self, match, subrepos, ignored, clean, unknown):
            files = match.files()
            if '.' in files:
                files = []
            if (self._inotifyon and not ignored and not subrepos and
                not self._dirty):
                cli = client(ui, repo)
                try:
                    result = cli.statusquery(files, match, False,
                                            clean, unknown)
                except QueryFailed, instr:
                    ui.debug(str(instr))
                    # don't retry within the same hg instance
                    inotifydirstate._inotifyon = False
                    pass
                else:
                    if ui.config('inotify', 'debug'):
                        r2 = super(inotifydirstate, self).status(
                            match, [], False, clean, unknown)
                        for c, a, b in zip('LMARDUIC', result, r2):
                            for f in a:
                                if f not in b:
                                    ui.warn('*** inotify: %s +%s\n' % (c, f))
                            for f in b:
                                if f not in a:
                                    ui.warn('*** inotify: %s -%s\n' % (c, f))
                        result = r2
                    return result
            return super(inotifydirstate, self).status(
                match, subrepos, ignored, clean, unknown)

    repo.dirstate.__class__ = inotifydirstate

cmdtable = {
    'debuginotify':
        (debuginotify, [], ('hg debuginotify')),
    '^inserve':
        (serve,
         [('d', 'daemon', None, _('run server in background')),
          ('', 'daemon-pipefds', '',
           _('used internally by daemon mode'), _('NUM')),
          ('t', 'idle-timeout', '',
           _('minutes to sit idle before exiting'), _('NUM')),
          ('', 'pid-file', '',
           _('name of file to write process ID to'), _('FILE'))],
         _('hg inserve [OPTION]...')),
    }
