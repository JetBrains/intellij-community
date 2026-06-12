// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.ModuleAnalysis
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.ResolvedModuleKind
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeAnalysisFlags
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal object SplitModeInspectionUtil {
  @Nls
  fun buildMixedModuleDependenciesMessage(reasoning: @NlsSafe String): String {
    val shortMessage = DevKitBundle.message("inspection.remote.dev.mixed.dependencies.message")
    return buildDetailedPlainTextMessage(shortMessage, null, reasoning)
  }

  @Nls
  fun buildImplicitModuleKindMessage(actualModuleKind: ResolvedModuleKind): String {
    val shortMessage = when (actualModuleKind.kind) {
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND ->
        DevKitBundle.message("inspection.remote.dev.plugin.indirect.frontend.dependencies.message")
      SplitModeApiRestrictionsService.ModuleKind.BACKEND ->
        DevKitBundle.message("inspection.remote.dev.plugin.indirect.backend.dependencies.message")
      else -> error("Unsupported implicit plugin kind message: ${actualModuleKind.kind}")
    }
    return buildDetailedPlainTextMessage(shortMessage, null, actualModuleKind.reasoning)
  }

  @Nls
  fun buildModuleKindMismatchShortMessage(
    apiName: @NlsSafe String,
    expectedModuleKind: SplitModeApiRestrictionsService.ModuleKind,
    actualModuleKind: ResolvedModuleKind,
  ): String {
    val baseMessage = DevKitBundle.message(
      "inspection.api.usage.restricted.to.module.type.default.message",
      apiName,
      expectedModuleKind.id,
      actualModuleKind.kind.id,
    )
    return "$baseMessage."
  }

  @Nls
  fun buildModuleKindMismatchMessage(
    apiName: @NlsSafe String,
    expectedModuleKind: SplitModeApiRestrictionsService.ModuleKind,
    actualModuleKind: ResolvedModuleKind,
    hint: @Nls String? = null,
  ): String {
    val shortMessage = buildModuleKindMismatchShortMessage(apiName, expectedModuleKind, actualModuleKind)
    return buildDetailedPlainTextMessage(shortMessage, hint, actualModuleKind.reasoning)
  }

  fun buildModuleKindMismatchTooltipMessage(
    apiName: @NlsSafe String,
    expectedModuleKind: SplitModeApiRestrictionsService.ModuleKind,
    actualModuleKind: ResolvedModuleKind,
    hint: @Nls String? = null,
  ): String {
    val shortMessage = buildModuleKindMismatchShortMessage(apiName, expectedModuleKind, actualModuleKind)
    return buildDetailedHtmlMessage(shortMessage, hint, actualModuleKind.reasoning)
  }

  private fun buildDetailedPlainTextMessage(
    shortMessage: @Nls String,
    hint: @Nls String?,
    reasoning: @NlsSafe String,
  ): String {
    val nonBlankHint = if (hint.isNullOrBlank()) null else hint
    val hasReasoning = reasoning.isNotBlank()
    if (nonBlankHint == null && !hasReasoning) {
      return shortMessage
    }

    return buildString {
      append(shortMessage)
      if (nonBlankHint != null) {
        append("\n\n")
        append(nonBlankHint)
      }
      if (hasReasoning) {
        append("\n\n")
        append(DevKitBundle.message("inspection.remote.dev.computed.module.kind.reasoning.heading"))
        append("\n\n")
        append(reasoning)
      }
    }
  }

  private fun buildDetailedHtmlMessage(
    shortMessage: @Nls String,
    hint: @Nls String?,
    reasoning: @NlsSafe String,
  ): String {
    val escapedShortMessage = XmlStringUtil.escapeString(shortMessage)
    val nonBlankHint = if (hint.isNullOrBlank()) null else hint
    val escapedHint = if (nonBlankHint == null) null else XmlStringUtil.escapeString(nonBlankHint)
    val hasReasoning = reasoning.isNotBlank()

    return XmlStringUtil.wrapInHtml(buildString {
      append(escapedShortMessage)
      if (escapedHint != null) {
        append("<br><br>")
        append(escapedHint)
      }
      if (hasReasoning) {
        append("<br><br><b>")
        append(XmlStringUtil.escapeString(DevKitBundle.message("inspection.remote.dev.computed.module.kind.reasoning.heading")))
        append("</b><br><br>")
        append(XmlStringUtil.escapeString(reasoning).replace("\n", "<br>"))
      }
    })
  }

  fun ensureRestrictionsServiceIsLoaded(restrictionsService: SplitModeApiRestrictionsService): Boolean {
    return restrictionsService.ensureLoaded()
  }

  fun isAllowedForSplitModeInspection(file: PsiFile): Boolean {
    val restrictionsService = SplitModeApiRestrictionsService.getInstance(file.project)
    if (shouldSuppressForSingleModuleExternalPlugin(file)) {
      return false
    }
    if (!ensureRestrictionsServiceIsLoaded(restrictionsService)) {
      return false
    }
    if (shouldSuppressForPredefinedModuleKind(file, restrictionsService)) {
      return false
    }
    return true
  }

  /**
   * When a main plugin.xml becomes frontend-only or backend-only because of its dependencies implicitly
   * (and not because the author explicitly declared platform.frontend/platform.backend/platform.monolith),
   * the UI should show a single root-level plugin state warning instead of many XML-specific warnings.
   */
  fun shouldReportSinglePluginLevelErrorInsteadOfManyNestedErrors(file: PsiFile, moduleAnalysis: ModuleAnalysis): Boolean {
    return !SplitModeAnalysisFlags.isShowAllErrorsInModulesWithImplicitKind()
           && isImplicitFrontendOrBackendMainPluginXml(file, moduleAnalysis)
  }

  fun isImplicitFrontendOrBackendMainPluginXml(file: PsiFile, moduleAnalysis: ModuleAnalysis): Boolean {
    val currentXmlFile = file as? XmlFile ?: return false
    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
    return isMainPluginXml(currentXmlFile, module)
           && moduleAnalysis.resolvedModuleKind.kind in IMPLICIT_PLUGIN_XML_KINDS
           && !moduleAnalysis.evidence.hasOwnExplicitPlatformDependency
  }

  fun shouldSuppressForPredefinedModuleKind(file: PsiFile, restrictionsService: SplitModeApiRestrictionsService): Boolean {
    if (!SplitModeAnalysisFlags.isSkippingInspectionsForPredefinedModuleKindsEnabled()) {
      return false
    }

    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
    val currentDescriptorFile = file as? XmlFile
    val predefinedModuleKind = if (currentDescriptorFile != null) {
      val currentIdeaPlugin = DescriptorUtil.getIdeaPlugin(currentDescriptorFile)
      restrictionsService.getPredefinedModuleKind(module, currentDescriptorFile, currentIdeaPlugin)
    }
    else {
      val pluginXml = PluginModuleType.getPluginXml(module)
      val ideaPlugin = if (pluginXml == null) null else DescriptorUtil.getIdeaPlugin(pluginXml)
      restrictionsService.getPredefinedModuleKind(module, ideaPlugin = ideaPlugin)
    }

    return predefinedModuleKind != null
  }

  fun shouldSuppressForSingleModuleExternalPlugin(file: PsiFile): Boolean {
    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
    val project = file.project
    return CachedValuesManager.getManager(project).getCachedValue(module) {
      CachedValueProvider.Result.create(
        shouldSuppressForSingleModuleExternalPlugin(module),
        ProjectRootModificationTracker.getInstance(project),
        PsiManager.getInstance(project).modificationTracker.forLanguage(XMLLanguage.INSTANCE),
      )
    }
  }

  internal fun findDependingContentModuleEntriesInFile(contentModuleDescriptor: XmlFile): Sequence<ModuleDescriptor> {
    val moduleVirtualFile = contentModuleDescriptor.virtualFile ?: return emptySequence()
    val moduleName = moduleVirtualFile.nameWithoutExtension
    val psiManager = contentModuleDescriptor.manager
    val indexedMatches = PluginIdDependenciesIndex.findFilesIncludingContentModule(contentModuleDescriptor.project, moduleVirtualFile)
      .asSequence()
      .flatMap { dependingFile ->
        findDependingContentModuleEntries(psiManager.findFile(dependingFile) as? XmlFile, moduleName)
      }
      .toList()
    if (indexedMatches.isNotEmpty()) {
      return indexedMatches.asSequence()
    }

    val project = contentModuleDescriptor.project
    return FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScopesCore.projectProductionScope(project))
      .asSequence()
      .mapNotNull { xmlFile -> psiManager.findFile(xmlFile) as? XmlFile }
      .flatMap { pluginXml -> findDependingContentModuleEntries(pluginXml, moduleName) }
  }

  private fun findDependingContentModuleEntries(
    pluginXml: XmlFile?,
    moduleName: String,
  ): Sequence<ModuleDescriptor> {
    val plugin = pluginXml?.let(DescriptorUtil::getIdeaPlugin) ?: return emptySequence()
    return plugin.content.asSequence()
      .flatMap { contentDescriptor -> contentDescriptor.moduleEntry.asSequence() }
      .filter { moduleDescriptor -> moduleDescriptor.name.stringValue == moduleName }
  }

  private fun shouldSuppressForSingleModuleExternalPlugin(module: Module): Boolean {
    if (IntelliJProjectUtil.isIntelliJPlatformProject(module.project)) {
      return false
    }

    val pluginXml = PluginModuleType.getPluginXml(module) ?: return false
    if (PluginModuleType.getContentModuleDescriptorXml(module) != null) {
      return false
    }

    val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml) ?: return false
    return ideaPlugin.content.isEmpty() || ideaPlugin.content.all { it.moduleEntry.isEmpty() }
  }

  private fun isMainPluginXml(currentXmlFile: XmlFile, module: Module): Boolean {
    val pluginXml = PluginModuleType.getPluginXml(module) ?: return false
    if (pluginXml.virtualFile != currentXmlFile.virtualFile) {
      return false
    }

    val contentModuleDescriptor = PluginModuleType.getContentModuleDescriptorXml(module)
    return contentModuleDescriptor?.virtualFile != currentXmlFile.virtualFile
  }

  private val IMPLICIT_PLUGIN_XML_KINDS = setOf(
    SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
    SplitModeApiRestrictionsService.ModuleKind.BACKEND,
  )
}
