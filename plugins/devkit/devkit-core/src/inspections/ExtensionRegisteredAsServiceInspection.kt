// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.uast.UClass

class ExtensionRegisteredAsServiceInspection : DevKitUastInspectionBase(UClass::class.java) {

  override fun checkClass(uClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
    val psiClass = uClass.javaPsi
    val extensionsCandidates = locateExtensionsByPsiClass(psiClass)
    val domManager = DomManager.getDomManager(manager.project)

    val extensions = extensionsCandidates
      .mapNotNull { it.pointer.element }
      .mapNotNull { domManager.getDomElement(it) }
      .filterIsInstance<Extension>()

    val isService = extensions.any { hasServiceBeanFqn(it) }
    val isExtension = extensions.any { !hasServiceBeanFqn(it) }
    val isLightService = uClass.findAnnotation(Service::class.java.canonicalName) != null

    if (isExtension && (isService || isLightService)) {
      val classPsiAnchor = uClass.uastAnchor?.sourcePsi!!
      val holder = createProblemsHolder(uClass, manager, isOnTheFly)
      holder.registerProblem(classPsiAnchor, DevKitBundle.message("inspection.extension.registered.as.service.message"))
      return holder.resultsArray
    }

    return ProblemDescriptor.EMPTY_ARRAY
  }

  private fun hasServiceBeanFqn(extension: Extension): Boolean {
    return extension.extensionPoint?.beanClass?.stringValue == ServiceDescriptor::class.java.canonicalName
  }
}
