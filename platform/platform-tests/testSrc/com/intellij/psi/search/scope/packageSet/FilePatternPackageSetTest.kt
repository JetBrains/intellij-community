// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assumptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@TestApplication
class FilePatternPackageSetTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val tempDir: TempDirectoryExtension = TempDirectoryExtension()

  @Test
  fun `files from CONTENT_NON_INDEXABLE root are included with empty module pattern when path matches`(): Unit = runBlocking {
    val rootPath = tempDir.newDirectoryPath("non-indexable-root")
    val filePath = rootPath.resolve("src/match.txt")
    filePath.parent.createDirectories()
    filePath.writeText("content")

    registerProjectRoot(projectModel.project, rootPath)

    val file = VfsTestUtil.findFileByCaseSensitivePath(filePath.pathString)
    Assumptions.assumeThat(file).isNotNull()

    val packageSet = FilePatternPackageSet("", "src/match.txt")
    assertTrue(readAction { packageSet.contains(file, projectModel.project, null) })
  }

  @Test
  fun `files from CONTENT_NON_INDEXABLE root are not included with non-empty module pattern when path matches`(): Unit = runBlocking {
    val rootPath = tempDir.newDirectoryPath("non-indexable-root")
    val filePath = rootPath.resolve("src/match.txt")
    filePath.parent.createDirectories()
    filePath.writeText("content")

    registerProjectRoot(projectModel.project, rootPath)

    val file = VfsTestUtil.findFileByCaseSensitivePath(filePath.pathString)
    Assumptions.assumeThat(file).isNotNull()

    val packageSet = FilePatternPackageSet("module", "src/match.txt")
    assertFalse(readAction { packageSet.contains(file, projectModel.project, null) })
  }
}
