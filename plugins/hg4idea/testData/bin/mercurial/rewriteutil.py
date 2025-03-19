# rewriteutil.py - utility functions for rewriting changesets
#
# Copyright 2017 Octobus <contact@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import re

from .i18n import _
from .node import (
    hex,
    nullrev,
)

from . import (
    error,
    node,
    obsolete,
    obsutil,
    revset,
    scmutil,
)


NODE_RE = re.compile(br'\b[0-9a-f]{6,64}\b')

# set of extra entry that should survive a rebase-like operation, extensible by extensions
retained_extras_on_rebase = {
    b'source',
    b'intermediate-source',
}


def preserve_extras_on_rebase(old_ctx, new_extra):
    """preserve the relevant `extra` entry from old_ctx on rebase-like operation"""
    new_extra.update(
        (key, value)
        for key, value in old_ctx.extra().items()
        if key in retained_extras_on_rebase
    )


def _formatrevs(repo, revs, maxrevs=4):
    """returns a string summarizing revisions in a decent size

    If there are few enough revisions, we list them all. Otherwise we display a
    summary of the form:

        1ea73414a91b and 5 others
    """
    tonode = repo.changelog.node
    numrevs = len(revs)
    if numrevs < maxrevs:
        shorts = [node.short(tonode(r)) for r in revs]
        summary = b', '.join(shorts)
    else:
        first = revs.first()
        summary = _(b'%s and %d others')
        summary %= (node.short(tonode(first)), numrevs - 1)
    return summary


def precheck(repo, revs, action=b'rewrite', check_divergence=True):
    """check if revs can be rewritten
    action is used to control the error message.

    check_divergence allows skipping the divergence checks in cases like adding
    a prune marker (A, ()) to obsstore (which can't be diverging).

    Make sure this function is called after taking the lock.
    """
    if nullrev in revs:
        msg = _(b"cannot %s the null revision") % action
        hint = _(b"no changeset checked out")
        raise error.InputError(msg, hint=hint)

    if any(hasattr(r, 'rev') for r in revs):
        repo.ui.develwarn(b"rewriteutil.precheck called with ctx not revs")
        revs = (r.rev() for r in revs)

    if len(repo[None].parents()) > 1:
        raise error.StateError(
            _(b"cannot %s changesets while merging") % action
        )

    publicrevs = repo.revs(b'%ld and public()', revs)
    if publicrevs:
        summary = _formatrevs(repo, publicrevs)
        msg = _(b"cannot %s public changesets: %s") % (action, summary)
        hint = _(b"see 'hg help phases' for details")
        raise error.InputError(msg, hint=hint)

    newunstable = disallowednewunstable(repo, revs)
    if newunstable:
        hint = _(b"see 'hg help evolution.instability'")
        raise error.InputError(
            _(b"cannot %s changeset, as that will orphan %d descendants")
            % (action, len(newunstable)),
            hint=hint,
        )

    if not check_divergence:
        return

    if not obsolete.isenabled(repo, obsolete.allowdivergenceopt):
        new_divergence = _find_new_divergence(repo, revs)
        if new_divergence:
            local_ctx, other_ctx, base_ctx = new_divergence
            msg = _(
                b'cannot %s %s, as that creates content-divergence with %s'
            ) % (
                action,
                local_ctx,
                other_ctx,
            )
            if local_ctx.rev() != base_ctx.rev():
                msg += _(b', from %s') % base_ctx
            if repo.ui.verbose:
                if local_ctx.rev() != base_ctx.rev():
                    msg += _(
                        b'\n    changeset %s is a successor of ' b'changeset %s'
                    ) % (local_ctx, base_ctx)
                msg += _(
                    b'\n    changeset %s already has a successor in '
                    b'changeset %s\n'
                    b'    rewriting changeset %s would create '
                    b'"content-divergence"\n'
                    b'    set experimental.evolution.allowdivergence=True to '
                    b'skip this check'
                ) % (base_ctx, other_ctx, local_ctx)
                raise error.InputError(
                    msg,
                    hint=_(
                        b"see 'hg help evolution.instability' for details on content-divergence"
                    ),
                )
            else:
                raise error.InputError(
                    msg,
                    hint=_(
                        b"add --verbose for details or see "
                        b"'hg help evolution.instability'"
                    ),
                )


