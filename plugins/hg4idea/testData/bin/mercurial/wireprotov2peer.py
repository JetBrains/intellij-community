# wireprotov2peer.py - client side code for wire protocol version 2
#
# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import threading

from .i18n import _
from . import (
    encoding,
    error,
    pycompat,
    sslutil,
    url as urlmod,
    util,
    wireprotoframing,
    wireprototypes,
)
from .utils import cborutil


def formatrichmessage(atoms):
    """Format an encoded message from the framing protocol."""

    chunks = []

    for atom in atoms:
        msg = _(atom[b'msg'])

        if b'args' in atom:
            msg = msg % tuple(atom[b'args'])

        chunks.append(msg)

    return b''.join(chunks)


SUPPORTED_REDIRECT_PROTOCOLS = {
    b'http',
    b'https',
}

SUPPORTED_CONTENT_HASHES = {
    b'sha1',
    b'sha256',
}


def redirecttargetsupported(ui, target):
    """Determine whether a redirect target entry is supported.

    ``target`` should come from the capabilities data structure emitted by
    the server.
    """
    if target.get(b'protocol') not in SUPPORTED_REDIRECT_PROTOCOLS:
        ui.note(
            _(b'(remote redirect target %s uses unsupported protocol: %s)\n')
            % (target[b'name'], target.get(b'protocol', b''))
        )
        return False

    if target.get(b'snirequired') and not sslutil.hassni:
        ui.note(
            _(b'(redirect target %s requires SNI, which is unsupported)\n')
            % target[b'name']
        )
        return False

    if b'tlsversions' in target:
        tlsversions = set(target[b'tlsversions'])
        supported = set()

        for v in sslutil.supportedprotocols:
            assert v.startswith(b'tls')
            supported.add(v[3:])

        if not tlsversions & supported:
            ui.note(
                _(
                    b'(remote redirect target %s requires unsupported TLS '
                    b'versions: %s)\n'
                )
                % (target[b'name'], b', '.join(sorted(tlsversions)))
            )
            return False

    ui.note(_(b'(remote redirect target %s is compatible)\n') % target[b'name'])

    return True


def supportedredirects(ui, apidescriptor):
    """Resolve the "redirect" command request key given an API descriptor.

    Given an API descriptor returned by the server, returns a data structure
    that can be used in hte "redirect" field of command requests to advertise
    support for compatible redirect targets.

    Returns None if no redirect targets are remotely advertised or if none are
    supported.
    """
    if not apidescriptor or b'redirect' not in apidescriptor:
        return None

    targets = [
        t[b'name']
        for t in apidescriptor[b'redirect'][b'targets']
        if redirecttargetsupported(ui, t)
    ]

    hashes = [
        h
        for h in apidescriptor[b'redirect'][b'hashes']
        if h in SUPPORTED_CONTENT_HASHES
    ]

    return {
        b'targets': targets,
        b'hashes': hashes,
    }


class commandresponse(object):
    """Represents the response to a command request.

    Instances track the state of the command and hold its results.

    An external entity is required to update the state of the object when
    events occur.
    """

    def __init__(self, requestid, command, fromredirect=False):
        self.requestid = requestid
        self.command = command
        self.fromredirect = fromredirect

        # Whether all remote input related to this command has been
        # received.
        self._inputcomplete = False

        # We have a lock that is acquired when important object state is
        # mutated. This is to prevent race conditions between 1 thread
        # sending us new data and another consuming it.
        self._lock = threading.RLock()

        # An event is set when state of the object changes. This event
        # is waited on by the generator emitting objects.
        self._serviceable = threading.Event()

        self._pendingevents = []
        self._pendingerror = None
        self._decoder = cborutil.bufferingdecoder()
        self._seeninitial = False
        self._redirect = None

    def _oninputcomplete(self):
        with self._lock:
            self._inputcomplete = True
            self._serviceable.set()

    def _onresponsedata(self, data):
        available, readcount, wanted = self._decoder.decode(data)

        if not available:
            return

        with self._lock:
            for o in self._decoder.getavailable():
                if not self._seeninitial and not self.fromredirect:
                    self._handleinitial(o)
                    continue

                # We should never see an object after a content redirect,
                # as the spec says the main status object containing the
                # content redirect is the only object in the stream. Fail
                # if we see a misbehaving server.
                if self._redirect:
                    raise error.Abort(
                        _(
                            b'received unexpected response data '
                            b'after content redirect; the remote is '
                            b'buggy'
                        )
                    )

                self._pendingevents.append(o)

            self._serviceable.set()

    def _onerror(self, e):
        self._pendingerror = e

        with self._lock:
            self._serviceable.set()

    def _handleinitial(self, o):
        self._seeninitial = True
        if o[b'status'] == b'ok':
            return

        elif o[b'status'] == b'redirect':
            l = o[b'location']
            self._redirect = wireprototypes.alternatelocationresponse(
                url=l[b'url'],
                mediatype=l[b'mediatype'],
                size=l.get(b'size'),
                fullhashes=l.get(b'fullhashes'),
                fullhashseed=l.get(b'fullhashseed'),
                serverdercerts=l.get(b'serverdercerts'),
                servercadercerts=l.get(b'servercadercerts'),
            )
            return

        atoms = [{b'msg': o[b'error'][b'message']}]
        if b'args' in o[b'error']:
            atoms[0][b'args'] = o[b'error'][b'args']

        raise error.RepoError(formatrichmessage(atoms))

    def objects(self):
        """Obtained decoded objects from this response.

        This is a generator of data structures that were decoded from the
        command response.

        Obtaining the next member of the generator may block due to waiting
        on external data to become available.

        If the server encountered an error in the middle of serving the data
        or if another error occurred, an exception may be raised when
        advancing the generator.
        """
        while True:
            # TODO this can infinite loop if self._inputcomplete is never
            # set. We likely want to tie the lifetime of this object/state
            # to that of the background thread receiving frames and updating
            # our state.
            self._serviceable.wait(1.0)

            if self._pendingerror:
                raise self._pendingerror

            with self._lock:
                self._serviceable.clear()

                # Make copies because objects could be mutated during
                # iteration.
                stop = self._inputcomplete
                pending = list(self._pendingevents)
                self._pendingevents[:] = []

            for o in pending:
                yield o

            if stop:
                break


