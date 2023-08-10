# exchange.py - utility to exchange data between repos.
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import collections
import weakref

from .i18n import _
from .node import (
    hex,
    nullrev,
)
from . import (
    bookmarks as bookmod,
    bundle2,
    bundlecaches,
    changegroup,
    discovery,
    error,
    exchangev2,
    lock as lockmod,
    logexchange,
    narrowspec,
    obsolete,
    obsutil,
    phases,
    pushkey,
    pycompat,
    requirements,
    scmutil,
    streamclone,
    url as urlmod,
    util,
    wireprototypes,
)
from .utils import (
    hashutil,
    stringutil,
    urlutil,
)
from .interfaces import repository

urlerr = util.urlerr
urlreq = util.urlreq

_NARROWACL_SECTION = b'narrowacl'


def readbundle(ui, fh, fname, vfs=None):
    header = changegroup.readexactly(fh, 4)

    alg = None
    if not fname:
        fname = b"stream"
        if not header.startswith(b'HG') and header.startswith(b'\0'):
            fh = changegroup.headerlessfixup(fh, header)
            header = b"HG10"
            alg = b'UN'
    elif vfs:
        fname = vfs.join(fname)

    magic, version = header[0:2], header[2:4]

    if magic != b'HG':
        raise error.Abort(_(b'%s: not a Mercurial bundle') % fname)
    if version == b'10':
        if alg is None:
            alg = changegroup.readexactly(fh, 2)
        return changegroup.cg1unpacker(fh, alg)
    elif version.startswith(b'2'):
        return bundle2.getunbundler(ui, fh, magicstring=magic + version)
    elif version == b'S1':
        return streamclone.streamcloneapplier(fh)
    else:
        raise error.Abort(
            _(b'%s: unknown bundle version %s') % (fname, version)
        )


def getbundlespec(ui, fh):
    """Infer the bundlespec from a bundle file handle.

    The input file handle is seeked and the original seek position is not
    restored.
    """

    def speccompression(alg):
        try:
            return util.compengines.forbundletype(alg).bundletype()[0]
        except KeyError:
            return None

    b = readbundle(ui, fh, None)
    if isinstance(b, changegroup.cg1unpacker):
        alg = b._type
        if alg == b'_truncatedBZ':
            alg = b'BZ'
        comp = speccompression(alg)
        if not comp:
            raise error.Abort(_(b'unknown compression algorithm: %s') % alg)
        return b'%s-v1' % comp
    elif isinstance(b, bundle2.unbundle20):
        if b'Compression' in b.params:
            comp = speccompression(b.params[b'Compression'])
            if not comp:
                raise error.Abort(
                    _(b'unknown compression algorithm: %s') % comp
                )
        else:
            comp = b'none'

        version = None
        for part in b.iterparts():
            if part.type == b'changegroup':
                version = part.params[b'version']
                if version in (b'01', b'02'):
                    version = b'v2'
                else:
                    raise error.Abort(
                        _(
                            b'changegroup version %s does not have '
                            b'a known bundlespec'
                        )
                        % version,
                        hint=_(b'try upgrading your Mercurial client'),
                    )
            elif part.type == b'stream2' and version is None:
                # A stream2 part requires to be part of a v2 bundle
                requirements = urlreq.unquote(part.params[b'requirements'])
                splitted = requirements.split()
                params = bundle2._formatrequirementsparams(splitted)
                return b'none-v2;stream=v2;%s' % params

        if not version:
            raise error.Abort(
                _(b'could not identify changegroup version in bundle')
            )

        return b'%s-%s' % (comp, version)
    elif isinstance(b, streamclone.streamcloneapplier):
        requirements = streamclone.readbundle1header(fh)[2]
        formatted = bundle2._formatrequirementsparams(requirements)
        return b'none-packed1;%s' % formatted
    else:
        raise error.Abort(_(b'unknown bundle type: %s') % b)


def _computeoutgoing(repo, heads, common):
    """Computes which revs are outgoing given a set of common
    and a set of heads.

    This is a separate function so extensions can have access to
    the logic.

    Returns a discovery.outgoing object.
    """
    cl = repo.changelog
    if common:
        hasnode = cl.hasnode
        common = [n for n in common if hasnode(n)]
    else:
        common = [repo.nullid]
    if not heads:
        heads = cl.heads()
    return discovery.outgoing(repo, common, heads)


def _checkpublish(pushop):
    repo = pushop.repo
    ui = repo.ui
    behavior = ui.config(b'experimental', b'auto-publish')
    if pushop.publish or behavior not in (b'warn', b'confirm', b'abort'):
        return
    remotephases = listkeys(pushop.remote, b'phases')
    if not remotephases.get(b'publishing', False):
        return

    if pushop.revs is None:
        published = repo.filtered(b'served').revs(b'not public()')
    else:
        published = repo.revs(b'::%ln - public()', pushop.revs)
        # we want to use pushop.revs in the revset even if they themselves are
        # secret, but we don't want to have anything that the server won't see
        # in the result of this expression
        published &= repo.filtered(b'served')
    if published:
        if behavior == b'warn':
            ui.warn(
                _(b'%i changesets about to be published\n') % len(published)
            )
        elif behavior == b'confirm':
            if ui.promptchoice(
                _(b'push and publish %i changesets (yn)?$$ &Yes $$ &No')
                % len(published)
            ):
                raise error.CanceledError(_(b'user quit'))
        elif behavior == b'abort':
            msg = _(b'push would publish %i changesets') % len(published)
            hint = _(
                b"use --publish or adjust 'experimental.auto-publish'"
                b" config"
            )
            raise error.Abort(msg, hint=hint)


def _forcebundle1(op):
    """return true if a pull/push must use bundle1

    This function is used to allow testing of the older bundle version"""
    ui = op.repo.ui
    # The goal is this config is to allow developer to choose the bundle
    # version used during exchanged. This is especially handy during test.
    # Value is a list of bundle version to be picked from, highest version
    # should be used.
    #
    # developer config: devel.legacy.exchange
    exchange = ui.configlist(b'devel', b'legacy.exchange')
    forcebundle1 = b'bundle2' not in exchange and b'bundle1' in exchange
    return forcebundle1 or not op.remote.capable(b'bundle2')


class pushoperation(object):
    """A object that represent a single push operation

    Its purpose is to carry push related state and very common operations.

    A new pushoperation should be created at the beginning of each push and
    discarded afterward.
    """

    def __init__(
        self,
        repo,
        remote,
        force=False,
        revs=None,
        newbranch=False,
        bookmarks=(),
        publish=False,
        pushvars=None,
    ):
        # repo we push from
        self.repo = repo
        self.ui = repo.ui
        # repo we push to
        self.remote = remote
        # force option provided
        self.force = force
        # revs to be pushed (None is "all")
        self.revs = revs
        # bookmark explicitly pushed
        self.bookmarks = bookmarks
        # allow push of new branch
        self.newbranch = newbranch
        # step already performed
        # (used to check what steps have been already performed through bundle2)
        self.stepsdone = set()
        # Integer version of the changegroup push result
        # - None means nothing to push
        # - 0 means HTTP error
        # - 1 means we pushed and remote head count is unchanged *or*
        #   we have outgoing changesets but refused to push
        # - other values as described by addchangegroup()
        self.cgresult = None
        # Boolean value for the bookmark push
        self.bkresult = None
        # discover.outgoing object (contains common and outgoing data)
        self.outgoing = None
        # all remote topological heads before the push
        self.remoteheads = None
        # Details of the remote branch pre and post push
        #
        # mapping: {'branch': ([remoteheads],
        #                      [newheads],
        #                      [unsyncedheads],
        #                      [discardedheads])}
        # - branch: the branch name
        # - remoteheads: the list of remote heads known locally
        #                None if the branch is new
        # - newheads: the new remote heads (known locally) with outgoing pushed
        # - unsyncedheads: the list of remote heads unknown locally.
        # - discardedheads: the list of remote heads made obsolete by the push
        self.pushbranchmap = None
        # testable as a boolean indicating if any nodes are missing locally.
        self.incoming = None
        # summary of the remote phase situation
        self.remotephases = None
        # phases changes that must be pushed along side the changesets
        self.outdatedphases = None
        # phases changes that must be pushed if changeset push fails
        self.fallbackoutdatedphases = None
        # outgoing obsmarkers
        self.outobsmarkers = set()
        # outgoing bookmarks, list of (bm, oldnode | '', newnode | '')
        self.outbookmarks = []
        # transaction manager
        self.trmanager = None
        # map { pushkey partid -> callback handling failure}
        # used to handle exception from mandatory pushkey part failure
        self.pkfailcb = {}
        # an iterable of pushvars or None
        self.pushvars = pushvars
        # publish pushed changesets
        self.publish = publish

    @util.propertycache
    def futureheads(self):
        """future remote heads if the changeset push succeeds"""
        return self.outgoing.ancestorsof

    @util.propertycache
    def fallbackheads(self):
        """future remote heads if the changeset push fails"""
        if self.revs is None:
            # not target to push, all common are relevant
            return self.outgoing.commonheads
        unfi = self.repo.unfiltered()
        # I want cheads = heads(::ancestorsof and ::commonheads)
        # (ancestorsof is revs with secret changeset filtered out)
        #
        # This can be expressed as:
        #     cheads = ( (ancestorsof and ::commonheads)
        #              + (commonheads and ::ancestorsof))"
        #              )
        #
        # while trying to push we already computed the following:
        #     common = (::commonheads)
        #     missing = ((commonheads::ancestorsof) - commonheads)
        #
        # We can pick:
        # * ancestorsof part of common (::commonheads)
        common = self.outgoing.common
        rev = self.repo.changelog.index.rev
        cheads = [node for node in self.revs if rev(node) in common]
        # and
        # * commonheads parents on missing
        revset = unfi.set(
            b'%ln and parents(roots(%ln))',
            self.outgoing.commonheads,
            self.outgoing.missing,
        )
        cheads.extend(c.node() for c in revset)
        return cheads

    @property
    def commonheads(self):
        """set of all common heads after changeset bundle push"""
        if self.cgresult:
            return self.futureheads
        else:
            return self.fallbackheads


# mapping of message used when pushing bookmark
bookmsgmap = {
    b'update': (
        _(b"updating bookmark %s\n"),
        _(b'updating bookmark %s failed\n'),
    ),
    b'export': (
        _(b"exporting bookmark %s\n"),
        _(b'exporting bookmark %s failed\n'),
    ),
    b'delete': (
        _(b"deleting remote bookmark %s\n"),
        _(b'deleting remote bookmark %s failed\n'),
    ),
}


