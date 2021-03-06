// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class EventLogUploaderCliParser {

  @NotNull
  public static Map<String, String> parseOptions(String[] args) {
    Map<String, String> options = new HashMap<>();
    for (int i = 0, length = args.length; i < length; i++) {
      String arg = args[i];
      if (!isOptionName(arg)) continue;

      if (requireValue(arg) && (i + 1) < length && !isOptionName(args[i + 1])) {
        options.put(arg, args[i + 1]);
      }
      else {
        options.put(arg, null);
      }
    }
    return options;
  }

  private static boolean isOptionName(String arg) {
    return arg.startsWith("--");
  }

  private static boolean requireValue(String arg) {
    return !EventLogUploaderOptions.INTERNAL_OPTION.equals(arg) &&
           !EventLogUploaderOptions.TEST_OPTION.equals(arg) &&
           !EventLogUploaderOptions.EAP_OPTION.equals(arg);
  }
}
