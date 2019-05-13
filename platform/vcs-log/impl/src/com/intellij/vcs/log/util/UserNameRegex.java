/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public class UserNameRegex implements Function<String, String> {
  @NotNull public static final UserNameRegex EXTENDED_INSTANCE = new UserNameRegex(true);
  @NotNull private static final char[] BASIC_REGEX_CHARS = new char[]{'.', '^', '$', '*', '[', ']'};
  @NotNull public static final char[] EXTENDED_REGEX_CHARS = new char[]{'.', '^', '$', '*', '+', '-', '?', '(', ')', '[', ']', '{', '}', '|'};
  private final boolean myExtended;

  private UserNameRegex(boolean extended) {
    myExtended = extended;
  }

  @Override
  public String fun(String s) {
    return "^" + StringUtil.escapeChars(StringUtil.escapeBackSlashes(s), myExtended ? EXTENDED_REGEX_CHARS : BASIC_REGEX_CHARS) + "$";
  }
}
