// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language

import com.intellij.editorconfig.common.plugin.EditorConfigFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.StructureChange
import com.intellij.openapi.vcs.changes.StructureChangeCollector
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class EditorConfigStructureChangeCollectorTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `modified options keep their names and navigation offsets`(): Unit = timeoutRunBlocking {
    val before = "[*]\nindent_size = 2\ncharset = utf-8\ntrim_trailing_whitespace = true"
    val after = "[*]\nindent_size = 4\nend_of_line = lf\ntrim_trailing_whitespace = true"

    val section = collect(before, after).single()
    assertEquals("[*]", section.text)
    assertEquals(FileStatus.NOT_CHANGED, section.status)
    assertEquals(0, section.offset)
    assertEquals(mapOf("indent_size" to FileStatus.MODIFIED, "end_of_line" to FileStatus.ADDED, "charset" to FileStatus.DELETED),
                 section.children.associate { it.text to it.status })
    assertEquals(after.indexOf("indent_size"), section.children.single { it.text == "indent_size" }.offset)
    assertEquals(after.indexOf("end_of_line"), section.children.single { it.text == "end_of_line" }.offset)
    assertNull(section.children.single { it.text == "charset" }.offset)
  }

  @Test
  fun `added files show every option name`(): Unit = timeoutRunBlocking {
    val text = "[*]\nindent_size = 4\nend_of_line = lf\ntrim_trailing_whitespace = true"
    val section = collect(null, text).single()
    assertEquals(FileStatus.ADDED, section.status)
    assertEquals(listOf("indent_size", "end_of_line", "trim_trailing_whitespace"), section.children.map { it.text })
    assertTrue(section.children.all { it.status == FileStatus.ADDED })
    assertEquals(section.children.map { text.indexOf(it.text) }, section.children.map { it.offset })
  }

  private suspend fun collect(before: String?, after: String): List<StructureChange> = readAction {
    val original = PsiFileFactory.getInstance(projectFixture.get()).createFileFromText(".editorconfig", EditorConfigFileType, after)
    val afterFile = checkNotNull(StructureChangeCollector.createSnapshot(original, after))
    val beforeFile = before?.let { checkNotNull(StructureChangeCollector.createSnapshot(original, it)) }
    StructureChangeCollector.collect(beforeFile, afterFile)
  }
}
