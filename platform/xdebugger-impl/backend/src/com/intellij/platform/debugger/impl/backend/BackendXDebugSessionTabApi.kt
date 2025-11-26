// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.ide.rpc.setupTransfer
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.ComponentWithActions
import com.intellij.platform.debugger.impl.rpc.XDebugSessionDataId
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterEvent
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabEvent
import com.intellij.ui.content.Content
import com.intellij.util.asDisposable
import com.intellij.xdebugger.impl.findValue
import com.intellij.xdebugger.impl.rpc.XDebugSessionAdditionalTabComponentManagerId
import com.intellij.xdebugger.impl.rpc.XDebugSessionTabApi
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionTabDto
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionTabInfoCallback
import com.intellij.xdebugger.impl.rpc.models.findValue
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

internal class BackendXDebugSessionTabApi : XDebugSessionTabApi {
  override suspend fun sessionTabInfo(sessionDataId: XDebugSessionDataId): Flow<XDebuggerSessionTabDto> {
    val session = sessionDataId.findValue()?.session ?: return emptyFlow()
    return session.tabInitDataFlow.map {
      XDebuggerSessionTabDto(it, session.getPausedEventsFlow().toRpc())
    }
  }

  override suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback) {
    val tab = tabInfo.tab ?: return
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.tabInitialized(tab)
    }
  }

  override suspend fun additionalTabEvents(tabComponentsManagerId: XDebugSessionAdditionalTabComponentManagerId): Flow<XDebuggerSessionAdditionalTabEvent> {
    val manager = tabComponentsManagerId.findValue() ?: return emptyFlow()
    return manager.tabComponentEvents
  }

  override suspend fun tabLayouterEvents(sessionId: XDebugSessionId): Flow<XDebugTabLayouterEvent> {
    val session = sessionId.findValue() ?: return emptyFlow()
    val layouter = session.debugProcess.createTabLayouter()
    // TODO Support XDebugTabLayouter.registerConsoleContent

    return channelFlow {
      val mockUI = session.getMockRunContentDescriptorIfInitialized()?.runnerLayoutUi ?: return@channelFlow
      val uiBridge = RunnerLayoutUiBridge(mockUI, this.asDisposable())
      withContext(Dispatchers.EDT) {
        layouter.registerAdditionalContent(uiBridge)
      }
      uiBridge.events.consumeEach { send(it) }
    }
  }
}

private class RunnerLayoutUiBridge(private val delegate: RunnerLayoutUi, private val disposable: Disposable) : RunnerLayoutUi by delegate {
  private val eventsChannel = Channel<XDebugTabLayouterEvent>(Channel.UNLIMITED)
  private val contents = hashMapOf<Content, Int>()

  val events: ReceiveChannel<XDebugTabLayouterEvent> get() = eventsChannel

  override fun createContent(contentId: @NonNls String, component: JComponent, displayName: @Nls String, icon: Icon?, toFocus: JComponent?): Content {
    // Do not pass component to delegate, as it will break LUX transfer due to adding content into a UI hierarchy
    val content = delegate.createContent(contentId, JLabel(), displayName, icon, toFocus)
    sendContentCreationEvent(component, content, contentId, displayName, icon)
    return content
  }

  override fun createContent(contentId: @NonNls String, contentWithActions: ComponentWithActions, displayName: @Nls String, icon: Icon?, toFocus: JComponent?): Content {
    return createContent(contentId, contentWithActions.component, displayName, icon, toFocus)
  }

  private fun sendContentCreationEvent(component: JComponent, fakeContent: Content, contentId: @NonNls String, displayName: @Nls String, icon: Icon?) {
    val tabId = component.setupTransfer(disposable)
    val uniqueId = contents.size
    contents[fakeContent] = uniqueId
    eventsChannel.trySend(XDebugTabLayouterEvent.ContentCreated(uniqueId, contentId, tabId, displayName, icon?.rpcIdOrNull()))
  }

  override fun addContent(content: Content): Content {
    val uniqueId = contents[content]
    if (uniqueId != null) {
      eventsChannel.trySend(XDebugTabLayouterEvent.TabAdded(uniqueId, content.isCloseable))
    }
    return delegate.addContent(content)
  }

  override fun addContent(content: Content, defaultTabId: Int, defaultPlace: PlaceInGrid, defaultIsMinimized: Boolean): Content {
    val uniqueId = contents[content]
    if (uniqueId != null) {
      eventsChannel.trySend(
        XDebugTabLayouterEvent.TabAddedExtended(uniqueId, defaultTabId, defaultPlace,
                                                defaultIsMinimized, content.isCloseable)
      )
    }
    return delegate.addContent(content, defaultTabId, defaultPlace, defaultIsMinimized)
  }

  override fun removeContent(content: Content?, dispose: Boolean): Boolean {
    val uniqueId = contents.remove(content)
    if (uniqueId != null) {
      eventsChannel.trySend(XDebugTabLayouterEvent.TabRemoved(uniqueId))
    }
    return delegate.removeContent(content, dispose)
  }
}
