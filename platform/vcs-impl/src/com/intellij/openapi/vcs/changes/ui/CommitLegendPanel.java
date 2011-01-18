/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
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
  private JPanel myHeadingPanel;

  private final InfoCalculator myInfoCalculator;

  public CommitLegendPanel(InfoCalculator infoCalculator) {
    myInfoCalculator = infoCalculator;
    final Color background = UIUtil.getListBackground();
    myModifiedPanel.setBackground(background);
    myNewPanel.setBackground(background);
    myDeletedPanel.setBackground(background);

    myModifiedLabel.setForeground(FileStatus.MODIFIED.getColor());
    myModifiedLabel.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));
    myNewLabel.setForeground(FileStatus.ADDED.getColor());
    myNewLabel.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));
    myDeletedLabel.setForeground(FileStatus.DELETED.getColor());
    myDeletedLabel.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));

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

  private void createUIComponents() {
    myHeadingPanel = (JPanel)SeparatorFactory.createSeparator(VcsBundle.message("commit.legend.summary"), null);
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
    label.setFont(label.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
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
