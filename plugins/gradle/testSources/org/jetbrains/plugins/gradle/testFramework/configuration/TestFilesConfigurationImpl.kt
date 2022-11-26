// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.configuration

import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.vfs.*
import com.intellij.openapi.file.VirtualFileUtil
import java.util.ArrayList
import java.util.HashMap

open class TestFilesConfigurationImpl : TestFilesConfiguration {

  private val directories = HashSet<String>()
  private val files = HashMap<String, String>()
  private val builders = ArrayList<(VirtualFile) -> Unit>()

  override fun findFile(relativePath: String): String? {
    return files[relativePath]
  }

  override fun getFile(relativePath: String): String {
    return requireNotNull(findFile(relativePath)) { "Cannot find file $relativePath" }
  }

  override fun withDirectory(relativePath: String) {
    directories.add(relativePath)
  }

  override fun withFile(relativePath: String, content: String) {
    files[relativePath] = content
  }

  override fun withFiles(action: (VirtualFile) -> Unit) {
    builders.add(action)
  }

  override fun areContentsEqual(root: VirtualFile): Boolean {
    for ((path, expectedContent) in files) {
      val file = VirtualFileUtil.findFile(root, path) ?: return false
      val content = VirtualFileUtil.getTextContent(file)
      if (expectedContent != content) {
        return false
      }
    }
    for (path in directories) {
      if (VirtualFileUtil.findDirectory(root, path) == null) {
        return false
      }
    }
    return true
  }

  override fun createFiles(root: VirtualFile) {
    runWriteActionAndWait {
      for ((path, content) in files) {
        val file = VirtualFileUtil.createFile(root, path)
        VirtualFileUtil.setTextContent(file, content)
      }
      for (path in directories) {
        VirtualFileUtil.createDirectory(root, path)
      }
    }
    builders.forEach { it(root) }
  }
}