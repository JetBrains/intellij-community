// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.indexing

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

@RunsInEdt
class IndexableFilesExcludedWithDirectoryIndexExcludePolicyTest : IndexableFilesBaseTest() {
  @Test
  fun `file residing under directory excluded by DirectoryIndexExcludePolicy must not be indexed`() {
    lateinit var excludedDir: DirectorySpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excludedDir = dir("excludedByPolicy") {
          file("ExcludedFile.java", "class ExcludedFile {}")
        }
      }
    }
    val excludedDirFile = excludedDir.file  // load VFS synchronously outside read action
    val directoryIndexExcludePolicy = object : DirectoryIndexExcludePolicy {
      override fun getExcludeUrlsForProject() = arrayOf(excludedDirFile.url)
    }
    maskDirectoryIndexExcludePolicy(directoryIndexExcludePolicy)
    assertIndexableFiles()
  }

  private fun maskDirectoryIndexExcludePolicy(vararg directoryIndexExcludePolicy: DirectoryIndexExcludePolicy) {
    runWriteAction {
      (DirectoryIndexExcludePolicy.EP_NAME.getPoint(project) as ExtensionPointImpl<DirectoryIndexExcludePolicy>).maskAll(
        directoryIndexExcludePolicy.toList(), disposableRule.disposable, true)
      fireRootsChanged()
    }
  }
}
