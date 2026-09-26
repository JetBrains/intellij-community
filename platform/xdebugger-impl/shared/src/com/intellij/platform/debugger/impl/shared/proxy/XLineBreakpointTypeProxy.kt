// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface XLineBreakpointTypeProxy : XBreakpointTypeProxy {
  val temporaryIcon: Icon?

  val priority: Int

  @ApiStatus.Internal
  fun supportsInterLinePlacement(): Boolean

  suspend fun canPutAt(editor: Editor, line: Int, project: Project): Boolean

  fun canPutAtFast(editor: Editor, line: Int, project: Project): ThreeState

  suspend fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean

  fun canPutAtFast(file: VirtualFile, line: Int, project: Project): ThreeState
}
