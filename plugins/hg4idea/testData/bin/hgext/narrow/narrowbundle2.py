# narrowbundle2.py - bundle2 extensions for narrow repository support
#
# Copyright 2017 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import struct

from mercurial.i18n import _
from mercurial import (
    bundle2,
    changegroup,
    error,
    exchange,
    localrepo,
    narrowspec,
    repair,
    requirements,
    scmutil,
    transaction,
    util,
    wireprototypes,
)

_NARROWACL_SECTION = b'narrowacl'
_CHANGESPECPART = b'narrow:changespec'
_RESSPECS = b'narrow:responsespec'
_SPECPART = b'narrow:spec'
_SPECPART_INCLUDE = b'include'
_SPECPART_EXCLUDE = b'exclude'
_KILLNODESIGNAL = b'KILL'
_DONESIGNAL = b'DONE'
_ELIDEDCSHEADER = b'>20s20s20sl'  # cset id, p1, p2, len(text)
_ELIDEDMFHEADER = b'>20s20s20s20sl'  # manifest id, p1, p2, link id, len(text)
_CSHEADERSIZE = struct.calcsize(_ELIDEDCSHEADER)
_MFHEADERSIZE = struct.calcsize(_ELIDEDMFHEADER)

# Serve a changegroup for a client with a narrow clone.
def getbundlechangegrouppart_narrow(
    bundler,
    repo,
    source,
    bundlecaps=None,
    b2caps=None,
    heads=None,
    common=None,
    **kwargs
):
    assert repo.ui.configbool(b'experimental', b'narrowservebrokenellipses')

    cgversions = b2caps.get(b'changegroup')
    cgversions = [
        v
        for v in cgversions
        if v in changegroup.supportedoutgoingversions(repo)
    ]
    if not cgversions:
        raise ValueError(_(b'no common changegroup version'))
    version = max(cgversions)

    include = sorted(filter(bool, kwargs.get('includepats', [])))
    exclude = sorted(filter(bool, kwargs.get('excludepats', [])))
    generateellipsesbundle2(
        bundler,
        repo,
        include,
        exclude,
        version,
        common,
        heads,
        kwargs.get('depth', None),
    )


def generateellipsesbundle2(
    bundler,
    repo,
    include,
    exclude,
    version,
    common,
    heads,
    depth,
):
    match = narrowspec.match(repo.root, include=include, exclude=exclude)
    if depth is not None:
        depth = int(depth)
        if depth < 1:
            raise error.Abort(_(b'depth must be positive, got %d') % depth)

    heads = set(heads or repo.heads())
    common = set(common or [repo.nullid])

    visitnodes, relevant_nodes, ellipsisroots = exchange._computeellipsis(
        repo, common, heads, set(), match, depth=depth
    )

    repo.ui.debug(b'Found %d relevant revs\n' % len(relevant_nodes))
    if visitnodes:
        packer = changegroup.getbundler(
            version,
            repo,
            matcher=match,
            ellipses=True,
            shallow=depth is not None,
            ellipsisroots=ellipsisroots,
            fullnodes=relevant_nodes,
        )
        cgdata = packer.generate(common, visitnodes, False, b'narrow_widen')

        part = bundler.newpart(b'changegroup', data=cgdata)
        part.addparam(b'version', version)
        if scmutil.istreemanifest(repo):
            part.addparam(b'treemanifest', b'1')


