# Copyright 2006, 2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''share a common history between several working directories

The share extension introduces a new command :hg:`share` to create a new
working directory. This is similar to :hg:`clone`, but doesn't involve
copying or linking the storage of the repository. This allows working on
different branches or changes in parallel without the associated cost in
terms of disk space.

Note: destructive operations or extensions like :hg:`rollback` should be
used with care as they can result in confusing problems.

Automatic Pooled Storage for Clones
-----------------------------------

When this extension is active, :hg:`clone` can be configured to
automatically share/pool storage across multiple clones. This
mode effectively converts :hg:`clone` to :hg:`clone` + :hg:`share`.
The benefit of using this mode is the automatic management of
store paths and intelligent pooling of related repositories.

The following ``share.`` config options influence this feature:

``share.pool``
    Filesystem path where shared repository data will be stored. When
    defined, :hg:`clone` will automatically use shared repository
    storage instead of creating a store inside each clone.

``share.poolnaming``
    How directory names in ``share.pool`` are constructed.

    "identity" means the name is derived from the first changeset in the
    repository. In this mode, different remotes share storage if their
    root/initial changeset is identical. In this mode, the local shared
    repository is an aggregate of all encountered remote repositories.

    "remote" means the name is derived from the source repository's
    path or URL. In this mode, storage is only shared if the path or URL
    requested in the :hg:`clone` command matches exactly to a repository
    that was cloned before.

    The default naming mode is "identity".

.. container:: verbose

    Sharing requirements and configs of source repository with shares:

    By default creating a shared repository only enables sharing a common
    history and does not share requirements and configs between them. This
    may lead to problems in some cases, for example when you upgrade the
    storage format from one repository but does not set related configs
    in the shares.

    Setting `format.exp-share-safe = True` enables sharing configs and
    requirements. This only applies to shares which are done after enabling
    the config option.

    For enabling this in existing shares, enable the config option and reshare.

    For resharing existing shares, make sure your working directory is clean
    and there are no untracked files, delete that share and create a new share.
'''


from mercurial.i18n import _
from mercurial import (
    bookmarks,
    commands,
    error,
    extensions,
    hg,
    registrar,
    txnutil,
    util,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


@command(
    b'share',
    [
        (b'U', b'noupdate', None, _(b'do not create a working directory')),
        (b'B', b'bookmarks', None, _(b'also share bookmarks')),
        (
            b'',
            b'relative',
            None,
            _(b'point to source using a relative path'),
        ),
    ],
    _(b'[-U] [-B] SOURCE [DEST]'),
    helpcategory=command.CATEGORY_REPO_CREATION,
    norepo=True,
)
def share(
    ui, source, dest=None, noupdate=False, bookmarks=False, relative=False
):
    """create a new shared repository

    Initialize a new repository and working directory that shares its
    history (and optionally bookmarks) with another repository.

    .. note::

       using rollback or extensions that destroy/modify history (mq,
       rebase, etc.) can cause considerable confusion with shared
       clones. In particular, if two shared clones are both updated to
       the same changeset, and one of them destroys that changeset
       with rollback, the other clone will suddenly stop working: all
       operations will fail with "abort: working directory has unknown
       parent". The only known workaround is to use debugsetparents on
       the broken clone to reset it to a changeset that still exists.
    """

    hg.share(
        ui,
        source,
        dest=dest,
        update=not noupdate,
        bookmarks=bookmarks,
        relative=relative,
    )
    return 0


@command(b'unshare', [], b'', helpcategory=command.CATEGORY_MAINTENANCE)
def unshare(ui, repo):
    """convert a shared repository to a normal one

    Copy the store data to the repo and remove the sharedpath data.
    """

    if not repo.shared():
        raise error.Abort(_(b"this is not a shared repo"))

    hg.unshare(ui, repo)


# Wrap clone command to pass auto share options.
def clone(orig, ui, source, *args, **opts):
    pool = ui.config(b'share', b'pool')
    if pool:
        pool = util.expandpath(pool)

    opts['shareopts'] = {
        b'pool': pool,
        b'mode': ui.config(b'share', b'poolnaming'),
    }

    return orig(ui, source, *args, **opts)


def extsetup(ui):
    extensions.wrapfunction(bookmarks, '_getbkfile', getbkfile)
    extensions.wrapfunction(bookmarks.bmstore, '_recordchange', recordchange)
    extensions.wrapfunction(bookmarks.bmstore, '_writerepo', writerepo)
    extensions.wrapcommand(commands.table, b'clone', clone)


def _hassharedbookmarks(repo):
    """Returns whether this repo has shared bookmarks"""
    if bookmarks.bookmarksinstore(repo):
        # Kind of a lie, but it means that we skip our custom reads and writes
        # from/to the source repo.
        return False
    try:
        shared = repo.vfs.read(b'shared').splitlines()
    except FileNotFoundError:
        return False
    return hg.sharedbookmarks in shared


def getbkfile(orig, repo):
    if _hassharedbookmarks(repo):
        srcrepo = hg.sharedreposource(repo)
        if srcrepo is not None:
            # just orig(srcrepo) doesn't work as expected, because
            # HG_PENDING refers repo.root.
            try:
                fp, pending = txnutil.trypending(
                    repo.root, repo.vfs, b'bookmarks'
                )
                if pending:
                    # only in this case, bookmark information in repo
                    # is up-to-date.
                    return fp
                fp.close()
            except FileNotFoundError:
                pass

            # otherwise, we should read bookmarks from srcrepo,
            # because .hg/bookmarks in srcrepo might be already
            # changed via another sharing repo
            repo = srcrepo

            # TODO: Pending changes in repo are still invisible in
            # srcrepo, because bookmarks.pending is written only into repo.
            # See also https://www.mercurial-scm.org/wiki/SharedRepository
    return orig(repo)


def recordchange(orig, self, tr):
    # Continue with write to local bookmarks file as usual
    orig(self, tr)

    if _hassharedbookmarks(self._repo):
        srcrepo = hg.sharedreposource(self._repo)
        if srcrepo is not None:
            category = b'share-bookmarks'
            tr.addpostclose(category, lambda tr: self._writerepo(srcrepo))


def writerepo(orig, self, repo):
    # First write local bookmarks file in case we ever unshare
    orig(self, repo)

    if _hassharedbookmarks(self._repo):
        srcrepo = hg.sharedreposource(self._repo)
        if srcrepo is not None:
            orig(self, srcrepo)
