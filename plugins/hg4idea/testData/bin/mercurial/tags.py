# tags.py - read tag info from local repository
#
# Copyright 2009 Matt Mackall <mpm@selenic.com>
# Copyright 2009 Greg Ward <greg@gerg.ca>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# Currently this module only deals with reading and caching tags.
# Eventually, it could take care of updating (adding/removing/moving)
# tags too.

from node import nullid, bin, hex, short
from i18n import _
import encoding
import error

def _debugalways(ui, *msg):
    ui.write(*msg)

def _debugconditional(ui, *msg):
    ui.debug(*msg)

def _debugnever(ui, *msg):
    pass

_debug = _debugalways
_debug = _debugnever

def findglobaltags1(ui, repo, alltags, tagtypes):
    '''Find global tags in repo by reading .hgtags from every head that
    has a distinct version of it.  Updates the dicts alltags, tagtypes
    in place: alltags maps tag name to (node, hist) pair (see _readtags()
    below), and tagtypes maps tag name to tag type ('global' in this
    case).'''

    seen = set()
    fctx = None
    ctxs = []                       # list of filectx
    for node in repo.heads():
        try:
            fnode = repo[node].filenode('.hgtags')
        except error.LookupError:
            continue
        if fnode not in seen:
            seen.add(fnode)
            if not fctx:
                fctx = repo.filectx('.hgtags', fileid=fnode)
            else:
                fctx = fctx.filectx(fnode)
            ctxs.append(fctx)

    # read the tags file from each head, ending with the tip
    for fctx in reversed(ctxs):
        filetags = _readtags(
            ui, repo, fctx.data().splitlines(), fctx)
        _updatetags(filetags, "global", alltags, tagtypes)

def findglobaltags2(ui, repo, alltags, tagtypes):
    '''Same as findglobaltags1(), but with caching.'''
    # This is so we can be lazy and assume alltags contains only global
    # tags when we pass it to _writetagcache().
    assert len(alltags) == len(tagtypes) == 0, \
           "findglobaltags() should be called first"

    (heads, tagfnode, cachetags, shouldwrite) = _readtagcache(ui, repo)
    if cachetags is not None:
        assert not shouldwrite
        # XXX is this really 100% correct?  are there oddball special
        # cases where a global tag should outrank a local tag but won't,
        # because cachetags does not contain rank info?
        _updatetags(cachetags, 'global', alltags, tagtypes)
        return

    _debug(ui, "reading tags from %d head(s): %s\n"
           % (len(heads), map(short, reversed(heads))))
    seen = set()                    # set of fnode
    fctx = None
    for head in reversed(heads):        # oldest to newest
        assert head in repo.changelog.nodemap, \
               "tag cache returned bogus head %s" % short(head)

        fnode = tagfnode.get(head)
        if fnode and fnode not in seen:
            seen.add(fnode)
            if not fctx:
                fctx = repo.filectx('.hgtags', fileid=fnode)
            else:
                fctx = fctx.filectx(fnode)

            filetags = _readtags(ui, repo, fctx.data().splitlines(), fctx)
            _updatetags(filetags, 'global', alltags, tagtypes)

    # and update the cache (if necessary)
    if shouldwrite:
        _writetagcache(ui, repo, heads, tagfnode, alltags)

# Set this to findglobaltags1 to disable tag caching.
findglobaltags = findglobaltags2

def readlocaltags(ui, repo, alltags, tagtypes):
    '''Read local tags in repo.  Update alltags and tagtypes.'''
    try:
        # localtags is in the local encoding; re-encode to UTF-8 on
        # input for consistency with the rest of this module.
        data = repo.opener("localtags").read()
        filetags = _readtags(
            ui, repo, data.splitlines(), "localtags",
            recode=encoding.fromlocal)
        _updatetags(filetags, "local", alltags, tagtypes)
    except IOError:
        pass

