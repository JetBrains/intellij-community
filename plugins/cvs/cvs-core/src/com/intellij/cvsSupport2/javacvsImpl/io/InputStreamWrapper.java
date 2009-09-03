package com.intellij.cvsSupport2.javacvsImpl.io;

import org.netbeans.lib.cvsclient.ICvsCommandStopper;

import java.io.IOException;
import java.io.InputStream;

import com.intellij.openapi.application.ApplicationManager;

/**
 * author: lesya
 */

public class InputStreamWrapper extends InputStream {
  private final ReadThread myReadThread;
  private final ReadWriteStatistics myStatistics;

  public InputStreamWrapper(InputStream original, 
                            ICvsCommandStopper cvsCommandStopper,
                            ReadWriteStatistics statistics) {
    myReadThread = new ReadThread(original, cvsCommandStopper);
    ApplicationManager.getApplication().executeOnPooledThread(myReadThread);
    myReadThread.waitForStart();
    myStatistics = statistics;
  }


  public int read() throws IOException {
    myStatistics.read(1);
    return myReadThread.read();
  }

  public int read(byte b[], int off, int len) throws IOException {
    int result = myReadThread.read(b, off, len);
    myStatistics.read(result);
    return result;
  }

  public long skip(long n) throws IOException {
    long result = myReadThread.skip(n);
    myStatistics.read(result);
    return result;
  }

  public int available() throws IOException {
    return myReadThread.available();
  }

  public void close() throws IOException {
    myReadThread.close();
  }
}
