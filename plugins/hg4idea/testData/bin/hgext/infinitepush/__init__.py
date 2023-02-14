# Infinite push
#
# Copyright 2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
""" store some pushes in a remote blob store on the server (EXPERIMENTAL)

IMPORTANT: if you use this extension, please contact
mercurial-devel@mercurial-scm.org ASAP. This extension is believed to
be unused and barring learning of users of this functionality, we will
delete this code at the end of 2020.

    [infinitepush]
    # Server-side and client-side option. Pattern of the infinitepush bookmark
    branchpattern = PATTERN

    # Server or client
    server = False

    # Server-side option. Possible values: 'disk' or 'sql'. Fails if not set
    indextype = disk

    # Server-side option. Used only if indextype=sql.
    # Format: 'IP:PORT:DB_NAME:USER:PASSWORD'
    sqlhost = IP:PORT:DB_NAME:USER:PASSWORD

    # Server-side option. Used only if indextype=disk.
    # Filesystem path to the index store
    indexpath = PATH

    # Server-side option. Possible values: 'disk' or 'external'
    # Fails if not set
    storetype = disk

    # Server-side option.
    # Path to the binary that will save bundle to the bundlestore
    # Formatted cmd line will be passed to it (see `put_args`)
    put_binary = put

    # Serser-side option. Used only if storetype=external.
    # Format cmd-line string for put binary. Placeholder: {filename}
    put_args = {filename}

    # Server-side option.
    # Path to the binary that get bundle from the bundlestore.
    # Formatted cmd line will be passed to it (see `get_args`)
    get_binary = get

    # Serser-side option. Used only if storetype=external.
    # Format cmd-line string for get binary. Placeholders: {filename} {handle}
    get_args = {filename} {handle}

    # Server-side option
    logfile = FIlE

    # Server-side option
    loglevel = DEBUG

    # Server-side option. Used only if indextype=sql.
    # Sets mysql wait_timeout option.
    waittimeout = 300

    # Server-side option. Used only if indextype=sql.
    # Sets mysql innodb_lock_wait_timeout option.
    locktimeout = 120

    # Server-side option. Used only if indextype=sql.
    # Name of the repository
    reponame = ''

    # Client-side option. Used by --list-remote option. List of remote scratch
    # patterns to list if no patterns are specified.
    defaultremotepatterns = ['*']

    # Instructs infinitepush to forward all received bundle2 parts to the
    # bundle for storage. Defaults to False.
    storeallparts = True

    # routes each incoming push to the bundlestore. defaults to False
    pushtobundlestore = True

    [remotenames]
    # Client-side option
    # This option should be set only if remotenames extension is enabled.
    # Whether remote bookmarks are tracked by remotenames extension.
    bookmarks = True
"""

from __future__ import absolute_import

import collections
import contextlib
import errno
import functools
import logging
import os
import random
import re
import socket
import subprocess
import time

from mercurial.node import (
    bin,
    hex,
)

from mercurial.i18n import _

from mercurial.pycompat import (
    getattr,
    open,
)

from mercurial.utils import (
    procutil,
    stringutil,
    urlutil,
)

from mercurial import (
    bundle2,
    changegroup,
    commands,
    discovery,
    encoding,
    error,
    exchange,
    extensions,
    hg,
    localrepo,
    phases,
    pushkey,
    pycompat,
    registrar,
    util,
    wireprototypes,
    wireprotov1peer,
    wireprotov1server,
)

from . import (
    bundleparts,
    common,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'infinitepush',
    b'server',
    default=False,
)
configitem(
    b'infinitepush',
    b'storetype',
    default=b'',
)
configitem(
    b'infinitepush',
    b'indextype',
    default=b'',
)
configitem(
    b'infinitepush',
    b'indexpath',
    default=b'',
)
configitem(
    b'infinitepush',
    b'storeallparts',
    default=False,
)
configitem(
    b'infinitepush',
    b'reponame',
    default=b'',
)
configitem(
    b'scratchbranch',
    b'storepath',
    default=b'',
)
configitem(
    b'infinitepush',
    b'branchpattern',
    default=b'',
)
configitem(
    b'infinitepush',
    b'pushtobundlestore',
    default=False,
)
configitem(
    b'experimental',
    b'server-bundlestore-bookmark',
    default=b'',
)
configitem(
    b'experimental',
    b'infinitepush-scratchpush',
    default=False,
)

experimental = b'experimental'
configbookmark = b'server-bundlestore-bookmark'
configscratchpush = b'infinitepush-scratchpush'

scratchbranchparttype = bundleparts.scratchbranchparttype
revsetpredicate = registrar.revsetpredicate()
templatekeyword = registrar.templatekeyword()
_scratchbranchmatcher = lambda x: False
_maybehash = re.compile('^[a-f0-9]+$').search


def _buildexternalbundlestore(ui):
    put_args = ui.configlist(b'infinitepush', b'put_args', [])
    put_binary = ui.config(b'infinitepush', b'put_binary')
    if not put_binary:
        raise error.Abort(b'put binary is not specified')
    get_args = ui.configlist(b'infinitepush', b'get_args', [])
    get_binary = ui.config(b'infinitepush', b'get_binary')
    if not get_binary:
        raise error.Abort(b'get binary is not specified')
    from . import store

    return store.externalbundlestore(put_binary, put_args, get_binary, get_args)


