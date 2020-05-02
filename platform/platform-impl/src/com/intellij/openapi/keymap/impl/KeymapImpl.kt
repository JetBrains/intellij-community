// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.configurationStore.SerializableScheme
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.ExternalizableSchemeAdapter
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.mapSmart
import com.intellij.util.containers.nullize
import gnu.trove.THashMap
import org.jdom.Element
import java.util.*
import javax.swing.KeyStroke
import kotlin.collections.HashSet

private const val KEY_MAP = "keymap"
private const val KEYBOARD_SHORTCUT = "keyboard-shortcut"
private const val KEYBOARD_GESTURE_SHORTCUT = "keyboard-gesture-shortcut"
private const val KEYBOARD_GESTURE_KEY = "keystroke"
private const val KEYBOARD_GESTURE_MODIFIER = "modifier"
private const val KEYSTROKE_ATTRIBUTE = "keystroke"
private const val FIRST_KEYSTROKE_ATTRIBUTE = "first-keystroke"
private const val SECOND_KEYSTROKE_ATTRIBUTE = "second-keystroke"
private const val ACTION = "action"
private const val VERSION_ATTRIBUTE = "version"
private const val PARENT_ATTRIBUTE = "parent"
private const val NAME_ATTRIBUTE = "name"
private const val ID_ATTRIBUTE = "id"
private const val MOUSE_SHORTCUT = "mouse-shortcut"

private val LOG = logger<KeymapImpl>()

fun KeymapImpl(name: String, dataHolder: SchemeDataHolder<KeymapImpl>): KeymapImpl {
  val result = KeymapImpl(dataHolder)
  result.name = name
  result.schemeState = SchemeState.UNCHANGED
  return result
}

private val NOTIFICATION_MANAGER by lazy {
  // we use name "Password Safe" instead of "Credentials Store" because it was named so previously (and no much sense to rename it)
  SingletonNotificationManager(NotificationGroup("Keymap", NotificationDisplayType.STICKY_BALLOON, true), NotificationType.ERROR)
}


open class KeymapImpl @JvmOverloads constructor(private var dataHolder: SchemeDataHolder<KeymapImpl>? = null) : ExternalizableSchemeAdapter(), Keymap, SerializableScheme {
  private var parent: KeymapImpl? = null
  private var unknownParentName: String? = null

  open var canModify: Boolean = true

  @JvmField
  internal var schemeState: SchemeState? = null

  override fun getSchemeState(): SchemeState? = schemeState

  private val actionIdToShortcuts = THashMap<String, MutableList<Shortcut>>()
    get() {
      val dataHolder = dataHolder
      if (dataHolder != null) {
        this.dataHolder = null
        readExternal(dataHolder.read())
      }
      return field
    }

  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<Keymap.Listener>()

  private val keymapManager by lazy { KeymapManagerEx.getInstanceEx()!! }

  /**
   * @return IDs of the action which are specified in the keymap. It doesn't return IDs of action from parent keymap.
   */
  val ownActionIds: Array<String>
    get() = actionIdToShortcuts.keys.toTypedArray()

  private var _mouseShortcutToListOfIds: Map<MouseShortcut, MutableList<String>>? = null
  private val mouseShortcutToActionIds: Map<MouseShortcut, MutableList<String>>
    get() {
      var result = _mouseShortcutToListOfIds
      if (result == null) {
        result = fillShortcutToListOfIds(MouseShortcut::class.java)
        _mouseShortcutToListOfIds = result
      }
      return result
    }

  private var _keystrokeToIds: MutableMap<KeyStroke, MutableList<String>>? = null
  private val keystrokeToIds: Map<KeyStroke, MutableList<String>>
    get() {
      _keystrokeToIds?.let {
        return it
      }

      val result = THashMap<KeyStroke, MutableList<String>>()

      fun addKeystrokesMap(actionId: String) {
        for (shortcut in getOwnOrBoundShortcuts(actionId)) {
          if (shortcut !is KeyboardShortcut) {
            continue
          }

          val idList = result.getOrPut(shortcut.firstKeyStroke) { SmartList() }
          // action may have more that 1 shortcut with same first keystroke
          if (!idList.contains(actionId)) {
            idList.add(actionId)
          }
        }
      }

      _keystrokeToIds = result
      for (id in actionIdToShortcuts.keys) {
        addKeystrokesMap(id)
      }
      for (id in keymapManager.boundActions) {
        addKeystrokesMap(id)
      }
      return result
    }

