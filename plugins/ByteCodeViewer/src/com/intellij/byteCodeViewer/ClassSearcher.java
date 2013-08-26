package com.intellij.byteCodeViewer;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Max Medvedev on 8/23/13
 */
public interface ClassSearcher {
  @Nullable
  PsiClass findClass(@NotNull PsiElement place);
}