def _buildsqlindex(ui):
    sqlhost = ui.config(b'infinitepush', b'sqlhost')
    if not sqlhost:
        raise error.Abort(_(b'please set infinitepush.sqlhost'))
    host, port, db, user, password = sqlhost.split(b':')
    reponame = ui.config(b'infinitepush', b'reponame')
    if not reponame:
        raise error.Abort(_(b'please set infinitepush.reponame'))

    logfile = ui.config(b'infinitepush', b'logfile', b'')
    waittimeout = ui.configint(b'infinitepush', b'waittimeout', 300)
    locktimeout = ui.configint(b'infinitepush', b'locktimeout', 120)
    from . import sqlindexapi

    return sqlindexapi.sqlindexapi(
        reponame,
        host,
        port,
        db,
        user,
        password,
        logfile,
        _getloglevel(ui),
        waittimeout=waittimeout,
        locktimeout=locktimeout,
    )


def _getloglevel(ui):
    loglevel = ui.config(b'infinitepush', b'loglevel', b'DEBUG')
    numeric_loglevel = getattr(logging, loglevel.upper(), None)
    if not isinstance(numeric_loglevel, int):
        raise error.Abort(_(b'invalid log level %s') % loglevel)
    return numeric_loglevel


def _tryhoist(ui, remotebookmark):
    """returns a bookmarks with hoisted part removed

    Remotenames extension has a 'hoist' config that allows to use remote
    bookmarks without specifying remote path. For example, 'hg update master'
    works as well as 'hg update remote/master'. We want to allow the same in
    infinitepush.
    """

    if common.isremotebooksenabled(ui):
        hoist = ui.config(b'remotenames', b'hoistedpeer') + b'/'
        if remotebookmark.startswith(hoist):
            return remotebookmark[len(hoist) :]
    return remotebookmark


class bundlestore(object):
    def __init__(self, repo):
        self._repo = repo
        storetype = self._repo.ui.config(b'infinitepush', b'storetype')
        if storetype == b'disk':
            from . import store

            self.store = store.filebundlestore(self._repo.ui, self._repo)
        elif storetype == b'external':
            self.store = _buildexternalbundlestore(self._repo.ui)
        else:
            raise error.Abort(
                _(b'unknown infinitepush store type specified %s') % storetype
            )

        indextype = self._repo.ui.config(b'infinitepush', b'indextype')
        if indextype == b'disk':
            from . import fileindexapi

            self.index = fileindexapi.fileindexapi(self._repo)
        elif indextype == b'sql':
            self.index = _buildsqlindex(self._repo.ui)
        else:
            raise error.Abort(
                _(b'unknown infinitepush index type specified %s') % indextype
            )


def _isserver(ui):
    return ui.configbool(b'infinitepush', b'server')


def reposetup(ui, repo):
    if _isserver(ui) and repo.local():
        repo.bundlestore = bundlestore(repo)


def extsetup(ui):
    commonsetup(ui)
    if _isserver(ui):
        serverextsetup(ui)
    else:
        clientextsetup(ui)


def commonsetup(ui):
    wireprotov1server.commands[b'listkeyspatterns'] = (
        wireprotolistkeyspatterns,
        b'namespace patterns',
    )
    scratchbranchpat = ui.config(b'infinitepush', b'branchpattern')
    if scratchbranchpat:
        global _scratchbranchmatcher
        kind, pat, _scratchbranchmatcher = stringutil.stringmatcher(
            scratchbranchpat
        )


def serverextsetup(ui):
    origpushkeyhandler = bundle2.parthandlermapping[b'pushkey']

    def newpushkeyhandler(*args, **kwargs):
        bundle2pushkey(origpushkeyhandler, *args, **kwargs)

    newpushkeyhandler.params = origpushkeyhandler.params
    bundle2.parthandlermapping[b'pushkey'] = newpushkeyhandler

    orighandlephasehandler = bundle2.parthandlermapping[b'phase-heads']
    newphaseheadshandler = lambda *args, **kwargs: bundle2handlephases(
        orighandlephasehandler, *args, **kwargs
    )
    newphaseheadshandler.params = orighandlephasehandler.params
    bundle2.parthandlermapping[b'phase-heads'] = newphaseheadshandler

    extensions.wrapfunction(
        localrepo.localrepository, b'listkeys', localrepolistkeys
    )
    wireprotov1server.commands[b'lookup'] = (
        _lookupwrap(wireprotov1server.commands[b'lookup'][0]),
        b'key',
    )
    extensions.wrapfunction(exchange, b'getbundlechunks', getbundlechunks)

    extensions.wrapfunction(bundle2, b'processparts', processparts)


def clientextsetup(ui):
    entry = extensions.wrapcommand(commands.table, b'push', _push)

    entry[1].append(
        (
            b'',
            b'bundle-store',
            None,
            _(b'force push to go to bundle store (EXPERIMENTAL)'),
        )
    )

    extensions.wrapcommand(commands.table, b'pull', _pull)

    extensions.wrapfunction(discovery, b'checkheads', _checkheads)

    wireprotov1peer.wirepeer.listkeyspatterns = listkeyspatterns

    partorder = exchange.b2partsgenorder
    index = partorder.index(b'changeset')
    partorder.insert(
        index, partorder.pop(partorder.index(scratchbranchparttype))
    )


def _checkheads(orig, pushop):
    if pushop.ui.configbool(experimental, configscratchpush, False):
        return
    return orig(pushop)


