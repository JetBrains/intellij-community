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
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.HashMap
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
private val SHIFT = "shift"
private val CONTROL = "control"
private val META = "meta"
private val ALT = "alt"
private val ALT_GRAPH = "altGraph"
private val DOUBLE_CLICK = "doubleClick"
private val EDITOR_ACTION_PREFIX = "Editor"

private val ourEmptyShortcutsArray = Shortcut.EMPTY_ARRAY

open class KeymapImpl : ExternalizableSchemeAdapter(), Keymap, SerializableScheme {
  private var myParent: KeymapImpl? = null
  private var myCanModify = true

  private val myActionIdToListOfShortcuts = THashMap<String, OrderedSet<Shortcut>>()

  /**
   * Don't use this field directly! Use it only through `getKeystroke2ListOfIds`.
   */
  private var myKeystroke2ListOfIds: MutableMap<KeyStroke, MutableList<String>>? = null
  private var myGesture2ListOfIds: MutableMap<KeyboardModifierGestureShortcut, MutableList<String>>? = null

  /**
   * Don't use this field directly! Use it only through `getMouseShortcut2ListOfIds`.
   */
  private var myMouseShortcut2ListOfIds: MutableMap<MouseShortcut, MutableList<String>>? = null
  private val myListeners = ContainerUtil.createLockFreeCopyOnWriteList<Keymap.Listener>()
  private var myKeymapManager: KeymapManagerEx? = null

  companion object {
    fun setCanModify(keymapImpl: KeymapImpl, `val`: Boolean) {
      keymapImpl.myCanModify = `val`
    }
  }

  override fun getPresentableName() = name

  override fun deriveKeymap(newName: String): KeymapImpl {
    if (canModify()) {
      return copy()
    }
    else {
      val newKeymap = KeymapImpl()
      newKeymap.myParent = this
      newKeymap.name = newName
      newKeymap.myCanModify = canModify()
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
    otherKeymap.myParent = myParent
    otherKeymap.name = name
    otherKeymap.myCanModify = canModify()

    otherKeymap.cleanShortcutsCache()

    otherKeymap.myActionIdToListOfShortcuts.clear()
    otherKeymap.myActionIdToListOfShortcuts.ensureCapacity(myActionIdToListOfShortcuts.size)
    myActionIdToListOfShortcuts.forEachEntry { actionId, shortcuts ->
      otherKeymap.myActionIdToListOfShortcuts.put(actionId, OrderedSet(shortcuts))
      true
    }
    return otherKeymap
  }

  override fun equals(other: Any?): Boolean {
    if (other !is KeymapImpl) return false
    if (name != other.name) return false
    if (myCanModify != other.myCanModify) return false
    if (myParent != other.myParent) return false
    if (myActionIdToListOfShortcuts != other.myActionIdToListOfShortcuts) return false
    return true
  }

  override fun hashCode() = name.hashCode()

  override fun getParent(): Keymap = myParent!!

  override fun canModify() = myCanModify

  protected open fun getParentShortcuts(actionId: String) = myParent!!.getShortcuts(actionId)

  override fun addShortcut(actionId: String, shortcut: Shortcut) {
    addShortcutSilently(actionId, shortcut, true)
    fireShortcutChanged(actionId)
  }

  private fun addShortcutSilently(actionId: String, shortcut: Shortcut, checkParentShortcut: Boolean) {
    var list: OrderedSet<Shortcut>? = myActionIdToListOfShortcuts.get(actionId)
    if (list == null) {
      list = OrderedSet<Shortcut>()
      myActionIdToListOfShortcuts.put(actionId, list)
      val boundShortcuts = getBoundShortcuts(actionId)
      if (boundShortcuts != null) {
        list.addAll(boundShortcuts)
      }
      else if (myParent != null) {
        list.addAll(getParentShortcuts(actionId))
      }
    }
    list.add(shortcut)

    if (checkParentShortcut && myParent != null && areShortcutsEqual(getParentShortcuts(actionId), getShortcuts(actionId))) {
      myActionIdToListOfShortcuts.remove(actionId)
    }
    cleanShortcutsCache()
  }

  private fun cleanShortcutsCache() {
    myKeystroke2ListOfIds = null
    myMouseShortcut2ListOfIds = null
  }

  override fun removeAllActionShortcuts(actionId: String) {
    for (shortcut in getShortcuts(actionId)) {
      removeShortcut(actionId, shortcut)
    }
  }

  override fun removeShortcut(actionId: String, toDelete: Shortcut) {
    val list = myActionIdToListOfShortcuts.get(actionId)
    if (list != null) {
      val it = list.iterator()
      while (it.hasNext()) {
        val each = it.next()
        if (toDelete == each) {
          it.remove()
          if (myParent != null && areShortcutsEqual(getParentShortcuts(actionId),
                                                    getShortcuts(actionId)) || myParent == null && list.isEmpty()) {
            myActionIdToListOfShortcuts.remove(actionId)
          }
          break
        }
      }
    }
    else {
      var inherited = getBoundShortcuts(actionId)
      if (inherited == null && myParent != null) {
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
          myActionIdToListOfShortcuts.put(actionId, newShortcuts)
        }
      }
    }
    cleanShortcutsCache()
    fireShortcutChanged(actionId)
  }

