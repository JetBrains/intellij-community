/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.vcsUtil;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author irengrig
 *         Date: 4/8/11
 *         Time: 5:15 PM
 */
public class FilesProgress {
  private final double myTotal;
  private final String myPrefix;
  private final ProgressIndicator myProgressIndicator;
  private int myCnt;
  private boolean myInText2;

  public FilesProgress(double total, final String prefix) {
    myTotal = total;
    myPrefix = prefix;
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    myCnt = 0;
    myInText2 = false;
  }

  public void updateIndicator(final VirtualFile vf) {
    if (myProgressIndicator == null) return;
    myProgressIndicator.checkCanceled();
    if (myInText2) {
      myProgressIndicator.setText2(myPrefix + getFileDescriptionForProgress(vf));
    } else {
      myProgressIndicator.setText(myPrefix + getFileDescriptionForProgress(vf));
    }
    myProgressIndicator.setFraction(myCnt/myTotal);
    ++ myCnt;
  }

  private static String getFileDescriptionForProgress(final VirtualFile file) {
    final VirtualFile parent = file.getParent();
    return file.getName() + " (" + (parent == null ? file.getPath() : parent.getPath()) + ")";
  }

  public void setInText2(boolean inText2) {
    myInText2 = inText2;
  }
}
