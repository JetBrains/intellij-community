# changegroup.py - Mercurial changegroup manipulation functions
#
#  Copyright 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import os
import struct
import weakref

from .i18n import _
from .node import (
    hex,
    nullrev,
    short,
)
from .pycompat import open

from . import (
    error,
    match as matchmod,
    mdiff,
    phases,
    pycompat,
    requirements,
    scmutil,
    util,
)

from .interfaces import repository
from .revlogutils import sidedata as sidedatamod
from .revlogutils import constants as revlog_constants
from .utils import storageutil

_CHANGEGROUPV1_DELTA_HEADER = struct.Struct(b"20s20s20s20s")
_CHANGEGROUPV2_DELTA_HEADER = struct.Struct(b"20s20s20s20s20s")
_CHANGEGROUPV3_DELTA_HEADER = struct.Struct(b">20s20s20s20s20sH")
_CHANGEGROUPV4_DELTA_HEADER = struct.Struct(b">B20s20s20s20s20sH")

LFS_REQUIREMENT = b'lfs'

readexactly = util.readexactly


def getchunk(stream):
    """return the next chunk from stream as a string"""
    d = readexactly(stream, 4)
    l = struct.unpack(b">l", d)[0]
    if l <= 4:
        if l:
            raise error.Abort(_(b"invalid chunk length %d") % l)
        return b""
    return readexactly(stream, l - 4)


def chunkheader(length):
    """return a changegroup chunk header (string)"""
    return struct.pack(b">l", length + 4)


def closechunk():
    """return a changegroup chunk header (string) for a zero-length chunk"""
    return struct.pack(b">l", 0)


def _fileheader(path):
    """Obtain a changegroup chunk header for a named path."""
    return chunkheader(len(path)) + path


def writechunks(ui, chunks, filename, vfs=None):
    """Write chunks to a file and return its filename.

    The stream is assumed to be a bundle file.
    Existing files will not be overwritten.
    If no filename is specified, a temporary file is created.
    """
    fh = None
    cleanup = None
    try:
        if filename:
            if vfs:
                fh = vfs.open(filename, b"wb")
            else:
                # Increase default buffer size because default is usually
                # small (4k is common on Linux).
                fh = open(filename, b"wb", 131072)
        else:
            fd, filename = pycompat.mkstemp(prefix=b"hg-bundle-", suffix=b".hg")
            fh = os.fdopen(fd, "wb")
        cleanup = filename
        for c in chunks:
            fh.write(c)
        cleanup = None
        return filename
    finally:
        if fh is not None:
            fh.close()
        if cleanup is not None:
            if filename and vfs:
                vfs.unlink(cleanup)
            else:
                os.unlink(cleanup)


