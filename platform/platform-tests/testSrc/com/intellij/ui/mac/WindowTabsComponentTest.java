// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunInEdt
@TestApplication
public class WindowTabsComponentTest {
  @Test
  void insertingExistingFrameDoesNotCreateDuplicate(@TestDisposable Disposable disposable) {
    IdeFrameImpl firstFrame = new IdeFrameImpl();
    IdeFrameImpl secondFrame = new IdeFrameImpl();
    try {
      WindowTabsComponent tabs = new WindowTabsComponent(firstFrame, null, disposable);
      tabs.createTabsForFrame(new IdeFrameImpl[]{firstFrame, secondFrame});

      tabs.insertTabForFrame(secondFrame, 1);

      assertEquals(2, tabs.getTabCount());
    }
    finally {
      firstFrame.dispose();
      secondFrame.dispose();
    }
  }
}
