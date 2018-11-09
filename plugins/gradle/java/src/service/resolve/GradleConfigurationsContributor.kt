// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyClosurePattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 * *
 * @since 8/29/13
 */
class GradleConfigurationsContributor : GradleMethodContextContributor {

  companion object {
    val configurationsClosure: GroovyClosurePattern = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, "configurations"))
    val configurationReference: ElementPattern<PsiElement> = object : ElementPattern<PsiElement> {
      override fun getCondition() = null
      override fun accepts(o: Any?) = false
      override fun accepts(o: Any?, context: ProcessingContext): Boolean {
        return o is GrExpression && o.type?.equalsToText(GRADLE_API_CONFIGURATION) ?: false
      }
    }
    val dependencySubstitutionClosure: GroovyClosurePattern = groovyClosure().inMethod(psiMethod(GRADLE_API_RESOLUTION_STRATEGY, "dependencySubstitution"))
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (configurationsClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_CONFIGURATION_CONTAINER, closure), Closure.DELEGATE_FIRST)
    }

    // // resolve configuration ref closure:
    // configurations.myConfiguration {
    //    extendsFrom(configurations.testCompile)
    // }
    if (psiElement().withParent(psiElement().withFirstChild(configurationReference)).accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_CONFIGURATION, closure), Closure.DELEGATE_FIRST)
    }

    if (dependencySubstitutionClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_DEPENDENCY_SUBSTITUTIONS, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }
}
