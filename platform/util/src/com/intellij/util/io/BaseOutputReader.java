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
  /** See {@link #BaseOutputReader(Reader, Options)}, {@link #readAvailable}, and {@link #processInput} for reference. */
  public static class Options {
    public static final Options BLOCKING = withPolicy(SleepingPolicy.BLOCKING);
    public static final Options NON_BLOCKING = withPolicy(SleepingPolicy.SIMPLE);

    public SleepingPolicy policy() { return null; }
    public boolean splitToLines() { return true; }
    public boolean sendIncompleteLines() { return true; }
    public boolean withSeparators() { return true; }

    public static Options withPolicy(final SleepingPolicy policy) {
      return new Options() {
        @Override
        public SleepingPolicy policy() {
          return policy;
        }
      };
    }
  }

  protected final Reader myReader;

  private final Options myOptions;
  private final char[] myInputBuffer = new char[8192];
  private final StringBuilder myLineBuffer = new StringBuilder();
  private boolean myCarry;

  public BaseOutputReader(@NotNull InputStream inputStream, @Nullable Charset charset) {
    this(createInputStreamReader(inputStream, charset));
  }

  public BaseOutputReader(@NotNull InputStream inputStream, @Nullable Charset charset, @NotNull Options options) {
    this(createInputStreamReader(inputStream, charset), options);
  }

  public BaseOutputReader(@NotNull Reader reader) {
    this(reader, new Options());
  }

  public BaseOutputReader(@NotNull Reader reader, @NotNull Options options) {
    super(options.policy());

    if (options.policy() == SleepingPolicy.BLOCKING && !(reader instanceof BaseInputStreamReader)) {
      throw new IllegalArgumentException("Blocking policy can be used only with BaseInputStreamReader, that doesn't lock on close");
    }

    if (options.policy() != SleepingPolicy.BLOCKING && !options.sendIncompleteLines()) {
      throw new IllegalArgumentException("In non-blocking mode, the reader cannot produce complete lines reliably");
    }

    myReader = reader;
    myOptions = options;
  }

  private static Reader createInputStreamReader(@NotNull InputStream stream, @Nullable Charset charset) {
    return charset == null ? new BaseInputStreamReader(stream) : new BaseInputStreamReader(stream, charset);
  }

  /**
   * Reads as much data as possible without blocking.
   * Relies on InputStream.ready method.
   * When in doubt, take a look at {@link #readAvailableBlocking()}.
   *
   * @return true if non-zero amount of data has been read
   * @throws IOException If an I/O error occurs
   */
  @Override
  protected final boolean readAvailableNonBlocking() throws IOException {
    boolean read = false;

    try {
      int n;
      while (myReader.ready() && (n = myReader.read(myInputBuffer)) >= 0) {
        if (n > 0) {
          read = true;
          processInput(myInputBuffer, myLineBuffer, n);
        }
      }
    }
    finally {
      if (myCarry) {
        myLineBuffer.append('\r');
        myCarry = false;
      }
      if (myLineBuffer.length() > 0) {
        sendText(myLineBuffer);
      }
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
  @Override
  protected final boolean readAvailableBlocking() throws IOException {
    boolean read = false;

    try {
      int n;
      while ((n = myReader.read(myInputBuffer)) >= 0) {
        if (n > 0) {
          read = true;
          processInput(myInputBuffer, myLineBuffer, n);
        }
      }
    }
    finally {
      if (myCarry) {
        myLineBuffer.append('\r');
        myCarry = false;
      }
      if (myLineBuffer.length() > 0) {
        sendText(myLineBuffer);
      }
    }

    return read;
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  private void processInput(char[] buffer, StringBuilder line, int n) {
    if (myOptions.splitToLines()) {
      for (int i = 0; i < n; i++) {
        char c;
        if (i == 0 && myCarry) {
          c = '\r';
          i--;
          myCarry = false;
        }
        else {
          c = buffer[i];
        }

        if (c == '\r') {
          if (i + 1 == n) {
            myCarry = true;
            continue;
          }
          else if (buffer[i + 1] == '\n') {
            continue;
          }
        }

        if (c != '\n' || myOptions.sendIncompleteLines() || myOptions.withSeparators()) {
          line.append(c);
        }

        if (c == '\n') {
          sendText(line);
        }
      }

      if (line.length() > 0 && myOptions.sendIncompleteLines()) {
        sendText(line);
      }
    }
    else {
      onTextAvailable(new String(buffer, 0, n));
    }
  }

  private void sendText(@NotNull StringBuilder line) {
    String text = line.toString();
    line.setLength(0);
    onTextAvailable(text);
  }

  @Override
  protected void close() throws IOException {
    myReader.close();
  }

  protected abstract void onTextAvailable(@NotNull String text);

  //<editor-fold desc="Deprecated stuff.">

  /** @deprecated use {@link #BaseOutputReader(Reader, Options)} (to be removed in IDEA 2018.1) */
  @Deprecated
  public BaseOutputReader(@NotNull Reader reader, @Nullable SleepingPolicy policy) {
    this(reader, Options.withPolicy(policy));
  }

  //</editor-fold>
}