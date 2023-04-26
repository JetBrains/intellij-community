// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.groovy.ext.spock.isSpockSpecification

/**
 * Repairs and converts test event data.
 *
 * These mechanics are very heuristic, and they should be upgraded
 * for test events in future Gradle versions.
 *
 * For example, in old Gradle (<7.0) parametrized tests have display name in [methodName].
 * Or in Spock test we have test parameters instead of method name in [methodName].
 * Junit5 and Junit4 have different ways to define method, class and display names.
 *
 * Everything should be converted to IDEA understandable pattern.
 * For example, IDEA test infrastructure doesn't support argument signature in [methodName].
 *
 * Etc.
 */
@ApiStatus.Internal
internal class GradleTestEventConverter(
  private val project: Project,
  private val parent: SMTestProxy,
  private val isSuite: Boolean,
  private val suiteName: String,
  private val className: String,
  private val methodName: String?,
  private val displayName: String
) {

  private val isTestSuite: Boolean by lazy {
    isSuite && className.isEmpty()
  }

  private val isTestClass: Boolean by lazy {
    isSuite && className.isNotEmpty()
  }

  private val isTestMethod: Boolean by lazy {
    !isSuite && StringUtil.isNotEmpty(className)
  }

  private val isJunit5ParametrizedTestSuite: Boolean by lazy {
    isTestSuite && extractName(suiteName, JUNIT5_PARAMETRIZED_SUITE_NAME_EXTRACTOR) != null
  }

  private val isJunit5ParametrizedTestMethod: Boolean by lazy {
    isTestMethod && extractName(methodName, JUNIT5_PARAMETRIZED_METHOD_DISPLAY_NAME_EXTRACTOR) != null
  }

  private val isOldJunit5ParametrizedTestMethod: Boolean by lazy {
    isJunit5ParametrizedTestMethod && parentMethodName == null
  }

  private val isSpockTestMethod: Boolean by lazy {
    isTestMethod && runReadAction {
      DumbService.getInstance(project).computeWithAlternativeResolveEnabled<Boolean, Throwable> {
        val scope = GlobalSearchScope.allScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)
        val psiClass = psiFacade.findClass(convertedClassName, scope)
        psiClass != null && psiClass.isSpockSpecification()
      }
    }
  }

  private val parentClassName: String? by lazy {
    val locationUrl = parent.locationUrl ?: return@lazy null
    val locationPath = URLUtil.extractPath(locationUrl)
    StringUtil.substringBefore(locationPath, "/") ?: locationPath
  }

  private val parentMethodName: String? by lazy {
    val locationUrl = parent.locationUrl ?: return@lazy null
    val locationPath = URLUtil.extractPath(locationUrl)
    StringUtil.substringAfter(locationPath, "/")
  }

  val convertedClassName: String by lazy {
    when {
      isTestSuite ->
        parentClassName ?: className
      else ->
        className
    }
  }

  val convertedMethodName: String? by lazy {
    when {
      isJunit5ParametrizedTestSuite ->
        extractName(suiteName, JUNIT5_METHOD_NAME_EXTRACTOR)?.trim()
        ?: suiteName
      isTestSuite ->
        // Incorrect if parametrized test annotated by display name.
        // We have information in child nodes, but we haven't accessed to them.
        extractName(suiteName, JUNIT5_METHOD_NAME_EXTRACTOR)?.trim()
        ?: suiteName
      isSpockTestMethod ->
        parentMethodName
        ?: methodName
      isTestMethod ->
        extractName(methodName, JUNIT5_PARAMETRIZED_METHOD_NAME_EXTRACTOR)
        ?: extractName(methodName, JUNIT5_METHOD_NAME_EXTRACTOR)
        ?: methodName
      else ->
        methodName
    }
  }

  val convertedDisplayName: String by lazy {
    when {
      isTestSuite ->
        extractName(displayName, JUNIT5_TEST_LAUNCHER_SUITE_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, JUNIT4_TEST_LAUNCHER_SUITE_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, JUNIT5_PARAMETRIZED_SUITE_DISPLAY_NAME_EXTRACTOR)
        ?: displayName
      isTestClass ->
        extractName(displayName, JUNIT4_CLASS_DISPLAY_NAME_EXTRACTOR)
        ?: displayName
      isOldJunit5ParametrizedTestMethod -> {
        val displayMethodName =
          extractName(convertedMethodName, JUNIT5_METHOD_DISPLAY_NAME_EXTRACTOR)
          ?: convertedMethodName
        "$displayMethodName $displayName"
      }
      isTestMethod ->
        extractName(displayName, TESTNG_TEST_LAUNCHER_METHOD_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, JUNIT5_TEST_LAUNCHER_METHOD_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, JUNIT4_TEST_LAUNCHER_METHOD_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, JUNIT5_METHOD_DISPLAY_NAME_EXTRACTOR)
        ?: displayName
      else ->
        displayName
    }
  }

  private fun extractName(name: String?, extractor: Regex): String? {
    if (name == null) return null
    val result = extractor.findAll(name).firstOrNull() ?: return null
    if (result.groupValues.firstOrNull() != name) {
      return null
    }
    return result.groupValues.drop(1).joinToString("")
  }

  companion object {
    private val JUNIT5_TEST_LAUNCHER_SUITE_DISPLAY_NAME_EXTRACTOR = "Test suite '(.+)\\(.+\\)'".toRegex()
    private val JUNIT5_TEST_LAUNCHER_METHOD_DISPLAY_NAME_EXTRACTOR = "Test (.+)\\(\\)\\(.+\\)".toRegex()
    private val JUNIT5_PARAMETRIZED_METHOD_NAME_EXTRACTOR = "(.+)\\(.*\\)\\[\\d+]".toRegex()
    private val JUNIT5_PARAMETRIZED_SUITE_NAME_EXTRACTOR = "(.+)\\(.*\\)\\[\\d+]".toRegex()
    private val JUNIT5_PARAMETRIZED_SUITE_DISPLAY_NAME_EXTRACTOR = "(.+?)\\s?\\(.+\\)".toRegex()
    private val JUNIT5_PARAMETRIZED_METHOD_DISPLAY_NAME_EXTRACTOR = "(.+)\\(.*\\)\\[\\d+]".toRegex()
    private val JUNIT5_METHOD_NAME_EXTRACTOR = "(.+)\\(.*\\)".toRegex()
    private val JUNIT5_METHOD_DISPLAY_NAME_EXTRACTOR = "(.+)\\(\\)".toRegex()
    private val JUNIT4_TEST_LAUNCHER_SUITE_DISPLAY_NAME_EXTRACTOR = "Test suite '(.+)'".toRegex()
    private val JUNIT4_TEST_LAUNCHER_METHOD_DISPLAY_NAME_EXTRACTOR = "Test (.+)\\(.+\\)".toRegex()
    private val JUNIT4_CLASS_DISPLAY_NAME_EXTRACTOR = ".*\\.([^.]+)".toRegex()
    private val TESTNG_TEST_LAUNCHER_METHOD_DISPLAY_NAME_EXTRACTOR = "Test method (.+)\\(.+\\)".toRegex()
  }
}