// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.spock

import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.URLUtil
import com.intellij.util.text.nullize
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationCustomizer
import org.jetbrains.plugins.groovy.ext.spock.isSpockSpecification

internal class SpockGradleTestLocationCustomizer : GradleTestLocationCustomizer {

  override fun isApplicable(
    project: Project,
    parent: SMTestProxy,
    isSuite: Boolean,
    suiteName: String,
    fqClassName: String,
    methodName: String?
  ): Boolean {
    return runReadAction {
      DumbService.getInstance(project).computeWithAlternativeResolveEnabled<Boolean, Throwable> {
        val patchedFqClassName = patchClassName(parent, suiteName, fqClassName)
        val patchedMethodName = patchMethodName(parent, isSuite, suiteName, fqClassName, methodName)
        val scope = GlobalSearchScope.allScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)
        val psiClass = psiFacade.findClass(patchedFqClassName, scope)
        psiClass != null && psiClass.isSpockSpecification() && (patchedMethodName == null || psiClass.methods.any { it.name == patchedMethodName })
      }
    }
  }

  override fun createLocationUrl(
    parent: SMTestProxy,
    isSuite: Boolean,
    suiteName: String,
    fqClassName: String,
    methodName: String?
  ): String {
    val protocol = if (isSuite) JavaTestLocator.SUITE_PROTOCOL else JavaTestLocator.TEST_PROTOCOL
    val patchedFqClassName = patchClassName(parent, suiteName, fqClassName)
    val patchedMethodName = patchMethodName(parent, isSuite, suiteName, fqClassName, methodName)
    return JavaTestLocator.createLocationUrl(protocol, patchedFqClassName, patchedMethodName)
  }

  private fun patchClassName(parent: SMTestProxy, suiteName: String, fqClassName: String): String {
    return when {
      fqClassName.isEmpty() -> getParentClassName(parent) ?: suiteName
      else -> fqClassName
    }
  }

  private fun patchMethodName(parent: SMTestProxy, isSuite: Boolean, suiteName: String, fqClassName: String, methodName: String?): String? {
    return when {
      !isSuite -> getParentMethodName(parent) ?: methodName.nullize() ?: suiteName
      fqClassName.isEmpty() -> methodName.nullize() ?: getParentMethodName(parent) ?: suiteName
      else -> methodName
    }
  }

  private fun getParentClassName(parent: SMTestProxy): String? {
    val locationUrl = parent.locationUrl ?: return null
    val locationPath = URLUtil.extractPath(locationUrl)
    return StringUtil.substringBefore(locationPath, "/") ?: locationPath
  }

  private fun getParentMethodName(parent: SMTestProxy): String? {
    val locationUrl = parent.locationUrl ?: return null
    val locationPath = URLUtil.extractPath(locationUrl)
    return StringUtil.substringAfter(locationPath, "/")
  }
}