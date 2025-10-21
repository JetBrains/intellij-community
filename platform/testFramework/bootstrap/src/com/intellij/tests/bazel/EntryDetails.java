/**
 * This file was originally part of [rules_jvm] (https://github.com/bazel-contrib/rules_jvm)
 * Original source:
 * https://github.com/bazel-contrib/rules_jvm/blob/201fa7198cfd50ae4d686715651500da656b368a/java/src/com/github/bazel_contrib/contrib_rules_jvm/junit5/EntryDetails.java
 * Licensed under the Apache License, Version 2.0
 */
package com.intellij.tests.bazel;

import static org.junit.platform.launcher.LauncherConstants.STDERR_REPORT_ENTRY_KEY;
import static org.junit.platform.launcher.LauncherConstants.STDOUT_REPORT_ENTRY_KEY;

import java.util.Map;
import org.junit.platform.engine.reporting.ReportEntry;

class EntryDetails {

  private EntryDetails() {
    // Utility class
  }

  public static String getStdOut(ReportEntry entry) {
    return getReportEntryValue(entry, STDOUT_REPORT_ENTRY_KEY);
  }

  public static String getStdErr(ReportEntry entry) {
    return getReportEntryValue(entry, STDERR_REPORT_ENTRY_KEY);
  }

  private static String getReportEntryValue(ReportEntry entry, String key) {
    return entry.getKeyValuePairs().entrySet().stream()
      .filter(e -> key.equals(e.getKey()))
      .map(Map.Entry::getValue)
      .findFirst()
      .orElse(null);
  }
}