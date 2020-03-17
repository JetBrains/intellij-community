/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.Progress;

/**
 * author: lesya
 */
public class ReadWriteStatistics {
  private final Progress myProgress;

  private long myReadBytes = 0;
  private long myReadFromLastUpdateBytes = 0;
  private long myShownReadKBytes = 0;

  private long mySentBytes = 0;
  private long mySentFromLastUpdateBytes = 0;
  private long myShownSentKBytes = 0;

  public static final int KB = 1024;

  public ReadWriteStatistics() {
    myProgress = Progress.create();
  }

  public ReadWriteStatistics(Progress progress) {
    myProgress = progress;
  }

  public void read(long bytes) {
    myReadBytes += bytes;
    myReadFromLastUpdateBytes += bytes;
    if (myReadFromLastUpdateBytes > KB) {
      myReadFromLastUpdateBytes = 0;
      myShownReadKBytes = myReadBytes / KB;
    }

    showProgress(getProgressReading());
  }

  public void send(long bytes) {
    mySentBytes += bytes;
    mySentFromLastUpdateBytes += bytes;
    if (mySentFromLastUpdateBytes > KB) {
      mySentFromLastUpdateBytes = 0;
      myShownSentKBytes = mySentBytes / KB;
    }
    showProgress(getProgressSending());
  }

  private void showProgress(String mesasge) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(mesasge);
    if ((myShownReadKBytes > 0) || (myShownSentKBytes > 0)) {
      buffer.append(": ");
    }
    if (myShownReadKBytes > 0) {
      buffer.append(myShownReadKBytes);
      buffer.append(getReadProgressMessage());
      if (myShownSentKBytes > 0) buffer.append("; ");
    }

    if (myShownSentKBytes > 0) {
      buffer.append(myShownSentKBytes);
      buffer.append(getSentProgressMessage());
    }


    myProgress.setText(buffer.toString());
  }

  private static String getReadProgressMessage() {
    return CvsBundle.message("progress.text.kb.read");
  }

  private static String getSentProgressMessage() {
    return CvsBundle.message("progress.text.kb.sent");
  }

  private static String getProgressSending() {
    return CvsBundle.message("progress.text.sending.data.to.server");
  }

  private static String getProgressReading() {
    return CvsBundle.message("progress.text.reading.data.from.server");
  }
}
