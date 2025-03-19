// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogFilterUi
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.filter.BranchFilterModel
import com.intellij.vcs.log.ui.filter.BranchFilterPopupComponent
import com.intellij.vcs.log.ui.filter.VcsLogPopupComponentAction
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import javax.swing.JComponent

@ApiStatus.Internal
class FileHistoryFilterUi(private val path: FilePath,
                          private val hash: Hash?,
                          private val root: VirtualFile,
                          properties: FileHistoryUiProperties,
                          private val data: VcsLogData,
                          private val initialFilters: VcsLogFilterCollection,
                          filterConsumer: Consumer<VcsLogFilterCollection>) : VcsLogFilterUi {
  private val propertiesWrapper = PropertiesWrapper(properties)
  private val branchFilterModel: BranchFilterModel

  var visiblePack: VisiblePack = VisiblePack.EMPTY

  init {
    branchFilterModel = BranchFilterModel(::visiblePack, data.storage, listOf(root), { listOf(root) }, propertiesWrapper, initialFilters)
    branchFilterModel.addSetFilterListener { filterConsumer.accept(filters) }
  }

  override fun getFilters(): VcsLogFilterCollection {
    val fileFilter = VcsLogFileHistoryFilter(path, hash)
    return VcsLogFilterObject.collection(fileFilter, branchFilterModel.branchFilter, branchFilterModel.revisionFilter,
                                         branchFilterModel.rangeFilter)
  }

  @RequiresEdt
  fun hasBranchFilter(): Boolean = branchFilterModel.getFilter()?.isEmpty == false

  fun isBranchFilterEnabled(): Boolean {
    if (FileHistoryFilterer.canFilterWithIndex(data.index, root, visiblePack.dataPack)) return true
    val handler = data.logProviders[root]?.getFileHistoryHandler(data.project) ?: return false
    val supportedFilters = handler.getSupportedFilters(root, path, hash)
    return BranchFilterModel.branchFilterKeys.any { supportedFilters.contains(it) }
  }

  @RequiresEdt
  fun resetFiltersToDefault() {
    branchFilterModel.setFilter(initialFilters)
  }

  @RequiresEdt
  fun clearFilters() {
    branchFilterModel.setFilter(null)
  }

  fun createActionGroup(): ActionGroup {
    val actionGroup = DefaultActionGroup()
    actionGroup.add(BranchFilterComponent())
    return actionGroup
  }

  private inner class BranchFilterComponent : VcsLogPopupComponentAction(VcsLogBundle.messagePointer("vcs.log.branch.filter.action.text")) {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return BranchFilterPopupComponent(propertiesWrapper, branchFilterModel).initUi().also {
        it.isEnabled = presentation.isEnabled
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = isBranchFilterEnabled()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun getTargetComponent(e: AnActionEvent) = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI)?.toolbar
  }

  private class PropertiesWrapper(private val properties: FileHistoryUiProperties) : MainVcsLogUiProperties, VcsLogUiProperties by properties {
    private val filters = mutableMapOf<String, List<String>>()

    override fun getFilterValues(filterName: String) = filters[filterName]
    override fun saveFilterValues(filterName: String, values: List<String>?) {
      if (values != null) {
        filters[filterName] = values
      }
      else {
        filters.remove(filterName)
      }
    }

    override fun addRecentlyFilteredGroup(filterName: String, values: Collection<String>) = properties.addRecentlyFilteredGroup(filterName, values)
    override fun getRecentlyFilteredGroups(filterName: String): List<List<String>> = properties.getRecentlyFilteredGroups(filterName)
  }
}