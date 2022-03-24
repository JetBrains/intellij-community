// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
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
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.IoTestUtil.assumeUnix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
    Path dir = Files.createDirectory(memoryFs.getFs().getPath("/dir"));
    Path file1 = Files.createFile(dir.resolve("file1")), file2 = Files.createFile(dir.resolve("file2"));
    try (Stream<Path> stream = Files.list(dir)) {
      assertThat(stream).containsExactlyInAnyOrder(file1, file2);
    }

    NioFiles.deleteRecursively(dir);
    assertThat(dir).doesNotExist();

    Path nonExisting = memoryFs.getFs().getPath("non-existing");
    assertThat(nonExisting).doesNotExist();
    NioFiles.deleteRecursively(nonExisting);
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
}
