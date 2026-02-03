// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.DescindingFilesFilter
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.SmartHashSet
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<VcsUpdateProcess>()

@ApiStatus.Experimental
object VcsUpdateProcess {
  @RequiresEdt
  fun launchUpdate(
    project: Project,
    actionInfo: ActionInfo,
    scopeInfo: ScopeInfo,
    context: DataContext,
    @Nls actionName: String,
    forceShowOptions: Boolean = false,
  ) {
    val showUpdateOptions = actionInfo.showOptions(project) || forceShowOptions
    launchUpdate(project, actionInfo, scopeInfo, context, showUpdateOptions, actionName)
  }

  @ApiStatus.Internal
  @RequiresEdt
  fun launchUpdate(
    project: Project,
    actionInfo: ActionInfo,
    scopeInfo: ScopeInfo,
    context: DataContext,
    showUpdateOptions: Boolean,
    @Nls actionName: String,
    @RequiresEdt onSuccess: () -> Unit = {},
  ) {
    LOG.debug { "project: $project, show update options: $showUpdateOptions" }

    val roots = getRoots(project, actionInfo, scopeInfo, context)
    if (roots.isEmpty()) {
      LOG.debug { "No roots found." }
      return
    }

    val updateSpec = createUpdateSpec(project, roots, actionInfo)
    if (!isUpdateSpecValid(updateSpec)) {
      LOG.debug { "Options not valid for update spec: $updateSpec" }
      return
    }

    if (showUpdateOptions) {
      val dialogOk = showOptionsDialog(project, actionInfo, scopeInfo, updateSpec, context)
      if (!dialogOk) {
        return
      }
    }

    launchUpdate(project, roots, updateSpec, actionInfo, actionName, onSuccess)
  }

  @ApiStatus.Internal
  fun launchUpdate(
    project: Project,
    roots: Array<FilePath>,
    updateSpec: List<VcsUpdateSpecification>,
    actionInfo: ActionInfo,
    @Nls actionName: String,
    @RequiresEdt onSuccess: () -> Unit = {},
  ) {
    project.service<ProjectVcsUpdateTaskExecutor>().launchUpdate(roots, updateSpec, actionInfo, actionName, onSuccess)
  }

  @ApiStatus.Internal
  suspend fun update(
    project: Project,
    roots: Array<FilePath>,
    updateSpec: List<VcsUpdateSpecification>,
    actionInfo: ActionInfo,
    actionName: @Nls String,
  ) {
    withContext(Dispatchers.Default) {
      VcsUpdateTask(project, roots, updateSpec, actionInfo, actionName).execute()
    }
  }

