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

from mercurial import util, repair, merge, cmdutil, commands, error
from mercurial import extensions, ancestor, copies, patch
from mercurial.commands import templateopts
from mercurial.node import nullrev
from mercurial.lock import release
from mercurial.i18n import _
import os, errno

nullmerge = -2

def rebase(ui, repo, **opts):
    """move changeset (and descendants) to a different branch

    Rebase uses repeated merging to graft changesets from one part of
    history (the source) onto another (the destination). This can be
    useful for linearizing local changes relative to a master
    development tree.

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
    """
    originalwd = target = None
    external = nullrev
    state = {}
    skipped = set()
    targetancestors = set()

    lock = wlock = None
    try:
        lock = repo.lock()
        wlock = repo.wlock()

        # Validate input and define rebasing points
        destf = opts.get('dest', None)
        srcf = opts.get('source', None)
        basef = opts.get('base', None)
        contf = opts.get('continue')
        abortf = opts.get('abort')
        collapsef = opts.get('collapse', False)
        extrafn = opts.get('extrafn')
        keepf = opts.get('keep', False)
        keepbranchesf = opts.get('keepbranches', False)
        detachf = opts.get('detach', False)

        if contf or abortf:
            if contf and abortf:
                raise error.ParseError('rebase',
                                       _('cannot use both abort and continue'))
            if collapsef:
                raise error.ParseError(
                    'rebase', _('cannot use collapse with continue or abort'))

            if detachf:
                raise error.ParseError(
                    'rebase', _('cannot use detach with continue or abort'))

            if srcf or basef or destf:
                raise error.ParseError('rebase',
                    _('abort and continue do not allow specifying revisions'))

            (originalwd, target, state, collapsef, keepf,
                                keepbranchesf, external) = restorestatus(repo)
            if abortf:
                abort(repo, originalwd, target, state)
                return
        else:
            if srcf and basef:
                raise error.ParseError('rebase', _('cannot specify both a '
                                                   'revision and a base'))
            if detachf:
                if not srcf:
                    raise error.ParseError(
                      'rebase', _('detach requires a revision to be specified'))
                if basef:
                    raise error.ParseError(
                        'rebase', _('cannot specify a base with detach'))

            cmdutil.bail_if_changed(repo)
            result = buildstate(repo, destf, srcf, basef, detachf)
            if not result:
                # Empty state built, nothing to rebase
                ui.status(_('nothing to rebase\n'))
                return
            else:
                originalwd, target, state = result
                if collapsef:
                    targetancestors = set(repo.changelog.ancestors(target))
                    external = checkexternal(repo, state, targetancestors)

        if keepbranchesf:
            if extrafn:
                raise error.ParseError(
                    'rebase', _('cannot use both keepbranches and extrafn'))
            def extrafn(ctx, extra):
                extra['branch'] = ctx.branch()

        # Rebase
        if not targetancestors:
            targetancestors = set(repo.changelog.ancestors(target))
            targetancestors.add(target)

        for rev in sorted(state):
            if state[rev] == -1:
                ui.debug("rebasing %d:%s\n" % (rev, repo[rev]))
                storestatus(repo, originalwd, target, state, collapsef, keepf,
                                                    keepbranchesf, external)
                p1, p2 = defineparents(repo, rev, target, state,
                                                        targetancestors)
                if len(repo.parents()) == 2:
                    repo.ui.debug('resuming interrupted rebase\n')
                else:
                    stats = rebasenode(repo, rev, p1, p2, state)
                    if stats and stats[3] > 0:
                        raise util.Abort(_('fix unresolved conflicts with hg '
                                    'resolve then run hg rebase --continue'))
                updatedirstate(repo, rev, target, p2)
                if not collapsef:
                    newrev = concludenode(repo, rev, p1, p2, extrafn=extrafn)
                else:
                    # Skip commit if we are collapsing
                    repo.dirstate.setparents(repo[p1].node())
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

        ui.note(_('rebase merging completed\n'))

        if collapsef:
            p1, p2 = defineparents(repo, min(state), target,
                                                        state, targetancestors)
            commitmsg = 'Collapsed revision'
            for rebased in state:
                if rebased not in skipped and state[rebased] != nullmerge:
                    commitmsg += '\n* %s' % repo[rebased].description()
            commitmsg = ui.edit(commitmsg, repo.ui.username())
            newrev = concludenode(repo, rev, p1, external, commitmsg=commitmsg,
                                  extrafn=extrafn)

        if 'qtip' in repo.tags():
            updatemq(repo, state, skipped, **opts)

        if not keepf:
            # Remove no more useful revisions
            rebased = [rev for rev in state if state[rev] != nullmerge]
            if rebased:
                if set(repo.changelog.descendants(min(rebased))) - set(state):
                    ui.warn(_("warning: new changesets detected "
                              "on source branch, not stripping\n"))
                else:
                    repair.strip(ui, repo, repo[min(rebased)].node(), "strip")

        clearstatus(repo)
        ui.status(_("rebase completed\n"))
        if os.path.exists(repo.sjoin('undo')):
            util.unlink(repo.sjoin('undo'))
        if skipped:
            ui.note(_("%d revisions have been skipped\n") % len(skipped))
    finally:
        release(lock, wlock)

