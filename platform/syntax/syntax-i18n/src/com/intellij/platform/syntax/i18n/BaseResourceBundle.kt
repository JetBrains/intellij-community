// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.i18n

import org.jetbrains.annotations.Nls

internal class BaseResourceBundle(
  private val map: Map<String, String>,
) : ResourceBundle {

  @Suppress("HardCodedStringLiteral")
  override fun message(key: String, vararg params: Any): @Nls String {
    val raw = map[key] ?: return key
    return format(raw, params)
  }

  override fun messagePointer(key: String, vararg params: Any): () -> @Nls String {
    val message = message(key, *params)
    return { message }
  }
}