// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.AlarmFactory
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.*
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

internal class HTMLFileEditor(private val project: Project, private val file: LightVirtualFile, url: String) : UserDataHolderBase(), FileEditor {
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this)
  private val contentPanel = JCEFHtmlPanel(null)
  private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.SWING_THREAD, this)
  private val initial = AtomicBoolean(true)
  private val navigating = AtomicBoolean(false)

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
        if (userGesture) { navigating.set(true); BrowserUtil.browse(request.url); true }
        else false
    }, contentPanel.cefBrowser)

    contentPanel.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
      override fun onBeforePopup(browser: CefBrowser, frame: CefFrame, targetUrl: String, targetFrameName: String?): Boolean {
        BrowserUtil.browse(targetUrl)
        return true
      }
    }, contentPanel.cefBrowser)

    contentPanel.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
      override fun onStatusMessage(browser: CefBrowser, @NlsSafe text: String) =
        StatusBar.Info.set(text, project)
    }, contentPanel.cefBrowser)

    contentPanel.setErrorPage { errorCode, errorText, failedUrl ->
      if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED && navigating.getAndSet(false)) null
      else ErrorPage.DEFAULT.create(errorCode, errorText, failedUrl)
    }

    multiPanel.select(CONTENT_KEY, true)

    if (url.isNotEmpty()) {
      if (file.content.isEmpty()) {
        file.setContent(this, EditorBundle.message("message.html.editor.timeout"), false)
      }
      alarm.addRequest({ contentPanel.loadHTML(file.content.toString()) }, Registry.intValue("html.editor.timeout", 10000))
      contentPanel.loadURL(url)
    }
    else {
      contentPanel.loadHTML(file.content.toString())
    }
  }

  override fun getComponent(): JComponent = multiPanel
  override fun getPreferredFocusedComponent(): JComponent = multiPanel
  override fun getName(): String = IdeBundle.message("tab.title.html.preview")
  override fun setState(state: FileEditorState) { }
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) { }
  override fun removePropertyChangeListener(listener: PropertyChangeListener) { }
  override fun getCurrentLocation(): FileEditorLocation? = null
  override fun dispose() { }

  override fun getFile(): VirtualFile = file

  private companion object {
    private const val LOADING_KEY = 1
    private const val CONTENT_KEY = 0
  }
}
