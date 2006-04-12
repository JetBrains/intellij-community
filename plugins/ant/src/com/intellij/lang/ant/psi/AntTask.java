package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTask extends AntElement, PsiNamedElement {

  @NotNull
  XmlTag getSourceElement();

  @Nullable
  AntTaskDefinition getTaskDefinition();
}
