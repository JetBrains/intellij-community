// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.platform.searchEverywhere.SeItemData

sealed interface SeResultListRow

class SeResultListItemRow(val item: SeItemData) : SeResultListRow
class SeResultListMoreRow : SeResultListRow
