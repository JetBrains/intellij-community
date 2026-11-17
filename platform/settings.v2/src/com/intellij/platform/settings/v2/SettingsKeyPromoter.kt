// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/key-registry.spec.md

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Registers the settings keys of one plugin, so the platform knows them before anybody reads them.
 *
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <settings.v2.keyPromoter implementation="com.example.MyKeyPromoter"/>
 * </extensions>
 * ```
 *
 * The registry knows a key only after a promoter or a write registers it. An identity that nobody promoted or wrote
 * resolves to nothing. A mutation from another side can carry such an identity, so a promoter makes the key known
 * before that mutation arrives.
 */
@Experimental
interface SettingsKeyPromoter {
  /**
   * Registers every key this plugin declares.
   *
   * @param register takes a key of this plugin, and refuses a key of another one
   */
  fun promote(register: (SettingsKey<*>) -> Unit)

  companion object {
    @Internal
    val EP_NAME: ExtensionPointName<SettingsKeyPromoter> = ExtensionPointName("com.intellij.settings.v2.keyPromoter")
  }
}