  private val keystrokeToListOfIds: Map<KeyStroke, MutableList<String>>
    get() {
      var result = myKeystroke2ListOfIds
      if (result != null) {
        return result
      }

      result = THashMap<KeyStroke, MutableList<String>>()
      myKeystroke2ListOfIds = result
      for (id in ContainerUtil.concat(myActionIdToListOfShortcuts.keys, keymapManager.boundActions)) {
        addKeystrokesMap(id, result)
      }
      return result
    }

  private val gesture2ListOfIds: Map<KeyboardModifierGestureShortcut, List<String>>
    get() {
      var result = myGesture2ListOfIds
      if (result == null) {
        result = THashMap<KeyboardModifierGestureShortcut, MutableList<String>>()
        myGesture2ListOfIds = result
        fillShortcutToListOfIds(result, KeyboardModifierGestureShortcut::class.java)
      }
      return result
    }

  private fun <T : Shortcut> fillShortcutToListOfIds(map: MutableMap<T, MutableList<String>>, shortcutClass: Class<T>) {
    for (id in ContainerUtil.concat(myActionIdToListOfShortcuts.keys, keymapManager.boundActions)) {
      addAction2ShortcutsMap(id, map, shortcutClass)
    }
  }

  private val mouseShortcut2ListOfIds: Map<MouseShortcut, MutableList<String>>
    get() {
      var result = myMouseShortcut2ListOfIds
      if (result == null) {
        result = THashMap<MouseShortcut, MutableList<String>>()
        myMouseShortcut2ListOfIds = result
        fillShortcutToListOfIds(result, MouseShortcut::class.java)
      }
      return result
    }

  private fun <T : Shortcut> addAction2ShortcutsMap(actionId: String, strokesMap: MutableMap<T, MutableList<String>>, shortcutClass: Class<T>) {
    for (shortcut in _getShortcuts(actionId)) {
      if (!shortcutClass.isAssignableFrom(shortcut.javaClass)) {
        continue
      }
      @Suppress("UNCHECKED_CAST")
      val t = shortcut as T

      var listOfIds: MutableList<String>? = strokesMap.get(t)
      if (listOfIds == null) {
        listOfIds = SmartList<String>()
        strokesMap.put(t, listOfIds)
      }

      // action may have more that 1 shortcut with same first keystroke
      if (!listOfIds.contains(actionId)) {
        listOfIds.add(actionId)
      }
    }
  }

