// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerEx;
import com.intellij.psi.impl.smartPointers.SmartPointerTracker;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MockSmartPointerManager extends SmartPointerManagerEx {
  private final Project myProject;

  public MockSmartPointerManager(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile psiFile, @NotNull TextRange range) {
    throw new IncorrectOperationException();
  }

  @Override
  public @NotNull <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element) {
    return createSmartPsiElementPointer(element, element.getContainingFile());
  }

  @Override
  public @NotNull <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element, @Nullable PsiFile containingFile) {
    return new MockSmartPsiElementPointer<>(element, containingFile, myProject);
  }

  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer<?> pointer1, @NotNull SmartPsiElementPointer<?> pointer2) {
    return false;
  }

  @Override
  public void removePointer(@NotNull SmartPsiElementPointer<?> pointer) {

  }

  @Override
  public void fastenBelts(@NotNull VirtualFile file) {

  }

  @Override
  public @NotNull <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element,
                                                                                                @Nullable PsiFile containingFile,
                                                                                                boolean forInjected) {
    return createSmartPsiElementPointer(element);
  }

  @Override
  public @NotNull SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file, @NotNull TextRange range, boolean forInjected) {
    throw new UnsupportedOperationException("createSmartPsiFileRangePointer not implemented in mock");
  }

  @Override
  public @Nullable SmartPointerTracker getTracker(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public @NotNull SmartPointerTracker getOrCreateTracker(@NotNull VirtualFile file) {
    return new SmartPointerTracker(0);
  }

  @Override
  public void updatePointers(@NotNull Document document, @NotNull FrozenDocument frozen, @NotNull List<? extends DocumentEvent> events) {

  }

  @Override
  public void updatePointerTargetsAfterReparse(@NotNull VirtualFile file) {

  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull PsiDocumentManagerEx getPsiDocumentManager() {
    return (PsiDocumentManagerEx)PsiDocumentManager.getInstance(myProject);
  }

  @Override
  public @NotNull SimpleModificationTracker getPossiblyInvalidationModCounter() {
    return new SimpleModificationTracker(); // no-op
  }

  @Override
  public void dispose() {

  }

  private static class MockSmartPsiElementPointer<E extends PsiElement> implements SmartPsiElementPointer<E> {
    private final @NotNull E myElement;
    private final @Nullable PsiFile myContainingFile;
    private final @NotNull Project myProject;

    private MockSmartPsiElementPointer(@NotNull E element, @Nullable PsiFile containingFile, @NotNull Project project) {
      myElement = element;
      myContainingFile = containingFile;
      myProject = project;
    }

    @Override
    public @NotNull E getElement() {
      return myElement;
    }

    @Override
    public @Nullable PsiFile getContainingFile() {
      return myContainingFile;
    }

    @Override
    public @NotNull Project getProject() {
      return myProject;
    }

    @Override
    public VirtualFile getVirtualFile() {
      return myContainingFile != null ? myContainingFile.getVirtualFile() : null;
    }

    @Override
    public @Nullable Segment getRange() {
      return myElement.getTextRange();
    }

    @Override
    public @Nullable Segment getPsiRange() {
      return getRange();
    }
  }
}
