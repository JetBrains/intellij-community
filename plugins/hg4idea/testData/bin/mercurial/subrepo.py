# subrepo.py - sub-repository classes and factory
#
# Copyright 2009-2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import copy
import errno
import os
import re
import stat
import subprocess
import sys
import tarfile
import xml.dom.minidom

from .i18n import _
from .node import (
    bin,
    hex,
    short,
)
from . import (
    cmdutil,
    encoding,
    error,
    exchange,
    logcmdutil,
    match as matchmod,
    merge as merge,
    pathutil,
    phases,
    pycompat,
    scmutil,
    subrepoutil,
    util,
    vfs as vfsmod,
)
from .utils import (
    dateutil,
    hashutil,
    procutil,
    urlutil,
)

hg = None
reporelpath = subrepoutil.reporelpath
subrelpath = subrepoutil.subrelpath
_abssource = subrepoutil._abssource
propertycache = util.propertycache


def _expandedabspath(path):
    """
    get a path or url and if it is a path expand it and return an absolute path
    """
    expandedpath = urlutil.urllocalpath(util.expandpath(path))
    u = urlutil.url(expandedpath)
    if not u.scheme:
        path = util.normpath(util.abspath(u.path))
    return path


def _getstorehashcachename(remotepath):
    '''get a unique filename for the store hash cache of a remote repository'''
    return hex(hashutil.sha1(_expandedabspath(remotepath)).digest())[0:12]


class SubrepoAbort(error.Abort):
    """Exception class used to avoid handling a subrepo error more than once"""

    def __init__(self, *args, **kw):
        self.subrepo = kw.pop('subrepo', None)
        self.cause = kw.pop('cause', None)
        error.Abort.__init__(self, *args, **kw)


def annotatesubrepoerror(func):
    def decoratedmethod(self, *args, **kargs):
        try:
            res = func(self, *args, **kargs)
        except SubrepoAbort as ex:
            # This exception has already been handled
            raise ex
        except error.Abort as ex:
            subrepo = subrelpath(self)
            errormsg = (
                ex.message + b' ' + _(b'(in subrepository "%s")') % subrepo
            )
            # avoid handling this exception by raising a SubrepoAbort exception
            raise SubrepoAbort(
                errormsg, hint=ex.hint, subrepo=subrepo, cause=sys.exc_info()
            )
        return res

    return decoratedmethod


def _updateprompt(ui, sub, dirty, local, remote):
    if dirty:
        msg = _(
            b' subrepository sources for %s differ\n'
            b'you can use (l)ocal source (%s) or (r)emote source (%s).\n'
            b'what do you want to do?'
            b'$$ &Local $$ &Remote'
        ) % (subrelpath(sub), local, remote)
    else:
        msg = _(
            b' subrepository sources for %s differ (in checked out '
            b'version)\n'
            b'you can use (l)ocal source (%s) or (r)emote source (%s).\n'
            b'what do you want to do?'
            b'$$ &Local $$ &Remote'
        ) % (subrelpath(sub), local, remote)
    return ui.promptchoice(msg, 0)


def _sanitize(ui, vfs, ignore):
    for dirname, dirs, names in vfs.walk():
        for i, d in enumerate(dirs):
            if d.lower() == ignore:
                del dirs[i]
                break
        if vfs.basename(dirname).lower() != b'.hg':
            continue
        for f in names:
            if f.lower() == b'hgrc':
                ui.warn(
                    _(
                        b"warning: removing potentially hostile 'hgrc' "
                        b"in '%s'\n"
                    )
                    % vfs.join(dirname)
                )
                vfs.unlink(vfs.reljoin(dirname, f))


def _auditsubrepopath(repo, path):
    # sanity check for potentially unsafe paths such as '~' and '$FOO'
    if path.startswith(b'~') or b'$' in path or util.expandpath(path) != path:
        raise error.Abort(
            _(b'subrepo path contains illegal component: %s') % path
        )
    # auditor doesn't check if the path itself is a symlink
    pathutil.pathauditor(repo.root)(path)
    if repo.wvfs.islink(path):
        raise error.Abort(_(b"subrepo '%s' traverses symbolic link") % path)


SUBREPO_ALLOWED_DEFAULTS = {
    b'hg': True,
    b'git': False,
    b'svn': False,
}


def _checktype(ui, kind):
    # subrepos.allowed is a master kill switch. If disabled, subrepos are
    # disabled period.
    if not ui.configbool(b'subrepos', b'allowed', True):
        raise error.Abort(
            _(b'subrepos not enabled'),
            hint=_(b"see 'hg help config.subrepos' for details"),
        )

    default = SUBREPO_ALLOWED_DEFAULTS.get(kind, False)
    if not ui.configbool(b'subrepos', b'%s:allowed' % kind, default):
        raise error.Abort(
            _(b'%s subrepos not allowed') % kind,
            hint=_(b"see 'hg help config.subrepos' for details"),
        )

    if kind not in types:
        raise error.Abort(_(b'unknown subrepo type %s') % kind)


def subrepo(ctx, path, allowwdir=False, allowcreate=True):
    """return instance of the right subrepo class for subrepo in path"""
    # subrepo inherently violates our import layering rules
    # because it wants to make repo objects from deep inside the stack
    # so we manually delay the circular imports to not break
    # scripts that don't use our demand-loading
    global hg
    from . import hg as h

    hg = h

    repo = ctx.repo()
    _auditsubrepopath(repo, path)
    state = ctx.substate[path]
    _checktype(repo.ui, state[2])
    if allowwdir:
        state = (state[0], ctx.subrev(path), state[2])
    return types[state[2]](ctx, path, state[:2], allowcreate)


def nullsubrepo(ctx, path, pctx):
    """return an empty subrepo in pctx for the extant subrepo in ctx"""
    # subrepo inherently violates our import layering rules
    # because it wants to make repo objects from deep inside the stack
    # so we manually delay the circular imports to not break
    # scripts that don't use our demand-loading
    global hg
    from . import hg as h

    hg = h

    repo = ctx.repo()
    _auditsubrepopath(repo, path)
    state = ctx.substate[path]
    _checktype(repo.ui, state[2])
    subrev = b''
    if state[2] == b'hg':
        subrev = b"0" * 40
    return types[state[2]](pctx, path, (state[0], subrev), True)


# subrepo classes need to implement the following abstract class:


