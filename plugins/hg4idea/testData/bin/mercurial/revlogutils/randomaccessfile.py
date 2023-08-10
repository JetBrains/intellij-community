# Copyright Mercurial Contributors
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import contextlib

from ..i18n import _
from .. import (
    error,
    util,
)


_MAX_CACHED_CHUNK_SIZE = 1048576  # 1 MiB

PARTIAL_READ_MSG = _(
    b'partial read of revlog %s; expected %d bytes from offset %d, got %d'
)


def _is_power_of_two(n):
    return (n & (n - 1) == 0) and n != 0


class randomaccessfile(object):
    """Accessing arbitrary chuncks of data within a file, with some caching"""

    def __init__(
        self,
        opener,
        filename,
        default_cached_chunk_size,
        initial_cache=None,
    ):
        # Required by bitwise manipulation below
        assert _is_power_of_two(default_cached_chunk_size)

        self.opener = opener
        self.filename = filename
        self.default_cached_chunk_size = default_cached_chunk_size
        self.writing_handle = None  # This is set from revlog.py
        self.reading_handle = None
        self._cached_chunk = b''
        self._cached_chunk_position = 0  # Offset from the start of the file
        if initial_cache:
            self._cached_chunk_position, self._cached_chunk = initial_cache

    def clear_cache(self):
        self._cached_chunk = b''
        self._cached_chunk_position = 0

    def _open(self, mode=b'r'):
        """Return a file object"""
        return self.opener(self.filename, mode=mode)

    @contextlib.contextmanager
    def _open_read(self, existing_file_obj=None):
        """File object suitable for reading data"""
        # Use explicit file handle, if given.
        if existing_file_obj is not None:
            yield existing_file_obj

        # Use a file handle being actively used for writes, if available.
        # There is some danger to doing this because reads will seek the
        # file. However, revlog._writeentry performs a SEEK_END before all
        # writes, so we should be safe.
        elif self.writing_handle:
            yield self.writing_handle

        elif self.reading_handle:
            yield self.reading_handle

        # Otherwise open a new file handle.
        else:
            with self._open() as fp:
                yield fp

    @contextlib.contextmanager
    def reading(self):
        """Context manager that keeps the file open for reading"""
        if (
            self.reading_handle is None
            and self.writing_handle is None
            and self.filename is not None
        ):
            with self._open() as fp:
                self.reading_handle = fp
                try:
                    yield
                finally:
                    self.reading_handle = None
        else:
            yield

    def read_chunk(self, offset, length, existing_file_obj=None):
        """Read a chunk of bytes from the file.

        Accepts an absolute offset, length to read, and an optional existing
        file handle to read from.

        If an existing file handle is passed, it will be seeked and the
        original seek position will NOT be restored.

        Returns a str or buffer of raw byte data.

        Raises if the requested number of bytes could not be read.
        """
        end = offset + length
        cache_start = self._cached_chunk_position
        cache_end = cache_start + len(self._cached_chunk)
        # Is the requested chunk within the cache?
        if cache_start <= offset and end <= cache_end:
            if cache_start == offset and end == cache_end:
                return self._cached_chunk  # avoid a copy
            relative_start = offset - cache_start
            return util.buffer(self._cached_chunk, relative_start, length)

        return self._read_and_update_cache(offset, length, existing_file_obj)

    def _read_and_update_cache(self, offset, length, existing_file_obj=None):
        # Cache data both forward and backward around the requested
        # data, in a fixed size window. This helps speed up operations
        # involving reading the revlog backwards.
        real_offset = offset & ~(self.default_cached_chunk_size - 1)
        real_length = (
            (offset + length + self.default_cached_chunk_size)
            & ~(self.default_cached_chunk_size - 1)
        ) - real_offset
        with self._open_read(existing_file_obj) as file_obj:
            file_obj.seek(real_offset)
            data = file_obj.read(real_length)

        self._add_cached_chunk(real_offset, data)

        relative_offset = offset - real_offset
        got = len(data) - relative_offset
        if got < length:
            message = PARTIAL_READ_MSG % (self.filename, length, offset, got)
            raise error.RevlogError(message)

        if offset != real_offset or real_length != length:
            return util.buffer(data, relative_offset, length)
        return data

    def _add_cached_chunk(self, offset, data):
        """Add to or replace the cached data chunk.

        Accepts an absolute offset and the data that is at that location.
        """
        if (
            self._cached_chunk_position + len(self._cached_chunk) == offset
            and len(self._cached_chunk) + len(data) < _MAX_CACHED_CHUNK_SIZE
        ):
            # add to existing cache
            self._cached_chunk += data
        else:
            self._cached_chunk = data
            self._cached_chunk_position = offset
