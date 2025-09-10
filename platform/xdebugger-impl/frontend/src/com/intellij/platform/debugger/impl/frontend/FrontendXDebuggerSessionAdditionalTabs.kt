// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.rpc.getComponent
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabDto
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabEvent
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabId
import com.intellij.ui.content.Content
import com.intellij.xdebugger.impl.rpc.XDebugSessionAdditionalTabComponentManagerId
import com.intellij.xdebugger.impl.rpc.XDebugSessionTabApi
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun subscribeOnAdditionalTabs(cs: CoroutineScope, tab: XDebugSessionTab, additionalTabsComponentManagerId: XDebugSessionAdditionalTabComponentManagerId) {
  val contentsMap = mutableMapOf<XDebuggerSessionAdditionalTabId, Content>()
  cs.launch(Dispatchers.EDT) {
    XDebugSessionTabApi.getInstance().additionalTabEvents(additionalTabsComponentManagerId).collect { event ->
      when (event) {
        is XDebuggerSessionAdditionalTabEvent.TabAdded -> {
          val content = event.tabDto.createContent(tab)
          if (content != null) {
            contentsMap[event.tabDto.id] = content
            content.setCloseable(event.tabDto.closeable)
            tab.ui.addContent(content)
          }
        }
        is XDebuggerSessionAdditionalTabEvent.TabRemoved -> {
          val contentToRemove = contentsMap.remove(event.tabId) ?: return@collect
          tab.ui.removeContent(contentToRemove, true)
        }
      }
    }
  }
}

private fun XDebuggerSessionAdditionalTabDto.createContent(tab: XDebugSessionTab): Content? {
  val component = id.getComponent() ?: return null
  return tab.ui.createContent(contentId, component, title, icon?.icon(), component.getPreferredFocusedComponent())
}