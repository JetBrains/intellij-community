# stuff related specifically to patch manipulation / parsing
#
# Copyright 2008 Mark Edgington <edgimar@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
#
# This code is based on the Mark Edgington's crecord extension.
# (Itself based on Bryan O'Sullivan's record extension.)


import os
import re
import signal

from .i18n import _
from .pycompat import (
    open,
)
from . import (
    diffhelper,
    encoding,
    error,
    patch as patchmod,
    pycompat,
    scmutil,
    util,
)
from .utils import stringutil

stringio = util.stringio

# patch comments based on the git one
diffhelptext = _(
    b"""# To remove '-' lines, make them ' ' lines (context).
# To remove '+' lines, delete them.
# Lines starting with # will be removed from the patch.
"""
)

hunkhelptext = _(
    b"""#
# If the patch applies cleanly, the edited hunk will immediately be
# added to the record list. If it does not apply cleanly, a rejects file
# will be generated. You can use that when you try again. If all lines
# of the hunk are removed, then the edit is aborted and the hunk is left
# unchanged.
"""
)

patchhelptext = _(
    b"""#
# If the patch applies cleanly, the edited patch will immediately
# be finalised. If it does not apply cleanly, rejects files will be
# generated. You can use those when you try again.
"""
)

try:
    import curses
    import curses.ascii

    curses.error
except (ImportError, AttributeError):
    curses = None


class fallbackerror(error.Abort):
    """Error that indicates the client should try to fallback to text mode."""

    # Inherits from error.Abort so that existing behavior is preserved if the
    # calling code does not know how to fallback.


def checkcurses(ui):
    """Return True if the user wants to use curses

    This method returns True if curses is found (and that python is built with
    it) and that the user has the correct flag for the ui.
    """
    return curses and ui.interface(b"chunkselector") == b"curses"


class patchnode:
    """abstract class for patch graph nodes
    (i.e. patchroot, header, hunk, hunkline)
    """

    @property
    def content(self):
        return b''

    def firstchild(self):
        raise NotImplementedError(b"method must be implemented by subclass")

    def lastchild(self):
        raise NotImplementedError(b"method must be implemented by subclass")

    def allchildren(self):
        """Return a list of all of the direct children of this node"""
        raise NotImplementedError(b"method must be implemented by subclass")

    def nextsibling(self):
        """
        Return the closest next item of the same type where there are no items
        of different types between the current item and this closest item.
        If no such item exists, return None.
        """
        raise NotImplementedError(b"method must be implemented by subclass")

    def prevsibling(self):
        """
        Return the closest previous item of the same type where there are no
        items of different types between the current item and this closest item.
        If no such item exists, return None.
        """
        raise NotImplementedError(b"method must be implemented by subclass")

    def parentitem(self):
        raise NotImplementedError(b"method must be implemented by subclass")

    def nextitem(self, skipfolded=True):
        """
        Try to return the next item closest to this item, regardless of item's
        type (header, hunk, or hunkline).

        If skipfolded == True, and the current item is folded, then the child
        items that are hidden due to folding will be skipped when determining
        the next item.

        If it is not possible to get the next item, return None.
        """
        try:
            itemfolded = self.folded
        except AttributeError:
            itemfolded = False
        if skipfolded and itemfolded:
            nextitem = self.nextsibling()
            if nextitem is None:
                try:
                    nextitem = self.parentitem().nextsibling()
                except AttributeError:
                    nextitem = None
            return nextitem
        else:
            # try child
            item = self.firstchild()
            if item is not None:
                return item

            # else try next sibling
            item = self.nextsibling()
            if item is not None:
                return item

            try:
                # else try parent's next sibling
                item = self.parentitem().nextsibling()
                if item is not None:
                    return item

                # else return grandparent's next sibling (or None)
                return self.parentitem().parentitem().nextsibling()

            except AttributeError:  # parent and/or grandparent was None
                return None

    def previtem(self, skipfolded=None):
        """
        Try to return the previous item closest to this item, regardless of
        item's type (header, hunk, or hunkline).

        If it is not possible to get the previous item, return None.
        """
        # try previous sibling's last child's last child,
        # else try previous sibling's last child, else try previous sibling
        prevsibling = self.prevsibling()
        if prevsibling is not None:
            prevsiblinglastchild = prevsibling.lastchild()
            if (prevsiblinglastchild is not None) and not prevsibling.folded:
                prevsiblinglclc = prevsiblinglastchild.lastchild()
                if (
                    prevsiblinglclc is not None
                ) and not prevsiblinglastchild.folded:
                    return prevsiblinglclc
                else:
                    return prevsiblinglastchild
            else:
                return prevsibling

        # try parent (or None)
        return self.parentitem()


class patch(patchnode, list):  # todo: rename patchroot
    """
    list of header objects representing the patch.
    """

    def __init__(self, headerlist):
        self.extend(headerlist)
        # add parent patch object reference to each header
        for header in self:
            header.patch = self


class uiheader(patchnode):
    """
    patchnode class wrapping a patch.header
    """

    def __init__(self, header):
        self.nonuiheader = header
        # flag to indicate whether to apply this chunk
        self.applied = True
        # flag which only affects the status display indicating if a node's
        # children are partially applied (i.e. some applied, some not).
        self.partial = False

        # flag to indicate whether to display as folded/unfolded to user
        self.folded = True

        # list of all headers in patch
        self.patch = None

        # flag is False if this header was ever unfolded from initial state
        self.neverunfolded = True
        self.hunks = [uihunk(h, self) for h in self.hunks]

    @property
    def content(self):
        return self.filename()

    def prettystr(self):
        x = stringio()
        self.pretty(x)
        return x.getvalue()

    def nextsibling(self):
        numheadersinpatch = len(self.patch)
        indexofthisheader = self.patch.index(self)

        if indexofthisheader < numheadersinpatch - 1:
            nextheader = self.patch[indexofthisheader + 1]
            return nextheader
        else:
            return None

    def prevsibling(self):
        indexofthisheader = self.patch.index(self)
        if indexofthisheader > 0:
            previousheader = self.patch[indexofthisheader - 1]
            return previousheader
        else:
            return None

    def parentitem(self):
        """
        there is no 'real' parent item of a header that can be selected,
        so return None.
        """
        return None

    def firstchild(self):
        """return the first child of this item, if one exists.  otherwise
        None."""
        if len(self.hunks) > 0:
            return self.hunks[0]
        else:
            return None

    def lastchild(self):
        """return the last child of this item, if one exists.  otherwise
        None."""
        if len(self.hunks) > 0:
            return self.hunks[-1]
        else:
            return None

    def allchildren(self):
        """return a list of all of the direct children of this node"""
        return self.hunks

    def __getattr__(self, name):
        return getattr(self.nonuiheader, name)


class uihunkline(patchnode):
    """represents a changed line in a hunk"""

    def __init__(self, linetext, hunk):
        self.linetext = linetext
        self.applied = True
        # the parent hunk to which this line belongs
        self.hunk = hunk
        # folding lines currently is not used/needed, but this flag is needed
        # in the previtem method.
        self.folded = False

    @property
    def content(self):
        return self.linetext

    def prettystr(self):
        return self.linetext

    def nextsibling(self):
        numlinesinhunk = len(self.hunk.changedlines)
        indexofthisline = self.hunk.changedlines.index(self)

        if indexofthisline < numlinesinhunk - 1:
            nextline = self.hunk.changedlines[indexofthisline + 1]
            return nextline
        else:
            return None

    def prevsibling(self):
        indexofthisline = self.hunk.changedlines.index(self)
        if indexofthisline > 0:
            previousline = self.hunk.changedlines[indexofthisline - 1]
            return previousline
        else:
            return None

    def parentitem(self):
        """return the parent to the current item"""
        return self.hunk

    def firstchild(self):
        """return the first child of this item, if one exists.  otherwise
        None."""
        # hunk-lines don't have children
        return None

    def lastchild(self):
        """return the last child of this item, if one exists.  otherwise
        None."""
        # hunk-lines don't have children
        return None