def _readtags(ui, repo, lines, fn, recode=None):
    '''Read tag definitions from a file (or any source of lines).
    Return a mapping from tag name to (node, hist): node is the node id
    from the last line read for that name, and hist is the list of node
    ids previously associated with it (in file order).  All node ids are
    binary, not hex.'''

    filetags = {}               # map tag name to (node, hist)
    count = 0

    def warn(msg):
        ui.warn(_("%s, line %s: %s\n") % (fn, count, msg))

    for line in lines:
        count += 1
        if not line:
            continue
        try:
            (nodehex, name) = line.split(" ", 1)
        except ValueError:
            warn(_("cannot parse entry"))
            continue
        name = name.strip()
        if recode:
            name = recode(name)
        try:
            nodebin = bin(nodehex)
        except TypeError:
            warn(_("node '%s' is not well formed") % nodehex)
            continue
        if nodebin not in repo.changelog.nodemap:
            # silently ignore as pull -r might cause this
            continue

        # update filetags
        hist = []
        if name in filetags:
            n, hist = filetags[name]
            hist.append(n)
        filetags[name] = (nodebin, hist)
    return filetags

def _updatetags(filetags, tagtype, alltags, tagtypes):
    '''Incorporate the tag info read from one file into the two
    dictionaries, alltags and tagtypes, that contain all tag
    info (global across all heads plus local).'''

    for name, nodehist in filetags.iteritems():
        if name not in alltags:
            alltags[name] = nodehist
            tagtypes[name] = tagtype
            continue

        # we prefer alltags[name] if:
        #  it supercedes us OR
        #  mutual supercedes and it has a higher rank
        # otherwise we win because we're tip-most
        anode, ahist = nodehist
        bnode, bhist = alltags[name]
        if (bnode != anode and anode in bhist and
            (bnode not in ahist or len(bhist) > len(ahist))):
            anode = bnode
        ahist.extend([n for n in bhist if n not in ahist])
        alltags[name] = anode, ahist
        tagtypes[name] = tagtype


# The tag cache only stores info about heads, not the tag contents
# from each head.  I.e. it doesn't try to squeeze out the maximum
# performance, but is simpler has a better chance of actually
# working correctly.  And this gives the biggest performance win: it
# avoids looking up .hgtags in the manifest for every head, and it
# can avoid calling heads() at all if there have been no changes to
# the repo.

