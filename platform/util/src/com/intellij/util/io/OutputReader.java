/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;

public abstract class OutputReader extends BaseOutputReader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.OutputReader");

  private final Semaphore myReadFullySemaphore = new Semaphore();


  public OutputReader(@NotNull Reader reader) {
    super(reader);
    start();
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

        Thread.sleep(mySleepingPolicy.getTimeToSleep(read));
      }
    }
    catch (InterruptedException ignore) {
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

  public void readFully() throws InterruptedException {
    myReadFullySemaphore.down();
    while (!myReadFullySemaphore.waitForUnsafe(10)) {
      if (isStopped) {
        waitFor();
        return;
      }
    }
  }

}
