# changelog bisection for mercurial
#
# Copyright 2007 Olivia Mackall
# Copyright 2005, 2006 Benoit Boissinot <benoit.boissinot@ens-lyon.org>
#
# Inspired by git bisect, extension skeleton taken from mq.py.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import collections
import contextlib

from .i18n import _
from .node import (
    hex,
    short,
)
from . import error


def bisect(repo, state):
    """find the next node (if any) for testing during a bisect search.
    returns a (nodes, number, good) tuple.

    'nodes' is the final result of the bisect if 'number' is 0.
    Otherwise 'number' indicates the remaining possible candidates for
    the search and 'nodes' contains the next bisect target.
    'good' is True if bisect is searching for a first good changeset, False
    if searching for a first bad one.
    """

    repo = repo.unfiltered()
    changelog = repo.changelog
    clparents = changelog.parentrevs
    skip = {changelog.rev(n) for n in state[b'skip']}

    def buildancestors(bad, good):
        badrev = min([changelog.rev(n) for n in bad])
        ancestors = collections.defaultdict(lambda: None)
        for rev in repo.revs(b"(%ln::%d) - (::%ln)", good, badrev, good):
            ancestors[rev] = []
        if ancestors[badrev] is None:
            return badrev, None
        return badrev, ancestors

    good = False
    badrev, ancestors = buildancestors(state[b'bad'], state[b'good'])
    if not ancestors:  # looking for bad to good transition?
        good = True
        badrev, ancestors = buildancestors(state[b'good'], state[b'bad'])
    bad = changelog.node(badrev)
    if not ancestors:  # now we're confused
        if (
            len(state[b'bad']) == 1
            and len(state[b'good']) == 1
            and state[b'bad'] != state[b'good']
        ):
            raise error.Abort(_(b"starting revisions are not directly related"))
        raise error.Abort(
            _(b"inconsistent state, %d:%s is good and bad")
            % (badrev, short(bad))
        )

    # build children dict
    children = {}
    visit = collections.deque([badrev])
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
        return ([changelog.node(c) for c in candidates], 0, good)
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

        x = len(a)  # number of ancestors
        y = tot - x  # number of non-ancestors
        value = min(x, y)  # how good is this test?
        if value > best_len and rev not in skip:
            best_len = value
            best_rev = rev
            if value == perfect:  # found a perfect candidate? quit early
                break

        if y < perfect and rev not in skip:  # all downhill from here?
            # poison children
            poison.update(children.get(rev, []))
            continue

        unvisited = []
        for c in children.get(rev, []):
            if ancestors[c]:
                ancestors[c] = list(set(ancestors[c] + a))
            else:
                unvisited.append(c)

        # Reuse existing ancestor list for the first unvisited child to avoid
        # excessive copying for linear portions of history.
        if unvisited:
            first = unvisited.pop(0)
            for c in unvisited:
                ancestors[c] = a + [c]
            a.append(first)
            ancestors[first] = a

    assert best_rev is not None
    best_node = changelog.node(best_rev)

    return ([best_node], tot, good)


def extendrange(repo, state, nodes, good):
    # bisect is incomplete when it ends on a merge node and
    # one of the parent was not checked.
    parents = repo[nodes[0]].parents()
    if len(parents) > 1:
        if good:
            side = state[b'bad']
        else:
            side = state[b'good']
        num = len({i.node() for i in parents} & set(side))
        if num == 1:
            return parents[0].ancestor(parents[1])
    return None


def load_state(repo):
    state = {b'current': [], b'good': [], b'bad': [], b'skip': []}
    for l in repo.vfs.tryreadlines(b"bisect.state"):
        kind, node = l[:-1].split()
        node = repo.unfiltered().lookup(node)
        if kind not in state:
            raise error.Abort(_(b"unknown bisect kind %s") % kind)
        state[kind].append(node)
    return state


def save_state(repo, state):
    f = repo.vfs(b"bisect.state", b"w", atomictemp=True)
    with repo.wlock():
        for kind in sorted(state):
            for node in state[kind]:
                f.write(b"%s %s\n" % (kind, hex(node)))
        f.close()


def resetstate(repo):
    """remove any bisect state from the repository"""
    if repo.vfs.exists(b"bisect.state"):
        repo.vfs.unlink(b"bisect.state")


def checkstate(state):
    """check we have both 'good' and 'bad' to define a range

    Raise StateError exception otherwise."""
    if state[b'good'] and state[b'bad']:
        return True
    if not state[b'good']:
        raise error.StateError(_(b'cannot bisect (no known good revisions)'))
    else:
        raise error.StateError(_(b'cannot bisect (no known bad revisions)'))


