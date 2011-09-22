/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * author: lesya
 */
public class CvsMessagePattern {
  private final Pattern myPattern;
  private final int myFileNameGroup;

  public CvsMessagePattern(@NonNls String[] groups, int fileNameGroup) {
    myFileNameGroup = fileNameGroup;
    final String regex = createRegex(groups);
    myPattern = Pattern.compile(regex);
  }

  private static String createRegex(@NonNls String[] groups) {
    final StringBuilder result = new StringBuilder();
    for (String group : groups) {
      result.append("(");
      result.append(PatternUtil.convertToRegex(group));
      result.append(")");
    }
    return result.toString();
  }

  public boolean matches(String string) {
    return myPattern.matcher(string).matches();
  }

  public CvsMessagePattern(String[] pattern) {
    this(pattern, -1);
  }

  public CvsMessagePattern(@NonNls String pattern) {
    this(new String[]{pattern});
  }

  public String getRelativeFileName(String message) {
    if (myFileNameGroup < 0) return null;
    final Matcher matcher = myPattern.matcher(message);
    if (matcher.matches()) {
      return matcher.group(myFileNameGroup);
    }
    return null;
  }
}
