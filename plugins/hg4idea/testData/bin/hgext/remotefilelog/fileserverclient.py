# fileserverclient.py - client for communicating with the cache process
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import io
import os
import threading
import time
import zlib

from mercurial.i18n import _
from mercurial.node import bin, hex
from mercurial import (
    error,
    pycompat,
    revlog,
    sshpeer,
    util,
    wireprotov1peer,
)
from mercurial.utils import (
    hashutil,
    procutil,
)

from . import (
    constants,
    contentstore,
    metadatastore,
)

_sshv1peer = sshpeer.sshv1peer

# Statistics for debugging
fetchcost = 0
fetches = 0
fetched = 0
fetchmisses = 0

_lfsmod = None


def getcachekey(reponame, file, id):
    pathhash = hex(hashutil.sha1(file).digest())
    return os.path.join(reponame, pathhash[:2], pathhash[2:], id)


def getlocalkey(file, id):
    pathhash = hex(hashutil.sha1(file).digest())
    return os.path.join(pathhash, id)


def peersetup(ui, peer):
    class remotefilepeer(peer.__class__):
        @wireprotov1peer.batchable
        def x_rfl_getfile(self, file, node):
            if not self.capable(b'x_rfl_getfile'):
                raise error.Abort(
                    b'configured remotefile server does not support getfile'
                )
            f = wireprotov1peer.future()
            yield {b'file': file, b'node': node}, f
            code, data = f.value.split(b'\0', 1)
            if int(code):
                raise error.LookupError(file, node, data)
            yield data

        @wireprotov1peer.batchable
        def x_rfl_getflogheads(self, path):
            if not self.capable(b'x_rfl_getflogheads'):
                raise error.Abort(
                    b'configured remotefile server does not '
                    b'support getflogheads'
                )
            f = wireprotov1peer.future()
            yield {b'path': path}, f
            heads = f.value.split(b'\n') if f.value else []
            yield heads

        def _updatecallstreamopts(self, command, opts):
            if command != b'getbundle':
                return
            if (
                constants.NETWORK_CAP_LEGACY_SSH_GETFILES
                not in self.capabilities()
            ):
                return
            if not util.safehasattr(self, '_localrepo'):
                return
            if (
                constants.SHALLOWREPO_REQUIREMENT
                not in self._localrepo.requirements
            ):
                return

            bundlecaps = opts.get(b'bundlecaps')
            if bundlecaps:
                bundlecaps = [bundlecaps]
            else:
                bundlecaps = []

            # shallow, includepattern, and excludepattern are a hacky way of
            # carrying over data from the local repo to this getbundle
            # command. We need to do it this way because bundle1 getbundle
            # doesn't provide any other place we can hook in to manipulate
            # getbundle args before it goes across the wire. Once we get rid
            # of bundle1, we can use bundle2's _pullbundle2extraprepare to
            # do this more cleanly.
            bundlecaps.append(constants.BUNDLE2_CAPABLITY)
            if self._localrepo.includepattern:
                patterns = b'\0'.join(self._localrepo.includepattern)
                includecap = b"includepattern=" + patterns
                bundlecaps.append(includecap)
            if self._localrepo.excludepattern:
                patterns = b'\0'.join(self._localrepo.excludepattern)
                excludecap = b"excludepattern=" + patterns
                bundlecaps.append(excludecap)
            opts[b'bundlecaps'] = b','.join(bundlecaps)

        def _sendrequest(self, command, args, **opts):
            self._updatecallstreamopts(command, args)
            return super(remotefilepeer, self)._sendrequest(
                command, args, **opts
            )

        def _callstream(self, command, **opts):
            supertype = super(remotefilepeer, self)
            if not util.safehasattr(supertype, '_sendrequest'):
                self._updatecallstreamopts(command, pycompat.byteskwargs(opts))
            return super(remotefilepeer, self)._callstream(command, **opts)

    peer.__class__ = remotefilepeer


class cacheconnection(object):
    """The connection for communicating with the remote cache. Performs
    gets and sets by communicating with an external process that has the
    cache-specific implementation.
    """

    def __init__(self):
        self.pipeo = self.pipei = self.pipee = None
        self.subprocess = None
        self.connected = False

    def connect(self, cachecommand):
        if self.pipeo:
            raise error.Abort(_(b"cache connection already open"))
        self.pipei, self.pipeo, self.pipee, self.subprocess = procutil.popen4(
            cachecommand
        )
        self.connected = True

    def close(self):
        def tryclose(pipe):
            try:
                pipe.close()
            except Exception:
                pass

        if self.connected:
            try:
                self.pipei.write(b"exit\n")
            except Exception:
                pass
            tryclose(self.pipei)
            self.pipei = None
            tryclose(self.pipeo)
            self.pipeo = None
            tryclose(self.pipee)
            self.pipee = None
            try:
                # Wait for process to terminate, making sure to avoid deadlock.
                # See https://docs.python.org/2/library/subprocess.html for
                # warnings about wait() and deadlocking.
                self.subprocess.communicate()
            except Exception:
                pass
            self.subprocess = None
        self.connected = False

    def request(self, request, flush=True):
        if self.connected:
            try:
                self.pipei.write(request)
                if flush:
                    self.pipei.flush()
            except IOError:
                self.close()

    def receiveline(self):
        if not self.connected:
            return None
        try:
            result = self.pipeo.readline()[:-1]
            if not result:
                self.close()
        except IOError:
            self.close()

        return result


