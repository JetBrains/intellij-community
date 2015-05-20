package com.jetbrains.javascript.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ExpressionInfoFactory {
  @NotNull
  ExpressionInfo create(@NotNull PsiElement element, @NotNull Document document);

  @Nullable
  NameMapper createNameMapper(@NotNull VirtualFile file, @NotNull Document document);
}