class cg1unpacker(object):
    """Unpacker for cg1 changegroup streams.

    A changegroup unpacker handles the framing of the revision data in
    the wire format. Most consumers will want to use the apply()
    method to add the changes from the changegroup to a repository.

    If you're forwarding a changegroup unmodified to another consumer,
    use getchunks(), which returns an iterator of changegroup
    chunks. This is mostly useful for cases where you need to know the
    data stream has ended by observing the end of the changegroup.

    deltachunk() is useful only if you're applying delta data. Most
    consumers should prefer apply() instead.

    A few other public methods exist. Those are used only for
    bundlerepo and some debug commands - their use is discouraged.
    """

    deltaheader = _CHANGEGROUPV1_DELTA_HEADER
    deltaheadersize = deltaheader.size
    version = b'01'
    _grouplistcount = 1  # One list of files after the manifests

    def __init__(self, fh, alg, extras=None):
        if alg is None:
            alg = b'UN'
        if alg not in util.compengines.supportedbundletypes:
            raise error.Abort(_(b'unknown stream compression type: %s') % alg)
        if alg == b'BZ':
            alg = b'_truncatedBZ'

        compengine = util.compengines.forbundletype(alg)
        self._stream = compengine.decompressorreader(fh)
        self._type = alg
        self.extras = extras or {}
        self.callback = None

    # These methods (compressed, read, seek, tell) all appear to only
    # be used by bundlerepo, but it's a little hard to tell.
    def compressed(self):
        return self._type is not None and self._type != b'UN'

    def read(self, l):
        return self._stream.read(l)

    def seek(self, pos):
        return self._stream.seek(pos)

    def tell(self):
        return self._stream.tell()

    def close(self):
        return self._stream.close()

    def _chunklength(self):
        d = readexactly(self._stream, 4)
        l = struct.unpack(b">l", d)[0]
        if l <= 4:
            if l:
                raise error.Abort(_(b"invalid chunk length %d") % l)
            return 0
        if self.callback:
            self.callback()
        return l - 4

    def changelogheader(self):
        """v10 does not have a changelog header chunk"""
        return {}

    def manifestheader(self):
        """v10 does not have a manifest header chunk"""
        return {}

    def filelogheader(self):
        """return the header of the filelogs chunk, v10 only has the filename"""
        l = self._chunklength()
        if not l:
            return {}
        fname = readexactly(self._stream, l)
        return {b'filename': fname}

    def _deltaheader(self, headertuple, prevnode):
        node, p1, p2, cs = headertuple
        if prevnode is None:
            deltabase = p1
        else:
            deltabase = prevnode
        flags = 0
        protocol_flags = 0
        return node, p1, p2, deltabase, cs, flags, protocol_flags

    def deltachunk(self, prevnode):
        # Chunkdata: (node, p1, p2, cs, deltabase, delta, flags, sidedata, proto_flags)
        l = self._chunklength()
        if not l:
            return {}
        headerdata = readexactly(self._stream, self.deltaheadersize)
        header = self.deltaheader.unpack(headerdata)
        delta = readexactly(self._stream, l - self.deltaheadersize)
        header = self._deltaheader(header, prevnode)
        node, p1, p2, deltabase, cs, flags, protocol_flags = header
        return node, p1, p2, cs, deltabase, delta, flags, {}, protocol_flags

    def getchunks(self):
        """returns all the chunks contains in the bundle

        Used when you need to forward the binary stream to a file or another
        network API. To do so, it parse the changegroup data, otherwise it will
        block in case of sshrepo because it don't know the end of the stream.
        """
        # For changegroup 1 and 2, we expect 3 parts: changelog, manifestlog,
        # and a list of filelogs. For changegroup 3, we expect 4 parts:
        # changelog, manifestlog, a list of tree manifestlogs, and a list of
        # filelogs.
        #
        # Changelog and manifestlog parts are terminated with empty chunks. The
        # tree and file parts are a list of entry sections. Each entry section
        # is a series of chunks terminating in an empty chunk. The list of these
        # entry sections is terminated in yet another empty chunk, so we know
        # we've reached the end of the tree/file list when we reach an empty
        # chunk that was proceeded by no non-empty chunks.

        parts = 0
        while parts < 2 + self._grouplistcount:
            noentries = True
            while True:
                chunk = getchunk(self)
                if not chunk:
                    # The first two empty chunks represent the end of the
                    # changelog and the manifestlog portions. The remaining
                    # empty chunks represent either A) the end of individual
                    # tree or file entries in the file list, or B) the end of
                    # the entire list. It's the end of the entire list if there
                    # were no entries (i.e. noentries is True).
                    if parts < 2:
                        parts += 1
                    elif noentries:
                        parts += 1
                    break
                noentries = False
                yield chunkheader(len(chunk))
                pos = 0
                while pos < len(chunk):
                    next = pos + 2 ** 20
                    yield chunk[pos:next]
                    pos = next
            yield closechunk()

    def _unpackmanifests(self, repo, revmap, trp, prog, addrevisioncb=None):
        self.callback = prog.increment
        # no need to check for empty manifest group here:
        # if the result of the merge of 1 and 2 is the same in 3 and 4,
        # no new manifest will be created and the manifest group will
        # be empty during the pull
        self.manifestheader()
        deltas = self.deltaiter()
        storage = repo.manifestlog.getstorage(b'')
        storage.addgroup(deltas, revmap, trp, addrevisioncb=addrevisioncb)
        prog.complete()
        self.callback = None

    def apply(
        self,
        repo,
        tr,
        srctype,
        url,
        targetphase=phases.draft,
        expectedtotal=None,
        sidedata_categories=None,
    ):
        """Add the changegroup returned by source.read() to this repo.
        srctype is a string like 'push', 'pull', or 'unbundle'.  url is
        the URL of the repo where this changegroup is coming from.

        Return an integer summarizing the change to this repo:
        - nothing changed or no source: 0
        - more heads than before: 1+added heads (2..n)
        - fewer heads than before: -1-removed heads (-2..-n)
        - number of heads stays the same: 1

        `sidedata_categories` is an optional set of the remote's sidedata wanted
        categories.
        """
        repo = repo.unfiltered()

        # Only useful if we're adding sidedata categories. If both peers have
        # the same categories, then we simply don't do anything.
        adding_sidedata = (
            (
                requirements.REVLOGV2_REQUIREMENT in repo.requirements
                or requirements.CHANGELOGV2_REQUIREMENT in repo.requirements
            )
            and self.version == b'04'
            and srctype == b'pull'
        )
        if adding_sidedata:
            sidedata_helpers = sidedatamod.get_sidedata_helpers(
                repo,
                sidedata_categories or set(),
                pull=True,
            )
        else:
            sidedata_helpers = None

        def csmap(x):
            repo.ui.debug(b"add changeset %s\n" % short(x))
            return len(cl)

        def revmap(x):
            return cl.rev(x)

        try:
            # The transaction may already carry source information. In this
            # case we use the top level data. We overwrite the argument
            # because we need to use the top level value (if they exist)
            # in this function.
            srctype = tr.hookargs.setdefault(b'source', srctype)
            tr.hookargs.setdefault(b'url', url)
            repo.hook(
                b'prechangegroup', throw=True, **pycompat.strkwargs(tr.hookargs)
            )

            # write changelog data to temp files so concurrent readers
            # will not see an inconsistent view
            cl = repo.changelog
            cl.delayupdate(tr)
            oldheads = set(cl.heads())

            trp = weakref.proxy(tr)
            # pull off the changeset group
            repo.ui.status(_(b"adding changesets\n"))
            clstart = len(cl)
            progress = repo.ui.makeprogress(
                _(b'changesets'), unit=_(b'chunks'), total=expectedtotal
            )
            self.callback = progress.increment

            efilesset = set()
            duprevs = []

            def ondupchangelog(cl, rev):
                if rev < clstart:
                    duprevs.append(rev)

            def onchangelog(cl, rev):
                ctx = cl.changelogrevision(rev)
                efilesset.update(ctx.files)
                repo.register_changeset(rev, ctx)

            self.changelogheader()
            deltas = self.deltaiter()
            if not cl.addgroup(
                deltas,
                csmap,
                trp,
                alwayscache=True,
                addrevisioncb=onchangelog,
                duplicaterevisioncb=ondupchangelog,
            ):
                repo.ui.develwarn(
                    b'applied empty changelog from changegroup',
                    config=b'warn-empty-changegroup',
                )
            efiles = len(efilesset)
            clend = len(cl)
            changesets = clend - clstart
            progress.complete()
            del deltas
            # TODO Python 2.7 removal
            # del efilesset
            efilesset = None
            self.callback = None

            # Keep track of the (non-changelog) revlogs we've updated and their
            # range of new revisions for sidedata rewrite.
            # TODO do something more efficient than keeping the reference to
            # the revlogs, especially memory-wise.
            touched_manifests = {}
            touched_filelogs = {}

            # pull off the manifest group
            repo.ui.status(_(b"adding manifests\n"))
            # We know that we'll never have more manifests than we had
            # changesets.
            progress = repo.ui.makeprogress(
                _(b'manifests'), unit=_(b'chunks'), total=changesets
            )
            on_manifest_rev = None
            if sidedata_helpers:
                if revlog_constants.KIND_MANIFESTLOG in sidedata_helpers[1]:

                    def on_manifest_rev(manifest, rev):
                        range = touched_manifests.get(manifest)
                        if not range:
                            touched_manifests[manifest] = (rev, rev)
                        else:
                            assert rev == range[1] + 1
                            touched_manifests[manifest] = (range[0], rev)

            self._unpackmanifests(
                repo,
                revmap,
                trp,
                progress,
                addrevisioncb=on_manifest_rev,
            )

            needfiles = {}
            if repo.ui.configbool(b'server', b'validate'):
                cl = repo.changelog
                ml = repo.manifestlog
                # validate incoming csets have their manifests
                for cset in pycompat.xrange(clstart, clend):
                    mfnode = cl.changelogrevision(cset).manifest
                    mfest = ml[mfnode].readdelta()
                    # store file nodes we must see
                    for f, n in pycompat.iteritems(mfest):
                        needfiles.setdefault(f, set()).add(n)

            on_filelog_rev = None
            if sidedata_helpers:
                if revlog_constants.KIND_FILELOG in sidedata_helpers[1]:

                    def on_filelog_rev(filelog, rev):
                        range = touched_filelogs.get(filelog)
                        if not range:
                            touched_filelogs[filelog] = (rev, rev)
                        else:
                            assert rev == range[1] + 1
                            touched_filelogs[filelog] = (range[0], rev)

            # process the files
            repo.ui.status(_(b"adding file changes\n"))
            newrevs, newfiles = _addchangegroupfiles(
                repo,
                self,
                revmap,
                trp,
                efiles,
                needfiles,
                addrevisioncb=on_filelog_rev,
            )

            if sidedata_helpers:
                if revlog_constants.KIND_CHANGELOG in sidedata_helpers[1]:
                    cl.rewrite_sidedata(
                        trp, sidedata_helpers, clstart, clend - 1
                    )
                for mf, (startrev, endrev) in touched_manifests.items():
                    mf.rewrite_sidedata(trp, sidedata_helpers, startrev, endrev)
                for fl, (startrev, endrev) in touched_filelogs.items():
                    fl.rewrite_sidedata(trp, sidedata_helpers, startrev, endrev)

            # making sure the value exists
            tr.changes.setdefault(b'changegroup-count-changesets', 0)
            tr.changes.setdefault(b'changegroup-count-revisions', 0)
            tr.changes.setdefault(b'changegroup-count-files', 0)
            tr.changes.setdefault(b'changegroup-count-heads', 0)

            # some code use bundle operation for internal purpose. They usually
            # set `ui.quiet` to do this outside of user sight. Size the report
            # of such operation now happens at the end of the transaction, that
            # ui.quiet has not direct effect on the output.
            #
            # To preserve this intend use an inelegant hack, we fail to report
            # the change if `quiet` is set. We should probably move to
            # something better, but this is a good first step to allow the "end
            # of transaction report" to pass tests.
            if not repo.ui.quiet:
                tr.changes[b'changegroup-count-changesets'] += changesets
                tr.changes[b'changegroup-count-revisions'] += newrevs
                tr.changes[b'changegroup-count-files'] += newfiles

            deltaheads = 0
            if oldheads:
                heads = cl.heads()
                deltaheads += len(heads) - len(oldheads)
                for h in heads:
                    if h not in oldheads and repo[h].closesbranch():
                        deltaheads -= 1

            # see previous comment about checking ui.quiet
            if not repo.ui.quiet:
                tr.changes[b'changegroup-count-heads'] += deltaheads
            repo.invalidatevolatilesets()

            if changesets > 0:
                if b'node' not in tr.hookargs:
                    tr.hookargs[b'node'] = hex(cl.node(clstart))
                    tr.hookargs[b'node_last'] = hex(cl.node(clend - 1))
                    hookargs = dict(tr.hookargs)
                else:
                    hookargs = dict(tr.hookargs)
                    hookargs[b'node'] = hex(cl.node(clstart))
                    hookargs[b'node_last'] = hex(cl.node(clend - 1))
                repo.hook(
                    b'pretxnchangegroup',
                    throw=True,
                    **pycompat.strkwargs(hookargs)
                )

            added = pycompat.xrange(clstart, clend)
            phaseall = None
            if srctype in (b'push', b'serve'):
                # Old servers can not push the boundary themselves.
                # New servers won't push the boundary if changeset already
                # exists locally as secret
                #
                # We should not use added here but the list of all change in
                # the bundle
                if repo.publishing():
                    targetphase = phaseall = phases.public
                else:
                    # closer target phase computation

                    # Those changesets have been pushed from the
                    # outside, their phases are going to be pushed
                    # alongside. Therefor `targetphase` is
                    # ignored.
                    targetphase = phaseall = phases.draft
            if added:
                phases.registernew(repo, tr, targetphase, added)
            if phaseall is not None:
                if duprevs:
                    duprevs.extend(added)
                else:
                    duprevs = added
                phases.advanceboundary(repo, tr, phaseall, [], revs=duprevs)
                duprevs = []

            if changesets > 0:

                def runhooks(unused_success):
                    # These hooks run when the lock releases, not when the
                    # transaction closes. So it's possible for the changelog
                    # to have changed since we last saw it.
                    if clstart >= len(repo):
                        return

                    repo.hook(b"changegroup", **pycompat.strkwargs(hookargs))

                    for rev in added:
                        args = hookargs.copy()
                        args[b'node'] = hex(cl.node(rev))
                        del args[b'node_last']
                        repo.hook(b"incoming", **pycompat.strkwargs(args))

                    newheads = [h for h in repo.heads() if h not in oldheads]
                    repo.ui.log(
                        b"incoming",
                        b"%d incoming changes - new heads: %s\n",
                        len(added),
                        b', '.join([hex(c[:6]) for c in newheads]),
                    )

                tr.addpostclose(
                    b'changegroup-runhooks-%020i' % clstart,
                    lambda tr: repo._afterlock(runhooks),
                )
        finally:
            repo.ui.flush()
        # never return 0 here:
        if deltaheads < 0:
            ret = deltaheads - 1
        else:
            ret = deltaheads + 1
        return ret

    def deltaiter(self):
        """
        returns an iterator of the deltas in this changegroup

        Useful for passing to the underlying storage system to be stored.
        """
        chain = None
        for chunkdata in iter(lambda: self.deltachunk(chain), {}):
            # Chunkdata: (node, p1, p2, cs, deltabase, delta, flags, sidedata, proto_flags)
            yield chunkdata[:8]
            chain = chunkdata[0]


