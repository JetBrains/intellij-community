// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.spock

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationCustomizer
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationCustomizer.GradleTestLocationInfo
import org.jetbrains.plugins.groovy.ext.spock.isSpockSpecification

internal class SpockGradleTestLocationCustomizer : GradleTestLocationCustomizer {
  override fun getLocationInfo(project: Project,
                               parent: SMTestProxy,
                               fqClassName: String,
                               methodName: String?,
                               displayName: String?): GradleTestLocationInfo? {
    val className = getClassName(fqClassName, parent)
    if (!shouldProcessAsSpock(project, className, methodName)) return null
    val actualMethodName = if (fqClassName.isEmpty() && displayName != null && methodName == null) {
      displayName
    }
    else {
      parent.name
    }
    return GradleTestLocationInfo(className, actualMethodName)
  }
}

private fun shouldProcessAsSpock(project: Project,
                                 className: String,
                                 name: String?): Boolean {
  return runReadAction {
    DumbService.getInstance(project).computeWithAlternativeResolveEnabled<Boolean, Throwable> {
      val clazz = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
      clazz != null && clazz.isSpockSpecification() && (name == null || clazz.methods.all { it.name != name })
    }
  }
}

private fun getClassName(fqClassName: String, parent: SMTestProxy): String {
  return fqClassName.takeUnless { it.isEmpty() } ?: parent.name
}