// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
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
    myManager.openFile(getFile("/src/1.txt"), true);
    myManager.openFile(getFile("/src/2.txt"), true);
    myManager.openFile(getFile("/src/3.txt"), true);
    List<?> filesToShow = Switcher.SwitcherPanel.getFilesToShowForTest(getProject());
    int selectedItem = Switcher.SwitcherPanel.getFilesSelectedIndexForTest(getProject(), goForward);

    assertEquals(goForward ? 1 : 2, selectedItem);
    assertEquals(3, filesToShow.size());
    assertEquals(getFile("/src/3.txt"), filesToShow.get(0));
    assertEquals(getFile("/src/2.txt"), filesToShow.get(1));
    assertEquals(getFile("/src/1.txt"), filesToShow.get(2));
  }
}
