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

import java.io.IOException;
import java.io.InputStream;

public abstract class BinaryOutputReader extends BaseDataReader {
  @NotNull private final InputStream myStream;
  private final byte @NotNull [] myBuffer = new byte[8192];

  public BinaryOutputReader(@NotNull InputStream stream, @NotNull SleepingPolicy sleepingPolicy) {
    super(sleepingPolicy);
    myStream = stream;
  }

  @Override
  protected boolean readAvailableNonBlocking() throws IOException {
    byte[] buffer = myBuffer;
    boolean read = false;

    int n;
    while (myStream.available() > 0 && (n = myStream.read(buffer)) >= 0) {
      if (n > 0) {
        read = true;
        onBinaryAvailable(buffer, n);
      }
    }

    return read;
  }

  @Override
  protected final boolean readAvailableBlocking() throws IOException {
    byte[] buffer = myBuffer;
    boolean read = false;

    int n;
    while ((n = myStream.read(buffer)) >= 0) {
      if (n > 0) {
        read = true;
        onBinaryAvailable(buffer, n);
      }
    }

    return read;
  }

  protected abstract void onBinaryAvailable(byte @NotNull [] data, int size);

  @Override
  protected void close() throws IOException {
    myStream.close();
  }
}