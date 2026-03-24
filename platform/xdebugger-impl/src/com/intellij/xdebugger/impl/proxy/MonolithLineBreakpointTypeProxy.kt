// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.util.ThreeState
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import javax.swing.Icon

@Suppress("DEPRECATION")
internal class MonolithLineBreakpointTypeProxy @Deprecated("Use type.asProxy() instead") internal constructor(
  project: Project,
  lineBreakpointType: XLineBreakpointType<*>,
) : MonolithBreakpointTypeProxy(project, lineBreakpointType), XLineBreakpointTypeProxy {
  override val breakpointType: XLineBreakpointType<*> = lineBreakpointType

  override val temporaryIcon: Icon?
    get() = breakpointType.temporaryIcon

  override val priority: Int get() = breakpointType.priority
  override fun supportsInterLinePlacement(): Boolean = breakpointType.supportsInterLinePlacement()

  override suspend fun canPutAt(editor: Editor, line: Int, project: Project): Boolean {
    return readAction {
      canPutAtFast(editor, line, project).toBoolean()
    }
  }

  override fun canPutAtFast(editor: Editor, line: Int, project: Project): ThreeState {
    val file = FileDocumentManager.getInstance().getFile(editor.getDocument()) ?: return ThreeState.NO
    return ThreeState.fromBoolean(breakpointType.canPutAt(file, line, project))
  }

  override suspend fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
    return readAction {
      canPutAtFast(file, line, project).toBoolean()
    }
  }

  override fun canPutAtFast(file: VirtualFile, line: Int, project: Project): ThreeState {
    return ThreeState.fromBoolean(breakpointType.canPutAt(file, line, project))
  }
}