class uihunk(patchnode):
    """ui patch hunk, wraps a hunk and keep track of ui behavior"""

    maxcontext = 3

    def __init__(self, hunk, header):
        self._hunk = hunk
        self.changedlines = [uihunkline(line, self) for line in hunk.hunk]
        self.header = header
        # used at end for detecting how many removed lines were un-applied
        self.originalremoved = self.removed

        # flag to indicate whether to display as folded/unfolded to user
        self.folded = True
        # flag to indicate whether to apply this chunk
        self.applied = True
        # flag which only affects the status display indicating if a node's
        # children are partially applied (i.e. some applied, some not).
        self.partial = False

    @property
    def content(self):
        return self.proc if self.proc else b''

    def nextsibling(self):
        numhunksinheader = len(self.header.hunks)
        indexofthishunk = self.header.hunks.index(self)

        if indexofthishunk < numhunksinheader - 1:
            nexthunk = self.header.hunks[indexofthishunk + 1]
            return nexthunk
        else:
            return None

    def prevsibling(self):
        indexofthishunk = self.header.hunks.index(self)
        if indexofthishunk > 0:
            previoushunk = self.header.hunks[indexofthishunk - 1]
            return previoushunk
        else:
            return None

    def parentitem(self):
        """return the parent to the current item"""
        return self.header

    def firstchild(self):
        """return the first child of this item, if one exists.  otherwise
        None."""
        if len(self.changedlines) > 0:
            return self.changedlines[0]
        else:
            return None

    def lastchild(self):
        """return the last child of this item, if one exists.  otherwise
        None."""
        if len(self.changedlines) > 0:
            return self.changedlines[-1]
        else:
            return None

    def allchildren(self):
        """return a list of all of the direct children of this node"""
        return self.changedlines

    def countchanges(self):
        """changedlines -> (n+,n-)"""
        add = len(
            [
                l
                for l in self.changedlines
                if l.applied and l.prettystr().startswith(b'+')
            ]
        )
        rem = len(
            [
                l
                for l in self.changedlines
                if l.applied and l.prettystr().startswith(b'-')
            ]
        )
        return add, rem

    def getfromtoline(self):
        # calculate the number of removed lines converted to context lines
        removedconvertedtocontext = self.originalremoved - self.removed

        contextlen = (
            len(self.before) + len(self.after) + removedconvertedtocontext
        )
        if self.after and self.after[-1] == diffhelper.MISSING_NEWLINE_MARKER:
            contextlen -= 1
        fromlen = contextlen + self.removed
        tolen = contextlen + self.added

        # diffutils manual, section "2.2.2.2 detailed description of unified
        # format": "an empty hunk is considered to end at the line that
        # precedes the hunk."
        #
        # so, if either of hunks is empty, decrease its line start. --immerrr
        # but only do this if fromline > 0, to avoid having, e.g fromline=-1.
        fromline, toline = self.fromline, self.toline
        if fromline != 0:
            if fromlen == 0:
                fromline -= 1
            if tolen == 0 and toline > 0:
                toline -= 1

        fromtoline = b'@@ -%d,%d +%d,%d @@%s\n' % (
            fromline,
            fromlen,
            toline,
            tolen,
            self.proc and (b' ' + self.proc),
        )
        return fromtoline

    def write(self, fp):
        # updated self.added/removed, which are used by getfromtoline()
        self.added, self.removed = self.countchanges()
        fp.write(self.getfromtoline())

        hunklinelist = []
        # add the following to the list: (1) all applied lines, and
        # (2) all unapplied removal lines (convert these to context lines)
        for changedline in self.changedlines:
            changedlinestr = changedline.prettystr()
            if changedline.applied:
                hunklinelist.append(changedlinestr)
            elif changedlinestr.startswith(b"-"):
                hunklinelist.append(b" " + changedlinestr[1:])

        fp.write(b''.join(self.before + hunklinelist + self.after))

    pretty = write

    def prettystr(self):
        x = stringio()
        self.pretty(x)
        return x.getvalue()

    def reversehunk(self):
        """return a recordhunk which is the reverse of the hunk

        Assuming the displayed patch is diff(A, B) result. The returned hunk is
        intended to be applied to B, instead of A.

        For example, when A is "0\n1\n2\n6\n" and B is "0\n3\n4\n5\n6\n", and
        the user made the following selection:

                 0
            [x] -1           [x]: selected
            [ ] -2           [ ]: not selected
            [x] +3
            [ ] +4
            [x] +5
                 6

        This function returns a hunk like:

                 0
                -3
                -4
                -5
                +1
                +4
                 6

        Note "4" was first deleted then added. That's because "4" exists in B
        side and "-4" must exist between "-3" and "-5" to make the patch
        applicable to B.
        """
        dels = []
        adds = []
        noeol = False
        for line in self.changedlines:
            text = line.linetext
            if line.linetext == diffhelper.MISSING_NEWLINE_MARKER:
                noeol = True
                continue
            if line.applied:
                if text.startswith(b'+'):
                    dels.append(text[1:])
                elif text.startswith(b'-'):
                    adds.append(text[1:])
            elif text.startswith(b'+'):
                dels.append(text[1:])
                adds.append(text[1:])
        hunk = [b'-%s' % l for l in dels] + [b'+%s' % l for l in adds]
        if noeol and hunk:
            # Remove the newline from the end of the hunk.
            hunk[-1] = hunk[-1][:-1]
        h = self._hunk
        return patchmod.recordhunk(
            h.header, h.toline, h.fromline, h.proc, h.before, hunk, h.after
        )

    def __getattr__(self, name):
        return getattr(self._hunk, name)

    def __repr__(self):
        return '<hunk %r@%d>' % (self.filename(), self.fromline)


def filterpatch(ui, chunks, chunkselector, operation=None):
    """interactively filter patch chunks into applied-only chunks"""
    chunks = list(chunks)
    # convert chunks list into structure suitable for displaying/modifying
    # with curses.  create a list of headers only.
    headers = [c for c in chunks if isinstance(c, patchmod.header)]

    # if there are no changed files
    if len(headers) == 0:
        return [], {}
    uiheaders = [uiheader(h) for h in headers]
    # let user choose headers/hunks/lines, and mark their applied flags
    # accordingly
    ret = chunkselector(ui, uiheaders, operation=operation)
    appliedhunklist = []
    for hdr in uiheaders:
        if hdr.applied and (
            hdr.special() or len([h for h in hdr.hunks if h.applied]) > 0
        ):
            appliedhunklist.append(hdr)
            fixoffset = 0
            for hnk in hdr.hunks:
                if hnk.applied:
                    appliedhunklist.append(hnk)
                    # adjust the 'to'-line offset of the hunk to be correct
                    # after de-activating some of the other hunks for this file
                    if fixoffset:
                        # hnk = copy.copy(hnk) # necessary??
                        hnk.toline += fixoffset
                else:
                    fixoffset += hnk.removed - hnk.added

    return (appliedhunklist, ret)


def chunkselector(ui, headerlist, operation=None):
    """
    curses interface to get selection of chunks, and mark the applied flags
    of the chosen chunks.
    """
    ui.write(_(b'starting interactive selection\n'))
    chunkselector = curseschunkselector(headerlist, ui, operation)
    origsigtstp = sentinel = object()
    if hasattr(signal, 'SIGTSTP'):
        origsigtstp = signal.getsignal(signal.SIGTSTP)
    try:
        with util.with_lc_ctype():
            curses.wrapper(chunkselector.main)
        if chunkselector.initexc is not None:
            raise chunkselector.initexc
        # ncurses does not restore signal handler for SIGTSTP
    finally:
        if origsigtstp is not sentinel:
            signal.signal(signal.SIGTSTP, origsigtstp)
    return chunkselector.opts


