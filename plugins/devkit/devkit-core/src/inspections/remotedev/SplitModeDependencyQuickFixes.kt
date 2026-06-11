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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.Dependency
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeAnalysisFlags
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.getExplicitPlatformDependencyName
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.recognizeExplicitDependencyKind
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil

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

  // Removes unsuitable and adds desired module kind dependencies, broader refactoring than the createAddExplicitDependenciesFixes
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

  // Only adds platform.frontend/backend dependencies and does not touch others - for modules that are implicitly fe/be only
  fun createAddExplicitDependenciesFixes(
    module: Module,
    currentDescriptor: IdeaPlugin?,
    actualModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Array<LocalQuickFix> {
    if (actualModuleKind != SplitModeApiRestrictionsService.ModuleKind.FRONTEND
        && actualModuleKind != SplitModeApiRestrictionsService.ModuleKind.BACKEND) {
      return emptyArray()
    }

    val availableDependencies = getRuntimeDependencies(module, currentDescriptor)
    val fixes = mutableListOf<LocalQuickFix>()
    if (shouldOfferAddExplicitDependencyFix(availableDependencies, actualModuleKind)) {
      fixes.add(AddExplicitPlatformDependencyFix(module.name, actualModuleKind))
    }
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
    return availableDependencies.runtimeDependencies.any { shouldRemoveDependency(availableDependencies.project, it, desiredModuleKind) }
  }

  private fun hasCompileDependencyToRemove(
    availableDependencies: DependenciesForFixAvailability,
    desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  ): Boolean {
    return availableDependencies.compileDependencies.any { shouldRemoveDependency(availableDependencies.project, it, desiredModuleKind) }
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

    override fun startInWriteAction(): Boolean = IntentionPreviewUtils.isIntentionPreviewActive()

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val ideaPlugin = findQuickFixTargetDescriptor(descriptor) ?: return
      val module = findTargetModule(descriptor)
      val xmlFile = ideaPlugin.xmlElement?.containingFile ?: return
      if (!IntentionPreviewUtils.prepareElementForWrite(xmlFile)) return

      if (IntentionPreviewUtils.isIntentionPreviewActive()) {
        applyDependencyFixInPreview(ideaPlugin, module, desiredModuleKind)
        return
      }

      val commandName: @NlsContexts.Command String = message("inspection.remote.dev.make.only.kind.dependencies.fix.progress.title", desiredModuleKind.id)

      runWithModalProgressBlocking(project, commandName) {
        applyDependencyFix(project, ideaPlugin, module, desiredModuleKind, commandName)
      }
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

    override fun startInWriteAction(): Boolean = IntentionPreviewUtils.isIntentionPreviewActive()

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val ideaPlugin = findQuickFixTargetDescriptor(descriptor) ?: return
      val module = findTargetModule(descriptor)
      val xmlFile = ideaPlugin.xmlElement?.containingFile ?: return
      if (!IntentionPreviewUtils.prepareElementForWrite(xmlFile)) return

      if (IntentionPreviewUtils.isIntentionPreviewActive()) {
        applyDependencyFixInPreview(ideaPlugin, module, desiredModuleKind)
        return
      }

      val commandName: @NlsContexts.Command String = message("inspection.remote.dev.make.only.kind.dependencies.fix.progress.title", desiredModuleKind.id)

      runWithModalProgressBlocking(project, commandName) {
        applyDependencyFix(project, ideaPlugin, module, desiredModuleKind, commandName)
      }
    }
  }
}

private data class DependenciesForFixAvailability(
  val runtimeDependencies: Set<String>,
  val compileDependencies: Set<String>,
  val project: Project,
)

private data class DependenciesRemovalPlan(
  val runtimeDependenciesToRemove: Set<String>,
  val compileDependenciesToRemove: Set<String>,
)

private data class DependencyFixChanges(
  val runtimeDependenciesToRemove: Set<String>,
  val compileDependenciesToRemove: Set<String>,
  val explicitDependencyName: String,
)

private fun getRuntimeDependencies(module: Module, currentDescriptor: IdeaPlugin?): DependenciesForFixAvailability {
  val targetDescriptor = currentDescriptor ?: findFallbackDescriptorForQuickFix(module)
  return DependenciesForFixAvailability(
    runtimeDependencies = getRuntimeDependencies(targetDescriptor),
    compileDependencies = getCompileDependencies(module),
    project = module.project,
  )
}

