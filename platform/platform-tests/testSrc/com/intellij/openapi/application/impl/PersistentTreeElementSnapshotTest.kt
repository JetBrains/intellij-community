// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.CharTableImpl
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILazyParseableElementType
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
internal class PersistentTreeElementSnapshotTest {

  private val psiManager: PsiManager
    get() = PsiManager.getInstance(ProjectManager.getInstance().defaultProject)

  companion object {
    @BeforeAll
    @JvmStatic
    fun ensureVersionedTreeEnabled() {
      // dynamic registry is a performance hit, so we disable this test if the versioned syntax is not enabled
      Assumptions.assumeTrue(InternalPsiVersioning.isVersionedSyntaxTreeEnabled())
    }
  }

  @Test
  fun `freezePsiVersion preserves snapshot and removed element relations during concurrent edits`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val (root, firstLeaf, removableLeaf, replacedBranch) = runSyncVersionedWriteAction {
      val firstLeaf = leaf("a")
      val left = composite("left", firstLeaf, leaf("b"))
      val removableLeaf = leaf("c")
      val middle = composite("middle", removableLeaf)
      val replacedBranch = composite("right", leaf("d"), leaf("e"))
      TreeUnderTest(
        root = composite("root", left, middle, replacedBranch),
        firstLeaf = firstLeaf,
        removableLeaf = removableLeaf,
        replacedBranch = replacedBranch,
      )
    }

