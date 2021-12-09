# blobstore.py - local and remote (speaking Git-LFS protocol) blob storages
#
# Copyright 2017 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import contextlib
import errno
import hashlib
import json
import os
import re
import socket

from mercurial.i18n import _
from mercurial.pycompat import getattr
from mercurial.node import hex

from mercurial import (
    encoding,
    error,
    httpconnection as httpconnectionmod,
    pathutil,
    pycompat,
    url as urlmod,
    util,
    vfs as vfsmod,
    worker,
)

from mercurial.utils import (
    stringutil,
    urlutil,
)

from ..largefiles import lfutil

# 64 bytes for SHA256
_lfsre = re.compile(br'\A[a-f0-9]{64}\Z')


class lfsvfs(vfsmod.vfs):
    def join(self, path):
        """split the path at first two characters, like: XX/XXXXX..."""
        if not _lfsre.match(path):
            raise error.ProgrammingError(b'unexpected lfs path: %s' % path)
        return super(lfsvfs, self).join(path[0:2], path[2:])

    def walk(self, path=None, onerror=None):
        """Yield (dirpath, [], oids) tuple for blobs under path

        Oids only exist in the root of this vfs, so dirpath is always ''.
        """
        root = os.path.normpath(self.base)
        # when dirpath == root, dirpath[prefixlen:] becomes empty
        # because len(dirpath) < prefixlen.
        prefixlen = len(pathutil.normasprefix(root))
        oids = []

        for dirpath, dirs, files in os.walk(
            self.reljoin(self.base, path or b''), onerror=onerror
        ):
            dirpath = dirpath[prefixlen:]

            # Silently skip unexpected files and directories
            if len(dirpath) == 2:
                oids.extend(
                    [dirpath + f for f in files if _lfsre.match(dirpath + f)]
                )

        yield (b'', [], oids)


class nullvfs(lfsvfs):
    def __init__(self):
        pass

    def exists(self, oid):
        return False

    def read(self, oid):
        # store.read() calls into here if the blob doesn't exist in its
        # self.vfs.  Raise the same error as a normal vfs when asked to read a
        # file that doesn't exist.  The only difference is the full file path
        # isn't available in the error.
        raise IOError(
            errno.ENOENT,
            pycompat.sysstr(b'%s: No such file or directory' % oid),
        )

    def walk(self, path=None, onerror=None):
        return (b'', [], [])

    def write(self, oid, data):
        pass


class lfsuploadfile(httpconnectionmod.httpsendfile):
    """a file-like object that supports keepalive."""

    def __init__(self, ui, filename):
        super(lfsuploadfile, self).__init__(ui, filename, b'rb')
        self.read = self._data.read

    def _makeprogress(self):
        return None  # progress is handled by the worker client