def push(
    repo,
    remote,
    force=False,
    revs=None,
    newbranch=False,
    bookmarks=(),
    publish=False,
    opargs=None,
):
    """Push outgoing changesets (limited by revs) from a local
    repository to remote. Return an integer:
      - None means nothing to push
      - 0 means HTTP error
      - 1 means we pushed and remote head count is unchanged *or*
        we have outgoing changesets but refused to push
      - other values as described by addchangegroup()
    """
    if opargs is None:
        opargs = {}
    pushop = pushoperation(
        repo,
        remote,
        force,
        revs,
        newbranch,
        bookmarks,
        publish,
        **pycompat.strkwargs(opargs)
    )
    if pushop.remote.local():
        missing = (
            set(pushop.repo.requirements) - pushop.remote.local().supported
        )
        if missing:
            msg = _(
                b"required features are not"
                b" supported in the destination:"
                b" %s"
            ) % (b', '.join(sorted(missing)))
            raise error.Abort(msg)

    if not pushop.remote.canpush():
        raise error.Abort(_(b"destination does not support push"))

    if not pushop.remote.capable(b'unbundle'):
        raise error.Abort(
            _(
                b'cannot push: destination does not support the '
                b'unbundle wire protocol command'
            )
        )
    for category in sorted(bundle2.read_remote_wanted_sidedata(pushop.remote)):
        # Check that a computer is registered for that category for at least
        # one revlog kind.
        for kind, computers in repo._sidedata_computers.items():
            if computers.get(category):
                break
        else:
            raise error.Abort(
                _(
                    b'cannot push: required sidedata category not supported'
                    b" by this client: '%s'"
                )
                % pycompat.bytestr(category)
            )
    # get lock as we might write phase data
    wlock = lock = None
    try:
        # bundle2 push may receive a reply bundle touching bookmarks
        # requiring the wlock. Take it now to ensure proper ordering.
        maypushback = pushop.ui.configbool(b'experimental', b'bundle2.pushback')
        if (
            (not _forcebundle1(pushop))
            and maypushback
            and not bookmod.bookmarksinstore(repo)
        ):
            wlock = pushop.repo.wlock()
        lock = pushop.repo.lock()
        pushop.trmanager = transactionmanager(
            pushop.repo, b'push-response', pushop.remote.url()
        )
    except error.LockUnavailable as err:
        # source repo cannot be locked.
        # We do not abort the push, but just disable the local phase
        # synchronisation.
        msg = b'cannot lock source repository: %s\n' % stringutil.forcebytestr(
            err
        )
        pushop.ui.debug(msg)

    with wlock or util.nullcontextmanager():
        with lock or util.nullcontextmanager():
            with pushop.trmanager or util.nullcontextmanager():
                pushop.repo.checkpush(pushop)
                _checkpublish(pushop)
                _pushdiscovery(pushop)
                if not pushop.force:
                    _checksubrepostate(pushop)
                if not _forcebundle1(pushop):
                    _pushbundle2(pushop)
                _pushchangeset(pushop)
                _pushsyncphase(pushop)
                _pushobsolete(pushop)
                _pushbookmark(pushop)

    if repo.ui.configbool(b'experimental', b'remotenames'):
        logexchange.pullremotenames(repo, remote)

    return pushop


# list of steps to perform discovery before push
pushdiscoveryorder = []

# Mapping between step name and function
#
# This exists to help extensions wrap steps if necessary
pushdiscoverymapping = {}


def pushdiscovery(stepname):
    """decorator for function performing discovery before push

    The function is added to the step -> function mapping and appended to the
    list of steps.  Beware that decorated function will be added in order (this
    may matter).

    You can only use this decorator for a new step, if you want to wrap a step
    from an extension, change the pushdiscovery dictionary directly."""

    def dec(func):
        assert stepname not in pushdiscoverymapping
        pushdiscoverymapping[stepname] = func
        pushdiscoveryorder.append(stepname)
        return func

    return dec


def _pushdiscovery(pushop):
    """Run all discovery steps"""
    for stepname in pushdiscoveryorder:
        step = pushdiscoverymapping[stepname]
        step(pushop)


def _checksubrepostate(pushop):
    """Ensure all outgoing referenced subrepo revisions are present locally"""
    for n in pushop.outgoing.missing:
        ctx = pushop.repo[n]

        if b'.hgsub' in ctx.manifest() and b'.hgsubstate' in ctx.files():
            for subpath in sorted(ctx.substate):
                sub = ctx.sub(subpath)
                sub.verify(onpush=True)


@pushdiscovery(b'changeset')
def _pushdiscoverychangeset(pushop):
    """discover the changeset that need to be pushed"""
    fci = discovery.findcommonincoming
    if pushop.revs:
        commoninc = fci(
            pushop.repo,
            pushop.remote,
            force=pushop.force,
            ancestorsof=pushop.revs,
        )
    else:
        commoninc = fci(pushop.repo, pushop.remote, force=pushop.force)
    common, inc, remoteheads = commoninc
    fco = discovery.findcommonoutgoing
    outgoing = fco(
        pushop.repo,
        pushop.remote,
        onlyheads=pushop.revs,
        commoninc=commoninc,
        force=pushop.force,
    )
    pushop.outgoing = outgoing
    pushop.remoteheads = remoteheads
    pushop.incoming = inc


@pushdiscovery(b'phase')
def _pushdiscoveryphase(pushop):
    """discover the phase that needs to be pushed

    (computed for both success and failure case for changesets push)"""
    outgoing = pushop.outgoing
    unfi = pushop.repo.unfiltered()
    remotephases = listkeys(pushop.remote, b'phases')

    if (
        pushop.ui.configbool(b'ui', b'_usedassubrepo')
        and remotephases  # server supports phases
        and not pushop.outgoing.missing  # no changesets to be pushed
        and remotephases.get(b'publishing', False)
    ):
        # When:
        # - this is a subrepo push
        # - and remote support phase
        # - and no changeset are to be pushed
        # - and remote is publishing
        # We may be in issue 3781 case!
        # We drop the possible phase synchronisation done by
        # courtesy to publish changesets possibly locally draft
        # on the remote.
        pushop.outdatedphases = []
        pushop.fallbackoutdatedphases = []
        return

    pushop.remotephases = phases.remotephasessummary(
        pushop.repo, pushop.fallbackheads, remotephases
    )
    droots = pushop.remotephases.draftroots

    extracond = b''
    if not pushop.remotephases.publishing:
        extracond = b' and public()'
    revset = b'heads((%%ln::%%ln) %s)' % extracond
    # Get the list of all revs draft on remote by public here.
    # XXX Beware that revset break if droots is not strictly
    # XXX root we may want to ensure it is but it is costly
    fallback = list(unfi.set(revset, droots, pushop.fallbackheads))
    if not pushop.remotephases.publishing and pushop.publish:
        future = list(
            unfi.set(
                b'%ln and (not public() or %ln::)', pushop.futureheads, droots
            )
        )
    elif not outgoing.missing:
        future = fallback
    else:
        # adds changeset we are going to push as draft
        #
        # should not be necessary for publishing server, but because of an
        # issue fixed in xxxxx we have to do it anyway.
        fdroots = list(
            unfi.set(b'roots(%ln  + %ln::)', outgoing.missing, droots)
        )
        fdroots = [f.node() for f in fdroots]
        future = list(unfi.set(revset, fdroots, pushop.futureheads))
    pushop.outdatedphases = future
    pushop.fallbackoutdatedphases = fallback


@pushdiscovery(b'obsmarker')
def _pushdiscoveryobsmarkers(pushop):
    if not obsolete.isenabled(pushop.repo, obsolete.exchangeopt):
        return

    if not pushop.repo.obsstore:
        return

    if b'obsolete' not in listkeys(pushop.remote, b'namespaces'):
        return

    repo = pushop.repo
    # very naive computation, that can be quite expensive on big repo.
    # However: evolution is currently slow on them anyway.
    nodes = (c.node() for c in repo.set(b'::%ln', pushop.futureheads))
    pushop.outobsmarkers = pushop.repo.obsstore.relevantmarkers(nodes)


@pushdiscovery(b'bookmarks')
def _pushdiscoverybookmarks(pushop):
    ui = pushop.ui
    repo = pushop.repo.unfiltered()
    remote = pushop.remote
    ui.debug(b"checking for updated bookmarks\n")
    ancestors = ()
    if pushop.revs:
        revnums = pycompat.maplist(repo.changelog.rev, pushop.revs)
        ancestors = repo.changelog.ancestors(revnums, inclusive=True)

    remotebookmark = bookmod.unhexlifybookmarks(listkeys(remote, b'bookmarks'))

    explicit = {
        repo._bookmarks.expandname(bookmark) for bookmark in pushop.bookmarks
    }

    comp = bookmod.comparebookmarks(repo, repo._bookmarks, remotebookmark)
    return _processcompared(pushop, ancestors, explicit, remotebookmark, comp)


def _processcompared(pushop, pushed, explicit, remotebms, comp):
    """take decision on bookmarks to push to the remote repo

    Exists to help extensions alter this behavior.
    """
    addsrc, adddst, advsrc, advdst, diverge, differ, invalid, same = comp

    repo = pushop.repo

    for b, scid, dcid in advsrc:
        if b in explicit:
            explicit.remove(b)
        if not pushed or repo[scid].rev() in pushed:
            pushop.outbookmarks.append((b, dcid, scid))
    # search added bookmark
    for b, scid, dcid in addsrc:
        if b in explicit:
            explicit.remove(b)
            if bookmod.isdivergent(b):
                pushop.ui.warn(_(b'cannot push divergent bookmark %s!\n') % b)
                pushop.bkresult = 2
            else:
                pushop.outbookmarks.append((b, b'', scid))
    # search for overwritten bookmark
    for b, scid, dcid in list(advdst) + list(diverge) + list(differ):
        if b in explicit:
            explicit.remove(b)
            pushop.outbookmarks.append((b, dcid, scid))
    # search for bookmark to delete
    for b, scid, dcid in adddst:
        if b in explicit:
            explicit.remove(b)
            # treat as "deleted locally"
            pushop.outbookmarks.append((b, dcid, b''))
    # identical bookmarks shouldn't get reported
    for b, scid, dcid in same:
        if b in explicit:
            explicit.remove(b)

    if explicit:
        explicit = sorted(explicit)
        # we should probably list all of them
        pushop.ui.warn(
            _(
                b'bookmark %s does not exist on the local '
                b'or remote repository!\n'
            )
            % explicit[0]
        )
        pushop.bkresult = 2

    pushop.outbookmarks.sort()


def _pushcheckoutgoing(pushop):
    outgoing = pushop.outgoing
    unfi = pushop.repo.unfiltered()
    if not outgoing.missing:
        # nothing to push
        scmutil.nochangesfound(unfi.ui, unfi, outgoing.excluded)
        return False
    # something to push
    if not pushop.force:
        # if repo.obsstore == False --> no obsolete
        # then, save the iteration
        if unfi.obsstore:
            # this message are here for 80 char limit reason
            mso = _(b"push includes obsolete changeset: %s!")
            mspd = _(b"push includes phase-divergent changeset: %s!")
            mscd = _(b"push includes content-divergent changeset: %s!")
            mst = {
                b"orphan": _(b"push includes orphan changeset: %s!"),
                b"phase-divergent": mspd,
                b"content-divergent": mscd,
            }
            # If we are to push if there is at least one
            # obsolete or unstable changeset in missing, at
            # least one of the missinghead will be obsolete or
            # unstable. So checking heads only is ok
            for node in outgoing.ancestorsof:
                ctx = unfi[node]
                if ctx.obsolete():
                    raise error.Abort(mso % ctx)
                elif ctx.isunstable():
                    # TODO print more than one instability in the abort
                    # message
                    raise error.Abort(mst[ctx.instabilities()[0]] % ctx)

        discovery.checkheads(pushop)
    return True


