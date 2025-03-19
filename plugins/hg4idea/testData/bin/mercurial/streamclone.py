# streamclone.py - producing and consuming streaming repository data
#
# Copyright 2015 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import contextlib
import os
import struct

from .i18n import _
from .interfaces import repository
from . import (
    bookmarks,
    bundle2 as bundle2mod,
    cacheutil,
    error,
    narrowspec,
    phases,
    pycompat,
    requirements as requirementsmod,
    scmutil,
    store,
    transaction,
    util,
)
from .revlogutils import (
    nodemap,
)


def new_stream_clone_requirements(default_requirements, streamed_requirements):
    """determine the final set of requirement for a new stream clone

    this method combine the "default" requirements that a new repository would
    use with the constaint we get from the stream clone content. We keep local
    configuration choice when possible.
    """
    requirements = set(default_requirements)
    requirements -= requirementsmod.STREAM_FIXED_REQUIREMENTS
    requirements.update(streamed_requirements)
    return requirements


def streamed_requirements(repo):
    """the set of requirement the new clone will have to support

    This is used for advertising the stream options and to generate the actual
    stream content."""
    requiredformats = (
        repo.requirements & requirementsmod.STREAM_FIXED_REQUIREMENTS
    )
    return requiredformats


def canperformstreamclone(pullop, bundle2=False):
    """Whether it is possible to perform a streaming clone as part of pull.

    ``bundle2`` will cause the function to consider stream clone through
    bundle2 and only through bundle2.

    Returns a tuple of (supported, requirements). ``supported`` is True if
    streaming clone is supported and False otherwise. ``requirements`` is
    a set of repo requirements from the remote, or ``None`` if stream clone
    isn't supported.
    """
    repo = pullop.repo
    remote = pullop.remote

    # should we consider streaming clone at all ?
    streamrequested = pullop.streamclonerequested
    # If we don't have a preference, let the server decide for us. This
    # likely only comes into play in LANs.
    if streamrequested is None:
        # The server can advertise whether to prefer streaming clone.
        streamrequested = remote.capable(b'stream-preferred')
    if not streamrequested:
        return False, None

    # Streaming clone only works on an empty destination repository
    if len(repo):
        return False, None

    # Streaming clone only works if all data is being requested.
    if pullop.heads:
        return False, None

    bundle2supported = False
    if pullop.canusebundle2:
        local_caps = bundle2mod.getrepocaps(repo, role=b'client')
        local_supported = set(local_caps.get(b'stream', []))
        remote_supported = set(pullop.remotebundle2caps.get(b'stream', []))
        bundle2supported = bool(local_supported & remote_supported)
        # else
        # Server doesn't support bundle2 stream clone or doesn't support
        # the versions we support. Fall back and possibly allow legacy.

    # Ensures legacy code path uses available bundle2.
    if bundle2supported and not bundle2:
        return False, None
    # Ensures bundle2 doesn't try to do a stream clone if it isn't supported.
    elif bundle2 and not bundle2supported:
        return False, None

    # In order for stream clone to work, the client has to support all the
    # requirements advertised by the server.
    #
    # The server advertises its requirements via the "stream" and "streamreqs"
    # capability. "stream" (a value-less capability) is advertised if and only
    # if the only requirement is "revlogv1." Else, the "streamreqs" capability
    # is advertised and contains a comma-delimited list of requirements.
    requirements = set()
    if remote.capable(b'stream'):
        requirements.add(requirementsmod.REVLOGV1_REQUIREMENT)
    else:
        streamreqs = remote.capable(b'streamreqs')
        # This is weird and shouldn't happen with modern servers.
        if not streamreqs:
            pullop.repo.ui.warn(
                _(
                    b'warning: stream clone requested but server has them '
                    b'disabled\n'
                )
            )
            return False, None

        streamreqs = set(streamreqs.split(b','))
        # Server requires something we don't support. Bail.
        missingreqs = streamreqs - repo.supported
        if missingreqs:
            pullop.repo.ui.warn(
                _(
                    b'warning: stream clone requested but client is missing '
                    b'requirements: %s\n'
                )
                % b', '.join(sorted(missingreqs))
            )
            pullop.repo.ui.warn(
                _(
                    b'(see https://www.mercurial-scm.org/wiki/MissingRequirement '
                    b'for more information)\n'
                )
            )
            return False, None
        requirements = streamreqs

    return True, requirements