class local(object):
    """Local blobstore for large file contents.

    This blobstore is used both as a cache and as a staging area for large blobs
    to be uploaded to the remote blobstore.
    """

    def __init__(self, repo):
        fullpath = repo.svfs.join(b'lfs/objects')
        self.vfs = lfsvfs(fullpath)

        if repo.ui.configbool(b'experimental', b'lfs.disableusercache'):
            self.cachevfs = nullvfs()
        else:
            usercache = lfutil._usercachedir(repo.ui, b'lfs')
            self.cachevfs = lfsvfs(usercache)
        self.ui = repo.ui

    def open(self, oid):
        """Open a read-only file descriptor to the named blob, in either the
        usercache or the local store."""
        return open(self.path(oid), 'rb')

    def path(self, oid):
        """Build the path for the given blob ``oid``.

        If the blob exists locally, the path may point to either the usercache
        or the local store.  If it doesn't, it will point to the local store.
        This is meant for situations where existing code that isn't LFS aware
        needs to open a blob.  Generally, prefer the ``open`` method on this
        class.
        """
        # The usercache is the most likely place to hold the file.  Commit will
        # write to both it and the local store, as will anything that downloads
        # the blobs.  However, things like clone without an update won't
        # populate the local store.  For an init + push of a local clone,
        # the usercache is the only place it _could_ be.  If not present, the
        # missing file msg here will indicate the local repo, not the usercache.
        if self.cachevfs.exists(oid):
            return self.cachevfs.join(oid)

        return self.vfs.join(oid)

    def download(self, oid, src, content_length):
        """Read the blob from the remote source in chunks, verify the content,
        and write to this local blobstore."""
        sha256 = hashlib.sha256()
        size = 0

        with self.vfs(oid, b'wb', atomictemp=True) as fp:
            for chunk in util.filechunkiter(src, size=1048576):
                fp.write(chunk)
                sha256.update(chunk)
                size += len(chunk)

            # If the server advertised a length longer than what we actually
            # received, then we should expect that the server crashed while
            # producing the response (but the server has no way of telling us
            # that), and we really don't need to try to write the response to
            # the localstore, because it's not going to match the expected.
            if content_length is not None and int(content_length) != size:
                msg = (
                    b"Response length (%s) does not match Content-Length "
                    b"header (%d): likely server-side crash"
                )
                raise LfsRemoteError(_(msg) % (size, int(content_length)))

            realoid = hex(sha256.digest())
            if realoid != oid:
                raise LfsCorruptionError(
                    _(b'corrupt remote lfs object: %s') % oid
                )

        self._linktousercache(oid)

    def write(self, oid, data):
        """Write blob to local blobstore.

        This should only be called from the filelog during a commit or similar.
        As such, there is no need to verify the data.  Imports from a remote
        store must use ``download()`` instead."""
        with self.vfs(oid, b'wb', atomictemp=True) as fp:
            fp.write(data)

        self._linktousercache(oid)

    def linkfromusercache(self, oid):
        """Link blobs found in the user cache into this store.

        The server module needs to do this when it lets the client know not to
        upload the blob, to ensure it is always available in this store.
        Normally this is done implicitly when the client reads or writes the
        blob, but that doesn't happen when the server tells the client that it
        already has the blob.
        """
        if not isinstance(self.cachevfs, nullvfs) and not self.vfs.exists(oid):
            self.ui.note(_(b'lfs: found %s in the usercache\n') % oid)
            lfutil.link(self.cachevfs.join(oid), self.vfs.join(oid))

    def _linktousercache(self, oid):
        # XXX: should we verify the content of the cache, and hardlink back to
        # the local store on success, but truncate, write and link on failure?
        if not self.cachevfs.exists(oid) and not isinstance(
            self.cachevfs, nullvfs
        ):
            self.ui.note(_(b'lfs: adding %s to the usercache\n') % oid)
            lfutil.link(self.vfs.join(oid), self.cachevfs.join(oid))

    def read(self, oid, verify=True):
        """Read blob from local blobstore."""
        if not self.vfs.exists(oid):
            blob = self._read(self.cachevfs, oid, verify)

            # Even if revlog will verify the content, it needs to be verified
            # now before making the hardlink to avoid propagating corrupt blobs.
            # Don't abort if corruption is detected, because `hg verify` will
            # give more useful info about the corruption- simply don't add the
            # hardlink.
            if verify or hex(hashlib.sha256(blob).digest()) == oid:
                self.ui.note(_(b'lfs: found %s in the usercache\n') % oid)
                lfutil.link(self.cachevfs.join(oid), self.vfs.join(oid))
        else:
            self.ui.note(_(b'lfs: found %s in the local lfs store\n') % oid)
            blob = self._read(self.vfs, oid, verify)
        return blob

    def _read(self, vfs, oid, verify):
        """Read blob (after verifying) from the given store"""
        blob = vfs.read(oid)
        if verify:
            _verify(oid, blob)
        return blob

    def verify(self, oid):
        """Indicate whether or not the hash of the underlying file matches its
        name."""
        sha256 = hashlib.sha256()

        with self.open(oid) as fp:
            for chunk in util.filechunkiter(fp, size=1048576):
                sha256.update(chunk)

        return oid == hex(sha256.digest())

    def has(self, oid):
        """Returns True if the local blobstore contains the requested blob,
        False otherwise."""
        return self.cachevfs.exists(oid) or self.vfs.exists(oid)