# List of names of steps to perform for an outgoing bundle2, order matters.
b2partsgenorder = []

# Mapping between step name and function
#
# This exists to help extensions wrap steps if necessary
b2partsgenmapping = {}


def b2partsgenerator(stepname, idx=None):
    """decorator for function generating bundle2 part

    The function is added to the step -> function mapping and appended to the
    list of steps.  Beware that decorated functions will be added in order
    (this may matter).

    You can only use this decorator for new steps, if you want to wrap a step
    from an extension, attack the b2partsgenmapping dictionary directly."""

    def dec(func):
        assert stepname not in b2partsgenmapping
        b2partsgenmapping[stepname] = func
        if idx is None:
            b2partsgenorder.append(stepname)
        else:
            b2partsgenorder.insert(idx, stepname)
        return func

    return dec


def _pushb2ctxcheckheads(pushop, bundler):
    """Generate race condition checking parts

    Exists as an independent function to aid extensions
    """
    # * 'force' do not check for push race,
    # * if we don't push anything, there are nothing to check.
    if not pushop.force and pushop.outgoing.ancestorsof:
        allowunrelated = b'related' in bundler.capabilities.get(
            b'checkheads', ()
        )
        emptyremote = pushop.pushbranchmap is None
        if not allowunrelated or emptyremote:
            bundler.newpart(b'check:heads', data=iter(pushop.remoteheads))
        else:
            affected = set()
            for branch, heads in pycompat.iteritems(pushop.pushbranchmap):
                remoteheads, newheads, unsyncedheads, discardedheads = heads
                if remoteheads is not None:
                    remote = set(remoteheads)
                    affected |= set(discardedheads) & remote
                    affected |= remote - set(newheads)
            if affected:
                data = iter(sorted(affected))
                bundler.newpart(b'check:updated-heads', data=data)


def _pushing(pushop):
    """return True if we are pushing anything"""
    return bool(
        pushop.outgoing.missing
        or pushop.outdatedphases
        or pushop.outobsmarkers
        or pushop.outbookmarks
    )


@b2partsgenerator(b'check-bookmarks')
def _pushb2checkbookmarks(pushop, bundler):
    """insert bookmark move checking"""
    if not _pushing(pushop) or pushop.force:
        return
    b2caps = bundle2.bundle2caps(pushop.remote)
    hasbookmarkcheck = b'bookmarks' in b2caps
    if not (pushop.outbookmarks and hasbookmarkcheck):
        return
    data = []
    for book, old, new in pushop.outbookmarks:
        data.append((book, old))
    checkdata = bookmod.binaryencode(pushop.repo, data)
    bundler.newpart(b'check:bookmarks', data=checkdata)


@b2partsgenerator(b'check-phases')
def _pushb2checkphases(pushop, bundler):
    """insert phase move checking"""
    if not _pushing(pushop) or pushop.force:
        return
    b2caps = bundle2.bundle2caps(pushop.remote)
    hasphaseheads = b'heads' in b2caps.get(b'phases', ())
    if pushop.remotephases is not None and hasphaseheads:
        # check that the remote phase has not changed
        checks = {p: [] for p in phases.allphases}
        checks[phases.public].extend(pushop.remotephases.publicheads)
        checks[phases.draft].extend(pushop.remotephases.draftroots)
        if any(pycompat.itervalues(checks)):
            for phase in checks:
                checks[phase].sort()
            checkdata = phases.binaryencode(checks)
            bundler.newpart(b'check:phases', data=checkdata)


@b2partsgenerator(b'changeset')
def _pushb2ctx(pushop, bundler):
    """handle changegroup push through bundle2

    addchangegroup result is stored in the ``pushop.cgresult`` attribute.
    """
    if b'changesets' in pushop.stepsdone:
        return
    pushop.stepsdone.add(b'changesets')
    # Send known heads to the server for race detection.
    if not _pushcheckoutgoing(pushop):
        return
    pushop.repo.prepushoutgoinghooks(pushop)

    _pushb2ctxcheckheads(pushop, bundler)

    b2caps = bundle2.bundle2caps(pushop.remote)
    version = b'01'
    cgversions = b2caps.get(b'changegroup')
    if cgversions:  # 3.1 and 3.2 ship with an empty value
        cgversions = [
            v
            for v in cgversions
            if v in changegroup.supportedoutgoingversions(pushop.repo)
        ]
        if not cgversions:
            raise error.Abort(_(b'no common changegroup version'))
        version = max(cgversions)

    remote_sidedata = bundle2.read_remote_wanted_sidedata(pushop.remote)
    cgstream = changegroup.makestream(
        pushop.repo,
        pushop.outgoing,
        version,
        b'push',
        bundlecaps=b2caps,
        remote_sidedata=remote_sidedata,
    )
    cgpart = bundler.newpart(b'changegroup', data=cgstream)
    if cgversions:
        cgpart.addparam(b'version', version)
    if scmutil.istreemanifest(pushop.repo):
        cgpart.addparam(b'treemanifest', b'1')
    if repository.REPO_FEATURE_SIDE_DATA in pushop.repo.features:
        cgpart.addparam(b'exp-sidedata', b'1')

    def handlereply(op):
        """extract addchangegroup returns from server reply"""
        cgreplies = op.records.getreplies(cgpart.id)
        assert len(cgreplies[b'changegroup']) == 1
        pushop.cgresult = cgreplies[b'changegroup'][0][b'return']

    return handlereply


@b2partsgenerator(b'phase')
def _pushb2phases(pushop, bundler):
    """handle phase push through bundle2"""
    if b'phases' in pushop.stepsdone:
        return
    b2caps = bundle2.bundle2caps(pushop.remote)
    ui = pushop.repo.ui

    legacyphase = b'phases' in ui.configlist(b'devel', b'legacy.exchange')
    haspushkey = b'pushkey' in b2caps
    hasphaseheads = b'heads' in b2caps.get(b'phases', ())

    if hasphaseheads and not legacyphase:
        return _pushb2phaseheads(pushop, bundler)
    elif haspushkey:
        return _pushb2phasespushkey(pushop, bundler)


def _pushb2phaseheads(pushop, bundler):
    """push phase information through a bundle2 - binary part"""
    pushop.stepsdone.add(b'phases')
    if pushop.outdatedphases:
        updates = {p: [] for p in phases.allphases}
        updates[0].extend(h.node() for h in pushop.outdatedphases)
        phasedata = phases.binaryencode(updates)
        bundler.newpart(b'phase-heads', data=phasedata)


def _pushb2phasespushkey(pushop, bundler):
    """push phase information through a bundle2 - pushkey part"""
    pushop.stepsdone.add(b'phases')
    part2node = []

    def handlefailure(pushop, exc):
        targetid = int(exc.partid)
        for partid, node in part2node:
            if partid == targetid:
                raise error.Abort(_(b'updating %s to public failed') % node)

    enc = pushkey.encode
    for newremotehead in pushop.outdatedphases:
        part = bundler.newpart(b'pushkey')
        part.addparam(b'namespace', enc(b'phases'))
        part.addparam(b'key', enc(newremotehead.hex()))
        part.addparam(b'old', enc(b'%d' % phases.draft))
        part.addparam(b'new', enc(b'%d' % phases.public))
        part2node.append((part.id, newremotehead))
        pushop.pkfailcb[part.id] = handlefailure

    def handlereply(op):
        for partid, node in part2node:
            partrep = op.records.getreplies(partid)
            results = partrep[b'pushkey']
            assert len(results) <= 1
            msg = None
            if not results:
                msg = _(b'server ignored update of %s to public!\n') % node
            elif not int(results[0][b'return']):
                msg = _(b'updating %s to public failed!\n') % node
            if msg is not None:
                pushop.ui.warn(msg)

    return handlereply


@b2partsgenerator(b'obsmarkers')
def _pushb2obsmarkers(pushop, bundler):
    if b'obsmarkers' in pushop.stepsdone:
        return
    remoteversions = bundle2.obsmarkersversion(bundler.capabilities)
    if obsolete.commonversion(remoteversions) is None:
        return
    pushop.stepsdone.add(b'obsmarkers')
    if pushop.outobsmarkers:
        markers = obsutil.sortedmarkers(pushop.outobsmarkers)
        bundle2.buildobsmarkerspart(bundler, markers)


@b2partsgenerator(b'bookmarks')
def _pushb2bookmarks(pushop, bundler):
    """handle bookmark push through bundle2"""
    if b'bookmarks' in pushop.stepsdone:
        return
    b2caps = bundle2.bundle2caps(pushop.remote)

    legacy = pushop.repo.ui.configlist(b'devel', b'legacy.exchange')
    legacybooks = b'bookmarks' in legacy

    if not legacybooks and b'bookmarks' in b2caps:
        return _pushb2bookmarkspart(pushop, bundler)
    elif b'pushkey' in b2caps:
        return _pushb2bookmarkspushkey(pushop, bundler)


def _bmaction(old, new):
    """small utility for bookmark pushing"""
    if not old:
        return b'export'
    elif not new:
        return b'delete'
    return b'update'


def _abortonsecretctx(pushop, node, b):
    """abort if a given bookmark points to a secret changeset"""
    if node and pushop.repo[node].phase() == phases.secret:
        raise error.Abort(
            _(b'cannot push bookmark %s as it points to a secret changeset') % b
        )


def _pushb2bookmarkspart(pushop, bundler):
    pushop.stepsdone.add(b'bookmarks')
    if not pushop.outbookmarks:
        return

    allactions = []
    data = []
    for book, old, new in pushop.outbookmarks:
        _abortonsecretctx(pushop, new, book)
        data.append((book, new))
        allactions.append((book, _bmaction(old, new)))
    checkdata = bookmod.binaryencode(pushop.repo, data)
    bundler.newpart(b'bookmarks', data=checkdata)

    def handlereply(op):
        ui = pushop.ui
        # if success
        for book, action in allactions:
            ui.status(bookmsgmap[action][0] % book)

    return handlereply


def _pushb2bookmarkspushkey(pushop, bundler):
    pushop.stepsdone.add(b'bookmarks')
    part2book = []
    enc = pushkey.encode

    def handlefailure(pushop, exc):
        targetid = int(exc.partid)
        for partid, book, action in part2book:
            if partid == targetid:
                raise error.Abort(bookmsgmap[action][1].rstrip() % book)
        # we should not be called for part we did not generated
        assert False

    for book, old, new in pushop.outbookmarks:
        _abortonsecretctx(pushop, new, book)
        part = bundler.newpart(b'pushkey')
        part.addparam(b'namespace', enc(b'bookmarks'))
        part.addparam(b'key', enc(book))
        part.addparam(b'old', enc(hex(old)))
        part.addparam(b'new', enc(hex(new)))
        action = b'update'
        if not old:
            action = b'export'
        elif not new:
            action = b'delete'
        part2book.append((part.id, book, action))
        pushop.pkfailcb[part.id] = handlefailure

    def handlereply(op):
        ui = pushop.ui
        for partid, book, action in part2book:
            partrep = op.records.getreplies(partid)
            results = partrep[b'pushkey']
            assert len(results) <= 1
            if not results:
                pushop.ui.warn(_(b'server ignored bookmark %s update\n') % book)
            else:
                ret = int(results[0][b'return'])
                if ret:
                    ui.status(bookmsgmap[action][0] % book)
                else:
                    ui.warn(bookmsgmap[action][1] % book)
                    if pushop.bkresult is not None:
                        pushop.bkresult = 1

    return handlereply


