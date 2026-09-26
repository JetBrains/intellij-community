// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.data

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeIsAllTabDataContextSerializer : CustomDataContextSerializer<Boolean> {
  override val key: DataKey<Boolean>
    get() = SeDataKeys.SPLIT_SE_IS_ALL_TAB
  override val serializer: KSerializer<Boolean>
    get() = Boolean.serializer()
}