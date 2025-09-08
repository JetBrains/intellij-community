// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.icons.api.IconIdentifier

class FallbackIconIdentifier(
  val nestedIdentifier: Any
): IconIdentifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FallbackIconIdentifier

    return nestedIdentifier == other.nestedIdentifier
  }

  override fun hashCode(): Int {
    return nestedIdentifier.hashCode()
  }
}