@b2partsgenerator(b'pushvars', idx=0)
def _getbundlesendvars(pushop, bundler):
    '''send shellvars via bundle2'''
    pushvars = pushop.pushvars
    if pushvars:
        shellvars = {}
        for raw in pushvars:
            if b'=' not in raw:
                msg = (
                    b"unable to parse variable '%s', should follow "
                    b"'KEY=VALUE' or 'KEY=' format"
                )
                raise error.Abort(msg % raw)
            k, v = raw.split(b'=', 1)
            shellvars[k] = v

        part = bundler.newpart(b'pushvars')

        for key, value in pycompat.iteritems(shellvars):
            part.addparam(key, value, mandatory=False)


def _pushbundle2(pushop):
    """push data to the remote using bundle2

    The only currently supported type of data is changegroup but this will
    evolve in the future."""
    bundler = bundle2.bundle20(pushop.ui, bundle2.bundle2caps(pushop.remote))
    pushback = pushop.trmanager and pushop.ui.configbool(
        b'experimental', b'bundle2.pushback'
    )

    # create reply capability
    capsblob = bundle2.encodecaps(
        bundle2.getrepocaps(pushop.repo, allowpushback=pushback, role=b'client')
    )
    bundler.newpart(b'replycaps', data=capsblob)
    replyhandlers = []
    for partgenname in b2partsgenorder:
        partgen = b2partsgenmapping[partgenname]
        ret = partgen(pushop, bundler)
        if callable(ret):
            replyhandlers.append(ret)
    # do not push if nothing to push
    if bundler.nbparts <= 1:
        return
    stream = util.chunkbuffer(bundler.getchunks())
    try:
        try:
            with pushop.remote.commandexecutor() as e:
                reply = e.callcommand(
                    b'unbundle',
                    {
                        b'bundle': stream,
                        b'heads': [b'force'],
                        b'url': pushop.remote.url(),
                    },
                ).result()
        except error.BundleValueError as exc:
            raise error.RemoteError(_(b'missing support for %s') % exc)
        try:
            trgetter = None
            if pushback:
                trgetter = pushop.trmanager.transaction
            op = bundle2.processbundle(pushop.repo, reply, trgetter)
        except error.BundleValueError as exc:
            raise error.RemoteError(_(b'missing support for %s') % exc)
        except bundle2.AbortFromPart as exc:
            pushop.ui.error(_(b'remote: %s\n') % exc)
            if exc.hint is not None:
                pushop.ui.error(_(b'remote: %s\n') % (b'(%s)' % exc.hint))
            raise error.RemoteError(_(b'push failed on remote'))
    except error.PushkeyFailed as exc:
        partid = int(exc.partid)
        if partid not in pushop.pkfailcb:
            raise
        pushop.pkfailcb[partid](pushop, exc)
    for rephand in replyhandlers:
        rephand(op)


def _pushchangeset(pushop):
    """Make the actual push of changeset bundle to remote repo"""
    if b'changesets' in pushop.stepsdone:
        return
    pushop.stepsdone.add(b'changesets')
    if not _pushcheckoutgoing(pushop):
        return

    # Should have verified this in push().
    assert pushop.remote.capable(b'unbundle')

    pushop.repo.prepushoutgoinghooks(pushop)
    outgoing = pushop.outgoing
    # TODO: get bundlecaps from remote
    bundlecaps = None
    # create a changegroup from local
    if pushop.revs is None and not (
        outgoing.excluded or pushop.repo.changelog.filteredrevs
    ):
        # push everything,
        # use the fast path, no race possible on push
        cg = changegroup.makechangegroup(
            pushop.repo,
            outgoing,
            b'01',
            b'push',
            fastpath=True,
            bundlecaps=bundlecaps,
        )
    else:
        cg = changegroup.makechangegroup(
            pushop.repo, outgoing, b'01', b'push', bundlecaps=bundlecaps
        )

    # apply changegroup to remote
    # local repo finds heads on server, finds out what
    # revs it must push. once revs transferred, if server
    # finds it has different heads (someone else won
    # commit/push race), server aborts.
    if pushop.force:
        remoteheads = [b'force']
    else:
        remoteheads = pushop.remoteheads
    # ssh: return remote's addchangegroup()
    # http: return remote's addchangegroup() or 0 for error
    pushop.cgresult = pushop.remote.unbundle(cg, remoteheads, pushop.repo.url())


def _pushsyncphase(pushop):
    """synchronise phase information locally and remotely"""
    cheads = pushop.commonheads
    # even when we don't push, exchanging phase data is useful
    remotephases = listkeys(pushop.remote, b'phases')
    if (
        pushop.ui.configbool(b'ui', b'_usedassubrepo')
        and remotephases  # server supports phases
        and pushop.cgresult is None  # nothing was pushed
        and remotephases.get(b'publishing', False)
    ):
        # When:
        # - this is a subrepo push
        # - and remote support phase
        # - and no changeset was pushed
        # - and remote is publishing
        # We may be in issue 3871 case!
        # We drop the possible phase synchronisation done by
        # courtesy to publish changesets possibly locally draft
        # on the remote.
        remotephases = {b'publishing': b'True'}
    if not remotephases:  # old server or public only reply from non-publishing
        _localphasemove(pushop, cheads)
        # don't push any phase data as there is nothing to push
    else:
        ana = phases.analyzeremotephases(pushop.repo, cheads, remotephases)
        pheads, droots = ana
        ### Apply remote phase on local
        if remotephases.get(b'publishing', False):
            _localphasemove(pushop, cheads)
        else:  # publish = False
            _localphasemove(pushop, pheads)
            _localphasemove(pushop, cheads, phases.draft)
        ### Apply local phase on remote

        if pushop.cgresult:
            if b'phases' in pushop.stepsdone:
                # phases already pushed though bundle2
                return
            outdated = pushop.outdatedphases
        else:
            outdated = pushop.fallbackoutdatedphases

        pushop.stepsdone.add(b'phases')

        # filter heads already turned public by the push
        outdated = [c for c in outdated if c.node() not in pheads]
        # fallback to independent pushkey command
        for newremotehead in outdated:
            with pushop.remote.commandexecutor() as e:
                r = e.callcommand(
                    b'pushkey',
                    {
                        b'namespace': b'phases',
                        b'key': newremotehead.hex(),
                        b'old': b'%d' % phases.draft,
                        b'new': b'%d' % phases.public,
                    },
                ).result()

            if not r:
                pushop.ui.warn(
                    _(b'updating %s to public failed!\n') % newremotehead
                )


def _localphasemove(pushop, nodes, phase=phases.public):
    """move <nodes> to <phase> in the local source repo"""
    if pushop.trmanager:
        phases.advanceboundary(
            pushop.repo, pushop.trmanager.transaction(), phase, nodes
        )
    else:
        # repo is not locked, do not change any phases!
        # Informs the user that phases should have been moved when
        # applicable.
        actualmoves = [n for n in nodes if phase < pushop.repo[n].phase()]
        phasestr = phases.phasenames[phase]
        if actualmoves:
            pushop.ui.status(
                _(
                    b'cannot lock source repo, skipping '
                    b'local %s phase update\n'
                )
                % phasestr
            )


def _pushobsolete(pushop):
    """utility function to push obsolete markers to a remote"""
    if b'obsmarkers' in pushop.stepsdone:
        return
    repo = pushop.repo
    remote = pushop.remote
    pushop.stepsdone.add(b'obsmarkers')
    if pushop.outobsmarkers:
        pushop.ui.debug(b'try to push obsolete markers to remote\n')
        rslts = []
        markers = obsutil.sortedmarkers(pushop.outobsmarkers)
        remotedata = obsolete._pushkeyescape(markers)
        for key in sorted(remotedata, reverse=True):
            # reverse sort to ensure we end with dump0
            data = remotedata[key]
            rslts.append(remote.pushkey(b'obsolete', key, b'', data))
        if [r for r in rslts if not r]:
            msg = _(b'failed to push some obsolete markers!\n')
            repo.ui.warn(msg)


def _pushbookmark(pushop):
    """Update bookmark position on remote"""
    if pushop.cgresult == 0 or b'bookmarks' in pushop.stepsdone:
        return
    pushop.stepsdone.add(b'bookmarks')
    ui = pushop.ui
    remote = pushop.remote

    for b, old, new in pushop.outbookmarks:
        action = b'update'
        if not old:
            action = b'export'
        elif not new:
            action = b'delete'

        with remote.commandexecutor() as e:
            r = e.callcommand(
                b'pushkey',
                {
                    b'namespace': b'bookmarks',
                    b'key': b,
                    b'old': hex(old),
                    b'new': hex(new),
                },
            ).result()

        if r:
            ui.status(bookmsgmap[action][0] % b)
        else:
            ui.warn(bookmsgmap[action][1] % b)
            # discovery can have set the value form invalid entry
            if pushop.bkresult is not None:
                pushop.bkresult = 1


class pulloperation(object):
    """A object that represent a single pull operation

    It purpose is to carry pull related state and very common operation.

    A new should be created at the beginning of each pull and discarded
    afterward.
    """

    def __init__(
        self,
        repo,
        remote,
        heads=None,
        force=False,
        bookmarks=(),
        remotebookmarks=None,
        streamclonerequested=None,
        includepats=None,
        excludepats=None,
        depth=None,
    ):
        # repo we pull into
        self.repo = repo
        # repo we pull from
        self.remote = remote
        # revision we try to pull (None is "all")
        self.heads = heads
        # bookmark pulled explicitly
        self.explicitbookmarks = [
            repo._bookmarks.expandname(bookmark) for bookmark in bookmarks
        ]
        # do we force pull?
        self.force = force
        # whether a streaming clone was requested
        self.streamclonerequested = streamclonerequested
        # transaction manager
        self.trmanager = None
        # set of common changeset between local and remote before pull
        self.common = None
        # set of pulled head
        self.rheads = None
        # list of missing changeset to fetch remotely
        self.fetch = None
        # remote bookmarks data
        self.remotebookmarks = remotebookmarks
        # result of changegroup pulling (used as return code by pull)
        self.cgresult = None
        # list of step already done
        self.stepsdone = set()
        # Whether we attempted a clone from pre-generated bundles.
        self.clonebundleattempted = False
        # Set of file patterns to include.
        self.includepats = includepats
        # Set of file patterns to exclude.
        self.excludepats = excludepats
        # Number of ancestor changesets to pull from each pulled head.
        self.depth = depth

    @util.propertycache
    def pulledsubset(self):
        """heads of the set of changeset target by the pull"""
        # compute target subset
        if self.heads is None:
            # We pulled every thing possible
            # sync on everything common
            c = set(self.common)
            ret = list(self.common)
            for n in self.rheads:
                if n not in c:
                    ret.append(n)
            return ret
        else:
            # We pulled a specific subset
            # sync on this subset
            return self.heads

    @util.propertycache
    def canusebundle2(self):
        return not _forcebundle1(self)

    @util.propertycache
    def remotebundle2caps(self):
        return bundle2.bundle2caps(self.remote)

    def gettransaction(self):
        # deprecated; talk to trmanager directly
        return self.trmanager.transaction()


