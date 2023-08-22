# scmutil.py - Mercurial core utility functions
#
#  Copyright Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno
import glob
import os
import posixpath
import re
import subprocess
import weakref

from .i18n import _
from .node import (
    bin,
    hex,
    nullrev,
    short,
    wdirrev,
)
from .pycompat import getattr
from .thirdparty import attr
from . import (
    copies as copiesmod,
    encoding,
    error,
    match as matchmod,
    obsolete,
    obsutil,
    pathutil,
    phases,
    policy,
    pycompat,
    requirements as requirementsmod,
    revsetlang,
    similar,
    smartset,
    url,
    util,
    vfs,
)

from .utils import (
    hashutil,
    procutil,
    stringutil,
)

if pycompat.iswindows:
    from . import scmwindows as scmplatform
else:
    from . import scmposix as scmplatform

parsers = policy.importmod('parsers')
rustrevlog = policy.importrust('revlog')

termsize = scmplatform.termsize


@attr.s(slots=True, repr=False)
class status(object):
    """Struct with a list of files per status.

    The 'deleted', 'unknown' and 'ignored' properties are only
    relevant to the working copy.
    """

    modified = attr.ib(default=attr.Factory(list))
    added = attr.ib(default=attr.Factory(list))
    removed = attr.ib(default=attr.Factory(list))
    deleted = attr.ib(default=attr.Factory(list))
    unknown = attr.ib(default=attr.Factory(list))
    ignored = attr.ib(default=attr.Factory(list))
    clean = attr.ib(default=attr.Factory(list))

    def __iter__(self):
        yield self.modified
        yield self.added
        yield self.removed
        yield self.deleted
        yield self.unknown
        yield self.ignored
        yield self.clean

    def __repr__(self):
        return (
            r'<status modified=%s, added=%s, removed=%s, deleted=%s, '
            r'unknown=%s, ignored=%s, clean=%s>'
        ) % tuple(pycompat.sysstr(stringutil.pprint(v)) for v in self)


def itersubrepos(ctx1, ctx2):
    """find subrepos in ctx1 or ctx2"""
    # Create a (subpath, ctx) mapping where we prefer subpaths from
    # ctx1. The subpaths from ctx2 are important when the .hgsub file
    # has been modified (in ctx2) but not yet committed (in ctx1).
    subpaths = dict.fromkeys(ctx2.substate, ctx2)
    subpaths.update(dict.fromkeys(ctx1.substate, ctx1))

    missing = set()

    for subpath in ctx2.substate:
        if subpath not in ctx1.substate:
            del subpaths[subpath]
            missing.add(subpath)

    for subpath, ctx in sorted(pycompat.iteritems(subpaths)):
        yield subpath, ctx.sub(subpath)

    # Yield an empty subrepo based on ctx1 for anything only in ctx2.  That way,
    # status and diff will have an accurate result when it does
    # 'sub.{status|diff}(rev2)'.  Otherwise, the ctx2 subrepo is compared
    # against itself.
    for subpath in missing:
        yield subpath, ctx2.nullsub(subpath, ctx1)


def nochangesfound(ui, repo, excluded=None):
    """Report no changes for push/pull, excluded is None or a list of
    nodes excluded from the push/pull.
    """
    secretlist = []
    if excluded:
        for n in excluded:
            ctx = repo[n]
            if ctx.phase() >= phases.secret and not ctx.extinct():
                secretlist.append(n)

    if secretlist:
        ui.status(
            _(b"no changes found (ignored %d secret changesets)\n")
            % len(secretlist)
        )
    else:
        ui.status(_(b"no changes found\n"))


def callcatch(ui, func):
    """call func() with global exception handling

    return func() if no exception happens. otherwise do some error handling
    and return an exit code accordingly. does not handle all exceptions.
    """
    coarse_exit_code = -1
    detailed_exit_code = -1
    try:
        try:
            return func()
        except:  # re-raises
            ui.traceback()
            raise
    # Global exception handling, alphabetically
    # Mercurial-specific first, followed by built-in and library exceptions
    except error.LockHeld as inst:
        detailed_exit_code = 20
        if inst.errno == errno.ETIMEDOUT:
            reason = _(b'timed out waiting for lock held by %r') % (
                pycompat.bytestr(inst.locker)
            )
        else:
            reason = _(b'lock held by %r') % inst.locker
        ui.error(
            _(b"abort: %s: %s\n")
            % (inst.desc or stringutil.forcebytestr(inst.filename), reason)
        )
        if not inst.locker:
            ui.error(_(b"(lock might be very busy)\n"))
    except error.LockUnavailable as inst:
        detailed_exit_code = 20
        ui.error(
            _(b"abort: could not lock %s: %s\n")
            % (
                inst.desc or stringutil.forcebytestr(inst.filename),
                encoding.strtolocal(inst.strerror),
            )
        )
    except error.RepoError as inst:
        ui.error(_(b"abort: %s\n") % inst)
        if inst.hint:
            ui.error(_(b"(%s)\n") % inst.hint)
    except error.ResponseError as inst:
        ui.error(_(b"abort: %s") % inst.args[0])
        msg = inst.args[1]
        if isinstance(msg, type(u'')):
            msg = pycompat.sysbytes(msg)
        if msg is None:
            ui.error(b"\n")
        elif not isinstance(msg, bytes):
            ui.error(b" %r\n" % (msg,))
        elif not msg:
            ui.error(_(b" empty string\n"))
        else:
            ui.error(b"\n%r\n" % pycompat.bytestr(stringutil.ellipsis(msg)))
    except error.CensoredNodeError as inst:
        ui.error(_(b"abort: file censored %s\n") % inst)
    except error.WdirUnsupported:
        ui.error(_(b"abort: working directory revision cannot be specified\n"))
    except error.Error as inst:
        if inst.detailed_exit_code is not None:
            detailed_exit_code = inst.detailed_exit_code
        if inst.coarse_exit_code is not None:
            coarse_exit_code = inst.coarse_exit_code
        ui.error(inst.format())
    except error.WorkerError as inst:
        # Don't print a message -- the worker already should have
        return inst.status_code
    except ImportError as inst:
        ui.error(_(b"abort: %s\n") % stringutil.forcebytestr(inst))
        m = stringutil.forcebytestr(inst).split()[-1]
        if m in b"mpatch bdiff".split():
            ui.error(_(b"(did you forget to compile extensions?)\n"))
        elif m in b"zlib".split():
            ui.error(_(b"(is your Python install correct?)\n"))
    except util.urlerr.httperror as inst:
        detailed_exit_code = 100
        ui.error(_(b"abort: %s\n") % stringutil.forcebytestr(inst))
    except util.urlerr.urlerror as inst:
        detailed_exit_code = 100
        try:  # usually it is in the form (errno, strerror)
            reason = inst.reason.args[1]
        except (AttributeError, IndexError):
            # it might be anything, for example a string
            reason = inst.reason
        if isinstance(reason, pycompat.unicode):
            # SSLError of Python 2.7.9 contains a unicode
            reason = encoding.unitolocal(reason)
        ui.error(_(b"abort: error: %s\n") % stringutil.forcebytestr(reason))
    except (IOError, OSError) as inst:
        if (
            util.safehasattr(inst, b"args")
            and inst.args
            and inst.args[0] == errno.EPIPE
        ):
            pass
        elif getattr(inst, "strerror", None):  # common IOError or OSError
            if getattr(inst, "filename", None) is not None:
                ui.error(
                    _(b"abort: %s: '%s'\n")
                    % (
                        encoding.strtolocal(inst.strerror),
                        stringutil.forcebytestr(inst.filename),
                    )
                )
            else:
                ui.error(_(b"abort: %s\n") % encoding.strtolocal(inst.strerror))
        else:  # suspicious IOError
            raise
    except MemoryError:
        ui.error(_(b"abort: out of memory\n"))
    except SystemExit as inst:
        # Commands shouldn't sys.exit directly, but give a return code.
        # Just in case catch this and and pass exit code to caller.
        detailed_exit_code = 254
        coarse_exit_code = inst.code

    if ui.configbool(b'ui', b'detailed-exit-code'):
        return detailed_exit_code
    else:
        return coarse_exit_code


def checknewlabel(repo, lbl, kind):
    # Do not use the "kind" parameter in ui output.
    # It makes strings difficult to translate.
    if lbl in [b'tip', b'.', b'null']:
        raise error.InputError(_(b"the name '%s' is reserved") % lbl)
    for c in (b':', b'\0', b'\n', b'\r'):
        if c in lbl:
            raise error.InputError(
                _(b"%r cannot be used in a name") % pycompat.bytestr(c)
            )
    try:
        int(lbl)
        raise error.InputError(_(b"cannot use an integer as a name"))
    except ValueError:
        pass
    if lbl.strip() != lbl:
        raise error.InputError(
            _(b"leading or trailing whitespace in name %r") % lbl
        )


