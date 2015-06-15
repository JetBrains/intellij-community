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
package com.intellij.idea;

import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author mike
 */
@Bombed(day = 20, month = Calendar.JUNE)
public class LockSupportTest extends TestCase {
  public void testLock() throws Exception {
    final SocketLock lock = new SocketLock();
    File temp = FileUtil.createTempDirectory("c", null);
    try {
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, lock.lock(temp.getPath() + "/c", temp.getPath() + "/s"));
    }
    finally {
      lock.dispose();

      FileUtil.delete(temp);
    }
  }

  public void testTwoLocks() throws Exception {
    List<SocketLock> toClose = new ArrayList<SocketLock>();
    File temp = FileUtil.createTempDirectory("c", null);
    try {
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLock(toClose).lock(temp.getPath() + "/1", temp.getPath() + "/1-"));
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLock(toClose).lock(temp.getPath() + "/1.1", temp.getPath() + "/1-1"));
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLock(toClose).lock(temp.getPath() + "/2", temp.getPath() + "/2-"));

      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLock(toClose).lock(temp.getPath() + "/2", temp.getPath() + "/2-"));
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLock(toClose).lock(temp.getPath() + "/1", temp.getPath() + "/1-"));
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLock(toClose).lock(temp.getPath() + "/1.1", temp.getPath() + "/1-1"));
    }
    finally {
      for (SocketLock lock : toClose) {
        lock.dispose();
      }

      FileUtil.delete(temp);
    }
  }

  @NotNull
  private static SocketLock createLock(@NotNull List<SocketLock> toClose) {
    SocketLock lock1 = new SocketLock();
    toClose.add(lock1);
    return lock1;
  }

  public void testDispose() throws Exception {
    final SocketLock lock1 = new SocketLock();
    final SocketLock lock2 = new SocketLock();

    File temp = FileUtil.createTempDirectory("c", null);
    try {
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, lock1.lock(temp.getPath() + "/1", temp.getPath() + "/1-"));
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, lock2.lock(temp.getPath() + "/1", temp.getPath() + "/1-"));

      lock1.dispose();
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, lock2.lock(temp.getPath() + "/1", temp.getPath() + "/1-"));
      lock2.dispose();
    }
    finally {
      FileUtil.delete(temp);
    }
  }
}
