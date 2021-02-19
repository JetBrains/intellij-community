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
import com.intellij.ui.jcef.JBCefBrowserBase.ErrorPage
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
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
    fallback = ""
    contentPanel.setErrorPage(ErrorPage.DEFAULT)
    contentPanel.loadHTML(html)
  }

  constructor(url: String, timeoutHtml: String? = null) : this() {
    fallback = if (!timeoutHtml.isNullOrEmpty()) timeoutHtml else EditorBundle.message("message.html.editor.timeout")
    contentPanel.setErrorPage(if (!timeoutHtml.isNullOrEmpty()) ErrorPage { _, _, _ -> timeoutHtml } else ErrorPage.DEFAULT)
    contentPanel.loadURL(url)
  }

  init {
    loadingPanel.setLoadingText(CommonBundle.getLoadingTreeNodeText())

    contentPanel.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadingStateChange(browser: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        if (isLoading) {
          if (fallback.isNotEmpty()) {
            alarm.addRequest({ contentPanel.loadHTML(fallback) }, Registry.intValue("html.editor.timeout", 10000))
          }
          invokeLater {
            loadingPanel.startLoading()
            multiPanel.select(LOADING_KEY, true)
          }
        }
        else {
          alarm.cancelAllRequests()
          invokeLater {
            loadingPanel.stopLoading()
            multiPanel.select(CONTENT_KEY, true)
          }
        }
      }
    }, contentPanel.cefBrowser)

    contentPanel.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
      override fun onBeforePopup(browser: CefBrowser, frame: CefFrame, targetUrl: String, targetFrameName: String?): Boolean {
        BrowserUtil.browse(targetUrl)
        return true
      }
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
