# repository.py - Interfaces and base classes for repositories and peers.
# coding: utf-8
#
# Copyright 2017 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from ..i18n import _
from .. import error
from . import util as interfaceutil

# Local repository feature string.

# Revlogs are being used for file storage.
REPO_FEATURE_REVLOG_FILE_STORAGE = b'revlogfilestorage'
# The storage part of the repository is shared from an external source.
REPO_FEATURE_SHARED_STORAGE = b'sharedstore'
# LFS supported for backing file storage.
REPO_FEATURE_LFS = b'lfs'
# Repository supports being stream cloned.
REPO_FEATURE_STREAM_CLONE = b'streamclone'
# Repository supports (at least) some sidedata to be stored
REPO_FEATURE_SIDE_DATA = b'side-data'
# Files storage may lack data for all ancestors.
REPO_FEATURE_SHALLOW_FILE_STORAGE = b'shallowfilestorage'

REVISION_FLAG_CENSORED = 1 << 15
REVISION_FLAG_ELLIPSIS = 1 << 14
REVISION_FLAG_EXTSTORED = 1 << 13
REVISION_FLAG_HASCOPIESINFO = 1 << 12

REVISION_FLAGS_KNOWN = (
    REVISION_FLAG_CENSORED
    | REVISION_FLAG_ELLIPSIS
    | REVISION_FLAG_EXTSTORED
    | REVISION_FLAG_HASCOPIESINFO
)

CG_DELTAMODE_STD = b'default'
CG_DELTAMODE_PREV = b'previous'
CG_DELTAMODE_FULL = b'fulltext'
CG_DELTAMODE_P1 = b'p1'


## Cache related constants:
#
# Used to control which cache should be warmed in a repo.updatecaches(…) call.

# Warm branchmaps of all known repoview's filter-level
CACHE_BRANCHMAP_ALL = b"branchmap-all"
# Warm branchmaps of repoview's filter-level used by server
CACHE_BRANCHMAP_SERVED = b"branchmap-served"
# Warm internal changelog cache (eg: persistent nodemap)
CACHE_CHANGELOG_CACHE = b"changelog-cache"
# Warm full manifest cache
CACHE_FULL_MANIFEST = b"full-manifest"
# Warm file-node-tags cache
CACHE_FILE_NODE_TAGS = b"file-node-tags"
# Warm internal manifestlog cache (eg: persistent nodemap)
CACHE_MANIFESTLOG_CACHE = b"manifestlog-cache"
# Warn rev branch cache
CACHE_REV_BRANCH = b"rev-branch-cache"
# Warm tags' cache for default repoview'
CACHE_TAGS_DEFAULT = b"tags-default"
# Warm tags' cache for  repoview's filter-level used by server
CACHE_TAGS_SERVED = b"tags-served"

# the cache to warm by default after a simple transaction
# (this is a mutable set to let extension update it)
CACHES_DEFAULT = {
    CACHE_BRANCHMAP_SERVED,
}

# the caches to warm when warming all of them
# (this is a mutable set to let extension update it)
CACHES_ALL = {
    CACHE_BRANCHMAP_SERVED,
    CACHE_BRANCHMAP_ALL,
    CACHE_CHANGELOG_CACHE,
    CACHE_FILE_NODE_TAGS,
    CACHE_FULL_MANIFEST,
    CACHE_MANIFESTLOG_CACHE,
    CACHE_TAGS_DEFAULT,
    CACHE_TAGS_SERVED,
}

# the cache to warm by default on simple call
# (this is a mutable set to let extension update it)
CACHES_POST_CLONE = CACHES_ALL.copy()
CACHES_POST_CLONE.discard(CACHE_FILE_NODE_TAGS)


class ipeerconnection(interfaceutil.Interface):
    """Represents a "connection" to a repository.

    This is the base interface for representing a connection to a repository.
    It holds basic properties and methods applicable to all peer types.

    This is not a complete interface definition and should not be used
    outside of this module.
    """

    ui = interfaceutil.Attribute("""ui.ui instance""")

    def url():
        """Returns a URL string representing this peer.

        Currently, implementations expose the raw URL used to construct the
        instance. It may contain credentials as part of the URL. The
        expectations of the value aren't well-defined and this could lead to
        data leakage.

        TODO audit/clean consumers and more clearly define the contents of this
        value.
        """

    def local():
        """Returns a local repository instance.

        If the peer represents a local repository, returns an object that
        can be used to interface with it. Otherwise returns ``None``.
        """

    def peer():
        """Returns an object conforming to this interface.

        Most implementations will ``return self``.
        """

    def canpush():
        """Returns a boolean indicating if this peer can be pushed to."""

    def close():
        """Close the connection to this peer.

        This is called when the peer will no longer be used. Resources
        associated with the peer should be cleaned up.
        """


class ipeercapabilities(interfaceutil.Interface):
    """Peer sub-interface related to capabilities."""

    def capable(name):
        """Determine support for a named capability.

        Returns ``False`` if capability not supported.

        Returns ``True`` if boolean capability is supported. Returns a string
        if capability support is non-boolean.

        Capability strings may or may not map to wire protocol capabilities.
        """

    def requirecap(name, purpose):
        """Require a capability to be present.

        Raises a ``CapabilityError`` if the capability isn't present.
        """


class ipeercommands(interfaceutil.Interface):
    """Client-side interface for communicating over the wire protocol.

    This interface is used as a gateway to the Mercurial wire protocol.
    methods commonly call wire protocol commands of the same name.
    """

    def branchmap():
        """Obtain heads in named branches.

        Returns a dict mapping branch name to an iterable of nodes that are
        heads on that branch.
        """

    def capabilities():
        """Obtain capabilities of the peer.

        Returns a set of string capabilities.
        """

    def clonebundles():
        """Obtains the clone bundles manifest for the repo.

        Returns the manifest as unparsed bytes.
        """

    def debugwireargs(one, two, three=None, four=None, five=None):
        """Used to facilitate debugging of arguments passed over the wire."""

    def getbundle(source, **kwargs):
        """Obtain remote repository data as a bundle.

        This command is how the bulk of repository data is transferred from
        the peer to the local repository

        Returns a generator of bundle data.
        """

    def heads():
        """Determine all known head revisions in the peer.

        Returns an iterable of binary nodes.
        """

    def known(nodes):
        """Determine whether multiple nodes are known.

        Accepts an iterable of nodes whose presence to check for.

        Returns an iterable of booleans indicating of the corresponding node
        at that index is known to the peer.
        """

    def listkeys(namespace):
        """Obtain all keys in a pushkey namespace.

        Returns an iterable of key names.
        """

    def lookup(key):
        """Resolve a value to a known revision.

        Returns a binary node of the resolved revision on success.
        """

    def pushkey(namespace, key, old, new):
        """Set a value using the ``pushkey`` protocol.

        Arguments correspond to the pushkey namespace and key to operate on and
        the old and new values for that key.

        Returns a string with the peer result. The value inside varies by the
        namespace.
        """

    def stream_out():
        """Obtain streaming clone data.

        Successful result should be a generator of data chunks.
        """

    def unbundle(bundle, heads, url):
        """Transfer repository data to the peer.

        This is how the bulk of data during a push is transferred.

        Returns the integer number of heads added to the peer.
        """