  override fun getPresentableName(): String = name

  override fun deriveKeymap(newName: String): KeymapImpl {
    if (canModify()) {
      val newKeymap = copy()
      newKeymap.name = newName
      return newKeymap
    }
    else {
      val newKeymap = KeymapImpl()
      newKeymap.parent = this
      newKeymap.name = newName
      return newKeymap
    }
  }

  @Deprecated("Please use {@link #deriveKeymap(String)} instead. " +
              "New method was introduced to ensure that you don't forget to set new keymap name.")
  fun deriveKeymap(): KeymapImpl {
    val name = try {
      name
    }
    catch (e: Exception) {
      // avoid possible NPE
      "unnamed"
    }

    return deriveKeymap("$name (copy)")
  }

  fun copy(): KeymapImpl {
    dataHolder?.let {
      return KeymapImpl(name, it)
    }

    return copyTo(KeymapImpl())
  }

  fun copyTo(otherKeymap: KeymapImpl): KeymapImpl {
    otherKeymap.cleanShortcutsCache()

    otherKeymap.actionIdToShortcuts.clear()
    otherKeymap.actionIdToShortcuts.ensureCapacity(actionIdToShortcuts.size)
    actionIdToShortcuts.forEachEntry { actionId, shortcuts ->
      otherKeymap.actionIdToShortcuts.put(actionId, SmartList(shortcuts))
      true
    }

    // after actionIdToShortcuts (on first access we lazily read itself)
    otherKeymap.parent = parent
    otherKeymap.name = name
    otherKeymap.canModify = canModify()
    return otherKeymap
  }

  override fun getParent(): KeymapImpl? = parent

  final override fun canModify(): Boolean = canModify

  override fun addShortcut(actionId: String, shortcut: Shortcut) {
    val list = actionIdToShortcuts.getOrPut(actionId) {
      val result = SmartList<Shortcut>()
      val boundShortcuts = keymapManager.getActionBinding(actionId)?.let { actionIdToShortcuts.get(it) }
      if (boundShortcuts != null) {
        result.addAll(boundShortcuts)
      }
      else {
        parent?.getMutableShortcutList(actionId)?.mapTo(result) { convertShortcut(it) }
      }
      result
    }

    if (!list.contains(shortcut)) {
      list.add(shortcut)
    }

    if (list.areShortcutsEqualToParent(actionId)) {
      actionIdToShortcuts.remove(actionId)
    }

    cleanShortcutsCache()
    fireShortcutChanged(actionId)
  }

  private fun cleanShortcutsCache() {
    _keystrokeToIds = null
    _mouseShortcutToListOfIds = null
    schemeState = SchemeState.POSSIBLY_CHANGED
  }

  override fun removeAllActionShortcuts(actionId: String) {
    for (shortcut in getShortcuts(actionId)) {
      removeShortcut(actionId, shortcut)
    }
  }

