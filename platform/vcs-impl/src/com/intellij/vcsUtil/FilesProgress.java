// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

@ApiStatus.Internal
public class FilesProgress {
  private final double myTotal;
  private final @Nls String myPrefix;
  private final ProgressIndicator myProgressIndicator;
  private int myCnt;
  private boolean myInText2;

  public FilesProgress(double total, @Nls String prefix) {
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

  private static @Nls String getFileDescriptionForProgress(final VirtualFile file) {
    final VirtualFile parent = file.getParent();
    return file.getName() + " (" + (parent == null ? file.getPresentableUrl() : parent.getPresentableUrl()) + ")";
  }

  public void setInText2(boolean inText2) {
    myInText2 = inText2;
  }
}
