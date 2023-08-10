# smartset.py - data structure for revision set
#
# Copyright 2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from .pycompat import getattr
from . import (
    encoding,
    error,
    pycompat,
    util,
)
from .utils import stringutil


def _typename(o):
    return pycompat.sysbytes(type(o).__name__).lstrip(b'_')


class abstractsmartset(object):
    def __nonzero__(self):
        """True if the smartset is not empty"""
        raise NotImplementedError()

    __bool__ = __nonzero__

    def __contains__(self, rev):
        """provide fast membership testing"""
        raise NotImplementedError()

    def __iter__(self):
        """iterate the set in the order it is supposed to be iterated"""
        raise NotImplementedError()

    # Attributes containing a function to perform a fast iteration in a given
    # direction. A smartset can have none, one, or both defined.
    #
    # Default value is None instead of a function returning None to avoid
    # initializing an iterator just for testing if a fast method exists.
    fastasc = None
    fastdesc = None

    def isascending(self):
        """True if the set will iterate in ascending order"""
        raise NotImplementedError()

    def isdescending(self):
        """True if the set will iterate in descending order"""
        raise NotImplementedError()

    def istopo(self):
        """True if the set will iterate in topographical order"""
        raise NotImplementedError()

    def min(self):
        """return the minimum element in the set"""
        if self.fastasc is None:
            v = min(self)
        else:
            for v in self.fastasc():
                break
            else:
                raise ValueError(b'arg is an empty sequence')
        self.min = lambda: v
        return v

    def max(self):
        """return the maximum element in the set"""
        if self.fastdesc is None:
            return max(self)
        else:
            for v in self.fastdesc():
                break
            else:
                raise ValueError(b'arg is an empty sequence')
        self.max = lambda: v
        return v

    def first(self):
        """return the first element in the set (user iteration perspective)

        Return None if the set is empty"""
        raise NotImplementedError()

    def last(self):
        """return the last element in the set (user iteration perspective)

        Return None if the set is empty"""
        raise NotImplementedError()

    def __len__(self):
        """return the length of the smartsets

        This can be expensive on smartset that could be lazy otherwise."""
        raise NotImplementedError()

    def reverse(self):
        """reverse the expected iteration order"""
        raise NotImplementedError()

    def sort(self, reverse=False):
        """get the set to iterate in an ascending or descending order"""
        raise NotImplementedError()

    def __and__(self, other):
        """Returns a new object with the intersection of the two collections.

        This is part of the mandatory API for smartset."""
        if isinstance(other, fullreposet):
            return self
        return self.filter(other.__contains__, condrepr=other, cache=False)

    def __add__(self, other):
        """Returns a new object with the union of the two collections.

        This is part of the mandatory API for smartset."""
        return addset(self, other)

    def __sub__(self, other):
        """Returns a new object with the substraction of the two collections.

        This is part of the mandatory API for smartset."""
        c = other.__contains__
        return self.filter(
            lambda r: not c(r), condrepr=(b'<not %r>', other), cache=False
        )

    def filter(self, condition, condrepr=None, cache=True):
        """Returns this smartset filtered by condition as a new smartset.

        `condition` is a callable which takes a revision number and returns a
        boolean. Optional `condrepr` provides a printable representation of
        the given `condition`.

        This is part of the mandatory API for smartset."""
        # builtin cannot be cached. but do not needs to
        if cache and util.safehasattr(condition, b'__code__'):
            condition = util.cachefunc(condition)
        return filteredset(self, condition, condrepr)

    def slice(self, start, stop):
        """Return new smartset that contains selected elements from this set"""
        if start < 0 or stop < 0:
            raise error.ProgrammingError(b'negative index not allowed')
        return self._slice(start, stop)

    def _slice(self, start, stop):
        # sub classes may override this. start and stop must not be negative,
        # but start > stop is allowed, which should be an empty set.
        ys = []
        it = iter(self)
        for x in pycompat.xrange(start):
            y = next(it, None)
            if y is None:
                break
        for x in pycompat.xrange(stop - start):
            y = next(it, None)
            if y is None:
                break
            ys.append(y)
        return baseset(ys, datarepr=(b'slice=%d:%d %r', start, stop, self))


