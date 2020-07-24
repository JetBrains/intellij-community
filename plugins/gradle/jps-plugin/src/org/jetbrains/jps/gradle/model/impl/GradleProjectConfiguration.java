// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.gradle.model.impl;

import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public final class GradleProjectConfiguration {
  public static final String CONFIGURATION_FILE_RELATIVE_PATH = "gradle/configuration.xml";

  @Tag("resource-processing")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "gradle-module",
                 keyAttributeName = "name")
  public Map<String, GradleModuleResourceConfiguration> moduleConfigurations = new HashMap<>();
}
