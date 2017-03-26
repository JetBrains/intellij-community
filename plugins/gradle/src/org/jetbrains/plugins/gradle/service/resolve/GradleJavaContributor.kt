/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
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
 * @since 11/10/2016
 */
class GradleJavaContributor : GradleMethodContextContributor {
  companion object {
    val sourceSetsClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_JAVA_PLUGIN_CONVENTION, "sourceSets"))
    val sourceSetReference = object : ElementPattern<PsiElement> {
      override fun getCondition() = null
      override fun accepts(o: Any?) = false
      override fun accepts(o: Any?, context: ProcessingContext): Boolean {
        return o is GrExpression && o.type?.equalsToText(GRADLE_API_SOURCE_SET) ?: false
      }
    }

    val sourceDirectorySetClosure = psiElement().andOr(
      groovyClosure().inMethod(or(psiMethod(GRADLE_API_SOURCE_SET, "java"),
                                  psiMethod(GRADLE_API_SOURCE_SET, "resources"))))
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (sourceSetsClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_SOURCE_SET_CONTAINER, closure), Closure.DELEGATE_FIRST)
    }
    if (sourceDirectorySetClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_SOURCE_DIRECTORY_SET, closure), Closure.DELEGATE_FIRST)
    }
    if (psiElement().withParent(
      psiElement().withFirstChild(sourceSetReference)).accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_SOURCE_SET, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }

  override fun process(methodCallInfo: List<String>, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    if (!GradleResolverUtil.processDeclarations(processor, state, place,
                                                GRADLE_API_BASE_PLUGIN_CONVENTION,
                                                GRADLE_API_JAVA_PLUGIN_CONVENTION,
                                                GRADLE_API_APPLICATION_PLUGIN_CONVENTION,
                                                GRADLE_API_WAR_CONVENTION)) {
      return false
    }

    return true
  }
}