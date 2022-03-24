# sparse.py - functionality for sparse checkouts
#
# Copyright 2014 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import os

from .i18n import _
from .node import hex
from . import (
    error,
    match as matchmod,
    merge as mergemod,
    mergestate as mergestatemod,
    pathutil,
    pycompat,
    requirements,
    scmutil,
    util,
)
from .utils import hashutil


# Whether sparse features are enabled. This variable is intended to be
# temporary to facilitate porting sparse to core. It should eventually be
# a per-repo option, possibly a repo requirement.
enabled = False


def parseconfig(ui, raw, action):
    """Parse sparse config file content.

    action is the command which is trigerring this read, can be narrow, sparse

    Returns a tuple of includes, excludes, and profiles.
    """
    includes = set()
    excludes = set()
    profiles = set()
    current = None
    havesection = False

    for line in raw.split(b'\n'):
        line = line.strip()
        if not line or line.startswith(b'#'):
            # empty or comment line, skip
            continue
        elif line.startswith(b'%include '):
            line = line[9:].strip()
            if line:
                profiles.add(line)
        elif line == b'[include]':
            if havesection and current != includes:
                # TODO pass filename into this API so we can report it.
                raise error.Abort(
                    _(
                        b'%(action)s config cannot have includes '
                        b'after excludes'
                    )
                    % {b'action': action}
                )
            havesection = True
            current = includes
            continue
        elif line == b'[exclude]':
            havesection = True
            current = excludes
        elif line:
            if current is None:
                raise error.Abort(
                    _(
                        b'%(action)s config entry outside of '
                        b'section: %(line)s'
                    )
                    % {b'action': action, b'line': line},
                    hint=_(
                        b'add an [include] or [exclude] line '
                        b'to declare the entry type'
                    ),
                )

            if line.strip().startswith(b'/'):
                ui.warn(
                    _(
                        b'warning: %(action)s profile cannot use'
                        b' paths starting with /, ignoring %(line)s\n'
                    )
                    % {b'action': action, b'line': line}
                )
                continue
            current.add(line)

    return includes, excludes, profiles


# Exists as separate function to facilitate monkeypatching.
def readprofile(repo, profile, changeid):
    """Resolve the raw content of a sparse profile file."""
    # TODO add some kind of cache here because this incurs a manifest
    # resolve and can be slow.
    return repo.filectx(profile, changeid=changeid).data()


def patternsforrev(repo, rev):
    """Obtain sparse checkout patterns for the given rev.

    Returns a tuple of iterables representing includes, excludes, and
    patterns.
    """
    # Feature isn't enabled. No-op.
    if not enabled:
        return set(), set(), set()

    raw = repo.vfs.tryread(b'sparse')
    if not raw:
        return set(), set(), set()

    if rev is None:
        raise error.Abort(
            _(b'cannot parse sparse patterns from working directory')
        )

    includes, excludes, profiles = parseconfig(repo.ui, raw, b'sparse')
    ctx = repo[rev]

    if profiles:
        visited = set()
        while profiles:
            profile = profiles.pop()
            if profile in visited:
                continue

            visited.add(profile)

            try:
                raw = readprofile(repo, profile, rev)
            except error.ManifestLookupError:
                msg = (
                    b"warning: sparse profile '%s' not found "
                    b"in rev %s - ignoring it\n" % (profile, ctx)
                )
                # experimental config: sparse.missingwarning
                if repo.ui.configbool(b'sparse', b'missingwarning'):
                    repo.ui.warn(msg)
                else:
                    repo.ui.debug(msg)
                continue

            pincludes, pexcludes, subprofs = parseconfig(
                repo.ui, raw, b'sparse'
            )
            includes.update(pincludes)
            excludes.update(pexcludes)
            profiles.update(subprofs)

        profiles = visited

    if includes:
        includes.add(b'.hg*')

    return includes, excludes, profiles


