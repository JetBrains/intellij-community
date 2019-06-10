// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.TimedReference;
import org.jetbrains.annotations.NotNull;

public abstract class DefaultProjectTimed extends TimedReference<Project> {
  @NotNull
  private final Disposable myParentDisposable;

  DefaultProjectTimed(@NotNull Disposable disposable) {
    super(disposable);
    myParentDisposable = disposable;
  }

  @NotNull
  abstract Project compute();

  abstract void init(Project project);

  @NotNull
  @Override
  public synchronized Project get() {
    Project value = super.get();
    if (value == null) {
      value = compute();
      set(value);
      init(value);
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
      if (!ApplicationManager.getApplication().isDisposed() && isCached()) {
        WriteCommandAction.runWriteCommandAction(null, () -> super.dispose());
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
