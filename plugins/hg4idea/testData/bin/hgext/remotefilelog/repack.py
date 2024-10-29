import os
import time

from mercurial.i18n import _
from mercurial.node import short
from mercurial import (
    encoding,
    error,
    lock as lockmod,
    mdiff,
    policy,
    scmutil,
    util,
    vfs,
)
from mercurial.utils import procutil
from . import (
    constants,
    contentstore,
    datapack,
    historypack,
    metadatastore,
    shallowutil,
)

osutil = policy.importmod('osutil')


class RepackAlreadyRunning(error.Abort):
    pass


def backgroundrepack(repo, incremental=True, packsonly=False):
    cmd = [procutil.hgexecutable(), b'-R', repo.origroot, b'repack']
    msg = _(b"(running background repack)\n")
    if incremental:
        cmd.append(b'--incremental')
        msg = _(b"(running background incremental repack)\n")
    if packsonly:
        cmd.append(b'--packsonly')
    repo.ui.warn(msg)
    # We know this command will find a binary, so don't block on it starting.
    kwargs = {}
    if repo.ui.configbool(b'devel', b'remotefilelog.bg-wait'):
        kwargs['record_wait'] = repo.ui.atexit

    procutil.runbgcommand(cmd, encoding.environ, ensurestart=False, **kwargs)


def fullrepack(repo, options=None):
    """If ``packsonly`` is True, stores creating only loose objects are skipped."""
    if hasattr(repo, 'shareddatastores'):
        datasource = contentstore.unioncontentstore(*repo.shareddatastores)
        historysource = metadatastore.unionmetadatastore(
            *repo.sharedhistorystores, allowincomplete=True
        )

        packpath = shallowutil.getcachepackpath(
            repo, constants.FILEPACK_CATEGORY
        )
        _runrepack(
            repo,
            datasource,
            historysource,
            packpath,
            constants.FILEPACK_CATEGORY,
            options=options,
        )

    if hasattr(repo.manifestlog, 'datastore'):
        localdata, shareddata = _getmanifeststores(repo)
        lpackpath, ldstores, lhstores = localdata
        spackpath, sdstores, shstores = shareddata

        # Repack the shared manifest store
        datasource = contentstore.unioncontentstore(*sdstores)
        historysource = metadatastore.unionmetadatastore(
            *shstores, allowincomplete=True
        )
        _runrepack(
            repo,
            datasource,
            historysource,
            spackpath,
            constants.TREEPACK_CATEGORY,
            options=options,
        )

        # Repack the local manifest store
        datasource = contentstore.unioncontentstore(
            *ldstores, allowincomplete=True
        )
        historysource = metadatastore.unionmetadatastore(
            *lhstores, allowincomplete=True
        )
        _runrepack(
            repo,
            datasource,
            historysource,
            lpackpath,
            constants.TREEPACK_CATEGORY,
            options=options,
        )


def incrementalrepack(repo, options=None):
    """This repacks the repo by looking at the distribution of pack files in the
    repo and performing the most minimal repack to keep the repo in good shape.
    """
    if hasattr(repo, 'shareddatastores'):
        packpath = shallowutil.getcachepackpath(
            repo, constants.FILEPACK_CATEGORY
        )
        _incrementalrepack(
            repo,
            repo.shareddatastores,
            repo.sharedhistorystores,
            packpath,
            constants.FILEPACK_CATEGORY,
            options=options,
        )

    if hasattr(repo.manifestlog, 'datastore'):
        localdata, shareddata = _getmanifeststores(repo)
        lpackpath, ldstores, lhstores = localdata
        spackpath, sdstores, shstores = shareddata

        # Repack the shared manifest store
        _incrementalrepack(
            repo,
            sdstores,
            shstores,
            spackpath,
            constants.TREEPACK_CATEGORY,
            options=options,
        )

        # Repack the local manifest store
        _incrementalrepack(
            repo,
            ldstores,
            lhstores,
            lpackpath,
            constants.TREEPACK_CATEGORY,
            allowincompletedata=True,
            options=options,
        )


