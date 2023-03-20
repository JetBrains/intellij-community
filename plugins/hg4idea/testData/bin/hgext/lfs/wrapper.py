# wrapper.py - methods wrapping core mercurial logic
#
# Copyright 2017 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import hashlib

from mercurial.i18n import _
from mercurial.node import bin, hex, short
from mercurial.pycompat import (
    getattr,
    setattr,
)

from mercurial import (
    bundle2,
    changegroup,
    cmdutil,
    context,
    error,
    exchange,
    exthelper,
    localrepo,
    pycompat,
    revlog,
    scmutil,
    util,
    vfs as vfsmod,
    wireprotov1server,
)

from mercurial.upgrade_utils import (
    actions as upgrade_actions,
    engine as upgrade_engine,
)

from mercurial.interfaces import repository

from mercurial.utils import (
    storageutil,
    stringutil,
)

from ..largefiles import lfutil

from . import (
    blobstore,
    pointer,
)

eh = exthelper.exthelper()


@eh.wrapfunction(localrepo, b'makefilestorage')
def localrepomakefilestorage(orig, requirements, features, **kwargs):
    if b'lfs' in requirements:
        features.add(repository.REPO_FEATURE_LFS)

    return orig(requirements=requirements, features=features, **kwargs)


@eh.wrapfunction(changegroup, b'allsupportedversions')
def allsupportedversions(orig, ui):
    versions = orig(ui)
    versions.add(b'03')
    return versions


@eh.wrapfunction(wireprotov1server, b'_capabilities')
def _capabilities(orig, repo, proto):
    '''Wrap server command to announce lfs server capability'''
    caps = orig(repo, proto)
    if util.safehasattr(repo.svfs, b'lfslocalblobstore'):
        # Advertise a slightly different capability when lfs is *required*, so
        # that the client knows it MUST load the extension.  If lfs is not
        # required on the server, there's no reason to autoload the extension
        # on the client.
        if b'lfs' in repo.requirements:
            caps.append(b'lfs-serve')

        caps.append(b'lfs')
    return caps


def bypasscheckhash(self, text):
    return False


def readfromstore(self, text):
    """Read filelog content from local blobstore transform for flagprocessor.

    Default tranform for flagprocessor, returning contents from blobstore.
    Returns a 2-typle (text, validatehash) where validatehash is True as the
    contents of the blobstore should be checked using checkhash.
    """
    p = pointer.deserialize(text)
    oid = p.oid()
    store = self.opener.lfslocalblobstore
    if not store.has(oid):
        p.filename = self.filename
        self.opener.lfsremoteblobstore.readbatch([p], store)

    # The caller will validate the content
    text = store.read(oid, verify=False)

    # pack hg filelog metadata
    hgmeta = {}
    for k in p.keys():
        if k.startswith(b'x-hg-'):
            name = k[len(b'x-hg-') :]
            hgmeta[name] = p[k]
    if hgmeta or text.startswith(b'\1\n'):
        text = storageutil.packmeta(hgmeta, text)

    return (text, True)


def writetostore(self, text):
    # hg filelog metadata (includes rename, etc)
    hgmeta, offset = storageutil.parsemeta(text)
    if offset and offset > 0:
        # lfs blob does not contain hg filelog metadata
        text = text[offset:]

    # git-lfs only supports sha256
    oid = hex(hashlib.sha256(text).digest())
    self.opener.lfslocalblobstore.write(oid, text)

    # replace contents with metadata
    longoid = b'sha256:%s' % oid
    metadata = pointer.gitlfspointer(oid=longoid, size=b'%d' % len(text))

    # by default, we expect the content to be binary. however, LFS could also
    # be used for non-binary content. add a special entry for non-binary data.
    # this will be used by filectx.isbinary().
    if not stringutil.binary(text):
        # not hg filelog metadata (affecting commit hash), no "x-hg-" prefix
        metadata[b'x-is-binary'] = b'0'

    # translate hg filelog metadata to lfs metadata with "x-hg-" prefix
    if hgmeta is not None:
        for k, v in pycompat.iteritems(hgmeta):
            metadata[b'x-hg-%s' % k] = v

    rawtext = metadata.serialize()
    return (rawtext, False)


