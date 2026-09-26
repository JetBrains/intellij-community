// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.cancellation

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.currentExtensionSupport
import org.jetbrains.annotations.ApiStatus

/**
 * @return the first registered [CancellationProvider] or `null` if there is no registered [CancellationProvider]
 */
fun cancellationProvider(): CancellationProvider? =
  currentExtensionSupport().getExtensions(cancellationProviderEP).firstNotNullOfOrNull { it.getCancellationProvider() }

/**
 * Implement this extension point to provide a [CancellationProvider] for your environment.
 */
@ApiStatus.OverrideOnly
interface CancellationProviderExtension {
  fun getCancellationProvider(): CancellationProvider?
}


private val cancellationProviderEP = ExtensionPointKey<CancellationProviderExtension>("com.intellij.syntax.cancellationProviderExtension")