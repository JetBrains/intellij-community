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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CommitLegendPanel {

  @NotNull private final SimpleColoredComponent myRootPanel;
  @NotNull private final InfoCalculator myInfoCalculator;

  public CommitLegendPanel(@NotNull InfoCalculator infoCalculator) {
    myInfoCalculator = infoCalculator;
    myRootPanel = new SimpleColoredComponent();
  }

  @NotNull
  public JComponent getComponent() {
    return myRootPanel;
  }

  public void update() {
    myRootPanel.clear();
    appendText(myInfoCalculator.getNew(), myInfoCalculator.getIncludedNew(), FileStatus.ADDED, VcsBundle.message("commit.legend.new"));
    appendText(myInfoCalculator.getModified(), myInfoCalculator.getIncludedModified(), FileStatus.MODIFIED, VcsBundle.message("commit.legend.modified"));
    appendText(myInfoCalculator.getDeleted(), myInfoCalculator.getIncludedDeleted(), FileStatus.DELETED, VcsBundle.message("commit.legend.deleted"));
    appendText(myInfoCalculator.getUnversioned(), myInfoCalculator.getIncludedUnversioned(), FileStatus.UNKNOWN,
               VcsBundle.message("commit.legend.unversioned"));
  }

  protected void appendText(int total, int included, @NotNull FileStatus fileStatus, @NotNull String labelName) {
    if (total > 0) {
      if (!isPanelEmpty()) {
        appendSpace();
      }
      String pattern = total == included ? "%s %d" : "%s %d of %d";
      String text = String.format(pattern, labelName, included, total);
      myRootPanel.append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.getColor()));
    }
  }

  private boolean isPanelEmpty() {
    return !myRootPanel.iterator().hasNext();
  }

  protected final void appendSpace() {
    myRootPanel.append("   ");
  }

  public interface InfoCalculator {
    int getNew();
    int getModified();
    int getDeleted();
    int getUnversioned();
    int getIncludedNew();
    int getIncludedModified();
    int getIncludedDeleted();
    int getIncludedUnversioned();
  }
}
