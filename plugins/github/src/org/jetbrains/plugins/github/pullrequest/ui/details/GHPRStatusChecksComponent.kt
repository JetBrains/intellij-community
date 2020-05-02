// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object GHPRStatusChecksComponent {

  fun create(model: SingleValueModel<GHPRMergeabilityState>): JComponent {

    val panel = JPanel(FlowLayout(FlowLayout.LEADING, 0, 0))
    Controller(model, panel)
    return panel
  }

  private class Controller(private val model: SingleValueModel<GHPRMergeabilityState>,
                           private val panel: JPanel) {

    init {
      update()

      model.addValueChangedListener {
        update()
      }
    }

    private fun update() {
      panel.removeAll()
      val mergeability = model.value
      val checksState = mergeability.checksState
      if (checksState == GHPRMergeabilityState.ChecksState.NONE) {
        panel.isVisible = false
        return
      }
      else {
        val label = JLabel().apply {
          icon = when (checksState) {
            GHPRMergeabilityState.ChecksState.BLOCKING_BEHIND,
            GHPRMergeabilityState.ChecksState.BLOCKING_FAILING -> AllIcons.RunConfigurations.TestError
            GHPRMergeabilityState.ChecksState.FAILING -> AllIcons.RunConfigurations.TestFailed
            GHPRMergeabilityState.ChecksState.PENDING -> AllIcons.RunConfigurations.TestNotRan
            GHPRMergeabilityState.ChecksState.SUCCESSFUL -> AllIcons.RunConfigurations.TestPassed
            else -> EmptyIcon.ICON_16
          }
          text = when (checksState) {
            GHPRMergeabilityState.ChecksState.BLOCKING_BEHIND -> GithubBundle.message("pull.request.branch.out.of.sync")
            GHPRMergeabilityState.ChecksState.BLOCKING_FAILING,
            GHPRMergeabilityState.ChecksState.FAILING,
            GHPRMergeabilityState.ChecksState.PENDING,
            GHPRMergeabilityState.ChecksState.SUCCESSFUL -> getChecksResultsText(mergeability.failedChecks,
                                                                                 mergeability.pendingChecks,
                                                                                 mergeability.successfulChecks)
            else -> ""
          }
        }

        with(panel) {
          add(label)
          add(createLink(mergeability.htmlUrl))
          validate()
          repaint()
          isVisible = true
        }
      }
    }
  }

  private fun getChecksResultsText(failedChecks: Int, pendingChecks: Int, successfulChecks: Int): String {
    val results = mutableListOf<String>()
    failedChecks.takeIf { it > 0 }?.let {
      GithubBundle.message("pull.request.checks.failing", it)
    }?.also {
      results.add(it)
    }

    pendingChecks.takeIf { it > 0 }?.let {
      GithubBundle.message("pull.request.checks.pending", it)
    }?.also {
      results.add(it)
    }

    successfulChecks.takeIf { it > 0 }?.let {
      GithubBundle.message("pull.request.checks.successful", it)
    }?.also {
      results.add(it)
    }

    val checksText = if (failedChecks + pendingChecks + successfulChecks == 1) GithubBundle.message("pull.request.check")
    else GithubBundle.message("pull.request.checks")

    return StringUtil.join(results, ", ") + " " + checksText
  }

  private fun createLink(url: String) =
    HyperlinkLabel(GithubBundle.message("open.in.browser.link")).apply {
      setHyperlinkTarget(url)
    }
}