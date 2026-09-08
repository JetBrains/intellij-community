// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target.selection

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Acts on an item that the user selected in Search Everywhere.
 *
 * The extension point is ordered. The platform asks each extension in the declaration order and takes
 * the first answer that is not null. Use the `order` attribute to run before another extension.
 * [SeDefaultTargetItemSelectionProcessor] carries `order="last"` and navigates to the item.
 */
@ApiStatus.Internal
interface SeTargetItemSelectionProcessor {
  /**
   * Acts on [item] of [provider], and returns whether the search popup should close.
   *
   * Returns null when this extension does not handle the item. The platform then asks the next
   * extension.
   *
   * A true answer does not promise that the action finished. Navigation can continue on another
   * coroutine.
   *
   * The caller holds no read lock and runs off the EDT, so switch to what the action needs.
   *
   * @param modifiers the modifier keys held during the selection, as in [java.awt.event.InputEvent]
   * @param searchText the raw query, which can carry a line and a column, such as the `:42` of `Foo.kt:42`
   */
  suspend fun process(item: SeItem, provider: SeItemsProvider, modifiers: Int, searchText: String): Boolean?

  companion object {
    val EP_NAME: ExtensionPointName<SeTargetItemSelectionProcessor> =
      ExtensionPointName("com.intellij.searchEverywhere.targetItemSelectionProcessor")

    /**
     * Returns the answer of the first extension that handles [item], or null when none does.
     *
     * The result is null only when no extension handled the item. That happens for an item that
     * cannot navigate, because [SeDefaultTargetItemSelectionProcessor] handles every other one.
     */
    suspend fun process(item: SeItem, provider: SeItemsProvider, modifiers: Int, searchText: String): Boolean? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.process(item, provider, modifiers, searchText) }
  }
}
