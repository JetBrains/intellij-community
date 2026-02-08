// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew.reaction

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.platform.whatsNew.WhatsNewBundle
import org.jetbrains.annotations.ApiStatus
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ReactionsPanel {
  companion object {
    @ApiStatus.Internal
    @JvmField
    val STATE_CHECKER_KEY: DataKey<ReactionChecker> = DataKey.create("RunWidgetSlot")

    private val group: ActionGroup = DefaultActionGroup(mutableListOf(LikeReactionAction(), DislikeReactionAction()))

    fun createPanel(
      place: String,
      stateChecker: ReactionChecker,
    ): JComponent {

      return object : JPanel(FlowLayout(FlowLayout.CENTER)), UiDataProvider {
        override fun uiDataSnapshot(sink: DataSink) {
          sink[STATE_CHECKER_KEY] = stateChecker
        }
      }.apply {
        add(JLabel(WhatsNewBundle.message("useful.pane.text")))

        val look = object : ActionButtonLook() {}

        val toolbar = object : ActionToolbarImpl(place, group, true) {
          init {
            border = null
            targetComponent = this@apply
            isOpaque = false
          }

          override fun getActionButtonLook(): ActionButtonLook {
            return look
          }
        }
        add(toolbar)
      }
    }
  }
}


internal class LikeReactionAction : ReactionAction(CommonBundle.message("button.without.mnemonic.yes"), AllIcons.Ide.LikeDimmed,
                                                   AllIcons.Ide.Like,
                                                   AllIcons.Ide.LikeSelected) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return getReactionStateChecker(e)?.checkState(ReactionChecker.State.Liked) == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    getReactionStateChecker(e)?.onLike(e.project, e.place)
  }
}

internal class DislikeReactionAction : ReactionAction(CommonBundle.message("button.without.mnemonic.no"), AllIcons.Ide.DislikeDimmed,
                                                      AllIcons.Ide.Dislike, AllIcons.Ide.DislikeSelected) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return getReactionStateChecker(e)?.checkState(ReactionChecker.State.Disliked) == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    getReactionStateChecker(e)?.onDislike(e.project, e.place)
  }
}

internal abstract class ReactionAction(
  text: @NlsActions.ActionText String,
  val icon: Icon,
  val hoveredIcon: Icon,
  val selectedIcon: Icon,
) : AnAction(text, null, icon), DumbAware {

  override fun update(e: AnActionEvent) {
    val selected = isSelected(e)
    val presentation = e.presentation

    presentation.icon = if (selected) selectedIcon else icon
    presentation.hoveredIcon = if (selected) selectedIcon else hoveredIcon
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  abstract fun isSelected(e: AnActionEvent): Boolean

  fun getReactionStateChecker(e: AnActionEvent): ReactionChecker? {
    return e.dataContext.getData(ReactionsPanel.STATE_CHECKER_KEY)
  }

}

interface ReactionChecker {
  enum class State(val index: Int) {
    Liked(1),
    Disliked(-1),
    Undefined(0);

    companion object {
      fun stateByIndex(ind: Int?): State {
        return ind?.let { entries.firstOrNull { it.index == ind } ?: Undefined } ?: Undefined
      }
    }
  }

  fun onLike(project: Project?, place: String?)

  fun onDislike(project: Project?, place: String?)

  fun checkState(state: State): Boolean

  fun clearLikenessState()
}