def _urlerrorreason(urlerror):
    """Create a friendly message for the given URLError to be used in an
    LfsRemoteError message.
    """
    inst = urlerror

    if isinstance(urlerror.reason, Exception):
        inst = urlerror.reason

    if util.safehasattr(inst, b'reason'):
        try:  # usually it is in the form (errno, strerror)
            reason = inst.reason.args[1]
        except (AttributeError, IndexError):
            # it might be anything, for example a string
            reason = inst.reason
        if isinstance(reason, pycompat.unicode):
            # SSLError of Python 2.7.9 contains a unicode
            reason = encoding.unitolocal(reason)
        return reason
    elif getattr(inst, "strerror", None):
        return encoding.strtolocal(inst.strerror)
    else:
        return stringutil.forcebytestr(urlerror)


class lfsauthhandler(util.urlreq.basehandler):
    handler_order = 480  # Before HTTPDigestAuthHandler (== 490)

    def http_error_401(self, req, fp, code, msg, headers):
        """Enforces that any authentication performed is HTTP Basic
        Authentication.  No authentication is also acceptable.
        """
        authreq = headers.get('www-authenticate', None)
        if authreq:
            scheme = authreq.split()[0]

            if scheme.lower() != 'basic':
                msg = _(b'the server must support Basic Authentication')
                raise util.urlerr.httperror(
                    req.get_full_url(),
                    code,
                    encoding.strfromlocal(msg),
                    headers,
                    fp,
                )
        return None


