# tags.py - read tag info from local repository
#
# Copyright 2009 Olivia Mackall <olivia@selenic.com>
# Copyright 2009 Greg Ward <greg@gerg.ca>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# Currently this module only deals with reading and caching tags.
# Eventually, it could take care of updating (adding/removing/moving)
# tags too.


import binascii
import io

from .node import (
    bin,
    hex,
    nullrev,
    short,
)
from .i18n import _
from .revlogutils.constants import ENTRY_NODE_ID
from . import (
    encoding,
    error,
    match as matchmod,
    scmutil,
    util,
)
from .utils import stringutil


# Tags computation can be expensive and caches exist to make it fast in
# the common case.
#
# The "hgtagsfnodes1" cache file caches the .hgtags filenode values for
# each revision in the repository. The file is effectively an array of
# fixed length records. Read the docs for "hgtagsfnodescache" for technical
# details.
#
# The .hgtags filenode cache grows in proportion to the length of the
# changelog. The file is truncated when the # changelog is stripped.
#
# The purpose of the filenode cache is to avoid the most expensive part
# of finding global tags, which is looking up the .hgtags filenode in the
# manifest for each head. This can take dozens or over 100ms for
# repositories with very large manifests. Multiplied by dozens or even
# hundreds of heads and there is a significant performance concern.
#
# There also exist a separate cache file for each repository filter.
# These "tags-*" files store information about the history of tags.
#
# The tags cache files consists of a cache validation line followed by
# a history of tags.
#
# The cache validation line has the format:
#
#   <tiprev> <tipnode> [<filteredhash>]
#
# <tiprev> is an integer revision and <tipnode> is a 40 character hex
# node for that changeset. These redundantly identify the repository
# tip from the time the cache was written. In addition, <filteredhash>,
# if present, is a 40 character hex hash of the contents of the filtered
# revisions for this filter. If the set of filtered revs changes, the
# hash will change and invalidate the cache.
#
# The history part of the tags cache consists of lines of the form:
#
#   <node> <tag>
#
# (This format is identical to that of .hgtags files.)
#
# <tag> is the tag name and <node> is the 40 character hex changeset
# the tag is associated with.
#
# Tags are written sorted by tag name.
#
# Tags associated with multiple changesets have an entry for each changeset.
# The most recent changeset (in terms of revlog ordering for the head
# setting it) for each tag is last.


def warm_cache(repo):
    """ensure the cache is properly filled"""
    unfi = repo.unfiltered()
    fnodescache = hgtagsfnodescache(unfi)
    validated_fnodes = set()
    unknown_entries = set()
    flog = None

    entries = enumerate(repo.changelog.index)
    node_revs = ((e[ENTRY_NODE_ID], rev) for (rev, e) in entries)

    for node, rev in node_revs:
        fnode = fnodescache.getfnode(node=node, rev=rev)
        if fnode != repo.nullid:
            if fnode not in validated_fnodes:
                if flog is None:
                    flog = repo.file(b'.hgtags')
                if flog.hasnode(fnode):
                    validated_fnodes.add(fnode)
                else:
                    unknown_entries.add(node)

    if unknown_entries:
        fnodescache.refresh_invalid_nodes(unknown_entries)

    fnodescache.write()


def fnoderevs(ui, repo, revs):
    """return the list of '.hgtags' fnodes used in a set revisions

    This is returned as list of unique fnodes. We use a list instead of a set
    because order matters when it comes to tags."""
    unfi = repo.unfiltered()
    tonode = unfi.changelog.node
    nodes = [tonode(r) for r in revs]
    fnodes = _getfnodes(ui, repo, nodes)
    fnodes = _filterfnodes(fnodes, nodes)
    return fnodes


def _nulltonone(repo, value):
    """convert nullid to None

    For tag value, nullid means "deleted". This small utility function helps
    translating that to None."""
    if value == repo.nullid:
        return None
    return value


