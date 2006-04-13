package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntProject extends AntElement, PsiNamedElement {
  @NotNull
  XmlTag getSourceElement();

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
  AntTaskDefinition[] getBaseTaskDefinitions();

  @Nullable
  AntTaskDefinition getBaseTaskDefinition(final String taskClassName);

  void registerCustomTask(final AntTaskDefinition definition);
}
