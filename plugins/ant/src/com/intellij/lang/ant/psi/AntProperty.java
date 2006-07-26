package com.intellij.lang.ant.psi;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public interface AntProperty extends AntTask {

  AntProperty[] EMPTY_ARRAY = new AntProperty[0];

  @Nullable
  String getValue();

  void setValue(final String value) throws IncorrectOperationException;

  @Nullable
  String getFileName();

  @Nullable
  PropertiesFile getPropertiesFile();

  void setPropertiesFile(final String name) throws IncorrectOperationException;

  @Nullable
  String getPrefix();

  @Nullable
  String getEnvironment();
}
