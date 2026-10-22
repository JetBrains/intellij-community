// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language

import com.intellij.editorconfig.common.plugin.EditorConfigFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.HunkLines
import com.intellij.openapi.vcs.changes.StructuralHunkNamer
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class EditorConfigStructuralHunkNamerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `options show their own lines and the section stays out`(): Unit = timeoutRunBlocking {
    val before = "[*]\nindent_size = 2\ncharset = utf-8\ntrim_trailing_whitespace = true"
    val after = "[*]\nindent_size = 4\nend_of_line = lf\ntrim_trailing_whitespace = true"
    val name = readAction {
      val original = PsiFileFactory.getInstance(projectFixture.get()).createFileFromText(".editorconfig", EditorConfigFileType, after)
      val afterFile = checkNotNull(StructuralHunkNamer.createSnapshot(original, after))
      val beforeFile = checkNotNull(StructuralHunkNamer.createSnapshot(original, before))
      StructuralHunkNamer.name(beforeFile, afterFile, listOf(HunkLines(1, 3, 1, 3))).single()
    }

    assertEquals(listOf("indent_size = 4" to FileStatus.MODIFIED, "end_of_line = lf" to FileStatus.ADDED,
                        "charset = utf-8" to FileStatus.DELETED),
                 name.parts.map { it.text to it.status })
  }
}
