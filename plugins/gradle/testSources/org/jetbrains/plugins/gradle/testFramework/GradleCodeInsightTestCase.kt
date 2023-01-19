// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.psi.PsiElement
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.plugins.groovy.util.ExpressionTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

abstract class GradleCodeInsightTestCase : GradleCodeInsightBaseTestCase(), ExpressionTest {

  fun testBuildscript(decorator: String, expression: String, test: () -> Unit) {
    if (decorator.isEmpty()) {
      testBuildscript(expression, test)
    }
    else {
      testBuildscript("$decorator { $expression }", test)
    }
  }

  fun testBuildscript(expression: String, test: () -> Unit) {
    checkCaret(expression)
    updateProjectFile(expression)
    runReadAction {
      test()
    }
  }

  protected fun checkCaret(expression: String) {
    assertTrue("<caret>" in expression, "Please define caret position in build script.")
  }

  fun testIntention(before: String, after: String, intentionPrefix: String) {
    testIntention("build.gradle", before, after, intentionPrefix)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun testIntention(fileName: String, before: String, after: String, intentionPrefix: String) {
    checkCaret(before)
    val file = findOrCreateFile(fileName, before)
    runInEdtAndWait {
      codeInsightFixture.configureFromExistingVirtualFile(file)
      val intention = codeInsightFixture.filterAvailableIntentions(intentionPrefix).single()
      codeInsightFixture.launchAction(intention)
      codeInsightFixture.checkResult(after)
      gradleFixture.fileFixture.rollback(fileName)
    }
  }

  fun testHighlighting(expression: String) = testHighlighting("build.gradle", expression)
  fun testHighlighting(relativePath: String, expression: String) {
    val file = findOrCreateFile(relativePath, expression)
    runInEdtAndWait {
      codeInsightFixture.testHighlighting(true, false, true, file)
    }
  }

  fun testCompletion(fileName: String, expression: String, checker: (Array<LookupElement>) -> Unit) {
    checkCaret(expression)
    val file = findOrCreateFile(fileName, expression)
    runInEdtAndWait {
      codeInsightFixture.configureFromExistingVirtualFile(file)
      checker(codeInsightFixture.completeBasic())
    }
  }

  fun testCompletion(expression: String, vararg completionCandidates: String, orderDependent: Boolean = true) = testCompletion("build.gradle", expression) {
    val lookup = listOf(*it)
    var startIndex = 0
    for (candidate in completionCandidates) {
      val fromIndex = if (orderDependent) startIndex else 0
      val newIndex = lookup.subList(fromIndex, lookup.size).indexOfFirst { it.lookupString == candidate }
      assertTrue(newIndex != -1, "Element '$candidate' must be in the lookup")
      startIndex = newIndex + 1
    }
  }

  fun testGotoDefinition(expression: String, checker: (PsiElement) -> Unit) {
    checkCaret(expression)
    val file = findOrCreateFile("build.gradle", expression)
    runInEdtAndWait {
      codeInsightFixture.configureFromExistingVirtualFile(file)
      val elementAtCaret = codeInsightFixture.elementAtCaret
      assertNotNull(elementAtCaret)
      val elem = GotoDeclarationAction.findTargetElement(project, codeInsightFixture.editor, codeInsightFixture.caretOffset)
      checker(elem!!)
    }
  }

  fun updateProjectFile(content: String) {
    val file = findOrCreateFile("build.gradle", content)
    runWriteActionAndWait {
      codeInsightFixture.configureFromExistingVirtualFile(file)
    }
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

  companion object {
    const val DECORATORS = """
      "",
      project(':'), 
      allprojects, 
      subprojects, 
      configure(project(':'))
    """
  }
}