def activeconfig(repo):
    """Determine the active sparse config rules.

    Rules are constructed by reading the current sparse config and bringing in
    referenced profiles from parents of the working directory.
    """
    revs = [
        repo.changelog.rev(node)
        for node in repo.dirstate.parents()
        if node != repo.nullid
    ]

    allincludes = set()
    allexcludes = set()
    allprofiles = set()

    for rev in revs:
        includes, excludes, profiles = patternsforrev(repo, rev)
        allincludes |= includes
        allexcludes |= excludes
        allprofiles |= profiles

    return allincludes, allexcludes, allprofiles


def configsignature(repo, includetemp=True):
    """Obtain the signature string for the current sparse configuration.

    This is used to construct a cache key for matchers.
    """
    cache = repo._sparsesignaturecache

    signature = cache.get(b'signature')

    if includetemp:
        tempsignature = cache.get(b'tempsignature')
    else:
        tempsignature = b'0'

    if signature is None or (includetemp and tempsignature is None):
        signature = hex(hashutil.sha1(repo.vfs.tryread(b'sparse')).digest())
        cache[b'signature'] = signature

        if includetemp:
            raw = repo.vfs.tryread(b'tempsparse')
            tempsignature = hex(hashutil.sha1(raw).digest())
            cache[b'tempsignature'] = tempsignature

    return b'%s %s' % (signature, tempsignature)


def writeconfig(repo, includes, excludes, profiles):
    """Write the sparse config file given a sparse configuration."""
    with repo.vfs(b'sparse', b'wb') as fh:
        for p in sorted(profiles):
            fh.write(b'%%include %s\n' % p)

        if includes:
            fh.write(b'[include]\n')
            for i in sorted(includes):
                fh.write(i)
                fh.write(b'\n')

        if excludes:
            fh.write(b'[exclude]\n')
            for e in sorted(excludes):
                fh.write(e)
                fh.write(b'\n')

    repo._sparsesignaturecache.clear()


def readtemporaryincludes(repo):
    raw = repo.vfs.tryread(b'tempsparse')
    if not raw:
        return set()

    return set(raw.split(b'\n'))


def writetemporaryincludes(repo, includes):
    repo.vfs.write(b'tempsparse', b'\n'.join(sorted(includes)))
    repo._sparsesignaturecache.clear()


def addtemporaryincludes(repo, additional):
    includes = readtemporaryincludes(repo)
    for i in additional:
        includes.add(i)
    writetemporaryincludes(repo, includes)


def prunetemporaryincludes(repo):
    if not enabled or not repo.vfs.exists(b'tempsparse'):
        return

    s = repo.status()
    if s.modified or s.added or s.removed or s.deleted:
        # Still have pending changes. Don't bother trying to prune.
        return

    sparsematch = matcher(repo, includetemp=False)
    dirstate = repo.dirstate
    mresult = mergemod.mergeresult()
    dropped = []
    tempincludes = readtemporaryincludes(repo)
    for file in tempincludes:
        if file in dirstate and not sparsematch(file):
            message = _(b'dropping temporarily included sparse files')
            mresult.addfile(file, mergestatemod.ACTION_REMOVE, None, message)
            dropped.append(file)

    mergemod.applyupdates(
        repo, mresult, repo[None], repo[b'.'], False, wantfiledata=False
    )

    # Fix dirstate
    for file in dropped:
        dirstate.update_file(file, p1_tracked=False, wc_tracked=False)

    repo.vfs.unlink(b'tempsparse')
    repo._sparsesignaturecache.clear()
    msg = _(
        b'cleaned up %d temporarily added file(s) from the '
        b'sparse checkout\n'
    )
    repo.ui.status(msg % len(tempincludes))


def forceincludematcher(matcher, includes):
    """Returns a matcher that returns true for any of the forced includes
    before testing against the actual matcher."""
    kindpats = [(b'path', include, b'') for include in includes]
    includematcher = matchmod.includematcher(b'', kindpats)
    return matchmod.unionmatcher([includematcher, matcher])


