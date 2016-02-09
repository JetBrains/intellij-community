/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * @author traff
 */
public abstract class BaseOutputReader extends BaseDataReader {
  protected final Reader myReader;

  private final char[] myInputBuffer = new char[8192];
  private final StringBuilder myLineBuffer = new StringBuilder();

  public BaseOutputReader(@NotNull InputStream inputStream, @Nullable Charset charset) {
    this(inputStream, charset, null);
  }

  public BaseOutputReader(@NotNull InputStream inputStream, @Nullable Charset charset, @Nullable SleepingPolicy sleepingPolicy) {
    this(createInputStreamReader(inputStream, charset), sleepingPolicy);
  }

  public BaseOutputReader(@NotNull Reader reader) {
    this(reader, null);
  }

  public BaseOutputReader(@NotNull Reader reader, SleepingPolicy sleepingPolicy) {
    super(sleepingPolicy);
    if (sleepingPolicy == SleepingPolicy.BLOCKING && !(reader instanceof BaseInputStreamReader)) {
      throw new IllegalArgumentException("Blocking policy can be used only with BaseInputStreamReader, that doesn't lock on close");
    }
    myReader = reader;
  }

  private static Reader createInputStreamReader(@NotNull InputStream stream, @Nullable Charset charset) {
    return charset == null ? new BaseInputStreamReader(stream) : new BaseInputStreamReader(stream, charset);
  }

  /**
   * Reads as much data as possible without blocking.
   * Relies on InputStream.ready method.
   * In case of doubts look at #readAvailableBlocking
   *
   * @return true if non-zero amount of data has been read
   * @throws IOException If an I/O error occurs
   */
  protected final boolean readAvailableNonBlocking() throws IOException {
    boolean read = false;

    int n;
    while (myReader.ready() && (n = myReader.read(myInputBuffer)) > 0) {
      read = true;
      processLine(myInputBuffer, myLineBuffer, n);
    }

    if (myLineBuffer.length() > 0) {
      sendLine(myLineBuffer);
    }

    return read;
  }

  /**
   * Reads data with blocking.
   * Should be used in case when ready method always returns false for your input stream.
   * Should be used if we want to to make our reader exit when end of stream reached.
   * Could be used if we prefer IO-blocking over CPU sleeping.
   *
   * @return true if non-zero amount of data has been read, false if end of the stream is reached
   * @throws IOException If an I/O error occurs
   */
  protected final boolean readAvailableBlocking() throws IOException {
    boolean read = false;

    int n;
    while ((n = myReader.read(myInputBuffer)) > 0) {
      read = true;
      processLine(myInputBuffer, myLineBuffer, n);

      if (!myReader.ready()) {
        if (myLineBuffer.length() > 0) sendLine(myLineBuffer);
        onBufferExhaustion();
      }
    }

    if (myLineBuffer.length() > 0) {
      sendLine(myLineBuffer);
    }

    return read;
  }

  protected final void processLine(char[] buffer, StringBuilder line, int n) {
    for (int i = 0; i < n; i++) {
      char c = buffer[i];

      if (c == '\n' && line.length() > 0 && line.charAt(line.length() - 1) == '\r') {
        line.setCharAt(line.length() - 1, '\n');
      }
      else {
        line.append(c);
      }

      if (c == '\n') {
        sendLine(line);
      }
    }
  }

  private void sendLine(@NotNull StringBuilder line) {
    onTextAvailable(line.toString());
    line.setLength(0);
  }

  @Override
  protected boolean readAvailable() throws IOException {
    return mySleepingPolicy == SleepingPolicy.BLOCKING ? readAvailableBlocking() : readAvailableNonBlocking();
  }

  @Override
  protected void close() throws IOException {
    myReader.close();
  }

  @Override
  public void stop() {
    super.stop();
    if (mySleepingPolicy == SleepingPolicy.BLOCKING) {
      // we can't count on super.stop() since it only sets 'isRunning = false', and blocked Reader.read won't wake up.
      try { close(); }
      catch (IOException ignore) { }
    }
  }

  protected void onBufferExhaustion() { }

  protected abstract void onTextAvailable(@NotNull String text);
}