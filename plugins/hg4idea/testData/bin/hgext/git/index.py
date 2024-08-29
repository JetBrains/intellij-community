import collections
import os
import sqlite3

from mercurial.i18n import _
from mercurial.node import sha1nodeconstants

from mercurial import (
    encoding,
    error,
    pycompat,
)

from . import gitutil


pygit2 = gitutil.get_pygit2()

_CURRENT_SCHEMA_VERSION = 1
_SCHEMA = (
    """
CREATE TABLE refs (
  -- node and name are unique together. There may be more than one name for
  -- a given node, and there may be no name at all for a given node (in the
  -- case of an anonymous hg head).
  node TEXT NOT NULL,
  name TEXT
);

-- The "possible heads" of the repository, which we use to figure out
-- if we need to re-walk the changelog.
CREATE TABLE possible_heads (
  node TEXT NOT NULL
);

-- The topological heads of the changelog, which hg depends on.
CREATE TABLE heads (
  node TEXT NOT NULL
);

-- A total ordering of the changelog
CREATE TABLE changelog (
  rev INTEGER NOT NULL PRIMARY KEY,
  node TEXT NOT NULL,
  p1 TEXT,
  p2 TEXT
);

CREATE UNIQUE INDEX changelog_node_idx ON changelog(node);
CREATE UNIQUE INDEX changelog_node_rev_idx ON changelog(rev, node);

-- Changed files for each commit, which lets us dynamically build
-- filelogs.
CREATE TABLE changedfiles (
  node TEXT NOT NULL,
  filename TEXT NOT NULL,
  -- 40 zeroes for deletions
  filenode TEXT NOT NULL,
-- to handle filelog parentage:
  p1node TEXT,
  p1filenode TEXT,
  p2node TEXT,
  p2filenode TEXT
);

CREATE INDEX changedfiles_nodes_idx
  ON changedfiles(node);

PRAGMA user_version=%d
"""
    % _CURRENT_SCHEMA_VERSION
)


def _createdb(path):
    # print('open db', path)
    # import traceback
    # traceback.print_stack()
    db = sqlite3.connect(encoding.strfromlocal(path))
    db.text_factory = bytes

    res = db.execute('PRAGMA user_version').fetchone()[0]

    # New database.
    if res == 0:
        for statement in _SCHEMA.split(';'):
            db.execute(statement.strip())

        db.commit()

    elif res == _CURRENT_SCHEMA_VERSION:
        pass

    else:
        raise error.Abort(_(b'sqlite database has unrecognized version'))

    db.execute('PRAGMA journal_mode=WAL')

    return db


_OUR_ORDER = ()
if pygit2:
    _OUR_ORDER = (
        pygit2.GIT_SORT_TOPOLOGICAL
        | pygit2.GIT_SORT_TIME
        | pygit2.GIT_SORT_REVERSE
    )

_DIFF_FLAGS = 1 << 21  # GIT_DIFF_FORCE_BINARY, which isn't exposed by pygit2


def _find_nearest_ancestor_introducing_node(
    db, gitrepo, file_path, walk_start, filenode
):
    """Find the nearest ancestor that introduces a file node.

    Args:
      db: a handle to our sqlite database.
      gitrepo: A pygit2.Repository instance.
      file_path: the path of a file in the repo
      walk_start: a pygit2.Oid that is a commit where we should start walking
                  for our nearest ancestor.

    Returns:
      A hexlified SHA that is the commit ID of the next-nearest parent.
    """
    assert isinstance(file_path, str), 'file_path must be str, got %r' % type(
        file_path
    )
    assert isinstance(filenode, str), 'filenode must be str, got %r' % type(
        filenode
    )
    parent_options = {
        row[0].decode('ascii')
        for row in db.execute(
            'SELECT node FROM changedfiles '
            'WHERE filename = ? AND filenode = ?',
            (file_path, filenode),
        )
    }
    inner_walker = gitrepo.walk(walk_start, _OUR_ORDER)
    for w in inner_walker:
        if w.id.hex in parent_options:
            return w.id.hex
    raise error.ProgrammingError(
        'Unable to find introducing commit for %s node %s from %s',
        (file_path, filenode, walk_start),
    )