def matcher(repo, revs=None, includetemp=True):
    """Obtain a matcher for sparse working directories for the given revs.

    If multiple revisions are specified, the matcher is the union of all
    revs.

    ``includetemp`` indicates whether to use the temporary sparse profile.
    """
    # If sparse isn't enabled, sparse matcher matches everything.
    if not enabled:
        return matchmod.always()

    if not revs or revs == [None]:
        revs = [
            repo.changelog.rev(node)
            for node in repo.dirstate.parents()
            if node != repo.nullid
        ]

    signature = configsignature(repo, includetemp=includetemp)

    key = b'%s %s' % (signature, b' '.join(map(pycompat.bytestr, revs)))

    result = repo._sparsematchercache.get(key)
    if result:
        return result

    matchers = []
    for rev in revs:
        try:
            includes, excludes, profiles = patternsforrev(repo, rev)

            if includes or excludes:
                matcher = matchmod.match(
                    repo.root,
                    b'',
                    [],
                    include=includes,
                    exclude=excludes,
                    default=b'relpath',
                )
                matchers.append(matcher)
        except IOError:
            pass

    if not matchers:
        result = matchmod.always()
    elif len(matchers) == 1:
        result = matchers[0]
    else:
        result = matchmod.unionmatcher(matchers)

    if includetemp:
        tempincludes = readtemporaryincludes(repo)
        result = forceincludematcher(result, tempincludes)

    repo._sparsematchercache[key] = result

    return result


def filterupdatesactions(repo, wctx, mctx, branchmerge, mresult):
    """Filter updates to only lay out files that match the sparse rules."""
    if not enabled:
        return

    oldrevs = [pctx.rev() for pctx in wctx.parents()]
    oldsparsematch = matcher(repo, oldrevs)

    if oldsparsematch.always():
        return

    files = set()
    prunedactions = {}

    if branchmerge:
        # If we're merging, use the wctx filter, since we're merging into
        # the wctx.
        sparsematch = matcher(repo, [wctx.p1().rev()])
    else:
        # If we're updating, use the target context's filter, since we're
        # moving to the target context.
        sparsematch = matcher(repo, [mctx.rev()])

    temporaryfiles = []
    for file, action in mresult.filemap():
        type, args, msg = action
        files.add(file)
        if sparsematch(file):
            prunedactions[file] = action
        elif type == mergestatemod.ACTION_MERGE:
            temporaryfiles.append(file)
            prunedactions[file] = action
        elif branchmerge:
            if type not in mergestatemod.NO_OP_ACTIONS:
                temporaryfiles.append(file)
                prunedactions[file] = action
        elif type == mergestatemod.ACTION_FORGET:
            prunedactions[file] = action
        elif file in wctx:
            prunedactions[file] = (mergestatemod.ACTION_REMOVE, args, msg)

        # in case or rename on one side, it is possible that f1 might not
        # be present in sparse checkout we should include it
        # TODO: should we do the same for f2?
        # exists as a separate check because file can be in sparse and hence
        # if we try to club this condition in above `elif type == ACTION_MERGE`
        # it won't be triggered
        if branchmerge and type == mergestatemod.ACTION_MERGE:
            f1, f2, fa, move, anc = args
            if not sparsematch(f1):
                temporaryfiles.append(f1)

    if len(temporaryfiles) > 0:
        repo.ui.status(
            _(
                b'temporarily included %d file(s) in the sparse '
                b'checkout for merging\n'
            )
            % len(temporaryfiles)
        )
        addtemporaryincludes(repo, temporaryfiles)

        # Add the new files to the working copy so they can be merged, etc
        tmresult = mergemod.mergeresult()
        message = b'temporarily adding to sparse checkout'
        wctxmanifest = repo[None].manifest()
        for file in temporaryfiles:
            if file in wctxmanifest:
                fctx = repo[None][file]
                tmresult.addfile(
                    file,
                    mergestatemod.ACTION_GET,
                    (fctx.flags(), False),
                    message,
                )

        with repo.dirstate.parentchange():
            mergemod.applyupdates(
                repo,
                tmresult,
                repo[None],
                repo[b'.'],
                False,
                wantfiledata=False,
            )

            dirstate = repo.dirstate
            for file, flags, msg in tmresult.getactions(
                [mergestatemod.ACTION_GET]
            ):
                dirstate.update_file(file, p1_tracked=True, wc_tracked=True)

    profiles = activeconfig(repo)[2]
    changedprofiles = profiles & files
    # If an active profile changed during the update, refresh the checkout.
    # Don't do this during a branch merge, since all incoming changes should
    # have been handled by the temporary includes above.
    if changedprofiles and not branchmerge:
        mf = mctx.manifest()
        for file in mf:
            old = oldsparsematch(file)
            new = sparsematch(file)
            if not old and new:
                flags = mf.flags(file)
                prunedactions[file] = (
                    mergestatemod.ACTION_GET,
                    (flags, False),
                    b'',
                )
            elif old and not new:
                prunedactions[file] = (mergestatemod.ACTION_REMOVE, [], b'')

    mresult.setactions(prunedactions)


