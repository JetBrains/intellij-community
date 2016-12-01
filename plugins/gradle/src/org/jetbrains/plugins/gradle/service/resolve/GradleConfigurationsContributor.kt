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
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getContainingCall
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.resolveActualCall

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
    val configurationClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_DOMAIN_OBJECT_COLLECTION, "all"))
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


  override fun process(methodCallInfo: List<String>, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    val psiManager = GroovyPsiManager.getInstance(place.project)
    val psiElement = psiElement()

    if (psiElement.inside(configurationClosure).accepts(place)) {
      val closure = PsiTreeUtil.getParentOfType(place, GrClosableBlock::class.java) ?: return true
      val call = getContainingCall(closure) ?: return true
      val result = resolveActualCall(call)
      for (psiType in result.substitutor.substitutionMap.values) {
        if (psiType != null && psiType.equalsToText(GRADLE_API_CONFIGURATION)) {
          if (!GradleResolverUtil.processDeclarations(psiManager, processor, state, place, GRADLE_API_CONFIGURATION)) return false
          return false
        }
      }
    }

    if (psiElement.inside(configurationsClosure).accepts(place)) {
      val name = place.text
      if (place.parent is GrReferenceExpression || place.parent is GrArgumentList ||
        psiElement.withTreeParent(configurationsClosure).accepts(place)) {
        val variable = GrLightVariable(place.manager, name, GRADLE_API_CONFIGURATION, place)
        if (!processor.execute(variable, state)) return false
      }
      val classHint = processor.getHint(ElementClassHint.KEY)
      val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)

      if (shouldProcessMethods && place is GrReferenceExpression) {
        val resolveScope = place.getResolveScope()
        val psiClass = psiManager.findClassWithCache(GRADLE_API_CONFIGURATION_CONTAINER, resolveScope) ?: return true
        if (GradleResolverUtil.canBeMethodOf(name, psiClass)) {
          return true
        }

        val call = PsiTreeUtil.getParentOfType(place, GrMethodCall::class.java) ?: return true
        val args = call.argumentList
        var argsCount = GradleResolverUtil.getGrMethodArumentsCount(args)
        argsCount += call.closureArguments.size
        argsCount++ // Configuration name is delivered as an argument.

        // at runtime, see org.gradle.internal.metaobject.ConfigureDelegate.invokeMethod
        val returnClass = psiManager.createTypeByFQClassName(GRADLE_API_CONFIGURATION, resolveScope) ?: return true
        val wrappedBase = GrLightMethodBuilder(place.manager, "configure").apply {
          returnType = returnClass
          containingClass = psiClass
          addParameter("configureClosure", GROOVY_LANG_CLOSURE, true)
          val method = psiClass.findMethodsByName("create", true).firstOrNull { it.parameterList.parametersCount == argsCount }
          if (method != null) navigationElement = method
        }
        if (!processor.execute(wrappedBase, state)) return false
      }
    }

    return true
  }
}