class transactionmanager(util.transactional):
    """An object to manage the life cycle of a transaction

    It creates the transaction on demand and calls the appropriate hooks when
    closing the transaction."""

    def __init__(self, repo, source, url):
        self.repo = repo
        self.source = source
        self.url = url
        self._tr = None

    def transaction(self):
        """Return an open transaction object, constructing if necessary"""
        if not self._tr:
            trname = b'%s\n%s' % (self.source, urlutil.hidepassword(self.url))
            self._tr = self.repo.transaction(trname)
            self._tr.hookargs[b'source'] = self.source
            self._tr.hookargs[b'url'] = self.url
        return self._tr

    def close(self):
        """close transaction if created"""
        if self._tr is not None:
            self._tr.close()

    def release(self):
        """release transaction if created"""
        if self._tr is not None:
            self._tr.release()


def listkeys(remote, namespace):
    with remote.commandexecutor() as e:
        return e.callcommand(b'listkeys', {b'namespace': namespace}).result()


def _fullpullbundle2(repo, pullop):
    # The server may send a partial reply, i.e. when inlining
    # pre-computed bundles. In that case, update the common
    # set based on the results and pull another bundle.
    #
    # There are two indicators that the process is finished:
    # - no changeset has been added, or
    # - all remote heads are known locally.
    # The head check must use the unfiltered view as obsoletion
    # markers can hide heads.
    unfi = repo.unfiltered()
    unficl = unfi.changelog

    def headsofdiff(h1, h2):
        """Returns heads(h1 % h2)"""
        res = unfi.set(b'heads(%ln %% %ln)', h1, h2)
        return {ctx.node() for ctx in res}

    def headsofunion(h1, h2):
        """Returns heads((h1 + h2) - null)"""
        res = unfi.set(b'heads((%ln + %ln - null))', h1, h2)
        return {ctx.node() for ctx in res}

    while True:
        old_heads = unficl.heads()
        clstart = len(unficl)
        _pullbundle2(pullop)
        if requirements.NARROW_REQUIREMENT in repo.requirements:
            # XXX narrow clones filter the heads on the server side during
            # XXX getbundle and result in partial replies as well.
            # XXX Disable pull bundles in this case as band aid to avoid
            # XXX extra round trips.
            break
        if clstart == len(unficl):
            break
        if all(unficl.hasnode(n) for n in pullop.rheads):
            break
        new_heads = headsofdiff(unficl.heads(), old_heads)
        pullop.common = headsofunion(new_heads, pullop.common)
        pullop.rheads = set(pullop.rheads) - pullop.common


def add_confirm_callback(repo, pullop):
    """adds a finalize callback to transaction which can be used to show stats
    to user and confirm the pull before committing transaction"""

    tr = pullop.trmanager.transaction()
    scmutil.registersummarycallback(
        repo, tr, txnname=b'pull', as_validator=True
    )
    reporef = weakref.ref(repo.unfiltered())

    def prompt(tr):
        repo = reporef()
        cm = _(b'accept incoming changes (yn)?$$ &Yes $$ &No')
        if repo.ui.promptchoice(cm):
            raise error.Abort(b"user aborted")

    tr.addvalidator(b'900-pull-prompt', prompt)


def pull(
    repo,
    remote,
    heads=None,
    force=False,
    bookmarks=(),
    opargs=None,
    streamclonerequested=None,
    includepats=None,
    excludepats=None,
    depth=None,
    confirm=None,
):
    """Fetch repository data from a remote.

    This is the main function used to retrieve data from a remote repository.

    ``repo`` is the local repository to clone into.
    ``remote`` is a peer instance.
    ``heads`` is an iterable of revisions we want to pull. ``None`` (the
    default) means to pull everything from the remote.
    ``bookmarks`` is an iterable of bookmarks requesting to be pulled. By
    default, all remote bookmarks are pulled.
    ``opargs`` are additional keyword arguments to pass to ``pulloperation``
    initialization.
    ``streamclonerequested`` is a boolean indicating whether a "streaming
    clone" is requested. A "streaming clone" is essentially a raw file copy
    of revlogs from the server. This only works when the local repository is
    empty. The default value of ``None`` means to respect the server
    configuration for preferring stream clones.
    ``includepats`` and ``excludepats`` define explicit file patterns to
    include and exclude in storage, respectively. If not defined, narrow
    patterns from the repo instance are used, if available.
    ``depth`` is an integer indicating the DAG depth of history we're
    interested in. If defined, for each revision specified in ``heads``, we
    will fetch up to this many of its ancestors and data associated with them.
    ``confirm`` is a boolean indicating whether the pull should be confirmed
    before committing the transaction. This overrides HGPLAIN.

    Returns the ``pulloperation`` created for this pull.
    """
    if opargs is None:
        opargs = {}

    # We allow the narrow patterns to be passed in explicitly to provide more
    # flexibility for API consumers.
    if includepats or excludepats:
        includepats = includepats or set()
        excludepats = excludepats or set()
    else:
        includepats, excludepats = repo.narrowpats

    narrowspec.validatepatterns(includepats)
    narrowspec.validatepatterns(excludepats)

    pullop = pulloperation(
        repo,
        remote,
        heads,
        force,
        bookmarks=bookmarks,
        streamclonerequested=streamclonerequested,
        includepats=includepats,
        excludepats=excludepats,
        depth=depth,
        **pycompat.strkwargs(opargs)
    )

    peerlocal = pullop.remote.local()
    if peerlocal:
        missing = set(peerlocal.requirements) - pullop.repo.supported
        if missing:
            msg = _(
                b"required features are not"
                b" supported in the destination:"
                b" %s"
            ) % (b', '.join(sorted(missing)))
            raise error.Abort(msg)

    for category in repo._wanted_sidedata:
        # Check that a computer is registered for that category for at least
        # one revlog kind.
        for kind, computers in repo._sidedata_computers.items():
            if computers.get(category):
                break
        else:
            # This should never happen since repos are supposed to be able to
            # generate the sidedata they require.
            raise error.ProgrammingError(
                _(
                    b'sidedata category requested by local side without local'
                    b"support: '%s'"
                )
                % pycompat.bytestr(category)
            )

    pullop.trmanager = transactionmanager(repo, b'pull', remote.url())
    wlock = util.nullcontextmanager()
    if not bookmod.bookmarksinstore(repo):
        wlock = repo.wlock()
    with wlock, repo.lock(), pullop.trmanager:
        if confirm or (
            repo.ui.configbool(b"pull", b"confirm") and not repo.ui.plain()
        ):
            add_confirm_callback(repo, pullop)

        # Use the modern wire protocol, if available.
        if remote.capable(b'command-changesetdata'):
            exchangev2.pull(pullop)
        else:
            # This should ideally be in _pullbundle2(). However, it needs to run
            # before discovery to avoid extra work.
            _maybeapplyclonebundle(pullop)
            streamclone.maybeperformlegacystreamclone(pullop)
            _pulldiscovery(pullop)
            if pullop.canusebundle2:
                _fullpullbundle2(repo, pullop)
            _pullchangeset(pullop)
            _pullphase(pullop)
            _pullbookmarks(pullop)
            _pullobsolete(pullop)

    # storing remotenames
    if repo.ui.configbool(b'experimental', b'remotenames'):
        logexchange.pullremotenames(repo, remote)

    return pullop


# list of steps to perform discovery before pull
pulldiscoveryorder = []

# Mapping between step name and function
#
# This exists to help extensions wrap steps if necessary
pulldiscoverymapping = {}


def pulldiscovery(stepname):
    """decorator for function performing discovery before pull

    The function is added to the step -> function mapping and appended to the
    list of steps.  Beware that decorated function will be added in order (this
    may matter).

    You can only use this decorator for a new step, if you want to wrap a step
    from an extension, change the pulldiscovery dictionary directly."""

    def dec(func):
        assert stepname not in pulldiscoverymapping
        pulldiscoverymapping[stepname] = func
        pulldiscoveryorder.append(stepname)
        return func

    return dec


def _pulldiscovery(pullop):
    """Run all discovery steps"""
    for stepname in pulldiscoveryorder:
        step = pulldiscoverymapping[stepname]
        step(pullop)


@pulldiscovery(b'b1:bookmarks')
def _pullbookmarkbundle1(pullop):
    """fetch bookmark data in bundle1 case

    If not using bundle2, we have to fetch bookmarks before changeset
    discovery to reduce the chance and impact of race conditions."""
    if pullop.remotebookmarks is not None:
        return
    if pullop.canusebundle2 and b'listkeys' in pullop.remotebundle2caps:
        # all known bundle2 servers now support listkeys, but lets be nice with
        # new implementation.
        return
    books = listkeys(pullop.remote, b'bookmarks')
    pullop.remotebookmarks = bookmod.unhexlifybookmarks(books)


@pulldiscovery(b'changegroup')
def _pulldiscoverychangegroup(pullop):
    """discovery phase for the pull

    Current handle changeset discovery only, will change handle all discovery
    at some point."""
    tmp = discovery.findcommonincoming(
        pullop.repo, pullop.remote, heads=pullop.heads, force=pullop.force
    )
    common, fetch, rheads = tmp
    has_node = pullop.repo.unfiltered().changelog.index.has_node
    if fetch and rheads:
        # If a remote heads is filtered locally, put in back in common.
        #
        # This is a hackish solution to catch most of "common but locally
        # hidden situation".  We do not performs discovery on unfiltered
        # repository because it end up doing a pathological amount of round
        # trip for w huge amount of changeset we do not care about.
        #
        # If a set of such "common but filtered" changeset exist on the server
        # but are not including a remote heads, we'll not be able to detect it,
        scommon = set(common)
        for n in rheads:
            if has_node(n):
                if n not in scommon:
                    common.append(n)
        if set(rheads).issubset(set(common)):
            fetch = []
    pullop.common = common
    pullop.fetch = fetch
    pullop.rheads = rheads


