// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.gradle.model.GradleExtension
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

/**
 * Provides common functionality for Gradle extension type contributors.
 */
open class AbstractGradleExtensionContributor : NonCodeMembersContributor() {

  protected fun processExtensions(
    extensions: Collection<GradleExtensionsSettings.GradleExtension>,
    containingFile: PsiFile,
    place: PsiElement,
    processor: PsiScopeProcessor,
    aClass: PsiClass,
    state: ResolveState,
  ) {
    if (extensions.isEmpty()) return

    val factory = PsiElementFactory.getInstance(containingFile.project)
    val manager = containingFile.manager

    val staticExtensions = getGradleStaticallyHandledExtensions(place.project)

    for (extension in extensions) {
      val result = registerExtension(extension.name, extension, staticExtensions, factory, manager, processor, aClass, place, state)
      if (result == false) {
        return
      }
    }
  }

  protected fun registerExtension(
    name: String,
    extension: GradleExtensionsSettings.GradleExtension,
    staticExtensions: Set<String>,
    factory: PsiElementFactory,
    manager: PsiManager,
    processor: PsiScopeProcessor,
    aClass: PsiClass,
    place: PsiElement,
    state: ResolveState
  ): Boolean? {
    val processMethods = processor.shouldProcessMethods()
    val processProperties = processor.shouldProcessProperties()

    if (staticExtensions.contains(name)) return null

    val delegateType = createType(factory, extension.typeFqn, place.resolveScope)
    if (delegateType !is PsiClassType) {
      return null
    }
    val type = GradleExtensionType(name, delegateType)
    if (processProperties) {
      val extensionProperty = GradleExtensionProperty(name.substringAfterLast("."), name.substringBeforeLast(".", ""), type, aClass)
      if (!processor.execute(extensionProperty, state)) {
        return false
      }
    }
    if (processMethods && shouldAddConfiguration(extension, place)) {
      val extensionMethod = GradleConfigureExtensionMethod(manager, name.substringAfterLast("."), name.substringBeforeLast(".", ""), type, aClass);
      if (!processor.execute(extensionMethod, state)) {
        return false
      }
    }

    return true
  }

  private fun shouldAddConfiguration(extension: GradleExtensionsSettings.GradleExtension, context: PsiElement): Boolean {
    val clazz = JavaPsiFacade.getInstance(context.project).findClass(extension.typeFqn, context.resolveScope) ?: return true
    return !InheritanceUtil.isInheritor(clazz, "org.gradle.api.internal.catalog.AbstractExternalDependencyFactory")
  }

  private fun createType(factory: PsiElementFactory, generifiedFqnClassName: String, resolveScope: GlobalSearchScope): PsiType {
    val className = generifiedFqnClassName.substringBefore('<')
    val hostClassType = factory.createTypeByFQClassName(className, resolveScope)
    val hostClass = hostClassType.resolve() ?: return hostClassType
    val parameters = mutableListOf<String>()
    val builder = StringBuilder()
    var parameterStack = 1
    for (char in generifiedFqnClassName.substringAfter('<')) {
      if (char == '<') {
        parameterStack += 1
      }
      else if (char == '>') {
        parameterStack -= 1
        if (parameterStack == 0) {
          parameters.add(builder.toString().trim())
        }
      }
      else if (char == ',') {
        if (parameterStack == 0) {
          parameters.add(builder.toString())
          builder.clear()
        }
        else {
          builder.append(char)
        }
      }
      else {
        builder.append(char)
      }
    }
    val parsedParameters = parameters.map { createType(factory, it, resolveScope) }
    return factory.createType(hostClass, *parsedParameters.toTypedArray())
  }
}