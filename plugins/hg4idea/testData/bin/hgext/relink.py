# Mercurial extension to provide 'hg relink' command
#
# Copyright (C) 2007 Brendan Cully <brendan@kublai.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""recreates hardlinks between repository clones"""

from mercurial import cmdutil, hg, util
from mercurial.i18n import _
import os, stat

def relink(ui, repo, origin=None, **opts):
    """recreate hardlinks between two repositories

    When repositories are cloned locally, their data files will be
    hardlinked so that they only use the space of a single repository.

    Unfortunately, subsequent pulls into either repository will break
    hardlinks for any files touched by the new changesets, even if
    both repositories end up pulling the same changes.

    Similarly, passing --rev to "hg clone" will fail to use any
    hardlinks, falling back to a complete copy of the source
    repository.

    This command lets you recreate those hardlinks and reclaim that
    wasted space.

    This repository will be relinked to share space with ORIGIN, which
    must be on the same local disk. If ORIGIN is omitted, looks for
    "default-relink", then "default", in [paths].

    Do not attempt any read operations on this repository while the
    command is running. (Both repositories will be locked against
    writes.)
    """
    if not hasattr(util, 'samefile') or not hasattr(util, 'samedevice'):
        raise util.Abort(_('hardlinks are not supported on this system'))
    src = hg.repository(
        cmdutil.remoteui(repo, opts),
        ui.expandpath(origin or 'default-relink', origin or 'default'))
    if not src.local():
        raise util.Abort('must specify local origin repository')
    ui.status(_('relinking %s to %s\n') % (src.store.path, repo.store.path))
    locallock = repo.lock()
    try:
        remotelock = src.lock()
        try:
            candidates = sorted(collect(src.store.path, ui))
            targets = prune(candidates, src.store.path, repo.store.path, ui)
            do_relink(src.store.path, repo.store.path, targets, ui)
        finally:
            remotelock.release()
    finally:
        locallock.release()

def collect(src, ui):
    seplen = len(os.path.sep)
    candidates = []
    for dirpath, dirnames, filenames in os.walk(src):
        relpath = dirpath[len(src) + seplen:]
        for filename in filenames:
            if not filename[-2:] in ('.d', '.i'):
                continue
            st = os.stat(os.path.join(dirpath, filename))
            if not stat.S_ISREG(st.st_mode):
                continue
            candidates.append((os.path.join(relpath, filename), st))

    ui.status(_('collected %d candidate storage files\n') % len(candidates))
    return candidates

def prune(candidates, src, dst, ui):
    def linkfilter(src, dst, st):
        try:
            ts = os.stat(dst)
        except OSError:
            # Destination doesn't have this file?
            return False
        if util.samefile(src, dst):
            return False
        if not util.samedevice(src, dst):
            # No point in continuing
            raise util.Abort(
                _('source and destination are on different devices'))
        if st.st_size != ts.st_size:
            return False
        return st

    targets = []
    for fn, st in candidates:
        srcpath = os.path.join(src, fn)
        tgt = os.path.join(dst, fn)
        ts = linkfilter(srcpath, tgt, st)
        if not ts:
            ui.debug(_('not linkable: %s\n') % fn)
            continue
        targets.append((fn, ts.st_size))

    ui.status(_('pruned down to %d probably relinkable files\n') % len(targets))
    return targets

def do_relink(src, dst, files, ui):
    def relinkfile(src, dst):
        bak = dst + '.bak'
        os.rename(dst, bak)
        try:
            util.os_link(src, dst)
        except OSError:
            os.rename(bak, dst)
            raise
        os.remove(bak)

    CHUNKLEN = 65536
    relinked = 0
    savedbytes = 0

    pos = 0
    total = len(files)
    for f, sz in files:
        pos += 1
        source = os.path.join(src, f)
        tgt = os.path.join(dst, f)
        # Binary mode, so that read() works correctly, especially on Windows
        sfp = file(source, 'rb')
        dfp = file(tgt, 'rb')
        sin = sfp.read(CHUNKLEN)
        while sin:
            din = dfp.read(CHUNKLEN)
            if sin != din:
                break
            sin = sfp.read(CHUNKLEN)
        sfp.close()
        dfp.close()
        if sin:
            ui.debug(_('not linkable: %s\n') % f)
            continue
        try:
            relinkfile(source, tgt)
            ui.progress(_('relinking'), pos, f, _(' files'), total)
            relinked += 1
            savedbytes += sz
        except OSError, inst:
            ui.warn('%s: %s\n' % (tgt, str(inst)))

    ui.progress(_('relinking'), None, unit=_(' files'), total=total)

    ui.status(_('relinked %d files (%d bytes reclaimed)\n') %
              (relinked, savedbytes))

cmdtable = {
    'relink': (
        relink,
        [],
        _('[ORIGIN]')
    )
}
