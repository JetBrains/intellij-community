# repoview.py - Filtered view of a localrepo object
#
# Copyright 2012 Pierre-Yves David <pierre-yves.david@ens-lyon.org>
#                Logilab SA        <contact@logilab.fr>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import copy
import weakref

from .i18n import _
from .node import (
    hex,
    nullrev,
)
from . import (
    error,
    obsolete,
    phases,
    pycompat,
    tags as tagsmod,
    util,
)
from .utils import repoviewutil


def hideablerevs(repo):
    """Revision candidates to be hidden

    This is a standalone function to allow extensions to wrap it.

    Because we use the set of immutable changesets as a fallback subset in
    branchmap (see mercurial.utils.repoviewutils.subsettable), you cannot set
    "public" changesets as "hideable". Doing so would break multiple code
    assertions and lead to crashes."""
    obsoletes = obsolete.getrevs(repo, b'obsolete')
    internals = repo._phasecache.getrevset(repo, phases.localhiddenphases)
    internals = frozenset(internals)
    return obsoletes | internals


def pinnedrevs(repo):
    """revisions blocking hidden changesets from being filtered"""

    cl = repo.changelog
    pinned = set()
    pinned.update([par.rev() for par in repo[None].parents()])
    pinned.update([cl.rev(bm) for bm in repo._bookmarks.values()])

    tags = {}
    tagsmod.readlocaltags(repo.ui, repo, tags, {})
    if tags:
        rev = cl.index.get_rev
        pinned.update(rev(t[0]) for t in tags.values())
        pinned.discard(None)

    # Avoid cycle: mercurial.filemerge -> mercurial.templater ->
    # mercurial.templatefuncs -> mercurial.revset -> mercurial.repoview ->
    # mercurial.mergestate -> mercurial.filemerge
    from . import mergestate

    ms = mergestate.mergestate.read(repo)
    if ms.active() and ms.unresolvedcount():
        for node in (ms.local, ms.other):
            rev = cl.index.get_rev(node)
            if rev is not None:
                pinned.add(rev)

    return pinned


def _revealancestors(pfunc, hidden, revs):
    """reveals contiguous chains of hidden ancestors of 'revs' by removing them
    from 'hidden'

    - pfunc(r): a funtion returning parent of 'r',
    - hidden: the (preliminary) hidden revisions, to be updated
    - revs: iterable of revnum,

    (Ancestors are revealed exclusively, i.e. the elements in 'revs' are
    *not* revealed)
    """
    stack = list(revs)
    while stack:
        for p in pfunc(stack.pop()):
            if p != nullrev and p in hidden:
                hidden.remove(p)
                stack.append(p)


def computehidden(repo, visibilityexceptions=None):
    """compute the set of hidden revision to filter

    During most operation hidden should be filtered."""
    assert not repo.changelog.filteredrevs

    hidden = hideablerevs(repo)
    if hidden:
        hidden = set(hidden - pinnedrevs(repo))
        if visibilityexceptions:
            hidden -= visibilityexceptions
        pfunc = repo.changelog.parentrevs
        mutable = repo._phasecache.getrevset(repo, phases.mutablephases)

        visible = mutable - hidden
        _revealancestors(pfunc, hidden, visible)
    return frozenset(hidden)


def computesecret(repo, visibilityexceptions=None):
    """compute the set of revision that can never be exposed through hgweb

    Changeset in the secret phase (or above) should stay unaccessible."""
    assert not repo.changelog.filteredrevs
    secrets = repo._phasecache.getrevset(repo, phases.remotehiddenphases)
    return frozenset(secrets)


def computeunserved(repo, visibilityexceptions=None):
    """compute the set of revision that should be filtered when used a server

    Secret and hidden changeset should not pretend to be here."""
    assert not repo.changelog.filteredrevs
    # fast path in simple case to avoid impact of non optimised code
    hiddens = filterrevs(repo, b'visible')
    secrets = filterrevs(repo, b'served.hidden')
    if secrets:
        return frozenset(hiddens | secrets)
    else:
        return hiddens