def wireprotolistkeyspatterns(repo, proto, namespace, patterns):
    patterns = wireprototypes.decodelist(patterns)
    d = pycompat.iteritems(repo.listkeys(encoding.tolocal(namespace), patterns))
    return pushkey.encodekeys(d)


def localrepolistkeys(orig, self, namespace, patterns=None):
    if namespace == b'bookmarks' and patterns:
        index = self.bundlestore.index
        results = {}
        bookmarks = orig(self, namespace)
        for pattern in patterns:
            results.update(index.getbookmarks(pattern))
            if pattern.endswith(b'*'):
                pattern = b're:^' + pattern[:-1] + b'.*'
            kind, pat, matcher = stringutil.stringmatcher(pattern)
            for bookmark, node in pycompat.iteritems(bookmarks):
                if matcher(bookmark):
                    results[bookmark] = node
        return results
    else:
        return orig(self, namespace)


@wireprotov1peer.batchable
def listkeyspatterns(self, namespace, patterns):
    if not self.capable(b'pushkey'):
        yield {}, None
    f = wireprotov1peer.future()
    self.ui.debug(b'preparing listkeys for "%s"\n' % namespace)
    yield {
        b'namespace': encoding.fromlocal(namespace),
        b'patterns': wireprototypes.encodelist(patterns),
    }, f
    d = f.value
    self.ui.debug(
        b'received listkey for "%s": %i bytes\n' % (namespace, len(d))
    )
    yield pushkey.decodekeys(d)


def _readbundlerevs(bundlerepo):
    return list(bundlerepo.revs(b'bundle()'))


def _includefilelogstobundle(bundlecaps, bundlerepo, bundlerevs, ui):
    """Tells remotefilelog to include all changed files to the changegroup

    By default remotefilelog doesn't include file content to the changegroup.
    But we need to include it if we are fetching from bundlestore.
    """
    changedfiles = set()
    cl = bundlerepo.changelog
    for r in bundlerevs:
        # [3] means changed files
        changedfiles.update(cl.read(r)[3])
    if not changedfiles:
        return bundlecaps

    changedfiles = b'\0'.join(changedfiles)
    newcaps = []
    appended = False
    for cap in bundlecaps or []:
        if cap.startswith(b'excludepattern='):
            newcaps.append(b'\0'.join((cap, changedfiles)))
            appended = True
        else:
            newcaps.append(cap)
    if not appended:
        # Not found excludepattern cap. Just append it
        newcaps.append(b'excludepattern=' + changedfiles)

    return newcaps


def _rebundle(bundlerepo, bundleroots, unknownhead):
    """
    Bundle may include more revision then user requested. For example,
    if user asks for revision but bundle also consists its descendants.
    This function will filter out all revision that user is not requested.
    """
    parts = []

    version = b'02'
    outgoing = discovery.outgoing(
        bundlerepo, commonheads=bundleroots, ancestorsof=[unknownhead]
    )
    cgstream = changegroup.makestream(bundlerepo, outgoing, version, b'pull')
    cgstream = util.chunkbuffer(cgstream).read()
    cgpart = bundle2.bundlepart(b'changegroup', data=cgstream)
    cgpart.addparam(b'version', version)
    parts.append(cgpart)

    return parts


def _getbundleroots(oldrepo, bundlerepo, bundlerevs):
    cl = bundlerepo.changelog
    bundleroots = []
    for rev in bundlerevs:
        node = cl.node(rev)
        parents = cl.parents(node)
        for parent in parents:
            # include all revs that exist in the main repo
            # to make sure that bundle may apply client-side
            if parent in oldrepo:
                bundleroots.append(parent)
    return bundleroots


def _needsrebundling(head, bundlerepo):
    bundleheads = list(bundlerepo.revs(b'heads(bundle())'))
    return not (
        len(bundleheads) == 1 and bundlerepo[bundleheads[0]].node() == head
    )


def _generateoutputparts(head, bundlerepo, bundleroots, bundlefile):
    """generates bundle that will be send to the user

    returns tuple with raw bundle string and bundle type
    """
    parts = []
    if not _needsrebundling(head, bundlerepo):
        with util.posixfile(bundlefile, b"rb") as f:
            unbundler = exchange.readbundle(bundlerepo.ui, f, bundlefile)
            if isinstance(unbundler, changegroup.cg1unpacker):
                part = bundle2.bundlepart(
                    b'changegroup', data=unbundler._stream.read()
                )
                part.addparam(b'version', b'01')
                parts.append(part)
            elif isinstance(unbundler, bundle2.unbundle20):
                haschangegroup = False
                for part in unbundler.iterparts():
                    if part.type == b'changegroup':
                        haschangegroup = True
                    newpart = bundle2.bundlepart(part.type, data=part.read())
                    for key, value in pycompat.iteritems(part.params):
                        newpart.addparam(key, value)
                    parts.append(newpart)

                if not haschangegroup:
                    raise error.Abort(
                        b'unexpected bundle without changegroup part, '
                        + b'head: %s' % hex(head),
                        hint=b'report to administrator',
                    )
            else:
                raise error.Abort(b'unknown bundle type')
    else:
        parts = _rebundle(bundlerepo, bundleroots, head)

    return parts