def _pullbundle2(pullop):
    """pull data using bundle2

    For now, the only supported data are changegroup."""
    kwargs = {b'bundlecaps': caps20to10(pullop.repo, role=b'client')}

    # make ui easier to access
    ui = pullop.repo.ui

    # At the moment we don't do stream clones over bundle2. If that is
    # implemented then here's where the check for that will go.
    streaming = streamclone.canperformstreamclone(pullop, bundle2=True)[0]

    # declare pull perimeters
    kwargs[b'common'] = pullop.common
    kwargs[b'heads'] = pullop.heads or pullop.rheads

    # check server supports narrow and then adding includepats and excludepats
    servernarrow = pullop.remote.capable(wireprototypes.NARROWCAP)
    if servernarrow and pullop.includepats:
        kwargs[b'includepats'] = pullop.includepats
    if servernarrow and pullop.excludepats:
        kwargs[b'excludepats'] = pullop.excludepats

    if streaming:
        kwargs[b'cg'] = False
        kwargs[b'stream'] = True
        pullop.stepsdone.add(b'changegroup')
        pullop.stepsdone.add(b'phases')

    else:
        # pulling changegroup
        pullop.stepsdone.add(b'changegroup')

        kwargs[b'cg'] = pullop.fetch

        legacyphase = b'phases' in ui.configlist(b'devel', b'legacy.exchange')
        hasbinaryphase = b'heads' in pullop.remotebundle2caps.get(b'phases', ())
        if not legacyphase and hasbinaryphase:
            kwargs[b'phases'] = True
            pullop.stepsdone.add(b'phases')

        if b'listkeys' in pullop.remotebundle2caps:
            if b'phases' not in pullop.stepsdone:
                kwargs[b'listkeys'] = [b'phases']

    bookmarksrequested = False
    legacybookmark = b'bookmarks' in ui.configlist(b'devel', b'legacy.exchange')
    hasbinarybook = b'bookmarks' in pullop.remotebundle2caps

    if pullop.remotebookmarks is not None:
        pullop.stepsdone.add(b'request-bookmarks')

    if (
        b'request-bookmarks' not in pullop.stepsdone
        and pullop.remotebookmarks is None
        and not legacybookmark
        and hasbinarybook
    ):
        kwargs[b'bookmarks'] = True
        bookmarksrequested = True

    if b'listkeys' in pullop.remotebundle2caps:
        if b'request-bookmarks' not in pullop.stepsdone:
            # make sure to always includes bookmark data when migrating
            # `hg incoming --bundle` to using this function.
            pullop.stepsdone.add(b'request-bookmarks')
            kwargs.setdefault(b'listkeys', []).append(b'bookmarks')

    # If this is a full pull / clone and the server supports the clone bundles
    # feature, tell the server whether we attempted a clone bundle. The
    # presence of this flag indicates the client supports clone bundles. This
    # will enable the server to treat clients that support clone bundles
    # differently from those that don't.
    if (
        pullop.remote.capable(b'clonebundles')
        and pullop.heads is None
        and list(pullop.common) == [pullop.repo.nullid]
    ):
        kwargs[b'cbattempted'] = pullop.clonebundleattempted

    if streaming:
        pullop.repo.ui.status(_(b'streaming all changes\n'))
    elif not pullop.fetch:
        pullop.repo.ui.status(_(b"no changes found\n"))
        pullop.cgresult = 0
    else:
        if pullop.heads is None and list(pullop.common) == [pullop.repo.nullid]:
            pullop.repo.ui.status(_(b"requesting all changes\n"))
    if obsolete.isenabled(pullop.repo, obsolete.exchangeopt):
        remoteversions = bundle2.obsmarkersversion(pullop.remotebundle2caps)
        if obsolete.commonversion(remoteversions) is not None:
            kwargs[b'obsmarkers'] = True
            pullop.stepsdone.add(b'obsmarkers')
    _pullbundle2extraprepare(pullop, kwargs)

    remote_sidedata = bundle2.read_remote_wanted_sidedata(pullop.remote)
    if remote_sidedata:
        kwargs[b'remote_sidedata'] = remote_sidedata

    with pullop.remote.commandexecutor() as e:
        args = dict(kwargs)
        args[b'source'] = b'pull'
        bundle = e.callcommand(b'getbundle', args).result()

        try:
            op = bundle2.bundleoperation(
                pullop.repo, pullop.gettransaction, source=b'pull'
            )
            op.modes[b'bookmarks'] = b'records'
            bundle2.processbundle(pullop.repo, bundle, op=op)
        except bundle2.AbortFromPart as exc:
            pullop.repo.ui.error(_(b'remote: abort: %s\n') % exc)
            raise error.RemoteError(_(b'pull failed on remote'), hint=exc.hint)
        except error.BundleValueError as exc:
            raise error.RemoteError(_(b'missing support for %s') % exc)

    if pullop.fetch:
        pullop.cgresult = bundle2.combinechangegroupresults(op)

    # processing phases change
    for namespace, value in op.records[b'listkeys']:
        if namespace == b'phases':
            _pullapplyphases(pullop, value)

    # processing bookmark update
    if bookmarksrequested:
        books = {}
        for record in op.records[b'bookmarks']:
            books[record[b'bookmark']] = record[b"node"]
        pullop.remotebookmarks = books
    else:
        for namespace, value in op.records[b'listkeys']:
            if namespace == b'bookmarks':
                pullop.remotebookmarks = bookmod.unhexlifybookmarks(value)

    # bookmark data were either already there or pulled in the bundle
    if pullop.remotebookmarks is not None:
        _pullbookmarks(pullop)


def _pullbundle2extraprepare(pullop, kwargs):
    """hook function so that extensions can extend the getbundle call"""


def _pullchangeset(pullop):
    """pull changeset from unbundle into the local repo"""
    # We delay the open of the transaction as late as possible so we
    # don't open transaction for nothing or you break future useful
    # rollback call
    if b'changegroup' in pullop.stepsdone:
        return
    pullop.stepsdone.add(b'changegroup')
    if not pullop.fetch:
        pullop.repo.ui.status(_(b"no changes found\n"))
        pullop.cgresult = 0
        return
    tr = pullop.gettransaction()
    if pullop.heads is None and list(pullop.common) == [pullop.repo.nullid]:
        pullop.repo.ui.status(_(b"requesting all changes\n"))
    elif pullop.heads is None and pullop.remote.capable(b'changegroupsubset'):
        # issue1320, avoid a race if remote changed after discovery
        pullop.heads = pullop.rheads

    if pullop.remote.capable(b'getbundle'):
        # TODO: get bundlecaps from remote
        cg = pullop.remote.getbundle(
            b'pull', common=pullop.common, heads=pullop.heads or pullop.rheads
        )
    elif pullop.heads is None:
        with pullop.remote.commandexecutor() as e:
            cg = e.callcommand(
                b'changegroup',
                {
                    b'nodes': pullop.fetch,
                    b'source': b'pull',
                },
            ).result()

    elif not pullop.remote.capable(b'changegroupsubset'):
        raise error.Abort(
            _(
                b"partial pull cannot be done because "
                b"other repository doesn't support "
                b"changegroupsubset."
            )
        )
    else:
        with pullop.remote.commandexecutor() as e:
            cg = e.callcommand(
                b'changegroupsubset',
                {
                    b'bases': pullop.fetch,
                    b'heads': pullop.heads,
                    b'source': b'pull',
                },
            ).result()

    bundleop = bundle2.applybundle(
        pullop.repo, cg, tr, b'pull', pullop.remote.url()
    )
    pullop.cgresult = bundle2.combinechangegroupresults(bundleop)


def _pullphase(pullop):
    # Get remote phases data from remote
    if b'phases' in pullop.stepsdone:
        return
    remotephases = listkeys(pullop.remote, b'phases')
    _pullapplyphases(pullop, remotephases)


def _pullapplyphases(pullop, remotephases):
    """apply phase movement from observed remote state"""
    if b'phases' in pullop.stepsdone:
        return
    pullop.stepsdone.add(b'phases')
    publishing = bool(remotephases.get(b'publishing', False))
    if remotephases and not publishing:
        # remote is new and non-publishing
        pheads, _dr = phases.analyzeremotephases(
            pullop.repo, pullop.pulledsubset, remotephases
        )
        dheads = pullop.pulledsubset
    else:
        # Remote is old or publishing all common changesets
        # should be seen as public
        pheads = pullop.pulledsubset
        dheads = []
    unfi = pullop.repo.unfiltered()
    phase = unfi._phasecache.phase
    rev = unfi.changelog.index.get_rev
    public = phases.public
    draft = phases.draft

    # exclude changesets already public locally and update the others
    pheads = [pn for pn in pheads if phase(unfi, rev(pn)) > public]
    if pheads:
        tr = pullop.gettransaction()
        phases.advanceboundary(pullop.repo, tr, public, pheads)

    # exclude changesets already draft locally and update the others
    dheads = [pn for pn in dheads if phase(unfi, rev(pn)) > draft]
    if dheads:
        tr = pullop.gettransaction()
        phases.advanceboundary(pullop.repo, tr, draft, dheads)


def _pullbookmarks(pullop):
    """process the remote bookmark information to update the local one"""
    if b'bookmarks' in pullop.stepsdone:
        return
    pullop.stepsdone.add(b'bookmarks')
    repo = pullop.repo
    remotebookmarks = pullop.remotebookmarks
    bookmod.updatefromremote(
        repo.ui,
        repo,
        remotebookmarks,
        pullop.remote.url(),
        pullop.gettransaction,
        explicit=pullop.explicitbookmarks,
    )


def _pullobsolete(pullop):
    """utility function to pull obsolete markers from a remote

    The `gettransaction` is function that return the pull transaction, creating
    one if necessary. We return the transaction to inform the calling code that
    a new transaction have been created (when applicable).

    Exists mostly to allow overriding for experimentation purpose"""
    if b'obsmarkers' in pullop.stepsdone:
        return
    pullop.stepsdone.add(b'obsmarkers')
    tr = None
    if obsolete.isenabled(pullop.repo, obsolete.exchangeopt):
        pullop.repo.ui.debug(b'fetching remote obsolete markers\n')
        remoteobs = listkeys(pullop.remote, b'obsolete')
        if b'dump0' in remoteobs:
            tr = pullop.gettransaction()
            markers = []
            for key in sorted(remoteobs, reverse=True):
                if key.startswith(b'dump'):
                    data = util.b85decode(remoteobs[key])
                    version, newmarks = obsolete._readmarkers(data)
                    markers += newmarks
            if markers:
                pullop.repo.obsstore.add(tr, markers)
            pullop.repo.invalidatevolatilesets()
    return tr


def applynarrowacl(repo, kwargs):
    """Apply narrow fetch access control.

    This massages the named arguments for getbundle wire protocol commands
    so requested data is filtered through access control rules.
    """
    ui = repo.ui
    # TODO this assumes existence of HTTP and is a layering violation.
    username = ui.shortuser(ui.environ.get(b'REMOTE_USER') or ui.username())
    user_includes = ui.configlist(
        _NARROWACL_SECTION,
        username + b'.includes',
        ui.configlist(_NARROWACL_SECTION, b'default.includes'),
    )
    user_excludes = ui.configlist(
        _NARROWACL_SECTION,
        username + b'.excludes',
        ui.configlist(_NARROWACL_SECTION, b'default.excludes'),
    )
    if not user_includes:
        raise error.Abort(
            _(b"%s configuration for user %s is empty")
            % (_NARROWACL_SECTION, username)
        )

    user_includes = [
        b'path:.' if p == b'*' else b'path:' + p for p in user_includes
    ]
    user_excludes = [
        b'path:.' if p == b'*' else b'path:' + p for p in user_excludes
    ]

    req_includes = set(kwargs.get('includepats', []))
    req_excludes = set(kwargs.get('excludepats', []))

    req_includes, req_excludes, invalid_includes = narrowspec.restrictpatterns(
        req_includes, req_excludes, user_includes, user_excludes
    )

    if invalid_includes:
        raise error.Abort(
            _(b"The following includes are not accessible for %s: %s")
            % (username, stringutil.pprint(invalid_includes))
        )

    new_args = {}
    new_args.update(kwargs)
    new_args['narrow'] = True
    new_args['narrow_acl'] = True
    new_args['includepats'] = req_includes
    if req_excludes:
        new_args['excludepats'] = req_excludes

    return new_args