def computemutable(repo, visibilityexceptions=None):
    assert not repo.changelog.filteredrevs
    # fast check to avoid revset call on huge repo
    if repo._phasecache.hasnonpublicphases(repo):
        return frozenset(repo._phasecache.getrevset(repo, phases.mutablephases))
    return frozenset()


def computeimpactable(repo, visibilityexceptions=None):
    """Everything impactable by mutable revision

    The immutable filter still have some chance to get invalidated. This will
    happen when:

    - you garbage collect hidden changeset,
    - public phase is moved backward,
    - something is changed in the filtering (this could be fixed)

    This filter out any mutable changeset and any public changeset that may be
    impacted by something happening to a mutable revision.

    This is achieved by filtered everything with a revision number equal or
    higher than the first mutable changeset is filtered."""
    assert not repo.changelog.filteredrevs
    cl = repo.changelog
    firstmutable = len(cl)
    roots = repo._phasecache.nonpublicphaseroots(repo)
    if roots:
        firstmutable = min(firstmutable, min(roots))
    # protect from nullrev root
    firstmutable = max(0, firstmutable)
    return frozenset(range(firstmutable, len(cl)))


# function to compute filtered set
#
# When adding a new filter you MUST update the table at:
#     mercurial.utils.repoviewutil.subsettable
# Otherwise your filter will have to recompute all its branches cache
# from scratch (very slow).
filtertable = {
    b'visible': computehidden,
    b'visible-hidden': computehidden,
    b'served.hidden': computesecret,
    b'served': computeunserved,
    b'immutable': computemutable,
    b'base': computeimpactable,
}

# set of filter level that will include the working copy parent no matter what.
filter_has_wc = {b'visible', b'visible-hidden'}

_basefiltername = list(filtertable)


def extrafilter(ui):
    """initialize extra filter and return its id

    If extra filtering is configured, we make sure the associated filtered view
    are declared and return the associated id.
    """
    frevs = ui.config(b'experimental', b'extra-filter-revs')
    if frevs is None:
        return None

    fid = pycompat.sysbytes(util.DIGESTS[b'sha1'](frevs).hexdigest())[:12]

    combine = lambda fname: fname + b'%' + fid

    subsettable = repoviewutil.subsettable

    if combine(b'base') not in filtertable:
        for base_name in _basefiltername:

            def extrafilteredrevs(repo, *args, name=base_name, **kwargs):
                baserevs = filtertable[name](repo, *args, **kwargs)
                extrarevs = frozenset(repo.revs(frevs))
                return baserevs | extrarevs

            filtertable[combine(base_name)] = extrafilteredrevs
            if base_name in subsettable:
                subsettable[combine(base_name)] = combine(
                    subsettable[base_name]
                )
    return fid


def filterrevs(repo, filtername, visibilityexceptions=None):
    """returns set of filtered revision for this filter name

    visibilityexceptions is a set of revs which must are exceptions for
    hidden-state and must be visible. They are dynamic and hence we should not
    cache it's result"""
    if filtername not in repo.filteredrevcache:
        if repo.ui.configbool(b'devel', b'debug.repo-filters'):
            msg = b'computing revision filter for "%s"'
            msg %= filtername
            if repo.ui.tracebackflag and repo.ui.debugflag:
                # XXX use ui.write_err
                util.debugstacktrace(
                    msg,
                    f=repo.ui._fout,
                    otherf=repo.ui._ferr,
                    prefix=b'debug.filters: ',
                )
            else:
                repo.ui.debug(b'debug.filters: %s\n' % msg)
        func = filtertable[filtername]
        if visibilityexceptions:
            return func(repo.unfiltered, visibilityexceptions)
        repo.filteredrevcache[filtername] = func(repo.unfiltered())
    return repo.filteredrevcache[filtername]


def wrapchangelog(unfichangelog, filteredrevs):
    cl = copy.copy(unfichangelog)
    cl.filteredrevs = filteredrevs

    class filteredchangelog(filteredchangelogmixin, cl.__class__):
        pass

    cl.__class__ = filteredchangelog

    return cl


