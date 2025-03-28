// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.ui.ColoredText
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.rpc.XStackFrameApi
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.rpc.XStackFramePresentationEvent
import com.intellij.xdebugger.impl.rpc.models.findValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
internal class BackendXStackFrameApi : XStackFrameApi {
  override suspend fun customizePresentation(stackFrameId: XStackFrameId): Flow<XStackFramePresentationEvent> {
    val stackFrameModel = stackFrameId.findValue() ?: return emptyFlow()

    return channelFlow {
      val container = object : ColoredTextContainer {
        // TODO: should we implement append with tag as well?
        override fun append(fragment: String, attributes: SimpleTextAttributes) {
          trySend(XStackFramePresentationEvent.AppendTextWithAttributes(
            fragment = fragment,
            attributes = attributes
          ))
        }

        override fun append(coloredText: ColoredText) {
          val fragments = mutableListOf<XStackFramePresentationEvent.TextFragment>()
          for (fragment in coloredText.fragments()) {
            fragments.add(XStackFramePresentationEvent.TextFragment(
              text = fragment.fragmentText(),
              attributes = fragment.fragmentAttributes()
            ))
          }
          trySend(XStackFramePresentationEvent.AppendColoredText(fragments))
        }

        override fun setIcon(icon: Icon?) {
          trySend(XStackFramePresentationEvent.SetIcon(icon?.rpcId()))
        }

        override fun setToolTipText(text: String?) {
          trySend(XStackFramePresentationEvent.SetTooltip(text))
        }
      }

      stackFrameModel.stackFrame.customizePresentation(container)

      awaitClose()
    }.buffer(Channel.UNLIMITED)
  }
}