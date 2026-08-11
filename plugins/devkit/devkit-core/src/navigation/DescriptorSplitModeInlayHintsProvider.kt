// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType
import com.intellij.xml.util.XmlUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.findDependingContentModuleEntriesInFile
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService.ModuleKind
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.recognizeSplitModeModuleKind
import org.jetbrains.idea.devkit.module.PluginModuleType

internal class DescriptorSplitModeInlayHintsProvider : InlayHintsProvider {

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    if (!isDescriptorSplitModeInlayEnabled()) return null
    val xmlFile = file as? XmlFile ?: return null
    if (findIdeaPluginRootTagNameToken(xmlFile) == null) return null
    return DescriptorSplitModeInlayHintsCollector()
  }

  private class DescriptorSplitModeInlayHintsCollector : OwnBypassCollector {
    override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
      val xmlFile = file as? XmlFile ?: return
      val tagNameToken = findIdeaPluginRootTagNameToken(xmlFile) ?: return
      val inlayInfo = getDescriptorSplitModeInlayInfo(xmlFile) ?: return

      sink.addPresentation(
        InlineInlayPosition(tagNameToken.textRange.endOffset, true),
        tooltip = inlayInfo.tooltip,
        hintFormat = HintFormat.default,
      ) {
        text(inlayInfo.text)
      }
    }

    override fun shouldSuggestToggling(project: Project, editor: Editor, file: PsiFile): Boolean {
      return isDescriptorSplitModeInlayEnabled() && file is XmlFile && findIdeaPluginRootTagNameToken(file) != null
    }
  }
}

internal data class DescriptorSplitModeInlayInfo(
  val text: String,
  val tooltip: String,
)

internal fun getDescriptorSplitModeInlayInfo(xmlFile: XmlFile): DescriptorSplitModeInlayInfo? {
  val recognizedModuleKind = recognizeSplitModeModuleKind(xmlFile) ?: return null
  val descriptorKindMessageKey = getDescriptorKindMessageKey(xmlFile)
  val descriptorKind = DevKitBundle.message(descriptorKindMessageKey)
  return DescriptorSplitModeInlayInfo(
    DevKitBundle.message(
      "inlay.split.mode.module.kind.presentation",
      getPresentableModuleKind(recognizedModuleKind.kind),
      descriptorKind,
    ),
    DevKitBundle.message(
      "inlay.split.mode.module.kind.tooltip",
      descriptorKind,
      getCompatibilityTarget(recognizedModuleKind.kind),
      normalizeTooltipReasoning(recognizedModuleKind.reasoning),
    ),
  )
}

private fun normalizeTooltipReasoning(reasoning: String): String {
  return reasoning.lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
}

private fun isDescriptorSplitModeInlayEnabled(): Boolean {
  return Registry.`is`("devkit.split.mode.inlay.plugin.descriptor.kind")
}

private fun getPresentableModuleKind(kind: ModuleKind): String {
  val presentableId = when (kind) {
    ModuleKind.FRONTEND -> ModuleKind.FRONTEND.id
    ModuleKind.BACKEND -> ModuleKind.BACKEND.id
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
    ModuleKind.SHARED -> "inlay.split.mode.module.kind.compatibility.shared"
    ModuleKind.MONOLITH,
    ModuleKind.MIXED,
      -> "inlay.split.mode.module.kind.compatibility.monolith"
    is ModuleKind.Composite -> "inlay.split.mode.module.kind.compatibility.shared"
  }
  return DevKitBundle.message(messageKey)
}

private fun getDescriptorKindMessageKey(xmlFile: XmlFile): String {
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

private fun findIdeaPluginRootTagNameToken(xmlFile: XmlFile): XmlToken? {
  val rootTag = xmlFile.rootTag ?: return null
  val tagNameToken = XmlUtil.getTokenOfType(rootTag, XmlTokenType.XML_NAME) ?: return null
  return tagNameToken.takeIf(::isIdeaPluginElementNameLeaf)
}
