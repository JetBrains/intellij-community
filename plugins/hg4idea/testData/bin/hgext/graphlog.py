# ASCII graph log extension for Mercurial
#
# Copyright 2007 Joel Rosdahl <joel@rosdahl.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''command to view revision graphs from a shell

This extension adds a --graph option to the incoming, outgoing and log
commands. When this options is given, an ASCII representation of the
revision graph is also shown.
'''

import os
from mercurial.cmdutil import revrange, show_changeset
from mercurial.commands import templateopts
from mercurial.i18n import _
from mercurial.node import nullrev
from mercurial import bundlerepo, changegroup, cmdutil, commands, extensions
from mercurial import hg, url, util, graphmod

ASCIIDATA = 'ASC'

def asciiedges(seen, rev, parents):
    """adds edge info to changelog DAG walk suitable for ascii()"""
    if rev not in seen:
        seen.append(rev)
    nodeidx = seen.index(rev)

    knownparents = []
    newparents = []
    for parent in parents:
        if parent in seen:
            knownparents.append(parent)
        else:
            newparents.append(parent)

    ncols = len(seen)
    seen[nodeidx:nodeidx + 1] = newparents
    edges = [(nodeidx, seen.index(p)) for p in knownparents]

    if len(newparents) > 0:
        edges.append((nodeidx, nodeidx))
    if len(newparents) > 1:
        edges.append((nodeidx, nodeidx + 1))

    nmorecols = len(seen) - ncols
    return nodeidx, edges, ncols, nmorecols

def fix_long_right_edges(edges):
    for (i, (start, end)) in enumerate(edges):
        if end > start:
            edges[i] = (start, end + 1)

def get_nodeline_edges_tail(
        node_index, p_node_index, n_columns, n_columns_diff, p_diff, fix_tail):
    if fix_tail and n_columns_diff == p_diff and n_columns_diff != 0:
        # Still going in the same non-vertical direction.
        if n_columns_diff == -1:
            start = max(node_index + 1, p_node_index)
            tail = ["|", " "] * (start - node_index - 1)
            tail.extend(["/", " "] * (n_columns - start))
            return tail
        else:
            return ["\\", " "] * (n_columns - node_index - 1)
    else:
        return ["|", " "] * (n_columns - node_index - 1)

def draw_edges(edges, nodeline, interline):
    for (start, end) in edges:
        if start == end + 1:
            interline[2 * end + 1] = "/"
        elif start == end - 1:
            interline[2 * start + 1] = "\\"
        elif start == end:
            interline[2 * start] = "|"
        else:
            nodeline[2 * end] = "+"
            if start > end:
                (start, end) = (end, start)
            for i in range(2 * start + 1, 2 * end):
                if nodeline[i] != "+":
                    nodeline[i] = "-"

def get_padding_line(ni, n_columns, edges):
    line = []
    line.extend(["|", " "] * ni)
    if (ni, ni - 1) in edges or (ni, ni) in edges:
        # (ni, ni - 1)      (ni, ni)
        # | | | |           | | | |
        # +---o |           | o---+
        # | | c |           | c | |
        # | |/ /            | |/ /
        # | | |             | | |
        c = "|"
    else:
        c = " "
    line.extend([c, " "])
    line.extend(["|", " "] * (n_columns - ni - 1))
    return line

def asciistate():
    """returns the initial value for the "state" argument to ascii()"""
    return [0, 0]

def ascii(ui, state, type, char, text, coldata):
    """prints an ASCII graph of the DAG

    takes the following arguments (one call per node in the graph):

      - ui to write to
      - Somewhere to keep the needed state in (init to asciistate())
      - Column of the current node in the set of ongoing edges.
      - Type indicator of node data == ASCIIDATA.
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
    if coldiff == -1:
        # Transform
        #
        #     | | |        | | |
        #     o | |  into  o---+
        #     |X /         |/ /
        #     | |          | |
        fix_long_right_edges(edges)

    # add_padding_line says whether to rewrite
    #
    #     | | | |        | | | |
    #     | o---+  into  | o---+
    #     |  / /         |   | |  # <--- padding line
    #     o | |          |  / /
    #                    o | |
    add_padding_line = (len(text) > 2 and coldiff == -1 and
                        [x for (x, y) in edges if x + 1 < y])

    # fix_nodeline_tail says whether to rewrite
    #
    #     | | o | |        | | o | |
    #     | | |/ /         | | |/ /
    #     | o | |    into  | o / /   # <--- fixed nodeline tail
    #     | |/ /           | |/ /
    #     o | |            o | |
    fix_nodeline_tail = len(text) <= 2 and not add_padding_line

    # nodeline is the line containing the node character (typically o)
    nodeline = ["|", " "] * idx
    nodeline.extend([char, " "])

    nodeline.extend(
        get_nodeline_edges_tail(idx, state[1], ncols, coldiff,
                                state[0], fix_nodeline_tail))

    # shift_interline is the line containing the non-vertical
    # edges between this entry and the next
    shift_interline = ["|", " "] * idx
    if coldiff == -1:
        n_spaces = 1
        edge_ch = "/"
    elif coldiff == 0:
        n_spaces = 2
        edge_ch = "|"
    else:
        n_spaces = 3
        edge_ch = "\\"
    shift_interline.extend(n_spaces * [" "])
    shift_interline.extend([edge_ch, " "] * (ncols - idx - 1))

    # draw edges from the current node to its parents
    draw_edges(edges, nodeline, shift_interline)

    # lines is the list of all graph lines to print
    lines = [nodeline]
    if add_padding_line:
        lines.append(get_padding_line(idx, ncols, edges))
    lines.append(shift_interline)

    # make sure that there are as many graph lines as there are
    # log strings
    while len(text) < len(lines):
        text.append("")
    if len(lines) < len(text):
        extra_interline = ["|", " "] * (ncols + coldiff)
        while len(lines) < len(text):
            lines.append(extra_interline)

    # print lines
    indentation_level = max(ncols, ncols + coldiff)
    for (line, logstr) in zip(lines, text):
        ln = "%-*s %s" % (2 * indentation_level, "".join(line), logstr)
        ui.write(ln.rstrip() + '\n')

    # ... and start over
    state[0] = coldiff
    state[1] = idx

def get_revs(repo, rev_opt):
    if rev_opt:
        revs = revrange(repo, rev_opt)
        return (max(revs), min(revs))
    else:
        return (len(repo) - 1, 0)

def check_unsupported_flags(opts):
    for op in ["follow", "follow_first", "date", "copies", "keyword", "remove",
               "only_merges", "user", "only_branch", "prune", "newest_first",
               "no_merges", "include", "exclude"]:
        if op in opts and opts[op]:
            raise util.Abort(_("--graph option is incompatible with --%s")
                             % op.replace("_", "-"))

def generate(ui, dag, displayer, showparents, edgefn):
    seen, state = [], asciistate()
    for rev, type, ctx, parents in dag:
        char = ctx.node() in showparents and '@' or 'o'
        displayer.show(ctx)
        lines = displayer.hunk.pop(rev).split('\n')[:-1]
        ascii(ui, state, type, char, lines, edgefn(seen, rev, parents))

def graphlog(ui, repo, path=None, **opts):
    """show revision history alongside an ASCII revision graph

    Print a revision history alongside a revision graph drawn with
    ASCII characters.

    Nodes printed as an @ character are parents of the working
    directory.
    """

    check_unsupported_flags(opts)
    limit = cmdutil.loglimit(opts)
    start, stop = get_revs(repo, opts["rev"])
    if start == nullrev:
        return

    if path:
        path = util.canonpath(repo.root, os.getcwd(), path)
    if path: # could be reset in canonpath
        revdag = graphmod.filerevs(repo, path, start, stop, limit)
    else:
        if limit is not None:
            stop = max(stop, start - limit + 1)
        revdag = graphmod.revisions(repo, start, stop)

    displayer = show_changeset(ui, repo, opts, buffered=True)
    showparents = [ctx.node() for ctx in repo[None].parents()]
    generate(ui, revdag, displayer, showparents, asciiedges)

def graphrevs(repo, nodes, opts):
    limit = cmdutil.loglimit(opts)
    nodes.reverse()
    if limit is not None:
        nodes = nodes[:limit]
    return graphmod.nodes(repo, nodes)

def goutgoing(ui, repo, dest=None, **opts):
    """show the outgoing changesets alongside an ASCII revision graph

    Print the outgoing changesets alongside a revision graph drawn with
    ASCII characters.

    Nodes printed as an @ character are parents of the working
    directory.
    """

    check_unsupported_flags(opts)
    dest = ui.expandpath(dest or 'default-push', dest or 'default')
    dest, branches = hg.parseurl(dest, opts.get('branch'))
    revs, checkout = hg.addbranchrevs(repo, repo, branches, opts.get('rev'))
    other = hg.repository(cmdutil.remoteui(ui, opts), dest)
    if revs:
        revs = [repo.lookup(rev) for rev in revs]
    ui.status(_('comparing with %s\n') % url.hidepassword(dest))
    o = repo.findoutgoing(other, force=opts.get('force'))
    if not o:
        ui.status(_("no changes found\n"))
        return

    o = repo.changelog.nodesbetween(o, revs)[0]
    revdag = graphrevs(repo, o, opts)
    displayer = show_changeset(ui, repo, opts, buffered=True)
    showparents = [ctx.node() for ctx in repo[None].parents()]
    generate(ui, revdag, displayer, showparents, asciiedges)

def gincoming(ui, repo, source="default", **opts):
    """show the incoming changesets alongside an ASCII revision graph

    Print the incoming changesets alongside a revision graph drawn with
    ASCII characters.

    Nodes printed as an @ character are parents of the working
    directory.
    """

    check_unsupported_flags(opts)
    source, branches = hg.parseurl(ui.expandpath(source), opts.get('branch'))
    other = hg.repository(cmdutil.remoteui(repo, opts), source)
    revs, checkout = hg.addbranchrevs(repo, other, branches, opts.get('rev'))
    ui.status(_('comparing with %s\n') % url.hidepassword(source))
    if revs:
        revs = [other.lookup(rev) for rev in revs]
    incoming = repo.findincoming(other, heads=revs, force=opts["force"])
    if not incoming:
        try:
            os.unlink(opts["bundle"])
        except:
            pass
        ui.status(_("no changes found\n"))
        return

    cleanup = None
    try:

        fname = opts["bundle"]
        if fname or not other.local():
            # create a bundle (uncompressed if other repo is not local)
            if revs is None:
                cg = other.changegroup(incoming, "incoming")
            else:
                cg = other.changegroupsubset(incoming, revs, 'incoming')
            bundletype = other.local() and "HG10BZ" or "HG10UN"
            fname = cleanup = changegroup.writebundle(cg, fname, bundletype)
            # keep written bundle?
            if opts["bundle"]:
                cleanup = None
            if not other.local():
                # use the created uncompressed bundlerepo
                other = bundlerepo.bundlerepository(ui, repo.root, fname)

        chlist = other.changelog.nodesbetween(incoming, revs)[0]
        revdag = graphrevs(other, chlist, opts)
        displayer = show_changeset(ui, other, opts, buffered=True)
        showparents = [ctx.node() for ctx in repo[None].parents()]
        generate(ui, revdag, displayer, showparents, asciiedges)

    finally:
        if hasattr(other, 'close'):
            other.close()
        if cleanup:
            os.unlink(cleanup)

def uisetup(ui):
    '''Initialize the extension.'''
    _wrapcmd(ui, 'log', commands.table, graphlog)
    _wrapcmd(ui, 'incoming', commands.table, gincoming)
    _wrapcmd(ui, 'outgoing', commands.table, goutgoing)

def _wrapcmd(ui, cmd, table, wrapfn):
    '''wrap the command'''
    def graph(orig, *args, **kwargs):
        if kwargs['graph']:
            return wrapfn(*args, **kwargs)
        return orig(*args, **kwargs)
    entry = extensions.wrapcommand(table, cmd, graph)
    entry[1].append(('G', 'graph', None, _("show the revision DAG")))

cmdtable = {
    "glog":
        (graphlog,
         [('l', 'limit', '', _('limit number of changes displayed')),
          ('p', 'patch', False, _('show patch')),
          ('r', 'rev', [], _('show the specified revision or range')),
         ] + templateopts,
         _('hg glog [OPTION]... [FILE]')),
}