class ipeerlegacycommands(interfaceutil.Interface):
    """Interface for implementing support for legacy wire protocol commands.

    Wire protocol commands transition to legacy status when they are no longer
    used by modern clients. To facilitate identifying which commands are
    legacy, the interfaces are split.
    """

    def between(pairs):
        """Obtain nodes between pairs of nodes.

        ``pairs`` is an iterable of node pairs.

        Returns an iterable of iterables of nodes corresponding to each
        requested pair.
        """

    def branches(nodes):
        """Obtain ancestor changesets of specific nodes back to a branch point.

        For each requested node, the peer finds the first ancestor node that is
        a DAG root or is a merge.

        Returns an iterable of iterables with the resolved values for each node.
        """

    def changegroup(nodes, source):
        """Obtain a changegroup with data for descendants of specified nodes."""

    def changegroupsubset(bases, heads, source):
        pass


class ipeercommandexecutor(interfaceutil.Interface):
    """Represents a mechanism to execute remote commands.

    This is the primary interface for requesting that wire protocol commands
    be executed. Instances of this interface are active in a context manager
    and have a well-defined lifetime. When the context manager exits, all
    outstanding requests are waited on.
    """

    def callcommand(name, args):
        """Request that a named command be executed.

        Receives the command name and a dictionary of command arguments.

        Returns a ``concurrent.futures.Future`` that will resolve to the
        result of that command request. That exact value is left up to
        the implementation and possibly varies by command.

        Not all commands can coexist with other commands in an executor
        instance: it depends on the underlying wire protocol transport being
        used and the command itself.

        Implementations MAY call ``sendcommands()`` automatically if the
        requested command can not coexist with other commands in this executor.

        Implementations MAY call ``sendcommands()`` automatically when the
        future's ``result()`` is called. So, consumers using multiple
        commands with an executor MUST ensure that ``result()`` is not called
        until all command requests have been issued.
        """

    def sendcommands():
        """Trigger submission of queued command requests.

        Not all transports submit commands as soon as they are requested to
        run. When called, this method forces queued command requests to be
        issued. It will no-op if all commands have already been sent.

        When called, no more new commands may be issued with this executor.
        """

    def close():
        """Signal that this command request is finished.

        When called, no more new commands may be issued. All outstanding
        commands that have previously been issued are waited on before
        returning. This not only includes waiting for the futures to resolve,
        but also waiting for all response data to arrive. In other words,
        calling this waits for all on-wire state for issued command requests
        to finish.

        When used as a context manager, this method is called when exiting the
        context manager.

        This method may call ``sendcommands()`` if there are buffered commands.
        """


class ipeerrequests(interfaceutil.Interface):
    """Interface for executing commands on a peer."""

    limitedarguments = interfaceutil.Attribute(
        """True if the peer cannot receive large argument value for commands."""
    )

    def commandexecutor():
        """A context manager that resolves to an ipeercommandexecutor.

        The object this resolves to can be used to issue command requests
        to the peer.

        Callers should call its ``callcommand`` method to issue command
        requests.

        A new executor should be obtained for each distinct set of commands
        (possibly just a single command) that the consumer wants to execute
        as part of a single operation or round trip. This is because some
        peers are half-duplex and/or don't support persistent connections.
        e.g. in the case of HTTP peers, commands sent to an executor represent
        a single HTTP request. While some peers may support multiple command
        sends over the wire per executor, consumers need to code to the least
        capable peer. So it should be assumed that command executors buffer
        called commands until they are told to send them and that each
        command executor could result in a new connection or wire-level request
        being issued.
        """


class ipeerbase(ipeerconnection, ipeercapabilities, ipeerrequests):
    """Unified interface for peer repositories.

    All peer instances must conform to this interface.
    """


class ipeerv2(ipeerconnection, ipeercapabilities, ipeerrequests):
    """Unified peer interface for wire protocol version 2 peers."""

    apidescriptor = interfaceutil.Attribute(
        """Data structure holding description of server API."""
    )


@interfaceutil.implementer(ipeerbase)
class peer(object):
    """Base class for peer repositories."""

    limitedarguments = False

    def capable(self, name):
        caps = self.capabilities()
        if name in caps:
            return True

        name = b'%s=' % name
        for cap in caps:
            if cap.startswith(name):
                return cap[len(name) :]

        return False

    def requirecap(self, name, purpose):
        if self.capable(name):
            return

        raise error.CapabilityError(
            _(
                b'cannot %s; remote repository does not support the '
                b'\'%s\' capability'
            )
            % (purpose, name)
        )


class iverifyproblem(interfaceutil.Interface):
    """Represents a problem with the integrity of the repository.

    Instances of this interface are emitted to describe an integrity issue
    with a repository (e.g. corrupt storage, missing data, etc).

    Instances are essentially messages associated with severity.
    """

    warning = interfaceutil.Attribute(
        """Message indicating a non-fatal problem."""
    )

    error = interfaceutil.Attribute("""Message indicating a fatal problem.""")

    node = interfaceutil.Attribute(
        """Revision encountering the problem.

        ``None`` means the problem doesn't apply to a single revision.
        """
    )


class irevisiondelta(interfaceutil.Interface):
    """Represents a delta between one revision and another.

    Instances convey enough information to allow a revision to be exchanged
    with another repository.

    Instances represent the fulltext revision data or a delta against
    another revision. Therefore the ``revision`` and ``delta`` attributes
    are mutually exclusive.

    Typically used for changegroup generation.
    """

    node = interfaceutil.Attribute("""20 byte node of this revision.""")

    p1node = interfaceutil.Attribute(
        """20 byte node of 1st parent of this revision."""
    )

    p2node = interfaceutil.Attribute(
        """20 byte node of 2nd parent of this revision."""
    )

    linknode = interfaceutil.Attribute(
        """20 byte node of the changelog revision this node is linked to."""
    )

    flags = interfaceutil.Attribute(
        """2 bytes of integer flags that apply to this revision.

        This is a bitwise composition of the ``REVISION_FLAG_*`` constants.
        """
    )

    basenode = interfaceutil.Attribute(
        """20 byte node of the revision this data is a delta against.

        ``nullid`` indicates that the revision is a full revision and not
        a delta.
        """
    )

    baserevisionsize = interfaceutil.Attribute(
        """Size of base revision this delta is against.

        May be ``None`` if ``basenode`` is ``nullid``.
        """
    )

    revision = interfaceutil.Attribute(
        """Raw fulltext of revision data for this node."""
    )

    delta = interfaceutil.Attribute(
        """Delta between ``basenode`` and ``node``.

        Stored in the bdiff delta format.
        """
    )

    sidedata = interfaceutil.Attribute(
        """Raw sidedata bytes for the given revision."""
    )

    protocol_flags = interfaceutil.Attribute(
        """Single byte of integer flags that can influence the protocol.

        This is a bitwise composition of the ``storageutil.CG_FLAG*`` constants.
        """
    )