def _islfs(rlog, node=None, rev=None):
    if rev is None:
        if node is None:
            # both None - likely working copy content where node is not ready
            return False
        rev = rlog.rev(node)
    else:
        node = rlog.node(rev)
    if node == rlog.nullid:
        return False
    flags = rlog.flags(rev)
    return bool(flags & revlog.REVIDX_EXTSTORED)


# Wrapping may also be applied by remotefilelog
def filelogaddrevision(
    orig,
    self,
    text,
    transaction,
    link,
    p1,
    p2,
    cachedelta=None,
    node=None,
    flags=revlog.REVIDX_DEFAULT_FLAGS,
    **kwds
):
    # The matcher isn't available if reposetup() wasn't called.
    lfstrack = self._revlog.opener.options.get(b'lfstrack')

    if lfstrack:
        textlen = len(text)
        # exclude hg rename meta from file size
        meta, offset = storageutil.parsemeta(text)
        if offset:
            textlen -= offset

        if lfstrack(self._revlog.filename, textlen):
            flags |= revlog.REVIDX_EXTSTORED

    return orig(
        self,
        text,
        transaction,
        link,
        p1,
        p2,
        cachedelta=cachedelta,
        node=node,
        flags=flags,
        **kwds
    )


# Wrapping may also be applied by remotefilelog
def filelogrenamed(orig, self, node):
    if _islfs(self._revlog, node):
        rawtext = self._revlog.rawdata(node)
        if not rawtext:
            return False
        metadata = pointer.deserialize(rawtext)
        if b'x-hg-copy' in metadata and b'x-hg-copyrev' in metadata:
            return metadata[b'x-hg-copy'], bin(metadata[b'x-hg-copyrev'])
        else:
            return False
    return orig(self, node)


# Wrapping may also be applied by remotefilelog
def filelogsize(orig, self, rev):
    if _islfs(self._revlog, rev=rev):
        # fast path: use lfs metadata to answer size
        rawtext = self._revlog.rawdata(rev)
        metadata = pointer.deserialize(rawtext)
        return int(metadata[b'size'])
    return orig(self, rev)


@eh.wrapfunction(revlog, b'_verify_revision')
def _verify_revision(orig, rl, skipflags, state, node):
    if _islfs(rl, node=node):
        rawtext = rl.rawdata(node)
        metadata = pointer.deserialize(rawtext)

        # Don't skip blobs that are stored locally, as local verification is
        # relatively cheap and there's no other way to verify the raw data in
        # the revlog.
        if rl.opener.lfslocalblobstore.has(metadata.oid()):
            skipflags &= ~revlog.REVIDX_EXTSTORED
        elif skipflags & revlog.REVIDX_EXTSTORED:
            # The wrapped method will set `skipread`, but there's enough local
            # info to check renames.
            state[b'safe_renamed'].add(node)

    orig(rl, skipflags, state, node)


@eh.wrapfunction(context.basefilectx, b'cmp')
def filectxcmp(orig, self, fctx):
    """returns True if text is different than fctx"""
    # some fctx (ex. hg-git) is not based on basefilectx and do not have islfs
    if self.islfs() and getattr(fctx, 'islfs', lambda: False)():
        # fast path: check LFS oid
        p1 = pointer.deserialize(self.rawdata())
        p2 = pointer.deserialize(fctx.rawdata())
        return p1.oid() != p2.oid()
    return orig(self, fctx)


@eh.wrapfunction(context.basefilectx, b'isbinary')
def filectxisbinary(orig, self):
    if self.islfs():
        # fast path: use lfs metadata to answer isbinary
        metadata = pointer.deserialize(self.rawdata())
        # if lfs metadata says nothing, assume it's binary by default
        return bool(int(metadata.get(b'x-is-binary', 1)))
    return orig(self)


def filectxislfs(self):
    return _islfs(self.filelog()._revlog, self.filenode())


@eh.wrapfunction(cmdutil, b'_updatecatformatter')
def _updatecatformatter(orig, fm, ctx, matcher, path, decode):
    orig(fm, ctx, matcher, path, decode)
    fm.data(rawdata=ctx[path].rawdata())