def maybeperformlegacystreamclone(pullop):
    """Possibly perform a legacy stream clone operation.

    Legacy stream clones are performed as part of pull but before all other
    operations.

    A legacy stream clone will not be performed if a bundle2 stream clone is
    supported.
    """
    from . import localrepo

    supported, requirements = canperformstreamclone(pullop)

    if not supported:
        return

    repo = pullop.repo
    remote = pullop.remote

    # Save remote branchmap. We will use it later to speed up branchcache
    # creation.
    rbranchmap = None
    if remote.capable(b'branchmap'):
        with remote.commandexecutor() as e:
            rbranchmap = e.callcommand(b'branchmap', {}).result()

    repo.ui.status(_(b'streaming all changes\n'))

    with remote.commandexecutor() as e:
        fp = e.callcommand(b'stream_out', {}).result()

    # TODO strictly speaking, this code should all be inside the context
    # manager because the context manager is supposed to ensure all wire state
    # is flushed when exiting. But the legacy peers don't do this, so it
    # doesn't matter.
    l = fp.readline()
    try:
        resp = int(l)
    except ValueError:
        raise error.ResponseError(
            _(b'unexpected response from remote server:'), l
        )
    if resp == 1:
        raise error.Abort(_(b'operation forbidden by server'))
    elif resp == 2:
        raise error.Abort(_(b'locking the remote repository failed'))
    elif resp != 0:
        raise error.Abort(_(b'the server sent an unknown error code'))

    l = fp.readline()
    try:
        filecount, bytecount = map(int, l.split(b' ', 1))
    except (ValueError, TypeError):
        raise error.ResponseError(
            _(b'unexpected response from remote server:'), l
        )

    with repo.lock():
        consumev1(repo, fp, filecount, bytecount)
        repo.requirements = new_stream_clone_requirements(
            repo.requirements,
            requirements,
        )
        repo.svfs.options = localrepo.resolvestorevfsoptions(
            repo.ui, repo.requirements, repo.features
        )
        scmutil.writereporequirements(repo)
        nodemap.post_stream_cleanup(repo)

        if rbranchmap:
            repo._branchcaches.replace(repo, rbranchmap)

        repo.invalidate()


def allowservergeneration(repo):
    """Whether streaming clones are allowed from the server."""
    if repository.REPO_FEATURE_STREAM_CLONE not in repo.features:
        return False

    if not repo.ui.configbool(b'server', b'uncompressed', untrusted=True):
        return False

    # The way stream clone works makes it impossible to hide secret changesets.
    # So don't allow this by default.
    secret = phases.hassecret(repo)
    if secret:
        return repo.ui.configbool(b'server', b'uncompressedallowsecret')

    return True


# This is it's own function so extensions can override it.
def _walkstreamfiles(repo, matcher=None, phase=False, obsolescence=False):
    return repo.store.walk(matcher, phase=phase, obsolescence=obsolescence)


def generatev1(repo):
    """Emit content for version 1 of a streaming clone.

    This returns a 3-tuple of (file count, byte size, data iterator).

    The data iterator consists of N entries for each file being transferred.
    Each file entry starts as a line with the file name and integer size
    delimited by a null byte.

    The raw file data follows. Following the raw file data is the next file
    entry, or EOF.

    When used on the wire protocol, an additional line indicating protocol
    success will be prepended to the stream. This function is not responsible
    for adding it.

    This function will obtain a repository lock to ensure a consistent view of
    the store is captured. It therefore may raise LockError.
    """
    entries = []
    total_bytes = 0
    # Get consistent snapshot of repo, lock during scan.
    with repo.lock():
        repo.ui.debug(b'scanning\n')
        for entry in _walkstreamfiles(repo):
            for f in entry.files():
                file_size = f.file_size(repo.store.vfs)
                if file_size:
                    entries.append((f.unencoded_path, file_size))
                    total_bytes += file_size
        _test_sync_point_walk_1(repo)
    _test_sync_point_walk_2(repo)

    repo.ui.debug(
        b'%d files, %d bytes to transfer\n' % (len(entries), total_bytes)
    )

    svfs = repo.svfs
    debugflag = repo.ui.debugflag

    def emitrevlogdata():
        for name, size in entries:
            if debugflag:
                repo.ui.debug(b'sending %s (%d bytes)\n' % (name, size))
            # partially encode name over the wire for backwards compat
            yield b'%s\0%d\n' % (store.encodedir(name), size)
            # auditing at this stage is both pointless (paths are already
            # trusted by the local repo) and expensive
            with svfs(name, b'rb', auditpath=False) as fp:
                if size <= 65536:
                    yield fp.read(size)
                else:
                    for chunk in util.filechunkiter(fp, limit=size):
                        yield chunk

    return len(entries), total_bytes, emitrevlogdata()


