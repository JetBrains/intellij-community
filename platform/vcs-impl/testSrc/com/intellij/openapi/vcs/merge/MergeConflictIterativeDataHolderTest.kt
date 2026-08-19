// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.TextMergeRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class MergeConflictIterativeDataHolderTest {
  private val projectFixture = projectFixture()
  private val project: Project get() = projectFixture.get()

  /**
   * Regression test for a project leak: reverting a file's resolution used to remove its [com.intellij.diff.merge.MergeConflictModel]
   * from the holder's map without disposing it. Because the model's inner `MergeModelImpl` registers a document listener with itself
   * as the parent disposable, it stays a root node in the global Disposer tree and pins the (closed) [Project] forever.
   *
   * A disposed model tears down its whole Disposer subtree, so we register a probe [Disposable] under the model and assert it is
   * disposed after [MergeConflictIterativeDataHolder.removeFiles] — proving the model itself was disposed.
   */
  @Test
  fun removeFilesDisposesTheModel(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val file = LightVirtualFile("conflicts/sample.txt", "text")
    val request = withContext(Dispatchers.EDT) { createTextMergeRequest() }

    val holder = MergeConflictIterativeDataHolder(project, disposable)
    val model = holder.prepareModelIfSupported(file, request)
    assertNotNull(model, "A built-in text merge request must produce a model")

    // A disposed model disposes its entire Disposer subtree; the probe lets us observe that without touching private state.
    val probe = Disposer.newDisposable()
    Disposer.register(model!!, probe)
    assertFalse(Disposer.isDisposed(probe))

    withContext(Dispatchers.EDT) {
      holder.removeFiles(listOf(file))
    }

    assertTrue(Disposer.isDisposed(probe), "removeFiles must dispose the removed merge model (otherwise it leaks the project)")
    withContext(Dispatchers.EDT) {
      assertNull(holder.getMergeConflictModel(file), "The reverted file's model must be gone from the holder")
    }
  }

  private fun createTextMergeRequest(): TextMergeRequest {
    val factory = DiffContentFactory.getInstance()
    val left = factory.create("left\n")
    val base = factory.create("base\n")
    val right = factory.create("right\n")
    val output = factory.create("").apply { document.setReadOnly(false) }
    return object : TextMergeRequest() {
      override fun getTitle(): String? = null
      override fun applyResult(result: MergeResult) {}
      override fun getContents(): List<DocumentContent> = listOf(left, base, right)
      override fun getOutputContent(): DocumentContent = output
      override fun getContentTitles(): List<String?> = listOf(null, null, null)
    }
  }
}
