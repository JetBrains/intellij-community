# tagmerge.py - merge .hgtags files
#
# Copyright 2014 Angel Ezquerra <angel.ezquerra@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# This module implements an automatic merge algorithm for mercurial's tag files
#
# The tagmerge algorithm implemented in this module is able to resolve most
# merge conflicts that currently would trigger a .hgtags merge conflict. The
# only case that it does not (and cannot) handle is that in which two tags point
# to different revisions on each merge parent _and_ their corresponding tag
# histories have the same rank (i.e. the same length). In all other cases the
# merge algorithm will choose the revision belonging to the parent with the
# highest ranked tag history. The merged tag history is the combination of both
# tag histories (special care is taken to try to combine common tag histories
# where possible).
#
# In addition to actually merging the tags from two parents, taking into
# account the base, the algorithm also tries to minimize the difference
# between the merged tag file and the first parent's tag file (i.e. it tries to
# make the merged tag order as as similar as possible to the first parent's tag
# file order).
#
# The algorithm works as follows:
# 1. read the tags from p1, p2 and the base
#     - when reading the p1 tags, also get the line numbers associated to each
#       tag node (these will be used to sort the merged tags in a way that
#       minimizes the diff to p1). Ignore the file numbers when reading p2 and
#       the base
# 2. recover the "lost tags" (i.e. those that are found in the base but not on
#    p1 or p2) and add them back to p1 and/or p2
#     - at this point the only tags that are on p1 but not on p2 are those new
#       tags that were introduced in p1. Same thing for the tags that are on p2
#       but not on p2
# 3. take all tags that are only on p1 or only on p2 (but not on the base)
#     - Note that these are the tags that were introduced between base and p1
#       and between base and p2, possibly on separate clones
# 4. for each tag found both on p1 and p2 perform the following merge algorithm:
#     - the tags conflict if their tag "histories" have the same "rank" (i.e.
#       length) AND the last (current) tag is NOT the same
#     - for non conflicting tags:
#         - choose which are the high and the low ranking nodes
#             - the high ranking list of nodes is the one that is longer.
#               In case of draw favor p1
#             - the merged node list is made of 3 parts:
#                 - first the nodes that are common to the beginning of both
#                   the low and the high ranking nodes
#                 - second the non common low ranking nodes
#                 - finally the non common high ranking nodes (with the last
#                   one being the merged tag node)
#             - note that this is equivalent to putting the whole low ranking
#               node list first, followed by the non common high ranking nodes
#     - note that during the merge we keep the "node line numbers", which will
#       be used when writing the merged tags to the tag file
# 5. write the merged tags taking into account to their positions in the first
#    parent (i.e. try to keep the relative ordering of the nodes that come
#    from p1). This minimizes the diff between the merged and the p1 tag files
#    This is done by using the following algorithm
#     - group the nodes for a given tag that must be written next to each other
#         - A: nodes that come from consecutive lines on p1
#         - B: nodes that come from p2 (i.e. whose associated line number is
#              None) and are next to one of the a nodes in A
#         - each group is associated with a line number coming from p1
#     - generate a "tag block" for each of the groups
#         - a tag block is a set of consecutive "node tag" lines belonging to
#           the same tag and which will be written next to each other on the
#           merged tags file
#     - sort the "tag blocks" according to their associated number line
#         - put blocks whose nodes come all from p2 first
#     - write the tag blocks in the sorted order


from .i18n import _
from . import (
    tags as tagsmod,
    util,
)


def readtagsformerge(ui, repo, lines, fn=b'', keeplinenums=False):
    """read the .hgtags file into a structure that is suitable for merging

    Depending on the keeplinenums flag, clear the line numbers associated
    with each tag. This is done because only the line numbers of the first
    parent are useful for merging.
    """
    filetags = tagsmod._readtaghist(
        ui, repo, lines, fn=fn, recode=None, calcnodelines=True
    )[1]
    for tagname, taginfo in filetags.items():
        if not keeplinenums:
            for el in taginfo:
                el[1] = None
    return filetags


def grouptagnodesbyline(tagnodes):
    """
    Group nearby nodes (i.e. those that must be written next to each other)

    The input is a list of [node, position] pairs, corresponding to a given tag
    The position is the line number where the node was found on the first parent
    .hgtags file, or None for those nodes that came from the base or the second
    parent .hgtags files.

    This function groups those [node, position] pairs, returning a list of
    groups of nodes that must be written next to each other because their
    positions are consecutive or have no position preference (because their
    position is None).

    The result is a list of [position, [consecutive node list]]
    """
    firstlinenum = None
    for hexnode, linenum in tagnodes:
        firstlinenum = linenum
        if firstlinenum is not None:
            break
    if firstlinenum is None:
        return [[None, [el[0] for el in tagnodes]]]
    tagnodes[0][1] = firstlinenum
    groupednodes = [[firstlinenum, []]]
    prevlinenum = firstlinenum
    for hexnode, linenum in tagnodes:
        if linenum is not None and linenum - prevlinenum > 1:
            groupednodes.append([linenum, []])
        groupednodes[-1][1].append(hexnode)
        if linenum is not None:
            prevlinenum = linenum
    return groupednodes