def getbundlechunks(orig, repo, source, heads=None, bundlecaps=None, **kwargs):
    heads = heads or []
    # newheads are parents of roots of scratch bundles that were requested
    newphases = {}
    scratchbundles = []
    newheads = []
    scratchheads = []
    nodestobundle = {}
    allbundlestocleanup = []
    try:
        for head in heads:
            if not repo.changelog.index.has_node(head):
                if head not in nodestobundle:
                    newbundlefile = common.downloadbundle(repo, head)
                    bundlepath = b"bundle:%s+%s" % (repo.root, newbundlefile)
                    bundlerepo = hg.repository(repo.ui, bundlepath)

                    allbundlestocleanup.append((bundlerepo, newbundlefile))
                    bundlerevs = set(_readbundlerevs(bundlerepo))
                    bundlecaps = _includefilelogstobundle(
                        bundlecaps, bundlerepo, bundlerevs, repo.ui
                    )
                    cl = bundlerepo.changelog
                    bundleroots = _getbundleroots(repo, bundlerepo, bundlerevs)
                    for rev in bundlerevs:
                        node = cl.node(rev)
                        newphases[hex(node)] = str(phases.draft)
                        nodestobundle[node] = (
                            bundlerepo,
                            bundleroots,
                            newbundlefile,
                        )

                scratchbundles.append(
                    _generateoutputparts(head, *nodestobundle[head])
                )
                newheads.extend(bundleroots)
                scratchheads.append(head)
    finally:
        for bundlerepo, bundlefile in allbundlestocleanup:
            bundlerepo.close()
            try:
                os.unlink(bundlefile)
            except (IOError, OSError):
                # if we can't cleanup the file then just ignore the error,
                # no need to fail
                pass

    pullfrombundlestore = bool(scratchbundles)
    wrappedchangegrouppart = False
    wrappedlistkeys = False
    oldchangegrouppart = exchange.getbundle2partsmapping[b'changegroup']
    try:

        def _changegrouppart(bundler, *args, **kwargs):
            # Order is important here. First add non-scratch part
            # and only then add parts with scratch bundles because
            # non-scratch part contains parents of roots of scratch bundles.
            result = oldchangegrouppart(bundler, *args, **kwargs)
            for bundle in scratchbundles:
                for part in bundle:
                    bundler.addpart(part)
            return result

        exchange.getbundle2partsmapping[b'changegroup'] = _changegrouppart
        wrappedchangegrouppart = True

        def _listkeys(orig, self, namespace):
            origvalues = orig(self, namespace)
            if namespace == b'phases' and pullfrombundlestore:
                if origvalues.get(b'publishing') == b'True':
                    # Make repo non-publishing to preserve draft phase
                    del origvalues[b'publishing']
                origvalues.update(newphases)
            return origvalues

        extensions.wrapfunction(
            localrepo.localrepository, b'listkeys', _listkeys
        )
        wrappedlistkeys = True
        heads = list((set(newheads) | set(heads)) - set(scratchheads))
        result = orig(
            repo, source, heads=heads, bundlecaps=bundlecaps, **kwargs
        )
    finally:
        if wrappedchangegrouppart:
            exchange.getbundle2partsmapping[b'changegroup'] = oldchangegrouppart
        if wrappedlistkeys:
            extensions.unwrapfunction(
                localrepo.localrepository, b'listkeys', _listkeys
            )
    return result


def _lookupwrap(orig):
    def _lookup(repo, proto, key):
        localkey = encoding.tolocal(key)

        if isinstance(localkey, str) and _scratchbranchmatcher(localkey):
            scratchnode = repo.bundlestore.index.getnode(localkey)
            if scratchnode:
                return b"%d %s\n" % (1, scratchnode)
            else:
                return b"%d %s\n" % (
                    0,
                    b'scratch branch %s not found' % localkey,
                )
        else:
            try:
                r = hex(repo.lookup(localkey))
                return b"%d %s\n" % (1, r)
            except Exception as inst:
                if repo.bundlestore.index.getbundle(localkey):
                    return b"%d %s\n" % (1, localkey)
                else:
                    r = stringutil.forcebytestr(inst)
                    return b"%d %s\n" % (0, r)

    return _lookup


def _pull(orig, ui, repo, source=b"default", **opts):
    opts = pycompat.byteskwargs(opts)
    # Copy paste from `pull` command
    source, branches = urlutil.get_unique_pull_path(
        b"infinite-push's pull",
        repo,
        ui,
        source,
        default_branches=opts.get(b'branch'),
    )

    scratchbookmarks = {}
    unfi = repo.unfiltered()
    unknownnodes = []
    for rev in opts.get(b'rev', []):
        if rev not in unfi:
            unknownnodes.append(rev)
    if opts.get(b'bookmark'):
        bookmarks = []
        revs = opts.get(b'rev') or []
        for bookmark in opts.get(b'bookmark'):
            if _scratchbranchmatcher(bookmark):
                # rev is not known yet
                # it will be fetched with listkeyspatterns next
                scratchbookmarks[bookmark] = b'REVTOFETCH'
            else:
                bookmarks.append(bookmark)

        if scratchbookmarks:
            other = hg.peer(repo, opts, source)
            try:
                fetchedbookmarks = other.listkeyspatterns(
                    b'bookmarks', patterns=scratchbookmarks
                )
                for bookmark in scratchbookmarks:
                    if bookmark not in fetchedbookmarks:
                        raise error.Abort(
                            b'remote bookmark %s not found!' % bookmark
                        )
                    scratchbookmarks[bookmark] = fetchedbookmarks[bookmark]
                    revs.append(fetchedbookmarks[bookmark])
            finally:
                other.close()
        opts[b'bookmark'] = bookmarks
        opts[b'rev'] = revs

    if scratchbookmarks or unknownnodes:
        # Set anyincoming to True
        extensions.wrapfunction(
            discovery, b'findcommonincoming', _findcommonincoming
        )
    try:
        # Remote scratch bookmarks will be deleted because remotenames doesn't
        # know about them. Let's save it before pull and restore after
        remotescratchbookmarks = _readscratchremotebookmarks(ui, repo, source)
        result = orig(ui, repo, source, **pycompat.strkwargs(opts))
        # TODO(stash): race condition is possible
        # if scratch bookmarks was updated right after orig.
        # But that's unlikely and shouldn't be harmful.
        if common.isremotebooksenabled(ui):
            remotescratchbookmarks.update(scratchbookmarks)
            _saveremotebookmarks(repo, remotescratchbookmarks, source)
        else:
            _savelocalbookmarks(repo, scratchbookmarks)
        return result
    finally:
        if scratchbookmarks:
            extensions.unwrapfunction(discovery, b'findcommonincoming')


