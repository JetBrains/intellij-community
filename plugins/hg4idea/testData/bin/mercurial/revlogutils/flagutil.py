# flagutils.py - code to deal with revlog flags and their processors
#
# Copyright 2016 Remi Chaintron <remi@fb.com>
# Copyright 2016-2019 Pierre-Yves David <pierre-yves.david@ens-lyon.org>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from ..i18n import _

from .constants import (
    REVIDX_DEFAULT_FLAGS,
    REVIDX_ELLIPSIS,
    REVIDX_EXTSTORED,
    REVIDX_FLAGS_ORDER,
    REVIDX_HASCOPIESINFO,
    REVIDX_ISCENSORED,
    REVIDX_RAWTEXT_CHANGING_FLAGS,
)

from .. import error, util

# blanked usage of all the name to prevent pyflakes constraints
# We need these name available in the module for extensions.
REVIDX_ISCENSORED
REVIDX_ELLIPSIS
REVIDX_EXTSTORED
REVIDX_HASCOPIESINFO,
REVIDX_DEFAULT_FLAGS
REVIDX_FLAGS_ORDER
REVIDX_RAWTEXT_CHANGING_FLAGS

# Keep this in sync with REVIDX_KNOWN_FLAGS in rust/hg-core/src/revlog/revlog.rs
REVIDX_KNOWN_FLAGS = util.bitsfrom(REVIDX_FLAGS_ORDER)

# Store flag processors (cf. 'addflagprocessor()' to register)
flagprocessors = {
    REVIDX_ISCENSORED: None,
    REVIDX_HASCOPIESINFO: None,
}


def addflagprocessor(flag, processor):
    """Register a flag processor on a revision data flag.

    Invariant:
    - Flags need to be defined in REVIDX_KNOWN_FLAGS and REVIDX_FLAGS_ORDER,
      and REVIDX_RAWTEXT_CHANGING_FLAGS if they can alter rawtext.
    - Only one flag processor can be registered on a specific flag.
    - flagprocessors must be 3-tuples of functions (read, write, raw) with the
      following signatures:
          - (read)  f(self, rawtext) -> text, bool
          - (write) f(self, text) -> rawtext, bool
          - (raw)   f(self, rawtext) -> bool
      "text" is presented to the user. "rawtext" is stored in revlog data, not
      directly visible to the user.
      The boolean returned by these transforms is used to determine whether
      the returned text can be used for hash integrity checking. For example,
      if "write" returns False, then "text" is used to generate hash. If
      "write" returns True, that basically means "rawtext" returned by "write"
      should be used to generate hash. Usually, "write" and "read" return
      different booleans. And "raw" returns a same boolean as "write".

      Note: The 'raw' transform is used for changegroup generation and in some
      debug commands. In this case the transform only indicates whether the
      contents can be used for hash integrity checks.
    """
    insertflagprocessor(flag, processor, flagprocessors)


def insertflagprocessor(flag, processor, flagprocessors):
    if not flag & REVIDX_KNOWN_FLAGS:
        msg = _(b"cannot register processor on unknown flag '%#x'.") % flag
        raise error.ProgrammingError(msg)
    if flag not in REVIDX_FLAGS_ORDER:
        msg = _(b"flag '%#x' undefined in REVIDX_FLAGS_ORDER.") % flag
        raise error.ProgrammingError(msg)
    if flag in flagprocessors:
        msg = _(b"cannot register multiple processors on flag '%#x'.") % flag
        raise error.Abort(msg)
    flagprocessors[flag] = processor


def processflagswrite(revlog, text, flags):
    """Inspect revision data flags and applies write transformations defined
    by registered flag processors.

    ``text`` - the revision data to process
    ``flags`` - the revision flags

    This method processes the flags in the order (or reverse order if
    ``operation`` is 'write') defined by REVIDX_FLAGS_ORDER, applying the
    flag processors registered for present flags. The order of flags defined
    in REVIDX_FLAGS_ORDER needs to be stable to allow non-commutativity.

    Returns a 2-tuple of ``(text, validatehash)`` where ``text`` is the
    processed text and ``validatehash`` is a bool indicating whether the
    returned text should be checked for hash integrity.
    """
    return _processflagsfunc(
        revlog,
        text,
        flags,
        b'write',
    )[:2]


def processflagsread(revlog, text, flags):
    """Inspect revision data flags and applies read transformations defined
    by registered flag processors.

    ``text`` - the revision data to process
    ``flags`` - the revision flags
    ``raw`` - an optional argument describing if the raw transform should be
    applied.

    This method processes the flags in the order (or reverse order if
    ``operation`` is 'write') defined by REVIDX_FLAGS_ORDER, applying the
    flag processors registered for present flags. The order of flags defined
    in REVIDX_FLAGS_ORDER needs to be stable to allow non-commutativity.

    Returns a 2-tuple of ``(text, validatehash)`` where ``text`` is the
    processed text and ``validatehash`` is a bool indicating whether the
    returned text should be checked for hash integrity.
    """
    return _processflagsfunc(revlog, text, flags, b'read')


def processflagsraw(revlog, text, flags):
    """Inspect revision data flags to check is the content hash should be
    validated.

    ``text`` - the revision data to process
    ``flags`` - the revision flags

    This method processes the flags in the order (or reverse order if
    ``operation`` is 'write') defined by REVIDX_FLAGS_ORDER, applying the
    flag processors registered for present flags. The order of flags defined
    in REVIDX_FLAGS_ORDER needs to be stable to allow non-commutativity.

    Returns a 2-tuple of ``(text, validatehash)`` where ``text`` is the
    processed text and ``validatehash`` is a bool indicating whether the
    returned text should be checked for hash integrity.
    """
    return _processflagsfunc(revlog, text, flags, b'raw')[1]


def _processflagsfunc(revlog, text, flags, operation):
    """internal function to process flag on a revlog

    This function is private to this module, code should never needs to call it
    directly."""
    # fast path: no flag processors will run
    if flags == 0:
        return text, True
    if operation not in (b'read', b'write', b'raw'):
        raise error.ProgrammingError(_(b"invalid '%s' operation") % operation)
    # Check all flags are known.
    if flags & ~REVIDX_KNOWN_FLAGS:
        raise revlog._flagserrorclass(
            _(b"incompatible revision flag '%#x'")
            % (flags & ~REVIDX_KNOWN_FLAGS)
        )
    validatehash = True
    # Depending on the operation (read or write), the order might be
    # reversed due to non-commutative transforms.
    orderedflags = REVIDX_FLAGS_ORDER
    if operation == b'write':
        orderedflags = reversed(orderedflags)

    for flag in orderedflags:
        # If a flagprocessor has been registered for a known flag, apply the
        # related operation transform and update result tuple.
        if flag & flags:
            vhash = True

            if flag not in revlog._flagprocessors:
                hint = None
                if flag == REVIDX_EXTSTORED:
                    hint = _(b"the lfs extension must be enabled")

                message = _(b"missing processor for flag '%#x'") % flag
                raise revlog._flagserrorclass(message, hint=hint)

            processor = revlog._flagprocessors[flag]
            if processor is not None:
                readtransform, writetransform, rawtransform = processor

                if operation == b'raw':
                    vhash = rawtransform(revlog, text)
                elif operation == b'read':
                    text, vhash = readtransform(revlog, text)
                else:  # write operation
                    text, vhash = writetransform(revlog, text)
            validatehash = validatehash and vhash

    return text, validatehash
