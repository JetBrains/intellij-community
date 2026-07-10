// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Graphics
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon

class ComposeResourcesGutterIconCacheTest : BasePlatformTestCase() {

  private lateinit var cache: ComposeResourcesGutterIconCache
  private lateinit var sampleFile: VirtualFile
  private var icon: Icon? = null
  private var highDpiDisplay = false

  override fun setUp() {
    super.setUp()
    cache = ComposeResourcesGutterIconCache(::highDpiDisplay)
    sampleFile = myFixture.addFileToProject("HeyImAFile.xml", "whose contents are immaterial").virtualFile
  }

  fun `test cache empty to start`() {
    assertNull(cache.getIconIfCached(sampleFile))
  }

  fun `test cached null value`() {
    assertNull(cache.getIcon(sampleFile) { icon })

    icon = ICON_A
    assertNull(cache.getIconIfCached(sampleFile))
    assertNull(cache.getIcon(sampleFile) { icon })
  }

  fun `test cached non-null value`() {
    icon = ICON_A
    assertSame(ICON_A, cache.getIcon(sampleFile) { icon })

    icon = null
    assertSame(ICON_A, cache.getIconIfCached(sampleFile))
    assertSame(ICON_A, cache.getIcon(sampleFile) { icon })
  }

  fun `test cached value ignored on file change`() {
    icon = ICON_A
    assertSame(ICON_A, cache.getIcon(sampleFile) { icon })

    val document = runReadAction {
      checkNotNull(FileDocumentManager.getInstance().getDocument(sampleFile))
    }
    with(ApplicationManager.getApplication()) {
      invokeAndWait { runWriteAction { document.setText(document.text) } }
    }
    assertNull(cache.getIconIfCached(sampleFile))

    icon = ICON_B
    assertSame(ICON_B, cache.getIcon(sampleFile) { icon })
  }

  fun `test cache cleared on HiDpi change`() {
    icon = ICON_A
    assertSame(ICON_A, cache.getIcon(sampleFile) { icon })

    highDpiDisplay = true
    assertNull(cache.getIconIfCached(sampleFile))

    icon = ICON_B
    assertSame(ICON_B, cache.getIcon(sampleFile) { icon })

    highDpiDisplay = false
    assertNull(cache.getIconIfCached(sampleFile))

    icon = ICON_A
    assertSame(ICON_A, cache.getIcon(sampleFile) { icon })
  }

  fun `test multiple files cached independently`() {
    val file1 = myFixture.addFileToProject("file1.xml", "content1").virtualFile
    val file2 = myFixture.addFileToProject("file2.xml", "content2").virtualFile
    val file3 = myFixture.addFileToProject("file3.xml", "content3").virtualFile

    assertSame(ICON_A, cache.getIcon(file1) { ICON_A })
    assertSame(ICON_B, cache.getIcon(file2) { ICON_B })
    assertSame(ICON_C, cache.getIcon(file3) { ICON_C })

    assertSame(ICON_A, cache.getIconIfCached(file1))
    assertSame(ICON_B, cache.getIconIfCached(file2))
    assertSame(ICON_C, cache.getIconIfCached(file3))

    assertSame(ICON_A, cache.getIcon(file1) { fail("Should use cached"); null })
    assertSame(ICON_B, cache.getIcon(file2) { fail("Should use cached"); null })
    assertSame(ICON_C, cache.getIcon(file3) { fail("Should use cached"); null })
  }

  fun `test file modification only invalidates that file`() {
    val file1 = myFixture.addFileToProject("file1.xml", "content1").virtualFile
    val file2 = myFixture.addFileToProject("file2.xml", "content2").virtualFile

    assertSame(ICON_A, cache.getIcon(file1) { ICON_A })
    assertSame(ICON_B, cache.getIcon(file2) { ICON_B })

    val document = runReadAction {
      checkNotNull(FileDocumentManager.getInstance().getDocument(file1))
    }
    with(ApplicationManager.getApplication()) {
      invokeAndWait { runWriteAction { document.setText("modified content") } }
    }

    assertNull(cache.getIconIfCached(file1))
    assertSame(ICON_B, cache.getIconIfCached(file2))
  }

  fun `test concurrent access to same file`() {
    val threadCount = 10
    val barrier = CyclicBarrier(threadCount)
    val renderCount = AtomicInteger(0)
    val latch = CountDownLatch(threadCount)
    val results = mutableListOf<Icon?>()
    val lock = Any()

    repeat(threadCount) {
      Thread {
        try {
          barrier.await(5, TimeUnit.SECONDS)
          val result = cache.getIcon(sampleFile) {
            renderCount.incrementAndGet()
            Thread.sleep(10)
            ICON_A
          }
          synchronized(lock) { results.add(result) }
        }
        finally {
          latch.countDown()
        }
      }.start()
    }

    assertTrue("Threads should complete", latch.await(10, TimeUnit.SECONDS))
    assertTrue("All results should be the same icon", results.all { it === ICON_A })
  }

  fun `test deleted file not returned from cache`() {
    icon = ICON_A
    assertSame(ICON_A, cache.getIcon(sampleFile) { icon })
    assertSame(ICON_A, cache.getIconIfCached(sampleFile))

    WriteCommandAction.runWriteCommandAction(project) {
      sampleFile.delete(this)
    }

    assertNull(cache.getIconIfCached(sampleFile))
  }

  companion object {
    private val ICON_A: Icon = TestIcon(16)
    private val ICON_B: Icon = TestIcon(32)
    private val ICON_C: Icon = TestIcon(24)
  }
}

private class TestIcon(private val size: Int) : Icon {
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {}
  override fun getIconWidth(): Int = size
  override fun getIconHeight(): Int = size
}