// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.editor.DiffEditorViewerFileEditor;
import com.intellij.diff.editor.DiffVirtualFile;
import com.intellij.diff.editor.DiffVirtualFileWithTabName;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * @deprecated Use {@link com.intellij.diff.editor.DiffViewerVirtualFile} and {@link DiffVirtualFileWithTabName} instead
 */
@Deprecated(forRemoval = true)
public class PreviewDiffVirtualFile extends DiffVirtualFile implements DiffVirtualFileWithTabName {
  private final @NotNull DiffPreviewProvider myProvider;

  public PreviewDiffVirtualFile(@NotNull DiffPreviewProvider provider) {
    super("DiffPreview");
    myProvider = provider;

    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true);
  }

  @Override
  public boolean isValid() {
    Object owner = myProvider.getOwner();
    if (!(owner instanceof Disposable)) return true;
    return !Disposer.isDisposed((Disposable)owner);
  }

  @Override
  public @NotNull DiffRequestProcessor createProcessor(@NotNull Project project) {
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

  @Override
  public @NotNull String toString() {
    return super.toString() + ":" + myProvider;
  }

  public @NotNull DiffPreviewProvider getProvider() {
    return myProvider;
  }

  @Override
  public @Nullable @NlsContexts.TabTitle String getEditorTabName(@NotNull Project project, @NotNull List<? extends FileEditor> editors) {
    DiffEditorViewerFileEditor editor = ContainerUtil.findInstance(editors, DiffEditorViewerFileEditor.class);
    DiffRequestProcessor processor = editor != null ? ObjectUtils.tryCast(editor.getEditorViewer(), DiffRequestProcessor.class) : null;
    return myProvider.getEditorTabName(processor);
  }
}