class _gitlfsremote(object):
    def __init__(self, repo, url):
        ui = repo.ui
        self.ui = ui
        baseurl, authinfo = url.authinfo()
        self.baseurl = baseurl.rstrip(b'/')
        useragent = repo.ui.config(b'experimental', b'lfs.user-agent')
        if not useragent:
            useragent = b'git-lfs/2.3.4 (Mercurial %s)' % util.version()
        self.urlopener = urlmod.opener(ui, authinfo, useragent)
        self.urlopener.add_handler(lfsauthhandler())
        self.retry = ui.configint(b'lfs', b'retry')

    def writebatch(self, pointers, fromstore):
        """Batch upload from local to remote blobstore."""
        self._batch(_deduplicate(pointers), fromstore, b'upload')

    def readbatch(self, pointers, tostore):
        """Batch download from remote to local blostore."""
        self._batch(_deduplicate(pointers), tostore, b'download')

    def _batchrequest(self, pointers, action):
        """Get metadata about objects pointed by pointers for given action

        Return decoded JSON object like {'objects': [{'oid': '', 'size': 1}]}
        See https://github.com/git-lfs/git-lfs/blob/master/docs/api/batch.md
        """
        objects = [
            {'oid': pycompat.strurl(p.oid()), 'size': p.size()}
            for p in pointers
        ]
        requestdata = pycompat.bytesurl(
            json.dumps(
                {
                    'objects': objects,
                    'operation': pycompat.strurl(action),
                }
            )
        )
        url = b'%s/objects/batch' % self.baseurl
        batchreq = util.urlreq.request(pycompat.strurl(url), data=requestdata)
        batchreq.add_header('Accept', 'application/vnd.git-lfs+json')
        batchreq.add_header('Content-Type', 'application/vnd.git-lfs+json')
        try:
            with contextlib.closing(self.urlopener.open(batchreq)) as rsp:
                rawjson = rsp.read()
        except util.urlerr.httperror as ex:
            hints = {
                400: _(
                    b'check that lfs serving is enabled on %s and "%s" is '
                    b'supported'
                )
                % (self.baseurl, action),
                404: _(b'the "lfs.url" config may be used to override %s')
                % self.baseurl,
            }
            hint = hints.get(ex.code, _(b'api=%s, action=%s') % (url, action))
            raise LfsRemoteError(
                _(b'LFS HTTP error: %s') % stringutil.forcebytestr(ex),
                hint=hint,
            )
        except util.urlerr.urlerror as ex:
            hint = (
                _(b'the "lfs.url" config may be used to override %s')
                % self.baseurl
            )
            raise LfsRemoteError(
                _(b'LFS error: %s') % _urlerrorreason(ex), hint=hint
            )
        try:
            response = pycompat.json_loads(rawjson)
        except ValueError:
            raise LfsRemoteError(
                _(b'LFS server returns invalid JSON: %s')
                % rawjson.encode("utf-8")
            )

        if self.ui.debugflag:
            self.ui.debug(b'Status: %d\n' % rsp.status)
            # lfs-test-server and hg serve return headers in different order
            headers = pycompat.bytestr(rsp.info()).strip()
            self.ui.debug(b'%s\n' % b'\n'.join(sorted(headers.splitlines())))

            if 'objects' in response:
                response['objects'] = sorted(
                    response['objects'], key=lambda p: p['oid']
                )
            self.ui.debug(
                b'%s\n'
                % pycompat.bytesurl(
                    json.dumps(
                        response,
                        indent=2,
                        separators=('', ': '),
                        sort_keys=True,
                    )
                )
            )

        def encodestr(x):
            if isinstance(x, pycompat.unicode):
                return x.encode('utf-8')
            return x

        return pycompat.rapply(encodestr, response)

    def _checkforservererror(self, pointers, responses, action):
        """Scans errors from objects

        Raises LfsRemoteError if any objects have an error"""
        for response in responses:
            # The server should return 404 when objects cannot be found. Some
            # server implementation (ex. lfs-test-server)  does not set "error"
            # but just removes "download" from "actions". Treat that case
            # as the same as 404 error.
            if b'error' not in response:
                if action == b'download' and action not in response.get(
                    b'actions', []
                ):
                    code = 404
                else:
                    continue
            else:
                # An error dict without a code doesn't make much sense, so
                # treat as a server error.
                code = response.get(b'error').get(b'code', 500)

            ptrmap = {p.oid(): p for p in pointers}
            p = ptrmap.get(response[b'oid'], None)
            if p:
                filename = getattr(p, 'filename', b'unknown')
                errors = {
                    404: b'The object does not exist',
                    410: b'The object was removed by the owner',
                    422: b'Validation error',
                    500: b'Internal server error',
                }
                msg = errors.get(code, b'status code %d' % code)
                raise LfsRemoteError(
                    _(b'LFS server error for "%s": %s') % (filename, msg)
                )
            else:
                raise LfsRemoteError(
                    _(b'LFS server error. Unsolicited response for oid %s')
                    % response[b'oid']
                )

    def _extractobjects(self, response, pointers, action):
        """extract objects from response of the batch API

        response: parsed JSON object returned by batch API
        return response['objects'] filtered by action
        raise if any object has an error
        """
        # Scan errors from objects - fail early
        objects = response.get(b'objects', [])
        self._checkforservererror(pointers, objects, action)

        # Filter objects with given action. Practically, this skips uploading
        # objects which exist in the server.
        filteredobjects = [
            o for o in objects if action in o.get(b'actions', [])
        ]

        return filteredobjects

    def _basictransfer(self, obj, action, localstore):
        """Download or upload a single object using basic transfer protocol

        obj: dict, an object description returned by batch API
        action: string, one of ['upload', 'download']
        localstore: blobstore.local

        See https://github.com/git-lfs/git-lfs/blob/master/docs/api/\
        basic-transfers.md
        """
        oid = obj[b'oid']
        href = obj[b'actions'][action].get(b'href')
        headers = obj[b'actions'][action].get(b'header', {}).items()

        request = util.urlreq.request(pycompat.strurl(href))
        if action == b'upload':
            # If uploading blobs, read data from local blobstore.
            if not localstore.verify(oid):
                raise error.Abort(
                    _(b'detected corrupt lfs object: %s') % oid,
                    hint=_(b'run hg verify'),
                )

        for k, v in headers:
            request.add_header(pycompat.strurl(k), pycompat.strurl(v))

        try:
            if action == b'upload':
                request.data = lfsuploadfile(self.ui, localstore.path(oid))
                request.get_method = lambda: 'PUT'
                request.add_header('Content-Type', 'application/octet-stream')
                request.add_header('Content-Length', request.data.length)

            with contextlib.closing(self.urlopener.open(request)) as res:
                contentlength = res.info().get(b"content-length")
                ui = self.ui  # Shorten debug lines
                if self.ui.debugflag:
                    ui.debug(b'Status: %d\n' % res.status)
                    # lfs-test-server and hg serve return headers in different
                    # order
                    headers = pycompat.bytestr(res.info()).strip()
                    ui.debug(b'%s\n' % b'\n'.join(sorted(headers.splitlines())))

                if action == b'download':
                    # If downloading blobs, store downloaded data to local
                    # blobstore
                    localstore.download(oid, res, contentlength)
                else:
                    blocks = []
                    while True:
                        data = res.read(1048576)
                        if not data:
                            break
                        blocks.append(data)

                    response = b"".join(blocks)
                    if response:
                        ui.debug(b'lfs %s response: %s' % (action, response))
        except util.urlerr.httperror as ex:
            if self.ui.debugflag:
                self.ui.debug(
                    b'%s: %s\n' % (oid, ex.read())
                )  # XXX: also bytes?
            raise LfsRemoteError(
                _(b'LFS HTTP error: %s (oid=%s, action=%s)')
                % (stringutil.forcebytestr(ex), oid, action)
            )
        except util.urlerr.urlerror as ex:
            hint = _(b'attempted connection to %s') % pycompat.bytesurl(
                util.urllibcompat.getfullurl(request)
            )
            raise LfsRemoteError(
                _(b'LFS error: %s') % _urlerrorreason(ex), hint=hint
            )
        finally:
            if request.data:
                request.data.close()

    def _batch(self, pointers, localstore, action):
        if action not in [b'upload', b'download']:
            raise error.ProgrammingError(b'invalid Git-LFS action: %s' % action)

        response = self._batchrequest(pointers, action)
        objects = self._extractobjects(response, pointers, action)
        total = sum(x.get(b'size', 0) for x in objects)
        sizes = {}
        for obj in objects:
            sizes[obj.get(b'oid')] = obj.get(b'size', 0)
        topic = {
            b'upload': _(b'lfs uploading'),
            b'download': _(b'lfs downloading'),
        }[action]
        if len(objects) > 1:
            self.ui.note(
                _(b'lfs: need to transfer %d objects (%s)\n')
                % (len(objects), util.bytecount(total))
            )

        def transfer(chunk):
            for obj in chunk:
                objsize = obj.get(b'size', 0)
                if self.ui.verbose:
                    if action == b'download':
                        msg = _(b'lfs: downloading %s (%s)\n')
                    elif action == b'upload':
                        msg = _(b'lfs: uploading %s (%s)\n')
                    self.ui.note(
                        msg % (obj.get(b'oid'), util.bytecount(objsize))
                    )
                retry = self.retry
                while True:
                    try:
                        self._basictransfer(obj, action, localstore)
                        yield 1, obj.get(b'oid')
                        break
                    except socket.error as ex:
                        if retry > 0:
                            self.ui.note(
                                _(b'lfs: failed: %r (remaining retry %d)\n')
                                % (stringutil.forcebytestr(ex), retry)
                            )
                            retry -= 1
                            continue
                        raise

        # Until https multiplexing gets sorted out
        if self.ui.configbool(b'experimental', b'lfs.worker-enable'):
            oids = worker.worker(
                self.ui,
                0.1,
                transfer,
                (),
                sorted(objects, key=lambda o: o.get(b'oid')),
            )
        else:
            oids = transfer(sorted(objects, key=lambda o: o.get(b'oid')))

        with self.ui.makeprogress(
            topic, unit=_(b"bytes"), total=total
        ) as progress:
            progress.update(0)
            processed = 0
            blobs = 0
            for _one, oid in oids:
                processed += sizes[oid]
                blobs += 1
                progress.update(processed)
                self.ui.note(_(b'lfs: processed: %s\n') % oid)

        if blobs > 0:
            if action == b'upload':
                self.ui.status(
                    _(b'lfs: uploaded %d files (%s)\n')
                    % (blobs, util.bytecount(processed))
                )
            elif action == b'download':
                self.ui.status(
                    _(b'lfs: downloaded %d files (%s)\n')
                    % (blobs, util.bytecount(processed))
                )

    def __del__(self):
        # copied from mercurial/httppeer.py
        urlopener = getattr(self, 'urlopener', None)
        if urlopener:
            for h in urlopener.handlers:
                h.close()
                getattr(h, "close_all", lambda: None)()


