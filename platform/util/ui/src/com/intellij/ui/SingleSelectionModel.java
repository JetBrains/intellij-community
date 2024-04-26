// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import javax.swing.*;

public class SingleSelectionModel extends DefaultListSelectionModel {
  {
    setSelectionMode(SINGLE_SELECTION);
  }

  @Override
  public void clearSelection() {
  }

  @Override
  public void removeSelectionInterval(int index0, int index1) {
  }
}
