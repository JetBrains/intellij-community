// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic


interface JetBrainsConsentProvider {
  fun isAllowedByUserConsentWithJetBrains(): Boolean
}

internal class EmptyJetBrainsConsentProvider: JetBrainsConsentProvider {
  override fun isAllowedByUserConsentWithJetBrains(): Boolean {
    // If service is not overridden in the plugin, the default implementation is used.
    return false
  }
}