// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.options.CompositeConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.ConfigurableWrapper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
@ApiStatus.Internal
class SettingsNewBadgeState {
  // Only contains configurables for which `isNewOptions` is true
  // The keys match the identities persisted onto the disk
  private val shownAtOpenCache = mutableMapOf<String, Int>()

  fun hasNewOptions(configurable: UnnamedConfigurable): Boolean {
    // Unnamed configurables can't be marked as opened, since they have no stable identity;
    // Therefore, it's impossible to persist the info about them being opened on the disk
    val showNewBadge = run {
      val configurable = configurable as? Configurable ?: return@run false

      isNewOptions(configurable) && shouldShowNewBadge(configurable)
    }

    // On the other hand, it's entirely possible to have the following:
    // `CompositeConfigurable<*> -> UnnamedConfigurable -> Configurable : NewOptions`
    // And since only the final configurable is marked as opened and that is propagated above,
    // we need to check ALL the children of a configurable, including the unnamed ones
    return showNewBadge || when (configurable) {
      is Configurable.Composite -> configurable.configurables.any(::hasNewOptions)
      is CompositeConfigurable<*> -> configurable.configurables.any(::hasNewOptions)
      else -> false
    }
  }

  fun markOpened(configurable: Configurable): Boolean {
    if (!isNewOptions(configurable)) return false

    recordOpened(configurable)
    shownAtOpenCache[ConfigurableVisitor.getId(configurable)] = MAX_SHOWS
    return true
  }

  private fun shouldShowNewBadge(configurable: Configurable): Boolean {
    val id = ConfigurableVisitor.getId(configurable)
    val shownAtOpen = shownAtOpenCache.getOrElse(id) { shownCount(configurable) }
    return shownAtOpen < MAX_SHOWS
  }

  private fun isNewOptions(configurable: Configurable): Boolean {
    return configurable is Configurable.NewOptions ||
           ConfigurableWrapper.cast(Configurable.NewOptions::class.java, configurable) != null
  }
}