def refreshwdir(repo, origstatus, origsparsematch, force=False):
    """Refreshes working directory by taking sparse config into account.

    The old status and sparse matcher is compared against the current sparse
    matcher.

    Will abort if a file with pending changes is being excluded or included
    unless ``force`` is True.
    """
    # Verify there are no pending changes
    pending = set()
    pending.update(origstatus.modified)
    pending.update(origstatus.added)
    pending.update(origstatus.removed)
    sparsematch = matcher(repo)
    abort = False

    for f in pending:
        if not sparsematch(f):
            repo.ui.warn(_(b"pending changes to '%s'\n") % f)
            abort = not force

    if abort:
        raise error.Abort(
            _(b'could not update sparseness due to pending changes')
        )

    # Calculate merge result
    dirstate = repo.dirstate
    ctx = repo[b'.']
    added = []
    lookup = []
    dropped = []
    mf = ctx.manifest()
    files = set(mf)
    mresult = mergemod.mergeresult()

    for file in files:
        old = origsparsematch(file)
        new = sparsematch(file)
        # Add files that are newly included, or that don't exist in
        # the dirstate yet.
        if (new and not old) or (old and new and not file in dirstate):
            fl = mf.flags(file)
            if repo.wvfs.exists(file):
                mresult.addfile(file, mergestatemod.ACTION_EXEC, (fl,), b'')
                lookup.append(file)
            else:
                mresult.addfile(
                    file, mergestatemod.ACTION_GET, (fl, False), b''
                )
                added.append(file)
        # Drop files that are newly excluded, or that still exist in
        # the dirstate.
        elif (old and not new) or (not old and not new and file in dirstate):
            dropped.append(file)
            if file not in pending:
                mresult.addfile(file, mergestatemod.ACTION_REMOVE, [], b'')

    # Verify there are no pending changes in newly included files
    abort = False
    for file in lookup:
        repo.ui.warn(_(b"pending changes to '%s'\n") % file)
        abort = not force
    if abort:
        raise error.Abort(
            _(
                b'cannot change sparseness due to pending '
                b'changes (delete the files or use '
                b'--force to bring them back dirty)'
            )
        )

    # Check for files that were only in the dirstate.
    for file, state in pycompat.iteritems(dirstate):
        if not file in files:
            old = origsparsematch(file)
            new = sparsematch(file)
            if old and not new:
                dropped.append(file)

    mergemod.applyupdates(
        repo, mresult, repo[None], repo[b'.'], False, wantfiledata=False
    )

    # Fix dirstate
    for file in added:
        dirstate.update_file(file, p1_tracked=True, wc_tracked=True)

    for file in dropped:
        dirstate.update_file(file, p1_tracked=False, wc_tracked=False)

    for file in lookup:
        # File exists on disk, and we're bringing it back in an unknown state.
        dirstate.update_file(
            file, p1_tracked=True, wc_tracked=True, possibly_dirty=True
        )

    return added, dropped, lookup


