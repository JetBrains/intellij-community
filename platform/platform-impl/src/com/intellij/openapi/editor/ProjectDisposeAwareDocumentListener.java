// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import org.jetbrains.annotations.NotNull;

// PsiChangeHandler is used in a light tests, where project is not disposed on close, so, listener is not removed on close,
// so, we have to check isDisposed explicitly
public final class ProjectDisposeAwareDocumentListener implements DocumentListener {
  private final Project project;
  private final DocumentListener listener;

  public static @NotNull DocumentListener create(@NotNull Project project, @NotNull DocumentListener listener) {
    //noinspection TestOnlyProblems
    return ProjectManagerImpl.Companion.isLight(project) ? new ProjectDisposeAwareDocumentListener(project, listener) : listener;
  }

  private ProjectDisposeAwareDocumentListener(@NotNull Project project, @NotNull DocumentListener listener) {
    this.project = project;
    this.listener = listener;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    if (!project.isDisposed()) {
      listener.beforeDocumentChange(event);
    }
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    if (!project.isDisposed()) {
      listener.documentChanged(event);
    }
  }

  @Override
  public void bulkUpdateStarting(@NotNull Document document) {
    if (!project.isDisposed()) {
      listener.bulkUpdateStarting(document);
    }
  }

  @Override
  public void bulkUpdateFinished(@NotNull Document document) {
    if (!project.isDisposed()) {
      listener.bulkUpdateFinished(document);
    }
  }
}
