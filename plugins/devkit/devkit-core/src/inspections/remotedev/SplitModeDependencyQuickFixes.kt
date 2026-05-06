// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.getExplicitPlatformDependencyName
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.resolveDependencyKind
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object SplitModeDependencyQuickFixes {
  fun createMismatchFixes(
    module: Module,
    currentDescriptor: IdeaPlugin?,
    desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Array<LocalQuickFix> {
    val desiredSplitKinds = desiredModuleKind.toFixableSplitKinds()
    if (desiredSplitKinds.isEmpty()) {
      return emptyArray()
    }

    val availableDependencies = getRuntimeDependencies(module, currentDescriptor)
    val fixes = mutableListOf<LocalQuickFix>()
    for (splitKind in desiredSplitKinds) {
      if (shouldOfferMakeOnlyDependenciesFix(availableDependencies, splitKind)) {
        fixes.add(MakeModuleOnlyKindDependenciesFix(module.name, splitKind))
      }
      if (shouldOfferAddExplicitDependencyFix(availableDependencies, splitKind)) {
        fixes.add(AddExplicitPlatformDependencyFix(module.name, splitKind))
      }
    }
    if (shouldOfferMonolithDependencyFix(availableDependencies)) {
      fixes.add(AddExplicitPlatformDependencyFix(module.name, SplitModeApiRestrictionsService.ModuleKind.MONOLITH))
    }
    return fixes.toTypedArray()
  }

  fun createMixedModuleFixes(module: Module, currentDescriptor: IdeaPlugin?): Array<LocalQuickFix> {
    val availableDependencies = getRuntimeDependencies(module, currentDescriptor)
    val fixes = mutableListOf<LocalQuickFix>(
      MakeModuleOnlyKindDependenciesFix(module.name, SplitModeApiRestrictionsService.ModuleKind.FRONTEND),
      MakeModuleOnlyKindDependenciesFix(module.name, SplitModeApiRestrictionsService.ModuleKind.BACKEND),
    )
    if (shouldOfferMonolithDependencyFix(availableDependencies)) {
      fixes.add(AddExplicitPlatformDependencyFix(module.name, SplitModeApiRestrictionsService.ModuleKind.MONOLITH))
    }
    return fixes.toTypedArray()
  }

  fun createAddExplicitDependencyFix(moduleName: String, moduleKind: SplitModeApiRestrictionsService.ModuleKind): LocalQuickFix {
    return AddExplicitPlatformDependencyFix(moduleName, moduleKind)
  }

  private fun shouldOfferMakeOnlyDependenciesFix(
    availableDependencies: DependenciesForFixAvailability,
    desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Boolean {
    return when (desiredModuleKind) {
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
      SplitModeApiRestrictionsService.ModuleKind.BACKEND,
        -> hasRuntimeDependencyToRemove(availableDependencies, desiredModuleKind)
            || hasCompileDependencyToRemove(availableDependencies, desiredModuleKind)
      SplitModeApiRestrictionsService.ModuleKind.MONOLITH,
      SplitModeApiRestrictionsService.ModuleKind.MIXED,
      SplitModeApiRestrictionsService.ModuleKind.SHARED,
      is SplitModeApiRestrictionsService.ModuleKind.Composite,
        -> false
    }
  }

  private fun shouldOfferAddExplicitDependencyFix(
    availableDependencies: DependenciesForFixAvailability,
    desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Boolean {
    return when (desiredModuleKind) {
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> {
        !hasRuntimeDependencyToRemove(availableDependencies, desiredModuleKind)
        && !hasCompileDependencyToRemove(availableDependencies, desiredModuleKind)
        && isExplicitDependencyActionable(availableDependencies, desiredModuleKind)
      }
      SplitModeApiRestrictionsService.ModuleKind.BACKEND -> {
        !hasRuntimeDependencyToRemove(availableDependencies, desiredModuleKind)
        && !hasCompileDependencyToRemove(availableDependencies, desiredModuleKind)
        && isExplicitDependencyActionable(availableDependencies, desiredModuleKind)
      }
      SplitModeApiRestrictionsService.ModuleKind.MONOLITH,
      SplitModeApiRestrictionsService.ModuleKind.MIXED,
      SplitModeApiRestrictionsService.ModuleKind.SHARED,
      is SplitModeApiRestrictionsService.ModuleKind.Composite,
        -> false
    }
  }

  private fun shouldOfferMonolithDependencyFix(availableDependencies: DependenciesForFixAvailability): Boolean {
    return hasRuntimeDependencyToRemove(availableDependencies, SplitModeApiRestrictionsService.ModuleKind.MONOLITH)
           || hasCompileDependencyToRemove(availableDependencies, SplitModeApiRestrictionsService.ModuleKind.MONOLITH)
           || isExplicitDependencyActionable(availableDependencies, SplitModeApiRestrictionsService.ModuleKind.MONOLITH)
  }

  private fun hasRuntimeDependencyToRemove(
    availableDependencies: DependenciesForFixAvailability,
    desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Boolean {
    return availableDependencies.runtimeDependencies.any { shouldRemoveDependency(it, desiredModuleKind) }
  }

  private fun hasCompileDependencyToRemove(
    availableDependencies: DependenciesForFixAvailability,
    desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Boolean {
    return availableDependencies.compileDependencies.any { shouldRemoveDependency(it, desiredModuleKind) }
  }

  private fun isExplicitDependencyActionable(
    availableDependencies: DependenciesForFixAvailability,
    desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Boolean {
    val dependencyName = getExplicitPlatformDependencyName(desiredModuleKind)
    return dependencyName !in availableDependencies.runtimeDependencies || dependencyName !in availableDependencies.compileDependencies
  }

  private class MakeModuleOnlyKindDependenciesFix(
    private val moduleName: String,
    private val desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ) : LocalQuickFix {

    override fun getName(): String {
      return message("inspection.remote.dev.make.module.work.in.kind.only.fix.name", moduleName, desiredModuleKind.id)
    }

    override fun getFamilyName(): String {
      return message("inspection.remote.dev.make.only.kind.dependencies.fix.name", desiredModuleKind.id)
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val ideaPlugin = findQuickFixTargetDescriptor(descriptor) ?: return
      val module = findTargetModule(descriptor)
      val xmlFile = ideaPlugin.xmlElement?.containingFile ?: return
      if (!IntentionPreviewUtils.prepareElementForWrite(xmlFile)) return

      applyResolvedDependenciesFix(project, ideaPlugin, module, desiredModuleKind)
    }
  }

  private class AddExplicitPlatformDependencyFix(
    private val moduleName: String,
    private val desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ) : LocalQuickFix {
    private val explicitDependencyName = getExplicitPlatformDependencyName(desiredModuleKind)

    override fun getName(): String {
      return message("inspection.remote.dev.make.module.work.in.kind.only.fix.name", moduleName, desiredModuleKind.id)
    }

    override fun getFamilyName(): String {
      return message("inspection.remote.dev.missing.runtime.dependency.fix.add", explicitDependencyName)
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val ideaPlugin = findQuickFixTargetDescriptor(descriptor) ?: return
      val module = findTargetModule(descriptor)
      val xmlFile = ideaPlugin.xmlElement?.containingFile ?: return
      if (!IntentionPreviewUtils.prepareElementForWrite(xmlFile)) return

      if (desiredModuleKind == SplitModeApiRestrictionsService.ModuleKind.FRONTEND
          || desiredModuleKind == SplitModeApiRestrictionsService.ModuleKind.BACKEND) {
        applyResolvedDependenciesFix(project, ideaPlugin, module, desiredModuleKind)
      }
      else {
        removeInappropriateDependencies(ideaPlugin, module, desiredModuleKind)
        if (!hasDirectDependency(ideaPlugin, explicitDependencyName)) {
          val newModuleEntry = ideaPlugin.dependencies.addModuleEntry()
          newModuleEntry.name.stringValue = explicitDependencyName
        }

        addModuleDependency(module, explicitDependencyName)
        if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          runJpsToBazelConverter(project)
        }
      }
    }
  }
}

private data class DependenciesForFixAvailability(
  val runtimeDependencies: Set<String>,
  val compileDependencies: Set<String>,
)

private data class DependenciesRemovalPlan(
  val runtimeDependenciesToRemove: Set<String>,
  val compileDependenciesToRemove: Set<String>,
)

private fun getRuntimeDependencies(module: Module, currentDescriptor: IdeaPlugin?): DependenciesForFixAvailability {
  val targetDescriptor = currentDescriptor ?: findFallbackDescriptorForQuickFix(module)
  return DependenciesForFixAvailability(
    runtimeDependencies = getRuntimeDependencies(targetDescriptor),
    compileDependencies = getCompileDependencies(module),
  )
}

private fun getRuntimeDependencies(ideaPlugin: IdeaPlugin?): Set<String> {
  if (ideaPlugin == null) {
    return emptySet()
  }

  return sequence {
    for (dependency in ideaPlugin.depends) {
      val dependencyName = dependency.rawText ?: dependency.stringValue ?: continue
      yield(dependencyName)
    }

    val dependencies = ideaPlugin.dependencies
    if (!dependencies.isValid) {
      return@sequence
    }

    for (moduleEntry in dependencies.moduleEntry) {
      val dependencyName = moduleEntry.name.stringValue ?: continue
      yield(dependencyName)
    }

    for (pluginEntry in dependencies.plugin) {
      val dependencyName = pluginEntry.id.stringValue ?: continue
      yield(dependencyName)
    }
  }.toSet()
}

private fun getCompileDependencies(module: Module): Set<String> {
  return ModuleRootManager.getInstance(module).orderEntries
    .asSequence()
    .filterIsInstance<ModuleOrderEntry>()
    .map { it.moduleName }
    .toSet()
}

private fun removeInappropriateDependencies(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
) {
  for (dependency in ideaPlugin.depends.toList()) {
    val dependencyName = dependency.rawText ?: dependency.stringValue ?: continue
    if (shouldRemoveDependency(dependencyName, desiredModuleKind)) {
      dependency.xmlElement?.delete()
    }
  }

  val dependencies = ideaPlugin.dependencies
  if (!dependencies.isValid) {
    removeInappropriateModuleDependencies(module, desiredModuleKind)
    return
  }

  for (moduleEntry in dependencies.moduleEntry.toList()) {
    val dependencyName = moduleEntry.name.stringValue ?: continue
    if (shouldRemoveDependency(dependencyName, desiredModuleKind)) {
      moduleEntry.xmlElement?.delete()
    }
  }
  for (pluginEntry in dependencies.plugin.toList()) {
    val dependencyName = pluginEntry.id.stringValue ?: continue
    if (shouldRemoveDependency(dependencyName, desiredModuleKind)) {
      pluginEntry.xmlElement?.delete()
    }
  }

  removeInappropriateModuleDependencies(module, desiredModuleKind)
}

private fun applyResolvedDependenciesFix(
  project: Project,
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
) {
  val commandName = message("inspection.remote.dev.make.only.kind.dependencies.fix.progress.title", desiredModuleKind.id)
  if (IntentionPreviewUtils.isIntentionPreviewActive()) {
    val removalPlan = buildDependenciesRemovalPlan(ideaPlugin, module, desiredModuleKind)
    applyDependenciesRemovalPlan(ideaPlugin, module, removalPlan)
    ensureExplicitDependency(ideaPlugin, module, desiredModuleKind)
    return
  }

  SplitModeQuickFixCoroutineService.getInstance(project).scope.launch {
    withModalProgress(project, message("inspection.remote.dev.make.only.kind.dependencies.fix.progress.title", desiredModuleKind.id)) {
      readAndEdtWriteAction {
        val removalPlan = buildDependenciesRemovalPlan(ideaPlugin, module, desiredModuleKind)
        writeAction {
          WriteCommandAction.writeCommandAction(project)
            .withName(commandName)
            .compute<Unit, Throwable> {
              applyDependenciesRemovalPlan(ideaPlugin, module, removalPlan)
              ensureExplicitDependency(ideaPlugin, module, desiredModuleKind)
              PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
        }
      }
    }
    runJpsToBazelConverter(project)
  }
}

private fun applyDependenciesRemovalPlan(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  removalPlan: DependenciesRemovalPlan,
) {
  for (dependency in ideaPlugin.depends.toList()) {
    val dependencyName = dependency.rawText ?: dependency.stringValue ?: continue
    if (dependencyName in removalPlan.runtimeDependenciesToRemove) {
      dependency.xmlElement?.delete()
    }
  }

  val dependencies = ideaPlugin.dependencies
  if (dependencies.isValid) {
    for (moduleEntry in dependencies.moduleEntry.toList()) {
      val dependencyName = moduleEntry.name.stringValue ?: continue
      if (dependencyName in removalPlan.runtimeDependenciesToRemove) {
        moduleEntry.xmlElement?.delete()
      }
    }
    for (pluginEntry in dependencies.plugin.toList()) {
      val dependencyName = pluginEntry.id.stringValue ?: continue
      if (dependencyName in removalPlan.runtimeDependenciesToRemove) {
        pluginEntry.xmlElement?.delete()
      }
    }
  }

  removeModuleDependencies(module, removalPlan.compileDependenciesToRemove)
}

private fun ensureExplicitDependency(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
) {
  val explicitDependencyName = getExplicitPlatformDependencyName(desiredModuleKind)
  if (!hasDirectDependency(ideaPlugin, explicitDependencyName)) {
    val newModuleEntry = ideaPlugin.dependencies.addModuleEntry()
    newModuleEntry.name.stringValue = explicitDependencyName
  }
  addModuleDependency(module, explicitDependencyName)
}

private fun buildDependenciesRemovalPlan(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
): DependenciesRemovalPlan {
  val runtimeDependenciesToRemove = mutableSetOf<String>()
  val dependencyModuleManager = if (module == null) null else ModuleManager.getInstance(module.project)

  for (dependency in ideaPlugin.depends) {
    ProgressManager.checkCanceled()
    val dependencyName = dependency.rawText ?: dependency.stringValue ?: continue
    val dependencyModule = dependencyModuleManager?.findModuleByName(dependencyName)
    addRuntimeDependencyToRemovalPlan(runtimeDependenciesToRemove, dependencyName, dependency.value, dependencyModule, desiredModuleKind)
  }

  val dependencies = ideaPlugin.dependencies
  if (dependencies.isValid) {
    for (moduleEntry in dependencies.moduleEntry) {
      ProgressManager.checkCanceled()
      val dependencyName = moduleEntry.name.stringValue ?: continue
      val dependencyModule = dependencyModuleManager?.findModuleByName(dependencyName)
      addRuntimeDependencyToRemovalPlan(runtimeDependenciesToRemove, dependencyName, moduleEntry.name.value, dependencyModule, desiredModuleKind)
    }
    for (pluginEntry in dependencies.plugin) {
      ProgressManager.checkCanceled()
      val dependencyName = pluginEntry.id.stringValue ?: continue
      val dependencyModule = dependencyModuleManager?.findModuleByName(dependencyName)
      addRuntimeDependencyToRemovalPlan(runtimeDependenciesToRemove, dependencyName, pluginEntry.id.value, dependencyModule, desiredModuleKind)
    }
  }

  val compileDependenciesToRemove = mutableSetOf<String>()
  if (module != null) {
    val currentDependencyModuleManager = dependencyModuleManager ?: return DependenciesRemovalPlan(runtimeDependenciesToRemove, compileDependenciesToRemove)
    for (moduleOrderEntry in ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<ModuleOrderEntry>()) {
      ProgressManager.checkCanceled()
      val dependencyName = moduleOrderEntry.moduleName
      val dependencyModule = currentDependencyModuleManager.findModuleByName(dependencyName)
      val dependencyKind = resolveCompileDependencyKind(dependencyName, dependencyModule)
      if (shouldRemoveResolvedDependency(dependencyKind, desiredModuleKind)) {
        compileDependenciesToRemove.add(dependencyName)
      }
    }
  }

  return DependenciesRemovalPlan(runtimeDependenciesToRemove, compileDependenciesToRemove)
}

private fun addRuntimeDependencyToRemovalPlan(
  runtimeDependenciesToRemove: MutableSet<String>,
  dependencyName: String,
  dependencyDescriptor: IdeaPlugin?,
  dependencyModule: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
) {
  val dependencyKind = resolveRuntimeDependencyKind(dependencyName, dependencyDescriptor, dependencyModule)
  if (shouldRemoveResolvedDependency(dependencyKind, desiredModuleKind)) {
    runtimeDependenciesToRemove.add(dependencyName)
  }
}

private fun findQuickFixTargetDescriptor(descriptor: ProblemDescriptor): IdeaPlugin? {
  val containingFile = descriptor.psiElement.containingFile
  if (containingFile is XmlFile) {
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(containingFile)
    if (ideaPlugin != null) {
      return ideaPlugin
    }
  }

  val module = ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement) ?: return null
  return findFallbackDescriptorForQuickFix(module)
}

private fun findFallbackDescriptorForQuickFix(module: Module): IdeaPlugin? {
  val descriptorFile = PluginModuleType.getContentModuleDescriptorXml(module) ?: PluginModuleType.getPluginXml(module) ?: return null
  return DescriptorUtil.getIdeaPlugin(descriptorFile)
}

private fun findTargetModule(descriptor: ProblemDescriptor): Module? {
  return ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement)
}

private fun removeInappropriateModuleDependencies(
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
) {
  if (module == null || IntentionPreviewUtils.isIntentionPreviewActive()) {
    return
  }

  ModuleRootModificationUtil.updateModel(module) { model ->
    for (orderEntry in model.orderEntries) {
      val moduleOrderEntry = orderEntry as? ModuleOrderEntry ?: continue
      if (shouldRemoveDependency(moduleOrderEntry.moduleName, desiredModuleKind)) {
        model.removeOrderEntry(moduleOrderEntry)
      }
    }
  }
}

private fun removeModuleDependencies(module: Module?, dependenciesToRemove: Set<String>) {
  if (module == null || IntentionPreviewUtils.isIntentionPreviewActive() || dependenciesToRemove.isEmpty()) {
    return
  }

  ModuleRootModificationUtil.updateModel(module) { model ->
    for (orderEntry in model.orderEntries) {
      val moduleOrderEntry = orderEntry as? ModuleOrderEntry ?: continue
      if (moduleOrderEntry.moduleName in dependenciesToRemove) {
        model.removeOrderEntry(moduleOrderEntry)
      }
    }
  }
}

private fun addModuleDependency(module: Module?, dependencyName: String) {
  if (module == null || IntentionPreviewUtils.isIntentionPreviewActive()) {
    return
  }

  ModuleRootModificationUtil.updateModel(module) { model ->
    if (model.orderEntries.filterIsInstance<ModuleOrderEntry>().any { it.moduleName == dependencyName }) {
      return@updateModel
    }

    val dependencyModule = ModuleManager.getInstance(module.project).findModuleByName(dependencyName)
    if (dependencyModule != null) {
      model.addModuleOrderEntry(dependencyModule)
    }
    else {
      model.addInvalidModuleEntry(dependencyName)
    }
  }
}

private fun runJpsToBazelConverter(project: Project) {
  if (IntentionPreviewUtils.isIntentionPreviewActive() || ApplicationManager.getApplication().isUnitTestMode) {
    return
  }

  val action = ActionManager.getInstance().getAction("MonorepoDevkit.Bazel.JpsToBazelConverter") ?: return
  val event = AnActionEvent.createEvent(action, SimpleDataContext.getProjectContext(project), null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  ActionUtil.performAction(action, event)
}

private fun shouldRemoveDependency(
  dependencyName: String,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
): Boolean {
  val dependencyKind = resolveDependencyKindForQuickFix(dependencyName)
  return when (desiredModuleKind) {
    SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> {
      dependencyKind == SplitModeApiRestrictionsService.ModuleKind.BACKEND
      || dependencyKind == SplitModeApiRestrictionsService.ModuleKind.MONOLITH
    }
    SplitModeApiRestrictionsService.ModuleKind.BACKEND -> {
      dependencyKind == SplitModeApiRestrictionsService.ModuleKind.FRONTEND
      || dependencyKind == SplitModeApiRestrictionsService.ModuleKind.MONOLITH
    }
    SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> {
      dependencyName == getExplicitPlatformDependencyName(SplitModeApiRestrictionsService.ModuleKind.FRONTEND)
      || dependencyName == getExplicitPlatformDependencyName(SplitModeApiRestrictionsService.ModuleKind.BACKEND)
    }
    else -> {
      false
    }
  }
}

private fun shouldRemoveResolvedDependency(
  dependencyKind: SplitModeApiRestrictionsService.ModuleKind?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
): Boolean {
  return when (desiredModuleKind) {
    SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> {
      dependencyKind == SplitModeApiRestrictionsService.ModuleKind.BACKEND
      || dependencyKind == SplitModeApiRestrictionsService.ModuleKind.MONOLITH
      || dependencyKind == SplitModeApiRestrictionsService.ModuleKind.MIXED
    }
    SplitModeApiRestrictionsService.ModuleKind.BACKEND -> {
      dependencyKind == SplitModeApiRestrictionsService.ModuleKind.FRONTEND
      || dependencyKind == SplitModeApiRestrictionsService.ModuleKind.MONOLITH
      || dependencyKind == SplitModeApiRestrictionsService.ModuleKind.MIXED
    }
    else -> {
      false
    }
  }
}

private fun resolveRuntimeDependencyKind(
  dependencyName: String,
  dependencyDescriptor: IdeaPlugin?,
  dependencyModule: Module?,
): SplitModeApiRestrictionsService.ModuleKind? {
  val resolvedDescriptorKind = resolveDescriptorDependencyKind(dependencyDescriptor)
  if (resolvedDescriptorKind != null) {
    return resolvedDescriptorKind
  }
  if (dependencyModule != null) {
    return SplitModeModuleKindResolver.getOrComputeModuleAnalysis(dependencyModule).resolvedModuleKind.kind
  }
  return resolveDependencyKindForQuickFix(dependencyName)
}

private fun resolveCompileDependencyKind(
  dependencyName: String,
  dependencyModule: Module?,
): SplitModeApiRestrictionsService.ModuleKind? {
  if (dependencyModule != null) {
    return SplitModeModuleKindResolver.getOrComputeModuleAnalysis(dependencyModule).resolvedModuleKind.kind
  }
  return resolveDependencyKindForQuickFix(dependencyName)
}

private fun resolveDependencyKindForQuickFix(dependencyName: String): SplitModeApiRestrictionsService.ModuleKind? {
  val dependencyKind = resolveDependencyKind(dependencyName)
  if (dependencyKind != null) {
    return dependencyKind
  }
  return SplitModeApiRestrictionsService.getInstance().getPredefinedDependencyKind(dependencyName)
}

private fun resolveDescriptorDependencyKind(
  dependencyDescriptor: IdeaPlugin?,
): SplitModeApiRestrictionsService.ModuleKind? {
  if (dependencyDescriptor == null) {
    return null
  }

  val containingFile = dependencyDescriptor.xmlElement?.containingFile
  val xmlFile = containingFile as? XmlFile ?: return null
  val module = ModuleUtilCore.findModuleForPsiElement(xmlFile) ?: return null
  return SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module, xmlFile).resolvedModuleKind.kind
}

private fun SplitModeApiRestrictionsService.ModuleKind.toFixableSplitKinds(): List<SplitModeApiRestrictionsService.ModuleKind> {
  return when (this) {
    SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
    SplitModeApiRestrictionsService.ModuleKind.BACKEND,
      -> listOf(this)
    is SplitModeApiRestrictionsService.ModuleKind.Composite -> moduleKinds.flatMap { it.toFixableSplitKinds() }.distinct()
    SplitModeApiRestrictionsService.ModuleKind.MONOLITH,
    SplitModeApiRestrictionsService.ModuleKind.MIXED,
    SplitModeApiRestrictionsService.ModuleKind.SHARED,
      -> emptyList()
  }
}

private fun hasDirectDependency(ideaPlugin: IdeaPlugin, dependencyName: String): Boolean {
  if (ideaPlugin.depends.any { dependency -> dependencyName == (dependency.rawText ?: dependency.stringValue) }) {
    return true
  }

  val dependencies = ideaPlugin.dependencies
  return dependencies.isValid
         && (dependencies.moduleEntry.any { it.name.stringValue == dependencyName }
           || dependencies.plugin.any { it.id.stringValue == dependencyName })
}

@Service(Service.Level.PROJECT)
private class SplitModeQuickFixCoroutineService(val scope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): SplitModeQuickFixCoroutineService {
      return project.service()
    }
  }
}
