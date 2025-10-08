// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.data

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.searchEverywhere.SeItemData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeSelectedItemsDataContextSerializer : CustomDataContextSerializer<List<SeItemData>> {
  override val key: DataKey<List<SeItemData>>
    get() = SeDataKeys.SPLIT_SE_SELECTED_ITEMS

  override val serializer: KSerializer<List<SeItemData>>
    get() = ListSerializer(SeItemData.serializer())
}