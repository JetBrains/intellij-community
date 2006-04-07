package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTarget extends AntElement, PsiNamedElement {

  @NotNull
  XmlTag getSourceElement();

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getDependsTargets();

  void setDependsTargets(@NotNull AntTarget[] targets);

  @NotNull
  AntCall[] getAntCalls();
}
