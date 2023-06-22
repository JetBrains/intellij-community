// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.Nls

enum class AttachDialogHostType(@Nls @NlsContexts.Button val displayText: String) {
  LOCAL(XDebuggerBundle.message("xdebugger.local.attach.button.name")),
  REMOTE(XDebuggerBundle.message("xdebugger.remote.attach.button.name")),
  DOCKER(XDebuggerBundle.message("xdebugger.docker.attach.button.name"))
}