class abstractsubrepo(object):
    def __init__(self, ctx, path):
        """Initialize abstractsubrepo part

        ``ctx`` is the context referring this subrepository in the
        parent repository.

        ``path`` is the path to this subrepository as seen from
        innermost repository.
        """
        self.ui = ctx.repo().ui
        self._ctx = ctx
        self._path = path

    def addwebdirpath(self, serverpath, webconf):
        """Add the hgwebdir entries for this subrepo, and any of its subrepos.

        ``serverpath`` is the path component of the URL for this repo.

        ``webconf`` is the dictionary of hgwebdir entries.
        """
        pass

    def storeclean(self, path):
        """
        returns true if the repository has not changed since it was last
        cloned from or pushed to a given repository.
        """
        return False

    def dirty(self, ignoreupdate=False, missing=False):
        """returns true if the dirstate of the subrepo is dirty or does not
        match current stored state. If ignoreupdate is true, only check
        whether the subrepo has uncommitted changes in its dirstate.  If missing
        is true, check for deleted files.
        """
        raise NotImplementedError

    def dirtyreason(self, ignoreupdate=False, missing=False):
        """return reason string if it is ``dirty()``

        Returned string should have enough information for the message
        of exception.

        This returns None, otherwise.
        """
        if self.dirty(ignoreupdate=ignoreupdate, missing=missing):
            return _(b'uncommitted changes in subrepository "%s"') % subrelpath(
                self
            )

    def bailifchanged(self, ignoreupdate=False, hint=None):
        """raise Abort if subrepository is ``dirty()``"""
        dirtyreason = self.dirtyreason(ignoreupdate=ignoreupdate, missing=True)
        if dirtyreason:
            raise error.Abort(dirtyreason, hint=hint)

    def basestate(self):
        """current working directory base state, disregarding .hgsubstate
        state and working directory modifications"""
        raise NotImplementedError

    def checknested(self, path):
        """check if path is a subrepository within this repository"""
        return False

    def commit(self, text, user, date):
        """commit the current changes to the subrepo with the given
        log message. Use given user and date if possible. Return the
        new state of the subrepo.
        """
        raise NotImplementedError

    def phase(self, state):
        """returns phase of specified state in the subrepository."""
        return phases.public

    def remove(self):
        """remove the subrepo

        (should verify the dirstate is not dirty first)
        """
        raise NotImplementedError

    def get(self, state, overwrite=False):
        """run whatever commands are needed to put the subrepo into
        this state
        """
        raise NotImplementedError

    def merge(self, state):
        """merge currently-saved state with the new state."""
        raise NotImplementedError

    def push(self, opts):
        """perform whatever action is analogous to 'hg push'

        This may be a no-op on some systems.
        """
        raise NotImplementedError

    def add(self, ui, match, prefix, uipathfn, explicitonly, **opts):
        return []

    def addremove(self, matcher, prefix, uipathfn, opts):
        self.ui.warn(b"%s: %s" % (prefix, _(b"addremove is not supported")))
        return 1

    def cat(self, match, fm, fntemplate, prefix, **opts):
        return 1

    def status(self, rev2, **opts):
        return scmutil.status([], [], [], [], [], [], [])

    def diff(self, ui, diffopts, node2, match, prefix, **opts):
        pass

    def outgoing(self, ui, dest, opts):
        return 1

    def incoming(self, ui, source, opts):
        return 1

    def files(self):
        """return filename iterator"""
        raise NotImplementedError

    def filedata(self, name, decode):
        """return file data, optionally passed through repo decoders"""
        raise NotImplementedError

    def fileflags(self, name):
        """return file flags"""
        return b''

    def matchfileset(self, cwd, expr, badfn=None):
        """Resolve the fileset expression for this repo"""
        return matchmod.never(badfn=badfn)

    def printfiles(self, ui, m, uipathfn, fm, fmt, subrepos):
        """handle the files command for this subrepo"""
        return 1

    def archive(self, archiver, prefix, match=None, decode=True):
        if match is not None:
            files = [f for f in self.files() if match(f)]
        else:
            files = self.files()
        total = len(files)
        relpath = subrelpath(self)
        progress = self.ui.makeprogress(
            _(b'archiving (%s)') % relpath, unit=_(b'files'), total=total
        )
        progress.update(0)
        for name in files:
            flags = self.fileflags(name)
            mode = b'x' in flags and 0o755 or 0o644
            symlink = b'l' in flags
            archiver.addfile(
                prefix + name, mode, symlink, self.filedata(name, decode)
            )
            progress.increment()
        progress.complete()
        return total

    def walk(self, match):
        """
        walk recursively through the directory tree, finding all files
        matched by the match function
        """

    def forget(self, match, prefix, uipathfn, dryrun, interactive):
        return ([], [])

    def removefiles(
        self,
        matcher,
        prefix,
        uipathfn,
        after,
        force,
        subrepos,
        dryrun,
        warnings,
    ):
        """remove the matched files from the subrepository and the filesystem,
        possibly by force and/or after the file has been removed from the
        filesystem.  Return 0 on success, 1 on any warning.
        """
        warnings.append(
            _(b"warning: removefiles not implemented (%s)") % self._path
        )
        return 1

    def revert(self, substate, *pats, **opts):
        self.ui.warn(
            _(b'%s: reverting %s subrepos is unsupported\n')
            % (substate[0], substate[2])
        )
        return []

    def shortid(self, revid):
        return revid

    def unshare(self):
        """
        convert this repository from shared to normal storage.
        """

    def verify(self, onpush=False):
        """verify the revision of this repository that is held in `_state` is
        present and not hidden.  Return 0 on success or warning, 1 on any
        error.  In the case of ``onpush``, warnings or errors will raise an
        exception if the result of pushing would be a broken remote repository.
        """
        return 0

    @propertycache
    def wvfs(self):
        """return vfs to access the working directory of this subrepository"""
        return vfsmod.vfs(self._ctx.repo().wvfs.join(self._path))

    @propertycache
    def _relpath(self):
        """return path to this subrepository as seen from outermost repository"""
        return self.wvfs.reljoin(reporelpath(self._ctx.repo()), self._path)


