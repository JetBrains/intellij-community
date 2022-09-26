// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.ide.CliResult;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.rules.TempDirectory;
import kotlin.Pair;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class LockSupportTest {
  @Rule public final TempDirectory tempDir = new TempDirectory();

  @Test(timeout = 30000)
  public void testUseCanonicalPathLock() {
    assumeTrue("case-insensitive system-only", !SystemInfo.isFileSystemCaseSensitive);

    String path1 = tempDir.getRoot().getPath();
    String path2 = path1.toUpperCase(Locale.ENGLISH);

    SocketLock lock1 = new SocketLock(StartupUtil.canonicalPath(path1 + "/c"), StartupUtil.canonicalPath(path1 + "/s"));
    SocketLock lock2 = new SocketLock(StartupUtil.canonicalPath(path2 + "/c"), StartupUtil.canonicalPath(path2 + "/s"));
    try {
      assertThat(tryActivate(lock1)).isEqualTo(SocketLock.ActivationStatus.NO_INSTANCE);
      assertThat(tryActivate(lock2)).isEqualTo(SocketLock.ActivationStatus.ACTIVATED);
    }
    finally {
      lock1.dispose();
      lock2.dispose();
    }
  }

  @Test(timeout = 30000)
  public void testLock() {
    SocketLock lock = new SocketLock(tempDir.getRoot().toPath().resolve("c"), tempDir.getRoot().toPath().resolve("s"));
    try {
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, lock.lockAndTryActivate(Collections.emptyList(), GlobalScope.INSTANCE).getFirst());
    }
    finally {
      lock.dispose();
    }
  }

  @Test(timeout = 30000)
  public void testTwoLocks() {
    List<SocketLock> toClose = new ArrayList<>();
    try {
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot().toPath(), "c1", "s1"));
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot().toPath(), "c2", "s2"));
      assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, createLockAndTryActivate(toClose, tempDir.getRoot().toPath(), "c3", "s3"));

      assertThat(createLockAndTryActivate(toClose, tempDir.getRoot().toPath(), "c1", "s1")).isEqualTo(SocketLock.ActivationStatus.ACTIVATED);
      assertThat(createLockAndTryActivate(toClose, tempDir.getRoot().toPath(), "c2", "s2")).isEqualTo(SocketLock.ActivationStatus.ACTIVATED);
      assertThat(createLockAndTryActivate(toClose, tempDir.getRoot().toPath(), "c3", "s3")).isEqualTo(SocketLock.ActivationStatus.ACTIVATED);
    }
    finally {
      toClose.forEach(SocketLock::dispose);
    }
  }

  private static SocketLock.ActivationStatus createLockAndTryActivate(List<SocketLock> toClose, @NotNull Path dir, String cfg, String sys) {
    SocketLock lock = new SocketLock(dir.resolve(cfg), dir.resolve(sys));
    toClose.add(lock);
    return tryActivate(lock);
  }

  private static SocketLock.ActivationStatus tryActivate(@NotNull SocketLock lock) {
    Pair<SocketLock.ActivationStatus, CliResult> result = lock.lockAndTryActivate(Collections.emptyList(), GlobalScope.INSTANCE);
    lock.getServer();
    return result.getFirst();
  }

  @Test(timeout = 30000)
  public void testDispose()  {
    SocketLock lock1 = new SocketLock(tempDir.getRoot().toPath().resolve("c"), tempDir.getRoot().toPath().resolve("s"));
    SocketLock lock2 = new SocketLock(tempDir.getRoot().toPath().resolve("c"), tempDir.getRoot().toPath().resolve("s"));

    assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, tryActivate(lock1));
    assertEquals(SocketLock.ActivationStatus.ACTIVATED, tryActivate(lock2));

    lock1.dispose();
    assertEquals(SocketLock.ActivationStatus.NO_INSTANCE, tryActivate(lock2));
    lock2.dispose();
  }

  @Test(timeout = 30000, expected = IllegalArgumentException.class)
  public void testPathCollision() {
    Path path = tempDir.getRoot().toPath().resolve("d");
    new SocketLock(path, path);
  }
}