def rebasemerge(repo, rev, first=False):
    'return the correct ancestor'
    oldancestor = ancestor.ancestor

    def newancestor(a, b, pfunc):
        if b == rev:
            return repo[rev].parents()[0].rev()
        return oldancestor(a, b, pfunc)

    if not first:
        ancestor.ancestor = newancestor
    else:
        repo.ui.debug("first revision, do not change ancestor\n")
    try:
        stats = merge.update(repo, rev, True, True, False)
        return stats
    finally:
        ancestor.ancestor = oldancestor

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

def updatedirstate(repo, rev, p1, p2):
    """Keep track of renamed files in the revision that is going to be rebased
    """
    # Here we simulate the copies and renames in the source changeset
    cop, diver = copies.copies(repo, repo[rev], repo[p1], repo[p2], True)
    m1 = repo[rev].manifest()
    m2 = repo[p1].manifest()
    for k, v in cop.iteritems():
        if k in m1:
            if v in m1 or v in m2:
                repo.dirstate.copy(v, k)
                if v in m2 and v not in m1:
                    repo.dirstate.remove(v)

def concludenode(repo, rev, p1, p2, commitmsg=None, extrafn=None):
    'Commit the changes and store useful information in extra'
    try:
        repo.dirstate.setparents(repo[p1].node(), repo[p2].node())
        if commitmsg is None:
            commitmsg = repo[rev].description()
        ctx = repo[rev]
        extra = {'rebase_source': ctx.hex()}
        if extrafn:
            extrafn(ctx, extra)
        # Commit might fail if unresolved files exist
        newrev = repo.commit(text=commitmsg, user=ctx.user(),
                             date=ctx.date(), extra=extra)
        repo.dirstate.setbranch(repo[newrev].branch())
        return newrev
    except util.Abort:
        # Invalidate the previous setparents
        repo.dirstate.invalidate()
        raise

def rebasenode(repo, rev, p1, p2, state):
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
    first = repo[rev].rev() == repo[min(state)].rev()
    stats = rebasemerge(repo, rev, first)
    return stats

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
    for p in repo.mq.applied:
        if repo[p.rev].rev() in state:
            repo.ui.debug('revision %d is an mq patch (%s), finalize it.\n' %
                                        (repo[p.rev].rev(), p.name))
            mqrebase[repo[p.rev].rev()] = (p.name, isagitpatch(repo, p.name))

    if mqrebase:
        repo.mq.finish(repo, mqrebase.keys())

        # We must start import from the newest revision
        for rev in sorted(mqrebase, reverse=True):
            if rev not in skipped:
                repo.ui.debug('import mq patch %d (%s)\n'
                              % (state[rev], mqrebase[rev][0]))
                repo.mq.qimport(repo, (), patchname=mqrebase[rev][0],
                            git=mqrebase[rev][1],rev=[str(state[rev])])
        repo.mq.save_dirty()

def storestatus(repo, originalwd, target, state, collapse, keep, keepbranches,
                                                                external):
    'Store the current status to allow recovery'
    f = repo.opener("rebasestate", "w")
    f.write(repo[originalwd].hex() + '\n')
    f.write(repo[target].hex() + '\n')
    f.write(repo[external].hex() + '\n')
    f.write('%d\n' % int(collapse))
    f.write('%d\n' % int(keep))
    f.write('%d\n' % int(keepbranches))
    for d, v in state.iteritems():
        oldrev = repo[d].hex()
        newrev = repo[v].hex()
        f.write("%s:%s\n" % (oldrev, newrev))
    f.close()
    repo.ui.debug('rebase status stored\n')

def clearstatus(repo):
    'Remove the status files'
    if os.path.exists(repo.join("rebasestate")):
        util.unlink(repo.join("rebasestate"))

def restorestatus(repo):
    'Restore a previously stored status'
    try:
        target = None
        collapse = False
        external = nullrev
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
            else:
                oldrev, newrev = l.split(':')
                state[repo[oldrev].rev()] = repo[newrev].rev()
        repo.ui.debug('rebase status resumed\n')
        return originalwd, target, state, collapse, keep, keepbranches, external
    except IOError, err:
        if err.errno != errno.ENOENT:
            raise
        raise util.Abort(_('no rebase in progress'))

