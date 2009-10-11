/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
