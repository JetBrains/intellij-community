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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.LinkedList;
import java.util.List;

public class SelectedFilesHelper implements Runnable {
  private final List<MyChecker> myCheckers;
  private final FileStatusManager myStatusManager;
  private final VirtualFile[] myData;
  private int myCnt;

  private SelectedFilesHelper(final Project project, final AnActionEvent e) {
    myStatusManager = FileStatusManager.getInstance(project);
    myData = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    myCheckers = new LinkedList<MyChecker>();
  }

  private void add(final MyChecker checker) {
    myCheckers.add(checker);
  }

  public static boolean hasChangedSelectedFiles(final Project project, final AnActionEvent e) {
    final SelectedFilesHelper helper = new SelectedFilesHelper(project, e);
    helper.add(new MyChangedChecker(helper));
    return helper.execute();
  }

  public static boolean hasChangedOrUnversionedFiles(final Project project, final AnActionEvent e) {
    final SelectedFilesHelper helper = new SelectedFilesHelper(project, e);
    helper.add(new MyChangedChecker(helper));
    helper.add(new MyUnversionedChecker(helper));
    return helper.execute();
  }

  private boolean execute() {
    if (myData != null && myData.length > 0) {
      myCnt = myCheckers.size();

      for (VirtualFile vf : myData) {
        final FileStatus status = myStatusManager.getStatus(vf);
        for (MyChecker checker : myCheckers) {
          checker.execute(status);
        }
        if (myCnt <= 0) break;
      }
      boolean result = false;
      for (MyChecker checker : myCheckers) {
        result |= checker.isFound();
      }
      return result;
    }
    return false;
  }

  public void run() {
    -- myCnt;
  }

  private static class MyUnversionedChecker extends MyChecker {
    private MyUnversionedChecker(Runnable finishedListener) {
      super(finishedListener);
    }

    @Override
    protected boolean check(FileStatus status) {
      return FileStatus.UNKNOWN.equals(status);
    }
  }

  private static class MyChangedChecker extends MyChecker {
    private MyChangedChecker(Runnable finishedListener) {
      super(finishedListener);
    }

    @Override
    protected boolean check(final FileStatus status) {
      return ! (FileStatus.UNKNOWN.equals(status) || FileStatus.NOT_CHANGED.equals(status) || FileStatus.IGNORED.equals(status));
    }
  }

  private static abstract class MyChecker {
    private boolean myFound;
    private final Runnable myFinishedListener;

    protected MyChecker(final Runnable finishedListener) {
      myFinishedListener = finishedListener;
    }

    protected abstract boolean check(final FileStatus status);

    public void execute(final FileStatus status) {
      if (myFound) return;
      if (check(status)) {
        myFound = true;
        myFinishedListener.run();
      }
    }

    public boolean isFound() {
      return myFound;
    }
  }

  /*public static boolean hasChangedFiles(final Project project, final AnActionEvent e) {
    final VirtualFile[] virtualFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length > 0) {
      final FileStatusManager statusManager = FileStatusManager.getInstance(project);
      for (VirtualFile vf : virtualFiles) {
        final FileStatus status = statusManager.getStatus(vf);
        if (! (FileStatus.UNKNOWN.equals(status) || FileStatus.NOT_CHANGED.equals(status) || FileStatus.IGNORED.equals(status))) {
          // versioned && changed
          return true;
        }
      }
    }
    return false;
  }*/
}
