// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file: ApiStatus.Experimental
package com.intellij.platform.syntax.i18n

import fleet.util.multiplatform.linkToActual
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
interface ResourceBundle {
  fun message(key: String, vararg params: Any): @Nls String
  fun messagePointer(key: String, vararg params: Any): () -> @Nls String
}

/**
 * see [com.intellij.platform.syntax.i18n.JvmResourceBundle]
 */
@Suppress("unused")
fun ResourceBundle(
  bundleClass: String,
  pathToBundle: String,
  self: Any,
): ResourceBundle = linkToActual()