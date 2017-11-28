/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.rules.TempDirectory;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class LockSupportTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test(timeout = 30000)
  public void testUseCanonicalPathLock() throws Exception {
    assumeTrue(!SystemInfo.isFileSystemCaseSensitive);

    String path1 = tempDir.getRoot().getPath();
    String path2 = path1.toUpperCase(Locale.ENGLISH);

    SocketLock lock1 = new SocketLock(path1 + "/c", path1 + "/s");
    SocketLock lock2 = new SocketLock(path2 + "/c", path2 + "/s");
    try {
      lock1.lock();
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, lock2.lock());
    }
    finally {
      lock1.dispose();
      lock2.dispose();
    }
  }

  @Test(timeout = 30000)
  public void testLock() throws Exception {
    SocketLock lock = new SocketLock(tempDir.getRoot().getPath() + "/c", tempDir.getRoot().getPath() + "/s");
    try {
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, lock.lock());
    }
    finally {
      lock.dispose();
    }
  }

  @Test(timeout = 30000)
  public void testTwoLocks() throws Exception {
    List<SocketLock> toClose = new ArrayList<>();
    try {
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLock(toClose, tempDir.getRoot(), "c1", "s1").lock());
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLock(toClose, tempDir.getRoot(), "c2", "s2").lock());
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLock(toClose, tempDir.getRoot(), "c3", "s3").lock());

      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLock(toClose, tempDir.getRoot(), "c1", "s1").lock());
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLock(toClose, tempDir.getRoot(), "c2", "s2").lock());
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLock(toClose, tempDir.getRoot(), "c3", "s3").lock());
    }
    finally {
      toClose.forEach(SocketLock::dispose);
    }
  }

  @NotNull
  private static SocketLock createLock(@NotNull List<SocketLock> toClose, @NotNull File dir, @NotNull String cfg, @NotNull String sys) {
    SocketLock lock = new SocketLock(dir.getPath() + "/" + cfg, dir.getPath() + "/" + sys);
    toClose.add(lock);
    return lock;
  }

  @Test(timeout = 30000)
  public void testDispose() throws Exception {
    SocketLock lock1 = new SocketLock(tempDir.getRoot().getPath() + "/c", tempDir.getRoot().getPath() + "/s");
    SocketLock lock2 = new SocketLock(tempDir.getRoot().getPath() + "/c", tempDir.getRoot().getPath() + "/s");

    assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, lock1.lock());
    assertEquals(SocketLock.ActivateStatus.ACTIVATED, lock2.lock());

    lock1.dispose();
    assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, lock2.lock());
    lock2.dispose();
  }
}