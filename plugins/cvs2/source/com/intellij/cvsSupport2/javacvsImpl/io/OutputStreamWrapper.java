package com.intellij.cvsSupport2.javacvsImpl.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * author: lesya
 */
public class OutputStreamWrapper extends OutputStream{
  private final OutputStream myOutputStream;
  private final ReadWriteStatistics myStatistics;

  public OutputStreamWrapper(OutputStream outputStream, ReadWriteStatistics statistics) {
    myOutputStream = outputStream;
    myStatistics = statistics;
  }

  public void write(int b) throws IOException {
    myOutputStream.write(b);
    myStatistics.send(1);
  }

  public void close() throws IOException {
    myOutputStream.close();
  }

  public void write(byte b[]) throws IOException {
    myOutputStream.write(b);
    myStatistics.send(b.length);
  }

  public void write(byte b[], int off, int len) throws IOException {
    myOutputStream.write(b, off, len);
    myStatistics.send(len);

  }

  public void flush() throws IOException {
    myOutputStream.flush();
  }
}
