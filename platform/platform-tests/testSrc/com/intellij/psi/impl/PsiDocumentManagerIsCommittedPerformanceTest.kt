// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.lang.ref.Reference

private const val CALLS = 10_000_000

/**
 * Measures [PsiDocumentManager.isCommitted] for a committed document.
 * While the user types, some other document is almost always uncommitted, so that case must cost the same as an idle IDE.
 */
@StressTestApplication
@PerformanceUnitTest
internal class PsiDocumentManagerIsCommittedPerformanceTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val sourceRootFixture = projectFixture.moduleFixture("is-committed").sourceRootFixture()
  private val committedFileFixture = sourceRootFixture.virtualFileFixture("committed.txt", "committed")
  private val typedFileFixture = sourceRootFixture.virtualFileFixture("typed.txt", "typed")

  @TestDisposable
  lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val documentManager get() = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase

  @Test
  fun testIsCommittedWithoutUncommittedDocuments() {
    documentManager.disableBackgroundCommit(disposable)
    val committed = documentWithPsi(committedFileFixture.get())

    benchmark("isCommitted of a committed document, no document uncommitted", committed)
  }

  @Test
  fun testIsCommittedWhileAnotherDocumentIsUncommitted() {
    documentManager.disableBackgroundCommit(disposable)
    val committed = documentWithPsi(committedFileFixture.get())
    val typed = documentWithPsi(typedFileFixture.get())
    timeoutRunBlocking(context = Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) { typed.insertString(0, "x") }
    }
    assertFalse(documentManager.isCommitted(typed))

    benchmark("isCommitted of a committed document, another document uncommitted", committed)
    Reference.reachabilityFence(typed)
  }

  private fun documentWithPsi(file: VirtualFile): Document {
    assertNotNull(PsiManager.getInstance(project).findFile(file))
    val document = FileDocumentManager.getInstance().getDocument(file)
    assertNotNull(document)
    return document
  }

  private fun benchmark(name: String, committed: Document) {
    assertTrue(documentManager.isCommitted(committed))
    Benchmark.newBenchmark(name) {
      repeat(CALLS) {
        if (!documentManager.isCommitted(committed)) throw AssertionError("$committed must stay committed")
      }
    }.runAsStressTest().start()
  }
}