def _getmanifeststores(repo):
    shareddatastores = repo.manifestlog.shareddatastores
    localdatastores = repo.manifestlog.localdatastores
    sharedhistorystores = repo.manifestlog.sharedhistorystores
    localhistorystores = repo.manifestlog.localhistorystores

    sharedpackpath = shallowutil.getcachepackpath(
        repo, constants.TREEPACK_CATEGORY
    )
    localpackpath = shallowutil.getlocalpackpath(
        repo.svfs.vfs.base, constants.TREEPACK_CATEGORY
    )

    return (
        (localpackpath, localdatastores, localhistorystores),
        (sharedpackpath, shareddatastores, sharedhistorystores),
    )


def _topacks(packpath, files, constructor):
    paths = list(os.path.join(packpath, p) for p in files)
    packs = list(constructor(p) for p in paths)
    return packs


def _deletebigpacks(repo, folder, files):
    """Deletes packfiles that are bigger than ``packs.maxpacksize``.

    Returns ``files` with the removed files omitted."""
    maxsize = repo.ui.configbytes(b"packs", b"maxpacksize")
    if maxsize <= 0:
        return files

    # This only considers datapacks today, but we could broaden it to include
    # historypacks.
    VALIDEXTS = [b".datapack", b".dataidx"]

    # Either an oversize index or datapack will trigger cleanup of the whole
    # pack:
    oversized = {
        os.path.splitext(path)[0]
        for path, ftype, stat in files
        if (stat.st_size > maxsize and (os.path.splitext(path)[1] in VALIDEXTS))
    }

    for rootfname in oversized:
        rootpath = os.path.join(folder, rootfname)
        for ext in VALIDEXTS:
            path = rootpath + ext
            repo.ui.debug(
                b'removing oversize packfile %s (%s)\n'
                % (path, util.bytecount(os.stat(path).st_size))
            )
            os.unlink(path)
    return [row for row in files if os.path.basename(row[0]) not in oversized]


def _incrementalrepack(
    repo,
    datastore,
    historystore,
    packpath,
    category,
    allowincompletedata=False,
    options=None,
):
    shallowutil.mkstickygroupdir(repo.ui, packpath)

    files = osutil.listdir(packpath, stat=True)
    files = _deletebigpacks(repo, packpath, files)
    datapacks = _topacks(
        packpath, _computeincrementaldatapack(repo.ui, files), datapack.datapack
    )
    datapacks.extend(
        s for s in datastore if not isinstance(s, datapack.datapackstore)
    )

    historypacks = _topacks(
        packpath,
        _computeincrementalhistorypack(repo.ui, files),
        historypack.historypack,
    )
    historypacks.extend(
        s
        for s in historystore
        if not isinstance(s, historypack.historypackstore)
    )

    # ``allhistory{files,packs}`` contains all known history packs, even ones we
    # don't plan to repack. They are used during the datapack repack to ensure
    # good ordering of nodes.
    allhistoryfiles = _allpackfileswithsuffix(
        files, historypack.PACKSUFFIX, historypack.INDEXSUFFIX
    )
    allhistorypacks = _topacks(
        packpath,
        (f for f, mode, stat in allhistoryfiles),
        historypack.historypack,
    )
    allhistorypacks.extend(
        s
        for s in historystore
        if not isinstance(s, historypack.historypackstore)
    )
    _runrepack(
        repo,
        contentstore.unioncontentstore(
            *datapacks, allowincomplete=allowincompletedata
        ),
        metadatastore.unionmetadatastore(*historypacks, allowincomplete=True),
        packpath,
        category,
        fullhistory=metadatastore.unionmetadatastore(
            *allhistorypacks, allowincomplete=True
        ),
        options=options,
    )


