# error.py - Mercurial exceptions
#
# Copyright 2005-2008 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Mercurial exceptions.

This allows us to catch exceptions at higher levels without forcing
imports.
"""

# Do not import anything here, please

class RevlogError(Exception):
    pass

class LookupError(RevlogError, KeyError):
    def __init__(self, name, index, message):
        self.name = name
        if isinstance(name, str) and len(name) == 20:
            from node import short
            name = short(name)
        RevlogError.__init__(self, '%s@%s: %s' % (index, name, message))

    def __str__(self):
        return RevlogError.__str__(self)

class ManifestLookupError(LookupError):
    pass

class CommandError(Exception):
    """Exception raised on errors in parsing the command line."""

class InterventionRequired(Exception):
    """Exception raised when a command requires human intervention."""

class Abort(Exception):
    """Raised if a command needs to print an error and exit."""
    def __init__(self, *args, **kw):
        Exception.__init__(self, *args)
        self.hint = kw.get('hint')

class ConfigError(Abort):
    'Exception raised when parsing config files'

class OutOfBandError(Exception):
    'Exception raised when a remote repo reports failure'

class ParseError(Exception):
    'Exception raised when parsing config files (msg[, pos])'

class RepoError(Exception):
    def __init__(self, *args, **kw):
        Exception.__init__(self, *args)
        self.hint = kw.get('hint')

class RepoLookupError(RepoError):
    pass

class CapabilityError(RepoError):
    pass

class RequirementError(RepoError):
    """Exception raised if .hg/requires has an unknown entry."""
    pass

class LockError(IOError):
    def __init__(self, errno, strerror, filename, desc):
        IOError.__init__(self, errno, strerror, filename)
        self.desc = desc

class LockHeld(LockError):
    def __init__(self, errno, filename, desc, locker):
        LockError.__init__(self, errno, 'Lock held', filename, desc)
        self.locker = locker

class LockUnavailable(LockError):
    pass

class ResponseError(Exception):
    """Raised to print an error with part of output and exit."""

class UnknownCommand(Exception):
    """Exception raised if command is not in the command table."""

class AmbiguousCommand(Exception):
    """Exception raised if command shortcut matches more than one command."""

# derived from KeyboardInterrupt to simplify some breakout code
class SignalInterrupt(KeyboardInterrupt):
    """Exception raised on SIGTERM and SIGHUP."""

class SignatureError(Exception):
    pass
