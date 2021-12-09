from __future__ import absolute_import

import errno
import os
import shutil
import stat
import time

from mercurial.i18n import _
from mercurial.node import bin, hex
from mercurial.pycompat import open
from mercurial import (
    error,
    pycompat,
    util,
)
from mercurial.utils import hashutil
from . import (
    constants,
    shallowutil,
)


class basestore(object):
    def __init__(self, repo, path, reponame, shared=False):
        """Creates a remotefilelog store object for the given repo name.

        `path` - The file path where this store keeps its data
        `reponame` - The name of the repo. This is used to partition data from
        many repos.
        `shared` - True if this store is a shared cache of data from the central
        server, for many repos on this machine. False means this store is for
        the local data for one repo.
        """
        self.repo = repo
        self.ui = repo.ui
        self._path = path
        self._reponame = reponame
        self._shared = shared
        self._uid = os.getuid() if not pycompat.iswindows else None

        self._validatecachelog = self.ui.config(
            b"remotefilelog", b"validatecachelog"
        )
        self._validatecache = self.ui.config(
            b"remotefilelog", b"validatecache", b'on'
        )
        if self._validatecache not in (b'on', b'strict', b'off'):
            self._validatecache = b'on'
        if self._validatecache == b'off':
            self._validatecache = False

        if shared:
            shallowutil.mkstickygroupdir(self.ui, path)

    def getmissing(self, keys):
        missing = []
        for name, node in keys:
            filepath = self._getfilepath(name, node)
            exists = os.path.exists(filepath)
            if (
                exists
                and self._validatecache == b'strict'
                and not self._validatekey(filepath, b'contains')
            ):
                exists = False
            if not exists:
                missing.append((name, node))

        return missing

    # BELOW THIS ARE IMPLEMENTATIONS OF REPACK SOURCE

    def markledger(self, ledger, options=None):
        if options and options.get(constants.OPTION_PACKSONLY):
            return
        if self._shared:
            for filename, nodes in self._getfiles():
                for node in nodes:
                    ledger.markdataentry(self, filename, node)
                    ledger.markhistoryentry(self, filename, node)

    def cleanup(self, ledger):
        ui = self.ui
        entries = ledger.sources.get(self, [])
        count = 0
        progress = ui.makeprogress(
            _(b"cleaning up"), unit=b"files", total=len(entries)
        )
        for entry in entries:
            if entry.gced or (entry.datarepacked and entry.historyrepacked):
                progress.update(count)
                path = self._getfilepath(entry.filename, entry.node)
                util.tryunlink(path)
            count += 1
        progress.complete()

        # Clean up the repo cache directory.
        self._cleanupdirectory(self._getrepocachepath())

    # BELOW THIS ARE NON-STANDARD APIS

    def _cleanupdirectory(self, rootdir):
        """Removes the empty directories and unnecessary files within the root
        directory recursively. Note that this method does not remove the root
        directory itself."""

        oldfiles = set()
        otherfiles = set()
        # osutil.listdir returns stat information which saves some rmdir/listdir
        # syscalls.
        for name, mode in util.osutil.listdir(rootdir):
            if stat.S_ISDIR(mode):
                dirpath = os.path.join(rootdir, name)
                self._cleanupdirectory(dirpath)

                # Now that the directory specified by dirpath is potentially
                # empty, try and remove it.
                try:
                    os.rmdir(dirpath)
                except OSError:
                    pass

            elif stat.S_ISREG(mode):
                if name.endswith(b'_old'):
                    oldfiles.add(name[:-4])
                else:
                    otherfiles.add(name)

        # Remove the files which end with suffix '_old' and have no
        # corresponding file without the suffix '_old'. See addremotefilelognode
        # method for the generation/purpose of files with '_old' suffix.
        for filename in oldfiles - otherfiles:
            filepath = os.path.join(rootdir, filename + b'_old')
            util.tryunlink(filepath)

    def _getfiles(self):
        """Return a list of (filename, [node,...]) for all the revisions that
        exist in the store.

        This is useful for obtaining a list of all the contents of the store
        when performing a repack to another store, since the store API requires
        name+node keys and not namehash+node keys.
        """
        existing = {}
        for filenamehash, node in self._listkeys():
            existing.setdefault(filenamehash, []).append(node)

        filenamemap = self._resolvefilenames(existing.keys())

        for filename, sha in pycompat.iteritems(filenamemap):
            yield (filename, existing[sha])

    def _resolvefilenames(self, hashes):
        """Given a list of filename hashes that are present in the
        remotefilelog store, return a mapping from filename->hash.

        This is useful when converting remotefilelog blobs into other storage
        formats.
        """
        if not hashes:
            return {}

        filenames = {}
        missingfilename = set(hashes)

        # Start with a full manifest, since it'll cover the majority of files
        for filename in self.repo[b'tip'].manifest():
            sha = hashutil.sha1(filename).digest()
            if sha in missingfilename:
                filenames[filename] = sha
                missingfilename.discard(sha)

        # Scan the changelog until we've found every file name
        cl = self.repo.unfiltered().changelog
        for rev in pycompat.xrange(len(cl) - 1, -1, -1):
            if not missingfilename:
                break
            files = cl.readfiles(cl.node(rev))
            for filename in files:
                sha = hashutil.sha1(filename).digest()
                if sha in missingfilename:
                    filenames[filename] = sha
                    missingfilename.discard(sha)

        return filenames

    def _getrepocachepath(self):
        return (
            os.path.join(self._path, self._reponame)
            if self._shared
            else self._path
        )

    def _listkeys(self):
        """List all the remotefilelog keys that exist in the store.

        Returns a iterator of (filename hash, filecontent hash) tuples.
        """

        for root, dirs, files in os.walk(self._getrepocachepath()):
            for filename in files:
                if len(filename) != 40:
                    continue
                node = filename
                if self._shared:
                    # .../1a/85ffda..be21
                    filenamehash = root[-41:-39] + root[-38:]
                else:
                    filenamehash = root[-40:]
                yield (bin(filenamehash), bin(node))

    def _getfilepath(self, name, node):
        node = hex(node)
        if self._shared:
            key = shallowutil.getcachekey(self._reponame, name, node)
        else:
            key = shallowutil.getlocalkey(name, node)

        return os.path.join(self._path, key)

    def _getdata(self, name, node):
        filepath = self._getfilepath(name, node)
        try:
            data = shallowutil.readfile(filepath)
            if self._validatecache and not self._validatedata(data, filepath):
                if self._validatecachelog:
                    with open(self._validatecachelog, b'ab+') as f:
                        f.write(b"corrupt %s during read\n" % filepath)
                os.rename(filepath, filepath + b".corrupt")
                raise KeyError(b"corrupt local cache file %s" % filepath)
        except IOError:
            raise KeyError(
                b"no file found at %s for %s:%s" % (filepath, name, hex(node))
            )

        return data

    def addremotefilelognode(self, name, node, data):
        filepath = self._getfilepath(name, node)

        oldumask = os.umask(0o002)
        try:
            # if this node already exists, save the old version for
            # recovery/debugging purposes.
            if os.path.exists(filepath):
                newfilename = filepath + b'_old'
                # newfilename can be read-only and shutil.copy will fail.
                # Delete newfilename to avoid it
                if os.path.exists(newfilename):
                    shallowutil.unlinkfile(newfilename)
                shutil.copy(filepath, newfilename)

            shallowutil.mkstickygroupdir(self.ui, os.path.dirname(filepath))
            shallowutil.writefile(filepath, data, readonly=True)

            if self._validatecache:
                if not self._validatekey(filepath, b'write'):
                    raise error.Abort(
                        _(b"local cache write was corrupted %s") % filepath
                    )
        finally:
            os.umask(oldumask)

    def markrepo(self, path):
        """Call this to add the given repo path to the store's list of
        repositories that are using it. This is useful later when doing garbage
        collection, since it allows us to insecpt the repos to see what nodes
        they want to be kept alive in the store.
        """
        repospath = os.path.join(self._path, b"repos")
        with open(repospath, b'ab') as reposfile:
            reposfile.write(os.path.dirname(path) + b"\n")

        repospathstat = os.stat(repospath)
        if repospathstat.st_uid == self._uid:
            os.chmod(repospath, 0o0664)

    def _validatekey(self, path, action):
        with open(path, b'rb') as f:
            data = f.read()

        if self._validatedata(data, path):
            return True

        if self._validatecachelog:
            with open(self._validatecachelog, b'ab+') as f:
                f.write(b"corrupt %s during %s\n" % (path, action))

        os.rename(path, path + b".corrupt")
        return False

    def _validatedata(self, data, path):
        try:
            if len(data) > 0:
                # see remotefilelogserver.createfileblob for the format
                offset, size, flags = shallowutil.parsesizeflags(data)
                if len(data) <= size:
                    # it is truncated
                    return False

                # extract the node from the metadata
                offset += size
                datanode = data[offset : offset + 20]

                # and compare against the path
                if os.path.basename(path) == hex(datanode):
                    # Content matches the intended path
                    return True
                return False
        except (ValueError, shallowutil.BadRemotefilelogHeader):
            pass

        return False

    def gc(self, keepkeys):
        ui = self.ui
        cachepath = self._path

        # prune cache
        queue = pycompat.queue.PriorityQueue()
        originalsize = 0
        size = 0
        count = 0
        removed = 0

        # keep files newer than a day even if they aren't needed
        limit = time.time() - (60 * 60 * 24)

        progress = ui.makeprogress(
            _(b"removing unnecessary files"), unit=b"files"
        )
        progress.update(0)
        for root, dirs, files in os.walk(cachepath):
            for file in files:
                if file == b'repos':
                    continue

                # Don't delete pack files
                if b'/packs/' in root:
                    continue

                progress.update(count)
                path = os.path.join(root, file)
                key = os.path.relpath(path, cachepath)
                count += 1
                try:
                    pathstat = os.stat(path)
                except OSError as e:
                    # errno.ENOENT = no such file or directory
                    if e.errno != errno.ENOENT:
                        raise
                    msg = _(
                        b"warning: file %s was removed by another process\n"
                    )
                    ui.warn(msg % path)
                    continue

                originalsize += pathstat.st_size

                if key in keepkeys or pathstat.st_atime > limit:
                    queue.put((pathstat.st_atime, path, pathstat))
                    size += pathstat.st_size
                else:
                    try:
                        shallowutil.unlinkfile(path)
                    except OSError as e:
                        # errno.ENOENT = no such file or directory
                        if e.errno != errno.ENOENT:
                            raise
                        msg = _(
                            b"warning: file %s was removed by another "
                            b"process\n"
                        )
                        ui.warn(msg % path)
                        continue
                    removed += 1
        progress.complete()

        # remove oldest files until under limit
        limit = ui.configbytes(b"remotefilelog", b"cachelimit")
        if size > limit:
            excess = size - limit
            progress = ui.makeprogress(
                _(b"enforcing cache limit"), unit=b"bytes", total=excess
            )
            removedexcess = 0
            while queue and size > limit and size > 0:
                progress.update(removedexcess)
                atime, oldpath, oldpathstat = queue.get()
                try:
                    shallowutil.unlinkfile(oldpath)
                except OSError as e:
                    # errno.ENOENT = no such file or directory
                    if e.errno != errno.ENOENT:
                        raise
                    msg = _(
                        b"warning: file %s was removed by another process\n"
                    )
                    ui.warn(msg % oldpath)
                size -= oldpathstat.st_size
                removed += 1
                removedexcess += oldpathstat.st_size
            progress.complete()

        ui.status(
            _(b"finished: removed %d of %d files (%0.2f GB to %0.2f GB)\n")
            % (
                removed,
                count,
                float(originalsize) / 1024.0 / 1024.0 / 1024.0,
                float(size) / 1024.0 / 1024.0 / 1024.0,
            )
        )


