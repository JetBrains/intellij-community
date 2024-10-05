// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextKt;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class MockPsiDocumentManager extends PsiDocumentManager {
  @Override
  public @Nullable PsiFile getPsiFile(@NotNull Document document, @NotNull CodeInsightContext context) {
    throw new UnsupportedOperationException("Method getPsiFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiFile getCachedPsiFile(@NotNull Document document) {
    return getCachedPsiFile(document, CodeInsightContextKt.anyContext());
  }

  @Override
  public @Nullable PsiFile getCachedPsiFile(@NotNull Document document, @NotNull CodeInsightContext context) {
    throw new UnsupportedOperationException("Method getCachedPsiFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable Document getDocument(@NotNull PsiFile file) {
    return null;
  }

  @Override
  public @Nullable Document getCachedDocument(@NotNull PsiFile file) {
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    return FileDocumentManager.getInstance().getCachedDocument(vFile);
  }

  @Override
  public void commitAllDocuments() {
  }

  @Override
  public boolean commitAllDocumentsUnderProgress() {
    return true;
  }

  @Override
  public void performForCommittedDocument(final @NotNull Document document, final @NotNull Runnable action) {
    action.run();
  }

  @Override
  public void commitDocument(@NotNull Document document) {
  }

  @Override
  public @NotNull CharSequence getLastCommittedText(@NotNull Document document) {
    return document.getImmutableCharSequence();
  }

  @Override
  public long getLastCommittedStamp(@NotNull Document document) {
    return document.getModificationStamp();
  }

  @Override
  public @Nullable Document getLastCommittedDocument(@NotNull PsiFile file) {
    return null;
  }

  @Override
  public Document @NotNull [] getUncommittedDocuments() {
    throw new UnsupportedOperationException("Method getUncommittedDocuments is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isUncommited(@NotNull Document document) {
    throw new UnsupportedOperationException("Method isUncommited is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isCommitted(@NotNull Document document) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable PsiFile getPsiFile(@NotNull Document document) {
    return getPsiFile(document, CodeInsightContextKt.anyContext());
  }

  @Override
  public boolean hasUncommitedDocuments() {
    throw new UnsupportedOperationException("Method hasUncommitedDocuments is not yet implemented in " + getClass().getName());
  }

  @Override
  public void commitAndRunReadAction(@NotNull Runnable runnable) {
    throw new UnsupportedOperationException("Method commitAndRunReadAction is not yet implemented in " + getClass().getName());
  }

  @Override
  public <T> T commitAndRunReadAction(@NotNull Computable<T> computation) {
    throw new UnsupportedOperationException("Method commitAndRunReadAction is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isDocumentBlockedByPsi(@NotNull Document doc) {
    throw new UnsupportedOperationException("Method isDocumentBlockedByPsi is not yet implemented in " + getClass().getName());
  }

  @Override
  public void doPostponedOperationsAndUnblockDocument(@NotNull Document doc) {
    throw new UnsupportedOperationException(
      "Method doPostponedOperationsAndUnblockDocument is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean performWhenAllCommitted(@NotNull Runnable action) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reparseFiles(@NotNull Collection<? extends VirtualFile> files, boolean includeOpenFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void performLaterWhenAllCommitted(final @NotNull Runnable runnable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void performLaterWhenAllCommitted(@NotNull ModalityState modalityState,
                                           @NotNull Runnable runnable) {
    throw new UnsupportedOperationException();
  }
}