def fill_in_filelog(gitrepo, db, startcommit, path, startfilenode):
    """Given a starting commit and path, fill in a filelog's parent pointers.

    Args:
      gitrepo: a pygit2.Repository
      db: a handle to our sqlite database
      startcommit: a hexlified node id for the commit to start at
      path: the path of the file whose parent pointers we should fill in.
      filenode: the hexlified node id of the file at startcommit

    TODO: make filenode optional
    """
    assert isinstance(
        startcommit, str
    ), 'startcommit must be str, got %r' % type(startcommit)
    assert isinstance(
        startfilenode, str
    ), 'startfilenode must be str, got %r' % type(startfilenode)
    visit = collections.deque([(startcommit, startfilenode)])
    while visit:
        cnode, filenode = visit.popleft()
        commit = gitrepo[cnode]
        parents = []
        for parent in commit.parents:
            t = parent.tree
            for comp in path.split('/'):
                try:
                    t = gitrepo[t[comp].id]
                except KeyError:
                    break
            else:
                introducer = _find_nearest_ancestor_introducing_node(
                    db, gitrepo, path, parent.id, t.id.hex
                )
                parents.append((introducer, t.id.hex))
        p1node = p1fnode = p2node = p2fnode = gitutil.nullgit
        for par, parfnode in parents:
            found = int(
                db.execute(
                    'SELECT COUNT(*) FROM changedfiles WHERE '
                    'node = ? AND filename = ? AND filenode = ? AND '
                    'p1node NOT NULL',
                    (par, path, parfnode),
                ).fetchone()[0]
            )
            if found == 0:
                assert par is not None
                visit.append((par, parfnode))
        if parents:
            p1node, p1fnode = parents[0]
        if len(parents) == 2:
            p2node, p2fnode = parents[1]
        if len(parents) > 2:
            raise error.ProgrammingError(
                b"git support can't handle octopus merges"
            )
        db.execute(
            'UPDATE changedfiles SET '
            'p1node = ?, p1filenode = ?, p2node = ?, p2filenode = ? '
            'WHERE node = ? AND filename = ? AND filenode = ?',
            (p1node, p1fnode, p2node, p2fnode, commit.id.hex, path, filenode),
        )
    db.commit()


