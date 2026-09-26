// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public abstract class VcsBackgroundTask<T> extends Task.Backgroundable {
  private final Collection<? extends T> myItems;
  private final List<VcsException> myExceptions = new ArrayList<>();

  public VcsBackgroundTask(Project project,
                           @NotNull @NlsContexts.ProgressTitle String title,
                           Collection<? extends T> itemsToProcess,
                           boolean canBeCanceled) {
    super(project, title, canBeCanceled);
    myItems = itemsToProcess;
  }

  public VcsBackgroundTask(Project project,
                           @NotNull @NlsContexts.ProgressTitle String title,
                           Collection<? extends T> itemsToProcess) {
    this(project, title, itemsToProcess, false);
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    for (T item : myItems) {
      try {
        process(item);
      }
      catch (VcsException ex) {
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

  @Override
  public void onSuccess() {
    if (!myExceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(getProject()).showErrors(myExceptions, getTitle());
    }
  }

  protected abstract void process(T item) throws VcsException;
}
