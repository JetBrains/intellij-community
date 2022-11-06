// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.configuration

import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.vfs.VirtualFile
import java.util.ArrayList
import java.util.HashMap

open class TestFilesConfigurationImpl : TestFilesConfiguration {

  private val files = HashMap<String, String>()
  private val builders = ArrayList<(VirtualFile) -> Unit>()

  override fun findFile(relativePath: String): String? {
    return files[relativePath]
  }

  override fun getFile(relativePath: String): String {
    return requireNotNull(findFile(relativePath)) { "Cannot find file $relativePath" }
  }

  override fun withFile(relativePath: String, content: String) {
    files[relativePath] = content
  }

  override fun withFiles(action: (VirtualFile) -> Unit) {
    builders.add(action)
  }

  override fun areContentsEqual(root: VirtualFile): Boolean {
    for ((path, expectedContent) in files) {
      val content = loadText(root, path)
      if (expectedContent != content) {
        return false
      }
    }
    return true
  }

  private fun loadText(root: VirtualFile, relativePath: String): String? {
    return runReadAction {
      root.findFile(relativePath)?.loadText()
    }
  }

  override fun createFiles(root: VirtualFile) {
    runWriteActionAndWait {
      for ((path, content) in files) {
        val file = root.createFile(path)
        file.text = content
      }
    }
    builders.forEach { it(root) }
  }
}