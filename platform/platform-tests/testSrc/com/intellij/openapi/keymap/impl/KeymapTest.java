// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.Keymap;
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

  private KeymapImpl parent;
  private KeymapImpl child;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    parent = new KeymapImpl();
    parent.setName("Parent");
    parent.setCanModify(false);

    parent.addShortcut(ACTION_1, shortcut1);
    parent.addShortcut(ACTION_2, shortcut2);

    child = parent.deriveKeymap("Child");
    child.setCanModify(false);
    assertThat(parent).isSameAs(child.getParent());

    child.addShortcut(ACTION_1, shortcutA);
  }

  public void testParentAndChildShortcuts() {
    assertTrue(parent.hasOwnActionId(ACTION_1));
    assertTrue(parent.hasOwnActionId(ACTION_2));
    assertFalse(parent.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(parent.getShortcuts(ACTION_1), shortcut1);
    assertSameElements(parent.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(parent.getShortcuts(ACTION_NON_EXISTENT));

    assertSameElements(parent.getActionIdList(shortcut1), ACTION_1);
    assertSameElements(parent.getActionIdList(shortcut2), ACTION_2);
    assertSameElements(parent.getActionIdList(shortcutA));
    assertSameElements(parent.getActionIdList(shortcutB));

    assertTrue(child.hasOwnActionId(ACTION_1));
    assertFalse(child.hasOwnActionId(ACTION_2));
    assertFalse(child.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(child.getShortcuts(ACTION_1), shortcut1, shortcutA);
    assertSameElements(child.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(child.getShortcuts(ACTION_NON_EXISTENT));

    assertSameElements(child.getActionIdList(shortcut1), ACTION_1);
    assertSameElements(child.getActionIdList(shortcut2), ACTION_2);
    assertSameElements(child.getActionIdList(shortcutA), ACTION_1);
    assertSameElements(child.getActionIdList(shortcutB));
  }

  public void testRemovingShortcutsFromParentAndChild() {
    parent.removeShortcut(ACTION_1, shortcut1);

    assertFalse(parent.hasOwnActionId(ACTION_1));
    assertTrue(parent.hasOwnActionId(ACTION_2));
    assertFalse(parent.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(parent.getShortcuts(ACTION_1));
    assertSameElements(parent.getShortcuts(ACTION_2), shortcut2);

    assertSameElements(parent.getActionIdList(shortcut1));
    assertSameElements(parent.getActionIdList(shortcut2), ACTION_2);

    // child keymap still lists inherited shortcut
    assertSameElements(child.getShortcuts(ACTION_1), shortcut1, shortcutA);

    child.removeShortcut(ACTION_1, shortcut1);
    assertSameElements(child.getShortcuts(ACTION_1), shortcutA);
    assertSameElements(child.getActionIdList(shortcut1));
    assertSameElements(child.getActionIdList(shortcutA), ACTION_1);
    assertTrue(child.hasOwnActionId(ACTION_1));

    child.removeShortcut(ACTION_1, shortcutA);
    assertSameElements(child.getShortcuts(ACTION_1));
    assertSameElements(child.getActionIdList(shortcutA));
    assertFalse(child.hasOwnActionId(ACTION_1)); // since equal to parent list

    child.removeShortcut(ACTION_2, shortcut2);
    assertSameElements(child.getShortcuts(ACTION_2));
    assertSameElements(child.getActionIdList(shortcut2));
    assertTrue(child.hasOwnActionId(ACTION_2)); // since different from parent list
  }

  public void testRemovingShortcutFromChildWhenInheritedDontChangeTheListIfShortcutIsAbsent() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    parent.addShortcut(ACTION_1, shortcut1);

    assertThat(parent.hasOwnActionId(ACTION_1)).isTrue();
    assertThat(child.hasOwnActionId(ACTION_1)).isFalse();
    assertThat(child.getShortcuts(ACTION_1)).containsExactly(shortcut1);

    // should not have any effect
    child.removeShortcut(ACTION_1, shortcutA);

    assertThat(parent.hasOwnActionId(ACTION_1)).isTrue();
    assertThat(child.hasOwnActionId(ACTION_1)).isFalse();
    assertThat(child.getShortcuts(ACTION_1)).containsExactly(shortcut1);

    parent.addShortcut(ACTION_2, shortcut2);
    parent.addShortcut(ACTION_2, shortcutA);
    parent.addShortcut(ACTION_2, shortcutB);

    child.removeShortcut(ACTION_2, shortcutA);
    assertThat(child.getShortcuts(ACTION_2)).containsExactly(shortcut2, shortcutB);
  }

  public void testRemovingShortcutFirst() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    parent.addShortcut(ACTION_2, shortcut2);
    parent.addShortcut(ACTION_2, shortcutA);
    parent.addShortcut(ACTION_2, shortcutB);

    child.removeShortcut(ACTION_2, shortcut2);
    assertThat(child.getShortcuts(ACTION_2)).containsExactly(shortcutA, shortcutB);
  }

  public void testRemoveMouseShortcut() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    MouseShortcut mouseShortcut = new MouseShortcut(1, InputEvent.BUTTON2_MASK, 1);
    parent.addShortcut(ACTION_2, mouseShortcut);
    assertThat(child.getActionIdList(mouseShortcut)).containsExactly(ACTION_2);
    child.removeShortcut(ACTION_2, mouseShortcut);
    assertThat(child.getActionIdList(mouseShortcut)).isEmpty();
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
    parent.addShortcut(ACTION_2, mouseShortcut);
    Keymap grandChild = child.deriveKeymap("GrandChild");
    grandChild.removeShortcut(ACTION_2, mouseShortcut);
    grandChild.addShortcut(ACTION_1, mouseShortcut);
    assertThat(grandChild.getActionIdList(mouseShortcut)).containsExactly(ACTION_1);
  }

  public void testRemovingShortcutLast() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    parent.addShortcut(ACTION_2, shortcut2);
    parent.addShortcut(ACTION_2, shortcutA);
    parent.addShortcut(ACTION_2, shortcutB);

    child.removeShortcut(ACTION_2, shortcutB);
    assertThat(child.getShortcuts(ACTION_2)).containsExactly(shortcut2, shortcutA);
  }

  public void testRemovingShortcutFromChildWhenInherited() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    parent.addShortcut(ACTION_1, shortcut1);
    parent.addShortcut(ACTION_1, shortcut2);

    assertTrue(parent.hasOwnActionId(ACTION_1));
    assertSameElements(parent.getShortcuts(ACTION_1), shortcut1, shortcut2);
    assertFalse(child.hasOwnActionId(ACTION_1));
    assertSameElements(child.getShortcuts(ACTION_1), shortcut1, shortcut2);

    child.removeShortcut(ACTION_1, shortcut1);

    assertTrue(parent.hasOwnActionId(ACTION_1));
    assertSameElements(parent.getShortcuts(ACTION_1), shortcut1, shortcut2);
    assertTrue(child.hasOwnActionId(ACTION_1));
    assertSameElements(child.getShortcuts(ACTION_1), shortcut2);
  }

  public void testRemovingShortcutFromChildWhenInheritedAndBound() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    ActionManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      parent.addShortcut(BASE, shortcut1);
      parent.addShortcut(BASE, shortcut2);

      assertTrue(parent.hasOwnActionId(BASE));
      assertSameElements(parent.getShortcuts(BASE), shortcut1, shortcut2);

      assertFalse(child.hasOwnActionId(BASE));
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertFalse(child.hasOwnActionId(DEPENDENT));
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);

      // child::BASE don't have it's own mapping
      child.removeShortcut(DEPENDENT, shortcut1);

      assertFalse(child.hasOwnActionId(BASE));
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertTrue(child.hasOwnActionId(DEPENDENT));
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut2);

      child.clearOwnActionsIds();

      // child::BASE has it's own mapping
      child.addShortcut(BASE, shortcutA);
      assertTrue(child.hasOwnActionId(BASE));
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2, shortcutA);
      assertFalse(child.hasOwnActionId(DEPENDENT));
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);

      child.removeShortcut(DEPENDENT, shortcut1);
      assertTrue(child.hasOwnActionId(BASE));
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2, shortcutA);
      assertTrue(child.hasOwnActionId(DEPENDENT));
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut2, shortcutA);
    }
    finally {
      ActionManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testRemovingShortcutNotInheritedBoundAndNotBound() {
    KeymapImpl standalone = new KeymapImpl();
    standalone.setName("standalone");

    String BASE1 = "BASE_ACTION1";
    String DEPENDENT1 = "DEPENDENT_ACTION1";
    String BASE2 = "BASE_ACTION2";
    String DEPENDENT2 = "DEPENDENT_ACTION2";

    ActionManagerEx.getInstanceEx().bindShortcuts(BASE1, DEPENDENT1);
    ActionManagerEx.getInstanceEx().bindShortcuts(BASE2, DEPENDENT2);
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
      ActionManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT1);
      ActionManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT2);
    }
  }

  public void testResettingMappingInChild() {
    assertSameElements(child.getShortcuts(ACTION_1), shortcut1, shortcutA);
    assertSameElements(child.getActionIdList(shortcut1), ACTION_1);
    assertSameElements(child.getActionIdList(shortcutA), ACTION_1);
    assertTrue(child.hasOwnActionId(ACTION_1));

    child.clearOwnActionsId(ACTION_1);
    assertSameElements(child.getShortcuts(ACTION_1), shortcut1);
    assertSameElements(child.getActionIdList(shortcut1), ACTION_1);
    assertSameElements(child.getActionIdList(shortcutA));
    assertFalse(child.hasOwnActionId(ACTION_1));

    child.removeShortcut(ACTION_2, shortcut2);
    assertSameElements(child.getShortcuts(ACTION_2));
    assertSameElements(child.getActionIdList(shortcut2));
    assertTrue(child.hasOwnActionId(ACTION_2));
    child.clearOwnActionsId(ACTION_2);

    assertSameElements(child.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(child.getActionIdList(shortcut2), ACTION_2);
    assertFalse(child.hasOwnActionId(ACTION_2));
  }

  public void testChangingAndResettingBoundShortcutsInParentKeymap() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    ActionManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(parent.getShortcuts(BASE));
      assertSameElements(parent.getShortcuts(DEPENDENT));
      assertSameElements(parent.getActionIdList(shortcut1));

      assertSameElements(child.getShortcuts(BASE));
      assertSameElements(child.getShortcuts(DEPENDENT));
      assertSameElements(child.getActionIdList(shortcut1));

      parent.addShortcut(BASE, shortcut1);

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  -
      assertSameElements(parent.getShortcuts(BASE), shortcut1);
      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(parent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertTrue(parent.hasOwnActionId(BASE));
      assertFalse(parent.hasOwnActionId(DEPENDENT));

      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertFalse(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));

      // override BASE action in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      child.addShortcut(BASE, shortcut2);
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertTrue(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));

      // extend BASE action, overridden in child
      // parent:
      //  BASE -> shortcut1, shortcutA
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      parent.addShortcut(BASE, shortcutA);
      // parent
      assertSameElements(parent.getShortcuts(BASE), shortcut1, shortcutA);
      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1, shortcutA);
      assertSameElements(parent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(parent.getActionIdList(shortcutA), BASE, DEPENDENT);
      assertTrue(parent.hasOwnActionId(BASE));
      assertFalse(parent.hasOwnActionId(DEPENDENT));
      // child is not affected since the action is overridden
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutA));
      assertTrue(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));

      // extend DEPENDENT action, not-overridden in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> shortcut1, shortcutB
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      parent.removeShortcut(BASE, shortcutA);
      parent.addShortcut(DEPENDENT, shortcutB);
      // parent
      assertSameElements(parent.getShortcuts(BASE), shortcut1);
      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1, shortcutB);
      assertSameElements(parent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(parent.getActionIdList(shortcut2));
      assertSameElements(parent.getActionIdList(shortcutA));
      assertSameElements(parent.getActionIdList(shortcutB), DEPENDENT);
      assertTrue(parent.hasOwnActionId(BASE));
      assertTrue(parent.hasOwnActionId(DEPENDENT));
      // child
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutA));
      assertSameElements(child.getActionIdList(shortcutB));
      assertTrue(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));

      // override DEPENDENT action in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> shortcut1, shortcutB
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      child.addShortcut(DEPENDENT, shortcutA);
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutA), DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutB));
      assertTrue(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));
    }
    finally {
      ActionManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testChangingAndResettingBoundShortcutsInChildKeymap() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    ActionManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(parent.getShortcuts(BASE));
      assertSameElements(parent.getShortcuts(DEPENDENT));
      assertSameElements(parent.getActionIdList(shortcut1));

      assertSameElements(child.getShortcuts(BASE));
      assertSameElements(child.getShortcuts(DEPENDENT));
      assertSameElements(child.getActionIdList(shortcut1));

      parent.addShortcut(BASE, shortcut1);

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  -
      assertSameElements(parent.getShortcuts(BASE), shortcut1);
      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(parent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertTrue(parent.hasOwnActionId(BASE));
      assertFalse(parent.hasOwnActionId(DEPENDENT));

      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertFalse(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));

      // overriding BASE in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> child:BASE
      child.addShortcut(BASE, shortcut2);
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertTrue(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));

      // overriding DEPENDENT in child
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcut2
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      child.addShortcut(DEPENDENT, shortcutA);
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutA), DEPENDENT);
      assertTrue(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));

      // removing one of BASE binding
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut2
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      child.removeShortcut(BASE, shortcut1);
      assertSameElements(child.getShortcuts(BASE), shortcut2);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(child.getActionIdList(shortcut1), DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutA), DEPENDENT);
      assertTrue(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));

      // removing last of BASE binding
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> -
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      child.removeShortcut(BASE, shortcut2);
      assertSameElements(child.getShortcuts(BASE));
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(child.getActionIdList(shortcut1), DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutA), DEPENDENT);
      assertTrue(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));

      // clearing BASE binding
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> parent:BASE
      //  DEPENDENT -> shortcut1, shortcut2, shortcutA
      child.clearOwnActionsId(BASE);
      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);
      assertSameElements(child.getActionIdList(shortcut1), DEPENDENT, BASE);
      assertSameElements(child.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutA), DEPENDENT);
      assertFalse(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));

      // clearing DEPENDENT binding
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> parent:BASE
      //  DEPENDENT -> child:BASE
      child.clearOwnActionsId(DEPENDENT);
      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2));
      assertSameElements(child.getActionIdList(shortcutA));
      assertFalse(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));
    }
    finally {
      ActionManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testRemovingChildMappingIsTheSameAsResetting() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    ActionManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(parent.getShortcuts(BASE));
      assertSameElements(parent.getShortcuts(DEPENDENT));
      assertSameElements(parent.getActionIdList(shortcut1));

      assertSameElements(child.getShortcuts(BASE));
      assertSameElements(child.getShortcuts(DEPENDENT));
      assertSameElements(child.getActionIdList(shortcut1));

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  -
      parent.addShortcut(BASE, shortcut1);

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1, shortcutA
      //  DEPENDENT -> shortcut1, shortcutA, shortcutB
      child.addShortcut(BASE, shortcutA);
      child.addShortcut(DEPENDENT, shortcutB);
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcutA);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcutA, shortcutB);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2));
      assertSameElements(child.getActionIdList(shortcutA), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutB), DEPENDENT);
      assertTrue(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));
      // remove from child:BASE first
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1
      //  DEPENDENT -> shortcut1, shortcutA, shortcutB
      child.removeShortcut(BASE, shortcutA);
      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcutA, shortcutB);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2));
      assertSameElements(child.getActionIdList(shortcutA), DEPENDENT);
      assertSameElements(child.getActionIdList(shortcutB), DEPENDENT);
      assertFalse(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));
      // remove dependent child:BASE first
      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  BASE -> shortcut1 == parent:BASE
      //  DEPENDENT -> shortcut1 == child:BASE
      child.removeShortcut(DEPENDENT, shortcutA);
      child.removeShortcut(DEPENDENT, shortcutB);
      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut2));
      assertSameElements(child.getActionIdList(shortcutA));
      assertSameElements(child.getActionIdList(shortcutB));
      assertFalse(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));
    }
    finally {
      ActionManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testLookingForShortcutsInParentFirstAndOnlyThenConsiderBoundActions() {
    parent.clearOwnActionsIds();
    child.clearOwnActionsIds();
    KeymapImpl myGrandChild = child.deriveKeymap("GrandChild");
    assertSame(child, myGrandChild.getParent());

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    ActionManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      // parent:
      //  BASE -> shortcut1  <-- change is here
      //  DEPENDENT -> BASE
      // child:
      //  -
      // grand-child:
      //  -
      parent.addShortcut(BASE, shortcut1);

      assertSameElements(parent.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1);

      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1);

      assertTrue(parent.hasOwnActionId(BASE));
      assertFalse(parent.hasOwnActionId(DEPENDENT));
      assertFalse(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));
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
      child.addShortcut(DEPENDENT, shortcut2);

      assertSameElements(parent.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1);

      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);

      assertSameElements(parent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(parent.getActionIdList(shortcut2));
      assertSameElements(child.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut2), DEPENDENT);

      assertTrue(parent.hasOwnActionId(BASE));
      assertFalse(parent.hasOwnActionId(DEPENDENT));
      assertFalse(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));
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

      assertSameElements(parent.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(BASE), shortcut1);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1);

      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);

      assertSameElements(parent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(parent.getActionIdList(shortcut2));
      assertSameElements(child.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut2), DEPENDENT);
      assertSameElements(parent.getActionIdList(shortcutA));
      assertSameElements(child.getActionIdList(shortcutA));
      assertSameElements(myGrandChild.getActionIdList(shortcutA), DEPENDENT);

      assertTrue(parent.hasOwnActionId(BASE));
      assertFalse(parent.hasOwnActionId(DEPENDENT));
      assertFalse(child.hasOwnActionId(BASE));
      assertTrue(child.hasOwnActionId(DEPENDENT));
      assertFalse(myGrandChild.hasOwnActionId(BASE));
      assertTrue(myGrandChild.hasOwnActionId(DEPENDENT));

      // Now let's try the other way round - redefine base shortcut in children and check that DEPENDENT action uses the correct one
      child.clearOwnActionsIds();
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
      child.addShortcut(BASE, shortcut2);

      assertSameElements(parent.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1, shortcut2);

      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);

      assertSameElements(parent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(parent.getActionIdList(shortcut2));
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut2), BASE, DEPENDENT);

      assertTrue(parent.hasOwnActionId(BASE));
      assertFalse(parent.hasOwnActionId(DEPENDENT));
      assertTrue(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));
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

      assertSameElements(parent.getShortcuts(BASE), shortcut1);
      assertSameElements(child.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(BASE), shortcut1, shortcut2, shortcutA);

      assertSameElements(parent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(child.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myGrandChild.getShortcuts(DEPENDENT), shortcut1, shortcut2, shortcutA);

      assertSameElements(parent.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(child.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut1), BASE, DEPENDENT);
      assertSameElements(parent.getActionIdList(shortcut2));
      assertSameElements(child.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(myGrandChild.getActionIdList(shortcut2), BASE, DEPENDENT);
      assertSameElements(parent.getActionIdList(shortcutA));
      assertSameElements(child.getActionIdList(shortcutA));
      assertSameElements(myGrandChild.getActionIdList(shortcutA), BASE, DEPENDENT);

      assertTrue(parent.hasOwnActionId(BASE));
      assertFalse(parent.hasOwnActionId(DEPENDENT));
      assertTrue(child.hasOwnActionId(BASE));
      assertFalse(child.hasOwnActionId(DEPENDENT));
      assertTrue(myGrandChild.hasOwnActionId(BASE));
      assertFalse(myGrandChild.hasOwnActionId(DEPENDENT));
    }
    finally {
      ActionManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }
  
  public void testParallelGetShortcuts() {
    Keymap grandChild = child.deriveKeymap("GrandChild");
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
