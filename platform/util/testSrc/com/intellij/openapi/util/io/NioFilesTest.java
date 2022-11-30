// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.rules.InMemoryFsRule;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class NioFilesTest {
  @Rule public TempDirectory tempDir = new TempDirectory();
  @Rule public InMemoryFsRule memoryFs = new InMemoryFsRule();

  @Test
  public void fileName() {
    assertThat(NioFiles.getFileName(memoryFs.getFs().getPath("/f"))).isEqualTo("f");
    assertThat(NioFiles.getFileName(memoryFs.getFs().getRootDirectories().iterator().next())).isEqualTo("/");
  }

  @Test
  public void deleteRecursively() throws IOException {
    var dir = Files.createDirectory(tempDir.getRootPath().resolve("dir"));
    Files.createFile(dir.resolve("file1"));
    Files.createFile(dir.resolve("file2"));
    NioFiles.deleteRecursively(dir.resolve("no_such_file"));
    assertThat(dir).isDirectory();
    NioFiles.deleteRecursively(dir);
    assertThat(dir).doesNotExist();

    var file = Files.createFile(tempDir.getRootPath().resolve("file"));
    NioFiles.deleteRecursively(file.resolve("no_such_file"));
    assertThat(file).isRegularFile();
    NioFiles.deleteRecursively(file);
    assertThat(file).doesNotExist();
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
    Path file = Files.createFile(memoryFs.getFs().getPath("/file"));

    Path dir = Files.createDirectory(memoryFs.getFs().getPath("/d1"));
    Files.createFile(
      Files.createDirectory(dir.resolve("d2"))
        .resolve("f"));

    List<String> visited = new ArrayList<>();
    NioFiles.deleteRecursively(file, p -> visited.add(p.toString()));
    NioFiles.deleteRecursively(dir, p -> visited.add(p.toString()));
    assertThat(visited).containsExactly("/file", "/d1/d2/f", "/d1/d2", "/d1");
  }

  @Test
  public void createDirectories() throws IOException {
    Path existingDir = Files.createDirectory(memoryFs.getFs().getPath("/existing"));
    NioFiles.createDirectories(existingDir);

    Path nonExisting = memoryFs.getFs().getPath("/d1/d2/d3/non-existing");
    NioFiles.createDirectories(nonExisting);
    assertThat(nonExisting).isDirectory();

    Path existingFile = Files.createFile(memoryFs.getFs().getPath("/file"));
    try {
      NioFiles.createDirectories(existingFile);
      fail("`createDirectories()` over an existing file shall not pass");
    }
    catch (FileAlreadyExistsException ignored) { }
    try {
      NioFiles.createDirectories(existingFile.resolve("dir"));
      fail("`createDirectories()` over an existing file shall not pass");
    }
    catch (FileAlreadyExistsException ignored) { }

    Path endLink = memoryFs.getFs().getPath("/end-link");
    Files.createSymbolicLink(endLink, existingDir);
    NioFiles.createDirectories(endLink);
    assertThat(endLink).isDirectory().isSymbolicLink();

    Path middleLinkDir = endLink.resolve("d1/d2");
    NioFiles.createDirectories(middleLinkDir);
    assertThat(middleLinkDir).isDirectory();

    Path badLink = memoryFs.getFs().getPath("/bad-link");
    Files.createSymbolicLink(badLink, memoryFs.getFs().getPath("bad-target"));
    try {
      NioFiles.createDirectories(badLink);
      fail("`createDirectories()` over a dangling symlink shall not pass");
    }
    catch (FileAlreadyExistsException ignored) { }
  }

  @Test
  public void setReadOnly() throws IOException {
    Path f = tempDir.newFile("f").toPath();

    NioFiles.setReadOnly(f, true);
    try {
      Files.writeString(f, "test");
      fail("Writing to " + f + " should have failed");
    }
    catch (AccessDeniedException ignored) { }

    NioFiles.setReadOnly(f, false);
    Files.writeString(f, "test");

    Path d = tempDir.newDirectory("d").toPath(), child = d.resolve("f");

    NioFiles.setReadOnly(d, true);
    if (!SystemInfo.isWindows) {
      try {
        Files.createFile(child);
        fail("Creating " + child + " should have failed");
      }
      catch (AccessDeniedException ignored) { }
    }

    NioFiles.setReadOnly(d, false);
    Files.createFile(child);
  }

  @Test
  public void setExecutable() throws IOException, InterruptedException {
    assumeUnix();

    Path script = Files.writeString(tempDir.newFile("test.sh").toPath(), "#!/bin/sh\nexit 42\n");
    try { runAndGetExitValue(script.toString()); }
    catch (IOException ignored) { }

    NioFiles.setExecutable(script);
    assertEquals(42, runAndGetExitValue(script.toString()));
  }

  private static int runAndGetExitValue(String command) throws IOException, InterruptedException {
    Process process = Runtime.getRuntime().exec(command);
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
    Path dir = Files.createDirectory(memoryFs.getFs().getPath("/dir"));
    Path f1 = Files.createFile(dir.resolve("f1")), f2 = Files.createFile(dir.resolve("f2"));
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
    assertSame(FileAttributes.BROKEN_SYMLINK, FileSystemUtil.getAttributes(symlink.toString()));
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

      assertSame(FileAttributes.BROKEN_SYMLINK, FileSystemUtil.getAttributes(fileLink.toString()));
      assertSame(FileAttributes.BROKEN_SYMLINK, FileSystemUtil.getAttributes(dirLink.toString()));
    }
    finally {
      PlatformTestUtil.assertSuccessful(new GeneralCommandLine("wsl", "-d", distribution, "-e", "rm", "-rf", tmpPath));
    }
  }
}
