// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class AnnotationPresentation implements TextAnnotationPresentation {
  private final @NotNull FileAnnotation myFileAnnotation;
  private final @NotNull UpToDateLineNumberProvider myUpToDateLineNumberProvider;
  private final @Nullable AnnotationSourceSwitcher mySwitcher;
  private final ArrayList<AnAction> myActions = new ArrayList<>();

  private final @NotNull Disposable myDisposable;
  private boolean myDisposed = false;

  AnnotationPresentation(@NotNull FileAnnotation fileAnnotation,
                         @NotNull UpToDateLineNumberProvider upToDateLineNumberProvider,
                         @Nullable AnnotationSourceSwitcher switcher,
                         @NotNull Disposable disposable) {
    myUpToDateLineNumberProvider = upToDateLineNumberProvider;
    myFileAnnotation = fileAnnotation;
    mySwitcher = switcher;
    myDisposable = disposable;
  }

  @NotNull
  FileAnnotation getFileAnnotation() {
    return myFileAnnotation;
  }


  @Override
  public int getAnnotationLine(int editorLine) {
    return getAnnotationLine(editorLine, false);
  }

  @Override
  public int getAnnotationLine(int editorLine, boolean approximate) {
    return myUpToDateLineNumberProvider.getLineNumber(editorLine, approximate);
  }

  @Override
  public EditorFontType getFontType(final int line) {
    return isLastCommit(line) ? EditorFontType.BOLD : EditorFontType.PLAIN;
  }

  private boolean isLastCommit(int line) {
    VcsRevisionNumber revision = myFileAnnotation.originalRevision(line);
    VcsRevisionNumber currentRevision = myFileAnnotation.getCurrentRevision();
    return currentRevision != null && currentRevision.equals(revision);
  }

  @Override
  public ColorKey getColor(final int line) {
    if (mySwitcher == null) return AnnotationSource.LOCAL.getColor(isLastCommit(line));
    return mySwitcher.getAnnotationSource(line).getColor();
  }

  @Override
  public List<AnAction> getActions(int line) {
    int correctedNumber = getAnnotationLine(line);
    for (AnAction action : myActions) {
      UpToDateLineNumberListener upToDateListener = ObjectUtils.tryCast(action, UpToDateLineNumberListener.class);
      if (upToDateListener != null) upToDateListener.consume(correctedNumber);

      LineNumberListener listener = ObjectUtils.tryCast(action, LineNumberListener.class);
      if (listener != null) listener.consume(line);
    }

    return myActions;
  }

  public @NotNull List<AnAction> getActions() {
    return myActions;
  }

  public void addAction(@NotNull AnAction action) {
    myActions.add(action);
  }

  public void addAction(@NotNull AnAction action, int index) {
    myActions.add(index, action);
  }

  @Override
  public void gutterClosed() {
    if (myDisposed) return;
    myDisposed = true;
    Disposer.dispose(myDisposable);
  }
}
