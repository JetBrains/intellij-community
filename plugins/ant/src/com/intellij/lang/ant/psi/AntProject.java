package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntProject extends AntStructuredElement, PsiNamedElement {

  @Nullable
  String getBaseDir();

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getTargets();

  @Nullable
  AntTarget getTarget(final String name);

  @Nullable
  AntTarget getDefaultTarget();
}
