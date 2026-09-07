// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target.presentation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.backend.presentation.TargetPresentation
import org.jetbrains.annotations.ApiStatus

/**
 * Computes the [TargetPresentation] of a raw Search Everywhere item.
 *
 * This extension point replaces `PSIPresentationBgRendererWrapper.element2presentation`. The old code
 * read the presentation through `SearchEverywherePsiRenderer`, so it had to build a Swing component
 * first. An implementation here must touch no Swing component, because the whole computation runs on
 * a background thread.
 *
 * The extension point is ordered. The platform asks each extension in the declaration order and takes
 * the first one that both [appliesTo] the item and returns a presentation. Use the `order` attribute
 * to place an extension against another one. [SeDefaultTargetPresentationProvider] carries
 * `order="last"` and applies to every item, so it is the fallback.
 */
@ApiStatus.Internal
interface SeTargetPresentationProvider {
  /**
   * Returns true when this extension can compute the presentation of [item].
   *
   * Keep the check cheap. The platform calls it for every item until an extension claims it.
   */
  fun appliesTo(item: Any): Boolean

  /**
   * Returns the presentation of [item], or null when this extension computed none.
   *
   * The caller holds no read lock. Take a read action when the implementation reads the PSI.
   * [com.intellij.openapi.application.readAction] also moves the work to a background thread.
   */
  suspend fun getPresentation(item: Any): TargetPresentation?

  companion object {
    val EP_NAME: ExtensionPointName<SeTargetPresentationProvider> =
      ExtensionPointName("com.intellij.searchEverywhere.targetPresentationProvider")

    /**
     * Returns the presentation from the first extension that applies to [item] and computes one.
     *
     * The result is null only when no extension computed a presentation. That cannot happen while
     * [SeDefaultTargetPresentationProvider] is registered, because it applies to every item.
     */
    suspend fun computePresentation(item: Any): TargetPresentation? =
      EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
        if (provider.appliesTo(item)) provider.getPresentation(item) else null
      }
  }
}
