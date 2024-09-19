# Mercurial extension to provide 'hg relink' command
#
# Copyright (C) 2007 Brendan Cully <brendan@kublai.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""recreates hardlinks between repository clones"""
from __future__ import absolute_import

import os
import stat

from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial import (
    error,
    hg,
    registrar,
    util,
)
from mercurial.utils import (
    stringutil,
    urlutil,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


@command(
    b'relink', [], _(b'[ORIGIN]'), helpcategory=command.CATEGORY_MAINTENANCE
)
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
    if not util.safehasattr(util, b'samefile') or not util.safehasattr(
        util, b'samedevice'
    ):
        raise error.Abort(_(b'hardlinks are not supported on this system'))

    if origin is None and b'default-relink' in ui.paths:
        origin = b'default-relink'
    path, __ = urlutil.get_unique_pull_path(b'relink', repo, ui, origin)
    src = hg.repository(repo.baseui, path)
    ui.status(_(b'relinking %s to %s\n') % (src.store.path, repo.store.path))
    if repo.root == src.root:
        ui.status(_(b'there is nothing to relink\n'))
        return

    if not util.samedevice(src.store.path, repo.store.path):
        # No point in continuing
        raise error.Abort(_(b'source and destination are on different devices'))

    with repo.lock(), src.lock():
        candidates = sorted(collect(src, ui))
        targets = prune(candidates, src.store.path, repo.store.path, ui)
        do_relink(src.store.path, repo.store.path, targets, ui)


def collect(src, ui):
    seplen = len(os.path.sep)
    candidates = []
    live = len(src[b'tip'].manifest())
    # Your average repository has some files which were deleted before
    # the tip revision. We account for that by assuming that there are
    # 3 tracked files for every 2 live files as of the tip version of
    # the repository.
    #
    # mozilla-central as of 2010-06-10 had a ratio of just over 7:5.
    total = live * 3 // 2
    src = src.store.path
    progress = ui.makeprogress(_(b'collecting'), unit=_(b'files'), total=total)
    pos = 0
    ui.status(
        _(b"tip has %d files, estimated total number of files: %d\n")
        % (live, total)
    )
    for dirpath, dirnames, filenames in os.walk(src):
        dirnames.sort()
        relpath = dirpath[len(src) + seplen :]
        for filename in sorted(filenames):
            if filename[-2:] not in (b'.d', b'.i'):
                continue
            st = os.stat(os.path.join(dirpath, filename))
            if not stat.S_ISREG(st.st_mode):
                continue
            pos += 1
            candidates.append((os.path.join(relpath, filename), st))
            progress.update(pos, item=filename)

    progress.complete()
    ui.status(_(b'collected %d candidate storage files\n') % len(candidates))
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
            raise error.Abort(
                _(b'source and destination are on different devices')
            )
        if st.st_size != ts.st_size:
            return False
        return st

    targets = []
    progress = ui.makeprogress(
        _(b'pruning'), unit=_(b'files'), total=len(candidates)
    )
    pos = 0
    for fn, st in candidates:
        pos += 1
        srcpath = os.path.join(src, fn)
        tgt = os.path.join(dst, fn)
        ts = linkfilter(srcpath, tgt, st)
        if not ts:
            ui.debug(b'not linkable: %s\n' % fn)
            continue
        targets.append((fn, ts.st_size))
        progress.update(pos, item=fn)

    progress.complete()
    ui.status(
        _(b'pruned down to %d probably relinkable files\n') % len(targets)
    )
    return targets


def do_relink(src, dst, files, ui):
    def relinkfile(src, dst):
        bak = dst + b'.bak'
        os.rename(dst, bak)
        try:
            util.oslink(src, dst)
        except OSError:
            os.rename(bak, dst)
            raise
        os.remove(bak)

    CHUNKLEN = 65536
    relinked = 0
    savedbytes = 0

    progress = ui.makeprogress(
        _(b'relinking'), unit=_(b'files'), total=len(files)
    )
    pos = 0
    for f, sz in files:
        pos += 1
        source = os.path.join(src, f)
        tgt = os.path.join(dst, f)
        # Binary mode, so that read() works correctly, especially on Windows
        sfp = open(source, b'rb')
        dfp = open(tgt, b'rb')
        sin = sfp.read(CHUNKLEN)
        while sin:
            din = dfp.read(CHUNKLEN)
            if sin != din:
                break
            sin = sfp.read(CHUNKLEN)
        sfp.close()
        dfp.close()
        if sin:
            ui.debug(b'not linkable: %s\n' % f)
            continue
        try:
            relinkfile(source, tgt)
            progress.update(pos, item=f)
            relinked += 1
            savedbytes += sz
        except OSError as inst:
            ui.warn(b'%s: %s\n' % (tgt, stringutil.forcebytestr(inst)))

    progress.complete()

    ui.status(
        _(b'relinked %d files (%s reclaimed)\n')
        % (relinked, util.bytecount(savedbytes))
    )
