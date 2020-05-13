// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.components.JBList;

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
    List<Switcher.FileInfo> filesToShow = Switcher.SwitcherPanel.getFilesToShow(getProject(), Switcher.SwitcherPanel.collectFiles(getProject(), false), 10, true);
    JList<Switcher.FileInfo> list = new JBList<>(filesToShow);
    int selectedItem = Switcher.SwitcherPanel.getFilesSelectedIndex(getProject(), list, goForward);

    if (tabPlacement == UISettings.TABS_NONE) {
      assertEquals(goForward ? 1 : 2, selectedItem);
      assertEquals(3, filesToShow.size());
      assertEquals(getFile("/src/3.txt"), filesToShow.get(0).first);
      assertEquals(getFile("/src/2.txt"), filesToShow.get(1).first);
      assertEquals(getFile("/src/1.txt"), filesToShow.get(2).first);
    } else {
      assertEquals(goForward ? 0 : 1, selectedItem);
      assertEquals(2, filesToShow.size());
      assertEquals(getFile("/src/2.txt"), filesToShow.get(0).first);
      assertEquals(getFile("/src/1.txt"), filesToShow.get(1).first);
    }
  }
}