class hgsubrepo(abstractsubrepo):
    def __init__(self, ctx, path, state, allowcreate):
        super(hgsubrepo, self).__init__(ctx, path)
        self._state = state
        r = ctx.repo()
        root = r.wjoin(util.localpath(path))
        create = allowcreate and not r.wvfs.exists(b'%s/.hg' % path)
        # repository constructor does expand variables in path, which is
        # unsafe since subrepo path might come from untrusted source.
        norm_root = os.path.normcase(root)
        real_root = os.path.normcase(os.path.realpath(util.expandpath(root)))
        if real_root != norm_root:
            raise error.Abort(
                _(b'subrepo path contains illegal component: %s') % path
            )
        self._repo = hg.repository(r.baseui, root, create=create)
        if os.path.normcase(self._repo.root) != os.path.normcase(root):
            raise error.ProgrammingError(
                b'failed to reject unsafe subrepo '
                b'path: %s (expanded to %s)' % (root, self._repo.root)
            )

        # Propagate the parent's --hidden option
        if r is r.unfiltered():
            self._repo = self._repo.unfiltered()

        self.ui = self._repo.ui
        for s, k in [(b'ui', b'commitsubrepos')]:
            v = r.ui.config(s, k)
            if v:
                self.ui.setconfig(s, k, v, b'subrepo')
        # internal config: ui._usedassubrepo
        self.ui.setconfig(b'ui', b'_usedassubrepo', b'True', b'subrepo')
        self._initrepo(r, state[0], create)

    @annotatesubrepoerror
    def addwebdirpath(self, serverpath, webconf):
        cmdutil.addwebdirpath(self._repo, subrelpath(self), webconf)

    def storeclean(self, path):
        with self._repo.lock():
            return self._storeclean(path)

    def _storeclean(self, path):
        clean = True
        itercache = self._calcstorehash(path)
        for filehash in self._readstorehashcache(path):
            if filehash != next(itercache, None):
                clean = False
                break
        if clean:
            # if not empty:
            # the cached and current pull states have a different size
            clean = next(itercache, None) is None
        return clean

    def _calcstorehash(self, remotepath):
        """calculate a unique "store hash"

        This method is used to to detect when there are changes that may
        require a push to a given remote path."""
        # sort the files that will be hashed in increasing (likely) file size
        filelist = (b'bookmarks', b'store/phaseroots', b'store/00changelog.i')
        yield b'# %s\n' % _expandedabspath(remotepath)
        vfs = self._repo.vfs
        for relname in filelist:
            filehash = hex(hashutil.sha1(vfs.tryread(relname)).digest())
            yield b'%s = %s\n' % (relname, filehash)

    @propertycache
    def _cachestorehashvfs(self):
        return vfsmod.vfs(self._repo.vfs.join(b'cache/storehash'))

    def _readstorehashcache(self, remotepath):
        '''read the store hash cache for a given remote repository'''
        cachefile = _getstorehashcachename(remotepath)
        return self._cachestorehashvfs.tryreadlines(cachefile, b'r')

    def _cachestorehash(self, remotepath):
        """cache the current store hash

        Each remote repo requires its own store hash cache, because a subrepo
        store may be "clean" versus a given remote repo, but not versus another
        """
        cachefile = _getstorehashcachename(remotepath)
        with self._repo.lock():
            storehash = list(self._calcstorehash(remotepath))
            vfs = self._cachestorehashvfs
            vfs.writelines(cachefile, storehash, mode=b'wb', notindexed=True)

    def _getctx(self):
        """fetch the context for this subrepo revision, possibly a workingctx"""
        if self._ctx.rev() is None:
            return self._repo[None]  # workingctx if parent is workingctx
        else:
            rev = self._state[1]
            return self._repo[rev]

    @annotatesubrepoerror
    def _initrepo(self, parentrepo, source, create):
        self._repo._subparent = parentrepo
        self._repo._subsource = source

        if create:
            lines = [b'[paths]\n']

            def addpathconfig(key, value):
                if value:
                    lines.append(b'%s = %s\n' % (key, value))
                    self.ui.setconfig(b'paths', key, value, b'subrepo')

            defpath = _abssource(self._repo, abort=False)
            defpushpath = _abssource(self._repo, True, abort=False)
            addpathconfig(b'default', defpath)
            if defpath != defpushpath:
                addpathconfig(b'default-push', defpushpath)

            self._repo.vfs.write(b'hgrc', util.tonativeeol(b''.join(lines)))

    @annotatesubrepoerror
    def add(self, ui, match, prefix, uipathfn, explicitonly, **opts):
        return cmdutil.add(
            ui, self._repo, match, prefix, uipathfn, explicitonly, **opts
        )

    @annotatesubrepoerror
    def addremove(self, m, prefix, uipathfn, opts):
        # In the same way as sub directories are processed, once in a subrepo,
        # always entry any of its subrepos.  Don't corrupt the options that will
        # be used to process sibling subrepos however.
        opts = copy.copy(opts)
        opts[b'subrepos'] = True
        return scmutil.addremove(self._repo, m, prefix, uipathfn, opts)

    @annotatesubrepoerror
    def cat(self, match, fm, fntemplate, prefix, **opts):
        rev = self._state[1]
        ctx = self._repo[rev]
        return cmdutil.cat(
            self.ui, self._repo, ctx, match, fm, fntemplate, prefix, **opts
        )

    @annotatesubrepoerror
    def status(self, rev2, **opts):
        try:
            rev1 = self._state[1]
            ctx1 = self._repo[rev1]
            ctx2 = self._repo[rev2]
            return self._repo.status(ctx1, ctx2, **opts)
        except error.RepoLookupError as inst:
            self.ui.warn(
                _(b'warning: error "%s" in subrepository "%s"\n')
                % (inst, subrelpath(self))
            )
            return scmutil.status([], [], [], [], [], [], [])

    @annotatesubrepoerror
    def diff(self, ui, diffopts, node2, match, prefix, **opts):
        try:
            node1 = bin(self._state[1])
            # We currently expect node2 to come from substate and be
            # in hex format
            if node2 is not None:
                node2 = bin(node2)
            logcmdutil.diffordiffstat(
                ui,
                self._repo,
                diffopts,
                self._repo[node1],
                self._repo[node2],
                match,
                prefix=prefix,
                listsubrepos=True,
                **opts
            )
        except error.RepoLookupError as inst:
            self.ui.warn(
                _(b'warning: error "%s" in subrepository "%s"\n')
                % (inst, subrelpath(self))
            )

    @annotatesubrepoerror
    def archive(self, archiver, prefix, match=None, decode=True):
        self._get(self._state + (b'hg',))
        files = self.files()
        if match:
            files = [f for f in files if match(f)]
        rev = self._state[1]
        ctx = self._repo[rev]
        scmutil.prefetchfiles(
            self._repo, [(ctx.rev(), scmutil.matchfiles(self._repo, files))]
        )
        total = abstractsubrepo.archive(self, archiver, prefix, match)
        for subpath in ctx.substate:
            s = subrepo(ctx, subpath, True)
            submatch = matchmod.subdirmatcher(subpath, match)
            subprefix = prefix + subpath + b'/'
            total += s.archive(archiver, subprefix, submatch, decode)
        return total

    @annotatesubrepoerror
    def dirty(self, ignoreupdate=False, missing=False):
        r = self._state[1]
        if r == b'' and not ignoreupdate:  # no state recorded
            return True
        w = self._repo[None]
        if r != w.p1().hex() and not ignoreupdate:
            # different version checked out
            return True
        return w.dirty(missing=missing)  # working directory changed

    def basestate(self):
        return self._repo[b'.'].hex()

    def checknested(self, path):
        return self._repo._checknested(self._repo.wjoin(path))

    @annotatesubrepoerror
    def commit(self, text, user, date):
        # don't bother committing in the subrepo if it's only been
        # updated
        if not self.dirty(True):
            return self._repo[b'.'].hex()
        self.ui.debug(b"committing subrepo %s\n" % subrelpath(self))
        n = self._repo.commit(text, user, date)
        if not n:
            return self._repo[b'.'].hex()  # different version checked out
        return hex(n)

    @annotatesubrepoerror
    def phase(self, state):
        return self._repo[state or b'.'].phase()

    @annotatesubrepoerror
    def remove(self):
        # we can't fully delete the repository as it may contain
        # local-only history
        self.ui.note(_(b'removing subrepo %s\n') % subrelpath(self))
        hg.clean(self._repo, self._repo.nullid, False)

    def _get(self, state):
        source, revision, kind = state
        parentrepo = self._repo._subparent

        if revision in self._repo.unfiltered():
            # Allow shared subrepos tracked at null to setup the sharedpath
            if len(self._repo) != 0 or not parentrepo.shared():
                return True
        self._repo._subsource = source
        srcurl = _abssource(self._repo)

        # Defer creating the peer until after the status message is logged, in
        # case there are network problems.
        getpeer = lambda: hg.peer(self._repo, {}, srcurl)

        if len(self._repo) == 0:
            # use self._repo.vfs instead of self.wvfs to remove .hg only
            self._repo.vfs.rmtree()

            # A remote subrepo could be shared if there is a local copy
            # relative to the parent's share source.  But clone pooling doesn't
            # assemble the repos in a tree, so that can't be consistently done.
            # A simpler option is for the user to configure clone pooling, and
            # work with that.
            if parentrepo.shared() and hg.islocal(srcurl):
                self.ui.status(
                    _(b'sharing subrepo %s from %s\n')
                    % (subrelpath(self), srcurl)
                )
                peer = getpeer()
                try:
                    shared = hg.share(
                        self._repo._subparent.baseui,
                        peer,
                        self._repo.root,
                        update=False,
                        bookmarks=False,
                    )
                finally:
                    peer.close()
                self._repo = shared.local()
            else:
                # TODO: find a common place for this and this code in the
                # share.py wrap of the clone command.
                if parentrepo.shared():
                    pool = self.ui.config(b'share', b'pool')
                    if pool:
                        pool = util.expandpath(pool)

                    shareopts = {
                        b'pool': pool,
                        b'mode': self.ui.config(b'share', b'poolnaming'),
                    }
                else:
                    shareopts = {}

                self.ui.status(
                    _(b'cloning subrepo %s from %s\n')
                    % (subrelpath(self), urlutil.hidepassword(srcurl))
                )
                peer = getpeer()
                try:
                    other, cloned = hg.clone(
                        self._repo._subparent.baseui,
                        {},
                        peer,
                        self._repo.root,
                        update=False,
                        shareopts=shareopts,
                    )
                finally:
                    peer.close()
                self._repo = cloned.local()
            self._initrepo(parentrepo, source, create=True)
            self._cachestorehash(srcurl)
        else:
            self.ui.status(
                _(b'pulling subrepo %s from %s\n')
                % (subrelpath(self), urlutil.hidepassword(srcurl))
            )
            cleansub = self.storeclean(srcurl)
            peer = getpeer()
            try:
                exchange.pull(self._repo, peer)
            finally:
                peer.close()
            if cleansub:
                # keep the repo clean after pull
                self._cachestorehash(srcurl)
        return False

    @annotatesubrepoerror
    def get(self, state, overwrite=False):
        inrepo = self._get(state)
        source, revision, kind = state
        repo = self._repo
        repo.ui.debug(b"getting subrepo %s\n" % self._path)
        if inrepo:
            urepo = repo.unfiltered()
            ctx = urepo[revision]
            if ctx.hidden():
                urepo.ui.warn(
                    _(b'revision %s in subrepository "%s" is hidden\n')
                    % (revision[0:12], self._path)
                )
                repo = urepo
        if overwrite:
            merge.clean_update(repo[revision])
        else:
            merge.update(repo[revision])

    @annotatesubrepoerror
    def merge(self, state):
        self._get(state)
        cur = self._repo[b'.']
        dst = self._repo[state[1]]
        anc = dst.ancestor(cur)

        def mergefunc():
            if anc == cur and dst.branch() == cur.branch():
                self.ui.debug(
                    b'updating subrepository "%s"\n' % subrelpath(self)
                )
                hg.update(self._repo, state[1])
            elif anc == dst:
                self.ui.debug(
                    b'skipping subrepository "%s"\n' % subrelpath(self)
                )
            else:
                self.ui.debug(
                    b'merging subrepository "%s"\n' % subrelpath(self)
                )
                hg.merge(dst, remind=False)

        wctx = self._repo[None]
        if self.dirty():
            if anc != dst:
                if _updateprompt(self.ui, self, wctx.dirty(), cur, dst):
                    mergefunc()
            else:
                mergefunc()
        else:
            mergefunc()

    @annotatesubrepoerror
    def push(self, opts):
        force = opts.get(b'force')
        newbranch = opts.get(b'new_branch')
        ssh = opts.get(b'ssh')

        # push subrepos depth-first for coherent ordering
        c = self._repo[b'.']
        subs = c.substate  # only repos that are committed
        for s in sorted(subs):
            if c.sub(s).push(opts) == 0:
                return False

        dsturl = _abssource(self._repo, True)
        if not force:
            if self.storeclean(dsturl):
                self.ui.status(
                    _(b'no changes made to subrepo %s since last push to %s\n')
                    % (subrelpath(self), urlutil.hidepassword(dsturl))
                )
                return None
        self.ui.status(
            _(b'pushing subrepo %s to %s\n')
            % (subrelpath(self), urlutil.hidepassword(dsturl))
        )
        other = hg.peer(self._repo, {b'ssh': ssh}, dsturl)
        try:
            res = exchange.push(self._repo, other, force, newbranch=newbranch)
        finally:
            other.close()

        # the repo is now clean
        self._cachestorehash(dsturl)
        return res.cgresult

    @annotatesubrepoerror
    def outgoing(self, ui, dest, opts):
        if b'rev' in opts or b'branch' in opts:
            opts = copy.copy(opts)
            opts.pop(b'rev', None)
            opts.pop(b'branch', None)
        subpath = subrepoutil.repo_rel_or_abs_source(self._repo)
        return hg.outgoing(ui, self._repo, dest, opts, subpath=subpath)

    @annotatesubrepoerror
    def incoming(self, ui, source, opts):
        if b'rev' in opts or b'branch' in opts:
            opts = copy.copy(opts)
            opts.pop(b'rev', None)
            opts.pop(b'branch', None)
        subpath = subrepoutil.repo_rel_or_abs_source(self._repo)
        return hg.incoming(ui, self._repo, source, opts, subpath=subpath)

    @annotatesubrepoerror
    def files(self):
        rev = self._state[1]
        ctx = self._repo[rev]
        return ctx.manifest().keys()

    def filedata(self, name, decode):
        rev = self._state[1]
        data = self._repo[rev][name].data()
        if decode:
            data = self._repo.wwritedata(name, data)
        return data

    def fileflags(self, name):
        rev = self._state[1]
        ctx = self._repo[rev]
        return ctx.flags(name)

    @annotatesubrepoerror
    def printfiles(self, ui, m, uipathfn, fm, fmt, subrepos):
        # If the parent context is a workingctx, use the workingctx here for
        # consistency.
        if self._ctx.rev() is None:
            ctx = self._repo[None]
        else:
            rev = self._state[1]
            ctx = self._repo[rev]
        return cmdutil.files(ui, ctx, m, uipathfn, fm, fmt, subrepos)

    @annotatesubrepoerror
    def matchfileset(self, cwd, expr, badfn=None):
        if self._ctx.rev() is None:
            ctx = self._repo[None]
        else:
            rev = self._state[1]
            ctx = self._repo[rev]

        matchers = [ctx.matchfileset(cwd, expr, badfn=badfn)]

        for subpath in ctx.substate:
            sub = ctx.sub(subpath)

            try:
                sm = sub.matchfileset(cwd, expr, badfn=badfn)
                pm = matchmod.prefixdirmatcher(subpath, sm, badfn=badfn)
                matchers.append(pm)
            except error.LookupError:
                self.ui.status(
                    _(b"skipping missing subrepository: %s\n")
                    % self.wvfs.reljoin(reporelpath(self), subpath)
                )
        if len(matchers) == 1:
            return matchers[0]
        return matchmod.unionmatcher(matchers)

    def walk(self, match):
        ctx = self._repo[None]
        return ctx.walk(match)

    @annotatesubrepoerror
    def forget(self, match, prefix, uipathfn, dryrun, interactive):
        return cmdutil.forget(
            self.ui,
            self._repo,
            match,
            prefix,
            uipathfn,
            True,
            dryrun=dryrun,
            interactive=interactive,
        )

    @annotatesubrepoerror
    def removefiles(
        self,
        matcher,
        prefix,
        uipathfn,
        after,
        force,
        subrepos,
        dryrun,
        warnings,
    ):
        return cmdutil.remove(
            self.ui,
            self._repo,
            matcher,
            prefix,
            uipathfn,
            after,
            force,
            subrepos,
            dryrun,
        )

    @annotatesubrepoerror
    def revert(self, substate, *pats, **opts):
        # reverting a subrepo is a 2 step process:
        # 1. if the no_backup is not set, revert all modified
        #    files inside the subrepo
        # 2. update the subrepo to the revision specified in
        #    the corresponding substate dictionary
        self.ui.status(_(b'reverting subrepo %s\n') % substate[0])
        if not opts.get('no_backup'):
            # Revert all files on the subrepo, creating backups
            # Note that this will not recursively revert subrepos
            # We could do it if there was a set:subrepos() predicate
            opts = opts.copy()
            opts['date'] = None
            opts['rev'] = substate[1]

            self.filerevert(*pats, **opts)

        # Update the repo to the revision specified in the given substate
        if not opts.get('dry_run'):
            self.get(substate, overwrite=True)

    def filerevert(self, *pats, **opts):
        ctx = self._repo[opts['rev']]
        if opts.get('all'):
            pats = [b'set:modified()']
        else:
            pats = []
        cmdutil.revert(self.ui, self._repo, ctx, *pats, **opts)

    def shortid(self, revid):
        return revid[:12]

    @annotatesubrepoerror
    def unshare(self):
        # subrepo inherently violates our import layering rules
        # because it wants to make repo objects from deep inside the stack
        # so we manually delay the circular imports to not break
        # scripts that don't use our demand-loading
        global hg
        from . import hg as h

        hg = h

        # Nothing prevents a user from sharing in a repo, and then making that a
        # subrepo.  Alternately, the previous unshare attempt may have failed
        # part way through.  So recurse whether or not this layer is shared.
        if self._repo.shared():
            self.ui.status(_(b"unsharing subrepo '%s'\n") % self._relpath)

        hg.unshare(self.ui, self._repo)

    def verify(self, onpush=False):
        try:
            rev = self._state[1]
            ctx = self._repo.unfiltered()[rev]
            if ctx.hidden():
                # Since hidden revisions aren't pushed/pulled, it seems worth an
                # explicit warning.
                msg = _(b"subrepo '%s' is hidden in revision %s") % (
                    self._relpath,
                    short(self._ctx.node()),
                )

                if onpush:
                    raise error.Abort(msg)
                else:
                    self._repo.ui.warn(b'%s\n' % msg)
            return 0
        except error.RepoLookupError:
            # A missing subrepo revision may be a case of needing to pull it, so
            # don't treat this as an error for `hg verify`.
            msg = _(b"subrepo '%s' not found in revision %s") % (
                self._relpath,
                short(self._ctx.node()),
            )

            if onpush:
                raise error.Abort(msg)
            else:
                self._repo.ui.warn(b'%s\n' % msg)
            return 0

    @propertycache
    def wvfs(self):
        """return own wvfs for efficiency and consistency"""
        return self._repo.wvfs

    @propertycache
    def _relpath(self):
        """return path to this subrepository as seen from outermost repository"""
        # Keep consistent dir separators by avoiding vfs.join(self._path)
        return reporelpath(self._repo)


