# exchangev2.py - repository exchange for wire protocol version 2
#
# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import collections
import weakref

from .i18n import _
from .node import short
from . import (
    bookmarks,
    error,
    mdiff,
    narrowspec,
    phases,
    pycompat,
    requirements as requirementsmod,
    setdiscovery,
)
from .interfaces import repository


def pull(pullop):
    """Pull using wire protocol version 2."""
    repo = pullop.repo
    remote = pullop.remote

    usingrawchangelogandmanifest = _checkuserawstorefiledata(pullop)

    # If this is a clone and it was requested to perform a "stream clone",
    # we obtain the raw files data from the remote then fall back to an
    # incremental pull. This is somewhat hacky and is not nearly robust enough
    # for long-term usage.
    if usingrawchangelogandmanifest:
        with repo.transaction(b'clone'):
            _fetchrawstorefiles(repo, remote)
            repo.invalidate(clearfilecache=True)

    tr = pullop.trmanager.transaction()

    # We don't use the repo's narrow matcher here because the patterns passed
    # to exchange.pull() could be different.
    narrowmatcher = narrowspec.match(
        repo.root,
        # Empty maps to nevermatcher. So always
        # set includes if missing.
        pullop.includepats or {b'path:.'},
        pullop.excludepats,
    )

    if pullop.includepats or pullop.excludepats:
        pathfilter = {}
        if pullop.includepats:
            pathfilter[b'include'] = sorted(pullop.includepats)
        if pullop.excludepats:
            pathfilter[b'exclude'] = sorted(pullop.excludepats)
    else:
        pathfilter = None

    # Figure out what needs to be fetched.
    common, fetch, remoteheads = _pullchangesetdiscovery(
        repo, remote, pullop.heads, abortwhenunrelated=pullop.force
    )

    # And fetch the data.
    pullheads = pullop.heads or remoteheads
    csetres = _fetchchangesets(repo, tr, remote, common, fetch, pullheads)

    # New revisions are written to the changelog. But all other updates
    # are deferred. Do those now.

    # Ensure all new changesets are draft by default. If the repo is
    # publishing, the phase will be adjusted by the loop below.
    if csetres[b'added']:
        phases.registernew(
            repo, tr, phases.draft, [repo[n].rev() for n in csetres[b'added']]
        )

    # And adjust the phase of all changesets accordingly.
    for phasenumber, phase in phases.phasenames.items():
        if phase == b'secret' or not csetres[b'nodesbyphase'][phase]:
            continue

        phases.advanceboundary(
            repo,
            tr,
            phasenumber,
            csetres[b'nodesbyphase'][phase],
        )

    # Write bookmark updates.
    bookmarks.updatefromremote(
        repo.ui,
        repo,
        csetres[b'bookmarks'],
        remote.url(),
        pullop.gettransaction,
        explicit=pullop.explicitbookmarks,
    )

    manres = _fetchmanifests(repo, tr, remote, csetres[b'manifestnodes'])

    # We don't properly support shallow changeset and manifest yet. So we apply
    # depth limiting locally.
    if pullop.depth:
        relevantcsetnodes = set()
        clnode = repo.changelog.node

        for rev in repo.revs(
            b'ancestors(%ln, %s)', pullheads, pullop.depth - 1
        ):
            relevantcsetnodes.add(clnode(rev))

        csetrelevantfilter = lambda n: n in relevantcsetnodes

    else:
        csetrelevantfilter = lambda n: True

    # If obtaining the raw store files, we need to scan the full repo to
    # derive all the changesets, manifests, and linkrevs.
    if usingrawchangelogandmanifest:
        csetsforfiles = []
        mnodesforfiles = []
        manifestlinkrevs = {}

        for rev in repo:
            ctx = repo[rev]
            node = ctx.node()

            if not csetrelevantfilter(node):
                continue

            mnode = ctx.manifestnode()

            csetsforfiles.append(node)
            mnodesforfiles.append(mnode)
            manifestlinkrevs[mnode] = rev

    else:
        csetsforfiles = [n for n in csetres[b'added'] if csetrelevantfilter(n)]
        mnodesforfiles = manres[b'added']
        manifestlinkrevs = manres[b'linkrevs']

    # Find all file nodes referenced by added manifests and fetch those
    # revisions.
    fnodes = _derivefilesfrommanifests(repo, narrowmatcher, mnodesforfiles)
    _fetchfilesfromcsets(
        repo,
        tr,
        remote,
        pathfilter,
        fnodes,
        csetsforfiles,
        manifestlinkrevs,
        shallow=bool(pullop.depth),
    )