  override fun removeShortcut(actionId: String, toDelete: Shortcut) {
    val list = actionIdToShortcuts.get(actionId)
    if (list == null) {
      val inherited = keymapManager.getActionBinding(actionId)?.let { actionIdToShortcuts.get(it) }
                      ?: parent?.getMutableShortcutList(actionId)?.mapSmart { convertShortcut(it) }.nullize()
      if (inherited != null) {
        var newShortcuts: MutableList<Shortcut>? = null
        for (itemIndex in 0..inherited.lastIndex) {
          val item = inherited.get(itemIndex)
          if (toDelete == item) {
            if (newShortcuts == null) {
              newShortcuts = SmartList()
              for (notAddedItemIndex in 0..itemIndex - 1) {
                newShortcuts.add(inherited.get(notAddedItemIndex))
              }
            }
          }
          else if (newShortcuts != null) {
            newShortcuts.add(item)
          }
        }
        newShortcuts?.let {
          actionIdToShortcuts.put(actionId, it)
        }
      }
    }
    else {
      val index = list.indexOf(toDelete)
      if (index >= 0) {
        if (parent == null) {
          if (list.size == 1) {
            actionIdToShortcuts.remove(actionId)
          }
          else {
            list.removeAt(index)
          }
        }
        else {
          list.removeAt(index)
          if (list.areShortcutsEqualToParent(actionId)) {
            actionIdToShortcuts.remove(actionId)
          }
        }
      }
    }

    cleanShortcutsCache()
    fireShortcutChanged(actionId)
  }

  private fun MutableList<Shortcut>.areShortcutsEqualToParent(actionId: String) = parent.let { parent -> parent != null && areShortcutsEqual(this, parent.getMutableShortcutList(actionId).mapSmart { convertShortcut(it) }) }

  private val gestureToListOfIds: Map<KeyboardModifierGestureShortcut, List<String>> by lazy { fillShortcutToListOfIds(KeyboardModifierGestureShortcut::class.java) }

  private fun <T : Shortcut> fillShortcutToListOfIds(shortcutClass: Class<T>): Map<T, MutableList<String>> {
    val map = THashMap<T, MutableList<String>>()

    fun addActionToShortcutsMap(actionId: String) {
      for (shortcut in getOwnOrBoundShortcuts(actionId)) {
        if (!shortcutClass.isAssignableFrom(shortcut.javaClass)) {
          continue
        }

        @Suppress("UNCHECKED_CAST")
        val ids = map.getOrPut(shortcut as T) { SmartList() }
        // action may have more that 1 shortcut with same first keystroke
        if (!ids.contains(actionId)) {
          ids.add(actionId)
        }
      }
    }

    for (id in actionIdToShortcuts.keys) {
      addActionToShortcutsMap(id)
    }
    for (id in keymapManager.boundActions) {
      addActionToShortcutsMap(id)
    }
    return map
  }

  private fun getOwnOrBoundShortcuts(actionId: String): List<Shortcut> {
    actionIdToShortcuts.get(actionId)?.let {
      return it
    }

    val result = SmartList<Shortcut>()
    keymapManager.getActionBinding(actionId)?.let {
      result.addAll(getOwnOrBoundShortcuts(it))
    }
    return result
  }

  private fun getActionIds(shortcut: KeyboardModifierGestureShortcut): List<String> {
    // first, get keystrokes from own map
    val list = SmartList<String>()
    for ((key, value) in gestureToListOfIds) {
      if (shortcut.startsWith(key)) {
        list.addAll(value)
      }
    }

    if (parent != null) {
      val ids = parent!!.getActionIds(shortcut)
      if (ids.isNotEmpty()) {
        for (id in ids) {
          // add actions from parent keymap only if they are absent in this keymap
          if (!actionIdToShortcuts.containsKey(id)) {
            list.add(id)
          }
        }
      }
    }
    sortInRegistrationOrder(list)
    return list
  }

  override fun getActionIds(firstKeyStroke: KeyStroke): Array<String> {
    return ArrayUtilRt.toStringArray(getActionIds(firstKeyStroke, { it.keystrokeToIds }, KeymapImpl::convertKeyStroke))
  }

  override fun getActionIds(firstKeyStroke: KeyStroke, secondKeyStroke: KeyStroke?): Array<String> {
    val ids = getActionIds(firstKeyStroke)
    var actualBindings: MutableList<String>? = null
    for (id in ids) {
      val shortcuts = getShortcuts(id)
      for (shortcut in shortcuts) {
        if (shortcut !is KeyboardShortcut) {
          continue
        }
        if (firstKeyStroke == shortcut.firstKeyStroke && secondKeyStroke == shortcut.secondKeyStroke) {
          if (actualBindings == null) {
            actualBindings = SmartList<String>()
          }
          actualBindings.add(id)
          break
        }
      }
    }
    return ArrayUtilRt.toStringArray(actualBindings)
  }

