# wireprotolfsserver.py - lfs protocol server side implementation
#
# Copyright 2018 Matt Harbison <matt_harbison@yahoo.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import datetime
import errno
import json
import traceback

from mercurial.hgweb import common as hgwebcommon

from mercurial import (
    exthelper,
    pycompat,
    util,
    wireprotoserver,
)

from . import blobstore

HTTP_OK = hgwebcommon.HTTP_OK
HTTP_CREATED = hgwebcommon.HTTP_CREATED
HTTP_BAD_REQUEST = hgwebcommon.HTTP_BAD_REQUEST
HTTP_NOT_FOUND = hgwebcommon.HTTP_NOT_FOUND
HTTP_METHOD_NOT_ALLOWED = hgwebcommon.HTTP_METHOD_NOT_ALLOWED
HTTP_NOT_ACCEPTABLE = hgwebcommon.HTTP_NOT_ACCEPTABLE
HTTP_UNSUPPORTED_MEDIA_TYPE = hgwebcommon.HTTP_UNSUPPORTED_MEDIA_TYPE

eh = exthelper.exthelper()


@eh.wrapfunction(wireprotoserver, b'handlewsgirequest')
def handlewsgirequest(orig, rctx, req, res, checkperm):
    """Wrap wireprotoserver.handlewsgirequest() to possibly process an LFS
    request if it is left unprocessed by the wrapped method.
    """
    if orig(rctx, req, res, checkperm):
        return True

    if not rctx.repo.ui.configbool(b'experimental', b'lfs.serve'):
        return False

    if not util.safehasattr(rctx.repo.svfs, 'lfslocalblobstore'):
        return False

    if not req.dispatchpath:
        return False

    try:
        if req.dispatchpath == b'.git/info/lfs/objects/batch':
            checkperm(rctx, req, b'pull')
            return _processbatchrequest(rctx.repo, req, res)
        # TODO: reserve and use a path in the proposed http wireprotocol /api/
        #       namespace?
        elif req.dispatchpath.startswith(b'.hg/lfs/objects'):
            return _processbasictransfer(
                rctx.repo, req, res, lambda perm: checkperm(rctx, req, perm)
            )
        return False
    except hgwebcommon.ErrorResponse as e:
        # XXX: copied from the handler surrounding wireprotoserver._callhttp()
        #      in the wrapped function.  Should this be moved back to hgweb to
        #      be a common handler?
        for k, v in e.headers:
            res.headers[k] = v
        res.status = hgwebcommon.statusmessage(e.code, pycompat.bytestr(e))
        res.setbodybytes(b'0\n%s\n' % pycompat.bytestr(e))
        return True


def _sethttperror(res, code, message=None):
    res.status = hgwebcommon.statusmessage(code, message=message)
    res.headers[b'Content-Type'] = b'text/plain; charset=utf-8'
    res.setbodybytes(b'')


def _logexception(req):
    """Write information about the current exception to wsgi.errors."""
    tb = pycompat.sysbytes(traceback.format_exc())
    errorlog = req.rawenv[b'wsgi.errors']

    uri = b''
    if req.apppath:
        uri += req.apppath
    uri += b'/' + req.dispatchpath

    errorlog.write(
        b"Exception happened while processing request '%s':\n%s" % (uri, tb)
    )


