// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.util.log

import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.currentExtensionSupport
import com.intellij.platform.syntax.logger.noopLogger
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a [Logger] for your environment or [noopLogger] if no Logger was provided.
 */
@ApiStatus.Experimental
fun logger(name: String): Logger =
  currentExtensionSupport().getExtensions(logProviderExtensionPoint).firstNotNullOfOrNull { it.getLogger(name) } ?: noopLogger()

/**
 * Implement this extension point to provide a [Logger] for your environment with the given name..
 */
@ApiStatus.OverrideOnly
@ApiStatus.Experimental
interface LogProvider {
  fun getLogger(name: String): Logger?
}

private val logProviderExtensionPoint: ExtensionPointKey<LogProvider> = ExtensionPointKey("com.intellij.syntax.logProvider")