# copies.py - copy detection for Mercurial
#
# Copyright 2008 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import util
import heapq

def _nonoverlap(d1, d2, d3):
    "Return list of elements in d1 not in d2 or d3"
    return sorted([d for d in d1 if d not in d3 and d not in d2])

def _dirname(f):
    s = f.rfind("/")
    if s == -1:
        return ""
    return f[:s]

def _findlimit(repo, a, b):
    """Find the earliest revision that's an ancestor of a or b but not both,
    None if no such revision exists.
    """
    # basic idea:
    # - mark a and b with different sides
    # - if a parent's children are all on the same side, the parent is
    #   on that side, otherwise it is on no side
    # - walk the graph in topological order with the help of a heap;
    #   - add unseen parents to side map
    #   - clear side of any parent that has children on different sides
    #   - track number of interesting revs that might still be on a side
    #   - track the lowest interesting rev seen
    #   - quit when interesting revs is zero

    cl = repo.changelog
    working = len(cl) # pseudo rev for the working directory
    if a is None:
        a = working
    if b is None:
        b = working

    side = {a: -1, b: 1}
    visit = [-a, -b]
    heapq.heapify(visit)
    interesting = len(visit)
    hascommonancestor = False
    limit = working

    while interesting:
        r = -heapq.heappop(visit)
        if r == working:
            parents = [cl.rev(p) for p in repo.dirstate.parents()]
        else:
            parents = cl.parentrevs(r)
        for p in parents:
            if p < 0:
                continue
            if p not in side:
                # first time we see p; add it to visit
                side[p] = side[r]
                if side[p]:
                    interesting += 1
                heapq.heappush(visit, -p)
            elif side[p] and side[p] != side[r]:
                # p was interesting but now we know better
                side[p] = 0
                interesting -= 1
                hascommonancestor = True
        if side[r]:
            limit = r # lowest rev visited
            interesting -= 1

    if not hascommonancestor:
        return None
    return limit

def _chain(src, dst, a, b):
    '''chain two sets of copies a->b'''
    t = a.copy()
    for k, v in b.iteritems():
        if v in t:
            # found a chain
            if t[v] != k:
                # file wasn't renamed back to itself
                t[k] = t[v]
            if v not in dst:
                # chain was a rename, not a copy
                del t[v]
        if v in src:
            # file is a copy of an existing file
            t[k] = v

    # remove criss-crossed copies
    for k, v in t.items():
        if k in src and v in dst:
            del t[k]

    return t

def _tracefile(fctx, actx):
    '''return file context that is the ancestor of fctx present in actx'''
    stop = actx.rev()
    am = actx.manifest()

    for f in fctx.ancestors():
        if am.get(f.path(), None) == f.filenode():
            return f
        if f.rev() < stop:
            return None

def _dirstatecopies(d):
    ds = d._repo.dirstate
    c = ds.copies().copy()
    for k in c.keys():
        if ds[k] not in 'anm':
            del c[k]
    return c

def _forwardcopies(a, b):
    '''find {dst@b: src@a} copy mapping where a is an ancestor of b'''

    # check for working copy
    w = None
    if b.rev() is None:
        w = b
        b = w.p1()
        if a == b:
            # short-circuit to avoid issues with merge states
            return _dirstatecopies(w)

    # find where new files came from
    # we currently don't try to find where old files went, too expensive
    # this means we can miss a case like 'hg rm b; hg cp a b'
    cm = {}
    missing = set(b.manifest().iterkeys())
    missing.difference_update(a.manifest().iterkeys())

    for f in missing:
        ofctx = _tracefile(b[f], a)
        if ofctx:
            cm[f] = ofctx.path()

    # combine copies from dirstate if necessary
    if w is not None:
        cm = _chain(a, w, cm, _dirstatecopies(w))

    return cm

def _backwardrenames(a, b):
    # Even though we're not taking copies into account, 1:n rename situations
    # can still exist (e.g. hg cp a b; hg mv a c). In those cases we
    # arbitrarily pick one of the renames.
    f = _forwardcopies(b, a)
    r = {}
    for k, v in sorted(f.iteritems()):
        # remove copies
        if v in a:
            continue
        r[v] = k
    return r

def pathcopies(x, y):
    '''find {dst@y: src@x} copy mapping for directed compare'''
    if x == y or not x or not y:
        return {}
    a = y.ancestor(x)
    if a == x:
        return _forwardcopies(x, y)
    if a == y:
        return _backwardrenames(x, y)
    return _chain(x, y, _backwardrenames(x, a), _forwardcopies(a, y))

