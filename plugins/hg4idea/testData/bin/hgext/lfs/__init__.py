# lfs - hash-preserving large file support using Git-LFS protocol
#
# Copyright 2017 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""lfs - large file support (EXPERIMENTAL)

This extension allows large files to be tracked outside of the normal
repository storage and stored on a centralized server, similar to the
``largefiles`` extension.  The ``git-lfs`` protocol is used when
communicating with the server, so existing git infrastructure can be
harnessed.  Even though the files are stored outside of the repository,
they are still integrity checked in the same manner as normal files.

The files stored outside of the repository are downloaded on demand,
which reduces the time to clone, and possibly the local disk usage.
This changes fundamental workflows in a DVCS, so careful thought
should be given before deploying it.  :hg:`convert` can be used to
convert LFS repositories to normal repositories that no longer
require this extension, and do so without changing the commit hashes.
This allows the extension to be disabled if the centralized workflow
becomes burdensome.  However, the pre and post convert clones will
not be able to communicate with each other unless the extension is
enabled on both.

To start a new repository, or to add LFS files to an existing one, just
create an ``.hglfs`` file as described below in the root directory of
the repository.  Typically, this file should be put under version
control, so that the settings will propagate to other repositories with
push and pull.  During any commit, Mercurial will consult this file to
determine if an added or modified file should be stored externally.  The
type of storage depends on the characteristics of the file at each
commit.  A file that is near a size threshold may switch back and forth
between LFS and normal storage, as needed.

Alternately, both normal repositories and largefile controlled
repositories can be converted to LFS by using :hg:`convert` and the
``lfs.track`` config option described below.  The ``.hglfs`` file
should then be created and added, to control subsequent LFS selection.
The hashes are also unchanged in this case.  The LFS and non-LFS
repositories can be distinguished because the LFS repository will
abort any command if this extension is disabled.

Committed LFS files are held locally, until the repository is pushed.
Prior to pushing the normal repository data, the LFS files that are
tracked by the outgoing commits are automatically uploaded to the
configured central server.  No LFS files are transferred on
:hg:`pull` or :hg:`clone`.  Instead, the files are downloaded on
demand as they need to be read, if a cached copy cannot be found
locally.  Both committing and downloading an LFS file will link the
file to a usercache, to speed up future access.  See the `usercache`
config setting described below.

The extension reads its configuration from a versioned ``.hglfs``
configuration file found in the root of the working directory. The
``.hglfs`` file uses the same syntax as all other Mercurial
configuration files. It uses a single section, ``[track]``.

The ``[track]`` section specifies which files are stored as LFS (or
not). Each line is keyed by a file pattern, with a predicate value.
The first file pattern match is used, so put more specific patterns
first.  The available predicates are ``all()``, ``none()``, and
``size()``. See "hg help filesets.size" for the latter.

Example versioned ``.hglfs`` file::

  [track]
  # No Makefile or python file, anywhere, will be LFS
  **Makefile = none()
  **.py = none()

  **.zip = all()
  **.exe = size(">1MB")

  # Catchall for everything not matched above
  ** = size(">10MB")

Configs::

    [lfs]
    # Remote endpoint. Multiple protocols are supported:
    # - http(s)://user:pass@example.com/path
    #   git-lfs endpoint
    # - file:///tmp/path
    #   local filesystem, usually for testing
    # if unset, lfs will assume the remote repository also handles blob storage
    # for http(s) URLs.  Otherwise, lfs will prompt to set this when it must
    # use this value.
    # (default: unset)
    url = https://example.com/repo.git/info/lfs

    # Which files to track in LFS.  Path tests are "**.extname" for file
    # extensions, and "path:under/some/directory" for path prefix.  Both
    # are relative to the repository root.
    # File size can be tested with the "size()" fileset, and tests can be
    # joined with fileset operators.  (See "hg help filesets.operators".)
    #
    # Some examples:
    # - all()                       # everything
    # - none()                      # nothing
    # - size(">20MB")               # larger than 20MB
    # - !**.txt                     # anything not a *.txt file
    # - **.zip | **.tar.gz | **.7z  # some types of compressed files
    # - path:bin                    # files under "bin" in the project root
    # - (**.php & size(">2MB")) | (**.js & size(">5MB")) | **.tar.gz
    #     | (path:bin & !path:/bin/README) | size(">1GB")
    # (default: none())
    #
    # This is ignored if there is a tracked '.hglfs' file, and this setting
    # will eventually be deprecated and removed.
    track = size(">10M")

    # how many times to retry before giving up on transferring an object
    retry = 5

    # the local directory to store lfs files for sharing across local clones.
    # If not set, the cache is located in an OS specific cache location.
    usercache = /path/to/global/cache
