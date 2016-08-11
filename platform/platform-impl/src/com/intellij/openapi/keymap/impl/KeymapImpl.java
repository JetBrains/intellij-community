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
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.options.ExternalizableSchemeAdapter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.*;

/**
 * @author Eugene Belyaev
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class KeymapImpl extends ExternalizableSchemeAdapter implements Keymap {
  @NonNls private static final String KEY_MAP = "keymap";
  @NonNls private static final String KEYBOARD_SHORTCUT = "keyboard-shortcut";
  @NonNls private static final String KEYBOARD_GESTURE_SHORTCUT = "keyboard-gesture-shortcut";
  @NonNls private static final String KEYBOARD_GESTURE_KEY = "keystroke";
  @NonNls private static final String KEYBOARD_GESTURE_MODIFIER = "modifier";
  @NonNls private static final String KEYSTROKE_ATTRIBUTE = "keystroke";
  @NonNls private static final String FIRST_KEYSTROKE_ATTRIBUTE = "first-keystroke";
  @NonNls private static final String SECOND_KEYSTROKE_ATTRIBUTE = "second-keystroke";
  @NonNls private static final String ACTION = "action";
  @NonNls private static final String VERSION_ATTRIBUTE = "version";
  @NonNls private static final String PARENT_ATTRIBUTE = "parent";
  @NonNls private static final String NAME_ATTRIBUTE = "name";
  @NonNls private static final String ID_ATTRIBUTE = "id";
  @NonNls private static final String MOUSE_SHORTCUT = "mouse-shortcut";
  @NonNls private static final String SHIFT = "shift";
  @NonNls private static final String CONTROL = "control";
  @NonNls private static final String META = "meta";
  @NonNls private static final String ALT = "alt";
  @NonNls private static final String ALT_GRAPH = "altGraph";
  @NonNls private static final String DOUBLE_CLICK = "doubleClick";
  @NonNls private static final String EDITOR_ACTION_PREFIX = "Editor";

  private KeymapImpl myParent;
  private boolean myCanModify = true;

  private final THashMap<String, OrderedSet<Shortcut>> myActionId2ListOfShortcuts = new THashMap<>();

  /**
   * Don't use this field directly! Use it only through <code>getKeystroke2ListOfIds</code>.
   */
  private Map<KeyStroke, List<String>> myKeystroke2ListOfIds = null;
  private Map<KeyboardModifierGestureShortcut, List<String>> myGesture2ListOfIds = null;

  /**
   * Don't use this field directly! Use it only through <code>getMouseShortcut2ListOfIds</code>.
   */
  private Map<MouseShortcut, List<String>> myMouseShortcut2ListOfIds = null;

  private static final Shortcut[] ourEmptyShortcutsArray = Shortcut.EMPTY_ARRAY;
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private KeymapManagerEx myKeymapManager;

  @Override
  public String getPresentableName() {
    return getName();
  }

  public KeymapImpl deriveKeymap(@NotNull String newName) {
    if (canModify()) {
      return copy();
    }
    else {
      KeymapImpl newKeymap = new KeymapImpl();
      newKeymap.myParent = this;
      newKeymap.setName(newName);
      newKeymap.myCanModify = canModify();
      return newKeymap;
    }
  }

  @NotNull
  public KeymapImpl copy() {
    return copyTo(new KeymapImpl());
  }

  @NotNull
  public KeymapImpl copyTo(@NotNull final KeymapImpl otherKeymap) {
    otherKeymap.myParent = myParent;
    otherKeymap.setName(getName());
    otherKeymap.myCanModify = canModify();

    otherKeymap.cleanShortcutsCache();

    otherKeymap.myActionId2ListOfShortcuts.clear();
    otherKeymap.myActionId2ListOfShortcuts.ensureCapacity(myActionId2ListOfShortcuts.size());
    myActionId2ListOfShortcuts.forEachEntry(new TObjectObjectProcedure<String, OrderedSet<Shortcut>>() {
      @Override
      public boolean execute(String actionId, OrderedSet<Shortcut> shortcuts) {
        otherKeymap.myActionId2ListOfShortcuts.put(actionId, new OrderedSet<>(shortcuts));
        return true;
      }
    });
    return otherKeymap;
  }

  public boolean equals(Object object) {
    if (!(object instanceof KeymapImpl)) return false;
    KeymapImpl secondKeymap = (KeymapImpl)object;
    if (!Comparing.equal(getName(), secondKeymap.getName())) return false;
    if (myCanModify != secondKeymap.myCanModify) return false;
    if (!Comparing.equal(myParent, secondKeymap.myParent)) return false;
    if (!Comparing.equal(myActionId2ListOfShortcuts, secondKeymap.myActionId2ListOfShortcuts)) return false;
    return true;
  }

  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public Keymap getParent() {
    return myParent;
  }

  @Override
  public boolean canModify() {
    return myCanModify;
  }

  public void setCanModify(boolean val) {
    myCanModify = val;
  }

  protected Shortcut[] getParentShortcuts(String actionId) {
    return myParent.getShortcuts(actionId);
  }

  @Override
  public void addShortcut(String actionId, Shortcut shortcut) {
    addShortcutSilently(actionId, shortcut, true);
    fireShortcutChanged(actionId);
  }

  private void addShortcutSilently(String actionId, Shortcut shortcut, final boolean checkParentShortcut) {
    OrderedSet<Shortcut> list = myActionId2ListOfShortcuts.get(actionId);
    if (list == null) {
      list = new OrderedSet<>();
      myActionId2ListOfShortcuts.put(actionId, list);
      Shortcut[] boundShortcuts = getBoundShortcuts(actionId);
      if (boundShortcuts != null) {
        ContainerUtil.addAll(list, boundShortcuts);
      }
      else if (myParent != null) {
        ContainerUtil.addAll(list, getParentShortcuts(actionId));
      }
    }
    list.add(shortcut);

    if (checkParentShortcut && myParent != null && areShortcutsEqual(getParentShortcuts(actionId), getShortcuts(actionId))) {
      myActionId2ListOfShortcuts.remove(actionId);
    }
    cleanShortcutsCache();
  }

  private void cleanShortcutsCache() {
    myKeystroke2ListOfIds = null;
    myMouseShortcut2ListOfIds = null;
  }

  @Override
  public void removeAllActionShortcuts(String actionId) {
    Shortcut[] allShortcuts = getShortcuts(actionId);
    for (Shortcut shortcut : allShortcuts) {
      removeShortcut(actionId, shortcut);
    }
  }

  @Override
  public void removeShortcut(String actionId, Shortcut toDelete) {
    OrderedSet<Shortcut> list = myActionId2ListOfShortcuts.get(actionId);
    if (list != null) {
      Iterator<Shortcut> it = list.iterator();
      while (it.hasNext()) {
        Shortcut each = it.next();
        if (toDelete.equals(each)) {
          it.remove();
          if ((myParent != null && areShortcutsEqual(getParentShortcuts(actionId), getShortcuts(actionId)))
              || (myParent == null && list.isEmpty())) {
            myActionId2ListOfShortcuts.remove(actionId);
          }
          break;
        }
      }
    }
    else {
      Shortcut[] inherited = getBoundShortcuts(actionId);
      if (inherited == null && myParent != null) {
        inherited = getParentShortcuts(actionId);
      }

      if (inherited != null) {
        boolean affected = false;
        OrderedSet<Shortcut> newShortcuts = new OrderedSet<>(inherited.length);
        for (Shortcut eachInherited : inherited) {
          if (toDelete.equals(eachInherited)) {
            // skip this one
            affected = true;
          }
          else {
            newShortcuts.add(eachInherited);
          }
        }
        if (affected) {
          myActionId2ListOfShortcuts.put(actionId, newShortcuts);
        }
      }
    }
    cleanShortcutsCache();
    fireShortcutChanged(actionId);
  }

  private Map<KeyStroke, List<String>> getKeystroke2ListOfIds() {
    if (myKeystroke2ListOfIds != null) return myKeystroke2ListOfIds;

    myKeystroke2ListOfIds = new THashMap<>();
    for (String id : ContainerUtil.concat(myActionId2ListOfShortcuts.keySet(), getKeymapManager().getBoundActions())) {
      addKeystrokesMap(id, myKeystroke2ListOfIds);
    }
    return myKeystroke2ListOfIds;
  }

  private Map<KeyboardModifierGestureShortcut, List<String>> getGesture2ListOfIds() {
    if (myGesture2ListOfIds == null) {
      myGesture2ListOfIds = new THashMap<>();
      fillShortcut2ListOfIds(myGesture2ListOfIds, KeyboardModifierGestureShortcut.class);
    }
    return myGesture2ListOfIds;
  }

  private <T extends Shortcut>void fillShortcut2ListOfIds(final Map<T,List<String>> map, final Class<T> shortcutClass) {
    for (String id : ContainerUtil.concat(myActionId2ListOfShortcuts.keySet(), getKeymapManager().getBoundActions())) {
      addAction2ShortcutsMap(id, map, shortcutClass);
    }
  }

  private Map<MouseShortcut, List<String>> getMouseShortcut2ListOfIds() {
    if (myMouseShortcut2ListOfIds == null) {
      myMouseShortcut2ListOfIds = new THashMap<>();

      fillShortcut2ListOfIds(myMouseShortcut2ListOfIds, MouseShortcut.class);
    }
    return myMouseShortcut2ListOfIds;
  }

  private <T extends Shortcut>void addAction2ShortcutsMap(final String actionId, final Map<T, List<String>> strokesMap, final Class<T> shortcutClass) {
    OrderedSet<Shortcut> listOfShortcuts = _getShortcuts(actionId);
    for (Shortcut shortcut : listOfShortcuts) {
      if (!shortcutClass.isAssignableFrom(shortcut.getClass())) {
        continue;
      }
      @SuppressWarnings({"unchecked"})
      T t = (T)shortcut;

      List<String> listOfIds = strokesMap.get(t);
      if (listOfIds == null) {
        listOfIds = new ArrayList<>();
        strokesMap.put(t, listOfIds);
      }

      // action may have more that 1 shortcut with same first keystroke
      if (!listOfIds.contains(actionId)) {
        listOfIds.add(actionId);
      }
    }
  }

  private void addKeystrokesMap(final String actionId, final Map<KeyStroke, List<String>> strokesMap) {
    OrderedSet<Shortcut> listOfShortcuts = _getShortcuts(actionId);
    for (Shortcut shortcut : listOfShortcuts) {
      if (!(shortcut instanceof KeyboardShortcut)) {
        continue;
      }
      KeyStroke firstKeyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
      List<String> listOfIds = strokesMap.get(firstKeyStroke);
      if (listOfIds == null) {
        listOfIds = new ArrayList<>();
        strokesMap.put(firstKeyStroke, listOfIds);
      }

      // action may have more that 1 shortcut with same first keystroke
      if (!listOfIds.contains(actionId)) {
        listOfIds.add(actionId);
      }
    }
  }

  private OrderedSet<Shortcut> _getShortcuts(final String actionId) {
    KeymapManagerEx keymapManager = getKeymapManager();
    OrderedSet<Shortcut> listOfShortcuts = myActionId2ListOfShortcuts.get(actionId);
    if (listOfShortcuts != null) {
      return listOfShortcuts;
    }
    else {
      listOfShortcuts = new OrderedSet<>();
    }

    final String actionBinding = keymapManager.getActionBinding(actionId);
    if (actionBinding != null) {
      listOfShortcuts.addAll(_getShortcuts(actionBinding));
    }

    return listOfShortcuts;
  }


  protected String[] getParentActionIds(KeyStroke firstKeyStroke) {
    return myParent.getActionIds(firstKeyStroke);
  }

  protected String[] getParentActionIds(KeyboardModifierGestureShortcut gesture) {
    return myParent.getActionIds(gesture);
  }

  private String[] getActionIds(KeyboardModifierGestureShortcut shortcut) {
    // first, get keystrokes from own map
    final Map<KeyboardModifierGestureShortcut, List<String>> map = getGesture2ListOfIds();
    List<String> list = new ArrayList<>();

    for (Map.Entry<KeyboardModifierGestureShortcut, List<String>> entry : map.entrySet()) {
      if (shortcut.startsWith(entry.getKey())) {
        list.addAll(entry.getValue());
      }
    }

    if (myParent != null) {
      String[] ids = getParentActionIds(shortcut);
      if (ids.length > 0) {
        for (String id : ids) {
          // add actions from parent keymap only if they are absent in this keymap
          if (!myActionId2ListOfShortcuts.containsKey(id)) {
            list.add(id);
          }
        }
      }
    }
    return sortInOrderOfRegistration(ArrayUtil.toStringArray(list));
  }

  @Override
  public String[] getActionIds(KeyStroke firstKeyStroke) {
    // first, get keystrokes from own map
    List<String> list = getKeystroke2ListOfIds().get(firstKeyStroke);
    if (myParent != null) {
      String[] ids = getParentActionIds(firstKeyStroke);
      if (ids.length > 0) {
        boolean originalListInstance = true;
        for (String id : ids) {
          // add actions from parent keymap only if they are absent in this keymap
          // do not add parent bind actions, if bind-on action is overwritten in the child
          if (!myActionId2ListOfShortcuts.containsKey(id) &&
              !myActionId2ListOfShortcuts.containsKey(getActionBinding(id))) {
            if (list == null) {
              list = new ArrayList<>();
              originalListInstance = false;
            }
            else if (originalListInstance) {
              list = new ArrayList<>(list);
              originalListInstance = false;
            }
            if (!list.contains(id)) list.add(id);
          }
        }
      }
    }
    if (list == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    return sortInOrderOfRegistration(ArrayUtil.toStringArray(list));
  }

  @Override
  public String[] getActionIds(KeyStroke firstKeyStroke, KeyStroke secondKeyStroke) {
    String[] ids = getActionIds(firstKeyStroke);
    ArrayList<String> actualBindings = new ArrayList<>();
    for (String id : ids) {
      Shortcut[] shortcuts = getShortcuts(id);
      for (Shortcut shortcut : shortcuts) {
        if (!(shortcut instanceof KeyboardShortcut)) {
          continue;
        }
        if (Comparing.equal(firstKeyStroke, ((KeyboardShortcut)shortcut).getFirstKeyStroke()) &&
            Comparing.equal(secondKeyStroke, ((KeyboardShortcut)shortcut).getSecondKeyStroke())) {
          actualBindings.add(id);
          break;
        }
      }
    }
    return ArrayUtil.toStringArray(actualBindings);
  }

  @Override
  public String[] getActionIds(final Shortcut shortcut) {
    if (shortcut instanceof KeyboardShortcut) {
      final KeyboardShortcut kb = (KeyboardShortcut)shortcut;
      final KeyStroke first = kb.getFirstKeyStroke();
      final KeyStroke second = kb.getSecondKeyStroke();
      return second != null ? getActionIds(first, second) : getActionIds(first);
    }
    else if (shortcut instanceof MouseShortcut) {
      return getActionIds((MouseShortcut)shortcut);
    }
    else if (shortcut instanceof KeyboardModifierGestureShortcut) {
      return getActionIds((KeyboardModifierGestureShortcut)shortcut);
    }
    else {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  protected String[] getParentActionIds(MouseShortcut shortcut) {
    return myParent.getActionIds(shortcut);
  }


  @Override
  public String[] getActionIds(MouseShortcut shortcut) {
    // first, get shortcuts from own map
    List<String> list = getMouseShortcut2ListOfIds().get(shortcut);
    if (myParent != null) {
      String[] ids = getParentActionIds(shortcut);
      if (ids.length > 0) {
        boolean originalListInstance = true;
        for (String id : ids) {
          // add actions from parent keymap only if they are absent in this keymap
          if (!myActionId2ListOfShortcuts.containsKey(id)) {
            if (list == null) {
              list = new ArrayList<>();
              originalListInstance = false;
            }
            else if (originalListInstance) {
              list = new ArrayList<>(list);
            }
            list.add(id);
          }
        }
      }
    }
    if (list == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return sortInOrderOfRegistration(ArrayUtil.toStringArray(list));
  }

  private static String[] sortInOrderOfRegistration(String[] ids) {
    Arrays.sort(ids, ActionManagerEx.getInstanceEx().getRegistrationOrderComparator());
    return ids;
  }

  public boolean isActionBound(@NotNull final String actionId) {
    return getKeymapManager().getBoundActions().contains(actionId);
  }

  @Nullable
  public String getActionBinding(@NotNull final String actionId) {
    return getKeymapManager().getActionBinding(actionId);
  }

  @NotNull
  @Override
  public Shortcut[] getShortcuts(String actionId) {
    OrderedSet<Shortcut> shortcuts = myActionId2ListOfShortcuts.get(actionId);

    if (shortcuts == null) {
      Shortcut[] boundShortcuts = getBoundShortcuts(actionId);
      if (boundShortcuts!= null) return boundShortcuts;
    }

    if (shortcuts == null) {
      if (myParent != null) {
        return getParentShortcuts(actionId);
      }
      else {
        return ourEmptyShortcutsArray;
      }
    }
    return shortcuts.isEmpty() ? ourEmptyShortcutsArray : shortcuts.toArray(new Shortcut[shortcuts.size()]);
  }

  @Nullable
  private Shortcut[] getOwnShortcuts(String actionId) {
    OrderedSet<Shortcut> own = myActionId2ListOfShortcuts.get(actionId);
    if (own == null) return null;
    return own.isEmpty() ? ourEmptyShortcutsArray : own.toArray(new Shortcut[own.size()]);
  }

  @Nullable
  private Shortcut[] getBoundShortcuts(String actionId) {
    KeymapManagerEx keymapManager = getKeymapManager();
    boolean hasBoundedAction = keymapManager.getBoundActions().contains(actionId);
    if (hasBoundedAction) {
      return getOwnShortcuts(keymapManager.getActionBinding(actionId));
    }
    return null;
  }

  private KeymapManagerEx getKeymapManager() {
    if (myKeymapManager == null) {
      myKeymapManager = KeymapManagerEx.getInstanceEx();
    }
    return myKeymapManager;
  }

  /**
   * @param keymapElement element which corresponds to "keymap" tag.
   */
  public void readExternal(Element keymapElement, Keymap[] existingKeymaps) throws InvalidDataException {
    // Check and convert parameters
    if (!KEY_MAP.equals(keymapElement.getName())) {
      throw new InvalidDataException("unknown element: " + keymapElement);
    }
    if (keymapElement.getAttributeValue(VERSION_ATTRIBUTE) == null) {
      Converter01.convert(keymapElement);
    }
    //
    String parentName = keymapElement.getAttributeValue(PARENT_ATTRIBUTE);
    if (parentName != null) {
      for (Keymap existingKeymap : existingKeymaps) {
        if (parentName.equals(existingKeymap.getName())) {
          myParent = (KeymapImpl)existingKeymap;
          myCanModify = true;
          break;
        }
      }
    }
    setName(keymapElement.getAttributeValue(NAME_ATTRIBUTE));

    Map<String, ArrayList<Shortcut>> id2shortcuts = new HashMap<>();
    final boolean skipInserts = SystemInfo.isMac && !ApplicationManager.getApplication().isUnitTestMode();
    for (final Object o : keymapElement.getChildren()) {
      Element actionElement = (Element)o;
      if (ACTION.equals(actionElement.getName())) {
        String id = actionElement.getAttributeValue(ID_ATTRIBUTE);
        if (id == null) {
          throw new InvalidDataException("Attribute 'id' cannot be null; Keymap's name=" + getName());
        }
        id2shortcuts.put(id, new ArrayList<>(1));
        for (final Object o1 : actionElement.getChildren()) {
          Element shortcutElement = (Element)o1;
          if (KEYBOARD_SHORTCUT.equals(shortcutElement.getName())) {

            // Parse first keystroke

            String firstKeyStrokeStr = shortcutElement.getAttributeValue(FIRST_KEYSTROKE_ATTRIBUTE);
            if (firstKeyStrokeStr == null) {
              throw new InvalidDataException("Attribute '" + FIRST_KEYSTROKE_ATTRIBUTE + "' cannot be null; Action's id=" + id + "; Keymap's name=" + getName());
            }
            if (skipInserts && firstKeyStrokeStr.contains("INSERT")) continue;

            KeyStroke firstKeyStroke = KeyStrokeAdapter.getKeyStroke(firstKeyStrokeStr);
            if (firstKeyStroke == null) continue; // logged when parsed

            // Parse second keystroke

            KeyStroke secondKeyStroke = null;
            String secondKeyStrokeStr = shortcutElement.getAttributeValue(SECOND_KEYSTROKE_ATTRIBUTE);
            if (secondKeyStrokeStr != null) {
              secondKeyStroke = KeyStrokeAdapter.getKeyStroke(secondKeyStrokeStr);
              if (secondKeyStroke == null) continue; // logged when parsed
            }
            Shortcut shortcut = new KeyboardShortcut(firstKeyStroke, secondKeyStroke);
            ArrayList<Shortcut> shortcuts = id2shortcuts.get(id);
            shortcuts.add(shortcut);
          }
          else if (KEYBOARD_GESTURE_SHORTCUT.equals(shortcutElement.getName())) {
            final String strokeText = shortcutElement.getAttributeValue(KEYBOARD_GESTURE_KEY);
            if (strokeText == null) {
              throw new InvalidDataException("Attribute '" + KEYBOARD_GESTURE_KEY + "' cannot be null; Action's id=" + id + "; Keymap's name=" + getName());
            }
            KeyStroke stroke = KeyStrokeAdapter.getKeyStroke(strokeText);
            if (stroke == null) continue; // logged when parsed

            final String modifierText = shortcutElement.getAttributeValue(KEYBOARD_GESTURE_MODIFIER);
            KeyboardGestureAction.ModifierType modifier = null;
            if (KeyboardGestureAction.ModifierType.dblClick.toString().equalsIgnoreCase(modifierText)) {
              modifier = KeyboardGestureAction.ModifierType.dblClick;
            }
            else if (KeyboardGestureAction.ModifierType.hold.toString().equalsIgnoreCase(modifierText)) {
              modifier = KeyboardGestureAction.ModifierType.hold;
            }

            if (modifier == null) {
              throw new InvalidDataException("Wrong modifier=" + modifierText + " action id=" + id + " keymap=" + getName());
            }

            Shortcut shortcut = KeyboardModifierGestureShortcut.newInstance(modifier, stroke);
            final ArrayList<Shortcut> shortcuts = id2shortcuts.get(id);
            shortcuts.add(shortcut);
          }
          else if (MOUSE_SHORTCUT.equals(shortcutElement.getName())) {
            String keystrokeString = shortcutElement.getAttributeValue(KEYSTROKE_ATTRIBUTE);
            if (keystrokeString == null) {
              throw new InvalidDataException("Attribute 'keystroke' cannot be null; Action's id=" + id + "; Keymap's name=" + getName());
            }

            try {
              MouseShortcut shortcut = KeymapUtil.parseMouseShortcut(keystrokeString);
              ArrayList<Shortcut> shortcuts = id2shortcuts.get(id);
              shortcuts.add(shortcut);
            }
            catch (InvalidDataException exc) {
              throw new InvalidDataException(
                "Wrong mouse-shortcut: '" + keystrokeString + "'; Action's id=" + id + "; Keymap's name=" + getName());
            }
          }
          else {
            throw new InvalidDataException("unknown element: " + shortcutElement + "; Keymap's name=" + getName());
          }
        }
      }
      else {
        throw new InvalidDataException("unknown element: " + actionElement + "; Keymap's name=" + getName());
      }
    }
    // Add read shortcuts
    for (String id : id2shortcuts.keySet()) {
      myActionId2ListOfShortcuts.put(id, new OrderedSet<>(2)); // It's a trick! After that parent's shortcuts are not added to the keymap
      ArrayList<Shortcut> shortcuts = id2shortcuts.get(id);
      for (Shortcut shortcut : shortcuts) {
        addShortcutSilently(id, shortcut, false);
      }
    }
  }

  public Element writeExternal() {
    Element keymapElement = new Element(KEY_MAP);
    keymapElement.setAttribute(VERSION_ATTRIBUTE, Integer.toString(1));
    keymapElement.setAttribute(NAME_ATTRIBUTE, getName());

    if (myParent != null) {
      keymapElement.setAttribute(PARENT_ATTRIBUTE, myParent.getName());
    }
    writeOwnActionIds(keymapElement);
    return keymapElement;
  }

  private void writeOwnActionIds(final Element keymapElement) {
    String[] ownActionIds = getOwnActionIds();
    Arrays.sort(ownActionIds);
    for (String actionId : ownActionIds) {
      Element actionElement = new Element(ACTION);
      actionElement.setAttribute(ID_ATTRIBUTE, actionId);
      // Save keyboard shortcuts
      Shortcut[] shortcuts = getShortcuts(actionId);
      for (Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
          Element element = new Element(KEYBOARD_SHORTCUT);
          element.setAttribute(FIRST_KEYSTROKE_ATTRIBUTE, getKeyShortcutString(keyboardShortcut.getFirstKeyStroke()));
          if (keyboardShortcut.getSecondKeyStroke() != null) {
            element.setAttribute(SECOND_KEYSTROKE_ATTRIBUTE, getKeyShortcutString(keyboardShortcut.getSecondKeyStroke()));
          }
          actionElement.addContent(element);
        }
        else if (shortcut instanceof MouseShortcut) {
          MouseShortcut mouseShortcut = (MouseShortcut)shortcut;
          Element element = new Element(MOUSE_SHORTCUT);
          element.setAttribute(KEYSTROKE_ATTRIBUTE, getMouseShortcutString(mouseShortcut));
          actionElement.addContent(element);
        }
        else if (shortcut instanceof KeyboardModifierGestureShortcut) {
          final KeyboardModifierGestureShortcut gesture = (KeyboardModifierGestureShortcut)shortcut;
          final Element element = new Element(KEYBOARD_GESTURE_SHORTCUT);
          element.setAttribute(KEYBOARD_GESTURE_SHORTCUT, getKeyShortcutString(gesture.getStroke()));
          element.setAttribute(KEYBOARD_GESTURE_MODIFIER, gesture.getType().name());
          actionElement.addContent(element);
        }
        else {
          throw new IllegalStateException("unknown shortcut class: " + shortcut);
        }
      }
      keymapElement.addContent(actionElement);
    }
  }

  private static boolean areShortcutsEqual(Shortcut[] shortcuts1, Shortcut[] shortcuts2) {
    if (shortcuts1.length != shortcuts2.length) {
      return false;
    }
    for (Shortcut shortcut : shortcuts1) {
      Shortcut parentShortcutEqual = null;
      for (Shortcut parentShortcut : shortcuts2) {
        if (shortcut.equals(parentShortcut)) {
          parentShortcutEqual = parentShortcut;
          break;
        }
      }
      if (parentShortcutEqual == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return string representation of passed keystroke.
   */
  public static String getKeyShortcutString(KeyStroke keyStroke) {
    return KeyStrokeAdapter.toString(keyStroke);
  }

  /**
   * @return string representation of passed mouse shortcut. This method should
   *         be used only for serializing of the <code>MouseShortcut</code>
   */
  private static String getMouseShortcutString(MouseShortcut shortcut) {

    if (Registry.is("ide.mac.forceTouch") && shortcut instanceof PressureShortcut) {
      return "Force touch";
    }

    StringBuilder buffer = new StringBuilder();

    // modifiers

    int modifiers = shortcut.getModifiers();
    if ((InputEvent.SHIFT_DOWN_MASK & modifiers) > 0) {
      buffer.append(SHIFT);
      buffer.append(' ');
    }
    if ((InputEvent.CTRL_DOWN_MASK & modifiers) > 0) {
      buffer.append(CONTROL);
      buffer.append(' ');
    }
    if ((InputEvent.META_DOWN_MASK & modifiers) > 0) {
      buffer.append(META);
      buffer.append(' ');
    }
    if ((InputEvent.ALT_DOWN_MASK & modifiers) > 0) {
      buffer.append(ALT);
      buffer.append(' ');
    }
    if ((InputEvent.ALT_GRAPH_DOWN_MASK & modifiers) > 0) {
      buffer.append(ALT_GRAPH);
      buffer.append(' ');
    }

    // button

    buffer.append("button").append(shortcut.getButton()).append(' ');

    if (shortcut.getClickCount() > 1) {
      buffer.append(DOUBLE_CLICK);
    }
    return buffer.toString().trim(); // trim trailing space (if any)
  }

  /**
   * @return IDs of the action which are specified in the keymap. It doesn't
   *         return IDs of action from parent keymap.
   */
  public String[] getOwnActionIds() {
    return myActionId2ListOfShortcuts.keySet().toArray(new String[myActionId2ListOfShortcuts.size()]);
  }

  public void clearOwnActionsIds() {
    myActionId2ListOfShortcuts.clear();
    cleanShortcutsCache();
  }

  public boolean hasOwnActionId(@NotNull String actionId) {
    return myActionId2ListOfShortcuts.containsKey(actionId);
  }

  public void clearOwnActionsId(String actionId) {
    myActionId2ListOfShortcuts.remove(actionId);
    cleanShortcutsCache();
  }

  @Override
  public String[] getActionIds() {
    Set<String> ids = ContainerUtil.newLinkedHashSet();
    if (myParent != null) {
      ContainerUtil.addAll(ids, getParentActionIds());
    }
    Collections.addAll(ids, getOwnActionIds());
    return ArrayUtil.toStringArray(ids);
  }

  protected String[] getParentActionIds() {
    return myParent.getActionIds();
  }


  @Override
  public Map<String, ArrayList<KeyboardShortcut>> getConflicts(String actionId, KeyboardShortcut keyboardShortcut) {
    HashMap<String, ArrayList<KeyboardShortcut>> result = new HashMap<>();

    String[] actionIds = getActionIds(keyboardShortcut.getFirstKeyStroke());
    for (String id : actionIds) {
      if (id.equals(actionId)) {
        continue;
      }

      if (actionId.startsWith(EDITOR_ACTION_PREFIX) && id.equals("$" + actionId.substring(6))) {
        continue;
      }
      if (StringUtil.startsWithChar(actionId, '$') && id.equals(EDITOR_ACTION_PREFIX + actionId.substring(1))) {
        continue;
      }

      final String useShortcutOf = myKeymapManager.getActionBinding(id);
      if (useShortcutOf != null && useShortcutOf.equals(actionId)) {
        continue;
      }

      Shortcut[] shortcuts = getShortcuts(id);
      for (Shortcut shortcut1 : shortcuts) {
        if (!(shortcut1 instanceof KeyboardShortcut)) {
          continue;
        }

        KeyboardShortcut shortcut = (KeyboardShortcut)shortcut1;

        if (!shortcut.getFirstKeyStroke().equals(keyboardShortcut.getFirstKeyStroke())) {
          continue;
        }

        if (keyboardShortcut.getSecondKeyStroke() != null &&
            shortcut.getSecondKeyStroke() != null &&
            !keyboardShortcut.getSecondKeyStroke().equals(shortcut.getSecondKeyStroke())) {
          continue;
        }

        ArrayList<KeyboardShortcut> list = result.get(id);
        if (list == null) {
          list = new ArrayList<>();
          result.put(id, list);
        }

        list.add(shortcut);
      }
    }

    return result;
  }

  @Override
  public void addShortcutChangeListener(Listener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeShortcutChangeListener(Listener listener) {
    myListeners.remove(listener);
  }

  private void fireShortcutChanged(String actionId) {
    for (Listener listener : myListeners) {
      listener.onShortcutChanged(actionId);
    }
  }

  @Override
  public String[] getAbbreviations() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void addAbbreviation(String actionId, String abbreviation) {

  }

  @Override
  public void removeAbbreviation(String actionId, String abbreviation) {

  }

  @NotNull
  @Override
  public String toString() {
    return getPresentableName();
  }
}