class _dummyremote(object):
    """Dummy store storing blobs to temp directory."""

    def __init__(self, repo, url):
        fullpath = repo.vfs.join(b'lfs', url.path)
        self.vfs = lfsvfs(fullpath)

    def writebatch(self, pointers, fromstore):
        for p in _deduplicate(pointers):
            content = fromstore.read(p.oid(), verify=True)
            with self.vfs(p.oid(), b'wb', atomictemp=True) as fp:
                fp.write(content)

    def readbatch(self, pointers, tostore):
        for p in _deduplicate(pointers):
            with self.vfs(p.oid(), b'rb') as fp:
                tostore.download(p.oid(), fp, None)


class _nullremote(object):
    """Null store storing blobs to /dev/null."""

    def __init__(self, repo, url):
        pass

    def writebatch(self, pointers, fromstore):
        pass

    def readbatch(self, pointers, tostore):
        pass


class _promptremote(object):
    """Prompt user to set lfs.url when accessed."""

    def __init__(self, repo, url):
        pass

    def writebatch(self, pointers, fromstore, ui=None):
        self._prompt()

    def readbatch(self, pointers, tostore, ui=None):
        self._prompt()

    def _prompt(self):
        raise error.Abort(_(b'lfs.url needs to be configured'))


_storemap = {
    b'https': _gitlfsremote,
    b'http': _gitlfsremote,
    b'file': _dummyremote,
    b'null': _nullremote,
    None: _promptremote,
}


