package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
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

  @NotNull
  AntTypeDefinition[] getBaseTypeDefinitions();

  @Nullable
  AntTypeDefinition getBaseTypeDefinition(final String taskClassName);

  @NotNull
  AntTypeDefinition getTargetDefinition();
}
