/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SerializableScheme
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.ExternalizableSchemeAdapter
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.util.ArrayUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.OrderedSet
import gnu.trove.THashMap
import org.jdom.Element
import java.awt.event.InputEvent
import java.util.*
import javax.swing.KeyStroke
import kotlin.reflect.jvm.internal.impl.utils.SmartList

private val KEY_MAP = "keymap"
private val KEYBOARD_SHORTCUT = "keyboard-shortcut"
private val KEYBOARD_GESTURE_SHORTCUT = "keyboard-gesture-shortcut"
private val KEYBOARD_GESTURE_KEY = "keystroke"
private val KEYBOARD_GESTURE_MODIFIER = "modifier"
private val KEYSTROKE_ATTRIBUTE = "keystroke"
private val FIRST_KEYSTROKE_ATTRIBUTE = "first-keystroke"
private val SECOND_KEYSTROKE_ATTRIBUTE = "second-keystroke"
private val ACTION = "action"
private val VERSION_ATTRIBUTE = "version"
private val PARENT_ATTRIBUTE = "parent"
private val NAME_ATTRIBUTE = "name"
private val ID_ATTRIBUTE = "id"
private val MOUSE_SHORTCUT = "mouse-shortcut"

open class KeymapImpl : ExternalizableSchemeAdapter(), Keymap, SerializableScheme {
  private var parent: KeymapImpl? = null
  private var canModify = true

  private val actionIdToListOfShortcuts = THashMap<String, OrderedSet<Shortcut>>()

  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<Keymap.Listener>()

  private val keymapManager by lazy { KeymapManagerEx.getInstanceEx()!! }

  /**
   * @return IDs of the action which are specified in the keymap. It doesn't
   * *         return IDs of action from parent keymap.
   */
  val ownActionIds: Array<String>
    get() = actionIdToListOfShortcuts.keys.toTypedArray()

  private var _mouseShortcutToListOfIds: Map<MouseShortcut, MutableList<String>>? = null
  private val mouseShortcutToListOfIds: Map<MouseShortcut, MutableList<String>>
    get() {
      var result = _mouseShortcutToListOfIds
      if (result == null) {
        result = fillShortcutToListOfIds(MouseShortcut::class.java)
        _mouseShortcutToListOfIds = result
      }
      return result
    }

  private var _keystrokeToListOfIds: MutableMap<KeyStroke, MutableList<String>>? = null
  private val keystrokeToListOfIds: Map<KeyStroke, MutableList<String>>
    get() {
      var result = _keystrokeToListOfIds
      if (result != null) {
        return result
      }

      result = THashMap<KeyStroke, MutableList<String>>()
      _keystrokeToListOfIds = result
      for (id in ContainerUtil.concat(actionIdToListOfShortcuts.keys, keymapManager.boundActions)) {
        addKeystrokesMap(id, result)
      }
      return result
    }

  companion object {
    fun setCanModify(keymapImpl: KeymapImpl, `val`: Boolean) {
      keymapImpl.canModify = `val`
    }
  }

  override fun getPresentableName() = name

