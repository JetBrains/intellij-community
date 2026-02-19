from mercurial.i18n import _

from mercurial.node import (
    bin,
    hex,
    nullrev,
    sha1nodeconstants,
)
from mercurial import (
    ancestor,
    changelog as hgchangelog,
    dagop,
    encoding,
    error,
    manifest,
    pycompat,
)
from mercurial.interfaces import (
    repository,
    util as interfaceutil,
)
from mercurial.utils import stringutil
from . import (
    gitutil,
    index,
    manifest as gitmanifest,
)

pygit2 = gitutil.get_pygit2()


class baselog:  # revlog.revlog):
    """Common implementations between changelog and manifestlog."""

    def __init__(self, gr, db):
        self.gitrepo = gr
        self._db = db

    def __len__(self):
        return int(
            self._db.execute('SELECT COUNT(*) FROM changelog').fetchone()[0]
        )

    def rev(self, n):
        if n == sha1nodeconstants.nullid:
            return -1
        t = self._db.execute(
            'SELECT rev FROM changelog WHERE node = ?', (gitutil.togitnode(n),)
        ).fetchone()
        if t is None:
            raise error.LookupError(n, b'00changelog.i', _(b'no node %d'))
        return t[0]

    def node(self, r):
        if r == nullrev:
            return sha1nodeconstants.nullid
        t = self._db.execute(
            'SELECT node FROM changelog WHERE rev = ?', (r,)
        ).fetchone()
        if t is None:
            raise error.LookupError(r, b'00changelog.i', _(b'no node'))
        return bin(t[0])

    def hasnode(self, n):
        t = self._db.execute(
            'SELECT node FROM changelog WHERE node = ?',
            (pycompat.sysstr(n),),
        ).fetchone()
        return t is not None


class baselogindex:
    def __init__(self, log):
        self._log = log

    def has_node(self, n):
        return self._log.rev(n) != -1

    def __len__(self):
        return len(self._log)

    def __getitem__(self, idx):
        p1rev, p2rev = self._log.parentrevs(idx)
        # TODO: it's messy that the index leaks so far out of the
        # storage layer that we have to implement things like reading
        # this raw tuple, which exposes revlog internals.
        return (
            # Pretend offset is just the index, since we don't really care.
            idx,
            # Same with lengths
            idx,  # length
            idx,  # rawsize
            -1,  # delta base
            idx,  # linkrev TODO is this right?
            p1rev,
            p2rev,
            self._log.node(idx),
        )


