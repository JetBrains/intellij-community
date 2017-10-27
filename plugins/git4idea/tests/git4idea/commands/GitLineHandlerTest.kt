// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import git4idea.test.GitSingleRepoTest

class GitLineHandlerTest : GitSingleRepoTest() {

  fun `test lines split with LF`() {
    `check handler with text`(listOf("line one\nline two\nline ", "three\nline four\nthe last one"),
                              listOf("line one", "line two", "line three", "line four", "the last one"))
  }

  fun `test lines split with CRLF`() {
    `check handler with text`(listOf("Do not go gentle", " into that good night,\r\nOld age should burn and rave at close of day;",
                                     "\r\nRage, rage ", "against the ", "dying of the ", "light."),
                              listOf("Do not go gentle into that good night,",
                                     "Old age should burn and rave at close of day;",
                                     "Rage, rage against the dying of the light."))
  }

  fun `test lines split with a mix`() {
    `check handler with text`(listOf("A climber climbs", " with his guts, his brain, his so", "ul, and his feet\nAll",
                                     " of the", "m bound for a cold and white world\n\rA world that is all ",
                                     "up and up\n\rUp and up"),
                              listOf("A climber climbs with his guts, his brain, his soul, and his feet",
                                     "All of them bound for a cold and white world",
                                     "A world that is all up and up",
                                     "Up and up"))
  }

  fun `test with a simple lines`() {
    `check handler with text`(listOf("To be, or not to be, that is the question:\nWhether 'tis nobler in the mind to suffer\n" +
                                     "The slings and arrows of outrageous fortune,\n" +
                                     "Or to take arms against a sea of troubles\nAnd by opposing end them."),
                              listOf("To be, or not to be, that is the question:",
                                     "Whether 'tis nobler in the mind to suffer",
                                     "The slings and arrows of outrageous fortune,",
                                     "Or to take arms against a sea of troubles",
                                     "And by opposing end them."))
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