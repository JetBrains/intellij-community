// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.Actions
import org.jetbrains.idea.devkit.dom.ExtensionPoints
import org.jetbrains.idea.devkit.dom.Extensions
import org.jetbrains.idea.devkit.dom.Listeners

internal class EmptyPluginXmlTagInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (!isAllowed(holder)) return
    if (element is ExtensionPoints || element is Extensions || element is Listeners || element is Actions) {
      val xmlTag = element.xmlTag ?: return
      if (xmlTag.subTags.isEmpty()) {
        highlightRedundant(
          element,
          message("inspection.empty.plugin.xml.tag.message", xmlTag.name),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL,
          holder
        )
      }
    }
  }
}
