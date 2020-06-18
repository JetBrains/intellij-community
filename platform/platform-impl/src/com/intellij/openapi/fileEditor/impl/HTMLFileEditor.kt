// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import com.intellij.CommonBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.jcef.JCEFHtmlPanel
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.beans.PropertyChangeListener

class HTMLFileEditor(url: String? = null, html: String? = null) : FileEditor {
  private val htmlPanelComponent = JCEFHtmlPanel(null)
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this).apply { setLoadingText(CommonBundle.getLoadingTreeNodeText()) }

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
  override fun dispose() {}

  companion object {
    const val LOADING_KEY = 1
    const val CONTENT_KEY = 0
  }
}