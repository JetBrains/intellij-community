// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.ide.rpc.setupTransfer
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentWithActions
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterEvent
import com.intellij.ui.content.Content
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

internal class RunnerLayoutUiBridge(
  private val delegate: RunnerLayoutUi,
  private val disposable: Disposable,
) : RunnerLayoutUi by delegate {
  private val eventsChannel = Channel<XDebugTabLayouterEvent>(Channel.UNLIMITED)
  private val contents = hashMapOf<Content, Int>()

  val events: Flow<XDebugTabLayouterEvent>
    get() = channelFlow {
      eventsChannel.consumeEach { send(it) }
    }

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