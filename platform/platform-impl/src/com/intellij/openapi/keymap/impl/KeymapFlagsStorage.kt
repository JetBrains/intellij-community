package com.intellij.openapi.keymap.impl

import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus.Internal

private val logger = logger<KeymapFlagsStorage>()

@Internal
@Service(Service.Level.APP)
@State(name = "KeymapFlagsStorage", storages = [(Storage(value = "keymapFlags.xml", roamingType = RoamingType.PER_OS))])
class KeymapFlagsStorage : PersistentStateComponent<KeymapFlagsStorage.State> {
  companion object {
    fun getInstance() = service<KeymapFlagsStorage>()

    const val FLAG_MIGRATED_SHORTCUT = "MIGRATED_SHORTCUT"
  }

  data class FlagDescriptor(
    val shortcut: String,
    val flag: String,
    val lifetime: KeymapFlagLifetimeKind
  )
  class State internal constructor(): BaseState() {
    val keybindingsFlags by map<String, MutableMap<String, MutableSet<FlagDescriptor>>>() // <keymapName, map<actionName, set<FlagDescriptor>>>,
  }

  private var myState: State? = null

  init {
    application.messageBus.connect().subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun shortcutChanged(keymap: Keymap, actionId: String) = removeOutdatedFlags(keymap, actionId)
      override fun keymapRemoved(keymap: Keymap) = removeFlagsForKeymap(keymap)
    })
  }

  override fun getState() = myState

  override fun loadState(state: State) { myState = state }

  override fun noStateLoaded() { loadState(State()) }

  fun addFlag(keymap: Keymap, actionId: String, shortcut: Shortcut, flag: String, lifetime: KeymapFlagLifetimeKind) {
    val state = myState
    requireNotNull(state)

    forKeymap(keymap).forAction(actionId).add(FlagDescriptor(shortcut.toString(), flag, lifetime))

    logger.info("Added flag $flag for keymap=${keymap.name};actionId=${actionId};shortcut=${shortcut};lifetime=${lifetime}")

    state.addModificationCount(1)
  }

  fun getFlags(keymap: Keymap) =
    forKeymap(keymap).values.flatten()

  fun getFlags(keymap: Keymap, actionId: String) =
    forKeymap(keymap).forActionOrNot(actionId)?.map { it.flag } ?: emptyList()

  fun getFlags(keymap: Keymap, actionId: String, shortcut: Shortcut) =
    forKeymap(keymap).forActionOrNot(actionId)?.forShortcut(shortcut)?.map { it.flag } ?: emptyList()

  fun hasFlag(keymap: Keymap, actionId: String, flag: String) =
    getFlags(keymap, actionId).contains(flag)

  fun hasFlag(keymap: Keymap, actionId: String, shortcut: Shortcut, flag: String) =
    getFlags(keymap, actionId, shortcut).contains(flag)


  private fun removeOutdatedFlags(keymap: Keymap, actionId: String) {
    val state = myState
    requireNotNull(state)

    val currentShortcuts = keymap.getShortcuts(actionId).map { it.toString() }

    val res = forKeymap(keymap).forAction(actionId)
      .removeIf {
        (it.lifetime == KeymapFlagLifetimeKind.UNTIL_SHORTCUT_DELETED && !currentShortcuts.contains(it.shortcut)) ||
        (it.lifetime == KeymapFlagLifetimeKind.UNTIL_ACTION_SHORTCUT_UPDATED)
      }

    if (res) {
      logger.info("Several flags were deleted as a part of cleanup keymap=${keymap.name};actionId=${actionId}")
      state.addModificationCount(1)
    }
  }

  private fun removeFlagsForKeymap(keymap: Keymap) {
    val state = myState
    requireNotNull(state)

    if (state.keybindingsFlags.remove(keymap.name) != null) {
      logger.info("Flags were deleted for keymap=${keymap.name}")
      state.addModificationCount(1)
    }
  }

  private fun forKeymap(keymap: Keymap): MutableMap<String, MutableSet<FlagDescriptor>> {
    val state = myState
    requireNotNull(state)

    return state.keybindingsFlags.getOrPut(keymap.name) { mutableMapOf() }
  }

  private fun MutableMap<String, MutableSet<FlagDescriptor>>.forAction(actionId: String) =
    getOrPut(actionId) { mutableSetOf() }

  private fun MutableMap<String, MutableSet<FlagDescriptor>>.forActionOrNot(actionId: String) =
    get(actionId)

  private fun MutableSet<FlagDescriptor>.forShortcut(shortcut: Shortcut) = filter { it.shortcut == shortcut.toString() }
}

enum class KeymapFlagLifetimeKind {
  UNTIL_SHORTCUT_DELETED,
  UNTIL_ACTION_SHORTCUT_UPDATED,
  FOREVER_AND_EVERMORE
}