class ifilerevisionssequence(interfaceutil.Interface):
    """Contains index data for all revisions of a file.

    Types implementing this behave like lists of tuples. The index
    in the list corresponds to the revision number. The values contain
    index metadata.

    The *null* revision (revision number -1) is always the last item
    in the index.
    """

    def __len__():
        """The total number of revisions."""

    def __getitem__(rev):
        """Returns the object having a specific revision number.

        Returns an 8-tuple with the following fields:

        offset+flags
           Contains the offset and flags for the revision. 64-bit unsigned
           integer where first 6 bytes are the offset and the next 2 bytes
           are flags. The offset can be 0 if it is not used by the store.
        compressed size
            Size of the revision data in the store. It can be 0 if it isn't
            needed by the store.
        uncompressed size
            Fulltext size. It can be 0 if it isn't needed by the store.
        base revision
            Revision number of revision the delta for storage is encoded
            against. -1 indicates not encoded against a base revision.
        link revision
            Revision number of changelog revision this entry is related to.
        p1 revision
            Revision number of 1st parent. -1 if no 1st parent.
        p2 revision
            Revision number of 2nd parent. -1 if no 1st parent.
        node
            Binary node value for this revision number.

        Negative values should index off the end of the sequence. ``-1``
        should return the null revision. ``-2`` should return the most
        recent revision.
        """

    def __contains__(rev):
        """Whether a revision number exists."""

    def insert(self, i, entry):
        """Add an item to the index at specific revision."""


class ifileindex(interfaceutil.Interface):
    """Storage interface for index data of a single file.

    File storage data is divided into index metadata and data storage.
    This interface defines the index portion of the interface.

    The index logically consists of:

    * A mapping between revision numbers and nodes.
    * DAG data (storing and querying the relationship between nodes).
    * Metadata to facilitate storage.
    """

    nullid = interfaceutil.Attribute(
        """node for the null revision for use as delta base."""
    )

    def __len__():
        """Obtain the number of revisions stored for this file."""

    def __iter__():
        """Iterate over revision numbers for this file."""

    def hasnode(node):
        """Returns a bool indicating if a node is known to this store.

        Implementations must only return True for full, binary node values:
        hex nodes, revision numbers, and partial node matches must be
        rejected.

        The null node is never present.
        """

    def revs(start=0, stop=None):
        """Iterate over revision numbers for this file, with control."""

    def parents(node):
        """Returns a 2-tuple of parent nodes for a revision.

        Values will be ``nullid`` if the parent is empty.
        """

    def parentrevs(rev):
        """Like parents() but operates on revision numbers."""

    def rev(node):
        """Obtain the revision number given a node.

        Raises ``error.LookupError`` if the node is not known.
        """

    def node(rev):
        """Obtain the node value given a revision number.

        Raises ``IndexError`` if the node is not known.
        """

    def lookup(node):
        """Attempt to resolve a value to a node.

        Value can be a binary node, hex node, revision number, or a string
        that can be converted to an integer.

        Raises ``error.LookupError`` if a node could not be resolved.
        """

    def linkrev(rev):
        """Obtain the changeset revision number a revision is linked to."""

    def iscensored(rev):
        """Return whether a revision's content has been censored."""

    def commonancestorsheads(node1, node2):
        """Obtain an iterable of nodes containing heads of common ancestors.

        See ``ancestor.commonancestorsheads()``.
        """

    def descendants(revs):
        """Obtain descendant revision numbers for a set of revision numbers.

        If ``nullrev`` is in the set, this is equivalent to ``revs()``.
        """

    def heads(start=None, stop=None):
        """Obtain a list of nodes that are DAG heads, with control.

        The set of revisions examined can be limited by specifying
        ``start`` and ``stop``. ``start`` is a node. ``stop`` is an
        iterable of nodes. DAG traversal starts at earlier revision
        ``start`` and iterates forward until any node in ``stop`` is
        encountered.
        """

    def children(node):
        """Obtain nodes that are children of a node.

        Returns a list of nodes.
        """


class ifiledata(interfaceutil.Interface):
    """Storage interface for data storage of a specific file.

    This complements ``ifileindex`` and provides an interface for accessing
    data for a tracked file.
    """

    def size(rev):
        """Obtain the fulltext size of file data.

        Any metadata is excluded from size measurements.
        """

    def revision(node, raw=False):
        """Obtain fulltext data for a node.

        By default, any storage transformations are applied before the data
        is returned. If ``raw`` is True, non-raw storage transformations
        are not applied.

        The fulltext data may contain a header containing metadata. Most
        consumers should use ``read()`` to obtain the actual file data.
        """

    def rawdata(node):
        """Obtain raw data for a node."""

    def read(node):
        """Resolve file fulltext data.

        This is similar to ``revision()`` except any metadata in the data
        headers is stripped.
        """

    def renamed(node):
        """Obtain copy metadata for a node.

        Returns ``False`` if no copy metadata is stored or a 2-tuple of
        (path, node) from which this revision was copied.
        """

    def cmp(node, fulltext):
        """Compare fulltext to another revision.

        Returns True if the fulltext is different from what is stored.

        This takes copy metadata into account.

        TODO better document the copy metadata and censoring logic.
        """

    def emitrevisions(
        nodes,
        nodesorder=None,
        revisiondata=False,
        assumehaveparentrevisions=False,
        deltamode=CG_DELTAMODE_STD,
    ):
        """Produce ``irevisiondelta`` for revisions.

        Given an iterable of nodes, emits objects conforming to the
        ``irevisiondelta`` interface that describe revisions in storage.

        This method is a generator.

        The input nodes may be unordered. Implementations must ensure that a
        node's parents are emitted before the node itself. Transitively, this
        means that a node may only be emitted once all its ancestors in
        ``nodes`` have also been emitted.

        By default, emits "index" data (the ``node``, ``p1node``, and
        ``p2node`` attributes). If ``revisiondata`` is set, revision data
        will also be present on the emitted objects.

        With default argument values, implementations can choose to emit
        either fulltext revision data or a delta. When emitting deltas,
        implementations must consider whether the delta's base revision
        fulltext is available to the receiver.

        The base revision fulltext is guaranteed to be available if any of
        the following are met:

        * Its fulltext revision was emitted by this method call.
        * A delta for that revision was emitted by this method call.
        * ``assumehaveparentrevisions`` is True and the base revision is a
          parent of the node.

        ``nodesorder`` can be used to control the order that revisions are
        emitted. By default, revisions can be reordered as long as they are
        in DAG topological order (see above). If the value is ``nodes``,
        the iteration order from ``nodes`` should be used. If the value is
        ``storage``, then the native order from the backing storage layer
        is used. (Not all storage layers will have strong ordering and behavior
        of this mode is storage-dependent.) ``nodes`` ordering can force
        revisions to be emitted before their ancestors, so consumers should
        use it with care.

        The ``linknode`` attribute on the returned ``irevisiondelta`` may not
        be set and it is the caller's responsibility to resolve it, if needed.

        If ``deltamode`` is CG_DELTAMODE_PREV and revision data is requested,
        all revision data should be emitted as deltas against the revision
        emitted just prior. The initial revision should be a delta against its
        1st parent.
        """


