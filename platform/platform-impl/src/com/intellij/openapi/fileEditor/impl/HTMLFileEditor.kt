// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.beans.PropertyChangeListener

internal class HTMLFileEditor private constructor() : UserDataHolderBase(), FileEditor {
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this)
  private val contentPanel = JCEFHtmlPanel(null)
  private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.SWING_THREAD, this)

  private val multiPanel = object : MultiPanel() {
    override fun create(key: Int) = when (key) {
      LOADING_KEY -> loadingPanel
      CONTENT_KEY -> contentPanel.component
      else -> throw IllegalArgumentException("Unknown key: ${key}")
    }
  }

  private lateinit var fallback: String

  constructor(html: String) : this() {
    contentPanel.loadHTML(html)
    fallback = ""
  }

  constructor(url: String, timeoutHtml: String? = null) : this() {
    contentPanel.loadURL(url)
    fallback = if (!timeoutHtml.isNullOrEmpty()) timeoutHtml else EditorBundle.message("message.html.editor.timeout")
  }

  init {
    loadingPanel.setLoadingText(CommonBundle.getLoadingTreeNodeText())

    contentPanel.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadStart(browser: CefBrowser, frame: CefFrame, transitionType: CefRequest.TransitionType?) {
        if (fallback.isNotEmpty()) {
          alarm.addRequest({ contentPanel.setHtml(fallback)}, Registry.intValue("html.editor.timeout", 10000))
        }
      }

      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        alarm.cancelAllRequests()
      }

      override fun onLoadingStateChange(browser: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
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
    }, contentPanel.cefBrowser)

    contentPanel.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
      override fun onBeforeBrowse(browser: CefBrowser, frame: CefFrame, request: CefRequest, userGesture: Boolean, isRedirect: Boolean): Boolean =
        if (userGesture) { BrowserUtil.browse(request.url); true }
        else false
    }, contentPanel.cefBrowser)

    multiPanel.select(CONTENT_KEY, true)
  }

  override fun getComponent(): MultiPanel = multiPanel
  override fun getPreferredFocusedComponent(): MultiPanel = multiPanel
  override fun getName(): String = IdeBundle.message("tab.title.html.preview")
  override fun setState(state: FileEditorState) { }
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) { }
  override fun removePropertyChangeListener(listener: PropertyChangeListener) { }
  override fun getCurrentLocation(): FileEditorLocation? = null
  override fun dispose() { }

  companion object {
    private const val LOADING_KEY = 1
    private const val CONTENT_KEY = 0
  }
}