class svnsubrepo(abstractsubrepo):
    def __init__(self, ctx, path, state, allowcreate):
        super(svnsubrepo, self).__init__(ctx, path)
        self._state = state
        self._exe = procutil.findexe(b'svn')
        if not self._exe:
            raise error.Abort(
                _(b"'svn' executable not found for subrepo '%s'") % self._path
            )

    def _svncommand(self, commands, filename=b'', failok=False):
        cmd = [self._exe]
        extrakw = {}
        if not self.ui.interactive():
            # Making stdin be a pipe should prevent svn from behaving
            # interactively even if we can't pass --non-interactive.
            extrakw['stdin'] = subprocess.PIPE
            # Starting in svn 1.5 --non-interactive is a global flag
            # instead of being per-command, but we need to support 1.4 so
            # we have to be intelligent about what commands take
            # --non-interactive.
            if commands[0] in (b'update', b'checkout', b'commit'):
                cmd.append(b'--non-interactive')
        cmd.extend(commands)
        if filename is not None:
            path = self.wvfs.reljoin(
                self._ctx.repo().origroot, self._path, filename
            )
            cmd.append(path)
        env = dict(encoding.environ)
        # Avoid localized output, preserve current locale for everything else.
        lc_all = env.get(b'LC_ALL')
        if lc_all:
            env[b'LANG'] = lc_all
            del env[b'LC_ALL']
        env[b'LC_MESSAGES'] = b'C'
        p = subprocess.Popen(
            pycompat.rapply(procutil.tonativestr, cmd),
            bufsize=-1,
            close_fds=procutil.closefds,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=procutil.tonativeenv(env),
            **extrakw
        )
        stdout, stderr = map(util.fromnativeeol, p.communicate())
        stderr = stderr.strip()
        if not failok:
            if p.returncode:
                raise error.Abort(
                    stderr or b'exited with code %d' % p.returncode
                )
            if stderr:
                self.ui.warn(stderr + b'\n')
        return stdout, stderr

    @propertycache
    def _svnversion(self):
        output, err = self._svncommand(
            [b'--version', b'--quiet'], filename=None
        )
        m = re.search(br'^(\d+)\.(\d+)', output)
        if not m:
            raise error.Abort(_(b'cannot retrieve svn tool version'))
        return (int(m.group(1)), int(m.group(2)))

    def _svnmissing(self):
        return not self.wvfs.exists(b'.svn')

    def _wcrevs(self):
        # Get the working directory revision as well as the last
        # commit revision so we can compare the subrepo state with
        # both. We used to store the working directory one.
        output, err = self._svncommand([b'info', b'--xml'])
        doc = xml.dom.minidom.parseString(output)
        entries = doc.getElementsByTagName('entry')
        lastrev, rev = b'0', b'0'
        if entries:
            rev = pycompat.bytestr(entries[0].getAttribute('revision')) or b'0'
            commits = entries[0].getElementsByTagName('commit')
            if commits:
                lastrev = (
                    pycompat.bytestr(commits[0].getAttribute('revision'))
                    or b'0'
                )
        return (lastrev, rev)

    def _wcrev(self):
        return self._wcrevs()[0]

    def _wcchanged(self):
        """Return (changes, extchanges, missing) where changes is True
        if the working directory was changed, extchanges is
        True if any of these changes concern an external entry and missing
        is True if any change is a missing entry.
        """
        output, err = self._svncommand([b'status', b'--xml'])
        externals, changes, missing = [], [], []
        doc = xml.dom.minidom.parseString(output)
        for e in doc.getElementsByTagName('entry'):
            s = e.getElementsByTagName('wc-status')
            if not s:
                continue
            item = s[0].getAttribute('item')
            props = s[0].getAttribute('props')
            path = e.getAttribute('path').encode('utf8')
            if item == 'external':
                externals.append(path)
            elif item == 'missing':
                missing.append(path)
            if (
                item
                not in (
                    '',
                    'normal',
                    'unversioned',
                    'external',
                )
                or props not in ('', 'none', 'normal')
            ):
                changes.append(path)
        for path in changes:
            for ext in externals:
                if path == ext or path.startswith(ext + pycompat.ossep):
                    return True, True, bool(missing)
        return bool(changes), False, bool(missing)

    @annotatesubrepoerror
    def dirty(self, ignoreupdate=False, missing=False):
        if self._svnmissing():
            return self._state[1] != b''
        wcchanged = self._wcchanged()
        changed = wcchanged[0] or (missing and wcchanged[2])
        if not changed:
            if self._state[1] in self._wcrevs() or ignoreupdate:
                return False
        return True

    def basestate(self):
        lastrev, rev = self._wcrevs()
        if lastrev != rev:
            # Last committed rev is not the same than rev. We would
            # like to take lastrev but we do not know if the subrepo
            # URL exists at lastrev.  Test it and fallback to rev it
            # is not there.
            try:
                self._svncommand(
                    [b'list', b'%s@%s' % (self._state[0], lastrev)]
                )
                return lastrev
            except error.Abort:
                pass
        return rev

    @annotatesubrepoerror
    def commit(self, text, user, date):
        # user and date are out of our hands since svn is centralized
        changed, extchanged, missing = self._wcchanged()
        if not changed:
            return self.basestate()
        if extchanged:
            # Do not try to commit externals
            raise error.Abort(_(b'cannot commit svn externals'))
        if missing:
            # svn can commit with missing entries but aborting like hg
            # seems a better approach.
            raise error.Abort(_(b'cannot commit missing svn entries'))
        commitinfo, err = self._svncommand([b'commit', b'-m', text])
        self.ui.status(commitinfo)
        newrev = re.search(b'Committed revision ([0-9]+).', commitinfo)
        if not newrev:
            if not commitinfo.strip():
                # Sometimes, our definition of "changed" differs from
                # svn one. For instance, svn ignores missing files
                # when committing. If there are only missing files, no
                # commit is made, no output and no error code.
                raise error.Abort(_(b'failed to commit svn changes'))
            raise error.Abort(commitinfo.splitlines()[-1])
        newrev = newrev.groups()[0]
        self.ui.status(self._svncommand([b'update', b'-r', newrev])[0])
        return newrev

    @annotatesubrepoerror
    def remove(self):
        if self.dirty():
            self.ui.warn(
                _(b'not removing repo %s because it has changes.\n')
                % self._path
            )
            return
        self.ui.note(_(b'removing subrepo %s\n') % self._path)

        self.wvfs.rmtree(forcibly=True)
        try:
            pwvfs = self._ctx.repo().wvfs
            pwvfs.removedirs(pwvfs.dirname(self._path))
        except OSError:
            pass

    @annotatesubrepoerror
    def get(self, state, overwrite=False):
        if overwrite:
            self._svncommand([b'revert', b'--recursive'])
        args = [b'checkout']
        if self._svnversion >= (1, 5):
            args.append(b'--force')
        # The revision must be specified at the end of the URL to properly
        # update to a directory which has since been deleted and recreated.
        args.append(b'%s@%s' % (state[0], state[1]))

        # SEC: check that the ssh url is safe
        urlutil.checksafessh(state[0])

        status, err = self._svncommand(args, failok=True)
        _sanitize(self.ui, self.wvfs, b'.svn')
        if not re.search(b'Checked out revision [0-9]+.', status):
            if b'is already a working copy for a different URL' in err and (
                self._wcchanged()[:2] == (False, False)
            ):
                # obstructed but clean working copy, so just blow it away.
                self.remove()
                self.get(state, overwrite=False)
                return
            raise error.Abort((status or err).splitlines()[-1])
        self.ui.status(status)

    @annotatesubrepoerror
    def merge(self, state):
        old = self._state[1]
        new = state[1]
        wcrev = self._wcrev()
        if new != wcrev:
            dirty = old == wcrev or self._wcchanged()[0]
            if _updateprompt(self.ui, self, dirty, wcrev, new):
                self.get(state, False)

    def push(self, opts):
        # push is a no-op for SVN
        return True

    @annotatesubrepoerror
    def files(self):
        output = self._svncommand([b'list', b'--recursive', b'--xml'])[0]
        doc = xml.dom.minidom.parseString(output)
        paths = []
        for e in doc.getElementsByTagName('entry'):
            kind = pycompat.bytestr(e.getAttribute('kind'))
            if kind != b'file':
                continue
            name = ''.join(
                c.data
                for c in e.getElementsByTagName('name')[0].childNodes
                if c.nodeType == c.TEXT_NODE
            )
            paths.append(name.encode('utf8'))
        return paths

    def filedata(self, name, decode):
        return self._svncommand([b'cat'], name)[0]


