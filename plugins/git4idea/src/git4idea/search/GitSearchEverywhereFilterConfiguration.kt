// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.search

import com.intellij.ide.util.TypeVisibilityStateHolder
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable

@Service
@State(name = "GitSEFilterConfiguration", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class GitSearchEverywhereFilterConfiguration(@Suppress("unused") private val project: Project)
  : TypeVisibilityStateHolder<GitSearchEverywhereItemType>, PersistentStateComponent<FilterState> {

  @Volatile
  private var state = FilterState()

  override fun getState(): FilterState = state

  override fun loadState(state: FilterState) {
    this.state = state
  }

  override fun isVisible(type: GitSearchEverywhereItemType) = state.visibleItems.contains(type)

  override fun setVisible(type: GitSearchEverywhereItemType, visible: Boolean) {
    val visibleItems = state.visibleItems
    if(visible) {
      visibleItems.add(type)
    } else {
      visibleItems.remove(type)
    }
  }
}

@Serializable
data class FilterState(val visibleItems: MutableSet<GitSearchEverywhereItemType> = mutableSetOf(GitSearchEverywhereItemType.COMMIT_BY_HASH))
