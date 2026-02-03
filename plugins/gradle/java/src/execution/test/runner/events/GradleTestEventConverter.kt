// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.runBlockingMaybeCancellable
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
    isSuite && (className.isEmpty() || suiteName == methodName)
  }

  private val isTestClass: Boolean by lazy {
    isSuite && className.isNotEmpty()
  }

  private val isTestMethod: Boolean by lazy {
    !isSuite && StringUtil.isNotEmpty(className)
  }

  private val isJunit5ParametrizedTestMethod: Boolean by lazy {
    isTestMethod && extractName(methodName, JUNIT5_PARAMETRIZED_METHOD_NAME_EXTRACTOR) != null
  }

  private val isOldJunit5ParametrizedTestMethod: Boolean by lazy {
    isJunit5ParametrizedTestMethod && parentMethodName == null
  }

  private val isEnabledGroovyPlugin: Boolean by lazy {
    val groovyPluginId = PluginId.findId("org.intellij.groovy")
    val groovyPlugin = PluginManagerCore.getPluginSet()
    groovyPluginId != null && groovyPlugin.isPluginEnabled(groovyPluginId)
  }

  private val isSpockTestMethod: Boolean by lazy {
    isTestMethod
    && isEnabledGroovyPlugin
    && runBlockingMaybeCancellable {
      readAction {
        DumbService.getInstance(project).computeWithAlternativeResolveEnabled<Boolean, Throwable> {
          val scope = GlobalSearchScope.allScope(project)
          val psiFacade = JavaPsiFacade.getInstance(project)
          val psiClass = psiFacade.findClass(convertedClassName, scope)
          psiClass != null && psiClass.isSpockSpecification()
        }
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
      isTestSuite ->
        // Incorrect if parametrized test annotated by display name.
        // We have information in child nodes, but we haven't accessed to them.
        extractName(suiteName, JUNIT5_SUITE_NAME_EXTRACTOR)
        ?: suiteName
      isSpockTestMethod ->
        parentMethodName
        ?: methodName
      isTestMethod ->
        extractName(methodName, JUNIT5_PARAMETRIZED_METHOD_NAME_EXTRACTOR)
        ?: extractName(methodName, JUNIT4_PARAMETRIZED_METHOD_NAME_EXTRACTOR)
        ?: extractName(methodName, TESTNG_PARAMETRIZED_METHOD_NAME_EXTRACTOR)
        ?: extractName(methodName, JUNIT5_METHOD_NAME_EXTRACTOR)
        ?: methodName
      else ->
        methodName
    }
  }

  val convertedParameterName: String? by lazy {
    when {
      isTestMethod ->
        extractName(methodName, JUNIT5_PARAMETER_NAME_EXTRACTOR)
        ?: extractName(methodName, JUNIT4_PARAMETER_NAME_EXTRACTOR)
        ?: extractName(methodName, TESTNG_PARAMETER_NAME_EXTRACTOR)
      else ->
        null
    }
  }

  val convertedDisplayName: String by lazy {
    val displayName =
      extractName(displayName, TEST_LAUNCHER_SUITE_DISPLAY_NAME_EXTRACTOR)
      ?: extractName(displayName, TEST_LAUNCHER_METHOD_DISPLAY_NAME_EXTRACTOR)
      ?: extractName(displayName, TEST_LAUNCHER_CLASS_DISPLAY_NAME_EXTRACTOR)
      ?: extractName(displayName, TEST_LAUNCHER_TEST_DISPLAY_NAME_EXTRACTOR)
      ?: displayName
    when {
      isTestSuite ->
        extractName(displayName, JUNIT5_METHOD_DISPLAY_NAME_EXTRACTOR)
        ?: displayName
      isTestClass && displayName == className ->
        extractName(displayName, JUNIT4_CLASS_DISPLAY_NAME_EXTRACTOR)
        ?: displayName
      isOldJunit5ParametrizedTestMethod -> {
        "$convertedMethodName $displayName"
      }
      isTestMethod ->
        extractName(displayName, JUNIT5_METHOD_DISPLAY_NAME_EXTRACTOR)
        ?: displayName
      else ->
        displayName
    }
  }

  private fun extractName(name: String?, extractor: Regex): String? {
    if (name == null) return null
    val result = extractor.matchEntire(name) ?: return null
    return result.groupValues.drop(1).joinToString("")
  }

  companion object {
    private val TEST_LAUNCHER_SUITE_DISPLAY_NAME_EXTRACTOR = "Test suite '(.+)'".toRegex()
    private val TEST_LAUNCHER_METHOD_DISPLAY_NAME_EXTRACTOR = "Test method (.+)\\(.+\\)".toRegex()
    private val TEST_LAUNCHER_CLASS_DISPLAY_NAME_EXTRACTOR = "Test class (.+)".toRegex()
    private val TEST_LAUNCHER_TEST_DISPLAY_NAME_EXTRACTOR = "Test (.+)\\(.+\\)".toRegex()

    private val JUNIT5_METHOD_DISPLAY_NAME_EXTRACTOR = "(.+)\\(\\)".toRegex()
    private val JUNIT4_CLASS_DISPLAY_NAME_EXTRACTOR = ".*[.$]([^.$]+)".toRegex()

    private val JUNIT5_PARAMETER_NAME_EXTRACTOR = ".+\\(.*\\)(\\[\\d+])".toRegex()
    private val JUNIT5_PARAMETRIZED_METHOD_NAME_EXTRACTOR = "(.+)\\(.*\\)\\[\\d+]".toRegex()
    private val JUNIT4_PARAMETER_NAME_EXTRACTOR = ".+(\\[\\d+])".toRegex()
    private val JUNIT4_PARAMETRIZED_METHOD_NAME_EXTRACTOR = "(.+)\\[\\d+]".toRegex()
    private val TESTNG_PARAMETER_NAME_EXTRACTOR = ".+(\\[\\d+])\\(.+\\)".toRegex()
    private val TESTNG_PARAMETRIZED_METHOD_NAME_EXTRACTOR = "(.+)\\[\\d+]\\(.+\\)".toRegex()

    private val JUNIT5_SUITE_NAME_EXTRACTOR = "(.+?)\\s?\\(.*\\)".toRegex()
    private val JUNIT5_METHOD_NAME_EXTRACTOR = "(.+)\\(.*\\)".toRegex()
  }
}