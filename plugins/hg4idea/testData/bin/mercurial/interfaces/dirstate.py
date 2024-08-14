import contextlib

from . import util as interfaceutil


class idirstate(interfaceutil.Interface):
    def __init__(
        opener,
        ui,
        root,
        validate,
        sparsematchfn,
        nodeconstants,
        use_dirstate_v2,
        use_tracked_hint=False,
    ):
        """Create a new dirstate object.

        opener is an open()-like callable that can be used to open the
        dirstate file; root is the root of the directory tracked by
        the dirstate.
        """

    # TODO: all these private methods and attributes should be made
    # public or removed from the interface.
    _ignore = interfaceutil.Attribute("""Matcher for ignored files.""")
    is_changing_any = interfaceutil.Attribute(
        """True if any changes in progress."""
    )
    is_changing_parents = interfaceutil.Attribute(
        """True if parents changes in progress."""
    )
    is_changing_files = interfaceutil.Attribute(
        """True if file tracking changes in progress."""
    )

    def _ignorefiles():
        """Return a list of files containing patterns to ignore."""

    def _ignorefileandline(f):
        """Given a file `f`, return the ignore file and line that ignores it."""

    _checklink = interfaceutil.Attribute("""Callable for checking symlinks.""")
    _checkexec = interfaceutil.Attribute("""Callable for checking exec bits.""")

    @contextlib.contextmanager
    def changing_parents(repo):
        """Context manager for handling dirstate parents.

        If an exception occurs in the scope of the context manager,
        the incoherent dirstate won't be written when wlock is
        released.
        """

    @contextlib.contextmanager
    def changing_files(repo):
        """Context manager for handling dirstate files.

        If an exception occurs in the scope of the context manager,
        the incoherent dirstate won't be written when wlock is
        released.
        """

    def hasdir(d):
        pass

    def flagfunc(buildfallback):
        """build a callable that returns flags associated with a filename

        The information is extracted from three possible layers:
        1. the file system if it supports the information
        2. the "fallback" information stored in the dirstate if any
        3. a more expensive mechanism inferring the flags from the parents.
        """

    def getcwd():
        """Return the path from which a canonical path is calculated.

        This path should be used to resolve file patterns or to convert
        canonical paths back to file paths for display. It shouldn't be
        used to get real file paths. Use vfs functions instead.
        """

    def pathto(f, cwd=None):
        pass

    def get_entry(path):
        """return a DirstateItem for the associated path"""

    def __contains__(key):
        """Check if bytestring `key` is known to the dirstate."""

    def __iter__():
        """Iterate the dirstate's contained filenames as bytestrings."""

    def items():
        """Iterate the dirstate's entries as (filename, DirstateItem.

        As usual, filename is a bytestring.
        """

    iteritems = items

    def parents():
        pass

    def p1():
        pass

    def p2():
        pass

    def branch():
        pass

    def setparents(p1, p2=None):
        """Set dirstate parents to p1 and p2.

        When moving from two parents to one, "merged" entries a
        adjusted to normal and previous copy records discarded and
        returned by the call.

        See localrepo.setparents()
        """

    def setbranch(branch, transaction):
        pass

    def invalidate():
        """Causes the next access to reread the dirstate.

        This is different from localrepo.invalidatedirstate() because it always
        rereads the dirstate. Use localrepo.invalidatedirstate() if you want to
        check whether the dirstate has changed before rereading it."""

    def copy(source, dest):
        """Mark dest as a copy of source. Unmark dest if source is None."""

    def copied(file):
        pass

    def copies():
        pass

    def normalize(path, isknown=False, ignoremissing=False):
        """
        normalize the case of a pathname when on a casefolding filesystem

        isknown specifies whether the filename came from walking the
        disk, to avoid extra filesystem access.

        If ignoremissing is True, missing path are returned
        unchanged. Otherwise, we try harder to normalize possibly
        existing path components.

        The normalized case is determined based on the following precedence:

        - version of name already stored in the dirstate
        - version of name stored on disk
        - version provided via command arguments
        """

    def clear():
        pass

    def rebuild(parent, allfiles, changedfiles=None):
        pass

    def write(tr):
        pass

    def addparentchangecallback(category, callback):
        """add a callback to be called when the wd parents are changed

        Callback will be called with the following arguments:
            dirstate, (oldp1, oldp2), (newp1, newp2)

        Category is a unique identifier to allow overwriting an old callback
        with a newer callback.
        """

    def walk(match, subrepos, unknown, ignored, full=True):
        """
        Walk recursively through the directory tree, finding all files
        matched by match.

        If full is False, maybe skip some known-clean files.

        Return a dict mapping filename to stat-like object (either
        mercurial.osutil.stat instance or return value of os.stat()).

        """

    def status(match, subrepos, ignored, clean, unknown):
        """Determine the status of the working copy relative to the
        dirstate and return a pair of (unsure, status), where status is of type
        scmutil.status and:

          unsure:
            files that might have been modified since the dirstate was
            written, but need to be read to be sure (size is the same
            but mtime differs)
          status.modified:
            files that have definitely been modified since the dirstate
            was written (different size or mode)
          status.clean:
            files that have definitely not been modified since the
            dirstate was written
        """

    def matches(match):
        """
        return files in the dirstate (in whatever state) filtered by match
        """

    def verify(m1, m2, p1, narrow_matcher=None):
        """
        check the dirstate contents against the parent manifest and yield errors
        """