class clienthandler(object):
    """Object to handle higher-level client activities.

    The ``clientreactor`` is used to hold low-level state about the frame-based
    protocol, such as which requests and streams are active. This type is used
    for higher-level operations, such as reading frames from a socket, exposing
    and managing a higher-level primitive for representing command responses,
    etc. This class is what peers should probably use to bridge wire activity
    with the higher-level peer API.
    """

    def __init__(
        self, ui, clientreactor, opener=None, requestbuilder=util.urlreq.request
    ):
        self._ui = ui
        self._reactor = clientreactor
        self._requests = {}
        self._futures = {}
        self._responses = {}
        self._redirects = []
        self._frameseof = False
        self._opener = opener or urlmod.opener(ui)
        self._requestbuilder = requestbuilder

    def callcommand(self, command, args, f, redirect=None):
        """Register a request to call a command.

        Returns an iterable of frames that should be sent over the wire.
        """
        request, action, meta = self._reactor.callcommand(
            command, args, redirect=redirect
        )

        if action != b'noop':
            raise error.ProgrammingError(b'%s not yet supported' % action)

        rid = request.requestid
        self._requests[rid] = request
        self._futures[rid] = f
        # TODO we need some kind of lifetime on response instances otherwise
        # objects() may deadlock.
        self._responses[rid] = commandresponse(rid, command)

        return iter(())

    def flushcommands(self):
        """Flush all queued commands.

        Returns an iterable of frames that should be sent over the wire.
        """
        action, meta = self._reactor.flushcommands()

        if action != b'sendframes':
            raise error.ProgrammingError(b'%s not yet supported' % action)

        return meta[b'framegen']

    def readdata(self, framefh):
        """Attempt to read data and do work.

        Returns None if no data was read. Presumably this means we're
        done with all read I/O.
        """
        if not self._frameseof:
            frame = wireprotoframing.readframe(framefh)
            if frame is None:
                # TODO tell reactor?
                self._frameseof = True
            else:
                self._ui.debug(b'received %r\n' % frame)
                self._processframe(frame)

        # Also try to read the first redirect.
        if self._redirects:
            if not self._processredirect(*self._redirects[0]):
                self._redirects.pop(0)

        if self._frameseof and not self._redirects:
            return None

        return True

    def _processframe(self, frame):
        """Process a single read frame."""

        action, meta = self._reactor.onframerecv(frame)

        if action == b'error':
            e = error.RepoError(meta[b'message'])

            if frame.requestid in self._responses:
                self._responses[frame.requestid]._oninputcomplete()

            if frame.requestid in self._futures:
                self._futures[frame.requestid].set_exception(e)
                del self._futures[frame.requestid]
            else:
                raise e

            return
        elif action == b'noop':
            return
        elif action == b'responsedata':
            # Handled below.
            pass
        else:
            raise error.ProgrammingError(b'action not handled: %s' % action)

        if frame.requestid not in self._requests:
            raise error.ProgrammingError(
                b'received frame for unknown request; this is either a bug in '
                b'the clientreactor not screening for this or this instance was '
                b'never told about this request: %r' % frame
            )

        response = self._responses[frame.requestid]

        if action == b'responsedata':
            # Any failures processing this frame should bubble up to the
            # future tracking the request.
            try:
                self._processresponsedata(frame, meta, response)
            except BaseException as e:
                # If an exception occurs before the future is resolved,
                # fail the future. Otherwise, we stuff the exception on
                # the response object so it can be raised during objects()
                # iteration. If nothing is consuming objects(), we could
                # silently swallow this exception. That's a risk we'll have to
                # take.
                if frame.requestid in self._futures:
                    self._futures[frame.requestid].set_exception(e)
                    del self._futures[frame.requestid]
                    response._oninputcomplete()
                else:
                    response._onerror(e)
        else:
            raise error.ProgrammingError(
                b'unhandled action from clientreactor: %s' % action
            )

    def _processresponsedata(self, frame, meta, response):
        # This can raise. The caller can handle it.
        response._onresponsedata(meta[b'data'])

        # We need to be careful about resolving futures prematurely. If a
        # response is a redirect response, resolving the future before the
        # redirect is processed would result in the consumer seeing an
        # empty stream of objects, since they'd be consuming our
        # response.objects() instead of the redirect's response.objects().
        #
        # Our strategy is to not resolve/finish the request until either
        # EOS occurs or until the initial response object is fully received.

        # Always react to eos.
        if meta[b'eos']:
            response._oninputcomplete()
            del self._requests[frame.requestid]

        # Not EOS but we haven't decoded the initial response object yet.
        # Return and wait for more data.
        elif not response._seeninitial:
            return

        # The specification says no objects should follow the initial/redirect
        # object. So it should be safe to handle the redirect object if one is
        # decoded, without having to wait for EOS.
        if response._redirect:
            self._followredirect(frame.requestid, response._redirect)
            return

        # If the command has a decoder, we wait until all input has been
        # received before resolving the future. Otherwise we resolve the
        # future immediately.
        if frame.requestid not in self._futures:
            return

        if response.command not in COMMAND_DECODERS:
            self._futures[frame.requestid].set_result(response.objects())
            del self._futures[frame.requestid]
        elif response._inputcomplete:
            decoded = COMMAND_DECODERS[response.command](response.objects())
            self._futures[frame.requestid].set_result(decoded)
            del self._futures[frame.requestid]

    def _followredirect(self, requestid, redirect):
        """Called to initiate redirect following for a request."""
        self._ui.note(_(b'(following redirect to %s)\n') % redirect.url)

        # TODO handle framed responses.
        if redirect.mediatype != b'application/mercurial-cbor':
            raise error.Abort(
                _(b'cannot handle redirects for the %s media type')
                % redirect.mediatype
            )

        if redirect.fullhashes:
            self._ui.warn(
                _(
                    b'(support for validating hashes on content '
                    b'redirects not supported)\n'
                )
            )

        if redirect.serverdercerts or redirect.servercadercerts:
            self._ui.warn(
                _(
                    b'(support for pinning server certificates on '
                    b'content redirects not supported)\n'
                )
            )

        headers = {
            'Accept': redirect.mediatype,
        }

        req = self._requestbuilder(pycompat.strurl(redirect.url), None, headers)

        try:
            res = self._opener.open(req)
        except util.urlerr.httperror as e:
            if e.code == 401:
                raise error.Abort(_(b'authorization failed'))
            raise
        except util.httplib.HTTPException as e:
            self._ui.debug(b'http error requesting %s\n' % req.get_full_url())
            self._ui.traceback()
            raise IOError(None, e)

        urlmod.wrapresponse(res)

        # The existing response object is associated with frame data. Rather
        # than try to normalize its state, just create a new object.
        oldresponse = self._responses[requestid]
        self._responses[requestid] = commandresponse(
            requestid, oldresponse.command, fromredirect=True
        )

        self._redirects.append((requestid, res))

    def _processredirect(self, rid, res):
        """Called to continue processing a response from a redirect.

        Returns a bool indicating if the redirect is still serviceable.
        """
        response = self._responses[rid]

        try:
            data = res.read(32768)
            response._onresponsedata(data)

            # We're at end of stream.
            if not data:
                response._oninputcomplete()

            if rid not in self._futures:
                return bool(data)

            if response.command not in COMMAND_DECODERS:
                self._futures[rid].set_result(response.objects())
                del self._futures[rid]
            elif response._inputcomplete:
                decoded = COMMAND_DECODERS[response.command](response.objects())
                self._futures[rid].set_result(decoded)
                del self._futures[rid]

            return bool(data)

        except BaseException as e:
            self._futures[rid].set_exception(e)
            del self._futures[rid]
            response._oninputcomplete()
            return False


def decodebranchmap(objs):
    # Response should be a single CBOR map of branch name to array of nodes.
    bm = next(objs)

    return {encoding.tolocal(k): v for k, v in bm.items()}


def decodeheads(objs):
    # Array of node bytestrings.
    return next(objs)


def decodeknown(objs):
    # Bytestring where each byte is a 0 or 1.
    raw = next(objs)

    return [True if raw[i : i + 1] == b'1' else False for i in range(len(raw))]


def decodelistkeys(objs):
    # Map with bytestring keys and values.
    return next(objs)


def decodelookup(objs):
    return next(objs)


def decodepushkey(objs):
    return next(objs)


COMMAND_DECODERS = {
    b'branchmap': decodebranchmap,
    b'heads': decodeheads,
    b'known': decodeknown,
    b'listkeys': decodelistkeys,
    b'lookup': decodelookup,
    b'pushkey': decodepushkey,
}
