// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.findDependingContentModuleEntriesInFile
import org.jetbrains.idea.devkit.util.DescriptorUtil
import java.awt.event.MouseEvent
import java.util.function.Supplier

private val PREVIEW_INCLUDING_DESCRIPTOR_COUNT_KEY: Key<Int> =
  Key.create("preview.${DescriptorsIncludingContentModuleCodeVisionProvider.ID}.count")

private const val PREVIEW_INCLUDING_DESCRIPTOR_COUNT: Int = 2

/**
 * Shows the descriptors that include a content module above the root tag of the content module descriptor.
 */
internal class DescriptorsIncludingContentModuleCodeVisionProvider : DaemonBoundCodeVisionProvider {
  companion object {
    const val ID: String = "devkit.descriptors.including.content.module"
  }

  override val id: String get() = ID
  override val name: String get() = DevKitBundle.message("code.vision.descriptors.including.content.module.name")
  override val relativeOrderings: List<CodeVisionRelativeOrdering> get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Default

  /**
   * The settings preview file has no including descriptors, so the preview shows a fixed number of descriptors.
   */
  override fun preparePreview(editor: Editor, file: PsiFile) {
    editor.putUserData(PREVIEW_INCLUDING_DESCRIPTOR_COUNT_KEY, PREVIEW_INCLUDING_DESCRIPTOR_COUNT)
  }

  override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    val previewCount = editor.getUserData(PREVIEW_INCLUDING_DESCRIPTOR_COUNT_KEY)
    if (previewCount != null) return computePreview(file, previewCount)

    if (!DescriptorUtil.isPluginXml(file) || !DevKitInspectionUtil.isAllowed(file)) return emptyList()
    val xmlFile = file as XmlFile
    val rootTag = DescriptorUtil.getIdeaPlugin(xmlFile)?.xmlTag ?: return emptyList()
    val moduleName = xmlFile.virtualFile?.nameWithoutExtension ?: return emptyList()
    val includingDescriptors = findDependingContentModuleEntriesInFile(xmlFile).toList()
    val includingEntries = includingDescriptors.mapNotNull { it.xmlElement }
    if (includingEntries.isEmpty()) return emptyList()

    val pointers = includingEntries.map { it.createSmartPointer() }
    val entry = ClickableTextCodeVisionEntry(
      text = getEntryText(includingDescriptors),
      providerId = id,
      onClick = { event, clickedEditor -> navigateToIncludingEntries(pointers, event, clickedEditor) },
      tooltip = DevKitBundle.message("code.vision.descriptors.including.content.module.tooltip", moduleName, includingEntries.size),
    )
    return listOf(rootTag.textRange to entry)
  }

  private fun getEntryText(includingDescriptors: List<ModuleDescriptor>): @Nls String {
    val pluginId = includingDescriptors.singleOrNull()?.let { DomUtil.getParentOfType(it, IdeaPlugin::class.java, true) }?.pluginId
    if (pluginId != null) {
      return DevKitBundle.message("code.vision.descriptors.including.content.module.plugin.text", pluginId)
    }
    return DevKitBundle.message("code.vision.descriptors.including.content.module.text", includingDescriptors.size)
  }

  private fun computePreview(file: PsiFile, count: Int): List<Pair<TextRange, CodeVisionEntry>> {
    val rootTag = (file as? XmlFile)?.rootTag ?: return emptyList()
    val entry = ClickableTextCodeVisionEntry(
      text = DevKitBundle.message("code.vision.descriptors.including.content.module.text", count),
      providerId = id,
      onClick = { _, _ -> },
    )
    return listOf(rootTag.textRange to entry)
  }

  private fun navigateToIncludingEntries(pointers: List<SmartPsiElementPointer<out PsiElement>>, event: MouseEvent?, editor: Editor) {
    val project = editor.project ?: return
    val navigator = PsiTargetNavigator<PsiElement>(Supplier { pointers.mapNotNull { it.element } })
      .presentationProvider(IncludingDescriptorTargetRenderer())
    val title = DevKitBundle.message("code.vision.descriptors.including.content.module.popup.title")
    if (event != null) {
      navigator.navigate(event, title, project)
    }
    else {
      navigator.navigate(editor, title)
    }
  }
}

internal class DescriptorsIncludingContentModuleCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
  override val groupId: String get() = DescriptorsIncludingContentModuleCodeVisionProvider.ID
  override val groupName: String get() = DevKitBundle.message("code.vision.descriptors.including.content.module.name")
  override val description: String get() = DevKitBundle.message("code.vision.descriptors.including.content.module.description")
}
