# repair.py - functions for repository repair for mercurial
#
# Copyright 2005, 2006 Chris Mason <mason@suse.com>
# Copyright 2007 Matt Mackall
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import changegroup
from node import nullrev, short
from i18n import _
import os

def _bundle(repo, bases, heads, node, suffix, extranodes=None):
    """create a bundle with the specified revisions as a backup"""
    cg = repo.changegroupsubset(bases, heads, 'strip', extranodes)
    backupdir = repo.join("strip-backup")
    if not os.path.isdir(backupdir):
        os.mkdir(backupdir)
    name = os.path.join(backupdir, "%s-%s" % (short(node), suffix))
    repo.ui.warn(_("saving bundle to %s\n") % name)
    return changegroup.writebundle(cg, name, "HG10BZ")

def _collectfiles(repo, striprev):
    """find out the filelogs affected by the strip"""
    files = set()

    for x in xrange(striprev, len(repo)):
        files.update(repo[x].files())

    return sorted(files)

def _collectextranodes(repo, files, link):
    """return the nodes that have to be saved before the strip"""
    def collectone(revlog):
        extra = []
        startrev = count = len(revlog)
        # find the truncation point of the revlog
        for i in xrange(count):
            lrev = revlog.linkrev(i)
            if lrev >= link:
                startrev = i + 1
                break

        # see if any revision after that point has a linkrev less than link
        # (we have to manually save these guys)
        for i in xrange(startrev, count):
            node = revlog.node(i)
            lrev = revlog.linkrev(i)
            if lrev < link:
                extra.append((node, cl.node(lrev)))

        return extra

    extranodes = {}
    cl = repo.changelog
    extra = collectone(repo.manifest)
    if extra:
        extranodes[1] = extra
    for fname in files:
        f = repo.file(fname)
        extra = collectone(f)
        if extra:
            extranodes[fname] = extra

    return extranodes

def strip(ui, repo, node, backup="all"):
    cl = repo.changelog
    # TODO delete the undo files, and handle undo of merge sets
    striprev = cl.rev(node)

    # Some revisions with rev > striprev may not be descendants of striprev.
    # We have to find these revisions and put them in a bundle, so that
    # we can restore them after the truncations.
    # To create the bundle we use repo.changegroupsubset which requires
    # the list of heads and bases of the set of interesting revisions.
    # (head = revision in the set that has no descendant in the set;
    #  base = revision in the set that has no ancestor in the set)
    tostrip = set((striprev,))
    saveheads = set()
    savebases = []
    for r in xrange(striprev + 1, len(cl)):
        parents = cl.parentrevs(r)
        if parents[0] in tostrip or parents[1] in tostrip:
            # r is a descendant of striprev
            tostrip.add(r)
            # if this is a merge and one of the parents does not descend
            # from striprev, mark that parent as a savehead.
            if parents[1] != nullrev:
                for p in parents:
                    if p not in tostrip and p > striprev:
                        saveheads.add(p)
        else:
            # if no parents of this revision will be stripped, mark it as
            # a savebase
            if parents[0] < striprev and parents[1] < striprev:
                savebases.append(cl.node(r))

            saveheads.difference_update(parents)
            saveheads.add(r)

    saveheads = [cl.node(r) for r in saveheads]
    files = _collectfiles(repo, striprev)

    extranodes = _collectextranodes(repo, files, striprev)

    # create a changegroup for all the branches we need to keep
    if backup == "all":
        _bundle(repo, [node], cl.heads(), node, 'backup')
    if saveheads or extranodes:
        chgrpfile = _bundle(repo, savebases, saveheads, node, 'temp',
                            extranodes)

    mfst = repo.manifest

    tr = repo.transaction()
    offset = len(tr.entries)

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
    except:
        tr.abort()
        raise

    if saveheads or extranodes:
        ui.status(_("adding branch\n"))
        f = open(chgrpfile, "rb")
        gen = changegroup.readbundle(f, chgrpfile)
        repo.addchangegroup(gen, 'strip', 'bundle:' + chgrpfile, True)
        f.close()
        if backup != "strip":
            os.unlink(chgrpfile)

    repo.destroyed()