def abort(repo, originalwd, target, state):
    'Restore the repository to its original state'
    if set(repo.changelog.descendants(target)) - set(state.values()):
        repo.ui.warn(_("warning: new changesets detected on target branch, "
                                                    "not stripping\n"))
    else:
        # Strip from the first rebased revision
        merge.update(repo, repo[originalwd].rev(), False, True, False)
        rebased = filter(lambda x: x > -1, state.values())
        if rebased:
            strippoint = min(rebased)
            repair.strip(repo.ui, repo, repo[strippoint].node(), "strip")
        clearstatus(repo)
        repo.ui.status(_('rebase aborted\n'))

def buildstate(repo, dest, src, base, detach):
    'Define which revisions are going to be rebased and where'
    targetancestors = set()
    detachset = set()

    if not dest:
        # Destination defaults to the latest revision in the current branch
        branch = repo[None].branch()
        dest = repo[branch].rev()
    else:
        dest = repo[dest].rev()

    # This check isn't strictly necessary, since mq detects commits over an
    # applied patch. But it prevents messing up the working directory when
    # a partially completed rebase is blocked by mq.
    if 'qtip' in repo.tags() and (repo[dest].hex() in
                            [s.rev for s in repo.mq.applied]):
        raise util.Abort(_('cannot rebase onto an applied mq patch'))

    if src:
        commonbase = repo[src].ancestor(repo[dest])
        if commonbase == repo[src]:
            raise util.Abort(_('source is ancestor of destination'))
        if commonbase == repo[dest]:
            raise util.Abort(_('source is descendant of destination'))
        source = repo[src].rev()
        if detach:
            # We need to keep track of source's ancestors up to the common base
            srcancestors = set(repo.changelog.ancestors(source))
            baseancestors = set(repo.changelog.ancestors(commonbase.rev()))
            detachset = srcancestors - baseancestors
            detachset.remove(commonbase.rev())
    else:
        if base:
            cwd = repo[base].rev()
        else:
            cwd = repo['.'].rev()

        if cwd == dest:
            repo.ui.debug('source and destination are the same\n')
            return None

        targetancestors = set(repo.changelog.ancestors(dest))
        if cwd in targetancestors:
            repo.ui.debug('source is ancestor of destination\n')
            return None

        cwdancestors = set(repo.changelog.ancestors(cwd))
        if dest in cwdancestors:
            repo.ui.debug('source is descendant of destination\n')
            return None

        cwdancestors.add(cwd)
        rebasingbranch = cwdancestors - targetancestors
        source = min(rebasingbranch)

    repo.ui.debug('rebase onto %d starting from %d\n' % (dest, source))
    state = dict.fromkeys(repo.changelog.descendants(source), nullrev)
    state.update(dict.fromkeys(detachset, nullmerge))
    state[source] = nullrev
    return repo['.'].rev(), repo[dest].rev(), state

def pullrebase(orig, ui, repo, *args, **opts):
    'Call rebase after pull if the latter has been invoked with --rebase'
    if opts.get('rebase'):
        if opts.get('update'):
            del opts['update']
            ui.debug('--update and --rebase are not compatible, ignoring '
                     'the update flag\n')

        cmdutil.bail_if_changed(repo)
        revsprepull = len(repo)
        orig(ui, repo, *args, **opts)
        revspostpull = len(repo)
        if revspostpull > revsprepull:
            rebase(ui, repo, **opts)
            branch = repo[None].branch()
            dest = repo[branch].rev()
            if dest != repo['.'].rev():
                # there was nothing to rebase we force an update
                merge.update(repo, dest, False, False, False)
    else:
        orig(ui, repo, *args, **opts)

def uisetup(ui):
    'Replace pull with a decorator to provide --rebase option'
    entry = extensions.wrapcommand(commands.table, 'pull', pullrebase)
    entry[1].append(('', 'rebase', None,
                     _("rebase working directory to branch head"))
)

cmdtable = {
"rebase":
        (rebase,
        [
        ('s', 'source', '', _('rebase from the specified changeset')),
        ('b', 'base', '', _('rebase from the base of the specified changeset '
                            '(up to greatest common ancestor of base and dest)')),
        ('d', 'dest', '', _('rebase onto the specified changeset')),
        ('', 'collapse', False, _('collapse the rebased changesets')),
        ('', 'keep', False, _('keep original changesets')),
        ('', 'keepbranches', False, _('keep original branch names')),
        ('', 'detach', False, _('force detaching of source from its original '
                                'branch')),
        ('c', 'continue', False, _('continue an interrupted rebase')),
        ('a', 'abort', False, _('abort an interrupted rebase'))] +
         templateopts,
        _('hg rebase [-s REV | -b REV] [-d REV] [options]\n'
          'hg rebase {-a|-c}'))
}
