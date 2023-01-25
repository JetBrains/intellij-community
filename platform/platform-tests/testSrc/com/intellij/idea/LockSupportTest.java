// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.rules.TempDirectory;
import kotlinx.coroutines.GlobalScope;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class LockSupportTest {
  @Rule public final TempDirectory tempDir = new TempDirectory();

  @Test(timeout = 30000)
  public void useCanonicalPathLock() {
    assumeTrue("case-insensitive system-only", !SystemInfo.isFileSystemCaseSensitive);

    var path1 = tempDir.getRootPath().toString();
    var path2 = path1.toUpperCase(Locale.ENGLISH);
    var lock1 = new SocketLock(StartupUtil.canonicalPath(path1 + "/c"), StartupUtil.canonicalPath(path1 + "/s"));
    var lock2 = new SocketLock(StartupUtil.canonicalPath(path2 + "/c"), StartupUtil.canonicalPath(path2 + "/s"));
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
  public void lock() {
    var lock = new SocketLock(tempDir.getRootPath().resolve("c"), tempDir.getRootPath().resolve("s"));
    try {
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, lock.lockAndTryActivate(List.of(), GlobalScope.INSTANCE).getFirst());
    }
    finally {
      lock.dispose();
    }
  }

  @Test(timeout = 30000)
  public void twoLocks() {
    var toClose = new ArrayList<SocketLock>();
    try {
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRootPath(), "c1", "s1"));
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRootPath(), "c2", "s2"));
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRootPath(), "c3", "s3"));
      assertEquals(SocketLock.ActivationStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRootPath(), "c1", "s1"));
      assertEquals(SocketLock.ActivationStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRootPath(), "c2", "s2"));
      assertEquals(SocketLock.ActivationStatus.ACTIVATED, createLockAndTryActivate(toClose, tempDir.getRootPath(), "c3", "s3"));
    }
    finally {
      toClose.forEach(SocketLock::dispose);
    }
  }

  @Test(timeout = 30000)
  public void dispose() {
    var lock1 = new SocketLock(tempDir.getRootPath().resolve("c"), tempDir.getRootPath().resolve("s"));
    var lock2 = new SocketLock(tempDir.getRootPath().resolve("c"), tempDir.getRootPath().resolve("s"));
    assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, tryActivate(lock1));
    assertEquals(SocketLock.ActivationStatus.ACTIVATED, tryActivate(lock2));
    lock1.dispose();
    assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, tryActivate(lock2));
    lock2.dispose();
  }

  @Test(timeout = 30000, expected = IllegalArgumentException.class)
  public void pathCollision() {
    var path = tempDir.getRootPath().resolve("d");
    new SocketLock(path, path);
  }

  private static SocketLock.ActivationStatus createLockAndTryActivate(List<SocketLock> toClose, Path dir, String cfg, String sys) {
    var lock = new SocketLock(dir.resolve(cfg), dir.resolve(sys));
    toClose.add(lock);
    return tryActivate(lock);
  }

  private static SocketLock.ActivationStatus tryActivate(SocketLock lock) {
    var statusAndResult = lock.lockAndTryActivate(List.of(), GlobalScope.INSTANCE);
    lock.getServer();
    return statusAndResult.getFirst();
  }
}
