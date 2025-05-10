// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.project.ProjectId
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.impl.rpc.RemoteXDebuggerConsoleViewData
import com.intellij.xdebugger.impl.rpc.XDebuggerConsoleViewData
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

