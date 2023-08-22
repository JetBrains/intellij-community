# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from .node import (
    bin,
    hex,
)
from .i18n import _
from .pycompat import getattr
from .thirdparty import attr
from . import (
    error,
    util,
)
from .interfaces import util as interfaceutil
from .utils import compression

# Names of the SSH protocol implementations.
SSHV1 = b'ssh-v1'
# These are advertised over the wire. Increment the counters at the end
# to reflect BC breakages.
SSHV2 = b'exp-ssh-v2-0003'
HTTP_WIREPROTO_V2 = b'exp-http-v2-0003'

NARROWCAP = b'exp-narrow-1'
ELLIPSESCAP1 = b'exp-ellipses-1'
ELLIPSESCAP = b'exp-ellipses-2'
SUPPORTED_ELLIPSESCAP = (ELLIPSESCAP1, ELLIPSESCAP)

# All available wire protocol transports.
TRANSPORTS = {
    SSHV1: {
        b'transport': b'ssh',
        b'version': 1,
    },
    SSHV2: {
        b'transport': b'ssh',
        # TODO mark as version 2 once all commands are implemented.
        b'version': 1,
    },
    b'http-v1': {
        b'transport': b'http',
        b'version': 1,
    },
    HTTP_WIREPROTO_V2: {
        b'transport': b'http',
        b'version': 2,
    },
}


class bytesresponse(object):
    """A wire protocol response consisting of raw bytes."""

    def __init__(self, data):
        self.data = data


class ooberror(object):
    """wireproto reply: failure of a batch of operation

    Something failed during a batch call. The error message is stored in
    `self.message`.
    """

    def __init__(self, message):
        self.message = message


class pushres(object):
    """wireproto reply: success with simple integer return

    The call was successful and returned an integer contained in `self.res`.
    """

    def __init__(self, res, output):
        self.res = res
        self.output = output


class pusherr(object):
    """wireproto reply: failure

    The call failed. The `self.res` attribute contains the error message.
    """

    def __init__(self, res, output):
        self.res = res
        self.output = output


class streamres(object):
    """wireproto reply: binary stream

    The call was successful and the result is a stream.

    Accepts a generator containing chunks of data to be sent to the client.

    ``prefer_uncompressed`` indicates that the data is expected to be
    uncompressable and that the stream should therefore use the ``none``
    engine.
    """

    def __init__(self, gen=None, prefer_uncompressed=False):
        self.gen = gen
        self.prefer_uncompressed = prefer_uncompressed


class streamreslegacy(object):
    """wireproto reply: uncompressed binary stream

    The call was successful and the result is a stream.

    Accepts a generator containing chunks of data to be sent to the client.

    Like ``streamres``, but sends an uncompressed data for "version 1" clients
    using the application/mercurial-0.1 media type.
    """

    def __init__(self, gen=None):
        self.gen = gen


# list of nodes encoding / decoding
def decodelist(l, sep=b' '):
    if l:
        return [bin(v) for v in l.split(sep)]
    return []


def encodelist(l, sep=b' '):
    try:
        return sep.join(map(hex, l))
    except TypeError:
        raise


# batched call argument encoding


def escapebatcharg(plain):
    return (
        plain.replace(b':', b':c')
        .replace(b',', b':o')
        .replace(b';', b':s')
        .replace(b'=', b':e')
    )


def unescapebatcharg(escaped):
    return (
        escaped.replace(b':e', b'=')
        .replace(b':s', b';')
        .replace(b':o', b',')
        .replace(b':c', b':')
    )


