# scmutil.py - Mercurial core utility functions
#
#  Copyright Olivia Mackall <olivia@selenic.com> and other
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from . import repoview


def cachetocopy(srcrepo):
    """return the list of cache file valuable to copy during a clone"""
    # In local clones we're copying all nodes, not just served
    # ones. Therefore copy all branch caches over.
    cachefiles = [b'branch2']
    cachefiles += [b'branch2-%s' % f for f in repoview.filtertable]
    cachefiles += [b'branch3']
    cachefiles += [b'branch3-%s' % f for f in repoview.filtertable]
    cachefiles += [b'rbc-names-v1', b'rbc-revs-v1']
    cachefiles += [b'tags2']
    cachefiles += [b'tags2-%s' % f for f in repoview.filtertable]
    cachefiles += [b'hgtagsfnodes1']
    return cachefiles
