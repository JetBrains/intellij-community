// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl.projectlevelman

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "VcsDirectoryMappingCache", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class VcsDirectoryMappingCache : PersistentStateComponent<VcsDirectoryMappingCache.MyState> {
  class MyState : BaseState() {
    var vcsName: String? by string()
    var roots: MutableSet<String> by stringSet()
  }

  private var state = MyState()

  override fun getState(): MyState {
    return state
  }

  override fun loadState(state: MyState) {
    this.state = state
  }

  fun getMappings(vcs: String): List<String> {
    val state = state
    if (vcs == state.vcsName) {
      return state.roots.toList()
    }
    return emptyList()
  }

  fun setMappings(vcs: String?, vcsRoots: List<String>) {
    val newState = MyState()
    newState.vcsName = vcs
    newState.roots += vcsRoots
    state = newState
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsDirectoryMappingCache = project.service()
  }
}
