package com.intellij.lang.ant.psi;

import com.intellij.lang.properties.psi.PropertiesFile;
import org.jetbrains.annotations.Nullable;

public interface AntProperty extends AntTask {

  AntProperty[] EMPTY_ARRAY = new AntProperty[0];

  /**
   * Calculates property value.
   *
   * @param propName -- name of a property, some property elements may define several properties.
   * @return
   */
  @Nullable
  String getValue(final String propName);

  @Nullable
  String getFileName();

  @Nullable
  PropertiesFile getPropertiesFile();

  @Nullable
  String getPrefix();

  @Nullable
  String getEnvironment();

  @Nullable
  String[] getNames();

  @Nullable
  AntElement getFormatElement();
}
