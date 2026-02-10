// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import org.jetbrains.kotlin.test.KtAssert.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal const val END = "$" + "END$"
internal const val TAB = "\t"
internal const val TEMPLATE_END = "$TAB$END"

internal fun variable(num: Int): String = $$"$V$$num$"

private fun assertTemplate(
  expectedVariableCounter: Int?,
  expectedTemplate: String,
  build: TemplateBuilder.Context.() -> String,
) {
  val result = TemplateBuilder.Context.buildTemplate(build)
  assertEquals(expectedTemplate.trimIndent(), result.text)
  expectedVariableCounter?.let { assertEquals(it, result.counter) }
}

internal fun assertItemTemplate(
  expectedTemplate: String,
  expectedVariableCounter: Int? = null,
  text: String,
  repetitions: Int,
  hasQuantity: Boolean,
) {
  assertTemplate(expectedVariableCounter, expectedTemplate) {
    makeItemTemplate(hasQuantity, text, repetitions)
  }
}

internal fun assertStringTemplate(
  expectedTemplate: String,
  expectedVariableCounter: Int? = null,
  name: String,
  text: String,
  repetitions: Int,
) {
  assertTemplate(expectedVariableCounter, expectedTemplate) {
    makeStringTemplate(name, text, repetitions)
  }
}

internal fun assertStringArrayTemplate(
  expectedTemplate: String,
  expectedVariableCounter: Int? = null,
  name: String,
  text: String,
  repetitions: Int,
  items: Int,
) {
  assertTemplate(expectedVariableCounter, expectedTemplate) {
    makeStringArrayTemplate(name, items, text, repetitions)
  }
}

internal fun assertPluralsTemplate(
  expectedTemplate: String,
  expectedVariableCounter: Int? = null,
  name: String,
  text: String,
  repetitions: Int,
  categoryMode: String,
  qualifier: String,
) {
  assertTemplate(expectedVariableCounter, expectedTemplate) {
    makePluralsTemplate(name, categoryMode, text, repetitions, qualifier)
  }
}

internal fun assertShortcutMatch(expectedNumber: String, expectedType: String, input: String) {
  val match = templateShortcutRegex.find(input)
  assertNotNull(match, "Should match '$input'")
  assertEquals(expectedNumber, match.groupValues[1])
  assertEquals(expectedType, match.groupValues[2].ifEmpty { "s" })
}


internal fun TemplateType.assertMatchesKeys(vararg keys: String) = keys.forEach { key ->
  assertTrue("'$key' should be matched by ${this::class.simpleName}", matchesKey(key))
}

