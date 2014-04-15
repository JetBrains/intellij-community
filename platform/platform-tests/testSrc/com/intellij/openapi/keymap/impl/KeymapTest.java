/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.testFramework.PlatformTestCase;

import javax.swing.*;

public class KeymapTest extends PlatformTestCase {
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
    initPlatformLangPrefix();
    super.setUp();

    myParent = new KeymapImpl();
    myParent.setName("Parent");
    myParent.setCanModify(false);

    myParent.addShortcut(ACTION_1, shortcut1);
    myParent.addShortcut(ACTION_2, shortcut2);

    myChild = myParent.deriveKeymap();
    myChild.setName("Child");
    assertSame(myParent, myChild.getParent());

    myChild.addShortcut(ACTION_1, shortcutA);
  }

  public void testParentAndChildShortcuts() throws Exception {
    assertTrue(myParent.hasOwnActionId(ACTION_1));
    assertTrue(myParent.hasOwnActionId(ACTION_2));
    assertFalse(myParent.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(myParent.getShortcuts(ACTION_1), shortcut1);
    assertSameElements(myParent.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(myParent.getShortcuts(ACTION_NON_EXISTENT));

    assertSameElements(myParent.getActionIds(shortcut1), ACTION_1);
    assertSameElements(myParent.getActionIds(shortcut2), ACTION_2);
    assertSameElements(myParent.getActionIds(shortcutA));
    assertSameElements(myParent.getActionIds(shortcutB));

    assertTrue(myChild.hasOwnActionId(ACTION_1));
    assertFalse(myChild.hasOwnActionId(ACTION_2));
    assertFalse(myChild.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1, shortcutA);
    assertSameElements(myChild.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(myChild.getShortcuts(ACTION_NON_EXISTENT));

    assertSameElements(myChild.getActionIds(shortcut1), ACTION_1);
    assertSameElements(myChild.getActionIds(shortcut2), ACTION_2);
    assertSameElements(myChild.getActionIds(shortcutA), ACTION_1);
    assertSameElements(myChild.getActionIds(shortcutB));
  }

  public void testRemovingShortcutsFromParentAndChild() throws Exception {
    myParent.removeShortcut(ACTION_1, shortcut1);

    assertFalse(myParent.hasOwnActionId(ACTION_1));
    assertTrue(myParent.hasOwnActionId(ACTION_2));
    assertFalse(myParent.hasOwnActionId(ACTION_NON_EXISTENT));

    assertSameElements(myParent.getShortcuts(ACTION_1));
    assertSameElements(myParent.getShortcuts(ACTION_2), shortcut2);

    assertSameElements(myParent.getActionIds(shortcut1));
    assertSameElements(myParent.getActionIds(shortcut2), ACTION_2);

    // child keymap still lists inherited shortcut
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1, shortcutA);

    myChild.removeShortcut(ACTION_1, shortcut1);
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcutA);
    assertSameElements(myChild.getActionIds(shortcut1));
    assertSameElements(myChild.getActionIds(shortcutA), ACTION_1);
    assertTrue(myChild.hasOwnActionId(ACTION_1));

    myChild.removeShortcut(ACTION_1, shortcutA);
    assertSameElements(myChild.getShortcuts(ACTION_1));
    assertSameElements(myChild.getActionIds(shortcutA));
    assertFalse(myChild.hasOwnActionId(ACTION_1)); // since equal to parent list

    myChild.removeShortcut(ACTION_2, shortcut2);
    assertSameElements(myChild.getShortcuts(ACTION_2));
    assertSameElements(myChild.getActionIds(shortcut2));
    assertTrue(myChild.hasOwnActionId(ACTION_2)); // since different from parent list
  }

  public void testResettingMappingInChild() throws Exception {
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1, shortcutA);
    assertSameElements(myChild.getActionIds(shortcut1), ACTION_1);
    assertSameElements(myChild.getActionIds(shortcutA), ACTION_1);
    assertTrue(myChild.hasOwnActionId(ACTION_1));

    myChild.clearOwnActionsId(ACTION_1);
    assertSameElements(myChild.getShortcuts(ACTION_1), shortcut1);
    assertSameElements(myChild.getActionIds(shortcut1), ACTION_1);
    assertSameElements(myChild.getActionIds(shortcutA));
    assertFalse(myChild.hasOwnActionId(ACTION_1));

    myChild.removeShortcut(ACTION_2, shortcut2);
    assertSameElements(myChild.getShortcuts(ACTION_2));
    assertSameElements(myChild.getActionIds(shortcut2));
    assertTrue(myChild.hasOwnActionId(ACTION_2));
    myChild.clearOwnActionsId(ACTION_2);

    assertSameElements(myChild.getShortcuts(ACTION_2), shortcut2);
    assertSameElements(myChild.getActionIds(shortcut2), ACTION_2);
    assertFalse(myChild.hasOwnActionId(ACTION_2));
  }

  public void testChangingAndResettingBoundShortcutsInParentKeymap() throws Exception {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(myParent.getShortcuts(BASE));
      assertSameElements(myParent.getShortcuts(DEPENDENT));
      assertSameElements(myParent.getActionIds(shortcut1));

      assertSameElements(myChild.getShortcuts(BASE));
      assertSameElements(myChild.getShortcuts(DEPENDENT));
      assertSameElements(myChild.getActionIds(shortcut1));

      myParent.addShortcut(BASE, shortcut1);

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  -
      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myParent.getActionIds(shortcut1), BASE, DEPENDENT);
      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));

      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2), BASE, DEPENDENT);
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
      assertSameElements(myParent.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIds(shortcutA), BASE, DEPENDENT);
      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));
      // child is not affected since the action is overridden
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutA));
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
      assertSameElements(myParent.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myParent.getActionIds(shortcut2));
      assertSameElements(myParent.getActionIds(shortcutA));
      assertSameElements(myParent.getActionIds(shortcutB), DEPENDENT);
      assertTrue(myParent.hasOwnActionId(BASE));
      assertTrue(myParent.hasOwnActionId(DEPENDENT));
      // child
      assertSameElements(myChild.getShortcuts(BASE), shortcut1, shortcut2);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1, shortcut2);
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutA));
      assertSameElements(myChild.getActionIds(shortcutB));
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
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutA), DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutB));
      assertTrue(myChild.hasOwnActionId(BASE));
      assertTrue(myChild.hasOwnActionId(DEPENDENT));
    }
    finally {
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }

  public void testChangingAndResettingBoundShortcutsInChildKeymap() throws Exception {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(myParent.getShortcuts(BASE));
      assertSameElements(myParent.getShortcuts(DEPENDENT));
      assertSameElements(myParent.getActionIds(shortcut1));

      assertSameElements(myChild.getShortcuts(BASE));
      assertSameElements(myChild.getShortcuts(DEPENDENT));
      assertSameElements(myChild.getActionIds(shortcut1));

      myParent.addShortcut(BASE, shortcut1);

      // parent:
      //  BASE -> shortcut1
      //  DEPENDENT -> BASE
      // child:
      //  -
      assertSameElements(myParent.getShortcuts(BASE), shortcut1);
      assertSameElements(myParent.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myParent.getActionIds(shortcut1), BASE, DEPENDENT);
      assertTrue(myParent.hasOwnActionId(BASE));
      assertFalse(myParent.hasOwnActionId(DEPENDENT));

      assertSameElements(myChild.getShortcuts(BASE), shortcut1);
      assertSameElements(myChild.getShortcuts(DEPENDENT), shortcut1);
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2), BASE, DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutA), DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutA), DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2), DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutA), DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), DEPENDENT, BASE);
      assertSameElements(myChild.getActionIds(shortcut2), DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutA), DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2));
      assertSameElements(myChild.getActionIds(shortcutA));
      assertFalse(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
    }
    finally {
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }
  public void testRemovingChildMappingIsTheSameAsResetting() throws Exception {
    myParent.clearOwnActionsIds();
    myChild.clearOwnActionsIds();

    String BASE = "BASE_ACTION";
    String DEPENDENT = "DEPENDENT_ACTION";

    KeymapManagerEx.getInstanceEx().bindShortcuts(BASE, DEPENDENT);
    try {
      assertSameElements(myParent.getShortcuts(BASE));
      assertSameElements(myParent.getShortcuts(DEPENDENT));
      assertSameElements(myParent.getActionIds(shortcut1));

      assertSameElements(myChild.getShortcuts(BASE));
      assertSameElements(myChild.getShortcuts(DEPENDENT));
      assertSameElements(myChild.getActionIds(shortcut1));

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
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2));
      assertSameElements(myChild.getActionIds(shortcutA), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutB), DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2));
      assertSameElements(myChild.getActionIds(shortcutA), DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcutB), DEPENDENT);
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
      assertSameElements(myChild.getActionIds(shortcut1), BASE, DEPENDENT);
      assertSameElements(myChild.getActionIds(shortcut2));
      assertSameElements(myChild.getActionIds(shortcutA));
      assertSameElements(myChild.getActionIds(shortcutB));
      assertFalse(myChild.hasOwnActionId(BASE));
      assertFalse(myChild.hasOwnActionId(DEPENDENT));
    }
    finally {
      KeymapManagerEx.getInstanceEx().unbindShortcuts(DEPENDENT);
    }
  }
}