def checkfilename(f):
    '''Check that the filename f is an acceptable filename for a tracked file'''
    if b'\r' in f or b'\n' in f:
        raise error.InputError(
            _(b"'\\n' and '\\r' disallowed in filenames: %r")
            % pycompat.bytestr(f)
        )


def checkportable(ui, f):
    '''Check if filename f is portable and warn or abort depending on config'''
    checkfilename(f)
    abort, warn = checkportabilityalert(ui)
    if abort or warn:
        msg = util.checkwinfilename(f)
        if msg:
            msg = b"%s: %s" % (msg, procutil.shellquote(f))
            if abort:
                raise error.InputError(msg)
            ui.warn(_(b"warning: %s\n") % msg)


def checkportabilityalert(ui):
    """check if the user's config requests nothing, a warning, or abort for
    non-portable filenames"""
    val = ui.config(b'ui', b'portablefilenames')
    lval = val.lower()
    bval = stringutil.parsebool(val)
    abort = pycompat.iswindows or lval == b'abort'
    warn = bval or lval == b'warn'
    if bval is None and not (warn or abort or lval == b'ignore'):
        raise error.ConfigError(
            _(b"ui.portablefilenames value is invalid ('%s')") % val
        )
    return abort, warn


class casecollisionauditor(object):
    def __init__(self, ui, abort, dirstate):
        self._ui = ui
        self._abort = abort
        allfiles = b'\0'.join(dirstate)
        self._loweredfiles = set(encoding.lower(allfiles).split(b'\0'))
        self._dirstate = dirstate
        # The purpose of _newfiles is so that we don't complain about
        # case collisions if someone were to call this object with the
        # same filename twice.
        self._newfiles = set()

    def __call__(self, f):
        if f in self._newfiles:
            return
        fl = encoding.lower(f)
        if fl in self._loweredfiles and f not in self._dirstate:
            msg = _(b'possible case-folding collision for %s') % f
            if self._abort:
                raise error.Abort(msg)
            self._ui.warn(_(b"warning: %s\n") % msg)
        self._loweredfiles.add(fl)
        self._newfiles.add(f)


def filteredhash(repo, maxrev):
    """build hash of filtered revisions in the current repoview.

    Multiple caches perform up-to-date validation by checking that the
    tiprev and tipnode stored in the cache file match the current repository.
    However, this is not sufficient for validating repoviews because the set
    of revisions in the view may change without the repository tiprev and
    tipnode changing.

    This function hashes all the revs filtered from the view and returns
    that SHA-1 digest.
    """
    cl = repo.changelog
    if not cl.filteredrevs:
        return None
    key = cl._filteredrevs_hashcache.get(maxrev)
    if not key:
        revs = sorted(r for r in cl.filteredrevs if r <= maxrev)
        if revs:
            s = hashutil.sha1()
            for rev in revs:
                s.update(b'%d;' % rev)
            key = s.digest()
            cl._filteredrevs_hashcache[maxrev] = key
    return key


def walkrepos(path, followsym=False, seen_dirs=None, recurse=False):
    """yield every hg repository under path, always recursively.
    The recurse flag will only control recursion into repo working dirs"""

    def errhandler(err):
        if err.filename == path:
            raise err

    samestat = getattr(os.path, 'samestat', None)
    if followsym and samestat is not None:

        def adddir(dirlst, dirname):
            dirstat = os.stat(dirname)
            match = any(samestat(dirstat, lstdirstat) for lstdirstat in dirlst)
            if not match:
                dirlst.append(dirstat)
            return not match

    else:
        followsym = False

    if (seen_dirs is None) and followsym:
        seen_dirs = []
        adddir(seen_dirs, path)
    for root, dirs, files in os.walk(path, topdown=True, onerror=errhandler):
        dirs.sort()
        if b'.hg' in dirs:
            yield root  # found a repository
            qroot = os.path.join(root, b'.hg', b'patches')
            if os.path.isdir(os.path.join(qroot, b'.hg')):
                yield qroot  # we have a patch queue repo here
            if recurse:
                # avoid recursing inside the .hg directory
                dirs.remove(b'.hg')
            else:
                dirs[:] = []  # don't descend further
        elif followsym:
            newdirs = []
            for d in dirs:
                fname = os.path.join(root, d)
                if adddir(seen_dirs, fname):
                    if os.path.islink(fname):
                        for hgname in walkrepos(fname, True, seen_dirs):
                            yield hgname
                    else:
                        newdirs.append(d)
            dirs[:] = newdirs


def binnode(ctx):
    """Return binary node id for a given basectx"""
    node = ctx.node()
    if node is None:
        return ctx.repo().nodeconstants.wdirid
    return node


def intrev(ctx):
    """Return integer for a given basectx that can be used in comparison or
    arithmetic operation"""
    rev = ctx.rev()
    if rev is None:
        return wdirrev
    return rev


def formatchangeid(ctx):
    """Format changectx as '{rev}:{node|formatnode}', which is the default
    template provided by logcmdutil.changesettemplater"""
    repo = ctx.repo()
    return formatrevnode(repo.ui, intrev(ctx), binnode(ctx))


def formatrevnode(ui, rev, node):
    """Format given revision and node depending on the current verbosity"""
    if ui.debugflag:
        hexfunc = hex
    else:
        hexfunc = short
    return b'%d:%s' % (rev, hexfunc(node))


def resolvehexnodeidprefix(repo, prefix):
    if prefix.startswith(b'x'):
        prefix = prefix[1:]
    try:
        # Uses unfiltered repo because it's faster when prefix is ambiguous/
        # This matches the shortesthexnodeidprefix() function below.
        node = repo.unfiltered().changelog._partialmatch(prefix)
    except error.AmbiguousPrefixLookupError:
        revset = repo.ui.config(
            b'experimental', b'revisions.disambiguatewithin'
        )
        if revset:
            # Clear config to avoid infinite recursion
            configoverrides = {
                (b'experimental', b'revisions.disambiguatewithin'): None
            }
            with repo.ui.configoverride(configoverrides):
                revs = repo.anyrevs([revset], user=True)
                matches = []
                for rev in revs:
                    node = repo.changelog.node(rev)
                    if hex(node).startswith(prefix):
                        matches.append(node)
                if len(matches) == 1:
                    return matches[0]
        raise
    if node is None:
        return
    repo.changelog.rev(node)  # make sure node isn't filtered
    return node


def mayberevnum(repo, prefix):
    """Checks if the given prefix may be mistaken for a revision number"""
    try:
        i = int(prefix)
        # if we are a pure int, then starting with zero will not be
        # confused as a rev; or, obviously, if the int is larger
        # than the value of the tip rev. We still need to disambiguate if
        # prefix == '0', since that *is* a valid revnum.
        if (prefix != b'0' and prefix[0:1] == b'0') or i >= len(repo):
            return False
        return True
    except ValueError:
        return False


def shortesthexnodeidprefix(repo, node, minlength=1, cache=None):
    """Find the shortest unambiguous prefix that matches hexnode.

    If "cache" is not None, it must be a dictionary that can be used for
    caching between calls to this method.
    """
    # _partialmatch() of filtered changelog could take O(len(repo)) time,
    # which would be unacceptably slow. so we look for hash collision in
    # unfiltered space, which means some hashes may be slightly longer.

    minlength = max(minlength, 1)

    def disambiguate(prefix):
        """Disambiguate against revnums."""
        if repo.ui.configbool(b'experimental', b'revisions.prefixhexnode'):
            if mayberevnum(repo, prefix):
                return b'x' + prefix
            else:
                return prefix

        hexnode = hex(node)
        for length in range(len(prefix), len(hexnode) + 1):
            prefix = hexnode[:length]
            if not mayberevnum(repo, prefix):
                return prefix

    cl = repo.unfiltered().changelog
    revset = repo.ui.config(b'experimental', b'revisions.disambiguatewithin')
    if revset:
        revs = None
        if cache is not None:
            revs = cache.get(b'disambiguationrevset')
        if revs is None:
            revs = repo.anyrevs([revset], user=True)
            if cache is not None:
                cache[b'disambiguationrevset'] = revs
        if cl.rev(node) in revs:
            hexnode = hex(node)
            nodetree = None
            if cache is not None:
                nodetree = cache.get(b'disambiguationnodetree')
            if not nodetree:
                if util.safehasattr(parsers, 'nodetree'):
                    # The CExt is the only implementation to provide a nodetree
                    # class so far.
                    index = cl.index
                    if util.safehasattr(index, 'get_cindex'):
                        # the rust wrapped need to give access to its internal index
                        index = index.get_cindex()
                    nodetree = parsers.nodetree(index, len(revs))
                    for r in revs:
                        nodetree.insert(r)
                    if cache is not None:
                        cache[b'disambiguationnodetree'] = nodetree
            if nodetree is not None:
                length = max(nodetree.shortest(node), minlength)
                prefix = hexnode[:length]
                return disambiguate(prefix)
            for length in range(minlength, len(hexnode) + 1):
                matches = []
                prefix = hexnode[:length]
                for rev in revs:
                    otherhexnode = repo[rev].hex()
                    if prefix == otherhexnode[:length]:
                        matches.append(otherhexnode)
                if len(matches) == 1:
                    return disambiguate(prefix)

    try:
        return disambiguate(cl.shortest(node, minlength))
    except error.LookupError:
        raise error.RepoLookupError()


