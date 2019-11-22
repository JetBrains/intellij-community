// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.CliResult;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ArrayUtil;
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
    assumeTrue("case-insensitive system-only", !SystemInfo.isFileSystemCaseSensitive);

    String path1 = tempDir.getRoot().getPath();
    String path2 = path1.toUpperCase(Locale.ENGLISH);

    SocketLock lock1 = new SocketLock(path1 + "/c", path1 + "/s");
    SocketLock lock2 = new SocketLock(path2 + "/c", path2 + "/s");
    try {
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, tryActivate(lock1));
      assertEquals(SocketLock.ActivationStatus.ACTIVATED, tryActivate(lock2));
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
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, lock.lockAndTryActivate(ArrayUtil.EMPTY_STRING_ARRAY).first);
    }
    finally {
      lock.dispose();
    }
  }

  @Test(timeout = 30000)
  public void testTwoLocks() throws Exception {
    List<SocketLock> toClose = new ArrayList<>();
    try {
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot(), "c1", "s1"));
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot(), "c2", "s2"));
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot(), "c3", "s3"));

      assertEquals(SocketLock.ActivationStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRoot(), "c1", "s1"));
      assertEquals(SocketLock.ActivationStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRoot(), "c2", "s2"));
      assertEquals(SocketLock.ActivationStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRoot(), "c3", "s3"));
    }
    finally {
      toClose.forEach(SocketLock::dispose);
    }
  }

  private static SocketLock.ActivationStatus createLockAndTryActivate(List<SocketLock> toClose, File dir, String cfg, String sys) throws Exception {
    SocketLock lock = new SocketLock(dir.getPath() + '/' + cfg, dir.getPath() + '/' + sys);
    toClose.add(lock);
    return tryActivate(lock);
  }

  private static SocketLock.ActivationStatus tryActivate(SocketLock lock) throws Exception {
    Pair<SocketLock.ActivationStatus, CliResult> result = lock.lockAndTryActivate(ArrayUtil.EMPTY_STRING_ARRAY);
    lock.getServer();
    return result.first;
  }

  @Test(timeout = 30000)
  public void testDispose() throws Exception {
    SocketLock lock1 = new SocketLock(tempDir.getRoot().getPath() + "/c", tempDir.getRoot().getPath() + "/s");
    SocketLock lock2 = new SocketLock(tempDir.getRoot().getPath() + "/c", tempDir.getRoot().getPath() + "/s");

    assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, tryActivate(lock1));
    assertEquals(SocketLock.ActivationStatus.ACTIVATED, tryActivate(lock2));

    lock1.dispose();
    assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, tryActivate(lock2));
    lock2.dispose();
  }

  @Test(timeout = 30000, expected = IllegalArgumentException.class)
  public void testPathCollision() {
    String path = tempDir.getRoot().getPath() + "/d";
    new SocketLock(path, path);
  }
}