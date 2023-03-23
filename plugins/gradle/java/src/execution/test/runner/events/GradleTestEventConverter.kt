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

  private val isParametrizedTestSuite: Boolean by lazy {
    isTestSuite && extractName(suiteName, PARAMETERIZED_JUNIT_5_SUITE_DISPLAY_NAME_EXTRACTOR) != null
  }

  private val isParametrizedTestMethod: Boolean by lazy {
    isTestMethod && extractName(methodName, PARAMETERIZED_JUNIT_5_METHOD_DISPLAY_NAME_EXTRACTOR) != null
  }

  private val isOldParametrizedTestMethod: Boolean by lazy {
    isParametrizedTestMethod && parentMethodName == null
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
      isParametrizedTestSuite ->
        extractName(suiteName, JUNIT_5_METHOD_EXTRACTOR)?.trim()
        ?: suiteName
      isTestSuite ->
        // Incorrect if parametrized test annotated by display name.
        // We have information in child nodes, but we haven't accessed to them.
        extractName(suiteName, JUNIT_5_METHOD_EXTRACTOR)?.trim()
        ?: suiteName
      isSpockTestMethod ->
        parentMethodName
        ?: extractName(methodName, PARAMETERIZED_JUNIT_5_METHOD_EXTRACTOR)
        ?: extractName(methodName, JUNIT_5_METHOD_EXTRACTOR)
        ?: methodName
      isTestMethod ->
        extractName(methodName, PARAMETERIZED_JUNIT_5_METHOD_EXTRACTOR)
        ?: extractName(methodName, JUNIT_5_METHOD_EXTRACTOR)
        ?: methodName
      else ->
        methodName
    }
  }

  val convertedDisplayName: String by lazy {
    when {
      isTestSuite ->
        extractName(displayName, TEST_LAUNCHER_JUNIT5_SUITE_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, TEST_LAUNCHER_JUNIT4_SUITE_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, JUNIT_5_METHOD_DISPLAY_NAME_EXTRACTOR)?.trim()
        ?: displayName
      isTestClass ->
        extractName(displayName, JUNIT_4_CLASS_DISPLAY_NAME_EXTRACTOR)
        ?: displayName
      isOldParametrizedTestMethod -> {
        val displayMethodName =
          extractName(convertedMethodName, JUNIT_5_METHOD_DISPLAY_NAME_EXTRACTOR)
          ?: convertedMethodName
        "$displayMethodName $displayName"
      }
      isTestMethod ->
        extractName(displayName, TEST_LAUNCHER_JUNIT5_METHOD_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, TEST_LAUNCHER_JUNIT4_METHOD_DISPLAY_NAME_EXTRACTOR)
        ?: extractName(displayName, JUNIT_5_METHOD_DISPLAY_NAME_EXTRACTOR)
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
    private val TEST_LAUNCHER_JUNIT5_SUITE_DISPLAY_NAME_EXTRACTOR = "Test suite '(.+)\\(.+\\)'".toRegex()
    private val TEST_LAUNCHER_JUNIT5_METHOD_DISPLAY_NAME_EXTRACTOR = "Test (.+)\\(\\)\\(.+\\)".toRegex()
    private val PARAMETERIZED_JUNIT_5_METHOD_EXTRACTOR = "(.+)\\(.*\\)\\[\\d+]".toRegex()
    private val PARAMETERIZED_JUNIT_5_SUITE_DISPLAY_NAME_EXTRACTOR = "(.+)\\(.*\\)\\[\\d+]".toRegex()
    private val PARAMETERIZED_JUNIT_5_METHOD_DISPLAY_NAME_EXTRACTOR = "(.+)\\(.*\\)\\[\\d+]".toRegex()
    private val JUNIT_5_METHOD_EXTRACTOR = "(.+)\\(.*\\)".toRegex()
    private val JUNIT_5_METHOD_DISPLAY_NAME_EXTRACTOR = "(.+)\\(.*\\)".toRegex()
    private val TEST_LAUNCHER_JUNIT4_SUITE_DISPLAY_NAME_EXTRACTOR = "Test suite '(.+)'".toRegex()
    private val TEST_LAUNCHER_JUNIT4_METHOD_DISPLAY_NAME_EXTRACTOR = "Test (.+)\\(.+\\)".toRegex()
    private val JUNIT_4_CLASS_DISPLAY_NAME_EXTRACTOR = ".*\\.([^.]+)".toRegex()
  }
}