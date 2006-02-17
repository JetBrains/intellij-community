package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface AntElement extends PsiElement {
  @Nullable
  String getName();
}
