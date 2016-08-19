/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public abstract class VcsBackgroundTask<T> extends Task.ConditionalModal {
  private final Collection<T> myItems;
  private final List<VcsException> myExceptions = new ArrayList<>();

  public VcsBackgroundTask(final Project project, @NotNull final String title, @NotNull final PerformInBackgroundOption backgroundOption,
                           final Collection<T> itemsToProcess, final boolean canBeCanceled) {
    super(project, title, canBeCanceled, backgroundOption);
    myItems = itemsToProcess;
  }

  public VcsBackgroundTask(final Project project, @NotNull final String title, @NotNull final PerformInBackgroundOption backgroundOption,
                           final Collection<T> itemsToProcess) {
    this(project, title, backgroundOption, itemsToProcess, false);
  }

  public void run(@NotNull ProgressIndicator indicator) {
    for(T item: myItems) {
      try {
        process(item);
      }
      catch(VcsException ex) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          throw new RuntimeException(ex);
        }
        myExceptions.add(ex);
      }
    }
  }

  protected boolean executedOk() {
    return myExceptions.isEmpty();
  }

  public void onSuccess() {
    if (!myExceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, myTitle);
    }
  }

  protected abstract void process(T item) throws VcsException;
}
