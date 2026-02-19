// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.components.service
import java.awt.Dialog
import java.awt.Frame
import java.awt.GraphicsDevice
import java.awt.Window
import java.awt.peer.DialogPeer
import java.awt.peer.FramePeer
import java.awt.peer.RobotPeer
import java.awt.peer.WindowPeer

interface ClientToolkit {
  companion object {
    fun getInstance(): ClientToolkit = service()
  }
  fun createWindow(target: Window): WindowPeer
  fun createDialog(target: Dialog): DialogPeer
  fun createFrame(target: Frame): FramePeer
  fun createRobot(screen: GraphicsDevice?): RobotPeer
}