def mergecopies(repo, c1, c2, ca):
    """
    Find moves and copies between context c1 and c2 that are relevant
    for merging.

    Returns four dicts: "copy", "movewithdir", "diverge", and
    "renamedelete".

    "copy" is a mapping from destination name -> source name,
    where source is in c1 and destination is in c2 or vice-versa.

    "movewithdir" is a mapping from source name -> destination name,
    where the file at source present in one context but not the other
    needs to be moved to destination by the merge process, because the
    other context moved the directory it is in.

    "diverge" is a mapping of source name -> list of destination names
    for divergent renames.

    "renamedelete" is a mapping of source name -> list of destination
    names for files deleted in c1 that were renamed in c2 or vice-versa.
    """
    # avoid silly behavior for update from empty dir
    if not c1 or not c2 or c1 == c2:
        return {}, {}, {}, {}

    # avoid silly behavior for parent -> working dir
    if c2.node() is None and c1.node() == repo.dirstate.p1():
        return repo.dirstate.copies(), {}, {}, {}

    limit = _findlimit(repo, c1.rev(), c2.rev())
    if limit is None:
        # no common ancestor, no copies
        return {}, {}, {}, {}
    m1 = c1.manifest()
    m2 = c2.manifest()
    ma = ca.manifest()

    def makectx(f, n):
        if len(n) != 20: # in a working context?
            if c1.rev() is None:
                return c1.filectx(f)
            return c2.filectx(f)
        return repo.filectx(f, fileid=n)

    ctx = util.lrucachefunc(makectx)
    copy = {}
    movewithdir = {}
    fullcopy = {}
    diverge = {}

    def related(f1, f2, limit):
        # Walk back to common ancestor to see if the two files originate
        # from the same file. Since workingfilectx's rev() is None it messes
        # up the integer comparison logic, hence the pre-step check for
        # None (f1 and f2 can only be workingfilectx's initially).

        if f1 == f2:
            return f1 # a match

        g1, g2 = f1.ancestors(), f2.ancestors()
        try:
            f1r, f2r = f1.rev(), f2.rev()

            if f1r is None:
                f1 = g1.next()
            if f2r is None:
                f2 = g2.next()

            while True:
                f1r, f2r = f1.rev(), f2.rev()
                if f1r > f2r:
                    f1 = g1.next()
                elif f2r > f1r:
                    f2 = g2.next()
                elif f1 == f2:
                    return f1 # a match
                elif f1r == f2r or f1r < limit or f2r < limit:
                    return False # copy no longer relevant
        except StopIteration:
            return False

    def checkcopies(f, m1, m2):
        '''check possible copies of f from m1 to m2'''
        of = None
        seen = set([f])
        for oc in ctx(f, m1[f]).ancestors():
            ocr = oc.rev()
            of = oc.path()
            if of in seen:
                # check limit late - grab last rename before
                if ocr < limit:
                    break
                continue
            seen.add(of)

            fullcopy[f] = of # remember for dir rename detection
            if of not in m2:
                continue # no match, keep looking
            if m2[of] == ma.get(of):
                break # no merge needed, quit early
            c2 = ctx(of, m2[of])
            cr = related(oc, c2, ca.rev())
            if cr and (of == f or of == c2.path()): # non-divergent
                copy[f] = of
                of = None
                break

        if of in ma:
            diverge.setdefault(of, []).append(f)

    repo.ui.debug("  searching for copies back to rev %d\n" % limit)

    u1 = _nonoverlap(m1, m2, ma)
    u2 = _nonoverlap(m2, m1, ma)

    if u1:
        repo.ui.debug("  unmatched files in local:\n   %s\n"
                      % "\n   ".join(u1))
    if u2:
        repo.ui.debug("  unmatched files in other:\n   %s\n"
                      % "\n   ".join(u2))

    for f in u1:
        checkcopies(f, m1, m2)
    for f in u2:
        checkcopies(f, m2, m1)

    renamedelete = {}
    renamedelete2 = set()
    diverge2 = set()
    for of, fl in diverge.items():
        if len(fl) == 1 or of in c1 or of in c2:
            del diverge[of] # not actually divergent, or not a rename
            if of not in c1 and of not in c2:
                # renamed on one side, deleted on the other side, but filter
                # out files that have been renamed and then deleted
                renamedelete[of] = [f for f in fl if f in c1 or f in c2]
                renamedelete2.update(fl) # reverse map for below
        else:
            diverge2.update(fl) # reverse map for below

    if fullcopy:
        repo.ui.debug("  all copies found (* = to merge, ! = divergent, "
                      "% = renamed and deleted):\n")
        for f in sorted(fullcopy):
            note = ""
            if f in copy:
                note += "*"
            if f in diverge2:
                note += "!"
            if f in renamedelete2:
                note += "%"
            repo.ui.debug("   src: '%s' -> dst: '%s' %s\n" % (fullcopy[f], f,
                                                              note))
    del diverge2

    if not fullcopy:
        return copy, movewithdir, diverge, renamedelete

    repo.ui.debug("  checking for directory renames\n")

    # generate a directory move map
    d1, d2 = c1.dirs(), c2.dirs()
    d1.addpath('/')
    d2.addpath('/')
    invalid = set()
    dirmove = {}

    # examine each file copy for a potential directory move, which is
    # when all the files in a directory are moved to a new directory
    for dst, src in fullcopy.iteritems():
        dsrc, ddst = _dirname(src), _dirname(dst)
        if dsrc in invalid:
            # already seen to be uninteresting
            continue
        elif dsrc in d1 and ddst in d1:
            # directory wasn't entirely moved locally
            invalid.add(dsrc)
        elif dsrc in d2 and ddst in d2:
            # directory wasn't entirely moved remotely
            invalid.add(dsrc)
        elif dsrc in dirmove and dirmove[dsrc] != ddst:
            # files from the same directory moved to two different places
            invalid.add(dsrc)
        else:
            # looks good so far
            dirmove[dsrc + "/"] = ddst + "/"

    for i in invalid:
        if i in dirmove:
            del dirmove[i]
    del d1, d2, invalid

    if not dirmove:
        return copy, movewithdir, diverge, renamedelete

    for d in dirmove:
        repo.ui.debug("   discovered dir src: '%s' -> dst: '%s'\n" %
                      (d, dirmove[d]))

    # check unaccounted nonoverlapping files against directory moves
    for f in u1 + u2:
        if f not in fullcopy:
            for d in dirmove:
                if f.startswith(d):
                    # new file added in a directory that was moved, move it
                    df = dirmove[d] + f[len(d):]
                    if df not in copy:
                        movewithdir[f] = df
                        repo.ui.debug(("   pending file src: '%s' -> "
                                       "dst: '%s'\n") % (f, df))
                    break

    return copy, movewithdir, diverge, renamedelete
