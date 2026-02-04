// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XExecutionStackGroupDto
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XExecutionStackGroup
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

internal class FrontendXExecutionStackGroup(
  val groupDto: XExecutionStackGroupDto,
  val project: Project,
  private val cs: CoroutineScope,
) : XExecutionStackGroup(groupDto.displayName) {
  override val icon: Icon?
    get() = groupDto.icon?.icon()

  override val groups: List<XExecutionStackGroup>
    get() = groupDto.groups.map { FrontendXExecutionStackGroup(it, project, cs) }

  override val stacks: List<XExecutionStack>
    get() = groupDto.stacks.map { FrontendXExecutionStack(it, project, cs) }
}