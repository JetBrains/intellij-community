// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.diagnostic.logging.AdditionalTabComponent
import com.intellij.execution.configurations.AdditionalTabComponentManagerEx
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabDto
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabEvent
import com.intellij.platform.debugger.impl.shared.XDebugSessionAdditionalTabComponentConverter
import com.intellij.platform.debugger.impl.shared.XDebuggerSessionAdditionalTabId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.ui.content.Content
import com.intellij.xdebugger.impl.rpc.XDebugSessionAdditionalTabComponentManagerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class XDebugSessionAdditionalTabComponentManager(
  private val project: Project,
  private val debugTabScope: CoroutineScope,
) : AdditionalTabComponentManagerEx {
  val id: XDebugSessionAdditionalTabComponentManagerId = storeValueGlobally(debugTabScope, this, XDebugSessionAdditionalTabComponentManagerValueIdType)
  private val tabToId = mutableMapOf<AdditionalTabComponent, XDebuggerSessionAdditionalTabId>()

  private val _tabComponentEvents = MutableSharedFlow<XDebuggerSessionAdditionalTabEvent>(replay = 1000)
  val tabComponentEvents: Flow<XDebuggerSessionAdditionalTabEvent> = _tabComponentEvents.asSharedFlow()

  override fun addAdditionalTabComponent(tabComponent: AdditionalTabComponent, id: String, icon: Icon?, closeable: Boolean): Content? {
    val tabId = XDebugSessionAdditionalTabComponentConverter.EP_NAME.extensionList.firstNotNullOfOrNull {
      it.convertToId(project, debugTabScope, tabComponent)
    } ?: return null
    tabToId[tabComponent] = tabId
    val serializableTab = XDebuggerSessionAdditionalTabDto(tabId, contentId = id, tabComponent.tabTitle, tabComponent.tooltip, icon?.rpcId(), closeable)
    _tabComponentEvents.tryEmit(XDebuggerSessionAdditionalTabEvent.TabAdded(serializableTab))
    return null
  }

  override fun addAdditionalTabComponent(component: AdditionalTabComponent, id: String) {
    addAdditionalTabComponent(component, id, null, true)
  }

  override fun removeAdditionalTabComponent(component: AdditionalTabComponent) {
    val tabId = tabToId.remove(component) ?: return
    _tabComponentEvents.tryEmit(XDebuggerSessionAdditionalTabEvent.TabRemoved(tabId))
  }
}

@ApiStatus.Internal
fun XDebugSessionAdditionalTabComponentManagerId.findValue(): XDebugSessionAdditionalTabComponentManager? {
  return findValueById(this, type = XDebugSessionAdditionalTabComponentManagerValueIdType)
}

private object XDebugSessionAdditionalTabComponentManagerValueIdType :
  BackendValueIdType<XDebugSessionAdditionalTabComponentManagerId, XDebugSessionAdditionalTabComponentManager>(
    ::XDebugSessionAdditionalTabComponentManagerId
  )