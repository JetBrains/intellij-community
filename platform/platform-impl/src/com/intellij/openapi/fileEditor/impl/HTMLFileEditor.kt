// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import com.intellij.CommonBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.AlarmFactory
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.beans.PropertyChangeListener

class HTMLFileEditor(url: String? = null, html: String? = null,
                     var timeoutCallback: String? = EditorBundle.message("message.html.editor.timeout") ) : FileEditor {
  private val htmlPanelComponent = JCEFHtmlPanel(null)
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this).apply { setLoadingText(CommonBundle.getLoadingTreeNodeText()) }
  private val alarm = AlarmFactory.getInstance().create()

  private val multiPanel: MultiPanel = object : MultiPanel() {
    override fun create(key: Int) = when (key) {
      LOADING_KEY -> loadingPanel
      CONTENT_KEY -> htmlPanelComponent.component
      else -> throw UnsupportedOperationException("Unknown key")
    }
  }

  init {
    if (url != null) { htmlPanelComponent.loadURL(url) }
    if (html != null) { htmlPanelComponent.loadHTML(html) }
    multiPanel.select(CONTENT_KEY, true)
  }

  init {
    htmlPanelComponent.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
        alarm.addRequest({ htmlPanelComponent.setHtml(timeoutCallback!!) }, Registry.intValue("html.editor.timeout", 10000))
      }

      override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
        alarm.cancelAllRequests()
      }

      override fun onLoadingStateChange(browser: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        if (isLoading) {
          invokeLater {
            loadingPanel.startLoading()
            multiPanel.select(LOADING_KEY, true)
          }
        }
        else {
          invokeLater {
            loadingPanel.stopLoading()
            multiPanel.select(CONTENT_KEY, true)
          }
        }
      }
    }, htmlPanelComponent.cefBrowser)
  }

  override fun getComponent() = multiPanel
  override fun getPreferredFocusedComponent() = multiPanel
  override fun getName() = "HTML Preview"
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun <T : Any?> getUserData(key: Key<T>): T? = null
  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getCurrentLocation(): FileEditorLocation? = null
  override fun dispose() {
    alarm.dispose()
  }

  companion object {
    private const val LOADING_KEY = 1
    private const val CONTENT_KEY = 0
  }
}