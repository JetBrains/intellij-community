# histedit.py - interactive history editing for mercurial
#
# Copyright 2009 Augie Fackler <raf@durin42.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""interactive history editing

With this extension installed, Mercurial gains one new command: histedit. Usage
is as follows, assuming the following history::

 @  3[tip]   7c2fd3b9020c   2009-04-27 18:04 -0500   durin42
 |    Add delta
 |
 o  2   030b686bedc4   2009-04-27 18:04 -0500   durin42
 |    Add gamma
 |
 o  1   c561b4e977df   2009-04-27 18:04 -0500   durin42
 |    Add beta
 |
 o  0   d8d2fcd0e319   2009-04-27 18:04 -0500   durin42
      Add alpha

If you were to run ``hg histedit c561b4e977df``, you would see the following
file open in your editor::

 pick c561b4e977df Add beta
 pick 030b686bedc4 Add gamma
 pick 7c2fd3b9020c Add delta

 # Edit history between c561b4e977df and 7c2fd3b9020c
 #
 # Commands:
 #  p, pick = use commit
 #  e, edit = use commit, but stop for amending
 #  f, fold = use commit, but fold into previous commit (combines N and N-1)
 #  d, drop = remove commit from history
 #  m, mess = edit message without changing commit content
 #

In this file, lines beginning with ``#`` are ignored. You must specify a rule
for each revision in your history. For example, if you had meant to add gamma
before beta, and then wanted to add delta in the same revision as beta, you
would reorganize the file to look like this::

 pick 030b686bedc4 Add gamma
 pick c561b4e977df Add beta
 fold 7c2fd3b9020c Add delta

 # Edit history between c561b4e977df and 7c2fd3b9020c
 #
 # Commands:
 #  p, pick = use commit
 #  e, edit = use commit, but stop for amending
 #  f, fold = use commit, but fold into previous commit (combines N and N-1)
 #  d, drop = remove commit from history
 #  m, mess = edit message without changing commit content
 #

At which point you close the editor and ``histedit`` starts working. When you
specify a ``fold`` operation, ``histedit`` will open an editor when it folds
those revisions together, offering you a chance to clean up the commit message::

 Add beta
 ***
 Add delta

Edit the commit message to your liking, then close the editor. For
this example, let's assume that the commit message was changed to
``Add beta and delta.`` After histedit has run and had a chance to
remove any old or temporary revisions it needed, the history looks
like this::

 @  2[tip]   989b4d060121   2009-04-27 18:04 -0500   durin42
 |    Add beta and delta.
 |
 o  1   081603921c3f   2009-04-27 18:04 -0500   durin42
 |    Add gamma
 |
 o  0   d8d2fcd0e319   2009-04-27 18:04 -0500   durin42
      Add alpha

Note that ``histedit`` does *not* remove any revisions (even its own temporary
ones) until after it has completed all the editing operations, so it will
probably perform several strip operations when it's done. For the above example,
it had to run strip twice. Strip can be slow depending on a variety of factors,
so you might need to be a little patient. You can choose to keep the original
revisions by passing the ``--keep`` flag.

The ``edit`` operation will drop you back to a command prompt,
allowing you to edit files freely, or even use ``hg record`` to commit
some changes as a separate commit. When you're done, any remaining
uncommitted changes will be committed as well. When done, run ``hg
histedit --continue`` to finish this step. You'll be prompted for a
new commit message, but the default commit message will be the
original message for the ``edit`` ed revision.

