# repair.py - functions for repository repair for mercurial
#
# Copyright 2005, 2006 Chris Mason <mason@suse.com>
# Copyright 2007 Matt Mackall
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from mercurial import changegroup
from mercurial.node import short
from mercurial.i18n import _
import os
import errno

def _bundle(repo, bases, heads, node, suffix, compress=True):
    """create a bundle with the specified revisions as a backup"""
    cg = repo.changegroupsubset(bases, heads, 'strip')
    backupdir = repo.join("strip-backup")
    if not os.path.isdir(backupdir):
        os.mkdir(backupdir)
    name = os.path.join(backupdir, "%s-%s.hg" % (short(node), suffix))
    if compress:
        bundletype = "HG10BZ"
    else:
        bundletype = "HG10UN"
    return changegroup.writebundle(cg, name, bundletype)

def _collectfiles(repo, striprev):
    """find out the filelogs affected by the strip"""
    files = set()

    for x in xrange(striprev, len(repo)):
        files.update(repo[x].files())

    return sorted(files)

def _collectbrokencsets(repo, files, striprev):
    """return the changesets which will be broken by the truncation"""
    s = set()
    def collectone(revlog):
        linkgen = (revlog.linkrev(i) for i in revlog)
        # find the truncation point of the revlog
        for lrev in linkgen:
            if lrev >= striprev:
                break
        # see if any revision after this point has a linkrev
        # less than striprev (those will be broken by strip)
        for lrev in linkgen:
            if lrev < striprev:
                s.add(lrev)

    collectone(repo.manifest)
    for fname in files:
        collectone(repo.file(fname))

    return s

def strip(ui, repo, nodelist, backup="all", topic='backup'):
    repo = repo.unfiltered()
    repo.destroying()

    cl = repo.changelog
    # TODO handle undo of merge sets
    if isinstance(nodelist, str):
        nodelist = [nodelist]
    striplist = [cl.rev(node) for node in nodelist]
    striprev = min(striplist)

    keeppartialbundle = backup == 'strip'

    # Some revisions with rev > striprev may not be descendants of striprev.
    # We have to find these revisions and put them in a bundle, so that
    # we can restore them after the truncations.
    # To create the bundle we use repo.changegroupsubset which requires
    # the list of heads and bases of the set of interesting revisions.
    # (head = revision in the set that has no descendant in the set;
    #  base = revision in the set that has no ancestor in the set)
    tostrip = set(striplist)
    for rev in striplist:
        for desc in cl.descendants([rev]):
            tostrip.add(desc)

    files = _collectfiles(repo, striprev)
    saverevs = _collectbrokencsets(repo, files, striprev)

    # compute heads
    saveheads = set(saverevs)
    for r in xrange(striprev + 1, len(cl)):
        if r not in tostrip:
            saverevs.add(r)
            saveheads.difference_update(cl.parentrevs(r))
            saveheads.add(r)
    saveheads = [cl.node(r) for r in saveheads]

    # compute base nodes
    if saverevs:
        descendants = set(cl.descendants(saverevs))
        saverevs.difference_update(descendants)
    savebases = [cl.node(r) for r in saverevs]
    stripbases = [cl.node(r) for r in tostrip]

    # For a set s, max(parents(s) - s) is the same as max(heads(::s - s)), but
    # is much faster
    newbmtarget = repo.revs('max(parents(%ld) - (%ld))', tostrip, tostrip)
    if newbmtarget:
        newbmtarget = repo[newbmtarget[0]].node()
    else:
        newbmtarget = '.'

    bm = repo._bookmarks
    updatebm = []
    for m in bm:
        rev = repo[bm[m]].rev()
        if rev in tostrip:
            updatebm.append(m)

    # create a changegroup for all the branches we need to keep
    backupfile = None
    if backup == "all":
        backupfile = _bundle(repo, stripbases, cl.heads(), node, topic)
        repo.ui.status(_("saved backup bundle to %s\n") % backupfile)
        repo.ui.log("backupbundle", "saved backup bundle to %s\n", backupfile)
    if saveheads or savebases:
        # do not compress partial bundle if we remove it from disk later
        chgrpfile = _bundle(repo, savebases, saveheads, node, 'temp',
                            compress=keeppartialbundle)

    mfst = repo.manifest

    tr = repo.transaction("strip")
    offset = len(tr.entries)

    try:
        tr.startgroup()
        cl.strip(striprev, tr)
        mfst.strip(striprev, tr)
        for fn in files:
            repo.file(fn).strip(striprev, tr)
        tr.endgroup()

        try:
            for i in xrange(offset, len(tr.entries)):
                file, troffset, ignore = tr.entries[i]
                repo.sopener(file, 'a').truncate(troffset)
            tr.close()
        except: # re-raises
            tr.abort()
            raise

        if saveheads or savebases:
            ui.note(_("adding branch\n"))
            f = open(chgrpfile, "rb")
            gen = changegroup.readbundle(f, chgrpfile)
            if not repo.ui.verbose:
                # silence internal shuffling chatter
                repo.ui.pushbuffer()
            repo.addchangegroup(gen, 'strip', 'bundle:' + chgrpfile, True)
            if not repo.ui.verbose:
                repo.ui.popbuffer()
            f.close()
            if not keeppartialbundle:
                os.unlink(chgrpfile)

        # remove undo files
        for undofile in repo.undofiles():
            try:
                os.unlink(undofile)
            except OSError, e:
                if e.errno != errno.ENOENT:
                    ui.warn(_('error removing %s: %s\n') % (undofile, str(e)))

        for m in updatebm:
            bm[m] = repo[newbmtarget].node()
        bm.write()
    except: # re-raises
        if backupfile:
            ui.warn(_("strip failed, full bundle stored in '%s'\n")
                    % backupfile)
        elif saveheads:
            ui.warn(_("strip failed, partial bundle stored in '%s'\n")
                    % chgrpfile)
        raise

    repo.destroyed()
