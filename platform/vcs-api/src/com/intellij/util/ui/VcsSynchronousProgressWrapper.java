// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public final class VcsSynchronousProgressWrapper {
  private VcsSynchronousProgressWrapper() {
  }

  public static boolean wrap(@NotNull ThrowableRunnable<? extends VcsException> runnable,
                             @NotNull Project project,
                             @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    final VcsException[] exc = new VcsException[1];
    final Runnable process = () -> {
      try {
        runnable.run();
      }
      catch (VcsException e) {
        exc[0] = e;
      }
    };
    final boolean notCanceled;
    if (ApplicationManager.getApplication().isDispatchThread()) {
      notCanceled = ProgressManager.getInstance().runProcessWithProgressSynchronously(process, title, true, project);
    }
    else {
      process.run();
      notCanceled = true;
    }
    if (exc[0] != null) {
      AbstractVcsHelper.getInstance(project).showError(exc[0], title);
      return false;
    }
    return notCanceled;
  }

  @Nullable
  public static <T> T compute(@NotNull ThrowableComputable<T, ? extends VcsException> computable,
                              @NotNull Project project,
                              @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    final Ref<T> ref = new Ref<>();
    boolean notCanceled = wrap(() -> ref.set(computable.compute()), project, title);
    return notCanceled ? ref.get() : null;
  }
}
