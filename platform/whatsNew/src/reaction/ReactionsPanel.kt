// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew.reaction

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.platform.whatsNew.WhatsNewBundle
import com.intellij.ui.JBColor
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ReactionsPanel {
  companion object {
    @ApiStatus.Internal
    @JvmField
    val STATE_CHECKER_KEY = DataKey.create<ReactionChecker>("RunWidgetSlot")

    private val group: ActionGroup = DefaultActionGroup(mutableListOf(LikeReactionAction(), DislikeUsefulAction()))

    fun createPanel(place: String,
                    stateChecker: ReactionChecker): JComponent {

      return JPanel(MigLayout("ins 0, gap 7", "push[min!][pref!]push")).apply {
        add(JLabel(WhatsNewBundle.message("useful.pane.text")))

        DataManager.registerDataProvider(this) { key ->
          if (STATE_CHECKER_KEY.`is`(key))
            stateChecker
          else null
        }

        val look = object : ActionButtonLook() {}

        val toolbar = object : ActionToolbarImpl(place, group, true) {
          override fun getActionButtonLook(): ActionButtonLook {
            return look
          }
        }
        toolbar.border = null
        toolbar.targetComponent = this
        add(toolbar)

        // isOpaque = false
        background = JBColor.WHITE
        toolbar.isOpaque = false
      }
    }
  }
}


private class LikeReactionAction() : ReactionAction(CommonBundle.message("button.without.mnemonic.yes"), AllIcons.Ide.LikeDimmed,
                                                                                          AllIcons.Ide.Like,
                                                                                          AllIcons.Ide.LikeSelected) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return getReactionStateChecker(e)?.checkState(ReactionChecker.State.Liked) == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    getReactionStateChecker(e)?.onLike(e.project, e.place)
  }
}

private class DislikeUsefulAction() : ReactionAction(CommonBundle.message("button.without.mnemonic.no"), AllIcons.Ide.DislikeDimmed,
                                                                                           AllIcons.Ide.Dislike, AllIcons.Ide.DislikeSelected) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return getReactionStateChecker(e)?.checkState(ReactionChecker.State.Disliked) == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    getReactionStateChecker(e)?.onDislike(e.project, e.place)
  }

}

private abstract class ReactionAction(text: @NlsActions.ActionText String,
                                      val icon: Icon,
                                      val hoveredIcon: Icon,
                                      val selectedIcon: Icon) : AnAction(text, null, icon), DumbAware {

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
    Liked (1),
    Disliked (-1),
    Undefined (0);

    companion object {
      fun stateByIndex(ind: Int?): State {
        return ind?.let { values().firstOrNull { it.index == ind } ?: Undefined } ?: Undefined
      }
    }
  }

  fun onLike(project: Project?, place: String?)

  fun onDislike(project: Project?, place: String?)

  fun checkState(state: State): Boolean

  fun clearLikenessState()
}