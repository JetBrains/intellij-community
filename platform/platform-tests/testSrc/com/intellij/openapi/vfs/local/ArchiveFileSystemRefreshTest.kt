// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local

import com.intellij.ide.plugins.loadExtensionWithText
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.openapi.vfs.impl.ZipHandler
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import com.intellij.testFramework.*
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunsInEdt
class ArchiveFileSystemRefreshTest {
  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val appRule = ApplicationRule()

  @get:Rule
  val tempDir = TemporaryDirectory()

  @Test
  fun testArchiveHandlerCacheInvalidation() {
    val text = "<virtualFileSystem implementationClass=\"" + CorruptedJarFileSystemTestWrapper::class.java.name + "\" key=\"corrupted-jar\" physical=\"true\"/>"
    loadExtensionWithText(text, "com.intellij").use {
      CorruptedJarFileSystemTestWrapper.corrupted = true

      val originalJar = Paths.get(PathManager.getJarPathForClass(Test::class.java)!!)
      val tempDir = tempDir.createDir()
      val copiedJar = tempDir.resolve("temp.jar")
      Files.copy(originalJar, copiedJar)

      val rootUrl = "corrupted-jar://$copiedJar!/"
      val root = VirtualFileManager.getInstance().findFileByUrl(rootUrl)!!
      assertTrue { root.isValid }

      val url = "corrupted-jar://$copiedJar!/org/junit/Test.class"
      val file = VirtualFileManager.getInstance().findFileByUrl(url)
      assertNull(file)

      CorruptedJarFileSystemTestWrapper.corrupted = false
      val event = TestActionEvent {
        when (it) {
          CommonDataKeys.VIRTUAL_FILE_ARRAY.name -> arrayOf(root)
          CommonDataKeys.PROJECT.name -> DefaultProjectFactory.getInstance().defaultProject
          else -> null
        }
      }
      ActionManager.getInstance().getAction("SynchronizeCurrentFile").actionPerformed(event)

      val fixedFile = VirtualFileManager.getInstance().findFileByUrl(url)
      assertNotNull(fixedFile)
    }
  }
}

class CorruptedJarFileSystemTestWrapper : JarFileSystemImpl() {
  override fun getProtocol(): String {
    return "corrupted-jar"
  }

  override fun getHandler(entryFile: VirtualFile): ArchiveHandler {
    return VfsImplUtil.getHandler(this, entryFile) {
      object : ZipHandler(it) {
        override fun getEntriesMap(): Map<String, EntryInfo> {
          return if (corrupted) emptyMap() else super.getEntriesMap()
        }
      }
    }
  }

  companion object {
    @Volatile
    var corrupted = false
  }
}