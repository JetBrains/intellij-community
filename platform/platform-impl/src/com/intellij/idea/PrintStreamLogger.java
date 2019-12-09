// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

class PrintStreamLogger extends PrintStream {
  PrintStreamLogger(String name, PrintStream originalStream) {
    super(new OutputStreamLogger(name, originalStream), true);
  }
}

class OutputStreamLogger extends OutputStream {
  private static final int BUFFER_SIZE = 10000;
  private final byte[] myBuffer = new byte[BUFFER_SIZE];
  private final PrintStream myOriginalStream;
  private final Logger myLogger;
  private int myPosition;
  private boolean mySlashRWritten;

  OutputStreamLogger(String name, PrintStream originalStream) {
    myOriginalStream = originalStream;
    myLogger = Logger.getInstance(name);
  }

  @Override
  public void write(int b) {
    myOriginalStream.write(b);
    processByte(b);
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) {
    myOriginalStream.write(b, off, len);
    for (int i = 0; i < len; i++) {
      processByte(b[off + i]);
    }
  }

  private synchronized void processByte(int b) {
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