@eh.wrapfunction(scmutil, b'wrapconvertsink')
def convertsink(orig, sink):
    sink = orig(sink)
    if sink.repotype == b'hg':

        class lfssink(sink.__class__):
            def putcommit(
                self,
                files,
                copies,
                parents,
                commit,
                source,
                revmap,
                full,
                cleanp2,
            ):
                pc = super(lfssink, self).putcommit
                node = pc(
                    files,
                    copies,
                    parents,
                    commit,
                    source,
                    revmap,
                    full,
                    cleanp2,
                )

                if b'lfs' not in self.repo.requirements:
                    ctx = self.repo[node]

                    # The file list may contain removed files, so check for
                    # membership before assuming it is in the context.
                    if any(f in ctx and ctx[f].islfs() for f, n in files):
                        self.repo.requirements.add(b'lfs')
                        scmutil.writereporequirements(self.repo)

                return node

        sink.__class__ = lfssink

    return sink


# bundlerepo uses "vfsmod.readonlyvfs(othervfs)", we need to make sure lfs
# options and blob stores are passed from othervfs to the new readonlyvfs.
@eh.wrapfunction(vfsmod.readonlyvfs, b'__init__')
def vfsinit(orig, self, othervfs):
    orig(self, othervfs)
    # copy lfs related options
    for k, v in othervfs.options.items():
        if k.startswith(b'lfs'):
            self.options[k] = v
    # also copy lfs blobstores. note: this can run before reposetup, so lfs
    # blobstore attributes are not always ready at this time.
    for name in [b'lfslocalblobstore', b'lfsremoteblobstore']:
        if util.safehasattr(othervfs, name):
            setattr(self, name, getattr(othervfs, name))


def _prefetchfiles(repo, revmatches):
    """Ensure that required LFS blobs are present, fetching them as a group if
    needed."""
    if not util.safehasattr(repo.svfs, b'lfslocalblobstore'):
        return

    pointers = []
    oids = set()
    localstore = repo.svfs.lfslocalblobstore

    for rev, match in revmatches:
        ctx = repo[rev]
        for f in ctx.walk(match):
            p = pointerfromctx(ctx, f)
            if p and p.oid() not in oids and not localstore.has(p.oid()):
                p.filename = f
                pointers.append(p)
                oids.add(p.oid())

    if pointers:
        # Recalculating the repo store here allows 'paths.default' that is set
        # on the repo by a clone command to be used for the update.
        blobstore.remote(repo).readbatch(pointers, localstore)


def _canskipupload(repo):
    # Skip if this hasn't been passed to reposetup()
    if not util.safehasattr(repo.svfs, b'lfsremoteblobstore'):
        return True

    # if remotestore is a null store, upload is a no-op and can be skipped
    return isinstance(repo.svfs.lfsremoteblobstore, blobstore._nullremote)


def candownload(repo):
    # Skip if this hasn't been passed to reposetup()
    if not util.safehasattr(repo.svfs, b'lfsremoteblobstore'):
        return False

    # if remotestore is a null store, downloads will lead to nothing
    return not isinstance(repo.svfs.lfsremoteblobstore, blobstore._nullremote)


def uploadblobsfromrevs(repo, revs):
    """upload lfs blobs introduced by revs

    Note: also used by other extensions e. g. infinitepush. avoid renaming.
    """
    if _canskipupload(repo):
        return
    pointers = extractpointers(repo, revs)
    uploadblobs(repo, pointers)


def prepush(pushop):
    """Prepush hook.

    Read through the revisions to push, looking for filelog entries that can be
    deserialized into metadata so that we can block the push on their upload to
    the remote blobstore.
    """
    return uploadblobsfromrevs(pushop.repo, pushop.outgoing.missing)


@eh.wrapfunction(exchange, b'push')
def push(orig, repo, remote, *args, **kwargs):
    """bail on push if the extension isn't enabled on remote when needed, and
    update the remote store based on the destination path."""
    if b'lfs' in repo.requirements:
        # If the remote peer is for a local repo, the requirement tests in the
        # base class method enforce lfs support.  Otherwise, some revisions in
        # this repo use lfs, and the remote repo needs the extension loaded.
        if not remote.local() and not remote.capable(b'lfs'):
            # This is a copy of the message in exchange.push() when requirements
            # are missing between local repos.
            m = _(b"required features are not supported in the destination: %s")
            raise error.Abort(
                m % b'lfs', hint=_(b'enable the lfs extension on the server')
            )

        # Repositories where this extension is disabled won't have the field.
        # But if there's a requirement, then the extension must be loaded AND
        # there may be blobs to push.
        remotestore = repo.svfs.lfsremoteblobstore
        try:
            repo.svfs.lfsremoteblobstore = blobstore.remote(repo, remote.url())
            return orig(repo, remote, *args, **kwargs)
        finally:
            repo.svfs.lfsremoteblobstore = remotestore
    else:
        return orig(repo, remote, *args, **kwargs)


