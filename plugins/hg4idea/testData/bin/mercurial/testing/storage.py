# storage.py - Testing of storage primitives.
#
# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import unittest

from ..node import (
    hex,
    nullrev,
)
from .. import (
    error,
    mdiff,
)
from ..interfaces import repository
from ..utils import storageutil


class basetestcase(unittest.TestCase):
    if not getattr(unittest.TestCase, 'assertRaisesRegex', False):
        assertRaisesRegex = (  # camelcase-required
            unittest.TestCase.assertRaisesRegexp
        )


class ifileindextests(basetestcase):
    """Generic tests for the ifileindex interface.

    All file storage backends for index data should conform to the tests in this
    class.

    Use ``makeifileindextests()`` to create an instance of this type.
    """

    def testempty(self):
        f = self._makefilefn()
        self.assertEqual(len(f), 0, b'new file store has 0 length by default')
        self.assertEqual(list(f), [], b'iter yields nothing by default')

        gen = iter(f)
        with self.assertRaises(StopIteration):
            next(gen)

        self.assertFalse(f.hasnode(None))
        self.assertFalse(f.hasnode(0))
        self.assertFalse(f.hasnode(nullrev))
        self.assertFalse(f.hasnode(f.nullid))
        self.assertFalse(f.hasnode(b'0'))
        self.assertFalse(f.hasnode(b'a' * 20))

        # revs() should evaluate to an empty list.
        self.assertEqual(list(f.revs()), [])

        revs = iter(f.revs())
        with self.assertRaises(StopIteration):
            next(revs)

        self.assertEqual(list(f.revs(start=20)), [])

        # parents() and parentrevs() work with f.nullid/nullrev.
        self.assertEqual(f.parents(f.nullid), (f.nullid, f.nullid))
        self.assertEqual(f.parentrevs(nullrev), (nullrev, nullrev))

        with self.assertRaises(error.LookupError):
            f.parents(b'\x01' * 20)

        for i in range(-5, 5):
            if i == nullrev:
                continue

            with self.assertRaises(IndexError):
                f.parentrevs(i)

        # f.nullid/nullrev lookup always works.
        self.assertEqual(f.rev(f.nullid), nullrev)
        self.assertEqual(f.node(nullrev), f.nullid)

        with self.assertRaises(error.LookupError):
            f.rev(b'\x01' * 20)

        for i in range(-5, 5):
            if i == nullrev:
                continue

            with self.assertRaises(IndexError):
                f.node(i)

        self.assertEqual(f.lookup(f.nullid), f.nullid)
        self.assertEqual(f.lookup(nullrev), f.nullid)
        self.assertEqual(f.lookup(hex(f.nullid)), f.nullid)
        self.assertEqual(f.lookup(b'%d' % nullrev), f.nullid)

        with self.assertRaises(error.LookupError):
            f.lookup(b'badvalue')

        with self.assertRaises(error.LookupError):
            f.lookup(hex(f.nullid)[0:12])

        with self.assertRaises(error.LookupError):
            f.lookup(b'-2')

        with self.assertRaises(error.LookupError):
            f.lookup(b'0')

        with self.assertRaises(error.LookupError):
            f.lookup(b'1')

        with self.assertRaises(error.LookupError):
            f.lookup(b'11111111111111111111111111111111111111')

        for i in range(-5, 5):
            if i == nullrev:
                continue

            with self.assertRaises(LookupError):
                f.lookup(i)

        self.assertEqual(f.linkrev(nullrev), nullrev)

        for i in range(-5, 5):
            if i == nullrev:
                continue

            with self.assertRaises(IndexError):
                f.linkrev(i)

        self.assertFalse(f.iscensored(nullrev))

        for i in range(-5, 5):
            if i == nullrev:
                continue

            with self.assertRaises(IndexError):
                f.iscensored(i)

        self.assertEqual(list(f.commonancestorsheads(f.nullid, f.nullid)), [])

        with self.assertRaises(ValueError):
            self.assertEqual(list(f.descendants([])), [])

        self.assertEqual(list(f.descendants([nullrev])), [])

        self.assertEqual(f.heads(), [f.nullid])
        self.assertEqual(f.heads(f.nullid), [f.nullid])
        self.assertEqual(f.heads(None, [f.nullid]), [f.nullid])
        self.assertEqual(f.heads(f.nullid, [f.nullid]), [f.nullid])

        self.assertEqual(f.children(f.nullid), [])

        with self.assertRaises(error.LookupError):
            f.children(b'\x01' * 20)

    def testsinglerevision(self):
        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            node = f.add(b'initial', None, tr, 0, f.nullid, f.nullid)

        self.assertEqual(len(f), 1)
        self.assertEqual(list(f), [0])

        gen = iter(f)
        self.assertEqual(next(gen), 0)

        with self.assertRaises(StopIteration):
            next(gen)

        self.assertTrue(f.hasnode(node))
        self.assertFalse(f.hasnode(hex(node)))
        self.assertFalse(f.hasnode(nullrev))
        self.assertFalse(f.hasnode(f.nullid))
        self.assertFalse(f.hasnode(node[0:12]))
        self.assertFalse(f.hasnode(hex(node)[0:20]))

        self.assertEqual(list(f.revs()), [0])
        self.assertEqual(list(f.revs(start=1)), [])
        self.assertEqual(list(f.revs(start=0)), [0])
        self.assertEqual(list(f.revs(stop=0)), [0])
        self.assertEqual(list(f.revs(stop=1)), [0])
        self.assertEqual(list(f.revs(1, 1)), [])
        # TODO buggy
        self.assertEqual(list(f.revs(1, 0)), [1, 0])
        self.assertEqual(list(f.revs(2, 0)), [2, 1, 0])

        self.assertEqual(f.parents(node), (f.nullid, f.nullid))
        self.assertEqual(f.parentrevs(0), (nullrev, nullrev))

        with self.assertRaises(error.LookupError):
            f.parents(b'\x01' * 20)

        with self.assertRaises(IndexError):
            f.parentrevs(1)

        self.assertEqual(f.rev(node), 0)

        with self.assertRaises(error.LookupError):
            f.rev(b'\x01' * 20)

        self.assertEqual(f.node(0), node)

        with self.assertRaises(IndexError):
            f.node(1)

        self.assertEqual(f.lookup(node), node)
        self.assertEqual(f.lookup(0), node)
        self.assertEqual(f.lookup(-1), f.nullid)
        self.assertEqual(f.lookup(b'0'), node)
        self.assertEqual(f.lookup(hex(node)), node)

        with self.assertRaises(error.LookupError):
            f.lookup(hex(node)[0:12])

        with self.assertRaises(error.LookupError):
            f.lookup(-2)

        with self.assertRaises(error.LookupError):
            f.lookup(b'-2')

        with self.assertRaises(error.LookupError):
            f.lookup(1)

        with self.assertRaises(error.LookupError):
            f.lookup(b'1')

        self.assertEqual(f.linkrev(0), 0)

        with self.assertRaises(IndexError):
            f.linkrev(1)

        self.assertFalse(f.iscensored(0))

        with self.assertRaises(IndexError):
            f.iscensored(1)

        self.assertEqual(list(f.descendants([0])), [])

        self.assertEqual(f.heads(), [node])
        self.assertEqual(f.heads(node), [node])
        self.assertEqual(f.heads(stop=[node]), [node])

        with self.assertRaises(error.LookupError):
            f.heads(stop=[b'\x01' * 20])

        self.assertEqual(f.children(node), [])

    def testmultiplerevisions(self):
        fulltext0 = b'x' * 1024
        fulltext1 = fulltext0 + b'y'
        fulltext2 = b'y' + fulltext0 + b'z'

        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, None, tr, 0, f.nullid, f.nullid)
            node1 = f.add(fulltext1, None, tr, 1, node0, f.nullid)
            node2 = f.add(fulltext2, None, tr, 3, node1, f.nullid)

        self.assertEqual(len(f), 3)
        self.assertEqual(list(f), [0, 1, 2])

        gen = iter(f)
        self.assertEqual(next(gen), 0)
        self.assertEqual(next(gen), 1)
        self.assertEqual(next(gen), 2)

        with self.assertRaises(StopIteration):
            next(gen)

        self.assertEqual(list(f.revs()), [0, 1, 2])
        self.assertEqual(list(f.revs(0)), [0, 1, 2])
        self.assertEqual(list(f.revs(1)), [1, 2])
        self.assertEqual(list(f.revs(2)), [2])
        self.assertEqual(list(f.revs(3)), [])
        self.assertEqual(list(f.revs(stop=1)), [0, 1])
        self.assertEqual(list(f.revs(stop=2)), [0, 1, 2])
        self.assertEqual(list(f.revs(stop=3)), [0, 1, 2])
        self.assertEqual(list(f.revs(2, 0)), [2, 1, 0])
        self.assertEqual(list(f.revs(2, 1)), [2, 1])
        # TODO this is wrong
        self.assertEqual(list(f.revs(3, 2)), [3, 2])

        self.assertEqual(f.parents(node0), (f.nullid, f.nullid))
        self.assertEqual(f.parents(node1), (node0, f.nullid))
        self.assertEqual(f.parents(node2), (node1, f.nullid))

        self.assertEqual(f.parentrevs(0), (nullrev, nullrev))
        self.assertEqual(f.parentrevs(1), (0, nullrev))
        self.assertEqual(f.parentrevs(2), (1, nullrev))

        self.assertEqual(f.rev(node0), 0)
        self.assertEqual(f.rev(node1), 1)
        self.assertEqual(f.rev(node2), 2)

        with self.assertRaises(error.LookupError):
            f.rev(b'\x01' * 20)

        self.assertEqual(f.node(0), node0)
        self.assertEqual(f.node(1), node1)
        self.assertEqual(f.node(2), node2)

        with self.assertRaises(IndexError):
            f.node(3)

        self.assertEqual(f.lookup(node0), node0)
        self.assertEqual(f.lookup(0), node0)
        self.assertEqual(f.lookup(b'0'), node0)
        self.assertEqual(f.lookup(hex(node0)), node0)

        self.assertEqual(f.lookup(node1), node1)
        self.assertEqual(f.lookup(1), node1)
        self.assertEqual(f.lookup(b'1'), node1)
        self.assertEqual(f.lookup(hex(node1)), node1)

        self.assertEqual(f.linkrev(0), 0)
        self.assertEqual(f.linkrev(1), 1)
        self.assertEqual(f.linkrev(2), 3)

        with self.assertRaises(IndexError):
            f.linkrev(3)

        self.assertFalse(f.iscensored(0))
        self.assertFalse(f.iscensored(1))
        self.assertFalse(f.iscensored(2))

        with self.assertRaises(IndexError):
            f.iscensored(3)

        self.assertEqual(f.commonancestorsheads(node1, f.nullid), [])
        self.assertEqual(f.commonancestorsheads(node1, node0), [node0])
        self.assertEqual(f.commonancestorsheads(node1, node1), [node1])
        self.assertEqual(f.commonancestorsheads(node0, node1), [node0])
        self.assertEqual(f.commonancestorsheads(node1, node2), [node1])
        self.assertEqual(f.commonancestorsheads(node2, node1), [node1])

        self.assertEqual(list(f.descendants([0])), [1, 2])
        self.assertEqual(list(f.descendants([1])), [2])
        self.assertEqual(list(f.descendants([0, 1])), [1, 2])

        self.assertEqual(f.heads(), [node2])
        self.assertEqual(f.heads(node0), [node2])
        self.assertEqual(f.heads(node1), [node2])
        self.assertEqual(f.heads(node2), [node2])

        # TODO this behavior seems wonky. Is it correct? If so, the
        # docstring for heads() should be updated to reflect desired
        # behavior.
        self.assertEqual(f.heads(stop=[node1]), [node1, node2])
        self.assertEqual(f.heads(stop=[node0]), [node0, node2])
        self.assertEqual(f.heads(stop=[node1, node2]), [node1, node2])

        with self.assertRaises(error.LookupError):
            f.heads(stop=[b'\x01' * 20])

        self.assertEqual(f.children(node0), [node1])
        self.assertEqual(f.children(node1), [node2])
        self.assertEqual(f.children(node2), [])

    def testmultipleheads(self):
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            node0 = f.add(b'0', None, tr, 0, f.nullid, f.nullid)
            node1 = f.add(b'1', None, tr, 1, node0, f.nullid)
            node2 = f.add(b'2', None, tr, 2, node1, f.nullid)
            node3 = f.add(b'3', None, tr, 3, node0, f.nullid)
            node4 = f.add(b'4', None, tr, 4, node3, f.nullid)
            node5 = f.add(b'5', None, tr, 5, node0, f.nullid)

        self.assertEqual(len(f), 6)

        self.assertEqual(list(f.descendants([0])), [1, 2, 3, 4, 5])
        self.assertEqual(list(f.descendants([1])), [2])
        self.assertEqual(list(f.descendants([2])), [])
        self.assertEqual(list(f.descendants([3])), [4])
        self.assertEqual(list(f.descendants([0, 1])), [1, 2, 3, 4, 5])
        self.assertEqual(list(f.descendants([1, 3])), [2, 4])

        self.assertEqual(f.heads(), [node2, node4, node5])
        self.assertEqual(f.heads(node0), [node2, node4, node5])
        self.assertEqual(f.heads(node1), [node2])
        self.assertEqual(f.heads(node2), [node2])
        self.assertEqual(f.heads(node3), [node4])
        self.assertEqual(f.heads(node4), [node4])
        self.assertEqual(f.heads(node5), [node5])

        # TODO this seems wrong.
        self.assertEqual(f.heads(stop=[node0]), [node0, node2, node4, node5])
        self.assertEqual(f.heads(stop=[node1]), [node1, node2, node4, node5])

        self.assertEqual(f.children(node0), [node1, node3, node5])
        self.assertEqual(f.children(node1), [node2])
        self.assertEqual(f.children(node2), [])
        self.assertEqual(f.children(node3), [node4])
        self.assertEqual(f.children(node4), [])
        self.assertEqual(f.children(node5), [])


