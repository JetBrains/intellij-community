# pushkey.py - dispatching for pushing and pulling keys
#
# Copyright 2010 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import bookmarks, phases, obsolete

def _nslist(repo):
    n = {}
    for k in _namespaces:
        n[k] = ""
    if not obsolete._enabled:
        n.pop('obsolete')
    return n

_namespaces = {"namespaces": (lambda *x: False, _nslist),
               "bookmarks": (bookmarks.pushbookmark, bookmarks.listbookmarks),
               "phases": (phases.pushphase, phases.listphases),
               "obsolete": (obsolete.pushmarker, obsolete.listmarkers),
              }

def register(namespace, pushkey, listkeys):
    _namespaces[namespace] = (pushkey, listkeys)

def _get(namespace):
    return _namespaces.get(namespace, (lambda *x: False, lambda *x: {}))

def push(repo, namespace, key, old, new):
    '''should succeed iff value was old'''
    pk = _get(namespace)[0]
    return pk(repo, key, old, new)

def list(repo, namespace):
    '''return a dict'''
    lk = _get(namespace)[1]
    return lk(repo)

