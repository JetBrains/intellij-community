# merge.py - directory-level update/merge handling for Mercurial
#
# Copyright 2006, 2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import nullid, nullrev, hex, bin
from i18n import _
import util, filemerge, copies, subrepo
import errno, os, shutil

class mergestate(object):
    '''track 3-way merge state of individual files'''
    def __init__(self, repo):
        self._repo = repo
        self._read()
    def reset(self, node=None):
        self._state = {}
        if node:
            self._local = node
        shutil.rmtree(self._repo.join("merge"), True)
    def _read(self):
        self._state = {}
        try:
            localnode = None
            f = self._repo.opener("merge/state")
            for i, l in enumerate(f):
                if i == 0:
                    localnode = l[:-1]
                else:
                    bits = l[:-1].split("\0")
                    self._state[bits[0]] = bits[1:]
            self._local = bin(localnode)
        except IOError, err:
            if err.errno != errno.ENOENT:
                raise
    def _write(self):
        f = self._repo.opener("merge/state", "w")
        f.write(hex(self._local) + "\n")
        for d, v in self._state.iteritems():
            f.write("\0".join([d] + v) + "\n")
    def add(self, fcl, fco, fca, fd, flags):
        hash = util.sha1(fcl.path()).hexdigest()
        self._repo.opener("merge/" + hash, "w").write(fcl.data())
        self._state[fd] = ['u', hash, fcl.path(), fca.path(),
                           hex(fca.filenode()), fco.path(), flags]
        self._write()
    def __contains__(self, dfile):
        return dfile in self._state
    def __getitem__(self, dfile):
        return self._state[dfile][0]
    def __iter__(self):
        l = self._state.keys()
        l.sort()
        for f in l:
            yield f
    def mark(self, dfile, state):
        self._state[dfile][0] = state
        self._write()
    def resolve(self, dfile, wctx, octx):
        if self[dfile] == 'r':
            return 0
        state, hash, lfile, afile, anode, ofile, flags = self._state[dfile]
        f = self._repo.opener("merge/" + hash)
        self._repo.wwrite(dfile, f.read(), flags)
        fcd = wctx[dfile]
        fco = octx[ofile]
        fca = self._repo.filectx(afile, fileid=anode)
        r = filemerge.filemerge(self._repo, self._local, lfile, fcd, fco, fca)
        if not r:
            self.mark(dfile, 'r')
        return r

def _checkunknown(wctx, mctx):
    "check for collisions between unknown files and files in mctx"
    for f in wctx.unknown():
        if f in mctx and mctx[f].cmp(wctx[f].data()):
            raise util.Abort(_("untracked file in working directory differs"
                               " from file in requested revision: '%s'") % f)

def _checkcollision(mctx):
    "check for case folding collisions in the destination context"
    folded = {}
    for fn in mctx:
        fold = fn.lower()
        if fold in folded:
            raise util.Abort(_("case-folding collision between %s and %s")
                             % (fn, folded[fold]))
        folded[fold] = fn

def _forgetremoved(wctx, mctx, branchmerge):
    """
    Forget removed files

    If we're jumping between revisions (as opposed to merging), and if
    neither the working directory nor the target rev has the file,
    then we need to remove it from the dirstate, to prevent the
    dirstate from listing the file when it is no longer in the
    manifest.

    If we're merging, and the other revision has removed a file
    that is not present in the working directory, we need to mark it
    as removed.
    """

    action = []
    state = branchmerge and 'r' or 'f'
    for f in wctx.deleted():
        if f not in mctx:
            action.append((f, state))

    if not branchmerge:
        for f in wctx.removed():
            if f not in mctx:
                action.append((f, "f"))

    return action

