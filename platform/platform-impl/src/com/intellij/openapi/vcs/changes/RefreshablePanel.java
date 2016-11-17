package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;

import javax.swing.*;

public interface RefreshablePanel extends Disposable {
  void refresh();
  JPanel getPanel();
}
