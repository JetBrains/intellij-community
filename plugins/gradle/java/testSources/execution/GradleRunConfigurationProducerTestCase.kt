// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.PatternGradleConfigurationProducer
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.runReadActionAndWait

abstract class GradleRunConfigurationProducerTestCase : GradleImportingTestCase() {

  protected inline fun <reified P : GradleRunConfigurationProducer> verifyRunConfigurationProducer(
    expectedSettings: String,
    vararg elements: PsiElement,
    noinline configureProducer: P.() -> Unit = {},
  ) = verifyRunConfigurationProducer(expectedSettings, { getContextByLocation(*elements) }, configureProducer)

  protected inline fun <reified P : GradleRunConfigurationProducer> verifyRunConfigurationProducer(
    expectedSettings: String,
    noinline context: () -> ConfigurationContext,
    noinline configureProducer: P.() -> Unit = {}
  ) = runReadActionAndWait {
    val resContext = context()
    val configurationFromContext = getConfigurationFromContext(resContext)
    val producer = configurationFromContext.configurationProducer as P
    producer.configureProducer()
    val configuration = configurationFromContext.configuration as GradleRunConfiguration
    if (producer is GradleTestRunConfigurationProducer) {
      assertTrue("Configuration created from context must force test re-execution", configuration.isRunAsTest)
    }
    assertTrue("Configuration can be setup by producer from his context",
               producer.setupConfigurationFromContext(configuration, resContext, Ref(resContext.psiLocation)))
    if (producer !is PatternGradleConfigurationProducer) {
      assertTrue("Producer have to identify configuration that was created by him",
                 producer.isConfigurationFromContext(configuration, resContext))
    }
    producer.onFirstRun(configurationFromContext, resContext, Runnable {})
    assertEquals(expectedSettings, configuration.settings.toString())
  }

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