# Infinite push
#
# Copyright 2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""
    [infinitepush]
    # Server-side option. Used only if indextype=disk.
    # Filesystem path to the index store
    indexpath = PATH
"""

from __future__ import absolute_import

import os

from mercurial import util

from mercurial.utils import stringutil

from . import indexapi


class fileindexapi(indexapi.indexapi):
    def __init__(self, repo):
        super(fileindexapi, self).__init__()
        self._repo = repo
        root = repo.ui.config(b'infinitepush', b'indexpath')
        if not root:
            root = os.path.join(b'scratchbranches', b'index')

        self._nodemap = os.path.join(root, b'nodemap')
        self._bookmarkmap = os.path.join(root, b'bookmarkmap')
        self._metadatamap = os.path.join(root, b'nodemetadatamap')
        self._lock = None

    def __enter__(self):
        self._lock = self._repo.wlock()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self._lock:
            self._lock.__exit__(exc_type, exc_val, exc_tb)

    def addbundle(self, bundleid, nodesctx):
        for node in nodesctx:
            nodepath = os.path.join(self._nodemap, node.hex())
            self._write(nodepath, bundleid)

    def addbookmark(self, bookmark, node):
        bookmarkpath = os.path.join(self._bookmarkmap, bookmark)
        self._write(bookmarkpath, node)

    def addmanybookmarks(self, bookmarks):
        for bookmark, node in bookmarks.items():
            self.addbookmark(bookmark, node)

    def deletebookmarks(self, patterns):
        for pattern in patterns:
            for bookmark, _ in self._listbookmarks(pattern):
                bookmarkpath = os.path.join(self._bookmarkmap, bookmark)
                self._delete(bookmarkpath)

    def getbundle(self, node):
        nodepath = os.path.join(self._nodemap, node)
        return self._read(nodepath)

    def getnode(self, bookmark):
        bookmarkpath = os.path.join(self._bookmarkmap, bookmark)
        return self._read(bookmarkpath)

    def getbookmarks(self, query):
        return dict(self._listbookmarks(query))

    def saveoptionaljsonmetadata(self, node, jsonmetadata):
        vfs = self._repo.vfs
        vfs.write(os.path.join(self._metadatamap, node), jsonmetadata)

    def _listbookmarks(self, pattern):
        if pattern.endswith(b'*'):
            pattern = b're:^' + pattern[:-1] + b'.*'
        kind, pat, matcher = stringutil.stringmatcher(pattern)
        prefixlen = len(self._bookmarkmap) + 1
        for dirpath, _, books in self._repo.vfs.walk(self._bookmarkmap):
            for book in books:
                bookmark = os.path.join(dirpath, book)[prefixlen:]
                bookmark = util.pconvert(bookmark)
                if not matcher(bookmark):
                    continue
                yield bookmark, self._read(os.path.join(dirpath, book))

    def _write(self, path, value):
        vfs = self._repo.vfs
        dirname = vfs.dirname(path)
        if not vfs.exists(dirname):
            vfs.makedirs(dirname)

        vfs.write(path, value)

    def _read(self, path):
        vfs = self._repo.vfs
        if not vfs.exists(path):
            return None
        return vfs.read(path)

    def _delete(self, path):
        vfs = self._repo.vfs
        if not vfs.exists(path):
            return
        return vfs.unlink(path)
