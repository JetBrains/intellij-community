// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.Pair;
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
    testTabPlacement(SwingConstants.TOP);
  }

  public void testSwitcherPanelWithoutTabs() {
    testTabPlacement(UISettings.TABS_NONE);
  }

  private void testTabPlacement(int tabPlacement) {
    UISettings.getInstance().getState().setEditorTabPlacement(tabPlacement);
    myManager.openFile(getFile("/src/1.txt"), true);
    myManager.openFile(getFile("/src/2.txt"), true);
    myManager.openFile(getFile("/src/3.txt"), true);
    Pair<List<Switcher.FileInfo>, Integer> filesToShowAndSelectionIndex =
      Switcher.SwitcherPanel
        .getFilesToShowAndSelectionIndex(getProject(), Switcher.SwitcherPanel.collectFiles(getProject(), false), 10, true);

    if (tabPlacement == UISettings.TABS_NONE) {
      assertEquals(1, filesToShowAndSelectionIndex.second.intValue());
      assertEquals(3, filesToShowAndSelectionIndex.first.size());
      assertEquals(getFile("/src/3.txt"), filesToShowAndSelectionIndex.first.get(0).first);
      assertEquals(getFile("/src/2.txt"), filesToShowAndSelectionIndex.first.get(1).first);
      assertEquals(getFile("/src/1.txt"), filesToShowAndSelectionIndex.first.get(2).first);
    } else {
      assertEquals(0, filesToShowAndSelectionIndex.second.intValue());
      assertEquals(2, filesToShowAndSelectionIndex.first.size());
      assertEquals(getFile("/src/2.txt"), filesToShowAndSelectionIndex.first.get(0).first);
      assertEquals(getFile("/src/1.txt"), filesToShowAndSelectionIndex.first.get(1).first);
    }
  }
}
