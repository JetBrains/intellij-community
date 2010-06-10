# progress.py show progress bars for some actions
#
# Copyright (C) 2010 Augie Fackler <durin42@gmail.com>
#
# This program is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the
# Free Software Foundation; either version 2 of the License, or (at your
# option) any later version.
#
# This program is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General
# Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

"""show progress bars for some actions

This extension uses the progress information logged by hg commands
to draw progress bars that are as informative as possible. Some progress
bars only offer indeterminate information, while others have a definite
end point.

The following settings are available::

  [progress]
  delay = 3 # number of seconds (float) before showing the progress bar
  refresh = 0.1 # time in seconds between refreshes of the progress bar
  format = topic bar number # format of the progress bar
  width = <none> # if set, the maximum width of the progress information
                 # (that is, min(width, term width) will be used)
  clear-complete = True # clear the progress bar after it's done
  disable = False # if true, don't show a progress bar
  assume-tty = False # if true, ALWAYS show a progress bar, unless
                     # disable is given

Valid entries for the format field are topic, bar, number, unit, and
item. item defaults to the last 20 characters of the item, but this
can be changed by adding either ``-<num>`` which would take the last
num characters, or ``+<num>`` for the first num characters.
"""

import sys
import time

from mercurial import extensions
from mercurial import util

def spacejoin(*args):
    return ' '.join(s for s in args if s)

class progbar(object):
    def __init__(self, ui):
        self.ui = ui
        self.resetstate()

    def resetstate(self):
        self.topics = []
        self.printed = False
        self.lastprint = time.time() + float(self.ui.config(
            'progress', 'delay', default=3))
        self.indetcount = 0
        self.refresh = float(self.ui.config(
            'progress', 'refresh', default=0.1))
        self.order = self.ui.configlist(
            'progress', 'format',
            default=['topic', 'bar', 'number'])

    def show(self, topic, pos, item, unit, total):
        termwidth = self.width()
        self.printed = True
        head = ''
        needprogress = False
        tail = ''
        for indicator in self.order:
            add = ''
            if indicator == 'topic':
                add = topic
            elif indicator == 'number':
                if total:
                    add = ('% ' + str(len(str(total))) +
                           's/%s') % (pos, total)
                else:
                    add = str(pos)
            elif indicator.startswith('item') and item:
                slice = 'end'
                if '-' in indicator:
                    wid = int(indicator.split('-')[1])
                elif '+' in indicator:
                    slice = 'beginning'
                    wid = int(indicator.split('+')[1])
                else:
                    wid = 20
                if slice == 'end':
                    add = item[-wid:]
                else:
                    add = item[:wid]
                add += (wid - len(add)) * ' '
            elif indicator == 'bar':
                add = ''
                needprogress = True
            elif indicator == 'unit' and unit:
                add = unit
            if not needprogress:
                head = spacejoin(head, add)
            else:
                tail = spacejoin(add, tail)
        if needprogress:
            used = 0
            if head:
                used += len(head) + 1
            if tail:
                used += len(tail) + 1
            progwidth = termwidth - used - 3
            if total:
                amt = pos * progwidth // total
                bar = '=' * (amt - 1)
                if amt > 0:
                    bar += '>'
                bar += ' ' * (progwidth - amt)
            else:
                progwidth -= 3
                self.indetcount += 1
                # mod the count by twice the width so we can make the
                # cursor bounce between the right and left sides
                amt = self.indetcount % (2 * progwidth)
                amt -= progwidth
                bar = (' ' * int(progwidth - abs(amt)) + '<=>' +
                       ' ' * int(abs(amt)))
            prog = ''.join(('[', bar , ']'))
            out = spacejoin(head, prog, tail)
        else:
            out = spacejoin(head, tail)
        sys.stderr.write('\r' + out[:termwidth])
        sys.stderr.flush()

    def clear(self):
        sys.stderr.write('\r%s\r' % (' ' * self.width()))

    def complete(self):
        if self.ui.configbool('progress', 'clear-complete', default=True):
            self.clear()
        else:
            sys.stderr.write('\n')
        sys.stderr.flush()

    def width(self):
        tw = util.termwidth()
        return min(int(self.ui.config('progress', 'width', default=tw)), tw)

    def progress(self, orig, topic, pos, item='', unit='', total=None):
        if pos is None:
            if self.topics and self.topics[-1] == topic and self.printed:
                self.complete()
                self.resetstate()
        else:
            if topic not in self.topics:
                self.topics.append(topic)
            now = time.time()
            if (now - self.lastprint >= self.refresh
                and topic == self.topics[-1]):
                self.lastprint = now
                self.show(topic, pos, item, unit, total)
        return orig(topic, pos, item=item, unit=unit, total=total)

    def write(self, orig, *args):
        if self.printed:
            self.clear()
        return orig(*args)

sharedprog = None

def uisetup(ui):
    # Apps that derive a class from ui.ui() can use
    # setconfig('progress', 'disable', 'True') to disable this extension
    if ui.configbool('progress', 'disable'):
        return
    if ((sys.stderr.isatty() or ui.configbool('progress', 'assume-tty'))
        and not ui.debugflag and not ui.quiet):
        # we instantiate one globally shared progress bar to avoid
        # competing progress bars when multiple UI objects get created
        global sharedprog
        if not sharedprog:
            sharedprog = progbar(ui)
        extensions.wrapfunction(ui, 'progress', sharedprog.progress)
        extensions.wrapfunction(ui, 'write', sharedprog.write)
        extensions.wrapfunction(ui, 'write_err', sharedprog.write)

def reposetup(ui, repo):
    uisetup(repo.ui)
