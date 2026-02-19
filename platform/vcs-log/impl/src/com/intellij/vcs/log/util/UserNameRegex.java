// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public final class UserNameRegex implements Function<String, String> {
  public static final @NotNull UserNameRegex EXTENDED_INSTANCE = new UserNameRegex(true);
  private static final char @NotNull [] BASIC_REGEX_CHARS = new char[]{'.', '^', '$', '*', '[', ']'};
  public static final char @NotNull []
    EXTENDED_REGEX_CHARS = new char[]{'.', '^', '$', '*', '+', '-', '?', '(', ')', '[', ']', '{', '}', '|'};
  private final boolean myExtended;

  private UserNameRegex(boolean extended) {
    myExtended = extended;
  }

  @Override
  public String fun(String s) {
    return "^" + StringUtil.escapeChars(StringUtil.escapeBackSlashes(s), myExtended ? EXTENDED_REGEX_CHARS : BASIC_REGEX_CHARS) + "$";
  }
}
