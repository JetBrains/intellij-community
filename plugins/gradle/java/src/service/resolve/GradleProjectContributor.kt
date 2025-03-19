// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.util.ProcessingContext
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyClosurePattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 */
class GradleProjectContributor : GradleMethodContextContributor {
  companion object {
    val projectClosure: GroovyClosurePattern = groovyClosure().inMethod(
      psiMethod(GRADLE_API_PROJECT, "configure")
    ).inMethodResult(saveProjectType)
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    val context = ProcessingContext()
    if (projectClosure.accepts(closure, context)) {
      val projectType = createType(GRADLE_API_PROJECT, closure)
      return DelegatesToInfo(context[projectTypeKey]?.setType(projectType) ?: projectType, Closure.DELEGATE_FIRST)
    }
    return null
  }
}