def _computeincrementaldatapack(ui, files):
    opts = {
        b'gencountlimit': ui.configint(b'remotefilelog', b'data.gencountlimit'),
        b'generations': ui.configlist(b'remotefilelog', b'data.generations'),
        b'maxrepackpacks': ui.configint(
            b'remotefilelog', b'data.maxrepackpacks'
        ),
        b'repackmaxpacksize': ui.configbytes(
            b'remotefilelog', b'data.repackmaxpacksize'
        ),
        b'repacksizelimit': ui.configbytes(
            b'remotefilelog', b'data.repacksizelimit'
        ),
    }

    packfiles = _allpackfileswithsuffix(
        files, datapack.PACKSUFFIX, datapack.INDEXSUFFIX
    )
    return _computeincrementalpack(packfiles, opts)


def _computeincrementalhistorypack(ui, files):
    opts = {
        b'gencountlimit': ui.configint(
            b'remotefilelog', b'history.gencountlimit'
        ),
        b'generations': ui.configlist(
            b'remotefilelog', b'history.generations', [b'100MB']
        ),
        b'maxrepackpacks': ui.configint(
            b'remotefilelog', b'history.maxrepackpacks'
        ),
        b'repackmaxpacksize': ui.configbytes(
            b'remotefilelog', b'history.repackmaxpacksize', b'400MB'
        ),
        b'repacksizelimit': ui.configbytes(
            b'remotefilelog', b'history.repacksizelimit'
        ),
    }

    packfiles = _allpackfileswithsuffix(
        files, historypack.PACKSUFFIX, historypack.INDEXSUFFIX
    )
    return _computeincrementalpack(packfiles, opts)


def _allpackfileswithsuffix(files, packsuffix, indexsuffix):
    result = []
    fileset = {fn for fn, mode, stat in files}
    for filename, mode, stat in files:
        if not filename.endswith(packsuffix):
            continue

        prefix = filename[: -len(packsuffix)]

        # Don't process a pack if it doesn't have an index.
        if (prefix + indexsuffix) not in fileset:
            continue
        result.append((prefix, mode, stat))

    return result


def _computeincrementalpack(files, opts):
    """Given a set of pack files along with the configuration options, this
    function computes the list of files that should be packed as part of an
    incremental repack.

    It tries to strike a balance between keeping incremental repacks cheap (i.e.
    packing small things when possible, and rolling the packs up to the big ones
    over time).
    """

    limits = list(
        sorted((util.sizetoint(s) for s in opts[b'generations']), reverse=True)
    )
    limits.append(0)

    # Group the packs by generation (i.e. by size)
    generations = []
    for i in range(len(limits)):
        generations.append([])

    sizes = {}
    for prefix, mode, stat in files:
        size = stat.st_size
        if size > opts[b'repackmaxpacksize']:
            continue

        sizes[prefix] = size
        for i, limit in enumerate(limits):
            if size > limit:
                generations[i].append(prefix)
                break

    # Steps for picking what packs to repack:
    # 1. Pick the largest generation with > gencountlimit pack files.
    # 2. Take the smallest three packs.
    # 3. While total-size-of-packs < repacksizelimit: add another pack

    # Find the largest generation with more than gencountlimit packs
    genpacks = []
    for i, limit in enumerate(limits):
        if len(generations[i]) > opts[b'gencountlimit']:
            # Sort to be smallest last, for easy popping later
            genpacks.extend(
                sorted(generations[i], reverse=True, key=lambda x: sizes[x])
            )
            break

    # Take as many packs from the generation as we can
    chosenpacks = genpacks[-3:]
    genpacks = genpacks[:-3]
    repacksize = sum(sizes[n] for n in chosenpacks)
    while (
        repacksize < opts[b'repacksizelimit']
        and genpacks
        and len(chosenpacks) < opts[b'maxrepackpacks']
    ):
        chosenpacks.append(genpacks.pop())
        repacksize += sizes[chosenpacks[-1]]

    return chosenpacks


