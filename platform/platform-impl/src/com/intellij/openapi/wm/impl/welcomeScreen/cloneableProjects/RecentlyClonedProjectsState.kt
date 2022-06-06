// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.components.*
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.util.xmlb.annotations.OptionTag
import java.io.File
import java.util.*

@State(
  name = "RecentlyClonedProjectsState",
  storages = [Storage(value = "recentlyClonedProjects.xml", roamingType = RoamingType.DISABLED)],
  category = SettingsCategory.TOOLS
)
@Service(Level.APP)
class RecentlyClonedProjectsState : BaseState(), PersistentStateComponent<RecentlyClonedProjectsState> {
  @get:OptionTag("RECENTLY_CLONED_PROJECTS_PATHS")
  val recentlyClonedProjectsPaths by list<String>()

  override fun getState(): RecentlyClonedProjectsState = this

  override fun loadState(state: RecentlyClonedProjectsState) {
    copyFrom(state)
  }

  fun collectRecentlyClonedProjects(): List<RecentProjectItem> {
    val recentProjectManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase

    return state.recentlyClonedProjectsPaths.map { projectPath ->
      val projectName = recentProjectManager.getProjectName(projectPath)
      val displayName = recentProjectManager.getDisplayName(projectPath) ?: projectName

      RecentProjectItem(projectPath, projectName, displayName)
    }
  }

  fun addClonedProject(projectPath: String) {
    recentlyClonedProjectsPaths.add(projectPath)
  }

  fun removeClonedProject(projectPath: String) {
    var file: File? = File(projectPath)
    while (file != null) {
      val result = recentlyClonedProjectsPaths.removeIf { it == file!!.path }
      if (result) break

      file = FileUtil.getParentFile(file)
    }
  }

  companion object {
    @JvmStatic
    val instance: RecentlyClonedProjectsState
      get() = service()
  }
}