def manifestmerge(repo, p1, p2, pa, overwrite, partial):
    """
    Merge p1 and p2 with ancestor ma and generate merge action list

    overwrite = whether we clobber working files
    partial = function to filter file lists
    """

    def fmerge(f, f2, fa):
        """merge flags"""
        a, m, n = ma.flags(fa), m1.flags(f), m2.flags(f2)
        if m == n: # flags agree
            return m # unchanged
        if m and n and not a: # flags set, don't agree, differ from parent
            r = repo.ui.promptchoice(
                _(" conflicting flags for %s\n"
                  "(n)one, e(x)ec or sym(l)ink?") % f,
                (_("&None"), _("E&xec"), _("Sym&link")), 0)
            if r == 1:
                return "x" # Exec
            if r == 2:
                return "l" # Symlink
            return ""
        if m and m != a: # changed from a to m
            return m
        if n and n != a: # changed from a to n
            return n
        return '' # flag was cleared

    def act(msg, m, f, *args):
        repo.ui.debug(" %s: %s -> %s\n" % (f, msg, m))
        action.append((f, m) + args)

    action, copy = [], {}

    if overwrite:
        pa = p1
    elif pa == p2: # backwards
        pa = p1.p1()
    elif pa and repo.ui.configbool("merge", "followcopies", True):
        dirs = repo.ui.configbool("merge", "followdirs", True)
        copy, diverge = copies.copies(repo, p1, p2, pa, dirs)
        for of, fl in diverge.iteritems():
            act("divergent renames", "dr", of, fl)

    repo.ui.note(_("resolving manifests\n"))
    repo.ui.debug(" overwrite %s partial %s\n" % (overwrite, bool(partial)))
    repo.ui.debug(" ancestor %s local %s remote %s\n" % (pa, p1, p2))

    m1, m2, ma = p1.manifest(), p2.manifest(), pa.manifest()
    copied = set(copy.values())

    if not overwrite and '.hgsubstate' in m1:
        # check whether sub state is modified
        for s in p1.substate:
            if p1.sub(s).dirty():
                m1['.hgsubstate'] += "+"
                break

    # Compare manifests
    for f, n in m1.iteritems():
        if partial and not partial(f):
            continue
        if f in m2:
            rflags = fmerge(f, f, f)
            a = ma.get(f, nullid)
            if n == m2[f] or m2[f] == a: # same or local newer
                if m1.flags(f) != rflags:
                    act("update permissions", "e", f, rflags)
            elif n == a: # remote newer
                act("remote is newer", "g", f, rflags)
            else: # both changed
                act("versions differ", "m", f, f, f, rflags, False)
        elif f in copied: # files we'll deal with on m2 side
            pass
        elif f in copy:
            f2 = copy[f]
            if f2 not in m2: # directory rename
                act("remote renamed directory to " + f2, "d",
                    f, None, f2, m1.flags(f))
            else: # case 2 A,B/B/B or case 4,21 A/B/B
                act("local copied/moved to " + f2, "m",
                    f, f2, f, fmerge(f, f2, f2), False)
        elif f in ma: # clean, a different, no remote
            if n != ma[f]:
                if repo.ui.promptchoice(
                    _(" local changed %s which remote deleted\n"
                      "use (c)hanged version or (d)elete?") % f,
                    (_("&Changed"), _("&Delete")), 0):
                    act("prompt delete", "r", f)
                else:
                    act("prompt keep", "a", f)
            elif n[20:] == "a": # added, no remote
                act("remote deleted", "f", f)
            elif n[20:] != "u":
                act("other deleted", "r", f)

    for f, n in m2.iteritems():
        if partial and not partial(f):
            continue
        if f in m1 or f in copied: # files already visited
            continue
        if f in copy:
            f2 = copy[f]
            if f2 not in m1: # directory rename
                act("local renamed directory to " + f2, "d",
                    None, f, f2, m2.flags(f))
            elif f2 in m2: # rename case 1, A/A,B/A
                act("remote copied to " + f, "m",
                    f2, f, f, fmerge(f2, f, f2), False)
            else: # case 3,20 A/B/A
                act("remote moved to " + f, "m",
                    f2, f, f, fmerge(f2, f, f2), True)
        elif f not in ma:
            act("remote created", "g", f, m2.flags(f))
        elif n != ma[f]:
            if repo.ui.promptchoice(
                _("remote changed %s which local deleted\n"
                  "use (c)hanged version or leave (d)eleted?") % f,
                (_("&Changed"), _("&Deleted")), 0) == 0:
                act("prompt recreating", "g", f, m2.flags(f))

    return action

def actionkey(a):
    return a[1] == 'r' and -1 or 0, a