def generatev1wireproto(repo):
    """Emit content for version 1 of streaming clone suitable for the wire.

    This is the data output from ``generatev1()`` with 2 header lines. The
    first line indicates overall success. The 2nd contains the file count and
    byte size of payload.

    The success line contains "0" for success, "1" for stream generation not
    allowed, and "2" for error locking the repository (possibly indicating
    a permissions error for the server process).
    """
    if not allowservergeneration(repo):
        yield b'1\n'
        return

    try:
        filecount, bytecount, it = generatev1(repo)
    except error.LockError:
        yield b'2\n'
        return

    # Indicates successful response.
    yield b'0\n'
    yield b'%d %d\n' % (filecount, bytecount)
    for chunk in it:
        yield chunk


def generatebundlev1(repo, compression=b'UN'):
    """Emit content for version 1 of a stream clone bundle.

    The first 4 bytes of the output ("HGS1") denote this as stream clone
    bundle version 1.

    The next 2 bytes indicate the compression type. Only "UN" is currently
    supported.

    The next 16 bytes are two 64-bit big endian unsigned integers indicating
    file count and byte count, respectively.

    The next 2 bytes is a 16-bit big endian unsigned short declaring the length
    of the requirements string, including a trailing \0. The following N bytes
    are the requirements string, which is ASCII containing a comma-delimited
    list of repo requirements that are needed to support the data.

    The remaining content is the output of ``generatev1()`` (which may be
    compressed in the future).

    Returns a tuple of (requirements, data generator).
    """
    if compression != b'UN':
        raise ValueError(b'we do not support the compression argument yet')

    requirements = streamed_requirements(repo)
    requires = b','.join(sorted(requirements))

    def gen():
        yield b'HGS1'
        yield compression

        filecount, bytecount, it = generatev1(repo)
        repo.ui.status(
            _(b'writing %d bytes for %d files\n') % (bytecount, filecount)
        )

        yield struct.pack(b'>QQ', filecount, bytecount)
        yield struct.pack(b'>H', len(requires) + 1)
        yield requires + b'\0'

        # This is where we'll add compression in the future.
        assert compression == b'UN'

        progress = repo.ui.makeprogress(
            _(b'bundle'), total=bytecount, unit=_(b'bytes')
        )
        progress.update(0)

        for chunk in it:
            progress.increment(step=len(chunk))
            yield chunk

        progress.complete()

    return requirements, gen()


def consumev1(repo, fp, filecount, bytecount):
    """Apply the contents from version 1 of a streaming clone file handle.

    This takes the output from "stream_out" and applies it to the specified
    repository.

    Like "stream_out," the status line added by the wire protocol is not
    handled by this function.
    """
    with repo.lock():
        repo.ui.status(
            _(b'%d files to transfer, %s of data\n')
            % (filecount, util.bytecount(bytecount))
        )
        progress = repo.ui.makeprogress(
            _(b'clone'), total=bytecount, unit=_(b'bytes')
        )
        progress.update(0)
        start = util.timer()

        # TODO: get rid of (potential) inconsistency
        #
        # If transaction is started and any @filecache property is
        # changed at this point, it causes inconsistency between
        # in-memory cached property and streamclone-ed file on the
        # disk. Nested transaction prevents transaction scope "clone"
        # below from writing in-memory changes out at the end of it,
        # even though in-memory changes are discarded at the end of it
        # regardless of transaction nesting.
        #
        # But transaction nesting can't be simply prohibited, because
        # nesting occurs also in ordinary case (e.g. enabling
        # clonebundles).

        with repo.transaction(b'clone'):
            with repo.svfs.backgroundclosing(repo.ui, expectedcount=filecount):
                for i in range(filecount):
                    # XXX doesn't support '\n' or '\r' in filenames
                    if hasattr(fp, 'readline'):
                        l = fp.readline()
                    else:
                        # inline clonebundles use a chunkbuffer, so no readline
                        # --> this should be small anyway, the first line
                        # only contains the size of the bundle
                        l_buf = []
                        while not (l_buf and l_buf[-1] == b'\n'):
                            l_buf.append(fp.read(1))
                        l = b''.join(l_buf)
                    try:
                        name, size = l.split(b'\0', 1)
                        size = int(size)
                    except (ValueError, TypeError):
                        raise error.ResponseError(
                            _(b'unexpected response from remote server:'), l
                        )
                    if repo.ui.debugflag:
                        repo.ui.debug(
                            b'adding %s (%s)\n' % (name, util.bytecount(size))
                        )
                    # for backwards compat, name was partially encoded
                    path = store.decodedir(name)
                    with repo.svfs(path, b'w', backgroundclose=True) as ofp:
                        for chunk in util.filechunkiter(fp, limit=size):
                            progress.increment(step=len(chunk))
                            ofp.write(chunk)

            # force @filecache properties to be reloaded from
            # streamclone-ed file at next access
            repo.invalidate(clearfilecache=True)

        elapsed = util.timer() - start
        if elapsed <= 0:
            elapsed = 0.001
        progress.complete()
        repo.ui.status(
            _(b'transferred %s in %.1f seconds (%s/sec)\n')
            % (
                util.bytecount(bytecount),
                elapsed,
                util.bytecount(bytecount / elapsed),
            )
        )


