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
import org.jetbrains.annotations.NonNls;

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
  @NonNls private static final String READ_PROGRESS_MESSAGE = CvsBundle.message("progress.text.kb.read");
  @NonNls private static final String SENT_PROGRESS_MESSAGE = CvsBundle.message("progress.text.kb.sent");
  @NonNls private static final String PROGRESS_SENDING = CvsBundle.message("progress.text.sending.data.to.server");
  @NonNls private static final String PROGRESS_READING = CvsBundle.message("progress.text.reading.data.from.server");

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

    showProgress(PROGRESS_READING);
  }

  public void send(long bytes) {
    mySentBytes += bytes;
    mySentFromLastUpdateBytes += bytes;
    if (mySentFromLastUpdateBytes > KB) {
      mySentFromLastUpdateBytes = 0;
      myShownSentKBytes = mySentBytes / KB;
    }
    showProgress(PROGRESS_SENDING);
  }

  private void showProgress(String mesasge) {
    StringBuffer buffer = new StringBuffer();
    buffer.append(mesasge);
    if ((myShownReadKBytes > 0) || (myShownSentKBytes > 0)) {
      buffer.append(": ");
    }
    if (myShownReadKBytes > 0) {
      buffer.append(String.valueOf(myShownReadKBytes));
      buffer.append(READ_PROGRESS_MESSAGE);
      if (myShownSentKBytes > 0) buffer.append("; ");
    }

    if (myShownSentKBytes > 0) {
      buffer.append(String.valueOf(myShownSentKBytes));
      buffer.append(SENT_PROGRESS_MESSAGE);
    }


    myProgress.setText(buffer.toString());
  }
}
