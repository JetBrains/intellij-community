# error.py - Mercurial exceptions
#
# Copyright 2005-2008 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Mercurial exceptions.

This allows us to catch exceptions at higher levels without forcing
imports.
"""

from __future__ import absolute_import

import difflib

# Do not import anything but pycompat here, please
from . import pycompat

if pycompat.TYPE_CHECKING:
    from typing import (
        Any,
        AnyStr,
        Iterable,
        List,
        Optional,
        Sequence,
        Union,
    )


def _tobytes(exc):
    """Byte-stringify exception in the same way as BaseException_str()"""
    if not exc.args:
        return b''
    if len(exc.args) == 1:
        return pycompat.bytestr(exc.args[0])
    return b'(%s)' % b', '.join(b"'%s'" % pycompat.bytestr(a) for a in exc.args)


class Hint(object):
    """Mix-in to provide a hint of an error

    This should come first in the inheritance list to consume a hint and
    pass remaining arguments to the exception class.
    """

    def __init__(self, *args, **kw):
        self.hint = kw.pop('hint', None)
        super(Hint, self).__init__(*args, **kw)


class Error(Hint, Exception):
    """Base class for Mercurial errors."""

    coarse_exit_code = None
    detailed_exit_code = None

    def __init__(self, message, hint=None):
        # type: (bytes, Optional[bytes]) -> None
        self.message = message
        self.hint = hint
        # Pass the message into the Exception constructor to help extensions
        # that look for exc.args[0].
        Exception.__init__(self, message)

    def __bytes__(self):
        return self.message

    if pycompat.ispy3:

        def __str__(self):
            # the output would be unreadable if the message was translated,
            # but do not replace it with encoding.strfromlocal(), which
            # may raise another exception.
            return pycompat.sysstr(self.__bytes__())

    def format(self):
        # type: () -> bytes
        from .i18n import _

        message = _(b"abort: %s\n") % self.message
        if self.hint:
            message += _(b"(%s)\n") % self.hint
        return message


class Abort(Error):
    """Raised if a command needs to print an error and exit."""


class StorageError(Error):
    """Raised when an error occurs in a storage layer.

    Usually subclassed by a storage-specific exception.
    """

    detailed_exit_code = 50


class RevlogError(StorageError):
    pass


class SidedataHashError(RevlogError):
    def __init__(self, key, expected, got):
        self.hint = None
        self.sidedatakey = key
        self.expecteddigest = expected
        self.actualdigest = got


class FilteredIndexError(IndexError):
    __bytes__ = _tobytes


class LookupError(RevlogError, KeyError):
    def __init__(self, name, index, message):
        self.name = name
        self.index = index
        # this can't be called 'message' because at least some installs of
        # Python 2.6+ complain about the 'message' property being deprecated
        self.lookupmessage = message
        if isinstance(name, bytes) and len(name) == 20:
            from .node import hex

            name = hex(name)
        # if name is a binary node, it can be None
        RevlogError.__init__(
            self, b'%s@%s: %s' % (index, pycompat.bytestr(name), message)
        )

    def __bytes__(self):
        return RevlogError.__bytes__(self)

    def __str__(self):
        return RevlogError.__str__(self)


class AmbiguousPrefixLookupError(LookupError):
    pass


class FilteredLookupError(LookupError):
    pass


class ManifestLookupError(LookupError):
    pass


class CommandError(Exception):
    """Exception raised on errors in parsing the command line."""

    def __init__(self, command, message):
        # type: (bytes, bytes) -> None
        self.command = command
        self.message = message
        super(CommandError, self).__init__()

    __bytes__ = _tobytes


class UnknownCommand(Exception):
    """Exception raised if command is not in the command table."""

    def __init__(self, command, all_commands=None):
        # type: (bytes, Optional[List[bytes]]) -> None
        self.command = command
        self.all_commands = all_commands
        super(UnknownCommand, self).__init__()

    __bytes__ = _tobytes


class AmbiguousCommand(Exception):
    """Exception raised if command shortcut matches more than one command."""

    def __init__(self, prefix, matches):
        # type: (bytes, List[bytes]) -> None
        self.prefix = prefix
        self.matches = matches
        super(AmbiguousCommand, self).__init__()

    __bytes__ = _tobytes


class WorkerError(Exception):
    """Exception raised when a worker process dies."""

    def __init__(self, status_code):
        # type: (int) -> None
        self.status_code = status_code
        # Pass status code to superclass just so it becomes part of __bytes__
        super(WorkerError, self).__init__(status_code)

    __bytes__ = _tobytes


class InterventionRequired(Abort):
    """Exception raised when a command requires human intervention."""

    coarse_exit_code = 1
    detailed_exit_code = 240

    def format(self):
        # type: () -> bytes
        from .i18n import _

        message = _(b"%s\n") % self.message
        if self.hint:
            message += _(b"(%s)\n") % self.hint
        return message


class ConflictResolutionRequired(InterventionRequired):
    """Exception raised when a continuable command required merge conflict resolution."""

    def __init__(self, opname):
        # type: (bytes) -> None
        from .i18n import _

        self.opname = opname
        InterventionRequired.__init__(
            self,
            _(
                b"unresolved conflicts (see 'hg resolve', then 'hg %s --continue')"
            )
            % opname,
        )


class InputError(Abort):
    """Indicates that the user made an error in their input.

    Examples: Invalid command, invalid flags, invalid revision.
    """

    detailed_exit_code = 10


class StateError(Abort):
    """Indicates that the operation might work if retried in a different state.

    Examples: Unresolved merge conflicts, unfinished operations.
    """

    detailed_exit_code = 20


class CanceledError(Abort):
    """Indicates that the user canceled the operation.

    Examples: Close commit editor with error status, quit chistedit.
    """

    detailed_exit_code = 250


class SecurityError(Abort):
    """Indicates that some aspect of security failed.

    Examples: Bad server credentials, expired local credentials for network
    filesystem, mismatched GPG signature, DoS protection.
    """

    detailed_exit_code = 150


class HookLoadError(Abort):
    """raised when loading a hook fails, aborting an operation

    Exists to allow more specialized catching."""


class HookAbort(Abort):
    """raised when a validation hook fails, aborting an operation

    Exists to allow more specialized catching."""

    detailed_exit_code = 40


class ConfigError(Abort):
    """Exception raised when parsing config files"""

    detailed_exit_code = 30

    def __init__(self, message, location=None, hint=None):
        # type: (bytes, Optional[bytes], Optional[bytes]) -> None
        super(ConfigError, self).__init__(message, hint=hint)
        self.location = location

    def format(self):
        # type: () -> bytes
        from .i18n import _

        if self.location is not None:
            message = _(b"config error at %s: %s\n") % (
                pycompat.bytestr(self.location),
                self.message,
            )
        else:
            message = _(b"config error: %s\n") % self.message
        if self.hint:
            message += _(b"(%s)\n") % self.hint
        return message


class UpdateAbort(Abort):
    """Raised when an update is aborted for destination issue"""


class MergeDestAbort(Abort):
    """Raised when an update is aborted for destination issues"""


class NoMergeDestAbort(MergeDestAbort):
    """Raised when an update is aborted because there is nothing to merge"""


class ManyMergeDestAbort(MergeDestAbort):
    """Raised when an update is aborted because destination is ambiguous"""


class ResponseExpected(Abort):
    """Raised when an EOF is received for a prompt"""

    def __init__(self):
        from .i18n import _

        Abort.__init__(self, _(b'response expected'))


class RemoteError(Abort):
    """Exception raised when interacting with a remote repo fails"""

    detailed_exit_code = 100


class OutOfBandError(RemoteError):
    """Exception raised when a remote repo reports failure"""

    def __init__(self, message=None, hint=None):
        from .i18n import _

        if message:
            # Abort.format() adds a trailing newline
            message = _(b"remote error:\n%s") % message.rstrip(b'\n')
        else:
            message = _(b"remote error")
        super(OutOfBandError, self).__init__(message, hint=hint)


class ParseError(Abort):
    """Raised when parsing config files and {rev,file}sets (msg[, pos])"""

    detailed_exit_code = 10

    def __init__(self, message, location=None, hint=None):
        # type: (bytes, Optional[Union[bytes, int]], Optional[bytes]) -> None
        super(ParseError, self).__init__(message, hint=hint)
        self.location = location

    def format(self):
        # type: () -> bytes
        from .i18n import _

        if self.location is not None:
            message = _(b"hg: parse error at %s: %s\n") % (
                pycompat.bytestr(self.location),
                self.message,
            )
        else:
            message = _(b"hg: parse error: %s\n") % self.message
        if self.hint:
            message += _(b"(%s)\n") % self.hint
        return message


class PatchError(Exception):
    __bytes__ = _tobytes


def getsimilar(symbols, value):
    # type: (Iterable[bytes], bytes) -> List[bytes]
    sim = lambda x: difflib.SequenceMatcher(None, value, x).ratio()
    # The cutoff for similarity here is pretty arbitrary. It should
    # probably be investigated and tweaked.
    return [s for s in symbols if sim(s) > 0.6]


def similarity_hint(similar):
    # type: (List[bytes]) -> Optional[bytes]
    from .i18n import _

    if len(similar) == 1:
        return _(b"did you mean %s?") % similar[0]
    elif similar:
        ss = b", ".join(sorted(similar))
        return _(b"did you mean one of %s?") % ss
    else:
        return None


class UnknownIdentifier(ParseError):
    """Exception raised when a {rev,file}set references an unknown identifier"""

    def __init__(self, function, symbols):
        # type: (bytes, Iterable[bytes]) -> None
        from .i18n import _

        similar = getsimilar(symbols, function)
        hint = similarity_hint(similar)

        ParseError.__init__(
            self, _(b"unknown identifier: %s") % function, hint=hint
        )


class RepoError(Hint, Exception):
    __bytes__ = _tobytes


class RepoLookupError(RepoError):
    pass


class FilteredRepoLookupError(RepoLookupError):
    pass


class CapabilityError(RepoError):
    pass


class RequirementError(RepoError):
    """Exception raised if .hg/requires has an unknown entry."""


class StdioError(IOError):
    """Raised if I/O to stdout or stderr fails"""

    def __init__(self, err):
        # type: (IOError) -> None
        IOError.__init__(self, err.errno, err.strerror)

    # no __bytes__() because error message is derived from the standard IOError


class UnsupportedMergeRecords(Abort):
    def __init__(self, recordtypes):
        # type: (Iterable[bytes]) -> None
        from .i18n import _

        self.recordtypes = sorted(recordtypes)
        s = b' '.join(self.recordtypes)
        Abort.__init__(
            self,
            _(b'unsupported merge state records: %s') % s,
            hint=_(
                b'see https://mercurial-scm.org/wiki/MergeStateRecords for '
                b'more information'
            ),
        )


class UnknownVersion(Abort):
    """generic exception for aborting from an encounter with an unknown version"""

    def __init__(self, msg, hint=None, version=None):
        # type: (bytes, Optional[bytes], Optional[bytes]) -> None
        self.version = version
        super(UnknownVersion, self).__init__(msg, hint=hint)


class LockError(IOError):
    def __init__(self, errno, strerror, filename, desc):
        # TODO: figure out if this should be bytes or str
        # _type: (int, str, str, bytes) -> None
        IOError.__init__(self, errno, strerror, filename)
        self.desc = desc

    # no __bytes__() because error message is derived from the standard IOError


class LockHeld(LockError):
    def __init__(self, errno, filename, desc, locker):
        LockError.__init__(self, errno, b'Lock held', filename, desc)
        self.locker = locker


class LockUnavailable(LockError):
    pass


# LockError is for errors while acquiring the lock -- this is unrelated
class LockInheritanceContractViolation(RuntimeError):
    __bytes__ = _tobytes


class ResponseError(Exception):
    """Raised to print an error with part of output and exit."""

    __bytes__ = _tobytes


# derived from KeyboardInterrupt to simplify some breakout code
class SignalInterrupt(KeyboardInterrupt):
    """Exception raised on SIGTERM and SIGHUP."""


class SignatureError(Exception):
    __bytes__ = _tobytes


class PushRaced(RuntimeError):
    """An exception raised during unbundling that indicate a push race"""

    __bytes__ = _tobytes


class ProgrammingError(Hint, RuntimeError):
    """Raised if a mercurial (core or extension) developer made a mistake"""

    def __init__(self, msg, *args, **kwargs):
        # type: (AnyStr, Any, Any) -> None
        # On Python 3, turn the message back into a string since this is
        # an internal-only error that won't be printed except in a
        # stack traces.
        msg = pycompat.sysstr(msg)
        super(ProgrammingError, self).__init__(msg, *args, **kwargs)

    __bytes__ = _tobytes


class WdirUnsupported(Exception):
    """An exception which is raised when 'wdir()' is not supported"""

    __bytes__ = _tobytes


# bundle2 related errors
class BundleValueError(ValueError):
    """error raised when bundle2 cannot be processed"""

    __bytes__ = _tobytes


class BundleUnknownFeatureError(BundleValueError):
    def __init__(self, parttype=None, params=(), values=()):
        self.parttype = parttype
        self.params = params
        self.values = values
        if self.parttype is None:
            msg = b'Stream Parameter'
        else:
            msg = parttype
        entries = self.params
        if self.params and self.values:
            assert len(self.params) == len(self.values)
            entries = []
            for idx, par in enumerate(self.params):
                val = self.values[idx]
                if val is None:
                    entries.append(val)
                else:
                    entries.append(b"%s=%r" % (par, pycompat.maybebytestr(val)))
        if entries:
            msg = b'%s - %s' % (msg, b', '.join(entries))
        ValueError.__init__(self, msg)  # TODO: convert to str?


class ReadOnlyPartError(RuntimeError):
    """error raised when code tries to alter a part being generated"""

    __bytes__ = _tobytes


class PushkeyFailed(Abort):
    """error raised when a pushkey part failed to update a value"""

    def __init__(
        self, partid, namespace=None, key=None, new=None, old=None, ret=None
    ):
        self.partid = partid
        self.namespace = namespace
        self.key = key
        self.new = new
        self.old = old
        self.ret = ret
        # no i18n expected to be processed into a better message
        Abort.__init__(
            self, b'failed to update value for "%s/%s"' % (namespace, key)
        )


class CensoredNodeError(StorageError):
    """error raised when content verification fails on a censored node

    Also contains the tombstone data substituted for the uncensored data.
    """

    def __init__(self, filename, node, tombstone):
        # type: (bytes, bytes, bytes) -> None
        from .node import short

        StorageError.__init__(self, b'%s:%s' % (filename, short(node)))
        self.tombstone = tombstone


class CensoredBaseError(StorageError):
    """error raised when a delta is rejected because its base is censored

    A delta based on a censored revision must be formed as single patch
    operation which replaces the entire base with new content. This ensures
    the delta may be applied by clones which have not censored the base.
    """


class InvalidBundleSpecification(Exception):
    """error raised when a bundle specification is invalid.

    This is used for syntax errors as opposed to support errors.
    """

    __bytes__ = _tobytes


class UnsupportedBundleSpecification(Exception):
    """error raised when a bundle specification is not supported."""

    __bytes__ = _tobytes


class CorruptedState(Exception):
    """error raised when a command is not able to read its state from file"""

    __bytes__ = _tobytes


class PeerTransportError(Abort):
    """Transport-level I/O error when communicating with a peer repo."""


class InMemoryMergeConflictsError(Exception):
    """Exception raised when merge conflicts arose during an in-memory merge."""

    __bytes__ = _tobytes


class WireprotoCommandError(Exception):
    """Represents an error during execution of a wire protocol command.

    Should only be thrown by wire protocol version 2 commands.

    The error is a formatter string and an optional iterable of arguments.
    """

    def __init__(self, message, args=None):
        # type: (bytes, Optional[Sequence[bytes]]) -> None
        self.message = message
        self.messageargs = args
