package com.intellij.lang.ant.psi;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntProperty extends AntElement, PsiNamedElement {

  @NotNull
  XmlTag getSourceElement();

  @Nullable
  String getValue();

  void setValue(final String value) throws IncorrectOperationException;

  @Nullable
  String getFileName();

  @Nullable
  PropertiesFile getPropertiesFile();

  void setPropertiesFile(final String name) throws IncorrectOperationException;
}
