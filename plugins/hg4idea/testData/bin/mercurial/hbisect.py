# changelog bisection for mercurial
#
# Copyright 2007 Matt Mackall
# Copyright 2005, 2006 Benoit Boissinot <benoit.boissinot@ens-lyon.org>
#
# Inspired by git bisect, extension skeleton taken from mq.py.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os, error
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

        # set nodes descended from goodrevs
        for rev in goodrevs:
            ancestors[rev] = []
        for rev in changelog.revs(goodrev + 1):
            for prev in clparents(rev):
                if ancestors[prev] == []:
                    ancestors[rev] = []

        # clear good revs from array
        for rev in goodrevs:
            ancestors[rev] = None
        for rev in changelog.revs(len(changelog), goodrev):
            if ancestors[rev] is None:
                for prev in clparents(rev):
                    ancestors[prev] = None

        if ancestors[badrev] is None:
            return badrev, None
        return badrev, ancestors

    good = False
    badrev, ancestors = buildancestors(state['bad'], state['good'])
    if not ancestors: # looking for bad to good transition?
        good = True
        badrev, ancestors = buildancestors(state['good'], state['bad'])
    bad = changelog.node(badrev)
    if not ancestors: # now we're confused
        if len(state['bad']) == 1 and len(state['good']) == 1:
            raise util.Abort(_("starting revisions are not directly related"))
        raise util.Abort(_("inconsistent state, %s:%s is good and bad")
                         % (badrev, short(bad)))

    # build children dict
    children = {}
    visit = util.deque([badrev])
    candidates = []
    while visit:
        rev = visit.popleft()
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
    state = {'current': [], 'good': [], 'bad': [], 'skip': []}
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
        for kind in sorted(state):
            for node in state[kind]:
                f.write("%s %s\n" % (kind, hex(node)))
        f.close()
    finally:
        wlock.release()

def get(repo, status):
    """
    Return a list of revision(s) that match the given status:

    - ``good``, ``bad``, ``skip``: csets explicitly marked as good/bad/skip
    - ``goods``, ``bads``      : csets topologically good/bad
    - ``range``              : csets taking part in the bisection
    - ``pruned``             : csets that are goods, bads or skipped
    - ``untested``           : csets whose fate is yet unknown
    - ``ignored``            : csets ignored due to DAG topology
    - ``current``            : the cset currently being bisected
    """
    state = load_state(repo)
    if status in ('good', 'bad', 'skip', 'current'):
        return map(repo.changelog.rev, state[status])
    else:
        # In the following sets, we do *not* call 'bisect()' with more
        # than one level of recursion, because that can be very, very
        # time consuming. Instead, we always develop the expression as
        # much as possible.

        # 'range' is all csets that make the bisection:
        #   - have a good ancestor and a bad descendant, or conversely
        # that's because the bisection can go either way
        range = '( bisect(bad)::bisect(good) | bisect(good)::bisect(bad) )'

        _t = repo.revs('bisect(good)::bisect(bad)')
        # The sets of topologically good or bad csets
        if len(_t) == 0:
            # Goods are topologically after bads
            goods = 'bisect(good)::'    # Pruned good csets
            bads  = '::bisect(bad)'     # Pruned bad csets
        else:
            # Goods are topologically before bads
            goods = '::bisect(good)'    # Pruned good csets
            bads  = 'bisect(bad)::'     # Pruned bad csets

        # 'pruned' is all csets whose fate is already known: good, bad, skip
        skips = 'bisect(skip)'                 # Pruned skipped csets
        pruned = '( (%s) | (%s) | (%s) )' % (goods, bads, skips)

        # 'untested' is all cset that are- in 'range', but not in 'pruned'
        untested = '( (%s) - (%s) )' % (range, pruned)

        # 'ignored' is all csets that were not used during the bisection
        # due to DAG topology, but may however have had an impact.
        # E.g., a branch merged between bads and goods, but whose branch-
        # point is out-side of the range.
        iba = '::bisect(bad) - ::bisect(good)'  # Ignored bads' ancestors
        iga = '::bisect(good) - ::bisect(bad)'  # Ignored goods' ancestors
        ignored = '( ( (%s) | (%s) ) - (%s) )' % (iba, iga, range)

        if status == 'range':
            return repo.revs(range)
        elif status == 'pruned':
            return repo.revs(pruned)
        elif status == 'untested':
            return repo.revs(untested)
        elif status == 'ignored':
            return repo.revs(ignored)
        elif status == "goods":
            return repo.revs(goods)
        elif status == "bads":
            return repo.revs(bads)
        else:
            raise error.ParseError(_('invalid bisect state'))

def label(repo, node):
    rev = repo.changelog.rev(node)

    # Try explicit sets
    if rev in get(repo, 'good'):
        # i18n: bisect changeset status
        return _('good')
    if rev in get(repo, 'bad'):
        # i18n: bisect changeset status
        return _('bad')
    if rev in get(repo, 'skip'):
        # i18n: bisect changeset status
        return _('skipped')
    if rev in get(repo, 'untested') or rev in get(repo, 'current'):
        # i18n: bisect changeset status
        return _('untested')
    if rev in get(repo, 'ignored'):
        # i18n: bisect changeset status
        return _('ignored')

    # Try implicit sets
    if rev in get(repo, 'goods'):
        # i18n: bisect changeset status
        return _('good (implicit)')
    if rev in get(repo, 'bads'):
        # i18n: bisect changeset status
        return _('bad (implicit)')

    return None

def shortlabel(label):
    if label:
        return label[0].upper()

    return None
