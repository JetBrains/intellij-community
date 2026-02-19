// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.rpc.AnActionId
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.project.ProjectId
import com.intellij.xdebugger.XDebugProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class XDebuggerConsoleViewData(@Serializable internal val remoteData: RemoteXDebuggerConsoleViewData? = null, @Transient internal val localConsole: ConsoleView? = null)

@ApiStatus.Internal
@Serializable
data class RemoteXDebuggerConsoleViewData(
  val projectId: ProjectId,
  val executionId: Long,
  val idBased: Boolean,
  val testFramework: String,
  val uniqueId: String,
  val consoleId: Int,
  val runnerLayoutUiId: Int?,
  val actionIds: List<AnActionId>,
)

@ApiStatus.Internal
suspend fun ConsoleView.toRpc(lifetimeScope: CoroutineScope, debugProcess: XDebugProcess?): XDebuggerConsoleViewData {
  val remoteData = XDebuggerConsoleViewConverter.EP_NAME.extensionList.firstNotNullOfOrNull {
    it.convert(this, lifetimeScope, debugProcess)
  }
  return XDebuggerConsoleViewData(remoteData, this)
}

@ApiStatus.Internal
suspend fun XDebuggerConsoleViewData.consoleView(processHandler: ProcessHandler): ConsoleView? {
  if (localConsole != null) {
    return localConsole
  }
  if (remoteData == null) {
    return null
  }
  val consoleView = XDebuggerConsoleViewConverter.EP_NAME.extensionList.firstNotNullOfOrNull {
    it.convert(remoteData, processHandler)
  }
  return consoleView
}

/**
 * Returns `null` if remote data is not available
 */
@ApiStatus.Internal
fun XDebuggerConsoleViewData.actionIds(): List<AnActionId>? {
  return remoteData?.actionIds
}

@ApiStatus.Internal
interface XDebuggerConsoleViewConverter {
  companion object {
    internal val EP_NAME = ExtensionPointName.create<XDebuggerConsoleViewConverter>("com.intellij.xdebugger.consoleViewDataConverter")
  }

  suspend fun convert(consoleView: ConsoleView, lifetimeScope: CoroutineScope, debugProcess: XDebugProcess?): RemoteXDebuggerConsoleViewData?

  suspend fun convert(remoteData: RemoteXDebuggerConsoleViewData, processHandler: ProcessHandler): ConsoleView?
}