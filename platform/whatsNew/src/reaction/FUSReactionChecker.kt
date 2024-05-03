package com.intellij.platform.whatsNew.reaction

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class FUSReactionChecker(private val stateKey: String): ReactionChecker {
  override fun onLike(project: Project?, place: String?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val value = if (getLikenessState() == ReactionChecker.State.Liked) {
      0
    }
    else 1

    putValue(value)
    ReactionCollector.reactedPerformed(project, place, ReactionType.Like,
                                       if (value == 0) ReationAction.Unset
                                                    else ReationAction.Set)
  }

  override fun onDislike(project: Project?, place: String?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val value = if (getLikenessState() == ReactionChecker.State.Disliked) {
      0
    }
    else -1

    putValue(value)
    ReactionCollector.reactedPerformed(project, place, ReactionType.Dislike,
                                       if (value == 0) ReationAction.Unset
                                                    else ReationAction.Set)
  }

  internal fun putValue(value: Int) {
    val propertiesComponent = PropertiesComponent.getInstance()
    propertiesComponent.setValue(stateKey, value, 0)
  }

  private fun getLikenessState(): ReactionChecker.State {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val propertiesComponent = PropertiesComponent.getInstance()

    return ReactionChecker.State.stateByIndex(propertiesComponent.getInt(stateKey, 0))
  }

  override fun clearLikenessState() {
    putValue(0)
  }

  override fun checkState(state: ReactionChecker.State): Boolean {
    return getLikenessState() == state
  }
}