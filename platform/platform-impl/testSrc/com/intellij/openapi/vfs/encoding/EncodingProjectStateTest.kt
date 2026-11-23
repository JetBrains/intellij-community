// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@TestApplication
class EncodingProjectStateTest {
  companion object {
    private val project = projectFixture()
  }

  private val tempDir = tempPathFixture()

  @Test
  fun `an empty state clears a previously loaded project charset`() {
    val component = EncodingProjectManagerImpl(project.get())
    component.loadState(Element("component").addContent(Element("file").setAttribute("url", "PROJECT").setAttribute("charset", "UTF-16")))
    assertEquals(1, component.state!!.getChildren("file").size)
    component.loadState(Element("component"))
    assertTrue(component.state!!.getChildren("file").isEmpty())
  }

  @Test
  fun `an empty state releases the file pointers of a previously loaded state`() {
    val url = VfsUtilCore.pathToUrl(tempDir.get().resolve("a.txt").toString())
    val component = EncodingProjectManagerImpl(project.get())
    try {
      component.loadState(Element("component").addContent(Element("file").setAttribute("url", url).setAttribute("charset", "UTF-16")))
      assertEquals(1, pointerCount(url))
      component.loadState(Element("component"))
      assertEquals(0, pointerCount(url))
    }
    finally {
      Disposer.dispose(component)
    }
  }

  @Test
  fun `a cleared file encoding releases the file pointer`(): Unit = timeoutRunBlocking {
    val path = Files.writeString(tempDir.get().resolve("b.txt"), "b")
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    val component = EncodingProjectManagerImpl(project.get())
    try {
      withContext(Dispatchers.EDT) {
        component.setEncoding(file, StandardCharsets.US_ASCII)
        component.setEncoding(file, StandardCharsets.ISO_8859_1)
      }
      assertEquals(StandardCharsets.ISO_8859_1, component.getEncoding(file, false))
      assertEquals(1, pointerCount(file.url))
      withContext(Dispatchers.EDT) {
        component.setEncoding(file, null)
      }
      assertTrue(component.allPointersMappings.isEmpty())
      assertEquals(0, pointerCount(file.url))
    }
    finally {
      Disposer.dispose(component)
    }
  }

  @Test
  fun `a pointer mapping without a mapped file reloads the file and releases its pointer`(): Unit = timeoutRunBlocking {
    val path = Files.writeString(tempDir.get().resolve("c.txt"), "c")
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    val component = EncodingProjectManagerImpl(project.get())
    try {
      withContext(Dispatchers.EDT) {
        component.setEncoding(file, StandardCharsets.US_ASCII)
      }
      assertTrue(file.isCharsetSet)
      withContext(Dispatchers.EDT) {
        component.setPointerMapping(emptyMap())
      }
      assertTrue(component.allPointersMappings.isEmpty())
      assertFalse(file.isCharsetSet)
      assertEquals(0, pointerCount(file.url))
    }
    finally {
      Disposer.dispose(component)
    }
  }

  @Test
  fun `a pointer mapping skips a released pointer`(): Unit = timeoutRunBlocking {
    val path = Files.writeString(tempDir.get().resolve("d.txt"), "d")
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    val component = EncodingProjectManagerImpl(project.get())
    try {
      withContext(Dispatchers.EDT) {
        component.setEncoding(file, StandardCharsets.US_ASCII)
      }
      val releasedPointer = component.allPointersMappings.keys.single()
      withContext(Dispatchers.EDT) {
        component.setEncoding(file, null)
        component.setPointerMapping(mapOf(releasedPointer to StandardCharsets.US_ASCII))
      }
      assertTrue(component.allPointersMappings.isEmpty())
      assertEquals(0, pointerCount(file.url))
    }
    finally {
      Disposer.dispose(component)
    }
  }

  private fun pointerCount(url: String): Int =
    (VirtualFilePointerManager.getInstance() as VirtualFilePointerManagerImpl).dumpAllPointers().count { it.url == url }
}
