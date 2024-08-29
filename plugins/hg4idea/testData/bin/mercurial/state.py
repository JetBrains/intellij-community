# state.py - writing and reading state files in Mercurial
#
# Copyright 2018 Pulkit Goyal <pulkitmgoyal@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""
This file contains class to wrap the state for commands and other
related logic.

All the data related to the command state is stored as dictionary in the object.
The class has methods using which the data can be stored to disk in a file under
.hg/ directory.

We store the data on disk in cbor, for which we use the CBOR format to encode
the data.
"""


import contextlib

from typing import (
    Any,
    Dict,
)

from .i18n import _

from . import (
    error,
    util,
)
from .utils import cborutil

# keeps pyflakes happy
for t in (Any, Dict):
    assert t


class cmdstate:
    """a wrapper class to store the state of commands like `rebase`, `graft`,
    `histedit`, `shelve` etc. Extensions can also use this to write state files.

    All the data for the state is stored in the form of key-value pairs in a
    dictionary.

    The class object can write all the data to a file in .hg/ directory and
    can populate the object data reading that file.

    Uses cbor to serialize and deserialize data while writing and reading from
    disk.
    """

    def __init__(self, repo, fname):
        """repo is the repo object
        fname is the file name in which data should be stored in .hg directory
        """
        self._repo = repo
        self.fname = fname

    def read(self) -> Dict[bytes, Any]:
        """read the existing state file and return a dict of data stored"""
        return self._read()

    def save(self, version, data):
        """write all the state data stored to .hg/<filename> file

        we use third-party library cbor to serialize data to write in the file.
        """
        if not isinstance(version, int):
            raise error.ProgrammingError(
                b"version of state file should be an integer"
            )

        with self._repo.vfs(self.fname, b'wb', atomictemp=True) as fp:
            fp.write(b'%d\n' % version)
            for chunk in cborutil.streamencode(data):
                fp.write(chunk)

    def _read(self):
        """reads the state file and returns a dictionary which contain
        data in the same format as it was before storing"""
        with self._repo.vfs(self.fname, b'rb') as fp:
            try:
                int(fp.readline())
            except ValueError:
                raise error.CorruptedState(
                    b"unknown version of state file found"
                )

            return cborutil.decodeall(fp.read())[0]

    def delete(self):
        """drop the state file if exists"""
        util.unlinkpath(self._repo.vfs.join(self.fname), ignoremissing=True)

    def exists(self):
        """check whether the state file exists or not"""
        return self._repo.vfs.exists(self.fname)


class _statecheck:
    """a utility class that deals with multistep operations like graft,
    histedit, bisect, update etc and check whether such commands
    are in an unfinished conditition or not and return appropriate message
    and hint.
    It also has the ability to register and determine the states of any new
    multistep operation or multistep command extension.
    """

    def __init__(
        self,
        opname,
        fname,
        clearable,
        allowcommit,
        reportonly,
        continueflag,
        stopflag,
        childopnames,
        cmdmsg,
        cmdhint,
        statushint,
        abortfunc,
        continuefunc,
    ):
        self._opname = opname
        self._fname = fname
        self._clearable = clearable
        self._allowcommit = allowcommit
        self._reportonly = reportonly
        self._continueflag = continueflag
        self._stopflag = stopflag
        self._childopnames = childopnames
        self._delegating = False
        self._cmdmsg = cmdmsg
        self._cmdhint = cmdhint
        self._statushint = statushint
        self.abortfunc = abortfunc
        self.continuefunc = continuefunc

    def statusmsg(self):
        """returns the hint message corresponding to the command for
        hg status --verbose
        """
        if not self._statushint:
            hint = _(
                b'To continue:    hg %s --continue\n'
                b'To abort:       hg %s --abort'
            ) % (self._opname, self._opname)
            if self._stopflag:
                hint = hint + (
                    _(b'\nTo stop:        hg %s --stop') % (self._opname)
                )
            return hint
        return self._statushint

    def hint(self):
        """returns the hint message corresponding to an interrupted
        operation
        """
        if not self._cmdhint:
            if not self._stopflag:
                return _(b"use 'hg %s --continue' or 'hg %s --abort'") % (
                    self._opname,
                    self._opname,
                )
            else:
                return _(
                    b"use 'hg %s --continue', 'hg %s --abort', "
                    b"or 'hg %s --stop'"
                ) % (
                    self._opname,
                    self._opname,
                    self._opname,
                )

        return self._cmdhint

    def msg(self):
        """returns the status message corresponding to the command"""
        if not self._cmdmsg:
            return _(b'%s in progress') % (self._opname)
        return self._cmdmsg

    def continuemsg(self):
        """returns appropriate continue message corresponding to command"""
        return _(b'hg %s --continue') % (self._opname)

    def isunfinished(self, repo):
        """determines whether a multi-step operation is in progress
        or not
        """
        if self._opname == b'merge':
            return len(repo[None].parents()) > 1
        elif self._delegating:
            return False
        else:
            return repo.vfs.exists(self._fname)


# A list of statecheck objects for multistep operations like graft.
_unfinishedstates = []
_unfinishedstatesbyname = {}


def addunfinished(
    opname,
    fname,
    clearable=False,
    allowcommit=False,
    reportonly=False,
    continueflag=False,
    stopflag=False,
    childopnames=None,
    cmdmsg=b"",
    cmdhint=b"",
    statushint=b"",
    abortfunc=None,
    continuefunc=None,
):
    """this registers a new command or operation to unfinishedstates
    opname is the name the command or operation
    fname is the file name in which data should be stored in .hg directory.
    It is None for merge command.
    clearable boolean determines whether or not interrupted states can be
    cleared by running `hg update -C .` which in turn deletes the
    state file.
    allowcommit boolean decides whether commit is allowed during interrupted
    state or not.
    reportonly flag is used for operations like bisect where we just
    need to detect the operation using 'hg status --verbose'
    continueflag is a boolean determines whether or not a command supports
    `--continue` option or not.
    stopflag is a boolean that determines whether or not a command supports
    --stop flag
    childopnames is a list of other opnames this op uses as sub-steps of its
    own execution. They must already be added.
    cmdmsg is used to pass a different status message in case standard
    message of the format "abort: cmdname in progress" is not desired.
    cmdhint is used to pass a different hint message in case standard
    message of the format "To continue: hg cmdname --continue
    To abort: hg cmdname --abort" is not desired.
    statushint is used to pass a different status message in case standard
    message of the format ('To continue:    hg cmdname --continue'
    'To abort:       hg cmdname --abort') is not desired
    abortfunc stores the function required to abort an unfinished state.
    continuefunc stores the function required to finish an interrupted
    operation.
    """
    childopnames = childopnames or []
    statecheckobj = _statecheck(
        opname,
        fname,
        clearable,
        allowcommit,
        reportonly,
        continueflag,
        stopflag,
        childopnames,
        cmdmsg,
        cmdhint,
        statushint,
        abortfunc,
        continuefunc,
    )

    if opname == b'merge':
        _unfinishedstates.append(statecheckobj)
    else:
        # This check enforces that for any op 'foo' which depends on op 'bar',
        # 'foo' comes before 'bar' in _unfinishedstates. This ensures that
        # getrepostate() always returns the most specific applicable answer.
        for childopname in childopnames:
            if childopname not in _unfinishedstatesbyname:
                raise error.ProgrammingError(
                    _(b'op %s depends on unknown op %s') % (opname, childopname)
                )

        _unfinishedstates.insert(0, statecheckobj)

    if opname in _unfinishedstatesbyname:
        raise error.ProgrammingError(_(b'op %s registered twice') % opname)
    _unfinishedstatesbyname[opname] = statecheckobj


def _getparentandchild(opname, childopname):
    p = _unfinishedstatesbyname.get(opname, None)
    if not p:
        raise error.ProgrammingError(_(b'unknown op %s') % opname)
    if childopname not in p._childopnames:
        raise error.ProgrammingError(
            _(b'op %s does not delegate to %s') % (opname, childopname)
        )
    c = _unfinishedstatesbyname[childopname]
    return p, c


@contextlib.contextmanager
def delegating(repo, opname, childopname):
    """context wrapper for delegations from opname to childopname.

    requires that childopname was specified when opname was registered.

    Usage:
      def my_command_foo_that_uses_rebase(...):
        ...
        with state.delegating(repo, 'foo', 'rebase'):
          _run_rebase(...)
        ...
    """

    p, c = _getparentandchild(opname, childopname)
    if p._delegating:
        raise error.ProgrammingError(
            _(b'cannot delegate from op %s recursively') % opname
        )
    p._delegating = True
    try:
        yield
    except error.ConflictResolutionRequired as e:
        # Rewrite conflict resolution advice for the parent opname.
        if e.opname == childopname:
            raise error.ConflictResolutionRequired(opname)
        raise e
    finally:
        p._delegating = False


def ischildunfinished(repo, opname, childopname):
    """Returns true if both opname and childopname are unfinished."""

    p, c = _getparentandchild(opname, childopname)
    return (p._delegating or p.isunfinished(repo)) and c.isunfinished(repo)


def continuechild(ui, repo, opname, childopname):
    """Checks that childopname is in progress, and continues it."""

    p, c = _getparentandchild(opname, childopname)
    if not ischildunfinished(repo, opname, childopname):
        raise error.ProgrammingError(
            _(b'child op %s of parent %s is not unfinished')
            % (childopname, opname)
        )
    if not c.continuefunc:
        raise error.ProgrammingError(
            _(b'op %s has no continue function') % childopname
        )
    return c.continuefunc(ui, repo)


addunfinished(
    b'update',
    fname=b'updatestate',
    clearable=True,
    cmdmsg=_(b'last update was interrupted'),
    cmdhint=_(b"use 'hg update' to get a consistent checkout"),
    statushint=_(b"To continue:    hg update ."),
)
addunfinished(
    b'bisect',
    fname=b'bisect.state',
    allowcommit=True,
    reportonly=True,
    cmdhint=_(b"use 'hg bisect --reset'"),
    statushint=_(
        b'To mark the changeset good:    hg bisect --good\n'
        b'To mark the changeset bad:     hg bisect --bad\n'
        b'To abort:                      hg bisect --reset\n'
    ),
)


def getrepostate(repo):
    # experimental config: commands.status.skipstates
    skip = set(repo.ui.configlist(b'commands', b'status.skipstates'))
    for state in _unfinishedstates:
        if state._opname in skip:
            continue
        if state.isunfinished(repo):
            return (state._opname, state.statusmsg())
