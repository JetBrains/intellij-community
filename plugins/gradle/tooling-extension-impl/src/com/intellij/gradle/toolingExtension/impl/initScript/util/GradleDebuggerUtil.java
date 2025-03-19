// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.util;

import com.intellij.gradle.toolingExtension.impl.util.GradleObjectUtil;
import com.intellij.openapi.util.text.StringUtilRt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GradleDebuggerUtil {

  public static boolean isDebugAllEnabled() {
    String enabled = System.getProperty("idea.gradle.debug.all");
    return Boolean.parseBoolean(enabled);
  }

  public static boolean isDebuggerEnabled() {
    String enabled = GradleObjectUtil.notNull(System.getenv("DEBUGGER_ENABLED"), "false");
    return Boolean.parseBoolean(enabled);
  }

  public static String getDebuggerId() {
    return System.getenv("DEBUGGER_ID");
  }

  public static String getProcessParameters() {
    return System.getenv("PROCESS_PARAMETERS");
  }

  public static List<String> getProcessOptions() {
    String envValue = System.getenv("PROCESS_OPTIONS");
    if (StringUtilRt.isEmptyOrSpaces(envValue)) {
      return Collections.emptyList();
    }
    List<String> options = new ArrayList<>();
    for (String option : envValue.split(", ")) {
      if (!StringUtilRt.isEmptyOrSpaces(option)) {
        options.add(option.trim());
      }
    }
    return options;
  }
}
