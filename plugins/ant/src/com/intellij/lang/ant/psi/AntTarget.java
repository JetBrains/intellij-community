package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTarget extends AntStructuredElement, PsiNamedElement {

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getDependsTargets();

  void setDependsTargets(@NotNull AntTarget[] targets);

  @NotNull
  AntCall[] getAntCalls();
}
