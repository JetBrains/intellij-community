// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public final class JsonPathConstants {
  private JsonPathConstants() {
  }

  public static final List<String> STANDARD_NAMED_OPERATORS = List.of(
    "anyof",
    "contains",
    "empty",
    "in",
    "nin",
    "noneof",
    "size",
    "subsetof"
  );

  public static final Map<String, String> STANDARD_FUNCTIONS = ImmutableMap.<String, String>builder()
    .put("avg", "number")
    .put("concat", "string")
    .put("keys", "array")
    .put("length", "number")
    .put("max", "number")
    .put("min", "number")
    .put("size", "number")
    .put("stddev", "number")
    .put("sum", "number")
    .build();
}