  override fun getActionIds(shortcut: Shortcut): Array<String> {
    return when (shortcut) {
      is KeyboardShortcut -> {
        val first = shortcut.firstKeyStroke
        val second = shortcut.secondKeyStroke
        if (second == null) getActionIds(first) else getActionIds(first, second)
      }
      is MouseShortcut -> ArrayUtilRt.toStringArray(getActionIds(shortcut))
      is KeyboardModifierGestureShortcut -> ArrayUtilRt.toStringArray(getActionIds(shortcut))
      else -> ArrayUtilRt.EMPTY_STRING_ARRAY
    }
  }

  override fun hasActionId(actionId: String, shortcut: MouseShortcut): Boolean {
    var convertedShortcut = shortcut
    var keymap = this
    do {
      val list = keymap.mouseShortcutToActionIds.get(convertedShortcut)
      if (list != null && list.contains(actionId)) {
        return true
      }

      val parent = keymap.parent ?: return false
      convertedShortcut = keymap.convertMouseShortcut(shortcut)
      keymap = parent
    }
    while (true)
  }

  override fun getActionIds(shortcut: MouseShortcut): List<String> {
    return getActionIds(shortcut, { it.mouseShortcutToActionIds }, KeymapImpl::convertMouseShortcut)
  }

  private fun <T> getActionIds(shortcut: T, shortcutToActionIds: (keymap: KeymapImpl) -> Map<T, MutableList<String>>, convertShortcut: (keymap: KeymapImpl, shortcut: T) -> T): List<String> {
    // first, get keystrokes from own map
    var list = shortcutToActionIds(this).get(shortcut)
    val parentIds = parent?.getActionIds(convertShortcut(this, shortcut), shortcutToActionIds, convertShortcut) ?: emptyList()
    var isOriginalListInstance = list != null
    for (id in parentIds) {
      // add actions from parent keymap only if they are absent in this keymap
      // do not add parent bind actions, if bind-on action is overwritten in the child
      if (actionIdToShortcuts.containsKey(id) || actionIdToShortcuts.containsKey(keymapManager.getActionBinding(id))) {
        continue
      }

      if (list == null) {
        list = SmartList()
      }
      else if (isOriginalListInstance) {
        list = SmartList(list)
        isOriginalListInstance = false
      }

      if (!list.contains(id)) {
        list.add(id)
      }
    }
    sortInRegistrationOrder(list ?: return emptyList())
    return list
  }

  fun isActionBound(actionId: String): Boolean = keymapManager.boundActions.contains(actionId)

  override fun getShortcuts(actionId: String?): Array<Shortcut> = getMutableShortcutList(actionId).let { if (it.isEmpty()) Shortcut.EMPTY_ARRAY else it.toTypedArray() }

  private fun getMutableShortcutList(actionId: String?): List<Shortcut> {
    if (actionId == null) {
      return emptyList()
    }

    // it is critical to use convertShortcut - otherwise MacOSDefaultKeymap doesn't convert shortcuts
    // todo why not convert on add? why we don't need to convert our own shortcuts?
    return actionIdToShortcuts.get(actionId) ?: keymapManager.getActionBinding(actionId)?.let { actionIdToShortcuts.get(it) } ?: parent?.getMutableShortcutList(actionId)?.mapSmart { convertShortcut(it) } ?: emptyList()
  }

  fun getOwnShortcuts(actionId: String): Array<Shortcut> {
    val own = actionIdToShortcuts.get(actionId) ?: return Shortcut.EMPTY_ARRAY
    return if (own.isEmpty()) Shortcut.EMPTY_ARRAY else own.toTypedArray()
  }

