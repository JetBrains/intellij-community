// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.inspections.ExtensionUtil.getNormalizedClassName
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass

private inline val visibleForTestingAnnotations
  get() = listOf(
    "com.google.common.annotations.VisibleForTesting",
    "com.android.annotations.VisibleForTesting",
    "org.jetbrains.annotations.VisibleForTesting"
  )

internal class ExtensionClassShouldBeFinalAndNonPublicInspection : DevKitJvmInspection.ForClass() {

  override fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink) {
    if (!ExtensionUtil.isExtensionPointImplementationCandidate(psiClass)) {
      return
    }
    val sourceElement = psiClass.sourceElement
    val language = sourceElement?.language ?: return
    val file = psiClass.containingFile ?: return

    val isFinal = psiClass.hasModifier(JvmModifier.FINAL)
    val extensionClassShouldNotBePublicProvider = getProvider(ExtensionClassShouldNotBePublicProviders, language) ?: return
    val isPublic = extensionClassShouldNotBePublicProvider.isPublic(psiClass)
    if (isFinal && !isPublic) return

    if (!ExtensionUtil.isInstantiatedExtension(psiClass) { false }) return

    if (!isFinal && !hasInheritors(psiClass)) {
      val actions = createModifierActions(psiClass, modifierRequest(JvmModifier.FINAL, true))
      val errorMessageProvider = getProvider(ExtensionClassShouldBeFinalErrorMessageProviders, language) ?: return
      val message = errorMessageProvider.provideErrorMessage()
      val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
      sink.highlight(message, *fixes)
    }
    if (isPublic && !isAnnotatedAsVisibleForTesting(psiClass)) {
      val message = when {
        isServiceImplementationRegisteredInPluginXml(psiClass) -> DevKitBundle.message("inspection.extension.class.should.not.be.public.service")
        else -> DevKitBundle.message("inspection.extension.class.should.not.be.public.text")
      }
      val fixes = extensionClassShouldNotBePublicProvider.provideQuickFix(psiClass, file)
      sink.highlight(message, *fixes)
    }
  }

}

private fun isServiceImplementationRegisteredInPluginXml(aClass: PsiClass): Boolean {
  val domManager = DomManager.getDomManager(aClass.project)

  for (candidate in locateExtensionsByPsiClass(aClass)) {
    val tag = candidate.pointer.element ?: continue
    val extension = domManager.getDomElement(tag) as? Extension ?: continue
    if (ExtensionUtil.hasServiceBeanFqn(extension)) {
      val attribute = DevKitDomUtil.getAttribute(extension, "serviceImplementation") ?: continue
      return getNormalizedClassName(attribute.stringValue) == aClass.qualifiedName
    }
  }
  return false
}

private fun hasInheritors(aClass: PsiClass): Boolean {
  return DirectClassInheritorsSearch.search(aClass).findFirst() != null
}

private fun isAnnotatedAsVisibleForTesting(psiClass: JvmClass): Boolean {
  return psiClass.annotations.any { visibleForTestingAnnotations.contains(it.qualifiedName) }
}
