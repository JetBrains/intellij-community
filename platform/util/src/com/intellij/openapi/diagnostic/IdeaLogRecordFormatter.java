// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class IdeaLogRecordFormatter extends Formatter {
  private static final String LINE_SEPARATOR = System.lineSeparator();

  private final long logCreation;
  private final boolean withDateTime;

  public IdeaLogRecordFormatter() {
    this(true);
  }

  public IdeaLogRecordFormatter(boolean withDateTime) {
    this(withDateTime, null);
  }

  public IdeaLogRecordFormatter(boolean withDateTime, @Nullable IdeaLogRecordFormatter copyFrom) {
    this.withDateTime = withDateTime;
    logCreation = copyFrom == null ? System.currentTimeMillis() : copyFrom.getStartedMillis();
  }

  protected long getStartedMillis() {
    return logCreation;
  }

  @Override
  public String format(LogRecord record) {
    long recordMillis = record.getMillis(), startedMillis = getStartedMillis();
    StringBuilder sb = new StringBuilder();

    if (withDateTime) {
      LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(recordMillis), ZoneId.systemDefault());
      sb.append(date.getYear());
      sb.append('-');
      appendWithPadding(sb, Integer.toString(date.getMonthValue()), 2, '0');
      sb.append('-');
      appendWithPadding(sb, Integer.toString(date.getDayOfMonth()), 2, '0');
      sb.append(' ');
      appendWithPadding(sb, Integer.toString(date.getHour()), 2, '0');
      sb.append(':');
      appendWithPadding(sb, Integer.toString(date.getMinute()), 2, '0');
      sb.append(':');
      appendWithPadding(sb, Integer.toString(date.getSecond()), 2, '0');
      sb.append(',');
      appendWithPadding(sb, Long.toString(recordMillis % 1000), 3, '0');
      sb.append(' ');
    }

    sb.append('[');
    appendWithPadding(sb, startedMillis == 0 ? "-------" : String.valueOf(recordMillis - startedMillis), 7, ' ');
    sb.append("] ");

    appendWithPadding(sb, LogLevel.getPrettyLevelName(record.getLevel()), 6, ' ');

    sb.append(" - ")
      .append(smartAbbreviate(record.getLoggerName()))
      .append(" - ")
      .append(formatMessage(record))
      .append(LINE_SEPARATOR);

    if (record.getThrown() != null) {
      appendThrowable(record.getThrown(), sb);
    }

    return sb.toString();
  }

  private static void appendWithPadding(StringBuilder sb, String s, int width, char padChar) {
    for (int i = 0, padding = width - s.length(); i < padding; i++) sb.append(padChar);
    sb.append(s);
  }

  @ApiStatus.Internal
  public static String smartAbbreviate(String category) {
    if (category == null) return null;

    StringBuilder result = new StringBuilder();
    int pos = 0, nextDot;
    if (category.startsWith("#")) {
      result.append('#');
      pos++;
    }
    if (!(category.startsWith("com.intellij", pos) || category.startsWith("com.jetbrains", pos) || category.startsWith("org.jetbrains", pos))) {
      return category;
    }
    while ((nextDot = category.indexOf('.', pos)) >= 0) {
      result.append(category.charAt(pos)).append('.');
      pos = nextDot + 1;
    }
    result.append(category.substring(pos));
    return result.toString();
  }

  public static @NotNull String formatThrowable(@NotNull Throwable thrown) {
    StringBuilder sb = new StringBuilder();
    appendThrowable(thrown, sb);
    return sb.toString();
  }

  private static void appendThrowable(Throwable thrown, StringBuilder sb) {
    StringWriter stringWriter = new StringWriter();
    thrown.printStackTrace(new PrintWriter(stringWriter));
    String[] lines = StringUtil.splitByLines(stringWriter.toString());
    int maxStackSize = 1024, maxExtraSize = 256;
    if (lines.length > maxStackSize + maxExtraSize) {
      String[] res = new String[maxStackSize + maxExtraSize + 1];
      System.arraycopy(lines, 0, res, 0, maxStackSize);
      res[maxStackSize] = "\t...";
      System.arraycopy(lines, lines.length - maxExtraSize, res, maxStackSize + 1, maxExtraSize);
      for (int i = 0; i < res.length; i++) {
        if (i > 0) {
          sb.append(LINE_SEPARATOR);
        }
        sb.append(res[i]);
      }
    }
    else {
      sb.append(stringWriter.getBuffer());
    }
  }
}