class baseset(abstractsmartset):
    """Basic data structure that represents a revset and contains the basic
    operation that it should be able to perform.

    Every method in this class should be implemented by any smartset class.

    This class could be constructed by an (unordered) set, or an (ordered)
    list-like object. If a set is provided, it'll be sorted lazily.

    >>> x = [4, 0, 7, 6]
    >>> y = [5, 6, 7, 3]

    Construct by a set:
    >>> xs = baseset(set(x))
    >>> ys = baseset(set(y))
    >>> [list(i) for i in [xs + ys, xs & ys, xs - ys]]
    [[0, 4, 6, 7, 3, 5], [6, 7], [0, 4]]
    >>> [type(i).__name__ for i in [xs + ys, xs & ys, xs - ys]]
    ['addset', 'baseset', 'baseset']

    Construct by a list-like:
    >>> xs = baseset(x)
    >>> ys = baseset(i for i in y)
    >>> [list(i) for i in [xs + ys, xs & ys, xs - ys]]
    [[4, 0, 7, 6, 5, 3], [7, 6], [4, 0]]
    >>> [type(i).__name__ for i in [xs + ys, xs & ys, xs - ys]]
    ['addset', 'filteredset', 'filteredset']

    Populate "_set" fields in the lists so set optimization may be used:
    >>> [1 in xs, 3 in ys]
    [False, True]

    Without sort(), results won't be changed:
    >>> [list(i) for i in [xs + ys, xs & ys, xs - ys]]
    [[4, 0, 7, 6, 5, 3], [7, 6], [4, 0]]
    >>> [type(i).__name__ for i in [xs + ys, xs & ys, xs - ys]]
    ['addset', 'filteredset', 'filteredset']

    With sort(), set optimization could be used:
    >>> xs.sort(reverse=True)
    >>> [list(i) for i in [xs + ys, xs & ys, xs - ys]]
    [[7, 6, 4, 0, 5, 3], [7, 6], [4, 0]]
    >>> [type(i).__name__ for i in [xs + ys, xs & ys, xs - ys]]
    ['addset', 'baseset', 'baseset']

    >>> ys.sort()
    >>> [list(i) for i in [xs + ys, xs & ys, xs - ys]]
    [[7, 6, 4, 0, 3, 5], [7, 6], [4, 0]]
    >>> [type(i).__name__ for i in [xs + ys, xs & ys, xs - ys]]
    ['addset', 'baseset', 'baseset']

    istopo is preserved across set operations
    >>> xs = baseset(set(x), istopo=True)
    >>> rs = xs & ys
    >>> type(rs).__name__
    'baseset'
    >>> rs._istopo
    True
    """

    def __init__(self, data=(), datarepr=None, istopo=False):
        """
        datarepr: a tuple of (format, obj, ...), a function or an object that
                  provides a printable representation of the given data.
        """
        self._ascending = None
        self._istopo = istopo
        if isinstance(data, set):
            # converting set to list has a cost, do it lazily
            self._set = data
            # set has no order we pick one for stability purpose
            self._ascending = True
        else:
            if not isinstance(data, list):
                data = list(data)
            self._list = data
        self._datarepr = datarepr

    @util.propertycache
    def _set(self):
        return set(self._list)

    @util.propertycache
    def _asclist(self):
        asclist = self._list[:]
        asclist.sort()
        return asclist

    @util.propertycache
    def _list(self):
        # _list is only lazily constructed if we have _set
        assert '_set' in self.__dict__
        return list(self._set)

    def __iter__(self):
        if self._ascending is None:
            return iter(self._list)
        elif self._ascending:
            return iter(self._asclist)
        else:
            return reversed(self._asclist)

    def fastasc(self):
        return iter(self._asclist)

    def fastdesc(self):
        return reversed(self._asclist)

    @util.propertycache
    def __contains__(self):
        return self._set.__contains__

    def __nonzero__(self):
        return bool(len(self))

    __bool__ = __nonzero__

    def sort(self, reverse=False):
        self._ascending = not bool(reverse)
        self._istopo = False

    def reverse(self):
        if self._ascending is None:
            self._list.reverse()
        else:
            self._ascending = not self._ascending
        self._istopo = False

    def __len__(self):
        if '_list' in self.__dict__:
            return len(self._list)
        else:
            return len(self._set)

    def isascending(self):
        """Returns True if the collection is ascending order, False if not.

        This is part of the mandatory API for smartset."""
        if len(self) <= 1:
            return True
        return self._ascending is not None and self._ascending

    def isdescending(self):
        """Returns True if the collection is descending order, False if not.

        This is part of the mandatory API for smartset."""
        if len(self) <= 1:
            return True
        return self._ascending is not None and not self._ascending

    def istopo(self):
        """Is the collection is in topographical order or not.

        This is part of the mandatory API for smartset."""
        if len(self) <= 1:
            return True
        return self._istopo

    def first(self):
        if self:
            if self._ascending is None:
                return self._list[0]
            elif self._ascending:
                return self._asclist[0]
            else:
                return self._asclist[-1]
        return None

    def last(self):
        if self:
            if self._ascending is None:
                return self._list[-1]
            elif self._ascending:
                return self._asclist[-1]
            else:
                return self._asclist[0]
        return None

    def _fastsetop(self, other, op):
        # try to use native set operations as fast paths
        if (
            type(other) is baseset
            and '_set' in other.__dict__
            and '_set' in self.__dict__
            and self._ascending is not None
        ):
            s = baseset(
                data=getattr(self._set, op)(other._set), istopo=self._istopo
            )
            s._ascending = self._ascending
        else:
            s = getattr(super(baseset, self), op)(other)
        return s

    def __and__(self, other):
        return self._fastsetop(other, b'__and__')

    def __sub__(self, other):
        return self._fastsetop(other, b'__sub__')

    def _slice(self, start, stop):
        # creating new list should be generally cheaper than iterating items
        if self._ascending is None:
            return baseset(self._list[start:stop], istopo=self._istopo)

        data = self._asclist
        if not self._ascending:
            start, stop = max(len(data) - stop, 0), max(len(data) - start, 0)
        s = baseset(data[start:stop], istopo=self._istopo)
        s._ascending = self._ascending
        return s

    @encoding.strmethod
    def __repr__(self):
        d = {None: b'', False: b'-', True: b'+'}[self._ascending]
        s = stringutil.buildrepr(self._datarepr)
        if not s:
            l = self._list
            # if _list has been built from a set, it might have a different
            # order from one python implementation to another.
            # We fallback to the sorted version for a stable output.
            if self._ascending is not None:
                l = self._asclist
            s = pycompat.byterepr(l)
        return b'<%s%s %s>' % (_typename(self), d, s)


