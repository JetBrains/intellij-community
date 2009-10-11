/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util;

import java.io.IOException;
import java.io.OutputStream;

public class ScrambledOutputStream extends OutputStream{
  static final int MASK = 0xAA;
  private final OutputStream myOriginalStream;

  public ScrambledOutputStream(OutputStream originalStream) {
    myOriginalStream = originalStream;
  }

  public void write(int b) throws IOException {
    myOriginalStream.write(b ^ MASK);
  }

  public void write(byte[] b, int off, int len) throws IOException {
    byte[] newBytes = new byte[len];
    for(int i = 0; i < len; i++) {
      newBytes[i] = (byte)(b[off + i] ^ MASK);      
    }
    myOriginalStream.write(newBytes, 0, len);
  }

  public void flush() throws IOException {
    myOriginalStream.flush();
  }

  public void close() throws IOException {
    myOriginalStream.close();
  }

}
