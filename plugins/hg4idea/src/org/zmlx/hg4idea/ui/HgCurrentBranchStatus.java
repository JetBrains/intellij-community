// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcsMessages;

import javax.swing.*;

public class HgCurrentBranchStatus extends JLabel implements CustomStatusBarWidget {
  private static final Icon MERCURIAL_ICON = IconLoader.getIcon("/images/mercurial.png");

  public HgCurrentBranchStatus() {
    super(MERCURIAL_ICON, SwingConstants.TRAILING);
    setVisible(false);
  }

  public void setCurrentBranch(String branch) {
    String statusText = StringUtils.isNotBlank(branch)
      ? HgVcsMessages.message("hg4idea.status.currentBranch.text", branch) : "";

    String toolTipText = StringUtils.isNotBlank(branch)
      ? HgVcsMessages.message("hg4idea.status.currentBranch.description") : "";

    setVisible(StringUtils.isNotBlank(branch));
    setText(statusText);
    setToolTipText(toolTipText);
  }

  public JComponent getComponent() {
    return this;
  }

  @NotNull
  public String ID() {
    return "HgCurrentBranchStatus";
  }

  public Presentation getPresentation(@NotNull Type type) {
    return null;
  }

  public void install(@NotNull StatusBar statusBar) {
  }

  public void dispose() {
  }
}
