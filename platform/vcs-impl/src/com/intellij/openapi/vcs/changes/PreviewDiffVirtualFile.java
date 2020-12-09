// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.editor.DiffVirtualFile;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PreviewDiffVirtualFile extends DiffVirtualFile {
  @NotNull private final DiffPreviewProvider myProvider;

  public PreviewDiffVirtualFile(@NotNull DiffPreviewProvider provider) {
    super(provider.getEditorTabName());
    myProvider = provider;
  }

  @Override
  public boolean isValid() {
    Object owner = myProvider.getOwner();
    if (!(owner instanceof Disposable)) return true;
    return !Disposer.isDisposed((Disposable)owner);
  }

  @NotNull
  @Override
  public DiffRequestProcessor createProcessor(@NotNull Project project) {
    return myProvider.createDiffRequestProcessor();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PreviewDiffVirtualFile file = (PreviewDiffVirtualFile)o;
    return myProvider.getOwner().equals(file.myProvider.getOwner());
  }

  @Override
  public int hashCode() {
    return Objects.hash(myProvider.getOwner());
  }

  @NotNull
  @Override
  public String toString() {
    return super.toString() + ":" + myProvider;
  }

  @NotNull
  public DiffPreviewProvider getProvider() {
    return myProvider;
  }
}
