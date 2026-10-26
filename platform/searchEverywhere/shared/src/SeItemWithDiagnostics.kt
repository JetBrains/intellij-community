// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * An item that adds diagnostic data of its own to the result list.
 * The list shows the data while the `search.everywhere.new.show.diagnostics` registry key is on.
 */
@ApiStatus.Internal
interface SeItemWithDiagnostics {
  /** The diagnostic text, or `null` when the item has none. The text is for developers, thus it needs no localization. */
  val diagnostics: @NonNls String?
}