def _readscratchremotebookmarks(ui, repo, other):
    if common.isremotebooksenabled(ui):
        remotenamesext = extensions.find(b'remotenames')
        remotepath = remotenamesext.activepath(repo.ui, other)
        result = {}
        # Let's refresh remotenames to make sure we have it up to date
        # Seems that `repo.names['remotebookmarks']` may return stale bookmarks
        # and it results in deleting scratch bookmarks. Our best guess how to
        # fix it is to use `clearnames()`
        repo._remotenames.clearnames()
        for remotebookmark in repo.names[b'remotebookmarks'].listnames(repo):
            path, bookname = remotenamesext.splitremotename(remotebookmark)
            if path == remotepath and _scratchbranchmatcher(bookname):
                nodes = repo.names[b'remotebookmarks'].nodes(
                    repo, remotebookmark
                )
                if nodes:
                    result[bookname] = hex(nodes[0])
        return result
    else:
        return {}


def _saveremotebookmarks(repo, newbookmarks, remote):
    remotenamesext = extensions.find(b'remotenames')
    remotepath = remotenamesext.activepath(repo.ui, remote)
    branches = collections.defaultdict(list)
    bookmarks = {}
    remotenames = remotenamesext.readremotenames(repo)
    for hexnode, nametype, remote, rname in remotenames:
        if remote != remotepath:
            continue
        if nametype == b'bookmarks':
            if rname in newbookmarks:
                # It's possible if we have a normal bookmark that matches
                # scratch branch pattern. In this case just use the current
                # bookmark node
                del newbookmarks[rname]
            bookmarks[rname] = hexnode
        elif nametype == b'branches':
            # saveremotenames expects 20 byte binary nodes for branches
            branches[rname].append(bin(hexnode))

    for bookmark, hexnode in pycompat.iteritems(newbookmarks):
        bookmarks[bookmark] = hexnode
    remotenamesext.saveremotenames(repo, remotepath, branches, bookmarks)


def _savelocalbookmarks(repo, bookmarks):
    if not bookmarks:
        return
    with repo.wlock(), repo.lock(), repo.transaction(b'bookmark') as tr:
        changes = []
        for scratchbook, node in pycompat.iteritems(bookmarks):
            changectx = repo[node]
            changes.append((scratchbook, changectx.node()))
        repo._bookmarks.applychanges(repo, tr, changes)


def _findcommonincoming(orig, *args, **kwargs):
    common, inc, remoteheads = orig(*args, **kwargs)
    return common, True, remoteheads


def _push(orig, ui, repo, *dests, **opts):
    opts = pycompat.byteskwargs(opts)
    bookmark = opts.get(b'bookmark')
    # we only support pushing one infinitepush bookmark at once
    if len(bookmark) == 1:
        bookmark = bookmark[0]
    else:
        bookmark = b''

    oldphasemove = None
    overrides = {(experimental, configbookmark): bookmark}

    with ui.configoverride(overrides, b'infinitepush'):
        scratchpush = opts.get(b'bundle_store')
        if _scratchbranchmatcher(bookmark):
            scratchpush = True
            # bundle2 can be sent back after push (for example, bundle2
            # containing `pushkey` part to update bookmarks)
            ui.setconfig(experimental, b'bundle2.pushback', True)

        if scratchpush:
            # this is an infinitepush, we don't want the bookmark to be applied
            # rather that should be stored in the bundlestore
            opts[b'bookmark'] = []
            ui.setconfig(experimental, configscratchpush, True)
            oldphasemove = extensions.wrapfunction(
                exchange, b'_localphasemove', _phasemove
            )

        paths = list(urlutil.get_push_paths(repo, ui, dests))
        if len(paths) > 1:
            msg = _(b'cannot push to multiple path with infinitepush')
            raise error.Abort(msg)

        path = paths[0]
        destpath = path.pushloc or path.loc
        # Remote scratch bookmarks will be deleted because remotenames doesn't
        # know about them. Let's save it before push and restore after
        remotescratchbookmarks = _readscratchremotebookmarks(ui, repo, destpath)
        result = orig(ui, repo, *dests, **pycompat.strkwargs(opts))
        if common.isremotebooksenabled(ui):
            if bookmark and scratchpush:
                other = hg.peer(repo, opts, destpath)
                try:
                    fetchedbookmarks = other.listkeyspatterns(
                        b'bookmarks', patterns=[bookmark]
                    )
                    remotescratchbookmarks.update(fetchedbookmarks)
                finally:
                    other.close()
            _saveremotebookmarks(repo, remotescratchbookmarks, destpath)
    if oldphasemove:
        exchange._localphasemove = oldphasemove
    return result


