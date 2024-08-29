# pushkey.py - dispatching for pushing and pulling keys
#
# Copyright 2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from . import (
    bookmarks,
    encoding,
    obsolete,
    phases,
)


def _nslist(repo):
    n = {}
    for k in _namespaces:
        n[k] = b""
    if not obsolete.isenabled(repo, obsolete.exchangeopt):
        n.pop(b'obsolete')
    return n


_namespaces = {
    b"namespaces": (lambda *x: False, _nslist),
    b"bookmarks": (bookmarks.pushbookmark, bookmarks.listbookmarks),
    b"phases": (phases.pushphase, phases.listphases),
    b"obsolete": (obsolete.pushmarker, obsolete.listmarkers),
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


encode = encoding.fromlocal

decode = encoding.tolocal


def encodekeys(keys):
    """encode the content of a pushkey namespace for exchange over the wire"""
    return b'\n'.join([b'%s\t%s' % (encode(k), encode(v)) for k, v in keys])


def decodekeys(data):
    """decode the content of a pushkey namespace from exchange over the wire"""
    result = {}
    for l in data.splitlines():
        k, v = l.split(b'\t')
        result[decode(k)] = decode(v)
    return result