class ifilemutation(interfaceutil.Interface):
    """Storage interface for mutation events of a tracked file."""

    def add(filedata, meta, transaction, linkrev, p1, p2):
        """Add a new revision to the store.

        Takes file data, dictionary of metadata, a transaction, linkrev,
        and parent nodes.

        Returns the node that was added.

        May no-op if a revision matching the supplied data is already stored.
        """

    def addrevision(
        revisiondata,
        transaction,
        linkrev,
        p1,
        p2,
        node=None,
        flags=0,
        cachedelta=None,
    ):
        """Add a new revision to the store and return its number.

        This is similar to ``add()`` except it operates at a lower level.

        The data passed in already contains a metadata header, if any.

        ``node`` and ``flags`` can be used to define the expected node and
        the flags to use with storage. ``flags`` is a bitwise value composed
        of the various ``REVISION_FLAG_*`` constants.

        ``add()`` is usually called when adding files from e.g. the working
        directory. ``addrevision()`` is often called by ``add()`` and for
        scenarios where revision data has already been computed, such as when
        applying raw data from a peer repo.
        """

    def addgroup(
        deltas,
        linkmapper,
        transaction,
        addrevisioncb=None,
        duplicaterevisioncb=None,
        maybemissingparents=False,
    ):
        """Process a series of deltas for storage.

        ``deltas`` is an iterable of 7-tuples of
        (node, p1, p2, linknode, deltabase, delta, flags) defining revisions
        to add.

        The ``delta`` field contains ``mpatch`` data to apply to a base
        revision, identified by ``deltabase``. The base node can be
        ``nullid``, in which case the header from the delta can be ignored
        and the delta used as the fulltext.

        ``alwayscache`` instructs the lower layers to cache the content of the
        newly added revision, even if it needs to be explicitly computed.
        This used to be the default when ``addrevisioncb`` was provided up to
        Mercurial 5.8.

        ``addrevisioncb`` should be called for each new rev as it is committed.
        ``duplicaterevisioncb`` should be called for all revs with a
        pre-existing node.

        ``maybemissingparents`` is a bool indicating whether the incoming
        data may reference parents/ancestor revisions that aren't present.
        This flag is set when receiving data into a "shallow" store that
        doesn't hold all history.

        Returns a list of nodes that were processed. A node will be in the list
        even if it existed in the store previously.
        """

    def censorrevision(tr, node, tombstone=b''):
        """Remove the content of a single revision.

        The specified ``node`` will have its content purged from storage.
        Future attempts to access the revision data for this node will
        result in failure.

        A ``tombstone`` message can optionally be stored. This message may be
        displayed to users when they attempt to access the missing revision
        data.

        Storage backends may have stored deltas against the previous content
        in this revision. As part of censoring a revision, these storage
        backends are expected to rewrite any internally stored deltas such
        that they no longer reference the deleted content.
        """

    def getstrippoint(minlink):
        """Find the minimum revision that must be stripped to strip a linkrev.

        Returns a 2-tuple containing the minimum revision number and a set
        of all revisions numbers that would be broken by this strip.

        TODO this is highly revlog centric and should be abstracted into
        a higher-level deletion API. ``repair.strip()`` relies on this.
        """

    def strip(minlink, transaction):
        """Remove storage of items starting at a linkrev.

        This uses ``getstrippoint()`` to determine the first node to remove.
        Then it effectively truncates storage for all revisions after that.

        TODO this is highly revlog centric and should be abstracted into a
        higher-level deletion API.
        """


class ifilestorage(ifileindex, ifiledata, ifilemutation):
    """Complete storage interface for a single tracked file."""

    def files():
        """Obtain paths that are backing storage for this file.

        TODO this is used heavily by verify code and there should probably
        be a better API for that.
        """

    def storageinfo(
        exclusivefiles=False,
        sharedfiles=False,
        revisionscount=False,
        trackedsize=False,
        storedsize=False,
    ):
        """Obtain information about storage for this file's data.

        Returns a dict describing storage for this tracked path. The keys
        in the dict map to arguments of the same. The arguments are bools
        indicating whether to calculate and obtain that data.

        exclusivefiles
           Iterable of (vfs, path) describing files that are exclusively
           used to back storage for this tracked path.

        sharedfiles
           Iterable of (vfs, path) describing files that are used to back
           storage for this tracked path. Those files may also provide storage
           for other stored entities.

        revisionscount
           Number of revisions available for retrieval.

        trackedsize
           Total size in bytes of all tracked revisions. This is a sum of the
           length of the fulltext of all revisions.

        storedsize
           Total size in bytes used to store data for all tracked revisions.
           This is commonly less than ``trackedsize`` due to internal usage
           of deltas rather than fulltext revisions.

        Not all storage backends may support all queries are have a reasonable
        value to use. In that case, the value should be set to ``None`` and
        callers are expected to handle this special value.
        """

    def verifyintegrity(state):
        """Verifies the integrity of file storage.

        ``state`` is a dict holding state of the verifier process. It can be
        used to communicate data between invocations of multiple storage
        primitives.

        If individual revisions cannot have their revision content resolved,
        the method is expected to set the ``skipread`` key to a set of nodes
        that encountered problems.  If set, the method can also add the node(s)
        to ``safe_renamed`` in order to indicate nodes that may perform the
        rename checks with currently accessible data.

        The method yields objects conforming to the ``iverifyproblem``
        interface.
        """


class idirs(interfaceutil.Interface):
    """Interface representing a collection of directories from paths.

    This interface is essentially a derived data structure representing
    directories from a collection of paths.
    """

    def addpath(path):
        """Add a path to the collection.

        All directories in the path will be added to the collection.
        """

    def delpath(path):
        """Remove a path from the collection.

        If the removal was the last path in a particular directory, the
        directory is removed from the collection.
        """

    def __iter__():
        """Iterate over the directories in this collection of paths."""

    def __contains__(path):
        """Whether a specific directory is in this collection."""