def _processbatchrequest(repo, req, res):
    """Handle a request for the Batch API, which is the gateway to granting file
    access.

    https://github.com/git-lfs/git-lfs/blob/master/docs/api/batch.md
    """

    # Mercurial client request:
    #
    #   HOST: localhost:$HGPORT
    #   ACCEPT: application/vnd.git-lfs+json
    #   ACCEPT-ENCODING: identity
    #   USER-AGENT: git-lfs/2.3.4 (Mercurial 4.5.2+1114-f48b9754f04c+20180316)
    #   Content-Length: 125
    #   Content-Type: application/vnd.git-lfs+json
    #
    #   {
    #     "objects": [
    #       {
    #         "oid": "31cf...8e5b"
    #         "size": 12
    #       }
    #     ]
    #     "operation": "upload"
    #  }

    if req.method != b'POST':
        _sethttperror(res, HTTP_METHOD_NOT_ALLOWED)
        return True

    if req.headers[b'Content-Type'] != b'application/vnd.git-lfs+json':
        _sethttperror(res, HTTP_UNSUPPORTED_MEDIA_TYPE)
        return True

    if req.headers[b'Accept'] != b'application/vnd.git-lfs+json':
        _sethttperror(res, HTTP_NOT_ACCEPTABLE)
        return True

    # XXX: specify an encoding?
    lfsreq = pycompat.json_loads(req.bodyfh.read())

    # If no transfer handlers are explicitly requested, 'basic' is assumed.
    if 'basic' not in lfsreq.get('transfers', ['basic']):
        _sethttperror(
            res,
            HTTP_BAD_REQUEST,
            b'Only the basic LFS transfer handler is supported',
        )
        return True

    operation = lfsreq.get('operation')
    operation = pycompat.bytestr(operation)

    if operation not in (b'upload', b'download'):
        _sethttperror(
            res,
            HTTP_BAD_REQUEST,
            b'Unsupported LFS transfer operation: %s' % operation,
        )
        return True

    localstore = repo.svfs.lfslocalblobstore

    objects = [
        p
        for p in _batchresponseobjects(
            req, lfsreq.get('objects', []), operation, localstore
        )
    ]

    rsp = {
        'transfer': 'basic',
        'objects': objects,
    }

    res.status = hgwebcommon.statusmessage(HTTP_OK)
    res.headers[b'Content-Type'] = b'application/vnd.git-lfs+json'
    res.setbodybytes(pycompat.bytestr(json.dumps(rsp)))

    return True


def _batchresponseobjects(req, objects, action, store):
    """Yield one dictionary of attributes for the Batch API response for each
    object in the list.

    req: The parsedrequest for the Batch API request
    objects: The list of objects in the Batch API object request list
    action: 'upload' or 'download'
    store: The local blob store for servicing requests"""

    # Successful lfs-test-server response to solict an upload:
    # {
    #    u'objects': [{
    #       u'size': 12,
    #       u'oid': u'31cf...8e5b',
    #       u'actions': {
    #           u'upload': {
    #               u'href': u'http://localhost:$HGPORT/objects/31cf...8e5b',
    #               u'expires_at': u'0001-01-01T00:00:00Z',
    #               u'header': {
    #                   u'Accept': u'application/vnd.git-lfs'
    #               }
    #           }
    #       }
    #    }]
    # }

    # TODO: Sort out the expires_at/expires_in/authenticated keys.

    for obj in objects:
        # Convert unicode to ASCII to create a filesystem path
        soid = obj.get('oid')
        oid = soid.encode('ascii')
        rsp = {
            'oid': soid,
            'size': obj.get('size'),  # XXX: should this check the local size?
            # 'authenticated': True,
        }

        exists = True
        verifies = False

        # Verify an existing file on the upload request, so that the client is
        # solicited to re-upload if it corrupt locally.  Download requests are
        # also verified, so the error can be flagged in the Batch API response.
        # (Maybe we can use this to short circuit the download for `hg verify`,
        # IFF the client can assert that the remote end is an hg server.)
        # Otherwise, it's potentially overkill on download, since it is also
        # verified as the file is streamed to the caller.
        try:
            verifies = store.verify(oid)
            if verifies and action == b'upload':
                # The client will skip this upload, but make sure it remains
                # available locally.
                store.linkfromusercache(oid)
        except IOError as inst:
            if inst.errno != errno.ENOENT:
                _logexception(req)

                rsp['error'] = {
                    'code': 500,
                    'message': inst.strerror or 'Internal Server Server',
                }
                yield rsp
                continue

            exists = False

        # Items are always listed for downloads.  They are dropped for uploads
        # IFF they already exist locally.
        if action == b'download':
            if not exists:
                rsp['error'] = {
                    'code': 404,
                    'message': "The object does not exist",
                }
                yield rsp
                continue

            elif not verifies:
                rsp['error'] = {
                    'code': 422,  # XXX: is this the right code?
                    'message': "The object is corrupt",
                }
                yield rsp
                continue

        elif verifies:
            yield rsp  # Skip 'actions': already uploaded
            continue

        expiresat = datetime.datetime.now() + datetime.timedelta(minutes=10)

        def _buildheader():
            # The spec doesn't mention the Accept header here, but avoid
            # a gratuitous deviation from lfs-test-server in the test
            # output.
            hdr = {'Accept': 'application/vnd.git-lfs'}

            auth = req.headers.get(b'Authorization', b'')
            if auth.startswith(b'Basic '):
                hdr['Authorization'] = pycompat.strurl(auth)

            return hdr

        rsp['actions'] = {
            '%s'
            % pycompat.strurl(action): {
                'href': pycompat.strurl(
                    b'%s%s/.hg/lfs/objects/%s' % (req.baseurl, req.apppath, oid)
                ),
                # datetime.isoformat() doesn't include the 'Z' suffix
                "expires_at": expiresat.strftime('%Y-%m-%dT%H:%M:%SZ'),
                'header': _buildheader(),
            }
        }

        yield rsp