def _checkuserawstorefiledata(pullop):
    """Check whether we should use rawstorefiledata command to retrieve data."""

    repo = pullop.repo
    remote = pullop.remote

    # Command to obtain raw store data isn't available.
    if b'rawstorefiledata' not in remote.apidescriptor[b'commands']:
        return False

    # Only honor if user requested stream clone operation.
    if not pullop.streamclonerequested:
        return False

    # Only works on empty repos.
    if len(repo):
        return False

    # TODO This is super hacky. There needs to be a storage API for this. We
    # also need to check for compatibility with the remote.
    if requirementsmod.REVLOGV1_REQUIREMENT not in repo.requirements:
        return False

    return True


def _fetchrawstorefiles(repo, remote):
    with remote.commandexecutor() as e:
        objs = e.callcommand(
            b'rawstorefiledata',
            {
                b'files': [b'changelog', b'manifestlog'],
            },
        ).result()

        # First object is a summary of files data that follows.
        overall = next(objs)

        progress = repo.ui.makeprogress(
            _(b'clone'), total=overall[b'totalsize'], unit=_(b'bytes')
        )
        with progress:
            progress.update(0)

            # Next are pairs of file metadata, data.
            while True:
                try:
                    filemeta = next(objs)
                except StopIteration:
                    break

                for k in (b'location', b'path', b'size'):
                    if k not in filemeta:
                        raise error.Abort(
                            _(b'remote file data missing key: %s') % k
                        )

                if filemeta[b'location'] == b'store':
                    vfs = repo.svfs
                else:
                    raise error.Abort(
                        _(b'invalid location for raw file data: %s')
                        % filemeta[b'location']
                    )

                bytesremaining = filemeta[b'size']

                with vfs.open(filemeta[b'path'], b'wb') as fh:
                    while True:
                        try:
                            chunk = next(objs)
                        except StopIteration:
                            break

                        bytesremaining -= len(chunk)

                        if bytesremaining < 0:
                            raise error.Abort(
                                _(
                                    b'received invalid number of bytes for file '
                                    b'data; expected %d, got extra'
                                )
                                % filemeta[b'size']
                            )

                        progress.increment(step=len(chunk))
                        fh.write(chunk)

                        try:
                            if chunk.islast:
                                break
                        except AttributeError:
                            raise error.Abort(
                                _(
                                    b'did not receive indefinite length bytestring '
                                    b'for file data'
                                )
                            )

                if bytesremaining:
                    raise error.Abort(
                        _(
                            b'received invalid number of bytes for'
                            b'file data; expected %d got %d'
                        )
                        % (
                            filemeta[b'size'],
                            filemeta[b'size'] - bytesremaining,
                        )
                    )


def _pullchangesetdiscovery(repo, remote, heads, abortwhenunrelated=True):
    """Determine which changesets need to be pulled."""

    if heads:
        knownnode = repo.changelog.hasnode
        if all(knownnode(head) for head in heads):
            return heads, False, heads

    # TODO wire protocol version 2 is capable of more efficient discovery
    # than setdiscovery. Consider implementing something better.
    common, fetch, remoteheads = setdiscovery.findcommonheads(
        repo.ui, repo, remote, abortwhenunrelated=abortwhenunrelated
    )

    common = set(common)
    remoteheads = set(remoteheads)

    # If a remote head is filtered locally, put it back in the common set.
    # See the comment in exchange._pulldiscoverychangegroup() for more.

    if fetch and remoteheads:
        has_node = repo.unfiltered().changelog.index.has_node

        common |= {head for head in remoteheads if has_node(head)}

        if set(remoteheads).issubset(common):
            fetch = []

    common.discard(repo.nullid)

    return common, fetch, remoteheads


