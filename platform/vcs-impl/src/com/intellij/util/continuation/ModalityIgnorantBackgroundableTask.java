/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.continuation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ModalityIgnorantBackgroundableTask extends Task.Backgroundable {
  private final static Logger LOG = Logger.getInstance(ModalityIgnorantBackgroundableTask.class);

  public ModalityIgnorantBackgroundableTask(@Nullable Project project, @NotNull String title, boolean canBeCancelled) {
    super(project, title, canBeCancelled);
  }

  protected abstract void doInAwtIfFail(@NotNull Exception e);
  protected abstract void doInAwtIfCancel();
  protected abstract void doInAwtIfSuccess();
  protected abstract void runImpl(@NotNull ProgressIndicator indicator);

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      runImpl(indicator);
    }
    catch (Exception e) {
      LOG.info(e);
      SwingUtilities.invokeLater(() -> doInAwtIfFail(e));
      return;
    }

    SwingUtilities.invokeLater(() -> {
      if (indicator.isCanceled()) {
        doInAwtIfCancel();
      }
      else {
        doInAwtIfSuccess();
      }
    });
  }
}
