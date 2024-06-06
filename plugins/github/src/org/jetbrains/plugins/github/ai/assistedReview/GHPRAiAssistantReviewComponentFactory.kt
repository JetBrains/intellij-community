      // Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.bindIconIn
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.hover.HoverListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.jetbrains.plugins.github.ai.GithubAIBundle
import org.jetbrains.plugins.github.pullrequest.data.ai.comment.GHPRAIComment
import java.awt.*
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.event.ChangeListener

object GHPRAiAssistantReviewComponentFactory {
  fun create(scope: CoroutineScope, vm: GHPRAiAssistantReviewVm): JComponent {
    val reviewPanel = VerticalListPanel(10).apply {
      border = JBUI.Borders.empty(8)
    }
    val stickyHeaderComponents = mutableListOf<Pair<JComponent, () -> JComponent>>()
    val isLoading = MutableStateFlow(true)
    scope.launchNow(Dispatchers.EDT) {
      isLoading.collect {
        if (it) {
          reviewPanel.removeAll()
          reviewPanel.add(LoadingTextLabel())
        }
        reviewPanel.revalidate()
        reviewPanel.repaint()
      }
    }

    scope.launchNow(Dispatchers.EDT) {
      vm.state.collectScoped { review ->
        val cs = this
        if (review == null) {
          return@collectScoped
        }
        isLoading.value = false
        reviewPanel.removeAll()
        stickyHeaderComponents.clear()
        /*when (it) {
          is AiReviewSummaryReceived -> {
              reviewPanel.add(createAiResponseComponent(vm, it.summary, emptyList(), stickyHeaderComponents))
            reviewPanel.add(JLabel("Loading comments..."))
          }
          is AiReviewCompleted -> {
            reviewPanel.add(createAiResponseComponent(vm, it.summary, it.sortedFilesResponse, stickyHeaderComponents))
          }
          is AiReviewFailed -> {
            reviewPanel.add(JLabel("Error during AI review: ${it.error}"))
          }
          else -> {
            error("Invalid code path")
          }
        }*/
        reviewPanel.add(cs.createAiResponseComponent(vm, review, stickyHeaderComponents))
        reviewPanel.revalidate()
        reviewPanel.repaint()
      }
    }

    return withStickyHeader(
      ScrollPaneFactory.createScrollPane(reviewPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),
      stickyHeaderComponents
    )
  }

  private fun withStickyHeader(
    scrollPane: JScrollPane,
    componentsToCheckViewport: List<Pair<JComponent, () -> JComponent>>,
  ): JComponent {
    val stickyPanel = BorderLayoutPanel().apply {
      border = JBUI.Borders.empty(5, 8, 0, 8)
    }
    val viewPortListener = ChangeListener {
      val result = componentsToCheckViewport.lastOrNull { (component, _) ->
        component.bounds.y < scrollPane.viewport.viewPosition.y
      }

      if (result != null) {
        val provider = result.second
        stickyPanel.removeAll()
        val content = provider()
        stickyPanel.addToCenter(content)
        stickyPanel.setBounds(0, 0, scrollPane.width - JBUI.scale(12), JBUI.scale(35))
      }
      else {
        stickyPanel.setBounds(0, 0, 0, 0)
        stickyPanel.removeAll()
      }
      stickyPanel.revalidate()
      stickyPanel.repaint()
    }

    scrollPane.viewport.addChangeListener(viewPortListener)

    val contentPanel: JComponent = object : JBLayeredPane() {
      override fun getPreferredSize(): Dimension = scrollPane.preferredSize

      override fun doLayout() {
        scrollPane.setBounds(0, 0, width, height)
      }
    }.apply {
      isFocusable = false
      add(scrollPane, JLayeredPane.DEFAULT_LAYER, 0)
      add(stickyPanel, JLayeredPane.POPUP_LAYER, 1)
    }

    return contentPanel
  }

  private fun CoroutineScope.createAiResponseComponent(vm: GHPRAiAssistantReviewVm,
                                                       review: GHPRAIReview,
                                                       stickyHeaderComponents: MutableList<Pair<JComponent, () -> JComponent>>): JComponent {
    return VerticalListPanel(5).apply {
      add(createSummaryComponent(review.ideaHtml, review.summaryHtml))
      val files = review.files
      for (fileReview in files) {
        val fileReviewComponent = createFileReviewComponent(vm, fileReview) ?: continue
        stickyHeaderComponents.add(fileReviewComponent to {
          createSingleFileComponent(vm, fileReview)
        })
        add(fileReviewComponent)
      }
      if (!review.reviewCompleted) {
        add(LoadingTextLabel())
      }
    }
  }