def isrevsymbol(repo, symbol):
    """Checks if a symbol exists in the repo.

    See revsymbol() for details. Raises error.AmbiguousPrefixLookupError if the
    symbol is an ambiguous nodeid prefix.
    """
    try:
        revsymbol(repo, symbol)
        return True
    except error.RepoLookupError:
        return False


def revsymbol(repo, symbol):
    """Returns a context given a single revision symbol (as string).

    This is similar to revsingle(), but accepts only a single revision symbol,
    i.e. things like ".", "tip", "1234", "deadbeef", "my-bookmark" work, but
    not "max(public())".
    """
    if not isinstance(symbol, bytes):
        msg = (
            b"symbol (%s of type %s) was not a string, did you mean "
            b"repo[symbol]?" % (symbol, type(symbol))
        )
        raise error.ProgrammingError(msg)
    try:
        if symbol in (b'.', b'tip', b'null'):
            return repo[symbol]

        try:
            r = int(symbol)
            if b'%d' % r != symbol:
                raise ValueError
            l = len(repo.changelog)
            if r < 0:
                r += l
            if r < 0 or r >= l and r != wdirrev:
                raise ValueError
            return repo[r]
        except error.FilteredIndexError:
            raise
        except (ValueError, OverflowError, IndexError):
            pass

        if len(symbol) == 2 * repo.nodeconstants.nodelen:
            try:
                node = bin(symbol)
                rev = repo.changelog.rev(node)
                return repo[rev]
            except error.FilteredLookupError:
                raise
            except (TypeError, LookupError):
                pass

        # look up bookmarks through the name interface
        try:
            node = repo.names.singlenode(repo, symbol)
            rev = repo.changelog.rev(node)
            return repo[rev]
        except KeyError:
            pass

        node = resolvehexnodeidprefix(repo, symbol)
        if node is not None:
            rev = repo.changelog.rev(node)
            return repo[rev]

        raise error.RepoLookupError(_(b"unknown revision '%s'") % symbol)

    except error.WdirUnsupported:
        return repo[None]
    except (
        error.FilteredIndexError,
        error.FilteredLookupError,
        error.FilteredRepoLookupError,
    ):
        raise _filterederror(repo, symbol)


def _filterederror(repo, changeid):
    """build an exception to be raised about a filtered changeid

    This is extracted in a function to help extensions (eg: evolve) to
    experiment with various message variants."""
    if repo.filtername.startswith(b'visible'):

        # Check if the changeset is obsolete
        unfilteredrepo = repo.unfiltered()
        ctx = revsymbol(unfilteredrepo, changeid)

        # If the changeset is obsolete, enrich the message with the reason
        # that made this changeset not visible
        if ctx.obsolete():
            msg = obsutil._getfilteredreason(repo, changeid, ctx)
        else:
            msg = _(b"hidden revision '%s'") % changeid

        hint = _(b'use --hidden to access hidden revisions')

        return error.FilteredRepoLookupError(msg, hint=hint)
    msg = _(b"filtered revision '%s' (not in '%s' subset)")
    msg %= (changeid, repo.filtername)
    return error.FilteredRepoLookupError(msg)


def revsingle(repo, revspec, default=b'.', localalias=None):
    if not revspec and revspec != 0:
        return repo[default]

    l = revrange(repo, [revspec], localalias=localalias)
    if not l:
        raise error.Abort(_(b'empty revision set'))
    return repo[l.last()]


def _pairspec(revspec):
    tree = revsetlang.parse(revspec)
    return tree and tree[0] in (
        b'range',
        b'rangepre',
        b'rangepost',
        b'rangeall',
    )


def revpair(repo, revs):
    if not revs:
        return repo[b'.'], repo[None]

    l = revrange(repo, revs)

    if not l:
        raise error.Abort(_(b'empty revision range'))

    first = l.first()
    second = l.last()

    if (
        first == second
        and len(revs) >= 2
        and not all(revrange(repo, [r]) for r in revs)
    ):
        raise error.Abort(_(b'empty revision on one side of range'))

    # if top-level is range expression, the result must always be a pair
    if first == second and len(revs) == 1 and not _pairspec(revs[0]):
        return repo[first], repo[None]

    return repo[first], repo[second]


def revrange(repo, specs, localalias=None):
    """Execute 1 to many revsets and return the union.

    This is the preferred mechanism for executing revsets using user-specified
    config options, such as revset aliases.

    The revsets specified by ``specs`` will be executed via a chained ``OR``
    expression. If ``specs`` is empty, an empty result is returned.

    ``specs`` can contain integers, in which case they are assumed to be
    revision numbers.

    It is assumed the revsets are already formatted. If you have arguments
    that need to be expanded in the revset, call ``revsetlang.formatspec()``
    and pass the result as an element of ``specs``.

    Specifying a single revset is allowed.

    Returns a ``smartset.abstractsmartset`` which is a list-like interface over
    integer revisions.
    """
    allspecs = []
    for spec in specs:
        if isinstance(spec, int):
            spec = revsetlang.formatspec(b'%d', spec)
        allspecs.append(spec)
    return repo.anyrevs(allspecs, user=True, localalias=localalias)


def increasingwindows(windowsize=8, sizelimit=512):
    while True:
        yield windowsize
        if windowsize < sizelimit:
            windowsize *= 2


def walkchangerevs(repo, revs, makefilematcher, prepare):
    """Iterate over files and the revs in a "windowed" way.

    Callers most commonly need to iterate backwards over the history
    in which they are interested. Doing so has awful (quadratic-looking)
    performance, so we use iterators in a "windowed" way.

    We walk a window of revisions in the desired order.  Within the
    window, we first walk forwards to gather data, then in the desired
    order (usually backwards) to display it.

    This function returns an iterator yielding contexts. Before
    yielding each context, the iterator will first call the prepare
    function on each context in the window in forward order."""

    if not revs:
        return []
    change = repo.__getitem__

    def iterate():
        it = iter(revs)
        stopiteration = False
        for windowsize in increasingwindows():
            nrevs = []
            for i in pycompat.xrange(windowsize):
                rev = next(it, None)
                if rev is None:
                    stopiteration = True
                    break
                nrevs.append(rev)
            for rev in sorted(nrevs):
                ctx = change(rev)
                prepare(ctx, makefilematcher(ctx))
            for rev in nrevs:
                yield change(rev)

            if stopiteration:
                break

    return iterate()


def meaningfulparents(repo, ctx):
    """Return list of meaningful (or all if debug) parentrevs for rev.

    For merges (two non-nullrev revisions) both parents are meaningful.
    Otherwise the first parent revision is considered meaningful if it
    is not the preceding revision.
    """
    parents = ctx.parents()
    if len(parents) > 1:
        return parents
    if repo.ui.debugflag:
        return [parents[0], repo[nullrev]]
    if parents[0].rev() >= intrev(ctx) - 1:
        return []
    return parents


def getuipathfn(repo, legacyrelativevalue=False, forcerelativevalue=None):
    """Return a function that produced paths for presenting to the user.

    The returned function takes a repo-relative path and produces a path
    that can be presented in the UI.

    Depending on the value of ui.relative-paths, either a repo-relative or
    cwd-relative path will be produced.

    legacyrelativevalue is the value to use if ui.relative-paths=legacy

    If forcerelativevalue is not None, then that value will be used regardless
    of what ui.relative-paths is set to.
    """
    if forcerelativevalue is not None:
        relative = forcerelativevalue
    else:
        config = repo.ui.config(b'ui', b'relative-paths')
        if config == b'legacy':
            relative = legacyrelativevalue
        else:
            relative = stringutil.parsebool(config)
            if relative is None:
                raise error.ConfigError(
                    _(b"ui.relative-paths is not a boolean ('%s')") % config
                )

    if relative:
        cwd = repo.getcwd()
        if cwd != b'':
            # this branch would work even if cwd == b'' (ie cwd = repo
            # root), but its generality makes the returned function slower
            pathto = repo.pathto
            return lambda f: pathto(f, cwd)
    if repo.ui.configbool(b'ui', b'slash'):
        return lambda f: f
    else:
        return util.localpath


