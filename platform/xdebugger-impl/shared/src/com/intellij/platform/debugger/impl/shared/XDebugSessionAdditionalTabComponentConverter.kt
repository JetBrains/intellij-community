// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface XDebugSessionAdditionalTabComponentConverter {
  companion object {
    val EP_NAME: ExtensionPointName<XDebugSessionAdditionalTabComponentConverter> = ExtensionPointName.create<XDebugSessionAdditionalTabComponentConverter>("com.intellij.xdebugger.additionalTabComponentConverter")
  }

  // TODO: provide more strict CoroutineScope, so when tab component is removed, tab id is disposed
  fun convertToId(project: Project, sessionTabScope: CoroutineScope, component: JComponent): XDebuggerSessionAdditionalTabId?

  fun getComponent(project: Project, id: XDebuggerSessionAdditionalTabId): JComponent?
}

typealias XDebuggerSessionAdditionalTabId = Int