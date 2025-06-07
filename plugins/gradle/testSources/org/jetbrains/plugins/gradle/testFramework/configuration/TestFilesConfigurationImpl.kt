// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.configuration

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import org.junit.jupiter.api.Assertions
import java.util.ArrayList
import java.util.HashMap

open class TestFilesConfigurationImpl : TestFilesConfiguration {

  private val directories = HashSet<String>()
  private val files = HashMap<String, String>()
  private val builders = ArrayList<suspend (VirtualFile) -> Unit>()

  override fun withDirectory(relativePath: String) {
    directories.add(relativePath)
  }

  override fun withFile(relativePath: String, content: String) {
    files[relativePath] = content
  }

  override fun withFiles(action: suspend (VirtualFile) -> Unit) {
    builders.add(action)
  }

  override fun areContentsEqual(root: VirtualFile): Boolean {
    for ((relativePath, expectedContent) in files) {
      val file = root.findFile(relativePath) ?: return false
      val content = file.readText()
      if (expectedContent != content) {
        return false
      }
    }
    for (relativePath in directories) {
      if (root.findDirectory(relativePath) == null) {
        return false
      }
    }
    return true
  }

  override fun assertContentsAreEqual(root: VirtualFile) {
    for ((relativePath, expectedContent) in files) {
      val file = root.findFile(relativePath)
      Assertions.assertNotNull(file) {
        "File doesn't exist: ${root.path}/$relativePath"
      }
      val actualContent = file!!.readText()
      val actual = StringUtil.convertLineSeparators(actualContent.trim())
      val expected = StringUtil.convertLineSeparators(expectedContent.trim())
      Assertions.assertEquals(expected, actual) {
        "File doesn't match: ${root.path}/$relativePath"
      }
    }
    for (relativePath in directories) {
      Assertions.assertNotNull(root.findDirectory(relativePath)) {
        "Directory doesn't exist: ${root.path}/$relativePath"
      }
    }
  }

  override suspend fun createFiles(root: VirtualFile) {
    edtWriteAction {
      for ((path, content) in files) {
        val file = root.createFile(path)
        file.writeText(content)
      }
      for (path in directories) {
        root.createDirectory(path)
      }
    }
    builders.forEach { it(root) }
  }
}