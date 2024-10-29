// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass

internal class ExtensionRegisteredAsServiceOrComponentInspection : DevKitJvmInspection.ForClass() {

  private val serviceAttributeNames = setOf("service")

  override fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink) {
    if (!ExtensionUtil.isExtensionPointImplementationCandidate(psiClass)) {
      return
    }

    var isExtension = false
    var isService = false

    val extensionsCandidates = locateExtensionsByPsiClass(psiClass)
    val domManager = DomManager.getDomManager(project)

    for (candidate in extensionsCandidates) {
      if (isExtension && isService) break
      val tag = candidate.pointer.element ?: continue
      val element = domManager.getDomElement(tag) ?: continue
      if (element is Extension) {
        if (ExtensionUtil.hasServiceBeanFqn(element)) {
          isService = true
        }
        else if (!isValueOfServiceAttribute(tag, psiClass.qualifiedName)) {
          isExtension = true
        }
      }
    }

    if (!isExtension) {
      return
    }

    if (isService || isLightService(psiClass)) {
      return sink.highlight(DevKitBundle.message("inspection.extension.registered.as.service.message"))
    }

    if (isRegisteredComponentImplementation(psiClass)) {
      return sink.highlight(DevKitBundle.message("inspection.extension.registered.as.component.message"))
    }

    return
  }

  /**
   * Finds all attribute names with a given value and checks they all are among [serviceAttributeNames].
   */
  private fun isValueOfServiceAttribute(tag: XmlTag, value: String?): Boolean {
    val attributeNames = tag.attributes.filter { it.value == value }.map { it.name }.toSet()
    return serviceAttributeNames.containsAll(attributeNames)
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

}
