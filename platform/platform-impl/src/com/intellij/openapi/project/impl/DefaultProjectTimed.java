// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.TimedReference;
import org.jetbrains.annotations.NotNull;

public abstract class DefaultProjectTimed extends TimedReference<Project> {
  @NotNull
  private final DefaultProject myParentDisposable;

  DefaultProjectTimed(@NotNull DefaultProject disposable) {
    super(disposable);
    myParentDisposable = disposable;
  }

  @NotNull
  abstract Project compute();

  abstract void init(@NotNull Project project);

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
      if (isCached()) {
        WriteAction.run(() -> super.dispose());
      }
    };
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, myParentDisposable.getDisposed(), doDispose);
  }
}
