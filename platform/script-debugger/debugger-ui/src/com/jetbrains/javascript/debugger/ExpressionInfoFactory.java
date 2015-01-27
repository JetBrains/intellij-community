package com.jetbrains.javascript.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import org.jetbrains.annotations.NotNull;

public interface ExpressionInfoFactory {
  @NotNull
  ExpressionInfo create(@NotNull PsiElement element, @NotNull Document document);
}
