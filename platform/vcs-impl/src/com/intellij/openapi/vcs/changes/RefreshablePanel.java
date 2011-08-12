package com.intellij.openapi.vcs.changes;

import javax.swing.*;

/**
 * @author irengrig
 *         Date: 8/12/11
 *         Time: 6:47 PM
 */
public interface RefreshablePanel {
  void refresh();
  JPanel getPanel();
}
