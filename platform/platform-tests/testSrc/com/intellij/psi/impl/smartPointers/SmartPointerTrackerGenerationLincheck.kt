// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.impl.PsiDocumentManagerEx
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.junit.jupiter.api.Test

/**
 * Linearizability of [SmartPointerTracker]'s generation protocol under concurrency:
 * [SmartPointerTracker.isPossiblyInvalidated] reads the generation pair without the tracker
 * monitor (it sits on the hot path of every `SmartPsiElementPointer.getElement()` call), while
 * [SmartPointerTracker.revalidate] catches the tracker up under the monitor and generation bumps
 * come from a third side. A non-linearizable outcome here would mean a pointer can observe
 * "not invalidated" for a state that never existed and skip a revalidation it needs.
 *
 * [intern] checks that the holder interning stays linearizable next to the revalidation,
 * which forgets the holder of a remapped context.
 *
 * Only the generation, mapping and interning surface is exercised: the rest of the tracker API needs real PSI
 * and the application read-write lock, which Lincheck threads cannot provide. The pointers-and-PSI
 * side is covered by `SmartPointerLifecycleTest` and the suites listed in
 * `docs/IntelliJ-Platform/4_man/Lang/PSI/Smart-Pointers.md`.
 */
@TestApplication // SmartPointerTracker's class initializer registers a LowMemoryWatcher against the application
@Suppress("unused")
class SmartPointerTrackerGenerationLincheck {
  private val modCounter = SimpleModificationTracker()
  private val manager = ManagerStub(modCounter)
  private val file: VirtualFile = LightVirtualFile("SmartPointerTrackerGenerationLincheck.txt")

  private val tracker = SmartPointerTracker(-1).also {
    // Consume one mapping at generation 0: this initializes the tracker's context cache to the
    // exact (empty) set, so later revalidations never reach for project services.
    it.pushContextMappingForTests(mapOf(TestContext.A to null))
    it.revalidate(file, manager)
  }

  @Operation
  fun bump() {
    modCounter.incModificationCount()
  }

  @Operation
  fun pushMapping() {
    tracker.pushContextMappingForTests(mapOf(TestContext.A to TestContext.B))
  }

  @Operation
  fun revalidate() {
    tracker.revalidate(file, manager)
  }

  @Operation
  fun stale(): Boolean = tracker.isPossiblyInvalidated(manager)

  private var lastHolder: FileHolder? = null

  /**
   * Returns `true` when the interned holder of [TestContext.A] is the same instance as at the previous call.
   * The tracker monitor makes the interning and the comparison with [lastHolder] one atomic step.
   */
  @Operation
  fun intern(): Boolean = synchronized(tracker) {
    val holder = tracker.internFileHolderForTests(file, TestContext.A)
    val same = holder === lastHolder
    lastHolder = holder
    same
  }

  @Test
  fun stressTest() = StressOptions()
    .sequentialSpecification(GenerationSeqSpec::class.java)
    .check(this::class)

  private enum class TestContext : CodeInsightContext { A, B }

  /** Implements only what [SmartPointerTracker.revalidate] and [SmartPointerTracker.isPossiblyInvalidated] touch. */
  private class ManagerStub(private val modCounter: SimpleModificationTracker) : SmartPointerManagerEx() {
    override fun getPossiblyInvalidationModCounter(): SimpleModificationTracker = modCounter

    override fun fastenBelts(file: VirtualFile) = unused()
    override fun <E : PsiElement> createSmartPsiElementPointer(element: E): SmartPsiElementPointer<E> = unused()
    override fun <E : PsiElement> createSmartPsiElementPointer(element: E, containingFile: PsiFile?): SmartPsiElementPointer<E> = unused()
    override fun <E : PsiElement> createSmartPsiElementPointer(element: E, containingFile: PsiFile?, forInjected: Boolean): SmartPsiElementPointer<E> = unused()
    override fun createSmartPsiFileRangePointer(psiFile: PsiFile, range: TextRange): SmartPsiFileRange = unused()
    override fun createSmartPsiFileRangePointer(file: PsiFile, range: TextRange, forInjected: Boolean): SmartPsiFileRange = unused()
    override fun pointToTheSameElement(pointer1: SmartPsiElementPointer<*>, pointer2: SmartPsiElementPointer<*>): Boolean = unused()
    override fun removePointer(pointer: SmartPsiElementPointer<*>) = unused()
    override fun getTracker(file: VirtualFile): SmartPointerTracker = unused()
    override fun getOrCreateTracker(file: VirtualFile): SmartPointerTracker = unused()
    override fun updatePointers(document: Document, frozen: FrozenDocument, events: List<DocumentEvent>) = unused()
    override fun updatePointerTargetsAfterReparse(file: VirtualFile) = unused()
    override fun getProject(): Project = unused()
    override fun getPsiDocumentManager(): PsiDocumentManagerEx = unused()
    override fun dispose() = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("not used by the generation protocol")
  }
}

/**
 * The sequential model: one generation counter, one validated watermark. `revalidate` catches the
 * watermark up to the counter; `stale` compares them. A revalidation that consumes a pending mapping
 * forgets the interned holder, so the next `intern` gets a new one.
 */
@Suppress("unused")
class GenerationSeqSpec {
  private var modCount = 0L
  private var validated = 0L
  private var mappingPending = false
  private var holderInterned = false

  fun bump() {
    modCount++
  }

  fun pushMapping() {
    mappingPending = true
  }

  fun revalidate() {
    if (modCount > validated) {
      validated = modCount
      if (mappingPending) {
        mappingPending = false
        holderInterned = false
      }
    }
  }

  fun stale(): Boolean = modCount > validated

  fun intern(): Boolean {
    val same = holderInterned
    holderInterned = true
    return same
  }
}
