// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import git4idea.test.GitSingleRepoTest

class GitLineHandlerTest : GitSingleRepoTest() {

  fun `test lines split with LF`() {
    `check handler with text`(listOf("some lo", "ng line one\nso", "me lo", "ng line two", "\nsome long line three\n", "some lo", "n", "", "g line fou", "r\nthe last long line"),
        listOf("some long line one", "some long line two", "some long line three", "some long line four", "the last long line"))
  }

  fun `test lines split with CRLF`() {
    `check handler with text`(listOf("some", " long ", "line one\r\nsome long", " line two", "\r\n", "some long line thr", "ee\r\n", "some", " long li", "ne four\r\nth", "e last l", "ong line"),
        listOf("some long line one", "some long line two", "some long line three", "some long line four", "the last long line"))
  }

  fun `test lines split with a mix`() {
    `check handler with text`(listOf("some", " long line one\r\nso", "me long line two\n\r", "some long line three", "\n", "some long l", "ine four\r\nthe last ", "long line"),
        listOf("some long line one", "some long line two", "some long line three", "some long line four", "the last long line"))
  }

  fun `test with a simple lines`() {
    `check handler with text`(listOf("some long line one\n", "some long line two\nsome long line three\n", "some long line four\nthe last long line"),
        listOf("some long line one", "some long line two", "some long line three", "some long line four", "the last long line"))
  }

  private fun `check handler with text`(text: List<String>, expectedLines: List<String>) {
    val lineCollector = LineCollector()

    val handler = GitLineHandler(project, myProjectRoot, GitCommand.LOG)
    handler.addLineListener(lineCollector)

    for (line in text) {
      handler.onTextAvailable(line, ProcessOutputTypes.STDOUT)
    }
    handler.processTerminated(0)

    assertOrderedEquals(lineCollector.outputLines, expectedLines)
    assertOrderedEquals(lineCollector.errorLines, listOf())
  }
}

private class LineCollector(val outputLines: MutableList<String> = ContainerUtil.newArrayList(),
                            val errorLines: MutableList<String> = ContainerUtil.newArrayList()) : GitLineHandlerAdapter() {
  override fun onLineAvailable(line: String?, outputType: Key<*>?) {
    when (outputType) {
      ProcessOutputTypes.STDOUT -> outputLines.add(line!!)
      ProcessOutputTypes.STDERR -> errorLines.add(line!!)
    }
  }
}