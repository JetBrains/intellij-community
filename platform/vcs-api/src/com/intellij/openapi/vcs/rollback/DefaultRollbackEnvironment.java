// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.rollback;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public abstract class DefaultRollbackEnvironment implements RollbackEnvironment {
  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getRollbackOperationName() {
    return getRollbackOperationText();
  }

  @Override
  public void rollbackModifiedWithoutCheckout(final List<? extends VirtualFile> files, final List<? super VcsException> exceptions,
                                              final RollbackProgressListener listener) {
    throw new UnsupportedOperationException();
  }

  public static @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getRollbackOperationText() {
    return VcsBundle.message("changes.action.rollback.text");
  }
}
