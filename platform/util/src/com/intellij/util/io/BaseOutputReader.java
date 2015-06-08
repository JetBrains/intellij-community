/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author traff
 */
public abstract class BaseOutputReader extends BaseDataReader {
  protected static final int DELAY_AFTER_BLOCKING_READ = 10;

  protected final Reader myReader;

  private final char[] myBuffer = new char[8192];
  private final StringBuilder myTextBuffer = new StringBuilder();
  private ExecutorService myExecutorService;
  private boolean skipLF = false;
  private volatile Future myScheduledSubmitter;
  private Runnable myTokenSubmitter;

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
    if (sleepingPolicy == SleepingPolicy.BLOCKING) {
      if (!(reader instanceof BaseInputStreamReader)) {
        throw new IllegalArgumentException("Blocking policy can be used only with BaseInputStreamReader, that doesn't lock on close");
      }
      myExecutorService = Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Base output reader"));
      myTokenSubmitter = new Runnable() {
        @Override
        public void run() {
          try {
            Thread.sleep(DELAY_AFTER_BLOCKING_READ);
            submitToken();
          }
          catch (InterruptedException ignore) { }
        }
      };
    }
    myReader = reader;
  }

  private static Reader createInputStreamReader(final @NotNull InputStream streamToRead, @Nullable Charset charset) {
    if (charset == null) {
      return new BaseInputStreamReader(streamToRead);
    }
    else {
      return new BaseInputStreamReader(streamToRead, charset);
    }
  }

  private void processLine(char[] buffer, StringBuilder token, int n) {
    for (int i = 0; i < n; i++) {
      char c = buffer[i];
      if (skipLF && c != '\n') {
        token.append('\r');
      }

      if (c == '\r') {
        skipLF = true;
      }
      else {
        skipLF = false;
        token.append(c);
      }

      if (c == '\n') {
        onTextAvailable(token.toString());
        token.setLength(0);
      }
    }
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
    char[] buffer = myBuffer;
    StringBuilder token = myTextBuffer;
    token.setLength(0);

    boolean read = false;
    while (myReader.ready()) {
      int n = myReader.read(buffer);
      if (n <= 0) break;
      read = true;

      processLine(buffer, token, n);
    }

    submitToken();

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
    char[] buffer = myBuffer;
    StringBuilder token = myTextBuffer;
    token.setLength(0);

    boolean read = false;
    int n;
    while ((n = myReader.read(buffer)) > 0) {
      if (myScheduledSubmitter != null) myScheduledSubmitter.cancel(true);

      read = true;

      synchronized (myTextBuffer) {
        processLine(buffer, token, n);
      }

      myScheduledSubmitter = myExecutorService.submit(myTokenSubmitter);
    }

    submitToken();

    return read;
  }

  private void submitToken() {
    synchronized (myTextBuffer) {
      if (myTextBuffer.length() != 0) {
        onTextAvailable(myTextBuffer.toString());
        myTextBuffer.setLength(0);
      }
    }
  }

  @Nullable
  protected Future getScheduledSubmitter() {
    return myScheduledSubmitter;
  }

  @Override
  protected boolean readAvailable() throws IOException {
    return mySleepingPolicy == SleepingPolicy.BLOCKING ? readAvailableBlocking() : readAvailableNonBlocking();
  }

  @Override
  protected void close() throws IOException {
    myReader.close();
  }

  protected abstract void onTextAvailable(@NotNull String text);
}
