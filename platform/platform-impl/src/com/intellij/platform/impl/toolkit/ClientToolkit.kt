// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.components.service
import java.awt.*
import java.awt.peer.DialogPeer
import java.awt.peer.FramePeer
import java.awt.peer.WindowPeer

interface ClientToolkit {
  companion object {
    fun getInstance(): ClientToolkit = service()
  }
  fun createWindow(target: Window): WindowPeer
  fun createDialog(target: Dialog): DialogPeer
  fun createFrame(target: Frame): FramePeer
}