class filteredchangelogmixin:
    def tiprev(self):
        """filtered version of revlog.tiprev"""
        for i in range(len(self) - 1, -2, -1):
            if i not in self.filteredrevs:
                return i

    def __contains__(self, rev):
        """filtered version of revlog.__contains__"""
        return 0 <= rev < len(self) and rev not in self.filteredrevs

    def __iter__(self):
        """filtered version of revlog.__iter__"""

        def filterediter():
            for i in range(len(self)):
                if i not in self.filteredrevs:
                    yield i

        return filterediter()

    def revs(self, start=0, stop=None):
        """filtered version of revlog.revs"""
        for i in super(filteredchangelogmixin, self).revs(start, stop):
            if i not in self.filteredrevs:
                yield i

    def _checknofilteredinrevs(self, revs):
        """raise the appropriate error if 'revs' contains a filtered revision

        This returns a version of 'revs' to be used thereafter by the caller.
        In particular, if revs is an iterator, it is converted into a set.
        """
        if hasattr(revs, '__next__'):
            # Note that inspect.isgenerator() is not true for iterators,
            revs = set(revs)

        filteredrevs = self.filteredrevs
        if hasattr(revs, 'first'):  # smartset
            offenders = revs & filteredrevs
        else:
            offenders = filteredrevs.intersection(revs)

        for rev in offenders:
            raise error.FilteredIndexError(rev)
        return revs

    def _head_node_ids(self):
        # no Rust fast path implemented yet, so just loop in Python
        return [self.node(r) for r in self.headrevs()]

    def headrevs(self, revs=None):
        if revs is None:
            try:
                return self.index.headrevsfiltered(self.filteredrevs)
            # AttributeError covers non-c-extension environments and
            # old c extensions without filter handling.
            except AttributeError:
                return self._headrevs()

        revs = self._checknofilteredinrevs(revs)
        return super(filteredchangelogmixin, self).headrevs(revs)

    def strip(self, *args, **kwargs):
        # XXX make something better than assert
        # We can't expect proper strip behavior if we are filtered.
        assert not self.filteredrevs
        super(filteredchangelogmixin, self).strip(*args, **kwargs)

    def rev(self, node):
        """filtered version of revlog.rev"""
        r = super(filteredchangelogmixin, self).rev(node)
        if r in self.filteredrevs:
            raise error.FilteredLookupError(
                hex(node), self.display_id, _(b'filtered node')
            )
        return r

    def node(self, rev):
        """filtered version of revlog.node"""
        if rev in self.filteredrevs:
            raise error.FilteredIndexError(rev)
        return super(filteredchangelogmixin, self).node(rev)

    def linkrev(self, rev):
        """filtered version of revlog.linkrev"""
        if rev in self.filteredrevs:
            raise error.FilteredIndexError(rev)
        return super(filteredchangelogmixin, self).linkrev(rev)

    def parentrevs(self, rev):
        """filtered version of revlog.parentrevs"""
        if rev in self.filteredrevs:
            raise error.FilteredIndexError(rev)
        return super(filteredchangelogmixin, self).parentrevs(rev)

    def flags(self, rev):
        """filtered version of revlog.flags"""
        if rev in self.filteredrevs:
            raise error.FilteredIndexError(rev)
        return super(filteredchangelogmixin, self).flags(rev)