def readbundle1header(fp):
    compression = fp.read(2)
    if compression != b'UN':
        raise error.Abort(
            _(
                b'only uncompressed stream clone bundles are '
                b'supported; got %s'
            )
            % compression
        )

    filecount, bytecount = struct.unpack(b'>QQ', fp.read(16))
    requireslen = struct.unpack(b'>H', fp.read(2))[0]
    requires = fp.read(requireslen)

    if not requires.endswith(b'\0'):
        raise error.Abort(
            _(
                b'malformed stream clone bundle: '
                b'requirements not properly encoded'
            )
        )

    requirements = set(requires.rstrip(b'\0').split(b','))

    return filecount, bytecount, requirements


def applybundlev1(repo, fp):
    """Apply the content from a stream clone bundle version 1.

    We assume the 4 byte header has been read and validated and the file handle
    is at the 2 byte compression identifier.
    """
    if len(repo):
        raise error.Abort(
            _(b'cannot apply stream clone bundle on non-empty repo')
        )

    filecount, bytecount, requirements = readbundle1header(fp)
    missingreqs = requirements - repo.supported
    if missingreqs:
        raise error.Abort(
            _(b'unable to apply stream clone: unsupported format: %s')
            % b', '.join(sorted(missingreqs))
        )

    consumev1(repo, fp, filecount, bytecount)
    nodemap.post_stream_cleanup(repo)


class streamcloneapplier:
    """Class to manage applying streaming clone bundles.

    We need to wrap ``applybundlev1()`` in a dedicated type to enable bundle
    readers to perform bundle type-specific functionality.
    """

    def __init__(self, fh):
        self._fh = fh

    def apply(self, repo):
        return applybundlev1(repo, self._fh)


# type of file to stream
_fileappend = 0  # append only file
_filefull = 1  # full snapshot file

# Source of the file
_srcstore = b's'  # store (svfs)
_srccache = b'c'  # cache (cache)

# This is it's own function so extensions can override it.
def _walkstreamfullstorefiles(repo):
    """list snapshot file from the store"""
    fnames = []
    if not repo.publishing():
        fnames.append(b'phaseroots')
    return fnames


def _filterfull(entry, copy, vfsmap):
    """actually copy the snapshot files"""
    src, name, ftype, data = entry
    if ftype != _filefull:
        return entry
    return (src, name, ftype, copy(vfsmap[src].join(name)))


class TempCopyManager:
    """Manage temporary backup of volatile file during stream clone

    This should be used as a Python context, the copies will be discarded when
    exiting the context.

    A copy can be done by calling the object on the real path (encoded full
    path)

    The backup path can be retrieved using the __getitem__ protocol, obj[path].
    On file without backup, it will return the unmodified path. (equivalent to
    `dict.get(x, x)`)
    """

    def __init__(self):
        self._copies = None
        self._dst_dir = None

    def __enter__(self):
        if self._copies is not None:
            msg = "Copies context already open"
            raise error.ProgrammingError(msg)
        self._copies = {}
        self._dst_dir = pycompat.mkdtemp(prefix=b'hg-clone-')
        return self

    def __call__(self, src):
        """create a backup of the file at src"""
        prefix = os.path.basename(src)
        fd, dst = pycompat.mkstemp(prefix=prefix, dir=self._dst_dir)
        os.close(fd)
        self._copies[src] = dst
        util.copyfiles(src, dst, hardlink=True)
        return dst

    def __getitem__(self, src):
        """return the path to a valid version of `src`

        If the file has no backup, the path of the file is returned
        unmodified."""
        return self._copies.get(src, src)

    def __exit__(self, *args, **kwars):
        """discard all backups"""
        for tmp in self._copies.values():
            util.tryunlink(tmp)
        util.tryrmdir(self._dst_dir)
        self._copies = None
        self._dst_dir = None


