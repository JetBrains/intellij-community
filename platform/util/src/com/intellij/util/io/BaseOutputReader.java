/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.io.IOException;
import java.io.Reader;

/**
 * @author traff
 */
public abstract class BaseOutputReader extends BaseDataReader {

  protected final Reader myReader;

  private final char[] myBuffer = new char[8192];
  private final StringBuilder myTextBuffer = new StringBuilder();
  private boolean skipLF = false;

  public BaseOutputReader(@NotNull Reader reader) {
    this(reader, null);
  }

  public BaseOutputReader(@NotNull Reader reader, SleepingPolicy sleepingPolicy) {
    super(sleepingPolicy);
    myReader = reader;
  }

  /**
   * Reads as much data as possible without blocking.
   * @return true if non-zero amount of data has been read
   * @exception  IOException  If an I/O error occurs
   */
  protected final boolean readAvailable() throws IOException {
    char[] buffer = myBuffer;
    StringBuilder token = myTextBuffer;
    token.setLength(0);

    boolean read = false;
    while (myReader.ready()) {
      int n = myReader.read(buffer);
      if (n <= 0) break;
      read = true;

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

    if (token.length() != 0) {
      onTextAvailable(token.toString());
      token.setLength(0);
    }
    return read;
  }

  @Override
  protected void close() throws IOException {
    myReader.close();
  }

  protected abstract void onTextAvailable(@NotNull String text);
}