    val beforeSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("a", 0), leafSnapshot("b", 1)),
      branchSnapshot("middle", 2, leafSnapshot("c", 0)),
      branchSnapshot("right", 3, leafSnapshot("d", 0), leafSnapshot("e", 1)),
    )
    val afterSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("a", 0), leafSnapshot("x", 1), leafSnapshot("b", 2)),
      branchSnapshot("middle", 3),
      branchSnapshot("replacement", 3, leafSnapshot("y", 0), leafSnapshot("z", 1)),
    )
    val frozenRemovedRelations = ElementRelations(
      name = "c",
      text = "c",
      startOffsetInParent = 0,
      parent = "middle",
      previous = null,
      next = null,
    )
    val detachedRemovedRelations = ElementRelations(
      name = "c",
      text = "c",
      startOffsetInParent = 0,
      parent = "DUMMY_HOLDER",
      previous = null,
      next = null,
    )

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      val frozenVersion = assertNotNull(InternalPsiVersioning.getCurrentPsiVersionInsideFrozenPsi())
      assertEquals(beforeSnapshot, snapshot(root))

      async(Dispatchers.Default) {
        backgroundWriteAction {
          val left = assertNotNull(firstLeaf.treeParent)
          left.addChild(leaf("x"), firstLeaf.treeNext)

          val middle = assertNotNull(removableLeaf.treeParent)
          middle.removeChild(removableLeaf)

          val rootParent = assertNotNull(replacedBranch.treeParent)
          rootParent.replaceChild(replacedBranch, composite("replacement", leaf("y"), leaf("z")))
        }
      }.asCompletableFuture().get()

      assertEquals(frozenVersion, InternalPsiVersioning.getCurrentPsiVersionInsideFrozenPsi())
      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(frozenRemovedRelations, relations(removableLeaf))
    }

    assertEquals(afterSnapshot, liveSnapshot(root))
    assertEquals(detachedRemovedRelations, liveRelations(removableLeaf))
  }

  @Test
  fun `freezePsiVersion preserves child order and offsets during head and tail churn`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val (root, firstLeaf, middleLeaf, tailLeaf) = runSyncVersionedWriteAction {
      val firstLeaf = leaf("a")
      val middleLeaf = leaf("b")
      val row = composite("row", firstLeaf, middleLeaf, leaf("c"))
      val tailLeaf = leaf("d")
      TreeHeadTailTestData(
        root = composite("root", row, composite("tail", tailLeaf)),
        firstLeaf = firstLeaf,
        middleLeaf = middleLeaf,
        tailLeaf = tailLeaf,
      )
    }

    val beforeSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("row", 0, leafSnapshot("a", 0), leafSnapshot("b", 1), leafSnapshot("c", 2)),
      branchSnapshot("tail", 3, leafSnapshot("d", 0)),
    )
    val afterSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("row", 0, leafSnapshot("x", 0), leafSnapshot("b", 1), leafSnapshot("c", 2), leafSnapshot("y", 3)),
      branchSnapshot("tail", 4, leafSnapshot("z", 0)),
    )

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      assertEquals(beforeSnapshot, snapshot(root))

      async(Dispatchers.Default) {
        backgroundWriteAction {
          val row = assertNotNull(firstLeaf.treeParent)
          row.removeChild(firstLeaf)
          row.addChild(leaf("x"), middleLeaf)
          row.addChild(leaf("y"), null)

          val tail = assertNotNull(tailLeaf.treeParent)
          tail.replaceChild(tailLeaf, leaf("z"))
        }
      }.asCompletableFuture().get()

      assertEquals(beforeSnapshot, snapshot(root))
    }

    assertEquals(afterSnapshot, liveSnapshot(root))
  }

  @Test
  fun `freezePsiVersion observes only published tree shapes during concurrent rewrites`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val root = runSyncVersionedWriteAction {
      composite("root", *shapeAChildren())
    }
    val expectedShapeA = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("a", 0), leafSnapshot("b", 1)),
      branchSnapshot("middle", 2, leafSnapshot("c", 0)),
      branchSnapshot("right", 3, leafSnapshot("d", 0)),
    )
    val expectedShapeB = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("a", 0), leafSnapshot("x", 1)),
      branchSnapshot("middle", 2),
      branchSnapshot("replacement", 2, leafSnapshot("y", 0), leafSnapshot("z", 1)),
    )
    val firstRewriteFinished = CompletableDeferred<Unit>()
    val writer = launch(Dispatchers.Default) {
      backgroundWriteAction {
        replaceChildren(root, *shapeBChildren())
      }
      firstRewriteFinished.complete(Unit)
      repeat(12) { iteration ->
        backgroundWriteAction {
          if (iteration % 2 == 0) {
            replaceChildren(root, *shapeAChildren())
          }
          else {
            replaceChildren(root, *shapeBChildren())
          }
        }
      }
    }

    val observed = mutableSetOf<TreeSnapshot>()
    val initialFrozenSnapshot = PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      snapshot(root)
    }
    assertEquals(expectedShapeA, initialFrozenSnapshot)
    observed += initialFrozenSnapshot

    firstRewriteFinished.await()
    repeat(60) {
      val currentSnapshot = PsiVersioningService.freezePsiVersion {
        ThreadingAssertions.assertNoReadAccess()
        snapshot(root)
      }
      assertTrue(
        currentSnapshot == expectedShapeA || currentSnapshot == expectedShapeB,
        "Unexpected snapshot observed: $currentSnapshot",
      )
      observed += currentSnapshot
    }

    writer.join()

    assertTrue(observed.contains(expectedShapeA))
    assertTrue(observed.contains(expectedShapeB))
    assertEquals(expectedShapeB, liveSnapshot(root))
  }

  @Test
  fun `freezePsiVersion preserves subtree relations during same object reparenting`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val (root, left, right, movingBranch, rightTail) = runSyncVersionedWriteAction {
      val movingBranch = composite("moving", leaf("m"), leaf("n"))
      val left = composite("left", leaf("a"), movingBranch, leaf("b"))
      val rightTail = leaf("d")
      SameObjectReparentTree(
        root = composite("root", left, composite("middle", leaf("s")), composite("right", leaf("c"), rightTail)),
        left = left,
        right = assertNotNull(left.treeParent?.lastChildNode as? CompositePsiElement),
        movingBranch = movingBranch,
        rightTail = rightTail,
      )
    }

    val beforeSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("a", 0), branchSnapshot("moving", 1, leafSnapshot("m", 0), leafSnapshot("n", 1)), leafSnapshot("b", 3)),
      branchSnapshot("middle", 4, leafSnapshot("s", 0)),
      branchSnapshot("right", 5, leafSnapshot("c", 0), leafSnapshot("d", 1)),
    )
    val afterSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("a", 0), leafSnapshot("b", 1)),
      branchSnapshot("middle", 2, leafSnapshot("s", 0)),
      branchSnapshot("right", 3, leafSnapshot("c", 0), branchSnapshot("moving", 1, leafSnapshot("m", 0), leafSnapshot("n", 1)), leafSnapshot("d", 3)),
    )
    val frozenRelations = ElementRelations(
      name = "moving",
      text = "mn",
      startOffsetInParent = 1,
      parent = "left",
      previous = "a",
      next = "b",
    )
    val liveRelations = ElementRelations(
      name = "moving",
      text = "mn",
      startOffsetInParent = 1,
      parent = "right",
      previous = "c",
      next = "d",
    )

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      assertEquals(beforeSnapshot, snapshot(root))

      async(Dispatchers.Default) {
        backgroundWriteAction {
          left.removeChild(movingBranch)
          right.addChild(movingBranch, rightTail)
        }
      }.asCompletableFuture().get()

      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(frozenRelations, relations(movingBranch))
    }

    assertEquals(afterSnapshot, liveSnapshot(root))
    assertEquals(liveRelations, liveRelations(movingBranch))
  }

  @Test
  fun `freezePsiVersion preserves invalidated offsets across multiple ancestor levels`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val (root, headInner, firstHeadInnerLeaf, middle, tail, tailInner, tailLeaf) = runSyncVersionedWriteAction {
      val firstHeadInnerLeaf = leaf("b")
      val headInner = composite("headInner", firstHeadInnerLeaf, leaf("c"))
      val middle = composite("middle", leaf("d"), leaf("ee"))
      val tailLeaf = leaf("hhh")
      val tailInner = composite("tailInner", leaf("g"), tailLeaf)
      val tail = composite("tail", leaf("ff"), tailInner)
      OffsetInvalidationTree(
        root = composite("root", composite("head", leaf("aa"), headInner), middle, tail),
        headInner = headInner,
        firstHeadInnerLeaf = firstHeadInnerLeaf,
        middle = middle,
        tail = tail,
        tailInner = tailInner,
        tailLeaf = tailLeaf,
      )
    }

    val beforeSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("head", 0, leafSnapshot("aa", 0), branchSnapshot("headInner", 2, leafSnapshot("b", 0), leafSnapshot("c", 1))),
      branchSnapshot("middle", 4, leafSnapshot("d", 0), leafSnapshot("ee", 1)),
      branchSnapshot("tail", 7, leafSnapshot("ff", 0), branchSnapshot("tailInner", 2, leafSnapshot("g", 0), leafSnapshot("hhh", 1))),
    )
    val afterSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("head", 0, leafSnapshot("aa", 0), branchSnapshot("headInner", 2, leafSnapshot("long", 0), leafSnapshot("c", 4))),
      branchSnapshot("middle", 7, leafSnapshot("d", 0), leafSnapshot("ee", 1)),
      branchSnapshot("tail", 10, leafSnapshot("ff", 0), branchSnapshot("tailInner", 2, leafSnapshot("g", 0), leafSnapshot("hhh", 1))),
    )
    val beforeOffsets = OffsetObservation(
      middleStartOffsetInParent = 4,
      middleStartOffset = 4,
      middleEndOffset = 7,
      tailStartOffsetInParent = 7,
      tailStartOffset = 7,
      tailEndOffset = 13,
      tailInnerStartOffsetInParent = 2,
      tailInnerStartOffset = 9,
      tailLeafStartOffsetInParent = 1,
      tailLeafStartOffset = 10,
      tailLeafEndOffset = 13,
    )
    val afterOffsets = OffsetObservation(
      middleStartOffsetInParent = 7,
      middleStartOffset = 7,
      middleEndOffset = 10,
      tailStartOffsetInParent = 10,
      tailStartOffset = 10,
      tailEndOffset = 16,
      tailInnerStartOffsetInParent = 2,
      tailInnerStartOffset = 12,
      tailLeafStartOffsetInParent = 1,
      tailLeafStartOffset = 13,
      tailLeafEndOffset = 16,
    )

    runReadActionBlocking {
      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(beforeOffsets, collectOffsets(middle, tail, tailInner, tailLeaf))
    }

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(beforeOffsets, collectOffsets(middle, tail, tailInner, tailLeaf))

      async(Dispatchers.Default) {
        backgroundWriteAction {
          headInner.replaceChild(firstHeadInnerLeaf, leaf("long"))
        }
      }.asCompletableFuture().get()

      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(beforeOffsets, collectOffsets(middle, tail, tailInner, tailLeaf))
    }

    assertEquals(afterSnapshot, liveSnapshot(root))
    assertEquals(afterOffsets, runReadActionBlocking { collectOffsets(middle, tail, tailInner, tailLeaf) })
  }

  @Test
  fun `freezePsiVersion preserves original subtree identity across detach replace and reattach`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val (root, left, right, originalBranch, rightTail) = runSyncVersionedWriteAction {
      val originalBranch = composite("original", leaf("p"), leaf("q"))
      val left = composite("left", leaf("u"), originalBranch, leaf("v"))
      val rightTail = leaf("z")
      AbaReattachTree(
        root = composite("root", left, composite("bridge", leaf("w"), leaf("x")), composite("right", leaf("y"), rightTail)),
        left = left,
        right = assertNotNull(left.treeParent?.lastChildNode as? CompositePsiElement),
        originalBranch = originalBranch,
        rightTail = rightTail,
      )
    }

    val beforeSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("u", 0), branchSnapshot("original", 1, leafSnapshot("p", 0), leafSnapshot("q", 1)), leafSnapshot("v", 3)),
      branchSnapshot("bridge", 4, leafSnapshot("w", 0), leafSnapshot("x", 1)),
      branchSnapshot("right", 6, leafSnapshot("y", 0), leafSnapshot("z", 1)),
    )
    val afterSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("u", 0), branchSnapshot("replacement", 1, leafSnapshot("r", 0), leafSnapshot("s", 1), leafSnapshot("t", 2)), leafSnapshot("v", 4)),
      branchSnapshot("bridge", 5, leafSnapshot("w", 0), leafSnapshot("x", 1)),
      branchSnapshot("right", 7, leafSnapshot("y", 0), branchSnapshot("original", 1, leafSnapshot("p", 0), leafSnapshot("q", 1)), leafSnapshot("z", 3)),
    )
    val frozenRelations = ElementRelations(
      name = "original",
      text = "pq",
      startOffsetInParent = 1,
      parent = "left",
      previous = "u",
      next = "v",
    )
    val liveRelations = ElementRelations(
      name = "original",
      text = "pq",
      startOffsetInParent = 1,
      parent = "right",
      previous = "y",
      next = "z",
    )

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      assertEquals(beforeSnapshot, snapshot(root))

      async(Dispatchers.Default) {
        backgroundWriteAction {
          left.replaceChild(originalBranch, composite("replacement", leaf("r"), leaf("s"), leaf("t")))
          right.addChild(originalBranch, rightTail)
        }
      }.asCompletableFuture().get()

      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(frozenRelations, relations(originalBranch))
    }

    assertEquals(afterSnapshot, liveSnapshot(root))
    assertEquals(liveRelations, liveRelations(originalBranch))
  }

  @Test
  fun `freezePsiVersion preserves zero-length sibling offsets during earlier branch growth`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val (root, growingLeaf, emptyBeforeMiddle, middle, emptyBeforeTail, tail, tailInner, tailLeaf) = runSyncVersionedWriteAction {
      val growingLeaf = leaf("b")
      val emptyBeforeMiddle = composite("gapOne")
      val middle = composite("middle", leaf("cc"))
      val emptyBeforeTail = composite("gapTwo")
      val tailLeaf = leaf("ff")
      val tailInner = composite("tailInner", leaf("e"), tailLeaf)
      val tail = composite("tail", leaf("dd"), tailInner)
      ZeroLengthSiblingTree(
        root = composite("root", composite("head", leaf("aa"), composite("headGap"), growingLeaf), emptyBeforeMiddle, middle, emptyBeforeTail, tail),
        growingLeaf = growingLeaf,
        emptyBeforeMiddle = emptyBeforeMiddle,
        middle = middle,
        emptyBeforeTail = emptyBeforeTail,
        tail = tail,
        tailInner = tailInner,
        tailLeaf = tailLeaf,
      )
    }

    val beforeSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("head", 0, leafSnapshot("aa", 0), branchSnapshot("headGap", 2), leafSnapshot("b", 2)),
      branchSnapshot("gapOne", 3),
      branchSnapshot("middle", 3, leafSnapshot("cc", 0)),
      branchSnapshot("gapTwo", 5),
      branchSnapshot("tail", 5, leafSnapshot("dd", 0), branchSnapshot("tailInner", 2, leafSnapshot("e", 0), leafSnapshot("ff", 1))),
    )
    val afterSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("head", 0, leafSnapshot("aa", 0), branchSnapshot("headGap", 2), leafSnapshot("longer", 2)),
      branchSnapshot("gapOne", 8),
      branchSnapshot("middle", 8, leafSnapshot("cc", 0)),
      branchSnapshot("gapTwo", 10),
      branchSnapshot("tail", 10, leafSnapshot("dd", 0), branchSnapshot("tailInner", 2, leafSnapshot("e", 0), leafSnapshot("ff", 1))),
    )
    val beforeOffsets = ZeroLengthOffsetObservation(
      emptyBeforeMiddleStartOffsetInParent = 3,
      emptyBeforeMiddleStartOffset = 3,
      emptyBeforeMiddleEndOffset = 3,
      middleStartOffsetInParent = 3,
      middleStartOffset = 3,
      middleEndOffset = 5,
      emptyBeforeTailStartOffsetInParent = 5,
      emptyBeforeTailStartOffset = 5,
      emptyBeforeTailEndOffset = 5,
      tailStartOffsetInParent = 5,
      tailStartOffset = 5,
      tailEndOffset = 10,
      tailInnerStartOffsetInParent = 2,
      tailInnerStartOffset = 7,
      tailLeafStartOffsetInParent = 1,
      tailLeafStartOffset = 8,
      tailLeafEndOffset = 10,
    )
    val afterOffsets = ZeroLengthOffsetObservation(
      emptyBeforeMiddleStartOffsetInParent = 8,
      emptyBeforeMiddleStartOffset = 8,
      emptyBeforeMiddleEndOffset = 8,
      middleStartOffsetInParent = 8,
      middleStartOffset = 8,
      middleEndOffset = 10,
      emptyBeforeTailStartOffsetInParent = 10,
      emptyBeforeTailStartOffset = 10,
      emptyBeforeTailEndOffset = 10,
      tailStartOffsetInParent = 10,
      tailStartOffset = 10,
      tailEndOffset = 15,
      tailInnerStartOffsetInParent = 2,
      tailInnerStartOffset = 12,
      tailLeafStartOffsetInParent = 1,
      tailLeafStartOffset = 13,
      tailLeafEndOffset = 15,
    )

    runReadActionBlocking {
      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(beforeOffsets, collectZeroLengthOffsets(emptyBeforeMiddle, middle, emptyBeforeTail, tail, tailInner, tailLeaf))
    }

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(beforeOffsets, collectZeroLengthOffsets(emptyBeforeMiddle, middle, emptyBeforeTail, tail, tailInner, tailLeaf))

      async(Dispatchers.Default) {
        backgroundWriteAction {
          val head = assertNotNull(growingLeaf.treeParent)
          head.replaceChild(growingLeaf, leaf("longer"))
        }
      }.asCompletableFuture().get()

      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(beforeOffsets, collectZeroLengthOffsets(emptyBeforeMiddle, middle, emptyBeforeTail, tail, tailInner, tailLeaf))
    }

    assertEquals(afterSnapshot, liveSnapshot(root))
    assertEquals(afterOffsets, runReadActionBlocking { collectZeroLengthOffsets(emptyBeforeMiddle, middle, emptyBeforeTail, tail, tailInner, tailLeaf) })
  }

  @Test
  fun `freezePsiVersion preserves detached subtree state while subtree is mutated and reattached`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val (root, left, right, originalBranch, originalInner, innerTailLeaf, rightTail) = runSyncVersionedWriteAction {
      val innerTailLeaf = leaf("r")
      val originalInner = composite("inner", leaf("q"), innerTailLeaf)
      val originalBranch = composite("original", leaf("p"), originalInner)
      val left = composite("left", leaf("a"), originalBranch, leaf("b"))
      val rightTail = leaf("d")
      val right = composite("right", leaf("c"), rightTail)
      DetachedMutationReattachTree(
        root = composite("root", left, composite("bridge", leaf("u"), leaf("v")), right),
        left = left,
        right = right,
        originalBranch = originalBranch,
        originalInner = originalInner,
        innerTailLeaf = innerTailLeaf,
        rightTail = rightTail,
      )
    }

    val beforeSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("a", 0), branchSnapshot("original", 1, leafSnapshot("p", 0), branchSnapshot("inner", 1, leafSnapshot("q", 0), leafSnapshot("r", 1))), leafSnapshot("b", 4)),
      branchSnapshot("bridge", 5, leafSnapshot("u", 0), leafSnapshot("v", 1)),
      branchSnapshot("right", 7, leafSnapshot("c", 0), leafSnapshot("d", 1)),
    )
    val afterSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("left", 0, leafSnapshot("a", 0), leafSnapshot("b", 1)),
      branchSnapshot("bridge", 2, leafSnapshot("u", 0), leafSnapshot("v", 1)),
      branchSnapshot("right", 4, leafSnapshot("c", 0), branchSnapshot("original", 1, leafSnapshot("p", 0), branchSnapshot("inner", 1, leafSnapshot("q", 0), leafSnapshot("long", 1)), leafSnapshot("z", 6)), leafSnapshot("d", 8)),
    )
    val frozenRelations = ElementRelations(
      name = "original",
      text = "pqr",
      startOffsetInParent = 1,
      parent = "left",
      previous = "a",
      next = "b",
    )
    val liveRelations = ElementRelations(
      name = "original",
      text = "pqlongz",
      startOffsetInParent = 1,
      parent = "right",
      previous = "c",
      next = "d",
    )
    val frozenInnerTailRelations = ElementRelations(
      name = "r",
      text = "r",
      startOffsetInParent = 1,
      parent = "inner",
      previous = "q",
      next = null,
    )
    val detachedInnerTailRelations = ElementRelations(
      name = "r",
      text = "r",
      startOffsetInParent = 0,
      parent = "DUMMY_HOLDER",
      previous = null,
      next = null,
    )

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      assertEquals(beforeSnapshot, snapshot(root))

      async(Dispatchers.Default) {
        backgroundWriteAction {
          left.removeChild(originalBranch)
          originalInner.replaceChild(innerTailLeaf, leaf("long"))
          originalBranch.addChild(leaf("z"))
          right.addChild(originalBranch, rightTail)
        }
      }.asCompletableFuture().get()

      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(frozenRelations, relations(originalBranch))
      assertEquals(frozenInnerTailRelations, relations(innerTailLeaf))
    }

    assertEquals(afterSnapshot, liveSnapshot(root))
    assertEquals(liveRelations, liveRelations(originalBranch))
    assertEquals(detachedInnerTailRelations, liveRelations(innerTailLeaf))
  }

  @Test
  fun `freezePsiVersion preserves equal-length structural replacement when sibling offsets stay stable`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val (root, subject, removedLeaf, suffix, tail) = runSyncVersionedWriteAction {
      val removedLeaf = leaf("cd")
      val subject = composite("subject", leaf("ab"), removedLeaf)
      val suffix = composite("suffix", leaf("xy"), leaf("z"))
      val tail = composite("tail", leaf("!"))
      EqualLengthStructuralRewriteTree(
        root = composite("root", composite("prefix", leaf("w")), subject, suffix, tail),
        subject = subject,
        removedLeaf = removedLeaf,
        suffix = suffix,
        tail = tail,
      )
    }

    val beforeSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("prefix", 0, leafSnapshot("w", 0)),
      branchSnapshot("subject", 1, leafSnapshot("ab", 0), leafSnapshot("cd", 2)),
      branchSnapshot("suffix", 5, leafSnapshot("xy", 0), leafSnapshot("z", 2)),
      branchSnapshot("tail", 8, leafSnapshot("!", 0)),
    )
    val afterSnapshot = branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot("prefix", 0, leafSnapshot("w", 0)),
      branchSnapshot(
        "subject",
        1,
        branchSnapshot("first", 0, leafSnapshot("a", 0)),
        branchSnapshot("gap", 1),
        branchSnapshot("second", 1, leafSnapshot("b", 0), leafSnapshot("c", 1)),
        leafSnapshot("d", 3),
      ),
      branchSnapshot("suffix", 5, leafSnapshot("xy", 0), leafSnapshot("z", 2)),
      branchSnapshot("tail", 8, leafSnapshot("!", 0)),
    )
    val stableOffsets = TrailingOffsetObservation(
      suffixStartOffsetInParent = 5,
      suffixStartOffset = 5,
      suffixEndOffset = 8,
      tailStartOffsetInParent = 8,
      tailStartOffset = 8,
      tailEndOffset = 9,
    )
    val frozenRemovedLeafRelations = ElementRelations(
      name = "cd",
      text = "cd",
      startOffsetInParent = 2,
      parent = "subject",
      previous = "ab",
      next = null,
    )
    val detachedSubjectRelations = ElementRelations(
      name = "subject",
      text = "abcd",
      startOffsetInParent = 0,
      parent = "DUMMY_HOLDER",
      previous = null,
      next = null,
    )
    val detachedRemovedLeafRelations = ElementRelations(
      name = "cd",
      text = "cd",
      startOffsetInParent = 2,
      parent = "subject",
      previous = "ab",
      next = null,
    )

    runReadActionBlocking {
      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(stableOffsets, collectTrailingOffsets(suffix, tail))
    }

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(stableOffsets, collectTrailingOffsets(suffix, tail))

      async(Dispatchers.Default) {
        backgroundWriteAction {
          val root = assertNotNull(subject.treeParent)
          root.replaceChild(
            subject,
            composite(
              "subject",
              composite("first", leaf("a")),
              composite("gap"),
              composite("second", leaf("b"), leaf("c")),
              leaf("d"),
            ),
          )
        }
      }.asCompletableFuture().get()

      assertEquals(beforeSnapshot, snapshot(root))
      assertEquals(stableOffsets, collectTrailingOffsets(suffix, tail))
      assertEquals(frozenRemovedLeafRelations, relations(removedLeaf))
    }

    assertEquals(afterSnapshot, liveSnapshot(root))
    assertEquals(stableOffsets, runReadActionBlocking { collectTrailingOffsets(suffix, tail) })
    assertEquals(detachedSubjectRelations, liveRelations(subject))
    assertEquals(detachedRemovedLeafRelations, liveRelations(removedLeaf))
  }

  @Test
  fun `freezePsiVersion preserves lazy parse snapshot during concurrent replacement`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val parseProbe = LazyParseProbe()
    val (root, lazyHost, lazyChild) = runSyncVersionedWriteAction {
      exoticLazyTree(lazyName = "lazy-old", lazyText = "ab", parseProbe = parseProbe)
    }
    val beforeSnapshot = exoticLazySnapshot(lazyName = "lazy-old", lazyText = "ab")
    val afterSnapshot = exoticLazySnapshot(lazyName = "lazy-new", lazyText = "xyzuvw")

    val writer = launch(Dispatchers.Default) {
      parseProbe.parseStarted.await()
      backgroundWriteAction {
        lazyHost.replaceChild(lazyChild, lazy("lazy-new", "xyzuvw"))
      }
      parseProbe.continueParsing.complete(Unit)
    }

    val frozenSnapshot = PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      snapshot(root)
    }

    writer.join()

    assertEquals(beforeSnapshot, frozenSnapshot)
    assertEquals(1, parseProbe.parseCount.get())
    assertEquals(afterSnapshot, liveSnapshot(root))
  }

  @Test
  fun `concurrent freezePsiVersion snapshots expand lazy parseable element only once`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val parseProbe = LazyParseProbe()
    val (root, _, lazyChild) = runSyncVersionedWriteAction {
      exoticLazyTree(lazyName = "lazy", lazyText = "cabd", parseProbe = parseProbe)
    }
    val secondSnapshotReachedLazy = CompletableDeferred<Unit>()
    val expectedSnapshot = exoticLazySnapshot(lazyName = "lazy", lazyText = "cabd")

    val firstSnapshot = async(Dispatchers.Default) {
      PsiVersioningService.freezePsiVersion {
        ThreadingAssertions.assertNoReadAccess()
        snapshot(root)
      }
    }

    parseProbe.parseStarted.await()

    val secondSnapshot = async(Dispatchers.Default) {
      PsiVersioningService.freezePsiVersion {
        ThreadingAssertions.assertNoReadAccess()
        snapshot(root) { element ->
          if (element === lazyChild) {
            secondSnapshotReachedLazy.complete(Unit)
          }
        }
      }
    }

    secondSnapshotReachedLazy.await()
    parseProbe.continueParsing.complete(Unit)

    assertEquals(expectedSnapshot, firstSnapshot.await())
    assertEquals(expectedSnapshot, secondSnapshot.await())
    assertEquals(1, parseProbe.parseCount.get())
  }

  @Test
  fun `freezePsiVersion does not permit locks`() {
    PsiVersioningService.freezePsiVersion {
      assertFailsWith<ThreadingSupport.LockAccessDisallowed> {
        runReadActionBlocking {
        }
      }
      assertFailsWith<ThreadingSupport.LockAccessDisallowed> {
        runWriteAction {}
      }
      assertFailsWith<ThreadingSupport.LockAccessDisallowed> {
        WriteIntentReadAction.run {}
      }
    }
  }

  private fun installVersioningListeners(disposable: Disposable) {
    val listener = PsiVersioningLockingListener()
    ApplicationManagerEx.getApplicationEx().addWriteActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addSuspendingWriteActionListener(listener, disposable)
  }

  private fun snapshot(element: TreeElement, onEnter: ((TreeElement) -> Unit)? = null): TreeSnapshot {
    onEnter?.invoke(element)
    val firstChild = element.firstChildNode
    var previousChild: TreeElement? = null
    var currentChild = firstChild
    val childSnapshots = mutableListOf<TreeSnapshot>()
    while (currentChild != null) {
      assertSame(element, currentChild.treeParent)
      if (previousChild == null) {
        assertNull(currentChild.treePrev)
      }
      else {
        assertSame(previousChild, currentChild.treePrev)
        assertSame(currentChild, previousChild.treeNext)
      }
      childSnapshots += snapshot(currentChild, onEnter)
      previousChild = currentChild
      currentChild = currentChild.treeNext
    }

    if (firstChild == null) {
      assertNull(element.lastChildNode)
    }
    else {
      assertSame(previousChild, element.lastChildNode)
      assertNull(previousChild?.treeNext)
    }

    val text = if (childSnapshots.isEmpty()) element.text else childSnapshots.joinToString(separator = "") { it.text }

    return TreeSnapshot(
      name = element.elementType.toString(),
      text = text,
      startOffsetInParent = element.startOffsetInParent,
      children = childSnapshots,
    )
  }

  private fun relations(element: TreeElement): ElementRelations {
    return ElementRelations(
      name = element.elementType.toString(),
      text = element.text,
      startOffsetInParent = element.startOffsetInParent,
      parent = element.treeParent?.elementType?.toString(),
      previous = element.treePrev?.elementType?.toString(),
      next = element.treeNext?.elementType?.toString(),
    )
  }

  private fun liveSnapshot(element: TreeElement): TreeSnapshot {
    return runReadActionBlocking {
      snapshot(element)
    }
  }

  private fun liveRelations(element: TreeElement): ElementRelations {
    return runReadActionBlocking {
      relations(element)
    }
  }

  private fun collectOffsets(
    middle: TreeElement,
    tail: TreeElement,
    tailInner: TreeElement,
    tailLeaf: TreeElement,
  ): OffsetObservation {
    return OffsetObservation(
      middleStartOffsetInParent = middle.startOffsetInParent,
      middleStartOffset = middle.startOffset,
      middleEndOffset = middle.textRange.endOffset,
      tailStartOffsetInParent = tail.startOffsetInParent,
      tailStartOffset = tail.startOffset,
      tailEndOffset = tail.textRange.endOffset,
      tailInnerStartOffsetInParent = tailInner.startOffsetInParent,
      tailInnerStartOffset = tailInner.startOffset,
      tailLeafStartOffsetInParent = tailLeaf.startOffsetInParent,
      tailLeafStartOffset = tailLeaf.startOffset,
      tailLeafEndOffset = tailLeaf.textRange.endOffset,
    )
  }

  private fun collectZeroLengthOffsets(
    emptyBeforeMiddle: TreeElement,
    middle: TreeElement,
    emptyBeforeTail: TreeElement,
    tail: TreeElement,
    tailInner: TreeElement,
    tailLeaf: TreeElement,
  ): ZeroLengthOffsetObservation {
    return ZeroLengthOffsetObservation(
      emptyBeforeMiddleStartOffsetInParent = emptyBeforeMiddle.startOffsetInParent,
      emptyBeforeMiddleStartOffset = emptyBeforeMiddle.startOffset,
      emptyBeforeMiddleEndOffset = emptyBeforeMiddle.textRange.endOffset,
      middleStartOffsetInParent = middle.startOffsetInParent,
      middleStartOffset = middle.startOffset,
      middleEndOffset = middle.textRange.endOffset,
      emptyBeforeTailStartOffsetInParent = emptyBeforeTail.startOffsetInParent,
      emptyBeforeTailStartOffset = emptyBeforeTail.startOffset,
      emptyBeforeTailEndOffset = emptyBeforeTail.textRange.endOffset,
      tailStartOffsetInParent = tail.startOffsetInParent,
      tailStartOffset = tail.startOffset,
      tailEndOffset = tail.textRange.endOffset,
      tailInnerStartOffsetInParent = tailInner.startOffsetInParent,
      tailInnerStartOffset = tailInner.startOffset,
      tailLeafStartOffsetInParent = tailLeaf.startOffsetInParent,
      tailLeafStartOffset = tailLeaf.startOffset,
      tailLeafEndOffset = tailLeaf.textRange.endOffset,
    )
  }

  private fun collectTrailingOffsets(suffix: TreeElement, tail: TreeElement): TrailingOffsetObservation {
    return TrailingOffsetObservation(
      suffixStartOffsetInParent = suffix.startOffsetInParent,
      suffixStartOffset = suffix.startOffset,
      suffixEndOffset = suffix.textRange.endOffset,
      tailStartOffsetInParent = tail.startOffsetInParent,
      tailStartOffset = tail.startOffset,
      tailEndOffset = tail.textRange.endOffset,
    )
  }

  private fun replaceChildren(parent: CompositeElement, vararg children: TreeElement) {
    while (parent.firstChildNode != null) {
      parent.removeChild(parent.firstChildNode)
    }
    children.forEach { child ->
      parent.addChild(child)
    }
  }

  private fun shapeAChildren(): Array<TreeElement> {
    return arrayOf(
      composite("left", leaf("a"), leaf("b")),
      composite("middle", leaf("c")),
      composite("right", leaf("d")),
    )
  }

  private fun shapeBChildren(): Array<TreeElement> {
    return arrayOf(
      composite("left", leaf("a"), leaf("x")),
      composite("middle"),
      composite("replacement", leaf("y"), leaf("z")),
    )
  }

  private fun composite(name: String, vararg children: TreeElement): CompositePsiElement {
    val composite = attachToDummyHolder(object : CompositePsiElement(IElementType(name, null)) {
      override fun toString(): String {
        return name
      }
    })
    children.forEach { child ->
      composite.addChild(child)
    }
    return composite
  }

  private fun rawLazyHost(vararg children: TreeElement): CompositePsiElement {
    val composite = attachToDummyHolder(object : CompositePsiElement(IElementType("host", null)) {
      override fun toString(): String {
        return "host"
      }
    })
    children.forEach { child ->
      composite.rawAddChildrenWithoutNotifications(child)
    }
    return composite
  }

  private fun leaf(name: String): LeafPsiElement {
    return attachToDummyHolder(object : LeafPsiElement(IElementType(name, null), name) {
      override fun toString(): String {
        return name
      }
    })
  }

  private fun lazy(name: String, text: String, parseProbe: LazyParseProbe? = null): LazyParseableElement {
    return attachToDummyHolder(LazyParseableElement(TestLazyElementType(name, parseProbe), text))
  }

  private fun exoticLazyTree(
    lazyName: String,
    lazyText: String,
    parseProbe: LazyParseProbe? = null,
  ): ExoticLazyTree {
    val lazyChild = lazy(lazyName, lazyText, parseProbe)
    val lazyHost = rawLazyHost(leaf("("), lazyChild, leaf(")"), leaf("!"))
    val root = composite(
      "root",
      composite("prefix", leaf("pre"), composite("ornament", leaf("{"), leaf("}"))),
      lazyHost,
      composite("middle", leaf("mu"), leaf("d")),
      composite("void"),
      composite("suffix", leaf("tail"), leaf("?")),
    )
    return ExoticLazyTree(
      root = root,
      lazyHost = lazyHost,
      lazyChild = lazyChild,
    )
  }

  private fun exoticLazySnapshot(lazyName: String, lazyText: String): TreeSnapshot {
    val prefixLength = "pre{}".length
    val hostLength = 1 + lazyText.length + 1 + 1
    val middleOffset = prefixLength + hostLength
    val voidOffset = middleOffset + "mud".length
    val lazyChildren = lazyText.mapIndexed { index, character ->
      leafSnapshot(character.toString(), index)
    }

    return branchSnapshot(
      name = "root",
      startOffsetInParent = 0,
      branchSnapshot(
        "prefix",
        0,
        leafSnapshot("pre", 0),
        branchSnapshot("ornament", 3, leafSnapshot("{", 0), leafSnapshot("}", 1)),
      ),
      branchSnapshot(
        "host",
        prefixLength,
        leafSnapshot("(", 0),
        branchSnapshot(lazyName, 1, *lazyChildren.toTypedArray()),
        leafSnapshot(")", 1 + lazyText.length),
        leafSnapshot("!", 2 + lazyText.length),
      ),
      branchSnapshot("middle", middleOffset, leafSnapshot("mu", 0), leafSnapshot("d", 2)),
      branchSnapshot("void", voidOffset),
      branchSnapshot("suffix", voidOffset, leafSnapshot("tail", 0), leafSnapshot("?", 4)),
    )
  }

  private fun parsedChildren(text: CharSequence): ASTNode? {
    if (text.isEmpty()) {
      return null
    }

    val container = object : CompositePsiElement(IElementType("parsed", null)) {}
    text.forEach { character ->
      val charText = character.toString()
      container.rawAddChildrenWithoutNotifications(object : LeafPsiElement(IElementType(charText, null), charText) {
        override fun toString(): String {
          return charText
        }
      })
    }
    return container.firstChildNode
  }

  private fun <T : TreeElement> attachToDummyHolder(element: T): T {
    DummyHolder(psiManager, element, null, CharTableImpl())
    CodeEditUtil.setNodeGenerated(element, true)
    return element
  }

  private fun branchSnapshot(name: String, startOffsetInParent: Int, vararg children: TreeSnapshot): TreeSnapshot {
    return TreeSnapshot(
      name = name,
      text = children.joinToString(separator = "") { it.text },
      startOffsetInParent = startOffsetInParent,
      children = children.toList(),
    )
  }

  private fun leafSnapshot(name: String, startOffsetInParent: Int): TreeSnapshot {
    return TreeSnapshot(
      name = name,
      text = name,
      startOffsetInParent = startOffsetInParent,
      children = emptyList(),
    )
  }

  private data class TreeUnderTest(
    val root: CompositePsiElement,
    val firstLeaf: LeafPsiElement,
    val removableLeaf: LeafPsiElement,
    val replacedBranch: CompositePsiElement,
  )

  private data class TreeHeadTailTestData(
    val root: CompositePsiElement,
    val firstLeaf: LeafPsiElement,
    val middleLeaf: LeafPsiElement,
    val tailLeaf: LeafPsiElement,
  )

  private data class SameObjectReparentTree(
    val root: CompositePsiElement,
    val left: CompositePsiElement,
    val right: CompositePsiElement,
    val movingBranch: CompositePsiElement,
    val rightTail: LeafPsiElement,
  )

  private data class OffsetInvalidationTree(
    val root: CompositePsiElement,
    val headInner: CompositePsiElement,
    val firstHeadInnerLeaf: LeafPsiElement,
    val middle: CompositePsiElement,
    val tail: CompositePsiElement,
    val tailInner: CompositePsiElement,
    val tailLeaf: LeafPsiElement,
  )

  private data class AbaReattachTree(
    val root: CompositePsiElement,
    val left: CompositePsiElement,
    val right: CompositePsiElement,
    val originalBranch: CompositePsiElement,
    val rightTail: LeafPsiElement,
  )

  private data class ZeroLengthSiblingTree(
    val root: CompositePsiElement,
    val growingLeaf: LeafPsiElement,
    val emptyBeforeMiddle: CompositePsiElement,
    val middle: CompositePsiElement,
    val emptyBeforeTail: CompositePsiElement,
    val tail: CompositePsiElement,
    val tailInner: CompositePsiElement,
    val tailLeaf: LeafPsiElement,
  )

  private data class DetachedMutationReattachTree(
    val root: CompositePsiElement,
    val left: CompositePsiElement,
    val right: CompositePsiElement,
    val originalBranch: CompositePsiElement,
    val originalInner: CompositePsiElement,
    val innerTailLeaf: LeafPsiElement,
    val rightTail: LeafPsiElement,
  )

  private data class EqualLengthStructuralRewriteTree(
    val root: CompositePsiElement,
    val subject: CompositePsiElement,
    val removedLeaf: LeafPsiElement,
    val suffix: CompositePsiElement,
    val tail: CompositePsiElement,
  )

  private data class TreeSnapshot(
    val name: String,
    val text: String,
    val startOffsetInParent: Int,
    val children: List<TreeSnapshot>,
  )

  private data class OffsetObservation(
    val middleStartOffsetInParent: Int,
    val middleStartOffset: Int,
    val middleEndOffset: Int,
    val tailStartOffsetInParent: Int,
    val tailStartOffset: Int,
    val tailEndOffset: Int,
    val tailInnerStartOffsetInParent: Int,
    val tailInnerStartOffset: Int,
    val tailLeafStartOffsetInParent: Int,
    val tailLeafStartOffset: Int,
    val tailLeafEndOffset: Int,
  )

  private data class ZeroLengthOffsetObservation(
    val emptyBeforeMiddleStartOffsetInParent: Int,
    val emptyBeforeMiddleStartOffset: Int,
    val emptyBeforeMiddleEndOffset: Int,
    val middleStartOffsetInParent: Int,
    val middleStartOffset: Int,
    val middleEndOffset: Int,
    val emptyBeforeTailStartOffsetInParent: Int,
    val emptyBeforeTailStartOffset: Int,
    val emptyBeforeTailEndOffset: Int,
    val tailStartOffsetInParent: Int,
    val tailStartOffset: Int,
    val tailEndOffset: Int,
    val tailInnerStartOffsetInParent: Int,
    val tailInnerStartOffset: Int,
    val tailLeafStartOffsetInParent: Int,
    val tailLeafStartOffset: Int,
    val tailLeafEndOffset: Int,
  )

  private data class TrailingOffsetObservation(
    val suffixStartOffsetInParent: Int,
    val suffixStartOffset: Int,
    val suffixEndOffset: Int,
    val tailStartOffsetInParent: Int,
    val tailStartOffset: Int,
    val tailEndOffset: Int,
  )

  private data class ElementRelations(
    val name: String,
    val text: String,
    val startOffsetInParent: Int,
    val parent: String?,
    val previous: String?,
    val next: String?,
  )

  private data class ExoticLazyTree(
    val root: CompositePsiElement,
    val lazyHost: CompositePsiElement,
    val lazyChild: LazyParseableElement,
  )

  private class LazyParseProbe {
    val parseStarted = CompletableDeferred<Unit>()
    val continueParsing = CompletableDeferred<Unit>()
    val parseCount = AtomicInteger()
  }

  private inner class TestLazyElementType(
    debugName: String,
    private val parseProbe: LazyParseProbe?,
  ) : ILazyParseableElementType(debugName) {
    override fun parseContents(chameleon: ASTNode): ASTNode? {
      parseProbe?.let { probe ->
        probe.parseCount.incrementAndGet()
        probe.parseStarted.complete(Unit)
        probe.continueParsing.asCompletableFuture().get()
      }
      return parsedChildren(chameleon.chars)
    }
  }

  fun <T> runSyncVersionedWriteAction(action: () -> T): T {
    return runWriteAction {
      InternalPsiVersioning.inVersionedEnvironment(true, action)
    }
  }
}