  // you must clear actionIdToShortcuts before call
  protected open fun readExternal(keymapElement: Element) {
    if (KEY_MAP != keymapElement.name) {
      throw InvalidDataException("unknown element: $keymapElement")
    }

    if (keymapElement.getAttributeValue(VERSION_ATTRIBUTE) == null) {
      Converter01.convert(keymapElement)
    }

    name = keymapElement.getAttributeValue(NAME_ATTRIBUTE)

    unknownParentName = null

    keymapElement.getAttributeValue(PARENT_ATTRIBUTE)?.let { parentSchemeName ->
      var parentScheme = findParentScheme(parentSchemeName)
      if (parentScheme == null && parentSchemeName == "Default for Mac OS X") {
        // https://youtrack.jetbrains.com/issue/RUBY-17767#comment=27-1374197
        parentScheme = findParentScheme("Mac OS X")
      }

      if (parentScheme == null) {
        LOG.warn("Cannot find parent scheme $parentSchemeName for scheme $name")
        unknownParentName = parentSchemeName
        notifyAboutMissingKeymap(parentSchemeName, "Cannot find parent keymap \"$parentSchemeName\" for \"$name\"")
      }
      else {
        parent = parentScheme as KeymapImpl
        canModify = true
      }
    }

    val actionIds = HashSet<String>()
    val skipInserts = SystemInfo.isMac && (ApplicationManager.getApplication() == null || !ApplicationManager.getApplication().isUnitTestMode)
    for (actionElement in keymapElement.children) {
      if (actionElement.name != ACTION) {
        throw InvalidDataException("unknown element: $actionElement; Keymap's name=$name")
      }

      val id = actionElement.getAttributeValue(ID_ATTRIBUTE) ?: throw InvalidDataException("Attribute 'id' cannot be null; Keymap's name=$name")
      actionIds.add(id)
      val shortcuts = SmartList<Shortcut>()
      // always creates list even if no shortcuts - empty action element means that action overrides parent to denote that no shortcuts
      actionIdToShortcuts.put(id, shortcuts)

      for (shortcutElement in actionElement.children) {
        if (KEYBOARD_SHORTCUT == shortcutElement.name) {
          // Parse first keystroke
          val firstKeyStrokeStr = shortcutElement.getAttributeValue(FIRST_KEYSTROKE_ATTRIBUTE)
                                  ?: throw InvalidDataException("Attribute '$FIRST_KEYSTROKE_ATTRIBUTE' cannot be null; Action's id=$id; Keymap's name=$name")
          if (skipInserts && firstKeyStrokeStr.contains("INSERT")) {
            continue
          }

          val firstKeyStroke = KeyStrokeAdapter.getKeyStroke(firstKeyStrokeStr) ?: continue

          // Parse second keystroke
          var secondKeyStroke: KeyStroke? = null
          val secondKeyStrokeStr = shortcutElement.getAttributeValue(SECOND_KEYSTROKE_ATTRIBUTE)
          if (secondKeyStrokeStr != null) {
            secondKeyStroke = KeyStrokeAdapter.getKeyStroke(secondKeyStrokeStr) ?: continue
          }
          shortcuts.add(KeyboardShortcut(firstKeyStroke, secondKeyStroke))
        }
        else if (KEYBOARD_GESTURE_SHORTCUT == shortcutElement.name) {
          val strokeText = shortcutElement.getAttributeValue(KEYBOARD_GESTURE_KEY) ?: throw InvalidDataException(
            "Attribute '$KEYBOARD_GESTURE_KEY' cannot be null; Action's id=$id; Keymap's name=$name")

          val stroke = KeyStrokeAdapter.getKeyStroke(strokeText) ?: continue
          val modifierText = shortcutElement.getAttributeValue(KEYBOARD_GESTURE_MODIFIER)
          var modifier: KeyboardGestureAction.ModifierType? = null
          if (KeyboardGestureAction.ModifierType.dblClick.toString().equals(modifierText, ignoreCase = true)) {
            modifier = KeyboardGestureAction.ModifierType.dblClick
          }
          else if (KeyboardGestureAction.ModifierType.hold.toString().equals(modifierText, ignoreCase = true)) {
            modifier = KeyboardGestureAction.ModifierType.hold
          }

          if (modifier == null) {
            throw InvalidDataException("Wrong modifier=$modifierText action id=$id keymap=$name")
          }

          shortcuts.add(KeyboardModifierGestureShortcut.newInstance(modifier, stroke))
        }
        else if (MOUSE_SHORTCUT == shortcutElement.name) {
          val keystrokeString = shortcutElement.getAttributeValue(KEYSTROKE_ATTRIBUTE) ?: throw InvalidDataException(
            "Attribute 'keystroke' cannot be null; Action's id=$id; Keymap's name=$name")

          try {
            shortcuts.add(KeymapUtil.parseMouseShortcut(keystrokeString))
          }
          catch (e: InvalidDataException) {
            throw InvalidDataException("Wrong mouse-shortcut: '$keystrokeString'; Action's id=$id; Keymap's name=$name")
          }

        }
        else {
          throw InvalidDataException("unknown element: $shortcutElement; Keymap's name=$name")
        }
      }
    }

    ActionsCollectorImpl.onActionsLoadedFromKeymapXml(this, actionIds)
    cleanShortcutsCache()
  }