def _runrepack(
    repo, data, history, packpath, category, fullhistory=None, options=None
):
    shallowutil.mkstickygroupdir(repo.ui, packpath)

    def isold(repo, filename, node):
        """Check if the file node is older than a limit.
        Unless a limit is specified in the config the default limit is taken.
        """
        filectx = repo.filectx(filename, fileid=node)
        filetime = repo[filectx.linkrev()].date()

        ttl = repo.ui.configint(b'remotefilelog', b'nodettl')

        limit = time.time() - ttl
        return filetime[0] < limit

    garbagecollect = repo.ui.configbool(b'remotefilelog', b'gcrepack')
    if not fullhistory:
        fullhistory = history
    packer = repacker(
        repo,
        data,
        history,
        fullhistory,
        category,
        gc=garbagecollect,
        isold=isold,
        options=options,
    )

    with datapack.mutabledatapack(repo.ui, packpath) as dpack:
        with historypack.mutablehistorypack(repo.ui, packpath) as hpack:
            try:
                packer.run(dpack, hpack)
            except error.LockHeld:
                raise RepackAlreadyRunning(
                    _(
                        b"skipping repack - another repack "
                        b"is already running"
                    )
                )


def keepset(repo, keyfn, lastkeepkeys=None):
    """Computes a keepset which is not garbage collected.
    'keyfn' is a function that maps filename, node to a unique key.
    'lastkeepkeys' is an optional argument and if provided the keepset
    function updates lastkeepkeys with more keys and returns the result.
    """
    if not lastkeepkeys:
        keepkeys = set()
    else:
        keepkeys = lastkeepkeys

    # We want to keep:
    # 1. Working copy parent
    # 2. Draft commits
    # 3. Parents of draft commits
    # 4. Pullprefetch and bgprefetchrevs revsets if specified
    revs = [b'.', b'draft()', b'parents(draft())']
    prefetchrevs = repo.ui.config(b'remotefilelog', b'pullprefetch', None)
    if prefetchrevs:
        revs.append(b'(%s)' % prefetchrevs)
    prefetchrevs = repo.ui.config(b'remotefilelog', b'bgprefetchrevs', None)
    if prefetchrevs:
        revs.append(b'(%s)' % prefetchrevs)
    revs = b'+'.join(revs)

    revs = [b'sort((%s), "topo")' % revs]
    keep = scmutil.revrange(repo, revs)

    processed = set()
    lastmanifest = None

    # process the commits in toposorted order starting from the oldest
    for r in reversed(keep._list):
        if repo[r].p1().rev() in processed:
            # if the direct parent has already been processed
            # then we only need to process the delta
            m = repo[r].manifestctx().readdelta()
        else:
            # otherwise take the manifest and diff it
            # with the previous manifest if one exists
            if lastmanifest:
                m = repo[r].manifest().diff(lastmanifest)
            else:
                m = repo[r].manifest()
        lastmanifest = repo[r].manifest()
        processed.add(r)

        # populate keepkeys with keys from the current manifest
        if type(m) is dict:
            # m is a result of diff of two manifests and is a dictionary that
            # maps filename to ((newnode, newflag), (oldnode, oldflag)) tuple
            for filename, diff in m.items():
                if diff[0][0] is not None:
                    keepkeys.add(keyfn(filename, diff[0][0]))
        else:
            # m is a manifest object
            for filename, filenode in m.items():
                keepkeys.add(keyfn(filename, filenode))

    return keepkeys


