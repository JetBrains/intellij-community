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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  public void determinate() {
    if (myIndicator != null) {
      myIndicator.setIndeterminate(false);
    }
  }

  public void indeterminate() {
    if (myIndicator != null) {
      myIndicator.setIndeterminate(true);
    }
  }

  public void accept(@NotNull final Change change) {
    acceptImpl(ChangesUtil.getFilePath(change).getPath());
  }

  public void accept(@NotNull final FilePath filePath) {
    acceptImpl(filePath.getPath());
  }

  public void accept(final List<FilePath> paths) {
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

  public void accept(final File file) {
    acceptImpl(file.getAbsolutePath());
  }

  public void accept(final VirtualFile file) {
    acceptImpl(new File(file.getPath()).getAbsolutePath());
  }

  public void checkCanceled() {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
    }
  }
}
