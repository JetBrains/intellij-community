// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/multidimensional-settings.spec.md

import com.intellij.util.system.LowLevelLocalMachineAccess
import org.jetbrains.annotations.ApiStatus.Experimental
import com.intellij.util.system.OS as SystemOs

/**
 * Holds the stable id of the operating system that the IDE runs on: `windows`, `macos`, `linux`, `freebsd`, `ohos`
 * or `other`. It is the discriminator of [PER_OS_SETTINGS].
 *
 * The key is a tool, not a setting: no layer stores the OS, so the key is read-only (ADR 0011). A read with no pinned
 * OS gets the current one. A read pinned to another OS, as `context.access { this[OS] = "macos" }`, reads the slot of
 * that OS. The value is an id, not a label (ADR 0056), so it stays the same across versions and locales.
 */
@Experimental
val OS: SettingsKey<String> = SettingsKey("platform.os", default = currentOsId(), readonly = true)

/**
 * Holds the settings that differ per operating system, in one slot for each [OS] id. All the features share this one
 * storage, so a config has one split by the OS and not one per feature (ADR 0177). A value that belongs to one OS,
 * such as the name of a keymap, then never reaches another OS through a shared or a mirrored config.
 *
 * ```
 * // declaration: a child key of this storage
 * val KEYMAP_NAME = SettingsKey("myFeature.keymapName", default = "default", storageKey = PER_OS_SETTINGS)
 *
 * // read: the slot of the current OS
 * val name = KEYMAP_NAME[context]
 *
 * // write: a write names its slot, so it pins the slot of the current OS first
 * KEYMAP_NAME[context.accessFor(KEYMAP_NAME)] = "Emacs"
 * ```
 *
 * In the config the storage is one entry under its [identity][SettingsKey.identity], with a map of the slots, for
 * example `"@platform.perOs": { "macos": { "myFeature.keymapName": "Emacs" }, "windows": { ... } }`.
 */
@Experimental
val PER_OS_SETTINGS: SettingsMapKey = SettingsMapKey("platform.perOs", discriminatorKey = OS)

/** Returns the [OS] id of the current operating system. */
private fun currentOsId(): String = when (SystemOs.CURRENT) {
  SystemOs.Windows -> "windows"
  SystemOs.macOS -> "macos"
  SystemOs.Linux -> "linux"
  SystemOs.FreeBSD -> "freebsd"
  SystemOs.OHOS -> "ohos"
  SystemOs.Other -> "other"
}
