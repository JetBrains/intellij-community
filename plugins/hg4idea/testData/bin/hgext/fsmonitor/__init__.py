# __init__.py - fsmonitor initialization and overrides
#
# Copyright 2013-2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''Faster status operations with the Watchman file monitor (EXPERIMENTAL)

Integrates the file-watching program Watchman with Mercurial to produce faster
status results.

On a particular Linux system, for a real-world repository with over 400,000
files hosted on ext4, vanilla `hg status` takes 1.3 seconds. On the same
system, with fsmonitor it takes about 0.3 seconds.

fsmonitor requires no configuration -- it will tell Watchman about your
repository as necessary. You'll need to install Watchman from
https://facebook.github.io/watchman/ and make sure it is in your PATH.

fsmonitor is incompatible with the largefiles and eol extensions, and
will disable itself if any of those are active.

The following configuration options exist:

::

    [fsmonitor]
    mode = {off, on, paranoid}

When `mode = off`, fsmonitor will disable itself (similar to not loading the
extension at all). When `mode = on`, fsmonitor will be enabled (the default).
When `mode = paranoid`, fsmonitor will query both Watchman and the filesystem,
and ensure that the results are consistent.

::

    [fsmonitor]
    timeout = (float)

A value, in seconds, that determines how long fsmonitor will wait for Watchman
to return results. Defaults to `2.0`.

::

    [fsmonitor]
    blacklistusers = (list of userids)

A list of usernames for which fsmonitor will disable itself altogether.

::

    [fsmonitor]
    walk_on_invalidate = (boolean)

Whether or not to walk the whole repo ourselves when our cached state has been
invalidated, for example when Watchman has been restarted or .hgignore rules
have been changed. Walking the repo in that case can result in competing for
I/O with Watchman. For large repos it is recommended to set this value to
false. You may wish to set this to true if you have a very fast filesystem
that can outpace the IPC overhead of getting the result data for the full repo
from Watchman. Defaults to false.

::

    [fsmonitor]
    warn_when_unused = (boolean)

Whether to print a warning during certain operations when fsmonitor would be
beneficial to performance but isn't enabled.

::

    [fsmonitor]
    warn_update_file_count = (integer)
    # or when mercurial is built with rust support
    warn_update_file_count_rust = (integer)

