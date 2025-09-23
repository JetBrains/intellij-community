// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * @author tav
 */
open class JCEFHtmlPanel(isOffScreenRendering: Boolean, client: JBCefClient?, url: String?) : JBCefBrowser(
  createBuilder().setOffScreenRendering(isOffScreenRendering).setClient(client).setUrl(url)) {
  private val url = cefBrowser.url

  constructor(url: String?) : this(cefClient, url)

  constructor(isOffScreenRendering: Boolean, url: String?) : this(
    isOffScreenRendering = isOffScreenRendering,
    client = cefClient,
    url = url,
  )

  // should no pass url to ctor
  constructor(client: JBCefClient?, url: String?) : this(isOffScreenRendering = true, client = client, url = url)

  @Internal
  companion object {
    private val cefClient by lazy {
      val client = JBCefApp.getInstance().createClient()
      Disposer.register(ApplicationManager.getApplication(), client)
      client
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
    loadHTML(htmlToRender, url)
  }

  protected open fun prepareHtml(html: String): String = html
}