class cg2unpacker(cg1unpacker):
    """Unpacker for cg2 streams.

    cg2 streams add support for generaldelta, so the delta header
    format is slightly different. All other features about the data
    remain the same.
    """

    deltaheader = _CHANGEGROUPV2_DELTA_HEADER
    deltaheadersize = deltaheader.size
    version = b'02'

    def _deltaheader(self, headertuple, prevnode):
        node, p1, p2, deltabase, cs = headertuple
        flags = 0
        protocol_flags = 0
        return node, p1, p2, deltabase, cs, flags, protocol_flags


class cg3unpacker(cg2unpacker):
    """Unpacker for cg3 streams.

    cg3 streams add support for exchanging treemanifests and revlog
    flags. It adds the revlog flags to the delta header and an empty chunk
    separating manifests and files.
    """

    deltaheader = _CHANGEGROUPV3_DELTA_HEADER
    deltaheadersize = deltaheader.size
    version = b'03'
    _grouplistcount = 2  # One list of manifests and one list of files

    def _deltaheader(self, headertuple, prevnode):
        node, p1, p2, deltabase, cs, flags = headertuple
        protocol_flags = 0
        return node, p1, p2, deltabase, cs, flags, protocol_flags

    def _unpackmanifests(self, repo, revmap, trp, prog, addrevisioncb=None):
        super(cg3unpacker, self)._unpackmanifests(
            repo, revmap, trp, prog, addrevisioncb=addrevisioncb
        )
        for chunkdata in iter(self.filelogheader, {}):
            # If we get here, there are directory manifests in the changegroup
            d = chunkdata[b"filename"]
            repo.ui.debug(b"adding %s revisions\n" % d)
            deltas = self.deltaiter()
            if not repo.manifestlog.getstorage(d).addgroup(
                deltas, revmap, trp, addrevisioncb=addrevisioncb
            ):
                raise error.Abort(_(b"received dir revlog group is empty"))


class cg4unpacker(cg3unpacker):
    """Unpacker for cg4 streams.

    cg4 streams add support for exchanging sidedata.
    """

    deltaheader = _CHANGEGROUPV4_DELTA_HEADER
    deltaheadersize = deltaheader.size
    version = b'04'

    def _deltaheader(self, headertuple, prevnode):
        protocol_flags, node, p1, p2, deltabase, cs, flags = headertuple
        return node, p1, p2, deltabase, cs, flags, protocol_flags

    def deltachunk(self, prevnode):
        res = super(cg4unpacker, self).deltachunk(prevnode)
        if not res:
            return res

        (
            node,
            p1,
            p2,
            cs,
            deltabase,
            delta,
            flags,
            sidedata,
            protocol_flags,
        ) = res
        assert not sidedata

        sidedata = {}
        if protocol_flags & storageutil.CG_FLAG_SIDEDATA:
            sidedata_raw = getchunk(self._stream)
            sidedata = sidedatamod.deserialize_sidedata(sidedata_raw)

        return (
            node,
            p1,
            p2,
            cs,
            deltabase,
            delta,
            flags,
            sidedata,
            protocol_flags,
        )


class headerlessfixup(object):
    def __init__(self, fh, h):
        self._h = h
        self._fh = fh

    def read(self, n):
        if self._h:
            d, self._h = self._h[:n], self._h[n:]
            if len(d) < n:
                d += readexactly(self._fh, n - len(d))
            return d
        return readexactly(self._fh, n)


