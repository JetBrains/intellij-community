// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandler
import org.jetbrains.plugins.github.util.getName
import java.awt.event.ActionEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

internal abstract class GHListLoaderPanel<L : GHListLoader>(protected val listLoader: L,
                                                            private val contentComponent: JComponent,
                                                            private val loadAllAfterFirstScroll: Boolean = false)
  : BorderLayoutPanel(), Disposable {

  private var userScrolled = false
  val scrollPane = ScrollPaneFactory.createScrollPane(contentComponent,
                                                      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
    isOpaque = false
    viewport.isOpaque = false
    border = JBUI.Borders.empty()
    verticalScrollBar.model.addChangeListener { potentiallyLoadMore() }
    verticalScrollBar.model.addChangeListener { if (!userScrolled && verticalScrollBar.value > 0) userScrolled = true }
  }

  protected val infoPanel = HtmlInfoPanel()

  protected open val loadingText
    get() = ApplicationBundle.message("label.loading.page.please.wait")

  var errorHandler: GHLoadingErrorHandler? = null

  init {
    addToCenter(createCenterPanel(simplePanel(scrollPane).addToTop(infoPanel).apply {
      isOpaque = false
    }))

    listLoader.addLoadingStateChangeListener(this) {
      setLoading(listLoader.loading)
      updateEmptyText()
    }

    listLoader.addErrorChangeListener(this) {
      updateInfoPanel()
      updateEmptyText()
    }

    setLoading(listLoader.loading)
    updateInfoPanel()
    updateEmptyText()
  }

  abstract fun createCenterPanel(content: JComponent): JPanel

  abstract fun setLoading(isLoading: Boolean)

  private fun updateEmptyText() {
    val emptyText = (contentComponent as? ComponentWithEmptyText)?.emptyText ?: return
    emptyText.clear()
    if (listLoader.loading) {
      emptyText.text = loadingText
    }
    else {
      val error = listLoader.error
      if (error != null) {
        displayErrorStatus(emptyText, error)
      }
      else {
        displayEmptyStatus(emptyText)
      }
    }
  }

  private fun displayErrorStatus(emptyText: StatusText, error: Throwable) {
    emptyText.appendText(getErrorPrefix(!listLoader.hasLoadedItems), SimpleTextAttributes.ERROR_ATTRIBUTES)
      .appendSecondaryText(getLoadingErrorText(error), SimpleTextAttributes.ERROR_ATTRIBUTES, null)

    errorHandler?.getActionForError(error)?.let {
      emptyText.appendSecondaryText(" ${it.getName()}", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, it)
    }
  }

  protected open fun displayEmptyStatus(emptyText: StatusText) {
    emptyText.text = GithubBundle.message("list.empty")
    emptyText.appendSecondaryText(CommonBundle.message("action.refresh"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
      listLoader.reset()
    }
  }

  protected open fun updateInfoPanel() {
    val error = listLoader.error
    if (error != null && listLoader.hasLoadedItems) {
      val errorPrefix = getErrorPrefix(!listLoader.hasLoadedItems)
      val errorText = getLoadingErrorText(error, "<br/>")
      val action = errorHandler?.getActionForError(error)
      if (action != null) {
        //language=HTML
        infoPanel.setInfo("""<html><body>$errorPrefix<br/>$errorText<a href=''>&nbsp;${action.getName()}</a></body></html>""",
                          HtmlInfoPanel.Severity.ERROR) {
          action.actionPerformed(ActionEvent(infoPanel, ActionEvent.ACTION_PERFORMED, it.eventType.toString()))
        }

      }
      else {
        //language=HTML
        infoPanel.setInfo("""<html><body>$errorPrefix<br/>$errorText</body></html>""",
                          HtmlInfoPanel.Severity.ERROR)
      }
    }
    else infoPanel.setInfo(null)
  }

  protected open fun getErrorPrefix(listEmpty: Boolean) = if (listEmpty) GithubBundle.message("cannot.load.list")
  else GithubBundle.message("cannot.load.full.list")

  private fun potentiallyLoadMore() {
    if (listLoader.canLoadMore() && ((userScrolled && loadAllAfterFirstScroll) || isScrollAtThreshold())) {
      listLoader.loadMore()
    }
  }

  private fun isScrollAtThreshold(): Boolean {
    val verticalScrollBar = scrollPane.verticalScrollBar
    val visibleAmount = verticalScrollBar.visibleAmount
    val value = verticalScrollBar.value
    val maximum = verticalScrollBar.maximum
    if (maximum == 0) return false
    val scrollFraction = (visibleAmount + value) / maximum.toFloat()
    if (scrollFraction < 0.5) return false
    return true
  }

  override fun dispose() {}

  companion object {
    private fun getLoadingErrorText(error: Throwable, newLineSeparator: String = "\n"): String {
      if (error is GithubStatusCodeException && error.error != null) {
        val githubError = error.error!!
        val builder = StringBuilder(githubError.message)
        if (githubError.errors.isNotEmpty()) {
          builder.append(": ").append(newLineSeparator)
          for (e in githubError.errors) {

            builder.append(e.message ?: GithubBundle.message("gql.error.in.field", e.code, e.resource, e.field.orEmpty())).append(
              newLineSeparator)
          }
        }
        return builder.toString()
      }

      return error.message?.let { addDotIfNeeded(it) } ?: GithubBundle.message("unknown.loading.error")
    }

    private fun addDotIfNeeded(line: String) = if (line.endsWith('.')) line else "$line."
  }
}