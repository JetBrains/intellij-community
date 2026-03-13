// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.debugger

import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.BreakpointFileProhibitionPolicy

internal class VcsBreakpointFileProhibitionPolicy : BreakpointFileProhibitionPolicy {
  // Do not allow breakpoints in VCS revisions opened in editor
  override fun isBreakpointProhibited(virtualFile: VirtualFile): Boolean = virtualFile is AbstractVcsVirtualFile
}
