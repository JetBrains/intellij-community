// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/change-listeners.spec.md

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Receives a settings change. Register it through [SettingsKey.addListener]. It gets only the identity of the change,
 * the key and a context, and no value. Read `key[context]` in the callback for the current value, which is fresher
 * than any snapshot the vault could hand over. Because of the uniform `(key, context)` shape, one listener instance
 * can serve several registrations and dispatch on what fired.
 *
 * The meaning of the context depends on the registration:
 * - **contextual**, registered with a context: it is that same context. The callback fires only when the stored
 *   value that the key resolves there changed. It compares the stored values, not the values that a read returns.
 * - **key-only**, registered without a context: it is the context where the change was *detected*. The value of the
 *   key may have changed at or under that context. It is a hint to check again, not a guarantee of a difference.
 *
 * The callback fires only when the context that it gets is ready (ADR 0161). A change at a dirty context, such as a
 * directory context while its file layer loads, waits until the context gets ready. Then a contextual callback fires
 * once, and only when the stored value changed in total. A key-only callback fires once per changed context. A context
 * under the context of a key-only callback can still be dirty. Check [SettingsContext.isReady] before you read there.
 */
@Experimental
fun interface SettingsChangeListener {
  /**
   * Handles a change of [key].
   *
   * @param key the key that may have changed
   * @param context the context of the change, as the registration defines it
   */
  fun onChanged(key: SettingsKey<*>, context: SettingsContext)
}