class filteredset(abstractsmartset):
    """Duck type for baseset class which iterates lazily over the revisions in
    the subset and contains a function which tests for membership in the
    revset
    """

    def __init__(self, subset, condition=lambda x: True, condrepr=None):
        """
        condition: a function that decide whether a revision in the subset
                   belongs to the revset or not.
        condrepr: a tuple of (format, obj, ...), a function or an object that
                  provides a printable representation of the given condition.
        """
        self._subset = subset
        self._condition = condition
        self._condrepr = condrepr

    def __contains__(self, x):
        return x in self._subset and self._condition(x)

    def __iter__(self):
        return self._iterfilter(self._subset)

    def _iterfilter(self, it):
        cond = self._condition
        for x in it:
            if cond(x):
                yield x

    @property
    def fastasc(self):
        it = self._subset.fastasc
        if it is None:
            return None
        return lambda: self._iterfilter(it())

    @property
    def fastdesc(self):
        it = self._subset.fastdesc
        if it is None:
            return None
        return lambda: self._iterfilter(it())

    def __nonzero__(self):
        fast = None
        candidates = [
            self.fastasc if self.isascending() else None,
            self.fastdesc if self.isdescending() else None,
            self.fastasc,
            self.fastdesc,
        ]
        for candidate in candidates:
            if candidate is not None:
                fast = candidate
                break

        if fast is not None:
            it = fast()
        else:
            it = self

        for r in it:
            return True
        return False

    __bool__ = __nonzero__

    def __len__(self):
        # Basic implementation to be changed in future patches.
        # until this gets improved, we use generator expression
        # here, since list comprehensions are free to call __len__ again
        # causing infinite recursion
        l = baseset(r for r in self)
        return len(l)

    def sort(self, reverse=False):
        self._subset.sort(reverse=reverse)

    def reverse(self):
        self._subset.reverse()

    def isascending(self):
        return self._subset.isascending()

    def isdescending(self):
        return self._subset.isdescending()

    def istopo(self):
        return self._subset.istopo()

    def first(self):
        for x in self:
            return x
        return None

    def last(self):
        it = None
        if self.isascending():
            it = self.fastdesc
        elif self.isdescending():
            it = self.fastasc
        if it is not None:
            for x in it():
                return x
            return None  # empty case
        else:
            x = None
            for x in self:
                pass
            return x

    @encoding.strmethod
    def __repr__(self):
        xs = [pycompat.byterepr(self._subset)]
        s = stringutil.buildrepr(self._condrepr)
        if s:
            xs.append(s)
        return b'<%s %s>' % (_typename(self), b', '.join(xs))


