/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.io.*;

/* Unsync version of java.io.DataOutputStream */
public class DataOutputStream extends java.io.DataOutputStream {
  public DataOutputStream(OutputStream out) {
    super(out);
  }

  @Override
  public void write(int b) throws IOException {
    out.write(b);
    written ++;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    out.write(b, off, len);
    written += len;
  }

  public int getWrittenBytesCount() {
    return written;
  }

  public int resetWrittenBytesCount() {
    int result = written;
    written = 0;
    return result;
  }
}
