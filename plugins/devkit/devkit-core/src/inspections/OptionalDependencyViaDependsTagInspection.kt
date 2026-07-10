// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Dependency
import org.jetbrains.idea.devkit.inspections.extractModule.ExtractContentModuleFix

internal class OptionalDependencyViaDependsTagInspection : DevKitPluginXmlInspectionBase() {
  override fun checkDomElement(
    element: DomElement,
    holder: DomElementAnnotationHolder,
    helper: DomHighlightingHelper,
  ) {
    val dependsTag = element as? Dependency ?: return
    if (dependsTag.optional.value == true) {
      val configFileName = dependsTag.configFile.value?.path
      val fixes =
        if (configFileName != null && IntelliJProjectUtil.isIntelliJPlatformProject(element.xmlElement?.project))
          arrayOf(ExtractContentModuleFix(configFileName))
        else
          emptyArray()
      holder.createProblem(dependsTag.optional, DevKitBundle.message("inspection.optional.dependency.declared.by.depends.tag.message"), *fixes)
    }
  }
}
