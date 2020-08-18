// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JLabel

internal object GHPRTitleComponent {

  fun create(model: SingleValueModel<GHPullRequestShort>, detailsDataProvider: GHPRDetailsDataProvider): JComponent {
    val icon = JLabel()
    val title = HtmlEditorPane().apply {
      font = font.deriveFont((font.size * 1.5).toFloat())
    }

    model.addAndInvokeValueChangedListener {
      icon.icon = GithubUIUtil.getPullRequestStateIcon(model.value.state, model.value.isDraft)
      title.setBody(getTitleBody(model.value.title, model.value.number.toString()))
    }

    if (model.value.viewerCanUpdate) {
      val panelHandle = object : GHEditableHtmlPaneHandle(title,
                                                          { CompletableFuture.completedFuture(model.value.title) },
                                                          { newText ->
                                                            detailsDataProvider.updateDetails(EmptyProgressIndicator(),
                                                                                              title = newText).successOnEdt {
                                                              title.setBody(getTitleBody(newText, model.value.number.toString()))
                                                            }
                                                          }) {
        override fun wrapEditorPane(editorPane: HtmlEditorPane): JComponent {
          val editButton = GHTextActions.createEditButton(this)
          return layout(icon, editorPane, editButton)
        }
      }

      return panelHandle.panel
    }
    else {
      return layout(icon, title)
    }
  }

  fun create(detailsModel: GHPRDetailsModel): JComponent {
    val icon = JLabel()
    val title = HtmlEditorPane().apply {
      font = font.deriveFont((font.size * 1.2).toFloat())
    }

    detailsModel.addAndInvokeDetailsChangedListener {
      icon.icon = GithubUIUtil.getPullRequestStateIcon(detailsModel.state, detailsModel.isDraft)
      title.setBody(getTitleBody(detailsModel.title, detailsModel.number))
    }

    return layout(icon, title)
  }

  private fun getTitleBody(title: String, number: String): String {
    val contextHelpColorText = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())
    //language=html
    return title + "&nbsp<span style='color: $contextHelpColorText'>#${number}</span>"
  }

  private fun layout(icon: JLabel, title: HtmlEditorPane, editButton: JComponent? = null): NonOpaquePanel {
    return NonOpaquePanel(MigLayout(LC().insets("0").gridGap("0", "0").fill())).apply {
      add(icon, CC().gapRight("${UI.scale(4)}"))
      add(title, CC().push())
      if (editButton != null) add(editButton, CC().gapLeft("${UI.scale(12)}"))
    }
  }
}