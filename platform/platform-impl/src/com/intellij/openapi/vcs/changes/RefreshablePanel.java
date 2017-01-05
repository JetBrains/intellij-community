package com.intellij.openapi.vcs.changes;

import javax.swing.*;

public interface RefreshablePanel {
  void refresh();
  JPanel getPanel();
}
