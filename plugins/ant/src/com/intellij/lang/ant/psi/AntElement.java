package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntElement extends PsiElement {
  AntElement[] EMPTY_ARRAY = new AntElement[0];
  @NotNull
  XmlElement getSourceElement();

  AntElement getAntParent();

  @Nullable
  AntProject getAntProject();

  @NotNull
  AntProperty[] getProperties();

  @Nullable
  AntProperty getProperty(final String name);

  @NotNull
  XmlAttribute[] getAttributes();

  void subtreeChanged();
}
