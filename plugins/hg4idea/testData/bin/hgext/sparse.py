# sparse.py - allow sparse checkouts of the working directory
#
# Copyright 2014 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""allow sparse checkouts of the working directory (EXPERIMENTAL)

(This extension is not yet protected by backwards compatibility
guarantees. Any aspect may break in future releases until this
notice is removed.)

This extension allows the working directory to only consist of a
subset of files for the revision. This allows specific files or
directories to be explicitly included or excluded. Many repository
operations have performance proportional to the number of files in
the working directory. So only realizing a subset of files in the
working directory can improve performance.

Sparse Config Files
-------------------

The set of files that are part of a sparse checkout are defined by
a sparse config file. The file defines 3 things: includes (files to
include in the sparse checkout), excludes (files to exclude from the
sparse checkout), and profiles (links to other config files).

The file format is newline delimited. Empty lines and lines beginning
with ``#`` are ignored.

Lines beginning with ``%include `` denote another sparse config file
to include. e.g. ``%include tests.sparse``. The filename is relative
to the repository root.

The special lines ``[include]`` and ``[exclude]`` denote the section
for includes and excludes that follow, respectively. It is illegal to
have ``[include]`` after ``[exclude]``.

Non-special lines resemble file patterns to be added to either includes
or excludes. The syntax of these lines is documented by :hg:`help patterns`.
Patterns are interpreted as ``glob:`` by default and match against the
root of the repository.

Exclusion patterns take precedence over inclusion patterns. So even
if a file is explicitly included, an ``[exclude]`` entry can remove it.

