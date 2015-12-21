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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
* @author Konstantin Kolosovsky.
*/
public abstract class BinaryOutputReader extends BaseDataReader {
  @NotNull private final InputStream myStream;
  @NotNull private final byte[] myBuffer = new byte[8192];

  public BinaryOutputReader(@NotNull InputStream stream, @NotNull SleepingPolicy sleepingPolicy) {
    super(sleepingPolicy);
    myStream = stream;
  }

  @Override
  protected boolean readAvailable() throws IOException {
    byte[] buffer = myBuffer;

    boolean read = false;
    while (myStream.available() > 0) {
      int n = myStream.read(buffer);
      if (n <= 0) break;
      read = true;

      onBinaryAvailable(buffer, n);
    }

    return read;
  }

  protected abstract void onBinaryAvailable(@NotNull byte[] data, int size);

  @Override
  protected void close() throws IOException {
    myStream.close();
  }
}