def subdiruipathfn(subpath, uipathfn):
    '''Create a new uipathfn that treats the file as relative to subpath.'''
    return lambda f: uipathfn(posixpath.join(subpath, f))


def anypats(pats, opts):
    """Checks if any patterns, including --include and --exclude were given.

    Some commands (e.g. addremove) use this condition for deciding whether to
    print absolute or relative paths.
    """
    return bool(pats or opts.get(b'include') or opts.get(b'exclude'))


def expandpats(pats):
    """Expand bare globs when running on windows.
    On posix we assume it already has already been done by sh."""
    if not util.expandglobs:
        return list(pats)
    ret = []
    for kindpat in pats:
        kind, pat = matchmod._patsplit(kindpat, None)
        if kind is None:
            try:
                globbed = glob.glob(pat)
            except re.error:
                globbed = [pat]
            if globbed:
                ret.extend(globbed)
                continue
        ret.append(kindpat)
    return ret


def matchandpats(
    ctx, pats=(), opts=None, globbed=False, default=b'relpath', badfn=None
):
    """Return a matcher and the patterns that were used.
    The matcher will warn about bad matches, unless an alternate badfn callback
    is provided."""
    if opts is None:
        opts = {}
    if not globbed and default == b'relpath':
        pats = expandpats(pats or [])

    uipathfn = getuipathfn(ctx.repo(), legacyrelativevalue=True)

    def bad(f, msg):
        ctx.repo().ui.warn(b"%s: %s\n" % (uipathfn(f), msg))

    if badfn is None:
        badfn = bad

    m = ctx.match(
        pats,
        opts.get(b'include'),
        opts.get(b'exclude'),
        default,
        listsubrepos=opts.get(b'subrepos'),
        badfn=badfn,
    )

    if m.always():
        pats = []
    return m, pats


def match(
    ctx, pats=(), opts=None, globbed=False, default=b'relpath', badfn=None
):
    '''Return a matcher that will warn about bad matches.'''
    return matchandpats(ctx, pats, opts, globbed, default, badfn=badfn)[0]


def matchall(repo):
    '''Return a matcher that will efficiently match everything.'''
    return matchmod.always()


def matchfiles(repo, files, badfn=None):
    '''Return a matcher that will efficiently match exactly these files.'''
    return matchmod.exact(files, badfn=badfn)


def parsefollowlinespattern(repo, rev, pat, msg):
    """Return a file name from `pat` pattern suitable for usage in followlines
    logic.
    """
    if not matchmod.patkind(pat):
        return pathutil.canonpath(repo.root, repo.getcwd(), pat)
    else:
        ctx = repo[rev]
        m = matchmod.match(repo.root, repo.getcwd(), [pat], ctx=ctx)
        files = [f for f in ctx if m(f)]
        if len(files) != 1:
            raise error.ParseError(msg)
        return files[0]


def getorigvfs(ui, repo):
    """return a vfs suitable to save 'orig' file

    return None if no special directory is configured"""
    origbackuppath = ui.config(b'ui', b'origbackuppath')
    if not origbackuppath:
        return None
    return vfs.vfs(repo.wvfs.join(origbackuppath))


def backuppath(ui, repo, filepath):
    """customize where working copy backup files (.orig files) are created

    Fetch user defined path from config file: [ui] origbackuppath = <path>
    Fall back to default (filepath with .orig suffix) if not specified

    filepath is repo-relative

    Returns an absolute path
    """
    origvfs = getorigvfs(ui, repo)
    if origvfs is None:
        return repo.wjoin(filepath + b".orig")

    origbackupdir = origvfs.dirname(filepath)
    if not origvfs.isdir(origbackupdir) or origvfs.islink(origbackupdir):
        ui.note(_(b'creating directory: %s\n') % origvfs.join(origbackupdir))

        # Remove any files that conflict with the backup file's path
        for f in reversed(list(pathutil.finddirs(filepath))):
            if origvfs.isfileorlink(f):
                ui.note(_(b'removing conflicting file: %s\n') % origvfs.join(f))
                origvfs.unlink(f)
                break

        origvfs.makedirs(origbackupdir)

    if origvfs.isdir(filepath) and not origvfs.islink(filepath):
        ui.note(
            _(b'removing conflicting directory: %s\n') % origvfs.join(filepath)
        )
        origvfs.rmtree(filepath, forcibly=True)

    return origvfs.join(filepath)


class _containsnode(object):
    """proxy __contains__(node) to container.__contains__ which accepts revs"""

    def __init__(self, repo, revcontainer):
        self._torev = repo.changelog.rev
        self._revcontains = revcontainer.__contains__

    def __contains__(self, node):
        return self._revcontains(self._torev(node))


def cleanupnodes(
    repo,
    replacements,
    operation,
    moves=None,
    metadata=None,
    fixphase=False,
    targetphase=None,
    backup=True,
):
    """do common cleanups when old nodes are replaced by new nodes

    That includes writing obsmarkers or stripping nodes, and moving bookmarks.
    (we might also want to move working directory parent in the future)

    By default, bookmark moves are calculated automatically from 'replacements',
    but 'moves' can be used to override that. Also, 'moves' may include
    additional bookmark moves that should not have associated obsmarkers.

    replacements is {oldnode: [newnode]} or a iterable of nodes if they do not
    have replacements. operation is a string, like "rebase".

    metadata is dictionary containing metadata to be stored in obsmarker if
    obsolescence is enabled.
    """
    assert fixphase or targetphase is None
    if not replacements and not moves:
        return

    # translate mapping's other forms
    if not util.safehasattr(replacements, b'items'):
        replacements = {(n,): () for n in replacements}
    else:
        # upgrading non tuple "source" to tuple ones for BC
        repls = {}
        for key, value in replacements.items():
            if not isinstance(key, tuple):
                key = (key,)
            repls[key] = value
        replacements = repls

    # Unfiltered repo is needed since nodes in replacements might be hidden.
    unfi = repo.unfiltered()

    # Calculate bookmark movements
    if moves is None:
        moves = {}
        for oldnodes, newnodes in replacements.items():
            for oldnode in oldnodes:
                if oldnode in moves:
                    continue
                if len(newnodes) > 1:
                    # usually a split, take the one with biggest rev number
                    newnode = next(unfi.set(b'max(%ln)', newnodes)).node()
                elif len(newnodes) == 0:
                    # move bookmark backwards
                    allreplaced = []
                    for rep in replacements:
                        allreplaced.extend(rep)
                    roots = list(
                        unfi.set(b'max((::%n) - %ln)', oldnode, allreplaced)
                    )
                    if roots:
                        newnode = roots[0].node()
                    else:
                        newnode = repo.nullid
                else:
                    newnode = newnodes[0]
                moves[oldnode] = newnode

    allnewnodes = [n for ns in replacements.values() for n in ns]
    toretract = {}
    toadvance = {}
    if fixphase:
        precursors = {}
        for oldnodes, newnodes in replacements.items():
            for oldnode in oldnodes:
                for newnode in newnodes:
                    precursors.setdefault(newnode, []).append(oldnode)

        allnewnodes.sort(key=lambda n: unfi[n].rev())
        newphases = {}

        def phase(ctx):
            return newphases.get(ctx.node(), ctx.phase())

        for newnode in allnewnodes:
            ctx = unfi[newnode]
            parentphase = max(phase(p) for p in ctx.parents())
            if targetphase is None:
                oldphase = max(
                    unfi[oldnode].phase() for oldnode in precursors[newnode]
                )
                newphase = max(oldphase, parentphase)
            else:
                newphase = max(targetphase, parentphase)
            newphases[newnode] = newphase
            if newphase > ctx.phase():
                toretract.setdefault(newphase, []).append(newnode)
            elif newphase < ctx.phase():
                toadvance.setdefault(newphase, []).append(newnode)

    with repo.transaction(b'cleanup') as tr:
        # Move bookmarks
        bmarks = repo._bookmarks
        bmarkchanges = []
        for oldnode, newnode in moves.items():
            oldbmarks = repo.nodebookmarks(oldnode)
            if not oldbmarks:
                continue
            from . import bookmarks  # avoid import cycle

            repo.ui.debug(
                b'moving bookmarks %r from %s to %s\n'
                % (
                    pycompat.rapply(pycompat.maybebytestr, oldbmarks),
                    hex(oldnode),
                    hex(newnode),
                )
            )
            # Delete divergent bookmarks being parents of related newnodes
            deleterevs = repo.revs(
                b'parents(roots(%ln & (::%n))) - parents(%n)',
                allnewnodes,
                newnode,
                oldnode,
            )
            deletenodes = _containsnode(repo, deleterevs)
            for name in oldbmarks:
                bmarkchanges.append((name, newnode))
                for b in bookmarks.divergent2delete(repo, deletenodes, name):
                    bmarkchanges.append((b, None))

        if bmarkchanges:
            bmarks.applychanges(repo, tr, bmarkchanges)

        for phase, nodes in toretract.items():
            phases.retractboundary(repo, tr, phase, nodes)
        for phase, nodes in toadvance.items():
            phases.advanceboundary(repo, tr, phase, nodes)

        mayusearchived = repo.ui.config(b'experimental', b'cleanup-as-archived')
        # Obsolete or strip nodes
        if obsolete.isenabled(repo, obsolete.createmarkersopt):
            # If a node is already obsoleted, and we want to obsolete it
            # without a successor, skip that obssolete request since it's
            # unnecessary. That's the "if s or not isobs(n)" check below.
            # Also sort the node in topology order, that might be useful for
            # some obsstore logic.
            # NOTE: the sorting might belong to createmarkers.
            torev = unfi.changelog.rev
            sortfunc = lambda ns: torev(ns[0][0])
            rels = []
            for ns, s in sorted(replacements.items(), key=sortfunc):
                rel = (tuple(unfi[n] for n in ns), tuple(unfi[m] for m in s))
                rels.append(rel)
            if rels:
                obsolete.createmarkers(
                    repo, rels, operation=operation, metadata=metadata
                )
        elif phases.supportinternal(repo) and mayusearchived:
            # this assume we do not have "unstable" nodes above the cleaned ones
            allreplaced = set()
            for ns in replacements.keys():
                allreplaced.update(ns)
            if backup:
                from . import repair  # avoid import cycle

                node = min(allreplaced, key=repo.changelog.rev)
                repair.backupbundle(
                    repo, allreplaced, allreplaced, node, operation
                )
            phases.retractboundary(repo, tr, phases.archived, allreplaced)
        else:
            from . import repair  # avoid import cycle

            tostrip = list(n for ns in replacements for n in ns)
            if tostrip:
                repair.delayedstrip(
                    repo.ui, repo, tostrip, operation, backup=backup
                )


