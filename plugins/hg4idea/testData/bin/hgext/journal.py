# journal.py
#
# Copyright 2014-2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""track previous positions of bookmarks (EXPERIMENTAL)

This extension adds a new command: `hg journal`, which shows you where
bookmarks were previously located.

"""


import collections
import os
import weakref

from mercurial.i18n import _
from mercurial.node import (
    bin,
    hex,
)

from mercurial import (
    bookmarks,
    cmdutil,
    dispatch,
    encoding,
    error,
    extensions,
    hg,
    localrepo,
    lock,
    logcmdutil,
    pycompat,
    registrar,
    util,
)
from mercurial.utils import (
    dateutil,
    procutil,
    stringutil,
)

cmdtable = {}
command = registrar.command(cmdtable)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

# storage format version; increment when the format changes
storageversion = 0

# namespaces
bookmarktype = b'bookmark'
wdirparenttype = b'wdirparent'
# In a shared repository, what shared feature name is used
# to indicate this namespace is shared with the source?
sharednamespaces = {
    bookmarktype: hg.sharedbookmarks,
}

# Journal recording, register hooks and storage object
def extsetup(ui):
    extensions.wrapfunction(dispatch, 'runcommand', runcommand)
    extensions.wrapfunction(bookmarks.bmstore, '_write', recordbookmarks)
    extensions.wrapfilecache(
        localrepo.localrepository, b'dirstate', wrapdirstate
    )
    extensions.wrapfunction(hg, 'postshare', wrappostshare)
    extensions.wrapfunction(hg, 'copystore', unsharejournal)


def reposetup(ui, repo):
    if repo.local():
        repo.journal = journalstorage(repo)
        repo._wlockfreeprefix.add(b'namejournal')

        dirstate, cached = localrepo.isfilecached(repo, b'dirstate')
        if cached:
            # already instantiated dirstate isn't yet marked as
            # "journal"-ing, even though repo.dirstate() was already
            # wrapped by own wrapdirstate()
            _setupdirstate(repo, dirstate)


def runcommand(orig, lui, repo, cmd, fullargs, *args):
    """Track the command line options for recording in the journal"""
    journalstorage.recordcommand(*fullargs)
    return orig(lui, repo, cmd, fullargs, *args)


def _setupdirstate(repo, dirstate):
    dirstate.journalstorage = repo.journal
    dirstate.addparentchangecallback(b'journal', recorddirstateparents)


# hooks to record dirstate changes
def wrapdirstate(orig, repo):
    """Make journal storage available to the dirstate object"""
    dirstate = orig(repo)
    if hasattr(repo, 'journal'):
        _setupdirstate(repo, dirstate)
    return dirstate


def recorddirstateparents(dirstate, old, new):
    """Records all dirstate parent changes in the journal."""
    old = list(old)
    new = list(new)
    if hasattr(dirstate, 'journalstorage'):
        # only record two hashes if there was a merge
        oldhashes = old[:1] if old[1] == dirstate._nodeconstants.nullid else old
        newhashes = new[:1] if new[1] == dirstate._nodeconstants.nullid else new
        dirstate.journalstorage.record(
            wdirparenttype, b'.', oldhashes, newhashes
        )


# hooks to record bookmark changes (both local and remote)
def recordbookmarks(orig, store, fp):
    """Records all bookmark changes in the journal."""
    repo = store._repo
    if hasattr(repo, 'journal'):
        oldmarks = bookmarks.bmstore(repo)
        all_marks = set(b for b, n in oldmarks.items())
        all_marks.update(b for b, n in store.items())
        for mark in sorted(all_marks):
            value = store.get(mark, repo.nullid)
            oldvalue = oldmarks.get(mark, repo.nullid)
            if value != oldvalue:
                repo.journal.record(bookmarktype, mark, oldvalue, value)
    return orig(store, fp)


# shared repository support
def _readsharedfeatures(repo):
    """A set of shared features for this repository"""
    try:
        return set(repo.vfs.read(b'shared').splitlines())
    except FileNotFoundError:
        return set()


def _mergeentriesiter(*iterables, **kwargs):
    """Given a set of sorted iterables, yield the next entry in merged order

    Note that by default entries go from most recent to oldest.
    """
    order = kwargs.pop('order', max)
    iterables = [iter(it) for it in iterables]
    # this tracks still active iterables; iterables are deleted as they are
    # exhausted, which is why this is a dictionary and why each entry also
    # stores the key. Entries are mutable so we can store the next value each
    # time.
    iterable_map = {}
    for key, it in enumerate(iterables):
        try:
            iterable_map[key] = [next(it), key, it]
        except StopIteration:
            # empty entry, can be ignored
            pass

    while iterable_map:
        value, key, it = order(iterable_map.values())
        yield value
        try:
            iterable_map[key][0] = next(it)
        except StopIteration:
            # this iterable is empty, remove it from consideration
            del iterable_map[key]


def wrappostshare(orig, sourcerepo, destrepo, **kwargs):
    """Mark this shared working copy as sharing journal information"""
    with destrepo.wlock():
        orig(sourcerepo, destrepo, **kwargs)
        with destrepo.vfs(b'shared', b'a') as fp:
            fp.write(b'journal\n')


def unsharejournal(orig, ui, repo, repopath):
    """Copy shared journal entries into this repo when unsharing"""
    if repo.path == repopath and repo.shared() and hasattr(repo, 'journal'):
        sharedrepo = hg.sharedreposource(repo)
        sharedfeatures = _readsharedfeatures(repo)
        if sharedrepo and sharedfeatures > {b'journal'}:
            # there is a shared repository and there are shared journal entries
            # to copy. move shared date over from source to destination but
            # move the local file first
            if repo.vfs.exists(b'namejournal'):
                journalpath = repo.vfs.join(b'namejournal')
                util.rename(journalpath, journalpath + b'.bak')
            storage = repo.journal
            local = storage._open(
                repo.vfs, filename=b'namejournal.bak', _newestfirst=False
            )
            shared = (
                e
                for e in storage._open(sharedrepo.vfs, _newestfirst=False)
                if sharednamespaces.get(e.namespace) in sharedfeatures
            )
            for entry in _mergeentriesiter(local, shared, order=min):
                storage._write(repo.vfs, entry)

    return orig(ui, repo, repopath)


class journalentry(
    collections.namedtuple(
        'journalentry',
        'timestamp user command namespace name oldhashes newhashes',
    )
):
    """Individual journal entry

    * timestamp: a mercurial (time, timezone) tuple
    * user: the username that ran the command
    * namespace: the entry namespace, an opaque string
    * name: the name of the changed item, opaque string with meaning in the
      namespace
    * command: the hg command that triggered this record
    * oldhashes: a tuple of one or more binary hashes for the old location
    * newhashes: a tuple of one or more binary hashes for the new location

    Handles serialisation from and to the storage format. Fields are
    separated by newlines, hashes are written out in hex separated by commas,
    timestamp and timezone are separated by a space.

    """

    @classmethod
    def fromstorage(cls, line):
        (
            time,
            user,
            command,
            namespace,
            name,
            oldhashes,
            newhashes,
        ) = line.split(b'\n')
        timestamp, tz = time.split()
        timestamp, tz = float(timestamp), int(tz)
        oldhashes = tuple(bin(hash) for hash in oldhashes.split(b','))
        newhashes = tuple(bin(hash) for hash in newhashes.split(b','))
        return cls(
            (timestamp, tz),
            user,
            command,
            namespace,
            name,
            oldhashes,
            newhashes,
        )

    def __bytes__(self):
        """bytes representation for storage"""
        time = b' '.join(map(pycompat.bytestr, self.timestamp))
        oldhashes = b','.join([hex(hash) for hash in self.oldhashes])
        newhashes = b','.join([hex(hash) for hash in self.newhashes])
        return b'\n'.join(
            (
                time,
                self.user,
                self.command,
                self.namespace,
                self.name,
                oldhashes,
                newhashes,
            )
        )

    __str__ = encoding.strmethod(__bytes__)


class journalstorage:
    """Storage for journal entries

    Entries are divided over two files; one with entries that pertain to the
    local working copy *only*, and one with entries that are shared across
    multiple working copies when shared using the share extension.

    Entries are stored with NUL bytes as separators. See the journalentry
    class for the per-entry structure.

    The file format starts with an integer version, delimited by a NUL.

    This storage uses a dedicated lock; this makes it easier to avoid issues
    with adding entries that added when the regular wlock is unlocked (e.g.
    the dirstate).

    """

    _currentcommand = ()
    _lockref = None

    def __init__(self, repo):
        self.user = procutil.getuser()
        self.ui = repo.ui
        self.vfs = repo.vfs

        # is this working copy using a shared storage?
        self.sharedfeatures = self.sharedvfs = None
        if repo.shared():
            features = _readsharedfeatures(repo)
            sharedrepo = hg.sharedreposource(repo)
            if sharedrepo is not None and b'journal' in features:
                self.sharedvfs = sharedrepo.vfs
                self.sharedfeatures = features

    # track the current command for recording in journal entries
    @property
    def command(self):
        commandstr = b' '.join(
            map(procutil.shellquote, journalstorage._currentcommand)
        )
        if b'\n' in commandstr:
            # truncate multi-line commands
            commandstr = commandstr.partition(b'\n')[0] + b' ...'
        return commandstr

    @classmethod
    def recordcommand(cls, *fullargs):
        """Set the current hg arguments, stored with recorded entries"""
        # Set the current command on the class because we may have started
        # with a non-local repo (cloning for example).
        cls._currentcommand = fullargs

    def _currentlock(self, lockref):
        """Returns the lock if it's held, or None if it's not.

        (This is copied from the localrepo class)
        """
        if lockref is None:
            return None
        l = lockref()
        if l is None or not l.held:
            return None
        return l

    def jlock(self, vfs):
        """Create a lock for the journal file"""
        if self._currentlock(self._lockref) is not None:
            raise error.Abort(_(b'journal lock does not support nesting'))
        desc = _(b'journal of %s') % vfs.base
        try:
            l = lock.lock(vfs, b'namejournal.lock', 0, desc=desc)
        except error.LockHeld as inst:
            self.ui.warn(
                _(b"waiting for lock on %s held by %r\n") % (desc, inst.locker)
            )
            # default to 600 seconds timeout
            l = lock.lock(
                vfs,
                b'namejournal.lock',
                self.ui.configint(b"ui", b"timeout"),
                desc=desc,
            )
            self.ui.warn(_(b"got lock after %s seconds\n") % l.delay)
        self._lockref = weakref.ref(l)
        return l

    def record(self, namespace, name, oldhashes, newhashes):
        """Record a new journal entry

        * namespace: an opaque string; this can be used to filter on the type
          of recorded entries.
        * name: the name defining this entry; for bookmarks, this is the
          bookmark name. Can be filtered on when retrieving entries.
        * oldhashes and newhashes: each a single binary hash, or a list of
          binary hashes. These represent the old and new position of the named
          item.

        """
        if not isinstance(oldhashes, list):
            oldhashes = [oldhashes]
        if not isinstance(newhashes, list):
            newhashes = [newhashes]

        entry = journalentry(
            dateutil.makedate(),
            self.user,
            self.command,
            namespace,
            name,
            oldhashes,
            newhashes,
        )

        vfs = self.vfs
        if self.sharedvfs is not None:
            # write to the shared repository if this feature is being
            # shared between working copies.
            if sharednamespaces.get(namespace) in self.sharedfeatures:
                vfs = self.sharedvfs

        self._write(vfs, entry)

    def _write(self, vfs, entry):
        with self.jlock(vfs):
            # open file in amend mode to ensure it is created if missing
            with vfs(b'namejournal', mode=b'a+b') as f:
                f.seek(0, os.SEEK_SET)
                # Read just enough bytes to get a version number (up to 2
                # digits plus separator)
                version = f.read(3).partition(b'\0')[0]
                if version and version != b"%d" % storageversion:
                    # different version of the storage. Exit early (and not
                    # write anything) if this is not a version we can handle or
                    # the file is corrupt. In future, perhaps rotate the file
                    # instead?
                    self.ui.warn(
                        _(b"unsupported journal file version '%s'\n") % version
                    )
                    return
                if not version:
                    # empty file, write version first
                    f.write((b"%d" % storageversion) + b'\0')
                f.seek(0, os.SEEK_END)
                f.write(bytes(entry) + b'\0')

    def filtered(self, namespace=None, name=None):
        """Yield all journal entries with the given namespace or name

        Both the namespace and the name are optional; if neither is given all
        entries in the journal are produced.

        Matching supports regular expressions by using the `re:` prefix
        (use `literal:` to match names or namespaces that start with `re:`)

        """
        if namespace is not None:
            namespace = stringutil.stringmatcher(namespace)[-1]
        if name is not None:
            name = stringutil.stringmatcher(name)[-1]
        for entry in self:
            if namespace is not None and not namespace(entry.namespace):
                continue
            if name is not None and not name(entry.name):
                continue
            yield entry

    def __iter__(self):
        """Iterate over the storage

        Yields journalentry instances for each contained journal record.

        """
        local = self._open(self.vfs)

        if self.sharedvfs is None:
            return local

        # iterate over both local and shared entries, but only those
        # shared entries that are among the currently shared features
        shared = (
            e
            for e in self._open(self.sharedvfs)
            if sharednamespaces.get(e.namespace) in self.sharedfeatures
        )
        return _mergeentriesiter(local, shared)

    def _open(self, vfs, filename=b'namejournal', _newestfirst=True):
        if not vfs.exists(filename):
            return

        with vfs(filename) as f:
            raw = f.read()

        lines = raw.split(b'\0')
        version = lines and lines[0]
        if version != b"%d" % storageversion:
            version = version or _(b'not available')
            raise error.Abort(_(b"unknown journal file version '%s'") % version)

        # Skip the first line, it's a version number. Normally we iterate over
        # these in reverse order to list newest first; only when copying across
        # a shared storage do we forgo reversing.
        lines = lines[1:]
        if _newestfirst:
            lines = reversed(lines)
        for line in lines:
            if not line:
                continue
            yield journalentry.fromstorage(line)


# journal reading
# log options that don't make sense for journal
_ignoreopts = (b'no-merges', b'graph')


@command(
    b'journal',
    [
        (b'', b'all', None, b'show history for all names'),
        (b'c', b'commits', None, b'show commit metadata'),
    ]
    + [opt for opt in cmdutil.logopts if opt[1] not in _ignoreopts],
    b'[OPTION]... [BOOKMARKNAME]',
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def journal(ui, repo, *args, **opts):
    """show the previous position of bookmarks and the working copy

    The journal is used to see the previous commits that bookmarks and the
    working copy pointed to. By default the previous locations for the working
    copy.  Passing a bookmark name will show all the previous positions of
    that bookmark. Use the --all switch to show previous locations for all
    bookmarks and the working copy; each line will then include the bookmark
    name, or '.' for the working copy, as well.

    If `name` starts with `re:`, the remainder of the name is treated as
    a regular expression. To match a name that actually starts with `re:`,
    use the prefix `literal:`.

    By default hg journal only shows the commit hash and the command that was
    running at that time. -v/--verbose will show the prior hash, the user, and
    the time at which it happened.

    Use -c/--commits to output log information on each commit hash; at this
    point you can use the usual `--patch`, `--git`, `--stat` and `--template`
    switches to alter the log output for these.

    `hg journal -T json` can be used to produce machine readable output.

    """
    opts = pycompat.byteskwargs(opts)
    name = b'.'
    if opts.get(b'all'):
        if args:
            raise error.Abort(
                _(b"You can't combine --all and filtering on a name")
            )
        name = None
    if args:
        name = args[0]

    fm = ui.formatter(b'journal', opts)

    def formatnodes(nodes):
        return fm.formatlist(map(fm.hexfunc, nodes), name=b'node', sep=b',')

    if opts.get(b"template") != b"json":
        if name is None:
            displayname = _(b'the working copy and bookmarks')
        else:
            displayname = b"'%s'" % name
        ui.status(_(b"previous locations of %s:\n") % displayname)

    limit = logcmdutil.getlimit(opts)
    entry = None
    ui.pager(b'journal')
    for count, entry in enumerate(repo.journal.filtered(name=name)):
        if count == limit:
            break

        fm.startitem()
        fm.condwrite(
            ui.verbose, b'oldnodes', b'%s -> ', formatnodes(entry.oldhashes)
        )
        fm.write(b'newnodes', b'%s', formatnodes(entry.newhashes))
        fm.condwrite(ui.verbose, b'user', b' %-8s', entry.user)

        # ``name`` is bytes, or None only if 'all' was an option.
        fm.condwrite(
            # pytype: disable=attribute-error
            opts.get(b'all') or name.startswith(b're:'),
            # pytype: enable=attribute-error
            b'name',
            b'  %-8s',
            entry.name,
        )

        fm.condwrite(
            ui.verbose,
            b'date',
            b' %s',
            fm.formatdate(entry.timestamp, b'%Y-%m-%d %H:%M %1%2'),
        )
        fm.write(b'command', b'  %s\n', entry.command)

        if opts.get(b"commits"):
            if fm.isplain():
                displayer = logcmdutil.changesetdisplayer(ui, repo, opts)
            else:
                displayer = logcmdutil.changesetformatter(
                    ui, repo, fm.nested(b'changesets'), diffopts=opts
                )
            for hash in entry.newhashes:
                try:
                    ctx = repo[hash]
                    displayer.show(ctx)
                except error.RepoLookupError as e:
                    fm.plain(b"%s\n\n" % pycompat.bytestr(e))
            displayer.close()

    fm.end()

    if entry is None:
        ui.status(_(b"no recorded locations\n"))