def _iterordered(ascending, iter1, iter2):
    """produce an ordered iteration from two iterators with the same order

    The ascending is used to indicated the iteration direction.
    """
    choice = max
    if ascending:
        choice = min

    val1 = None
    val2 = None
    try:
        # Consume both iterators in an ordered way until one is empty
        while True:
            if val1 is None:
                val1 = next(iter1)
            if val2 is None:
                val2 = next(iter2)
            n = choice(val1, val2)
            yield n
            if val1 == n:
                val1 = None
            if val2 == n:
                val2 = None
    except StopIteration:
        # Flush any remaining values and consume the other one
        it = iter2
        if val1 is not None:
            yield val1
            it = iter1
        elif val2 is not None:
            # might have been equality and both are empty
            yield val2
        for val in it:
            yield val


class addset(abstractsmartset):
    """Represent the addition of two sets

    Wrapper structure for lazily adding two structures without losing much
    performance on the __contains__ method

    If the ascending attribute is set, that means the two structures are
    ordered in either an ascending or descending way. Therefore, we can add
    them maintaining the order by iterating over both at the same time

    >>> xs = baseset([0, 3, 2])
    >>> ys = baseset([5, 2, 4])

    >>> rs = addset(xs, ys)
    >>> bool(rs), 0 in rs, 1 in rs, 5 in rs, rs.first(), rs.last()
    (True, True, False, True, 0, 4)
    >>> rs = addset(xs, baseset([]))
    >>> bool(rs), 0 in rs, 1 in rs, rs.first(), rs.last()
    (True, True, False, 0, 2)
    >>> rs = addset(baseset([]), baseset([]))
    >>> bool(rs), 0 in rs, rs.first(), rs.last()
    (False, False, None, None)

    iterate unsorted:
    >>> rs = addset(xs, ys)
    >>> # (use generator because pypy could call len())
    >>> list(x for x in rs)  # without _genlist
    [0, 3, 2, 5, 4]
    >>> assert not rs._genlist
    >>> len(rs)
    5
    >>> [x for x in rs]  # with _genlist
    [0, 3, 2, 5, 4]
    >>> assert rs._genlist

    iterate ascending:
    >>> rs = addset(xs, ys, ascending=True)
    >>> # (use generator because pypy could call len())
    >>> list(x for x in rs), list(x for x in rs.fastasc())  # without _asclist
    ([0, 2, 3, 4, 5], [0, 2, 3, 4, 5])
    >>> assert not rs._asclist
    >>> len(rs)
    5
    >>> [x for x in rs], [x for x in rs.fastasc()]
    ([0, 2, 3, 4, 5], [0, 2, 3, 4, 5])
    >>> assert rs._asclist

    iterate descending:
    >>> rs = addset(xs, ys, ascending=False)
    >>> # (use generator because pypy could call len())
    >>> list(x for x in rs), list(x for x in rs.fastdesc())  # without _asclist
    ([5, 4, 3, 2, 0], [5, 4, 3, 2, 0])
    >>> assert not rs._asclist
    >>> len(rs)
    5
    >>> [x for x in rs], [x for x in rs.fastdesc()]
    ([5, 4, 3, 2, 0], [5, 4, 3, 2, 0])
    >>> assert rs._asclist

    iterate ascending without fastasc:
    >>> rs = addset(xs, generatorset(ys), ascending=True)
    >>> assert rs.fastasc is None
    >>> [x for x in rs]
    [0, 2, 3, 4, 5]

    iterate descending without fastdesc:
    >>> rs = addset(generatorset(xs), ys, ascending=False)
    >>> assert rs.fastdesc is None
    >>> [x for x in rs]
    [5, 4, 3, 2, 0]
    """

    def __init__(self, revs1, revs2, ascending=None):
        self._r1 = revs1
        self._r2 = revs2
        self._iter = None
        self._ascending = ascending
        self._genlist = None
        self._asclist = None

    def __len__(self):
        return len(self._list)

    def __nonzero__(self):
        return bool(self._r1) or bool(self._r2)

    __bool__ = __nonzero__

    @util.propertycache
    def _list(self):
        if not self._genlist:
            self._genlist = baseset(iter(self))
        return self._genlist

    def __iter__(self):
        """Iterate over both collections without repeating elements

        If the ascending attribute is not set, iterate over the first one and
        then over the second one checking for membership on the first one so we
        dont yield any duplicates.

        If the ascending attribute is set, iterate over both collections at the
        same time, yielding only one value at a time in the given order.
        """
        if self._ascending is None:
            if self._genlist:
                return iter(self._genlist)

            def arbitraryordergen():
                for r in self._r1:
                    yield r
                inr1 = self._r1.__contains__
                for r in self._r2:
                    if not inr1(r):
                        yield r

            return arbitraryordergen()
        # try to use our own fast iterator if it exists
        self._trysetasclist()
        if self._ascending:
            attr = b'fastasc'
        else:
            attr = b'fastdesc'
        it = getattr(self, attr)
        if it is not None:
            return it()
        # maybe half of the component supports fast
        # get iterator for _r1
        iter1 = getattr(self._r1, attr)
        if iter1 is None:
            # let's avoid side effect (not sure it matters)
            iter1 = iter(sorted(self._r1, reverse=not self._ascending))
        else:
            iter1 = iter1()
        # get iterator for _r2
        iter2 = getattr(self._r2, attr)
        if iter2 is None:
            # let's avoid side effect (not sure it matters)
            iter2 = iter(sorted(self._r2, reverse=not self._ascending))
        else:
            iter2 = iter2()
        return _iterordered(self._ascending, iter1, iter2)

    def _trysetasclist(self):
        """populate the _asclist attribute if possible and necessary"""
        if self._genlist is not None and self._asclist is None:
            self._asclist = sorted(self._genlist)

    @property
    def fastasc(self):
        self._trysetasclist()
        if self._asclist is not None:
            return self._asclist.__iter__
        iter1 = self._r1.fastasc
        iter2 = self._r2.fastasc
        if None in (iter1, iter2):
            return None
        return lambda: _iterordered(True, iter1(), iter2())

    @property
    def fastdesc(self):
        self._trysetasclist()
        if self._asclist is not None:
            return self._asclist.__reversed__
        iter1 = self._r1.fastdesc
        iter2 = self._r2.fastdesc
        if None in (iter1, iter2):
            return None
        return lambda: _iterordered(False, iter1(), iter2())

    def __contains__(self, x):
        return x in self._r1 or x in self._r2

    def sort(self, reverse=False):
        """Sort the added set

        For this we use the cached list with all the generated values and if we
        know they are ascending or descending we can sort them in a smart way.
        """
        self._ascending = not reverse

    def isascending(self):
        return self._ascending is not None and self._ascending

    def isdescending(self):
        return self._ascending is not None and not self._ascending

    def istopo(self):
        # not worth the trouble asserting if the two sets combined are still
        # in topographical order. Use the sort() predicate to explicitly sort
        # again instead.
        return False

    def reverse(self):
        if self._ascending is None:
            self._list.reverse()
        else:
            self._ascending = not self._ascending

    def first(self):
        for x in self:
            return x
        return None

    def last(self):
        self.reverse()
        val = self.first()
        self.reverse()
        return val

    @encoding.strmethod
    def __repr__(self):
        d = {None: b'', False: b'-', True: b'+'}[self._ascending]
        return b'<%s%s %r, %r>' % (_typename(self), d, self._r1, self._r2)


