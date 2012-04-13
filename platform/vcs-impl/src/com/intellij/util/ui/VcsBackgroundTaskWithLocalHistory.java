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
package com.intellij.util.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 4/13/12
 * Time: 1:59 PM
 */
public abstract class VcsBackgroundTaskWithLocalHistory<T> extends VcsBackgroundTask<T> {
  private final String myActionName;

  protected VcsBackgroundTaskWithLocalHistory(Project project,
                                              @NotNull String title,
                                              @NotNull PerformInBackgroundOption backgroundOption,
                                              Collection<T> itemsToProcess,
                                              String actionName) {
    super(project, title, backgroundOption, itemsToProcess);
    myActionName = actionName;
  }

  protected VcsBackgroundTaskWithLocalHistory(Project project,
                                              @NotNull String title,
                                              @NotNull PerformInBackgroundOption backgroundOption,
                                              Collection<T> itemsToProcess,
                                              boolean canBeCanceled, String actionName) {
    super(project, title, backgroundOption, itemsToProcess, canBeCanceled);
    myActionName = actionName;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    LocalHistoryAction action = LocalHistoryAction.NULL;
    if (myActionName != null) {
      action = LocalHistory.getInstance().startAction(myActionName);
    }
    try {
      super.run(indicator);
    } finally {
      action.finish();
    }
  }
}
