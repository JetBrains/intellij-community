/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.keymap;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.MacOSDefaultKeymap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public abstract class KeymapsTestCaseBase extends PlatformTestCase {
  private static final boolean OUTPUT_TEST_DATA = false;

  protected abstract void collectKnownDuplicates(Map<String, Map<String, List<String>>> result);

  protected abstract void collectUnknownActions(Set<String> result);

  protected static void appendKnownDuplicates(Map<String, Map<String, List<String>>> result, Map<String, String[][]> duplicates) {
    for (Map.Entry<String, String[][]> eachKeymap : duplicates.entrySet()) {
      String keymapName = eachKeymap.getKey();

      Map<String, List<String>> mapping = result.get(keymapName);
      if (mapping == null) {
        result.put(keymapName, mapping = new LinkedHashMap<>());
      }

      for (String[] shortcuts : eachKeymap.getValue()) {
        TestCase.assertTrue("known duplicates list entry for '" + keymapName + "' must not contain empty array",
                            shortcuts.length > 0);
        TestCase.assertTrue("known duplicates list entry for '" + keymapName + "', shortcut '" + shortcuts[0] +
                            "' must contain at least two conflicting action ids",
                            shortcuts.length > 2);
        mapping.put(shortcuts[0], ContainerUtil.newArrayList(shortcuts, 1, shortcuts.length));
      }
    }
  }


  private Map<String, Map<String, List<String>>> getKnownDuplicates() {
    Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
    collectKnownDuplicates(result);
    return result;
  }

  public void testDuplicateShortcuts() {
    StringBuilder failMessage = new StringBuilder();
    Map<String, Map<String, List<String>>> knownDuplicates = getKnownDuplicates();

    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getSchemeManager().getAllSchemes()) {
      String failure = checkDuplicatesInKeymap((KeymapImpl)keymap, knownDuplicates);
      if (failMessage.length() > 0) {
        failMessage.append("\n");
      }
      failMessage.append(failure);
    }
    if (failMessage.length() > 0) {
      TestCase.fail(failMessage +
                    "\n" +
                    "Please specify 'use-shortcut-of' attribute for your action if it is similar to another action (but it won't appear in Settings/Keymap),\n" +
                    "reassign shortcut or, if absolutely must, modify the 'known duplicates list'");
    }
  }

  @NotNull
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String checkDuplicatesInKeymap(@NotNull KeymapImpl keymap,
                                                @NotNull Map<String, Map<String, List<String>>> allKnownDuplicates) {
    Set<String> aids = keymap.getActionIdList();
    removeBoundActionIds(aids);

    Set<Shortcut> shortcuts = new THashSet<>();

    nextId:
    for (String id : aids) {
      Map<String, List<String>> knownDuplicates = allKnownDuplicates.get(keymap.getName());
      if (knownDuplicates != null) {
        for (List<String> actionsMapping : knownDuplicates.values()) {
          if (actionsMapping.contains(id)) {
            continue nextId;
          }
        }
      }

      for (Shortcut shortcut : keymap.getShortcuts(id)) {
        if (shortcut instanceof KeyboardShortcut) {
          shortcuts.add(shortcut);
        }
      }
    }
    List<Shortcut> sorted = new ArrayList<>(shortcuts);
    Collections.sort(sorted, Comparator.comparing(KeymapsTestCaseBase::getText));

    if (OUTPUT_TEST_DATA) {
      System.out.println("put(\"" + keymap.getName() + "\", new String[][] {");
    }
    else {
      System.out.println(keymap.getName());
    }
    StringBuilder failMessage = new StringBuilder();
    for (Shortcut shortcut : sorted) {
      if (!(shortcut instanceof KeyboardShortcut)) {
        continue;
      }

      Set<String> ids = new THashSet<>(Arrays.asList(keymap.getActionIds(shortcut)));
      removeBoundActionIds(ids);
      if (ids.size() == 1) {
        continue;
      }

      Keymap parent = keymap.getParent();
      if (parent != null) {
        // ignore duplicates from default keymap
        boolean differFromParent = false;
        for (String id : ids) {
          Shortcut[] here = keymap.getShortcuts(id);
          Shortcut[] there = parent.getShortcuts(id);
          if (keymap.getName().startsWith("Mac")) {
            convertMac(there);
          }

          if (!new THashSet<>(Arrays.asList(here)).equals(new THashSet<>(Arrays.asList(there)))) {
            differFromParent = true;
            break;
          }
        }
        if (!differFromParent) continue;
      }

      String def = "{ "
                   + "\"" + getText(shortcut) + "\","
                   + StringUtil.repeatSymbol(' ', 25 - getText(shortcut).length())
                   + StringUtil.join(ids, StringUtil.QUOTER, ", ")
                   + "},";
      if (OUTPUT_TEST_DATA) {
        System.out.println(def);
      }
      else {
        if (failMessage.length() == 0) {
          failMessage.append("Shortcut '").append(getText(shortcut)).append("' conflicts found in keymap '")
            .append(keymap.getName()).append("':\n");
        }
        failMessage.append(def).append("\n");
      }
    }
    if (OUTPUT_TEST_DATA) {
      System.out.println("});");
    }
    return failMessage.toString();
  }

  private static void removeBoundActionIds(@NotNull Set<String> aids) {
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    // explicitly bound to another action
    for (Iterator<String> it = aids.iterator(); it.hasNext(); ) {
      String id = it.next();
      String sourceId = keymapManager.getActionBinding(id);
      if (sourceId != null) {
        it.remove();
      }
    }
  }

  public void testValidActionIds() {
    THashSet<String> unknownActions = new THashSet<>();
    collectUnknownActions(unknownActions);

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    Map<String, List<String>> missingActions = new FactoryMap<String, List<String>>() {
      @Override
      protected Map<String, List<String>> createMap() {
        return new LinkedHashMap<>();
      }

      @Nullable
      @Override
      protected List<String> create(String key) {
        return new ArrayList<>();
      }
    };
    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      List<String> ids = new ArrayList<>(keymap.getActionIdList());
      ids.sort(null);
      assertThat(ids).isEqualTo(new ArrayList<>(new LinkedHashSet<>(ids)));
      for (String cid : ids) {
        if (unknownActions.contains(cid)) continue;
        AnAction action = ActionManager.getInstance().getAction(cid);
        if (action == null) {
          if (OUTPUT_TEST_DATA) {
            System.out.print("\"" + cid + "\", ");
          }
          else {
            missingActions.get(keymap.getName()).add(cid);
          }
        }
      }
    }

    List<String> reappearedAction = new ArrayList<>();
    for (String id : unknownActions) {
      AnAction action = ActionManager.getInstance().getAction(id);
      if (action != null) {
        reappearedAction.add(id);
      }
    }

    if (!missingActions.isEmpty() || !reappearedAction.isEmpty()) {
      StringBuilder message = new StringBuilder();
      if (!missingActions.isEmpty()) {
        for (Map.Entry<String, List<String>> keymapAndActions : missingActions.entrySet()) {
          message.append("Unknown actions in keymap ").append(keymapAndActions.getKey()).append(", add them to unknown actions list:\n");
          for (String action : keymapAndActions.getValue()) {
            message.append("\"").append(action).append("\",").append("\n");
          }
        }
      }
      if (!reappearedAction.isEmpty()) {
        message.append("The following actions have reappeared, remove them from unknown action list:\n");
        for (String action : reappearedAction) {
          message.append(action).append("\n");
        }
      }
      fail("\n" + message);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void testIdsListIsConsistent() {
    Map<String, Map<String, List<String>>> duplicates = getKnownDuplicates();

    Set<String> allMaps = new THashSet<>(ContainerUtil.map(KeymapManagerEx.getInstanceEx().getAllKeymaps(), keymap -> keymap.getName()));
    assertThat(ContainerUtil.subtract(allMaps, duplicates.keySet()))
      .overridingErrorMessage("Modify 'known duplicates list' test data. Keymaps were added: %s",
                              ContainerUtil.subtract(allMaps, duplicates.keySet()))
      .isEmpty();

    assertThat(ContainerUtil.subtract(duplicates.keySet(), allMaps))
      .overridingErrorMessage("Modify 'known duplicates list' test data. Keymaps were removed: %s",
                              ContainerUtil.subtract(duplicates.keySet(), allMaps))
      .isEmpty();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    MultiMap<Keymap, Shortcut> reassignedShortcuts = MultiMap.createLinked();
    for (String name : duplicates.keySet()) {
      Keymap keymap = KeymapManagerEx.getInstanceEx().getKeymap(name);
      assertThat(keymap).overridingErrorMessage("KeyMap %s not found", name).isNotNull();
      Map<String, List<String>> duplicateIdsList = duplicates.get(name);
      Set<String> mentionedShortcuts = new THashSet<>();
      for (Map.Entry<String, List<String>> shortcutMappings : duplicateIdsList.entrySet()) {

        String shortcutString = shortcutMappings.getKey();
        if (!mentionedShortcuts.add(shortcutString)) {
          TestCase.fail("Shortcut '" + shortcutString + "' duplicate in keymap '" + keymap + "'. Please modify 'known duplicates list'");
        }
        Shortcut shortcut = parse(shortcutString);
        Set<String> actualShortcuts = new THashSet<>(Arrays.asList(keymap.getActionIds(shortcut)));

        removeBoundActionIds(actualShortcuts);

        Set<String> expectedSc = new THashSet<>(shortcutMappings.getValue());
        for (String s : actualShortcuts) {
          if (!expectedSc.contains(s)) {
            reassignedShortcuts.putValue(keymap, shortcut);
          }
        }
        for (String s : expectedSc) {
          if (!actualShortcuts.contains(s)) {
            System.out.println("Expected action '" + s + "' does not reassign shortcut " + getText(shortcut) + " in keymap " + keymap + " or is not registered");
          }
        }
      }
    }
    if (!reassignedShortcuts.isEmpty()) {
      StringBuilder message = new StringBuilder();
      for (Map.Entry<Keymap, Collection<Shortcut>> keymapToShortcuts : reassignedShortcuts.entrySet()) {
        Keymap keymap = keymapToShortcuts.getKey();
        message
          .append("The following shortcuts was reassigned in keymap ").append(keymap.getName())
          .append(". Please modify known duplicates list:\n");
        for (Shortcut eachShortcut : keymapToShortcuts.getValue()) {
          message.append(" { ").append(StringUtil.wrapWithDoubleQuote(getText(eachShortcut))).append(",\t")
            .append(StringUtil.join(keymap.getActionIds(eachShortcut), s -> StringUtil.wrapWithDoubleQuote(s), ", "))
            .append("},\n");
        }
      }
      TestCase.fail("\n" + message.toString());
    }
  }

  private static Shortcut parse(String s) {
    String[] sc = s.split(",");
    KeyStroke fst = ActionManagerEx.getKeyStroke(sc[0]);
    assert fst != null : s;
    KeyStroke snd = null;
    if (sc.length == 2) {
      snd = ActionManagerEx.getKeyStroke(sc[1]);
    }
    return new KeyboardShortcut(fst, snd);
  }

  @NotNull
  private static String getText(@NotNull Shortcut shortcut) {
    if (shortcut instanceof KeyboardShortcut) {
      KeyStroke fst = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
      String s = getText(fst);

      KeyStroke snd = ((KeyboardShortcut)shortcut).getSecondKeyStroke();
      if (snd != null) {
        s += "," + getText(snd);
      }
      return s;
    }
    return KeymapUtil.getShortcutText(shortcut);
  }

  private static String getText(KeyStroke fst) {
    String text = KeyStrokeAdapter.toString(fst);
    int offset = text.lastIndexOf(' ');
    if (offset == -1) offset = 0;
    return text.substring(0, offset) + text.substring(offset).toUpperCase(Locale.ENGLISH);
  }

  private static void convertMac(@NotNull Shortcut[] there) {
    for (int i = 0; i < there.length; i++) {
      there[i] = MacOSDefaultKeymap.convertShortcutFromParent(there[i]);
    }
  }

  private static final Set<String> LINUX_KEYMAPS = ContainerUtil.newHashSet("Default for XWin", "Default for GNOME", "Default for KDE");

  public void testLinuxShortcuts() {
    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      if (LINUX_KEYMAPS.contains(keymap.getName())) {
        checkLinuxKeymap(keymap);
      }
    }
  }

  private static void checkLinuxKeymap(final Keymap keymap) {
    for (String actionId : keymap.getActionIdList()) {
      for (Shortcut shortcut : keymap.getShortcuts(actionId)) {
        if (shortcut instanceof KeyboardShortcut) {
          checkCtrlAltFn(keymap, shortcut, ((KeyboardShortcut)shortcut).getFirstKeyStroke());
          checkCtrlAltFn(keymap, shortcut, ((KeyboardShortcut)shortcut).getSecondKeyStroke());
        }
      }
    }
  }

  private static void checkCtrlAltFn(final Keymap keymap, final Shortcut shortcut, final KeyStroke stroke) {
    if (stroke != null) {
      final int modifiers = stroke.getModifiers();
      final int keyCode = stroke.getKeyCode();
      if (KeyEvent.VK_F1 <= keyCode && keyCode <= KeyEvent.VK_F12 &&
          (modifiers & InputEvent.CTRL_MASK) != 0 && (modifiers & InputEvent.ALT_MASK) != 0 && (modifiers & InputEvent.SHIFT_MASK) == 0) {
        final String message = "Invalid shortcut '" + shortcut + "' for action(s) " + Arrays.asList(keymap.getActionIds(shortcut)) +
                               " in keymap '" + keymap.getName() + "' " +
                               "(Ctrl-Alt-Fn shortcuts switch Linux virtual terminals (causes newbie panic), " +
                               "so either assign another shortcut, or remove it; see Keymap_XWin.xml for reference).";
        TestCase.fail(message);
      }
    }
  }
}