def applyupdates(repo, action, wctx, mctx):
    "apply the merge action list to the working directory"

    updated, merged, removed, unresolved = 0, 0, 0, 0
    ms = mergestate(repo)
    ms.reset(wctx.parents()[0].node())
    moves = []
    action.sort(key=actionkey)
    substate = wctx.substate # prime

    # prescan for merges
    u = repo.ui
    for a in action:
        f, m = a[:2]
        if m == 'm': # merge
            f2, fd, flags, move = a[2:]
            if f == '.hgsubstate': # merged internally
                continue
            repo.ui.debug("preserving %s for resolve of %s\n" % (f, fd))
            fcl = wctx[f]
            fco = mctx[f2]
            fca = fcl.ancestor(fco) or repo.filectx(f, fileid=nullrev)
            ms.add(fcl, fco, fca, fd, flags)
            if f != fd and move:
                moves.append(f)

    # remove renamed files after safely stored
    for f in moves:
        if util.lexists(repo.wjoin(f)):
            repo.ui.debug("removing %s\n" % f)
            os.unlink(repo.wjoin(f))

    audit_path = util.path_auditor(repo.root)

    numupdates = len(action)
    for i, a in enumerate(action):
        f, m = a[:2]
        u.progress('update', i + 1, item=f, total=numupdates, unit='files')
        if f and f[0] == "/":
            continue
        if m == "r": # remove
            repo.ui.note(_("removing %s\n") % f)
            audit_path(f)
            if f == '.hgsubstate': # subrepo states need updating
                subrepo.submerge(repo, wctx, mctx, wctx)
            try:
                util.unlink(repo.wjoin(f))
            except OSError, inst:
                if inst.errno != errno.ENOENT:
                    repo.ui.warn(_("update failed to remove %s: %s!\n") %
                                 (f, inst.strerror))
            removed += 1
        elif m == "m": # merge
            if f == '.hgsubstate': # subrepo states need updating
                subrepo.submerge(repo, wctx, mctx, wctx.ancestor(mctx))
                continue
            f2, fd, flags, move = a[2:]
            r = ms.resolve(fd, wctx, mctx)
            if r is not None and r > 0:
                unresolved += 1
            else:
                if r is None:
                    updated += 1
                else:
                    merged += 1
            util.set_flags(repo.wjoin(fd), 'l' in flags, 'x' in flags)
            if f != fd and move and util.lexists(repo.wjoin(f)):
                repo.ui.debug("removing %s\n" % f)
                os.unlink(repo.wjoin(f))
        elif m == "g": # get
            flags = a[2]
            repo.ui.note(_("getting %s\n") % f)
            t = mctx.filectx(f).data()
            repo.wwrite(f, t, flags)
            updated += 1
            if f == '.hgsubstate': # subrepo states need updating
                subrepo.submerge(repo, wctx, mctx, wctx)
        elif m == "d": # directory rename
            f2, fd, flags = a[2:]
            if f:
                repo.ui.note(_("moving %s to %s\n") % (f, fd))
                t = wctx.filectx(f).data()
                repo.wwrite(fd, t, flags)
                util.unlink(repo.wjoin(f))
            if f2:
                repo.ui.note(_("getting %s to %s\n") % (f2, fd))
                t = mctx.filectx(f2).data()
                repo.wwrite(fd, t, flags)
            updated += 1
        elif m == "dr": # divergent renames
            fl = a[2]
            repo.ui.warn(_("warning: detected divergent renames of %s to:\n") % f)
            for nf in fl:
                repo.ui.warn(" %s\n" % nf)
        elif m == "e": # exec
            flags = a[2]
            util.set_flags(repo.wjoin(f), 'l' in flags, 'x' in flags)
    u.progress('update', None, total=numupdates, unit='files')

    return updated, merged, removed, unresolved

def recordupdates(repo, action, branchmerge):
    "record merge actions to the dirstate"

    for a in action:
        f, m = a[:2]
        if m == "r": # remove
            if branchmerge:
                repo.dirstate.remove(f)
            else:
                repo.dirstate.forget(f)
        elif m == "a": # re-add
            if not branchmerge:
                repo.dirstate.add(f)
        elif m == "f": # forget
            repo.dirstate.forget(f)
        elif m == "e": # exec change
            repo.dirstate.normallookup(f)
        elif m == "g": # get
            if branchmerge:
                repo.dirstate.normaldirty(f)
            else:
                repo.dirstate.normal(f)
        elif m == "m": # merge
            f2, fd, flag, move = a[2:]
            if branchmerge:
                # We've done a branch merge, mark this file as merged
                # so that we properly record the merger later
                repo.dirstate.merge(fd)
                if f != f2: # copy/rename
                    if move:
                        repo.dirstate.remove(f)
                    if f != fd:
                        repo.dirstate.copy(f, fd)
                    else:
                        repo.dirstate.copy(f2, fd)
            else:
                # We've update-merged a locally modified file, so
                # we set the dirstate to emulate a normal checkout
                # of that file some time in the past. Thus our
                # merge will appear as a normal local file
                # modification.
                repo.dirstate.normallookup(fd)
                if move:
                    repo.dirstate.forget(f)
        elif m == "d": # directory rename
            f2, fd, flag = a[2:]
            if not f2 and f not in repo.dirstate:
                # untracked file moved
                continue
            if branchmerge:
                repo.dirstate.add(fd)
                if f:
                    repo.dirstate.remove(f)
                    repo.dirstate.copy(f, fd)
                if f2:
                    repo.dirstate.copy(f2, fd)
            else:
                repo.dirstate.normal(fd)
                if f:
                    repo.dirstate.forget(f)