def _computeellipsis(repo, common, heads, known, match, depth=None):
    """Compute the shape of a narrowed DAG.

    Args:
      repo: The repository we're transferring.
      common: The roots of the DAG range we're transferring.
              May be just [nullid], which means all ancestors of heads.
      heads: The heads of the DAG range we're transferring.
      match: The narrowmatcher that allows us to identify relevant changes.
      depth: If not None, only consider nodes to be full nodes if they are at
             most depth changesets away from one of heads.

    Returns:
      A tuple of (visitnodes, relevant_nodes, ellipsisroots) where:

        visitnodes: The list of nodes (either full or ellipsis) which
                    need to be sent to the client.
        relevant_nodes: The set of changelog nodes which change a file inside
                 the narrowspec. The client needs these as non-ellipsis nodes.
        ellipsisroots: A dict of {rev: parents} that is used in
                       narrowchangegroup to produce ellipsis nodes with the
                       correct parents.
    """
    cl = repo.changelog
    mfl = repo.manifestlog

    clrev = cl.rev

    commonrevs = {clrev(n) for n in common} | {nullrev}
    headsrevs = {clrev(n) for n in heads}

    if depth:
        revdepth = {h: 0 for h in headsrevs}

    ellipsisheads = collections.defaultdict(set)
    ellipsisroots = collections.defaultdict(set)

    def addroot(head, curchange):
        """Add a root to an ellipsis head, splitting heads with 3 roots."""
        ellipsisroots[head].add(curchange)
        # Recursively split ellipsis heads with 3 roots by finding the
        # roots' youngest common descendant which is an elided merge commit.
        # That descendant takes 2 of the 3 roots as its own, and becomes a
        # root of the head.
        while len(ellipsisroots[head]) > 2:
            child, roots = splithead(head)
            splitroots(head, child, roots)
            head = child  # Recurse in case we just added a 3rd root

    def splitroots(head, child, roots):
        ellipsisroots[head].difference_update(roots)
        ellipsisroots[head].add(child)
        ellipsisroots[child].update(roots)
        ellipsisroots[child].discard(child)

    def splithead(head):
        r1, r2, r3 = sorted(ellipsisroots[head])
        for nr1, nr2 in ((r2, r3), (r1, r3), (r1, r2)):
            mid = repo.revs(
                b'sort(merge() & %d::%d & %d::%d, -rev)', nr1, head, nr2, head
            )
            for j in mid:
                if j == nr2:
                    return nr2, (nr1, nr2)
                if j not in ellipsisroots or len(ellipsisroots[j]) < 2:
                    return j, (nr1, nr2)
        raise error.Abort(
            _(
                b'Failed to split up ellipsis node! head: %d, '
                b'roots: %d %d %d'
            )
            % (head, r1, r2, r3)
        )

    missing = list(cl.findmissingrevs(common=commonrevs, heads=headsrevs))
    visit = reversed(missing)
    relevant_nodes = set()
    visitnodes = [cl.node(m) for m in missing]
    required = set(headsrevs) | known
    for rev in visit:
        clrev = cl.changelogrevision(rev)
        ps = [prev for prev in cl.parentrevs(rev) if prev != nullrev]
        if depth is not None:
            curdepth = revdepth[rev]
            for p in ps:
                revdepth[p] = min(curdepth + 1, revdepth.get(p, depth + 1))
        needed = False
        shallow_enough = depth is None or revdepth[rev] <= depth
        if shallow_enough:
            curmf = mfl[clrev.manifest].read()
            if ps:
                # We choose to not trust the changed files list in
                # changesets because it's not always correct. TODO: could
                # we trust it for the non-merge case?
                p1mf = mfl[cl.changelogrevision(ps[0]).manifest].read()
                needed = bool(curmf.diff(p1mf, match))
                if not needed and len(ps) > 1:
                    # For merge changes, the list of changed files is not
                    # helpful, since we need to emit the merge if a file
                    # in the narrow spec has changed on either side of the
                    # merge. As a result, we do a manifest diff to check.
                    p2mf = mfl[cl.changelogrevision(ps[1]).manifest].read()
                    needed = bool(curmf.diff(p2mf, match))
            else:
                # For a root node, we need to include the node if any
                # files in the node match the narrowspec.
                needed = any(curmf.walk(match))

        if needed:
            for head in ellipsisheads[rev]:
                addroot(head, rev)
            for p in ps:
                required.add(p)
            relevant_nodes.add(cl.node(rev))
        else:
            if not ps:
                ps = [nullrev]
            if rev in required:
                for head in ellipsisheads[rev]:
                    addroot(head, rev)
                for p in ps:
                    ellipsisheads[p].add(rev)
            else:
                for p in ps:
                    ellipsisheads[p] |= ellipsisheads[rev]

    # add common changesets as roots of their reachable ellipsis heads
    for c in commonrevs:
        for head in ellipsisheads[c]:
            addroot(head, c)
    return visitnodes, relevant_nodes, ellipsisroots


def caps20to10(repo, role):
    """return a set with appropriate options to use bundle20 during getbundle"""
    caps = {b'HG20'}
    capsblob = bundle2.encodecaps(bundle2.getrepocaps(repo, role=role))
    caps.add(b'bundle2=' + urlreq.quote(capsblob))
    return caps


# List of names of steps to perform for a bundle2 for getbundle, order matters.
getbundle2partsorder = []

# Mapping between step name and function
#
# This exists to help extensions wrap steps if necessary
getbundle2partsmapping = {}


def getbundle2partsgenerator(stepname, idx=None):
    """decorator for function generating bundle2 part for getbundle

    The function is added to the step -> function mapping and appended to the
    list of steps.  Beware that decorated functions will be added in order
    (this may matter).

    You can only use this decorator for new steps, if you want to wrap a step
    from an extension, attack the getbundle2partsmapping dictionary directly."""

    def dec(func):
        assert stepname not in getbundle2partsmapping
        getbundle2partsmapping[stepname] = func
        if idx is None:
            getbundle2partsorder.append(stepname)
        else:
            getbundle2partsorder.insert(idx, stepname)
        return func

    return dec


def bundle2requested(bundlecaps):
    if bundlecaps is not None:
        return any(cap.startswith(b'HG2') for cap in bundlecaps)
    return False


def getbundlechunks(
    repo,
    source,
    heads=None,
    common=None,
    bundlecaps=None,
    remote_sidedata=None,
    **kwargs
):
    """Return chunks constituting a bundle's raw data.

    Could be a bundle HG10 or a bundle HG20 depending on bundlecaps
    passed.

    Returns a 2-tuple of a dict with metadata about the generated bundle
    and an iterator over raw chunks (of varying sizes).
    """
    kwargs = pycompat.byteskwargs(kwargs)
    info = {}
    usebundle2 = bundle2requested(bundlecaps)
    # bundle10 case
    if not usebundle2:
        if bundlecaps and not kwargs.get(b'cg', True):
            raise ValueError(
                _(b'request for bundle10 must include changegroup')
            )

        if kwargs:
            raise ValueError(
                _(b'unsupported getbundle arguments: %s')
                % b', '.join(sorted(kwargs.keys()))
            )
        outgoing = _computeoutgoing(repo, heads, common)
        info[b'bundleversion'] = 1
        return (
            info,
            changegroup.makestream(
                repo,
                outgoing,
                b'01',
                source,
                bundlecaps=bundlecaps,
                remote_sidedata=remote_sidedata,
            ),
        )

    # bundle20 case
    info[b'bundleversion'] = 2
    b2caps = {}
    for bcaps in bundlecaps:
        if bcaps.startswith(b'bundle2='):
            blob = urlreq.unquote(bcaps[len(b'bundle2=') :])
            b2caps.update(bundle2.decodecaps(blob))
    bundler = bundle2.bundle20(repo.ui, b2caps)

    kwargs[b'heads'] = heads
    kwargs[b'common'] = common

    for name in getbundle2partsorder:
        func = getbundle2partsmapping[name]
        func(
            bundler,
            repo,
            source,
            bundlecaps=bundlecaps,
            b2caps=b2caps,
            remote_sidedata=remote_sidedata,
            **pycompat.strkwargs(kwargs)
        )

    info[b'prefercompressed'] = bundler.prefercompressed

    return info, bundler.getchunks()


@getbundle2partsgenerator(b'stream2')
def _getbundlestream2(bundler, repo, *args, **kwargs):
    return bundle2.addpartbundlestream2(bundler, repo, **kwargs)


@getbundle2partsgenerator(b'changegroup')
def _getbundlechangegrouppart(
    bundler,
    repo,
    source,
    bundlecaps=None,
    b2caps=None,
    heads=None,
    common=None,
    remote_sidedata=None,
    **kwargs
):
    """add a changegroup part to the requested bundle"""
    if not kwargs.get('cg', True) or not b2caps:
        return

    version = b'01'
    cgversions = b2caps.get(b'changegroup')
    if cgversions:  # 3.1 and 3.2 ship with an empty value
        cgversions = [
            v
            for v in cgversions
            if v in changegroup.supportedoutgoingversions(repo)
        ]
        if not cgversions:
            raise error.Abort(_(b'no common changegroup version'))
        version = max(cgversions)

    outgoing = _computeoutgoing(repo, heads, common)
    if not outgoing.missing:
        return

    if kwargs.get('narrow', False):
        include = sorted(filter(bool, kwargs.get('includepats', [])))
        exclude = sorted(filter(bool, kwargs.get('excludepats', [])))
        matcher = narrowspec.match(repo.root, include=include, exclude=exclude)
    else:
        matcher = None

    cgstream = changegroup.makestream(
        repo,
        outgoing,
        version,
        source,
        bundlecaps=bundlecaps,
        matcher=matcher,
        remote_sidedata=remote_sidedata,
    )

    part = bundler.newpart(b'changegroup', data=cgstream)
    if cgversions:
        part.addparam(b'version', version)

    part.addparam(b'nbchanges', b'%d' % len(outgoing.missing), mandatory=False)

    if scmutil.istreemanifest(repo):
        part.addparam(b'treemanifest', b'1')

    if repository.REPO_FEATURE_SIDE_DATA in repo.features:
        part.addparam(b'exp-sidedata', b'1')
        sidedata = bundle2.format_remote_wanted_sidedata(repo)
        part.addparam(b'exp-wanted-sidedata', sidedata)

    if (
        kwargs.get('narrow', False)
        and kwargs.get('narrow_acl', False)
        and (include or exclude)
    ):
        # this is mandatory because otherwise ACL clients won't work
        narrowspecpart = bundler.newpart(b'Narrow:responsespec')
        narrowspecpart.data = b'%s\0%s' % (
            b'\n'.join(include),
            b'\n'.join(exclude),
        )


@getbundle2partsgenerator(b'bookmarks')
def _getbundlebookmarkpart(
    bundler, repo, source, bundlecaps=None, b2caps=None, **kwargs
):
    """add a bookmark part to the requested bundle"""
    if not kwargs.get('bookmarks', False):
        return
    if not b2caps or b'bookmarks' not in b2caps:
        raise error.Abort(_(b'no common bookmarks exchange method'))
    books = bookmod.listbinbookmarks(repo)
    data = bookmod.binaryencode(repo, books)
    if data:
        bundler.newpart(b'bookmarks', data=data)


