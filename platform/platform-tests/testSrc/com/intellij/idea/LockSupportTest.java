// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.rules.TempDirectory;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class LockSupportTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test(timeout = 30000)
  public void testUseCanonicalPathLock() throws Exception {
    assumeTrue("case-insensitive system-only", !SystemInfo.isFileSystemCaseSensitive);

    String path1 = tempDir.getRoot().getPath();
    String path2 = path1.toUpperCase(Locale.ENGLISH);

    SocketLock lock1 = new SocketLock(path1 + "/c", path1 + "/s");
    SocketLock lock2 = new SocketLock(path2 + "/c", path2 + "/s");
    try {
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, tryActivate(lock1));
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, tryActivate(lock2));
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
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, lock.lockAndTryActivate().getActivateStatus());
    }
    finally {
      lock.dispose();
    }
  }

  @Test(timeout = 30000)
  public void testTwoLocks() throws Exception {
    List<SocketLock> toClose = new ArrayList<>();
    try {
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot(), "c1", "s1"));
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot(), "c2", "s2"));
      assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot(), "c3", "s3"));

      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRoot(), "c1", "s1"));
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRoot(), "c2", "s2"));
      assertEquals(SocketLock.ActivateStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRoot(), "c3", "s3"));
    }
    finally {
      toClose.forEach(SocketLock::dispose);
    }
  }

  @NotNull
  private static SocketLock.ActivateStatus createLockAndTryActivate(@NotNull List<SocketLock> toClose, @NotNull File dir, @NotNull String cfg, @NotNull String sys)
    throws Exception {
    SocketLock lock = new SocketLock(dir.getPath() + "/" + cfg, dir.getPath() + "/" + sys);
    toClose.add(lock);

    return tryActivate(lock);
  }

  @NotNull
  private static SocketLock.ActivateStatus tryActivate(@NotNull SocketLock lock) throws Exception {
    for (int attempt = 0; attempt < 10; attempt++) {
      try {
        return lock.lockAndTryActivate().getActivateStatus();
      }
      // The case of creating two parallel locks on one JVM
      catch (OverlappingFileLockException e) {
        //noinspection BusyWait
        Thread.sleep(1000);
      }
    }
    throw new AssertionError("Count not finish waiting for the config/system files to unlock");
  }

  @Test(timeout = 30000)
  public void testDispose() throws Exception {
    SocketLock lock1 = new SocketLock(tempDir.getRoot().getPath() + "/c", tempDir.getRoot().getPath() + "/s");
    SocketLock lock2 = new SocketLock(tempDir.getRoot().getPath() + "/c", tempDir.getRoot().getPath() + "/s");

    assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, tryActivate(lock1));
    assertEquals(SocketLock.ActivateStatus.ACTIVATED, tryActivate(lock2));

    lock1.dispose();
    assertEquals(SocketLock.ActivateStatus.NO_INSTANCE, tryActivate(lock2));
    lock2.dispose();
  }

  @Test(timeout = 30000, expected = IllegalArgumentException.class)
  public void testPathCollision() {
    String path = tempDir.getRoot().getPath() + "/d";
    new SocketLock(path, path).dispose();
  }
}