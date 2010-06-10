# changelog bisection for mercurial
#
# Copyright 2007 Matt Mackall
# Copyright 2005, 2006 Benoit Boissinot <benoit.boissinot@ens-lyon.org>
#
# Inspired by git bisect, extension skeleton taken from mq.py.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os
from i18n import _
from node import short, hex
import util

def bisect(changelog, state):
    """find the next node (if any) for testing during a bisect search.
    returns a (nodes, number, good) tuple.

    'nodes' is the final result of the bisect if 'number' is 0.
    Otherwise 'number' indicates the remaining possible candidates for
    the search and 'nodes' contains the next bisect target.
    'good' is True if bisect is searching for a first good changeset, False
    if searching for a first bad one.
    """

    clparents = changelog.parentrevs
    skip = set([changelog.rev(n) for n in state['skip']])

    def buildancestors(bad, good):
        # only the earliest bad revision matters
        badrev = min([changelog.rev(n) for n in bad])
        goodrevs = [changelog.rev(n) for n in good]
        goodrev = min(goodrevs)
        # build visit array
        ancestors = [None] * (len(changelog) + 1) # an extra for [-1]

        # set nodes descended from goodrev
        ancestors[goodrev] = []
        for rev in xrange(goodrev + 1, len(changelog)):
            for prev in clparents(rev):
                if ancestors[prev] == []:
                    ancestors[rev] = []

        # clear good revs from array
        for node in goodrevs:
            ancestors[node] = None
        for rev in xrange(len(changelog), -1, -1):
            if ancestors[rev] is None:
                for prev in clparents(rev):
                    ancestors[prev] = None

        if ancestors[badrev] is None:
            return badrev, None
        return badrev, ancestors

    good = 0
    badrev, ancestors = buildancestors(state['bad'], state['good'])
    if not ancestors: # looking for bad to good transition?
        good = 1
        badrev, ancestors = buildancestors(state['good'], state['bad'])
    bad = changelog.node(badrev)
    if not ancestors: # now we're confused
        raise util.Abort(_("Inconsistent state, %s:%s is good and bad")
                         % (badrev, short(bad)))

    # build children dict
    children = {}
    visit = [badrev]
    candidates = []
    while visit:
        rev = visit.pop(0)
        if ancestors[rev] == []:
            candidates.append(rev)
            for prev in clparents(rev):
                if prev != -1:
                    if prev in children:
                        children[prev].append(rev)
                    else:
                        children[prev] = [rev]
                        visit.append(prev)

    candidates.sort()
    # have we narrowed it down to one entry?
    # or have all other possible candidates besides 'bad' have been skipped?
    tot = len(candidates)
    unskipped = [c for c in candidates if (c not in skip) and (c != badrev)]
    if tot == 1 or not unskipped:
        return ([changelog.node(rev) for rev in candidates], 0, good)
    perfect = tot // 2

    # find the best node to test
    best_rev = None
    best_len = -1
    poison = set()
    for rev in candidates:
        if rev in poison:
            # poison children
            poison.update(children.get(rev, []))
            continue

        a = ancestors[rev] or [rev]
        ancestors[rev] = None

        x = len(a) # number of ancestors
        y = tot - x # number of non-ancestors
        value = min(x, y) # how good is this test?
        if value > best_len and rev not in skip:
            best_len = value
            best_rev = rev
            if value == perfect: # found a perfect candidate? quit early
                break

        if y < perfect and rev not in skip: # all downhill from here?
            # poison children
            poison.update(children.get(rev, []))
            continue

        for c in children.get(rev, []):
            if ancestors[c]:
                ancestors[c] = list(set(ancestors[c] + a))
            else:
                ancestors[c] = a + [c]

    assert best_rev is not None
    best_node = changelog.node(best_rev)

    return ([best_node], tot, good)


def load_state(repo):
    state = {'good': [], 'bad': [], 'skip': []}
    if os.path.exists(repo.join("bisect.state")):
        for l in repo.opener("bisect.state"):
            kind, node = l[:-1].split()
            node = repo.lookup(node)
            if kind not in state:
                raise util.Abort(_("unknown bisect kind %s") % kind)
            state[kind].append(node)
    return state


def save_state(repo, state):
    f = repo.opener("bisect.state", "w", atomictemp=True)
    wlock = repo.wlock()
    try:
        for kind in state:
            for node in state[kind]:
                f.write("%s %s\n" % (kind, hex(node)))
        f.rename()
    finally:
        wlock.release()

