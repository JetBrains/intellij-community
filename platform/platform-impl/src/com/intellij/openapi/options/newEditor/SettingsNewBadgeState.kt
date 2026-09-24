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
    // `CompositeConfigurable<*> -> UnnamedConfigurable -> a declaration with newOptions="true"`
    // And since only the final configurable is marked as opened and that is propagated above,
    // we need to check the unnamed children here, because they have no node in the settings tree.
    // A `Configurable.Composite` has a node for every child, so `SettingsTreeView` rolls the badge up over the
    // nodes instead. Never ask a composite for its children here: that builds every child of a dynamic parent.
    return showNewBadge || when (configurable) {
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

  /**
   * The declaration states the new option, so the answer costs no class load and no construction.
   * A configurable component that no extension point declares cannot hold a new option.
   */
  private fun isNewOptions(configurable: Configurable): Boolean {
    return configurable is ConfigurableWrapper && configurable.extensionPoint.newOptions
  }
}
