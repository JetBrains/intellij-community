// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.ide.rpc.getComponent
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.platform.debugger.impl.rpc.XDebugSessionTabApi
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterDto
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterEvent
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterId
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.asDisposable
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private class FrontendXDebugTabLayouter(
  private val cs: CoroutineScope,
  private val id: XDebugTabLayouterId,
) : XDebugTabLayouter() {

  override fun registerAdditionalContent(ui: RunnerLayoutUi) {
    cs.launch(Dispatchers.EDT) {
      val contents = hashMapOf<Int, Content>()
      val contentToUniqueId = hashMapOf<Content, Int>()
      val selectionChannel = Channel<Pair<Int, Boolean>>(Channel.UNLIMITED)
      launch {
        for ((uniqueId, isSelected) in selectionChannel) {
          XDebugSessionTabApi.getInstance().updateTabSelection(id, uniqueId, isSelected)
        }
      }

      ui.addListener(object : ContentManagerListener {
        override fun selectionChanged(event: ContentManagerEvent) {
          val content = event.content
          val uniqueId = contentToUniqueId[content] ?: return
          selectionChannel.trySend(uniqueId to content.isSelected)
        }
      }, cs.asDisposable())

      XDebugSessionTabApi.getInstance().tabLayouterEvents(id).collect { e ->
        when (e) {
          is XDebugTabLayouterEvent.ContentCreated -> {
            val content = e.createContent(ui) ?: return@collect
            contents[e.contentUniqueId] = content
            contentToUniqueId[content] = e.contentUniqueId
          }
          is XDebugTabLayouterEvent.TabAdded -> {
            val content = contents[e.contentUniqueId] ?: return@collect
            content.isCloseable = e.closeable
            ui.addContent(content)
          }
          is XDebugTabLayouterEvent.TabAddedExtended -> {
            val content = contents[e.contentUniqueId] ?: return@collect
            content.isCloseable = e.closeable
            ui.addContent(content, e.defaultTabId, e.defaultPlace, e.defaultIsMinimized)
          }
          is XDebugTabLayouterEvent.TabRemoved -> {
            val content = contents[e.contentUniqueId] ?: return@collect
            contentToUniqueId.remove(content)
            ui.removeContent(content, true)
          }
        }
      }
    }
  }
}

internal fun createLayouter(dto: XDebugTabLayouterDto, cs: CoroutineScope): XDebugTabLayouter {
  return dto.localLayouter ?: FrontendXDebugTabLayouter(cs, dto.id)
}

private fun XDebugTabLayouterEvent.ContentCreated.createContent(ui: RunnerLayoutUi): Content? {
  val component = tabId.getComponent() ?: return null
  return ui.createContent(contentId, component, displayName, icon?.icon(), component.getPreferredFocusedComponent())
}
