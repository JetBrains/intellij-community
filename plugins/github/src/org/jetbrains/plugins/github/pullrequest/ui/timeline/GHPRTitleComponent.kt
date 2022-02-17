// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModel
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel

internal object GHPRTitleComponent {

  fun create(project: Project, model: SingleValueModel<GHPullRequestShort>, detailsDataProvider: GHPRDetailsDataProvider): JComponent {
    val icon = JLabel()
    val titlePane = HtmlEditorPane().apply {
      font = font.deriveFont((font.size * 1.5).toFloat())
    }
    val editButton = GHTextActions.createEditButton()

    model.addAndInvokeListener {
      icon.icon = GHUIUtil.getPullRequestStateIcon(model.value.state, model.value.isDraft)
      titlePane.setBody(getTitleBody(model.value.title, model.value.number.toString()))
    }

    val fieldPanel = layout(icon, titlePane, editButton)

    val paneHandle = GHEditableHtmlPaneHandle(project, fieldPanel, { model.value.title }) { newText ->
      detailsDataProvider.updateDetails(EmptyProgressIndicator(), title = newText)
        .successOnEdt { titlePane.setBody(getTitleBody(newText, model.value.number.toString())) }
    }

    editButton.actionListener = ActionListener {
      paneHandle.showAndFocusEditor()
    }

    editButton.isVisible = model.value.viewerCanUpdate
    return paneHandle.panel
  }

  fun create(detailsModel: GHPRDetailsModel): JComponent {
    val icon = JLabel()
    val title = HtmlEditorPane().apply {
      font = font.deriveFont((font.size * 1.2).toFloat())
    }

    detailsModel.addAndInvokeDetailsChangedListener {
      icon.icon = GHUIUtil.getPullRequestStateIcon(detailsModel.state, detailsModel.isDraft)
      title.setBody(getTitleBody(detailsModel.title, detailsModel.number))
    }

    return layout(icon, title)
  }

  @NlsSafe
  private fun getTitleBody(@NlsSafe title: String, @NlsSafe number: String): String {
    val contextHelpColorText = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())
    //language=html
    return HtmlBuilder()
      .append(title)
      .nbsp()
      .append(HtmlChunk.span("color: $contextHelpColorText").addText("#${number}"))
      .toString()
  }

  private fun layout(icon: JLabel, title: JComponent, editButton: JComponent? = null): NonOpaquePanel {
    return NonOpaquePanel(MigLayout(LC().insets("0").gridGap("0", "0").fill())).apply {
      add(icon, CC().gapRight("${JBUIScale.scale(4)}"))
      add(title, CC().push())
      if (editButton != null) add(editButton, CC().gapLeft("${JBUIScale.scale(12)}").hideMode(3))
    }
  }
}