class ifiledatatests(basetestcase):
    """Generic tests for the ifiledata interface.

    All file storage backends for data should conform to the tests in this
    class.

    Use ``makeifiledatatests()`` to create an instance of this type.
    """

    def testempty(self):
        f = self._makefilefn()

        self.assertEqual(f.storageinfo(), {})
        self.assertEqual(
            f.storageinfo(revisionscount=True, trackedsize=True),
            {b'revisionscount': 0, b'trackedsize': 0},
        )

        self.assertEqual(f.size(nullrev), 0)

        for i in range(-5, 5):
            if i == nullrev:
                continue

            with self.assertRaises(IndexError):
                f.size(i)

        self.assertEqual(f.revision(f.nullid), b'')
        self.assertEqual(f.rawdata(f.nullid), b'')

        with self.assertRaises(error.LookupError):
            f.revision(b'\x01' * 20)

        self.assertEqual(f.read(f.nullid), b'')

        with self.assertRaises(error.LookupError):
            f.read(b'\x01' * 20)

        self.assertFalse(f.renamed(f.nullid))

        with self.assertRaises(error.LookupError):
            f.read(b'\x01' * 20)

        self.assertTrue(f.cmp(f.nullid, b''))
        self.assertTrue(f.cmp(f.nullid, b'foo'))

        with self.assertRaises(error.LookupError):
            f.cmp(b'\x01' * 20, b'irrelevant')

        # Emitting empty list is an empty generator.
        gen = f.emitrevisions([])
        with self.assertRaises(StopIteration):
            next(gen)

        # Emitting null node yields nothing.
        gen = f.emitrevisions([f.nullid])
        with self.assertRaises(StopIteration):
            next(gen)

        # Requesting unknown node fails.
        with self.assertRaises(error.LookupError):
            list(f.emitrevisions([b'\x01' * 20]))

    def testsinglerevision(self):
        fulltext = b'initial'

        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            node = f.add(fulltext, None, tr, 0, f.nullid, f.nullid)

        self.assertEqual(f.storageinfo(), {})
        self.assertEqual(
            f.storageinfo(revisionscount=True, trackedsize=True),
            {b'revisionscount': 1, b'trackedsize': len(fulltext)},
        )

        self.assertEqual(f.size(0), len(fulltext))

        with self.assertRaises(IndexError):
            f.size(1)

        self.assertEqual(f.revision(node), fulltext)
        self.assertEqual(f.rawdata(node), fulltext)

        self.assertEqual(f.read(node), fulltext)

        self.assertFalse(f.renamed(node))

        self.assertFalse(f.cmp(node, fulltext))
        self.assertTrue(f.cmp(node, fulltext + b'extra'))

        # Emitting a single revision works.
        gen = f.emitrevisions([node])
        rev = next(gen)

        self.assertEqual(rev.node, node)
        self.assertEqual(rev.p1node, f.nullid)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertIsNone(rev.linknode)
        self.assertEqual(rev.basenode, f.nullid)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertIsNone(rev.delta)

        with self.assertRaises(StopIteration):
            next(gen)

        # Requesting revision data works.
        gen = f.emitrevisions([node], revisiondata=True)
        rev = next(gen)

        self.assertEqual(rev.node, node)
        self.assertEqual(rev.p1node, f.nullid)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertIsNone(rev.linknode)
        self.assertEqual(rev.basenode, f.nullid)
        self.assertIsNone(rev.baserevisionsize)
        self.assertEqual(rev.revision, fulltext)
        self.assertIsNone(rev.delta)

        with self.assertRaises(StopIteration):
            next(gen)

        # Emitting an unknown node after a known revision results in error.
        with self.assertRaises(error.LookupError):
            list(f.emitrevisions([node, b'\x01' * 20]))

    def testmultiplerevisions(self):
        fulltext0 = b'x' * 1024
        fulltext1 = fulltext0 + b'y'
        fulltext2 = b'y' + fulltext0 + b'z'

        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, None, tr, 0, f.nullid, f.nullid)
            node1 = f.add(fulltext1, None, tr, 1, node0, f.nullid)
            node2 = f.add(fulltext2, None, tr, 3, node1, f.nullid)

        self.assertEqual(f.storageinfo(), {})
        self.assertEqual(
            f.storageinfo(revisionscount=True, trackedsize=True),
            {
                b'revisionscount': 3,
                b'trackedsize': len(fulltext0)
                + len(fulltext1)
                + len(fulltext2),
            },
        )

        self.assertEqual(f.size(0), len(fulltext0))
        self.assertEqual(f.size(1), len(fulltext1))
        self.assertEqual(f.size(2), len(fulltext2))

        with self.assertRaises(IndexError):
            f.size(3)

        self.assertEqual(f.revision(node0), fulltext0)
        self.assertEqual(f.rawdata(node0), fulltext0)
        self.assertEqual(f.revision(node1), fulltext1)
        self.assertEqual(f.rawdata(node1), fulltext1)
        self.assertEqual(f.revision(node2), fulltext2)
        self.assertEqual(f.rawdata(node2), fulltext2)

        with self.assertRaises(error.LookupError):
            f.revision(b'\x01' * 20)

        self.assertEqual(f.read(node0), fulltext0)
        self.assertEqual(f.read(node1), fulltext1)
        self.assertEqual(f.read(node2), fulltext2)

        with self.assertRaises(error.LookupError):
            f.read(b'\x01' * 20)

        self.assertFalse(f.renamed(node0))
        self.assertFalse(f.renamed(node1))
        self.assertFalse(f.renamed(node2))

        with self.assertRaises(error.LookupError):
            f.renamed(b'\x01' * 20)

        self.assertFalse(f.cmp(node0, fulltext0))
        self.assertFalse(f.cmp(node1, fulltext1))
        self.assertFalse(f.cmp(node2, fulltext2))

        self.assertTrue(f.cmp(node1, fulltext0))
        self.assertTrue(f.cmp(node2, fulltext1))

        with self.assertRaises(error.LookupError):
            f.cmp(b'\x01' * 20, b'irrelevant')

        # Nodes should be emitted in order.
        gen = f.emitrevisions([node0, node1, node2], revisiondata=True)

        rev = next(gen)

        self.assertEqual(rev.node, node0)
        self.assertEqual(rev.p1node, f.nullid)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertIsNone(rev.linknode)
        self.assertEqual(rev.basenode, f.nullid)
        self.assertIsNone(rev.baserevisionsize)
        self.assertEqual(rev.revision, fulltext0)
        self.assertIsNone(rev.delta)

        rev = next(gen)

        self.assertEqual(rev.node, node1)
        self.assertEqual(rev.p1node, node0)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertIsNone(rev.linknode)
        self.assertEqual(rev.basenode, node0)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertEqual(
            rev.delta,
            b'\x00\x00\x00\x00\x00\x00\x04\x00\x00\x00\x04\x01' + fulltext1,
        )

        rev = next(gen)

        self.assertEqual(rev.node, node2)
        self.assertEqual(rev.p1node, node1)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertIsNone(rev.linknode)
        self.assertEqual(rev.basenode, node1)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertEqual(
            rev.delta,
            b'\x00\x00\x00\x00\x00\x00\x04\x01\x00\x00\x04\x02' + fulltext2,
        )

        with self.assertRaises(StopIteration):
            next(gen)

        # Request not in DAG order is reordered to be in DAG order.
        gen = f.emitrevisions([node2, node1, node0], revisiondata=True)

        rev = next(gen)

        self.assertEqual(rev.node, node0)
        self.assertEqual(rev.p1node, f.nullid)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertIsNone(rev.linknode)
        self.assertEqual(rev.basenode, f.nullid)
        self.assertIsNone(rev.baserevisionsize)
        self.assertEqual(rev.revision, fulltext0)
        self.assertIsNone(rev.delta)

        rev = next(gen)

        self.assertEqual(rev.node, node1)
        self.assertEqual(rev.p1node, node0)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertIsNone(rev.linknode)
        self.assertEqual(rev.basenode, node0)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertEqual(
            rev.delta,
            b'\x00\x00\x00\x00\x00\x00\x04\x00\x00\x00\x04\x01' + fulltext1,
        )

        rev = next(gen)

        self.assertEqual(rev.node, node2)
        self.assertEqual(rev.p1node, node1)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertIsNone(rev.linknode)
        self.assertEqual(rev.basenode, node1)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertEqual(
            rev.delta,
            b'\x00\x00\x00\x00\x00\x00\x04\x01\x00\x00\x04\x02' + fulltext2,
        )

        with self.assertRaises(StopIteration):
            next(gen)

        # Unrecognized nodesorder value raises ProgrammingError.
        with self.assertRaises(error.ProgrammingError):
            list(f.emitrevisions([], nodesorder=b'bad'))

        # nodesorder=storage is recognized. But we can't test it thoroughly
        # because behavior is storage-dependent.
        res = list(
            f.emitrevisions([node2, node1, node0], nodesorder=b'storage')
        )
        self.assertEqual(len(res), 3)
        self.assertEqual({o.node for o in res}, {node0, node1, node2})

        # nodesorder=nodes forces the order.
        gen = f.emitrevisions(
            [node2, node0], nodesorder=b'nodes', revisiondata=True
        )

        rev = next(gen)
        self.assertEqual(rev.node, node2)
        self.assertEqual(rev.p1node, node1)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertEqual(rev.basenode, f.nullid)
        self.assertIsNone(rev.baserevisionsize)
        self.assertEqual(rev.revision, fulltext2)
        self.assertIsNone(rev.delta)

        rev = next(gen)
        self.assertEqual(rev.node, node0)
        self.assertEqual(rev.p1node, f.nullid)
        self.assertEqual(rev.p2node, f.nullid)
        # Delta behavior is storage dependent, so we can't easily test it.

        with self.assertRaises(StopIteration):
            next(gen)

        # assumehaveparentrevisions=False (the default) won't send a delta for
        # the first revision.
        gen = f.emitrevisions({node2, node1}, revisiondata=True)

        rev = next(gen)
        self.assertEqual(rev.node, node1)
        self.assertEqual(rev.p1node, node0)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertEqual(rev.basenode, f.nullid)
        self.assertIsNone(rev.baserevisionsize)
        self.assertEqual(rev.revision, fulltext1)
        self.assertIsNone(rev.delta)

        rev = next(gen)
        self.assertEqual(rev.node, node2)
        self.assertEqual(rev.p1node, node1)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertEqual(rev.basenode, node1)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertEqual(
            rev.delta,
            b'\x00\x00\x00\x00\x00\x00\x04\x01\x00\x00\x04\x02' + fulltext2,
        )

        with self.assertRaises(StopIteration):
            next(gen)

        # assumehaveparentrevisions=True allows delta against initial revision.
        gen = f.emitrevisions(
            [node2, node1], revisiondata=True, assumehaveparentrevisions=True
        )

        rev = next(gen)
        self.assertEqual(rev.node, node1)
        self.assertEqual(rev.p1node, node0)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertEqual(rev.basenode, node0)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertEqual(
            rev.delta,
            b'\x00\x00\x00\x00\x00\x00\x04\x00\x00\x00\x04\x01' + fulltext1,
        )

        # forceprevious=True forces a delta against the previous revision.
        # Special case for initial revision.
        gen = f.emitrevisions(
            [node0], revisiondata=True, deltamode=repository.CG_DELTAMODE_PREV
        )

        rev = next(gen)
        self.assertEqual(rev.node, node0)
        self.assertEqual(rev.p1node, f.nullid)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertEqual(rev.basenode, f.nullid)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertEqual(
            rev.delta,
            b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x04\x00' + fulltext0,
        )

        with self.assertRaises(StopIteration):
            next(gen)

        gen = f.emitrevisions(
            [node0, node2],
            revisiondata=True,
            deltamode=repository.CG_DELTAMODE_PREV,
        )

        rev = next(gen)
        self.assertEqual(rev.node, node0)
        self.assertEqual(rev.p1node, f.nullid)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertEqual(rev.basenode, f.nullid)
        self.assertIsNone(rev.baserevisionsize)
        self.assertIsNone(rev.revision)
        self.assertEqual(
            rev.delta,
            b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x04\x00' + fulltext0,
        )

        rev = next(gen)
        self.assertEqual(rev.node, node2)
        self.assertEqual(rev.p1node, node1)
        self.assertEqual(rev.p2node, f.nullid)
        self.assertEqual(rev.basenode, node0)

        with self.assertRaises(StopIteration):
            next(gen)

    def testrenamed(self):
        fulltext0 = b'foo'
        fulltext1 = b'bar'
        fulltext2 = b'baz'

        meta1 = {
            b'copy': b'source0',
            b'copyrev': b'a' * 40,
        }

        meta2 = {
            b'copy': b'source1',
            b'copyrev': b'b' * 40,
        }

        stored1 = b''.join(
            [
                b'\x01\ncopy: source0\n',
                b'copyrev: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n\x01\n',
                fulltext1,
            ]
        )

        stored2 = b''.join(
            [
                b'\x01\ncopy: source1\n',
                b'copyrev: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n\x01\n',
                fulltext2,
            ]
        )

        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, None, tr, 0, f.nullid, f.nullid)
            node1 = f.add(fulltext1, meta1, tr, 1, node0, f.nullid)
            node2 = f.add(fulltext2, meta2, tr, 2, f.nullid, f.nullid)

        # Metadata header isn't recognized when parent isn't f.nullid.
        self.assertEqual(f.size(1), len(stored1))
        self.assertEqual(f.size(2), len(fulltext2))

        self.assertEqual(f.revision(node1), stored1)
        self.assertEqual(f.rawdata(node1), stored1)
        self.assertEqual(f.revision(node2), stored2)
        self.assertEqual(f.rawdata(node2), stored2)

        self.assertEqual(f.read(node1), fulltext1)
        self.assertEqual(f.read(node2), fulltext2)

        # Returns False when first parent is set.
        self.assertFalse(f.renamed(node1))
        self.assertEqual(f.renamed(node2), (b'source1', b'\xbb' * 20))

        self.assertTrue(f.cmp(node1, fulltext1))
        self.assertTrue(f.cmp(node1, stored1))
        self.assertFalse(f.cmp(node2, fulltext2))
        self.assertTrue(f.cmp(node2, stored2))

    def testmetadataprefix(self):
        # Content with metadata prefix has extra prefix inserted in storage.
        fulltext0 = b'\x01\nfoo'
        stored0 = b'\x01\n\x01\n\x01\nfoo'

        fulltext1 = b'\x01\nbar'
        meta1 = {
            b'copy': b'source0',
            b'copyrev': b'b' * 40,
        }
        stored1 = b''.join(
            [
                b'\x01\ncopy: source0\n',
                b'copyrev: %s\n' % (b'b' * 40),
                b'\x01\n\x01\nbar',
            ]
        )

        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, {}, tr, 0, f.nullid, f.nullid)
            node1 = f.add(fulltext1, meta1, tr, 1, f.nullid, f.nullid)

        # TODO this is buggy.
        self.assertEqual(f.size(0), len(fulltext0) + 4)

        self.assertEqual(f.size(1), len(fulltext1))

        self.assertEqual(f.revision(node0), stored0)
        self.assertEqual(f.rawdata(node0), stored0)

        self.assertEqual(f.revision(node1), stored1)
        self.assertEqual(f.rawdata(node1), stored1)

        self.assertEqual(f.read(node0), fulltext0)
        self.assertEqual(f.read(node1), fulltext1)

        self.assertFalse(f.cmp(node0, fulltext0))
        self.assertTrue(f.cmp(node0, stored0))

        self.assertFalse(f.cmp(node1, fulltext1))
        self.assertTrue(f.cmp(node1, stored0))

    def testbadnoderead(self):
        f = self._makefilefn()

        fulltext0 = b'foo\n' * 30
        fulltext1 = fulltext0 + b'bar\n'

        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, None, tr, 0, f.nullid, f.nullid)
            node1 = b'\xaa' * 20

            self._addrawrevisionfn(
                f, tr, node1, node0, f.nullid, 1, rawtext=fulltext1
            )

        self.assertEqual(len(f), 2)
        self.assertEqual(f.parents(node1), (node0, f.nullid))

        # revision() raises since it performs hash verification.
        with self.assertRaises(error.StorageError):
            f.revision(node1)

        # rawdata() still verifies because there are no special storage
        # settings.
        with self.assertRaises(error.StorageError):
            f.rawdata(node1)

        # read() behaves like revision().
        with self.assertRaises(error.StorageError):
            f.read(node1)

        # We can't test renamed() here because some backends may not require
        # reading/validating the fulltext to return rename metadata.

    def testbadnoderevisionraw(self):
        # Like above except we test rawdata() first to isolate
        # revision caching behavior.
        f = self._makefilefn()

        fulltext0 = b'foo\n' * 30
        fulltext1 = fulltext0 + b'bar\n'

        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, None, tr, 0, f.nullid, f.nullid)
            node1 = b'\xaa' * 20

            self._addrawrevisionfn(
                f, tr, node1, node0, f.nullid, 1, rawtext=fulltext1
            )

        with self.assertRaises(error.StorageError):
            f.rawdata(node1)

        with self.assertRaises(error.StorageError):
            f.rawdata(node1)

    def testbadnoderevision(self):
        # Like above except we test read() first to isolate revision caching
        # behavior.
        f = self._makefilefn()

        fulltext0 = b'foo\n' * 30
        fulltext1 = fulltext0 + b'bar\n'

        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, None, tr, 0, f.nullid, f.nullid)
            node1 = b'\xaa' * 20

            self._addrawrevisionfn(
                f, tr, node1, node0, f.nullid, 1, rawtext=fulltext1
            )

        with self.assertRaises(error.StorageError):
            f.read(node1)

        with self.assertRaises(error.StorageError):
            f.read(node1)

    def testbadnodedelta(self):
        f = self._makefilefn()

        fulltext0 = b'foo\n' * 31
        fulltext1 = fulltext0 + b'bar\n'
        fulltext2 = fulltext1 + b'baz\n'

        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, None, tr, 0, f.nullid, f.nullid)
            node1 = b'\xaa' * 20

            self._addrawrevisionfn(
                f, tr, node1, node0, f.nullid, 1, rawtext=fulltext1
            )

        with self.assertRaises(error.StorageError):
            f.read(node1)

        node2 = storageutil.hashrevisionsha1(fulltext2, node1, f.nullid)

        with self._maketransactionfn() as tr:
            delta = mdiff.textdiff(fulltext1, fulltext2)
            self._addrawrevisionfn(
                f, tr, node2, node1, f.nullid, 2, delta=(1, delta)
            )

        self.assertEqual(len(f), 3)

        # Assuming a delta is stored, we shouldn't need to validate node1 in
        # order to retrieve node2.
        self.assertEqual(f.read(node2), fulltext2)

    def testcensored(self):
        f = self._makefilefn()

        stored1 = storageutil.packmeta(
            {
                b'censored': b'tombstone',
            },
            b'',
        )

        with self._maketransactionfn() as tr:
            node0 = f.add(b'foo', None, tr, 0, f.nullid, f.nullid)

            # The node value doesn't matter since we can't verify it.
            node1 = b'\xbb' * 20

            self._addrawrevisionfn(
                f, tr, node1, node0, f.nullid, 1, stored1, censored=True
            )

        self.assertTrue(f.iscensored(1))

        with self.assertRaises(error.CensoredNodeError):
            f.revision(1)

        with self.assertRaises(error.CensoredNodeError):
            f.rawdata(1)

        with self.assertRaises(error.CensoredNodeError):
            f.read(1)

    def testcensoredrawrevision(self):
        # Like above, except we do the rawdata() request first to
        # isolate revision caching behavior.

        f = self._makefilefn()

        stored1 = storageutil.packmeta(
            {
                b'censored': b'tombstone',
            },
            b'',
        )

        with self._maketransactionfn() as tr:
            node0 = f.add(b'foo', None, tr, 0, f.nullid, f.nullid)

            # The node value doesn't matter since we can't verify it.
            node1 = b'\xbb' * 20

            self._addrawrevisionfn(
                f, tr, node1, node0, f.nullid, 1, stored1, censored=True
            )

        with self.assertRaises(error.CensoredNodeError):
            f.rawdata(1)


