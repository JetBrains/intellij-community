/*
 * @author max
 */
package com.intellij.usages;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface UsageTargetProvider {
  @Nullable
  UsageTarget[] getTargets(Editor editor, PsiFile file);

  @Nullable
  UsageTarget[] getTargets(PsiElement psiElement);
}