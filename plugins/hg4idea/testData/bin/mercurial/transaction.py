# transaction.py - simple journalling scheme for mercurial
#
# This transaction scheme is intended to gracefully handle program
# errors and interruptions. More serious failures like system crashes
# can be recovered with an fsck-like tool. As the whole repository is
# effectively log-structured, this should amount to simply truncating
# anything that isn't referenced in the changelog.
#
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import os, errno
import error

def active(func):
    def _active(self, *args, **kwds):
        if self.count == 0:
            raise error.Abort(_(
                'cannot use transaction when it is already committed/aborted'))
        return func(self, *args, **kwds)
    return _active

def _playback(journal, report, opener, entries, unlink=True):
    for f, o, ignore in entries:
        if o or not unlink:
            try:
                opener(f, 'a').truncate(o)
            except IOError:
                report(_("failed to truncate %s\n") % f)
                raise
        else:
            try:
                fn = opener(f).name
                os.unlink(fn)
            except (IOError, OSError), inst:
                if inst.errno != errno.ENOENT:
                    raise
    os.unlink(journal)

class transaction(object):
    def __init__(self, report, opener, journal, after=None, createmode=None):
        self.count = 1
        self.report = report
        self.opener = opener
        self.after = after
        self.entries = []
        self.map = {}
        self.journal = journal
        self._queue = []

        self.file = open(self.journal, "w")
        if createmode is not None:
            os.chmod(self.journal, createmode & 0666)

    def __del__(self):
        if self.journal:
            self._abort()

    @active
    def startgroup(self):
        self._queue.append([])

    @active
    def endgroup(self):
        q = self._queue.pop()
        d = ''.join(['%s\0%d\n' % (x[0], x[1]) for x in q])
        self.entries.extend(q)
        self.file.write(d)
        self.file.flush()

    @active
    def add(self, file, offset, data=None):
        if file in self.map:
            return
        if self._queue:
            self._queue[-1].append((file, offset, data))
            return

        self.entries.append((file, offset, data))
        self.map[file] = len(self.entries) - 1
        # add enough data to the journal to do the truncate
        self.file.write("%s\0%d\n" % (file, offset))
        self.file.flush()

    @active
    def find(self, file):
        if file in self.map:
            return self.entries[self.map[file]]
        return None

    @active
    def replace(self, file, offset, data=None):
        '''
        replace can only replace already committed entries
        that are not pending in the queue
        '''

        if file not in self.map:
            raise KeyError(file)
        index = self.map[file]
        self.entries[index] = (file, offset, data)
        self.file.write("%s\0%d\n" % (file, offset))
        self.file.flush()

    @active
    def nest(self):
        self.count += 1
        return self

    def running(self):
        return self.count > 0

    @active
    def close(self):
        '''commit the transaction'''
        self.count -= 1
        if self.count != 0:
            return
        self.file.close()
        self.entries = []
        if self.after:
            self.after()
        if os.path.isfile(self.journal):
            os.unlink(self.journal)
        self.journal = None

    @active
    def abort(self):
        '''abort the transaction (generally called on error, or when the
        transaction is not explicitly committed before going out of
        scope)'''
        self._abort()

    def _abort(self):
        self.count = 0
        self.file.close()

        try:
            if not self.entries:
                if self.journal:
                    os.unlink(self.journal)
                return

            self.report(_("transaction abort!\n"))

            try:
                _playback(self.journal, self.report, self.opener,
                          self.entries, False)
                self.report(_("rollback completed\n"))
            except:
                self.report(_("rollback failed - please run hg recover\n"))
        finally:
            self.journal = None


def rollback(opener, file, report):
    entries = []

    for l in open(file).readlines():
        f, o = l.split('\0')
        entries.append((f, int(o), None))

    _playback(file, report, opener, entries)
