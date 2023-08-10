// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.jcef.JBCefBrowserBase.ErrorPage
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.Alarm
import com.intellij.util.namedChildScope
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.*
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

internal class HTMLFileEditor(private val project: Project, private val file: LightVirtualFile, request: HTMLEditorProvider.Request) : UserDataHolderBase(), FileEditor {
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this)
  private val contentPanel = JCEFHtmlPanel(true, null, null)
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val initial = AtomicBoolean(true)
  private val navigating = AtomicBoolean(false)
  @Suppress("DEPRECATION")
  private val htmlTabScope = project.coroutineScope.namedChildScope("HTMLFileEditor[${file.name}]")

  private val multiPanel = object : MultiPanel() {
    override fun create(key: Int): JComponent = when (key) {
      LOADING_KEY -> loadingPanel
      CONTENT_KEY -> contentPanel.component
      else -> throw IllegalArgumentException("Unknown key: ${key}")
    }

    override fun select(key: Int, now: Boolean): ActionCallback {
      val callback = super.select(key, now)
      if (key == CONTENT_KEY) {
        UIUtil.invokeLaterIfNeeded { contentPanel.component.requestFocusInWindow() }
      }
      return callback
    }
  }

  init {
    loadingPanel.setLoadingText(CommonBundle.getLoadingTreeNodeText())

    contentPanel.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadingStateChange(browser: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        if (initial.get()) {
          if (isLoading) {
            invokeLater {
              loadingPanel.startLoading()
              multiPanel.select(LOADING_KEY, true)
            }
          }
          else {
            alarm.cancelAllRequests()
            initial.set(false)
            invokeLater {
              loadingPanel.stopLoading()
              multiPanel.select(CONTENT_KEY, true)
            }
          }
        }
      }
    }, contentPanel.cefBrowser)

    contentPanel.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
      override fun onBeforeBrowse(browser: CefBrowser, frame: CefFrame, request: CefRequest, userGesture: Boolean, isRedirect: Boolean): Boolean =
        if (userGesture) { navigating.set(true); browse(request.url); true }
        else false
    }, contentPanel.cefBrowser)

    contentPanel.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
      override fun onBeforePopup(browser: CefBrowser, frame: CefFrame, targetUrl: String, targetFrameName: String?): Boolean {
        browse(targetUrl)
        return true
      }
    }, contentPanel.cefBrowser)

    val queryHandler = request.queryHandler
    if (queryHandler != null) {
      val config = CefMessageRouter.CefMessageRouterConfig(HTMLEditorProvider.JS_FUNCTION_NAME, "${HTMLEditorProvider.JS_FUNCTION_NAME}Cancel")
      val jsRouter = CefMessageRouter.create(config)
      jsRouter.addHandler(object : CefMessageRouterHandlerAdapter() {
        override fun onQuery(browser: CefBrowser, frame: CefFrame, id: Long, request: String?, persistent: Boolean, callback: CefQueryCallback): Boolean {
          htmlTabScope.launch {
            runCatching { queryHandler.query(id, request ?: "") }
              .onSuccess { callback.success(it) }
              .onFailure { callback.failure(-1, "${it.javaClass}: ${it.message}") }
          }
          return true
        }
      }, true)
      contentPanel.jbCefClient.cefClient.addMessageRouter(jsRouter)
    }

    contentPanel.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
      override fun onStatusMessage(browser: CefBrowser, text: @NlsSafe String) =
        StatusBar.Info.set(text, project)
    }, contentPanel.cefBrowser)

    contentPanel.setErrorPage { errorCode, errorText, failedUrl ->
      if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED && navigating.getAndSet(false)) null
      else ErrorPage.DEFAULT.create(errorCode, errorText, failedUrl)
    }

    multiPanel.select(CONTENT_KEY, true)

    if (request.url != null) {
      val timeoutText = request.timeoutHtml ?: EditorBundle.message("message.html.editor.timeout")
      alarm.addRequest({ contentPanel.loadHTML(timeoutText) }, Registry.intValue("html.editor.timeout", URL_LOADING_TIMEOUT_MS))
      contentPanel.loadURL(request.url)
    }
    else {
      contentPanel.loadHTML(request.html!!)
    }
  }

  private fun browse(url: String) =
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url))

  override fun getComponent(): JComponent = multiPanel
  override fun getPreferredFocusedComponent(): JComponent = multiPanel
  override fun getName(): String = IdeBundle.message("tab.title.html.preview")
  override fun setState(state: FileEditorState) { }
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) { }
  override fun removePropertyChangeListener(listener: PropertyChangeListener) { }
  override fun dispose() { htmlTabScope.cancel() }
  override fun getFile(): VirtualFile = file

  private companion object {
    private const val LOADING_KEY = 1
    private const val CONTENT_KEY = 0
    private const val URL_LOADING_TIMEOUT_MS = 10000
  }
}