def _deduplicate(pointers):
    """Remove any duplicate oids that exist in the list"""
    reduced = util.sortdict()
    for p in pointers:
        reduced[p.oid()] = p
    return reduced.values()


def _verify(oid, content):
    realoid = hex(hashlib.sha256(content).digest())
    if realoid != oid:
        raise LfsCorruptionError(
            _(b'detected corrupt lfs object: %s') % oid,
            hint=_(b'run hg verify'),
        )


def remote(repo, remote=None):
    """remotestore factory. return a store in _storemap depending on config

    If ``lfs.url`` is specified, use that remote endpoint.  Otherwise, try to
    infer the endpoint, based on the remote repository using the same path
    adjustments as git.  As an extension, 'http' is supported as well so that
    ``hg serve`` works out of the box.

    https://github.com/git-lfs/git-lfs/blob/master/docs/api/server-discovery.md
    """
    lfsurl = repo.ui.config(b'lfs', b'url')
    url = urlutil.url(lfsurl or b'')
    if lfsurl is None:
        if remote:
            path = remote
        elif util.safehasattr(repo, b'_subtoppath'):
            # The pull command sets this during the optional update phase, which
            # tells exactly where the pull originated, whether 'paths.default'
            # or explicit.
            path = repo._subtoppath
        else:
            # TODO: investigate 'paths.remote:lfsurl' style path customization,
            # and fall back to inferring from 'paths.remote' if unspecified.
            path = repo.ui.config(b'paths', b'default') or b''

        defaulturl = urlutil.url(path)

        # TODO: support local paths as well.
        # TODO: consider the ssh -> https transformation that git applies
        if defaulturl.scheme in (b'http', b'https'):
            if defaulturl.path and defaulturl.path[:-1] != b'/':
                defaulturl.path += b'/'
            defaulturl.path = (defaulturl.path or b'') + b'.git/info/lfs'

            url = urlutil.url(bytes(defaulturl))
            repo.ui.note(_(b'lfs: assuming remote store: %s\n') % url)

    scheme = url.scheme
    if scheme not in _storemap:
        raise error.Abort(_(b'lfs: unknown url scheme: %s') % scheme)
    return _storemap[scheme](repo, url)


class LfsRemoteError(error.StorageError):
    pass


class LfsCorruptionError(error.Abort):
    """Raised when a corrupt blob is detected, aborting an operation

    It exists to allow specialized handling on the server side."""
