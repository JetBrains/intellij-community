// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl

import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

private val LOG = logger<KeymapFlagsStorage>()

private class KeymapFlagsStorageListener : KeymapManagerListener {
  override fun shortcutsChanged(keymap: Keymap, actionIds: @NonNls MutableCollection<String>, fromSettings: Boolean) {
    if (!fromSettings) {
      return
    }

    val storage = service<KeymapFlagsStorage>()
    for (actionId in actionIds) {
      storage.removeOutdatedFlags(keymap, actionId, fromSettings)
    }
  }

  override fun keymapRemoved(keymap: Keymap) {
    service<KeymapFlagsStorage>().removeFlagsForKeymap(keymap)
  }
}

@Internal
@State(name = "KeymapFlagsStorage",
       category = SettingsCategory.KEYMAP,
       exportable = true,
       storages = [Storage("keymapFlags.xml", roamingType = RoamingType.DISABLED)])
internal class KeymapFlagsStorage : SimplePersistentStateComponent<KeymapFlagsStorage.State>(State()) {
  companion object {
    const val FLAG_MIGRATED_SHORTCUT: String = "MIGRATED_SHORTCUT"
  }

  data class FlagDescriptor(
    var action: String?,
    var shortcut: String?,
    var flag: String?,
    var lifetime: KeymapFlagLifetimeKind?
  )

  class State internal constructor(): BaseState() {
    // keymapName to descriptor
    var keymapToDescriptor: MutableMap<String, MutableList<FlagDescriptor>> by map()
  }

  fun addFlag(keymap: Keymap, actionId: String, shortcut: Shortcut, flag: String, lifetime: KeymapFlagLifetimeKind) {
    forKeymap(keymap).add(FlagDescriptor(actionId, shortcut.toString(), flag, lifetime))

    LOG.info("Added flag $flag for keymap=${keymap.name};actionId=${actionId};shortcut=${shortcut};lifetime=${lifetime}")

    state.intIncrementModificationCount()
  }

  fun getFlags(keymap: Keymap): Set<String?> = forKeymap(keymap).map { it.flag }.toSet()

  fun getFlags(keymap: Keymap, actionId: String): List<String?> = forKeymap(keymap).forAction(actionId).map { it.flag }

  fun getFlags(keymap: Keymap, actionId: String, shortcut: Shortcut): List<String?> {
    return forKeymap(keymap).forAction(actionId).forShortcut(shortcut).map { it.flag }
  }

  fun hasFlag(keymap: Keymap, actionId: String, flag: String): Boolean = getFlags(keymap, actionId).contains(flag)

  fun hasFlag(keymap: Keymap, actionId: String, shortcut: Shortcut, flag: String): Boolean {
    return getFlags(keymap = keymap, actionId = actionId, shortcut = shortcut).contains(flag)
  }

  internal fun removeOutdatedFlags(keymap: Keymap, actionId: String, fromSettings: Boolean) {
    assert(fromSettings)

    val currentShortcuts = keymap.getShortcuts(actionId).map { it.toString() }

    val result = forKeymap(keymap)
      .removeIf {
        it.action == actionId &&
        (it.lifetime == KeymapFlagLifetimeKind.UNTIL_SHORTCUT_DELETED && !currentShortcuts.contains(it.shortcut)) ||
        (it.lifetime == KeymapFlagLifetimeKind.UNTIL_ACTION_SHORTCUT_UPDATED)
      }

    if (result) {
      LOG.info("Several flags were deleted as a part of cleanup keymap=${keymap.name};actionId=${actionId}")
      state.intIncrementModificationCount()
    }
  }

  internal fun removeFlagsForKeymap(keymap: Keymap) {
    if (state.keymapToDescriptor.remove(keymap.name) != null) {
      LOG.info("Flags were deleted for keymap=${keymap.name}")
      state.intIncrementModificationCount()
    }
  }

  private fun forKeymap(keymap: Keymap): MutableList<FlagDescriptor> {
    return state.keymapToDescriptor.getOrPut(keymap.name) { mutableListOf() }
  }

  private fun MutableList<FlagDescriptor>.forAction(actionId: String) =
    filter { it.action == actionId }

  private fun List<FlagDescriptor>.forShortcut(sc: Shortcut) =
    filter { it.shortcut == sc.toString() }
}

@Internal
enum class KeymapFlagLifetimeKind {
  UNTIL_SHORTCUT_DELETED,
  UNTIL_ACTION_SHORTCUT_UPDATED,
  FOREVER_AND_EVERMORE
}