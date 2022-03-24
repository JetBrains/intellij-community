// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ui.SwingActionDelegate;

public abstract class FileChooserPanelActions extends SwingActionDelegate {
  private FileChooserPanelActions(String actionId) {
    super(actionId);
  }

  public static final class Root extends FileChooserPanelActions {
    public static final String ID = "fileChooserRoot";

    public Root() {
      super(ID);
    }
  }

  public static final class LevelUp extends FileChooserPanelActions {
    public static final String ID = "fileChooserLevelUp";

    public LevelUp() {
      super(ID);
    }
  }
}
