// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockSmartPointerManager extends SmartPointerManager {
  @NotNull
  @Override
  public SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file, @NotNull TextRange range) {
    return null;
  }

  @NotNull
  @Override
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element) {
    return createSmartPsiElementPointer(element, element.getContainingFile());
  }

  @NotNull
  @Override
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element, PsiFile containingFile) {
    return new SmartPsiElementPointer<E>() {
      @Nullable
      @Override
      public E getElement() {
        return element;
      }

      @Nullable
      @Override
      public PsiFile getContainingFile() {
        return containingFile;
      }

      @NotNull
      @Override
      public Project getProject() {
        return containingFile.getProject();
      }

      @Override
      public VirtualFile getVirtualFile() {
        return containingFile.getVirtualFile();
      }

      @Nullable
      @Override
      public Segment getRange() {
        return element.getTextRange();
      }

      @Nullable
      @Override
      public Segment getPsiRange() {
        return getRange();
      }
    };
  }

  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer pointer1, @NotNull SmartPsiElementPointer pointer2) {
    return false;
  }

  @Override
  public void removePointer(@NotNull SmartPsiElementPointer pointer) {

  }
}
