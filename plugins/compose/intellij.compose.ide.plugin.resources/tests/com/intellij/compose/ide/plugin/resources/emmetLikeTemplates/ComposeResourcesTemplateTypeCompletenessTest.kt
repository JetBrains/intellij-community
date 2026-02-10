// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ComposeResourcesTemplateTypeCompletenessTest {

  @Test
  fun `test ALL_TYPES contains all sealed subclasses of TemplateType`() {
    val registeredTypeClasses = ALL_TEMPLATE_TYPE_TYPES.map { it::class }.toSet()

    @Suppress("NO_REFLECTION_IN_CLASS_PATH")
    val sealedSubclasses = TemplateType::class.sealedSubclasses.toSet()

    val missingFromRegistered = sealedSubclasses.filter { it !in registeredTypeClasses }
    val extrasInRegistered = registeredTypeClasses.filter { it !in sealedSubclasses }

    if (missingFromRegistered.isNotEmpty()) {
      fail(
        "ALL_TYPES is missing sealed subclasses: ${missingFromRegistered.map { it.simpleName }}. " +
        "Please add them to ALL_TYPES in TemplateType.Companion."
      )
    }
    if (extrasInRegistered.isNotEmpty()) {
      fail(
        "ALL_TYPES contains extra elements: ${extrasInRegistered.map { it.simpleName }}. " +
        "Please remove them from ALL_TYPES in TemplateType.Companion."
      )
    }
  }

  @Test
  fun `test StringTemplateType is a fallback so it should be the last element`() {
    assertTrue("ALL_TYPES should not be empty", ALL_TEMPLATE_TYPE_TYPES.isNotEmpty())
    val lastType = ALL_TEMPLATE_TYPE_TYPES.last()
    assertTrue(
      "StringTemplateType must be the last element in ALL_TYPES as it is a catch-all fallback",
      lastType is StringTemplateType
    )
  }

}