  override fun deriveKeymap(newName: String): KeymapImpl {
    if (canModify()) {
      return copy()
    }
    else {
      val newKeymap = KeymapImpl()
      newKeymap.parent = this
      newKeymap.name = newName
      newKeymap.canModify = canModify()
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

  fun copy() = copyTo(KeymapImpl())

  fun copyTo(otherKeymap: KeymapImpl): KeymapImpl {
    otherKeymap.parent = parent
    otherKeymap.name = name
    otherKeymap.canModify = canModify()

    otherKeymap.cleanShortcutsCache()

    otherKeymap.actionIdToListOfShortcuts.clear()
    otherKeymap.actionIdToListOfShortcuts.ensureCapacity(actionIdToListOfShortcuts.size)
    actionIdToListOfShortcuts.forEachEntry { actionId, shortcuts ->
      otherKeymap.actionIdToListOfShortcuts.put(actionId, OrderedSet(shortcuts))
      true
    }
    return otherKeymap
  }

  override fun getParent() = parent

  override fun canModify() = canModify

  protected open fun getParentShortcuts(actionId: String) = parent!!.getShortcuts(actionId)

  override fun addShortcut(actionId: String, shortcut: Shortcut) {
    addShortcutSilently(actionId, shortcut, true)
    fireShortcutChanged(actionId)
  }

  private fun addShortcutSilently(actionId: String, shortcut: Shortcut, checkParentShortcut: Boolean) {
    var list: OrderedSet<Shortcut>? = actionIdToListOfShortcuts.get(actionId)
    if (list == null) {
      list = OrderedSet<Shortcut>()
      actionIdToListOfShortcuts.put(actionId, list)
      val boundShortcuts = getBoundShortcuts(actionId)
      if (boundShortcuts != null) {
        list.addAll(boundShortcuts)
      }
      else if (parent != null) {
        list.addAll(getParentShortcuts(actionId))
      }
    }
    list.add(shortcut)

    if (checkParentShortcut && parent != null && areShortcutsEqual(getParentShortcuts(actionId), getShortcuts(actionId))) {
      actionIdToListOfShortcuts.remove(actionId)
    }
    cleanShortcutsCache()
  }

  private fun cleanShortcutsCache() {
    _keystrokeToListOfIds = null
    _mouseShortcutToListOfIds = null
  }

  override fun removeAllActionShortcuts(actionId: String) {
    for (shortcut in getShortcuts(actionId)) {
      removeShortcut(actionId, shortcut)
    }
  }

  override fun removeShortcut(actionId: String, toDelete: Shortcut) {
    val list = actionIdToListOfShortcuts.get(actionId)
    if (list == null) {
      var inherited = getBoundShortcuts(actionId)
      if (inherited == null && parent != null) {
        inherited = getParentShortcuts(actionId)
      }

      if (inherited != null) {
        var affected = false
        val newShortcuts = OrderedSet<Shortcut>(inherited.size)
        for (eachInherited in inherited) {
          if (toDelete == eachInherited) {
            // skip this one
            affected = true
          }
          else {
            newShortcuts.add(eachInherited)
          }
        }
        if (affected) {
          actionIdToListOfShortcuts.put(actionId, newShortcuts)
        }
      }
    }
    else {
      val it = list.iterator()
      while (it.hasNext()) {
        val each = it.next()
        if (toDelete == each) {
          it.remove()
          if (parent != null && areShortcutsEqual(getParentShortcuts(actionId), getShortcuts(actionId)) || parent == null && list.isEmpty()) {
            actionIdToListOfShortcuts.remove(actionId)
          }
          break
        }
      }
    }

    cleanShortcutsCache()
    fireShortcutChanged(actionId)
  }

  private val gestureToListOfIds: Map<KeyboardModifierGestureShortcut, List<String>> by lazy { fillShortcutToListOfIds(KeyboardModifierGestureShortcut::class.java) }

  private fun <T : Shortcut> fillShortcutToListOfIds(shortcutClass: Class<T>): Map<T, MutableList<String>> {
    val map = THashMap<T, MutableList<String>>()
    for (id in ContainerUtil.concat(actionIdToListOfShortcuts.keys, keymapManager.boundActions)) {
      addActionToShortcutsMap(id, map, shortcutClass)
    }
    return map
  }

  private fun <T : Shortcut> addActionToShortcutsMap(actionId: String, strokesMap: MutableMap<T, MutableList<String>>, shortcutClass: Class<T>) {
    for (shortcut in _getShortcuts(actionId)) {
      if (!shortcutClass.isAssignableFrom(shortcut.javaClass)) {
        continue
      }

      @Suppress("UNCHECKED_CAST")
      val listOfIds = strokesMap.getOrPut(shortcut as T) { SmartList() }
      // action may have more that 1 shortcut with same first keystroke
      if (!listOfIds.contains(actionId)) {
        listOfIds.add(actionId)
      }
    }
  }

  private fun addKeystrokesMap(actionId: String, strokesMap: MutableMap<KeyStroke, MutableList<String>>) {
    for (shortcut in _getShortcuts(actionId)) {
      if (shortcut !is KeyboardShortcut) {
        continue
      }

      val listOfIds = strokesMap.getOrPut(shortcut.firstKeyStroke) { SmartList() }
      // action may have more that 1 shortcut with same first keystroke
      if (!listOfIds.contains(actionId)) {
        listOfIds.add(actionId)
      }
    }
  }

  private fun _getShortcuts(actionId: String): OrderedSet<Shortcut> {
    val keymapManager = keymapManager
    var listOfShortcuts = actionIdToListOfShortcuts.get(actionId)
    if (listOfShortcuts != null) {
      return listOfShortcuts
    }

    listOfShortcuts = OrderedSet<Shortcut>()
    keymapManager.getActionBinding(actionId)?.let {
      listOfShortcuts!!.addAll(_getShortcuts(it))
    }
    return listOfShortcuts
  }

  protected open fun getParentActionIds(firstKeyStroke: KeyStroke) = parent!!.getActionIds(firstKeyStroke)

  private fun getActionIds(shortcut: KeyboardModifierGestureShortcut): Array<String> {
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
          if (!actionIdToListOfShortcuts.containsKey(id)) {
            list.add(id)
          }
        }
      }
    }
    return sortInOrderOfRegistration(list)
  }

  override fun getActionIds(firstKeyStroke: KeyStroke): Array<String> {
    // first, get keystrokes from own map
    var list = keystrokeToListOfIds.get(firstKeyStroke)
    if (parent != null) {
      val ids = getParentActionIds(firstKeyStroke)
      if (ids.isNotEmpty()) {
        var originalListInstance = true
        for (id in ids) {
          // add actions from parent keymap only if they are absent in this keymap
          // do not add parent bind actions, if bind-on action is overwritten in the child
          if (!actionIdToListOfShortcuts.containsKey(id) && !actionIdToListOfShortcuts.containsKey(getActionBinding(id))) {
            if (list == null) {
              list = SmartList<String>()
              originalListInstance = false
            }
            else if (originalListInstance) {
              list = SmartList(list)
              originalListInstance = false
            }
            if (!list.contains(id)) {
              list.add(id)
            }
          }
        }
      }
    }
    return sortInOrderOfRegistration(list)
  }

  override fun getActionIds(firstKeyStroke: KeyStroke, secondKeyStroke: KeyStroke): Array<String> {
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
      is MouseShortcut -> getActionIds(shortcut)
      is KeyboardModifierGestureShortcut -> getActionIds(shortcut)
      else -> ArrayUtil.EMPTY_STRING_ARRAY
    }
  }

  protected open fun getParentActionIds(shortcut: MouseShortcut) = parent!!.getActionIds(shortcut)

  override fun getActionIds(shortcut: MouseShortcut): Array<String> {
    // first, get shortcuts from own map
    var list = mouseShortcutToListOfIds.get(shortcut)
    if (parent != null) {
      val ids = getParentActionIds(shortcut)
      if (ids.isNotEmpty()) {
        var originalListInstance = true
        for (id in ids) {
          // add actions from parent keymap only if they are absent in this keymap
          if (!actionIdToListOfShortcuts.containsKey(id)) {
            if (list == null) {
              list = SmartList<String>()
              originalListInstance = false
            }
            else if (originalListInstance) {
              list = SmartList(list)
            }
            list.add(id)
          }
        }
      }
    }
    return sortInOrderOfRegistration(list)
  }

  fun isActionBound(actionId: String) = keymapManager.boundActions.contains(actionId)

  fun getActionBinding(actionId: String): String? = keymapManager.getActionBinding(actionId)

  override fun getShortcuts(actionId: String?): Array<Shortcut> {
    if (actionId == null) {
      return Shortcut.EMPTY_ARRAY
    }

    val shortcuts = actionIdToListOfShortcuts.get(actionId)

    if (shortcuts == null) {
      getBoundShortcuts(actionId)?.let {
        return it
      }
    }

    if (shortcuts == null) {
      return parent?.getShortcuts(actionId) ?: Shortcut.EMPTY_ARRAY
    }
    return if (shortcuts.isEmpty()) Shortcut.EMPTY_ARRAY else shortcuts.toTypedArray()
  }

  fun getOwnShortcuts(actionId: String): Array<Shortcut>? {
    val own = actionIdToListOfShortcuts.get(actionId) ?: return null
    return if (own.isEmpty()) Shortcut.EMPTY_ARRAY else own.toTypedArray()
  }

  private fun getBoundShortcuts(actionId: String) = keymapManager.getActionBinding(actionId)?.let { getOwnShortcuts(it) }

  open fun readExternal(keymapElement: Element, existingKeymaps: Array<Keymap>) {
    if (KEY_MAP != keymapElement.name) {
      throw InvalidDataException("unknown element: $keymapElement")
    }

    if (keymapElement.getAttributeValue(VERSION_ATTRIBUTE) == null) {
      Converter01.convert(keymapElement)
    }

    val parentName = keymapElement.getAttributeValue(PARENT_ATTRIBUTE)
    if (parentName != null) {
      for (existingKeymap in existingKeymaps) {
        if (parentName == existingKeymap.name) {
          parent = existingKeymap as KeymapImpl
          canModify = true
          break
        }
      }
    }

    name = keymapElement.getAttributeValue(NAME_ATTRIBUTE)

    val idToShortcuts = THashMap<String, MutableList<Shortcut>>()
    val skipInserts = SystemInfo.isMac && (ApplicationManager.getApplication() == null || !ApplicationManager.getApplication().isUnitTestMode)
    for (actionElement in keymapElement.children) {
      if (actionElement.name != ACTION) {
        throw InvalidDataException("unknown element: $actionElement; Keymap's name=$name")
      }

      val id = actionElement.getAttributeValue(ID_ATTRIBUTE) ?: throw InvalidDataException("Attribute 'id' cannot be null; Keymap's name=$name")

      idToShortcuts.put(id, SmartList<Shortcut>())
      for (shortcutElement in actionElement.children) {
        if (KEYBOARD_SHORTCUT == shortcutElement.name) {
          // Parse first keystroke

          val firstKeyStrokeStr = shortcutElement.getAttributeValue(FIRST_KEYSTROKE_ATTRIBUTE) ?: throw InvalidDataException(
            "Attribute '$FIRST_KEYSTROKE_ATTRIBUTE' cannot be null; Action's id=$id; Keymap's name=$name")
          if (skipInserts && firstKeyStrokeStr.contains("INSERT")) {
            continue
          }

          val firstKeyStroke = KeyStrokeAdapter.getKeyStroke(firstKeyStrokeStr) ?: continue
          // Parse second keystroke

          var secondKeyStroke: KeyStroke? = null
          val secondKeyStrokeStr = shortcutElement.getAttributeValue(SECOND_KEYSTROKE_ATTRIBUTE)
          if (secondKeyStrokeStr != null) {
            secondKeyStroke = KeyStrokeAdapter.getKeyStroke(secondKeyStrokeStr)
            if (secondKeyStroke == null) {
              // logged when parsed
              continue
            }
          }
          idToShortcuts.get(id)!!.add(KeyboardShortcut(firstKeyStroke, secondKeyStroke))
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

          idToShortcuts.get(id)!!.add(KeyboardModifierGestureShortcut.newInstance(modifier, stroke))
        }
        else if (MOUSE_SHORTCUT == shortcutElement.name) {
          val keystrokeString = shortcutElement.getAttributeValue(KEYSTROKE_ATTRIBUTE) ?: throw InvalidDataException(
            "Attribute 'keystroke' cannot be null; Action's id=$id; Keymap's name=$name")

          try {
            idToShortcuts.get(id)!!.add(KeymapUtil.parseMouseShortcut(keystrokeString))
          }
          catch (exc: InvalidDataException) {
            throw InvalidDataException("Wrong mouse-shortcut: '$keystrokeString'; Action's id=$id; Keymap's name=$name")
          }

        }
        else {
          throw InvalidDataException("unknown element: $shortcutElement; Keymap's name=$name")
        }
      }
    }

    // Add read shortcuts
    for (id in idToShortcuts.keys) {
      // It's a trick! After that parent's shortcuts are not added to the keymap
      actionIdToListOfShortcuts.put(id, OrderedSet<Shortcut>(2))
      for (shortcut in idToShortcuts.get(id)!!) {
        addShortcutSilently(id, shortcut, false)
      }
    }
  }

  override fun writeScheme(): Element {
    val keymapElement = Element(KEY_MAP)
    keymapElement.setAttribute(VERSION_ATTRIBUTE, Integer.toString(1))
    keymapElement.setAttribute(NAME_ATTRIBUTE, name)

    if (parent != null) {
      keymapElement.setAttribute(PARENT_ATTRIBUTE, parent!!.name)
    }
    writeOwnActionIds(keymapElement)
    return keymapElement
  }

  private fun writeOwnActionIds(keymapElement: Element) {
    val ownActionIds = ownActionIds
    Arrays.sort(ownActionIds)
    for (actionId in ownActionIds) {
      val actionElement = Element(ACTION)
      actionElement.setAttribute(ID_ATTRIBUTE, actionId)
      // Save keyboard shortcuts
      for (shortcut in getShortcuts(actionId)) {
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
            element.setAttribute(KEYSTROKE_ATTRIBUTE, getMouseShortcutString(shortcut))
            actionElement.addContent(element)
          }
          is KeyboardModifierGestureShortcut -> {
            val element = Element(KEYBOARD_GESTURE_SHORTCUT)
            element.setAttribute(KEYBOARD_GESTURE_SHORTCUT, KeyStrokeAdapter.toString(shortcut.stroke))
            element.setAttribute(KEYBOARD_GESTURE_MODIFIER, shortcut.type.name)
            actionElement.addContent(element)
          }
          else -> throw IllegalStateException("unknown shortcut class: " + shortcut)
        }
      }
      keymapElement.addContent(actionElement)
    }
  }

  fun clearOwnActionsIds() {
    actionIdToListOfShortcuts.clear()
    cleanShortcutsCache()
  }

  fun hasOwnActionId(actionId: String) = actionIdToListOfShortcuts.containsKey(actionId)

  fun clearOwnActionsId(actionId: String) {
    actionIdToListOfShortcuts.remove(actionId)
    cleanShortcutsCache()
  }

  override fun getActionIds(): Array<String> {
    val ids = LinkedHashSet<String>()
    parent?.let {
      ids.addAll(it.actionIds)
    }
    ids.addAll(ownActionIds)
    return ArrayUtilRt.toStringArray(ids)
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

      for (shortcut1 in getShortcuts(id)) {
        if (shortcut1 !is KeyboardShortcut || shortcut1.firstKeyStroke != keyboardShortcut.firstKeyStroke) {
          continue
        }

        if (keyboardShortcut.secondKeyStroke != null && shortcut1.secondKeyStroke != null && keyboardShortcut.secondKeyStroke != shortcut1.secondKeyStroke) {
          continue
        }

        result.getOrPut(id) { SmartList<KeyboardShortcut>() }.add(shortcut1)
      }
    }

    return result
  }

  override fun addShortcutChangeListener(listener: Keymap.Listener) {
    listeners.add(listener)
  }

  override fun removeShortcutChangeListener(listener: Keymap.Listener) {
    listeners.remove(listener)
  }

  private fun fireShortcutChanged(actionId: String) {
    for (listener in listeners) {
      listener.onShortcutChanged(actionId)
    }
  }

  override fun toString() = presentableName

  override fun equals(other: Any?): Boolean {
    if (other !is KeymapImpl) return false
    if (name != other.name) return false
    if (canModify != other.canModify) return false
    if (parent != other.parent) return false
    if (actionIdToListOfShortcuts != other.actionIdToListOfShortcuts) return false
    return true
  }

  override fun hashCode() = name.hashCode()
}