def _revisiondeltatochunks(repo, delta, headerfn):
    """Serialize a revisiondelta to changegroup chunks."""

    # The captured revision delta may be encoded as a delta against
    # a base revision or as a full revision. The changegroup format
    # requires that everything on the wire be deltas. So for full
    # revisions, we need to invent a header that says to rewrite
    # data.

    if delta.delta is not None:
        prefix, data = b'', delta.delta
    elif delta.basenode == repo.nullid:
        data = delta.revision
        prefix = mdiff.trivialdiffheader(len(data))
    else:
        data = delta.revision
        prefix = mdiff.replacediffheader(delta.baserevisionsize, len(data))

    meta = headerfn(delta)

    yield chunkheader(len(meta) + len(prefix) + len(data))
    yield meta
    if prefix:
        yield prefix
    yield data

    if delta.protocol_flags & storageutil.CG_FLAG_SIDEDATA:
        # Need a separate chunk for sidedata to be able to differentiate
        # "raw delta" length and sidedata length
        sidedata = delta.sidedata
        yield chunkheader(len(sidedata))
        yield sidedata


def _sortnodesellipsis(store, nodes, cl, lookup):
    """Sort nodes for changegroup generation."""
    # Ellipses serving mode.
    #
    # In a perfect world, we'd generate better ellipsis-ified graphs
    # for non-changelog revlogs. In practice, we haven't started doing
    # that yet, so the resulting DAGs for the manifestlog and filelogs
    # are actually full of bogus parentage on all the ellipsis
    # nodes. This has the side effect that, while the contents are
    # correct, the individual DAGs might be completely out of whack in
    # a case like 882681bc3166 and its ancestors (back about 10
    # revisions or so) in the main hg repo.
    #
    # The one invariant we *know* holds is that the new (potentially
    # bogus) DAG shape will be valid if we order the nodes in the
    # order that they're introduced in dramatis personae by the
    # changelog, so what we do is we sort the non-changelog histories
    # by the order in which they are used by the changelog.
    key = lambda n: cl.rev(lookup(n))
    return sorted(nodes, key=key)


def _resolvenarrowrevisioninfo(
    cl,
    store,
    ischangelog,
    rev,
    linkrev,
    linknode,
    clrevtolocalrev,
    fullclnodes,
    precomputedellipsis,
):
    linkparents = precomputedellipsis[linkrev]

    def local(clrev):
        """Turn a changelog revnum into a local revnum.

        The ellipsis dag is stored as revnums on the changelog,
        but when we're producing ellipsis entries for
        non-changelog revlogs, we need to turn those numbers into
        something local. This does that for us, and during the
        changelog sending phase will also expand the stored
        mappings as needed.
        """
        if clrev == nullrev:
            return nullrev

        if ischangelog:
            return clrev

        # Walk the ellipsis-ized changelog breadth-first looking for a
        # change that has been linked from the current revlog.
        #
        # For a flat manifest revlog only a single step should be necessary
        # as all relevant changelog entries are relevant to the flat
        # manifest.
        #
        # For a filelog or tree manifest dirlog however not every changelog
        # entry will have been relevant, so we need to skip some changelog
        # nodes even after ellipsis-izing.
        walk = [clrev]
        while walk:
            p = walk[0]
            walk = walk[1:]
            if p in clrevtolocalrev:
                return clrevtolocalrev[p]
            elif p in fullclnodes:
                walk.extend([pp for pp in cl.parentrevs(p) if pp != nullrev])
            elif p in precomputedellipsis:
                walk.extend(
                    [pp for pp in precomputedellipsis[p] if pp != nullrev]
                )
            else:
                # In this case, we've got an ellipsis with parents
                # outside the current bundle (likely an
                # incremental pull). We "know" that we can use the
                # value of this same revlog at whatever revision
                # is pointed to by linknode. "Know" is in scare
                # quotes because I haven't done enough examination
                # of edge cases to convince myself this is really
                # a fact - it works for all the (admittedly
                # thorough) cases in our testsuite, but I would be
                # somewhat unsurprised to find a case in the wild
                # where this breaks down a bit. That said, I don't
                # know if it would hurt anything.
                for i in pycompat.xrange(rev, 0, -1):
                    if store.linkrev(i) == clrev:
                        return i
                # We failed to resolve a parent for this node, so
                # we crash the changegroup construction.
                if util.safehasattr(store, 'target'):
                    target = store.display_id
                else:
                    # some revlog not actually a revlog
                    target = store._revlog.display_id

                raise error.Abort(
                    b"unable to resolve parent while packing '%s' %r"
                    b' for changeset %r' % (target, rev, clrev)
                )

        return nullrev

    if not linkparents or (store.parentrevs(rev) == (nullrev, nullrev)):
        p1, p2 = nullrev, nullrev
    elif len(linkparents) == 1:
        (p1,) = sorted(local(p) for p in linkparents)
        p2 = nullrev
    else:
        p1, p2 = sorted(local(p) for p in linkparents)

    p1node, p2node = store.node(p1), store.node(p2)

    return p1node, p2node, linknode


def deltagroup(
    repo,
    store,
    nodes,
    ischangelog,
    lookup,
    forcedeltaparentprev,
    topic=None,
    ellipses=False,
    clrevtolocalrev=None,
    fullclnodes=None,
    precomputedellipsis=None,
    sidedata_helpers=None,
):
    """Calculate deltas for a set of revisions.

    Is a generator of ``revisiondelta`` instances.

    If topic is not None, progress detail will be generated using this
    topic name (e.g. changesets, manifests, etc).

    See `revlogutil.sidedata.get_sidedata_helpers` for the doc on
    `sidedata_helpers`.
    """
    if not nodes:
        return

    cl = repo.changelog

    if ischangelog:
        # `hg log` shows changesets in storage order. To preserve order
        # across clones, send out changesets in storage order.
        nodesorder = b'storage'
    elif ellipses:
        nodes = _sortnodesellipsis(store, nodes, cl, lookup)
        nodesorder = b'nodes'
    else:
        nodesorder = None

    # Perform ellipses filtering and revision massaging. We do this before
    # emitrevisions() because a) filtering out revisions creates less work
    # for emitrevisions() b) dropping revisions would break emitrevisions()'s
    # assumptions about delta choices and we would possibly send a delta
    # referencing a missing base revision.
    #
    # Also, calling lookup() has side-effects with regards to populating
    # data structures. If we don't call lookup() for each node or if we call
    # lookup() after the first pass through each node, things can break -
    # possibly intermittently depending on the python hash seed! For that
    # reason, we store a mapping of all linknodes during the initial node
    # pass rather than use lookup() on the output side.
    if ellipses:
        filtered = []
        adjustedparents = {}
        linknodes = {}

        for node in nodes:
            rev = store.rev(node)
            linknode = lookup(node)
            linkrev = cl.rev(linknode)
            clrevtolocalrev[linkrev] = rev

            # If linknode is in fullclnodes, it means the corresponding
            # changeset was a full changeset and is being sent unaltered.
            if linknode in fullclnodes:
                linknodes[node] = linknode

            # If the corresponding changeset wasn't in the set computed
            # as relevant to us, it should be dropped outright.
            elif linkrev not in precomputedellipsis:
                continue

            else:
                # We could probably do this later and avoid the dict
                # holding state. But it likely doesn't matter.
                p1node, p2node, linknode = _resolvenarrowrevisioninfo(
                    cl,
                    store,
                    ischangelog,
                    rev,
                    linkrev,
                    linknode,
                    clrevtolocalrev,
                    fullclnodes,
                    precomputedellipsis,
                )

                adjustedparents[node] = (p1node, p2node)
                linknodes[node] = linknode

            filtered.append(node)

        nodes = filtered

    # We expect the first pass to be fast, so we only engage the progress
    # meter for constructing the revision deltas.
    progress = None
    if topic is not None:
        progress = repo.ui.makeprogress(
            topic, unit=_(b'chunks'), total=len(nodes)
        )

    configtarget = repo.ui.config(b'devel', b'bundle.delta')
    if configtarget not in (b'', b'p1', b'full'):
        msg = _(b"""config "devel.bundle.delta" as unknown value: %s""")
        repo.ui.warn(msg % configtarget)

    deltamode = repository.CG_DELTAMODE_STD
    if forcedeltaparentprev:
        deltamode = repository.CG_DELTAMODE_PREV
    elif configtarget == b'p1':
        deltamode = repository.CG_DELTAMODE_P1
    elif configtarget == b'full':
        deltamode = repository.CG_DELTAMODE_FULL

    revisions = store.emitrevisions(
        nodes,
        nodesorder=nodesorder,
        revisiondata=True,
        assumehaveparentrevisions=not ellipses,
        deltamode=deltamode,
        sidedata_helpers=sidedata_helpers,
    )

    for i, revision in enumerate(revisions):
        if progress:
            progress.update(i + 1)

        if ellipses:
            linknode = linknodes[revision.node]

            if revision.node in adjustedparents:
                p1node, p2node = adjustedparents[revision.node]
                revision.p1node = p1node
                revision.p2node = p2node
                revision.flags |= repository.REVISION_FLAG_ELLIPSIS

        else:
            linknode = lookup(revision.node)

        revision.linknode = linknode
        yield revision

    if progress:
        progress.complete()