def difftags(ui, repo, oldfnodes, newfnodes):
    """list differences between tags expressed in two set of file-nodes

    The list contains entries in the form: (tagname, oldvalue, new value).
    None is used to expressed missing value:
        ('foo', None, 'abcd') is a new tag,
        ('bar', 'ef01', None) is a deletion,
        ('baz', 'abcd', 'ef01') is a tag movement.
    """
    if oldfnodes == newfnodes:
        return []
    oldtags = _tagsfromfnodes(ui, repo, oldfnodes)
    newtags = _tagsfromfnodes(ui, repo, newfnodes)

    # list of (tag, old, new): None means missing
    entries = []
    for tag, (new, __) in newtags.items():
        new = _nulltonone(repo, new)
        old, __ = oldtags.pop(tag, (None, None))
        old = _nulltonone(repo, old)
        if old != new:
            entries.append((tag, old, new))
    # handle deleted tags
    for tag, (old, __) in oldtags.items():
        old = _nulltonone(repo, old)
        if old is not None:
            entries.append((tag, old, None))
    entries.sort()
    return entries


def writediff(fp, difflist):
    """write tags diff information to a file.

    Data are stored with a line based format:

        <action> <hex-node> <tag-name>\n

    Action are defined as follow:
       -R tag is removed,
       +A tag is added,
       -M tag is moved (old value),
       +M tag is moved (new value),

    Example:

         +A 875517b4806a848f942811a315a5bce30804ae85 t5

    See documentation of difftags output for details about the input.
    """
    add = b'+A %s %s\n'
    remove = b'-R %s %s\n'
    updateold = b'-M %s %s\n'
    updatenew = b'+M %s %s\n'
    for tag, old, new in difflist:
        # translate to hex
        if old is not None:
            old = hex(old)
        if new is not None:
            new = hex(new)
        # write to file
        if old is None:
            fp.write(add % (new, tag))
        elif new is None:
            fp.write(remove % (old, tag))
        else:
            fp.write(updateold % (old, tag))
            fp.write(updatenew % (new, tag))


def findglobaltags(ui, repo):
    """Find global tags in a repo: return a tagsmap

    tagsmap: tag name to (node, hist) 2-tuples.

    The tags cache is read and updated as a side-effect of calling.
    """
    (heads, tagfnode, valid, cachetags, shouldwrite) = _readtagcache(ui, repo)
    if cachetags is not None:
        assert not shouldwrite
        # XXX is this really 100% correct?  are there oddball special
        # cases where a global tag should outrank a local tag but won't,
        # because cachetags does not contain rank info?
        alltags = {}
        _updatetags(cachetags, alltags)
        return alltags

    has_node = repo.changelog.index.has_node
    for head in reversed(heads):  # oldest to newest
        assert has_node(head), b"tag cache returned bogus head %s" % short(head)
    fnodes = _filterfnodes(tagfnode, reversed(heads))
    alltags = _tagsfromfnodes(ui, repo, fnodes)

    # and update the cache (if necessary)
    if shouldwrite:
        _writetagcache(ui, repo, valid, alltags)
    return alltags


def _filterfnodes(tagfnode, nodes):
    """return a list of unique fnodes

    The order of this list matches the order of "nodes". Preserving this order
    is important as reading tags in different order provides different
    results."""
    seen = set()  # set of fnode
    fnodes = []
    for no in nodes:  # oldest to newest
        fnode = tagfnode.get(no)
        if fnode and fnode not in seen:
            seen.add(fnode)
            fnodes.append(fnode)
    return fnodes


def _tagsfromfnodes(ui, repo, fnodes):
    """return a tagsmap from a list of file-node

    tagsmap: tag name to (node, hist) 2-tuples.

    The order of the list matters."""
    alltags = {}
    fctx = None
    for fnode in fnodes:
        if fctx is None:
            fctx = repo.filectx(b'.hgtags', fileid=fnode)
        else:
            fctx = fctx.filectx(fnode)
        filetags = _readtags(ui, repo, fctx.data().splitlines(), fctx)
        _updatetags(filetags, alltags)
    return alltags


def readlocaltags(ui, repo, alltags, tagtypes):
    '''Read local tags in repo. Update alltags and tagtypes.'''
    try:
        data = repo.vfs.read(b"localtags")
    except FileNotFoundError:
        return

    # localtags is in the local encoding; re-encode to UTF-8 on
    # input for consistency with the rest of this module.
    filetags = _readtags(
        ui, repo, data.splitlines(), b"localtags", recode=encoding.fromlocal
    )

    # remove tags pointing to invalid nodes
    cl = repo.changelog
    for t in list(filetags):
        try:
            cl.rev(filetags[t][0])
        except (LookupError, ValueError):
            del filetags[t]

    _updatetags(filetags, alltags, b'local', tagtypes)


