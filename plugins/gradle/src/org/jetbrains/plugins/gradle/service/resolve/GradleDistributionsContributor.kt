/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 * *
 * @since 8/30/13
 */
class GradleDistributionsContributor : GradleMethodContextContributor {

  companion object {
    val DISTRIBUTIONS_METHOD = "distributions"
    val distributionsClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, DISTRIBUTIONS_METHOD))
    val distributionClosure = groovyClosure().withAncestor(2, distributionsClosure)
    val contentsClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_DISTRIBUTION, "contents"))
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (distributionsClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_DISTRIBUTION_CONTAINER, closure), Closure.DELEGATE_FIRST)
    }
    if (distributionClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_DISTRIBUTION, closure), Closure.DELEGATE_FIRST)
    }
    if (contentsClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_FILE_COPY_SPEC, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }

  override fun process(methodCallInfo: MutableList<String>,
                       processor: PsiScopeProcessor,
                       state: ResolveState,
                       place: PsiElement): Boolean {
    val classHint = processor.getHint(ElementClassHint.KEY)
    val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)
    if (shouldProcessMethods && processor.getHint(NameHint.KEY)?.getName(state) == DISTRIBUTIONS_METHOD) {
      val psiManager = GroovyPsiManager.getInstance(place.project)
      val projectClass = psiManager.findClassWithCache(GRADLE_API_PROJECT, place.resolveScope) ?: return true
      val distContainerClass = psiManager.createTypeByFQClassName(GRADLE_API_DISTRIBUTION_CONTAINER, place.resolveScope) ?: return true
      val methodBuilder = GrLightMethodBuilder(place.manager, DISTRIBUTIONS_METHOD).apply {
        containingClass = projectClass
        returnType = distContainerClass
      }
      methodBuilder.addParameter("configuration", GROOVY_LANG_CLOSURE, true)
      if (!processor.execute(methodBuilder, state)) return false
    }

    val psiElement = PsiJavaPatterns.psiElement()
    if (psiElement.inside(distributionsClosure).accepts(place)) {
      val name = place.text
      if (place.parent is GrMethodCallExpression) {
        val psiManager = GroovyPsiManager.getInstance(place.project)
        val methodBuilder = GradleResolverUtil.createMethodWithClosure(
          name, GRADLE_API_DISTRIBUTION, null, place, psiManager)
        if (methodBuilder != null) {
          if (!processor.execute(methodBuilder, state)) return false
        }
      }
      if (place.parent is GrReferenceExpression || psiElement.withTreeParent(distributionsClosure).accepts(place)) {
        val variable = GrLightVariable(place.manager, name, GRADLE_API_DISTRIBUTION, place)
        if (!processor.execute(variable, state)) return false
      }
    }

    return true
  }
}
