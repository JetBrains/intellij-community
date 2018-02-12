/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, lock1.lock());
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