  private fun addKeystrokesMap(actionId: String, strokesMap: MutableMap<KeyStroke, MutableList<String>>) {
    val listOfShortcuts = _getShortcuts(actionId)
    for (shortcut in listOfShortcuts) {
      if (shortcut !is KeyboardShortcut) {
        continue
      }
      val firstKeyStroke = shortcut.firstKeyStroke
      var listOfIds: MutableList<String>? = strokesMap.get(firstKeyStroke)
      if (listOfIds == null) {
        listOfIds = ArrayList<String>()
        strokesMap.put(firstKeyStroke, listOfIds)
      }

      // action may have more that 1 shortcut with same first keystroke
      if (!listOfIds.contains(actionId)) {
        listOfIds.add(actionId)
      }
    }
  }

  private fun _getShortcuts(actionId: String): OrderedSet<Shortcut> {
    val keymapManager = keymapManager
    var listOfShortcuts: OrderedSet<Shortcut>? = myActionIdToListOfShortcuts[actionId]
    if (listOfShortcuts != null) {
      return listOfShortcuts
    }
    else {
      listOfShortcuts = OrderedSet<Shortcut>()
    }

    val actionBinding = keymapManager.getActionBinding(actionId)
    if (actionBinding != null) {
      listOfShortcuts.addAll(_getShortcuts(actionBinding))
    }

    return listOfShortcuts
  }

  protected open fun getParentActionIds(firstKeyStroke: KeyStroke): Array<String> {
    return myParent!!.getActionIds(firstKeyStroke)
  }

  protected fun getParentActionIds(gesture: KeyboardModifierGestureShortcut): Array<String> {
    return myParent!!.getActionIds(gesture)
  }

  private fun getActionIds(shortcut: KeyboardModifierGestureShortcut): Array<String> {
    // first, get keystrokes from own map
    val map = gesture2ListOfIds
    val list = ArrayList<String>()

    for ((key, value) in map) {
      if (shortcut.startsWith(key)) {
        list.addAll(value)
      }
    }

    if (myParent != null) {
      val ids = getParentActionIds(shortcut)
      if (ids.isNotEmpty()) {
        for (id in ids) {
          // add actions from parent keymap only if they are absent in this keymap
          if (!myActionIdToListOfShortcuts.containsKey(id)) {
            list.add(id)
          }
        }
      }
    }
    return sortInOrderOfRegistration(ArrayUtil.toStringArray(list))
  }

  override fun getActionIds(firstKeyStroke: KeyStroke): Array<String> {
    // first, get keystrokes from own map
    var list: MutableList<String>? = keystrokeToListOfIds.get(firstKeyStroke)
    if (myParent != null) {
      val ids = getParentActionIds(firstKeyStroke)
      if (ids.isNotEmpty()) {
        var originalListInstance = true
        for (id in ids) {
          // add actions from parent keymap only if they are absent in this keymap
          // do not add parent bind actions, if bind-on action is overwritten in the child
          if (!myActionIdToListOfShortcuts.containsKey(id) && !myActionIdToListOfShortcuts.containsKey(getActionBinding(id))) {
            if (list == null) {
              list = ArrayList<String>()
              originalListInstance = false
            }
            else if (originalListInstance) {
              list = ArrayList(list)
              originalListInstance = false
            }
            if (!list.contains(id)) list.add(id)
          }
        }
      }
    }
    if (list == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY
    }
    return sortInOrderOfRegistration(ArrayUtil.toStringArray(list))
  }

  override fun getActionIds(firstKeyStroke: KeyStroke, secondKeyStroke: KeyStroke): Array<String> {
    val ids = getActionIds(firstKeyStroke)
    val actualBindings = ArrayList<String>()
    for (id in ids) {
      val shortcuts = getShortcuts(id)
      for (shortcut in shortcuts) {
        if (shortcut !is KeyboardShortcut) {
          continue
        }
        if (Comparing.equal(firstKeyStroke, shortcut.firstKeyStroke) && Comparing.equal(secondKeyStroke, shortcut.secondKeyStroke)) {
          actualBindings.add(id)
          break
        }
      }
    }
    return ArrayUtil.toStringArray(actualBindings)
  }

