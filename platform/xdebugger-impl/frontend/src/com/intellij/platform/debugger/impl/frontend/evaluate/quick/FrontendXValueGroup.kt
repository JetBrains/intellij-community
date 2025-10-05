// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.rpc.XValueApi
import com.intellij.platform.debugger.impl.rpc.XValueGroupDto
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueGroup
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

internal class FrontendXValueGroup(
  val project: Project,
  cs: CoroutineScope,
  val xValueGroupDto: XValueGroupDto,
  hasParentValue: Boolean,
) : XValueGroup(xValueGroupDto.groupName) {

  private val xValueContainer = FrontendXValueContainer(project, cs, hasParentValue) {
    XValueApi.getInstance().computeXValueGroupChildren(xValueGroupDto.id)
  }

  override fun computeChildren(node: XCompositeNode) {
    xValueContainer.computeChildren(node)
  }

  override fun getIcon(): Icon? {
    return xValueGroupDto.icon?.icon()
  }

  override fun isAutoExpand(): Boolean {
    return xValueGroupDto.isAutoExpand
  }

  override fun isRestoreExpansion(): Boolean {
    return xValueGroupDto.isRestoreExpansion
  }

  override fun getSeparator(): @NlsSafe String {
    return xValueGroupDto.separator
  }

  override fun getComment(): @NlsSafe String? {
    return xValueGroupDto.comment
  }
}
