@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.impl.toolkit

import java.awt.*
import java.awt.peer.DialogPeer
import java.awt.peer.FramePeer
import java.awt.peer.WindowPeer

interface ClientToolkit {
  fun createPanelWindow(panel: Component, target: Window, realParent: Container?): WindowPeer
  fun createWindow(target: Window): WindowPeer
  fun createDialog(target: Dialog): DialogPeer
  fun createFrame(target: Frame): FramePeer
}