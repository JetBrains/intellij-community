// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.build.output.BuildOutputMulticaster
import com.intellij.build.output.BuildOutputReplayableLineReader
import com.intellij.build.output.BuildOutputText
import com.intellij.build.output.JavacOutputParser

class GradleCompilationReportParser : BuildOutputLineParserBridge() {

  private companion object {
    private const val GROUPED_COMPILATION_ERROR = "> Compilation failed; see the compiler output below."
    private const val ERROR_CURSOR = "^"
    private const val MESSAGE_GROUP_TERMINATOR = "* Try:"
    private val ERROR_GROUP_TERMINATOR = "(\\d+ error(s)?)".toRegex()
  }

  override suspend fun parse(
    line: BuildOutputText,
    reader: BuildOutputReplayableLineReader,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
    isSingleFlow: Boolean,
  ): Boolean {
    if (GROUPED_COMPILATION_ERROR != line.text) {
      return false
    }
    val parser by lazy { JavacOutputParser() }
    var parsed = false
    drainCompilationErrors(reader) { subLine, boundedReader ->
      if (parser.parse(subLine, boundedReader, taskId, multicaster, isSingleFlow)) {
        parsed = true
      }
    }
    return parsed
  }

  private suspend fun drainCompilationErrors(
    reader: BuildOutputReplayableLineReader,
    errorConsumer: suspend (BuildOutputText, BuildOutputReplayableLineReader) -> Unit,
  ) {
    val buffer = mutableListOf<BuildOutputText>()
    do {
      val line = reader.readLine()
      if (line == null) {
        errorConsumer(buffer.first(), ArrayBackedReader(buffer))
        return
      }
      val text = line.text
      when {
        text == MESSAGE_GROUP_TERMINATOR || ERROR_GROUP_TERMINATOR.matches(text.trim()) -> {
          return
        }
        text.trim() == ERROR_CURSOR -> {
          buffer.add(line)
          errorConsumer(buffer.first(), ArrayBackedReader(buffer))
          buffer.clear()
        }
        else -> buffer.add(line)
      }
    }
    while (true)
  }

  private class ArrayBackedReader(private val source: List<BuildOutputText>) : BuildOutputReplayableLineReader {

    private var index = 0

    override suspend fun readLine(): BuildOutputText? {
      if (index + 1 < source.size) {
        index++
        return source[index]
      }
      return null
    }

    override fun pushBack(numberOfLines: Int) {
      index -= numberOfLines
    }
  }
}