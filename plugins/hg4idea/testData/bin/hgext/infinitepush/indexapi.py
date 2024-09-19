# Infinite push
#
# Copyright 2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import


class indexapi(object):
    """Class that manages access to infinitepush index.

    This class is a context manager and all write operations (like
    deletebookmarks, addbookmark etc) should use `with` statement:

      with index:
          index.deletebookmarks(...)
          ...
    """

    def __init__(self):
        """Initializes the metadata store connection."""

    def close(self):
        """Cleans up the metadata store connection."""

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass

    def addbundle(self, bundleid, nodesctx):
        """Takes a bundleid and a list of node contexts for each node
        in that bundle and records that."""
        raise NotImplementedError()

    def addbookmark(self, bookmark, node):
        """Takes a bookmark name and hash, and records mapping in the metadata
        store."""
        raise NotImplementedError()

    def addmanybookmarks(self, bookmarks):
        """Takes a dict with mapping from bookmark to hash and records mapping
        in the metadata store."""
        raise NotImplementedError()

    def deletebookmarks(self, patterns):
        """Accepts list of bookmarks and deletes them."""
        raise NotImplementedError()

    def getbundle(self, node):
        """Returns the bundleid for the bundle that contains the given node."""
        raise NotImplementedError()

    def getnode(self, bookmark):
        """Returns the node for the given bookmark. None if it doesn't exist."""
        raise NotImplementedError()

    def getbookmarks(self, query):
        """Returns bookmarks that match the query"""
        raise NotImplementedError()

    def saveoptionaljsonmetadata(self, node, jsonmetadata):
        """Saves optional metadata for a given node"""
        raise NotImplementedError()


class indexexception(Exception):
    pass
