// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.TimedReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultProjectTimed extends TimedReference<Project> {
  private static final Logger LOG = Logger.getInstance(DefaultProjectTimed.class);
  @NotNull
  private final Disposable myParentDisposable;

  DefaultProjectTimed(@NotNull Disposable disposable) {
    super(disposable);
    myParentDisposable = disposable;
  }

  @NotNull
  private static Project compute() {
    LOG.assertTrue(!ApplicationManager.getApplication().isDisposeInProgress(), "Application is being disposed!");
    ProjectImpl project = new ProjectImpl() {
      @Override
      public boolean isDefault() {
        return true;
      }

      @Override
      public boolean isInitialized() {
        return true; // no startup activities, never opened
      }

      @Nullable
      @Override
      protected String activityNamePrefix() {
        // exclude from measurement because default project initialization is not a sequential activity
        // (so, complicates timeline because not applicable)
        // for now we don't measure default project initialization at all, because it takes only ~10 ms
        return null;
      }

      @Override
      protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
        return super.isComponentSuitable(componentConfig) && componentConfig.isLoadForDefaultProject();
      }
    };
    project.init();
    return project;
  }

  @NotNull
  @Override
  public Project get() {
    Project value = super.get();
    if (value == null) {
      value = compute();
      set(value);
      // disable "the only project" optimization since we have now more than one project.
      // (even though the default project is not a real project, it can be used indirectly in e.g. "Settings|Code Style" code fragments PSI)
      ((ProjectManagerImpl)ProjectManager.getInstance()).updateTheOnlyProjectField();

    }
    return value;
  }

  @Override
  public void dispose() {
    // project must be disposed in EDT in write action
    Runnable doDispose = () -> {
      if (!ApplicationManager.getApplication().isDisposed()) {
        WriteCommandAction.runWriteCommandAction(null, () -> {
          super.dispose();
          set(null);
        });
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      doDispose.run();
    }
    else {
      TransactionGuard.submitTransaction(myParentDisposable, doDispose);
    }
  }
}
