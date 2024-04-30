// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class IdeaLogRecordFormatter extends Formatter {
  private static final String LINE_SEPARATOR = System.lineSeparator();
  private static final DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
    String loggerName = record.getLoggerName();
    if (loggerName != null) {
      loggerName = smartAbbreviate(loggerName);
    }
    long startedMillis = getStartedMillis();
    long recordMillis = record.getMillis();
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(recordMillis), ZoneId.systemDefault());
    String relativeToStartedMillis = (startedMillis == 0) ? "-------" : String.valueOf(recordMillis - startedMillis);
    String prettyLevelName = LogLevel.getPrettyLevelName(record.getLevel());
    StringBuilder sb = new StringBuilder();
    if (withDateTime) {
      timestampFormat.formatTo(date, sb);
      sb.append(',');
      pad(Long.toString(recordMillis % 1000), sb, 3, '0');
      pad(relativeToStartedMillis, sb, 7, '0');
      sb.append(' ');
    }

    sb.append("[");
    pad(relativeToStartedMillis, sb, 7, ' ');
    sb.append("] ");

    pad(prettyLevelName, sb, 6, ' ');
    sb.append(" - ")
      .append(loggerName)
      .append(" - ")
      .append(formatMessage(record))
      .append(LINE_SEPARATOR);

    if (record.getThrown() != null) {
      appendThrowable(record.getThrown(), sb);
    }
    return sb.toString();
  }

  private static void pad(String s, StringBuilder sb, int width, char pad) {
    int paddingNeeded = width - s.length();
    for (int i = 0; i < paddingNeeded; i++) {
      sb.append(pad);
    }
    sb.append(s);
  }

  private static String smartAbbreviate(String loggerName) {
    StringBuilder result = new StringBuilder();
    int pos = 0;
    if (loggerName.startsWith("#")) {
      result.append('#');
      pos++;
    }
    if (!loggerName.startsWith("com.intellij", pos) &&
        !loggerName.startsWith("com.jetbrains", pos) &&
        !loggerName.startsWith("org.jetbrains", pos)) {
      return loggerName;
    }
    while (true) {
      int nextDot = loggerName.indexOf('.', pos);
      if (nextDot < 0) {
        result.append(loggerName.substring(pos));
        return result.toString();
      }
      result.append(loggerName.charAt(pos)).append('.');
      pos = nextDot + 1;
    }
  }

  public static @NotNull String formatThrowable(@NotNull Throwable thrown) {
    StringBuilder sb = new StringBuilder();
    appendThrowable(thrown, sb);
    return sb.toString();
  }

  private static void appendThrowable(@NotNull Throwable thrown, @NotNull StringBuilder sb) {
    StringWriter stringWriter = new StringWriter();
    thrown.printStackTrace(new PrintWriter(stringWriter));
    String[] lines = StringUtil.splitByLines(stringWriter.toString());
    int maxStackSize = 1024;
    int maxExtraSize = 256;
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
