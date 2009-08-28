package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 9, 2003
 * Time: 2:20:15 AM
 * To change this template use Options | File Templates.
 */
public class ProgressStream extends InputStream {
  private InputStream myInputStream;
  private ProgressIndicator myProgressIndicator;
  private long available;
  private long count;

  public ProgressStream(InputStream inputStream, ProgressIndicator progressIndicator) {
    this (0, 0, inputStream, progressIndicator);
  }

  public ProgressStream(long start, long available, InputStream inputStream, ProgressIndicator progressIndicator) {
    count = start;
    this.available = available;
    myInputStream = inputStream;
    myProgressIndicator = progressIndicator;
  }

  public int read() throws IOException {
    if (myProgressIndicator != null) {
      if (myProgressIndicator.isCanceled()) {
        throw new RuntimeException (new InterruptedException());
      }

      if (available > 0) {
        myProgressIndicator.setFraction((double) count++ / (double) available);
      } else {
        myProgressIndicator.setFraction((double) 1 - (double) 1 / (double) count++);
      }
    }
    return myInputStream.read();
  }
}
