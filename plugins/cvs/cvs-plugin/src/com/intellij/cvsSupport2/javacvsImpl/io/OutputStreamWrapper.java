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
package com.intellij.cvsSupport2.javacvsImpl.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * author: lesya
 */
public class OutputStreamWrapper extends OutputStream{
  private final OutputStream myOutputStream;
  private final ReadWriteStatistics myStatistics;

  public OutputStreamWrapper(OutputStream outputStream, ReadWriteStatistics statistics) {
    myOutputStream = outputStream;
    myStatistics = statistics;
  }

  public void write(int b) throws IOException {
    myOutputStream.write(b);
    myStatistics.send(1);
  }

  public void close() throws IOException {
    myOutputStream.close();
  }

  public void write(byte[] b) throws IOException {
    myOutputStream.write(b);
    myStatistics.send(b.length);
  }

  public void write(byte[] b, int off, int len) throws IOException {
    myOutputStream.write(b, off, len);
    myStatistics.send(len);

  }

  public void flush() throws IOException {
    myOutputStream.flush();
  }
}
