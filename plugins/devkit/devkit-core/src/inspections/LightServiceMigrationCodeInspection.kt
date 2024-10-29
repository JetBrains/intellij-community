// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.*
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.util.JvmInheritanceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToLightServiceFix
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.uast.UClass

internal class LightServiceMigrationCodeInspection : DevKitUastInspectionBase(UClass::class.java) {

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val psiClass = aClass.javaPsi
    if (!aClass.isFinal || !ExtensionUtil.isExtensionPointImplementationCandidate(psiClass)) {
      return ProblemDescriptor.EMPTY_ARRAY
    }
    if (isVersion193OrHigher(psiClass) || ApplicationManager.getApplication().isUnitTestMode) {
      if (isLightService(psiClass)) return ProblemDescriptor.EMPTY_ARRAY
      val candidate = locateExtensionsByPsiClass(psiClass).singleOrNull() ?: return ProblemDescriptor.EMPTY_ARRAY
      val extension = DomUtil.findDomElement(candidate.pointer.element, Extension::class.java, false)
                      ?: return ProblemDescriptor.EMPTY_ARRAY
      val (serviceImplementation, level) = getServiceImplementation(extension) ?: return ProblemDescriptor.EMPTY_ARRAY
      if (level == Service.Level.APP &&
          JvmInheritanceUtil.isInheritor(aClass, PersistentStateComponent::class.java.canonicalName)) {
        return ProblemDescriptor.EMPTY_ARRAY
      }
      if (serviceImplementation == psiClass && !containsUnitTestOrHeadlessModeCheck(aClass)) {
        val fixes = arrayOf<LocalQuickFix>(ConvertToLightServiceFix(psiClass, extension.xmlTag, level))
        return registerProblem(aClass, manager, isOnTheFly, fixes)
      }
    }
    return ProblemDescriptor.EMPTY_ARRAY
  }

  private fun registerProblem(aClass: UClass,
                              manager: InspectionManager,
                              isOnTheFly: Boolean,
                              fixes: Array<LocalQuickFix>): Array<ProblemDescriptor> {
    val holder = createProblemsHolder(aClass, manager, isOnTheFly)
    holder.registerUProblem(aClass, DevKitBundle.message("inspection.light.service.migration.message"), *fixes)
    return holder.resultsArray
  }
}