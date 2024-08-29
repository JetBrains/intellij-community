# storageutil.py - Storage functionality agnostic of backend implementation.
#
# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import re
import struct

from ..i18n import _
from ..node import (
    bin,
    nullrev,
    sha1nodeconstants,
)
from .. import (
    dagop,
    error,
    mdiff,
)
from ..interfaces import repository
from ..revlogutils import sidedata as sidedatamod
from ..utils import hashutil

_nullhash = hashutil.sha1(sha1nodeconstants.nullid)

# revision data contains extra metadata not part of the official digest
# Only used in changegroup >= v4.
CG_FLAG_SIDEDATA = 1


def hashrevisionsha1(text, p1, p2):
    """Compute the SHA-1 for revision data and its parents.

    This hash combines both the current file contents and its history
    in a manner that makes it easy to distinguish nodes with the same
    content in the revision graph.
    """
    # As of now, if one of the parent node is null, p2 is null
    if p2 == sha1nodeconstants.nullid:
        # deep copy of a hash is faster than creating one
        s = _nullhash.copy()
        s.update(p1)
    else:
        # none of the parent nodes are nullid
        if p1 < p2:
            a = p1
            b = p2
        else:
            a = p2
            b = p1
        s = hashutil.sha1(a)
        s.update(b)
    s.update(text)
    return s.digest()


METADATA_RE = re.compile(b'\x01\n')


def parsemeta(text):
    """Parse metadata header from revision data.

    Returns a 2-tuple of (metadata, offset), where both can be None if there
    is no metadata.
    """
    # text can be buffer, so we can't use .startswith or .index
    if text[:2] != b'\x01\n':
        return None, None
    s = METADATA_RE.search(text, 2).start()
    mtext = text[2:s]
    meta = {}
    for l in mtext.splitlines():
        k, v = l.split(b': ', 1)
        meta[k] = v
    return meta, s + 2


def packmeta(meta, text):
    """Add metadata to fulltext to produce revision text."""
    keys = sorted(meta)
    metatext = b''.join(b'%s: %s\n' % (k, meta[k]) for k in keys)
    return b'\x01\n%s\x01\n%s' % (metatext, text)


def iscensoredtext(text):
    meta = parsemeta(text)[0]
    return meta and b'censored' in meta


def filtermetadata(text):
    """Extract just the revision data from source text.

    Returns ``text`` unless it has a metadata header, in which case we return
    a new buffer without hte metadata.
    """
    if not text.startswith(b'\x01\n'):
        return text

    offset = text.index(b'\x01\n', 2)
    return text[offset + 2 :]


def filerevisioncopied(store, node):
    """Resolve file revision copy metadata.

    Returns ``False`` if the file has no copy metadata. Otherwise a
    2-tuple of the source filename and node.
    """
    if store.parents(node)[0] != sha1nodeconstants.nullid:
        # When creating a copy or move we set filelog parents to null,
        # because contents are probably unrelated and making a delta
        # would not be useful.
        # Conversely, if filelog p1 is non-null we know
        # there is no copy metadata.
        # In the presence of merges, this reasoning becomes invalid
        # if we reorder parents. See tests/test-issue6528.t.
        return False

    meta = parsemeta(store.revision(node))[0]

    # copy and copyrev occur in pairs. In rare cases due to old bugs,
    # one can occur without the other. So ensure both are present to flag
    # as a copy.
    if meta and b'copy' in meta and b'copyrev' in meta:
        return meta[b'copy'], bin(meta[b'copyrev'])

    return False


def filedataequivalent(store, node, filedata):
    """Determines whether file data is equivalent to a stored node.

    Returns True if the passed file data would hash to the same value
    as a stored revision and False otherwise.

    When a stored revision is censored, filedata must be empty to have
    equivalence.

    When a stored revision has copy metadata, it is ignored as part
    of the compare.
    """

    if filedata.startswith(b'\x01\n'):
        revisiontext = b'\x01\n\x01\n' + filedata
    else:
        revisiontext = filedata

    p1, p2 = store.parents(node)

    computednode = hashrevisionsha1(revisiontext, p1, p2)

    if computednode == node:
        return True

    # Censored files compare against the empty file.
    if store.iscensored(store.rev(node)):
        return filedata == b''

    # Renaming a file produces a different hash, even if the data
    # remains unchanged. Check if that's the case.
    if store.renamed(node):
        return store.read(node) == filedata

    return False


def iterrevs(storelen, start=0, stop=None):
    """Iterate over revision numbers in a store."""
    step = 1

    if stop is not None:
        if start > stop:
            step = -1
        stop += step
        if stop > storelen:
            stop = storelen
    else:
        stop = storelen

    return range(start, stop, step)


