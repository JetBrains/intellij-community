// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.scaffolding

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.ui.icons.IconWrapperWithToolTip
import org.jetbrains.idea.devkit.DevKitIcons
import java.util.function.Supplier
import javax.swing.Icon

internal class ScaffoldingDirectoryIconProvider : IconProvider() {

  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    if (!Registry.`is`("devkit.plugin.directory.icons")) {
      return null
    }
    if (element is PsiDirectory) {
      val project = element.project
      if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
        val kind = directoryKind(project, element.virtualFile)
        return when (kind) {
          DirectoryKind.PLUGIN -> pluginDirectoryIcon
          DirectoryKind.LEGACY_PLUGIN_WITH_MAIN_MODULE -> legacyPluginWithModuleIcon
          DirectoryKind.MODULE -> moduleDirectoryIcon
          null -> null
        }
      }
    }
    return null
  }
}

private val pluginDirectoryIcon = AllIcons.Nodes.Plugin
  .withTooltip(messagePointer("plugin.directory.tooltip"))

private val moduleDirectoryIcon = DevKitIcons.PluginModule
  .withTooltip(messagePointer("module.directory.tooltip"))

private val legacyPluginWithModuleIcon = DevKitIcons.LegacyPluginModule
  .withTooltip(messagePointer("plugin.and.module.directory.tooltip"))

private fun Icon.withTooltip(message: Supplier<String>): Icon {
  return IconWrapperWithToolTip(this, message)
}
