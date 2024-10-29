// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.Nls

abstract class AttachDialogHostType(@Nls @NlsContexts.Button val displayText: String) {
  object LOCAL: AttachDialogHostType(XDebuggerBundle.message("xdebugger.local.attach.button.name"))
  object REMOTE: AttachDialogHostType(XDebuggerBundle.message("xdebugger.remote.attach.button.name"))
  object DOCKER: AttachDialogHostType(XDebuggerBundle.message("xdebugger.docker.attach.button.name"))
}