  override fun getActionIds(shortcut: Shortcut): Array<String> {
    if (shortcut is KeyboardShortcut) {
      val first = shortcut.firstKeyStroke
      val second = shortcut.secondKeyStroke
      return if (second != null) getActionIds(first, second) else getActionIds(first)
    }
    else if (shortcut is MouseShortcut) {
      return getActionIds(shortcut)
    }
    else if (shortcut is KeyboardModifierGestureShortcut) {
      return getActionIds(shortcut)
    }
    else {
      return ArrayUtil.EMPTY_STRING_ARRAY
    }
  }

  protected open fun getParentActionIds(shortcut: MouseShortcut): Array<String> {
    return myParent!!.getActionIds(shortcut)
  }

  override fun getActionIds(shortcut: MouseShortcut): Array<String> {
    // first, get shortcuts from own map
    var list: MutableList<String>? = mouseShortcut2ListOfIds.get(shortcut)
    if (myParent != null) {
      val ids = getParentActionIds(shortcut)
      if (ids.isNotEmpty()) {
        var originalListInstance = true
        for (id in ids) {
          // add actions from parent keymap only if they are absent in this keymap
          if (!myActionIdToListOfShortcuts.containsKey(id)) {
            if (list == null) {
              list = ArrayList<String>()
              originalListInstance = false
            }
            else if (originalListInstance) {
              list = ArrayList(list)
            }
            list.add(id)
          }
        }
      }
    }
    if (list == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY
    }
    return sortInOrderOfRegistration(ArrayUtil.toStringArray(list))
  }

  fun isActionBound(actionId: String): Boolean {
    return keymapManager.boundActions.contains(actionId)
  }

  fun getActionBinding(actionId: String): String? {
    return keymapManager.getActionBinding(actionId)
  }

  override fun getShortcuts(actionId: String): Array<Shortcut> {
    val shortcuts = myActionIdToListOfShortcuts[actionId]

    if (shortcuts == null) {
      val boundShortcuts = getBoundShortcuts(actionId)
      if (boundShortcuts != null) return boundShortcuts
    }

    if (shortcuts == null) {
      if (myParent != null) {
        return getParentShortcuts(actionId)
      }
      else {
        return ourEmptyShortcutsArray
      }
    }
    return if (shortcuts.isEmpty()) ourEmptyShortcutsArray else shortcuts.toTypedArray()
  }

  fun getOwnShortcuts(actionId: String): Array<Shortcut>? {
    val own = myActionIdToListOfShortcuts[actionId] ?: return null
    return if (own.isEmpty()) ourEmptyShortcutsArray else own.toTypedArray()
  }

  private fun getBoundShortcuts(actionId: String): Array<Shortcut>? {
    val keymapManager = keymapManager
    val hasBoundedAction = keymapManager.boundActions.contains(actionId)
    if (hasBoundedAction) {
      return getOwnShortcuts(keymapManager.getActionBinding(actionId))
    }
    return null
  }

  private val keymapManager: KeymapManagerEx
    get() {
      var result = myKeymapManager
      if (result == null) {
        result = KeymapManagerEx.getInstanceEx()!!
        myKeymapManager = result
      }
      return result
    }

