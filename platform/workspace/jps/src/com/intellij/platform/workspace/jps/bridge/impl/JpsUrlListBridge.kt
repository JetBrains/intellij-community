// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl

import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.jps.model.JpsUrlList
import org.jetbrains.jps.model.ex.JpsElementBase

internal class JpsUrlListBridge(private val urls: List<VirtualFileUrl>, parentElement: JpsElementBase<*>) : JpsElementBase<JpsUrlListBridge>(), JpsUrlList {
  init {
    parent = parentElement
  }
  
  override fun getUrls(): List<String> = urls.map { it.url }

  override fun addUrl(url: String) {
    reportModificationAttempt()
  }

  override fun removeUrl(url: String) {
    reportModificationAttempt()
  }
}
