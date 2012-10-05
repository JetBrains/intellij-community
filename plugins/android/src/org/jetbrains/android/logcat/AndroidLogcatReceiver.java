/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.logcat;

import com.android.ddmlib.Log;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Sep 12, 2009
 * Time: 9:01:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidLogcatReceiver extends AndroidOutputReceiver {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.logcat.AndroidLogcatReceiver");
  private static Pattern LOG_PATTERN =
    Pattern.compile("^\\[\\s(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s+(\\d*):\\s*(\\S+)\\s([VDIWEAF])/(.*)\\]$", Pattern.DOTALL);

  private static final String SHIFT = "        ";

  private LogMessageHeader myLastMessageHeader;
  private volatile boolean myCanceled = false;
  private Log.LogLevel myPrevLogLevel;
  private final Writer myWriter;

  public AndroidLogcatReceiver(Writer writer) {
    myWriter = new PrintWriter(writer);
  }

  @Override
  public void processNewLine(String line) {
    Matcher matcher = LOG_PATTERN.matcher(line);
    if (myLastMessageHeader == null && matcher.matches()) {
      myLastMessageHeader = new LogMessageHeader();
      myLastMessageHeader.myTime = matcher.group(1);
      myLastMessageHeader.myPid = Integer.valueOf(matcher.group(2));
      myLastMessageHeader.myLogLevel = getByLetterString(matcher.group(4));
      myLastMessageHeader.myTag = matcher.group(5).trim();
    }
    else {
      if (line.length() == 0) return;
      String text = myLastMessageHeader == null ? SHIFT + line : getFullMessage(line, myLastMessageHeader);
      if (myLastMessageHeader == null) {
        myLastMessageHeader = new LogMessageHeader();
        myLastMessageHeader.myLogLevel = myPrevLogLevel;
      }
      try {
        myWriter.write(text + '\n');
      }
      catch (IOException ignored) {
        LOG.info(ignored);
      }
      myPrevLogLevel = myLastMessageHeader.myLogLevel;
      myLastMessageHeader = null;
    }
  }

  @Nullable
  private static Log.LogLevel getByLetterString(@Nullable String s) {
    if (s == null) {
      return null;
    }
    final Log.LogLevel logLevel = Log.LogLevel.getByLetterString(s);

    /* LogLevel doesn't support messages with severity "F". Log.wtf() is supposed
     * to generate "A", but generates "F" */
    if (logLevel == null && s.equals("F")) {
      return Log.LogLevel.ASSERT;
    }
    return logLevel;
  }

  public boolean isCancelled() {
    return myCanceled;
  }

  private static class LogMessageHeader {
    String myTime;
    Log.LogLevel myLogLevel;
    int myPid;
    String myTag;
  }

  private static String getFullMessage(String message, LogMessageHeader header) {
    return header.myTime + ": " + header.myLogLevel + "/" + header.myTag + "(" + header.myPid + "): " + message;
  }

  public void cancel() {
    myCanceled = true;
  }
}