  protected open fun findParentScheme(parentSchemeName: String): Keymap? = keymapManager.schemeManager.findSchemeByName(parentSchemeName)

  override fun writeScheme(): Element {
    dataHolder?.let {
      return it.read()
    }

    val keymapElement = Element(KEY_MAP)
    keymapElement.setAttribute(VERSION_ATTRIBUTE, Integer.toString(1))
    keymapElement.setAttribute(NAME_ATTRIBUTE, name)

    (parent?.name ?: unknownParentName)?.let {
      keymapElement.setAttribute(PARENT_ATTRIBUTE, it)
    }
    writeOwnActionIds(keymapElement)

    schemeState = SchemeState.UNCHANGED
    return keymapElement
  }

  private fun writeOwnActionIds(keymapElement: Element) {
    val ownActionIds = ownActionIds
    Arrays.sort(ownActionIds)
    for (actionId in ownActionIds) {
      val shortcuts = actionIdToShortcuts.get(actionId) ?: continue
      val actionElement = Element(ACTION)
      actionElement.setAttribute(ID_ATTRIBUTE, actionId)
      for (shortcut in shortcuts) {
        when (shortcut) {
          is KeyboardShortcut -> {
            val element = Element(KEYBOARD_SHORTCUT)
            element.setAttribute(FIRST_KEYSTROKE_ATTRIBUTE, KeyStrokeAdapter.toString(shortcut.firstKeyStroke))
            shortcut.secondKeyStroke?.let {
              element.setAttribute(SECOND_KEYSTROKE_ATTRIBUTE, KeyStrokeAdapter.toString(it))
            }
            actionElement.addContent(element)
          }
          is MouseShortcut -> {
            val element = Element(MOUSE_SHORTCUT)
            element.setAttribute(KEYSTROKE_ATTRIBUTE, KeymapUtil.getMouseShortcutString(shortcut))
            actionElement.addContent(element)
          }
          is KeyboardModifierGestureShortcut -> {
            val element = Element(KEYBOARD_GESTURE_SHORTCUT)
            element.setAttribute(KEYBOARD_GESTURE_SHORTCUT, KeyStrokeAdapter.toString(shortcut.stroke))
            element.setAttribute(KEYBOARD_GESTURE_MODIFIER, shortcut.type.name)
            actionElement.addContent(element)
          }
          else -> throw IllegalStateException("unknown shortcut class: $shortcut")
        }
      }
      keymapElement.addContent(actionElement)
    }
  }

  fun clearOwnActionsIds() {
    actionIdToShortcuts.clear()
    cleanShortcutsCache()
  }

  fun hasOwnActionId(actionId: String): Boolean = actionIdToShortcuts.containsKey(actionId)

