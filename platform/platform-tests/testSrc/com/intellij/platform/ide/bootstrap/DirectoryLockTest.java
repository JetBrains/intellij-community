// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.ide.CliResult;
import com.intellij.ide.SpecialConfigFiles;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.ide.bootstrap.DirectoryLock.CannotActivateException;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.rules.InMemoryFsExtension;
import com.intellij.util.Suppressions;
import com.intellij.util.system.OS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@SuppressWarnings("UseOptimizedEelFunctions")
public abstract sealed class DirectoryLockTest {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  public static final class StandardModeTest extends DirectoryLockTest {
    @Override
    protected Path getTestDir() throws IOException {
      var testDir = tempDir;
      if (testDir.toString().length() > 90) {
        var dir = OS.CURRENT == OS.Windows ? Path.of(System.getenv("SystemRoot"), "Temp") : Path.of("/tmp");
        testDir = Files.createTempDirectory(dir, testDir.getFileName().toString());
      }
      return testDir;
    }
  }

  public static final class RedirectedModeTest extends DirectoryLockTest {
    @Override
    protected Path getTestDir() throws IOException {
      var testDir = tempDir;
      if (testDir.toString().length() < 100) {
        var padding = "_path_length_padding_" + "x".repeat(100 - testDir.toString().length());
        testDir = Files.createDirectories(testDir.resolve(padding));
      }
      return testDir;
    }
  }

  public static final class FallbackModeTest extends DirectoryLockTest {
    @RegisterExtension public final InMemoryFsExtension memoryFs = new InMemoryFsExtension(OS.CURRENT);

    @Override
    protected Path getTestDir() {
      var path = OS.CURRENT == OS.Windows ? "C:\\tests\\" + tempDir.getFileName() : tempDir.toString();
      return memoryFs.getFs().getPath(path);
    }
  }

  @TempDir public Path tempDir;

  private Path testDir;
  private final List<DirectoryLock> activeLocks = new ArrayList<>();
  private final Path currentDir = Path.of("");

  @BeforeEach
  public void setUp() throws Exception {
    testDir = getTestDir();
  }

  protected abstract Path getTestDir() throws IOException;

  @AfterEach
  public void tearDown() throws Exception {
    Suppressions.runSuppressing(
      () -> activeLocks.forEach(DirectoryLock::dispose),
      () -> activeLocks.clear(),
      () -> {
        if (testDir != tempDir) {
          NioFiles.deleteRecursively(testDir);
        }
      });
  }

  private DirectoryLock createLock(Path configPath, Path systemPath) {
    var lock = new DirectoryLock(configPath, systemPath, _ -> CliResult.OK);
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
  public void socketFileIsOwnerOnly() throws Exception {
    assumeTrue(!(this instanceof FallbackModeTest));
    assumeTrue(Files.getFileAttributeView(testDir, PosixFileAttributeView.class) != null);

    var systemDir = Files.createDirectories(testDir.resolve("s"));
    var lock = createLock(testDir.resolve("c"), systemDir);
    assertNull(lock.lockOrActivate(currentDir, List.of()));

    var redirectedPortFile = lock.getRedirectedPortFile();
    var portFile = redirectedPortFile != null ? redirectedPortFile : systemDir.resolve(SpecialConfigFiles.PORT_FILE);
    assertThat(Files.getPosixFilePermissions(portFile))
      .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  }

  @Test
  public void lockingVacantDirectories() throws Exception {
    var lock = createLock(Files.createDirectories(testDir.resolve("c")), Files.createDirectories(testDir.resolve("s")));
    assertNull(lock.lockOrActivate(currentDir, List.of()));
  }

  @Test
  public void lockingReadOnlyDirectory() throws Exception {
    assumeTrue(OS.CURRENT != OS.Windows);
    var configDir = Files.createDirectories(testDir.resolve("c"));
    NioFiles.setReadOnly(configDir, true);
    var lock = createLock(configDir, Files.createDirectories(testDir.resolve("s")));
    assertThatThrownBy(() -> lock.lockOrActivate(currentDir, List.of())).isInstanceOf(IOException.class);
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
    assumeTrue(Files.isSameFile(configDir1, configDir2), "case-insensitive system-only");

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
  public void deletingRealStalePortFile() throws Exception {
    assumeTrue(this instanceof StandardModeTest);
    var systemDir = Files.createDirectories(testDir.resolve("s"));
    var lock = createLock(testDir.resolve("c"), systemDir);
    var portFile = systemDir.resolve(SpecialConfigFiles.PORT_FILE);
    var process = DummyProcess.start(portFile.toString());
    while (process.isAlive()) {
      if ("accepting connections".equals(process.inputReader().readLine())) break;
    }
    assumeTrue(process.isAlive());
    process.destroyForcibly();
    assertThat(portFile).exists();
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
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    var command = ProcessHandle.current().info().command().get();
    var nonIdeProcess = ProcessHandle.allProcesses()
      .filter(h -> { var other = h.info().command().orElse(null); return other != null && !other.endsWith(command); })
      .findFirst().orElse(null);
    assumeTrue(nonIdeProcess != null, "Cannot find a non-IDE process among running");
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
  public void activatingWithDelayedExitingProcess() throws Exception {
    var configDir = Files.createDirectories(testDir.resolve("c"));
    var systemDir = Files.createDirectories(testDir.resolve("s"));
    var process = DummyProcess.start(500);
    try {
      Files.writeString(configDir.resolve(SpecialConfigFiles.LOCK_FILE), Long.toString(process.pid()));
      var lock = createLock(configDir, systemDir);
      assertNull(lock.lockOrActivate(currentDir, List.of()));
    }
    finally {
      process.destroyForcibly();
    }
  }

  @Test
  public void activatingWithHungProcessTimesOut() throws Exception {
    var configDir = Files.createDirectories(testDir.resolve("c"));
    var systemDir = Files.createDirectories(testDir.resolve("s"));
    var process = DummyProcess.start(5000);
    try {
      Files.writeString(configDir.resolve(SpecialConfigFiles.LOCK_FILE), Long.toString(process.pid()));
      var lock = createLock(configDir, systemDir);
      assertThatThrownBy(() -> lock.lockOrActivate(currentDir, List.of()))
        .isInstanceOf(CannotActivateException.class);
    }
    finally {
      process.destroyForcibly();
    }
  }

  static final class DummyProcess {
    private static Process start(int timeout) throws IOException {
      return start(String.valueOf(timeout));
    }

    private static Process start(String arg) throws IOException {
      var javaExe = ProcessHandle.current().info().command().orElseThrow();
      var classpath = PathManager.getJarForClass(DummyProcess.class).toString();
      return new ProcessBuilder(javaExe, "-cp", classpath, DummyProcess.class.getName(), arg).start();
    }

    static void main(String[] args) throws Exception {
      var arg = args[0];
      if (Character.isDigit(arg.charAt(0))) {
        Thread.sleep(Long.parseLong(arg));
      }
      else {
        var address = UnixDomainSocketAddress.of(Path.of(arg));
        var serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverChannel.bind(address);
        System.out.println("accepting connections");
        serverChannel.accept();
      }
    }
  }
}