def addremove(repo, matcher, prefix, uipathfn, opts=None):
    if opts is None:
        opts = {}
    m = matcher
    dry_run = opts.get(b'dry_run')
    try:
        similarity = float(opts.get(b'similarity') or 0)
    except ValueError:
        raise error.Abort(_(b'similarity must be a number'))
    if similarity < 0 or similarity > 100:
        raise error.Abort(_(b'similarity must be between 0 and 100'))
    similarity /= 100.0

    ret = 0

    wctx = repo[None]
    for subpath in sorted(wctx.substate):
        submatch = matchmod.subdirmatcher(subpath, m)
        if opts.get(b'subrepos') or m.exact(subpath) or any(submatch.files()):
            sub = wctx.sub(subpath)
            subprefix = repo.wvfs.reljoin(prefix, subpath)
            subuipathfn = subdiruipathfn(subpath, uipathfn)
            try:
                if sub.addremove(submatch, subprefix, subuipathfn, opts):
                    ret = 1
            except error.LookupError:
                repo.ui.status(
                    _(b"skipping missing subrepository: %s\n")
                    % uipathfn(subpath)
                )

    rejected = []

    def badfn(f, msg):
        if f in m.files():
            m.bad(f, msg)
        rejected.append(f)

    badmatch = matchmod.badmatch(m, badfn)
    added, unknown, deleted, removed, forgotten = _interestingfiles(
        repo, badmatch
    )

    unknownset = set(unknown + forgotten)
    toprint = unknownset.copy()
    toprint.update(deleted)
    for abs in sorted(toprint):
        if repo.ui.verbose or not m.exact(abs):
            if abs in unknownset:
                status = _(b'adding %s\n') % uipathfn(abs)
                label = b'ui.addremove.added'
            else:
                status = _(b'removing %s\n') % uipathfn(abs)
                label = b'ui.addremove.removed'
            repo.ui.status(status, label=label)

    renames = _findrenames(
        repo, m, added + unknown, removed + deleted, similarity, uipathfn
    )

    if not dry_run:
        _markchanges(repo, unknown + forgotten, deleted, renames)

    for f in rejected:
        if f in m.files():
            return 1
    return ret


def marktouched(repo, files, similarity=0.0):
    """Assert that files have somehow been operated upon. files are relative to
    the repo root."""
    m = matchfiles(repo, files, badfn=lambda x, y: rejected.append(x))
    rejected = []

    added, unknown, deleted, removed, forgotten = _interestingfiles(repo, m)

    if repo.ui.verbose:
        unknownset = set(unknown + forgotten)
        toprint = unknownset.copy()
        toprint.update(deleted)
        for abs in sorted(toprint):
            if abs in unknownset:
                status = _(b'adding %s\n') % abs
            else:
                status = _(b'removing %s\n') % abs
            repo.ui.status(status)

    # TODO: We should probably have the caller pass in uipathfn and apply it to
    # the messages above too. legacyrelativevalue=True is consistent with how
    # it used to work.
    uipathfn = getuipathfn(repo, legacyrelativevalue=True)
    renames = _findrenames(
        repo, m, added + unknown, removed + deleted, similarity, uipathfn
    )

    _markchanges(repo, unknown + forgotten, deleted, renames)

    for f in rejected:
        if f in m.files():
            return 1
    return 0


def _interestingfiles(repo, matcher):
    """Walk dirstate with matcher, looking for files that addremove would care
    about.

    This is different from dirstate.status because it doesn't care about
    whether files are modified or clean."""
    added, unknown, deleted, removed, forgotten = [], [], [], [], []
    audit_path = pathutil.pathauditor(repo.root, cached=True)

    ctx = repo[None]
    dirstate = repo.dirstate
    matcher = repo.narrowmatch(matcher, includeexact=True)
    walkresults = dirstate.walk(
        matcher,
        subrepos=sorted(ctx.substate),
        unknown=True,
        ignored=False,
        full=False,
    )
    for abs, st in pycompat.iteritems(walkresults):
        dstate = dirstate[abs]
        if dstate == b'?' and audit_path.check(abs):
            unknown.append(abs)
        elif dstate != b'r' and not st:
            deleted.append(abs)
        elif dstate == b'r' and st:
            forgotten.append(abs)
        # for finding renames
        elif dstate == b'r' and not st:
            removed.append(abs)
        elif dstate == b'a':
            added.append(abs)

    return added, unknown, deleted, removed, forgotten


def _findrenames(repo, matcher, added, removed, similarity, uipathfn):
    '''Find renames from removed files to added ones.'''
    renames = {}
    if similarity > 0:
        for old, new, score in similar.findrenames(
            repo, added, removed, similarity
        ):
            if (
                repo.ui.verbose
                or not matcher.exact(old)
                or not matcher.exact(new)
            ):
                repo.ui.status(
                    _(
                        b'recording removal of %s as rename to %s '
                        b'(%d%% similar)\n'
                    )
                    % (uipathfn(old), uipathfn(new), score * 100)
                )
            renames[new] = old
    return renames


def _markchanges(repo, unknown, deleted, renames):
    """Marks the files in unknown as added, the files in deleted as removed,
    and the files in renames as copied."""
    wctx = repo[None]
    with repo.wlock():
        wctx.forget(deleted)
        wctx.add(unknown)
        for new, old in pycompat.iteritems(renames):
            wctx.copy(old, new)


def getrenamedfn(repo, endrev=None):
    if copiesmod.usechangesetcentricalgo(repo):

        def getrenamed(fn, rev):
            ctx = repo[rev]
            p1copies = ctx.p1copies()
            if fn in p1copies:
                return p1copies[fn]
            p2copies = ctx.p2copies()
            if fn in p2copies:
                return p2copies[fn]
            return None

        return getrenamed

    rcache = {}
    if endrev is None:
        endrev = len(repo)

    def getrenamed(fn, rev):
        """looks up all renames for a file (up to endrev) the first
        time the file is given. It indexes on the changerev and only
        parses the manifest if linkrev != changerev.
        Returns rename info for fn at changerev rev."""
        if fn not in rcache:
            rcache[fn] = {}
            fl = repo.file(fn)
            for i in fl:
                lr = fl.linkrev(i)
                renamed = fl.renamed(fl.node(i))
                rcache[fn][lr] = renamed and renamed[0]
                if lr >= endrev:
                    break
        if rev in rcache[fn]:
            return rcache[fn][rev]

        # If linkrev != rev (i.e. rev not found in rcache) fallback to
        # filectx logic.
        try:
            return repo[rev][fn].copysource()
        except error.LookupError:
            return None

    return getrenamed


