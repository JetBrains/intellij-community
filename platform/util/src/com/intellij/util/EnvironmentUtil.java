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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public class EnvironmentUtil {
  private static final Map<String, String> ourEnvironmentProperties = Collections.unmodifiableMap(new ProcessBuilder().environment());
  private static final Map<String, String> ourEnvironmentVariablesOsSpecific;

  static {
    Map<String, String> envVars = ourEnvironmentProperties;
    if (SystemInfo.isWindows) {
      THashMap<String, String> map = new THashMap<String, String>(CaseInsensitiveStringHashingStrategy.INSTANCE);
      map.putAll(envVars);
      ourEnvironmentVariablesOsSpecific = map;
    }
    else {
      ourEnvironmentVariablesOsSpecific = envVars;
    }
  }

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

  /**
   * Returns value for the passed environment variable name.
   * The passed environment variable name is handled in a case-sensitive or case-insensitive manner depending on OS.<p>
   * For example, on Windows <code>getValue("Path")</code> will return the same result as <code>getValue("PATH")</code>.
   *
   * @param name environment variable name
   * @return value of the environment variable or null if no such variable found
   */
  @Nullable
  public static String getValue(@NotNull String name) {
    return ourEnvironmentVariablesOsSpecific.get(name);
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
