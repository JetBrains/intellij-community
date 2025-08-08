// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.rules.InMemoryFsRule;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.system.OS;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NioFilesTest {
  @Rule public TempDirectory tempDir = new TempDirectory();  // for platform-specific tests
  @Rule public InMemoryFsRule memoryFs = new InMemoryFsRule();

  @Test
  public void fileName() {
    assertThat(NioFiles.getFileName(memoryFs.getFs().getPath("/f"))).isEqualTo("f");
    assertThat(NioFiles.getFileName(memoryFs.getFs().getRootDirectories().iterator().next())).isEqualTo("/");
  }

  @Test
  public void copyFilesRecursively() throws IOException {
    var file = Files.writeString(memoryFs.getFs().getPath("/file"), "...");

    var copy = file.resolveSibling("copy");
    NioFiles.copyRecursively(file, copy);
    assertThat(copy).isRegularFile().hasSameBinaryContentAs(file);

    assertThrows(FileAlreadyExistsException.class, () -> NioFiles.copyRecursively(file, copy));

    var link = Files.createSymbolicLink(file.resolveSibling("link"), copy.getFileName());
    assertThrows(FileAlreadyExistsException.class, () -> NioFiles.copyRecursively(file, link));
  }

  @Test
  public void copyDirectoriesRecursively() throws IOException {
    var dir = Files.createDirectory(memoryFs.getFs().getPath("/dir"));
    var f1 = Files.createFile(dir.resolve("file1"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    var f2 = Files.writeString(Files.createDirectories(dir.resolve("d1/d2/d3")).resolve("file2"), "...");
    var l1 = Files.createSymbolicLink(dir.resolve("link1"), memoryFs.getFs().getPath("d1/d2/d3"));
    var l2 = Files.createSymbolicLink(dir.resolve("bad-link"), memoryFs.getFs().getPath("no-such-file"));

    var copy = dir.resolveSibling("copy");
    NioFiles.copyRecursively(dir, copy);

    assertThat(copy).isDirectory();
    assertThat(copy.resolve(dir.relativize(f1))).isRegularFile();
    assertThat(Files.getPosixFilePermissions(copy.resolve(dir.relativize(f1)))).isEqualTo(Files.getPosixFilePermissions(f1));
    assertThat(copy.resolve(dir.relativize(f2))).isRegularFile().hasSameBinaryContentAs(f2);
    assertThat(copy.resolve(dir.relativize(l1))).isSymbolicLink().isDirectoryContaining(p -> p.getFileName().equals(f2.getFileName()));
    assertThat(copy.resolve(dir.relativize(l2))).isSymbolicLink();

    var existingTarget = Files.createDirectories(dir.resolveSibling("existing"));
    NioFiles.copyRecursively(dir, existingTarget);
    assertThat(existingTarget.resolve(dir.relativize(f2))).isRegularFile().hasSameBinaryContentAs(f2);

    var nonConflictingTarget = Files.createDirectories(dir.resolveSibling("non-conflicting"));
    NioFiles.copyRecursively(dir, nonConflictingTarget);
    assertThat(nonConflictingTarget.resolve(dir.relativize(f2))).isRegularFile().hasSameBinaryContentAs(f2);

    var conflictingTarget = Files.createDirectories(dir.resolveSibling("conflicting"));
    Files.createFile(conflictingTarget.resolve("file1"));
    assertThrows(FileAlreadyExistsException.class, () -> NioFiles.copyRecursively(dir, conflictingTarget));
  }

  @Test
  public void deleteRecursively() throws IOException {
    var dir = Files.createDirectory(memoryFs.getFs().getPath("/dir"));
    Files.createFile(dir.resolve("file1"));
    Files.createFile(dir.resolve("file2"));
    assertThat(dir).isDirectory();
    NioFiles.deleteRecursively(dir);
    assertThat(dir).doesNotExist();

    var file = Files.createFile(memoryFs.getFs().getPath("/file"));
    assertThat(file).isRegularFile();
    NioFiles.deleteRecursively(file);
    assertThat(file).doesNotExist();

    NioFiles.deleteRecursively(memoryFs.getFs().getPath("/no_such_file"));
  }

  @Test
  public void deleteLinksRecursively() throws IOException {
    var dir = Files.createDirectory(tempDir.getRootPath().resolve("dir"));
    var file = Files.createFile(dir.resolve("file"));
    var subDir = Files.createDirectory(dir.resolve("subDir"));
    Files.createFile(subDir.resolve("subFile"));
    var link = Files.createSymbolicLink(tempDir.getRootPath().resolve("link"), dir);
    NioFiles.deleteRecursively(link.resolve(subDir.getFileName()));
    assertThat(subDir).doesNotExist();
    NioFiles.deleteRecursively(link.resolve(file.getFileName()));
    assertThat(file).doesNotExist();
    NioFiles.deleteRecursively(link);
    assertThat(link).doesNotExist();
    assertThat(dir).isDirectory();

    var anotherFile = Files.createFile(tempDir.getRootPath().resolve("file"));
    var fileLink = Files.createSymbolicLink(tempDir.getRootPath().resolve("link"), anotherFile);
    NioFiles.deleteRecursively(fileLink.resolve("no_such_file"));
    assertThat(fileLink).isSymbolicLink();
    NioFiles.deleteRecursively(fileLink);
    assertThat(fileLink).doesNotExist();

    var nonExistingLink = Files.createSymbolicLink(tempDir.getRootPath().resolve("link"), tempDir.getRootPath().resolve("no_such_file"));
    NioFiles.deleteRecursively(nonExistingLink.resolve("no_such_file"));
    assertThat(nonExistingLink).isSymbolicLink();
    NioFiles.deleteRecursively(nonExistingLink);
    assertThat(nonExistingLink).doesNotExist();
  }

  @Test
  public void deleteCallbackInvocation() throws IOException {
    var file = Files.createFile(memoryFs.getFs().getPath("/file"));

    var dir = Files.createDirectory(memoryFs.getFs().getPath("/d1"));
    Files.createFile(Files.createDirectory(dir.resolve("d2")).resolve("f"));

    var visited = new ArrayList<String>();
    NioFiles.deleteRecursively(file, p -> visited.add(p.toString()));
    NioFiles.deleteRecursively(dir, p -> visited.add(p.toString()));
    assertThat(visited).containsExactly("/file", "/d1/d2/f", "/d1/d2", "/d1");
  }

  @Test
  public void deleteQuietly() throws IOException {
    var exception = Ref.<IOException>create();
    var handler = (Consumer<IOException>)(e -> exception.set(e));

    NioFiles.deleteQuietly(null, handler);
    assertThat(exception.get()).isNull();

    var file = memoryFs.getFs().getPath("/missing/file");
    NioFiles.deleteQuietly(file, handler);
    assertThat(exception.get()).isNull();

    file = Files.createFile(memoryFs.getFs().getPath("/file"));
    NioFiles.deleteQuietly(file, handler);
    assertThat(exception.get()).isNull();

    file = Files.createDirectory(memoryFs.getFs().getPath("/empty"));
    NioFiles.deleteQuietly(file, handler);
    assertThat(exception.get()).isNull();

    file = Files.createDirectories(memoryFs.getFs().getPath("/dir/sub")).getParent();
    NioFiles.deleteQuietly(file, handler);
    assertThat(exception.get()).isInstanceOf(DirectoryNotEmptyException.class);
  }

  @Test
  public void createDirectories() throws IOException {
    var existingDir = Files.createDirectory(memoryFs.getFs().getPath("/existing"));
    NioFiles.createDirectories(existingDir);

    var nonExisting = memoryFs.getFs().getPath("/d1/d2/d3/non-existing");
    NioFiles.createDirectories(nonExisting);
    assertThat(nonExisting).isDirectory();

    var existingFile = Files.createFile(memoryFs.getFs().getPath("/file"));
    assertThatThrownBy(() -> NioFiles.createDirectories(existingFile))
      .isInstanceOf(FileAlreadyExistsException.class);
    assertThatThrownBy(() -> NioFiles.createDirectories(existingFile.resolve("dir")))
      .isInstanceOf(FileAlreadyExistsException.class);

    var endLink = Files.createSymbolicLink(memoryFs.getFs().getPath("/end-link"), existingDir);
    NioFiles.createDirectories(endLink);
    assertThat(endLink).isDirectory().isSymbolicLink();

    var middleLinkDir = endLink.resolve("d1/d2");
    NioFiles.createDirectories(middleLinkDir);
    assertThat(middleLinkDir).isDirectory();

    var badLink = Files.createSymbolicLink(memoryFs.getFs().getPath("/bad-link"), memoryFs.getFs().getPath("bad-target"));
    assertThatThrownBy(() -> NioFiles.createDirectories(badLink))
      .isInstanceOf(FileAlreadyExistsException.class);
  }

  @Test
  public void createParentDirectories() throws IOException {
    var nonExisting = memoryFs.getFs().getPath("/d1/d2/d3/non-existing");
    assertThatThrownBy(() -> Files.writeString(nonExisting, "..."))
      .isInstanceOf(NoSuchFileException.class);
    Files.writeString(NioFiles.createParentDirectories(nonExisting), "...");
    assertThat(nonExisting).isRegularFile();
  }

  @Test
  public void createIfNotExists() throws IOException {
    var existingFile = Files.createFile(memoryFs.getFs().getPath("/existing"));
    NioFiles.createIfNotExists(existingFile);
    assertThat(existingFile).isRegularFile();

    var nonExisting = memoryFs.getFs().getPath("/d1/d2/d3/non-existing");
    NioFiles.createIfNotExists(nonExisting);
    assertThat(nonExisting).isRegularFile();

    var existingDir = Files.createDirectories(memoryFs.getFs().getPath("/dir"));
    assertThatThrownBy(() -> NioFiles.createIfNotExists(existingDir))
      .isInstanceOf(FileAlreadyExistsException.class);

    var endLink = Files.createSymbolicLink(memoryFs.getFs().getPath("/end-link"), existingFile);
    NioFiles.createIfNotExists(endLink);
    assertThat(endLink).isRegularFile().isSymbolicLink();
  }

  @Test
  public void caseOnlyRename() throws IOException {
    var file = tempDir.newFileNio("dir/test.txt");
    NioFiles.rename(file, "FILE.txt");
    assertThat(NioFiles.list(file.getParent()).stream().map(p -> p.getFileName().toString())).containsExactly("FILE.txt");
  }

  @Test
  public void setReadOnly() throws IOException {
    var f = tempDir.newFileNio("f");

    NioFiles.setReadOnly(f, true);
    assertThatThrownBy(() -> Files.writeString(f, "test"))
      .isInstanceOf(AccessDeniedException.class);

    NioFiles.setReadOnly(f, false);
    Files.writeString(f, "test");

    var d = tempDir.newDirectoryPath("d");
    var child = d.resolve("f");

    NioFiles.setReadOnly(d, true);
    if (OS.CURRENT != OS.Windows) {
      assertThatThrownBy(() -> Files.createFile(child))
        .isInstanceOf(AccessDeniedException.class);
    }

    NioFiles.setReadOnly(d, false);
    Files.createFile(child);
  }

  @Test
  public void setExecutable() throws IOException, InterruptedException {
    assumeUnix();

    var script = Files.writeString(tempDir.newFileNio("test.sh"), "#!/bin/sh\nexit 42\n");
    try { runAndGetExitValue(script.toString()); }
    catch (IOException ignored) { }

    NioFiles.setExecutable(script);
    assertEquals(42, runAndGetExitValue(script.toString()));
  }

  private static int runAndGetExitValue(String command) throws IOException, InterruptedException {
    var process = Runtime.getRuntime().exec(command);
    if (process.waitFor(30, TimeUnit.SECONDS)) {
      return process.exitValue();
    }
    else {
      process.destroy();
      throw new AssertionError("Timed out and killed: " + command);
    }
  }

  @Test
  public void list() throws IOException {
    var dir = Files.createDirectory(memoryFs.getFs().getPath("/dir"));
    var f1 = Files.createFile(dir.resolve("f1"));
    var f2 = Files.createFile(dir.resolve("f2"));
    assertThat(NioFiles.list(dir)).containsExactlyInAnyOrder(f1, f2);
    assertThat(NioFiles.list(f1)).isEmpty();
    assertThat(NioFiles.list(dir.resolve("missing_file"))).isEmpty();
  }

  @Test
  public void circularSymlinkAttributesReading() throws IOException {
    assumeSymLinkCreationIsSupported();
    var symlink = tempDir.getRootPath().resolve("symlink");
    Files.createSymbolicLink(symlink, symlink);
    assertTrue(NioFiles.readAttributes(symlink).isSymbolicLink());
  }

  @Test
  public void wslSymlinkAttributesReading() throws IOException {
    var distribution = assumeWorkingWslDistribution();
    var tempDirPrefix = UsefulTestCase.TEMP_DIR_MARKER + "wslSymlinkAttributesReading" + "_";
    var tempDir = Files.createTempDirectory(Path.of("\\\\wsl$\\" + distribution + "\\tmp"), tempDirPrefix);
    var tmpPath = "/tmp/" + tempDir.getFileName() + '/';
    try {
      var fileLink = tempDir.resolve("fileLink");
      var dirLink = tempDir.resolve("dirLink");
      PlatformTestUtil.assertSuccessful(new GeneralCommandLine("wsl", "-d", distribution, "-e", "ln", "-s", "file", tmpPath + fileLink.getFileName()));
      PlatformTestUtil.assertSuccessful(new GeneralCommandLine("wsl", "-d", distribution, "-e", "ln", "-s", "dir", tmpPath + dirLink.getFileName()));
      Files.writeString(tempDir.resolve("file"), "...");
      Files.createDirectories(tempDir.resolve("dir"));

      assertSame(NioFiles.BROKEN_SYMLINK, NioFiles.readAttributes(fileLink));
      assertSame(NioFiles.BROKEN_SYMLINK, NioFiles.readAttributes(dirLink));
    }
    finally {
      PlatformTestUtil.assertSuccessful(new GeneralCommandLine("wsl", "-d", distribution, "-e", "rm", "-rf", tmpPath));
    }
  }

  @Test
  public void sizeIfExists() throws IOException {
    var data = new byte[]{1, 2, 3};
    var existing = Files.write(memoryFs.getFs().getPath("/existing.file"), data);
    assertThat(NioFiles.sizeIfExists(existing)).isEqualTo(data.length);

    var nonExisting = memoryFs.getFs().getPath("/non-existing");
    assertThat(NioFiles.sizeIfExists(nonExisting)).isEqualTo(-1);
  }
}