class repacker:
    """Class for orchestrating the repack of data and history information into a
    new format.
    """

    def __init__(
        self,
        repo,
        data,
        history,
        fullhistory,
        category,
        gc=False,
        isold=None,
        options=None,
    ):
        self.repo = repo
        self.data = data
        self.history = history
        self.fullhistory = fullhistory
        self.unit = constants.getunits(category)
        self.garbagecollect = gc
        self.options = options
        if self.garbagecollect:
            if not isold:
                raise ValueError(b"Function 'isold' is not properly specified")
            # use (filename, node) tuple as a keepset key
            self.keepkeys = keepset(repo, lambda f, n: (f, n))
            self.isold = isold

    def run(self, targetdata, targethistory):
        ledger = repackledger()

        with lockmod.lock(
            repacklockvfs(self.repo), b"repacklock", desc=None, timeout=0
        ):
            self.repo.hook(b'prerepack')

            # Populate ledger from source
            self.data.markledger(ledger, options=self.options)
            self.history.markledger(ledger, options=self.options)

            # Run repack
            self.repackdata(ledger, targetdata)
            self.repackhistory(ledger, targethistory)

            # Call cleanup on each source
            for source in ledger.sources:
                source.cleanup(ledger)

    def _chainorphans(self, ui, filename, nodes, orphans, deltabases):
        """Reorderes ``orphans`` into a single chain inside ``nodes`` and
        ``deltabases``.

        We often have orphan entries (nodes without a base that aren't
        referenced by other nodes -- i.e., part of a chain) due to gaps in
        history. Rather than store them as individual fulltexts, we prefer to
        insert them as one chain sorted by size.
        """
        if not orphans:
            return nodes

        def getsize(node, default=0):
            meta = self.data.getmeta(filename, node)
            if constants.METAKEYSIZE in meta:
                return meta[constants.METAKEYSIZE]
            else:
                return default

        # Sort orphans by size; biggest first is preferred, since it's more
        # likely to be the newest version assuming files grow over time.
        # (Sort by node first to ensure the sort is stable.)
        orphans = sorted(orphans)
        orphans = list(sorted(orphans, key=getsize, reverse=True))
        if ui.debugflag:
            ui.debug(
                b"%s: orphan chain: %s\n"
                % (filename, b", ".join([short(s) for s in orphans]))
            )

        # Create one contiguous chain and reassign deltabases.
        for i, node in enumerate(orphans):
            if i == 0:
                deltabases[node] = (self.repo.nullid, 0)
            else:
                parent = orphans[i - 1]
                deltabases[node] = (parent, deltabases[parent][1] + 1)
        nodes = [n for n in nodes if n not in orphans]
        nodes += orphans
        return nodes

    def repackdata(self, ledger, target):
        ui = self.repo.ui
        maxchainlen = ui.configint(b'packs', b'maxchainlen', 1000)

        byfile = {}
        for entry in ledger.entries.values():
            if entry.datasource:
                byfile.setdefault(entry.filename, {})[entry.node] = entry

        count = 0
        repackprogress = ui.makeprogress(
            _(b"repacking data"), unit=self.unit, total=len(byfile)
        )
        for filename, entries in sorted(byfile.items()):
            repackprogress.update(count)

            ancestors = {}
            nodes = list(node for node in entries)
            nohistory = []
            buildprogress = ui.makeprogress(
                _(b"building history"), unit=b'nodes', total=len(nodes)
            )
            for i, node in enumerate(nodes):
                if node in ancestors:
                    continue
                buildprogress.update(i)
                try:
                    ancestors.update(
                        self.fullhistory.getancestors(
                            filename, node, known=ancestors
                        )
                    )
                except KeyError:
                    # Since we're packing data entries, we may not have the
                    # corresponding history entries for them. It's not a big
                    # deal, but the entries won't be delta'd perfectly.
                    nohistory.append(node)
            buildprogress.complete()

            # Order the nodes children first, so we can produce reverse deltas
            orderednodes = list(reversed(self._toposort(ancestors)))
            if len(nohistory) > 0:
                ui.debug(
                    b'repackdata: %d nodes without history\n' % len(nohistory)
                )
            orderednodes.extend(sorted(nohistory))

            # Filter orderednodes to just the nodes we want to serialize (it
            # currently also has the edge nodes' ancestors).
            orderednodes = list(
                filter(lambda node: node in nodes, orderednodes)
            )

            # Garbage collect old nodes:
            if self.garbagecollect:
                neworderednodes = []
                for node in orderednodes:
                    # If the node is old and is not in the keepset, we skip it,
                    # and mark as garbage collected
                    if (filename, node) not in self.keepkeys and self.isold(
                        self.repo, filename, node
                    ):
                        entries[node].gced = True
                        continue
                    neworderednodes.append(node)
                orderednodes = neworderednodes

            # Compute delta bases for nodes:
            deltabases = {}
            nobase = set()
            referenced = set()
            nodes = set(nodes)
            processprogress = ui.makeprogress(
                _(b"processing nodes"), unit=b'nodes', total=len(orderednodes)
            )
            for i, node in enumerate(orderednodes):
                processprogress.update(i)
                # Find delta base
                # TODO: allow delta'ing against most recent descendant instead
                # of immediate child
                deltatuple = deltabases.get(node, None)
                if deltatuple is None:
                    deltabase, chainlen = self.repo.nullid, 0
                    deltabases[node] = (self.repo.nullid, 0)
                    nobase.add(node)
                else:
                    deltabase, chainlen = deltatuple
                    referenced.add(deltabase)

                # Use available ancestor information to inform our delta choices
                ancestorinfo = ancestors.get(node)
                if ancestorinfo:
                    p1, p2, linknode, copyfrom = ancestorinfo

                    # The presence of copyfrom means we're at a point where the
                    # file was copied from elsewhere. So don't attempt to do any
                    # deltas with the other file.
                    if copyfrom:
                        p1 = self.repo.nullid

                    if chainlen < maxchainlen:
                        # Record this child as the delta base for its parents.
                        # This may be non optimal, since the parents may have
                        # many children, and this will only choose the last one.
                        # TODO: record all children and try all deltas to find
                        # best
                        if p1 != self.repo.nullid:
                            deltabases[p1] = (node, chainlen + 1)
                        if p2 != self.repo.nullid:
                            deltabases[p2] = (node, chainlen + 1)

            # experimental config: repack.chainorphansbysize
            if ui.configbool(b'repack', b'chainorphansbysize'):
                orphans = nobase - referenced
                orderednodes = self._chainorphans(
                    ui, filename, orderednodes, orphans, deltabases
                )

            # Compute deltas and write to the pack
            for i, node in enumerate(orderednodes):
                deltabase, chainlen = deltabases[node]
                # Compute delta
                # TODO: Optimize the deltachain fetching. Since we're
                # iterating over the different version of the file, we may
                # be fetching the same deltachain over and over again.
                if deltabase != self.repo.nullid:
                    deltaentry = self.data.getdelta(filename, node)
                    delta, deltabasename, origdeltabase, meta = deltaentry
                    size = meta.get(constants.METAKEYSIZE)
                    if (
                        deltabasename != filename
                        or origdeltabase != deltabase
                        or size is None
                    ):
                        deltabasetext = self.data.get(filename, deltabase)
                        original = self.data.get(filename, node)
                        size = len(original)
                        delta = mdiff.textdiff(deltabasetext, original)
                else:
                    delta = self.data.get(filename, node)
                    size = len(delta)
                    meta = self.data.getmeta(filename, node)

                # TODO: don't use the delta if it's larger than the fulltext
                if constants.METAKEYSIZE not in meta:
                    meta[constants.METAKEYSIZE] = size
                target.add(filename, node, deltabase, delta, meta)

                entries[node].datarepacked = True

            processprogress.complete()
            count += 1

        repackprogress.complete()
        target.close(ledger=ledger)

    def repackhistory(self, ledger, target):
        ui = self.repo.ui

        byfile = {}
        for entry in ledger.entries.values():
            if entry.historysource:
                byfile.setdefault(entry.filename, {})[entry.node] = entry

        progress = ui.makeprogress(
            _(b"repacking history"), unit=self.unit, total=len(byfile)
        )
        for filename, entries in sorted(byfile.items()):
            ancestors = {}
            nodes = list(node for node in entries)

            for node in nodes:
                if node in ancestors:
                    continue
                ancestors.update(
                    self.history.getancestors(filename, node, known=ancestors)
                )

            # Order the nodes children first
            orderednodes = reversed(self._toposort(ancestors))

            # Write to the pack
            dontprocess = set()
            for node in orderednodes:
                p1, p2, linknode, copyfrom = ancestors[node]

                # If the node is marked dontprocess, but it's also in the
                # explicit entries set, that means the node exists both in this
                # file and in another file that was copied to this file.
                # Usually this happens if the file was copied to another file,
                # then the copy was deleted, then reintroduced without copy
                # metadata. The original add and the new add have the same hash
                # since the content is identical and the parents are null.
                if node in dontprocess and node not in entries:
                    # If copyfrom == filename, it means the copy history
                    # went to come other file, then came back to this one, so we
                    # should continue processing it.
                    if p1 != self.repo.nullid and copyfrom != filename:
                        dontprocess.add(p1)
                    if p2 != self.repo.nullid:
                        dontprocess.add(p2)
                    continue

                if copyfrom:
                    dontprocess.add(p1)

                target.add(filename, node, p1, p2, linknode, copyfrom)

                if node in entries:
                    entries[node].historyrepacked = True

            progress.increment()

        progress.complete()
        target.close(ledger=ledger)

    def _toposort(self, ancestors):
        def parentfunc(node):
            p1, p2, linknode, copyfrom = ancestors[node]
            parents = []
            if p1 != self.repo.nullid:
                parents.append(p1)
            if p2 != self.repo.nullid:
                parents.append(p2)
            return parents

        sortednodes = shallowutil.sortnodes(ancestors.keys(), parentfunc)
        return sortednodes


