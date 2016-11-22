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

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Ref
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.ProcessingContext
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder.DOCUMENTATION
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
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
 * @since 11/16/2016
 */
class GradleExtensionsContributor : GradleMethodContextContributor {

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    val extensionsData = Companion.getExtensionsFor(closure) ?: return null
    for (extension in extensionsData.extensions) {
      val extensionClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, extension.name))
      if (extensionClosure.accepts(closure)) {
        return DelegatesToInfo(TypesUtil.createType(extension.rootTypeFqn, closure), Closure.DELEGATE_FIRST)
      }
      val objectTypeFqn = extension.namedObjectTypeFqn?.let { if (it.isNotBlank()) it else null } ?: continue
      val objectClosure = groovyClosure().withAncestor(2, extensionClosure)
      if (objectClosure.accepts(closure)) {
        return DelegatesToInfo(TypesUtil.createType(objectTypeFqn, closure), Closure.DELEGATE_FIRST)
      }

      val objectReference = object : ElementPattern<PsiElement> {
        override fun getCondition() = null
        override fun accepts(o: Any?) = false
        override fun accepts(o: Any?, context: ProcessingContext): Boolean {
          return o is GrExpression && o.type?.equalsToText(objectTypeFqn) ?: false
        }
      }
      if (psiElement().withParent(
        psiElement().withFirstChild(objectReference)).accepts(closure)) {
        return DelegatesToInfo(TypesUtil.createType(objectTypeFqn, closure), Closure.DELEGATE_FIRST)
      }
    }
    return null
  }

  override fun process(methodCallInfo: MutableList<String>,
                       processor: PsiScopeProcessor,
                       state: ResolveState,
                       place: PsiElement): Boolean {
    val extensionsData = Companion.getExtensionsFor(place) ?: return true
    val classHint = processor.getHint(ElementClassHint.KEY)
    val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)
    val shouldProcessProperties = ResolveUtil.shouldProcessProperties(classHint)
    val psiManager = GroovyPsiManager.getInstance(place.project)
    val resolveScope = place.resolveScope
    val projectClass = psiManager.findClassWithCache(GRADLE_API_PROJECT, resolveScope) ?: return true
    val name = processor.getHint(NameHint.KEY)?.getName(state)
    if (!shouldProcessMethods && shouldProcessProperties && place is GrReferenceExpression) {
      if (!place.isQualified) {
        for (gradleProp in extensionsData.properties) {
          if (name == gradleProp.name) {
            val docRef = Ref.create<String>()
            val variable = object : GrLightVariable(place.manager, name, gradleProp.typeFqn, place) {
              override fun getNavigationElement(): PsiElement {
                val navigationElement = super.getNavigationElement()
                navigationElement.putUserData(DOCUMENTATION, docRef.get())
                return navigationElement
              }
            }
            val doc = getDocumentation(gradleProp, variable)
            docRef.set(doc)
            place.putUserData(DOCUMENTATION, doc)
            if (!processor.execute(variable, state)) return false
          }
        }
      }
    }

    for (extension in extensionsData.extensions) {
      if (!shouldProcessMethods && shouldProcessProperties && name == extension.name) {
        val variable = GrLightVariable(place.manager, name, extension.rootTypeFqn, place)
        if (!processor.execute(variable, state)) return false
      }

      if (shouldProcessMethods && name == extension.name) {
        val returnClass = psiManager.createTypeByFQClassName(extension.rootTypeFqn, resolveScope) ?: continue
        val methodBuilder = GrLightMethodBuilder(place.manager, extension.name).apply {
          containingClass = projectClass
          returnType = returnClass
        }
        methodBuilder.addParameter("configuration", GROOVY_LANG_CLOSURE, true)
        if (!processor.execute(methodBuilder, state)) return false
      }

      val objectTypeFqn = extension.namedObjectTypeFqn?.let { if (it.isNotBlank()) it else null }
      val extensionClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, extension.name))
      val placeText = place.text
      val psiElement = psiElement()
      if (psiElement.inside(extensionClosure).accepts(place)) {
        if (shouldProcessMethods && !GradleResolverUtil.processDeclarations(psiManager, processor, state, place, extension.rootTypeFqn)) {
          return false
        }

        if(objectTypeFqn == null) continue
        if (place.parent is GrMethodCallExpression) {
          val methodBuilder = GradleResolverUtil.createMethodWithClosure(placeText, objectTypeFqn, null, place, psiManager)
          if (methodBuilder != null) {
            if (!processor.execute(methodBuilder, state)) return false
          }
        }
        if (place.parent is GrReferenceExpression || psiElement.withTreeParent(extensionClosure).accepts(place)) {
          val variable = GrLightVariable(place.manager, placeText, objectTypeFqn, place)
          if (!processor.execute(variable, state)) return false
        }

      }
    }
    return true
  }

  private fun getDocumentation(gradleProp: GradleExtensionsSettings.GradleProp,
                               lightVariable: GrLightVariable): String {
    val buffer = StringBuilder()
    buffer.append("<PRE>")
    JavaDocInfoGenerator.generateType(buffer, lightVariable.type, lightVariable, true)
    buffer.append(" " + gradleProp.name)
    val hasInitializer = !gradleProp.value.isNullOrBlank()
    if (hasInitializer) {
      buffer.append(" = " + gradleProp.value)
    }
    buffer.append("</PRE>")
    if (hasInitializer) {
      buffer.append("<b>Initial value has been got during last import</b>")
    }
    return buffer.toString()
  }

  companion object {
    fun getExtensionsFor(psiElement: PsiElement): GradleExtensionsSettings.GradleExtensionsData? {
      val project = psiElement.project
      val virtualFile = psiElement.containingFile?.originalFile?.virtualFile ?: return null
      val module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile)
      return GradleExtensionsSettings.getInstance(project).getExtensionsFor(module) ?: return null
    }
  }
}
