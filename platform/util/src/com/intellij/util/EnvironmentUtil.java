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
  private static final Map<String, String> ourEnviromentProperties = Collections.unmodifiableMap(new ProcessBuilder().environment());

  private EnvironmentUtil() {
  }

  @NonNls
  public static Map<String, String> getEnviromentProperties() {
    return ourEnviromentProperties;
  }

  public static String[] getFlattenEnvironmentProperties() {
    return getEnvironment();
  }

  public static String[] getEnvironment() {
    Map enviroment = getEnviromentProperties();
    String[] envp = new String[enviroment.size()];
    int i = 0;
    for (Object o : enviroment.keySet()) {
      String name = (String)o;
      String value = (String)enviroment.get(name);
      envp[i++] = name + "=" + value;
    }
    return envp;
  }
}
