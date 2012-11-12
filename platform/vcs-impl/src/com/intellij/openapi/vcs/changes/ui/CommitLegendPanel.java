/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * @author max
 */
public class CommitLegendPanel {
  private JLabel myModifiedShown;
  private JLabel myModifiedIncluded;
  private JLabel myNewShown;
  private JLabel myNewIncluded;
  private JLabel myDeletedIncluded;
  private JLabel myTotalShown;
  private JLabel myTotalIncluded;
  private JPanel myRootPanel;
  private JLabel myDeletedShown;
  private JPanel myModifiedPanel;
  private JLabel myModifiedLabel;
  private JPanel myNewPanel;
  private JLabel myNewLabel;
  private JPanel myDeletedPanel;
  private JLabel myDeletedLabel;

  private final InfoCalculator myInfoCalculator;

  public CommitLegendPanel(InfoCalculator infoCalculator) {
    myInfoCalculator = infoCalculator;
    final Color background = UIUtil.getListBackground();
    myModifiedPanel.setBackground(background);
    myNewPanel.setBackground(background);
    myDeletedPanel.setBackground(background);
    if (UIUtil.isUnderDarcula()) {
      final Color color = UIUtil.getSeparatorColor();
      myModifiedPanel.setBorder(new TitledBorder(new LineBorder(color, 1)));
      myNewPanel.setBorder(new TitledBorder(new LineBorder(color, 1)));
      myDeletedPanel.setBorder(new TitledBorder(new LineBorder(color, 1)));
    }

    myModifiedLabel.setForeground(FileStatus.MODIFIED.getColor());
    myNewLabel.setForeground(FileStatus.ADDED.getColor());
    myDeletedLabel.setForeground(FileStatus.DELETED.getColor());

    boldLabel(myModifiedLabel, true);
    boldLabel(myNewLabel, true);
    boldLabel(myDeletedLabel, true);
  }

  public JPanel getComponent() {
    return myRootPanel;
  }

  public void update() {
    final int deleted = myInfoCalculator.getDeleted();
    final int modified = myInfoCalculator.getModified();
    final int cntNew = myInfoCalculator.getNew();

    final int includedDeleted = myInfoCalculator.getIncludedDeleted();
    final int includedModified = myInfoCalculator.getIncludedModified();
    final int includedNew = myInfoCalculator.getIncludedNew();

    updateCategory(myTotalShown, myTotalIncluded, deleted + modified + cntNew, includedDeleted + includedModified + includedNew);
    updateCategory(myModifiedShown, myModifiedIncluded, modified, includedModified);
    updateCategory(myNewShown, myNewIncluded, cntNew, includedNew);
    updateCategory(myDeletedShown, myDeletedIncluded, deleted, includedDeleted);
  }

  private static void updateCategory(JLabel totalLabel,
                                     JLabel includedLabel,
                                     int totalCnt,
                                     int includedCnt) {
    updateLabel(totalLabel, totalCnt, false);
    updateLabel(includedLabel, includedCnt, totalCnt != includedCnt);
  }

  private static void updateLabel(JLabel label, int count, boolean bold) {
    label.setText(String.valueOf(count));
    label.setEnabled(bold || count != 0);
    boldLabel(label, bold);
  }

  private static void boldLabel(final JLabel label, final boolean bold) {
    int style = bold ? Font.BOLD : Font.PLAIN;
    if (label.getFont().getStyle() == style)
      return;
    label.setFont(label.getFont().deriveFont(style));
  }

  public interface InfoCalculator {
    int getNew();
    int getModified();
    int getDeleted();
    int getIncludedNew();
    int getIncludedModified();
    int getIncludedDeleted();
  }
}
