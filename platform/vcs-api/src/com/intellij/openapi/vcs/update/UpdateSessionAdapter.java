// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A default implementation of the {@link UpdateSession} interface. This implementation can
 * be used if no post-update processing is required.
 */
public class UpdateSessionAdapter implements UpdateSession{
  private final List<VcsException> myExceptions;
  private final boolean myIsCanceled;

  public UpdateSessionAdapter(List<VcsException> exceptions, boolean isCanceled) {
    myExceptions = exceptions;
    myIsCanceled = isCanceled;
  }

  @Override
  public @NotNull List<VcsException> getExceptions() {
    return myExceptions;
  }

  @Override
  public void onRefreshFilesCompleted() {
  }

  @Override
  public boolean isCanceled() {
    return myIsCanceled;
  }
}
