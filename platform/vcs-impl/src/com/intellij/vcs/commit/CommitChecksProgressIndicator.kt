// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.util.text.StringUtil.ELLIPSIS
import com.intellij.openapi.util.text.StringUtil.THREE_DOTS
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.impl.status.InlineProgressIndicator
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI.Borders.emptyTop
import java.awt.BorderLayout
import javax.swing.JPanel

internal class CommitChecksTaskInfo : TaskInfo {
  override fun getTitle(): String = message("progress.title.commit.checks")
  override fun getCancelText(): String = getCancelButtonText()
  override fun getCancelTooltipText(): String = cancelText
  override fun isCancellable(): Boolean = true
}

internal abstract class CommitChecksProgressIndicator : InlineProgressIndicator(true, CommitChecksTaskInfo()) {
  init {
    component.toolTipText = null

    addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun cancel() = updateProgress() // to show "Stopping" text right away
    })
  }

  override fun updateProgressNow() {
    super.updateProgressNow()
    setText2Enabled(false) // to set "gray" color
  }
}

internal class InlineCommitChecksProgressIndicator(isOnlyRunCommitChecks: Boolean) : CommitChecksProgressIndicator() {
  val statusBarDelegate: ProgressIndicatorEx = StatusBarProgressIndicator(isOnlyRunCommitChecks, this)

  init {
    addStateDelegate(statusBarDelegate)
  }

  override fun createCompactTextAndProgress(component: JPanel) {
    val detailsPanel = NonOpaquePanel(HorizontalLayout(6)).apply {
      border = emptyTop(5)

      add(myText)
      add(myText2)
    }

    component.add(myProgress, BorderLayout.CENTER)
    component.add(detailsPanel, BorderLayout.SOUTH)

    myText.recomputeSize()
    myText2.recomputeSize()
  }

  override fun setTextValue(text: String) {
    super.setTextValue(text)
    fixDoubleEllipsis()
  }

  override fun setText2Value(text: String) {
    super.setText2Value(text)
    fixDoubleEllipsis()
  }

  private fun fixDoubleEllipsis() {
    val text = textValue ?: return
    val text2 = text2Value ?: return

    if (text.endsWithEllipsis() && text2.startsWithEllipsis()) {
      setTextValue(text.removeEllipsisSuffix())
    }
  }

  private fun String.endsWithEllipsis(): Boolean = endsWith(ELLIPSIS) || endsWith(THREE_DOTS)
  private fun String.startsWithEllipsis(): Boolean = startsWith(ELLIPSIS) || startsWith(THREE_DOTS)
}

internal class PopupCommitChecksProgressIndicator(private val original: ProgressIndicatorEx) : CommitChecksProgressIndicator() {
  init {
    original.addStateDelegate(this)
  }

  override fun createCompactTextAndProgress(component: JPanel) {
    component.add(myText, BorderLayout.NORTH)
    component.add(myProgress, BorderLayout.CENTER)
    component.add(myText2, BorderLayout.SOUTH)

    myText.recomputeSize()
    myText2.recomputeSize()
  }

  override fun cancelRequest() = original.cancel()

  override fun isStopping(): Boolean = isCanceled
}

private class StatusBarProgressIndicator(
  private val isOnlyRunCommitChecks: Boolean,
  private val realIndicator: InlineCommitChecksProgressIndicator
) : AbstractProgressIndicatorExBase(false) {

  init {
    addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun cancel() {
        if (!realIndicator.isCanceled) { // avoid recursion - we're its state delegate
          realIndicator.cancel()
        }
      }
    })
  }

  override fun setText(text: String?) {
    // TODO: customize with AbstractCommitWorkflowHandlerKt.getDefaultCommitActionName
    val progressText = when {
      text != null -> when {
        isOnlyRunCommitChecks -> message("commit.checks.only.progress.text.with.context", text)
        else -> message("commit.checks.on.commit.progress.text.with.context", text)
      }
      else -> when {
        isOnlyRunCommitChecks -> message("commit.checks.only.progress.text")
        else -> message("commit.checks.on.commit.progress.text")
      }
    }
    super.setText(progressText)
  }
}