class repoview:
    """Provide a read/write view of a repo through a filtered changelog

    This object is used to access a filtered version of a repository without
    altering the original repository object itself. We can not alter the
    original object for two main reasons:
    - It prevents the use of a repo with multiple filters at the same time. In
      particular when multiple threads are involved.
    - It makes scope of the filtering harder to control.

    This object behaves very closely to the original repository. All attribute
    operations are done on the original repository:
    - An access to `repoview.someattr` actually returns `repo.someattr`,
    - A write to `repoview.someattr` actually sets value of `repo.someattr`,
    - A deletion of `repoview.someattr` actually drops `someattr`
      from `repo.__dict__`.

    The only exception is the `changelog` property. It is overridden to return
    a (surface) copy of `repo.changelog` with some revisions filtered. The
    `filtername` attribute of the view control the revisions that need to be
    filtered.  (the fact the changelog is copied is an implementation detail).

    Unlike attributes, this object intercepts all method calls. This means that
    all methods are run on the `repoview` object with the filtered `changelog`
    property. For this purpose the simple `repoview` class must be mixed with
    the actual class of the repository. This ensures that the resulting
    `repoview` object have the very same methods than the repo object. This
    leads to the property below.

        repoview.method() --> repo.__class__.method(repoview)

    The inheritance has to be done dynamically because `repo` can be of any
    subclasses of `localrepo`. Eg: `bundlerepo` or `statichttprepo`.
    """

    def __init__(self, repo, filtername, visibilityexceptions=None):
        if filtername is None:
            msg = "repoview should have a non-None filtername"
            raise error.ProgrammingError(msg)
        object.__setattr__(self, '_unfilteredrepo', repo)
        object.__setattr__(self, 'filtername', filtername)
        object.__setattr__(self, '_clcachekey', None)
        object.__setattr__(self, '_clcache', None)
        # revs which are exceptions and must not be hidden
        object.__setattr__(self, '_visibilityexceptions', visibilityexceptions)

    # not a propertycache on purpose we shall implement a proper cache later
    @property
    def changelog(self):
        """return a filtered version of the changeset

        this changelog must not be used for writing"""
        # some cache may be implemented later
        unfi = self._unfilteredrepo
        unfichangelog = unfi.changelog
        # bypass call to changelog.method
        unfiindex = unfichangelog.index
        unfilen = len(unfiindex)
        unfinode = unfiindex[unfilen - 1][7]
        with util.timedcm('repo filter for %s', self.filtername):
            revs = filterrevs(unfi, self.filtername, self._visibilityexceptions)
        cl = self._clcache
        newkey = (unfilen, unfinode, hash(revs), unfichangelog.is_delaying)
        # if cl.index is not unfiindex, unfi.changelog would be
        # recreated, and our clcache refers to garbage object
        if cl is not None and (
            cl.index is not unfiindex or newkey != self._clcachekey
        ):
            cl = None
        # could have been made None by the previous if
        if cl is None:
            # Only filter if there's something to filter
            cl = wrapchangelog(unfichangelog, revs) if revs else unfichangelog
            object.__setattr__(self, '_clcache', cl)
            object.__setattr__(self, '_clcachekey', newkey)
        return cl

    def unfiltered(self):
        """Return an unfiltered version of a repo"""
        return self._unfilteredrepo

    def filtered(self, name, visibilityexceptions=None):
        """Return a filtered version of a repository"""
        if name == self.filtername and not visibilityexceptions:
            return self
        return self.unfiltered().filtered(name, visibilityexceptions)

    def __repr__(self):
        return '<%s:%s %r>' % (
            self.__class__.__name__,
            pycompat.sysstr(self.filtername),
            self.unfiltered(),
        )

    # everything access are forwarded to the proxied repo
    def __getattr__(self, attr):
        return getattr(self._unfilteredrepo, attr)

    def __setattr__(self, attr, value):
        return setattr(self._unfilteredrepo, attr, value)

    def __delattr__(self, attr):
        return delattr(self._unfilteredrepo, attr)


# Dynamically created classes introduce memory cycles via __mro__. See
# https://bugs.python.org/issue17950.
# This need of the garbage collector can turn into memory leak in
# Python <3.4, which is the first version released with PEP 442.
_filteredrepotypes = weakref.WeakKeyDictionary()


def newtype(base):
    """Create a new type with the repoview mixin and the given base class"""
    ref = _filteredrepotypes.get(base)
    if ref is not None:
        cls = ref()
        if cls is not None:
            return cls

    class filteredrepo(repoview, base):
        pass

    _filteredrepotypes[base] = weakref.ref(filteredrepo)
    # do not reread from weakref to be 100% sure not to return None
    return filteredrepo
