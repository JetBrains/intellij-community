// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl


import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.openapi.externalSystem.util.runWriteAction
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.intellij.util.lang.CompoundRuntimeException.throwIfNotEmpty
import org.jetbrains.plugins.gradle.testFramework.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.util.ResolveTest


abstract class GradleHighlightingLightTestCase : GradleLightTestCase(), ResolveTest {

  private lateinit var fixture: JavaCodeInsightTestFixture

  override fun getFixture(): JavaCodeInsightTestFixture = fixture

  open fun getParentCalls(): List<String> {
    return listOf(
      "project(':')",
      "allprojects",
      "subprojects",
      "configure(project(':'))"
    )
  }

  override fun setUp() {
    super.setUp()

    fixture = JavaTestFixtureFactory.getFixtureFactory()
      // CodeInsightTestFixtureImpl calls setup and teardown for project fixture, but these are run in GradleLightTestCase
      .createCodeInsightFixture(object : IdeaProjectTestFixture by projectFixture {
        override fun setUp() {}
        override fun tearDown() {}
      })

    (fixture as JavaCodeInsightTestFixtureImpl).setVirtualFileFilter(null)
    fixture.setUp()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { fixture.tearDown() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  fun doTestHighlighting(text: String) = doTestHighlighting("build.gradle", text)
  fun doTestHighlighting(relativePath: String, text: String) {
    val file = findOrCreateFile(relativePath, text)
    runInEdtAndWait {
      fixture.testHighlighting(true, false, true, file)
    }
  }

  fun doTest(text: String, test: () -> Unit) = doTest(listOf(text), test)
  fun doTest(texts: List<String>, test: () -> Unit) = doTest(texts, getParentCalls(), test)
  fun doTest(text: String, calls: List<String>, test: () -> Unit) = doTest(listOf(text), calls, test)
  fun doTest(texts: List<String>, calls: List<String>, test: () -> Unit) {
    val errors = ArrayList<Throwable>()
    for (text in texts) {
      val pattens = listOf(text) + calls.map { "$it { $text }" }
      for (pattern in pattens) {
        updateProjectFile(pattern)
        try {
          runReadAction {
            test()
          }
        }
        catch (e: Throwable) {
          errors.add(Exception(pattern, e))
        }
      }
    }
    throwIfNotEmpty(errors)
  }

  fun updateProjectFile(text: String) = updateProjectFile("build.gradle", text)
  fun updateProjectFile(relativePath: String, text: String) {
    val file = findOrCreateFile(relativePath, text)
    runWriteAction {
      fixture.configureFromExistingVirtualFile(file)
    }
  }

  fun setterMethodTest(name: String, originalName: String, containingClass: String) {
    val result = elementUnderCaret(GrMethodCall::class.java).advancedResolve()
    val method = assertInstanceOf(result.element, PsiMethod::class.java)
    methodTest(method, name, containingClass)
    val original = assertInstanceOf(method.navigationElement, PsiMethod::class.java)
    methodTest(original, originalName, containingClass)
  }

  fun getDistributionBaseNameMethod(): String {
    return when {
      isGradleAtLeast("7.0") -> "getDistributionBaseName()"
      else -> "getBaseName()"
    }
  }

  fun getDistributionContainerFqn(): String {
    return when {
      isGradleAtLeast("3.5") -> "org.gradle.api.NamedDomainObjectContainer<org.gradle.api.distribution.Distribution>"
      else -> "org.gradle.api.distribution.internal.DefaultDistributionContainer"
    }
  }

  fun getExtraPropertiesExtensionFqn(): String {
    return when {
      isGradleOlderThan("5.2") -> "org.gradle.api.internal.plugins.DefaultExtraPropertiesExtension"
      else -> "org.gradle.internal.extensibility.DefaultExtraPropertiesExtension"
    }
  }

  fun getPublishingExtensionFqn(): String {
    return when {
      isGradleOlderThan("4.8") -> "org.gradle.api.publish.internal.DefaultPublishingExtension"
      isGradleAtLeast("5.0") -> "org.gradle.api.publish.internal.DefaultPublishingExtension"
      else -> "org.gradle.api.publish.internal.DeferredConfigurablePublishingExtension"
    }
  }
}
