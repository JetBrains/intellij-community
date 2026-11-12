// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent.Kind
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.createFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

@TestApplication
class GradleCompilationReportParserTest {

  private val FAILURE_HEADER: String = "> Compilation failed; see the compiler output below."
  private val FAILURE_MESSAGE: String = """
      %s:4: error: ']' expected
          public static void main(String[ args) {
                                         ^
      %s:5: error: ';' expected
              System.out.printf("Hello and welcome!")
                                                     ^
      2 errors
      
      * Try:
      > Check your code and dependencies to fix the compilation error(s)
      > Run with --scan to generate a Build Scan (Powered by Develocity).
      
      BUILD FAILED in 78ms
  """.trimIndent()

  @Test
  fun `should handle grouped compilation reports`(@TempDir tempDir: Path) {
    val parser = GradleCompilationReportParser()
    val producedEvents = mutableListOf<BuildEvent>()
    val anchor = tempDir.createFile("Anchor.java")
    val failure = FAILURE_MESSAGE.format(anchor, anchor).split("\n")
    val parentId = UUID.randomUUID()

    val parsed = parser.parse(FAILURE_HEADER, TestReader(failure, parentId)) {
      producedEvents.add(it)
    }

    assertTrue(parsed)
    assertEquals(2, producedEvents.size)

    val eventsByMessage = producedEvents.groupBy { it.message }
    eventsByMessage["']' expected"].assertBuildEvent(FilePosition(anchor, 3, 35), parentId)
    eventsByMessage["';' expected"].assertBuildEvent(FilePosition(anchor, 4, 47), parentId)
  }

  private fun List<BuildEvent>?.assertBuildEvent(position: FilePosition, parentId: Any) {
    assertNotNull(this)
    assertEquals(1, this.size)

    val event = this.first() as FileMessageEvent
    assertEquals(parentId, event.parentId)
    assertNotNull(event.id)
    assertEquals(Kind.ERROR, event.kind)
    assertEquals("Compiler", event.group)
    assertEquals(position, event.filePosition)
    assertNotNull(event.eventTime)
  }

  private class TestReader(private val lines: List<String>, private val parentId: Any) : BuildOutputInstantReader {
    private var index = 0

    override fun getParentEventId(): Any = parentId

    override fun readLine(): String? = if (index >= lines.size) {
      null
    }
    else {
      lines[index++]
    }

    override fun pushBack() {
      if (index > 0) {
        index--
      }
    }

    override fun pushBack(numberOfLines: Int) {
      index = (index - numberOfLines).coerceAtLeast(0)
    }
  }
}