  fun clearOwnActionsId(actionId: String) {
    actionIdToShortcuts.remove(actionId)
    cleanShortcutsCache()
  }

  override fun getActionIds(): Array<String> = ArrayUtilRt.toStringArray(actionIdList)

  override fun getActionIdList(): Set<String> {
    val ids = LinkedHashSet<String>()
    ids.addAll(actionIdToShortcuts.keys)
    var parent = parent
    while (parent != null) {
      ids.addAll(parent.actionIdToShortcuts.keys)
      parent = parent.parent
    }
    return ids
  }

  override fun getConflicts(actionId: String, keyboardShortcut: KeyboardShortcut): Map<String, MutableList<KeyboardShortcut>> {
    val result = THashMap<String, MutableList<KeyboardShortcut>>()

    for (id in getActionIds(keyboardShortcut.firstKeyStroke)) {
      if (id == actionId || (actionId.startsWith("Editor") && id == "$${actionId.substring(6)}")) {
        continue
      }

      val useShortcutOf = keymapManager.getActionBinding(id)
      if (useShortcutOf != null && useShortcutOf == actionId) {
        continue
      }

      for (shortcut1 in getMutableShortcutList(id)) {
        if (shortcut1 !is KeyboardShortcut || shortcut1.firstKeyStroke != keyboardShortcut.firstKeyStroke) {
          continue
        }

        if (keyboardShortcut.secondKeyStroke != null && shortcut1.secondKeyStroke != null && keyboardShortcut.secondKeyStroke != shortcut1.secondKeyStroke) {
          continue
        }

        result.getOrPut(id) { SmartList() }.add(shortcut1)
      }
    }

    return result
  }

  protected open fun convertKeyStroke(keyStroke: KeyStroke): KeyStroke = keyStroke

  protected open fun convertMouseShortcut(shortcut: MouseShortcut): MouseShortcut = shortcut

  protected open fun convertShortcut(shortcut: Shortcut): Shortcut = shortcut

  override fun addShortcutChangeListener(listener: Keymap.Listener) {
    listeners.add(listener)
  }

  override fun removeShortcutChangeListener(listener: Keymap.Listener) {
    listeners.remove(listener)
  }

  private fun fireShortcutChanged(actionId: String) {
    (KeymapManager.getInstance() as? KeymapManagerImpl)?.fireShortcutChanged(this, actionId)
    for (listener in listeners) {
      listener.onShortcutChanged(actionId)
    }
  }

  override fun toString(): String = presentableName

  override fun equals(other: Any?): Boolean {
    if (other !is KeymapImpl) return false
    if (other === this) return true
    if (name != other.name) return false
    if (canModify != other.canModify) return false
    if (parent != other.parent) return false
    if (actionIdToShortcuts != other.actionIdToShortcuts) return false
    return true
  }

  override fun hashCode(): Int = name.hashCode()
}

private fun sortInRegistrationOrder(ids: MutableList<String>) {
  ids.sortWith(ActionManagerEx.getInstanceEx().registrationOrderComparator)
}

// compare two lists in any order
private fun areShortcutsEqual(shortcuts1: List<Shortcut>, shortcuts2: List<Shortcut>): Boolean {
  if (shortcuts1.size != shortcuts2.size) {
    return false
  }

  for (shortcut in shortcuts1) {
    if (!shortcuts2.contains(shortcut)) {
      return false
    }
  }
  return true
}

private val macOSKeymap = "com.intellij.plugins.macoskeymap"
private val gnomeKeymap = "com.intellij.plugins.gnomekeymap"
private val kdeKeymap = "com.intellij.plugins.kdekeymap"
private val xwinKeymap = "com.intellij.plugins.xwinkeymap"
private val eclipseKeymap = "com.intellij.plugins.eclipsekeymap"
private val emacsKeymap = "com.intellij.plugins.emacskeymap"
private val netbeansKeymap = "com.intellij.plugins.netbeanskeymap"
private val resharperKeymap = "com.intellij.plugins.resharperkeymap"
private val sublimeKeymap = "com.intellij.plugins.sublimetextkeymap"
private val visualStudioKeymap = "com.intellij.plugins.visualstudiokeymap"
private val xcodeKeymap = "com.intellij.plugins.xcodekeymap"
private val visualAssistKeymap = "com.intellij.plugins.visualassistkeymap"
private val riderKeymap = "com.intellij.plugins.riderkeymap"