class repackledger:
    """Storage for all the bookkeeping that happens during a repack. It contains
    the list of revisions being repacked, what happened to each revision, and
    which source store contained which revision originally (for later cleanup).
    """

    def __init__(self):
        self.entries = {}
        self.sources = {}
        self.created = set()

    def markdataentry(self, source, filename, node):
        """Mark the given filename+node revision as having a data rev in the
        given source.
        """
        entry = self._getorcreateentry(filename, node)
        entry.datasource = True
        entries = self.sources.get(source)
        if not entries:
            entries = set()
            self.sources[source] = entries
        entries.add(entry)

    def markhistoryentry(self, source, filename, node):
        """Mark the given filename+node revision as having a history rev in the
        given source.
        """
        entry = self._getorcreateentry(filename, node)
        entry.historysource = True
        entries = self.sources.get(source)
        if not entries:
            entries = set()
            self.sources[source] = entries
        entries.add(entry)

    def _getorcreateentry(self, filename, node):
        key = (filename, node)
        value = self.entries.get(key)
        if not value:
            value = repackentry(filename, node)
            self.entries[key] = value

        return value

    def addcreated(self, value):
        self.created.add(value)


class repackentry:
    """Simple class representing a single revision entry in the repackledger."""

    __slots__ = (
        'filename',
        'node',
        'datasource',
        'historysource',
        'datarepacked',
        'historyrepacked',
        'gced',
    )

    def __init__(self, filename, node):
        self.filename = filename
        self.node = node
        # If the revision has a data entry in the source
        self.datasource = False
        # If the revision has a history entry in the source
        self.historysource = False
        # If the revision's data entry was repacked into the repack target
        self.datarepacked = False
        # If the revision's history entry was repacked into the repack target
        self.historyrepacked = False
        # If garbage collected
        self.gced = False


def repacklockvfs(repo):
    if hasattr(repo, 'name'):
        # Lock in the shared cache so repacks across multiple copies of the same
        # repo are coordinated.
        sharedcachepath = shallowutil.getcachepackpath(
            repo, constants.FILEPACK_CATEGORY
        )
        return vfs.vfs(sharedcachepath)
    else:
        return repo.svfs