def aftercommit(repo, node):
    """Perform actions after a working directory commit."""
    # This function is called unconditionally, even if sparse isn't
    # enabled.
    ctx = repo[node]

    profiles = patternsforrev(repo, ctx.rev())[2]

    # profiles will only have data if sparse is enabled.
    if profiles & set(ctx.files()):
        origstatus = repo.status()
        origsparsematch = matcher(repo)
        refreshwdir(repo, origstatus, origsparsematch, force=True)

    prunetemporaryincludes(repo)


def _updateconfigandrefreshwdir(
    repo, includes, excludes, profiles, force=False, removing=False
):
    """Update the sparse config and working directory state."""
    raw = repo.vfs.tryread(b'sparse')
    oldincludes, oldexcludes, oldprofiles = parseconfig(repo.ui, raw, b'sparse')

    oldstatus = repo.status()
    oldmatch = matcher(repo)
    oldrequires = set(repo.requirements)

    # TODO remove this try..except once the matcher integrates better
    # with dirstate. We currently have to write the updated config
    # because that will invalidate the matcher cache and force a
    # re-read. We ideally want to update the cached matcher on the
    # repo instance then flush the new config to disk once wdir is
    # updated. But this requires massive rework to matcher() and its
    # consumers.

    if requirements.SPARSE_REQUIREMENT in oldrequires and removing:
        repo.requirements.discard(requirements.SPARSE_REQUIREMENT)
        scmutil.writereporequirements(repo)
    elif requirements.SPARSE_REQUIREMENT not in oldrequires:
        repo.requirements.add(requirements.SPARSE_REQUIREMENT)
        scmutil.writereporequirements(repo)

    try:
        writeconfig(repo, includes, excludes, profiles)
        return refreshwdir(repo, oldstatus, oldmatch, force=force)
    except Exception:
        if repo.requirements != oldrequires:
            repo.requirements.clear()
            repo.requirements |= oldrequires
            scmutil.writereporequirements(repo)
        writeconfig(repo, oldincludes, oldexcludes, oldprofiles)
        raise


def clearrules(repo, force=False):
    """Clears include/exclude rules from the sparse config.

    The remaining sparse config only has profiles, if defined. The working
    directory is refreshed, as needed.
    """
    with repo.wlock(), repo.dirstate.parentchange():
        raw = repo.vfs.tryread(b'sparse')
        includes, excludes, profiles = parseconfig(repo.ui, raw, b'sparse')

        if not includes and not excludes:
            return

        _updateconfigandrefreshwdir(repo, set(), set(), profiles, force=force)


def importfromfiles(repo, opts, paths, force=False):
    """Import sparse config rules from files.

    The updated sparse config is written out and the working directory
    is refreshed, as needed.
    """
    with repo.wlock(), repo.dirstate.parentchange():
        # read current configuration
        raw = repo.vfs.tryread(b'sparse')
        includes, excludes, profiles = parseconfig(repo.ui, raw, b'sparse')
        aincludes, aexcludes, aprofiles = activeconfig(repo)

        # Import rules on top; only take in rules that are not yet
        # part of the active rules.
        changed = False
        for p in paths:
            with util.posixfile(util.expandpath(p), mode=b'rb') as fh:
                raw = fh.read()

            iincludes, iexcludes, iprofiles = parseconfig(
                repo.ui, raw, b'sparse'
            )
            oldsize = len(includes) + len(excludes) + len(profiles)
            includes.update(iincludes - aincludes)
            excludes.update(iexcludes - aexcludes)
            profiles.update(iprofiles - aprofiles)
            if len(includes) + len(excludes) + len(profiles) > oldsize:
                changed = True

        profilecount = includecount = excludecount = 0
        fcounts = (0, 0, 0)

        if changed:
            profilecount = len(profiles - aprofiles)
            includecount = len(includes - aincludes)
            excludecount = len(excludes - aexcludes)

            fcounts = map(
                len,
                _updateconfigandrefreshwdir(
                    repo, includes, excludes, profiles, force=force
                ),
            )

        printchanges(
            repo.ui, opts, profilecount, includecount, excludecount, *fcounts
        )


