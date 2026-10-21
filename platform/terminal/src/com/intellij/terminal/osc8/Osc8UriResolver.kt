// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.osc8

import com.intellij.execution.filters.HyperlinkInfo
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves the target URI of an OSC 8 terminal hyperlink to a [HyperlinkInfo].
 *
 * @see Osc8UriResolverProvider
 */
@ApiStatus.Internal
interface Osc8UriResolver {
  /**
   * Returns the hyperlink for [uri], or `null` if [uri] is unrecognized.
   *
   * This method is called on a background thread without a read action,
   * but possibly under an internal lock of the terminal.
   * Therefore, implementations must not take the Read or Write lock to avoid deadlock.
   */
  fun resolve(uri: String): HyperlinkInfo?
}
