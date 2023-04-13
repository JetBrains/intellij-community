// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rdserver.unattendedHost.browser.UnattendedHostUrlOpener
import java.awt.Desktop
import java.awt.peer.DesktopPeer
import java.io.File
import java.net.URI

class IdeDesktopPeer : DesktopPeer {
  companion object {
    val logger = logger<IdeDesktopPeer>()
  }
  override fun isSupported(action: Desktop.Action) =
    action in setOf(Desktop.Action.OPEN, Desktop.Action.EDIT, Desktop.Action.PRINT, Desktop.Action.MAIL, Desktop.Action.BROWSE)

  override fun open(file: File) {
    logger.error("open ignored")
  }

  override fun edit(file: File) {
    logger.error("edit ignored")
  }

  override fun print(file: File) {
    logger.error("print ignored")
  }

  override fun mail(mailtoURL: URI) {
    logger.error("mail ignored")
  }

  override fun browse(uri: URI) {
    UnattendedHostUrlOpener.openUrlOnControllerWithPortForward(uri.toASCIIString(), null)
  }
}