# when writing a bundle via "hg bundle" command, upload related LFS blobs
@eh.wrapfunction(bundle2, b'writenewbundle')
def writenewbundle(
    orig, ui, repo, source, filename, bundletype, outgoing, *args, **kwargs
):
    """upload LFS blobs added by outgoing revisions on 'hg bundle'"""
    uploadblobsfromrevs(repo, outgoing.missing)
    return orig(
        ui, repo, source, filename, bundletype, outgoing, *args, **kwargs
    )


def extractpointers(repo, revs):
    """return a list of lfs pointers added by given revs"""
    repo.ui.debug(b'lfs: computing set of blobs to upload\n')
    pointers = {}

    makeprogress = repo.ui.makeprogress
    with makeprogress(
        _(b'lfs search'), _(b'changesets'), len(revs)
    ) as progress:
        for r in revs:
            ctx = repo[r]
            for p in pointersfromctx(ctx).values():
                pointers[p.oid()] = p
            progress.increment()
        return sorted(pointers.values(), key=lambda p: p.oid())


def pointerfromctx(ctx, f, removed=False):
    """return a pointer for the named file from the given changectx, or None if
    the file isn't LFS.

    Optionally, the pointer for a file deleted from the context can be returned.
    Since no such pointer is actually stored, and to distinguish from a non LFS
    file, this pointer is represented by an empty dict.
    """
    _ctx = ctx
    if f not in ctx:
        if not removed:
            return None
        if f in ctx.p1():
            _ctx = ctx.p1()
        elif f in ctx.p2():
            _ctx = ctx.p2()
        else:
            return None
    fctx = _ctx[f]
    if not _islfs(fctx.filelog()._revlog, fctx.filenode()):
        return None
    try:
        p = pointer.deserialize(fctx.rawdata())
        if ctx == _ctx:
            return p
        return {}
    except pointer.InvalidPointer as ex:
        raise error.Abort(
            _(b'lfs: corrupted pointer (%s@%s): %s\n')
            % (f, short(_ctx.node()), ex)
        )


def pointersfromctx(ctx, removed=False):
    """return a dict {path: pointer} for given single changectx.

    If ``removed`` == True and the LFS file was removed from ``ctx``, the value
    stored for the path is an empty dict.
    """
    result = {}
    m = ctx.repo().narrowmatch()

    # TODO: consider manifest.fastread() instead
    for f in ctx.files():
        if not m(f):
            continue
        p = pointerfromctx(ctx, f, removed=removed)
        if p is not None:
            result[f] = p
    return result


def uploadblobs(repo, pointers):
    """upload given pointers from local blobstore"""
    if not pointers:
        return

    remoteblob = repo.svfs.lfsremoteblobstore
    remoteblob.writebatch(pointers, repo.svfs.lfslocalblobstore)


@eh.wrapfunction(upgrade_engine, b'finishdatamigration')
def upgradefinishdatamigration(orig, ui, srcrepo, dstrepo, requirements):
    orig(ui, srcrepo, dstrepo, requirements)

    # Skip if this hasn't been passed to reposetup()
    if util.safehasattr(
        srcrepo.svfs, b'lfslocalblobstore'
    ) and util.safehasattr(dstrepo.svfs, b'lfslocalblobstore'):
        srclfsvfs = srcrepo.svfs.lfslocalblobstore.vfs
        dstlfsvfs = dstrepo.svfs.lfslocalblobstore.vfs

        for dirpath, dirs, files in srclfsvfs.walk():
            for oid in files:
                ui.write(_(b'copying lfs blob %s\n') % oid)
                lfutil.link(srclfsvfs.join(oid), dstlfsvfs.join(oid))


@eh.wrapfunction(upgrade_actions, b'preservedrequirements')
@eh.wrapfunction(upgrade_actions, b'supporteddestrequirements')
def upgraderequirements(orig, repo):
    reqs = orig(repo)
    if b'lfs' in repo.requirements:
        reqs.add(b'lfs')
    return reqs
