// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.CommitMode.NonModalCommitMode
import com.intellij.vcs.commit.CommitModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class MergeConflictManager(private val project: Project, private val cs: CoroutineScope): Disposable {

  init {
    isNonModalMergeRegistryValue().addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
      }
    }, cs)
  }

  fun isResolvedConflict(item: Any): Boolean {
    if (item is FilePath) {
      return isResolvedConflict(item)
    }
    if (item is Change) {
      return isResolvedConflict(ChangesUtil.getFilePath(item))
    }

    return false
  }

  fun isResolvedConflict(path: FilePath): Boolean =
    ChangeListManager.getInstance(project).isResolvedConflict(path)

  fun getResolvedConflictPaths(): List<FilePath> =
    ChangeListManager.getInstance(project).getResolvedConflictPaths()

  fun isMergeConflict(): Boolean =
    ChangeListManager.getInstance(project).allChanges.any { isMergeConflict(it.fileStatus) }

  fun showMergeConflicts(files: Collection<VirtualFile>) {
    VcsDirtyScopeManager.getInstance(project).filesDirty(files, emptyList())
    navigateToChanges()
  }

  private fun navigateToChanges() {
    val toolWindow = ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.Companion.LOCAL_CHANGES)
    cs.launch(Dispatchers.EDT) {
      toolWindow?.activate {
        ChangesViewContentManager.getInstance(project).selectContent(ChangesViewContentManager.Companion.LOCAL_CHANGES)
      }
    }
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun isNonModalMergeEnabled(project: Project): Boolean {
      return isNonModalMergeRegistryValue().asBoolean()
             && CommitModeManager.getInstance(project).getCurrentCommitMode() is NonModalCommitMode
    }

   private fun isNonModalMergeRegistryValue() = Registry.get("vcs.non.modal.merge.enabled")

    @JvmStatic
    fun isForceIncludeResolvedConflicts(): Boolean = Registry.`is`("vcs.non.modal.merge.force.include.resolved")

    @JvmStatic
    fun getInstance(project: Project): MergeConflictManager = project.service<MergeConflictManager>()

    @JvmStatic
    fun isMergeConflict(status: FileStatus): Boolean {
      return status === FileStatus.MERGED_WITH_CONFLICTS ||
             status === FileStatus.MERGED_WITH_BOTH_CONFLICTS ||
             status === FileStatus.MERGED_WITH_PROPERTY_CONFLICTS
    }
  }
}
