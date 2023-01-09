// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
//import org.jetbrains.plugins.gradle.service.resolve.static.getStaticallyHandledExtensions
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

class GradleProjectExtensionContributor : NonCodeMembersContributor() {

  override fun getParentClassName(): String = GRADLE_API_PROJECT

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (qualifierType !is GradleProjectAwareType) return

    val processMethods = processor.shouldProcessMethods()
    val processProperties = processor.shouldProcessProperties()
    if (!processMethods && !processProperties) {
      return
    }

    val containingFile = place.containingFile
    val extensionsData = GradleExtensionsContributor.getExtensionsFor(containingFile) ?: return

    val name = processor.getName(state)
    val allExtensions = extensionsData.extensions
    val extensions = if (name == null) allExtensions.values else listOf(allExtensions[name] ?: return)
    if (extensions.isEmpty()) return

    val factory = PsiElementFactory.getInstance(containingFile.project)
    val manager = containingFile.manager

    val staticExtensions = getGradleStaticallyHandledExtensions(place.project)

    for (extension in extensions) {
      if (staticExtensions.contains(extension.name)) continue

      val delegateType = createType(factory, extension.rootTypeFqn, place.resolveScope)
      if (delegateType !is PsiClassType) {
        continue
      }
      val type = GradleExtensionType(decoratePsiClassType(delegateType))
      if (processProperties) {
        val extensionProperty = GradleExtensionProperty(extension.name, type, containingFile)
        if (!processor.execute(extensionProperty, state)) {
          return
        }
      }
      if (processMethods && shouldAddConfiguration(extension, place)) {
        val extensionMethod = GrLightMethodBuilder(manager, extension.name).apply {
          returnType = type
          containingClass = aClass
          addAndGetParameter("configuration", createType(GROOVY_LANG_CLOSURE, containingFile))
            .putUserData(DELEGATES_TO_KEY, DelegatesToInfo(type, Closure.DELEGATE_FIRST))
        }
        if (!processor.execute(extensionMethod, state)) {
          return
        }
      }
    }
  }

  private fun shouldAddConfiguration(extension: GradleExtensionsSettings.GradleExtension, context: PsiElement): Boolean {
    val clazz = JavaPsiFacade.getInstance(context.project).findClass(extension.rootTypeFqn, context.resolveScope) ?: return true
    return !InheritanceUtil.isInheritor(clazz, "org.gradle.api.internal.catalog.AbstractExternalDependencyFactory")
  }

  private fun createType(factory: PsiElementFactory, generifiedFqnClassName: String, resolveScope: GlobalSearchScope) : PsiType {
    val className = generifiedFqnClassName.substringBefore('<')
    val hostClassType = factory.createTypeByFQClassName(className, resolveScope)
    val hostClass = hostClassType.resolve() ?: return hostClassType
    val parameters = mutableListOf<String>()
    val builder = StringBuilder()
    var parameterStack = 1
    for (char in generifiedFqnClassName.substringAfter('<')) {
      if (char == '<') {
        parameterStack += 1
      } else if (char == '>') {
        parameterStack -= 1
        if (parameterStack == 0) {
          parameters.add(builder.toString().trim())
        }
      } else if (char == ',') {
        if (parameterStack == 0) {
          parameters.add(builder.toString())
          builder.clear()
        } else {
          builder.append(char)
        }
      } else {
        builder.append(char)
      }
    }
    val parsedParameters = parameters.map { createType(factory, it, resolveScope) }
    return factory.createType(hostClass, *parsedParameters.toTypedArray())
  }
}
