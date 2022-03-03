# show.py - Extension implementing `hg show`
#
# Copyright 2017 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""unified command to show various repository information (EXPERIMENTAL)

This extension provides the :hg:`show` command, which provides a central
command for displaying commonly-accessed repository data and views of that
data.

The following config options can influence operation.

``commands``
------------

``show.aliasprefix``
   List of strings that will register aliases for views. e.g. ``s`` will
   effectively set config options ``alias.s<view> = show <view>`` for all
   views. i.e. `hg swork` would execute `hg show work`.

   Aliases that would conflict with existing registrations will not be
   performed.
"""

from __future__ import absolute_import

from mercurial.i18n import _
from mercurial.node import nullrev
from mercurial import (
    cmdutil,
    commands,
    destutil,
    error,
    formatter,
    graphmod,
    logcmdutil,
    phases,
    pycompat,
    registrar,
    revset,
    revsetlang,
    scmutil,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

cmdtable = {}
command = registrar.command(cmdtable)

revsetpredicate = registrar.revsetpredicate()


class showcmdfunc(registrar._funcregistrarbase):
    """Register a function to be invoked for an `hg show <thing>`."""

    # Used by _formatdoc().
    _docformat = b'%s -- %s'

    def _extrasetup(self, name, func, fmtopic=None, csettopic=None):
        """Called with decorator arguments to register a show view.

        ``name`` is the sub-command name.

        ``func`` is the function being decorated.

        ``fmtopic`` is the topic in the style that will be rendered for
        this view.

        ``csettopic`` is the topic in the style to be used for a changeset
        printer.

        If ``fmtopic`` is specified, the view function will receive a
        formatter instance. If ``csettopic`` is specified, the view
        function will receive a changeset printer.
        """
        func._fmtopic = fmtopic
        func._csettopic = csettopic


showview = showcmdfunc()


@command(
    b'show',
    [
        # TODO: Switch this template flag to use cmdutil.formatteropts if
        # 'hg show' becomes stable before --template/-T is stable. For now,
        # we are putting it here without the '(EXPERIMENTAL)' flag because it
        # is an important part of the 'hg show' user experience and the entire
        # 'hg show' experience is experimental.
        (b'T', b'template', b'', b'display with template', _(b'TEMPLATE')),
    ],
    _(b'VIEW'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
)
def show(ui, repo, view=None, template=None):
    """show various repository information

    A requested view of repository data is displayed.

    If no view is requested, the list of available views is shown and the
    command aborts.

    .. note::

       There are no backwards compatibility guarantees for the output of this
       command. Output may change in any future Mercurial release.

       Consumers wanting stable command output should specify a template via
       ``-T/--template``.

    List of available views:
    """
    if ui.plain() and not template:
        hint = _(b'invoke with -T/--template to control output format')
        raise error.Abort(
            _(b'must specify a template in plain mode'), hint=hint
        )

    views = showview._table

    if not view:
        ui.pager(b'show')
        # TODO consider using formatter here so available views can be
        # rendered to custom format.
        ui.write(_(b'available views:\n'))
        ui.write(b'\n')

        for name, func in sorted(views.items()):
            ui.write(b'%s\n' % pycompat.sysbytes(func.__doc__))

        ui.write(b'\n')
        raise error.Abort(
            _(b'no view requested'),
            hint=_(b'use "hg show VIEW" to choose a view'),
        )

    # TODO use same logic as dispatch to perform prefix matching.
    if view not in views:
        raise error.Abort(
            _(b'unknown view: %s') % view,
            hint=_(b'run "hg show" to see available views'),
        )

    template = template or b'show'

    fn = views[view]
    ui.pager(b'show')

    if fn._fmtopic:
        fmtopic = b'show%s' % fn._fmtopic
        with ui.formatter(fmtopic, {b'template': template}) as fm:
            return fn(ui, repo, fm)
    elif fn._csettopic:
        ref = b'show%s' % fn._csettopic
        spec = formatter.lookuptemplate(ui, ref, template)
        displayer = logcmdutil.changesettemplater(ui, repo, spec, buffered=True)
        return fn(ui, repo, displayer)
    else:
        return fn(ui, repo)


@showview(b'bookmarks', fmtopic=b'bookmarks')
def showbookmarks(ui, repo, fm):
    """bookmarks and their associated changeset"""
    marks = repo._bookmarks
    if not len(marks):
        # This is a bit hacky. Ideally, templates would have a way to
        # specify an empty output, but we shouldn't corrupt JSON while
        # waiting for this functionality.
        if not isinstance(fm, formatter.jsonformatter):
            ui.write(_(b'(no bookmarks set)\n'))
        return

    revs = [repo[node].rev() for node in marks.values()]
    active = repo._activebookmark
    longestname = max(len(b) for b in marks)
    nodelen = longestshortest(repo, revs)

    for bm, node in sorted(marks.items()):
        fm.startitem()
        fm.context(ctx=repo[node])
        fm.write(b'bookmark', b'%s', bm)
        fm.write(b'node', fm.hexfunc(node), fm.hexfunc(node))
        fm.data(
            active=bm == active, longestbookmarklen=longestname, nodelen=nodelen
        )


@showview(b'stack', csettopic=b'stack')
def showstack(ui, repo, displayer):
    """current line of work"""
    wdirctx = repo[b'.']
    if wdirctx.rev() == nullrev:
        raise error.Abort(
            _(
                b'stack view only available when there is a '
                b'working directory'
            )
        )

    if wdirctx.phase() == phases.public:
        ui.write(
            _(
                b'(empty stack; working directory parent is a published '
                b'changeset)\n'
            )
        )
        return

    # TODO extract "find stack" into a function to facilitate
    # customization and reuse.

    baserev = destutil.stackbase(ui, repo)
    basectx = None

    if baserev is None:
        baserev = wdirctx.rev()
        stackrevs = {wdirctx.rev()}
    else:
        stackrevs = set(repo.revs(b'%d::.', baserev))

    ctx = repo[baserev]
    if ctx.p1().rev() != nullrev:
        basectx = ctx.p1()

    # And relevant descendants.
    branchpointattip = False
    cl = repo.changelog

    for rev in cl.descendants([wdirctx.rev()]):
        ctx = repo[rev]

        # Will only happen if . is public.
        if ctx.phase() == phases.public:
            break

        stackrevs.add(ctx.rev())

        # ctx.children() within a function iterating on descandants
        # potentially has severe performance concerns because revlog.children()
        # iterates over all revisions after ctx's node. However, the number of
        # draft changesets should be a reasonably small number. So even if
        # this is quadratic, the perf impact should be minimal.
        if len(ctx.children()) > 1:
            branchpointattip = True
            break

    stackrevs = list(sorted(stackrevs, reverse=True))

    # Find likely target heads for the current stack. These are likely
    # merge or rebase targets.
    if basectx:
        # TODO make this customizable?
        newheads = set(
            repo.revs(
                b'heads(%d::) - %ld - not public()', basectx.rev(), stackrevs
            )
        )
    else:
        newheads = set()

    allrevs = set(stackrevs) | newheads | {baserev}
    nodelen = longestshortest(repo, allrevs)

    try:
        cmdutil.findcmd(b'rebase', commands.table)
        haverebase = True
    except (error.AmbiguousCommand, error.UnknownCommand):
        haverebase = False

    # TODO use templating.
    # TODO consider using graphmod. But it may not be necessary given
    # our simplicity and the customizations required.
    # TODO use proper graph symbols from graphmod

    tres = formatter.templateresources(ui, repo)
    shortesttmpl = formatter.maketemplater(
        ui, b'{shortest(node, %d)}' % nodelen, resources=tres
    )

    def shortest(ctx):
        return shortesttmpl.renderdefault({b'ctx': ctx, b'node': ctx.hex()})

    # We write out new heads to aid in DAG awareness and to help with decision
    # making on how the stack should be reconciled with commits made since the
    # branch point.
    if newheads:
        # Calculate distance from base so we can render the count and so we can
        # sort display order by commit distance.
        revdistance = {}
        for head in newheads:
            # There is some redundancy in DAG traversal here and therefore
            # room to optimize.
            ancestors = cl.ancestors([head], stoprev=basectx.rev())
            revdistance[head] = len(list(ancestors))

        sourcectx = repo[stackrevs[-1]]

        sortedheads = sorted(
            newheads, key=lambda x: revdistance[x], reverse=True
        )

        for i, rev in enumerate(sortedheads):
            ctx = repo[rev]

            if i:
                ui.write(b': ')
            else:
                ui.write(b'  ')

            ui.writenoi18n(b'o  ')
            displayer.show(ctx, nodelen=nodelen)
            displayer.flush(ctx)
            ui.write(b'\n')

            if i:
                ui.write(b':/')
            else:
                ui.write(b' /')

            ui.write(b'    (')
            ui.write(
                _(b'%d commits ahead') % revdistance[rev],
                label=b'stack.commitdistance',
            )

            if haverebase:
                # TODO may be able to omit --source in some scenarios
                ui.write(b'; ')
                ui.write(
                    (
                        b'hg rebase --source %s --dest %s'
                        % (shortest(sourcectx), shortest(ctx))
                    ),
                    label=b'stack.rebasehint',
                )

            ui.write(b')\n')

        ui.write(b':\n:    ')
        ui.write(_(b'(stack head)\n'), label=b'stack.label')

    if branchpointattip:
        ui.write(b' \\ /  ')
        ui.write(_(b'(multiple children)\n'), label=b'stack.label')
        ui.write(b'  |\n')

    for rev in stackrevs:
        ctx = repo[rev]
        symbol = b'@' if rev == wdirctx.rev() else b'o'

        if newheads:
            ui.write(b': ')
        else:
            ui.write(b'  ')

        ui.write(symbol, b'  ')
        displayer.show(ctx, nodelen=nodelen)
        displayer.flush(ctx)
        ui.write(b'\n')

    # TODO display histedit hint?

    if basectx:
        # Vertically and horizontally separate stack base from parent
        # to reinforce stack boundary.
        if newheads:
            ui.write(b':/   ')
        else:
            ui.write(b' /   ')

        ui.write(_(b'(stack base)'), b'\n', label=b'stack.label')
        ui.writenoi18n(b'o  ')

        displayer.show(basectx, nodelen=nodelen)
        displayer.flush(basectx)
        ui.write(b'\n')


@revsetpredicate(b'_underway([commitage[, headage]])')
def underwayrevset(repo, subset, x):
    args = revset.getargsdict(x, b'underway', b'commitage headage')
    if b'commitage' not in args:
        args[b'commitage'] = None
    if b'headage' not in args:
        args[b'headage'] = None

    # We assume callers of this revset add a topographical sort on the
    # result. This means there is no benefit to making the revset lazy
    # since the topographical sort needs to consume all revs.
    #
    # With this in mind, we build up the set manually instead of constructing
    # a complex revset. This enables faster execution.

    # Mutable changesets (non-public) are the most important changesets
    # to return. ``not public()`` will also pull in obsolete changesets if
    # there is a non-obsolete changeset with obsolete ancestors. This is
    # why we exclude obsolete changesets from this query.
    rs = b'not public() and not obsolete()'
    rsargs = []
    if args[b'commitage']:
        rs += b' and date(%s)'
        rsargs.append(
            revsetlang.getstring(
                args[b'commitage'], _(b'commitage requires a string')
            )
        )

    mutable = repo.revs(rs, *rsargs)
    relevant = revset.baseset(mutable)

    # Add parents of mutable changesets to provide context.
    relevant += repo.revs(b'parents(%ld)', mutable)

    # We also pull in (public) heads if they a) aren't closing a branch
    # b) are recent.
    rs = b'head() and not closed()'
    rsargs = []
    if args[b'headage']:
        rs += b' and date(%s)'
        rsargs.append(
            revsetlang.getstring(
                args[b'headage'], _(b'headage requires a string')
            )
        )

    relevant += repo.revs(rs, *rsargs)

    # Add working directory parent.
    wdirrev = repo[b'.'].rev()
    if wdirrev != nullrev:
        relevant += revset.baseset({wdirrev})

    return subset & relevant


@showview(b'work', csettopic=b'work')
def showwork(ui, repo, displayer):
    """changesets that aren't finished"""
    # TODO support date-based limiting when calling revset.
    revs = repo.revs(b'sort(_underway(), topo)')
    nodelen = longestshortest(repo, revs)

    revdag = graphmod.dagwalker(repo, revs)

    ui.setconfig(b'experimental', b'graphshorten', True)
    logcmdutil.displaygraph(
        ui,
        repo,
        revdag,
        displayer,
        graphmod.asciiedges,
        props={b'nodelen': nodelen},
    )


