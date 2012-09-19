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
package com.intellij.util;

import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.Map;

public class EnvironmentUtil {
  private static final Map<String, String> ourEnvironmentProperties = Collections.unmodifiableMap(new ProcessBuilder().environment());

  private EnvironmentUtil() {
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  @NonNls
  public static Map<String, String> getEnviromentProperties() {
    return getEnvironmentProperties();
  }

  @NonNls
  public static Map<String, String> getEnvironmentProperties() {
    return ourEnvironmentProperties;
  }

  public static String[] getEnvironment() {
    return flattenEnvironment(getEnvironmentProperties());
  }

  public static String[] flattenEnvironment(Map<String, String> environment) {
    String[] array = new String[environment.size()];
    int i = 0;
    for (String name : environment.keySet()) {
      array[i++] = name + "=" + environment.get(name);
    }
    return array;
  }
}