class cgpacker(object):
    def __init__(
        self,
        repo,
        oldmatcher,
        matcher,
        version,
        builddeltaheader,
        manifestsend,
        forcedeltaparentprev=False,
        bundlecaps=None,
        ellipses=False,
        shallow=False,
        ellipsisroots=None,
        fullnodes=None,
        remote_sidedata=None,
    ):
        """Given a source repo, construct a bundler.

        oldmatcher is a matcher that matches on files the client already has.
        These will not be included in the changegroup.

        matcher is a matcher that matches on files to include in the
        changegroup. Used to facilitate sparse changegroups.

        forcedeltaparentprev indicates whether delta parents must be against
        the previous revision in a delta group. This should only be used for
        compatibility with changegroup version 1.

        builddeltaheader is a callable that constructs the header for a group
        delta.

        manifestsend is a chunk to send after manifests have been fully emitted.

        ellipses indicates whether ellipsis serving mode is enabled.

        bundlecaps is optional and can be used to specify the set of
        capabilities which can be used to build the bundle. While bundlecaps is
        unused in core Mercurial, extensions rely on this feature to communicate
        capabilities to customize the changegroup packer.

        shallow indicates whether shallow data might be sent. The packer may
        need to pack file contents not introduced by the changes being packed.

        fullnodes is the set of changelog nodes which should not be ellipsis
        nodes. We store this rather than the set of nodes that should be
        ellipsis because for very large histories we expect this to be
        significantly smaller.

        remote_sidedata is the set of sidedata categories wanted by the remote.
        """
        assert oldmatcher
        assert matcher
        self._oldmatcher = oldmatcher
        self._matcher = matcher

        self.version = version
        self._forcedeltaparentprev = forcedeltaparentprev
        self._builddeltaheader = builddeltaheader
        self._manifestsend = manifestsend
        self._ellipses = ellipses

        # Set of capabilities we can use to build the bundle.
        if bundlecaps is None:
            bundlecaps = set()
        self._bundlecaps = bundlecaps
        if remote_sidedata is None:
            remote_sidedata = set()
        self._remote_sidedata = remote_sidedata
        self._isshallow = shallow
        self._fullclnodes = fullnodes

        # Maps ellipsis revs to their roots at the changelog level.
        self._precomputedellipsis = ellipsisroots

        self._repo = repo

        if self._repo.ui.verbose and not self._repo.ui.debugflag:
            self._verbosenote = self._repo.ui.note
        else:
            self._verbosenote = lambda s: None

    def generate(
        self, commonrevs, clnodes, fastpathlinkrev, source, changelog=True
    ):
        """Yield a sequence of changegroup byte chunks.
        If changelog is False, changelog data won't be added to changegroup
        """

        repo = self._repo
        cl = repo.changelog

        self._verbosenote(_(b'uncompressed size of bundle content:\n'))
        size = 0

        sidedata_helpers = None
        if self.version == b'04':
            remote_sidedata = self._remote_sidedata
            if source == b'strip':
                # We're our own remote when stripping, get the no-op helpers
                # TODO a better approach would be for the strip bundle to
                # correctly advertise its sidedata categories directly.
                remote_sidedata = repo._wanted_sidedata
            sidedata_helpers = sidedatamod.get_sidedata_helpers(
                repo, remote_sidedata
            )

        clstate, deltas = self._generatechangelog(
            cl,
            clnodes,
            generate=changelog,
            sidedata_helpers=sidedata_helpers,
        )
        for delta in deltas:
            for chunk in _revisiondeltatochunks(
                self._repo, delta, self._builddeltaheader
            ):
                size += len(chunk)
                yield chunk

        close = closechunk()
        size += len(close)
        yield closechunk()

        self._verbosenote(_(b'%8.i (changelog)\n') % size)

        clrevorder = clstate[b'clrevorder']
        manifests = clstate[b'manifests']
        changedfiles = clstate[b'changedfiles']

        # We need to make sure that the linkrev in the changegroup refers to
        # the first changeset that introduced the manifest or file revision.
        # The fastpath is usually safer than the slowpath, because the filelogs
        # are walked in revlog order.
        #
        # When taking the slowpath when the manifest revlog uses generaldelta,
        # the manifest may be walked in the "wrong" order. Without 'clrevorder',
        # we would get an incorrect linkrev (see fix in cc0ff93d0c0c).
        #
        # When taking the fastpath, we are only vulnerable to reordering
        # of the changelog itself. The changelog never uses generaldelta and is
        # never reordered. To handle this case, we simply take the slowpath,
        # which already has the 'clrevorder' logic. This was also fixed in
        # cc0ff93d0c0c.

        # Treemanifests don't work correctly with fastpathlinkrev
        # either, because we don't discover which directory nodes to
        # send along with files. This could probably be fixed.
        fastpathlinkrev = fastpathlinkrev and not scmutil.istreemanifest(repo)

        fnodes = {}  # needed file nodes

        size = 0
        it = self.generatemanifests(
            commonrevs,
            clrevorder,
            fastpathlinkrev,
            manifests,
            fnodes,
            source,
            clstate[b'clrevtomanifestrev'],
            sidedata_helpers=sidedata_helpers,
        )

        for tree, deltas in it:
            if tree:
                assert self.version in (b'03', b'04')
                chunk = _fileheader(tree)
                size += len(chunk)
                yield chunk

            for delta in deltas:
                chunks = _revisiondeltatochunks(
                    self._repo, delta, self._builddeltaheader
                )
                for chunk in chunks:
                    size += len(chunk)
                    yield chunk

            close = closechunk()
            size += len(close)
            yield close

        self._verbosenote(_(b'%8.i (manifests)\n') % size)
        yield self._manifestsend

        mfdicts = None
        if self._ellipses and self._isshallow:
            mfdicts = [
                (repo.manifestlog[n].read(), lr)
                for (n, lr) in pycompat.iteritems(manifests)
            ]

        manifests.clear()
        clrevs = {cl.rev(x) for x in clnodes}

        it = self.generatefiles(
            changedfiles,
            commonrevs,
            source,
            mfdicts,
            fastpathlinkrev,
            fnodes,
            clrevs,
            sidedata_helpers=sidedata_helpers,
        )

        for path, deltas in it:
            h = _fileheader(path)
            size = len(h)
            yield h

            for delta in deltas:
                chunks = _revisiondeltatochunks(
                    self._repo, delta, self._builddeltaheader
                )
                for chunk in chunks:
                    size += len(chunk)
                    yield chunk

            close = closechunk()
            size += len(close)
            yield close

            self._verbosenote(_(b'%8.i  %s\n') % (size, path))

        yield closechunk()

        if clnodes:
            repo.hook(b'outgoing', node=hex(clnodes[0]), source=source)

    def _generatechangelog(
        self, cl, nodes, generate=True, sidedata_helpers=None
    ):
        """Generate data for changelog chunks.

        Returns a 2-tuple of a dict containing state and an iterable of
        byte chunks. The state will not be fully populated until the
        chunk stream has been fully consumed.

        if generate is False, the state will be fully populated and no chunk
        stream will be yielded

        See `revlogutil.sidedata.get_sidedata_helpers` for the doc on
        `sidedata_helpers`.
        """
        clrevorder = {}
        manifests = {}
        mfl = self._repo.manifestlog
        changedfiles = set()
        clrevtomanifestrev = {}

        state = {
            b'clrevorder': clrevorder,
            b'manifests': manifests,
            b'changedfiles': changedfiles,
            b'clrevtomanifestrev': clrevtomanifestrev,
        }

        if not (generate or self._ellipses):
            # sort the nodes in storage order
            nodes = sorted(nodes, key=cl.rev)
            for node in nodes:
                c = cl.changelogrevision(node)
                clrevorder[node] = len(clrevorder)
                # record the first changeset introducing this manifest version
                manifests.setdefault(c.manifest, node)
                # Record a complete list of potentially-changed files in
                # this manifest.
                changedfiles.update(c.files)

            return state, ()

        # Callback for the changelog, used to collect changed files and
        # manifest nodes.
        # Returns the linkrev node (identity in the changelog case).
        def lookupcl(x):
            c = cl.changelogrevision(x)
            clrevorder[x] = len(clrevorder)

            if self._ellipses:
                # Only update manifests if x is going to be sent. Otherwise we
                # end up with bogus linkrevs specified for manifests and
                # we skip some manifest nodes that we should otherwise
                # have sent.
                if (
                    x in self._fullclnodes
                    or cl.rev(x) in self._precomputedellipsis
                ):

                    manifestnode = c.manifest
                    # Record the first changeset introducing this manifest
                    # version.
                    manifests.setdefault(manifestnode, x)
                    # Set this narrow-specific dict so we have the lowest
                    # manifest revnum to look up for this cl revnum. (Part of
                    # mapping changelog ellipsis parents to manifest ellipsis
                    # parents)
                    clrevtomanifestrev.setdefault(
                        cl.rev(x), mfl.rev(manifestnode)
                    )
                # We can't trust the changed files list in the changeset if the
                # client requested a shallow clone.
                if self._isshallow:
                    changedfiles.update(mfl[c.manifest].read().keys())
                else:
                    changedfiles.update(c.files)
            else:
                # record the first changeset introducing this manifest version
                manifests.setdefault(c.manifest, x)
                # Record a complete list of potentially-changed files in
                # this manifest.
                changedfiles.update(c.files)

            return x

        gen = deltagroup(
            self._repo,
            cl,
            nodes,
            True,
            lookupcl,
            self._forcedeltaparentprev,
            ellipses=self._ellipses,
            topic=_(b'changesets'),
            clrevtolocalrev={},
            fullclnodes=self._fullclnodes,
            precomputedellipsis=self._precomputedellipsis,
            sidedata_helpers=sidedata_helpers,
        )

        return state, gen

    def generatemanifests(
        self,
        commonrevs,
        clrevorder,
        fastpathlinkrev,
        manifests,
        fnodes,
        source,
        clrevtolocalrev,
        sidedata_helpers=None,
    ):
        """Returns an iterator of changegroup chunks containing manifests.

        `source` is unused here, but is used by extensions like remotefilelog to
        change what is sent based in pulls vs pushes, etc.

        See `revlogutil.sidedata.get_sidedata_helpers` for the doc on
        `sidedata_helpers`.
        """
        repo = self._repo
        mfl = repo.manifestlog
        tmfnodes = {b'': manifests}

        # Callback for the manifest, used to collect linkrevs for filelog
        # revisions.
        # Returns the linkrev node (collected in lookupcl).
        def makelookupmflinknode(tree, nodes):
            if fastpathlinkrev:
                assert not tree

                # pytype: disable=unsupported-operands
                return manifests.__getitem__
                # pytype: enable=unsupported-operands

            def lookupmflinknode(x):
                """Callback for looking up the linknode for manifests.

                Returns the linkrev node for the specified manifest.

                SIDE EFFECT:

                1) fclnodes gets populated with the list of relevant
                   file nodes if we're not using fastpathlinkrev
                2) When treemanifests are in use, collects treemanifest nodes
                   to send

                Note that this means manifests must be completely sent to
                the client before you can trust the list of files and
                treemanifests to send.
                """
                clnode = nodes[x]
                mdata = mfl.get(tree, x).readfast(shallow=True)
                for p, n, fl in mdata.iterentries():
                    if fl == b't':  # subdirectory manifest
                        subtree = tree + p + b'/'
                        tmfclnodes = tmfnodes.setdefault(subtree, {})
                        tmfclnode = tmfclnodes.setdefault(n, clnode)
                        if clrevorder[clnode] < clrevorder[tmfclnode]:
                            tmfclnodes[n] = clnode
                    else:
                        f = tree + p
                        fclnodes = fnodes.setdefault(f, {})
                        fclnode = fclnodes.setdefault(n, clnode)
                        if clrevorder[clnode] < clrevorder[fclnode]:
                            fclnodes[n] = clnode
                return clnode

            return lookupmflinknode

        while tmfnodes:
            tree, nodes = tmfnodes.popitem()

            should_visit = self._matcher.visitdir(tree[:-1])
            if tree and not should_visit:
                continue

            store = mfl.getstorage(tree)

            if not should_visit:
                # No nodes to send because this directory is out of
                # the client's view of the repository (probably
                # because of narrow clones). Do this even for the root
                # directory (tree=='')
                prunednodes = []
            else:
                # Avoid sending any manifest nodes we can prove the
                # client already has by checking linkrevs. See the
                # related comment in generatefiles().
                prunednodes = self._prunemanifests(store, nodes, commonrevs)

            if tree and not prunednodes:
                continue

            lookupfn = makelookupmflinknode(tree, nodes)

            deltas = deltagroup(
                self._repo,
                store,
                prunednodes,
                False,
                lookupfn,
                self._forcedeltaparentprev,
                ellipses=self._ellipses,
                topic=_(b'manifests'),
                clrevtolocalrev=clrevtolocalrev,
                fullclnodes=self._fullclnodes,
                precomputedellipsis=self._precomputedellipsis,
                sidedata_helpers=sidedata_helpers,
            )

            if not self._oldmatcher.visitdir(store.tree[:-1]):
                yield tree, deltas
            else:
                # 'deltas' is a generator and we need to consume it even if
                # we are not going to send it because a side-effect is that
                # it updates tmdnodes (via lookupfn)
                for d in deltas:
                    pass
                if not tree:
                    yield tree, []

    def _prunemanifests(self, store, nodes, commonrevs):
        if not self._ellipses:
            # In non-ellipses case and large repositories, it is better to
            # prevent calling of store.rev and store.linkrev on a lot of
            # nodes as compared to sending some extra data
            return nodes.copy()
        # This is split out as a separate method to allow filtering
        # commonrevs in extension code.
        #
        # TODO(augie): this shouldn't be required, instead we should
        # make filtering of revisions to send delegated to the store
        # layer.
        frev, flr = store.rev, store.linkrev
        return [n for n in nodes if flr(frev(n)) not in commonrevs]

    # The 'source' parameter is useful for extensions
    def generatefiles(
        self,
        changedfiles,
        commonrevs,
        source,
        mfdicts,
        fastpathlinkrev,
        fnodes,
        clrevs,
        sidedata_helpers=None,
    ):
        changedfiles = [
            f
            for f in changedfiles
            if self._matcher(f) and not self._oldmatcher(f)
        ]

        if not fastpathlinkrev:

            def normallinknodes(unused, fname):
                return fnodes.get(fname, {})

        else:
            cln = self._repo.changelog.node

            def normallinknodes(store, fname):
                flinkrev = store.linkrev
                fnode = store.node
                revs = ((r, flinkrev(r)) for r in store)
                return {fnode(r): cln(lr) for r, lr in revs if lr in clrevs}

        clrevtolocalrev = {}

        if self._isshallow:
            # In a shallow clone, the linknodes callback needs to also include
            # those file nodes that are in the manifests we sent but weren't
            # introduced by those manifests.
            commonctxs = [self._repo[c] for c in commonrevs]
            clrev = self._repo.changelog.rev

            def linknodes(flog, fname):
                for c in commonctxs:
                    try:
                        fnode = c.filenode(fname)
                        clrevtolocalrev[c.rev()] = flog.rev(fnode)
                    except error.ManifestLookupError:
                        pass
                links = normallinknodes(flog, fname)
                if len(links) != len(mfdicts):
                    for mf, lr in mfdicts:
                        fnode = mf.get(fname, None)
                        if fnode in links:
                            links[fnode] = min(links[fnode], lr, key=clrev)
                        elif fnode:
                            links[fnode] = lr
                return links

        else:
            linknodes = normallinknodes

        repo = self._repo
        progress = repo.ui.makeprogress(
            _(b'files'), unit=_(b'files'), total=len(changedfiles)
        )
        for i, fname in enumerate(sorted(changedfiles)):
            filerevlog = repo.file(fname)
            if not filerevlog:
                raise error.Abort(
                    _(b"empty or missing file data for %s") % fname
                )

            clrevtolocalrev.clear()

            linkrevnodes = linknodes(filerevlog, fname)
            # Lookup for filenodes, we collected the linkrev nodes above in the
            # fastpath case and with lookupmf in the slowpath case.
            def lookupfilelog(x):
                return linkrevnodes[x]

            frev, flr = filerevlog.rev, filerevlog.linkrev
            # Skip sending any filenode we know the client already
            # has. This avoids over-sending files relatively
            # inexpensively, so it's not a problem if we under-filter
            # here.
            filenodes = [
                n for n in linkrevnodes if flr(frev(n)) not in commonrevs
            ]

            if not filenodes:
                continue

            progress.update(i + 1, item=fname)

            deltas = deltagroup(
                self._repo,
                filerevlog,
                filenodes,
                False,
                lookupfilelog,
                self._forcedeltaparentprev,
                ellipses=self._ellipses,
                clrevtolocalrev=clrevtolocalrev,
                fullclnodes=self._fullclnodes,
                precomputedellipsis=self._precomputedellipsis,
                sidedata_helpers=sidedata_helpers,
            )

            yield fname, deltas

        progress.complete()


