# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# based on bundleheads extension by Gregory Szorc <gps@mozilla.com>

from __future__ import absolute_import

import abc
import os
import subprocess

from mercurial.node import hex
from mercurial.pycompat import open
from mercurial import pycompat
from mercurial.utils import (
    hashutil,
    procutil,
)


class BundleWriteException(Exception):
    pass


class BundleReadException(Exception):
    pass


class abstractbundlestore(object):  # pytype: disable=ignored-metaclass
    """Defines the interface for bundle stores.

    A bundle store is an entity that stores raw bundle data. It is a simple
    key-value store. However, the keys are chosen by the store. The keys can
    be any Python object understood by the corresponding bundle index (see
    ``abstractbundleindex`` below).
    """

    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def write(self, data):
        """Write bundle data to the store.

        This function receives the raw data to be written as a str.
        Throws BundleWriteException
        The key of the written data MUST be returned.
        """

    @abc.abstractmethod
    def read(self, key):
        """Obtain bundle data for a key.

        Returns None if the bundle isn't known.
        Throws BundleReadException
        The returned object should be a file object supporting read()
        and close().
        """


class filebundlestore(object):
    """bundle store in filesystem

    meant for storing bundles somewhere on disk and on network filesystems
    """

    def __init__(self, ui, repo):
        self.ui = ui
        self.repo = repo
        self.storepath = ui.configpath(b'scratchbranch', b'storepath')
        if not self.storepath:
            self.storepath = self.repo.vfs.join(
                b"scratchbranches", b"filebundlestore"
            )
        if not os.path.exists(self.storepath):
            os.makedirs(self.storepath)

    def _dirpath(self, hashvalue):
        """First two bytes of the hash are the name of the upper
        level directory, next two bytes are the name of the
        next level directory"""
        return os.path.join(self.storepath, hashvalue[0:2], hashvalue[2:4])

    def _filepath(self, filename):
        return os.path.join(self._dirpath(filename), filename)

    def write(self, data):
        filename = hex(hashutil.sha1(data).digest())
        dirpath = self._dirpath(filename)

        if not os.path.exists(dirpath):
            os.makedirs(dirpath)

        with open(self._filepath(filename), b'wb') as f:
            f.write(data)

        return filename

    def read(self, key):
        try:
            with open(self._filepath(key), b'rb') as f:
                return f.read()
        except IOError:
            return None


def format_placeholders_args(args, filename=None, handle=None):
    """Formats `args` with Infinitepush replacements.

    Hack to get `str.format()`-ed strings working in a BC way with
    bytes.
    """
    formatted_args = []
    for arg in args:
        if filename and arg == b'{filename}':
            formatted_args.append(filename)
        elif handle and arg == b'{handle}':
            formatted_args.append(handle)
        else:
            formatted_args.append(arg)
    return formatted_args


class externalbundlestore(abstractbundlestore):
    def __init__(self, put_binary, put_args, get_binary, get_args):
        """
        `put_binary` - path to binary file which uploads bundle to external
            storage and prints key to stdout
        `put_args` - format string with additional args to `put_binary`
                     {filename} replacement field can be used.
        `get_binary` - path to binary file which accepts filename and key
            (in that order), downloads bundle from store and saves it to file
        `get_args` - format string with additional args to `get_binary`.
                     {filename} and {handle} replacement field can be used.
        """

        self.put_args = put_args
        self.get_args = get_args
        self.put_binary = put_binary
        self.get_binary = get_binary

    def _call_binary(self, args):
        p = subprocess.Popen(
            pycompat.rapply(procutil.tonativestr, args),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            close_fds=True,
        )
        stdout, stderr = p.communicate()
        returncode = p.returncode
        return returncode, stdout, stderr

    def write(self, data):
        # Won't work on windows because you can't open file second time without
        # closing it
        # TODO: rewrite without str.format() and replace NamedTemporaryFile()
        # with pycompat.namedtempfile()
        with pycompat.namedtempfile() as temp:
            temp.write(data)
            temp.flush()
            temp.seek(0)
            formatted_args = format_placeholders_args(
                self.put_args, filename=temp.name
            )
            returncode, stdout, stderr = self._call_binary(
                [self.put_binary] + formatted_args
            )

            if returncode != 0:
                raise BundleWriteException(
                    b'Failed to upload to external store: %s' % stderr
                )
            stdout_lines = stdout.splitlines()
            if len(stdout_lines) == 1:
                return stdout_lines[0]
            else:
                raise BundleWriteException(
                    b'Bad output from %s: %s' % (self.put_binary, stdout)
                )

    def read(self, handle):
        # Won't work on windows because you can't open file second time without
        # closing it
        with pycompat.namedtempfile() as temp:
            formatted_args = format_placeholders_args(
                self.get_args, filename=temp.name, handle=handle
            )
            returncode, stdout, stderr = self._call_binary(
                [self.get_binary] + formatted_args
            )

            if returncode != 0:
                raise BundleReadException(
                    b'Failed to download from external store: %s' % stderr
                )
            return temp.read()
