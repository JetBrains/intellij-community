// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public class RollbackProgressModifier implements RollbackProgressListener {
  private final Set<String> myTakenPaths;
  private final double myTotal;
  private final ProgressIndicator myIndicator;
  private int myCnt;

  public RollbackProgressModifier(final double total, final ProgressIndicator indicator) {
    myTotal = total;
    myIndicator = indicator;
    myTakenPaths = new HashSet<>();
    myCnt = 0;
  }

  private void acceptImpl(final String name) {
    if (myIndicator != null) {
      myIndicator.setText2(VcsBundle.message("rolling.back.file", name));
      checkName(name);
      if (! myIndicator.isIndeterminate()) {
        myIndicator.setFraction(myCnt / myTotal);
      }
      myIndicator.checkCanceled();
    }
  }

  private void checkName(final String name) {
    if (! myTakenPaths.contains(name)) {
      myTakenPaths.add(name);
      if (myTotal >= (myCnt + 1)) {
        ++ myCnt;
      }
    }
  }

  @Override
  public void determinate() {
    if (myIndicator != null) {
      myIndicator.setIndeterminate(false);
    }
  }

  @Override
  public void indeterminate() {
    if (myIndicator != null) {
      myIndicator.setIndeterminate(true);
    }
  }

  @Override
  public void accept(@NotNull final Change change) {
    acceptImpl(ChangesUtil.getFilePath(change).getPath());
  }

  @Override
  public void accept(@NotNull final FilePath filePath) {
    acceptImpl(filePath.getPath());
  }

  @Override
  public void accept(final List<? extends FilePath> paths) {
    if (myIndicator != null) {
      if (paths != null && (! paths.isEmpty())) {
        for (FilePath path : paths) {
          checkName(path.getPath());
        }
        myIndicator.setFraction(myCnt / myTotal);
        myIndicator.setText2(VcsBundle.message("rolling.back.file", paths.get(0).getPath()));
      }
    }
  }

  @Override
  public void accept(final File file) {
    acceptImpl(file.getAbsolutePath());
  }

  @Override
  public void accept(final VirtualFile file) {
    acceptImpl(new File(file.getPath()).getAbsolutePath());
  }

  @Override
  public void checkCanceled() {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
    }
  }
}
