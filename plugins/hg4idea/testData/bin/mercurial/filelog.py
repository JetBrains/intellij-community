# filelog.py - file history class for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import revlog
import re

_mdre = re.compile('\1\n')
def _parsemeta(text):
    """return (metadatadict, keylist, metadatasize)"""
    # text can be buffer, so we can't use .startswith or .index
    if text[:2] != '\1\n':
        return None, None, None
    s = _mdre.search(text, 2).start()
    mtext = text[2:s]
    meta = {}
    keys = []
    for l in mtext.splitlines():
        k, v = l.split(": ", 1)
        meta[k] = v
        keys.append(k)
    return meta, keys, (s + 2)

def _packmeta(meta, keys=None):
    if not keys:
        keys = sorted(meta.iterkeys())
    return "".join("%s: %s\n" % (k, meta[k]) for k in keys)

class filelog(revlog.revlog):
    def __init__(self, opener, path):
        revlog.revlog.__init__(self, opener,
                        "/".join(("data", path + ".i")))

    def read(self, node):
        t = self.revision(node)
        if not t.startswith('\1\n'):
            return t
        s = t.index('\1\n', 2)
        return t[s + 2:]

    def add(self, text, meta, transaction, link, p1=None, p2=None):
        if meta or text.startswith('\1\n'):
            text = "\1\n%s\1\n%s" % (_packmeta(meta), text)
        return self.addrevision(text, transaction, link, p1, p2)

    def renamed(self, node):
        if self.parents(node)[0] != revlog.nullid:
            return False
        t = self.revision(node)
        m = _parsemeta(t)[0]
        if m and "copy" in m:
            return (m["copy"], revlog.bin(m["copyrev"]))
        return False

    def size(self, rev):
        """return the size of a given revision"""

        # for revisions with renames, we have to go the slow way
        node = self.node(rev)
        if self.renamed(node):
            return len(self.read(node))

        # XXX if self.read(node).startswith("\1\n"), this returns (size+4)
        return revlog.revlog.size(self, rev)

    def cmp(self, node, text):
        """compare text with a given file revision

        returns True if text is different than what is stored.
        """

        t = text
        if text.startswith('\1\n'):
            t = '\1\n\1\n' + text

        samehashes = not revlog.revlog.cmp(self, node, t)
        if samehashes:
            return False

        # renaming a file produces a different hash, even if the data
        # remains unchanged. Check if it's the case (slow):
        if self.renamed(node):
            t2 = self.read(node)
            return t2 != text

        return True

    def _file(self, f):
        return filelog(self.opener, f)