def generate_ellipses_bundle2_for_widening(
    bundler,
    repo,
    oldmatch,
    newmatch,
    version,
    common,
    known,
):
    common = set(common or [repo.nullid])
    # Steps:
    # 1. Send kill for "$known & ::common"
    #
    # 2. Send changegroup for ::common
    #
    # 3. Proceed.
    #
    # In the future, we can send kills for only the specific
    # nodes we know should go away or change shape, and then
    # send a data stream that tells the client something like this:
    #
    # a) apply this changegroup
    # b) apply nodes XXX, YYY, ZZZ that you already have
    # c) goto a
    #
    # until they've built up the full new state.
    knownrevs = {repo.changelog.rev(n) for n in known}
    # TODO: we could send only roots() of this set, and the
    # list of nodes in common, and the client could work out
    # what to strip, instead of us explicitly sending every
    # single node.
    deadrevs = knownrevs

    def genkills():
        for r in deadrevs:
            yield _KILLNODESIGNAL
            yield repo.changelog.node(r)
        yield _DONESIGNAL

    bundler.newpart(_CHANGESPECPART, data=genkills())
    newvisit, newfull, newellipsis = exchange._computeellipsis(
        repo, set(), common, knownrevs, newmatch
    )
    if newvisit:
        packer = changegroup.getbundler(
            version,
            repo,
            matcher=newmatch,
            ellipses=True,
            shallow=False,
            ellipsisroots=newellipsis,
            fullnodes=newfull,
        )
        cgdata = packer.generate(common, newvisit, False, b'narrow_widen')

        part = bundler.newpart(b'changegroup', data=cgdata)
        part.addparam(b'version', version)
        if scmutil.istreemanifest(repo):
            part.addparam(b'treemanifest', b'1')


@bundle2.parthandler(_SPECPART, (_SPECPART_INCLUDE, _SPECPART_EXCLUDE))
def _handlechangespec_2(op, inpart):
    # XXX: This bundle2 handling is buggy and should be removed after hg5.2 is
    # released. New servers will send a mandatory bundle2 part named
    # 'Narrowspec' and will send specs as data instead of params.
    # Refer to issue5952 and 6019
    includepats = set(inpart.params.get(_SPECPART_INCLUDE, b'').splitlines())
    excludepats = set(inpart.params.get(_SPECPART_EXCLUDE, b'').splitlines())
    narrowspec.validatepatterns(includepats)
    narrowspec.validatepatterns(excludepats)

    if not requirements.NARROW_REQUIREMENT in op.repo.requirements:
        op.repo.requirements.add(requirements.NARROW_REQUIREMENT)
        scmutil.writereporequirements(op.repo)
    op.repo.setnarrowpats(includepats, excludepats)
    narrowspec.copytoworkingcopy(op.repo)


@bundle2.parthandler(_RESSPECS)
def _handlenarrowspecs(op, inpart):
    data = inpart.read()
    inc, exc = data.split(b'\0')
    includepats = set(inc.splitlines())
    excludepats = set(exc.splitlines())
    narrowspec.validatepatterns(includepats)
    narrowspec.validatepatterns(excludepats)

    if requirements.NARROW_REQUIREMENT not in op.repo.requirements:
        op.repo.requirements.add(requirements.NARROW_REQUIREMENT)
        scmutil.writereporequirements(op.repo)
    op.repo.setnarrowpats(includepats, excludepats)
    narrowspec.copytoworkingcopy(op.repo)


@bundle2.parthandler(_CHANGESPECPART)
def _handlechangespec(op, inpart):
    repo = op.repo
    cl = repo.changelog

    # changesets which need to be stripped entirely. either they're no longer
    # needed in the new narrow spec, or the server is sending a replacement
    # in the changegroup part.
    clkills = set()

    # A changespec part contains all the updates to ellipsis nodes
    # that will happen as a result of widening or narrowing a
    # repo. All the changes that this block encounters are ellipsis
    # nodes or flags to kill an existing ellipsis.
    chunksignal = changegroup.readexactly(inpart, 4)
    while chunksignal != _DONESIGNAL:
        if chunksignal == _KILLNODESIGNAL:
            # a node used to be an ellipsis but isn't anymore
            ck = changegroup.readexactly(inpart, 20)
            if cl.hasnode(ck):
                clkills.add(ck)
        else:
            raise error.Abort(
                _(b'unexpected changespec node chunk type: %s') % chunksignal
            )
        chunksignal = changegroup.readexactly(inpart, 4)

    if clkills:
        # preserve bookmarks that repair.strip() would otherwise strip
        op._bookmarksbackup = repo._bookmarks

        class dummybmstore(dict):
            def applychanges(self, repo, tr, changes):
                pass

        localrepo.localrepository._bookmarks.set(repo, dummybmstore())
        chgrpfile = repair.strip(
            op.ui, repo, list(clkills), backup=True, topic=b'widen'
        )
        if chgrpfile:
            op._widen_uninterr = repo.ui.uninterruptible()
            op._widen_uninterr.__enter__()
            # presence of _widen_bundle attribute activates widen handler later
            op._widen_bundle = chgrpfile
    # Set the new narrowspec if we're widening. The setnewnarrowpats() method
    # will currently always be there when using the core+narrowhg server, but
    # other servers may include a changespec part even when not widening (e.g.
    # because we're deepening a shallow repo).
    if hasattr(repo, 'setnewnarrowpats'):
        op.gettransaction()
        repo.setnewnarrowpats()


