// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.spock

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.URLUtil
import com.intellij.util.text.nullize
import org.jetbrains.plugins.gradle.execution.test.runner.events.DefaultGradleTestEventConverter.Companion.extractName
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleTestEventConverter
import org.jetbrains.plugins.groovy.ext.spock.isSpockSpecification

internal class SpockGradleTestEventConverter(
  private val parent: SMTestProxy,
  private val isSuite: Boolean,
  private val suiteName: String,
  private val className: String,
  private val methodName: String?,
  private val displayName: String
) : GradleTestEventConverter {

  private fun isApplicable(project: Project): Boolean {
    return runReadAction {
      DumbService.getInstance(project).computeWithAlternativeResolveEnabled<Boolean, Throwable> {
        val className = getClassName()
        val methodName = getMethodName()
        val scope = GlobalSearchScope.allScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)
        val psiClass = psiFacade.findClass(className, scope)
        psiClass != null && psiClass.isSpockSpecification() && (methodName == null || psiClass.methods.any { it.name == methodName })
      }
    }
  }

  override fun getClassName(): String {
    return when {
      className.isEmpty() -> getParentClassName() ?: suiteName
      else -> className
    }
  }

  override fun getMethodName(): String? {
    return when {
      !isSuite -> getParentMethodName() ?: methodName.nullize() ?: suiteName
      className.isEmpty() -> methodName.nullize() ?: getParentMethodName() ?: suiteName
      else -> methodName
    }
  }

  override fun getDisplayName(): String {
    return when {
      !isSuite -> extractName(displayName, TEST_LAUNCHER_METHOD_EXTRACTOR) ?: displayName
      className.isEmpty() -> extractName(displayName, TEST_LAUNCHER_SPOCK_SUITE_EXTRACTOR) ?: displayName
      else -> extractName(displayName, JUNIT_4_CLASS_EXTRACTOR) ?: displayName
    }
  }

  private fun getParentClassName(): String? {
    val locationUrl = parent.locationUrl ?: return null
    val locationPath = URLUtil.extractPath(locationUrl)
    return StringUtil.substringBefore(locationPath, "/") ?: locationPath
  }

  private fun getParentMethodName(): String? {
    val locationUrl = parent.locationUrl ?: return null
    val locationPath = URLUtil.extractPath(locationUrl)
    return StringUtil.substringAfter(locationPath, "/")
  }

  class Factory : GradleTestEventConverter.Factory {

    override fun createConverter(
      project: Project,
      parent: SMTestProxy,
      isSuite: Boolean,
      suiteName: String,
      className: String,
      methodName: String?,
      displayName: String
    ): GradleTestEventConverter? {
      val converter = SpockGradleTestEventConverter(parent, isSuite, suiteName, className, methodName, displayName)
      if (converter.isApplicable(project)) {
        return converter
      }
      return null
    }
  }

  companion object {
    private val TEST_LAUNCHER_SPOCK_SUITE_EXTRACTOR = "Test suite '(.*)'".toRegex()
    private val TEST_LAUNCHER_METHOD_EXTRACTOR = "Test (.+)\\(.+\\)".toRegex()
    private val JUNIT_4_CLASS_EXTRACTOR = ".*\\.([^.]+)".toRegex()
  }
}