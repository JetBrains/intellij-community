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
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.openapi.application.ApplicationManager;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;

import java.io.IOException;
import java.io.InputStream;

/**
 * author: lesya
 */
public class InputStreamWrapper extends InputStream {
  private final ReadThread myReadThread;
  private final ReadWriteStatistics myStatistics;

  public InputStreamWrapper(InputStream original, ICvsCommandStopper cvsCommandStopper, ReadWriteStatistics statistics) {
    myReadThread = new ReadThread(original, cvsCommandStopper);
    myReadThread.prepareForWait();
    startThread(myReadThread);
    myReadThread.waitForStart();
    myStatistics = statistics;
  }

  protected void startThread(Runnable runnable) {
    ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }

  @Override
  public int read() throws IOException {
    myStatistics.read(1);
    return myReadThread.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int result = myReadThread.read(b, off, len);
    myStatistics.read(result);
    return result;
  }

  @Override
  public long skip(long n) throws IOException {
    long result = myReadThread.skip(n);
    myStatistics.read(result);
    return result;
  }

  @Override
  public int available() throws IOException {
    return myReadThread.available();
  }

  @Override
  public void close() throws IOException {
    myReadThread.close();
  }
}
