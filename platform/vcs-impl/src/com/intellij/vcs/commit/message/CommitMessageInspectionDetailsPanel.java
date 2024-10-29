// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public class CommitMessageInspectionDetailsPanel {
  private JPanel mySeverityChooserPanel;
  private JPanel myMainPanel;

  public CommitMessageInspectionDetailsPanel(@NotNull JComponent severityPanel,
                                             @Nullable JComponent optionsPanel) {
    mySeverityChooserPanel.add(severityPanel, BorderLayout.CENTER);
    if (optionsPanel != null) {
      myMainPanel.add(optionsPanel, createOptionsPanelConstraints());
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myMainPanel;
  }

  @NotNull
  private static GridConstraints createOptionsPanelConstraints() {
    GridConstraints result = new GridConstraints();

    result.setRow(1);
    result.setColumn(0);
    result.setRowSpan(1);
    result.setColSpan(2);
    result.setAnchor(GridConstraints.ANCHOR_NORTHWEST);
    result.setUseParentLayout(true);

    return result;
  }
}