@contextlib.contextmanager
def restore_state(repo, state, node):
    try:
        yield
    finally:
        state[b'current'] = [node]
        save_state(repo, state)


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
    if status in (b'good', b'bad', b'skip', b'current'):
        return map(repo.unfiltered().changelog.rev, state[status])
    else:
        # In the following sets, we do *not* call 'bisect()' with more
        # than one level of recursion, because that can be very, very
        # time consuming. Instead, we always develop the expression as
        # much as possible.

        # 'range' is all csets that make the bisection:
        #   - have a good ancestor and a bad descendant, or conversely
        # that's because the bisection can go either way
        range = b'( bisect(bad)::bisect(good) | bisect(good)::bisect(bad) )'

        _t = repo.revs(b'bisect(good)::bisect(bad)')
        # The sets of topologically good or bad csets
        if len(_t) == 0:
            # Goods are topologically after bads
            goods = b'bisect(good)::'  # Pruned good csets
            bads = b'::bisect(bad)'  # Pruned bad csets
        else:
            # Goods are topologically before bads
            goods = b'::bisect(good)'  # Pruned good csets
            bads = b'bisect(bad)::'  # Pruned bad csets

        # 'pruned' is all csets whose fate is already known: good, bad, skip
        skips = b'bisect(skip)'  # Pruned skipped csets
        pruned = b'( (%s) | (%s) | (%s) )' % (goods, bads, skips)

        # 'untested' is all cset that are- in 'range', but not in 'pruned'
        untested = b'( (%s) - (%s) )' % (range, pruned)

        # 'ignored' is all csets that were not used during the bisection
        # due to DAG topology, but may however have had an impact.
        # E.g., a branch merged between bads and goods, but whose branch-
        # point is out-side of the range.
        iba = b'::bisect(bad) - ::bisect(good)'  # Ignored bads' ancestors
        iga = b'::bisect(good) - ::bisect(bad)'  # Ignored goods' ancestors
        ignored = b'( ( (%s) | (%s) ) - (%s) )' % (iba, iga, range)

        if status == b'range':
            return repo.revs(range)
        elif status == b'pruned':
            return repo.revs(pruned)
        elif status == b'untested':
            return repo.revs(untested)
        elif status == b'ignored':
            return repo.revs(ignored)
        elif status == b"goods":
            return repo.revs(goods)
        elif status == b"bads":
            return repo.revs(bads)
        else:
            raise error.ParseError(_(b'invalid bisect state'))


def label(repo, node):
    rev = repo.changelog.rev(node)

    # Try explicit sets
    if rev in get(repo, b'good'):
        # i18n: bisect changeset status
        return _(b'good')
    if rev in get(repo, b'bad'):
        # i18n: bisect changeset status
        return _(b'bad')
    if rev in get(repo, b'skip'):
        # i18n: bisect changeset status
        return _(b'skipped')
    if rev in get(repo, b'untested') or rev in get(repo, b'current'):
        # i18n: bisect changeset status
        return _(b'untested')
    if rev in get(repo, b'ignored'):
        # i18n: bisect changeset status
        return _(b'ignored')

    # Try implicit sets
    if rev in get(repo, b'goods'):
        # i18n: bisect changeset status
        return _(b'good (implicit)')
    if rev in get(repo, b'bads'):
        # i18n: bisect changeset status
        return _(b'bad (implicit)')

    return None


def printresult(ui, repo, state, displayer, nodes, good):
    repo = repo.unfiltered()
    if len(nodes) == 1:
        # narrowed it down to a single revision
        if good:
            ui.write(_(b"The first good revision is:\n"))
        else:
            ui.write(_(b"The first bad revision is:\n"))
        displayer.show(repo[nodes[0]])
        extendnode = extendrange(repo, state, nodes, good)
        if extendnode is not None:
            ui.write(
                _(
                    b'Not all ancestors of this changeset have been'
                    b' checked.\nUse bisect --extend to continue the '
                    b'bisection from\nthe common ancestor, %s.\n'
                )
                % extendnode
            )
    else:
        # multiple possible revisions
        if good:
            ui.write(
                _(
                    b"Due to skipped revisions, the first "
                    b"good revision could be any of:\n"
                )
            )
        else:
            ui.write(
                _(
                    b"Due to skipped revisions, the first "
                    b"bad revision could be any of:\n"
                )
            )
        for n in nodes:
            displayer.show(repo[n])
    displayer.close()
