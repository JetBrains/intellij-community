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
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.findDependingContentModuleEntriesInFile
import org.jetbrains.idea.devkit.util.DescriptorUtil
import java.awt.event.MouseEvent
import java.util.function.Supplier

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
  override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Top

  override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    if (!DescriptorUtil.isPluginXml(file) || !DevKitInspectionUtil.isAllowed(file)) return emptyList()
    val xmlFile = file as XmlFile
    val rootTag = DescriptorUtil.getIdeaPlugin(xmlFile)?.xmlTag ?: return emptyList()
    val moduleName = xmlFile.virtualFile?.nameWithoutExtension ?: return emptyList()
    val includingEntries = findDependingContentModuleEntriesInFile(xmlFile).mapNotNull { it.xmlElement }.toList()
    if (includingEntries.isEmpty()) return emptyList()

    val pointers = includingEntries.map { it.createSmartPointer() }
    val entry = ClickableTextCodeVisionEntry(
      text = DevKitBundle.message("code.vision.descriptors.including.content.module.text", includingEntries.size),
      providerId = id,
      onClick = { event, clickedEditor -> navigateToIncludingEntries(pointers, event, clickedEditor) },
      tooltip = DevKitBundle.message("code.vision.descriptors.including.content.module.tooltip", moduleName, includingEntries.size),
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
