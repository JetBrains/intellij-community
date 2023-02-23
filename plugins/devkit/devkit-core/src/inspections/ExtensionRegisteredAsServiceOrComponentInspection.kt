// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.uast.UClass

class ExtensionRegisteredAsServiceOrComponentInspection : DevKitUastInspectionBase(UClass::class.java) {

  override fun checkClass(uClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
    val psiClass = uClass.javaPsi
    if (psiClass.classKind != JvmClassKind.CLASS ||
        PsiUtil.isInnerClass(psiClass) ||
        PsiUtil.isLocalOrAnonymousClass(psiClass) ||
        PsiUtil.isAbstractClass(psiClass)) {
      return ProblemDescriptor.EMPTY_ARRAY
    }

    var isExtension = false
    var isService = false

    val extensionsCandidates = locateExtensionsByPsiClass(psiClass)
    val domManager = DomManager.getDomManager(manager.project)

    for (candidate in extensionsCandidates) {
      if (isExtension && isService) break
      val tag = candidate.pointer.element ?: continue
      val element = domManager.getDomElement(tag) ?: continue
      if (element is Extension) {
        if (hasServiceBeanFqn(element)) {
          isService = true
        } else {
          isExtension = true
        }
      }
    }

    if (!isExtension) {
      return ProblemDescriptor.EMPTY_ARRAY
    }

    if (isService || isLightService(uClass)) {
      return registerProblem(uClass, DevKitBundle.message("inspection.extension.registered.as.service.message"), manager, isOnTheFly)
    }

    if (isRegisteredComponentImplementation(psiClass)) {
      return registerProblem(uClass, DevKitBundle.message("inspection.extension.registered.as.component.message"), manager, isOnTheFly)
    }

    return ProblemDescriptor.EMPTY_ARRAY
  }

  private fun hasServiceBeanFqn(extension: Extension): Boolean {
    return extension.extensionPoint?.beanClass?.stringValue == ServiceDescriptor::class.java.canonicalName
  }

  private fun isLightService(uClass: UClass): Boolean {
    return uClass.findAnnotation(Service::class.java.canonicalName) != null
  }

  private fun isRegisteredComponentImplementation(psiClass: PsiClass): Boolean {
    @Suppress("DEPRECATION") // BaseComponent is used for its name only
    val baseComponentFqn = com.intellij.openapi.components.BaseComponent::class.java.canonicalName
    if (!InheritanceUtil.isInheritor(psiClass, false, baseComponentFqn)) {
      return false
    }
    val types = RegistrationCheckerUtil.getRegistrationTypes(psiClass, RegistrationCheckerUtil.RegistrationType.ALL_COMPONENTS)
    return !types.isNullOrEmpty()
  }

  private fun registerProblem(uClass: UClass,
                              @InspectionMessage message: String,
                              manager: InspectionManager,
                              isOnTheFly: Boolean): Array<ProblemDescriptor?> {
    val classPsiAnchor = uClass.uastAnchor?.sourcePsi!!
    val holder = createProblemsHolder(uClass, manager, isOnTheFly)
    holder.registerProblem(classPsiAnchor, message)
    return holder.resultsArray
  }
}
