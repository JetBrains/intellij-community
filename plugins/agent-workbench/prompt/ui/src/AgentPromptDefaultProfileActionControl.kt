// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil

internal class AgentPromptDefaultProfileActionControl {
  val component: ActionLink = ActionLink(AgentPromptBundle.message("popup.profile.make.default")).apply {
    autoHideOnDisable = false
    withFont(JBUI.Fonts.smallFont())
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty()
    isVisible = false
  }

  private var actionHandler: (() -> Unit)? = null

  init {
    component.addActionListener { actionHandler?.invoke() }
    setState(null)
  }

  fun setActionHandler(handler: () -> Unit) {
    actionHandler = handler
  }

  fun setState(state: AgentPromptDefaultProfileActionState?) {
    component.isVisible = state != null
    component.isEnabled = state != null
    val presentation = state?.presentation ?: AgentPromptDefaultProfileActionPresentation.MAKE_DEFAULT
    val text = AgentPromptBundle.message(presentation.textKey)
    component.text = text
    component.setToolTipText(HtmlChunk.text(AgentPromptBundle.message(presentation.descriptionKey)))
    component.accessibleContext.accessibleName = text
  }
}

internal enum class AgentPromptDefaultProfileActionState(
  val presentation: AgentPromptDefaultProfileActionPresentation,
) {
  MAKE_DEFAULT(AgentPromptDefaultProfileActionPresentation.MAKE_DEFAULT),
  SAVE_AS_DEFAULT(AgentPromptDefaultProfileActionPresentation.SAVE_AS_DEFAULT),
}

internal enum class AgentPromptDefaultProfileActionPresentation(
  val textKey: String,
  val descriptionKey: String,
) {
  MAKE_DEFAULT(
    textKey = "popup.profile.make.default",
    descriptionKey = "popup.profile.make.default.description",
  ),
  SAVE_AS_DEFAULT(
    textKey = "popup.profile.save.as.default",
    descriptionKey = "popup.profile.save.as.default.description",
  ),
}
