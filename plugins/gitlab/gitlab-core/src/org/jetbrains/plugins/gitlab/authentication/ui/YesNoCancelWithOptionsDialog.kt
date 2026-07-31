// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ExitActionType
import com.intellij.openapi.ui.OptionAction
import com.intellij.openapi.ui.messages.MessageDialog
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon

/**
 * A message dialog with a "Cancel" button, a single "No" button, and one or more "Yes" buttons collapsed into a split button.
 */
internal class YesNoCancelWithOptionsDialog<T>(
  project: Project?,
  parentComponent: Component?,
  title: @NlsContexts.DialogTitle String,
  message: @NlsContexts.DialogMessage String,
  private val yesChoices: List<Pair<@NlsContexts.Button String, T>>,
  private val noChoice: Pair<@NlsContexts.Button String, T>,
  icon: Icon? = UIUtil.getQuestionIcon(),
) : MessageDialog(project, parentComponent, false) {

  var chosenValue: T? = null
    private set

  init {
    // the options array will be built by createActions()
    _init(title, message, emptyArray(), -1, -1, icon, null, null, null, emptyArray())
  }

  override fun createActions(): Array<Action> {
    val yesActions = yesChoices.map { (text, value) -> choiceAction(text, value) }
    val yesAction = if (yesActions.size == 1) yesActions.single() else SplitAction(yesActions.first(), yesActions.drop(1))
    yesAction.putValue(DEFAULT_ACTION, true)
    yesAction.putValue(FOCUSED_ACTION, true)
    return arrayOf(cancelAction, choiceAction(noChoice.first, noChoice.second), yesAction)
  }

  private fun choiceAction(text: @NlsContexts.Button String, value: T): Action {
    val action = object : AbstractAction(text) {
      override fun actionPerformed(e: ActionEvent) {
        chosenValue = value
        close(OK_EXIT_CODE, true, ExitActionType.YES)
      }
    }
    return action
  }

  private class SplitAction(private val mainAction: Action, private val options: List<Action>) :
    AbstractAction(mainAction.getValue(NAME) as? String), OptionAction {

    override fun actionPerformed(e: ActionEvent) = mainAction.actionPerformed(e)
    override fun getOptions(): Array<Action> = options.toTypedArray()
  }
}
