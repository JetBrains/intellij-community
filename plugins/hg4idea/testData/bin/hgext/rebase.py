# rebase.py - rebasing feature for mercurial
#
# Copyright 2008 Stefano Tortarolo <stefano.tortarolo at gmail dot com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''command to move sets of revisions to a different ancestor

This extension lets you rebase changesets in an existing Mercurial
repository.

For more information:
http://mercurial.selenic.com/wiki/RebaseExtension
'''

from mercurial import hg, util, repair, merge, cmdutil, commands, bookmarks
from mercurial import extensions, patch, scmutil, phases, obsolete, error
from mercurial.commands import templateopts
from mercurial.node import nullrev
from mercurial.lock import release
from mercurial.i18n import _
import os, errno

nullmerge = -2
revignored = -3

cmdtable = {}
command = cmdutil.command(cmdtable)
testedwith = 'internal'

@command('rebase',
    [('s', 'source', '',
     _('rebase from the specified changeset'), _('REV')),
    ('b', 'base', '',
     _('rebase from the base of the specified changeset '
       '(up to greatest common ancestor of base and dest)'),
     _('REV')),
    ('r', 'rev', [],
     _('rebase these revisions'),
     _('REV')),
    ('d', 'dest', '',
     _('rebase onto the specified changeset'), _('REV')),
    ('', 'collapse', False, _('collapse the rebased changesets')),
    ('m', 'message', '',
     _('use text as collapse commit message'), _('TEXT')),
    ('e', 'edit', False, _('invoke editor on commit messages')),
    ('l', 'logfile', '',
     _('read collapse commit message from file'), _('FILE')),
    ('', 'keep', False, _('keep original changesets')),
    ('', 'keepbranches', False, _('keep original branch names')),
    ('D', 'detach', False, _('(DEPRECATED)')),
    ('t', 'tool', '', _('specify merge tool')),
    ('c', 'continue', False, _('continue an interrupted rebase')),
    ('a', 'abort', False, _('abort an interrupted rebase'))] +
     templateopts,
    _('[-s REV | -b REV] [-d REV] [OPTION]'))
def rebase(ui, repo, **opts):
    """move changeset (and descendants) to a different branch

    Rebase uses repeated merging to graft changesets from one part of
    history (the source) onto another (the destination). This can be
    useful for linearizing *local* changes relative to a master
    development tree.

    You should not rebase changesets that have already been shared
    with others. Doing so will force everybody else to perform the
    same rebase or they will end up with duplicated changesets after
    pulling in your rebased changesets.

    In its default configuration, Mercurial will prevent you from
    rebasing published changes. See :hg:`help phases` for details.

    If you don't specify a destination changeset (``-d/--dest``),
    rebase uses the tipmost head of the current named branch as the
    destination. (The destination changeset is not modified by
    rebasing, but new changesets are added as its descendants.)

    You can specify which changesets to rebase in two ways: as a
    "source" changeset or as a "base" changeset. Both are shorthand
    for a topologically related set of changesets (the "source
    branch"). If you specify source (``-s/--source``), rebase will
    rebase that changeset and all of its descendants onto dest. If you
    specify base (``-b/--base``), rebase will select ancestors of base
    back to but not including the common ancestor with dest. Thus,
    ``-b`` is less precise but more convenient than ``-s``: you can
    specify any changeset in the source branch, and rebase will select
    the whole branch. If you specify neither ``-s`` nor ``-b``, rebase
    uses the parent of the working directory as the base.

    For advanced usage, a third way is available through the ``--rev``
    option. It allows you to specify an arbitrary set of changesets to
    rebase. Descendants of revs you specify with this option are not
    automatically included in the rebase.

    By default, rebase recreates the changesets in the source branch
    as descendants of dest and then destroys the originals. Use
    ``--keep`` to preserve the original source changesets. Some
    changesets in the source branch (e.g. merges from the destination
    branch) may be dropped if they no longer contribute any change.

    One result of the rules for selecting the destination changeset
    and source branch is that, unlike ``merge``, rebase will do
    nothing if you are at the latest (tipmost) head of a named branch
    with two heads. You need to explicitly specify source and/or
    destination (or ``update`` to the other head, if it's the head of
    the intended source branch).

    If a rebase is interrupted to manually resolve a merge, it can be
    continued with --continue/-c or aborted with --abort/-a.

    Returns 0 on success, 1 if nothing to rebase.
    """
    originalwd = target = None
    activebookmark = None
    external = nullrev
    state = {}
    skipped = set()
    targetancestors = set()

    editor = None
    if opts.get('edit'):
        editor = cmdutil.commitforceeditor

    lock = wlock = None
    try:
        wlock = repo.wlock()
        lock = repo.lock()

        # Validate input and define rebasing points
        destf = opts.get('dest', None)
        srcf = opts.get('source', None)
        basef = opts.get('base', None)
        revf = opts.get('rev', [])
        contf = opts.get('continue')
        abortf = opts.get('abort')
        collapsef = opts.get('collapse', False)
        collapsemsg = cmdutil.logmessage(ui, opts)
        extrafn = opts.get('extrafn') # internal, used by e.g. hgsubversion
        keepf = opts.get('keep', False)
        keepbranchesf = opts.get('keepbranches', False)
        # keepopen is not meant for use on the command line, but by
        # other extensions
        keepopen = opts.get('keepopen', False)

        if collapsemsg and not collapsef:
            raise util.Abort(
                _('message can only be specified with collapse'))

        if contf or abortf:
            if contf and abortf:
                raise util.Abort(_('cannot use both abort and continue'))
            if collapsef:
                raise util.Abort(
                    _('cannot use collapse with continue or abort'))
            if srcf or basef or destf:
                raise util.Abort(
                    _('abort and continue do not allow specifying revisions'))
            if opts.get('tool', False):
                ui.warn(_('tool option will be ignored\n'))

            (originalwd, target, state, skipped, collapsef, keepf,
                keepbranchesf, external, activebookmark) = restorestatus(repo)
            if abortf:
                return abort(repo, originalwd, target, state)
        else:
            if srcf and basef:
                raise util.Abort(_('cannot specify both a '
                                   'source and a base'))
            if revf and basef:
                raise util.Abort(_('cannot specify both a '
                                   'revision and a base'))
            if revf and srcf:
                raise util.Abort(_('cannot specify both a '
                                   'revision and a source'))

            cmdutil.bailifchanged(repo)

            if not destf:
                # Destination defaults to the latest revision in the
                # current branch
                branch = repo[None].branch()
                dest = repo[branch]
            else:
                dest = scmutil.revsingle(repo, destf)

            if revf:
                rebaseset = repo.revs('%lr', revf)
            elif srcf:
                src = scmutil.revrange(repo, [srcf])
                rebaseset = repo.revs('(%ld)::', src)
            else:
                base = scmutil.revrange(repo, [basef or '.'])
                rebaseset = repo.revs(
                    '(children(ancestor(%ld, %d)) and ::(%ld))::',
                    base, dest, base)
            if rebaseset:
                root = min(rebaseset)
            else:
                root = None

            if not rebaseset:
                repo.ui.debug('base is ancestor of destination\n')
                result = None
            elif (not (keepf or obsolete._enabled)
                  and repo.revs('first(children(%ld) - %ld)',
                                rebaseset, rebaseset)):
                raise util.Abort(
                    _("can't remove original changesets with"
                      " unrebased descendants"),
                    hint=_('use --keep to keep original changesets'))
            else:
                result = buildstate(repo, dest, rebaseset, collapsef)

            if not result:
                # Empty state built, nothing to rebase
                ui.status(_('nothing to rebase\n'))
                return 1
            elif not keepf and not repo[root].mutable():
                raise util.Abort(_("can't rebase immutable changeset %s")
                                 % repo[root],
                                 hint=_('see hg help phases for details'))
            else:
                originalwd, target, state = result
                if collapsef:
                    targetancestors = repo.changelog.ancestors([target],
                                                               inclusive=True)
                    external = checkexternal(repo, state, targetancestors)

        if keepbranchesf:
            assert not extrafn, 'cannot use both keepbranches and extrafn'
            def extrafn(ctx, extra):
                extra['branch'] = ctx.branch()
            if collapsef:
                branches = set()
                for rev in state:
                    branches.add(repo[rev].branch())
                    if len(branches) > 1:
                        raise util.Abort(_('cannot collapse multiple named '
                            'branches'))


        # Rebase
        if not targetancestors:
            targetancestors = repo.changelog.ancestors([target], inclusive=True)

        # Keep track of the current bookmarks in order to reset them later
        currentbookmarks = repo._bookmarks.copy()
        activebookmark = activebookmark or repo._bookmarkcurrent
        if activebookmark:
            bookmarks.unsetcurrent(repo)

        sortedstate = sorted(state)
        total = len(sortedstate)
        pos = 0
        for rev in sortedstate:
            pos += 1
            if state[rev] == -1:
                ui.progress(_("rebasing"), pos, ("%d:%s" % (rev, repo[rev])),
                            _('changesets'), total)
                storestatus(repo, originalwd, target, state, collapsef, keepf,
                            keepbranchesf, external, activebookmark)
                p1, p2 = defineparents(repo, rev, target, state,
                                                        targetancestors)
                if len(repo.parents()) == 2:
                    repo.ui.debug('resuming interrupted rebase\n')
                else:
                    try:
                        ui.setconfig('ui', 'forcemerge', opts.get('tool', ''))
                        stats = rebasenode(repo, rev, p1, state, collapsef)
                        if stats and stats[3] > 0:
                            raise error.InterventionRequired(
                                _('unresolved conflicts (see hg '
                                  'resolve, then hg rebase --continue)'))
                    finally:
                        ui.setconfig('ui', 'forcemerge', '')
                cmdutil.duplicatecopies(repo, rev, target)
                if not collapsef:
                    newrev = concludenode(repo, rev, p1, p2, extrafn=extrafn,
                                          editor=editor)
                else:
                    # Skip commit if we are collapsing
                    repo.setparents(repo[p1].node())
                    newrev = None
                # Update the state
                if newrev is not None:
                    state[rev] = repo[newrev].rev()
                else:
                    if not collapsef:
                        ui.note(_('no changes, revision %d skipped\n') % rev)
                        ui.debug('next revision set to %s\n' % p1)
                        skipped.add(rev)
                    state[rev] = p1

        ui.progress(_('rebasing'), None)
        ui.note(_('rebase merging completed\n'))

        if collapsef and not keepopen:
            p1, p2 = defineparents(repo, min(state), target,
                                                        state, targetancestors)
            if collapsemsg:
                commitmsg = collapsemsg
            else:
                commitmsg = 'Collapsed revision'
                for rebased in state:
                    if rebased not in skipped and state[rebased] > nullmerge:
                        commitmsg += '\n* %s' % repo[rebased].description()
                commitmsg = ui.edit(commitmsg, repo.ui.username())
            newrev = concludenode(repo, rev, p1, external, commitmsg=commitmsg,
                                  extrafn=extrafn, editor=editor)

        if 'qtip' in repo.tags():
            updatemq(repo, state, skipped, **opts)

        if currentbookmarks:
            # Nodeids are needed to reset bookmarks
            nstate = {}
            for k, v in state.iteritems():
                if v > nullmerge:
                    nstate[repo[k].node()] = repo[v].node()
            # XXX this is the same as dest.node() for the non-continue path --
            # this should probably be cleaned up
            targetnode = repo[target].node()

        if not keepf:
            collapsedas = None
            if collapsef:
                collapsedas = newrev
            clearrebased(ui, repo, state, skipped, collapsedas)

        if currentbookmarks:
            updatebookmarks(repo, targetnode, nstate, currentbookmarks)

        clearstatus(repo)
        ui.note(_("rebase completed\n"))
        util.unlinkpath(repo.sjoin('undo'), ignoremissing=True)
        if skipped:
            ui.note(_("%d revisions have been skipped\n") % len(skipped))

        if (activebookmark and
            repo['tip'].node() == repo._bookmarks[activebookmark]):
                bookmarks.setcurrent(repo, activebookmark)

    finally:
        release(lock, wlock)

def checkexternal(repo, state, targetancestors):
    """Check whether one or more external revisions need to be taken in
    consideration. In the latter case, abort.
    """
    external = nullrev
    source = min(state)
    for rev in state:
        if rev == source:
            continue
        # Check externals and fail if there are more than one
        for p in repo[rev].parents():
            if (p.rev() not in state
                        and p.rev() not in targetancestors):
                if external != nullrev:
                    raise util.Abort(_('unable to collapse, there is more '
                            'than one external parent'))
                external = p.rev()
    return external

def concludenode(repo, rev, p1, p2, commitmsg=None, editor=None, extrafn=None):
    'Commit the changes and store useful information in extra'
    try:
        repo.setparents(repo[p1].node(), repo[p2].node())
        ctx = repo[rev]
        if commitmsg is None:
            commitmsg = ctx.description()
        extra = {'rebase_source': ctx.hex()}
        if extrafn:
            extrafn(ctx, extra)
        # Commit might fail if unresolved files exist
        newrev = repo.commit(text=commitmsg, user=ctx.user(),
                             date=ctx.date(), extra=extra, editor=editor)
        repo.dirstate.setbranch(repo[newrev].branch())
        targetphase = max(ctx.phase(), phases.draft)
        # retractboundary doesn't overwrite upper phase inherited from parent
        newnode = repo[newrev].node()
        if newnode:
            phases.retractboundary(repo, targetphase, [newnode])
        return newrev
    except util.Abort:
        # Invalidate the previous setparents
        repo.dirstate.invalidate()
        raise

def rebasenode(repo, rev, p1, state, collapse):
    'Rebase a single revision'
    # Merge phase
    # Update to target and merge it with local
    if repo['.'].rev() != repo[p1].rev():
        repo.ui.debug(" update to %d:%s\n" % (repo[p1].rev(), repo[p1]))
        merge.update(repo, p1, False, True, False)
    else:
        repo.ui.debug(" already in target\n")
    repo.dirstate.write()
    repo.ui.debug(" merge against %d:%s\n" % (repo[rev].rev(), repo[rev]))
    base = None
    if repo[rev].rev() != repo[min(state)].rev():
        base = repo[rev].p1().node()
    # When collapsing in-place, the parent is the common ancestor, we
    # have to allow merging with it.
    return merge.update(repo, rev, True, True, False, base, collapse)

def nearestrebased(repo, rev, state):
    """return the nearest ancestors of rev in the rebase result"""
    rebased = [r for r in state if state[r] > nullmerge]
    candidates = repo.revs('max(%ld  and (::%d))', rebased, rev)
    if candidates:
        return state[candidates[0]]
    else:
        return None

def defineparents(repo, rev, target, state, targetancestors):
    'Return the new parent relationship of the revision that will be rebased'
    parents = repo[rev].parents()
    p1 = p2 = nullrev

    P1n = parents[0].rev()
    if P1n in targetancestors:
        p1 = target
    elif P1n in state:
        if state[P1n] == nullmerge:
            p1 = target
        elif state[P1n] == revignored:
            p1 = nearestrebased(repo, P1n, state)
            if p1 is None:
                p1 = target
        else:
            p1 = state[P1n]
    else: # P1n external
        p1 = target
        p2 = P1n

    if len(parents) == 2 and parents[1].rev() not in targetancestors:
        P2n = parents[1].rev()
        # interesting second parent
        if P2n in state:
            if p1 == target: # P1n in targetancestors or external
                p1 = state[P2n]
            elif state[P2n] == revignored:
                p2 = nearestrebased(repo, P2n, state)
                if p2 is None:
                    # no ancestors rebased yet, detach
                    p2 = target
            else:
                p2 = state[P2n]
        else: # P2n external
            if p2 != nullrev: # P1n external too => rev is a merged revision
                raise util.Abort(_('cannot use revision %d as base, result '
                        'would have 3 parents') % rev)
            p2 = P2n
    repo.ui.debug(" future parents are %d and %d\n" %
                            (repo[p1].rev(), repo[p2].rev()))
    return p1, p2

def isagitpatch(repo, patchname):
    'Return true if the given patch is in git format'
    mqpatch = os.path.join(repo.mq.path, patchname)
    for line in patch.linereader(file(mqpatch, 'rb')):
        if line.startswith('diff --git'):
            return True
    return False

def updatemq(repo, state, skipped, **opts):
    'Update rebased mq patches - finalize and then import them'
    mqrebase = {}
    mq = repo.mq
    original_series = mq.fullseries[:]
    skippedpatches = set()

    for p in mq.applied:
        rev = repo[p.node].rev()
        if rev in state:
            repo.ui.debug('revision %d is an mq patch (%s), finalize it.\n' %
                                        (rev, p.name))
            mqrebase[rev] = (p.name, isagitpatch(repo, p.name))
        else:
            # Applied but not rebased, not sure this should happen
            skippedpatches.add(p.name)

    if mqrebase:
        mq.finish(repo, mqrebase.keys())

        # We must start import from the newest revision
        for rev in sorted(mqrebase, reverse=True):
            if rev not in skipped:
                name, isgit = mqrebase[rev]
                repo.ui.debug('import mq patch %d (%s)\n' % (state[rev], name))
                mq.qimport(repo, (), patchname=name, git=isgit,
                                rev=[str(state[rev])])
            else:
                # Rebased and skipped
                skippedpatches.add(mqrebase[rev][0])

        # Patches were either applied and rebased and imported in
        # order, applied and removed or unapplied. Discard the removed
        # ones while preserving the original series order and guards.
        newseries = [s for s in original_series
                     if mq.guard_re.split(s, 1)[0] not in skippedpatches]
        mq.fullseries[:] = newseries
        mq.seriesdirty = True
        mq.savedirty()

def updatebookmarks(repo, targetnode, nstate, originalbookmarks):
    'Move bookmarks to their correct changesets, and delete divergent ones'
    marks = repo._bookmarks
    for k, v in originalbookmarks.iteritems():
        if v in nstate:
            # update the bookmarks for revs that have moved
            marks[k] = nstate[v]
            bookmarks.deletedivergent(repo, [targetnode], k)

    marks.write()

def storestatus(repo, originalwd, target, state, collapse, keep, keepbranches,
                external, activebookmark):
    'Store the current status to allow recovery'
    f = repo.opener("rebasestate", "w")
    f.write(repo[originalwd].hex() + '\n')
    f.write(repo[target].hex() + '\n')
    f.write(repo[external].hex() + '\n')
    f.write('%d\n' % int(collapse))
    f.write('%d\n' % int(keep))
    f.write('%d\n' % int(keepbranches))
    f.write('%s\n' % (activebookmark or ''))
    for d, v in state.iteritems():
        oldrev = repo[d].hex()
        if v > nullmerge:
            newrev = repo[v].hex()
        else:
            newrev = v
        f.write("%s:%s\n" % (oldrev, newrev))
    f.close()
    repo.ui.debug('rebase status stored\n')

def clearstatus(repo):
    'Remove the status files'
    util.unlinkpath(repo.join("rebasestate"), ignoremissing=True)

def restorestatus(repo):
    'Restore a previously stored status'
    try:
        target = None
        collapse = False
        external = nullrev
        activebookmark = None
        state = {}
        f = repo.opener("rebasestate")
        for i, l in enumerate(f.read().splitlines()):
            if i == 0:
                originalwd = repo[l].rev()
            elif i == 1:
                target = repo[l].rev()
            elif i == 2:
                external = repo[l].rev()
            elif i == 3:
                collapse = bool(int(l))
            elif i == 4:
                keep = bool(int(l))
            elif i == 5:
                keepbranches = bool(int(l))
            elif i == 6 and not (len(l) == 81 and ':' in l):
                # line 6 is a recent addition, so for backwards compatibility
                # check that the line doesn't look like the oldrev:newrev lines
                activebookmark = l
            else:
                oldrev, newrev = l.split(':')
                if newrev in (str(nullmerge), str(revignored)):
                    state[repo[oldrev].rev()] = int(newrev)
                else:
                    state[repo[oldrev].rev()] = repo[newrev].rev()
        skipped = set()
        # recompute the set of skipped revs
        if not collapse:
            seen = set([target])
            for old, new in sorted(state.items()):
                if new != nullrev and new in seen:
                    skipped.add(old)
                seen.add(new)
        repo.ui.debug('computed skipped revs: %s\n' % skipped)
        repo.ui.debug('rebase status resumed\n')
        return (originalwd, target, state, skipped,
                collapse, keep, keepbranches, external, activebookmark)
    except IOError, err:
        if err.errno != errno.ENOENT:
            raise
        raise util.Abort(_('no rebase in progress'))

def abort(repo, originalwd, target, state):
    'Restore the repository to its original state'
    dstates = [s for s in state.values() if s != nullrev]
    immutable = [d for d in dstates if not repo[d].mutable()]
    if immutable:
        raise util.Abort(_("can't abort rebase due to immutable changesets %s")
                         % ', '.join(str(repo[r]) for r in immutable),
                         hint=_('see hg help phases for details'))

    descendants = set()
    if dstates:
        descendants = set(repo.changelog.descendants(dstates))
    if descendants - set(dstates):
        repo.ui.warn(_("warning: new changesets detected on target branch, "
                       "can't abort\n"))
        return -1
    else:
        # Strip from the first rebased revision
        merge.update(repo, repo[originalwd].rev(), False, True, False)
        rebased = filter(lambda x: x > -1 and x != target, state.values())
        if rebased:
            strippoints = [c.node()  for c in repo.set('roots(%ld)', rebased)]
            # no backup of rebased cset versions needed
            repair.strip(repo.ui, repo, strippoints)
        clearstatus(repo)
        repo.ui.warn(_('rebase aborted\n'))
        return 0

def buildstate(repo, dest, rebaseset, collapse):
    '''Define which revisions are going to be rebased and where

    repo: repo
    dest: context
    rebaseset: set of rev
    '''

    # This check isn't strictly necessary, since mq detects commits over an
    # applied patch. But it prevents messing up the working directory when
    # a partially completed rebase is blocked by mq.
    if 'qtip' in repo.tags() and (dest.node() in
                            [s.node for s in repo.mq.applied]):
        raise util.Abort(_('cannot rebase onto an applied mq patch'))

    roots = list(repo.set('roots(%ld)', rebaseset))
    if not roots:
        raise util.Abort(_('no matching revisions'))
    roots.sort()
    state = {}
    detachset = set()
    for root in roots:
        commonbase = root.ancestor(dest)
        if commonbase == root:
            raise util.Abort(_('source is ancestor of destination'))
        if commonbase == dest:
            samebranch = root.branch() == dest.branch()
            if not collapse and samebranch and root in dest.children():
                repo.ui.debug('source is a child of destination\n')
                return None

        repo.ui.debug('rebase onto %d starting from %s\n' % (dest, roots))
        state.update(dict.fromkeys(rebaseset, nullrev))
        # Rebase tries to turn <dest> into a parent of <root> while
        # preserving the number of parents of rebased changesets:
        #
        # - A changeset with a single parent will always be rebased as a
        #   changeset with a single parent.
        #
        # - A merge will be rebased as merge unless its parents are both
        #   ancestors of <dest> or are themselves in the rebased set and
        #   pruned while rebased.
        #
        # If one parent of <root> is an ancestor of <dest>, the rebased
        # version of this parent will be <dest>. This is always true with
        # --base option.
        #
        # Otherwise, we need to *replace* the original parents with
        # <dest>. This "detaches" the rebased set from its former location
        # and rebases it onto <dest>. Changes introduced by ancestors of
        # <root> not common with <dest> (the detachset, marked as
        # nullmerge) are "removed" from the rebased changesets.
        #
        # - If <root> has a single parent, set it to <dest>.
        #
        # - If <root> is a merge, we cannot decide which parent to
        #   replace, the rebase operation is not clearly defined.
        #
        # The table below sums up this behavior:
        #
        # +------------------+----------------------+-------------------------+
        # |                  |     one parent       |  merge                  |
        # +------------------+----------------------+-------------------------+
        # | parent in        | new parent is <dest> | parents in ::<dest> are |
        # | ::<dest>         |                      | remapped to <dest>      |
        # +------------------+----------------------+-------------------------+
        # | unrelated source | new parent is <dest> | ambiguous, abort        |
        # +------------------+----------------------+-------------------------+
        #
        # The actual abort is handled by `defineparents`
        if len(root.parents()) <= 1:
            # ancestors of <root> not ancestors of <dest>
            detachset.update(repo.changelog.findmissingrevs([commonbase.rev()],
                                                            [root.rev()]))
    for r in detachset:
        if r not in state:
            state[r] = nullmerge
    if len(roots) > 1:
        # If we have multiple roots, we may have "hole" in the rebase set.
        # Rebase roots that descend from those "hole" should not be detached as
        # other root are. We use the special `revignored` to inform rebase that
        # the revision should be ignored but that `defineparents` should search
        # a rebase destination that make sense regarding rebased topology.
        rebasedomain = set(repo.revs('%ld::%ld', rebaseset, rebaseset))
        for ignored in set(rebasedomain) - set(rebaseset):
            state[ignored] = revignored
    return repo['.'].rev(), dest.rev(), state

def clearrebased(ui, repo, state, skipped, collapsedas=None):
    """dispose of rebased revision at the end of the rebase

    If `collapsedas` is not None, the rebase was a collapse whose result if the
    `collapsedas` node."""
    if obsolete._enabled:
        markers = []
        for rev, newrev in sorted(state.items()):
            if newrev >= 0:
                if rev in skipped:
                    succs = ()
                elif collapsedas is not None:
                    succs = (repo[collapsedas],)
                else:
                    succs = (repo[newrev],)
                markers.append((repo[rev], succs))
        if markers:
            obsolete.createmarkers(repo, markers)
    else:
        rebased = [rev for rev in state if state[rev] > nullmerge]
        if rebased:
            stripped = []
            for root in repo.set('roots(%ld)', rebased):
                if set(repo.changelog.descendants([root.rev()])) - set(state):
                    ui.warn(_("warning: new changesets detected "
                              "on source branch, not stripping\n"))
                else:
                    stripped.append(root.node())
            if stripped:
                # backup the old csets by default
                repair.strip(ui, repo, stripped, "all")


def pullrebase(orig, ui, repo, *args, **opts):
    'Call rebase after pull if the latter has been invoked with --rebase'
    if opts.get('rebase'):
        if opts.get('update'):
            del opts['update']
            ui.debug('--update and --rebase are not compatible, ignoring '
                     'the update flag\n')

        movemarkfrom = repo['.'].node()
        cmdutil.bailifchanged(repo)
        revsprepull = len(repo)
        origpostincoming = commands.postincoming
        def _dummy(*args, **kwargs):
            pass
        commands.postincoming = _dummy
        try:
            orig(ui, repo, *args, **opts)
        finally:
            commands.postincoming = origpostincoming
        revspostpull = len(repo)
        if revspostpull > revsprepull:
            # --rev option from pull conflict with rebase own --rev
            # dropping it
            if 'rev' in opts:
                del opts['rev']
            rebase(ui, repo, **opts)
            branch = repo[None].branch()
            dest = repo[branch].rev()
            if dest != repo['.'].rev():
                # there was nothing to rebase we force an update
                hg.update(repo, dest)
                if bookmarks.update(repo, [movemarkfrom], repo['.'].node()):
                    ui.status(_("updating bookmark %s\n")
                              % repo._bookmarkcurrent)
    else:
        if opts.get('tool'):
            raise util.Abort(_('--tool can only be used with --rebase'))
        orig(ui, repo, *args, **opts)

def uisetup(ui):
    'Replace pull with a decorator to provide --rebase option'
    entry = extensions.wrapcommand(commands.table, 'pull', pullrebase)
    entry[1].append(('', 'rebase', None,
                     _("rebase working directory to branch head")))
    entry[1].append(('t', 'tool', '',
                     _("specify merge tool for rebase")))
