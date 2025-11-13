// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.PluginModule

internal class WrongPluginModuleAliasInspection : DevKitPluginXmlInspectionBase() {

  private val reservedPrefix = "com.intellij."

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    val module = element as? PluginModule ?: return
    if (IntelliJProjectUtil.isIntelliJPlatformProject(module.xmlElement?.project) || isDevelopedByJetBrains(element)) return

    val value: GenericAttributeValue<String> = module.value
    val alias = value.value ?: return
    if (alias.startsWith(reservedPrefix)) {
      holder.createProblem(value, message("inspection.plugin.module.alias.message"))
    }
  }

  private fun isDevelopedByJetBrains(element: PluginModule): Boolean {
    val vendor = DomUtil.findDomElement(element.xmlElement, IdeaPlugin::class.java)?.vendor ?: return false
    return PluginManagerCore.isDevelopedByJetBrains(vendor.stringValue)
  }

}
