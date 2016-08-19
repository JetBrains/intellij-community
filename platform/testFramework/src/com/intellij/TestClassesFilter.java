/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public abstract class TestClassesFilter {
  public static final TestClassesFilter ALL_CLASSES = new TestClassesFilter() {
    @Override
    public boolean matches(String className, String moduleName) {
      return true;
    }
  };

  public abstract boolean matches(String className, String moduleName);

  protected static ArrayList<Pattern> compilePatterns(Collection<String> filterList) {
    ArrayList<Pattern> patterns = new ArrayList<>();
    for (String aFilter : filterList) {
      String filter = aFilter.trim();
      if (filter.length() == 0) continue;
      filter = filter.replaceAll("\\*", ".\\*");
      Pattern pattern = Pattern.compile(filter);
      patterns.add(pattern);
    }
    return patterns;
  }

  protected static boolean matchesAnyPattern(Collection<Pattern> patterns, String className) {
    for (Pattern pattern : patterns) {
      if (pattern.matcher(className).matches()) {
        return true;
      }
    }
    return false;
  }
}
