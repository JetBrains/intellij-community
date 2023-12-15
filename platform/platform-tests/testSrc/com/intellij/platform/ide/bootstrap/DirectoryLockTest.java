// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.ide.CliResult;
import com.intellij.ide.SpecialConfigFiles;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.ide.bootstrap.DirectoryLock.CannotActivateException;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.rules.InMemoryFsRule;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.Suppressions;
import com.intellij.util.TimeoutUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

public abstract sealed class DirectoryLockTest {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  public static final class StandardModeTest extends DirectoryLockTest {
    @Override
    protected Path getTestDir() throws IOException {
      var testDir = tempDir.getRootPath();
      if (testDir.toString().length() > 90) {
        var dir = SystemInfo.isWindows ? Path.of(System.getenv("SystemRoot"), "Temp") : Path.of("/tmp");
        testDir = Files.createTempDirectory(dir, testDir.getFileName().toString());
      }
      return testDir;
    }
  }

  public static final class RedirectedModeTest extends DirectoryLockTest {
    @Override
    protected Path getTestDir() throws IOException {
      var testDir = tempDir.getRootPath();
      if (testDir.toString().length() < 100) {
        var padding = "_path_length_padding_" + "x".repeat(100 - testDir.toString().length());
        testDir = Files.createDirectories(testDir.resolve(padding));
      }
      return testDir;
    }
  }

  public static final class FallbackModeTest extends DirectoryLockTest {
    @Override
    protected Path getTestDir() {
      var path = SystemInfo.isWindows ? "C:\\tests\\" + tempDir.getRootPath().getFileName() : tempDir.getRootPath().toString();
      return memoryFs.getFs().getPath(path);
    }
  }

  @Rule public final TestRule watcher = TestLoggerFactory.createTestWatcher();
  @Rule public final Timeout timeout = Timeout.seconds(30);
  @Rule public final TempDirectory tempDir = new TempDirectory();
  @Rule public final InMemoryFsRule memoryFs = new InMemoryFsRule(SystemInfo.isWindows);

  private Path testDir;
  private final List<DirectoryLock> activeLocks = new ArrayList<>();
  private final Path currentDir = Path.of("");

  @Before
  public void setUp() throws Exception {
    testDir = getTestDir();
  }

  protected abstract Path getTestDir() throws IOException;

  @After
  public void tearDown() throws Exception {
    Suppressions.runSuppressing(
      () -> activeLocks.forEach(DirectoryLock::dispose),
      () -> activeLocks.clear(),
      () -> {
        if (testDir != tempDir.getRootPath()) {
          NioFiles.deleteRecursively(testDir);
        }
      });
  }

  private DirectoryLock createLock(Path configPath, Path systemPath) {
    var lock = new DirectoryLock(configPath, systemPath, args -> CliResult.OK);
    activeLocks.add(lock);
    return lock;
  }