def _getfilesbatch(
    remote, receivemissing, progresstick, missed, idmap, batchsize
):
    # Over http(s), iterbatch is a streamy method and we can start
    # looking at results early. This means we send one (potentially
    # large) request, but then we show nice progress as we process
    # file results, rather than showing chunks of $batchsize in
    # progress.
    #
    # Over ssh, iterbatch isn't streamy because batch() wasn't
    # explicitly designed as a streaming method. In the future we
    # should probably introduce a streambatch() method upstream and
    # use that for this.
    with remote.commandexecutor() as e:
        futures = []
        for m in missed:
            futures.append(
                e.callcommand(
                    b'x_rfl_getfile', {b'file': idmap[m], b'node': m[-40:]}
                )
            )

        for i, m in enumerate(missed):
            r = futures[i].result()
            futures[i] = None  # release memory
            file_ = idmap[m]
            node = m[-40:]
            receivemissing(io.BytesIO(b'%d\n%s' % (len(r), r)), file_, node)
            progresstick()


def _getfiles_optimistic(
    remote, receivemissing, progresstick, missed, idmap, step
):
    remote._callstream(b"x_rfl_getfiles")
    i = 0
    pipeo = remote._pipeo
    pipei = remote._pipei
    while i < len(missed):
        # issue a batch of requests
        start = i
        end = min(len(missed), start + step)
        i = end
        for missingid in missed[start:end]:
            # issue new request
            versionid = missingid[-40:]
            file = idmap[missingid]
            sshrequest = b"%s%s\n" % (versionid, file)
            pipeo.write(sshrequest)
        pipeo.flush()

        # receive batch results
        for missingid in missed[start:end]:
            versionid = missingid[-40:]
            file = idmap[missingid]
            receivemissing(pipei, file, versionid)
            progresstick()

    # End the command
    pipeo.write(b'\n')
    pipeo.flush()


def _getfiles_threaded(
    remote, receivemissing, progresstick, missed, idmap, step
):
    remote._callstream(b"x_rfl_getfiles")
    pipeo = remote._pipeo
    pipei = remote._pipei

    def writer():
        for missingid in missed:
            versionid = missingid[-40:]
            file = idmap[missingid]
            sshrequest = b"%s%s\n" % (versionid, file)
            pipeo.write(sshrequest)
        pipeo.flush()

    writerthread = threading.Thread(target=writer)
    writerthread.daemon = True
    writerthread.start()

    for missingid in missed:
        versionid = missingid[-40:]
        file = idmap[missingid]
        receivemissing(pipei, file, versionid)
        progresstick()

    writerthread.join()
    # End the command
    pipeo.write(b'\n')
    pipeo.flush()