def disallowednewunstable(repo, revs):
    """Checks whether editing the revs will create new unstable changesets and
    are we allowed to create them.

    To allow new unstable changesets, set the config:
        `experimental.evolution.allowunstable=True`
    """
    allowunstable = obsolete.isenabled(repo, obsolete.allowunstableopt)
    if allowunstable:
        return revset.baseset()
    return repo.revs(b"(%ld::) - %ld", revs, revs)


def _find_new_divergence(repo, revs):
    obsrevs = repo.revs(b'%ld and obsolete()', revs)
    for r in obsrevs:
        div = find_new_divergence_from(repo, repo[r])
        if div:
            return (repo[r], repo[div[0]], repo.unfiltered()[div[1]])
    return None


def find_new_divergence_from(repo, ctx):
    """return divergent revision if rewriting an obsolete cset (ctx) will
    create divergence

    Returns (<other node>, <common ancestor node>) or None
    """
    if not ctx.obsolete():
        return None
    # We need to check two cases that can cause divergence:
    # case 1: the rev being rewritten has a non-obsolete successor (easily
    #     detected by successorssets)
    sset = obsutil.successorssets(repo, ctx.node())
    if sset:
        return (sset[0][0], ctx.node())
    else:
        # case 2: one of the precursors of the rev being revived has a
        #     non-obsolete successor (we need divergentsets for this)
        divsets = obsutil.divergentsets(repo, ctx)
        if divsets:
            nsuccset = divsets[0][b'divergentnodes']
            prec = divsets[0][b'commonpredecessor']
            return (nsuccset[0], prec)
        return None


def skip_empty_successor(ui, command):
    empty_successor = ui.config(b'rewrite', b'empty-successor')
    if empty_successor == b'skip':
        return True
    elif empty_successor == b'keep':
        return False
    else:
        raise error.ConfigError(
            _(
                b"%s doesn't know how to handle config "
                b"rewrite.empty-successor=%s (only 'skip' and 'keep' are "
                b"supported)"
            )
            % (command, empty_successor)
        )


def update_hash_refs(repo, commitmsg, pending=None):
    """Replace all obsolete commit hashes in the message with the current hash.

    If the obsolete commit was split or is divergent, the hash is not replaced
    as there's no way to know which successor to choose.

    For commands that update a series of commits in the current transaction, the
    new obsolete markers can be considered by setting ``pending`` to a mapping
    of ``pending[oldnode] = [successor_node1, successor_node2,..]``.
    """
    if not pending:
        pending = {}
    cache = {}
    hashes = re.findall(NODE_RE, commitmsg)
    unfi = repo.unfiltered()
    for h in hashes:
        try:
            fullnode = scmutil.resolvehexnodeidprefix(unfi, h)
        except (error.WdirUnsupported, error.AmbiguousPrefixLookupError):
            # Someone has an fffff... or some other prefix that's ambiguous in a
            # commit message we're rewriting. Don't try rewriting that.
            continue
        if fullnode is None:
            continue
        ctx = unfi[fullnode]
        if not ctx.obsolete():
            successors = pending.get(fullnode)
            if successors is None:
                continue
            # obsutil.successorssets() returns a list of list of nodes
            successors = [successors]
        else:
            successors = obsutil.successorssets(repo, ctx.node(), cache=cache)

        # We can't make any assumptions about how to update the hash if the
        # cset in question was split or diverged.
        if len(successors) == 1 and len(successors[0]) == 1:
            successor = successors[0][0]
            if successor is not None:
                newhash = hex(successor)
                commitmsg = commitmsg.replace(h, newhash[: len(h)])
            else:
                repo.ui.note(
                    _(
                        b'The stale commit message reference to %s could '
                        b'not be updated\n(The referenced commit was dropped)\n'
                    )
                    % h
                )
        else:
            repo.ui.note(
                _(
                    b'The stale commit message reference to %s could '
                    b'not be updated\n'
                )
                % h
            )

    return commitmsg
