// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl
import git4idea.update.VcsLogUiPropertiesWithSharedRecentFilters

@State(name = "Git.Compare.Branches.Top.Log.Properties", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
internal class GitCompareBranchesTopLogProperties(project: Project) : GitCompareBranchesLogProperties(project)

@State(name = "Git.Compare.Branches.Bottom.Log.Properties", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
internal class GitCompareBranchesBottomLogProperties(project: Project) : GitCompareBranchesLogProperties(project)

abstract class GitCompareBranchesLogProperties(project: Project) :
  VcsLogUiPropertiesWithSharedRecentFilters<GitCompareBranchesLogProperties.MyState>(project, service()) {

  class MyState : VcsLogUiPropertiesImpl.State() {
    var SHOW_DIFF_PREVIEW = false
  }

  private var commonState = MyState()

  override fun getState(): MyState {
    return commonState
  }

  override fun loadState(state: MyState) {
    commonState = state
  }

  override fun <T : Any> get(property: VcsLogUiProperty<T>): T =
    if (CommonUiProperties.SHOW_DIFF_PREVIEW == property) {
      @Suppress("UNCHECKED_CAST")
      state.SHOW_DIFF_PREVIEW as T
    }
    else {
      super.get(property)
    }

  override fun <T : Any> set(property: VcsLogUiProperty<T>, value: T) {
    if (CommonUiProperties.SHOW_DIFF_PREVIEW == property) {
      state.SHOW_DIFF_PREVIEW = (value as Boolean)
      onPropertyChanged(property)
    }
    else {
      super.set(property, value)
    }
  }
}