internal fun notifyAboutMissingKeymap(keymapName: String, message: String) {
  val connection = ApplicationManager.getApplication().messageBus.connect()
  connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
    override fun projectOpened(project: Project) {
      connection.disconnect()
      ApplicationManager.getApplication().invokeLater(
        {
          // TODO remove when PluginAdvertiser implements that
          @Suppress("SpellCheckingInspection")
          val pluginId = when (keymapName) {
            "Mac OS X",
            "Mac OS X 10.5+" -> macOSKeymap
            "Default for GNOME" -> gnomeKeymap
            "Default for KDE" -> kdeKeymap
            "Default for XWin" -> xwinKeymap
            "Eclipse",
            "Eclipse (Mac OS X)" -> eclipseKeymap
            "Emacs" -> emacsKeymap
            "NetBeans 6.5" -> netbeansKeymap
            "ReSharper",
            "ReSharper OSX" -> resharperKeymap
            "Sublime Text",
            "Sublime Text (Mac OS X)" -> sublimeKeymap
            "Visual Studio",
            "Visual Studio OSX" -> visualStudioKeymap
            "Visual Assist",
            "Visual Assist OSX" -> visualAssistKeymap
            "Xcode" -> xcodeKeymap
            "Rider",
            "Rider OSX"-> riderKeymap
            else -> null
          }
          val action: AnAction? = when (pluginId) {
            null -> object : NotificationAction(IdeBundle.message("action.text.search.for.keymap", keymapName)) {
              override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                //TODO enableSearch("$keymapName /tag:Keymap")?.run()
                ShowSettingsUtil.getInstance().showSettingsDialog(e.project, PluginManagerConfigurable::class.java)
              }
            }
            else -> object : NotificationAction(IdeBundle.message("action.text.install.keymap", keymapName)) {
              override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                val connect = ApplicationManager.getApplication().messageBus.connect()
                connect.subscribe(KeymapManagerListener.TOPIC, object: KeymapManagerListener {
                  override fun keymapAdded(keymap: Keymap) {
                    ApplicationManager.getApplication().invokeLater {
                      if (keymap.name == keymapName) {
                        connect.disconnect()
                        KeymapManagerEx.getInstanceEx().activeKeymap = keymap
                        val group = NotificationGroup("Keymap", NotificationDisplayType.BALLOON, true)
                        val notificationManager = SingletonNotificationManager(group, NotificationType.INFORMATION)
                        notificationManager.notify(IdeBundle.message("notification.content.keymap.successfully.activated", keymapName), project)
                      }
                    }
                  }
                })

                  PluginsAdvertiser.installAndEnable(project, getPluginIdWithDependencies(pluginId), false) {

                  }
                notification.expire()
              }

              fun toPluginIds(vararg ids: String) = ids.map { PluginId.getId(it) }.toSet()

              private fun getPluginIdWithDependencies(pluginId: String): Set<PluginId> {
                return when (pluginId) {
                  gnomeKeymap -> toPluginIds(gnomeKeymap, xwinKeymap)
                  kdeKeymap -> toPluginIds(kdeKeymap, xwinKeymap)
                  resharperKeymap -> toPluginIds(resharperKeymap, visualStudioKeymap)
                  xcodeKeymap -> toPluginIds(xcodeKeymap, macOSKeymap)
                  else -> toPluginIds(pluginId)
                }
              }
            }
          }
          NOTIFICATION_MANAGER.notify(IdeBundle.message("notification.group.missing.keymap"), message, action = action)
        }, ModalityState.NON_MODAL)
    }
  }
  )
}
