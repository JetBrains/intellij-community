// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/settings-writes.spec.md

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Collects a composable, **atomic** batch of settings writes. Get it from [SettingsContext.mutate], collect the
 * entries fluently, then call [apply] once:
 *
 * ```
 * projectCtx.mutate()
 *   .set(currentProfile, "release", context = appCtx)                  // an app-level discriminator
 *   .set(inspectionEnabled, false, projectCtx.access { this[currentProfile] = "release" })  // the pinned slot
 *   .copySlot(profileData, from = "default", to = "release")
 *   .apply()
 * ```
 *
 * **Fluent, not a lambda.** An entry is added only through these methods. So the operator `key[ctx] = v` cannot
 * appear "inside" a batch and silently run as a separate mutation. The cost is the explicit [apply] terminal.
 * Without it, nothing is written.
 *
 * **Semantics.**
 * - **Per-entry context.** Each entry defaults to the **current context** of the builder. At first it is the context
 *   that [SettingsContext.mutate] was called on, and [context] switches it for the entries that follow. An entry may
 *   also pass its own `context`, also an [access]-pinned one, for that entry only. So one batch can span the
 *   application, a project and a file.
 * - **In order.** The entries apply in the order added, and each one sees the effect of the earlier ones. A slotted
 *   entry uses the slots that its own context pins (see [accessFor]). An earlier entry that moves a discriminator
 *   does not change where it lands.
 * - **Atomic.** [apply] validates and resolves every entry first, so a rejected entry fails the whole batch before
 *   anything is written. Then it applies them all under one lock, and no other writer interleaves. A read takes no
 *   lock, and it sees the model before the batch or after it, never a part of it (ADR 0136).
 *
 * Build and apply an instance once. It is not meant for reuse.
 */
@Experimental
interface SettingsMutationBuilder {
  /** Switches the current context for the entries that follow, until the next switch. The entries already added keep their context. */
  fun context(context: SettingsContext): SettingsMutationBuilder

  /**
   * Adds a write of [value] for [key] at [context]. A slotted [key] needs its slots pinned on that context. See
   * [SettingsKey.set].
   *
   * @param context the context of the entry, or `null` for the current context
   */
  fun <T> set(key: SettingsKey<T>, value: T, context: SettingsContext? = null): SettingsMutationBuilder

  /**
   * Adds a copy of the [from] slot of [mapKey] to [to] at [context]. See [SettingsMapKey.copySlot].
   *
   * @param context the context of the entry, or `null` for the current context
   */
  fun copySlot(mapKey: SettingsMapKey, from: String, to: String, context: SettingsContext? = null): SettingsMutationBuilder

  /**
   * Adds a drop of the [value] slot of [mapKey] at [context]. See [SettingsMapKey.dropSlot].
   *
   * @param context the context of the entry, or `null` for the current context
   */
  fun dropSlot(mapKey: SettingsMapKey, value: String, context: SettingsContext? = null): SettingsMutationBuilder

  /**
   * Adds a rename of the [from] slot of [mapKey] to [to] at [context]. See [SettingsMapKey.renameSlot].
   *
   * @param context the context of the entry, or `null` for the current context
   */
  fun renameSlot(mapKey: SettingsMapKey, from: String, to: String, context: SettingsContext? = null): SettingsMutationBuilder

  /**
   * Applies the whole batch atomically, and suspends until it is applied. A rejected entry throws the exception that
   * [SettingsKey.set] names for it, and then nothing in the batch is written.
   */
  suspend fun apply()
}
