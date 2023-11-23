// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.testFramework.LightPlatformTestCase;
import one.util.streamex.StreamEx;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public class KeymapTest extends LightPlatformTestCase {
  private static final String ACTION_1 = "ACTION_1";
  private static final String ACTION_2 = "ACTION_2";
  private static final String ACTION_NON_EXISTENT = "NON_EXISTENT";

  KeyboardShortcut shortcut1 = new KeyboardShortcut(KeyStroke.getKeyStroke('1'), null);
  KeyboardShortcut shortcut2 = new KeyboardShortcut(KeyStroke.getKeyStroke('2'), null);
  KeyboardShortcut shortcutA = new KeyboardShortcut(KeyStroke.getKeyStroke('a'), null);
  KeyboardShortcut shortcutB = new KeyboardShortcut(KeyStroke.getKeyStroke('b'), null);

  private KeymapImpl myParent;
  private KeymapImpl myChild;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myParent = new KeymapImpl();
    myParent.setName("Parent");
    myParent.setCanModify(false);

    myParent.addShortcut(ACTION_1, shortcut1);
    myParent.addShortcut(ACTION_2, shortcut2);

    myChild = myParent.deriveKeymap("Child");
    myChild.setCanModify(false);
    assertThat(myParent).isSameAs(myChild.getParent());

    myChild.addShortcut(ACTION_1, shortcutA);
  }

  public void testParentAndChildShortcuts() {
    assertTrue(myParent.hasOwnActionId(ACTION_1));
    assertTrue(myParent.hasOwnActionId(ACTION_2));
    assertFalse(myParent.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(myParent.getShortcuts(ACTION_1), shortcut1);
    assertSameElements(myParent.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(myParent.getShortcuts(ACTION_NON_EXISTENT));

    assertSameElements(myParent.getActionIdList(shortcut1), ACTION_1);
    assertSameElements(myParent.getActionIdList(shortcut2), ACTION_2);
    assertSameElements(myParent.getActionIdList(shortcutA));
    assertSameElements(myParent.getActionIdList(shortcutB));

    assertTrue(myChild.hasOwnActionId(ACTION_1));
    assertFalse(myChild.hasOwnActionId(ACTION_2));
    assertFalse(myChild.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1, shortcutA);
    assertSameElements(myChild.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(myChild.getShortcuts(ACTION_NON_EXISTENT));

    assertSameElements(myChild.getActionIdList(shortcut1), ACTION_1);
    assertSameElements(myChild.getActionIdList(shortcut2), ACTION_2);
    assertSameElements(myChild.getActionIdList(shortcutA), ACTION_1);
    assertSameElements(myChild.getActionIdList(shortcutB));
  }

  public void testRemovingShortcutsFromParentAndChild() {
    myParent.removeShortcut(ACTION_1, shortcut1);

    assertFalse(myParent.hasOwnActionId(ACTION_1));
    assertTrue(myParent.hasOwnActionId(ACTION_2));
    assertFalse(myParent.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(myParent.getShortcuts(ACTION_1));
    assertSameElements(myParent.getShortcuts(ACTION_2), shortcut2);

    assertSameElements(myParent.getActionIdList(shortcut1));
    assertSameElements(myParent.getActionIdList(shortcut2), ACTION_2);

    // child keymap still lists inherited shortcut
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1, shortcutA);

    myChild.removeShortcut(ACTION_1, shortcut1);
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcutA);
    assertSameElements(myChild.getActionIdList(shortcut1));
    assertSameElements(myChild.getActionIdList(shortcutA), ACTION_1);
    assertTrue(myChild.hasOwnActionId(ACTION_1));

    myChild.removeShortcut(ACTION_1, shortcutA);
    assertSameElements(myChild.getShortcuts(ACTION_1));
    assertSameElements(myChild.getActionIdList(shortcutA));
    assertFalse(myChild.hasOwnActionId(ACTION_1)); // since equal to parent list

    myChild.removeShortcut(ACTION_2, shortcut2);
    assertSameElements(myChild.getShortcuts(ACTION_2));
    assertSameElements(myChild.getActionIdList(shortcut2));
    assertTrue(myChild.hasOwnActionId(ACTION_2)); // since different from parent list
  }

  public void testRemovingShortcutFromChildWhenInheritedDontChangeTheListIfShortcutIsAbsent() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    myParent.addShortcut(ACTION_1, shortcut1);

    assertThat(myParent.hasOwnActionId(ACTION_1)).isTrue();
    assertThat(myChild.hasOwnActionId(ACTION_1)).isFalse();
    assertThat(myChild.getShortcuts(ACTION_1)).containsExactly(shortcut1);

    // should not have any effect
    myChild.removeShortcut(ACTION_1, shortcutA);

    assertThat(myParent.hasOwnActionId(ACTION_1)).isTrue();
    assertThat(myChild.hasOwnActionId(ACTION_1)).isFalse();
    assertThat(myChild.getShortcuts(ACTION_1)).containsExactly(shortcut1);

    myParent.addShortcut(ACTION_2, shortcut2);
    myParent.addShortcut(ACTION_2, shortcutA);
    myParent.addShortcut(ACTION_2, shortcutB);

    myChild.removeShortcut(ACTION_2, shortcutA);
    assertThat(myChild.getShortcuts(ACTION_2)).containsExactly(shortcut2, shortcutB);
  }

  public void testRemovingShortcutFirst() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    myParent.addShortcut(ACTION_2, shortcut2);
    myParent.addShortcut(ACTION_2, shortcutA);
    myParent.addShortcut(ACTION_2, shortcutB);

    myChild.removeShortcut(ACTION_2, shortcut2);
    assertThat(myChild.getShortcuts(ACTION_2)).containsExactly(shortcutA, shortcutB);
  }

  public void testRemoveMouseShortcut() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    MouseShortcut mouseShortcut = new MouseShortcut(1, InputEvent.BUTTON2_MASK, 1);
    myParent.addShortcut(ACTION_2, mouseShortcut);
    assertThat(myChild.getActionIdList(mouseShortcut)).containsExactly(ACTION_2);
    myChild.removeShortcut(ACTION_2, mouseShortcut);
    assertThat(myChild.getActionIdList(mouseShortcut)).isEmpty();
  }
  
  // decided to not change order and keep old behavior
  //public void testChangeMouseShortcut() throws Exception {
  //  myParent.clearOwnActionsIds();
  //  myChild.clearOwnActionsIds();
  //
  //  ActionManager actionManager = ActionManager.getInstance();
  //  actionManager.registerAction(ACTION_2, new EmptyAction());
  //  actionManager.registerAction(ACTION_1, new EmptyAction());
  //  try {
  //    MouseShortcut mouseShortcut = new MouseShortcut(1, InputEvent.BUTTON2_MASK, 1);
  //    myParent.addShortcut(ACTION_2, mouseShortcut);
  //    assertThat(myChild.getActionIdList(mouseShortcut)).containsExactly(ACTION_2);
  //
  //    Keymap grandChild = myChild.deriveKeymap("GrandChild");
  //    myChild.addShortcut(ACTION_2, mouseShortcut);
  //
  //    grandChild.addShortcut(ACTION_1, mouseShortcut);
  //    assertThat(grandChild.getActionIdList(mouseShortcut)).containsExactly(ACTION_1, ACTION_2);
  //  }
  //  finally {
  //    actionManager.unregisterAction(ACTION_2);
  //    actionManager.unregisterAction(ACTION_1);
  //  }
  //}

  public void testChangingMouseShortcutInGrandChild() {
    MouseShortcut mouseShortcut = new MouseShortcut(MouseEvent.BUTTON1, 0, 1);
    myParent.addShortcut(ACTION_2, mouseShortcut);
    Keymap grandChild = myChild.deriveKeymap("GrandChild");
    grandChild.removeShortcut(ACTION_2, mouseShortcut);
    grandChild.addShortcut(ACTION_1, mouseShortcut);
    assertThat(grandChild.getActionIdList(mouseShortcut)).containsExactly(ACTION_1);
  }

  public void testRemovingShortcutLast() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    myParent.addShortcut(ACTION_2, shortcut2);
    myParent.addShortcut(ACTION_2, shortcutA);
    myParent.addShortcut(ACTION_2, shortcutB);

    myChild.removeShortcut(ACTION_2, shortcutB);
    assertThat(myChild.getShortcuts(ACTION_2)).containsExactly(shortcut2, shortcutA);
  }

  public void testRemovingShortcutFromChildWhenInherited() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    myParent.addShortcut(ACTION_1, shortcut1);
    myParent.addShortcut(ACTION_1, shortcut2);

    assertTrue(myParent.hasOwnActionId(ACTION_1));
    assertSameElements(myParent.getShortcuts(ACTION_1), shortcut1, shortcut2);
    assertFalse(myChild.hasOwnActionId(ACTION_1));
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1, shortcut2);

    myChild.removeShortcut(ACTION_1, shortcut1);

    assertTrue(myParent.hasOwnActionId(ACTION_1));
    assertSameElements(myParent.getShortcuts(ACTION_1), shortcut1, shortcut2);
    assertTrue(myChild.hasOwnActionId(ACTION_1));
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut2);
  }

  public void testRemovingShortcutFromChildWhenInheritedAndBound() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      myParent.addShortcut(BASE, shortcut1);
      myParent.addShortcut(BASE, shortcut2);

      assertTrue(myParent.hasOwnActionId(BASE));
      assertSameElements(myParent.getShortcuts(BASE), shortcut1, shortcut2);

      assertFalse(myChild.hasOwnActionId(BASE));
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);

      // child::BASE don't have it's own mapping
      myChild.removeShortcut(DEPENDENT, shortcut1);

      assertFalse(myChild.hasOwnActionId(BASE));
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertTrue(myChild.hasOwnActionId(DEPENDENT));
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut2);

      myChild.clearOwnActionsIds();

      // child::BASE has it's own mapping
      myChild.addShortcut(BASE, shortcutA);
      assertTrue(myChild.hasOwnActionId(BASE));
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2, shortcutA);
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);

      myChild.removeShortcut(DEPENDENT, shortcut1);
      assertTrue(myChild.hasOwnActionId(BASE));
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2, shortcutA);
      assertTrue(myChild.hasOwnActionId(DEPENDENT));
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut2, shortcutA);
    }
    finally {
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testRemovingShortcutNotInheritedBoundAndNotBound() {
    KeymapImpl standalone = new KeymapImpl();
    standalone.setName("standalone");

    String BASE1 = "BASE_ACTION1";
    String DEPENDENT1 = "DEPENDENT_ACTION1";
    String BASE2 = "BASE_ACTION2";
    String DEPENDENT2 = "DEPENDENT_ACTION2";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE1, DEPENDENT1);
    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE2, DEPENDENT2);
    try {
      standalone.addShortcut(ACTION_1, shortcut1);
      standalone.addShortcut(BASE1, shortcut1);

      assertTrue(standalone.hasOwnActionId(ACTION_1));
      assertFalse(standalone.hasOwnActionId(ACTION_2));
      assertTrue(standalone.hasOwnActionId(BASE1));
      assertFalse(standalone.hasOwnActionId(DEPENDENT1));
      assertFalse(standalone.hasOwnActionId(BASE2));
      assertFalse(standalone.hasOwnActionId(DEPENDENT2));

      standalone.removeShortcut(ACTION_1, shortcut1);
      standalone.removeShortcut(ACTION_2, shortcut1); // empty mapping -> should not have any effect
      standalone.removeShortcut(DEPENDENT1, shortcut1);
      standalone.removeShortcut(DEPENDENT2, shortcut1);  // empty mapping -> should not have any effect

      assertFalse(standalone.hasOwnActionId(ACTION_1));
      assertFalse(standalone.hasOwnActionId(ACTION_2));
      assertTrue(standalone.hasOwnActionId(BASE1));
      assertTrue(standalone.hasOwnActionId(DEPENDENT1));
      assertSameElements(standalone.getShortcuts(BASE1), shortcut1);
      assertSameElements(standalone.getShortcuts(DEPENDENT1));
      assertFalse(standalone.hasOwnActionId(BASE2));
      assertFalse(standalone.hasOwnActionId(DEPENDENT2));
    }
    finally{
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT1);
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT2);
    }
  }

  public void testResettingMappingInChild() {
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1, shortcutA);
    assertSameElements(myChild.getActionIdList(shortcut1), ACTION_1);
    assertSameElements(myChild.getActionIdList(shortcutA), ACTION_1);
    assertTrue(myChild.hasOwnActionId(ACTION_1));

    myChild.clearOwnActionsId(ACTION_1);
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1);
    assertSameElements(myChild.getActionIdList(shortcut1), ACTION_1);
    assertSameElements(myChild.getActionIdList(shortcutA));
    assertFalse(myChild.hasOwnActionId(ACTION_1));

    myChild.removeShortcut(ACTION_2, shortcut2);
    assertSameElements(myChild.getShortcuts(ACTION_2));
    assertSameElements(myChild.getActionIdList(shortcut2));
    assertTrue(myChild.hasOwnActionId(ACTION_2));
    myChild.clearOwnActionsId(ACTION_2);

    assertSameElements(myChild.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(myChild.getActionIdList(shortcut2), ACTION_2);
    assertFalse(myChild.hasOwnActionId(ACTION_2));
  }

  public void testChangingAndResettingBoundShortcutsInParentKeymap() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(myParent.getShortcuts(BASE));
      assertSameElements(myParent.getShortcuts(DEPENDENT));
      assertSameElements(myParent.getActionIdList(shortcut1));

      assertSameElements(myChild.getShortcuts(BASE));
      assertSameElements(myChild.getShortcuts(DEPENDENT));
      assertSameElements(myChild.getActionIdList(shortcut1));

      myParent.addShortcut(BASE, shortcut1);

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  -
      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myParent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));

      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertFalse(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));

      // override BASE action in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      myChild.addShortcut(BASE, shortcut2);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertTrue(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));

      // extend BASE action, overridden in child
      // parent:
      //  BASE -> shortcut1, shortcutA
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      myParent.addShortcut(BASE, shortcutA);
      // parent
      assertSameElements(myParent.getShortcuts(BASE), shortcut1, shortcutA);
      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1, shortcutA);
      assertSameElements(myParent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIdList(shortcutA), BASE, DEPENDENT);
      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));
      // child is not affected since the action is overridden
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutA));
      assertTrue(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));

      // extend DEPENDENT action, not-overridden in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> shortcut1, shortcutB
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      myParent.removeShortcut(BASE, shortcutA);
      myParent.addShortcut(DEPENDENT, shortcutB);
      // parent
      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1, shortcutB);
      assertSameElements(myParent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIdList(shortcut2));
      assertSameElements(myParent.getActionIdList(shortcutA));
      assertSameElements(myParent.getActionIdList(shortcutB), DEPENDENT);
      assertTrue(myParent.hasOwnActionId(BASE));
      assertTrue(myParent.hasOwnActionId(DEPENDENT));
      // child
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutA));
      assertSameElements(myChild.getActionIdList(shortcutB));
      assertTrue(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));

      // override DEPENDENT action in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> shortcut1, shortcutB
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      myChild.addShortcut(DEPENDENT, shortcutA);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutA), DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutB));
      assertTrue(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));
    }
    finally {
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testChangingAndResettingBoundShortcutsInChildKeymap() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(myParent.getShortcuts(BASE));
      assertSameElements(myParent.getShortcuts(DEPENDENT));
      assertSameElements(myParent.getActionIdList(shortcut1));

      assertSameElements(myChild.getShortcuts(BASE));
      assertSameElements(myChild.getShortcuts(DEPENDENT));
      assertSameElements(myChild.getActionIdList(shortcut1));

      myParent.addShortcut(BASE, shortcut1);

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  -
      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myParent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));

      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertFalse(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));

      // overriding BASE in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      myChild.addShortcut(BASE, shortcut2);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertTrue(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));

      // overriding DEPENDENT in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      myChild.addShortcut(DEPENDENT, shortcutA);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutA), DEPENDENT);
      assertTrue(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));

      // removing one of BASE binding
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut2
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      myChild.removeShortcut(BASE, shortcut1);
      assertSameElements(myChild.getShortcuts(BASE), shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(myChild.getActionIdList(shortcut1), DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutA), DEPENDENT);
      assertTrue(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));

      // removing last of BASE binding
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> -
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      myChild.removeShortcut(BASE, shortcut2);
      assertSameElements(myChild.getShortcuts(BASE));
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(myChild.getActionIdList(shortcut1), DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutA), DEPENDENT);
      assertTrue(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));

      // clearing BASE binding
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> parent:BASE
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      myChild.clearOwnActionsId(BASE);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(myChild.getActionIdList(shortcut1), DEPENDENT, BASE);
      assertSameElements(myChild.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutA), DEPENDENT);
      assertFalse(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));

      // clearing DEPENDENT binding
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> parent:BASE
      //  DEPENDENT -> child:BASE
      myChild.clearOwnActionsId(DEPENDENT);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2));
      assertSameElements(myChild.getActionIdList(shortcutA));
      assertFalse(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
    }
    finally {
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testRemovingChildMappingIsTheSameAsResetting() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(myParent.getShortcuts(BASE));
      assertSameElements(myParent.getShortcuts(DEPENDENT));
      assertSameElements(myParent.getActionIdList(shortcut1));

      assertSameElements(myChild.getShortcuts(BASE));
      assertSameElements(myChild.getShortcuts(DEPENDENT));
      assertSameElements(myChild.getActionIdList(shortcut1));

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  -
      myParent.addShortcut(BASE, shortcut1);

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcutA
      //  DEPENDENT -> shortcut1, shortcutA, shortcutB
      myChild.addShortcut(BASE, shortcutA);
      myChild.addShortcut(DEPENDENT, shortcutB);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcutA);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcutA, shortcutB);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2));
      assertSameElements(myChild.getActionIdList(shortcutA), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutB), DEPENDENT);
      assertTrue(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));
      // remove from child:BASE first
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1
      //  DEPENDENT -> shortcut1, shortcutA, shortcutB
      myChild.removeShortcut(BASE, shortcutA);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcutA, shortcutB);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2));
      assertSameElements(myChild.getActionIdList(shortcutA), DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcutB), DEPENDENT);
      assertFalse(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));
      // remove dependent child:BASE first
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1 == parent:BASE
      //  DEPENDENT -> shortcut1 == child:BASE
      myChild.removeShortcut(DEPENDENT, shortcutA);
      myChild.removeShortcut(DEPENDENT, shortcutB);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut2));
      assertSameElements(myChild.getActionIdList(shortcutA));
      assertSameElements(myChild.getActionIdList(shortcutB));
      assertFalse(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
    }
    finally {
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testLookingForShortcutsInParentFirstAndOnlyThenConsiderBoundActions() {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();
    KeymapImpl myGrandChild = myChild.deriveKeymap("GrandChild");
    assertSame(myChild, myGrandChild.getParent());

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      // parent:
      //  BASE -> shortcut1  <-- change is here
      //  DEPENDENT -> BASE
      // child:
      //  -
      // grand-child:
      //  -
      myParent.addShortcut(BASE, shortcut1);

      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1);

      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1);

      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));
      assertFalse(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
      assertFalse(myGrandChild.hasOwnActionId(BASE));
      assertFalse(myGrandChild.hasOwnActionId(DEPENDENT));

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> parent:BASE
      //  DEPENDENT -> shortcut1, +shortcut2  <-- change is here
      // grand-child:
      //  BASE -> parent:BASE
      //  DEPENDENT -> child:DEPENDENT
      myChild.addShortcut(DEPENDENT, shortcut2);

      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1);

      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);

      assertSameElements(myParent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIdList(shortcut2));
      assertSameElements(myChild.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut2), DEPENDENT);

      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));
      assertFalse(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));
      assertFalse(myGrandChild.hasOwnActionId(BASE));
      assertFalse(myGrandChild.hasOwnActionId(DEPENDENT));

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> parent:BASE
      //  DEPENDENT -> shortcut1, +shortcut2
      // grand-child:
      //  BASE -> parent:BASE
      //  DEPENDENT -> shortcut1, shortcut2, + shortcutA  <-- change is here
      myGrandChild.addShortcut(DEPENDENT, shortcutA);

      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1);

      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);

      assertSameElements(myParent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIdList(shortcut2));
      assertSameElements(myChild.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(myParent.getActionIdList(shortcutA));
      assertSameElements(myChild.getActionIdList(shortcutA));
      assertSameElements(myGrandChild.getActionIdList(shortcutA), DEPENDENT);

      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));
      assertFalse(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));
      assertFalse(myGrandChild.hasOwnActionId(BASE));
      assertTrue(myGrandChild.hasOwnActionId(DEPENDENT));

      // Now let's try the other way round - redefine base shortcut in children and check that DEPENDENT action uses the correct one
      myChild.clearOwnActionsIds();
      myGrandChild.clearOwnActionsIds();

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, +shortcut2 <-- change is here
      //  DEPENDENT -> child:BASE
      // grand-child:
      //  BASE -> child:BASE
      //  DEPENDENT -> child:BASE
      myChild.addShortcut(BASE, shortcut2);

      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1, shortcut2);

      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);

      assertSameElements(myParent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIdList(shortcut2));
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut2), BASE, DEPENDENT);

      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));
      assertTrue(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
      assertFalse(myGrandChild.hasOwnActionId(BASE));
      assertFalse(myGrandChild.hasOwnActionId(DEPENDENT));

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      // grand-child:
      //  BASE -> shortcut1, shortcut2, +shortcutA <-- change is here
      //  DEPENDENT -> grand-child:BASE
      myGrandChild.addShortcut(BASE, shortcutA);

      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1, shortcut2, shortcutA);

      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);

      assertSameElements(myParent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIdList(shortcut2));
      assertSameElements(myChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIdList(shortcutA));
      assertSameElements(myChild.getActionIdList(shortcutA));
      assertSameElements(myGrandChild.getActionIdList(shortcutA), BASE, DEPENDENT);

      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));
      assertTrue(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
      assertTrue(myGrandChild.hasOwnActionId(BASE));
      assertFalse(myGrandChild.hasOwnActionId(DEPENDENT));
    }
    finally {
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }
  
  public void testParallelGetShortcuts() {
    Keymap grandChild = myChild.deriveKeymap("GrandChild");
    Runnable task = () -> {
      for (int i = 0; i < 1000; i++) {
        assertEquals(Shortcut.EMPTY_ARRAY, grandChild.getShortcuts("none"));
        List<Shortcut> shortcuts = Arrays.asList(grandChild.getShortcuts(ACTION_1));
        assertTrue(shortcuts.size() >= 2 && shortcuts.size() <= 3);
        String message = shortcuts.toString();
        assertTrue(message, shortcuts.contains(shortcut1));
        assertTrue(message, shortcuts.contains(shortcutA));
        if (shortcuts.size() == 3) {
          assertTrue(message, shortcuts.contains(shortcut2));
        }
      }
    };
    List<CompletableFuture<Void>> tasks = StreamEx.constant(task, 10).map(CompletableFuture::runAsync).toList();
    for (int i = 0; i < 10000; i++) {
      grandChild.addShortcut(ACTION_1, shortcut2);
      grandChild.removeShortcut(ACTION_1, shortcut2);
    }
    tasks.forEach(CompletableFuture::join);
  }
}