def _makemap(repo):
    """make a (src -> vfs) map for the repo"""
    vfsmap = {
        _srcstore: repo.svfs,
        _srccache: repo.cachevfs,
    }
    # we keep repo.vfs out of the on purpose, ther are too many danger there
    # (eg: .hg/hgrc)
    assert repo.vfs not in vfsmap.values()

    return vfsmap


def _emit2(repo, entries):
    """actually emit the stream bundle"""
    vfsmap = _makemap(repo)
    # we keep repo.vfs out of the on purpose, ther are too many danger there
    # (eg: .hg/hgrc),
    #
    # this assert is duplicated (from _makemap) as author might think this is
    # fine, while this is really not fine.
    if repo.vfs in vfsmap.values():
        raise error.ProgrammingError(
            b'repo.vfs must not be added to vfsmap for security reasons'
        )

    # translate the vfs one
    entries = [(vfs_key, vfsmap[vfs_key], e) for (vfs_key, e) in entries]

    max_linkrev = len(repo)
    file_count = totalfilesize = 0
    with util.nogc():
        # record the expected size of every file
        for k, vfs, e in entries:
            for f in e.files():
                file_count += 1
                totalfilesize += f.file_size(vfs)

    progress = repo.ui.makeprogress(
        _(b'bundle'), total=totalfilesize, unit=_(b'bytes')
    )
    progress.update(0)
    with TempCopyManager() as copy, progress:
        # create a copy of volatile files
        for k, vfs, e in entries:
            for f in e.files():
                if f.is_volatile:
                    copy(vfs.join(f.unencoded_path))
        # the first yield release the lock on the repository
        yield file_count, totalfilesize
        totalbytecount = 0

        for src, vfs, e in entries:
            entry_streams = e.get_streams(
                repo=repo,
                vfs=vfs,
                copies=copy,
                max_changeset=max_linkrev,
                preserve_file_count=True,
            )
            for name, stream, size in entry_streams:
                yield src
                yield util.uvarintencode(len(name))
                yield util.uvarintencode(size)
                yield name
                bytecount = 0
                for chunk in stream:
                    bytecount += len(chunk)
                    totalbytecount += len(chunk)
                    progress.update(totalbytecount)
                    yield chunk
                if bytecount != size:
                    # Would most likely be caused by a race due to `hg
                    # strip` or a revlog split
                    msg = _(
                        b'clone could only read %d bytes from %s, but '
                        b'expected %d bytes'
                    )
                    raise error.Abort(msg % (bytecount, name, size))


def _emit3(repo, entries):
    """actually emit the stream bundle (v3)"""
    vfsmap = _makemap(repo)
    # we keep repo.vfs out of the map on purpose, ther are too many dangers
    # there (eg: .hg/hgrc),
    #
    # this assert is duplicated (from _makemap) as authors might think this is
    # fine, while this is really not fine.
    if repo.vfs in vfsmap.values():
        raise error.ProgrammingError(
            b'repo.vfs must not be added to vfsmap for security reasons'
        )

    # translate the vfs once
    entries = [(vfs_key, vfsmap[vfs_key], e) for (vfs_key, e) in entries]
    total_entry_count = len(entries)

    max_linkrev = len(repo)
    progress = repo.ui.makeprogress(
        _(b'bundle'),
        total=total_entry_count,
        unit=_(b'entry'),
    )
    progress.update(0)
    with TempCopyManager() as copy, progress:
        # create a copy of volatile files
        for k, vfs, e in entries:
            if e.maybe_volatile:
                for f in e.files():
                    if f.is_volatile:
                        # record the expected size under lock
                        f.file_size(vfs)
                        copy(vfs.join(f.unencoded_path))
        # the first yield release the lock on the repository
        yield None

        yield util.uvarintencode(total_entry_count)

        for src, vfs, e in entries:
            entry_streams = e.get_streams(
                repo=repo,
                vfs=vfs,
                copies=copy,
                max_changeset=max_linkrev,
            )
            yield util.uvarintencode(len(entry_streams))
            for name, stream, size in entry_streams:
                yield src
                yield util.uvarintencode(len(name))
                yield util.uvarintencode(size)
                yield name
                yield from stream
            progress.increment()


