// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

internal class ToolWindowIdDataContextSerializer : CustomDataContextSerializer<String> {
  override val key: DataKey<String>
    get() = PlatformDataKeys.TOOL_WINDOW_ID
  override val serializer: KSerializer<String>
    get() = String.serializer()
}