def _fetchchangesets(repo, tr, remote, common, fetch, remoteheads):
    # TODO consider adding a step here where we obtain the DAG shape first
    # (or ask the server to slice changesets into chunks for us) so that
    # we can perform multiple fetches in batches. This will facilitate
    # resuming interrupted clones, higher server-side cache hit rates due
    # to smaller segments, etc.
    with remote.commandexecutor() as e:
        objs = e.callcommand(
            b'changesetdata',
            {
                b'revisions': [
                    {
                        b'type': b'changesetdagrange',
                        b'roots': sorted(common),
                        b'heads': sorted(remoteheads),
                    }
                ],
                b'fields': {b'bookmarks', b'parents', b'phase', b'revision'},
            },
        ).result()

        # The context manager waits on all response data when exiting. So
        # we need to remain in the context manager in order to stream data.
        return _processchangesetdata(repo, tr, objs)


def _processchangesetdata(repo, tr, objs):
    repo.hook(b'prechangegroup', throw=True, **pycompat.strkwargs(tr.hookargs))

    urepo = repo.unfiltered()
    cl = urepo.changelog

    cl.delayupdate(tr)

    # The first emitted object is a header describing the data that
    # follows.
    meta = next(objs)

    progress = repo.ui.makeprogress(
        _(b'changesets'), unit=_(b'chunks'), total=meta.get(b'totalitems')
    )

    manifestnodes = {}
    added = []

    def linkrev(node):
        repo.ui.debug(b'add changeset %s\n' % short(node))
        # Linkrev for changelog is always self.
        return len(cl)

    def ondupchangeset(cl, rev):
        added.append(cl.node(rev))

    def onchangeset(cl, rev):
        progress.increment()

        revision = cl.changelogrevision(rev)
        added.append(cl.node(rev))

        # We need to preserve the mapping of changelog revision to node
        # so we can set the linkrev accordingly when manifests are added.
        manifestnodes[rev] = revision.manifest

        repo.register_changeset(rev, revision)

    nodesbyphase = {phase: set() for phase in phases.phasenames.values()}
    remotebookmarks = {}

    # addgroup() expects a 7-tuple describing revisions. This normalizes
    # the wire data to that format.
    #
    # This loop also aggregates non-revision metadata, such as phase
    # data.
    def iterrevisions():
        for cset in objs:
            node = cset[b'node']

            if b'phase' in cset:
                nodesbyphase[cset[b'phase']].add(node)

            for mark in cset.get(b'bookmarks', []):
                remotebookmarks[mark] = node

            # TODO add mechanism for extensions to examine records so they
            # can siphon off custom data fields.

            extrafields = {}

            for field, size in cset.get(b'fieldsfollowing', []):
                extrafields[field] = next(objs)

            # Some entries might only be metadata only updates.
            if b'revision' not in extrafields:
                continue

            data = extrafields[b'revision']

            yield (
                node,
                cset[b'parents'][0],
                cset[b'parents'][1],
                # Linknode is always itself for changesets.
                cset[b'node'],
                # We always send full revisions. So delta base is not set.
                repo.nullid,
                mdiff.trivialdiffheader(len(data)) + data,
                # Flags not yet supported.
                0,
                # Sidedata not yet supported
                {},
            )

    cl.addgroup(
        iterrevisions(),
        linkrev,
        weakref.proxy(tr),
        alwayscache=True,
        addrevisioncb=onchangeset,
        duplicaterevisioncb=ondupchangeset,
    )

    progress.complete()

    return {
        b'added': added,
        b'nodesbyphase': nodesbyphase,
        b'bookmarks': remotebookmarks,
        b'manifestnodes': manifestnodes,
    }


