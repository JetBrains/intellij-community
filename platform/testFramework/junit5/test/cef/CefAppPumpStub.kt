// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package org.cef

import java.awt.event.ActionEvent
import java.awt.event.ActionListener

/** A listener whose class name starts with `org.cef.CefApp`, as the listener of the message pump of JCEF in process. */
internal class CefAppPumpStub : ActionListener {
  override fun actionPerformed(e: ActionEvent?) {
  }
}
