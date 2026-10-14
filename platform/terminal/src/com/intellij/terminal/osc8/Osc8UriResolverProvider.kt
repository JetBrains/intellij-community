// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.osc8

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the [Osc8UriResolver]s that are applied to the OSC 8 terminal hyperlinks.
 *
 * The terminal asks the resolvers in the order of the extensions and uses the first
 * hyperlink that a resolver returns (see [CompositeOsc8UriResolver], [CompositeOsc8UriResolverWrapper]).
 *
 * Register an implementation as the `com.intellij.terminal.osc8UriResolverProvider` extension.
 */
@ApiStatus.Internal
interface Osc8UriResolverProvider {
  /**
   * Returns the resolvers for [project].
   *
   * The terminal can call it more than once for the same [project].
   * The call has the thread and lock contract of [Osc8UriResolver.resolve].
   */
  fun getResolvers(project: Project): List<Osc8UriResolver>

  companion object {
    val EP_NAME: ExtensionPointName<Osc8UriResolverProvider> = ExtensionPointName("com.intellij.terminal.osc8UriResolverProvider")
  }
}