class imanifestdict(interfaceutil.Interface):
    """Interface representing a manifest data structure.

    A manifest is effectively a dict mapping paths to entries. Each entry
    consists of a binary node and extra flags affecting that entry.
    """

    def __getitem__(path):
        """Returns the binary node value for a path in the manifest.

        Raises ``KeyError`` if the path does not exist in the manifest.

        Equivalent to ``self.find(path)[0]``.
        """

    def find(path):
        """Returns the entry for a path in the manifest.

        Returns a 2-tuple of (node, flags).

        Raises ``KeyError`` if the path does not exist in the manifest.
        """

    def __len__():
        """Return the number of entries in the manifest."""

    def __nonzero__():
        """Returns True if the manifest has entries, False otherwise."""

    __bool__ = __nonzero__

    def __setitem__(path, node):
        """Define the node value for a path in the manifest.

        If the path is already in the manifest, its flags will be copied to
        the new entry.
        """

    def __contains__(path):
        """Whether a path exists in the manifest."""

    def __delitem__(path):
        """Remove a path from the manifest.

        Raises ``KeyError`` if the path is not in the manifest.
        """

    def __iter__():
        """Iterate over paths in the manifest."""

    def iterkeys():
        """Iterate over paths in the manifest."""

    def keys():
        """Obtain a list of paths in the manifest."""

    def filesnotin(other, match=None):
        """Obtain the set of paths in this manifest but not in another.

        ``match`` is an optional matcher function to be applied to both
        manifests.

        Returns a set of paths.
        """

    def dirs():
        """Returns an object implementing the ``idirs`` interface."""

    def hasdir(dir):
        """Returns a bool indicating if a directory is in this manifest."""

    def walk(match):
        """Generator of paths in manifest satisfying a matcher.

        If the matcher has explicit files listed and they don't exist in
        the manifest, ``match.bad()`` is called for each missing file.
        """

    def diff(other, match=None, clean=False):
        """Find differences between this manifest and another.

        This manifest is compared to ``other``.

        If ``match`` is provided, the two manifests are filtered against this
        matcher and only entries satisfying the matcher are compared.

        If ``clean`` is True, unchanged files are included in the returned
        object.

        Returns a dict with paths as keys and values of 2-tuples of 2-tuples of
        the form ``((node1, flag1), (node2, flag2))`` where ``(node1, flag1)``
        represents the node and flags for this manifest and ``(node2, flag2)``
        are the same for the other manifest.
        """

    def setflag(path, flag):
        """Set the flag value for a given path.

        Raises ``KeyError`` if the path is not already in the manifest.
        """

    def get(path, default=None):
        """Obtain the node value for a path or a default value if missing."""

    def flags(path):
        """Return the flags value for a path (default: empty bytestring)."""

    def copy():
        """Return a copy of this manifest."""

    def items():
        """Returns an iterable of (path, node) for items in this manifest."""

    def iteritems():
        """Identical to items()."""

    def iterentries():
        """Returns an iterable of (path, node, flags) for this manifest.

        Similar to ``iteritems()`` except items are a 3-tuple and include
        flags.
        """

    def text():
        """Obtain the raw data representation for this manifest.

        Result is used to create a manifest revision.
        """

    def fastdelta(base, changes):
        """Obtain a delta between this manifest and another given changes.

        ``base`` in the raw data representation for another manifest.

        ``changes`` is an iterable of ``(path, to_delete)``.

        Returns a 2-tuple containing ``bytearray(self.text())`` and the
        delta between ``base`` and this manifest.

        If this manifest implementation can't support ``fastdelta()``,
        raise ``mercurial.manifest.FastdeltaUnavailable``.
        """


class imanifestrevisionbase(interfaceutil.Interface):
    """Base interface representing a single revision of a manifest.

    Should not be used as a primary interface: should always be inherited
    as part of a larger interface.
    """

    def copy():
        """Obtain a copy of this manifest instance.

        Returns an object conforming to the ``imanifestrevisionwritable``
        interface. The instance will be associated with the same
        ``imanifestlog`` collection as this instance.
        """

    def read():
        """Obtain the parsed manifest data structure.

        The returned object conforms to the ``imanifestdict`` interface.
        """


class imanifestrevisionstored(imanifestrevisionbase):
    """Interface representing a manifest revision committed to storage."""

    def node():
        """The binary node for this manifest."""

    parents = interfaceutil.Attribute(
        """List of binary nodes that are parents for this manifest revision."""
    )

    def readdelta(shallow=False):
        """Obtain the manifest data structure representing changes from parent.

        This manifest is compared to its 1st parent. A new manifest representing
        those differences is constructed.

        The returned object conforms to the ``imanifestdict`` interface.
        """

    def readfast(shallow=False):
        """Calls either ``read()`` or ``readdelta()``.

        The faster of the two options is called.
        """

    def find(key):
        """Calls self.read().find(key)``.

        Returns a 2-tuple of ``(node, flags)`` or raises ``KeyError``.
        """


class imanifestrevisionwritable(imanifestrevisionbase):
    """Interface representing a manifest revision that can be committed."""

    def write(transaction, linkrev, p1node, p2node, added, removed, match=None):
        """Add this revision to storage.

        Takes a transaction object, the changeset revision number it will
        be associated with, its parent nodes, and lists of added and
        removed paths.

        If match is provided, storage can choose not to inspect or write out
        items that do not match. Storage is still required to be able to provide
        the full manifest in the future for any directories written (these
        manifests should not be "narrowed on disk").

        Returns the binary node of the created revision.
        """


