/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    val configurationsClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, "configurations"))
    val configurationReference = object : ElementPattern<PsiElement> {
      override fun getCondition() = null
      override fun accepts(o: Any?) = false
      override fun accepts(o: Any?, context: ProcessingContext): Boolean {
        return o is GrExpression && o.type?.equalsToText(GRADLE_API_CONFIGURATION) ?: false
      }
    }
    val dependencySubstitutionClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_RESOLUTION_STRATEGY, "dependencySubstitution"))
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
