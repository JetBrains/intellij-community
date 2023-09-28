// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

final class PrintStreamLogger extends PrintStream {
  PrintStreamLogger(String name, PrintStream originalStream) {
    super(new OutputStreamLogger(name, originalStream), true);
  }
}

final class OutputStreamLogger extends OutputStream {
  private static final int BUFFER_SIZE = 10000;
  private final byte[] myBuffer = new byte[BUFFER_SIZE];
  private final PrintStream myOriginalStream;
  @SuppressWarnings("NonConstantLogger") private final Logger myLogger;
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
  public void write(byte @NotNull [] b, int off, int len) {
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

  private final AtomicBoolean myStackOverflowProtectionTriggered = new AtomicBoolean();
  private final ThreadLocal<Boolean> myStackOverflowProtection = ThreadLocal.withInitial(() -> false);

  private void writeBuffer() {
    if (myStackOverflowProtection.get()) {
      if (myStackOverflowProtectionTriggered.compareAndSet(false, true)) {
        myLogger.error("Stack overflow protection triggered in logger. A standard stream overridden to write to logs?");
      }

      return;
    }

    myStackOverflowProtection.set(true);
    try {
      myLogger.info(new String(myBuffer, 0, myPosition, Charset.defaultCharset()));
      myPosition = 0;
    } finally {
      myStackOverflowProtection.set(false);
    }
  }
}