def _makecg1packer(
    repo,
    oldmatcher,
    matcher,
    bundlecaps,
    ellipses=False,
    shallow=False,
    ellipsisroots=None,
    fullnodes=None,
    remote_sidedata=None,
):
    builddeltaheader = lambda d: _CHANGEGROUPV1_DELTA_HEADER.pack(
        d.node, d.p1node, d.p2node, d.linknode
    )

    return cgpacker(
        repo,
        oldmatcher,
        matcher,
        b'01',
        builddeltaheader=builddeltaheader,
        manifestsend=b'',
        forcedeltaparentprev=True,
        bundlecaps=bundlecaps,
        ellipses=ellipses,
        shallow=shallow,
        ellipsisroots=ellipsisroots,
        fullnodes=fullnodes,
    )


def _makecg2packer(
    repo,
    oldmatcher,
    matcher,
    bundlecaps,
    ellipses=False,
    shallow=False,
    ellipsisroots=None,
    fullnodes=None,
    remote_sidedata=None,
):
    builddeltaheader = lambda d: _CHANGEGROUPV2_DELTA_HEADER.pack(
        d.node, d.p1node, d.p2node, d.basenode, d.linknode
    )

    return cgpacker(
        repo,
        oldmatcher,
        matcher,
        b'02',
        builddeltaheader=builddeltaheader,
        manifestsend=b'',
        bundlecaps=bundlecaps,
        ellipses=ellipses,
        shallow=shallow,
        ellipsisroots=ellipsisroots,
        fullnodes=fullnodes,
    )