def fileidlookup(store, fileid, identifier):
    """Resolve the file node for a value.

    ``store`` is an object implementing the ``ifileindex`` interface.

    ``fileid`` can be:

    * A binary node of appropiate size (e.g. 20/32 Bytes).
    * An integer revision number
    * A hex node of appropiate size (e.g. 40/64 Bytes).
    * A bytes that can be parsed as an integer representing a revision number.

    ``identifier`` is used to populate ``error.LookupError`` with an identifier
    for the store.

    Raises ``error.LookupError`` on failure.
    """
    if isinstance(fileid, int):
        try:
            return store.node(fileid)
        except IndexError:
            raise error.LookupError(
                b'%d' % fileid, identifier, _(b'no match found')
            )

    if len(fileid) == len(store.nullid):
        try:
            store.rev(fileid)
            return fileid
        except error.LookupError:
            pass

    if len(fileid) == 2 * len(store.nullid):
        try:
            rawnode = bin(fileid)
            store.rev(rawnode)
            return rawnode
        except TypeError:
            pass

    try:
        rev = int(fileid)

        if b'%d' % rev != fileid:
            raise ValueError

        try:
            return store.node(rev)
        except (IndexError, TypeError):
            pass
    except (ValueError, OverflowError):
        pass

    raise error.LookupError(fileid, identifier, _(b'no match found'))


def resolvestripinfo(minlinkrev, tiprev, headrevs, linkrevfn, parentrevsfn):
    """Resolve information needed to strip revisions.

    Finds the minimum revision number that must be stripped in order to
    strip ``minlinkrev``.

    Returns a 2-tuple of the minimum revision number to do that and a set
    of all revision numbers that have linkrevs that would be broken
    by that strip.

    ``tiprev`` is the current tip-most revision. It is ``len(store) - 1``.
    ``headrevs`` is an iterable of head revisions.
    ``linkrevfn`` is a callable that receives a revision and returns a linked
    revision.
    ``parentrevsfn`` is a callable that receives a revision number and returns
    an iterable of its parent revision numbers.
    """
    brokenrevs = set()
    strippoint = tiprev + 1

    heads = {}
    futurelargelinkrevs = set()
    for head in headrevs:
        headlinkrev = linkrevfn(head)
        heads[head] = headlinkrev
        if headlinkrev >= minlinkrev:
            futurelargelinkrevs.add(headlinkrev)

    # This algorithm involves walking down the rev graph, starting at the
    # heads. Since the revs are topologically sorted according to linkrev,
    # once all head linkrevs are below the minlink, we know there are
    # no more revs that could have a linkrev greater than minlink.
    # So we can stop walking.
    while futurelargelinkrevs:
        strippoint -= 1
        linkrev = heads.pop(strippoint)

        if linkrev < minlinkrev:
            brokenrevs.add(strippoint)
        else:
            futurelargelinkrevs.remove(linkrev)

        for p in parentrevsfn(strippoint):
            if p != nullrev:
                plinkrev = linkrevfn(p)
                heads[p] = plinkrev
                if plinkrev >= minlinkrev:
                    futurelargelinkrevs.add(plinkrev)

    return strippoint, brokenrevs