private fun sortInOrderOfRegistration(ids: List<String>?): Array<String> {
  val array = ArrayUtilRt.toStringArray(ids)
  if (array.isNotEmpty()) {
    Arrays.sort(array, ActionManagerEx.getInstanceEx().registrationOrderComparator)
  }
  return array
}

private fun areShortcutsEqual(shortcuts1: Array<Shortcut>, shortcuts2: Array<Shortcut>): Boolean {
  if (shortcuts1.size != shortcuts2.size) {
    return false
  }

  for (shortcut in shortcuts1) {
    var parentShortcutEqual: Shortcut? = null
    for (parentShortcut in shortcuts2) {
      if (shortcut == parentShortcut) {
        parentShortcutEqual = parentShortcut
        break
      }
    }
    if (parentShortcutEqual == null) {
      return false
    }
  }
  return true
}

/**
 * @return string representation of passed mouse shortcut. This method should
 * *         be used only for serializing of the `MouseShortcut`
 */
private fun getMouseShortcutString(shortcut: MouseShortcut): String {
  if (Registry.`is`("ide.mac.forceTouch") && shortcut is PressureShortcut) {
    return "Force touch"
  }

  val buffer = StringBuilder()

  // modifiers
  val modifiers = shortcut.modifiers
  if (InputEvent.SHIFT_DOWN_MASK and modifiers > 0) {
    buffer.append("shift")
    buffer.append(' ')
  }
  if (InputEvent.CTRL_DOWN_MASK and modifiers > 0) {
    buffer.append("control")
    buffer.append(' ')
  }
  if (InputEvent.META_DOWN_MASK and modifiers > 0) {
    buffer.append("meta")
    buffer.append(' ')
  }
  if (InputEvent.ALT_DOWN_MASK and modifiers > 0) {
    buffer.append("alt")
    buffer.append(' ')
  }
  if (InputEvent.ALT_GRAPH_DOWN_MASK and modifiers > 0) {
    buffer.append("altGraph")
    buffer.append(' ')
  }

  // button
  buffer.append("button").append(shortcut.button).append(' ')

  if (shortcut.clickCount > 1) {
    buffer.append("doubleClick")
  }
  return buffer.toString().trim { it <= ' ' } // trim trailing space (if any)
}