class baseunionstore(object):
    def __init__(self, *args, **kwargs):
        # If one of the functions that iterates all of the stores is about to
        # throw a KeyError, try this many times with a full refresh between
        # attempts. A repack operation may have moved data from one store to
        # another while we were running.
        self.numattempts = kwargs.get('numretries', 0) + 1
        # If not-None, call this function on every retry and if the attempts are
        # exhausted.
        self.retrylog = kwargs.get('retrylog', None)

    def markforrefresh(self):
        for store in self.stores:
            if util.safehasattr(store, b'markforrefresh'):
                store.markforrefresh()

    @staticmethod
    def retriable(fn):
        def noop(*args):
            pass

        def wrapped(self, *args, **kwargs):
            retrylog = self.retrylog or noop
            funcname = fn.__name__
            i = 0
            while i < self.numattempts:
                if i > 0:
                    retrylog(
                        b're-attempting (n=%d) %s\n'
                        % (i, pycompat.sysbytes(funcname))
                    )
                    self.markforrefresh()
                i += 1
                try:
                    return fn(self, *args, **kwargs)
                except KeyError:
                    if i == self.numattempts:
                        # retries exhausted
                        retrylog(
                            b'retries exhausted in %s, raising KeyError\n'
                            % pycompat.sysbytes(funcname)
                        )
                        raise

        return wrapped
