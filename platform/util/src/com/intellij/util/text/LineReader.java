// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class LineReader {
  private int myPos = -1;
  private final int[] myBuffer = new int[2];
  private final InputStream myInputStream;
  private boolean myAtEnd = false;

  public LineReader(InputStream in) {
    myInputStream = in;
  }

  public List<byte[]> readLines() throws IOException {

    ArrayList<byte[]> result = new ArrayList<>();
    byte[] line;
    while ((line = readLineInternal()) != null) result.add(line);
    return result;
  }

  public byte[] readLine() throws IOException {
    return readLineInternal();
  }

  private int read() throws IOException {
    if (myPos >= 0) {
      int result = myBuffer[myPos];
      myPos--;
      return result;
    }
    return myInputStream.read();
  }

  private final class ReadLine {
    private String myCurrentEOL = "";
    private ByteArrayOutputStream myResult = null;

    public byte @Nullable [] execute() throws IOException {

      if (myAtEnd) return null;

      synchronized (myInputStream) {
        while (true) {
          int ch = read();
          if (ch < 0)
            return processEndOfStream();
          if (notLineSeparator(ch)) {
            if (myCurrentEOL.equals("\r")) {
              unread(ch);
              return getResult();
            } else if (myCurrentEOL.equals("\r\r")) {
              unread(ch);
              unread('\r');
              return getResult();
            } else {
              appendToResult(ch);
              continue;
            }
          }
          if (ch == '\r') {
            if (myCurrentEOL.isEmpty() || myCurrentEOL.equals("\r")) {
              myCurrentEOL += "\r";
            } else if (myCurrentEOL.equals("\r\r")) {
              unread('\r');
              unread('\r');
              return getResult();
            }
            continue;
          }
          if (ch == '\n') {
            return getResult();
          }
        }
      }
    }

    private boolean notLineSeparator(int ch) {
      return ch != '\r' && ch != '\n';
    }

    private void appendToResult(int ch) {
      createResult();
      myResult.write(ch);
    }

    private byte[] getResult() {
      createResult();
      try {
        myResult.flush();
      } catch (IOException e) {
        //ignore
      }

      return myResult.toByteArray();
    }

    private void createResult() {
      if (myResult == null) myResult = new ByteArrayOutputStream();
    }

    private byte[] processEndOfStream() {
      myAtEnd = true;
      return getResult();
    }
  }

  private byte @Nullable [] readLineInternal() throws IOException {
    return new ReadLine().execute();
  }

  private void unread(int b) throws IOException {
    myPos++;
    if (myPos >= myBuffer.length)
      throw new IOException("Push back buffer is full");
    myBuffer[myPos] = b;

  }
}
