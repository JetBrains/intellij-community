// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel

/**
 * @author tav
 */
open class JCEFHtmlPanel(isOffScreenRendering: Boolean, client: JBCefClient?, url: String?) : JBCefBrowser(
  createBuilder().setOffScreenRendering(isOffScreenRendering).setClient(client).setUrl(url)) {
  private val myUrl: String

  constructor(url: String?) : this(ourCefClient, url)

  constructor(isOffScreenRendering: Boolean, url: String?) : this(isOffScreenRendering, ourCefClient, url)

  constructor(client: JBCefClient?, url: String?) : this(true, client, url) // should no pass url to ctor


  init {
    myUrl = getCefBrowser().getURL()
  }

  companion object {
    private val ourCefClient = JBCefApp.getInstance().createClient()

    init {
      Disposer.register(ApplicationManager.getApplication(), ourCefClient)
    }
  }

  override fun createDefaultContextMenuHandler(): DefaultCefContextMenuHandler {
    return object : DefaultCefContextMenuHandler() {
      override fun onBeforeContextMenu(browser: CefBrowser?, frame: CefFrame?, params: CefContextMenuParams?, model: CefMenuModel) {
        model.clear()
        super.onBeforeContextMenu(browser, frame, params, model)
      }
    }
  }

  open fun setHtml(html: String) {
    val htmlToRender = prepareHtml(html)
    loadHTML(htmlToRender, myUrl)
  }

  protected open fun prepareHtml(html: String): String = html
}
