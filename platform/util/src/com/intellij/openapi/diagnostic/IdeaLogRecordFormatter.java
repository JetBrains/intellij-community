// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.text.StringUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class IdeaLogRecordFormatter extends Formatter {
  private static final String FORMAT_WITH_DATE_TIME = "%1$tF %1$tT,%1$tL [%2$7d] %3$6s - %4$s - %5$s%6$s";
  private static final String FORMAT_WITHOUT_DATE_TIME = "[%2$7d] %3$6s - %4$s - %5$s%6$s";
  private static final String LINE_SEPARATOR = System.lineSeparator();

  private final long myLogCreation;
  private final boolean myWithDateTime;

  public IdeaLogRecordFormatter() {
    myWithDateTime = true;
    myLogCreation = System.currentTimeMillis();
  }

  public IdeaLogRecordFormatter(IdeaLogRecordFormatter copyFrom, boolean withDateTime) {
    myLogCreation = copyFrom.myLogCreation;
    myWithDateTime = withDateTime;
  }

  @Override
  public String format(LogRecord record) {
    String loggerName = record.getLoggerName();
    if (loggerName != null) {
      loggerName = smartAbbreviate(loggerName);
    }
    String level = record.getLevel() == Level.WARNING ? "WARN" : record.getLevel().toString();
    String result = String.format(
      myWithDateTime ? FORMAT_WITH_DATE_TIME : FORMAT_WITHOUT_DATE_TIME,
      record.getMillis(),
      record.getMillis() - myLogCreation,
      level,
      loggerName,
      record.getMessage(),
      LINE_SEPARATOR
    );
    if (record.getThrown() != null) {
      return result + formatThrowable(record.getThrown());
    }
    return result;
  }

  private static String smartAbbreviate(String loggerName) {
    StringBuilder result = new StringBuilder();
    int pos = 0;
    if (loggerName.startsWith("#")) {
      result.append('#');
      pos++;
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

  public static String formatThrowable(Throwable thrown) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    thrown.printStackTrace(pw);
    String[] lines = StringUtil.splitByLines(sw.toString());
    int maxStackSize = 1024;
    int maxExtraSize = 256;
    if (lines.length > maxStackSize + maxExtraSize) {
      String[] res = new String[maxStackSize + maxExtraSize + 1];
      System.arraycopy(lines, 0, res, 0, maxStackSize);
      res[maxStackSize] = "\t...";
      System.arraycopy(lines, lines.length - maxExtraSize, res, maxStackSize + 1, maxExtraSize);
      return StringUtil.join(res, LINE_SEPARATOR);
    }
    return sw.toString();
  }
}
