// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.GenericAttributeValue
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService.ModuleKind
import org.jetbrains.idea.devkit.util.DescriptorUtil

private val PREVIEW_KEY: Key<Boolean> = Key.create("preview.${ModuleEntrySplitModeCodeVisionProvider.ID}")

/**
 * The split-mode kinds of the `module` entries in the settings preview file.
 */
private val PREVIEW_MODULE_KINDS: Map<String, ModuleKind> = mapOf(
  "intellij.platform.frontend" to ModuleKind.FRONTEND,
  "my.plugin.frontend" to ModuleKind.FRONTEND,
  "my.plugin.backend" to ModuleKind.BACKEND,
  "my.plugin.shared" to ModuleKind.SHARED,
)

/**
 * Shows the split-mode kind of the descriptor that a `module` entry of `content` or `dependencies` names.
 * The entry is at the end of the line of the `module` tag.
 */
internal class ModuleEntrySplitModeCodeVisionProvider : DaemonBoundCodeVisionProvider {
  companion object {
    const val ID: String = "devkit.module.entry.split.mode.kind"
  }

  override val id: String get() = ID
  override val name: String get() = DevKitBundle.message("inlay.split.mode.module.entry.kind.name")
  override val relativeOrderings: List<CodeVisionRelativeOrdering> get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Right

  /**
   * The settings preview file is not in a module, so the preview shows fixed split-mode kinds.
   */
  override fun preparePreview(editor: Editor, file: PsiFile) {
    editor.putUserData(PREVIEW_KEY, true)
  }

  override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    if (editor.getUserData(PREVIEW_KEY) == true) return computePreview(file)

    if (!DescriptorUtil.isPluginXml(file)) return emptyList()
    if (!SplitModeInspectionUtil.ensureRestrictionsServiceIsLoaded(SplitModeApiRestrictionsService.getInstance(file.project))) {
      return emptyList()
    }
    val xmlFile = file as XmlFile
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile) ?: return emptyList()
    return findModuleEntryNames(ideaPlugin).mapNotNull { name ->
      ProgressManager.checkCanceled()
      val tag = name.xmlTag ?: return@mapNotNull null
      val descriptorFile = name.value?.xmlElement?.containingFile as? XmlFile ?: return@mapNotNull null
      val info = getDescriptorSplitModeInlayInfo(descriptorFile, "inlay.split.mode.module.kind.content.module", xmlFile)
                 ?: return@mapNotNull null
      val entry = ClickableTextCodeVisionEntry(
        text = getPresentableModuleKind(info.moduleKind),
        providerId = id,
        onClick = { event, clickedEditor -> showDescriptorSplitModeReasonPopup(clickedEditor, event, info.reason) },
        tooltip = info.tooltip,
      )
      tag.textRange to entry
    }
  }

  private fun computePreview(file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    return PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).mapNotNull { tag ->
      if (tag.localName != "module") return@mapNotNull null
      val kind = PREVIEW_MODULE_KINDS[tag.getAttributeValue("name")] ?: return@mapNotNull null
      tag.textRange to TextCodeVisionEntry(text = getPresentableModuleKind(kind), providerId = id)
    }
  }
}

internal class ModuleEntrySplitModeCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
  override val groupId: String get() = ModuleEntrySplitModeCodeVisionProvider.ID
  override val groupName: String get() = DevKitBundle.message("inlay.split.mode.module.entry.kind.name")
  override val description: String get() = DevKitBundle.message("inlay.split.mode.module.entry.kind.description")
}

/**
 * Returns the `name` attributes of the `module` entries of `content` and `dependencies` in [ideaPlugin].
 */
private fun findModuleEntryNames(ideaPlugin: IdeaPlugin): List<GenericAttributeValue<IdeaPlugin>> {
  val contentNames = ideaPlugin.content.flatMap { content -> content.moduleEntry.map { it.name } }
  return contentNames + ideaPlugin.dependencies.moduleEntry.map { it.name }
}