@getbundle2partsgenerator(b'listkeys')
def _getbundlelistkeysparts(
    bundler, repo, source, bundlecaps=None, b2caps=None, **kwargs
):
    """add parts containing listkeys namespaces to the requested bundle"""
    listkeys = kwargs.get('listkeys', ())
    for namespace in listkeys:
        part = bundler.newpart(b'listkeys')
        part.addparam(b'namespace', namespace)
        keys = repo.listkeys(namespace).items()
        part.data = pushkey.encodekeys(keys)


@getbundle2partsgenerator(b'obsmarkers')
def _getbundleobsmarkerpart(
    bundler, repo, source, bundlecaps=None, b2caps=None, heads=None, **kwargs
):
    """add an obsolescence markers part to the requested bundle"""
    if kwargs.get('obsmarkers', False):
        if heads is None:
            heads = repo.heads()
        subset = [c.node() for c in repo.set(b'::%ln', heads)]
        markers = repo.obsstore.relevantmarkers(subset)
        markers = obsutil.sortedmarkers(markers)
        bundle2.buildobsmarkerspart(bundler, markers)


@getbundle2partsgenerator(b'phases')
def _getbundlephasespart(
    bundler, repo, source, bundlecaps=None, b2caps=None, heads=None, **kwargs
):
    """add phase heads part to the requested bundle"""
    if kwargs.get('phases', False):
        if not b2caps or b'heads' not in b2caps.get(b'phases'):
            raise error.Abort(_(b'no common phases exchange method'))
        if heads is None:
            heads = repo.heads()

        headsbyphase = collections.defaultdict(set)
        if repo.publishing():
            headsbyphase[phases.public] = heads
        else:
            # find the appropriate heads to move

            phase = repo._phasecache.phase
            node = repo.changelog.node
            rev = repo.changelog.rev
            for h in heads:
                headsbyphase[phase(repo, rev(h))].add(h)
            seenphases = list(headsbyphase.keys())

            # We do not handle anything but public and draft phase for now)
            if seenphases:
                assert max(seenphases) <= phases.draft

            # if client is pulling non-public changesets, we need to find
            # intermediate public heads.
            draftheads = headsbyphase.get(phases.draft, set())
            if draftheads:
                publicheads = headsbyphase.get(phases.public, set())

                revset = b'heads(only(%ln, %ln) and public())'
                extraheads = repo.revs(revset, draftheads, publicheads)
                for r in extraheads:
                    headsbyphase[phases.public].add(node(r))

        # transform data in a format used by the encoding function
        phasemapping = {
            phase: sorted(headsbyphase[phase]) for phase in phases.allphases
        }

        # generate the actual part
        phasedata = phases.binaryencode(phasemapping)
        bundler.newpart(b'phase-heads', data=phasedata)


@getbundle2partsgenerator(b'hgtagsfnodes')
def _getbundletagsfnodes(
    bundler,
    repo,
    source,
    bundlecaps=None,
    b2caps=None,
    heads=None,
    common=None,
    **kwargs
):
    """Transfer the .hgtags filenodes mapping.

    Only values for heads in this bundle will be transferred.

    The part data consists of pairs of 20 byte changeset node and .hgtags
    filenodes raw values.
    """
    # Don't send unless:
    # - changeset are being exchanged,
    # - the client supports it.
    if not b2caps or not (kwargs.get('cg', True) and b'hgtagsfnodes' in b2caps):
        return

    outgoing = _computeoutgoing(repo, heads, common)
    bundle2.addparttagsfnodescache(repo, bundler, outgoing)


@getbundle2partsgenerator(b'cache:rev-branch-cache')
def _getbundlerevbranchcache(
    bundler,
    repo,
    source,
    bundlecaps=None,
    b2caps=None,
    heads=None,
    common=None,
    **kwargs
):
    """Transfer the rev-branch-cache mapping

    The payload is a series of data related to each branch

    1) branch name length
    2) number of open heads
    3) number of closed heads
    4) open heads nodes
    5) closed heads nodes
    """
    # Don't send unless:
    # - changeset are being exchanged,
    # - the client supports it.
    # - narrow bundle isn't in play (not currently compatible).
    if (
        not kwargs.get('cg', True)
        or not b2caps
        or b'rev-branch-cache' not in b2caps
        or kwargs.get('narrow', False)
        or repo.ui.has_section(_NARROWACL_SECTION)
    ):
        return

    outgoing = _computeoutgoing(repo, heads, common)
    bundle2.addpartrevbranchcache(repo, bundler, outgoing)


def check_heads(repo, their_heads, context):
    """check if the heads of a repo have been modified

    Used by peer for unbundling.
    """
    heads = repo.heads()
    heads_hash = hashutil.sha1(b''.join(sorted(heads))).digest()
    if not (
        their_heads == [b'force']
        or their_heads == heads
        or their_heads == [b'hashed', heads_hash]
    ):
        # someone else committed/pushed/unbundled while we
        # were transferring data
        raise error.PushRaced(
            b'repository changed while %s - please try again' % context
        )


def unbundle(repo, cg, heads, source, url):
    """Apply a bundle to a repo.

    this function makes sure the repo is locked during the application and have
    mechanism to check that no push race occurred between the creation of the
    bundle and its application.

    If the push was raced as PushRaced exception is raised."""
    r = 0
    # need a transaction when processing a bundle2 stream
    # [wlock, lock, tr] - needs to be an array so nested functions can modify it
    lockandtr = [None, None, None]
    recordout = None
    # quick fix for output mismatch with bundle2 in 3.4
    captureoutput = repo.ui.configbool(
        b'experimental', b'bundle2-output-capture'
    )
    if url.startswith(b'remote:http:') or url.startswith(b'remote:https:'):
        captureoutput = True
    try:
        # note: outside bundle1, 'heads' is expected to be empty and this
        # 'check_heads' call wil be a no-op
        check_heads(repo, heads, b'uploading changes')
        # push can proceed
        if not isinstance(cg, bundle2.unbundle20):
            # legacy case: bundle1 (changegroup 01)
            txnname = b"\n".join([source, urlutil.hidepassword(url)])
            with repo.lock(), repo.transaction(txnname) as tr:
                op = bundle2.applybundle(repo, cg, tr, source, url)
                r = bundle2.combinechangegroupresults(op)
        else:
            r = None
            try:

                def gettransaction():
                    if not lockandtr[2]:
                        if not bookmod.bookmarksinstore(repo):
                            lockandtr[0] = repo.wlock()
                        lockandtr[1] = repo.lock()
                        lockandtr[2] = repo.transaction(source)
                        lockandtr[2].hookargs[b'source'] = source
                        lockandtr[2].hookargs[b'url'] = url
                        lockandtr[2].hookargs[b'bundle2'] = b'1'
                    return lockandtr[2]

                # Do greedy locking by default until we're satisfied with lazy
                # locking.
                if not repo.ui.configbool(
                    b'experimental', b'bundle2lazylocking'
                ):
                    gettransaction()

                op = bundle2.bundleoperation(
                    repo,
                    gettransaction,
                    captureoutput=captureoutput,
                    source=b'push',
                )
                try:
                    op = bundle2.processbundle(repo, cg, op=op)
                finally:
                    r = op.reply
                    if captureoutput and r is not None:
                        repo.ui.pushbuffer(error=True, subproc=True)

                        def recordout(output):
                            r.newpart(b'output', data=output, mandatory=False)

                if lockandtr[2] is not None:
                    lockandtr[2].close()
            except BaseException as exc:
                exc.duringunbundle2 = True
                if captureoutput and r is not None:
                    parts = exc._bundle2salvagedoutput = r.salvageoutput()

                    def recordout(output):
                        part = bundle2.bundlepart(
                            b'output', data=output, mandatory=False
                        )
                        parts.append(part)

                raise
    finally:
        lockmod.release(lockandtr[2], lockandtr[1], lockandtr[0])
        if recordout is not None:
            recordout(repo.ui.popbuffer())
    return r


def _maybeapplyclonebundle(pullop):
    """Apply a clone bundle from a remote, if possible."""

    repo = pullop.repo
    remote = pullop.remote

    if not repo.ui.configbool(b'ui', b'clonebundles'):
        return

    # Only run if local repo is empty.
    if len(repo):
        return

    if pullop.heads:
        return

    if not remote.capable(b'clonebundles'):
        return

    with remote.commandexecutor() as e:
        res = e.callcommand(b'clonebundles', {}).result()

    # If we call the wire protocol command, that's good enough to record the
    # attempt.
    pullop.clonebundleattempted = True

    entries = bundlecaches.parseclonebundlesmanifest(repo, res)
    if not entries:
        repo.ui.note(
            _(
                b'no clone bundles available on remote; '
                b'falling back to regular clone\n'
            )
        )
        return

    entries = bundlecaches.filterclonebundleentries(
        repo, entries, streamclonerequested=pullop.streamclonerequested
    )

    if not entries:
        # There is a thundering herd concern here. However, if a server
        # operator doesn't advertise bundles appropriate for its clients,
        # they deserve what's coming. Furthermore, from a client's
        # perspective, no automatic fallback would mean not being able to
        # clone!
        repo.ui.warn(
            _(
                b'no compatible clone bundles available on server; '
                b'falling back to regular clone\n'
            )
        )
        repo.ui.warn(
            _(b'(you may want to report this to the server operator)\n')
        )
        return

    entries = bundlecaches.sortclonebundleentries(repo.ui, entries)

    url = entries[0][b'URL']
    repo.ui.status(_(b'applying clone bundle from %s\n') % url)
    if trypullbundlefromurl(repo.ui, repo, url):
        repo.ui.status(_(b'finished applying clone bundle\n'))
    # Bundle failed.
    #
    # We abort by default to avoid the thundering herd of
    # clients flooding a server that was expecting expensive
    # clone load to be offloaded.
    elif repo.ui.configbool(b'ui', b'clonebundlefallback'):
        repo.ui.warn(_(b'falling back to normal clone\n'))
    else:
        raise error.Abort(
            _(b'error applying bundle'),
            hint=_(
                b'if this error persists, consider contacting '
                b'the server operator or disable clone '
                b'bundles via '
                b'"--config ui.clonebundles=false"'
            ),
        )


def trypullbundlefromurl(ui, repo, url):
    """Attempt to apply a bundle from a URL."""
    with repo.lock(), repo.transaction(b'bundleurl') as tr:
        try:
            fh = urlmod.open(ui, url)
            cg = readbundle(ui, fh, b'stream')

            if isinstance(cg, streamclone.streamcloneapplier):
                cg.apply(repo)
            else:
                bundle2.applybundle(repo, cg, tr, b'clonebundles', url)
            return True
        except urlerr.httperror as e:
            ui.warn(
                _(b'HTTP error fetching bundle: %s\n')
                % stringutil.forcebytestr(e)
            )
        except urlerr.urlerror as e:
            ui.warn(
                _(b'error fetching bundle: %s\n')
                % stringutil.forcebytestr(e.reason)
            )

        return False