class ifilemutationtests(basetestcase):
    """Generic tests for the ifilemutation interface.

    All file storage backends that support writing should conform to this
    interface.

    Use ``makeifilemutationtests()`` to create an instance of this type.
    """

    def testaddnoop(self):
        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            node0 = f.add(b'foo', None, tr, 0, f.nullid, f.nullid)
            node1 = f.add(b'foo', None, tr, 0, f.nullid, f.nullid)
            # Varying by linkrev shouldn't impact hash.
            node2 = f.add(b'foo', None, tr, 1, f.nullid, f.nullid)

        self.assertEqual(node1, node0)
        self.assertEqual(node2, node0)
        self.assertEqual(len(f), 1)

    def testaddrevisionbadnode(self):
        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            # Adding a revision with bad node value fails.
            with self.assertRaises(error.StorageError):
                f.addrevision(
                    b'foo', tr, 0, f.nullid, f.nullid, node=b'\x01' * 20
                )

    def testaddrevisionunknownflag(self):
        f = self._makefilefn()
        with self._maketransactionfn() as tr:
            for i in range(15, 0, -1):
                if (1 << i) & ~repository.REVISION_FLAGS_KNOWN:
                    flags = 1 << i
                    break

            with self.assertRaises(error.StorageError):
                f.addrevision(b'foo', tr, 0, f.nullid, f.nullid, flags=flags)

    def testaddgroupsimple(self):
        f = self._makefilefn()

        callbackargs = []

        def cb(*args, **kwargs):
            callbackargs.append((args, kwargs))

        def linkmapper(node):
            return 0

        with self._maketransactionfn() as tr:
            nodes = []

            def onchangeset(cl, rev):
                node = cl.node(rev)
                nodes.append(node)
                cb(cl, node)

            def ondupchangeset(cl, rev):
                nodes.append(cl.node(rev))

            f.addgroup(
                [],
                None,
                tr,
                addrevisioncb=onchangeset,
                duplicaterevisioncb=ondupchangeset,
            )

        self.assertEqual(nodes, [])
        self.assertEqual(callbackargs, [])
        self.assertEqual(len(f), 0)

        fulltext0 = b'foo'
        delta0 = mdiff.trivialdiffheader(len(fulltext0)) + fulltext0

        with self._maketransactionfn() as tr:
            node0 = f.add(fulltext0, None, tr, 0, f.nullid, f.nullid)

        f = self._makefilefn()

        deltas = [
            (node0, f.nullid, f.nullid, f.nullid, f.nullid, delta0, 0, {}),
        ]

        with self._maketransactionfn() as tr:
            nodes = []

            def onchangeset(cl, rev):
                node = cl.node(rev)
                nodes.append(node)
                cb(cl, node)

            def ondupchangeset(cl, rev):
                nodes.append(cl.node(rev))

            f.addgroup(
                deltas,
                linkmapper,
                tr,
                addrevisioncb=onchangeset,
                duplicaterevisioncb=ondupchangeset,
            )

        self.assertEqual(
            nodes,
            [
                b'\x49\xd8\xcb\xb1\x5c\xe2\x57\x92\x04\x47'
                b'\x00\x6b\x46\x97\x8b\x7a\xf9\x80\xa9\x79'
            ],
        )

        self.assertEqual(len(callbackargs), 1)
        self.assertEqual(callbackargs[0][0][1], nodes[0])

        self.assertEqual(list(f.revs()), [0])
        self.assertEqual(f.rev(nodes[0]), 0)
        self.assertEqual(f.node(0), nodes[0])

    def testaddgroupmultiple(self):
        f = self._makefilefn()

        fulltexts = [
            b'foo',
            b'bar',
            b'x' * 1024,
        ]

        nodes = []
        with self._maketransactionfn() as tr:
            for fulltext in fulltexts:
                nodes.append(f.add(fulltext, None, tr, 0, f.nullid, f.nullid))

        f = self._makefilefn()
        deltas = []
        for i, fulltext in enumerate(fulltexts):
            delta = mdiff.trivialdiffheader(len(fulltext)) + fulltext

            deltas.append(
                (nodes[i], f.nullid, f.nullid, f.nullid, f.nullid, delta, 0, {})
            )

        with self._maketransactionfn() as tr:
            newnodes = []

            def onchangeset(cl, rev):
                newnodes.append(cl.node(rev))

            f.addgroup(
                deltas,
                lambda x: 0,
                tr,
                addrevisioncb=onchangeset,
                duplicaterevisioncb=onchangeset,
            )
            self.assertEqual(newnodes, nodes)

        self.assertEqual(len(f), len(deltas))
        self.assertEqual(list(f.revs()), [0, 1, 2])
        self.assertEqual(f.rev(nodes[0]), 0)
        self.assertEqual(f.rev(nodes[1]), 1)
        self.assertEqual(f.rev(nodes[2]), 2)
        self.assertEqual(f.node(0), nodes[0])
        self.assertEqual(f.node(1), nodes[1])
        self.assertEqual(f.node(2), nodes[2])

    def testdeltaagainstcensored(self):
        # Attempt to apply a delta made against a censored revision.
        f = self._makefilefn()

        stored1 = storageutil.packmeta(
            {
                b'censored': b'tombstone',
            },
            b'',
        )

        with self._maketransactionfn() as tr:
            node0 = f.add(b'foo\n' * 30, None, tr, 0, f.nullid, f.nullid)

            # The node value doesn't matter since we can't verify it.
            node1 = b'\xbb' * 20

            self._addrawrevisionfn(
                f, tr, node1, node0, f.nullid, 1, stored1, censored=True
            )

        delta = mdiff.textdiff(b'bar\n' * 30, (b'bar\n' * 30) + b'baz\n')
        deltas = [
            (b'\xcc' * 20, node1, f.nullid, b'\x01' * 20, node1, delta, 0, {})
        ]

        with self._maketransactionfn() as tr:
            with self.assertRaises(error.CensoredBaseError):
                f.addgroup(deltas, lambda x: 0, tr)

    def testcensorrevisionbasic(self):
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            node0 = f.add(b'foo\n' * 30, None, tr, 0, f.nullid, f.nullid)
            node1 = f.add(b'foo\n' * 31, None, tr, 1, node0, f.nullid)
            node2 = f.add(b'foo\n' * 32, None, tr, 2, node1, f.nullid)

        with self._maketransactionfn() as tr:
            f.censorrevision(tr, [node1])

        self.assertEqual(len(f), 3)
        self.assertEqual(list(f.revs()), [0, 1, 2])

        self.assertEqual(f.read(node0), b'foo\n' * 30)
        self.assertEqual(f.read(node2), b'foo\n' * 32)

        with self.assertRaises(error.CensoredNodeError):
            f.read(node1)

    def testgetstrippointnoparents(self):
        # N revisions where none have parents.
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            for rev in range(10):
                f.add(b'%d' % rev, None, tr, rev, f.nullid, f.nullid)

        for rev in range(10):
            self.assertEqual(f.getstrippoint(rev), (rev, set()))

    def testgetstrippointlinear(self):
        # N revisions in a linear chain.
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            p1 = f.nullid

            for rev in range(10):
                f.add(b'%d' % rev, None, tr, rev, p1, f.nullid)

        for rev in range(10):
            self.assertEqual(f.getstrippoint(rev), (rev, set()))

    def testgetstrippointmultipleheads(self):
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            node0 = f.add(b'0', None, tr, 0, f.nullid, f.nullid)
            node1 = f.add(b'1', None, tr, 1, node0, f.nullid)
            f.add(b'2', None, tr, 2, node1, f.nullid)
            f.add(b'3', None, tr, 3, node0, f.nullid)
            f.add(b'4', None, tr, 4, node0, f.nullid)

        for rev in range(5):
            self.assertEqual(f.getstrippoint(rev), (rev, set()))

    def testgetstrippointearlierlinkrevs(self):
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            node0 = f.add(b'0', None, tr, 0, f.nullid, f.nullid)
            f.add(b'1', None, tr, 10, node0, f.nullid)
            f.add(b'2', None, tr, 5, node0, f.nullid)

        self.assertEqual(f.getstrippoint(0), (0, set()))
        self.assertEqual(f.getstrippoint(1), (1, set()))
        self.assertEqual(f.getstrippoint(2), (1, set()))
        self.assertEqual(f.getstrippoint(3), (1, set()))
        self.assertEqual(f.getstrippoint(4), (1, set()))
        self.assertEqual(f.getstrippoint(5), (1, set()))
        self.assertEqual(f.getstrippoint(6), (1, {2}))
        self.assertEqual(f.getstrippoint(7), (1, {2}))
        self.assertEqual(f.getstrippoint(8), (1, {2}))
        self.assertEqual(f.getstrippoint(9), (1, {2}))
        self.assertEqual(f.getstrippoint(10), (1, {2}))
        self.assertEqual(f.getstrippoint(11), (3, set()))

    def teststripempty(self):
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            f.strip(0, tr)

        self.assertEqual(len(f), 0)

    def teststripall(self):
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            p1 = f.nullid
            for rev in range(10):
                p1 = f.add(b'%d' % rev, None, tr, rev, p1, f.nullid)

        self.assertEqual(len(f), 10)

        with self._maketransactionfn() as tr:
            f.strip(0, tr)

        self.assertEqual(len(f), 0)

    def teststrippartial(self):
        f = self._makefilefn()

        with self._maketransactionfn() as tr:
            f.add(b'0', None, tr, 0, f.nullid, f.nullid)
            node1 = f.add(b'1', None, tr, 5, f.nullid, f.nullid)
            node2 = f.add(b'2', None, tr, 10, f.nullid, f.nullid)

        self.assertEqual(len(f), 3)

        with self._maketransactionfn() as tr:
            f.strip(11, tr)

        self.assertEqual(len(f), 3)

        with self._maketransactionfn() as tr:
            f.strip(10, tr)

        self.assertEqual(len(f), 2)

        with self.assertRaises(error.LookupError):
            f.rev(node2)

        with self._maketransactionfn() as tr:
            f.strip(6, tr)

        self.assertEqual(len(f), 2)

        with self._maketransactionfn() as tr:
            f.strip(3, tr)

        self.assertEqual(len(f), 1)

        with self.assertRaises(error.LookupError):
            f.rev(node1)


