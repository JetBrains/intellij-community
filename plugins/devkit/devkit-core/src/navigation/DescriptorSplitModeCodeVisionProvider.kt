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
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.findDependingContentModuleEntriesInFile
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService.ModuleKind
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil

private val PREVIEW_MODULE_KIND_KEY: Key<ModuleKind> = Key.create("preview.${DescriptorSplitModeCodeVisionProvider.ID}.kind")

/**
 * Shows the split-mode kind of a plugin descriptor above its root tag.
 */
internal class DescriptorSplitModeCodeVisionProvider : DaemonBoundCodeVisionProvider {
  companion object {
    const val ID: String = "devkit.plugin.descriptor.split.mode.kind"
  }

  override val id: String get() = ID
  override val name: String get() = DevKitBundle.message("inlay.split.mode.module.kind.name")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore(DescriptorsIncludingContentModuleCodeVisionProvider.ID))
  override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Default

  /**
   * The settings preview file is not in a module, so the preview shows a fixed split-mode kind.
   */
  override fun preparePreview(editor: Editor, file: PsiFile) {
    editor.putUserData(PREVIEW_MODULE_KIND_KEY, ModuleKind.BACKEND)
  }

  override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    val previewModuleKind = editor.getUserData(PREVIEW_MODULE_KIND_KEY)
    if (previewModuleKind != null) return computePreview(file, previewModuleKind)

    if (!DescriptorUtil.isPluginXml(file)) return emptyList()
    if (!SplitModeInspectionUtil.ensureRestrictionsServiceIsLoaded(SplitModeApiRestrictionsService.getInstance(file.project))) {
      return emptyList()
    }
    val xmlFile = file as XmlFile
    val rootTag = DescriptorUtil.getIdeaPlugin(xmlFile)?.xmlTag ?: return emptyList()
    val info = getDescriptorSplitModeInlayInfo(xmlFile) ?: return emptyList()
    val entry = ClickableTextCodeVisionEntry(
      text = info.text,
      providerId = id,
      onClick = { event, clickedEditor -> showDescriptorSplitModeReasonPopup(clickedEditor, event, info.reason) },
      tooltip = info.tooltip,
    )
    return listOf(rootTag.textRange to entry)
  }

  private fun computePreview(file: PsiFile, moduleKind: ModuleKind): List<Pair<TextRange, CodeVisionEntry>> {
    val rootTag = (file as? XmlFile)?.rootTag ?: return emptyList()
    val text = DevKitBundle.message(
      "inlay.split.mode.module.kind.presentation",
      getPresentableModuleKind(moduleKind),
      DevKitBundle.message("inlay.split.mode.module.kind.plugin"),
    )
    return listOf(rootTag.textRange to TextCodeVisionEntry(text = text, providerId = id))
  }
}

internal class DescriptorSplitModeCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
  override val groupId: String get() = DescriptorSplitModeCodeVisionProvider.ID
  override val groupName: String get() = DevKitBundle.message("inlay.split.mode.module.kind.name")
  override val description: String get() = DevKitBundle.message("inlay.split.mode.module.kind.description")
}

/**
 * @property moduleKind the resolved split-mode kind of the descriptor.
 * @property reason the content of the popup that opens on a click on the inlay.
 */
internal data class DescriptorSplitModeInlayInfo(
  val moduleKind: ModuleKind,
  val text: @Nls String,
  val tooltip: @NlsContexts.Tooltip String,
  val reason: DescriptorSplitModeReason,
)

/**
 * @param descriptorKindMessageKey the message key of the descriptor kind, for example "content module".
 * A caller that knows the descriptor kind gives it, because the default computation can search all XML files of the project.
 * @param currentFile the descriptor in the editor. The reason popup shows it without a link.
 */
