// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.platform.searchEverywhere.SeItemData
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
sealed interface SeListItem

@Internal
class SeListItemData(val value: SeItemData) : SeListItem

@Internal
data object SeListTerminalItem : SeListItem