def _deleteinfinitepushbookmarks(ui, repo, path, names):
    """Prune remote names by removing the bookmarks we don't want anymore,
    then writing the result back to disk
    """
    remotenamesext = extensions.find(b'remotenames')

    # remotename format is:
    # (node, nametype ("branches" or "bookmarks"), remote, name)
    nametype_idx = 1
    remote_idx = 2
    name_idx = 3
    remotenames = [
        remotename
        for remotename in remotenamesext.readremotenames(repo)
        if remotename[remote_idx] == path
    ]
    remote_bm_names = [
        remotename[name_idx]
        for remotename in remotenames
        if remotename[nametype_idx] == b"bookmarks"
    ]

    for name in names:
        if name not in remote_bm_names:
            raise error.Abort(
                _(
                    b"infinitepush bookmark '{}' does not exist "
                    b"in path '{}'"
                ).format(name, path)
            )

    bookmarks = {}
    branches = collections.defaultdict(list)
    for node, nametype, remote, name in remotenames:
        if nametype == b"bookmarks" and name not in names:
            bookmarks[name] = node
        elif nametype == b"branches":
            # saveremotenames wants binary nodes for branches
            branches[name].append(bin(node))

    remotenamesext.saveremotenames(repo, path, branches, bookmarks)


def _phasemove(orig, pushop, nodes, phase=phases.public):
    """prevent commits from being marked public

    Since these are going to a scratch branch, they aren't really being
    published."""

    if phase != phases.public:
        orig(pushop, nodes, phase)


@exchange.b2partsgenerator(scratchbranchparttype)
def partgen(pushop, bundler):
    bookmark = pushop.ui.config(experimental, configbookmark)
    scratchpush = pushop.ui.configbool(experimental, configscratchpush)
    if b'changesets' in pushop.stepsdone or not scratchpush:
        return

    if scratchbranchparttype not in bundle2.bundle2caps(pushop.remote):
        return

    pushop.stepsdone.add(b'changesets')
    if not pushop.outgoing.missing:
        pushop.ui.status(_(b'no changes found\n'))
        pushop.cgresult = 0
        return

    # This parameter tells the server that the following bundle is an
    # infinitepush. This let's it switch the part processing to our infinitepush
    # code path.
    bundler.addparam(b"infinitepush", b"True")

    scratchparts = bundleparts.getscratchbranchparts(
        pushop.repo, pushop.remote, pushop.outgoing, pushop.ui, bookmark
    )

    for scratchpart in scratchparts:
        bundler.addpart(scratchpart)

    def handlereply(op):
        # server either succeeds or aborts; no code to read
        pushop.cgresult = 1

    return handlereply


bundle2.capabilities[bundleparts.scratchbranchparttype] = ()


def _getrevs(bundle, oldnode, force, bookmark):
    b'extracts and validates the revs to be imported'
    revs = [bundle[r] for r in bundle.revs(b'sort(bundle())')]

    # new bookmark
    if oldnode is None:
        return revs

    # Fast forward update
    if oldnode in bundle and list(bundle.set(b'bundle() & %s::', oldnode)):
        return revs

    return revs


@contextlib.contextmanager
def logservicecall(logger, service, **kwargs):
    start = time.time()
    logger(service, eventtype=b'start', **kwargs)
    try:
        yield
        logger(
            service,
            eventtype=b'success',
            elapsedms=(time.time() - start) * 1000,
            **kwargs
        )
    except Exception as e:
        logger(
            service,
            eventtype=b'failure',
            elapsedms=(time.time() - start) * 1000,
            errormsg=stringutil.forcebytestr(e),
            **kwargs
        )
        raise


def _getorcreateinfinitepushlogger(op):
    logger = op.records[b'infinitepushlogger']
    if not logger:
        ui = op.repo.ui
        try:
            username = procutil.getuser()
        except Exception:
            username = b'unknown'
        # Generate random request id to be able to find all logged entries
        # for the same request. Since requestid is pseudo-generated it may
        # not be unique, but we assume that (hostname, username, requestid)
        # is unique.
        random.seed()
        requestid = random.randint(0, 2000000000)
        hostname = socket.gethostname()
        logger = functools.partial(
            ui.log,
            b'infinitepush',
            user=username,
            requestid=requestid,
            hostname=hostname,
            reponame=ui.config(b'infinitepush', b'reponame'),
        )
        op.records.add(b'infinitepushlogger', logger)
    else:
        logger = logger[0]
    return logger


