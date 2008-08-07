package com.intellij.lang.ant.psi;

import com.intellij.lang.properties.psi.PropertiesFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface AntProperty extends AntTask {

  AntProperty[] EMPTY_ARRAY = new AntProperty[0];
  @NonNls String TSTAMP_TAG = "tstamp";
  @NonNls String TSTAMP_PATTERN_ATTRIBUTE_NAME = "pattern";
  @NonNls String TSTAMP_TIMEZONE_ATTRIBUTE_NAME = "timezone";

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
  AntElement getFormatElement(final String propName);

  boolean isTstamp();
}
