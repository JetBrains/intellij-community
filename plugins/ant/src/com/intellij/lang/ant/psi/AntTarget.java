package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTarget extends AntElement, PsiNamedElement {

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getDependsTargets();

  @NotNull
  AntCall[] getAntCalls();
}
