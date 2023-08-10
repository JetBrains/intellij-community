// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.toml

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension
import org.jetbrains.plugins.gradle.toml.findOriginInTomlFile
import org.jetbrains.plugins.gradle.toml.findTomlFile
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyBase

class GradleVersionCatalogTomlAwareGotoDeclarationHandler : GotoDeclarationHandler {

  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
    if (!Registry.`is`(CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT, false)) {
      return null
    }
    if (sourceElement == null) {
      return null
    }
    val resolved = sourceElement.parentOfType<GrReferenceElement<*>>(true)?.resolve()
    if (resolved is GroovyPropertyBase && resolved.name == "libs") {
      val toml = findTomlFile(sourceElement, resolved.name)
      if (toml != null) {
        return arrayOf(toml)
      }
    }
    if (resolved is PsiMethod && resolved.isInAccessor()) {
      val actualMethod = findFinishingNode(sourceElement) ?: resolved
      return findOriginInTomlFile(actualMethod, sourceElement)?.let { arrayOf(it) }
    }
    return null
  }
}

private fun findFinishingNode(element: PsiElement): PsiMethod? {
  var topElement: PsiMethod? = null
  for (ancestor in element.parents(true)) {
    if (ancestor !is GrReferenceElement<*>) {
      continue
    }
    val resolved = ancestor.resolve()
    if (resolved is PsiMethod && resolved.isInAccessor()) {
      topElement = resolved
    }
  }
  return topElement
}

private fun PsiMethod.isInAccessor(): Boolean {
  return this.containingClass?.getTopContainingClass()?.name?.startsWith("LibrariesFor") == true
}

private fun PsiClass.getTopContainingClass(): PsiClass {
  return containingClass?.getTopContainingClass() ?: this
}