def extsetup(ui):
    # Alias `hg <prefix><view>` to `hg show <view>`.
    for prefix in ui.configlist(b'commands', b'show.aliasprefix'):
        for view in showview._table:
            name = b'%s%s' % (prefix, view)

            choice, allcommands = cmdutil.findpossible(
                name, commands.table, strict=True
            )

            # This alias is already a command name. Don't set it.
            if name in choice:
                continue

            # Same for aliases.
            if ui.config(b'alias', name, None):
                continue

            ui.setconfig(b'alias', name, b'show %s' % view, source=b'show')


def longestshortest(repo, revs, minlen=4):
    """Return the length of the longest shortest node to identify revisions.

    The result of this function can be used with the ``shortest()`` template
    function to ensure that a value is unique and unambiguous for a given
    set of nodes.

    The number of revisions in the repo is taken into account to prevent
    a numeric node prefix from conflicting with an integer revision number.
    If we fail to do this, a value of e.g. ``10023`` could mean either
    revision 10023 or node ``10023abc...``.
    """
    if not revs:
        return minlen
    cl = repo.changelog
    return max(
        len(scmutil.shortesthexnodeidprefix(repo, cl.node(r), minlen))
        for r in revs
    )


# Adjust the docstring of the show command so it shows all registered views.
# This is a bit hacky because it runs at the end of module load. When moved
# into core or when another extension wants to provide a view, we'll need
# to do this more robustly.
# TODO make this more robust.
def _updatedocstring():
    longest = max(map(len, showview._table.keys()))
    entries = []
    for key in sorted(showview._table.keys()):
        entries.append(
            r'    %s   %s'
            % (
                pycompat.sysstr(key.ljust(longest)),
                showview._table[key]._origdoc,
            )
        )

    cmdtable[b'show'][0].__doc__ = pycompat.sysstr(b'%s\n\n%s\n    ') % (
        cmdtable[b'show'][0].__doc__.rstrip(),
        pycompat.sysstr(b'\n\n').join(entries),
    )


_updatedocstring()
