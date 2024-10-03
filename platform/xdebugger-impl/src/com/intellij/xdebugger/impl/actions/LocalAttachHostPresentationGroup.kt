// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.ui.EmptyIcon
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
object LocalAttachHostPresentationGroup: XAttachPresentationGroup<XAttachHost> {
  // Should be at the bottom of the list
  override fun getOrder(): Int {
    return Int.MAX_VALUE
  }

  override fun getGroupName(): String {
    return ""
  }

  override fun getItemIcon(project: Project, info: XAttachHost, dataHolder: UserDataHolder): Icon {
    return EmptyIcon.ICON_16
  }

  override fun getItemDisplayText(project: Project, info: XAttachHost, dataHolder: UserDataHolder): String {
    return XDebuggerBundle.message("xdebugger.attach.local.host")
  }

  override fun compare(o1: XAttachHost?, o2: XAttachHost?): Int {
    return compareValuesBy(o1, o2, Any?::hashCode)
  }
}