# TODO: an interface for the changelog type?
class changelog(baselog):
    # TODO: this appears to be an enumerated type, and should probably
    # be part of the public changelog interface
    _copiesstorage = b'extra'

    def __contains__(self, rev):
        try:
            self.node(rev)
            return True
        except error.LookupError:
            return False

    def __iter__(self):
        return iter(range(len(self)))

    @property
    def filteredrevs(self):
        # TODO: we should probably add a refs/hg/ namespace for hidden
        # heads etc, but that's an idea for later.
        return set()

    @property
    def index(self):
        return baselogindex(self)

    @property
    def nodemap(self):
        r = {
            bin(v[0]): v[1]
            for v in self._db.execute('SELECT node, rev FROM changelog')
        }
        r[sha1nodeconstants.nullid] = nullrev
        return r

    def tip(self):
        t = self._db.execute(
            'SELECT node FROM changelog ORDER BY rev DESC LIMIT 1'
        ).fetchone()
        if t:
            return bin(t[0])
        return sha1nodeconstants.nullid

    def revs(self, start=0, stop=None):
        if stop is None:
            stop = self.tiprev()
        t = self._db.execute(
            'SELECT rev FROM changelog '
            'WHERE rev >= ? AND rev <= ? '
            'ORDER BY REV ASC',
            (start, stop),
        )
        return (int(r[0]) for r in t)

    def tiprev(self):
        t = self._db.execute(
            'SELECT rev FROM changelog ' 'ORDER BY REV DESC ' 'LIMIT 1'
        ).fetchone()

        if t is not None:
            return t[0]
        return -1

    def _partialmatch(self, id):
        if sha1nodeconstants.wdirhex.startswith(id):
            raise error.WdirUnsupported
        candidates = [
            bin(x[0])
            for x in self._db.execute(
                'SELECT node FROM changelog WHERE node LIKE ?',
                (pycompat.sysstr(id + b'%'),),
            )
        ]
        if sha1nodeconstants.nullhex.startswith(id):
            candidates.append(sha1nodeconstants.nullid)
        if len(candidates) > 1:
            raise error.AmbiguousPrefixLookupError(
                id, b'00changelog.i', _(b'ambiguous identifier')
            )
        if candidates:
            return candidates[0]
        return None

    def flags(self, rev):
        return 0

    def shortest(self, node, minlength=1):
        nodehex = hex(node)
        for attempt in range(minlength, len(nodehex) + 1):
            candidate = nodehex[:attempt]
            matches = int(
                self._db.execute(
                    'SELECT COUNT(*) FROM changelog WHERE node LIKE ?',
                    (pycompat.sysstr(candidate + b'%'),),
                ).fetchone()[0]
            )
            if matches == 1:
                return candidate
        return nodehex

    def headrevs(self, revs=None):
        realheads = [
            int(x[0])
            for x in self._db.execute(
                'SELECT rev FROM changelog '
                'INNER JOIN heads ON changelog.node = heads.node'
            )
        ]
        if revs:
            return sorted([r for r in revs if r in realheads])
        return sorted(realheads)

    def changelogrevision(self, nodeorrev):
        # Ensure we have a node id
        if isinstance(nodeorrev, int):
            n = self.node(nodeorrev)
        else:
            n = nodeorrev
        extra = {b'branch': b'default'}
        # handle looking up nullid
        if n == sha1nodeconstants.nullid:
            return hgchangelog._changelogrevision(
                extra=extra, manifest=sha1nodeconstants.nullid
            )
        hn = gitutil.togitnode(n)
        # We've got a real commit!
        files = [
            r[0]
            for r in self._db.execute(
                'SELECT filename FROM changedfiles '
                'WHERE node = ? and filenode != ?',
                (hn, gitutil.nullgit),
            )
        ]
        filesremoved = [
            r[0]
            for r in self._db.execute(
                'SELECT filename FROM changedfiles '
                'WHERE node = ? and filenode = ?',
                (hn, gitutil.nullgit),
            )
        ]
        c = self.gitrepo[hn]
        return hgchangelog._changelogrevision(
            manifest=n,  # pretend manifest the same as the commit node
            user=b'%s <%s>'
            % (c.author.name.encode('utf8'), c.author.email.encode('utf8')),
            date=(c.author.time, -c.author.offset * 60),
            files=files,
            # TODO filesadded in the index
            filesremoved=filesremoved,
            description=c.message.encode('utf8'),
            # TODO do we want to handle extra? how?
            extra=extra,
        )

    def ancestors(self, revs, stoprev=0, inclusive=False):
        revs = list(revs)
        tip = self.rev(self.tip())
        for r in revs:
            if r > tip:
                raise IndexError(b'Invalid rev %r' % r)
        return ancestor.lazyancestors(
            self.parentrevs, revs, stoprev=stoprev, inclusive=inclusive
        )

    # Cleanup opportunity: this is *identical* to the revlog.py version
    def descendants(self, revs):
        return dagop.descendantrevs(revs, self.revs, self.parentrevs)

    def incrementalmissingrevs(self, common=None):
        """Return an object that can be used to incrementally compute the
        revision numbers of the ancestors of arbitrary sets that are not
        ancestors of common. This is an ancestor.incrementalmissingancestors
        object.

        'common' is a list of revision numbers. If common is not supplied, uses
        nullrev.
        """
        if common is None:
            common = [nullrev]

        return ancestor.incrementalmissingancestors(self.parentrevs, common)

    def findmissingrevs(self, common=None, heads=None):
        """Return the revision numbers of the ancestors of heads that
        are not ancestors of common.

        More specifically, return a list of revision numbers corresponding to
        nodes N such that every N satisfies the following constraints:

          1. N is an ancestor of some node in 'heads'
          2. N is not an ancestor of any node in 'common'

        The list is sorted by revision number, meaning it is
        topologically sorted.

        'heads' and 'common' are both lists of revision numbers.  If heads is
        not supplied, uses all of the revlog's heads.  If common is not
        supplied, uses nullid."""
        if common is None:
            common = [nullrev]
        if heads is None:
            heads = self.headrevs()

        inc = self.incrementalmissingrevs(common=common)
        return inc.missingancestors(heads)

    def findmissing(self, common=None, heads=None):
        """Return the ancestors of heads that are not ancestors of common.

        More specifically, return a list of nodes N such that every N
        satisfies the following constraints:

          1. N is an ancestor of some node in 'heads'
          2. N is not an ancestor of any node in 'common'

        The list is sorted by revision number, meaning it is
        topologically sorted.

        'heads' and 'common' are both lists of node IDs.  If heads is
        not supplied, uses all of the revlog's heads.  If common is not
        supplied, uses nullid."""
        if common is None:
            common = [sha1nodeconstants.nullid]
        if heads is None:
            heads = [self.node(r) for r in self.headrevs()]

        common = [self.rev(n) for n in common]
        heads = [self.rev(n) for n in heads]

        inc = self.incrementalmissingrevs(common=common)
        return [self.node(r) for r in inc.missingancestors(heads)]

    def children(self, node):
        """find the children of a given node"""
        c = []
        p = self.rev(node)
        for r in self.revs(start=p + 1):
            prevs = [pr for pr in self.parentrevs(r) if pr != nullrev]
            if prevs:
                for pr in prevs:
                    if pr == p:
                        c.append(self.node(r))
            elif p == nullrev:
                c.append(self.node(r))
        return c

    def reachableroots(self, minroot, heads, roots, includepath=False):
        return dagop._reachablerootspure(
            self.parentrevs, minroot, roots, heads, includepath
        )

    # Cleanup opportunity: this is *identical* to the revlog.py version
    def isancestor(self, a, b):
        a, b = self.rev(a), self.rev(b)
        return self.isancestorrev(a, b)

    # Cleanup opportunity: this is *identical* to the revlog.py version
    def isancestorrev(self, a, b):
        if a == nullrev:
            return True
        elif a == b:
            return True
        elif a > b:
            return False
        return bool(self.reachableroots(a, [b], [a], includepath=False))

    def parentrevs(self, rev):
        n = self.node(rev)
        hn = gitutil.togitnode(n)
        if hn != gitutil.nullgit:
            c = self.gitrepo[hn]
        else:
            return nullrev, nullrev
        p1 = p2 = nullrev
        if c.parents:
            p1 = self.rev(c.parents[0].id.raw)
            if len(c.parents) > 2:
                raise error.Abort(b'TODO octopus merge handling')
            if len(c.parents) == 2:
                p2 = self.rev(c.parents[1].id.raw)
        return p1, p2

    # Private method is used at least by the tags code.
    _uncheckedparentrevs = parentrevs

    def commonancestorsheads(self, a, b):
        # TODO the revlog verson of this has a C path, so we probably
        # need to optimize this...
        a, b = self.rev(a), self.rev(b)
        return [
            self.node(n)
            for n in ancestor.commonancestorsheads(self.parentrevs, a, b)
        ]

    def branchinfo(self, rev):
        """Git doesn't do named branches, so just put everything on default."""
        return b'default', False

    def delayupdate(self, tr):
        # TODO: I think we can elide this because we're just dropping
        # an object in the git repo?
        pass

    def add(
        self,
        manifest,
        files,
        desc,
        transaction,
        p1,
        p2,
        user,
        date=None,
        extra=None,
        p1copies=None,
        p2copies=None,
        filesadded=None,
        filesremoved=None,
    ):
        parents = []
        hp1, hp2 = gitutil.togitnode(p1), gitutil.togitnode(p2)
        if p1 != sha1nodeconstants.nullid:
            parents.append(hp1)
        if p2 and p2 != sha1nodeconstants.nullid:
            parents.append(hp2)
        assert date is not None
        timestamp, tz = date
        sig = pygit2.Signature(
            encoding.unifromlocal(stringutil.person(user)),
            encoding.unifromlocal(stringutil.email(user)),
            int(timestamp),
            -int(tz // 60),
        )
        oid = self.gitrepo.create_commit(
            None, sig, sig, desc, gitutil.togitnode(manifest), parents
        )
        # Set up an internal reference to force the commit into the
        # changelog. Hypothetically, we could even use this refs/hg/
        # namespace to allow for anonymous heads on git repos, which
        # would be neat.
        self.gitrepo.references.create(
            'refs/hg/internal/latest-commit', oid, force=True
        )
        # Reindex now to pick up changes. We omit the progress
        # and log callbacks because this will be very quick.
        index._index_repo(self.gitrepo, self._db)
        return oid.raw


class manifestlog(baselog):
    nodeconstants = sha1nodeconstants

    def __getitem__(self, node):
        return self.get(b'', node)

    def get(self, relpath, node):
        if node == sha1nodeconstants.nullid:
            # TODO: this should almost certainly be a memgittreemanifestctx
            return manifest.memtreemanifestctx(self, relpath)
        commit = self.gitrepo[gitutil.togitnode(node)]
        t = commit.tree
        if relpath:
            parts = relpath.split(b'/')
            for p in parts:
                te = t[p]
                t = self.gitrepo[te.id]
        return gitmanifest.gittreemanifestctx(self.gitrepo, t)


@interfaceutil.implementer(repository.ifilestorage)
class filelog(baselog):
    def __init__(self, gr, db, path):
        super(filelog, self).__init__(gr, db)
        assert isinstance(path, bytes)
        self.path = path
        self.nullid = sha1nodeconstants.nullid

    def read(self, node):
        if node == sha1nodeconstants.nullid:
            return b''
        return self.gitrepo[gitutil.togitnode(node)].data

    def lookup(self, node):
        if len(node) not in (20, 40):
            node = int(node)
        if isinstance(node, int):
            assert False, b'todo revnums for nodes'
        if len(node) == 40:
            node = bin(node)
        hnode = gitutil.togitnode(node)
        if hnode in self.gitrepo:
            return node
        raise error.LookupError(self.path, node, _(b'no match found'))

    def cmp(self, node, text):
        """Returns True if text is different than content at `node`."""
        return self.read(node) != text

    def add(self, text, meta, transaction, link, p1=None, p2=None):
        assert not meta  # Should we even try to handle this?
        return self.gitrepo.create_blob(text).raw

    def __iter__(self):
        for clrev in self._db.execute(
            '''
SELECT rev FROM changelog
INNER JOIN changedfiles ON changelog.node = changedfiles.node
WHERE changedfiles.filename = ? AND changedfiles.filenode != ?
        ''',
            (pycompat.fsdecode(self.path), gitutil.nullgit),
        ):
            yield clrev[0]

    def linkrev(self, fr):
        return fr

    def rev(self, node):
        row = self._db.execute(
            '''
SELECT rev FROM changelog
INNER JOIN changedfiles ON changelog.node = changedfiles.node
WHERE changedfiles.filename = ? AND changedfiles.filenode = ?''',
            (pycompat.fsdecode(self.path), gitutil.togitnode(node)),
        ).fetchone()
        if row is None:
            raise error.LookupError(self.path, node, _(b'no such node'))
        return int(row[0])

    def node(self, rev):
        maybe = self._db.execute(
            '''SELECT filenode FROM changedfiles
INNER JOIN changelog ON changelog.node = changedfiles.node
WHERE changelog.rev = ? AND filename = ?
''',
            (rev, pycompat.fsdecode(self.path)),
        ).fetchone()
        if maybe is None:
            raise IndexError('gitlog %r out of range %d' % (self.path, rev))
        return bin(maybe[0])

    def parents(self, node):
        gn = gitutil.togitnode(node)
        gp = pycompat.fsdecode(self.path)
        ps = []
        for p in self._db.execute(
            '''SELECT p1filenode, p2filenode FROM changedfiles
WHERE filenode = ? AND filename = ?
''',
            (gn, gp),
        ).fetchone():
            if p is None:
                commit = self._db.execute(
                    "SELECT node FROM changedfiles "
                    "WHERE filenode = ? AND filename = ?",
                    (gn, gp),
                ).fetchone()[0]
                # This filelog is missing some data. Build the
                # filelog, then recurse (which will always find data).
                commit = commit.decode('ascii')
                index.fill_in_filelog(self.gitrepo, self._db, commit, gp, gn)
                return self.parents(node)
            else:
                ps.append(bin(p))
        return ps

    def renamed(self, node):
        # TODO: renames/copies
        return False
