// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor
import org.jetbrains.plugins.gradle.testFramework.annotations.CsvCrossProductSource
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream

class CsvCrossProductArgumentsProcessor : ArgumentsProcessor<CsvCrossProductSource> {
  private var value: List<String> = emptyList()
  private var separator: Char = '\u0000'
  private var delimiter: Char = '\u0000'

  override fun accept(annotation: CsvCrossProductSource) {
    value = annotation.value.toList()
    separator = annotation.separator
    delimiter = annotation.delimiter
  }

  override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
    val values = value.map { evaluateValues(it, separator, delimiter) }
    return crossProduct(values).stream()
      .map { Arguments.of(*it.flatten().toTypedArray()) }
  }

  companion object {
    private fun <T> crossProduct(lists: List<List<T>>): List<List<T>> {
      if (lists.isEmpty()) return emptyList()
      var result = lists.first().map { listOf(it) }
      for (list in lists.drop(1)) {
        result = result.flatMap { a ->
          list.map { b -> a + b }
        }
      }
      return result
    }

    private fun evaluateValues(expression: String, separator: Char, delimiter: Char): List<List<String>> {
      return StringUtil.splitHonorQuotes(expression, separator)
        .asSequence()
        .map { it.trim() }
        .map { evaluateValues(it, delimiter) }
        .toList()
    }

    private fun evaluateValues(expression: String, separator: Char): List<String> {
      return StringUtil.splitHonorQuotes(expression, separator)
        .asSequence()
        .map { it.trim() }
        .map { StringUtil.unquoteString(it) }
        .map { StringUtil.unescapeStringCharacters(it) }
        .toList()
    }
  }
}
