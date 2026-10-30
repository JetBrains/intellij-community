// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class StructuralChangesPreviewDimTest {
  private val projectFixture = projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun registerHighlighter() {
    projectFixture.get().registerOrReplaceServiceInstance(StructuralChangesPreviewHighlighter::class.java, NoHighlighter, disposable)
  }

  @Test
  fun `a new dim ends the previous dim before it starts`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val dim = StructuralChangesPreviewDim(projectFixture.get())
      val first = dimHandle()
      assertTrue(dim.replace("first") { first })

      val second = dimHandle()
      assertTrue(dim.replace("second") {
        assertTrue(first.isDisposed, "The previous dim ends first")
        second
      })

      assertTrue(dim.isShown)
      assertEquals("second", dim.owner)
      assertFalse(second.isDisposed)
    }
  }

  @Test
  fun `a dim that ends on its own clears the owner`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val dim = StructuralChangesPreviewDim(projectFixture.get())
      val handle = dimHandle()
      dim.replace("hunk") { handle }

      Disposer.dispose(handle)

      assertFalse(dim.isShown)
      assertNull(dim.owner)
    }
  }

  @Test
  fun `a dim that cannot start leaves no dim`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val dim = StructuralChangesPreviewDim(projectFixture.get())
      val handle = dimHandle()
      dim.replace("first") { handle }

      assertFalse(dim.replace("second") { null })

      assertTrue(handle.isDisposed)
      assertFalse(dim.isShown)
      assertNull(dim.owner)
    }
  }

  private fun dimHandle(): CheckedDisposable = Disposer.newCheckedDisposable(disposable)

  private object NoHighlighter : StructuralChangesPreviewHighlighter {
    override fun highlight(file: VirtualFile, range: TextRange, modificationStamp: Long): Disposable? = null
  }
}
