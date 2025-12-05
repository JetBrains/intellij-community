// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.presentations

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

// Must stay internal, it's not serializable and can't be used for transfer
@ApiStatus.Internal
class SeAdaptedItemPresentation(
    override val isMultiSelectionSupported: Boolean,
    val fetchedItem: Any,
    val rendererProvider: () -> ListCellRenderer<Any>,
) : SeItemPresentation {
  override val text: String get() = ""

  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeAdaptedItemPresentation) return false

    return super.contentEquals(other) && fetchedItem == other.fetchedItem
  }
}

@ApiStatus.Internal
@Serializable
class SeAdaptedItemEmptyPresentation(override val isMultiSelectionSupported: Boolean) : SeItemPresentation {
  override val text: String get() = ""
}