class imanifeststorage(interfaceutil.Interface):
    """Storage interface for manifest data."""

    nodeconstants = interfaceutil.Attribute(
        """nodeconstants used by the current repository."""
    )

    tree = interfaceutil.Attribute(
        """The path to the directory this manifest tracks.

        The empty bytestring represents the root manifest.
        """
    )

    index = interfaceutil.Attribute(
        """An ``ifilerevisionssequence`` instance."""
    )

    opener = interfaceutil.Attribute(
        """VFS opener to use to access underlying files used for storage.

        TODO this is revlog specific and should not be exposed.
        """
    )

    _generaldelta = interfaceutil.Attribute(
        """Whether generaldelta storage is being used.

        TODO this is revlog specific and should not be exposed.
        """
    )

    fulltextcache = interfaceutil.Attribute(
        """Dict with cache of fulltexts.

        TODO this doesn't feel appropriate for the storage interface.
        """
    )

    def __len__():
        """Obtain the number of revisions stored for this manifest."""

    def __iter__():
        """Iterate over revision numbers for this manifest."""

    def rev(node):
        """Obtain the revision number given a binary node.

        Raises ``error.LookupError`` if the node is not known.
        """

    def node(rev):
        """Obtain the node value given a revision number.

        Raises ``error.LookupError`` if the revision is not known.
        """

    def lookup(value):
        """Attempt to resolve a value to a node.

        Value can be a binary node, hex node, revision number, or a bytes
        that can be converted to an integer.

        Raises ``error.LookupError`` if a ndoe could not be resolved.
        """

    def parents(node):
        """Returns a 2-tuple of parent nodes for a node.

        Values will be ``nullid`` if the parent is empty.
        """

    def parentrevs(rev):
        """Like parents() but operates on revision numbers."""

    def linkrev(rev):
        """Obtain the changeset revision number a revision is linked to."""

    def revision(node, _df=None, raw=False):
        """Obtain fulltext data for a node."""

    def rawdata(node, _df=None):
        """Obtain raw data for a node."""

    def revdiff(rev1, rev2):
        """Obtain a delta between two revision numbers.

        The returned data is the result of ``bdiff.bdiff()`` on the raw
        revision data.
        """

    def cmp(node, fulltext):
        """Compare fulltext to another revision.

        Returns True if the fulltext is different from what is stored.
        """

    def emitrevisions(
        nodes,
        nodesorder=None,
        revisiondata=False,
        assumehaveparentrevisions=False,
    ):
        """Produce ``irevisiondelta`` describing revisions.

        See the documentation for ``ifiledata`` for more.
        """

    def addgroup(
        deltas,
        linkmapper,
        transaction,
        addrevisioncb=None,
        duplicaterevisioncb=None,
    ):
        """Process a series of deltas for storage.

        See the documentation in ``ifilemutation`` for more.
        """

    def rawsize(rev):
        """Obtain the size of tracked data.

        Is equivalent to ``len(m.rawdata(node))``.

        TODO this method is only used by upgrade code and may be removed.
        """

    def getstrippoint(minlink):
        """Find minimum revision that must be stripped to strip a linkrev.

        See the documentation in ``ifilemutation`` for more.
        """

    def strip(minlink, transaction):
        """Remove storage of items starting at a linkrev.

        See the documentation in ``ifilemutation`` for more.
        """

    def checksize():
        """Obtain the expected sizes of backing files.

        TODO this is used by verify and it should not be part of the interface.
        """

    def files():
        """Obtain paths that are backing storage for this manifest.

        TODO this is used by verify and there should probably be a better API
        for this functionality.
        """

    def deltaparent(rev):
        """Obtain the revision that a revision is delta'd against.

        TODO delta encoding is an implementation detail of storage and should
        not be exposed to the storage interface.
        """

    def clone(tr, dest, **kwargs):
        """Clone this instance to another."""

    def clearcaches(clear_persisted_data=False):
        """Clear any caches associated with this instance."""

    def dirlog(d):
        """Obtain a manifest storage instance for a tree."""

    def add(
        m, transaction, link, p1, p2, added, removed, readtree=None, match=None
    ):
        """Add a revision to storage.

        ``m`` is an object conforming to ``imanifestdict``.

        ``link`` is the linkrev revision number.

        ``p1`` and ``p2`` are the parent revision numbers.

        ``added`` and ``removed`` are iterables of added and removed paths,
        respectively.

        ``readtree`` is a function that can be used to read the child tree(s)
        when recursively writing the full tree structure when using
        treemanifets.

        ``match`` is a matcher that can be used to hint to storage that not all
        paths must be inspected; this is an optimization and can be safely
        ignored. Note that the storage must still be able to reproduce a full
        manifest including files that did not match.
        """

    def storageinfo(
        exclusivefiles=False,
        sharedfiles=False,
        revisionscount=False,
        trackedsize=False,
        storedsize=False,
    ):
        """Obtain information about storage for this manifest's data.

        See ``ifilestorage.storageinfo()`` for a description of this method.
        This one behaves the same way, except for manifest data.
        """


class imanifestlog(interfaceutil.Interface):
    """Interface representing a collection of manifest snapshots.

    Represents the root manifest in a repository.

    Also serves as a means to access nested tree manifests and to cache
    tree manifests.
    """

    nodeconstants = interfaceutil.Attribute(
        """nodeconstants used by the current repository."""
    )

    def __getitem__(node):
        """Obtain a manifest instance for a given binary node.

        Equivalent to calling ``self.get('', node)``.

        The returned object conforms to the ``imanifestrevisionstored``
        interface.
        """

    def get(tree, node, verify=True):
        """Retrieve the manifest instance for a given directory and binary node.

        ``node`` always refers to the node of the root manifest (which will be
        the only manifest if flat manifests are being used).

        If ``tree`` is the empty string, the root manifest is returned.
        Otherwise the manifest for the specified directory will be returned
        (requires tree manifests).

        If ``verify`` is True, ``LookupError`` is raised if the node is not
        known.

        The returned object conforms to the ``imanifestrevisionstored``
        interface.
        """

    def getstorage(tree):
        """Retrieve an interface to storage for a particular tree.

        If ``tree`` is the empty bytestring, storage for the root manifest will
        be returned. Otherwise storage for a tree manifest is returned.

        TODO formalize interface for returned object.
        """

    def clearcaches():
        """Clear caches associated with this collection."""

    def rev(node):
        """Obtain the revision number for a binary node.

        Raises ``error.LookupError`` if the node is not known.
        """

    def update_caches(transaction):
        """update whatever cache are relevant for the used storage."""


class ilocalrepositoryfilestorage(interfaceutil.Interface):
    """Local repository sub-interface providing access to tracked file storage.

    This interface defines how a repository accesses storage for a single
    tracked file path.
    """

    def file(f):
        """Obtain a filelog for a tracked path.

        The returned type conforms to the ``ifilestorage`` interface.
        """