def _index_repo(
    gitrepo,
    db,
    logfn=lambda x: None,
    progress_factory=lambda *args, **kwargs: None,
):
    # Identify all references so we can tell the walker to visit all of them.
    all_refs = gitrepo.listall_references()
    possible_heads = set()
    prog = progress_factory(b'refs')
    for pos, ref in enumerate(all_refs):
        if prog is not None:
            prog.update(pos)
        if not (
            ref.startswith('refs/heads/')  # local branch
            or ref.startswith('refs/tags/')  # tag
            or ref.startswith('refs/remotes/')  # remote branch
            or ref.startswith('refs/hg/')  # from this extension
        ):
            continue
        try:
            start = gitrepo.lookup_reference(ref).peel(pygit2.GIT_OBJ_COMMIT)
        except ValueError:
            # No commit to be found, so we don't care for hg's purposes.
            continue
        possible_heads.add(start.id)
    # Optimization: if the list of heads hasn't changed, don't
    # reindex, the changelog. This doesn't matter on small
    # repositories, but on even moderately deep histories (eg cpython)
    # this is a very important performance win.
    #
    # TODO: we should figure out how to incrementally index history
    # (preferably by detecting rewinds!) so that we don't have to do a
    # full changelog walk every time a new commit is created.
    cache_heads = {
        pycompat.sysstr(x[0])
        for x in db.execute('SELECT node FROM possible_heads')
    }
    walker = None
    cur_cache_heads = {h.hex for h in possible_heads}
    if cur_cache_heads == cache_heads:
        return
    logfn(b'heads mismatch, rebuilding dagcache\n')
    for start in possible_heads:
        if walker is None:
            walker = gitrepo.walk(start, _OUR_ORDER)
        else:
            walker.push(start)

    # Empty out the existing changelog. Even for large-ish histories
    # we can do the top-level "walk all the commits" dance very
    # quickly as long as we don't need to figure out the changed files
    # list.
    db.execute('DELETE FROM changelog')
    if prog is not None:
        prog.complete()
    prog = progress_factory(b'commits')
    # This walker is sure to visit all the revisions in history, but
    # only once.
    for pos, commit in enumerate(walker):
        if prog is not None:
            prog.update(pos)
        p1 = p2 = gitutil.nullgit
        if len(commit.parents) > 2:
            raise error.ProgrammingError(
                (
                    b"git support can't handle octopus merges, "
                    b"found a commit with %d parents :("
                )
                % len(commit.parents)
            )
        if commit.parents:
            p1 = commit.parents[0].id.hex
        if len(commit.parents) == 2:
            p2 = commit.parents[1].id.hex
        db.execute(
            'INSERT INTO changelog (rev, node, p1, p2) VALUES(?, ?, ?, ?)',
            (pos, commit.id.hex, p1, p2),
        )

        num_changedfiles = db.execute(
            "SELECT COUNT(*) from changedfiles WHERE node = ?",
            (commit.id.hex,),
        ).fetchone()[0]
        if not num_changedfiles:
            files = {}
            # I *think* we only need to check p1 for changed files
            # (and therefore linkrevs), because any node that would
            # actually have this commit as a linkrev would be
            # completely new in this rev.
            p1 = commit.parents[0].id.hex if commit.parents else None
            if p1 is not None:
                patchgen = gitrepo.diff(p1, commit.id.hex, flags=_DIFF_FLAGS)
            else:
                patchgen = commit.tree.diff_to_tree(
                    swap=True, flags=_DIFF_FLAGS
                )
            new_files = (p.delta.new_file for p in patchgen)
            files = {
                nf.path: nf.id.hex
                for nf in new_files
                if nf.id.raw != sha1nodeconstants.nullid
            }
            for p, n in files.items():
                # We intentionally set NULLs for any file parentage
                # information so it'll get demand-computed later. We
                # used to do it right here, and it was _very_ slow.
                db.execute(
                    'INSERT INTO changedfiles ('
                    'node, filename, filenode, p1node, p1filenode, p2node, '
                    'p2filenode) VALUES(?, ?, ?, ?, ?, ?, ?)',
                    (commit.id.hex, p, n, None, None, None, None),
                )
    db.execute('DELETE FROM heads')
    db.execute('DELETE FROM possible_heads')
    for hid in possible_heads:
        h = hid.hex
        db.execute('INSERT INTO possible_heads (node) VALUES(?)', (h,))
        haschild = db.execute(
            'SELECT COUNT(*) FROM changelog WHERE p1 = ? OR p2 = ?', (h, h)
        ).fetchone()[0]
        if not haschild:
            db.execute('INSERT INTO heads (node) VALUES(?)', (h,))

    db.commit()
    if prog is not None:
        prog.complete()


def get_index(
    gitrepo, logfn=lambda x: None, progress_factory=lambda *args, **kwargs: None
):
    cachepath = os.path.join(
        pycompat.fsencode(gitrepo.path), b'..', b'.hg', b'cache'
    )
    if not os.path.exists(cachepath):
        os.makedirs(cachepath)
    dbpath = os.path.join(cachepath, b'git-commits.sqlite')
    db = _createdb(dbpath)
    # TODO check against gitrepo heads before doing a full index
    # TODO thread a ui.progress call into this layer
    _index_repo(gitrepo, db, logfn, progress_factory)
    return db