def _readtaghist(ui, repo, lines, fn, recode=None, calcnodelines=False):
    """Read tag definitions from a file (or any source of lines).

    This function returns two sortdicts with similar information:

    - the first dict, bintaghist, contains the tag information as expected by
      the _readtags function, i.e. a mapping from tag name to (node, hist):
        - node is the node id from the last line read for that name,
        - hist is the list of node ids previously associated with it (in file
          order). All node ids are binary, not hex.

    - the second dict, hextaglines, is a mapping from tag name to a list of
      [hexnode, line number] pairs, ordered from the oldest to the newest node.

    When calcnodelines is False the hextaglines dict is not calculated (an
    empty dict is returned). This is done to improve this function's
    performance in cases where the line numbers are not needed.
    """

    bintaghist = util.sortdict()
    hextaglines = util.sortdict()
    count = 0

    def dbg(msg):
        ui.debug(b"%s, line %d: %s\n" % (fn, count, msg))

    for nline, line in enumerate(lines):
        count += 1
        if not line:
            continue
        try:
            (nodehex, name) = line.split(b" ", 1)
        except ValueError:
            dbg(b"cannot parse entry")
            continue
        name = name.strip()
        if recode:
            name = recode(name)
        try:
            nodebin = bin(nodehex)
        except binascii.Error:
            dbg(b"node '%s' is not well formed" % nodehex)
            continue

        # update filetags
        if calcnodelines:
            # map tag name to a list of line numbers
            if name not in hextaglines:
                hextaglines[name] = []
            hextaglines[name].append([nodehex, nline])
            continue
        # map tag name to (node, hist)
        if name not in bintaghist:
            bintaghist[name] = []
        bintaghist[name].append(nodebin)
    return bintaghist, hextaglines


def _readtags(ui, repo, lines, fn, recode=None, calcnodelines=False):
    """Read tag definitions from a file (or any source of lines).

    Returns a mapping from tag name to (node, hist).

    "node" is the node id from the last line read for that name. "hist"
    is the list of node ids previously associated with it (in file order).
    All node ids are binary, not hex.
    """
    filetags, nodelines = _readtaghist(
        ui, repo, lines, fn, recode=recode, calcnodelines=calcnodelines
    )
    # util.sortdict().__setitem__ is much slower at replacing then inserting
    # new entries. The difference can matter if there are thousands of tags.
    # Create a new sortdict to avoid the performance penalty.
    newtags = util.sortdict()
    for tag, taghist in filetags.items():
        newtags[tag] = (taghist[-1], taghist[:-1])
    return newtags


def _updatetags(filetags, alltags, tagtype=None, tagtypes=None):
    """Incorporate the tag info read from one file into dictionnaries

    The first one, 'alltags', is a "tagmaps" (see 'findglobaltags' for details).

    The second one, 'tagtypes', is optional and will be updated to track the
    "tagtype" of entries in the tagmaps. When set, the 'tagtype' argument also
    needs to be set."""
    if tagtype is None:
        assert tagtypes is None

    for name, nodehist in filetags.items():
        if name not in alltags:
            alltags[name] = nodehist
            if tagtype is not None:
                tagtypes[name] = tagtype
            continue

        # we prefer alltags[name] if:
        #  it supersedes us OR
        #  mutual supersedes and it has a higher rank
        # otherwise we win because we're tip-most
        anode, ahist = nodehist
        bnode, bhist = alltags[name]
        if (
            bnode != anode
            and anode in bhist
            and (bnode not in ahist or len(bhist) > len(ahist))
        ):
            anode = bnode
        elif tagtype is not None:
            tagtypes[name] = tagtype
        ahist.extend([n for n in bhist if n not in ahist])
        alltags[name] = anode, ahist


def _filename(repo):
    """name of a tagcache file for a given repo or repoview"""
    filename = b'tags2'
    if repo.filtername:
        filename = b'%s-%s' % (filename, repo.filtername)
    return filename


