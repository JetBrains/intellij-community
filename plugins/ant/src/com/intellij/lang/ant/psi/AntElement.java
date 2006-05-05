package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntElement extends PsiNamedElement {
  AntElement[] EMPTY_ARRAY = new AntElement[0];

  @NotNull
  XmlElement getSourceElement();

  AntElement getAntParent();

  @NotNull
  AntProject getAntProject();

  void subtreeChanged();

  @Nullable
  PsiFile findFileByName(final String name);

  void setProperty(final String name, final PsiElement element);

  @Nullable
  PsiElement getProperty(final String name);

  @NotNull
  PsiElement[] getProperties();
}
