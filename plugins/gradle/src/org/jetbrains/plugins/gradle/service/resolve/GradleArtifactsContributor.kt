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

import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 * *
 * @since 8/30/13
 */
class GradleArtifactsContributor : GradleMethodContextContributor {

  companion object {
    val artifactsClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, "artifacts"))
  }

  override fun process(methodCallInfo: List<String>,
                       processor: PsiScopeProcessor,
                       state: ResolveState,
                       place: PsiElement): Boolean {
    val methodName = if (methodCallInfo.isNotEmpty()) methodCallInfo[0] else null
    if (methodName != null && place is GrReferenceExpression && psiElement().inside(artifactsClosure).accepts(place)) {
      val text = place.text
      if (!methodCallInfo.contains(text) && place is GrReferenceExpression) {
        val myPsi = GrLightField(text, JAVA_LANG_OBJECT, place)
        processor.execute(myPsi, state)
        return false
      }

      if (!GradleResolverUtil.processDeclarations(processor, state, place, GRADLE_API_ARTIFACT_HANDLER)) return false
      // assuming that the method call is addition of an artifact to the given configuration.
      if (!processArtifactAddition(processor, state, place)) return false
    }
    return true
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (artifactsClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_ARTIFACT_HANDLER, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }

  private fun processArtifactAddition(processor: PsiScopeProcessor,
                                      state: ResolveState,
                                      place: PsiElement): Boolean {
    val name = ResolveUtil.getNameHint(processor) ?: return true
    val groovyPsiManager = GroovyPsiManager.getInstance(place.project)
    val artifactHandlerClass = JavaPsiFacade.getInstance(place.project).findClass(GRADLE_API_ARTIFACT_HANDLER, place.resolveScope) ?: return true

    val call = PsiTreeUtil.getParentOfType(place, GrMethodCall::class.java) ?: return true
    val returnClass = groovyPsiManager.createTypeByFQClassName(GRADLE_API_PUBLISH_ARTIFACT, place.resolveScope) ?: return true
    val type = PsiType.getJavaLangObject(place.manager, place.resolveScope).createArrayType()
    val builder = GrLightMethodBuilder(place.manager, name).apply {
      containingClass = artifactHandlerClass
      addParameter(GrLightParameter("artifactNotation", type, place))
      returnType = returnClass
    }
    val args = call.argumentList

    var argsCount = GradleResolverUtil.getGrMethodArumentsCount(args)
    argsCount++ // Configuration name is delivered as an argument.

    val method = artifactHandlerClass.findMethodsByName("add", false).firstOrNull { it.parameterList.parametersCount == argsCount }
    if (method != null) builder.navigationElement = method

    return processor.execute(builder, state)
  }
}
