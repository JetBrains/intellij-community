// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface QualifiedNameProvider {
  ExtensionPointName<QualifiedNameProvider> EP_NAME = ExtensionPointName.create("com.intellij.qualifiedNameProvider");

  @Nullable
  PsiElement adjustElementToCopy(@NotNull PsiElement element);

  @Nullable
  String getQualifiedName(@NotNull PsiElement element);

  @Nullable
  PsiElement qualifiedNameToElement(@NotNull String fqn, @NotNull Project project);

  default void insertQualifiedName(@NotNull String fqn, @NotNull PsiElement element, @NotNull Editor editor, @NotNull Project project) {
    EditorModificationUtilEx.insertStringAtCaret(editor, fqn);
  }
}
