// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy
import com.intellij.ui.components.Badge
import com.intellij.util.IconUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.swing.Icon

internal class AddXBreakpointAction(
  private val project: Project,
  private val myType: XBreakpointTypeProxy,
  private val saveCurrentItem: () -> Unit,
  private val selectBreakpoint: (breakpointId: XBreakpointId) -> Unit,
) : AnAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.icon = myType.enabledIcon
    e.presentation.text = myType.title
    // SECONDARY_ICON is drawn right-aligned by the popup renderer; show it while the per-user "new" window is open.
    val showNewBadge = myType.isNewBadgeVisible() && isWithinNewBadgeWindow(myType.id)
    e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, if (showNewBadge) NEW_BADGE_ICON else null)
  }

  /** True until [NEW_BADGE_DURATION_MS] has passed since this type's badge was first shown to this user (persisted). */
  private fun isWithinNewBadgeWindow(typeId: String): Boolean {
    val props = PropertiesComponent.getInstance()
    val key = NEW_BADGE_FIRST_SEEN_KEY_PREFIX + typeId
    val firstSeen = props.getLong(key, 0L)
    val now = System.currentTimeMillis()
    if (firstSeen <= 0L) {
      props.setValue(key, now.toString())
      return true
    }
    return now - firstSeen < NEW_BADGE_DURATION_MS
  }

  override fun actionPerformed(e: AnActionEvent) {
    saveCurrentItem()
    e.coroutineScope.launch {
      val breakpoint = myType.addBreakpoint(project)
      if (breakpoint != null) {
        withContext(Dispatchers.EDT) {
          selectBreakpoint(breakpoint.id)
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  companion object {
    private const val NEW_BADGE_FIRST_SEEN_KEY_PREFIX = "debugger.addBreakpoint.newBadge.firstSeen."
    private val NEW_BADGE_DURATION_MS = TimeUnit.DAYS.toMillis(30)
    private val NEW_BADGE_ICON: Icon = IconUtil.scale(Badge.new, null, 0.75f)
  }
}
