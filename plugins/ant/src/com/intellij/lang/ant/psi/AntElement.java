package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

public interface AntElement extends PsiElement {
  AntElement[] EMPTY_ARRAY = new AntElement[0];
  @NotNull
  XmlElement getSourceElement();

  AntElement getAntParent();

  void subtreeChanged();
}
