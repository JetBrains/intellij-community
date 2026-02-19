# verify.py - repository integrity checking for Mercurial
#
# Copyright 2006, 2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import os

from .i18n import _
from .node import short
from .utils import stringutil

from . import (
    error,
    pycompat,
    requirements,
    revlog,
    transaction,
    util,
)

VERIFY_DEFAULT = 0
VERIFY_FULL = 1


def verify(repo, level=None):
    with repo.lock():
        v = verifier(repo, level)
        return v.verify()


def _normpath(f):
    # under hg < 2.4, convert didn't sanitize paths properly, so a
    # converted repo may contain repeated slashes
    while b'//' in f:
        f = f.replace(b'//', b'/')
    return f


HINT_FNCACHE = _(
    b'hint: run "hg debugrebuildfncache" to recover from corrupt fncache\n'
)

WARN_PARENT_DIR_UNKNOWN_REV = _(
    b"parent-directory manifest refers to unknown revision %s"
)

WARN_UNKNOWN_COPY_SOURCE = _(
    b"warning: copy source of '%s' not in parents of %s"
)

WARN_NULLID_COPY_SOURCE = _(
    b"warning: %s@%s: copy source revision is nullid %s:%s\n"
)


class verifier:
    def __init__(self, repo, level=None):
        self.repo = repo.unfiltered()
        self.ui = repo.ui
        self.match = repo.narrowmatch()
        if level is None:
            level = VERIFY_DEFAULT
        self._level = level
        self.badrevs = set()
        self.errors = 0
        self.warnings = 0
        self.havecl = len(repo.changelog) > 0
        self.havemf = len(repo.manifestlog.getstorage(b'')) > 0
        self.revlogv1 = repo.changelog._format_version != revlog.REVLOGV0
        self.lrugetctx = util.lrucachefunc(repo.unfiltered().__getitem__)
        self.refersmf = False
        self.fncachewarned = False
        # developer config: verify.skipflags
        self.skipflags = repo.ui.configint(b'verify', b'skipflags')
        self.warnorphanstorefiles = True

    def _warn(self, msg):
        """record a "warning" level issue"""
        self.ui.warn(msg + b"\n")
        self.warnings += 1

    def _err(self, linkrev, msg, filename=None):
        """record a "error" level issue"""
        if linkrev is not None:
            self.badrevs.add(linkrev)
            linkrev = b"%d" % linkrev
        else:
            linkrev = b'?'
        msg = b"%s: %s" % (linkrev, msg)
        if filename:
            msg = b"%s@%s" % (filename, msg)
        self.ui.warn(b" " + msg + b"\n")
        self.errors += 1

    def _exc(self, linkrev, msg, inst, filename=None):
        """record exception raised during the verify process"""
        fmsg = stringutil.forcebytestr(inst)
        if not fmsg:
            fmsg = pycompat.byterepr(inst)
        self._err(linkrev, b"%s: %s" % (msg, fmsg), filename)

    def _checkrevlog(self, obj, name, linkrev):
        """verify high level property of a revlog

        - revlog is present,
        - revlog is non-empty,
        - sizes (index and data) are correct,
        - revlog's format version is correct.
        """
        if not len(obj) and (self.havecl or self.havemf):
            self._err(linkrev, _(b"empty or missing %s") % name)
            return

        d = obj.checksize()
        if d[0]:
            self._err(None, _(b"data length off by %d bytes") % d[0], name)
        if d[1]:
            self._err(None, _(b"index contains %d extra bytes") % d[1], name)

        if obj._format_version != revlog.REVLOGV0:
            if not self.revlogv1:
                self._warn(_(b"warning: `%s' uses revlog format 1") % name)
        elif self.revlogv1:
            self._warn(_(b"warning: `%s' uses revlog format 0") % name)

    def _checkentry(self, obj, i, node, seen, linkrevs, f):
        """verify a single revlog entry

        arguments are:
        - obj:      the source revlog
        - i:        the revision number
        - node:     the revision node id
        - seen:     nodes previously seen for this revlog
        - linkrevs: [changelog-revisions] introducing "node"
        - f:        string label ("changelog", "manifest", or filename)

        Performs the following checks:
        - linkrev points to an existing changelog revision,
        - linkrev points to a changelog revision that introduces this revision,
        - linkrev points to the lowest of these changesets,
        - both parents exist in the revlog,
        - the revision is not duplicated.

        Return the linkrev of the revision (or None for changelog's revisions).
        """
        lr = obj.linkrev(obj.rev(node))
        if lr < 0 or (self.havecl and lr not in linkrevs):
            if lr < 0 or lr >= len(self.repo.changelog):
                msg = _(b"rev %d points to nonexistent changeset %d")
            else:
                msg = _(b"rev %d points to unexpected changeset %d")
            self._err(None, msg % (i, lr), f)
            if linkrevs:
                if f and len(linkrevs) > 1:
                    try:
                        # attempt to filter down to real linkrevs
                        linkrevs = []
                        for lr in linkrevs:
                            if self.lrugetctx(lr)[f].filenode() == node:
                                linkrevs.append(lr)
                    except Exception:
                        pass
                msg = _(b" (expected %s)")
                msg %= b" ".join(map(pycompat.bytestr, linkrevs))
                self._warn(msg)
            lr = None  # can't be trusted

        try:
            p1, p2 = obj.parents(node)
            if p1 not in seen and p1 != self.repo.nullid:
                msg = _(b"unknown parent 1 %s of %s") % (short(p1), short(node))
                self._err(lr, msg, f)
            if p2 not in seen and p2 != self.repo.nullid:
                msg = _(b"unknown parent 2 %s of %s") % (short(p2), short(node))
                self._err(lr, msg, f)
        except Exception as inst:
            self._exc(lr, _(b"checking parents of %s") % short(node), inst, f)

        if node in seen:
            self._err(lr, _(b"duplicate revision %d (%d)") % (i, seen[node]), f)
        seen[node] = i
        return lr

    def verify(self):
        """verify the content of the Mercurial repository

        This method run all verifications, displaying issues as they are found.

        return 1 if any error have been encountered, 0 otherwise."""
        # initial validation and generic report
        repo = self.repo
        ui = repo.ui
        if not repo.url().startswith(b'file:'):
            raise error.Abort(_(b"cannot verify bundle or remote repos"))

        if transaction.has_abandoned_transaction(repo):
            ui.warn(_(b"abandoned transaction found - run hg recover\n"))

        if ui.verbose or not self.revlogv1:
            ui.status(
                _(b"repository uses revlog format %d\n")
                % (self.revlogv1 and 1 or 0)
            )

        # data verification
        mflinkrevs, filelinkrevs = self._verifychangelog()
        filenodes = self._verifymanifest(mflinkrevs)
        del mflinkrevs
        self._crosscheckfiles(filelinkrevs, filenodes)
        totalfiles, filerevisions = self._verifyfiles(filenodes, filelinkrevs)

        if self.errors:
            ui.warn(_(b"not checking dirstate because of previous errors\n"))
            dirstate_errors = 0
        else:
            dirstate_errors = self._verify_dirstate()

        # final report
        ui.status(
            _(b"checked %d changesets with %d changes to %d files\n")
            % (len(repo.changelog), filerevisions, totalfiles)
        )
        if self.warnings:
            ui.warn(_(b"%d warnings encountered!\n") % self.warnings)
        if self.fncachewarned:
            ui.warn(HINT_FNCACHE)
        if self.errors:
            ui.warn(_(b"%d integrity errors encountered!\n") % self.errors)
            if self.badrevs:
                msg = _(b"(first damaged changeset appears to be %d)\n")
                msg %= min(self.badrevs)
                ui.warn(msg)
            if dirstate_errors:
                ui.warn(
                    _(b"dirstate inconsistent with current parent's manifest\n")
                )
                ui.warn(_(b"%d dirstate errors\n") % dirstate_errors)
            return 1
        return 0

    def _verifychangelog(self):
        """verify the changelog of a repository

        The following checks are performed:
        - all of `_checkrevlog` checks,
        - all of `_checkentry` checks (for each revisions),
        - each revision can be read.

        The function returns some of the data observed in the changesets as a
        (mflinkrevs, filelinkrevs) tuples:
        - mflinkrevs:   is a { manifest-node -> [changelog-rev] } mapping
        - filelinkrevs: is a { file-path -> [changelog-rev] } mapping

        If a matcher was specified, filelinkrevs will only contains matched
        files.
        """
        ui = self.ui
        repo = self.repo
        match = self.match
        cl = repo.changelog

        ui.status(_(b"checking changesets\n"))
        mflinkrevs = {}
        filelinkrevs = {}
        seen = {}
        self._checkrevlog(cl, b"changelog", 0)
        progress = ui.makeprogress(
            _(b'checking'), unit=_(b'changesets'), total=len(repo)
        )
        with cl.reading():
            for i in repo:
                progress.update(i)
                n = cl.node(i)
                self._checkentry(cl, i, n, seen, [i], b"changelog")

                try:
                    changes = cl.read(n)
                    if changes[0] != self.repo.nullid:
                        mflinkrevs.setdefault(changes[0], []).append(i)
                        self.refersmf = True
                    for f in changes[3]:
                        if match(f):
                            filelinkrevs.setdefault(_normpath(f), []).append(i)
                except Exception as inst:
                    self.refersmf = True
                    self._exc(i, _(b"unpacking changeset %s") % short(n), inst)
        progress.complete()
        return mflinkrevs, filelinkrevs

    def _verifymanifest(
        self, mflinkrevs, dir=b"", storefiles=None, subdirprogress=None
    ):
        """verify the manifestlog content

        Inputs:
        - mflinkrevs:     a {manifest-node -> [changelog-revisions]} mapping
        - dir:            a subdirectory to check (for tree manifest repo)
        - storefiles:     set of currently "orphan" files.
        - subdirprogress: a progress object

        This function checks:
        * all of `_checkrevlog` checks (for all manifest related revlogs)
        * all of `_checkentry` checks (for all manifest related revisions)
        * nodes for subdirectory exists in the sub-directory manifest
        * each manifest entries have a file path
        * each manifest node refered in mflinkrevs exist in the manifest log

        If tree manifest is in use and a matchers is specified, only the
        sub-directories matching it will be verified.

        return a two level mapping:
            {"path" -> { filenode -> changelog-revision}}

        This mapping primarily contains entries for every files in the
        repository. In addition, when tree-manifest is used, it also contains
        sub-directory entries.

        If a matcher is provided, only matching paths will be included.
        """
        repo = self.repo
        ui = self.ui
        match = self.match
        mfl = self.repo.manifestlog
        mf = mfl.getstorage(dir)

        if not dir:
            self.ui.status(_(b"checking manifests\n"))

        filenodes = {}
        subdirnodes = {}
        seen = {}
        label = b"manifest"
        if dir:
            label = dir
            revlogfiles = mf.files()
            storefiles.difference_update(revlogfiles)
            if subdirprogress:  # should be true since we're in a subdirectory
                subdirprogress.increment()
        if self.refersmf:
            # Do not check manifest if there are only changelog entries with
            # null manifests.
            self._checkrevlog(mf._revlog, label, 0)
        progress = ui.makeprogress(
            _(b'checking'), unit=_(b'manifests'), total=len(mf)
        )
        for i in mf:
            if not dir:
                progress.update(i)
            n = mf.node(i)
            lr = self._checkentry(mf, i, n, seen, mflinkrevs.get(n, []), label)
            if n in mflinkrevs:
                del mflinkrevs[n]
            elif dir:
                msg = _(b"%s not in parent-directory manifest") % short(n)
                self._err(lr, msg, label)
            else:
                self._err(lr, _(b"%s not in changesets") % short(n), label)

            try:
                mfdelta = mfl.get(dir, n).readdelta(shallow=True)
                for f, fn, fl in mfdelta.iterentries():
                    if not f:
                        self._err(lr, _(b"entry without name in manifest"))
                    elif f == b"/dev/null":  # ignore this in very old repos
                        continue
                    fullpath = dir + _normpath(f)
                    if fl == b't':
                        if not match.visitdir(fullpath):
                            continue
                        sdn = subdirnodes.setdefault(fullpath + b'/', {})
                        sdn.setdefault(fn, []).append(lr)
                    else:
                        if not match(fullpath):
                            continue
                        filenodes.setdefault(fullpath, {}).setdefault(fn, lr)
            except Exception as inst:
                self._exc(lr, _(b"reading delta %s") % short(n), inst, label)
            if self._level >= VERIFY_FULL:
                try:
                    # Various issues can affect manifest. So we read each full
                    # text from storage. This triggers the checks from the core
                    # code (eg: hash verification, filename are ordered, etc.)
                    mfdelta = mfl.get(dir, n).read()
                except Exception as inst:
                    msg = _(b"reading full manifest %s") % short(n)
                    self._exc(lr, msg, inst, label)

        if not dir:
            progress.complete()

        if self.havemf:
            # since we delete entry in `mflinkrevs` during iteration, any
            # remaining entries are "missing". We need to issue errors for them.
            changesetpairs = [(c, m) for m in mflinkrevs for c in mflinkrevs[m]]
            for c, m in sorted(changesetpairs):
                if dir:
                    self._err(c, WARN_PARENT_DIR_UNKNOWN_REV % short(m), label)
                else:
                    msg = _(b"changeset refers to unknown revision %s")
                    msg %= short(m)
                    self._err(c, msg, label)

        if not dir and subdirnodes:
            self.ui.status(_(b"checking directory manifests\n"))
            storefiles = set()
            subdirs = set()
            revlogv1 = self.revlogv1
            undecodable = []
            for entry in repo.store.data_entries(undecodable=undecodable):
                for file_ in entry.files():
                    f = file_.unencoded_path
                    size = file_.file_size(repo.store.vfs)
                    if (size > 0 or not revlogv1) and f.startswith(b'meta/'):
                        storefiles.add(_normpath(f))
                        subdirs.add(os.path.dirname(f))
            for f in undecodable:
                self._err(None, _(b"cannot decode filename '%s'") % f)
            subdirprogress = ui.makeprogress(
                _(b'checking'), unit=_(b'manifests'), total=len(subdirs)
            )

        for subdir, linkrevs in subdirnodes.items():
            subdirfilenodes = self._verifymanifest(
                linkrevs, subdir, storefiles, subdirprogress
            )
            for f, onefilenodes in subdirfilenodes.items():
                filenodes.setdefault(f, {}).update(onefilenodes)

        if not dir and subdirnodes:
            assert subdirprogress is not None  # help pytype
            subdirprogress.complete()
            if self.warnorphanstorefiles:
                for f in sorted(storefiles):
                    self._warn(_(b"warning: orphan data file '%s'") % f)

        return filenodes

    def _crosscheckfiles(self, filelinkrevs, filenodes):
        repo = self.repo
        ui = self.ui
        ui.status(_(b"crosschecking files in changesets and manifests\n"))

        total = len(filelinkrevs) + len(filenodes)
        progress = ui.makeprogress(
            _(b'crosschecking'), unit=_(b'files'), total=total
        )
        if self.havemf:
            for f in sorted(filelinkrevs):
                progress.increment()
                if f not in filenodes:
                    lr = filelinkrevs[f][0]
                    self._err(lr, _(b"in changeset but not in manifest"), f)

        if self.havecl:
            for f in sorted(filenodes):
                progress.increment()
                if f not in filelinkrevs:
                    try:
                        fl = repo.file(f)
                        lr = min([fl.linkrev(fl.rev(n)) for n in filenodes[f]])
                    except Exception:
                        lr = None
                    self._err(lr, _(b"in manifest but not in changeset"), f)

        progress.complete()

    def _verifyfiles(self, filenodes, filelinkrevs):
        repo = self.repo
        ui = self.ui
        lrugetctx = self.lrugetctx
        revlogv1 = self.revlogv1
        havemf = self.havemf
        ui.status(_(b"checking files\n"))

        storefiles = set()
        undecodable = []
        for entry in repo.store.data_entries(undecodable=undecodable):
            for file_ in entry.files():
                size = file_.file_size(repo.store.vfs)
                f = file_.unencoded_path
                if (size > 0 or not revlogv1) and f.startswith(b'data/'):
                    storefiles.add(_normpath(f))
        for f in undecodable:
            self._err(None, _(b"cannot decode filename '%s'") % f)

        state = {
            # TODO this assumes revlog storage for changelog.
            b'expectedversion': self.repo.changelog._format_version,
            b'skipflags': self.skipflags,
            # experimental config: censor.policy
            b'erroroncensored': ui.config(b'censor', b'policy') == b'abort',
        }

        files = sorted(set(filenodes) | set(filelinkrevs))
        revisions = 0
        progress = ui.makeprogress(
            _(b'checking'), unit=_(b'files'), total=len(files)
        )
        for i, f in enumerate(files):
            progress.update(i, item=f)
            try:
                linkrevs = filelinkrevs[f]
            except KeyError:
                # in manifest but not in changelog
                linkrevs = []

            if linkrevs:
                lr = linkrevs[0]
            else:
                lr = None

            try:
                fl = repo.file(f)
            except error.StorageError as e:
                self._err(lr, _(b"broken revlog! (%s)") % e, f)
                continue

            for ff in fl.files():
                try:
                    storefiles.remove(ff)
                except KeyError:
                    if self.warnorphanstorefiles:
                        msg = _(b" warning: revlog '%s' not in fncache!")
                        self._warn(msg % ff)
                        self.fncachewarned = True

            if not len(fl) and (self.havecl or self.havemf):
                self._err(lr, _(b"empty or missing %s") % f)
            else:
                # Guard against implementations not setting this.
                state[b'skipread'] = set()
                state[b'safe_renamed'] = set()

                for problem in fl.verifyintegrity(state):
                    if problem.node is not None:
                        linkrev = fl.linkrev(fl.rev(problem.node))
                    else:
                        linkrev = None

                    if problem.warning:
                        self._warn(problem.warning)
                    elif problem.error:
                        linkrev_msg = linkrev if linkrev is not None else lr
                        self._err(linkrev_msg, problem.error, f)
                    else:
                        raise error.ProgrammingError(
                            b'problem instance does not set warning or error '
                            b'attribute: %s' % problem.msg
                        )

            seen = {}
            for i in fl:
                revisions += 1
                n = fl.node(i)
                lr = self._checkentry(fl, i, n, seen, linkrevs, f)
                if f in filenodes:
                    if havemf and n not in filenodes[f]:
                        self._err(lr, _(b"%s not in manifests") % (short(n)), f)
                    else:
                        del filenodes[f][n]

                if n in state[b'skipread'] and n not in state[b'safe_renamed']:
                    continue

                # check renames
                try:
                    # This requires resolving fulltext (at least on revlogs,
                    # though not with LFS revisions). We may want
                    # ``verifyintegrity()`` to pass a set of nodes with
                    # rename metadata as an optimization.
                    rp = fl.renamed(n)
                    if rp:
                        if lr is not None and ui.verbose:
                            ctx = lrugetctx(lr)
                            if not any(rp[0] in pctx for pctx in ctx.parents()):
                                self._warn(WARN_UNKNOWN_COPY_SOURCE % (f, ctx))
                        fl2 = repo.file(rp[0])
                        if not len(fl2):
                            m = _(b"empty or missing copy source revlog %s:%s")
                            self._err(lr, m % (rp[0], short(rp[1])), f)
                        elif rp[1] == self.repo.nullid:
                            msg = WARN_NULLID_COPY_SOURCE
                            msg %= (f, lr, rp[0], short(rp[1]))
                            ui.note(msg)
                        else:
                            fl2.rev(rp[1])
                except Exception as inst:
                    self._exc(
                        lr, _(b"checking rename of %s") % short(n), inst, f
                    )

            # cross-check
            if f in filenodes:
                fns = [(v, k) for k, v in filenodes[f].items()]
                for lr, node in sorted(fns):
                    msg = _(b"manifest refers to unknown revision %s")
                    self._err(lr, msg % short(node), f)
        progress.complete()

        if self.warnorphanstorefiles:
            for f in sorted(storefiles):
                self._warn(_(b"warning: orphan data file '%s'") % f)

        return len(files), revisions

    def _verify_dirstate(self):
        """Check that the dirstate is consistent with the parent's manifest"""
        repo = self.repo
        ui = self.ui
        ui.status(_(b"checking dirstate\n"))

        parent1, parent2 = repo.dirstate.parents()
        m1 = repo[parent1].manifest()
        m2 = repo[parent2].manifest()
        dirstate_errors = 0

        is_narrow = requirements.NARROW_REQUIREMENT in repo.requirements
        narrow_matcher = repo.narrowmatch() if is_narrow else None

        for err in repo.dirstate.verify(m1, m2, parent1, narrow_matcher):
            ui.error(err)
            dirstate_errors += 1

        if dirstate_errors:
            self.errors += dirstate_errors
        return dirstate_errors
