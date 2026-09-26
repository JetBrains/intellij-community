// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.events.BuildEvent
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.JavacOutputParser
import com.intellij.openapi.util.NlsSafe
import java.util.function.Consumer

class GradleCompilationReportParser : BuildOutputParser {

  private companion object {
    private const val GROUPED_COMPILATION_ERROR = "> Compilation failed; see the compiler output below."
    private const val ERROR_CURSOR = "^"
    private const val MESSAGE_GROUP_TERMINATOR = "* Try:"
    private val ERROR_GROUP_TERMINATOR = "(\\d+ error(s)?)".toRegex()
  }

  override fun parse(line: @NlsSafe String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (GROUPED_COMPILATION_ERROR != line) {
      return false
    }
    val parser by lazy { JavacOutputParser() }
    var parsed = false
    drainCompilationErrors(reader) { error ->
      parsed = true
      if (error.isNotEmpty()) {
        parser.parse(error.first(), ErrorHolder(error, reader.parentEventId)) { event ->
          messageConsumer.accept(event)
        }
      }
    }
    return parsed
  }

  private fun drainCompilationErrors(reader: BuildOutputInstantReader, errorConsumer: Consumer<List<String>>) {
    val buffer = mutableListOf<String>()
    do {
      val line = reader.readLine()
      when {
        line == null || line == MESSAGE_GROUP_TERMINATOR || ERROR_GROUP_TERMINATOR.matches(line.trim()) -> {
          errorConsumer.accept(buffer)
          return
        }
        line.trim() == ERROR_CURSOR -> {
          buffer.add(line)
          errorConsumer.accept(buffer)
          buffer.clear()
        }
        else -> buffer.add(line)
      }
    }
    while (true)
  }

  private class ErrorHolder(val errors: List<String>, val parentId: Any) : BuildOutputInstantReader {
    private var index = 0
    override fun getParentEventId(): Any {
      return parentId
    }

    override fun readLine(): @NlsSafe String? {
      if (index + 1 < errors.size) {
        index++
        return errors[index]
      }
      return null
    }

    override fun pushBack() {
      index--
    }

    override fun pushBack(numberOfLines: Int) {
      index = index - numberOfLines
    }
  }
}