# mapping of options accepted by getbundle and their types
#
# Meant to be extended by extensions. It is the extension's responsibility to
# ensure such options are properly processed in exchange.getbundle.
#
# supported types are:
#
# :nodes: list of binary nodes, transmitted as space-separated hex nodes
# :csv:   list of values, transmitted as comma-separated values
# :scsv:  set of values, transmitted as comma-separated values
# :plain: string with no transformation needed.
GETBUNDLE_ARGUMENTS = {
    b'heads': b'nodes',
    b'bookmarks': b'boolean',
    b'common': b'nodes',
    b'obsmarkers': b'boolean',
    b'phases': b'boolean',
    b'bundlecaps': b'scsv',
    b'listkeys': b'csv',
    b'cg': b'boolean',
    b'cbattempted': b'boolean',
    b'stream': b'boolean',
    b'includepats': b'csv',
    b'excludepats': b'csv',
}


class baseprotocolhandler(interfaceutil.Interface):
    """Abstract base class for wire protocol handlers.

    A wire protocol handler serves as an interface between protocol command
    handlers and the wire protocol transport layer. Protocol handlers provide
    methods to read command arguments, redirect stdio for the duration of
    the request, handle response types, etc.
    """

    name = interfaceutil.Attribute(
        """The name of the protocol implementation.

        Used for uniquely identifying the transport type.
        """
    )

    def getargs(args):
        """return the value for arguments in <args>

        For version 1 transports, returns a list of values in the same
        order they appear in ``args``. For version 2 transports, returns
        a dict mapping argument name to value.
        """

    def getprotocaps():
        """Returns the list of protocol-level capabilities of client

        Returns a list of capabilities as declared by the client for
        the current request (or connection for stateful protocol handlers)."""

    def getpayload():
        """Provide a generator for the raw payload.

        The caller is responsible for ensuring that the full payload is
        processed.
        """

    def mayberedirectstdio():
        """Context manager to possibly redirect stdio.

        The context manager yields a file-object like object that receives
        stdout and stderr output when the context manager is active. Or it
        yields ``None`` if no I/O redirection occurs.

        The intent of this context manager is to capture stdio output
        so it may be sent in the response. Some transports support streaming
        stdio to the client in real time. For these transports, stdio output
        won't be captured.
        """

    def client():
        """Returns a string representation of this client (as bytes)."""

    def addcapabilities(repo, caps):
        """Adds advertised capabilities specific to this protocol.

        Receives the list of capabilities collected so far.

        Returns a list of capabilities. The passed in argument can be returned.
        """

    def checkperm(perm):
        """Validate that the client has permissions to perform a request.

        The argument is the permission required to proceed. If the client
        doesn't have that permission, the exception should raise or abort
        in a protocol specific manner.
        """


class commandentry(object):
    """Represents a declared wire protocol command."""

    def __init__(
        self,
        func,
        args=b'',
        transports=None,
        permission=b'push',
        cachekeyfn=None,
        extracapabilitiesfn=None,
    ):
        self.func = func
        self.args = args
        self.transports = transports or set()
        self.permission = permission
        self.cachekeyfn = cachekeyfn
        self.extracapabilitiesfn = extracapabilitiesfn

    def _merge(self, func, args):
        """Merge this instance with an incoming 2-tuple.

        This is called when a caller using the old 2-tuple API attempts
        to replace an instance. The incoming values are merged with
        data not captured by the 2-tuple and a new instance containing
        the union of the two objects is returned.
        """
        return commandentry(
            func,
            args=args,
            transports=set(self.transports),
            permission=self.permission,
        )

    # Old code treats instances as 2-tuples. So expose that interface.
    def __iter__(self):
        yield self.func
        yield self.args

    def __getitem__(self, i):
        if i == 0:
            return self.func
        elif i == 1:
            return self.args
        else:
            raise IndexError(b'can only access elements 0 and 1')


