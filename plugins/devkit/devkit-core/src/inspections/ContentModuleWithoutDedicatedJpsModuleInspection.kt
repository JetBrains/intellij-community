// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.ContentDescriptor
import org.jetbrains.idea.devkit.inspections.extractModule.ExtractModuleFix

internal class ContentModuleWithoutDedicatedJpsModuleInspection : DevKitPluginXmlInspectionBase() {
  override fun checkDomElement(
    element: DomElement,
    holder: DomElementAnnotationHolder,
    helper: DomHighlightingHelper,
  ) {
    val contentModule = element as? ContentDescriptor.ModuleDescriptor ?: return
    val moduleName = contentModule.name.stringValue ?: return
    if (moduleName.contains("/")) {
      val message = DevKitBundle.message("inspection.content.module.without.dedicated.jps.module.message")
      holder.createProblem(contentModule, message, ExtractModuleFix(moduleName))
    }
  }
}
