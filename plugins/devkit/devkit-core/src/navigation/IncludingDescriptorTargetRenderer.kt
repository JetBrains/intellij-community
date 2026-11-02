// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.icons.AllIcons
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.util.xml.DomUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindIcons

/**
 * Renders a `<module>` entry of a descriptor that includes a content module.
 */
internal class IncludingDescriptorTargetRenderer : PsiTargetPresentationRenderer<PsiElement>() {
  override fun getPresentation(element: PsiElement): TargetPresentation {
    val module = ProjectRootManager.getInstance(element.project).getFileIndex().getModuleForFile(element.containingFile.virtualFile)
                 ?: return super.getPresentation(element)
    return TargetPresentation.builder(element.containingFile.name)
      .icon(SplitModeModuleKindIcons.getDescriptorIcon(element.containingFile) ?: DevkitCoreIcons.PluginV2)
      .containerText(getLoading(element))
      .locationText(module.name, AllIcons.Nodes.Module)
      .presentation()
  }

  private fun getLoading(element: PsiElement): @Nls String {
    val moduleDescriptor = DomUtil.getDomElement(element) as? ModuleDescriptor
                           ?: throw IllegalStateException("ModuleDescriptor expected, got: $element")
    return moduleDescriptor.loading.value?.value ?: "optional"
  }
}
