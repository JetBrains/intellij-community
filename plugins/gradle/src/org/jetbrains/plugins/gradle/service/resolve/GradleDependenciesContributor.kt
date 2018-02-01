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

import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
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
    val moduleDependencyConfigurationClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_DEPENDENCY_HANDLER, "module"))
    val modulesClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_DEPENDENCY_HANDLER, "modules"))
    val componentsClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_DEPENDENCY_HANDLER, "components"))
    val moduleClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_COMPONENT_MODULE_METADATA_HANDLER, "module"))
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (dependencyConfigurationClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY, closure), Closure.DELEGATE_FIRST)
    }
    if (moduleDependencyConfigurationClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_ARTIFACTS_CLIENT_MODULE_DEPENDENCY, closure), Closure.DELEGATE_FIRST)
    }
    if (dependenciesClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_DEPENDENCY_HANDLER, closure), Closure.DELEGATE_FIRST)
    }
    if (modulesClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_COMPONENT_MODULE_METADATA_HANDLER, closure), Closure.DELEGATE_FIRST)
    }
    if (moduleClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_COMPONENT_MODULE_METADATA_DETAILS, closure), Closure.DELEGATE_FIRST)
    }
    if (componentsClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_COMPONENT_METADATA_HANDLER, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }

  override fun process(methodCallInfo: List<String>, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    val groovyPsiManager = GroovyPsiManager.getInstance(place.project)
    val methodName = methodCallInfo.firstOrNull() ?: return true

    val classHint = processor.getHint(ElementClassHint.KEY)
    val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)
    if (shouldProcessMethods && place is GrReferenceExpression && psiElement().inside(dependenciesClosure).accepts(place)) {
      if (methodCallInfo.size == 2) {
        val resolveScope = place.getResolveScope()
        val psiClass = JavaPsiFacade.getInstance(place.project).findClass(GRADLE_API_DEPENDENCY_HANDLER, resolveScope) ?: return true
        if (canBeMethodOf(methodName, psiClass)) {
          return true
        }

        val returnClass = groovyPsiManager.createTypeByFQClassName(GRADLE_API_ARTIFACTS_DEPENDENCY, resolveScope) ?: return true
        val wrappedBase = GrLightMethodBuilder(place.manager, methodName).apply {
          returnType = returnClass
          containingClass = psiClass
        }
        wrappedBase.addParameter("dependencyNotation", TypesUtil.getJavaLangObject(place).createArrayType())
        if (!processor.execute(wrappedBase, state)) return false
      }
    }
    if (psiElement().inside(dependencyConfigurationClosure).accepts(place)) {
      if (GradleResolverUtil.processDeclarations(processor, state, place,
                                                 GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY,
                                                 GRADLE_API_ARTIFACTS_CLIENT_MODULE_DEPENDENCY)) return false
    }
    return true
  }
}