package com.intellij.openapi.vcs.changes;

import javax.swing.JPanel;

public interface RefreshablePanel {
  void refresh();
  JPanel getPanel();
}