  open fun readExternal(keymapElement: Element, existingKeymaps: Array<Keymap>) {
    if (KEY_MAP != keymapElement.name) {
      throw InvalidDataException("unknown element: " + keymapElement)
    }

    if (keymapElement.getAttributeValue(VERSION_ATTRIBUTE) == null) {
      Converter01.convert(keymapElement)
    }

    val parentName = keymapElement.getAttributeValue(PARENT_ATTRIBUTE)
    if (parentName != null) {
      for (existingKeymap in existingKeymaps) {
        if (parentName == existingKeymap.name) {
          myParent = existingKeymap as KeymapImpl
          myCanModify = true
          break
        }
      }
    }

    name = keymapElement.getAttributeValue(NAME_ATTRIBUTE)

    val idToShortcuts = THashMap<String, MutableList<Shortcut>>()
    val skipInserts = SystemInfo.isMac && (ApplicationManager.getApplication() == null || !ApplicationManager.getApplication().isUnitTestMode)
    for (actionElement in keymapElement.children) {
      if (ACTION != actionElement.name) {
        throw InvalidDataException("unknown element: $actionElement; Keymap's name=$name")
      }

      val id = actionElement.getAttributeValue(ID_ATTRIBUTE) ?: throw InvalidDataException(
        "Attribute 'id' cannot be null; Keymap's name=" + name)

      idToShortcuts.put(id, SmartList<Shortcut>())
      for (shortcutElement in actionElement.children) {
        if (KEYBOARD_SHORTCUT == shortcutElement.name) {
          // Parse first keystroke

          val firstKeyStrokeStr = shortcutElement.getAttributeValue(FIRST_KEYSTROKE_ATTRIBUTE) ?: throw InvalidDataException(
            "Attribute '$FIRST_KEYSTROKE_ATTRIBUTE' cannot be null; Action's id=$id; Keymap's name=$name")
          if (skipInserts && firstKeyStrokeStr.contains("INSERT")) {
            continue
          }

          val firstKeyStroke = KeyStrokeAdapter.getKeyStroke(firstKeyStrokeStr) ?: // logged when parsed
                               continue

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

          val stroke = KeyStrokeAdapter.getKeyStroke(strokeText) ?: // logged when parsed
                       continue

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
      myActionIdToListOfShortcuts.put(id, OrderedSet<Shortcut>(2))
      for (shortcut in idToShortcuts.get(id)!!) {
        addShortcutSilently(id, shortcut, false)
      }
    }
  }

  override fun writeScheme(): Element {
    val keymapElement = Element(KEY_MAP)
    keymapElement.setAttribute(VERSION_ATTRIBUTE, Integer.toString(1))
    keymapElement.setAttribute(NAME_ATTRIBUTE, name)

    if (myParent != null) {
      keymapElement.setAttribute(PARENT_ATTRIBUTE, myParent!!.name)
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
      val shortcuts = getShortcuts(actionId)
      for (shortcut in shortcuts) {
        if (shortcut is KeyboardShortcut) {
          val element = Element(KEYBOARD_SHORTCUT)
          element.setAttribute(FIRST_KEYSTROKE_ATTRIBUTE, KeyStrokeAdapter.toString(shortcut.firstKeyStroke))
          shortcut.secondKeyStroke?.let {
            element.setAttribute(SECOND_KEYSTROKE_ATTRIBUTE, KeyStrokeAdapter.toString(it))
          }
          actionElement.addContent(element)
        }
        else if (shortcut is MouseShortcut) {
          val element = Element(MOUSE_SHORTCUT)
          element.setAttribute(KEYSTROKE_ATTRIBUTE, getMouseShortcutString(shortcut))
          actionElement.addContent(element)
        }
        else if (shortcut is KeyboardModifierGestureShortcut) {
          val element = Element(KEYBOARD_GESTURE_SHORTCUT)
          element.setAttribute(KEYBOARD_GESTURE_SHORTCUT, KeyStrokeAdapter.toString(shortcut.stroke))
          element.setAttribute(KEYBOARD_GESTURE_MODIFIER, shortcut.type.name)
          actionElement.addContent(element)
        }
        else {
          throw IllegalStateException("unknown shortcut class: " + shortcut)
        }
      }
      keymapElement.addContent(actionElement)
    }
  }

  /**
   * @return IDs of the action which are specified in the keymap. It doesn't
   * *         return IDs of action from parent keymap.
   */
  val ownActionIds: Array<String>
    get() = myActionIdToListOfShortcuts.keys.toTypedArray()

  fun clearOwnActionsIds() {
    myActionIdToListOfShortcuts.clear()
    cleanShortcutsCache()
  }

  fun hasOwnActionId(actionId: String): Boolean {
    return myActionIdToListOfShortcuts.containsKey(actionId)
  }

  fun clearOwnActionsId(actionId: String) {
    myActionIdToListOfShortcuts.remove(actionId)
    cleanShortcutsCache()
  }

  override fun getActionIds(): Array<String> {
    val ids = ContainerUtil.newLinkedHashSet<String>()
    if (myParent != null) {
      ContainerUtil.addAll<String, String, Set<String>>(ids, *parentActionIds)
    }
    Collections.addAll(ids, *ownActionIds)
    return ArrayUtil.toStringArray(ids)
  }

  protected val parentActionIds: Array<String>
    get() = myParent!!.actionIds

  override fun getConflicts(actionId: String, keyboardShortcut: KeyboardShortcut): Map<String, ArrayList<KeyboardShortcut>> {
    val result = HashMap<String, ArrayList<KeyboardShortcut>>()

    for (id in getActionIds(keyboardShortcut.firstKeyStroke)) {
      if (id == actionId) {
        continue
      }

      if (actionId.startsWith(EDITOR_ACTION_PREFIX) && id == "$" + actionId.substring(6)) {
        continue
      }
      if (StringUtil.startsWithChar(actionId, '$') && id == EDITOR_ACTION_PREFIX + actionId.substring(1)) {
        continue
      }

      val useShortcutOf = keymapManager.getActionBinding(id)
      if (useShortcutOf != null && useShortcutOf == actionId) {
        continue
      }

      val shortcuts = getShortcuts(id)
      for (shortcut1 in shortcuts) {
        if (shortcut1 !is KeyboardShortcut) {
          continue
        }

        if (shortcut1.firstKeyStroke != keyboardShortcut.firstKeyStroke) {
          continue
        }

        if (keyboardShortcut.secondKeyStroke != null &&
            shortcut1.secondKeyStroke != null &&
            keyboardShortcut.secondKeyStroke != shortcut1.secondKeyStroke) {
          continue
        }

        var list: ArrayList<KeyboardShortcut>? = result[id]
        if (list == null) {
          list = ArrayList<KeyboardShortcut>()
          result.put(id, list)
        }

        list.add(shortcut1)
      }
    }

    return result
  }

  override fun addShortcutChangeListener(listener: Keymap.Listener) {
    myListeners.add(listener)
  }

  override fun removeShortcutChangeListener(listener: Keymap.Listener) {
    myListeners.remove(listener)
  }

  private fun fireShortcutChanged(actionId: String) {
    for (listener in myListeners) {
      listener.onShortcutChanged(actionId)
    }
  }

  override fun toString(): String {
    return presentableName
  }
}

private fun sortInOrderOfRegistration(ids: Array<String>): Array<String> {
  Arrays.sort(ids, ActionManagerEx.getInstanceEx().registrationOrderComparator)
  return ids
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
    buffer.append(SHIFT)
    buffer.append(' ')
  }
  if (InputEvent.CTRL_DOWN_MASK and modifiers > 0) {
    buffer.append(CONTROL)
    buffer.append(' ')
  }
  if (InputEvent.META_DOWN_MASK and modifiers > 0) {
    buffer.append(META)
    buffer.append(' ')
  }
  if (InputEvent.ALT_DOWN_MASK and modifiers > 0) {
    buffer.append(ALT)
    buffer.append(' ')
  }
  if (InputEvent.ALT_GRAPH_DOWN_MASK and modifiers > 0) {
    buffer.append(ALT_GRAPH)
    buffer.append(' ')
  }

  // button
  buffer.append("button").append(shortcut.button).append(' ')

  if (shortcut.clickCount > 1) {
    buffer.append(DOUBLE_CLICK)
  }
  return buffer.toString().trim { it <= ' ' } // trim trailing space (if any)
}