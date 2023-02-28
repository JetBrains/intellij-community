// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.util.ArrayUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

public class MethodTags {
  @NotNull
  public static Set<String> tags(@Nullable String referenceName) {
    if (referenceName == null) {
      return Collections.emptySet();
    }
    String[] strings = NameUtilCore.nameToWords(referenceName);
    if (strings.length == 0) {
      return Collections.emptySet();
    }
    String[] canBeFirst = getTags(strings[0]);
    Set<String> result = new HashSet<>();
    for (String firstPart : canBeFirst) {
      StringJoiner joiner = new StringJoiner("");
      joiner.add(firstPart);
      for (int i = 1; i < strings.length; i++) {
        String string = strings[i];
        joiner.add(string);
      }
      result.add(joiner.toString());
    }
    return result;
  }

  @SuppressWarnings("DuplicateBranchesInSwitch")
  private static String[] getTags(String string) {
    switch (string) {
      case "add":
        return new String[]{"put", "sum"};
      case "append":
        return new String[]{"add"};
      case "apply":
        return new String[]{"invoke", "do", "call"};
      case "assert":
        return new String[]{"expect", "verify", "test"};
      case "call":
        return new String[]{"execute", "run"};
      case "check":
        return new String[]{"test", "match"};
      case "count":
        return new String[]{"size", "length"};
      case "convert":
        return new String[]{"map"};
      case "create":
        return new String[]{"build", "make", "generate"};
      case "delete":
        return new String[]{"remove"};
      case "do":
        return new String[]{"run", "execute", "call"};
      case "execute":
        return new String[]{"run", "do", "call"};
      case "expect":
        return new String[]{"verify", "assert", "test"};
      case "from":
        return new String[]{"of", "parse"};
      case "generate":
        return new String[]{"create", "build"};
      case "invoke":
        return new String[]{"apply", "do", "call"};
      case "has":
        return new String[]{"contains", "check"};
      case "length":
        return new String[]{"size"};
      case "load":
        return new String[]{"read"};
      case "match":
        return new String[]{"test", "check"};
      case "minus":
        return new String[]{"subtract"};
      case "of":
        return new String[]{"parse", "from"};
      case "parse":
        return new String[]{"of", "from"};
      case "perform":
        return new String[]{"execute", "run", "do"};
      case "persist":
        return new String[]{"save"};
      case "print":
        return new String[]{"write"};
      case "put":
        return new String[]{"add"};
      case "remove":
        return new String[]{"delete"};
      case "run":
        return new String[]{"start", "execute", "call"};
      case "save":
        return new String[]{"persist", "write"};
      case "size":
        return new String[]{"length"};
      case "start":
        return new String[]{"call", "execute", "run"};
      case "test":
        return new String[]{"check", "match"};
      case "validate":
        return new String[]{"test", "check"};
      case "verify":
        return new String[]{"expect", "assert", "test"};
      case "write":
        return new String[]{"print"};
      default:
        return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }
}