def storetobundlestore(orig, repo, op, unbundler):
    """stores the incoming bundle coming from push command to the bundlestore
    instead of applying on the revlogs"""

    repo.ui.status(_(b"storing changesets on the bundlestore\n"))
    bundler = bundle2.bundle20(repo.ui)

    # processing each part and storing it in bundler
    with bundle2.partiterator(repo, op, unbundler) as parts:
        for part in parts:
            bundlepart = None
            if part.type == b'replycaps':
                # This configures the current operation to allow reply parts.
                bundle2._processpart(op, part)
            else:
                bundlepart = bundle2.bundlepart(part.type, data=part.read())
                for key, value in pycompat.iteritems(part.params):
                    bundlepart.addparam(key, value)

                # Certain parts require a response
                if part.type in (b'pushkey', b'changegroup'):
                    if op.reply is not None:
                        rpart = op.reply.newpart(b'reply:%s' % part.type)
                        rpart.addparam(
                            b'in-reply-to', b'%d' % part.id, mandatory=False
                        )
                        rpart.addparam(b'return', b'1', mandatory=False)

            op.records.add(
                part.type,
                {
                    b'return': 1,
                },
            )
            if bundlepart:
                bundler.addpart(bundlepart)

    # storing the bundle in the bundlestore
    buf = util.chunkbuffer(bundler.getchunks())
    fd, bundlefile = pycompat.mkstemp()
    try:
        try:
            fp = os.fdopen(fd, 'wb')
            fp.write(buf.read())
        finally:
            fp.close()
        storebundle(op, {}, bundlefile)
    finally:
        try:
            os.unlink(bundlefile)
        except Exception:
            # we would rather see the original exception
            pass


def processparts(orig, repo, op, unbundler):

    # make sure we don't wrap processparts in case of `hg unbundle`
    if op.source == b'unbundle':
        return orig(repo, op, unbundler)

    # this server routes each push to bundle store
    if repo.ui.configbool(b'infinitepush', b'pushtobundlestore'):
        return storetobundlestore(orig, repo, op, unbundler)

    if unbundler.params.get(b'infinitepush') != b'True':
        return orig(repo, op, unbundler)

    handleallparts = repo.ui.configbool(b'infinitepush', b'storeallparts')

    bundler = bundle2.bundle20(repo.ui)
    cgparams = None
    with bundle2.partiterator(repo, op, unbundler) as parts:
        for part in parts:
            bundlepart = None
            if part.type == b'replycaps':
                # This configures the current operation to allow reply parts.
                bundle2._processpart(op, part)
            elif part.type == bundleparts.scratchbranchparttype:
                # Scratch branch parts need to be converted to normal
                # changegroup parts, and the extra parameters stored for later
                # when we upload to the store. Eventually those parameters will
                # be put on the actual bundle instead of this part, then we can
                # send a vanilla changegroup instead of the scratchbranch part.
                cgversion = part.params.get(b'cgversion', b'01')
                bundlepart = bundle2.bundlepart(
                    b'changegroup', data=part.read()
                )
                bundlepart.addparam(b'version', cgversion)
                cgparams = part.params

                # If we're not dumping all parts into the new bundle, we need to
                # alert the future pushkey and phase-heads handler to skip
                # the part.
                if not handleallparts:
                    op.records.add(
                        scratchbranchparttype + b'_skippushkey', True
                    )
                    op.records.add(
                        scratchbranchparttype + b'_skipphaseheads', True
                    )
            else:
                if handleallparts:
                    # Ideally we would not process any parts, and instead just
                    # forward them to the bundle for storage, but since this
                    # differs from previous behavior, we need to put it behind a
                    # config flag for incremental rollout.
                    bundlepart = bundle2.bundlepart(part.type, data=part.read())
                    for key, value in pycompat.iteritems(part.params):
                        bundlepart.addparam(key, value)

                    # Certain parts require a response
                    if part.type == b'pushkey':
                        if op.reply is not None:
                            rpart = op.reply.newpart(b'reply:pushkey')
                            rpart.addparam(
                                b'in-reply-to', str(part.id), mandatory=False
                            )
                            rpart.addparam(b'return', b'1', mandatory=False)
                else:
                    bundle2._processpart(op, part)

            if handleallparts:
                op.records.add(
                    part.type,
                    {
                        b'return': 1,
                    },
                )
            if bundlepart:
                bundler.addpart(bundlepart)

    # If commits were sent, store them
    if cgparams:
        buf = util.chunkbuffer(bundler.getchunks())
        fd, bundlefile = pycompat.mkstemp()
        try:
            try:
                fp = os.fdopen(fd, 'wb')
                fp.write(buf.read())
            finally:
                fp.close()
            storebundle(op, cgparams, bundlefile)
        finally:
            try:
                os.unlink(bundlefile)
            except Exception:
                # we would rather see the original exception
                pass


