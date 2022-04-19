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
                               parent: SMTestProxy?,
                               fqClassName: String,
                               methodName: String?,
                               displayName: String?): GradleTestLocationInfo? {
    if (parent == null) {
      return null
    }
    val className = getClassName(fqClassName, parent)
    if (!shouldProcessAsSpock(project, className, methodName)) return null
    val methodName = if (fqClassName.isEmpty() && displayName != null && methodName == null) {
      displayName
    }
    else {
      parent.name
    }
    return GradleTestLocationInfo(className, methodName)
  }
}

private fun shouldProcessAsSpock(project: Project,
                                 className: String,
                                 name: String?): Boolean {
  var shouldProcessAsSpock = true
  runReadAction {
    DumbService.getInstance(project).runWithAlternativeResolveEnabled<Throwable> {
      val clazz = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
      shouldProcessAsSpock = clazz != null && clazz.isSpockSpecification() && (name == null || clazz.methods.all { it.name != name })
    }
  }
  return shouldProcessAsSpock
}

private fun getClassName(fqClassName: String, parent: SMTestProxy): String {
  return fqClassName.takeUnless { it.isEmpty() } ?: parent.name
}