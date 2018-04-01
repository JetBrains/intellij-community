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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public class VcsBackgroundableComputable<T> extends Task.Backgroundable {
  private final String myErrorTitle;

  private boolean mySilent;
  @NotNull private final BackgroundableActionLock myLock;
  private final ThrowableComputable<T, VcsException> myBackgroundable;
  private final Consumer<T> myAwtSuccessContinuation;

  private VcsException myException;
  private T myResult;

  public VcsBackgroundableComputable(final Project project, final String title,
                                     final String errorTitle,
                                     final ThrowableComputable<T, VcsException> backgroundable,
                                     final Consumer<T> awtSuccessContinuation,
                                     @NotNull BackgroundableActionLock lock) {
    super(project, title, true);
    myErrorTitle = errorTitle;
    myBackgroundable = backgroundable;
    myAwtSuccessContinuation = awtSuccessContinuation;
    myLock = lock;
  }

  public void run(@NotNull ProgressIndicator indicator) {
    try {
      myResult = myBackgroundable.compute();
    }
    catch (VcsException e) {
      myException = e;
    }
  }

  @Override
  public void onCancel() {
    commonFinish();
  }

  @Override
  public void onSuccess() {
    commonFinish();
    if (myException == null) {
      if (myAwtSuccessContinuation != null) {
        myAwtSuccessContinuation.consume(myResult);
      }
    }
  }

  @Override
  public void onThrowable(@NotNull Throwable error) {
    myException = new VcsException(error);
    commonFinish();
  }

  private void commonFinish() {
    myLock.unlock();

    if ((!mySilent) && (myException != null)) {
      AbstractVcsHelper.getInstance(getProject()).showError(myException, myErrorTitle);
    }
  }

  public void setSilent(boolean silent) {
    mySilent = silent;
  }
}