For example, say you have a repository with 3 directories, ``frontend/``,
``backend/``, and ``tools/``. ``frontend/`` and ``backend/`` correspond
to different projects and it is uncommon for someone working on one
to need the files for the other. But ``tools/`` contains files shared
between both projects. Your sparse config files may resemble::

  # frontend.sparse
  frontend/**
  tools/**

  # backend.sparse
  backend/**
  tools/**

Say the backend grows in size. Or there's a directory with thousands
of files you wish to exclude. You can modify the profile to exclude
certain files::

  [include]
  backend/**
  tools/**

  [exclude]
  tools/tests/**
"""


from mercurial.i18n import _
from mercurial import (
    cmdutil,
    commands,
    error,
    extensions,
    logcmdutil,
    merge as mergemod,
    pycompat,
    registrar,
    sparse,
    util,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

cmdtable = {}
command = registrar.command(cmdtable)


def extsetup(ui):
    sparse.enabled = True

    _setupclone(ui)
    _setuplog(ui)
    _setupadd(ui)


def replacefilecache(cls, propname, replacement):
    """Replace a filecache property with a new class. This allows changing the
    cache invalidation condition."""
    origcls = cls
    assert callable(replacement)
    while cls is not object:
        if propname in cls.__dict__:
            orig = cls.__dict__[propname]
            setattr(cls, propname, replacement(orig))
            break
        cls = cls.__bases__[0]

    if cls is object:
        raise AttributeError(
            _(b"type '%s' has no property '%s'") % (origcls, propname)
        )


def _setuplog(ui):
    entry = commands.table[b'log|history']
    entry[1].append(
        (
            b'',
            b'sparse',
            None,
            b"limit to changesets affecting the sparse checkout",
        )
    )

    def _initialrevs(orig, repo, wopts):
        revs = orig(repo, wopts)
        if wopts.opts.get(b'sparse'):
            sparsematch = sparse.matcher(repo)

            def ctxmatch(rev):
                ctx = repo[rev]
                return any(f for f in ctx.files() if sparsematch(f))

            revs = revs.filter(ctxmatch)
        return revs

    extensions.wrapfunction(logcmdutil, '_initialrevs', _initialrevs)


def _clonesparsecmd(orig, ui, repo, *args, **opts):
    include = opts.get('include')
    exclude = opts.get('exclude')
    enableprofile = opts.get('enable_profile')
    narrow_pat = opts.get('narrow')

    # if --narrow is passed, it means they are includes and excludes for narrow
    # clone
    if not narrow_pat and (include or exclude or enableprofile):

        def clonesparse(orig, ctx, *args, **kwargs):
            sparse.updateconfig(
                ctx.repo().unfiltered(),
                {},
                include=include,
                exclude=exclude,
                enableprofile=enableprofile,
                usereporootpaths=True,
            )
            return orig(ctx, *args, **kwargs)

        extensions.wrapfunction(mergemod, 'update', clonesparse)
    return orig(ui, repo, *args, **opts)


def _setupclone(ui):
    entry = commands.table[b'clone']
    entry[1].append((b'', b'enable-profile', [], b'enable a sparse profile'))
    entry[1].append((b'', b'include', [], b'include sparse pattern'))
    entry[1].append((b'', b'exclude', [], b'exclude sparse pattern'))
    extensions.wrapcommand(commands.table, b'clone', _clonesparsecmd)


def _setupadd(ui):
    entry = commands.table[b'add']
    entry[1].append(
        (
            b's',
            b'sparse',
            None,
            b'also include directories of added files in sparse config',
        )
    )

    def _add(orig, ui, repo, *pats, **opts):
        if opts.get('sparse'):
            dirs = set()
            for pat in pats:
                dirname, basename = util.split(pat)
                dirs.add(dirname)
            sparse.updateconfig(repo, opts, include=list(dirs))
        return orig(ui, repo, *pats, **opts)

    extensions.wrapcommand(commands.table, b'add', _add)


@command(
    b'debugsparse',
    [
        (
            b'I',
            b'include',
            [],
            _(b'include files in the sparse checkout'),
            _(b'PATTERN'),
        ),
        (
            b'X',
            b'exclude',
            [],
            _(b'exclude files in the sparse checkout'),
            _(b'PATTERN'),
        ),
        (
            b'd',
            b'delete',
            [],
            _(b'delete an include/exclude rule'),
            _(b'PATTERN'),
        ),
        (
            b'f',
            b'force',
            False,
            _(b'allow changing rules even with pending changes'),
        ),
        (
            b'',
            b'enable-profile',
            [],
            _(b'enables the specified profile'),
            _(b'PATTERN'),
        ),
        (
            b'',
            b'disable-profile',
            [],
            _(b'disables the specified profile'),
            _(b'PATTERN'),
        ),
        (
            b'',
            b'import-rules',
            [],
            _(b'imports rules from a file'),
            _(b'PATTERN'),
        ),
        (b'', b'clear-rules', False, _(b'clears local include/exclude rules')),
        (
            b'',
            b'refresh',
            False,
            _(b'updates the working after sparseness changes'),
        ),
        (b'', b'reset', False, _(b'makes the repo full again')),
    ]
    + commands.templateopts,
    _(b'[--OPTION]'),
    helpbasic=True,
)
def debugsparse(ui, repo, **opts):
    """make the current checkout sparse, or edit the existing checkout

    The sparse command is used to make the current checkout sparse.
    This means files that don't meet the sparse condition will not be
    written to disk, or show up in any working copy operations. It does
    not affect files in history in any way.

    Passing no arguments prints the currently applied sparse rules.

    --include and --exclude are used to add and remove files from the sparse
    checkout. The effects of adding an include or exclude rule are applied
    immediately. If applying the new rule would cause a file with pending
    changes to be added or removed, the command will fail. Pass --force to
    force a rule change even with pending changes (the changes on disk will
    be preserved).

    --delete removes an existing include/exclude rule. The effects are
    immediate.

    --refresh refreshes the files on disk based on the sparse rules. This is
    only necessary if .hg/sparse was changed by hand.

    --enable-profile and --disable-profile accept a path to a .hgsparse file.
    This allows defining sparse checkouts and tracking them inside the
    repository. This is useful for defining commonly used sparse checkouts for
    many people to use. As the profile definition changes over time, the sparse
    checkout will automatically be updated appropriately, depending on which
    changeset is checked out. Changes to .hgsparse are not applied until they
    have been committed.

    --import-rules accepts a path to a file containing rules in the .hgsparse
    format, allowing you to add --include, --exclude and --enable-profile rules
    in bulk. Like the --include, --exclude and --enable-profile switches, the
    changes are applied immediately.

    --clear-rules removes all local include and exclude rules, while leaving
    any enabled profiles in place.

    Returns 0 if editing the sparse checkout succeeds.
    """
    opts = pycompat.byteskwargs(opts)
    include = opts.get(b'include')
    exclude = opts.get(b'exclude')
    force = opts.get(b'force')
    enableprofile = opts.get(b'enable_profile')
    disableprofile = opts.get(b'disable_profile')
    importrules = opts.get(b'import_rules')
    clearrules = opts.get(b'clear_rules')
    delete = opts.get(b'delete')
    refresh = opts.get(b'refresh')
    reset = opts.get(b'reset')
    action = cmdutil.check_at_most_one_arg(
        opts, b'import_rules', b'clear_rules', b'refresh'
    )
    updateconfig = bool(
        include or exclude or delete or reset or enableprofile or disableprofile
    )
    count = sum([updateconfig, bool(action)])
    if count > 1:
        raise error.Abort(_(b"too many flags specified"))

    # enable sparse on repo even if the requirements is missing.
    repo._has_sparse = True

    if count == 0:
        if repo.vfs.exists(b'sparse'):
            ui.status(repo.vfs.read(b"sparse") + b"\n")
            temporaryincludes = sparse.readtemporaryincludes(repo)
            if temporaryincludes:
                ui.status(
                    _(b"Temporarily Included Files (for merge/rebase):\n")
                )
                ui.status((b"\n".join(temporaryincludes) + b"\n"))
            return
        else:
            raise error.Abort(
                _(
                    b'the debugsparse command is only supported on'
                    b' sparse repositories'
                )
            )

    if updateconfig:
        sparse.updateconfig(
            repo,
            opts,
            include=include,
            exclude=exclude,
            reset=reset,
            delete=delete,
            enableprofile=enableprofile,
            disableprofile=disableprofile,
            force=force,
        )

    if importrules:
        sparse.importfromfiles(repo, opts, importrules, force=force)

    if clearrules:
        sparse.clearrules(repo, force=force)

    if refresh:
        with repo.wlock():
            fcounts = pycompat.maplist(
                len,
                sparse.refreshwdir(
                    repo, repo.status(), sparse.matcher(repo), force=force
                ),
            )
            sparse.printchanges(
                ui,
                opts,
                added=fcounts[0],
                dropped=fcounts[1],
                conflicting=fcounts[2],
            )

    del repo._has_sparse
