// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.data

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataKey.Companion.create
import com.intellij.platform.searchEverywhere.SeItemData

object SeDataKeys {
  val SPLIT_SE_SELECTED_ITEMS: DataKey<List<SeItemData>> = create("split.se.selected.items")
}

