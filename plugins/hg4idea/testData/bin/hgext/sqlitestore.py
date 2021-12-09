# sqlitestore.py - Storage backend that uses SQLite
#
# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""store repository data in SQLite (EXPERIMENTAL)

The sqlitestore extension enables the storage of repository data in SQLite.

This extension is HIGHLY EXPERIMENTAL. There are NO BACKWARDS COMPATIBILITY
GUARANTEES. This means that repositories created with this extension may
only be usable with the exact version of this extension/Mercurial that was
used. The extension attempts to enforce this in order to prevent repository
corruption.

In addition, several features are not yet supported or have known bugs:

* Only some data is stored in SQLite. Changeset, manifest, and other repository
  data is not yet stored in SQLite.
* Transactions are not robust. If the process is aborted at the right time
  during transaction close/rollback, the repository could be in an inconsistent
  state. This problem will diminish once all repository data is tracked by
  SQLite.
* Bundle repositories do not work (the ability to use e.g.
  `hg -R <bundle-file> log` to automatically overlay a bundle on top of the
  existing repository).
* Various other features don't work.

This extension should work for basic clone/pull, update, and commit workflows.
Some history rewriting operations may fail due to lack of support for bundle
repositories.

To use, activate the extension and set the ``storage.new-repo-backend`` config
option to ``sqlite`` to enable new repositories to use SQLite for storage.
"""

# To run the test suite with repos using SQLite by default, execute the
# following:
#
# HGREPOFEATURES="sqlitestore" run-tests.py \
#     --extra-config-opt extensions.sqlitestore= \
#     --extra-config-opt storage.new-repo-backend=sqlite

from __future__ import absolute_import

import sqlite3
import struct
import threading
import zlib

from mercurial.i18n import _
from mercurial.node import (
    nullrev,
    sha1nodeconstants,
    short,
)
from mercurial.thirdparty import attr
from mercurial import (
    ancestor,
    dagop,
    encoding,
    error,
    extensions,
    localrepo,
    mdiff,
    pycompat,
    registrar,
    requirements,
    util,
    verify,
)
from mercurial.interfaces import (
    repository,
    util as interfaceutil,
)
from mercurial.utils import (
    hashutil,
    storageutil,
)

try:
    from mercurial import zstd

    zstd.__version__
except ImportError:
    zstd = None

configtable = {}
configitem = registrar.configitem(configtable)

# experimental config: storage.sqlite.compression
configitem(
    b'storage',
    b'sqlite.compression',
    default=b'zstd' if zstd else b'zlib',
    experimental=True,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

REQUIREMENT = b'exp-sqlite-001'
REQUIREMENT_ZSTD = b'exp-sqlite-comp-001=zstd'
REQUIREMENT_ZLIB = b'exp-sqlite-comp-001=zlib'
REQUIREMENT_NONE = b'exp-sqlite-comp-001=none'
REQUIREMENT_SHALLOW_FILES = b'exp-sqlite-shallow-files'

CURRENT_SCHEMA_VERSION = 1

COMPRESSION_NONE = 1
COMPRESSION_ZSTD = 2
COMPRESSION_ZLIB = 3

FLAG_CENSORED = 1
FLAG_MISSING_P1 = 2
FLAG_MISSING_P2 = 4

CREATE_SCHEMA = [
    # Deltas are stored as content-indexed blobs.
    # compression column holds COMPRESSION_* constant for how the
    # delta is encoded.
    'CREATE TABLE delta ('
    '    id INTEGER PRIMARY KEY, '
    '    compression INTEGER NOT NULL, '
    '    hash BLOB UNIQUE ON CONFLICT ABORT, '
    '    delta BLOB NOT NULL '
    ')',
    # Tracked paths are denormalized to integers to avoid redundant
    # storage of the path name.
    'CREATE TABLE filepath ('
    '    id INTEGER PRIMARY KEY, '
    '    path BLOB NOT NULL '
    ')',
    'CREATE UNIQUE INDEX filepath_path ON filepath (path)',
    # We have a single table for all file revision data.
    # Each file revision is uniquely described by a (path, rev) and
    # (path, node).
    #
    # Revision data is stored as a pointer to the delta producing this
    # revision and the file revision whose delta should be applied before
    # that one. One can reconstruct the delta chain by recursively following
    # the delta base revision pointers until one encounters NULL.
    #
    # flags column holds bitwise integer flags controlling storage options.
    # These flags are defined by the FLAG_* constants.
    'CREATE TABLE fileindex ('
    '    id INTEGER PRIMARY KEY, '
    '    pathid INTEGER REFERENCES filepath(id), '
    '    revnum INTEGER NOT NULL, '
    '    p1rev INTEGER NOT NULL, '
    '    p2rev INTEGER NOT NULL, '
    '    linkrev INTEGER NOT NULL, '
    '    flags INTEGER NOT NULL, '
    '    deltaid INTEGER REFERENCES delta(id), '
    '    deltabaseid INTEGER REFERENCES fileindex(id), '
    '    node BLOB NOT NULL '
    ')',
    'CREATE UNIQUE INDEX fileindex_pathrevnum '
    '    ON fileindex (pathid, revnum)',
    'CREATE UNIQUE INDEX fileindex_pathnode ON fileindex (pathid, node)',
    # Provide a view over all file data for convenience.
    'CREATE VIEW filedata AS '
    'SELECT '
    '    fileindex.id AS id, '
    '    filepath.id AS pathid, '
    '    filepath.path AS path, '
    '    fileindex.revnum AS revnum, '
    '    fileindex.node AS node, '
    '    fileindex.p1rev AS p1rev, '
    '    fileindex.p2rev AS p2rev, '
    '    fileindex.linkrev AS linkrev, '
    '    fileindex.flags AS flags, '
    '    fileindex.deltaid AS deltaid, '
    '    fileindex.deltabaseid AS deltabaseid '
    'FROM filepath, fileindex '
    'WHERE fileindex.pathid=filepath.id',
    'PRAGMA user_version=%d' % CURRENT_SCHEMA_VERSION,
]


def resolvedeltachain(db, pathid, node, revisioncache, stoprids, zstddctx=None):
    """Resolve a delta chain for a file node."""

    # TODO the "not in ({stops})" here is possibly slowing down the query
    # because it needs to perform the lookup on every recursive invocation.
    # This could possibly be faster if we created a temporary query with
    # baseid "poisoned" to null and limited the recursive filter to
    # "is not null".
    res = db.execute(
        'WITH RECURSIVE '
        '    deltachain(deltaid, baseid) AS ('
        '        SELECT deltaid, deltabaseid FROM fileindex '
        '            WHERE pathid=? AND node=? '
        '        UNION ALL '
        '        SELECT fileindex.deltaid, deltabaseid '
        '            FROM fileindex, deltachain '
        '            WHERE '
        '                fileindex.id=deltachain.baseid '
        '                AND deltachain.baseid IS NOT NULL '
        '                AND fileindex.id NOT IN ({stops}) '
        '    ) '
        'SELECT deltachain.baseid, compression, delta '
        'FROM deltachain, delta '
        'WHERE delta.id=deltachain.deltaid'.format(
            stops=','.join(['?'] * len(stoprids))
        ),
        tuple([pathid, node] + list(stoprids.keys())),
    )

    deltas = []
    lastdeltabaseid = None

    for deltabaseid, compression, delta in res:
        lastdeltabaseid = deltabaseid

        if compression == COMPRESSION_ZSTD:
            delta = zstddctx.decompress(delta)
        elif compression == COMPRESSION_NONE:
            delta = delta
        elif compression == COMPRESSION_ZLIB:
            delta = zlib.decompress(delta)
        else:
            raise SQLiteStoreError(
                b'unhandled compression type: %d' % compression
            )

        deltas.append(delta)

    if lastdeltabaseid in stoprids:
        basetext = revisioncache[stoprids[lastdeltabaseid]]
    else:
        basetext = deltas.pop()

    deltas.reverse()
    fulltext = mdiff.patches(basetext, deltas)

    # SQLite returns buffer instances for blob columns on Python 2. This
    # type can propagate through the delta application layer. Because
    # downstream callers assume revisions are bytes, cast as needed.
    if not isinstance(fulltext, bytes):
        fulltext = bytes(delta)

    return fulltext


def insertdelta(db, compression, hash, delta):
    try:
        return db.execute(
            'INSERT INTO delta (compression, hash, delta) VALUES (?, ?, ?)',
            (compression, hash, delta),
        ).lastrowid
    except sqlite3.IntegrityError:
        return db.execute(
            'SELECT id FROM delta WHERE hash=?', (hash,)
        ).fetchone()[0]


class SQLiteStoreError(error.StorageError):
    pass


@attr.s
class revisionentry(object):
    rid = attr.ib()
    rev = attr.ib()
    node = attr.ib()
    p1rev = attr.ib()
    p2rev = attr.ib()
    p1node = attr.ib()
    p2node = attr.ib()
    linkrev = attr.ib()
    flags = attr.ib()


@interfaceutil.implementer(repository.irevisiondelta)
@attr.s(slots=True)
class sqliterevisiondelta(object):
    node = attr.ib()
    p1node = attr.ib()
    p2node = attr.ib()
    basenode = attr.ib()
    flags = attr.ib()
    baserevisionsize = attr.ib()
    revision = attr.ib()
    delta = attr.ib()
    sidedata = attr.ib()
    protocol_flags = attr.ib()
    linknode = attr.ib(default=None)


@interfaceutil.implementer(repository.iverifyproblem)
@attr.s(frozen=True)
class sqliteproblem(object):
    warning = attr.ib(default=None)
    error = attr.ib(default=None)
    node = attr.ib(default=None)


@interfaceutil.implementer(repository.ifilestorage)
class sqlitefilestore(object):
    """Implements storage for an individual tracked path."""

    def __init__(self, db, path, compression):
        self.nullid = sha1nodeconstants.nullid
        self._db = db
        self._path = path

        self._pathid = None

        # revnum -> node
        self._revtonode = {}
        # node -> revnum
        self._nodetorev = {}
        # node -> data structure
        self._revisions = {}

        self._revisioncache = util.lrucachedict(10)

        self._compengine = compression

        if compression == b'zstd':
            self._cctx = zstd.ZstdCompressor(level=3)
            self._dctx = zstd.ZstdDecompressor()
        else:
            self._cctx = None
            self._dctx = None

        self._refreshindex()

    def _refreshindex(self):
        self._revtonode = {}
        self._nodetorev = {}
        self._revisions = {}

        res = list(
            self._db.execute(
                'SELECT id FROM filepath WHERE path=?', (self._path,)
            )
        )

        if not res:
            self._pathid = None
            return

        self._pathid = res[0][0]

        res = self._db.execute(
            'SELECT id, revnum, node, p1rev, p2rev, linkrev, flags '
            'FROM fileindex '
            'WHERE pathid=? '
            'ORDER BY revnum ASC',
            (self._pathid,),
        )

        for i, row in enumerate(res):
            rid, rev, node, p1rev, p2rev, linkrev, flags = row

            if i != rev:
                raise SQLiteStoreError(
                    _(b'sqlite database has inconsistent revision numbers')
                )

            if p1rev == nullrev:
                p1node = sha1nodeconstants.nullid
            else:
                p1node = self._revtonode[p1rev]

            if p2rev == nullrev:
                p2node = sha1nodeconstants.nullid
            else:
                p2node = self._revtonode[p2rev]

            entry = revisionentry(
                rid=rid,
                rev=rev,
                node=node,
                p1rev=p1rev,
                p2rev=p2rev,
                p1node=p1node,
                p2node=p2node,
                linkrev=linkrev,
                flags=flags,
            )

            self._revtonode[rev] = node
            self._nodetorev[node] = rev
            self._revisions[node] = entry

    # Start of ifileindex interface.

    def __len__(self):
        return len(self._revisions)

    def __iter__(self):
        return iter(pycompat.xrange(len(self._revisions)))

    def hasnode(self, node):
        if node == sha1nodeconstants.nullid:
            return False

        return node in self._nodetorev

    def revs(self, start=0, stop=None):
        return storageutil.iterrevs(
            len(self._revisions), start=start, stop=stop
        )

    def parents(self, node):
        if node == sha1nodeconstants.nullid:
            return sha1nodeconstants.nullid, sha1nodeconstants.nullid

        if node not in self._revisions:
            raise error.LookupError(node, self._path, _(b'no node'))

        entry = self._revisions[node]
        return entry.p1node, entry.p2node

    def parentrevs(self, rev):
        if rev == nullrev:
            return nullrev, nullrev

        if rev not in self._revtonode:
            raise IndexError(rev)

        entry = self._revisions[self._revtonode[rev]]
        return entry.p1rev, entry.p2rev

    def rev(self, node):
        if node == sha1nodeconstants.nullid:
            return nullrev

        if node not in self._nodetorev:
            raise error.LookupError(node, self._path, _(b'no node'))

        return self._nodetorev[node]

    def node(self, rev):
        if rev == nullrev:
            return sha1nodeconstants.nullid

        if rev not in self._revtonode:
            raise IndexError(rev)

        return self._revtonode[rev]

    def lookup(self, node):
        return storageutil.fileidlookup(self, node, self._path)

    def linkrev(self, rev):
        if rev == nullrev:
            return nullrev

        if rev not in self._revtonode:
            raise IndexError(rev)

        entry = self._revisions[self._revtonode[rev]]
        return entry.linkrev

    def iscensored(self, rev):
        if rev == nullrev:
            return False

        if rev not in self._revtonode:
            raise IndexError(rev)

        return self._revisions[self._revtonode[rev]].flags & FLAG_CENSORED

    def commonancestorsheads(self, node1, node2):
        rev1 = self.rev(node1)
        rev2 = self.rev(node2)

        ancestors = ancestor.commonancestorsheads(self.parentrevs, rev1, rev2)
        return pycompat.maplist(self.node, ancestors)

    def descendants(self, revs):
        # TODO we could implement this using a recursive SQL query, which
        # might be faster.
        return dagop.descendantrevs(revs, self.revs, self.parentrevs)

    def heads(self, start=None, stop=None):
        if start is None and stop is None:
            if not len(self):
                return [sha1nodeconstants.nullid]

        startrev = self.rev(start) if start is not None else nullrev
        stoprevs = {self.rev(n) for n in stop or []}

        revs = dagop.headrevssubset(
            self.revs, self.parentrevs, startrev=startrev, stoprevs=stoprevs
        )

        return [self.node(rev) for rev in revs]

    def children(self, node):
        rev = self.rev(node)

        res = self._db.execute(
            'SELECT'
            '  node '
            '  FROM filedata '
            '  WHERE path=? AND (p1rev=? OR p2rev=?) '
            '  ORDER BY revnum ASC',
            (self._path, rev, rev),
        )

        return [row[0] for row in res]

    # End of ifileindex interface.

    # Start of ifiledata interface.

    def size(self, rev):
        if rev == nullrev:
            return 0

        if rev not in self._revtonode:
            raise IndexError(rev)

        node = self._revtonode[rev]

        if self.renamed(node):
            return len(self.read(node))

        return len(self.revision(node))

    def revision(self, node, raw=False, _verifyhash=True):
        if node in (sha1nodeconstants.nullid, nullrev):
            return b''

        if isinstance(node, int):
            node = self.node(node)

        if node not in self._nodetorev:
            raise error.LookupError(node, self._path, _(b'no node'))

        if node in self._revisioncache:
            return self._revisioncache[node]

        # Because we have a fulltext revision cache, we are able to
        # short-circuit delta chain traversal and decompression as soon as
        # we encounter a revision in the cache.

        stoprids = {self._revisions[n].rid: n for n in self._revisioncache}

        if not stoprids:
            stoprids[-1] = None

        fulltext = resolvedeltachain(
            self._db,
            self._pathid,
            node,
            self._revisioncache,
            stoprids,
            zstddctx=self._dctx,
        )

        # Don't verify hashes if parent nodes were rewritten, as the hash
        # wouldn't verify.
        if self._revisions[node].flags & (FLAG_MISSING_P1 | FLAG_MISSING_P2):
            _verifyhash = False

        if _verifyhash:
            self._checkhash(fulltext, node)
            self._revisioncache[node] = fulltext

        return fulltext

    def rawdata(self, *args, **kwargs):
        return self.revision(*args, **kwargs)

    def read(self, node):
        return storageutil.filtermetadata(self.revision(node))

    def renamed(self, node):
        return storageutil.filerevisioncopied(self, node)

    def cmp(self, node, fulltext):
        return not storageutil.filedataequivalent(self, node, fulltext)

    def emitrevisions(
        self,
        nodes,
        nodesorder=None,
        revisiondata=False,
        assumehaveparentrevisions=False,
        deltamode=repository.CG_DELTAMODE_STD,
        sidedata_helpers=None,
    ):
        if nodesorder not in (b'nodes', b'storage', b'linear', None):
            raise error.ProgrammingError(
                b'unhandled value for nodesorder: %s' % nodesorder
            )

        nodes = [n for n in nodes if n != sha1nodeconstants.nullid]

        if not nodes:
            return

        # TODO perform in a single query.
        res = self._db.execute(
            'SELECT revnum, deltaid FROM fileindex '
            'WHERE pathid=? '
            '    AND node in (%s)' % (','.join(['?'] * len(nodes))),
            tuple([self._pathid] + nodes),
        )

        deltabases = {}

        for rev, deltaid in res:
            res = self._db.execute(
                'SELECT revnum from fileindex WHERE pathid=? AND deltaid=?',
                (self._pathid, deltaid),
            )
            deltabases[rev] = res.fetchone()[0]

        # TODO define revdifffn so we can use delta from storage.
        for delta in storageutil.emitrevisions(
            self,
            nodes,
            nodesorder,
            sqliterevisiondelta,
            deltaparentfn=deltabases.__getitem__,
            revisiondata=revisiondata,
            assumehaveparentrevisions=assumehaveparentrevisions,
            deltamode=deltamode,
            sidedata_helpers=sidedata_helpers,
        ):

            yield delta

    # End of ifiledata interface.

    # Start of ifilemutation interface.

    def add(self, filedata, meta, transaction, linkrev, p1, p2):
        if meta or filedata.startswith(b'\x01\n'):
            filedata = storageutil.packmeta(meta, filedata)

        rev = self.addrevision(filedata, transaction, linkrev, p1, p2)
        return self.node(rev)

    def addrevision(
        self,
        revisiondata,
        transaction,
        linkrev,
        p1,
        p2,
        node=None,
        flags=0,
        cachedelta=None,
    ):
        if flags:
            raise SQLiteStoreError(_(b'flags not supported on revisions'))

        validatehash = node is not None
        node = node or storageutil.hashrevisionsha1(revisiondata, p1, p2)

        if validatehash:
            self._checkhash(revisiondata, node, p1, p2)

        rev = self._nodetorev.get(node)
        if rev is not None:
            return rev

        rev = self._addrawrevision(
            node, revisiondata, transaction, linkrev, p1, p2
        )

        self._revisioncache[node] = revisiondata
        return rev

    def addgroup(
        self,
        deltas,
        linkmapper,
        transaction,
        addrevisioncb=None,
        duplicaterevisioncb=None,
        maybemissingparents=False,
    ):
        empty = True

        for (
            node,
            p1,
            p2,
            linknode,
            deltabase,
            delta,
            wireflags,
            sidedata,
        ) in deltas:
            storeflags = 0

            if wireflags & repository.REVISION_FLAG_CENSORED:
                storeflags |= FLAG_CENSORED

            if wireflags & ~repository.REVISION_FLAG_CENSORED:
                raise SQLiteStoreError(b'unhandled revision flag')

            if maybemissingparents:
                if p1 != sha1nodeconstants.nullid and not self.hasnode(p1):
                    p1 = sha1nodeconstants.nullid
                    storeflags |= FLAG_MISSING_P1

                if p2 != sha1nodeconstants.nullid and not self.hasnode(p2):
                    p2 = sha1nodeconstants.nullid
                    storeflags |= FLAG_MISSING_P2

            baserev = self.rev(deltabase)

            # If base is censored, delta must be full replacement in a single
            # patch operation.
            if baserev != nullrev and self.iscensored(baserev):
                hlen = struct.calcsize(b'>lll')
                oldlen = len(self.rawdata(deltabase, _verifyhash=False))
                newlen = len(delta) - hlen

                if delta[:hlen] != mdiff.replacediffheader(oldlen, newlen):
                    raise error.CensoredBaseError(self._path, deltabase)

            if not (storeflags & FLAG_CENSORED) and storageutil.deltaiscensored(
                delta, baserev, lambda x: len(self.rawdata(x))
            ):
                storeflags |= FLAG_CENSORED

            linkrev = linkmapper(linknode)

            if node in self._revisions:
                # Possibly reset parents to make them proper.
                entry = self._revisions[node]

                if (
                    entry.flags & FLAG_MISSING_P1
                    and p1 != sha1nodeconstants.nullid
                ):
                    entry.p1node = p1
                    entry.p1rev = self._nodetorev[p1]
                    entry.flags &= ~FLAG_MISSING_P1

                    self._db.execute(
                        'UPDATE fileindex SET p1rev=?, flags=? WHERE id=?',
                        (self._nodetorev[p1], entry.flags, entry.rid),
                    )

                if (
                    entry.flags & FLAG_MISSING_P2
                    and p2 != sha1nodeconstants.nullid
                ):
                    entry.p2node = p2
                    entry.p2rev = self._nodetorev[p2]
                    entry.flags &= ~FLAG_MISSING_P2

                    self._db.execute(
                        'UPDATE fileindex SET p2rev=?, flags=? WHERE id=?',
                        (self._nodetorev[p1], entry.flags, entry.rid),
                    )

                if duplicaterevisioncb:
                    duplicaterevisioncb(self, self.rev(node))
                empty = False
                continue

            if deltabase == sha1nodeconstants.nullid:
                text = mdiff.patch(b'', delta)
                storedelta = None
            else:
                text = None
                storedelta = (deltabase, delta)

            rev = self._addrawrevision(
                node,
                text,
                transaction,
                linkrev,
                p1,
                p2,
                storedelta=storedelta,
                flags=storeflags,
            )

            if addrevisioncb:
                addrevisioncb(self, rev)
            empty = False

        return not empty

    def censorrevision(self, tr, censornode, tombstone=b''):
        tombstone = storageutil.packmeta({b'censored': tombstone}, b'')

        # This restriction is cargo culted from revlogs and makes no sense for
        # SQLite, since columns can be resized at will.
        if len(tombstone) > len(self.rawdata(censornode)):
            raise error.Abort(
                _(b'censor tombstone must be no longer than censored data')
            )

        # We need to replace the censored revision's data with the tombstone.
        # But replacing that data will have implications for delta chains that
        # reference it.
        #
        # While "better," more complex strategies are possible, we do something
        # simple: we find delta chain children of the censored revision and we
        # replace those incremental deltas with fulltexts of their corresponding
        # revision. Then we delete the now-unreferenced delta and original
        # revision and insert a replacement.

        # Find the delta to be censored.
        censoreddeltaid = self._db.execute(
            'SELECT deltaid FROM fileindex WHERE id=?',
            (self._revisions[censornode].rid,),
        ).fetchone()[0]

        # Find all its delta chain children.
        # TODO once we support storing deltas for !files, we'll need to look
        # for those delta chains too.
        rows = list(
            self._db.execute(
                'SELECT id, pathid, node FROM fileindex '
                'WHERE deltabaseid=? OR deltaid=?',
                (censoreddeltaid, censoreddeltaid),
            )
        )

        for row in rows:
            rid, pathid, node = row

            fulltext = resolvedeltachain(
                self._db, pathid, node, {}, {-1: None}, zstddctx=self._dctx
            )

            deltahash = hashutil.sha1(fulltext).digest()

            if self._compengine == b'zstd':
                deltablob = self._cctx.compress(fulltext)
                compression = COMPRESSION_ZSTD
            elif self._compengine == b'zlib':
                deltablob = zlib.compress(fulltext)
                compression = COMPRESSION_ZLIB
            elif self._compengine == b'none':
                deltablob = fulltext
                compression = COMPRESSION_NONE
            else:
                raise error.ProgrammingError(
                    b'unhandled compression engine: %s' % self._compengine
                )

            if len(deltablob) >= len(fulltext):
                deltablob = fulltext
                compression = COMPRESSION_NONE

            deltaid = insertdelta(self._db, compression, deltahash, deltablob)

            self._db.execute(
                'UPDATE fileindex SET deltaid=?, deltabaseid=NULL '
                'WHERE id=?',
                (deltaid, rid),
            )

        # Now create the tombstone delta and replace the delta on the censored
        # node.
        deltahash = hashutil.sha1(tombstone).digest()
        tombstonedeltaid = insertdelta(
            self._db, COMPRESSION_NONE, deltahash, tombstone
        )

        flags = self._revisions[censornode].flags
        flags |= FLAG_CENSORED

        self._db.execute(
            'UPDATE fileindex SET flags=?, deltaid=?, deltabaseid=NULL '
            'WHERE pathid=? AND node=?',
            (flags, tombstonedeltaid, self._pathid, censornode),
        )

        self._db.execute('DELETE FROM delta WHERE id=?', (censoreddeltaid,))

        self._refreshindex()
        self._revisioncache.clear()

    def getstrippoint(self, minlink):
        return storageutil.resolvestripinfo(
            minlink,
            len(self) - 1,
            [self.rev(n) for n in self.heads()],
            self.linkrev,
            self.parentrevs,
        )

    def strip(self, minlink, transaction):
        if not len(self):
            return

        rev, _ignored = self.getstrippoint(minlink)

        if rev == len(self):
            return

        for rev in self.revs(rev):
            self._db.execute(
                'DELETE FROM fileindex WHERE pathid=? AND node=?',
                (self._pathid, self.node(rev)),
            )

        # TODO how should we garbage collect data in delta table?

        self._refreshindex()

    # End of ifilemutation interface.

    # Start of ifilestorage interface.

    def files(self):
        return []

    def sidedata(self, nodeorrev, _df=None):
        # Not supported for now
        return {}

    def storageinfo(
        self,
        exclusivefiles=False,
        sharedfiles=False,
        revisionscount=False,
        trackedsize=False,
        storedsize=False,
    ):
        d = {}

        if exclusivefiles:
            d[b'exclusivefiles'] = []

        if sharedfiles:
            # TODO list sqlite file(s) here.
            d[b'sharedfiles'] = []

        if revisionscount:
            d[b'revisionscount'] = len(self)

        if trackedsize:
            d[b'trackedsize'] = sum(
                len(self.revision(node)) for node in self._nodetorev
            )

        if storedsize:
            # TODO implement this?
            d[b'storedsize'] = None

        return d

    def verifyintegrity(self, state):
        state[b'skipread'] = set()

        for rev in self:
            node = self.node(rev)

            try:
                self.revision(node)
            except Exception as e:
                yield sqliteproblem(
                    error=_(b'unpacking %s: %s') % (short(node), e), node=node
                )

                state[b'skipread'].add(node)

    # End of ifilestorage interface.

    def _checkhash(self, fulltext, node, p1=None, p2=None):
        if p1 is None and p2 is None:
            p1, p2 = self.parents(node)

        if node == storageutil.hashrevisionsha1(fulltext, p1, p2):
            return

        try:
            del self._revisioncache[node]
        except KeyError:
            pass

        if storageutil.iscensoredtext(fulltext):
            raise error.CensoredNodeError(self._path, node, fulltext)

        raise SQLiteStoreError(_(b'integrity check failed on %s') % self._path)

    def _addrawrevision(
        self,
        node,
        revisiondata,
        transaction,
        linkrev,
        p1,
        p2,
        storedelta=None,
        flags=0,
    ):
        if self._pathid is None:
            res = self._db.execute(
                'INSERT INTO filepath (path) VALUES (?)', (self._path,)
            )
            self._pathid = res.lastrowid

        # For simplicity, always store a delta against p1.
        # TODO we need a lot more logic here to make behavior reasonable.

        if storedelta:
            deltabase, delta = storedelta

            if isinstance(deltabase, int):
                deltabase = self.node(deltabase)

        else:
            assert revisiondata is not None
            deltabase = p1

            if deltabase == sha1nodeconstants.nullid:
                delta = revisiondata
            else:
                delta = mdiff.textdiff(
                    self.revision(self.rev(deltabase)), revisiondata
                )

        # File index stores a pointer to its delta and the parent delta.
        # The parent delta is stored via a pointer to the fileindex PK.
        if deltabase == sha1nodeconstants.nullid:
            baseid = None
        else:
            baseid = self._revisions[deltabase].rid

        # Deltas are stored with a hash of their content. This allows
        # us to de-duplicate. The table is configured to ignore conflicts
        # and it is faster to just insert and silently noop than to look
        # first.
        deltahash = hashutil.sha1(delta).digest()

        if self._compengine == b'zstd':
            deltablob = self._cctx.compress(delta)
            compression = COMPRESSION_ZSTD
        elif self._compengine == b'zlib':
            deltablob = zlib.compress(delta)
            compression = COMPRESSION_ZLIB
        elif self._compengine == b'none':
            deltablob = delta
            compression = COMPRESSION_NONE
        else:
            raise error.ProgrammingError(
                b'unhandled compression engine: %s' % self._compengine
            )

        # Don't store compressed data if it isn't practical.
        if len(deltablob) >= len(delta):
            deltablob = delta
            compression = COMPRESSION_NONE

        deltaid = insertdelta(self._db, compression, deltahash, deltablob)

        rev = len(self)

        if p1 == sha1nodeconstants.nullid:
            p1rev = nullrev
        else:
            p1rev = self._nodetorev[p1]

        if p2 == sha1nodeconstants.nullid:
            p2rev = nullrev
        else:
            p2rev = self._nodetorev[p2]

        rid = self._db.execute(
            'INSERT INTO fileindex ('
            '    pathid, revnum, node, p1rev, p2rev, linkrev, flags, '
            '    deltaid, deltabaseid) '
            '    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
            (
                self._pathid,
                rev,
                node,
                p1rev,
                p2rev,
                linkrev,
                flags,
                deltaid,
                baseid,
            ),
        ).lastrowid

        entry = revisionentry(
            rid=rid,
            rev=rev,
            node=node,
            p1rev=p1rev,
            p2rev=p2rev,
            p1node=p1,
            p2node=p2,
            linkrev=linkrev,
            flags=flags,
        )

        self._nodetorev[node] = rev
        self._revtonode[rev] = node
        self._revisions[node] = entry

        return rev


class sqliterepository(localrepo.localrepository):
    def cancopy(self):
        return False

    def transaction(self, *args, **kwargs):
        current = self.currenttransaction()

        tr = super(sqliterepository, self).transaction(*args, **kwargs)

        if current:
            return tr

        self._dbconn.execute('BEGIN TRANSACTION')

        def committransaction(_):
            self._dbconn.commit()

        tr.addfinalize(b'sqlitestore', committransaction)

        return tr

    @property
    def _dbconn(self):
        # SQLite connections can only be used on the thread that created
        # them. In most cases, this "just works." However, hgweb uses
        # multiple threads.
        tid = threading.current_thread().ident

        if self._db:
            if self._db[0] == tid:
                return self._db[1]

        db = makedb(self.svfs.join(b'db.sqlite'))
        self._db = (tid, db)

        return db


def makedb(path):
    """Construct a database handle for a database at path."""

    db = sqlite3.connect(encoding.strfromlocal(path))
    db.text_factory = bytes

    res = db.execute('PRAGMA user_version').fetchone()[0]

    # New database.
    if res == 0:
        for statement in CREATE_SCHEMA:
            db.execute(statement)

        db.commit()

    elif res == CURRENT_SCHEMA_VERSION:
        pass

    else:
        raise error.Abort(_(b'sqlite database has unrecognized version'))

    db.execute('PRAGMA journal_mode=WAL')

    return db


def featuresetup(ui, supported):
    supported.add(REQUIREMENT)

    if zstd:
        supported.add(REQUIREMENT_ZSTD)

    supported.add(REQUIREMENT_ZLIB)
    supported.add(REQUIREMENT_NONE)
    supported.add(REQUIREMENT_SHALLOW_FILES)
    supported.add(requirements.NARROW_REQUIREMENT)


def newreporequirements(orig, ui, createopts):
    if createopts[b'backend'] != b'sqlite':
        return orig(ui, createopts)

    # This restriction can be lifted once we have more confidence.
    if b'sharedrepo' in createopts:
        raise error.Abort(
            _(b'shared repositories not supported with SQLite store')
        )

    # This filtering is out of an abundance of caution: we want to ensure
    # we honor creation options and we do that by annotating exactly the
    # creation options we recognize.
    known = {
        b'narrowfiles',
        b'backend',
        b'shallowfilestore',
    }

    unsupported = set(createopts) - known
    if unsupported:
        raise error.Abort(
            _(b'SQLite store does not support repo creation option: %s')
            % b', '.join(sorted(unsupported))
        )

    # Since we're a hybrid store that still relies on revlogs, we fall back
    # to using the revlogv1 backend's storage requirements then adding our
    # own requirement.
    createopts[b'backend'] = b'revlogv1'
    requirements = orig(ui, createopts)
    requirements.add(REQUIREMENT)

    compression = ui.config(b'storage', b'sqlite.compression')

    if compression == b'zstd' and not zstd:
        raise error.Abort(
            _(
                b'storage.sqlite.compression set to "zstd" but '
                b'zstandard compression not available to this '
                b'Mercurial install'
            )
        )

    if compression == b'zstd':
        requirements.add(REQUIREMENT_ZSTD)
    elif compression == b'zlib':
        requirements.add(REQUIREMENT_ZLIB)
    elif compression == b'none':
        requirements.add(REQUIREMENT_NONE)
    else:
        raise error.Abort(
            _(
                b'unknown compression engine defined in '
                b'storage.sqlite.compression: %s'
            )
            % compression
        )

    if createopts.get(b'shallowfilestore'):
        requirements.add(REQUIREMENT_SHALLOW_FILES)

    return requirements


@interfaceutil.implementer(repository.ilocalrepositoryfilestorage)
class sqlitefilestorage(object):
    """Repository file storage backed by SQLite."""

    def file(self, path):
        if path[0] == b'/':
            path = path[1:]

        if REQUIREMENT_ZSTD in self.requirements:
            compression = b'zstd'
        elif REQUIREMENT_ZLIB in self.requirements:
            compression = b'zlib'
        elif REQUIREMENT_NONE in self.requirements:
            compression = b'none'
        else:
            raise error.Abort(
                _(
                    b'unable to determine what compression engine '
                    b'to use for SQLite storage'
                )
            )

        return sqlitefilestore(self._dbconn, path, compression)


def makefilestorage(orig, requirements, features, **kwargs):
    """Produce a type conforming to ``ilocalrepositoryfilestorage``."""
    if REQUIREMENT in requirements:
        if REQUIREMENT_SHALLOW_FILES in requirements:
            features.add(repository.REPO_FEATURE_SHALLOW_FILE_STORAGE)

        return sqlitefilestorage
    else:
        return orig(requirements=requirements, features=features, **kwargs)


def makemain(orig, ui, requirements, **kwargs):
    if REQUIREMENT in requirements:
        if REQUIREMENT_ZSTD in requirements and not zstd:
            raise error.Abort(
                _(
                    b'repository uses zstandard compression, which '
                    b'is not available to this Mercurial install'
                )
            )

        return sqliterepository

    return orig(requirements=requirements, **kwargs)


def verifierinit(orig, self, *args, **kwargs):
    orig(self, *args, **kwargs)

    # We don't care that files in the store don't align with what is
    # advertised. So suppress these warnings.
    self.warnorphanstorefiles = False


def extsetup(ui):
    localrepo.featuresetupfuncs.add(featuresetup)
    extensions.wrapfunction(
        localrepo, b'newreporequirements', newreporequirements
    )
    extensions.wrapfunction(localrepo, b'makefilestorage', makefilestorage)
    extensions.wrapfunction(localrepo, b'makemain', makemain)
    extensions.wrapfunction(verify.verifier, b'__init__', verifierinit)


def reposetup(ui, repo):
    if isinstance(repo, sqliterepository):
        repo._db = None

    # TODO check for bundlerepository?
