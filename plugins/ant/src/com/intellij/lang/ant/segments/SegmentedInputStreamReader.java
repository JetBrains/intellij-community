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
package com.intellij.lang.ant.segments;


import java.io.IOException;
import java.io.Reader;

/**
 * @author Eugene Zhuravlev
*         Date: Apr 25, 2007
*/
public class SegmentedInputStreamReader extends Reader {
  private final SegmentedInputStream myStream;

  public SegmentedInputStreamReader(SegmentedInputStream stream) {
    myStream = stream;
  }

  public void close() throws IOException {
    myStream.close();
  }

  public boolean ready() throws IOException {
    return myStream.available() > 0;
  }

  public int read(final char[] cbuf, final int off, final int len) throws IOException {
    int bytesRead = 0;
    while (ready() && bytesRead < len) {
      final int aChar = myStream.read();
      if (aChar == -1) {
        return bytesRead == 0 ? -1 : bytesRead;
      }
      cbuf[off + (bytesRead++)] = (char)aChar;
    }
    return bytesRead;
  }
}