def _test_sync_point_walk_1(repo):
    """a function for synchronisation during tests"""


def _test_sync_point_walk_2(repo):
    """a function for synchronisation during tests"""


def _entries_walk(repo, includes, excludes, includeobsmarkers):
    """emit a seris of files information useful to clone a repo

    return (vfs-key, entry) iterator

    Where `entry` is StoreEntry. (used even for cache entries)
    """
    assert repo._currentlock(repo._lockref) is not None

    matcher = None
    if includes or excludes:
        matcher = narrowspec.match(repo.root, includes, excludes)

    phase = not repo.publishing()
    # Python is getting crazy at all the small container we creates, disabling
    # the gc while we do so helps performance a lot.
    with util.nogc():
        entries = _walkstreamfiles(
            repo,
            matcher,
            phase=phase,
            obsolescence=includeobsmarkers,
        )
        for entry in entries:
            yield (_srcstore, entry)

        for name in cacheutil.cachetocopy(repo):
            if repo.cachevfs.exists(name):
                # not really a StoreEntry, but close enough
                entry = store.SimpleStoreEntry(
                    entry_path=name,
                    is_volatile=True,
                )
                yield (_srccache, entry)


def generatev2(repo, includes, excludes, includeobsmarkers):
    """Emit content for version 2 of a streaming clone.

    the data stream consists the following entries:
    1) A char representing the file destination (eg: store or cache)
    2) A varint containing the length of the filename
    3) A varint containing the length of file data
    4) N bytes containing the filename (the internal, store-agnostic form)
    5) N bytes containing the file data

    Returns a 3-tuple of (file count, file size, data iterator).
    """

    with repo.lock():

        repo.ui.debug(b'scanning\n')

        entries = _entries_walk(
            repo,
            includes=includes,
            excludes=excludes,
            includeobsmarkers=includeobsmarkers,
        )

        chunks = _emit2(repo, entries)
        first = next(chunks)
        file_count, total_file_size = first
        _test_sync_point_walk_1(repo)
    _test_sync_point_walk_2(repo)

    return file_count, total_file_size, chunks


def generatev3(repo, includes, excludes, includeobsmarkers):
    """Emit content for version 3 of a streaming clone.

    the data stream consists the following:
    1) A varint E containing the number of entries (can be 0), then E entries follow
    2) For each entry:
    2.1) The number of files in this entry (can be 0, but typically 1 or 2)
    2.2) For each file:
    2.2.1) A char representing the file destination (eg: store or cache)
    2.2.2) A varint N containing the length of the filename
    2.2.3) A varint M containing the length of file data
    2.2.4) N bytes containing the filename (the internal, store-agnostic form)
    2.2.5) M bytes containing the file data

    Returns the data iterator.

    XXX This format is experimental and subject to change. Here is a
    XXX non-exhaustive list of things this format could do or change:

    - making it easier to write files in parallel
    - holding the lock for a shorter time
    - improving progress information
    - ways to adjust the number of expected entries/files ?
    """

    # Python is getting crazy at all the small container we creates while
    # considering the files to preserve, disabling the gc while we do so helps
    # performance a lot.
    with repo.lock(), util.nogc():

        repo.ui.debug(b'scanning\n')

        entries = _entries_walk(
            repo,
            includes=includes,
            excludes=excludes,
            includeobsmarkers=includeobsmarkers,
        )
        chunks = _emit3(repo, list(entries))
        first = next(chunks)
        assert first is None
        _test_sync_point_walk_1(repo)
    _test_sync_point_walk_2(repo)

    return chunks


@contextlib.contextmanager
def nested(*ctxs):
    this = ctxs[0]
    rest = ctxs[1:]
    with this:
        if rest:
            with nested(*rest):
                yield
        else:
            yield


