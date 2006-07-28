package com.intellij.lang.ant.psi;

import com.intellij.lang.properties.psi.PropertiesFile;
import org.jetbrains.annotations.Nullable;

public interface AntProperty extends AntTask {

  AntProperty[] EMPTY_ARRAY = new AntProperty[0];

  @Nullable
  String getValue();

  @Nullable
  String getFileName();

  @Nullable
  PropertiesFile getPropertiesFile();

  @Nullable
  String getPrefix();

  @Nullable
  String getEnvironment();
}