  @ApiStatus.Obsolete
  @ApiStatus.Internal
  @JvmStatic
  fun runUpdateBlocking(
    project: Project,
    roots: Array<FilePath>,
    updateSpec: List<VcsUpdateSpecification>,
    actionInfo: ActionInfo,
    @Nls actionName: String,
  ) {
    runBlockingMaybeCancellable {
      VcsUpdateTask(project, roots, updateSpec, actionInfo, actionName)
        .executeUpdate(mutableMapOf(), UpdatedFiles.create(), mutableMapOf(), mutableListOf())
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun getRoots(
    project: Project,
    actionInfo: ActionInfo,
    scopeInfo: ScopeInfo,
    context: DataContext,
    filterDescending: Boolean = true,
  ): Array<FilePath> {
    val filePaths = scopeInfo.getRoots(context, actionInfo)
    val filterExistsInVcs = scopeInfo.filterExistsInVcs()
    val roots = filterRoots(project, filePaths, filterExistsInVcs, actionInfo::getEnvironment)
    return if (filterDescending) {
      DescindingFilesFilter.filterDescindingFiles(roots, project)
    }
    else {
      roots
    }
  }

  private fun filterRoots(
    project: Project,
    roots: Collection<FilePath>,
    filterExistsInVcs: Boolean,
    updateEnvironmentSupplier: (AbstractVcs) -> UpdateEnvironment?,
  ): Array<FilePath> {
    val result = ArrayList<FilePath>()
    for (file in roots) {
      val vcs = VcsUtil.getVcsFor(project, file) ?: continue
      if (!filterExistsInVcs || AbstractVcs.fileInVcsByFileStatus(project, file)) {
        val updateEnvironment = updateEnvironmentSupplier(vcs)
        if (updateEnvironment != null) {
          result.add(file)
        }
      }
      else {
        val virtualFile = file.getVirtualFile()
        if (virtualFile != null && virtualFile.isDirectory()) {
          val vcsRoots = ProjectLevelVcsManager.getInstance(project).getAllVersionedRoots()
          for (vcsRoot in vcsRoots) {
            if (VfsUtilCore.isAncestor(virtualFile, vcsRoot, false)) {
              result.add(file)
            }
          }
        }
      }
    }
    return result.toTypedArray()
  }

  @ApiStatus.Internal
  @JvmStatic
  fun createUpdateSpec(
    project: Project,
    roots: Array<FilePath>,
    actionInfo: ActionInfo,
  ): List<VcsUpdateSpecification> {
    val resultPrep = mutableMapOf<AbstractVcs, MutableCollection<FilePath>>()
    for (file in roots) {
      val vcs = VcsUtil.getVcsFor(project, file) ?: continue
      resultPrep.getOrPut(vcs) { SmartHashSet() }.add(file)
    }
    return buildList {
      for ((vcs, roots) in resultPrep.entries) {
        val environment = actionInfo.getEnvironment(vcs) ?: continue

        @Suppress("DEPRECATION") val uniqueRoots = vcs.filterUniqueRoots(roots.toList(), FilePath::getVirtualFile)
        add(VcsUpdateSpecification(vcs, environment, uniqueRoots))
      }
    }
  }

  private fun isUpdateSpecValid(spec: List<VcsUpdateSpecification>): Boolean {
    for ((_, updateEnvironment, roots) in spec) {
      if (!updateEnvironment.validateOptions(roots)) { // messages already shown
        return false
      }
    }
    return true
  }

  @ApiStatus.Internal
  fun showOptionsDialog(
    project: Project,
    actionInfo: ActionInfo,
    scopeInfo: ScopeInfo,
    updateSpec: List<VcsUpdateSpecification>,
    dataContext: DataContext,
  ): Boolean {
    val envToConfMap = createConfigurableToEnvMap(updateSpec)
    val scopeName = scopeInfo.getScopeName(dataContext, actionInfo)
    if (!envToConfMap.isEmpty()) {
      val dialogOrStatus = actionInfo.createOptionsDialog(project, envToConfMap, scopeName)
      return dialogOrStatus.showAndGet()
    }
    return true
  }

  private fun createConfigurableToEnvMap(updateSpec: List<VcsUpdateSpecification>): LinkedHashMap<Configurable, AbstractVcs> {
    val envToConfMap = LinkedHashMap<Configurable, AbstractVcs>()
    for ((vcs, environment, roots) in updateSpec) {
      val configurable = environment.createConfigurable(roots)
      if (configurable != null) {
        envToConfMap[configurable] = vcs
      }
    }
    return envToConfMap
  }

  @JvmStatic
  fun checkUpdateHasCustomNotification(vcss: Collection<AbstractVcs>): Boolean {
    return vcss.all { vcs ->
      val environment = vcs.updateEnvironment
      environment != null && environment.hasCustomNotification()
    }
  }
}

@ApiStatus.Internal
data class VcsUpdateSpecification(
  val vcs: AbstractVcs,
  val environment: UpdateEnvironment,
  val roots: Collection<FilePath>,
)

@Service(Service.Level.PROJECT)
private class ProjectVcsUpdateTaskExecutor(private val project: Project, private val cs: CoroutineScope) {
  fun launchUpdate(
    roots: Array<FilePath>,
    updateSpec: List<VcsUpdateSpecification>,
    actionInfo: ActionInfo,
    @Nls actionName: String,
    @RequiresEdt onSuccess: () -> Unit = {},
  ) {
    cs.launch {
      try {
        VcsUpdateProcess.update(project, roots, updateSpec, actionInfo, actionName)
        withContext(Dispatchers.UI) {
          onSuccess()
        }
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }
}