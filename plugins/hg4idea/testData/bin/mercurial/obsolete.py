# obsolete.py - obsolete markers handling
#
# Copyright 2012 Pierre-Yves David <pierre-yves.david@ens-lyon.org>
#                Logilab SA        <contact@logilab.fr>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Obsolete markers handling

An obsolete marker maps an old changeset to a list of new
changesets. If the list of new changesets is empty, the old changeset
is said to be "killed". Otherwise, the old changeset is being
"replaced" by the new changesets.

Obsolete markers can be used to record and distribute changeset graph
transformations performed by history rewriting operations, and help
building new tools to reconciliate conflicting rewriting actions. To
facilitate conflicts resolution, markers include various annotations
besides old and news changeset identifiers, such as creation date or
author name.

The old obsoleted changeset is called "precursor" and possible replacements are
called "successors".  Markers that used changeset X as a precursors are called
"successor markers of X" because they hold information about the successors of
X. Markers that use changeset Y as a successors are call "precursor markers of
Y" because they hold information about the precursors of Y.

Examples:

- When changeset A is replacement by a changeset A', one marker is stored:

    (A, (A'))

- When changesets A and B are folded into a new changeset C two markers are
  stored:

    (A, (C,)) and (B, (C,))

- When changeset A is simply "pruned" from the graph, a marker in create:

    (A, ())

- When changeset A is split into B and C, a single marker are used:

    (A, (C, C))

  We use a single marker to distinct the "split" case from the "divergence"
  case. If two independents operation rewrite the same changeset A in to A' and
  A'' when have an error case: divergent rewriting. We can detect it because
  two markers will be created independently:

  (A, (B,)) and (A, (C,))

Format
------

Markers are stored in an append-only file stored in
'.hg/store/obsstore'.

The file starts with a version header:

- 1 unsigned byte: version number, starting at zero.


The header is followed by the markers. Each marker is made of:

- 1 unsigned byte: number of new changesets "R", could be zero.

- 1 unsigned 32-bits integer: metadata size "M" in bytes.

- 1 byte: a bit field. It is reserved for flags used in obsolete
  markers common operations, to avoid repeated decoding of metadata
  entries.

- 20 bytes: obsoleted changeset identifier.

- N*20 bytes: new changesets identifiers.

- M bytes: metadata as a sequence of nul-terminated strings. Each
  string contains a key and a value, separated by a color ':', without
  additional encoding. Keys cannot contain '\0' or ':' and values
  cannot contain '\0'.
"""
import struct
import util, base85, node
from i18n import _

_pack = struct.pack
_unpack = struct.unpack

_SEEK_END = 2 # os.SEEK_END was introduced in Python 2.5

# the obsolete feature is not mature enough to be enabled by default.
# you have to rely on third party extension extension to enable this.
_enabled = False

# data used for parsing and writing
_fmversion = 0
_fmfixed   = '>BIB20s'
_fmnode = '20s'
_fmfsize = struct.calcsize(_fmfixed)
_fnodesize = struct.calcsize(_fmnode)

### obsolescence marker flag

## bumpedfix flag
#
# When a changeset A' succeed to a changeset A which became public, we call A'
# "bumped" because it's a successors of a public changesets
#
# o    A' (bumped)
# |`:
# | o  A
# |/
# o    Z
#
# The way to solve this situation is to create a new changeset Ad as children
# of A. This changeset have the same content than A'. So the diff from A to A'
# is the same than the diff from A to Ad. Ad is marked as a successors of A'
#
# o   Ad
# |`:
# | x A'
# |'|
# o | A
# |/
# o Z
#
# But by transitivity Ad is also a successors of A. To avoid having Ad marked
# as bumped too, we add the `bumpedfix` flag to the marker. <A', (Ad,)>.
# This flag mean that the successors express the changes between the public and
# bumped version and fix the situation, breaking the transitivity of
# "bumped" here.
bumpedfix = 1

def _readmarkers(data):
    """Read and enumerate markers from raw data"""
    off = 0
    diskversion = _unpack('>B', data[off:off + 1])[0]
    off += 1
    if diskversion != _fmversion:
        raise util.Abort(_('parsing obsolete marker: unknown version %r')
                         % diskversion)

    # Loop on markers
    l = len(data)
    while off + _fmfsize <= l:
        # read fixed part
        cur = data[off:off + _fmfsize]
        off += _fmfsize
        nbsuc, mdsize, flags, pre = _unpack(_fmfixed, cur)
        # read replacement
        sucs = ()
        if nbsuc:
            s = (_fnodesize * nbsuc)
            cur = data[off:off + s]
            sucs = _unpack(_fmnode * nbsuc, cur)
            off += s
        # read metadata
        # (metadata will be decoded on demand)
        metadata = data[off:off + mdsize]
        if len(metadata) != mdsize:
            raise util.Abort(_('parsing obsolete marker: metadata is too '
                               'short, %d bytes expected, got %d')
                             % (mdsize, len(metadata)))
        off += mdsize
        yield (pre, sucs, flags, metadata)

def encodemeta(meta):
    """Return encoded metadata string to string mapping.

    Assume no ':' in key and no '\0' in both key and value."""
    for key, value in meta.iteritems():
        if ':' in key or '\0' in key:
            raise ValueError("':' and '\0' are forbidden in metadata key'")
        if '\0' in value:
            raise ValueError("':' are forbidden in metadata value'")
    return '\0'.join(['%s:%s' % (k, meta[k]) for k in sorted(meta)])

def decodemeta(data):
    """Return string to string dictionary from encoded version."""
    d = {}
    for l in data.split('\0'):
        if l:
            key, value = l.split(':')
            d[key] = value
    return d

class marker(object):
    """Wrap obsolete marker raw data"""

    def __init__(self, repo, data):
        # the repo argument will be used to create changectx in later version
        self._repo = repo
        self._data = data
        self._decodedmeta = None

    def precnode(self):
        """Precursor changeset node identifier"""
        return self._data[0]

    def succnodes(self):
        """List of successor changesets node identifiers"""
        return self._data[1]

    def metadata(self):
        """Decoded metadata dictionary"""
        if self._decodedmeta is None:
            self._decodedmeta = decodemeta(self._data[3])
        return self._decodedmeta

    def date(self):
        """Creation date as (unixtime, offset)"""
        parts = self.metadata()['date'].split(' ')
        return (float(parts[0]), int(parts[1]))

class obsstore(object):
    """Store obsolete markers

    Markers can be accessed with two mappings:
    - precursors[x] -> set(markers on precursors edges of x)
    - successors[x] -> set(markers on successors edges of x)
    """

    def __init__(self, sopener):
        # caches for various obsolescence related cache
        self.caches = {}
        self._all = []
        # new markers to serialize
        self.precursors = {}
        self.successors = {}
        self.sopener = sopener
        data = sopener.tryread('obsstore')
        if data:
            self._load(_readmarkers(data))

    def __iter__(self):
        return iter(self._all)

    def __nonzero__(self):
        return bool(self._all)

    def create(self, transaction, prec, succs=(), flag=0, metadata=None):
        """obsolete: add a new obsolete marker

        * ensuring it is hashable
        * check mandatory metadata
        * encode metadata
        """
        if metadata is None:
            metadata = {}
        if 'date' not in metadata:
            metadata['date'] = "%d %d" % util.makedate()
        if len(prec) != 20:
            raise ValueError(prec)
        for succ in succs:
            if len(succ) != 20:
                raise ValueError(succ)
        marker = (str(prec), tuple(succs), int(flag), encodemeta(metadata))
        self.add(transaction, [marker])

    def add(self, transaction, markers):
        """Add new markers to the store

        Take care of filtering duplicate.
        Return the number of new marker."""
        if not _enabled:
            raise util.Abort('obsolete feature is not enabled on this repo')
        new = [m for m in markers if m not in self._all]
        if new:
            f = self.sopener('obsstore', 'ab')
            try:
                # Whether the file's current position is at the begin or at
                # the end after opening a file for appending is implementation
                # defined. So we must seek to the end before calling tell(),
                # or we may get a zero offset for non-zero sized files on
                # some platforms (issue3543).
                f.seek(0, _SEEK_END)
                offset = f.tell()
                transaction.add('obsstore', offset)
                # offset == 0: new file - add the version header
                for bytes in _encodemarkers(new, offset == 0):
                    f.write(bytes)
            finally:
                # XXX: f.close() == filecache invalidation == obsstore rebuilt.
                # call 'filecacheentry.refresh()'  here
                f.close()
            self._load(new)
            # new marker *may* have changed several set. invalidate the cache.
            self.caches.clear()
        return len(new)

    def mergemarkers(self, transaction, data):
        markers = _readmarkers(data)
        self.add(transaction, markers)

    def _load(self, markers):
        for mark in markers:
            self._all.append(mark)
            pre, sucs = mark[:2]
            self.successors.setdefault(pre, set()).add(mark)
            for suc in sucs:
                self.precursors.setdefault(suc, set()).add(mark)
        if node.nullid in self.precursors:
            raise util.Abort(_('bad obsolescence marker detected: '
                               'invalid successors nullid'))

def _encodemarkers(markers, addheader=False):
    # Kept separate from flushmarkers(), it will be reused for
    # markers exchange.
    if addheader:
        yield _pack('>B', _fmversion)
    for marker in markers:
        yield _encodeonemarker(marker)


def _encodeonemarker(marker):
    pre, sucs, flags, metadata = marker
    nbsuc = len(sucs)
    format = _fmfixed + (_fmnode * nbsuc)
    data = [nbsuc, len(metadata), flags, pre]
    data.extend(sucs)
    return _pack(format, *data) + metadata

# arbitrary picked to fit into 8K limit from HTTP server
# you have to take in account:
# - the version header
# - the base85 encoding
_maxpayload = 5300

def listmarkers(repo):
    """List markers over pushkey"""
    if not repo.obsstore:
        return {}
    keys = {}
    parts = []
    currentlen = _maxpayload * 2  # ensure we create a new part
    for marker in  repo.obsstore:
        nextdata = _encodeonemarker(marker)
        if (len(nextdata) + currentlen > _maxpayload):
            currentpart = []
            currentlen = 0
            parts.append(currentpart)
        currentpart.append(nextdata)
        currentlen += len(nextdata)
    for idx, part in enumerate(reversed(parts)):
        data = ''.join([_pack('>B', _fmversion)] + part)
        keys['dump%i' % idx] = base85.b85encode(data)
    return keys

def pushmarker(repo, key, old, new):
    """Push markers over pushkey"""
    if not key.startswith('dump'):
        repo.ui.warn(_('unknown key: %r') % key)
        return 0
    if old:
        repo.ui.warn(_('unexpected old value') % key)
        return 0
    data = base85.b85decode(new)
    lock = repo.lock()
    try:
        tr = repo.transaction('pushkey: obsolete markers')
        try:
            repo.obsstore.mergemarkers(tr, data)
            tr.close()
            return 1
        finally:
            tr.release()
    finally:
        lock.release()

def syncpush(repo, remote):
    """utility function to push bookmark to a remote

    Exist mostly to allow overridding for experimentation purpose"""
    if (_enabled and repo.obsstore and
        'obsolete' in remote.listkeys('namespaces')):
        rslts = []
        remotedata = repo.listkeys('obsolete')
        for key in sorted(remotedata, reverse=True):
            # reverse sort to ensure we end with dump0
            data = remotedata[key]
            rslts.append(remote.pushkey('obsolete', key, '', data))
        if [r for r in rslts if not r]:
            msg = _('failed to push some obsolete markers!\n')
            repo.ui.warn(msg)

def syncpull(repo, remote, gettransaction):
    """utility function to pull bookmark to a remote

    The `gettransaction` is function that return the pull transaction, creating
    one if necessary. We return the transaction to inform the calling code that
    a new transaction have been created (when applicable).

    Exists mostly to allow overridding for experimentation purpose"""
    tr = None
    if _enabled:
        repo.ui.debug('fetching remote obsolete markers\n')
        remoteobs = remote.listkeys('obsolete')
        if 'dump0' in remoteobs:
            tr = gettransaction()
            for key in sorted(remoteobs, reverse=True):
                if key.startswith('dump'):
                    data = base85.b85decode(remoteobs[key])
                    repo.obsstore.mergemarkers(tr, data)
            repo.invalidatevolatilesets()
    return tr

def allmarkers(repo):
    """all obsolete markers known in a repository"""
    for markerdata in repo.obsstore:
        yield marker(repo, markerdata)

def precursormarkers(ctx):
    """obsolete marker marking this changeset as a successors"""
    for data in ctx._repo.obsstore.precursors.get(ctx.node(), ()):
        yield marker(ctx._repo, data)

def successormarkers(ctx):
    """obsolete marker making this changeset obsolete"""
    for data in ctx._repo.obsstore.successors.get(ctx.node(), ()):
        yield marker(ctx._repo, data)

def allsuccessors(obsstore, nodes, ignoreflags=0):
    """Yield node for every successor of <nodes>.

    Some successors may be unknown locally.

    This is a linear yield unsuited to detecting split changesets."""
    remaining = set(nodes)
    seen = set(remaining)
    while remaining:
        current = remaining.pop()
        yield current
        for mark in obsstore.successors.get(current, ()):
            # ignore marker flagged with with specified flag
            if mark[2] & ignoreflags:
                continue
            for suc in mark[1]:
                if suc not in seen:
                    seen.add(suc)
                    remaining.add(suc)

def foreground(repo, nodes):
    """return all nodes in the "foreground" of other node

    The foreground of a revision is anything reachable using parent -> children
    or precursor -> sucessor relation. It is very similars to "descendant" but
    augmented with obsolescence information.

    Beware that possible obsolescence cycle may result if complexe situation.
    """
    repo = repo.unfiltered()
    foreground = set(repo.set('%ln::', nodes))
    if repo.obsstore:
        # We only need this complicated logic if there is obsolescence
        # XXX will probably deserve an optimised revset.
        nm = repo.changelog.nodemap
        plen = -1
        # compute the whole set of successors or descendants
        while len(foreground) != plen:
            plen = len(foreground)
            succs = set(c.node() for c in foreground)
            mutable = [c.node() for c in foreground if c.mutable()]
            succs.update(allsuccessors(repo.obsstore, mutable))
            known = (n for n in succs if n in nm)
            foreground = set(repo.set('%ln::', known))
    return set(c.node() for c in foreground)


def successorssets(repo, initialnode, cache=None):
    """Return all set of successors of initial nodes

    Successors set of changeset A are a group of revision that succeed A. It
    succeed A as a consistent whole, each revision being only partial
    replacement.  Successors set contains non-obsolete changeset only.

    In most cases a changeset A have zero (changeset pruned) or a single
    successors set that contains a single successor (changeset A replaced by
    A')

    When changeset is split, it results successors set containing more than
    a single element. Divergent rewriting will result in multiple successors
    sets.

    They are returned as a list of tuples containing all valid successors sets.

    Final successors unknown locally are considered plain prune (obsoleted
    without successors).

    The optional `cache` parameter is a dictionary that may contains
    precomputed successors sets. It is meant to reuse the computation of
    previous call to `successorssets` when multiple calls are made at the same
    time. The cache dictionary is updated in place. The caller is responsible
    for its live spawn. Code that makes multiple calls to `successorssets`
    *must* use this cache mechanism or suffer terrible performances."""

    succmarkers = repo.obsstore.successors

    # Stack of nodes we search successors sets for
    toproceed = [initialnode]
    # set version of above list for fast loop detection
    # element added to "toproceed" must be added here
    stackedset = set(toproceed)
    if cache is None:
        cache = {}

    # This while loop is the flattened version of a recursive search for
    # successors sets
    #
    # def successorssets(x):
    #    successors = directsuccessors(x)
    #    ss = [[]]
    #    for succ in directsuccessors(x):
    #        # product as in itertools cartesian product
    #        ss = product(ss, successorssets(succ))
    #    return ss
    #
    # But we can not use plain recursive calls here:
    # - that would blow the python call stack
    # - obsolescence markers may have cycles, we need to handle them.
    #
    # The `toproceed` list act as our call stack. Every node we search
    # successors set for are stacked there.
    #
    # The `stackedset` is set version of this stack used to check if a node is
    # already stacked. This check is used to detect cycles and prevent infinite
    # loop.
    #
    # successors set of all nodes are stored in the `cache` dictionary.
    #
    # After this while loop ends we use the cache to return the successors sets
    # for the node requested by the caller.
    while toproceed:
        # Every iteration tries to compute the successors sets of the topmost
        # node of the stack: CURRENT.
        #
        # There are four possible outcomes:
        #
        # 1) We already know the successors sets of CURRENT:
        #    -> mission accomplished, pop it from the stack.
        # 2) Node is not obsolete:
        #    -> the node is its own successors sets. Add it to the cache.
        # 3) We do not know successors set of direct successors of CURRENT:
        #    -> We add those successors to the stack.
        # 4) We know successors sets of all direct successors of CURRENT:
        #    -> We can compute CURRENT successors set and add it to the
        #       cache.
        #
        current = toproceed[-1]
        if current in cache:
            # case (1): We already know the successors sets
            stackedset.remove(toproceed.pop())
        elif current not in succmarkers:
            # case (2): The node is not obsolete.
            if current in repo:
                # We have a valid last successors.
                cache[current] = [(current,)]
            else:
                # Final obsolete version is unknown locally.
                # Do not count that as a valid successors
                cache[current] = []
        else:
            # cases (3) and (4)
            #
            # We proceed in two phases. Phase 1 aims to distinguish case (3)
            # from case (4):
            #
            #     For each direct successors of CURRENT, we check whether its
            #     successors sets are known. If they are not, we stack the
            #     unknown node and proceed to the next iteration of the while
            #     loop. (case 3)
            #
            #     During this step, we may detect obsolescence cycles: a node
            #     with unknown successors sets but already in the call stack.
            #     In such a situation, we arbitrary set the successors sets of
            #     the node to nothing (node pruned) to break the cycle.
            #
            #     If no break was encountered we proceed to phase 2.
            #
            # Phase 2 computes successors sets of CURRENT (case 4); see details
            # in phase 2 itself.
            #
            # Note the two levels of iteration in each phase.
            # - The first one handles obsolescence markers using CURRENT as
            #   precursor (successors markers of CURRENT).
            #
            #   Having multiple entry here means divergence.
            #
            # - The second one handles successors defined in each marker.
            #
            #   Having none means pruned node, multiple successors means split,
            #   single successors are standard replacement.
            #
            for mark in sorted(succmarkers[current]):
                for suc in mark[1]:
                    if suc not in cache:
                        if suc in stackedset:
                            # cycle breaking
                            cache[suc] = []
                        else:
                            # case (3) If we have not computed successors sets
                            # of one of those successors we add it to the
                            # `toproceed` stack and stop all work for this
                            # iteration.
                            toproceed.append(suc)
                            stackedset.add(suc)
                            break
                else:
                    continue
                break
            else:
                # case (4): we know all successors sets of all direct
                # successors
                #
                # Successors set contributed by each marker depends on the
                # successors sets of all its "successors" node.
                #
                # Each different marker is a divergence in the obsolescence
                # history. It contributes successors sets distinct from other
                # markers.
                #
                # Within a marker, a successor may have divergent successors
                # sets. In such a case, the marker will contribute multiple
                # divergent successors sets. If multiple successors have
                # divergent successors sets, a cartesian product is used.
                #
                # At the end we post-process successors sets to remove
                # duplicated entry and successors set that are strict subset of
                # another one.
                succssets = []
                for mark in sorted(succmarkers[current]):
                    # successors sets contributed by this marker
                    markss = [[]]
                    for suc in mark[1]:
                        # cardinal product with previous successors
                        productresult = []
                        for prefix in markss:
                            for suffix in cache[suc]:
                                newss = list(prefix)
                                for part in suffix:
                                    # do not duplicated entry in successors set
                                    # first entry wins.
                                    if part not in newss:
                                        newss.append(part)
                                productresult.append(newss)
                        markss = productresult
                    succssets.extend(markss)
                # remove duplicated and subset
                seen = []
                final = []
                candidate = sorted(((set(s), s) for s in succssets if s),
                                   key=lambda x: len(x[1]), reverse=True)
                for setversion, listversion in candidate:
                    for seenset in seen:
                        if setversion.issubset(seenset):
                            break
                    else:
                        final.append(listversion)
                        seen.append(setversion)
                final.reverse() # put small successors set first
                cache[current] = final
    return cache[initialnode]

def _knownrevs(repo, nodes):
    """yield revision numbers of known nodes passed in parameters

    Unknown revisions are silently ignored."""
    torev = repo.changelog.nodemap.get
    for n in nodes:
        rev = torev(n)
        if rev is not None:
            yield rev

# mapping of 'set-name' -> <function to compute this set>
cachefuncs = {}
def cachefor(name):
    """Decorator to register a function as computing the cache for a set"""
    def decorator(func):
        assert name not in cachefuncs
        cachefuncs[name] = func
        return func
    return decorator

def getrevs(repo, name):
    """Return the set of revision that belong to the <name> set

    Such access may compute the set and cache it for future use"""
    repo = repo.unfiltered()
    if not repo.obsstore:
        return ()
    if name not in repo.obsstore.caches:
        repo.obsstore.caches[name] = cachefuncs[name](repo)
    return repo.obsstore.caches[name]

# To be simple we need to invalidate obsolescence cache when:
#
# - new changeset is added:
# - public phase is changed
# - obsolescence marker are added
# - strip is used a repo
def clearobscaches(repo):
    """Remove all obsolescence related cache from a repo

    This remove all cache in obsstore is the obsstore already exist on the
    repo.

    (We could be smarter here given the exact event that trigger the cache
    clearing)"""
    # only clear cache is there is obsstore data in this repo
    if 'obsstore' in repo._filecache:
        repo.obsstore.caches.clear()

@cachefor('obsolete')
def _computeobsoleteset(repo):
    """the set of obsolete revisions"""
    obs = set()
    getrev = repo.changelog.nodemap.get
    getphase = repo._phasecache.phase
    for node in repo.obsstore.successors:
        rev = getrev(node)
        if rev is not None and getphase(repo, rev):
            obs.add(rev)
    return obs

@cachefor('unstable')
def _computeunstableset(repo):
    """the set of non obsolete revisions with obsolete parents"""
    # revset is not efficient enough here
    # we do (obsolete()::) - obsolete() by hand
    obs = getrevs(repo, 'obsolete')
    if not obs:
        return set()
    cl = repo.changelog
    return set(r for r in cl.descendants(obs) if r not in obs)

@cachefor('suspended')
def _computesuspendedset(repo):
    """the set of obsolete parents with non obsolete descendants"""
    suspended = repo.changelog.ancestors(getrevs(repo, 'unstable'))
    return set(r for r in getrevs(repo, 'obsolete') if r in suspended)

@cachefor('extinct')
def _computeextinctset(repo):
    """the set of obsolete parents without non obsolete descendants"""
    return getrevs(repo, 'obsolete') - getrevs(repo, 'suspended')


@cachefor('bumped')
def _computebumpedset(repo):
    """the set of revs trying to obsolete public revisions"""
    # get all possible bumped changesets
    tonode = repo.changelog.node
    publicnodes = (tonode(r) for r in repo.revs('public()'))
    successors = allsuccessors(repo.obsstore, publicnodes,
                               ignoreflags=bumpedfix)
    # revision public or already obsolete don't count as bumped
    query = '%ld - obsolete() - public()'
    return set(repo.revs(query, _knownrevs(repo, successors)))

@cachefor('divergent')
def _computedivergentset(repo):
    """the set of rev that compete to be the final successors of some revision.
    """
    divergent = set()
    obsstore = repo.obsstore
    newermap = {}
    for ctx in repo.set('(not public()) - obsolete()'):
        mark = obsstore.precursors.get(ctx.node(), ())
        toprocess = set(mark)
        while toprocess:
            prec = toprocess.pop()[0]
            if prec not in newermap:
                successorssets(repo, prec, newermap)
            newer = [n for n in newermap[prec] if n]
            if len(newer) > 1:
                divergent.add(ctx.rev())
                break
            toprocess.update(obsstore.precursors.get(prec, ()))
    return divergent


def createmarkers(repo, relations, flag=0, metadata=None):
    """Add obsolete markers between changesets in a repo

    <relations> must be an iterable of (<old>, (<new>, ...)) tuple.
    `old` and `news` are changectx.

    Trying to obsolete a public changeset will raise an exception.

    Current user and date are used except if specified otherwise in the
    metadata attribute.

    This function operates within a transaction of its own, but does
    not take any lock on the repo.
    """
    # prepare metadata
    if metadata is None:
        metadata = {}
    if 'date' not in metadata:
        metadata['date'] = '%i %i' % util.makedate()
    if 'user' not in metadata:
        metadata['user'] = repo.ui.username()
    tr = repo.transaction('add-obsolescence-marker')
    try:
        for prec, sucs in relations:
            if not prec.mutable():
                raise util.Abort("cannot obsolete immutable changeset: %s"
                                 % prec)
            nprec = prec.node()
            nsucs = tuple(s.node() for s in sucs)
            if nprec in nsucs:
                raise util.Abort("changeset %s cannot obsolete itself" % prec)
            repo.obsstore.create(tr, nprec, nsucs, flag, metadata)
            repo.filteredrevcache.clear()
        tr.close()
    finally:
        tr.release()
