// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.util.messages.Topic

internal data class ToolbarCompressedEvent(val toolbar: MainToolbar)

internal object ToolbarCompressedNotifier {
  val TOPIC: Topic<ToolbarCompressedListener> = Topic.create("ToolbarCompressed", ToolbarCompressedListener::class.java)
}

internal interface ToolbarCompressedListener {
  fun onToolbarCompressed(event: ToolbarCompressedEvent)
}
