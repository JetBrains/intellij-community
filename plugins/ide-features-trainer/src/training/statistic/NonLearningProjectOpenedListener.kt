// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import training.lang.LangManager
import training.util.isLearningProject
import training.util.trainerPluginConfigName

internal class NonLearningProjectOpenedListener : ProjectManagerListener {
  override fun projectOpened(project: Project) {
    val learnProjectState = LearnProjectState.instance
    val way = learnProjectState.firstTimeOpenedWay
    if (way != null) {
      val langSupport = LangManager.getInstance().getLangSupport() ?: return
      if (!isLearningProject(project, langSupport)) {
        StatisticBase.logNonLearningProjectOpened(way)
        learnProjectState.firstTimeOpenedWay = null
      }
    }
  }
}

@State(name = "LearnProjectState", storages = [Storage(value = trainerPluginConfigName)])
internal class LearnProjectState : PersistentStateComponent<LearnProjectState> {
  var firstTimeOpenedWay: StatisticBase.LearnProjectOpeningWay? = null

  override fun getState(): LearnProjectState = this

  override fun loadState(state: LearnProjectState) {
    firstTimeOpenedWay = state.firstTimeOpenedWay
  }

  companion object {
    internal val instance: LearnProjectState
      get() = ApplicationManager.getApplication().getService(LearnProjectState::class.java)
  }
}