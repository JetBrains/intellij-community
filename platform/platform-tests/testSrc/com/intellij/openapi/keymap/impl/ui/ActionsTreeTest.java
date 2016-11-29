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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.keymap.impl.ShortcutRestrictions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActionsTreeTest extends LightPlatformCodeInsightTestCase {
  private static final String ACTION_WITHOUT_TEXT_AND_DESCRIPTION = "DummyWithoutTextAndDescription";
  private static final String ACTION_WITH_TEXT_ONLY = "DummyWithTextOnly";
  private static final String ACTION_WITH_TEXT_AND_DESCRIPTION = "DummyWithTextAndDescription";
  private static final String EXISTENT_ACTION = "DummyExistent";
  private static final String NON_EXISTENT_ACTION = "DummyNonExistent";
  private static final String ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION = "DummyWithUseShortcutOfExistentAction";
  private static final String ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED = "DummyWithUseShortcutOfExistentActionRedefined";
  private static final String ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT = "DummyWithUseShortcutOfExistentActionRedefinedInParent";
  private static final String ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION = "DummyWithUseShortcutOfNonExistentAction";

  private static final String ACTION_WITH_FIXED_SHORTCUTS = "DummyActionWithFixedShortcuts";

  private static final String ACTION_EDITOR_DELETE_WITH_SHORTCUT = "EditorDelete";
  private static final String ACTION_EDITOR_CUT_WITHOUT_SHORTCUT = "EditorCut";

  private AnAction myActionWithoutTextAndDescription;
  private AnAction myActionWithTextOnly;
  private AnAction myActionWithTextAndDescription;
  private AnAction myActionExistent;
  private AnAction myActionWithUseShortcutOfExistent;
  private AnAction myActionWithUseShortcutOfExistentRedefined;
  private AnAction myActionWithUseShortcutOfExistentRedefinedInParent;
  private AnAction myActionWithUseShortcutOfNonExistent;
  private AnAction myActionWithFixedShortcuts;

  private ActionsTree myActionsTree;

  private ActionShortcutRestrictions mySavedRestrictions;

  public void setUp() throws Exception {
    super.setUp();
    // create dummy actions
    myActionWithoutTextAndDescription = new MyAction(null, null);
    myActionWithTextOnly = new MyAction("some text", null);
    myActionWithTextAndDescription = new MyAction("text", "description");
    myActionExistent = new MyAction("text", "description");
    myActionWithUseShortcutOfExistent = new MyAction("text", "description");
    myActionWithUseShortcutOfExistentRedefined = new MyAction("text", "description");
    myActionWithUseShortcutOfExistentRedefinedInParent = new MyAction("text", "description");
    myActionWithUseShortcutOfNonExistent = new MyAction("text", "description");
    myActionWithFixedShortcuts = new MyAction("text", "description");

    ActionManager actionManager = ActionManager.getInstance();
    actionManager.registerAction(ACTION_WITHOUT_TEXT_AND_DESCRIPTION, myActionWithoutTextAndDescription);
    actionManager.registerAction(ACTION_WITH_TEXT_ONLY, myActionWithTextOnly);
    actionManager.registerAction(ACTION_WITH_TEXT_AND_DESCRIPTION, myActionWithTextAndDescription);
    actionManager.registerAction(EXISTENT_ACTION, myActionExistent);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION, myActionWithUseShortcutOfExistent);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED, myActionWithUseShortcutOfExistentRedefined);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT, myActionWithUseShortcutOfExistentRedefinedInParent);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION, myActionWithUseShortcutOfNonExistent);
    actionManager.registerAction(ACTION_WITH_FIXED_SHORTCUTS, myActionWithFixedShortcuts);

    KeymapManagerEx.getInstanceEx().bindShortcuts(EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION);
    KeymapManagerEx.getInstanceEx().bindShortcuts(EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED);
    KeymapManagerEx.getInstanceEx().bindShortcuts(EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT);
    KeymapManagerEx.getInstanceEx().bindShortcuts(NON_EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION);

    mySavedRestrictions = ActionShortcutRestrictions.getInstance();
    setRestrictions(new ActionShortcutRestrictions(){
      @NotNull
      @Override
      public ShortcutRestrictions getForActionId(String actionId) {
        return ACTION_WITH_FIXED_SHORTCUTS.equals(actionId)
               ? new ShortcutRestrictions(false, false, false, false, false, false) : ShortcutRestrictions.NO_RESTRICTIONS;
      }
    });

    assertEquals("$Delete", KeymapManagerEx.getInstanceEx().getActionBinding(ACTION_EDITOR_DELETE_WITH_SHORTCUT));
    assertEquals("$Cut", KeymapManagerEx.getInstanceEx().getActionBinding(ACTION_EDITOR_CUT_WITHOUT_SHORTCUT));
    assertNotNull(actionManager.getAction(ACTION_EDITOR_DELETE_WITH_SHORTCUT));
    assertNotNull(actionManager.getAction(ACTION_EDITOR_CUT_WITHOUT_SHORTCUT));

    DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_EDITOR);
    group.addAll(myActionWithoutTextAndDescription,
                 myActionWithTextOnly,
                 myActionWithTextAndDescription,
                 myActionExistent,
                 myActionWithUseShortcutOfExistent,
                 myActionWithUseShortcutOfExistentRedefined,
                 myActionWithUseShortcutOfExistentRedefinedInParent,
                 myActionWithUseShortcutOfNonExistent,
                 myActionWithFixedShortcuts);
    // populate action tree
    myActionsTree = new ActionsTree();

    KeyboardShortcut shortcut1 = new KeyboardShortcut(KeyStroke.getKeyStroke('1'), null);
    KeyboardShortcut shortcut2 = new KeyboardShortcut(KeyStroke.getKeyStroke('2'), null);
    KeymapImpl parent = new KeymapImpl();
    parent.addShortcut(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT, shortcut1);
    parent.setName("parent");
    parent.setCanModify(false);
    KeymapImpl child = parent.deriveKeymap("child");
    child.addShortcut(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED, shortcut2);
    child.addShortcut(ACTION_EDITOR_DELETE_WITH_SHORTCUT, shortcut2);
    myActionsTree.reset(child, new QuickList[0]);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (mySavedRestrictions != null) {
        setRestrictions(mySavedRestrictions);
      }

      ActionManager actionManager = ActionManager.getInstance();
      DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_EDITOR);
      group.remove(myActionWithoutTextAndDescription);
      group.remove(myActionWithTextOnly);
      group.remove(myActionWithTextAndDescription);
      group.remove(myActionExistent);
      group.remove(myActionWithUseShortcutOfExistent);
      group.remove(myActionWithUseShortcutOfExistentRedefined);
      group.remove(myActionWithUseShortcutOfExistentRedefinedInParent);
      group.remove(myActionWithUseShortcutOfNonExistent);
      group.remove(myActionWithFixedShortcuts);
      actionManager.unregisterAction(ACTION_WITHOUT_TEXT_AND_DESCRIPTION);
      actionManager.unregisterAction(ACTION_WITH_TEXT_ONLY);
      actionManager.unregisterAction(ACTION_WITH_TEXT_AND_DESCRIPTION);
      actionManager.unregisterAction(EXISTENT_ACTION);
      actionManager.unregisterAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION);
      actionManager.unregisterAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED);
      actionManager.unregisterAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT);
      actionManager.unregisterAction(ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION);
      actionManager.unregisterAction(ACTION_WITH_FIXED_SHORTCUTS);

      ((KeymapManagerImpl)KeymapManager.getInstance()).unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION);
      ((KeymapManagerImpl)KeymapManager.getInstance()).unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED);
      ((KeymapManagerImpl)KeymapManager.getInstance()).unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT);
      ((KeymapManagerImpl)KeymapManager.getInstance()).unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION);
    }
    finally {
      super.tearDown();
    }
  }

  private static void setRestrictions(ActionShortcutRestrictions restrictions) {
    MutablePicoContainer picoContainer = (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer();
    String restrictionsKey = ActionShortcutRestrictions.class.getName();
    picoContainer.unregisterComponent(restrictionsKey);
    picoContainer.registerComponentInstance(restrictionsKey, restrictions);
  }

  public void testVariousActionsArePresent() {
    doTest(null,
           Arrays.asList(
             ACTION_WITHOUT_TEXT_AND_DESCRIPTION,
             ACTION_WITH_TEXT_ONLY,
             ACTION_WITH_TEXT_AND_DESCRIPTION,
             EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT,
             ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION,
             ACTION_EDITOR_DELETE_WITH_SHORTCUT), // this action is shown, since the keymap redefines the shortcut of $Delete
           Arrays.asList(
             NON_EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION,
             ACTION_EDITOR_CUT_WITHOUT_SHORTCUT, // this one is not shown since bound to $cut
             ACTION_WITH_FIXED_SHORTCUTS
           )
    );
  }

  public void testPresentation() {
    ActionManager manager = ActionManager.getInstance();

    List<String> failures = new SmartList<>();
    for (String id : manager.getActionIds("")) {
      if (!ACTION_WITHOUT_TEXT_AND_DESCRIPTION.equals(id)) {
        try {
          AnAction stub = manager.getActionOrStub(id);
          AnAction action = manager.getAction(id);
          String message = id + " (" + action.getClass().getName() + ")";
          if (stub != action) {
            Presentation before = stub.getTemplatePresentation();
            Presentation after = action.getTemplatePresentation();
            checkPresentationProperty("icon", message, before.getIcon(), after.getIcon());
            checkPresentationProperty("text", message, before.getText(), after.getText());
            checkPresentationProperty("description", message, before.getDescription(), after.getDescription());
          }
          if (action instanceof ActionGroup) {
            System.out.println("ignored action group: " + message);
          }
          else if (StringUtil.isEmpty(action.getTemplatePresentation().getText())) {
            failures.add("no text: " + message);
          }
        }
        catch (PluginException exception) {
          System.out.println(id + " ignored because " + exception.getMessage());
        }
      }
    }

    assertEmpty(failures);
  }

  private static void checkPresentationProperty(String name, String message, Object expected, Object actual) {
    if (!(expected == null ? actual == null : expected.equals(actual))) {
      System.out.println(name + " updated: "+ message + "; old:" + expected + "; new:" + actual);
    }
  }

  public void testFiltering() {
    doTest("Dummy",
           // all below actions should still be present, as they contain 'Dummy' in their actionId
           Arrays.asList(
             ACTION_WITHOUT_TEXT_AND_DESCRIPTION,
             ACTION_WITH_TEXT_ONLY,
             ACTION_WITH_TEXT_AND_DESCRIPTION,
             EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT,
             ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION),
           Arrays.asList(
             NON_EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION,
             ACTION_EDITOR_DELETE_WITH_SHORTCUT,
             ACTION_EDITOR_CUT_WITHOUT_SHORTCUT,
             ACTION_WITH_FIXED_SHORTCUTS
           )
    );
  }

  private void doTest(String filter, List<String> idsThatMustBePresent, List<String> idsThatMustNotBePresent) {
    if (filter != null) {
      myActionsTree.filter(filter, new QuickList[0]);
    }

    List<String> missing = new ArrayList<>();
    List<String> present = new ArrayList<>();
    for (String actionId : idsThatMustBePresent) {
      if (!myActionsTree.getMainGroup().containsId(actionId)) missing.add(actionId);
    }
    for (String actionId : idsThatMustNotBePresent) {
      if (myActionsTree.getMainGroup().containsId(actionId)) present.add(actionId);
    }
    assertTrue("Missing actions: " + missing + "\nWrongly shown: " + present,
               missing.isEmpty() && present.isEmpty());
  }

  private static class MyAction extends AnAction {
    private MyAction(@Nullable String text, @Nullable String description) {
      super(text, description, null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  }
}
