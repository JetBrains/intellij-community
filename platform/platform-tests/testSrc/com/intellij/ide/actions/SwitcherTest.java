// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import javax.swing.*;
import java.util.List;

public class SwitcherTest extends FileEditorManagerTestCase {
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "fileEditorManager";
  }

  public void testSwitcherPanelWithTabs() {
    testTabPlacement(SwingConstants.TOP, true);
    testTabPlacement(SwingConstants.TOP, false);
  }

  public void testSwitcherPanelWithoutTabs() {
    testTabPlacement(UISettings.TABS_NONE, true);
    testTabPlacement(UISettings.TABS_NONE, false);
  }

  private void testTabPlacement(int tabPlacement, boolean goForward) {
    UISettings.getInstance().getState().setEditorTabPlacement(tabPlacement);
    manager.openFile(getFile("/src/1.txt"), null, new FileEditorOpenOptions().withRequestFocus());
    manager.openFile(getFile("/src/2.txt"), null, new FileEditorOpenOptions().withRequestFocus());
    manager.openFile(getFile("/src/3.txt"), null, new FileEditorOpenOptions().withRequestFocus());
    List<?> filesToShow = Switcher.SwitcherPanel.getFilesToShowForTest(getProject());
    int selectedItem = Switcher.SwitcherPanel.getFilesSelectedIndexForTest(getProject(), goForward);

    assertEquals(goForward ? 1 : 2, selectedItem);
    assertEquals(3, filesToShow.size());
    assertEquals(getFile("/src/3.txt"), filesToShow.get(0));
    assertEquals(getFile("/src/2.txt"), filesToShow.get(1));
    assertEquals(getFile("/src/1.txt"), filesToShow.get(2));
  }
}
