// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.ShortcutRestrictions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.junit5.DynamicTests;
import com.intellij.testFramework.junit5.NamedFailure;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@TestApplication
public class ActionsTreeTest {
  private static final Logger LOG = Logger.getInstance(ActionsTreeTest.class);
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

  @BeforeEach
  void setUp(@TestDisposable Disposable testDisposable) {
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

    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    actionManager.registerAction(ACTION_WITHOUT_TEXT_AND_DESCRIPTION, myActionWithoutTextAndDescription);
    actionManager.registerAction(ACTION_WITH_TEXT_ONLY, myActionWithTextOnly);
    actionManager.registerAction(ACTION_WITH_TEXT_AND_DESCRIPTION, myActionWithTextAndDescription);
    actionManager.registerAction(EXISTENT_ACTION, myActionExistent);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION, myActionWithUseShortcutOfExistent);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED, myActionWithUseShortcutOfExistentRedefined);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT, myActionWithUseShortcutOfExistentRedefinedInParent);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION, myActionWithUseShortcutOfNonExistent);
    actionManager.registerAction(ACTION_WITH_FIXED_SHORTCUTS, myActionWithFixedShortcuts);

    actionManager.bindShortcuts(EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION);
    actionManager.bindShortcuts(EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED);
    actionManager.bindShortcuts(EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT);
    actionManager.bindShortcuts(NON_EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION);

    setRestrictions(testDisposable, new ActionShortcutRestrictions(){
      @NotNull
      @Override
      public ShortcutRestrictions getForActionId(String actionId) {
        return ACTION_WITH_FIXED_SHORTCUTS.equals(actionId)
               ? new ShortcutRestrictions(false, false, false, false, false, false) : ShortcutRestrictions.NO_RESTRICTIONS;
      }
    });

    assertEquals("$Delete", actionManager.getActionBinding(ACTION_EDITOR_DELETE_WITH_SHORTCUT));
    assertEquals("$Cut", actionManager.getActionBinding(ACTION_EDITOR_CUT_WITHOUT_SHORTCUT));
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
    // populate an action tree
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

  @AfterEach
  void tearDown() {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
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

    actionManager.unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION);
    actionManager.unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED);
    actionManager.unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT);
    actionManager.unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION);
  }

  private static void setRestrictions(@TestDisposable @NotNull Disposable testDisposable,
                                      @NotNull ActionShortcutRestrictions restrictions) {
    ServiceContainerUtil
      .replaceService(ApplicationManager.getApplication(), ActionShortcutRestrictions.class, restrictions,
                      testDisposable);
  }

  @Test
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
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION,
             ACTION_EDITOR_CUT_WITHOUT_SHORTCUT, // this one is shown since bound to $cut
             ACTION_EDITOR_DELETE_WITH_SHORTCUT), // this action is shown, since the keymap redefines the shortcut of $Delete
           Arrays.asList(
             NON_EXISTENT_ACTION,
             ACTION_WITH_FIXED_SHORTCUTS
           )
    );
  }

  @TestFactory
  public List<DynamicTest> testPresentation() {
    ActionManager manager = ActionManager.getInstance();

    List<NamedFailure> failures = new ArrayList<>();
    for (String id : ContainerUtil.sorted(manager.getActionIdList(""))) {
      if (ACTION_WITHOUT_TEXT_AND_DESCRIPTION.equals(id)) {
        continue;
      }

      try {
        AnAction stub = manager.getActionOrStub(id);
        AnAction action = manager.getAction(id);
        String actionIdAndClass = "'"+id + "' (" + action.getClass() + ")";
        if (stub != action) {
          Presentation before = stub.getTemplatePresentation();
          Presentation after = action.getTemplatePresentation();
          checkPresentationProperty("icon", actionIdAndClass, before.getIcon(), after.getIcon());
          checkPresentationProperty("text", actionIdAndClass, before.getText(), after.getText());
          checkPresentationProperty("description", actionIdAndClass, before.getDescription(), after.getDescription());
        }

        if (action instanceof ActionGroup || action instanceof DecorativeElement) {
          LOG.debug("ignored action group or separator: " + actionIdAndClass);
        }
        else if (StringUtil.isEmpty(action.getTemplatePresentation().getText())) {
          String message = "no text is defined for template presentation of " +
                           actionIdAndClass +
                           "; even if text is set in 'update' method the internal ID will be shown in Settings | Keymap";
          failures.add(new NamedFailure("no text defined for " + id, message));
        }
      }
      catch (PluginException exception) {
        LOG.debug(id + " ignored because " + exception.getMessage());
      }
    }

    return DynamicTests.asDynamicTests(failures, "incorrect presentations");
  }

  private static void checkPresentationProperty(String name, String message, Object expected, Object actual) {
    if (!Objects.equals(expected, actual)) {
      LOG.debug(name + " updated: "+ message + "; old:" + expected + "; new:" + actual);
    }
  }

  @Test
  public void testFiltering() {
    doTest("Dummy",
           // all below actions should still be present, as they contain 'Dummy' in their actionId
           Arrays.asList(
             ACTION_WITHOUT_TEXT_AND_DESCRIPTION,
             ACTION_WITH_TEXT_ONLY,
             ACTION_WITH_TEXT_AND_DESCRIPTION,
             EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION_REDEFINED_IN_PARENT,
             ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION),
           Arrays.asList(
             NON_EXISTENT_ACTION,
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
    assertTrue(missing.isEmpty() && present.isEmpty(),
               "Missing actions: " + missing + "\nWrongly shown: " + present);
  }

  private static final class MyAction extends AnAction {
    private MyAction(@Nullable String text, @Nullable String description) {
      super(text, description, null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  }
}
