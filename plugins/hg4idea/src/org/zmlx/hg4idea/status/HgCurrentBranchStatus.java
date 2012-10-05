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
package org.zmlx.hg4idea.status;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcsMessages;

import java.util.List;

public class HgCurrentBranchStatus {

	private final HgUpdater toUpdate;

	private String text;
	private String toolTip;


  public HgCurrentBranchStatus( HgUpdater toUpdate ) {
	  this.toUpdate = toUpdate;
  }

  public void updateFor(
	  Project project,
	  @Nullable String branch,
	  @NotNull List<HgRevisionNumber> parents
  ) {
    StringBuffer parentsBuffer = new StringBuffer();
    for (HgRevisionNumber parent : parents) {
      String rev = parent.getRevision();
      parentsBuffer.append(rev).append(", ");
    }
    int length = parentsBuffer.length();
    if (length > 2) {
      parentsBuffer.delete(length - 2, length);
    }
    String statusText = !StringUtil.isEmptyOrSpaces(branch)
      ? HgVcsMessages.message( "hg4idea.status.currentSituationText", branch, parentsBuffer.toString()) : "";

    String toolTipText = !StringUtil.isEmptyOrSpaces(statusText)
      ? HgVcsMessages.message("hg4idea.status.currentSituation.description") : "";

    text = statusText;
    toolTip = toolTipText;

	  toUpdate.update( project );
  }


	public String getStatusText() {
		return text;
	}


	public String getToolTipText() {
		return toolTip;
	}

}
