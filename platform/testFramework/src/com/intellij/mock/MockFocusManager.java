// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import javax.swing.FocusManager;
import java.awt.Component;

public final class MockFocusManager extends FocusManager {
  private final Component myFocusOwner;

  public MockFocusManager(Component focusOwner) {
    myFocusOwner = focusOwner;
  }

  @Override
  public Component getFocusOwner() {
    return myFocusOwner;
  }
}