class generatorset(abstractsmartset):
    """Wrap a generator for lazy iteration

    Wrapper structure for generators that provides lazy membership and can
    be iterated more than once.
    When asked for membership it generates values until either it finds the
    requested one or has gone through all the elements in the generator

    >>> xs = generatorset([0, 1, 4], iterasc=True)
    >>> assert xs.last() == xs.last()
    >>> xs.last()  # cached
    4
    """

    def __new__(cls, gen, iterasc=None):
        if iterasc is None:
            typ = cls
        elif iterasc:
            typ = _generatorsetasc
        else:
            typ = _generatorsetdesc

        return super(generatorset, cls).__new__(typ)

    def __init__(self, gen, iterasc=None):
        """
        gen: a generator producing the values for the generatorset.
        """
        self._gen = gen
        self._asclist = None
        self._cache = {}
        self._genlist = []
        self._finished = False
        self._ascending = True

    def __nonzero__(self):
        # Do not use 'for r in self' because it will enforce the iteration
        # order (default ascending), possibly unrolling a whole descending
        # iterator.
        if self._genlist:
            return True
        for r in self._consumegen():
            return True
        return False

    __bool__ = __nonzero__

    def __contains__(self, x):
        if x in self._cache:
            return self._cache[x]

        # Use new values only, as existing values would be cached.
        for l in self._consumegen():
            if l == x:
                return True

        self._cache[x] = False
        return False

    def __iter__(self):
        if self._ascending:
            it = self.fastasc
        else:
            it = self.fastdesc
        if it is not None:
            return it()
        # we need to consume the iterator
        for x in self._consumegen():
            pass
        # recall the same code
        return iter(self)

    def _iterator(self):
        if self._finished:
            return iter(self._genlist)

        # We have to use this complex iteration strategy to allow multiple
        # iterations at the same time. We need to be able to catch revision
        # removed from _consumegen and added to genlist in another instance.
        #
        # Getting rid of it would provide an about 15% speed up on this
        # iteration.
        genlist = self._genlist
        nextgen = self._consumegen()
        _len, _next = len, next  # cache global lookup

        def gen():
            i = 0
            while True:
                if i < _len(genlist):
                    yield genlist[i]
                else:
                    try:
                        yield _next(nextgen)
                    except StopIteration:
                        return
                i += 1

        return gen()

    def _consumegen(self):
        cache = self._cache
        genlist = self._genlist.append
        for item in self._gen:
            cache[item] = True
            genlist(item)
            yield item
        if not self._finished:
            self._finished = True
            asc = self._genlist[:]
            asc.sort()
            self._asclist = asc
            self.fastasc = asc.__iter__
            self.fastdesc = asc.__reversed__

    def __len__(self):
        for x in self._consumegen():
            pass
        return len(self._genlist)

    def sort(self, reverse=False):
        self._ascending = not reverse

    def reverse(self):
        self._ascending = not self._ascending

    def isascending(self):
        return self._ascending

    def isdescending(self):
        return not self._ascending

    def istopo(self):
        # not worth the trouble asserting if the two sets combined are still
        # in topographical order. Use the sort() predicate to explicitly sort
        # again instead.
        return False

    def first(self):
        if self._ascending:
            it = self.fastasc
        else:
            it = self.fastdesc
        if it is None:
            # we need to consume all and try again
            for x in self._consumegen():
                pass
            return self.first()
        return next(it(), None)

    def last(self):
        if self._ascending:
            it = self.fastdesc
        else:
            it = self.fastasc
        if it is None:
            # we need to consume all and try again
            for x in self._consumegen():
                pass
            return self.last()
        return next(it(), None)

    @encoding.strmethod
    def __repr__(self):
        d = {False: b'-', True: b'+'}[self._ascending]
        return b'<%s%s>' % (_typename(self), d)


