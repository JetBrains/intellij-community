package com.intellij.lang.ant.psi;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public interface AntProperty extends AntTask, PsiNamedElement {

  AntProperty[] EMPTY_ARRAY = new AntProperty[0];

  @Nullable
  String getValue();

  void setValue(final String value) throws IncorrectOperationException;

  @Nullable
  String getFileName();

  @Nullable
  PropertiesFile getPropertiesFile();

  void setPropertiesFile(final String name) throws IncorrectOperationException;
}
