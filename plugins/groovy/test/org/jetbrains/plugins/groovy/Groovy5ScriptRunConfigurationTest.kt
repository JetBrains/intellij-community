// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.util.ScriptFileUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration
import org.jetbrains.plugins.groovy.runner.ScriptRunConfigurationProducer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
internal class Groovy5ScriptRunConfigurationTest {
  companion object {
    private val DESCRIPTOR = object : LibraryLightProjectDescriptor(GroovyProjectDescriptors.LIB_GROOVY_5_0) {

      override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        val version = "5.13.0"
        MavenDependencyUtil.addFromMaven(model, "org.junit.jupiter:junit-jupiter-api:$version")
        MavenDependencyUtil.addFromMaven(model, "org.junit.jupiter:junit-jupiter-params:$version")
      }
    }

    private val projectFixture = lightJavaProjectFixture(DESCRIPTOR)
  }

  private val myFixture by lightGroovyFixture(projectFixture)

  @Test
  fun testNoScriptRunConfigurationInTest() {
    val file = myFixture.configureByText("CalculatorTest.groovy", """
      package com.example

      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions

      class CalculatorTest {
          @Test
          void testAdd() {
              Assertions.assertEquals(2+2, 4)
          }
      }
    """.trimIndent())

    runReadActionBlocking {
      val producer = ScriptRunConfigurationProducer()
      val configuration = GroovyScriptRunConfiguration("Calculator test", myFixture.project, producer.configurationFactory).apply {
        scriptPath = ScriptFileUtil.getScriptFilePath(file.virtualFile)
      }
      val context = ConfigurationContext(file)
      assertFalse(producer.isConfigurationFromContext(configuration, context))
    }
  }
}
