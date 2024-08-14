# typelib.py - type hint aliases and support
#
# Copyright 2022 Matt Harbison <matt_harbison@yahoo.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import typing

# Note: this is slightly different from pycompat.TYPE_CHECKING, as using
# pycompat causes the BinaryIO_Proxy type to be resolved to ``object`` when
# used as the base class during a pytype run.
TYPE_CHECKING = typing.TYPE_CHECKING


# The BinaryIO class provides empty methods, which at runtime means that
# ``__getattr__`` on the proxy classes won't get called for the methods that
# should delegate to the internal object.  So to avoid runtime changes because
# of the required typing inheritance, just use BinaryIO when typechecking, and
# ``object`` otherwise.
if TYPE_CHECKING:
    from typing import (
        BinaryIO,
    )

    BinaryIO_Proxy = BinaryIO
else:
    BinaryIO_Proxy = object
