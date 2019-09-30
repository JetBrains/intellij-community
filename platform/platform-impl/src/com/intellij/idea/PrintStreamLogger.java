// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

public class PrintStreamLogger extends PrintStream {
  public PrintStreamLogger(String name) {
    super(new OutputStreamLogger(name), true);
  }
}

class OutputStreamLogger extends OutputStream {
  private static final int BUFFER_SIZE = 10000;
  private final byte[] myBuffer = new byte[BUFFER_SIZE];
  private final Logger myLogger;
  private int myPosition;
  private boolean mySlashRWritten;

  OutputStreamLogger(String name) {
    myLogger = Logger.getInstance(name);
  }

  @Override
  public synchronized void write(int b) throws IOException {
    if (b == '\r') {
      writeBuffer();
      mySlashRWritten = true;
    }
    else if (b == '\n') {
      if (mySlashRWritten) {
        mySlashRWritten = false;
      }
      else {
        writeBuffer();
      }
    }
    else {
      mySlashRWritten = false;
      if (myPosition == BUFFER_SIZE) {
        writeBuffer();
      }
      myBuffer[myPosition++] = (byte)b;
    }
  }

  private void writeBuffer() {
    myLogger.info(new String(myBuffer, 0, myPosition, Charset.defaultCharset()));
    myPosition = 0;
  }
}