def getcopiesfn(repo, endrev=None):
    if copiesmod.usechangesetcentricalgo(repo):

        def copiesfn(ctx):
            if ctx.p2copies():
                allcopies = ctx.p1copies().copy()
                # There should be no overlap
                allcopies.update(ctx.p2copies())
                return sorted(allcopies.items())
            else:
                return sorted(ctx.p1copies().items())

    else:
        getrenamed = getrenamedfn(repo, endrev)

        def copiesfn(ctx):
            copies = []
            for fn in ctx.files():
                rename = getrenamed(fn, ctx.rev())
                if rename:
                    copies.append((fn, rename))
            return copies

    return copiesfn


def dirstatecopy(ui, repo, wctx, src, dst, dryrun=False, cwd=None):
    """Update the dirstate to reflect the intent of copying src to dst. For
    different reasons it might not end with dst being marked as copied from src.
    """
    origsrc = repo.dirstate.copied(src) or src
    if dst == origsrc:  # copying back a copy?
        if repo.dirstate[dst] not in b'mn' and not dryrun:
            repo.dirstate.set_tracked(dst)
    else:
        if repo.dirstate[origsrc] == b'a' and origsrc == src:
            if not ui.quiet:
                ui.warn(
                    _(
                        b"%s has not been committed yet, so no copy "
                        b"data will be stored for %s.\n"
                    )
                    % (repo.pathto(origsrc, cwd), repo.pathto(dst, cwd))
                )
            if repo.dirstate[dst] in b'?r' and not dryrun:
                wctx.add([dst])
        elif not dryrun:
            wctx.copy(origsrc, dst)


def movedirstate(repo, newctx, match=None):
    """Move the dirstate to newctx and adjust it as necessary.

    A matcher can be provided as an optimization. It is probably a bug to pass
    a matcher that doesn't match all the differences between the parent of the
    working copy and newctx.
    """
    oldctx = repo[b'.']
    ds = repo.dirstate
    copies = dict(ds.copies())
    ds.setparents(newctx.node(), repo.nullid)
    s = newctx.status(oldctx, match=match)

    for f in s.modified:
        ds.update_file_p1(f, p1_tracked=True)

    for f in s.added:
        ds.update_file_p1(f, p1_tracked=False)

    for f in s.removed:
        ds.update_file_p1(f, p1_tracked=True)

    # Merge old parent and old working dir copies
    oldcopies = copiesmod.pathcopies(newctx, oldctx, match)
    oldcopies.update(copies)
    copies = {
        dst: oldcopies.get(src, src)
        for dst, src in pycompat.iteritems(oldcopies)
    }
    # Adjust the dirstate copies
    for dst, src in pycompat.iteritems(copies):
        if src not in newctx or dst in newctx or ds[dst] != b'a':
            src = None
        ds.copy(src, dst)
    repo._quick_access_changeid_invalidate()


def filterrequirements(requirements):
    """filters the requirements into two sets:

    wcreq: requirements which should be written in .hg/requires
    storereq: which should be written in .hg/store/requires

    Returns (wcreq, storereq)
    """
    if requirementsmod.SHARESAFE_REQUIREMENT in requirements:
        wc, store = set(), set()
        for r in requirements:
            if r in requirementsmod.WORKING_DIR_REQUIREMENTS:
                wc.add(r)
            else:
                store.add(r)
        return wc, store
    return requirements, None


def istreemanifest(repo):
    """returns whether the repository is using treemanifest or not"""
    return requirementsmod.TREEMANIFEST_REQUIREMENT in repo.requirements


def writereporequirements(repo, requirements=None):
    """writes requirements for the repo

    Requirements are written to .hg/requires and .hg/store/requires based
    on whether share-safe mode is enabled and which requirements are wdir
    requirements and which are store requirements
    """
    if requirements:
        repo.requirements = requirements
    wcreq, storereq = filterrequirements(repo.requirements)
    if wcreq is not None:
        writerequires(repo.vfs, wcreq)
    if storereq is not None:
        writerequires(repo.svfs, storereq)
    elif repo.ui.configbool(b'format', b'usestore'):
        # only remove store requires if we are using store
        repo.svfs.tryunlink(b'requires')


def writerequires(opener, requirements):
    with opener(b'requires', b'w', atomictemp=True) as fp:
        for r in sorted(requirements):
            fp.write(b"%s\n" % r)


class filecachesubentry(object):
    def __init__(self, path, stat):
        self.path = path
        self.cachestat = None
        self._cacheable = None

        if stat:
            self.cachestat = filecachesubentry.stat(self.path)

            if self.cachestat:
                self._cacheable = self.cachestat.cacheable()
            else:
                # None means we don't know yet
                self._cacheable = None

    def refresh(self):
        if self.cacheable():
            self.cachestat = filecachesubentry.stat(self.path)

    def cacheable(self):
        if self._cacheable is not None:
            return self._cacheable

        # we don't know yet, assume it is for now
        return True

    def changed(self):
        # no point in going further if we can't cache it
        if not self.cacheable():
            return True

        newstat = filecachesubentry.stat(self.path)

        # we may not know if it's cacheable yet, check again now
        if newstat and self._cacheable is None:
            self._cacheable = newstat.cacheable()

            # check again
            if not self._cacheable:
                return True

        if self.cachestat != newstat:
            self.cachestat = newstat
            return True
        else:
            return False

    @staticmethod
    def stat(path):
        try:
            return util.cachestat(path)
        except OSError as e:
            if e.errno != errno.ENOENT:
                raise


class filecacheentry(object):
    def __init__(self, paths, stat=True):
        self._entries = []
        for path in paths:
            self._entries.append(filecachesubentry(path, stat))

    def changed(self):
        '''true if any entry has changed'''
        for entry in self._entries:
            if entry.changed():
                return True
        return False

    def refresh(self):
        for entry in self._entries:
            entry.refresh()


class filecache(object):
    """A property like decorator that tracks files under .hg/ for updates.

    On first access, the files defined as arguments are stat()ed and the
    results cached. The decorated function is called. The results are stashed
    away in a ``_filecache`` dict on the object whose method is decorated.

    On subsequent access, the cached result is used as it is set to the
    instance dictionary.

    On external property set/delete operations, the caller must update the
    corresponding _filecache entry appropriately. Use __class__.<attr>.set()
    instead of directly setting <attr>.

    When using the property API, the cached data is always used if available.
    No stat() is performed to check if the file has changed.

    Others can muck about with the state of the ``_filecache`` dict. e.g. they
    can populate an entry before the property's getter is called. In this case,
    entries in ``_filecache`` will be used during property operations,
    if available. If the underlying file changes, it is up to external callers
    to reflect this by e.g. calling ``delattr(obj, attr)`` to remove the cached
    method result as well as possibly calling ``del obj._filecache[attr]`` to
    remove the ``filecacheentry``.
    """

    def __init__(self, *paths):
        self.paths = paths

    def join(self, obj, fname):
        """Used to compute the runtime path of a cached file.

        Users should subclass filecache and provide their own version of this
        function to call the appropriate join function on 'obj' (an instance
        of the class that its member function was decorated).
        """
        raise NotImplementedError

    def __call__(self, func):
        self.func = func
        self.sname = func.__name__
        self.name = pycompat.sysbytes(self.sname)
        return self

    def __get__(self, obj, type=None):
        # if accessed on the class, return the descriptor itself.
        if obj is None:
            return self

        assert self.sname not in obj.__dict__

        entry = obj._filecache.get(self.name)

        if entry:
            if entry.changed():
                entry.obj = self.func(obj)
        else:
            paths = [self.join(obj, path) for path in self.paths]

            # We stat -before- creating the object so our cache doesn't lie if
            # a writer modified between the time we read and stat
            entry = filecacheentry(paths, True)
            entry.obj = self.func(obj)

            obj._filecache[self.name] = entry

        obj.__dict__[self.sname] = entry.obj
        return entry.obj

    # don't implement __set__(), which would make __dict__ lookup as slow as
    # function call.

    def set(self, obj, value):
        if self.name not in obj._filecache:
            # we add an entry for the missing value because X in __dict__
            # implies X in _filecache
            paths = [self.join(obj, path) for path in self.paths]
            ce = filecacheentry(paths, False)
            obj._filecache[self.name] = ce
        else:
            ce = obj._filecache[self.name]

        ce.obj = value  # update cached copy
        obj.__dict__[self.sname] = value  # update copy returned by obj.x