"""

from __future__ import absolute_import

import sys

from mercurial.i18n import _
from mercurial.node import bin

from mercurial import (
    bundlecaches,
    config,
    context,
    error,
    extensions,
    exthelper,
    filelog,
    filesetlang,
    localrepo,
    minifileset,
    pycompat,
    revlog,
    scmutil,
    templateutil,
    util,
)

from mercurial.interfaces import repository

from . import (
    blobstore,
    wireprotolfsserver,
    wrapper,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

eh = exthelper.exthelper()
eh.merge(wrapper.eh)
eh.merge(wireprotolfsserver.eh)

cmdtable = eh.cmdtable
configtable = eh.configtable
extsetup = eh.finalextsetup
uisetup = eh.finaluisetup
filesetpredicate = eh.filesetpredicate
reposetup = eh.finalreposetup
templatekeyword = eh.templatekeyword

eh.configitem(
    b'experimental',
    b'lfs.serve',
    default=True,
)
eh.configitem(
    b'experimental',
    b'lfs.user-agent',
    default=None,
)
eh.configitem(
    b'experimental',
    b'lfs.disableusercache',
    default=False,
)
eh.configitem(
    b'experimental',
    b'lfs.worker-enable',
    default=True,
)

eh.configitem(
    b'lfs',
    b'url',
    default=None,
)
eh.configitem(
    b'lfs',
    b'usercache',
    default=None,
)
# Deprecated
eh.configitem(
    b'lfs',
    b'threshold',
    default=None,
)
eh.configitem(
    b'lfs',
    b'track',
    default=b'none()',
)
eh.configitem(
    b'lfs',
    b'retry',
    default=5,
)

lfsprocessor = (
    wrapper.readfromstore,
    wrapper.writetostore,
    wrapper.bypasscheckhash,
)


def featuresetup(ui, supported):
    # don't die on seeing a repo with the lfs requirement
    supported |= {b'lfs'}


@eh.uisetup
def _uisetup(ui):
    localrepo.featuresetupfuncs.add(featuresetup)


@eh.reposetup
def _reposetup(ui, repo):
    # Nothing to do with a remote repo
    if not repo.local():
        return

    repo.svfs.lfslocalblobstore = blobstore.local(repo)
    repo.svfs.lfsremoteblobstore = blobstore.remote(repo)

    class lfsrepo(repo.__class__):
        @localrepo.unfilteredmethod
        def commitctx(self, ctx, error=False, origctx=None):
            repo.svfs.options[b'lfstrack'] = _trackedmatcher(self)
            return super(lfsrepo, self).commitctx(ctx, error, origctx=origctx)

    repo.__class__ = lfsrepo

    if b'lfs' not in repo.requirements:

        def checkrequireslfs(ui, repo, **kwargs):
            if b'lfs' in repo.requirements:
                return 0

            last = kwargs.get('node_last')
            if last:
                s = repo.set(b'%n:%n', bin(kwargs['node']), bin(last))
            else:
                s = repo.set(b'%n', bin(kwargs['node']))
            match = repo._storenarrowmatch
            for ctx in s:
                # TODO: is there a way to just walk the files in the commit?
                if any(
                    ctx[f].islfs() for f in ctx.files() if f in ctx and match(f)
                ):
                    repo.requirements.add(b'lfs')
                    repo.features.add(repository.REPO_FEATURE_LFS)
                    scmutil.writereporequirements(repo)
                    repo.prepushoutgoinghooks.add(b'lfs', wrapper.prepush)
                    break

        ui.setconfig(b'hooks', b'commit.lfs', checkrequireslfs, b'lfs')
        ui.setconfig(
            b'hooks', b'pretxnchangegroup.lfs', checkrequireslfs, b'lfs'
        )
    else:
        repo.prepushoutgoinghooks.add(b'lfs', wrapper.prepush)


def _trackedmatcher(repo):
    """Return a function (path, size) -> bool indicating whether or not to
    track a given file with lfs."""
    if not repo.wvfs.exists(b'.hglfs'):
        # No '.hglfs' in wdir.  Fallback to config for now.
        trackspec = repo.ui.config(b'lfs', b'track')

        # deprecated config: lfs.threshold
        threshold = repo.ui.configbytes(b'lfs', b'threshold')
        if threshold:
            filesetlang.parse(trackspec)  # make sure syntax errors are confined
            trackspec = b"(%s) | size('>%d')" % (trackspec, threshold)

        return minifileset.compile(trackspec)

    data = repo.wvfs.tryread(b'.hglfs')
    if not data:
        return lambda p, s: False

    # Parse errors here will abort with a message that points to the .hglfs file
    # and line number.
    cfg = config.config()
    cfg.parse(b'.hglfs', data)

    try:
        rules = [
            (minifileset.compile(pattern), minifileset.compile(rule))
            for pattern, rule in cfg.items(b'track')
        ]
    except error.ParseError as e:
        # The original exception gives no indicator that the error is in the
        # .hglfs file, so add that.

        # TODO: See if the line number of the file can be made available.
        raise error.Abort(_(b'parse error in .hglfs: %s') % e)

    def _match(path, size):
        for pat, rule in rules:
            if pat(path, size):
                return rule(path, size)

        return False

    return _match


# Called by remotefilelog
def wrapfilelog(filelog):
    wrapfunction = extensions.wrapfunction

    wrapfunction(filelog, 'addrevision', wrapper.filelogaddrevision)
    wrapfunction(filelog, 'renamed', wrapper.filelogrenamed)
    wrapfunction(filelog, 'size', wrapper.filelogsize)


@eh.wrapfunction(localrepo, b'resolverevlogstorevfsoptions')
def _resolverevlogstorevfsoptions(orig, ui, requirements, features):
    opts = orig(ui, requirements, features)
    for name, module in extensions.extensions(ui):
        if module is sys.modules[__name__]:
            if revlog.REVIDX_EXTSTORED in opts[b'flagprocessors']:
                msg = (
                    _(b"cannot register multiple processors on flag '%#x'.")
                    % revlog.REVIDX_EXTSTORED
                )
                raise error.Abort(msg)

            opts[b'flagprocessors'][revlog.REVIDX_EXTSTORED] = lfsprocessor
            break

    return opts


@eh.extsetup
def _extsetup(ui):
    wrapfilelog(filelog.filelog)

    context.basefilectx.islfs = wrapper.filectxislfs

    scmutil.fileprefetchhooks.add(b'lfs', wrapper._prefetchfiles)

    # Make bundle choose changegroup3 instead of changegroup2. This affects
    # "hg bundle" command. Note: it does not cover all bundle formats like
    # "packed1". Using "packed1" with lfs will likely cause trouble.
    bundlecaches._bundlespeccontentopts[b"v2"][b"cg.version"] = b"03"


@eh.filesetpredicate(b'lfs()')
def lfsfileset(mctx, x):
    """File that uses LFS storage."""
    # i18n: "lfs" is a keyword
    filesetlang.getargs(x, 0, 0, _(b"lfs takes no arguments"))
    ctx = mctx.ctx

    def lfsfilep(f):
        return wrapper.pointerfromctx(ctx, f, removed=True) is not None

    return mctx.predicate(lfsfilep, predrepr=b'<lfs>')


@eh.templatekeyword(b'lfs_files', requires={b'ctx'})
def lfsfiles(context, mapping):
    """List of strings. All files modified, added, or removed by this
    changeset."""
    ctx = context.resource(mapping, b'ctx')

    pointers = wrapper.pointersfromctx(ctx, removed=True)  # {path: pointer}
    files = sorted(pointers.keys())

    def pointer(v):
        # In the file spec, version is first and the other keys are sorted.
        sortkeyfunc = lambda x: (x[0] != b'version', x)
        items = sorted(pycompat.iteritems(pointers[v]), key=sortkeyfunc)
        return util.sortdict(items)

    makemap = lambda v: {
        b'file': v,
        b'lfsoid': pointers[v].oid() if pointers[v] else None,
        b'lfspointer': templateutil.hybriddict(pointer(v)),
    }

    # TODO: make the separator ', '?
    f = templateutil._showcompatlist(context, mapping, b'lfs_file', files)
    return templateutil.hybrid(f, files, makemap, pycompat.identity)


@eh.command(
    b'debuglfsupload',
    [(b'r', b'rev', [], _(b'upload large files introduced by REV'))],
)
def debuglfsupload(ui, repo, **opts):
    """upload lfs blobs added by the working copy parent or given revisions"""
    revs = opts.get('rev', [])
    pointers = wrapper.extractpointers(repo, scmutil.revrange(repo, revs))
    wrapper.uploadblobs(repo, pointers)


@eh.wrapcommand(
    b'verify',
    opts=[(b'', b'no-lfs', None, _(b'skip missing lfs blob content'))],
)
def verify(orig, ui, repo, **opts):
    skipflags = repo.ui.configint(b'verify', b'skipflags')
    no_lfs = opts.pop('no_lfs')

    if skipflags:
        # --lfs overrides the config bit, if set.
        if no_lfs is False:
            skipflags &= ~repository.REVISION_FLAG_EXTSTORED
    else:
        skipflags = 0

    if no_lfs is True:
        skipflags |= repository.REVISION_FLAG_EXTSTORED

    with ui.configoverride({(b'verify', b'skipflags'): skipflags}):
        return orig(ui, repo, **opts)
