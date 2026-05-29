// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor
import org.jetbrains.plugins.gradle.testFramework.annotations.CsvCrossProductSource
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream
import kotlin.streams.asStream

class CsvCrossProductArgumentsProcessor : ArgumentsProcessor<CsvCrossProductSource> {
  private var value: List<String> = emptyList()
  private var separator: Char = '\u0000'
  private var delimiter: Char = '\u0000'

  override fun accept(annotation: CsvCrossProductSource) {
    value = annotation.value.toList()
    separator = annotation.separator
    delimiter = annotation.delimiter
  }

  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
    val values = value.map { evaluateValues(it, separator, delimiter) }
    return crossProductArguments(values)
  }

  companion object {
    internal fun crossProduct(
      firstValues: List<Any>,
      additionalValues: List<String>,
      separator: Char,
      delimiter: Char,
    ): Stream<out Arguments> {
      val parsedAdditionalValues = additionalValues.map { evaluateValues(it, separator, delimiter) }
      val allValues = listOf(firstValues) + parsedAdditionalValues
      return crossProductArguments(allValues)
    }

    internal fun crossProductArguments(values: List<List<*>>): Stream<out Arguments> {
      return crossProduct(values).asSequence()
        .map { args -> Arguments.of(*args.flatMap { it as? List<*> ?: listOf(it) }.toTypedArray()) }
        .asStream()
    }

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
