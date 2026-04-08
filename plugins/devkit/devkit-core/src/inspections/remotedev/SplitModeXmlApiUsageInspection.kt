// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.Extensions
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeModuleKindResolver.doesApiKindMatchExpectedModuleKind

internal class SplitModeXmlApiUsageInspection : DevKitPluginXmlInspectionBase() {
  private val restrictionsService = SplitModeApiRestrictionsService.getInstance()

  override fun isAllowed(holder: DomElementAnnotationHolder): Boolean {
    if (!super.isAllowed(holder)) return false

    if (restrictionsService.isLoaded()) {
      return true
    }

    restrictionsService.scheduleLoadRestrictions()
    return false
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is Extension && element !is Extensions.UnresolvedExtension) return
    if (!isAllowed(holder)) return

    val extensionPointName = getExtensionPointName(element) ?: return
    val expectedModuleKind = restrictionsService.getExtensionPointKind(extensionPointName) ?: return
    val xmlTag = element.xmlTag ?: return
    val actualModuleKind = SplitModeModuleKindResolver.getOrComputeModuleKind(xmlTag)

    if (doesApiKindMatchExpectedModuleKind(actualModuleKind, expectedModuleKind)) return

    holder.createProblem(
      element,
      DevKitBundle.message(
        "inspection.api.usage.restricted.to.module.type.default.message",
        extensionPointName,
        expectedModuleKind.presentableName
      )
    )
  }

  private fun getExtensionPointName(element: DomElement): String? {
    if (element is Extension) {
      element.extensionPoint?.effectiveQualifiedName?.let { return it }
    }

    val extensions = element.getParentOfType(Extensions::class.java, true) ?: return null
    return extensions.epPrefix + element.xmlElementName
  }
}