def _makecg3packer(
    repo,
    oldmatcher,
    matcher,
    bundlecaps,
    ellipses=False,
    shallow=False,
    ellipsisroots=None,
    fullnodes=None,
    remote_sidedata=None,
):
    builddeltaheader = lambda d: _CHANGEGROUPV3_DELTA_HEADER.pack(
        d.node, d.p1node, d.p2node, d.basenode, d.linknode, d.flags
    )

    return cgpacker(
        repo,
        oldmatcher,
        matcher,
        b'03',
        builddeltaheader=builddeltaheader,
        manifestsend=closechunk(),
        bundlecaps=bundlecaps,
        ellipses=ellipses,
        shallow=shallow,
        ellipsisroots=ellipsisroots,
        fullnodes=fullnodes,
    )


def _makecg4packer(
    repo,
    oldmatcher,
    matcher,
    bundlecaps,
    ellipses=False,
    shallow=False,
    ellipsisroots=None,
    fullnodes=None,
    remote_sidedata=None,
):
    # Sidedata is in a separate chunk from the delta to differentiate
    # "raw delta" and sidedata.
    def builddeltaheader(d):
        return _CHANGEGROUPV4_DELTA_HEADER.pack(
            d.protocol_flags,
            d.node,
            d.p1node,
            d.p2node,
            d.basenode,
            d.linknode,
            d.flags,
        )

    return cgpacker(
        repo,
        oldmatcher,
        matcher,
        b'04',
        builddeltaheader=builddeltaheader,
        manifestsend=closechunk(),
        bundlecaps=bundlecaps,
        ellipses=ellipses,
        shallow=shallow,
        ellipsisroots=ellipsisroots,
        fullnodes=fullnodes,
        remote_sidedata=remote_sidedata,
    )