private fun getRuntimeDependencies(ideaPlugin: IdeaPlugin?): Set<String> {
  if (ideaPlugin == null) {
    return emptySet()
  }

  return sequence {
    for (dependency in ideaPlugin.depends) {
      if (dependency.isOptionalOldStyleDependency()) {
        continue
      }
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

private fun applyDependencyFixInPreview(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
) {
  val changes = buildDependencyFixChanges(ideaPlugin, module, desiredModuleKind, includeCompileDependencies = false)
  applyXmlDependencyChanges(ideaPlugin, changes)
}

private suspend fun applyDependencyFix(
  project: Project,
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  commandName: @NlsContexts.Command String,
) {
  val changes = readAction {
    buildDependencyFixChanges(ideaPlugin, module, desiredModuleKind, includeCompileDependencies = true)
  }

  withContext(Dispatchers.EDT) {
    WriteCommandAction.writeCommandAction(project)
      .withName(commandName)
      .compute<Unit, Throwable> {
        applyXmlDependencyChanges(ideaPlugin, changes)
        applyCompileDependencyChanges(module, changes)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }

    runJpsToBazelConverterIfNeeded(project)
  }
}

private fun applyXmlDependencyChanges(
  ideaPlugin: IdeaPlugin,
  changes: DependencyFixChanges,
) {
  for (dependency in ideaPlugin.depends.toList()) {
    if (dependency.isOptionalOldStyleDependency()) {
      continue
    }
    val dependencyName = dependency.rawText ?: dependency.stringValue ?: continue
    if (dependencyName in changes.runtimeDependenciesToRemove) {
      dependency.xmlElement?.delete()
    }
  }

  val dependencies = ideaPlugin.dependencies
  if (dependencies.isValid) {
    for (moduleEntry in dependencies.moduleEntry.toList()) {
      val dependencyName = moduleEntry.name.stringValue ?: continue
      if (dependencyName in changes.runtimeDependenciesToRemove) {
        moduleEntry.xmlElement?.delete()
      }
    }
    for (pluginEntry in dependencies.plugin.toList()) {
      val dependencyName = pluginEntry.id.stringValue ?: continue
      if (dependencyName in changes.runtimeDependenciesToRemove) {
        pluginEntry.xmlElement?.delete()
      }
    }
  }

  ensureExplicitDependencyInXml(ideaPlugin, changes.explicitDependencyName)
}

private fun applyCompileDependencyChanges(
  module: Module?,
  changes: DependencyFixChanges,
) {
  removeModuleDependencies(module, changes.compileDependenciesToRemove)
  addModuleDependency(module, changes.explicitDependencyName)
}

private fun ensureExplicitDependencyInXml(
  ideaPlugin: IdeaPlugin,
  explicitDependencyName: String,
) {
  if (!hasDirectDependency(ideaPlugin, explicitDependencyName)) {
    val newModuleEntry = ideaPlugin.dependencies.addModuleEntry()
    newModuleEntry.name.stringValue = explicitDependencyName
  }
}

private fun buildDependencyFixChanges(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  includeCompileDependencies: Boolean,
): DependencyFixChanges {
  val removalPlan = buildDependenciesRemovalPlan(ideaPlugin, module, desiredModuleKind, includeCompileDependencies)
  return DependencyFixChanges(
    runtimeDependenciesToRemove = removalPlan.runtimeDependenciesToRemove,
    compileDependenciesToRemove = removalPlan.compileDependenciesToRemove,
    explicitDependencyName = getExplicitPlatformDependencyName(desiredModuleKind),
  )
}

private fun buildDependenciesRemovalPlan(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  includeCompileDependencies: Boolean,
): DependenciesRemovalPlan {
  if (desiredModuleKind == SplitModeApiRestrictionsService.ModuleKind.MONOLITH) {
    return buildMonolithDependenciesRemovalPlan(ideaPlugin, module, includeCompileDependencies)
  }

  return buildResolvableRuntimeDependenciesRemovalPlan(ideaPlugin, module, desiredModuleKind, includeCompileDependencies)
}

private fun buildResolvableRuntimeDependenciesRemovalPlan(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
  includeCompileDependencies: Boolean,
): DependenciesRemovalPlan {
  val dependencyModuleManager = if (module == null) null else ModuleManager.getInstance(module.project)
  var runtimeDependenciesToRemove = emptySet<String>()

  for (dependency in ideaPlugin.depends) {
    ProgressManager.checkCanceled()
    if (dependency.isOptionalOldStyleDependency()) {
      continue
    }
    val dependencyName = dependency.rawText ?: dependency.stringValue ?: continue
    val dependencyModule = dependencyModuleManager?.findModuleByName(dependencyName)
    runtimeDependenciesToRemove = addRuntimeDependencyToRemovalPlan(
      runtimeDependenciesToRemove,
      dependencyName,
      dependency.value,
      dependencyModule,
      desiredModuleKind,
    )
  }

  val dependencies = ideaPlugin.dependencies
  if (dependencies.isValid) {
    for (moduleEntry in dependencies.moduleEntry) {
      ProgressManager.checkCanceled()
      val dependencyName = moduleEntry.name.stringValue ?: continue
      val dependencyModule = dependencyModuleManager?.findModuleByName(dependencyName)
      runtimeDependenciesToRemove = addRuntimeDependencyToRemovalPlan(
        runtimeDependenciesToRemove,
        dependencyName,
        moduleEntry.name.value,
        dependencyModule,
        desiredModuleKind,
      )
    }
    for (pluginEntry in dependencies.plugin) {
      ProgressManager.checkCanceled()
      val dependencyName = pluginEntry.id.stringValue ?: continue
      val dependencyModule = dependencyModuleManager?.findModuleByName(dependencyName)
      runtimeDependenciesToRemove = addRuntimeDependencyToRemovalPlan(
        runtimeDependenciesToRemove,
        dependencyName,
        pluginEntry.id.value,
        dependencyModule,
        desiredModuleKind,
      )
    }
  }

  val compileDependenciesToRemove = if (!includeCompileDependencies || module == null) {
    emptySet()
  }
  else {
    val currentDependencyModuleManager = dependencyModuleManager ?: return DependenciesRemovalPlan(runtimeDependenciesToRemove, emptySet())
    ModuleRootManager.getInstance(module).orderEntries
      .filterIsInstance<ModuleOrderEntry>()
      .mapNotNull { moduleOrderEntry ->
        ProgressManager.checkCanceled()
        val dependencyName = moduleOrderEntry.moduleName
        val dependencyModule = currentDependencyModuleManager.findModuleByName(dependencyName)
        val dependencyKind = resolveCompileDependencyKind(dependencyName, dependencyModule)
        dependencyName.takeIf { shouldRemoveResolvedDependency(dependencyKind, desiredModuleKind) }
      }
      .toSet()
  }

  return DependenciesRemovalPlan(runtimeDependenciesToRemove, compileDependenciesToRemove)
}

private fun buildMonolithDependenciesRemovalPlan(
  ideaPlugin: IdeaPlugin,
  module: Module?,
  includeCompileDependencies: Boolean,
): DependenciesRemovalPlan {
  val runtimeDependenciesToRemove = getRuntimeDependencies(ideaPlugin)
    .filter { shouldRemoveDependency(module?.project ?: ideaPlugin.xmlElement?.project, it, SplitModeApiRestrictionsService.ModuleKind.MONOLITH) }
    .toSet()

  val compileDependenciesToRemove = if (!includeCompileDependencies || module == null) {
    emptySet()
  }
  else {
    getCompileDependencies(module)
      .filter { shouldRemoveDependency(module.project, it, SplitModeApiRestrictionsService.ModuleKind.MONOLITH) }
      .toSet()
  }

  return DependenciesRemovalPlan(runtimeDependenciesToRemove, compileDependenciesToRemove)
}

private fun addRuntimeDependencyToRemovalPlan(
  runtimeDependenciesToRemove: Set<String>,
  dependencyName: String,
  dependencyDescriptor: IdeaPlugin?,
  dependencyModule: Module?,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
): Set<String> {
  val dependencyKind = resolveRuntimeDependencyKind(dependencyName, dependencyDescriptor, dependencyModule)
  return if (shouldRemoveResolvedDependency(dependencyKind, desiredModuleKind)) runtimeDependenciesToRemove + dependencyName else runtimeDependenciesToRemove
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

private fun removeModuleDependencies(module: Module?, dependenciesToRemove: Set<String>) {
  if (module == null || dependenciesToRemove.isEmpty()) {
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
  if (module == null) {
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

private fun runJpsToBazelConverterIfNeeded(project: Project) {
  if (ApplicationManager.getApplication().isUnitTestMode || !SplitModeAnalysisFlags.isRunJpsToBazelInQuickFixEnabled()) {
    return
  }

  val action = ActionManager.getInstance().getAction("MonorepoDevkit.Bazel.JpsToBazelConverter") ?: return
  val event = AnActionEvent.createEvent(action, SimpleDataContext.getProjectContext(project), null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  ActionUtil.performAction(action, event)
}

private fun shouldRemoveDependency(
  project: Project?,
  dependencyName: String,
  desiredModuleKind: SplitModeApiRestrictionsService.ModuleKind,
): Boolean {
  val dependencyKind = resolveDependencyKindForQuickFix(project, dependencyName)
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
  return resolveDependencyKindForQuickFix(dependencyDescriptor?.xmlElement?.project, dependencyName)
}

private fun resolveCompileDependencyKind(
  dependencyName: String,
  dependencyModule: Module?,
): SplitModeApiRestrictionsService.ModuleKind? {
  if (dependencyModule != null) {
    return SplitModeModuleKindResolver.getOrComputeModuleAnalysis(dependencyModule).resolvedModuleKind.kind
  }
  return resolveDependencyKindForQuickFix(null, dependencyName)
}

private fun resolveDependencyKindForQuickFix(
  project: Project?,
  dependencyName: String,
): SplitModeApiRestrictionsService.ModuleKind? {
  val dependencyKind = recognizeExplicitDependencyKind(dependencyName)
  if (dependencyKind != null) {
    return dependencyKind
  }
  return project?.let { SplitModeApiRestrictionsService.getInstance(it).getPredefinedDependencyKind(dependencyName) }
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
  if (ideaPlugin.depends.any { dependency ->
      !dependency.isOptionalOldStyleDependency() && dependencyName == (dependency.rawText ?: dependency.stringValue)
    }) {
    return true
  }

  val dependencies = ideaPlugin.dependencies
  return dependencies.isValid
         && (dependencies.moduleEntry.any { it.name.stringValue == dependencyName }
           || dependencies.plugin.any { it.id.stringValue == dependencyName })
}

private fun Dependency.isOptionalOldStyleDependency(): Boolean = optional.value == true