internal fun getDescriptorSplitModeInlayInfo(
  xmlFile: XmlFile,
  descriptorKindMessageKey: @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String = getDescriptorKindMessageKey(xmlFile),
  currentFile: XmlFile = xmlFile,
): DescriptorSplitModeInlayInfo? {
  val module = ModuleUtilCore.findModuleForPsiElement(xmlFile) ?: return null
  val resolvedModuleKind = SplitModeModuleKindResolver.getOrComputeDescriptorAnalysis(xmlFile).resolvedModuleKind
  val descriptorKind = DevKitBundle.message(descriptorKindMessageKey)
  val text = DevKitBundle.message(
    "inlay.split.mode.module.kind.presentation",
    getPresentableModuleKind(resolvedModuleKind.kind),
    descriptorKind,
  )
  val compatibilityTarget = getCompatibilityTarget(resolvedModuleKind.kind)
  val analyzedDescriptor = DescriptorLink(xmlFile.name, module.name)
  val currentDescriptor =
    if (currentFile == xmlFile) analyzedDescriptor
    else ModuleUtilCore.findModuleForPsiElement(currentFile)?.let { DescriptorLink(currentFile.name, it.name) }
  return DescriptorSplitModeInlayInfo(
    moduleKind = resolvedModuleKind.kind,
    text = text,
    tooltip = DevKitBundle.message(
      "inlay.split.mode.module.kind.tooltip",
      descriptorKind,
      compatibilityTarget,
      normalizeTooltipReasoning(resolvedModuleKind.reasoning),
    ),
    reason = DescriptorSplitModeReason(
      presentation = text,
      compatibilityTarget = compatibilityTarget,
      analyzedDescriptor = analyzedDescriptor,
      currentDescriptor = currentDescriptor,
      analyzedPluginId = DescriptorUtil.getIdeaPlugin(xmlFile)?.pluginId,
      reason = resolvedModuleKind.reason,
    ),
  )
}

private fun normalizeTooltipReasoning(reasoning: String): String {
  return reasoning.lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
}

internal fun getPresentableModuleKind(kind: ModuleKind): @NlsSafe String {
  val presentableId = when (kind) {
    ModuleKind.FRONTEND -> ModuleKind.FRONTEND.id
    ModuleKind.BACKEND -> ModuleKind.BACKEND.id
    ModuleKind.LITE_FRONTEND -> ModuleKind.LITE_FRONTEND.id
    ModuleKind.SHARED -> ModuleKind.SHARED.id
    ModuleKind.MONOLITH,
    ModuleKind.MIXED,
      -> ModuleKind.MONOLITH.id
    is ModuleKind.Composite -> kind.id
  }
  return StringUtil.capitalize(presentableId)
}

private fun getCompatibilityTarget(kind: ModuleKind): String {
  val messageKey = when (kind) {
    ModuleKind.FRONTEND -> "inlay.split.mode.module.kind.compatibility.frontend"
    ModuleKind.BACKEND -> "inlay.split.mode.module.kind.compatibility.backend"
    ModuleKind.LITE_FRONTEND -> "inlay.split.mode.module.kind.compatibility.lite.frontend"
    ModuleKind.SHARED -> "inlay.split.mode.module.kind.compatibility.shared"
    ModuleKind.MONOLITH,
    ModuleKind.MIXED,
      -> "inlay.split.mode.module.kind.compatibility.monolith"
    is ModuleKind.Composite -> "inlay.split.mode.module.kind.compatibility.shared"
  }
  return DevKitBundle.message(messageKey)
}

private fun getDescriptorKindMessageKey(xmlFile: XmlFile): @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String {
  if (xmlFile.name == "plugin.xml") {
    return "inlay.split.mode.module.kind.plugin"
  }
  if (isContentModuleDescriptor(xmlFile)) {
    return "inlay.split.mode.module.kind.content.module"
  }
  return "inlay.split.mode.module.kind.xml.descriptor"
}

private fun isContentModuleDescriptor(xmlFile: XmlFile): Boolean {
  val module = ModuleUtilCore.findModuleForPsiElement(xmlFile)
  if (PluginModuleType.getContentModuleDescriptorXml(module)?.virtualFile == xmlFile.virtualFile) {
    return true
  }
  if (module != null && xmlFile.name in computePossibleContentModuleDescriptorNames(module.name)) {
    return true
  }
  return findDependingContentModuleEntriesInFile(xmlFile).any()
}

private fun computePossibleContentModuleDescriptorNames(moduleName: String): List<String> {
  return listOf(
    "$moduleName.xml",
    "${moduleName.replace(".main", "")}.xml",
    "${moduleName.replace("_", ".").replace(".main", "")}.xml",
  )
}
