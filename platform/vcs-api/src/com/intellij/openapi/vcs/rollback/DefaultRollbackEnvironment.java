// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.rollback;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public abstract class DefaultRollbackEnvironment implements RollbackEnvironment {
  @Override
  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  public  String getRollbackOperationName() {
    return getRollbackOperationText();
  }

  @Override
  public void rollbackModifiedWithoutCheckout(final List<? extends VirtualFile> files, final List<? super VcsException> exceptions,
                                              final RollbackProgressListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void rollbackIfUnchanged(final VirtualFile file) {
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  public static String getRollbackOperationText() {
    return VcsBundle.message("changes.action.rollback.text");
  }
}