The ``message`` operation will give you a chance to revise a commit
message without changing the contents. It's a shortcut for doing
``edit`` immediately followed by `hg histedit --continue``.

If ``histedit`` encounters a conflict when moving a revision (while
handling ``pick`` or ``fold``), it'll stop in a similar manner to
``edit`` with the difference that it won't prompt you for a commit
message when done. If you decide at this point that you don't like how
much work it will be to rearrange history, or that you made a mistake,
you can use ``hg histedit --abort`` to abandon the new changes you
have made and return to the state before you attempted to edit your
history.

If we clone the histedit-ed example repository above and add four more
changes, such that we have the following history::

   @  6[tip]   038383181893   2009-04-27 18:04 -0500   stefan
   |    Add theta
   |
   o  5   140988835471   2009-04-27 18:04 -0500   stefan
   |    Add eta
   |
   o  4   122930637314   2009-04-27 18:04 -0500   stefan
   |    Add zeta
   |
   o  3   836302820282   2009-04-27 18:04 -0500   stefan
   |    Add epsilon
   |
   o  2   989b4d060121   2009-04-27 18:04 -0500   durin42
   |    Add beta and delta.
   |
   o  1   081603921c3f   2009-04-27 18:04 -0500   durin42
   |    Add gamma
   |
   o  0   d8d2fcd0e319   2009-04-27 18:04 -0500   durin42
        Add alpha

If you run ``hg histedit --outgoing`` on the clone then it is the same
as running ``hg histedit 836302820282``. If you need plan to push to a
repository that Mercurial does not detect to be related to the source
repo, you can add a ``--force`` option.
"""

try:
    import cPickle as pickle
except ImportError:
    import pickle
import os
import sys

from mercurial import cmdutil
from mercurial import discovery
from mercurial import error
from mercurial import copies
from mercurial import context
from mercurial import hg
from mercurial import lock as lockmod
from mercurial import node
from mercurial import repair
from mercurial import scmutil
from mercurial import util
from mercurial import obsolete
from mercurial import merge as mergemod
from mercurial.i18n import _

cmdtable = {}
command = cmdutil.command(cmdtable)

testedwith = 'internal'

# i18n: command names and abbreviations must remain untranslated
editcomment = _("""# Edit history between %s and %s
#
# Commands:
#  p, pick = use commit
#  e, edit = use commit, but stop for amending
#  f, fold = use commit, but fold into previous commit (combines N and N-1)
#  d, drop = remove commit from history
#  m, mess = edit message without changing commit content
#
""")

def commitfuncfor(repo, src):
    """Build a commit function for the replacement of <src>

    This function ensure we apply the same treatment to all changesets.

    - Add a 'histedit_source' entry in extra.

    Note that fold have its own separated logic because its handling is a bit
    different and not easily factored out of the fold method.
    """
    phasemin = src.phase()
    def commitfunc(**kwargs):
        phasebackup = repo.ui.backupconfig('phases', 'new-commit')
        try:
            repo.ui.setconfig('phases', 'new-commit', phasemin)
            extra = kwargs.get('extra', {}).copy()
            extra['histedit_source'] = src.hex()
            kwargs['extra'] = extra
            return repo.commit(**kwargs)
        finally:
            repo.ui.restoreconfig(phasebackup)
    return commitfunc



def applychanges(ui, repo, ctx, opts):
    """Merge changeset from ctx (only) in the current working directory"""
    wcpar = repo.dirstate.parents()[0]
    if ctx.p1().node() == wcpar:
        # edition ar "in place" we do not need to make any merge,
        # just applies changes on parent for edition
        cmdutil.revert(ui, repo, ctx, (wcpar, node.nullid), all=True)
        stats = None
    else:
        try:
            # ui.forcemerge is an internal variable, do not document
            repo.ui.setconfig('ui', 'forcemerge', opts.get('tool', ''))
            stats = mergemod.update(repo, ctx.node(), True, True, False,
                                    ctx.p1().node())
        finally:
            repo.ui.setconfig('ui', 'forcemerge', '')
        repo.setparents(wcpar, node.nullid)
        repo.dirstate.write()
        # fix up dirstate for copies and renames
    cmdutil.duplicatecopies(repo, ctx.rev(), ctx.p1().rev())
    return stats

def collapse(repo, first, last, commitopts):
    """collapse the set of revisions from first to last as new one.

    Expected commit options are:
        - message
        - date
        - username
    Commit message is edited in all cases.

    This function works in memory."""
    ctxs = list(repo.set('%d::%d', first, last))
    if not ctxs:
        return None
    base = first.parents()[0]

    # commit a new version of the old changeset, including the update
    # collect all files which might be affected
    files = set()
    for ctx in ctxs:
        files.update(ctx.files())

    # Recompute copies (avoid recording a -> b -> a)
    copied = copies.pathcopies(first, last)

    # prune files which were reverted by the updates
    def samefile(f):
        if f in last.manifest():
            a = last.filectx(f)
            if f in base.manifest():
                b = base.filectx(f)
                return (a.data() == b.data()
                        and a.flags() == b.flags())
            else:
                return False
        else:
            return f not in base.manifest()
    files = [f for f in files if not samefile(f)]
    # commit version of these files as defined by head
    headmf = last.manifest()
    def filectxfn(repo, ctx, path):
        if path in headmf:
            fctx = last[path]
            flags = fctx.flags()
            mctx = context.memfilectx(fctx.path(), fctx.data(),
                                      islink='l' in flags,
                                      isexec='x' in flags,
                                      copied=copied.get(path))
            return mctx
        raise IOError()

    if commitopts.get('message'):
        message = commitopts['message']
    else:
        message = first.description()
    user = commitopts.get('user')
    date = commitopts.get('date')
    extra = commitopts.get('extra')

    parents = (first.p1().node(), first.p2().node())
    new = context.memctx(repo,
                         parents=parents,
                         text=message,
                         files=files,
                         filectxfn=filectxfn,
                         user=user,
                         date=date,
                         extra=extra)
    new._text = cmdutil.commitforceeditor(repo, new, [])
    return repo.commitctx(new)

def pick(ui, repo, ctx, ha, opts):
    oldctx = repo[ha]
    if oldctx.parents()[0] == ctx:
        ui.debug('node %s unchanged\n' % ha)
        return oldctx, []
    hg.update(repo, ctx.node())
    stats = applychanges(ui, repo, oldctx, opts)
    if stats and stats[3] > 0:
        raise error.InterventionRequired(_('Fix up the change and run '
                                           'hg histedit --continue'))
    # drop the second merge parent
    commit = commitfuncfor(repo, oldctx)
    n = commit(text=oldctx.description(), user=oldctx.user(),
               date=oldctx.date(), extra=oldctx.extra())
    if n is None:
        ui.warn(_('%s: empty changeset\n')
                     % node.hex(ha))
        return ctx, []
    new = repo[n]
    return new, [(oldctx.node(), (n,))]


def edit(ui, repo, ctx, ha, opts):
    oldctx = repo[ha]
    hg.update(repo, ctx.node())
    applychanges(ui, repo, oldctx, opts)
    raise error.InterventionRequired(
        _('Make changes as needed, you may commit or record as needed now.\n'
          'When you are finished, run hg histedit --continue to resume.'))

def fold(ui, repo, ctx, ha, opts):
    oldctx = repo[ha]
    hg.update(repo, ctx.node())
    stats = applychanges(ui, repo, oldctx, opts)
    if stats and stats[3] > 0:
        raise error.InterventionRequired(
            _('Fix up the change and run hg histedit --continue'))
    n = repo.commit(text='fold-temp-revision %s' % ha, user=oldctx.user(),
                    date=oldctx.date(), extra=oldctx.extra())
    if n is None:
        ui.warn(_('%s: empty changeset')
                     % node.hex(ha))
        return ctx, []
    return finishfold(ui, repo, ctx, oldctx, n, opts, [])

def finishfold(ui, repo, ctx, oldctx, newnode, opts, internalchanges):
    parent = ctx.parents()[0].node()
    hg.update(repo, parent)
    ### prepare new commit data
    commitopts = opts.copy()
    # username
    if ctx.user() == oldctx.user():
        username = ctx.user()
    else:
        username = ui.username()
    commitopts['user'] = username
    # commit message
    newmessage = '\n***\n'.join(
        [ctx.description()] +
        [repo[r].description() for r in internalchanges] +
        [oldctx.description()]) + '\n'
    commitopts['message'] = newmessage
    # date
    commitopts['date'] = max(ctx.date(), oldctx.date())
    extra = ctx.extra().copy()
    # histedit_source
    # note: ctx is likely a temporary commit but that the best we can do here
    #       This is sufficient to solve issue3681 anyway
    extra['histedit_source'] = '%s,%s' % (ctx.hex(), oldctx.hex())
    commitopts['extra'] = extra
    phasebackup = repo.ui.backupconfig('phases', 'new-commit')
    try:
        phasemin = max(ctx.phase(), oldctx.phase())
        repo.ui.setconfig('phases', 'new-commit', phasemin)
        n = collapse(repo, ctx, repo[newnode], commitopts)
    finally:
        repo.ui.restoreconfig(phasebackup)
    if n is None:
        return ctx, []
    hg.update(repo, n)
    replacements = [(oldctx.node(), (newnode,)),
                     (ctx.node(), (n,)),
                     (newnode, (n,)),
                    ]
    for ich in internalchanges:
        replacements.append((ich, (n,)))
    return repo[n], replacements

def drop(ui, repo, ctx, ha, opts):
    return ctx, [(repo[ha].node(), ())]


def message(ui, repo, ctx, ha, opts):
    oldctx = repo[ha]
    hg.update(repo, ctx.node())
    stats = applychanges(ui, repo, oldctx, opts)
    if stats and stats[3] > 0:
        raise error.InterventionRequired(
            _('Fix up the change and run hg histedit --continue'))
    message = oldctx.description() + '\n'
    message = ui.edit(message, ui.username())
    commit = commitfuncfor(repo, oldctx)
    new = commit(text=message, user=oldctx.user(), date=oldctx.date(),
                 extra=oldctx.extra())
    newctx = repo[new]
    if oldctx.node() != newctx.node():
        return newctx, [(oldctx.node(), (new,))]
    # We didn't make an edit, so just indicate no replaced nodes
    return newctx, []

def findoutgoing(ui, repo, remote=None, force=False, opts={}):
    """utility function to find the first outgoing changeset

    Used by initialisation code"""
    dest = ui.expandpath(remote or 'default-push', remote or 'default')
    dest, revs = hg.parseurl(dest, None)[:2]
    ui.status(_('comparing with %s\n') % util.hidepassword(dest))

    revs, checkout = hg.addbranchrevs(repo, repo, revs, None)
    other = hg.peer(repo, opts, dest)

    if revs:
        revs = [repo.lookup(rev) for rev in revs]

    # hexlify nodes from outgoing, because we're going to parse
    # parent[0] using revsingle below, and if the binary hash
    # contains special revset characters like ":" the revset
    # parser can choke.
    outgoing = discovery.findcommonoutgoing(repo, other, revs, force=force)
    if not outgoing.missing:
        raise util.Abort(_('no outgoing ancestors'))
    return outgoing.missing[0]

actiontable = {'p': pick,
               'pick': pick,
               'e': edit,
               'edit': edit,
               'f': fold,
               'fold': fold,
               'd': drop,
               'drop': drop,
               'm': message,
               'mess': message,
               }

@command('histedit',
    [('', 'commands', '',
      _('Read history edits from the specified file.')),
     ('c', 'continue', False, _('continue an edit already in progress')),
     ('k', 'keep', False,
      _("don't strip old nodes after edit is complete")),
     ('', 'abort', False, _('abort an edit in progress')),
     ('o', 'outgoing', False, _('changesets not found in destination')),
     ('f', 'force', False,
      _('force outgoing even for unrelated repositories')),
     ('r', 'rev', [], _('first revision to be edited'))],
     _("[PARENT]"))
def histedit(ui, repo, *freeargs, **opts):
    """interactively edit changeset history
    """
    # TODO only abort if we try and histedit mq patches, not just
    # blanket if mq patches are applied somewhere
    mq = getattr(repo, 'mq', None)
    if mq and mq.applied:
        raise util.Abort(_('source has mq patches applied'))

    # basic argument incompatibility processing
    outg = opts.get('outgoing')
    cont = opts.get('continue')
    abort = opts.get('abort')
    force = opts.get('force')
    rules = opts.get('commands', '')
    revs = opts.get('rev', [])
    goal = 'new' # This invocation goal, in new, continue, abort
    if force and not outg:
        raise util.Abort(_('--force only allowed with --outgoing'))
    if cont:
        if util.any((outg, abort, revs, freeargs, rules)):
            raise util.Abort(_('no arguments allowed with --continue'))
        goal = 'continue'
    elif abort:
        if util.any((outg, revs, freeargs, rules)):
            raise util.Abort(_('no arguments allowed with --abort'))
        goal = 'abort'
    else:
        if os.path.exists(os.path.join(repo.path, 'histedit-state')):
            raise util.Abort(_('history edit already in progress, try '
                               '--continue or --abort'))
        if outg:
            if revs:
                raise util.Abort(_('no revisions allowed with --outgoing'))
            if len(freeargs) > 1:
                raise util.Abort(
                    _('only one repo argument allowed with --outgoing'))
        else:
            revs.extend(freeargs)
            if len(revs) != 1:
                raise util.Abort(
                    _('histedit requires exactly one parent revision'))


    if goal == 'continue':
        (parentctxnode, rules, keep, topmost, replacements) = readstate(repo)
        currentparent, wantnull = repo.dirstate.parents()
        parentctx = repo[parentctxnode]
        parentctx, repl = bootstrapcontinue(ui, repo, parentctx, rules, opts)
        replacements.extend(repl)
    elif goal == 'abort':
        (parentctxnode, rules, keep, topmost, replacements) = readstate(repo)
        mapping, tmpnodes, leafs, _ntm = processreplacement(repo, replacements)
        ui.debug('restore wc to old parent %s\n' % node.short(topmost))
        hg.clean(repo, topmost)
        cleanupnode(ui, repo, 'created', tmpnodes)
        cleanupnode(ui, repo, 'temp', leafs)
        os.unlink(os.path.join(repo.path, 'histedit-state'))
        return
    else:
        cmdutil.bailifchanged(repo)

        topmost, empty = repo.dirstate.parents()
        if outg:
            if freeargs:
                remote = freeargs[0]
            else:
                remote = None
            root = findoutgoing(ui, repo, remote, force, opts)
        else:
            root = revs[0]
            root = scmutil.revsingle(repo, root).node()

        keep = opts.get('keep', False)
        revs = between(repo, root, topmost, keep)
        if not revs:
            raise util.Abort(_('%s is not an ancestor of working directory') %
                             node.short(root))

        ctxs = [repo[r] for r in revs]
        if not rules:
            rules = '\n'.join([makedesc(c) for c in ctxs])
            rules += '\n\n'
            rules += editcomment % (node.short(root), node.short(topmost))
            rules = ui.edit(rules, ui.username())
            # Save edit rules in .hg/histedit-last-edit.txt in case
            # the user needs to ask for help after something
            # surprising happens.
            f = open(repo.join('histedit-last-edit.txt'), 'w')
            f.write(rules)
            f.close()
        else:
            if rules == '-':
                f = sys.stdin
            else:
                f = open(rules)
            rules = f.read()
            f.close()
        rules = [l for l in (r.strip() for r in rules.splitlines())
                 if l and not l[0] == '#']
        rules = verifyrules(rules, repo, ctxs)

        parentctx = repo[root].parents()[0]
        keep = opts.get('keep', False)
        replacements = []


    while rules:
        writestate(repo, parentctx.node(), rules, keep, topmost, replacements)
        action, ha = rules.pop(0)
        ui.debug('histedit: processing %s %s\n' % (action, ha))
        actfunc = actiontable[action]
        parentctx, replacement_ = actfunc(ui, repo, parentctx, ha, opts)
        replacements.extend(replacement_)

    hg.update(repo, parentctx.node())

    mapping, tmpnodes, created, ntm = processreplacement(repo, replacements)
    if mapping:
        for prec, succs in mapping.iteritems():
            if not succs:
                ui.debug('histedit: %s is dropped\n' % node.short(prec))
            else:
                ui.debug('histedit: %s is replaced by %s\n' % (
                    node.short(prec), node.short(succs[0])))
                if len(succs) > 1:
                    m = 'histedit:                            %s'
                    for n in succs[1:]:
                        ui.debug(m % node.short(n))

    if not keep:
        if mapping:
            movebookmarks(ui, repo, mapping, topmost, ntm)
            # TODO update mq state
        if obsolete._enabled:
            markers = []
            # sort by revision number because it sound "right"
            for prec in sorted(mapping, key=repo.changelog.rev):
                succs = mapping[prec]
                markers.append((repo[prec],
                                tuple(repo[s] for s in succs)))
            if markers:
                obsolete.createmarkers(repo, markers)
        else:
            cleanupnode(ui, repo, 'replaced', mapping)

    cleanupnode(ui, repo, 'temp', tmpnodes)
    os.unlink(os.path.join(repo.path, 'histedit-state'))
    if os.path.exists(repo.sjoin('undo')):
        os.unlink(repo.sjoin('undo'))


def bootstrapcontinue(ui, repo, parentctx, rules, opts):
    action, currentnode = rules.pop(0)
    ctx = repo[currentnode]
    # is there any new commit between the expected parent and "."
    #
    # note: does not take non linear new change in account (but previous
    #       implementation didn't used them anyway (issue3655)
    newchildren = [c.node() for c in repo.set('(%d::.)', parentctx)]
    if parentctx.node() != node.nullid:
        if not newchildren:
            # `parentctxnode` should match but no result. This means that
            # currentnode is not a descendant from parentctxnode.
            msg = _('%s is not an ancestor of working directory')
            hint = _('update to %s or descendant and run "hg histedit '
                     '--continue" again') % parentctx
            raise util.Abort(msg % parentctx, hint=hint)
        newchildren.pop(0)  # remove parentctxnode
    # Commit dirty working directory if necessary
    new = None
    m, a, r, d = repo.status()[:4]
    if m or a or r or d:
        # prepare the message for the commit to comes
        if action in ('f', 'fold'):
            message = 'fold-temp-revision %s' % currentnode
        else:
            message = ctx.description() + '\n'
        if action in ('e', 'edit', 'm', 'mess'):
            editor = cmdutil.commitforceeditor
        else:
            editor = False
        commit = commitfuncfor(repo, ctx)
        new = commit(text=message, user=ctx.user(),
                     date=ctx.date(), extra=ctx.extra(),
                     editor=editor)
        if new is not None:
            newchildren.append(new)

    replacements = []
    # track replacements
    if ctx.node() not in newchildren:
        # note: new children may be empty when the changeset is dropped.
        # this happen e.g during conflicting pick where we revert content
        # to parent.
        replacements.append((ctx.node(), tuple(newchildren)))

    if action in ('f', 'fold'):
        if newchildren:
            # finalize fold operation if applicable
            if new is None:
                new = newchildren[-1]
            else:
                newchildren.pop()  # remove new from internal changes
            parentctx, repl = finishfold(ui, repo, parentctx, ctx, new, opts,
                                         newchildren)
            replacements.extend(repl)
        else:
            # newchildren is empty if the fold did not result in any commit
            # this happen when all folded change are discarded during the
            # merge.
            replacements.append((ctx.node(), (parentctx.node(),)))
    elif newchildren:
        # otherwise update "parentctx" before proceeding to further operation
        parentctx = repo[newchildren[-1]]
    return parentctx, replacements


def between(repo, old, new, keep):
    """select and validate the set of revision to edit

    When keep is false, the specified set can't have children."""
    ctxs = list(repo.set('%n::%n', old, new))
    if ctxs and not keep:
        if (not obsolete._enabled and
            repo.revs('(%ld::) - (%ld)', ctxs, ctxs)):
            raise util.Abort(_('cannot edit history that would orphan nodes'))
        root = ctxs[0] # list is already sorted by repo.set
        if not root.phase():
            raise util.Abort(_('cannot edit immutable changeset: %s') % root)
    return [c.node() for c in ctxs]


def writestate(repo, parentnode, rules, keep, topmost, replacements):
    fp = open(os.path.join(repo.path, 'histedit-state'), 'w')
    pickle.dump((parentnode, rules, keep, topmost, replacements), fp)
    fp.close()

def readstate(repo):
    """Returns a tuple of (parentnode, rules, keep, topmost, replacements).
    """
    fp = open(os.path.join(repo.path, 'histedit-state'))
    return pickle.load(fp)


def makedesc(c):
    """build a initial action line for a ctx `c`

    line are in the form:

      pick <hash> <rev> <summary>
    """
    summary = ''
    if c.description():
        summary = c.description().splitlines()[0]
    line = 'pick %s %d %s' % (c, c.rev(), summary)
    return line[:80]  # trim to 80 chars so it's not stupidly wide in my editor

def verifyrules(rules, repo, ctxs):
    """Verify that there exists exactly one edit rule per given changeset.

    Will abort if there are to many or too few rules, a malformed rule,
    or a rule on a changeset outside of the user-given range.
    """
    parsed = []
    expected = set(str(c) for c in ctxs)
    seen = set()
    for r in rules:
        if ' ' not in r:
            raise util.Abort(_('malformed line "%s"') % r)
        action, rest = r.split(' ', 1)
        ha = rest.strip().split(' ', 1)[0]
        try:
            ha = str(repo[ha])  # ensure its a short hash
        except error.RepoError:
            raise util.Abort(_('unknown changeset %s listed') % ha)
        if ha not in expected:
            raise util.Abort(
                _('may not use changesets other than the ones listed'))
        if ha in seen:
            raise util.Abort(_('duplicated command for changeset %s') % ha)
        seen.add(ha)
        if action not in actiontable:
            raise util.Abort(_('unknown action "%s"') % action)
        parsed.append([action, ha])
    missing = sorted(expected - seen)  # sort to stabilize output
    if missing:
        raise util.Abort(_('missing rules for changeset %s') % missing[0],
                         hint=_('do you want to use the drop action?'))
    return parsed

def processreplacement(repo, replacements):
    """process the list of replacements to return

    1) the final mapping between original and created nodes
    2) the list of temporary node created by histedit
    3) the list of new commit created by histedit"""
    allsuccs = set()
    replaced = set()
    fullmapping = {}
    # initialise basic set
    # fullmapping record all operation recorded in replacement
    for rep in replacements:
        allsuccs.update(rep[1])
        replaced.add(rep[0])
        fullmapping.setdefault(rep[0], set()).update(rep[1])
    new = allsuccs - replaced
    tmpnodes = allsuccs & replaced
    # Reduce content fullmapping  into direct relation between original nodes
    # and final node created during history edition
    # Dropped changeset are replaced by an empty list
    toproceed = set(fullmapping)
    final = {}
    while toproceed:
        for x in list(toproceed):
            succs = fullmapping[x]
            for s in list(succs):
                if s in toproceed:
                    # non final node with unknown closure
                    # We can't process this now
                    break
                elif s in final:
                    # non final node, replace with closure
                    succs.remove(s)
                    succs.update(final[s])
            else:
                final[x] = succs
                toproceed.remove(x)
    # remove tmpnodes from final mapping
    for n in tmpnodes:
        del final[n]
    # we expect all changes involved in final to exist in the repo
    # turn `final` into list (topologically sorted)
    nm = repo.changelog.nodemap
    for prec, succs in final.items():
        final[prec] = sorted(succs, key=nm.get)

    # computed topmost element (necessary for bookmark)
    if new:
        newtopmost = sorted(new, key=repo.changelog.rev)[-1]
    elif not final:
        # Nothing rewritten at all. we won't need `newtopmost`
        # It is the same as `oldtopmost` and `processreplacement` know it
        newtopmost = None
    else:
        # every body died. The newtopmost is the parent of the root.
        newtopmost = repo[sorted(final, key=repo.changelog.rev)[0]].p1().node()

    return final, tmpnodes, new, newtopmost

def movebookmarks(ui, repo, mapping, oldtopmost, newtopmost):
    """Move bookmark from old to newly created node"""
    if not mapping:
        # if nothing got rewritten there is not purpose for this function
        return
    moves = []
    for bk, old in sorted(repo._bookmarks.iteritems()):
        if old == oldtopmost:
            # special case ensure bookmark stay on tip.
            #
            # This is arguably a feature and we may only want that for the
            # active bookmark. But the behavior is kept compatible with the old
            # version for now.
            moves.append((bk, newtopmost))
            continue
        base = old
        new = mapping.get(base, None)
        if new is None:
            continue
        while not new:
            # base is killed, trying with parent
            base = repo[base].p1().node()
            new = mapping.get(base, (base,))
            # nothing to move
        moves.append((bk, new[-1]))
    if moves:
        marks = repo._bookmarks
        for mark, new in moves:
            old = marks[mark]
            ui.note(_('histedit: moving bookmarks %s from %s to %s\n')
                    % (mark, node.short(old), node.short(new)))
            marks[mark] = new
        marks.write()

def cleanupnode(ui, repo, name, nodes):
    """strip a group of nodes from the repository

    The set of node to strip may contains unknown nodes."""
    ui.debug('should strip %s nodes %s\n' %
             (name, ', '.join([node.short(n) for n in nodes])))
    lock = None
    try:
        lock = repo.lock()
        # Find all node that need to be stripped
        # (we hg %lr instead of %ln to silently ignore unknown item
        nm = repo.changelog.nodemap
        nodes = [n for n in nodes if n in nm]
        roots = [c.node() for c in repo.set("roots(%ln)", nodes)]
        for c in roots:
            # We should process node in reverse order to strip tip most first.
            # but this trigger a bug in changegroup hook.
            # This would reduce bundle overhead
            repair.strip(ui, repo, c)
    finally:
        lockmod.release(lock)