def _readtagcache(ui, repo):
    """Read the tag cache.

    Returns a tuple (heads, fnodes, validinfo, cachetags, shouldwrite).

    If the cache is completely up-to-date, "cachetags" is a dict of the
    form returned by _readtags() and "heads", "fnodes", and "validinfo" are
    None and "shouldwrite" is False.

    If the cache is not up to date, "cachetags" is None. "heads" is a list
    of all heads currently in the repository, ordered from tip to oldest.
    "validinfo" is a tuple describing cache validation info. This is used
    when writing the tags cache. "fnodes" is a mapping from head to .hgtags
    filenode. "shouldwrite" is True.

    If the cache is not up to date, the caller is responsible for reading tag
    info from each returned head. (See findglobaltags().)
    """
    try:
        cachefile = repo.cachevfs(_filename(repo), b'r')
        # force reading the file for static-http
        cachelines = iter(cachefile)
    except IOError:
        cachefile = None

    cacherev = None
    cachenode = None
    cachehash = None
    if cachefile:
        try:
            validline = next(cachelines)
            validline = validline.split()
            cacherev = int(validline[0])
            cachenode = bin(validline[1])
            if len(validline) > 2:
                cachehash = bin(validline[2])
        except Exception:
            # corruption of the cache, just recompute it.
            pass

    tipnode = repo.changelog.tip()
    tiprev = len(repo.changelog) - 1

    # Case 1 (common): tip is the same, so nothing has changed.
    # (Unchanged tip trivially means no changesets have been added.
    # But, thanks to localrepository.destroyed(), it also means none
    # have been destroyed by strip or rollback.)
    if (
        cacherev == tiprev
        and cachenode == tipnode
        and cachehash
        == scmutil.combined_filtered_and_obsolete_hash(
            repo,
            tiprev,
        )
    ):
        tags = _readtags(ui, repo, cachelines, cachefile.name)
        cachefile.close()
        return (None, None, None, tags, False)
    if cachefile:
        cachefile.close()  # ignore rest of file

    valid = (
        tiprev,
        tipnode,
        scmutil.combined_filtered_and_obsolete_hash(
            repo,
            tiprev,
        ),
    )

    repoheads = repo.heads()
    # Case 2 (uncommon): empty repo; get out quickly and don't bother
    # writing an empty cache.
    if repoheads == [repo.nullid]:
        return ([], {}, valid, {}, False)

    # Case 3 (uncommon): cache file missing or empty.

    # Case 4 (uncommon): tip rev decreased.  This should only happen
    # when we're called from localrepository.destroyed().  Refresh the
    # cache so future invocations will not see disappeared heads in the
    # cache.

    # Case 5 (common): tip has changed, so we've added/replaced heads.

    # As it happens, the code to handle cases 3, 4, 5 is the same.

    # N.B. in case 4 (nodes destroyed), "new head" really means "newly
    # exposed".
    if not len(repo.file(b'.hgtags')):
        # No tags have ever been committed, so we can avoid a
        # potentially expensive search.
        return ([], {}, valid, None, True)

    # Now we have to lookup the .hgtags filenode for every new head.
    # This is the most expensive part of finding tags, so performance
    # depends primarily on the size of newheads.  Worst case: no cache
    # file, so newheads == repoheads.
    # Reversed order helps the cache ('repoheads' is in descending order)
    cachefnode = _getfnodes(ui, repo, reversed(repoheads))

    # Caller has to iterate over all heads, but can use the filenodes in
    # cachefnode to get to each .hgtags revision quickly.
    return (repoheads, cachefnode, valid, None, True)


def _getfnodes(ui, repo, nodes=None, revs=None):
    """return .hgtags fnodes for a list of changeset nodes

    Return value is a {node: fnode} mapping. There will be no entry for nodes
    without a '.hgtags' file.
    """
    starttime = util.timer()
    fnodescache = hgtagsfnodescache(repo.unfiltered())
    cachefnode = {}
    validated_fnodes = set()
    unknown_entries = set()

    if nodes is None and revs is None:
        raise error.ProgrammingError("need to specify either nodes or revs")
    elif nodes is not None and revs is None:
        to_rev = repo.changelog.index.rev
        nodes_revs = ((n, to_rev(n)) for n in nodes)
    elif nodes is None and revs is not None:
        to_node = repo.changelog.node
        nodes_revs = ((to_node(r), r) for r in revs)
    else:
        msg = "need to specify only one of nodes or revs"
        raise error.ProgrammingError(msg)

    flog = None
    for node, rev in nodes_revs:
        fnode = fnodescache.getfnode(node=node, rev=rev)
        if fnode != repo.nullid:
            if fnode not in validated_fnodes:
                if flog is None:
                    flog = repo.file(b'.hgtags')
                if flog.hasnode(fnode):
                    validated_fnodes.add(fnode)
                else:
                    unknown_entries.add(node)
            cachefnode[node] = fnode

    if unknown_entries:
        fixed_nodemap = fnodescache.refresh_invalid_nodes(unknown_entries)
        for node, fnode in fixed_nodemap.items():
            if fnode != repo.nullid:
                cachefnode[node] = fnode

    fnodescache.write()

    duration = util.timer() - starttime
    ui.log(
        b'tagscache',
        b'%d/%d cache hits/lookups in %0.4f seconds\n',
        fnodescache.hitcount,
        fnodescache.lookupcount,
        duration,
    )
    return cachefnode


