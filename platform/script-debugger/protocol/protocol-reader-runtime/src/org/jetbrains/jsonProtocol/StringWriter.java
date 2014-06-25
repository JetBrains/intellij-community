package org.jetbrains.jsonProtocol;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;

public class StringWriter extends Writer {
  private final StringBuilder builder;

  public StringWriter() {
    builder = new StringBuilder();
    lock = builder;
  }

  public StringWriter(int initialSize) {
    if (initialSize < 0) {
      throw new IllegalArgumentException("Negative buffer size");
    }

    builder = new StringBuilder(initialSize);
    lock = builder;
  }

  @Override
  public void write(int c) {
    builder.append((char)c);
  }

  @Override
  public void write(@NotNull char[] chars, int off, int len) {
    builder.append(chars, off, len);
  }

  @Override
  public void write(@NotNull String string) {
    builder.append(string);
  }

  @Override
  public void write(@NotNull String str, int off, int length) {
    builder.append(str, off, off + length);
  }

  @Override
  public StringWriter append(CharSequence charSequence) {
    builder.append(charSequence);
    return this;
  }

  @Override
  public StringWriter append(CharSequence charSequence, int start, int end) {
    builder.append(charSequence, start, end);
    return this;
  }

  @Override
  public StringWriter append(char c) {
    write(c);
    return this;
  }

  public String toString() {
    return builder.toString();
  }

  public StringBuilder getBuffer() {
    return builder;
  }

  /**
   * Flush the stream.
   */
  @Override
  public void flush() {
  }

  @Override
  public void close() throws IOException {
  }
}