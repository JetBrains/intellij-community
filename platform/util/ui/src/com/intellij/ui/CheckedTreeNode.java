// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import javax.swing.tree.DefaultMutableTreeNode;

public class CheckedTreeNode extends DefaultMutableTreeNode {
  protected boolean isChecked = true;
  private boolean isEnabled = true;

  public CheckedTreeNode() {
  }

  public CheckedTreeNode(Object userObject) {
    super(userObject);
  }

  public boolean isChecked() {
    return isChecked;
  }


  public void setChecked(boolean checked) {
    isChecked = checked;
  }

  public void setEnabled(final boolean enabled) {
    isEnabled = enabled;
  }

  public boolean isEnabled() {
    return isEnabled;
  }
}
