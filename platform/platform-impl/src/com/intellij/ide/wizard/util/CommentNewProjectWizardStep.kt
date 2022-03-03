// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.util

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.event.HyperlinkEvent

abstract class CommentNewProjectWizardStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {
  abstract val comment: @Nls(capitalization = Nls.Capitalization.Sentence) String

  open val isFullWidth = true

  open fun onHyperlinkActivated(e: HyperlinkEvent) {
    HyperlinkEventAction.HTML_HYPERLINK_INSTANCE.hyperlinkActivated(e)
  }

  override fun setupUI(builder: Panel) {
    with(builder) {
      val row = when (isFullWidth) {
        true -> row(init = ::setupCommentUi)
        else -> row(EMPTY_LABEL, init = ::setupCommentUi)
      }
      row.bottomGap(BottomGap.SMALL)
    }
  }

  private fun setupCommentUi(builder: Row) {
    builder.text(comment, action = ::onHyperlinkActivated)
      .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
  }
}