class ilocalrepositorymain(interfaceutil.Interface):
    """Main interface for local repositories.

    This currently captures the reality of things - not how things should be.
    """

    nodeconstants = interfaceutil.Attribute(
        """Constant nodes matching the hash function used by the repository."""
    )
    nullid = interfaceutil.Attribute(
        """null revision for the hash function used by the repository."""
    )

    supportedformats = interfaceutil.Attribute(
        """Set of requirements that apply to stream clone.

        This is actually a class attribute and is shared among all instances.
        """
    )

    supported = interfaceutil.Attribute(
        """Set of requirements that this repo is capable of opening."""
    )

    requirements = interfaceutil.Attribute(
        """Set of requirements this repo uses."""
    )

    features = interfaceutil.Attribute(
        """Set of "features" this repository supports.

        A "feature" is a loosely-defined term. It can refer to a feature
        in the classical sense or can describe an implementation detail
        of the repository. For example, a ``readonly`` feature may denote
        the repository as read-only. Or a ``revlogfilestore`` feature may
        denote that the repository is using revlogs for file storage.

        The intent of features is to provide a machine-queryable mechanism
        for repo consumers to test for various repository characteristics.

        Features are similar to ``requirements``. The main difference is that
        requirements are stored on-disk and represent requirements to open the
        repository. Features are more run-time capabilities of the repository
        and more granular capabilities (which may be derived from requirements).
        """
    )

    filtername = interfaceutil.Attribute(
        """Name of the repoview that is active on this repo."""
    )

    wvfs = interfaceutil.Attribute(
        """VFS used to access the working directory."""
    )

    vfs = interfaceutil.Attribute(
        """VFS rooted at the .hg directory.

        Used to access repository data not in the store.
        """
    )

    svfs = interfaceutil.Attribute(
        """VFS rooted at the store.

        Used to access repository data in the store. Typically .hg/store.
        But can point elsewhere if the store is shared.
        """
    )

    root = interfaceutil.Attribute(
        """Path to the root of the working directory."""
    )

    path = interfaceutil.Attribute("""Path to the .hg directory.""")

    origroot = interfaceutil.Attribute(
        """The filesystem path that was used to construct the repo."""
    )

    auditor = interfaceutil.Attribute(
        """A pathauditor for the working directory.

        This checks if a path refers to a nested repository.

        Operates on the filesystem.
        """
    )

    nofsauditor = interfaceutil.Attribute(
        """A pathauditor for the working directory.

        This is like ``auditor`` except it doesn't do filesystem checks.
        """
    )

    baseui = interfaceutil.Attribute(
        """Original ui instance passed into constructor."""
    )

    ui = interfaceutil.Attribute("""Main ui instance for this instance.""")

    sharedpath = interfaceutil.Attribute(
        """Path to the .hg directory of the repo this repo was shared from."""
    )

    store = interfaceutil.Attribute("""A store instance.""")

    spath = interfaceutil.Attribute("""Path to the store.""")

    sjoin = interfaceutil.Attribute("""Alias to self.store.join.""")

    cachevfs = interfaceutil.Attribute(
        """A VFS used to access the cache directory.

        Typically .hg/cache.
        """
    )

    wcachevfs = interfaceutil.Attribute(
        """A VFS used to access the cache directory dedicated to working copy

        Typically .hg/wcache.
        """
    )

    filteredrevcache = interfaceutil.Attribute(
        """Holds sets of revisions to be filtered."""
    )

    names = interfaceutil.Attribute("""A ``namespaces`` instance.""")

    filecopiesmode = interfaceutil.Attribute(
        """The way files copies should be dealt with in this repo."""
    )

    def close():
        """Close the handle on this repository."""

    def peer():
        """Obtain an object conforming to the ``peer`` interface."""

    def unfiltered():
        """Obtain an unfiltered/raw view of this repo."""

    def filtered(name, visibilityexceptions=None):
        """Obtain a named view of this repository."""

    obsstore = interfaceutil.Attribute("""A store of obsolescence data.""")

    changelog = interfaceutil.Attribute("""A handle on the changelog revlog.""")

    manifestlog = interfaceutil.Attribute(
        """An instance conforming to the ``imanifestlog`` interface.

        Provides access to manifests for the repository.
        """
    )

    dirstate = interfaceutil.Attribute("""Working directory state.""")

    narrowpats = interfaceutil.Attribute(
        """Matcher patterns for this repository's narrowspec."""
    )

    def narrowmatch(match=None, includeexact=False):
        """Obtain a matcher for the narrowspec."""

    def setnarrowpats(newincludes, newexcludes):
        """Define the narrowspec for this repository."""

    def __getitem__(changeid):
        """Try to resolve a changectx."""

    def __contains__(changeid):
        """Whether a changeset exists."""

    def __nonzero__():
        """Always returns True."""
        return True

    __bool__ = __nonzero__

    def __len__():
        """Returns the number of changesets in the repo."""

    def __iter__():
        """Iterate over revisions in the changelog."""

    def revs(expr, *args):
        """Evaluate a revset.

        Emits revisions.
        """

    def set(expr, *args):
        """Evaluate a revset.

        Emits changectx instances.
        """

    def anyrevs(specs, user=False, localalias=None):
        """Find revisions matching one of the given revsets."""

    def url():
        """Returns a string representing the location of this repo."""

    def hook(name, throw=False, **args):
        """Call a hook."""

    def tags():
        """Return a mapping of tag to node."""

    def tagtype(tagname):
        """Return the type of a given tag."""

    def tagslist():
        """Return a list of tags ordered by revision."""

    def nodetags(node):
        """Return the tags associated with a node."""

    def nodebookmarks(node):
        """Return the list of bookmarks pointing to the specified node."""

    def branchmap():
        """Return a mapping of branch to heads in that branch."""

    def revbranchcache():
        pass

    def register_changeset(rev, changelogrevision):
        """Extension point for caches for new nodes.

        Multiple consumers are expected to need parts of the changelogrevision,
        so it is provided as optimization to avoid duplicate lookups. A simple
        cache would be fragile when other revisions are accessed, too."""
        pass

    def branchtip(branchtip, ignoremissing=False):
        """Return the tip node for a given branch."""

    def lookup(key):
        """Resolve the node for a revision."""

    def lookupbranch(key):
        """Look up the branch name of the given revision or branch name."""

    def known(nodes):
        """Determine whether a series of nodes is known.

        Returns a list of bools.
        """

    def local():
        """Whether the repository is local."""
        return True

    def publishing():
        """Whether the repository is a publishing repository."""

    def cancopy():
        pass

    def shared():
        """The type of shared repository or None."""

    def wjoin(f, *insidef):
        """Calls self.vfs.reljoin(self.root, f, *insidef)"""

    def setparents(p1, p2):
        """Set the parent nodes of the working directory."""

    def filectx(path, changeid=None, fileid=None):
        """Obtain a filectx for the given file revision."""

    def getcwd():
        """Obtain the current working directory from the dirstate."""

    def pathto(f, cwd=None):
        """Obtain the relative path to a file."""

    def adddatafilter(name, fltr):
        pass

    def wread(filename):
        """Read a file from wvfs, using data filters."""

    def wwrite(filename, data, flags, backgroundclose=False, **kwargs):
        """Write data to a file in the wvfs, using data filters."""

    def wwritedata(filename, data):
        """Resolve data for writing to the wvfs, using data filters."""

    def currenttransaction():
        """Obtain the current transaction instance or None."""

    def transaction(desc, report=None):
        """Open a new transaction to write to the repository."""

    def undofiles():
        """Returns a list of (vfs, path) for files to undo transactions."""

    def recover():
        """Roll back an interrupted transaction."""

    def rollback(dryrun=False, force=False):
        """Undo the last transaction.

        DANGEROUS.
        """

    def updatecaches(tr=None, full=False):
        """Warm repo caches."""

    def invalidatecaches():
        """Invalidate cached data due to the repository mutating."""

    def invalidatevolatilesets():
        pass

    def invalidatedirstate():
        """Invalidate the dirstate."""

    def invalidate(clearfilecache=False):
        pass

    def invalidateall():
        pass

    def lock(wait=True):
        """Lock the repository store and return a lock instance."""

    def wlock(wait=True):
        """Lock the non-store parts of the repository."""

    def currentwlock():
        """Return the wlock if it's held or None."""

    def checkcommitpatterns(wctx, match, status, fail):
        pass

    def commit(
        text=b'',
        user=None,
        date=None,
        match=None,
        force=False,
        editor=False,
        extra=None,
    ):
        """Add a new revision to the repository."""

    def commitctx(ctx, error=False, origctx=None):
        """Commit a commitctx instance to the repository."""

    def destroying():
        """Inform the repository that nodes are about to be destroyed."""

    def destroyed():
        """Inform the repository that nodes have been destroyed."""

    def status(
        node1=b'.',
        node2=None,
        match=None,
        ignored=False,
        clean=False,
        unknown=False,
        listsubrepos=False,
    ):
        """Convenience method to call repo[x].status()."""

    def addpostdsstatus(ps):
        pass

    def postdsstatus():
        pass

    def clearpostdsstatus():
        pass

    def heads(start=None):
        """Obtain list of nodes that are DAG heads."""

    def branchheads(branch=None, start=None, closed=False):
        pass

    def branches(nodes):
        pass

    def between(pairs):
        pass

    def checkpush(pushop):
        pass

    prepushoutgoinghooks = interfaceutil.Attribute("""util.hooks instance.""")

    def pushkey(namespace, key, old, new):
        pass

    def listkeys(namespace):
        pass

    def debugwireargs(one, two, three=None, four=None, five=None):
        pass

    def savecommitmessage(text):
        pass

    def register_sidedata_computer(
        kind, category, keys, computer, flags, replace=False
    ):
        pass

    def register_wanted_sidedata(category):
        pass


