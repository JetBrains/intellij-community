// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

public abstract class BaseOutputReader extends BaseDataReader {
  /**
   * See {@link #BaseOutputReader(Reader, Options)}, {@link #readAvailable}, {@link BaseDataReader.SleepingPolicy}
   * and {@link #processInput} for reference.
   *
   */
  public static class Options {
    /**
     * @see BaseDataReader.SleepingPolicy#BLOCKING
     */
    public static final Options BLOCKING = withPolicy(SleepingPolicy.BLOCKING);
    /**
     * @see BaseDataReader.SleepingPolicy#NON_BLOCKING
     */
    public static final Options NON_BLOCKING = withPolicy(SleepingPolicy.NON_BLOCKING);

    @NotNull
    public SleepingPolicy policy() { return SleepingPolicy.BLOCKING; }

    public boolean splitToLines() { return true; }

    public boolean sendIncompleteLines() { return true; }

    public boolean withSeparators() { return true; }

    public static Options withPolicy(final @NotNull SleepingPolicy policy) {
      return new Options() {
        @Override
        public @NotNull SleepingPolicy policy() {
          return policy;
        }
      };
    }

    public static Options forMostlySilentProcess() {
      if (SystemProperties.getBooleanProperty("output.reader.blocking.mode.for.mostly.silent.processes", true)) {
        return BLOCKING;
      }
      return NON_BLOCKING;
    }

    public static @NotNull Options forTerminalPtyProcess() {
      return new BaseOutputReader.Options() {
        @Override
        public boolean splitToLines() {
          return false; // prevent converting \r\n to \n to render in terminal emulator correctly
        }
      };
    }

    /**
     * Creates same options but with a provided policy
     */
    @ApiStatus.Internal
    @NotNull
    public final Options copyWithPolicy(@NotNull SleepingPolicy newPolicy) {
      Options original = this;
      return new Options() {
        @Override
        public @NotNull SleepingPolicy policy() {
          return newPolicy;
        }

        @Override
        public boolean splitToLines() {
          return original.splitToLines();
        }

        @Override
        public boolean sendIncompleteLines() {
          return original.sendIncompleteLines();
        }

        @Override
        public boolean withSeparators() {
          return original.withSeparators();
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
      if (myLineBuffer.length() > 0 && myOptions.sendIncompleteLines()) {
        sendText(myLineBuffer);
      }
    }

    return read;
  }

  /**
   * Reads data with blocking.
   * Should be used in case when {@link Reader#ready()} method always returns {@code false} for your input stream.
   * Should be used if we want to make our reader exit when the end of a stream is reached.
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

  @Override
  protected void flush() {
    if (myLineBuffer.length() > 0) {
      sendText(myLineBuffer);
    }
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
}
