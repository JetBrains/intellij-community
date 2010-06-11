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

from mercurial.node import nullrev

CHANGESET = 'C'

def revisions(repo, start, stop):
    """cset DAG generator yielding (id, CHANGESET, ctx, [parentids]) tuples

    This generator function walks through the revision history from revision
    start to revision stop (which must be less than or equal to start). It
    returns a tuple for each node. The node and parent ids are arbitrary
    integers which identify a node in the context of the graph returned.
    """
    cur = start
    while cur >= stop:
        ctx = repo[cur]
        parents = [p.rev() for p in ctx.parents() if p.rev() != nullrev]
        yield (cur, CHANGESET, ctx, sorted(parents))
        cur -= 1

def filerevs(repo, path, start, stop, limit=None):
    """file cset DAG generator yielding (id, CHANGESET, ctx, [parentids]) tuples

    This generator function walks through the revision history of a single
    file from revision start down to revision stop.
    """
    filerev = len(repo.file(path)) - 1
    rev = stop + 1
    count = 0
    while filerev >= 0 and rev > stop:
        fctx = repo.filectx(path, fileid=filerev)
        parents = [f.linkrev() for f in fctx.parents() if f.path() == path]
        rev = fctx.rev()
        if rev <= start:
            yield (rev, CHANGESET, fctx.changectx(), sorted(parents))
            count += 1
            if count == limit:
                break
        filerev -= 1

def nodes(repo, nodes):
    """cset DAG generator yielding (id, CHANGESET, ctx, [parentids]) tuples

    This generator function walks the given nodes. It only returns parents
    that are in nodes, too.
    """
    include = set(nodes)
    for node in nodes:
        ctx = repo[node]
        parents = [p.rev() for p in ctx.parents() if p.node() in include]
        yield (ctx.rev(), CHANGESET, ctx, sorted(parents))

def colored(dag):
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
    for (cur, type, data, parents) in dag:

        # Compute seen and next
        if cur not in seen:
            seen.append(cur) # new head
            colors[cur] = newcolor
            newcolor += 1

        col = seen.index(cur)
        color = colors.pop(cur)
        next = seen[:]

        # Add parents to next
        addparents = [p for p in parents if p not in next]
        next[col:col + 1] = addparents

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
                edges.append((ecol, next.index(eid), colors[eid]))
            elif eid == cur:
                for p in parents:
                    edges.append((ecol, next.index(p), colors[p]))

        # Yield and move on
        yield (cur, type, data, (col, color), edges)
        seen = next
