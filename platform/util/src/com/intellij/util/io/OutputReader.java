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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

public abstract class OutputReader extends BaseOutputReader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.OutputReader");
  private static final int READ_FULLY_TIMEOUT = 10;

  private final Semaphore myReadFullySemaphore = new Semaphore();

  /** @deprecated use {@link #OutputReader(InputStream, Charset, String)} to be removed in IDEA 16 */
  @Deprecated
  public OutputReader(@NotNull InputStream inputStream, @Nullable Charset charset) {
    this(inputStream, charset, "");
  }

  /** @deprecated use {@link #OutputReader(InputStream, Charset, SleepingPolicy, String)} to be removed in IDEA 16 */
  @Deprecated
  public OutputReader(@NotNull InputStream inputStream, @Nullable Charset charset, @Nullable SleepingPolicy sleepingPolicy) {
    this(inputStream, charset, sleepingPolicy, "");
  }

  public OutputReader(@NotNull InputStream inputStream, @Nullable Charset charset, @NotNull String presentableName) {
    super(inputStream, charset);
    start(presentableName);
  }

  public OutputReader(@NotNull InputStream inputStream,
                      @Nullable Charset charset,
                      @Nullable SleepingPolicy sleepingPolicy,
                      @NotNull String presentableName) {
    super(inputStream, charset, sleepingPolicy);
    start(presentableName);
  }

  /** @deprecated to be removed in IDEA 16 */
  @Deprecated
  public OutputReader(@NotNull Reader reader) {
    super(reader);
    start("");
  }

  @Override
  protected void doRun() {
    try {
      while (true) {
        boolean read = readAvailable();

        if (!read) {
          myReadFullySemaphore.up();
        }

        if (isStopped) {
          break;
        }

        TimeoutUtil.sleep(mySleepingPolicy.getTimeToSleep(read));
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      try {
        myReader.close();
      }
      catch (IOException e) {
        LOG.warn("Can't close reader", e);
      }
    }
  }

  @Override
  protected void onBufferExhaustion() {
    if (mySleepingPolicy == SleepingPolicy.BLOCKING) {
      myReadFullySemaphore.up();
    }
  }

  public void readFully() throws InterruptedException {
    if (mySleepingPolicy != SleepingPolicy.BLOCKING) {
      myReadFullySemaphore.down();
      while (!myReadFullySemaphore.waitForUnsafe(READ_FULLY_TIMEOUT)) {
        if (isStopped) {
          waitFor();
          return;
        }
      }
    }
    else {
      do {
        myReadFullySemaphore.down();
      }
      while (myReadFullySemaphore.waitForUnsafe(READ_FULLY_TIMEOUT));

      myReadFullySemaphore.up();

      if (isStopped) waitFor();
    }
  }
}