def writemergedtags(fcd, mergedtags):
    """
    write the merged tags while trying to minimize the diff to the first parent

    This function uses the ordering info stored on the merged tags dict to
    generate an .hgtags file which is correct (in the sense that its contents
    correspond to the result of the tag merge) while also being as close as
    possible to the first parent's .hgtags file.
    """
    # group the node-tag pairs that must be written next to each other
    for tname, taglist in list(mergedtags.items()):
        mergedtags[tname] = grouptagnodesbyline(taglist)

    # convert the grouped merged tags dict into a format that resembles the
    # final .hgtags file (i.e. a list of blocks of 'node tag' pairs)
    def taglist2string(tlist, tname):
        return b'\n'.join([b'%s %s' % (hexnode, tname) for hexnode in tlist])

    finaltags = []
    for tname, tags in mergedtags.items():
        for block in tags:
            block[1] = taglist2string(block[1], tname)
        finaltags += tags

    # the tag groups are linked to a "position" that can be used to sort them
    # before writing them
    # the position is calculated to ensure that the diff of the merged .hgtags
    # file to the first parent's .hgtags file is as small as possible
    finaltags.sort(key=lambda x: -1 if x[0] is None else x[0])

    # finally we can join the sorted groups to get the final contents of the
    # merged .hgtags file, and then write it to disk
    mergedtagstring = b'\n'.join([tags for rank, tags in finaltags if tags])
    fcd.write(mergedtagstring + b'\n', fcd.flags())


def singletagmerge(p1nodes, p2nodes):
    """
    merge the nodes corresponding to a single tag

    Note that the inputs are lists of node-linenum pairs (i.e. not just lists
    of nodes)
    """
    if not p2nodes:
        return p1nodes
    if not p1nodes:
        return p2nodes

    # there is no conflict unless both tags point to different revisions
    # and have a non identical tag history
    p1currentnode = p1nodes[-1][0]
    p2currentnode = p2nodes[-1][0]
    if p1currentnode != p2currentnode and len(p1nodes) == len(p2nodes):
        # cannot merge two tags with same rank pointing to different nodes
        return None

    # which are the highest ranking (hr) / lowest ranking (lr) nodes?
    if len(p1nodes) >= len(p2nodes):
        hrnodes, lrnodes = p1nodes, p2nodes
    else:
        hrnodes, lrnodes = p2nodes, p1nodes

    # the lowest ranking nodes will be written first, followed by the highest
    # ranking nodes
    # to avoid unwanted tag rank explosion we try to see if there are some
    # common nodes that can be written only once
    commonidx = len(lrnodes)
    for n in range(len(lrnodes)):
        if hrnodes[n][0] != lrnodes[n][0]:
            commonidx = n
            break
        lrnodes[n][1] = p1nodes[n][1]

    # the merged node list has 3 parts:
    # - common nodes
    # - non common lowest ranking nodes
    # - non common highest ranking nodes
    # note that the common nodes plus the non common lowest ranking nodes is the
    # whole list of lr nodes
    return lrnodes + hrnodes[commonidx:]


def merge(repo, fcd, fco, fca):
    """
    Merge the tags of two revisions, taking into account the base tags
    Try to minimize the diff between the merged tags and the first parent tags
    """
    ui = repo.ui
    # read the p1, p2 and base tags
    # only keep the line numbers for the p1 tags
    p1tags = readtagsformerge(
        ui, repo, fcd.data().splitlines(), fn=b"p1 tags", keeplinenums=True
    )
    p2tags = readtagsformerge(
        ui, repo, fco.data().splitlines(), fn=b"p2 tags", keeplinenums=False
    )
    basetags = readtagsformerge(
        ui, repo, fca.data().splitlines(), fn=b"base tags", keeplinenums=False
    )

    # recover the list of "lost tags" (i.e. those that were found on the base
    # revision but not on one of the revisions being merged)
    basetagset = set(basetags)
    for n, pntags in enumerate((p1tags, p2tags)):
        pntagset = set(pntags)
        pnlosttagset = basetagset - pntagset
        for t in pnlosttagset:
            pntags[t] = basetags[t]
            if pntags[t][-1][0] != repo.nodeconstants.nullhex:
                pntags[t].append([repo.nodeconstants.nullhex, None])

    conflictedtags = []  # for reporting purposes
    mergedtags = util.sortdict(p1tags)
    # sortdict does not implement iteritems()
    for tname, p2nodes in p2tags.items():
        if tname not in mergedtags:
            mergedtags[tname] = p2nodes
            continue
        p1nodes = mergedtags[tname]
        mergednodes = singletagmerge(p1nodes, p2nodes)
        if mergednodes is None:
            conflictedtags.append(tname)
            continue
        mergedtags[tname] = mergednodes

    if conflictedtags:
        numconflicts = len(conflictedtags)
        ui.warn(
            _(
                b'automatic .hgtags merge failed\n'
                b'the following %d tags are in conflict: %s\n'
            )
            % (numconflicts, b', '.join(sorted(conflictedtags)))
        )
        return True, 1

    writemergedtags(fcd, mergedtags)
    ui.note(_(b'.hgtags merged successfully\n'))
    return False, 0
