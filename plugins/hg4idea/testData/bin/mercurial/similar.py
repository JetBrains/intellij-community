# similar.py - mechanisms for finding similar files
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import util
import mdiff
import bdiff

def _findexactmatches(repo, added, removed):
    '''find renamed files that have no changes

    Takes a list of new filectxs and a list of removed filectxs, and yields
    (before, after) tuples of exact matches.
    '''
    numfiles = len(added) + len(removed)

    # Get hashes of removed files.
    hashes = {}
    for i, fctx in enumerate(removed):
        repo.ui.progress(_('searching for exact renames'), i, total=numfiles)
        h = util.sha1(fctx.data()).digest()
        hashes[h] = fctx

    # For each added file, see if it corresponds to a removed file.
    for i, fctx in enumerate(added):
        repo.ui.progress(_('searching for exact renames'), i + len(removed),
                total=numfiles)
        h = util.sha1(fctx.data()).digest()
        if h in hashes:
            yield (hashes[h], fctx)

    # Done
    repo.ui.progress(_('searching for exact renames'), None)

def _findsimilarmatches(repo, added, removed, threshold):
    '''find potentially renamed files based on similar file content

    Takes a list of new filectxs and a list of removed filectxs, and yields
    (before, after, score) tuples of partial matches.
    '''
    copies = {}
    for i, r in enumerate(removed):
        repo.ui.progress(_('searching for similar files'), i,
                         total=len(removed))

        # lazily load text
        @util.cachefunc
        def data():
            orig = r.data()
            return orig, mdiff.splitnewlines(orig)

        def score(text):
            orig, lines = data()
            # bdiff.blocks() returns blocks of matching lines
            # count the number of bytes in each
            equal = 0
            matches = bdiff.blocks(text, orig)
            for x1, x2, y1, y2 in matches:
                for line in lines[y1:y2]:
                    equal += len(line)

            lengths = len(text) + len(orig)
            return equal * 2.0 / lengths

        for a in added:
            bestscore = copies.get(a, (None, threshold))[1]
            myscore = score(a.data())
            if myscore >= bestscore:
                copies[a] = (r, myscore)
    repo.ui.progress(_('searching'), None)

    for dest, v in copies.iteritems():
        source, score = v
        yield source, dest, score

def findrenames(repo, added, removed, threshold):
    '''find renamed files -- yields (before, after, score) tuples'''
    parentctx = repo['.']
    workingctx = repo[None]

    # Zero length files will be frequently unrelated to each other, and
    # tracking the deletion/addition of such a file will probably cause more
    # harm than good. We strip them out here to avoid matching them later on.
    addedfiles = set([workingctx[fp] for fp in added
            if workingctx[fp].size() > 0])
    removedfiles = set([parentctx[fp] for fp in removed
            if fp in parentctx and parentctx[fp].size() > 0])

    # Find exact matches.
    for (a, b) in _findexactmatches(repo,
            sorted(addedfiles), sorted(removedfiles)):
        addedfiles.remove(b)
        yield (a.path(), b.path(), 1.0)

    # If the user requested similar files to be matched, search for them also.
    if threshold < 1.0:
        for (a, b, score) in _findsimilarmatches(repo,
                sorted(addedfiles), sorted(removedfiles), threshold):
            yield (a.path(), b.path(), score)