def _fetchmanifests(repo, tr, remote, manifestnodes):
    rootmanifest = repo.manifestlog.getstorage(b'')

    # Some manifests can be shared between changesets. Filter out revisions
    # we already know about.
    fetchnodes = []
    linkrevs = {}
    seen = set()

    for clrev, node in sorted(pycompat.iteritems(manifestnodes)):
        if node in seen:
            continue

        try:
            rootmanifest.rev(node)
        except error.LookupError:
            fetchnodes.append(node)
            linkrevs[node] = clrev

        seen.add(node)

    # TODO handle tree manifests

    # addgroup() expects 7-tuple describing revisions. This normalizes
    # the wire data to that format.
    def iterrevisions(objs, progress):
        for manifest in objs:
            node = manifest[b'node']

            extrafields = {}

            for field, size in manifest.get(b'fieldsfollowing', []):
                extrafields[field] = next(objs)

            if b'delta' in extrafields:
                basenode = manifest[b'deltabasenode']
                delta = extrafields[b'delta']
            elif b'revision' in extrafields:
                basenode = repo.nullid
                revision = extrafields[b'revision']
                delta = mdiff.trivialdiffheader(len(revision)) + revision
            else:
                continue

            yield (
                node,
                manifest[b'parents'][0],
                manifest[b'parents'][1],
                # The value passed in is passed to the lookup function passed
                # to addgroup(). We already have a map of manifest node to
                # changelog revision number. So we just pass in the
                # manifest node here and use linkrevs.__getitem__ as the
                # resolution function.
                node,
                basenode,
                delta,
                # Flags not yet supported.
                0,
                # Sidedata not yet supported.
                {},
            )

            progress.increment()

    progress = repo.ui.makeprogress(
        _(b'manifests'), unit=_(b'chunks'), total=len(fetchnodes)
    )

    commandmeta = remote.apidescriptor[b'commands'][b'manifestdata']
    batchsize = commandmeta.get(b'recommendedbatchsize', 10000)
    # TODO make size configurable on client?

    # We send commands 1 at a time to the remote. This is not the most
    # efficient because we incur a round trip at the end of each batch.
    # However, the existing frame-based reactor keeps consuming server
    # data in the background. And this results in response data buffering
    # in memory. This can consume gigabytes of memory.
    # TODO send multiple commands in a request once background buffering
    # issues are resolved.

    added = []

    for i in pycompat.xrange(0, len(fetchnodes), batchsize):
        batch = [node for node in fetchnodes[i : i + batchsize]]
        if not batch:
            continue

        with remote.commandexecutor() as e:
            objs = e.callcommand(
                b'manifestdata',
                {
                    b'tree': b'',
                    b'nodes': batch,
                    b'fields': {b'parents', b'revision'},
                    b'haveparents': True,
                },
            ).result()

            # Chomp off header object.
            next(objs)

            def onchangeset(cl, rev):
                added.append(cl.node(rev))

            rootmanifest.addgroup(
                iterrevisions(objs, progress),
                linkrevs.__getitem__,
                weakref.proxy(tr),
                addrevisioncb=onchangeset,
                duplicaterevisioncb=onchangeset,
            )

    progress.complete()

    return {
        b'added': added,
        b'linkrevs': linkrevs,
    }


def _derivefilesfrommanifests(repo, matcher, manifestnodes):
    """Determine what file nodes are relevant given a set of manifest nodes.

    Returns a dict mapping file paths to dicts of file node to first manifest
    node.
    """
    ml = repo.manifestlog
    fnodes = collections.defaultdict(dict)

    progress = repo.ui.makeprogress(
        _(b'scanning manifests'), total=len(manifestnodes)
    )

    with progress:
        for manifestnode in manifestnodes:
            m = ml.get(b'', manifestnode)

            # TODO this will pull in unwanted nodes because it takes the storage
            # delta into consideration. What we really want is something that
            # takes the delta between the manifest's parents. And ideally we
            # would ignore file nodes that are known locally. For now, ignore
            # both these limitations. This will result in incremental fetches
            # requesting data we already have. So this is far from ideal.
            md = m.readfast()

            for path, fnode in md.items():
                if matcher(path):
                    fnodes[path].setdefault(fnode, manifestnode)

            progress.increment()

    return fnodes


def _fetchfiles(repo, tr, remote, fnodes, linkrevs):
    """Fetch file data from explicit file revisions."""

    def iterrevisions(objs, progress):
        for filerevision in objs:
            node = filerevision[b'node']

            extrafields = {}

            for field, size in filerevision.get(b'fieldsfollowing', []):
                extrafields[field] = next(objs)

            if b'delta' in extrafields:
                basenode = filerevision[b'deltabasenode']
                delta = extrafields[b'delta']
            elif b'revision' in extrafields:
                basenode = repo.nullid
                revision = extrafields[b'revision']
                delta = mdiff.trivialdiffheader(len(revision)) + revision
            else:
                continue

            yield (
                node,
                filerevision[b'parents'][0],
                filerevision[b'parents'][1],
                node,
                basenode,
                delta,
                # Flags not yet supported.
                0,
                # Sidedata not yet supported.
                {},
            )

            progress.increment()

    progress = repo.ui.makeprogress(
        _(b'files'),
        unit=_(b'chunks'),
        total=sum(len(v) for v in pycompat.itervalues(fnodes)),
    )

    # TODO make batch size configurable
    batchsize = 10000
    fnodeslist = [x for x in sorted(fnodes.items())]

    for i in pycompat.xrange(0, len(fnodeslist), batchsize):
        batch = [x for x in fnodeslist[i : i + batchsize]]
        if not batch:
            continue

        with remote.commandexecutor() as e:
            fs = []
            locallinkrevs = {}

            for path, nodes in batch:
                fs.append(
                    (
                        path,
                        e.callcommand(
                            b'filedata',
                            {
                                b'path': path,
                                b'nodes': sorted(nodes),
                                b'fields': {b'parents', b'revision'},
                                b'haveparents': True,
                            },
                        ),
                    )
                )

                locallinkrevs[path] = {
                    node: linkrevs[manifestnode]
                    for node, manifestnode in pycompat.iteritems(nodes)
                }

            for path, f in fs:
                objs = f.result()

                # Chomp off header objects.
                next(objs)

                store = repo.file(path)
                store.addgroup(
                    iterrevisions(objs, progress),
                    locallinkrevs[path].__getitem__,
                    weakref.proxy(tr),
                )