def extdatasource(repo, source):
    """Gather a map of rev -> value dict from the specified source

    A source spec is treated as a URL, with a special case shell: type
    for parsing the output from a shell command.

    The data is parsed as a series of newline-separated records where
    each record is a revision specifier optionally followed by a space
    and a freeform string value. If the revision is known locally, it
    is converted to a rev, otherwise the record is skipped.

    Note that both key and value are treated as UTF-8 and converted to
    the local encoding. This allows uniformity between local and
    remote data sources.
    """

    spec = repo.ui.config(b"extdata", source)
    if not spec:
        raise error.Abort(_(b"unknown extdata source '%s'") % source)

    data = {}
    src = proc = None
    try:
        if spec.startswith(b"shell:"):
            # external commands should be run relative to the repo root
            cmd = spec[6:]
            proc = subprocess.Popen(
                procutil.tonativestr(cmd),
                shell=True,
                bufsize=-1,
                close_fds=procutil.closefds,
                stdout=subprocess.PIPE,
                cwd=procutil.tonativestr(repo.root),
            )
            src = proc.stdout
        else:
            # treat as a URL or file
            src = url.open(repo.ui, spec)
        for l in src:
            if b" " in l:
                k, v = l.strip().split(b" ", 1)
            else:
                k, v = l.strip(), b""

            k = encoding.tolocal(k)
            try:
                data[revsingle(repo, k).rev()] = encoding.tolocal(v)
            except (error.LookupError, error.RepoLookupError, error.InputError):
                pass  # we ignore data for nodes that don't exist locally
    finally:
        if proc:
            try:
                proc.communicate()
            except ValueError:
                # This happens if we started iterating src and then
                # get a parse error on a line. It should be safe to ignore.
                pass
        if src:
            src.close()
    if proc and proc.returncode != 0:
        raise error.Abort(
            _(b"extdata command '%s' failed: %s")
            % (cmd, procutil.explainexit(proc.returncode))
        )

    return data


class progress(object):
    def __init__(self, ui, updatebar, topic, unit=b"", total=None):
        self.ui = ui
        self.pos = 0
        self.topic = topic
        self.unit = unit
        self.total = total
        self.debug = ui.configbool(b'progress', b'debug')
        self._updatebar = updatebar

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, exc_tb):
        self.complete()

    def update(self, pos, item=b"", total=None):
        assert pos is not None
        if total:
            self.total = total
        self.pos = pos
        self._updatebar(self.topic, self.pos, item, self.unit, self.total)
        if self.debug:
            self._printdebug(item)

    def increment(self, step=1, item=b"", total=None):
        self.update(self.pos + step, item, total)

    def complete(self):
        self.pos = None
        self.unit = b""
        self.total = None
        self._updatebar(self.topic, self.pos, b"", self.unit, self.total)

    def _printdebug(self, item):
        unit = b''
        if self.unit:
            unit = b' ' + self.unit
        if item:
            item = b' ' + item

        if self.total:
            pct = 100.0 * self.pos / self.total
            self.ui.debug(
                b'%s:%s %d/%d%s (%4.2f%%)\n'
                % (self.topic, item, self.pos, self.total, unit, pct)
            )
        else:
            self.ui.debug(b'%s:%s %d%s\n' % (self.topic, item, self.pos, unit))


def gdinitconfig(ui):
    """helper function to know if a repo should be created as general delta"""
    # experimental config: format.generaldelta
    return ui.configbool(b'format', b'generaldelta') or ui.configbool(
        b'format', b'usegeneraldelta'
    )


def gddeltaconfig(ui):
    """helper function to know if incoming delta should be optimised"""
    # experimental config: format.generaldelta
    return ui.configbool(b'format', b'generaldelta')


class simplekeyvaluefile(object):
    """A simple file with key=value lines

    Keys must be alphanumerics and start with a letter, values must not
    contain '\n' characters"""

    firstlinekey = b'__firstline'

    def __init__(self, vfs, path, keys=None):
        self.vfs = vfs
        self.path = path

    def read(self, firstlinenonkeyval=False):
        """Read the contents of a simple key-value file

        'firstlinenonkeyval' indicates whether the first line of file should
        be treated as a key-value pair or reuturned fully under the
        __firstline key."""
        lines = self.vfs.readlines(self.path)
        d = {}
        if firstlinenonkeyval:
            if not lines:
                e = _(b"empty simplekeyvalue file")
                raise error.CorruptedState(e)
            # we don't want to include '\n' in the __firstline
            d[self.firstlinekey] = lines[0][:-1]
            del lines[0]

        try:
            # the 'if line.strip()' part prevents us from failing on empty
            # lines which only contain '\n' therefore are not skipped
            # by 'if line'
            updatedict = dict(
                line[:-1].split(b'=', 1) for line in lines if line.strip()
            )
            if self.firstlinekey in updatedict:
                e = _(b"%r can't be used as a key")
                raise error.CorruptedState(e % self.firstlinekey)
            d.update(updatedict)
        except ValueError as e:
            raise error.CorruptedState(stringutil.forcebytestr(e))
        return d

    def write(self, data, firstline=None):
        """Write key=>value mapping to a file
        data is a dict. Keys must be alphanumerical and start with a letter.
        Values must not contain newline characters.

        If 'firstline' is not None, it is written to file before
        everything else, as it is, not in a key=value form"""
        lines = []
        if firstline is not None:
            lines.append(b'%s\n' % firstline)

        for k, v in data.items():
            if k == self.firstlinekey:
                e = b"key name '%s' is reserved" % self.firstlinekey
                raise error.ProgrammingError(e)
            if not k[0:1].isalpha():
                e = b"keys must start with a letter in a key-value file"
                raise error.ProgrammingError(e)
            if not k.isalnum():
                e = b"invalid key name in a simple key-value file"
                raise error.ProgrammingError(e)
            if b'\n' in v:
                e = b"invalid value in a simple key-value file"
                raise error.ProgrammingError(e)
            lines.append(b"%s=%s\n" % (k, v))
        with self.vfs(self.path, mode=b'wb', atomictemp=True) as fp:
            fp.write(b''.join(lines))


_reportobsoletedsource = [
    b'debugobsolete',
    b'pull',
    b'push',
    b'serve',
    b'unbundle',
]

_reportnewcssource = [
    b'pull',
    b'unbundle',
]


def prefetchfiles(repo, revmatches):
    """Invokes the registered file prefetch functions, allowing extensions to
    ensure the corresponding files are available locally, before the command
    uses them.

    Args:
      revmatches: a list of (revision, match) tuples to indicate the files to
      fetch at each revision. If any of the match elements is None, it matches
      all files.
    """

    def _matcher(m):
        if m:
            assert isinstance(m, matchmod.basematcher)
            # The command itself will complain about files that don't exist, so
            # don't duplicate the message.
            return matchmod.badmatch(m, lambda fn, msg: None)
        else:
            return matchall(repo)

    revbadmatches = [(rev, _matcher(match)) for (rev, match) in revmatches]

    fileprefetchhooks(repo, revbadmatches)


# a list of (repo, revs, match) prefetch functions
fileprefetchhooks = util.hooks()

# A marker that tells the evolve extension to suppress its own reporting
_reportstroubledchangesets = True