def updateconfig(
    repo,
    pats,
    opts,
    include=False,
    exclude=False,
    reset=False,
    delete=False,
    enableprofile=False,
    disableprofile=False,
    force=False,
    usereporootpaths=False,
):
    """Perform a sparse config update.

    Only one of the actions may be performed.

    The new config is written out and a working directory refresh is performed.
    """
    with repo.wlock(), repo.dirstate.parentchange():
        raw = repo.vfs.tryread(b'sparse')
        oldinclude, oldexclude, oldprofiles = parseconfig(
            repo.ui, raw, b'sparse'
        )

        if reset:
            newinclude = set()
            newexclude = set()
            newprofiles = set()
        else:
            newinclude = set(oldinclude)
            newexclude = set(oldexclude)
            newprofiles = set(oldprofiles)

        if any(os.path.isabs(pat) for pat in pats):
            raise error.Abort(_(b'paths cannot be absolute'))

        if not usereporootpaths:
            # let's treat paths as relative to cwd
            root, cwd = repo.root, repo.getcwd()
            abspats = []
            for kindpat in pats:
                kind, pat = matchmod._patsplit(kindpat, None)
                if kind in matchmod.cwdrelativepatternkinds or kind is None:
                    ap = (kind + b':' if kind else b'') + pathutil.canonpath(
                        root, cwd, pat
                    )
                    abspats.append(ap)
                else:
                    abspats.append(kindpat)
            pats = abspats

        if include:
            newinclude.update(pats)
        elif exclude:
            newexclude.update(pats)
        elif enableprofile:
            newprofiles.update(pats)
        elif disableprofile:
            newprofiles.difference_update(pats)
        elif delete:
            newinclude.difference_update(pats)
            newexclude.difference_update(pats)

        profilecount = len(newprofiles - oldprofiles) - len(
            oldprofiles - newprofiles
        )
        includecount = len(newinclude - oldinclude) - len(
            oldinclude - newinclude
        )
        excludecount = len(newexclude - oldexclude) - len(
            oldexclude - newexclude
        )

        fcounts = map(
            len,
            _updateconfigandrefreshwdir(
                repo,
                newinclude,
                newexclude,
                newprofiles,
                force=force,
                removing=reset,
            ),
        )

        printchanges(
            repo.ui, opts, profilecount, includecount, excludecount, *fcounts
        )


def printchanges(
    ui,
    opts,
    profilecount=0,
    includecount=0,
    excludecount=0,
    added=0,
    dropped=0,
    conflicting=0,
):
    """Print output summarizing sparse config changes."""
    with ui.formatter(b'sparse', opts) as fm:
        fm.startitem()
        fm.condwrite(
            ui.verbose,
            b'profiles_added',
            _(b'Profiles changed: %d\n'),
            profilecount,
        )
        fm.condwrite(
            ui.verbose,
            b'include_rules_added',
            _(b'Include rules changed: %d\n'),
            includecount,
        )
        fm.condwrite(
            ui.verbose,
            b'exclude_rules_added',
            _(b'Exclude rules changed: %d\n'),
            excludecount,
        )

        # In 'plain' verbose mode, mergemod.applyupdates already outputs what
        # files are added or removed outside of the templating formatter
        # framework. No point in repeating ourselves in that case.
        if not fm.isplain():
            fm.condwrite(
                ui.verbose, b'files_added', _(b'Files added: %d\n'), added
            )
            fm.condwrite(
                ui.verbose, b'files_dropped', _(b'Files dropped: %d\n'), dropped
            )
            fm.condwrite(
                ui.verbose,
                b'files_conflicting',
                _(b'Files conflicting: %d\n'),
                conflicting,
            )