_packermap = {
    b'01': (_makecg1packer, cg1unpacker),
    # cg2 adds support for exchanging generaldelta
    b'02': (_makecg2packer, cg2unpacker),
    # cg3 adds support for exchanging revlog flags and treemanifests
    b'03': (_makecg3packer, cg3unpacker),
    # ch4 adds support for exchanging sidedata
    b'04': (_makecg4packer, cg4unpacker),
}


def allsupportedversions(repo):
    versions = set(_packermap.keys())
    needv03 = False
    if (
        repo.ui.configbool(b'experimental', b'changegroup3')
        or repo.ui.configbool(b'experimental', b'treemanifest')
        or scmutil.istreemanifest(repo)
    ):
        # we keep version 03 because we need to to exchange treemanifest data
        #
        # we also keep vresion 01 and 02, because it is possible for repo to
        # contains both normal and tree manifest at the same time. so using
        # older version to pull data is viable
        #
        # (or even to push subset of history)
        needv03 = True
    if not needv03:
        versions.discard(b'03')
    want_v4 = (
        repo.ui.configbool(b'experimental', b'changegroup4')
        or requirements.REVLOGV2_REQUIREMENT in repo.requirements
        or requirements.CHANGELOGV2_REQUIREMENT in repo.requirements
    )
    if not want_v4:
        versions.discard(b'04')
    return versions


# Changegroup versions that can be applied to the repo
def supportedincomingversions(repo):
    return allsupportedversions(repo)


# Changegroup versions that can be created from the repo
def supportedoutgoingversions(repo):
    versions = allsupportedversions(repo)
    if scmutil.istreemanifest(repo):
        # Versions 01 and 02 support only flat manifests and it's just too
        # expensive to convert between the flat manifest and tree manifest on
        # the fly. Since tree manifests are hashed differently, all of history
        # would have to be converted. Instead, we simply don't even pretend to
        # support versions 01 and 02.
        versions.discard(b'01')
        versions.discard(b'02')
    if requirements.NARROW_REQUIREMENT in repo.requirements:
        # Versions 01 and 02 don't support revlog flags, and we need to
        # support that for stripping and unbundling to work.
        versions.discard(b'01')
        versions.discard(b'02')
    if LFS_REQUIREMENT in repo.requirements:
        # Versions 01 and 02 don't support revlog flags, and we need to
        # mark LFS entries with REVIDX_EXTSTORED.
        versions.discard(b'01')
        versions.discard(b'02')

    return versions


def localversion(repo):
    # Finds the best version to use for bundles that are meant to be used
    # locally, such as those from strip and shelve, and temporary bundles.
    return max(supportedoutgoingversions(repo))


def safeversion(repo):
    # Finds the smallest version that it's safe to assume clients of the repo
    # will support. For example, all hg versions that support generaldelta also
    # support changegroup 02.
    versions = supportedoutgoingversions(repo)
    if requirements.GENERALDELTA_REQUIREMENT in repo.requirements:
        versions.discard(b'01')
    assert versions
    return min(versions)


def getbundler(
    version,
    repo,
    bundlecaps=None,
    oldmatcher=None,
    matcher=None,
    ellipses=False,
    shallow=False,
    ellipsisroots=None,
    fullnodes=None,
    remote_sidedata=None,
):
    assert version in supportedoutgoingversions(repo)

    if matcher is None:
        matcher = matchmod.always()
    if oldmatcher is None:
        oldmatcher = matchmod.never()

    if version == b'01' and not matcher.always():
        raise error.ProgrammingError(
            b'version 01 changegroups do not support sparse file matchers'
        )

    if ellipses and version in (b'01', b'02'):
        raise error.Abort(
            _(
                b'ellipsis nodes require at least cg3 on client and server, '
                b'but negotiated version %s'
            )
            % version
        )

    # Requested files could include files not in the local store. So
    # filter those out.
    matcher = repo.narrowmatch(matcher)

    fn = _packermap[version][0]
    return fn(
        repo,
        oldmatcher,
        matcher,
        bundlecaps,
        ellipses=ellipses,
        shallow=shallow,
        ellipsisroots=ellipsisroots,
        fullnodes=fullnodes,
        remote_sidedata=remote_sidedata,
    )


def getunbundler(version, fh, alg, extras=None):
    return _packermap[version][1](fh, alg, extras=extras)


def _changegroupinfo(repo, nodes, source):
    if repo.ui.verbose or source == b'bundle':
        repo.ui.status(_(b"%d changesets found\n") % len(nodes))
    if repo.ui.debugflag:
        repo.ui.debug(b"list of changesets:\n")
        for node in nodes:
            repo.ui.debug(b"%s\n" % hex(node))


def makechangegroup(
    repo, outgoing, version, source, fastpath=False, bundlecaps=None
):
    cgstream = makestream(
        repo,
        outgoing,
        version,
        source,
        fastpath=fastpath,
        bundlecaps=bundlecaps,
    )
    return getunbundler(
        version,
        util.chunkbuffer(cgstream),
        None,
        {b'clcount': len(outgoing.missing)},
    )


def makestream(
    repo,
    outgoing,
    version,
    source,
    fastpath=False,
    bundlecaps=None,
    matcher=None,
    remote_sidedata=None,
):
    bundler = getbundler(
        version,
        repo,
        bundlecaps=bundlecaps,
        matcher=matcher,
        remote_sidedata=remote_sidedata,
    )

    repo = repo.unfiltered()
    commonrevs = outgoing.common
    csets = outgoing.missing
    heads = outgoing.ancestorsof
    # We go through the fast path if we get told to, or if all (unfiltered
    # heads have been requested (since we then know there all linkrevs will
    # be pulled by the client).
    heads.sort()
    fastpathlinkrev = fastpath or (
        repo.filtername is None and heads == sorted(repo.heads())
    )

    repo.hook(b'preoutgoing', throw=True, source=source)
    _changegroupinfo(repo, csets, source)
    return bundler.generate(commonrevs, csets, fastpathlinkrev, source)


def _addchangegroupfiles(
    repo,
    source,
    revmap,
    trp,
    expectedfiles,
    needfiles,
    addrevisioncb=None,
):
    revisions = 0
    files = 0
    progress = repo.ui.makeprogress(
        _(b'files'), unit=_(b'files'), total=expectedfiles
    )
    for chunkdata in iter(source.filelogheader, {}):
        files += 1
        f = chunkdata[b"filename"]
        repo.ui.debug(b"adding %s revisions\n" % f)
        progress.increment()
        fl = repo.file(f)
        o = len(fl)
        try:
            deltas = source.deltaiter()
            added = fl.addgroup(
                deltas,
                revmap,
                trp,
                addrevisioncb=addrevisioncb,
            )
            if not added:
                raise error.Abort(_(b"received file revlog group is empty"))
        except error.CensoredBaseError as e:
            raise error.Abort(_(b"received delta base is censored: %s") % e)
        revisions += len(fl) - o
        if f in needfiles:
            needs = needfiles[f]
            for new in pycompat.xrange(o, len(fl)):
                n = fl.node(new)
                if n in needs:
                    needs.remove(n)
                else:
                    raise error.Abort(_(b"received spurious file revlog entry"))
            if not needs:
                del needfiles[f]
    progress.complete()

    for f, needs in pycompat.iteritems(needfiles):
        fl = repo.file(f)
        for n in needs:
            try:
                fl.rev(n)
            except error.LookupError:
                raise error.Abort(
                    _(b'missing file data for %s:%s - run hg verify')
                    % (f, hex(n))
                )

    return revisions, files
