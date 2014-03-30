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
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

/**
 * @author max
 */
public class CommitLegendPanel {

  private final SimpleColoredComponent myRootPanel;
  private final InfoCalculator myInfoCalculator;

  public CommitLegendPanel(InfoCalculator infoCalculator) {
    myInfoCalculator = infoCalculator;
    myRootPanel = new SimpleColoredComponent();
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  public void update() {
    final int deleted = myInfoCalculator.getDeleted();
    final int modified = myInfoCalculator.getModified();
    final int cntNew = myInfoCalculator.getNew();

    myRootPanel.clear();
    if (cntNew > 0) {
      appendText(cntNew, myInfoCalculator.getIncludedNew(), FileStatus.ADDED, "commit.legend.new");
      if (modified > 0 || deleted > 0) {
        appendSpace();
      }
    }
    if (modified > 0) {
      appendText(modified, myInfoCalculator.getIncludedModified(), FileStatus.MODIFIED, "commit.legend.modified");
      if (deleted > 0) {
        appendSpace();
      }
    }
    if (deleted > 0) {
      appendText(deleted, myInfoCalculator.getIncludedDeleted(), FileStatus.DELETED, "commit.legend.deleted");
    }
  }

  private void appendText(int total, int included, FileStatus fileStatus, String labelKey) {
    String pattern = total == included ? "%s %d" : "%s %d of %d";
    String text = String.format(pattern, VcsBundle.message(labelKey), included, total);
    myRootPanel.append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.getColor()));
  }

  private void appendSpace() {
    myRootPanel.append("   ");
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
