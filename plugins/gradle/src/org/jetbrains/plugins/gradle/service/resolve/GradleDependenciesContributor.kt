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

import com.intellij.openapi.util.Key
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.*
import com.intellij.psi.scope.BaseScopeProcessor
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.PsiScopeProcessor
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil.canBeMethodOf
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 * @since 11/10/2016
 */
class GradleDependenciesContributor : GradleMethodContextContributor {
  companion object {
    val dependenciesClosure = groovyClosure().inMethod(or(psiMethod(GRADLE_API_PROJECT, "dependencies"),
                                                          psiMethod(GRADLE_API_SCRIPT_HANDLER, "dependencies")))
    val dependencyConfigurationClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_DEPENDENCY_HANDLER, "add"))
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (dependencyConfigurationClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY, closure), Closure.DELEGATE_FIRST)
    }
    if (dependenciesClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_DEPENDENCY_HANDLER, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }

  override fun process(methodCallInfo: List<String>, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    val psiManager = GroovyPsiManager.getInstance(place.project)
    val methodName = if (methodCallInfo.isNotEmpty()) methodCallInfo[0] else return true

    val classHint = processor.getHint(ElementClassHint.KEY)
    val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)
    if (shouldProcessMethods && place is GrReferenceExpression && psiElement().inside(dependenciesClosure).accepts(place)) {
      if (methodCallInfo.size == 2) {
        val psiClass = psiManager.findClassWithCache(GRADLE_API_DEPENDENCY_HANDLER, place.getResolveScope()) ?: return true
        if (canBeMethodOf(methodName, psiClass)) {
          return true
        }
        val componentProcessor = AddDependencyNotationProcessor(processor, place, psiClass)
        if (!psiClass.processDeclarations(componentProcessor, state, null, place)) return false
      }
    }
    if (psiElement().inside(dependencyConfigurationClosure).accepts(place)) {
      if (GradleResolverUtil.processDeclarations(psiManager, processor, state, place,
                                                 GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY,
                                                 GRADLE_API_ARTIFACTS_CLIENT_MODULE_DEPENDENCY)) return false
    }
    return true
  }

  class AddDependencyNotationProcessor(val delegate: PsiScopeProcessor, val place: PsiElement, val psiClass: PsiClass) : BaseScopeProcessor() {
    override fun execute(method: PsiElement, state: ResolveState): Boolean {
      method as? PsiMethod ?: return true

      val wrappedBase = GrLightMethodBuilder(place.manager, "add").apply {
        navigationElement = method
        containingClass = psiClass
      }
      wrappedBase.addParameter("configurationName", CommonClassNames.JAVA_LANG_OBJECT)
      wrappedBase.addParameter("dependencyNotation", CommonClassNames.JAVA_LANG_OBJECT, true)
      if (!delegate.execute(wrappedBase, state)) return false
      return true
    }

    override fun <T : Any?> getHint(hintKey: Key<T>) = if (hintKey == com.intellij.psi.scope.ElementClassHint.KEY) {
      @Suppress("UNCHECKED_CAST")
      ElementClassHint { it == com.intellij.psi.scope.ElementClassHint.DeclarationKind.METHOD } as T
    }
    else {
      null
    }
  }
}