def _readtagcache(ui, repo):
    '''Read the tag cache and return a tuple (heads, fnodes, cachetags,
    shouldwrite).  If the cache is completely up-to-date, cachetags is a
    dict of the form returned by _readtags(); otherwise, it is None and
    heads and fnodes are set.  In that case, heads is the list of all
    heads currently in the repository (ordered from tip to oldest) and
    fnodes is a mapping from head to .hgtags filenode.  If those two are
    set, caller is responsible for reading tag info from each head.'''

    try:
        cachefile = repo.opener('tags.cache', 'r')
        # force reading the file for static-http
        cachelines = iter(cachefile)
        _debug(ui, 'reading tag cache from %s\n' % cachefile.name)
    except IOError:
        cachefile = None

    # The cache file consists of lines like
    #   <headrev> <headnode> [<tagnode>]
    # where <headrev> and <headnode> redundantly identify a repository
    # head from the time the cache was written, and <tagnode> is the
    # filenode of .hgtags on that head.  Heads with no .hgtags file will
    # have no <tagnode>.  The cache is ordered from tip to oldest (which
    # is part of why <headrev> is there: a quick visual check is all
    # that's required to ensure correct order).
    #
    # This information is enough to let us avoid the most expensive part
    # of finding global tags, which is looking up <tagnode> in the
    # manifest for each head.
    cacherevs = []                      # list of headrev
    cacheheads = []                     # list of headnode
    cachefnode = {}                     # map headnode to filenode
    if cachefile:
        for line in cachelines:
            if line == "\n":
                break
            line = line.rstrip().split()
            cacherevs.append(int(line[0]))
            headnode = bin(line[1])
            cacheheads.append(headnode)
            if len(line) == 3:
                fnode = bin(line[2])
                cachefnode[headnode] = fnode

    tipnode = repo.changelog.tip()
    tiprev = len(repo.changelog) - 1

    # Case 1 (common): tip is the same, so nothing has changed.
    # (Unchanged tip trivially means no changesets have been added.
    # But, thanks to localrepository.destroyed(), it also means none
    # have been destroyed by strip or rollback.)
    if cacheheads and cacheheads[0] == tipnode and cacherevs[0] == tiprev:
        _debug(ui, "tag cache: tip unchanged\n")
        tags = _readtags(ui, repo, cachelines, cachefile.name)
        cachefile.close()
        return (None, None, tags, False)
    if cachefile:
        cachefile.close()               # ignore rest of file

    repoheads = repo.heads()
    # Case 2 (uncommon): empty repo; get out quickly and don't bother
    # writing an empty cache.
    if repoheads == [nullid]:
        return ([], {}, {}, False)

    # Case 3 (uncommon): cache file missing or empty.
    if not cacheheads:
        _debug(ui, 'tag cache: cache file missing or empty\n')

    # Case 4 (uncommon): tip rev decreased.  This should only happen
    # when we're called from localrepository.destroyed().  Refresh the
    # cache so future invocations will not see disappeared heads in the
    # cache.
    elif cacheheads and tiprev < cacherevs[0]:
        _debug(ui,
               'tag cache: tip rev decremented (from %d to %d), '
               'so we must be destroying nodes\n'
               % (cacherevs[0], tiprev))

    # Case 5 (common): tip has changed, so we've added/replaced heads.
    else:
        _debug(ui,
               'tag cache: tip has changed (%d:%s); must find new heads\n'
               % (tiprev, short(tipnode)))

    # Luckily, the code to handle cases 3, 4, 5 is the same.  So the
    # above if/elif/else can disappear once we're confident this thing
    # actually works and we don't need the debug output.

    # N.B. in case 4 (nodes destroyed), "new head" really means "newly
    # exposed".
    newheads = [head
                for head in repoheads
                if head not in set(cacheheads)]
    _debug(ui, 'tag cache: found %d head(s) not in cache: %s\n'
           % (len(newheads), map(short, newheads)))

    # Now we have to lookup the .hgtags filenode for every new head.
    # This is the most expensive part of finding tags, so performance
    # depends primarily on the size of newheads.  Worst case: no cache
    # file, so newheads == repoheads.
    for head in newheads:
        cctx = repo[head]
        try:
            fnode = cctx.filenode('.hgtags')
            cachefnode[head] = fnode
        except error.LookupError:
            # no .hgtags file on this head
            pass

    # Caller has to iterate over all heads, but can use the filenodes in
    # cachefnode to get to each .hgtags revision quickly.
    return (repoheads, cachefnode, None, True)

def _writetagcache(ui, repo, heads, tagfnode, cachetags):

    try:
        cachefile = repo.opener('tags.cache', 'w', atomictemp=True)
    except (OSError, IOError):
        return
    _debug(ui, 'writing cache file %s\n' % cachefile.name)

    realheads = repo.heads()            # for sanity checks below
    for head in heads:
        # temporary sanity checks; these can probably be removed
        # once this code has been in crew for a few weeks
        assert head in repo.changelog.nodemap, \
               'trying to write non-existent node %s to tag cache' % short(head)
        assert head in realheads, \
               'trying to write non-head %s to tag cache' % short(head)
        assert head != nullid, \
               'trying to write nullid to tag cache'

        # This can't fail because of the first assert above.  When/if we
        # remove that assert, we might want to catch LookupError here
        # and downgrade it to a warning.
        rev = repo.changelog.rev(head)

        fnode = tagfnode.get(head)
        if fnode:
            cachefile.write('%d %s %s\n' % (rev, hex(head), hex(fnode)))
        else:
            cachefile.write('%d %s\n' % (rev, hex(head)))

    # Tag names in the cache are in UTF-8 -- which is the whole reason
    # we keep them in UTF-8 throughout this module.  If we converted
    # them local encoding on input, we would lose info writing them to
    # the cache.
    cachefile.write('\n')
    for (name, (node, hist)) in cachetags.iteritems():
        cachefile.write("%s %s\n" % (hex(node), name))

    cachefile.rename()
    cachefile.close()