def storebundle(op, params, bundlefile):
    log = _getorcreateinfinitepushlogger(op)
    parthandlerstart = time.time()
    log(scratchbranchparttype, eventtype=b'start')
    index = op.repo.bundlestore.index
    store = op.repo.bundlestore.store
    op.records.add(scratchbranchparttype + b'_skippushkey', True)

    bundle = None
    try:  # guards bundle
        bundlepath = b"bundle:%s+%s" % (op.repo.root, bundlefile)
        bundle = hg.repository(op.repo.ui, bundlepath)

        bookmark = params.get(b'bookmark')
        bookprevnode = params.get(b'bookprevnode', b'')
        force = params.get(b'force')

        if bookmark:
            oldnode = index.getnode(bookmark)
        else:
            oldnode = None
        bundleheads = bundle.revs(b'heads(bundle())')
        if bookmark and len(bundleheads) > 1:
            raise error.Abort(
                _(b'cannot push more than one head to a scratch branch')
            )

        revs = _getrevs(bundle, oldnode, force, bookmark)

        # Notify the user of what is being pushed
        plural = b's' if len(revs) > 1 else b''
        op.repo.ui.warn(_(b"pushing %d commit%s:\n") % (len(revs), plural))
        maxoutput = 10
        for i in range(0, min(len(revs), maxoutput)):
            firstline = bundle[revs[i]].description().split(b'\n')[0][:50]
            op.repo.ui.warn(b"    %s  %s\n" % (revs[i], firstline))

        if len(revs) > maxoutput + 1:
            op.repo.ui.warn(b"    ...\n")
            firstline = bundle[revs[-1]].description().split(b'\n')[0][:50]
            op.repo.ui.warn(b"    %s  %s\n" % (revs[-1], firstline))

        nodesctx = [bundle[rev] for rev in revs]
        inindex = lambda rev: bool(index.getbundle(bundle[rev].hex()))
        if bundleheads:
            newheadscount = sum(not inindex(rev) for rev in bundleheads)
        else:
            newheadscount = 0
        # If there's a bookmark specified, there should be only one head,
        # so we choose the last node, which will be that head.
        # If a bug or malicious client allows there to be a bookmark
        # with multiple heads, we will place the bookmark on the last head.
        bookmarknode = nodesctx[-1].hex() if nodesctx else None
        key = None
        if newheadscount:
            with open(bundlefile, b'rb') as f:
                bundledata = f.read()
                with logservicecall(
                    log, b'bundlestore', bundlesize=len(bundledata)
                ):
                    bundlesizelimit = 100 * 1024 * 1024  # 100 MB
                    if len(bundledata) > bundlesizelimit:
                        error_msg = (
                            b'bundle is too big: %d bytes. '
                            + b'max allowed size is 100 MB'
                        )
                        raise error.Abort(error_msg % (len(bundledata),))
                    key = store.write(bundledata)

        with logservicecall(log, b'index', newheadscount=newheadscount), index:
            if key:
                index.addbundle(key, nodesctx)
            if bookmark:
                index.addbookmark(bookmark, bookmarknode)
                _maybeaddpushbackpart(
                    op, bookmark, bookmarknode, bookprevnode, params
                )
        log(
            scratchbranchparttype,
            eventtype=b'success',
            elapsedms=(time.time() - parthandlerstart) * 1000,
        )

    except Exception as e:
        log(
            scratchbranchparttype,
            eventtype=b'failure',
            elapsedms=(time.time() - parthandlerstart) * 1000,
            errormsg=stringutil.forcebytestr(e),
        )
        raise
    finally:
        if bundle:
            bundle.close()


@bundle2.parthandler(
    scratchbranchparttype,
    (
        b'bookmark',
        b'bookprevnode',
        b'force',
        b'pushbackbookmarks',
        b'cgversion',
    ),
)
def bundle2scratchbranch(op, part):
    '''unbundle a bundle2 part containing a changegroup to store'''

    bundler = bundle2.bundle20(op.repo.ui)
    cgversion = part.params.get(b'cgversion', b'01')
    cgpart = bundle2.bundlepart(b'changegroup', data=part.read())
    cgpart.addparam(b'version', cgversion)
    bundler.addpart(cgpart)
    buf = util.chunkbuffer(bundler.getchunks())

    fd, bundlefile = pycompat.mkstemp()
    try:
        try:
            fp = os.fdopen(fd, 'wb')
            fp.write(buf.read())
        finally:
            fp.close()
        storebundle(op, part.params, bundlefile)
    finally:
        try:
            os.unlink(bundlefile)
        except OSError as e:
            if e.errno != errno.ENOENT:
                raise

    return 1


def _maybeaddpushbackpart(op, bookmark, newnode, oldnode, params):
    if params.get(b'pushbackbookmarks'):
        if op.reply and b'pushback' in op.reply.capabilities:
            params = {
                b'namespace': b'bookmarks',
                b'key': bookmark,
                b'new': newnode,
                b'old': oldnode,
            }
            op.reply.newpart(
                b'pushkey', mandatoryparams=pycompat.iteritems(params)
            )


def bundle2pushkey(orig, op, part):
    """Wrapper of bundle2.handlepushkey()

    The only goal is to skip calling the original function if flag is set.
    It's set if infinitepush push is happening.
    """
    if op.records[scratchbranchparttype + b'_skippushkey']:
        if op.reply is not None:
            rpart = op.reply.newpart(b'reply:pushkey')
            rpart.addparam(b'in-reply-to', str(part.id), mandatory=False)
            rpart.addparam(b'return', b'1', mandatory=False)
        return 1

    return orig(op, part)


def bundle2handlephases(orig, op, part):
    """Wrapper of bundle2.handlephases()

    The only goal is to skip calling the original function if flag is set.
    It's set if infinitepush push is happening.
    """

    if op.records[scratchbranchparttype + b'_skipphaseheads']:
        return

    return orig(op, part)


def _asyncsavemetadata(root, nodes):
    """starts a separate process that fills metadata for the nodes

    This function creates a separate process and doesn't wait for it's
    completion. This was done to avoid slowing down pushes
    """

    maxnodes = 50
    if len(nodes) > maxnodes:
        return
    nodesargs = []
    for node in nodes:
        nodesargs.append(b'--node')
        nodesargs.append(node)
    with open(os.devnull, b'w+b') as devnull:
        cmdline = [
            util.hgexecutable(),
            b'debugfillinfinitepushmetadata',
            b'-R',
            root,
        ] + nodesargs
        # Process will run in background. We don't care about the return code
        subprocess.Popen(
            pycompat.rapply(procutil.tonativestr, cmdline),
            close_fds=True,
            shell=False,
            stdin=devnull,
            stdout=devnull,
            stderr=devnull,
        )
