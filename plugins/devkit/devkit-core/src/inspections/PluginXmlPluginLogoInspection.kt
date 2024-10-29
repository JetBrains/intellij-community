// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.IdeaPlugin

internal class PluginXmlPluginLogoInspection : DevKitPluginXmlInspectionBase() {

  @NonNls
  private val pluginIconFileName: String = "pluginIcon.svg"

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is IdeaPlugin) return;

    if (!isAllowed(holder)) return

    if (!element.hasRealPluginId()) return

    val module = element.module ?: return
    if (!isUnderProductionSources(element, module)) return
    if (true == element.getImplementationDetail().getValue()) return

    val pluginIconFiles =
      FilenameIndex.getVirtualFilesByName(pluginIconFileName, GlobalSearchScope.moduleScope(module))
    if (pluginIconFiles.isEmpty()) {
      holder.createProblem(element,
                           DevKitBundle.message("inspections.plugin.xml.no.plugin.icon.svg.file", pluginIconFileName))
    }
  }
}