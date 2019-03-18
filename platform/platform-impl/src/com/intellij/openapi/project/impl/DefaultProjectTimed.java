// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.util.TimedReference;
import org.jetbrains.annotations.NotNull;

public class DefaultProjectTimed extends TimedReference<Project> {
  private static final Logger LOG = Logger.getInstance(DefaultProjectTimed.class);
  @NotNull private final Disposable myParentDisposable;

  DefaultProjectTimed(@NotNull Disposable disposable) {
    super(disposable);
    myParentDisposable = disposable;
  }

  @NotNull
  private static Project compute() {
    LOG.assertTrue(!ApplicationManager.getApplication().isDisposeInProgress(), "Application is being disposed!");
    ProjectEx[] project = new ProjectEx[1];
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      try {
        project[0] = ProjectManagerImpl.createProject(null, "", true);
        ProjectManagerImpl.initProject(project[0], null);
      }
      catch (Throwable t) {
        PluginManager.processException(t);
      }
    });
    return project[0];
  }

  @NotNull
  @Override
  public synchronized Project get() {
    Project value = super.get();
    if (value == null) {
      value = compute();
      set(value);
    }
    return value;
  }

  @Override
  public synchronized void dispose() {
    // project must be disposed in EDT in write action
    Runnable doDispose = () -> WriteCommandAction.runWriteCommandAction(null, () -> super.dispose());
    if (ApplicationManager.getApplication().isDispatchThread()) {
      doDispose.run();
    }
    else {
      TransactionGuard.submitTransaction(myParentDisposable, doDispose);
    }
  }
}
