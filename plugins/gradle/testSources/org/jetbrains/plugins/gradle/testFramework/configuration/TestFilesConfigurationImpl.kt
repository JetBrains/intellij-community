// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.configuration

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.vfs.*
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import java.util.ArrayList
import java.util.HashMap

open class TestFilesConfigurationImpl : TestFilesConfiguration {

  private val directories = HashSet<String>()
  private val files = HashMap<String, String>()
  private val builders = ArrayList<suspend (VirtualFile) -> Unit>()

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

  override fun withFiles(action: suspend (VirtualFile) -> Unit) {
    builders.add(action)
  }

  override fun areContentsEqual(root: VirtualFile): Boolean {
    for ((path, expectedContent) in files) {
      val file = root.findFile(path) ?: return false
      val content = file.readText()
      if (expectedContent != content) {
        return false
      }
    }
    for (path in directories) {
      if (root.findDirectory(path) == null) {
        return false
      }
    }
    return true
  }

  override suspend fun createFiles(root: VirtualFile) {
    writeAction {
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