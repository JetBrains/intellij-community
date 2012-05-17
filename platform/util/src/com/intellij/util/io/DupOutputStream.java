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

/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public class DupOutputStream extends OutputStream {
  private final OutputStream myStream1;
  private final OutputStream myStream2;

  public DupOutputStream(@NotNull OutputStream stream1, @NotNull OutputStream stream2) {
    myStream1 = stream1;
    myStream2 = stream2;
  }

  @Override
  public void write(final int b) throws IOException {
    myStream1.write(b);
    myStream2.write(b);
  }

  @Override
  public void close() throws IOException {
    myStream1.close();
    myStream2.close();
  }

  @Override
  public void flush() throws IOException {
    myStream1.flush();
    myStream2.flush();
  }

  @Override
  public void write(final byte[] b) throws IOException {
    myStream1.write(b);
    myStream2.write(b);
  }

  @Override
  public void write(final byte[] b, final int off, final int len) throws IOException {
    myStream1.write(b, off, len);
    myStream2.write(b, off, len);
  }
}
