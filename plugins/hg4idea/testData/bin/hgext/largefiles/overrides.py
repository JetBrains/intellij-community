# Copyright 2009-2010 Gregory P. Ward
# Copyright 2009-2010 Intelerad Medical Systems Incorporated
# Copyright 2010-2011 Fog Creek Software
# Copyright 2010-2011 Unity Technologies
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''Overridden Mercurial commands and functions for the largefiles extension'''

import os
import copy

from mercurial import hg, commands, util, cmdutil, scmutil, match as match_, \
    node, archival, error, merge, discovery
from mercurial.i18n import _
from mercurial.node import hex
from hgext import rebase

import lfutil
import lfcommands
import basestore

# -- Utility functions: commonly/repeatedly needed functionality ---------------

def installnormalfilesmatchfn(manifest):
    '''overrides scmutil.match so that the matcher it returns will ignore all
    largefiles'''
    oldmatch = None # for the closure
    def overridematch(ctx, pats=[], opts={}, globbed=False,
            default='relpath'):
        match = oldmatch(ctx, pats, opts, globbed, default)
        m = copy.copy(match)
        notlfile = lambda f: not (lfutil.isstandin(f) or lfutil.standin(f) in
                manifest)
        m._files = filter(notlfile, m._files)
        m._fmap = set(m._files)
        m._always = False
        origmatchfn = m.matchfn
        m.matchfn = lambda f: notlfile(f) and origmatchfn(f) or None
        return m
    oldmatch = installmatchfn(overridematch)

def installmatchfn(f):
    oldmatch = scmutil.match
    setattr(f, 'oldmatch', oldmatch)
    scmutil.match = f
    return oldmatch

def restorematchfn():
    '''restores scmutil.match to what it was before installnormalfilesmatchfn
    was called.  no-op if scmutil.match is its original function.

    Note that n calls to installnormalfilesmatchfn will require n calls to
    restore matchfn to reverse'''
    scmutil.match = getattr(scmutil.match, 'oldmatch', scmutil.match)

def addlargefiles(ui, repo, *pats, **opts):
    large = opts.pop('large', None)
    lfsize = lfutil.getminsize(
        ui, lfutil.islfilesrepo(repo), opts.pop('lfsize', None))

    lfmatcher = None
    if lfutil.islfilesrepo(repo):
        lfpats = ui.configlist(lfutil.longname, 'patterns', default=[])
        if lfpats:
            lfmatcher = match_.match(repo.root, '', list(lfpats))

    lfnames = []
    m = scmutil.match(repo[None], pats, opts)
    m.bad = lambda x, y: None
    wctx = repo[None]
    for f in repo.walk(m):
        exact = m.exact(f)
        lfile = lfutil.standin(f) in wctx
        nfile = f in wctx
        exists = lfile or nfile

        # Don't warn the user when they attempt to add a normal tracked file.
        # The normal add code will do that for us.
        if exact and exists:
            if lfile:
                ui.warn(_('%s already a largefile\n') % f)
            continue

        if (exact or not exists) and not lfutil.isstandin(f):
            wfile = repo.wjoin(f)

            # In case the file was removed previously, but not committed
            # (issue3507)
            if not os.path.exists(wfile):
                continue

            abovemin = (lfsize and
                        os.lstat(wfile).st_size >= lfsize * 1024 * 1024)
            if large or abovemin or (lfmatcher and lfmatcher(f)):
                lfnames.append(f)
                if ui.verbose or not exact:
                    ui.status(_('adding %s as a largefile\n') % m.rel(f))

    bad = []
    standins = []

    # Need to lock, otherwise there could be a race condition between
    # when standins are created and added to the repo.
    wlock = repo.wlock()
    try:
        if not opts.get('dry_run'):
            lfdirstate = lfutil.openlfdirstate(ui, repo)
            for f in lfnames:
                standinname = lfutil.standin(f)
                lfutil.writestandin(repo, standinname, hash='',
                    executable=lfutil.getexecutable(repo.wjoin(f)))
                standins.append(standinname)
                if lfdirstate[f] == 'r':
                    lfdirstate.normallookup(f)
                else:
                    lfdirstate.add(f)
            lfdirstate.write()
            bad += [lfutil.splitstandin(f)
                    for f in repo[None].add(standins)
                    if f in m.files()]
    finally:
        wlock.release()
    return bad

def removelargefiles(ui, repo, *pats, **opts):
    after = opts.get('after')
    if not pats and not after:
        raise util.Abort(_('no files specified'))
    m = scmutil.match(repo[None], pats, opts)
    try:
        repo.lfstatus = True
        s = repo.status(match=m, clean=True)
    finally:
        repo.lfstatus = False
    manifest = repo[None].manifest()
    modified, added, deleted, clean = [[f for f in list
                                        if lfutil.standin(f) in manifest]
                                       for list in [s[0], s[1], s[3], s[6]]]

    def warn(files, msg):
        for f in files:
            ui.warn(msg % m.rel(f))
        return int(len(files) > 0)

    result = 0

    if after:
        remove, forget = deleted, []
        result = warn(modified + added + clean,
                      _('not removing %s: file still exists\n'))
    else:
        remove, forget = deleted + clean, []
        result = warn(modified, _('not removing %s: file is modified (use -f'
                                  ' to force removal)\n'))
        result = warn(added, _('not removing %s: file has been marked for add'
                               ' (use forget to undo)\n')) or result

    for f in sorted(remove + forget):
        if ui.verbose or not m.exact(f):
            ui.status(_('removing %s\n') % m.rel(f))

    # Need to lock because standin files are deleted then removed from the
    # repository and we could race in-between.
    wlock = repo.wlock()
    try:
        lfdirstate = lfutil.openlfdirstate(ui, repo)
        for f in remove:
            if not after:
                # If this is being called by addremove, notify the user that we
                # are removing the file.
                if getattr(repo, "_isaddremove", False):
                    ui.status(_('removing %s\n') % f)
                util.unlinkpath(repo.wjoin(f), ignoremissing=True)
            lfdirstate.remove(f)
        lfdirstate.write()
        forget = [lfutil.standin(f) for f in forget]
        remove = [lfutil.standin(f) for f in remove]
        repo[None].forget(forget)
        # If this is being called by addremove, let the original addremove
        # function handle this.
        if not getattr(repo, "_isaddremove", False):
            for f in remove:
                util.unlinkpath(repo.wjoin(f), ignoremissing=True)
        repo[None].forget(remove)
    finally:
        wlock.release()

    return result

# For overriding mercurial.hgweb.webcommands so that largefiles will
# appear at their right place in the manifests.
def decodepath(orig, path):
    return lfutil.splitstandin(path) or path

# -- Wrappers: modify existing commands --------------------------------

# Add works by going through the files that the user wanted to add and
# checking if they should be added as largefiles. Then it makes a new
# matcher which matches only the normal files and runs the original
# version of add.
def overrideadd(orig, ui, repo, *pats, **opts):
    normal = opts.pop('normal')
    if normal:
        if opts.get('large'):
            raise util.Abort(_('--normal cannot be used with --large'))
        return orig(ui, repo, *pats, **opts)
    bad = addlargefiles(ui, repo, *pats, **opts)
    installnormalfilesmatchfn(repo[None].manifest())
    result = orig(ui, repo, *pats, **opts)
    restorematchfn()

    return (result == 1 or bad) and 1 or 0

def overrideremove(orig, ui, repo, *pats, **opts):
    installnormalfilesmatchfn(repo[None].manifest())
    result = orig(ui, repo, *pats, **opts)
    restorematchfn()
    return removelargefiles(ui, repo, *pats, **opts) or result

def overridestatusfn(orig, repo, rev2, **opts):
    try:
        repo._repo.lfstatus = True
        return orig(repo, rev2, **opts)
    finally:
        repo._repo.lfstatus = False

def overridestatus(orig, ui, repo, *pats, **opts):
    try:
        repo.lfstatus = True
        return orig(ui, repo, *pats, **opts)
    finally:
        repo.lfstatus = False

def overridedirty(orig, repo, ignoreupdate=False):
    try:
        repo._repo.lfstatus = True
        return orig(repo, ignoreupdate)
    finally:
        repo._repo.lfstatus = False

def overridelog(orig, ui, repo, *pats, **opts):
    def overridematch(ctx, pats=[], opts={}, globbed=False,
            default='relpath'):
        """Matcher that merges root directory with .hglf, suitable for log.
        It is still possible to match .hglf directly.
        For any listed files run log on the standin too.
        matchfn tries both the given filename and with .hglf stripped.
        """
        match = oldmatch(ctx, pats, opts, globbed, default)
        m = copy.copy(match)
        standins = [lfutil.standin(f) for f in m._files]
        m._files.extend(standins)
        m._fmap = set(m._files)
        m._always = False
        origmatchfn = m.matchfn
        def lfmatchfn(f):
            lf = lfutil.splitstandin(f)
            if lf is not None and origmatchfn(lf):
                return True
            r = origmatchfn(f)
            return r
        m.matchfn = lfmatchfn
        return m
    oldmatch = installmatchfn(overridematch)
    try:
        repo.lfstatus = True
        return orig(ui, repo, *pats, **opts)
    finally:
        repo.lfstatus = False
        restorematchfn()

def overrideverify(orig, ui, repo, *pats, **opts):
    large = opts.pop('large', False)
    all = opts.pop('lfa', False)
    contents = opts.pop('lfc', False)

    result = orig(ui, repo, *pats, **opts)
    if large or all or contents:
        result = result or lfcommands.verifylfiles(ui, repo, all, contents)
    return result

def overridedebugstate(orig, ui, repo, *pats, **opts):
    large = opts.pop('large', False)
    if large:
        lfcommands.debugdirstate(ui, repo)
    else:
        orig(ui, repo, *pats, **opts)

# Override needs to refresh standins so that update's normal merge
# will go through properly. Then the other update hook (overriding repo.update)
# will get the new files. Filemerge is also overridden so that the merge
# will merge standins correctly.
def overrideupdate(orig, ui, repo, *pats, **opts):
    lfdirstate = lfutil.openlfdirstate(ui, repo)
    s = lfdirstate.status(match_.always(repo.root, repo.getcwd()), [], False,
        False, False)
    (unsure, modified, added, removed, missing, unknown, ignored, clean) = s

    # Need to lock between the standins getting updated and their
    # largefiles getting updated
    wlock = repo.wlock()
    try:
        if opts['check']:
            mod = len(modified) > 0
            for lfile in unsure:
                standin = lfutil.standin(lfile)
                if repo['.'][standin].data().strip() != \
                        lfutil.hashfile(repo.wjoin(lfile)):
                    mod = True
                else:
                    lfdirstate.normal(lfile)
            lfdirstate.write()
            if mod:
                raise util.Abort(_('uncommitted local changes'))
        # XXX handle removed differently
        if not opts['clean']:
            for lfile in unsure + modified + added:
                lfutil.updatestandin(repo, lfutil.standin(lfile))
    finally:
        wlock.release()
    return orig(ui, repo, *pats, **opts)

# Before starting the manifest merge, merge.updates will call
# _checkunknown to check if there are any files in the merged-in
# changeset that collide with unknown files in the working copy.
#
# The largefiles are seen as unknown, so this prevents us from merging
# in a file 'foo' if we already have a largefile with the same name.
#
# The overridden function filters the unknown files by removing any
# largefiles. This makes the merge proceed and we can then handle this
# case further in the overridden manifestmerge function below.
def overridecheckunknownfile(origfn, repo, wctx, mctx, f):
    if lfutil.standin(repo.dirstate.normalize(f)) in wctx:
        return False
    return origfn(repo, wctx, mctx, f)

# The manifest merge handles conflicts on the manifest level. We want
# to handle changes in largefile-ness of files at this level too.
#
# The strategy is to run the original manifestmerge and then process
# the action list it outputs. There are two cases we need to deal with:
#
# 1. Normal file in p1, largefile in p2. Here the largefile is
#    detected via its standin file, which will enter the working copy
#    with a "get" action. It is not "merge" since the standin is all
#    Mercurial is concerned with at this level -- the link to the
#    existing normal file is not relevant here.
#
# 2. Largefile in p1, normal file in p2. Here we get a "merge" action
#    since the largefile will be present in the working copy and
#    different from the normal file in p2. Mercurial therefore
#    triggers a merge action.
#
# In both cases, we prompt the user and emit new actions to either
# remove the standin (if the normal file was kept) or to remove the
# normal file and get the standin (if the largefile was kept). The
# default prompt answer is to use the largefile version since it was
# presumably changed on purpose.
#
# Finally, the merge.applyupdates function will then take care of
# writing the files into the working copy and lfcommands.updatelfiles
# will update the largefiles.
def overridemanifestmerge(origfn, repo, p1, p2, pa, branchmerge, force,
                          partial, acceptremote=False):
    overwrite = force and not branchmerge
    actions = origfn(repo, p1, p2, pa, branchmerge, force, partial,
                     acceptremote)
    processed = []

    for action in actions:
        if overwrite:
            processed.append(action)
            continue
        f, m, args, msg = action

        choices = (_('&Largefile'), _('&Normal file'))

        splitstandin = lfutil.splitstandin(f)
        if (m == "g" and splitstandin is not None and
            splitstandin in p1 and f in p2):
            # Case 1: normal file in the working copy, largefile in
            # the second parent
            lfile = splitstandin
            standin = f
            msg = _('%s has been turned into a largefile\n'
                    'use (l)argefile or keep as (n)ormal file?') % lfile
            if repo.ui.promptchoice(msg, choices, 0) == 0:
                processed.append((lfile, "r", None, msg))
                processed.append((standin, "g", (p2.flags(standin),), msg))
            else:
                processed.append((standin, "r", None, msg))
        elif m == "g" and lfutil.standin(f) in p1 and f in p2:
            # Case 2: largefile in the working copy, normal file in
            # the second parent
            standin = lfutil.standin(f)
            lfile = f
            msg = _('%s has been turned into a normal file\n'
                    'keep as (l)argefile or use (n)ormal file?') % lfile
            if repo.ui.promptchoice(msg, choices, 0) == 0:
                processed.append((lfile, "r", None, msg))
            else:
                processed.append((standin, "r", None, msg))
                processed.append((lfile, "g", (p2.flags(lfile),), msg))
        else:
            processed.append(action)

    return processed

# Override filemerge to prompt the user about how they wish to merge
# largefiles. This will handle identical edits, and copy/rename +
# edit without prompting the user.
def overridefilemerge(origfn, repo, mynode, orig, fcd, fco, fca):
    # Use better variable names here. Because this is a wrapper we cannot
    # change the variable names in the function declaration.
    fcdest, fcother, fcancestor = fcd, fco, fca
    if not lfutil.isstandin(orig):
        return origfn(repo, mynode, orig, fcdest, fcother, fcancestor)
    else:
        if not fcother.cmp(fcdest): # files identical?
            return None

        # backwards, use working dir parent as ancestor
        if fcancestor == fcother:
            fcancestor = fcdest.parents()[0]

        if orig != fcother.path():
            repo.ui.status(_('merging %s and %s to %s\n')
                           % (lfutil.splitstandin(orig),
                              lfutil.splitstandin(fcother.path()),
                              lfutil.splitstandin(fcdest.path())))
        else:
            repo.ui.status(_('merging %s\n')
                           % lfutil.splitstandin(fcdest.path()))

        if fcancestor.path() != fcother.path() and fcother.data() == \
                fcancestor.data():
            return 0
        if fcancestor.path() != fcdest.path() and fcdest.data() == \
                fcancestor.data():
            repo.wwrite(fcdest.path(), fcother.data(), fcother.flags())
            return 0

        if repo.ui.promptchoice(_('largefile %s has a merge conflict\n'
                             'keep (l)ocal or take (o)ther?') %
                             lfutil.splitstandin(orig),
                             (_('&Local'), _('&Other')), 0) == 0:
            return 0
        else:
            repo.wwrite(fcdest.path(), fcother.data(), fcother.flags())
            return 0

# Copy first changes the matchers to match standins instead of
# largefiles.  Then it overrides util.copyfile in that function it
# checks if the destination largefile already exists. It also keeps a
# list of copied files so that the largefiles can be copied and the
# dirstate updated.
def overridecopy(orig, ui, repo, pats, opts, rename=False):
    # doesn't remove largefile on rename
    if len(pats) < 2:
        # this isn't legal, let the original function deal with it
        return orig(ui, repo, pats, opts, rename)

    def makestandin(relpath):
        path = scmutil.canonpath(repo.root, repo.getcwd(), relpath)
        return os.path.join(repo.wjoin(lfutil.standin(path)))

    fullpats = scmutil.expandpats(pats)
    dest = fullpats[-1]

    if os.path.isdir(dest):
        if not os.path.isdir(makestandin(dest)):
            os.makedirs(makestandin(dest))
    # This could copy both lfiles and normal files in one command,
    # but we don't want to do that. First replace their matcher to
    # only match normal files and run it, then replace it to just
    # match largefiles and run it again.
    nonormalfiles = False
    nolfiles = False
    try:
        try:
            installnormalfilesmatchfn(repo[None].manifest())
            result = orig(ui, repo, pats, opts, rename)
        except util.Abort, e:
            if str(e) != _('no files to copy'):
                raise e
            else:
                nonormalfiles = True
            result = 0
    finally:
        restorematchfn()

    # The first rename can cause our current working directory to be removed.
    # In that case there is nothing left to copy/rename so just quit.
    try:
        repo.getcwd()
    except OSError:
        return result

    try:
        try:
            # When we call orig below it creates the standins but we don't add
            # them to the dir state until later so lock during that time.
            wlock = repo.wlock()

            manifest = repo[None].manifest()
            oldmatch = None # for the closure
            def overridematch(ctx, pats=[], opts={}, globbed=False,
                    default='relpath'):
                newpats = []
                # The patterns were previously mangled to add the standin
                # directory; we need to remove that now
                for pat in pats:
                    if match_.patkind(pat) is None and lfutil.shortname in pat:
                        newpats.append(pat.replace(lfutil.shortname, ''))
                    else:
                        newpats.append(pat)
                match = oldmatch(ctx, newpats, opts, globbed, default)
                m = copy.copy(match)
                lfile = lambda f: lfutil.standin(f) in manifest
                m._files = [lfutil.standin(f) for f in m._files if lfile(f)]
                m._fmap = set(m._files)
                m._always = False
                origmatchfn = m.matchfn
                m.matchfn = lambda f: (lfutil.isstandin(f) and
                                    (f in manifest) and
                                    origmatchfn(lfutil.splitstandin(f)) or
                                    None)
                return m
            oldmatch = installmatchfn(overridematch)
            listpats = []
            for pat in pats:
                if match_.patkind(pat) is not None:
                    listpats.append(pat)
                else:
                    listpats.append(makestandin(pat))

            try:
                origcopyfile = util.copyfile
                copiedfiles = []
                def overridecopyfile(src, dest):
                    if (lfutil.shortname in src and
                        dest.startswith(repo.wjoin(lfutil.shortname))):
                        destlfile = dest.replace(lfutil.shortname, '')
                        if not opts['force'] and os.path.exists(destlfile):
                            raise IOError('',
                                _('destination largefile already exists'))
                    copiedfiles.append((src, dest))
                    origcopyfile(src, dest)

                util.copyfile = overridecopyfile
                result += orig(ui, repo, listpats, opts, rename)
            finally:
                util.copyfile = origcopyfile

            lfdirstate = lfutil.openlfdirstate(ui, repo)
            for (src, dest) in copiedfiles:
                if (lfutil.shortname in src and
                    dest.startswith(repo.wjoin(lfutil.shortname))):
                    srclfile = src.replace(repo.wjoin(lfutil.standin('')), '')
                    destlfile = dest.replace(repo.wjoin(lfutil.standin('')), '')
                    destlfiledir = os.path.dirname(repo.wjoin(destlfile)) or '.'
                    if not os.path.isdir(destlfiledir):
                        os.makedirs(destlfiledir)
                    if rename:
                        os.rename(repo.wjoin(srclfile), repo.wjoin(destlfile))
                        lfdirstate.remove(srclfile)
                    else:
                        util.copyfile(repo.wjoin(srclfile),
                                      repo.wjoin(destlfile))

                    lfdirstate.add(destlfile)
            lfdirstate.write()
        except util.Abort, e:
            if str(e) != _('no files to copy'):
                raise e
            else:
                nolfiles = True
    finally:
        restorematchfn()
        wlock.release()

    if nolfiles and nonormalfiles:
        raise util.Abort(_('no files to copy'))

    return result

# When the user calls revert, we have to be careful to not revert any
# changes to other largefiles accidentally. This means we have to keep
# track of the largefiles that are being reverted so we only pull down
# the necessary largefiles.
#
# Standins are only updated (to match the hash of largefiles) before
# commits. Update the standins then run the original revert, changing
# the matcher to hit standins instead of largefiles. Based on the
# resulting standins update the largefiles. Then return the standins
# to their proper state
def overriderevert(orig, ui, repo, *pats, **opts):
    # Because we put the standins in a bad state (by updating them)
    # and then return them to a correct state we need to lock to
    # prevent others from changing them in their incorrect state.
    wlock = repo.wlock()
    try:
        lfdirstate = lfutil.openlfdirstate(ui, repo)
        (modified, added, removed, missing, unknown, ignored, clean) = \
            lfutil.lfdirstatestatus(lfdirstate, repo, repo['.'].rev())
        lfdirstate.write()
        for lfile in modified:
            lfutil.updatestandin(repo, lfutil.standin(lfile))
        for lfile in missing:
            if (os.path.exists(repo.wjoin(lfutil.standin(lfile)))):
                os.unlink(repo.wjoin(lfutil.standin(lfile)))

        try:
            ctx = scmutil.revsingle(repo, opts.get('rev'))
            oldmatch = None # for the closure
            def overridematch(ctx, pats=[], opts={}, globbed=False,
                    default='relpath'):
                match = oldmatch(ctx, pats, opts, globbed, default)
                m = copy.copy(match)
                def tostandin(f):
                    if lfutil.standin(f) in ctx:
                        return lfutil.standin(f)
                    elif lfutil.standin(f) in repo[None]:
                        return None
                    return f
                m._files = [tostandin(f) for f in m._files]
                m._files = [f for f in m._files if f is not None]
                m._fmap = set(m._files)
                m._always = False
                origmatchfn = m.matchfn
                def matchfn(f):
                    if lfutil.isstandin(f):
                        # We need to keep track of what largefiles are being
                        # matched so we know which ones to update later --
                        # otherwise we accidentally revert changes to other
                        # largefiles. This is repo-specific, so duckpunch the
                        # repo object to keep the list of largefiles for us
                        # later.
                        if origmatchfn(lfutil.splitstandin(f)) and \
                                (f in repo[None] or f in ctx):
                            lfileslist = getattr(repo, '_lfilestoupdate', [])
                            lfileslist.append(lfutil.splitstandin(f))
                            repo._lfilestoupdate = lfileslist
                            return True
                        else:
                            return False
                    return origmatchfn(f)
                m.matchfn = matchfn
                return m
            oldmatch = installmatchfn(overridematch)
            scmutil.match
            matches = overridematch(repo[None], pats, opts)
            orig(ui, repo, *pats, **opts)
        finally:
            restorematchfn()
        lfileslist = getattr(repo, '_lfilestoupdate', [])
        lfcommands.updatelfiles(ui, repo, filelist=lfileslist,
                                printmessage=False)

        # empty out the largefiles list so we start fresh next time
        repo._lfilestoupdate = []
        for lfile in modified:
            if lfile in lfileslist:
                if os.path.exists(repo.wjoin(lfutil.standin(lfile))) and lfile\
                        in repo['.']:
                    lfutil.writestandin(repo, lfutil.standin(lfile),
                        repo['.'][lfile].data().strip(),
                        'x' in repo['.'][lfile].flags())
        lfdirstate = lfutil.openlfdirstate(ui, repo)
        for lfile in added:
            standin = lfutil.standin(lfile)
            if standin not in ctx and (standin in matches or opts.get('all')):
                if lfile in lfdirstate:
                    lfdirstate.drop(lfile)
                util.unlinkpath(repo.wjoin(standin))
        lfdirstate.write()
    finally:
        wlock.release()

def hgupdaterepo(orig, repo, node, overwrite):
    if not overwrite:
        # Only call updatelfiles on the standins that have changed to save time
        oldstandins = lfutil.getstandinsstate(repo)

    result = orig(repo, node, overwrite)

    filelist = None
    if not overwrite:
        newstandins = lfutil.getstandinsstate(repo)
        filelist = lfutil.getlfilestoupdate(oldstandins, newstandins)
    lfcommands.updatelfiles(repo.ui, repo, filelist=filelist)
    return result

def hgmerge(orig, repo, node, force=None, remind=True):
    result = orig(repo, node, force, remind)
    lfcommands.updatelfiles(repo.ui, repo)
    return result

# When we rebase a repository with remotely changed largefiles, we need to
# take some extra care so that the largefiles are correctly updated in the
# working copy
def overridepull(orig, ui, repo, source=None, **opts):
    revsprepull = len(repo)
    if not source:
        source = 'default'
    repo.lfpullsource = source
    if opts.get('rebase', False):
        repo._isrebasing = True
        try:
            if opts.get('update'):
                del opts['update']
                ui.debug('--update and --rebase are not compatible, ignoring '
                         'the update flag\n')
            del opts['rebase']
            cmdutil.bailifchanged(repo)
            origpostincoming = commands.postincoming
            def _dummy(*args, **kwargs):
                pass
            commands.postincoming = _dummy
            try:
                result = commands.pull(ui, repo, source, **opts)
            finally:
                commands.postincoming = origpostincoming
            revspostpull = len(repo)
            if revspostpull > revsprepull:
                result = result or rebase.rebase(ui, repo)
        finally:
            repo._isrebasing = False
    else:
        result = orig(ui, repo, source, **opts)
    revspostpull = len(repo)
    lfrevs = opts.get('lfrev', [])
    if opts.get('all_largefiles'):
        lfrevs.append('pulled()')
    if lfrevs and revspostpull > revsprepull:
        numcached = 0
        repo.firstpulled = revsprepull # for pulled() revset expression
        try:
            for rev in scmutil.revrange(repo, lfrevs):
                ui.note(_('pulling largefiles for revision %s\n') % rev)
                (cached, missing) = lfcommands.cachelfiles(ui, repo, rev)
                numcached += len(cached)
        finally:
            del repo.firstpulled
        ui.status(_("%d largefiles cached\n") % numcached)
    return result

def pulledrevsetsymbol(repo, subset, x):
    """``pulled()``
    Changesets that just has been pulled.

    Only available with largefiles from pull --lfrev expressions.

    .. container:: verbose

      Some examples:

      - pull largefiles for all new changesets::

          hg pull -lfrev "pulled()"

      - pull largefiles for all new branch heads::

          hg pull -lfrev "head(pulled()) and not closed()"

    """

    try:
        firstpulled = repo.firstpulled
    except AttributeError:
        raise util.Abort(_("pulled() only available in --lfrev"))
    return [r for r in subset if r >= firstpulled]

def overrideclone(orig, ui, source, dest=None, **opts):
    d = dest
    if d is None:
        d = hg.defaultdest(source)
    if opts.get('all_largefiles') and not hg.islocal(d):
            raise util.Abort(_(
            '--all-largefiles is incompatible with non-local destination %s' %
            d))

    return orig(ui, source, dest, **opts)

def hgclone(orig, ui, opts, *args, **kwargs):
    result = orig(ui, opts, *args, **kwargs)

    if result is not None:
        sourcerepo, destrepo = result
        repo = destrepo.local()

        # Caching is implicitly limited to 'rev' option, since the dest repo was
        # truncated at that point.  The user may expect a download count with
        # this option, so attempt whether or not this is a largefile repo.
        if opts.get('all_largefiles'):
            success, missing = lfcommands.downloadlfiles(ui, repo, None)

            if missing != 0:
                return None

    return result

def overriderebase(orig, ui, repo, **opts):
    repo._isrebasing = True
    try:
        return orig(ui, repo, **opts)
    finally:
        repo._isrebasing = False

def overridearchive(orig, repo, dest, node, kind, decode=True, matchfn=None,
            prefix=None, mtime=None, subrepos=None):
    # No need to lock because we are only reading history and
    # largefile caches, neither of which are modified.
    lfcommands.cachelfiles(repo.ui, repo, node)

    if kind not in archival.archivers:
        raise util.Abort(_("unknown archive type '%s'") % kind)

    ctx = repo[node]

    if kind == 'files':
        if prefix:
            raise util.Abort(
                _('cannot give prefix when archiving to files'))
    else:
        prefix = archival.tidyprefix(dest, kind, prefix)

    def write(name, mode, islink, getdata):
        if matchfn and not matchfn(name):
            return
        data = getdata()
        if decode:
            data = repo.wwritedata(name, data)
        archiver.addfile(prefix + name, mode, islink, data)

    archiver = archival.archivers[kind](dest, mtime or ctx.date()[0])

    if repo.ui.configbool("ui", "archivemeta", True):
        def metadata():
            base = 'repo: %s\nnode: %s\nbranch: %s\n' % (
                hex(repo.changelog.node(0)), hex(node), ctx.branch())

            tags = ''.join('tag: %s\n' % t for t in ctx.tags()
                           if repo.tagtype(t) == 'global')
            if not tags:
                repo.ui.pushbuffer()
                opts = {'template': '{latesttag}\n{latesttagdistance}',
                        'style': '', 'patch': None, 'git': None}
                cmdutil.show_changeset(repo.ui, repo, opts).show(ctx)
                ltags, dist = repo.ui.popbuffer().split('\n')
                tags = ''.join('latesttag: %s\n' % t for t in ltags.split(':'))
                tags += 'latesttagdistance: %s\n' % dist

            return base + tags

        write('.hg_archival.txt', 0644, False, metadata)

    for f in ctx:
        ff = ctx.flags(f)
        getdata = ctx[f].data
        if lfutil.isstandin(f):
            path = lfutil.findfile(repo, getdata().strip())
            if path is None:
                raise util.Abort(
                    _('largefile %s not found in repo store or system cache')
                    % lfutil.splitstandin(f))
            f = lfutil.splitstandin(f)

            def getdatafn():
                fd = None
                try:
                    fd = open(path, 'rb')
                    return fd.read()
                finally:
                    if fd:
                        fd.close()

            getdata = getdatafn
        write(f, 'x' in ff and 0755 or 0644, 'l' in ff, getdata)

    if subrepos:
        for subpath in sorted(ctx.substate):
            sub = ctx.sub(subpath)
            submatch = match_.narrowmatcher(subpath, matchfn)
            sub.archive(repo.ui, archiver, prefix, submatch)

    archiver.done()

def hgsubrepoarchive(orig, repo, ui, archiver, prefix, match=None):
    repo._get(repo._state + ('hg',))
    rev = repo._state[1]
    ctx = repo._repo[rev]

    lfcommands.cachelfiles(ui, repo._repo, ctx.node())

    def write(name, mode, islink, getdata):
        # At this point, the standin has been replaced with the largefile name,
        # so the normal matcher works here without the lfutil variants.
        if match and not match(f):
            return
        data = getdata()

        archiver.addfile(prefix + repo._path + '/' + name, mode, islink, data)

    for f in ctx:
        ff = ctx.flags(f)
        getdata = ctx[f].data
        if lfutil.isstandin(f):
            path = lfutil.findfile(repo._repo, getdata().strip())
            if path is None:
                raise util.Abort(
                    _('largefile %s not found in repo store or system cache')
                    % lfutil.splitstandin(f))
            f = lfutil.splitstandin(f)

            def getdatafn():
                fd = None
                try:
                    fd = open(os.path.join(prefix, path), 'rb')
                    return fd.read()
                finally:
                    if fd:
                        fd.close()

            getdata = getdatafn

        write(f, 'x' in ff and 0755 or 0644, 'l' in ff, getdata)

    for subpath in sorted(ctx.substate):
        sub = ctx.sub(subpath)
        submatch = match_.narrowmatcher(subpath, match)
        sub.archive(ui, archiver, os.path.join(prefix, repo._path) + '/',
                    submatch)

# If a largefile is modified, the change is not reflected in its
# standin until a commit. cmdutil.bailifchanged() raises an exception
# if the repo has uncommitted changes. Wrap it to also check if
# largefiles were changed. This is used by bisect and backout.
def overridebailifchanged(orig, repo):
    orig(repo)
    repo.lfstatus = True
    modified, added, removed, deleted = repo.status()[:4]
    repo.lfstatus = False
    if modified or added or removed or deleted:
        raise util.Abort(_('outstanding uncommitted changes'))

# Fetch doesn't use cmdutil.bailifchanged so override it to add the check
def overridefetch(orig, ui, repo, *pats, **opts):
    repo.lfstatus = True
    modified, added, removed, deleted = repo.status()[:4]
    repo.lfstatus = False
    if modified or added or removed or deleted:
        raise util.Abort(_('outstanding uncommitted changes'))
    return orig(ui, repo, *pats, **opts)

def overrideforget(orig, ui, repo, *pats, **opts):
    installnormalfilesmatchfn(repo[None].manifest())
    result = orig(ui, repo, *pats, **opts)
    restorematchfn()
    m = scmutil.match(repo[None], pats, opts)

    try:
        repo.lfstatus = True
        s = repo.status(match=m, clean=True)
    finally:
        repo.lfstatus = False
    forget = sorted(s[0] + s[1] + s[3] + s[6])
    forget = [f for f in forget if lfutil.standin(f) in repo[None].manifest()]

    for f in forget:
        if lfutil.standin(f) not in repo.dirstate and not \
                os.path.isdir(m.rel(lfutil.standin(f))):
            ui.warn(_('not removing %s: file is already untracked\n')
                    % m.rel(f))
            result = 1

    for f in forget:
        if ui.verbose or not m.exact(f):
            ui.status(_('removing %s\n') % m.rel(f))

    # Need to lock because standin files are deleted then removed from the
    # repository and we could race in-between.
    wlock = repo.wlock()
    try:
        lfdirstate = lfutil.openlfdirstate(ui, repo)
        for f in forget:
            if lfdirstate[f] == 'a':
                lfdirstate.drop(f)
            else:
                lfdirstate.remove(f)
        lfdirstate.write()
        standins = [lfutil.standin(f) for f in forget]
        for f in standins:
            util.unlinkpath(repo.wjoin(f), ignoremissing=True)
        repo[None].forget(standins)
    finally:
        wlock.release()

    return result

def getoutgoinglfiles(ui, repo, dest=None, **opts):
    dest = ui.expandpath(dest or 'default-push', dest or 'default')
    dest, branches = hg.parseurl(dest, opts.get('branch'))
    revs, checkout = hg.addbranchrevs(repo, repo, branches, opts.get('rev'))
    if revs:
        revs = [repo.lookup(rev) for rev in scmutil.revrange(repo, revs)]

    try:
        remote = hg.peer(repo, opts, dest)
    except error.RepoError:
        return None
    outgoing = discovery.findcommonoutgoing(repo, remote.peer(), force=False)
    if not outgoing.missing:
        return outgoing.missing
    o = repo.changelog.nodesbetween(outgoing.missing, revs)[0]
    if opts.get('newest_first'):
        o.reverse()

    toupload = set()
    for n in o:
        parents = [p for p in repo.changelog.parents(n) if p != node.nullid]
        ctx = repo[n]
        files = set(ctx.files())
        if len(parents) == 2:
            mc = ctx.manifest()
            mp1 = ctx.parents()[0].manifest()
            mp2 = ctx.parents()[1].manifest()
            for f in mp1:
                if f not in mc:
                        files.add(f)
            for f in mp2:
                if f not in mc:
                    files.add(f)
            for f in mc:
                if mc[f] != mp1.get(f, None) or mc[f] != mp2.get(f, None):
                    files.add(f)
        toupload = toupload.union(
            set([f for f in files if lfutil.isstandin(f) and f in ctx]))
    return sorted(toupload)

def overrideoutgoing(orig, ui, repo, dest=None, **opts):
    result = orig(ui, repo, dest, **opts)

    if opts.pop('large', None):
        toupload = getoutgoinglfiles(ui, repo, dest, **opts)
        if toupload is None:
            ui.status(_('largefiles: No remote repo\n'))
        elif not toupload:
            ui.status(_('largefiles: no files to upload\n'))
        else:
            ui.status(_('largefiles to upload:\n'))
            for file in toupload:
                ui.status(lfutil.splitstandin(file) + '\n')
            ui.status('\n')

    return result

def overridesummary(orig, ui, repo, *pats, **opts):
    try:
        repo.lfstatus = True
        orig(ui, repo, *pats, **opts)
    finally:
        repo.lfstatus = False

    if opts.pop('large', None):
        toupload = getoutgoinglfiles(ui, repo, None, **opts)
        if toupload is None:
            # i18n: column positioning for "hg summary"
            ui.status(_('largefiles: (no remote repo)\n'))
        elif not toupload:
            # i18n: column positioning for "hg summary"
            ui.status(_('largefiles: (no files to upload)\n'))
        else:
            # i18n: column positioning for "hg summary"
            ui.status(_('largefiles: %d to upload\n') % len(toupload))

def scmutiladdremove(orig, repo, pats=[], opts={}, dry_run=None,
                     similarity=None):
    if not lfutil.islfilesrepo(repo):
        return orig(repo, pats, opts, dry_run, similarity)
    # Get the list of missing largefiles so we can remove them
    lfdirstate = lfutil.openlfdirstate(repo.ui, repo)
    s = lfdirstate.status(match_.always(repo.root, repo.getcwd()), [], False,
        False, False)
    (unsure, modified, added, removed, missing, unknown, ignored, clean) = s

    # Call into the normal remove code, but the removing of the standin, we want
    # to have handled by original addremove.  Monkey patching here makes sure
    # we don't remove the standin in the largefiles code, preventing a very
    # confused state later.
    if missing:
        m = [repo.wjoin(f) for f in missing]
        repo._isaddremove = True
        removelargefiles(repo.ui, repo, *m, **opts)
        repo._isaddremove = False
    # Call into the normal add code, and any files that *should* be added as
    # largefiles will be
    addlargefiles(repo.ui, repo, *pats, **opts)
    # Now that we've handled largefiles, hand off to the original addremove
    # function to take care of the rest.  Make sure it doesn't do anything with
    # largefiles by installing a matcher that will ignore them.
    installnormalfilesmatchfn(repo[None].manifest())
    result = orig(repo, pats, opts, dry_run, similarity)
    restorematchfn()
    return result

# Calling purge with --all will cause the largefiles to be deleted.
# Override repo.status to prevent this from happening.
def overridepurge(orig, ui, repo, *dirs, **opts):
    # XXX large file status is buggy when used on repo proxy.
    # XXX this needs to be investigate.
    repo = repo.unfiltered()
    oldstatus = repo.status
    def overridestatus(node1='.', node2=None, match=None, ignored=False,
                        clean=False, unknown=False, listsubrepos=False):
        r = oldstatus(node1, node2, match, ignored, clean, unknown,
                      listsubrepos)
        lfdirstate = lfutil.openlfdirstate(ui, repo)
        modified, added, removed, deleted, unknown, ignored, clean = r
        unknown = [f for f in unknown if lfdirstate[f] == '?']
        ignored = [f for f in ignored if lfdirstate[f] == '?']
        return modified, added, removed, deleted, unknown, ignored, clean
    repo.status = overridestatus
    orig(ui, repo, *dirs, **opts)
    repo.status = oldstatus

def overriderollback(orig, ui, repo, **opts):
    result = orig(ui, repo, **opts)
    merge.update(repo, node=None, branchmerge=False, force=True,
        partial=lfutil.isstandin)
    wlock = repo.wlock()
    try:
        lfdirstate = lfutil.openlfdirstate(ui, repo)
        lfiles = lfutil.listlfiles(repo)
        oldlfiles = lfutil.listlfiles(repo, repo[None].parents()[0].rev())
        for file in lfiles:
            if file in oldlfiles:
                lfdirstate.normallookup(file)
            else:
                lfdirstate.add(file)
        lfdirstate.write()
    finally:
        wlock.release()
    return result

def overridetransplant(orig, ui, repo, *revs, **opts):
    try:
        oldstandins = lfutil.getstandinsstate(repo)
        repo._istransplanting = True
        result = orig(ui, repo, *revs, **opts)
        newstandins = lfutil.getstandinsstate(repo)
        filelist = lfutil.getlfilestoupdate(oldstandins, newstandins)
        lfcommands.updatelfiles(repo.ui, repo, filelist=filelist,
                                printmessage=True)
    finally:
        repo._istransplanting = False
    return result

def overridecat(orig, ui, repo, file1, *pats, **opts):
    ctx = scmutil.revsingle(repo, opts.get('rev'))
    err = 1
    notbad = set()
    m = scmutil.match(ctx, (file1,) + pats, opts)
    origmatchfn = m.matchfn
    def lfmatchfn(f):
        lf = lfutil.splitstandin(f)
        if lf is None:
            return origmatchfn(f)
        notbad.add(lf)
        return origmatchfn(lf)
    m.matchfn = lfmatchfn
    origbadfn = m.bad
    def lfbadfn(f, msg):
        if not f in notbad:
            return origbadfn(f, msg)
    m.bad = lfbadfn
    for f in ctx.walk(m):
        fp = cmdutil.makefileobj(repo, opts.get('output'), ctx.node(),
                                 pathname=f)
        lf = lfutil.splitstandin(f)
        if lf is None:
            # duplicating unreachable code from commands.cat
            data = ctx[f].data()
            if opts.get('decode'):
                data = repo.wwritedata(f, data)
            fp.write(data)
        else:
            hash = lfutil.readstandin(repo, lf, ctx.rev())
            if not lfutil.inusercache(repo.ui, hash):
                store = basestore._openstore(repo)
                success, missing = store.get([(lf, hash)])
                if len(success) != 1:
                    raise util.Abort(
                        _('largefile %s is not in cache and could not be '
                          'downloaded')  % lf)
            path = lfutil.usercachepath(repo.ui, hash)
            fpin = open(path, "rb")
            for chunk in util.filechunkiter(fpin, 128 * 1024):
                fp.write(chunk)
            fpin.close()
        fp.close()
        err = 0
    return err

def mercurialsinkbefore(orig, sink):
    sink.repo._isconverting = True
    orig(sink)

def mercurialsinkafter(orig, sink):
    sink.repo._isconverting = False
    orig(sink)
