// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.index.ExtensionPointClassIndex
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement

/**
 * Provides gutter icon for EP interface class to matching `<extensionPoint>` in `plugin.xml`.
 */
internal class ExtensionPointDeclarationRelatedItemLineMarkerProvider : DevkitRelatedLineMarkerProviderBase() {

  override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>?>) {
    val uClass = element.toUElement(UClass::class.java) ?: return
    val psiClass = uClass.javaPsi
    val project = psiClass.project
    val scope = PluginRelatedLocatorsUtils.getCandidatesScope(project)

    val extensionPoints = ExtensionPointClassIndex.getExtensionPointsByClass(project, psiClass, scope)
    if (extensionPoints.isEmpty()) return

    // Filter to only include EPs where this class is the interface or with.implements, not just the beanClass
    val relevantEps = filterRelevantExtensionPoints(psiClass, extensionPoints)
    if (relevantEps.isEmpty()) return

    val classIdentifier = uClass.uastAnchor?.sourcePsi ?: return

    val targets = relevantEps.map { ExtensionPointCandidate(SmartPointerManager.createPointer(it.xmlTag), it.effectiveQualifiedName) }
    val info = LineMarkerInfoHelper.createExtensionPointLineMarkerInfo(targets, classIdentifier)
    result.add(info)
  }

  /**
   * Filters extension points to only include those where the class is referenced as
   * `interface` or `with.implements`, excluding those where it's only `beanClass`
   * (it would show hundreds of inlay hints for classes like `LanguageExtensionPoint`).
   */
  private fun filterRelevantExtensionPoints(psiClass: PsiClass, extensionPoints: List<ExtensionPoint>): List<ExtensionPoint> {
    val classQualifiedName = psiClass.qualifiedName ?: return emptyList()

    val relevantEps = extensionPoints.filter { ep ->
      // include if class is the EP interface
      if (ep.`interface`.stringValue == classQualifiedName) {
        return@filter true
      }
      // include if class is the implementationClass of EP's `with`
      for (withElement in ep.withElements) {
        if (withElement.attribute.stringValue == "implementationClass" && withElement.implements.stringValue == classQualifiedName) {
          return@filter true
        }
      }
      false
    }

    // handle duplications from Kotlin compiler frontend (OSIP-191)
    val epsByQualifiedName = relevantEps.groupBy { it.effectiveQualifiedName }
    return epsByQualifiedName.values.flatMap {
      if (it.size > 1) {
        val epsWithoutLibs = extensionPointsWithoutLibs(psiClass, it)
        if (epsWithoutLibs.any()) {
          return@flatMap epsWithoutLibs
        }
      }
      it
    }
  }

  private fun extensionPointsWithoutLibs(psiClass: PsiClass, relevantEps: List<ExtensionPoint>): List<ExtensionPoint> {
    val project = psiClass.project
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    val epsWithoutLibs = relevantEps.filter {
      val virtualFile = it.xmlElement?.containingFile?.virtualFile ?: return@filter false
      !projectFileIndex.isInLibrary(virtualFile)
    }
    return epsWithoutLibs
  }
}