def _writetagcache(ui, repo, valid, cachetags):
    filename = _filename(repo)
    try:
        cachefile = repo.cachevfs(filename, b'w', atomictemp=True)
    except (OSError, IOError):
        return

    ui.log(
        b'tagscache',
        b'writing .hg/cache/%s with %d tags\n',
        filename,
        len(cachetags),
    )

    if valid[2]:
        cachefile.write(
            b'%d %s %s\n' % (valid[0], hex(valid[1]), hex(valid[2]))
        )
    else:
        cachefile.write(b'%d %s\n' % (valid[0], hex(valid[1])))

    # Tag names in the cache are in UTF-8 -- which is the whole reason
    # we keep them in UTF-8 throughout this module.  If we converted
    # them local encoding on input, we would lose info writing them to
    # the cache.
    for (name, (node, hist)) in sorted(cachetags.items()):
        for n in hist:
            cachefile.write(b"%s %s\n" % (hex(n), name))
        cachefile.write(b"%s %s\n" % (hex(node), name))

    try:
        cachefile.close()
    except (OSError, IOError):
        pass


def tag(repo, names, node, message, local, user, date, editor=False):
    """tag a revision with one or more symbolic names.

    names is a list of strings or, when adding a single tag, names may be a
    string.

    if local is True, the tags are stored in a per-repository file.
    otherwise, they are stored in the .hgtags file, and a new
    changeset is committed with the change.

    keyword arguments:

    local: whether to store tags in non-version-controlled file
    (default False)

    message: commit message to use if committing

    user: name of user to use if committing

    date: date tuple to use if committing"""

    if not local:
        m = matchmod.exact([b'.hgtags'])
        st = repo.status(match=m, unknown=True, ignored=True)
        if any(
            (
                st.modified,
                st.added,
                st.removed,
                st.deleted,
                st.unknown,
                st.ignored,
            )
        ):
            raise error.Abort(
                _(b'working copy of .hgtags is changed'),
                hint=_(b'please commit .hgtags manually'),
            )

    with repo.wlock():
        repo.tags()  # instantiate the cache
        _tag(repo, names, node, message, local, user, date, editor=editor)


def _tag(
    repo, names, node, message, local, user, date, extra=None, editor=False
):
    if isinstance(names, bytes):
        names = (names,)

    branches = repo.branchmap()
    for name in names:
        repo.hook(b'pretag', throw=True, node=hex(node), tag=name, local=local)
        if name in branches:
            repo.ui.warn(
                _(b"warning: tag %s conflicts with existing branch name\n")
                % name
            )

    def writetags(fp, names, munge, prevtags):
        fp.seek(0, io.SEEK_END)
        if prevtags and not prevtags.endswith(b'\n'):
            fp.write(b'\n')
        for name in names:
            if munge:
                m = munge(name)
            else:
                m = name

            if repo._tagscache.tagtypes and name in repo._tagscache.tagtypes:
                old = repo.tags().get(name, repo.nullid)
                fp.write(b'%s %s\n' % (hex(old), m))
            fp.write(b'%s %s\n' % (hex(node), m))
        fp.close()

    prevtags = b''
    if local:
        try:
            fp = repo.vfs(b'localtags', b'r+')
        except IOError:
            fp = repo.vfs(b'localtags', b'a')
        else:
            prevtags = fp.read()

        # local tags are stored in the current charset
        writetags(fp, names, None, prevtags)
        for name in names:
            repo.hook(b'tag', node=hex(node), tag=name, local=local)
        return

    try:
        fp = repo.wvfs(b'.hgtags', b'rb+')
    except FileNotFoundError:
        fp = repo.wvfs(b'.hgtags', b'ab')
    else:
        prevtags = fp.read()

    # committed tags are stored in UTF-8
    writetags(fp, names, encoding.fromlocal, prevtags)

    fp.close()

    repo.invalidatecaches()

    with repo.dirstate.changing_files(repo):
        if b'.hgtags' not in repo.dirstate:
            repo[None].add([b'.hgtags'])

    m = matchmod.exact([b'.hgtags'])
    tagnode = repo.commit(
        message, user, date, extra=extra, match=m, editor=editor
    )

    for name in names:
        repo.hook(b'tag', node=hex(node), tag=name, local=local)

    return tagnode