def consumev2(repo, fp, filecount, filesize):
    """Apply the contents from a version 2 streaming clone.

    Data is read from an object that only needs to provide a ``read(size)``
    method.
    """
    with repo.lock():
        repo.ui.status(
            _(b'%d files to transfer, %s of data\n')
            % (filecount, util.bytecount(filesize))
        )

        start = util.timer()
        progress = repo.ui.makeprogress(
            _(b'clone'), total=filesize, unit=_(b'bytes')
        )
        progress.update(0)

        vfsmap = _makemap(repo)
        # we keep repo.vfs out of the on purpose, ther are too many danger
        # there (eg: .hg/hgrc),
        #
        # this assert is duplicated (from _makemap) as author might think this
        # is fine, while this is really not fine.
        if repo.vfs in vfsmap.values():
            raise error.ProgrammingError(
                b'repo.vfs must not be added to vfsmap for security reasons'
            )

        with repo.transaction(b'clone'):
            ctxs = (vfs.backgroundclosing(repo.ui) for vfs in vfsmap.values())
            with nested(*ctxs):
                for i in range(filecount):
                    src = util.readexactly(fp, 1)
                    vfs = vfsmap[src]
                    namelen = util.uvarintdecodestream(fp)
                    datalen = util.uvarintdecodestream(fp)

                    name = util.readexactly(fp, namelen)

                    if repo.ui.debugflag:
                        repo.ui.debug(
                            b'adding [%s] %s (%s)\n'
                            % (src, name, util.bytecount(datalen))
                        )

                    with vfs(name, b'w') as ofp:
                        for chunk in util.filechunkiter(fp, limit=datalen):
                            progress.increment(step=len(chunk))
                            ofp.write(chunk)

            # force @filecache properties to be reloaded from
            # streamclone-ed file at next access
            repo.invalidate(clearfilecache=True)

        elapsed = util.timer() - start
        if elapsed <= 0:
            elapsed = 0.001
        repo.ui.status(
            _(b'transferred %s in %.1f seconds (%s/sec)\n')
            % (
                util.bytecount(progress.pos),
                elapsed,
                util.bytecount(progress.pos / elapsed),
            )
        )
        progress.complete()


def consumev3(repo, fp):
    """Apply the contents from a version 3 streaming clone.

    Data is read from an object that only needs to provide a ``read(size)``
    method.
    """
    with repo.lock():
        start = util.timer()

        entrycount = util.uvarintdecodestream(fp)
        repo.ui.status(_(b'%d entries to transfer\n') % (entrycount))

        progress = repo.ui.makeprogress(
            _(b'clone'),
            total=entrycount,
            unit=_(b'entries'),
        )
        progress.update(0)
        bytes_transferred = 0

        vfsmap = _makemap(repo)
        # we keep repo.vfs out of the on purpose, there are too many dangers
        # there (eg: .hg/hgrc),
        #
        # this assert is duplicated (from _makemap) as authors might think this
        # is fine, while this is really not fine.
        if repo.vfs in vfsmap.values():
            raise error.ProgrammingError(
                b'repo.vfs must not be added to vfsmap for security reasons'
            )

        with repo.transaction(b'clone'):
            ctxs = (vfs.backgroundclosing(repo.ui) for vfs in vfsmap.values())
            with nested(*ctxs):

                for i in range(entrycount):
                    filecount = util.uvarintdecodestream(fp)
                    if filecount == 0:
                        if repo.ui.debugflag:
                            repo.ui.debug(b'entry with no files [%d]\n' % (i))
                    for i in range(filecount):
                        src = util.readexactly(fp, 1)
                        vfs = vfsmap[src]
                        namelen = util.uvarintdecodestream(fp)
                        datalen = util.uvarintdecodestream(fp)

                        name = util.readexactly(fp, namelen)

                        if repo.ui.debugflag:
                            msg = b'adding [%s] %s (%s)\n'
                            msg %= (src, name, util.bytecount(datalen))
                            repo.ui.debug(msg)
                        bytes_transferred += datalen

                        with vfs(name, b'w') as ofp:
                            for chunk in util.filechunkiter(fp, limit=datalen):
                                ofp.write(chunk)
                    progress.increment(step=1)

            # force @filecache properties to be reloaded from
            # streamclone-ed file at next access
            repo.invalidate(clearfilecache=True)

        elapsed = util.timer() - start
        if elapsed <= 0:
            elapsed = 0.001
        msg = _(b'transferred %s in %.1f seconds (%s/sec)\n')
        byte_count = util.bytecount(bytes_transferred)
        bytes_sec = util.bytecount(bytes_transferred / elapsed)
        msg %= (byte_count, elapsed, bytes_sec)
        repo.ui.status(msg)
        progress.complete()


def applybundlev2(repo, fp, filecount, filesize, requirements):
    from . import localrepo

    missingreqs = [r for r in requirements if r not in repo.supported]
    if missingreqs:
        raise error.Abort(
            _(b'unable to apply stream clone: unsupported format: %s')
            % b', '.join(sorted(missingreqs))
        )

    consumev2(repo, fp, filecount, filesize)

    repo.requirements = new_stream_clone_requirements(
        repo.requirements,
        requirements,
    )
    repo.svfs.options = localrepo.resolvestorevfsoptions(
        repo.ui, repo.requirements, repo.features
    )
    scmutil.writereporequirements(repo)
    nodemap.post_stream_cleanup(repo)


