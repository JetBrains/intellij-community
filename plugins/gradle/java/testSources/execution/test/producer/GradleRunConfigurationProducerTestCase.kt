// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.producer

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase

abstract class GradleRunConfigurationProducerTestCase : GradleImportingTestCase() {
  fun getContextByLocation(vararg elements: PsiElement): ConfigurationContext {
    assertTrue(elements.isNotEmpty())
    return object : ConfigurationContext(elements[0]) {
      override fun getDataContext() = SimpleDataContext.getSimpleContext(LangDataKeys.PSI_ELEMENT_ARRAY, elements, super.getDataContext())
      override fun containsMultipleSelection() = elements.size > 1
    }
  }

  fun getConfigurationFromContext(context: ConfigurationContext): ConfigurationFromContextImpl {
    val fromContexts = context.configurationsFromContext
    val fromContext = fromContexts?.firstOrNull()
    assertNotNull("Gradle configuration from context not found", fromContext)
    return fromContext as ConfigurationFromContextImpl
  }
}