If ``warn_when_unused`` is set and fsmonitor isn't enabled, a warning will
be printed during working directory updates if this many files will be
created.
'''

# Platforms Supported
# ===================
#
# **Linux:** *Stable*. Watchman and fsmonitor are both known to work reliably,
#   even under severe loads.
#
# **Mac OS X:** *Stable*. The Mercurial test suite passes with fsmonitor
#   turned on, on case-insensitive HFS+. There has been a reasonable amount of
#   user testing under normal loads.
#
# **Solaris, BSD:** *Alpha*. watchman and fsmonitor are believed to work, but
#   very little testing has been done.
#
# **Windows:** *Alpha*. Not in a release version of watchman or fsmonitor yet.
#
# Known Issues
# ============
#
# * fsmonitor will disable itself if any of the following extensions are
#   enabled: largefiles, inotify, eol; or if the repository has subrepos.
# * fsmonitor will produce incorrect results if nested repos that are not
#   subrepos exist. *Workaround*: add nested repo paths to your `.hgignore`.
#
# The issues related to nested repos and subrepos are probably not fundamental
# ones. Patches to fix them are welcome.

from __future__ import absolute_import

import codecs
import os
import stat
import sys
import tempfile
import weakref

from mercurial.i18n import _
from mercurial.node import hex
from mercurial.pycompat import open
from mercurial import (
    context,
    encoding,
    error,
    extensions,
    localrepo,
    merge,
    pathutil,
    pycompat,
    registrar,
    scmutil,
    util,
)
from mercurial import match as matchmod
from mercurial.utils import (
    hashutil,
    stringutil,
)

from . import (
    pywatchman,
    state,
    watchmanclient,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'fsmonitor',
    b'mode',
    default=b'on',
)
configitem(
    b'fsmonitor',
    b'walk_on_invalidate',
    default=False,
)
configitem(
    b'fsmonitor',
    b'timeout',
    default=b'2',
)
configitem(
    b'fsmonitor',
    b'blacklistusers',
    default=list,
)
configitem(
    b'fsmonitor',
    b'watchman_exe',
    default=b'watchman',
)
configitem(
    b'fsmonitor',
    b'verbose',
    default=True,
    experimental=True,
)
configitem(
    b'experimental',
    b'fsmonitor.transaction_notify',
    default=False,
)

# This extension is incompatible with the following blacklisted extensions
# and will disable itself when encountering one of these:
_blacklist = [b'largefiles', b'eol']


def debuginstall(ui, fm):
    fm.write(
        b"fsmonitor-watchman",
        _(b"fsmonitor checking for watchman binary... (%s)\n"),
        ui.configpath(b"fsmonitor", b"watchman_exe"),
    )
    root = tempfile.mkdtemp()
    c = watchmanclient.client(ui, root)
    err = None
    try:
        v = c.command(b"version")
        fm.write(
            b"fsmonitor-watchman-version",
            _(b" watchman binary version %s\n"),
            pycompat.bytestr(v["version"]),
        )
    except watchmanclient.Unavailable as e:
        err = stringutil.forcebytestr(e)
    fm.condwrite(
        err,
        b"fsmonitor-watchman-error",
        _(b" watchman binary missing or broken: %s\n"),
        err,
    )
    return 1 if err else 0


def _handleunavailable(ui, state, ex):
    """Exception handler for Watchman interaction exceptions"""
    if isinstance(ex, watchmanclient.Unavailable):
        # experimental config: fsmonitor.verbose
        if ex.warn and ui.configbool(b'fsmonitor', b'verbose'):
            if b'illegal_fstypes' not in stringutil.forcebytestr(ex):
                ui.warn(stringutil.forcebytestr(ex) + b'\n')
        if ex.invalidate:
            state.invalidate()
        # experimental config: fsmonitor.verbose
        if ui.configbool(b'fsmonitor', b'verbose'):
            ui.log(
                b'fsmonitor',
                b'Watchman unavailable: %s\n',
                stringutil.forcebytestr(ex.msg),
            )
    else:
        ui.log(
            b'fsmonitor',
            b'Watchman exception: %s\n',
            stringutil.forcebytestr(ex),
        )


def _hashignore(ignore):
    """Calculate hash for ignore patterns and filenames

    If this information changes between Mercurial invocations, we can't
    rely on Watchman information anymore and have to re-scan the working
    copy.

    """
    sha1 = hashutil.sha1()
    sha1.update(pycompat.byterepr(ignore))
    return pycompat.sysbytes(sha1.hexdigest())


_watchmanencoding = pywatchman.encoding.get_local_encoding()
_fsencoding = sys.getfilesystemencoding() or sys.getdefaultencoding()
_fixencoding = codecs.lookup(_watchmanencoding) != codecs.lookup(_fsencoding)


def _watchmantofsencoding(path):
    """Fix path to match watchman and local filesystem encoding

    watchman's paths encoding can differ from filesystem encoding. For example,
    on Windows, it's always utf-8.
    """
    try:
        decoded = path.decode(_watchmanencoding)
    except UnicodeDecodeError as e:
        raise error.Abort(
            stringutil.forcebytestr(e), hint=b'watchman encoding error'
        )

    try:
        encoded = decoded.encode(_fsencoding, 'strict')
    except UnicodeEncodeError as e:
        raise error.Abort(stringutil.forcebytestr(e))

    return encoded


def overridewalk(orig, self, match, subrepos, unknown, ignored, full=True):
    """Replacement for dirstate.walk, hooking into Watchman.

    Whenever full is False, ignored is False, and the Watchman client is
    available, use Watchman combined with saved state to possibly return only a
    subset of files."""

    def bail(reason):
        self._ui.debug(b'fsmonitor: fallback to core status, %s\n' % reason)
        return orig(match, subrepos, unknown, ignored, full=True)

    if full:
        return bail(b'full rewalk requested')
    if ignored:
        return bail(b'listing ignored files')
    if not self._watchmanclient.available():
        return bail(b'client unavailable')
    state = self._fsmonitorstate
    clock, ignorehash, notefiles = state.get()
    if not clock:
        if state.walk_on_invalidate:
            return bail(b'no clock')
        # Initial NULL clock value, see
        # https://facebook.github.io/watchman/docs/clockspec.html
        clock = b'c:0:0'
        notefiles = []

    ignore = self._ignore
    dirignore = self._dirignore
    if unknown:
        if _hashignore(ignore) != ignorehash and clock != b'c:0:0':
            # ignore list changed -- can't rely on Watchman state any more
            if state.walk_on_invalidate:
                return bail(b'ignore rules changed')
            notefiles = []
            clock = b'c:0:0'
    else:
        # always ignore
        ignore = util.always
        dirignore = util.always

    matchfn = match.matchfn
    matchalways = match.always()
    dmap = self._map
    if util.safehasattr(dmap, b'_map'):
        # for better performance, directly access the inner dirstate map if the
        # standard dirstate implementation is in use.
        dmap = dmap._map
    nonnormalset = self._map.nonnormalset

    copymap = self._map.copymap
    getkind = stat.S_IFMT
    dirkind = stat.S_IFDIR
    regkind = stat.S_IFREG
    lnkkind = stat.S_IFLNK
    join = self._join
    normcase = util.normcase
    fresh_instance = False

    exact = skipstep3 = False
    if match.isexact():  # match.exact
        exact = True
        dirignore = util.always  # skip step 2
    elif match.prefix():  # match.match, no patterns
        skipstep3 = True

    if not exact and self._checkcase:
        # note that even though we could receive directory entries, we're only
        # interested in checking if a file with the same name exists. So only
        # normalize files if possible.
        normalize = self._normalizefile
        skipstep3 = False
    else:
        normalize = None

    # step 1: find all explicit files
    results, work, dirsnotfound = self._walkexplicit(match, subrepos)

    skipstep3 = skipstep3 and not (work or dirsnotfound)
    work = [d for d in work if not dirignore(d[0])]

    if not work and (exact or skipstep3):
        for s in subrepos:
            del results[s]
        del results[b'.hg']
        return results

    # step 2: query Watchman
    try:
        # Use the user-configured timeout for the query.
        # Add a little slack over the top of the user query to allow for
        # overheads while transferring the data
        self._watchmanclient.settimeout(state.timeout + 0.1)
        result = self._watchmanclient.command(
            b'query',
            {
                b'fields': [b'mode', b'mtime', b'size', b'exists', b'name'],
                b'since': clock,
                b'expression': [
                    b'not',
                    [
                        b'anyof',
                        [b'dirname', b'.hg'],
                        [b'name', b'.hg', b'wholename'],
                    ],
                ],
                b'sync_timeout': int(state.timeout * 1000),
                b'empty_on_fresh_instance': state.walk_on_invalidate,
            },
        )
    except Exception as ex:
        _handleunavailable(self._ui, state, ex)
        self._watchmanclient.clearconnection()
        return bail(b'exception during run')
    else:
        # We need to propagate the last observed clock up so that we
        # can use it for our next query
        state.setlastclock(pycompat.sysbytes(result[b'clock']))
        if result[b'is_fresh_instance']:
            if state.walk_on_invalidate:
                state.invalidate()
                return bail(b'fresh instance')
            fresh_instance = True
            # Ignore any prior noteable files from the state info
            notefiles = []

    # for file paths which require normalization and we encounter a case
    # collision, we store our own foldmap
    if normalize:
        foldmap = {normcase(k): k for k in results}

    switch_slashes = pycompat.ossep == b'\\'
    # The order of the results is, strictly speaking, undefined.
    # For case changes on a case insensitive filesystem we may receive
    # two entries, one with exists=True and another with exists=False.
    # The exists=True entries in the same response should be interpreted
    # as being happens-after the exists=False entries due to the way that
    # Watchman tracks files.  We use this property to reconcile deletes
    # for name case changes.
    for entry in result[b'files']:
        fname = entry[b'name']

        # Watchman always give us a str. Normalize to bytes on Python 3
        # using Watchman's encoding, if needed.
        if not isinstance(fname, bytes):
            fname = fname.encode(_watchmanencoding)

        if _fixencoding:
            fname = _watchmantofsencoding(fname)

        if switch_slashes:
            fname = fname.replace(b'\\', b'/')
        if normalize:
            normed = normcase(fname)
            fname = normalize(fname, True, True)
            foldmap[normed] = fname
        fmode = entry[b'mode']
        fexists = entry[b'exists']
        kind = getkind(fmode)

        if b'/.hg/' in fname or fname.endswith(b'/.hg'):
            return bail(b'nested-repo-detected')

        if not fexists:
            # if marked as deleted and we don't already have a change
            # record, mark it as deleted.  If we already have an entry
            # for fname then it was either part of walkexplicit or was
            # an earlier result that was a case change
            if (
                fname not in results
                and fname in dmap
                and (matchalways or matchfn(fname))
            ):
                results[fname] = None
        elif kind == dirkind:
            if fname in dmap and (matchalways or matchfn(fname)):
                results[fname] = None
        elif kind == regkind or kind == lnkkind:
            if fname in dmap:
                if matchalways or matchfn(fname):
                    results[fname] = entry
            elif (matchalways or matchfn(fname)) and not ignore(fname):
                results[fname] = entry
        elif fname in dmap and (matchalways or matchfn(fname)):
            results[fname] = None

    # step 3: query notable files we don't already know about
    # XXX try not to iterate over the entire dmap
    if normalize:
        # any notable files that have changed case will already be handled
        # above, so just check membership in the foldmap
        notefiles = {
            normalize(f, True, True)
            for f in notefiles
            if normcase(f) not in foldmap
        }
    visit = {
        f
        for f in notefiles
        if (f not in results and matchfn(f) and (f in dmap or not ignore(f)))
    }

    if not fresh_instance:
        if matchalways:
            visit.update(f for f in nonnormalset if f not in results)
            visit.update(f for f in copymap if f not in results)
        else:
            visit.update(
                f for f in nonnormalset if f not in results and matchfn(f)
            )
            visit.update(f for f in copymap if f not in results and matchfn(f))
    else:
        if matchalways:
            visit.update(
                f for f, st in pycompat.iteritems(dmap) if f not in results
            )
            visit.update(f for f in copymap if f not in results)
        else:
            visit.update(
                f
                for f, st in pycompat.iteritems(dmap)
                if f not in results and matchfn(f)
            )
            visit.update(f for f in copymap if f not in results and matchfn(f))

    audit = pathutil.pathauditor(self._root, cached=True).check
    auditpass = [f for f in visit if audit(f)]
    auditpass.sort()
    auditfail = visit.difference(auditpass)
    for f in auditfail:
        results[f] = None

    nf = iter(auditpass)
    for st in util.statfiles([join(f) for f in auditpass]):
        f = next(nf)
        if st or f in dmap:
            results[f] = st

    for s in subrepos:
        del results[s]
    del results[b'.hg']
    return results


def overridestatus(
    orig,
    self,
    node1=b'.',
    node2=None,
    match=None,
    ignored=False,
    clean=False,
    unknown=False,
    listsubrepos=False,
):
    listignored = ignored
    listclean = clean
    listunknown = unknown

    def _cmpsets(l1, l2):
        try:
            if b'FSMONITOR_LOG_FILE' in encoding.environ:
                fn = encoding.environ[b'FSMONITOR_LOG_FILE']
                f = open(fn, b'wb')
            else:
                fn = b'fsmonitorfail.log'
                f = self.vfs.open(fn, b'wb')
        except (IOError, OSError):
            self.ui.warn(_(b'warning: unable to write to %s\n') % fn)
            return

        try:
            for i, (s1, s2) in enumerate(zip(l1, l2)):
                if set(s1) != set(s2):
                    f.write(b'sets at position %d are unequal\n' % i)
                    f.write(b'watchman returned: %s\n' % s1)
                    f.write(b'stat returned: %s\n' % s2)
        finally:
            f.close()

    if isinstance(node1, context.changectx):
        ctx1 = node1
    else:
        ctx1 = self[node1]
    if isinstance(node2, context.changectx):
        ctx2 = node2
    else:
        ctx2 = self[node2]

    working = ctx2.rev() is None
    parentworking = working and ctx1 == self[b'.']
    match = match or matchmod.always()

    # Maybe we can use this opportunity to update Watchman's state.
    # Mercurial uses workingcommitctx and/or memctx to represent the part of
    # the workingctx that is to be committed. So don't update the state in
    # that case.
    # HG_PENDING is set in the environment when the dirstate is being updated
    # in the middle of a transaction; we must not update our state in that
    # case, or we risk forgetting about changes in the working copy.
    updatestate = (
        parentworking
        and match.always()
        and not isinstance(ctx2, (context.workingcommitctx, context.memctx))
        and b'HG_PENDING' not in encoding.environ
    )

    try:
        if self._fsmonitorstate.walk_on_invalidate:
            # Use a short timeout to query the current clock.  If that
            # takes too long then we assume that the service will be slow
            # to answer our query.
            # walk_on_invalidate indicates that we prefer to walk the
            # tree ourselves because we can ignore portions that Watchman
            # cannot and we tend to be faster in the warmer buffer cache
            # cases.
            self._watchmanclient.settimeout(0.1)
        else:
            # Give Watchman more time to potentially complete its walk
            # and return the initial clock.  In this mode we assume that
            # the filesystem will be slower than parsing a potentially
            # very large Watchman result set.
            self._watchmanclient.settimeout(self._fsmonitorstate.timeout + 0.1)
        startclock = self._watchmanclient.getcurrentclock()
    except Exception as ex:
        self._watchmanclient.clearconnection()
        _handleunavailable(self.ui, self._fsmonitorstate, ex)
        # boo, Watchman failed. bail
        return orig(
            node1,
            node2,
            match,
            listignored,
            listclean,
            listunknown,
            listsubrepos,
        )

    if updatestate:
        # We need info about unknown files. This may make things slower the
        # first time, but whatever.
        stateunknown = True
    else:
        stateunknown = listunknown

    if updatestate:
        ps = poststatus(startclock)
        self.addpostdsstatus(ps)

    r = orig(
        node1, node2, match, listignored, listclean, stateunknown, listsubrepos
    )
    modified, added, removed, deleted, unknown, ignored, clean = r

    if not listunknown:
        unknown = []

    # don't do paranoid checks if we're not going to query Watchman anyway
    full = listclean or match.traversedir is not None
    if self._fsmonitorstate.mode == b'paranoid' and not full:
        # run status again and fall back to the old walk this time
        self.dirstate._fsmonitordisable = True

        # shut the UI up
        quiet = self.ui.quiet
        self.ui.quiet = True
        fout, ferr = self.ui.fout, self.ui.ferr
        self.ui.fout = self.ui.ferr = open(os.devnull, b'wb')

        try:
            rv2 = orig(
                node1,
                node2,
                match,
                listignored,
                listclean,
                listunknown,
                listsubrepos,
            )
        finally:
            self.dirstate._fsmonitordisable = False
            self.ui.quiet = quiet
            self.ui.fout, self.ui.ferr = fout, ferr

        # clean isn't tested since it's set to True above
        with self.wlock():
            _cmpsets(
                [modified, added, removed, deleted, unknown, ignored, clean],
                rv2,
            )
        modified, added, removed, deleted, unknown, ignored, clean = rv2

    return scmutil.status(
        modified, added, removed, deleted, unknown, ignored, clean
    )


class poststatus(object):
    def __init__(self, startclock):
        self._startclock = pycompat.sysbytes(startclock)

    def __call__(self, wctx, status):
        clock = wctx.repo()._fsmonitorstate.getlastclock() or self._startclock
        hashignore = _hashignore(wctx.repo().dirstate._ignore)
        notefiles = (
            status.modified
            + status.added
            + status.removed
            + status.deleted
            + status.unknown
        )
        wctx.repo()._fsmonitorstate.set(clock, hashignore, notefiles)


def makedirstate(repo, dirstate):
    class fsmonitordirstate(dirstate.__class__):
        def _fsmonitorinit(self, repo):
            # _fsmonitordisable is used in paranoid mode
            self._fsmonitordisable = False
            self._fsmonitorstate = repo._fsmonitorstate
            self._watchmanclient = repo._watchmanclient
            self._repo = weakref.proxy(repo)

        def walk(self, *args, **kwargs):
            orig = super(fsmonitordirstate, self).walk
            if self._fsmonitordisable:
                return orig(*args, **kwargs)
            return overridewalk(orig, self, *args, **kwargs)

        def rebuild(self, *args, **kwargs):
            self._fsmonitorstate.invalidate()
            return super(fsmonitordirstate, self).rebuild(*args, **kwargs)

        def invalidate(self, *args, **kwargs):
            self._fsmonitorstate.invalidate()
            return super(fsmonitordirstate, self).invalidate(*args, **kwargs)

    dirstate.__class__ = fsmonitordirstate
    dirstate._fsmonitorinit(repo)


def wrapdirstate(orig, self):
    ds = orig(self)
    # only override the dirstate when Watchman is available for the repo
    if util.safehasattr(self, b'_fsmonitorstate'):
        makedirstate(self, ds)
    return ds


def extsetup(ui):
    extensions.wrapfilecache(
        localrepo.localrepository, b'dirstate', wrapdirstate
    )
    if pycompat.isdarwin:
        # An assist for avoiding the dangling-symlink fsevents bug
        extensions.wrapfunction(os, b'symlink', wrapsymlink)

    extensions.wrapfunction(merge, b'_update', wrapupdate)


def wrapsymlink(orig, source, link_name):
    """if we create a dangling symlink, also touch the parent dir
    to encourage fsevents notifications to work more correctly"""
    try:
        return orig(source, link_name)
    finally:
        try:
            os.utime(os.path.dirname(link_name), None)
        except OSError:
            pass


class state_update(object):
    """This context manager is responsible for dispatching the state-enter
    and state-leave signals to the watchman service. The enter and leave
    methods can be invoked manually (for scenarios where context manager
    semantics are not possible). If parameters oldnode and newnode are None,
    they will be populated based on current working copy in enter and
    leave, respectively. Similarly, if the distance is none, it will be
    calculated based on the oldnode and newnode in the leave method."""

    def __init__(
        self,
        repo,
        name,
        oldnode=None,
        newnode=None,
        distance=None,
        partial=False,
    ):
        self.repo = repo.unfiltered()
        self.name = name
        self.oldnode = oldnode
        self.newnode = newnode
        self.distance = distance
        self.partial = partial
        self._lock = None
        self.need_leave = False

    def __enter__(self):
        self.enter()

    def enter(self):
        # Make sure we have a wlock prior to sending notifications to watchman.
        # We don't want to race with other actors. In the update case,
        # merge.update is going to take the wlock almost immediately. We are
        # effectively extending the lock around several short sanity checks.
        if self.oldnode is None:
            self.oldnode = self.repo[b'.'].node()

        if self.repo.currentwlock() is None:
            if util.safehasattr(self.repo, b'wlocknostateupdate'):
                self._lock = self.repo.wlocknostateupdate()
            else:
                self._lock = self.repo.wlock()
        self.need_leave = self._state(b'state-enter', hex(self.oldnode))
        return self

    def __exit__(self, type_, value, tb):
        abort = True if type_ else False
        self.exit(abort=abort)

    def exit(self, abort=False):
        try:
            if self.need_leave:
                status = b'failed' if abort else b'ok'
                if self.newnode is None:
                    self.newnode = self.repo[b'.'].node()
                if self.distance is None:
                    self.distance = calcdistance(
                        self.repo, self.oldnode, self.newnode
                    )
                self._state(b'state-leave', hex(self.newnode), status=status)
        finally:
            self.need_leave = False
            if self._lock:
                self._lock.release()

    def _state(self, cmd, commithash, status=b'ok'):
        if not util.safehasattr(self.repo, b'_watchmanclient'):
            return False
        try:
            self.repo._watchmanclient.command(
                cmd,
                {
                    b'name': self.name,
                    b'metadata': {
                        # the target revision
                        b'rev': commithash,
                        # approximate number of commits between current and target
                        b'distance': self.distance if self.distance else 0,
                        # success/failure (only really meaningful for state-leave)
                        b'status': status,
                        # whether the working copy parent is changing
                        b'partial': self.partial,
                    },
                },
            )
            return True
        except Exception as e:
            # Swallow any errors; fire and forget
            self.repo.ui.log(
                b'watchman', b'Exception %s while running %s\n', e, cmd
            )
            return False


# Estimate the distance between two nodes
def calcdistance(repo, oldnode, newnode):
    anc = repo.changelog.ancestor(oldnode, newnode)
    ancrev = repo[anc].rev()
    distance = abs(repo[oldnode].rev() - ancrev) + abs(
        repo[newnode].rev() - ancrev
    )
    return distance


# Bracket working copy updates with calls to the watchman state-enter
# and state-leave commands.  This allows clients to perform more intelligent
# settling during bulk file change scenarios
# https://facebook.github.io/watchman/docs/cmd/subscribe.html#advanced-settling
def wrapupdate(
    orig,
    repo,
    node,
    branchmerge,
    force,
    ancestor=None,
    mergeancestor=False,
    labels=None,
    matcher=None,
    **kwargs
):

    distance = 0
    partial = True
    oldnode = repo[b'.'].node()
    newnode = repo[node].node()
    if matcher is None or matcher.always():
        partial = False
        distance = calcdistance(repo.unfiltered(), oldnode, newnode)

    with state_update(
        repo,
        name=b"hg.update",
        oldnode=oldnode,
        newnode=newnode,
        distance=distance,
        partial=partial,
    ):
        return orig(
            repo,
            node,
            branchmerge,
            force,
            ancestor,
            mergeancestor,
            labels,
            matcher,
            **kwargs
        )


def repo_has_depth_one_nested_repo(repo):
    for f in repo.wvfs.listdir():
        if os.path.isdir(os.path.join(repo.root, f, b'.hg')):
            msg = b'fsmonitor: sub-repository %r detected, fsmonitor disabled\n'
            repo.ui.debug(msg % f)
            return True
    return False


def reposetup(ui, repo):
    # We don't work with largefiles or inotify
    exts = extensions.enabled()
    for ext in _blacklist:
        if ext in exts:
            ui.warn(
                _(
                    b'The fsmonitor extension is incompatible with the %s '
                    b'extension and has been disabled.\n'
                )
                % ext
            )
            return

    if repo.local():
        # We don't work with subrepos either.
        #
        # if repo[None].substate can cause a dirstate parse, which is too
        # slow. Instead, look for a file called hgsubstate,
        if repo.wvfs.exists(b'.hgsubstate') or repo.wvfs.exists(b'.hgsub'):
            return

        if repo_has_depth_one_nested_repo(repo):
            return

        fsmonitorstate = state.state(repo)
        if fsmonitorstate.mode == b'off':
            return

        try:
            client = watchmanclient.client(repo.ui, repo.root)
        except Exception as ex:
            _handleunavailable(ui, fsmonitorstate, ex)
            return

        repo._fsmonitorstate = fsmonitorstate
        repo._watchmanclient = client

        dirstate, cached = localrepo.isfilecached(repo, b'dirstate')
        if cached:
            # at this point since fsmonitorstate wasn't present,
            # repo.dirstate is not a fsmonitordirstate
            makedirstate(repo, dirstate)

        class fsmonitorrepo(repo.__class__):
            def status(self, *args, **kwargs):
                orig = super(fsmonitorrepo, self).status
                return overridestatus(orig, self, *args, **kwargs)

            def wlocknostateupdate(self, *args, **kwargs):
                return super(fsmonitorrepo, self).wlock(*args, **kwargs)

            def wlock(self, *args, **kwargs):
                l = super(fsmonitorrepo, self).wlock(*args, **kwargs)
                if not ui.configbool(
                    b"experimental", b"fsmonitor.transaction_notify"
                ):
                    return l
                if l.held != 1:
                    return l
                origrelease = l.releasefn

                def staterelease():
                    if origrelease:
                        origrelease()
                    if l.stateupdate:
                        l.stateupdate.exit()
                        l.stateupdate = None

                try:
                    l.stateupdate = None
                    l.stateupdate = state_update(self, name=b"hg.transaction")
                    l.stateupdate.enter()
                    l.releasefn = staterelease
                except Exception as e:
                    # Swallow any errors; fire and forget
                    self.ui.log(
                        b'watchman', b'Exception in state update %s\n', e
                    )
                return l

        repo.__class__ = fsmonitorrepo
