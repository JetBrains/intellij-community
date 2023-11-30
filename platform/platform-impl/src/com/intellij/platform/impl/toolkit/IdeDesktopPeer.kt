// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.platform.impl.toolkit

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import java.awt.Desktop
import java.awt.peer.DesktopPeer
import java.io.File
import java.net.URI

internal class IdeDesktopPeer : DesktopPeer {
  companion object {
    private val LOG: Logger = logger<IdeDesktopPeer>()

    val clientInstance: ClientDesktopPeer
      get() = service()
  }

  override fun isSupported(action: Desktop.Action): Boolean {
    return action in setOf(Desktop.Action.OPEN, Desktop.Action.EDIT, Desktop.Action.PRINT, Desktop.Action.MAIL, Desktop.Action.BROWSE)
  }

  override fun open(file: File) {
    LOG.error("open ignored")
  }

  override fun edit(file: File) {
    LOG.error("edit ignored")
  }

  override fun print(file: File) {
    LOG.error("print ignored")
  }

  override fun mail(mailtoURL: URI) {
    LOG.error("mail ignored")
  }

  override fun browse(uri: URI) {
    clientInstance.browse(uri)
  }
}