def _processbasictransfer(repo, req, res, checkperm):
    """Handle a single file upload (PUT) or download (GET) action for the Basic
    Transfer Adapter.

    After determining if the request is for an upload or download, the access
    must be checked by calling ``checkperm()`` with either 'pull' or 'upload'
    before accessing the files.

    https://github.com/git-lfs/git-lfs/blob/master/docs/api/basic-transfers.md
    """

    method = req.method
    oid = req.dispatchparts[-1]
    localstore = repo.svfs.lfslocalblobstore

    if len(req.dispatchparts) != 4:
        _sethttperror(res, HTTP_NOT_FOUND)
        return True

    if method == b'PUT':
        checkperm(b'upload')

        # TODO: verify Content-Type?

        existed = localstore.has(oid)

        # TODO: how to handle timeouts?  The body proxy handles limiting to
        #       Content-Length, but what happens if a client sends less than it
        #       says it will?

        statusmessage = hgwebcommon.statusmessage
        try:
            localstore.download(oid, req.bodyfh, req.headers[b'Content-Length'])
            res.status = statusmessage(HTTP_OK if existed else HTTP_CREATED)
        except blobstore.LfsCorruptionError:
            _logexception(req)

            # XXX: Is this the right code?
            res.status = statusmessage(422, b'corrupt blob')

        # There's no payload here, but this is the header that lfs-test-server
        # sends back.  This eliminates some gratuitous test output conditionals.
        res.headers[b'Content-Type'] = b'text/plain; charset=utf-8'
        res.setbodybytes(b'')

        return True
    elif method == b'GET':
        checkperm(b'pull')

        res.status = hgwebcommon.statusmessage(HTTP_OK)
        res.headers[b'Content-Type'] = b'application/octet-stream'

        try:
            # TODO: figure out how to send back the file in chunks, instead of
            #       reading the whole thing.  (Also figure out how to send back
            #       an error status if an IOError occurs after a partial write
            #       in that case.  Here, everything is read before starting.)
            res.setbodybytes(localstore.read(oid))
        except blobstore.LfsCorruptionError:
            _logexception(req)

            # XXX: Is this the right code?
            res.status = hgwebcommon.statusmessage(422, b'corrupt blob')
            res.setbodybytes(b'')

        return True
    else:
        _sethttperror(
            res,
            HTTP_METHOD_NOT_ALLOWED,
            message=b'Unsupported LFS transfer method: %s' % method,
        )
        return True
