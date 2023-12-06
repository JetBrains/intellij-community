// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplacePutWithAssignment")

package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.configurationStore.SerializableScheme
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.ExternalizableSchemeAdapter
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.util.ArrayUtilRt
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jdom.Element
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.swing.KeyStroke
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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

fun KeymapImpl(name: String, dataHolder: SchemeDataHolder<KeymapImpl>): KeymapImpl {
  val result = KeymapImpl(dataHolder)
  result.name = name
  result.schemeState = SchemeState.UNCHANGED
  return result
}

open class KeymapImpl @JvmOverloads constructor(@field:Volatile private var dataHolder: SchemeDataHolder<KeymapImpl>? = null)
  : ExternalizableSchemeAdapter(), Keymap, SerializableScheme {
  @Volatile
  private var parent: KeymapImpl? = null
  private var unknownParentName: String? = null

  open var canModify: Boolean = true

  @JvmField
  internal var schemeState: SchemeState? = null

  override fun getSchemeState(): SchemeState? = schemeState

  private val actionIdToShortcuts = SynchronizedClearableLazy<MutableMap<String, List<Shortcut>>> {
    val actionManager = ActionManagerEx.getInstanceEx()
    val actionIdToShortcuts = ConcurrentHashMap<String, List<Shortcut>>()

    dataHolder?.let {
      dataHolder = null
      readExternal(keymapElement = it.read(),
                   actionBinding = { id -> actionManager.getActionBinding(id) },
                   actionIdToShortcuts = actionIdToShortcuts)
    }

    // only after pendingInit we can use the name (it is set by readExternal)
    if (actionManager is ActionManagerImpl) {
      doInitShortcuts(operations = actionManager.getKeymapPendingOperations(name),
                      actionIdToShortcuts = actionIdToShortcuts,
                      actionBinding = { actionManager.getActionBinding(it) })
    }
    actionIdToShortcuts
  }

  private val keymapManager by lazy { KeymapManagerEx.getInstanceEx()!! }

  /**
   * @return IDs of the action which are specified in the keymap. It doesn't return IDs of action from the parent keymap.
   */
  val ownActionIds: Array<String>
    get() = actionIdToShortcuts.value.keys.toTypedArray()

  private fun <T> cachedShortcuts(mapper: (Shortcut) -> T?): ReadWriteProperty<Any?, Map<T, MutableList<String>>> {
    return object : ReadWriteProperty<Any?, Map<T, MutableList<String>>> {
      private var cache: Map<T, MutableList<String>>? = null

      override fun getValue(thisRef: Any?, property: KProperty<*>): Map<T, MutableList<String>> {
        return cache ?: mapShortcuts(mapper).also { cache = it }
      }

      override fun setValue(thisRef: Any?, property: KProperty<*>, value: Map<T, MutableList<String>>) {
        cache = null
      }

      private fun mapShortcuts(mapper: (Shortcut) -> T?): Map<T, MutableList<String>> {
        val actionManager = ActionManagerEx.getInstanceEx()

        fun addActionToShortcutMap(actionId: String, map: MutableMap<T, MutableList<String>>) {
          for (shortcut in getOwnOrBoundShortcuts(actionId, actionManager)) {
            mapper(shortcut)?.let {
              val ids = map.computeIfAbsent(it) { ArrayList() }
              if (!ids.contains(actionId)) {
                ids.add(actionId)
              }
            }
          }
        }

        val map = HashMap<T, MutableList<String>>()
        actionIdToShortcuts.value.keys.forEach { addActionToShortcutMap(it, map) }
        actionManager.getBoundActions().forEach { addActionToShortcutMap(it, map) }
        return map
      }
    }
  }

  // Accesses to these caches are non-synchronized, so must be performed
  // from EDT only (where all the modifications are currently done)
  private var keystrokeToActionIds: Map<KeyStroke, MutableList<String>> by cachedShortcuts { (it as? KeyboardShortcut)?.firstKeyStroke }
  private var mouseShortcutToActionIds: Map<MouseShortcut, MutableList<String>> by cachedShortcuts { it as? MouseShortcut }
  private var gestureToActionIds: Map<KeyboardModifierGestureShortcut, MutableList<String>> by cachedShortcuts { it as? KeyboardModifierGestureShortcut }

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

  fun copy(): KeymapImpl {
    val dataHolder = dataHolder
    if (dataHolder != null) {
      return KeymapImpl(name, dataHolder)
    }

    val otherKeymap = KeymapImpl(dataHolder = null)
    otherKeymap.actionIdToShortcuts.value = ConcurrentHashMap(actionIdToShortcuts.value)
    otherKeymap.parent = parent
    otherKeymap.name = name
    otherKeymap.canModify = canModify()
    return otherKeymap
  }

  fun copyTo(otherKeymap: KeymapImpl): KeymapImpl {
    otherKeymap.cleanShortcutsCache()

    otherKeymap.actionIdToShortcuts.value = ConcurrentHashMap(actionIdToShortcuts.value)
    // after actionIdToShortcuts (on first access, we lazily read itself)
    otherKeymap.parent = parent
    otherKeymap.name = name
    otherKeymap.canModify = canModify()
    return otherKeymap
  }

  override fun getParent(): KeymapImpl? = parent

  final override fun canModify(): Boolean = canModify

  final override fun addShortcut(actionId: String, shortcut: Shortcut) {
    addShortcut(actionId = actionId,
                shortcut = shortcut,
                fromSettings = false,
                actionIdToShortcuts = actionIdToShortcuts.value,
                actionBinding = ActionManagerEx.getInstanceEx()::getActionBinding)
  }

  internal fun addShortcutFromSettings(actionId: String, shortcut: Shortcut) {
    addShortcut(actionId = actionId,
                shortcut = shortcut,
                fromSettings = true,
                actionIdToShortcuts = actionIdToShortcuts.value,
                actionBinding = { ActionManagerEx.getInstanceEx().getActionBinding(it) })
  }

  private fun getParentShortcuts(actionId: String, actionBinding: (String) -> String?): List<Shortcut> {
    val parent = parent ?: return java.util.List.of()
    return parent.getShortcutList(actionId, parent.actionIdToShortcuts.value, actionBinding).map { convertShortcut(it) }
  }

  internal fun addShortcut(actionId: String,
                           shortcut: Shortcut,
                           fromSettings: Boolean,
                           actionIdToShortcuts: MutableMap<String, List<Shortcut>>,
                           actionBinding: (String) -> String?) {
    val boundShortcuts = actionBinding(actionId)?.let { actionIdToShortcuts.get(it) }
    actionIdToShortcuts.compute(actionId) { id, list ->
      var result = list ?: boundShortcuts ?: getParentShortcuts(id, actionBinding)
      if (result.isEmpty()) {
        result = java.util.List.of(shortcut)
      }
      else if (!result.contains(shortcut)) {
        result = when (result.size) {
          1 -> java.util.List.of(result.get(0), shortcut)
          2 -> java.util.List.of(result.get(0), result.get(1), shortcut)
          3 -> java.util.List.of(result.get(0), result.get(1), result.get(2), shortcut)
          4 -> java.util.List.of(result.get(0), result.get(1), result.get(2), result.get(3), shortcut)
          else -> result + shortcut
        }
      }
      if (result.areShortcutsEqualToParent(id, actionBinding)) null else result
    }

    cleanShortcutsCache()
    fireShortcutChanged(actionId, fromSettings)
  }

  private fun cleanShortcutsCache() {
    keystrokeToActionIds = java.util.Map.of()
    mouseShortcutToActionIds = java.util.Map.of()
    gestureToActionIds = java.util.Map.of()
    schemeState = SchemeState.POSSIBLY_CHANGED
  }

  final override fun removeAllActionShortcuts(actionId: String) {
    return removeAllActionShortcuts(actionId, actionIdToShortcuts.value, ActionManagerEx.getInstanceEx()::getActionBinding)
  }

  private fun removeAllActionShortcuts(actionId: String,
                                       actionIdToShortcuts: MutableMap<String, List<Shortcut>>,
                                       actionBinding: (String) -> String?) {
    for (shortcut in getShortcutList(actionId = actionId, actionIdToShortcuts = actionIdToShortcuts, actionBinding = actionBinding)) {
      removeShortcut(actionId = actionId,
                     toDelete = shortcut,
                     fromSettings = false,
                     actionBinding = actionBinding,
                     actionIdToShortcuts = actionIdToShortcuts)
    }
  }

  final override fun removeShortcut(actionId: String, toDelete: Shortcut) {
    removeShortcut(actionId = actionId,
                   toDelete = toDelete,
                   fromSettings = false,
                   actionIdToShortcuts = actionIdToShortcuts.value,
                   actionBinding = ActionManagerEx.getInstanceEx()::getActionBinding)
  }

  internal fun initShortcuts(operations: List<KeymapShortcutOperation>, actionBinding: (String) -> String?) {
    doInitShortcuts(operations = operations, actionIdToShortcuts = actionIdToShortcuts.value, actionBinding = actionBinding)
  }

  private fun doInitShortcuts(operations: List<KeymapShortcutOperation>,
                              actionIdToShortcuts: MutableMap<String, List<Shortcut>>,
                              actionBinding: (String) -> String?) {
    for (operation in operations) {
      when (operation) {
        is RemoveShortcutOperation -> {
          removeShortcut(actionId = operation.actionId,
                         toDelete = operation.shortcut,
                         fromSettings = false,
                         actionIdToShortcuts = actionIdToShortcuts,
                         actionBinding = actionBinding)
        }
        is RemoveAllShortcutsOperation -> {
          removeAllActionShortcuts(actionId = operation.actionId, actionIdToShortcuts = actionIdToShortcuts, actionBinding = actionBinding)
        }
        is AddShortcutOperation -> {
          addShortcut(actionId = operation.actionId,
                      shortcut = operation.shortcut,
                      fromSettings = false,
                      actionIdToShortcuts = actionIdToShortcuts,
                      actionBinding = actionBinding)
        }
      }
    }
  }

  internal fun removeShortcutFromSettings(actionId: String, toDelete: Shortcut) {
    removeShortcut(actionId = actionId,
                   toDelete = toDelete,
                   fromSettings = true,
                   actionIdToShortcuts = actionIdToShortcuts.value,
                   actionBinding = { ActionManagerEx.getInstanceEx().getActionBinding(it) })
  }

  private fun removeShortcut(actionId: String,
                             toDelete: Shortcut,
                             fromSettings: Boolean,
                             actionIdToShortcuts: MutableMap<String, List<Shortcut>>,
                             actionBinding: (String) -> String?) {
    val fromBinding = actionBinding(actionId)?.let { actionIdToShortcuts.get(it) }
    actionIdToShortcuts.compute(actionId) { id, list ->
      when {
        list == null -> {
          val inherited = fromBinding ?: getParentShortcuts(id,  actionBinding)
          if (inherited.contains(toDelete)) inherited - toDelete else null
        }
        !list.contains(toDelete) -> list
        parent == null -> if (list.size == 1) null else java.util.List.copyOf(list - toDelete)
        else -> {
          val result = list - toDelete
          if (result.areShortcutsEqualToParent(id, actionBinding)) null else java.util.List.copyOf(result)
        }
      }
    }

    cleanShortcutsCache()
    fireShortcutChanged(actionId = actionId, fromSettings = fromSettings)
  }

  private fun List<Shortcut>.areShortcutsEqualToParent(actionId: String, actionBinding: (String) -> String?): Boolean {
    if (parent == null) {
      return false
    }

    val shortcuts2 = getParentShortcuts(actionId, actionBinding)
    return areShortcutsEqual(shortcuts1 = this, shortcuts2 = shortcuts2)
  }

  private fun getOwnOrBoundShortcuts(actionId: String, actionManager: ActionManagerEx): List<Shortcut> {
    actionIdToShortcuts.value.get(actionId)?.let {
      return it
    }

    return getOwnOrBoundShortcuts(actionManager.getActionBinding(actionId) ?: return java.util.List.of(), actionManager)
  }

  private fun getActionIds(shortcut: KeyboardModifierGestureShortcut): List<String> {
    // first, get keystrokes from our own map
    val list = ArrayList<String>()
    for ((key, value) in gestureToActionIds) {
      if (shortcut.startsWith(key)) {
        list.addAll(value)
      }
    }

    if (parent != null) {
      val ids = parent!!.getActionIds(shortcut)
      if (ids.isNotEmpty()) {
        for (id in ids) {
          // add actions from the parent keymap only if they are absent in this keymap
          if (!actionIdToShortcuts.value.containsKey(id)) {
            list.add(id)
          }
        }
      }
    }
    sortInRegistrationOrder(list)
    return list
  }

  final override fun getActionIds(firstKeyStroke: KeyStroke): Array<String> {
    return ArrayUtilRt.toStringArray(getActionIds(firstKeyStroke, { it.keystrokeToActionIds }, KeymapImpl::convertKeyStroke))
  }

  final override fun getActionIds(firstKeyStroke: KeyStroke, secondKeyStroke: KeyStroke?): Array<String> {
    return ArrayUtilRt.toStringArray(getActionIdList())
  }

  private fun getActionIdList(firstKeyStroke: KeyStroke, secondKeyStroke: KeyStroke?): List<String> {
    val ids = getActionIds(firstKeyStroke)
    var actualBindings: MutableList<String>? = null
    val actionBinding = ActionManagerEx.getInstanceEx()::getActionBinding
    val actionIdToShortcuts = actionIdToShortcuts.value
    for (id in ids) {
      for (shortcut in getShortcutList(actionId = id, actionIdToShortcuts = actionIdToShortcuts, actionBinding = actionBinding)) {
        if (shortcut !is KeyboardShortcut) {
          continue
        }

        if (firstKeyStroke == shortcut.firstKeyStroke && secondKeyStroke == shortcut.secondKeyStroke) {
          if (actualBindings == null) {
            actualBindings = ArrayList()
          }
          actualBindings.add(id)
          break
        }
      }
    }
    return actualBindings ?: java.util.List.of()
  }

  @Suppress("OVERRIDE_DEPRECATION")
  final override fun getActionIds(shortcut: Shortcut): Array<String> {
    return ArrayUtilRt.toStringArray(getActionIdList(shortcut))
  }

  final override fun getActionIdList(shortcut: Shortcut): List<String> {
    return when (shortcut) {
      is KeyboardShortcut -> {
        val first = shortcut.firstKeyStroke
        val second = shortcut.secondKeyStroke
        if (second == null) {
          getActionIds(shortcut = first, shortcutToActionIds = { it.keystrokeToActionIds }, convertShortcut = KeymapImpl::convertKeyStroke)
        }
        else {
          getActionIdList(first, second)
        }
      }
      is MouseShortcut -> getActionIds(shortcut)
      is KeyboardModifierGestureShortcut -> getActionIds(shortcut)
      else -> java.util.List.of()
    }
  }

  final override fun hasActionId(actionId: String, shortcut: MouseShortcut): Boolean {
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

  final override fun getActionIds(shortcut: MouseShortcut): List<String> {
    return getActionIds(shortcut = shortcut,
                        shortcutToActionIds = { it.mouseShortcutToActionIds },
                        convertShortcut = KeymapImpl::convertMouseShortcut)
  }

  @RequiresEdt
  private fun <T> getActionIds(shortcut: T,
                               shortcutToActionIds: (keymap: KeymapImpl) -> Map<T, MutableList<String>>,
                               convertShortcut: (keymap: KeymapImpl, shortcut: T) -> T): List<String> {
    // first, get keystrokes from our own map
    var list = shortcutToActionIds(this).get(shortcut)
    val parentIds = parent?.getActionIds(shortcut = convertShortcut(this, shortcut),
                                         shortcutToActionIds = shortcutToActionIds,
                                         convertShortcut = convertShortcut) ?: java.util.List.of()
    var isOriginalListInstance = list != null
    for (id in parentIds) {
      // add actions from the parent keymap only if they are absent in this keymap
      // do not add parent bind actions, if bind-on action is overwritten in the child
      if (actionIdToShortcuts.value.containsKey(id)) {
        continue
      }

      val key = ActionManagerEx.getInstanceEx().getActionBinding(id)
      if (key != null && actionIdToShortcuts.value.containsKey(key)) {
        continue
      }

      if (list == null) {
        list = ArrayList()
      }
      else if (isOriginalListInstance) {
        list = ArrayList(list)
        isOriginalListInstance = false
      }

      if (!list.contains(id)) {
        list.add(id)
      }
    }
    sortInRegistrationOrder(list ?: return java.util.List.of())
    return list
  }

  override fun getShortcuts(actionId: String?): Array<Shortcut> {
    val result = getShortcutList(actionId = actionId,
                                 actionIdToShortcuts = actionIdToShortcuts.value,
                                 actionBinding = {
                                   (ActionManager.getInstance() as? ActionManagerEx)?.getActionBinding(it)
                                 })
    return if (result.isEmpty()) Shortcut.EMPTY_ARRAY else result.toTypedArray()
  }

  private fun getShortcutList(actionId: String?,
                              actionIdToShortcuts: MutableMap<String, List<Shortcut>>,
                              actionBinding: (String) -> String?): List<Shortcut> {
    if (actionId == null) {
      return java.util.List.of()
    }

    // it is critical to use convertShortcut - otherwise MacOSDefaultKeymap doesn't convert shortcuts
    // todo why not convert on add? why we don't need to convert our own shortcuts?
    return actionIdToShortcuts.get(actionId)
           ?: actionBinding(actionId)?.let { actionIdToShortcuts.get(it) }
           ?: getParentShortcuts(actionId, actionBinding)
  }

  fun hasShortcutDefined(actionId: String): Boolean {
    return actionIdToShortcuts.value.get(actionId) != null || parent?.hasShortcutDefined(actionId) == true
  }

  // you must clear `actionIdToShortcuts` before calling
  protected open fun readExternal(keymapElement: Element,
                                  actionIdToShortcuts: MutableMap<String, List<Shortcut>>,
                                  actionBinding: (String) -> String?) {
    if (KEY_MAP != keymapElement.name) {
      throw InvalidDataException("unknown element: $keymapElement")
    }

    name = keymapElement.getAttributeValue(NAME_ATTRIBUTE)!!

    unknownParentName = null

    keymapElement.getAttributeValue(PARENT_ATTRIBUTE)?.let { parentSchemeName ->
      var parentScheme = findParentScheme(parentSchemeName)
      if (parentScheme == null && parentSchemeName == "Default for Mac OS X") {
        // https://youtrack.jetbrains.com/issue/RUBY-17767#comment=27-1374197
        parentScheme = findParentScheme("Mac OS X")
      }

      if (parentScheme == null) {
        logger<KeymapImpl>().warn("Cannot find parent scheme $parentSchemeName for scheme $name")
        unknownParentName = parentSchemeName
        notifyAboutMissingKeymap(parentSchemeName,
                                 IdeBundle.message("notification.content.cannot.find.parent.keymap", parentSchemeName, name), true)
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

      val id = actionElement.getAttributeValue(ID_ATTRIBUTE)
               ?: throw InvalidDataException("Attribute 'id' cannot be null; Keymap's name=$name")
      actionIds.add(id)
      val shortcuts = ArrayList<Shortcut>()
      for (shortcutElement in actionElement.children) {
        when (shortcutElement.name) {
          KEYBOARD_SHORTCUT -> {
            // Parse first keystroke
            val firstKeyStrokeStr = shortcutElement.getAttributeValue(FIRST_KEYSTROKE_ATTRIBUTE)
                                    ?: throw InvalidDataException(
                                      "Attribute '$FIRST_KEYSTROKE_ATTRIBUTE' cannot be null; Action's id=$id; Keymap's name=$name")
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
          KEYBOARD_GESTURE_SHORTCUT -> {
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
          MOUSE_SHORTCUT -> {
            val keystrokeString = shortcutElement.getAttributeValue(KEYSTROKE_ATTRIBUTE)
                                  ?: throw InvalidDataException(
                                    "Attribute 'keystroke' cannot be null; Action's id=$id; Keymap's name=$name")

            try {
              shortcuts.add(KeymapUtil.parseMouseShortcut(keystrokeString))
            }
            catch (e: InvalidDataException) {
              throw InvalidDataException("Wrong mouse-shortcut: '$keystrokeString'; Action's id=$id; Keymap's name=$name")
            }
          }
          else -> {
            throw InvalidDataException("unknown element: $shortcutElement; Keymap's name=$name")
          }
        }
      }
      // creating the list even when there are no shortcuts
      // (an empty element means that an action overrides a parent one to clear shortcuts)
      actionIdToShortcuts.put(id, java.util.List.copyOf(shortcuts))
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
    keymapElement.setAttribute(VERSION_ATTRIBUTE, "1")
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
      val shortcuts = actionIdToShortcuts.value.get(actionId) ?: continue
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
    actionIdToShortcuts.valueIfInitialized?.clear()
    cleanShortcutsCache()
  }

  fun hasOwnActionId(actionId: String): Boolean = actionIdToShortcuts.value.containsKey(actionId)

  fun clearOwnActionsId(actionId: String) {
    actionIdToShortcuts.value.remove(actionId)
    cleanShortcutsCache()
  }

  override fun getActionIds(): Array<String> = ArrayUtilRt.toStringArray(actionIdList)

  override fun getActionIdList(): Set<String> {
    val ids = LinkedHashSet<String>()
    ids.addAll(actionIdToShortcuts.value.keys)
    var parent = parent
    while (parent != null) {
      ids.addAll(parent.actionIdToShortcuts.value.keys)
      parent = parent.parent
    }
    return ids
  }

  override fun getConflicts(actionId: String, keyboardShortcut: KeyboardShortcut): Map<String, MutableList<KeyboardShortcut>> {
    val result = HashMap<String, MutableList<KeyboardShortcut>>()
    val actionIdToShortcuts = actionIdToShortcuts.value
    for (id in getActionIds(keyboardShortcut.firstKeyStroke)) {
      if (id == actionId || (actionId.startsWith("Editor") && id == "$${actionId.substring(6)}")) {
        continue
      }

      val actionManager = ActionManagerEx.getInstanceEx()
      val useShortcutOf = actionManager.getActionBinding(id)
      if (useShortcutOf != null && useShortcutOf == actionId) {
        continue
      }

      for (shortcut1 in getShortcutList(actionId = id,
                                        actionIdToShortcuts = actionIdToShortcuts,
                                        actionBinding = { actionManager.getActionBinding(it) })) {
        if (shortcut1 !is KeyboardShortcut || shortcut1.firstKeyStroke != keyboardShortcut.firstKeyStroke) {
          continue
        }

        if (keyboardShortcut.secondKeyStroke != null &&
            shortcut1.secondKeyStroke != null &&
            keyboardShortcut.secondKeyStroke != shortcut1.secondKeyStroke) {
          continue
        }

        result.computeIfAbsent(id) { ArrayList() }.add(shortcut1)
      }
    }

    return result
  }

  protected open fun convertKeyStroke(keyStroke: KeyStroke): KeyStroke = keyStroke

  protected open fun convertMouseShortcut(shortcut: MouseShortcut): MouseShortcut = shortcut

  protected open fun convertShortcut(shortcut: Shortcut): Shortcut = shortcut

  private fun fireShortcutChanged(actionId: String, fromSettings: Boolean) {
    ApplicationManager.getApplication().messageBus.syncPublisher(KeymapManagerListener.TOPIC).shortcutChanged(this, actionId, fromSettings)
  }

  override fun toString(): String = presentableName

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is KeymapImpl) return false
    if (name != other.name) return false
    if (canModify != other.canModify) return false
    if (parent != other.parent) return false
    if (actionIdToShortcuts.value != other.actionIdToShortcuts.value) return false
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

@Suppress("SpellCheckingInspection")
private const val macOSKeymap = "com.intellij.plugins.macoskeymap"
@Suppress("SpellCheckingInspection")
private const val gnomeKeymap = "com.intellij.plugins.gnomekeymap"
@Suppress("SpellCheckingInspection")
private const val kdeKeymap = "com.intellij.plugins.kdekeymap"
@Suppress("SpellCheckingInspection")
private const val xwinKeymap = "com.intellij.plugins.xwinkeymap"
@Suppress("SpellCheckingInspection")
private const val eclipseKeymap = "com.intellij.plugins.eclipsekeymap"
@Suppress("SpellCheckingInspection")
private const val emacsKeymap = "com.intellij.plugins.emacskeymap"
@Suppress("SpellCheckingInspection")
private const val netbeansKeymap = "com.intellij.plugins.netbeanskeymap"
@Suppress("SpellCheckingInspection")
private const val qtcreatorKeymap = "com.intellij.plugins.qtcreatorkeymap"
@Suppress("SpellCheckingInspection")
private const val resharperKeymap = "com.intellij.plugins.resharperkeymap"
@Suppress("SpellCheckingInspection")
private const val sublimeKeymap = "com.intellij.plugins.sublimetextkeymap"
@Suppress("SpellCheckingInspection")
private const val visualStudioKeymap = "com.intellij.plugins.visualstudiokeymap"
private const val visualStudio2022Keymap = "com.intellij.plugins.visualstudio2022keymap"
@Suppress("SpellCheckingInspection")
private const val xcodeKeymap = "com.intellij.plugins.xcodekeymap"
@Suppress("SpellCheckingInspection")
private const val visualAssistKeymap = "com.intellij.plugins.visualassistkeymap"
@Suppress("SpellCheckingInspection")
private const val riderKeymap = "com.intellij.plugins.riderkeymap"
@Suppress("SpellCheckingInspection")
private const val vsCodeKeymap = "com.intellij.plugins.vscodekeymap"
@Suppress("SpellCheckingInspection")
private const val vsForMacKeymap = "com.intellij.plugins.vsformackeymap"

internal fun notifyAboutMissingKeymap(keymapName: String, @NlsContexts.NotificationContent message: String, isParent: Boolean) {
  val connection = ApplicationManager.getApplication().messageBus.connect()
  connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
    @Suppress("removal", "OVERRIDE_DEPRECATION")
    override fun projectOpened(project: Project) {
      connection.disconnect()

      ApplicationManager.getApplication().invokeLater(
        {
          // TODO remove when PluginAdvertiser implements that
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
            "QtCreator",
            "QtCreator OSX" -> qtcreatorKeymap
            "ReSharper",
            "ReSharper OSX" -> resharperKeymap
            "Sublime Text",
            "Sublime Text (Mac OS X)" -> sublimeKeymap
            "Visual Studio",
            "Visual Studio OSX" -> visualStudioKeymap
            "Visual Studio 2022" -> visualStudio2022Keymap
            "Visual Assist",
            "Visual Assist OSX" -> visualAssistKeymap
            "Xcode" -> xcodeKeymap
            "Visual Studio for Mac" -> vsForMacKeymap
            "Rider",
            "Rider OSX" -> riderKeymap
            "VSCode",
            "VSCode OSX" -> vsCodeKeymap
            else -> null
          }

          val action: AnAction = when (pluginId) {
            null -> object : NotificationAction(IdeBundle.message("action.text.search.for.keymap", keymapName)) {
              override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                //TODO enableSearch("$keymapName /tag:Keymap")?.run()
                ShowSettingsUtil.getInstance().showSettingsDialog(e.project, PluginManagerConfigurable::class.java)
              }
            }
            else -> object : NotificationAction(IdeBundle.message("action.text.install.keymap", keymapName)) {
              override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                val connect = ApplicationManager.getApplication().messageBus.connect()
                connect.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
                  override fun keymapAdded(keymap: Keymap) {
                    ApplicationManager.getApplication().invokeLater {
                      if (keymap.name == keymapName) {
                        connect.disconnect()
                        val successMessage = if (isParent) IdeBundle.message("notification.content.keymap.successfully.installed",
                                                                             keymapName)
                        else {
                          KeymapManagerEx.getInstanceEx().activeKeymap = keymap
                          IdeBundle.message("notification.content.keymap.successfully.activated", keymapName)
                        }
                        Notification("KeymapInstalled", successMessage,
                                     NotificationType.INFORMATION).notify(e.project)
                      }
                    }
                  }
                })

                val plugins = mutableSetOf(PluginId.getId(pluginId))
                when (pluginId) {
                  gnomeKeymap, kdeKeymap -> plugins += PluginId.getId(xwinKeymap)
                  resharperKeymap -> plugins += PluginId.getId(visualStudioKeymap)
                  visualAssistKeymap -> plugins += PluginId.getId(visualStudioKeymap)
                  visualStudio2022Keymap -> plugins += PluginId.getId(visualStudioKeymap)
                  xcodeKeymap, vsForMacKeymap -> plugins += PluginId.getId(macOSKeymap)
                }
                installAndEnable(project, plugins) { }

                notification.expire()
              }
            }
          }

          Notification("KeymapMissing", IdeBundle.message("notification.group.missing.keymap"),
                       message, NotificationType.ERROR)
            .addAction(action)
            .notify(project)
        },
        ModalityState.nonModal())
    }
  })
}
