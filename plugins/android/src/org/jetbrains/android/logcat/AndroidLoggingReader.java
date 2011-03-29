/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.logcat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;

/**
* @author Eugene.Kudelevsky
*/
public abstract class AndroidLoggingReader extends Reader {
  @NotNull
  protected abstract Object getLock();

  @Nullable
  protected abstract Reader getReader();

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    Reader reader;
    synchronized (getLock()) {
      reader = getReader();
    }
    return reader != null ? reader.read(cbuf, off, len) : -1;
  }

  @Override
  public boolean ready() throws IOException {
    Reader reader = getReader();
    return reader != null ? reader.ready() : false;
  }

  @Override
  public void close() throws IOException {
    Reader reader = getReader();
    if (reader != null) reader.close();
  }
}
