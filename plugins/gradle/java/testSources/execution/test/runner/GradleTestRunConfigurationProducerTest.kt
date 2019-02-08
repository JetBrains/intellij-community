// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.Test

class GradleTestRunConfigurationProducerTest : GradleTestRunConfigurationProducerTestCase() {

  @Test
  fun `test simple configuration`() {
    val projectData = generateAndImportTemplateProject()
    runReadActionAndWait {
      val context = getContextByLocation(projectData["project"]["TestCase"]["test1"].element)
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as TestMethodGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      assertTrue(producer.isConfigurationFromContext(configuration, context))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(configuration, """:cleanTest :test --tests "TestCase.test1"""")
    }
    runReadActionAndWait {
      val context = getContextByLocation(projectData["project"]["TestCase"].element)
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as TestClassGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      assertTrue(producer.isConfigurationFromContext(configuration, context))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(configuration, """:cleanTest :test --tests "TestCase"""")
    }
  }

  @Test
  fun `test package configuration`() {
    val projectData = generateAndImportTemplateProject()
    runReadActionAndWait {
      val context = getContextByLocation(projectData["project"]["pkg.TestCase"].element.containingFile.containingDirectory)
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as AllInPackageGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      assertTrue(producer.isConfigurationFromContext(configuration, context))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(configuration, """:cleanTest :test --tests "pkg.*"""")
    }
  }

  @Test
  fun `test pattern configuration`() {
    val projectData = generateAndImportTemplateProject()
    runReadActionAndWait {
      val context = getContextByLocation(
        projectData["project"]["TestCase"]["test1"].element,
        projectData["project"]["pkg.TestCase"]["test1"].element,
        projectData["module"]["ModuleTestCase"]["test1"].element
      )
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as PatternGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(
        configuration,
        """:cleanTest :test --tests "TestCase.test1" --tests "pkg.TestCase.test1" """ +
        """:module:cleanTest :module:test --tests "ModuleTestCase.test1" --continue"""
      )
    }
    runReadActionAndWait {
      val context = getContextByLocation(
        projectData["project"]["TestCase"].element,
        projectData["project"]["pkg.TestCase"].element,
        projectData["module"]["ModuleTestCase"].element
      )
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as PatternGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(
        configuration,
        """:cleanTest :test --tests "TestCase" --tests "pkg.TestCase" """ +
        """:module:cleanTest :module:test --tests "ModuleTestCase" --continue"""
      )
    }
    runReadActionAndWait {
      val context = getContextByLocation(
        projectData["project"]["TestCase"]["test1"].element,
        projectData["project"]["pkg.TestCase"]["test1"].element,
        projectData["module"]["ModuleTestCase"].element
      )
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as PatternGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(
        configuration,
        """:cleanTest :test --tests "TestCase.test1" --tests "pkg.TestCase.test1" """ +
        """:module:cleanTest :module:test --tests "ModuleTestCase" --continue"""
      )
    }
  }

  @Test
  fun `test configuration from context`() {
    val projectData = generateAndImportTemplateProject()
    runReadActionAndWait {
      val context1 = getContextByLocation(projectData["project"]["TestCase"]["test1"].element)
      val context2 = getContextByLocation(projectData["project"]["TestCase"].element)
      val context3 = getContextByLocation(projectData["project"]["pkg.TestCase"].element.containingFile.containingDirectory)
      val context4 = getContextByLocation(projectData["project"]["TestCase"]["test2"].element)
      val context5 = getContextByLocation(projectData["project"]["TestCase"]["test1"].element)
      val context6 = getContextByLocation(projectData["project"]["TestCase"].element)
      val context7 = getContextByLocation(projectData["project"]["pkg.TestCase"].element.containingFile.containingDirectory)
      val context8 = getContextByLocation(projectData["project"]["TestCase"]["test2"].element)
      val configurationFromContext1 = getConfigurationFromContext(context1)
      val configurationFromContext2 = getConfigurationFromContext(context2)
      val configurationFromContext3 = getConfigurationFromContext(context3)
      val configurationFromContext4 = getConfigurationFromContext(context4)
      val configurationFromContext5 = getConfigurationFromContext(context5)
      val configurationFromContext6 = getConfigurationFromContext(context6)
      val configurationFromContext7 = getConfigurationFromContext(context7)
      val configurationFromContext8 = getConfigurationFromContext(context8)
      val configuration1 = configurationFromContext1.configuration as ExternalSystemRunConfiguration
      val configuration2 = configurationFromContext2.configuration as ExternalSystemRunConfiguration
      val configuration3 = configurationFromContext3.configuration as ExternalSystemRunConfiguration
      val configuration4 = configurationFromContext4.configuration as ExternalSystemRunConfiguration
      val configuration5 = configurationFromContext5.configuration as ExternalSystemRunConfiguration
      val configuration6 = configurationFromContext6.configuration as ExternalSystemRunConfiguration
      val configuration7 = configurationFromContext7.configuration as ExternalSystemRunConfiguration
      val configuration8 = configurationFromContext8.configuration as ExternalSystemRunConfiguration
      val producer1 = configurationFromContext1.configurationProducer
      val producer2 = configurationFromContext2.configurationProducer
      val producer3 = configurationFromContext3.configurationProducer
      val producer4 = configurationFromContext4.configurationProducer
      assertTrue(producer1.isConfigurationFromContext(configuration1, context1))
      assertFalse(producer1.isConfigurationFromContext(configuration1, context2))
      assertFalse(producer1.isConfigurationFromContext(configuration1, context3))
      assertFalse(producer1.isConfigurationFromContext(configuration1, context4))
      assertFalse(producer2.isConfigurationFromContext(configuration2, context1))
      assertTrue(producer2.isConfigurationFromContext(configuration2, context2))
      assertFalse(producer2.isConfigurationFromContext(configuration2, context3))
      assertFalse(producer2.isConfigurationFromContext(configuration2, context4))
      assertFalse(producer3.isConfigurationFromContext(configuration3, context1))
      assertFalse(producer3.isConfigurationFromContext(configuration3, context2))
      assertTrue(producer3.isConfigurationFromContext(configuration3, context3))
      assertFalse(producer3.isConfigurationFromContext(configuration3, context4))
      assertFalse(producer4.isConfigurationFromContext(configuration4, context1))
      assertFalse(producer4.isConfigurationFromContext(configuration4, context2))
      assertFalse(producer4.isConfigurationFromContext(configuration4, context3))
      assertTrue(producer4.isConfigurationFromContext(configuration4, context4))
      assertTrue(producer1.isConfigurationFromContext(configuration5, context5))
      assertTrue(producer2.isConfigurationFromContext(configuration6, context6))
      assertTrue(producer3.isConfigurationFromContext(configuration7, context7))
      assertTrue(producer4.isConfigurationFromContext(configuration8, context8))
      assertTrue(producer1.isConfigurationFromContext(configuration1, context5))
      assertTrue(producer2.isConfigurationFromContext(configuration2, context6))
      assertTrue(producer3.isConfigurationFromContext(configuration3, context7))
      assertTrue(producer4.isConfigurationFromContext(configuration4, context8))
      assertTrue(producer1.isConfigurationFromContext(configuration5, context1))
      assertTrue(producer2.isConfigurationFromContext(configuration6, context2))
      assertTrue(producer3.isConfigurationFromContext(configuration7, context3))
      assertTrue(producer4.isConfigurationFromContext(configuration8, context4))
      val context = getContextByLocation(
        projectData["project"]["TestCase"]["test1"].element,
        projectData["project"]["pkg.TestCase"]["test1"].element,
        projectData["module"]["ModuleTestCase"]["test1"].element
      )
      assertFalse(producer1.isConfigurationFromContext(configuration1, context))
      assertFalse(producer2.isConfigurationFromContext(configuration2, context))
      assertFalse(producer3.isConfigurationFromContext(configuration3, context))
      assertFalse(producer4.isConfigurationFromContext(configuration4, context))
    }
  }

  @Test
  fun `test configuration escaping`() {
    val projectData = generateAndImportTemplateProject()
    runReadActionAndWait {
      val context = getContextByLocation(projectData["my module"]["MyModuleTestCase"]["test1"].element)
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as TestMethodGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      assertTrue(producer.isConfigurationFromContext(configuration, context))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(configuration, """':my module:cleanTest' ':my module:test' --tests "MyModuleTestCase.test1"""")
    }
    runReadActionAndWait {
      val context = getContextByLocation(projectData["my module"]["MyModuleTestCase"].element)
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as TestClassGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      assertTrue(producer.isConfigurationFromContext(configuration, context))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(configuration, """':my module:cleanTest' ':my module:test' --tests "MyModuleTestCase"""")
    }
    runReadActionAndWait {
      val context = getContextByLocation(
        projectData["my module"]["MyModuleTestCase"]["test1"].element,
        projectData["my module"]["MyModuleTestCase"]["test2"].element
      )
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as PatternGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(
        configuration, """':my module:cleanTest' ':my module:test' --tests "MyModuleTestCase.test1" --tests "MyModuleTestCase.test2"""")
    }
    runReadActionAndWait {
      val context = getContextByLocation(projectData["project"]["GroovyTestCase"]["""Don\'t use single . quo\"tes"""].element)
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as TestMethodGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      assertTrue(producer.isConfigurationFromContext(configuration, context))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(configuration, """:cleanTest :test --tests "GroovyTestCase.Don\'t use single * quo\*tes"""")
    }
    runReadActionAndWait {
      val context = getContextByLocation(
        projectData["project"]["GroovyTestCase"]["""Don\'t use single . quo\"tes"""].element,
        projectData["project"]["GroovyTestCase"]["test2"].element
      )
      val configurationFromContext = getConfigurationFromContext(context)
      val producer = configurationFromContext.configurationProducer as PatternGradleConfigurationProducer
      val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
      assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
      producer.onFirstRun(configurationFromContext, context, Runnable {})
      assertEqualsConfigurationSettings(
        configuration, """:cleanTest :test --tests "GroovyTestCase.Don\'t use single * quo\*tes" --tests "GroovyTestCase.test2"""")
    }
  }
}
