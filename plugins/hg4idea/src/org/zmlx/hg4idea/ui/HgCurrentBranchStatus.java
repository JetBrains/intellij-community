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

import com.intellij.openapi.util.*;
import org.apache.commons.lang.*;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.*;

import javax.swing.*;
import java.util.*;

public class HgCurrentBranchStatus extends JLabel {

  private static final Icon MERCURIAL_ICON = IconLoader.getIcon("/images/mercurial.png");

  public HgCurrentBranchStatus() {
    super(MERCURIAL_ICON, SwingConstants.TRAILING);
    setVisible(false);
  }

  public void updateFor(@Nullable String branch, @NotNull List<HgRevisionNumber> parents) {
    StringBuffer parentsBuffer = new StringBuffer();
    for (HgRevisionNumber parent : parents) {
      String rev = parent.getRevision();
      parentsBuffer.append(rev).append(", ");
    }
    int length = parentsBuffer.length();
    if (length > 2) {
      parentsBuffer.delete(length - 2, length);
    }
    String statusText = StringUtils.isNotBlank(branch)
      ? HgVcsMessages.message("hg4idea.status.currentSituationtext", branch, parentsBuffer.toString()) : "";

    String toolTipText = StringUtils.isNotBlank(statusText)
      ? HgVcsMessages.message("hg4idea.status.currentSituation.description") : "";

    setVisible(StringUtils.isNotBlank(branch));
    setText(statusText);
    setToolTipText(toolTipText);
  }

}
