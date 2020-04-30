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
import org.jetbrains.plugins.github.ui.util.BoundedRangeModelThresholdListener
import org.jetbrains.plugins.github.util.getName
import java.awt.event.ActionEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

internal abstract class GHListLoaderPanel(private val listLoader: GHListLoader<*>,
                                          private val contentComponent: JComponent,
                                          private val loadAllAfterFirstScroll: Boolean = false)
  : BorderLayoutPanel(), Disposable {

  val scrollPane = ScrollPaneFactory.createScrollPane(contentComponent,
                                                      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
    isOpaque = false
    viewport.isOpaque = false
    border = JBUI.Borders.empty()
    if (!loadAllAfterFirstScroll) {
      BoundedRangeModelThresholdListener.install(verticalScrollBar) {
        potentiallyLoadMore()
      }
    }
    else {
      verticalScrollBar.model.addChangeListener(object : ChangeListener {
        private var firstScroll = true

        override fun stateChanged(e: ChangeEvent) {
          if (firstScroll && verticalScrollBar.value > 0) firstScroll = false
          if (!firstScroll) potentiallyLoadMore()
        }
      })
    }
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
    emptyText.appendText(getErrorPrefix(listLoader.loadedData.isEmpty()), SimpleTextAttributes.ERROR_ATTRIBUTES)
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
    val listEmpty = listLoader.loadedData.isEmpty()
    if (error != null && !listEmpty) {
      val errorPrefix = getErrorPrefix(listEmpty)
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
    if (listLoader.canLoadMore()) {
      listLoader.loadMore()
    }
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