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

/*
 * User: anna
 * Date: 23-Jun-2009
 */
package com.intellij.junit4;

import org.junit.runner.Description;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JUnit4ReflectionUtil {
  private JUnit4ReflectionUtil() {
  }

  public static String getClassName(Description description) {
    try {
      return description.getClassName();
    }
    catch (NoSuchMethodError e) {
      final String displayName = description.getDisplayName();
      Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
      return matcher.matches() ? matcher.group(2) : displayName;
    }
  }

  public static String getMethodName(Description description) {
    try {
      return description.getMethodName();
    }
    catch (NoSuchMethodError e) {
      final String displayName = description.getDisplayName();
      Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
      if (matcher.matches()) return matcher.group(1);
      return null;
    }
  }
}