def update(repo, node, branchmerge, force, partial):
    """
    Perform a merge between the working directory and the given node

    node = the node to update to, or None if unspecified
    branchmerge = whether to merge between branches
    force = whether to force branch merging or file overwriting
    partial = a function to filter file lists (dirstate not updated)

    The table below shows all the behaviors of the update command
    given the -c and -C or no options, whether the working directory
    is dirty, whether a revision is specified, and the relationship of
    the parent rev to the target rev (linear, on the same named
    branch, or on another named branch).

    This logic is tested by test-update-branches.

    -c  -C  dirty  rev  |  linear   same  cross
     n   n    n     n   |    ok     (1)     x
     n   n    n     y   |    ok     ok     ok
     n   n    y     *   |   merge   (2)    (2)
     n   y    *     *   |    ---  discard  ---
     y   n    y     *   |    ---    (3)    ---
     y   n    n     *   |    ---    ok     ---
     y   y    *     *   |    ---    (4)    ---

    x = can't happen
    * = don't-care
    1 = abort: crosses branches (use 'hg merge' or 'hg update -c')
    2 = abort: crosses branches (use 'hg merge' to merge or
                 use 'hg update -C' to discard changes)
    3 = abort: uncommitted local changes
    4 = incompatible options (checked in commands.py)
    """

    onode = node
    wlock = repo.wlock()
    try:
        wc = repo[None]
        if node is None:
            # tip of current branch
            try:
                node = repo.branchtags()[wc.branch()]
            except KeyError:
                if wc.branch() == "default": # no default branch!
                    node = repo.lookup("tip") # update to tip
                else:
                    raise util.Abort(_("branch %s not found") % wc.branch())
        overwrite = force and not branchmerge
        pl = wc.parents()
        p1, p2 = pl[0], repo[node]
        pa = p1.ancestor(p2)
        fp1, fp2, xp1, xp2 = p1.node(), p2.node(), str(p1), str(p2)
        fastforward = False

        ### check phase
        if not overwrite and len(pl) > 1:
            raise util.Abort(_("outstanding uncommitted merges"))
        if branchmerge:
            if pa == p2:
                raise util.Abort(_("can't merge with ancestor"))
            elif pa == p1:
                if p1.branch() != p2.branch():
                    fastforward = True
                else:
                    raise util.Abort(_("nothing to merge (use 'hg update'"
                                       " or check 'hg heads')"))
            if not force and (wc.files() or wc.deleted()):
                raise util.Abort(_("outstanding uncommitted changes "
                                   "(use 'hg status' to list changes)"))
        elif not overwrite:
            if pa == p1 or pa == p2: # linear
                pass # all good
            elif wc.files() or wc.deleted():
                raise util.Abort(_("crosses branches (use 'hg merge' to merge "
                                 "or use 'hg update -C' to discard changes)"))
            elif onode is None:
                raise util.Abort(_("crosses branches (use 'hg merge' or use "
                                   "'hg update -c')"))
            else:
                # Allow jumping branches if clean and specific rev given
                overwrite = True

        ### calculate phase
        action = []
        if not force:
            _checkunknown(wc, p2)
        if not util.checkcase(repo.path):
            _checkcollision(p2)
        action += _forgetremoved(wc, p2, branchmerge)
        action += manifestmerge(repo, wc, p2, pa, overwrite, partial)

        ### apply phase
        if not branchmerge: # just jump to the new rev
            fp1, fp2, xp1, xp2 = fp2, nullid, xp2, ''
        if not partial:
            repo.hook('preupdate', throw=True, parent1=xp1, parent2=xp2)

        stats = applyupdates(repo, action, wc, p2)

        if not partial:
            recordupdates(repo, action, branchmerge)
            repo.dirstate.setparents(fp1, fp2)
            if not branchmerge and not fastforward:
                repo.dirstate.setbranch(p2.branch())
    finally:
        wlock.release()

    if not partial:
        repo.hook('update', parent1=xp1, parent2=xp2, error=stats[3])
    return stats
