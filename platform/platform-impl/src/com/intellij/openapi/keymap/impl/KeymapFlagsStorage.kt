package com.intellij.openapi.keymap.impl

import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import org.jetbrains.annotations.ApiStatus.Internal

private val logger = logger<KeymapFlagsStorage>()

class KeymapFlagsStorageListener : KeymapManagerListener {
  private val mgr get() = service<KeymapFlagsStorage>()

  override fun shortcutChanged(keymap: Keymap, actionId: String, fromSettings: Boolean) = mgr.removeOutdatedFlags(keymap, actionId, fromSettings)
  override fun keymapRemoved(keymap: Keymap) = mgr.removeFlagsForKeymap(keymap)
}

@Internal
@Service(Service.Level.APP)
@State(name = "KeymapFlagsStorage", storages = [Storage("keymapFlags.xml")])
class KeymapFlagsStorage : SimplePersistentStateComponent<KeymapFlagsStorage.State>(State()) {
  companion object {
    @JvmStatic
    fun getInstance() = service<KeymapFlagsStorage>()

    const val FLAG_MIGRATED_SHORTCUT = "MIGRATED_SHORTCUT"
  }

  data class FlagDescriptor(
    var action: String?,
    var shortcut: String?,
    var flag: String?,
    var lifetime: KeymapFlagLifetimeKind?
  )
  class State internal constructor(): BaseState() {
    var keymapToDescriptor by map<String, MutableList<FlagDescriptor>>() // keymapName to descriptor
  }

  fun addFlag(keymap: Keymap, actionId: String, shortcut: Shortcut, flag: String, lifetime: KeymapFlagLifetimeKind) {
    forKeymap(keymap).add(FlagDescriptor(actionId, shortcut.toString(), flag, lifetime))

    logger.info("Added flag $flag for keymap=${keymap.name};actionId=${actionId};shortcut=${shortcut};lifetime=${lifetime}")

    state.addModificationCount(1)
  }

  fun getFlags(keymap: Keymap) =
    forKeymap(keymap).map { it.flag }.toSet()

  fun getFlags(keymap: Keymap, actionId: String) =
    forKeymap(keymap).forAction(actionId).map { it.flag }

  fun getFlags(keymap: Keymap, actionId: String, shortcut: Shortcut) =
    forKeymap(keymap).forAction(actionId).forShortcut(shortcut).map { it.flag }

  fun hasFlag(keymap: Keymap, actionId: String, flag: String) =
    getFlags(keymap, actionId).contains(flag)

  fun hasFlag(keymap: Keymap, actionId: String, shortcut: Shortcut, flag: String) =
    getFlags(keymap, actionId, shortcut).contains(flag)


  internal fun removeOutdatedFlags(keymap: Keymap, actionId: String, fromSettings: Boolean) {
    if (!fromSettings) return
    val currentShortcuts = keymap.getShortcuts(actionId).map { it.toString() }

    val res = forKeymap(keymap)
      .removeIf {
        it.action == actionId &&
        (it.lifetime == KeymapFlagLifetimeKind.UNTIL_SHORTCUT_DELETED && !currentShortcuts.contains(it.shortcut)) ||
        (it.lifetime == KeymapFlagLifetimeKind.UNTIL_ACTION_SHORTCUT_UPDATED)
      }

    if (res) {
      logger.info("Several flags were deleted as a part of cleanup keymap=${keymap.name};actionId=${actionId}")
      state.addModificationCount(1)
    }
  }

  internal fun removeFlagsForKeymap(keymap: Keymap) {
    if (state.keymapToDescriptor.remove(keymap.name) != null) {
      logger.info("Flags were deleted for keymap=${keymap.name}")
      state.addModificationCount(1)
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

enum class KeymapFlagLifetimeKind {
  UNTIL_SHORTCUT_DELETED,
  UNTIL_ACTION_SHORTCUT_UPDATED,
  FOREVER_AND_EVERMORE
}