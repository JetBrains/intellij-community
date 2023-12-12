// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.util.JvmInheritanceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import com.intellij.util.xml.highlighting.RemoveDomElementQuickFix
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement

internal class LightServiceMigrationXMLInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is Extension) return
    if (!isAllowed(holder)) return

    if (isVersion193OrHigher(element) ||
        ApplicationManager.getApplication().isUnitTestMode) {
      val (aClass, level) = getServiceImplementation(element) ?: return
      if (!aClass.hasModifier(JvmModifier.FINAL) || isLibraryClass(aClass)) return
      if (level == Service.Level.APP &&
          JvmInheritanceUtil.isInheritor(aClass, PersistentStateComponent::class.java.canonicalName)) {
        return
      }
      val uClass = aClass.toUElement(UClass::class.java)
      if (uClass == null || containsUnitTestOrHeadlessModeCheck(uClass)) return
      if (aClass.hasAnnotation(Service::class.java.canonicalName)) {
        val message = DevKitBundle.message("inspection.light.service.migration.already.annotated.message")
        holder.createProblem(element, ProblemHighlightType.ERROR, message, null, RemoveDomElementQuickFix(element))
      }
      else {
        val message = getMessage(level)
        holder.createProblem(element, message)
      }
    }
  }

  private fun isLibraryClass(aClass: PsiClass): Boolean {
    val containingVirtualFile = PsiUtilCore.getVirtualFile(aClass)
    return containingVirtualFile != null && ProjectFileIndex.getInstance(aClass.project).isInLibraryClasses(containingVirtualFile)
  }
}