class _generatorsetasc(generatorset):
    """Special case of generatorset optimized for ascending generators."""

    fastasc = generatorset._iterator

    def __contains__(self, x):
        if x in self._cache:
            return self._cache[x]

        # Use new values only, as existing values would be cached.
        for l in self._consumegen():
            if l == x:
                return True
            if l > x:
                break

        self._cache[x] = False
        return False


class _generatorsetdesc(generatorset):
    """Special case of generatorset optimized for descending generators."""

    fastdesc = generatorset._iterator

    def __contains__(self, x):
        if x in self._cache:
            return self._cache[x]

        # Use new values only, as existing values would be cached.
        for l in self._consumegen():
            if l == x:
                return True
            if l < x:
                break

        self._cache[x] = False
        return False


def spanset(repo, start=0, end=None):
    """Create a spanset that represents a range of repository revisions

    start: first revision included the set (default to 0)
    end:   first revision excluded (last+1) (default to len(repo))

    Spanset will be descending if `end` < `start`.
    """
    if end is None:
        end = len(repo)
    ascending = start <= end
    if not ascending:
        start, end = end + 1, start + 1
    return _spanset(start, end, ascending, repo.changelog.filteredrevs)


class _spanset(abstractsmartset):
    """Duck type for baseset class which represents a range of revisions and
    can work lazily and without having all the range in memory

    Note that spanset(x, y) behave almost like xrange(x, y) except for two
    notable points:
    - when x < y it will be automatically descending,
    - revision filtered with this repoview will be skipped.

    """

    def __init__(self, start, end, ascending, hiddenrevs):
        self._start = start
        self._end = end
        self._ascending = ascending
        self._hiddenrevs = hiddenrevs

    def sort(self, reverse=False):
        self._ascending = not reverse

    def reverse(self):
        self._ascending = not self._ascending

    def istopo(self):
        # not worth the trouble asserting if the two sets combined are still
        # in topographical order. Use the sort() predicate to explicitly sort
        # again instead.
        return False

    def _iterfilter(self, iterrange):
        s = self._hiddenrevs
        for r in iterrange:
            if r not in s:
                yield r

    def __iter__(self):
        if self._ascending:
            return self.fastasc()
        else:
            return self.fastdesc()

    def fastasc(self):
        iterrange = pycompat.xrange(self._start, self._end)
        if self._hiddenrevs:
            return self._iterfilter(iterrange)
        return iter(iterrange)

    def fastdesc(self):
        iterrange = pycompat.xrange(self._end - 1, self._start - 1, -1)
        if self._hiddenrevs:
            return self._iterfilter(iterrange)
        return iter(iterrange)

    def __contains__(self, rev):
        hidden = self._hiddenrevs
        return (self._start <= rev < self._end) and not (
            hidden and rev in hidden
        )

    def __nonzero__(self):
        for r in self:
            return True
        return False

    __bool__ = __nonzero__

    def __len__(self):
        if not self._hiddenrevs:
            return abs(self._end - self._start)
        else:
            count = 0
            start = self._start
            end = self._end
            for rev in self._hiddenrevs:
                if (end < rev <= start) or (start <= rev < end):
                    count += 1
            return abs(self._end - self._start) - count

    def isascending(self):
        return self._ascending

    def isdescending(self):
        return not self._ascending

    def first(self):
        if self._ascending:
            it = self.fastasc
        else:
            it = self.fastdesc
        for x in it():
            return x
        return None

    def last(self):
        if self._ascending:
            it = self.fastdesc
        else:
            it = self.fastasc
        for x in it():
            return x
        return None

    def _slice(self, start, stop):
        if self._hiddenrevs:
            # unoptimized since all hidden revisions in range has to be scanned
            return super(_spanset, self)._slice(start, stop)
        if self._ascending:
            x = min(self._start + start, self._end)
            y = min(self._start + stop, self._end)
        else:
            x = max(self._end - stop, self._start)
            y = max(self._end - start, self._start)
        return _spanset(x, y, self._ascending, self._hiddenrevs)

    @encoding.strmethod
    def __repr__(self):
        d = {False: b'-', True: b'+'}[self._ascending]
        return b'<%s%s %d:%d>' % (_typename(self), d, self._start, self._end)


class fullreposet(_spanset):
    """a set containing all revisions in the repo

    This class exists to host special optimization and magic to handle virtual
    revisions such as "null".
    """

    def __init__(self, repo):
        super(fullreposet, self).__init__(
            0, len(repo), True, repo.changelog.filteredrevs
        )

    def __and__(self, other):
        """As self contains the whole repo, all of the other set should also be
        in self. Therefore `self & other = other`.

        This boldly assumes the other contains valid revs only.
        """
        # other not a smartset, make is so
        if not util.safehasattr(other, b'isascending'):
            # filter out hidden revision
            # (this boldly assumes all smartset are pure)
            #
            # `other` was used with "&", let's assume this is a set like
            # object.
            other = baseset(other - self._hiddenrevs)

        other.sort(reverse=self.isdescending())
        return other
