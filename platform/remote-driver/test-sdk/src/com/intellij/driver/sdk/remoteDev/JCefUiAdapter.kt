package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.sdk.ui.components.common.JCefUI
import com.intellij.driver.sdk.ui.components.common.JcefComponentWrapper

class JCefUiAdapter(component: BeControlComponentBase) :
  BeControlComponentBase(component.driver, component.frontendComponent, component.backendComponent),
  JcefComponentWrapper {
  private val fixture = onFrontend(JCefUI::class) { byType("com.intellij.ui.jcef.JBCefBrowser${"$"}MyPanel") }.jcefWorker

  override fun runJs(js: String) {
    fixture.runJs(js)
  }

  override fun callJs(js: String, executeTimeoutMs: Long): String {
    return fixture.callJs(js, executeTimeoutMs)
  }

  override fun hasDocument(): Boolean {
    return fixture.hasDocument()
  }

  override fun getUrl(): String {
    return fixture.getUrl()
  }

  override fun isLoading(): Boolean {
    return fixture.isLoading()
  }
}