class commanddict(dict):
    """Container for registered wire protocol commands.

    It behaves like a dict. But __setitem__ is overwritten to allow silent
    coercion of values from 2-tuples for API compatibility.
    """

    def __setitem__(self, k, v):
        if isinstance(v, commandentry):
            pass
        # Cast 2-tuples to commandentry instances.
        elif isinstance(v, tuple):
            if len(v) != 2:
                raise ValueError(b'command tuples must have exactly 2 elements')

            # It is common for extensions to wrap wire protocol commands via
            # e.g. ``wireproto.commands[x] = (newfn, args)``. Because callers
            # doing this aren't aware of the new API that uses objects to store
            # command entries, we automatically merge old state with new.
            if k in self:
                v = self[k]._merge(v[0], v[1])
            else:
                # Use default values from @wireprotocommand.
                v = commandentry(
                    v[0],
                    args=v[1],
                    transports=set(TRANSPORTS),
                    permission=b'push',
                )
        else:
            raise ValueError(
                b'command entries must be commandentry instances '
                b'or 2-tuples'
            )

        return super(commanddict, self).__setitem__(k, v)

    def commandavailable(self, command, proto):
        """Determine if a command is available for the requested protocol."""
        assert proto.name in TRANSPORTS

        entry = self.get(command)

        if not entry:
            return False

        if proto.name not in entry.transports:
            return False

        return True


def supportedcompengines(ui, role):
    """Obtain the list of supported compression engines for a request."""
    assert role in (compression.CLIENTROLE, compression.SERVERROLE)

    compengines = compression.compengines.supportedwireengines(role)

    # Allow config to override default list and ordering.
    if role == compression.SERVERROLE:
        configengines = ui.configlist(b'server', b'compressionengines')
        config = b'server.compressionengines'
    else:
        # This is currently implemented mainly to facilitate testing. In most
        # cases, the server should be in charge of choosing a compression engine
        # because a server has the most to lose from a sub-optimal choice. (e.g.
        # CPU DoS due to an expensive engine or a network DoS due to poor
        # compression ratio).
        configengines = ui.configlist(
            b'experimental', b'clientcompressionengines'
        )
        config = b'experimental.clientcompressionengines'

    # No explicit config. Filter out the ones that aren't supposed to be
    # advertised and return default ordering.
    if not configengines:
        attr = (
            b'serverpriority' if role == util.SERVERROLE else b'clientpriority'
        )
        return [
            e for e in compengines if getattr(e.wireprotosupport(), attr) > 0
        ]

    # If compression engines are listed in the config, assume there is a good
    # reason for it (like server operators wanting to achieve specific
    # performance characteristics). So fail fast if the config references
    # unusable compression engines.
    validnames = {e.name() for e in compengines}
    invalidnames = {e for e in configengines if e not in validnames}
    if invalidnames:
        raise error.Abort(
            _(b'invalid compression engine defined in %s: %s')
            % (config, b', '.join(sorted(invalidnames)))
        )

    compengines = [e for e in compengines if e.name() in configengines]
    compengines = sorted(
        compengines, key=lambda e: configengines.index(e.name())
    )

    if not compengines:
        raise error.Abort(
            _(
                b'%s config option does not specify any known '
                b'compression engines'
            )
            % config,
            hint=_(b'usable compression engines: %s')
            % b', '.sorted(validnames),  # pytype: disable=attribute-error
        )

    return compengines


@attr.s
class encodedresponse(object):
    """Represents response data that is already content encoded.

    Wire protocol version 2 only.

    Commands typically emit Python objects that are encoded and sent over the
    wire. If commands emit an object of this type, the encoding step is bypassed
    and the content from this object is used instead.
    """

    data = attr.ib()


@attr.s
class alternatelocationresponse(object):
    """Represents a response available at an alternate location.

    Instances are sent in place of actual response objects when the server
    is sending a "content redirect" response.

    Only compatible with wire protocol version 2.
    """

    url = attr.ib()
    mediatype = attr.ib()
    size = attr.ib(default=None)
    fullhashes = attr.ib(default=None)
    fullhashseed = attr.ib(default=None)
    serverdercerts = attr.ib(default=None)
    servercadercerts = attr.ib(default=None)


@attr.s
class indefinitebytestringresponse(object):
    """Represents an object to be encoded to an indefinite length bytestring.

    Instances are initialized from an iterable of chunks, with each chunk being
    a bytes instance.
    """

    chunks = attr.ib()