class fileserverclient(object):
    """A client for requesting files from the remote file server."""

    def __init__(self, repo):
        ui = repo.ui
        self.repo = repo
        self.ui = ui
        self.cacheprocess = ui.config(b"remotefilelog", b"cacheprocess")
        if self.cacheprocess:
            self.cacheprocess = util.expandpath(self.cacheprocess)

        # This option causes remotefilelog to pass the full file path to the
        # cacheprocess instead of a hashed key.
        self.cacheprocesspasspath = ui.configbool(
            b"remotefilelog", b"cacheprocess.includepath"
        )

        self.debugoutput = ui.configbool(b"remotefilelog", b"debug")

        self.remotecache = cacheconnection()

    def setstore(self, datastore, historystore, writedata, writehistory):
        self.datastore = datastore
        self.historystore = historystore
        self.writedata = writedata
        self.writehistory = writehistory

    def _connect(self):
        return self.repo.connectionpool.get(self.repo.fallbackpath)

    def request(self, fileids):
        """Takes a list of filename/node pairs and fetches them from the
        server. Files are stored in the local cache.
        A list of nodes that the server couldn't find is returned.
        If the connection fails, an exception is raised.
        """
        if not self.remotecache.connected:
            self.connect()
        cache = self.remotecache
        writedata = self.writedata

        repo = self.repo
        total = len(fileids)
        request = b"get\n%d\n" % total
        idmap = {}
        reponame = repo.name
        for file, id in fileids:
            fullid = getcachekey(reponame, file, id)
            if self.cacheprocesspasspath:
                request += file + b'\0'
            request += fullid + b"\n"
            idmap[fullid] = file

        cache.request(request)

        progress = self.ui.makeprogress(_(b'downloading'), total=total)
        progress.update(0)

        missed = []
        while True:
            missingid = cache.receiveline()
            if not missingid:
                missedset = set(missed)
                for missingid in idmap:
                    if not missingid in missedset:
                        missed.append(missingid)
                self.ui.warn(
                    _(
                        b"warning: cache connection closed early - "
                        + b"falling back to server\n"
                    )
                )
                break
            if missingid == b"0":
                break
            if missingid.startswith(b"_hits_"):
                # receive progress reports
                parts = missingid.split(b"_")
                progress.increment(int(parts[2]))
                continue

            missed.append(missingid)

        global fetchmisses
        fetchmisses += len(missed)

        fromcache = total - len(missed)
        progress.update(fromcache, total=total)
        self.ui.log(
            b"remotefilelog",
            b"remote cache hit rate is %r of %r\n",
            fromcache,
            total,
            hit=fromcache,
            total=total,
        )

        oldumask = os.umask(0o002)
        try:
            # receive cache misses from master
            if missed:
                # When verbose is true, sshpeer prints 'running ssh...'
                # to stdout, which can interfere with some command
                # outputs
                verbose = self.ui.verbose
                self.ui.verbose = False
                try:
                    with self._connect() as conn:
                        remote = conn.peer
                        if remote.capable(
                            constants.NETWORK_CAP_LEGACY_SSH_GETFILES
                        ):
                            if not isinstance(remote, _sshv1peer):
                                raise error.Abort(
                                    b'remotefilelog requires ssh servers'
                                )
                            step = self.ui.configint(
                                b'remotefilelog', b'getfilesstep'
                            )
                            getfilestype = self.ui.config(
                                b'remotefilelog', b'getfilestype'
                            )
                            if getfilestype == b'threaded':
                                _getfiles = _getfiles_threaded
                            else:
                                _getfiles = _getfiles_optimistic
                            _getfiles(
                                remote,
                                self.receivemissing,
                                progress.increment,
                                missed,
                                idmap,
                                step,
                            )
                        elif remote.capable(b"x_rfl_getfile"):
                            if remote.capable(b'batch'):
                                batchdefault = 100
                            else:
                                batchdefault = 10
                            batchsize = self.ui.configint(
                                b'remotefilelog', b'batchsize', batchdefault
                            )
                            self.ui.debug(
                                b'requesting %d files from '
                                b'remotefilelog server...\n' % len(missed)
                            )
                            _getfilesbatch(
                                remote,
                                self.receivemissing,
                                progress.increment,
                                missed,
                                idmap,
                                batchsize,
                            )
                        else:
                            raise error.Abort(
                                b"configured remotefilelog server"
                                b" does not support remotefilelog"
                            )

                    self.ui.log(
                        b"remotefilefetchlog",
                        b"Success\n",
                        fetched_files=progress.pos - fromcache,
                        total_to_fetch=total - fromcache,
                    )
                except Exception:
                    self.ui.log(
                        b"remotefilefetchlog",
                        b"Fail\n",
                        fetched_files=progress.pos - fromcache,
                        total_to_fetch=total - fromcache,
                    )
                    raise
                finally:
                    self.ui.verbose = verbose
                # send to memcache
                request = b"set\n%d\n%s\n" % (len(missed), b"\n".join(missed))
                cache.request(request)

            progress.complete()

            # mark ourselves as a user of this cache
            writedata.markrepo(self.repo.path)
        finally:
            os.umask(oldumask)

    def receivemissing(self, pipe, filename, node):
        line = pipe.readline()[:-1]
        if not line:
            raise error.ResponseError(
                _(b"error downloading file contents:"),
                _(b"connection closed early"),
            )
        size = int(line)
        data = pipe.read(size)
        if len(data) != size:
            raise error.ResponseError(
                _(b"error downloading file contents:"),
                _(b"only received %s of %s bytes") % (len(data), size),
            )

        self.writedata.addremotefilelognode(
            filename, bin(node), zlib.decompress(data)
        )

    def connect(self):
        if self.cacheprocess:
            cmd = b"%s %s" % (self.cacheprocess, self.writedata._path)
            self.remotecache.connect(cmd)
        else:
            # If no cache process is specified, we fake one that always
            # returns cache misses.  This enables tests to run easily
            # and may eventually allow us to be a drop in replacement
            # for the largefiles extension.
            class simplecache(object):
                def __init__(self):
                    self.missingids = []
                    self.connected = True

                def close(self):
                    pass

                def request(self, value, flush=True):
                    lines = value.split(b"\n")
                    if lines[0] != b"get":
                        return
                    self.missingids = lines[2:-1]
                    self.missingids.append(b'0')

                def receiveline(self):
                    if len(self.missingids) > 0:
                        return self.missingids.pop(0)
                    return None

            self.remotecache = simplecache()

    def close(self):
        if fetches:
            msg = (
                b"%d files fetched over %d fetches - "
                + b"(%d misses, %0.2f%% hit ratio) over %0.2fs\n"
            ) % (
                fetched,
                fetches,
                fetchmisses,
                float(fetched - fetchmisses) / float(fetched) * 100.0,
                fetchcost,
            )
            if self.debugoutput:
                self.ui.warn(msg)
            self.ui.log(
                b"remotefilelog.prefetch",
                msg.replace(b"%", b"%%"),
                remotefilelogfetched=fetched,
                remotefilelogfetches=fetches,
                remotefilelogfetchmisses=fetchmisses,
                remotefilelogfetchtime=fetchcost * 1000,
            )

        if self.remotecache.connected:
            self.remotecache.close()

    def prefetch(
        self, fileids, force=False, fetchdata=True, fetchhistory=False
    ):
        """downloads the given file versions to the cache"""
        repo = self.repo
        idstocheck = []
        for file, id in fileids:
            # hack
            # - we don't use .hgtags
            # - workingctx produces ids with length 42,
            #   which we skip since they aren't in any cache
            if (
                file == b'.hgtags'
                or len(id) == 42
                or not repo.shallowmatch(file)
            ):
                continue

            idstocheck.append((file, bin(id)))

        datastore = self.datastore
        historystore = self.historystore
        if force:
            datastore = contentstore.unioncontentstore(*repo.shareddatastores)
            historystore = metadatastore.unionmetadatastore(
                *repo.sharedhistorystores
            )

        missingids = set()
        if fetchdata:
            missingids.update(datastore.getmissing(idstocheck))
        if fetchhistory:
            missingids.update(historystore.getmissing(idstocheck))

        # partition missing nodes into nullid and not-nullid so we can
        # warn about this filtering potentially shadowing bugs.
        nullids = len(
            [None for unused, id in missingids if id == self.repo.nullid]
        )
        if nullids:
            missingids = [
                (f, id) for f, id in missingids if id != self.repo.nullid
            ]
            repo.ui.develwarn(
                (
                    b'remotefilelog not fetching %d null revs'
                    b' - this is likely hiding bugs' % nullids
                ),
                config=b'remotefilelog-ext',
            )
        if missingids:
            global fetches, fetched, fetchcost
            fetches += 1

            # We want to be able to detect excess individual file downloads, so
            # let's log that information for debugging.
            if fetches >= 15 and fetches < 18:
                if fetches == 15:
                    fetchwarning = self.ui.config(
                        b'remotefilelog', b'fetchwarning'
                    )
                    if fetchwarning:
                        self.ui.warn(fetchwarning + b'\n')
                self.logstacktrace()
            missingids = [(file, hex(id)) for file, id in sorted(missingids)]
            fetched += len(missingids)
            start = time.time()
            missingids = self.request(missingids)
            if missingids:
                raise error.Abort(
                    _(b"unable to download %d files") % len(missingids)
                )
            fetchcost += time.time() - start
            self._lfsprefetch(fileids)

    def _lfsprefetch(self, fileids):
        if not _lfsmod or not util.safehasattr(
            self.repo.svfs, b'lfslocalblobstore'
        ):
            return
        if not _lfsmod.wrapper.candownload(self.repo):
            return
        pointers = []
        store = self.repo.svfs.lfslocalblobstore
        for file, id in fileids:
            node = bin(id)
            rlog = self.repo.file(file)
            if rlog.flags(node) & revlog.REVIDX_EXTSTORED:
                text = rlog.rawdata(node)
                p = _lfsmod.pointer.deserialize(text)
                oid = p.oid()
                if not store.has(oid):
                    pointers.append(p)
        if len(pointers) > 0:
            self.repo.svfs.lfsremoteblobstore.readbatch(pointers, store)
            assert all(store.has(p.oid()) for p in pointers)

    def logstacktrace(self):
        import traceback

        self.ui.log(
            b'remotefilelog',
            b'excess remotefilelog fetching:\n%s\n',
            b''.join(pycompat.sysbytes(s) for s in traceback.format_stack()),
        )
