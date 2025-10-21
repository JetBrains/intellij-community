// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.data

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.searchEverywhere.SeSession
import kotlinx.serialization.KSerializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeSessionDataContextSerializer : CustomDataContextSerializer<SeSession> {
  override val key: DataKey<SeSession>
    get() = SeDataKeys.SPLIT_SE_SESSION
  override val serializer: KSerializer<SeSession>
    get() = SeSession.serializer()
}