def makeifileindextests(makefilefn, maketransactionfn, addrawrevisionfn):
    """Create a unittest.TestCase class suitable for testing file storage.

    ``makefilefn`` is a callable which receives the test case as an
    argument and returns an object implementing the ``ifilestorage`` interface.

    ``maketransactionfn`` is a callable which receives the test case as an
    argument and returns a transaction object.

    ``addrawrevisionfn`` is a callable which receives arguments describing a
    low-level revision to add. This callable allows the insertion of
    potentially bad data into the store in order to facilitate testing.

    Returns a type that is a ``unittest.TestCase`` that can be used for
    testing the object implementing the file storage interface. Simply
    assign the returned value to a module-level attribute and a test loader
    should find and run it automatically.
    """
    d = {
        '_makefilefn': makefilefn,
        '_maketransactionfn': maketransactionfn,
        '_addrawrevisionfn': addrawrevisionfn,
    }
    return type('ifileindextests', (ifileindextests,), d)


def makeifiledatatests(makefilefn, maketransactionfn, addrawrevisionfn):
    d = {
        '_makefilefn': makefilefn,
        '_maketransactionfn': maketransactionfn,
        '_addrawrevisionfn': addrawrevisionfn,
    }
    return type('ifiledatatests', (ifiledatatests,), d)


def makeifilemutationtests(makefilefn, maketransactionfn, addrawrevisionfn):
    d = {
        '_makefilefn': makefilefn,
        '_maketransactionfn': maketransactionfn,
        '_addrawrevisionfn': addrawrevisionfn,
    }
    return type('ifilemutationtests', (ifilemutationtests,), d)
