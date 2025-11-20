// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.ide.rpc.getComponent
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterDto
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterEvent
import com.intellij.ui.content.Content
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private class FrontendXDebugTabLayouter(
  private val cs: CoroutineScope,
  private val dto: XDebugTabLayouterDto,
) : XDebugTabLayouter() {

  override fun registerAdditionalContent(ui: RunnerLayoutUi) {
    cs.launch(Dispatchers.EDT) {
      val contents = hashMapOf<Int, Content>()
      dto.events.toFlow().collect { e ->
        when (e) {
          is XDebugTabLayouterEvent.ContentCreated -> {
            val content = e.createContent(ui) ?: return@collect
            contents[e.contentUniqueId] = content
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
            ui.removeContent(content, true)
          }
        }
      }
    }
  }
}

internal fun XDebugTabLayouterDto.createLayouter(cs: CoroutineScope): XDebugTabLayouter {
  return localLayouter ?: FrontendXDebugTabLayouter(cs, this)
}

private fun XDebugTabLayouterEvent.ContentCreated.createContent(ui: RunnerLayoutUi): Content? {
  val component = tabId.getComponent() ?: return null
  return ui.createContent(contentId, component, displayName, icon?.icon(), component.getPreferredFocusedComponent())
}
