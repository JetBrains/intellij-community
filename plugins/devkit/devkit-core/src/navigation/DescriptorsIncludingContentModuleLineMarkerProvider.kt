// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.icons.AllIcons
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType
import com.intellij.util.NotNullFunction
import com.intellij.util.xml.DomUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevKitIcons
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class DescriptorsIncludingContentModuleLineMarkerProvider : DevkitRelatedLineMarkerProviderBase() {

  private val CONVERTER: NotNullFunction<ModuleDescriptor, Collection<PsiElement>> = NotNullFunction { listOfNotNull(it.xmlElement) }

  private val RELATED_ITEM_PROVIDER: NotNullFunction<ModuleDescriptor, MutableCollection<GotoRelatedItem>> = NotNullFunction {
    GotoRelatedItem.createItems(listOfNotNull(it.xmlElement), "DevKit")
  }

  override fun getName(): String = DevKitBundle.message("line.marker.descriptors.including.content.module.name")

  override fun getIcon() = AllIcons.Nodes.Module

  override fun collectNavigationMarkers(leaf: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>?>) {
    if (isIdeaPluginElementNameLeaf(leaf)) {
      val dependingDescriptorFiles = findDependingContentModuleEntries(leaf)
      if (dependingDescriptorFiles.isNotEmpty()) {
        result.add(createLineMarkerInfo(leaf, dependingDescriptorFiles))
      }
    }
  }

  private fun isIdeaPluginElementNameLeaf(leaf: PsiElement): Boolean {
    if (leaf !is XmlToken) return false
    if (leaf.getNode().elementType != XmlTokenType.XML_NAME) return false
    val prev = PsiTreeUtil.getPrevSiblingOfType(leaf, XmlToken::class.java)
    if (prev == null || prev.node.elementType !== XmlTokenType.XML_START_TAG_START) return false
    if (!leaf.textMatches("idea-plugin")) return false
    return DomUtil.getDomElement(leaf) is IdeaPlugin
  }

  private fun findDependingContentModuleEntries(element: PsiElement): List<ModuleDescriptor> {
    val moduleVirtualFile = element.containingFile.virtualFile ?: return emptyList()
    val moduleName = moduleVirtualFile.nameWithoutExtension
    val psiManager = element.manager
    @Suppress("UNCHECKED_CAST")
    return PluginIdDependenciesIndex.findDependsTo(element.project, moduleVirtualFile).flatMap { dependingFile ->
      val psiFile = psiManager.findFile(dependingFile) as? XmlFile ?: return@flatMap emptyList<PsiElement>()
      val plugin = DescriptorUtil.getIdeaPlugin(psiFile) ?: return@flatMap emptyList<PsiElement>()
      plugin.content.flatMap { it.moduleEntry }.filter { it.name.stringValue == moduleName }
    } as List<ModuleDescriptor>
  }

  private fun createLineMarkerInfo(
    leaf: PsiElement,
    contentEntries: List<ModuleDescriptor>,
  ): RelatedItemLineMarkerInfo<PsiElement> {
    val moduleName = leaf.containingFile.virtualFile.nameWithoutExtension
    return NavigationGutterIconBuilder.create(AllIcons.Nodes.Module, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(contentEntries)
      .setTargetRenderer { TargetRenderer() }
      .setPopupTitle(DevKitBundle.message("line.marker.descriptors.including.content.module.popup.title"))
      .setTooltipText(DevKitBundle.message("line.marker.descriptors.including.content.module.tooltip", moduleName, contentEntries.size))
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(leaf)
  }

  class TargetRenderer : PsiTargetPresentationRenderer<PsiElement>() {
    override fun getPresentation(element: PsiElement): TargetPresentation {
      val module = ProjectRootManager.getInstance(element.project).getFileIndex().getModuleForFile(element.containingFile.virtualFile)
                   ?: return super.getPresentation(element)
      return TargetPresentation.builder(element.containingFile.name)
        .icon(DevKitIcons.PluginV2)
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
}
