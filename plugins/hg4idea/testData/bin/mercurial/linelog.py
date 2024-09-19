# linelog - efficient cache for annotate data
#
# Copyright 2018 Google LLC.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""linelog is an efficient cache for annotate data inspired by SCCS Weaves.

SCCS Weaves are an implementation of
https://en.wikipedia.org/wiki/Interleaved_deltas. See
mercurial/helptext/internals/linelog.txt for an exploration of SCCS weaves
and how linelog works in detail.

Here's a hacker's summary: a linelog is a program which is executed in
the context of a revision. Executing the program emits information
about lines, including the revision that introduced them and the line
number in the file at the introducing revision. When an insertion or
deletion is performed on the file, a jump instruction is used to patch
in a new body of annotate information.
"""
from __future__ import absolute_import, print_function

import abc
import struct

from .thirdparty import attr
from . import pycompat

_llentry = struct.Struct(b'>II')


class LineLogError(Exception):
    """Error raised when something bad happens internally in linelog."""


@attr.s
class lineinfo(object):
    # Introducing revision of this line.
    rev = attr.ib()
    # Line number for this line in its introducing revision.
    linenum = attr.ib()
    # Private. Offset in the linelog program of this line. Used internally.
    _offset = attr.ib()


@attr.s
class annotateresult(object):
    rev = attr.ib()
    lines = attr.ib()
    _eof = attr.ib()

    def __iter__(self):
        return iter(self.lines)


class _llinstruction(object):  # pytype: disable=ignored-metaclass

    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def __init__(self, op1, op2):
        pass

    @abc.abstractmethod
    def __str__(self):
        pass

    def __repr__(self):
        return str(self)

    @abc.abstractmethod
    def __eq__(self, other):
        pass

    @abc.abstractmethod
    def encode(self):
        """Encode this instruction to the binary linelog format."""

    @abc.abstractmethod
    def execute(self, rev, pc, emit):
        """Execute this instruction.

        Args:
          rev: The revision we're annotating.
          pc: The current offset in the linelog program.
          emit: A function that accepts a single lineinfo object.

        Returns:
          The new value of pc. Returns None if exeuction should stop
          (that is, we've found the end of the file.)
        """


class _jge(_llinstruction):
    """If the current rev is greater than or equal to op1, jump to op2."""

    def __init__(self, op1, op2):
        self._cmprev = op1
        self._target = op2

    def __str__(self):
        return 'JGE %d %d' % (self._cmprev, self._target)

    def __eq__(self, other):
        return (
            type(self) == type(other)
            and self._cmprev == other._cmprev
            and self._target == other._target
        )

    def encode(self):
        return _llentry.pack(self._cmprev << 2, self._target)

    def execute(self, rev, pc, emit):
        if rev >= self._cmprev:
            return self._target
        return pc + 1


class _jump(_llinstruction):
    """Unconditional jumps are expressed as a JGE with op1 set to 0."""

    def __init__(self, op1, op2):
        if op1 != 0:
            raise LineLogError(b"malformed JUMP, op1 must be 0, got %d" % op1)
        self._target = op2

    def __str__(self):
        return 'JUMP %d' % (self._target)

    def __eq__(self, other):
        return type(self) == type(other) and self._target == other._target

    def encode(self):
        return _llentry.pack(0, self._target)

    def execute(self, rev, pc, emit):
        return self._target


class _eof(_llinstruction):
    """EOF is expressed as a JGE that always jumps to 0."""

    def __init__(self, op1, op2):
        if op1 != 0:
            raise LineLogError(b"malformed EOF, op1 must be 0, got %d" % op1)
        if op2 != 0:
            raise LineLogError(b"malformed EOF, op2 must be 0, got %d" % op2)

    def __str__(self):
        return r'EOF'

    def __eq__(self, other):
        return type(self) == type(other)

    def encode(self):
        return _llentry.pack(0, 0)

    def execute(self, rev, pc, emit):
        return None


class _jl(_llinstruction):
    """If the current rev is less than op1, jump to op2."""

    def __init__(self, op1, op2):
        self._cmprev = op1
        self._target = op2

    def __str__(self):
        return 'JL %d %d' % (self._cmprev, self._target)

    def __eq__(self, other):
        return (
            type(self) == type(other)
            and self._cmprev == other._cmprev
            and self._target == other._target
        )

    def encode(self):
        return _llentry.pack(1 | (self._cmprev << 2), self._target)

    def execute(self, rev, pc, emit):
        if rev < self._cmprev:
            return self._target
        return pc + 1


class _line(_llinstruction):
    """Emit a line."""

    def __init__(self, op1, op2):
        # This line was introduced by this revision number.
        self._rev = op1
        # This line had the specified line number in the introducing revision.
        self._origlineno = op2

    def __str__(self):
        return 'LINE %d %d' % (self._rev, self._origlineno)

    def __eq__(self, other):
        return (
            type(self) == type(other)
            and self._rev == other._rev
            and self._origlineno == other._origlineno
        )

    def encode(self):
        return _llentry.pack(2 | (self._rev << 2), self._origlineno)

    def execute(self, rev, pc, emit):
        emit(lineinfo(self._rev, self._origlineno, pc))
        return pc + 1


def _decodeone(data, offset):
    """Decode a single linelog instruction from an offset in a buffer."""
    try:
        op1, op2 = _llentry.unpack_from(data, offset)
    except struct.error as e:
        raise LineLogError(b'reading an instruction failed: %r' % e)
    opcode = op1 & 0b11
    op1 = op1 >> 2
    if opcode == 0:
        if op1 == 0:
            if op2 == 0:
                return _eof(op1, op2)
            return _jump(op1, op2)
        return _jge(op1, op2)
    elif opcode == 1:
        return _jl(op1, op2)
    elif opcode == 2:
        return _line(op1, op2)
    raise NotImplementedError(b'Unimplemented opcode %r' % opcode)


class linelog(object):
    """Efficient cache for per-line history information."""

    def __init__(self, program=None, maxrev=0):
        if program is None:
            # We pad the program with an extra leading EOF so that our
            # offsets will match the C code exactly. This means we can
            # interoperate with the C code.
            program = [_eof(0, 0), _eof(0, 0)]
        self._program = program
        self._lastannotate = None
        self._maxrev = maxrev

    def __eq__(self, other):
        return (
            type(self) == type(other)
            and self._program == other._program
            and self._maxrev == other._maxrev
        )

    def __repr__(self):
        return '<linelog at %s: maxrev=%d size=%d>' % (
            hex(id(self)),
            self._maxrev,
            len(self._program),
        )

    def debugstr(self):
        fmt = '%%%dd %%s' % len(str(len(self._program)))
        return pycompat.sysstr(b'\n').join(
            fmt % (idx, i) for idx, i in enumerate(self._program[1:], 1)
        )

    @classmethod
    def fromdata(cls, buf):
        if len(buf) % _llentry.size != 0:
            raise LineLogError(
                b"invalid linelog buffer size %d (must be a multiple of %d)"
                % (len(buf), _llentry.size)
            )
        expected = len(buf) / _llentry.size
        fakejge = _decodeone(buf, 0)
        if isinstance(fakejge, _jump):
            maxrev = 0
        elif isinstance(fakejge, (_jge, _jl)):
            maxrev = fakejge._cmprev
        else:
            raise LineLogError(
                'Expected one of _jump, _jge, or _jl. Got %s.'
                % type(fakejge).__name__
            )
        assert isinstance(fakejge, (_jump, _jge, _jl))  # help pytype
        numentries = fakejge._target
        if expected != numentries:
            raise LineLogError(
                b"corrupt linelog data: claimed"
                b" %d entries but given data for %d entries"
                % (expected, numentries)
            )
        instructions = [_eof(0, 0)]
        for offset in pycompat.xrange(1, numentries):
            instructions.append(_decodeone(buf, offset * _llentry.size))
        return cls(instructions, maxrev=maxrev)

    def encode(self):
        hdr = _jge(self._maxrev, len(self._program)).encode()
        return hdr + b''.join(i.encode() for i in self._program[1:])

    def clear(self):
        self._program = []
        self._maxrev = 0
        self._lastannotate = None

    def replacelines_vec(self, rev, a1, a2, blines):
        return self.replacelines(
            rev, a1, a2, 0, len(blines), _internal_blines=blines
        )

    def replacelines(self, rev, a1, a2, b1, b2, _internal_blines=None):
        """Replace lines [a1, a2) with lines [b1, b2)."""
        if self._lastannotate:
            # TODO(augie): make replacelines() accept a revision at
            # which we're editing as well as a revision to mark
            # responsible for the edits. In hg-experimental it's
            # stateful like this, so we're doing the same thing to
            # retain compatibility with absorb until that's imported.
            ar = self._lastannotate
        else:
            ar = self.annotate(rev)
            #        ar = self.annotate(self._maxrev)
        if a1 > len(ar.lines):
            raise LineLogError(
                b'%d contains %d lines, tried to access line %d'
                % (rev, len(ar.lines), a1)
            )
        elif a1 == len(ar.lines):
            # Simulated EOF instruction since we're at EOF, which
            # doesn't have a "real" line.
            a1inst = _eof(0, 0)
            a1info = lineinfo(0, 0, ar._eof)
        else:
            a1info = ar.lines[a1]
            a1inst = self._program[a1info._offset]
        programlen = self._program.__len__
        oldproglen = programlen()
        appendinst = self._program.append

        # insert
        blineinfos = []
        bappend = blineinfos.append
        if b1 < b2:
            # Determine the jump target for the JGE at the start of
            # the new block.
            tgt = oldproglen + (b2 - b1 + 1)
            # Jump to skip the insert if we're at an older revision.
            appendinst(_jl(rev, tgt))
            for linenum in pycompat.xrange(b1, b2):
                if _internal_blines is None:
                    bappend(lineinfo(rev, linenum, programlen()))
                    appendinst(_line(rev, linenum))
                else:
                    newrev, newlinenum = _internal_blines[linenum]
                    bappend(lineinfo(newrev, newlinenum, programlen()))
                    appendinst(_line(newrev, newlinenum))
        # delete
        if a1 < a2:
            if a2 > len(ar.lines):
                raise LineLogError(
                    b'%d contains %d lines, tried to access line %d'
                    % (rev, len(ar.lines), a2)
                )
            elif a2 == len(ar.lines):
                endaddr = ar._eof
            else:
                endaddr = ar.lines[a2]._offset
            if a2 > 0 and rev < self._maxrev:
                # If we're here, we're deleting a chunk of an old
                # commit, so we need to be careful and not touch
                # invisible lines between a2-1 and a2 (IOW, lines that
                # are added later).
                endaddr = ar.lines[a2 - 1]._offset + 1
            appendinst(_jge(rev, endaddr))
        # copy instruction from a1
        a1instpc = programlen()
        appendinst(a1inst)
        # if a1inst isn't a jump or EOF, then we need to add an unconditional
        # jump back into the program here.
        if not isinstance(a1inst, (_jump, _eof)):
            appendinst(_jump(0, a1info._offset + 1))
        # Patch instruction at a1, which makes our patch live.
        self._program[a1info._offset] = _jump(0, oldproglen)

        # Update self._lastannotate in place. This serves as a cache to avoid
        # expensive "self.annotate" in this function, when "replacelines" is
        # used continuously.
        if len(self._lastannotate.lines) > a1:
            self._lastannotate.lines[a1]._offset = a1instpc
        else:
            assert isinstance(a1inst, _eof)
            self._lastannotate._eof = a1instpc
        self._lastannotate.lines[a1:a2] = blineinfos
        self._lastannotate.rev = max(self._lastannotate.rev, rev)

        if rev > self._maxrev:
            self._maxrev = rev

    def annotate(self, rev):
        pc = 1
        lines = []
        executed = 0
        # Sanity check: if instructions executed exceeds len(program), we
        # hit an infinite loop in the linelog program somehow and we
        # should stop.
        while pc is not None and executed < len(self._program):
            inst = self._program[pc]
            lastpc = pc
            pc = inst.execute(rev, pc, lines.append)
            executed += 1
        if pc is not None:
            raise LineLogError(
                r'Probably hit an infinite loop in linelog. Program:\n'
                + self.debugstr()
            )
        ar = annotateresult(rev, lines, lastpc)
        self._lastannotate = ar
        return ar

    @property
    def maxrev(self):
        return self._maxrev

    # Stateful methods which depend on the value of the last
    # annotation run. This API is for compatiblity with the original
    # linelog, and we should probably consider refactoring it.
    @property
    def annotateresult(self):
        """Return the last annotation result. C linelog code exposed this."""
        return [(l.rev, l.linenum) for l in self._lastannotate.lines]

    def getoffset(self, line):
        return self._lastannotate.lines[line]._offset

    def getalllines(self, start=0, end=0):
        """Get all lines that ever occurred in [start, end).

        Passing start == end == 0 means "all lines ever".

        This works in terms of *internal* program offsets, not line numbers.
        """
        pc = start or 1
        lines = []
        # only take as many steps as there are instructions in the
        # program - if we don't find an EOF or our stop-line before
        # then, something is badly broken.
        for step in pycompat.xrange(len(self._program)):
            inst = self._program[pc]
            nextpc = pc + 1
            if isinstance(inst, _jump):
                nextpc = inst._target
            elif isinstance(inst, _eof):
                return lines
            elif isinstance(inst, (_jl, _jge)):
                pass
            elif isinstance(inst, _line):
                lines.append((inst._rev, inst._origlineno))
            else:
                raise LineLogError(b"Illegal instruction %r" % inst)
            if nextpc == end:
                return lines
            pc = nextpc
        raise LineLogError(b"Failed to perform getalllines")