def _fetchfilesfromcsets(
    repo, tr, remote, pathfilter, fnodes, csets, manlinkrevs, shallow=False
):
    """Fetch file data from explicit changeset revisions."""

    def iterrevisions(objs, remaining, progress):
        while remaining:
            filerevision = next(objs)

            node = filerevision[b'node']

            extrafields = {}

            for field, size in filerevision.get(b'fieldsfollowing', []):
                extrafields[field] = next(objs)

            if b'delta' in extrafields:
                basenode = filerevision[b'deltabasenode']
                delta = extrafields[b'delta']
            elif b'revision' in extrafields:
                basenode = repo.nullid
                revision = extrafields[b'revision']
                delta = mdiff.trivialdiffheader(len(revision)) + revision
            else:
                continue

            if b'linknode' in filerevision:
                linknode = filerevision[b'linknode']
            else:
                linknode = node

            yield (
                node,
                filerevision[b'parents'][0],
                filerevision[b'parents'][1],
                linknode,
                basenode,
                delta,
                # Flags not yet supported.
                0,
                # Sidedata not yet supported.
                {},
            )

            progress.increment()
            remaining -= 1

    progress = repo.ui.makeprogress(
        _(b'files'),
        unit=_(b'chunks'),
        total=sum(len(v) for v in pycompat.itervalues(fnodes)),
    )

    commandmeta = remote.apidescriptor[b'commands'][b'filesdata']
    batchsize = commandmeta.get(b'recommendedbatchsize', 50000)

    shallowfiles = repository.REPO_FEATURE_SHALLOW_FILE_STORAGE in repo.features
    fields = {b'parents', b'revision'}
    clrev = repo.changelog.rev

    # There are no guarantees that we'll have ancestor revisions if
    # a) this repo has shallow file storage b) shallow data fetching is enabled.
    # Force remote to not delta against possibly unknown revisions when these
    # conditions hold.
    haveparents = not (shallowfiles or shallow)

    # Similarly, we may not have calculated linkrevs for all incoming file
    # revisions. Ask the remote to do work for us in this case.
    if not haveparents:
        fields.add(b'linknode')

    for i in pycompat.xrange(0, len(csets), batchsize):
        batch = [x for x in csets[i : i + batchsize]]
        if not batch:
            continue

        with remote.commandexecutor() as e:
            args = {
                b'revisions': [
                    {
                        b'type': b'changesetexplicit',
                        b'nodes': batch,
                    }
                ],
                b'fields': fields,
                b'haveparents': haveparents,
            }

            if pathfilter:
                args[b'pathfilter'] = pathfilter

            objs = e.callcommand(b'filesdata', args).result()

            # First object is an overall header.
            overall = next(objs)

            # We have overall['totalpaths'] segments.
            for i in pycompat.xrange(overall[b'totalpaths']):
                header = next(objs)

                path = header[b'path']
                store = repo.file(path)

                linkrevs = {
                    fnode: manlinkrevs[mnode]
                    for fnode, mnode in pycompat.iteritems(fnodes[path])
                }

                def getlinkrev(node):
                    if node in linkrevs:
                        return linkrevs[node]
                    else:
                        return clrev(node)

                store.addgroup(
                    iterrevisions(objs, header[b'totalitems'], progress),
                    getlinkrev,
                    weakref.proxy(tr),
                    maybemissingparents=shallow,
                )