class completelocalrepository(
    ilocalrepositorymain, ilocalrepositoryfilestorage
):
    """Complete interface for a local repository."""


class iwireprotocolcommandcacher(interfaceutil.Interface):
    """Represents a caching backend for wire protocol commands.

    Wire protocol version 2 supports transparent caching of many commands.
    To leverage this caching, servers can activate objects that cache
    command responses. Objects handle both cache writing and reading.
    This interface defines how that response caching mechanism works.

    Wire protocol version 2 commands emit a series of objects that are
    serialized and sent to the client. The caching layer exists between
    the invocation of the command function and the sending of its output
    objects to an output layer.

    Instances of this interface represent a binding to a cache that
    can serve a response (in place of calling a command function) and/or
    write responses to a cache for subsequent use.

    When a command request arrives, the following happens with regards
    to this interface:

    1. The server determines whether the command request is cacheable.
    2. If it is, an instance of this interface is spawned.
    3. The cacher is activated in a context manager (``__enter__`` is called).
    4. A cache *key* for that request is derived. This will call the
       instance's ``adjustcachekeystate()`` method so the derivation
       can be influenced.
    5. The cacher is informed of the derived cache key via a call to
       ``setcachekey()``.
    6. The cacher's ``lookup()`` method is called to test for presence of
       the derived key in the cache.
    7. If ``lookup()`` returns a hit, that cached result is used in place
       of invoking the command function. ``__exit__`` is called and the instance
       is discarded.
    8. The command function is invoked.
    9. ``onobject()`` is called for each object emitted by the command
       function.
    10. After the final object is seen, ``onfinished()`` is called.
    11. ``__exit__`` is called to signal the end of use of the instance.

    Cache *key* derivation can be influenced by the instance.

    Cache keys are initially derived by a deterministic representation of
    the command request. This includes the command name, arguments, protocol
    version, etc. This initial key derivation is performed by CBOR-encoding a
    data structure and feeding that output into a hasher.

    Instances of this interface can influence this initial key derivation
    via ``adjustcachekeystate()``.

    The instance is informed of the derived cache key via a call to
    ``setcachekey()``. The instance must store the key locally so it can
    be consulted on subsequent operations that may require it.

    When constructed, the instance has access to a callable that can be used
    for encoding response objects. This callable receives as its single
    argument an object emitted by a command function. It returns an iterable
    of bytes chunks representing the encoded object. Unless the cacher is
    caching native Python objects in memory or has a way of reconstructing
    the original Python objects, implementations typically call this function
    to produce bytes from the output objects and then store those bytes in
    the cache. When it comes time to re-emit those bytes, they are wrapped
    in a ``wireprototypes.encodedresponse`` instance to tell the output
    layer that they are pre-encoded.

    When receiving the objects emitted by the command function, instances
    can choose what to do with those objects. The simplest thing to do is
    re-emit the original objects. They will be forwarded to the output
    layer and will be processed as if the cacher did not exist.

    Implementations could also choose to not emit objects - instead locally
    buffering objects or their encoded representation. They could then emit
    a single "coalesced" object when ``onfinished()`` is called. In
    this way, the implementation would function as a filtering layer of
    sorts.

    When caching objects, typically the encoded form of the object will
    be stored. Keep in mind that if the original object is forwarded to
    the output layer, it will need to be encoded there as well. For large
    output, this redundant encoding could add overhead. Implementations
    could wrap the encoded object data in ``wireprototypes.encodedresponse``
    instances to avoid this overhead.
    """

    def __enter__():
        """Marks the instance as active.

        Should return self.
        """

    def __exit__(exctype, excvalue, exctb):
        """Called when cacher is no longer used.

        This can be used by implementations to perform cleanup actions (e.g.
        disconnecting network sockets, aborting a partially cached response.
        """

    def adjustcachekeystate(state):
        """Influences cache key derivation by adjusting state to derive key.

        A dict defining the state used to derive the cache key is passed.

        Implementations can modify this dict to record additional state that
        is wanted to influence key derivation.

        Implementations are *highly* encouraged to not modify or delete
        existing keys.
        """

    def setcachekey(key):
        """Record the derived cache key for this request.

        Instances may mutate the key for internal usage, as desired. e.g.
        instances may wish to prepend the repo name, introduce path
        components for filesystem or URL addressing, etc. Behavior is up to
        the cache.

        Returns a bool indicating if the request is cacheable by this
        instance.
        """

    def lookup():
        """Attempt to resolve an entry in the cache.

        The instance is instructed to look for the cache key that it was
        informed about via the call to ``setcachekey()``.

        If there's no cache hit or the cacher doesn't wish to use the cached
        entry, ``None`` should be returned.

        Else, a dict defining the cached result should be returned. The
        dict may have the following keys:

        objs
           An iterable of objects that should be sent to the client. That
           iterable of objects is expected to be what the command function
           would return if invoked or an equivalent representation thereof.
        """

    def onobject(obj):
        """Called when a new object is emitted from the command function.

        Receives as its argument the object that was emitted from the
        command function.

        This method returns an iterator of objects to forward to the output
        layer. The easiest implementation is a generator that just
        ``yield obj``.
        """

    def onfinished():
        """Called after all objects have been emitted from the command function.

        Implementations should return an iterator of objects to forward to
        the output layer.

        This method can be a generator.
        """
