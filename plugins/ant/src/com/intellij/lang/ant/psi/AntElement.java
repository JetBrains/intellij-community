package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntElement extends PsiElement {
  @Nullable
  String getElementName();

  @NotNull
  XmlTag getSourceTag();
}
