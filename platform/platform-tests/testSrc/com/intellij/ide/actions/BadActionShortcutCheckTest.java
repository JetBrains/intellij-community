// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.RunAll;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class BadActionShortcutCheckTest extends LightPlatformTestCase {
  private static final String MARKER = "ShortcutSet of global AnActions should not be changed outside of KeymapManager";

  private final List<String> myLoggedWarnings = ContainerUtil.createConcurrentList();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    LoggedErrorProcessor.setNewInstance(new LoggedErrorProcessor() {
      @Override
      public void processWarn(String message, Throwable t, @NotNull Logger logger) {
        super.processWarn(message, t, logger);
        myLoggedWarnings.add(message);
      }
    });
  }

  @Override
  public void tearDown() throws Exception {
    new RunAll(
      () -> myLoggedWarnings.clear(),
      () -> LoggedErrorProcessor.restoreDefaultProcessor(),
      () -> super.tearDown()
    ).run();
  }

  public void testActionCanChangeShortcut() {
    AnAction action1 = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) { }
    };
    AnAction action2 = ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE);
    JPanel component = new JPanel();

    action1.registerCustomShortcutSet(action2.getShortcutSet(), component);
    assertWarningShown(false);
  }

  public void testGlobalActionCantChangeShortcut1() {
    AnAction action1 = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY);
    AnAction action2 = ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE);
    JPanel component = new JPanel();

    action1.registerCustomShortcutSet(action2.getShortcutSet(), component);
    assertWarningShown(true);
  }

  public void testGlobalActionCantChangeShortcut2() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY);
    JPanel component = new JPanel();

    action.registerCustomShortcutSet(CustomShortcutSet.EMPTY, component);
    assertWarningShown(true);
  }

  private void assertWarningShown(boolean isExpected) {
    boolean hasWarning = ContainerUtil.exists(myLoggedWarnings, message -> message.contains(MARKER));
    if (hasWarning != isExpected) {
      fail(myLoggedWarnings.toString());
    }
  }
}