  private fun CoroutineScope.createSummaryComponent(idea: String, summary: String?): JComponent {
    return VerticalListPanel(5).apply {
      border = JBUI.Borders.empty(10)
      add(JLabel(GithubAIBundle.message("pull.request.summary")).apply {
        font = font.deriveFont(Font.BOLD, 16f)
      })
      add(SimpleHtmlPane(idea))

      if(summary != null){
        val collapsed = MutableStateFlow(true)
        val link = ActionLink("") {
          collapsed.update { !it }
        }
        val summaryPane = SimpleHtmlPane(summary)

        launchNow {
          collapsed.collect {
            if (it) {
              link.text = GithubAIBundle.message("more.details")
              link.setIcon(AllIcons.Actions.ArrowExpand, true)
              summaryPane.isVisible = false
            }
            else {
              link.text = GithubAIBundle.message("hide.details")
              link.setIcon(AllIcons.Actions.ArrowCollapse, true)
              summaryPane.isVisible = true
            }
          }
        }

        add(link)
        add(summaryPane)
      }
    }
  }

  private fun CoroutineScope.createFileReviewComponent(vm: GHPRAiAssistantReviewVm, file: GHPRAIReviewFile): JComponent {
    return VerticalListPanel(5).apply {
      add(createSingleFileComponent(vm, file))
      file.comments.let {
        createFileHighlightsComponent(vm, file, it)
      }.also(::add)
    }
  }

  private fun createSingleFileComponent(vm: GHPRAiAssistantReviewVm, file: GHPRAIReviewFile): JComponent {
    val singleFilePanelContent = BorderLayoutPanel().apply {
      border = JBUI.Borders.empty(5, 10)
      background = JBColor(0xEBECF0, 0x494B57)
      val fileLink = ActionLink(file.req.path.name, ActionListener {
        vm.showDiffFor(file.req.changeToNavigate)
      }).apply {
        foreground = UIUtil.getLabelForeground()
        setIcon(file.req.path.fileType.icon)
        minimumSize = Dimension(0, 0)
      }
      addToCenter(fileLink)
    }

    val singleFilePanel = ClippingRoundedPanel(arcRadius = 5, layoutManager = BorderLayout()).apply {
      add(singleFilePanelContent, BorderLayout.CENTER)
    }

    singleFilePanel.addMouseHoverListener(null, object : HoverListener() {
      override fun mouseExited(component: Component) {
        singleFilePanelContent.background = JBColor(0xEBECF0, 0x494B57)
        singleFilePanelContent.repaint()
      }

      override fun mouseMoved(component: Component, x: Int, y: Int) {
      }

      override fun mouseEntered(component: Component, x: Int, y: Int) {
        singleFilePanelContent.background = Color(223, 225, 229)
        singleFilePanelContent.repaint()
      }
    })

    return singleFilePanel
  }

  private fun CoroutineScope.createFileHighlightsComponent(vm: GHPRAiAssistantReviewVm, file: GHPRAIReviewFile, highlights: List<GHPRAIComment>): JComponent {
    return VerticalListPanel(5).apply {
      border = JBUI.Borders.emptyLeft(5)
      for (highlight in highlights) {
        add(createAiCommentComponent(vm, file.req.changeToNavigate, highlight))
      }
    }
  }

  private fun CoroutineScope.createAiCommentComponent(vm: GHPRAiAssistantReviewVm, change: RefComparisonChange, comment: GHPRAIComment): JComponent {
    val cs = this
    val icon = JBLabel(AllIcons.General.InspectionsEye).apply {
      bindIconIn(cs, comment.accepted.combine(comment.rejected) { acc, rej ->
        if (acc) {
          AllIcons.General.InspectionsOK
        }
        else if (rej) {
          AllIcons.General.InspectionsPause
        }
        else {
          AllIcons.General.InspectionsEye
        }
      })
    }
    val lineLink = ActionLink(GithubAIBundle.message("line", comment.position.lineIndex)).apply {
      addActionListener {
        vm.showDiffFor(change, comment.position.lineIndex)
      }
    }
    val commentPanelL = SizeRestrictedSingleComponentLayout()
    val commentPane = SimpleHtmlPane(comment.textHtml)
    val commentPanel = JPanel(commentPanelL).apply {
      isOpaque = false
      add(commentPane)
    }

    val iconAndLine = JPanel(BorderLayout()).apply {
      add(
        HorizontalListPanel(5).apply {
          add(icon)
          add(lineLink)
        },
        BorderLayout.NORTH
      )
    }

    val reasonComponent = SimpleHtmlPane(comment.reasoningHtml).apply {
      foreground = UIUtil.getContextHelpForeground()
    }

    cs.launchNow {
      comment.accepted.combine(comment.rejected) { acc, rej -> acc || rej }.collect { hide ->
        reasonComponent.isVisible = !hide
        commentPanelL.maxSize =
          if (hide) DimensionRestrictions.LinesHeight(commentPanel, 1)
          else DimensionRestrictions.None
        commentPane.foreground = if (hide) UIUtil.getContextHelpForeground() else UIUtil.getLabelForeground()
      }
    }

    return BorderLayoutPanel().apply {
      addToLeft(iconAndLine)
      addToCenter(VerticalListPanel(5).apply {
        border = JBUI.Borders.emptyLeft(5)
        add(commentPanel)
        add(reasonComponent)
      })
    }
  }
}