class gitsubrepo(abstractsubrepo):
    def __init__(self, ctx, path, state, allowcreate):
        super(gitsubrepo, self).__init__(ctx, path)
        self._state = state
        self._abspath = ctx.repo().wjoin(path)
        self._subparent = ctx.repo()
        self._ensuregit()

    def _ensuregit(self):
        try:
            self._gitexecutable = b'git'
            out, err = self._gitnodir([b'--version'])
        except OSError as e:
            genericerror = _(b"error executing git for subrepo '%s': %s")
            notfoundhint = _(b"check git is installed and in your PATH")
            if e.errno != errno.ENOENT:
                raise error.Abort(
                    genericerror % (self._path, encoding.strtolocal(e.strerror))
                )
            elif pycompat.iswindows:
                try:
                    self._gitexecutable = b'git.cmd'
                    out, err = self._gitnodir([b'--version'])
                except OSError as e2:
                    if e2.errno == errno.ENOENT:
                        raise error.Abort(
                            _(
                                b"couldn't find 'git' or 'git.cmd'"
                                b" for subrepo '%s'"
                            )
                            % self._path,
                            hint=notfoundhint,
                        )
                    else:
                        raise error.Abort(
                            genericerror
                            % (self._path, encoding.strtolocal(e2.strerror))
                        )
            else:
                raise error.Abort(
                    _(b"couldn't find git for subrepo '%s'") % self._path,
                    hint=notfoundhint,
                )
        versionstatus = self._checkversion(out)
        if versionstatus == b'unknown':
            self.ui.warn(_(b'cannot retrieve git version\n'))
        elif versionstatus == b'abort':
            raise error.Abort(
                _(b'git subrepo requires at least 1.6.0 or later')
            )
        elif versionstatus == b'warning':
            self.ui.warn(_(b'git subrepo requires at least 1.6.0 or later\n'))

    @staticmethod
    def _gitversion(out):
        m = re.search(br'^git version (\d+)\.(\d+)\.(\d+)', out)
        if m:
            return (int(m.group(1)), int(m.group(2)), int(m.group(3)))

        m = re.search(br'^git version (\d+)\.(\d+)', out)
        if m:
            return (int(m.group(1)), int(m.group(2)), 0)

        return -1

    @staticmethod
    def _checkversion(out):
        """ensure git version is new enough

        >>> _checkversion = gitsubrepo._checkversion
        >>> _checkversion(b'git version 1.6.0')
        'ok'
        >>> _checkversion(b'git version 1.8.5')
        'ok'
        >>> _checkversion(b'git version 1.4.0')
        'abort'
        >>> _checkversion(b'git version 1.5.0')
        'warning'
        >>> _checkversion(b'git version 1.9-rc0')
        'ok'
        >>> _checkversion(b'git version 1.9.0.265.g81cdec2')
        'ok'
        >>> _checkversion(b'git version 1.9.0.GIT')
        'ok'
        >>> _checkversion(b'git version 12345')
        'unknown'
        >>> _checkversion(b'no')
        'unknown'
        """
        version = gitsubrepo._gitversion(out)
        # git 1.4.0 can't work at all, but 1.5.X can in at least some cases,
        # despite the docstring comment.  For now, error on 1.4.0, warn on
        # 1.5.0 but attempt to continue.
        if version == -1:
            return b'unknown'
        if version < (1, 5, 0):
            return b'abort'
        elif version < (1, 6, 0):
            return b'warning'
        return b'ok'

    def _gitcommand(self, commands, env=None, stream=False):
        return self._gitdir(commands, env=env, stream=stream)[0]

    def _gitdir(self, commands, env=None, stream=False):
        return self._gitnodir(
            commands, env=env, stream=stream, cwd=self._abspath
        )

    def _gitnodir(self, commands, env=None, stream=False, cwd=None):
        """Calls the git command

        The methods tries to call the git command. versions prior to 1.6.0
        are not supported and very probably fail.
        """
        self.ui.debug(b'%s: git %s\n' % (self._relpath, b' '.join(commands)))
        if env is None:
            env = encoding.environ.copy()
        # disable localization for Git output (issue5176)
        env[b'LC_ALL'] = b'C'
        # fix for Git CVE-2015-7545
        if b'GIT_ALLOW_PROTOCOL' not in env:
            env[b'GIT_ALLOW_PROTOCOL'] = b'file:git:http:https:ssh'
        # unless ui.quiet is set, print git's stderr,
        # which is mostly progress and useful info
        errpipe = None
        if self.ui.quiet:
            errpipe = pycompat.open(os.devnull, b'w')
        if self.ui._colormode and len(commands) and commands[0] == b"diff":
            # insert the argument in the front,
            # the end of git diff arguments is used for paths
            commands.insert(1, b'--color')
        p = subprocess.Popen(
            pycompat.rapply(
                procutil.tonativestr, [self._gitexecutable] + commands
            ),
            bufsize=-1,
            cwd=pycompat.rapply(procutil.tonativestr, cwd),
            env=procutil.tonativeenv(env),
            close_fds=procutil.closefds,
            stdout=subprocess.PIPE,
            stderr=errpipe,
        )
        if stream:
            return p.stdout, None

        retdata = p.stdout.read().strip()
        # wait for the child to exit to avoid race condition.
        p.wait()

        if p.returncode != 0 and p.returncode != 1:
            # there are certain error codes that are ok
            command = commands[0]
            if command in (b'cat-file', b'symbolic-ref'):
                return retdata, p.returncode
            # for all others, abort
            raise error.Abort(
                _(b'git %s error %d in %s')
                % (command, p.returncode, self._relpath)
            )

        return retdata, p.returncode

    def _gitmissing(self):
        return not self.wvfs.exists(b'.git')

    def _gitstate(self):
        return self._gitcommand([b'rev-parse', b'HEAD'])

    def _gitcurrentbranch(self):
        current, err = self._gitdir([b'symbolic-ref', b'HEAD', b'--quiet'])
        if err:
            current = None
        return current

    def _gitremote(self, remote):
        out = self._gitcommand([b'remote', b'show', b'-n', remote])
        line = out.split(b'\n')[1]
        i = line.index(b'URL: ') + len(b'URL: ')
        return line[i:]

    def _githavelocally(self, revision):
        out, code = self._gitdir([b'cat-file', b'-e', revision])
        return code == 0

    def _gitisancestor(self, r1, r2):
        base = self._gitcommand([b'merge-base', r1, r2])
        return base == r1

    def _gitisbare(self):
        return self._gitcommand([b'config', b'--bool', b'core.bare']) == b'true'

    def _gitupdatestat(self):
        """This must be run before git diff-index.
        diff-index only looks at changes to file stat;
        this command looks at file contents and updates the stat."""
        self._gitcommand([b'update-index', b'-q', b'--refresh'])

    def _gitbranchmap(self):
        """returns 2 things:
        a map from git branch to revision
        a map from revision to branches"""
        branch2rev = {}
        rev2branch = {}

        out = self._gitcommand(
            [b'for-each-ref', b'--format', b'%(objectname) %(refname)']
        )
        for line in out.split(b'\n'):
            revision, ref = line.split(b' ')
            if not ref.startswith(b'refs/heads/') and not ref.startswith(
                b'refs/remotes/'
            ):
                continue
            if ref.startswith(b'refs/remotes/') and ref.endswith(b'/HEAD'):
                continue  # ignore remote/HEAD redirects
            branch2rev[ref] = revision
            rev2branch.setdefault(revision, []).append(ref)
        return branch2rev, rev2branch

    def _gittracking(self, branches):
        """return map of remote branch to local tracking branch"""
        # assumes no more than one local tracking branch for each remote
        tracking = {}
        for b in branches:
            if b.startswith(b'refs/remotes/'):
                continue
            bname = b.split(b'/', 2)[2]
            remote = self._gitcommand([b'config', b'branch.%s.remote' % bname])
            if remote:
                ref = self._gitcommand([b'config', b'branch.%s.merge' % bname])
                tracking[
                    b'refs/remotes/%s/%s' % (remote, ref.split(b'/', 2)[2])
                ] = b
        return tracking

    def _abssource(self, source):
        if b'://' not in source:
            # recognize the scp syntax as an absolute source
            colon = source.find(b':')
            if colon != -1 and b'/' not in source[:colon]:
                return source
        self._subsource = source
        return _abssource(self)

    def _fetch(self, source, revision):
        if self._gitmissing():
            # SEC: check for safe ssh url
            urlutil.checksafessh(source)

            source = self._abssource(source)
            self.ui.status(
                _(b'cloning subrepo %s from %s\n') % (self._relpath, source)
            )
            self._gitnodir([b'clone', source, self._abspath])
        if self._githavelocally(revision):
            return
        self.ui.status(
            _(b'pulling subrepo %s from %s\n')
            % (self._relpath, self._gitremote(b'origin'))
        )
        # try only origin: the originally cloned repo
        self._gitcommand([b'fetch'])
        if not self._githavelocally(revision):
            raise error.Abort(
                _(b'revision %s does not exist in subrepository "%s"\n')
                % (revision, self._relpath)
            )

    @annotatesubrepoerror
    def dirty(self, ignoreupdate=False, missing=False):
        if self._gitmissing():
            return self._state[1] != b''
        if self._gitisbare():
            return True
        if not ignoreupdate and self._state[1] != self._gitstate():
            # different version checked out
            return True
        # check for staged changes or modified files; ignore untracked files
        self._gitupdatestat()
        out, code = self._gitdir([b'diff-index', b'--quiet', b'HEAD'])
        return code == 1

    def basestate(self):
        return self._gitstate()

    @annotatesubrepoerror
    def get(self, state, overwrite=False):
        source, revision, kind = state
        if not revision:
            self.remove()
            return
        self._fetch(source, revision)
        # if the repo was set to be bare, unbare it
        if self._gitisbare():
            self._gitcommand([b'config', b'core.bare', b'false'])
            if self._gitstate() == revision:
                self._gitcommand([b'reset', b'--hard', b'HEAD'])
                return
        elif self._gitstate() == revision:
            if overwrite:
                # first reset the index to unmark new files for commit, because
                # reset --hard will otherwise throw away files added for commit,
                # not just unmark them.
                self._gitcommand([b'reset', b'HEAD'])
                self._gitcommand([b'reset', b'--hard', b'HEAD'])
            return
        branch2rev, rev2branch = self._gitbranchmap()

        def checkout(args):
            cmd = [b'checkout']
            if overwrite:
                # first reset the index to unmark new files for commit, because
                # the -f option will otherwise throw away files added for
                # commit, not just unmark them.
                self._gitcommand([b'reset', b'HEAD'])
                cmd.append(b'-f')
            self._gitcommand(cmd + args)
            _sanitize(self.ui, self.wvfs, b'.git')

        def rawcheckout():
            # no branch to checkout, check it out with no branch
            self.ui.warn(
                _(b'checking out detached HEAD in subrepository "%s"\n')
                % self._relpath
            )
            self.ui.warn(
                _(b'check out a git branch if you intend to make changes\n')
            )
            checkout([b'-q', revision])

        if revision not in rev2branch:
            rawcheckout()
            return
        branches = rev2branch[revision]
        firstlocalbranch = None
        for b in branches:
            if b == b'refs/heads/master':
                # master trumps all other branches
                checkout([b'refs/heads/master'])
                return
            if not firstlocalbranch and not b.startswith(b'refs/remotes/'):
                firstlocalbranch = b
        if firstlocalbranch:
            checkout([firstlocalbranch])
            return

        tracking = self._gittracking(branch2rev.keys())
        # choose a remote branch already tracked if possible
        remote = branches[0]
        if remote not in tracking:
            for b in branches:
                if b in tracking:
                    remote = b
                    break

        if remote not in tracking:
            # create a new local tracking branch
            local = remote.split(b'/', 3)[3]
            checkout([b'-b', local, remote])
        elif self._gitisancestor(branch2rev[tracking[remote]], remote):
            # When updating to a tracked remote branch,
            # if the local tracking branch is downstream of it,
            # a normal `git pull` would have performed a "fast-forward merge"
            # which is equivalent to updating the local branch to the remote.
            # Since we are only looking at branching at update, we need to
            # detect this situation and perform this action lazily.
            if tracking[remote] != self._gitcurrentbranch():
                checkout([tracking[remote]])
            self._gitcommand([b'merge', b'--ff', remote])
            _sanitize(self.ui, self.wvfs, b'.git')
        else:
            # a real merge would be required, just checkout the revision
            rawcheckout()

    @annotatesubrepoerror
    def commit(self, text, user, date):
        if self._gitmissing():
            raise error.Abort(_(b"subrepo %s is missing") % self._relpath)
        cmd = [b'commit', b'-a', b'-m', text]
        env = encoding.environ.copy()
        if user:
            cmd += [b'--author', user]
        if date:
            # git's date parser silently ignores when seconds < 1e9
            # convert to ISO8601
            env[b'GIT_AUTHOR_DATE'] = dateutil.datestr(
                date, b'%Y-%m-%dT%H:%M:%S %1%2'
            )
        self._gitcommand(cmd, env=env)
        # make sure commit works otherwise HEAD might not exist under certain
        # circumstances
        return self._gitstate()

    @annotatesubrepoerror
    def merge(self, state):
        source, revision, kind = state
        self._fetch(source, revision)
        base = self._gitcommand([b'merge-base', revision, self._state[1]])
        self._gitupdatestat()
        out, code = self._gitdir([b'diff-index', b'--quiet', b'HEAD'])

        def mergefunc():
            if base == revision:
                self.get(state)  # fast forward merge
            elif base != self._state[1]:
                self._gitcommand([b'merge', b'--no-commit', revision])
            _sanitize(self.ui, self.wvfs, b'.git')

        if self.dirty():
            if self._gitstate() != revision:
                dirty = self._gitstate() == self._state[1] or code != 0
                if _updateprompt(
                    self.ui, self, dirty, self._state[1][:7], revision[:7]
                ):
                    mergefunc()
        else:
            mergefunc()

    @annotatesubrepoerror
    def push(self, opts):
        force = opts.get(b'force')

        if not self._state[1]:
            return True
        if self._gitmissing():
            raise error.Abort(_(b"subrepo %s is missing") % self._relpath)
        # if a branch in origin contains the revision, nothing to do
        branch2rev, rev2branch = self._gitbranchmap()
        if self._state[1] in rev2branch:
            for b in rev2branch[self._state[1]]:
                if b.startswith(b'refs/remotes/origin/'):
                    return True
        for b, revision in pycompat.iteritems(branch2rev):
            if b.startswith(b'refs/remotes/origin/'):
                if self._gitisancestor(self._state[1], revision):
                    return True
        # otherwise, try to push the currently checked out branch
        cmd = [b'push']
        if force:
            cmd.append(b'--force')

        current = self._gitcurrentbranch()
        if current:
            # determine if the current branch is even useful
            if not self._gitisancestor(self._state[1], current):
                self.ui.warn(
                    _(
                        b'unrelated git branch checked out '
                        b'in subrepository "%s"\n'
                    )
                    % self._relpath
                )
                return False
            self.ui.status(
                _(b'pushing branch %s of subrepository "%s"\n')
                % (current.split(b'/', 2)[2], self._relpath)
            )
            ret = self._gitdir(cmd + [b'origin', current])
            return ret[1] == 0
        else:
            self.ui.warn(
                _(
                    b'no branch checked out in subrepository "%s"\n'
                    b'cannot push revision %s\n'
                )
                % (self._relpath, self._state[1])
            )
            return False

    @annotatesubrepoerror
    def add(self, ui, match, prefix, uipathfn, explicitonly, **opts):
        if self._gitmissing():
            return []

        s = self.status(None, unknown=True, clean=True)

        tracked = set()
        # dirstates 'amn' warn, 'r' is added again
        for l in (s.modified, s.added, s.deleted, s.clean):
            tracked.update(l)

        # Unknown files not of interest will be rejected by the matcher
        files = s.unknown
        files.extend(match.files())

        rejected = []

        files = [f for f in sorted(set(files)) if match(f)]
        for f in files:
            exact = match.exact(f)
            command = [b"add"]
            if exact:
                command.append(b"-f")  # should be added, even if ignored
            if ui.verbose or not exact:
                ui.status(_(b'adding %s\n') % uipathfn(f))

            if f in tracked:  # hg prints 'adding' even if already tracked
                if exact:
                    rejected.append(f)
                continue
            if not opts.get('dry_run'):
                self._gitcommand(command + [f])

        for f in rejected:
            ui.warn(_(b"%s already tracked!\n") % uipathfn(f))

        return rejected

    @annotatesubrepoerror
    def remove(self):
        if self._gitmissing():
            return
        if self.dirty():
            self.ui.warn(
                _(b'not removing repo %s because it has changes.\n')
                % self._relpath
            )
            return
        # we can't fully delete the repository as it may contain
        # local-only history
        self.ui.note(_(b'removing subrepo %s\n') % self._relpath)
        self._gitcommand([b'config', b'core.bare', b'true'])
        for f, kind in self.wvfs.readdir():
            if f == b'.git':
                continue
            if kind == stat.S_IFDIR:
                self.wvfs.rmtree(f)
            else:
                self.wvfs.unlink(f)

    def archive(self, archiver, prefix, match=None, decode=True):
        total = 0
        source, revision = self._state
        if not revision:
            return total
        self._fetch(source, revision)

        # Parse git's native archive command.
        # This should be much faster than manually traversing the trees
        # and objects with many subprocess calls.
        tarstream = self._gitcommand([b'archive', revision], stream=True)
        tar = tarfile.open(fileobj=tarstream, mode='r|')
        relpath = subrelpath(self)
        progress = self.ui.makeprogress(
            _(b'archiving (%s)') % relpath, unit=_(b'files')
        )
        progress.update(0)
        for info in tar:
            if info.isdir():
                continue
            bname = pycompat.fsencode(info.name)
            if match and not match(bname):
                continue
            if info.issym():
                data = info.linkname
            else:
                f = tar.extractfile(info)
                if f:
                    data = f.read()
                else:
                    self.ui.warn(_(b'skipping "%s" (unknown type)') % bname)
                    continue
            archiver.addfile(prefix + bname, info.mode, info.issym(), data)
            total += 1
            progress.increment()
        progress.complete()
        return total

    @annotatesubrepoerror
    def cat(self, match, fm, fntemplate, prefix, **opts):
        rev = self._state[1]
        if match.anypats():
            return 1  # No support for include/exclude yet

        if not match.files():
            return 1

        # TODO: add support for non-plain formatter (see cmdutil.cat())
        for f in match.files():
            output = self._gitcommand([b"show", b"%s:%s" % (rev, f)])
            fp = cmdutil.makefileobj(
                self._ctx, fntemplate, pathname=self.wvfs.reljoin(prefix, f)
            )
            fp.write(output)
            fp.close()
        return 0

    @annotatesubrepoerror
    def status(self, rev2, **opts):
        rev1 = self._state[1]
        if self._gitmissing() or not rev1:
            # if the repo is missing, return no results
            return scmutil.status([], [], [], [], [], [], [])
        modified, added, removed = [], [], []
        self._gitupdatestat()
        if rev2:
            command = [b'diff-tree', b'--no-renames', b'-r', rev1, rev2]
        else:
            command = [b'diff-index', b'--no-renames', rev1]
        out = self._gitcommand(command)
        for line in out.split(b'\n'):
            tab = line.find(b'\t')
            if tab == -1:
                continue
            status, f = line[tab - 1 : tab], line[tab + 1 :]
            if status == b'M':
                modified.append(f)
            elif status == b'A':
                added.append(f)
            elif status == b'D':
                removed.append(f)

        deleted, unknown, ignored, clean = [], [], [], []

        command = [b'status', b'--porcelain', b'-z']
        if opts.get('unknown'):
            command += [b'--untracked-files=all']
        if opts.get('ignored'):
            command += [b'--ignored']
        out = self._gitcommand(command)

        changedfiles = set()
        changedfiles.update(modified)
        changedfiles.update(added)
        changedfiles.update(removed)
        for line in out.split(b'\0'):
            if not line:
                continue
            st = line[0:2]
            # moves and copies show 2 files on one line
            if line.find(b'\0') >= 0:
                filename1, filename2 = line[3:].split(b'\0')
            else:
                filename1 = line[3:]
                filename2 = None

            changedfiles.add(filename1)
            if filename2:
                changedfiles.add(filename2)

            if st == b'??':
                unknown.append(filename1)
            elif st == b'!!':
                ignored.append(filename1)

        if opts.get('clean'):
            out = self._gitcommand([b'ls-files'])
            for f in out.split(b'\n'):
                if not f in changedfiles:
                    clean.append(f)

        return scmutil.status(
            modified, added, removed, deleted, unknown, ignored, clean
        )

    @annotatesubrepoerror
    def diff(self, ui, diffopts, node2, match, prefix, **opts):
        node1 = self._state[1]
        cmd = [b'diff', b'--no-renames']
        if opts['stat']:
            cmd.append(b'--stat')
        else:
            # for Git, this also implies '-p'
            cmd.append(b'-U%d' % diffopts.context)

        if diffopts.noprefix:
            cmd.extend(
                [b'--src-prefix=%s/' % prefix, b'--dst-prefix=%s/' % prefix]
            )
        else:
            cmd.extend(
                [b'--src-prefix=a/%s/' % prefix, b'--dst-prefix=b/%s/' % prefix]
            )

        if diffopts.ignorews:
            cmd.append(b'--ignore-all-space')
        if diffopts.ignorewsamount:
            cmd.append(b'--ignore-space-change')
        if (
            self._gitversion(self._gitcommand([b'--version'])) >= (1, 8, 4)
            and diffopts.ignoreblanklines
        ):
            cmd.append(b'--ignore-blank-lines')

        cmd.append(node1)
        if node2:
            cmd.append(node2)

        output = b""
        if match.always():
            output += self._gitcommand(cmd) + b'\n'
        else:
            st = self.status(node2)
            files = [
                f
                for sublist in (st.modified, st.added, st.removed)
                for f in sublist
            ]
            for f in files:
                if match(f):
                    output += self._gitcommand(cmd + [b'--', f]) + b'\n'

        if output.strip():
            ui.write(output)

    @annotatesubrepoerror
    def revert(self, substate, *pats, **opts):
        self.ui.status(_(b'reverting subrepo %s\n') % substate[0])
        if not opts.get('no_backup'):
            status = self.status(None)
            names = status.modified
            for name in names:
                # backuppath() expects a path relative to the parent repo (the
                # repo that ui.origbackuppath is relative to)
                parentname = os.path.join(self._path, name)
                bakname = scmutil.backuppath(
                    self.ui, self._subparent, parentname
                )
                self.ui.note(
                    _(b'saving current version of %s as %s\n')
                    % (name, os.path.relpath(bakname))
                )
                util.rename(self.wvfs.join(name), bakname)

        if not opts.get('dry_run'):
            self.get(substate, overwrite=True)
        return []

    def shortid(self, revid):
        return revid[:7]


types = {
    b'hg': hgsubrepo,
    b'svn': svnsubrepo,
    b'git': gitsubrepo,
}
