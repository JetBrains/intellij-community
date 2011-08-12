package com.intellij.openapi.vcs.changes;

import javax.swing.*;

/**
 * @author irengrig
 *         Date: 8/12/11
 *         Time: 6:51 PM
 */
public class NotRefreshablePanel implements RefreshablePanel {
  private final JPanel myPanel;

  public NotRefreshablePanel(JPanel panel) {
    myPanel = panel;
  }

  @Override
  public void refresh() {
  }

  @Override
  public JPanel getPanel() {
    return myPanel;
  }
}
