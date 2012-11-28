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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/28/12
 * Time: 3:53 PM
 */
public class LimitHistoryCheck {
  private final Project myProject;
  private final String myFilePath;
  private int myLimit;
  private int myCnt;
  private boolean myWarningShown;
  private boolean myOver;

  public LimitHistoryCheck(final Project project, String filePath) {
    myProject = project;
    myFilePath = filePath;
    myWarningShown = false;
    init();
  }

  private void init() {
    final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    myLimit = configuration.LIMIT_HISTORY ? configuration.MAXIMUM_HISTORY_ROWS : -1;
    myCnt = 0;
  }

  public void checkNumber() {
    if (myLimit <= 0) return;
    ++ myCnt;
    if (isOver()) {
      if (! myWarningShown) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, "File History: only " + myLimit + " revisions were loaded for " + myFilePath +
          "\nTo change the history limit, go to Settings | Version Control.", MessageType.WARNING);
        myWarningShown = true;
      }
      throw new ProcessCanceledException();
    }
  }

  public void reset() {
    init();
  }

  public boolean isOver() {
    return myLimit > 0 && myLimit < myCnt;
  }
}
