# Copyright (C) 2006 - Marco Barisione <marco@barisione.org>
#
# This is a small extension for Mercurial (http://mercurial.selenic.com/)
# that removes files not known to mercurial
#
# This program was inspired by the "cvspurge" script contained in CVS
# utilities (http://www.red-bean.com/cvsutils/).
#
# For help on the usage of "hg purge" use:
#  hg help purge
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, see <http://www.gnu.org/licenses/>.

'''command to delete untracked files from the working directory'''

from mercurial import util, commands, cmdutil, scmutil
from mercurial.i18n import _
import os, stat

cmdtable = {}
command = cmdutil.command(cmdtable)
testedwith = 'internal'

@command('purge|clean',
    [('a', 'abort-on-err', None, _('abort if an error occurs')),
    ('',  'all', None, _('purge ignored files too')),
    ('p', 'print', None, _('print filenames instead of deleting them')),
    ('0', 'print0', None, _('end filenames with NUL, for use with xargs'
                            ' (implies -p/--print)')),
    ] + commands.walkopts,
    _('hg purge [OPTION]... [DIR]...'))
def purge(ui, repo, *dirs, **opts):
    '''removes files not tracked by Mercurial

    Delete files not known to Mercurial. This is useful to test local
    and uncommitted changes in an otherwise-clean source tree.

    This means that purge will delete:

    - Unknown files: files marked with "?" by :hg:`status`
    - Empty directories: in fact Mercurial ignores directories unless
      they contain files under source control management

    But it will leave untouched:

    - Modified and unmodified tracked files
    - Ignored files (unless --all is specified)
    - New files added to the repository (with :hg:`add`)

    If directories are given on the command line, only files in these
    directories are considered.

    Be careful with purge, as you could irreversibly delete some files
    you forgot to add to the repository. If you only want to print the
    list of files that this program would delete, use the --print
    option.
    '''
    act = not opts['print']
    eol = '\n'
    if opts['print0']:
        eol = '\0'
        act = False # --print0 implies --print

    def remove(remove_func, name):
        if act:
            try:
                remove_func(repo.wjoin(name))
            except OSError:
                m = _('%s cannot be removed') % name
                if opts['abort_on_err']:
                    raise util.Abort(m)
                ui.warn(_('warning: %s\n') % m)
        else:
            ui.write('%s%s' % (name, eol))

    def removefile(path):
        try:
            os.remove(path)
        except OSError:
            # read-only files cannot be unlinked under Windows
            s = os.stat(path)
            if (s.st_mode & stat.S_IWRITE) != 0:
                raise
            os.chmod(path, stat.S_IMODE(s.st_mode) | stat.S_IWRITE)
            os.remove(path)

    directories = []
    match = scmutil.match(repo[None], dirs, opts)
    match.dir = directories.append
    status = repo.status(match=match, ignored=opts['all'], unknown=True)

    for f in sorted(status[4] + status[5]):
        ui.note(_('removing file %s\n') % f)
        remove(removefile, f)

    for f in sorted(directories, reverse=True):
        if match(f) and not os.listdir(repo.wjoin(f)):
            ui.note(_('removing directory %s\n') % f)
            remove(os.rmdir, f)
