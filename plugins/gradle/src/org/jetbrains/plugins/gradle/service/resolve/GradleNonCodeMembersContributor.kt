/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Ref
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionsContributor.Companion.getDocumentation
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings.GradleExtensionsData
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_STRATEGY_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo

/**
 * @author Vladislav.Soroka
 *
 * @since 11/25/2016
 */
class GradleNonCodeMembersContributor : NonCodeMembersContributor() {
  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (aClass == null) return
    val containingFile = place.containingFile
    if (!containingFile.isGradleScript() || containingFile?.originalFile?.virtualFile == aClass.containingFile?.originalFile?.virtualFile) return

    processDeclarations(aClass, processor, state, place)

    if (qualifierType.equalsToText(GRADLE_API_PROJECT)) {
      val propCandidate = place.references.singleOrNull()?.canonicalText ?: return
      val extensionsData: GradleExtensionsData?
      val methodCall = place.children.singleOrNull()
      if (methodCall is GrMethodCallExpression) {
        val projectPath = methodCall.argumentList.expressionArguments.singleOrNull()?.reference?.canonicalText ?: return
        if (projectPath == ":") {
          val file = containingFile?.originalFile?.virtualFile ?: return
          val module = ProjectFileIndex.SERVICE.getInstance(place.project).getModuleForFile(file)
          val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
          extensionsData = GradleExtensionsSettings.getInstance(place.project).getExtensionsFor(rootProjectPath, rootProjectPath) ?: return
        }
        else {
          val module = ModuleManager.getInstance(place.project).findModuleByName(projectPath.trimStart(':')) ?: return
          extensionsData = GradleExtensionsSettings.getInstance(place.project).getExtensionsFor(module) ?: return
        }
      }
      else if (methodCall is GrReferenceExpression) {
        if (place.children[0].text == "rootProject") {
          val file = containingFile?.originalFile?.virtualFile ?: return
          val module = ProjectFileIndex.SERVICE.getInstance(place.project).getModuleForFile(file)
          val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
          extensionsData = GradleExtensionsSettings.getInstance(place.project).getExtensionsFor(rootProjectPath, rootProjectPath) ?: return
        }
        else return
      }
      else return

      val processVariable: (GradleExtensionsSettings.TypeAware) -> Boolean = {
        val docRef = Ref.create<String>()
        val variable = object : GrLightVariable(place.manager, propCandidate, it.typeFqn, place) {
          override fun getNavigationElement(): PsiElement {
            val navigationElement = super.getNavigationElement()
            navigationElement.putUserData(NonCodeMembersHolder.DOCUMENTATION, docRef.get())
            return navigationElement
          }
        }
        val doc = getDocumentation(it, variable)
        docRef.set(doc)
        place.putUserData(NonCodeMembersHolder.DOCUMENTATION, doc)
        processor.execute(variable, state)
      }

      extensionsData.tasks.firstOrNull { it.name == propCandidate }?.let(processVariable)
      extensionsData.findProperty(propCandidate)?.let(processVariable)
    }
    else {
      val propCandidate = place.references.singleOrNull()?.canonicalText ?: return
      val domainObjectType = (qualifierType.superTypes.firstOrNull { it is PsiClassType } as? PsiClassType)?.parameters?.singleOrNull() ?: return
      if (!InheritanceUtil.isInheritor(qualifierType, GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER)) return

      val classHint = processor.getHint(com.intellij.psi.scope.ElementClassHint.KEY)
      val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)
      val shouldProcessProperties = ResolveUtil.shouldProcessProperties(classHint)
      if (GradleResolverUtil.canBeMethodOf(propCandidate, aClass)) return

      val domainObjectFqn = TypesUtil.getQualifiedName(domainObjectType) ?: return
      val javaPsiFacade = JavaPsiFacade.getInstance(place.project)
      val domainObjectPsiClass = javaPsiFacade.findClass(domainObjectFqn, place.resolveScope) ?: return
      if (GradleResolverUtil.canBeMethodOf(propCandidate, domainObjectPsiClass)) return
      if (GradleResolverUtil.canBeMethodOf("get" + propCandidate.capitalize(), domainObjectPsiClass)) return
      if (GradleResolverUtil.canBeMethodOf("set" + propCandidate.capitalize(), domainObjectPsiClass)) return

      val closure = PsiTreeUtil.getParentOfType(place, GrClosableBlock::class.java)
      val typeToDelegate = closure?.let { getDelegatesToInfo(it)?.typeToDelegate }
      if (typeToDelegate != null) {
        val fqNameToDelegate = TypesUtil.getQualifiedName(typeToDelegate) ?: return
        val classToDelegate = javaPsiFacade.findClass(fqNameToDelegate, place.resolveScope) ?: return
        if (classToDelegate !== aClass) {
          val parent = place.parent
          if (parent is GrMethodCall) {
            if (canBeMethodOf(propCandidate, parent, typeToDelegate)) return
          }
        }
      }

      if (!shouldProcessMethods && shouldProcessProperties && place is GrReferenceExpression && place.parent !is GrApplicationStatement) {
        val variable = object : GrLightField(propCandidate, domainObjectFqn, place) {
          override fun getNavigationElement(): PsiElement {
            val navigationElement = super.getNavigationElement()
            return navigationElement
          }
        }
        place.putUserData(RESOLVED_CODE, true)
        if (!processor.execute(variable, state)) return
      }
      if (shouldProcessMethods && place is GrReferenceExpression) {
        val call = PsiTreeUtil.getParentOfType(place, GrMethodCall::class.java) ?: return
        val args = call.argumentList
        var argsCount = GradleResolverUtil.getGrMethodArumentsCount(args)
        argsCount += call.closureArguments.size
        argsCount++ // Configuration name is delivered as an argument.

        // at runtime, see org.gradle.internal.metaobject.ConfigureDelegate.invokeMethod
        val wrappedBase = GrLightMethodBuilder(place.manager, propCandidate).apply {
          returnType = domainObjectType
          containingClass = aClass
          val closureParam = addAndGetParameter("configuration", GROOVY_LANG_CLOSURE, true)
          closureParam.putUserData(DELEGATES_TO_KEY, domainObjectFqn)
          closureParam.putUserData(DELEGATES_TO_STRATEGY_KEY, Closure.DELEGATE_FIRST)

          val method = aClass.findMethodsByName("create", true).firstOrNull { it.parameterList.parametersCount == argsCount }
          if (method != null) navigationElement = method
        }
        place.putUserData(RESOLVED_CODE, true)
        if (!processor.execute(wrappedBase, state)) return
      }
    }
  }
}