  @Test
  public void pathCollision() {
    var path = testDir.resolve("same");
    var lock = createLock(path, path);
    assertThatThrownBy(() -> lock.lockOrActivate(currentDir, List.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void lockingNonExistingDirectories() throws Exception {
    var lock = createLock(testDir.resolve("c"), testDir.resolve("s"));
    assertNull(lock.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void lockingVacantDirectories() throws Exception {
    var lock = createLock(Files.createDirectories(testDir.resolve("c")), Files.createDirectories(testDir.resolve("s")));
    assertNull(lock.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void lockIndependence() throws Exception {
    var lock1 = createLock(testDir.resolve("c1"), testDir.resolve("s1"));
    assertNull(lock1.lockOrActivate(currentDir, List.of()));
    var lock2 = createLock(testDir.resolve("c2"), testDir.resolve("s2"));
    assertNull(lock2.lockOrActivate(currentDir, List.of()));
    var lock1copy = createLock(testDir.resolve("c1"), testDir.resolve("s1"));
    assertNotNull(lock1copy.lockOrActivate(currentDir, List.of()));
    var lock2copy = createLock(testDir.resolve("c2"), testDir.resolve("s2"));
    assertNotNull(lock2copy.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void releasingDirectories() throws Exception {
    var configDir = testDir.resolve("c");
    var systemDir = testDir.resolve("s");
    var lock1 = createLock(configDir, systemDir);
    var lock2 = createLock(configDir, systemDir);
    assertNull(lock1.lockOrActivate(currentDir, List.of()));
    assertNotNull(lock2.lockOrActivate(currentDir, List.of()));
    lock1.dispose();
    assertNull(lock2.lockOrActivate(currentDir, List.of()));
    lock2.dispose();
    assertThat(configDir).isEmptyDirectory();
    assertThat(systemDir).isEmptyDirectory();
  }

  @Test
  public void activatingViaCaseMismatchingPath() throws Exception {
    var configDir1 = Files.createDirectories(testDir.resolve("c"));
    var configDir2 = Files.createDirectories(testDir.resolve("C"));
    assumeTrue("case-insensitive system-only", Files.isSameFile(configDir1, configDir2));

    var lock1 = createLock(configDir1, testDir.resolve("s"));
    var lock2 = createLock(configDir2, testDir.resolve("S"));
    assertNull(lock1.lockOrActivate(currentDir, List.of()));
    assertNotNull(lock2.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void activatingViaSymlinkedPath() throws Exception {
    var dir = Files.createDirectories(testDir.resolve("dir"));
    var link = Files.createSymbolicLink(testDir.resolve("link"), dir);
    var lock1 = createLock(dir.resolve("c"), dir.resolve("s"));
    var lock2 = createLock(link.resolve("c"), link.resolve("s"));
    assertNull(lock1.lockOrActivate(currentDir, List.of()));
    assertNotNull(lock2.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void symlinkPathCollision() throws Exception {
    var dir = Files.createDirectories(testDir.resolve("dir"));
    var configLink = Files.createSymbolicLink(testDir.resolve("c"), dir);
    var systemLink = Files.createSymbolicLink(testDir.resolve("s"), dir);
    var lock = createLock(configLink, systemLink);
    assertThatThrownBy(() -> lock.lockOrActivate(currentDir, List.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void configDirectoryCannotBeShared() throws Exception {
    var configDir = Files.createDirectories(testDir.resolve("c"));
    var systemDir1 = Files.createDirectories(testDir.resolve("s1"));
    var systemDir2 = Files.createDirectories(testDir.resolve("s2"));
    var lock1 = createLock(configDir, systemDir1);
    var lock2 = createLock(configDir, systemDir2);
    assertNull(lock1.lockOrActivate(currentDir, List.of()));
    assertThatThrownBy(() -> lock2.lockOrActivate(currentDir, List.of())).isInstanceOf(CannotActivateException.class);
    assertThatThrownBy(() -> lock2.lockOrActivate(currentDir, List.of())).isInstanceOf(CannotActivateException.class);
  }

  @Test
  public void deletingStalePortFile() throws Exception {
    var systemDir = Files.createDirectories(testDir.resolve("s"));
    var lock = createLock(testDir.resolve("c"), systemDir);
    Files.createFile(systemDir.resolve(SpecialConfigFiles.PORT_FILE));
    if (lock.getRedirectedPortFile() != null) {
      Files.createFile(lock.getRedirectedPortFile());
    }
    assertNull(lock.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void deletingStaleLockFile() throws Exception {
    var configDir = Files.createDirectories(testDir.resolve("c"));
    Files.writeString(configDir.resolve(SpecialConfigFiles.LOCK_FILE), "---");
    var lock = createLock(configDir, testDir.resolve("s"));
    assertNull(lock.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void deletingStaleLockFileWithRecycledPid() throws Exception {
    var scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    var nonIdeProcess = ProcessHandle.allProcesses()
      .filter(h -> { var command = h.info().command().orElse(""); return !(command.contains("java") || command.contains(scriptName)); })
      .findFirst().orElse(null);
    assumeTrue("Cannot find a non-IDE process among running", nonIdeProcess != null);
    var configDir = Files.createDirectories(testDir.resolve("c"));
    Files.writeString(configDir.resolve(SpecialConfigFiles.LOCK_FILE), Long.toString(nonIdeProcess.pid()));
    var lock = createLock(configDir, testDir.resolve("s"));
    assertNull(lock.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void preservingActiveLockFile() throws Exception {
    var configDir = Files.createDirectories(testDir.resolve("c"));
    Files.writeString(configDir.resolve(SpecialConfigFiles.LOCK_FILE), Long.toString(ProcessHandle.current().pid()));
    var lock = createLock(configDir, testDir.resolve("s"));
    assertThatThrownBy(() -> lock.lockOrActivate(currentDir, List.of())).isInstanceOf(CannotActivateException.class);
  }

  @Test
  public void responseTimeout() throws Exception {
    var timeoutMs = 300;
    var lock = new DirectoryLock(testDir.resolve("c"), testDir.resolve("s"), args -> { TimeoutUtil.sleep(10_000L); return CliResult.OK; })
      .withConnectTimeout(timeoutMs);
    activeLocks.add(lock);
    assertNull(lock.lockOrActivate(currentDir, List.of()));
    var t = System.nanoTime();
    assertThatThrownBy(() -> lock.lockOrActivate(currentDir, List.of())).isInstanceOf(CannotActivateException.class);
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)).isGreaterThanOrEqualTo(timeoutMs);
  }
}
