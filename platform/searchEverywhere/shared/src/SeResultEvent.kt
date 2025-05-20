// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
sealed interface SeResultEvent

@Internal
class SeResultAddedEvent(val itemData: SeItemData) : SeResultEvent
@Internal
class SeResultReplacedEvent(val oldItemData: SeItemData, val newItemData: SeItemData) : SeResultEvent
