// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.diagnostic.logging.AdditionalTabComponent
import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.execution.configurations.AdditionalTabComponentManagerEx
import com.intellij.ide.rpc.rpcId
import com.intellij.ide.rpc.setupTransfer
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Disposer
import com.intellij.platform.debugger.impl.rpc.XDebugSessionAdditionalTabComponentManagerId
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabDto
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabEvent
import com.intellij.platform.debugger.impl.rpc.XDebuggerTabId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.ui.content.Content
import com.intellij.util.asDisposable
import com.intellij.xdebugger.SplitDebuggerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class XDebugSessionAdditionalTabComponentManager(private val debugTabScope: CoroutineScope) : AdditionalTabComponentManagerEx {
  init {
    Disposer.register(debugTabScope.asDisposable(), this)
  }

  val id: XDebugSessionAdditionalTabComponentManagerId = storeValueGlobally(debugTabScope, this, XDebugSessionAdditionalTabComponentManagerValueIdType)
  private val tabToId = mutableMapOf<AdditionalTabComponent, XDebuggerTabId>()

  private val _tabComponentEvents = MutableSharedFlow<XDebuggerSessionAdditionalTabEvent>(replay = 1000)
  val tabComponentEvents: Flow<XDebuggerSessionAdditionalTabEvent> = _tabComponentEvents.asSharedFlow()

  override fun addAdditionalTabComponent(tabComponent: AdditionalTabComponent, id: String, icon: Icon?, closeable: Boolean): Content? {
    if (tabComponent is LogConsoleBase && SplitDebuggerMode.isSplitDebugger()) {
      // This call is required to initialize the component
      // see com.intellij.diagnostic.logging.LogConsoleBase.getComponent
      tabComponent.component
      // start log file reading
      tabComponent.activate(/* force = */ true)
    }
    val tabId = tabComponent.setupTransfer(debugTabScope.asDisposable())
    tabToId[tabComponent] = tabId
    val groupId = tabComponent.toolbarActions?.rpcId(debugTabScope)
    val serializableTab = XDebuggerSessionAdditionalTabDto(tabId, contentId = id, tabComponent.tabTitle, tabComponent.tooltip, icon?.rpcId(), closeable, groupId)
    _tabComponentEvents.tryEmit(XDebuggerSessionAdditionalTabEvent.TabAdded(serializableTab))
    return null
  }

  override fun addAdditionalTabComponent(component: AdditionalTabComponent, id: String) {
    addAdditionalTabComponent(component, id, null, true)
  }

  override fun removeAdditionalTabComponent(component: AdditionalTabComponent) {
    runInEdt {
      Disposer.dispose(component)
    }
    val tabId = tabToId.remove(component) ?: return
    _tabComponentEvents.tryEmit(XDebuggerSessionAdditionalTabEvent.TabRemoved(tabId))
  }

  override fun dispose() {
    val tabs = tabToId.keys.toList()
    for (tab in tabs) {
      removeAdditionalTabComponent(tab)
    }
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