def testdecorator(testfn, f):
    def u(*args, **kwargs):
        return f(testfn, *args, **kwargs)

    return u


def testchunkselector(testfn, ui, headerlist, operation=None):
    """
    test interface to get selection of chunks, and mark the applied flags
    of the chosen chunks.
    """
    chunkselector = curseschunkselector(headerlist, ui, operation)

    class dummystdscr:
        def clear(self):
            pass

        def refresh(self):
            pass

    chunkselector.stdscr = dummystdscr()
    if testfn and os.path.exists(testfn):
        testf = open(testfn, b'r')
        # TODO: open in binary mode?
        testcommands = [x.rstrip('\n') for x in testf.readlines()]
        testf.close()
        while True:
            if chunkselector.handlekeypressed(testcommands.pop(0), test=True):
                break
    return chunkselector.opts


_headermessages = {  # {operation: text}
    b'apply': _(b'Select hunks to apply'),
    b'discard': _(b'Select hunks to discard'),
    b'keep': _(b'Select hunks to keep'),
    None: _(b'Select hunks to record'),
}


class curseschunkselector:
    def __init__(self, headerlist, ui, operation=None):
        # put the headers into a patch object
        self.headerlist = patch(headerlist)

        self.ui = ui
        self.opts = {}

        self.errorstr = None
        # list of all chunks
        self.chunklist = []
        for h in headerlist:
            self.chunklist.append(h)
            self.chunklist.extend(h.hunks)

        # dictionary mapping (fgcolor, bgcolor) pairs to the
        # corresponding curses color-pair value.
        self.colorpairs = {}
        # maps custom nicknames of color-pairs to curses color-pair values
        self.colorpairnames = {}

        # Honor color setting of ui section. Keep colored setup as
        # long as not explicitly set to a falsy value - especially,
        # when not set at all. This is to stay most compatible with
        # previous (color only) behaviour.
        uicolor = stringutil.parsebool(self.ui.config(b'ui', b'color'))
        self.usecolor = uicolor is not False

        # the currently selected header, hunk, or hunk-line
        self.currentselecteditem = self.headerlist[0]
        self.lastapplieditem = None

        # updated when printing out patch-display -- the 'lines' here are the
        # line positions *in the pad*, not on the screen.
        self.selecteditemstartline = 0
        self.selecteditemendline = None

        # define indentation levels
        self.headerindentnumchars = 0
        self.hunkindentnumchars = 3
        self.hunklineindentnumchars = 6

        # the first line of the pad to print to the screen
        self.firstlineofpadtoprint = 0

        # keeps track of the number of lines in the pad
        self.numpadlines = None

        self.numstatuslines = 1

        # keep a running count of the number of lines printed to the pad
        # (used for determining when the selected item begins/ends)
        self.linesprintedtopadsofar = 0

        # stores optional text for a commit comment provided by the user
        self.commenttext = b""

        # if the last 'toggle all' command caused all changes to be applied
        self.waslasttoggleallapplied = True

        # affects some ui text
        if operation not in _headermessages:
            raise error.ProgrammingError(
                b'unexpected operation: %s' % operation
            )
        self.operation = operation

        self.regex = None

    def uparrowevent(self):
        """
        try to select the previous item to the current item that has the
        most-indented level.  for example, if a hunk is selected, try to select
        the last hunkline of the hunk prior to the selected hunk.  or, if
        the first hunkline of a hunk is currently selected, then select the
        hunk itself.
        """
        currentitem = self.currentselecteditem

        nextitem = currentitem.previtem()

        if nextitem is None:
            # if no parent item (i.e. currentitem is the first header), then
            # no change...
            nextitem = currentitem

        self.currentselecteditem = nextitem

    def uparrowshiftevent(self):
        """
        select (if possible) the previous item on the same level as the
        currently selected item.  otherwise, select (if possible) the
        parent-item of the currently selected item.
        """
        currentitem = self.currentselecteditem
        nextitem = currentitem.prevsibling()
        # if there's no previous sibling, try choosing the parent
        if nextitem is None:
            nextitem = currentitem.parentitem()
        if nextitem is None:
            # if no parent item (i.e. currentitem is the first header), then
            # no change...
            nextitem = currentitem

        self.currentselecteditem = nextitem
        self.recenterdisplayedarea()

    def downarrowevent(self):
        """
        try to select the next item to the current item that has the
        most-indented level.  for example, if a hunk is selected, select
        the first hunkline of the selected hunk.  or, if the last hunkline of
        a hunk is currently selected, then select the next hunk, if one exists,
        or if not, the next header if one exists.
        """
        # self.startprintline += 1 #debug
        currentitem = self.currentselecteditem

        nextitem = currentitem.nextitem()
        # if there's no next item, keep the selection as-is
        if nextitem is None:
            nextitem = currentitem

        self.currentselecteditem = nextitem

    def downarrowshiftevent(self):
        """
        select (if possible) the next item on the same level as the currently
        selected item.  otherwise, select (if possible) the next item on the
        same level as the parent item of the currently selected item.
        """
        currentitem = self.currentselecteditem
        nextitem = currentitem.nextsibling()
        # if there's no next sibling, try choosing the parent's nextsibling
        if nextitem is None:
            try:
                nextitem = currentitem.parentitem().nextsibling()
            except AttributeError:
                # parentitem returned None, so nextsibling() can't be called
                nextitem = None
        if nextitem is None:
            # if parent has no next sibling, then no change...
            nextitem = currentitem

        self.currentselecteditem = nextitem
        self.recenterdisplayedarea()

    def nextsametype(self, test=False):
        currentitem = self.currentselecteditem
        sametype = lambda item: isinstance(item, type(currentitem))
        nextitem = currentitem.nextitem()

        while nextitem is not None and not sametype(nextitem):
            nextitem = nextitem.nextitem()

        if nextitem is None:
            nextitem = currentitem
        else:
            parent = nextitem.parentitem()
            if parent is not None and parent.folded:
                self.togglefolded(parent)

        self.currentselecteditem = nextitem
        if not test:
            self.recenterdisplayedarea()

    def rightarrowevent(self):
        """
        select (if possible) the first of this item's child-items.
        """
        currentitem = self.currentselecteditem
        nextitem = currentitem.firstchild()

        # turn off folding if we want to show a child-item
        if currentitem.folded:
            self.togglefolded(currentitem)

        if nextitem is None:
            # if no next item on parent-level, then no change...
            nextitem = currentitem

        self.currentselecteditem = nextitem

    def leftarrowevent(self):
        """
        if the current item can be folded (i.e. it is an unfolded header or
        hunk), then fold it.  otherwise try select (if possible) the parent
        of this item.
        """
        currentitem = self.currentselecteditem

        # try to fold the item
        if not isinstance(currentitem, uihunkline):
            if not currentitem.folded:
                self.togglefolded(item=currentitem)
                return

        # if it can't be folded, try to select the parent item
        nextitem = currentitem.parentitem()

        if nextitem is None:
            # if no item on parent-level, then no change...
            nextitem = currentitem
            if not nextitem.folded:
                self.togglefolded(item=nextitem)

        self.currentselecteditem = nextitem

    def leftarrowshiftevent(self):
        """
        select the header of the current item (or fold current item if the
        current item is already a header).
        """
        currentitem = self.currentselecteditem

        if isinstance(currentitem, uiheader):
            if not currentitem.folded:
                self.togglefolded(item=currentitem)
                return

        # select the parent item recursively until we're at a header
        while True:
            nextitem = currentitem.parentitem()
            if nextitem is None:
                break
            else:
                currentitem = nextitem

        self.currentselecteditem = currentitem

    def updatescroll(self):
        """scroll the screen to fully show the currently-selected"""
        selstart = self.selecteditemstartline
        selend = self.selecteditemendline

        padstart = self.firstlineofpadtoprint
        padend = padstart + self.yscreensize - self.numstatuslines - 1
        # 'buffered' pad start/end values which scroll with a certain
        # top/bottom context margin
        padstartbuffered = padstart + 3
        padendbuffered = padend - 3

        if selend > padendbuffered:
            self.scrolllines(selend - padendbuffered)
        elif selstart < padstartbuffered:
            # negative values scroll in pgup direction
            self.scrolllines(selstart - padstartbuffered)

    def scrolllines(self, numlines):
        """scroll the screen up (down) by numlines when numlines >0 (<0)."""
        self.firstlineofpadtoprint += numlines
        if self.firstlineofpadtoprint < 0:
            self.firstlineofpadtoprint = 0
        if self.firstlineofpadtoprint > self.numpadlines - 1:
            self.firstlineofpadtoprint = self.numpadlines - 1

    def toggleapply(self, item=None):
        """
        toggle the applied flag of the specified item.  if no item is specified,
        toggle the flag of the currently selected item.
        """
        if item is None:
            item = self.currentselecteditem
            # Only set this when NOT using 'toggleall'
            self.lastapplieditem = item

        item.applied = not item.applied

        if isinstance(item, uiheader):
            item.partial = False
            if item.applied:
                # apply all its hunks
                for hnk in item.hunks:
                    hnk.applied = True
                    # apply all their hunklines
                    for hunkline in hnk.changedlines:
                        hunkline.applied = True
            else:
                # un-apply all its hunks
                for hnk in item.hunks:
                    hnk.applied = False
                    hnk.partial = False
                    # un-apply all their hunklines
                    for hunkline in hnk.changedlines:
                        hunkline.applied = False
        elif isinstance(item, uihunk):
            item.partial = False
            # apply all it's hunklines
            for hunkline in item.changedlines:
                hunkline.applied = item.applied

            siblingappliedstatus = [hnk.applied for hnk in item.header.hunks]
            allsiblingsapplied = not (False in siblingappliedstatus)
            nosiblingsapplied = not (True in siblingappliedstatus)

            siblingspartialstatus = [hnk.partial for hnk in item.header.hunks]
            somesiblingspartial = True in siblingspartialstatus

            # cases where applied or partial should be removed from header

            # if no 'sibling' hunks are applied (including this hunk)
            if nosiblingsapplied:
                if not item.header.special():
                    item.header.applied = False
                    item.header.partial = False
            else:  # some/all parent siblings are applied
                item.header.applied = True
                item.header.partial = (
                    somesiblingspartial or not allsiblingsapplied
                )

        elif isinstance(item, uihunkline):
            siblingappliedstatus = [ln.applied for ln in item.hunk.changedlines]
            allsiblingsapplied = not (False in siblingappliedstatus)
            nosiblingsapplied = not (True in siblingappliedstatus)

            # if no 'sibling' lines are applied
            if nosiblingsapplied:
                item.hunk.applied = False
                item.hunk.partial = False
            elif allsiblingsapplied:
                item.hunk.applied = True
                item.hunk.partial = False
            else:  # some siblings applied
                item.hunk.applied = True
                item.hunk.partial = True

            parentsiblingsapplied = [
                hnk.applied for hnk in item.hunk.header.hunks
            ]
            noparentsiblingsapplied = not (True in parentsiblingsapplied)
            allparentsiblingsapplied = not (False in parentsiblingsapplied)

            parentsiblingspartial = [
                hnk.partial for hnk in item.hunk.header.hunks
            ]
            someparentsiblingspartial = True in parentsiblingspartial

            # if all parent hunks are not applied, un-apply header
            if noparentsiblingsapplied:
                if not item.hunk.header.special():
                    item.hunk.header.applied = False
                    item.hunk.header.partial = False
            # set the applied and partial status of the header if needed
            else:  # some/all parent siblings are applied
                item.hunk.header.applied = True
                item.hunk.header.partial = (
                    someparentsiblingspartial or not allparentsiblingsapplied
                )

    def toggleall(self):
        """toggle the applied flag of all items."""
        if self.waslasttoggleallapplied:  # then unapply them this time
            for item in self.headerlist:
                if item.applied:
                    self.toggleapply(item)
        else:
            for item in self.headerlist:
                if not item.applied:
                    self.toggleapply(item)
        self.waslasttoggleallapplied = not self.waslasttoggleallapplied

    def flipselections(self):
        """
        Flip all selections. Every selected line is unselected and vice
        versa.
        """
        for header in self.headerlist:
            for hunk in header.allchildren():
                for line in hunk.allchildren():
                    self.toggleapply(line)

    def toggleallbetween(self):
        """toggle applied on or off for all items in range [lastapplied,
        current]."""
        if (
            not self.lastapplieditem
            or self.currentselecteditem == self.lastapplieditem
        ):
            # Treat this like a normal 'x'/' '
            self.toggleapply()
            return

        startitem = self.lastapplieditem
        enditem = self.currentselecteditem
        # Verify that enditem is "after" startitem, otherwise swap them.
        for direction in [b'forward', b'reverse']:
            nextitem = startitem.nextitem()
            while nextitem and nextitem != enditem:
                nextitem = nextitem.nextitem()
            if nextitem:
                break
            # Looks like we went the wrong direction :)
            startitem, enditem = enditem, startitem

        if not nextitem:
            # We didn't find a path going either forward or backward? Don't know
            # how this can happen, let's not crash though.
            return

        nextitem = startitem
        # Switch all items to be the opposite state of the currently selected
        # item. Specifically:
        #  [ ] startitem
        #  [x] middleitem
        #  [ ] enditem  <-- currently selected
        # This will turn all three on, since the currently selected item is off.
        # This does *not* invert each item (i.e. middleitem stays marked/on)
        desiredstate = not self.currentselecteditem.applied
        while nextitem != enditem.nextitem():
            if nextitem.applied != desiredstate:
                self.toggleapply(item=nextitem)
            nextitem = nextitem.nextitem()

    def togglefolded(self, item=None, foldparent=False):
        """toggle folded flag of specified item (defaults to currently
        selected)"""
        if item is None:
            item = self.currentselecteditem
        if foldparent or (isinstance(item, uiheader) and item.neverunfolded):
            if not isinstance(item, uiheader):
                # we need to select the parent item in this case
                self.currentselecteditem = item = item.parentitem()
            elif item.neverunfolded:
                item.neverunfolded = False

            # also fold any foldable children of the parent/current item
            if isinstance(item, uiheader):  # the original or 'new' item
                for child in item.allchildren():
                    child.folded = not item.folded

        if isinstance(item, (uiheader, uihunk)):
            item.folded = not item.folded

    def alignstring(self, instr, window):
        """
        add whitespace to the end of a string in order to make it fill
        the screen in the x direction.  the current cursor position is
        taken into account when making this calculation.  the string can span
        multiple lines.
        """
        y, xstart = window.getyx()
        width = self.xscreensize
        # turn tabs into spaces
        instr = instr.expandtabs(4)
        strwidth = encoding.colwidth(instr)
        numspaces = width - ((strwidth + xstart) % width)
        return instr + b" " * numspaces

    def printstring(
        self,
        window,
        text,
        fgcolor=None,
        bgcolor=None,
        pair=None,
        pairname=None,
        attrlist=None,
        towin=True,
        align=True,
        showwhtspc=False,
    ):
        """
        print the string, text, with the specified colors and attributes, to
        the specified curses window object.

        the foreground and background colors are of the form
        curses.color_xxxx, where xxxx is one of: [black, blue, cyan, green,
        magenta, red, white, yellow].  if pairname is provided, a color
        pair will be looked up in the self.colorpairnames dictionary.

        attrlist is a list containing text attributes in the form of
        curses.a_xxxx, where xxxx can be: [bold, dim, normal, standout,
        underline].

        if align == True, whitespace is added to the printed string such that
        the string stretches to the right border of the window.

        if showwhtspc == True, trailing whitespace of a string is highlighted.
        """
        # preprocess the text, converting tabs to spaces
        text = text.expandtabs(4)
        # strip \n, and convert control characters to ^[char] representation
        text = re.sub(
            br'[\x00-\x08\x0a-\x1f]',
            lambda m: b'^' + pycompat.sysbytes(chr(ord(m.group()) + 64)),
            text.strip(b'\n'),
        )

        if pair is not None:
            colorpair = pair
        elif pairname is not None:
            colorpair = self.colorpairnames[pairname]
        else:
            if fgcolor is None:
                fgcolor = -1
            if bgcolor is None:
                bgcolor = -1
            if (fgcolor, bgcolor) in self.colorpairs:
                colorpair = self.colorpairs[(fgcolor, bgcolor)]
            else:
                colorpair = self.getcolorpair(fgcolor, bgcolor)
        # add attributes if possible
        if attrlist is None:
            attrlist = []
        if colorpair < 256:
            # then it is safe to apply all attributes
            for textattr in attrlist:
                colorpair |= textattr
        else:
            # just apply a select few (safe?) attributes
            for textattr in (curses.A_UNDERLINE, curses.A_BOLD):
                if textattr in attrlist:
                    colorpair |= textattr

        y, xstart = self.chunkpad.getyx()
        t = b""  # variable for counting lines printed
        # if requested, show trailing whitespace
        if showwhtspc:
            origlen = len(text)
            text = text.rstrip(b' \n')  # tabs have already been expanded
            strippedlen = len(text)
            numtrailingspaces = origlen - strippedlen

        if towin:
            window.addstr(encoding.strfromlocal(text), colorpair)
        t += text

        if showwhtspc:
            wscolorpair = colorpair | curses.A_REVERSE
            if towin:
                for i in range(numtrailingspaces):
                    window.addch(curses.ACS_CKBOARD, wscolorpair)
            t += b" " * numtrailingspaces

        if align:
            if towin:
                extrawhitespace = self.alignstring(b"", window)
                window.addstr(extrawhitespace, colorpair)
            else:
                # need to use t, since the x position hasn't incremented
                extrawhitespace = self.alignstring(t, window)
            t += extrawhitespace

        # is reset to 0 at the beginning of printitem()

        linesprinted = (xstart + len(t)) // self.xscreensize
        self.linesprintedtopadsofar += linesprinted
        return t

    def _getstatuslinesegments(self):
        """-> [str]. return segments"""
        selected = self.currentselecteditem.applied
        spaceselect = _(b'space/enter: select')
        spacedeselect = _(b'space/enter: deselect')
        # Format the selected label into a place as long as the longer of the
        # two possible labels.  This may vary by language.
        spacelen = max(len(spaceselect), len(spacedeselect))
        selectedlabel = b'%-*s' % (
            spacelen,
            spacedeselect if selected else spaceselect,
        )
        segments = [
            _headermessages[self.operation],
            b'-',
            _(b'[x]=selected **=collapsed'),
            _(b'c: confirm'),
            _(b'q: abort'),
            _(b'arrow keys: move/expand/collapse'),
            selectedlabel,
            _(b'?: help'),
        ]
        return segments

    def _getstatuslines(self):
        """() -> [str]. return short help used in the top status window"""
        if self.errorstr is not None:
            lines = [self.errorstr, _(b'Press any key to continue')]
        else:
            # wrap segments to lines
            segments = self._getstatuslinesegments()
            width = self.xscreensize
            lines = []
            lastwidth = width
            for s in segments:
                w = encoding.colwidth(s)
                sep = b' ' * (1 + (s and s[0] not in b'-['))
                if lastwidth + w + len(sep) >= width:
                    lines.append(s)
                    lastwidth = w
                else:
                    lines[-1] += sep + s
                    lastwidth += w + len(sep)
        if len(lines) != self.numstatuslines:
            self.numstatuslines = len(lines)
            self.statuswin.resize(self.numstatuslines, self.xscreensize)
        return [stringutil.ellipsis(l, self.xscreensize - 1) for l in lines]

    def updatescreen(self):
        self.statuswin.erase()
        self.chunkpad.erase()

        printstring = self.printstring

        # print out the status lines at the top
        try:
            for line in self._getstatuslines():
                printstring(self.statuswin, line, pairname=b"legend")
            self.statuswin.refresh()
        except curses.error:
            pass
        if self.errorstr is not None:
            return

        # print out the patch in the remaining part of the window
        try:
            self.printitem()
            self.updatescroll()
            self.chunkpad.refresh(
                self.firstlineofpadtoprint,
                0,
                self.numstatuslines,
                0,
                self.yscreensize - self.numstatuslines,
                self.xscreensize - 1,
            )
        except curses.error:
            pass

    def getstatusprefixstring(self, item):
        """
        create a string to prefix a line with which indicates whether 'item'
        is applied and/or folded.
        """

        # create checkbox string
        if item.applied:
            if not isinstance(item, uihunkline) and item.partial:
                checkbox = b"[~]"
            else:
                checkbox = b"[x]"
        else:
            checkbox = b"[ ]"

        try:
            if item.folded:
                checkbox += b"**"
                if isinstance(item, uiheader):
                    # one of "m", "a", or "d" (modified, added, deleted)
                    filestatus = item.changetype

                    checkbox += filestatus + b" "
            else:
                checkbox += b"  "
                if isinstance(item, uiheader):
                    # add two more spaces for headers
                    checkbox += b"  "
        except AttributeError:  # not foldable
            checkbox += b"  "

        return checkbox

    def printheader(
        self, header, selected=False, towin=True, ignorefolding=False
    ):
        """
        print the header to the pad.  if countlines is True, don't print
        anything, but just count the number of lines which would be printed.
        """

        outstr = b""
        text = header.prettystr()
        chunkindex = self.chunklist.index(header)

        if chunkindex != 0 and not header.folded:
            # add separating line before headers
            outstr += self.printstring(
                self.chunkpad, b'_' * self.xscreensize, towin=towin, align=False
            )
        # select color-pair based on if the header is selected
        colorpair = self.getcolorpair(
            name=selected and b"selected" or b"normal", attrlist=[curses.A_BOLD]
        )

        # print out each line of the chunk, expanding it to screen width

        # number of characters to indent lines on this level by
        indentnumchars = 0
        checkbox = self.getstatusprefixstring(header)
        if not header.folded or ignorefolding:
            textlist = text.split(b"\n")
            linestr = checkbox + textlist[0]
        else:
            linestr = checkbox + header.filename()
        outstr += self.printstring(
            self.chunkpad, linestr, pair=colorpair, towin=towin
        )
        if not header.folded or ignorefolding:
            if len(textlist) > 1:
                for line in textlist[1:]:
                    linestr = b" " * (indentnumchars + len(checkbox)) + line
                    outstr += self.printstring(
                        self.chunkpad, linestr, pair=colorpair, towin=towin
                    )

        return outstr

    def printhunklinesbefore(
        self, hunk, selected=False, towin=True, ignorefolding=False
    ):
        """includes start/end line indicator"""
        outstr = b""
        # where hunk is in list of siblings
        hunkindex = hunk.header.hunks.index(hunk)

        if hunkindex != 0:
            # add separating line before headers
            outstr += self.printstring(
                self.chunkpad, b' ' * self.xscreensize, towin=towin, align=False
            )

        colorpair = self.getcolorpair(
            name=selected and b"selected" or b"normal", attrlist=[curses.A_BOLD]
        )

        # print out from-to line with checkbox
        checkbox = self.getstatusprefixstring(hunk)

        lineprefix = b" " * self.hunkindentnumchars + checkbox
        frtoline = b"   " + hunk.getfromtoline().strip(b"\n")

        outstr += self.printstring(
            self.chunkpad, lineprefix, towin=towin, align=False
        )  # add uncolored checkbox/indent
        outstr += self.printstring(
            self.chunkpad, frtoline, pair=colorpair, towin=towin
        )

        if hunk.folded and not ignorefolding:
            # skip remainder of output
            return outstr

        # print out lines of the chunk preceeding changed-lines
        for line in hunk.before:
            linestr = (
                b" " * (self.hunklineindentnumchars + len(checkbox)) + line
            )
            outstr += self.printstring(self.chunkpad, linestr, towin=towin)

        return outstr

    def printhunklinesafter(self, hunk, towin=True, ignorefolding=False):
        outstr = b""
        if hunk.folded and not ignorefolding:
            return outstr

        # a bit superfluous, but to avoid hard-coding indent amount
        checkbox = self.getstatusprefixstring(hunk)
        for line in hunk.after:
            linestr = (
                b" " * (self.hunklineindentnumchars + len(checkbox)) + line
            )
            outstr += self.printstring(self.chunkpad, linestr, towin=towin)

        return outstr

    def printhunkchangedline(self, hunkline, selected=False, towin=True):
        outstr = b""
        checkbox = self.getstatusprefixstring(hunkline)

        linestr = hunkline.prettystr().strip(b"\n")

        # select color-pair based on whether line is an addition/removal
        if selected:
            colorpair = self.getcolorpair(name=b"selected")
        elif linestr.startswith(b"+"):
            colorpair = self.getcolorpair(name=b"addition")
        elif linestr.startswith(b"-"):
            colorpair = self.getcolorpair(name=b"deletion")
        elif linestr.startswith(b"\\"):
            colorpair = self.getcolorpair(name=b"normal")

        lineprefix = b" " * self.hunklineindentnumchars + checkbox
        outstr += self.printstring(
            self.chunkpad, lineprefix, towin=towin, align=False
        )  # add uncolored checkbox/indent
        outstr += self.printstring(
            self.chunkpad, linestr, pair=colorpair, towin=towin, showwhtspc=True
        )
        return outstr

    def printitem(
        self, item=None, ignorefolding=False, recursechildren=True, towin=True
    ):
        """
        use __printitem() to print the the specified item.applied.
        if item is not specified, then print the entire patch.
        (hiding folded elements, etc. -- see __printitem() docstring)
        """

        if item is None:
            item = self.headerlist
        if recursechildren:
            self.linesprintedtopadsofar = 0

        outstr = []
        self.__printitem(
            item, ignorefolding, recursechildren, outstr, towin=towin
        )
        return b''.join(outstr)

    def outofdisplayedarea(self):
        y, _ = self.chunkpad.getyx()  # cursor location
        # * 2 here works but an optimization would be the max number of
        # consecutive non selectable lines
        # i.e the max number of context line for any hunk in the patch
        miny = min(0, self.firstlineofpadtoprint - self.yscreensize)
        maxy = self.firstlineofpadtoprint + self.yscreensize * 2
        return y < miny or y > maxy

    def handleselection(self, item, recursechildren):
        selected = item is self.currentselecteditem
        if selected and recursechildren:
            # assumes line numbering starting from line 0
            self.selecteditemstartline = self.linesprintedtopadsofar
            selecteditemlines = self.getnumlinesdisplayed(
                item, recursechildren=False
            )
            self.selecteditemendline = (
                self.selecteditemstartline + selecteditemlines - 1
            )
        return selected

    def __printitem(
        self, item, ignorefolding, recursechildren, outstr, towin=True
    ):
        """
        recursive method for printing out patch/header/hunk/hunk-line data to
        screen.  also returns a string with all of the content of the displayed
        patch (not including coloring, etc.).

        if ignorefolding is True, then folded items are printed out.

        if recursechildren is False, then only print the item without its
        child items.
        """

        if towin and self.outofdisplayedarea():
            return

        selected = self.handleselection(item, recursechildren)

        # patch object is a list of headers
        if isinstance(item, patch):
            if recursechildren:
                for hdr in item:
                    self.__printitem(
                        hdr, ignorefolding, recursechildren, outstr, towin
                    )
        # todo: eliminate all isinstance() calls
        if isinstance(item, uiheader):
            outstr.append(
                self.printheader(
                    item, selected, towin=towin, ignorefolding=ignorefolding
                )
            )
            if recursechildren:
                for hnk in item.hunks:
                    self.__printitem(
                        hnk, ignorefolding, recursechildren, outstr, towin
                    )
        elif isinstance(item, uihunk) and (
            (not item.header.folded) or ignorefolding
        ):
            # print the hunk data which comes before the changed-lines
            outstr.append(
                self.printhunklinesbefore(
                    item, selected, towin=towin, ignorefolding=ignorefolding
                )
            )
            if recursechildren:
                for l in item.changedlines:
                    self.__printitem(
                        l, ignorefolding, recursechildren, outstr, towin
                    )
                outstr.append(
                    self.printhunklinesafter(
                        item, towin=towin, ignorefolding=ignorefolding
                    )
                )
        elif isinstance(item, uihunkline) and (
            (not item.hunk.folded) or ignorefolding
        ):
            outstr.append(
                self.printhunkchangedline(item, selected, towin=towin)
            )

        return outstr

    def getnumlinesdisplayed(
        self, item=None, ignorefolding=False, recursechildren=True
    ):
        """
        return the number of lines which would be displayed if the item were
        to be printed to the display.  the item will not be printed to the
        display (pad).
        if no item is given, assume the entire patch.
        if ignorefolding is True, folded items will be unfolded when counting
        the number of lines.
        """

        # temporarily disable printing to windows by printstring
        patchdisplaystring = self.printitem(
            item, ignorefolding, recursechildren, towin=False
        )
        numlines = len(patchdisplaystring) // self.xscreensize
        return numlines

    def sigwinchhandler(self, n, frame):
        """handle window resizing"""
        try:
            self.xscreensize, self.yscreensize = scmutil.termsize(self.ui)
            self.statuswin.resize(self.numstatuslines, self.xscreensize)
            self.numpadlines = self.getnumlinesdisplayed(ignorefolding=True) + 1
            self.chunkpad = curses.newpad(self.numpadlines, self.xscreensize)
        except curses.error:
            pass

    def getcolorpair(
        self, fgcolor=None, bgcolor=None, name=None, attrlist=None
    ):
        """
        get a curses color pair, adding it to self.colorpairs if it is not
        already defined.  an optional string, name, can be passed as a shortcut
        for referring to the color-pair.  by default, if no arguments are
        specified, the white foreground / black background color-pair is
        returned.

        it is expected that this function will be used exclusively for
        initializing color pairs, and not curses.init_pair().

        attrlist is used to 'flavor' the returned color-pair.  this information
        is not stored in self.colorpairs.  it contains attribute values like
        curses.A_BOLD.
        """

        if (name is not None) and name in self.colorpairnames:
            # then get the associated color pair and return it
            colorpair = self.colorpairnames[name]
        else:
            if fgcolor is None:
                fgcolor = -1
            if bgcolor is None:
                bgcolor = -1
            if (fgcolor, bgcolor) in self.colorpairs:
                colorpair = self.colorpairs[(fgcolor, bgcolor)]
            else:
                pairindex = len(self.colorpairs) + 1
                if self.usecolor:
                    curses.init_pair(pairindex, fgcolor, bgcolor)
                    colorpair = self.colorpairs[
                        (fgcolor, bgcolor)
                    ] = curses.color_pair(pairindex)
                    if name is not None:
                        self.colorpairnames[name] = curses.color_pair(pairindex)
                else:
                    cval = 0
                    if name is not None:
                        if name == b'selected':
                            cval = curses.A_REVERSE
                        self.colorpairnames[name] = cval
                    colorpair = self.colorpairs[(fgcolor, bgcolor)] = cval

        # add attributes if possible
        if attrlist is None:
            attrlist = []
        if colorpair < 256:
            # then it is safe to apply all attributes
            for textattr in attrlist:
                colorpair |= textattr
        else:
            # just apply a select few (safe?) attributes
            for textattrib in (curses.A_UNDERLINE, curses.A_BOLD):
                if textattrib in attrlist:
                    colorpair |= textattrib
        return colorpair

    def initcolorpair(self, *args, **kwargs):
        """same as getcolorpair."""
        self.getcolorpair(*args, **kwargs)

    def helpwindow(self):
        """print a help window to the screen.  exit after any keypress."""
        helptext = _(
            b"""            [press any key to return to the patch-display]

The curses hunk selector allows you to interactively choose among the
changes you have made, and confirm only those changes you select for
further processing by the command you are running (such as commit,
shelve, or revert). After confirming the selected changes, the
unselected changes are still present in your working copy, so you can
use the hunk selector multiple times to split large changes into
smaller changesets. the following are valid keystrokes:

              x [space] : (un-)select item ([~]/[x] = partly/fully applied)
                [enter] : (un-)select item and go to next item of same type
                      A : (un-)select all items
                      X : (un-)select all items between current and most-recent
    up/down-arrow [k/j] : go to previous/next unfolded item
        pgup/pgdn [K/J] : go to previous/next item of same type
 right/left-arrow [l/h] : go to child item / parent item
 shift-left-arrow   [H] : go to parent header / fold selected header
                      g : go to the top
                      G : go to the bottom
                      f : fold / unfold item, hiding/revealing its children
                      F : fold / unfold parent item and all of its ancestors
                 ctrl-l : scroll the selected line to the top of the screen
                      m : edit / resume editing the commit message
                      e : edit the currently selected hunk
                      a : toggle all selections
                      c : confirm selected changes
                      r : review/edit and confirm selected changes
                      / : regex search for code or filename
                      n : next search result for code or filename
                      N : previous search result for code or filename
                      q : quit without confirming (no changes will be made)
                      ? : help (what you're currently reading)"""
        )

        helpwin = curses.newwin(self.yscreensize, 0, 0, 0)
        helplines = helptext.split(b"\n")
        helplines = helplines + [b" "] * (
            self.yscreensize - self.numstatuslines - len(helplines) - 1
        )
        try:
            for line in helplines:
                self.printstring(helpwin, line, pairname=b"legend")
        except curses.error:
            pass
        helpwin.refresh()
        try:
            with self.ui.timeblockedsection(b'crecord'):
                helpwin.getkey()
        except curses.error:
            pass

    def commitMessageWindow(self):
        """Create a temporary commit message editing window on the screen."""

        curses.raw()
        curses.def_prog_mode()
        self.commenttext = self.ui.edit(self.commenttext, self.ui.username())
        curses.cbreak()
        self.stdscr.refresh()
        self.stdscr.keypad(1)  # allow arrow-keys to continue to function

    def handlefirstlineevent(self):
        """
        Handle 'g' to navigate to the top most file in the ncurses window.
        """
        self.currentselecteditem = self.headerlist[0]
        currentitem = self.currentselecteditem
        # select the parent item recursively until we're at a header
        while True:
            nextitem = currentitem.parentitem()
            if nextitem is None:
                break
            else:
                currentitem = nextitem

        self.currentselecteditem = currentitem

    def handlelastlineevent(self):
        """
        Handle 'G' to navigate to the bottom most file/hunk/line depending
        on the whether the fold is active or not.

        If the bottom most file is folded, it navigates to that file and
        stops there. If the bottom most file is unfolded, it navigates to
        the bottom most hunk in that file and stops there. If the bottom most
        hunk is unfolded, it navigates to the bottom most line in that hunk.
        """
        currentitem = self.currentselecteditem
        nextitem = currentitem.nextitem()
        # select the child item recursively until we're at a footer
        while nextitem is not None:
            nextitem = currentitem.nextitem()
            if nextitem is None:
                break
            else:
                currentitem = nextitem

        self.currentselecteditem = currentitem
        self.recenterdisplayedarea()

    def confirmationwindow(self, windowtext):
        """display an informational window, then wait for and return a
        keypress."""

        confirmwin = curses.newwin(self.yscreensize, 0, 0, 0)
        try:
            lines = windowtext.split(b"\n")
            for line in lines:
                self.printstring(confirmwin, line, pairname=b"selected")
        except curses.error:
            pass
        self.stdscr.refresh()
        confirmwin.refresh()
        try:
            with self.ui.timeblockedsection(b'crecord'):
                response = chr(self.stdscr.getch())
        except ValueError:
            response = None

        return response

    def reviewcommit(self):
        """ask for 'y' to be pressed to confirm selected. return True if
        confirmed."""
        confirmtext = _(
            b"""If you answer yes to the following, your currently chosen patch chunks
will be loaded into an editor. To modify the patch, make the changes in your
editor and save. To accept the current patch as-is, close the editor without
saving.

note: don't add/remove lines unless you also modify the range information.
      failing to follow this rule will result in the commit aborting.

are you sure you want to review/edit and confirm the selected changes [yn]?
"""
        )
        with self.ui.timeblockedsection(b'crecord'):
            response = self.confirmationwindow(confirmtext)
        if response is None:
            response = "n"
        if response.lower().startswith("y"):
            return True
        else:
            return False

    def recenterdisplayedarea(self):
        """
        once we scrolled with pg up pg down we can be pointing outside of the
        display zone. we print the patch with towin=False to compute the
        location of the selected item even though it is outside of the displayed
        zone and then update the scroll.
        """
        self.printitem(towin=False)
        self.updatescroll()

    def toggleedit(self, item=None, test=False):
        """
        edit the currently selected chunk
        """

        def updateui(self):
            self.numpadlines = self.getnumlinesdisplayed(ignorefolding=True) + 1
            self.chunkpad = curses.newpad(self.numpadlines, self.xscreensize)
            self.updatescroll()
            self.stdscr.refresh()
            self.statuswin.refresh()
            self.stdscr.keypad(1)

        def editpatchwitheditor(self, chunk):
            if chunk is None:
                self.ui.write(_(b'cannot edit patch for whole file'))
                self.ui.write(b"\n")
                return None
            if chunk.header.binary():
                self.ui.write(_(b'cannot edit patch for binary file'))
                self.ui.write(b"\n")
                return None

            # write the initial patch
            patch = stringio()
            patch.write(diffhelptext + hunkhelptext)
            chunk.header.write(patch)
            chunk.write(patch)

            # start the editor and wait for it to complete
            try:
                patch = self.ui.edit(patch.getvalue(), b"", action=b"diff")
            except error.Abort as exc:
                self.errorstr = exc.message
                return None
            finally:
                self.stdscr.clear()
                self.stdscr.refresh()

            # remove comment lines
            patch = [
                line + b'\n'
                for line in patch.splitlines()
                if not line.startswith(b'#')
            ]
            return patchmod.parsepatch(patch)

        if item is None:
            item = self.currentselecteditem
        if isinstance(item, uiheader):
            return
        if isinstance(item, uihunkline):
            item = item.parentitem()
        if not isinstance(item, uihunk):
            return

        # To go back to that hunk or its replacement at the end of the edit
        itemindex = item.parentitem().hunks.index(item)

        beforeadded, beforeremoved = item.added, item.removed
        newpatches = editpatchwitheditor(self, item)
        if newpatches is None:
            if not test:
                updateui(self)
            return
        header = item.header
        editedhunkindex = header.hunks.index(item)
        hunksbefore = header.hunks[:editedhunkindex]
        hunksafter = header.hunks[editedhunkindex + 1 :]
        newpatchheader = newpatches[0]
        newhunks = [uihunk(h, header) for h in newpatchheader.hunks]
        newadded = sum([h.added for h in newhunks])
        newremoved = sum([h.removed for h in newhunks])
        offset = (newadded - beforeadded) - (newremoved - beforeremoved)

        for h in hunksafter:
            h.toline += offset
        for h in newhunks:
            h.folded = False
        header.hunks = hunksbefore + newhunks + hunksafter
        if self.emptypatch():
            header.hunks = hunksbefore + [item] + hunksafter
        self.currentselecteditem = header
        if len(header.hunks) > itemindex:
            self.currentselecteditem = header.hunks[itemindex]

        if not test:
            updateui(self)

    def emptypatch(self):
        item = self.headerlist
        if not item:
            return True
        for header in item:
            if header.hunks:
                return False
        return True

    def handlesearch(self):
        win = curses.newwin(1, self.xscreensize, self.yscreensize - 1, 0)
        win.echochar("/")

        curses.echo()
        curses.curs_set(1)
        self.regex = win.getstr() or None
        curses.noecho()
        curses.curs_set(0)

        if not self.showsearch(self.regex):
            self.printstring(
                win,
                _(b"Pattern not found (press ENTER)"),
                pairname=b"legend",
                align=False,
            )
            while win.getkey() not in ["\n", "KEY_ENTER"]:
                pass
        del win

        self.stdscr.clear()
        self.stdscr.refresh()

    def showsearch(self, regex, forward=True):
        if not regex:
            return

        moveattr = 'nextitem' if forward else 'previtem'
        currentitem = getattr(self.currentselecteditem, moveattr)(
            skipfolded=False
        )

        matches = None
        regex = re.compile(regex)
        while currentitem:
            matches = regex.search(currentitem.content)
            if matches:
                self.currentselecteditem = currentitem
                break
            currentitem = getattr(currentitem, moveattr)(skipfolded=False)

        # Whatever is selected should now be visible
        unfolded = self.currentselecteditem
        while matches and unfolded:
            unfolded.folded = False
            unfolded = unfolded.parentitem()

        return matches

    def searchdirection(self, failuremsg, forward=True):
        if not self.regex:
            return

        if not self.showsearch(self.regex, forward=forward):
            win = curses.newwin(1, self.xscreensize, self.yscreensize - 1, 0)
            self.printstring(win, failuremsg, pairname=b"legend", align=False)
            while win.getkey() not in ["\n", "KEY_ENTER"]:
                pass
            del win

            self.stdscr.clear()
            self.stdscr.refresh()

    def handlenextsearch(self):
        self.searchdirection(
            _(b"Next pattern not found (press ENTER)"), forward=True
        )

    def handleprevsearch(self):
        self.searchdirection(
            _(b"Previous pattern not found (press ENTER)"), forward=False
        )

    def handlekeypressed(self, keypressed, test=False):
        """
        Perform actions based on pressed keys.

        Return true to exit the main loop.
        """
        if keypressed in ["k", "KEY_UP"]:
            self.uparrowevent()
        elif keypressed in ["K", "KEY_PPAGE"]:
            self.uparrowshiftevent()
        elif keypressed in ["j", "KEY_DOWN"]:
            self.downarrowevent()
        elif keypressed in ["J", "KEY_NPAGE"]:
            self.downarrowshiftevent()
        elif keypressed in ["l", "KEY_RIGHT"]:
            self.rightarrowevent()
        elif keypressed in ["h", "KEY_LEFT"]:
            self.leftarrowevent()
        elif keypressed in ["H", "KEY_SLEFT"]:
            self.leftarrowshiftevent()
        elif keypressed in ["q"]:
            raise error.CanceledError(_(b'user quit'))
        elif keypressed in ['a']:
            self.flipselections()
        elif keypressed in ["c"]:
            return True
        elif keypressed in ["r"]:
            if self.reviewcommit():
                self.opts[b'review'] = True
                return True
        elif test and keypressed in ["R"]:
            self.opts[b'review'] = True
            return True
        elif keypressed in [" ", "x"]:
            self.toggleapply()
        elif keypressed in ["\n", "KEY_ENTER"]:
            self.toggleapply()
            self.nextsametype(test=test)
        elif keypressed in ["X"]:
            self.toggleallbetween()
        elif keypressed in ["A"]:
            self.toggleall()
        elif keypressed in ["e"]:
            self.toggleedit(test=test)
        elif keypressed in ["f"]:
            self.togglefolded()
        elif keypressed in ["F"]:
            self.togglefolded(foldparent=True)
        elif keypressed in ["m"]:
            self.commitMessageWindow()
        elif keypressed in ["/"]:
            self.handlesearch()
        elif keypressed in ["n"]:
            self.handlenextsearch()
        elif keypressed in ["N"]:
            self.handleprevsearch()
        elif keypressed in ["g", "KEY_HOME"]:
            self.handlefirstlineevent()
        elif keypressed in ["G", "KEY_END"]:
            self.handlelastlineevent()
        elif keypressed in ["?"]:
            self.helpwindow()
            self.stdscr.clear()
            self.stdscr.refresh()
        elif keypressed in [curses.ascii.ctrl("L")]:
            # scroll the current line to the top of the screen, and redraw
            # everything
            self.scrolllines(self.selecteditemstartline)
            self.stdscr.clear()
            self.stdscr.refresh()

    def main(self, stdscr):
        """
        method to be wrapped by curses.wrapper() for selecting chunks.
        """

        origsigwinch = sentinel = object()
        if hasattr(signal, 'SIGWINCH'):
            origsigwinch = signal.signal(signal.SIGWINCH, self.sigwinchhandler)
        try:
            return self._main(stdscr)
        finally:
            if origsigwinch is not sentinel:
                signal.signal(signal.SIGWINCH, origsigwinch)

    def _main(self, stdscr):
        self.stdscr = stdscr
        # error during initialization, cannot be printed in the curses
        # interface, it should be printed by the calling code
        self.initexc = None
        self.yscreensize, self.xscreensize = self.stdscr.getmaxyx()

        curses.start_color()
        try:
            curses.use_default_colors()
        except curses.error:
            self.usecolor = False

        # In some situations we may have some cruft left on the "alternate
        # screen" from another program (or previous iterations of ourself), and
        # we won't clear it if the scroll region is small enough to comfortably
        # fit on the terminal.
        self.stdscr.clear()

        # don't display the cursor
        try:
            curses.curs_set(0)
        except curses.error:
            pass

        # available colors: black, blue, cyan, green, magenta, white, yellow
        # init_pair(color_id, foreground_color, background_color)
        self.initcolorpair(None, None, name=b"normal")
        self.initcolorpair(
            curses.COLOR_WHITE, curses.COLOR_MAGENTA, name=b"selected"
        )
        self.initcolorpair(curses.COLOR_RED, None, name=b"deletion")
        self.initcolorpair(curses.COLOR_GREEN, None, name=b"addition")
        self.initcolorpair(
            curses.COLOR_WHITE, curses.COLOR_BLUE, name=b"legend"
        )
        # newwin([height, width,] begin_y, begin_x)
        self.statuswin = curses.newwin(self.numstatuslines, 0, 0, 0)
        self.statuswin.keypad(True)  # interpret arrow-key, etc. esc sequences

        # figure out how much space to allocate for the chunk-pad which is
        # used for displaying the patch

        # stupid hack to prevent getnumlinesdisplayed from failing
        self.chunkpad = curses.newpad(1, self.xscreensize)

        # add 1 so to account for last line text reaching end of line
        self.numpadlines = self.getnumlinesdisplayed(ignorefolding=True) + 1

        try:
            self.chunkpad = curses.newpad(self.numpadlines, self.xscreensize)
        except curses.error:
            self.initexc = fallbackerror(
                _(b'this diff is too large to be displayed')
            )
            return
        # initialize selecteditemendline (initial start-line is 0)
        self.selecteditemendline = self.getnumlinesdisplayed(
            self.currentselecteditem, recursechildren=False
        )

        while True:
            self.updatescreen()
            try:
                with self.ui.timeblockedsection(b'crecord'):
                    keypressed = self.statuswin.getkey()
                if self.errorstr is not None:
                    self.errorstr = None
                    continue
            except curses.error:
                keypressed = b"foobar"
            if self.handlekeypressed(keypressed):
                break

        if self.commenttext != b"":
            whitespaceremoved = re.sub(
                br"(?m)^\s.*(\n|$)", b"", self.commenttext
            )
            if whitespaceremoved != b"":
                self.opts[b'message'] = self.commenttext
