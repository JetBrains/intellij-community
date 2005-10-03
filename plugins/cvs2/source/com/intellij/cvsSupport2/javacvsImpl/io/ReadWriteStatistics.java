package com.intellij.cvsSupport2.javacvsImpl.io;

import java.text.MessageFormat;

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

  public void read(long bytes){
    myReadBytes += bytes;
    myReadFromLastUpdateBytes += bytes;
    if (myReadFromLastUpdateBytes > KB) {
      myReadFromLastUpdateBytes = 0;
      myShownReadKBytes = myReadBytes / KB;
    }

    showProgress(com.intellij.CvsBundle.message("progress.text.reading.data.from.server"));
  }

  public void send(long bytes){
    mySentBytes += bytes;
    mySentFromLastUpdateBytes += bytes;
    if (mySentFromLastUpdateBytes > KB) {
      mySentFromLastUpdateBytes = 0;
      myShownSentKBytes = mySentBytes / KB;
    }
    showProgress(com.intellij.CvsBundle.message("progress.text.sending.data.to.server"));
  }

  private void showProgress(String mesasge) {
    StringBuffer buffer = new StringBuffer();
    buffer.append(mesasge);
    if ((myShownReadKBytes > 0) || (myShownSentKBytes > 0)){
      buffer.append(": ");
    }
    if (myShownReadKBytes > 0){
      buffer.append(com.intellij.CvsBundle.message("progress.text.kb.read", myShownReadKBytes));
      if (myShownSentKBytes > 0) buffer.append("; ");
    }

    if (myShownSentKBytes > 0)
      buffer.append(com.intellij.CvsBundle.message("progress.text.kb.sent", myShownSentKBytes));

    myProgress.setText(buffer.toString());
  }
}
