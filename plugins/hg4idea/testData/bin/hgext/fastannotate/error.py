# Copyright 2016-present Facebook. All Rights Reserved.
#
# error: errors used in fastannotate
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


class CorruptedFileError(Exception):
    pass


class CannotReuseError(Exception):
    """cannot reuse or update the cache incrementally"""