_fnodescachefile = b'hgtagsfnodes1'
_fnodesrecsize = 4 + 20  # changeset fragment + filenode
_fnodesmissingrec = b'\xff' * 24


class hgtagsfnodescache:
    """Persistent cache mapping revisions to .hgtags filenodes.

    The cache is an array of records. Each item in the array corresponds to
    a changelog revision. Values in the array contain the first 4 bytes of
    the node hash and the 20 bytes .hgtags filenode for that revision.

    The first 4 bytes are present as a form of verification. Repository
    stripping and rewriting may change the node at a numeric revision in the
    changelog. The changeset fragment serves as a verifier to detect
    rewriting. This logic is shared with the rev branch cache (see
    branchmap.py).

    The instance holds in memory the full cache content but entries are
    only parsed on read.

    Instances behave like lists. ``c[i]`` works where i is a rev or
    changeset node. Missing indexes are populated automatically on access.
    """

    def __init__(self, repo):
        assert repo.filtername is None

        self._repo = repo

        # Only for reporting purposes.
        self.lookupcount = 0
        self.hitcount = 0

        try:
            data = repo.cachevfs.read(_fnodescachefile)
        except (OSError, IOError):
            data = b""
        self._raw = bytearray(data)

        # The end state of self._raw is an array that is of the exact length
        # required to hold a record for every revision in the repository.
        # We truncate or extend the array as necessary. self._dirtyoffset is
        # defined to be the start offset at which we need to write the output
        # file. This offset is also adjusted when new entries are calculated
        # for array members.
        cllen = len(repo.changelog)
        wantedlen = cllen * _fnodesrecsize
        rawlen = len(self._raw)

        self._dirtyoffset = None

        rawlentokeep = min(
            wantedlen, (rawlen // _fnodesrecsize) * _fnodesrecsize
        )
        if rawlen > rawlentokeep:
            # There's no easy way to truncate array instances. This seems
            # slightly less evil than copying a potentially large array slice.
            for i in range(rawlen - rawlentokeep):
                self._raw.pop()
            rawlen = len(self._raw)
            self._dirtyoffset = rawlen
        if rawlen < wantedlen:
            if self._dirtyoffset is None:
                self._dirtyoffset = rawlen
            # TODO: zero fill entire record, because it's invalid not missing?
            self._raw.extend(b'\xff' * (wantedlen - rawlen))

    def getfnode(self, node, computemissing=True, rev=None):
        """Obtain the filenode of the .hgtags file at a specified revision.

        If the value is in the cache, the entry will be validated and returned.
        Otherwise, the filenode will be computed and returned unless
        "computemissing" is False.  In that case, None will be returned if
        the entry is missing or False if the entry is invalid without
        any potentially expensive computation being performed.

        If an .hgtags does not exist at the specified revision, nullid is
        returned.
        """
        if node == self._repo.nullid:
            return node

        if rev is None:
            rev = self._repo.changelog.rev(node)

        self.lookupcount += 1

        offset = rev * _fnodesrecsize
        record = b'%s' % self._raw[offset : offset + _fnodesrecsize]
        properprefix = node[0:4]

        # Validate and return existing entry.
        if record != _fnodesmissingrec and len(record) == _fnodesrecsize:
            fileprefix = record[0:4]

            if fileprefix == properprefix:
                self.hitcount += 1
                return record[4:]

            # Fall through.

        # If we get here, the entry is either missing or invalid.

        if not computemissing:
            if record != _fnodesmissingrec:
                return False
            return None

        fnode = self._computefnode(node)
        self._writeentry(offset, properprefix, fnode)
        return fnode

    def _computefnode(self, node):
        """Finds the tag filenode for a node which is missing or invalid
        in cache"""
        ctx = self._repo[node]
        rev = ctx.rev()
        fnode = None
        cl = self._repo.changelog
        p1rev, p2rev = cl._uncheckedparentrevs(rev)
        p1node = cl.node(p1rev)
        p1fnode = self.getfnode(p1node, computemissing=False)
        if p2rev != nullrev:
            # There is some no-merge changeset where p1 is null and p2 is set
            # Processing them as merge is just slower, but still gives a good
            # result.
            p2node = cl.node(p2rev)
            p2fnode = self.getfnode(p2node, computemissing=False)
            if p1fnode != p2fnode:
                # we cannot rely on readfast because we don't know against what
                # parent the readfast delta is computed
                p1fnode = None
        if p1fnode:
            mctx = ctx.manifestctx()
            fnode = mctx.readfast().get(b'.hgtags')
            if fnode is None:
                fnode = p1fnode
        if fnode is None:
            # Populate missing entry.
            try:
                fnode = ctx.filenode(b'.hgtags')
            except error.LookupError:
                # No .hgtags file on this revision.
                fnode = self._repo.nullid
        return fnode

    def setfnode(self, node, fnode):
        """Set the .hgtags filenode for a given changeset."""
        assert len(fnode) == 20
        ctx = self._repo[node]

        # Do a lookup first to avoid writing if nothing has changed.
        if self.getfnode(ctx.node(), computemissing=False) == fnode:
            return

        self._writeentry(ctx.rev() * _fnodesrecsize, node[0:4], fnode)

    def refresh_invalid_nodes(self, nodes):
        """recomputes file nodes for a given set of nodes which has unknown
        filenodes for them in the cache
        Also updates the in-memory cache with the correct filenode.
        Caller needs to take care about calling `.write()` so that updates are
        persisted.
        Returns a map {node: recomputed fnode}
        """
        fixed_nodemap = {}
        for node in nodes:
            fnode = self._computefnode(node)
            fixed_nodemap[node] = fnode
            self.setfnode(node, fnode)
        return fixed_nodemap

    def _writeentry(self, offset, prefix, fnode):
        # Slices on array instances only accept other array.
        entry = bytearray(prefix + fnode)
        self._raw[offset : offset + _fnodesrecsize] = entry
        # self._dirtyoffset could be None.
        self._dirtyoffset = min(self._dirtyoffset or 0, offset or 0)

    def write(self):
        """Perform all necessary writes to cache file.

        This may no-op if no writes are needed or if a write lock could
        not be obtained.
        """
        if self._dirtyoffset is None:
            return

        data = self._raw[self._dirtyoffset :]
        if not data:
            return

        repo = self._repo

        try:
            lock = repo.lock(wait=False)
        except error.LockError:
            repo.ui.log(
                b'tagscache',
                b'not writing .hg/cache/%s because '
                b'lock cannot be acquired\n' % _fnodescachefile,
            )
            return

        try:
            f = repo.cachevfs.open(_fnodescachefile, b'ab')
            try:
                # if the file has been truncated
                actualoffset = f.tell()
                if actualoffset < self._dirtyoffset:
                    self._dirtyoffset = actualoffset
                    data = self._raw[self._dirtyoffset :]
                f.seek(self._dirtyoffset)
                f.truncate()
                repo.ui.log(
                    b'tagscache',
                    b'writing %d bytes to cache/%s\n'
                    % (len(data), _fnodescachefile),
                )
                f.write(data)
                self._dirtyoffset = None
            finally:
                f.close()
        except (IOError, OSError) as inst:
            repo.ui.log(
                b'tagscache',
                b"couldn't write cache/%s: %s\n"
                % (_fnodescachefile, stringutil.forcebytestr(inst)),
            )
        finally:
            lock.release()


def clear_cache_on_disk(repo):
    """function used by the perf extension to "tags" cache"""
    repo.cachevfs.tryunlink(_filename(repo))


# a small attribute to help `hg perf::tags` to detect a fixed version.
clear_cache_fnodes_is_working = True


def clear_cache_fnodes(repo):
    """function used by the perf extension to clear "file node cache"""
    repo.cachevfs.tryunlink(_fnodescachefile)


def forget_fnodes(repo, revs):
    """function used by the perf extension to prune some entries from the fnodes
    cache"""
    missing_1 = b'\xff' * 4
    missing_2 = b'\xff' * 20
    cache = hgtagsfnodescache(repo.unfiltered())
    for r in revs:
        cache._writeentry(r * _fnodesrecsize, missing_1, missing_2)
    cache.write()