def emitrevisions(
    store,
    nodes,
    nodesorder,
    resultcls,
    deltaparentfn=None,
    candeltafn=None,
    rawsizefn=None,
    revdifffn=None,
    flagsfn=None,
    deltamode=repository.CG_DELTAMODE_STD,
    revisiondata=False,
    assumehaveparentrevisions=False,
    sidedata_helpers=None,
    debug_info=None,
):
    """Generic implementation of ifiledata.emitrevisions().

    Emitting revision data is subtly complex. This function attempts to
    encapsulate all the logic for doing so in a backend-agnostic way.

    ``store``
       Object conforming to ``ifilestorage`` interface.

    ``nodes``
       List of revision nodes whose data to emit.

    ``resultcls``
       A type implementing the ``irevisiondelta`` interface that will be
       constructed and returned.

    ``deltaparentfn`` (optional)
       Callable receiving a revision number and returning the revision number
       of a revision that the internal delta is stored against. This delta
       will be preferred over computing a new arbitrary delta.

       If not defined, a delta will always be computed from raw revision
       data.

    ``candeltafn`` (optional)
       Callable receiving a pair of revision numbers that returns a bool
       indicating whether a delta between them can be produced.

       If not defined, it is assumed that any two revisions can delta with
       each other.

    ``rawsizefn`` (optional)
       Callable receiving a revision number and returning the length of the
       ``store.rawdata(rev)``.

       If not defined, ``len(store.rawdata(rev))`` will be called.

    ``revdifffn`` (optional)
       Callable receiving a pair of revision numbers that returns a delta
       between them.

       If not defined, a delta will be computed by invoking mdiff code
       on ``store.revision()`` results.

       Defining this function allows a precomputed or stored delta to be
       used without having to compute on.

    ``flagsfn`` (optional)
       Callable receiving a revision number and returns the integer flags
       value for it. If not defined, flags value will be 0.

    ``deltamode``
       constaint on delta to be sent:
       * CG_DELTAMODE_STD  - normal mode, try to reuse storage deltas,
       * CG_DELTAMODE_PREV - only delta against "prev",
       * CG_DELTAMODE_FULL - only issue full snapshot.

       Whether to send fulltext revisions instead of deltas, if allowed.

    ``nodesorder``
    ``revisiondata``
    ``assumehaveparentrevisions``
    ``sidedata_helpers`` (optional)
        If not None, means that sidedata should be included.
        See `revlogutil.sidedata.get_sidedata_helpers`.

    ``debug_info`
        An optionnal dictionnary to gather information about the bundling
        process (if present, see config: debug.bundling.stats.
    """

    fnode = store.node
    frev = store.rev
    parents = store.parentrevs

    if nodesorder == b'nodes':
        revs = [frev(n) for n in nodes]
    elif nodesorder == b'linear':
        revs = {frev(n) for n in nodes}
        revs = dagop.linearize(revs, store.parentrevs)
    else:  # storage and default
        revs = sorted(frev(n) for n in nodes)

    prevrev = None

    if deltamode == repository.CG_DELTAMODE_PREV or assumehaveparentrevisions:
        prevrev = parents(revs[0])[0]

    # Sets of revs available to delta against.
    emitted = set()
    available = set()
    if assumehaveparentrevisions:
        common_heads = set(p for r in revs for p in parents(r))
        common_heads.difference_update(revs)
        available = store.ancestors(common_heads, inclusive=True)

    def is_usable_base(rev):
        """Is a delta against this revision usable over the wire"""
        if rev == nullrev:
            return False
        return rev in emitted or rev in available

    for rev in revs:
        if rev == nullrev:
            continue

        debug_delta_source = None
        if debug_info is not None:
            debug_info['revision-total'] += 1

        node = fnode(rev)
        p1rev, p2rev = parents(rev)

        if debug_info is not None:
            if p1rev != p2rev and p1rev != nullrev and p2rev != nullrev:
                debug_info['merge-total'] += 1

        if deltaparentfn:
            deltaparentrev = deltaparentfn(rev)
            if debug_info is not None:
                if deltaparentrev == nullrev:
                    debug_info['available-full'] += 1
                else:
                    debug_info['available-delta'] += 1

        else:
            deltaparentrev = nullrev

        # Forced delta against previous mode.
        if deltamode == repository.CG_DELTAMODE_PREV:
            if debug_info is not None:
                debug_delta_source = "prev"
            baserev = prevrev

        # We're instructed to send fulltext. Honor that.
        elif deltamode == repository.CG_DELTAMODE_FULL:
            if debug_info is not None:
                debug_delta_source = "full"
            baserev = nullrev
        # We're instructed to use p1. Honor that
        elif deltamode == repository.CG_DELTAMODE_P1:
            if debug_info is not None:
                debug_delta_source = "p1"
            baserev = p1rev

        # There is a delta in storage. We try to use that because it
        # amounts to effectively copying data from storage and is
        # therefore the fastest.
        elif is_usable_base(deltaparentrev):
            if debug_info is not None:
                debug_delta_source = "storage"
            baserev = deltaparentrev
        elif deltaparentrev == nullrev:
            if debug_info is not None:
                debug_delta_source = "storage"
            baserev = deltaparentrev
        else:
            if deltaparentrev != nullrev and debug_info is not None:
                debug_info['denied-base-not-available'] += 1
            # No guarantee the receiver has the delta parent, or Storage has a
            # fulltext revision.
            #
            # We compute a delta on the fly to send over the wire.
            #
            # We start with a try against p1, which in the common case should
            # be close to this revision content.
            #
            # note: we could optimize between p1 and p2 in merges cases.
            elif is_usable_base(p1rev):
                if debug_info is not None:
                    debug_delta_source = "p1"
                baserev = p1rev
            # if p1 was not an option, try p2
            elif is_usable_base(p2rev):
                if debug_info is not None:
                    debug_delta_source = "p2"
                baserev = p2rev
            # Send delta against prev in despair
            #
            # using the closest available ancestors first might be better?
            elif prevrev is not None:
                if debug_info is not None:
                    debug_delta_source = "prev"
                baserev = prevrev
            else:
                if debug_info is not None:
                    debug_delta_source = "full"
                baserev = nullrev

        # But we can't actually use our chosen delta base for whatever
        # reason. Reset to fulltext.
        if (
            baserev != nullrev
            and candeltafn is not None
            and not candeltafn(baserev, rev)
        ):
            if debug_info is not None:
                debug_delta_source = "full"
                debug_info['denied-delta-candeltafn'] += 1
            baserev = nullrev

        revision = None
        delta = None
        baserevisionsize = None

        if revisiondata:
            if store.iscensored(baserev) or store.iscensored(rev):
                try:
                    revision = store.rawdata(node)
                except error.CensoredNodeError as e:
                    if debug_info is not None:
                        debug_delta_source = "full"
                        debug_info['denied-delta-not-available'] += 1
                    revision = e.tombstone

                if baserev != nullrev:
                    if rawsizefn:
                        baserevisionsize = rawsizefn(baserev)
                    else:
                        baserevisionsize = len(store.rawdata(baserev))

            elif (
                baserev == nullrev and deltamode != repository.CG_DELTAMODE_PREV
            ):
                if debug_info is not None:
                    debug_info['computed-delta'] += 1  # close enough
                    debug_info['delta-full'] += 1
                revision = store.rawdata(node)
                emitted.add(rev)
            else:
                if revdifffn:
                    if debug_info is not None:
                        if debug_delta_source == "full":
                            debug_info['computed-delta'] += 1
                            debug_info['delta-full'] += 1
                        elif debug_delta_source == "prev":
                            debug_info['computed-delta'] += 1
                            debug_info['delta-against-prev'] += 1
                        elif debug_delta_source == "p1":
                            debug_info['computed-delta'] += 1
                            debug_info['delta-against-p1'] += 1
                        elif debug_delta_source == "storage":
                            debug_info['reused-storage-delta'] += 1
                        else:
                            assert False, 'unreachable'

                    delta = revdifffn(baserev, rev)
                else:
                    if debug_info is not None:
                        if debug_delta_source == "full":
                            debug_info['computed-delta'] += 1
                            debug_info['delta-full'] += 1
                        elif debug_delta_source == "prev":
                            debug_info['computed-delta'] += 1
                            debug_info['delta-against-prev'] += 1
                        elif debug_delta_source == "p1":
                            debug_info['computed-delta'] += 1
                            debug_info['delta-against-p1'] += 1
                        elif debug_delta_source == "storage":
                            # seem quite unlikelry to happens
                            debug_info['computed-delta'] += 1
                            debug_info['reused-storage-delta'] += 1
                        else:
                            assert False, 'unreachable'
                    delta = mdiff.textdiff(
                        store.rawdata(baserev), store.rawdata(rev)
                    )

                emitted.add(rev)

        serialized_sidedata = None
        sidedata_flags = (0, 0)
        if sidedata_helpers:
            try:
                old_sidedata = store.sidedata(rev)
            except error.CensoredNodeError:
                # skip any potential sidedata of the censored revision
                sidedata = {}
            else:
                sidedata, sidedata_flags = sidedatamod.run_sidedata_helpers(
                    store=store,
                    sidedata_helpers=sidedata_helpers,
                    sidedata=old_sidedata,
                    rev=rev,
                )
            if sidedata:
                serialized_sidedata = sidedatamod.serialize_sidedata(sidedata)

        flags = flagsfn(rev) if flagsfn else 0
        protocol_flags = 0
        if serialized_sidedata:
            # Advertise that sidedata exists to the other side
            protocol_flags |= CG_FLAG_SIDEDATA
            # Computers and removers can return flags to add and/or remove
            flags = flags | sidedata_flags[0] & ~sidedata_flags[1]

        yield resultcls(
            node=node,
            p1node=fnode(p1rev),
            p2node=fnode(p2rev),
            basenode=fnode(baserev),
            flags=flags,
            baserevisionsize=baserevisionsize,
            revision=revision,
            delta=delta,
            sidedata=serialized_sidedata,
            protocol_flags=protocol_flags,
        )

        prevrev = rev


def deltaiscensored(delta, baserev, baselenfn):
    """Determine if a delta represents censored revision data.

    ``baserev`` is the base revision this delta is encoded against.
    ``baselenfn`` is a callable receiving a revision number that resolves the
    length of the revision fulltext.

    Returns a bool indicating if the result of the delta represents a censored
    revision.
    """
    # Fragile heuristic: unless new file meta keys are added alphabetically
    # preceding "censored", all censored revisions are prefixed by
    # "\1\ncensored:". A delta producing such a censored revision must be a
    # full-replacement delta, so we inspect the first and only patch in the
    # delta for this prefix.
    hlen = struct.calcsize(b">lll")
    if len(delta) <= hlen:
        return False

    oldlen = baselenfn(baserev)
    newlen = len(delta) - hlen
    if delta[:hlen] != mdiff.replacediffheader(oldlen, newlen):
        return False

    add = b"\1\ncensored:"
    addlen = len(add)
    return newlen >= addlen and delta[hlen : hlen + addlen] == add
