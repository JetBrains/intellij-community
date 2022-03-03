# Revision graph generator for Mercurial
#
# Copyright 2008 Dirkjan Ochtman <dirkjan@ochtman.nl>
# Copyright 2007 Joel Rosdahl <joel@rosdahl.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""supports walking the history as DAGs suitable for graphical output

The most basic format we use is that of::

  (id, type, data, [parentids])

The node and parent ids are arbitrary integers which identify a node in the
context of the graph returned. Type is a constant specifying the node type.
Data depends on type.
"""

from __future__ import absolute_import

from .node import nullrev
from .thirdparty import attr
from . import (
    dagop,
    pycompat,
    smartset,
    util,
)

CHANGESET = b'C'
PARENT = b'P'
GRANDPARENT = b'G'
MISSINGPARENT = b'M'
# Style of line to draw. None signals a line that ends and is removed at this
# point. A number prefix means only the last N characters of the current block
# will use that style, the rest will use the PARENT style. Add a - sign
# (so making N negative) and all but the first N characters use that style.
EDGES = {PARENT: b'|', GRANDPARENT: b':', MISSINGPARENT: None}


def dagwalker(repo, revs):
    """cset DAG generator yielding (id, CHANGESET, ctx, [parentinfo]) tuples

    This generator function walks through revisions (which should be ordered
    from bigger to lower). It returns a tuple for each node.

    Each parentinfo entry is a tuple with (edgetype, parentid), where edgetype
    is one of PARENT, GRANDPARENT or MISSINGPARENT. The node and parent ids
    are arbitrary integers which identify a node in the context of the graph
    returned.

    """
    gpcache = {}

    for rev in revs:
        ctx = repo[rev]
        # partition into parents in the rev set and missing parents, then
        # augment the lists with markers, to inform graph drawing code about
        # what kind of edge to draw between nodes.
        pset = {p.rev() for p in ctx.parents() if p.rev() in revs}
        mpars = [
            p.rev()
            for p in ctx.parents()
            if p.rev() != nullrev and p.rev() not in pset
        ]
        parents = [(PARENT, p) for p in sorted(pset)]

        for mpar in mpars:
            gp = gpcache.get(mpar)
            if gp is None:
                # precompute slow query as we know reachableroots() goes
                # through all revs (issue4782)
                if not isinstance(revs, smartset.baseset):
                    revs = smartset.baseset(revs)
                gp = gpcache[mpar] = sorted(
                    set(dagop.reachableroots(repo, revs, [mpar]))
                )
            if not gp:
                parents.append((MISSINGPARENT, mpar))
                pset.add(mpar)
            else:
                parents.extend((GRANDPARENT, g) for g in gp if g not in pset)
                pset.update(gp)

        yield (ctx.rev(), CHANGESET, ctx, parents)


def nodes(repo, nodes):
    """cset DAG generator yielding (id, CHANGESET, ctx, [parentids]) tuples

    This generator function walks the given nodes. It only returns parents
    that are in nodes, too.
    """
    include = set(nodes)
    for node in nodes:
        ctx = repo[node]
        parents = {
            (PARENT, p.rev()) for p in ctx.parents() if p.node() in include
        }
        yield (ctx.rev(), CHANGESET, ctx, sorted(parents))


def colored(dag, repo):
    """annotates a DAG with colored edge information

    For each DAG node this function emits tuples::

      (id, type, data, (col, color), [(col, nextcol, color)])

    with the following new elements:

      - Tuple (col, color) with column and color index for the current node
      - A list of tuples indicating the edges between the current node and its
        parents.
    """
    seen = []
    colors = {}
    newcolor = 1
    config = {}

    for key, val in repo.ui.configitems(b'graph'):
        if b'.' in key:
            branch, setting = key.rsplit(b'.', 1)
            # Validation
            if setting == b"width" and val.isdigit():
                config.setdefault(branch, {})[setting] = int(val)
            elif setting == b"color" and val.isalnum():
                config.setdefault(branch, {})[setting] = val

    if config:
        getconf = util.lrucachefunc(
            lambda rev: config.get(repo[rev].branch(), {})
        )
    else:
        getconf = lambda rev: {}

    for (cur, type, data, parents) in dag:

        # Compute seen and next
        if cur not in seen:
            seen.append(cur)  # new head
            colors[cur] = newcolor
            newcolor += 1

        col = seen.index(cur)
        color = colors.pop(cur)
        next = seen[:]

        # Add parents to next
        addparents = [p for pt, p in parents if p not in next]
        next[col : col + 1] = addparents

        # Set colors for the parents
        for i, p in enumerate(addparents):
            if not i:
                colors[p] = color
            else:
                colors[p] = newcolor
                newcolor += 1

        # Add edges to the graph
        edges = []
        for ecol, eid in enumerate(seen):
            if eid in next:
                bconf = getconf(eid)
                edges.append(
                    (
                        ecol,
                        next.index(eid),
                        colors[eid],
                        bconf.get(b'width', -1),
                        bconf.get(b'color', b''),
                    )
                )
            elif eid == cur:
                for ptype, p in parents:
                    bconf = getconf(p)
                    edges.append(
                        (
                            ecol,
                            next.index(p),
                            color,
                            bconf.get(b'width', -1),
                            bconf.get(b'color', b''),
                        )
                    )

        # Yield and move on
        yield (cur, type, data, (col, color), edges)
        seen = next


def asciiedges(type, char, state, rev, parents):
    """adds edge info to changelog DAG walk suitable for ascii()"""
    seen = state.seen
    if rev not in seen:
        seen.append(rev)
    nodeidx = seen.index(rev)

    knownparents = []
    newparents = []
    for ptype, parent in parents:
        if parent == rev:
            # self reference (should only be seen in null rev)
            continue
        if parent in seen:
            knownparents.append(parent)
        else:
            newparents.append(parent)
            state.edges[parent] = state.styles.get(ptype, b'|')

    ncols = len(seen)
    width = 1 + ncols * 2
    nextseen = seen[:]
    nextseen[nodeidx : nodeidx + 1] = newparents
    edges = [(nodeidx, nextseen.index(p)) for p in knownparents]

    seen[:] = nextseen
    while len(newparents) > 2:
        # ascii() only knows how to add or remove a single column between two
        # calls. Nodes with more than two parents break this constraint so we
        # introduce intermediate expansion lines to grow the active node list
        # slowly.
        edges.append((nodeidx, nodeidx))
        edges.append((nodeidx, nodeidx + 1))
        nmorecols = 1
        width += 2
        yield (type, char, width, (nodeidx, edges, ncols, nmorecols))
        char = b'\\'
        nodeidx += 1
        ncols += 1
        edges = []
        del newparents[0]

    if len(newparents) > 0:
        edges.append((nodeidx, nodeidx))
    if len(newparents) > 1:
        edges.append((nodeidx, nodeidx + 1))
    nmorecols = len(nextseen) - ncols
    if nmorecols > 0:
        width += 2
    # remove current node from edge characters, no longer needed
    state.edges.pop(rev, None)
    yield (type, char, width, (nodeidx, edges, ncols, nmorecols))


def _fixlongrightedges(edges):
    for (i, (start, end)) in enumerate(edges):
        if end > start:
            edges[i] = (start, end + 1)


def _getnodelineedgestail(echars, idx, pidx, ncols, coldiff, pdiff, fix_tail):
    if fix_tail and coldiff == pdiff and coldiff != 0:
        # Still going in the same non-vertical direction.
        if coldiff == -1:
            start = max(idx + 1, pidx)
            tail = echars[idx * 2 : (start - 1) * 2]
            tail.extend([b"/", b" "] * (ncols - start))
            return tail
        else:
            return [b"\\", b" "] * (ncols - idx - 1)
    else:
        remainder = ncols - idx - 1
        return echars[-(remainder * 2) :] if remainder > 0 else []


def _drawedges(echars, edges, nodeline, interline):
    for (start, end) in edges:
        if start == end + 1:
            interline[2 * end + 1] = b"/"
        elif start == end - 1:
            interline[2 * start + 1] = b"\\"
        elif start == end:
            interline[2 * start] = echars[2 * start]
        else:
            if 2 * end >= len(nodeline):
                continue
            nodeline[2 * end] = b"+"
            if start > end:
                (start, end) = (end, start)
            for i in range(2 * start + 1, 2 * end):
                if nodeline[i] != b"+":
                    nodeline[i] = b"-"


def _getpaddingline(echars, idx, ncols, edges):
    # all edges up to the current node
    line = echars[: idx * 2]
    # an edge for the current node, if there is one
    if (idx, idx - 1) in edges or (idx, idx) in edges:
        # (idx, idx - 1)      (idx, idx)
        # | | | |           | | | |
        # +---o |           | o---+
        # | | X |           | X | |
        # | |/ /            | |/ /
        # | | |             | | |
        line.extend(echars[idx * 2 : (idx + 1) * 2])
    else:
        line.extend([b' ', b' '])
    # all edges to the right of the current node
    remainder = ncols - idx - 1
    if remainder > 0:
        line.extend(echars[-(remainder * 2) :])
    return line


def _drawendinglines(lines, extra, edgemap, seen, state):
    """Draw ending lines for missing parent edges

    None indicates an edge that ends at between this node and the next
    Replace with a short line ending in ~ and add / lines to any edges to
    the right.

    """
    if None not in edgemap.values():
        return

    # Check for more edges to the right of our ending edges.
    # We need enough space to draw adjustment lines for these.
    edgechars = extra[::2]
    while edgechars and edgechars[-1] is None:
        edgechars.pop()
    shift_size = max((edgechars.count(None) * 2) - 1, 0)
    minlines = 3 if not state.graphshorten else 2
    while len(lines) < minlines + shift_size:
        lines.append(extra[:])

    if shift_size:
        empties = []
        toshift = []
        first_empty = extra.index(None)
        for i, c in enumerate(extra[first_empty::2], first_empty // 2):
            if c is None:
                empties.append(i * 2)
            else:
                toshift.append(i * 2)
        targets = list(range(first_empty, first_empty + len(toshift) * 2, 2))
        positions = toshift[:]
        for line in lines[-shift_size:]:
            line[first_empty:] = [b' '] * (len(line) - first_empty)
            for i in range(len(positions)):
                pos = positions[i] - 1
                positions[i] = max(pos, targets[i])
                line[pos] = b'/' if pos > targets[i] else extra[toshift[i]]

    map = {1: b'|', 2: b'~'} if not state.graphshorten else {1: b'~'}
    for i, line in enumerate(lines):
        if None not in line:
            continue
        line[:] = [c or map.get(i, b' ') for c in line]

    # remove edges that ended
    remove = [p for p, c in edgemap.items() if c is None]
    for parent in remove:
        del edgemap[parent]
        seen.remove(parent)


@attr.s
class asciistate(object):
    """State of ascii() graph rendering"""

    seen = attr.ib(init=False, default=attr.Factory(list))
    edges = attr.ib(init=False, default=attr.Factory(dict))
    lastcoldiff = attr.ib(init=False, default=0)
    lastindex = attr.ib(init=False, default=0)
    styles = attr.ib(init=False, default=attr.Factory(EDGES.copy))
    graphshorten = attr.ib(init=False, default=False)


def outputgraph(ui, graph):
    """outputs an ASCII graph of a DAG

    this is a helper function for 'ascii' below.

    takes the following arguments:

    - ui to write to
    - graph data: list of { graph nodes/edges, text }

    this function can be monkey-patched by extensions to alter graph display
    without needing to mimic all of the edge-fixup logic in ascii()
    """
    for (ln, logstr) in graph:
        ui.write((ln + logstr).rstrip() + b"\n")


def ascii(ui, state, type, char, text, coldata):
    """prints an ASCII graph of the DAG

    takes the following arguments (one call per node in the graph):

      - ui to write to
      - Somewhere to keep the needed state in (init to asciistate())
      - Column of the current node in the set of ongoing edges.
      - Type indicator of node data, usually 'C' for changesets.
      - Payload: (char, lines):
        - Character to use as node's symbol.
        - List of lines to display as the node's text.
      - Edges; a list of (col, next_col) indicating the edges between
        the current node and its parents.
      - Number of columns (ongoing edges) in the current revision.
      - The difference between the number of columns (ongoing edges)
        in the next revision and the number of columns (ongoing edges)
        in the current revision. That is: -1 means one column removed;
        0 means no columns added or removed; 1 means one column added.
    """
    idx, edges, ncols, coldiff = coldata
    assert -2 < coldiff < 2

    edgemap, seen = state.edges, state.seen
    # Be tolerant of history issues; make sure we have at least ncols + coldiff
    # elements to work with. See test-glog.t for broken history test cases.
    echars = [c for p in seen for c in (edgemap.get(p, b'|'), b' ')]
    echars.extend((b'|', b' ') * max(ncols + coldiff - len(seen), 0))

    if coldiff == -1:
        # Transform
        #
        #     | | |        | | |
        #     o | |  into  o---+
        #     |X /         |/ /
        #     | |          | |
        _fixlongrightedges(edges)

    # add_padding_line says whether to rewrite
    #
    #     | | | |        | | | |
    #     | o---+  into  | o---+
    #     |  / /         |   | |  # <--- padding line
    #     o | |          |  / /
    #                    o | |
    add_padding_line = (
        len(text) > 2 and coldiff == -1 and [x for (x, y) in edges if x + 1 < y]
    )

    # fix_nodeline_tail says whether to rewrite
    #
    #     | | o | |        | | o | |
    #     | | |/ /         | | |/ /
    #     | o | |    into  | o / /   # <--- fixed nodeline tail
    #     | |/ /           | |/ /
    #     o | |            o | |
    fix_nodeline_tail = len(text) <= 2 and not add_padding_line

    # nodeline is the line containing the node character (typically o)
    nodeline = echars[: idx * 2]
    nodeline.extend([char, b" "])

    nodeline.extend(
        _getnodelineedgestail(
            echars,
            idx,
            state.lastindex,
            ncols,
            coldiff,
            state.lastcoldiff,
            fix_nodeline_tail,
        )
    )

    # shift_interline is the line containing the non-vertical
    # edges between this entry and the next
    shift_interline = echars[: idx * 2]
    for i in pycompat.xrange(2 + coldiff):
        shift_interline.append(b' ')
    count = ncols - idx - 1
    if coldiff == -1:
        for i in pycompat.xrange(count):
            shift_interline.extend([b'/', b' '])
    elif coldiff == 0:
        shift_interline.extend(echars[(idx + 1) * 2 : ncols * 2])
    else:
        for i in pycompat.xrange(count):
            shift_interline.extend([b'\\', b' '])

    # draw edges from the current node to its parents
    _drawedges(echars, edges, nodeline, shift_interline)

    # lines is the list of all graph lines to print
    lines = [nodeline]
    if add_padding_line:
        lines.append(_getpaddingline(echars, idx, ncols, edges))

    # If 'graphshorten' config, only draw shift_interline
    # when there is any non vertical flow in graph.
    if state.graphshorten:
        if any(c in br'\/' for c in shift_interline if c):
            lines.append(shift_interline)
    # Else, no 'graphshorten' config so draw shift_interline.
    else:
        lines.append(shift_interline)

    # make sure that there are as many graph lines as there are
    # log strings
    extra_interline = echars[: (ncols + coldiff) * 2]
    if len(lines) < len(text):
        while len(lines) < len(text):
            lines.append(extra_interline[:])

    _drawendinglines(lines, extra_interline, edgemap, seen, state)

    while len(text) < len(lines):
        text.append(b"")

    # print lines
    indentation_level = max(ncols, ncols + coldiff)
    lines = [
        b"%-*s " % (2 * indentation_level, b"".join(line)) for line in lines
    ]
    outputgraph(ui, zip(lines, text))

    # ... and start over
    state.lastcoldiff = coldiff
    state.lastindex = idx