def handlechangegroup_widen(op, inpart):
    """Changegroup exchange handler which restores temporarily-stripped nodes"""
    # We saved a bundle with stripped node data we must now restore.
    # This approach is based on mercurial/repair.py@6ee26a53c111.
    repo = op.repo
    ui = op.ui

    chgrpfile = op._widen_bundle
    del op._widen_bundle
    vfs = repo.vfs

    ui.note(_(b"adding branch\n"))
    f = vfs.open(chgrpfile, b"rb")
    try:
        gen = exchange.readbundle(ui, f, chgrpfile, vfs)
        # silence internal shuffling chatter
        maybe_silent = (
            ui.silent() if not ui.verbose else util.nullcontextmanager()
        )
        with maybe_silent:
            if isinstance(gen, bundle2.unbundle20):
                with repo.transaction(b'strip') as tr:
                    bundle2.processbundle(repo, gen, lambda: tr)
            else:
                gen.apply(
                    repo, b'strip', b'bundle:' + vfs.join(chgrpfile), True
                )
    finally:
        f.close()

    transaction.cleanup_undo_files(repo.ui.warn, repo.vfs_map)

    # Remove partial backup only if there were no exceptions
    op._widen_uninterr.__exit__(None, None, None)
    vfs.unlink(chgrpfile)


def setup():
    """Enable narrow repo support in bundle2-related extension points."""
    getbundleargs = wireprototypes.GETBUNDLE_ARGUMENTS

    getbundleargs[b'narrow'] = b'boolean'
    getbundleargs[b'depth'] = b'plain'
    getbundleargs[b'oldincludepats'] = b'csv'
    getbundleargs[b'oldexcludepats'] = b'csv'
    getbundleargs[b'known'] = b'csv'

    # Extend changegroup serving to handle requests from narrow clients.
    origcgfn = exchange.getbundle2partsmapping[b'changegroup']

    def wrappedcgfn(*args, **kwargs):
        repo = args[1]
        if repo.ui.has_section(_NARROWACL_SECTION):
            kwargs = exchange.applynarrowacl(repo, kwargs)

        if kwargs.get('narrow', False) and repo.ui.configbool(
            b'experimental', b'narrowservebrokenellipses'
        ):
            getbundlechangegrouppart_narrow(*args, **kwargs)
        else:
            origcgfn(*args, **kwargs)

    exchange.getbundle2partsmapping[b'changegroup'] = wrappedcgfn

    # Extend changegroup receiver so client can fixup after widen requests.
    origcghandler = bundle2.parthandlermapping[b'changegroup']

    def wrappedcghandler(op, inpart):
        origcghandler(op, inpart)
        if hasattr(op, '_widen_bundle'):
            handlechangegroup_widen(op, inpart)
        if hasattr(op, '_bookmarksbackup'):
            localrepo.localrepository._bookmarks.set(
                op.repo, op._bookmarksbackup
            )
            del op._bookmarksbackup

    wrappedcghandler.params = origcghandler.params
    bundle2.parthandlermapping[b'changegroup'] = wrappedcghandler