def applybundlev3(repo, fp, requirements):
    from . import localrepo

    missingreqs = [r for r in requirements if r not in repo.supported]
    if missingreqs:
        msg = _(b'unable to apply stream clone: unsupported format: %s')
        msg %= b', '.join(sorted(missingreqs))
        raise error.Abort(msg)

    consumev3(repo, fp)

    repo.requirements = new_stream_clone_requirements(
        repo.requirements,
        requirements,
    )
    repo.svfs.options = localrepo.resolvestorevfsoptions(
        repo.ui, repo.requirements, repo.features
    )
    scmutil.writereporequirements(repo)
    nodemap.post_stream_cleanup(repo)


def _copy_files(src_vfs_map, dst_vfs_map, entries, progress):
    hardlink = [True]

    def copy_used():
        hardlink[0] = False
        progress.topic = _(b'copying')

    for k, path in entries:
        src_vfs = src_vfs_map[k]
        dst_vfs = dst_vfs_map[k]
        src_path = src_vfs.join(path)
        dst_path = dst_vfs.join(path)
        # We cannot use dirname and makedirs of dst_vfs here because the store
        # encoding confuses them. See issue 6581 for details.
        dirname = os.path.dirname(dst_path)
        if not os.path.exists(dirname):
            util.makedirs(dirname)
        dst_vfs.register_file(path)
        # XXX we could use the #nb_bytes argument.
        util.copyfile(
            src_path,
            dst_path,
            hardlink=hardlink[0],
            no_hardlink_cb=copy_used,
            check_fs_hardlink=False,
        )
        progress.increment()
    return hardlink[0]


def local_copy(src_repo, dest_repo):
    """copy all content from one local repository to another

    This is useful for local clone"""
    src_store_requirements = {
        r
        for r in src_repo.requirements
        if r not in requirementsmod.WORKING_DIR_REQUIREMENTS
    }
    dest_store_requirements = {
        r
        for r in dest_repo.requirements
        if r not in requirementsmod.WORKING_DIR_REQUIREMENTS
    }
    assert src_store_requirements == dest_store_requirements

    with dest_repo.lock():
        with src_repo.lock():

            # bookmark is not integrated to the streaming as it might use the
            # `repo.vfs` and they are too many sentitive data accessible
            # through `repo.vfs` to expose it to streaming clone.
            src_book_vfs = bookmarks.bookmarksvfs(src_repo)
            srcbookmarks = src_book_vfs.join(b'bookmarks')
            bm_count = 0
            if os.path.exists(srcbookmarks):
                bm_count = 1

            entries = _entries_walk(
                src_repo,
                includes=None,
                excludes=None,
                includeobsmarkers=True,
            )
            entries = list(entries)
            src_vfs_map = _makemap(src_repo)
            dest_vfs_map = _makemap(dest_repo)
            total_files = sum(len(e[1].files()) for e in entries) + bm_count
            progress = src_repo.ui.makeprogress(
                topic=_(b'linking'),
                total=total_files,
                unit=_(b'files'),
            )
            # copy  files
            #
            # We could copy the full file while the source repository is locked
            # and the other one without the lock. However, in the linking case,
            # this would also requires checks that nobody is appending any data
            # to the files while we do the clone, so this is not done yet. We
            # could do this blindly when copying files.
            files = [
                (vfs_key, f.unencoded_path)
                for vfs_key, e in entries
                for f in e.files()
            ]
            hardlink = _copy_files(src_vfs_map, dest_vfs_map, files, progress)

            # copy bookmarks over
            if bm_count:
                dst_book_vfs = bookmarks.bookmarksvfs(dest_repo)
                dstbookmarks = dst_book_vfs.join(b'bookmarks')
                util.copyfile(srcbookmarks, dstbookmarks)
        progress.complete()
        if hardlink:
            msg = b'linked %d files\n'
        else:
            msg = b'copied %d files\n'
        src_repo.ui.debug(msg % total_files)

        with dest_repo.transaction(b"localclone") as tr:
            dest_repo.store.write(tr)

        # clean up transaction file as they do not make sense
        transaction.cleanup_undo_files(dest_repo.ui.warn, dest_repo.vfs_map)