def registersummarycallback(repo, otr, txnname=b'', as_validator=False):
    """register a callback to issue a summary after the transaction is closed

    If as_validator is true, then the callbacks are registered as transaction
    validators instead
    """

    def txmatch(sources):
        return any(txnname.startswith(source) for source in sources)

    categories = []

    def reportsummary(func):
        """decorator for report callbacks."""
        # The repoview life cycle is shorter than the one of the actual
        # underlying repository. So the filtered object can die before the
        # weakref is used leading to troubles. We keep a reference to the
        # unfiltered object and restore the filtering when retrieving the
        # repository through the weakref.
        filtername = repo.filtername
        reporef = weakref.ref(repo.unfiltered())

        def wrapped(tr):
            repo = reporef()
            if filtername:
                assert repo is not None  # help pytype
                repo = repo.filtered(filtername)
            func(repo, tr)

        newcat = b'%02i-txnreport' % len(categories)
        if as_validator:
            otr.addvalidator(newcat, wrapped)
        else:
            otr.addpostclose(newcat, wrapped)
        categories.append(newcat)
        return wrapped

    @reportsummary
    def reportchangegroup(repo, tr):
        cgchangesets = tr.changes.get(b'changegroup-count-changesets', 0)
        cgrevisions = tr.changes.get(b'changegroup-count-revisions', 0)
        cgfiles = tr.changes.get(b'changegroup-count-files', 0)
        cgheads = tr.changes.get(b'changegroup-count-heads', 0)
        if cgchangesets or cgrevisions or cgfiles:
            htext = b""
            if cgheads:
                htext = _(b" (%+d heads)") % cgheads
            msg = _(b"added %d changesets with %d changes to %d files%s\n")
            if as_validator:
                msg = _(b"adding %d changesets with %d changes to %d files%s\n")
            assert repo is not None  # help pytype
            repo.ui.status(msg % (cgchangesets, cgrevisions, cgfiles, htext))

    if txmatch(_reportobsoletedsource):

        @reportsummary
        def reportobsoleted(repo, tr):
            obsoleted = obsutil.getobsoleted(repo, tr)
            newmarkers = len(tr.changes.get(b'obsmarkers', ()))
            if newmarkers:
                repo.ui.status(_(b'%i new obsolescence markers\n') % newmarkers)
            if obsoleted:
                msg = _(b'obsoleted %i changesets\n')
                if as_validator:
                    msg = _(b'obsoleting %i changesets\n')
                repo.ui.status(msg % len(obsoleted))

    if obsolete.isenabled(
        repo, obsolete.createmarkersopt
    ) and repo.ui.configbool(
        b'experimental', b'evolution.report-instabilities'
    ):
        instabilitytypes = [
            (b'orphan', b'orphan'),
            (b'phase-divergent', b'phasedivergent'),
            (b'content-divergent', b'contentdivergent'),
        ]

        def getinstabilitycounts(repo):
            filtered = repo.changelog.filteredrevs
            counts = {}
            for instability, revset in instabilitytypes:
                counts[instability] = len(
                    set(obsolete.getrevs(repo, revset)) - filtered
                )
            return counts

        oldinstabilitycounts = getinstabilitycounts(repo)

        @reportsummary
        def reportnewinstabilities(repo, tr):
            newinstabilitycounts = getinstabilitycounts(repo)
            for instability, revset in instabilitytypes:
                delta = (
                    newinstabilitycounts[instability]
                    - oldinstabilitycounts[instability]
                )
                msg = getinstabilitymessage(delta, instability)
                if msg:
                    repo.ui.warn(msg)

    if txmatch(_reportnewcssource):

        @reportsummary
        def reportnewcs(repo, tr):
            """Report the range of new revisions pulled/unbundled."""
            origrepolen = tr.changes.get(b'origrepolen', len(repo))
            unfi = repo.unfiltered()
            if origrepolen >= len(unfi):
                return

            # Compute the bounds of new visible revisions' range.
            revs = smartset.spanset(repo, start=origrepolen)
            if revs:
                minrev, maxrev = repo[revs.min()], repo[revs.max()]

                if minrev == maxrev:
                    revrange = minrev
                else:
                    revrange = b'%s:%s' % (minrev, maxrev)
                draft = len(repo.revs(b'%ld and draft()', revs))
                secret = len(repo.revs(b'%ld and secret()', revs))
                if not (draft or secret):
                    msg = _(b'new changesets %s\n') % revrange
                elif draft and secret:
                    msg = _(b'new changesets %s (%d drafts, %d secrets)\n')
                    msg %= (revrange, draft, secret)
                elif draft:
                    msg = _(b'new changesets %s (%d drafts)\n')
                    msg %= (revrange, draft)
                elif secret:
                    msg = _(b'new changesets %s (%d secrets)\n')
                    msg %= (revrange, secret)
                else:
                    errormsg = b'entered unreachable condition'
                    raise error.ProgrammingError(errormsg)
                repo.ui.status(msg)

            # search new changesets directly pulled as obsolete
            duplicates = tr.changes.get(b'revduplicates', ())
            obsadded = unfi.revs(
                b'(%d: + %ld) and obsolete()', origrepolen, duplicates
            )
            cl = repo.changelog
            extinctadded = [r for r in obsadded if r not in cl]
            if extinctadded:
                # They are not just obsolete, but obsolete and invisible
                # we call them "extinct" internally but the terms have not been
                # exposed to users.
                msg = b'(%d other changesets obsolete on arrival)\n'
                repo.ui.status(msg % len(extinctadded))

        @reportsummary
        def reportphasechanges(repo, tr):
            """Report statistics of phase changes for changesets pre-existing
            pull/unbundle.
            """
            origrepolen = tr.changes.get(b'origrepolen', len(repo))
            published = []
            for revs, (old, new) in tr.changes.get(b'phases', []):
                if new != phases.public:
                    continue
                published.extend(rev for rev in revs if rev < origrepolen)
            if not published:
                return
            msg = _(b'%d local changesets published\n')
            if as_validator:
                msg = _(b'%d local changesets will be published\n')
            repo.ui.status(msg % len(published))


def getinstabilitymessage(delta, instability):
    """function to return the message to show warning about new instabilities

    exists as a separate function so that extension can wrap to show more
    information like how to fix instabilities"""
    if delta > 0:
        return _(b'%i new %s changesets\n') % (delta, instability)


def nodesummaries(repo, nodes, maxnumnodes=4):
    if len(nodes) <= maxnumnodes or repo.ui.verbose:
        return b' '.join(short(h) for h in nodes)
    first = b' '.join(short(h) for h in nodes[:maxnumnodes])
    return _(b"%s and %d others") % (first, len(nodes) - maxnumnodes)


def enforcesinglehead(repo, tr, desc, accountclosed, filtername):
    """check that no named branch has multiple heads"""
    if desc in (b'strip', b'repair'):
        # skip the logic during strip
        return
    visible = repo.filtered(filtername)
    # possible improvement: we could restrict the check to affected branch
    bm = visible.branchmap()
    for name in bm:
        heads = bm.branchheads(name, closed=accountclosed)
        if len(heads) > 1:
            msg = _(b'rejecting multiple heads on branch "%s"')
            msg %= name
            hint = _(b'%d heads: %s')
            hint %= (len(heads), nodesummaries(repo, heads))
            raise error.Abort(msg, hint=hint)


def wrapconvertsink(sink):
    """Allow extensions to wrap the sink returned by convcmd.convertsink()
    before it is used, whether or not the convert extension was formally loaded.
    """
    return sink


def unhidehashlikerevs(repo, specs, hiddentype):
    """parse the user specs and unhide changesets whose hash or revision number
    is passed.

    hiddentype can be: 1) 'warn': warn while unhiding changesets
                       2) 'nowarn': don't warn while unhiding changesets

    returns a repo object with the required changesets unhidden
    """
    if not repo.filtername or not repo.ui.configbool(
        b'experimental', b'directaccess'
    ):
        return repo

    if repo.filtername not in (b'visible', b'visible-hidden'):
        return repo

    symbols = set()
    for spec in specs:
        try:
            tree = revsetlang.parse(spec)
        except error.ParseError:  # will be reported by scmutil.revrange()
            continue

        symbols.update(revsetlang.gethashlikesymbols(tree))

    if not symbols:
        return repo

    revs = _getrevsfromsymbols(repo, symbols)

    if not revs:
        return repo

    if hiddentype == b'warn':
        unfi = repo.unfiltered()
        revstr = b", ".join([pycompat.bytestr(unfi[l]) for l in revs])
        repo.ui.warn(
            _(
                b"warning: accessing hidden changesets for write "
                b"operation: %s\n"
            )
            % revstr
        )

    # we have to use new filtername to separate branch/tags cache until we can
    # disbale these cache when revisions are dynamically pinned.
    return repo.filtered(b'visible-hidden', revs)


def _getrevsfromsymbols(repo, symbols):
    """parse the list of symbols and returns a set of revision numbers of hidden
    changesets present in symbols"""
    revs = set()
    unfi = repo.unfiltered()
    unficl = unfi.changelog
    cl = repo.changelog
    tiprev = len(unficl)
    allowrevnums = repo.ui.configbool(b'experimental', b'directaccess.revnums')
    for s in symbols:
        try:
            n = int(s)
            if n <= tiprev:
                if not allowrevnums:
                    continue
                else:
                    if n not in cl:
                        revs.add(n)
                    continue
        except ValueError:
            pass

        try:
            s = resolvehexnodeidprefix(unfi, s)
        except (error.LookupError, error.WdirUnsupported):
            s = None

        if s is not None:
            rev = unficl.rev(s)
            if rev not in cl:
                revs.add(rev)

    return revs


def bookmarkrevs(repo, mark):
    """Select revisions reachable by a given bookmark

    If the bookmarked revision isn't a head, an empty set will be returned.
    """
    return repo.revs(format_bookmark_revspec(mark))


def format_bookmark_revspec(mark):
    """Build a revset expression to select revisions reachable by a given
    bookmark"""
    mark = b'literal:' + mark
    return revsetlang.formatspec(
        b"ancestors(bookmark(%s)) - "
        b"ancestors(head() and not bookmark(%s)) - "
        b"ancestors(bookmark() and not bookmark(%s))",
        mark,
        mark,
        mark,
    )
